package com.dndtool.service;

import com.dndtool.persistence.CheckEffectPlanRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.CheckEffectExecutionRepository;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Converts both validated effect branches into the closed host-command execution action set.
 *
 * <p>Target database ids must come from the pre-roll locking service. This class resolves
 * no database identity and performs no write, so malformed plans and frozen-map drift fail before
 * the persistence boundary is entered.
 */
public final class CheckEffectExecutionService {
    private static final String ADJUST_HP = "effect.adjust_current_hp";
    private static final String GRANT_MODULE_ITEM = "effect.grant_module_item";
    private static final String GRANT_TEMPORARY_ITEM = "effect.grant_temporary_item";
    private static final String SET_POSITION = "effect.set_entity_position";
    private static final String APPEND_MESSAGE = "effect.append_event_message";
    private static final Map<String, String> ALGORITHMS = Map.of(
            ADJUST_HP, "ADJUST_CURRENT_HP_CLAMP_V1",
            GRANT_MODULE_ITEM, "GRANT_MODULE_ITEM_V1",
            GRANT_TEMPORARY_ITEM, "GRANT_TEMPORARY_ITEM_V1",
            SET_POSITION, "SET_ENTITY_NODE_POSITION_V1",
            APPEND_MESSAGE, "APPEND_EVENT_MESSAGE_V1");

    private final Map<String, ModuleCatalog.EffectDefinition> effects;
    private final Map<String, ModuleCatalog.ItemTemplate> itemTemplates;
    private final Set<String> positionNodeKeys;

    public CheckEffectExecutionService(ModuleCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        if (!"RELEASED".equals(catalog.release().releaseStatus())) throw invalidCatalog();
        effects = uniqueIndex(catalog.effectDefinitions(), ModuleCatalog.EffectDefinition::effectKey);
        itemTemplates = uniqueIndex(catalog.itemTemplates(), ModuleCatalog.ItemTemplate::itemKey);
        positionNodeKeys = positionNodeKeys(catalog);
        ALGORITHMS.forEach((key, algorithm) -> {
            ModuleCatalog.EffectDefinition definition = effects.get(key);
            if (definition == null || !algorithm.equals(definition.executionAlgorithm())) {
                throw invalidCatalog();
            }
        });
    }

    /** Prepares both branches; the JDBC repository later selects only the stored result branch. */
    public CheckEffectExecutionRepository.Command prepare(
            long checkExecutionId,
            long campaignId,
            long moduleReleaseId,
            long gameEventId,
            List<TargetCharacter> targets,
            CheckEffectPlanRepository.BranchPlan success,
            CheckEffectPlanRepository.BranchPlan failure) {
        if (checkExecutionId <= 0 || campaignId <= 0 || moduleReleaseId <= 0 || gameEventId <= 0
                || success == null || failure == null) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        return prepareBranches(targets, success, failure).command(
                checkExecutionId, campaignId, moduleReleaseId, gameEventId);
    }

    /** Fully validates both branches before the transaction coordinator consumes randomness. */
    public PreparedBranches prepareBranches(
            List<TargetCharacter> targets,
            CheckEffectPlanRepository.BranchPlan success,
            CheckEffectPlanRepository.BranchPlan failure) {
        if (success == null || failure == null) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        Map<String, TargetCharacter> indexedTargets = targetIndex(targets);
        return new PreparedBranches(
                prepareBranch(success, CheckEffectPlanRepository.EffectBranch.SUCCESS, indexedTargets),
                prepareBranch(failure, CheckEffectPlanRepository.EffectBranch.FAILURE, indexedTargets));
    }

    /** Extracts the exact possible target set from both already policy-prepared branches. */
    public Set<String> requiredTargetKeys(
            CheckEffectPlanRepository.BranchPlan success,
            CheckEffectPlanRepository.BranchPlan failure) {
        java.util.HashSet<String> targets = new java.util.HashSet<>();
        collectTargetKeys(success, CheckEffectPlanRepository.EffectBranch.SUCCESS, targets);
        collectTargetKeys(failure, CheckEffectPlanRepository.EffectBranch.FAILURE, targets);
        return Set.copyOf(targets);
    }

    private static void collectTargetKeys(
            CheckEffectPlanRepository.BranchPlan plan,
            CheckEffectPlanRepository.EffectBranch expected,
            Set<String> targets) {
        if (plan == null || plan.branch() != expected) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        int order = 1;
        for (CheckEffectPlanRepository.EffectPlan effectPlan : plan.effects()) {
            if (effectPlan == null || effectPlan.effectOrder() != order++
                    || effectPlan.effect() == null) {
                throw rejected(Rejection.INVALID_EFFECT_PLAN);
            }
            CheckRequestPolicy.PreparedEffect effect = effectPlan.effect();
            String key = effect.effectKey();
            if (key == null
                    || !ALGORITHMS.getOrDefault(key, "").equals(effect.executionAlgorithm())) {
                throw rejected(Rejection.INVALID_EFFECT_PLAN);
            }
            if (!APPEND_MESSAGE.equals(key)) {
                String target = reference(effect, 0, "target_character", 1);
                if (!isCanonicalUuid(target)) throw rejected(Rejection.INVALID_EFFECT_PLAN);
                targets.add(target);
            }
        }
    }

