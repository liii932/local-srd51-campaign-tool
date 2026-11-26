package com.dndtool.module.composition;

import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2CharacterCheckRules;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.CheckResult;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.storage.BundledCampaignRules;
import com.dndtool.service.storage.CampaignRuleSupport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static com.dndtool.module.composition.ProductionRuleArtifactTest.zip;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2StonecunningCompositionTest {
    private static final String VARIANT = BundledCampaignRules.CHECK_VARIANT;
    private static final RuleIdentity STONECUNNING =
            new RuleIdentity("srd51_characters", "character.stonecunning", "stonecunning.dwarf");
    private static final int CHARACTERS_INDEX = 1;
    private static final int VARIANT_INDEX = 3;

    @Test void standardAndVariantRestoreExactIdentitiesMultiplierAndFieldChain() {
        var standard = BundledCampaignRules.projection();
        var variant = BundledCampaignRules.projection(VARIANT);
        var row = trait(standard);
        assertTrue(row.enabled());
        assertEquals(STONECUNNING_DWARF, row.definition().owner());
        assertEquals(STONECUNNING_HISTORY, row.definition().skill());
        assertEquals(STONECUNNING_ALGORITHM, row.definition().algorithm());
        assertEquals(3, row.definition().sourcePage());
        assertEquals(2, row.definition().proficiencyMultiplier());
        assertEquals(3, trait(variant).definition().proficiencyMultiplier());

        var chain = multiplierChain(variant);
        assertEquals(List.of(new IntegerValue(2), new IntegerValue(3)),
                chain.steps().stream().map(step -> step.after().value()).toList());
        assertEquals("srd51_characters", chain.steps().getFirst().provider().moduleKey());
        assertEquals("dm_runtime_house_rules", chain.steps().getLast().provider().moduleKey());
        assertEquals("changes.json", chain.steps().getLast().authorLocation().path());
        assertEquals(variant.members().getLast().rawZipSha256(), chain.steps().getLast().authorLocation().rawZipSha256());

        for (var material : List.of(standard, variant)) {
            var restored = restore(material, material.projection().chains());
            assertArrayEquals(material.configurationJson(), restored.configurationJson());
            assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
            assertArrayEquals(material.chainsJson(), restored.chainsJson());
        }

        var incomplete = variant.projection().chains().stream().filter(value -> !value.equals(chain)).toList();
        assertCode("S2_STORED_INVALID", () -> restore(variant, incomplete));
        var wrongProvider = variant.projection().chains().stream().map(value -> {
            if (!value.equals(chain)) return value;
            var change = chain.steps().getLast();
            return new FieldChain(chain.target(), chain.field(), List.of(chain.steps().getFirst(),
                    new ChainStep(change.op(), change.before(), change.after(), chain.steps().getFirst().provider(),
                            change.loadIndex(), change.authorLocation(), change.origin())));
        }).toList();
        assertCode("S2_STORED_INVALID", () -> restore(variant, wrongProvider));
    }

    @Test void rejectsWrongExactReferencesAndOutOfRangeAuthorValuesAndOverrides() {
        assertBadField("owner", identity(new RuleIdentity("spoof", "character.race", "race.dwarf")));
        assertBadField("skill", identity(new RuleIdentity("spoof", "character.proficiency", "proficiency.skill.history")));
        assertBadField("skill", identity(new RuleIdentity("srd51_runtime", "character.proficiency", "proficiency.skill.arcana")));
        assertBadField("source_page", new JsonPrimitive(4));
        assertBadField("algorithm", new JsonPrimitive("UNKNOWN"));
        for (var invalid : List.of(new JsonPrimitive(0), new JsonPrimitive(4), new JsonPrimitive("2"), new JsonPrimitive(2.5)))
            assertBadField("proficiency_multiplier", invalid);
    }

    @Test void multiplierOneGrantsProficiencyAndDisablingRemovesIt() {
        var variant = artifacts();
        edit(variant, VARIANT_INDEX, "changes.json", rows -> rows.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> row.get("field").getAsString().equals("proficiency_multiplier")).findFirst().orElseThrow()
                .addProperty("value", 1));
        var enabled = ProductionSchema2Projection.compose(variant);
        assertEquals(1, trait(enabled).definition().proficiencyMultiplier());

        UUID campaign = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        assertEquals(new CheckResult(10, null, 10, -1, 2, 11, true, false, false, 2, STONECUNNING),
                resolveStoneworkCheck(enabled, campaign, character));

        changes(variant, operation("set_enabled", STONECUNNING, null, new JsonPrimitive(false)));
        var disabled = ProductionSchema2Projection.compose(variant);
        assertFalse(trait(disabled).enabled());
        assertEquals(new CheckResult(10, null, 10, -1, 0, 9, false, false, false, 2, null),
                resolveStoneworkCheck(disabled, campaign, character));
    }

    @Test void declarationRequiresAnExplicitCoreDependency() {
        var variant = artifacts();
        var members = variant.getLast().originalMembers();
        var header = read(members, "author-package.json").getAsJsonObject();
        header.addProperty("module_key", "local_stonecunning");
        header.getAsJsonArray("dependencies").remove(0);
        header.getAsJsonArray("compatible_combinations").get(0).getAsJsonObject().remove("srd51_runtime");
        header.getAsJsonArray("files").remove(1);
        var row = declaration(variant);
        row.getAsJsonObject("identity").addProperty("module_key", "local_stonecunning");
        var rows = new JsonArray();
        rows.add(row);
        variant.add(ProductionRuleArtifact.read(zip(Map.of("author-package.json", bytes(header), "rules.json", bytes(rows)), 0)));
        assertCode("S2_DIRECT_DEPENDENCY", () -> ProductionSchema2Projection.compose(variant));
    }

    @Test void activeTraitRequiresIntelligenceHistoryAndAUniqueEnabledProfile() {
        var variant = artifacts();
        changes(variant, operation("set_field", STONECUNNING_HISTORY, "ability_key", new JsonPrimitive("ability.wisdom")));
        assertCode("S2_TYPE", () -> ProductionSchema2Projection.compose(variant));

        var disabled = artifacts();
        changes(disabled, operation("set_enabled", STONECUNNING, null, new JsonPrimitive(false)));
        changes(disabled, operation("set_field", STONECUNNING_HISTORY, "ability_key", new JsonPrimitive("ability.wisdom")));
        assertFalse(trait(ProductionSchema2Projection.compose(disabled)).enabled());

        var duplicate = artifacts();
        var extra = declaration(duplicate);
        extra.getAsJsonObject("identity").addProperty("rule_key", "stonecunning.duplicate");
        edit(duplicate, CHARACTERS_INDEX, "rules.json", rows -> rows.add(extra));
        assertCode("S2_STONECUNNING_AMBIGUOUS", () -> ProductionSchema2Projection.compose(duplicate));
        changes(duplicate, operation("set_enabled", STONECUNNING, null, new JsonPrimitive(false)));
        assertFalse(trait(ProductionSchema2Projection.compose(duplicate)).enabled());
    }

    @Test void restoredOriginalSkillCannotBeRepairedByALaterOverride() {
        var variant = artifacts();
        changes(variant, operation("set_field", STONECUNNING_HISTORY, "ability_key", new JsonPrimitive("ability.intelligence")));
        var material = ProductionSchema2Projection.compose(variant);
        var forged = material.projection().chains().stream().map(chain -> {
            if (!chain.target().equals(STONECUNNING_HISTORY) || chain.field() != Field.ABILITY_KEY) return chain;
            var initial = chain.steps().getFirst();
            var override = chain.steps().getLast();
            var wisdom = new FieldState(StateKind.PRESENT, new TextValue("ability.wisdom"));
            return new FieldChain(chain.target(), chain.field(), List.of(
                    new ChainStep(initial.op(), initial.before(), wisdom, initial.provider(), initial.loadIndex(),
                            initial.authorLocation(), initial.origin()),
                    new ChainStep(override.op(), wisdom, override.after(), override.provider(), override.loadIndex(),
                            override.authorLocation(), override.origin())));
        }).toList();
        assertCode("S2_STORED_INVALID", () -> restore(material, forged));
    }

    private static CheckResult resolveStoneworkCheck(ProductionSchema2Projection material, UUID campaign, UUID character) {
        var context = CampaignRuleSupport.validate(material);
        var plan = context.initialPlan(campaign, character, Schema2PreparationIdentity.random(),
                "fighter", "custom_artisan", "dwarf");
        var state = new CampaignDomainEngine().initial(material, plan, UUID.randomUUID(), context.initialAbilities("fighter"),
                context.initialChoices("fighter"), UUID.randomUUID()).current();
        assertTrue(state.proficiencies().stream().noneMatch(fact -> fact.target().equals(STONECUNNING_HISTORY)));
        var input = new CheckInput(context.checkRule(), "SKILL", "ability.intelligence", STONECUNNING_HISTORY,
                11, false, false, "Identify the origin of the stonework.", CheckInput.STONEWORK_ORIGIN);
        var command = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, input);
        var checks = new Schema2CharacterCheckRules();
        return checks.resolve(checks.prepare(material, plan, state, command), 10, null);
    }

    private static void assertBadField(String field, JsonElement value) {
        var authored = artifacts();
        assertCode("S2_TYPE", () -> edit(authored, CHARACTERS_INDEX, "rules.json",
                rows -> declaration(rows).add(field, value)));

        var overridden = artifacts();
        edit(overridden, VARIANT_INDEX, "changes.json",
                rows -> rows.asList().removeIf(row -> row.getAsJsonObject().get("field").getAsString().equals(field)));
        assertCode("S2_TYPE", () -> changes(overridden, operation("set_field", STONECUNNING, field, value)));
    }

    private static ArrayList<ProductionRuleArtifact> artifacts() {
        return new ArrayList<>(BundledCampaignRules.artifacts(VARIANT));
    }

    private static JsonObject declaration(List<ProductionRuleArtifact> artifacts) {
        return declaration(read(artifacts.get(CHARACTERS_INDEX).originalMembers(), "rules.json").getAsJsonArray());
    }

    private static JsonObject declaration(JsonArray rows) {
        var target = identity(STONECUNNING);
        return rows.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> target.equals(row.getAsJsonObject("identity"))).findFirst().orElseThrow();
    }

    private static FinalStonecunning trait(ProductionSchema2Projection material) {
        return (FinalStonecunning) material.projection().rules().stream()
                .filter(row -> row.identity().equals(STONECUNNING)).findFirst().orElseThrow();
    }

    private static FieldChain multiplierChain(ProductionSchema2Projection material) {
        return material.projection().chains().stream()
                .filter(chain -> chain.target().equals(STONECUNNING) && chain.field() == Field.PROFICIENCY_MULTIPLIER)
                .findFirst().orElseThrow();
    }

    private static ProductionSchema2Projection restore(ProductionSchema2Projection material, List<FieldChain> chains) {
        var source = material.projection();
        return ProductionSchema2Projection.restore(material.members(), source.rules(), source.entries(), chains);
    }

    private static JsonObject identity(RuleIdentity identity) {
        var json = new JsonObject();
        json.addProperty("kind", "rule");
        json.addProperty("module_key", identity.moduleKey());
        json.addProperty("rule_type", identity.ruleType());
        json.addProperty("rule_key", identity.ruleKey());
        return json;
    }

    private static JsonObject operation(String op, RuleIdentity target, String field, JsonElement value) {
        var row = new JsonObject();
        row.addProperty("op", op);
        row.add("target", identity(target));
        if (field != null) row.addProperty("field", field);
        row.add("value", value);
        return row;
    }

    private static void changes(List<ProductionRuleArtifact> artifacts, JsonObject row) {
        edit(artifacts, VARIANT_INDEX, "changes.json", rows -> rows.add(row));
    }

    private static void edit(List<ProductionRuleArtifact> artifacts, int index, String path,
            Consumer<JsonArray> update) {
        var members = new TreeMap<>(artifacts.get(index).originalMembers());
        var rows = read(members, path).getAsJsonArray();
        update.accept(rows);
        members.put(path, bytes(rows));
        artifacts.set(index, ProductionRuleArtifact.read(zip(members, 0)));
    }

    private static JsonElement read(Map<String, byte[]> members, String path) {
        return JsonParser.parseString(new String(members.get(path), StandardCharsets.UTF_8));
    }

    private static byte[] bytes(JsonElement value) {
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void assertCode(String code, Executable action) {
        assertEquals(code, assertThrows(Schema2Exception.class, action).diagnostic().code());
    }
}
