package com.dndtool.service;

import com.dndtool.persistence.ModuleCatalog;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.UUID;

/**
 * Defines the untrusted check command shape before any roll or persistence occurs.
 *
 * <p>The client request contains stable keys and typed parameter values only. Dice candidates,
 * selected candidates, derived modifiers, algorithms and effect implementations are resolved
 * from the already verified frozen module and never accepted as request fields.
 */
public final class CheckRequestPolicy {
    private static final Set<String> CHECK_ALGORITHMS = Set.of(
            "ABILITY_MODIFIER_V1",
            "SKILL_BONUS_V1",
            "SAVING_THROW_BONUS_V1",
            "MANUAL_MODIFIER_V1");
    private static final Set<String> ROLL_ALGORITHMS = Set.of(
            "ONLY_CANDIDATE_V1",
            "HIGHEST_FIRST_ON_TIE_V1",
            "LOWEST_FIRST_ON_TIE_V1");
    private static final Set<String> PARAMETER_TYPES = Set.of(
            "REFERENCE", "INTEGER", "DECIMAL", "TEXT", "BOOLEAN");
    private static final Set<String> EFFECT_ALGORITHMS = Set.of(
            "ADJUST_CURRENT_HP_CLAMP_V1",
            "GRANT_MODULE_ITEM_V1",
            "GRANT_TEMPORARY_ITEM_V1",
            "SET_ENTITY_NODE_POSITION_V1",
            "APPEND_EVENT_MESSAGE_V1");
    private static final String POSITION_EFFECT = "effect.set_entity_position";

    private final Map<String, ModuleCatalog.CheckDefinition> checks;
    private final Map<String, ModuleCatalog.RollMode> rollModes;
    private final Map<String, ModuleCatalog.EffectDefinition> effects;
    private final Map<String, List<ModuleCatalog.EffectParameter>> parameters;
    private final Set<String> modifierSources;
    private final Set<String> positionNodeKeys;

    /** Requires a catalog that has already passed full release and content-hash verification. */
    public CheckRequestPolicy(ModuleCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        if (!"RELEASED".equals(catalog.release().releaseStatus())) {
            throw invalidCatalog();
        }
        checks = uniqueIndex(catalog.checkDefinitions(),
                ModuleCatalog.CheckDefinition::checkKey);
        rollModes = uniqueIndex(catalog.rollModes(), ModuleCatalog.RollMode::rollModeKey);
        effects = uniqueIndex(catalog.effectDefinitions(),
                ModuleCatalog.EffectDefinition::effectKey);
        parameters = indexParameters(catalog.effectParameters(), effects);
        modifierSources = modifierSources(catalog);
        positionNodeKeys = positionNodeKeys(catalog);
        validateAlgorithms();
    }

    /** Validates the untrusted request and returns a server-owned execution description. */
    public PreparedRequest prepare(ClientRequest request) {
        if (request == null) throw rejected(Rejection.INVALID_REQUEST);
        ModuleCatalog.CheckDefinition check = checks.get(request.checkKey());
        if (check == null) throw rejected(Rejection.CHECK_NOT_ALLOWED);
        ModuleCatalog.RollMode rollMode = rollModes.get(request.rollModeKey());
        if (rollMode == null) throw rejected(Rejection.ROLL_MODE_NOT_ALLOWED);
        if (request.difficultyClass() < 0 || request.difficultyClass() > 60) {
            throw rejected(Rejection.INVALID_REQUEST);
        }

        String normalizedManualName = validateModifierSource(check, request);
        List<PreparedEffect> preparedEffects = prepareEffects(request.effects());
        return new PreparedRequest(
                check.checkKey(),
                check.enumCode(),
                check.modifierAlgorithm(),
                rollMode.rollModeKey(),
                rollMode.enumCode(),
                rollMode.selectionAlgorithm(),
                rollMode.candidateCount(),
                request.modifierSourceKey(),
                request.manualModifier(),
                normalizedManualName,
                request.difficultyClass(),
                preparedEffects);
    }

    private String validateModifierSource(
            ModuleCatalog.CheckDefinition check, ClientRequest request) {
        return switch (check.enumCode()) {
            case "ABILITY", "SKILL", "SAVING_THROW" -> {
                if (request.manualName() != null || request.manualModifier() != null
                        || request.modifierSourceKey() == null
                        || !modifierSources.contains(request.modifierSourceKey())
                        || !request.modifierSourceKey().startsWith(sourcePrefix(check.enumCode()))) {
                    throw rejected(Rejection.INVALID_REQUEST);
                }
                yield null;
            }
            case "MANUAL" -> {
                if (request.modifierSourceKey() != null
                        || request.manualModifier() == null
                        || request.manualModifier() < -99
                        || request.manualModifier() > 99) {
                    throw rejected(Rejection.INVALID_REQUEST);
                }
                try {
                    yield CheckTextPolicy.normalizeManualName(request.manualName());
                } catch (IllegalArgumentException exception) {
                    throw rejected(Rejection.INVALID_REQUEST);
                }
            }
            default -> throw invalidCatalog();
        };
    }

