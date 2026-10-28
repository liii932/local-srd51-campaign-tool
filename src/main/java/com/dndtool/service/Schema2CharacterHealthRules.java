package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Bounded direct environmental health effects, manual death saves and recovery; no combat turn owner. */
public final class Schema2CharacterHealthRules {
    private final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
    private final Schema2CharacterRestRules rests = new Schema2CharacterRestRules();

    public record Healing(long before, long maximum, long requested, long applied, long after) { }

    public static Healing heal(long current, long maximum, long requested) {
        if (maximum < 1 || maximum > 65535 || current < 0 || current > maximum || requested < 0 || requested > 65535)
            throw new IllegalArgumentException("S2_HEALTH_HEALING");
        long applied = Math.min(requested, maximum - current);
        return new Healing(current, maximum, requested, applied, current + applied);
    }

    public static boolean handles(Command command) { return Set.of("HEALTH_EFFECT_RESOLVED", "DEATH_SAVE_RESOLVED").contains(command.kind()); }
    public record Resolution(State state, HealthResult result) { }
    public record DamageResolution(State state, int requestedAmount, int appliedAmount, boolean critical) { }

    /** Shared health owner for a server-proved damage packet; this method never rolls or creates an effect. */
    public DamageResolution applyDamage(State before, int amount, boolean critical) {
        prepareDamage(before);
        return damageValidated(before,amount,critical);
    }
    public DamageResolution applyDamage(ProductionSchema2Projection material, State before, int amount, boolean critical) {
        prepareDamage(material,before);
        return damageValidated(before,amount,critical);
    }
    private DamageResolution damageValidated(State before,int amount,boolean critical) {
        if (amount < 0 || amount > 65535) throw invalid("S2_HEALTH_DAMAGE_AMOUNT");
        HealthState old = before.health(), next = old;
        int absorbed = Math.min(old.temporaryHitPoints(), amount), remaining = amount - absorbed;
        int applied = absorbed + (int)Math.min(before.hitPoints(), remaining);
        int temporary = old.temporaryHitPoints() - absorbed;
        long hp = Math.max(0, before.hitPoints() - remaining);
        if (before.hitPoints() == 0) {
            if (amount > 0) next = remaining >= before.maximumHitPoints() ? dead(temporary) : failure(old, temporary, critical ? 2 : 1);
        } else next = hp == 0 ? (remaining - before.hitPoints() >= before.maximumHitPoints() ? dead(temporary) : living(temporary)) : living(temporary);
        State after = copy(before, hp, next, before.rest(), before.version() + 1);
        validateState(after);
        return new DamageResolution(after, amount, applied, critical);
    }

    public Resolution applyHealing(State before, int amount) {
        validateState(before);
        if (before.health().dead()) throw invalid("S2_HEALTH_DEAD");
        if (before.rest().active()!=null || before.version()>=JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS-1) throw invalid("S2_HEALTH_COMMAND");
        var healing=heal(before.hitPoints(),before.maximumHitPoints(),amount);
        HealthState next=healing.applied()>0?living(before.health().temporaryHitPoints()):before.health();
        State after=copy(before,healing.after(),next,before.rest(),before.version()+1); validateState(after);
        return new Resolution(after,new HealthResult(List.of(),null,null,amount,(int)healing.applied()));
    }

    public void prepareDamage(ProductionSchema2Projection material, State before) {
        Schema2SpellRules.requireSupportedFeatures(material,before,"S2_HEALTH_EFFECT_UNSUPPORTED");
        prepareDamageCore(before);
    }
    /** Preflight usable by attack owners before their first random draw. */
    public void prepareDamage(State before) {
        if (!before.features().isEmpty() || !before.feats().isEmpty()) throw invalid("S2_HEALTH_EFFECT_UNSUPPORTED");
        prepareDamageCore(before);
    }
    private void prepareDamageCore(State before) {
        validateState(before);
        if (before.health().dead()) throw invalid("S2_HEALTH_DEAD");
        if (before.rest().active() != null) throw invalid("S2_HEALTH_REST_ACTIVE");
        if (before.version() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS - 1) throw invalid("S2_HEALTH_COMMAND");
    }

