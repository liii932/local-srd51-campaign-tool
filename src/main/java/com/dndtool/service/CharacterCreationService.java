package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.module.ModuleHashManifest;
import com.dndtool.persistence.CampaignModuleBindingRepository;
import com.dndtool.persistence.CharacterCreationRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import com.dndtool.service.CharacterCreationIdentityFactory.CharacterType;
import com.dndtool.service.CharacterCreationIdentityFactory.NewCharacterRequest;
import com.dndtool.service.CharacterCreationIdentityFactory.PreparedCharacter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Validates and prepares blank or template-backed characters for one atomic write. */
public final class CharacterCreationService {
    private static final String NONE_PROFICIENCY = "proficiency.none";

    private final ModuleCatalogRepository moduleRepository;
    private final CharacterCreationRepository characterRepository;
    private final CharacterCreationIdentityFactory identityFactory;
    private final DigestComputer hasher;
    private final ModuleHashManifest manifest;
    private final CampaignModuleBindingRepository campaignBindings;
    private final BuiltinModuleReleaseRegistry releaseRegistry;

    public CharacterCreationService(
            ModuleCatalogRepository moduleRepository,
            CharacterCreationRepository characterRepository,
            CampaignModuleBindingRepository campaignBindings) {
        this(moduleRepository, characterRepository, new CharacterCreationIdentityFactory(),
                new ModuleContentHasher()::sha256, new BuiltinModuleHashManifest(),
                campaignBindings, new BuiltinModuleReleaseRegistry());
    }

