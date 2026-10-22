package com.dndtool.service;

import com.dndtool.module.CharacterProficiencyGrant.GrantKind;
import com.dndtool.module.composition.*;
import com.dndtool.service.Schema2InitialProficiencyRules.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2InitialProficiencyRules.MAX_SLOTS;
import static org.junit.jupiter.api.Assertions.*;

class Schema2InitialProficiencyRulesTest {
    static final RuleIdentity RACE = rule("grants_base", "character.race", "race.human");
    static final RuleIdentity SUBRACE = rule("grants_base", "character.subrace", "subrace.north");
    static final RuleIdentity BACKGROUND = rule("grants_base", "character.background", "background.sailor");
    static final RuleIdentity CLASS = rule("grants_base", "character.class", "class.scholar");
    static final EntryIdentity LANGUAGE_SLOT = slot(RACE, "grants_base", "extra_languages");
    static final EntryIdentity TOOL_SLOT = slot(BACKGROUND, "grants_base", "trade_tools");
    static final RuleIdentity COMMON = language("grants_base", "common");
    static final RuleIdentity FOREIGN_COMMON = language("grants_extension", "common");
    static final RuleIdentity ELVISH = language("grants_base", "elvish");
    static final RuleIdentity DWARVISH = language("grants_base", "dwarvish");
    static final RuleIdentity FLUTE = rule("grants_base", "character.tool", "tool.flute");
    static final RuleIdentity HERBALISM = rule("grants_base", "character.tool", "tool.herbalism");
    static final RuleIdentity SMITH_TOOLS = rule("grants_base", "character.tool", "tool.smith_tools");
    static final Sources SOURCES = new Sources(RACE, null, BACKGROUND, CLASS);
    private final Schema2InitialProficiencyRules rules = new Schema2InitialProficiencyRules();

    @Test void originalProductionGoldenHasACompleteFourSourceInitialPlan() {
        var prepared = rules.prepare(ProductionSchema2Projection.compose(Schema2GrantFixtures.fixedArtifacts()),
                new Request(new Sources(RACE, SUBRACE, BACKGROUND, CLASS),
                        Map.of(LANGUAGE_SLOT, List.of(ELVISH), TOOL_SLOT, List.of(SMITH_TOOLS))));
        assertEquals(8, prepared.slots().size());
        assertEquals(List.of(
                new GrantFact(slot(BACKGROUND, "grants_base", "shared_language"), GrantCategory.LANGUAGE, GrantKind.FIXED, COMMON, false),
                new GrantFact(TOOL_SLOT, GrantCategory.TOOL, GrantKind.CHOICE, SMITH_TOOLS, false),
                new GrantFact(slot(CLASS, "grants_base", "disabled_tool"), GrantCategory.TOOL, GrantKind.FIXED, HERBALISM, true),
                new GrantFact(slot(CLASS, "grants_base", "lost_language"), GrantCategory.LANGUAGE, GrantKind.FIXED, DWARVISH, true),
                new GrantFact(LANGUAGE_SLOT, GrantCategory.LANGUAGE, GrantKind.CHOICE, ELVISH, false),
                new GrantFact(slot(RACE, "grants_base", "native_language"), GrantCategory.LANGUAGE, GrantKind.FIXED, COMMON, false),
                new GrantFact(slot(RACE, "grants_extension", "extension_language"), GrantCategory.LANGUAGE, GrantKind.FIXED, FOREIGN_COMMON, false),
                new GrantFact(slot(SUBRACE, "grants_base", "instrument"), GrantCategory.TOOL, GrantKind.FIXED, FLUTE, false)), prepared.grants());
        assertTrue(prepared.slots().contains(new SlotFact(slot(CLASS, "grants_base", "disabled_tool"),
                new FixedGrant(GrantCategory.TOOL, HERBALISM), false)));
        assertTrue(prepared.slots().contains(new SlotFact(slot(CLASS, "grants_base", "lost_language"),
                new FixedGrant(GrantCategory.LANGUAGE, DWARVISH), true)));
    }

