package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.storage.CampaignSpaceFixtures.command;
import static org.junit.jupiter.api.Assertions.*;

class CampaignRogueCreationIntegrationTest {
    private static final String ATHLETICS = "proficiency.skill.athletics";
    private static final String PERCEPTION = "proficiency.skill.perception";
    private static final String STEALTH = "proficiency.skill.stealth";
    private static final String INSIGHT = "proficiency.skill.insight";
    private static final String TOOLS = "tool.thieves_tools";
    @TempDir Path temporary;

    @Test void creationRejectsInvalidChoicesBeforeWritingAndReplaysTheSelectedCard() throws Exception {
        try (var service = new CampaignStorageService(temporary)) {
            CampaignWolfBiteIntegrationTest.createWithDeliveredRules(service, "游荡者建卡");
            var before = service.accessSnapshot();
            var context = before.snapshot().domains().material().restore();
            assertEquals(11, context.metadata().rogueCreation().skillOptions().size());
            var valid = rogue("斥候", STEALTH, TOOLS);
            for (var malformed : List.of(
                    changed(valid, "skillKeys", ATHLETICS, INSIGHT, STEALTH), changed(valid, "skillKeys", ATHLETICS, INSIGHT, STEALTH, STEALTH),
                    changed(valid, "skillKeys", ATHLETICS, INSIGHT, STEALTH, "proficiency.skill.arcana"),
                    changed(valid, "expertiseKeys", TOOLS), changed(valid, "expertiseKeys", STEALTH, STEALTH),
                    changed(valid, "expertiseKeys", STEALTH, "proficiency.skill.deception"),
                    changed(valid, "expertiseKeys", STEALTH, "tool.mason_tools"))) {
                assertEquals("DOMAIN_REJECTED", assertThrows(StorageException.class,
                        () -> command(service, "character-create", malformed)).code());
                assertEquals(before, service.accessSnapshot());
            }
            var missing = valid.deepCopy();
            missing.remove("expertiseKeys");
            assertThrows(StorageException.class, () -> command(service, "character-create", missing));
            var wrongClass = valid.deepCopy();
            wrongClass.addProperty("classKey", "wizard");
            assertThrows(StorageException.class, () -> command(service, "character-create", wrongClass));
            assertEquals(before, service.accessSnapshot());

            String request = UUID.randomUUID().toString();
            var payload = CampaignSpaceFixtures.focused(before.snapshot().domains(), valid);
            var created = service.domainCommand(request, before.epoch(), before.revision(), "character-create", payload);
            var card = created.domain().getAsJsonArray("characters").get(0).getAsJsonObject();
            assertEquals(10, card.get("hitPoints").getAsInt());
            var expertise = card.getAsJsonArray("expertise");
            assertEquals(2, expertise.size());
            expertise.forEach(item -> assertEquals(4, item.getAsJsonObject().get("proficiencyContribution").getAsInt()));
            var retained = service.accessSnapshot();
            assertTrue(service.domainCommand(request, before.epoch(), before.revision(), "character-create", payload).lastCommand().replayed());
            assertEquals(retained, service.accessSnapshot());
        }
    }

