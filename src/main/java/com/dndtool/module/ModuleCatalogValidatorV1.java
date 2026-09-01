package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Performs fail-closed structural and reference validation before canonical encoding. */
final class ModuleCatalogValidatorV1 {
    private static final Pattern STABLE_KEY =
            Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final Set<String> VALUE_TYPES =
            Set.of("TEXT", "IDENTIFIER", "INTEGER", "DECIMAL", "BOOLEAN");
    private static final Set<String> FIELD_TYPES =
            Set.of("TEXT", "INTEGER", "DECIMAL", "BOOLEAN");
    private static final Set<String> EFFECT_PARAMETER_TYPES =
            Set.of("REFERENCE", "INTEGER", "DECIMAL", "TEXT", "BOOLEAN");
    private static final Set<String> REFERENCE_KINDS =
            Set.of("CHARACTER", "ITEM_TEMPLATE", "MAP", "NODE");
    private static final Set<String> CHECK_ALGORITHMS = Set.of(
            "ABILITY_MODIFIER_V1",
            "SKILL_BONUS_V1",
            "SAVING_THROW_BONUS_V1",
            "MANUAL_MODIFIER_V1");
    private static final Set<String> ROLL_ALGORITHMS = Set.of(
            "ONLY_CANDIDATE_V1",
            "HIGHEST_FIRST_ON_TIE_V1",
            "LOWEST_FIRST_ON_TIE_V1");
    private static final Set<String> EFFECT_ALGORITHMS = Set.of(
            "ADJUST_CURRENT_HP_CLAMP_V1",
            "GRANT_MODULE_ITEM_V1",
            "GRANT_TEMPORARY_ITEM_V1",
            "SET_ENTITY_NODE_POSITION_V1",
            "APPEND_EVENT_MESSAGE_V1");

    private ModuleCatalogValidatorV1() {
    }

