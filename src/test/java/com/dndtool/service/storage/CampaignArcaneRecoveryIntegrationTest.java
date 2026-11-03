package com.dndtool.service.storage;

import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignArcaneRecoveryIntegrationTest {
    @TempDir Path temporary;
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static JsonObject input(String... pairs) {
        JsonObject input = new JsonObject();
        for (int index = 0; index < pairs.length; index += 2) input.addProperty(pairs[index], pairs[index + 1]);
        return input;
    }
    private static void createWizard(CampaignStorageService service) {
        service.create("奥术回想", List.of(CampaignRules.BASE, CampaignRules.HEALING, CampaignRules.RUNTIME));
        var current = service.current();
        service.domainCommand(uuid(), current.epoch(), current.revision(), "character-create",
                input("name", "法师", "kind", "PC", "classKey", "wizard", "backgroundKey", "acolyte"));
    }
    private static JsonObject character(CampaignStorageService service) {
        return service.current().domain().getAsJsonArray("characters").get(0).getAsJsonObject();
    }
    private static State state(CampaignStorageService service) {
        return new Gson().fromJson(character(service).get("state"), State.class);
    }
    private static JsonObject request(CampaignStorageService service, Command command) {
        var character = character(service);
        var request = input("characterId", character.get("id").getAsString(), "characterVersion", character.get("version").getAsString());
        request.add("command", CampaignDomainCodec.toJson(command));
        return request;
    }
    private static void apply(CampaignStorageService service, Command command) {
        var current = service.current();
        service.domainCommand(uuid(), current.epoch(), current.revision(), "character-command", request(service, command));
    }
    private static Command rest(String kind, RestInput input) {
        return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, input);
    }
    private static void start(CampaignStorageService service) {
        apply(service, rest("REST_STARTED", new RestInput("SHORT_REST", null, 0, 0, Map.of())));
    }
    private static void time(CampaignStorageService service, int minutes) {
        apply(service, rest("TIME_ADVANCED", new RestInput(null, state(service).rest().active() == null ? "NONE" : "LIGHT", minutes, 0, Map.of())));
    }
    private static void endRest(CampaignStorageService service) {
        apply(service, rest("REST_ENDED", new RestInput(null, null, 0, 0, Map.of())));
    }
    private static Command finish(Map<Integer, Integer> choices) {
        return rest("REST_COMPLETED", new RestInput(null, null, 0, 0, Map.of(), choices));
    }
    private static void learn(CampaignStorageService service) {
        apply(service, Command.spellbook("SPELLBOOK_INITIALIZED", new SpellbookInput(
                BundledCampaignRules.rule("character.wizard_spellcasting", "spellcasting.wizard"),
                BundledCampaignRules.initialWizardBook(), BundledCampaignRules.initialWizardPrepared())));
    }
    private static void spend(CampaignStorageService service, int amount) {
        apply(service, new Command("RESOURCE_SPENT", BundledCampaignRules.rule("character.resource", "resource.wizard.slot1"), amount, null, null));
    }
    private static long slots(CampaignStorageService service) {
        return state(service).resources().stream().filter(resource -> resource.identity().ruleKey().equals("resource.wizard.slot1"))
                .findFirst().orElseThrow().current();
    }
    private static void rejected(CampaignStorageService service, Command command, String reason) {
        var before = service.current();
        StorageException failure = assertThrows(StorageException.class, () -> apply(service, command));
        assertEquals("DOMAIN_REJECTED", failure.code()); assertEquals(reason, failure.getMessage());
        var after = service.current();
        assertEquals(before.domain(), after.domain()); assertEquals(before.history(), after.history());
        assertEquals(before.revision(), after.revision(), "Rejected recovery must not append roots, spend resources or use the day");
    }

    @Test void realWizardRecoveryReplayAndDailyUseSurviveSaveEndAndReopen() throws Exception {
        try (var service = new CampaignStorageService(temporary.resolve("whole-file"))) {
            createWizard(service); learn(service); spend(service, 2); start(service); time(service, 60);
            var before = service.current(); String operation = uuid(); JsonObject request = request(service, finish(Map.of(1, 1)));
            var accepted = service.domainCommand(operation, before.epoch(), before.revision(), "character-command", request);
            assertEquals(1, slots(service)); assertEquals(0L, state(service).spellbook().lastArcaneRecoveryDay());
            assertTrue(service.domainCommand(operation, before.epoch(), before.revision(), "character-command", request).lastCommand().replayed());
            assertEquals(accepted.domain(), service.current().domain());
            endRest(service); start(service); time(service, 60);
            rejected(service, finish(Map.of(1, 1)), "S2_ARCANE_RECOVERY_DAILY_LIMIT");
            apply(service, finish(Map.of()));
            assertEquals(1, slots(service)); assertEquals(0L, state(service).spellbook().lastArcaneRecoveryDay());
            endRest(service); time(service, 1260); start(service); time(service, 60);
            apply(service, finish(Map.of(1, 1)));
            assertEquals(1440, state(service).rest().minute());
            assertEquals(2, slots(service)); assertEquals(1L, state(service).spellbook().lastArcaneRecoveryDay());
            var retained = service.current(); service.save(uuid(), retained.epoch(), retained.revision());
            var current = service.current(); var saved = service.end(uuid(), current.epoch(), current.revision());
            var restored = service.open(saved.fileKey());
            assertEquals(retained.domain(), restored.domain()); assertEquals(retained.history(), restored.history());
            assertEquals(2, slots(service)); assertEquals(1L, state(service).spellbook().lastArcaneRecoveryDay());
            endRest(service); spend(service, 1); start(service); time(service, 60);
            rejected(service, finish(Map.of(1, 1)), "S2_ARCANE_RECOVERY_DAILY_LIMIT");
        }
    }

    @Test void timeBudgetSpentSlotAndUnsupportedRankBoundariesHaveNoPartialWrites() throws Exception {
        try (var service = new CampaignStorageService(temporary.resolve("choices"))) {
            createWizard(service); learn(service);
            rejected(service, finish(Map.of(1, 1)), "S2_REST_NOT_ACTIVE");
            start(service); time(service, 60);
            rejected(service, finish(Map.of(1, 1)), "S2_ARCANE_RECOVERY_CHOICE");
            apply(service, finish(Map.of()));
            assertEquals(2, slots(service)); assertNull(state(service).spellbook().lastArcaneRecoveryDay());
            endRest(service); spend(service, 2); start(service); time(service, 59);
            rejected(service, finish(Map.of(1, 1)), "S2_REST_REQUIREMENTS");
            time(service, 1);
            for (var choices : List.of(Map.of(1, 2), Map.of(2, 1), Map.of(3, 1), Map.of(4, 1), Map.of(5, 1)))
                rejected(service, finish(choices), "S2_ARCANE_RECOVERY_CHOICE");
            apply(service, finish(Map.of(1, 1)));
            assertEquals(1, slots(service)); assertEquals(0L, state(service).spellbook().lastArcaneRecoveryDay());
        }
    }

    @Test void missingAndUnfinishedSpellbooksCannotRecoverSlots() throws Exception {
        try (var service = new CampaignStorageService(temporary.resolve("spellbook"))) {
            createWizard(service); spend(service, 1); start(service); time(service, 60);
            rejected(service, finish(Map.of(1, 1)), "S2_ARCANE_RECOVERY_UNSUPPORTED");
            apply(service, finish(Map.of())); endRest(service); learn(service);
            apply(service, new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE",
                    BundledCampaignRules.rule("character.class", "class.wizard"),
                    new GrowthChoices(BundledCampaignRules.rule("character.subclass", "subclass.evocation"), Map.of(), null, Map.of())));
            start(service); time(service, 60);
            rejected(service, finish(Map.of(1, 1)), "S2_SPELLBOOK_LEARNING_REQUIRED");
        }
    }

    @Test void lateTransactionFailureRollsBackSlotsDayAndBothHistories() throws Exception {
        boolean[] fail = {false};
        try (var service = new CampaignStorageService(temporary.resolve("rollback"), boundary -> {
            if (fail[0] && boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE) throw new IllegalStateException("injected");
        })) {
            createWizard(service); learn(service); spend(service, 1); start(service); time(service, 60);
            var before = service.current(); fail[0] = true;
            assertThrows(RuntimeException.class, () -> apply(service, finish(Map.of(1, 1))));
            fail[0] = false;
            assertEquals(before.domain(), service.current().domain()); assertEquals(before.history(), service.current().history());
            assertEquals(before.revision(), service.current().revision());
            apply(service, finish(Map.of(1, 1)));
            assertEquals(2, slots(service)); assertEquals(0L, state(service).spellbook().lastArcaneRecoveryDay());
        }
    }
}
