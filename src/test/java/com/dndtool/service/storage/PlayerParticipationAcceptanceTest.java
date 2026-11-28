package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.service.Schema2CombatState;
import com.google.gson.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.dndtool.service.storage.CampaignParticipationData.*;
import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

/** Current player acceptance boundaries on disposable SQLite; no listener or browser is started. */
class PlayerParticipationAcceptanceTest {
    private static final String REVEALED_WOLF = "林间灰狼";
    private static final String HIDDEN_WOLF = "密伏黑狼";
    private static final String OUTSIDE_WOLF = "营地外的白狼";
    private static final String FIGHTER = "持剑战士";
    private static final String SCENE = "林中战场";
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
            JsonObject option = f.option(old, "装备 ");
            JsonObject original = f.command(old, option);
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
            JsonObject closed = f.post(newlyApproved, "receipt-close", closureIdentity(original, option)).body();
            assertEquals("UNVERIFIED", closed.getAsJsonObject("receipt").get("state").getAsString());
            assertFalse(result.getAsJsonObject("session").get("writeEnabled").getAsBoolean());
            assertEquals(beforeLookup, f.storage.accessSnapshot(), "old acceptance is neither a login credential nor a new-run replay task");
        }
    }

    @Test void sameDeviceMayCloseUndeliveredRequestAndFenceItsOldSessionWithoutTakingOver() throws Exception {
        try (Fixture f = new Fixture("receipt-close")) {
            UUID pc = f.character("战士", "PC");
            f.acquire(pc, false);
            Seat original = f.approve("玩家"); f.assign(pc, original.member); f.permit(pc); f.takeover(original);
            JsonObject option = f.option(original, "装备 "), delayed = f.command(original, option);
            JsonObject close = closureIdentity(delayed, option);
            Seat otherDevice = f.approve("同席位的另一台设备", original.member);
            var before = f.storage.accessSnapshot();
            assertReceipt("UNVERIFIED", f.post(original, "receipt", receiptIdentity(delayed)));
            assertReceipt("UNVERIFIED", f.post(otherDevice, "receipt-close", close));
            JsonObject forged = close.deepCopy();
            forged.addProperty("receiptKey", "forged");
            assertReceipt("UNVERIFIED", f.post(original, "receipt-close", forged));

            var anonymous = f.gateway.read(null, null, true);
            Seat resumed = new Seat(f.gateway.post(anonymous.sessionToken(), csrf(anonymous), null, "resume", object(), original.device));
            assertReceipt("NOT_EXECUTED", f.post(resumed, "receipt-close", close));
            assertReceipt("NOT_EXECUTED", f.post(resumed, "receipt-close", close));
            assertFalse(f.read(resumed).getAsJsonObject("session").get("writeEnabled").getAsBoolean());
            assertEquals("PLAYER_VIEW_EXPIRED", assertThrows(PlayerGatewayException.class, () -> f.post(original, "command", delayed)).code());
            assertEquals(before, f.storage.accessSnapshot(), "closure and late delivery must write no business or attempt rows");

            f.takeover(resumed);
            JsonObject fresh = f.option(resumed, "装备 "), next = f.command(resumed, fresh);
            var accepted = f.post(resumed, "command", next);
            assertReceipt("ACCEPTED", accepted);
            assertEquals(accepted.body().get("receipt"), f.post(resumed, "receipt-close", closureIdentity(next, fresh)).body().get("receipt"));
            assertEquals(before.revision() + 1, f.storage.accessSnapshot().revision());
        }
    }

    @Test void hiddenOccupancyRejectsTheWholeExplorationRouteAndAllowsReceiptClosure() throws Exception {
        try (Fixture f = new Fixture("rejected-route")) {
            UUID pc = f.character("战士", "PC"), hidden = f.character("秘密守卫", "NPC");
            Seat player = f.approve("玩家"); f.assign(pc, player.member); f.takeover(player);
            PlayerSpaceFixture.beginBattle(f.storage, pc, hidden);
            f.combat(f.battle(), Command.action(Kind.END_COMBAT, f.battle().activeActor()));
            var permission = object("characterId", pc.toString(), "permissionVersion", "0", "sceneId", f.battle().combatId().toString());
            permission.addProperty("active", true);
            JsonArray actions = new JsonArray(); actions.add("EXPLORATION_MOVE"); permission.add("actions", actions);
            f.host("exploration", object("payload", permission.toString()));

            JsonObject option = f.option(player, "规划探索移动路线");
            assertFalse(option.toString().contains(hidden.toString()));
            var route = JsonParser.parseString("[{\"x\":1,\"y\":1},{\"x\":2,\"y\":0},{\"x\":3,\"y\":1}]");
            JsonObject command = f.command(player, option); command.add("route", route);
            var before = f.storage.accessSnapshot();
            assertEquals("PLAYER_COMMAND_REJECTED", assertThrows(PlayerGatewayException.class, () -> f.post(player, "command", command)).code());
            assertEquals(before.snapshot(), f.storage.accessSnapshot().snapshot(), "a free prefix before a hidden blocker cannot change position, clock or roots");
            assertEquals(before.revision(), f.storage.accessSnapshot().revision());
            assertReceipt("UNVERIFIED", f.post(player, "receipt", receiptIdentity(command)));
            assertReceipt("NOT_EXECUTED", f.post(player, "receipt-close", closureIdentity(command, option)));
            assertEquals("PLAYER_VIEW_EXPIRED", assertThrows(PlayerGatewayException.class, () -> f.post(player, "command", command)).code());
            assertEquals(before.snapshot(), f.storage.accessSnapshot().snapshot());
        }
    }

    @Test void receiptClosureCannotClearAnUnknownCommit() throws Exception {
        var interrupt = new AtomicBoolean();
        try (Fixture f = new Fixture("unknown-receipt", boundary -> {
            if (boundary == CampaignStorageService.Boundary.AFTER_BUSINESS_COMMIT && interrupt.getAndSet(false))
                throw new IllegalStateException("Simulated lost commit result");
        })) {
            UUID pc = f.character("战士", "PC");
            f.acquire(pc, false);
            Seat player = f.approve("玩家"); f.assign(pc, player.member); f.permit(pc); f.takeover(player);
            JsonObject option = f.option(player, "装备 "), command = f.command(player, option);
            interrupt.set(true);
            assertEquals("PLAYER_RESULT_UNVERIFIED", assertThrows(PlayerGatewayException.class, () -> f.post(player, "command", command)).code());
            assertEquals("BLOCKED", f.storage.status().state());
            assertEquals("PLAYER_RUN_UNAVAILABLE", assertThrows(PlayerGatewayException.class,
                    () -> f.post(player, "receipt-close", closureIdentity(command, option))).code());
            assertEquals("BLOCKED", f.storage.status().state());
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

    @Test void aSharedWolfTakesPlayerWeaponDamageAndKeepsTheOriginalPrivateReceipts() throws Exception {
        var dice = new ScriptedDice();
        try (Fixture f = new Fixture("shared-wolf", dice)) {
            UUID fighter = f.character(FIGHTER, "PC"), observer = f.character("哨兵", "PC");
            f.acquire(fighter, true);
            UUID world = PlayerSpaceFixture.placeAtWorld(f.storage, fighter, observer);
            UUID wolf = f.wolf(REVEALED_WOLF), hidden = f.wolf(HIDDEN_WOLF);
            UUID namesake = f.wolf(REVEALED_WOLF);
            UUID scene = f.createScene(world);
            var deployments = List.of(new Deployment(fighter, new Position(2, 2), 0),
                    new Deployment(observer, new Position(5, 5), 0), new Deployment(wolf, new Position(2, 1), 1),
                    new Deployment(hidden, new Position(7, 7), 1), new Deployment(namesake, new Position(1, 2), 1));
            dice.add(20, 6, 12, 16, 1);
            f.enterBattle(scene, deployments);
            assertTrue(dice.isEmpty());
            assertEquals(11, f.monster(wolf).hitPoints());

            Seat first = f.approve("持剑玩家"), second = f.approve("观察玩家");
            assertNotEquals(first.member, second.member);
            assertNotEquals(first.token, second.token);
            f.assign(fighter, first.member);
            f.assign(observer, second.member);
            f.share(ShareKind.SCENE, scene, true, first.member, second.member);
            f.share(ShareKind.MONSTER, wolf, true, first.member);
            f.share(ShareKind.MONSTER, namesake, true, first.member);
            f.takeover(first);
            f.takeover(second);
            var firstView = f.view(first);
            var secondView = f.view(second);
            assertCardOwner(firstView, fighter);
            assertCardOwner(secondView, observer);
            assertWolfParticipant(firstView, "2, 1", false);
            assertWolfParticipant(firstView, "1, 2", false);
            assertAbsent(firstView, wolf, hidden, HIDDEN_WOLF, namesake, observer, "哨兵", "monster.wolf");
            assertAbsent(secondView, wolf, REVEALED_WOLF, hidden, HIDDEN_WOLF, namesake, fighter, FIGHTER);

            dice.add(1);
            var miss = strike(f, first, fighter, wolf, false, 0, false, 11);
            assertTrue(dice.isEmpty());
            f.endTurn();
            assertEquals(hidden, f.battle().activeActor());
            var hiddenTurn = f.view(first);
            assertAbsent(hiddenTurn, hidden, HIDDEN_WOLF);
            assertTrue(participants(hiddenTurn).asList().stream()
                    .noneMatch(row -> row.getAsJsonObject().get("current").getAsBoolean()));
            f.endTurn();
            assertEquals(wolf, f.battle().activeActor());
            var revealedTurn = f.view(first);
            assertWolfParticipant(revealedTurn, "2, 1", true);
            assertTrue(publicScene(revealedTurn).get("turnLabel").getAsString().contains(REVEALED_WOLF));
            assertAbsent(f.view(second), wolf, REVEALED_WOLF);
            f.advanceTo(fighter);

            dice.add(12, 3);
            var hit = strike(f, first, fighter, wolf, true, 5, false, 6);
            assertTrue(dice.isEmpty());
            f.endTurn();
            f.advanceTo(fighter);

            dice.add(12, 8);
            var drop = strike(f, first, fighter, wolf, true, 10, true, 0);
            assertTrue(dice.isEmpty());
            assertTrue(f.monster(wolf).dead());
            var anonymous = f.gateway.read(null, null, true);
            var resumed = new Seat(f.gateway.post(anonymous.sessionToken(), csrf(anonymous), null, "resume", object(), first.device));
            var beforeLookup = f.storage.accessSnapshot();
            for (var strike : List.of(miss, hit, drop)) {
                JsonObject query = receiptIdentity(strike.command());
                assertEquals(strike.receipt(), f.post(resumed, "receipt", query).body().get("receipt"));
                JsonObject otherReceipt = f.post(second, "receipt", query).body().getAsJsonObject("receipt");
                assertEquals(Set.of("state"), otherReceipt.keySet());
                assertEquals("UNVERIFIED", otherReceipt.get("state").getAsString());
            }
            assertEquals(beforeLookup, f.storage.accessSnapshot());

            String file = f.storage.end(uuid(), beforeLookup.epoch(), beforeLookup.revision()).fileKey();
            f.storage.open(file);
            assertEquals(beforeLookup.snapshot(), f.storage.accessSnapshot().snapshot());
            assertEquals(0, f.monster(wolf).hitPoints());
            assertTrue(f.monster(wolf).dead());
            assertEquals(11, f.monster(hidden).hitPoints());
            assertEquals(11, f.monster(namesake).hitPoints());
            assertEquals("PLAYER_SESSION_REQUIRED", assertThrows(PlayerGatewayException.class, () -> f.read(first)).code());
            f.invitation = f.host("invite", object()).get("invitation").getAsString();
            Seat firstRestored = f.approve("重新核对持剑玩家", first.member);
            Seat secondRestored = f.approve("重新核对观察玩家", second.member);
            var restoredView = f.view(firstRestored);
            var otherRestoredView = f.view(secondRestored);
            assertCardOwner(restoredView, fighter);
            assertCardOwner(otherRestoredView, observer);
            assertWolfParticipant(restoredView, "2, 1", false);
            assertWolfParticipant(restoredView, "1, 2", false);
            assertAbsent(restoredView, wolf, hidden, HIDDEN_WOLF, namesake, observer, "哨兵", "monster.wolf");
            assertAbsent(otherRestoredView, wolf, REVEALED_WOLF, hidden, HIDDEN_WOLF, namesake, fighter, FIGHTER);
            assertReceipt("UNVERIFIED", f.post(firstRestored, "receipt", receiptIdentity(drop.command())));
        }
    }

    @Test void swappingHiddenOccupancyPreservesThePlayerViewAndCandidateMeaning() throws Exception {
        var dice = new ScriptedDice();
        try (Fixture f = new Fixture("hidden-wolf", dice)) {
            UUID fighter = f.character(FIGHTER, "PC");
            f.acquire(fighter, true);
            UUID world = PlayerSpaceFixture.placeAtWorld(f.storage, fighter);
            UUID wolf = f.wolf(REVEALED_WOLF), hidden = f.wolf(HIDDEN_WOLF), outside = f.wolf(OUTSIDE_WOLF);
            UUID scene = f.createScene(world);
            var pcAtCenter = new Deployment(fighter, new Position(2, 2), 0);
            var wolfAtNorth = new Deployment(wolf, new Position(2, 1), 1);
            Seat first = f.approve("先控制者"), second = f.approve("后控制者");
            f.assign(fighter, first.member);
            f.share(ShareKind.SCENE, scene, true, first.member);
            f.share(ShareKind.MONSTER, wolf, true, first.member);
            f.share(ShareKind.MONSTER, outside, true, first.member);
            f.takeover(first);
            f.takeover(second);

            dice.add(20, 5, 12);
            f.enterBattle(scene, List.of(pcAtCenter, wolfAtNorth, new Deployment(hidden, new Position(3, 2), 1)));
            f.combat(f.battle(), Command.action(Kind.BEGIN_ATTACK, fighter));
            var east = f.view(first);
            assertEquals(List.of(fighter, hidden, wolf), f.battle().initiative().stream().map(Initiative::characterId).toList());
            assertEquals(List.of(FIGHTER, REVEALED_WOLF), participantNames(east));
            assertWolfParticipant(east, "2, 1", false);
            assertAbsent(east, hidden, HIDDEN_WOLF, outside, OUTSIDE_WOLF);
            assertEquals(1, attackLabels(east).size());
            wolfOption(east, "2, 1");
            assertTrue(labels(east).containsAll(List.of("向东移动一格", "向西移动一格")));
            assertFalse(labels(east).contains("向北移动一格"));
            assertHiddenMoveRejected(f, first, east, "向东移动一格");

            f.leaveBattle(world, fighter, wolf, hidden);
            dice.add(20, 5, 12);
            f.enterBattle(scene, List.of(pcAtCenter, wolfAtNorth, new Deployment(hidden, new Position(1, 2), 1)));
            f.combat(f.battle(), Command.action(Kind.BEGIN_ATTACK, fighter));
            var west = f.view(first);
            assertEquals(labels(east), labels(west), "opaque candidate tokens are allowed to change with the revision");
            var eastVisible = east.deepCopy();
            var westVisible = west.deepCopy();
            eastVisible.remove("candidates");
            westVisible.remove("candidates");
            assertEquals(eastVisible, westVisible, "the scene, visible initiative and positions are unchanged");
            assertAbsent(west, hidden, HIDDEN_WOLF, outside, OUTSIDE_WOLF);
            assertHiddenMoveRejected(f, first, west, "向西移动一格");
            assertTrue(dice.isEmpty());

            var beforeWithdrawal = f.command(first, wolfOption(west, "2, 1"));
            f.share(ShareKind.MONSTER, wolf, false, first.member);
            assertStale(f, first, beforeWithdrawal);
            var withdrawn = f.view(first);
            assertAbsent(withdrawn, wolf, REVEALED_WOLF, hidden, HIDDEN_WOLF);
            assertTrue(attackLabels(withdrawn).isEmpty());
            assertTrue(labels(withdrawn).contains("向北移动一格"));

            f.share(ShareKind.MONSTER, wolf, true, first.member, second.member);
            var beforeTransfer = f.command(first, wolfOption(f.view(first), "2, 1"));
            f.assign(fighter, second.member);
            assertStale(f, first, beforeTransfer);
            var formerController = f.view(first);
            assertEquals(0, formerController.getAsJsonArray("cards").size());
            assertTrue(attackLabels(formerController).isEmpty());
            assertWolfParticipant(formerController, "2, 1", false);
            assertAbsent(formerController, fighter, FIGHTER, hidden, HIDDEN_WOLF);
            var noSceneShare = f.view(second);
            assertCardOwner(noSceneShare, fighter);
            assertEquals(0, noSceneShare.getAsJsonObject("shared").getAsJsonArray("scenes").size());
            assertTrue(attackLabels(noSceneShare).isEmpty());
            assertAbsent(noSceneShare, wolf, REVEALED_WOLF, hidden, HIDDEN_WOLF);
            f.share(ShareKind.SCENE, scene, true, first.member, second.member);
            var newlyShared = f.view(second);
            assertWolfParticipant(newlyShared, "2, 1", false);
            assertEquals(attackLabels(west), attackLabels(newlyShared));
            assertAbsent(newlyShared, hidden, HIDDEN_WOLF, outside, OUTSIDE_WOLF);
            assertTrue(dice.isEmpty());
        }
    }

    private static Strike strike(Fixture f, Seat seat, UUID fighter, UUID wolf,
            boolean hit, int damage, boolean dropped, int remaining) {
        var begun = f.post(seat, "command", f.command(seat, f.option(seat, "开始攻击动作")));
        assertReceipt("ACCEPTED", begun);
        var begunView = begun.body().getAsJsonObject("view");
        assertEquals(2, attackLabels(begunView).size());
        var north = wolfOption(begunView, "2, 1");
        var west = wolfOption(begunView, "1, 2");
        assertNotEquals(north.get("label"), west.get("label"));
        var command = f.command(seat, north);
        var before = f.storage.accessSnapshot();
        var response = f.post(seat, "command", command).body();
        var receipt = response.getAsJsonObject("receipt");
        assertEquals(Set.of("state", "outcome", "requestId", "label", "attackResult"), receipt.keySet());
        assertEquals("ACCEPTED", receipt.get("state").getAsString());
        assertEquals("COMPLETED", receipt.get("outcome").getAsString());
        var expected = new JsonObject();
        expected.addProperty("hit", hit);
        expected.addProperty("damage", damage);
        expected.addProperty("dropped", dropped);
        assertEquals(expected, receipt.get("attackResult"));
        assertEquals(Integer.toString(damage), receipt.getAsJsonObject("attackResult").get("damage").toString());
        assertAbsent(receipt, wolf, REVEALED_WOLF, HIDDEN_WOLF);
        var view = response.getAsJsonObject("view");
        assertWolfParticipant(view, "2, 1", false);
        assertAbsent(view, wolf, HIDDEN_WOLF, "monster.wolf");

        var after = f.storage.accessSnapshot();
        assertEquals(before.revision() + 1, after.revision());
        var roots = after.snapshot().domains().roots();
        assertEquals(before.snapshot().domains().roots().size() + 1, roots.size());
        var root = roots.getLast();
        assertEquals(command.get("requestId").getAsString(), root.operationId().toString());
        assertEquals(Actor.player(seat.member, fighter), root.actor());
        long matchingRoots = roots.stream().filter(event -> event.operationId().equals(root.operationId())).count();
        assertEquals(1, matchingRoots);
        var attack = f.battle().flows().getLast().attacks().getFirst();
        assertEquals(wolf, attack.targetId());
        assertEquals(AttackStatus.RESOLVED, attack.status());
        assertEquals(hit, attack.hit());
        assertEquals(damage, attack.damageAmount());
        assertEquals(13, attack.armorClass());
        assertEquals(hit ? List.of(20, 8) : List.of(20), root.rolls().stream().map(CampaignSpaceData.Die::sides).toList());
        assertEquals(remaining, f.monster(wolf).hitPoints());
        return new Strike(command, receipt);
    }

    private static void assertHiddenMoveRejected(Fixture f, Seat seat, JsonObject view, String direction) {
        var command = f.command(seat, option(view, direction));
        var before = f.storage.accessSnapshot();
        var failure = assertThrows(PlayerGatewayException.class, () -> f.post(seat, "command", command));
        assertEquals("PLAYER_COMMAND_REJECTED", failure.code());
        assertEquals(409, failure.status());
        assertEquals(failure.code(), failure.getMessage());
        var after = f.storage.accessSnapshot();
        assertEquals(before.revision(), after.revision());
        assertEquals(before.snapshot(), after.snapshot(), "rejection leaves positions, clocks, dice and roots intact");
        assertReceipt("UNVERIFIED", f.post(seat, "receipt", receiptIdentity(command)));
    }

    private static void assertStale(Fixture f, Seat seat, JsonObject command) {
        var before = f.storage.accessSnapshot();
        assertEquals("PLAYER_VIEW_EXPIRED", assertThrows(PlayerGatewayException.class, () -> f.post(seat, "command", command)).code());
        assertEquals(before, f.storage.accessSnapshot());
    }

    private static JsonObject publicScene(JsonObject view) {
        var scenes = view.getAsJsonObject("shared").getAsJsonArray("scenes");
        assertEquals(1, scenes.size());
        var scene = scenes.get(0).getAsJsonObject();
        assertEquals(SCENE, scene.get("name").getAsString());
        return scene;
    }

    private static JsonArray participants(JsonObject view) {
        return publicScene(view).getAsJsonArray("participants");
    }

    private static List<String> participantNames(JsonObject view) {
        return participants(view).asList().stream().map(row -> row.getAsJsonObject().get("name").getAsString()).toList();
    }

    private static void assertWolfParticipant(JsonObject view, String position, boolean current) {
        var wolf = participants(view).asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> REVEALED_WOLF.equals(row.get("name").getAsString()))
                .filter(row -> position.equals(row.get("position").getAsString())).findFirst().orElseThrow();
        assertEquals(Set.of("name", "position", "current"), wolf.keySet());
        assertEquals(current, wolf.get("current").getAsBoolean());
        assertEquals(0, view.getAsJsonObject("shared").getAsJsonArray("cards").size());
    }

    private static void assertCardOwner(JsonObject view, UUID character) {
        var cards = view.getAsJsonArray("cards");
        assertEquals(1, cards.size());
        assertEquals(character.toString(), cards.get(0).getAsJsonObject().get("id").getAsString());
    }

    private static void assertAbsent(JsonObject result, Object... privateValues) {
        for (Object value : privateValues)
            assertFalse(result.toString().contains(value.toString()), () -> "Player result disclosed " + value);
    }

    private static List<String> labels(JsonObject view) {
        return view.getAsJsonArray("candidates").asList().stream().map(row -> row.getAsJsonObject().get("label").getAsString()).sorted().toList();
    }

    private static List<String> attackLabels(JsonObject view) {
        return labels(view).stream().filter(label -> label.startsWith("攻击 ")).toList();
    }

    private static JsonObject wolfOption(JsonObject view, String position) {
        var matches = attackLabels(view).stream()
                .filter(label -> label.startsWith("攻击 " + REVEALED_WOLF) && label.contains(position)).toList();
        assertEquals(1, matches.size(), () -> "Expected one attack against the wolf at " + position);
        return option(view, matches.getFirst());
    }

    private static JsonObject option(JsonObject view, String label) {
        return view.getAsJsonArray("candidates").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(option -> option.get("label").getAsString().startsWith(label)).findFirst()
                .orElseThrow(() -> new AssertionError("Expected current legal option: " + label));
    }

    private record Strike(JsonObject command, JsonObject receipt) { }

    private static final class ScriptedDice implements IntUnaryOperator {
        private final Queue<Integer> rolls = new ArrayDeque<>();
        void add(Integer... values) { rolls.addAll(List.of(values)); }
        boolean isEmpty() { return rolls.isEmpty(); }
        @Override public int applyAsInt(int sides) {
            Integer roll = rolls.poll();
            assertNotNull(roll, "Unexpected live d" + sides);
            assertTrue(roll >= 1 && roll <= sides, "Invalid scripted d" + sides);
            return roll;
        }
    }

    private final class Fixture implements AutoCloseable {
        final CampaignStorageService storage;
        final MutableClock clock = new MutableClock();
        final PlayerGatewayService gateway;
        final CampaignRuleContext rules;
        String invitation;
        Fixture(String name) throws Exception {
            this(name, ignored -> { });
        }
        Fixture(String name, CampaignStorageService.FaultInjector faults) throws Exception {
            this(new CampaignStorageService(PlayerParticipationAcceptanceTest.this.temporary.resolve(name), faults), false);
        }
        Fixture(String name, ScriptedDice dice) throws Exception {
            this(new CampaignStorageService(PlayerParticipationAcceptanceTest.this.temporary.resolve(name), ignored -> { }, dice), true);
        }
        private Fixture(CampaignStorageService storage, boolean deliveredRules) throws Exception {
            this.storage = storage;
            if (deliveredRules) CampaignWolfBiteIntegrationTest.createWithDeliveredRules(storage, "玩家怪物验收");
            else CampaignRuleFixtures.create(storage, "玩家边界验收");
            rules = storage.accessSnapshot().snapshot().domains().material().restore();
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
        JsonObject view(Seat seat) { return read(seat).getAsJsonObject("view"); }
        PlayerGatewayService.Reply post(Seat seat, String action, JsonObject input) { return gateway.post(seat.token, seat.csrf, seat.connection, action, input, null); }
        void takeover(Seat seat) { seat.connection = post(seat, "takeover", object()).body().get("connectionId").getAsString(); }
        JsonObject option(Seat seat, String label) { return PlayerParticipationAcceptanceTest.option(view(seat), label); }
        JsonObject command(Seat seat, JsonObject option) { return object("connectionId", seat.connection, "candidateId", option.get("id").getAsString(), "precondition", option.get("precondition").getAsString(), "requestId", uuid()); }
        UUID character(String name, String kind) { send("character-create", object("name", name, "kind", kind, "classKey", "fighter", "backgroundKey", "acolyte")); return storage.accessSnapshot().snapshot().domains().characters().stream().filter(character -> character.name().equals(name)).findFirst().orElseThrow().id(); }
        UUID manual(String name, String kind) { send("manual-create", object("name", name, "kind", kind, "template", "blank")); return storage.accessSnapshot().snapshot().domains().manual().characters().stream().filter(character -> character.current().name().equals(name)).findFirst().orElseThrow().id(); }
        UUID wolf(String name) {
            var input = object("name", name);
            input.add("monster", CampaignDomainCodec.toJson(rules.rule("adventure.monster", "monster.wolf")));
            return UUID.fromString(send("monster-create", input).lastCommand().subjectId());
        }
        CampaignSpaceData.Monster monster(UUID id) {
            return storage.accessSnapshot().snapshot().domains().space().monsters().stream()
                    .filter(monster -> monster.id().equals(id)).findFirst().orElseThrow();
        }
        UUID createScene(UUID world) {
            return UUID.fromString(send("scene-create", object("name", SCENE, "nodeId", world.toString(), "width", "10",
                    "height", "10", "resolution", "RULES")).lastCommand().subjectId());
        }
        void enterBattle(UUID scene, List<Deployment> placements) {
            var state = storage.accessSnapshot().snapshot().domains();
            var subjects = new JsonArray();
            for (var placement : placements) {
                var old = CampaignSpaceCommands.position(state, placement.characterId());
                subjects.add(object("id", placement.characterId().toString(), "version", Long.toString(old.version()),
                        "x", Integer.toString(placement.position().x()), "y", Integer.toString(placement.position().y()),
                        "side", Integer.toString(placement.side())));
            }
            var input = object("sceneId", scene.toString());
            input.add("subjects", subjects);
            send("scene-enter", input);
            var roster = placements.stream().map(Deployment::characterId).toList();
            combat(battle(), Command.reenter(roster.getFirst(), roster));
            assertEquals(roster.getFirst(), battle().activeActor());
        }
        void leaveBattle(UUID world, UUID... actors) {
            var battle = battle();
            combat(battle, Command.action(Kind.END_COMBAT, battle.activeActor()));
            var state = storage.accessSnapshot().snapshot().domains();
            var subjects = new JsonArray();
            for (UUID actor : actors) {
                var old = CampaignSpaceCommands.position(state, actor);
                subjects.add(object("id", actor.toString(), "version", Long.toString(old.version())));
            }
            var input = object();
            input.add("subjects", subjects);
            send("scene-leave", input);
            PlayerSpaceFixture.focus(storage, CampaignSpaceData.Ref.world(world));
        }
        void endTurn() {
            var current = battle();
            combat(current, Command.action(Kind.END_TURN, current.activeActor()));
        }
        void advanceTo(UUID actor) {
            int remaining = battle().initiative().size();
            while (!actor.equals(battle().activeActor()) && remaining-- > 0) endTurn();
            assertEquals(actor, battle().activeActor());
        }
        void assign(UUID pc, UUID member) { var control = storage.accessSnapshot().snapshot().domains().participation().control(pc); host("control", object("payload", object("characterId", pc.toString(), "memberId", member.toString(), "controlVersion", control == null ? "0" : Long.toString(control.version())).toString())); }
        void permit(UUID pc) {
            UUID node = PlayerSpaceFixture.placeAtWorld(storage, pc);
            long version = storage.accessSnapshot().snapshot().domains().participation().exploration().stream().filter(permission -> permission.characterId().equals(pc)).mapToLong(Exploration::version).findFirst().orElse(0);
            JsonObject input = object("characterId", pc.toString(), "permissionVersion", Long.toString(version)); input.addProperty("sceneId", node.toString()); input.addProperty("active", true);
            JsonArray actions = new JsonArray(); actions.add("CHARACTER_COMMAND"); input.add("actions", actions); host("exploration", object("payload", input.toString()));
        }
        void share(ShareKind kind, UUID subject, boolean active, UUID... members) {
            var previous = storage.accessSnapshot().snapshot().domains().participation().shares().stream().filter(share -> share.kind() == kind && share.subjectId().equals(subject)).findFirst().orElse(null);
            JsonObject input = object("shareVersion", previous == null ? "0" : Long.toString(previous.version()), "kind", kind.name(), "subjectId", subject.toString());
            input.add("shareId", previous == null ? JsonNull.INSTANCE : new JsonPrimitive(previous.id().toString()));
            var audience = Arrays.stream(members).sorted(Comparator.comparing(UUID::toString)).toList();
            input.add("audience", CampaignDomainCodec.toJson(new Audience(members.length == 0 ? AudienceKind.ALL : AudienceKind.SPECIFIC, audience)));
            input.addProperty("active", active);
            host("share", object("payload", input.toString()));
        }
        void acquire(UUID pc, boolean equip) {
            var command = new Schema2CharacterRuntime.Command("ITEM_ACQUIRED", null, 0, null, null, null, Schema2CharacterRuntime.GrowthChoices.EMPTY, null, null,
                    new Schema2CharacterRuntime.EquipmentInput(UUID.randomUUID(), rules.rule("equipment.item", "item.longsword"), "CARRIED", "主持准备"), null);
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
                    new Schema2CharacterRuntime.CheckInput(rules.rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, 10, false, false, "固定验收检定")))); return input;
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
    private static void assertReceipt(String expected, PlayerGatewayService.Reply reply) {
        assertEquals(expected, reply.body().getAsJsonObject("receipt").get("state").getAsString());
    }
    private static JsonObject closureIdentity(JsonObject command, JsonObject option) {
        JsonObject id = receiptIdentity(command);
        id.add("receiptKey", option.get("receiptKey"));
        return id;
    }
    private static JsonObject receiptIdentity(JsonObject command) { return object("requestId", command.get("requestId").getAsString(), "candidateId", command.get("candidateId").getAsString(), "precondition", command.get("precondition").getAsString()); }
    private static String csrf(PlayerGatewayService.Reply reply) { return reply.body().getAsJsonObject("session").get("csrf").getAsString(); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static JsonObject object(String... values) { JsonObject result = new JsonObject(); for (int i = 0; i < values.length; i += 2) result.addProperty(values[i], values[i + 1]); return result; }
}
