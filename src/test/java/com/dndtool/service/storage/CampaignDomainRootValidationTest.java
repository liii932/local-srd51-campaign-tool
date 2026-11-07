package com.dndtool.service.storage;

import com.dndtool.service.Schema2CombatState;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.RestInput;
import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.service.storage.CampaignDomainData.*;
import static org.junit.jupiter.api.Assertions.*;

/** Changes keep the final projection and complete child sets coherent; root semantics must reject them. */
class CampaignDomainRootValidationTest {
    @Test void emptyRuleSceneRejectsCombatBeforeDice() {
        Fixture f = new Fixture();
        UUID world = f.world("无人营地");
        f.apply("scene-create", object("name", "空战场", "nodeId", world.toString(), "width", "4", "height", "4", "resolution", "RULES"));
        UUID scene = f.snapshot.domains().roots().getLast().subjectId();
        f.apply("focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.scene(scene)));
        var before = f.snapshot;
        var battle = CampaignSpaceCommands.currentBattle(before.domains()).current();
        JsonObject start = object("combatId", scene.toString(), "combatVersion", Long.toString(battle.version()));
        start.add("command", CampaignDomainCodec.toJson(Schema2CombatState.Command.reenter(null, List.of())));
        assertThrows(RuntimeException.class, () -> new CampaignDomainCommands().apply(before, "combat-command",
                CampaignSpaceFixtures.focused(before.domains(), start), UUID.randomUUID(), "0".repeat(64),
                sides -> { throw new AssertionError("Empty roster reached RNG"); }));
        assertEquals(before, f.snapshot);
    }
    @Test void nodeCreationCannotPrelinkAndLinkCannotRenameDeleteOrUseFutureNodes() {
        Fixture f=new Fixture();UUID a=f.node("甲"),b=f.node("乙");
        Node original=f.snapshot.domains().nodes().stream().filter(n->n.id().equals(b)).findFirst().orElseThrow();
        Node linked=new Node(b,original.name(),List.of(a));
        assertThrows(StorageException.class,()->CampaignData.validate(f.replaceLast(linked,List.of(f.nodeValue(a),linked),f.snapshot.domains().items())));
        f.focus(a);
        f.apply("node-link",object("from",a.toString(),"to",b.toString()));Node old=f.nodeValue(a);
        for(Node forged:List.of(new Node(a,"改名",old.adjacent()),new Node(a,old.name(),List.of()),new Node(a,old.name(),List.of(UUID.randomUUID())))){
            List<Node> nodes=new ArrayList<>(f.snapshot.domains().nodes());nodes.set(nodes.indexOf(old),forged);
            assertThrows(StorageException.class,()->CampaignData.validate(f.replaceLast(forged,nodes,f.snapshot.domains().items())));
        }
    }
    @Test void positionCannotReferenceANodeCreatedByALaterRoot() {
        Fixture f = new Fixture(); UUID actor = f.rich("甲");
        UUID node = f.world("甲节点");
        f.place(node, actor);
        assertThrows(StorageException.class, () -> CampaignData.validate(reorder(f.snapshot, 1, 3)));
    }
    @Test void cachedExactRootPrefixDoesNotAuthorizeDifferentCurrentPlacements() {
        Fixture f = new Fixture();
        UUID actor = f.rich("甲");
        UUID original = f.world("营地"), other = f.node("山路");
        f.place(original, actor);
        var retained = f.snapshot.domains();
        var old = retained.positions().getFirst();
        var forged = new Position(actor, CampaignSpaceData.Ref.world(other), null, old.side(), old.version());
        var altered = CampaignSpaceCommands.withSpace(retained, retained.space(), List.of(forged));
        CampaignDomainValidation.validateSpace(altered);
        assertEquals(retained.roots(), altered.roots());
        assertEquals(f.snapshot, CampaignData.validate(f.snapshot));
        assertThrows(StorageException.class, () -> CampaignData.validate(snapshot(f.snapshot.campaignId(), altered)));
        assertEquals(f.snapshot, CampaignData.validate(f.snapshot));
    }
    @Test void ordinaryItemsCannotInventModuleProvenanceOrChangeOwner() {
        Fixture f=new Fixture();UUID a=f.rich("甲"),b=f.rich("乙");JsonObject input=object("characterId",a.toString(),"name","随身物","quantity","1");input.add("itemId",JsonNull.INSTANCE);f.apply("item",input);
        Item old=f.snapshot.domains().items().getFirst();var template=CampaignManualData.catalog().items().getFirst();Item invented=new Item(old.id(),a,template.name(),1,template.key(),template.description(),true);
        assertThrows(StorageException.class,()->CampaignData.validate(f.replaceLast(invented,f.snapshot.domains().nodes(),List.of(invented))));
        f.apply("item",object("itemId",old.id().toString(),"characterId",a.toString(),"name",old.name(),"quantity","2"));
        Item changed=f.snapshot.domains().items().getFirst(),transferred=new Item(changed.id(),b,changed.name(),changed.quantity(),changed.sourceKey(),changed.description(),changed.active());
        assertThrows(StorageException.class,()->CampaignData.validate(f.replaceLast(transferred,f.snapshot.domains().nodes(),List.of(transferred))));
    }
    @Test void twoUnrelatedChecksCannotBeGroupedUnderOneOrdinaryRoot() {
        Fixture f=new Fixture();UUID id=f.rich("甲");
        f.place(f.world("营地"), id);
        int firstCheck = f.snapshot.domains().roots().size();
        for(int i=0;i<2;i++){var c=f.snapshot.domains().characters().getFirst();JsonObject p=object("characterId",id.toString(),"characterVersion",Long.toString(c.history().current().version()));p.add("command",CampaignDomainCodec.toJson(new Command("CHECK_RESOLVED",null,0,null,null,null,GrowthChoices.EMPTY,null,new CheckInput(CampaignRuleFixtures.rules().rule("adventure.check","check.srd2014"),"ABILITY","ability.strength",null,0,false,false,"DM确认检定。"))));f.apply("character-command",p);}
        var state=f.snapshot.domains();List<Root> roots=new ArrayList<>(state.roots());Root first=roots.get(firstCheck),last=roots.removeLast();List<UUID> children=new ArrayList<>(first.characterOperations());children.addAll(last.characterOperations());roots.set(firstCheck,new Root(first.sequence(),first.operationId(),first.kind(),first.subjectId(),first.intent(),first.before(),first.after(),children,null,first.actor(),first.space(),first.rolls()));
        assertThrows(StorageException.class,()->CampaignData.validate(snapshot(f.snapshot.campaignId(),new State(state.material(),state.characters(),state.combats(),state.nodes(),state.positions(),state.items(),roots,state.manual(),state.participation(),state.space()))));
    }
    @Test void sameSpaceTimeCannotBeSplitBetweenRootsEvenWhenTheFinalClocksMatch() {
        Fixture f=new Fixture();UUID a=f.rich("甲"),b=f.rich("乙");
        f.place(f.world("营地"), a, b);
        int timeIndex = f.snapshot.domains().roots().size();
        var character=f.snapshot.domains().characters().stream().filter(c->c.id().equals(a)).findFirst().orElseThrow();
        JsonObject input=object("characterId",a.toString(),"characterVersion",Long.toString(character.history().current().version()));
        input.add("command",CampaignDomainCodec.toJson(new Command("TIME_ADVANCED",null,0,null,null,null,GrowthChoices.EMPTY,new RestInput(null,"NONE",10,0,Map.of()))));f.apply("character-command",input);f.apply("note",object("text","后续备注"));
        var state=f.snapshot.domains();List<Root> roots=new ArrayList<>(state.roots());Root time=roots.get(timeIndex),note=roots.get(timeIndex+1);UUID continuation=time.characterOperations().stream().filter(id->!id.equals(time.operationId())).findFirst().orElseThrow();
        roots.set(timeIndex,new Root(time.sequence(),time.operationId(),time.kind(),time.subjectId(),time.intent(),"","",List.of(time.operationId()),null,time.actor(),time.space(),time.rolls()));
        roots.set(timeIndex+1,new Root(note.sequence(),continuation,"character-command",b,note.intent(),"","",List.of(continuation),null,CampaignParticipationData.Actor.dm(b,null),note.space(),note.rolls()));
        assertThrows(StorageException.class,()->CampaignData.validate(snapshot(f.snapshot.campaignId(),new State(state.material(),state.characters(),state.combats(),state.nodes(),state.positions(),state.items(),roots,state.manual(),state.participation(),state.space()))));
    }
    private static final class Fixture {
        CampaignData.Snapshot snapshot=snapshot(UUID.randomUUID().toString(),State.initial(BundledCampaignRules.projection()));
        void apply(String action,JsonObject input){State state=new CampaignDomainCommands().apply(snapshot,action,CampaignSpaceFixtures.focused(snapshot.domains(),input),UUID.randomUUID(),"0".repeat(64),sides->10);snapshot=CampaignData.validate(snapshot(snapshot.campaignId(),state));}
        UUID node(String name){apply("node-create",object("name",name));return snapshot.domains().nodes().stream().filter(n->n.name().equals(name)).findFirst().orElseThrow().id();}
        void focus(UUID world) { apply("focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(world))); }
        UUID world(String name) { UUID id = node(name); focus(id); return id; }
        void place(UUID world, UUID... subjects) {
            for (UUID subject : subjects) apply("position", object("characterId", subject.toString(), "nodeId", world.toString()));
        }
        UUID rich(String name){apply("character-create",object("name",name,"kind","PC","classKey","fighter","backgroundKey","acolyte"));return snapshot.domains().characters().stream().filter(c->c.name().equals(name)).findFirst().orElseThrow().id();}
        Node nodeValue(UUID id){return snapshot.domains().nodes().stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();}
        CampaignData.Snapshot replaceLast(Object after,List<Node> nodes,List<Item> items){var state=snapshot.domains();List<Root> roots=new ArrayList<>(state.roots());Root old=roots.getLast();roots.set(roots.size()-1,new Root(old.sequence(),old.operationId(),old.kind(),old.subjectId(),old.intent(),old.before(),CampaignDomainCodec.text(after),old.characterOperations(),old.combatId(),old.actor(),old.space(),old.rolls()));return snapshot(snapshot.campaignId(),new State(state.material(),state.characters(),state.combats(),nodes,state.positions(),items,roots,state.manual(),state.participation(),state.space()));}
    }
    private static CampaignData.Snapshot reorder(CampaignData.Snapshot source,int a,int b){var state=source.domains();List<Root> roots=new ArrayList<>(state.roots());Collections.swap(roots,a,b);for(int i=0;i<roots.size();i++){Root r=roots.get(i);roots.set(i,new Root(i+1L,r.operationId(),r.kind(),r.subjectId(),r.intent(),r.before(),r.after(),r.characterOperations(),r.combatId(),r.actor(),r.space(),r.rolls()));}return snapshot(source.campaignId(),new State(state.material(),state.characters(),state.combats(),state.nodes(),state.positions(),state.items(),roots,state.manual(),state.participation(),state.space()));}
    private static CampaignData.Snapshot snapshot(String id,State state){return new CampaignData.Snapshot(id,"根历史",state);}
    private static JsonObject object(String...pairs){JsonObject o=new JsonObject();for(int i=0;i<pairs.length;i+=2)o.addProperty(pairs[i],pairs[i+1]);return o;}
}
