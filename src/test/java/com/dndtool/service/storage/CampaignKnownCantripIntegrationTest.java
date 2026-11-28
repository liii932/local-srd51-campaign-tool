package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.FinalSpell;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.History;
import com.dndtool.service.Schema2CharacterRuntime.KnownCantrip;
import com.dndtool.service.Schema2CharacterRuntime.LearnedSpell;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.module.composition.Schema2CompositionKernel.spellLevel;
import static org.junit.jupiter.api.Assertions.*;

class CampaignKnownCantripIntegrationTest {
    @TempDir Path temporary;

    @Test void rejectsInvalidChoicesAndStoresExplicitAndDefaultSelectionsByClass() throws Exception {
        try (var service = service()) {
            var before = service.accessSnapshot();
            for (List<String> choices : List.of(List.<String>of(), List.of("spell.mage_hand", "spell.light"),
                    List.of("spell.mage_hand", "spell.mage_hand", "spell.light"),
                    List.of("spell.mage_hand", "spell.light", "spell.sacred_flame"),
                    List.of("spell.mage_hand", "spell.light", "spell.shield"))) {
                var rejected = assertThrows(StorageException.class, () -> create(service, "wizard", choices));
                assertEquals("DOMAIN_REJECTED", rejected.code());
                assertEquals("S2_KNOWN_CANTRIP_CHOICES", rejected.getMessage());
                assertEquals(before, service.accessSnapshot());
            }
            var forged = creation("wizard", null);
            var classIdentity = context(service).rule("character.class", "class.cleric");
            forged.add("cantripProfile", CampaignDomainCodec.toJson(classIdentity));
            assertThrows(StorageException.class, () -> CampaignSpaceFixtures.command(service, "character-create", forged));
            assertEquals(before, service.accessSnapshot());

            UUID wizard = create(service, "wizard", List.of("spell.mage_hand", "spell.light", "spell.prestidigitation"));
            UUID cleric = create(service, "cleric", List.of("spell.guidance", "spell.light", "spell.thaumaturgy"));
            UUID defaultWizard = create(service, "wizard", null);
            UUID defaultCleric = create(service, "cleric", null);
            UUID fighter = create(service, "fighter", List.of());
            assertEquals(Set.of("spell.mage_hand", "spell.light", "spell.prestidigitation"), keys(state(service, wizard)));
            assertEquals(Set.of("spell.guidance", "spell.light", "spell.thaumaturgy"), keys(state(service, cleric)));
            assertEquals(Set.of("spell.fire_bolt", "spell.light", "spell.mage_hand"), keys(state(service, defaultWizard)));
            assertEquals(Set.of("spell.sacred_flame", "spell.guidance", "spell.light"), keys(state(service, defaultCleric)));
            assertEquals(List.of(), state(service, fighter).knownCantrips());
            for (UUID actor : List.of(wizard, cleric, defaultWizard, defaultCleric)) {
                var current = state(service, actor);
                var profile = context(service).projection().classCantrips(current.characterClass()).orElseThrow().definition();
                for (KnownCantrip cantrip : current.knownCantrips()) {
                    assertEquals(1, cantrip.acquiredAtClassLevel());
                    assertEquals(profile.ownerClass(), cantrip.characterClass());
                    assertEquals(profile.identity(), cantrip.cantripProfile());
                }
                assertNull(current.spellbook());
            }
            var catalog = context(service).metadata().catalog();
            var light = catalog.stream().filter(rule -> rule.key().equals("spell.light")).findFirst().orElseThrow();
            assertEquals(List.of("class.cleric", "class.wizard"), light.cantripClasses());
            assertEquals(light.cantripClasses(), light.defaultCantripClasses());
            assertFalse(light.executable());
            assertFalse(light.spellbookChoice());
            assertEquals(List.of(3, 3, 3, 4, 4), catalog.stream().filter(rule -> rule.key().equals("class.wizard"))
                    .findFirst().orElseThrow().cantripCounts());
        }
    }