    public State verifyDamage(State before, int amount, boolean critical, DamageResolution saved) {
        var expected = applyDamage(before, amount, critical);
        if (!expected.equals(saved)) throw invalid("S2_HEALTH_RESULT_CORRUPT");
        return expected.state();
    }
    public static final class PreparedHealth {
        private final State before, capacityState;
        private final HealthInput input;
        private final VitalEffectDefinition effect;
        private PreparedHealth(State before, State capacityState, HealthInput input, VitalEffectDefinition effect) {
            this.before = before; this.capacityState = capacityState; this.input = input; this.effect = effect;
        }
        public State capacityState() { return capacityState; }
        public boolean deathSave() { return effect == null; }
        public int diceCount() { return effect == null ? 0 : effect.diceCount(); }
        public int diceSides() { return effect == null ? 0 : effect.diceSides(); }
        public boolean needsRecoveryD4(Integer deathD20) {
            return deathD20!=null && needsRecoveryD4(deathD20,0);
        }
        public boolean needsRecoveryD4(int deathD20, int bonus) {
            return deathSave() && deathD20!=1 && deathD20<20 && deathD20+bonus>=10 && before.health().deathSuccesses()==2;
        }
    }

    public PreparedHealth prepare(ProductionSchema2Projection material, State before, Command command) {
        growth.validateState(material, before); rests.validateState(material, before); validateState(before);
        Schema2CharacterEquipmentRules.requireCompleteScope(material, before, "S2_HEALTH_GROWTH_SCOPE");
        if (!handles(command) || before.version() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS - 1) throw invalid("S2_HEALTH_COMMAND");
        if (before.health().dead()) throw invalid("S2_HEALTH_DEAD");
        if (before.rest().active() != null) throw invalid("S2_HEALTH_REST_ACTIVE");
        Schema2SpellRules.requireSupportedFeatures(material,before,"S2_HEALTH_EFFECT_UNSUPPORTED");
        var owners = material.projection().rules().stream().filter(FinalHealth.class::isInstance).map(FinalHealth.class::cast).filter(FinalHealth::enabled).toList();
        HealthInput input = command.healthInput();
        if (owners.size() != 1 || !owners.getFirst().identity().equals(input.rule())
                || !owners.getFirst().definition().algorithm().equals("SRD_2014_HEALTH_V1")) throw invalid("S2_HEALTH_RULE_UNSUPPORTED");
        VitalEffectDefinition effect = null;
        if (command.kind().equals("DEATH_SAVE_RESOLVED")) {
            if (before.hitPoints() != 0 || before.health().stable()) throw invalid("S2_HEALTH_DEATH_SAVE_UNAVAILABLE");
            if (before.health().deathSuccesses() == 2 && before.rest().minute() > Schema2CharacterRestRules.MAX_MINUTE - 240)
                throw invalid("S2_HEALTH_TIME_CAPACITY");
        } else {
            var effects = material.projection().rules().stream().filter(FinalVitalEffect.class::isInstance).map(FinalVitalEffect.class::cast)
                    .filter(row -> row.enabled() && row.identity().equals(input.effect())).toList();
            if (effects.size() != 1 || !effects.getFirst().definition().algorithm().equals("DIRECT_VITAL_EFFECT_V1")) throw invalid("S2_HEALTH_EFFECT_UNSUPPORTED");
            effect = effects.getFirst().definition();
            if (effect.kind() == VitalEffectKind.TEMP_HP ? input.temporaryChoice() == null : input.temporaryChoice() != null)
                throw invalid("S2_HEALTH_TEMPORARY_CHOICE");
        }
        return new PreparedHealth(before, copy(before, before.hitPoints(), before.health(), before.rest(), before.version() + 1), input, effect);
    }

