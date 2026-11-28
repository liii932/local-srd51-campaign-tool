package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime.CheckResult;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.Event;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.History;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2RogueRulesTest {
    private static final RuleIdentity ROGUE = new RuleIdentity(BASE, "character.class", "class.rogue");
    private static final RuleIdentity PROGRESSION = new RuleIdentity(BASE, "character.class_progression", "progression.rogue");
    private static final RuleIdentity EXPERTISE = new RuleIdentity(BASE, "character.feature", "feature.rogue_expertise");
    private static final RuleIdentity CUNNING_ACTION = new RuleIdentity(BASE, "character.feature", "feature.rogue_cunning_action");
    private static final RuleIdentity TOOL_EXPERTISE = new RuleIdentity(BASE, "character.tool", "tool.thieves_tools");
    private static final RuleIdentity THIEVES_TOOLS = new RuleIdentity(BASE, "equipment.item", "item.thieves_tools");
    private static final RuleIdentity DEXTERITY_SAVE = new RuleIdentity(BASE, "character.proficiency", "proficiency.save.dexterity");
    private static final EntryIdentity SKILLS = new EntryIdentity(PROGRESSION, "starting_proficiencies", BASE, "skills");
    private static final List<RuleIdentity> SELECTED_SKILLS = List.of(ACROBATICS, ATHLETICS, ARCANA, STEALTH);
    private static final GrowthChoices CHOICES =
            new GrowthChoices(null, Map.of(), null, Map.of(SKILLS, SELECTED_SKILLS));

    private final CampaignDomainEngine engine = new CampaignDomainEngine();
    private final ProductionSchema2Projection material = compose(roguePackages());
    private final InitialPlan plan = roguePlan(material);
    private final UUID operation = UUID.randomUUID();

    @Test void bindsExactLevelOneChoicesAndGrantsCunningActionAtLevelTwoOnly() {
        var initial = initialize(List.of(STEALTH, TOOL_EXPERTISE));
        var first = initial.current();
        assertEquals(initial.events().getFirst().command().growthChoices().expertise(), first.expertise());
        assertFalse(Schema2FeatureSupport.hasCunningAction(material, first));
        var second = engine.executeCharacter(material, plan, initial,
                new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE", ROGUE, GrowthChoices.EMPTY),
                UUID.randomUUID(), sides -> fail("Fixed HP drew d" + sides));
        assertTrue(Schema2FeatureSupport.hasCunningAction(material, second.current()));
        assertEquals(first.expertise(), second.current().expertise());
        engine.validateCharacter(material, plan, second);
        assertEquals("S2_GROWTH_LEVEL_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> engine.executeCharacter(material, plan, second,
                        new Command("CHARACTER_ADVANCED", null, 0, null, "SERVER_ROLL", ROGUE, GrowthChoices.EMPTY),
                        UUID.randomUUID(), sides -> fail("Unsupported growth drew d" + sides))).getMessage());
    }

    @Test void rejectsMissingDuplicateAndUnqualifiedChoicesBeforeChangingTheCharacter() {
        for (var invalid : List.of(List.<RuleIdentity>of(), List.of(STEALTH, STEALTH), List.of(STEALTH),
                List.of(STEALTH, TOOL), List.of(STEALTH, OTHER_SKILL), List.of(TOOL, TOOL_EXPERTISE))) {
            assertEquals("S2_ROGUE_EXPERTISE_CHOICES",
                    assertThrows(IllegalArgumentException.class, () -> initialize(invalid), invalid.toString()).getMessage());
        }
        assertEquals("S2_ROGUE_EXPERTISE_CHOICES", assertThrows(IllegalArgumentException.class, () ->
                engine.initial(material, fullPlan(material, IDENTITY, CAMPAIGN, CHARACTER), PREPARATION, fullAbilities(),
                        INITIAL_CHOICES.withExpertise(List.of(STEALTH, TOOL_EXPERTISE)), operation)).getMessage());
        var initial = initialize(List.of(STEALTH, ACROBATICS));
        assertEquals("S2_ROGUE_EXPERTISE_NOT_DUE", assertThrows(IllegalArgumentException.class,
                () -> engine.executeCharacter(material, plan, initial,
                        new Command("CHARACTER_ADVANCED", null, 0, null, "SERVER_ROLL", ROGUE,
                                GrowthChoices.EMPTY.withExpertise(List.of(STEALTH, ACROBATICS))),
                        UUID.randomUUID(), sides -> fail("Invalid choice drew d" + sides))).getMessage());
    }

    @Test void rerunsTheSelectedExactTargetsEvenWhenAForgedDigestMatchesTheAlteredRequest() {
        var initial = initialize(List.of(STEALTH, TOOL_EXPERTISE));
        var original = initial.events().getFirst();
        var after = original.after();
        var command = new Command("INITIALIZED", null, 0, null, null, null,
                CHOICES.withExpertise(List.of(ATHLETICS, ARCANA)));
        var request = new Schema2CharacterRequest(operation, after.identity(), CAMPAIGN, CHARACTER, PREPARATION,
                Schema2RulesBinding.from(material), -1, command, after.abilities());
        assertNotEquals(original.requestDigest(), request.digest());
        var altered = new Event(operation, request.digest(), command, null, null, after);
        assertEquals("S2_CHARACTER_HISTORY_CORRUPT", assertThrows(IllegalArgumentException.class, () ->
                engine.validateCharacter(material, plan, new History(after, List.of(altered)))).getMessage());
    }

    @Test void activePassiveAndContestedSkillChecksUseOneDoubledContributionAndToolsMustBePrepared() {
        var checks = new Schema2CharacterCheckRules();
        var initialized = initialize(List.of(STEALTH, TOOL_EXPERTISE));
        var first = initialized.current();
        assertEquals(4, Schema2CharacterCheckRules.checkProficiencyBonus(material, first, STEALTH));
        assertEquals(2, Schema2CharacterCheckRules.checkProficiencyBonus(material, first, ATHLETICS));
        assertEquals(0, Schema2CharacterCheckRules.checkProficiencyBonus(material, first, OTHER_SKILL));
        assertEquals(2, first.proficiencies().stream().filter(fact -> fact.target().equals(TOOL_EXPERTISE)).count());
        assertEquals(4, Schema2CharacterCheckRules.checkProficiencyBonus(material, first, TOOL_EXPERTISE));
        assertEquals(2, Schema2SavingThrowRules.proficiency(new Schema2CombatRules.Context(material, plan, first), "ability.dexterity"));
        assertEquals(1, Schema2FeatureSupport.attacksPerAttackAction(material, first));
        assertEquals(20, Schema2FeatureSupport.weaponCriticalMinimum(material, first));
        var stealth = check("SKILL", "ability.dexterity", STEALTH, 15, false, false);
        var checked = engine.executeCharacter(material, plan, initialized, stealth, UUID.randomUUID(), sides -> 10);
        CheckResult result = checked.events().getLast().checkResult();
        assertEquals(15, result.total());
        assertEquals(EXPERTISE, result.expertiseRule());
        engine.validateCharacter(material, plan, checked);

        var passive = new Command(Schema2CharacterRuntime.PASSIVE_CHECK, null, 0, null, null, null,
                GrowthChoices.EMPTY, null, stealth.checkInput());
        var passiveResult = checks.resolvePassive(checks.prepare(material, first, passive));
        assertNull(passiveResult.firstD20());
        assertEquals(15, passiveResult.total());
        assertEquals(EXPERTISE, passiveResult.expertiseRule());
        var roll = checks.prepareContestRoll(material, first, new Schema2CharacterCheckRules.RollInput(
                CHECK, "SKILL", "ability.dexterity", STEALTH, false, false));
        assertEquals(4, checks.resolveRoll(roll, 10, null).proficiencyBonus());

        var tool = check("TOOL", "ability.dexterity", TOOL_EXPERTISE, 15, false, false);
        assertEquals("S2_CHECK_TOOL_UNAVAILABLE", assertThrows(IllegalArgumentException.class,
                () -> checks.prepare(material, first, tool)).getMessage());
        var equipment = new Schema2CharacterEquipmentRules();
        UUID kit = UUID.randomUUID();
        var carried = equipment.apply(material, first, equipment("ITEM_ACQUIRED", kit, THIEVES_TOOLS, "CARRIED"), kit);
        var equipped = equipment.apply(material, carried, equipment("ITEM_EQUIPPED", kit, null, "MAIN_HAND"), UUID.randomUUID());
        assertEquals(4, checks.resolve(checks.prepare(material, equipped, tool), 10, null).proficiencyBonus());
    }

    @Test void publishedShapeAndUniquenessAreRequiredAtComposition() {
        var duplicate = roguePackages();
        Schema2GrantFixtures.edit(duplicate, 0, "rules.json", rows -> rows.getAsJsonArray().add(feature(
                new RuleIdentity(BASE, "character.feature", "feature.duplicate_expertise"), 1, 39, "CHOICE", ROGUE_EXPERTISE_ALGORITHM)));
        assertEquals("S2_ROGUE_EXPERTISE_AMBIGUOUS",
                assertThrows(Schema2Exception.class, () -> compose(duplicate)).diagnostic().code());
        var invalid = roguePackages();
        Schema2GrantFixtures.edit(invalid, 0, "rules.json", rows -> rows.getAsJsonArray().add(feature(
                new RuleIdentity(BASE, "character.feature", "feature.early_cunning"), 1, 40, "AUTOMATIC", ROGUE_CUNNING_ACTION_ALGORITHM)));
        assertEquals("S2_TYPE", assertThrows(Schema2Exception.class, () -> compose(invalid)).diagnostic().code());
        var stored = material.projection();
        assertEquals(stored.rules(), ProductionSchema2Projection.restore(material.members(), stored.rules(), stored.entries(),
                stored.chains()).projection().rules());
    }

    private History initialize(List<RuleIdentity> targets) {
        return engine.initial(material, plan, PREPARATION, fullAbilities(), CHOICES.withExpertise(targets), operation);
    }

    private static InitialPlan roguePlan(ProductionSchema2Projection material) {
        var sources = new Schema2InitialProficiencyRules.Sources(new RuleIdentity(BASE, "character.race", "race.human"),
                null, new RuleIdentity(BASE, "character.background", "background.guard"), ROGUE);
        var plan = new Schema2InitialCharacterPlanService().prepareMaterial(IDENTITY, Schema2RulesBinding.from(material),
                material, new Schema2InitialProficiencyRules.Request(sources, Map.of()));
        return new InitialPlan(CAMPAIGN, CHARACTER, plan);
    }

    private static ProductionSchema2Projection compose(List<Map<String, byte[]>> packages) {
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages));
    }

    private static List<Map<String, byte[]>> roguePackages() {
        var packages = new ArrayList<>(packages("normal"));
        Schema2GrantFixtures.edit(packages, 0, "rules.json", rows -> {
            var definitions = rows.getAsJsonArray();
            definitions.add(definition(ROGUE, 39));
            var progression = definitions.asList().stream().map(value -> value.getAsJsonObject())
                    .filter(row -> row.getAsJsonObject("identity").get("rule_key").getAsString().equals("progression.guard"))
                    .findFirst().orElseThrow().deepCopy();
            progression.add("identity", jsonIdentity(PROGRESSION));
            progression.add("owner_class", jsonIdentity(ROGUE));
            progression.addProperty("hit_die_sides", 8);
            progression.addProperty("supported_maximum_level", 2);
            definitions.add(progression);
            definitions.add(feature(EXPERTISE, 1, 39, "CHOICE", ROGUE_EXPERTISE_ALGORITHM));
            definitions.add(feature(CUNNING_ACTION, 2, 40, "AUTOMATIC", ROGUE_CUNNING_ACTION_ALGORITHM));
            var savingThrow = definition(DEXTERITY_SAVE, 39);
            savingThrow.addProperty("category", "SAVING_THROW");
            savingThrow.addProperty("ability_key", "ability.dexterity");
            definitions.add(savingThrow);
            var tool = definition(TOOL_EXPERTISE, 70);
            tool.addProperty("category", "KIT");
            definitions.add(tool);
            var item = definition(THIEVES_TOOLS, 70);
            item.addProperty("kind", "TOOL");
            item.addProperty("armor_class", 0);
            item.addProperty("strength_minimum", 0);
            item.addProperty("stealth_disadvantage", false);
            item.addProperty("hands", 1);
            item.add("proficiency", jsonIdentity(TOOL_EXPERTISE));
            item.addProperty("algorithm", "SRD_2014_EQUIPMENT_V1");
            definitions.add(item);
        });
        Schema2GrantFixtures.edit(packages, 0, "growth.json", rows -> {
            var entries = rows.getAsJsonArray();
            for (int level : List.of(1, 2)) {
                var row = entry(PROGRESSION, "levels", "level_" + level);
                row.addProperty("level", level);
                row.addProperty("asi_available", false);
                entries.add(row);
            }
            var skillSlot = entry(PROGRESSION, "starting_proficiencies", SKILLS.entryKey());
            var skills = new JsonObject();
            skills.addProperty("kind", "CHOICE");
            skills.addProperty("count", 4);
            skills.add("candidates", jsonIdentity(SKILL_SET));
            skillSlot.add("growth_grant", skills);
            entries.add(skillSlot);
            var stealth = entry(SKILL_SET, "members", "stealth");
            stealth.add("proficiency", jsonIdentity(STEALTH));
            entries.add(stealth);
            var toolSlot = entry(PROGRESSION, "starting_proficiencies", "thieves_tools");
            var tool = new JsonObject();
            tool.addProperty("kind", "FIXED");
            tool.add("target", jsonIdentity(TOOL_EXPERTISE));
            toolSlot.add("growth_grant", tool);
            entries.add(toolSlot);
            var saveSlot = entry(PROGRESSION, "starting_proficiencies", "dexterity_save");
            var save = new JsonObject();
            save.addProperty("kind", "FIXED");
            save.add("target", jsonIdentity(DEXTERITY_SAVE));
            saveSlot.add("growth_grant", save);
            entries.add(saveSlot);
            var background = new RuleIdentity(BASE, "character.background", "background.guard");
            var duplicateToolSlot = entry(background, "proficiency_grants", "rogue_tool_training");
            var initialTool = tool.deepCopy();
            initialTool.addProperty("category", "TOOL");
            duplicateToolSlot.add("grant", initialTool);
            entries.add(duplicateToolSlot);
        });
        return packages;
    }

    private static JsonObject definition(RuleIdentity identity, int page) {
        var row = new JsonObject();
        row.add("identity", jsonIdentity(identity));
        row.addProperty("display_name", identity.ruleKey());
        row.addProperty("description", "Rogue rule fixture.");
        row.addProperty("source_page", page);
        return row;
    }

    private static JsonObject feature(RuleIdentity identity, int level, int page, String mode, String algorithm) {
        var row = definition(identity, page);
        row.add("owner", jsonIdentity(ROGUE));
        row.addProperty("level", level);
        row.addProperty("feature_kind", "FEATURE");
        row.addProperty("execution_mode", mode);
        row.addProperty("execution_algorithm", algorithm);
        return row;
    }

    private static JsonObject entry(RuleIdentity rule, String collection, String key) {
        var identity = new JsonObject();
        identity.addProperty("kind", "entry");
        identity.add("rule", jsonIdentity(rule));
        identity.addProperty("module_key", BASE);
        identity.addProperty("collection_key", collection);
        identity.addProperty("entry_key", key);
        var row = new JsonObject();
        row.add("identity", identity);
        return row;
    }
}