    @Test void spellbookAndGrowthKeepSeparateAcquisitionsThroughFullFileReopen() throws Exception {
        try (var service = service()) {
            UUID wizard = create(service, "wizard", List.of("spell.mage_hand", "spell.light", "spell.prestidigitation"));
            CampaignRuleContext rules = context(service);
            var profile = rules.rule("character.wizard_spellcasting", "spellcasting.wizard");
            var light = rules.rule("adventure.spell", "spell.light");
            var fireBolt = rules.rule("adventure.spell", "spell.fire_bolt");
            var initial = state(service, wizard);
            var attempts = new ArrayList<>(rules.initialWizardBook());
            attempts.set(2, light);
            assertRejected(service, wizard, Command.spellbook("SPELLBOOK_INITIALIZED",
                    new SpellbookInput(profile, attempts, rules.initialWizardPrepared())), "S2_SPELLBOOK_LEARNING");
            apply(service, wizard, Command.spellbook("SPELLBOOK_INITIALIZED",
                    new SpellbookInput(profile, rules.initialWizardBook(), rules.initialWizardPrepared())));
            assertEquals(initial.knownCantrips(), state(service, wizard).knownCantrips());
            assertEquals(initial.resources(), state(service, wizard).resources());

            var evocation = rules.rule("character.subclass", "subclass.evocation");
            assertRejected(service, wizard, advance(initial.characterClass(), evocation, Map.of(), List.of(fireBolt), "SERVER_ROLL"),
                    "S2_KNOWN_CANTRIP_CHOICES");
            apply(service, wizard, advance(initial.characterClass(), evocation, Map.of(), List.of(), "FIXED_AVERAGE"));
            learnMore(service, wizard, profile);
            apply(service, wizard, advance(initial.characterClass(), null, Map.of(), List.of(), "FIXED_AVERAGE"));
            learnMore(service, wizard, profile);
            assertEquals(initial.knownCantrips(), state(service, wizard).knownCantrips());

            Map<String, Integer> asi = Map.of("ability.intelligence", 2);
            assertRejected(service, wizard, advance(initial.characterClass(), null, asi, List.of(light), "SERVER_ROLL"),
                    "S2_KNOWN_CANTRIP_CHOICES");
            apply(service, wizard, advance(initial.characterClass(), null, asi, List.of(fireBolt), "FIXED_AVERAGE"));
            learnMore(service, wizard, profile);
            var current = state(service, wizard);
            var acquired = current.knownCantrips().stream().filter(cantrip -> cantrip.spell().equals(fireBolt)).findFirst().orElseThrow();
            assertEquals(4, current.level());
            assertEquals(4, acquired.acquiredAtClassLevel());
            assertEquals(4, current.knownCantrips().size());
            assertTrue(current.knownCantrips().containsAll(initial.knownCantrips()));
            assertEquals(12, current.spellbook().learned().size());
            assertTrue(current.spellbook().learned().stream().map(LearnedSpell::spell).noneMatch(Set.of(light, fireBolt)::contains));
            assertEquals(rules.initialWizardPrepared().stream().sorted(java.util.Comparator.comparing(RuleIdentity::ruleKey)).toList(),
                    current.spellbook().prepared());

            var retained = service.accessSnapshot();
            String file = service.end(UUID.randomUUID().toString(), retained.epoch(), retained.revision()).fileKey();
            service.open(file);
            assertEquals(retained.snapshot(), service.accessSnapshot().snapshot());
            assertEquals(current, state(service, wizard));
        }
    }

    @Test void missingOrForgedKnownFactsFailCharacterReplay() throws Exception {
        try (var service = service()) {
            UUID wizard = create(service, "wizard", null);
            var character = character(service, wizard);
            var history = character.history();
            var current = history.current();
            JsonObject missing = CampaignDomainCodec.toJson(current).getAsJsonObject();
            missing.remove("knownCantrips");
            assertThrows(StorageException.class, () -> CampaignDomainCodec.fromJson(missing, State.class));

            var material = context(service).projection();
            var engine = new CampaignDomainEngine();
            JsonObject emptyChoices = CampaignDomainCodec.toJson(history).getAsJsonObject();
            emptyChoices.getAsJsonObject("current").add("knownCantrips", new JsonArray());
            emptyChoices.getAsJsonArray("events").get(0).getAsJsonObject().add("after", emptyChoices.get("current").deepCopy());
            var empty = CampaignDomainCodec.fromJson(emptyChoices, History.class);
            assertEquals("S2_CHARACTER_HISTORY_CORRUPT", assertThrows(IllegalArgumentException.class, () ->
                    engine.validateCharacter(material, character.initial(), empty)).getMessage());

            JsonObject wrongSource = CampaignDomainCodec.toJson(history).getAsJsonObject();
            var clericProfile = context(service).rule("character.class_cantrips", "cantrips.cleric");
            var currentDocument = wrongSource.getAsJsonObject("current");
            var fact = currentDocument.getAsJsonArray("knownCantrips").get(0).getAsJsonObject();
            fact.add("cantripProfile", CampaignDomainCodec.toJson(clericProfile));
            wrongSource.getAsJsonArray("events").get(0).getAsJsonObject().add("after", currentDocument.deepCopy());
            History corrupted = CampaignDomainCodec.fromJson(wrongSource, History.class);
            assertEquals("S2_CHARACTER_HISTORY_CORRUPT", assertThrows(IllegalArgumentException.class, () ->
                    engine.validateCharacter(material, character.initial(), corrupted)).getMessage());
        }
    }

