package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.service.Schema2CombatState;
import com.google.gson.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.dndtool.service.storage.CampaignParticipationData.*;
import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

/** Current player acceptance boundaries on disposable SQLite; no listener or browser is started. */
class PlayerParticipationAcceptanceTest {
    @TempDir Path temporary;

    @Test void invitationRotationExactExpiryAndClosureDoNotRevokeAlreadyApprovedPlayers() throws Exception {
        try (Fixture f = new Fixture("invitations")) {
            String initial = f.invitation;
            Seat approved = f.approve("已核对玩家");
            var business = f.storage.accessSnapshot();
            JsonObject rotated = f.host("invite", object());
            f.invitation = rotated.get("invitation").getAsString();
            assertNotEquals(initial, f.invitation);
            assertEquals("PLAYER_INVITATION_INVALID", f.rejectedJoin(initial).code());
            assertEquals(business, f.storage.accessSnapshot());

            f.clock.setMillis(Long.parseLong(rotated.get("invitationExpires").getAsString()));
            assertEquals("PLAYER_INVITATION_INVALID", f.rejectedJoin(f.invitation).code());
            assertFalse(f.gateway.hostState().get("invitationOpen").getAsBoolean());
            assertEquals("APPROVED", f.read(approved).getAsJsonObject("session").get("status").getAsString());
            assertEquals(business, f.storage.accessSnapshot());

            f.invitation = f.host("invite", object()).get("invitation").getAsString();
            f.host("close", object());
            assertEquals("PLAYER_INVITATION_INVALID", f.rejectedJoin(f.invitation).code());
            assertEquals("APPROVED", f.read(approved).getAsJsonObject("session").get("status").getAsString());
            f.takeover(approved);
            assertTrue(f.read(approved).getAsJsonObject("session").get("writeEnabled").getAsBoolean());
            assertEquals(0, f.gateway.hostState().getAsJsonArray("applications").size());
            assertEquals(business, f.storage.accessSnapshot(), "invalid joins must not create members, consume game dice or append roots");
        }
    }

    @Test void endAndFileReopenRequireFreshApprovalAndNeverReinterpretAnOldReceipt() throws Exception {
        try (Fixture f = new Fixture("new-run")) {
            UUID pc = f.character("战士", "PC");
            f.acquire(pc, false);
            Seat old = f.approve("玩家"); f.assign(pc, old.member); f.permit(pc); f.takeover(old);
            JsonObject original = f.command(old, f.option(old, "装备 "));
            JsonObject accepted = f.post(old, "command", original).body();
            assertEquals("ACCEPTED", accepted.getAsJsonObject("receipt").get("state").getAsString());
            JsonObject exact = receiptIdentity(original);
            assertEquals(accepted.get("receipt"), f.post(old, "receipt", exact).body().get("receipt"));
            var previous = f.storage.accessSnapshot();
            var current = f.storage.current(); String file = f.storage.end(uuid(), current.epoch(), current.revision()).fileKey();
            assertEquals("IDLE", f.storage.status().state());
            assertEquals("PLAYER_SESSION_REQUIRED", assertThrows(PlayerGatewayException.class, () -> f.read(old)).code());
            assertEquals("PLAYER_SESSION_REQUIRED", assertThrows(PlayerGatewayException.class, () -> f.post(old, "receipt", exact)).code());

            f.storage.open(file);
            var reopened = f.storage.accessSnapshot();
            assertNotEquals(previous.runId(), reopened.runId());
            assertEquals(previous.snapshot(), reopened.snapshot(), "portable participation and accepted business facts remain intact");
            f.invitation = f.host("invite", object()).get("invitation").getAsString();
            var anonymous = f.gateway.read(null, null, true);
            assertEquals("PLAYER_DEVICE_REAPPROVAL_REQUIRED", assertThrows(PlayerGatewayException.class,
                    () -> f.gateway.post(anonymous.sessionToken(), csrf(anonymous), null, "resume", object(), old.device)).code());
            assertEquals("PLAYER_SESSION_REQUIRED", assertThrows(PlayerGatewayException.class, () -> f.post(old, "receipt", exact)).code());
            Seat newlyApproved = f.approve("重新现场核对", old.member);
            var beforeLookup = f.storage.accessSnapshot();
            JsonObject result = f.post(newlyApproved, "receipt", exact).body();
            assertEquals("UNVERIFIED", result.getAsJsonObject("receipt").get("state").getAsString());
            assertFalse(result.getAsJsonObject("session").get("writeEnabled").getAsBoolean());
            assertEquals(beforeLookup, f.storage.accessSnapshot(), "old acceptance is neither a login credential nor a new-run replay task");
        }
    }

