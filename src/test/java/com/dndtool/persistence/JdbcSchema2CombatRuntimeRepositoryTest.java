package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.Event;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.History;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.Schema2CombatState.Command;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2CombatRuntimeRepositoryTest {
    private static final JdbcSchema2CombatRuntimeRepository REPO = new JdbcSchema2CombatRuntimeRepository();
    private static final JdbcSchema2CharacterRuntimeRepository CHARACTERS = new JdbcSchema2CharacterRuntimeRepository();
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000000001"), B = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID CAMPAIGN = UUID.randomUUID(), COMBAT = UUID.randomUUID(), ITEM = UUID.randomUUID();
    private static final Identity IA = Identity.random(), IB = Identity.random();
    private static final UUID PA = UUID.randomUUID(), PB = UUID.randomUUID();
    private static final RuleIdentity CLASS = rule("character.class","class.guard"), COMBAT_RULE = rule("adventure.combat","combat.srd2014"), HEALTH = rule("adventure.health","health.srd2014");
    private static final List<Member> MEMBERS = List.of(new Member(A,IA,PA),new Member(B,IB,PB));
    private static final List<Dependency> DEPS = List.of(new Dependency(A,0),new Dependency(B,0));

    @Test void pendingWindowCompletePathAndImmutableAcceptedResultRoundTripAndRestore() throws Exception {
        History expected = pendingHistory(); var fixture = stored(expected);
        assertEquals(expected,REPO.read(fixture.connection,COMBAT));
        assertEquals(expected.events().get(1),REPO.findOperation(fixture.connection,expected.events().get(1).operationId()));
        assertEquals(FlowStatus.ACTIVE,REPO.findOperation(fixture.connection,expected.events().get(1).operationId()).after().activeFlow().status());
        assertNull(expected.current().activeFlow());
        assertEquals(20,expected.current().combatants().getFirst().movementSpentHalfFeet());
        assertEquals(WindowStatus.CONSUMED,expected.current().flows().getFirst().windows().getFirst().status());
        assertEquals(List.of(expected),REPO.findByCampaign(fixture.connection,CAMPAIGN));
        var target = seeded();try(var tx=BusinessWriteTransactions.openInternal(target.dataSource())){REPO.restore(tx.connection(),expected);tx.commit();}
        assertEquals(expected,REPO.read(target.connection,COMBAT));
        assertEquals(1,CHARACTERS.read(target.connection,A).events().size());
        assertEquals(1,CHARACTERS.read(target.connection,B).events().size());
    }

    @Test void attackDiceAndHealthCauseReadTheExistingCharacterOwnerWithoutDuplicatingHp() throws Exception {
        var fixture=seeded(); Battle initial=battle(0,List.of(),new Position(0,0),0);
        var start=start(initial); UUID op=UUID.randomUUID(),frame=UUID.randomUUID(),flow=UUID.randomUUID(),childOp=UUID.randomUUID();
        State before=CHARACTERS.read(fixture.connection,B).current(),after=character(B,IB,PB,1,0);
        var childCommand=new JdbcSchema2CharacterRuntimeRepository.Command("COMBAT_DAMAGE_APPLIED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,
                new HealthInput(HEALTH,null,null,"战斗伤害"),new CombatInput(COMBAT,op,frame,A,11,false));
        var child=new JdbcSchema2CharacterRuntimeRepository.Event(childOp,"c".repeat(64),childCommand,null,before,after,null,new HealthResult(List.of(),null,null,11,11));
        var attempt=new AttackAttempt(frame,A,B,ITEM,null,0,false,15,null,15,2,2,10,false,false,false,true,List.of(9),11);
        var f=new Flow(flow,frame,null,FlowStatus.COMPLETED,List.of(new Frame(frame,null,FrameKind.ATTACK,frame,Invocation.ROOT,FrameStatus.TERMINAL,Stage.ATTACK_RESOLVED,null,null)),List.of(),null,List.of(attempt));
        Battle ordinary=battle(1,List.of(f),new Position(0,0),0);
        var target=ordinary.combatants().get(1);
        Battle finalState=new Battle(ordinary.combatId(),ordinary.campaignId(),ordinary.combatRule(),ordinary.status(),ordinary.round(),ordinary.turnNumber(),ordinary.activeOrdinal(),ordinary.version(),
                List.of(ordinary.combatants().getFirst(),new Combatant(B,target.position(),target.side(),target.actionAvailable(),target.reactionAvailable(),0,0,false,false,null,true)),ordinary.initiative(),ordinary.flows());
        var health=new HealthChange(B,before,after,frame,11,false,child.healthResult(),false);
        Event attack=new Event(op,"b".repeat(64),Command.attack(A,B,ITEM),DEPS,List.of(childOp),initial,
                new Resolution(finalState,List.of(health),List.of(new Roll("ATTACK_D20",A,20,15),new Roll("DAMAGE",A,12,9)),List.of(new CombatFact("ATTACK_RESOLVED",A,frame,null))));
        try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){REPO.append(tx.connection(),MEMBERS,start);CHARACTERS.append(tx.connection(),child);REPO.append(tx.connection(),attack);tx.commit();}
        var read=REPO.read(fixture.connection,COMBAT);
        assertEquals(attack,read.events().getLast());assertEquals(List.of(9),read.current().flows().getFirst().attacks().getFirst().damageDice());
        assertTrue(read.current().combatants().get(1).prone());
        assertEquals(0,CHARACTERS.read(fixture.connection,B).current().hitPoints());
        assertFalse(fixture.rows("s2_combat_actor").getFirst().containsKey("hit_points"));
        fixture.rows("s2_character_combat_input").getFirst().put("combat_operation_id",bytes(UUID.randomUUID()));
        assertThrows(SQLException.class,()->REPO.read(fixture.connection,COMBAT));
    }

    @Test void roundTimeReferencesTheCharacterClockAndPreservesSixSecondResults() throws Exception {
        var fixture=seeded();Battle initial=battle(0,List.of(),new Position(0,0),0);
        Battle next=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,1,2,1,1,initial.combatants(),initial.initiative(),List.of());
        Event firstEnd=new Event(UUID.randomUUID(),"b".repeat(64),Command.action(Kind.END_TURN,A),DEPS,List.of(),initial,new Resolution(next,List.of(),List.of(),List.of()));
        UUID op=UUID.randomUUID();List<HealthChange>changes=new ArrayList<>();List<UUID>children=new ArrayList<>();
        try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){
            REPO.append(tx.connection(),MEMBERS,start(initial));REPO.append(tx.connection(),firstEnd);
            for(Member member:MEMBERS){
                State before=CHARACTERS.read(tx.connection(),member.characterId()).current();
                State after=new State(before.identity(),before.campaignId(),before.characterId(),before.preparationOperationId(),before.characterClass(),before.abilities(),before.level(),before.proficiencyBonus(),before.hitPoints(),before.maximumHitPoints(),before.classes(),before.hitDicePools(),before.subclasses(),before.features(),before.feats(),before.proficiencies(),1,before.resources(),new RestState(0,6,null,null,null),before.equipment(),before.health());
                UUID child=UUID.randomUUID(),frame=UUID.randomUUID();HealthResult health=new HealthResult(List.of(),null,null,0,0);
                var command=new JdbcSchema2CharacterRuntimeRepository.Command("COMBAT_TIME_ADVANCED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,new HealthInput(HEALTH,null,null,"战斗回合时间"),new CombatInput(COMBAT,op,frame,B,0,false,6));
                CHARACTERS.append(tx.connection(),new JdbcSchema2CharacterRuntimeRepository.Event(child,"c".repeat(64),command,null,before,after,null,health));
                children.add(child);changes.add(new HealthChange(member.characterId(),before,after,frame,0,false,health,false,true,6));
            }
            Battle round=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,2,3,0,2,initial.combatants(),initial.initiative(),List.of());
            REPO.append(tx.connection(),new Event(op,"d".repeat(64),Command.action(Kind.END_TURN,B),DEPS,children,next,new Resolution(round,changes,List.of(),List.of())));tx.commit();
        }
        var read=REPO.read(fixture.connection,COMBAT);assertEquals(changes,read.events().getLast().resolution().healthChanges());
        assertTrue(read.events().getLast().resolution().healthChanges().stream().allMatch(c->c.timeAdvance()&&c.seconds()==6));
        assertEquals(6,CHARACTERS.read(fixture.connection,A).current().rest().second());
        assertEquals(6,CHARACTERS.read(fixture.connection,B).current().rest().second());
    }

    @Test void everyCombatWriteFailureRollsBackRootWindowPathAndAcceptedHistory() throws Exception {
        History history=pendingHistory();var full=stored(history);int writes=full.writes-seeded().writes;
        assertTrue(writes>20);
        for(int point=1;point<=writes;point++){
            var fixture=seeded();fixture.failWrite=fixture.writes+point;
            try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){assertThrows(SQLException.class,()->REPO.restore(tx.connection(),history));tx.rollback();}
            assertTrue(fixture.rows("s2_combat_runtime").isEmpty(),"root retained at "+point);
            assertTrue(fixture.rows("s2_combat_event").isEmpty(),"event retained at "+point);
            assertTrue(fixture.rows("s2_combat_window").isEmpty(),"window retained at "+point);
            assertEquals(2,fixture.rows("s2_character_runtime").size());
        }
    }

    @Test void missingSurplusWrongIndicesAndCrossGraphRowsFailClosed() throws Exception {
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations=List.of(
            f->f.rows("s2_combat_member").getFirst().put("member_index",1),
            f->f.rows("s2_combat_dependency").removeFirst(),
            f->f.rows("s2_combat_command_point").removeFirst(),
            f->f.rows("s2_combat_state").add(new LinkedHashMap<>(f.rows("s2_combat_state").getFirst())),
            f->f.rows("s2_combat_actor").getFirst().put("actor_index",7),
            f->f.rows("s2_combat_initiative").getFirst().put("total",99),
            f->f.rows("s2_combat_frame").getFirst().put("parent_frame_key",bytes(UUID.randomUUID())),
            f->f.rows("s2_combat_opportunity_item").removeFirst(),
            f->f.rows("s2_combat_movement_point").getFirst().put("point_index",4),
            f->f.rows("s2_combat_window").getFirst().put("owner_frame_key",bytes(UUID.randomUUID())),
            f->f.rows("s2_combat_roll").getFirst().put("die_value",21));
        History history=pendingHistory();for(var mutation:mutations){var fixture=stored(history);mutation.accept(fixture);assertThrows(SQLException.class,()->REPO.read(fixture.connection,COMBAT));}
        var orphan=seeded();orphan.rows("s2_combat_frame").add(new LinkedHashMap<>(stored(history).rows("s2_combat_frame").getFirst()));
        assertThrows(SQLException.class,()->REPO.read(orphan.connection,COMBAT));
    }

    @Test void candidateAndMandatorySuffixCapacityRejectBeforeAnyMutation() throws Exception {
        var history=pendingHistory();
        assertDoesNotThrow(()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(null,null,8,2,3));
        assertDoesNotThrow(()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(null,null,16,16,0));
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(history,history.current(),128,2,3));
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(history,history.current(),30,16,64));
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(null,null,16,16,64));
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(null,null,-1,2,1));
        var fixture=seeded();assertThrows(SQLException.class,()->REPO.restore(fixture.connection,history));
        assertTrue(fixture.rows("s2_combat_event").isEmpty());
    }

    @Test void appendRequiresExactAdjacentStateAndRejectsReusedOperations() throws Exception {
        History history=pendingHistory();var fixture=stored(history);
        int writes=fixture.writes;
        try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){
            assertThrows(SQLException.class,()->REPO.append(tx.connection(),history.events().get(1)));tx.rollback();
        }
        assertEquals(writes,fixture.writes);
        List<Member> reversed=new ArrayList<>(MEMBERS);Collections.reverse(reversed);
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.validate(new History(history.current(),reversed,history.events())));
    }

    private static History pendingHistory(){
        Battle initial=battle(0,List.of(),new Position(0,0),0);Event first=start(initial);
        UUID flow=UUID.randomUUID(),root=UUID.randomUUID(),movement=UUID.randomUUID(),window=UUID.randomUUID(),opportunity=UUID.randomUUID(),selection=UUID.randomUUID();
        List<Position>path=List.of(new Position(1,0),new Position(2,0));
        var rootWaiting=new Frame(root,null,FrameKind.MOVEMENT,movement,Invocation.ROOT,FrameStatus.WAITING_WINDOW,Stage.MOVEMENT_LEAVE_REACH_BEFORE,window,null);
        var select=new Frame(selection,root,FrameKind.REACTION_SELECTION,opportunity,Invocation.REACTION,FrameStatus.PENDING,Stage.MOVEMENT_LEAVE_REACH_BEFORE,window,opportunity);
        var chance=new Opportunity(opportunity,B,0,List.of(ITEM),OpportunityStatus.PENDING,selection,null,null);
        var waiting=new Flow(flow,root,selection,FlowStatus.ACTIVE,List.of(rootWaiting,select),List.of(new Window(window,root,0,0,WindowStatus.OPEN,List.of(chance))),new Movement(movement,A,new Position(0,0),path,0,0,MovementStatus.PENDING,null),List.of());
        Battle paused=battle(1,List.of(waiting),new Position(0,0),0);
        Event move=new Event(UUID.randomUUID(),"b".repeat(64),Command.move(A,path),DEPS,List.of(),initial,new Resolution(paused,List.of(),List.of(),List.of(new CombatFact("MOVEMENT_PAUSED",A,window,null))));
        var decided=new Frame(selection,root,FrameKind.REACTION_SELECTION,opportunity,Invocation.REACTION,FrameStatus.DECIDED,Stage.MOVEMENT_LEAVE_REACH_BEFORE,window,opportunity);
        var passed=new Opportunity(opportunity,B,0,List.of(ITEM),OpportunityStatus.PASSED,selection,null,"PASS");
        var closed=new Flow(flow,root,root,FlowStatus.ACTIVE,List.of(rootWaiting,decided),List.of(new Window(window,root,0,0,WindowStatus.CLOSED,List.of(passed))),waiting.movement(),List.of());
        Battle afterPass=battle(2,List.of(closed),new Position(0,0),0);
        Event pass=new Event(UUID.randomUUID(),"c".repeat(64),Command.reaction(Kind.PASS_REACTION,B,null,flow,selection,window,opportunity),DEPS,List.of(),paused,new Resolution(afterPass,List.of(),List.of(),List.of(new CombatFact("REACTION_PASSED",B,opportunity,"PASS"))));
        var terminal=new Frame(root,null,FrameKind.MOVEMENT,movement,Invocation.ROOT,FrameStatus.TERMINAL,Stage.MOVEMENT_COMPLETED,window,null);
        var completed=new Flow(flow,root,null,FlowStatus.COMPLETED,List.of(terminal,decided),List.of(new Window(window,root,0,0,WindowStatus.CONSUMED,List.of(passed))),new Movement(movement,A,new Position(0,0),path,2,10,MovementStatus.COMPLETED,null),List.of());
        Battle finalState=battle(3,List.of(completed),new Position(2,0),20);
        Event continued=new Event(UUID.randomUUID(),"d".repeat(64),Command.continuation(A,flow,root,window),DEPS,List.of(),afterPass,new Resolution(finalState,List.of(),List.of(),List.of(new CombatFact("MOVEMENT_COMPLETED",A,movement,null))));
        return new History(finalState,MEMBERS,List.of(first,move,pass,continued));
    }
    private static Event start(Battle initial){return new Event(UUID.randomUUID(),"a".repeat(64),Command.start(List.of(new Deployment(A,new Position(0,0),0),new Deployment(B,new Position(0,1),1)),List.of()),DEPS,List.of(),null,
            new Resolution(initial,List.of(),List.of(new Roll("INITIATIVE",A,20,15),new Roll("INITIATIVE",B,20,10)),List.of(new CombatFact("COMBAT_STARTED",null,null,null))));}
    private static Battle battle(long version,List<Flow>flows,Position a,int spent){return new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,1,1,0,version,List.of(new Combatant(A,a,0,true,true,spent,0,false,false,null),new Combatant(B,new Position(0,1),1,true,true,0,0,false,false,null)),List.of(new Initiative(A,15,2,17,0),new Initiative(B,10,2,12,1)),flows);}
    private static Schema2RuntimeJdbcFixture stored(History history)throws Exception{var fixture=seeded();try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){REPO.restore(tx.connection(),history);tx.commit();}return fixture;}
    private static Schema2RuntimeJdbcFixture seeded()throws Exception{
        var f=new Schema2RuntimeJdbcFixture();for(Member m:MEMBERS)f.rows("s2_initial_plan").add(new LinkedHashMap<>(Map.of("operation_id",bytes(m.preparationOperationId()),"run_id",m.identity().runBytes(),"snapshot_id",m.identity().snapshotBytes(),"campaign_id",bytes(CAMPAIGN),"character_id",bytes(m.characterId()))));
        f.dataSource();f.saveCommitted();try(var tx=BusinessWriteTransactions.openInternal(f.dataSource())){for(Member m:MEMBERS){State s=character(m.characterId(),m.identity(),m.preparationOperationId(),0,11);var e=new JdbcSchema2CharacterRuntimeRepository.Event(UUID.randomUUID(),"e".repeat(64),new JdbcSchema2CharacterRuntimeRepository.Command("INITIALIZED",null,0,null,null),null,null,s);CHARACTERS.restore(tx.connection(),new JdbcSchema2CharacterRuntimeRepository.History(s,List.of(e)));}tx.commit();}return f;
    }
    private static State character(UUID id,Identity identity,UUID preparation,long version,long hp){return new State(identity,CAMPAIGN,id,preparation,CLASS,Map.of("ability.strength",15,"ability.dexterity",14,"ability.constitution",13,"ability.intelligence",12,"ability.wisdom",10,"ability.charisma",8),1,2,hp,11,List.of(new ClassLevel(CLASS,1)),List.of(new HitDicePool(10,1,1)),List.of(),List.of(),List.of(),List.of(),version,List.of(),RestState.INITIAL,List.of(),HealthState.INITIAL);}
    private static RuleIdentity rule(String type,String key){return new RuleIdentity("base",type,key);}
    private static byte[]bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
