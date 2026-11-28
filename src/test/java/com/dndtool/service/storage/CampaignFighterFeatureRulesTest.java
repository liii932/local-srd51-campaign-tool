package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.Schema2Exception;
import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2CharacterEquipmentRules;
import com.dndtool.service.Schema2CharacterProgressionRules;
import com.dndtool.service.Schema2CharacterRequest;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.Event;
import com.dndtool.service.Schema2CharacterRuntime.History;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2CharacterRuntime.FeatureState;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.ResourceState;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2FeatureSupport;
import com.dndtool.service.Schema2PreparationIdentity;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.storage.CampaignRuleSupportTest.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignFighterFeatureRulesTest {
    private static final String CHARACTERS = BundledCampaignRules.CHARACTER_MODULE;
    private final CampaignDomainEngine engine = new CampaignDomainEngine();
    private final Schema2CharacterEquipmentRules equipment = new Schema2CharacterEquipmentRules();
    private final UUID campaign = UUID.randomUUID(), character = UUID.randomUUID();
    private final UUID preparation = UUID.randomUUID(), operation = UUID.randomUUID();

    @Test void selectedDefenseChangesOnlyWornArmorAndSurvivesLevelGain() throws IOException {
        var context = context();
        var fighter = context.rule("character.class", "class.fighter");
        var defense = context.rule("character.feature", "feature.fighter_defense");
        var plan = plan(context, "fighter");
        var choices = context.initialChoices("fighter").withFightingStyle(defense);
        var history = engine.initial(context.projection(), plan, preparation, context.initialAbilities("fighter"), choices, operation);
        assertEquals(defense, history.events().getFirst().command().growthChoices().fightingStyle());
        var state = history.current();
        assertTrue(state.features().contains(new FeatureState(defense, fighter, 1)));
        int unarmored = ac(context, state);
        assertNull(Schema2FeatureSupport.actionSurgeResource(context.projection(), state));

        UUID shield = UUID.randomUUID();
        state = item(context, state, "ITEM_ACQUIRED", shield, "item.shield", "CARRIED");
        state = item(context, state, "ITEM_EQUIPPED", shield, null, "OFF_HAND");
        assertEquals(unarmored + 2, ac(context, state));
        for (String key : List.of("item.leather", "item.scale_mail", "item.plate")) {
            UUID armor = UUID.randomUUID();
            state = item(context, state, "ITEM_ACQUIRED", armor, key, "CARRIED");
            assertEquals(unarmored + 2, ac(context, state), key);
            state = item(context, state, "ITEM_EQUIPPED", armor, null, "WORN");
            var item = context.projection().projection().rules().stream().filter(FinalItem.class::isInstance)
                    .map(FinalItem.class::cast).filter(row -> row.identity().ruleKey().equals(key)).findFirst().orElseThrow().definition();
            int dexterity = Math.floorDiv(state.abilities().get("ability.dexterity") - 10, 2);
            int modifier = switch (item.kind()) {
                case LIGHT_ARMOR -> dexterity;
                case MEDIUM_ARMOR -> Math.min(2, dexterity);
                default -> 0;
            };
            assertEquals(item.armorClass() + modifier + 2 + 1, ac(context, state), key);
            state = item(context, state, "ITEM_UNEQUIPPED", armor, null, null);
            assertEquals(unarmored + 2, ac(context, state), key);
        }

        var advanced = engine.executeCharacter(context.projection(), plan, history,
                new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE", fighter, GrowthChoices.EMPTY),
                UUID.randomUUID(), sides -> fail("Fixed advancement drew d" + sides));
        var second = advanced.current();
        assertEquals(List.of(new FeatureState(defense, fighter, 1)), second.features().stream()
                .filter(feature -> feature.feature().equals(defense)).toList());
        var surge = fighterActionSurgeResource(fighter);
        assertEquals(surge, Schema2FeatureSupport.actionSurgeResource(context.projection(), second));
        assertTrue(second.resources().contains(new ResourceState(surge, 1, 1, false)));
        var missingSurge = CampaignDomainCodec.toJson(second).getAsJsonObject();
        missingSurge.getAsJsonArray("features").asList().removeIf(row -> row.getAsJsonObject().getAsJsonObject("feature")
                .get("ruleKey").getAsString().equals("feature.test_action_surge"));
        var tampered = CampaignDomainCodec.fromJson(missingSurge, State.class);
        assertEquals("S2_COMBAT_FEATURE_UNSUPPORTED", assertThrows(IllegalArgumentException.class,
                () -> Schema2FeatureSupport.actionSurgeResource(context.projection(), tampered)).getMessage());
        assertEquals("S2_RESOURCE_FEATURE_OWNER_REQUIRED", assertThrows(IllegalArgumentException.class,
                () -> new Schema2CharacterProgressionRules().spendOrdinary(context.projection(), second, surge, 1)).getMessage());
        var restored = CampaignDomainCodec.decode(CampaignDomainCodec.encode(advanced), History.class);
        assertEquals(advanced, restored);
        assertDoesNotThrow(() -> engine.validateCharacter(context.projection(), plan, restored));
    }

    @Test void choiceMustUseTheExactEnabledFighterFeatureAndAffectsTheRequestDigest() throws IOException {
        var context = context();
        var plan = plan(context, "fighter");
        var defense = context.rule("character.feature", "feature.fighter_defense");
        var base = context.initialChoices("fighter").withFightingStyle(null);
        var selected = base.withFightingStyle(defense);
        var foreign = new RuleIdentity("other_provider", "character.feature", defense.ruleKey());
        assertEquals("S2_GROWTH_FIGHTING_STYLE_REQUIRED", assertThrows(IllegalArgumentException.class, () ->
                engine.initial(context.projection(), plan, preparation, context.initialAbilities("fighter"), base, operation)).getMessage());
        assertEquals("S2_GROWTH_FIGHTING_STYLE_CHOICE", assertThrows(IllegalArgumentException.class, () ->
                engine.initial(context.projection(), plan, preparation, context.initialAbilities("fighter"),
                        base.withFightingStyle(foreign), operation)).getMessage());
        assertEquals("S2_GROWTH_FIGHTING_STYLE_CHOICE", assertThrows(IllegalArgumentException.class, () ->
                engine.initial(context.projection(), plan(context, "cleric"), preparation, context.initialAbilities("cleric"),
                        context.initialChoices("cleric").withFightingStyle(defense), operation)).getMessage());
        var history = engine.initial(context.projection(), plan, preparation, context.initialAbilities("fighter"), selected, operation);
        assertEquals("S2_GROWTH_FIGHTING_STYLE_NOT_DUE", assertThrows(IllegalArgumentException.class, () ->
                engine.executeCharacter(context.projection(), plan, history,
                        new Command("CHARACTER_ADVANCED", null, 0, null, "SERVER_ROLL", history.current().characterClass(),
                                GrowthChoices.EMPTY.withFightingStyle(defense)),
                        UUID.randomUUID(), sides -> fail("Rejected advancement drew d" + sides))).getMessage());
        var withoutChoice = new Schema2CharacterRequest(operation, plan.character().identity(), campaign, character,
                preparation, context.binding(), -1, new Command("INITIALIZED", null, 0, null, null, null, base),
                context.initialAbilities("fighter"));
        var original = history.events().getFirst();
        assertNotEquals(withoutChoice.digest(), original.requestDigest());
        var tampered = new Event(operation, withoutChoice.digest(), withoutChoice.command(), null, null, original.after());
        assertEquals("S2_CHARACTER_HISTORY_CORRUPT", assertThrows(IllegalArgumentException.class, () ->
                engine.validateCharacter(context.projection(), plan, new History(original.after(), List.of(tampered)))).getMessage());
    }

    @Test void compositionAndRestoreCheckDefenseUniquenessAndTheExactSurgeResource() throws IOException {
        var valid = CampaignRuleFixtures.fighterFeatureArtifacts();
        var projection = ProductionSchema2Projection.compose(valid);
        var stored = projection.projection();
        assertEquals(stored.rules(), ProductionSchema2Projection.restore(projection.members(), stored.rules(), stored.entries(), stored.chains())
                .projection().rules());

        var defense = feature("feature.second_defense", "CHOICE", FIGHTER_DEFENSE_ALGORITHM, 1, 24);
        assertRejected("S2_FIGHTER_DEFENSE_AMBIGUOUS", valid, List.of(defense), List.of());
        var surge = feature("feature.second_surge", "AUTOMATIC", FIGHTER_ACTION_SURGE_ALGORITHM, 2, 25);
        assertRejected("S2_FIGHTER_ACTION_SURGE_AMBIGUOUS", valid, List.of(surge), List.of());

        var resource = authorRow(CHARACTERS, "rules.json", "rule_key", "resource.fighter.action_surge");
        assertRejected("S2_REFERENCE_MISSING", valid, List.of(), List.of(disabled(resource)));
        var wrongRest = operation("set_field", resource);
        wrongRest.addProperty("field", "recovery_profile");
        wrongRest.addProperty("value", "1-20:LONG_REST");
        assertRejected("S2_TYPE", valid, List.of(), List.of(wrongRest));
    }

    @Test void directRestoreRejectsAnInvalidOriginalSurgeProfileEvenWithAValidFinalRow() throws IOException {
        var valid = CampaignRuleFixtures.fighterFeatureArtifacts();
        var reset = operation("set_field", authorRow(CHARACTERS, "rules.json", "rule_key", "resource.fighter.action_surge"));
        reset.addProperty("field", "recovery_profile");
        reset.addProperty("value", "1-20:SHORT_REST");
        var artifacts = new ArrayList<>(valid);
        artifacts.add(extension("fighter_restore_fixture", valid, List.of(), List.of(), List.of(reset)));
        var projection = ProductionSchema2Projection.compose(artifacts);
        var stored = projection.projection();
        var resource = new RuleIdentity(CHARACTERS, "character.resource", "resource.fighter.action_surge");
        var chains = stored.chains().stream().map(chain -> {
            if (!chain.target().equals(resource) || chain.field() != Field.RECOVERY_PROFILE) return chain;
            assertEquals(2, chain.steps().size());
            var original = chain.steps().getFirst();
            var later = chain.steps().getLast();
            var invalid = new FieldState(StateKind.PRESENT, new TextValue("1-20:LONG_REST"));
            return new FieldChain(chain.target(), chain.field(), List.of(
                    new ChainStep(original.op(), original.before(), invalid, original.provider(), original.loadIndex(),
                            original.authorLocation(), original.origin()),
                    new ChainStep(later.op(), invalid, later.after(), later.provider(), later.loadIndex(),
                            later.authorLocation(), later.origin())));
        }).toList();
        assertEquals("S2_STORED_INVALID", assertThrows(Schema2Exception.class, () ->
                ProductionSchema2Projection.restore(projection.members(), stored.rules(), stored.entries(), chains)).diagnostic().code());
    }

    @Test void dedicatedSurgeResourceCannotBeSpentWithoutAnEnabledFeature() {
        var context = CampaignRuleSupport.validate(BundledCampaignRules.projection());
        var plan = plan(context, "fighter");
        var history = engine.initial(context.projection(), plan, preparation, context.initialAbilities("fighter"),
                context.initialChoices("fighter"), operation);
        var fighter = history.current().characterClass();
        var advanced = engine.executeCharacter(context.projection(), plan, history,
                new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE", fighter, GrowthChoices.EMPTY),
                UUID.randomUUID(), sides -> fail("Fixed advancement drew d" + sides));
        var surge = fighterActionSurgeResource(fighter);
        assertNull(Schema2FeatureSupport.actionSurgeResource(context.projection(), advanced.current()));
        assertEquals("S2_RESOURCE_FEATURE_OWNER_REQUIRED", assertThrows(IllegalArgumentException.class,
                () -> new Schema2CharacterProgressionRules().spendOrdinary(context.projection(), advanced.current(), surge, 1)).getMessage());
    }

    private static void assertRejected(String code, List<ProductionRuleArtifact> valid,
            List<JsonObject> definitions, List<JsonObject> changes) throws IOException {
        var artifacts = new ArrayList<>(valid);
        artifacts.add(extension("fighter_feature_invalid", valid, definitions, List.of(), changes));
        assertEquals(code, assertThrows(Schema2Exception.class, () ->
                ProductionSchema2Projection.compose(artifacts)).diagnostic().code());
    }

    private CampaignRuleContext context() throws IOException {
        return CampaignRuleSupport.validate(ProductionSchema2Projection.compose(CampaignRuleFixtures.fighterFeatureArtifacts()));
    }

    private static JsonObject feature(String key, String mode, String algorithm, int level, int page) {
        var row = authorRow(CHARACTERS, "rules.json", "rule_key", "feature.fighter_fighting_style");
        row.getAsJsonObject("identity").addProperty("module_key", "fighter_feature_invalid");
        row.getAsJsonObject("identity").addProperty("rule_key", key);
        row.addProperty("feature_kind", "FEATURE");
        row.addProperty("execution_mode", mode);
        row.addProperty("execution_algorithm", algorithm);
        row.addProperty("level", level);
        row.addProperty("source_page", page);
        return row;
    }

    private InitialPlan plan(CampaignRuleContext context, String characterClass) {
        return context.initialPlan(campaign, character, Schema2PreparationIdentity.random(), characterClass, "acolyte");
    }

    private int ac(CampaignRuleContext context, State state) {
        return equipment.derive(context.projection(), state).armorClass();
    }

    private State item(CampaignRuleContext context, State state, String kind, UUID id, String key, String location) {
        var rule = key == null ? null : context.rule("equipment.item", key);
        return equipment.apply(context.projection(), state,
                new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, null, null,
                        new EquipmentInput(id, rule, location, "DM confirms equipment preparation."), null),
                kind.equals("ITEM_ACQUIRED") ? id : UUID.randomUUID());
    }
}