    CharacterCreationService(
            ModuleCatalogRepository moduleRepository,
            CharacterCreationRepository characterRepository,
            CharacterCreationIdentityFactory identityFactory,
            DigestComputer hasher,
            ModuleHashManifest manifest,
            CampaignModuleBindingRepository campaignBindings,
            BuiltinModuleReleaseRegistry releaseRegistry) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository);
        this.characterRepository = Objects.requireNonNull(characterRepository);
        this.identityFactory = Objects.requireNonNull(identityFactory);
        this.hasher = Objects.requireNonNull(hasher);
        this.manifest = Objects.requireNonNull(manifest);
        this.campaignBindings = Objects.requireNonNull(campaignBindings);
        this.releaseRegistry = Objects.requireNonNull(releaseRegistry);
    }

    public Result create(
            String campaignKey,
            String characterType,
            String characterName,
            String templateKey,
            String requestId,
            String requestDigestSha256) throws SQLException {
        final CharacterType type;
        final String normalizedName;
        final String normalizedTemplate;
        try {
            type = CharacterType.valueOf(characterType == null ? "" : characterType);
            normalizedName = CharacterNamePolicy.normalize(characterName);
            normalizedTemplate = normalizeTemplateKey(templateKey);
        } catch (IllegalArgumentException exception) {
            return failure(Status.INVALID_REQUEST);
        }
        if (!isCanonicalUuid(campaignKey)
                || !isCanonicalUuid(requestId)
                || !isSha256(requestDigestSha256)) {
            return failure(Status.INVALID_REQUEST);
        }
        String actualRequestDigest = CharacterCreationRequestDigest.sha256(
                campaignKey, type.name(), normalizedName, normalizedTemplate);
        if (!secureEquals(actualRequestDigest, requestDigestSha256)) {
            return failure(Status.INVALID_REQUEST);
        }
        if (normalizedTemplate != null && type != CharacterType.NPC) {
            return failure(Status.INVALID_REQUEST);
        }

        Optional<CampaignModuleBindingRepository.Binding> bound =
                campaignBindings.findByCampaignKey(campaignKey);
        if (bound.isEmpty()) {
            return failure(Status.CAMPAIGN_UNAVAILABLE);
        }
        CampaignModuleBindingRepository.Binding binding = bound.orElseThrow();
        BuiltinModuleReleaseRegistry.Resolution resolved = releaseRegistry.resolveReleased(
                binding.frozenModuleKey(), binding.frozenReleaseVersion());
        if (resolved.status() != BuiltinModuleReleaseRegistry.ResolutionStatus.READY) {
            return failure(Status.MODULE_UNAVAILABLE);
        }
        Optional<ModuleCatalog> found = moduleRepository.findByIdentity(
                binding.frozenModuleKey(), binding.frozenReleaseVersion());
        if (found.isEmpty()) {
            return failure(Status.MODULE_UNAVAILABLE);
        }
        ModuleCatalog catalog = found.orElseThrow();
        if (catalog.release() == null
                || !binding.frozenModuleKey().equals(catalog.release().moduleKey())
                || !binding.frozenReleaseVersion().equals(
                        catalog.release().releaseVersion())) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }
        String approvedHash = verifyRelease(catalog);
        if (approvedHash == null || !approvedHash.equals(binding.frozenContentSha256())) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }

        final CreationValues values;
        try {
            values = normalizedTemplate == null
                    ? blankValues(catalog)
                    : templateValues(catalog, normalizedTemplate);
        } catch (UnknownTemplateException exception) {
            return failure(Status.TEMPLATE_UNAVAILABLE);
        } catch (IllegalArgumentException exception) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }

        PreparedCharacter prepared = identityFactory.prepare(
                new NewCharacterRequest(campaignKey, type, normalizedName));
        ModuleCatalog.Release release = catalog.release();
        CharacterCreationRepository.Result persisted = characterRepository.create(
                new CharacterCreationRepository.Command(
                        requestId,
                        requestDigestSha256,
                        prepared.characterKey(),
                        prepared.campaignKey(),
                        prepared.characterType().name(),
                        prepared.characterName(),
                        release.moduleKey(),
                        release.releaseVersion(),
                        approvedHash,
                        normalizedTemplate,
                        values.fields(),
                        values.classes(),
                        values.skills(),
                        values.saves()));
        return switch (persisted.status()) {
            case CREATED -> new Result(
                    Status.CREATED, persisted.characterKey(), persisted.rowVersion());
            case ALREADY_SUCCEEDED -> new Result(
                    Status.ALREADY_SUCCEEDED,
                    persisted.characterKey(), persisted.rowVersion());
            case IDEMPOTENCY_CONFLICT -> failure(Status.IDEMPOTENCY_CONFLICT);
            case CAMPAIGN_UNAVAILABLE -> failure(Status.CAMPAIGN_UNAVAILABLE);
            case MODULE_BINDING_MISMATCH -> failure(Status.MODULE_HASH_MISMATCH);
        };
    }

    private String verifyRelease(ModuleCatalog catalog) {
        ModuleCatalog.Release release = catalog.release();
        if (!"RELEASED".equals(release.releaseStatus()) || release.contentSha256() == null) {
            return null;
        }
        String expected = manifest.expectedSha256(release).orElse(null);
        final String actual;
        try {
            actual = hasher.sha256(catalog);
        } catch (ModuleCanonicalException exception) {
            return null;
        }
        return expected != null
                        && secureEquals(expected, release.contentSha256())
                        && secureEquals(expected, actual)
                ? expected : null;
    }

    private static CreationValues blankValues(ModuleCatalog catalog) {
        List<CharacterCreationRepository.FieldValue> fields = catalog.fieldDefinitions().stream()
                .map(field -> new CharacterCreationRepository.FieldValue(
                        field.fieldKey(), field.dataType(), field.defaultValue()))
                .toList();
        requireNoneProficiency(catalog);
        List<CharacterCreationRepository.Proficiency> skills = catalog.skillDefinitions().stream()
                .map(skill -> new CharacterCreationRepository.Proficiency(
                        skill.skillKey(), NONE_PROFICIENCY))
                .toList();
        List<CharacterCreationRepository.Proficiency> saves = catalog.saveDefinitions().stream()
                .map(save -> new CharacterCreationRepository.Proficiency(
                        save.saveKey(), NONE_PROFICIENCY))
                .toList();
        return new CreationValues(fields, List.of(), skills, saves);
    }

    private static CreationValues templateValues(ModuleCatalog catalog, String templateKey)
            throws UnknownTemplateException {
        boolean exists = catalog.entityTemplates().stream()
                .anyMatch(template -> template.templateKey().equals(templateKey));
        if (!exists) {
            throw new UnknownTemplateException();
        }
        List<CharacterCreationRepository.FieldValue> fields = catalog.entityTemplateValues()
                .stream()
                .filter(value -> value.templateKey().equals(templateKey))
                .map(value -> new CharacterCreationRepository.FieldValue(
                        value.fieldKey(), value.valueType(), value.value()))
                .toList();
        List<CharacterCreationRepository.ClassLevel> classes = catalog
                .entityTemplateClassLevels().stream()
                .filter(value -> value.templateKey().equals(templateKey))
                .map(value -> new CharacterCreationRepository.ClassLevel(
                        value.classKey(), value.level()))
                .toList();
        List<CharacterCreationRepository.Proficiency> skills = new ArrayList<>();
        List<CharacterCreationRepository.Proficiency> saves = new ArrayList<>();
        for (ModuleCatalog.EntityTemplateProficiency proficiency
                : catalog.entityTemplateProficiencies()) {
            if (!proficiency.templateKey().equals(templateKey)) {
                continue;
            }
            CharacterCreationRepository.Proficiency value =
                    new CharacterCreationRepository.Proficiency(
                            proficiency.targetKey(), proficiency.proficiencyKey());
            if ("SKILL".equals(proficiency.targetKind())) {
                skills.add(value);
            } else if ("SAVING_THROW".equals(proficiency.targetKind())) {
                saves.add(value);
            } else {
                throw new IllegalArgumentException("Unknown template proficiency kind");
            }
        }
        if (fields.size() != catalog.fieldDefinitions().size()
                || skills.size() != catalog.skillDefinitions().size()
                || saves.size() != catalog.saveDefinitions().size()) {
            throw new IllegalArgumentException("Incomplete entity template");
        }
        return new CreationValues(fields, classes, skills, saves);
    }

    private static void requireNoneProficiency(ModuleCatalog catalog) {
        boolean exists = catalog.proficiencyTiers().stream()
                .anyMatch(tier -> NONE_PROFICIENCY.equals(tier.proficiencyKey()));
        if (!exists) {
            throw new IllegalArgumentException("Missing none proficiency");
        }
    }

    private static String normalizeTemplateKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.matches("[a-z][a-z0-9_]*(?:[.][a-z][a-z0-9_]*)*")) {
            throw new IllegalArgumentException("Invalid template key");
        }
        return value;
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static Result failure(Status status) {
        return new Result(status, null, null);
    }

    private record CreationValues(
            List<CharacterCreationRepository.FieldValue> fields,
            List<CharacterCreationRepository.ClassLevel> classes,
            List<CharacterCreationRepository.Proficiency> skills,
            List<CharacterCreationRepository.Proficiency> saves) {
    }

    private static final class UnknownTemplateException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @FunctionalInterface
    interface DigestComputer {
        String sha256(ModuleCatalog catalog) throws ModuleCanonicalException;
    }

    public record Result(Status status, String characterKey, Long rowVersion) {
    }

    public enum Status {
        CREATED,
        ALREADY_SUCCEEDED,
        INVALID_REQUEST,
        IDEMPOTENCY_CONFLICT,
        CAMPAIGN_UNAVAILABLE,
        TEMPLATE_UNAVAILABLE,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH
    }
}
