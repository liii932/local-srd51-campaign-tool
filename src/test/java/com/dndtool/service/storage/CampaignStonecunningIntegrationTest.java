package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.Event;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.storage.CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE;
import static org.junit.jupiter.api.Assertions.*;

class CampaignStonecunningIntegrationTest {
    private static final String INTELLIGENCE = "ability.intelligence";
    private static final RuleIdentity HISTORY = new RuleIdentity("srd51_runtime", "character.proficiency",
            "proficiency.skill.history");
    private static final RuleIdentity STONECUNNING = new RuleIdentity("srd51_characters", "character.stonecunning",
            "stonecunning.dwarf");
    private static final RuleIdentity DAMAGE = new RuleIdentity("dm_runtime_house_rules", "adventure.vital_effect",
            "vital_effect.dm_damage");
    private static final RuleIdentity HEALING = new RuleIdentity("dm_runtime_house_rules", "adventure.vital_effect",
            "vital_effect.dm_healing");

    @TempDir Path temporary;

    @Test void standardChecksUseFrozenAncestryAndReplaySavedRolls() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            fixture.service.create("石工知识标准战役");
            UUID world = CampaignSpaceFixtures.world(fixture.service, "石室");
            UUID dwarf = fixture.createCharacter(world, "矮人战士", "fighter", "dwarf", null);
            UUID hillDwarf = fixture.createCharacter(world, "山丘矮人牧师", "cleric", "dwarf", "subrace.hill_dwarf");
            UUID human = fixture.createCharacter(world, "人类牧师", "cleric", "human", null);
            assertFalse(fixture.hasHistoryProficiency(dwarf));
            assertTrue(fixture.hasHistoryProficiency(hillDwarf));
            assertTrue(fixture.hasHistoryProficiency(human));

            fixture.check(dwarf, CheckInput.GENERAL, 12);
            assertCheck(fixture.lastEvent(dwarf), CheckInput.GENERAL, 2, 0, 9, false, null);
            fixture.check(dwarf, CheckInput.STONEWORK_ORIGIN, 12);
            assertCheck(fixture.lastEvent(dwarf), CheckInput.STONEWORK_ORIGIN, 2, 4, 13, true, STONECUNNING);
            assertFalse(fixture.hasHistoryProficiency(dwarf));
            fixture.check(hillDwarf, CheckInput.STONEWORK_ORIGIN, 12);
            assertCheck(fixture.lastEvent(hillDwarf), CheckInput.STONEWORK_ORIGIN, 2, 4, 13, true, STONECUNNING);
            fixture.check(human, CheckInput.STONEWORK_ORIGIN, 12);
            assertCheck(fixture.lastEvent(human), CheckInput.STONEWORK_ORIGIN, 2, 2, 11, false, null);
            assertEquals(List.of(20, 20, 20, 20), fixture.dice);

