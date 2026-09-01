package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fail-closed structural and cross-reference validation for canonical format version 2. */
final class ModuleCatalogValidatorV2 {
    private static final Set<String> DEFINITION_TYPES = Set.of(
            "character.race",
            "character.subrace",
            "character.background",
            "character.language",
            "character.tool",
            "character.class",
            "character.subclass",
            "character.feature",
            "character.resource",
            "character.feat");
    private static final Map<String, String> KEY_PREFIXES = Map.ofEntries(
            Map.entry("character.race", "race."),
            Map.entry("character.subrace", "subrace."),
            Map.entry("character.background", "background."),
            Map.entry("character.language", "language."),
            Map.entry("character.tool", "tool."),
            Map.entry("character.class", "class."),
            Map.entry("character.subclass", "subclass."),
            Map.entry("character.feature", "feature."),
            Map.entry("character.resource", "resource."),
            Map.entry("character.feat", "feat."));
    private static final Set<String> ATTRIBUTE_KEYS = Set.of(
            "source.page",
            "catalog.category",
            "class.hit_die_sides",
            "class.proficiency_bonus_profile",
            "class.multiclass_prerequisite",
            "class.multiclass_proficiency_profile",
            "class.asi_levels",
            "class.starting_proficiency_profile",
            "feature.level",
            "feature.execution_mode",
            "feature.execution_algorithm",
            "subclass.selection_level",
            "resource.recovery",
            "resource.maximum_profile",
            "resource.execution_mode",
            "resource.recovery_profile",
            "feat.minimum_strength",
            "feat.prerequisite",
            "feat.execution_mode",
            "feat.execution_algorithm",
            "creation.level_one_profile",
            "creation.ability_method");

    private ModuleCatalogValidatorV2() {
    }

    static void validate(ModuleCatalog catalog) throws ModuleCanonicalException {
        if (catalog == null) {
            reject();
        }
        validateRelease(catalog.release());
        rejectLegacyPartitions(catalog);

        Map<Identity, ModuleCatalog.CatalogDefinition> definitions =
                validateDefinitions(catalog.catalogDefinitions());
        Map<AttributeIdentity, List<ModuleCatalog.CatalogAttribute>> attributes =
                validateAttributes(catalog.catalogAttributes(), definitions);
        validateRequiredAttributes(definitions, attributes);
        validateRelations(catalog.catalogRelations(), definitions);
        validateLifecycleAttributes(definitions, attributes, catalog.catalogRelations());
    }

    private static void validateRelease(ModuleCatalog.Release release)
            throws ModuleCanonicalException {
        if (release == null
                || !"dnd5e2014_srd51_se".equals(release.moduleKey())
                || !"1".equals(release.releaseVersion())
                || release.canonicalFormatVersion() != 2
                || !"SHA-256".equals(release.hashAlgorithm())
                || !("DRAFT".equals(release.releaseStatus())
                        || "RELEASED".equals(release.releaseStatus()))
                || (release.contentSha256() != null
                        && !release.contentSha256().matches("[0-9a-f]{64}"))) {
            reject();
        }
    }

    private static void rejectLegacyPartitions(ModuleCatalog catalog)
            throws ModuleCanonicalException {
        List<List<?>> legacy = List.of(
                catalog.ruleConstants(), catalog.fieldDefinitions(), catalog.classDefinitions(),
                catalog.proficiencyTiers(), catalog.proficiencyBonusBands(),
                catalog.skillDefinitions(), catalog.saveDefinitions(), catalog.itemTemplates(),
                catalog.entityTemplates(), catalog.entityTemplateValues(),
                catalog.entityTemplateClassLevels(), catalog.entityTemplateProficiencies(),
                catalog.checkDefinitions(), catalog.rollModes(), catalog.eventTemplates(),
                catalog.eventChecks(), catalog.eventEffects(), catalog.effectDefinitions(),
                catalog.effectParameters(), catalog.mapDefinitions(), catalog.mapNodes(),
                catalog.mapConnections());
        if (legacy.stream().anyMatch(list -> !list.isEmpty())) {
            reject();
        }
    }