    private CheckEffectExecutionRepository.BranchActions prepareBranch(
            CheckEffectPlanRepository.BranchPlan plan,
            CheckEffectPlanRepository.EffectBranch expected,
            Map<String, TargetCharacter> targets) {
        if (plan.branch() != expected) throw rejected(Rejection.INVALID_EFFECT_PLAN);
        int order = 1;
        int messageCount = 0;
        java.util.ArrayList<CheckEffectExecutionRepository.Action> actions =
                new java.util.ArrayList<>();
        for (CheckEffectPlanRepository.EffectPlan effectPlan : plan.effects()) {
            if (effectPlan == null || effectPlan.effectOrder() != order || effectPlan.effect() == null) {
                throw rejected(Rejection.INVALID_EFFECT_PLAN);
            }
            CheckEffectExecutionRepository.Action action =
                    prepareAction(order, effectPlan.effect(), targets);
            if (action instanceof CheckEffectExecutionRepository.AppendEventMessage
                    && ++messageCount > 1) {
                throw rejected(Rejection.INVALID_EFFECT_PLAN);
            }
            actions.add(action);
            order++;
        }
        return new CheckEffectExecutionRepository.BranchActions(expected, actions);
    }

    private CheckEffectExecutionRepository.Action prepareAction(
            int order,
            CheckRequestPolicy.PreparedEffect effect,
            Map<String, TargetCharacter> targets) {
        String key = effect.effectKey();
        if (key == null || !ALGORITHMS.getOrDefault(key, "").equals(effect.executionAlgorithm())) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        return switch (key) {
            case ADJUST_HP -> {
                requireParameterCount(effect, 2);
                TargetCharacter target = target(effect, 0, targets);
                long amount = integer(effect, 1, "amount", 2, -999, 999);
                yield new CheckEffectExecutionRepository.AdjustCurrentHp(
                        order, target.databaseId(), target.characterKey(), amount);
            }
            case GRANT_MODULE_ITEM -> {
                requireParameterCount(effect, 3);
                TargetCharacter target = target(effect, 0, targets);
                String itemKey = reference(effect, 1, "item_template", 2);
                ModuleCatalog.ItemTemplate template = itemTemplates.get(itemKey);
                if (template == null) throw rejected(Rejection.ITEM_TEMPLATE_NOT_FOUND);
                int quantity = Math.toIntExact(integer(effect, 2, "quantity", 3, 1, 999));
                yield new CheckEffectExecutionRepository.GrantModuleItem(
                        order, target.databaseId(), target.characterKey(), itemKey,
                        template.displayName(), template.description(), quantity);
            }
            case GRANT_TEMPORARY_ITEM -> {
                requireParameterCount(effect, 4);
                TargetCharacter target = target(effect, 0, targets);
                String name = text(effect, 1, "name", 2, 1, 80);
                String description = text(effect, 2, "description", 3, 0, 500);
                int quantity = Math.toIntExact(integer(effect, 3, "quantity", 4, 1, 999));
                yield new CheckEffectExecutionRepository.GrantTemporaryItem(
                        order, target.databaseId(), target.characterKey(),
                        name, description, quantity);
            }
            case SET_POSITION -> {
                requireParameterCount(effect, 3);
                TargetCharacter target = target(effect, 0, targets);
                String mapKey = reference(effect, 1, "map", 2);
                String nodeKey = reference(effect, 2, "node", 3);
                if (!EncounterStateService.MAP_KEY.equals(mapKey)
                        || !positionNodeKeys.contains(nodeKey)) {
                    throw rejected(Rejection.INVALID_EFFECT_PLAN);
                }
                yield new CheckEffectExecutionRepository.SetEntityPosition(
                        order, target.databaseId(), target.characterKey(), mapKey, nodeKey);
            }
            case APPEND_MESSAGE -> {
                requireParameterCount(effect, 1);
                yield new CheckEffectExecutionRepository.AppendEventMessage(
                        order, text(effect, 0, "message", 1, 1, 500));
            }
            default -> throw rejected(Rejection.INVALID_EFFECT_PLAN);
        };
    }

    private static TargetCharacter target(
            CheckRequestPolicy.PreparedEffect effect,
            int index,
            Map<String, TargetCharacter> targets) {
        String key = reference(effect, index, "target_character", 1);
        TargetCharacter target = targets.get(key);
        if (target == null) throw rejected(Rejection.TARGET_NOT_FOUND);
        return target;
    }

    private static String reference(
            CheckRequestPolicy.PreparedEffect effect,
            int index,
            String key,
            int order) {
        CheckRequestPolicy.PreparedParameter parameter =
                parameter(effect, index, key, order, "REFERENCE");
        if (!(parameter.value() instanceof CheckRequestPolicy.ReferenceValue value)
                || value.value() == null || value.value().isBlank()
                || value.value().codePointCount(0, value.value().length()) > 255
                || value.value().codePoints().anyMatch(Character::isISOControl)) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        return value.value();
    }

