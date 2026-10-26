package com.dndtool.service;

import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.service.Schema2CombatState.Position;
import java.util.*;

/** Equipment consequence of an unconscious combatant, inside the same health transaction. */
public final class Schema2CombatEquipmentFacts {
    private Schema2CombatEquipmentFacts() { }
    public static State drop(State state,UUID combatId,Position position) {
        Schema2CombatState.uuid(combatId);Objects.requireNonNull(position);
        if(state.hitPoints()!=0)throw Schema2CombatState.invalid("S2_COMBAT_DROP_STATE");
        List<EquipmentItem> items=state.equipment().stream().map(item->Set.of("MAIN_HAND","OFF_HAND","BOTH_HANDS").contains(item.location())
                ?new EquipmentItem(item.itemId(),item.item(),"GROUND",combatId,position.x(),position.y()):item).toList();
        return new State(state.identity(),state.campaignId(),state.characterId(),state.preparationOperationId(),state.characterClass(),state.abilities(),state.level(),state.proficiencyBonus(),state.hitPoints(),state.maximumHitPoints(),state.classes(),state.hitDicePools(),state.subclasses(),state.features(),state.feats(),state.proficiencies(),state.version(),state.resources(),state.rest(),items,state.health());
    }
}
