package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.service.Schema2CombatState;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.dndtool.service.storage.CampaignParticipationData.*;

class CampaignParticipationIntegrationTest {
    @TempDir Path temporary;

    @Test void businessParticipationAndTruthfulDmActorSurviveFilesWithoutPrivateSecurity() throws Exception {
        try (var storage = new CampaignStorageService(temporary.resolve("portable"))) {
            CampaignRuleFixtures.create(storage, "共同冒险");
            UUID pc = manual(storage, "甲", "PC"), other = manual(storage, "乙", "PC"), npc = manual(storage, "守卫", "NPC");
            UUID first = member(storage, "同名"), second = member(storage, "同名"); assertNotEquals(first, second);
            control(storage, pc, first, 0); control(storage, other, first, 0);
            assertEquals(2, storage.accessSnapshot().snapshot().domains().participation().controls().size());
            var prior = storage.accessSnapshot();
            assertEquals("CONTROL_PC_REQUIRED", assertThrows(StorageException.class, () -> control(storage, npc, second, 0)).code());
            assertEquals(prior.snapshot(), storage.accessSnapshot().snapshot());
            JsonObject rename = object("characterId", pc.toString(), "characterVersion", "0", "fieldKey", "ability.strength", "value", "13");
            send(storage, "manual-field", rename);
            var acting = storage.accessSnapshot().snapshot().domains().roots().getLast().actor();
            assertEquals(Actor.dm(pc, first), acting);
            JsonObject share = object("shareVersion", "0", "kind", "LOG", "subjectId", storage.accessSnapshot().snapshot().campaignId());
            share.add("shareId", JsonNull.INSTANCE); share.add("audience", CampaignDomainCodec.toJson(new Audience(AudienceKind.ALL, List.of()))); share.addProperty("active", true); send(storage, "share-set", share);
            JsonObject security = new JsonObject(); security.addProperty("paused", true); security.addProperty("secret", "private-device-secret"); storage.updateLocalPlayerControl(security);
            var complete = storage.accessSnapshot().snapshot(); var current = storage.current();
            String file = storage.end(uuid(), current.epoch(), current.revision()).fileKey();
            String bytes = Files.readString(storage.savedFile(file)); assertFalse(bytes.contains("private-device-secret")); assertFalse(bytes.contains("localControl"));
            storage.open(file); assertEquals(complete, storage.accessSnapshot().snapshot()); assertEquals(new JsonObject(), storage.localPlayerControl());
            assertEquals(Actor.dm(pc, first), storage.accessSnapshot().snapshot().domains().roots().stream().filter(r -> r.kind().equals("manual-field")).findFirst().orElseThrow().actor());
        }
    }

    @Test void participationMutationsRollbackWithTheirAuditAndRejectStaleMapping() throws Exception {
        boolean[] fail = {false};
        try (var storage = new CampaignStorageService(temporary.resolve("rollback"), b -> { if (fail[0] && b == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE) throw new IllegalStateException("injected"); })) {
            CampaignRuleFixtures.create(storage, "原子归属"); UUID pc = manual(storage, "甲", "PC"), first = member(storage, "甲"), second = member(storage, "乙"); control(storage, pc, first, 0);
            var before = storage.accessSnapshot(); fail[0] = true; assertThrows(IllegalStateException.class, () -> control(storage, pc, second, 1)); fail[0] = false;
            assertEquals(before.snapshot(), storage.accessSnapshot().snapshot()); assertEquals(before.revision(), storage.accessSnapshot().revision());
            assertEquals("STALE_PARTICIPATION", assertThrows(StorageException.class, () -> control(storage, pc, second, 0)).code());
            control(storage, pc, second, 1); assertEquals(second, storage.accessSnapshot().snapshot().domains().participation().controller(pc));
        }
    }

