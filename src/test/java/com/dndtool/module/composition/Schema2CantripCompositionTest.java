package com.dndtool.module.composition;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CantripCompositionTest {
    private static final RuleIdentity WIZARD = new RuleIdentity("srd51_characters", "character.class", "class.wizard");
    private static final RuleIdentity CLERIC = new RuleIdentity("srd51_characters", "character.class", "class.cleric");
    private static final RuleIdentity FIRE_BOLT = spell("fire_bolt"), SACRED_FLAME = spell("sacred_flame");

    @Test void classChoicesAndCombatParametersFreezeIndependentlyFromTheLeveledSpellbook() throws IOException {
        var material = ProductionSchema2Projection.compose(bundled());
        var wizard = material.classCantrips(WIZARD).orElseThrow();
        var cleric = material.classCantrips(CLERIC).orElseThrow();
        assertEquals(Set.of(FIRE_BOLT, spell("light"), spell("mage_hand"), spell("prestidigitation")), identities(wizard));
        assertEquals(Set.of(SACRED_FLAME, spell("guidance"), spell("light"), spell("thaumaturgy")), identities(cleric));
        assertEquals(List.of(0, 3, 3, 3, 4, 4, 0), java.util.stream.IntStream.rangeClosed(0, 6)
                .map(wizard.definition()::knownCount).boxed().toList());
        assertEquals(3, cleric.definition().knownCount(1));
        assertEquals(0, cleric.definition().knownCount(2));
        assertEquals("ability.intelligence", wizard.definition().abilityKey());
        assertEquals("ability.wisdom", cleric.definition().abilityKey());

        var fireBolt = wizard.options().stream().filter(spell -> spell.identity().equals(FIRE_BOLT)).findFirst().orElseThrow();
        var sacredFlame = cleric.options().stream().filter(spell -> spell.identity().equals(SACRED_FLAME)).findFirst().orElseThrow();
        assertEquals(FIRE_BOLT_ALGORITHM, fireBolt.algorithm());
        assertEquals(SACRED_FLAME_ALGORITHM, sacredFlame.algorithm());
        assertEquals(0, spellLevel(fireBolt.algorithm()));
        assertEquals(0, spellLevel(sacredFlame.algorithm()));
        assertEquals(new CantripProfile(120, 10, DamageType.FIRE, 5, false), fireBolt.cantripProfile());
        assertEquals(new CantripProfile(60, 8, DamageType.RADIANT, 5, true), sacredFlame.cantripProfile());
        assertEquals(1, fireBolt.cantripProfile().diceCount(4));
        assertEquals(2, fireBolt.cantripProfile().diceCount(5));
        assertEquals(1, wizard.options().stream().filter(spell -> spell.cantripProfile() != null).count());
        assertEquals(1, cleric.options().stream().filter(spell -> spell.cantripProfile() != null).count());
        var leveled = material.projection().entries().stream().map(FinalEntry::entry)
                .filter(SpellbookEntry.class::isInstance).map(Entry::target).collect(Collectors.toSet());
        assertTrue(java.util.Collections.disjoint(leveled, identities(wizard)));

        var projection = material.projection();
        var restored = ProductionSchema2Projection.restore(material.members(), projection.rules(), projection.entries(), projection.chains());
        assertArrayEquals(material.configurationJson(), restored.configurationJson());
        assertArrayEquals(material.finalRulesJson(), restored.finalRulesJson());
        assertArrayEquals(material.chainsJson(), restored.chainsJson());
        assertEquals(wizard, restored.classCantrips(WIZARD).orElseThrow());
        var incomplete = projection.chains().stream().filter(chain ->
                !(chain.target().equals(FIRE_BOLT) && chain.field() == Field.CANTRIP_PROFILE)).toList();
        assertCode("S2_STORED_INVALID", () -> ProductionSchema2Projection.restore(
                material.members(), projection.rules(), projection.entries(), incomplete));
    }

    @Test void malformedExecutableParametersFailClosedAtTheAuthorBoundary() {
        assertCode("S2_SHAPE", () -> changed("rules.json", rows -> definition(rows, FIRE_BOLT).remove("cantrip_profile")));
        assertCode("S2_TYPE", () -> changed("rules.json", rows -> definition(rows, FIRE_BOLT)
                .getAsJsonObject("cantrip_profile").addProperty("range_feet", 115)));
        assertCode("S2_TYPE", () -> changed("rules.json", rows -> definition(rows, SACRED_FLAME)
                .getAsJsonObject("cantrip_profile").addProperty("ignores_save_cover", false)));
        assertCode("S2_TYPE", () -> changed("rules.json", rows -> definition(rows, SACRED_FLAME)
                .getAsJsonObject("cantrip_profile").addProperty("scaling_level", "5")));
    }

    @Test void wrongClassLeveledMissingAndDuplicateChoicesFailClosed() {
        for (var invalid : List.of(SACRED_FLAME, spell("shield"))) {
            assertCode("S2_TYPE", () -> changed("entries.json", rows ->
                    member(rows, "fire_bolt").add("spell", ruleJson(invalid))));
        }
        assertCode("S2_REFERENCE_MISSING", () -> changed("entries.json", rows ->
                member(rows, "fire_bolt").add("spell", ruleJson(spell("missing")))));
        assertCode("S2_CANDIDATES_INSUFFICIENT", () -> changed("entries.json", rows -> rows.remove(member(rows, "fire_bolt"))));
        assertCode("S2_DUPLICATE_IDENTITY", () -> changed("entries.json", rows ->
                member(rows, "fire_bolt").add("spell", ruleJson(spell("mage_hand")))));
    }

    @Test void restoreChecksOriginalCantripMembershipBeforeAValidOverride() throws IOException {
        var packages = bundled();
        var magic = packages.get(2);
        var entries = Schema2GrantFixtures.json(magic.originalMembers().get("entries.json")).getAsJsonArray();
        var change = new JsonObject();
        change.addProperty("op", "set_field");
        change.add("target", member(entries, "fire_bolt").getAsJsonObject("identity"));
        change.addProperty("field", "spell");
        change.add("value", ruleJson(FIRE_BOLT));
        var changes = new JsonArray();
        changes.add(change);
        String manifest = """
                {"author_schema_version":2,"module_key":"cantrip_variant","release_version":"1",
                 "package_display_name":"Cantrip test variant","role":"extension",
                 "dependencies":[{"module_key":"srd51_magic","supported_versions":["%s"]}],
                 "compatible_combinations":[{"srd51_magic":"%s"}],"load_order_constraints":[],
                 "files":[{"kind":"changes","path":"changes.json"}]}
                """.formatted(magic.releaseVersion(), magic.releaseVersion());
        packages.add(ProductionRuleArtifact.read(Schema2ValidationCodec.rawZip(Map.of(
                "author-package.json", bytes(manifest), "changes.json", bytes(changes.toString())))));
        var material = ProductionSchema2Projection.compose(packages);
        var projection = material.projection();
        assertEquals(material.finalRulesSha256(),
                ProductionSchema2Projection.restore(material.members(), projection.rules(),
                        projection.entries(), projection.chains()).finalRulesSha256());

        var member = new EntryIdentity(material.classCantrips(WIZARD).orElseThrow().definition().identity(),
                "cantrips", "srd51_magic", "fire_bolt");
        var hiddenOriginal = new FieldState(StateKind.PRESENT, new IdentityValue(SACRED_FLAME));
        var forged = projection.chains().stream().map(chain -> {
            if (!chain.target().equals(member) || chain.field() != Field.SPELL) return chain;
            var initial = chain.steps().getFirst();
            var override = chain.steps().getLast();
            return new FieldChain(chain.target(), chain.field(), List.of(
                    new ChainStep(initial.op(), initial.before(), hiddenOriginal, initial.provider(),
                            initial.loadIndex(), initial.authorLocation(), initial.origin()),
                    new ChainStep(override.op(), hiddenOriginal, override.after(), override.provider(),
                            override.loadIndex(), override.authorLocation(), override.origin())));
        }).toList();
        assertCode("S2_STORED_INVALID", () -> ProductionSchema2Projection.restore(
                material.members(), projection.rules(), projection.entries(), forged));
    }

    private static Set<RuleIdentity> identities(ProductionSchema2Projection.ClassCantrips cantrips) {
        return cantrips.options().stream().map(SpellDefinition::identity).collect(Collectors.toSet());
    }

    private static ProductionSchema2Projection changed(String path, Consumer<JsonArray> edit) throws IOException {
        var packages = bundled();
        int index = 2;
        Map<String, byte[]> members = new HashMap<>(packages.get(index).originalMembers());
        var rules = Schema2GrantFixtures.json(members.get(path)).getAsJsonArray();
        edit.accept(rules);
        members.put(path, bytes(rules.toString()));
        packages.set(index, ProductionRuleArtifact.read(Schema2ValidationCodec.rawZip(members)));
        return ProductionSchema2Projection.compose(packages);
    }

    private static ArrayList<ProductionRuleArtifact> bundled() throws IOException {
        var artifacts = new ArrayList<ProductionRuleArtifact>();
        for (var name : List.of("srd51-runtime", "srd51-characters", "srd51-magic", "dm-runtime-house-rules")) {
            try (var input = Schema2CantripCompositionTest.class.getResourceAsStream("/storage/rules/" + name + ".zip")) {
                assertNotNull(input, name);
                artifacts.add(ProductionRuleArtifact.read(input.readAllBytes()));
            }
        }
        return artifacts;
    }

    private static JsonObject definition(JsonArray rows, RuleIdentity identity) {
        return rows.asList().stream().map(row -> row.getAsJsonObject())
                .filter(row -> row.getAsJsonObject("identity").equals(ruleJson(identity)))
                .findFirst().orElseThrow();
    }

    private static JsonObject member(JsonArray rows, String key) {
        return rows.asList().stream().map(row -> row.getAsJsonObject())
                .filter(row -> row.getAsJsonObject("identity").get("entry_key").getAsString().equals(key))
                .findFirst().orElseThrow();
    }

    private static RuleIdentity spell(String key) { return new RuleIdentity("srd51_magic", "adventure.spell", "spell." + key); }

    private static JsonObject ruleJson(RuleIdentity id) {
        return Schema2GrantFixtures.rule(id.moduleKey(), id.ruleType(), id.ruleKey());
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static void assertCode(String code, Executable action) {
        assertEquals(code, assertThrows(Schema2Exception.class, action).diagnostic().code());
    }
}
