package com.dndtool.service.storage;

import com.dndtool.service.CharacterNamePolicy;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntUnaryOperator;
import static com.dndtool.service.storage.CampaignDomainData.*;
import static com.dndtool.service.storage.CampaignManualData.*;
import com.dndtool.service.storage.CampaignDomainData.State;

/** Closed DM-managed commands. No manual card or result is converted into schema-2 facts. */
public final class CampaignManualCommands {
    private CampaignManualCommands() { }
    public sealed interface Request permits Create, ProfileEdit, FieldEdit, ClassEdit, ProficiencyEdit, ItemEdit, Check, EncounterStart, EncounterMembers, EncounterClose, PartyPosition { }
    public record Create(String name,String kind,String template) implements Request { public Create { name=normalizedName(name);if(kind==null||template==null)throw CampaignJson.bad("Missing manual creation kind"); } }
    public record ProfileEdit(UUID characterId,long characterVersion,String name,String kind,boolean active) implements Request { public ProfileEdit { uuid(characterId);name=normalizedName(name);if(kind==null||characterVersion<0)throw CampaignJson.bad("Invalid manual profile"); } }
    public record FieldEdit(UUID characterId,long characterVersion,String fieldKey,int value) implements Request { public FieldEdit { version(characterId,characterVersion);if(fieldKey==null)throw CampaignJson.bad("Missing field key"); } }
    public record ClassEdit(UUID characterId,long characterVersion,String classKey,int level) implements Request { public ClassEdit { version(characterId,characterVersion);if(classKey==null)throw CampaignJson.bad("Missing class key"); } }
    public record ProficiencyEdit(UUID characterId,long characterVersion,String category,String targetKey,String tier) implements Request { public ProficiencyEdit { version(characterId,characterVersion);if(category==null||targetKey==null||tier==null)throw CampaignJson.bad("Incomplete proficiency edit"); } }
    public record ItemEdit(UUID characterId,long characterVersion,UUID itemId,String sourceKey,String name,String description,int quantity,boolean active) implements Request { public ItemEdit { version(characterId,characterVersion);if(itemId!=null)uuid(itemId);text(name,1,80);text(description,0,500);CampaignManualCommands.quantity(quantity); } }
    public record Check(UUID characterId,long characterVersion,String checkKind,String key,String manualName,Integer manualModifier,
                        String rollMode,int dc,Map<UUID,Long> targets,List<Effect> success,List<Effect> failure) implements Request {
        public Check { version(characterId,characterVersion);if(checkKind==null||rollMode==null)throw CampaignJson.bad("Missing check kind");targets=Map.copyOf(targets);success=List.copyOf(success);failure=List.copyOf(failure); }
    }
    public record EncounterStart(UUID nodeId,List<Deployment> participants) implements Request { public EncounterStart { uuid(nodeId);participants=List.copyOf(participants); } }
    public record EncounterMembers(UUID encounterId,List<Deployment> participants) implements Request { public EncounterMembers { uuid(encounterId);participants=List.copyOf(participants); } }
    public record EncounterClose(UUID encounterId) implements Request { public EncounterClose { uuid(encounterId); } }
    public record PartyPosition(UUID nodeId) implements Request { public PartyPosition { uuid(nodeId); } }
    public sealed interface Effect permits Hp,GrantModuleItem,GrantTemporaryItem,Move,Message { }
    public record Hp(UUID targetId,int amount) implements Effect { public Hp { uuid(targetId);if(amount<-999||amount>999)throw CampaignJson.bad("HP adjustment outside bounds"); } }
    public record GrantModuleItem(UUID targetId,String itemKey,int quantity) implements Effect { public GrantModuleItem { uuid(targetId);CampaignManualCommands.quantity(quantity);if(itemKey==null)throw CampaignJson.bad("Missing item template"); } }
    public record GrantTemporaryItem(UUID targetId,String name,String description,int quantity) implements Effect { public GrantTemporaryItem { uuid(targetId);CampaignManualCommands.quantity(quantity);text(name,1,80);text(description,0,500); } }
    public record Move(UUID targetId,UUID nodeId) implements Effect { public Move { uuid(targetId);uuid(nodeId); } }
    public record Message(String text) implements Effect { public Message { CampaignManualData.text(text,1,500); } }
    public record Roll(Check request,List<Integer> dice,int selectedIndex,int modifier,int total,boolean success) { public Roll { dice=List.copyOf(dice); } }
    public record CardChange(ManualCharacter before,ManualCharacter after) { }
    public record ItemChange(Item before,Item after) { }
    public record PositionChange(Position before,Position after) { }
    public record Audit(Request request,List<CardChange> cards,List<ItemChange> items,List<PositionChange> positions,
                        Encounter encounterBefore,Encounter encounterAfter,UUID partyBefore,UUID partyAfter,Roll result) {
        public Audit { cards=List.copyOf(cards);items=List.copyOf(items);positions=List.copyOf(positions);if(request==null)throw CampaignJson.bad("Missing manual request"); }
    }
    public record Result(State state,UUID subjectId,Audit audit) { }

