package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.CheckEffectPlanRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.CheckEffectExecutionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies frozen-catalog resolution before any selected-branch database write. */
final class CheckEffectExecutionServiceTest {
    private static final String TARGET = "11111111-1111-1111-1111-111111111111";

    @Test
    void preparesFiveEnabledActionsFromBothAuthoritativePlans() {
        CheckEffectExecutionService service = new CheckEffectExecutionService(catalog());
        CheckEffectPlanRepository.BranchPlan success = branch(
                CheckEffectPlanRepository.EffectBranch.SUCCESS,
                effect(1, adjustHp(-4)),
                effect(2, moduleItem("item.backpack", 2)),
                effect(3, temporaryItem("钥匙", "临时物品", 1)),
                effect(4, position()),
                effect(5, message("检定完成")));

        CheckEffectExecutionRepository.Command command = service.prepare(
                31L, 7L, 9L, 41L,
                List.of(new CheckEffectExecutionService.TargetCharacter(101L, TARGET)),
                success, branch(CheckEffectPlanRepository.EffectBranch.FAILURE));

        assertEquals(5, command.success().actions().size());
        CheckEffectExecutionRepository.AdjustCurrentHp hp = assertInstanceOf(
                CheckEffectExecutionRepository.AdjustCurrentHp.class,
                command.success().actions().get(0));
        assertEquals(101L, hp.targetCharacterId());
        assertEquals(-4L, hp.amount());
        CheckEffectExecutionRepository.GrantModuleItem module = assertInstanceOf(
                CheckEffectExecutionRepository.GrantModuleItem.class,
                command.success().actions().get(1));
        assertEquals("背包", module.itemName());
        assertEquals("可存放随身物品。", module.itemDescription());
        assertInstanceOf(CheckEffectExecutionRepository.GrantTemporaryItem.class,
                command.success().actions().get(2));
        CheckEffectExecutionRepository.SetEntityPosition position = assertInstanceOf(
                CheckEffectExecutionRepository.SetEntityPosition.class,
                command.success().actions().get(3));
        assertEquals("map.tavern_cellar", position.mapKey());
        assertEquals("node.entry", position.nodeKey());
        assertInstanceOf(CheckEffectExecutionRepository.AppendEventMessage.class,
                command.success().actions().get(4));
        assertEquals(List.of(), command.failure().actions());
    }

