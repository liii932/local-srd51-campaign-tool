package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatState.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Typed, append-only combat facts. The caller owns the common guarded transaction. */
public final class JdbcSchema2CombatRuntimeRepository {
    public static final int MAX_EVENTS = 128, MAX_COMBATANTS = 16, MAX_HISTORY_CHILD_ROWS = 65536;
    public static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);
    public record Member(UUID characterId, Identity identity, UUID preparationOperationId) { }
    public record Dependency(UUID characterId, long version) { }
    public record Event(UUID operationId, String requestDigest, Command command, List<Dependency> dependencies,
            List<UUID> characterOperations, Battle before, Resolution resolution) {
        public Event { dependencies = List.copyOf(dependencies); characterOperations = List.copyOf(characterOperations); }
        public Battle after() { return resolution.after(); }
    }
    public record History(Battle current, List<Member> members, List<Event> events) {
        public History { members = List.copyOf(members); events = List.copyOf(events); }
    }
    private static final List<String> TABLES = List.of("s2_combat_member", "s2_combat_event", "s2_combat_dependency", "s2_combat_character_result", "s2_combat_command_point", "s2_combat_command_deployment", "s2_combat_command_tie", "s2_combat_roll", "s2_combat_fact", "s2_combat_state", "s2_combat_actor", "s2_combat_initiative", "s2_combat_flow", "s2_combat_frame", "s2_combat_window", "s2_combat_opportunity", "s2_combat_opportunity_item", "s2_combat_movement", "s2_combat_movement_point", "s2_combat_attack", "s2_combat_attack_die");
    private final JdbcSchema2CharacterRuntimeRepository characters = new JdbcSchema2CharacterRuntimeRepository();

    public History read(Connection connection, UUID combatId) throws SQLException {
        uuid(combatId);
        var roots = query(connection,"s2_combat_runtime","combat_id",bytes(combatId),"combat_id",1,row -> {
            require(id(row,"combat_id").equals(combatId)); return id(row,"campaign_id");
        });
        if (roots.isEmpty()) {
            for (String table : TABLES) require(query(connection,table,"combat_id",bytes(combatId),"combat_id",1,row -> true).isEmpty());
            return null;
        }
        int[] memberIndex = {0};
        List<Member> members = query(connection,"s2_combat_member","combat_id",bytes(combatId),"member_index",MAX_COMBATANTS,row -> {
            require(integer(row,"member_index") == memberIndex[0]++);
            return new Member(id(row,"character_id"),new Identity(id(row,"run_id"),id(row,"snapshot_id")),id(row,"preparation_operation_id"));
        });
        for (Member member : members) verifyMember(connection,roots.getFirst(),member);
        Rows data = new Rows(connection,combatId);
        List<Event> events = new ArrayList<>();
        List<Map<String,Object>> headers = rows(data,"s2_combat_event",combatId,"event_sequence",MAX_EVENTS);
        for (var header : headers) {
            long sequence = number(header,"event_sequence"); require(sequence == events.size()+1L);
            Command command = command(data,combatId,sequence,header);
            List<Dependency> dependencies = indexed(data,"s2_combat_dependency",combatId,sequence,"dependency_index",MAX_COMBATANTS,row -> new Dependency(id(row,"character_id"),number(row,"character_version")));
            List<UUID> operations = indexed(data,"s2_combat_character_result",combatId,sequence,"result_index",MAX_COMBATANTS * 2,row -> id(row,"character_operation_id"));
            Battle before = sequence == 1 ? null : state(data,combatId,sequence,"BEFORE",roots.getFirst());
            Battle after = state(data,combatId,sequence,"AFTER",roots.getFirst());
            List<Roll> rolls = indexed(data,"s2_combat_roll",combatId,sequence,"roll_index",512,row -> new Roll(ascii(row,"purpose"),id(row,"subject_id"),integer(row,"sides"),integer(row,"die_value")));
            List<CombatFact> facts = indexed(data,"s2_combat_fact",combatId,sequence,"fact_index",512,row -> new CombatFact(ascii(row,"fact_kind"),nullableId(row,"subject_id"),nullableId(row,"reference_key"),nullableAscii(row,"reason")));
            List<HealthChange> changes = new ArrayList<>();
            for (UUID operation : operations) changes.add(characterResult(connection,combatId,id(header,"operation_id"),operation));
            events.add(new Event(id(header,"operation_id"),ascii(header,"request_digest"),command,dependencies,operations,before,new Resolution(after,changes,rolls,facts)));
        }
        require(!events.isEmpty());
        History history = new History(events.getLast().after(),members,events); validate(history);
        verifyDependencies(connection,history);
        // Re-encode the exact typed graph and compare every physical row. This also rejects
        // surplus states, orphaned child rows, wrong phases and hidden rows beyond a count.
        verifyRows(data,history);
        return history;
    }

    public List<History> findByCampaign(Connection connection, UUID campaignId) throws SQLException {
        uuid(campaignId);
        List<UUID> ids = query(connection,"s2_combat_runtime","campaign_id",bytes(campaignId),"combat_id",MAX_EVENTS,row -> id(row,"combat_id"));
        List<History> result = new ArrayList<>(); for (UUID id : ids) result.add(read(connection,id));
        require(result.stream().filter(h -> h.current().status() == Status.ACTIVE).count() <= 1);
        return List.copyOf(result);
    }
    public Event findOperation(Connection connection, UUID operationId) throws SQLException {
        uuid(operationId);
        List<UUID> ids = query(connection,"s2_combat_event","operation_id",bytes(operationId),"event_sequence",1,row -> id(row,"combat_id"));
        if (ids.isEmpty()) return null;
        return read(connection,ids.getFirst()).events().stream().filter(e -> e.operationId().equals(operationId)).findFirst().orElseThrow(JdbcSchema2CombatRuntimeRepository::corrupt);
    }
    public void append(Connection connection, Event event) throws SQLException {
        History previous = read(connection,event.after().combatId()); require(previous != null); append(connection,previous.members(),event);
    }
    public void append(Connection connection, List<Member> members, Event event) throws SQLException {
        BusinessWriteTransactions.requireGuarded(connection);
        History previous = read(connection,event.after().combatId());
        require((previous == null) == (event.before() == null));
        require(previous == null || previous.current().equals(event.before()) && previous.members().equals(members));
        require(findOperation(connection,event.operationId()) == null);
        List<Event> events = new ArrayList<>(previous == null ? List.of() : previous.events()); events.add(event);
        History expected = new History(event.after(),members,events); validate(expected);
        require(characters.findOperation(connection,event.operationId()) == null);
        for (Member member : members) verifyMember(connection,event.after().campaignId(),member);
        verifyDependencies(connection,expected);
        verifyCharacterResults(connection,event);
        if (previous == null) writeRoot(connection,expected);
        writeEvent(connection,event);
        require(expected.equals(read(connection,event.after().combatId())));
    }
    public void restore(Connection connection, History history) throws SQLException {
        BusinessWriteTransactions.requireGuarded(connection); validate(history);
        require(read(connection,history.current().combatId()) == null);
        for (Member member : history.members()) verifyMember(connection,history.current().campaignId(),member);
        verifyDependencies(connection,history);
        for (Event event : history.events()) { require(findOperation(connection,event.operationId()) == null && characters.findOperation(connection,event.operationId()) == null); verifyCharacterResults(connection,event); }
        writeRoot(connection,history); for (Event event : history.events()) writeEvent(connection,event);
        require(history.equals(read(connection,history.current().combatId())));
    }

    private void verifyMember(Connection connection, UUID campaign, Member member) throws SQLException {
        var history = characters.read(connection,member.characterId()); require(history != null);
        var state = history.current();
        require(state.campaignId().equals(campaign) && state.identity().equals(member.identity()) && state.preparationOperationId().equals(member.preparationOperationId()));
    }
    private HealthChange characterResult(Connection connection, UUID combat, UUID combatOperation, UUID operation) throws SQLException {
        var event = characters.findOperation(connection,operation); require(event != null && event.before() != null && event.healthResult() != null);
        var facts = query(connection,"s2_character_combat_input","operation_id",bytes(operation),"event_sequence",1,row -> {
            require(id(row,"operation_id").equals(operation) && id(row,"combat_id").equals(combat) && id(row,"combat_operation_id").equals(combatOperation)
                    && id(row,"character_id").equals(event.after().characterId()) && number(row,"event_sequence") == event.after().version()+1);
            boolean death = event.kind().equals("COMBAT_DEATH_SAVE_RESOLVED"), time = event.kind().equals("COMBAT_TIME_ADVANCED");
            require(death || time || event.kind().equals("COMBAT_DAMAGE_APPLIED"));
            return new HealthChange(event.after().characterId(),event.before(),event.after(),id(row,"frame_key"),integer(row,"damage_amount"),bool(row,"critical"),event.healthResult(),death,time,integer(row,"seconds_advanced"));
        });
        require(facts.size() == 1); return facts.getFirst();
    }
    private void verifyCharacterResults(Connection connection, Event event) throws SQLException {
        require(event.characterOperations().size() == event.resolution().healthChanges().size());
        for (int i=0;i<event.characterOperations().size();i++) require(characterResult(connection,event.after().combatId(),event.operationId(),event.characterOperations().get(i)).equals(event.resolution().healthChanges().get(i)));
    }
    private void verifyDependencies(Connection connection, History history) throws SQLException {
        Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> roster = new HashMap<>();
        for (Member member : history.members()) roster.put(member.characterId(),characters.read(connection,member.characterId()));
        for (Event event : history.events()) {
            Map<UUID,JdbcSchema2CharacterRuntimeRepository.State> states = new HashMap<>();
            for (Dependency dependency : event.dependencies()) {
                var character = roster.get(dependency.characterId()); require(character != null);
                var matches = character.events().stream().map(JdbcSchema2CharacterRuntimeRepository.Event::after)
                        .filter(state -> state.version() == dependency.version()).toList();
                require(matches.size() == 1); states.put(dependency.characterId(),matches.getFirst());
            }
            for (HealthChange change : event.resolution().healthChanges()) {
                require(change.before().equals(states.get(change.characterId())));
                states.put(change.characterId(),change.after());
            }
        }
    }

    private static Command command(Rows data, UUID combat, long sequence, Map<String,Object> row) throws SQLException {
        List<Position> path = indexed(data,"s2_combat_command_point",combat,sequence,"point_index",64,r -> new Position(signed(r,"x"),signed(r,"y")));
        List<Deployment> deployments = indexed(data,"s2_combat_command_deployment",combat,sequence,"deployment_index",MAX_COMBATANTS,r -> new Deployment(id(r,"character_id"),new Position(signed(r,"x"),signed(r,"y")),integer(r,"side")));
        List<UUID> ties = indexed(data,"s2_combat_command_tie",combat,sequence,"tie_index",MAX_COMBATANTS,r -> id(r,"character_id"));
        return new Command(en(Kind.class,row,"command_kind"),nullableId(row,"actor_id"),nullableId(row,"target_id"),nullableId(row,"item_id"),path,deployments,ties,
                nullableId(row,"flow_key"),nullableId(row,"leaf_key"),nullableId(row,"window_key"),nullableId(row,"opportunity_key"));
    }

    private static Battle state(Rows data, UUID combat, long sequence, String phase, UUID campaign) throws SQLException {
        var roots = scoped(data,"s2_combat_state",combat,sequence,phase,"state_index",1); require(roots.size()==1); var root=roots.getFirst();
        List<Combatant> actors = new ArrayList<>();
        for (var row:scoped(data,"s2_combat_actor",combat,sequence,phase,"actor_index",MAX_COMBATANTS)) {
            AttackSequence attack = nullableId(row,"sequence_key") == null ? null : new AttackSequence(id(row,"sequence_key"),integer(row,"attack_limit"),integer(row,"performed"),integer(row,"forfeited"),bool(row,"sequence_closed"));
            actors.add(new Combatant(id(row,"character_id"),new Position(signed(row,"x"),signed(row,"y")),integer(row,"side"),bool(row,"action_available"),bool(row,"reaction_available"),integer(row,"movement_spent_half_feet"),integer(row,"movement_bonus"),bool(row,"disengaged"),bool(row,"dodging"),attack,bool(row,"prone")));
        }
        List<Initiative> initiative = new ArrayList<>();
        for(var row:scoped(data,"s2_combat_initiative",combat,sequence,phase,"initiative_index",MAX_COMBATANTS)) initiative.add(new Initiative(id(row,"character_id"),integer(row,"d20"),signed(row,"dexterity_modifier"),signed(row,"total"),integer(row,"ordinal")));
        List<Flow> flows = new ArrayList<>();
        for(var row:scoped(data,"s2_combat_flow",combat,sequence,phase,"flow_index",128)) {
            int flow=integer(row,"flow_index");
            List<Frame> frames = new ArrayList<>();
            for(var r:flowRows(data,"s2_combat_frame",combat,sequence,phase,flow,"frame_index",1024)) frames.add(new Frame(id(r,"frame_key"),nullableId(r,"parent_frame_key"),en(FrameKind.class,r,"frame_kind"),id(r,"owner_key"),en(Invocation.class,r,"invocation"),en(FrameStatus.class,r,"frame_status"),en(Stage.class,r,"stage"),nullableId(r,"window_key"),nullableId(r,"opportunity_key")));
            List<Window> windows = new ArrayList<>();
            for(var r:flowRows(data,"s2_combat_window",combat,sequence,phase,flow,"window_index",1024)) {
                int window=integer(r,"window_index"); List<Opportunity> opportunities=new ArrayList<>();
                for(var o:flowRows(data,"s2_combat_opportunity",combat,sequence,phase,flow,"window_index, opportunity_index",131072)) if(integer(o,"window_index")==window) {
                    int opportunity=integer(o,"opportunity_index"); List<UUID> items=new ArrayList<>();
                    for(var item:flowRows(data,"s2_combat_opportunity_item",combat,sequence,phase,flow,"window_index, opportunity_index, item_index",MAX_HISTORY_CHILD_ROWS)) if(integer(item,"window_index")==window && integer(item,"opportunity_index")==opportunity) items.add(id(item,"item_id"));
                    opportunities.add(new Opportunity(id(o,"opportunity_key"),id(o,"actor_id"),integer(o,"priority"),items,en(OpportunityStatus.class,o,"opportunity_status"),nullableId(o,"selection_frame_key"),nullableId(o,"attack_frame_key"),nullableAscii(o,"reason")));
                }
                windows.add(new Window(id(r,"window_key"),id(r,"owner_frame_key"),integer(r,"checkpoint_ordinal"),integer(r,"boundary_index"),en(WindowStatus.class,r,"window_status"),opportunities));
            }
            var moves=flowRows(data,"s2_combat_movement",combat,sequence,phase,flow,"movement_index",1); Movement movement=null;
            if(!moves.isEmpty()) { var r=moves.getFirst(); List<Position> points=new ArrayList<>();
                for(var p:flowRows(data,"s2_combat_movement_point",combat,sequence,phase,flow,"point_index",64)) points.add(new Position(signed(p,"x"),signed(p,"y")));
                movement=new Movement(id(r,"movement_key"),id(r,"actor_id"),new Position(signed(r,"origin_x"),signed(r,"origin_y")),points,integer(r,"cursor"),integer(r,"spent"),en(MovementStatus.class,r,"movement_status"),nullableAscii(r,"stop_reason"));
            }
            List<AttackAttempt> attacks=new ArrayList<>();
            for(var r:flowRows(data,"s2_combat_attack",combat,sequence,phase,flow,"attack_index",1024)) {
                int attack=integer(r,"attack_index");List<Integer> dice=new ArrayList<>();
                for(var d:flowRows(data,"s2_combat_attack_die",combat,sequence,phase,flow,"attack_index, die_index",MAX_HISTORY_CHILD_ROWS)) if(integer(d,"attack_index")==attack) dice.add(integer(d,"die_value"));
                attacks.add(new AttackAttempt(id(r,"frame_key"),id(r,"actor_id"),id(r,"target_id"),id(r,"item_id"),nullableId(r,"sequence_key"),integer(r,"attack_ordinal"),bool(r,"opportunity_attack"),integer(r,"first_d20"),nullableInteger(r,"second_d20"),integer(r,"selected_d20"),signed(r,"ability_modifier"),integer(r,"proficiency_bonus"),integer(r,"armor_class"),bool(r,"advantage"),bool(r,"disadvantage"),bool(r,"critical"),bool(r,"hit"),dice,integer(r,"damage_amount")));
            }
            flows.add(new Flow(id(row,"flow_key"),id(row,"root_frame_key"),nullableId(row,"leaf_frame_key"),en(FlowStatus.class,row,"flow_status"),frames,windows,movement,attacks));
        }
        return new Battle(combat,campaign,new RuleIdentity(ascii(root,"rule_module"),ascii(root,"rule_type"),ascii(root,"rule_key")),en(Status.class,root,"combat_status"),integer(root,"round_number"),number(root,"turn_number"),integer(root,"active_ordinal"),number(root,"row_version"),actors,initiative,flows);
    }

    // Row writing and shape validation are deliberately explicit: no opaque state blob exists.
    private static void writeRoot(Connection connection, History history) throws SQLException {
        put(connection,"s2_combat_runtime",row("combat_id",bytes(history.current().combatId()),"campaign_id",bytes(history.current().campaignId())));
        int index=0;for(Member member:history.members())put(connection,"s2_combat_member",row("combat_id",bytes(history.current().combatId()),"member_index",index++,"character_id",bytes(member.characterId()),"run_id",member.identity().runBytes(),"snapshot_id",member.identity().snapshotBytes(),"preparation_operation_id",bytes(member.preparationOperationId())));
    }
    private static void writeEvent(Connection connection, Event event) throws SQLException { for(var entry:eventRows(event).entrySet())for(var row:entry.getValue())put(connection,entry.getKey(),row); }
    private static Map<String,List<Map<String,Object>>> eventRows(Event event) throws SQLException {
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        UUID combat=event.after().combatId();long sequence=event.after().version()+1;Command c=event.command();
        add(result,"s2_combat_event",base(combat,sequence,"operation_id",bytes(event.operationId()),"request_digest",a(event.requestDigest()),"command_kind",a(c.kind()),"actor_id",b(c.actorId()),"target_id",b(c.targetId()),"item_id",b(c.itemId()),"flow_key",b(c.flowKey()),"leaf_key",b(c.leafKey()),"window_key",b(c.windowKey()),"opportunity_key",b(c.opportunityKey())));
        for(int i=0;i<c.path().size();i++){Position p=c.path().get(i);add(result,"s2_combat_command_point",base(combat,sequence,"point_index",i,"x",p.x(),"y",p.y()));}
        for(int i=0;i<c.deployments().size();i++){Deployment d=c.deployments().get(i);add(result,"s2_combat_command_deployment",base(combat,sequence,"deployment_index",i,"character_id",bytes(d.characterId()),"x",d.position().x(),"y",d.position().y(),"side",d.side()));}
        for(int i=0;i<c.tieBreakOrder().size();i++)add(result,"s2_combat_command_tie",base(combat,sequence,"tie_index",i,"character_id",bytes(c.tieBreakOrder().get(i))));
        for(int i=0;i<event.dependencies().size();i++){Dependency d=event.dependencies().get(i);add(result,"s2_combat_dependency",base(combat,sequence,"dependency_index",i,"character_id",bytes(d.characterId()),"character_version",d.version()));}
        for(int i=0;i<event.characterOperations().size();i++)add(result,"s2_combat_character_result",base(combat,sequence,"result_index",i,"character_operation_id",bytes(event.characterOperations().get(i))));
        for(int i=0;i<event.resolution().rolls().size();i++){Roll r=event.resolution().rolls().get(i);add(result,"s2_combat_roll",base(combat,sequence,"roll_index",i,"purpose",a(r.purpose()),"subject_id",bytes(r.subjectId()),"sides",r.sides(),"die_value",r.value()));}
        for(int i=0;i<event.resolution().facts().size();i++){CombatFact f=event.resolution().facts().get(i);add(result,"s2_combat_fact",base(combat,sequence,"fact_index",i,"fact_kind",a(f.kind()),"subject_id",b(f.subjectId()),"reference_key",b(f.referenceKey()),"reason",a(f.reason())));}
        if(event.before()!=null)stateRows(result,event.before(),sequence,"BEFORE");stateRows(result,event.after(),sequence,"AFTER");return result;
    }
    private static void stateRows(Map<String,List<Map<String,Object>>> out,Battle state,long sequence,String phase)throws SQLException{
        UUID combat=state.combatId();
        add(out,"s2_combat_state",phase(combat,sequence,phase,"state_index",0,"rule_module",a(state.combatRule().moduleKey()),"rule_type",a(state.combatRule().ruleType()),"rule_key",a(state.combatRule().ruleKey()),"combat_status",a(state.status()),"round_number",state.round(),"turn_number",state.turnNumber(),"active_ordinal",state.activeOrdinal(),"row_version",state.version()));
        for(int i=0;i<state.combatants().size();i++){Combatant c=state.combatants().get(i);AttackSequence s=c.sequence();add(out,"s2_combat_actor",phase(combat,sequence,phase,"actor_index",i,"character_id",bytes(c.characterId()),"x",c.position().x(),"y",c.position().y(),"side",c.side(),"action_available",bit(c.actionAvailable()),"reaction_available",bit(c.reactionAvailable()),"movement_spent_half_feet",c.movementSpentHalfFeet(),"movement_bonus",c.movementBonus(),"disengaged",bit(c.disengaged()),"dodging",bit(c.dodging()),"prone",bit(c.prone()),"sequence_key",s==null?null:bytes(s.sequenceKey()),"attack_limit",s==null?null:s.limit(),"performed",s==null?null:s.performed(),"forfeited",s==null?null:s.forfeited(),"sequence_closed",s==null?null:bit(s.closed())));}
        for(int i=0;i<state.initiative().size();i++){Initiative n=state.initiative().get(i);add(out,"s2_combat_initiative",phase(combat,sequence,phase,"initiative_index",i,"character_id",bytes(n.characterId()),"d20",n.d20(),"dexterity_modifier",n.dexterityModifier(),"total",n.total(),"ordinal",n.ordinal()));}
        for(int fi=0;fi<state.flows().size();fi++){Flow f=state.flows().get(fi);
            add(out,"s2_combat_flow",flow(combat,sequence,phase,fi,"flow_key",bytes(f.flowKey()),"root_frame_key",bytes(f.rootFrameKey()),"leaf_frame_key",b(f.leafFrameKey()),"flow_status",a(f.status())));
            for(int i=0;i<f.frames().size();i++){Frame r=f.frames().get(i);add(out,"s2_combat_frame",flow(combat,sequence,phase,fi,"frame_index",i,"frame_key",bytes(r.frameKey()),"parent_frame_key",b(r.parentFrameKey()),"frame_kind",a(r.kind()),"owner_key",bytes(r.ownerKey()),"invocation",a(r.invocation()),"frame_status",a(r.status()),"stage",a(r.stage()),"window_key",b(r.windowKey()),"opportunity_key",b(r.opportunityKey())));}
            for(int wi=0;wi<f.windows().size();wi++){Window w=f.windows().get(wi);add(out,"s2_combat_window",flow(combat,sequence,phase,fi,"window_index",wi,"window_key",bytes(w.windowKey()),"owner_frame_key",bytes(w.ownerFrameKey()),"checkpoint_ordinal",w.checkpointOrdinal(),"boundary_index",w.boundaryIndex(),"window_status",a(w.status())));
                for(int oi=0;oi<w.opportunities().size();oi++){Opportunity o=w.opportunities().get(oi);add(out,"s2_combat_opportunity",flow(combat,sequence,phase,fi,"window_index",wi,"opportunity_index",oi,"opportunity_key",bytes(o.opportunityKey()),"actor_id",bytes(o.actorId()),"priority",o.priority(),"opportunity_status",a(o.status()),"selection_frame_key",b(o.selectionFrameKey()),"attack_frame_key",b(o.attackFrameKey()),"reason",a(o.reason())));
                    for(int ii=0;ii<o.allowedItems().size();ii++)add(out,"s2_combat_opportunity_item",flow(combat,sequence,phase,fi,"window_index",wi,"opportunity_index",oi,"item_index",ii,"item_id",bytes(o.allowedItems().get(ii))));}}
            if(f.movement()!=null){Movement m=f.movement();add(out,"s2_combat_movement",flow(combat,sequence,phase,fi,"movement_index",0,"movement_key",bytes(m.movementKey()),"actor_id",bytes(m.actorId()),"origin_x",m.origin().x(),"origin_y",m.origin().y(),"cursor",m.cursor(),"spent",m.spent(),"movement_status",a(m.status()),"stop_reason",a(m.stopReason())));
                for(int i=0;i<m.path().size();i++){Position p=m.path().get(i);add(out,"s2_combat_movement_point",flow(combat,sequence,phase,fi,"point_index",i,"x",p.x(),"y",p.y()));}}
            for(int ai=0;ai<f.attacks().size();ai++){AttackAttempt t=f.attacks().get(ai);add(out,"s2_combat_attack",flow(combat,sequence,phase,fi,"attack_index",ai,"frame_key",bytes(t.frameKey()),"actor_id",bytes(t.actorId()),"target_id",bytes(t.targetId()),"item_id",bytes(t.itemId()),"sequence_key",b(t.sequenceKey()),"attack_ordinal",t.attackOrdinal(),"opportunity_attack",bit(t.opportunityAttack()),"first_d20",t.firstD20(),"second_d20",t.secondD20(),"selected_d20",t.selectedD20(),"ability_modifier",t.abilityModifier(),"proficiency_bonus",t.proficiencyBonus(),"armor_class",t.armorClass(),"advantage",bit(t.advantage()),"disadvantage",bit(t.disadvantage()),"critical",bit(t.critical()),"hit",bit(t.hit()),"damage_amount",t.damageAmount()));
                for(int i=0;i<t.damageDice().size();i++)add(out,"s2_combat_attack_die",flow(combat,sequence,phase,fi,"attack_index",ai,"die_index",i,"die_value",t.damageDice().get(i)));}
        }
    }

    public static void validate(History history)throws SQLException{
        require(history!=null&&!history.events().isEmpty()&&history.events().size()<=MAX_EVENTS&&history.members().size()>=2&&history.members().size()<=MAX_COMBATANTS);
        List<UUID> members=history.members().stream().map(Member::characterId).toList();require(new HashSet<>(members).size()==members.size()&&members.equals(members.stream().sorted(UUID_ORDER).toList()));
        for(Member m:history.members()){uuid(m.characterId());uuid(m.identity().runId());uuid(m.identity().snapshotId());uuid(m.preparationOperationId());}
        Set<UUID> operations=new HashSet<>(),children=new HashSet<>();Battle previous=null;long rows=0;
        for(Event e:history.events()){
            uuid(e.operationId());require(operations.add(e.operationId())&&e.requestDigest()!=null&&e.requestDigest().matches("[0-9a-f]{64}")&&e.command()!=null&&e.resolution()!=null&&Objects.equals(previous,e.before()));
            validateCommand(e.command());
            require(e.after().version()==(previous==null?0:previous.version()+1));validateState(e.after(),members);
            require(previous==null?e.command().kind()==Kind.START:e.command().kind()!=Kind.START&&previous.combatId().equals(e.after().combatId())&&previous.campaignId().equals(e.after().campaignId())&&previous.combatRule().equals(e.after().combatRule()));
            require(e.dependencies().stream().map(Dependency::characterId).toList().equals(members));
            for(Dependency d:e.dependencies())require(d.version()>=0&&d.version()<JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS);
            require(e.characterOperations().size()==e.resolution().healthChanges().size()&&e.characterOperations().size()<=MAX_COMBATANTS * 2);
            for(UUID child:e.characterOperations()){uuid(child);require(children.add(child)&&!operations.contains(child));}
            require(e.resolution().rolls().size()<=512&&e.resolution().facts().size()<=512&&e.command().path().size()<=64&&e.command().deployments().size()<=MAX_COMBATANTS&&e.command().tieBreakOrder().size()<=MAX_COMBATANTS);
            for(Roll r:e.resolution().rolls()){uuid(r.subjectId());require(token(r.purpose())&&r.sides()>=2&&r.sides()<=20&&r.value()>=1&&r.value()<=r.sides());}
            for(CombatFact f:e.resolution().facts())require(token(f.kind())&&(f.reason()==null||token(f.reason())));
            for(var rs:eventRows(e).values())rows+=rs.size();previous=e.after();
        }
        require(previous.equals(history.current())&&Collections.disjoint(operations,children)&&rows<=MAX_HISTORY_CHILD_ROWS);
    }
    public static void checkCandidateCapacity(History previous,Battle candidate)throws SQLException{
        require(previous==null||previous.events().size()<MAX_EVENTS);long count=0;
        if(previous!=null)for(Event e:previous.events())for(var rows:eventRows(e).values())count+=rows.size();
        Map<String,List<Map<String,Object>>> next=new LinkedHashMap<>();if(previous!=null)stateRows(next,previous.current(),candidate.version()+1,"BEFORE");stateRows(next,candidate,candidate.version()+1,"AFTER");
        for(var rows:next.values())count+=rows.size();require(count+2048<=MAX_HISTORY_CHILD_ROWS);
    }
    /** Reserves the complete mandatory suffix before an accepted prefix consumes any die. */
    public static void checkReservedCapacity(History previous, Battle before, int suffixEvents,
            int rosterSize, int remainingPath) throws SQLException {
        if (suffixEvents < 0 || rosterSize < 2 || rosterSize > MAX_COMBATANTS || remainingPath < 0 || remainingPath > 64
                || (previous == null ? 0 : previous.events().size()) + 1L + suffixEvents > MAX_EVENTS)
            throw new SQLException("S2_COMBAT_CAPACITY");
        long retained = 0;
        if (previous != null) for (Event event : previous.events())
            for (var rows : eventRows(event).values()) retained += rows.size();
        long stateSize = 1L + 2L * rosterSize;
        if (before != null) {
            Map<String,List<Map<String,Object>>> rows = new LinkedHashMap<>();
            stateRows(rows,before,before.version()+2,"BEFORE");
            stateSize = rows.values().stream().mapToLong(List::size).sum();
        }
        // The root allowance includes one ordinary attack flow. Each remaining movement
        // boundary can retain a window, all selection/attack frames, source items and dice.
        // Every suffix event saves both snapshots.
        long addedState = 64L + 100L * rosterSize * remainingPath;
        long perEvent = 2048L + 2L * (stateSize + addedState);
        if (retained + (suffixEvents + 1L) * perEvent > MAX_HISTORY_CHILD_ROWS)
            throw new SQLException("S2_COMBAT_CAPACITY");
    }
    private static void validateState(Battle b,List<UUID> members)throws SQLException{
        uuid(b.combatId());uuid(b.campaignId());require(b.combatRule()!=null&&b.combatRule().ruleType().equals("adventure.combat")&&b.status()!=null&&b.round()>=1&&b.turnNumber()>=1&&b.activeOrdinal()>=0&&b.activeOrdinal()<members.size()&&b.version()>=0);
        require(b.combatants().stream().map(Combatant::characterId).toList().equals(members)&&b.initiative().size()==members.size());
        Set<UUID> initiative=new HashSet<>();for(int i=0;i<b.initiative().size();i++){Initiative n=b.initiative().get(i);require(initiative.add(n.characterId())&&members.contains(n.characterId())&&n.ordinal()==i&&n.d20()>=1&&n.d20()<=20&&n.dexterityModifier()>=-5&&n.dexterityModifier()<=10&&n.total()==n.d20()+n.dexterityModifier());}
        for(Combatant c:b.combatants()){require(c.position()!=null&&c.side()>=0&&c.side()<=15&&c.movementSpentHalfFeet()>=0&&c.movementBonus()>=0);if(c.sequence()!=null){AttackSequence s=c.sequence();uuid(s.sequenceKey());require(s.limit()>=1&&s.limit()<=20&&s.performed()>=0&&s.forfeited()>=0&&s.performed()+s.forfeited()<=s.limit()&&s.closed()==(s.performed()+s.forfeited()==s.limit()));}}
        require(b.flows().size()<=128&&b.flows().stream().filter(f->f.status()==FlowStatus.ACTIVE).count()<=1);Set<UUID> keys=new HashSet<>();
        for(Flow f:b.flows()){
            uuid(f.flowKey());require(keys.add(f.flowKey())&&f.frames().size()>=1&&f.frames().size()<=1024&&f.windows().size()<=1024&&f.attacks().size()<=1024);
            Map<UUID,Frame> frames=new HashMap<>();for(Frame r:f.frames()){uuid(r.frameKey());uuid(r.ownerKey());require(keys.add(r.frameKey())&&frames.put(r.frameKey(),r)==null&&r.kind()!=null&&r.status()!=null&&r.invocation()!=null&&r.stage()!=null);}
            Frame root=frames.get(f.rootFrameKey());require(root!=null&&root.parentFrameKey()==null);require(f.status()==FlowStatus.ACTIVE?frames.containsKey(f.leafFrameKey()):f.leafFrameKey()==null);
            for(Frame r:f.frames()){Set<UUID> chain=new HashSet<>();Frame cursor=r;while(cursor.parentFrameKey()!=null){require(chain.add(cursor.frameKey())&&chain.size()<=32);cursor=frames.get(cursor.parentFrameKey());require(cursor!=null);}require(cursor.frameKey().equals(f.rootFrameKey()));}
            Set<UUID> windows=new HashSet<>();for(Window w:f.windows()){uuid(w.windowKey());require(keys.add(w.windowKey())&&windows.add(w.windowKey())&&frames.containsKey(w.ownerFrameKey())&&w.checkpointOrdinal()>=0&&w.checkpointOrdinal()<64&&w.boundaryIndex()>=0&&w.boundaryIndex()<64&&w.opportunities().size()<=128);Set<UUID>actors=new HashSet<>();
                for(int i=0;i<w.opportunities().size();i++){Opportunity o=w.opportunities().get(i);uuid(o.opportunityKey());require(keys.add(o.opportunityKey())&&actors.add(o.actorId())&&members.contains(o.actorId())&&o.priority()==i&&o.allowedItems().size()>=1&&o.allowedItems().size()<=32&&new HashSet<>(o.allowedItems()).size()==o.allowedItems().size()&&(o.selectionFrameKey()==null?o.status()==OpportunityStatus.PENDING:frames.containsKey(o.selectionFrameKey()))&&(o.attackFrameKey()==null||frames.containsKey(o.attackFrameKey()))&&(o.reason()==null||token(o.reason())));for(UUID item:o.allowedItems())uuid(item);}}
            if(f.movement()!=null){Movement m=f.movement();uuid(m.movementKey());require(members.contains(m.actorId())&&m.path().size()>=1&&m.path().size()<=64&&m.cursor()>=0&&m.cursor()<=m.path().size()&&m.spent()>=0&&m.status()!=null&&(m.stopReason()==null||token(m.stopReason())));}
            for(AttackAttempt t:f.attacks())require(frames.containsKey(t.frameKey())&&members.contains(t.actorId())&&members.contains(t.targetId())&&!t.actorId().equals(t.targetId())&&t.firstD20()>=1&&t.firstD20()<=20&&(t.secondD20()==null||t.secondD20()>=1&&t.secondD20()<=20)&&t.selectedD20()>=1&&t.selectedD20()<=20&&t.damageDice().size()<=40&&t.damageDice().stream().allMatch(v->v!=null&&v>=1&&v<=12)&&t.damageAmount()>=0);
        }
    }
    private static void validateCommand(Command c) throws SQLException {
        require(c.kind()!=null);
        boolean start=c.kind()==Kind.START,move=c.kind()==Kind.MOVE,attack=c.kind()==Kind.ATTACK,
                declare=c.kind()==Kind.DECLARE_OPPORTUNITY_ATTACK,reaction=declare||c.kind()==Kind.PASS_REACTION||c.kind()==Kind.ADVANCE_WINDOW,
                continuation=c.kind()==Kind.CONTINUE_MOVEMENT;
        require(start?c.actorId()==null:c.actorId()!=null);
        require((c.targetId()!=null)==attack&&(c.itemId()!=null)==(attack||declare)
                &&(move?!c.path().isEmpty()&&c.path().size()<=64:c.path().isEmpty())
                &&(start?c.deployments().size()>=2&&c.deployments().size()<=MAX_COMBATANTS:c.deployments().isEmpty())
                &&(start||c.tieBreakOrder().isEmpty())&&(c.flowKey()!=null)==(reaction||continuation)
                &&(c.leafKey()!=null)==(reaction||continuation)&&(c.windowKey()!=null)==(reaction||continuation)
                &&(c.opportunityKey()!=null)==reaction);
        for(UUID id:Arrays.asList(c.actorId(),c.targetId(),c.itemId(),c.flowKey(),c.leafKey(),c.windowKey(),c.opportunityKey()))if(id!=null)uuid(id);
    }

    private static void verifyRows(Rows data,History history)throws SQLException{
        Map<String,List<Map<String,Object>>> expected=new LinkedHashMap<>();for(Event e:history.events())eventRows(e).forEach((table,rs)->expected.computeIfAbsent(table,k->new ArrayList<>()).addAll(rs));
        for(String table:TABLES){if(table.equals("s2_combat_member"))continue;
            List<Map<String,Object>> actual=rows(data,table,history.current().combatId(),"combat_id",MAX_HISTORY_CHILD_ROWS), want=expected.getOrDefault(table,List.of());
            require(actual.size()==want.size());Map<Map<String,Object>,Integer> counts=new HashMap<>();
            for(var row:actual)counts.merge(normalized(row),1,Integer::sum);
            for(var row:want){var key=normalized(row);Integer count=counts.get(key);require(count!=null&&count>0);if(count==1)counts.remove(key);else counts.put(key,count-1);}require(counts.isEmpty());
        }
    }
    private static Map<String,Object> normalized(Map<String,Object> row){
        Map<String,Object> result=new TreeMap<>();row.forEach((key,value)->result.put(key,value instanceof byte[]bytes?ByteBuffer.wrap(bytes):value instanceof Number number?number.longValue():value));return result;
    }
    private static List<Map<String,Object>> scoped(Rows c,String table,UUID combat,long sequence,String phase,String order,int maximum)throws SQLException{List<Map<String,Object>> result=new ArrayList<>();for(var row:rows(c,table,combat,"event_sequence, phase, "+order,MAX_HISTORY_CHILD_ROWS))if(number(row,"event_sequence")==sequence&&ascii(row,"phase").equals(phase))result.add(row);require(result.size()<=maximum);return result;}
    private static List<Map<String,Object>> flowRows(Rows c,String table,UUID combat,long sequence,String phase,int flow,String order,int maximum)throws SQLException{List<Map<String,Object>> result=new ArrayList<>();for(var row:scoped(c,table,combat,sequence,phase,"flow_index, "+order,MAX_HISTORY_CHILD_ROWS))if(integer(row,"flow_index")==flow)result.add(row);require(result.size()<=maximum);return result;}
    private static<T>List<T>indexed(Rows c,String table,UUID combat,long sequence,String index,int maximum,MapReader<T> reader)throws SQLException{List<T>result=new ArrayList<>();for(var row:rows(c,table,combat,"event_sequence, "+index,MAX_HISTORY_CHILD_ROWS))if(number(row,"event_sequence")==sequence){require(integer(row,index)==result.size()&&result.size()<maximum);result.add(reader.read(row));}return result;}
    private record Rows(Connection connection, UUID combat, Map<String,List<Map<String,Object>>> cache) {
        Rows(Connection connection, UUID combat) { this(connection,combat,new HashMap<>()); }
    }
    private static List<Map<String,Object>>rows(Rows data,String table,UUID combat,String order,int maximum)throws SQLException{
        require(data.combat().equals(combat));
        var stored=data.cache().get(table);
        if(stored==null){stored=query(data.connection(),table,"combat_id",bytes(combat),"combat_id",MAX_HISTORY_CHILD_ROWS,JdbcSchema2CombatRuntimeRepository::map);data.cache().put(table,stored);}
        require(stored.size()<=MAX_HISTORY_CHILD_ROWS);List<Map<String,Object>> result=new ArrayList<>(stored);
        String[] columns=order.split(", ");result.sort((left,right)->{for(String column:columns){Object a=left.get(column),b=right.get(column);int compared;
            if(a instanceof Number x&&b instanceof Number y)compared=Long.compare(x.longValue(),y.longValue());else if(a instanceof byte[]x&&b instanceof byte[]y)compared=Arrays.compareUnsigned(x,y);else compared=Objects.toString(a).compareTo(Objects.toString(b));if(compared!=0)return compared;}return 0;});
        require(result.size()<=maximum);return result;
    }
    private static Map<String,Object>map(ResultSet row)throws SQLException{ResultSetMetaData metadata=row.getMetaData();Map<String,Object>result=new LinkedHashMap<>();for(int i=1;i<=metadata.getColumnCount();i++)result.put(metadata.getColumnLabel(i),row.getObject(i));return result;}
    private static<T>List<T>query(Connection c,String table,String key,byte[] value,String order,int maximum,Reader<T> reader)throws SQLException{List<T>result=new ArrayList<>();try(PreparedStatement s=c.prepareStatement("SELECT * FROM "+table+" WHERE "+key+" = ? ORDER BY "+order+" LIMIT "+(maximum+1))){s.setBytes(1,value);s.setQueryTimeout(5);s.setMaxRows(maximum+1);try(ResultSet row=s.executeQuery()){while(row.next()){require(result.size()<maximum);result.add(reader.read(row));}}}return result;}
    private static void put(Connection c,String table,Map<String,Object> row)throws SQLException{String columns=String.join(", ",row.keySet());try(PreparedStatement s=c.prepareStatement("INSERT INTO "+table+" ("+columns+") VALUES ("+String.join(", ",Collections.nCopies(row.size(),"?"))+")")){int i=1;for(Object value:row.values()){if(value==null)s.setNull(i,Types.NULL);else if(value instanceof byte[]v)s.setBytes(i,v);else if(value instanceof Integer v)s.setInt(i,v);else if(value instanceof Long v)s.setLong(i,v);else throw corrupt();i++;}s.setQueryTimeout(5);require(s.executeUpdate()==1);}}
    private static Map<String,Object>row(Object...values){Map<String,Object>r=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)r.put((String)values[i],values[i+1]);return r;}
    private static Map<String,Object>base(UUID combat,long sequence,Object...values){Map<String,Object>r=row("combat_id",bytes(combat),"event_sequence",sequence);r.putAll(row(values));return r;}
    private static Map<String,Object>phase(UUID combat,long sequence,String phase,Object...values){Map<String,Object>r=base(combat,sequence,"phase",a(phase));r.putAll(row(values));return r;}
    private static Map<String,Object>flow(UUID combat,long sequence,String phase,int flow,Object...values){Map<String,Object>r=phase(combat,sequence,phase,"flow_index",flow);r.putAll(row(values));return r;}
    private static void add(Map<String,List<Map<String,Object>>>out,String table,Map<String,Object>row){out.computeIfAbsent(table,k->new ArrayList<>()).add(row);}
    private static byte[]a(Object value){return value==null?null:value.toString().getBytes(StandardCharsets.US_ASCII);}
    private static byte[]b(UUID id){return id==null?null:bytes(id);}
    private static int bit(boolean b){return b?1:0;}
    private static byte[]bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    private static UUID id(ResultSet row,String key)throws SQLException{return id(row.getObject(key));}
    private static UUID id(Map<String,Object>row,String key)throws SQLException{return id(row.get(key));}
    private static UUID id(Object value)throws SQLException{require(value instanceof byte[]&&((byte[])value).length==16);ByteBuffer b=ByteBuffer.wrap((byte[])value);UUID id=new UUID(b.getLong(),b.getLong());uuid(id);return id;}
    private static UUID nullableId(Map<String,Object>row,String key)throws SQLException{return row.get(key)==null?null:id(row,key);}
    private static String ascii(ResultSet row,String key)throws SQLException{return ascii(row.getObject(key));}
    private static String ascii(Map<String,Object>row,String key)throws SQLException{return ascii(row.get(key));}
    private static String ascii(Object value)throws SQLException{require(value instanceof byte[]);byte[]data=(byte[])value;require(data.length>=1&&data.length<=128);for(byte b:data)require(b>=0x21&&b<=0x7e);return new String(data,StandardCharsets.US_ASCII);}
    private static String nullableAscii(Map<String,Object>row,String key)throws SQLException{return row.get(key)==null?null:ascii(row,key);}
    private static long number(Map<String,Object>row,String key)throws SQLException{long v=numeric(row.get(key));require(v>=0);return v;}
    private static long number(ResultSet row,String key)throws SQLException{long v=numeric(row.getObject(key));require(v>=0);return v;}
    private static long numeric(Object value)throws SQLException{require(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long);return((Number)value).longValue();}
    private static int integer(Map<String,Object>row,String key)throws SQLException{long v=number(row,key);require(v<=Integer.MAX_VALUE);return(int)v;}
    private static int integer(ResultSet row,String key)throws SQLException{long v=number(row,key);require(v<=Integer.MAX_VALUE);return(int)v;}
    private static int signed(Map<String,Object>row,String key)throws SQLException{long v=numeric(row.get(key));require(v>=Integer.MIN_VALUE&&v<=Integer.MAX_VALUE);return(int)v;}
    private static Integer nullableInteger(Map<String,Object>row,String key)throws SQLException{return row.get(key)==null?null:integer(row,key);}
    private static boolean bool(Map<String,Object>row,String key)throws SQLException{int v=integer(row,key);require(v<=1);return v==1;}
    private static boolean bool(ResultSet row,String key)throws SQLException{int v=integer(row,key);require(v<=1);return v==1;}
    private static<E extends Enum<E>>E en(Class<E>type,Map<String,Object>row,String key)throws SQLException{try{return Enum.valueOf(type,ascii(row,key));}catch(IllegalArgumentException e){throw corrupt();}}
    private static boolean token(String value){return value!=null&&value.matches("[A-Z][A-Z0-9_]{0,127}");}
    private static void uuid(UUID id)throws SQLException{require(id!=null&&id.version()==4&&id.variant()==2);}
    private static void require(boolean condition)throws SQLException{if(!condition)throw corrupt();}
    private static SQLException corrupt(){return new SQLException("S2_COMBAT_RUNTIME_CORRUPT");}
    @FunctionalInterface private interface Reader<T>{T read(ResultSet row)throws SQLException;}
    @FunctionalInterface private interface MapReader<T>{T read(Map<String,Object>row)throws SQLException;}
}
