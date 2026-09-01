package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.persistence.LevelAdvancementRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/** Preview/confirm orchestration; confirmed randomness is deferred into the locked transaction. */
public final class LevelAdvancementService {
    private final ModuleCatalogRepository catalogs;
    private final LevelAdvancementRepository repository;
    private final LevelAdvancementRules rules;
    private final ReleaseVerifier releaseVerifier;
    private final IntUnaryOperator dieRoller;

    public LevelAdvancementService(
            ModuleCatalogRepository catalogs, LevelAdvancementRepository repository) {
        this(catalogs, repository, new LevelAdvancementRules(), standardVerifier(),
                sides -> ThreadLocalRandom.current().nextInt(1, sides + 1));
    }

    LevelAdvancementService(ModuleCatalogRepository catalogs,
            LevelAdvancementRepository repository, LevelAdvancementRules rules,
            ReleaseVerifier releaseVerifier, IntUnaryOperator dieRoller) {
        this.catalogs = Objects.requireNonNull(catalogs);
        this.repository = Objects.requireNonNull(repository);
        this.rules = Objects.requireNonNull(rules);
        this.releaseVerifier = Objects.requireNonNull(releaseVerifier);
        this.dieRoller = Objects.requireNonNull(dieRoller);
    }

    public PreviewResult preview(LevelAdvancementRules.Request request) throws SQLException {
        if (request == null || !canonicalUuid(request.characterKey())) {
            return PreviewResult.rejected(Status.INVALID_REQUEST, "INVALID_REQUEST");
        }
        Optional<LevelAdvancementRepository.PreviewContext> found =
                repository.findPreviewContext(request.characterKey());
        if (found.isEmpty()) {
            return PreviewResult.rejected(
                    Status.CHARACTER_UNAVAILABLE, "CHARACTER_UNAVAILABLE");
        }
        LevelAdvancementRepository.PreviewContext context = found.orElseThrow();
        Optional<ModuleCatalog> catalog = catalogs.findByIdentity(
                context.moduleKey(), context.releaseVersion());
        if (catalog.isEmpty()) {
            return PreviewResult.rejected(Status.MODULE_UNAVAILABLE, "MODULE_UNAVAILABLE");
        }
        String approvedHash = releaseVerifier.verify(catalog.orElseThrow(), context);
        if (approvedHash == null) {
            return PreviewResult.rejected(Status.MODULE_UNAVAILABLE, "MODULE_UNAVAILABLE");
        }
        if (!secureEquals(approvedHash, context.contentSha256())) {
            return PreviewResult.rejected(Status.MODULE_HASH_MISMATCH, "MODULE_HASH_MISMATCH");
        }
        try {
            return PreviewResult.ready(rules.prepare(
                    catalog.orElseThrow(), request, context, approvedHash));
        } catch (IllegalArgumentException exception) {
            String code = exception instanceof LevelAdvancementRules.RuleException rule
                    ? rule.code() : "INVALID_REQUEST";
            return PreviewResult.rejected(Status.INVALID_REQUEST, code);
        }
    }

    public ConfirmResult confirm(LevelAdvancementRules.Request request,
            long expectedEventTail, long expectedRowVersion, String previewDigest,
            String requestId, String requestDigest) throws SQLException {
        if (request == null || !canonicalUuid(requestId)
                || expectedEventTail < 0 || expectedRowVersion < 0
                || !sha256(previewDigest) || !sha256(requestDigest)) {
            return ConfirmResult.rejected(Status.INVALID_REQUEST);
        }
        String expectedDigest = LevelAdvancementRequestDigest.sha256(request, previewDigest);
        if (!secureEquals(expectedDigest, requestDigest)) {
            return ConfirmResult.rejected(Status.INVALID_REQUEST);
        }
        Optional<LevelAdvancementRepository.Result> completed = repository.findCompleted(
                requestId, requestDigest);
        if (completed.isPresent()) {
            LevelAdvancementRepository.Result replay = completed.orElseThrow();
            return replay.status() == LevelAdvancementRepository.Result.Status.ALREADY_SUCCEEDED
                    ? ConfirmResult.success(Status.ALREADY_SUCCEEDED, replay)
                    : ConfirmResult.rejected(Status.IDEMPOTENCY_CONFLICT);
        }
        PreviewResult preview = preview(request);
        if (preview.status() != Status.PREVIEW_READY) {
            return ConfirmResult.rejected(preview.status());
        }
        LevelAdvancementRules.Prepared prepared = preview.prepared();
        if (prepared.context().expectedEventTail() != expectedEventTail
                || prepared.context().expectedRowVersion() != expectedRowVersion
                || !secureEquals(prepared.previewDigestSha256(), previewDigest)) {
            return ConfirmResult.rejected(Status.STALE_PREVIEW);
        }
        LevelAdvancementRepository.Command command = new LevelAdvancementRepository.Command(
                requestId, requestDigest, previewDigest, prepared.context(),
                prepared.hpChoiceAlgorithm(), prepared.targetLevel(), prepared.hitDieSides(),
                prepared.constitutionModifier(), prepared.previousProficiencyBonus(),
                prepared.newProficiencyBonus(), prepared.resourceChanges(),
                prepared.advancementChoice(), prepared.featureTransition());
        LevelAdvancementRepository.Result result = repository.confirm(
                command, resolver(prepared.hpChoiceAlgorithm(),
                        prepared.retroactiveConstitutionIncrease()));
        return switch (result.status()) {
            case ADVANCED -> ConfirmResult.success(Status.ADVANCED, result);
            case ALREADY_SUCCEEDED -> ConfirmResult.success(Status.ALREADY_SUCCEEDED, result);
            case IDEMPOTENCY_CONFLICT -> ConfirmResult.rejected(Status.IDEMPOTENCY_CONFLICT);
            case CHARACTER_UNAVAILABLE -> ConfirmResult.rejected(Status.CHARACTER_UNAVAILABLE);
            case MODULE_BINDING_MISMATCH -> ConfirmResult.rejected(Status.MODULE_HASH_MISMATCH);
            case STALE_PREVIEW -> ConfirmResult.rejected(Status.STALE_PREVIEW);
            case STALE_ROW_VERSION -> ConfirmResult.rejected(Status.STALE_ROW_VERSION);
            case AUTHORITATIVE_STATE_MISMATCH ->
                    ConfirmResult.rejected(Status.AUTHORITATIVE_STATE_MISMATCH);
        };
    }

