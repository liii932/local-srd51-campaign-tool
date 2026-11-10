package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.FeatureState;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthInput;
import com.dndtool.service.Schema2CharacterRuntime.HealthResult;
import com.dndtool.service.Schema2CharacterRuntime.ResourceState;
import com.dndtool.service.Schema2CharacterRuntime.RestInput;
import com.dndtool.service.Schema2CombatHistory;
import com.dndtool.service.Schema2CombatState.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class CampaignSecondWindIntegrationTest {
    private static final String FEATURE = "feature.fighter_second_wind";
    private static final String RESOURCE = "resource.fighter.second_wind";
    @TempDir Path temporary;

    @Test void levelOnePaymentHealingAndReceiptAreAtomicAndSurviveDirectOpen() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("whole-file"))) {
            UUID fighter = fixture.character("新兵", "PC", "fighter");
            UUID guard = fixture.character("守卫", "NPC", "fighter");
            assertEquals(List.of(new FeatureState(fixture.rule("character.feature", FEATURE), fixture.fighterRule(), 1)),
                    fixture.card(fighter).features().stream().filter(feature -> feature.feature().ruleKey().equals(FEATURE)).toList());
            assertEquals(new ResourceState(fixture.resourceRule(), 1, 1, false), fixture.resource(fighter));
            fixture.damage(fighter);
            fixture.rejectCharacter(fighter, new Schema2CharacterRuntime.Command("RESOURCE_SPENT", fixture.resourceRule(), 1, null, null),
                    "S2_RESOURCE_FEATURE_OWNER_REQUIRED");
            fixture.startBattle(fighter, guard, 20, 10);
            fixture.combat(Command.action(Kind.DASH, fighter));
            assertFalse(fixture.actor(fighter).actionAvailable());
            assertTrue(fixture.actor(fighter).movementBonus() > 0);

            var before = fixture.access();
            var input = fixture.combatInput(Command.action(Kind.SECOND_WIND, fighter));
            for (String field : List.of("amount", "die", "source")) {
                var forged = input.deepCopy();
                forged.getAsJsonObject("command").addProperty(field, "1");
                assertCode("INVALID_FILE", () -> fixture.submit(id(), before, forged));
            }
            var wrongTarget = input.deepCopy();
            wrongTarget.getAsJsonObject("command").addProperty("targetId", guard.toString());
            assertEquals("S2_COMBAT_COMMAND", assertCode("DOMAIN_REJECTED", () -> fixture.submit(id(), before, wrongTarget)).getMessage());
            var stale = input.deepCopy();
            stale.addProperty("combatVersion", Long.toString(fixture.battle().version() - 1));
            assertCode("STALE_COMBAT", () -> fixture.submit(id(), before, stale));
            assertCode("STALE_REVISION", () -> fixture.service.domainCommand(id(), before.epoch(), before.revision() - 1,
                    "combat-command", input));
            assertEquals(before, fixture.access());

            String request = id();
            var beforeUse = fixture.card(fighter);
            var actor = fixture.actor(fighter);
            fixture.failAfterWrite = true;
            var fault = fixture.withDice(() -> assertThrows(IllegalStateException.class,
                    () -> fixture.submit(request, before, input)), new Die(10, 1));
            assertEquals("second wind transaction failure", fault.getMessage());
            assertEquals(before, fixture.access());

            var accepted = fixture.withDice(() -> fixture.submit(request, before, input), new Die(10, 10));
            fixture.assertSecondWind(fighter, beforeUse, actor, 10);
            assertEquals(fixture.card(fighter).maximumHitPoints(), fixture.card(fighter).hitPoints());
            var committed = fixture.access();
            var replay = fixture.submit(request, before, input);
            assertTrue(replay.lastCommand().replayed());
            assertEquals(accepted.lastCommand().acceptedRevision(), replay.lastCommand().acceptedRevision());
            assertEquals(committed, fixture.access());
            var changed = fixture.combatInput(Command.action(Kind.END_TURN, fighter));
            assertCode("IDEMPOTENCY_CONFLICT", () -> fixture.submit(request, before, changed));
            fixture.rejectCombat(Command.action(Kind.SECOND_WIND, fighter), "S2_RESOURCE_INSUFFICIENT");

            fixture.service.save(id(), committed.epoch(), committed.revision());
            String file = fixture.end();
            byte[] complete = Files.readAllBytes(fixture.service.savedFile(file));
            assertEquals(committed.snapshot(), new CampaignCodec().decode(complete).snapshot());
            assertTamperedFilesRejected(fixture.service, complete, fighter, request);
            fixture.service.open(file);
            assertEquals(committed.snapshot(), fixture.access().snapshot());
            fixture.assertSecondWind(fighter, beforeUse, actor, 10);
            assertCode("STALE_EPOCH", () -> fixture.submit(id(), committed, changed));
            fixture.combat(Command.action(Kind.END_TURN, fighter));
            assertEquals(guard, fixture.battle().activeActor());
        }
    }

    @Test void completedRestsRestoreTheUseAndLevelTwoHealingUsesTheActualFighterLevel() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("rest"))) {
            UUID fighter = fixture.character("战士", "PC", "fighter");
            UUID guard = fixture.character("训练伙伴", "NPC", "fighter");
            fixture.advance(fighter, 2);
            assertEquals(List.of(new Schema2CharacterRuntime.ClassLevel(fixture.fighterRule(), 2)), fixture.card(fighter).classes());
            fixture.damage(fighter);
            fixture.startBattle(fighter, guard, 20, 10);
            var shortRest = rest("REST_STARTED", "SHORT_REST", null, 0);
            var complete = rest("REST_COMPLETED", null, null, 0);
            var active = fixture.access();
            assertCode("RULE_SCENE_OWNER_REQUIRED", () -> fixture.characterCommand(fighter, shortRest));
            assertEquals(active, fixture.access());
            var wounded = fixture.card(fighter);
            var actor = fixture.actor(fighter);
            fixture.combat(Command.action(Kind.SECOND_WIND, fighter), new Die(10, 2));
            fixture.assertSecondWind(fighter, wounded, actor, 2);
            assertEquals(wounded.hitPoints() + 4, fixture.card(fighter).hitPoints());
            fixture.leaveBattle(fighter, guard);

            fixture.characterCommand(fighter, shortRest);
            assertEquals(0, fixture.resource(fighter).current());
            fixture.characterCommand(fighter, rest("TIME_ADVANCED", null, "LIGHT", 59));
            assertEquals(0, fixture.resource(fighter).current());
            fixture.rejectCharacter(fighter, complete, "S2_REST_REQUIREMENTS");
            fixture.characterCommand(fighter, rest("TIME_ADVANCED", null, "LIGHT", 1));
            assertEquals(0, fixture.resource(fighter).current());
            fixture.characterCommand(fighter, complete);
            assertEquals(1, fixture.resource(fighter).current());
            fixture.characterCommand(fighter, rest("REST_ENDED", null, null, 0));

            fixture.startBattle(fighter, guard, 20, 10);
            fixture.combat(Command.action(Kind.SECOND_WIND, fighter), new Die(10, 2));
            fixture.leaveBattle(fighter, guard);
            fixture.characterCommand(fighter, rest("REST_STARTED", "LONG_REST", null, 0));
            assertEquals(0, fixture.resource(fighter).current());
            fixture.characterCommand(fighter, rest("TIME_ADVANCED", null, "SLEEP", 479));
            fixture.rejectCharacter(fighter, complete, "S2_REST_REQUIREMENTS");
            fixture.characterCommand(fighter, rest("TIME_ADVANCED", null, "SLEEP", 1));
            assertEquals(0, fixture.resource(fighter).current());
            fixture.characterCommand(fighter, complete);
            assertEquals(1, fixture.resource(fighter).current());

            fixture.startBattle(fighter, guard, 20, 10);
            var full = fixture.card(fighter);
            assertEquals(full.maximumHitPoints(), full.hitPoints());
            var available = fixture.actor(fighter);
            fixture.combat(Command.action(Kind.SECOND_WIND, fighter), new Die(10, 3));
            fixture.assertSecondWind(fighter, full, available, 3);
        }
    }

    @Test void otherTurnMissingGrantAndUnconsciousActorCannotRollOrSpend() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("eligibility"))) {
            UUID fighter = fixture.character("受伤战士", "PC", "fighter");
            UUID wizard = fixture.character("法师", "NPC", "wizard");
            UUID weapon = fixture.dagger(wizard);
            fixture.damage(fighter);
            fixture.startBattle(fighter, wizard, 10, 20);
            assertEquals(wizard, fixture.battle().activeActor());
            fixture.rejectCombat(Command.action(Kind.SECOND_WIND, fighter), "S2_COMBAT_NOT_CURRENT_TURN");
            fixture.rejectCombat(Command.action(Kind.SECOND_WIND, wizard), "S2_COMBAT_SECOND_WIND_UNAVAILABLE");
            fixture.combat(Command.action(Kind.BEGIN_ATTACK, wizard));
            fixture.combat(Command.attack(wizard, fighter, weapon), new Die(20, 20), new Die(4, 4), new Die(4, 4));
            assertEquals(0, fixture.card(fighter).hitPoints());
            fixture.combat(Command.action(Kind.END_TURN, wizard), new Die(20, 10));
            assertEquals(fighter, fixture.battle().activeActor());
            fixture.rejectCombat(Command.action(Kind.SECOND_WIND, fighter), "S2_COMBAT_ACTOR_INACTIVE");
            assertEquals(1, fixture.resource(fighter).current());
        }
    }

    @Test void pendingMovementBlocksSecondWindAndTwoAttacksKeepTheirSequenceAcrossIt() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("between-attacks"))) {
            UUID fighter = fixture.character("五级战士", "PC", "fighter");
            UUID guard = fixture.character("持刀守卫", "NPC", "fighter");
            fixture.advance(fighter, 5);
            UUID dagger = fixture.dagger(fighter);
            fixture.dagger(guard);
            fixture.damage(fighter);
            fixture.startBattle(fighter, guard, 20, 10);
            fixture.combat(Command.action(Kind.BEGIN_ATTACK, fighter));
            fixture.combat(Command.attack(fighter, guard, dagger), new Die(20, 1));
            var oneAttack = fixture.actor(fighter).sequence();
            assertEquals(new AttackSequence(oneAttack.sequenceKey(), 2, 1, 0, false), oneAttack);
            fixture.combat(Command.move(fighter, List.of(new Position(0, 0))));
            fixture.rejectCombat(Command.action(Kind.SECOND_WIND, fighter), "ACTION_FLOW_BUSY");
            var flow = fixture.battle().activeFlow();
            var window = flow.windows().getLast();
            var opportunity = window.opportunities().stream().filter(row -> row.status() == OpportunityStatus.PENDING)
                    .findFirst().orElseThrow();
            assertEquals(guard, opportunity.actorId());
            fixture.combat(Command.reaction(Kind.PASS_REACTION, guard, null, flow.flowKey(), flow.leafFrameKey(),
                    window.windowKey(), opportunity.opportunityKey()));
            flow = fixture.battle().activeFlow();
            fixture.combat(Command.continuation(fighter, flow.flowKey(), flow.leafFrameKey(), window.windowKey()));
            fixture.combat(Command.move(fighter, List.of(new Position(1, 0))));
            var actor = fixture.actor(fighter);
            assertEquals(oneAttack, actor.sequence());
            assertTrue(actor.movementSpentHalfFeet() > 0);
            var wounded = fixture.card(fighter);
            fixture.combat(Command.action(Kind.SECOND_WIND, fighter), new Die(10, 1));
            fixture.assertSecondWind(fighter, wounded, actor, 1);
            fixture.combat(Command.attack(fighter, guard, dagger), new Die(20, 1));
            assertEquals(new AttackSequence(oneAttack.sequenceKey(), 2, 2, 0, true), fixture.actor(fighter).sequence());
            assertEquals(List.of(0, 1), fixture.battle().flows().stream().flatMap(row -> row.attacks().stream())
                    .filter(attack -> oneAttack.sequenceKey().equals(attack.sequenceKey())).map(AttackAttempt::attackOrdinal).toList());
        }
    }

    private static void assertTamperedFilesRejected(CampaignStorageService service, byte[] complete, UUID fighter, String request) {
        var before = service.status();
        var badAlgorithm = CampaignJson.read(complete);
        definition(badAlgorithm, FEATURE).addProperty("executionAlgorithm", "UNKNOWN_HEALING_ALGORITHM");
        assertCode("INVALID_FILE", () -> service.importFile(resign(badAlgorithm)));
        var badResource = CampaignJson.read(complete);
        definition(badResource, RESOURCE).addProperty("maximumProfile", "1-20:2");
        assertCode("INVALID_FILE", () -> service.importFile(resign(badResource)));

        var missingGrant = CampaignJson.read(complete);
        var history = domains(missingGrant).getAsJsonArray("characters").asList().stream().map(JsonElement::getAsJsonObject)
                .map(row -> row.getAsJsonObject("history"))
                .filter(row -> fighter.toString().equals(row.getAsJsonObject("current").get("characterId").getAsString()))
                .findFirst().orElseThrow();
        assertTrue(removeGrant(history.getAsJsonObject("current")));
        for (var row : history.getAsJsonArray("events")) {
            var event = row.getAsJsonObject();
            if (event.get("before").isJsonObject()) removeGrant(event.getAsJsonObject("before"));
            removeGrant(event.getAsJsonObject("after"));
        }
        assertCode("INVALID_FILE", () -> service.importFile(resign(missingGrant)));

        var alteredRoll = CampaignJson.read(complete);
        var combatEvent = domains(alteredRoll).getAsJsonArray("combats").get(0).getAsJsonObject()
                .getAsJsonArray("events").asList().getLast().getAsJsonObject();
        assertEquals(request, combatEvent.get("operationId").getAsString());
        assertEquals("SECOND_WIND", combatEvent.getAsJsonObject("command").get("kind").getAsString());
        var roll = combatEvent.getAsJsonObject("resolution").getAsJsonArray("rolls").get(0).getAsJsonObject();
        var root = domains(alteredRoll).getAsJsonArray("roots").asList().getLast().getAsJsonObject();
        assertEquals(request, root.get("operationId").getAsString());
        var rootRoll = root.getAsJsonArray("rolls").get(0).getAsJsonObject();
        assertEquals("10", roll.get("value").getAsString());
        assertEquals(roll.get("value"), rootRoll.get("value"));
        roll.addProperty("value", "9");
        rootRoll.addProperty("value", "9");
        assertCode("INVALID_FILE", () -> service.importFile(resign(alteredRoll)));
        assertEquals(before, service.status());
    }

    private static JsonObject domains(JsonObject file) { return file.getAsJsonObject("domains"); }

    private static JsonObject definition(JsonObject file, String key) {
        return domains(file).getAsJsonObject("material").getAsJsonArray("rules").asList().stream()
                .map(JsonElement::getAsJsonObject).map(rule -> rule.getAsJsonObject("definition"))
                .filter(definition -> key.equals(definition.getAsJsonObject("identity").get("ruleKey").getAsString()))
                .findFirst().orElseThrow();
    }

    private static boolean removeGrant(JsonObject state) {
        return state.getAsJsonArray("features").asList().removeIf(row ->
                FEATURE.equals(row.getAsJsonObject().getAsJsonObject("feature").get("ruleKey").getAsString()));
    }

    private static byte[] resign(JsonObject file) {
        var integrity = file.remove("integrity").getAsJsonObject();
        file.addProperty("fileKey", id());
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
        file.add("integrity", integrity);
        return CampaignJson.canonical(file);
    }

    private static Schema2CharacterRuntime.Command rest(String kind, String restKind, String activity, int minutes) {
        return new Schema2CharacterRuntime.Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY,
                new RestInput(restKind, activity, minutes, 0, Map.of()));
    }

    private static StorageException assertCode(String code, Executable call) {
        var failure = assertThrows(StorageException.class, call);
        assertEquals(code, failure.code(), failure.getMessage());
        return failure;
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static JsonObject fields(Object... pairs) {
        var result = new JsonObject();
        for (int index = 0; index < pairs.length; index += 2) result.addProperty((String) pairs[index], pairs[index + 1].toString());
        return result;
    }

    private record Die(int sides, int value) { }

    private static final class Fixture implements AutoCloseable {
        final CampaignStorageService service;
        final CampaignRuleContext rules;
        final ArrayDeque<Die> dice = new ArrayDeque<>();
        boolean failAfterWrite;

        Fixture(Path root) throws IOException {
            service = new CampaignStorageService(root, boundary -> {
                if (boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE && failAfterWrite) {
                    failAfterWrite = false;
                    throw new IllegalStateException("second wind transaction failure");
                }
            }, sides -> {
                Die roll = dice.poll();
                assertNotNull(roll, "Unexpected live d" + sides);
                assertEquals(roll.sides(), sides);
                return roll.value();
            });
            var library = service.ruleLibrary();
            var members = BundledCampaignRules.artifacts().stream().map(artifact -> {
                var imported = library.importArtifact(id(), artifact.originalZip()).imported().member();
                var review = library.review(imported.moduleKey(), imported.releaseVersion());
                assertEquals(imported, review.imported().member());
                assertNull(review.acceptance());
                library.accept(id(), review.imported().member(), LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
                return imported;
            }).toList();
            var preview = service.previewRules(members);
            assertEquals(members, preview.binding().members());
            assertTrue(preview.catalog().stream().anyMatch(rule -> rule.key().equals(FEATURE) && rule.executable()));
            service.create("回气验收", members);
            assertEquals(preview.binding(), state().material().binding());
            rules = state().material().restore();
        }

        CampaignStorageService.AccessSnapshot access() { return service.accessSnapshot(); }
        CampaignDomainData.State state() { return access().snapshot().domains(); }
        Schema2CombatHistory.History battleHistory() { return CampaignSpaceCommands.currentBattle(state()); }
        Battle battle() { return battleHistory().current(); }
        Combatant actor(UUID id) {
            return battle().combatants().stream().filter(actor -> actor.characterId().equals(id)).findFirst().orElseThrow();
        }
        Schema2CharacterRuntime.History history(UUID id) {
            return state().characters().stream().filter(card -> card.id().equals(id)).findFirst().orElseThrow().history();
        }
        Schema2CharacterRuntime.State card(UUID id) { return history(id).current(); }
        RuleIdentity rule(String type, String key) { return rules.rule(type, key); }
        RuleIdentity fighterRule() { return rule("character.class", "class.fighter"); }
        RuleIdentity resourceRule() { return rule("character.resource", RESOURCE); }
        ResourceState resource(UUID id) {
            return card(id).resources().stream().filter(resource -> resource.identity().equals(resourceRule())).findFirst().orElseThrow();
        }

        UUID character(String name, String kind, String classKey) {
            return UUID.fromString(CampaignSpaceFixtures.command(service, "character-create",
                    fields("name", name, "kind", kind, "classKey", classKey, "backgroundKey", "acolyte")).lastCommand().subjectId());
        }

        void characterCommand(UUID id, Schema2CharacterRuntime.Command command) {
            var input = fields("characterId", id, "characterVersion", card(id).version());
            input.add("command", CampaignDomainCodec.toJson(command));
            CampaignSpaceFixtures.command(service, "character-command", input);
        }

        void rejectCharacter(UUID id, Schema2CharacterRuntime.Command command, String reason) {
            var before = access();
            assertEquals(reason, assertCode("DOMAIN_REJECTED", () -> characterCommand(id, command)).getMessage());
            assertEquals(before, access());
        }

        void advance(UUID fighter, int targetLevel) {
            for (int level = card(fighter).level() + 1; level <= targetLevel; level++) {
                var subclass = level == 3 ? rule("character.subclass", "subclass.champion") : null;
                var abilities = level == 4 ? Map.of("ability.strength", 2) : Map.<String, Integer>of();
                characterCommand(fighter, new Schema2CharacterRuntime.Command("CHARACTER_ADVANCED", null, 0,
                        null, "FIXED_AVERAGE", fighterRule(), new GrowthChoices(subclass, abilities, null, Map.of())));
            }
            assertEquals(targetLevel, card(fighter).level());
        }

        void damage(UUID id) {
            PlayerSpaceFixture.placeAtWorld(service, id);
            var health = new HealthInput(rule("adventure.health", "health.srd2014"),
                    rule("adventure.vital_effect", "vital_effect.dm_damage"), null, "主持人确认环境伤害");
            long hp = card(id).hitPoints();
            characterCommand(id, new Schema2CharacterRuntime.Command("HEALTH_EFFECT_RESOLVED", null, 0,
                    null, null, null, GrowthChoices.EMPTY, null, null, null, health));
            assertEquals(hp - 6, card(id).hitPoints());
        }

        UUID dagger(UUID actor) {
            UUID id = UUID.randomUUID();
            equipment(actor, "ITEM_ACQUIRED", new EquipmentInput(id, rule("equipment.item", "item.dagger"), "CARRIED", "主持人确认取得匕首"));
            equipment(actor, "ITEM_EQUIPPED", new EquipmentInput(id, null, "MAIN_HAND", "主持人确认装备匕首"));
            return id;
        }

        void equipment(UUID actor, String kind, EquipmentInput input) {
            var character = fields("characterId", actor, "characterVersion", card(actor).version());
            character.add("command", CampaignDomainCodec.toJson(new Schema2CharacterRuntime.Command(kind, null, 0,
                    null, null, null, GrowthChoices.EMPTY, null, null, input, null)));
            CampaignSpaceFixtures.command(service, kind.equals("ITEM_ACQUIRED") ? input.itemId().toString() : id(), "character-command", character);
        }

        void startBattle(UUID fighter, UUID guard, int fighterInitiative, int guardInitiative) {
            withDice(() -> {
                PlayerSpaceFixture.beginBattle(service, fighter, guard);
                return null;
            }, new Die(20, fighterInitiative), new Die(20, guardInitiative));
        }

        void leaveBattle(UUID fighter, UUID guard) {
            combat(Command.action(Kind.END_COMBAT, fighter));
            rejectCombat(Command.action(Kind.SECOND_WIND, fighter), "S2_COMBAT_REQUIRED");
            var before = state();
            var subjects = new JsonArray();
            for (UUID id : List.of(fighter, guard))
                subjects.add(fields("id", id, "version", CampaignSpaceCommands.position(before, id).version()));
            var leave = new JsonObject();
            leave.add("subjects", subjects);
            CampaignSpaceFixtures.command(service, "scene-leave", leave);
        }

        void rejectCombat(Command command, String reason) {
            var before = access();
            assertEquals(reason, assertCode("DOMAIN_REJECTED", () -> submit(id(), before, combatInput(command))).getMessage());
            assertEquals(before, access());
        }

        void combat(Command command, Die... rolls) {
            withDice(() -> submit(id(), access(), combatInput(command)), rolls);
        }

        <T> T withDice(Supplier<T> action, Die... rolls) {
            assertTrue(dice.isEmpty());
            dice.addAll(List.of(rolls));
            var result = action.get();
            assertTrue(dice.isEmpty(), "Not all scripted dice were drawn");
            return result;
        }

        JsonObject combatInput(Command command) {
            var battle = battle();
            var input = fields("combatId", battle.combatId(), "combatVersion", battle.version());
            input.add("command", CampaignDomainCodec.toJson(command));
            return CampaignSpaceFixtures.focused(state(), input);
        }

        CampaignStorageService.CampaignView submit(String request, CampaignStorageService.AccessSnapshot at, JsonObject input) {
            return service.domainCommand(request, at.epoch(), at.revision(), "combat-command", input);
        }

        void assertSecondWind(UUID fighter, Schema2CharacterRuntime.State before, Combatant combatant, int die) {
            var event = battleHistory().events().getLast();
            assertEquals(Command.action(Kind.SECOND_WIND, fighter), event.command());
            var resolution = event.resolution();
            assertEquals(List.of(new Roll("SECOND_WIND_HEALING", fighter, 10, die)), resolution.rolls());
            assertEquals(2, resolution.healthChanges().size());
            var payment = resolution.healthChanges().getFirst();
            var healing = resolution.healthChanges().getLast();
            assertEquals(fighter, payment.characterId());
            assertEquals(fighter, healing.characterId());
            assertEquals(before, payment.before());
            assertEquals(resourceRule(), payment.resource());
            assertEquals(1, payment.resourceAmount());
            assertEquals(before.hitPoints(), payment.after().hitPoints());
            assertEquals(payment.after(), healing.before());
            assertEquals(payment.frameKey(), healing.frameKey());
            assertEquals(List.of(new CombatFact("SECOND_WIND", fighter, payment.frameKey(), "BONUS_ACTION")), resolution.facts());
            assertTrue(healing.healing());
            int rolled = die + before.classes().getFirst().level();
            int applied = (int) Math.min(rolled, before.maximumHitPoints() - before.hitPoints());
            var result = new HealthResult(List.of(die), null, null, rolled, applied);
            assertEquals(result, healing.healthResult());
            assertEquals(before.hitPoints() + applied, card(fighter).hitPoints());
            assertEquals(before.version() + 2, card(fighter).version());
            assertEquals(event.before().version() + 1, event.after().version());
            assertEquals(new ResourceState(resourceRule(), 0, 1, false), resource(fighter));
            assertEquals(combatant, actor(fighter));
            var events = history(fighter).events();
            var spent = events.get(events.size() - 2);
            var healed = events.getLast();
            assertEquals("COMBAT_RESOURCE_SPENT", spent.kind());
            assertEquals("COMBAT_HEALING_APPLIED", healed.kind());
            assertEquals(result, healed.healthResult());
            var root = state().roots().getLast();
            assertEquals(event.operationId(), root.operationId());
            assertEquals(Set.of(spent.operationId(), healed.operationId()), Set.copyOf(root.characterOperations()));
            assertEquals(List.of(new CampaignSpaceData.Die(10, die)), root.rolls());
        }

        String end() {
            var at = access();
            return service.end(id(), at.epoch(), at.revision()).fileKey();
        }

        @Override public void close() throws Exception { service.close(); }
    }
}
