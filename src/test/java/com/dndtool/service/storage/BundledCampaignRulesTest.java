package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.service.Schema2CharacterCheckRules;
import com.dndtool.service.Schema2CharacterEquipmentRules;
import com.dndtool.service.Schema2CharacterHealthRules;
import com.dndtool.service.Schema2CharacterProgressionRules;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.FeatureState;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthInput;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2CombatRules;
import com.dndtool.service.Schema2CombatState;
import com.dndtool.service.Schema2FeatureSupport;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.Schema2SpellRules;
import com.dndtool.service.Schema2WizardSpellbookRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.storage.BundledCampaignRules.*;
import static org.junit.jupiter.api.Assertions.*;

class BundledCampaignRulesTest {
    private static final Map<String, Integer> ARRAY = Map.of("ability.strength", 15, "ability.dexterity", 14,
            "ability.constitution", 13, "ability.intelligence", 12, "ability.wisdom", 10, "ability.charisma", 8);

    private static final CampaignRuleContext STANDARD =
            CampaignDomainData.Material.of(projection()).restore();

    @Test void productionBundlesMatchTheAuthoredSourcesAndRestoreTheirCompleteTypedProjection() throws Exception {
        verifyCombination(BundledCampaignRules.STANDARD, "dm-runtime-house-rules");
        verifyCombination(BundledCampaignRules.CHECK_VARIANT, "dm-runtime-check-variant");
    }

    private static void verifyCombination(String combination, String fourth) throws Exception {
        List<String> directories = List.of("srd51-runtime", "srd51-characters", "srd51-magic", fourth);
        List<ProductionRuleArtifact> selected = artifacts(combination);
        assertEquals(4, selected.size());
        Path source = Path.of("rule-packages", "srd51-runtime");
        for (int index = 0; index < selected.size(); index++) {
            ProductionRuleArtifact artifact = ProductionRuleArtifact.read(selected.get(index).originalZip());
            assertArrayEquals(selected.get(index).logicalJson(), artifact.logicalJson());
            Path directory = source.resolve(directories.get(index));
            Path packageGuide = directory.resolve("package-guide.md");
            for (var member : artifact.originalMembers().entrySet()) {
                Path expected = switch (member.getKey()) {
                    case "NOTICE" -> source.resolve("notice.md");
                    case "README.md" -> Files.isRegularFile(packageGuide) ? packageGuide : source.resolve("package-guide.md");
                    default -> directory.resolve(member.getKey());
                };
                assertArrayEquals(Files.readAllBytes(expected), member.getValue(), expected.toString());
            }
            assertTrue(artifact.originalMembers().containsKey("NOTICE"));
        }
        var material = projection(combination);
        var restored = CampaignDomainData.Material.of(material).restore().projection();
        assertNotSame(material, restored);
        assertArrayEquals(material.configurationJson(), restored.configurationJson());
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(material.chainsJson(), restored.chainsJson());
        assertTrue(material.projection().rules().stream().noneMatch(row -> row.identity().ruleKey().contains("guard")));
    }