    private static Map<Identity, ModuleCatalog.CatalogDefinition> validateDefinitions(
            List<ModuleCatalog.CatalogDefinition> rows) throws ModuleCanonicalException {
        Map<Identity, ModuleCatalog.CatalogDefinition> definitions = new HashMap<>();
        Map<String, Set<Integer>> orders = new HashMap<>();
        for (ModuleCatalog.CatalogDefinition row : rows) {
            if (row == null
                    || !DEFINITION_TYPES.contains(row.definitionType())
                    || !stableKey(row.definitionType())
                    || !stableKey(row.definitionKey())
                    || !row.definitionKey().startsWith(KEY_PREFIXES.get(row.definitionType()))
                    || row.sortOrder() <= 0) {
                reject();
            }
            validateText(row.displayName(), 1, 120);
            validateText(row.description(), 1, 1000);
            Identity identity = new Identity(row.definitionType(), row.definitionKey());
            if (definitions.putIfAbsent(identity, row) != null
                    || !orders.computeIfAbsent(row.definitionType(), ignored -> new HashSet<>())
                            .add(row.sortOrder())) {
                reject();
            }
        }
        for (Set<Integer> typeOrders : orders.values()) {
            requireContiguous(typeOrders);
        }
        return definitions;
    }

    private static Map<AttributeIdentity, List<ModuleCatalog.CatalogAttribute>> validateAttributes(
            List<ModuleCatalog.CatalogAttribute> rows,
            Map<Identity, ModuleCatalog.CatalogDefinition> definitions)
            throws ModuleCanonicalException {
        Map<AttributeIdentity, List<ModuleCatalog.CatalogAttribute>> grouped = new HashMap<>();
        Set<String> uniqueRows = new HashSet<>();
        for (ModuleCatalog.CatalogAttribute row : rows) {
            if (row == null
                    || !definitions.containsKey(new Identity(
                            row.definitionType(), row.definitionKey()))
                    || !ATTRIBUTE_KEYS.contains(row.attributeKey())
                    || row.attributeOrder() <= 0
                    || !valueMatches(row.valueType(), row.value())) {
                reject();
            }
            validateAttributeDomain(row);
            String unique = row.definitionType() + '\0' + row.definitionKey() + '\0'
                    + row.attributeKey() + '\0' + row.attributeOrder();
            if (!uniqueRows.add(unique)) {
                reject();
            }
            AttributeIdentity identity = new AttributeIdentity(
                    row.definitionType(), row.definitionKey(), row.attributeKey());
            grouped.computeIfAbsent(identity, ignored -> new java.util.ArrayList<>()).add(row);
        }
        for (List<ModuleCatalog.CatalogAttribute> values : grouped.values()) {
            Set<Integer> orders = new HashSet<>();
            values.forEach(value -> orders.add(value.attributeOrder()));
            requireContiguous(orders);
        }
        return grouped;
    }