    private List<PreparedEffect> prepareEffects(List<EffectRequest> requests) {
        if (requests == null) throw rejected(Rejection.INVALID_REQUEST);
        return requests.stream().map(this::prepareEffect).toList();
    }

    private PreparedEffect prepareEffect(EffectRequest request) {
        if (request == null || request.effectKey() == null) {
            throw rejected(Rejection.EFFECT_NOT_ALLOWED);
        }
        ModuleCatalog.EffectDefinition definition = effects.get(request.effectKey());
        if (definition == null) throw rejected(Rejection.EFFECT_NOT_ALLOWED);
        List<ModuleCatalog.EffectParameter> definitions = parameters.getOrDefault(
                request.effectKey(), List.of());
        Map<String, ParameterInput> inputs = new HashMap<>();
        if (request.parameters() == null) throw rejected(Rejection.INVALID_REQUEST);
        for (ParameterInput input : request.parameters()) {
            if (input == null || input.parameterKey() == null
                    || inputs.putIfAbsent(input.parameterKey(), input) != null) {
                throw rejected(Rejection.PARAMETER_NOT_ALLOWED);
            }
        }
        if (inputs.size() != definitions.size()) {
            throw rejected(Rejection.PARAMETER_NOT_ALLOWED);
        }
        List<PreparedParameter> prepared = definitions.stream()
                .sorted(java.util.Comparator.comparingInt(ModuleCatalog.EffectParameter::parameterOrder))
                .map(definitionRow -> validateParameter(definitionRow, inputs.get(
                        definitionRow.parameterKey())))
                .toList();
        return new PreparedEffect(
                definition.effectKey(), definition.executionAlgorithm(), prepared);
    }

    private PreparedParameter validateParameter(
            ModuleCatalog.EffectParameter definition, ParameterInput input) {
        if (input == null || input.value() == null) {
            throw rejected(Rejection.PARAMETER_NOT_ALLOWED);
        }
        Value value = normalizeValue(definition, input.value());
        validatePositionReference(definition, value);
        return new PreparedParameter(
                definition.parameterKey(), definition.parameterOrder(), definition.dataType(), value);
    }

    private void validatePositionReference(
            ModuleCatalog.EffectParameter definition,
            Value value) {
        if (!POSITION_EFFECT.equals(definition.effectKey())) return;
        if (!(value instanceof ReferenceValue reference)) {
            throw rejected(Rejection.PARAMETER_TYPE_MISMATCH);
        }
        if ("map".equals(definition.parameterKey())) {
            if (!EncounterStateService.MAP_KEY.equals(reference.value())) {
                throw rejected(Rejection.PARAMETER_VALUE_INVALID);
            }
        } else if ("node".equals(definition.parameterKey())
                && !positionNodeKeys.contains(reference.value())) {
            throw rejected(Rejection.PARAMETER_VALUE_INVALID);
        }
    }

    private Value normalizeValue(ModuleCatalog.EffectParameter definition, Value value) {
        return switch (definition.dataType()) {
            case "REFERENCE" -> {
                if (!(value instanceof ReferenceValue reference)) {
                    throw rejected(Rejection.PARAMETER_TYPE_MISMATCH);
                }
                if (reference.value() == null
                        || reference.value().isBlank()
                        || reference.value().codePoints().anyMatch(Character::isISOControl)
                        || reference.value().codePointCount(0, reference.value().length()) > 255
                        || !validReferenceShape(definition.referenceKind(), reference.value())) {
                    throw rejected(Rejection.PARAMETER_VALUE_INVALID);
                }
                yield reference;
            }
            case "INTEGER" -> {
                if (!(value instanceof IntegerValue integer)) {
                    throw rejected(Rejection.PARAMETER_TYPE_MISMATCH);
                }
                if (!withinIntegerBounds(definition, integer.value())) {
                    throw rejected(Rejection.PARAMETER_VALUE_INVALID);
                }
                yield integer;
            }
            case "DECIMAL" -> {
                if (!(value instanceof DecimalValue decimal)) {
                    throw rejected(Rejection.PARAMETER_TYPE_MISMATCH);
                }
                if (!withinDecimalBounds(definition, decimal.value())
                        || decimal.value().precision() > 38
                        || decimal.value().scale() > 18) {
                    throw rejected(Rejection.PARAMETER_VALUE_INVALID);
                }
                yield decimal;
            }
            case "TEXT" -> {
                if (!(value instanceof TextValue text)) {
                    throw rejected(Rejection.PARAMETER_TYPE_MISMATCH);
                }
                yield normalizeText(definition, text.value());
            }
            case "BOOLEAN" -> {
                if (!(value instanceof BooleanValue)) {
                    throw rejected(Rejection.PARAMETER_TYPE_MISMATCH);
                }
                yield value;
            }
            default -> throw invalidCatalog();
        };
    }