    @Test void fixedStartingSkillsStayEligibleForExpertiseAndLeaveTheOrdinaryChoices() throws Exception {
        var artifacts = new ArrayList<ProductionRuleArtifact>();
        var delivered = Path.of("src", "main", "resources", "storage", "rules");
        for (var slot : List.of(BundledRuleManifest.Slot.CORE, BundledRuleManifest.Slot.CHARACTERS,
                BundledRuleManifest.Slot.MAGIC, BundledRuleManifest.Slot.HOUSE))
            artifacts.add(ProductionRuleArtifact.read(Files.readAllBytes(delivered.resolve(slot.fileName))));
        var grant = JsonParser.parseString("""
                {
                  "identity": {
                    "kind": "entry",
                    "rule": {
                      "kind": "rule",
                      "module_key": "srd51_characters",
                      "rule_type": "character.class_progression",
                      "rule_key": "progression.rogue"
                    },
                    "collection_key": "starting_proficiencies",
                    "module_key": "test_rogue_fixed_skill",
                    "entry_key": "fixed_perception"
                  },
                  "growth_grant": {
                    "kind": "FIXED",
                    "target": {
                      "kind": "rule",
                      "module_key": "srd51_runtime",
                      "rule_type": "character.proficiency",
                      "rule_key": "proficiency.skill.perception"
                    }
                  }
                }
                """).getAsJsonObject();
        artifacts.add(CampaignRuleSupportTest.extension("test_rogue_fixed_skill", artifacts, List.of(), List.of(grant), List.of()));

        try (var service = new CampaignStorageService(temporary)) {
            var library = service.ruleLibrary();
            var members = artifacts.stream().map(artifact -> {
                String importId = UUID.randomUUID().toString();
                var member = library.importArtifact(importId, artifact.originalZip()).imported().member();
                library.accept(UUID.randomUUID().toString(), member, LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
                return member;
            }).toList();
            var perception = new RuleIdentity("srd51_runtime", "character.proficiency", PERCEPTION);
            var creation = service.previewRules(members).rogueCreation();
            assertEquals(List.of(perception), creation.fixedSkills());
            assertEquals(10, creation.skillOptions().size());
            assertFalse(creation.skillOptions().contains(perception));
            service.create("固定技能与专精", members);

            var before = service.accessSnapshot();
            var redundant = rogue("巡查员", PERCEPTION, TOOLS);
            var rejection = assertThrows(StorageException.class, () -> command(service, "character-create", redundant));
            assertEquals("DOMAIN_REJECTED", rejection.code());
            assertEquals("S2_ROGUE_SKILL_CHOICES", rejection.getMessage());
            assertEquals(before, service.accessSnapshot());

            var valid = changed(redundant, "skillKeys", ATHLETICS, INSIGHT, STEALTH, "proficiency.skill.acrobatics");
            command(service, "character-create", valid);
            var created = service.accessSnapshot().snapshot().domains().characters().getFirst().history().current();
            assertEquals(1, created.proficiencies().stream().filter(row -> row.target().equals(perception)).count());
            assertTrue(created.expertise().contains(perception));
        }
    }

    @Test void groupAndContestUseEachRoguesOwnChoiceAndReopenWithTheSameResults() throws Exception {
        try (var service = new CampaignStorageService(temporary, boundary -> {}, sides -> 10)) {
            CampaignWolfBiteIntegrationTest.createWithDeliveredRules(service, "游荡者检定");
            var world = CampaignSpaceFixtures.world(service, "桥头");
            UUID expert = UUID.fromString(command(service, "character-create", rogue("先锋", ATHLETICS, PERCEPTION)).lastCommand().subjectId());
            UUID trained = UUID.fromString(command(service, "character-create", rogue("斥候", STEALTH, TOOLS)).lastCommand().subjectId());
            CampaignSpaceFixtures.place(service, expert, world);
            CampaignSpaceFixtures.place(service, trained, world);
            var before = service.accessSnapshot().snapshot().domains();
            var context = before.material().restore();
            RuleIdentity athletics = context.rule("character.proficiency", ATHLETICS);
            var group = new JsonObject();
            group.add("rule", CampaignDomainCodec.toJson(context.checkRule()));
            group.addProperty("kind", "SKILL");
            group.addProperty("abilityKey", "ability.strength");
            group.add("proficiency", CampaignDomainCodec.toJson(athletics));
            group.addProperty("difficultyClass", "12");
            group.addProperty("adjudication", "全队拉起绳索");
            group.addProperty("successMessage", "拉起绳索");
            group.addProperty("failureMessage", "绳索滑落");
            var participants = new JsonArray();
            for (var id : List.of(expert, trained)) participants.add(participant(before, id));
            group.add("participants", participants);
            command(service, "group-check", group);
            var grouped = service.accessSnapshot().snapshot().domains();
            for (var person : grouped.characters()) {
                var roll = person.history().events().getLast().checkResult();
                assertEquals(person.id().equals(expert) ? 4 : 2, roll.proficiencyBonus());
            }

            var contest = new JsonObject();
            contest.add("rule", CampaignDomainCodec.toJson(context.checkRule()));
            contest.addProperty("adjudication", "争夺同一条绳索");
            for (var key : List.of("initiator", "responder")) {
                var side = participant(grouped, key.equals("initiator") ? expert : trained);
                side.addProperty("kind", "SKILL");
                side.addProperty("abilityKey", "ability.strength");
                side.add("proficiency", CampaignDomainCodec.toJson(athletics));
                contest.add(key, side);
            }
            var resolved = command(service, "contest-check", contest);
            var saved = service.accessSnapshot().snapshot();
            var audit = JsonParser.parseString(saved.domains().roots().getLast().after()).getAsJsonObject();
            assertEquals(4, audit.getAsJsonObject("initiator").get("proficiencyBonus").getAsInt());
            assertEquals(2, audit.getAsJsonObject("responder").get("proficiencyBonus").getAsInt());
            assertEquals("INITIATOR_WINS", audit.get("outcome").getAsString());
            var file = service.end(UUID.randomUUID().toString(), resolved.epoch(), resolved.revision());
            service.open(file.fileKey());
            assertEquals(saved, service.accessSnapshot().snapshot());
        }
    }

    private static JsonObject participant(CampaignDomainData.State state, UUID id) {
        var card = state.characters().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        var participant = new JsonObject();
        participant.addProperty("characterId", id.toString());
        participant.addProperty("characterVersion", Long.toString(card.history().current().version()));
        participant.addProperty("advantage", false);
        participant.addProperty("disadvantage", false);
        return participant;
    }

    private static JsonObject rogue(String name, String first, String second) {
        var input = new JsonObject();
        input.addProperty("name", name);
        input.addProperty("kind", "PC");
        input.addProperty("classKey", "class.rogue");
        input.addProperty("backgroundKey", "acolyte");
        return changed(changed(input, "skillKeys", ATHLETICS, INSIGHT, PERCEPTION, STEALTH), "expertiseKeys", first, second);
    }

    private static JsonObject changed(JsonObject input, String field, String... keys) {
        var changed = input.deepCopy();
        var values = new JsonArray();
        for (String key : keys) values.add(key);
        changed.add(field, values);
        return changed;
    }
}
