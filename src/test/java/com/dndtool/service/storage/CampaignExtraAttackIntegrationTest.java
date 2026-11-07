package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.FeatureState;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthInput;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.Schema2CombatState.*;
import static com.dndtool.service.storage.CampaignParticipationData.*;
import static com.dndtool.service.storage.CampaignStorageService.Boundary;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises acquired Extra Attack through accepted bundles, the SQLite command gate and complete files. */
class CampaignExtraAttackIntegrationTest {
    private static final String FEATURE = "feature.fighter_extra_attack";
    private static final String STRENGTH = "ability.strength";
    @TempDir Path temporary;

    @Test void fifthLevelRetainsItsFirstStrikeThroughMovementAndReopenThenCommitsTheSecondOnce() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("round-trip"))) {
            var service = fixture.service;
            fixture.create();
            UUID fighter = fixture.character("战士", "PC");
            UUID firstTarget = fixture.character("近侧守卫", "NPC");
            UUID secondTarget = fixture.character("远侧守卫", "NPC");
            UUID weapon = fixture.equip(fighter);
            UUID defenderWeapon = fixture.equip(firstTarget);
            UUID member = fixture.player(fighter, firstTarget, secondTarget);
            List<Deployment> placements = List.of(new Deployment(fighter, new Position(1, 1), 0),
                    new Deployment(firstTarget, new Position(2, 1), 1),
                    new Deployment(secondTarget, new Position(0, 3), 1));

            fixture.advance(fighter, 4);
            assertTrue(fixture.extraAttack(fighter).isEmpty());
            fixture.startBattle("四级试炼", placements, 20, 10, 5);
            fixture.combat(Command.action(Kind.BEGIN_ATTACK, fighter));
            fixture.combat(Command.attack(fighter, firstTarget, weapon), 1);
            fixture.assertSequence(fighter, 1, 1, 0, true);
            fixture.reject(Command.attack(fighter, firstTarget, weapon), "S2_COMBAT_SEQUENCE_UNAVAILABLE");
            fixture.leaveBattle(fighter, firstTarget, secondTarget);

            fixture.advance(fighter, 5);
            assertEquals(List.of(new FeatureState(fixture.rule("character.feature", FEATURE),
                    fixture.rule("character.class", "class.fighter"), 5)), fixture.extraAttack(fighter));
            fixture.exerciseCharacterCommands(fighter, weapon);
            fixture.startBattle("五级试炼", placements, 20, 10, 5);
            fixture.reject(Command.action(Kind.BEGIN_ATTACK, firstTarget), "S2_COMBAT_NOT_CURRENT_TURN");
            var beforeAttack = fixture.access();
            var forgedAllowance = fixture.combatInput(Command.action(Kind.BEGIN_ATTACK, fighter));
            forgedAllowance.getAsJsonObject("command").addProperty("limit", "3");
            assertCode("INVALID_FILE", () -> fixture.submit(id(), beforeAttack, forgedAllowance, null));
            assertEquals(beforeAttack, fixture.access());
            fixture.combat(Command.action(Kind.BEGIN_ATTACK, fighter));
            AttackSequence started = fixture.assertSequence(fighter, 2, 0, 0, false);
            long firstHp = fixture.card(firstTarget).hitPoints();
            long secondHp = fixture.card(secondTarget).hitPoints();
            fixture.combat(Command.attack(fighter, firstTarget, weapon), 15, 2);
            AttackAttempt first = fixture.attempts(started).getFirst();
            assertEquals(0, first.attackOrdinal());
            assertEquals(15, first.firstD20());
            assertEquals(List.of(2), first.damageDice());
            assertEquals(firstHp - first.damageAmount(), fixture.card(firstTarget).hitPoints());
            AttackSequence afterFirst = fixture.assertSequence(fighter, 2, 1, 0, false);
            assertEquals(List.of(firstTarget), fixture.attackTargets(member));
            fixture.reject(Command.attack(fighter, secondTarget, weapon), "S2_COMBAT_OUT_OF_REACH");
            fixture.reject(Command.action(Kind.BEGIN_ATTACK, fighter), "S2_COMBAT_ACTION_UNAVAILABLE");

            var destination = new Position(0, 2);
            fixture.combat(Command.move(fighter, List.of(destination)));
            assertEquals(afterFirst, fixture.actor(fighter).sequence());
            assertEquals(new Position(1, 1), fixture.actor(fighter).position());
            fixture.reject(Command.attack(fighter, firstTarget, weapon), "ACTION_FLOW_BUSY");
            fixture.reject(Command.action(Kind.FORFEIT_ATTACKS, fighter), "ACTION_FLOW_BUSY");
            fixture.react(Kind.DECLARE_OPPORTUNITY_ATTACK, firstTarget, defenderWeapon, 1);
            fixture.continueMovement(fighter);
            assertEquals(destination, fixture.actor(fighter).position());
            assertEquals(10, fixture.actor(fighter).movementSpentHalfFeet());
            assertEquals(afterFirst, fixture.actor(fighter).sequence());
            assertEquals(List.of(secondTarget), fixture.attackTargets(member));

            var beforeSave = fixture.access();
            var manual = service.save(id(), beforeSave.epoch(), beforeSave.revision());
            String finalFile = fixture.end();
            assertEquals(beforeSave.snapshot(), new CampaignCodec().decode(Files.readAllBytes(service.savedFile(manual.fileKey()))).snapshot());
            service.open(finalFile);
            var reopened = fixture.access();
            assertNotEquals(beforeSave.epoch(), reopened.epoch());
            assertEquals(beforeSave.snapshot(), reopened.snapshot());
            assertEquals(List.of(first), fixture.attempts(started));
            assertEquals(afterFirst, fixture.assertSequence(fighter, 2, 1, 0, false));

            var candidate = fixture.attackCandidates(member).getFirst();
            JsonObject input = candidate.input();
            assertEquals(secondTarget, fixture.candidateTarget(candidate));
            var actor = Actor.player(member, fighter);
            assertCode("STALE_EPOCH", () -> service.domainCommand(id(), beforeSave.epoch(), reopened.revision(),
                    candidate.action(), input, actor, ignored -> { }));
            assertCode("STALE_REVISION", () -> service.domainCommand(id(), reopened.epoch(), reopened.revision() + 1,
                    candidate.action(), input, actor, ignored -> { }));
            var staleCombat = input.deepCopy();
            staleCombat.addProperty("combatVersion", Long.toString(fixture.battle().version() + 1));
            assertCode("STALE_COMBAT", () -> fixture.submit(id(), reopened, staleCombat, actor));
            assertEquals(reopened, fixture.access());

            String secondRequest = id();
            fixture.failure = Boundary.AFTER_CHARACTER_UPDATE;
            fixture.dice.addAll(List.of(16, 1));
            assertEquals("injected transaction failure", assertThrows(IllegalStateException.class,
                    () -> fixture.submit(secondRequest, reopened, input, actor)).getMessage());
            assertTrue(fixture.dice.isEmpty());
            assertEquals(reopened, fixture.access());
            assertEquals(List.of(first), fixture.attempts(started));
            assertEquals(secondHp, fixture.card(secondTarget).hitPoints());

            fixture.dice.addAll(List.of(17, 3));
            var accepted = fixture.submit(secondRequest, reopened, input, actor);
            assertTrue(fixture.dice.isEmpty());
            var closed = fixture.assertSequence(fighter, 2, 2, 0, true);
            assertEquals(started.sequenceKey(), closed.sequenceKey());
            var attempts = fixture.attempts(closed);
            assertEquals(2, attempts.size());
            assertEquals(first, attempts.getFirst());
            var second = attempts.getLast();
            assertEquals(1, second.attackOrdinal());
            assertEquals(secondTarget, second.targetId());
            assertEquals(17, second.firstD20());
            assertEquals(List.of(3), second.damageDice());
            assertEquals(secondHp - second.damageAmount(), fixture.card(secondTarget).hitPoints());
            assertEquals(1, fixture.state().roots().stream().filter(root -> root.operationId().toString().equals(secondRequest)).count());
            assertEquals(List.of(17, 3), fixture.state().roots().getLast().rolls().stream().map(CampaignSpaceData.Die::value).toList());
            assertTrue(fixture.attackCandidates(member).isEmpty());

            var replay = fixture.submit(secondRequest, reopened, input, actor);
            assertTrue(replay.lastCommand().replayed());
            assertEquals(accepted.lastCommand().acceptedRevision(), replay.lastCommand().acceptedRevision());
            assertEquals(accepted.history(), replay.history());
            assertEquals(accepted.domain(), replay.domain());
            var changed = input.deepCopy();
            changed.getAsJsonObject("command").addProperty("targetId", firstTarget.toString());
            assertCode("IDEMPOTENCY_CONFLICT", () -> fixture.submit(secondRequest, reopened, changed, actor));
            fixture.reject(Command.attack(fighter, secondTarget, weapon), "S2_COMBAT_SEQUENCE_UNAVAILABLE");

            UUID scene = fixture.battle().combatId();
            byte[] complete = Files.readAllBytes(service.savedFile(fixture.end()));
            assertTamperedFilesRejected(service, complete, scene, fighter);
        }
    }

    @Test void sixthLevelCanForfeitAndOpportunityAndReadyEachUseOneSeparateStrike() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("reactions"))) {
            fixture.create();
            UUID fighter = fixture.character("六级战士", "PC"), target = fixture.character("守卫", "NPC");
            UUID weapon = fixture.equip(fighter);
            UUID member = fixture.player(fighter, target);
            fixture.advance(fighter, 6);
            assertEquals(1, fixture.extraAttack(fighter).size());
            fixture.startBattle("反应试炼", List.of(new Deployment(fighter, new Position(1, 0), 0),
                    new Deployment(target, new Position(2, 0), 1)), 20, 10);

            fixture.combat(Command.action(Kind.BEGIN_ATTACK, fighter));
            fixture.combat(Command.attack(fighter, target, weapon), 1);
            assertEquals(List.of(target), fixture.attackTargets(member));
            fixture.combat(Command.action(Kind.FORFEIT_ATTACKS, fighter));
            var forfeited = fixture.assertSequence(fighter, 2, 1, 1, true);
            assertTrue(fixture.attackCandidates(member).isEmpty());
            fixture.reject(Command.attack(fighter, target, weapon), "S2_COMBAT_SEQUENCE_UNAVAILABLE");

            fixture.combat(Command.action(Kind.END_TURN, fighter));
            fixture.combat(Command.move(target, List.of(new Position(3, 0))));
            fixture.react(Kind.DECLARE_OPPORTUNITY_ATTACK, fighter, weapon, 1);
            assertFalse(fixture.actor(fighter).reactionAvailable());
            fixture.continueMovement(target);
            var opportunity = fixture.battle().flows().getLast().attacks().getFirst();
            assertEquals(List.of(opportunity), fixture.battle().flows().getLast().attacks());
            assertTrue(opportunity.opportunityAttack());
            assertNull(opportunity.sequenceKey());
            assertEquals(forfeited, fixture.actor(fighter).sequence());

            fixture.combat(Command.action(Kind.END_TURN, target));
            assertTrue(fixture.actor(fighter).actionAvailable());
            assertTrue(fixture.actor(fighter).reactionAvailable());
            var trigger = new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER, target, new Position(2, 0));
            fixture.combat(Command.ready(fighter, new ReadyInput(trigger, ReadyResponseKind.ATTACK, target, weapon, List.of(), null)));
            var ready = fixture.battle().readyPreparations().getFirst();
            assertEquals(ReadyStatus.ARMED, ready.status());
            assertFalse(fixture.actor(fighter).actionAvailable());
            assertNull(fixture.actor(fighter).sequence());
            fixture.combat(Command.action(Kind.END_TURN, fighter));
            fixture.combat(Command.move(target, List.of(new Position(2, 0))));
            Flow flow = fixture.battle().activeFlow();
            Window window = flow.windows().getLast();
            Opportunity decision = pending(window, fighter);
            fixture.combat(Command.releaseReady(fighter, ready.readyKey(), flow.flowKey(), flow.leafFrameKey(),
                    window.windowKey(), decision.opportunityKey()), 1);
            fixture.continueMovement(target);
            var released = fixture.battle().flows().getLast();
            assertEquals(1, released.attacks().size());
            assertEquals("RELEASED", released.readyReleases().getFirst().outcome());
            assertNull(released.attacks().getFirst().sequenceKey());
            assertFalse(released.attacks().getFirst().opportunityAttack());
            assertNull(fixture.actor(fighter).sequence());
            assertFalse(fixture.actor(fighter).reactionAvailable());

            fixture.combat(Command.action(Kind.END_TURN, target));
            fixture.combat(Command.action(Kind.BEGIN_ATTACK, fighter));
            fixture.combat(Command.attack(fighter, target, weapon), 1);
            fixture.combat(Command.action(Kind.END_TURN, fighter));
            fixture.assertSequence(fighter, 2, 1, 1, true);
        }
    }

    private static void assertTamperedFilesRejected(CampaignStorageService service, byte[] complete, UUID scene, UUID fighter) {
        var before = service.status();
        var material = CampaignJson.read(complete);
        var feature = material.getAsJsonObject("domains").getAsJsonObject("material").getAsJsonArray("rules").asList().stream()
                .map(JsonElement::getAsJsonObject).map(rule -> rule.getAsJsonObject("definition"))
                .filter(definition -> FEATURE.equals(definition.getAsJsonObject("identity").get("ruleKey").getAsString()))
                .findFirst().orElseThrow();
        feature.addProperty("executionAlgorithm", "UNKNOWN_FIGHTER_ATTACKS");
        assertCode("INVALID_FILE", () -> service.importFile(resign(material)));

        var wrongOrdinal = CampaignJson.read(complete);
        var history = wrongOrdinal.getAsJsonObject("domains").getAsJsonArray("combats").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(combat -> scene.toString().equals(combat.getAsJsonObject("current").get("combatId").getAsString()))
                .findFirst().orElseThrow();
        var battle = history.getAsJsonObject("current");
        var actor = battle.getAsJsonArray("combatants").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(combatant -> fighter.toString().equals(combatant.get("characterId").getAsString())).findFirst().orElseThrow();
        var attack = battle.getAsJsonArray("flows").asList().getLast().getAsJsonObject().getAsJsonArray("attacks").get(0).getAsJsonObject();
        assertEquals(actor.getAsJsonObject("sequence").get("sequenceKey"), attack.get("sequenceKey"));
        attack.addProperty("attackOrdinal", "0");
        history.getAsJsonArray("events").asList().getLast().getAsJsonObject().getAsJsonObject("resolution").add("after", battle.deepCopy());
        assertCode("INVALID_FILE", () -> service.importFile(resign(wrongOrdinal)));
        assertEquals(before, service.status());
    }

    private static byte[] resign(JsonObject file) {
        var integrity = file.remove("integrity").getAsJsonObject();
        file.addProperty("fileKey", id());
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
        file.add("integrity", integrity);
        return CampaignJson.canonical(file);
    }

    private static Opportunity pending(Window window, UUID actor) {
        var choice = window.opportunities().stream().filter(value -> value.status() == OpportunityStatus.PENDING)
                .findFirst().orElseThrow();
        assertEquals(actor, choice.actorId());
        return choice;
    }

    private static StorageException assertCode(String code, Executable call) {
        var failure = assertThrows(StorageException.class, call);
        assertEquals(code, failure.code(), failure.getMessage());
        return failure;
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static JsonObject input(Object... pairs) {
        var input = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) input.addProperty((String) pairs[i], pairs[i + 1].toString());
        return input;
    }

    private static final class Fixture implements AutoCloseable {
        final CampaignStorageService service;
        final ArrayDeque<Integer> dice = new ArrayDeque<>();
        Boundary failure;

        Fixture(Path root) throws Exception {
            service = new CampaignStorageService(root, boundary -> {
                if (boundary == failure) {
                    failure = null;
                    throw new IllegalStateException("injected transaction failure");
                }
            }, sides -> {
                Integer roll = dice.poll();
                assertNotNull(roll, "Unexpected live d" + sides);
                assertTrue(roll >= 1 && roll <= sides, "Invalid scripted d" + sides);
                return roll;
            });
        }

        void create() {
            var members = CampaignRuleFixtures.install(service);
            var preview = service.previewRules(members);
            assertEquals(members, preview.binding().members());
            assertTrue(preview.catalog().stream().anyMatch(rule -> rule.key().equals(FEATURE) && rule.executable()));
            service.create("额外攻击验收", members);
            assertEquals(preview.binding(), state().material().binding());
        }

        CampaignStorageService.AccessSnapshot access() { return service.accessSnapshot(); }
        CampaignDomainData.State state() { return access().snapshot().domains(); }
        Battle battle() { return CampaignSpaceCommands.currentBattle(state()).current(); }
        Combatant actor(UUID id) {
            return battle().combatants().stream().filter(actor -> actor.characterId().equals(id)).findFirst().orElseThrow();
        }
        Schema2CharacterRuntime.History characterHistory(UUID id) {
            return state().characters().stream().filter(card -> card.id().equals(id)).findFirst().orElseThrow().history();
        }
        Schema2CharacterRuntime.State card(UUID id) { return characterHistory(id).current(); }
        RuleIdentity rule(String type, String key) { return state().material().restore().rule(type, key); }
        List<FeatureState> extraAttack(UUID id) {
            return card(id).features().stream().filter(feature -> feature.feature().ruleKey().equals(FEATURE)).toList();
        }
        UUID character(String name, String kind) {
            return subject("character-create", input("name", name, "kind", kind, "classKey", "fighter", "backgroundKey", "acolyte"));
        }
        void advance(UUID fighter, int targetLevel) {
            for (int level = card(fighter).level() + 1; level <= targetLevel; level++) {
                var subclass = level == 3 ? rule("character.subclass", "subclass.champion") : null;
                var abilities = level == 4 || level == 6 ? Map.of(STRENGTH, 2) : Map.<String, Integer>of();
                characterCommand(id(), fighter, new Schema2CharacterRuntime.Command("CHARACTER_ADVANCED", null, 0,
                        null, "FIXED_AVERAGE", rule("character.class", "class.fighter"),
                        new GrowthChoices(subclass, abilities, null, Map.of())));
                assertEquals(level, card(fighter).level());
            }
        }
        UUID equip(UUID fighter) {
            UUID weapon = UUID.randomUUID();
            equipment(weapon.toString(), fighter, "ITEM_ACQUIRED",
                    new EquipmentInput(weapon, rule("equipment.item", "item.dagger"), "CARRIED", "主持人确认取得匕首"));
            equipment(id(), fighter, "ITEM_EQUIPPED", new EquipmentInput(weapon, null, "MAIN_HAND", "主持人确认主手装备"));
            return weapon;
        }
        private void equipment(String request, UUID fighter, String kind, EquipmentInput equipment) {
            characterCommand(request, fighter, new Schema2CharacterRuntime.Command(kind, null, 0, null, null,
                    null, GrowthChoices.EMPTY, null, null, equipment, null));
        }
        private void characterCommand(String request, UUID fighter, Schema2CharacterRuntime.Command command) {
            var input = input("characterId", fighter, "characterVersion", card(fighter).version());
            input.add("command", CampaignDomainCodec.toJson(command));
            CampaignSpaceFixtures.command(service, request, "character-command", input);
        }
        void exerciseCharacterCommands(UUID fighter, UUID weapon) {
            equipment(id(), fighter, "ITEM_UNEQUIPPED", new EquipmentInput(weapon, null, null, "主持人确认卸下匕首"));
            assertEquals("CARRIED", card(fighter).equipment().getFirst().location());
            equipment(id(), fighter, "ITEM_EQUIPPED", new EquipmentInput(weapon, null, "MAIN_HAND", "主持人确认重新装备"));
            assertEquals("MAIN_HAND", card(fighter).equipment().getFirst().location());

            var athletics = new CheckInput(rule("adventure.check", "check.srd2014"), "SKILL", STRENGTH,
                    rule("character.proficiency", "proficiency.skill.athletics"), 12, false, false, "主持人确认运动检定");
            dice.add(14);
            characterCommand(id(), fighter, new Schema2CharacterRuntime.Command("CHECK_RESOLVED", null, 0,
                    null, null, null, GrowthChoices.EMPTY, null, athletics));
            assertTrue(dice.isEmpty());
            var check = characterHistory(fighter).events().getLast().checkResult();
            assertEquals(14, check.selectedD20());
            assertEquals(3, check.proficiencyBonus());
            assertTrue(check.success());

            long hp = card(fighter).hitPoints();
            health(fighter, "vital_effect.dm_damage");
            assertEquals(hp - 6, card(fighter).hitPoints());
            health(fighter, "vital_effect.dm_healing");
            var healing = characterHistory(fighter).events().getLast();
            assertEquals("vital_effect.dm_healing", healing.command().healthInput().effect().ruleKey());
            assertEquals(hp - 6, healing.before().hitPoints());
            assertEquals(hp - 1, healing.after().hitPoints());
            assertEquals(1, extraAttack(fighter).size());
        }
        private void health(UUID fighter, String effect) {
            var health = new HealthInput(rule("adventure.health", "health.srd2014"),
                    rule("adventure.vital_effect", effect), null, "主持人确认直接健康效果");
            characterCommand(id(), fighter, new Schema2CharacterRuntime.Command("HEALTH_EFFECT_RESOLVED", null, 0,
                    null, null, null, GrowthChoices.EMPTY, null, null, null, health));
        }
        UUID player(UUID fighter, UUID... visible) {
            UUID member = subject("member-add", input("name", "战士玩家"));
            CampaignSpaceFixtures.command(service, "control-set", input("characterId", fighter, "memberId", member, "controlVersion", 0));
            for (UUID target : visible) {
                var share = input("kind", "CHARACTER", "subjectId", target, "shareVersion", 0);
                share.add("shareId", JsonNull.INSTANCE);
                share.addProperty("active", true);
                share.add("audience", CampaignDomainCodec.toJson(new Audience(AudienceKind.SPECIFIC, List.of(member))));
                CampaignSpaceFixtures.command(service, "share-set", share);
            }
            return member;
        }
        void startBattle(String name, List<Deployment> placements, Integer... initiative) {
            List<UUID> roster = placements.stream().map(Deployment::characterId).toList();
            UUID world = PlayerSpaceFixture.placeAtWorld(service, roster.toArray(UUID[]::new));
            UUID scene = subject("scene-create", input("name", name, "nodeId", world, "width", 10, "height", 10, "resolution", "RULES"));
            var enter = input("sceneId", scene);
            var subjects = new JsonArray();
            var state = state();
            for (var row : placements) {
                subjects.add(input("id", row.characterId(), "version", CampaignSpaceCommands.position(state, row.characterId()).version(),
                        "x", row.position().x(), "y", row.position().y(), "side", row.side()));
            }
            enter.add("subjects", subjects);
            CampaignSpaceFixtures.command(service, "scene-enter", enter);
            combat(Command.reenter(roster.getFirst(), roster), initiative);
            assertEquals(roster.getFirst(), battle().activeActor());
        }
        void leaveBattle(UUID fighter, UUID... opponents) {
            combat(Command.action(Kind.END_COMBAT, fighter));
            var roster = new ArrayList<>(List.of(opponents));
            roster.addFirst(fighter);
            var leave = new JsonObject();
            var subjects = new JsonArray();
            var state = state();
            for (UUID actor : roster) subjects.add(input("id", actor, "version", CampaignSpaceCommands.position(state, actor).version()));
            leave.add("subjects", subjects);
            CampaignSpaceFixtures.command(service, "scene-leave", leave);
        }
        private UUID subject(String action, JsonObject input) {
            return UUID.fromString(CampaignSpaceFixtures.command(service, action, input).lastCommand().subjectId());
        }
        AttackSequence assertSequence(UUID fighter, int limit, int performed, int forfeited, boolean closed) {
            Combatant actor = actor(fighter);
            assertFalse(actor.actionAvailable());
            AttackSequence sequence = actor.sequence();
            assertNotNull(sequence);
            assertEquals(new AttackSequence(sequence.sequenceKey(), limit, performed, forfeited, closed), sequence);
            return sequence;
        }
        List<AttackAttempt> attempts(AttackSequence sequence) {
            return battle().flows().stream().flatMap(flow -> flow.attacks().stream())
                    .filter(attempt -> sequence.sequenceKey().equals(attempt.sequenceKey())).toList();
        }
        List<PlayerCandidateFactory.Candidate> attackCandidates(UUID member) {
            return PlayerCandidateFactory.candidates(access(), member).stream().filter(candidate -> candidate.action().equals("combat-command"))
                    .filter(candidate -> candidate.input().getAsJsonObject("command").get("kind").getAsString().equals(Kind.ATTACK.name())).toList();
        }
        UUID candidateTarget(PlayerCandidateFactory.Candidate candidate) {
            return UUID.fromString(candidate.input().getAsJsonObject("command").get("targetId").getAsString());
        }
        List<UUID> attackTargets(UUID member) { return attackCandidates(member).stream().map(this::candidateTarget).toList(); }

        void react(Kind kind, UUID reactor, UUID item, Integer... rolls) {
            Flow flow = battle().activeFlow();
            Window window = flow.windows().getLast();
            combat(Command.reaction(kind, reactor, item, flow.flowKey(), flow.leafFrameKey(), window.windowKey(),
                    pending(window, reactor).opportunityKey()), rolls);
        }
        void continueMovement(UUID mover) {
            Flow flow = battle().activeFlow();
            Window window = flow.windows().getLast();
            assertEquals(WindowStatus.CLOSED, window.status());
            combat(Command.continuation(mover, flow.flowKey(), flow.leafFrameKey(), window.windowKey()));
            assertNull(battle().activeFlow());
        }
        void combat(Command command, Integer... rolls) {
            assertTrue(dice.isEmpty());
            dice.addAll(List.of(rolls));
            submit(id(), access(), combatInput(command), null);
            assertTrue(dice.isEmpty(), "Not all scripted dice were drawn");
        }
        void reject(Command command, String reason) {
            var before = access();
            var failure = assertCode("DOMAIN_REJECTED", () -> submit(id(), before, combatInput(command), null));
            assertEquals(reason, failure.getMessage());
            assertEquals(before, access());
        }
        private JsonObject combatInput(Command command) {
            var battle = battle();
            var input = input("combatId", battle.combatId(), "combatVersion", battle.version());
            input.add("command", CampaignDomainCodec.toJson(command));
            return CampaignSpaceFixtures.focused(state(), input);
        }
        CampaignStorageService.CampaignView submit(String request, CampaignStorageService.AccessSnapshot at, JsonObject input, Actor actor) {
            return service.domainCommand(request, at.epoch(), at.revision(), "combat-command", input, actor, ignored -> { });
        }
        String end() {
            var at = access();
            return service.end(id(), at.epoch(), at.revision()).fileKey();
        }
        @Override public void close() throws Exception { service.close(); }
    }
}
