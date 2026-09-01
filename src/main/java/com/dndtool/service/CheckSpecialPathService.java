package com.dndtool.service;

import com.dndtool.persistence.CheckExecutionRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.NoteEventRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates and prepares the two host command paths that do not use a normal event/check pairing.
 *
 * <p>Callers invoke this only after selecting and integrity-checking the campaign's frozen module.
 * The returned persistence commands still participate in the surrounding lock and transaction.
 */
public final class CheckSpecialPathService {
    private static final String MANUAL_CHECK_KEY = "check.manual";
    private static final String NOTE_EVENT_KEY = "event.note";
    private static final String MESSAGE_EFFECT_KEY = "effect.append_event_message";
    private static final String POSITION_EFFECT_KEY = "effect.set_entity_position";
    private static final Set<String> ENABLED_MANUAL_EFFECTS = Set.of(
            "effect.adjust_current_hp",
            "effect.grant_module_item",
            "effect.grant_temporary_item",
            POSITION_EFFECT_KEY,
            MESSAGE_EFFECT_KEY);

    private final D20CheckCalculator calculator;
    private final Map<String, ModuleCatalog.EffectDefinition> effectDefinitions;

    /** Uses the production d20 source after validating the frozen special-path rules. */
    public CheckSpecialPathService(ModuleCatalog catalog) {
        this(catalog, null);
    }

    /** Accepts a deterministic source for tests; a null source selects the production source. */
    public CheckSpecialPathService(
            ModuleCatalog catalog, D20CheckCalculator.D20Source d20Source) {
        Objects.requireNonNull(catalog, "catalog");
        effectDefinitions = uniqueEffects(catalog.effectDefinitions());
        validateSpecialPathCatalog(catalog, effectDefinitions);
        calculator = d20Source == null
                ? new D20CheckCalculator(catalog)
                : new D20CheckCalculator(catalog, d20Source);
    }

    /**
     * Normalizes the manual label, approves global effects, then performs the server roll.
     */
    public ManualPreparation prepareManual(ManualRequest request) {
        if (request == null) throw rejected(Rejection.INVALID_REQUEST);
        final String manualName;
        final List<String> approvedEffects;
        try {
            manualName = CheckTextPolicy.normalizeManualName(request.manualName());
            approvedEffects = approveManualEffects(request.effectKeys());
        } catch (SpecialPathException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw rejected(Rejection.INVALID_REQUEST);
        }
        if (request.campaignId() <= 0
                || request.expectedEventTail() < 0
                || request.moduleReleaseId() <= 0
                || request.executorCharacterId() <= 0) {
            throw rejected(Rejection.INVALID_REQUEST);
        }

        final D20CheckCalculator.Result calculation;
        try {
            calculation = calculator.calculate(
                    request.rollModeKey(), request.modifierValue(), request.difficultyClass());
        } catch (IllegalArgumentException exception) {
            throw rejected(Rejection.INVALID_REQUEST);
        }
        CheckExecutionRepository.Command persistence = new CheckExecutionRepository.Command(
                request.campaignId(),
                request.expectedEventTail(),
                request.moduleReleaseId(),
                request.executorCharacterId(),
                null,
                MANUAL_CHECK_KEY,
                null,
                manualName,
                calculation);
        return new ManualPreparation(persistence, approvedEffects);
    }

    /** Normalizes a note after proving the frozen module has the one exact note relationship. */
    public NoteEventRepository.Command prepareNote(NoteRequest request) {
        if (request == null || request.campaignId() <= 0 || request.expectedEventTail() < 0) {
            throw rejected(Rejection.INVALID_REQUEST);
        }
        try {
            return new NoteEventRepository.Command(
                    request.campaignId(),
                    request.expectedEventTail(),
                    CheckTextPolicy.normalizeNoteMessage(request.message()));
        } catch (IllegalArgumentException exception) {
            throw rejected(Rejection.INVALID_REQUEST);
        }
    }

    private List<String> approveManualEffects(List<String> effectKeys) {
        if (effectKeys == null) throw rejected(Rejection.INVALID_REQUEST);
        for (String effectKey : effectKeys) {
            if (effectKey == null
                    || !effectDefinitions.containsKey(effectKey)
                    || !ENABLED_MANUAL_EFFECTS.contains(effectKey)) {
                throw rejected(Rejection.EFFECT_NOT_ALLOWED);
            }
        }
        return List.copyOf(effectKeys);
    }