    @Test void guardPrecedesReplayAndReceiptLookupIsReadOnlyAndBindsExactActor() throws Exception {
        try (var storage = new CampaignStorageService(temporary.resolve("guard"))) {
            CampaignRuleFixtures.create(storage, "串行授权");
            send(storage, "character-create", object("name", "战士", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
            UUID pc = storage.accessSnapshot().snapshot().domains().characters().getFirst().id(), member = member(storage, "玩家"); control(storage, pc, member, 0);
            send(storage, "node-create", object("name", "营地"));
            UUID world = storage.accessSnapshot().snapshot().domains().roots().getLast().subjectId();
            send(storage, "focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(world)));
            send(storage, "position", object("characterId", pc.toString(), "nodeId", world.toString()));
            send(storage, "exploration-set", permission(pc, world, 0));
            send(storage, "character-command", characterInput(storage.accessSnapshot().snapshot().domains(), pc, acquire()));
            var state = storage.accessSnapshot().snapshot().domains();
            var command = CampaignSpaceFixtures.focused(state, characterInput(state, pc, equipCommand(state.roots().getLast().operationId())));
            var before = storage.accessSnapshot(); String request = uuid(); Actor actor = Actor.player(member, pc);
            storage.domainCommand(request, before.epoch(), before.revision(), "character-command", command, actor, access -> assertEquals(before.snapshot(), access.snapshot()));
            var after = storage.accessSnapshot();
            assertEquals("PLAYER_PAUSED", assertThrows(StorageException.class, () -> storage.domainCommand(request, before.epoch(), before.revision(), "character-command", command, actor, access -> { throw new StorageException("PLAYER_PAUSED", "paused"); })).code());
            var receipt = storage.exactDomainReceipt(request, before.runId(), before.revision(), "character-command", command, actor, access -> { });
            assertEquals(actor, receipt.orElseThrow().actor()); assertEquals(after.snapshot(), storage.accessSnapshot().snapshot());
            assertTrue(storage.exactDomainReceipt(uuid(), before.runId(), before.revision(), "character-command", command, actor, access -> { }).isEmpty());
            assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(StorageException.class, () -> storage.exactDomainReceipt(request, before.runId(), before.revision(), "character-command", command, Actor.dm(pc, member), access -> { })).code());
            assertEquals(after.snapshot(), storage.accessSnapshot().snapshot());
        }
    }

    @Test void historicalActorUsesControlAtThatRootAndTamperingCannotClaimNewController() {
        Fixture f = new Fixture(); UUID pc = f.rich("战士"); UUID first = f.member("甲"), second = f.member("乙"); f.control(pc, first, 0);
        f.place(f.world("营地"), pc);
        f.grant(pc);
        f.apply("character-command", characterInput(f.snapshot.domains(), pc, acquire()));
        f.apply("character-command", characterInput(f.snapshot.domains(), pc, equipCommand(f.snapshot.domains().roots().getLast().operationId())),
                Actor.player(first, pc));
        var original = f.snapshot.domains().roots().getLast(); f.control(pc, second, 1); assertEquals(first, original.actor().memberId());
        var state = f.snapshot.domains(); List<CampaignDomainData.Root> roots = new ArrayList<>(state.roots()); int index = roots.indexOf(original);
        roots.set(index, new CampaignDomainData.Root(original.sequence(), original.operationId(), original.kind(), original.subjectId(), original.intent(), original.before(), original.after(), original.characterOperations(), original.combatId(), Actor.player(second, pc), original.space(), original.rolls()));
        var damaged = new CampaignDomainData.State(state.material(), state.characters(), state.combats(), state.nodes(), state.positions(), state.items(), roots, state.manual(), state.participation(), state.space());
        assertThrows(StorageException.class, () -> CampaignData.validate(snapshot(f.snapshot.campaignId(), damaged)));
    }

    @Test void sceneTransitionsAndControlChangesInvalidateExplorationAndPendingTransferIsRefused() {
        Fixture f = new Fixture(); UUID first = f.rich("甲"), second = f.rich("乙"), player = f.member("玩家"); f.equip(first); f.equip(second); f.control(first, player, 0);
        f.apply("manual-create", object("name", "守卫", "kind", "NPC", "template", "guard"));
        UUID free = f.snapshot.domains().manual().characters().getFirst().id();
        UUID world = f.world("营地");
        f.place(world, first, second, free);
        f.grant(first);
        assertTrue(f.snapshot.domains().participation().exploration().getFirst().active());
        f.control(first, player, 1); assertFalse(f.snapshot.domains().participation().exploration().getFirst().active()); f.grant(first);
        f.enterRuleScene(world, first, second, free);
        assertFalse(f.snapshot.domains().participation().exploration().getFirst().active());
        f.grant(first);
        f.combat(Schema2CombatState.Command.reenter(first, List.of(first, second)));
        assertFalse(f.snapshot.domains().participation().exploration().getFirst().active());
        // The pure transition sees the true active leaf, and refuses even a mapping no-op while it is unresolved.
        var battle = f.snapshot.domains().combats().getFirst().current();
        assertTrue(battle.combatants().stream().noneMatch(c -> c.characterId().equals(free)));
        assertEquals(battle.combatId(), CampaignSpaceCommands.position(f.snapshot.domains(), free).sceneId());
        JsonObject field = object("characterId", free.toString(), "characterVersion", "0", "fieldKey", "hp.current", "value", "10");
        f.apply("manual-field", field);
        UUID mover = battle.activeActor();
        f.combat(Schema2CombatState.Command.move(mover, List.of(new Schema2CombatState.Position(mover.equals(first) ? 0 : 3, 0))));
        assertNotNull(f.snapshot.domains().combats().getFirst().current().activeFlow());
        assertEquals("CONTROL_FLOW_PENDING", assertThrows(StorageException.class, () -> f.control(first, player, 2)).code());
        field.addProperty("characterVersion", "1");
        assertEquals("ACTION_FLOW_BUSY", assertThrows(StorageException.class, () -> f.apply("manual-field", field)).code());
        CampaignData.validate(f.snapshot);
    }

    @Test void readyMovementUsesTheUnifiedGridAtDeclarationAndRelease() {
        Fixture f = new Fixture();
        UUID first = f.rich("甲"), second = f.rich("乙");
        f.apply("manual-create", object("name", "守卫", "kind", "NPC", "template", "guard"));
        UUID free = f.snapshot.domains().manual().characters().getFirst().id();
        UUID world = f.world("营地");
        f.place(world, first, second, free);
        f.enterRuleScene(world, first, second, free);
        f.combat(Schema2CombatState.Command.reenter(first, List.of(first, second)));
        assertEquals(first, CampaignSpaceCommands.currentBattle(f.snapshot.domains()).current().activeActor());

        var trigger = new Schema2CombatState.ReadyTrigger(Schema2CombatState.TriggerKind.CREATURE_ENTER_CELL_AFTER,
                second, new Schema2CombatState.Position(3, 0));
        var outside = readyMove(first, trigger, List.of(new Schema2CombatState.Position(0, 0), new Schema2CombatState.Position(-1, 0)));
        f.rejectCombat("SCENE_GRID_BOUNDS", outside);
        var occupied = readyMove(first, trigger, List.of(new Schema2CombatState.Position(2, 1),
                new Schema2CombatState.Position(3, 1), new Schema2CombatState.Position(4, 0)));
        f.rejectCombat("SCENE_CELL_OCCUPIED", occupied);

        f.combat(readyMove(first, trigger, List.of(new Schema2CombatState.Position(2, 1))));
        JsonObject manualMove = object("characterId", free.toString(), "version", "1", "minutes", "0", "adjudication", "DM裁定守卫移动。");
        JsonArray path = new JsonArray();
        path.add(object("x", "3", "y", "1"));
        path.add(object("x", "2", "y", "1"));
        manualMove.add("path", path);
        f.apply("space-move", manualMove);
        f.combat(Schema2CombatState.Command.action(Schema2CombatState.Kind.END_TURN, first));
        f.combat(Schema2CombatState.Command.move(second, List.of(trigger.position())));
        var flow = CampaignSpaceCommands.currentBattle(f.snapshot.domains()).current().activeFlow();
        assertNotNull(flow);
        var window = flow.windows().getLast();
        var opportunity = window.opportunities().stream()
                .filter(o -> o.status() == Schema2CombatState.OpportunityStatus.PENDING).findFirst().orElseThrow();
        var option = opportunity.options().stream().filter(o -> o.kind() == Schema2CombatState.ReactionKind.READY_RELEASE).findFirst().orElseThrow();
        var release = Schema2CombatState.Command.releaseReady(first, option.readyKey(), flow.flowKey(), flow.leafFrameKey(),
                window.windowKey(), opportunity.opportunityKey());
        f.rejectCombat("SCENE_CELL_OCCUPIED", release);
    }

    private static Schema2CombatState.Command readyMove(UUID actor, Schema2CombatState.ReadyTrigger trigger,
            List<Schema2CombatState.Position> path) {
        return Schema2CombatState.Command.ready(actor, new Schema2CombatState.ReadyInput(trigger,
                Schema2CombatState.ReadyResponseKind.MOVE, null, null, path, null));
    }

    @Test void explicitTypedAdjudicationKeepsProposalDecisionAndOriginalResultTogether() {
        Fixture f = new Fixture(); UUID pc = f.rich("战士"), player = f.member("玩家"), other = f.member("另一人"); f.control(pc, player, 0);
        f.place(f.world("城墙"), pc);
        JsonObject check = f.check(pc);
        for (String side : List.of("success", "failure")) {
            JsonObject branch = object("targetId", pc.toString(), "itemDelta", "0", "message", side.equals("success") ? "攀爬成功" : "攀爬未成功");
            branch.add("healthEffect", JsonNull.INSTANCE); branch.add("itemId", JsonNull.INSTANCE); check.add(side, branch);
        }
        JsonObject decision = object("proposalId", uuid(), "memberId", player.toString(), "proposalText", "我想攀上墙头", "reason", "固定检定。", "type", "ABILITY_CHECK"); decision.add("check", check);
        var before = f.snapshot; JsonObject wrong = decision.deepCopy(); wrong.addProperty("memberId", other.toString()); int[] draws = {0};
        assertEquals("ADJUDICATION_CONTROL", assertThrows(StorageException.class, () -> new CampaignDomainCommands().apply(before, "adjudicated-check", CampaignSpaceFixtures.focused(before.domains(), wrong), UUID.randomUUID(), "0".repeat(64), sides -> { draws[0]++; return 10; })).code()); assertEquals(0, draws[0]);
        f.apply("adjudicated-check", decision); var root = f.snapshot.domains().roots().getLast();
        assertEquals(Actor.dm(pc, player), root.actor());
        var audit = CampaignDomainCodec.decode(root.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), CampaignAdjudicationData.Audit.class);
        assertEquals("我想攀上墙头", audit.decision().proposalText()); assertEquals(CampaignAdjudicationData.Type.ABILITY_CHECK, audit.decision().adjudicationType());
        assertTrue(audit.result().success()); assertEquals(1, root.characterOperations().size());
        JsonObject refreshedCheck = f.check(pc); refreshedCheck.add("success", check.get("success").deepCopy()); refreshedCheck.add("failure", check.get("failure").deepCopy());
        JsonObject duplicate = decision.deepCopy(); duplicate.add("check", refreshedCheck); var acceptedState = f.snapshot;
        assertEquals("PROPOSAL_ALREADY_DECIDED", assertThrows(StorageException.class, () -> new CampaignDomainCommands().apply(acceptedState, "adjudicated-check", CampaignSpaceFixtures.focused(acceptedState.domains(), duplicate), UUID.randomUUID(), "0".repeat(64), sides -> { draws[0]++; return 10; })).code());
        assertEquals(0, draws[0]);
        JsonObject another = duplicate.deepCopy(); another.addProperty("proposalId", uuid()); f.apply("adjudicated-check", another);
        var last = f.snapshot.domains().roots().getLast(); var secondAudit = CampaignDomainCodec.decode(last.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), CampaignAdjudicationData.Audit.class);
        var d = secondAudit.decision(); var repeatedDecision = new CampaignAdjudicationData.Decision(audit.decision().proposalId(), d.memberId(), d.proposalText(), d.reason(), d.adjudicationType(), d.check());
        var forgedAudit = new CampaignAdjudicationData.Audit(repeatedDecision, secondAudit.result());
        var state = f.snapshot.domains(); List<CampaignDomainData.Root> roots = new ArrayList<>(state.roots());
        roots.set(roots.size() - 1, new CampaignDomainData.Root(last.sequence(), last.operationId(), last.kind(), last.subjectId(), last.intent(), last.before(), CampaignDomainCodec.text(forgedAudit), last.characterOperations(), last.combatId(), last.actor(), last.space(), last.rolls()));
        var invalid = new CampaignDomainData.State(state.material(), state.characters(), state.combats(), state.nodes(), state.positions(), state.items(), roots, state.manual(), state.participation(), state.space());
        assertThrows(StorageException.class, () -> CampaignData.validate(snapshot(f.snapshot.campaignId(), invalid)));
        f.control(pc, other, 1);
        var codec = new CampaignCodec(); var document = new CampaignData.Document(uuid(), f.snapshot); assertEquals(document, codec.decode(codec.encode(document)));
        JsonObject stale = decision.deepCopy(); stale.addProperty("memberId", other.toString()); stale.addProperty("proposalId", uuid());
        var beforeStale = f.snapshot;
        assertEquals("STALE_CHARACTER", assertThrows(StorageException.class, () -> new CampaignDomainCommands().apply(beforeStale, "adjudicated-check", CampaignSpaceFixtures.focused(beforeStale.domains(), stale), UUID.randomUUID(), "0".repeat(64), sides -> { draws[0]++; return 10; })).code());
        assertEquals(0, draws[0]); assertEquals(beforeStale, f.snapshot);
    }

