package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterRuntime.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CharacterRuntime.COMBAT_AMMUNITION_SPENT;
import static com.dndtool.service.Schema2CharacterRuntime.RANGED_WEAPON_ADJUDICATION;

/** Typed causal child of a combat operation, retained in the character history. */
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

    static Command ammunitionCommand(UUID combatId, UUID operationId, Schema2CombatState.AmmunitionUse use) {
        EquipmentItem item = Schema2CombatEquipmentFacts.carried(use.before(), use.itemId());
        return new Command(COMBAT_AMMUNITION_SPENT, null, 0, null, null, null, GrowthChoices.EMPTY, null, null,
                new EquipmentInput(item.itemId(), item.item(), item.location(), RANGED_WEAPON_ADJUDICATION), null,
                new CombatInput(combatId, operationId, use.frameKey(), use.characterId(), 0, false));
    }

    static State verify(ProductionSchema2Projection material, State before, Command command, HealthResult result, State savedAfter) {
        if (before == null || command.combatInput() == null)
            throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
        if (COMBAT_AMMUNITION_SPENT.equals(command.kind())) {
            EquipmentInput input = command.equipmentInput();
            EquipmentItem item = Schema2CombatEquipmentFacts.carried(before, input.itemId());
            if (result != null || before.hitPoints() == 0 || !command.combatInput().actorId().equals(before.characterId())
                    || !input.item().equals(item.item())
                    || Schema2CharacterEquipmentRules.definition(material, item.item()).kind() != EquipmentKind.AMMUNITION)
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            return Schema2CombatEquipmentFacts.spendAmmunition(before, input.itemId());
        }
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
            var input = command.combatInput();
            // The combat owner also verifies that this child belongs to the retained Second Wind event.
            boolean secondWind = input.frameKey().equals(Schema2CombatRules.secondWindReference(input.combatOperationId()));
            int die = result.dice().size() == 1 ? result.dice().getFirst() : 0;
            if (die < 1 || die > (secondWind ? 10 : 8) || result.deathD20() != null || result.recoveryD4() != null
                    || secondWind && (!input.actorId().equals(before.characterId()) || input.damageAmount() != die + before.level()
                        || Schema2FeatureSupport.secondWindResource(material, before) == null))
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            var healed = health.applyHealing(before, input.damageAmount());
            if (healed.result().rolledAmount() != result.rolledAmount()
                    || healed.result().appliedAmount() != result.appliedAmount())
                throw new IllegalArgumentException("S2_COMBAT_CHARACTER_FACT");
            return healed.state();
        }
        if (command.kind().equals("COMBAT_TIME_ADVANCED")) {
            State after=command.combatInput().secondsAdvanced()>60?Schema2CombatTimeRules.advanceRestSeconds(material,before,command.combatInput().secondsAdvanced()):Schema2CombatTimeRules.advanceSeconds(material,before,command.combatInput().secondsAdvanced());
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