    private static void validateSpecialPathCatalog(
            ModuleCatalog catalog,
            Map<String, ModuleCatalog.EffectDefinition> effectDefinitions) {
        if (!"RELEASED".equals(catalog.release().releaseStatus())
                || count(catalog.checkDefinitions(),
                        row -> MANUAL_CHECK_KEY.equals(row.checkKey())) != 1
                || count(catalog.checkDefinitions(), row -> MANUAL_CHECK_KEY.equals(row.checkKey())
                        && "MANUAL".equals(row.enumCode())
                        && "MANUAL_MODIFIER_V1".equals(row.modifierAlgorithm())) != 1
                || count(catalog.eventChecks(), row -> MANUAL_CHECK_KEY.equals(row.checkKey())) != 0
                || count(catalog.eventTemplates(), row -> NOTE_EVENT_KEY.equals(row.eventKey())) != 1
                || count(catalog.eventChecks(), row -> NOTE_EVENT_KEY.equals(row.eventKey())) != 0
                || count(catalog.eventEffects(), row -> NOTE_EVENT_KEY.equals(row.eventKey())
                        && MESSAGE_EFFECT_KEY.equals(row.effectKey())) != 1
                || count(catalog.eventEffects(), row -> NOTE_EVENT_KEY.equals(row.eventKey())) != 1
                || count(catalog.effectDefinitions(), row -> MESSAGE_EFFECT_KEY.equals(row.effectKey())
                        && "APPEND_EVENT_MESSAGE_V1".equals(row.executionAlgorithm())) != 1
                || !hasEffect(effectDefinitions,
                        "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1")
                || !hasEffect(effectDefinitions,
                        "effect.grant_module_item", "GRANT_MODULE_ITEM_V1")
                || !hasEffect(effectDefinitions,
                        "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1")
                || !hasEffect(effectDefinitions,
                        POSITION_EFFECT_KEY, "SET_ENTITY_NODE_POSITION_V1")
                || !hasEffect(effectDefinitions,
                        MESSAGE_EFFECT_KEY, "APPEND_EVENT_MESSAGE_V1")
                || !hasExactMessageParameter(catalog.effectParameters())) {
            throw new IllegalStateException("Invalid host command special-path module rules");
        }
    }

    private static boolean hasEffect(
            Map<String, ModuleCatalog.EffectDefinition> effects,
            String effectKey,
            String executionAlgorithm) {
        ModuleCatalog.EffectDefinition effect = effects.get(effectKey);
        return effect != null && executionAlgorithm.equals(effect.executionAlgorithm());
    }

    private static boolean hasExactMessageParameter(List<ModuleCatalog.EffectParameter> rows) {
        List<ModuleCatalog.EffectParameter> messageRows = rows.stream()
                .filter(Objects::nonNull)
                .filter(row -> MESSAGE_EFFECT_KEY.equals(row.effectKey()))
                .toList();
        if (messageRows.size() != 1) return false;
        ModuleCatalog.EffectParameter row = messageRows.getFirst();
        return "message".equals(row.parameterKey())
                && "TEXT".equals(row.dataType())
                && row.referenceKind() == null
                && integerValue(row.minimumValue()) == 1
                && integerValue(row.maximumValue()) == 500
                && "NFC".equals(row.textNormalization())
                && Boolean.TRUE.equals(row.rejectControlCharacters())
                && row.parameterOrder() == 1;
    }

    private static long integerValue(ModuleCatalog.ScalarValue value) {
        return value instanceof ModuleCatalog.IntegerValue integer ? integer.value() : Long.MIN_VALUE;
    }

    private static Map<String, ModuleCatalog.EffectDefinition> uniqueEffects(
            List<ModuleCatalog.EffectDefinition> rows) {
        Map<String, ModuleCatalog.EffectDefinition> indexed = new HashMap<>();
        for (ModuleCatalog.EffectDefinition row : rows) {
            if (row == null
                    || row.effectKey() == null
                    || indexed.putIfAbsent(row.effectKey(), row) != null) {
                throw new IllegalStateException("Invalid host command effect whitelist");
            }
        }
        return Map.copyOf(indexed);
    }

    private static <T> long count(List<T> rows, java.util.function.Predicate<T> predicate) {
        Set<T> nonNullRows = new HashSet<>();
        long matches = 0;
        for (T row : rows) {
            if (row == null || !nonNullRows.add(row)) {
                throw new IllegalStateException("Invalid duplicate special-path module row");
            }
            if (predicate.test(row)) matches++;
        }
        return matches;
    }

    private static SpecialPathException rejected(Rejection rejection) {
        return new SpecialPathException(rejection);
    }

    public record ManualRequest(
            long campaignId,
            long expectedEventTail,
            long moduleReleaseId,
            long executorCharacterId,
            String manualName,
            int modifierValue,
            String rollModeKey,
            int difficultyClass,
            List<String> effectKeys) {
    }

    public record ManualPreparation(
            CheckExecutionRepository.Command persistenceCommand,
            List<String> approvedEffectKeys) {
        public ManualPreparation {
            approvedEffectKeys = List.copyOf(approvedEffectKeys);
        }
    }

    public record NoteRequest(long campaignId, long expectedEventTail, String message) {
    }

    public enum Rejection {
        INVALID_REQUEST,
        EFFECT_NOT_ALLOWED,
        EFFECT_NOT_SUPPORTED
    }

    /** Stable internal rejection used by the future HTTP command mapping. */
    public static final class SpecialPathException extends IllegalArgumentException {
        private final Rejection rejection;

        private SpecialPathException(Rejection rejection) {
            super(rejection.name());
            this.rejection = rejection;
        }

        public Rejection rejection() {
            return rejection;
        }
    }
}
