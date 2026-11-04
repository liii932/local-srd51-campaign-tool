package com.dndtool.service.storage;

import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
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
    private static final List<String> FULL = List.of(CampaignRules.BASE, CampaignRules.HEALING, CampaignRules.RUNTIME);

    @Test void businessParticipationAndTruthfulDmActorSurviveFilesWithoutPrivateSecurity() throws Exception {
        try (var storage = new CampaignStorageService(temporary.resolve("portable"))) {
            storage.create("共同冒险", FULL);
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
            storage.create("原子归属", FULL); UUID pc = manual(storage, "甲", "PC"), first = member(storage, "甲"), second = member(storage, "乙"); control(storage, pc, first, 0);
            var before = storage.accessSnapshot(); fail[0] = true; assertThrows(IllegalStateException.class, () -> control(storage, pc, second, 1)); fail[0] = false;
            assertEquals(before.snapshot(), storage.accessSnapshot().snapshot()); assertEquals(before.revision(), storage.accessSnapshot().revision());
            assertEquals("STALE_PARTICIPATION", assertThrows(StorageException.class, () -> control(storage, pc, second, 0)).code());
            control(storage, pc, second, 1); assertEquals(second, storage.accessSnapshot().snapshot().domains().participation().controller(pc));
        }
    }

    @Test void guardPrecedesReplayAndReceiptLookupIsReadOnlyAndBindsExactActor() throws Exception {
        try (var storage = new CampaignStorageService(temporary.resolve("guard"))) {
            storage.create("串行授权", FULL);
            send(storage, "character-create", object("name", "战士", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
            UUID pc = storage.accessSnapshot().snapshot().domains().characters().getFirst().id(), member = member(storage, "玩家"); control(storage, pc, member, 0);
            JsonObject command = object("characterId", pc.toString(), "characterVersion", Long.toString(storage.accessSnapshot().snapshot().domains().characters().getFirst().history().current().version()));
            var check = new JdbcSchema2CharacterRuntimeRepository.Command("CHECK_RESOLVED", null, 0, null, null, null, JdbcSchema2CharacterRuntimeRepository.GrowthChoices.EMPTY, null,
                    new JdbcSchema2CharacterRuntimeRepository.CheckInput(BundledCampaignRules.rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, 10, false, false, "固定检定。"));
            command.add("command", CampaignDomainCodec.toJson(check));
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
        f.apply("character-command", f.check(pc), Actor.player(first, pc));
        var original = f.snapshot.domains().roots().getLast(); f.control(pc, second, 1); assertEquals(first, original.actor().memberId());
        var state = f.snapshot.domains(); List<CampaignDomainData.Root> roots = new ArrayList<>(state.roots()); int index = roots.indexOf(original);
        roots.set(index, new CampaignDomainData.Root(original.sequence(), original.operationId(), original.kind(), original.subjectId(), original.intent(), original.before(), original.after(), original.characterOperations(), original.combatId(), Actor.player(second, pc)));
        var damaged = new CampaignDomainData.State(state.material(), state.characters(), state.combats(), state.nodes(), state.positions(), state.items(), roots, state.manual(), state.participation());
        assertThrows(StorageException.class, () -> CampaignData.validate(snapshot(f.snapshot.campaignId(), damaged)));
    }

    @Test void sceneTransitionsAndControlChangesInvalidateExplorationAndPendingTransferIsRefused() {
        Fixture f = new Fixture(); UUID first = f.rich("甲"), second = f.rich("乙"), player = f.member("玩家"); f.equip(first); f.equip(second); f.control(first, player, 0); f.grant(first);
        assertTrue(f.snapshot.domains().participation().exploration().getFirst().active());
        f.control(first, player, 1); assertFalse(f.snapshot.domains().participation().exploration().getFirst().active()); f.grant(first);
        f.combat(Schema2CombatState.Command.start(List.of(new Schema2CombatState.Deployment(first, new Schema2CombatState.Position(0, 0), 0), new Schema2CombatState.Deployment(second, new Schema2CombatState.Position(1, 0), 1)), List.of(first, second)));
        assertFalse(f.snapshot.domains().participation().exploration().getFirst().active());
        // The pure transition sees the true active leaf, and refuses even a mapping no-op while it is unresolved.
        var battle = f.snapshot.domains().combats().getFirst().current();
        UUID mover = battle.activeActor();
        f.combat(Schema2CombatState.Command.move(mover, List.of(new Schema2CombatState.Position(mover.equals(first) ? -1 : 2, 0))));
        assertNotNull(f.snapshot.domains().combats().getFirst().current().activeFlow());
        assertEquals("CONTROL_FLOW_PENDING", assertThrows(StorageException.class, () -> f.control(first, player, 2)).code());
        CampaignData.validate(f.snapshot);
    }

    @Test void explicitTypedAdjudicationKeepsProposalDecisionAndOriginalResultTogether() {
        Fixture f = new Fixture(); UUID pc = f.rich("战士"), player = f.member("玩家"), other = f.member("另一人"); f.control(pc, player, 0);
        JsonObject check = f.check(pc);
        for (String side : List.of("success", "failure")) {
            JsonObject branch = object("targetId", pc.toString(), "itemDelta", "0", "message", side.equals("success") ? "攀爬成功" : "攀爬未成功");
            branch.add("healthEffect", JsonNull.INSTANCE); branch.add("itemId", JsonNull.INSTANCE); branch.add("nodeId", JsonNull.INSTANCE); check.add(side, branch);
        }
        JsonObject decision = object("proposalId", uuid(), "memberId", player.toString(), "proposalText", "我想攀上墙头", "reason", "固定检定。", "type", "ABILITY_CHECK"); decision.add("check", check);
        var before = f.snapshot; JsonObject wrong = decision.deepCopy(); wrong.addProperty("memberId", other.toString()); int[] draws = {0};
        assertEquals("ADJUDICATION_CONTROL", assertThrows(StorageException.class, () -> new CampaignDomainCommands().apply(before, "adjudicated-check", wrong, UUID.randomUUID(), "0".repeat(64), sides -> { draws[0]++; return 10; })).code()); assertEquals(0, draws[0]);
        f.apply("adjudicated-check", decision); var root = f.snapshot.domains().roots().getLast();
        assertEquals(Actor.dm(pc, player), root.actor());
        var audit = CampaignDomainCodec.decode(root.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), CampaignAdjudicationData.Audit.class);
        assertEquals("我想攀上墙头", audit.decision().proposalText()); assertEquals(CampaignAdjudicationData.Type.ABILITY_CHECK, audit.decision().adjudicationType());
        assertTrue(audit.result().success()); assertEquals(1, root.characterOperations().size());
        JsonObject refreshedCheck = f.check(pc); refreshedCheck.add("success", check.get("success").deepCopy()); refreshedCheck.add("failure", check.get("failure").deepCopy());
        JsonObject duplicate = decision.deepCopy(); duplicate.add("check", refreshedCheck); var acceptedState = f.snapshot;
        assertEquals("PROPOSAL_ALREADY_DECIDED", assertThrows(StorageException.class, () -> new CampaignDomainCommands().apply(acceptedState, "adjudicated-check", duplicate, UUID.randomUUID(), "0".repeat(64), sides -> { draws[0]++; return 10; })).code());
        assertEquals(0, draws[0]);
        JsonObject another = duplicate.deepCopy(); another.addProperty("proposalId", uuid()); f.apply("adjudicated-check", another);
        var last = f.snapshot.domains().roots().getLast(); var secondAudit = CampaignDomainCodec.decode(last.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), CampaignAdjudicationData.Audit.class);
        var d = secondAudit.decision(); var repeatedDecision = new CampaignAdjudicationData.Decision(audit.decision().proposalId(), d.memberId(), d.proposalText(), d.reason(), d.adjudicationType(), d.check());
        var forgedAudit = new CampaignAdjudicationData.Audit(repeatedDecision, secondAudit.result());
        var state = f.snapshot.domains(); List<CampaignDomainData.Root> roots = new ArrayList<>(state.roots());
        roots.set(roots.size() - 1, new CampaignDomainData.Root(last.sequence(), last.operationId(), last.kind(), last.subjectId(), last.intent(), last.before(), CampaignDomainCodec.text(forgedAudit), last.characterOperations(), last.combatId(), last.actor()));
        var invalid = new CampaignDomainData.State(state.material(), state.characters(), state.combats(), state.nodes(), state.positions(), state.items(), roots, state.manual(), state.participation());
        assertEquals("PROPOSAL_ALREADY_DECIDED", assertThrows(StorageException.class, () -> CampaignData.validate(snapshot(f.snapshot.campaignId(), invalid))).code());
        f.control(pc, other, 1);
        var codec = new CampaignCodec(); var document = new CampaignData.Document(uuid(), f.snapshot); assertEquals(document, codec.decode(codec.encode(document)));
        JsonObject stale = decision.deepCopy(); stale.addProperty("memberId", other.toString()); stale.addProperty("proposalId", uuid());
        var beforeStale = f.snapshot;
        assertEquals("STALE_CHARACTER", assertThrows(StorageException.class, () -> new CampaignDomainCommands().apply(beforeStale, "adjudicated-check", stale, UUID.randomUUID(), "0".repeat(64), sides -> { draws[0]++; return 10; })).code());
        assertEquals(0, draws[0]); assertEquals(beforeStale, f.snapshot);
    }

    private static final class Fixture {
        CampaignData.Snapshot snapshot = snapshot(uuid(), CampaignDomainData.State.initial(BundledCampaignRules.projection()));
        void apply(String action, JsonObject input) { apply(action, input, null); }
        void apply(String action, JsonObject input, Actor actor) { var next = actor == null ? new CampaignDomainCommands().apply(snapshot, action, input, UUID.randomUUID(), "0".repeat(64), sides -> 10) : new CampaignDomainCommands().apply(snapshot, action, input, UUID.randomUUID(), "0".repeat(64), sides -> 10, actor); snapshot = CampaignData.validate(snapshot(snapshot.campaignId(), next)); }
        UUID rich(String name) { apply("character-create", object("name", name, "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte")); return snapshot.domains().characters().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id(); }
        UUID member(String name) { apply("member-add", object("name", name)); return snapshot.domains().roots().getLast().subjectId(); }
        void control(UUID pc, UUID member, long version) { apply("control-set", object("characterId", pc.toString(), "memberId", member.toString(), "controlVersion", Long.toString(version))); }
        JsonObject check(UUID pc) { var c = snapshot.domains().characters().stream().filter(v -> v.id().equals(pc)).findFirst().orElseThrow(); JsonObject p = object("characterId", pc.toString(), "characterVersion", Long.toString(c.history().current().version())); p.add("command", CampaignDomainCodec.toJson(new JdbcSchema2CharacterRuntimeRepository.Command("CHECK_RESOLVED", null, 0, null, null, null, JdbcSchema2CharacterRuntimeRepository.GrowthChoices.EMPTY, null, new JdbcSchema2CharacterRuntimeRepository.CheckInput(BundledCampaignRules.rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, 10, false, false, "固定检定。")))); return p; }
        void grant(UUID pc) { long version = snapshot.domains().participation().exploration().stream().filter(e -> e.characterId().equals(pc)).mapToLong(Exploration::version).findFirst().orElse(0); JsonObject p = object("characterId", pc.toString(), "permissionVersion", Long.toString(version)); p.add("sceneId", JsonNull.INSTANCE); p.add("actions", new JsonArray()); p.getAsJsonArray("actions").add("CHARACTER_COMMAND"); p.addProperty("active", true); apply("exploration-set", p); }
        void equip(UUID pc) {
            UUID operation = UUID.randomUUID();
            var acquired = new JdbcSchema2CharacterRuntimeRepository.Command("ITEM_ACQUIRED", null, 0, null, null, null, JdbcSchema2CharacterRuntimeRepository.GrowthChoices.EMPTY, null, null,
                    new JdbcSchema2CharacterRuntimeRepository.EquipmentInput(operation, BundledCampaignRules.rule("equipment.item", "item.longsword"), "CARRIED", "确认物品。"), null);
            var card = snapshot.domains().characters().stream().filter(c -> c.id().equals(pc)).findFirst().orElseThrow();
            JsonObject input = object("characterId", pc.toString(), "characterVersion", Long.toString(card.history().current().version())); input.add("command", CampaignDomainCodec.toJson(acquired));
            var next = new CampaignDomainCommands().apply(snapshot, "character-command", input, operation, "0".repeat(64), sides -> 10); snapshot = CampaignData.validate(snapshot(snapshot.campaignId(), next));
            var equipped = new JdbcSchema2CharacterRuntimeRepository.Command("ITEM_EQUIPPED", null, 0, null, null, null, JdbcSchema2CharacterRuntimeRepository.GrowthChoices.EMPTY, null, null,
                    new JdbcSchema2CharacterRuntimeRepository.EquipmentInput(operation, null, "MAIN_HAND", "确认装备。"), null);
            card = snapshot.domains().characters().stream().filter(c -> c.id().equals(pc)).findFirst().orElseThrow(); input = object("characterId", pc.toString(), "characterVersion", Long.toString(card.history().current().version())); input.add("command", CampaignDomainCodec.toJson(equipped)); apply("character-command", input);
        }
        void combat(Schema2CombatState.Command command) { var battle = snapshot.domains().combats().isEmpty() ? null : snapshot.domains().combats().getFirst().current(); JsonObject p = object("combatVersion", battle == null ? "-1" : Long.toString(battle.version())); p.add("combatId", battle == null ? JsonNull.INSTANCE : new JsonPrimitive(battle.combatId().toString())); p.add("command", CampaignDomainCodec.toJson(command)); apply("combat-command", p); }
    }
    private static CampaignData.Snapshot snapshot(String id, CampaignDomainData.State state) { return new CampaignData.Snapshot(id, "玩家参与", FULL, List.of(), state.roots().stream().map(CampaignDomainValidation::summary).toList(), state); }
    private static UUID manual(CampaignStorageService s, String name, String kind) { send(s, "manual-create", object("name", name, "kind", kind, "template", "blank")); return s.accessSnapshot().snapshot().domains().manual().characters().stream().filter(c -> c.current().name().equals(name)).findFirst().orElseThrow().id(); }
    private static UUID member(CampaignStorageService s, String name) { send(s, "member-add", object("name", name)); return s.accessSnapshot().snapshot().domains().roots().getLast().subjectId(); }
    private static void control(CampaignStorageService s, UUID pc, UUID member, long version) { send(s, "control-set", object("characterId", pc.toString(), "memberId", member.toString(), "controlVersion", Long.toString(version))); }
    private static void send(CampaignStorageService s, String action, JsonObject p) { var a = s.accessSnapshot(); s.domainCommand(uuid(), a.epoch(), a.revision(), action, p); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static JsonObject object(String... pairs) { JsonObject p = new JsonObject(); for (int i = 0; i < pairs.length; i += 2) p.addProperty(pairs[i], pairs[i + 1]); return p; }
}
