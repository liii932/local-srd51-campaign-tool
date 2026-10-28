package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Typed causal child of a combat operation; resources and health remain in the character history. */
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
        return new Command(change.environmentInput()!=null ? "COMBAT_ENVIRONMENT_HEALTH" : change.resource() != null ? "COMBAT_RESOURCE_SPENT" : change.healing() ? "COMBAT_HEALING_APPLIED"
                        : change.timeAdvance() ? "COMBAT_TIME_ADVANCED" : change.deathSave() ? "COMBAT_DEATH_SAVE_RESOLVED" : "COMBAT_DAMAGE_APPLIED",
                change.resource(), change.resourceAmount(), null, null, null, GrowthChoices.EMPTY, null, null, null,
                change.environmentInput()!=null?change.environmentInput():change.resource() != null ? null : new HealthInput(healthRule(material), null, null, "Combat owner accepted consequence"),
                new CombatInput(combatId, operationId, change.frameKey(), actorId, change.damageAmount(), change.critical(),change.seconds()));
    }

    static State verify(ProductionSchema2Projection material, State before, Command command, HealthResult result, State savedAfter) {
        if (before == null || command.combatInput() == null)
            throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
        if (command.kind().equals("COMBAT_RESOURCE_SPENT")) {
            if (result != null || command.healthInput() != null || command.amount() != 1)
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            return new Schema2CharacterProgressionRules().spend(material, before, command.resource(), command.amount());
        }
        if (result == null || command.healthInput() == null || !healthRule(material).equals(command.healthInput().rule()))
            throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
        var health = new Schema2CharacterHealthRules();
        if(command.kind().equals("COMBAT_ENVIRONMENT_HEALTH")) {
            var input=command.healthInput();
            Command normal=new Command("HEALTH_EFFECT_RESOLVED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,input);
            var prepared=health.prepare(material,before,normal);
            var resolved=health.resolve(prepared,result.dice(),result.deathD20(),result.recoveryD4());
            var effect=material.projection().rules().stream().filter(FinalVitalEffect.class::isInstance).map(FinalVitalEffect.class::cast)
                    .filter(e->e.enabled()&&e.identity().equals(input.effect())).findFirst().orElseThrow(()->new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT"));
            if(!result.equals(resolved.result()) || command.combatInput().damageAmount()!=(effect.definition().kind()==VitalEffectKind.DAMAGE?result.rolledAmount():0))
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            return dropAfterDamage(before,resolved.state(),savedAfter,command);
        }
        if (command.kind().equals("COMBAT_HEALING_APPLIED")) {
            var healed=health.applyHealing(before, command.combatInput().damageAmount());
            if (result.dice().size()!=1 || result.dice().getFirst()<1 || result.dice().getFirst()>8
                    || result.deathD20()!=null || result.recoveryD4()!=null
                    || healed.result().rolledAmount()!=result.rolledAmount()
                    || healed.result().appliedAmount()!=result.appliedAmount())
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            return healed.state();
        }
        if (command.kind().equals("COMBAT_TIME_ADVANCED")) {
            State after=Schema2CombatTimeRules.advanceSeconds(material,before,command.combatInput().secondsAdvanced());
            if(!result.equals(new HealthResult(List.of(),null,null,0,(int)(after.hitPoints()-before.hitPoints()))))
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            return after;
        }
        if (command.kind().equals("COMBAT_DEATH_SAVE_RESOLVED")) {
            Command death = new Command("DEATH_SAVE_RESOLVED", null, 0, null, null, null,
                    GrowthChoices.EMPTY, null, null, null, command.healthInput());
            if (result.dice().size() > 1 || result.deathD20() == null)
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            int bonus=result.dice().isEmpty()?0:result.dice().getFirst();
            var resolved=health.resolveDeathSave(health.prepare(material,before,death),result.deathD20(),bonus,result.recoveryD4());
            if (!resolved.result().equals(result)) throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            return resolved.state();
        }
        if (!command.kind().equals("COMBAT_DAMAGE_APPLIED")) throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
        var damage = health.applyDamage(material, before, command.combatInput().damageAmount(), command.combatInput().critical());
        if (!result.equals(new HealthResult(List.of(), null, null, damage.requestedAmount(), damage.appliedAmount())))
            throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
        return dropAfterDamage(before,damage.state(),savedAfter,command);
    }
    private static State dropAfterDamage(State before,State after,State savedAfter,Command command) {
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
