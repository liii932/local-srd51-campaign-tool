package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.module.ModuleCanonicalException;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.persistence.CharacterCardMutationRepository;
import com.dndtool.persistence.CharacterCardMutationRepository.Action;
import com.dndtool.persistence.CharacterCardRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Loads verified character cards and validates all host card/item mutations. */
public final class CharacterCardService {
    private final ModuleCatalogRepository moduleRepository;
    private final CharacterCardRepository cardRepository;
    private final CharacterCardMutationRepository mutationRepository;
    private final DigestComputer hasher;
    private final ExpectedHashProvider manifest;
    private final BuiltinModuleReleaseRegistry releaseRegistry;

    public CharacterCardService(
            ModuleCatalogRepository moduleRepository,
            CharacterCardRepository cardRepository,
            CharacterCardMutationRepository mutationRepository) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository);
        this.cardRepository = Objects.requireNonNull(cardRepository);
        this.mutationRepository = Objects.requireNonNull(mutationRepository);
        hasher = new ModuleContentHasher()::sha256;
        BuiltinModuleHashManifest builtInManifest = new BuiltinModuleHashManifest();
        manifest = release -> builtInManifest.expectedSha256(release).orElse(null);
        releaseRegistry = new BuiltinModuleReleaseRegistry();
    }

    CharacterCardService(
            ModuleCatalogRepository moduleRepository,
            CharacterCardRepository cardRepository,
            CharacterCardMutationRepository mutationRepository,
            DigestComputer hasher,
            ExpectedHashProvider manifest) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository);
        this.cardRepository = Objects.requireNonNull(cardRepository);
        this.mutationRepository = Objects.requireNonNull(mutationRepository);
        this.hasher = Objects.requireNonNull(hasher);
        this.manifest = Objects.requireNonNull(manifest);
        this.releaseRegistry = new BuiltinModuleReleaseRegistry();
    }

    public LoadResult load(String characterKey) throws SQLException {
        if (!isCanonicalUuid(characterKey)) return loadFailure(LoadStatus.INVALID_REQUEST);
        Optional<CharacterCardRepository.Snapshot> found =
                cardRepository.findByCharacterKey(characterKey);
        if (found.isEmpty()) return loadFailure(LoadStatus.NOT_FOUND);
        CharacterCardRepository.Snapshot snapshot = found.orElseThrow();
        final VerifiedModule verified;
        try {
            verified = verifiedModule(
                    snapshot.binding().savedModuleKey(),
                    snapshot.binding().savedReleaseVersion());
        } catch (ModuleMismatchException exception) {
            return loadFailure(LoadStatus.MODULE_HASH_MISMATCH);
        }
        if (verified == null) return loadFailure(LoadStatus.MODULE_UNAVAILABLE);
        if (!bindingMatches(snapshot.binding(), verified)) {
            return loadFailure(LoadStatus.MODULE_HASH_MISMATCH);
        }
        try {
            return new LoadResult(LoadStatus.READY, buildCard(snapshot, verified.catalog()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return loadFailure(LoadStatus.INVALID_STATE);
        }
    }

    public MutationResult mutate(
            String characterKey,
            String rowVersion,
            String actionValue,
            String targetKey,
            String value,
            String description,
            String quantity,
            String requestId,
            String requestDigestSha256) throws SQLException {
        final long expectedRowVersion;
        final Action action;
        final PreparedInput input;
        try {
            expectedRowVersion = parseRowVersion(rowVersion);
            action = Action.valueOf(actionValue == null ? "" : actionValue);
            input = prepare(action, targetKey, value, description, quantity);
        } catch (IllegalArgumentException exception) {
            return mutationFailure(MutationStatus.INVALID_REQUEST);
        }
        if (!isCanonicalUuid(characterKey) || !isCanonicalUuid(requestId)
                || !isSha256(requestDigestSha256)) {
            return mutationFailure(MutationStatus.INVALID_REQUEST);
        }
        String actualDigest = CharacterCardRequestDigest.sha256(
                characterKey, expectedRowVersion, action.name(), input.targetKey(),
                input.digestValue(), input.description(), input.digestQuantity());
        if (!secureEquals(actualDigest, requestDigestSha256)) {
            return mutationFailure(MutationStatus.INVALID_REQUEST);
        }

        final VerifiedModule verified;
        Optional<CharacterCardRepository.Snapshot> found =
                cardRepository.findByCharacterKey(characterKey);
        if (found.isEmpty()) return mutationFailure(MutationStatus.NOT_FOUND);
        CharacterCardRepository.Snapshot snapshot = found.orElseThrow();
        try {
            verified = verifiedModule(
                    snapshot.binding().savedModuleKey(),
                    snapshot.binding().savedReleaseVersion());
        } catch (ModuleMismatchException exception) {
            return mutationFailure(MutationStatus.MODULE_HASH_MISMATCH);
        }
        if (verified == null) return mutationFailure(MutationStatus.MODULE_UNAVAILABLE);
        if (!bindingMatches(snapshot.binding(), verified)) {
            return mutationFailure(MutationStatus.MODULE_HASH_MISMATCH);
        }
        try {
            validateAgainstCatalog(action, input, verified.catalog());
            CharacterCardMutationRepository.Result persisted = mutationRepository.mutate(
                    new CharacterCardMutationRepository.Command(
                            requestId, requestDigestSha256, characterKey, expectedRowVersion,
                            action, input.targetKey(), input.textValue(), input.description(),
                            input.integerValue(), verified.catalog().release().moduleKey(),
                            verified.catalog().release().releaseVersion(),
                            verified.contentSha256(), integerFieldRules(verified.catalog())));
            return switch (persisted.status()) {
                case UPDATED -> new MutationResult(
                        MutationStatus.UPDATED, persisted.rowVersion());
                case ALREADY_SUCCEEDED -> new MutationResult(
                        MutationStatus.ALREADY_SUCCEEDED, persisted.rowVersion());
                case NOT_FOUND -> mutationFailure(MutationStatus.NOT_FOUND);
                case TARGET_NOT_FOUND -> mutationFailure(MutationStatus.NOT_FOUND);
                case VERSION_CONFLICT -> new MutationResult(
                        MutationStatus.VERSION_CONFLICT, persisted.rowVersion());
                case IDEMPOTENCY_CONFLICT ->
                        mutationFailure(MutationStatus.IDEMPOTENCY_CONFLICT);
                case MODULE_BINDING_MISMATCH ->
                        mutationFailure(MutationStatus.MODULE_HASH_MISMATCH);
                case NO_CHANGE -> new MutationResult(
                        MutationStatus.NO_CHANGE, persisted.rowVersion());
            };
        } catch (IllegalArgumentException exception) {
            return mutationFailure(MutationStatus.INVALID_REQUEST);
        }
    }

    private VerifiedModule verifiedModule(String moduleKey, String releaseVersion)
            throws SQLException, ModuleMismatchException {
        BuiltinModuleReleaseRegistry.Resolution resolved =
                releaseRegistry.resolveReleased(moduleKey, releaseVersion);
        if (resolved.status() != BuiltinModuleReleaseRegistry.ResolutionStatus.READY) {
            return null;
        }
        BuiltinModuleReleaseRegistry.Descriptor descriptor = resolved.descriptor();
        Optional<ModuleCatalog> found = moduleRepository.findByIdentity(
                moduleKey, releaseVersion);
        if (found.isEmpty()) return null;
        ModuleCatalog catalog = found.orElseThrow();
        ModuleCatalog.Release release = catalog.release();
        if (!moduleKey.equals(release.moduleKey())
                || !releaseVersion.equals(release.releaseVersion())
                || descriptor.canonicalFormatVersion() != release.canonicalFormatVersion()
                || !descriptor.hashAlgorithm().equals(release.hashAlgorithm())
                || !"RELEASED".equals(release.releaseStatus())
                || release.contentSha256() == null) {
            throw new ModuleMismatchException();
        }
        String expected = manifest.expectedSha256(release);
        final String actual;
        try {
            actual = hasher.sha256(catalog);
        } catch (ModuleCanonicalException exception) {
            throw new ModuleMismatchException();
        }
        if (expected == null || !secureEquals(expected, release.contentSha256())
                || !secureEquals(expected, actual)) {
            throw new ModuleMismatchException();
        }
        return new VerifiedModule(catalog, expected);
    }

    private static Card buildCard(
            CharacterCardRepository.Snapshot snapshot, ModuleCatalog catalog) {
        Map<String, CharacterCardRepository.FieldValue> storedFields = uniqueIndex(
                snapshot.fields(), CharacterCardRepository.FieldValue::fieldKey);
        if (storedFields.size() != catalog.fieldDefinitions().size()) {
            throw new IllegalArgumentException("Incomplete character fields");
        }
        Map<String, Integer> abilityScores = new HashMap<>();
        List<FieldView> fields = new ArrayList<>();
        for (ModuleCatalog.FieldDefinition definition : catalog.fieldDefinitions()) {
            CharacterCardRepository.FieldValue stored = storedFields.get(definition.fieldKey());
            if (stored == null || !definition.dataType().equals(stored.valueType())
                    || !(stored.value() instanceof ModuleCatalog.IntegerValue integer)) {
                throw new IllegalArgumentException("Invalid character field type");
            }
            int value = Math.toIntExact(integer.value());
            Integer modifier = definition.fieldKey().startsWith("ability.")
                    ? CharacterDerivedValueCalculator.abilityModifier(value) : null;
            fields.add(new FieldView(
                    definition.fieldKey(), definition.displayName(), value,
                    scalarInteger(definition.minimumValue()),
                    scalarInteger(definition.maximumValue()), modifier,
                    definition.unit()));
            if (modifier != null) abilityScores.put(definition.fieldKey(), value);
        }

        CharacterDerivedValueCalculator calculator =
                new CharacterDerivedValueCalculator(catalog);
        List<CharacterDerivedValueCalculator.ClassLevel> calculationLevels = snapshot
                .classLevels().stream()
                .map(level -> new CharacterDerivedValueCalculator.ClassLevel(
                        level.classKey(), level.level()))
                .toList();
        int totalLevel = calculator.totalLevel(calculationLevels);
        int proficiencyBonus = calculator.proficiencyBonus(totalLevel);
        Map<String, Integer> storedLevels = uniqueIndex(
                snapshot.classLevels(), CharacterCardRepository.ClassLevel::classKey)
                .entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> entry.getValue().level()));
        List<ClassView> classes = catalog.classDefinitions().stream()
                .map(definition -> new ClassView(
                        definition.classKey(), definition.displayName(),
                        storedLevels.getOrDefault(definition.classKey(), 0)))
                .toList();

        Map<String, CharacterCardRepository.Proficiency> skillTiers = uniqueIndex(
                snapshot.skillProficiencies(), CharacterCardRepository.Proficiency::targetKey);
        Map<String, CharacterCardRepository.Proficiency> saveTiers = uniqueIndex(
                snapshot.saveProficiencies(), CharacterCardRepository.Proficiency::targetKey);
        if (skillTiers.size() != catalog.skillDefinitions().size()
                || saveTiers.size() != catalog.saveDefinitions().size()) {
            throw new IllegalArgumentException("Incomplete proficiencies");
        }
        Set<String> tierKeys = catalog.proficiencyTiers().stream()
                .map(ModuleCatalog.ProficiencyTier::proficiencyKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<ProficiencyView> skills = catalog.skillDefinitions().stream()
                .map(definition -> proficiencyView(
                        definition.skillKey(), definition.displayName(),
                        definition.abilityFieldKey(), skillTiers, tierKeys,
                        key -> calculator.skillBonus(
                                key, abilityScores, calculationLevels,
                                skillTiers.get(key).proficiencyKey())))
                .toList();
        Map<String, String> abilityNames = catalog.fieldDefinitions().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        ModuleCatalog.FieldDefinition::fieldKey,
                        ModuleCatalog.FieldDefinition::displayName));
        List<ProficiencyView> saves = catalog.saveDefinitions().stream()
                .map(definition -> proficiencyView(
                        definition.saveKey(),
                        abilityNames.get(definition.abilityFieldKey()) + "豁免",
                        definition.abilityFieldKey(), saveTiers, tierKeys,
                        key -> calculator.savingThrowBonus(
                                key, abilityScores, calculationLevels,
                                saveTiers.get(key).proficiencyKey())))
                .toList();

        Map<String, ModuleCatalog.ItemTemplate> templates = uniqueIndex(
                catalog.itemTemplates(), ModuleCatalog.ItemTemplate::itemKey);
        List<ItemView> items = snapshot.items().stream().map(item -> {
            if (!Set.of("ACTIVE", "ARCHIVED").contains(item.itemStatus())
                    || item.quantity() < 1 || item.quantity() > 999) {
                throw new IllegalArgumentException("Invalid item");
            }
            if ("MODULE".equals(item.sourceKind())) {
                ModuleCatalog.ItemTemplate template = templates.get(item.itemKey());
                if (template == null || !template.displayName().equals(item.itemName())
                        || !template.description().equals(item.itemDescription())) {
                    throw new IllegalArgumentException("Invalid module item snapshot");
                }
            } else if (!"TEMPORARY".equals(item.sourceKind()) || item.itemKey() != null) {
                throw new IllegalArgumentException("Invalid item source");
            }
            return new ItemView(
                    Long.toString(item.itemId()), item.sourceKind(), item.itemKey(),
                    item.itemName(), item.itemDescription(), item.quantity(), item.itemStatus());
        }).toList();
        List<TierView> tiers = catalog.proficiencyTiers().stream()
                .map(tier -> new TierView(tier.proficiencyKey(), tier.enumCode()))
                .toList();
        List<ItemTemplateView> itemTemplates = catalog.itemTemplates().stream()
                .map(item -> new ItemTemplateView(
                        item.itemKey(), item.displayName(), item.description()))
                .toList();
        return new Card(
                snapshot.characterKey(), snapshot.characterType(), snapshot.characterName(),
                snapshot.characterStatus(), snapshot.rowVersion(), totalLevel,
                proficiencyBonus, fields, classes, skills, saves, tiers,
                itemTemplates, items);
    }

    private static ProficiencyView proficiencyView(
            String key,
            String displayName,
            String abilityFieldKey,
            Map<String, CharacterCardRepository.Proficiency> stored,
            Set<String> tierKeys,
            Function<String, Integer> bonus) {
        CharacterCardRepository.Proficiency proficiency = stored.get(key);
        if (proficiency == null || !tierKeys.contains(proficiency.proficiencyKey())) {
            throw new IllegalArgumentException("Invalid proficiency");
        }
        return new ProficiencyView(
                key, displayName, abilityFieldKey, proficiency.proficiencyKey(), bonus.apply(key));
    }

    private static PreparedInput prepare(
            Action action, String targetKey, String value, String description, String quantity) {
        String target = targetKey == null ? "" : targetKey;
        return switch (action) {
            case SET_FIELD, SET_CLASS_LEVEL -> {
                int number = parseInteger(value, 0, 999);
                yield new PreparedInput(target, null, "", number,
                        Integer.toString(number), "");
            }
            case SET_ITEM_QUANTITY -> {
                requirePositiveDecimal(target);
                int number = parseInteger(value, 1, 999);
                yield new PreparedInput(target, null, "", number,
                        Integer.toString(number), "");
            }
            case SET_SKILL_PROFICIENCY, SET_SAVE_PROFICIENCY ->
                    new PreparedInput(target, requireStableKey(value), "", null, value, "");
            case ADD_MODULE_ITEM -> {
                int count = parseInteger(quantity, 1, 999);
                yield new PreparedInput(
                        requireStableKey(target), null, "", count,
                        "", Integer.toString(count));
            }
            case ADD_TEMPORARY_ITEM -> {
                int count = parseInteger(quantity, 1, 999);
                String name = normalizeText(value, 1, 80, true);
                String normalizedDescription = normalizeText(description, 0, 500, false);
                yield new PreparedInput(
                        "", name, normalizedDescription, count,
                        name, Integer.toString(count));
            }
            case ARCHIVE_ITEM, RESTORE_ITEM -> {
                requirePositiveDecimal(target);
                yield new PreparedInput(target, null, "", null, "", "");
            }
        };
    }

    private static void validateAgainstCatalog(
            Action action, PreparedInput input, ModuleCatalog catalog) {
        switch (action) {
            case SET_FIELD -> {
                ModuleCatalog.FieldDefinition field = catalog.fieldDefinitions().stream()
                        .filter(definition -> definition.fieldKey().equals(input.targetKey()))
                        .findFirst().orElseThrow();
                if (!"INTEGER".equals(field.dataType())) throw new IllegalArgumentException();
                long value = input.integerValue();
                Long minimum = scalarInteger(field.minimumValue());
                Long maximum = scalarInteger(field.maximumValue());
                if ((minimum != null && value < minimum) || (maximum != null && value > maximum)) {
                    throw new IllegalArgumentException();
                }
            }
            case SET_CLASS_LEVEL -> {
                requireCatalogKey(
                        catalog.classDefinitions(), ModuleCatalog.ClassDefinition::classKey,
                        input.targetKey());
                if (input.integerValue() < 0 || input.integerValue() > 20) {
                    throw new IllegalArgumentException();
                }
            }
            case SET_SKILL_PROFICIENCY -> {
                requireCatalogKey(
                        catalog.skillDefinitions(), ModuleCatalog.SkillDefinition::skillKey,
                        input.targetKey());
                requireTier(catalog, input.textValue());
            }
            case SET_SAVE_PROFICIENCY -> {
                requireCatalogKey(
                        catalog.saveDefinitions(), ModuleCatalog.SaveDefinition::saveKey,
                        input.targetKey());
                requireTier(catalog, input.textValue());
            }
            case ADD_MODULE_ITEM -> {
                ModuleCatalog.ItemTemplate item = catalog.itemTemplates().stream()
                        .filter(template -> template.itemKey().equals(input.targetKey()))
                        .findFirst().orElseThrow();
                input.setTextAndDescription(item.displayName(), item.description());
            }
            case ADD_TEMPORARY_ITEM, SET_ITEM_QUANTITY, ARCHIVE_ITEM, RESTORE_ITEM -> {
                // Input normalization is the complete catalog-independent rule for these actions.
            }
        }
    }

    private static List<CharacterCardMutationRepository.IntegerFieldRule> integerFieldRules(
            ModuleCatalog catalog) {
        return catalog.fieldDefinitions().stream().map(field -> {
            if (!"INTEGER".equals(field.dataType())) throw new IllegalArgumentException();
            Long minimum = scalarInteger(field.minimumValue());
            Long maximum = scalarInteger(field.maximumValue());
            return new CharacterCardMutationRepository.IntegerFieldRule(
                    field.fieldKey(), minimum == null ? Long.MIN_VALUE : minimum,
                    maximum == null ? Long.MAX_VALUE : maximum,
                    field.dependentMaxFieldKey());
        }).toList();
    }

    private static void requireTier(ModuleCatalog catalog, String key) {
        requireCatalogKey(
                catalog.proficiencyTiers(), ModuleCatalog.ProficiencyTier::proficiencyKey, key);
    }

    private static <T> void requireCatalogKey(
            List<T> rows, Function<T, String> key, String expected) {
        if (rows.stream().noneMatch(row -> key.apply(row).equals(expected))) {
            throw new IllegalArgumentException("Unknown module key");
        }
    }

    private static Long scalarInteger(ModuleCatalog.ScalarValue value) {
        if (value == null) return null;
        if (!(value instanceof ModuleCatalog.IntegerValue integer)) {
            throw new IllegalArgumentException("Expected integer scalar");
        }
        return integer.value();
    }

    private static <T> Map<String, T> uniqueIndex(
            List<T> rows, Function<T, String> key) {
        Map<String, T> indexed = new HashMap<>();
        for (T row : rows) {
            if (row == null || indexed.putIfAbsent(key.apply(row), row) != null) {
                throw new IllegalArgumentException("Duplicate character card row");
            }
        }
        return Map.copyOf(indexed);
    }

    private static boolean bindingMatches(
            CharacterCardRepository.Binding binding, VerifiedModule verified) {
        ModuleCatalog.Release release = verified.catalog().release();
        String contentSha256 = verified.contentSha256();
        return "RELEASED".equals(binding.releaseStatus())
                && release.moduleKey().equals(binding.savedModuleKey())
                && release.moduleKey().equals(binding.frozenModuleKey())
                && release.moduleKey().equals(binding.releaseModuleKey())
                && release.releaseVersion().equals(binding.savedReleaseVersion())
                && release.releaseVersion().equals(binding.frozenReleaseVersion())
                && release.releaseVersion().equals(binding.releaseVersion())
                && contentSha256.equals(binding.savedContentSha256())
                && contentSha256.equals(binding.frozenContentSha256())
                && contentSha256.equals(binding.releaseContentSha256());
    }

    private static int parseInteger(String value, int minimum, int maximum) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,3})")) {
            throw new IllegalArgumentException("Invalid integer");
        }
        int parsed = Integer.parseInt(value);
        if (parsed < minimum || parsed > maximum) throw new IllegalArgumentException();
        return parsed;
    }

    private static long parseRowVersion(String value) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("Invalid row version");
        }
        long parsed = Long.parseLong(value);
        if (parsed == Long.MAX_VALUE) throw new IllegalArgumentException();
        return parsed;
    }

    private static String requireStableKey(String value) {
        if (value == null
                || !value.matches("[a-z][a-z0-9_]*(?:[.][a-z][a-z0-9_]*)*")) {
            throw new IllegalArgumentException("Invalid stable key");
        }
        return value;
    }

    private static void requirePositiveDecimal(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("Invalid item token");
        }
        long parsed = Long.parseLong(value);
        if (parsed <= 0) throw new IllegalArgumentException();
    }

    private static String normalizeText(
            String value, int minimumCodePoints, int maximumCodePoints, boolean trim) {
        if (value == null) throw new IllegalArgumentException("Missing text");
        String normalized = Normalizer.normalize(trim ? value.strip() : value, Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < minimumCodePoints || length > maximumCodePoints
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid text");
        }
        return normalized;
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
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

    private static LoadResult loadFailure(LoadStatus status) {
        return new LoadResult(status, null);
    }

    private static MutationResult mutationFailure(MutationStatus status) {
        return new MutationResult(status, null);
    }

    private record VerifiedModule(ModuleCatalog catalog, String contentSha256) {
    }

    private static final class ModuleMismatchException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @FunctionalInterface
    interface DigestComputer {
        String sha256(ModuleCatalog catalog) throws ModuleCanonicalException;
    }

    @FunctionalInterface
    interface ExpectedHashProvider {
        String expectedSha256(ModuleCatalog.Release release);
    }

    private static final class PreparedInput {
        private final String targetKey;
        private String textValue;
        private String description;
        private final Integer integerValue;
        private final String digestValue;
        private final String digestQuantity;

        private PreparedInput(
                String targetKey,
                String textValue,
                String description,
                Integer integerValue,
                String digestValue,
                String digestQuantity) {
            this.targetKey = targetKey;
            this.textValue = textValue;
            this.description = description;
            this.integerValue = integerValue;
            this.digestValue = digestValue;
            this.digestQuantity = digestQuantity;
        }

        String targetKey() { return targetKey; }
        String textValue() { return textValue; }
        String description() { return description; }
        Integer integerValue() { return integerValue; }
        String digestValue() { return digestValue; }
        String digestQuantity() { return digestQuantity; }

        void setTextAndDescription(String textValue, String description) {
            this.textValue = textValue;
            this.description = description;
        }
    }

    public record LoadResult(LoadStatus status, Card card) {
    }

    public enum LoadStatus {
        READY,
        INVALID_REQUEST,
        NOT_FOUND,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH,
        INVALID_STATE
    }

    public record MutationResult(MutationStatus status, Long rowVersion) {
    }

    public enum MutationStatus {
        UPDATED,
        ALREADY_SUCCEEDED,
        INVALID_REQUEST,
        NOT_FOUND,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        NO_CHANGE,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH
    }

    public record Card(
            String characterKey,
            String characterType,
            String characterName,
            String characterStatus,
            long rowVersion,
            int totalLevel,
            int proficiencyBonus,
            List<FieldView> fields,
            List<ClassView> classes,
            List<ProficiencyView> skills,
            List<ProficiencyView> saves,
            List<TierView> tiers,
            List<ItemTemplateView> itemTemplates,
            List<ItemView> items) {
        public Card {
            fields = List.copyOf(fields);
            classes = List.copyOf(classes);
            skills = List.copyOf(skills);
            saves = List.copyOf(saves);
            tiers = List.copyOf(tiers);
            itemTemplates = List.copyOf(itemTemplates);
            items = List.copyOf(items);
        }
    }

    public record FieldView(
            String fieldKey,
            String displayName,
            int value,
            Long minimum,
            Long maximum,
            Integer modifier,
            String unit) {
    }

    public record ClassView(String classKey, String displayName, int level) {
    }

    public record ProficiencyView(
            String targetKey,
            String displayName,
            String abilityFieldKey,
            String proficiencyKey,
            int bonus) {
    }

    public record TierView(String proficiencyKey, String enumCode) {
    }

    public record ItemTemplateView(String itemKey, String displayName, String description) {
    }

    public record ItemView(
            String itemToken,
            String sourceKind,
            String itemKey,
            String itemName,
            String itemDescription,
            int quantity,
            String itemStatus) {
    }
}