    public static Result resolve(State before,String action,JsonObject payload,UUID operation,IntUnaryOperator dice) {
        return execute(before,parse(action,payload),operation,dice);
    }
    public static String action(Request request) {
        return switch(request) {
            case Create ignored -> "manual-create";case ProfileEdit ignored -> "manual-profile";case FieldEdit ignored -> "manual-field";
            case ClassEdit ignored -> "manual-class";case ProficiencyEdit ignored -> "manual-proficiency";case ItemEdit ignored -> "manual-item";
            case Check ignored -> "manual-check";case EncounterStart ignored -> "manual-encounter-start";case EncounterMembers ignored -> "manual-encounter-members";
            case EncounterClose ignored -> "manual-encounter-close";case PartyPosition ignored -> "manual-party-position";
        };
    }
    private static Result execute(State before,Request request,UUID operation,IntUnaryOperator dice) {
        uuid(operation);
        if(before.roots().size()>=100000)throw new StorageException("CAPACITY","Complete root history is full");
        if(before.combats().stream().anyMatch(c->c.current().status()!=com.dndtool.service.Schema2CombatState.Status.ENDED))throw new StorageException("SCENE_ACTIVE","The schema-2 scene owns this campaign");
        Work work=new Work(before,operation);Roll roll=null;UUID subject=operation;
        switch(request) {
            case Create r -> {
                if(work.cards.size()>=10000)throw new StorageException("CAPACITY","Manual roster is full");
                subject=child(operation,"character",0);if(work.owner(subject))throw CampaignJson.bad("Character identity conflict");
                Card card=initial(subject,CharacterNamePolicy.normalize(r.name()),r.kind(),r.template());work.cards.put(subject,new ManualCharacter(card,card));
            }
            case ProfileEdit r -> {
                Card c=work.card(r.characterId(),r.characterVersion());
                if(!r.active()&&work.member(c.id()))throw CampaignJson.bad("Remove the participant before archiving it");
                work.edit(new Card(c.id(),CharacterNamePolicy.normalize(r.name()),r.kind(),r.active(),c.fields(),c.classes(),c.skills(),c.saves(),c.version()));subject=c.id();
            }
            case FieldEdit r -> {
                Card c=work.card(r.characterId(),r.characterVersion());work.activeOwner(c.id());if(!c.fields().containsKey(r.fieldKey()))throw CampaignJson.bad("Unknown manual field");
                Map<String,Integer> fields=new TreeMap<>(c.fields());fields.put(r.fieldKey(),r.value());work.edit(copy(c,fields,c.classes(),c.skills(),c.saves()));subject=c.id();
            }
            case ClassEdit r -> {
                Card c=work.card(r.characterId(),r.characterVersion());work.activeOwner(c.id());if(!CLASSES.contains(r.classKey())||r.level()<0||r.level()>20)throw CampaignJson.bad("Invalid manual class level");
                Map<String,Integer> classes=new TreeMap<>(c.classes());if(r.level()==0)classes.remove(r.classKey());else classes.put(r.classKey(),r.level());work.edit(copy(c,c.fields(),classes,c.skills(),c.saves()));subject=c.id();
            }
            case ProficiencyEdit r -> {
                Card c=work.card(r.characterId(),r.characterVersion());work.activeOwner(c.id());if(!TIERS.contains(r.tier()))throw CampaignJson.bad("Unknown proficiency tier");
                Map<String,String> skills=new TreeMap<>(c.skills()),saves=new TreeMap<>(c.saves());Map<String,String> selected=switch(r.category()){case "SKILL"->skills;case "SAVE"->saves;default->throw CampaignJson.bad("Unknown proficiency category");};
                if(!selected.containsKey(r.targetKey()))throw CampaignJson.bad("Unknown proficiency target");selected.put(r.targetKey(),r.tier());work.edit(copy(c,c.fields(),c.classes(),skills,saves));subject=c.id();
            }
            case ItemEdit r -> {
                Card c=work.card(r.characterId(),r.characterVersion());work.activeOwner(c.id());quantity(r.quantity());Item old=r.itemId()==null?null:work.items.get(r.itemId());
                if(r.itemId()!=null&&(old==null||!old.characterId().equals(c.id())))throw CampaignJson.bad("Unknown item or different owner");
                if(old!=null&&(!old.name().equals(r.name())||!old.description().equals(r.description())||!Objects.equals(old.sourceKey(),r.sourceKey())))throw CampaignJson.bad("Existing item provenance is immutable");
                if(r.sourceKey()!=null){ItemTemplate template=template(r.sourceKey());if(!template.name().equals(r.name())||!template.description().equals(r.description()))throw CampaignJson.bad("Module item metadata differs");}
                text(r.name(),1,80);text(r.description(),0,500);UUID id=old==null?child(operation,"item",0):old.id();
                if(old==null&&(!r.active()||work.items.containsKey(id)||work.items.size()>=10000))throw CampaignJson.bad("New item state or capacity differs");
                work.setItem(new Item(id,c.id(),r.name(),r.quantity(),r.sourceKey(),r.description(),r.active()));work.touch.add(c.id());subject=c.id();
            }
            case Check r -> {
                Card executor=work.card(r.characterId(),r.characterVersion());work.activeOwner(executor.id());int modifier=modifier(executor,r);
                checkShape(r);Set<UUID> targets=new HashSet<>();for(Effect e:r.success())if(target(e)!=null)targets.add(target(e));for(Effect e:r.failure())if(target(e)!=null)targets.add(target(e));
                if(!r.targets().keySet().equals(targets))throw CampaignJson.bad("Complete conditional target versions are required");
                for(UUID id:targets){work.activeOwner(id);if(work.version(id)!=r.targets().get(id))throw new StorageException("STALE_CHARACTER","Refresh every possible effect target");}
                Work success=new Work(before,operation);effects(success,r.success(),"success");success.finish(request,null);
                Work failure=new Work(before,operation);effects(failure,r.failure(),"failure");failure.finish(request,null);
                int count=r.rollMode().equals("NORMAL")?1:2;List<Integer> draws=new ArrayList<>();for(int i=0;i<count;i++){int d=dice.applyAsInt(20);if(d<1||d>20)throw new IllegalStateException("Invalid server d20");draws.add(d);}
                int selected=0;if(count==2&&(r.rollMode().equals("ADVANTAGE")?draws.get(1)>draws.get(0):draws.get(1)<draws.get(0)))selected=1;
                int total=draws.get(selected)+modifier;roll=new Roll(r,draws,selected,modifier,total,total>=r.dc());effects(work,roll.success()?r.success():r.failure(),roll.success()?"success":"failure");subject=executor.id();
            }
            case EncounterStart r -> {
                if(work.active()!=null||work.encounters.size()>=10000)throw CampaignJson.bad("An encounter already owns this campaign or capacity is full");work.node(r.nodeId());
                subject=child(operation,"encounter",0);work.encounterAfter=new Encounter(subject,"ACTIVE",deploy(work,r.participants()));work.encounters.put(subject,work.encounterAfter);work.party=r.nodeId();
            }
            case EncounterMembers r -> {
                Encounter old=work.requireEncounter(r.encounterId());work.encounterBefore=old;List<Participant> members=deploy(work,r.participants());
                Set<UUID> keep=new HashSet<>(members.stream().map(Participant::characterId).toList());for(Participant participant:old.participants())if(!keep.contains(participant.characterId()))work.removePosition(participant.characterId());
                work.encounterAfter=new Encounter(old.id(),"ACTIVE",members);work.encounters.put(old.id(),work.encounterAfter);subject=old.id();
            }
            case EncounterClose r -> {
                Encounter old=work.requireEncounter(r.encounterId());work.encounterBefore=old;work.encounterAfter=new Encounter(old.id(),"CLOSED",old.participants());work.encounters.put(old.id(),work.encounterAfter);
                for(Participant participant:old.participants())work.removePosition(participant.characterId());subject=old.id();
            }
            case PartyPosition r -> {work.node(r.nodeId());work.party=r.nodeId();subject=r.nodeId();}
        }
        Result finished=work.finish(request,roll);return new Result(finished.state(),subject,finished.audit());
    }
    private static List<Participant> deploy(Work work,List<Deployment> deployments) {
        if(deployments.isEmpty()||deployments.size()>10000||new HashSet<>(deployments.stream().map(Deployment::characterId).toList()).size()!=deployments.size())throw CampaignJson.bad("Invalid encounter roster");
        List<Participant> result=new ArrayList<>();for(Deployment d:deployments){work.activeOwner(d.characterId());work.node(d.nodeId());result.add(new Participant(d.characterId(),d.side()));work.setPosition(new Position(d.characterId(),d.nodeId()));}
        return result.stream().sorted(Comparator.comparing(p->p.characterId().toString())).toList();
    }
    private static void checkShape(Check r) {
        if(!catalog().checkKinds().contains(r.checkKind())||!catalog().rollModes().contains(r.rollMode())||r.dc()<0||r.dc()>60||r.success().size()>64||r.failure().size()>64)throw CampaignJson.bad("Unsupported bounded manual check");
        for(List<Effect> branch:List.of(r.success(),r.failure()))if(branch.stream().filter(Message.class::isInstance).count()>1)throw CampaignJson.bad("Only one root message is allowed per branch");
    }
    private static int modifier(Card c,Check r) {
        if(r.checkKind().equals("MANUAL")){if(r.key()!=null||r.manualModifier()==null||r.manualModifier()<-99||r.manualModifier()>99)throw CampaignJson.bad("Invalid manual modifier");text(r.manualName(),1,80);return r.manualModifier();}
        if(r.manualName()!=null||r.manualModifier()!=null)throw CampaignJson.bad("Derived checks cannot accept a manual modifier");
        if(r.key()==null)throw CampaignJson.bad("Derived check needs a stable target key");
        String ability;String tier="NONE";
        switch(r.checkKind()){
            case "ABILITY"->{if(!ABILITIES.contains(r.key()))throw CampaignJson.bad("Unknown ability");ability=r.key();}
            case "SKILL"->{Proficiency p=catalog().skills().stream().filter(x->x.key().equals(r.key())).findFirst().orElseThrow(()->CampaignJson.bad("Unknown skill"));ability=p.abilityKey();tier=c.skills().get(p.key());}
            case "SAVING_THROW"->{Proficiency p=catalog().saves().stream().filter(x->x.key().equals(r.key())).findFirst().orElseThrow(()->CampaignJson.bad("Unknown save"));ability=p.abilityKey();tier=c.saves().get(p.key());}
            default->throw CampaignJson.bad("Unknown check kind");
        }
        int pb=c.proficiencyBonus();return Math.floorDiv(c.fields().get(ability)-10,2)+switch(tier){case "NONE"->0;case "HALF"->pb/2;case "FULL"->pb;case "EXPERTISE"->pb*2;default->throw CampaignJson.bad("Unknown proficiency tier");};
    }
    private static void effects(Work work,List<Effect> effects,String branch) {
        for(int i=0;i<effects.size();i++){
            Effect effect=effects.get(i);UUID target=target(effect);if(target!=null)work.activeOwner(target);
            switch(effect){
                case Hp e->{if(e.amount()<-999||e.amount()>999)throw CampaignJson.bad("HP adjustment outside bounds");Card c=work.manualCard(e.targetId());Map<String,Integer> fields=new TreeMap<>(c.fields());fields.put("hp.current",Math.max(0,Math.min(fields.get("hp.maximum"),fields.get("hp.current")+e.amount())));work.edit(copy(c,fields,c.classes(),c.skills(),c.saves()));}
                case GrantModuleItem e->{ItemTemplate t=template(e.itemKey());grant(work,target,t.key(),t.name(),t.description(),e.quantity(),branch,i);}
                case GrantTemporaryItem e->{text(e.name(),1,80);text(e.description(),0,500);grant(work,target,null,e.name(),e.description(),e.quantity(),branch,i);}
                case Move e->{work.node(e.nodeId());if(!work.member(e.targetId()))throw CampaignJson.bad("Position effects require an existing active encounter member");work.setPosition(new Position(e.targetId(),e.nodeId()));}
                case Message e->text(e.text(),1,500);
            }
            if(target!=null&&work.cards.containsKey(target))work.touch.add(target);
        }
    }
    private static void grant(Work work,UUID target,String source,String name,String description,int quantity,String branch,int ordinal){
        quantity(quantity);UUID id=child(work.operation,branch+"-grant",ordinal);if(work.items.containsKey(id)||work.items.size()>=10000)throw CampaignJson.bad("Granted item identity or capacity conflict");work.setItem(new Item(id,target,name,quantity,source,description,true));
    }
    private static UUID target(Effect effect){return switch(effect){case Hp e->e.targetId();case GrantModuleItem e->e.targetId();case GrantTemporaryItem e->e.targetId();case Move e->e.targetId();case Message ignored->null;};}
    private static void quantity(int value){if(value<1||value>999)throw CampaignJson.bad("Simple item quantity must be 1–999");}
    private static ItemTemplate template(String key){return catalog().items().stream().filter(i->i.key().equals(key)).findFirst().orElseThrow(()->CampaignJson.bad("Unknown module item template"));}
    private static Card copy(Card c,Map<String,Integer> fields,Map<String,Integer> classes,Map<String,String> skills,Map<String,String> saves){return new Card(c.id(),c.name(),c.kind(),c.active(),fields,classes,skills,saves,c.version());}