    @Test void allAudienceIncludesLaterApprovedMembersAndPreviouslyAuthorizedLogsUntilWithdrawal() throws Exception {
        try (Fixture f = new Fixture("audiences")) {
            UUID sharedNpc = f.manual("公开使者", "NPC"); f.manual("仍然隐藏的守卫", "NPC");
            Seat first = f.approve("先加入者");
            f.send("note", object("text", "获准的先前记录"));
            UUID campaign = UUID.fromString(f.storage.accessSnapshot().snapshot().campaignId());
            f.share(ShareKind.CHARACTER, sharedNpc, true);
            f.share(ShareKind.LOG, campaign, true);
            Seat later = f.approve("后来加入者"); assertNotEquals(first.member, later.member);
            JsonObject view = f.read(later).getAsJsonObject("view");
            assertEquals(0, view.getAsJsonArray("cards").size());
            assertTrue(view.getAsJsonObject("shared").toString().contains("公开使者"));
            assertTrue(view.getAsJsonObject("shared").toString().contains("获准的先前记录"));
            assertFalse(view.toString().contains("仍然隐藏的守卫"));
            f.send("note", object("text", "政策持续覆盖的后续记录"));
            assertTrue(f.read(later).getAsJsonObject("view").getAsJsonObject("shared").toString().contains("政策持续覆盖的后续记录"));
            f.share(ShareKind.CHARACTER, sharedNpc, false); f.share(ShareKind.LOG, campaign, false);
            var beforeReads = f.storage.accessSnapshot();
            for (Seat seat : List.of(first, later)) {
                JsonObject shared = f.read(seat).getAsJsonObject("view").getAsJsonObject("shared");
                assertEquals(0, shared.getAsJsonArray("cards").size()); assertEquals(0, shared.getAsJsonArray("logs").size());
            }
            assertEquals(beforeReads, f.storage.accessSnapshot());
        }
    }