    /** A combat saving-throw owner proves the optional single Bless d4; natural 1/20 retain their own semantics. */
    public Resolution resolveDeathSave(PreparedHealth prepared, int deathD20, int bonus, Integer recoveryD4) {
        if (prepared==null || !prepared.deathSave() || deathD20<1 || deathD20>20 || bonus<0 || bonus>4
                || prepared.needsRecoveryD4(deathD20,bonus)!=(recoveryD4!=null)
                || recoveryD4!=null && (recoveryD4<1 || recoveryD4>4)) throw invalid("S2_HEALTH_RANDOM_RESULT");
        State before=prepared.before; HealthState old=before.health(), next; long hp=0; int applied=0;
        if (deathD20==20) {hp=1;applied=1;next=living(old.temporaryHitPoints());}
        else if (deathD20!=1 && deathD20+bonus>=10) {
            int successes=old.deathSuccesses()+1;
            next=successes==3?new HealthState(old.temporaryHitPoints(),0,0,true,false,before.rest().minute()+recoveryD4*60L,before.rest().second())
                    :new HealthState(old.temporaryHitPoints(),successes,old.deathFailures(),false,false,null);
        } else next=failure(old,old.temporaryHitPoints(),deathD20==1?2:1);
        State after=copy(before,hp,next,before.rest(),before.version()+1);validateState(after);
        return new Resolution(after,new HealthResult(bonus==0?List.of():List.of(bonus),deathD20,recoveryD4,0,applied));
    }

    /** Uses saved dice only. No dice source, command replay or benefit reapplication is involved in verification. */
    public Resolution resolve(PreparedHealth prepared, List<Integer> dice, Integer deathD20, Integer recoveryD4) {
        if (prepared == null || dice == null || dice.size() != prepared.diceCount()
                || dice.stream().anyMatch(value -> value == null || value < 1 || value > prepared.diceSides())) throw invalid("S2_HEALTH_RANDOM_RESULT");
        if (prepared.deathSave()) return resolveDeathSave(prepared,deathD20==null?0:deathD20,0,recoveryD4);
        State before = prepared.before; HealthState old = before.health(), next = old;
        long hp = before.hitPoints(); int amount = 0, applied = 0;
        if (deathD20 != null || recoveryD4 != null) throw invalid("S2_HEALTH_RANDOM_RESULT");
        amount = Math.max(0, dice.stream().mapToInt(Integer::intValue).sum() + prepared.effect.modifier());
        switch (prepared.effect.kind()) {
            case DAMAGE -> {
                var damage=damageValidated(before,amount,false);
                hp=damage.state().hitPoints();next=damage.state().health();applied=damage.appliedAmount();
            }
            case HEALING -> {
                var healing = heal(hp, before.maximumHitPoints(), amount); hp = healing.after(); applied = (int) healing.applied();
                if (applied > 0) next = living(old.temporaryHitPoints());
            }
            case TEMP_HP -> {
                if (prepared.input.temporaryChoice().equals("REPLACE")) {
                    applied = amount; next = new HealthState(amount, old.deathSuccesses(), old.deathFailures(), old.stable(), old.dead(), old.stableRecoveryMinute(),old.stableRecoverySecond());
                }
            }
        }
        State after = copy(before, hp, next, before.rest(), before.version() + 1); validateState(after);
        return new Resolution(after, new HealthResult(dice, deathD20, recoveryD4, amount, applied));
    }

    public State verify(ProductionSchema2Projection material, State before, Command command, HealthResult result) {
        if (result == null) throw invalid("S2_HEALTH_RESULT_CORRUPT");
        var expected = resolve(prepare(material, before, command), result.dice(), result.deathD20(), result.recoveryD4());
        if (!expected.result().equals(result)) throw invalid("S2_HEALTH_RESULT_CORRUPT");
        return expected.state();
    }