    private static TextValue normalizeText(ModuleCatalog.EffectParameter definition, String value) {
        if (value == null) throw rejected(Rejection.PARAMETER_VALUE_INVALID);
        String normalized = switch (definition.textNormalization()) {
            case "NFC" -> Normalizer.normalize(value, Normalizer.Form.NFC);
            case "TRIM_THEN_NFC" -> Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
            default -> throw invalidCatalog();
        };
        int codePoints = normalized.codePointCount(0, normalized.length());
        long minimum = integerBound(definition.minimumValue(), Long.MIN_VALUE);
        long maximum = integerBound(definition.maximumValue(), Long.MAX_VALUE);
        if (codePoints < minimum || codePoints > maximum
                || Boolean.TRUE.equals(definition.rejectControlCharacters())
                        && normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw rejected(Rejection.PARAMETER_VALUE_INVALID);
        }
        return new TextValue(normalized);
    }

    private static boolean withinIntegerBounds(
            ModuleCatalog.EffectParameter definition, long value) {
        return value >= integerBound(definition.minimumValue(), Long.MIN_VALUE)
                && value <= integerBound(definition.maximumValue(), Long.MAX_VALUE);
    }

    private static boolean withinDecimalBounds(
            ModuleCatalog.EffectParameter definition, BigDecimal value) {
        if (value == null) return false;
        if (definition.minimumValue() instanceof ModuleCatalog.DecimalValue minimum
                && value.compareTo(minimum.value()) < 0) return false;
        return !(definition.maximumValue() instanceof ModuleCatalog.DecimalValue maximum)
                || value.compareTo(maximum.value()) <= 0;
    }

    private static long integerBound(ModuleCatalog.ScalarValue value, long fallback) {
        return value instanceof ModuleCatalog.IntegerValue integer ? integer.value() : fallback;
    }

