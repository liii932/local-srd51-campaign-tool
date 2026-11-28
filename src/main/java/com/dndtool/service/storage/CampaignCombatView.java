package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CombatRules;
import com.dndtool.service.Schema2CombatState.Battle;
import com.dndtool.service.Schema2CombatState.CastInput;
import com.dndtool.service.Schema2CombatState.Command;
import com.dndtool.service.Schema2CombatState.Combatant;
import com.dndtool.service.Schema2CombatState.Cover;
import com.dndtool.service.Schema2CombatState.Kind;
import com.dndtool.service.Schema2CombatState.Status;
import com.dndtool.service.Schema2ObservationRules;
import com.dndtool.service.Schema2FeatureSupport;
import com.google.gson.JsonObject;
import java.util.*;

/** Current scene dependencies and the read-only turn budget used by both tables. */
final class CampaignCombatView {
    private CampaignCombatView() { }

    record CantripChoice(RuleIdentity castingRule, RuleIdentity spell, UUID target) {
        Command command(UUID actor) {
            return Command.cast(actor, new CastInput(castingRule, spell, List.of(target), null, 0, null));
        }
    }

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
        budget.addProperty("surprised", battle.surprised(actor.characterId()));
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

    static boolean actionSurgeAvailable(Map<UUID, Schema2CombatRules.Context> roster, Battle battle) {
        UUID actor = battle.activeActor();
        if (!roster.containsKey(actor) || battle.activeFlow() != null) return false;
        try {
            new Schema2CombatRules().preflight(UUID.randomUUID(), battle.combatId(), battle.campaignId(), roster,
                    battle, Command.action(Kind.ACTION_SURGE, actor));
            return true;
        } catch (IllegalArgumentException unavailable) {
            return false;
        }
    }

    static List<Kind> cunningActions(Map<UUID, Schema2CombatRules.Context> roster, Battle battle) {
        if (!mayOfferCunningActions(roster, battle)) return List.of();
        var rules = new Schema2CombatRules();
        return List.of(Kind.CUNNING_DASH, Kind.CUNNING_DISENGAGE, Kind.CUNNING_HIDE).stream().filter(kind -> {
            try {
                rules.preflight(UUID.randomUUID(), battle.combatId(), battle.campaignId(), roster,
                        battle, Command.action(kind, battle.activeActor()));
                return true;
            } catch (IllegalArgumentException unavailable) { return false; }
        }).toList();
    }

    static boolean mayOfferCunningActions(Map<UUID, Schema2CombatRules.Context> roster, Battle battle) {
        if (battle.status() != Status.ACTIVE || battle.activeFlow() != null) return false;
        UUID id = battle.activeActor();
        var context = roster.get(id);
        if (context == null) return false;
        var character = context.character();
        if (!"class.rogue".equals(character.characterClass().ruleKey()) || character.level() < 2
                || character.hitPoints() == 0 || character.health().dead() || battle.surprised(id)) return false;
        if (battle.combatants().stream().noneMatch(actor -> actor.characterId().equals(id) && actor.bonusActionsRemaining() > 0))
            return false;
        try {
            return Schema2FeatureSupport.hasCunningAction(context.material(), character);
        } catch (IllegalArgumentException unavailable) { return false; }
    }

    static List<CantripChoice> cantripChoices(Map<UUID, Schema2CombatRules.Context> roster, Battle battle,
            Set<UUID> audience) {
        if (battle.status() != Status.ACTIVE || battle.activeFlow() != null) return List.of();
        UUID caster = battle.activeActor();
        var context = roster.get(caster);
        if (context == null || context.character().knownCantrips().isEmpty()) return List.of();
        var actors = battle.combatants();
        if (actors.stream().noneMatch(actor -> actor.characterId().equals(caster) && actor.actionAvailable()))
            return List.of();
        var rules = new Schema2CombatRules();
        var seen = new HashSet<UUID>();
        for (var actor : actors) {
            UUID id = actor.characterId();
            if (audience.contains(id) && Schema2ObservationRules.visible(battle, caster, id)) seen.add(id);
        }
        var choices = new ArrayList<CantripChoice>();
        for (var known : context.character().knownCantrips()) {
            var profile = context.material().classCantrips(known.characterClass()).orElseThrow();
            var spell = profile.options().stream().filter(option -> option.identity().equals(known.spell()))
                    .findFirst().orElseThrow();
            if (spell.cantripProfile() == null) continue;
            for (var actor : actors) {
                UUID target = actor.characterId();
                if (!audience.contains(target) || Schema2ObservationRules.knownPosition(battle, caster, target) == null) continue;
                var choice = new CantripChoice(known.cantripProfile(), known.spell(), target);
                try {
                    rules.preflightCandidate(UUID.randomUUID(), roster, battle, choice.command(caster), seen);
                    choices.add(choice);
                } catch (IllegalArgumentException unavailable) {
                    // The same preflight checks range, sight, components and the caster's action on submission.
                }
            }
        }
        return List.copyOf(choices);
    }
}
