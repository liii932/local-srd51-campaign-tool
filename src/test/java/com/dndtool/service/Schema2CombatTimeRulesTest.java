package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CombatTimeRulesTest {
    private final ProductionSchema2Projection material=Schema2CombatTestFixture.material();
    private final State initial=Schema2CombatTestFixture.context(material,UUID.randomUUID(),UUID.randomUUID()).character();

    @Test void tenCompletedRoundsAdvanceExactlyOneMinuteWithoutChangingOtherFacts() {
        State state=initial;
        for(int i=0;i<10;i++)state=Schema2CombatTimeRules.advanceSeconds(material,state,6);
        assertEquals(1,state.rest().minute());assertEquals(0,state.rest().second());assertEquals(initial.version()+10,state.version());
        assertEquals(initial.hitPoints(),state.hitPoints());assertEquals(initial.health(),state.health());assertEquals(initial.equipment(),state.equipment());
        assertEquals(initial.resources(),state.resources());assertEquals(initial.abilities(),state.abilities());assertEquals(initial.proficiencies(),state.proficiencies());
    }
    @Test void aRoundCarriesTheExactRemainderAndPreservesLastLongRestTime() {
        State before=clock(initial,new RestState(900,58,480L,37,null));
        var after=Schema2CombatTimeRules.advanceSeconds(material,before,6);
        assertEquals(new RestState(901,4,480L,37,null),after.rest());
    }
    @Test void stableRecoveryOccursAtTheSavedExactSecondWithoutAnotherDie() {
        State before=clock(initial,new RestState(59,58,null,null,null));
        before=Schema2CombatTestFixture.withHealth(before,0,new HealthState(3,0,0,true,false,60L,3));
        var pending=Schema2CombatTimeRules.advanceSeconds(material,before,4);
        assertEquals(60,pending.rest().minute());assertEquals(2,pending.rest().second());assertEquals(0,pending.hitPoints());assertTrue(pending.health().stable());
        var recovered=Schema2CombatTimeRules.advanceSeconds(material,pending,1);
        assertEquals(3,recovered.rest().second());assertEquals(1,recovered.hitPoints());assertFalse(recovered.health().stable());
        assertNull(recovered.health().stableRecoveryMinute());assertNull(recovered.health().stableRecoverySecond());assertEquals(3,recovered.health().temporaryHitPoints());
    }
    @Test void minuteRestCommandsKeepTheRemainderAndTheLongRestIntervalIsMeasuredInSeconds() {
        var rules=new Schema2CharacterRestRules();
        State before=clock(initial,new RestState(1440,39,480L,40,null));
        State started=rules.apply(material,before,command("REST_STARTED",new RestInput("LONG_REST",null,0,0,Map.of())),null);
        State sleeping=rules.apply(material,started,command("TIME_ADVANCED",new RestInput(null,"SLEEP",360,0,Map.of())),null);
        State ready=rules.apply(material,sleeping,command("TIME_ADVANCED",new RestInput(null,"LIGHT",120,0,Map.of())),null);
        assertEquals(39,ready.rest().second());assertEquals(40,ready.rest().lastLongRestEndSecond());
        Command finish=command("REST_COMPLETED",new RestInput(null,null,0,0,Map.of()));
        assertEquals("S2_REST_LONG_INTERVAL",assertThrows(IllegalArgumentException.class,()->rules.apply(material,ready,finish,null)).getMessage());
        State later=rules.apply(material,ready,command("TIME_ADVANCED",new RestInput(null,"SLEEP",1,0,Map.of())),null);
        State completed=rules.apply(material,later,finish,null);
        assertEquals(39,completed.rest().second());assertEquals(39,completed.rest().lastLongRestEndSecond());
        assertEquals(completed.rest().minute(),completed.rest().lastLongRestEndMinute());
    }
    @Test void unsupportedDurationsActiveRestAndClockOverflowAreRejected() {
        for(int seconds:List.of(0,-1,61))assertThrows(IllegalArgumentException.class,()->Schema2CombatTimeRules.advanceSeconds(material,initial,seconds));
        State full=clock(initial,new RestState(Schema2CharacterRestRules.MAX_MINUTE,59,null,null,null));
        assertThrows(IllegalArgumentException.class,()->Schema2CombatTimeRules.advanceSeconds(material,full,1));
        State active=new Schema2CharacterRestRules().apply(material,initial,command("REST_STARTED",new RestInput("SHORT_REST",null,0,0,Map.of())),null);
        assertThrows(IllegalArgumentException.class,()->Schema2CombatTimeRules.advanceSeconds(material,active,6));
        assertThrows(IllegalArgumentException.class,()->new Schema2CharacterRestRules().validateState(material,clock(initial,new RestState(1,60,null,null,null))));
    }
    private static Command command(String kind,RestInput input){return new Command(kind,null,0,null,null,null,GrowthChoices.EMPTY,input);}
    private static State clock(State before,RestState rest){return new State(before.identity(),before.campaignId(),before.characterId(),before.preparationOperationId(),before.characterClass(),before.abilities(),before.level(),before.proficiencyBonus(),before.hitPoints(),before.maximumHitPoints(),before.classes(),before.hitDicePools(),before.subclasses(),before.features(),before.feats(),before.proficiencies(),before.version(),before.resources(),rest,before.equipment(),before.health());}
}