    static void validate(ModuleCatalog catalog) throws ModuleCanonicalException {
        if (catalog == null) {
            throw invalid();
        }

        validateRelease(catalog.release());
        Map<String, ModuleCatalog.FieldDefinition> fields = index(
                catalog.fieldDefinitions(),
                ModuleCatalog.FieldDefinition::fieldKey,
                ModuleCatalogValidatorV1::validateFieldDefinition);
        Map<String, ModuleCatalog.ClassDefinition> classes = index(
                catalog.classDefinitions(),
                ModuleCatalog.ClassDefinition::classKey,
                row -> {
                    stableKey(row.classKey());
                    nonBlankText(row.displayName());
                });
        Map<String, ModuleCatalog.ProficiencyTier> proficiencyTiers = index(
                catalog.proficiencyTiers(),
                ModuleCatalog.ProficiencyTier::proficiencyKey,
                ModuleCatalogValidatorV1::validateProficiencyTier);
        requireUniqueValues(proficiencyTiers.values().stream()
                .map(ModuleCatalog.ProficiencyTier::enumCode).toList());
        Map<String, ModuleCatalog.SkillDefinition> skills = index(
                catalog.skillDefinitions(),
                ModuleCatalog.SkillDefinition::skillKey,
                row -> {
                    stableKey(row.skillKey());
                    nonBlankText(row.displayName());
                    stableKey(row.abilityFieldKey());
                });
        Map<String, ModuleCatalog.SaveDefinition> saves = index(
                catalog.saveDefinitions(),
                ModuleCatalog.SaveDefinition::saveKey,
                row -> {
                    stableKey(row.saveKey());
                    stableKey(row.abilityFieldKey());
                });
        index(
                catalog.itemTemplates(),
                ModuleCatalog.ItemTemplate::itemKey,
                row -> {
                    stableKey(row.itemKey());
                    nonBlankText(row.displayName());
                    nonBlankText(row.description());
                });
        Map<String, ModuleCatalog.EntityTemplate> templates = index(
                catalog.entityTemplates(),
                ModuleCatalog.EntityTemplate::templateKey,
                row -> {
                    stableKey(row.templateKey());
                    nonBlankText(row.displayName());
                });
        Map<String, ModuleCatalog.CheckDefinition> checks = index(
                catalog.checkDefinitions(),
                ModuleCatalog.CheckDefinition::checkKey,
                ModuleCatalogValidatorV1::validateCheckDefinition);
        requireUniqueValues(checks.values().stream()
                .map(ModuleCatalog.CheckDefinition::enumCode).toList());
        Map<String, ModuleCatalog.RollMode> rollModes = index(
                catalog.rollModes(),
                ModuleCatalog.RollMode::rollModeKey,
                ModuleCatalogValidatorV1::validateRollMode);
        requireUniqueValues(rollModes.values().stream()
                .map(ModuleCatalog.RollMode::enumCode).toList());
        Map<String, ModuleCatalog.EventTemplate> events = index(
                catalog.eventTemplates(),
                ModuleCatalog.EventTemplate::eventKey,
                row -> {
                    stableKey(row.eventKey());
                    nonBlankText(row.displayName());
                });
        Map<String, ModuleCatalog.EffectDefinition> effects = index(
                catalog.effectDefinitions(),
                ModuleCatalog.EffectDefinition::effectKey,
                ModuleCatalogValidatorV1::validateEffectDefinition);
        Map<String, ModuleCatalog.MapDefinition> maps = index(
                catalog.mapDefinitions(),
                ModuleCatalog.MapDefinition::mapKey,
                row -> {
                    stableKey(row.mapKey());
                    known(row.mapType(), Set.of("NODE"));
                });

        validateRuleConstants(catalog.ruleConstants());
        validateFieldReferences(fields);
        validateProficiencyBonusBands(catalog.proficiencyBonusBands());
        validateAbilityReferences(skills, saves, fields);
        validateEntityTemplateValues(catalog.entityTemplateValues(), templates, fields);
        validateEntityTemplateClassLevels(
                catalog.entityTemplateClassLevels(), templates, classes);
        validateEntityTemplateProficiencies(
                catalog.entityTemplateProficiencies(),
                templates, skills, saves, proficiencyTiers);
        validateEventChecks(catalog.eventChecks(), events, checks);
        validateEventEffects(catalog.eventEffects(), events, effects);
        validateEffectParameters(catalog.effectParameters(), effects);
        Map<String, ModuleCatalog.MapNode> nodes =
                validateMapNodes(catalog.mapNodes(), maps);
        validateMapConnections(catalog.mapConnections(), maps, nodes);

    }

    private static void validateRelease(ModuleCatalog.Release release)
            throws ModuleCanonicalException {
        if (release == null) {
            throw invalid();
        }
        stableKey(release.moduleKey());
        identifier(release.releaseVersion());
        if (release.canonicalFormatVersion() != ModuleCanonicalEncoderV1.FORMAT_VERSION
                || !"SHA-256".equals(release.hashAlgorithm())) {
            throw invalid();
        }
    }

    private static void validateRuleConstants(List<ModuleCatalog.RuleConstant> rows)
            throws ModuleCanonicalException {
        index(rows, ModuleCatalog.RuleConstant::constantKey, row -> {
            stableKey(row.constantKey());
            known(row.valueType(), VALUE_TYPES);
            scalarMatches(row.valueType(), row.value(), false);
        });
    }

    private static void validateFieldDefinition(ModuleCatalog.FieldDefinition row)
            throws ModuleCanonicalException {
        stableKey(row.fieldKey());
        nonBlankText(row.displayName());
        known(row.dataType(), FIELD_TYPES);
        scalarMatches(row.dataType(), row.defaultValue(), false);
        validateFieldBound(row.dataType(), row.minimumValue());
        validateFieldBound(row.dataType(), row.maximumValue());
        compareBounds(row.minimumValue(), row.maximumValue());
        requireWithinBounds(row.defaultValue(), row.minimumValue(), row.maximumValue());
        if (row.dependentMaxFieldKey() != null) {
            stableKey(row.dependentMaxFieldKey());
            if (!"INTEGER".equals(row.dataType()) || row.maximumValue() != null) {
                throw invalid();
            }
        }
        if (row.unit() != null) {
            text(row.unit());
        }
        nonBlankText(row.description());
    }