    private static final class Work {
        final State before;final UUID operation;final Map<UUID,ManualCharacter> cards=new TreeMap<>(Comparator.comparing(UUID::toString));
        final Map<UUID,Encounter> encounters=new TreeMap<>(Comparator.comparing(UUID::toString));final Map<UUID,Item> items=new TreeMap<>(Comparator.comparing(UUID::toString));
        final Map<UUID,Position> positions=new TreeMap<>(Comparator.comparing(UUID::toString));final Set<UUID> touch=new HashSet<>();
        final List<ItemChange> itemChanges=new ArrayList<>();final List<PositionChange> positionChanges=new ArrayList<>();UUID party;Encounter encounterBefore,encounterAfter;
        Work(State before,UUID operation){this.before=before;this.operation=operation;before.manual().characters().forEach(c->cards.put(c.id(),c));before.manual().encounters().forEach(e->encounters.put(e.id(),e));before.items().forEach(i->items.put(i.id(),i));before.positions().forEach(p->positions.put(p.characterId(),p));party=before.manual().partyNodeId();}
        boolean owner(UUID id){return cards.containsKey(id)||before.characters().stream().anyMatch(c->c.id().equals(id));}
        void activeOwner(UUID id){uuid(id);if(cards.containsKey(id)){if(!cards.get(id).current().active())throw new StorageException("CHARACTER_INACTIVE","Restore the manual character first");}else if(before.characters().stream().noneMatch(c->c.id().equals(id)&&c.active()))throw CampaignJson.bad("Unknown or inactive effect target");}
        long version(UUID id){if(cards.containsKey(id))return cards.get(id).current().version();return before.characters().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow().history().current().version();}
        Card manualCard(UUID id){ManualCharacter c=cards.get(id);if(c==null)throw CampaignJson.bad("This command requires a DM-managed character");return c.current();}
        Card card(UUID id,long version){Card c=manualCard(id);if(c.version()!=version)throw new StorageException("STALE_CHARACTER","Refresh the free character card");return c;}
        void edit(Card card){ManualCharacter old=cards.get(card.id());cards.put(card.id(),new ManualCharacter(old.initial(),card));touch.add(card.id());}
        void node(UUID id){uuid(id);if(before.nodes().stream().noneMatch(n->n.id().equals(id)))throw CampaignJson.bad("Unknown current campaign node");}
        Encounter active(){return encounters.values().stream().filter(e->e.status().equals("ACTIVE")).findFirst().orElse(null);}
        Encounter requireEncounter(UUID id){Encounter e=active();if(e==null||!e.id().equals(id))throw CampaignJson.bad("Active encounter identity differs");return e;}
        boolean member(UUID id){Encounter e=active();return e!=null&&e.participants().stream().anyMatch(p->p.characterId().equals(id));}
        void setItem(Item item){itemChanges.add(new ItemChange(items.get(item.id()),item));items.put(item.id(),item);}
        void setPosition(Position p){positionChanges.add(new PositionChange(positions.get(p.characterId()),p));positions.put(p.characterId(),p);}
        void removePosition(UUID id){Position old=positions.remove(id);if(old!=null)positionChanges.add(new PositionChange(old,null));}
        Result finish(Request request,Roll roll){
            for(UUID id:touch){ManualCharacter c=cards.get(id);Card v=c.current();cards.put(id,new ManualCharacter(c.initial(),new Card(v.id(),v.name(),v.kind(),v.active(),v.fields(),v.classes(),v.skills(),v.saves(),v.version()+1)));}
            Map<UUID,ManualCharacter> old=new HashMap<>();before.manual().characters().forEach(c->old.put(c.id(),c));List<CardChange> changes=new ArrayList<>();for(var c:cards.values())if(!c.equals(old.get(c.id())))changes.add(new CardChange(old.get(c.id()),c));
            var manual=new CampaignManualData.State(List.copyOf(cards.values()),List.copyOf(encounters.values()),party);
            State state=new State(before.material(),before.characters(),before.combats(),before.nodes(),List.copyOf(positions.values()),List.copyOf(items.values()),before.roots(),manual);
            return new Result(state,operation,new Audit(request,changes,itemChanges,positionChanges,encounterBefore,encounterAfter,before.manual().partyNodeId(),party,roll));
        }
    }