    @Test
    void rejectsPositionOutsideTheFrozenMapBeforePersistencePreparation() {
        CheckEffectExecutionService service = new CheckEffectExecutionService(catalog());
        assertInvalid(service, branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                effect(1, position("map.other", "node.entry"))));
        assertInvalid(service, branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                effect(1, position("map.tavern_cellar", "node.unknown"))));
    }

    @Test
    void rejectsUnknownTargetAndUnknownModuleItem() {
        CheckEffectExecutionService service = new CheckEffectExecutionService(catalog());
        assertRejection(CheckEffectExecutionService.Rejection.TARGET_NOT_FOUND,
                () -> service.prepare(31L, 7L, 9L, 41L, List.of(),
                        branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                                effect(1, adjustHp(1))),
                        branch(CheckEffectPlanRepository.EffectBranch.FAILURE)));
        assertRejection(CheckEffectExecutionService.Rejection.ITEM_TEMPLATE_NOT_FOUND,
                () -> service.prepare(31L, 7L, 9L, 41L,
                        List.of(new CheckEffectExecutionService.TargetCharacter(101L, TARGET)),
                        branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                                effect(1, moduleItem("item.unknown", 1))),
                        branch(CheckEffectPlanRepository.EffectBranch.FAILURE)));
    }

    @Test
    void rejectsForgedAlgorithmParameterOrderAndDuplicateMessage() {
        CheckEffectExecutionService service = new CheckEffectExecutionService(catalog());
        CheckRequestPolicy.PreparedEffect forged = new CheckRequestPolicy.PreparedEffect(
                "effect.adjust_current_hp", "CLIENT_ALGORITHM", adjustHp(1).parameters());
        assertInvalid(service, branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                effect(1, forged)));
        CheckRequestPolicy.PreparedEffect wrongOrder = new CheckRequestPolicy.PreparedEffect(
                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1", List.of(
                        reference("target_character", 1, TARGET),
                        integer("amount", 3, 1)));
        assertInvalid(service, branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                effect(1, wrongOrder)));
        assertInvalid(service, branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                effect(1, message("一")), effect(2, message("二"))));
    }

    @Test
    void rejectsFrozenCatalogAlgorithmDrift() {
        ModuleCatalog base = catalog();
        List<ModuleCatalog.EffectDefinition> drift = base.effectDefinitions().stream()
                .map(effect -> "effect.grant_module_item".equals(effect.effectKey())
                        ? new ModuleCatalog.EffectDefinition(effect.effectKey(), "WRONG") : effect)
                .toList();
        ModuleCatalog changed = copy(base, drift);
        assertThrows(IllegalStateException.class,
                () -> new CheckEffectExecutionService(changed));
    }

    private static void assertInvalid(
            CheckEffectExecutionService service, CheckEffectPlanRepository.BranchPlan success) {
        assertRejection(CheckEffectExecutionService.Rejection.INVALID_EFFECT_PLAN,
                () -> service.prepare(31L, 7L, 9L, 41L,
                        List.of(new CheckEffectExecutionService.TargetCharacter(101L, TARGET)),
                        success, branch(CheckEffectPlanRepository.EffectBranch.FAILURE)));
    }

    private static void assertRejection(
            CheckEffectExecutionService.Rejection expected, Runnable action) {
        CheckEffectExecutionService.EffectExecutionException exception = assertThrows(
                CheckEffectExecutionService.EffectExecutionException.class, action::run);
        assertEquals(expected, exception.rejection());
    }

    private static CheckEffectPlanRepository.BranchPlan branch(
            CheckEffectPlanRepository.EffectBranch branch,
            CheckEffectPlanRepository.EffectPlan... effects) {
        return new CheckEffectPlanRepository.BranchPlan(branch, List.of(effects));
    }

    private static CheckEffectPlanRepository.EffectPlan effect(
            int order, CheckRequestPolicy.PreparedEffect effect) {
        return new CheckEffectPlanRepository.EffectPlan(order, effect);
    }

    private static CheckRequestPolicy.PreparedEffect adjustHp(long amount) {
        return new CheckRequestPolicy.PreparedEffect(
                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1", List.of(
                        reference("target_character", 1, TARGET),
                        integer("amount", 2, amount)));
    }

    private static CheckRequestPolicy.PreparedEffect moduleItem(String key, long quantity) {
        return new CheckRequestPolicy.PreparedEffect(
                "effect.grant_module_item", "GRANT_MODULE_ITEM_V1", List.of(
                        reference("target_character", 1, TARGET),
                        reference("item_template", 2, key),
                        integer("quantity", 3, quantity)));
    }

    private static CheckRequestPolicy.PreparedEffect temporaryItem(
            String name, String description, long quantity) {
        return new CheckRequestPolicy.PreparedEffect(
                "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1", List.of(
                        reference("target_character", 1, TARGET),
                        text("name", 2, name), text("description", 3, description),
                        integer("quantity", 4, quantity)));
    }

    private static CheckRequestPolicy.PreparedEffect message(String message) {
        return new CheckRequestPolicy.PreparedEffect(
                "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1",
                List.of(text("message", 1, message)));
    }

    private static CheckRequestPolicy.PreparedEffect position() {
        return position("map.tavern_cellar", "node.entry");
    }

    private static CheckRequestPolicy.PreparedEffect position(
            String mapKey, String nodeKey) {
        return new CheckRequestPolicy.PreparedEffect(
                "effect.set_entity_position", "SET_ENTITY_NODE_POSITION_V1", List.of(
                        reference("target_character", 1, TARGET),
                        reference("map", 2, mapKey),
                        reference("node", 3, nodeKey)));
    }

    private static CheckRequestPolicy.PreparedParameter reference(
            String key, int order, String value) {
        return new CheckRequestPolicy.PreparedParameter(
                key, order, "REFERENCE", new CheckRequestPolicy.ReferenceValue(value));
    }

    private static CheckRequestPolicy.PreparedParameter integer(
            String key, int order, long value) {
        return new CheckRequestPolicy.PreparedParameter(
                key, order, "INTEGER", new CheckRequestPolicy.IntegerValue(value));
    }

    private static CheckRequestPolicy.PreparedParameter text(
            String key, int order, String value) {
        return new CheckRequestPolicy.PreparedParameter(
                key, order, "TEXT", new CheckRequestPolicy.TextValue(value));
    }

    private static ModuleCatalog catalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", "a".repeat(64), "RELEASED"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ModuleCatalog.ItemTemplate(
                        "item.backpack", "背包", "可存放随身物品。")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                List.of(
                        new ModuleCatalog.EffectDefinition(
                                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.grant_module_item", "GRANT_MODULE_ITEM_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.set_entity_position", "SET_ENTITY_NODE_POSITION_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1")),
                List.of(),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.entry", "Entry"),
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.cellar", "Cellar")),
                List.of());
    }

    private static ModuleCatalog copy(
            ModuleCatalog catalog, List<ModuleCatalog.EffectDefinition> effects) {
        return new ModuleCatalog(
                catalog.release(), catalog.ruleConstants(), catalog.fieldDefinitions(),
                catalog.classDefinitions(), catalog.proficiencyTiers(),
                catalog.proficiencyBonusBands(), catalog.skillDefinitions(),
                catalog.saveDefinitions(), catalog.itemTemplates(), catalog.entityTemplates(),
                catalog.entityTemplateValues(), catalog.entityTemplateClassLevels(),
                catalog.entityTemplateProficiencies(), catalog.checkDefinitions(),
                catalog.rollModes(), catalog.eventTemplates(), catalog.eventChecks(),
                catalog.eventEffects(), effects, catalog.effectParameters(),
                catalog.mapDefinitions(), catalog.mapNodes(), catalog.mapConnections());
    }
}
