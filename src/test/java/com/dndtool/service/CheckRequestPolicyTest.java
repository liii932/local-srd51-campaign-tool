package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CheckRequestPolicyTest {
    private static final String CHARACTER_KEY = "123e4567-e89b-12d3-a456-426614174000";
    @Test
    void resolvesServerAlgorithmsAndTypedParametersFromStableKeys() {
        CheckRequestPolicy policy = new CheckRequestPolicy(catalog());

        CheckRequestPolicy.PreparedRequest prepared = policy.prepare(
                new CheckRequestPolicy.ClientRequest(
                        "check.ability",
                        "roll.advantage",
                        "ability.strength",
                        null,
                        null,
                        15,
                        List.of(new CheckRequestPolicy.EffectRequest(
                                "effect.adjust_current_hp",
                                List.of(
                                        new CheckRequestPolicy.ParameterInput(
                                                "amount",
                                                new CheckRequestPolicy.IntegerValue(-5)),
                                        new CheckRequestPolicy.ParameterInput(
                                                "target_character",
                                                new CheckRequestPolicy.ReferenceValue(
                                                        CHARACTER_KEY)))))));

        assertEquals("ABILITY_MODIFIER_V1", prepared.modifierAlgorithm());
        assertEquals("HIGHEST_FIRST_ON_TIE_V1", prepared.selectionAlgorithm());
        assertEquals("ability.strength", prepared.modifierSourceKey());
        assertEquals("ADJUST_CURRENT_HP_CLAMP_V1",
                prepared.effects().getFirst().executionAlgorithm());
        assertEquals(List.of("target_character", "amount"),
                prepared.effects().getFirst().parameters().stream()
                        .map(CheckRequestPolicy.PreparedParameter::parameterKey)
                        .toList());
        assertEquals(-5,
                ((CheckRequestPolicy.IntegerValue)
                        prepared.effects().getFirst().parameters().get(1).value()).value());
    }

    @Test
    void clientRequestHasNoAuthoritativeDiceModifierOrAlgorithmFields() {
        Set<String> names = Set.of(
                java.util.Arrays.stream(CheckRequestPolicy.ClientRequest.class
                                .getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toArray(String[]::new));

        assertFalse(names.contains("rolledValue"));
        assertFalse(names.contains("selectedValue"));
        assertFalse(names.contains("modifierValue"));
        assertFalse(names.contains("modifierAlgorithm"));
        assertFalse(names.contains("selectionAlgorithm"));
        assertFalse(names.contains("executionAlgorithm"));
    }

    @Test
    void manualAcceptsOnlyBoundedExplicitModifierAndNormalizedName() {
        CheckRequestPolicy policy = new CheckRequestPolicy(catalog());

        CheckRequestPolicy.PreparedRequest prepared = policy.prepare(
                new CheckRequestPolicy.ClientRequest(
                        "check.manual", "roll.normal", null, -99,
                        "  Cafe\u0301  ", 0, List.of()));

        assertEquals("MANUAL_MODIFIER_V1", prepared.modifierAlgorithm());
        assertEquals("Café", prepared.manualName());
        assertEquals(-99, prepared.manualModifier());
    }

    @Test
    void rejectsUnapprovedKeysAndTypeOrParameterShapeBeforeExecution() {
        CheckRequestPolicy policy = new CheckRequestPolicy(catalog());

        assertRejection(CheckRequestPolicy.Rejection.CHECK_NOT_ALLOWED,
                () -> policy.prepare(request("check.unknown", "roll.normal", List.of())));
        assertRejection(CheckRequestPolicy.Rejection.ROLL_MODE_NOT_ALLOWED,
                () -> policy.prepare(request("check.ability", "roll.unknown", List.of())));
        assertRejection(CheckRequestPolicy.Rejection.EFFECT_NOT_ALLOWED,
                () -> policy.prepare(request("check.ability", "roll.normal", List.of(
                        new CheckRequestPolicy.EffectRequest("effect.unknown", List.of())))));
        assertRejection(CheckRequestPolicy.Rejection.PARAMETER_TYPE_MISMATCH,
                () -> policy.prepare(request("check.ability", "roll.normal", List.of(
                        new CheckRequestPolicy.EffectRequest(
                                "effect.adjust_current_hp",
                                List.of(new CheckRequestPolicy.ParameterInput(
                                        "amount",
                                        new CheckRequestPolicy.ReferenceValue("wrong")),
                                        new CheckRequestPolicy.ParameterInput(
                                                "target_character",
                                                new CheckRequestPolicy.ReferenceValue(CHARACTER_KEY))))))));
        assertRejection(CheckRequestPolicy.Rejection.PARAMETER_NOT_ALLOWED,
                () -> policy.prepare(request("check.ability", "roll.normal", List.of(
                        new CheckRequestPolicy.EffectRequest(
                                "effect.adjust_current_hp",
                                List.of(new CheckRequestPolicy.ParameterInput(
                                        "amount",
                                        new CheckRequestPolicy.IntegerValue(1))))))));
    }

    @Test
    void normalChecksRejectManualFieldsAndDerivedFinalValues() {
        CheckRequestPolicy policy = new CheckRequestPolicy(catalog());

        assertRejection(CheckRequestPolicy.Rejection.INVALID_REQUEST,
                () -> policy.prepare(new CheckRequestPolicy.ClientRequest(
                        "check.skill", "roll.normal", "skill.athletics", 4,
                        null, 10, List.of())));
        assertRejection(CheckRequestPolicy.Rejection.INVALID_REQUEST,
                () -> policy.prepare(new CheckRequestPolicy.ClientRequest(
                        "check.skill", "roll.normal", "4", null,
                        null, 10, List.of())));
        assertRejection(CheckRequestPolicy.Rejection.PARAMETER_VALUE_INVALID,
                () -> policy.prepare(request("check.ability", "roll.normal", List.of(
                        new CheckRequestPolicy.EffectRequest(
                                "effect.adjust_current_hp",
                                List.of(
                                        new CheckRequestPolicy.ParameterInput(
                                                "target_character",
                                                new CheckRequestPolicy.ReferenceValue(CHARACTER_KEY)),
                                        new CheckRequestPolicy.ParameterInput(
                                                "amount",
                                                new CheckRequestPolicy.IntegerValue(1000))))))));
    }

    @Test
    void textParametersNormalizeNfcAndRejectControlsOrBounds() {
        CheckRequestPolicy policy = new CheckRequestPolicy(catalog());

        CheckRequestPolicy.PreparedRequest prepared = policy.prepare(request(
                "check.ability", "roll.normal", List.of(new CheckRequestPolicy.EffectRequest(
                        "effect.grant_temporary_item",
                        List.of(
                                new CheckRequestPolicy.ParameterInput(
                                        "target_character",
                                        new CheckRequestPolicy.ReferenceValue(CHARACTER_KEY)),
                                new CheckRequestPolicy.ParameterInput(
                                        "name", new CheckRequestPolicy.TextValue(
                                                "Cafe\u0301")),
                                new CheckRequestPolicy.ParameterInput(
                                        "description", new CheckRequestPolicy.TextValue("说明")),
                                new CheckRequestPolicy.ParameterInput(
                                        "quantity", new CheckRequestPolicy.IntegerValue(2)))))));

        assertEquals("Café", ((CheckRequestPolicy.TextValue)
                prepared.effects().getFirst().parameters().get(1).value()).value());
        assertRejection(CheckRequestPolicy.Rejection.PARAMETER_VALUE_INVALID,
                () -> policy.prepare(request("check.ability", "roll.normal", List.of(
                        new CheckRequestPolicy.EffectRequest(
                                "effect.grant_temporary_item",
                                List.of(
                                        new CheckRequestPolicy.ParameterInput(
                                                "target_character",
                                                new CheckRequestPolicy.ReferenceValue(CHARACTER_KEY)),
                                        new CheckRequestPolicy.ParameterInput(
                                                "name", new CheckRequestPolicy.TextValue("\n")),
                                        new CheckRequestPolicy.ParameterInput(
                                                "description", new CheckRequestPolicy.TextValue("x")),
                                        new CheckRequestPolicy.ParameterInput(
                                                "quantity", new CheckRequestPolicy.IntegerValue(1))))))));
    }

    @Test
    void acceptsOnlyTheFrozenMapAndKnownNodeForPositionEffect() {
        CheckRequestPolicy policy = new CheckRequestPolicy(catalog());

        CheckRequestPolicy.PreparedRequest prepared = policy.prepare(
                positionRequest("map.tavern_cellar", "node.entry"));
        assertEquals("SET_ENTITY_NODE_POSITION_V1",
                prepared.effects().getFirst().executionAlgorithm());

        assertRejection(CheckRequestPolicy.Rejection.PARAMETER_VALUE_INVALID,
                () -> policy.prepare(positionRequest("map.other", "node.entry")));
        assertRejection(CheckRequestPolicy.Rejection.PARAMETER_VALUE_INVALID,
                () -> policy.prepare(positionRequest("map.tavern_cellar", "node.unknown")));
    }

    private static CheckRequestPolicy.ClientRequest positionRequest(
            String mapKey, String nodeKey) {
        return request("check.ability", "roll.normal", List.of(
                new CheckRequestPolicy.EffectRequest(
                        "effect.set_entity_position",
                        List.of(
                                new CheckRequestPolicy.ParameterInput(
                                        "target_character",
                                        new CheckRequestPolicy.ReferenceValue(CHARACTER_KEY)),
                                new CheckRequestPolicy.ParameterInput(
                                        "map", new CheckRequestPolicy.ReferenceValue(mapKey)),
                                new CheckRequestPolicy.ParameterInput(
                                        "node", new CheckRequestPolicy.ReferenceValue(nodeKey))))));
    }

    private static CheckRequestPolicy.ClientRequest request(
            String checkKey, String rollModeKey, List<CheckRequestPolicy.EffectRequest> effects) {
        return new CheckRequestPolicy.ClientRequest(
                checkKey, rollModeKey, "ability.strength", null, null, 10, effects);
    }

    private static void assertRejection(
            CheckRequestPolicy.Rejection expected, Runnable action) {
        CheckRequestPolicy.PolicyException exception = assertThrows(
                CheckRequestPolicy.PolicyException.class, action::run);
        assertEquals(expected, exception.rejection());
    }

    private static ModuleCatalog catalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", "a".repeat(64), "RELEASED"),
                List.of(),
                List.of(new ModuleCatalog.FieldDefinition(
                        "ability.strength", "Strength", "INTEGER",
                        new ModuleCatalog.IntegerValue(10),
                        new ModuleCatalog.IntegerValue(1),
                        new ModuleCatalog.IntegerValue(30), null, null, null)),
                List.of(), List.of(), List.of(),
                List.of(new ModuleCatalog.SkillDefinition(
                        "skill.athletics", "Athletics", "ability.strength")),
                List.of(new ModuleCatalog.SaveDefinition(
                        "save.strength", "ability.strength")),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(
                        new ModuleCatalog.CheckDefinition(
                                "check.ability", "ABILITY", "ABILITY_MODIFIER_V1"),
                        new ModuleCatalog.CheckDefinition(
                                "check.skill", "SKILL", "SKILL_BONUS_V1"),
                        new ModuleCatalog.CheckDefinition(
                                "check.saving_throw", "SAVING_THROW", "SAVING_THROW_BONUS_V1"),
                        new ModuleCatalog.CheckDefinition(
                                "check.manual", "MANUAL", "MANUAL_MODIFIER_V1")),
                List.of(
                        new ModuleCatalog.RollMode(
                                "roll.normal", "NORMAL", 1, "ONLY_CANDIDATE_V1"),
                        new ModuleCatalog.RollMode(
                                "roll.advantage", "ADVANTAGE", 2,
                                "HIGHEST_FIRST_ON_TIE_V1"),
                        new ModuleCatalog.RollMode(
                                "roll.disadvantage", "DISADVANTAGE", 2,
                                "LOWEST_FIRST_ON_TIE_V1")),
                List.of(), List.of(), List.of(),
                List.of(
                        new ModuleCatalog.EffectDefinition(
                                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.set_entity_position", "SET_ENTITY_NODE_POSITION_V1")),
                List.of(
                        new ModuleCatalog.EffectParameter(
                                "effect.adjust_current_hp", "target_character", "REFERENCE",
                                "CHARACTER", null, null, null, null, 1),
                        new ModuleCatalog.EffectParameter(
                                "effect.adjust_current_hp", "amount", "INTEGER", null,
                                new ModuleCatalog.IntegerValue(-999),
                                new ModuleCatalog.IntegerValue(999), null, null, 2),
                        new ModuleCatalog.EffectParameter(
                                "effect.grant_temporary_item", "target_character", "REFERENCE",
                                "CHARACTER", null, null, null, null, 1),
                        new ModuleCatalog.EffectParameter(
                                "effect.grant_temporary_item", "name", "TEXT", null,
                                new ModuleCatalog.IntegerValue(1),
                                new ModuleCatalog.IntegerValue(80), "NFC", true, 2),
                        new ModuleCatalog.EffectParameter(
                                "effect.grant_temporary_item", "description", "TEXT", null,
                                new ModuleCatalog.IntegerValue(0),
                                new ModuleCatalog.IntegerValue(500), "NFC", true, 3),
                        new ModuleCatalog.EffectParameter(
                                "effect.grant_temporary_item", "quantity", "INTEGER", null,
                                new ModuleCatalog.IntegerValue(1),
                                new ModuleCatalog.IntegerValue(999), null, null, 4),
                        new ModuleCatalog.EffectParameter(
                                "effect.set_entity_position", "target_character", "REFERENCE",
                                "CHARACTER", null, null, null, null, 1),
                        new ModuleCatalog.EffectParameter(
                                "effect.set_entity_position", "map", "REFERENCE",
                                "MAP", null, null, null, null, 2),
                        new ModuleCatalog.EffectParameter(
                                "effect.set_entity_position", "node", "REFERENCE",
                                "NODE", null, null, null, null, 3)),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.entry", "Entry"),
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.cellar", "Cellar")),
                List.of());
    }
}