    @Test void realStandardArrayInitializersConsumeTheExactSourcesAndClassChoices() {
        var growth = new Schema2CharacterProgressionRules();
        for (String characterClass : List.of("fighter", "cleric", "wizard")) {
            var plan = STANDARD.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), characterClass, "custom_artisan");
            State state = growth.initial(STANDARD.projection(), plan, UUID.randomUUID(), ARRAY, STANDARD.initialChoices(characterClass));
            assertEquals(switch (characterClass) { case "fighter" -> 11; case "cleric" -> 9; default -> 7; }, state.maximumHitPoints());
            assertEquals(1, state.level());
            assertEquals(2, state.proficiencyBonus());
            assertEquals(STANDARD.rule("character.class", "class." + characterClass), state.characterClass());
            assertTrue(plan.character().grants().stream().anyMatch(grant ->
                    grant.target().equals(STANDARD.rule("character.tool", "tool.smith_tools"))));
            assertEquals(characterClass.equals("cleric") ? 1 : 0, state.subclasses().size());
        }
        var acolyte = STANDARD.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), "class.cleric", "background.acolyte");
        assertEquals(4, acolyte.character().grants().size());
        assertThrows(IllegalArgumentException.class, () -> STANDARD.initialChoices("bard"));
        assertThrows(IllegalArgumentException.class, () -> STANDARD.rule("adventure.spell", "spell.fireball"));
    }

    @Test void fighterGrowthUsesRealSubclassAsiAndMulticlassPrerequisites() {
        var material = STANDARD.projection();
        var growth = new Schema2CharacterProgressionRules();
        RuleIdentity fighter = STANDARD.rule("character.class", "class.fighter");
        State state = fighterAtLevelFour(growth);
        assertEquals(36, state.maximumHitPoints());
        assertEquals(15, state.abilities().get("ability.constitution"));
        assertThrows(IllegalArgumentException.class, () -> growth.prepareAdvance(material, state,
                STANDARD.rule("character.class", "class.wizard"), 1, "FIXED_AVERAGE", GrowthChoices.EMPTY));
        assertEquals(4, state.level());
        State cleric = initial("cleric");
        assertThrows(IllegalArgumentException.class, () -> growth.prepareAdvance(material, cleric,
                STANDARD.rule("character.class", "class.cleric"), 2, "FIXED_AVERAGE", GrowthChoices.EMPTY));
    }

    @Test void fighterExtraAttackRequiresTheExactAcquiredFifthLevelFeature() {
        var material = STANDARD.projection();
        var growth = new Schema2CharacterProgressionRules();
        var fighter = STANDARD.rule("character.class", "class.fighter");
        var feature = (FinalFeature) find("character.feature", "feature.fighter_extra_attack");
        assertEquals(25, feature.definition().sourcePage());
        assertEquals(FIGHTER_EXTRA_ATTACK_ALGORITHM, feature.definition().executionAlgorithm());
        assertEquals(FeatureExecution.AUTOMATIC, feature.definition().executionMode());
        assertTrue(STANDARD.metadata().catalog().stream().anyMatch(row -> row.key().equals(feature.identity().ruleKey()) && row.executable()));

        State fourth = fighterAtLevelFour(growth);
        assertTrue(fourth.features().isEmpty());
        assertEquals(1, Schema2FeatureSupport.attacksPerAttackAction(material, fourth));
        State fifth = advanceFighter(growth, fourth, 5, GrowthChoices.EMPTY);
        var acquired = new FeatureState(feature.identity(), fighter, 5);
        assertEquals(List.of(acquired), fifth.features());
        assertEquals(2, Schema2FeatureSupport.attacksPerAttackAction(material, fifth));
        State sixth = advanceFighter(growth, fifth, 6, new GrowthChoices(null, Map.of("ability.strength", 2), null, Map.of()));
        assertEquals(fifth.features(), sixth.features());
        assertEquals(2, Schema2FeatureSupport.attacksPerAttackAction(material, sixth));

        State missing = withFeatures(fifth, List.of());
        assertEquals(1, Schema2FeatureSupport.attacksPerAttackAction(material, missing));
        assertEquals("S2_GROWTH_FEATURE_STATE", assertThrows(IllegalArgumentException.class,
                () -> growth.validateState(material, missing)).getMessage());
        assertRejectedAttackFeature(withFeatures(fourth, List.of(acquired)));
        assertRejectedAttackFeature(withFeatures(fifth, List.of(acquired, acquired)));
        assertRejectedAttackFeature(withFeatures(fifth, List.of(new FeatureState(acquired.feature(), fighter, 4))));
        assertRejectedAttackFeature(withFeatures(fifth, List.of(new FeatureState(acquired.feature(),
                STANDARD.rule("character.class", "class.wizard"), 5))));
        assertRejectedAttackFeature(withFeatures(fifth, List.of(new FeatureState(
                new RuleIdentity("other_module", "character.feature", acquired.feature().ruleKey()), fighter, 5))));
    }

    @Test void wizardBookUsesRegisteredExecutableAndExplicitMetadataSpells() {
        var books = new Schema2WizardSpellbookRules();
        State wizard = initial("wizard");
        wizard = books.apply(STANDARD.projection(), wizard, Command.spellbook("SPELLBOOK_INITIALIZED",
                new SpellbookInput(STANDARD.rule("character.wizard_spellcasting", "spellcasting.wizard"),
                        STANDARD.initialWizardBook(), STANDARD.initialWizardPrepared())));
        assertEquals(6, wizard.spellbook().learned().size());
        assertEquals(2, wizard.spellbook().prepared().size());
        books.validateState(STANDARD.projection(), wizard);
        assertTrue(STANDARD.metadata().catalog().stream().anyMatch(row -> row.key().equals("spell.burning_hands") && row.executable()));
        assertTrue(STANDARD.metadata().catalog().stream().anyMatch(row -> row.key().equals("spell.alarm") && !row.executable()));
        assertTrue(STANDARD.metadata().catalog().stream().anyMatch(row -> row.key().equals("feature.wizard_arcane_recovery") && row.executable()));
    }

    @Test void weaponArmorAndBreathProfilesKeepTheirActualSrdNumbersAndHouseRuleIdentity() {
        FinalItem plate = (FinalItem) find("equipment.item", "item.plate");
        assertEquals(18, plate.definition().armorClass());
        assertEquals(15, plate.definition().strengthMinimum());
        FinalWeaponAttack greatsword = (FinalWeaponAttack) find("equipment.weapon_attack", "weapon_attack.greatsword");
        assertEquals(2, greatsword.definition().diceCount());
        assertEquals(6, greatsword.definition().diceSides());
        assertEquals(WeaponAbilityPolicy.STRENGTH, greatsword.definition().abilityPolicy());
        FinalWeaponAttack rapier = (FinalWeaponAttack) find("equipment.weapon_attack", "weapon_attack.rapier");
        assertEquals(WeaponAbilityPolicy.FINESSE, rapier.definition().abilityPolicy());
        var breath = STANDARD.projection().projection().entries().stream().map(FinalEntry::entry)
                .filter(MonsterActionEntry.class::isInstance).map(MonsterActionEntry.class::cast)
                .filter(row -> row.identity().entryKey().equals("fire_breath")).findFirst().orElseThrow().actionProfile();
        assertEquals(12, breath.savePolicy().dc());
        assertEquals(6, breath.damageProfile().diceCount());
        assertEquals(15, breath.area().lengthFeet());
        assertEquals(5, breath.recharge().minimumRoll());
        assertEquals(HOUSE_RULE_MODULE, STANDARD.rule("adventure.environment", "environment.antimagic_zone").moduleKey());
        assertEquals(MAGIC_MODULE, STANDARD.rule("adventure.spell", "spell.shield").moduleKey());
    }

    @Test void fighterExtraAttackDoesNotAffectEquipmentChecksOrHealth() {
        var growth = new Schema2CharacterProgressionRules();
        State fighter = advanceFighter(growth, fighterAtLevelFour(growth), 5, GrowthChoices.EMPTY);
        var equipment = new Schema2CharacterEquipmentRules();
        UUID plate = UUID.randomUUID();
        fighter = equipment.apply(STANDARD.projection(), fighter, equipmentCommand("ITEM_ACQUIRED", plate,
                STANDARD.rule("equipment.item", "item.plate"), "CARRIED"), plate);
        fighter = equipment.apply(STANDARD.projection(), fighter, equipmentCommand("ITEM_EQUIPPED", plate, null, "WORN"), UUID.randomUUID());
        assertEquals(18, equipment.derive(STANDARD.projection(), fighter).armorClass());
        var checks = new Schema2CharacterCheckRules();
        var check = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null,
                new CheckInput(STANDARD.rule("adventure.check", "check.srd2014"), "SKILL", "ability.strength",
                        STANDARD.rule("character.proficiency", "proficiency.skill.athletics"), 10, false, false, "DM confirms an ordinary Athletics task."));
        var prepared = checks.prepare(STANDARD.projection(), fighter, check);
        var result = checks.resolve(prepared, 10, null);
        assertEquals(15, result.total());
        fighter = checks.verify(STANDARD.projection(), fighter, check, result);
        var health = new Schema2CharacterHealthRules();
        var damage = new Command("HEALTH_EFFECT_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null,
                new HealthInput(STANDARD.rule("adventure.health", "health.srd2014"),
                        STANDARD.rule("adventure.vital_effect", "vital_effect.dm_damage"), null, "DM confirms one direct environmental damage effect."));
        var damaged = health.resolve(health.prepare(STANDARD.projection(), fighter, damage), List.of(), null, null).state();
        assertEquals(fighter.hitPoints() - 6, damaged.hitPoints());
    }

    @Test void levelTwoWizardRetainsEvocationIdentityAndPreflightsBurningHandsWithoutAcquiringSculptSpells() {
        var material = STANDARD.projection();
        UUID campaign = UUID.randomUUID(), wizardId = UUID.randomUUID(), fighterId = UUID.randomUUID();
        Schema2PreparationIdentity identity = Schema2PreparationIdentity.random();
        var wizardPlan = STANDARD.initialPlan(campaign, wizardId, identity, "wizard", "custom_artisan");
        var fighterPlan = STANDARD.initialPlan(campaign, fighterId, identity, "fighter", "custom_artisan");
        var growth = new Schema2CharacterProgressionRules();
        State wizard = growth.initial(material, wizardPlan, UUID.randomUUID(), ARRAY, STANDARD.initialChoices("wizard"));
        State fighter = growth.initial(material, fighterPlan, UUID.randomUUID(), ARRAY, STANDARD.initialChoices("fighter"));
        var books = new Schema2WizardSpellbookRules();
        RuleIdentity casting = STANDARD.rule("character.wizard_spellcasting", "spellcasting.wizard");
        wizard = books.apply(material, wizard, Command.spellbook("SPELLBOOK_INITIALIZED",
                new SpellbookInput(casting, STANDARD.initialWizardBook(), STANDARD.initialWizardPrepared())));
        wizard = growth.advance(material, growth.prepareAdvance(material, wizard,
                STANDARD.rule("character.class", "class.wizard"), 2, "FIXED_AVERAGE",
                new GrowthChoices(STANDARD.rule("character.subclass", "subclass.evocation"), Map.of(), null, Map.of())), null);
        wizard = books.apply(material, wizard, Command.spellbook("SPELLBOOK_ADVANCED", new SpellbookInput(casting,
                List.of(STANDARD.rule("adventure.spell", "spell.detect_magic"), STANDARD.rule("adventure.spell", "spell.disguise_self")),
                STANDARD.initialWizardPrepared())));
        assertEquals(STANDARD.rule("character.subclass", "subclass.evocation"), wizard.subclasses().getFirst().subclass());
        assertTrue(wizard.features().stream().noneMatch(row -> row.feature().equals(STANDARD.rule("character.feature", "feature.sculpt_spells"))));
        var roster = Map.of(wizardId, new Schema2CombatRules.Context(material, wizardPlan, wizard),
                fighterId, new Schema2CombatRules.Context(material, fighterPlan, fighter));
        var combat = new Schema2CombatRules();
        UUID combatId = UUID.randomUUID();
        var start = Schema2CombatState.Command.start(List.of(
                new Schema2CombatState.Deployment(wizardId, new Schema2CombatState.Position(0, 0), 0),
                new Schema2CombatState.Deployment(fighterId, new Schema2CombatState.Position(1, 0), 1)), List.of(wizardId, fighterId));
        var battle = combat.resolve(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, null, start), sides -> 10).after();
        var burning = new Schema2CombatState.CastInput(casting, STANDARD.rule("adventure.spell", "spell.burning_hands"), List.of(), null, 1, null, 0);
        var spell = new Schema2SpellRules().prepare(roster, battle, wizardId, burning);
        assertEquals(Schema2SpellRules.BURNING_HANDS, spell.spell().algorithm());
        assertNotNull(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, battle,
                Schema2CombatState.Command.cast(wizardId, burning)));
    }

    private static Command equipmentCommand(String kind, UUID item, RuleIdentity rule, String location) {
        return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, null, null,
                new EquipmentInput(item, rule, location, "DM confirms ownership and ordinary noncombat preparation."), null);
    }

    private static State fighterAtLevelFour(Schema2CharacterProgressionRules growth) {
        State state = advanceFighter(growth, initial("fighter"), 2, GrowthChoices.EMPTY);
        var champion = new GrowthChoices(STANDARD.rule("character.subclass", "subclass.champion"), Map.of(), null, Map.of());
        state = advanceFighter(growth, state, 3, champion);
        return advanceFighter(growth, state, 4, new GrowthChoices(null, Map.of("ability.constitution", 2), null, Map.of()));
    }

    private static State advanceFighter(Schema2CharacterProgressionRules growth, State state, int level, GrowthChoices choices) {
        var material = STANDARD.projection();
        return growth.advance(material, growth.prepareAdvance(material, state,
                STANDARD.rule("character.class", "class.fighter"), level, "FIXED_AVERAGE", choices), null);
    }

    private static State withFeatures(State state, List<FeatureState> features) {
        return new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(), state.characterClass(),
                state.abilities(), state.level(), state.proficiencyBonus(), state.hitPoints(), state.maximumHitPoints(), state.classes(),
                state.hitDicePools(), state.subclasses(), features, state.feats(), state.proficiencies(), state.version(), state.resources(),
                state.rest(), state.equipment(), state.health(), state.spellbook());
    }

    private static void assertRejectedAttackFeature(State state) {
        assertEquals("S2_COMBAT_FEATURE_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> Schema2FeatureSupport.attacksPerAttackAction(STANDARD.projection(), state)).getMessage());
    }

    private static State initial(String characterClass) {
        var plan = STANDARD.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), characterClass, "custom_artisan");
        return new Schema2CharacterProgressionRules().initial(STANDARD.projection(), plan, UUID.randomUUID(), ARRAY, STANDARD.initialChoices(characterClass));
    }

    private static FinalRule find(String type, String key) {
        return STANDARD.projection().projection().rules().stream().filter(row -> row.identity().equals(STANDARD.rule(type, key)))
                .findFirst().orElseThrow();
    }
}
