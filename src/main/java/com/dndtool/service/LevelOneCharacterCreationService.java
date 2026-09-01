package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.persistence.LevelOneCharacterCreationRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Preview/confirm orchestration with a published-release gate and stale-preview defense. */
public final class LevelOneCharacterCreationService {
    private final ModuleCatalogRepository catalogs;
    private final LevelOneCharacterCreationRepository repository;
    private final LevelOneCharacterRules rules;
    private final ReleaseVerifier releaseVerifier;
    private final java.util.function.Supplier<UUID> uuidSupplier;

    public LevelOneCharacterCreationService(
            ModuleCatalogRepository catalogs, LevelOneCharacterCreationRepository repository) {
        this(catalogs, repository, new LevelOneCharacterRules(), standardVerifier(), UUID::randomUUID);
    }

    LevelOneCharacterCreationService(ModuleCatalogRepository catalogs,
            LevelOneCharacterCreationRepository repository, LevelOneCharacterRules rules,
            ReleaseVerifier releaseVerifier, java.util.function.Supplier<UUID> uuidSupplier) {
        this.catalogs = Objects.requireNonNull(catalogs);
        this.repository = Objects.requireNonNull(repository);
        this.rules = Objects.requireNonNull(rules);
        this.releaseVerifier = Objects.requireNonNull(releaseVerifier);
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier);
    }

    public PreviewResult preview(LevelOneCharacterRules.Request request) throws SQLException {
        if (request == null || !canonicalUuid(request.campaignKey())) {
            return PreviewResult.rejected(Status.INVALID_REQUEST);
        }
        Optional<LevelOneCharacterCreationRepository.PreviewContext> found =
                repository.findPreviewContext(request.campaignKey());
        if (found.isEmpty()) return PreviewResult.rejected(Status.CAMPAIGN_UNAVAILABLE);
        LevelOneCharacterCreationRepository.PreviewContext context = found.orElseThrow();
        Optional<ModuleCatalog> catalog = catalogs.findByIdentity(
                context.moduleKey(), context.releaseVersion());
        if (catalog.isEmpty()) return PreviewResult.rejected(Status.MODULE_UNAVAILABLE);
        String approvedHash = releaseVerifier.verify(catalog.orElseThrow(), context);
        if (approvedHash == null) return PreviewResult.rejected(Status.MODULE_UNAVAILABLE);
        if (!secureEquals(approvedHash, context.contentSha256())) {
            return PreviewResult.rejected(Status.MODULE_HASH_MISMATCH);
        }
        try {
            LevelOneCharacterRules.Prepared prepared = rules.prepare(
                    catalog.orElseThrow(), request, context.expectedEventTail(), approvedHash);
            return PreviewResult.ready(prepared);
        } catch (IllegalArgumentException exception) {
            String code = exception instanceof LevelOneCharacterRules.RuleException rule
                    ? rule.code() : "INVALID_REQUEST";
            return PreviewResult.rejected(Status.INVALID_REQUEST, code);
        }
    }

    public ConfirmResult confirm(LevelOneCharacterRules.Request request, long expectedEventTail,
            String previewDigest, String requestId, String requestDigest) throws SQLException {
        if (!canonicalUuid(requestId) || expectedEventTail < 0 || !sha256(previewDigest)
                || !sha256(requestDigest)) {
            return ConfirmResult.rejected(Status.INVALID_REQUEST);
        }
        PreviewResult preview = preview(request);
        if (preview.status() != Status.PREVIEW_READY) {
            return ConfirmResult.rejected(preview.status());
        }
        LevelOneCharacterRules.Prepared prepared = preview.prepared();
        if (prepared.expectedEventTail() != expectedEventTail
                || !secureEquals(prepared.previewDigestSha256(), previewDigest)) {
            return ConfirmResult.rejected(Status.STALE_PREVIEW);
        }
        String expectedDigest = LevelOneCharacterCreationRequestDigest.sha256(
                request.campaignKey(), prepared.characterName(), previewDigest);
        if (!secureEquals(expectedDigest, requestDigest)) {
            return ConfirmResult.rejected(Status.INVALID_REQUEST);
        }

        LevelOneCharacterCreationRepository.PreviewContext context = repository
                .findPreviewContext(request.campaignKey()).orElse(null);
        if (context == null) return ConfirmResult.rejected(Status.CAMPAIGN_UNAVAILABLE);
        if (context.expectedEventTail() != expectedEventTail) {
            return ConfirmResult.rejected(Status.STALE_PREVIEW);
        }
        String characterKey = canonicalGeneratedUuid();
        List<LevelOneCharacterCreationRepository.Selection> selections = selections(prepared);
        LevelOneCharacterCreationRepository.Result result = repository.confirm(
                new LevelOneCharacterCreationRepository.Command(
                        requestId, requestDigest, characterKey, request.campaignKey(),
                        prepared.characterName(), context.moduleKey(), context.releaseVersion(),
                        context.contentSha256(), expectedEventTail, previewDigest,
                        prepared.raceKey(), prepared.subraceKey(), prepared.backgroundKey(),
                        prepared.classKey(), prepared.baseAbilityScores(),
                        prepared.finalAbilityScores(), selections, prepared.maximumHitPoints(),
                        prepared.hitDieSides(), prepared.initialResources().stream()
                                .map(value -> new LevelOneCharacterCreationRepository.InitialResource(
                                        value.resourceKey(), value.currentValue(),
                                        value.maximumValue(), value.unlimited()))
                                .toList(), prepared.featureTransition()));
        return switch (result.status()) {
            case CREATED -> ConfirmResult.success(Status.CREATED, result);
            case ALREADY_SUCCEEDED -> ConfirmResult.success(Status.ALREADY_SUCCEEDED, result);
            case IDEMPOTENCY_CONFLICT -> ConfirmResult.rejected(Status.IDEMPOTENCY_CONFLICT);
            case CAMPAIGN_UNAVAILABLE -> ConfirmResult.rejected(Status.CAMPAIGN_UNAVAILABLE);
            case MODULE_BINDING_MISMATCH -> ConfirmResult.rejected(Status.MODULE_HASH_MISMATCH);
            case STALE_PREVIEW -> ConfirmResult.rejected(Status.STALE_PREVIEW);
        };
    }

    private String canonicalGeneratedUuid() {
        UUID value = uuidSupplier.get();
        if (value == null || value.version() != 4) {
            throw new IllegalStateException("UUID supplier did not return UUIDv4");
        }
        return value.toString();
    }

    private static List<LevelOneCharacterCreationRepository.Selection> selections(
            LevelOneCharacterRules.Prepared prepared) {
        List<LevelOneCharacterCreationRepository.Selection> result = new ArrayList<>();
        add(result, "ABILITY_BONUS", prepared.abilityBonusChoices());
        add(result, "SKILL", prepared.skills());
        add(result, "SAVE", prepared.saves());
        add(result, "LANGUAGE", prepared.languages());
        add(result, "TOOL", prepared.tools());
        add(result, "STARTING_OPTION", prepared.startingOptions());
        return List.copyOf(result);
    }

    private static void add(List<LevelOneCharacterCreationRepository.Selection> target,
            String kind, List<String> values) {
        values.forEach(value -> target.add(
                new LevelOneCharacterCreationRepository.Selection(kind, value)));
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
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException exception) { return false; }
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
        String verify(ModuleCatalog catalog,
                LevelOneCharacterCreationRepository.PreviewContext context);
    }

    public record PreviewResult(Status status, LevelOneCharacterRules.Prepared prepared,
            String errorCode) {
        static PreviewResult ready(LevelOneCharacterRules.Prepared value) {
            return new PreviewResult(Status.PREVIEW_READY, value, null);
        }
        static PreviewResult rejected(Status status) { return rejected(status, status.name()); }
        static PreviewResult rejected(Status status, String code) {
            return new PreviewResult(status, null, code);
        }
    }

    public record ConfirmResult(Status status, String characterKey, Long rowVersion) {
        static ConfirmResult success(Status status,
                LevelOneCharacterCreationRepository.Result result) {
            return new ConfirmResult(status, result.characterKey(), result.rowVersion());
        }
        static ConfirmResult rejected(Status status) { return new ConfirmResult(status, null, null); }
    }

    public enum Status {
        PREVIEW_READY, CREATED, ALREADY_SUCCEEDED, INVALID_REQUEST,
        IDEMPOTENCY_CONFLICT, CAMPAIGN_UNAVAILABLE, MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH, STALE_PREVIEW
    }
}
