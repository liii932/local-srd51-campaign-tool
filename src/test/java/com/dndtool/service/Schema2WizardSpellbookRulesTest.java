package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2WizardFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2WizardSpellbookRulesTest {
    private static final ProductionSchema2Projection MATERIAL=projection();
    private final Schema2WizardSpellbookRules books=new Schema2WizardSpellbookRules();
    @Test void realInitializationGrowthLearningSlotsAndRestPreparationReachFifthLevel() {
        var context=learnedContext(MATERIAL,UUID.randomUUID(),UUID.randomUUID(),5);State fifth=context.character();
        assertEquals(5,fifth.level());assertEquals(14,fifth.spellbook().learned().size());assertEquals(5,fifth.spellbook().learnedThroughLevel());
        assertEquals(17,fifth.abilities().get("ability.intelligence"));assertEquals(EVOCATION,fifth.subclasses().getFirst().subclass());
        assertEquals(4,fifth.resources().stream().filter(r->r.identity().equals(SLOT1)).findFirst().orElseThrow().maximum());
        assertEquals(3,fifth.resources().stream().filter(r->r.identity().equals(SLOT2)).findFirst().orElseThrow().maximum());
        assertEquals(2,fifth.resources().stream().filter(r->r.identity().equals(SLOT3)).findFirst().orElseThrow().maximum());
        assertEquals(List.of(SHIELD),fifth.spellbook().prepared());
        State rested=longRest(MATERIAL,fifth);
        State prepared=books.apply(MATERIAL,rested,Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(CASTING,List.of(),List.of(SHIELD,COUNTERSPELL))));
        assertTrue(prepared.spellbook().prepared().containsAll(List.of(SHIELD,COUNTERSPELL)));
        assertEquals(rested.rest().minute()+4,prepared.rest().minute());assertEquals(rested.rest().lastLongRestEndMinute(),prepared.spellbook().preparedAtRestMinute());
        books.validateState(MATERIAL,prepared);
        assertCode("S2_SPELLBOOK_REST_REQUIRED",()->books.apply(MATERIAL,prepared,Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(CASTING,List.of(),List.of(SHIELD)))));
    }
    @Test void initialBookRejectsDuplicateUnlearnedPreparedOrPrematureCounterspell() {
        State initial=initialContext(MATERIAL,UUID.randomUUID(),UUID.randomUUID()).character();
        var duplicate=new ArrayList<>(INITIAL_BOOK);duplicate.set(1,SHIELD);
        assertThrows(IllegalArgumentException.class,()->new SpellbookInput(CASTING,duplicate,List.of(SHIELD)));
        assertCode("S2_SPELLBOOK_COMMAND",()->new SpellbookInput(CASTING,Arrays.asList(SHIELD,null),List.of(SHIELD)));
        assertCode("S2_SPELLBOOK_COMMAND",()->new SpellbookInput(CASTING,INITIAL_BOOK,Arrays.asList(SHIELD,null)));
        var premature=new ArrayList<>(INITIAL_BOOK);premature.set(1,COUNTERSPELL);
        assertCode("S2_SPELLBOOK_LEARNING",()->books.apply(MATERIAL,initial,Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,premature,List.of(SHIELD)))));
        assertCode("S2_SPELLBOOK_PREPARED",()->books.apply(MATERIAL,initial,Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,INITIAL_BOOK,List.of(spell("detect_magic"))))));
        assertNull(initial.spellbook());assertEquals(0,initial.version());
    }
    @Test void learningMustBeCompleteBeforeNextGrowthAndCannotChangePreparedList() {
        State initial=initialContext(MATERIAL,UUID.randomUUID(),UUID.randomUUID()).character();
        assertCode("S2_SPELLBOOK_LEARNING_REQUIRED",()->books.requireLearningCompleteBeforeAdvance(MATERIAL,initial));
        State first=learnedContext(MATERIAL,UUID.randomUUID(),UUID.randomUUID(),1).character();
        var growth=new Schema2CharacterProgressionRules();
        State second=growth.advance(MATERIAL,growth.prepareAdvance(MATERIAL,first,WIZARD,2,"FIXED_AVERAGE",new GrowthChoices(EVOCATION,Map.of(),null,Map.of())),null);
        assertCode("S2_SPELLBOOK_LEARNING_REQUIRED",()->books.requireLearningCompleteBeforeAdvance(MATERIAL,second));
        assertCode("S2_SPELLBOOK_LEARNING",()->books.apply(MATERIAL,second,Command.spellbook("SPELLBOOK_ADVANCED",new SpellbookInput(CASTING,learnedAt(2),List.of(spell("detect_magic"))))));
        assertCode("S2_SPELLBOOK_REST_REQUIRED",()->books.apply(MATERIAL,first,Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(CASTING,List.of(),List.of(SHIELD)))));
    }
    @Test void legitimatelyLearnedAndPreparedMetadataSpellStillHasNoExecutionConsumer() {
        UUID actor=UUID.randomUUID(),campaign=UUID.randomUUID();var context=learnedContext(MATERIAL,campaign,actor,1);
        State rested=longRest(MATERIAL,context.character());
        State prepared=books.apply(MATERIAL,rested,Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(CASTING,List.of(),List.of(spell("alarm")))));
        var roster=Map.of(actor,new Schema2CombatRules.Context(MATERIAL,context.initial(),prepared));
        var member=new Schema2CombatState.Combatant(actor,new Schema2CombatState.Position(0,0),0,true,true,0,0,false,false,null);
        var battle=new Schema2CombatState.Battle(UUID.randomUUID(),campaign,Schema2CombatTestFixture.COMBAT,Schema2CombatState.Status.ACTIVE,
                1,1,0,0,List.of(member),List.of(new Schema2CombatState.Initiative(actor,10,2,12,0)),List.of());
        var input=new Schema2CombatState.CastInput(CASTING,spell("alarm"),List.of(actor),null);
        assertCode("S2_SPELL_UNAVAILABLE",()->new Schema2SpellRules().prepare(roster,battle,actor,input));
        assertCode("S2_SPELL_UNAVAILABLE",()->new Schema2SpellRules().prepare(roster,battle,actor,input,true));
        assertEquals(List.of(spell("alarm")),prepared.spellbook().prepared());assertEquals(rested.resources(),prepared.resources());
    }
    private static void assertCode(String code,Runnable body){assertEquals(code,assertThrows(IllegalArgumentException.class,body::run).getMessage());}
}