    private static void validateAttributeDomain(ModuleCatalog.CatalogAttribute row)
            throws ModuleCanonicalException {
        switch (row.attributeKey()) {
            case "source.page" -> requireInteger(row, value -> value >= 3 && value <= 74);
            case "class.hit_die_sides" -> {
                requireDefinitionType(row, "character.class");
                requireInteger(row, value -> value == 6 || value == 8
                        || value == 10 || value == 12);
            }
            case "class.proficiency_bonus_profile" -> {
                requireDefinitionType(row, "character.class");
                if (!(row.value() instanceof ModuleCatalog.TextValue)) reject();
                final AdvancementValueProfile profile;
                try {
                    profile = AdvancementValueProfile.parse(
                            ((ModuleCatalog.TextValue) row.value()).value());
                } catch (IllegalArgumentException exception) {
                    reject();
                    return;
                }
                if (profile.firstLevel() != 1 || !profile.constantsOnly()
                        || profile.atLevel(1, 0).maximum() != 2
                        || profile.atLevel(4, 0).maximum() != 2
                        || profile.atLevel(5, 0).maximum() != 3
                        || profile.atLevel(8, 0).maximum() != 3
                        || profile.atLevel(9, 0).maximum() != 4
                        || profile.atLevel(12, 0).maximum() != 4
                        || profile.atLevel(13, 0).maximum() != 5
                        || profile.atLevel(16, 0).maximum() != 5
                        || profile.atLevel(17, 0).maximum() != 6
                        || profile.atLevel(20, 0).maximum() != 6) {
                    reject();
                }
            }
            case "class.multiclass_prerequisite" -> {
                requireDefinitionType(row, "character.class");
                if (!(row.value() instanceof ModuleCatalog.TextValue text)
                        || !validAbilityPrerequisite(text.value())) reject();
            }
            case "class.multiclass_proficiency_profile" -> {
                requireDefinitionType(row, "character.class");
                if (!(row.value() instanceof ModuleCatalog.TextValue text)
                        || !validMulticlassProficiencyProfile(text.value())) reject();
            }
            case "class.asi_levels" -> {
                requireDefinitionType(row, "character.class");
                if (!(row.value() instanceof ModuleCatalog.TextValue text)
                        || !validLevelList(text.value())) reject();
            }
            case "class.starting_proficiency_profile" -> {
                requireDefinitionType(row, "character.class");
                if (!(row.value() instanceof ModuleCatalog.TextValue text)
                        || !validSortedStableKeyList(text.value())) reject();
            }
            case "feature.level" -> {
                requireDefinitionType(row, "character.feature");
                requireInteger(row, value -> value >= 1 && value <= 20);
            }
            case "feature.execution_mode" -> {
                requireDefinitionType(row, "character.feature");
                requireIdentifier(row, Set.of("AUTOMATIC", "DM_ADJUDICATION", "BLOCKED"));
            }
            case "feature.execution_algorithm" -> {
                requireDefinitionType(row, "character.feature");
                requireIdentifier(row, Set.of(
                        "BLOCKED_ISSUE_8_V1",
                        "BLOCKED_SPELL_SYSTEM_V1",
                        "BOUNDED_FEATURE_SELECTION_V1",
                        "BOUNDED_SUBCLASS_SELECTION_V1",
                        "BOUNDED_DM_ADJUDICATION_V1",
                        "AUTOMATIC_RESOURCE_LIFECYCLE_V1",
                        "BLOCKED_DOWNSTREAM_SYSTEM_V1"));
            }
            case "subclass.selection_level" -> {
                requireDefinitionType(row, "character.subclass");
                requireInteger(row, value -> value >= 1 && value <= 3);
            }
            case "feat.minimum_strength" -> {
                requireDefinitionType(row, "character.feat");
                requireInteger(row, value -> value == 13);
            }
            case "feat.prerequisite" -> {
                requireDefinitionType(row, "character.feat");
                if (!(row.value() instanceof ModuleCatalog.TextValue text)
                        || !validAbilityPrerequisite(text.value())) reject();
            }
            case "feat.execution_mode" -> {
                requireDefinitionType(row, "character.feat");
                requireIdentifier(row, Set.of("AUTOMATIC", "DM_ADJUDICATION", "BLOCKED"));
            }
            case "feat.execution_algorithm" -> {
                requireDefinitionType(row, "character.feat");
                requireIdentifier(row, Set.of(
                        "AUTOMATIC_FEAT_EFFECT_V1",
                        "BOUNDED_DM_ADJUDICATION_V1",
                        "BLOCKED_DOWNSTREAM_SYSTEM_V1"));
            }
            case "catalog.category" -> {
                if (!(row.value() instanceof ModuleCatalog.IdentifierValue identifier)
                        || !allowedCategory(row.definitionType(), identifier.value())) {
                    reject();
                }
            }
            case "resource.recovery" -> {
                requireDefinitionType(row, "character.resource");
                if (!(row.value() instanceof ModuleCatalog.IdentifierValue identifier)
                        || !Set.of("SHORT_REST", "LONG_REST", "SPECIAL")
                                .contains(identifier.value())) {
                    reject();
                }
            }
            case "resource.maximum_profile" -> {
                requireDefinitionType(row, "character.resource");
                if (!(row.value() instanceof ModuleCatalog.TextValue)) reject();
                try {
                    AdvancementValueProfile.parse(
                            ((ModuleCatalog.TextValue) row.value()).value());
                } catch (IllegalArgumentException exception) {
                    reject();
                }
            }
            case "resource.execution_mode" -> {
                requireDefinitionType(row, "character.resource");
                requireIdentifier(row, Set.of("AUTOMATIC", "BLOCKED"));
            }
            case "resource.recovery_profile" -> {
                requireDefinitionType(row, "character.resource");
                if (!(row.value() instanceof ModuleCatalog.TextValue text)
                        || !validRecoveryProfile(text.value())) {
                    reject();
                }
            }
            case "creation.level_one_profile" -> {
                if (!Set.of("character.race", "character.subrace",
                                "character.background", "character.class")
                        .contains(row.definitionType())
                        || !(row.value() instanceof ModuleCatalog.TextValue)) {
                    reject();
                }
                try {
                    LevelOneRuleProfile.parse(
                            ((ModuleCatalog.TextValue) row.value()).value());
                } catch (IllegalArgumentException exception) {
                    reject();
                }
            }
            case "creation.ability_method" -> {
                if (!"character.class".equals(row.definitionType())
                        || !(row.value() instanceof ModuleCatalog.IdentifierValue identifier)
                        || !"ability.standard_array_v1".equals(identifier.value())) {
                    reject();
                }
            }
            default -> reject();
        }
    }