    /** Replay validation uses only the saved candidate dice and advances caller-owned projections after success. */
    public static CampaignManualData.State validateAudit(State retained,Root root,Audit audit,CampaignManualData.State manual,
            Map<UUID,Profile> profiles,Map<UUID,Node> nodes,Map<UUID,Item> items,Map<UUID,Position> positions) {
        if(!root.kind().equals(action(audit.request()))||!root.before().isEmpty()||root.combatId()!=null||!root.characterOperations().isEmpty()||!root.after().equals(CampaignDomainCodec.text(audit)))throw CampaignJson.bad("Invalid manual root");
        Set<UUID> previousCharacterOperations=new HashSet<>();retained.roots().stream().limit(root.sequence()-1).forEach(r->previousCharacterOperations.addAll(r.characterOperations()));
        List<CampaignDomainData.Character> visible=retained.characters().stream().filter(c->profiles.containsKey(c.id())).map(c->{Profile p=profiles.get(c.id());var es=c.history().events().stream().filter(e->previousCharacterOperations.contains(e.operationId())).toList();if(es.isEmpty())throw CampaignJson.bad("Missing historical rule-character prefix");return new CampaignDomainData.Character(p.name(),p.kind(),p.active(),c.initial(),new com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.History(es.getLast().after(),es));}).toList();
        Set<UUID> earlier=new HashSet<>();retained.roots().stream().limit(root.sequence()-1).filter(r->r.combatId()!=null).forEach(r->earlier.add(r.operationId()));
        var combats=retained.combats().stream().map(c->{var es=c.events().stream().filter(e->earlier.contains(e.operationId())).toList();return es.isEmpty()?null:new com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.History(es.getLast().after(),c.members(),es);}).filter(Objects::nonNull).toList();
        State before=new State(retained.material(),visible,combats,sort(nodes.values(),n->n.id().toString()),sort(positions.values(),p->p.characterId().toString()),sort(items.values(),i->i.id().toString()),retained.roots().subList(0,Math.toIntExact(root.sequence()-1)),manual);
        ArrayDeque<Integer> dice=new ArrayDeque<>(audit.result()==null?List.of():audit.result().dice());
        Result expected=execute(before,audit.request(),root.operationId(),sides->{if(sides!=20||dice.isEmpty())throw CampaignJson.bad("Missing saved manual die");return dice.removeFirst();});
        if(!dice.isEmpty()||!expected.audit().equals(audit)||!expected.subjectId().equals(root.subjectId()))throw CampaignJson.bad("Manual history arithmetic or adjacency differs");
        items.clear();expected.state().items().forEach(i->items.put(i.id(),i));positions.clear();expected.state().positions().forEach(p->positions.put(p.characterId(),p));return expected.state().manual();
    }
    public static CampaignManualData.State prefix(CampaignManualData.State before,Audit audit){
        Map<UUID,ManualCharacter> cards=new TreeMap<>(Comparator.comparing(UUID::toString));before.characters().forEach(c->cards.put(c.id(),c));for(CardChange c:audit.cards()){if(!Objects.equals(cards.get(c.after().id()),c.before()))throw CampaignJson.bad("Manual prefix adjacency differs");cards.put(c.after().id(),c.after());}
        Map<UUID,Encounter> encounters=new TreeMap<>(Comparator.comparing(UUID::toString));before.encounters().forEach(e->encounters.put(e.id(),e));if(audit.encounterAfter()!=null){if(!Objects.equals(encounters.get(audit.encounterAfter().id()),audit.encounterBefore()))throw CampaignJson.bad("Encounter prefix differs");encounters.put(audit.encounterAfter().id(),audit.encounterAfter());}
        if(!Objects.equals(before.partyNodeId(),audit.partyBefore()))throw CampaignJson.bad("Party prefix differs");return new CampaignManualData.State(List.copyOf(cards.values()),List.copyOf(encounters.values()),audit.partyAfter());
    }
    private static <T>List<T> sort(Collection<T> values,java.util.function.Function<T,String> key){return values.stream().sorted(Comparator.comparing(key)).toList();}
    private static UUID child(UUID root,String purpose,int ordinal){UUID d=UUID.nameUUIDFromBytes(("campaign-manual/1/"+root+"/"+purpose+"/"+ordinal).getBytes(StandardCharsets.US_ASCII));return new UUID((d.getMostSignificantBits()&~0xf000L)|0x4000L,(d.getLeastSignificantBits()&0x3fffffffffffffffL)|0x8000000000000000L);}