    private static final class Fixture {
        CampaignData.Snapshot snapshot = snapshot(uuid(), CampaignDomainData.State.initial(BundledCampaignRules.projection()));
        void apply(String action, JsonObject input) { apply(action, input, null); }
        void apply(String action, JsonObject input, Actor actor) {
            var commands = new CampaignDomainCommands();
            JsonObject payload = CampaignSpaceFixtures.focused(snapshot.domains(), input);
            UUID operation = UUID.randomUUID();
            var next = actor == null ? commands.apply(snapshot, action, payload, operation, "0".repeat(64), sides -> 10)
                    : commands.apply(snapshot, action, payload, operation, "0".repeat(64), sides -> 10, actor);
            snapshot = CampaignData.validate(snapshot(snapshot.campaignId(), next));
        }
        UUID rich(String name) { apply("character-create", object("name", name, "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte")); return snapshot.domains().characters().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id(); }
        UUID member(String name) { apply("member-add", object("name", name)); return snapshot.domains().roots().getLast().subjectId(); }
        void control(UUID pc, UUID member, long version) { apply("control-set", object("characterId", pc.toString(), "memberId", member.toString(), "controlVersion", Long.toString(version))); }
        JsonObject check(UUID pc) { var c = snapshot.domains().characters().stream().filter(v -> v.id().equals(pc)).findFirst().orElseThrow(); JsonObject p = object("characterId", pc.toString(), "characterVersion", Long.toString(c.history().current().version())); p.add("command", CampaignDomainCodec.toJson(new Schema2CharacterRuntime.Command("CHECK_RESOLVED", null, 0, null, null, null, Schema2CharacterRuntime.GrowthChoices.EMPTY, null, new Schema2CharacterRuntime.CheckInput(CampaignRuleFixtures.rules().rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, 10, false, false, "固定检定。")))); return p; }
        void grant(UUID pc) {
            var state = snapshot.domains();
            long version = state.participation().exploration().stream().filter(e -> e.characterId().equals(pc))
                    .mapToLong(Exploration::version).findFirst().orElse(0);
            apply("exploration-set", permission(pc, CampaignSpaceCommands.position(state, pc).space().id(), version));
        }
        void equip(UUID pc) {
            apply("character-command", characterInput(snapshot.domains(), pc, acquire()));
            var state = snapshot.domains();
            apply("character-command", characterInput(state, pc, equipCommand(state.roots().getLast().operationId())));
        }
        UUID world(String name) {
            apply("node-create", object("name", name));
            UUID world = snapshot.domains().roots().getLast().subjectId();
            apply("focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(world)));
            return world;
        }
        void place(UUID world, UUID... subjects) {
            for (UUID subject : subjects) apply("position", object("characterId", subject.toString(), "nodeId", world.toString()));
        }
        void enterRuleScene(UUID world, UUID first, UUID second, UUID free) {
            apply("scene-create", object("name", "局部战场", "nodeId", world.toString(), "width", "5", "height", "5", "resolution", "RULES"));
            UUID scene = snapshot.domains().roots().getLast().subjectId();
            JsonObject input = object("sceneId", scene.toString());
            JsonArray subjects = new JsonArray();
            subjects.add(object("id", first.toString(), "version", "0", "x", "1", "y", "0", "side", "0"));
            subjects.add(object("id", second.toString(), "version", "0", "x", "2", "y", "0", "side", "1"));
            subjects.add(object("id", free.toString(), "version", "0", "x", "4", "y", "0", "side", "2"));
            input.add("subjects", subjects);
            apply("scene-enter", input);
        }
        void combat(Schema2CombatState.Command command) {
            apply("combat-command", combatInput(command));
        }
        void rejectCombat(String code, Schema2CombatState.Command command) {
            var before = snapshot;
            JsonObject input = CampaignSpaceFixtures.focused(before.domains(), combatInput(command));
            assertEquals(code, assertThrows(StorageException.class,
                    () -> new CampaignDomainCommands().apply(before, "combat-command", input, UUID.randomUUID(), "0".repeat(64),
                            sides -> { throw new AssertionError("Invalid grid route reached RNG"); })).code());
            assertEquals(before, snapshot);
        }
        private JsonObject combatInput(Schema2CombatState.Command command) {
            var battle = CampaignSpaceCommands.currentBattle(snapshot.domains()).current();
            JsonObject input = object("combatId", battle.combatId().toString(), "combatVersion", Long.toString(battle.version()));
            input.add("command", CampaignDomainCodec.toJson(command));
            return input;
        }
    }
    private static Schema2CharacterRuntime.Command acquire() {
        return new Schema2CharacterRuntime.Command("ITEM_ACQUIRED", null, 0, null, null, null,
                Schema2CharacterRuntime.GrowthChoices.EMPTY, null, null,
                new Schema2CharacterRuntime.EquipmentInput(UUID.randomUUID(), CampaignRuleFixtures.rules().rule("equipment.item", "item.longsword"),
                        "CARRIED", "确认物品。"), null);
    }
    private static Schema2CharacterRuntime.Command equipCommand(UUID item) {
        return new Schema2CharacterRuntime.Command("ITEM_EQUIPPED", null, 0, null, null, null,
                Schema2CharacterRuntime.GrowthChoices.EMPTY, null, null,
                new Schema2CharacterRuntime.EquipmentInput(item, null, "MAIN_HAND", "确认装备。"), null);
    }
    private static JsonObject characterInput(CampaignDomainData.State state, UUID pc, Schema2CharacterRuntime.Command command) {
        long version = state.characters().stream().filter(c -> c.id().equals(pc)).findFirst().orElseThrow().history().current().version();
        JsonObject input = object("characterId", pc.toString(), "characterVersion", Long.toString(version));
        input.add("command", CampaignDomainCodec.toJson(command));
        return input;
    }
    private static JsonObject permission(UUID pc, UUID space, long version) {
        JsonObject input = object("characterId", pc.toString(), "permissionVersion", Long.toString(version), "sceneId", space.toString());
        JsonArray actions = new JsonArray();
        actions.add("CHARACTER_COMMAND");
        input.add("actions", actions);
        input.addProperty("active", true);
        return input;
    }
    private static CampaignData.Snapshot snapshot(String id, CampaignDomainData.State state) { return new CampaignData.Snapshot(id, "玩家参与", state); }
    private static UUID manual(CampaignStorageService s, String name, String kind) { send(s, "manual-create", object("name", name, "kind", kind, "template", "blank")); return s.accessSnapshot().snapshot().domains().manual().characters().stream().filter(c -> c.current().name().equals(name)).findFirst().orElseThrow().id(); }
    private static UUID member(CampaignStorageService s, String name) { send(s, "member-add", object("name", name)); return s.accessSnapshot().snapshot().domains().roots().getLast().subjectId(); }
    private static void control(CampaignStorageService s, UUID pc, UUID member, long version) { send(s, "control-set", object("characterId", pc.toString(), "memberId", member.toString(), "controlVersion", Long.toString(version))); }
    private static void send(CampaignStorageService s, String action, JsonObject p) {
        var a = s.accessSnapshot();
        s.domainCommand(uuid(), a.epoch(), a.revision(), action, CampaignSpaceFixtures.focused(a.snapshot().domains(), p));
    }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static JsonObject object(String... pairs) { JsonObject p = new JsonObject(); for (int i = 0; i < pairs.length; i += 2) p.addProperty(pairs[i], pairs[i + 1]); return p; }
}