    private static long integer(
            CheckRequestPolicy.PreparedEffect effect,
            int index,
            String key,
            int order,
            long minimum,
            long maximum) {
        CheckRequestPolicy.PreparedParameter parameter =
                parameter(effect, index, key, order, "INTEGER");
        if (!(parameter.value() instanceof CheckRequestPolicy.IntegerValue value)
                || value.value() < minimum || value.value() > maximum) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        return value.value();
    }

    private static String text(
            CheckRequestPolicy.PreparedEffect effect,
            int index,
            String key,
            int order,
            int minimum,
            int maximum) {
        CheckRequestPolicy.PreparedParameter parameter =
                parameter(effect, index, key, order, "TEXT");
        if (!(parameter.value() instanceof CheckRequestPolicy.TextValue value)
                || value.value() == null
                || !Normalizer.isNormalized(value.value(), Normalizer.Form.NFC)
                || value.value().codePoints().anyMatch(Character::isISOControl)) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        int length = value.value().codePointCount(0, value.value().length());
        if (length < minimum || length > maximum) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        return value.value();
    }

    private static CheckRequestPolicy.PreparedParameter parameter(
            CheckRequestPolicy.PreparedEffect effect,
            int index,
            String key,
            int order,
            String type) {
        if (effect.parameters() == null || index >= effect.parameters().size()) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        CheckRequestPolicy.PreparedParameter parameter = effect.parameters().get(index);
        if (parameter == null || !key.equals(parameter.parameterKey())
                || parameter.parameterOrder() != order || !type.equals(parameter.dataType())) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
        return parameter;
    }

    private static void requireParameterCount(
            CheckRequestPolicy.PreparedEffect effect, int count) {
        if (effect.parameters() == null || effect.parameters().size() != count) {
            throw rejected(Rejection.INVALID_EFFECT_PLAN);
        }
    }

    private static Map<String, TargetCharacter> targetIndex(List<TargetCharacter> targets) {
        if (targets == null) throw rejected(Rejection.INVALID_EFFECT_PLAN);
        Map<String, TargetCharacter> indexed = new HashMap<>();
        java.util.HashSet<Long> ids = new java.util.HashSet<>();
        for (TargetCharacter target : targets) {
            if (target == null || target.databaseId() <= 0
                    || !isCanonicalUuid(target.characterKey())
                    || !ids.add(target.databaseId())
                    || indexed.putIfAbsent(target.characterKey(), target) != null) {
                throw rejected(Rejection.INVALID_EFFECT_PLAN);
            }
        }
        return Map.copyOf(indexed);
    }

    private static <T> Map<String, T> uniqueIndex(
            List<T> rows, java.util.function.Function<T, String> key) {
        Map<String, T> indexed = new HashMap<>();
        for (T row : rows) {
            if (row == null || key.apply(row) == null
                    || indexed.putIfAbsent(key.apply(row), row) != null) {
                throw invalidCatalog();
            }
        }
        return Map.copyOf(indexed);
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
        java.util.HashSet<String> nodes = new java.util.HashSet<>();
        for (ModuleCatalog.MapNode row : catalog.mapNodes()) {
            if (row == null) throw invalidCatalog();
            if (EncounterStateService.MAP_KEY.equals(row.mapKey())
                    && (row.nodeKey() == null || !stableKey(row.nodeKey())
                    || !nodes.add(row.nodeKey()))) {
                throw invalidCatalog();
            }
        }
        if (nodes.isEmpty()) throw invalidCatalog();
        return Set.copyOf(nodes);
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean stableKey(String value) {
        return value != null
                && value.matches("[a-z][a-z0-9_]*(?:[.][a-z][a-z0-9_]*)*");
    }

    private static IllegalStateException invalidCatalog() {
        return new IllegalStateException("Invalid host command effect execution catalog");
    }

    private static EffectExecutionException rejected(Rejection rejection) {
        return new EffectExecutionException(rejection);
    }

    /** Database identity paired with the stable key resolved by the lock service. */
    public record TargetCharacter(long databaseId, String characterKey) {
    }

    public record PreparedBranches(
            CheckEffectExecutionRepository.BranchActions success,
            CheckEffectExecutionRepository.BranchActions failure) {
        public PreparedBranches {
            Objects.requireNonNull(success, "success actions are required");
            Objects.requireNonNull(failure, "failure actions are required");
        }

        public CheckEffectExecutionRepository.Command command(
                long checkExecutionId,
                long campaignId,
                long moduleReleaseId,
                long gameEventId) {
            return new CheckEffectExecutionRepository.Command(
                    checkExecutionId, campaignId, moduleReleaseId, gameEventId,
                    success, failure);
        }
    }

    public enum Rejection {
        INVALID_EFFECT_PLAN,
        TARGET_NOT_FOUND,
        ITEM_TEMPLATE_NOT_FOUND,
        EFFECT_NOT_SUPPORTED
    }

    public static final class EffectExecutionException extends IllegalArgumentException {
        private final Rejection rejection;

        private EffectExecutionException(Rejection rejection) {
            super(rejection.name());
            this.rejection = rejection;
        }

        public Rejection rejection() {
            return rejection;
        }
    }
}
