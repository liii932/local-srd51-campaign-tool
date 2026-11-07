package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthInput;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.Schema2CharacterProgressionRules;
import com.dndtool.service.Schema2CharacterCheckRules;
import com.dndtool.service.Schema2CharacterEquipmentRules;
import com.dndtool.service.Schema2CharacterHealthRules;
import com.dndtool.service.Schema2CombatRules;
import com.dndtool.service.Schema2CombatState;
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

    @Test void productionBundlesMatchTheAuthoredSourcesAndRestoreTheirCompleteTypedProjection() throws Exception {
        List<String> directories = List.of("srd51-runtime", "srd51-characters", "srd51-magic", "dm-runtime-house-rules");
        assertEquals(4, artifacts().size());
        Path source = Path.of("rule-packages", "srd51-runtime");
        for (int index = 0; index < artifacts().size(); index++) {
            ProductionRuleArtifact artifact = ProductionRuleArtifact.read(artifacts().get(index).originalZip());
            assertArrayEquals(artifacts().get(index).logicalJson(), artifact.logicalJson());
            for (var member : artifact.originalMembers().entrySet()) {
                Path expected = switch (member.getKey()) {
                    case "NOTICE" -> source.resolve("notice.md");
                    case "README.md" -> source.resolve("package-guide.md");
                    default -> source.resolve(directories.get(index)).resolve(member.getKey());
                };
                assertArrayEquals(Files.readAllBytes(expected), member.getValue(), expected.toString());
            }
            assertTrue(artifact.originalMembers().containsKey("NOTICE"));
        }
        var material = projection();
        var restored = ProductionSchema2Projection.restore(material.members(), material.projection().rules(),
                material.projection().entries(), material.projection().chains());
        assertArrayEquals(material.configurationJson(), restored.configurationJson());
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(material.chainsJson(), restored.chainsJson());
        assertTrue(material.projection().rules().stream().noneMatch(row -> row.identity().ruleKey().contains("guard")));
    }

    @Test void realStandardArrayInitializersConsumeTheExactSourcesAndClassChoices() {
        var growth = new Schema2CharacterProgressionRules();
        for (String characterClass : List.of("fighter", "cleric", "wizard")) {
            var plan = initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), characterClass, "custom_artisan");
            State state = growth.initial(projection(), plan, UUID.randomUUID(), ARRAY, initialChoices(characterClass));
            assertEquals(switch (characterClass) { case "fighter" -> 11; case "cleric" -> 9; default -> 7; }, state.maximumHitPoints());
            assertEquals(1, state.level());
            assertEquals(2, state.proficiencyBonus());
            assertEquals(rule("character.class", "class." + characterClass), state.characterClass());
            assertTrue(plan.character().grants().stream().anyMatch(grant ->
                    grant.target().equals(rule("character.tool", "tool.smith_tools"))));
            assertEquals(characterClass.equals("cleric") ? 1 : 0, state.subclasses().size());
        }
        var acolyte = initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), "class.cleric", "background.acolyte");
        assertEquals(4, acolyte.character().grants().size());
        assertThrows(IllegalArgumentException.class, () -> initialChoices("bard"));
        assertThrows(IllegalArgumentException.class, () -> rule("adventure.spell", "spell.fireball"));
    }

    @Test void fighterGrowthUsesRealSubclassAsiAndMulticlassPrerequisites() {
        var material = projection();
        var growth = new Schema2CharacterProgressionRules();
        State state = initial("fighter");
        RuleIdentity fighter = rule("character.class", "class.fighter");
        state = growth.advance(material, growth.prepareAdvance(material, state, fighter, 2, "FIXED_AVERAGE", GrowthChoices.EMPTY), null);
        var champion = new GrowthChoices(rule("character.subclass", "subclass.champion"), Map.of(), null, Map.of());
        state = growth.advance(material, growth.prepareAdvance(material, state, fighter, 3, "FIXED_AVERAGE", champion), null);
        state = growth.advance(material, growth.prepareAdvance(material, state, fighter, 4, "FIXED_AVERAGE",
                new GrowthChoices(null, Map.of("ability.constitution", 2), null, Map.of())), null);
        assertEquals(36, state.maximumHitPoints());
        assertEquals(15, state.abilities().get("ability.constitution"));
        State before = state;
        assertThrows(IllegalArgumentException.class, () -> growth.prepareAdvance(material, before,
                rule("character.class", "class.wizard"), 1, "FIXED_AVERAGE", GrowthChoices.EMPTY));
        assertEquals(4, before.level());
        State cleric = initial("cleric");
        assertThrows(IllegalArgumentException.class, () -> growth.prepareAdvance(material, cleric,
                rule("character.class", "class.cleric"), 2, "FIXED_AVERAGE", GrowthChoices.EMPTY));
    }

    @Test void wizardBookUsesRegisteredExecutableAndExplicitMetadataSpells() {
        var books = new Schema2WizardSpellbookRules();
        State wizard = initial("wizard");
        wizard = books.apply(projection(), wizard, Command.spellbook("SPELLBOOK_INITIALIZED",
                new SpellbookInput(rule("character.wizard_spellcasting", "spellcasting.wizard"),
                        initialWizardBook(), initialWizardPrepared())));
        assertEquals(6, wizard.spellbook().learned().size());
        assertEquals(2, wizard.spellbook().prepared().size());
        books.validateState(projection(), wizard);
        assertTrue(metadata().catalog().stream().anyMatch(row -> row.key().equals("spell.burning_hands") && row.executable()));
        assertTrue(metadata().catalog().stream().anyMatch(row -> row.key().equals("spell.alarm") && !row.executable()));
        assertTrue(metadata().catalog().stream().anyMatch(row -> row.key().equals("feature.wizard_arcane_recovery") && row.executable()));
        assertTrue(metadata().catalog().stream().anyMatch(row -> row.key().equals("feature.fighter_extra_attack") && !row.executable()));
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
        var breath = projection().projection().entries().stream().map(FinalEntry::entry)
                .filter(MonsterActionEntry.class::isInstance).map(MonsterActionEntry.class::cast)
                .filter(row -> row.identity().entryKey().equals("fire_breath")).findFirst().orElseThrow().actionProfile();
        assertEquals(12, breath.savePolicy().dc());
        assertEquals(6, breath.damageProfile().diceCount());
        assertEquals(15, breath.area().lengthFeet());
        assertEquals(5, breath.recharge().minimumRoll());
        assertEquals(HOUSE_RULE_MODULE, rule("adventure.environment", "environment.antimagic_zone").moduleKey());
        assertEquals(MAGIC_MODULE, rule("adventure.spell", "spell.shield").moduleKey());
    }

    @Test void catalogOnlyFighterOptionsDoNotBecomeUnownedEquipmentCheckOrHealthInfluences() {
        State fighter = initial("fighter");
        assertTrue(fighter.features().isEmpty());
        var equipment = new Schema2CharacterEquipmentRules();
        UUID plate = UUID.randomUUID();
        fighter = equipment.apply(projection(), fighter, equipmentCommand("ITEM_ACQUIRED", plate,
                rule("equipment.item", "item.plate"), "CARRIED"), plate);
        fighter = equipment.apply(projection(), fighter, equipmentCommand("ITEM_EQUIPPED", plate, null, "WORN"), UUID.randomUUID());
        assertEquals(18, equipment.derive(projection(), fighter).armorClass());
        var checks = new Schema2CharacterCheckRules();
        var check = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null,
                new CheckInput(rule("adventure.check", "check.srd2014"), "SKILL", "ability.strength",
                        rule("character.proficiency", "proficiency.skill.athletics"), 10, false, false, "DM confirms an ordinary Athletics task."));
        var prepared = checks.prepare(projection(), fighter, check);
        var result = checks.resolve(prepared, 10, null);
        assertEquals(14, result.total());
        fighter = checks.verify(projection(), fighter, check, result);
        var health = new Schema2CharacterHealthRules();
        var damage = new Command("HEALTH_EFFECT_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null,
                new HealthInput(rule("adventure.health", "health.srd2014"),
                        rule("adventure.vital_effect", "vital_effect.dm_damage"), null, "DM confirms one direct environmental damage effect."));
        fighter = health.resolve(health.prepare(projection(), fighter, damage), List.of(), null, null).state();
        assertEquals(5, fighter.hitPoints());
    }

    @Test void levelTwoWizardRetainsEvocationIdentityAndPreflightsBurningHandsWithoutAcquiringSculptSpells() {
        var material = projection();
        UUID campaign = UUID.randomUUID(), wizardId = UUID.randomUUID(), fighterId = UUID.randomUUID();
        Schema2PreparationIdentity identity = Schema2PreparationIdentity.random();
        var wizardPlan = initialPlan(campaign, wizardId, identity, "wizard", "custom_artisan");
        var fighterPlan = initialPlan(campaign, fighterId, identity, "fighter", "custom_artisan");
        var growth = new Schema2CharacterProgressionRules();
        State wizard = growth.initial(material, wizardPlan, UUID.randomUUID(), ARRAY, initialChoices("wizard"));
        State fighter = growth.initial(material, fighterPlan, UUID.randomUUID(), ARRAY, initialChoices("fighter"));
        var books = new Schema2WizardSpellbookRules();
        RuleIdentity casting = rule("character.wizard_spellcasting", "spellcasting.wizard");
        wizard = books.apply(material, wizard, Command.spellbook("SPELLBOOK_INITIALIZED",
                new SpellbookInput(casting, initialWizardBook(), initialWizardPrepared())));
        wizard = growth.advance(material, growth.prepareAdvance(material, wizard,
                rule("character.class", "class.wizard"), 2, "FIXED_AVERAGE",
                new GrowthChoices(rule("character.subclass", "subclass.evocation"), Map.of(), null, Map.of())), null);
        wizard = books.apply(material, wizard, Command.spellbook("SPELLBOOK_ADVANCED", new SpellbookInput(casting,
                List.of(rule("adventure.spell", "spell.detect_magic"), rule("adventure.spell", "spell.disguise_self")),
                initialWizardPrepared())));
        assertEquals(rule("character.subclass", "subclass.evocation"), wizard.subclasses().getFirst().subclass());
        assertTrue(wizard.features().stream().noneMatch(row -> row.feature().equals(rule("character.feature", "feature.sculpt_spells"))));
        var roster = Map.of(wizardId, new Schema2CombatRules.Context(material, wizardPlan, wizard),
                fighterId, new Schema2CombatRules.Context(material, fighterPlan, fighter));
        var combat = new Schema2CombatRules();
        UUID combatId = UUID.randomUUID();
        var start = Schema2CombatState.Command.start(List.of(
                new Schema2CombatState.Deployment(wizardId, new Schema2CombatState.Position(0, 0), 0),
                new Schema2CombatState.Deployment(fighterId, new Schema2CombatState.Position(1, 0), 1)), List.of(wizardId, fighterId));
        var battle = combat.resolve(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, null, start), sides -> 10).after();
        var burning = new Schema2CombatState.CastInput(casting, rule("adventure.spell", "spell.burning_hands"), List.of(), null, 1, null, 0);
        var spell = new Schema2SpellRules().prepare(roster, battle, wizardId, burning);
        assertEquals(Schema2SpellRules.BURNING_HANDS, spell.spell().algorithm());
        assertNotNull(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, battle,
                Schema2CombatState.Command.cast(wizardId, burning)));
    }

    private static Command equipmentCommand(String kind, UUID item, RuleIdentity rule, String location) {
        return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, null, null,
                new EquipmentInput(item, rule, location, "DM confirms ownership and ordinary noncombat preparation."), null);
    }

    private static State initial(String characterClass) {
        var plan = initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), characterClass, "custom_artisan");
        return new Schema2CharacterProgressionRules().initial(projection(), plan, UUID.randomUUID(), ARRAY, initialChoices(characterClass));
    }

    private static FinalRule find(String type, String key) {
        return projection().projection().rules().stream().filter(row -> row.identity().equals(rule(type, key)))
                .findFirst().orElseThrow();
    }
}
