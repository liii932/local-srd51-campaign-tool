package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.service.Schema2CombatState;
import com.google.gson.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private static final Position WOLF_START = new Position(1, 1);
    private static final Position FIGHTER_START = new Position(2, 2);
    private static final List<Position> WOLF_ROUTE = List.of(new Position(2, 1), new Position(3, 0), new Position(4, 0));
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

    @Test void approvedArcherPaysBeforeShieldAndReceivesOnlyPublicMonsterOutcomes() throws Exception {
        var dice = new ScriptedDice();
        var failAfterUpdate = new AtomicBoolean();
        try (Fixture f = new Fixture("ranged-weapon", dice, boundary -> {
            if (boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE && failAfterUpdate.getAndSet(false))
                throw new IllegalStateException("ranged transaction failure");
        })) {
            UUID archer = f.character("弓手", "PC");
            UUID wizard = f.character("护盾法师", "PC", "wizard");
            UUID bow = f.acquire(archer, "item.shortbow");
            UUID firstArrow = f.acquire(archer, "item.arrow");
            UUID secondArrow = f.acquire(archer, "item.arrow");
            UUID thirdArrow = f.acquire(archer, "item.arrow");
            UUID otherOwnerArrow = f.acquire(wizard, "item.arrow");
            var unequipped = f.storage.accessSnapshot();
            var wrongHand = assertThrows(StorageException.class, () -> f.equip(archer, bow, "MAIN_HAND"));
            assertEquals("DOMAIN_REJECTED", wrongHand.code());
            assertEquals("S2_EQUIPMENT_HANDS", wrongHand.getMessage());
            assertEquals(unequipped, f.storage.accessSnapshot());
            f.equip(archer, bow, "BOTH_HANDS");
            var casting = f.rules.rule("character.wizard_spellcasting", "spellcasting.wizard");
            f.characterCommand(wizard, Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_INITIALIZED",
                    new Schema2CharacterRuntime.SpellbookInput(casting, f.rules.initialWizardBook(), f.rules.initialWizardPrepared())));

            UUID world = PlayerSpaceFixture.placeAtWorld(f.storage, archer, wizard);
            UUID skeleton = f.monster("可见骷髅", "monster.skeleton");
            UUID distant = f.monster("越界骷髅", "monster.skeleton");
            UUID hidden = f.monster("未揭示骷髅", "monster.skeleton");
            UUID scene = f.createScene(world, "70", "10");
            dice.add(20, 12, 10, 5, 1);
            f.enterBattle(scene, List.of(new Deployment(archer, new Position(2, 2), 0),
                    new Deployment(wizard, new Position(18, 2), 1), new Deployment(skeleton, new Position(19, 2), 1),
                    new Deployment(distant, new Position(67, 2), 1),
                    new Deployment(hidden, new Position(19, 3), 1)));
            assertTrue(dice.isEmpty());

            Seat shooter = f.approve("弓手玩家"), defender = f.approve("法师玩家");
            f.assign(archer, shooter.member);
            f.assign(wizard, defender.member);
            f.share(ShareKind.CHARACTER, wizard, true, shooter.member);
            f.takeover(shooter);
            f.takeover(defender);
            assertReceipt("ACCEPTED", f.post(shooter, "command", f.command(shooter, f.option(shooter, "开始攻击动作"))));
            assertTrue(shotLabels(f.view(shooter)).isEmpty());
            rejectCombat(f, Command.attack(archer, wizard, bow), Actor.player(shooter.member, archer),
                    "PLAYER_COMMAND_FORBIDDEN", null);
            f.share(ShareKind.SCENE, scene, true, shooter.member, defender.member);
            f.share(ShareKind.MONSTER, skeleton, true, shooter.member);
            f.share(ShareKind.MONSTER, distant, true, shooter.member);
            var view = f.view(shooter);
            assertEquals(2, shotLabels(view).size());
            assertTrue(shotLabels(view).stream().anyMatch(label -> label.contains("80 尺（正常射程）")));
            assertTrue(shotLabels(view).stream().anyMatch(label -> label.contains("85 尺（长射程）")));
            assertAbsent(view, "未揭示骷髅", skeleton, hidden, "monster.skeleton", "finalArmorClass");
            var sharedCards = view.getAsJsonObject("shared").getAsJsonArray("cards");
            assertEquals(1, sharedCards.size());
            assertEquals("护盾法师", sharedCards.get(0).getAsJsonObject().get("name").getAsString());
            rejectCombat(f, Command.attack(archer, distant, bow), null, "DOMAIN_REJECTED", "S2_COMBAT_OUT_OF_RANGE");
            rejectCombat(f, Command.attack(archer, hidden, bow), Actor.player(shooter.member, archer),
                    "PLAYER_COMMAND_FORBIDDEN", null);

            var before = f.storage.accessSnapshot();
            var request = f.command(shooter, f.option(shooter, "射击 护盾法师"));
            var damageBefore = f.card(wizard).hitPoints();
            var slotsBefore = f.wizardSlots(wizard);
            var hostStrike = combatInput(before, Command.attack(archer, wizard, bow));
            String faultRequest = uuid();
            failAfterUpdate.set(true);
            dice.add(10);
            assertEquals("ranged transaction failure", assertThrows(IllegalStateException.class, () -> f.storage.domainCommand(
                    faultRequest, before.epoch(), before.revision(), "combat-command", hostStrike)).getMessage());
            assertTrue(dice.isEmpty());
            assertEquals(before, f.storage.accessSnapshot());
            assertTrue(f.storage.exactDomainReceipt(faultRequest, before.runId(), before.revision(), "combat-command", hostStrike,
                    Actor.dm(archer, shooter.member), access -> {}).isEmpty());
            assertEquals(0, f.actor(archer).sequence().performed());

            dice.add(10);
            var declared = f.post(shooter, "command", request);
            var receipt = declared.body().getAsJsonObject("receipt");
            assertEquals("WAITING", receipt.get("outcome").getAsString());
            assertFalse(receipt.has("attackResult"));
            var pending = f.latestAttack();
            assertEquals(AttackStatus.HIT_PENDING_DAMAGE, pending.status());
            assertTrue(pending.initialHit());
            assertFalse(pending.disadvantage());
            assertEquals(10, pending.firstD20());
            assertNull(pending.secondD20());
            assertEquals(1, pending.abilityModifier());
            assertEquals(2, pending.proficiencyBonus());
            assertEquals(1, f.actor(archer).sequence().performed());
            assertArrowPayment(f, pending, Set.of(firstArrow, secondArrow, thirdArrow));
            var root = f.state().roots().getLast();
            assertEquals(Actor.player(shooter.member, archer), root.actor());
            assertEquals(request.get("requestId").getAsString(), root.operationId().toString());
            assertEquals(List.of(new CampaignSpaceData.Die(20, 10)), root.rolls());
            assertEquals(damageBefore, f.card(wizard).hitPoints());
            assertEquals(slotsBefore, f.wizardSlots(wizard));
            var waiting = f.storage.accessSnapshot();
            assertEquals(receipt, f.post(shooter, "receipt", receiptIdentity(request)).body().get("receipt"));
            assertStale(f, shooter, request);
            assertEquals(waiting, f.storage.accessSnapshot());
            assertForgedArrowsRejected(f);

            UUID shooterMember = shooter.member;
            UUID defenderMember = defender.member;
            finishAndOpen(f);
            assertEquals(pending, f.latestAttack());
            assertEquals(2, f.arrowIds(archer).size());
            f.invitation = f.host("invite", object()).get("invitation").getAsString();
            shooter = f.approve("重新核对弓手", shooterMember);
            defender = f.approve("重新核对法师", defenderMember);
            f.takeover(shooter);
            f.takeover(defender);
            assertReceipt("ACCEPTED", f.post(defender, "command", f.command(defender, f.option(defender, "Shield"))));
            assertEquals(slotsBefore - 1, f.wizardSlots(wizard));
            assertEquals(pending, f.latestAttack());
            assertReceipt("ACCEPTED", f.post(shooter, "command", f.command(shooter, f.option(shooter, "继续结算当前行动"))));
            var shielded = f.latestAttack();
            assertEquals(AttackStatus.RESOLVED, shielded.status());
            assertEquals(pending.frameKey(), shielded.frameKey());
            assertEquals(pending.firstD20(), shielded.firstD20());
            assertEquals(pending.rangedAttack(), shielded.rangedAttack());
            assertEquals(pending.armorClass() + 5, shielded.finalArmorClass());
            assertFalse(shielded.hit());
            assertEquals(damageBefore, f.card(wizard).hitPoints());
            assertTrue(f.state().roots().getLast().rolls().isEmpty());
            assertEquals(2, f.arrowIds(archer).size());
            assertEquals(1, f.actor(archer).sequence().performed());

            f.endTurn();
            f.advanceTo(archer);
            assertPlayerShot(f, shooter, archer, skeleton, dice, 6, 7, false, 6);
            f.endTurn();
            f.advanceTo(archer);
            assertPlayerShot(f, shooter, archer, skeleton, dice, 5, 6, true, 0);
            assertTrue(f.arrowIds(archer).isEmpty());
            f.endTurn();
            f.advanceTo(archer);
            f.combat(f.battle(), Command.action(Kind.BEGIN_ATTACK, archer));
            rejectCombat(f, Command.attack(archer, wizard, bow), null, "DOMAIN_REJECTED", "S2_COMBAT_AMMUNITION_UNAVAILABLE");
            assertTrue(shotLabels(f.view(shooter)).isEmpty());
            assertTrue(dice.isEmpty());
            finishAndOpen(f);
            assertTrue(f.arrowIds(archer).isEmpty());
            assertEquals(Set.of(otherOwnerArrow), f.arrowIds(wizard));
            assertTrue(f.monster(skeleton).dead());
        }
    }

    @Test void goblinHideAndSearchKeepIndependentPlayerViewsThroughReopenAndRollback() throws Exception {
        var dice = new ScriptedDice();
        var failAfterUpdate = new AtomicBoolean();
        try (Fixture f = new Fixture("observer-battle", dice, boundary -> {
            if (boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE && failAfterUpdate.getAndSet(false))
                throw new IllegalStateException("observation transaction failure");
        })) {
            String goblinName = "掩体地精";
            UUID fighter = f.character("警戒战士", "PC"), wizard = f.character("察觉法师", "PC", "wizard");
            UUID bow = f.acquire(wizard, "item.shortbow");
            UUID arrow = f.acquire(wizard, "item.arrow");
            f.equip(wizard, bow, "BOTH_HANDS");
            UUID world = PlayerSpaceFixture.placeAtWorld(f.storage, fighter, wizard);
            UUID goblin = f.monster(goblinName, "monster.goblin");
            UUID scene = f.createScene(world);
            Position origin = new Position(4, 2), second = new Position(4, 3), third = new Position(5, 3);
            Position intervening = new Position(3, 2);
            dice.add(20, 16, 14);
            f.enterBattle(scene, fighter, List.of(new Deployment(goblin, origin, 1),
                    new Deployment(fighter, new Position(1, 2), 0), new Deployment(wizard, new Position(2, 2), 0)));

            Seat fighterSeat = f.approve("战士玩家"), wizardSeat = f.approve("法师玩家");
            f.assign(fighter, fighterSeat.member);
            f.assign(wizard, wizardSeat.member);
            f.share(ShareKind.SCENE, scene, true, fighterSeat.member, wizardSeat.member);
            f.share(ShareKind.MONSTER, goblin, true, fighterSeat.member, wizardSeat.member);
            f.takeover(fighterSeat);
            f.takeover(wizardSeat);
            setObservationCell(f, origin, true, false, Cover.NONE);
            setObservationCell(f, second, true, false, Cover.NONE);
            setObservationCell(f, intervening, false, false, Cover.HALF);
            assertObserverRow(f.view(fighterSeat), goblinName, origin, false, true);
            assertObserverRow(f.view(wizardSeat), goblinName, origin, false, true);
            rejectCombat(f, Command.setObservationCell(new ObservationCellInput(origin, false, false, Cover.NONE)),
                    Actor.player(wizardSeat.member, wizard), "ACTOR_MISMATCH", null);

            var before = f.storage.accessSnapshot();
            String hideId = uuid();
            Command hide = Command.action(Kind.NIMBLE_HIDE, goblin);
            var hideInput = combatInput(before, hide);
            failAfterUpdate.set(true);
            dice.add(6);
            assertEquals("observation transaction failure", assertThrows(IllegalStateException.class, () ->
                    f.storage.domainCommand(hideId, before.epoch(), before.revision(), "combat-command", hideInput)).getMessage());
            assertTrue(dice.isEmpty());
            assertEquals(before, f.storage.accessSnapshot());
            Actor hideActor = CampaignParticipationCommands.actualActor(before.snapshot().domains(), "combat-command", hideInput);
            assertTrue(f.storage.exactDomainReceipt(hideId, before.runId(), before.revision(), "combat-command",
                    hideInput, hideActor, access -> {}).isEmpty());
            dice.add(6);
            var accepted = f.storage.domainCommand(hideId, before.epoch(), before.revision(), "combat-command", hideInput);
            var afterHide = f.storage.accessSnapshot();
            assertEquals(before.revision() + 1, afterHide.revision());
            assertFalse(accepted.lastCommand().replayed());
            assertTrue(f.storage.domainCommand(hideId, before.epoch(), before.revision(), "combat-command",
                    hideInput).lastCommand().replayed());
            assertEquals(afterHide, f.storage.accessSnapshot());
            assertEquals(List.of(new CampaignSpaceData.Die(20, 6)), lastRoot(f).rolls());
            var check = f.battle().observation().checks().getLast();
            assertEquals(12, check.roll().total());
            assertEquals(Map.of(fighter, 13, wizard, 11), check.contests().stream().collect(
                    java.util.stream.Collectors.toMap(ObservationContest::counterpartId, ObservationContest::opposingScore)));
            assertEquals(1, f.actor(goblin).actionsRemaining());
            assertEquals(0, f.actor(goblin).bonusActionsRemaining());
            rejectCombat(f, Command.action(Kind.NIMBLE_DISENGAGE, goblin), null, "DOMAIN_REJECTED",
                    "S2_COMBAT_BONUS_ACTION_UNAVAILABLE");
            assertObserverRow(f.view(fighterSeat), goblinName, origin, false, true);
            var wizardView = f.view(wizardSeat);
            assertObserverRow(wizardView, goblinName, origin, true, false);
            assertEquals("等待其他行动者", publicScene(wizardView).get("turnLabel").getAsString());
            f.combat(f.battle(), Command.move(goblin, List.of(second)));
            assertObserverRow(f.view(fighterSeat), goblinName, second, false, true);
            wizardView = f.view(wizardSeat);
            assertObserverRow(wizardView, goblinName, origin, true, false);
            assertAbsent(wizardView, "4, 3", goblin, "hideOperationId", "OBSERVATION_STEALTH");

            UUID fighterMember = fighterSeat.member, wizardMember = wizardSeat.member;
            finishAndOpen(f);
            f.invitation = f.host("invite", object()).get("invitation").getAsString();
            fighterSeat = f.approve("重开后的战士", fighterMember);
            wizardSeat = f.approve("重开后的法师", wizardMember);
            f.takeover(fighterSeat);
            f.takeover(wizardSeat);
            assertObserverRow(f.view(fighterSeat), goblinName, second, false, true);
            assertObserverRow(f.view(wizardSeat), goblinName, origin, true, false);

            f.clock.advanceSeconds(10);
            f.advanceTo(wizard);
            assertReceipt("ACCEPTED", f.post(wizardSeat, "command",
                    f.command(wizardSeat, f.option(wizardSeat, "开始攻击动作"))));
            setObservationCell(f, intervening, false, false, Cover.TOTAL);
            assertTrue(shotLabels(f.view(wizardSeat)).isEmpty());
            rejectCombat(f, Command.attack(wizard, goblin, bow), null, "DOMAIN_REJECTED", "S2_COMBAT_TOTAL_COVER");
            setObservationCell(f, intervening, false, false, Cover.HALF);
            var sightless = f.view(wizardSeat);
            assertEquals(1, shotLabels(sightless).size());
            assertTrue(shotLabels(sightless).getFirst().contains("最后已知 4, 2"));
            assertAbsent(sightless, "4, 3", goblin);
            dice.add(20, 20);
            var staleShot = f.post(wizardSeat, "command",
                    f.command(wizardSeat, option(sightless, "射击 " + goblinName)));
            assertReceipt("ACCEPTED", staleShot);
            var shot = f.latestAttack();
            assertEquals(origin, shot.aimedPosition());
            assertEquals(Cover.HALF, shot.targetCover());
            assertFalse(shot.targetAtAimedPosition());
            assertFalse(shot.hit());
            assertFalse(shot.critical());
            assertEquals(arrow, shot.rangedAttack().ammunitionItemId());
            assertTrue(f.arrowIds(wizard).isEmpty());
            assertObserverRow(f.view(wizardSeat), goblinName, origin, true, false);

            f.clock.advanceSeconds(10);
            f.advanceTo(goblin);
            var shortbow = new EntryIdentity(f.rules.rule("adventure.monster", "monster.goblin"),
                    "actions", "srd51_magic", "shortbow");
            dice.add(1, 2);
            f.combat(f.battle(), Command.monsterAttack(goblin, wizard, shortbow));
            var reveal = f.latestAttack();
            assertFalse(reveal.targetSeesAttacker());
            assertTrue(reveal.advantage());
            assertFalse(reveal.hit());
            assertObserverRow(f.view(wizardSeat), goblinName, second, false, true);

            f.endTurn();
            f.advanceTo(goblin);
            dice.add(6);
            f.combat(f.battle(), hide);
            setObservationCell(f, second, true, true, Cover.NONE);
            setObservationCell(f, third, true, true, Cover.NONE);
            f.combat(f.battle(), Command.move(goblin, List.of(third)));
            assertObserverRow(f.view(fighterSeat), goblinName, second, true, false);
            wizardView = f.view(wizardSeat);
            assertObserverRow(wizardView, goblinName, second, true, false);
            assertAbsent(wizardView, "5, 3");
            f.advanceTo(wizard);
            var search = f.command(wizardSeat, f.option(wizardSeat, "搜索（动作）"));
            dice.add(12);
            var searched = f.post(wizardSeat, "command", search);
            assertReceipt("ACCEPTED", searched);
            assertEquals(searched.body().get("receipt"), f.post(wizardSeat, "receipt", receiptIdentity(search)).body().get("receipt"));
            assertStale(f, wizardSeat, search);
            var searchCheck = f.battle().observation().checks().getLast();
            assertEquals(Kind.SEARCH, searchCheck.kind());
            assertEquals(List.of(goblin), searchCheck.contests().stream().map(ObservationContest::counterpartId).toList());
            assertFalse(searchCheck.contests().getFirst().hiddenAfter());
            assertObserverRow(f.view(wizardSeat), goblinName, third, true, false);
            var fighterView = f.view(fighterSeat);
            assertObserverRow(fighterView, goblinName, second, true, false);
            assertAbsent(fighterView, "5, 3");
            assertForgedObservationRejected(f);
            f.advanceTo(goblin);
            f.combat(f.battle(), Command.action(Kind.NIMBLE_DISENGAGE, goblin));
            assertTrue(f.actor(goblin).disengaged());
            assertEquals(1, f.actor(goblin).actionsRemaining());
            assertEquals(0, f.actor(goblin).bonusActionsRemaining());
            assertTrue(lastRoot(f).rolls().isEmpty());
            assertTrue(dice.isEmpty());
            finishAndOpen(f);
            var attached = f.storage.accessSnapshot();
            f.storage.close();
            try (var restarted = new CampaignStorageService(temporary.resolve("observer-battle"))) {
                var recovered = restarted.recover(attached.runId());
                assertNotEquals(attached.epoch(), recovered.epoch());
                assertEquals(attached.snapshot(), restarted.accessSnapshot().snapshot());
            }
        }
    }

    @Test void sharedClericTargetMustBeSeenAndAnUnseenMoverDoesNotOpenAnOpportunity() throws Exception {
        var dice = new ScriptedDice();
        try (Fixture f = new Fixture("observer-spell", dice)) {
            String targetName = "受遮蔽目标";
            UUID cleric = f.character("施法牧师", "PC", "cleric"), target = f.character(targetName, "PC");
            UUID sword = f.acquire(cleric, "item.longsword");
            f.equip(cleric, sword, "MAIN_HAND");
            UUID world = PlayerSpaceFixture.placeAtWorld(f.storage, cleric, target);
            UUID scene = f.createScene(world);
            Position targetCell = new Position(2, 1);
            dice.add(20, 10);
            f.enterBattle(scene, List.of(new Deployment(cleric, new Position(1, 1), 0), new Deployment(target, targetCell, 1)));
            Seat seat = f.approve("牧师玩家");
            f.assign(cleric, seat.member);
            f.share(ShareKind.SCENE, scene, true, seat.member);
            f.share(ShareKind.CHARACTER, target, true, seat.member);
            f.takeover(seat);
            String heal = "Cure Wounds：" + targetName;
            assertTrue(labels(f.view(seat)).contains(heal));
            var spell = Command.cast(cleric, new CastInput(f.rules.rule("character.spellcasting", "spellcasting.life_domain"),
                    f.rules.rule("adventure.spell", "spell.cure_wounds"), List.of(target), null));
            for (Cover cover : List.of(Cover.HALF, Cover.TOTAL)) {
                setObservationCell(f, targetCell, false, cover == Cover.HALF, cover);
                var view = f.view(seat);
                assertObserverRow(view, targetName, targetCell, true, false);
                assertFalse(labels(view).contains(heal));
                rejectCombat(f, spell, Actor.player(seat.member, cleric), "PLAYER_COMMAND_FORBIDDEN", null);
                rejectCombat(f, spell, null, "DOMAIN_REJECTED", "S2_SPELL_TARGET");
            }
            setObservationCell(f, targetCell, false, false, Cover.NONE);
            dice.add(3);
            assertReceipt("ACCEPTED", f.post(seat, "command", f.command(seat, f.option(seat, heal))));
            assertEquals(cleric, lastRoot(f).actor().characterId());
            assertEquals(List.of(new CampaignSpaceData.Die(8, 3)), lastRoot(f).rolls());
            setObservationCell(f, targetCell, false, true, Cover.NONE);
            f.endTurn();
            Position destination = new Position(3, 1);
            f.combat(f.battle(), Command.move(target, List.of(destination)));
            assertNull(f.battle().activeFlow());
            assertTrue(f.actor(cleric).reactionAvailable());
            assertObserverRow(f.view(seat), targetName, targetCell, true, false);
            assertTrue(dice.isEmpty());
        }
    }

    private static void setObservationCell(Fixture f, Position at, boolean hide, boolean blocks, Cover cover) {
        f.combat(f.battle(), Command.setObservationCell(new ObservationCellInput(at, hide, blocks, cover)));
    }

    private static void assertObserverRow(JsonObject view, String name, Position at, boolean lastKnown, boolean current) {
        var row = participants(view).asList().stream().map(JsonElement::getAsJsonObject)
                .filter(participant -> name.equals(participant.get("name").getAsString())).findFirst().orElseThrow();
        assertEquals(at.x() + ", " + at.y(), row.get("position").getAsString());
        assertEquals(current, row.get("current").getAsBoolean());
        assertEquals(lastKnown, row.has("lastKnown"));
        if (lastKnown) assertTrue(row.get("lastKnown").getAsBoolean());
    }

    private static void assertForgedObservationRejected(Fixture f) {
        var codec = new CampaignCodec();
        byte[] original = codec.encode(new CampaignData.Document(uuid(), f.storage.accessSnapshot().snapshot()));
        for (String field : List.of("cells", "pairs")) {
            var forged = CampaignJson.read(original);
            var history = forged.getAsJsonObject("domains").getAsJsonArray("combats").get(0).getAsJsonObject();
            var after = history.getAsJsonArray("events").asList().getLast().getAsJsonObject()
                    .getAsJsonObject("resolution").getAsJsonObject("after");
            for (var battle : List.of(history.getAsJsonObject("current"), after)) {
                var row = battle.getAsJsonObject("observation").getAsJsonArray(field).get(0).getAsJsonObject();
                if (field.equals("cells")) row.addProperty("cover", Cover.THREE_QUARTERS.name());
                else row.add("lastKnownPosition", CampaignDomainCodec.toJson(new Position(9, 9)));
            }
            var integrity = forged.remove("integrity").getAsJsonObject();
            integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(forged)));
            forged.add("integrity", integrity);
            assertEquals("INVALID_FILE", assertThrows(StorageException.class,
                    () -> codec.decode(CampaignJson.canonical(forged))).code());
        }
    }

    @Test void monsterShortbowUsesCloseEnemyDisadvantageAndNeitherBowProvidesOpportunityAttacks() throws Exception {
        var dice = new ScriptedDice();
        try (Fixture f = new Fixture("ranged-monster", dice)) {
            UUID guard = f.character("守卫", "PC"), archer = f.character("近身弓手", "PC");
            UUID bow = f.acquire(archer, "item.shortbow");
            f.acquire(archer, "item.arrow");
            f.equip(archer, bow, "BOTH_HANDS");
            UUID world = PlayerSpaceFixture.placeAtWorld(f.storage, guard, archer);
            UUID skeleton = f.monster("骷髅弓手", "monster.skeleton");
            var rule = f.rules.rule("adventure.monster", "monster.skeleton");
            var shortbow = new EntryIdentity(rule, "actions", rule.moduleKey(), "shortbow");
            var shortsword = new EntryIdentity(rule, "actions", rule.moduleKey(), "shortsword");
            UUID scene = f.createScene(world, "20", "10");
            var origin = new Position(2, 2);
            var adjacent = new Position(3, 2);
            dice.add(20, 12, 5);
            f.enterBattle(scene, archer, List.of(new Deployment(skeleton, origin, 0),
                    new Deployment(archer, adjacent, 1), new Deployment(guard, new Position(18, 2), 1)));
            assertTrue(dice.isEmpty());

            long hitPoints = f.card(guard).hitPoints();
            dice.add(20, 10, 6);
            f.combat(f.battle(), Command.monsterAttack(skeleton, guard, shortbow));
            var attack = f.latestAttack();
            assertEquals(AttackStatus.RESOLVED, attack.status());
            assertEquals(new RangedAttackEvidence(80, 80, 320, true, null), attack.rangedAttack());
            assertEquals(shortbow, attack.monsterAttack().action());
            assertEquals(4, attack.monsterAttack().hitBonus());
            assertEquals(10, attack.selectedD20());
            assertTrue(attack.disadvantage());
            assertFalse(attack.critical());
            assertTrue(attack.hit());
            assertEquals(8, attack.damageAmount());
            assertEquals(hitPoints - 8, f.card(guard).hitPoints());
            assertFalse(f.actor(skeleton).actionAvailable());
            assertTrue(f.state().combats().getFirst().events().getLast().resolution().ammunitionUses().isEmpty());

            f.combat(f.battle(), Command.move(skeleton, List.of(new Position(1, 2))));
            assertNull(f.battle().activeFlow());
            assertTrue(f.actor(archer).reactionAvailable());
            f.endTurn();
            assertEquals(archer, f.battle().activeActor());
            f.combat(f.battle(), Command.move(archer, List.of(origin, adjacent)));
            var flow = f.battle().activeFlow();
            var window = flow.windows().getLast();
            var opportunity = window.opportunities().stream().filter(o -> o.status() == OpportunityStatus.PENDING)
                    .findFirst().orElseThrow();
            assertEquals(skeleton, opportunity.actorId());
            assertEquals(List.of(shortsword), opportunity.options().stream().map(ReactionOption::monsterAttack).toList());
            rejectCombat(f, Command.monsterOpportunityAttack(skeleton, shortbow, flow.flowKey(), flow.leafFrameKey(),
                    window.windowKey(), opportunity.opportunityKey()), null, "DOMAIN_REJECTED", null);
            assertTrue(dice.isEmpty());
            finishAndOpen(f);
            assertEquals(flow, f.battle().activeFlow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aRevealedWolfResumesItsPaidRouteAfterTheRightPlayerAttacksOrPasses(boolean attacks) throws Exception {
        var dice = new ScriptedDice();
        try (Fixture f = new Fixture("wolf-route-" + attacks, dice)) {
            var encounter = wolfMovement(f, dice);
            UUID wolf = encounter.wolf(), fighter = encounter.fighter();
            Seat owner = encounter.owner(), observer = encounter.observer();
            f.share(ShareKind.MONSTER, wolf, true, owner.member, observer.member);
            for (Seat seat : List.of(owner, observer)) {
                var view = f.view(seat);
                assertWolfParticipant(view, "1, 1", true);
                assertEquals(List.of(), labels(view));
            }
            var move = Command.move(wolf, WOLF_ROUTE);
            rejectCombat(f, move, Actor.player(owner.member, wolf), "PLAYER_CONTROL_REQUIRED", null);

            Combatant wolfBefore = combatant(f, wolf), fighterBefore = combatant(f, fighter);
            long wolfPositionBefore = placement(f, wolf).version();
            var initiative = f.battle().initiative();
            f.combat(f.battle(), move);
            var pausedFlow = f.battle().activeFlow();
            var waiting = wolfWindow(f, wolf, fighter);
            assertEquals(List.of(), lastRoot(f).rolls());
            assertEquals(Actor.dm(wolf, null), lastRoot(f).actor());
            assertEquals(1, pausedFlow.movement().cursor());
            assertEquals(10, pausedFlow.movement().spentHalfFeet());
            assertEquals(WOLF_ROUTE, pausedFlow.movement().path());
            assertEquals(wolfPositionBefore + 1, placement(f, wolf).version());
            assertRouteBudgets(f, wolfBefore, fighterBefore, WOLF_ROUTE.getFirst(), 10, true);
            assertPlayerReaction(f, owner, observer, wolf, fighter);

            finishAndOpen(f);
            assertEquals(pausedFlow, f.battle().activeFlow());
            assertEquals(initiative, f.battle().initiative());
            assertEquals(waiting, wolfWindow(f, wolf, fighter));
            assertRouteBudgets(f, wolfBefore, fighterBefore, WOLF_ROUTE.getFirst(), 10, true);
            assertEquals("PLAYER_SESSION_REQUIRED", assertThrows(PlayerGatewayException.class, () -> f.read(owner)).code());
            assertEquals("PLAYER_SESSION_REQUIRED", assertThrows(PlayerGatewayException.class, () -> f.read(observer)).code());
            f.invitation = f.host("invite", object()).get("invitation").getAsString();
            Seat restoredOwner = f.approve("重新核对持剑玩家", owner.member);
            Seat restoredObserver = f.approve("重新核对观察玩家", observer.member);
            f.takeover(restoredOwner);
            f.takeover(restoredObserver);
            assertPlayerReaction(f, restoredOwner, restoredObserver, wolf, fighter);

            String label = attacks ? "借机攻击" : "放弃本次反应";
            JsonObject option = f.option(restoredOwner, label);
            var beforeReaction = f.storage.accessSnapshot();
            assertEquals("PLAYER_VIEW_EXPIRED", assertThrows(PlayerGatewayException.class,
                    () -> f.post(restoredObserver, "command", f.command(restoredObserver, option))).code());
            assertEquals(beforeReaction, f.storage.accessSnapshot());
            if (attacks) dice.add(12, 3);
            JsonObject request = f.command(restoredOwner, option);
            var accepted = f.post(restoredOwner, "command", request);
            assertReceipt("ACCEPTED", accepted);
            var receipt = accepted.body().getAsJsonObject("receipt");
            assertEquals("WAITING", receipt.get("outcome").getAsString());
            assertAbsent(receipt, wolf, "armorClass", "hitPoints", "maxHitPoints");
            var closedFlow = f.battle().activeFlow();
            var closedWindow = closedFlow.windows().getFirst();
            assertEquals(waiting.windowKey(), closedWindow.windowKey());
            assertEquals(WindowStatus.CLOSED, closedWindow.status());
            var resolved = closedWindow.opportunities().getFirst();
            assertEquals(waiting.opportunities().getFirst().opportunityKey(), resolved.opportunityKey());
            assertEquals(attacks ? OpportunityStatus.COMPLETED : OpportunityStatus.PASSED, resolved.status());
            assertEquals(Actor.player(restoredOwner.member, fighter), lastRoot(f).actor());
            if (attacks) {
                var result = receipt.getAsJsonObject("attackResult");
                assertEquals(Set.of("hit", "damage", "dropped"), result.keySet());
                assertTrue(result.get("hit").getAsBoolean());
                assertEquals(5, result.get("damage").getAsInt());
                assertFalse(result.get("dropped").getAsBoolean());
                var attack = closedFlow.attacks().getFirst();
                assertTrue(attack.opportunityAttack());
                assertEquals(AttackStatus.RESOLVED, attack.status());
                assertEquals(fighter, attack.actorId());
                assertEquals(wolf, attack.targetId());
                assertEquals(resolved.allowedItems().getFirst(), attack.itemId());
                assertEquals(12, attack.firstD20());
                assertEquals(List.of(3), attack.damageDice());
                assertEquals(13, attack.finalArmorClass());
                assertEquals(List.of(new CampaignSpaceData.Die(20, 12), new CampaignSpaceData.Die(8, 3)), lastRoot(f).rolls());
            } else {
                assertFalse(receipt.has("attackResult"));
                assertTrue(closedFlow.attacks().isEmpty());
                assertEquals(List.of(), lastRoot(f).rolls());
            }
            assertEquals(attacks ? 6 : 11, f.monster(wolf).hitPoints());
            assertEquals(wolfPositionBefore + 1, placement(f, wolf).version());
            assertRouteBudgets(f, wolfBefore, fighterBefore, WOLF_ROUTE.getFirst(), 10, !attacks);
            var decidedView = accepted.body().getAsJsonObject("view");
            assertPlayerBudget(decidedView, combatant(f, fighter));
            assertEquals(List.of(), labels(decidedView));
            assertEquals(List.of(), labels(f.view(restoredObserver)));
            var decided = f.storage.accessSnapshot();
            assertEquals(beforeReaction.revision() + 1, decided.revision());
            assertEquals(receipt, f.post(restoredOwner, "receipt", receiptIdentity(request)).body().get("receipt"));
            assertReceipt("UNVERIFIED", f.post(restoredObserver, "receipt", receiptIdentity(request)));
            assertEquals("PLAYER_VIEW_EXPIRED", assertThrows(PlayerGatewayException.class,
                    () -> f.post(restoredOwner, "command", request)).code());
            assertEquals(decided, f.storage.accessSnapshot());

            if (attacks) {
                var reactionRolls = lastRoot(f).rolls();
                finishAndOpen(f);
                assertEquals(closedFlow, f.battle().activeFlow());
                assertEquals(reactionRolls, lastRoot(f).rolls());
                assertRouteBudgets(f, wolfBefore, fighterBefore, WOLF_ROUTE.getFirst(), 10, false);
            }

            var pending = f.storage.accessSnapshot();
            var continuation = Command.continuation(wolf, closedFlow.flowKey(), closedFlow.leafFrameKey(), closedWindow.windowKey());
            JsonObject input = combatInput(pending, continuation);
            String continuationId = uuid();
            var continued = f.storage.domainCommand(continuationId, pending.epoch(), pending.revision(), "combat-command", input);
            var completed = f.battle().flows().getLast();
            assertEquals(closedFlow.flowKey(), completed.flowKey());
            assertEquals(MovementStatus.COMPLETED, completed.movement().status());
            assertEquals(WOLF_ROUTE.size(), completed.movement().cursor());
            assertEquals(30, completed.movement().spentHalfFeet());
            assertEquals(closedFlow.attacks(), completed.attacks());
            assertEquals(WindowStatus.CONSUMED, completed.windows().getFirst().status());
            assertNull(f.battle().activeFlow());
            assertEquals(wolfPositionBefore + 2, placement(f, wolf).version());
            assertRouteBudgets(f, wolfBefore, fighterBefore, WOLF_ROUTE.getLast(), 30, !attacks);
            assertEquals(Actor.dm(wolf, null), lastRoot(f).actor());
            assertEquals(List.of(), lastRoot(f).rolls());
            var afterContinue = f.storage.accessSnapshot();
            assertEquals(pending.revision() + 1, afterContinue.revision());
            var replay = f.storage.domainCommand(continuationId, pending.epoch(), pending.revision(), "combat-command", input);
            assertTrue(replay.lastCommand().replayed());
            assertEquals(continued.lastCommand().acceptedRevision(), replay.lastCommand().acceptedRevision());
            assertEquals(afterContinue, f.storage.accessSnapshot());
            assertTrue(dice.isEmpty());
        }
    }

    @Test void aHiddenWolfKeepsItsReactionWindowPrivateAfterMonsterSharingIsWithdrawn() throws Exception {
        var dice = new ScriptedDice();
        try (Fixture f = new Fixture("hidden-wolf-route", dice)) {
            var encounter = wolfMovement(f, dice);
            UUID wolf = encounter.wolf(), fighter = encounter.fighter();
            Seat owner = encounter.owner();
            f.combat(f.battle(), Command.move(wolf, WOLF_ROUTE));
            var flow = f.battle().activeFlow();
            var window = wolfWindow(f, wolf, fighter);
            assertHiddenWolf(f.view(owner), wolf);
            assertHiddenWolf(f.view(encounter.observer()), wolf);

            f.share(ShareKind.MONSTER, wolf, true, owner.member, encounter.observer().member);
            assertPlayerReaction(f, owner, encounter.observer(), wolf, fighter);
            JsonObject issued = f.command(owner, f.option(owner, "借机攻击"));
            f.share(ShareKind.MONSTER, wolf, true, encounter.observer().member);
            var notShared = f.storage.accessSnapshot();
            assertEquals("PLAYER_VIEW_EXPIRED", assertThrows(PlayerGatewayException.class,
                    () -> f.post(owner, "command", issued)).code());
            assertHiddenWolf(f.view(owner), wolf);
            assertWolfParticipant(f.view(encounter.observer()), "2, 1", true);

            var offer = window.opportunities().getFirst();
            var attack = Command.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK, fighter, offer.allowedItems().getFirst(),
                    flow.flowKey(), flow.leafFrameKey(), window.windowKey(), offer.opportunityKey());
            var pass = Command.reaction(Kind.PASS_REACTION, fighter, null,
                    flow.flowKey(), flow.leafFrameKey(), window.windowKey(), offer.opportunityKey());
            rejectCombat(f, attack, Actor.player(owner.member, fighter), "PLAYER_COMMAND_FORBIDDEN", null);
            rejectCombat(f, pass, Actor.player(owner.member, fighter), "PLAYER_COMMAND_FORBIDDEN", null);
            assertEquals(notShared, f.storage.accessSnapshot());
            assertEquals(flow, f.battle().activeFlow());
            f.combat(f.battle(), pass);
            assertEquals(Actor.dm(fighter, owner.member), lastRoot(f).actor());
            var cleared = f.battle().activeFlow();
            f.combat(f.battle(), Command.continuation(wolf, flow.flowKey(), cleared.leafFrameKey(), window.windowKey()));
            assertEquals(WOLF_ROUTE.getLast(), placement(f, wolf).grid());
            assertTrue(combatant(f, fighter).reactionAvailable());
            assertTrue(dice.isEmpty());
        }
    }

    @Test void invalidWolfRoutesWriteNothingAndDisengagingSkipsTheFightersReaction() throws Exception {
        var dice = new ScriptedDice();
        try (Fixture f = new Fixture("wolf-disengage", dice)) {
            var encounter = wolfMovement(f, dice);
            UUID wolf = encounter.wolf(), fighter = encounter.fighter();
            f.share(ShareKind.MONSTER, wolf, true, encounter.owner().member);
            var overSpeed = new ArrayList<>(WOLF_ROUTE);
            overSpeed.addAll(List.of(new Position(5, 0), new Position(6, 0), new Position(7, 0),
                    new Position(8, 0), new Position(9, 0), new Position(9, 1)));
            rejectCombat(f, Command.move(wolf, overSpeed), null, "DOMAIN_REJECTED", "S2_COMBAT_MOVEMENT_ALLOWANCE");
            var outOfBounds = List.of(WOLF_ROUTE.getFirst(), WOLF_ROUTE.get(1), new Position(4, -1));
            rejectCombat(f, Command.move(wolf, outOfBounds), null, "SCENE_GRID_BOUNDS", null);
            assertEquals(WOLF_START, placement(f, wolf).grid());
            assertNull(f.battle().activeFlow());

            Combatant fighterBefore = combatant(f, fighter), wolfBefore = combatant(f, wolf);
            f.combat(f.battle(), Command.action(Kind.DISENGAGE, wolf));
            Combatant disengaged = combatant(f, wolf);
            assertEquals(wolfBefore.actionsRemaining() - 1, disengaged.actionsRemaining());
            assertEquals(wolfBefore.bonusActionsRemaining(), disengaged.bonusActionsRemaining());
            assertTrue(disengaged.disengaged());
            assertEquals(0, disengaged.movementSpentHalfFeet());
            f.combat(f.battle(), Command.move(wolf, WOLF_ROUTE));
            assertNull(f.battle().activeFlow());
            var completed = f.battle().flows().getLast();
            assertTrue(completed.windows().isEmpty());
            assertEquals(MovementStatus.COMPLETED, completed.movement().status());
            assertEquals(30, completed.movement().spentHalfFeet());
            assertRouteBudgets(f, disengaged, fighterBefore, WOLF_ROUTE.getLast(), 30, true);
            assertEquals(List.of(), lastRoot(f).rolls());
            assertEquals(List.of(), labels(f.view(encounter.owner())));
            assertTrue(dice.isEmpty());
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

    private static WolfMovement wolfMovement(Fixture f, ScriptedDice dice) {
        UUID fighter = f.character(FIGHTER, "PC"), watcher = f.character("观察者", "PC");
        f.acquire(fighter, true);
        UUID world = PlayerSpaceFixture.placeAtWorld(f.storage, fighter, watcher);
        UUID wolf = f.wolf(REVEALED_WOLF);
        UUID scene = f.createScene(world);
        dice.add(20, 12, 1);
        f.enterBattle(scene, List.of(new Deployment(fighter, FIGHTER_START, 0),
                new Deployment(wolf, WOLF_START, 1), new Deployment(watcher, new Position(8, 8), 0)));
        assertTrue(dice.isEmpty());
        f.endTurn();
        assertEquals(wolf, f.battle().activeActor());
        assertEquals(1, combatant(f, wolf).actionsRemaining());
        assertEquals(1, combatant(f, wolf).bonusActionsRemaining());
        assertTrue(combatant(f, wolf).reactionAvailable());
        assertTrue(combatant(f, fighter).reactionAvailable());
        Seat owner = f.approve("持剑玩家"), observer = f.approve("观察玩家");
        assertNotEquals(owner.member, observer.member);
        assertNotEquals(owner.token, observer.token);
        f.assign(fighter, owner.member);
        f.assign(watcher, observer.member);
        f.share(ShareKind.SCENE, scene, true, owner.member, observer.member);
        f.takeover(owner);
        f.takeover(observer);
        return new WolfMovement(wolf, fighter, owner, observer);
    }

    private static Window wolfWindow(Fixture f, UUID wolf, UUID fighter) {
        var flow = f.battle().activeFlow();
        assertNotNull(flow);
        assertEquals(1, flow.windows().size());
        var window = flow.windows().getFirst();
        assertEquals(WindowStatus.OPEN, window.status());
        assertEquals(TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE, window.trigger().kind());
        assertEquals(wolf, window.trigger().subjectId());
        assertEquals(WOLF_ROUTE.get(1), window.trigger().position());
        assertEquals(1, window.boundaryIndex());
        assertEquals(1, window.opportunities().size());
        var opportunity = window.opportunities().getFirst();
        assertEquals(fighter, opportunity.actorId());
        assertEquals(flow.leafFrameKey(), opportunity.selectionFrameKey());
        assertEquals(OpportunityStatus.PENDING, opportunity.status());
        assertEquals(List.of(ReactionKind.OPPORTUNITY_ATTACK), opportunity.options().stream().map(ReactionOption::kind).toList());
        return window;
    }

    private static void assertPlayerReaction(Fixture f, Seat owner, Seat observer, UUID wolf, UUID fighter) {
        var visible = f.view(owner);
        assertWolfParticipant(visible, "2, 1", true);
        assertAbsent(visible, wolf, "monster.wolf", "maxHitPoints");
        assertCardOwner(visible, fighter);
        assertPlayerBudget(visible, combatant(f, fighter));
        var pending = visible.getAsJsonObject("pending");
        assertTrue(pending.get("own").getAsBoolean());
        assertTrue(pending.get("description").getAsString().contains(REVEALED_WOLF));
        assertTrue(pending.get("description").getAsString().contains("2, 1"));
        assertEquals(2, labels(visible).size());
        option(visible, "借机攻击");
        option(visible, "放弃本次反应");
        var observation = f.view(observer);
        assertWolfParticipant(observation, "2, 1", true);
        assertAbsent(observation, wolf, fighter, FIGHTER, "monster.wolf");
        assertFalse(observation.getAsJsonObject("pending").get("own").getAsBoolean());
        assertEquals(List.of(), labels(observation));
    }

    private static void assertPlayerBudget(JsonObject view, Combatant combatant) {
        var card = view.getAsJsonArray("cards").get(0).getAsJsonObject();
        var budget = card.getAsJsonObject("turnBudget");
        assertEquals(combatant.actionsRemaining(), budget.get("actionsRemaining").getAsInt());
        assertEquals(combatant.bonusActionsRemaining(), budget.get("bonusActionsRemaining").getAsInt());
        assertEquals(combatant.reactionAvailable(), budget.get("reactionAvailable").getAsBoolean());
        assertEquals(combatant.remainingMovementHalfFeet(card.get("speed").getAsInt()),
                budget.get("movementRemainingHalfFeet").getAsInt());
    }

    private static void assertHiddenWolf(JsonObject view, UUID wolf) {
        assertAbsent(view, wolf, REVEALED_WOLF, "monster.wolf");
        var pending = view.getAsJsonObject("pending");
        assertFalse(pending.get("own").getAsBoolean());
        assertFalse(pending.get("active").getAsBoolean());
        assertEquals(List.of(), labels(view));
    }

    private static void assertRouteBudgets(Fixture f, Combatant wolf, Combatant fighter, Position wolfCell,
            int spent, boolean fighterReaction) {
        var state = f.storage.accessSnapshot().snapshot().domains();
        var battle = CampaignSpaceCommands.currentBattle(state).current();
        var currentWolf = combatant(battle, wolf.characterId());
        var currentFighter = combatant(battle, fighter.characterId());
        assertEquals(wolfCell, currentWolf.position());
        assertEquals(wolfCell, CampaignSpaceCommands.position(state, wolf.characterId()).grid());
        assertEquals(spent, currentWolf.movementSpentHalfFeet());
        assertEquals(wolf.actionsRemaining(), currentWolf.actionsRemaining());
        assertEquals(wolf.bonusActionsRemaining(), currentWolf.bonusActionsRemaining());
        assertEquals(wolf.reactionAvailable(), currentWolf.reactionAvailable());
        assertEquals(FIGHTER_START, currentFighter.position());
        assertEquals(FIGHTER_START, CampaignSpaceCommands.position(state, fighter.characterId()).grid());
        assertEquals(fighter.movementSpentHalfFeet(), currentFighter.movementSpentHalfFeet());
        assertEquals(fighter.actionsRemaining(), currentFighter.actionsRemaining());
        assertEquals(fighter.bonusActionsRemaining(), currentFighter.bonusActionsRemaining());
        assertEquals(fighterReaction, currentFighter.reactionAvailable());
    }

    private static Combatant combatant(Fixture f, UUID id) {
        return combatant(f.battle(), id);
    }

    private static Combatant combatant(Battle battle, UUID id) {
        return battle.combatants().stream().filter(actor -> actor.characterId().equals(id)).findFirst().orElseThrow();
    }

    private static CampaignDomainData.Position placement(Fixture f, UUID id) {
        return CampaignSpaceCommands.position(f.storage.accessSnapshot().snapshot().domains(), id);
    }

    private static CampaignDomainData.Root lastRoot(Fixture f) {
        return f.storage.accessSnapshot().snapshot().domains().roots().getLast();
    }

    private static void rejectCombat(Fixture f, Command command, Actor actor, String code, String message) {
        var before = f.storage.accessSnapshot();
        String request = uuid();
        var input = combatInput(before, command);
        var failure = assertThrows(StorageException.class, () -> f.storage.domainCommand(request, before.epoch(),
                before.revision(), "combat-command", input, actor, access -> {}));
        assertEquals(code, failure.code());
        if (message != null) assertEquals(message, failure.getMessage());
        assertEquals(before, f.storage.accessSnapshot());
        var submitted = actor == null
                ? CampaignParticipationCommands.actualActor(before.snapshot().domains(), "combat-command", input) : actor;
        assertTrue(f.storage.exactDomainReceipt(request, before.runId(), before.revision(), "combat-command", input,
                submitted, access -> {}).isEmpty());
    }

    private static void assertArrowPayment(Fixture f, AttackAttempt attack, Set<UUID> previousArrows) {
        UUID archer = attack.actorId();
        UUID arrow = attack.rangedAttack().ammunitionItemId();
        assertTrue(previousArrows.contains(arrow));
        var remaining = new HashSet<>(previousArrows);
        remaining.remove(arrow);
        assertEquals(remaining, f.arrowIds(archer));
        var child = f.history(archer).events().getLast();
        var root = f.state().roots().getLast();
        var event = f.state().combats().getFirst().events().getLast();
        assertEquals(List.of(new AmmunitionUse(archer, child.before(), child.after(), attack.frameKey(), arrow)),
                event.resolution().ammunitionUses());
        assertEquals(List.of(child.operationId()), root.characterOperations());
        assertEquals(root.characterOperations(), event.characterOperations());
        assertEquals("COMBAT_AMMUNITION_SPENT", child.kind());
        assertEquals(arrow, child.command().equipmentInput().itemId());
        assertEquals(root.operationId(), child.command().combatInput().combatOperationId());
        assertEquals(child.before().version() + 1, child.after().version());
        assertNull(child.healthResult());
        assertEquals(child.before().health(), child.after().health());
    }

    private static void assertPlayerShot(Fixture f, Seat shooter, UUID archer, UUID skeleton, ScriptedDice dice,
            int damageDie, int damage, boolean dropped, int remaining) {
        assertReceipt("ACCEPTED", f.post(shooter, "command", f.command(shooter, f.option(shooter, "开始攻击动作"))));
        var previousArrows = f.arrowIds(archer);
        var request = f.command(shooter, f.option(shooter, "射击 可见骷髅"));
        dice.add(19, 13, damageDie);
        var body = f.post(shooter, "command", request).body();
        assertTrue(dice.isEmpty());
        var receipt = body.getAsJsonObject("receipt");
        assertEquals(Set.of("state", "outcome", "requestId", "label", "attackResult"), receipt.keySet());
        assertEquals("COMPLETED", receipt.get("outcome").getAsString());
        var result = receipt.getAsJsonObject("attackResult");
        assertEquals(Set.of("hit", "damage", "dropped"), result.keySet());
        assertTrue(result.get("hit").getAsBoolean());
        assertEquals(damage, result.get("damage").getAsInt());
        assertEquals(dropped, result.get("dropped").getAsBoolean());
        assertAbsent(receipt, skeleton, "可见骷髅", "armorClass", "hitPoints", "remaining");
        var monster = participants(body.getAsJsonObject("view")).asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> row.get("name").getAsString().equals("可见骷髅")).findFirst().orElseThrow();
        assertEquals(Set.of("name", "position", "current"), monster.keySet());
        var attack = f.latestAttack();
        assertEquals(skeleton, attack.targetId());
        assertEquals(13, attack.armorClass());
        assertTrue(attack.disadvantage());
        assertEquals(13, attack.selectedD20());
        assertEquals(new RangedAttackEvidence(85, 80, 320, false, attack.rangedAttack().ammunitionItemId()), attack.rangedAttack());
        assertEquals(List.of(new CampaignSpaceData.Die(20, 19), new CampaignSpaceData.Die(20, 13),
                new CampaignSpaceData.Die(6, damageDie)), f.state().roots().getLast().rolls());
        assertArrowPayment(f, attack, previousArrows);
        assertEquals(remaining, f.monster(skeleton).hitPoints());
        assertEquals(dropped, f.monster(skeleton).dead());
        var committed = f.storage.accessSnapshot();
        assertEquals(receipt, f.post(shooter, "receipt", receiptIdentity(request)).body().get("receipt"));
        assertStale(f, shooter, request);
        assertEquals(committed, f.storage.accessSnapshot());
    }

    private static void assertForgedArrowsRejected(Fixture f) {
        var before = f.storage.accessSnapshot();
        var codec = new CampaignCodec();
        byte[] original = codec.encode(new CampaignData.Document(uuid(), before.snapshot()));
        var forged = CampaignJson.read(original);
        var history = forged.getAsJsonObject("domains").getAsJsonArray("combats").get(0).getAsJsonObject();
        var resolution = history.getAsJsonArray("events").asList().getLast().getAsJsonObject().getAsJsonObject("resolution");
        var use = resolution.getAsJsonArray("ammunitionUses").get(0).getAsJsonObject();
        use.addProperty("itemId", uuid());
        var integrity = forged.remove("integrity").getAsJsonObject();
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(forged)));
        forged.add("integrity", integrity);
        assertEquals("INVALID_FILE", assertThrows(StorageException.class,
                () -> codec.decode(CampaignJson.canonical(forged))).code());
    }

    private static JsonObject combatInput(CampaignStorageService.AccessSnapshot access, Command command) {
        var state = access.snapshot().domains();
        var current = CampaignSpaceCommands.currentBattle(state).current();
        var input = object("combatId", current.combatId().toString(), "combatVersion", Long.toString(current.version()));
        input.add("focus", CampaignSpaceCommands.focusPayload(state));
        input.add("command", CampaignDomainCodec.toJson(command));
        return input;
    }

    private static void finishAndOpen(Fixture f) throws Exception {
        var before = f.storage.accessSnapshot();
        String manual = f.storage.save(uuid(), before.epoch(), before.revision()).fileKey();
        String ended = f.storage.end(uuid(), before.epoch(), before.revision()).fileKey();
        assertEquals("IDLE", f.storage.status().state());
        var codec = new CampaignCodec();
        assertEquals(before.snapshot(), codec.decode(Files.readAllBytes(f.storage.savedFile(manual))).snapshot());
        assertEquals(before.snapshot(), codec.decode(Files.readAllBytes(f.storage.savedFile(ended))).snapshot());
        f.storage.open(ended);
        var reopened = f.storage.accessSnapshot();
        assertNotEquals(before.runId(), reopened.runId());
        assertEquals(before.snapshot(), reopened.snapshot());
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

    private static List<String> shotLabels(JsonObject view) {
        return labels(view).stream().filter(label -> label.startsWith("射击 ")).toList();
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

    private record WolfMovement(UUID wolf, UUID fighter, Seat owner, Seat observer) { }

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
            this(name, dice, ignored -> { });
        }
        Fixture(String name, ScriptedDice dice, CampaignStorageService.FaultInjector faults) throws Exception {
            this(new CampaignStorageService(PlayerParticipationAcceptanceTest.this.temporary.resolve(name), faults, dice), true);
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
        UUID character(String name, String kind) { return character(name, kind, "fighter"); }
        UUID character(String name, String kind, String classKey) {
            return UUID.fromString(send("character-create", object("name", name, "kind", kind, "classKey", classKey,
                    "backgroundKey", "acolyte")).lastCommand().subjectId());
        }
        UUID manual(String name, String kind) { send("manual-create", object("name", name, "kind", kind, "template", "blank")); return storage.accessSnapshot().snapshot().domains().manual().characters().stream().filter(character -> character.current().name().equals(name)).findFirst().orElseThrow().id(); }
        UUID wolf(String name) { return monster(name, "monster.wolf"); }
        UUID monster(String name, String ruleKey) {
            var input = object("name", name);
            input.add("monster", CampaignDomainCodec.toJson(rules.rule("adventure.monster", ruleKey)));
            return UUID.fromString(send("monster-create", input).lastCommand().subjectId());
        }
        CampaignSpaceData.Monster monster(UUID id) {
            return storage.accessSnapshot().snapshot().domains().space().monsters().stream()
                    .filter(monster -> monster.id().equals(id)).findFirst().orElseThrow();
        }
        UUID createScene(UUID world) { return createScene(world, "10", "10"); }
        UUID createScene(UUID world, String width, String height) {
            return UUID.fromString(send("scene-create", object("name", SCENE, "nodeId", world.toString(), "width", width,
                    "height", height, "resolution", "RULES")).lastCommand().subjectId());
        }
        void enterBattle(UUID scene, List<Deployment> placements) {
            enterBattle(scene, placements.getFirst().characterId(), placements);
        }
        void enterBattle(UUID scene, UUID initiator, List<Deployment> placements) {
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
            combat(battle(), Command.reenter(initiator, roster));
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
            UUID item = acquire(pc, "item.longsword");
            if (equip) equip(pc, item, "MAIN_HAND");
        }
        UUID acquire(UUID pc, String ruleKey) {
            var input = new Schema2CharacterRuntime.EquipmentInput(UUID.randomUUID(), rules.rule("equipment.item", ruleKey), "CARRIED", "主持准备");
            return UUID.fromString(equipment(pc, "ITEM_ACQUIRED", input).lastCommand().requestId());
        }
        void equip(UUID pc, UUID item, String location) {
            equipment(pc, "ITEM_EQUIPPED", new Schema2CharacterRuntime.EquipmentInput(item, null, location, "主持准备"));
        }
        CampaignStorageService.CampaignView equipment(UUID pc, String kind, Schema2CharacterRuntime.EquipmentInput input) {
            return characterCommand(pc, new Schema2CharacterRuntime.Command(kind, null, 0, null, null, null,
                    Schema2CharacterRuntime.GrowthChoices.EMPTY, null, null, input, null));
        }
        CampaignStorageService.CampaignView characterCommand(UUID pc, Schema2CharacterRuntime.Command command) {
            JsonObject input = characterInput(pc);
            input.add("command", CampaignDomainCodec.toJson(command));
            return send("character-command", input);
        }
        JsonObject characterInput(UUID pc) { return object("characterId", pc.toString(), "characterVersion", Long.toString(card(pc).version())); }
        CampaignDomainData.State state() { return storage.accessSnapshot().snapshot().domains(); }
        Schema2CharacterRuntime.History history(UUID id) {
            return state().characters().stream().filter(character -> character.id().equals(id)).findFirst().orElseThrow().history();
        }
        Schema2CharacterRuntime.State card(UUID id) { return history(id).current(); }
        long wizardSlots(UUID wizard) {
            return card(wizard).resources().stream().filter(resource -> resource.identity().ruleKey().equals("resource.wizard.slot1"))
                    .findFirst().orElseThrow().current();
        }
        Set<UUID> arrowIds(UUID archer) {
            Set<UUID> result = new HashSet<>();
            for (var item : card(archer).equipment()) {
                if (!item.item().ruleKey().equals("item.arrow")) continue;
                assertEquals("CARRIED", item.location());
                result.add(item.itemId());
            }
            return result;
        }
        JsonObject check(UUID pc) {
            JsonObject input = characterInput(pc); input.add("command", CampaignDomainCodec.toJson(new Schema2CharacterRuntime.Command("CHECK_RESOLVED", null, 0, null, null, null, Schema2CharacterRuntime.GrowthChoices.EMPTY, null,
                    new Schema2CharacterRuntime.CheckInput(rules.rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, 10, false, false, "固定验收检定")))); return input;
        }
        void combat(Battle previous, Command command) { PlayerSpaceFixture.combat(storage, previous, command); }
        Battle battle() { return state().combats().getFirst().current(); }
        Combatant actor(UUID id) {
            return battle().combatants().stream().filter(actor -> actor.characterId().equals(id)).findFirst().orElseThrow();
        }
        AttackAttempt latestAttack() {
            return battle().flows().reversed().stream().flatMap(flow -> flow.attacks().reversed().stream()).findFirst().orElseThrow();
        }
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
        void advanceSeconds(long seconds) { current = current.plusSeconds(seconds); }
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
