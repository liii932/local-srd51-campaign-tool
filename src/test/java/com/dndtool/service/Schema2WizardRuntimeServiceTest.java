package com.dndtool.service;

import com.dndtool.module.composition.Schema2WizardFixtures;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.Schema2CombatState.Command;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2WizardFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2WizardRuntimeServiceTest {
    static Schema2CombatRuntimeServiceTest.Fixture fixture(boolean initialize)throws Exception {
        var f=new Schema2CombatRuntimeServiceTest.Fixture(initialize,Schema2WizardFixtures.packages(),GrowthChoices.EMPTY,WIZARD);
        if(initialize)for(UUID actor:List.of(f.a,f.b)) {
            var result=f.characterService.execute(f.characterRequest(actor,com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,INITIAL_BOOK,List.of(SHIELD)))));
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,result.status(),result.code());
        }
        return f;
    }
    static Command continueLeaf(Schema2CombatRuntimeServiceTest.Fixture f) {
        var flow=f.battle.activeFlow();var leaf=flow.frames().stream().filter(x->x.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
        var window=flow.windows().stream().filter(w->w.windowKey().equals(leaf.windowKey())).findFirst().orElseThrow();
        UUID actor=flow.attacks().stream().filter(a->a.frameKey().equals(window.ownerFrameKey())).map(AttackAttempt::actorId).findFirst().orElse(f.a);
        return new Command(Kind.CONTINUE_FLOW,actor,null,null,List.of(),List.of(),List.of(),flow.flowKey(),leaf.frameKey(),window.windowKey(),null);
    }
    @Test void spellbookCommitsTypedRowsRejectsFreePreparationAndSurvivesOtherOwners()throws Exception {
        var f=fixture(true);var state=f.character(f.b);assertEquals(6,state.spellbook().learned().size());
        assertFalse(f.runtime.rows("s2_character_event_learned_spell").isEmpty());
        var invalid=com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(CASTING,List.of(),List.of(spell("alarm"))));
        int writes=f.runtime.writes;var rejected=f.characterService.execute(f.characterRequest(f.b,invalid));
        assertEquals("S2_SPELLBOOK_REST_REQUIRED",rejected.code());assertEquals(writes,f.runtime.writes);assertEquals(state,f.character(f.b));
        f.start();f.commit(Command.action(Kind.END_TURN,f.a));f.commit(Command.action(Kind.END_TURN,f.b));
        assertEquals(state.spellbook(),f.character(f.b).spellbook());
        var save=f.service.read(f.combat);var target=fixture(false);target.source.unavailable(true);
        assertEquals(Schema2CombatRuntimeService.Status.COMMITTED,target.service.restore(save).status());assertEquals(save,target.service.read(f.combat));
    }
    @Test void shieldPausesKnownHitPersistsCostAndRestoresWithoutRerollingDamage()throws Exception {
        var f=fixture(true);f.start();f.commit(Command.action(Kind.BEGIN_ATTACK,f.a));f.nextRolls.add(10);
        f.commit(Command.attack(f.a,f.b,f.items.get(f.a)));var flow=f.battle.activeFlow();assertNotNull(flow);
        var attack=flow.attacks().getFirst();assertTrue(attack.initialHit());assertEquals(AttackStatus.HIT_PENDING_DAMAGE,attack.status());assertTrue(attack.damageDice().isEmpty());
        int rolls=f.rolls;long hp=f.character(f.b).hitPoints();var window=flow.windows().getLast();var opportunity=window.opportunities().getFirst();
        var command=Command.reactionSpell(Kind.DECLARE_SHIELD,f.b,new CastInput(CASTING,SHIELD,List.of(f.b),null),flow.flowKey(),flow.leafFrameKey(),window.windowKey(),opportunity.opportunityKey());
        var request=f.request(command);f.commit(request);assertEquals(rolls,f.rolls);
        assertEquals(1,f.character(f.b).resources().stream().filter(r->r.identity().equals(SLOT1)).findFirst().orElseThrow().current());
        int writes=f.runtime.writes;assertEquals(Schema2CombatRuntimeService.Status.REPLAYED,f.service.execute(request).status());assertEquals(writes,f.runtime.writes);
        var save=f.service.read(f.combat);var target=fixture(false);target.source.unavailable(true);
        var restored=target.service.restore(save);assertEquals(Schema2CombatRuntimeService.Status.COMMITTED,restored.status(),restored.code());target.battle=save.combat().current();
        target.commit(continueLeaf(target));assertEquals(0,target.rolls);assertEquals(hp,target.character(target.b).hitPoints());
        var finalAttack=target.battle.flows().getLast().attacks().getFirst();assertTrue(finalAttack.initialHit());assertFalse(finalAttack.hit());assertEquals(finalAttack.armorClass()+5,finalAttack.finalArmorClass());
        assertTrue(finalAttack.damageDice().isEmpty());assertEquals(AttackStatus.RESOLVED,finalAttack.status());
    }
}
