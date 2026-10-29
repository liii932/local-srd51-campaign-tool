package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;

/** Advances the existing character clock for a completed combat round; no second time store or dice. */
public final class Schema2CombatTimeRules {
    private Schema2CombatTimeRules() { }

    /** The combat owner supplies the registered duration after proving the full round is complete. */
    public static State advanceSeconds(ProductionSchema2Projection material, State before, int seconds) {
        if(seconds>60)throw invalid("S2_COMBAT_TIME_INPUT");return advance(material,before,seconds);
    }
    /** Only the typed monster-rest completion owner may derive this longer interval. */
    static State advanceRestSeconds(ProductionSchema2Projection material, State before, int seconds) {
        return advance(material,before,seconds);
    }
    private static State advance(ProductionSchema2Projection material, State before, int seconds) {
        var growth = new Schema2CharacterProgressionRules();
        var rests = new Schema2CharacterRestRules();
        var healthRules = new Schema2CharacterHealthRules();
        growth.validateState(material, before);
        rests.validateState(material, before);
        healthRules.validateState(before);
        if (seconds < 1 || seconds > 28800 || before.rest().active() != null)
            throw invalid("S2_COMBAT_TIME_INPUT");
        if (before.version() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS - 1)
            throw invalid("S2_COMBAT_TIME_CAPACITY");
        long totalSeconds = before.rest().second() + (long) seconds;
        long minute = before.rest().minute() + totalSeconds / 60;
        int second = (int) (totalSeconds % 60);
        if (minute > Schema2CharacterRestRules.MAX_MINUTE) throw invalid("S2_COMBAT_TIME_CAPACITY");
        RestState clock = new RestState(minute, second, before.rest().lastLongRestEndMinute(),
                before.rest().lastLongRestEndSecond(), null);
        long hp = before.hitPoints();
        HealthState health = before.health();
        if (health.stable() && (minute > health.stableRecoveryMinute()
                || minute == health.stableRecoveryMinute() && second >= health.stableRecoverySecond())) {
            hp = 1;
            health = new HealthState(health.temporaryHitPoints(), 0, 0, false, false, null, null);
        }
        State after = new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(),
                before.characterClass(), before.abilities(), before.level(), before.proficiencyBonus(), hp,
                before.maximumHitPoints(), before.classes(), before.hitDicePools(), before.subclasses(), before.features(),
                before.feats(), before.proficiencies(), before.version() + 1, before.resources(), clock, before.equipment(), health, before.spellbook());
        growth.validateState(material, after);
        rests.validateState(material, after);
        healthRules.validateState(after);
        return after;
    }

    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
