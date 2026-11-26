package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CampaignHillDwarfIntegrationTest {
    private static final String CHARACTERS = BundledCampaignRules.CHARACTER_MODULE;
    private static final RuleIdentity HILL = new RuleIdentity(CHARACTERS, "character.subrace", "subrace.hill_dwarf");
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectedSourceControlsMaximumHpAndRollsThroughReopen(boolean houseRule) throws Exception {
        var rolls = new AtomicInteger();
        try (var service = new CampaignStorageService(temporary, boundary -> { }, sides -> {
            assertEquals(10, sides);
            rolls.incrementAndGet();
            return 6;
        })) {
            if (houseRule) createHouseCampaign(service);
            else service.create("标准强韧");

            var before = service.accessSnapshot();
            var invalid = assertThrows(StorageException.class, () -> createFighter(service, "human", HILL.ruleKey()));
            assertEquals("DOMAIN_REJECTED", invalid.code());
            assertEquals(before, service.accessSnapshot());

            UUID dwarf = createFighter(service, "dwarf", null);
            UUID hill = createFighter(service, "dwarf", HILL.ruleKey());
            var generic = character(service, dwarf);
            assertNull(generic.initial().character().sources().subrace());
            assertEquals(12, generic.history().current().maximumHitPoints());
            var selected = character(service, hill);
            assertEquals(HILL, selected.initial().character().sources().subrace());
            assertEquals(houseRule ? 14 : 13, selected.history().current().maximumHitPoints());

            var input = new JsonObject();
            input.addProperty("characterId", hill.toString());
            input.addProperty("characterVersion", Long.toString(selected.history().current().version()));
            var advance = new Command("CHARACTER_ADVANCED", null, 0, null, "SERVER_ROLL",
                    selected.history().current().characterClass(), GrowthChoices.EMPTY);
            input.add("command", CampaignDomainCodec.toJson(advance));
            var at = service.accessSnapshot();
            var payload = CampaignSpaceFixtures.focused(at.snapshot().domains(), input);
            String request = UUID.randomUUID().toString();
            service.domainCommand(request, at.epoch(), at.revision(), "character-command", payload);
            assertTrue(service.domainCommand(request, at.epoch(), at.revision(), "character-command", payload).lastCommand().replayed());
            var advanced = character(service, hill);
            assertEquals(6, advanced.history().events().getLast().hitDieRoll());
            assertEquals(houseRule ? 8 : 9, advanced.history().current().maximumHitPoints() - selected.history().current().maximumHitPoints());
            assertEquals(advanced.history().current().maximumHitPoints(), advanced.history().current().hitPoints());

            var saved = service.accessSnapshot();
            String file = service.end(UUID.randomUUID().toString(), saved.epoch(), saved.revision()).fileKey();
            service.open(file);
            assertEquals(advanced, character(service, hill));
            assertEquals(generic, character(service, dwarf));
            assertEquals(saved.snapshot().domains().material(), service.accessSnapshot().snapshot().domains().material());
            assertEquals(1, rolls.get());
        }
    }

    private static CampaignDomainData.Character character(CampaignStorageService service, UUID id) {
        return service.accessSnapshot().snapshot().domains().characters().stream()
                .filter(character -> character.id().equals(id)).findFirst().orElseThrow();
    }

    private static UUID createFighter(CampaignStorageService service, String race, String subrace) {
        var input = new JsonObject();
        input.addProperty("name", subrace == null ? "矮人战士" : "山丘战士");
        input.addProperty("kind", "PC");
        input.addProperty("classKey", "fighter");
        input.addProperty("backgroundKey", "acolyte");
        input.addProperty("raceKey", race);
        if (subrace != null) input.addProperty("subraceKey", subrace);
        return UUID.fromString(CampaignSpaceFixtures.command(service, "character-create", input).lastCommand().subjectId());
    }

    private static void createHouseCampaign(CampaignStorageService service) throws Exception {
        var original = CampaignRuleSupportTest.authorRow(CHARACTERS, "rules.json", "rule_key", "subrace_hit_points.hill_dwarf");
        var changes = new ArrayList<JsonObject>();
        for (String field : List.of("first_level_bonus", "per_additional_level_bonus")) {
            var change = CampaignRuleSupportTest.operation("set_field", original);
            change.addProperty("field", field);
            change.addProperty("value", field.equals("first_level_bonus") ? 2 : 0);
            changes.add(change);
        }
        var base = BundledCampaignRules.artifacts(BundledCampaignRules.STANDARD);
        var extension = CampaignRuleSupportTest.extension("test_hill_dwarf", base, List.of(), List.of(), changes);
        var members = new ArrayList<>(CampaignRuleFixtures.install(service));
        var library = service.ruleLibrary();
        var member = library.importArtifact(UUID.randomUUID().toString(), extension.originalZip()).imported().member();
        library.accept(UUID.randomUUID().toString(), member, LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
        members.add(member);
        service.create("家规强韧", members);
    }
}