    private LevelAdvancementRepository.HitPointResolver resolver(
            String algorithm, int retroactiveConstitutionIncrease) {
        return (sides, constitutionModifier) -> {
            if (LevelAdvancementRules.FIXED_AVERAGE.equals(algorithm)) {
                return new LevelAdvancementRepository.HitPointResolution(
                        null, Math.max(1, sides / 2 + 1 + constitutionModifier)
                                + retroactiveConstitutionIncrease);
            }
            int roll = dieRoller.applyAsInt(sides);
            if (roll < 1 || roll > sides) {
                throw new IllegalStateException("Server die roller returned an invalid value");
            }
            return new LevelAdvancementRepository.HitPointResolution(
                    roll, Math.max(1, roll + constitutionModifier)
                            + retroactiveConstitutionIncrease);
        };
    }

    private static ReleaseVerifier standardVerifier() {
        BuiltinModuleReleaseRegistry registry = new BuiltinModuleReleaseRegistry();
        BuiltinModuleHashManifest manifest = new BuiltinModuleHashManifest();
        ModuleContentHasher hasher = new ModuleContentHasher();
        return (catalog, context) -> {
            BuiltinModuleReleaseRegistry.Resolution resolution = registry.resolveReleased(
                    context.moduleKey(), context.releaseVersion());
            if (resolution.status() != BuiltinModuleReleaseRegistry.ResolutionStatus.READY
                    || catalog.release() == null
                    || !"RELEASED".equals(catalog.release().releaseStatus())) return null;
            String expected = manifest.expectedSha256(catalog.release()).orElse(null);
            try {
                String actual = hasher.sha256(catalog);
                return expected != null && secureEquals(expected, actual) ? expected : null;
            } catch (ModuleCanonicalException exception) {
                return null;
            }
        };
    }

    private static boolean canonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return java.util.UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean sha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean secureEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    @FunctionalInterface
    interface ReleaseVerifier {
        String verify(ModuleCatalog catalog, LevelAdvancementRepository.PreviewContext context);
    }

    public record PreviewResult(
            Status status, LevelAdvancementRules.Prepared prepared, String errorCode) {
        static PreviewResult ready(LevelAdvancementRules.Prepared value) {
            return new PreviewResult(Status.PREVIEW_READY, value, null);
        }

        static PreviewResult rejected(Status status, String errorCode) {
            return new PreviewResult(status, null, errorCode);
        }
    }

    public record ConfirmResult(Status status, String characterKey, Long rowVersion,
            Integer hitDieRoll, Integer hitPointIncrease) {
        static ConfirmResult success(Status status, LevelAdvancementRepository.Result result) {
            return new ConfirmResult(status, result.characterKey(), result.rowVersion(),
                    result.hitDieRoll(), result.hitPointIncrease());
        }

        static ConfirmResult rejected(Status status) {
            return new ConfirmResult(status, null, null, null, null);
        }
    }

    public enum Status {
        PREVIEW_READY,
        ADVANCED,
        ALREADY_SUCCEEDED,
        INVALID_REQUEST,
        IDEMPOTENCY_CONFLICT,
        CHARACTER_UNAVAILABLE,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH,
        STALE_PREVIEW,
        STALE_ROW_VERSION,
        AUTHORITATIVE_STATE_MISMATCH
    }
}