    @Test void serializedControlTransferRejectsAQueuedOldCommandBeforeAnyPartialWriteOrDie() throws Exception {
        try (Fixture f = new Fixture("transfer")) {
            UUID pc = f.character("被交接的战士", "PC"); f.acquire(pc, false);
            Seat old = f.approve("原控制者"), next = f.approve("新控制者");
            f.assign(pc, old.member); f.permit(pc); f.takeover(old);
            JsonObject stale = f.command(old, f.option(old, "装备 "));
            CountDownLatch submitted = new CountDownLatch(1);
            ExecutorService executor = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "late-player-command"); thread.setDaemon(true); return thread; });
            try {
                Future<PlayerGatewayService.Reply> late;
                CampaignStorageService.AccessSnapshot afterTransfer;
                synchronized (f.storage) {
                    late = executor.submit(() -> { submitted.countDown(); return f.post(old, "command", stale); });
                    assertTrue(submitted.await(5, TimeUnit.SECONDS));
                    f.assign(pc, next.member);
                    afterTransfer = f.storage.accessSnapshot();
                }
                ExecutionException rejected = assertThrows(ExecutionException.class, () -> late.get(30, TimeUnit.SECONDS));
                assertEquals("PLAYER_VIEW_EXPIRED", assertInstanceOf(PlayerGatewayException.class, rejected.getCause()).code());
                assertEquals(afterTransfer, f.storage.accessSnapshot(), "the queued command must not even retain a new attempt or partially alter equipment/history");
                assertEquals(0, f.read(old).getAsJsonObject("view").getAsJsonArray("cards").size());

                // A supported random command would draw a die for an authorized actor. The same
                // former controller must fail in the common pure command boundary before that draw.
                int[] draws = {0}; JsonObject check = f.check(pc);
                assertEquals("PLAYER_CONTROL_REQUIRED", assertThrows(StorageException.class, () -> new CampaignDomainCommands().apply(
                        afterTransfer.snapshot(), "character-command", check, UUID.randomUUID(), "0".repeat(64), sides -> { draws[0]++; return 10; }, Actor.player(old.member, pc))).code());
                assertEquals(0, draws[0]); assertEquals(afterTransfer, f.storage.accessSnapshot());
                f.permit(pc); f.takeover(next);
                var beforeAccepted = f.storage.accessSnapshot();
                assertEquals("ACCEPTED", f.post(next, "command", f.command(next, f.option(next, "装备 "))).body().getAsJsonObject("receipt").get("state").getAsString());
                var accepted = f.storage.accessSnapshot(); assertEquals(beforeAccepted.revision() + 1, accepted.revision());
                assertEquals(Actor.player(next.member, pc), accepted.snapshot().domains().roots().getLast().actor());
            } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
        }
    }

    @Test void pausingAtARealReactionWindowPreservesTheLeafTurnAndOriginalWaitingReceipt() throws Exception {
        try (Fixture f = new Fixture("pending-pause")) {
            UUID pc = f.character("移动的战士", "PC"), npc = f.character("隐藏借机攻击者", "NPC");
            f.acquire(pc, true); f.acquire(npc, true);
            Seat player = f.approve("玩家"); f.assign(pc, player.member);
            PlayerSpaceFixture.beginBattle(f.storage, pc, npc);
            Battle battle = f.battle(); if (battle.activeActor().equals(npc)) f.combat(battle, Command.action(Kind.END_TURN, npc));
            f.takeover(player);
            JsonObject movement = f.command(player, f.option(player, "向西移动一格"));
            JsonObject accepted = f.post(player, "command", movement).body();
            assertEquals("WAITING", accepted.getAsJsonObject("receipt").get("outcome").getAsString());
            var waiting = f.storage.accessSnapshot(); assertNotNull(f.battle().activeFlow());
            assertTrue(f.battle().activeFlow().windows().stream().flatMap(window -> window.opportunities().stream()).anyMatch(opportunity -> opportunity.actorId().equals(npc) && opportunity.status() == OpportunityStatus.PENDING));

            f.host("pause", object("paused", "true"));
            assertEquals(waiting.snapshot(), f.storage.accessSnapshot().snapshot()); assertEquals(waiting.revision(), f.storage.accessSnapshot().revision());
            JsonObject paused = f.read(player); assertTrue(paused.getAsJsonObject("session").get("paused").getAsBoolean()); assertFalse(paused.getAsJsonObject("session").get("writeEnabled").getAsBoolean());
            var pausedFacts = f.storage.accessSnapshot(); JsonObject whilePaused = movement.deepCopy(); whilePaused.addProperty("requestId", uuid());
            assertEquals("PLAYER_OPERATIONS_PAUSED", assertThrows(PlayerGatewayException.class, () -> f.post(player, "command", whilePaused)).code());
            assertEquals(pausedFacts, f.storage.accessSnapshot());
            JsonObject receipt = f.post(player, "receipt", receiptIdentity(movement)).body().getAsJsonObject("receipt");
            assertEquals(accepted.getAsJsonObject("receipt"), receipt); assertEquals("WAITING", receipt.get("outcome").getAsString());
            assertEquals(pausedFacts, f.storage.accessSnapshot(), "read-only verification must not PASS the other actor's reaction");
            f.host("pause", object("paused", "false"));
            assertEquals(waiting.snapshot(), f.storage.accessSnapshot().snapshot()); assertEquals(waiting.revision(), f.storage.accessSnapshot().revision());
            assertNotNull(f.battle().activeFlow());
        }
    }

    private final class Fixture implements AutoCloseable {
        final CampaignStorageService storage;
        final MutableClock clock = new MutableClock();
        final PlayerGatewayService gateway;
        String invitation;
        Fixture(String name) throws Exception {
            storage = new CampaignStorageService(temporary.resolve(name));
            CampaignRuleFixtures.create(storage, "玩家边界验收");
            gateway = new PlayerGatewayService(storage, clock); invitation = host("invite", object()).get("invitation").getAsString();
        }
        JsonObject host(String action, JsonObject input) { var current = storage.current(); return gateway.hostCommand(action, input, uuid(), current.epoch(), current.revision()); }
        CampaignStorageService.CampaignView send(String action, JsonObject input) { return PlayerSpaceFixture.send(storage, action, input); }
        Seat approve(String name) { return approve(name, null); }
        Seat approve(String name, UUID existing) {
            var anonymous = gateway.read(null, null, true);
            gateway.post(anonymous.sessionToken(), csrf(anonymous), null, "join", object("invitation", invitation, "nickname", name), null);
            host("approve", object("applicationId", csrf(anonymous), "memberId", existing == null ? "" : existing.toString()));
            var approved = gateway.read(anonymous.sessionToken(), null, false); return new Seat(approved);
        }
        PlayerGatewayException rejectedJoin(String code) {
            var anonymous = gateway.read(null, null, true);
            return assertThrows(PlayerGatewayException.class, () -> gateway.post(anonymous.sessionToken(), csrf(anonymous), null, "join", object("invitation", code, "nickname", "尚未核对"), null));
        }
        JsonObject read(Seat seat) { return gateway.read(seat.token, seat.connection, false).body(); }
        PlayerGatewayService.Reply post(Seat seat, String action, JsonObject input) { return gateway.post(seat.token, seat.csrf, seat.connection, action, input, null); }
        void takeover(Seat seat) { seat.connection = post(seat, "takeover", object()).body().get("connectionId").getAsString(); }
        JsonObject option(Seat seat, String label) { return read(seat).getAsJsonObject("view").getAsJsonArray("candidates").asList().stream().map(JsonElement::getAsJsonObject).filter(option -> option.get("label").getAsString().startsWith(label)).findFirst().orElseThrow(() -> new AssertionError("Expected current legal option: " + label)); }
        JsonObject command(Seat seat, JsonObject option) { return object("connectionId", seat.connection, "candidateId", option.get("id").getAsString(), "precondition", option.get("precondition").getAsString(), "requestId", uuid()); }
        UUID character(String name, String kind) { send("character-create", object("name", name, "kind", kind, "classKey", "fighter", "backgroundKey", "acolyte")); return storage.accessSnapshot().snapshot().domains().characters().stream().filter(character -> character.name().equals(name)).findFirst().orElseThrow().id(); }
        UUID manual(String name, String kind) { send("manual-create", object("name", name, "kind", kind, "template", "blank")); return storage.accessSnapshot().snapshot().domains().manual().characters().stream().filter(character -> character.current().name().equals(name)).findFirst().orElseThrow().id(); }
        void assign(UUID pc, UUID member) { var control = storage.accessSnapshot().snapshot().domains().participation().control(pc); host("control", object("payload", object("characterId", pc.toString(), "memberId", member.toString(), "controlVersion", control == null ? "0" : Long.toString(control.version())).toString())); }
        void permit(UUID pc) {
            UUID node = PlayerSpaceFixture.placeAtWorld(storage, pc);
            long version = storage.accessSnapshot().snapshot().domains().participation().exploration().stream().filter(permission -> permission.characterId().equals(pc)).mapToLong(Exploration::version).findFirst().orElse(0);
            JsonObject input = object("characterId", pc.toString(), "permissionVersion", Long.toString(version)); input.addProperty("sceneId", node.toString()); input.addProperty("active", true);
            JsonArray actions = new JsonArray(); actions.add("CHARACTER_COMMAND"); input.add("actions", actions); host("exploration", object("payload", input.toString()));
        }
        void share(ShareKind kind, UUID subject, boolean active) {
            var previous = storage.accessSnapshot().snapshot().domains().participation().shares().stream().filter(share -> share.kind() == kind && share.subjectId().equals(subject)).findFirst().orElse(null);
            JsonObject input = object("shareVersion", previous == null ? "0" : Long.toString(previous.version()), "kind", kind.name(), "subjectId", subject.toString());
            input.add("shareId", previous == null ? JsonNull.INSTANCE : new JsonPrimitive(previous.id().toString())); input.add("audience", CampaignDomainCodec.toJson(new Audience(AudienceKind.ALL, List.of()))); input.addProperty("active", active);
            host("share", object("payload", input.toString()));
        }
        void acquire(UUID pc, boolean equip) {
            var command = new Schema2CharacterRuntime.Command("ITEM_ACQUIRED", null, 0, null, null, null, Schema2CharacterRuntime.GrowthChoices.EMPTY, null, null,
                    new Schema2CharacterRuntime.EquipmentInput(UUID.randomUUID(), CampaignRuleFixtures.rules().rule("equipment.item", "item.longsword"), "CARRIED", "主持准备"), null);
            characterCommand(pc, command);
            if (equip) {
                UUID item = storage.accessSnapshot().snapshot().domains().characters().stream().filter(character -> character.id().equals(pc)).findFirst().orElseThrow().history().current().equipment().getFirst().itemId();
                characterCommand(pc, new Schema2CharacterRuntime.Command("ITEM_EQUIPPED", null, 0, null, null, null, Schema2CharacterRuntime.GrowthChoices.EMPTY, null, null,
                        new Schema2CharacterRuntime.EquipmentInput(item, null, "MAIN_HAND", "主持准备"), null));
            }
        }
        void characterCommand(UUID pc, Schema2CharacterRuntime.Command command) { JsonObject input = characterInput(pc); input.add("command", CampaignDomainCodec.toJson(command)); send("character-command", input); }
        JsonObject characterInput(UUID pc) { var state = storage.accessSnapshot().snapshot().domains().characters().stream().filter(character -> character.id().equals(pc)).findFirst().orElseThrow().history().current(); return object("characterId", pc.toString(), "characterVersion", Long.toString(state.version())); }
        JsonObject check(UUID pc) {
            JsonObject input = characterInput(pc); input.add("command", CampaignDomainCodec.toJson(new Schema2CharacterRuntime.Command("CHECK_RESOLVED", null, 0, null, null, null, Schema2CharacterRuntime.GrowthChoices.EMPTY, null,
                    new Schema2CharacterRuntime.CheckInput(CampaignRuleFixtures.rules().rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, 10, false, false, "固定验收检定")))); return input;
        }
        void combat(Battle previous, Command command) { PlayerSpaceFixture.combat(storage, previous, command); }
        Battle battle() { return storage.accessSnapshot().snapshot().domains().combats().getFirst().current(); }
        @Override public void close() throws Exception { storage.close(); }
    }
    private static final class Seat {
        final String token, csrf, device; final UUID member; String connection;
        Seat(PlayerGatewayService.Reply approved) { token = approved.sessionToken(); csrf = csrf(approved); device = approved.deviceToken(); member = UUID.fromString(approved.body().getAsJsonObject("session").get("playerKey").getAsString()); }
    }
    private static final class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-11-04T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { if (!zone.equals(ZoneOffset.UTC)) throw new IllegalArgumentException("Fixture clock uses UTC"); return this; }
        @Override public Instant instant() { return current; }
        void setMillis(long value) { current = Instant.ofEpochMilli(value); }
    }
    private static JsonObject receiptIdentity(JsonObject command) { return object("requestId", command.get("requestId").getAsString(), "candidateId", command.get("candidateId").getAsString(), "precondition", command.get("precondition").getAsString()); }
    private static String csrf(PlayerGatewayService.Reply reply) { return reply.body().getAsJsonObject("session").get("csrf").getAsString(); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static JsonObject object(String... values) { JsonObject result = new JsonObject(); for (int i = 0; i < values.length; i += 2) result.addProperty(values[i], values[i + 1]); return result; }
}
