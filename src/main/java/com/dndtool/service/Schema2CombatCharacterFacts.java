package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Typed causal child of a combat operation; health remains in the character history. */
final class Schema2CombatCharacterFacts {
    private Schema2CombatCharacterFacts() { }

    static RuleIdentity healthRule(ProductionSchema2Projection material) {
        var enabled = material.projection().rules().stream().filter(FinalHealth.class::isInstance)
                .map(FinalHealth.class::cast).filter(FinalHealth::enabled).toList();
        if (enabled.size() != 1 || !enabled.getFirst().definition().algorithm().equals("SRD_2014_HEALTH_V1"))
            throw new IllegalArgumentException("S2_COMBAT_HEALTH_RULE");
        return enabled.getFirst().identity();
    }

    static Command command(ProductionSchema2Projection material, UUID combatId, UUID operationId,
            UUID actorId, Schema2CombatState.HealthChange change) {
        return new Command(change.timeAdvance() ? "COMBAT_TIME_ADVANCED" : change.deathSave() ? "COMBAT_DEATH_SAVE_RESOLVED" : "COMBAT_DAMAGE_APPLIED",
                null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null,
                new HealthInput(healthRule(material), null, null, "Combat owner accepted consequence"),
                new CombatInput(combatId, operationId, change.frameKey(), actorId, change.damageAmount(), change.critical(),change.seconds()));
    }

    static State verify(ProductionSchema2Projection material, State before, Command command, HealthResult result, State savedAfter) {
        if (before == null || command.combatInput() == null || result == null
                || !healthRule(material).equals(command.healthInput().rule()))
            throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
        var health = new Schema2CharacterHealthRules();
        if (command.kind().equals("COMBAT_TIME_ADVANCED")) {
            State after=Schema2CombatTimeRules.advanceSeconds(material,before,command.combatInput().secondsAdvanced());
            if(!result.equals(new HealthResult(List.of(),null,null,0,(int)(after.hitPoints()-before.hitPoints()))))
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            return after;
        }
        if (command.kind().equals("COMBAT_DEATH_SAVE_RESOLVED")) {
            Command death = new Command("DEATH_SAVE_RESOLVED", null, 0, null, null, null,
                    GrowthChoices.EMPTY, null, null, null, command.healthInput());
            return health.verify(material, before, death, result);
        }
        if (!command.kind().equals("COMBAT_DAMAGE_APPLIED")) throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
        var damage = health.applyDamage(before, command.combatInput().damageAmount(), command.combatInput().critical());
        if (!result.equals(new HealthResult(List.of(), null, null, damage.requestedAmount(), damage.appliedAmount())))
            throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
        State after=damage.state();
        if(after.hitPoints()==0 && before.equipment().stream().anyMatch(item->Set.of("MAIN_HAND","OFF_HAND","BOTH_HANDS").contains(item.location()))) {
            if(savedAfter==null) throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            EquipmentItem held=before.equipment().stream().filter(item->Set.of("MAIN_HAND","OFF_HAND","BOTH_HANDS").contains(item.location())).findFirst().orElseThrow();
            EquipmentItem ground=savedAfter.equipment().stream().filter(item->item.itemId().equals(held.itemId())).findFirst().orElseThrow();
            if(!ground.location().equals("GROUND")||!command.combatInput().combatId().equals(ground.groundCombatId()))
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            after=Schema2CombatEquipmentFacts.drop(after,command.combatInput().combatId(),new Schema2CombatState.Position(ground.groundX(),ground.groundY()));
        }
        return after;
    }

    /** Stable child key under an accepted operation, with the project's UUID v4 representation. */
    static UUID childOperation(UUID operation, UUID character,int ordinal) {
        UUID derived = UUID.nameUUIDFromBytes(("schema2-combat-character/1/" + operation + "/" + character + "/" + ordinal).getBytes(StandardCharsets.US_ASCII));
        return new UUID((derived.getMostSignificantBits() & ~0xf000L) | 0x4000L,
                (derived.getLeastSignificantBits() & 0x3fffffffffffffffL) | 0x8000000000000000L);
    }
}
