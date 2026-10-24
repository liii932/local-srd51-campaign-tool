package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2RestStateTest {
    private static final JdbcSchema2CharacterRuntimeRepository REPOSITORY = new JdbcSchema2CharacterRuntimeRepository();
    private static final Identity ID = Identity.random();
    private static final UUID CAMPAIGN=UUID.randomUUID(), CHARACTER=UUID.randomUUID(), PREPARATION=UUID.randomUUID();
    private static final RuleIdentity CLASS=new RuleIdentity("base","character.class","class.guard"), REST=new RuleIdentity("base","adventure.rest","rest.srd2014");
    private static final Map<String,Integer> ABILITIES=Map.of("ability.strength",15,"ability.dexterity",14,"ability.constitution",13,"ability.intelligence",12,"ability.wisdom",10,"ability.charisma",8);

    @Test void timeRestInterruptionAndActualHitDieRollRoundTripAndDirectRestoreWithoutRandomness() throws Exception {
        History history=history(); var fixture=seeded();
        try(var transaction=BusinessWriteTransactions.openInternal(fixture.dataSource())){REPOSITORY.restore(transaction.connection(),history);transaction.commit();}
        assertEquals(history,REPOSITORY.read(fixture.connection,CHARACTER));
        assertEquals(4,fixture.rows("s2_character_event").get(4).get("hit_die_roll"));
        assertEquals(540L,history.current().rest().minute()); assertEquals(540L,history.current().rest().lastLongRestEndMinute());
        assertEquals(Map.of(10,1),history.events().getLast().command().restInput().recoveredHitDice());
        assertEquals(1,fixture.rows("s2_character_command_hit_die").size());
        assertEquals(1,history.current().hitDice());
    }
    @Test void restFactsMissingDiceAllocationsOrForgedTimeAreRejectedByActualReadback() throws Exception {
        for(String column:List.of("minute","rest_start_minute","consecutive_strenuous_minutes","interrupted")){
            var fixture=seeded();try(var transaction=BusinessWriteTransactions.openInternal(fixture.dataSource())){REPOSITORY.restore(transaction.connection(),history());transaction.commit();}
            var active=fixture.rows("s2_character_event_state").stream().filter(row->row.get("rest_module")!=null).findFirst().orElseThrow();active.put(column,-1L);
            assertThrows(SQLException.class,()->REPOSITORY.read(fixture.connection,CHARACTER));
        }
        var fixture=seeded();try(var transaction=BusinessWriteTransactions.openInternal(fixture.dataSource())){REPOSITORY.restore(transaction.connection(),history());transaction.commit();}
        fixture.rows("s2_character_command_hit_die").clear();assertThrows(SQLException.class,()->REPOSITORY.read(fixture.connection,CHARACTER));
    }
    @Test void malformedRestUnionsRejectBeforeAnyDatabaseAccess() {
        for(Runnable invalid:List.<Runnable>of(
                ()->command("REST_STARTED",new RestInput("SHORT_REST","SLEEP",0,0,Map.of())),
                ()->command("TIME_ADVANCED",new RestInput(null,"NONE",0,0,Map.of())),
                ()->command("HIT_DIE_SPENT",new RestInput(null,null,0,0,Map.of())),
                ()->command("REST_ENDED",new RestInput(null,null,0,0,Map.of(10,1))),
                ()->new Command("RESOURCE_SPENT",new RuleIdentity("base","character.resource","resource.energy"),1,null,null,null,GrowthChoices.EMPTY,new RestInput(null,null,0,0,Map.of()))))
            assertThrows(IllegalArgumentException.class,invalid::run);
    }
    private static History history(){
        var shortStart=new RestAttempt(REST,"SHORT_REST",0,11,0,0,0,"IN_PROGRESS",null,0,false);
        var shortDone=new RestAttempt(REST,"SHORT_REST",0,11,0,60,0,"IN_PROGRESS","LIGHT",0,false);
        var spending=new RestAttempt(REST,"SHORT_REST",0,11,0,60,0,"SPENDING_HIT_DICE","LIGHT",0,false);
        var longStart=new RestAttempt(REST,"LONG_REST",60,11,0,0,0,"IN_PROGRESS",null,0,false);
        var longDone=new RestAttempt(REST,"LONG_REST",60,11,480,0,0,"IN_PROGRESS","SLEEP",0,false);
        List<State> states=List.of(state(0,1,RestState.INITIAL),state(1,1,new RestState(0,null,shortStart)),state(2,1,new RestState(60,null,shortDone)),state(3,1,new RestState(60,null,spending)),state(4,0,new RestState(60,null,spending)),state(5,0,new RestState(60,null,null)),state(6,0,new RestState(60,null,longStart)),state(7,0,new RestState(540,null,longDone)),state(8,1,new RestState(540,540L,null)));
        List<Command> commands=List.of(new Command("INITIALIZED",null,0,null,null),command("REST_STARTED",new RestInput("SHORT_REST",null,0,0,Map.of())),command("TIME_ADVANCED",new RestInput(null,"LIGHT",60,0,Map.of())),command("REST_COMPLETED",new RestInput(null,null,0,0,Map.of())),command("HIT_DIE_SPENT",new RestInput(null,null,0,10,Map.of())),command("REST_ENDED",new RestInput(null,null,0,0,Map.of())),command("REST_STARTED",new RestInput("LONG_REST",null,0,0,Map.of())),command("TIME_ADVANCED",new RestInput(null,"SLEEP",480,0,Map.of())),command("REST_COMPLETED",new RestInput(null,null,0,0,Map.of(10,1))));
        List<Event> events=new ArrayList<>();for(int index=0;index<states.size();index++)events.add(new Event(UUID.randomUUID(),"a".repeat(64),commands.get(index),index==4?4:null,index==0?null:states.get(index-1),states.get(index)));return new History(states.getLast(),events);
    }
    private static State state(long version,int dice,RestState rest){return new State(ID,CAMPAIGN,CHARACTER,PREPARATION,CLASS,ABILITIES,1,2,11,11,List.of(new ClassLevel(CLASS,1)),List.of(new HitDicePool(10,dice,1)),List.of(),List.of(),List.of(),List.of(),version,List.of(),rest);}
    private static Command command(String kind,RestInput input){return new Command(kind,null,0,null,null,null,GrowthChoices.EMPTY,input);}
    private static Schema2RuntimeJdbcFixture seeded(){var f=new Schema2RuntimeJdbcFixture();f.rows("s2_initial_plan").add(new LinkedHashMap<>(Map.of("operation_id",bytes(PREPARATION),"run_id",ID.runBytes(),"snapshot_id",ID.snapshotBytes(),"campaign_id",bytes(CAMPAIGN),"character_id",bytes(CHARACTER))));f.dataSource();f.saveCommitted();return f;}
    private static byte[] bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
