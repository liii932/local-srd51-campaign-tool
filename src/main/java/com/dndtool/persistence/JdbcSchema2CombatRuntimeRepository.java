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
    public static final int MAX_EVENTS = 128, MAX_COMBATANTS = 64, MAX_CHARACTER_MEMBERS = 16, MAX_HISTORY_CHILD_ROWS = 65536;
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
    private static final List<String> TABLES = java.util.stream.Stream.concat(List.of("s2_combat_member", "s2_combat_event", "s2_combat_dependency", "s2_combat_character_result", "s2_combat_command_point", "s2_combat_command_deployment", "s2_combat_command_tie", "s2_combat_roll", "s2_combat_fact", "s2_combat_state", "s2_combat_actor", "s2_combat_initiative", "s2_combat_flow", "s2_combat_frame", "s2_combat_window", "s2_combat_opportunity", "s2_combat_opportunity_item", "s2_combat_movement", "s2_combat_movement_point", "s2_combat_attack", "s2_combat_attack_die", "s2_combat_cast_command", "s2_combat_cast_command_target", "s2_combat_ongoing_effect", "s2_combat_ongoing_target", "s2_combat_cast", "s2_combat_cast_target", "s2_combat_cast_die", "s2_combat_saving_throw", "s2_combat_ready_input", "s2_combat_ready_point", "s2_combat_ready_target", "s2_combat_zone_command", "s2_combat_health_command", "s2_combat_reaction_option", "s2_combat_window_trigger", "s2_combat_ready_release", "s2_combat_ability_check", "s2_combat_ready", "s2_combat_trigger", "s2_combat_shield", "s2_combat_concentration", "s2_combat_environment", "s2_combat_suppression").stream(), JdbcSchema2MonsterBreathRows.TABLES.stream()).toList();
    private final JdbcSchema2CharacterRuntimeRepository characters = new JdbcSchema2CharacterRuntimeRepository();

    public History read(Connection connection, UUID combatId) throws SQLException {
        try { return readTyped(connection,combatId); }
        catch(IllegalArgumentException malformed) { throw new SQLException("S2_COMBAT_RUNTIME_CORRUPT",malformed); }
    }
    private History readTyped(Connection connection, UUID combatId) throws SQLException {
        uuid(combatId);
        var roots = query(connection,"s2_combat_runtime","combat_id",bytes(combatId),"combat_id",1,row -> {
            require(id(row,"combat_id").equals(combatId)); return id(row,"campaign_id");
        });
        if (roots.isEmpty()) {
            for (String table : TABLES) require(query(connection,table,"combat_id",bytes(combatId),"combat_id",1,row -> true).isEmpty());
            return null;
        }
        int[] memberIndex = {0};
        List<Member> members = query(connection,"s2_combat_member","combat_id",bytes(combatId),"member_index",MAX_CHARACTER_MEMBERS,row -> {
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
        require(result.stream().filter(h -> h.current().status() != Status.ENDED).count() <= 1);
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
        var event = characters.findOperation(connection,operation); require(event != null && event.before() != null);
        var facts = query(connection,"s2_character_combat_input","operation_id",bytes(operation),"event_sequence",1,row -> {
            require(id(row,"operation_id").equals(operation) && id(row,"combat_id").equals(combat) && id(row,"combat_operation_id").equals(combatOperation)
                    && id(row,"character_id").equals(event.after().characterId()) && number(row,"event_sequence") == event.after().version()+1);
            boolean death = event.kind().equals("COMBAT_DEATH_SAVE_RESOLVED"), time = event.kind().equals("COMBAT_TIME_ADVANCED");
            boolean resource = event.kind().equals("COMBAT_RESOURCE_SPENT"), healing = event.kind().equals("COMBAT_HEALING_APPLIED");
            boolean environment=event.kind().equals("COMBAT_ENVIRONMENT_HEALTH");
            require(death || time || resource || healing || environment || event.kind().equals("COMBAT_DAMAGE_APPLIED"));
            require(resource == (event.healthResult() == null));
            return new HealthChange(event.after().characterId(),event.before(),event.after(),id(row,"frame_key"),integer(row,"damage_amount"),bool(row,"critical"),event.healthResult(),death,time,integer(row,"seconds_advanced"),resource ? event.command().resource() : null,resource ? event.command().amount() : 0,healing,environment?event.command().healthInput():null);
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
        List<Map<String,Object>> casts = indexed(data,"s2_combat_cast_command",combat,sequence,"command_index",1,r -> r);
        List<UUID> targets = indexed(data,"s2_combat_cast_command_target",combat,sequence,"target_index",MAX_COMBATANTS,r -> id(r,"target_id"));
        CastInput cast = null;
        if (!casts.isEmpty()) { var r=casts.getFirst(); require(targets.size()==integer(r,"target_count")); cast=new CastInput(rule(r,"casting"),rule(r,"spell"),targets,nullableId(r,"material_item_id"),integer(r,"slot_level"),nullableId(r,"target_cast_frame_key")); }
        else require(targets.isEmpty());
        var readyRows=scoped(data,"s2_combat_ready_input",combat,sequence,"COMMAND","ready_index",1);
        ReadyInput ready=readyRows.isEmpty()?null:readReadyInput(data,combat,sequence,"COMMAND",readyRows.getFirst());
        var zoneRows=indexed(data,"s2_combat_zone_command",combat,sequence,"command_index",1,r->r);
        ZoneInput zone=zoneRows.isEmpty()?null:new ZoneInput(rule(zoneRows.getFirst(),"rule"),new Position(signed(zoneRows.getFirst(),"x"),signed(zoneRows.getFirst(),"y")));
        var healthRows=indexed(data,"s2_combat_health_command",combat,sequence,"command_index",1,r->r);
        JdbcSchema2CharacterRuntimeRepository.HealthInput health=null;
        if(!healthRows.isEmpty()) {var r=healthRows.getFirst();health=new JdbcSchema2CharacterRuntimeRepository.HealthInput(rule(r,"rule"),nullableRule(r,"effect"),nullableAscii(r,"temporary_choice"),utf8(r,"adjudication"));}
        var monster=JdbcSchema2MonsterBreathRows.readCommand((table,order,maximum)->indexed(data,table,combat,sequence,
                table.equals("s2_combat_monster_deployment")?"deployment_index":"input_index",maximum,r->r));
        return new Command(en(Kind.class,row,"command_kind"),nullableId(row,"actor_id"),nullableId(row,"target_id"),nullableId(row,"item_id"),path,deployments,ties,
                nullableId(row,"flow_key"),nullableId(row,"leaf_key"),nullableId(row,"window_key"),nullableId(row,"opportunity_key"),cast,ready,nullableId(row,"ready_key"),zone,nullableId(row,"zone_key"),health,integer(row,"seconds"),monster.deployments(),monster.breath(),monster.rest());
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
                    int opportunity=integer(o,"opportunity_index"); List<ReactionOption> options=new ArrayList<>();
                    for(var option:flowRows(data,"s2_combat_reaction_option",combat,sequence,phase,flow,"window_index, opportunity_index, option_index",MAX_HISTORY_CHILD_ROWS)) if(integer(option,"window_index")==window&&integer(option,"opportunity_index")==opportunity) {
                        require(integer(option,"option_index")==options.size());options.add(new ReactionOption(en(ReactionKind.class,option,"reaction_kind"),nullableId(option,"item_id"),nullableRule(option,"casting"),nullableRule(option,"spell"),integer(option,"slot_level"),nullableId(option,"ready_key")));
                    }
                    require(options.size()==integer(o,"option_count"));
                    opportunities.add(new Opportunity(id(o,"opportunity_key"),id(o,"actor_id"),integer(o,"priority"),options,en(OpportunityStatus.class,o,"opportunity_status"),nullableId(o,"selection_frame_key"),nullableId(o,"child_frame_key"),nullableAscii(o,"reason")));
                }
                var triggers=flowRows(data,"s2_combat_window_trigger",combat,sequence,phase,flow,"window_index",1024).stream().filter(t->{try{return integer(t,"window_index")==window;}catch(SQLException invalid){throw new IllegalArgumentException(invalid);}}).toList();
                require(triggers.size()<=1);
                windows.add(new Window(id(r,"window_key"),id(r,"owner_frame_key"),integer(r,"checkpoint_ordinal"),integer(r,"boundary_index"),en(WindowStatus.class,r,"window_status"),opportunities,triggers.isEmpty()?null:readTrigger(triggers.getFirst())));
            }
            List<Movement> movements=new ArrayList<>();
            for(var r:flowRows(data,"s2_combat_movement",combat,sequence,phase,flow,"movement_index",1024)) {
                int movement=integer(r,"movement_index");require(movement==movements.size());List<Position> points=new ArrayList<>();
                for(var point:flowRows(data,"s2_combat_movement_point",combat,sequence,phase,flow,"movement_index, point_index",MAX_HISTORY_CHILD_ROWS)) if(integer(point,"movement_index")==movement) {
                    require(integer(point,"point_index")==points.size());points.add(new Position(signed(point,"x"),signed(point,"y")));
                }
                require(points.size()==integer(r,"point_count"));
                movements.add(new Movement(id(r,"movement_key"),id(r,"actor_id"),new Position(signed(r,"origin_x"),signed(r,"origin_y")),points,integer(r,"cursor"),integer(r,"spent"),en(MovementStatus.class,r,"movement_status"),nullableAscii(r,"stop_reason"),bool(r,"readied"),integer(r,"allowance_feet")));
            }
            List<AttackAttempt> attacks=new ArrayList<>();
            for(var r:flowRows(data,"s2_combat_attack",combat,sequence,phase,flow,"attack_index",1024)) {
                int attack=integer(r,"attack_index");List<Integer> dice=new ArrayList<>();
                for(var d:flowRows(data,"s2_combat_attack_die",combat,sequence,phase,flow,"attack_index, die_index",MAX_HISTORY_CHILD_ROWS)) if(integer(d,"attack_index")==attack) dice.add(integer(d,"die_value"));
                attacks.add(new AttackAttempt(id(r,"frame_key"),id(r,"actor_id"),id(r,"target_id"),id(r,"item_id"),nullableId(r,"sequence_key"),integer(r,"attack_ordinal"),bool(r,"opportunity_attack"),integer(r,"first_d20"),nullableInteger(r,"second_d20"),integer(r,"selected_d20"),signed(r,"ability_modifier"),integer(r,"proficiency_bonus"),integer(r,"armor_class"),bool(r,"advantage"),bool(r,"disadvantage"),bool(r,"critical"),bool(r,"hit"),dice,integer(r,"damage_amount"),nullableId(r,"blessing_effect_key"),nullableInteger(r,"blessing_d4"),en(AttackStatus.class,r,"attack_status"),bool(r,"initial_hit"),integer(r,"final_armor_class")));
            }
            List<CastAttempt> casts = new ArrayList<>();
            for(var r:flowRows(data,"s2_combat_cast",combat,sequence,phase,flow,"cast_index",1024)) {
                int cast=integer(r,"cast_index"); List<UUID> targets=new ArrayList<>(); List<Integer> dice=new ArrayList<>();
                for(var t:flowRows(data,"s2_combat_cast_target",combat,sequence,phase,flow,"cast_index, target_index",MAX_HISTORY_CHILD_ROWS)) if(integer(t,"cast_index")==cast) targets.add(id(t,"target_id"));
                for(var d:flowRows(data,"s2_combat_cast_die",combat,sequence,phase,flow,"cast_index, die_index",MAX_HISTORY_CHILD_ROWS)) if(integer(d,"cast_index")==cast) dice.add(integer(d,"die_value"));
                require(targets.size()==integer(r,"target_count") && dice.size()==integer(r,"healing_die_count"));
                casts.add(new CastAttempt(id(r,"frame_key"),id(r,"actor_id"),rule(r,"casting"),rule(r,"spell"),targets,rule(r,"slot"),integer(r,"slot_level"),nullableId(r,"material_item_id"),dice,signed(r,"healing_modifier"),integer(r,"requested_healing"),integer(r,"applied_healing"),nullableId(r,"effect_key"),en(CastStatus.class,r,"cast_status"),nullableId(r,"target_cast_frame_key"),nullableId(r,"ready_key")));
            }
            List<SavingThrowAttempt> saves = new ArrayList<>();
            for(var r:flowRows(data,"s2_combat_saving_throw",combat,sequence,phase,flow,"save_index",1024)) saves.add(new SavingThrowAttempt(id(r,"frame_key"),id(r,"subject_id"),ascii(r,"save_kind"),nullableAscii(r,"ability_key"),integer(r,"d20"),signed(r,"ability_modifier"),integer(r,"proficiency_bonus"),nullableId(r,"blessing_effect_key"),nullableInteger(r,"blessing_d4"),integer(r,"difficulty_class"),signed(r,"total"),bool(r,"success")));
            List<ReadyRelease> releases=new ArrayList<>();
            for(var r:flowRows(data,"s2_combat_ready_release",combat,sequence,phase,flow,"release_index",1024)) {require(integer(r,"release_index")==releases.size());releases.add(new ReadyRelease(id(r,"frame_key"),id(r,"ready_key"),id(r,"trigger_key"),bool(r,"completed"),nullableAscii(r,"outcome")));}
            List<AbilityCheckAttempt> checks=new ArrayList<>();
            for(var r:flowRows(data,"s2_combat_ability_check",combat,sequence,phase,flow,"check_index",1024)) {require(integer(r,"check_index")==checks.size());checks.add(new AbilityCheckAttempt(id(r,"frame_key"),id(r,"subject_id"),ascii(r,"check_kind"),ascii(r,"ability_key"),integer(r,"d20"),signed(r,"ability_modifier"),integer(r,"proficiency_bonus"),integer(r,"difficulty_class"),signed(r,"total"),bool(r,"success")));}
            require(casts.size()==integer(row,"cast_count")&&saves.size()==integer(row,"save_count")&&movements.size()==integer(row,"movement_count")&&releases.size()==integer(row,"release_count")&&checks.size()==integer(row,"check_count"));
            flows.add(new Flow(id(row,"flow_key"),id(row,"root_frame_key"),nullableId(row,"leaf_frame_key"),en(FlowStatus.class,row,"flow_status"),frames,windows,movements,attacks,casts,saves,releases,checks));
        }
        List<OngoingEffect> effects=new ArrayList<>();
        for(var r:scoped(data,"s2_combat_ongoing_effect",combat,sequence,phase,"effect_index",128)) {
            int effect=integer(r,"effect_index"); List<UUID> targets=new ArrayList<>();
            for(var t:scoped(data,"s2_combat_ongoing_target",combat,sequence,phase,"effect_index, target_index",MAX_HISTORY_CHILD_ROWS)) if(integer(t,"effect_index")==effect) targets.add(id(t,"target_id"));
            require(targets.size()==integer(r,"target_count"));
            effects.add(new OngoingEffect(id(r,"effect_key"),rule(r,"spell"),id(r,"caster_id"),targets,number(r,"started_at_second"),number(r,"expires_at_second"),en(EffectStatus.class,r,"effect_status"),nullableAscii(r,"end_reason")));
        }
        require(effects.size()==integer(root,"ongoing_effect_count"));
        List<ReadyPreparation> readies=new ArrayList<>();
        for(var r:scoped(data,"s2_combat_ready",combat,sequence,phase,"ready_index",128)) {
            int i=integer(r,"ready_index");require(i==readies.size());
            var inputs=scoped(data,"s2_combat_ready_input",combat,sequence,phase,"ready_index",128).stream().filter(t->{try{return integer(t,"ready_index")==i;}catch(SQLException invalid){throw new IllegalArgumentException(invalid);}}).toList();require(inputs.size()==1);
            readies.add(new ReadyPreparation(id(r,"ready_key"),id(r,"actor_id"),number(r,"declared_turn_number"),number(r,"expires_at_turn_number"),readReadyInput(data,combat,sequence,phase,inputs.getFirst()),en(ReadyStatus.class,r,"ready_status"),nullableId(r,"held_cast_frame_key"),nullableAscii(r,"end_reason")));
        }
        List<TriggerFact> triggers=new ArrayList<>();for(var r:scoped(data,"s2_combat_trigger",combat,sequence,phase,"trigger_index",1024)) {require(integer(r,"trigger_index")==triggers.size());triggers.add(readTrigger(r));}
        List<ShieldEffect> shields=new ArrayList<>();for(var r:scoped(data,"s2_combat_shield",combat,sequence,phase,"shield_index",128)) {require(integer(r,"shield_index")==shields.size());shields.add(new ShieldEffect(id(r,"effect_key"),id(r,"cast_frame_key"),id(r,"actor_id"),number(r,"started_turn_number"),number(r,"expires_at_turn_number"),en(EffectStatus.class,r,"effect_status"),nullableAscii(r,"end_reason")));}
        List<ConcentrationHolder> holders=new ArrayList<>();for(var r:scoped(data,"s2_combat_concentration",combat,sequence,phase,"holder_index",MAX_COMBATANTS)) {require(integer(r,"holder_index")==holders.size());holders.add(new ConcentrationHolder(id(r,"actor_id"),en(ConcentrationKind.class,r,"holder_kind"),id(r,"source_key")));}
        List<EnvironmentZone> zones=new ArrayList<>();for(var r:scoped(data,"s2_combat_environment",combat,sequence,phase,"zone_index",16)) {require(integer(r,"zone_index")==zones.size());zones.add(new EnvironmentZone(id(r,"zone_key"),rule(r,"rule"),new Position(signed(r,"x"),signed(r,"y")),id(r,"created_operation_id"),nullableId(r,"ended_operation_id")));}
        List<SuppressionCause> causes=new ArrayList<>();for(var r:scoped(data,"s2_combat_suppression",combat,sequence,phase,"cause_index",512)) {require(integer(r,"cause_index")==causes.size());causes.add(new SuppressionCause(id(r,"cause_key"),id(r,"zone_key"),id(r,"effect_key"),id(r,"target_id"),id(r,"established_operation_id"),nullableId(r,"released_operation_id"),r.get("release_reason")==null?null:en(SuppressionReleaseReason.class,r,"release_reason")));}
        require(readies.size()==integer(root,"ready_count")&&triggers.size()==integer(root,"trigger_count")&&shields.size()==integer(root,"shield_count")&&holders.size()==integer(root,"holder_count")&&zones.size()==integer(root,"zone_count")&&causes.size()==integer(root,"cause_count"));
        var monster=JdbcSchema2MonsterBreathRows.readState((table,order,maximum)->scoped(data,table,combat,sequence,phase,order,maximum),
                integer(root,"monster_count"),integer(root,"batch_count"),integer(root,"monster_rest_count"));
        return new Battle(combat,campaign,new RuleIdentity(ascii(root,"rule_module"),ascii(root,"rule_type"),ascii(root,"rule_key")),en(Status.class,root,"combat_status"),integer(root,"round_number"),number(root,"turn_number"),integer(root,"active_ordinal"),number(root,"row_version"),actors,initiative,flows,effects,readies,triggers,shields,holders,zones,causes,monster.monsters(),monster.batches(),monster.rests());
    }

    private static ReadyInput readReadyInput(Rows data,UUID combat,long sequence,String phase,Map<String,Object> r)throws SQLException {
        int index=integer(r,"ready_index");List<Position> path=new ArrayList<>();List<UUID> targets=new ArrayList<>();
        for(var p:scoped(data,"s2_combat_ready_point",combat,sequence,phase,"ready_index, point_index",8192)) if(integer(p,"ready_index")==index) {require(integer(p,"point_index")==path.size());path.add(new Position(signed(p,"x"),signed(p,"y")));}
        for(var t:scoped(data,"s2_combat_ready_target",combat,sequence,phase,"ready_index, target_index",2048)) if(integer(t,"ready_index")==index) {require(integer(t,"target_index")==targets.size());targets.add(id(t,"target_id"));}
        require(path.size()==integer(r,"point_count")&&targets.size()==integer(r,"target_count"));
        CastInput spell=null;RuleIdentity casting=nullableRule(r,"casting"),identity=nullableRule(r,"spell");
        if(casting!=null) {require(identity!=null);spell=new CastInput(casting,identity,targets,nullableId(r,"material_item_id"),integer(r,"slot_level"),nullableId(r,"target_cast_frame_key"));}
        else require(identity==null&&targets.isEmpty()&&r.get("material_item_id")==null&&integer(r,"slot_level")==0&&r.get("target_cast_frame_key")==null);
        return new ReadyInput(new ReadyTrigger(en(TriggerKind.class,r,"trigger_kind"),nullableId(r,"trigger_subject_id"),nullablePosition(r,"trigger_x","trigger_y")),en(ReadyResponseKind.class,r,"response_kind"),nullableId(r,"target_id"),nullableId(r,"item_id"),path,spell);
    }
    private static void writeReadyInput(Map<String,List<Map<String,Object>>> out,UUID combat,long sequence,String phase,int index,ReadyInput input) {
        CastInput spell=input.spell();var trigger=input.trigger();var r=phase(combat,sequence,phase,"ready_index",index,"trigger_kind",a(trigger.kind()),"trigger_subject_id",b(trigger.subjectId()),"trigger_x",trigger.position()==null?null:trigger.position().x(),"trigger_y",trigger.position()==null?null:trigger.position().y(),"response_kind",a(input.responseKind()),"target_id",b(input.targetId()),"item_id",b(input.itemId()),"point_count",input.path().size(),"target_count",spell==null?0:spell.targets().size(),"material_item_id",spell==null?null:b(spell.materialItemId()),"slot_level",spell==null?0:spell.slotLevel(),"target_cast_frame_key",spell==null?null:b(spell.targetCastFrameKey()));
        nullableRuleColumns(r,"casting",spell==null?null:spell.castingRule());nullableRuleColumns(r,"spell",spell==null?null:spell.spell());add(out,"s2_combat_ready_input",r);
        for(int i=0;i<input.path().size();i++){var p=input.path().get(i);add(out,"s2_combat_ready_point",phase(combat,sequence,phase,"ready_index",index,"point_index",i,"x",p.x(),"y",p.y()));}
        if(spell!=null)for(int i=0;i<spell.targets().size();i++)add(out,"s2_combat_ready_target",phase(combat,sequence,phase,"ready_index",index,"target_index",i,"target_id",bytes(spell.targets().get(i))));
    }
    private static TriggerFact readTrigger(Map<String,Object> r)throws SQLException {return new TriggerFact(id(r,"trigger_key"),en(TriggerKind.class,r,"trigger_kind"),id(r,"source_frame_key"),id(r,"subject_id"),nullableId(r,"target_id"),nullablePosition(r,"x","y"),integer(r,"ordinal"));}
    private static void triggerColumns(Map<String,Object> r,TriggerFact t) {r.putAll(row("trigger_key",bytes(t.triggerKey()),"trigger_kind",a(t.kind()),"source_frame_key",bytes(t.sourceFrameKey()),"subject_id",bytes(t.subjectId()),"target_id",b(t.targetId()),"x",t.position()==null?null:t.position().x(),"y",t.position()==null?null:t.position().y(),"ordinal",t.ordinal()));}
    private static Position nullablePosition(Map<String,Object> r,String x,String y)throws SQLException {require((r.get(x)==null)==(r.get(y)==null));return r.get(x)==null?null:new Position(signed(r,x),signed(r,y));}
    private static RuleIdentity nullableRule(Map<String,Object> r,String prefix)throws SQLException {if(r.get(prefix+"_module")==null){require(r.get(prefix+"_type")==null&&r.get(prefix+"_key")==null);return null;}return rule(r,prefix);}
    private static void nullableRuleColumns(Map<String,Object> r,String prefix,RuleIdentity rule) {if(rule!=null)ruleColumns(r,prefix,rule);else r.putAll(row(prefix+"_module",null,prefix+"_type",null,prefix+"_key",null));}
    private static String utf8(Map<String,Object> r,String key)throws SQLException {Object value=r.get(key);if(value==null)return null;require(value instanceof byte[]&&((byte[])value).length<=16384);try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap((byte[])value)).toString();}catch(java.nio.charset.CharacterCodingException invalid){throw corrupt();}}

    // Row writing and shape validation are deliberately explicit: no opaque state blob exists.
    private static void writeRoot(Connection connection, History history) throws SQLException {
        put(connection,"s2_combat_runtime",row("combat_id",bytes(history.current().combatId()),"campaign_id",bytes(history.current().campaignId())));
        int index=0;for(Member member:history.members())put(connection,"s2_combat_member",row("combat_id",bytes(history.current().combatId()),"member_index",index++,"character_id",bytes(member.characterId()),"run_id",member.identity().runBytes(),"snapshot_id",member.identity().snapshotBytes(),"preparation_operation_id",bytes(member.preparationOperationId())));
    }
    private static void writeEvent(Connection connection, Event event) throws SQLException { for(var entry:eventRows(event).entrySet())for(var row:entry.getValue())put(connection,entry.getKey(),row); }
    private static Map<String,List<Map<String,Object>>> eventRows(Event event) throws SQLException {
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        UUID combat=event.after().combatId();long sequence=event.after().version()+1;Command c=event.command();
        add(result,"s2_combat_event",base(combat,sequence,"operation_id",bytes(event.operationId()),"request_digest",a(event.requestDigest()),"command_kind",a(c.kind()),"actor_id",b(c.actorId()),"target_id",b(c.targetId()),"item_id",b(c.itemId()),"flow_key",b(c.flowKey()),"leaf_key",b(c.leafKey()),"window_key",b(c.windowKey()),"opportunity_key",b(c.opportunityKey()),"ready_key",b(c.readyKey()),"zone_key",b(c.zoneKey()),"seconds",c.seconds()));
        if(c.castInput()!=null) {
            CastInput cast=c.castInput(); Map<String,Object> r=base(combat,sequence,"command_index",0,"material_item_id",b(cast.materialItemId()),"target_count",cast.targets().size(),"slot_level",cast.slotLevel(),"target_cast_frame_key",b(cast.targetCastFrameKey()));
            ruleColumns(r,"casting",cast.castingRule());ruleColumns(r,"spell",cast.spell());add(result,"s2_combat_cast_command",r);
            for(int i=0;i<cast.targets().size();i++)add(result,"s2_combat_cast_command_target",base(combat,sequence,"target_index",i,"target_id",bytes(cast.targets().get(i))));
        }
        if(c.readyInput()!=null)writeReadyInput(result,combat,sequence,"COMMAND",0,c.readyInput());
        if(c.zoneInput()!=null) {Map<String,Object> r=base(combat,sequence,"command_index",0,"x",c.zoneInput().center().x(),"y",c.zoneInput().center().y());ruleColumns(r,"rule",c.zoneInput().rule());add(result,"s2_combat_zone_command",r);}
        if(c.healthInput()!=null) {var h=c.healthInput();Map<String,Object> r=base(combat,sequence,"command_index",0,"temporary_choice",a(h.temporaryChoice()),"adjudication",h.adjudication()==null?null:h.adjudication().getBytes(StandardCharsets.UTF_8));ruleColumns(r,"rule",h.rule());nullableRuleColumns(r,"effect",h.effect());add(result,"s2_combat_health_command",r);}
        JdbcSchema2MonsterBreathRows.commandRows(result,combat,sequence,c);
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
        add(out,"s2_combat_state",phase(combat,sequence,phase,"state_index",0,"rule_module",a(state.combatRule().moduleKey()),"rule_type",a(state.combatRule().ruleType()),"rule_key",a(state.combatRule().ruleKey()),"combat_status",a(state.status()),"round_number",state.round(),"turn_number",state.turnNumber(),"active_ordinal",state.activeOrdinal(),"row_version",state.version(),"ongoing_effect_count",state.ongoingEffects().size(),"ready_count",state.readyPreparations().size(),"trigger_count",state.triggerFacts().size(),"shield_count",state.shields().size(),"holder_count",state.concentrationHolders().size(),"zone_count",state.environmentZones().size(),"cause_count",state.suppressionCauses().size(),"monster_count",state.monsters().size(),"batch_count",state.saveDamageBatches().size(),"monster_rest_count",state.monsterRests().size()));
        for(int i=0;i<state.combatants().size();i++){Combatant c=state.combatants().get(i);AttackSequence s=c.sequence();add(out,"s2_combat_actor",phase(combat,sequence,phase,"actor_index",i,"character_id",bytes(c.characterId()),"x",c.position().x(),"y",c.position().y(),"side",c.side(),"action_available",bit(c.actionAvailable()),"reaction_available",bit(c.reactionAvailable()),"movement_spent_half_feet",c.movementSpentHalfFeet(),"movement_bonus",c.movementBonus(),"disengaged",bit(c.disengaged()),"dodging",bit(c.dodging()),"prone",bit(c.prone()),"sequence_key",s==null?null:bytes(s.sequenceKey()),"attack_limit",s==null?null:s.limit(),"performed",s==null?null:s.performed(),"forfeited",s==null?null:s.forfeited(),"sequence_closed",s==null?null:bit(s.closed())));}
        for(int i=0;i<state.initiative().size();i++){Initiative n=state.initiative().get(i);add(out,"s2_combat_initiative",phase(combat,sequence,phase,"initiative_index",i,"character_id",bytes(n.characterId()),"d20",n.d20(),"dexterity_modifier",n.dexterityModifier(),"total",n.total(),"ordinal",n.ordinal()));}
        for(int i=0;i<state.ongoingEffects().size();i++) {
            OngoingEffect effect=state.ongoingEffects().get(i); Map<String,Object> r=phase(combat,sequence,phase,"effect_index",i,"effect_key",bytes(effect.effectKey()),"caster_id",bytes(effect.casterId()),"started_at_second",effect.startedAtSecond(),"expires_at_second",effect.expiresAtSecond(),"effect_status",a(effect.status()),"end_reason",a(effect.endReason()),"target_count",effect.targets().size());
            ruleColumns(r,"spell",effect.spell());add(out,"s2_combat_ongoing_effect",r);
            for(int ti=0;ti<effect.targets().size();ti++)add(out,"s2_combat_ongoing_target",phase(combat,sequence,phase,"effect_index",i,"target_index",ti,"target_id",bytes(effect.targets().get(ti))));
        }
        for(int i=0;i<state.readyPreparations().size();i++) {var r=state.readyPreparations().get(i);add(out,"s2_combat_ready",phase(combat,sequence,phase,"ready_index",i,"ready_key",bytes(r.readyKey()),"actor_id",bytes(r.actorId()),"declared_turn_number",r.declaredTurnNumber(),"expires_at_turn_number",r.expiresAtTurnNumber(),"ready_status",a(r.status()),"held_cast_frame_key",b(r.heldCastFrameKey()),"end_reason",a(r.endReason())));writeReadyInput(out,combat,sequence,phase,i,r.input());}
        for(int i=0;i<state.triggerFacts().size();i++) {var r=phase(combat,sequence,phase,"trigger_index",i);triggerColumns(r,state.triggerFacts().get(i));add(out,"s2_combat_trigger",r);}
        for(int i=0;i<state.shields().size();i++) {var r=state.shields().get(i);add(out,"s2_combat_shield",phase(combat,sequence,phase,"shield_index",i,"effect_key",bytes(r.effectKey()),"cast_frame_key",bytes(r.castFrameKey()),"actor_id",bytes(r.actorId()),"started_turn_number",r.startedTurnNumber(),"expires_at_turn_number",r.expiresAtTurnNumber(),"effect_status",a(r.status()),"end_reason",a(r.endReason())));}
        for(int i=0;i<state.concentrationHolders().size();i++) {var r=state.concentrationHolders().get(i);add(out,"s2_combat_concentration",phase(combat,sequence,phase,"holder_index",i,"actor_id",bytes(r.actorId()),"holder_kind",a(r.kind()),"source_key",bytes(r.sourceKey())));}
        for(int i=0;i<state.environmentZones().size();i++) {var z=state.environmentZones().get(i);var r=phase(combat,sequence,phase,"zone_index",i,"zone_key",bytes(z.zoneKey()),"x",z.center().x(),"y",z.center().y(),"created_operation_id",bytes(z.createdOperationId()),"ended_operation_id",b(z.endedOperationId()));ruleColumns(r,"rule",z.rule());add(out,"s2_combat_environment",r);}
        for(int i=0;i<state.suppressionCauses().size();i++) {var r=state.suppressionCauses().get(i);add(out,"s2_combat_suppression",phase(combat,sequence,phase,"cause_index",i,"cause_key",bytes(r.causeKey()),"zone_key",bytes(r.zoneKey()),"effect_key",bytes(r.effectKey()),"target_id",bytes(r.targetId()),"established_operation_id",bytes(r.establishedOperationId()),"released_operation_id",b(r.releasedOperationId()),"release_reason",a(r.releaseReason())));}
        for(int fi=0;fi<state.flows().size();fi++){Flow f=state.flows().get(fi);
            add(out,"s2_combat_flow",flow(combat,sequence,phase,fi,"flow_key",bytes(f.flowKey()),"root_frame_key",bytes(f.rootFrameKey()),"leaf_frame_key",b(f.leafFrameKey()),"flow_status",a(f.status()),"cast_count",f.casts().size(),"save_count",f.savingThrows().size(),"movement_count",f.movements().size(),"release_count",f.readyReleases().size(),"check_count",f.abilityChecks().size()));
            for(int i=0;i<f.frames().size();i++){Frame r=f.frames().get(i);add(out,"s2_combat_frame",flow(combat,sequence,phase,fi,"frame_index",i,"frame_key",bytes(r.frameKey()),"parent_frame_key",b(r.parentFrameKey()),"frame_kind",a(r.kind()),"owner_key",bytes(r.ownerKey()),"invocation",a(r.invocation()),"frame_status",a(r.status()),"stage",a(r.stage()),"window_key",b(r.windowKey()),"opportunity_key",b(r.opportunityKey())));}
            for(int wi=0;wi<f.windows().size();wi++){Window w=f.windows().get(wi);add(out,"s2_combat_window",flow(combat,sequence,phase,fi,"window_index",wi,"window_key",bytes(w.windowKey()),"owner_frame_key",bytes(w.ownerFrameKey()),"checkpoint_ordinal",w.checkpointOrdinal(),"boundary_index",w.boundaryIndex(),"window_status",a(w.status())));
                for(int oi=0;oi<w.opportunities().size();oi++){Opportunity o=w.opportunities().get(oi);add(out,"s2_combat_opportunity",flow(combat,sequence,phase,fi,"window_index",wi,"opportunity_index",oi,"opportunity_key",bytes(o.opportunityKey()),"actor_id",bytes(o.actorId()),"priority",o.priority(),"opportunity_status",a(o.status()),"selection_frame_key",b(o.selectionFrameKey()),"child_frame_key",b(o.childFrameKey()),"option_count",o.options().size(),"reason",a(o.reason())));
                    for(int oi2=0;oi2<o.options().size();oi2++) {var option=o.options().get(oi2);var r=flow(combat,sequence,phase,fi,"window_index",wi,"opportunity_index",oi,"option_index",oi2,"reaction_kind",a(option.kind()),"item_id",b(option.itemId()),"slot_level",option.slotLevel(),"ready_key",b(option.readyKey()));nullableRuleColumns(r,"casting",option.castingRule());nullableRuleColumns(r,"spell",option.spell());add(out,"s2_combat_reaction_option",r);}}
                if(w.trigger()!=null) {var r=flow(combat,sequence,phase,fi,"window_index",wi);triggerColumns(r,w.trigger());add(out,"s2_combat_window_trigger",r);}}
            for(int mi=0;mi<f.movements().size();mi++){Movement m=f.movements().get(mi);add(out,"s2_combat_movement",flow(combat,sequence,phase,fi,"movement_index",mi,"movement_key",bytes(m.movementKey()),"actor_id",bytes(m.actorId()),"origin_x",m.origin().x(),"origin_y",m.origin().y(),"cursor",m.cursor(),"spent",m.spent(),"movement_status",a(m.status()),"stop_reason",a(m.stopReason()),"readied",bit(m.readied()),"allowance_feet",m.allowanceFeet(),"point_count",m.path().size()));
                for(int i=0;i<m.path().size();i++){Position p=m.path().get(i);add(out,"s2_combat_movement_point",flow(combat,sequence,phase,fi,"movement_index",mi,"point_index",i,"x",p.x(),"y",p.y()));}}
            for(int ai=0;ai<f.attacks().size();ai++){AttackAttempt t=f.attacks().get(ai);add(out,"s2_combat_attack",flow(combat,sequence,phase,fi,"attack_index",ai,"frame_key",bytes(t.frameKey()),"actor_id",bytes(t.actorId()),"target_id",bytes(t.targetId()),"item_id",bytes(t.itemId()),"sequence_key",b(t.sequenceKey()),"attack_ordinal",t.attackOrdinal(),"opportunity_attack",bit(t.opportunityAttack()),"first_d20",t.firstD20(),"second_d20",t.secondD20(),"selected_d20",t.selectedD20(),"ability_modifier",t.abilityModifier(),"proficiency_bonus",t.proficiencyBonus(),"armor_class",t.armorClass(),"advantage",bit(t.advantage()),"disadvantage",bit(t.disadvantage()),"critical",bit(t.critical()),"hit",bit(t.hit()),"damage_amount",t.damageAmount(),"blessing_effect_key",b(t.blessingEffectKey()),"blessing_d4",t.blessingD4(),"attack_status",a(t.status()),"initial_hit",bit(t.initialHit()),"final_armor_class",t.finalArmorClass()));
                for(int i=0;i<t.damageDice().size();i++)add(out,"s2_combat_attack_die",flow(combat,sequence,phase,fi,"attack_index",ai,"die_index",i,"die_value",t.damageDice().get(i)));}
            for(int ci=0;ci<f.casts().size();ci++) {
                CastAttempt cast=f.casts().get(ci); Map<String,Object> r=flow(combat,sequence,phase,fi,"cast_index",ci,"frame_key",bytes(cast.frameKey()),"actor_id",bytes(cast.actorId()),"slot_level",cast.slotLevel(),"material_item_id",b(cast.materialItemId()),"healing_modifier",cast.healingModifier(),"requested_healing",cast.requestedHealing(),"applied_healing",cast.appliedHealing(),"effect_key",b(cast.effectKey()),"target_count",cast.targets().size(),"healing_die_count",cast.healingDice().size(),"cast_status",a(cast.status()),"target_cast_frame_key",b(cast.targetCastFrameKey()),"ready_key",b(cast.readyKey()));
                ruleColumns(r,"casting",cast.castingRule());ruleColumns(r,"spell",cast.spell());ruleColumns(r,"slot",cast.slotResource());add(out,"s2_combat_cast",r);
                for(int ti=0;ti<cast.targets().size();ti++)add(out,"s2_combat_cast_target",flow(combat,sequence,phase,fi,"cast_index",ci,"target_index",ti,"target_id",bytes(cast.targets().get(ti))));
                for(int di=0;di<cast.healingDice().size();di++)add(out,"s2_combat_cast_die",flow(combat,sequence,phase,fi,"cast_index",ci,"die_index",di,"die_value",cast.healingDice().get(di)));
            }
            for(int i=0;i<f.readyReleases().size();i++) {var r=f.readyReleases().get(i);add(out,"s2_combat_ready_release",flow(combat,sequence,phase,fi,"release_index",i,"frame_key",bytes(r.frameKey()),"ready_key",bytes(r.readyKey()),"trigger_key",bytes(r.triggerKey()),"completed",bit(r.completed()),"outcome",a(r.outcome())));}
            for(int i=0;i<f.abilityChecks().size();i++) {var r=f.abilityChecks().get(i);add(out,"s2_combat_ability_check",flow(combat,sequence,phase,fi,"check_index",i,"frame_key",bytes(r.frameKey()),"subject_id",bytes(r.subjectId()),"check_kind",a(r.kind()),"ability_key",a(r.abilityKey()),"d20",r.d20(),"ability_modifier",r.abilityModifier(),"proficiency_bonus",r.proficiencyBonus(),"difficulty_class",r.difficultyClass(),"total",r.total(),"success",bit(r.success())));}
            for(int si=0;si<f.savingThrows().size();si++) {
                SavingThrowAttempt save=f.savingThrows().get(si);add(out,"s2_combat_saving_throw",flow(combat,sequence,phase,fi,"save_index",si,"frame_key",bytes(save.frameKey()),"subject_id",bytes(save.subjectId()),"save_kind",a(save.kind()),"ability_key",a(save.abilityKey()),"d20",save.d20(),"ability_modifier",save.abilityModifier(),"proficiency_bonus",save.proficiencyBonus(),"blessing_effect_key",b(save.blessingEffectKey()),"blessing_d4",save.blessingD4(),"difficulty_class",save.difficultyClass(),"total",save.total(),"success",bit(save.success())));
            }
        }
        JdbcSchema2MonsterBreathRows.stateRows(out,combat,sequence,phase,state);
    }

    public static void validate(History history)throws SQLException{
        require(history!=null&&!history.events().isEmpty()&&history.events().size()<=MAX_EVENTS&&history.members().size()>=2&&history.members().size()<=MAX_CHARACTER_MEMBERS);
        List<UUID> members=history.members().stream().map(Member::characterId).toList();require(new HashSet<>(members).size()==members.size()&&members.equals(members.stream().sorted(UUID_ORDER).toList()));
        for(Member m:history.members()){uuid(m.characterId());uuid(m.identity().runId());uuid(m.identity().snapshotId());uuid(m.preparationOperationId());}
        Set<UUID> operations=new HashSet<>(),children=new HashSet<>();Battle previous=null;long rows=0;
        for(Event e:history.events()){
            uuid(e.operationId());require(operations.add(e.operationId())&&e.requestDigest()!=null&&e.requestDigest().matches("[0-9a-f]{64}")&&e.command()!=null&&e.resolution()!=null&&Objects.equals(previous,e.before()));
            validateCommand(e.command());
            require(e.after().version()==(previous==null?0:previous.version()+1));validateState(e.after(),members);
            require(previous==null?e.command().kind()==Kind.START:e.command().kind()!=Kind.START&&previous.combatId().equals(e.after().combatId())&&previous.campaignId().equals(e.after().campaignId())&&previous.combatRule().equals(e.after().combatRule()));
            require(e.dependencies().stream().map(Dependency::characterId).toList().equals(members));
            if(e.command().castInput()!=null)require(members.containsAll(e.command().castInput().targets()));
            for(Dependency d:e.dependencies())require(d.version()>=0&&d.version()<JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS);
            require(e.characterOperations().size()==e.resolution().healthChanges().size()&&e.characterOperations().size()<=MAX_COMBATANTS * 2);
            for(UUID child:e.characterOperations()){uuid(child);require(children.add(child)&&!operations.contains(child));}
            for(HealthChange change:e.resolution().healthChanges()) {
                uuid(change.frameKey()); require(members.contains(change.characterId()) && change.before()!=null && change.after()!=null
                        && change.characterId().equals(change.before().characterId()) && change.characterId().equals(change.after().characterId())
                        && change.after().version()==change.before().version()+1 && change.damageAmount()>=0 && change.damageAmount()<=65535);
                require(change.resource()!=null ? change.resource().ruleType().equals("character.resource") && change.resourceAmount()>=1
                        && change.healthResult()==null && change.damageAmount()==0 && !change.critical() && !change.deathSave() && !change.timeAdvance() && !change.healing()
                        : change.resourceAmount()==0 && change.healthResult()!=null);
                require(!change.healing() || !change.critical() && !change.deathSave() && !change.timeAdvance());
                require(!change.timeAdvance()||change.seconds()<=60||e.command().kind()==Kind.COMPLETE_MONSTER_REST);
            }
            validateCastResults(e);validateExtendedTransition(e);
            require(e.resolution().rolls().size()<=512&&e.resolution().facts().size()<=512&&e.command().path().size()<=64&&e.command().deployments().size()<=MAX_COMBATANTS&&e.command().tieBreakOrder().size()<=MAX_COMBATANTS);
            for(Roll r:e.resolution().rolls()){uuid(r.subjectId());require(token(r.purpose())&&r.sides()>=2&&r.sides()<=20&&r.value()>=1&&r.value()<=r.sides());}
            for(CombatFact f:e.resolution().facts())require(token(f.kind())&&(f.reason()==null||token(f.reason())));
            for(var rs:eventRows(e).values())rows+=rs.size();previous=e.after();
        }
        require(previous.equals(history.current())&&Collections.disjoint(operations,children)&&rows<=MAX_HISTORY_CHILD_ROWS);
    }
    private static void validateCastResults(Event event) throws SQLException {
        Map<UUID,CastAttempt> retained=new HashMap<>();
        if(event.before()!=null)for(Flow flow:event.before().flows())for(CastAttempt cast:flow.casts())require(retained.put(cast.frameKey(),cast)==null);
        List<CastAttempt> created=new ArrayList<>(),completedHealing=new ArrayList<>();Set<UUID> seen=new HashSet<>();
        for(Flow flow:event.after().flows())for(CastAttempt cast:flow.casts()) {
            require(seen.add(cast.frameKey()));CastAttempt old=retained.remove(cast.frameKey());
            if(old==null)created.add(cast);
            else {
                require(old.actorId().equals(cast.actorId())&&old.castingRule().equals(cast.castingRule())&&old.spell().equals(cast.spell())&&old.targets().equals(cast.targets())
                        &&old.slotResource().equals(cast.slotResource())&&old.slotLevel()==cast.slotLevel()&&Objects.equals(old.materialItemId(),cast.materialItemId())
                        &&old.healingModifier()==cast.healingModifier()&&Objects.equals(old.targetCastFrameKey(),cast.targetCastFrameKey())&&Objects.equals(old.readyKey(),cast.readyKey()));
                if(old.status()==CastStatus.RESOLVED||old.status()==CastStatus.COUNTERED)require(old.equals(cast));
                else require(cast.status()==old.status()||cast.status()==CastStatus.RESOLVED||cast.status()==CastStatus.COUNTERED||old.status()==CastStatus.PENDING&&cast.status()==CastStatus.HELD);
            }
            if(!cast.healingDice().isEmpty()&&(old==null||old.healingDice().isEmpty()))completedHealing.add(cast);
        }
        require(retained.isEmpty());
        List<HealthChange> costs=event.resolution().healthChanges().stream().filter(c->c.resource()!=null).toList();
        require(created.size()==costs.size());Set<UUID> costFrames=new HashSet<>();
        for(CastAttempt cast:created) {
            var matches=costs.stream().filter(c->c.frameKey().equals(cast.frameKey())).toList();require(matches.size()==1&&costFrames.add(cast.frameKey()));
            var cost=matches.getFirst();require(cost.characterId().equals(cast.actorId())&&cost.resource().equals(cast.slotResource())&&cost.resourceAmount()==1);
        }
        List<HealthChange> healing=event.resolution().healthChanges().stream().filter(HealthChange::healing).toList();
        if(event.command().kind()!=Kind.EXPLORATION_HEALTH)require(healing.size()==completedHealing.size());
        for(CastAttempt cast:completedHealing) {
            Set<UUID> frames=new HashSet<>(Set.of(cast.frameKey()));
            if(cast.readyKey()!=null) {
                var preparation=event.after().readyPreparations().stream().filter(r->r.readyKey().equals(cast.readyKey())&&Objects.equals(r.heldCastFrameKey(),cast.frameKey())).toList();require(preparation.size()==1);
                Set<UUID> earlier=new HashSet<>();if(event.before()!=null)for(Flow flow:event.before().flows())for(ReadyRelease release:flow.readyReleases())earlier.add(release.frameKey());
                for(Flow flow:event.after().flows())for(ReadyRelease release:flow.readyReleases())if(release.readyKey().equals(cast.readyKey())&&release.completed()&&!earlier.contains(release.frameKey()))frames.add(release.frameKey());
            }
            var matches=healing.stream().filter(c->frames.contains(c.frameKey())).toList();require(matches.size()==1&&cast.targets().size()==1);
            var result=matches.getFirst();require(result.characterId().equals(cast.targets().getFirst())&&result.damageAmount()==cast.requestedHealing()
                    &&result.healthResult().dice().equals(cast.healingDice())&&result.healthResult().rolledAmount()==cast.requestedHealing()&&result.healthResult().appliedAmount()==cast.appliedHealing());
        }
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
    private static void validateState(Battle b,List<UUID> characterMembers)throws SQLException{
        List<UUID> members=JdbcSchema2MonsterBreathRows.validateState(b,characterMembers);
        uuid(b.combatId());uuid(b.campaignId());require(b.combatRule()!=null&&b.combatRule().ruleType().equals("adventure.combat")&&b.status()!=null&&b.round()>=1&&b.turnNumber()>=1&&b.activeOrdinal()>=0&&b.activeOrdinal()<members.size()&&b.version()>=0);
        require(b.combatants().stream().map(Combatant::characterId).toList().equals(members)&&b.initiative().size()==members.size());
        Set<UUID> initiative=new HashSet<>();for(int i=0;i<b.initiative().size();i++){Initiative n=b.initiative().get(i);require(initiative.add(n.characterId())&&members.contains(n.characterId())&&n.ordinal()==i&&n.d20()>=1&&n.d20()<=20&&n.dexterityModifier()>=-5&&n.dexterityModifier()<=10&&n.total()==n.d20()+n.dexterityModifier());}
        for(Combatant c:b.combatants()){require(c.position()!=null&&c.side()>=0&&c.side()<=15&&c.movementSpentHalfFeet()>=0&&c.movementBonus()>=0);if(c.sequence()!=null){AttackSequence s=c.sequence();uuid(s.sequenceKey());require(s.limit()>=1&&s.limit()<=20&&s.performed()>=0&&s.forfeited()>=0&&s.performed()+s.forfeited()<=s.limit()&&s.closed()==(s.performed()+s.forfeited()==s.limit()));}}
        require(b.ongoingEffects().size()<=128);Set<UUID> effectKeys=new HashSet<>();
        for(OngoingEffect effect:b.ongoingEffects()) {
            uuid(effect.effectKey()); require(effectKeys.add(effect.effectKey()) && ruleType(effect.spell(),"adventure.spell") && members.contains(effect.casterId())
                    && !effect.targets().isEmpty() && effect.targets().size()<=MAX_COMBATANTS && new HashSet<>(effect.targets()).size()==effect.targets().size()
                    && members.containsAll(effect.targets()) && effect.startedAtSecond()>=0 && effect.expiresAtSecond()>effect.startedAtSecond() && effect.expiresAtSecond()<=60000000059L
                    && effect.status()!=null && (effect.status()==EffectStatus.ACTIVE ? effect.endReason()==null : token(effect.endReason())));
        }
        require(b.flows().size()<=128&&b.flows().stream().filter(f->f.status()==FlowStatus.ACTIVE).count()<=1);Set<UUID> keys=new HashSet<>();
        for(Flow f:b.flows()){
            uuid(f.flowKey());require(keys.add(f.flowKey())&&f.frames().size()>=1&&f.frames().size()<=1024&&f.windows().size()<=1024&&f.attacks().size()<=1024&&f.casts().size()<=1024&&f.savingThrows().size()<=1024);
            Map<UUID,Frame> frames=new HashMap<>();for(Frame r:f.frames()){uuid(r.frameKey());uuid(r.ownerKey());require(keys.add(r.frameKey())&&frames.put(r.frameKey(),r)==null&&r.kind()!=null&&r.status()!=null&&r.invocation()!=null&&r.stage()!=null);}
            Frame root=frames.get(f.rootFrameKey());require(root!=null&&root.parentFrameKey()==null);require(f.status()==FlowStatus.ACTIVE?frames.containsKey(f.leafFrameKey()):f.leafFrameKey()==null);
            for(Frame r:f.frames()){Set<UUID> chain=new HashSet<>();Frame cursor=r;while(cursor.parentFrameKey()!=null){require(chain.add(cursor.frameKey())&&chain.size()<=32);cursor=frames.get(cursor.parentFrameKey());require(cursor!=null);}require(cursor.frameKey().equals(f.rootFrameKey()));}
            Set<UUID> windows=new HashSet<>();for(Window w:f.windows()){uuid(w.windowKey());require(keys.add(w.windowKey())&&windows.add(w.windowKey())&&frames.containsKey(w.ownerFrameKey())&&w.checkpointOrdinal()>=0&&w.checkpointOrdinal()<64&&w.boundaryIndex()>=0&&w.boundaryIndex()<64&&w.opportunities().size()<=128);Set<UUID>actors=new HashSet<>();
                for(int i=0;i<w.opportunities().size();i++){Opportunity o=w.opportunities().get(i);uuid(o.opportunityKey());require(keys.add(o.opportunityKey())&&actors.add(o.actorId())&&members.contains(o.actorId())&&o.priority()==i&&o.options().size()>=1&&o.options().size()<=32&&new HashSet<>(o.options()).size()==o.options().size()&&(o.selectionFrameKey()==null?o.status()==OpportunityStatus.PENDING:frames.containsKey(o.selectionFrameKey()))&&(o.attackFrameKey()==null||frames.containsKey(o.attackFrameKey()))&&(o.reason()==null||token(o.reason())));for(ReactionOption option:o.options())validateOption(option);}}
            for(Movement m:f.movements()){uuid(m.movementKey());require(members.contains(m.actorId())&&m.path().size()>=1&&m.path().size()<=64&&m.cursor()>=0&&m.cursor()<=m.path().size()&&m.spent()>=0&&m.status()!=null&&(m.stopReason()==null||token(m.stopReason())));}
            for(AttackAttempt t:f.attacks())require(frames.containsKey(t.frameKey())&&members.contains(t.actorId())&&members.contains(t.targetId())&&!t.actorId().equals(t.targetId())&&t.firstD20()>=1&&t.firstD20()<=20&&(t.secondD20()==null||t.secondD20()>=1&&t.secondD20()<=20)&&t.selectedD20()>=1&&t.selectedD20()<=20&&t.damageDice().size()<=40&&t.damageDice().stream().allMatch(v->v!=null&&v>=1&&v<=12)&&t.damageAmount()>=0&&validBlessing(t.blessingEffectKey(),t.blessingD4()));
            Set<UUID> castFrames=new HashSet<>();
            for(CastAttempt cast:f.casts()) {
                require(frames.containsKey(cast.frameKey()) && frames.get(cast.frameKey()).kind()==FrameKind.CAST && castFrames.add(cast.frameKey()) && members.contains(cast.actorId())
                        && castingType(cast.castingRule()) && ruleType(cast.spell(),"adventure.spell") && ruleType(cast.slotResource(),"character.resource")
                        && cast.slotLevel()>=1 && cast.slotLevel()<=9 && !cast.targets().isEmpty() && cast.targets().size()<=MAX_COMBATANTS
                        && new HashSet<>(cast.targets()).size()==cast.targets().size() && members.containsAll(cast.targets())
                        && cast.healingDice().size()<=20 && cast.healingDice().stream().allMatch(d->d!=null&&d>=1&&d<=12)
                        && cast.status()!=null && cast.healingModifier()>=-5 && cast.healingModifier()<=10 && cast.requestedHealing()>=0 && cast.requestedHealing()<=65535
                        && cast.appliedHealing()>=0 && cast.appliedHealing()<=cast.requestedHealing()
                        && (cast.healingDice().isEmpty() ? cast.requestedHealing()==0 && cast.appliedHealing()==0
                            : cast.effectKey()==null && cast.requestedHealing()==Math.max(0,cast.healingDice().stream().mapToInt(Integer::intValue).sum()+cast.healingModifier())));
                if(cast.materialItemId()!=null)uuid(cast.materialItemId());if(cast.effectKey()!=null)uuid(cast.effectKey());
            }
            for(SavingThrowAttempt save:f.savingThrows()) {
                require(frames.containsKey(save.frameKey()) && members.contains(save.subjectId()) && token(save.kind())
                        && (save.abilityKey()==null || JdbcSchema2CharacterRuntimeRepository.ABILITIES.contains(save.abilityKey()))
                        && save.d20()>=1 && save.d20()<=20 && save.abilityModifier()>=-5 && save.abilityModifier()<=10
                        && save.proficiencyBonus()>=0 && save.proficiencyBonus()<=6 && validBlessing(save.blessingEffectKey(),save.blessingD4())
                        && save.difficultyClass()>=0 && save.difficultyClass()<=32767
                        && save.total()==save.d20()+save.abilityModifier()+save.proficiencyBonus()+(save.blessingD4()==null?0:save.blessingD4())
                        && save.success()==(save.total()>=save.difficultyClass()));
            }
        }
        validateExtendedState(b,members);
    }
    private static void validateExtendedTransition(Event event)throws SQLException {
        Battle before=event.before(),after=event.after();Map<UUID,EnvironmentZone> zones=new HashMap<>();Map<UUID,SuppressionCause> causes=new HashMap<>();
        if(before!=null) {for(var z:before.environmentZones())zones.put(z.zoneKey(),z);for(var c:before.suppressionCauses())causes.put(c.causeKey(),c);}
        for(var z:after.environmentZones()) {var old=zones.remove(z.zoneKey());
            if(old==null)require(z.createdOperationId().equals(event.operationId())&&z.endedOperationId()==null);
            else {require(old.rule().equals(z.rule())&&old.center().equals(z.center())&&old.createdOperationId().equals(z.createdOperationId()));require(old.endedOperationId()!=null?old.equals(z):z.endedOperationId()==null||z.endedOperationId().equals(event.operationId()));}}
        require(zones.isEmpty());
        for(var c:after.suppressionCauses()) {var old=causes.remove(c.causeKey());
            if(old==null)require(c.establishedOperationId().equals(event.operationId())&&(c.releasedOperationId()==null||c.releasedOperationId().equals(event.operationId())));
            else {require(old.zoneKey().equals(c.zoneKey())&&old.effectKey().equals(c.effectKey())&&old.targetId().equals(c.targetId())&&old.establishedOperationId().equals(c.establishedOperationId()));require(old.releasedOperationId()!=null?old.equals(c):c.releasedOperationId()==null||c.releasedOperationId().equals(event.operationId()));}}
        require(causes.isEmpty());
        if(before!=null) {
            require(after.triggerFacts().size()>=before.triggerFacts().size()&&after.triggerFacts().subList(0,before.triggerFacts().size()).equals(before.triggerFacts()));
            require(after.ongoingEffects().size()>=before.ongoingEffects().size());
            for(int i=0;i<before.ongoingEffects().size();i++) {var old=before.ongoingEffects().get(i);var next=after.ongoingEffects().get(i);
                require(old.effectKey().equals(next.effectKey())&&old.spell().equals(next.spell())&&old.casterId().equals(next.casterId())&&old.targets().equals(next.targets())&&old.startedAtSecond()==next.startedAtSecond()&&old.expiresAtSecond()==next.expiresAtSecond());
                if(old.status()==EffectStatus.ENDED)require(old.equals(next));}
        }
    }
    private static void validateExtendedState(Battle b,List<UUID> members)throws SQLException {
        require(b.readyPreparations().size()<=128&&b.triggerFacts().size()<=1024&&b.shields().size()<=128&&b.concentrationHolders().size()<=members.size()&&b.environmentZones().size()<=16&&b.suppressionCauses().size()<=512);
        Map<UUID,ReadyPreparation> readies=new HashMap<>();Map<UUID,TriggerFact> triggers=new HashMap<>();Map<UUID,EnvironmentZone> zones=new HashMap<>();
        Map<UUID,Frame> frames=new HashMap<>();for(var f:b.flows())for(var r:f.frames())frames.put(r.frameKey(),r);
        for(var r:b.readyPreparations()) {uuid(r.readyKey());require(readies.put(r.readyKey(),r)==null&&members.contains(r.actorId())&&r.declaredTurnNumber()>=1&&r.expiresAtTurnNumber()>r.declaredTurnNumber()&&r.status()!=null);validateReadyInput(r.input());if(r.heldCastFrameKey()!=null)require(frames.containsKey(r.heldCastFrameKey()));}
        for(var t:b.triggerFacts()) {uuid(t.triggerKey());require(triggers.put(t.triggerKey(),t)==null&&t.kind()!=null&&frames.containsKey(t.sourceFrameKey())&&members.contains(t.subjectId())&&(t.targetId()==null||members.contains(t.targetId()))&&t.ordinal()>=0&&t.ordinal()<1024);}
        Set<UUID> effects=new HashSet<>();for(var e:b.ongoingEffects())effects.add(e.effectKey());
        for(var sh:b.shields()) {uuid(sh.effectKey());require(effects.add(sh.effectKey())&&frames.containsKey(sh.castFrameKey())&&members.contains(sh.actorId())&&sh.startedTurnNumber()>=1&&sh.expiresAtTurnNumber()>sh.startedTurnNumber()&&sh.status()!=null&&(sh.status()==EffectStatus.ACTIVE?sh.endReason()==null:token(sh.endReason())));}
        Set<UUID> holders=new HashSet<>();
        for(var h:b.concentrationHolders()) {uuid(h.sourceKey());require(holders.add(h.actorId())&&members.contains(h.actorId())&&h.kind()!=null);
            if(h.kind()==ConcentrationKind.BLESS_EFFECT)require(b.ongoingEffects().stream().anyMatch(e->e.effectKey().equals(h.sourceKey())&&e.casterId().equals(h.actorId())&&e.status()==EffectStatus.ACTIVE));
            else {var r=readies.get(h.sourceKey());require(r!=null&&r.actorId().equals(h.actorId())&&r.input().responseKind()==ReadyResponseKind.SPELL&&(r.status()==ReadyStatus.ARMED||r.status()==ReadyStatus.PREPARING));}}
        for(var e:b.ongoingEffects())if(e.status()==EffectStatus.ACTIVE)require(b.concentrationHolders().stream().anyMatch(h->h.kind()==ConcentrationKind.BLESS_EFFECT&&h.actorId().equals(e.casterId())&&h.sourceKey().equals(e.effectKey())));
        for(var z:b.environmentZones()) {uuid(z.zoneKey());uuid(z.createdOperationId());if(z.endedOperationId()!=null)uuid(z.endedOperationId());require(zones.put(z.zoneKey(),z)==null&&ruleType(z.rule(),"adventure.environment")&&z.center()!=null);}
        Set<UUID> causes=new HashSet<>();Set<String> live=new HashSet<>();
        for(var c:b.suppressionCauses()) {uuid(c.causeKey());uuid(c.establishedOperationId());if(c.releasedOperationId()!=null)uuid(c.releasedOperationId());require(causes.add(c.causeKey())&&zones.containsKey(c.zoneKey())&&effects.contains(c.effectKey())&&members.contains(c.targetId())&&(c.releasedOperationId()==null)==(c.releaseReason()==null));if(c.releasedOperationId()==null)require(live.add(c.zoneKey()+":"+c.effectKey()+":"+c.targetId()));}
        try {com.dndtool.service.Schema2OngoingEffectRules.validateGraph(b);}catch(IllegalArgumentException malformed){throw corrupt();}
        for(var f:b.flows()) {
            for(var cast:f.casts()) {if(cast.targetCastFrameKey()!=null)require(frames.containsKey(cast.targetCastFrameKey())&&frames.get(cast.targetCastFrameKey()).kind()==FrameKind.CAST);if(cast.readyKey()!=null)require(readies.containsKey(cast.readyKey()));}
            require(f.movements().size()<=1024&&f.readyReleases().size()<=1024&&f.abilityChecks().size()<=1024);
            for(var w:f.windows())if(w.trigger()!=null)require(w.trigger().equals(triggers.get(w.trigger().triggerKey())));
            for(var r:f.readyReleases())require(frames.containsKey(r.frameKey())&&readies.containsKey(r.readyKey())&&triggers.containsKey(r.triggerKey())&&(r.completed()?token(r.outcome()):r.outcome()==null));
            for(var c:f.abilityChecks())require(frames.containsKey(c.frameKey())&&members.contains(c.subjectId())&&token(c.kind())&&JdbcSchema2CharacterRuntimeRepository.ABILITIES.contains(c.abilityKey())&&c.d20()>=1&&c.d20()<=20&&c.proficiencyBonus()>=0&&c.proficiencyBonus()<=6&&c.difficultyClass()>=0&&c.difficultyClass()<=32767&&c.total()==c.d20()+c.abilityModifier()+c.proficiencyBonus()&&c.success()==(c.total()>=c.difficultyClass()));
        }
        if(b.status()==Status.ENDED)require(holders.isEmpty()&&b.activeFlow()==null&&b.ongoingEffects().stream().allMatch(e->e.status()==EffectStatus.ENDED)&&b.shields().stream().allMatch(e->e.status()==EffectStatus.ENDED)&&b.readyPreparations().stream().noneMatch(r->r.status()==ReadyStatus.ARMED||r.status()==ReadyStatus.PREPARING)&&b.environmentZones().stream().allMatch(z->z.endedOperationId()!=null)&&b.suppressionCauses().stream().allMatch(c->c.releasedOperationId()!=null));
    }
    private static void validateCommand(Command c)throws SQLException {
        require(c.kind()!=null);boolean start=c.kind()==Kind.START,reenter=c.kind()==Kind.REENTER_COMBAT;
        boolean move=c.kind()==Kind.MOVE||c.kind()==Kind.EXPLORATION_MOVE,attack=c.kind()==Kind.ATTACK,health=c.kind()==Kind.EXPLORATION_HEALTH;
        boolean reaction=Set.of(Kind.DECLARE_OPPORTUNITY_ATTACK,Kind.DECLARE_SHIELD,Kind.DECLARE_COUNTERSPELL,Kind.RELEASE_READY,Kind.PASS_REACTION,Kind.ADVANCE_WINDOW).contains(c.kind());
        boolean continuation=c.kind()==Kind.CONTINUE_MOVEMENT||c.kind()==Kind.CONTINUE_FLOW;
        boolean ready=Set.of(Kind.READY_ATTACK,Kind.READY_MOVE,Kind.READY_SPELL).contains(c.kind());
        boolean cast=c.kind()==Kind.CAST||c.kind()==Kind.DECLARE_SHIELD||c.kind()==Kind.DECLARE_COUNTERSPELL;
        require(start?c.actorId()==null:c.actorId()!=null);
        require((c.targetId()!=null)==(attack||health)&&(c.itemId()!=null)==(attack||c.kind()==Kind.DECLARE_OPPORTUNITY_ATTACK)
                &&(c.castInput()!=null)==cast&&(c.readyInput()!=null)==ready&&(c.healthInput()!=null)==health
                &&(c.zoneInput()!=null)==(c.kind()==Kind.CREATE_ENVIRONMENT_ZONE)&&(c.zoneKey()!=null)==(c.kind()==Kind.END_ENVIRONMENT_ZONE)
                &&(c.readyKey()!=null)==(c.kind()==Kind.RELEASE_READY)
                &&(move?!c.path().isEmpty()&&c.path().size()<=64:c.path().isEmpty())
                &&(start?c.deployments().size()>=2&&c.deployments().size()<=MAX_CHARACTER_MEMBERS&&c.deployments().size()+c.monsterDeployments().size()<=MAX_COMBATANTS:c.deployments().isEmpty()&&c.monsterDeployments().isEmpty())
                &&(start||reenter||c.tieBreakOrder().isEmpty())&&(c.flowKey()!=null)==(reaction||continuation)
                &&(c.leafKey()!=null)==(reaction||continuation)&&(reaction?c.windowKey()!=null:continuation?c.kind()==Kind.CONTINUE_FLOW||c.windowKey()!=null:c.windowKey()==null)&&(c.opportunityKey()!=null)==reaction
                &&(c.kind()==Kind.EXPLORATION_ADVANCE_TIME?c.seconds()>=1&&c.seconds()<=60:c.seconds()==0));
        for(UUID id:Arrays.asList(c.actorId(),c.targetId(),c.itemId(),c.flowKey(),c.leafKey(),c.windowKey(),c.opportunityKey(),c.readyKey(),c.zoneKey()))if(id!=null)uuid(id);
        require((c.breathInput()!=null)==(c.kind()==Kind.MONSTER_BREATH));
        require((c.monsterRestInput()!=null)==(c.kind()==Kind.BEGIN_MONSTER_REST||c.kind()==Kind.COMPLETE_MONSTER_REST));
        if(c.castInput()!=null)validateCastInput(c.castInput());if(c.readyInput()!=null)validateReadyInput(c.readyInput());
        if(c.zoneInput()!=null)require(ruleType(c.zoneInput().rule(),"adventure.environment")&&c.zoneInput().center()!=null);
    }
    private static void validateCastInput(CastInput input)throws SQLException {
        require(castingType(input.castingRule())&&ruleType(input.spell(),"adventure.spell")&&input.slotLevel()>=1&&input.slotLevel()<=9
                &&!input.targets().isEmpty()&&input.targets().size()<=MAX_COMBATANTS&&new HashSet<>(input.targets()).size()==input.targets().size());
        for(UUID target:input.targets())uuid(target);if(input.materialItemId()!=null)uuid(input.materialItemId());if(input.targetCastFrameKey()!=null)uuid(input.targetCastFrameKey());
    }
    private static boolean castingType(RuleIdentity rule){return ruleType(rule,"character.spellcasting")||ruleType(rule,"character.wizard_spellcasting");}
    private static void validateReadyInput(ReadyInput input)throws SQLException {
        require(input.trigger()!=null&&input.trigger().kind()!=null&&input.responseKind()!=null&&input.path().size()<=64);
        if(input.trigger().subjectId()!=null)uuid(input.trigger().subjectId());if(input.targetId()!=null)uuid(input.targetId());if(input.itemId()!=null)uuid(input.itemId());
        require((input.spell()!=null)==(input.responseKind()==ReadyResponseKind.SPELL));if(input.spell()!=null)validateCastInput(input.spell());
    }
    private static void validateOption(ReactionOption option)throws SQLException {
        require(option!=null&&option.kind()!=null);
        switch(option.kind()) {
            case OPPORTUNITY_ATTACK -> {uuid(option.itemId());require(option.castingRule()==null&&option.spell()==null&&option.slotLevel()==0&&option.readyKey()==null);}
            case SHIELD,COUNTERSPELL -> {require(option.itemId()==null&&castingType(option.castingRule())&&ruleType(option.spell(),"adventure.spell")&&option.slotLevel()>=1&&option.slotLevel()<=9&&option.readyKey()==null);}
            case READY_RELEASE -> {uuid(option.readyKey());require(option.itemId()==null&&option.castingRule()==null&&option.spell()==null&&option.slotLevel()==0);}
        }
    }
    private static boolean validBlessing(UUID effect,Integer die)throws SQLException {
        if(effect==null)return die==null;uuid(effect);return die!=null&&die>=1&&die<=4;
    }
    private static boolean ruleType(RuleIdentity rule,String type) {
        String prefix=switch(type){case "character.spellcasting","character.wizard_spellcasting"->"spellcasting";case "adventure.environment"->"environment";case "adventure.spell"->"spell";case "character.resource"->"resource";default->"";};
        return rule!=null && type.equals(rule.ruleType()) && rule.moduleKey()!=null && rule.moduleKey().matches("[a-z][a-z0-9_]{0,63}")
                && rule.ruleKey()!=null && rule.ruleKey().length()<=128 && rule.ruleKey().matches(prefix+"[.][a-z][a-z0-9_]*([.][a-z0-9_]+)*");
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
    private static RuleIdentity rule(Map<String,Object> row,String prefix)throws SQLException {
        try { return new RuleIdentity(ascii(row,prefix+"_module"),ascii(row,prefix+"_type"),ascii(row,prefix+"_key")); }
        catch(IllegalArgumentException malformed) { throw corrupt(); }
    }
    private static void ruleColumns(Map<String,Object> row,String prefix,RuleIdentity identity) {
        row.put(prefix+"_module",a(identity.moduleKey()));row.put(prefix+"_type",a(identity.ruleType()));row.put(prefix+"_key",a(identity.ruleKey()));
    }
    private static boolean token(String value){return value!=null&&value.matches("[A-Z][A-Z0-9_]{0,127}");}
    private static void uuid(UUID id)throws SQLException{require(id!=null&&id.version()==4&&id.variant()==2);}
    private static void require(boolean condition)throws SQLException{if(!condition)throw corrupt();}
    private static SQLException corrupt(){return new SQLException("S2_COMBAT_RUNTIME_CORRUPT");}
    @FunctionalInterface private interface Reader<T>{T read(ResultSet row)throws SQLException;}
    @FunctionalInterface private interface MapReader<T>{T read(Map<String,Object>row)throws SQLException;}
}
