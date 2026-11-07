package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CombatState;
import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.dndtool.service.storage.CampaignDomainData.*;
import com.dndtool.service.storage.CampaignDomainData.Character;
import static org.junit.jupiter.api.Assertions.*;

class CampaignManualIntegrationTest {
    @TempDir Path temporary;
    private static final List<String> FULL=List.of(CampaignRules.BASE,CampaignRules.HEALING,CampaignRules.RUNTIME);

    @Test void freeCardOrderedEffectsRollbackAndCompleteFileKeepInitialAndCurrentDistinct() throws Exception {
        boolean[] fail={false};
        try(var service=new CampaignStorageService(temporary.resolve("manual"),b->{if(fail[0]&&b==CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE)throw new IllegalStateException("manual fault");})){
            service.create("自由卡",FULL);send(service,"manual-create",object("name","甲","kind","PC","template","blank"));UUID id=viewId(service,"甲");
            for(var field:Map.of("ability.strength",30,"hp.maximum",20,"hp.current",17,"armor_class",99,"speed.ground",999).entrySet()){
                // Set the HP maximum before current HP; this is a real dependency, not input order luck.
                if(field.getKey().equals("hp.current"))continue;
                JsonObject p=actor(service,id);p.addProperty("fieldKey",field.getKey());p.addProperty("value",field.getValue().toString());send(service,"manual-field",p);
            }
            JsonObject hp=actor(service,id);hp.addProperty("fieldKey","hp.current");hp.addProperty("value","17");send(service,"manual-field",hp);
            JsonObject level=actor(service,id);level.addProperty("classKey","class.warlock");level.addProperty("level","7");send(service,"manual-class",level);
            JsonObject proficiency=actor(service,id);proficiency.addProperty("category","SAVE");proficiency.addProperty("targetKey","save.wisdom");proficiency.addProperty("tier","HALF");send(service,"manual-proficiency",proficiency);
            JsonObject input=manualCheck(id,viewCard(service,id).get("version").getAsLong(),"MANUAL",null,"机关",99,"ADVANTAGE",60);
            input.getAsJsonArray("targets").add(object("characterId",id.toString(),"version",viewCard(service,id).get("version").getAsString()));
            input.getAsJsonArray("success").add(object("kind","HP","targetId",id.toString(),"amount","-2"));
            input.getAsJsonArray("success").add(object("kind","HP","targetId",id.toString(),"amount","1"));
            input.getAsJsonArray("success").add(object("kind","GRANT_MODULE_ITEM","targetId",id.toString(),"itemKey","item.torch","quantity","1"));
            input.getAsJsonArray("success").add(object("kind","GRANT_TEMPORARY_ITEM","targetId",id.toString(),"name","钥匙","description","铜钥匙","quantity","2"));
            input.getAsJsonArray("success").add(object("kind","MESSAGE","text","机关开启"));
            input.getAsJsonArray("failure").add(object("kind","HP","targetId",id.toString(),"amount","-999"));
            var before=service.current();long version=viewCard(service,id).get("version").getAsLong();fail[0]=true;
            assertThrows(RuntimeException.class,()->send(service,"manual-check",input));fail[0]=false;
            assertEquals(before.domain(),service.current().domain());assertEquals(before.revision(),service.current().revision());
            send(service,"manual-check",input);assertEquals(16,viewCard(service,id).getAsJsonObject("fields").get("hp.current").getAsInt());
            assertEquals(version+1,viewCard(service,id).get("version").getAsLong());assertEquals(2,service.current().domain().getAsJsonArray("items").size());
            var root=service.current().domain().getAsJsonArray("roots").asList().getLast().getAsJsonObject();
            var audit=CampaignDomainCodec.decode(root.get("after").getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8),CampaignManualCommands.Audit.class);
            assertEquals(2,audit.result().dice().size());assertEquals(99,audit.result().modifier());assertTrue(audit.result().success());assertEquals(1,audit.cards().size());
            send(service,"note",object("text","规则卡旁的普通备注不能抹掉自由卡"));
            var accepted=service.current();String file=service.end(uuid(),accepted.epoch(),accepted.revision()).fileKey();assertEquals(accepted.domain(),service.open(file).domain());
            JsonObject entry=service.current().domain().getAsJsonObject("manual").getAsJsonArray("characters").get(0).getAsJsonObject();
            assertEquals(10,entry.getAsJsonObject("initial").getAsJsonObject("fields").get("ability.strength").getAsInt());
            assertEquals(30,entry.getAsJsonObject("current").getAsJsonObject("fields").get("ability.strength").getAsInt());
        }
    }

    @Test void manualAndSavingThrowUseExactCandidatesTiersAndNoNaturalSpecialOutcome() {
        Fixture f=new Fixture();UUID id=f.create("甲","PC","blank");
        f.field(id,"ability.wisdom",14);f.level(id,"class.cleric",9);f.proficiency(id,"SAVE","save.wisdom","HALF");
        JsonObject save=manualCheck(id,f.card(id).version(),"SAVING_THROW","save.wisdom",null,null,"NORMAL",10);
        f.apply("manual-check",save,sides->5);var roll=f.audit().result();assertEquals(4,roll.modifier());assertEquals(9,roll.total());assertFalse(roll.success());
        JsonObject advantage=manualCheck(id,f.card(id).version(),"MANUAL",null,"同点先取首骰",99,"ADVANTAGE",60);
        f.apply("manual-check",advantage,sides->1);roll=f.audit().result();assertEquals(List.of(1,1),roll.dice());assertEquals(0,roll.selectedIndex());assertEquals(100,roll.total());assertTrue(roll.success());
        JsonObject natural=manualCheck(id,f.card(id).version(),"MANUAL",null,"负修正",-99,"NORMAL",0);f.apply("manual-check",natural,sides->20);assertFalse(f.audit().result().success());
        assertTrue(CampaignManualData.catalog().fields().stream().anyMatch(x->x.key().equals("ability.strength")&&x.name().equals("力量")));
        assertTrue(CampaignManualData.catalog().classes().stream().anyMatch(x->x.key().equals("class.warlock")&&x.name().equals("邪术师")));
    }

    @Test void malformedUnknownDuplicateAndUnselectedEffectsFailBeforeDice() {
        Fixture f=new Fixture();UUID id=f.create("甲","NPC","guard");
        JsonObject base=manualCheck(id,f.card(id).version(),"MANUAL",null,"检定",99,"NORMAL",0);
        List<JsonObject> invalid=new ArrayList<>();
        JsonObject bad=base.deepCopy();bad.getAsJsonArray("failure").add(new JsonPrimitive("bad"));invalid.add(bad);
        bad=base.deepCopy();bad.getAsJsonArray("targets").add(new JsonPrimitive("bad"));invalid.add(bad);
        bad=base.deepCopy();bad.addProperty("unexpected","x");invalid.add(bad);
        bad=base.deepCopy();bad.addProperty("dc","61");invalid.add(bad);
        bad=base.deepCopy();bad.addProperty("manualModifier","100");invalid.add(bad);
        bad=base.deepCopy();bad.getAsJsonArray("failure").add(object("kind","GRANT_MODULE_ITEM","targetId",id.toString(),"itemKey","item.unpublished","quantity","1"));bad.getAsJsonArray("targets").add(object("characterId",id.toString(),"version",Long.toString(f.card(id).version())));invalid.add(bad);
        bad=base.deepCopy();bad.getAsJsonArray("failure").add(object("kind","HP","targetId",id.toString(),"amount","-1000"));bad.getAsJsonArray("targets").add(object("characterId",id.toString(),"version",Long.toString(f.card(id).version())));invalid.add(bad);
        bad=base.deepCopy();bad.getAsJsonArray("targets").add(object("characterId",id.toString(),"version","0"));bad.getAsJsonArray("targets").add(object("characterId",id.toString(),"version","0"));invalid.add(bad);
        for(JsonObject input:invalid){int[] draws={0};assertThrows(StorageException.class,()->f.apply("manual-check",input,sides->{draws[0]++;return 20;}));assertEquals(0,draws[0]);}
        JsonObject participants=object("nodeId",UUID.randomUUID().toString());JsonArray values=new JsonArray();values.add("bad");participants.add("participants",values);
        assertThrows(StorageException.class,()->f.apply("manual-encounter-start",participants,sides->{throw new AssertionError();}));
    }

    @Test void simpleEncounterMixedRosterPositionEffectsAndClosureKeepOneOwner() {
        Fixture f=new Fixture();f.apply("character-create",object("name","规则战士","kind","PC","classKey","fighter","backgroundKey","acolyte"));UUID rich=f.snapshot.domains().characters().getFirst().id();
        UUID manual=f.create("守卫","NPC","guard"),first=f.node("街道"),second=f.node("地窖");
        JsonObject start=object("nodeId",first.toString());JsonArray participants=new JsonArray();participants.add(object("characterId",manual.toString(),"side","ALLY","nodeId",first.toString()));participants.add(object("characterId",rich.toString(),"side","ENEMY","nodeId",first.toString()));start.add("participants",participants);f.apply("manual-encounter-start",start);
        assertEquals(first,f.snapshot.domains().manual().partyNodeId());assertEquals(2,f.snapshot.domains().positions().size());
        JsonObject input=manualCheck(manual,f.card(manual).version(),"MANUAL",null,"移动目标",99,"NORMAL",0);
        input.getAsJsonArray("targets").add(object("characterId",rich.toString(),"version",Long.toString(f.snapshot.domains().characters().getFirst().history().current().version())));
        input.getAsJsonArray("success").add(object("kind","POSITION","targetId",rich.toString(),"nodeId",second.toString()));f.apply("manual-check",input);
        assertEquals(second,f.snapshot.domains().positions().stream().filter(p->p.characterId().equals(rich)).findFirst().orElseThrow().nodeId());
        JsonObject richStart=object("combatVersion","-1");richStart.add("combatId",JsonNull.INSTANCE);richStart.add("command",CampaignDomainCodec.toJson(Schema2CombatState.Command.start(List.of(),List.of())));
        assertEquals("SCENE_ACTIVE",assertThrows(StorageException.class,()->f.apply("combat-command",richStart)).code());
        UUID encounter=f.snapshot.domains().manual().encounters().getFirst().id();f.apply("manual-encounter-close",object("encounterId",encounter.toString()));
        assertEquals("CLOSED",f.snapshot.domains().manual().encounters().getFirst().status());assertTrue(f.snapshot.domains().positions().isEmpty());assertEquals(first,f.snapshot.domains().manual().partyNodeId());
    }

    @Test void ordinaryCharacterAndSceneRootsCannotBeReorderedAgainstTheirEventPrefixes() {
        Fixture f=new Fixture();f.apply("character-create",object("name","甲","kind","PC","classKey","fighter","backgroundKey","acolyte"));UUID a=f.snapshot.domains().characters().getFirst().id();
        for(int i=0;i<2;i++){var c=f.snapshot.domains().characters().getFirst();var check=new Schema2CharacterRuntime.Command("CHECK_RESOLVED",null,0,null,null,null,Schema2CharacterRuntime.GrowthChoices.EMPTY,null,new Schema2CharacterRuntime.CheckInput(BundledCampaignRules.rule("adventure.check","check.srd2014"),"ABILITY","ability.strength",null,10,false,false,"DM确认检定。"));JsonObject p=object("characterId",a.toString(),"characterVersion",Long.toString(c.history().current().version()));p.add("command",CampaignDomainCodec.toJson(check));f.apply("character-command",p);}
        assertThrows(StorageException.class,()->CampaignData.validate(reorder(f.snapshot,1,2)));
        Fixture scene=new Fixture();scene.apply("character-create",object("name","甲","kind","PC","classKey","fighter","backgroundKey","acolyte"));scene.apply("character-create",object("name","乙","kind","PC","classKey","fighter","backgroundKey","acolyte"));var ids=scene.snapshot.domains().characters().stream().map(Character::id).toList();
        JsonObject start=object("combatVersion","-1");start.add("combatId",JsonNull.INSTANCE);start.add("command",CampaignDomainCodec.toJson(Schema2CombatState.Command.start(List.of(new Schema2CombatState.Deployment(ids.get(0),new Schema2CombatState.Position(0,0),0),new Schema2CombatState.Deployment(ids.get(1),new Schema2CombatState.Position(1,0),1)),ids)));scene.apply("combat-command",start);
        for(int i=0;i<2;i++){var battle=scene.snapshot.domains().combats().getFirst().current();JsonObject p=object("combatId",battle.combatId().toString(),"combatVersion",Long.toString(battle.version()));p.add("command",CampaignDomainCodec.toJson(Schema2CombatState.Command.action(Schema2CombatState.Kind.END_TURN,battle.activeActor())));scene.apply("combat-command",p);}
        assertThrows(StorageException.class,()->CampaignData.validate(reorder(scene.snapshot,3,4)));
    }
    @Test void ordinaryItemAndRichCommandsKeepManualStateAndArchivedModuleProvenance() {
        Fixture f=new Fixture();UUID id=f.create("甲","PC","blank");var template=CampaignManualData.catalog().items().getFirst();
        JsonObject create=f.actor(id);create.add("itemId",JsonNull.INSTANCE);create.addProperty("sourceKey",template.key());create.addProperty("name",template.name());create.addProperty("description",template.description());create.addProperty("quantity","1");create.addProperty("active",true);f.apply("manual-item",create);
        Item item=f.snapshot.domains().items().getFirst();JsonObject archive=f.actor(id);archive.addProperty("itemId",item.id().toString());archive.addProperty("sourceKey",template.key());archive.addProperty("name",template.name());archive.addProperty("description",template.description());archive.addProperty("quantity","1");archive.addProperty("active",false);f.apply("manual-item",archive);
        f.apply("item",object("itemId",item.id().toString(),"characterId",id.toString(),"name",template.name(),"quantity","2"));
        Item retained=f.snapshot.domains().items().getFirst();assertFalse(retained.active());assertEquals(template.key(),retained.sourceKey());assertEquals(template.description(),retained.description());
        var manual=f.snapshot.domains().manual();f.apply("note",object("text","保留自由卡"));assertEquals(manual,f.snapshot.domains().manual());
        f.apply("character-create",object("name","规则卡","kind","PC","classKey","fighter","backgroundKey","acolyte"));var rich=f.snapshot.domains().characters().getFirst();
        JsonObject check=object("characterId",rich.id().toString(),"characterVersion",Long.toString(rich.history().current().version()));check.add("command",CampaignDomainCodec.toJson(new Schema2CharacterRuntime.Command("CHECK_RESOLVED",null,0,null,null,null,Schema2CharacterRuntime.GrowthChoices.EMPTY,null,new Schema2CharacterRuntime.CheckInput(BundledCampaignRules.rule("adventure.check","check.srd2014"),"ABILITY","ability.strength",null,0,false,false,"DM确认检定。"))));
        for(String branch:List.of("success","failure")){JsonObject effects=object("targetId",rich.id().toString(),"itemDelta","0","message","保留其他角色");effects.add("healthEffect",JsonNull.INSTANCE);effects.add("itemId",JsonNull.INSTANCE);effects.add("nodeId",JsonNull.INSTANCE);check.add(branch,effects);}
        f.apply("check-effects",check);assertEquals(manual,f.snapshot.domains().manual());assertEquals(retained,f.snapshot.domains().items().getFirst());
    }

    private static final class Fixture {
        CampaignData.Snapshot snapshot=snapshot(UUID.randomUUID().toString(),State.initial(BundledCampaignRules.projection()));
        void apply(String action,JsonObject input){apply(action,input,sides->10);}
        void apply(String action,JsonObject input,IntUnaryOperator dice){State after=new CampaignDomainCommands().apply(snapshot,action,input,UUID.randomUUID(),"0".repeat(64),dice);snapshot=CampaignData.validate(snapshot(snapshot.campaignId(),after));}
        UUID create(String name,String kind,String template){apply("manual-create",object("name",name,"kind",kind,"template",template));return snapshot.domains().manual().characters().stream().filter(c->c.current().name().equals(name)).findFirst().orElseThrow().id();}
        CampaignManualData.Card card(UUID id){return snapshot.domains().manual().characters().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow().current();}
        JsonObject actor(UUID id){return object("characterId",id.toString(),"characterVersion",Long.toString(card(id).version()));}
        void field(UUID id,String key,int value){JsonObject p=actor(id);p.addProperty("fieldKey",key);p.addProperty("value",Integer.toString(value));apply("manual-field",p);}
        void level(UUID id,String key,int level){JsonObject p=actor(id);p.addProperty("classKey",key);p.addProperty("level",Integer.toString(level));apply("manual-class",p);}
        void proficiency(UUID id,String category,String key,String tier){JsonObject p=actor(id);p.addProperty("category",category);p.addProperty("targetKey",key);p.addProperty("tier",tier);apply("manual-proficiency",p);}
        UUID node(String name){apply("node-create",object("name",name));return snapshot.domains().nodes().stream().filter(n->n.name().equals(name)).findFirst().orElseThrow().id();}
        CampaignManualCommands.Audit audit(){return CampaignDomainCodec.decode(snapshot.domains().roots().getLast().after().getBytes(java.nio.charset.StandardCharsets.UTF_8),CampaignManualCommands.Audit.class);}
    }
    private static CampaignData.Snapshot snapshot(String id,State state){return new CampaignData.Snapshot(id,"自由规则",FULL,List.of(),state.roots().stream().map(CampaignDomainValidation::summary).toList(),state);}
    private static CampaignData.Snapshot reorder(CampaignData.Snapshot source,int a,int b){List<Root> roots=new ArrayList<>(source.domains().roots());Collections.swap(roots,a,b);for(int i=0;i<roots.size();i++){Root r=roots.get(i);roots.set(i,new Root(i+1L,r.operationId(),r.kind(),r.subjectId(),r.intent(),r.before(),r.after(),r.characterOperations(),r.combatId(),r.actor()));}State s=source.domains();return snapshot(source.campaignId(),new State(s.material(),s.characters(),s.combats(),s.nodes(),s.positions(),s.items(),roots,s.manual()));}
    private static JsonObject manualCheck(UUID id,long version,String kind,String key,String name,Integer modifier,String mode,int dc){JsonObject p=object("characterId",id.toString(),"characterVersion",Long.toString(version),"checkKind",kind,"rollMode",mode,"dc",Integer.toString(dc));p.add("key",key==null?JsonNull.INSTANCE:new JsonPrimitive(key));p.add("manualName",name==null?JsonNull.INSTANCE:new JsonPrimitive(name));p.add("manualModifier",modifier==null?JsonNull.INSTANCE:new JsonPrimitive(modifier.toString()));p.add("targets",new JsonArray());p.add("success",new JsonArray());p.add("failure",new JsonArray());return p;}
    private static String uuid(){return UUID.randomUUID().toString();}
    private static JsonObject object(String...pairs){JsonObject p=new JsonObject();for(int i=0;i<pairs.length;i+=2)p.addProperty(pairs[i],pairs[i+1]);return p;}
    private static void send(CampaignStorageService service,String action,JsonObject input){var current=service.current();service.domainCommand(uuid(),current.epoch(),current.revision(),action,input);}
    private static JsonObject viewCard(CampaignStorageService service,UUID id){return service.current().domain().getAsJsonObject("manual").getAsJsonArray("characters").asList().stream().map(JsonElement::getAsJsonObject).map(c->c.getAsJsonObject("current")).filter(c->c.get("id").getAsString().equals(id.toString())).findFirst().orElseThrow();}
    private static UUID viewId(CampaignStorageService service,String name){return service.current().domain().getAsJsonObject("manual").getAsJsonArray("characters").asList().stream().map(JsonElement::getAsJsonObject).map(c->c.getAsJsonObject("current")).filter(c->c.get("name").getAsString().equals(name)).map(c->UUID.fromString(c.get("id").getAsString())).findFirst().orElseThrow();}
    private static JsonObject actor(CampaignStorageService service,UUID id){return object("characterId",id.toString(),"characterVersion",viewCard(service,id).get("version").getAsString());}
}