    private static boolean validReferenceShape(String referenceKind, String value) {
        if ("CHARACTER".equals(referenceKind)) {
            try {
                return UUID.fromString(value).toString().equals(value);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return referenceKind != null
                && value.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    }

    private void validateAlgorithms() {
        for (ModuleCatalog.CheckDefinition row : checks.values()) {
            if (!CHECK_ALGORITHMS.contains(row.modifierAlgorithm())) throw invalidCatalog();
        }
        for (ModuleCatalog.RollMode row : rollModes.values()) {
            int expected = switch (row.selectionAlgorithm()) {
                case "ONLY_CANDIDATE_V1" -> 1;
                case "HIGHEST_FIRST_ON_TIE_V1", "LOWEST_FIRST_ON_TIE_V1" -> 2;
                default -> -1;
            };
            if (!ROLL_ALGORITHMS.contains(row.selectionAlgorithm())
                    || row.candidateCount() != expected) throw invalidCatalog();
        }
        for (ModuleCatalog.EffectDefinition row : effects.values()) {
            if (!EFFECT_ALGORITHMS.contains(row.executionAlgorithm())) throw invalidCatalog();
        }
    }

    private static Set<String> modifierSources(ModuleCatalog catalog) {
        Set<String> sources = new HashSet<>();
        catalog.fieldDefinitions().stream()
                .map(ModuleCatalog.FieldDefinition::fieldKey)
                .filter(Objects::nonNull)
                .filter(key -> key.startsWith("ability."))
                .forEach(sources::add);
        catalog.skillDefinitions().stream()
                .map(ModuleCatalog.SkillDefinition::skillKey)
                .filter(Objects::nonNull)
                .forEach(sources::add);
        catalog.saveDefinitions().stream()
                .map(ModuleCatalog.SaveDefinition::saveKey)
                .filter(Objects::nonNull)
                .forEach(sources::add);
        return Set.copyOf(sources);
    }

    private static Set<String> positionNodeKeys(ModuleCatalog catalog) {
        int matchingMaps = 0;
        boolean exactMap = false;
        for (ModuleCatalog.MapDefinition row : catalog.mapDefinitions()) {
            if (row == null) throw invalidCatalog();
            if (EncounterStateService.MAP_KEY.equals(row.mapKey())) {
                matchingMaps++;
                exactMap = EncounterStateService.MAP_TYPE.equals(row.mapType());
            }
        }
        if (matchingMaps != 1 || !exactMap) throw invalidCatalog();

        Set<String> nodes = new HashSet<>();
        for (ModuleCatalog.MapNode row : catalog.mapNodes()) {
            if (row == null) throw invalidCatalog();
            if (EncounterStateService.MAP_KEY.equals(row.mapKey())
                    && (row.nodeKey() == null
                    || !row.nodeKey().matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")
                    || !nodes.add(row.nodeKey()))) {
                throw invalidCatalog();
            }
        }
        if (nodes.isEmpty()) throw invalidCatalog();
        return Set.copyOf(nodes);
    }

    private static String sourcePrefix(String enumCode) {
        return switch (enumCode) {
            case "ABILITY" -> "ability.";
            case "SKILL" -> "skill.";
            case "SAVING_THROW" -> "save.";
            default -> "";
        };
    }

    private static Map<String, List<ModuleCatalog.EffectParameter>> indexParameters(
            List<ModuleCatalog.EffectParameter> rows,
            Map<String, ModuleCatalog.EffectDefinition> effects) {
        Map<String, List<ModuleCatalog.EffectParameter>> indexed = new HashMap<>();
        Set<String> keys = new HashSet<>();
        Set<String> orders = new HashSet<>();
        for (ModuleCatalog.EffectParameter row : rows) {
            if (row == null || !effects.containsKey(row.effectKey())
                    || !PARAMETER_TYPES.contains(row.dataType()) || row.parameterOrder() <= 0
                    || !keys.add(row.effectKey() + "\0" + row.parameterKey())
                    || !orders.add(row.effectKey() + "\0" + row.parameterOrder())) {
                throw invalidCatalog();
            }
            indexed.computeIfAbsent(row.effectKey(), ignored -> new java.util.ArrayList<>()).add(row);
        }
        indexed.replaceAll((ignored, value) -> List.copyOf(value));
        return Map.copyOf(indexed);
    }

    private static <T> Map<String, T> uniqueIndex(List<T> rows, Function<T, String> keyFunction) {
        Map<String, T> indexed = new HashMap<>();
        for (T row : rows) {
            if (row == null) throw invalidCatalog();
            String key = keyFunction.apply(row);
            if (key == null || indexed.putIfAbsent(key, row) != null) throw invalidCatalog();
        }
        return Map.copyOf(indexed);
    }

    private static IllegalStateException invalidCatalog() {
        return new IllegalStateException("Invalid host command request catalog");
    }

    private static PolicyException rejected(Rejection rejection) {
        return new PolicyException(rejection);
    }

    public record ClientRequest(
            String checkKey,
            String rollModeKey,
            String modifierSourceKey,
            Integer manualModifier,
            String manualName,
            int difficultyClass,
            List<EffectRequest> effects) {
        public ClientRequest {
            effects = effects == null ? null : List.copyOf(effects);
        }
    }

    public record EffectRequest(String effectKey, List<ParameterInput> parameters) {
        public EffectRequest {
            parameters = parameters == null ? null : List.copyOf(parameters);
        }
    }

    public record ParameterInput(String parameterKey, Value value) {
    }

    public sealed interface Value
            permits ReferenceValue, IntegerValue, DecimalValue, TextValue, BooleanValue {
    }

    public record ReferenceValue(String value) implements Value {
    }

    public record IntegerValue(long value) implements Value {
    }

    public record DecimalValue(BigDecimal value) implements Value {
    }

    public record TextValue(String value) implements Value {
    }

    public record BooleanValue(boolean value) implements Value {
    }

    public record PreparedRequest(
            String checkKey,
            String checkEnumCode,
            String modifierAlgorithm,
            String rollModeKey,
            String rollModeEnumCode,
            String selectionAlgorithm,
            int candidateCount,
            String modifierSourceKey,
            Integer manualModifier,
            String manualName,
            int difficultyClass,
            List<PreparedEffect> effects) {
        public PreparedRequest {
            effects = List.copyOf(effects);
        }
    }

    public record PreparedEffect(
            String effectKey, String executionAlgorithm, List<PreparedParameter> parameters) {
        public PreparedEffect {
            parameters = List.copyOf(parameters);
        }
    }

    public record PreparedParameter(
            String parameterKey, int parameterOrder, String dataType, Value value) {
    }

    public enum Rejection {
        INVALID_REQUEST,
        CHECK_NOT_ALLOWED,
        ROLL_MODE_NOT_ALLOWED,
        EFFECT_NOT_ALLOWED,
        EFFECT_NOT_SUPPORTED,
        PARAMETER_NOT_ALLOWED,
        PARAMETER_TYPE_MISMATCH,
        PARAMETER_VALUE_INVALID
    }

    public static final class PolicyException extends IllegalArgumentException {
        private final Rejection rejection;

        private PolicyException(Rejection rejection) {
            super(rejection.name());
            this.rejection = rejection;
        }

        public Rejection rejection() {
            return rejection;
        }
    }
}
