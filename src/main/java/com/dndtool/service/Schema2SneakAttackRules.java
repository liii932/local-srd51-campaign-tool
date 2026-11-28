package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.WeaponAbilityPolicy;
import com.dndtool.module.composition.Schema2CompositionKernel.WeaponAttackDefinition;
import com.dndtool.service.Schema2CombatRules.Context;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.dndtool.service.Schema2CombatState.*;

/** Eligibility and retained evidence for the Rogue's on-hit damage choice. */
final class Schema2SneakAttackRules {
    static final String DAMAGE_ROLL = "SNEAK_ATTACK_DAMAGE";
    static final int DAMAGE_DIE = 6;

    private Schema2SneakAttackRules() { }

    static boolean decision(Kind kind) {
        return kind == Kind.USE_SNEAK_ATTACK || kind == Kind.DECLINE_SNEAK_ATTACK;
    }

    static boolean validCommand(Command command) {
        return command.actorId() != null && command.flowKey() != null && command.leafKey() != null
                && command.equals(Command.sneakAttack(command.kind(), command.actorId(), command.flowKey(), command.leafKey()));
    }

    static boolean available(Battle battle, Context source, WeaponAttackDefinition weapon) {
        return battle.status() == Status.ACTIVE && source != null
                && (weapon.ranged() || weapon.abilityPolicy() == WeaponAbilityPolicy.FINESSE)
                && Schema2FeatureSupport.hasSneakAttack(source.material(), source.character())
                && battle.flows().stream().flatMap(flow -> flow.attacks().stream())
                        .noneMatch(attack -> attack.actorId().equals(source.character().characterId())
                                && attack.sneakAttack() != null && attack.sneakAttack().status() == SneakAttackStatus.USED
                                && attack.sneakAttack().turnNumber() == battle.turnNumber());
    }

    static SneakAttackEvidence offer(Map<UUID, Context> roster, Battle battle,
            AttackAttempt attack, WeaponAttackDefinition weapon) {
        if (attack.disadvantage() && !attack.advantage() || !available(battle, roster.get(attack.actorId()), weapon))
            return null;
        if (attack.advantage() && !attack.disadvantage())
            return new SneakAttackEvidence(SneakAttackStatus.PENDING, battle.turnNumber(), null, List.of());
        Combatant target = Schema2CombatRules.combatant(battle, attack.targetId());
        return battle.combatants().stream()
                .filter(enemy -> nearbyEnemy(enemy, target, attack.actorId())
                        && Schema2MonsterRules.living(roster, battle, enemy.characterId()))
                .findFirst()
                .map(enemy -> new SneakAttackEvidence(SneakAttackStatus.PENDING, battle.turnNumber(), enemy.characterId(), List.of()))
                .orElse(null);
    }

    private static boolean nearbyEnemy(Combatant enemy, Combatant target, UUID attacker) {
        return !enemy.characterId().equals(attacker) && enemy.side() != target.side()
                && enemy.position().distanceFeet(target.position()) <= 5;
    }

    static boolean validState(Battle battle) {
        record ActorTurn(UUID actorId, long turnNumber) { }
        Set<ActorTurn> usedTurns = new HashSet<>();
        Set<ActorTurn> pendingTurns = new HashSet<>();
        for (Flow flow : battle.flows()) {
            List<Frame> choices = flow.frames().stream().filter(frame -> frame.stage() == Stage.SNEAK_ATTACK_CHOICE
                    || frame.kind() == FrameKind.ATTACK && frame.status() == FrameStatus.WAITING_CHOICE).toList();
            int pending = 0;
            for (AttackAttempt attack : flow.attacks()) {
                SneakAttackEvidence evidence = attack.sneakAttack();
                if (evidence == null) continue;
                if (attack.itemId() == null || attack.monsterAttack() != null || attack.spellAttack() != null
                        || !attack.initialHit() || !attack.hit() || !attack.targetAtAimedPosition()
                        || attack.disadvantage() && !attack.advantage()
                        || evidence.status() == null || evidence.turnNumber() < 1 || evidence.turnNumber() > battle.turnNumber())
                    return false;

                UUID enemyId = evidence.nearbyEnemyId();
                boolean effectiveAdvantage = attack.advantage() && !attack.disadvantage();
                if ((enemyId == null) != effectiveAdvantage
                        || enemyId != null && (enemyId.version() != 4 || enemyId.variant() != 2
                                || enemyId.equals(attack.actorId()) || enemyId.equals(attack.targetId())))
                    return false;

                if (evidence.status() == SneakAttackStatus.PENDING) {
                    pending++;
                    pendingTurns.add(new ActorTurn(attack.actorId(), evidence.turnNumber()));
                    if (battle.status() != Status.ACTIVE || evidence.turnNumber() != battle.turnNumber()
                            || flow.status() != FlowStatus.ACTIVE || !attack.frameKey().equals(flow.leafFrameKey())
                            || attack.status() != AttackStatus.HIT_PENDING_DAMAGE || !attack.damageDice().isEmpty()
                            || attack.damageAmount() != 0 || !validChoice(choices, attack))
                        return false;
                    if (enemyId != null) {
                        Combatant target = battle.combatants().stream()
                                .filter(actor -> actor.characterId().equals(attack.targetId())).findFirst().orElse(null);
                        if (target == null || battle.combatants().stream().noneMatch(enemy ->
                                enemyId.equals(enemy.characterId()) && nearbyEnemy(enemy, target, attack.actorId())))
                            return false;
                    }
                } else if (attack.status() != AttackStatus.RESOLVED || attack.damageDice().isEmpty()) return false;

                if (evidence.status() == SneakAttackStatus.USED) {
                    if (!usedTurns.add(new ActorTurn(attack.actorId(), evidence.turnNumber()))
                            || evidence.damageDice().size() != (attack.critical() ? 2 : 1)
                            || evidence.damageDice().stream().anyMatch(die -> die < 1 || die > DAMAGE_DIE))
                        return false;
                } else if (!evidence.damageDice().isEmpty()) return false;
            }
            if (choices.size() != pending || pending > 1) return false;
        }
        return usedTurns.stream().noneMatch(pendingTurns::contains);
    }

    private static boolean validChoice(List<Frame> choices, AttackAttempt attack) {
        return choices.size() == 1 && choices.stream().anyMatch(frame -> frame.frameKey().equals(attack.frameKey())
                && frame.ownerKey().equals(attack.frameKey()) && frame.kind() == FrameKind.ATTACK
                && frame.stage() == Stage.SNEAK_ATTACK_CHOICE && frame.status() == FrameStatus.WAITING_CHOICE
                && frame.windowKey() == null && frame.opportunityKey() == null);
    }
}