    private static boolean allowedCategory(String definitionType, String value) {
        return switch (definitionType) {
            case "character.race", "character.subrace", "character.class",
                    "character.subclass" -> value.equals("BASE");
            case "character.background" -> value.equals("SAMPLE");
            case "character.language" ->
                    Set.of("STANDARD", "EXOTIC", "SECRET").contains(value);
            case "character.tool" -> Set.of(
                    "ARTISAN", "KIT", "GAMING_SET", "MUSICAL_INSTRUMENT",
                    "NAVIGATION", "VEHICLE").contains(value);
            case "character.feature" -> Set.of(
                    "BASE", "RACIAL", "BACKGROUND", "SUBCLASS", "OPTION")
                    .contains(value);
            case "character.resource" -> Set.of("CLASS", "CORE").contains(value);
            case "character.feat" -> value.equals("OPTIONAL");
            default -> false;
        };
    }

    private static void validateRequiredAttributes(
            Map<Identity, ModuleCatalog.CatalogDefinition> definitions,
            Map<AttributeIdentity, List<ModuleCatalog.CatalogAttribute>> attributes)
            throws ModuleCanonicalException {
        for (Identity identity : definitions.keySet()) {
            requireAttributeCount(attributes, identity, "source.page", 1, 1);
            switch (identity.type()) {
                case "character.class" -> {
                    requireAttributeCount(attributes, identity, "class.hit_die_sides", 1, 1);
                    requireAttributeCount(
                            attributes, identity, "class.proficiency_bonus_profile", 1, 1);
                    requireAttributeCount(
                            attributes, identity, "class.multiclass_prerequisite", 1, 1);
                    requireAttributeCount(attributes, identity,
                            "class.multiclass_proficiency_profile", 1, 1);
                    requireAttributeCount(attributes, identity, "class.asi_levels", 1, 1);
                    requireAttributeCount(
                            attributes, identity, "class.starting_proficiency_profile", 1, 1);
                }
                case "character.feature" ->
                        requireAttributeCount(attributes, identity, "feature.level", 1, 20);
                case "character.resource" -> {
                    requireAttributeCount(attributes, identity, "resource.recovery", 1, 1);
                    if (!coreResource(identity.key())) {
                        requireAttributeCount(
                                attributes, identity, "resource.maximum_profile", 1, 1);
                    }
                }
                case "character.feat" -> {
                    requireAttributeCount(attributes, identity, "feat.minimum_strength", 1, 1);
                    requireAttributeCount(attributes, identity, "feat.prerequisite", 1, 1);
                    requireAttributeCount(attributes, identity, "feat.execution_mode", 1, 1);
                    requireAttributeCount(attributes, identity, "feat.execution_algorithm", 1, 1);
                }
                default -> {
                    // No additional mandatory attribute for this definition type.
                }
            }
        }
    }