    private static void validateFieldBound(
            String dataType, ModuleCatalog.ScalarValue bound) throws ModuleCanonicalException {
        if (bound == null) {
            return;
        }
        switch (dataType) {
            case "INTEGER" -> requireType(bound, ModuleCatalog.IntegerValue.class);
            case "DECIMAL" -> requireType(bound, ModuleCatalog.DecimalValue.class);
            case "TEXT", "BOOLEAN" -> throw invalid();
            default -> throw invalid();
        }
    }

    private static void validateFieldReferences(
            Map<String, ModuleCatalog.FieldDefinition> fields) throws ModuleCanonicalException {
        for (ModuleCatalog.FieldDefinition field : fields.values()) {
            if (field.dependentMaxFieldKey() != null) {
                ModuleCatalog.FieldDefinition maximum = fields.get(field.dependentMaxFieldKey());
                if (maximum == null || !"INTEGER".equals(maximum.dataType())) {
                    throw invalid();
                }
                requireAtMost(field.defaultValue(), maximum.defaultValue());
            }
        }
    }

    private static void validateProficiencyTier(ModuleCatalog.ProficiencyTier row)
            throws ModuleCanonicalException {
        stableKey(row.proficiencyKey());
        identifier(row.enumCode());
        known(row.roundingAlgorithm(), Set.of("EXACT", "FLOOR"));
        if (row.denominator() <= 0 || row.numerator() < 0) {
            throw invalid();
        }
    }

    private static void validateProficiencyBonusBands(
            List<ModuleCatalog.ProficiencyBonusBand> rows) throws ModuleCanonicalException {
        Set<String> keys = new HashSet<>();
        List<ModuleCatalog.ProficiencyBonusBand> sorted = rows.stream()
                .sorted(java.util.Comparator.comparingInt(
                        ModuleCatalog.ProficiencyBonusBand::minimumTotalLevel))
                .toList();
        int previousMaximum = -1;
        for (ModuleCatalog.ProficiencyBonusBand row : sorted) {
            if (row.minimumTotalLevel() < 0
                    || row.minimumTotalLevel() > row.maximumTotalLevel()
                    || row.maximumTotalLevel() > 20
                    || row.minimumTotalLevel() <= previousMaximum
                    || row.bonus() <= 0
                    || !keys.add(row.minimumTotalLevel() + "\0"
                            + row.maximumTotalLevel() + "\0" + row.bonus())) {
                throw invalid();
            }
            previousMaximum = row.maximumTotalLevel();
        }
    }

    private static void validateAbilityReferences(
            Map<String, ModuleCatalog.SkillDefinition> skills,
            Map<String, ModuleCatalog.SaveDefinition> saves,
            Map<String, ModuleCatalog.FieldDefinition> fields) throws ModuleCanonicalException {
        for (ModuleCatalog.SkillDefinition skill : skills.values()) {
            requireIntegerField(fields, skill.abilityFieldKey());
        }
        for (ModuleCatalog.SaveDefinition save : saves.values()) {
            requireIntegerField(fields, save.abilityFieldKey());
        }
    }

    private static void requireIntegerField(
            Map<String, ModuleCatalog.FieldDefinition> fields, String key)
            throws ModuleCanonicalException {
        ModuleCatalog.FieldDefinition field = fields.get(key);
        if (field == null || !"INTEGER".equals(field.dataType())) {
            throw invalid();
        }
    }

