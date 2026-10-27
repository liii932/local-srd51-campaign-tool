package com.dndtool.service;

import com.dndtool.module.composition.Schema2SpellFixtures;
import com.dndtool.persistence.Schema2RuntimeJdbcFixture;
import com.dndtool.persistence.BusinessWriteTransactions;
import com.dndtool.service.Schema2CombatRuntimeService.*;
import com.dndtool.service.Schema2CombatRuntimeService.Status;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.testing.IsolatedJvmTest;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2SpellFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises author material -> prepared snapshots -> actual character/combat history, never injected spell state. */
class Schema2SpellRuntimeServiceTest {
    static Schema2CombatRuntimeServiceTest.Fixture fixture(boolean initialize,boolean focus) throws Exception {
        var f=new Schema2CombatRuntimeServiceTest.Fixture(initialize,Schema2SpellFixtures.packages(),initialChoices());
        if(initialize && focus) for(UUID actor:List.of(f.a,f.b)) {
            UUID item=focus(actor);
            var owner=f.members.stream().filter(m->m.characterId().equals(actor)).findFirst().orElseThrow();
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,
                    f.characterService.execute(new Schema2CharacterRuntimeService.Request(item,owner.identity(),f.campaign,actor,
                            owner.preparationOperationId(),f.binding,f.character(actor).version(),equipment("ITEM_ACQUIRED",item,HOLY_SYMBOL,"CARRIED"),Map.of())).status());
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,
                    f.characterService.execute(f.characterRequest(actor,equipment("ITEM_EQUIPPED",item,null,"OFF_HAND"))).status());
        }
        return f;
    }
    static UUID focus(UUID actor) {return new UUID(actor.getMostSignificantBits(),actor.getLeastSignificantBits()+100);}
    static Command bless(Schema2CombatRuntimeServiceTest.Fixture f,UUID actor,List<UUID> targets) {
        return Command.cast(actor,new CastInput(CASTING,BLESS,targets,focus(actor)));
    }
    static Command cure(UUID actor,UUID target) {return Command.cast(actor,new CastInput(CASTING,CURE_WOUNDS,List.of(target),null));}
    static long slots(Schema2CombatRuntimeServiceTest.Fixture f,UUID actor) throws Exception {
        return f.character(actor).resources().stream().filter(r->r.identity().equals(SLOT_RESOURCE)).findFirst().orElseThrow().current();
    }

    @Test void blessCostAndEffectsCommitOnceAndRestoreAlongsidePausedMovement() throws Exception {
        var f=fixture(true,true); f.start(); Request cast=f.request(bless(f,f.a,List.of(f.a,f.b)));
        var accepted=f.commit(cast); assertEquals(1,slots(f,f.a));
        assertEquals("COMBAT_RESOURCE_SPENT",f.characterService.read(f.a).events().getLast().kind());
        assertEquals(1,accepted.event().after().ongoingEffects().size());
        int rolls=f.rolls,writes=f.runtime.writes;
        assertEquals(Status.REPLAYED,f.service.execute(cast).status()); assertEquals(rolls,f.rolls); assertEquals(writes,f.runtime.writes);
        f.commit(Command.move(f.a,List.of(new Position(-1,0)))); assertNotNull(f.battle.activeFlow());
        Save saved=f.service.read(f.combat); var restored=fixture(false,false);
        restored.source.unavailable(true);
        assertEquals(Status.COMMITTED,restored.service.restore(saved).status()); assertEquals(saved,restored.service.read(f.combat));
        assertEquals(0,restored.rolls); restored.battle=saved.combat().current();
        restored.commit(restored.reaction(Kind.PASS_REACTION)); restored.commit(restored.continuation());
        assertEquals(1,slots(restored,restored.a)); assertEquals(saved.combat().current().ongoingEffects(),restored.battle.ongoingEffects());
        restored.commit(Command.action(Kind.END_CONCENTRATION,restored.a));
        assertEquals(EffectStatus.ENDED,restored.battle.ongoingEffects().getFirst().status());
    }

    @Test void cureWoundsSpendsAndHealsTheSameCharacterWithConsecutiveCausalVersions() throws Exception {
        var f=fixture(true,false); f.start(); f.commit(Command.action(Kind.END_TURN,f.a));
        f.commit(Command.action(Kind.BEGIN_ATTACK,f.b)); f.nextRolls.addAll(List.of(15,1));
        f.commit(Command.attack(f.b,f.a,f.items.get(f.b))); long hp=f.character(f.a).hitPoints();
        assertTrue(hp<f.character(f.a).maximumHitPoints()); f.commit(Command.action(Kind.END_TURN,f.b));
        Request cast=f.request(cure(f.a,f.a)); f.nextRolls.add(2); var healed=f.commit(cast);
        var changes=healed.event().resolution().healthChanges(); assertEquals(2,changes.size());
        assertEquals(changes.getFirst().after(),changes.getLast().before());
        assertEquals(1,slots(f,f.a)); assertTrue(f.character(f.a).hitPoints()>hp);
        assertEquals(List.of(2),changes.getLast().healthResult().dice());
        assertEquals("COMBAT_HEALING_APPLIED",f.characterService.read(f.a).events().getLast().kind());
        int rolls=f.rolls; assertEquals(Status.REPLAYED,f.service.execute(cast).status()); assertEquals(rolls,f.rolls);
        Save save=f.service.read(f.combat); var restored=fixture(false,false);
        assertEquals(Status.COMMITTED,restored.service.restore(save).status()); assertEquals(save,restored.service.read(f.combat));
    }

    @Test void blessedDeathSavePersistsCharacterDieAndNullAbilityThroughReadAndRestore() throws Exception {
        var f=fixture(true,true); f.start(); f.commit(bless(f,f.a,List.of(f.b)));
        UUID effect=f.battle.ongoingEffects().getFirst().effectKey();
        f.commit(Command.action(Kind.END_TURN,f.a)); f.commit(Command.action(Kind.END_TURN,f.b));
        f.commit(Command.action(Kind.BEGIN_ATTACK,f.a)); f.nextRolls.addAll(List.of(20,5,5));
        f.commit(Command.attack(f.a,f.b,f.items.get(f.a)));
        assertEquals(0,f.character(f.b).hitPoints()); assertFalse(f.character(f.b).health().dead());
        Request nextTurn=f.request(Command.action(Kind.END_TURN,f.a)); int rolls=f.rolls;
        f.nextRolls.addAll(List.of(6,4)); var accepted=f.commit(nextTurn);
        assertEquals(rolls+2,f.rolls); assertTrue(f.nextRolls.isEmpty());
        var child=f.characterService.read(f.b).events().getLast();
        assertEquals("COMBAT_DEATH_SAVE_RESOLVED",child.kind());
        assertEquals(accepted.event().operationId(),child.command().combatInput().combatOperationId());
        assertEquals(6,child.healthResult().deathD20()); assertEquals(List.of(4),child.healthResult().dice());
        assertEquals(1,child.after().health().deathSuccesses());
        var flow=accepted.event().after().flows().getLast();
        assertEquals(FrameKind.SAVING_THROW,flow.frames().getFirst().kind());
        var savingThrow=flow.savingThrows().getFirst();
        assertEquals("DEATH",savingThrow.kind()); assertNull(savingThrow.abilityKey());
        assertEquals(effect,savingThrow.blessingEffectKey()); assertEquals(4,savingThrow.blessingD4());
        assertEquals(10,savingThrow.total()); assertTrue(savingThrow.success());
        assertNull(f.runtime.rows("s2_combat_saving_throw").getLast().get("ability_key"));
        int writes=f.runtime.writes; rolls=f.rolls;
        assertEquals(Status.REPLAYED,f.service.execute(nextTurn).status());
        assertEquals(writes,f.runtime.writes); assertEquals(rolls,f.rolls);
        Save save=f.service.read(f.combat); assertEquals(writes,f.runtime.writes); assertEquals(rolls,f.rolls);
        var restored=fixture(false,false); restored.source.unavailable(true);
        assertEquals(Status.COMMITTED,restored.service.restore(save).status());
        assertEquals(save,restored.service.read(f.combat)); assertEquals(0,restored.rolls);
        assertEquals(child,restored.characterService.read(restored.b).events().getLast());
        assertNull(restored.runtime.rows("s2_combat_saving_throw").getLast().get("ability_key"));
    }

    @Test void completeInputsVersionsAndSlotsRejectBeforeDiceOrWrites() throws Exception {
        var f=fixture(true,false); f.start();
        int rolls=f.rolls,writes=f.runtime.writes;
        assertEquals(Status.REJECTED,f.service.execute(f.request(bless(f,f.a,List.of(f.a)))).status());
        assertEquals(Status.REJECTED,f.service.execute(f.request(cure(f.a,UUID.randomUUID()))).status());
        assertEquals(rolls,f.rolls); assertEquals(writes,f.runtime.writes); assertEquals(2,slots(f,f.a));
        Request stale=f.request(cure(f.a,f.a)); f.commit(cure(f.a,f.a));
        rolls=f.rolls; writes=f.runtime.writes;
        assertEquals("S2_COMBAT_STALE_VERSION",f.service.execute(stale).code());
        assertEquals(rolls,f.rolls); assertEquals(writes,f.runtime.writes);
        f.commit(Command.action(Kind.END_TURN,f.a)); f.commit(Command.action(Kind.END_TURN,f.b)); f.commit(cure(f.a,f.a));
        f.commit(Command.action(Kind.END_TURN,f.a)); f.commit(Command.action(Kind.END_TURN,f.b));
        rolls=f.rolls; writes=f.runtime.writes;
        assertEquals("S2_SPELL_SLOT_UNAVAILABLE",f.service.execute(f.request(cure(f.a,f.a))).code());
        assertEquals(rolls,f.rolls); assertEquals(writes,f.runtime.writes); assertEquals(0,slots(f,f.a));
    }

    @Test void castAndCharacterFailuresRollbackTogether() throws Exception {
        for(String table:List.of("s2_character_combat_input","s2_combat_cast","s2_combat_event"))
            IsolatedJvmTest.run(FaultProbe.class,"rollback",table);
    }
    @Test void unknownSpellCommitIsResolvedWithoutRecasting() throws Exception {
        for(String outcome:List.of("COMMITTED","ROLLED_BACK")) IsolatedJvmTest.run(FaultProbe.class,"unknown",outcome);
    }
    @Test void missingEffectOrCastRowsFailClosedBeforeFurtherConsumption() throws Exception {
        for(String table:List.of("s2_combat_ongoing_effect","s2_combat_cast")) IsolatedJvmTest.run(FaultProbe.class,"corrupt",table);
    }
    public static final class FaultProbe {
        public static void main(String[] args) throws Exception {
            var f=fixture(true,true); f.start(); Request cast=f.request(bless(f,f.a,List.of(f.a,f.b)));
            Save before=f.service.read(f.combat); int rolls=f.rolls;
            if(args[0].equals("rollback")) {
                int count=f.runtime.rows(args[1]).size();
                f.runtime.afterWrite=db->{if(db.rows(args[1]).size()>count)throw new IllegalStateException("injected spell persistence failure");};
                assertEquals(Status.REJECTED,f.service.execute(cast).status()); assertEquals(before,f.service.read(f.combat));
            } else if(args[0].equals("unknown")) {
                f.runtime.lostCommit=Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);
                assertEquals(Status.UNKNOWN,f.service.execute(cast).status()); f.source.unavailable(true); int writes=f.runtime.writes;
                assertEquals(args[1].equals("COMMITTED")?Status.REPLAYED:Status.NOT_COMMITTED,f.service.inspect(cast).status());
                assertEquals(writes,f.runtime.writes); assertTrue(BusinessWriteTransactions.isBlocked());
            } else {
                f.commit(cast); Request next=f.request(Command.action(Kind.END_CONCENTRATION,f.a));
                assertFalse(f.runtime.rows(args[1]).isEmpty()); f.runtime.rows(args[1]).clear(); f.runtime.saveCommitted();
                int writes=f.runtime.writes;
                assertEquals(Status.REJECTED,f.service.execute(next).status()); assertEquals(writes,f.runtime.writes);
                assertThrows(java.sql.SQLException.class,()->f.service.read(f.combat));
            }
            assertEquals(rolls,f.rolls);
        }
    }
}
