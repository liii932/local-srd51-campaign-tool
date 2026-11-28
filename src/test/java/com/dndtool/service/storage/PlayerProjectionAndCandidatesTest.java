package com.dndtool.service.storage;

import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.RestInput;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.Schema2CombatHistory;
import com.dndtool.service.Schema2PreparationIdentity;
import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.dndtool.service.storage.CampaignParticipationData.*;
import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real current campaign facts; projection and option generation must remain pure and private. */
class PlayerProjectionAndCandidatesTest {
    @TempDir Path temporary;
    private final PlayerCandidateFactory choices = new PlayerCandidateFactory();
    private final PlayerCampaignProjection projection = new PlayerCampaignProjection();

    @Test void sharedSceneKeepsPositionsAndDirectTargetsRelativeToTheActingObserver() {
        var fixture = new ObservationFixture();
        var unshared = fixture.access(false, false, null);
        assertDirectTargetForbidden(unshared, fixture.player, fixture.cast(fixture.first));
        assertDirectTargetForbidden(unshared, fixture.player, fixture.grapple(fixture.first));

        var clear = fixture.access(true, false, null);
        var cast = fixture.cast(fixture.first);
        var grapple = fixture.grapple(fixture.first);
        assertDoesNotThrow(() -> requireExecutionPermission(clear, fixture.player, cast));
        assertDoesNotThrow(() -> requireExecutionPermission(clear, fixture.player, grapple));

        var obscured = fixture.access(true, true, null);
        var view = sharedScene(obscured, fixture.player);
        assertFalse(view.has("participants"));
        var perspectives = view.getAsJsonArray("perspectives");
        assertEquals(2, perspectives.size());
        var first = named(perspectives, "observer", "第一观察者").getAsJsonArray("participants");
        var second = named(perspectives, "observer", "第二观察者").getAsJsonArray("participants");
        var remembered = named(first, "name", "目标");
        assertEquals("3, 1", remembered.get("position").getAsString());
        assertTrue(remembered.get("lastKnown").getAsBoolean());
        var seen = named(second, "name", "目标");
        assertEquals("4, 1", seen.get("position").getAsString());
        assertFalse(seen.has("lastKnown"));
        assertFalse(view.toString().contains("未纳入观察的放置"));
        assertTrue(sharedScene(obscured, fixture.spectator).getAsJsonArray("participants").isEmpty());
        assertTrue(choices.candidates(obscured, fixture.spectator).isEmpty());

        assertDirectTargetForbidden(obscured, fixture.player, cast);
        assertDirectTargetForbidden(obscured, fixture.player, grapple);
        assertDoesNotThrow(() -> requireExecutionPermission(obscured, fixture.player, fixture.cast(fixture.second)));
        assertDoesNotThrow(() -> requireExecutionPermission(obscured, fixture.player, fixture.grapple(fixture.second)));
    }