            fixture.saveEndAndReopen();
            fixture.check(hillDwarf, CheckInput.GENERAL, 12);
            assertCheck(fixture.lastEvent(hillDwarf), CheckInput.GENERAL, 2, 2, 11, false, null);
            assertEquals(5, fixture.dice.size());
        }
    }

    @Test void variantResolvesOnlyTheSelectedBranchWithOneRootAndNoPartialWrite() throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var members = CampaignRuleFixtures.install(fixture.service, BundledCampaignRules.CHECK_VARIANT);
            var preview = fixture.service.previewRules(members);
            fixture.service.create("石工知识家规战役", members);
            assertEquals(preview.binding(), fixture.state().material().binding());
            UUID world = CampaignSpaceFixtures.world(fixture.service, "石室");
            UUID dwarf = fixture.createCharacter(world, "矮人战士", "fighter", "dwarf", null);
            UUID other = fixture.createCharacter(world, "人类同伴", "fighter", "human", null);
            fixture.advanceToFive(dwarf);
            var itemInput = object("characterId", other, "name", "拓片", "quantity", 3);
            itemInput.add("itemId", JsonNull.INSTANCE);
            CampaignSpaceFixtures.command(fixture.service, "item", itemInput);
            UUID item = fixture.state().items().getFirst().id();
            long fullHp = fixture.character(other).history().current().hitPoints();

            var input = fixture.checkInput(dwarf, CheckInput.STONEWORK_ORIGIN, 17);
            input.add("success", branch(other, item, "2", DAMAGE, "找到出处"));
            input.add("failure", branch(other, item, "-1", HEALING, "查阅未果"));
            var before = fixture.service.accessSnapshot();
            var intent = CampaignSpaceFixtures.focused(before.snapshot().domains(), input);

            var unknownSubject = intent.deepCopy();
            checkFields(unknownSubject).addProperty("subject", "STONE_MATERIAL");
            assertCode("INVALID_DOMAIN_DOCUMENT", () -> fixture.submit(id(), before, unknownSubject));
            var wrongAbility = intent.deepCopy();
            checkFields(wrongAbility).addProperty("abilityKey", "ability.wisdom");
            assertCode("INVALID_DOMAIN_DOCUMENT", () -> fixture.submit(id(), before, wrongAbility));
            var forgedMultiplier = intent.deepCopy();
            checkFields(forgedMultiplier).addProperty("proficiencyMultiplier", "10");
            assertCode("INVALID_FILE", () -> fixture.submit(id(), before, forgedMultiplier));
            var invalidFailure = intent.deepCopy();
            invalidFailure.getAsJsonObject("failure").addProperty("itemDelta", "-4");
            assertCode("INVALID_FILE", () -> fixture.submit(id(), before, invalidFailure));
            var staleCard = intent.deepCopy();
            staleCard.addProperty("characterVersion", "0");
            assertCode("STALE_CHARACTER", () -> fixture.submit(id(), before, staleCard));
            assertCode("STALE_REVISION", () -> fixture.service.domainCommand(id(), before.epoch(), before.revision() - 1,
                    "check-effects", intent));
            assertEquals(before, fixture.service.accessSnapshot());
            assertTrue(fixture.dice.isEmpty());

            String request = id();
            fixture.failAfterWriting = true;
            assertEquals("Injected check write failure", assertThrows(IllegalStateException.class,
                    () -> fixture.submit(request, before, intent)).getMessage());
            assertEquals(before, fixture.service.accessSnapshot());
            assertEquals(List.of(20), fixture.dice);

            var accepted = fixture.submit(request, before, intent);
            var after = fixture.service.accessSnapshot();
            assertEquals(before.revision() + 1, accepted.revision());
            assertEquals(before.snapshot().domains().roots().size() + 1, fixture.state().roots().size());
            assertCheck(fixture.lastEvent(dwarf), CheckInput.STONEWORK_ORIGIN, 3, 9, 18, true, STONECUNNING);
            fixture.assertBranch(request, dwarf, other, DAMAGE, fullHp - 3, 5, "找到出处");
            assertTrue(fixture.submit(request, before, intent).lastCommand().replayed());
            var changedSubject = intent.deepCopy();
            checkFields(changedSubject).addProperty("subject", CheckInput.GENERAL);
            assertCode("IDEMPOTENCY_CONFLICT", () -> fixture.submit(request, before, changedSubject));
            assertEquals(after, fixture.service.accessSnapshot());
            assertEquals(List.of(20, 20), fixture.dice);

            String generalRequest = id();
            changedSubject.addProperty("characterVersion", Long.toString(fixture.lastEvent(dwarf).after().version()));
            fixture.submit(generalRequest, after, changedSubject);
            assertCheck(fixture.lastEvent(dwarf), CheckInput.GENERAL, 3, 0, 9, false, null);
            fixture.assertBranch(generalRequest, dwarf, other, HEALING, fullHp, 4, "查阅未果");
            assertEquals(List.of(20, 20, 20), fixture.dice);
            fixture.saveEndAndReopen();
        }
    }

    private static void assertCheck(Event event, String subject, int base, int bonus, int total, boolean success,
            RuleIdentity source) {
        assertEquals(subject, event.command().checkInput().subject());
        var result = event.checkResult();
        assertEquals(10, result.firstD20());
        assertEquals(10, result.selectedD20());
        assertNull(result.secondD20());
        assertEquals(-1, result.abilityModifier());
        assertEquals(base, result.baseProficiencyBonus());
        assertEquals(bonus, result.proficiencyBonus());
        assertEquals(total, result.total());
        assertEquals(success, result.success());
        assertEquals(source, result.stonecunningRule());
    }

    private static JsonObject checkFields(JsonObject input) {
        return input.getAsJsonObject("command").getAsJsonObject("checkInput");
    }

    private static JsonObject branch(UUID target, UUID item, String delta, RuleIdentity effect, String message) {
        var result = object("targetId", target, "itemId", item, "itemDelta", delta, "message", message);
        result.add("healthEffect", CampaignDomainCodec.toJson(effect));
        return result;
    }

    private static JsonObject object(Object... fields) {
        var result = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) {
            result.addProperty((String) fields[i], fields[i + 1].toString());
        }
        return result;
    }

    private static void assertCode(String code, Executable action) {
        assertEquals(code, assertThrows(StorageException.class, action).code());
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static final class Fixture implements AutoCloseable {
        private final CampaignStorageService service;
        private final List<Integer> dice = new ArrayList<>();
        private boolean failAfterWriting, readingFile;

        private Fixture(Path storage) throws IOException {
            service = new CampaignStorageService(storage, boundary -> {
                if (failAfterWriting && boundary == AFTER_CHARACTER_UPDATE) {
                    failAfterWriting = false;
                    throw new IllegalStateException("Injected check write failure");
                }
            }, sides -> {
                assertFalse(readingFile, "Save or reopen requested a new die");
                assertEquals(20, sides);
                dice.add(sides);
                return 10;
            });
        }

        private CampaignDomainData.State state() { return service.accessSnapshot().snapshot().domains(); }

        private CampaignDomainData.Character character(UUID id) {
            return state().characters().stream().filter(character -> character.id().equals(id)).findFirst().orElseThrow();
        }

        private Event lastEvent(UUID characterId) { return character(characterId).history().events().getLast(); }

        private boolean hasHistoryProficiency(UUID characterId) {
            return character(characterId).history().current().proficiencies().stream()
                    .anyMatch(grant -> grant.target().equals(HISTORY));
        }

        private UUID createCharacter(UUID world, String name, String characterClass, String race, String subrace) {
            var input = object("name", name, "kind", "PC", "classKey", characterClass, "backgroundKey", "acolyte", "raceKey", race);
            if (subrace != null) input.addProperty("subraceKey", subrace);
            var created = CampaignSpaceFixtures.command(service, "character-create", input);
            var characterId = UUID.fromString(created.lastCommand().subjectId());
            CampaignSpaceFixtures.place(service, characterId, world);
            return characterId;
        }

        private void advanceToFive(UUID dwarf) {
            var context = state().material().restore();
            for (int level = 2; level <= 5; level++) {
                var subclass = level == 3 ? context.rule("character.subclass", "subclass.champion") : null;
                var increases = level == 4 ? Map.of("ability.constitution", 2) : Map.<String, Integer>of();
                var choices = new GrowthChoices(subclass, increases, null, Map.of());
                var command = new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE",
                        context.rule("character.class", "class.fighter"), choices);
                CampaignSpaceFixtures.command(service, "character-command", input(dwarf, command));
            }
            assertEquals(5, lastEvent(dwarf).after().level());
            assertEquals(3, lastEvent(dwarf).after().proficiencyBonus());
        }

        private JsonObject input(UUID actor, Command command) {
            var result = object("characterId", actor, "characterVersion", lastEvent(actor).after().version());
            result.add("command", CampaignDomainCodec.toJson(command));
            return result;
        }

        private JsonObject checkInput(UUID actor, String subject, int dc) {
            var check = new CheckInput(state().material().restore().checkRule(), "SKILL", INTELLIGENCE, HISTORY,
                    dc, false, false, "DM 描述石砌物的来历。", subject);
            return input(actor, new Command("CHECK_RESOLVED", null, 0, null, null, null,
                    GrowthChoices.EMPTY, null, check));
        }

        private void check(UUID actor, String subject, int dc) {
            CampaignSpaceFixtures.command(service, "character-command", checkInput(actor, subject, dc));
        }

        private CampaignStorageService.CampaignView submit(String request, CampaignStorageService.AccessSnapshot before,
                JsonObject input) {
            return service.domainCommand(request, before.epoch(), before.revision(), "check-effects", input);
        }

        private void assertBranch(String request, UUID actor, UUID target, RuleIdentity effect,
                long hitPoints, long quantity, String message) {
            var root = state().roots().getLast();
            var check = lastEvent(actor);
            var health = lastEvent(target);
            var audit = CampaignDomainCodec.fromJson(JsonParser.parseString(root.after()),
                    CampaignCheckEffects.Audit.class);
            assertEquals(UUID.fromString(request), root.operationId());
            assertEquals("check-effects", root.kind());
            assertEquals(List.of(new CampaignSpaceData.Die(20, 10)), root.rolls());
            assertEquals(Set.of(check.operationId(), health.operationId()), Set.copyOf(root.characterOperations()));
            assertEquals(check.operationId(), audit.checkOperationId());
            assertEquals(health.operationId(), audit.healthOperationId());
            assertEquals(Map.of(actor, check.before().version(), target, health.before().version()),
                    audit.characterVersions());
            assertEquals(check.before().version() + 1, check.after().version());
            assertEquals(health.before().version() + 1, health.after().version());
            assertEquals(check.command().checkInput(), audit.input().command().checkInput());
            assertEquals(check.checkResult().success(), audit.success());
            assertEquals(message, audit.message());
            assertEquals(effect, health.command().healthInput().effect());
            assertEquals(hitPoints, health.after().hitPoints());
            assertEquals(quantity, state().items().getFirst().quantity());
        }

        private void saveEndAndReopen() throws Exception {
            var before = service.accessSnapshot();
            readingFile = true;
            try {
                String saved = service.save(id(), before.epoch(), before.revision()).fileKey();
                String ended = service.end(id(), before.epoch(), before.revision()).fileKey();
                for (String file : List.of(saved, ended)) {
                    var decoded = new CampaignCodec().decode(Files.readAllBytes(service.savedFile(file)));
                    assertEquals(before.snapshot(), decoded.snapshot());
                }
                service.open(ended);
                var after = service.accessSnapshot();
                assertNotEquals(before.epoch(), after.epoch());
                assertEquals(before.snapshot(), after.snapshot());
            } finally {
                readingFile = false;
            }
        }

        @Override public void close() throws Exception { service.close(); }
    }
}