    private CampaignStorageService service() throws Exception {
        var service = new CampaignStorageService(temporary, boundary -> { }, sides -> fail("Unexpected RNG during cantrip choice"));
        service.create("戏法选择");
        return service;
    }

    private static Command advance(RuleIdentity owner, RuleIdentity subclass, Map<String, Integer> asi,
            List<RuleIdentity> cantrips, String hpChoice) {
        return new Command("CHARACTER_ADVANCED", null, 0, null, hpChoice, owner,
                new GrowthChoices(subclass, asi, null, Map.of(), cantrips));
    }

    private static void learnMore(CampaignStorageService service, UUID actor, RuleIdentity castingProfile) {
        State state = state(service, actor);
        Set<RuleIdentity> learned = state.spellbook().learned().stream().map(LearnedSpell::spell).collect(Collectors.toSet());
        var context = context(service);
        Set<String> choices = context.metadata().catalog().stream().filter(CampaignRuleContext.CatalogEntry::spellbookChoice)
                .map(CampaignRuleContext.CatalogEntry::key).collect(Collectors.toSet());
        var additions = context.projection().projection().rules().stream().filter(FinalSpell.class::isInstance)
                .map(FinalSpell.class::cast).filter(rule -> choices.contains(rule.identity().ruleKey())
                        && !learned.contains(rule.identity()) && spellLevel(rule.definition().algorithm()) <= (state.level() + 1) / 2)
                .map(FinalSpell::identity).limit(2).toList();
        apply(service, actor, Command.spellbook("SPELLBOOK_ADVANCED",
                new SpellbookInput(castingProfile, additions, state.spellbook().prepared())));
    }

    private static void assertRejected(CampaignStorageService service, UUID actor, Command command, String message) {
        var before = service.accessSnapshot();
        var rejected = assertThrows(StorageException.class, () -> apply(service, actor, command));
        assertEquals("DOMAIN_REJECTED", rejected.code());
        assertEquals(message, rejected.getMessage());
        assertEquals(before, service.accessSnapshot());
    }

    private static void apply(CampaignStorageService service, UUID actor, Command command) {
        var input = new JsonObject();
        input.addProperty("characterId", actor.toString());
        input.addProperty("characterVersion", Long.toString(state(service, actor).version()));
        input.add("command", CampaignDomainCodec.toJson(command));
        CampaignSpaceFixtures.command(service, "character-command", input);
    }

    private static UUID create(CampaignStorageService service, String owner, List<String> choices) {
        return UUID.fromString(CampaignSpaceFixtures.command(service, "character-create", creation(owner, choices)).lastCommand().subjectId());
    }

    private static JsonObject creation(String owner, List<String> choices) {
        var input = new JsonObject();
        input.addProperty("name", owner);
        input.addProperty("kind", "PC");
        input.addProperty("classKey", owner);
        input.addProperty("backgroundKey", "acolyte");
        if (choices != null) {
            var chosen = new JsonArray();
            choices.forEach(chosen::add);
            input.add("cantripKeys", chosen);
        }
        return input;
    }

    private static CampaignRuleContext context(CampaignStorageService service) {
        return service.accessSnapshot().snapshot().domains().material().restore();
    }

    private static CampaignDomainData.Character character(CampaignStorageService service, UUID actor) {
        return service.accessSnapshot().snapshot().domains().characters().stream().filter(character -> character.id().equals(actor))
                .findFirst().orElseThrow();
    }

    private static State state(CampaignStorageService service, UUID actor) {
        return character(service, actor).history().current();
    }

    private static Set<String> keys(State state) {
        return state.knownCantrips().stream().map(cantrip -> cantrip.spell().ruleKey()).collect(Collectors.toSet());
    }
}