    private static void validateRelations(
            List<ModuleCatalog.CatalogRelation> rows,
            Map<Identity, ModuleCatalog.CatalogDefinition> definitions)
            throws ModuleCanonicalException {
        Map<RelationGroup, Set<Integer>> orders = new HashMap<>();
        Map<RelationGroup, Integer> counts = new HashMap<>();
        Set<String> uniqueTargets = new HashSet<>();
        for (ModuleCatalog.CatalogRelation row : rows) {
            if (row == null) {
                reject();
            }
            Identity source = new Identity(row.sourceType(), row.sourceKey());
            Identity target = new Identity(row.targetType(), row.targetKey());
            if (!definitions.containsKey(source) || !definitions.containsKey(target)
                    || row.relationOrder() <= 0 || !validRelationShape(row)) {
                reject();
            }
            RelationGroup group = new RelationGroup(source, row.relationType());
            if (!orders.computeIfAbsent(group, ignored -> new HashSet<>())
                            .add(row.relationOrder())
                    || !uniqueTargets.add(source + "\0" + row.relationType() + "\0" + target)) {
                reject();
            }
            counts.merge(group, 1, Integer::sum);
        }
        for (Set<Integer> groupOrders : orders.values()) {
            requireContiguous(groupOrders);
        }
        for (Identity identity : definitions.keySet()) {
            switch (identity.type()) {
                case "character.subrace" -> requireExactlyOne(
                        counts, new RelationGroup(identity, "subrace.parent_race"));
                case "character.subclass" -> requireExactlyOne(
                        counts, new RelationGroup(identity, "subclass.parent_class"));
                case "character.feature" -> requireExactlyOne(
                        counts, new RelationGroup(identity, "feature.owner"));
                case "character.resource" -> {
                    if (!coreResource(identity.key())) {
                        requireExactlyOne(counts,
                                new RelationGroup(identity, "resource.owner"));
                    }
                }
                default -> {
                    // Root catalog entries need no ownership relation.
                }
            }
        }
    }

    private static void validateLifecycleAttributes(
            Map<Identity, ModuleCatalog.CatalogDefinition> definitions,
            Map<AttributeIdentity, List<ModuleCatalog.CatalogAttribute>> attributes,
            List<ModuleCatalog.CatalogRelation> relations) throws ModuleCanonicalException {
        Map<Identity, Identity> owners = new HashMap<>();
        for (ModuleCatalog.CatalogRelation relation : relations) {
            if ("feature.owner".equals(relation.relationType())
                    || "resource.owner".equals(relation.relationType())) {
                owners.put(new Identity(relation.sourceType(), relation.sourceKey()),
                        new Identity(relation.targetType(), relation.targetKey()));
            }
        }
        for (Identity identity : definitions.keySet()) {
            if ("character.subclass".equals(identity.type())) {
                requireAttributeCount(
                        attributes, identity, "subclass.selection_level", 1, 1);
            } else if ("character.feature".equals(identity.type())) {
                Identity owner = owners.get(identity);
                boolean classOwned = owner != null && Set.of(
                        "character.class", "character.subclass").contains(owner.type());
                requireAttributeCount(attributes, identity, "feature.execution_mode",
                        classOwned ? 1 : 0, classOwned ? 1 : 0);
                requireAttributeCount(attributes, identity, "feature.execution_algorithm",
                        classOwned ? 1 : 0, classOwned ? 1 : 0);
                if (classOwned) validateFeatureDisposition(identity, attributes);
            } else if ("character.resource".equals(identity.type())
                    && !coreResource(identity.key())) {
                requireAttributeCount(attributes, identity, "resource.execution_mode", 1, 1);
                requireAttributeCount(attributes, identity, "resource.recovery_profile", 1, 1);
            }
        }
    }

    private static void validateFeatureDisposition(
            Identity identity,
            Map<AttributeIdentity, List<ModuleCatalog.CatalogAttribute>> attributes)
            throws ModuleCanonicalException {
        String mode = identifierAttribute(attributes, identity, "feature.execution_mode");
        String algorithm = identifierAttribute(
                attributes, identity, "feature.execution_algorithm");
        boolean valid = switch (mode) {
            case "AUTOMATIC" -> "AUTOMATIC_RESOURCE_LIFECYCLE_V1".equals(algorithm);
            case "DM_ADJUDICATION" -> Set.of(
                    "BOUNDED_FEATURE_SELECTION_V1",
                    "BOUNDED_SUBCLASS_SELECTION_V1",
                    "BOUNDED_DM_ADJUDICATION_V1").contains(algorithm);
            case "BLOCKED" -> Set.of(
                    "BLOCKED_ISSUE_8_V1",
                    "BLOCKED_SPELL_SYSTEM_V1",
                    "BLOCKED_DOWNSTREAM_SYSTEM_V1").contains(algorithm);
            default -> false;
        };
        if (!valid) reject();
    }