    @Test void lastKnownRowsDoNotRevealTheHiddenSubjectsInitiativeOrder() {
        var fixture = new ObservationFixture();
        var base = fixture.access(true, true, null);
        var earlier = sharedScene(fixture.withTargetInitiative(base, 18), fixture.player).getAsJsonArray("perspectives");
        var later = sharedScene(fixture.withTargetInitiative(base, 5), fixture.player).getAsJsonArray("perspectives");
        var firstEarlier = named(earlier, "observer", "第一观察者").getAsJsonArray("participants");
        var firstLater = named(later, "observer", "第一观察者").getAsJsonArray("participants");
        assertTrue(named(firstEarlier, "name", "目标").get("lastKnown").getAsBoolean());
        assertEquals(firstEarlier, firstLater);
        var secondEarlier = named(earlier, "observer", "第二观察者").getAsJsonArray("participants");
        var secondLater = named(later, "observer", "第二观察者").getAsJsonArray("participants");
        assertEquals("目标", secondEarlier.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("目标", secondLater.asList().getLast().getAsJsonObject().get("name").getAsString());
    }

    @Test void monsterPendingUsesTheCurrentDecisionMakersSightEvenWithAnotherOwnedObserver() {
        var fixture = new ObservationFixture();
        var blocked = fixture.access(true, true, fixture.first);
        var pending = projection.project(blocked, fixture.player).getAsJsonObject("pending");
        assertFalse(pending.get("active").getAsBoolean());
        assertFalse(pending.get("own").getAsBoolean());
        assertFalse(pending.toString().contains("移动怪物"));
        assertTrue(choices.candidates(blocked, fixture.player).isEmpty());
        assertDirectTargetForbidden(blocked, fixture.player, passCurrentReaction(blocked, fixture.first));
        assertDirectTargetForbidden(blocked, fixture.player, passCurrentReaction(blocked, fixture.second));

        var visible = fixture.access(true, true, fixture.second);
        pending = projection.project(visible, fixture.player).getAsJsonObject("pending");
        assertTrue(pending.get("active").getAsBoolean());
        assertTrue(pending.get("own").getAsBoolean());
        assertTrue(pending.get("description").getAsString().contains("移动怪物（5, 3）"));
        assertDoesNotThrow(() -> requireExecutionPermission(visible, fixture.player, passCurrentReaction(visible, fixture.second)));
    }

    @Test void counterspellCandidateTargetsTheSharedCantripCasterAndRevocationRejectsDirectSubmission() {
        var snapshot = CampaignRuleFixtures.snapshot(UUID.randomUUID().toString(), "戏法反制");
        snapshot = apply(snapshot, "node-create", json("name", "营地"));
        UUID world = subject(snapshot);
        snapshot = apply(snapshot, "focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(world)));
        snapshot = apply(snapshot, "character-create",
                json("name", "反制法师", "kind", "PC", "classKey", "wizard", "backgroundKey", "acolyte"));
        UUID wizard = subject(snapshot);
        snapshot = apply(snapshot, "position", json("characterId", wizard.toString(), "nodeId", world.toString()));
        var rules = snapshot.domains().material().restore();
        snapshot = prepareCounterspell(snapshot, wizard, rules);

        snapshot = apply(snapshot, "character-create",
                json("name", "施法牧师", "kind", "NPC", "classKey", "cleric", "backgroundKey", "acolyte"));
        UUID caster = subject(snapshot);
        snapshot = apply(snapshot, "position", json("characterId", caster.toString(), "nodeId", world.toString()));
        snapshot = apply(snapshot, "member-add", json("name", "反制玩家"));
        UUID player = subject(snapshot);
        snapshot = apply(snapshot, "control-set", json("characterId", wizard.toString(), "memberId", player.toString(), "controlVersion", "0"));
        snapshot = apply(snapshot, "scene-create", json("name", "相遇", "nodeId", world.toString(),
                "width", "10", "height", "10", "resolution", "RULES"));
        UUID scene = subject(snapshot);
        JsonObject enter = json("sceneId", scene.toString());
        JsonArray placements = new JsonArray();
        placements.add(json("id", wizard.toString(), "version", "0", "x", "2", "y", "2", "side", "0"));
        placements.add(json("id", caster.toString(), "version", "0", "x", "4", "y", "2", "side", "1"));
        enter.add("subjects", placements);
        snapshot = apply(snapshot, "scene-enter", enter);
        var battle = CampaignSpaceCommands.currentBattle(snapshot.domains()).current();
        snapshot = apply(snapshot, "combat-command", combatInput(battle, Command.reenter(wizard, List.of(wizard, caster))));
        snapshot = apply(snapshot, "share-set", shareInput(player, ShareKind.SCENE, scene));
        battle = CampaignSpaceCommands.currentBattle(snapshot.domains()).current();
        assertEquals(wizard, battle.activeActor());
        snapshot = apply(snapshot, "combat-command", combatInput(battle, Command.action(Kind.END_TURN, wizard)));
        battle = CampaignSpaceCommands.currentBattle(snapshot.domains()).current();
        var sacredFlame = rules.rule("adventure.spell", "spell.sacred_flame");
        var cantrip = Command.cast(caster, new CastInput(rules.rule("character.class_cantrips", "cantrips.cleric"),
                sacredFlame, List.of(wizard), null, 0, null));
        snapshot = apply(snapshot, "combat-command", combatInput(battle, cantrip));
        var flow = CampaignSpaceCommands.currentBattle(snapshot.domains()).current().activeFlow();
        assertEquals(CastStatus.PENDING, flow.casts().getFirst().status());
        assertEquals(sacredFlame, flow.casts().getFirst().spell());
        assertEquals(TriggerKind.CAST_BEFORE_COMPLETION, flow.windows().getFirst().trigger().kind());
        assertEquals(ReactionKind.COUNTERSPELL, flow.windows().getFirst().opportunities().getFirst().options().getFirst().kind());
        assertTrue(counterspellCandidates(access(snapshot), player).isEmpty());

        snapshot = apply(snapshot, "share-set", shareInput(player, ShareKind.CHARACTER, caster));
        var shared = access(snapshot);
        var candidates = counterspellCandidates(shared, player);
        assertEquals(1, candidates.size());
        var option = candidates.getFirst();
        var counter = CampaignDomainCodec.fromJson(option.input().get("command"), Command.class);
        assertEquals(wizard, counter.actorId());
        assertEquals(List.of(caster), counter.castInput().targets());
        assertEquals(flow.rootFrameKey(), counter.castInput().targetCastFrameKey());
        assertEquals(flow.leafFrameKey(), counter.leafKey());
        assertEquals(3, counter.castInput().slotLevel());
        assertDoesNotThrow(() -> requireExecutionPermission(shared, player, counter));

        var retract = shareInput(player, ShareKind.CHARACTER, caster);
        retract.addProperty("shareId", subject(snapshot).toString());
        retract.addProperty("shareVersion", "1");
        retract.addProperty("active", false);
        var revoked = access(apply(snapshot, "share-set", retract));
        assertTrue(counterspellCandidates(revoked, player).isEmpty());
        assertDirectTargetForbidden(revoked, player, counter);

        var resolved = new CampaignDomainCommands().apply(shared.snapshot(), option.action(), option.input(),
                UUID.randomUUID(), "0".repeat(64), sides -> fail("Counterspell automatically counters a cantrip"),
                Actor.player(player, wizard));
        var resolvedFlow = CampaignSpaceCommands.currentBattle(resolved).current().activeFlow();
        assertEquals(CastStatus.COUNTERED, resolvedFlow.casts().getFirst().status());
        assertEquals(CastStatus.RESOLVED, resolvedFlow.casts().getLast().status());
        assertEquals(1, characterHistory(resolved, wizard).current().resources().stream()
                .filter(resource -> resource.identity().ruleKey().equals("resource.wizard.slot3")).findFirst().orElseThrow().current());
    }

    @Test void aSharedManualCardOutsideTheObservationRosterDoesNotChangeMovementCandidates() {
        var snapshot = CampaignRuleFixtures.snapshot(UUID.randomUUID().toString(), "手工卡占位");
        snapshot = apply(snapshot, "node-create", json("name", "营地"));
        UUID world = subject(snapshot);
        snapshot = apply(snapshot, "focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(world)));
        snapshot = apply(snapshot, "character-create", json("name", "移动的战士", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
        UUID fighter = subject(snapshot);
        snapshot = apply(snapshot, "character-create", json("name", "远处的守卫", "kind", "NPC", "classKey", "fighter", "backgroundKey", "acolyte"));
        UUID guard = subject(snapshot);
        snapshot = apply(snapshot, "manual-create", json("name", "已共享手工卡", "kind", "NPC", "template", "blank"));
        UUID manual = subject(snapshot);
        snapshot = apply(snapshot, "member-add", json("name", "移动玩家"));
        UUID player = subject(snapshot);
        snapshot = apply(snapshot, "control-set", json("characterId", fighter.toString(), "memberId", player.toString(), "controlVersion", "0"));
        for (UUID actor : List.of(fighter, guard, manual))
            snapshot = apply(snapshot, "position", json("characterId", actor.toString(), "nodeId", world.toString()));
        snapshot = apply(snapshot, "scene-create", json("name", "局部场景", "nodeId", world.toString(),
                "width", "10", "height", "10", "resolution", "RULES"));
        UUID scene = subject(snapshot);
        var enter = json("sceneId", scene.toString());
        var placements = new JsonArray();
        placements.add(json("id", fighter.toString(), "version", "0", "x", "2", "y", "2", "side", "0"));
        placements.add(json("id", guard.toString(), "version", "0", "x", "8", "y", "8", "side", "1"));
        placements.add(json("id", manual.toString(), "version", "0", "x", "3", "y", "2", "side", "1"));
        enter.add("subjects", placements);
        snapshot = apply(snapshot, "scene-enter", enter);
        var battle = CampaignSpaceCommands.currentBattle(snapshot.domains()).current();
        snapshot = apply(snapshot, "combat-command", combatInput(battle, Command.reenter(fighter, List.of(fighter, guard))));
        snapshot = apply(snapshot, "share-set", shareInput(player, ShareKind.SCENE, scene));
        snapshot = apply(snapshot, "share-set", shareInput(player, ShareKind.CHARACTER, manual));
        battle = CampaignSpaceCommands.currentBattle(snapshot.domains()).current();
        assertEquals(fighter, battle.activeActor());
        assertTrue(battle.combatants().stream().noneMatch(c -> c.characterId().equals(manual)));

        var eastOccupied = access(snapshot);
        var eastOptions = movementOptions(eastOccupied, player);
        var moveManual = json("characterId", manual.toString(), "version", "1", "minutes", "0", "adjudication", "手工卡绕过战士");
        JsonArray path = new JsonArray();
        path.add(json("x", "2", "y", "1"));
        path.add(json("x", "1", "y", "2"));
        moveManual.add("path", path);
        snapshot = apply(snapshot, "space-move", moveManual);
        var westOccupied = access(snapshot);
        var westOptions = movementOptions(westOccupied, player);
        var view = projection.project(eastOccupied, player);
        assertTrue(view.getAsJsonObject("shared").getAsJsonArray("cards").toString().contains("已共享手工卡"));
        assertEquals(view, projection.project(westOccupied, player));
        assertEquals(eastOptions.keySet(), westOptions.keySet());
        eastOptions.forEach((label, option) -> assertEquals(option.input().get("command"), westOptions.get(label).input().get("command")));
        String east = "向东移动一格", west = "向西移动一格";
        assertTrue(eastOptions.keySet().containsAll(List.of(east, west)));
        assertEquals("SCENE_CELL_OCCUPIED", assertThrows(StorageException.class,
                () -> submitMovement(eastOccupied, player, fighter, eastOptions.get(east))).code());
        assertEquals("SCENE_CELL_OCCUPIED", assertThrows(StorageException.class,
                () -> submitMovement(westOccupied, player, fighter, westOptions.get(west))).code());
        var openEast = submitMovement(westOccupied, player, fighter, westOptions.get(east));
        assertEquals(new Position(3, 2), CampaignSpaceCommands.position(openEast, fighter).grid());
    }

    @Test void ownCardSharedAudienceAndRevocationNeverSerializeTheHostGraph() throws Exception {
        try (var service = create("projection")) {
            UUID pc = character(service, "自己的战士", "PC"), npc = character(service, "秘密守卫和隐藏剧情", "NPC");
            UUID a = member(service, "同名"), b = member(service, "同名"); assign(service, pc, a);
            command(service, "note", json("text", "秘密DC与主持便笺"));
            String before = service.current().domain().toString();
            var access = service.accessSnapshot(); JsonObject view = projection.project(access, a);
            assertEquals("冒险房间",view.get("campaignName").getAsString(),"a private campaign title is not a shared description");
            assertEquals(1, view.getAsJsonArray("cards").size()); assertTrue(choices.candidates(access,a).isEmpty());
            assertEquals(0, projection.project(access,b).getAsJsonArray("cards").size());
            assertFalse(view.toString().contains("秘密")); assertNoPrivateKeys(view);
            assertEquals(before, service.current().domain().toString(), "reads must not write facts or roll dice");

            JsonObject share = json("shareVersion","0","kind","CHARACTER","subjectId",npc.toString());
            share.add("shareId",JsonNull.INSTANCE); share.addProperty("active",true);
            share.add("audience",CampaignDomainCodec.toJson(new Audience(AudienceKind.SPECIFIC,List.of(a))));
            command(service,"share-set",share);
            assertTrue(projection.project(service.accessSnapshot(),a).toString().contains("秘密守卫和隐藏剧情"));
            assertFalse(projection.project(service.accessSnapshot(),b).toString().contains("秘密守卫和隐藏剧情"));
            assertNoPrivateKeys(projection.project(service.accessSnapshot(),a));
            var policy = service.accessSnapshot().snapshot().domains().participation().shares().getFirst();
            share.addProperty("shareId",policy.id().toString()); share.addProperty("shareVersion",Long.toString(policy.version())); share.addProperty("active",false);
            command(service,"share-set",share);
            assertFalse(projection.project(service.accessSnapshot(),a).toString().contains("秘密守卫和隐藏剧情"));

            acquire(service,pc); UUID home = PlayerSpaceFixture.placeAtWorld(service, pc); permit(service,pc,home); access = service.accessSnapshot();
            List<PlayerCandidateFactory.Candidate> candidates = choices.candidates(access,a);
            assertFalse(candidates.isEmpty()); assertTrue(candidates.stream().allMatch(c -> c.characterId().equals(pc.toString()) && c.action().equals("character-command")));
            assertEquals(candidates.stream().map(PlayerCandidateFactory.Candidate::id).toList(),choices.candidates(access,a).stream().map(PlayerCandidateFactory.Candidate::id).toList());
            var choice = candidates.getFirst(); JsonObject escaped = choice.input(); escaped.addProperty("spoof",true); assertFalse(choice.input().has("spoof"));
            UUID background = PlayerSpaceFixture.node(service, "后台节点");
            PlayerSpaceFixture.focus(service, CampaignSpaceData.Ref.world(background));
            assertTrue(choices.candidates(service.accessSnapshot(), a).isEmpty());
            PlayerSpaceFixture.focus(service, CampaignSpaceData.Ref.world(home));
            var returned = service.accessSnapshot();
            assertEquals("STALE_FOCUS", assertThrows(StorageException.class, () -> service.domainCommand(
                    UUID.randomUUID().toString(), returned.epoch(), returned.revision(), choice.action(), choice.input(),
                    Actor.player(a, pc), current -> { })).code());
            assertEquals(returned, service.accessSnapshot());
            assertNotEquals(choice.id(), choices.candidates(returned, a).getFirst().id());
            long controlVersion = access.snapshot().domains().participation().control(pc).version();
            command(service,"control-set",json("characterId",pc.toString(),"memberId",b.toString(),"controlVersion",Long.toString(controlVersion)));
            assertEquals(0,projection.project(service.accessSnapshot(),a).getAsJsonArray("cards").size());
            assertTrue(choices.candidates(service.accessSnapshot(),a).isEmpty());
        }
    }

    @Test void currentLeafOnlyAndOriginalWaitingReceiptSurviveLaterContinuation() throws Exception {
        try (var service = create("flow")) {
            UUID pc = character(service,"战士","PC"), npc = character(service,"隐藏守卫","NPC"), member = member(service,"玩家");
            UUID spectator = member(service,"尚未分配角色的成员");
            assign(service,pc,member); equip(service,pc); equip(service,npc);
            PlayerSpaceFixture.beginBattle(service, pc, npc);
            Battle battle = battle(service);
            if (battle.activeActor().equals(npc)) combat(service,battle,Command.action(Kind.END_TURN,npc));
            var access = service.accessSnapshot(); var offered = choices.candidates(access,member);
            assertTrue(offered.stream().anyMatch(c -> c.label().equals("结束我的回合")));
            assertFalse(offered.stream().anyMatch(c -> c.label().contains("隐藏守卫")));
            assertFalse(offered.stream().anyMatch(c -> c.label().equals("向北移动一格")));
            var move = offered.stream().filter(c -> c.label().equals("向西移动一格")).findFirst().orElseThrow();
            String request = UUID.randomUUID().toString();
            service.domainCommand(request,access.epoch(),access.revision(),move.action(),move.input(),Actor.player(member,pc),current -> { });
            var root = service.accessSnapshot().snapshot().domains().roots().getLast();
            assertEquals("WAITING",projection.receipt(service.accessSnapshot(),member,root).get("outcome").getAsString());
            assertTrue(choices.candidates(service.accessSnapshot(),member).isEmpty(),"a player's turn does not authorize another actor's reaction");
            assertFalse(projection.project(service.accessSnapshot(),spectator).getAsJsonObject("pending").get("active").getAsBoolean(),"unrelated members cannot observe hidden pending flows");
            var pendingScene = CampaignSpaceData.Ref.scene(battle.combatId());
            UUID background = PlayerSpaceFixture.node(service, "另一个地点");
            PlayerSpaceFixture.focus(service, CampaignSpaceData.Ref.world(background));
            var frozen = projection.project(service.accessSnapshot(), member).getAsJsonObject("pending");
            assertTrue(frozen.get("frozen").getAsBoolean());
            assertFalse(frozen.get("active").getAsBoolean());
            assertTrue(choices.candidates(service.accessSnapshot(), member).isEmpty());
            assertFalse(projection.project(service.accessSnapshot(), spectator).getAsJsonObject("pending").get("frozen").getAsBoolean());
            assertEquals("WAITING", projection.receipt(service.accessSnapshot(), member, root).get("outcome").getAsString());
            PlayerSpaceFixture.focus(service, pendingScene);
            battle = battle(service); Flow flow = battle.activeFlow(); Window window = flow.windows().getLast();
            var opportunity = window.opportunities().stream().filter(o -> o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();
            combat(service,battle,Command.reaction(Kind.PASS_REACTION,npc,null,flow.flowKey(),flow.leafFrameKey(),window.windowKey(),opportunity.opportunityKey()));
            var resume = choices.candidates(service.accessSnapshot(),member).stream().filter(c -> c.label().contains("继续")).findFirst().orElseThrow();
            var now = service.accessSnapshot(); service.domainCommand(UUID.randomUUID().toString(),now.epoch(),now.revision(),resume.action(),resume.input(),Actor.player(member,pc),current -> { });
            assertNull(battle(service).activeFlow());
            assertEquals("WAITING",projection.receipt(service.accessSnapshot(),member,root).get("outcome").getAsString(),"receipt describes the original acceptance, never today's flow");
            assertNoPrivateKeys(projection.project(service.accessSnapshot(),member));
        }
    }

    @Test void combatMovementPreviewUsesSharedBoundsKnownOccupancyAndTheActorsBudget() throws Exception {
        try (var service = create("movement-preview")) {
            UUID pc = character(service, "玩家战士", "PC"), npc = character(service, "场景守卫", "NPC");
            UUID player = member(service, "玩家");
            assign(service, pc, player);
            UUID anchor = PlayerSpaceFixture.placeAtWorld(service, pc, npc);
            var created = command(service, "scene-create", json("name", "靠近边界", "nodeId", anchor.toString(),
                    "width", "10", "height", "201", "resolution", "RULES"));
            UUID scene = UUID.fromString(created.lastCommand().requestId());
            var placements = new JsonArray();
            placements.add(json("id", pc.toString(), "version", "0", "x", "9", "y", "200", "side", "0"));
            placements.add(json("id", npc.toString(), "version", "0", "x", "8", "y", "200", "side", "1"));
            var enter = json("sceneId", scene.toString());
            enter.add("subjects", placements);
            command(service, "scene-enter", enter);
            combat(service, battle(service), Command.reenter(pc, List.of(pc, npc)));
            if (battle(service).activeActor().equals(npc)) combat(service, battle(service), Command.action(Kind.END_TURN, npc));

            assertFalse(labels(service, player).contains("向南移动一格"));
            assertTrue(labels(service, player).contains("向东移动一格"));
            share(service, player, ShareKind.SCENE, scene);
            assertFalse(labels(service, player).contains("向东移动一格"));
            assertTrue(labels(service, player).contains("向西移动一格"));
            share(service, player, ShareKind.CHARACTER, npc);
            assertFalse(labels(service, player).contains("向西移动一格"));

            for (int y = 199; y >= 194; y--) {
                assertTrue(labels(service, player).contains("向北移动一格"));
                combat(service, battle(service), Command.move(pc, List.of(new Position(9, y))));
            }
            assertTrue(labels(service, player).stream().noneMatch(label -> label.endsWith("移动一格")));
        }
    }

    @Test void eitherSupportedRaceCanApplyOrIgnoreTheCustomHeavyArmorPenalty() throws Exception {
        try (var service = new CampaignStorageService(temporary.resolve("card-speed"))) {
            CampaignRuleFixtures.createWithArmorMovement(service, "测试战役", 30, "APPLY", "IGNORE", 15, 17);
            UUID player = member(service, "玩家");
            var actors = new ArrayList<UUID>();
            for (String race : List.of("human", "dwarf")) {
                var created = command(service, "character-create", json("name", race, "kind", "PC", "classKey", "cleric",
                        "backgroundKey", "acolyte", "raceKey", race));
                UUID actor = UUID.fromString(created.lastCommand().subjectId());
                actors.add(actor);
                assign(service, actor, player);
            }
            assertEquals(Map.of("human", 30, "dwarf", 30), cardSpeeds(service, player));

            PlayerSpaceFixture.placeAtWorld(service, actors.toArray(UUID[]::new));
            var domain = service.accessSnapshot().snapshot().domains();
            var plate = domain.material().restore().rule("equipment.item", "item.plate");
            for (var character : domain.characters()) {
                assertEquals(13, character.history().current().abilities().get("ability.strength"));
                var acquire = new EquipmentInput(UUID.randomUUID(), plate, "CARRIED", "取得板甲");
                characterCommand(service, character.id(), new Schema2CharacterRuntime.Command("ITEM_ACQUIRED", null, 0, null, null, null,
                        GrowthChoices.EMPTY, null, null, acquire, null));
                var current = characterHistory(service.accessSnapshot().snapshot().domains(), character.id()).current();
                var wear = new EquipmentInput(current.equipment().getFirst().itemId(), null, "WORN", "穿戴板甲");
                characterCommand(service, character.id(), new Schema2CharacterRuntime.Command("ITEM_EQUIPPED", null, 0, null, null, null,
                        GrowthChoices.EMPTY, null, null, wear, null));
            }
            assertEquals(Map.of("human", 30, "dwarf", 15), cardSpeeds(service, player));
        }
    }

    @Test void sceneCandidatesUseTheCurrentRosterAndSceneClockAfterDeparture() throws Exception {
        try (var service = create("scene-clock")) {
            UUID pc = character(service, "仍在场的战士", "PC");
            UUID departed = character(service, "已离场的斥候", "PC");
            UUID opponent = character(service, "先行的守卫", "NPC");
            UUID player = member(service, "玩家");
            assign(service, pc, player); assign(service, departed, player);
            UUID anchor = PlayerSpaceFixture.placeAtWorld(service, opponent);
            command(service, "space-time", json("minutes", "1", "adjudication", "守卫先到营地巡查"));
            PlayerSpaceFixture.placeAtWorld(service, pc, departed);
            var created = command(service, "scene-create", json("name", "会合场景", "nodeId", anchor.toString(),
                    "width", "6", "height", "6", "resolution", "RULES"));
            UUID scene = UUID.fromString(created.lastCommand().requestId());
            var enter = json("sceneId", scene.toString());
            var subjects = new JsonArray();
            subjects.add(json("id", pc.toString(), "version", "0", "x", "1", "y", "0", "side", "0"));
            subjects.add(json("id", opponent.toString(), "version", "0", "x", "2", "y", "0", "side", "1"));
            subjects.add(json("id", departed.toString(), "version", "0", "x", "3", "y", "0", "side", "0"));
            enter.add("subjects", subjects);
            command(service, "scene-enter", enter);
            var leaving = new JsonArray();
            leaving.add(json("id", departed.toString(), "version", "1"));
            var leave = new JsonObject(); leave.add("subjects", leaving);
            command(service, "scene-leave", leave);
            PlayerSpaceFixture.focus(service, CampaignSpaceData.Ref.scene(scene));
            combat(service, battle(service), Command.reenter(pc, List.of(pc, opponent)));
            if (battle(service).activeActor().equals(opponent)) combat(service, battle(service), Command.action(Kind.END_TURN, opponent));

            var access = service.accessSnapshot();
            var domain = access.snapshot().domains();
            var history = CampaignSpaceCommands.currentBattle(domain);
            assertTrue(history.members().stream().anyMatch(m -> m.characterId().equals(departed)));
            assertFalse(history.current().combatants().stream().anyMatch(c -> c.characterId().equals(departed)));
            assertEquals(CampaignSpaceData.Ref.world(anchor), CampaignSpaceCommands.position(domain, departed).space());
            assertEquals(0, domain.space().clock(CampaignSpaceData.Ref.scene(scene)).second());
            assertEquals(0, characterHistory(domain, pc).current().rest().minute());
            assertEquals(1, characterHistory(domain, opponent).current().rest().minute());
            var offered = choices.candidates(access, player);
            assertTrue(offered.stream().allMatch(c -> c.characterId().equals(pc.toString())));
            var endTurn = offered.stream().filter(c -> c.label().equals("结束我的回合")).findFirst().orElseThrow();
            service.domainCommand(UUID.randomUUID().toString(), access.epoch(), access.revision(),
                    endTurn.action(), endTurn.input(), Actor.player(player, pc), current -> { });
            assertEquals(characterHistory(domain, departed), characterHistory(service.accessSnapshot().snapshot().domains(), departed));
        }
    }

    private static Schema2CharacterRuntime.History characterHistory(CampaignDomainData.State domain, UUID id) {
        return domain.characters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().history();
    }
    private static List<PlayerCandidateFactory.Candidate> counterspellCandidates(CampaignStorageService.AccessSnapshot access, UUID player) {
        return PlayerCandidateFactory.candidates(access, player).stream()
                .filter(option -> option.action().equals("combat-command"))
                .filter(option -> CampaignDomainCodec.fromJson(option.input().get("command"), Command.class).kind()==Kind.DECLARE_COUNTERSPELL)
                .toList();
    }
    private static CampaignData.Snapshot prepareCounterspell(CampaignData.Snapshot snapshot, UUID wizard, CampaignRuleContext rules) {
        var profile = rules.rule("character.wizard_spellcasting", "spellcasting.wizard");
        var prepared = rules.initialWizardPrepared();
        snapshot = applyCharacter(snapshot, wizard, Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_INITIALIZED",
                new SpellbookInput(profile, rules.initialWizardBook(), prepared)));
        var learning = List.of(List.of("detect_magic", "disguise_self"), List.of("grease", "mage_armor"),
                List.of("magic_missile", "sleep"), List.of("counterspell", "fog_cloud"));
        for (int level = 2; level <= 5; level++) {
            var subclass = level==2 ? rules.rule("character.subclass", "subclass.evocation") : null;
            var increases = level==4 ? Map.of("ability.intelligence", 2) : Map.<String, Integer>of();
            snapshot = applyCharacter(snapshot, wizard, new Schema2CharacterRuntime.Command("CHARACTER_ADVANCED", null, 0,
                    null, "FIXED_AVERAGE", rules.rule("character.class", "class.wizard"),
                    new GrowthChoices(subclass, increases, null, Map.of())));
            var learned = learning.get(level-2).stream().map(spell -> rules.rule("adventure.spell", "spell." + spell)).toList();
            snapshot = applyCharacter(snapshot, wizard, Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_ADVANCED",
                    new SpellbookInput(profile, learned, prepared)));
        }
        snapshot = applyCharacter(snapshot, wizard, rest("REST_STARTED", new RestInput("LONG_REST", null, 0, 0, Map.of())));
        snapshot = applyCharacter(snapshot, wizard, rest("TIME_ADVANCED", new RestInput(null, "SLEEP", 480, 0, Map.of())));
        snapshot = applyCharacter(snapshot, wizard, rest("REST_COMPLETED", new RestInput(null, null, 0, 0, Map.of())));
        return applyCharacter(snapshot, wizard, Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_PREPARED",
                new SpellbookInput(profile, List.of(), List.of(rules.rule("adventure.spell", "spell.counterspell")))));
    }
    private static Schema2CharacterRuntime.Command rest(String kind, RestInput input) {
        return new Schema2CharacterRuntime.Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, input);
    }
    private static CampaignData.Snapshot applyCharacter(CampaignData.Snapshot snapshot, UUID character,
            Schema2CharacterRuntime.Command command) {
        long version = characterHistory(snapshot.domains(), character).current().version();
        var input = json("characterId", character.toString(), "characterVersion", Long.toString(version));
        input.add("command", CampaignDomainCodec.toJson(command));
        return apply(snapshot, "character-command", input);
    }
    private static CampaignData.Snapshot apply(CampaignData.Snapshot snapshot, String action, JsonObject input) {
        var state = new CampaignDomainCommands().apply(snapshot, action, CampaignSpaceFixtures.focused(snapshot.domains(), input),
                UUID.randomUUID(), "0".repeat(64), sides -> 10);
        return new CampaignData.Snapshot(snapshot.campaignId(), snapshot.name(), state);
    }
    private static UUID subject(CampaignData.Snapshot snapshot) {
        return snapshot.domains().roots().getLast().subjectId();
    }
    private static CampaignStorageService.AccessSnapshot access(CampaignData.Snapshot snapshot) {
        return new CampaignStorageService.AccessSnapshot(snapshot, UUID.randomUUID().toString(), 0, snapshot.domains().roots().size(), new JsonObject());
    }
    private static JsonObject combatInput(Battle battle, Command command) {
        var input = json("combatId", battle.combatId().toString(), "combatVersion", Long.toString(battle.version()));
        input.add("command", CampaignDomainCodec.toJson(command));
        return input;
    }
    private static Map<String, PlayerCandidateFactory.Candidate> movementOptions(CampaignStorageService.AccessSnapshot access, UUID player) {
        var result = new TreeMap<String, PlayerCandidateFactory.Candidate>();
        PlayerCandidateFactory.candidates(access, player).stream().filter(option -> option.label().endsWith("移动一格"))
                .forEach(option -> result.put(option.label(), option));
        return result;
    }
    private static CampaignDomainData.State submitMovement(CampaignStorageService.AccessSnapshot access, UUID player, UUID actor,
            PlayerCandidateFactory.Candidate option) {
        return new CampaignDomainCommands().apply(access.snapshot(), option.action(), option.input(), UUID.randomUUID(),
                "0".repeat(64), sides -> fail("Movement occupancy must be resolved before dice"), Actor.player(player, actor));
    }

    private static JsonObject sharedScene(CampaignStorageService.AccessSnapshot access, UUID player) {
        return PlayerCampaignProjection.project(access, player).getAsJsonObject("shared").getAsJsonArray("scenes").get(0).getAsJsonObject();
    }
    private static JsonObject named(JsonArray rows, String field, String value) {
        return rows.asList().stream().map(JsonElement::getAsJsonObject).filter(row -> value.equals(row.get(field).getAsString()))
                .findFirst().orElseThrow();
    }
    private static Command passCurrentReaction(CampaignStorageService.AccessSnapshot access, UUID actor) {
        var flow = CampaignSpaceCommands.currentBattle(access.snapshot().domains()).current().activeFlow();
        var window = flow.windows().getFirst();
        return Command.reaction(Kind.PASS_REACTION, actor, null, flow.flowKey(), flow.leafFrameKey(), window.windowKey(),
                window.opportunities().getFirst().opportunityKey());
    }
    private static void requireExecutionPermission(CampaignStorageService.AccessSnapshot access, UUID player, Command command) {
        JsonObject input = new JsonObject();
        input.add("command", CampaignDomainCodec.toJson(command));
        CampaignParticipationCommands.requireExecutionPermission(access.snapshot().domains(), "combat-command", input,
                Actor.player(player, command.actorId()));
    }
    private static void assertDirectTargetForbidden(CampaignStorageService.AccessSnapshot access, UUID player, Command command) {
        var domain = access.snapshot().domains();
        var battle = CampaignSpaceCommands.currentBattle(domain).current();
        JsonObject input = json("combatId", battle.combatId().toString(), "combatVersion", Long.toString(battle.version()));
        input.add("focus", CampaignSpaceCommands.focusPayload(domain));
        input.add("command", CampaignDomainCodec.toJson(command));
        assertEquals("PLAYER_COMMAND_FORBIDDEN", assertThrows(StorageException.class, () -> new CampaignDomainCommands()
                .apply(access.snapshot(), "combat-command", input, UUID.randomUUID(), "0".repeat(64),
                        sides -> fail("Unauthorized command must be rejected before draws"), Actor.player(player, command.actorId()))).code());
    }

    private static final class ObservationFixture {
        final UUID campaign = UUID.randomUUID(), node = UUID.randomUUID(), scene = UUID.randomUUID();
        final UUID player = UUID.randomUUID(), spectator = UUID.randomUUID();
        final UUID first = UUID.randomUUID(), second = UUID.randomUUID(), target = UUID.randomUUID();
        final UUID monster = UUID.randomUUID(), untracked = UUID.randomUUID();
        final CampaignRuleContext rules = CampaignRuleFixtures.rules();
        final CampaignDomainData.Material material = CampaignDomainData.Material.of(rules.projection());
        final Schema2PreparationIdentity preparation = Schema2PreparationIdentity.random();
        final CampaignSpaceData.Ref sceneRef = CampaignSpaceData.Ref.scene(scene);
        final List<CampaignDomainData.Character> roster = List.of(person(first, "第一观察者", "PC"),
                person(second, "第二观察者", "PC"), person(target, "目标", "NPC"), person(untracked, "未纳入观察的放置", "NPC"));

        Command cast(UUID actor) {
            return Command.cast(actor, new CastInput(rules.rule("character.spellcasting", "spellcasting.life_domain"),
                    rules.rule("adventure.spell", "spell.cure_wounds"), List.of(target), null));
        }
        Command grapple(UUID actor) {
            return Command.melee(Kind.GRAPPLE, actor, target, null, null,
                    new MeleeInput(UUID.randomUUID(), 0, null, HandSlot.MAIN_HAND, null, null));
        }
        CampaignStorageService.AccessSnapshot access(boolean shareTarget, boolean obscure, UUID reactor) {
            var monsterRule = rules.rule("adventure.monster", "monster.goblin");
            var positions = List.of(placed(first, 2, 1, 0), placed(second, 4, 2, 0), placed(target, 4, 1, 1),
                    placed(monster, 5, 3, 1), placed(untracked, 8, 8, 1));
            var combatants = positions.stream().filter(position -> !position.characterId().equals(untracked))
                    .map(p -> new Combatant(p.characterId(), p.grid(), p.side(), 1, 1, true, 0, 0, false, false, null, false)).toList();
            var observation = obscure ? obscuredView() : ObservationState.EMPTY;
            var battle = new Battle(scene, campaign, rules.rule("adventure.combat", "combat.standard"), Status.ACTIVE, 1, 1, 0,
                    obscure ? 2 : 1, combatants, List.of(new Initiative(first, 19, 1, 20, 0)),
                    reactor == null ? List.of() : List.of(monsterMovePending(reactor)), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(new MonsterInstance(monster, monsterRule, 7, false, List.of())),
                    List.of(), List.of(), MeleeControl.EMPTY, observation);
            var shares = new ArrayList<>(List.of(share(ShareKind.SCENE, scene), share(ShareKind.MONSTER, monster),
                    share(ShareKind.CHARACTER, untracked)));
            if (shareTarget) shares.add(share(ShareKind.CHARACTER, target));
            var participation = new State(List.of(new Member(player, "玩家", true, 1), new Member(spectator, "旁观成员", true, 1)),
                    List.of(new Control(first, player, 1), new Control(second, player, 1)), shares, List.of());
            var space = new CampaignSpaceData.State(new CampaignSpaceData.Focus(sceneRef, 1),
                    List.of(new CampaignSpaceData.Scene(scene, "观察试验场", node, 10, 10, "RULES", "COMBAT", 1)),
                    List.of(new CampaignSpaceData.Clock(sceneRef, 0, 0)),
                    List.of(new CampaignSpaceData.Monster(monster, "移动怪物", monsterRule, 7, false, false, List.of(), 0)));
            var domain = new CampaignDomainData.State(material, roster, List.of(new Schema2CombatHistory.History(battle, List.of(), List.of())),
                    List.of(new CampaignDomainData.Node(node, "营地", List.of())), positions, List.of(), List.of(),
                    CampaignManualData.State.empty(), participation, space);
            return new CampaignStorageService.AccessSnapshot(new CampaignData.Snapshot(campaign.toString(), "观察", domain),
                    UUID.randomUUID().toString(), 0, 0, new JsonObject());
        }
        CampaignStorageService.AccessSnapshot withTargetInitiative(CampaignStorageService.AccessSnapshot access, int targetRoll) {
            var domain = access.snapshot().domains();
            var history = domain.combats().getFirst();
            var battle = history.current();
            Map<UUID, Integer> rolls = Map.of(first, 20, second, 14, monster, 10, target, targetRoll);
            var ordered = rolls.keySet().stream().sorted(Comparator.comparingInt(rolls::get).reversed()).toList();
            var initiative = new ArrayList<Initiative>();
            for (UUID actor : ordered) initiative.add(new Initiative(actor, rolls.get(actor), 0, rolls.get(actor), initiative.size()));
            var changed = new Battle(battle.combatId(), battle.campaignId(), battle.combatRule(), battle.status(), battle.round(),
                    battle.turnNumber(), battle.activeOrdinal(), battle.version(), battle.combatants(), initiative, battle.flows(),
                    battle.ongoingEffects(), battle.readyPreparations(), battle.triggerFacts(), battle.shields(), battle.concentrationHolders(),
                    battle.environmentZones(), battle.suppressionCauses(), battle.monsters(), battle.saveDamageBatches(), battle.monsterRests(),
                    battle.meleeControl(), battle.observation());
            var state = new CampaignDomainData.State(domain.material(), domain.characters(),
                    List.of(new Schema2CombatHistory.History(changed, history.members(), history.events())), domain.nodes(), domain.positions(),
                    domain.items(), domain.roots(), domain.manual(), domain.participation(), domain.space());
            return new CampaignStorageService.AccessSnapshot(new CampaignData.Snapshot(access.snapshot().campaignId(),
                    access.snapshot().name(), state), access.runId(), access.epoch(), access.revision(), access.localControl());
        }
        private ObservationState obscuredView() {
            UUID hide = UUID.randomUUID();
            var hiding = new Position(4, 1);
            var stealth = rules.rule("character.proficiency", "proficiency.skill.stealth");
            var attempt = new ObservationCheck(hide, Kind.HIDE, target, hiding, rules.checkRule(), stealth, null,
                    false, new ObservationRoll(11, null, 11, 1, 0, false, 12),
                    List.of(new ObservationContest(first, 11, null, true), new ObservationContest(second, 13, null, false)));
            return new ObservationState(List.of(new ObservationCellInput(new Position(3, 2), false, true, Cover.NONE),
                    new ObservationCellInput(hiding, true, false, Cover.NONE)),
                    List.of(new ObservationPair(first, target, new Position(3, 1), hide),
                            new ObservationPair(first, monster, new Position(4, 3), null)), List.of(attempt));
        }
        private CampaignDomainData.Character person(UUID id, String name, String kind) {
            var plan = rules.initialPlan(campaign, id, preparation, "cleric", "acolyte");
            var history = new CampaignDomainEngine().initial(rules.projection(), plan, UUID.randomUUID(),
                    rules.initialAbilities("cleric"), rules.initialChoices("cleric"), UUID.randomUUID());
            return new CampaignDomainData.Character(name, kind, true, plan, history);
        }
        private CampaignDomainData.Position placed(UUID id, int x, int y, int side) {
            return new CampaignDomainData.Position(id, sceneRef, new Position(x, y), side, 1);
        }
        private static Share share(ShareKind kind, UUID subject) {
            return new Share(UUID.randomUUID(), kind, subject, new Audience(AudienceKind.ALL, List.of()), true, 1);
        }
        private Flow monsterMovePending(UUID reactor) {
            UUID root = UUID.randomUUID(), leaf = UUID.randomUUID(), window = UUID.randomUUID(), choice = UUID.randomUUID();
            var trigger = new TriggerFact(UUID.randomUUID(), TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE, root,
                    monster, null, new Position(6, 3), 0);
            var opportunity = new Opportunity(choice, reactor, 0, List.of(), OpportunityStatus.PENDING, leaf, null, null);
            var movement = new Frame(root, null, FrameKind.MOVEMENT, root, Invocation.ROOT, FrameStatus.WAITING_WINDOW,
                    Stage.MOVEMENT_LEAVE_REACH_BEFORE, null, null);
            var selection = new Frame(leaf, root, FrameKind.REACTION_SELECTION, choice, Invocation.REACTION,
                    FrameStatus.PENDING, Stage.MOVEMENT_LEAVE_REACH_BEFORE, window, choice);
            return new Flow(UUID.randomUUID(), root, leaf, FlowStatus.ACTIVE, List.of(movement, selection),
                    List.of(new Window(window, root, 0, 0, WindowStatus.OPEN, List.of(opportunity), trigger)), null, List.of());
        }
    }

    private static Map<String, Integer> cardSpeeds(CampaignStorageService service, UUID player) {
        var speeds = new HashMap<String, Integer>();
        for (var element : PlayerCampaignProjection.project(service.accessSnapshot(), player).getAsJsonArray("cards")) {
            var card = element.getAsJsonObject();
            speeds.put(card.get("name").getAsString(), card.get("speed").getAsInt());
        }
        return speeds;
    }
    private CampaignStorageService create(String folder) throws Exception { var service = new CampaignStorageService(temporary.resolve(folder)); CampaignRuleFixtures.create(service, "测试战役"); return service; }
    private static CampaignStorageService.CampaignView command(CampaignStorageService service,String action,JsonObject input) { return PlayerSpaceFixture.send(service, action, input); }
    private static UUID character(CampaignStorageService service,String name,String kind) { command(service,"character-create",json("name",name,"kind",kind,"classKey","fighter","backgroundKey","acolyte")); return service.accessSnapshot().snapshot().domains().characters().stream().filter(c->c.name().equals(name)).findFirst().orElseThrow().id(); }
    private static UUID member(CampaignStorageService service,String name) { var v=command(service,"member-add",json("name",name)); return UUID.fromString(v.lastCommand().requestId()); }
    private static void assign(CampaignStorageService service,UUID pc,UUID member) { command(service,"control-set",json("characterId",pc.toString(),"memberId",member.toString(),"controlVersion","0")); }
    private static void share(CampaignStorageService service, UUID player, ShareKind kind, UUID subject) {
        command(service, "share-set", shareInput(player, kind, subject));
    }
    private static JsonObject shareInput(UUID player, ShareKind kind, UUID subject) {
        JsonObject share = json("shareVersion", "0", "kind", kind.name(), "subjectId", subject.toString());
        share.add("shareId", JsonNull.INSTANCE);
        share.addProperty("active", true);
        share.add("audience", CampaignDomainCodec.toJson(new Audience(AudienceKind.SPECIFIC, List.of(player))));
        return share;
    }
    private static Set<String> labels(CampaignStorageService service, UUID player) {
        Set<String> result = new HashSet<>();
        PlayerCandidateFactory.candidates(service.accessSnapshot(), player).forEach(choice -> result.add(choice.label()));
        return result;
    }
    private static void permit(CampaignStorageService service,UUID pc,UUID node) { JsonObject p=json("characterId",pc.toString(),"permissionVersion","0","sceneId",node.toString());p.addProperty("active",true);JsonArray actions=new JsonArray();actions.add("CHARACTER_COMMAND");p.add("actions",actions);command(service,"exploration-set",p); }
    private static void acquire(CampaignStorageService service,UUID id) { var c=new Schema2CharacterRuntime.Command("ITEM_ACQUIRED",null,0,null,null,null,Schema2CharacterRuntime.GrowthChoices.EMPTY,null,null,new Schema2CharacterRuntime.EquipmentInput(UUID.randomUUID(),CampaignRuleFixtures.rules().rule("equipment.item","item.longsword"),"CARRIED","主持人准备装备"),null); characterCommand(service,id,c); }
    private static void equip(CampaignStorageService service,UUID id) { acquire(service,id);var item=service.accessSnapshot().snapshot().domains().characters().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow().history().current().equipment().getFirst(); characterCommand(service,id,new Schema2CharacterRuntime.Command("ITEM_EQUIPPED",null,0,null,null,null,Schema2CharacterRuntime.GrowthChoices.EMPTY,null,null,new Schema2CharacterRuntime.EquipmentInput(item.itemId(),null,"MAIN_HAND","准备武器"),null)); }
    private static void characterCommand(CampaignStorageService service,UUID id,Schema2CharacterRuntime.Command c) { var state=service.accessSnapshot().snapshot().domains().characters().stream().filter(v->v.id().equals(id)).findFirst().orElseThrow().history().current(); JsonObject p=json("characterId",id.toString(),"characterVersion",Long.toString(state.version()));p.add("command",CampaignDomainCodec.toJson(c));command(service,"character-command",p); }
    private static void combat(CampaignStorageService service,Battle b,Command c) { PlayerSpaceFixture.combat(service, b, c); }
    private static Battle battle(CampaignStorageService service) { return service.accessSnapshot().snapshot().domains().combats().getFirst().current(); }
    private static JsonObject json(String... values) { JsonObject o=new JsonObject();for(int i=0;i<values.length;i+=2)o.addProperty(values[i],values[i+1]);return o; }
    private static void assertNoPrivateKeys(JsonElement value) {
        if(value.isJsonObject())for(var e:value.getAsJsonObject().entrySet()) { assertFalse(Set.of("epoch","revision","version","roots","history","material","identity","preparationOperationId","difficultyClass","requestDigest","runId","memberIds","before","after").contains(e.getKey()),e.getKey());assertNoPrivateKeys(e.getValue()); }
        else if(value.isJsonArray())value.getAsJsonArray().forEach(PlayerProjectionAndCandidatesTest::assertNoPrivateKeys);
    }
}
