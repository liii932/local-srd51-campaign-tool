package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CharacterHealthRulesTest {
    private final Schema2CharacterHealthRules rules = new Schema2CharacterHealthRules();
    private final ProductionSchema2Projection material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(packages("normal")));
    private State state = new Schema2CharacterProgressionRules().initial(material,
            fullPlan(material, IDENTITY, CAMPAIGN, CHARACTER), PREPARATION, fullAbilities(), INITIAL_CHOICES);

    @Test void temporaryBufferDamageHealingAndReplacementKeepSeparateFacts() {
        apply(effect(TEMPORARY, "REPLACE"), List.of(), null, null);
        assertEquals(5, state.health().temporaryHitPoints()); assertEquals(11, state.hitPoints());
        var damage = apply(effect(DAMAGE, null), List.of(), null, null);
        assertEquals(6, damage.result().rolledAmount()); assertEquals(6, damage.result().appliedAmount());
        assertEquals(10, state.hitPoints()); assertEquals(0, state.health().temporaryHitPoints());
        apply(effect(TEMPORARY, "REPLACE"), List.of(), null, null);
        var kept = apply(effect(TEMPORARY, "KEEP"), List.of(), null, null); assertEquals(0, kept.result().appliedAmount());
        var healing = apply(effect(HEALING, null), List.of(4, 4), null, null);
        assertEquals(9, healing.result().rolledAmount()); assertEquals(1, healing.result().appliedAmount());
        assertEquals(11, state.hitPoints()); assertEquals(5, state.health().temporaryHitPoints());
        var zero = apply(effect(ZERO_DAMAGE, null), List.of(6), null, null);
        assertEquals(0, zero.result().rolledAmount()); assertEquals(0, zero.result().appliedAmount());
        assertEquals(5, state.health().temporaryHitPoints());
    }

    @Test void dyingTracksFailuresNaturalOneNaturalTwentyAndInstantDeath() {
        drop(); assertEquals(0, state.hitPoints()); assertFalse(state.health().dead());
        apply(deathSave(), List.of(), 1, null); assertEquals(2, state.health().deathFailures());
        apply(deathSave(), List.of(), 20, null); assertEquals(1, state.hitPoints()); assertEquals(HealthState.INITIAL, state.health());
        apply(effect(MASSIVE_DAMAGE, null), List.of(), null, null);
        assertTrue(state.health().dead());
        assertThrows(IllegalArgumentException.class, () -> rules.prepare(material, state, effect(HEALING, null)));
        assertThrows(IllegalArgumentException.class, () -> rules.requireCommand(state, time(1)));
    }

    @Test void threeSuccessesDrawOneBoundedRecoveryDieAndTimeCrossingReconcilesWithoutDice() {
        drop(); apply(deathSave(), List.of(), 10, null); apply(deathSave(), List.of(), 19, null);
        var prepared = rules.prepare(material, state, deathSave()); assertTrue(prepared.needsRecoveryD4(10));
        assertFalse(prepared.needsRecoveryD4(20));
        assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, List.of(), 10, null));
        apply(deathSave(), List.of(), 10, 3);
        assertTrue(state.health().stable()); assertEquals(180L, state.health().stableRecoveryMinute());
        assertEquals(0, state.health().deathSuccesses()); assertEquals(0, state.health().deathFailures());
        var rest = new Schema2CharacterRestRules();
        Command first = time(179); rules.requireCommand(state, first);
        state = rules.afterRest(state, rest.apply(material, state, first, null), first);
        assertEquals(0, state.hitPoints()); assertTrue(state.health().stable());
        Command crossing = time(2); rules.requireCommand(state, crossing);
        state = rules.afterRest(state, rest.apply(material, state, crossing, null), crossing);
        assertEquals(1, state.hitPoints()); assertEquals(181, state.rest().minute()); assertEquals(HealthState.INITIAL, state.health());
    }

    @Test void damageAtZeroBreaksStabilityCountsFailureAndTemporaryPointsDoNotStabilize() {
        drop(); apply(effect(TEMPORARY, "REPLACE"), List.of(), null, null);
        assertEquals(0, state.hitPoints()); assertFalse(state.health().stable());
        assertThrows(IllegalArgumentException.class, () -> rules.requireCommand(state, time(1)));
        apply(effect(DAMAGE, null), List.of(), null, null);
        assertEquals(1, state.health().deathFailures()); assertEquals(0, state.health().temporaryHitPoints());
        apply(deathSave(), List.of(), 10, null); apply(deathSave(), List.of(), 10, null); apply(deathSave(), List.of(), 10, 1);
        apply(effect(DAMAGE, null), List.of(), null, null);
        assertFalse(state.health().stable()); assertNull(state.health().stableRecoveryMinute()); assertEquals(1, state.health().deathFailures());
        apply(deathSave(), List.of(), 1, null); assertTrue(state.health().dead());
    }

    @Test void potentialRecoveryTimeCapacityAndWrongDiceFailBeforeResolution() {
        drop(); apply(deathSave(), List.of(), 10, null); apply(deathSave(), List.of(), 10, null);
        state = withClock(state, Schema2CharacterRestRules.MAX_MINUTE - 239);
        assertThrows(IllegalArgumentException.class, () -> rules.prepare(material, state, deathSave()));
        state = withClock(state, 0);
        var prepared = rules.prepare(material, state, effect(HEALING, null));
        assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, List.of(5, 4), null, null));
        assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, List.of(4), null, null));
        assertThrows(IllegalArgumentException.class, () -> rules.resolve(prepared, List.of(4, 4), 20, null));
        assertThrows(IllegalArgumentException.class, () -> rules.prepare(material, state, effect(TEMPORARY, null)));
    }

    private void drop() { apply(effect(DAMAGE, null), List.of(), null, null); apply(effect(DAMAGE, null), List.of(), null, null); }
    private Schema2CharacterHealthRules.Resolution apply(Command command, List<Integer> dice, Integer deathD20, Integer recoveryD4) {
        var result = rules.resolve(rules.prepare(material, state, command), dice, deathD20, recoveryD4); state = result.state(); return result;
    }
    static Command time(int minutes) { return new Command("TIME_ADVANCED", null, 0, null, null, null, GrowthChoices.EMPTY, new RestInput(null, "NONE", minutes, 0, Map.of())); }
    private static State withClock(State before, long minute) {
        return new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(), before.characterClass(), before.abilities(),
                before.level(), before.proficiencyBonus(), before.hitPoints(), before.maximumHitPoints(), before.classes(), before.hitDicePools(), before.subclasses(), before.features(),
                before.feats(), before.proficiencies(), before.version(), before.resources(), new RestState(minute, null, null), before.equipment(), before.health());
    }
}
