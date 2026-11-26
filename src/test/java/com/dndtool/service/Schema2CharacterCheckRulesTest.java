package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.dndtool.service.storage.CampaignRuleFixtures;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.STONECUNNING_HISTORY;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static com.dndtool.service.Schema2CharacterRuntime.PASSIVE_CHECK;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CharacterCheckRulesTest {
    private final Schema2CharacterCheckRules rules = new Schema2CharacterCheckRules();
    private final ProductionSchema2Projection material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages("normal")));
    private final State initial = Schema2CheckTestFixture.withPreparedTools(new Schema2CharacterProgressionRules().initial(material,
            fullPlan(material, IDENTITY, CAMPAIGN, CHARACTER), PREPARATION, fullAbilities(), INITIAL_CHOICES));

    @Test void defaultSkillAndAnyToolAbilityUseOneBonusDespiteMultipleGrantSources() {
        assertEquals(2, initial.proficiencies().stream().filter(value -> value.target().equals(ATHLETICS)).count());
        assertEquals(2, initial.proficiencies().stream().filter(value -> value.target().equals(TOOL)).count());
        var skill = rules.prepare(material, initial, check("SKILL", "ability.strength", ATHLETICS, 14, false, false));
        assertEquals(new CheckResult(10, null, 10, 2, 2, 14, true, false, false, 2, null), rules.resolve(skill, 10, null));
        var tool = rules.prepare(material, initial, check("TOOL", "ability.wisdom", TOOL, 11, false, false));
        assertEquals(new CheckResult(10, null, 10, -1, 2, 11, true, false, false, 2, null), rules.resolve(tool, 10, null));
        assertEquals(initial.version() + 1, tool.capacityState().version());
        assertEquals(initial.resources(), tool.capacityState().resources());
        assertEquals(initial.proficiencies(), tool.capacityState().proficiencies());
    }

    @Test void untrainedTargetsAndCrossModuleSameKeysDoNotReceiveAnotherTargetsBonus() {
        for (Command command : List.of(check("SKILL", "ability.dexterity", ACROBATICS, 11, false, false),
                check("SKILL", "ability.strength", OTHER_SKILL, 12, false, false),
                check("TOOL", "ability.strength", OTHER_TOOL, 12, false, false),
                check("ABILITY", "ability.strength", null, 12, false, false))) {
            assertEquals(0, rules.resolve(rules.prepare(material, initial, command), 10, null).proficiencyBonus());
        }
    }

    @Test void alternateSkillChecksUseTheSelectedAbilityAndOneExactProficiencyBonus() {
        var enabled = alternateSkillMaterial();
        var state = alternateSkillState(enabled);
        assertEquals(2, state.proficiencies().stream().filter(value -> value.target().equals(ATHLETICS)).count());
        for (var ability : Map.of("ability.strength", 2, "ability.dexterity", 1, "ability.constitution", 1,
                "ability.intelligence", 0, "ability.wisdom", -1, "ability.charisma", 2).entrySet()) {
            int modifier = ability.getValue();
            var command = check("SKILL", ability.getKey(), ATHLETICS, 12 + modifier, false, false);
            var result = rules.resolve(rules.prepare(enabled, state, command), 10, null);
            assertEquals(new CheckResult(10, null, 10, modifier, 2, 12 + modifier, true, false, false, 2, null), result);
            assertEquals(state.proficiencies(), rules.verify(enabled, state, command, result).proficiencies());
        }
        var untrained = check("SKILL", "ability.constitution", OTHER_SKILL, 12, false, false);
        assertEquals(new CheckResult(10, null, 10, 1, 0, 11, false, false, false, 2, null),
                rules.resolve(rules.prepare(enabled, state, untrained), 10, null));
        for (var skill : List.of(WEAPON, new Schema2CompositionKernel.RuleIdentity(BASE,
                "character.proficiency", "proficiency.skill.unknown"))) {
            var error = assertThrows(IllegalArgumentException.class, () -> rules.prepare(enabled, state,
                    check("SKILL", "ability.constitution", skill, 12, false, false)));
            assertEquals("S2_CHECK_SKILL_UNSUPPORTED", error.getMessage());
        }
    }

    @Test void armorDisadvantageUsesTheActualAbilityAndDexterityStealthPair() {
        var enabled = alternateSkillMaterial();
        var state = alternateSkillState(enabled);
        var equipment = new Schema2CharacterEquipmentRules();
        UUID armor = UUID.randomUUID();
        var carrying = equipment.apply(enabled, state, equipment("ITEM_ACQUIRED", armor, ITEM_MEDIUM, "CARRIED"), armor);
        var proficientArmor = equipment.apply(enabled, carrying, equipment("ITEM_EQUIPPED", armor, null, "WORN"), UUID.randomUUID());
        var dexterityStealth = check("SKILL", "ability.dexterity", STEALTH, 10, false, false);
        var strengthStealth = check("SKILL", "ability.strength", STEALTH, 10, false, false);
        var dexterity = rules.prepare(enabled, proficientArmor, dexterityStealth);
        assertEquals(new CheckResult(18, 3, 3, 1, 0, 4, false, false, true, 2, null),
                rules.resolve(dexterity, 18, 3));
        assertFalse(rules.prepare(enabled, proficientArmor, strengthStealth).secondDieRequired());

        UUID shield = UUID.randomUUID();
        carrying = equipment.apply(enabled, proficientArmor, equipment("ITEM_ACQUIRED", shield, ITEM_SHIELD, "CARRIED"), shield);
        var untrainedShield = equipment.apply(enabled, carrying, equipment("ITEM_EQUIPPED", shield, null, "OFF_HAND"), UUID.randomUUID());
        assertTrue(rules.prepare(enabled, untrainedShield, strengthStealth).secondDieRequired());
        var constitutionAthletics = check("SKILL", "ability.constitution", ATHLETICS, 10, false, false);
        assertFalse(rules.prepare(enabled, untrainedShield, constitutionAthletics).secondDieRequired());
        var dexterityAthletics = check("SKILL", "ability.dexterity", ATHLETICS, 10, true, false);
        var cancelled = rules.resolve(rules.prepare(enabled, untrainedShield, dexterityAthletics), 10, null);
        assertTrue(cancelled.effectiveAdvantage());
        assertTrue(cancelled.effectiveDisadvantage());
        assertFalse(dexterityAthletics.checkInput().disadvantage());
        var passiveStealth = rules.resolvePassive(rules.prepare(enabled, proficientArmor, passive(dexterityStealth)));
        assertEquals(new CheckResult(null, null, null, 1, 0, 10, true, false, true, 2, null, 12, -3), passiveStealth);
        var passiveCancelled = rules.resolvePassive(rules.prepare(enabled, untrainedShield, passive(dexterityAthletics)));
        assertEquals(new CheckResult(null, null, null, 1, 2, 15, true, true, true, 2, null, 12, 0), passiveCancelled);
    }

    @Test void passiveScoreAppliesFrozenBaseAndSignedAdjustmentWithoutRolling() {
        var enabled = alternateSkillMaterial();
        var state = alternateSkillState(enabled);
        for (boolean advantage : List.of(false, true)) for (boolean disadvantage : List.of(false, true)) {
            int direction = (advantage ? 1 : 0) - (disadvantage ? 1 : 0);
            var ordinary = check("SKILL", "ability.strength", ATHLETICS, 14, advantage, disadvantage);
            assertPassive(material, initial, passive(ordinary), 10, direction * 5, 2, 2);
            var alternate = check("SKILL", "ability.constitution", ATHLETICS, 14, advantage, disadvantage);
            assertPassive(enabled, state, passive(alternate), 12, direction * 3, 1, 2);
        }
        assertPassive(material, initial, passive(check("TOOL", "ability.wisdom", TOOL, 11, false, false)), 10, 0, -1, 2);
        assertPassive(material, initial, passive(check("ABILITY", "ability.wisdom", null, 11, false, false)), 10, 0, -1, 0);
        assertThrows(IllegalArgumentException.class, () -> rules.resolvePassive(
                rules.prepare(material, initial, check("ABILITY", "ability.strength", null, 14, false, false))));
        assertThrows(IllegalArgumentException.class, () -> rules.prepare(material, initial,
                passive(check("SKILL", "ability.constitution", ATHLETICS, 14, false, false))));
    }

    private void assertPassive(ProductionSchema2Projection material, State state, Command command, int base, int adjustment,
            int modifier, int proficiency) {
        var prepared = rules.prepare(material, state, command);
        assertFalse(prepared.secondDieRequired());
        assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, 10, null));
        int total = base + adjustment + modifier + proficiency;
        var input = command.checkInput();
        var result = new CheckResult(null, null, null, modifier, proficiency, total, total >= input.difficultyClass(),
                input.advantage(), input.disadvantage(), 2, null, base, adjustment);
        assertEquals(result, rules.resolvePassive(prepared));
        assertEquals(prepared.capacityState(), rules.verify(material, state, command, result));
    }

    private static Command passive(Command active) {
        return new Command(PASSIVE_CHECK, null, 0, null, null, null, GrowthChoices.EMPTY, null, active.checkInput());
    }

    private static ProductionSchema2Projection alternateSkillMaterial() {
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages("alternate-skill-ability")));
    }

    private static State alternateSkillState(ProductionSchema2Projection material) {
        return new Schema2CharacterProgressionRules().initial(material,
                fullPlan(material, IDENTITY, CAMPAIGN, CHARACTER), PREPARATION, fullAbilities(), INITIAL_CHOICES);
    }

    @Test void advantageDisadvantageAndCancellationUseExactDiceAndNoNaturalOneOrTwentyOverride() {
        for (boolean advantage : List.of(false, true)) for (boolean disadvantage : List.of(false, true)) {
            var prepared = rules.prepare(material, initial, check("ABILITY", "ability.strength", null, 10, advantage, disadvantage));
            assertEquals(advantage != disadvantage, prepared.secondDieRequired());
            var result = rules.resolve(prepared, 3, prepared.secondDieRequired() ? 18 : null);
            assertEquals(advantage && !disadvantage ? 18 : 3, result.selectedD20());
            assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, 3, prepared.secondDieRequired() ? null : 18));
        }
        assertTrue(rules.resolve(rules.prepare(material, initial, check("ABILITY", "ability.strength", null, 0, false, false)), 1, null).success());
        assertFalse(rules.resolve(rules.prepare(material, initial, check("ABILITY", "ability.wisdom", null, 30, false, false)), 20, null).success());
    }

    @Test void unknownTargetsNonSkillsAlternateSkillAbilitiesAndInvalidDiceFailClosed() {
        for (Command command : List.of(check("SKILL", "ability.strength", WEAPON, 10, false, false),
                check("SKILL", "ability.constitution", ATHLETICS, 10, false, false),
                check("SKILL", "ability.strength", new Schema2CompositionKernel.RuleIdentity(BASE, "character.proficiency", "proficiency.missing"), 10, false, false),
                check("TOOL", "ability.strength", new Schema2CompositionKernel.RuleIdentity(BASE, "character.tool", "tool.missing"), 10, false, false)))
            assertThrows(IllegalArgumentException.class, () -> rules.prepare(material, initial, command));
        var prepared = rules.prepare(material, initial, check("ABILITY", "ability.strength", null, 10, true, false));
        for (int roll : List.of(0, 21)) {
            assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, roll, 10));
            assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, 10, roll));
        }
        assertThrows(IllegalArgumentException.class, () -> rules.verify(material, initial,
                check("ABILITY", "ability.strength", null, 10, true, false), new CheckResult(3, 18, 18, 2, 0, 20, false, true, false, 2, null)));
    }

    @Test void boundedInputRejectsControlsBadUnicodeNonNfcAndForeignCommandFields() {
        for (String text : Arrays.asList(null, "", " ", "a\u0000b", "a\u0085b", "e\u0301", "\ud800", "a".repeat(501)))
            assertThrows(IllegalArgumentException.class, () -> new CheckInput(CHECK, "ABILITY", "ability.strength", null, 10, false, false, text));
        String astral = "😀".repeat(500);
        assertEquals(astral, new CheckInput(CHECK, "ABILITY", "ability.strength", null, 30, false, false, astral).adjudication());
        for (int dc : List.of(-1, 31)) assertThrows(IllegalArgumentException.class,
                () -> new CheckInput(CHECK, "ABILITY", "ability.strength", null, dc, false, false, "DM"));
        CheckInput input = new CheckInput(CHECK, "ABILITY", "ability.strength", null, 0, false, false, "DM");
        assertThrows(IllegalArgumentException.class, () -> new Command("CHECK_RESOLVED", ENERGY, 0, null, null, null, GrowthChoices.EMPTY, null, input));
        assertThrows(IllegalArgumentException.class, () -> new Command("RESOURCE_SPENT", ENERGY, 1, null, null, null, GrowthChoices.EMPTY, null, input));
    }

    @Test void statisticsOnlyStateCannotPretendToHaveRealSkillOrToolFacts() {
        var statistics = Schema2ProgressionFixtures.projection();
        var state = new Schema2CharacterProgressionRules().initial(statistics,
                initialPlan(statistics, IDENTITY, CAMPAIGN, CHARACTER), PREPARATION, abilities(), GrowthChoices.EMPTY);
        var error = assertThrows(IllegalArgumentException.class,
                () -> rules.prepare(statistics, state, check("ABILITY", "ability.strength", null, 10, false, false)));
        assertEquals("S2_CHECK_GROWTH_SCOPE", error.getMessage());
    }

    @Test void stoneworkSubjectRequiresTheExactHistoryIdentityAndChangesTheRequestDigest() {
        String intelligence = "ability.intelligence";
        var lookalike = new Schema2CompositionKernel.RuleIdentity(EXT, STONECUNNING_HISTORY.ruleType(), STONECUNNING_HISTORY.ruleKey());
        for (var skill : List.of(ARCANA, lookalike))
            assertThrows(IllegalArgumentException.class, () -> new CheckInput(CHECK, "SKILL", intelligence, skill,
                    10, false, false, "Identify the origin.", CheckInput.STONEWORK_ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new CheckInput(CHECK, "SKILL", "ability.wisdom", STONECUNNING_HISTORY,
                10, false, false, "Identify the origin.", CheckInput.STONEWORK_ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new CheckInput(CHECK, "ABILITY", intelligence, null,
                10, false, false, "Identify the origin.", CheckInput.STONEWORK_ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new CheckInput(CHECK, "SKILL", intelligence, STONECUNNING_HISTORY,
                10, false, false, "Identify the origin.", "UNKNOWN"));
        var ordinary = check(new CheckInput(CHECK, "SKILL", intelligence, STONECUNNING_HISTORY,
                10, false, false, "Identify the origin.", CheckInput.GENERAL));
        var stonework = check(new CheckInput(CHECK, "SKILL", intelligence, STONECUNNING_HISTORY,
                10, false, false, "Identify the origin.", CheckInput.STONEWORK_ORIGIN));
        UUID operation = UUID.randomUUID();
        var binding = Schema2RulesBinding.from(material);
        var normalRequest = new Schema2CharacterRequest(operation, IDENTITY, CAMPAIGN, CHARACTER, PREPARATION,
                binding, initial.version(), ordinary, Map.of());
        var stoneworkRequest = new Schema2CharacterRequest(operation, IDENTITY, CAMPAIGN, CHARACTER, PREPARATION,
                binding, initial.version(), stonework, Map.of());
        assertNotEquals(normalRequest.digest(), stoneworkRequest.digest());
    }

    @Test void historyReplaysStonecunningFromTheFrozenPlanAndRejectsForgedContribution() {
        var context = CampaignRuleFixtures.rules();
        var projection = context.projection();
        var plan = context.initialPlan(CAMPAIGN, CHARACTER, IDENTITY, "fighter", "custom_artisan", "dwarf", "hill_dwarf");
        var engine = new CampaignDomainEngine();
        var before = engine.initial(projection, plan, PREPARATION, context.initialAbilities("fighter"),
                context.initialChoices("fighter"), UUID.randomUUID());
        assertFalse(before.current().proficiencies().stream().anyMatch(fact -> fact.target().equals(STONECUNNING_HISTORY)));
        var command = check(new CheckInput(context.checkRule(), "SKILL", "ability.intelligence", STONECUNNING_HISTORY,
                13, false, false, "Identify the origin.", CheckInput.STONEWORK_ORIGIN));
        var passive = rules.resolvePassive(rules.prepare(projection, plan, before.current(), passive(command)));
        assertEquals(new CheckResult(null, null, null, -1, 4, 13, true, false, false, 2,
                context.rule("character.stonecunning", "stonecunning.dwarf"), 10, 0), passive);
        var applied = engine.executeCharacter(projection, plan, before, command, UUID.randomUUID(), sides -> {
            assertEquals(20, sides);
            return 10;
        });
        var event = applied.events().getLast();
        var rule = context.rule("character.stonecunning", "stonecunning.dwarf");
        assertEquals(new CheckResult(10, null, 10, -1, 4, 13, true, false, false, 2, rule), event.checkResult());
        assertEquals(before.current().proficiencies(), applied.current().proficiencies());
        assertDoesNotThrow(() -> engine.validateCharacter(projection, plan, applied));

        var forgedResult = new CheckResult(10, null, 10, -1, 6, 15, true, false, false, 2, rule);
        var forgedEvent = new Event(event.operationId(), event.requestDigest(), event.command(), event.hitDieRoll(),
                event.before(), event.after(), forgedResult);
        var forgedHistory = new History(applied.current(), List.of(before.events().getFirst(), forgedEvent));
        assertDoesNotThrow(() -> Schema2CharacterRuntime.validate(forgedHistory));
        var error = assertThrows(IllegalArgumentException.class, () -> engine.validateCharacter(projection, plan, forgedHistory));
        assertEquals("S2_CHARACTER_HISTORY_CORRUPT", error.getMessage());
    }
}