    private static void validateEntityTemplateValues(
            List<ModuleCatalog.EntityTemplateValue> rows,
            Map<String, ModuleCatalog.EntityTemplate> templates,
            Map<String, ModuleCatalog.FieldDefinition> fields) throws ModuleCanonicalException {
        Set<String> keys = new HashSet<>();
        Map<String, ModuleCatalog.EntityTemplateValue> indexed = new HashMap<>();
        for (ModuleCatalog.EntityTemplateValue row : rows) {
            stableKey(row.templateKey());
            stableKey(row.fieldKey());
            known(row.valueType(), FIELD_TYPES);
            ModuleCatalog.FieldDefinition field = fields.get(row.fieldKey());
            if (!templates.containsKey(row.templateKey())
                    || field == null
                    || !row.valueType().equals(field.dataType())
                    || !keys.add(pair(row.templateKey(), row.fieldKey()))
                    || indexed.putIfAbsent(pair(row.templateKey(), row.fieldKey()), row) != null) {
                throw invalid();
            }
            scalarMatches(row.valueType(), row.value(), false);
            requireWithinBounds(row.value(), field.minimumValue(), field.maximumValue());
        }
        for (ModuleCatalog.EntityTemplateValue row : rows) {
            ModuleCatalog.FieldDefinition field = fields.get(row.fieldKey());
            if (field.dependentMaxFieldKey() != null) {
                ModuleCatalog.EntityTemplateValue maximum = indexed.get(
                        pair(row.templateKey(), field.dependentMaxFieldKey()));
                if (maximum == null) {
                    throw invalid();
                }
                requireAtMost(row.value(), maximum.value());
            }
        }
    }

    private static void validateEntityTemplateClassLevels(
            List<ModuleCatalog.EntityTemplateClassLevel> rows,
            Map<String, ModuleCatalog.EntityTemplate> templates,
            Map<String, ModuleCatalog.ClassDefinition> classes) throws ModuleCanonicalException {
        Set<String> keys = new HashSet<>();
        Map<String, Integer> totalLevels = new HashMap<>();
        for (ModuleCatalog.EntityTemplateClassLevel row : rows) {
            stableKey(row.templateKey());
            stableKey(row.classKey());
            if (!templates.containsKey(row.templateKey())
                    || !classes.containsKey(row.classKey())
                    || row.level() < 1
                    || row.level() > 20
                    || !keys.add(pair(row.templateKey(), row.classKey()))) {
                throw invalid();
            }
            int current = totalLevels.getOrDefault(row.templateKey(), 0);
            if (current > 20 - row.level()) {
                throw invalid();
            }
            totalLevels.put(row.templateKey(), current + row.level());
        }
    }

    private static void validateEntityTemplateProficiencies(
            List<ModuleCatalog.EntityTemplateProficiency> rows,
            Map<String, ModuleCatalog.EntityTemplate> templates,
            Map<String, ModuleCatalog.SkillDefinition> skills,
            Map<String, ModuleCatalog.SaveDefinition> saves,
            Map<String, ModuleCatalog.ProficiencyTier> tiers)
            throws ModuleCanonicalException {
        Set<String> keys = new HashSet<>();
        for (ModuleCatalog.EntityTemplateProficiency row : rows) {
            stableKey(row.templateKey());
            known(row.targetKind(), Set.of("SKILL", "SAVING_THROW"));
            stableKey(row.targetKey());
            stableKey(row.proficiencyKey());
            boolean targetExists = "SKILL".equals(row.targetKind())
                    ? skills.containsKey(row.targetKey())
                    : saves.containsKey(row.targetKey());
            String key = row.templateKey() + "\0" + row.targetKind() + "\0" + row.targetKey();
            if (!templates.containsKey(row.templateKey())
                    || !targetExists
                    || !tiers.containsKey(row.proficiencyKey())
                    || !keys.add(key)) {
                throw invalid();
            }
        }
    }

    private static void validateCheckDefinition(ModuleCatalog.CheckDefinition row)
            throws ModuleCanonicalException {
        stableKey(row.checkKey());
        identifier(row.enumCode());
        known(row.modifierAlgorithm(), CHECK_ALGORITHMS);
        String expected = switch (row.enumCode()) {
            case "ABILITY" -> "ABILITY_MODIFIER_V1";
            case "SKILL" -> "SKILL_BONUS_V1";
            case "SAVING_THROW" -> "SAVING_THROW_BONUS_V1";
            case "MANUAL" -> "MANUAL_MODIFIER_V1";
            default -> throw invalid();
        };
        if (!expected.equals(row.modifierAlgorithm())) {
            throw invalid();
        }
    }