    private static Request parse(String action,JsonObject p){
        return switch(action){
            case "manual-create"->{fields(p,"name","kind","template");yield new Create(s(p,"name"),s(p,"kind"),s(p,"template"));}
            case "manual-profile"->{fields(p,"characterId","characterVersion","name","kind","active");yield new ProfileEdit(id(p,"characterId"),n(p,"characterVersion"),s(p,"name"),s(p,"kind"),b(p,"active"));}
            case "manual-field"->{fields(p,"characterId","characterVersion","fieldKey","value");yield new FieldEdit(id(p,"characterId"),n(p,"characterVersion"),s(p,"fieldKey"),integer(p,"value"));}
            case "manual-class"->{fields(p,"characterId","characterVersion","classKey","level");yield new ClassEdit(id(p,"characterId"),n(p,"characterVersion"),s(p,"classKey"),integer(p,"level"));}
            case "manual-proficiency"->{fields(p,"characterId","characterVersion","category","targetKey","tier");yield new ProficiencyEdit(id(p,"characterId"),n(p,"characterVersion"),s(p,"category"),s(p,"targetKey"),s(p,"tier"));}
            case "manual-item"->{fields(p,"characterId","characterVersion","itemId","sourceKey","name","description","quantity","active");yield new ItemEdit(id(p,"characterId"),n(p,"characterVersion"),optionalId(p,"itemId"),optional(p,"sourceKey"),s(p,"name"),s(p,"description"),integer(p,"quantity"),b(p,"active"));}
            case "manual-check"->{fields(p,"characterId","characterVersion","checkKind","key","manualName","manualModifier","rollMode","dc","targets","success","failure");Map<UUID,Long> targets=new LinkedHashMap<>();for(JsonElement e:CampaignJson.array(p,"targets")){JsonObject t=object(e);fields(t,"characterId","version");if(targets.putIfAbsent(id(t,"characterId"),n(t,"version"))!=null)throw CampaignJson.bad("Duplicate target version");}yield new Check(id(p,"characterId"),n(p,"characterVersion"),s(p,"checkKind"),optional(p,"key"),optional(p,"manualName"),p.get("manualModifier").isJsonNull()?null:integer(p,"manualModifier"),s(p,"rollMode"),integer(p,"dc"),targets,readEffects(CampaignJson.array(p,"success")),readEffects(CampaignJson.array(p,"failure")));}
            case "manual-encounter-start"->{fields(p,"nodeId","participants");yield new EncounterStart(id(p,"nodeId"),deployments(CampaignJson.array(p,"participants")));}
            case "manual-encounter-members"->{fields(p,"encounterId","participants");yield new EncounterMembers(id(p,"encounterId"),deployments(CampaignJson.array(p,"participants")));}
            case "manual-encounter-close"->{fields(p,"encounterId");yield new EncounterClose(id(p,"encounterId"));}
            case "manual-party-position"->{fields(p,"nodeId");yield new PartyPosition(id(p,"nodeId"));}
            default->throw CampaignJson.bad("Unknown manual command");
        };
    }
    private static List<Deployment> deployments(JsonArray array){List<Deployment> result=new ArrayList<>();for(JsonElement e:array){JsonObject o=object(e);fields(o,"characterId","side","nodeId");result.add(new Deployment(id(o,"characterId"),s(o,"side"),id(o,"nodeId")));}return result;}
    private static List<Effect> readEffects(JsonArray array){List<Effect> result=new ArrayList<>();if(array.size()>64)throw CampaignJson.bad("Effect branch capacity exceeded");for(JsonElement e:array){JsonObject o=object(e);result.add(switch(s(o,"kind")){
        case "HP"->{fields(o,"kind","targetId","amount");yield new Hp(id(o,"targetId"),integer(o,"amount"));}
        case "GRANT_MODULE_ITEM"->{fields(o,"kind","targetId","itemKey","quantity");yield new GrantModuleItem(id(o,"targetId"),s(o,"itemKey"),integer(o,"quantity"));}
        case "GRANT_TEMPORARY_ITEM"->{fields(o,"kind","targetId","name","description","quantity");yield new GrantTemporaryItem(id(o,"targetId"),s(o,"name"),s(o,"description"),integer(o,"quantity"));}
        case "POSITION"->{fields(o,"kind","targetId","nodeId");yield new Move(id(o,"targetId"),id(o,"nodeId"));}
        case "MESSAGE"->{fields(o,"kind","text");yield new Message(s(o,"text"));}
        default->throw CampaignJson.bad("Unknown manual effect");});}return result;}
    private static void fields(JsonObject o,String...names){CampaignJson.fields(o,names);}
    private static JsonObject object(JsonElement value){if(value==null||!value.isJsonObject())throw CampaignJson.bad("Expected typed manual object");return value.getAsJsonObject();}
    private static String s(JsonObject o,String k){return CampaignJson.string(o,k);}
    private static long n(JsonObject o,String k){return CampaignJson.integer(o,k);}
    private static int integer(JsonObject o,String k){String v=s(o,k);if(!v.matches("0|-?[1-9][0-9]*"))throw CampaignJson.bad("Expected signed integer string");try{return Math.toIntExact(Long.parseLong(v));}catch(NumberFormatException|ArithmeticException e){throw CampaignJson.bad("Integer outside bounds");}}
    private static UUID id(JsonObject o,String k){return UUID.fromString(CampaignJson.uuid(s(o,k)));}
    private static String optional(JsonObject o,String k){return o.get(k).isJsonNull()?null:s(o,k);}
    private static UUID optionalId(JsonObject o,String k){String v=optional(o,k);return v==null?null:UUID.fromString(CampaignJson.uuid(v));}
    private static boolean b(JsonObject o,String k){if(!o.get(k).isJsonPrimitive()||!o.getAsJsonPrimitive(k).isBoolean())throw CampaignJson.bad("Expected boolean");return o.get(k).getAsBoolean();}
    private static String normalizedName(String value){try{return CharacterNamePolicy.normalize(value);}catch(IllegalArgumentException invalid){throw CampaignJson.bad("Invalid manual character name");}}
    private static void version(UUID id,long version){uuid(id);if(version<0)throw CampaignJson.bad("Invalid manual version");}
}
