package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.service.Schema2CombatState;
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
    private CampaignStorageService create(String folder) throws Exception { var service = new CampaignStorageService(temporary.resolve(folder)); CampaignRuleFixtures.create(service, "测试战役"); return service; }
    private static CampaignStorageService.CampaignView command(CampaignStorageService service,String action,JsonObject input) { return PlayerSpaceFixture.send(service, action, input); }
    private static UUID character(CampaignStorageService service,String name,String kind) { command(service,"character-create",json("name",name,"kind",kind,"classKey","fighter","backgroundKey","acolyte")); return service.accessSnapshot().snapshot().domains().characters().stream().filter(c->c.name().equals(name)).findFirst().orElseThrow().id(); }
    private static UUID member(CampaignStorageService service,String name) { var v=command(service,"member-add",json("name",name)); return UUID.fromString(v.lastCommand().requestId()); }
    private static void assign(CampaignStorageService service,UUID pc,UUID member) { command(service,"control-set",json("characterId",pc.toString(),"memberId",member.toString(),"controlVersion","0")); }
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