    private static void validateRollMode(ModuleCatalog.RollMode row)
            throws ModuleCanonicalException {
        stableKey(row.rollModeKey());
        identifier(row.enumCode());
        known(row.selectionAlgorithm(), ROLL_ALGORITHMS);
        int expectedCandidates = switch (row.selectionAlgorithm()) {
            case "ONLY_CANDIDATE_V1" -> 1;
            case "HIGHEST_FIRST_ON_TIE_V1", "LOWEST_FIRST_ON_TIE_V1" -> 2;
            default -> throw invalid();
        };
        if (row.candidateCount() != expectedCandidates) {
            throw invalid();
        }
    }

    private static void validateEventChecks(
            List<ModuleCatalog.EventCheck> rows,
            Map<String, ModuleCatalog.EventTemplate> events,
            Map<String, ModuleCatalog.CheckDefinition> checks) throws ModuleCanonicalException {
        Set<String> keys = new HashSet<>();
        for (ModuleCatalog.EventCheck row : rows) {
            stableKey(row.eventKey());
            stableKey(row.checkKey());
            if (!events.containsKey(row.eventKey())
                    || !checks.containsKey(row.checkKey())
                    || !keys.add(pair(row.eventKey(), row.checkKey()))) {
                throw invalid();
            }
        }
    }

    private static void validateEventEffects(
            List<ModuleCatalog.EventEffect> rows,
            Map<String, ModuleCatalog.EventTemplate> events,
            Map<String, ModuleCatalog.EffectDefinition> effects) throws ModuleCanonicalException {
        Set<String> keys = new HashSet<>();
        for (ModuleCatalog.EventEffect row : rows) {
            stableKey(row.eventKey());
            stableKey(row.effectKey());
            if (!events.containsKey(row.eventKey())
                    || !effects.containsKey(row.effectKey())
                    || !keys.add(pair(row.eventKey(), row.effectKey()))) {
                throw invalid();
            }
        }
    }

    private static void validateEffectDefinition(ModuleCatalog.EffectDefinition row)
            throws ModuleCanonicalException {
        stableKey(row.effectKey());
        known(row.executionAlgorithm(), EFFECT_ALGORITHMS);
    }

    private static void validateEffectParameters(
            List<ModuleCatalog.EffectParameter> rows,
            Map<String, ModuleCatalog.EffectDefinition> effects) throws ModuleCanonicalException {
        Set<String> keys = new HashSet<>();
        Set<String> orders = new HashSet<>();
        for (ModuleCatalog.EffectParameter row : rows) {
            stableKey(row.effectKey());
            stableKey(row.parameterKey());
            known(row.dataType(), EFFECT_PARAMETER_TYPES);
            if (!effects.containsKey(row.effectKey())
                    || row.parameterOrder() <= 0
                    || !keys.add(pair(row.effectKey(), row.parameterKey()))
                    || !orders.add(pair(row.effectKey(), Integer.toString(row.parameterOrder())))) {
                throw invalid();
            }
            validateEffectParameterShape(row);
        }
    }

    private static void validateEffectParameterShape(ModuleCatalog.EffectParameter row)
            throws ModuleCanonicalException {
        switch (row.dataType()) {
            case "REFERENCE" -> {
                known(row.referenceKind(), REFERENCE_KINDS);
                requireNulls(row.minimumValue(), row.maximumValue(),
                        row.textNormalization(), row.rejectControlCharacters());
            }
            case "INTEGER" -> {
                requireNulls(row.referenceKind(), row.textNormalization(),
                        row.rejectControlCharacters());
                nullableType(row.minimumValue(), ModuleCatalog.IntegerValue.class);
                nullableType(row.maximumValue(), ModuleCatalog.IntegerValue.class);
                compareBounds(row.minimumValue(), row.maximumValue());
            }
            case "DECIMAL" -> {
                requireNulls(row.referenceKind(), row.textNormalization(),
                        row.rejectControlCharacters());
                nullableType(row.minimumValue(), ModuleCatalog.DecimalValue.class);
                nullableType(row.maximumValue(), ModuleCatalog.DecimalValue.class);
                compareBounds(row.minimumValue(), row.maximumValue());
            }
            case "TEXT" -> {
                requireNulls(row.referenceKind());
                nullableType(row.minimumValue(), ModuleCatalog.IntegerValue.class);
                nullableType(row.maximumValue(), ModuleCatalog.IntegerValue.class);
                compareBounds(row.minimumValue(), row.maximumValue());
                if (row.minimumValue() instanceof ModuleCatalog.IntegerValue minimum
                        && minimum.value() < 0) {
                    throw invalid();
                }
                known(row.textNormalization(), Set.of("NFC", "TRIM_THEN_NFC"));
                if (row.rejectControlCharacters() == null) {
                    throw invalid();
                }
            }
            case "BOOLEAN" -> requireNulls(
                    row.referenceKind(), row.minimumValue(), row.maximumValue(),
                    row.textNormalization(), row.rejectControlCharacters());
            default -> throw invalid();
        }
    }

