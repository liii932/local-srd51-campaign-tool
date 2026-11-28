package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CombatRules;
import com.dndtool.service.Schema2CombatState.Battle;
import com.dndtool.service.Schema2CombatState.Combatant;
import com.dndtool.service.Schema2CombatState.Cover;
import com.dndtool.service.Schema2ObservationRules;
import com.google.gson.JsonObject;
import java.util.*;

/** Current scene dependencies and the read-only turn budget used by both tables. */
final class CampaignCombatView {
    private CampaignCombatView() { }

    static Map<UUID, Schema2CombatRules.Context> roster(CampaignDomainData.State state, Battle battle,
            ProductionSchema2Projection material) {
        var scene = CampaignSpaceData.Ref.scene(battle.combatId());
        long second = state.space().clock(scene).second();
        Set<UUID> participants = new HashSet<>();
        battle.combatants().forEach(actor -> participants.add(actor.characterId()));
        Map<UUID, Schema2CombatRules.Context> roster = new LinkedHashMap<>();
        for (var character : state.characters()) {
            if (!participants.contains(character.id())) continue;
            var position = CampaignSpaceCommands.position(state, character.id());
            if (position != null && scene.equals(position.space()))
                roster.put(character.id(), new Schema2CombatRules.Context(material, character.initial(), character.history().current(), second));
        }
        return roster;
    }

    static JsonObject turnBudget(Map<UUID, Schema2CombatRules.Context> roster, Battle battle, Combatant actor) {
        int speed = new Schema2CombatRules().speed(roster, battle, actor.characterId());
        JsonObject budget = new JsonObject();
        budget.addProperty("actionsRemaining", actor.actionsRemaining());
        budget.addProperty("bonusActionsRemaining", actor.bonusActionsRemaining());
        budget.addProperty("reactionAvailable", actor.reactionAvailable());
        budget.addProperty("movementRemainingHalfFeet", actor.remainingMovementHalfFeet(speed));
        return budget;
    }

    static List<UUID> weaponTargets(Battle battle) {
        UUID attackerId = battle.activeActor();
        Combatant attacker = battle.combatants().stream().filter(actor -> actor.characterId().equals(attackerId))
                .findFirst().orElseThrow();
        List<UUID> targets = new ArrayList<>();
        for (Combatant defender : battle.combatants()) {
            if (defender.side() == attacker.side()) continue;
            var aim = Schema2ObservationRules.knownPosition(battle, attackerId, defender.characterId());
            if (aim != null && Schema2ObservationRules.cover(battle, attacker.position(), aim) != Cover.TOTAL)
                targets.add(defender.characterId());
        }
        return List.copyOf(targets);
    }
}