    public void validateState(State state) {
        HealthState value = state.health();
        if (value == null || state.maximumHitPoints() < 1 || state.maximumHitPoints() > 65535
                || state.hitPoints() < 0 || state.hitPoints() > state.maximumHitPoints()) throw invalid("S2_HEALTH_STATE");
        if (state.hitPoints() > 0) {
            if (value.dead() || value.stable() || value.deathSuccesses() != 0 || value.deathFailures() != 0 || value.stableRecoveryMinute() != null)
                throw invalid("S2_HEALTH_STATE");
        } else if (value.dead()) {
            if (value.stable() || value.deathSuccesses() != 0 || value.deathFailures() != 0 || value.stableRecoveryMinute() != null) throw invalid("S2_HEALTH_STATE");
        } else if (value.stable()) {
            if (value.deathSuccesses() != 0 || value.deathFailures() != 0 || value.stableRecoveryMinute() == null
                    || value.stableRecoveryMinute() < state.rest().minute()
                    || value.stableRecoveryMinute() == state.rest().minute() && value.stableRecoverySecond() <= state.rest().second()) throw invalid("S2_HEALTH_STATE");
        } else if (value.stableRecoveryMinute() != null) throw invalid("S2_HEALTH_STATE");
    }

    /** Protects other command owners before they consume any random value or change state. */
    public void requireCommand(State before, Command command) {
        if (before == null) return;
        validateState(before);
        if (before.health().dead()) throw invalid("S2_HEALTH_DEAD");
        if (before.hitPoints() == 0 && Set.of("CHECK_RESOLVED", "ITEM_ACQUIRED", "ITEM_REMOVED", "ITEM_EQUIPPED", "ITEM_UNEQUIPPED",
                "CHARACTER_ADVANCED", "STATISTICS_ADVANCED", "RESOURCE_SPENT", "RESOURCES_RECOVERED", "REST_STARTED").contains(command.kind()))
            throw invalid("S2_HEALTH_UNCONSCIOUS");
        if (before.hitPoints() == 0 && !before.health().stable() && command.kind().equals("TIME_ADVANCED"))
            throw invalid("S2_HEALTH_DEATH_SAVE_PENDING");
    }

    /** Reconciles stable recovery before validating the final time state. It never draws another die. */
    public State afterRest(State before, State after, Command command) {
        HealthState value = before.health(); long hp = after.hitPoints();
        if (command.kind().equals("TIME_ADVANCED") && value.stable() && (after.rest().minute() > value.stableRecoveryMinute()
                || after.rest().minute() == value.stableRecoveryMinute() && after.rest().second() >= value.stableRecoverySecond())) {
            hp = 1; value = living(value.temporaryHitPoints());
        } else if (command.kind().equals("REST_COMPLETED") && before.rest().active().kind().equals("LONG_REST")) value = living(0);
        else if (hp > before.hitPoints()) value = living(value.temporaryHitPoints());
        State result = copy(after, hp, value, after.rest(), after.version()); validateState(result); return result;
    }

    private static HealthState living(int temporary) { return new HealthState(temporary, 0, 0, false, false, null); }
    private static HealthState dead(int temporary) { return new HealthState(temporary, 0, 0, false, true, null); }
    private static HealthState failure(HealthState before, int temporary, int count) {
        int failures = before.deathFailures() + count;
        return failures >= 3 ? dead(temporary) : new HealthState(temporary, before.deathSuccesses(), failures, false, false, null);
    }
    private static State copy(State before, long hp, HealthState health, RestState clock, long version) {
        return new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(), before.characterClass(),
                before.abilities(), before.level(), before.proficiencyBonus(), hp, before.maximumHitPoints(), before.classes(), before.hitDicePools(),
                before.subclasses(), before.features(), before.feats(), before.proficiencies(), version, before.resources(), clock, before.equipment(), health, before.spellbook());
    }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