    private static String identifierAttribute(
            Map<AttributeIdentity, List<ModuleCatalog.CatalogAttribute>> attributes,
            Identity identity, String key) throws ModuleCanonicalException {
        List<ModuleCatalog.CatalogAttribute> values = attributes.get(
                new AttributeIdentity(identity.type(), identity.key(), key));
        if (values == null || values.size() != 1
                || !(values.getFirst().value()
                        instanceof ModuleCatalog.IdentifierValue identifier)) {
            reject();
            return "";
        }
        return identifier.value();
    }

    private static boolean validAbilityPrerequisite(String value) {
        if (value == null || value.isEmpty() || value.length() > 200) return false;
        String term = "ability[.](strength|dexterity|constitution|intelligence|wisdom|charisma)>=(1[0-9]|20)";
        return java.util.Arrays.stream(value.split("[|]", -1)).allMatch(alternative ->
                !alternative.isEmpty() && java.util.Arrays.stream(alternative.split("&", -1))
                        .allMatch(part -> part.matches(term)));
    }

    private static boolean validMulticlassProficiencyProfile(String value) {
        if (value == null || value.length() > 1000) return false;
        if (value.isEmpty()) return true;
        boolean grantSeen = false;
        for (String part : value.split("[|]", -1)) {
            String list;
            if (part.startsWith("grant=")) {
                if (grantSeen) return false;
                grantSeen = true;
                list = part.substring(6);
            } else {
                java.util.regex.Matcher choice = java.util.regex.Pattern.compile(
                        "choice=([1-9]):(.+)").matcher(part);
                if (!choice.matches()) return false;
                list = choice.group(2);
            }
            if (list.isEmpty()) continue;
            Set<String> unique = new HashSet<>();
            for (String key : list.split(",", -1)) {
                if (!stableKey(key) || !unique.add(key)) return false;
            }
        }
        return true;
    }

    private static boolean validSortedStableKeyList(String value) {
        if (value == null || value.isEmpty() || value.length() > 1000) return false;
        String previous = null;
        for (String key : value.split(",", -1)) {
            if (!stableKey(key) || previous != null && previous.compareTo(key) >= 0) return false;
            previous = key;
        }
        return true;
    }