    private static Map<String, ModuleCatalog.MapNode> validateMapNodes(
            List<ModuleCatalog.MapNode> rows,
            Map<String, ModuleCatalog.MapDefinition> maps) throws ModuleCanonicalException {
        Map<String, ModuleCatalog.MapNode> nodes = new HashMap<>();
        for (ModuleCatalog.MapNode row : rows) {
            stableKey(row.mapKey());
            stableKey(row.nodeKey());
            nonBlankText(row.displayName());
            String key = pair(row.mapKey(), row.nodeKey());
            if (!maps.containsKey(row.mapKey()) || nodes.putIfAbsent(key, row) != null) {
                throw invalid();
            }
        }
        return nodes;
    }

    private static void validateMapConnections(
            List<ModuleCatalog.MapConnection> rows,
            Map<String, ModuleCatalog.MapDefinition> maps,
            Map<String, ModuleCatalog.MapNode> nodes) throws ModuleCanonicalException {
        Set<String> keys = new HashSet<>();
        for (ModuleCatalog.MapConnection row : rows) {
            stableKey(row.mapKey());
            stableKey(row.endpointLowKey());
            stableKey(row.endpointHighKey());
            String key = row.mapKey() + "\0" + row.endpointLowKey()
                    + "\0" + row.endpointHighKey();
            if (!maps.containsKey(row.mapKey())
                    || !nodes.containsKey(pair(row.mapKey(), row.endpointLowKey()))
                    || !nodes.containsKey(pair(row.mapKey(), row.endpointHighKey()))
                    || ModuleCanonicalEncoderV1.compareCanonicalUtf8(
                                    row.endpointLowKey(), row.endpointHighKey()) >= 0
                    || !keys.add(key)) {
                throw invalid();
            }
        }
    }

    private static void scalarMatches(
            String type, ModuleCatalog.ScalarValue value, boolean nullable)
            throws ModuleCanonicalException {
        if (value == null) {
            if (!nullable) {
                throw invalid();
            }
            return;
        }
        switch (type) {
            case "TEXT" -> {
                ModuleCatalog.TextValue typed = requireType(value, ModuleCatalog.TextValue.class);
                text(typed.value());
            }
            case "IDENTIFIER" -> {
                ModuleCatalog.IdentifierValue typed =
                        requireType(value, ModuleCatalog.IdentifierValue.class);
                identifier(typed.value());
            }
            case "INTEGER" -> requireType(value, ModuleCatalog.IntegerValue.class);
            case "DECIMAL" -> {
                ModuleCatalog.DecimalValue typed =
                        requireType(value, ModuleCatalog.DecimalValue.class);
                if (typed.value() == null) {
                    throw invalid();
                }
            }
            case "BOOLEAN" -> requireType(value, ModuleCatalog.BooleanValue.class);
            default -> throw invalid();
        }
    }