    @Test void realSourcesAndCompleteSlotAndTargetIdentitiesDeriveFixedChoiceAndSuppressedFacts() {
        var prepared = rules.prepare(projection(), request());
        assertEquals(SOURCES, prepared.sources());
        assertEquals(7, prepared.slots().size()); assertEquals(7, prepared.grants().size());
        assertEquals(2, prepared.grants().stream().filter(g -> g.target().equals(COMMON)).count());
        assertTrue(prepared.grants().contains(new GrantFact(slot(RACE, "grants_extension", "extension_language"),
                GrantCategory.LANGUAGE, GrantKind.FIXED, FOREIGN_COMMON, false)));
        assertTrue(prepared.grants().contains(new GrantFact(LANGUAGE_SLOT, GrantCategory.LANGUAGE, GrantKind.CHOICE, ELVISH, false)));
        assertTrue(prepared.grants().contains(new GrantFact(TOOL_SLOT, GrantCategory.TOOL, GrantKind.CHOICE, FLUTE, false)));
        assertTrue(prepared.grants().stream().filter(g -> g.source().equals(CLASS)).allMatch(GrantFact::suppressed));
        assertTrue(prepared.grants().stream().anyMatch(g -> g.target().equals(DWARVISH) && g.suppressed()));
        assertTrue(prepared.slots().stream().anyMatch(s -> s.slot().entryKey().equals("disabled_tool") && !s.enabled()));
    }

