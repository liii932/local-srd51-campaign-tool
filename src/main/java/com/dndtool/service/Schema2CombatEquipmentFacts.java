package com.dndtool.service;

import com.dndtool.service.Schema2CharacterRuntime.*;
import com.dndtool.service.Schema2CombatState.Position;
import java.util.*;

/** Equipment consequences retained under their originating combat event. */
public final class Schema2CombatEquipmentFacts {
    private Schema2CombatEquipmentFacts() { }
    static EquipmentItem carried(State state, UUID itemId) {
        return state.equipment().stream().filter(item -> item.itemId().equals(itemId) && item.location().equals("CARRIED"))
                .findFirst().orElseThrow(() -> Schema2CombatState.invalid("S2_COMBAT_AMMUNITION_UNAVAILABLE"));
    }
    static State spendAmmunition(State state, UUID itemId) {
        EquipmentItem spent = carried(state, itemId);
        List<EquipmentItem> items = state.equipment().stream().filter(item -> !item.equals(spent)).toList();
        return withEquipment(state, items, state.version() + 1);
    }
    public static State drop(State state,UUID combatId,Position position) {
        Schema2CombatState.uuid(combatId);Objects.requireNonNull(position);
        if(state.hitPoints()!=0)throw Schema2CombatState.invalid("S2_COMBAT_DROP_STATE");
        List<EquipmentItem> items=state.equipment().stream().map(item->Set.of("MAIN_HAND","OFF_HAND","BOTH_HANDS").contains(item.location())
                ?new EquipmentItem(item.itemId(),item.item(),"GROUND",combatId,position.x(),position.y()):item).toList();
        return withEquipment(state, items, state.version());
    }
    private static State withEquipment(State state, List<EquipmentItem> items, long version) {
        return new State(state.identity(),state.campaignId(),state.characterId(),state.preparationOperationId(),state.characterClass(),state.abilities(),state.level(),state.proficiencyBonus(),state.hitPoints(),state.maximumHitPoints(),state.classes(),state.hitDicePools(),state.subclasses(),state.features(),state.feats(),state.proficiencies(),version,state.resources(),state.rest(),items,state.health(),state.spellbook(),state.knownCantrips());
    }
}