    private static boolean validLevelList(String value) {
        if (value == null || value.isEmpty() || value.length() > 60) return false;
        int previous = 0;
        for (String token : value.split(",", -1)) {
            try {
                int level = Integer.parseInt(token);
                if (level <= previous || level < 1 || level > 20) return false;
                previous = level;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    private static boolean validRecoveryProfile(String value) {
        if (value == null || value.length() > 200) return false;
        int expectedLevel = 1;
        for (String band : value.split(",", -1)) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "([1-9]|1[0-9]|20)-([1-9]|1[0-9]|20):(SHORT_REST|LONG_REST)")
                    .matcher(band);
            if (!matcher.matches()) return false;
            int first = Integer.parseInt(matcher.group(1));
            int last = Integer.parseInt(matcher.group(2));
            if (first != expectedLevel || last < first) return false;
            expectedLevel = last + 1;
        }
        return expectedLevel == 21;
    }

    private static boolean validRelationShape(ModuleCatalog.CatalogRelation row) {
        return switch (row.relationType()) {
            case "subrace.parent_race" -> row.sourceType().equals("character.subrace")
                    && row.targetType().equals("character.race");
            case "subclass.parent_class" -> row.sourceType().equals("character.subclass")
                    && row.targetType().equals("character.class");
            case "feature.owner" -> row.sourceType().equals("character.feature")
                    && Set.of(
                                    "character.race", "character.subrace",
                                    "character.background", "character.class",
                                    "character.subclass", "character.feat")
                            .contains(row.targetType());
            case "resource.owner" -> row.sourceType().equals("character.resource")
                    && Set.of("character.class", "character.subclass")
                            .contains(row.targetType());
            case "feature.prerequisite" -> row.sourceType().equals("character.feature")
                    && row.targetType().equals("character.feature")
                    && !row.sourceKey().equals(row.targetKey());
            default -> false;
        };
    }

    private static boolean coreResource(String key) {
        return "resource.hit_points".equals(key) || key.startsWith("resource.hit_dice.");
    }

    private static boolean valueMatches(String valueType, ModuleCatalog.ScalarValue value)
            throws ModuleCanonicalException {
        if (valueType == null || value == null) {
            return false;
        }
        if (value instanceof ModuleCatalog.TextValue text) {
            validateText(text.value(), 0, 1000);
        } else if (value instanceof ModuleCatalog.IdentifierValue identifier) {
            if (!asciiIdentifier(identifier.value())) {
                return false;
            }
        } else if (value instanceof ModuleCatalog.DecimalValue decimal) {
            BigDecimal canonical = decimal.value().stripTrailingZeros();
            if (canonical.toPlainString().length() > 80) {
                return false;
            }
        }
        return switch (valueType) {
            case "TEXT" -> value instanceof ModuleCatalog.TextValue;
            case "IDENTIFIER" -> value instanceof ModuleCatalog.IdentifierValue;
            case "INTEGER" -> value instanceof ModuleCatalog.IntegerValue;
            case "DECIMAL" -> value instanceof ModuleCatalog.DecimalValue;
            case "BOOLEAN" -> value instanceof ModuleCatalog.BooleanValue;
            default -> false;
        };
    }

    private static void requireInteger(
            ModuleCatalog.CatalogAttribute row, java.util.function.LongPredicate predicate)
            throws ModuleCanonicalException {
        if (!(row.value() instanceof ModuleCatalog.IntegerValue integer)
                || !predicate.test(integer.value())) {
            reject();
        }
    }

    private static void requireDefinitionType(
            ModuleCatalog.CatalogAttribute row, String expected)
            throws ModuleCanonicalException {
        if (!expected.equals(row.definitionType())) {
            reject();
        }
    }

    private static void requireIdentifier(
            ModuleCatalog.CatalogAttribute row, Set<String> allowed)
            throws ModuleCanonicalException {
        if (!(row.value() instanceof ModuleCatalog.IdentifierValue identifier)
                || !allowed.contains(identifier.value())) {
            reject();
        }
    }

    private static void requireAttributeCount(
            Map<AttributeIdentity, List<ModuleCatalog.CatalogAttribute>> attributes,
            Identity identity,
            String key,
            int minimum,
            int maximum) throws ModuleCanonicalException {
        int count = attributes.getOrDefault(
                new AttributeIdentity(identity.type(), identity.key(), key), List.of()).size();
        if (count < minimum || count > maximum) {
            reject();
        }
    }

    private static void requireExactlyOne(
            Map<RelationGroup, Integer> counts, RelationGroup group)
            throws ModuleCanonicalException {
        if (counts.getOrDefault(group, 0) != 1) {
            reject();
        }
    }

    private static void requireContiguous(Set<Integer> orders) throws ModuleCanonicalException {
        for (int expected = 1; expected <= orders.size(); expected++) {
            if (!orders.contains(expected)) {
                reject();
            }
        }
    }

    private static boolean stableKey(String value) {
        return value != null
                && value.length() <= 128
                && value.matches("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    }

    private static boolean asciiIdentifier(String value) {
        if (value == null || value.isEmpty() || value.length() > 128) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 0x7f || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }

    private static void validateText(String value, int minimumCodePoints, int maximumCodePoints)
            throws ModuleCanonicalException {
        if (value == null) {
            reject();
        }
        String normalized = Normalizer.normalize(
                value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
        try {
            StandardCharsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(normalized));
        } catch (java.nio.charset.CharacterCodingException exception) {
            reject();
        }
        int count = normalized.codePointCount(0, normalized.length());
        if (count < minimumCodePoints || count > maximumCodePoints
                || (minimumCodePoints > 0 && normalized.isBlank())) {
            reject();
        }
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if ((codePoint <= 0x1f && codePoint != '\n' && codePoint != '\t')
                    || (codePoint >= 0x7f && codePoint <= 0x9f)) {
                reject();
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static void reject() throws ModuleCanonicalException {
        throw new ModuleCanonicalException();
    }

    private record Identity(String type, String key) {
    }

    private record AttributeIdentity(String type, String key, String attributeKey) {
    }

    private record RelationGroup(Identity source, String relationType) {
    }
}