    @Test void twoExtensionsMayOwnTheSameLocalSlotKeyWithoutLosingTheirSourceIdentity() {
        var projection = projection(files -> {
            edit(files, 1, "members.json", root -> row(root, 3).getAsJsonObject("identity").addProperty("entry_key", "native_language"));
            edit(files, 2, "author-package.json", root -> {
                var declaration = new JsonObject(); declaration.addProperty("kind", "entries"); declaration.addProperty("path", "extra.json");
                root.getAsJsonObject().getAsJsonArray("files").add(declaration);
            });
            var row = new JsonObject(); row.add("identity", entryJson(slot(RACE, "grants_compatibility", "native_language")));
            var grant = new JsonObject(); grant.addProperty("category", "LANGUAGE"); grant.addProperty("kind", "FIXED"); grant.add("target", identityJson(COMMON));
            row.add("grant", grant); var entries = new JsonArray(); entries.add(row);
            files.get(2).put("extra.json", entries.toString().getBytes(StandardCharsets.UTF_8));
        });
        var actual = rules.prepare(projection, request()).grants().stream().filter(g -> g.slot().entryKey().equals("native_language")).toList();
        assertEquals(3, actual.size());
        assertEquals(Set.of("grants_base", "grants_extension", "grants_compatibility"),
                actual.stream().map(g -> g.slot().moduleKey()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(actual.stream().allMatch(g -> g.source().equals(RACE)));
        assertEquals(Set.of(COMMON, FOREIGN_COMMON), actual.stream().map(GrantFact::target).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void slotSelectionsAreExactAndAllSlotsMustBeSatisfiedTogetherWithoutGreedyReassignment() {
        var projection = projection(files -> {
            edit(files, 1, "changes.json", root -> row(root, 4).add("value", choiceJson(GrantCategory.LANGUAGE,
                    rule("grants_base", "catalog.language_set", "language_set.initial"), 1)));
            edit(files, 2, "changes.json", root -> row(root, 3).add("value", choiceJson(GrantCategory.LANGUAGE,
                    rule("grants_base", "catalog.language_set", "language_set.initial"), 1)));
            // The formerly fixed foreign target becomes a disabled fact, allowing its initial selection.
            disable(files, slot(RACE, "grants_extension", "extension_language"));
        });
        EntryIdentity backgroundLanguage = slot(BACKGROUND, "grants_base", "shared_language");
        var selected = new LinkedHashMap<EntryIdentity, List<RuleIdentity>>();
        selected.put(LANGUAGE_SLOT, List.of(FOREIGN_COMMON)); selected.put(backgroundLanguage, List.of(ELVISH));
        selected.put(TOOL_SLOT, List.of(FLUTE));
        var prepared = rules.prepare(projection, new Request(SOURCES, selected));
        assertTrue(prepared.grants().contains(new GrantFact(LANGUAGE_SLOT, GrantCategory.LANGUAGE, GrantKind.CHOICE, FOREIGN_COMMON, false)));
        assertTrue(prepared.grants().contains(new GrantFact(backgroundLanguage, GrantCategory.LANGUAGE, GrantKind.CHOICE, ELVISH, false)));
        selected.put(backgroundLanguage, List.of(FOREIGN_COMMON));
        rejects("S2_INITIAL_DUPLICATE_SELECTION", projection, new Request(SOURCES, selected));
        selected.remove(backgroundLanguage);
        rejects("S2_INITIAL_SELECTION_COUNT", projection, new Request(SOURCES, selected));
    }

    @Test void disabledChoiceRetainsSlotAndHasNoInputObligationOrFabricatedTarget() {
        var projection = projection(files -> disable(files, LANGUAGE_SLOT));
        var prepared = rules.prepare(projection, new Request(SOURCES, Map.of(TOOL_SLOT, List.of(FLUTE))));
        assertTrue(prepared.slots().contains(new SlotFact(LANGUAGE_SLOT, new ChoiceGrant(GrantCategory.LANGUAGE,
                rule("grants_base", "catalog.language_set", "language_set.initial"), 1), false)));
        assertTrue(prepared.grants().stream().noneMatch(g -> g.slot().equals(LANGUAGE_SLOT)));
        rejects("S2_INITIAL_GRANT_SLOT", projection, request());
    }

    @Test void disablingAFixedSlotRetainsItsFactEvenWhenTheTargetRemainsEnabled() {
        var prepared = rules.prepare(projection(files -> disable(files, slot(RACE, "grants_base", "native_language"))), request());
        assertTrue(prepared.grants().contains(new GrantFact(slot(RACE, "grants_base", "native_language"),
                GrantCategory.LANGUAGE, GrantKind.FIXED, COMMON, true)));
        assertTrue(prepared.grants().contains(new GrantFact(slot(BACKGROUND, "grants_base", "shared_language"),
                GrantCategory.LANGUAGE, GrantKind.FIXED, COMMON, false)));
        assertTrue(prepared.grants().contains(new GrantFact(slot(CLASS, "grants_base", "lost_language"),
                GrantCategory.LANGUAGE, GrantKind.FIXED, DWARVISH, true)));
    }

    @Test void sourcesMustExistBeEnabledAndMatchTheRealSubraceParent() {
        rejects("S2_INITIAL_SOURCE", projection(), new Request(new Sources(rule("grants_extension", "character.race", "race.human"), null, BACKGROUND, CLASS), request().choices()));
        rejects("S2_INITIAL_SOURCE", projection(), new Request(new Sources(RACE, null, BACKGROUND,
                rule("grants_extension", "character.class", "class.retired")), request().choices()));
        rejects("S2_INITIAL_SUBRACE_PARENT", projection(), new Request(new Sources(
                rule("grants_extension", "character.race", "race.elf"), SUBRACE, BACKGROUND, CLASS), Map.of()));
        var withSubrace = projection(files -> disable(files, TOOL_SLOT));
        var prepared = rules.prepare(withSubrace, new Request(new Sources(RACE, SUBRACE, BACKGROUND, CLASS), Map.of(LANGUAGE_SLOT, List.of(ELVISH))));
        assertTrue(prepared.grants().stream().anyMatch(g -> g.source().equals(SUBRACE) && g.target().equals(FLUTE)));
    }

    @Test void disabledCandidateEntryAndTargetAreUnavailableAndCategoryCannotCross() {
        var disabledEntry = projection(files -> edit(files, 2, "changes.json", root -> row(root, 4).addProperty("value", false)));
        rejects("S2_INITIAL_SELECTION", disabledEntry, request());
        rejects("S2_INITIAL_SELECTION", projection(), new Request(SOURCES, Map.of(LANGUAGE_SLOT, List.of(DWARVISH), TOOL_SLOT, List.of(FLUTE))));
        rejects("S2_INITIAL_SELECTION", projection(), new Request(SOURCES, Map.of(LANGUAGE_SLOT, List.of(FLUTE), TOOL_SLOT, List.of(ELVISH))));
        rejects("S2_INITIAL_DUPLICATE_SELECTION", projection(), new Request(SOURCES, Map.of(LANGUAGE_SLOT, List.of(COMMON), TOOL_SLOT, List.of(FLUTE))));
        rejects("S2_INITIAL_DUPLICATE_SELECTION", projection(), new Request(SOURCES, Map.of(LANGUAGE_SLOT, List.of(FOREIGN_COMMON), TOOL_SLOT, List.of(FLUTE))));
    }

    @Test void fullSlotIdentityRejectsWrongOwnerUnselectedSourceFixedSlotAndMissingCount() {
        for (EntryIdentity slot : List.of(slot(RACE, "grants_extension", "extra_languages"),
                slot(SUBRACE, "grants_base", "extra_languages"), slot(RACE, "grants_base", "native_language"))) {
            rejects("S2_INITIAL_GRANT_SLOT", projection(), new Request(SOURCES, Map.of(slot, List.of(ELVISH), TOOL_SLOT, List.of(FLUTE))));
        }
        rejects("S2_INITIAL_SELECTION_COUNT", projection(), new Request(SOURCES, Map.of(TOOL_SLOT, List.of(FLUTE))));
    }

    @Test void unselectedMalformedCandidateCannotBeHiddenByAValidChoiceRequest() {
        var failure = assertThrows(Schema2Exception.class, () -> projection(files ->
                edit(files, 0, "members.json", root -> row(root, 2).getAsJsonObject("language").addProperty("rule_key", "language.missing"))));
        assertEquals("S2_REFERENCE_MISSING", failure.diagnostic().code());
    }

    @Test void requestAndPreparedCollectionsAreBoundedImmutableAndRejectNullAndDuplicateSelections() {
        var targets = new ArrayList<>(List.of(ELVISH)); var choices = new LinkedHashMap<EntryIdentity, List<RuleIdentity>>();
        choices.put(LANGUAGE_SLOT, targets); choices.put(TOOL_SLOT, new ArrayList<>(List.of(FLUTE)));
        var request = new Request(SOURCES, choices); targets.clear(); choices.clear();
        assertEquals(List.of(ELVISH), request.choices().get(LANGUAGE_SLOT));
        assertThrows(UnsupportedOperationException.class, () -> request.choices().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.choices().get(LANGUAGE_SLOT).clear());
        var prepared = rules.prepare(projection(), request);
        assertThrows(UnsupportedOperationException.class, () -> prepared.slots().clear());
        assertThrows(UnsupportedOperationException.class, () -> prepared.grants().clear());
        assertThrows(IllegalArgumentException.class, () -> new Request(SOURCES, null));
        assertThrows(IllegalArgumentException.class, () -> new Request(SOURCES, Collections.singletonMap(LANGUAGE_SLOT, null)));
        assertThrows(IllegalArgumentException.class, () -> new Request(SOURCES, Map.of(LANGUAGE_SLOT, Arrays.asList(ELVISH, null))));
        assertThrows(IllegalArgumentException.class, () -> new Request(SOURCES, Map.of(LANGUAGE_SLOT, List.of(ELVISH, ELVISH))));
        assertThrows(IllegalArgumentException.class, () -> new Request(SOURCES, Map.of(LANGUAGE_SLOT, Collections.nCopies(17, ELVISH))));
        var excessive = new HashMap<EntryIdentity, List<RuleIdentity>>();
        for (int i = 0; i <= MAX_SLOTS; i++) excessive.put(slot(RACE, "grants_base", "slot_" + i), List.of());
        assertThrows(IllegalArgumentException.class, () -> new Request(SOURCES, excessive));
        var tooManyTargets = new HashMap<EntryIdentity, List<RuleIdentity>>();
        for (int i = 0; i < 17; i++) {
            var distinct = new ArrayList<RuleIdentity>();
            for (int j = 0; j < 16; j++) distinct.add(language("grants_base", "target_" + i + "_" + j));
            tooManyTargets.put(slot(RACE, "grants_base", "slot_" + i), distinct);
        }
        assertEquals("S2_INITIAL_BUDGET", assertThrows(IllegalArgumentException.class,
                () -> new Request(SOURCES, tooManyTargets)).getMessage());
    }

    @Test void choicesHaveDeterministicFullIdentityOrderAndPreparedCopiesCallerCollections() {
        var unordered = new LinkedHashMap<EntryIdentity, List<RuleIdentity>>();
        unordered.put(LANGUAGE_SLOT, List.of(FOREIGN_COMMON, ELVISH));
        unordered.put(TOOL_SLOT, List.of(FLUTE));
        var request = new Request(SOURCES, unordered);
        assertEquals(List.of(TOOL_SLOT, LANGUAGE_SLOT), new ArrayList<>(request.choices().keySet()));
        assertEquals(List.of(ELVISH, FOREIGN_COMMON), request.choices().get(LANGUAGE_SLOT));
        var original = rules.prepare(projection(), request());
        var slots = new ArrayList<>(original.slots()); var grants = new ArrayList<>(original.grants());
        var copied = new Prepared(original.sources(), original.choices(), slots, grants);
        slots.clear(); grants.clear();
        assertEquals(original, copied);
    }

    private void rejects(String code, ProductionSchema2Projection projection, Request request) {
        assertEquals(code, assertThrows(IllegalArgumentException.class, () -> rules.prepare(projection, request)).getMessage());
    }

    static Request request() { return new Request(SOURCES, Map.of(LANGUAGE_SLOT, List.of(ELVISH), TOOL_SLOT, List.of(FLUTE))); }
    static ProductionSchema2Projection projection() { return projection(files -> {}); }
    static ProductionSchema2Projection projection(Consumer<List<Map<String, byte[]>>> mutation) {
        var files = files();
        mutation.accept(files);
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
    }
    static List<Map<String, byte[]>> files() {
        return Schema2GrantFixtures.packages();
    }
    static void edit(List<Map<String, byte[]>> files, int index, String path, Consumer<JsonElement> mutation) {
        Schema2GrantFixtures.edit(files, index, path, mutation);
    }
    static void disable(List<Map<String, byte[]>> files, EntryIdentity slot) {
        edit(files, 2, "changes.json", root -> {
            var change = new JsonObject(); change.addProperty("op", "set_enabled"); change.add("target", entryJson(slot));
            change.addProperty("value", false); root.getAsJsonArray().add(change);
        });
    }
    static JsonObject choiceJson(GrantCategory category, RuleIdentity candidates, int count) {
        var grant = new JsonObject(); grant.addProperty("category", category.name()); grant.addProperty("kind", "CHOICE");
        grant.add("candidates", identityJson(candidates)); grant.addProperty("count", count); return grant;
    }
    static JsonObject identityJson(RuleIdentity identity) {
        var result = new JsonObject(); result.addProperty("kind", "rule"); result.addProperty("module_key", identity.moduleKey());
        result.addProperty("rule_type", identity.ruleType()); result.addProperty("rule_key", identity.ruleKey()); return result;
    }
    static JsonObject entryJson(EntryIdentity identity) {
        var result = new JsonObject(); result.addProperty("kind", "entry"); result.add("rule", identityJson(identity.rule()));
        result.addProperty("collection_key", identity.collectionKey()); result.addProperty("module_key", identity.moduleKey());
        result.addProperty("entry_key", identity.entryKey()); return result;
    }
    static RuleIdentity rule(String module, String type, String key) { return new RuleIdentity(module, type, key); }
    static EntryIdentity slot(RuleIdentity source, String module, String key) { return new EntryIdentity(source, "proficiency_grants", module, key); }
    static RuleIdentity language(String module, String key) { return rule(module, "character.language", "language." + key); }
    static JsonObject row(JsonElement root, int index) { return root.getAsJsonArray().get(index).getAsJsonObject(); }
}