    private static void compareBounds(
            ModuleCatalog.ScalarValue minimum,
            ModuleCatalog.ScalarValue maximum) throws ModuleCanonicalException {
        if (minimum == null || maximum == null) {
            return;
        }
        if (minimum instanceof ModuleCatalog.IntegerValue low
                && maximum instanceof ModuleCatalog.IntegerValue high) {
            if (low.value() > high.value()) {
                throw invalid();
            }
            return;
        }
        if (minimum instanceof ModuleCatalog.DecimalValue low
                && maximum instanceof ModuleCatalog.DecimalValue high) {
            BigDecimal lowValue = low.value();
            BigDecimal highValue = high.value();
            if (lowValue == null || highValue == null || lowValue.compareTo(highValue) > 0) {
                throw invalid();
            }
            return;
        }
        throw invalid();
    }

    private static void requireWithinBounds(
            ModuleCatalog.ScalarValue value,
            ModuleCatalog.ScalarValue minimum,
            ModuleCatalog.ScalarValue maximum) throws ModuleCanonicalException {
        if (value instanceof ModuleCatalog.IntegerValue integer) {
            if (minimum instanceof ModuleCatalog.IntegerValue low
                    && integer.value() < low.value()) {
                throw invalid();
            }
            if (maximum instanceof ModuleCatalog.IntegerValue high
                    && integer.value() > high.value()) {
                throw invalid();
            }
        } else if (value instanceof ModuleCatalog.DecimalValue decimal) {
            if (minimum instanceof ModuleCatalog.DecimalValue low
                    && decimal.value().compareTo(low.value()) < 0) {
                throw invalid();
            }
            if (maximum instanceof ModuleCatalog.DecimalValue high
                    && decimal.value().compareTo(high.value()) > 0) {
                throw invalid();
            }
        }
    }

    private static void requireAtMost(
            ModuleCatalog.ScalarValue value,
            ModuleCatalog.ScalarValue maximum) throws ModuleCanonicalException {
        if (!(value instanceof ModuleCatalog.IntegerValue integer)
                || !(maximum instanceof ModuleCatalog.IntegerValue upper)
                || integer.value() > upper.value()) {
            throw invalid();
        }
    }

    private static void requireUniqueValues(List<String> values)
            throws ModuleCanonicalException {
        if (new HashSet<>(values).size() != values.size()) {
            throw invalid();
        }
    }

    private static <T> Map<String, T> index(
            List<T> rows,
            Function<T, String> key,
            CheckedConsumer<T> validator) throws ModuleCanonicalException {
        Map<String, T> indexed = new HashMap<>();
        for (T row : rows) {
            validator.accept(row);
            String rowKey = key.apply(row);
            if (indexed.putIfAbsent(rowKey, row) != null) {
                throw invalid();
            }
        }
        return indexed;
    }

    private static void stableKey(String value) throws ModuleCanonicalException {
        if (value == null || !STABLE_KEY.matcher(value).matches()) {
            throw invalid();
        }
    }

    private static void identifier(String value) throws ModuleCanonicalException {
        ModuleCanonicalEncoderV1.strictAscii(value, false);
    }

    private static void text(String value) throws ModuleCanonicalException {
        ModuleCanonicalEncoderV1.normalizeText(value);
    }

    private static void nonBlankText(String value) throws ModuleCanonicalException {
        text(value);
        if (value.trim().isEmpty()) {
            throw invalid();
        }
    }

    private static void known(String value, Set<String> allowed)
            throws ModuleCanonicalException {
        identifier(value);
        if (!allowed.contains(value)) {
            throw invalid();
        }
    }

    private static void nullableType(Object value, Class<?> expected)
            throws ModuleCanonicalException {
        if (value != null && !expected.isInstance(value)) {
            throw invalid();
        }
    }

    private static <T> T requireType(Object value, Class<T> expected)
            throws ModuleCanonicalException {
        if (!expected.isInstance(value)) {
            throw invalid();
        }
        return expected.cast(value);
    }

    private static void requireNulls(Object... values) throws ModuleCanonicalException {
        for (Object value : values) {
            if (value != null) {
                throw invalid();
            }
        }
    }

    private static String pair(String left, String right) {
        return left + "\0" + right;
    }

    private static ModuleCanonicalException invalid() {
        return new ModuleCanonicalException();
    }

    @FunctionalInterface
    private interface CheckedConsumer<T> {
        void accept(T value) throws ModuleCanonicalException;
    }
}
