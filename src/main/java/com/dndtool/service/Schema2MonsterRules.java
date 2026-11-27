package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CombatRules.Context;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CombatState.*;

/** The finite stat-block owner. Metadata is never interpreted as an executable action. */
final class Schema2MonsterRules {
    private Schema2MonsterRules() { }
    static ProductionSchema2Projection material(Map<UUID,Context> roster) { return roster.values().iterator().next().material(); }
    static MonsterDefinition definition(Map<UUID,Context> roster,RuleIdentity identity) {
        return definition(material(roster),identity);
    }
    static MonsterDefinition definition(ProductionSchema2Projection material,RuleIdentity identity) {
        var definitions=material.projection().rules().stream().filter(FinalMonster.class::isInstance)
                .map(FinalMonster.class::cast).filter(r->r.enabled()&&r.identity().equals(identity)).toList();
        if(definitions.size()!=1)throw invalid("S2_MONSTER_RULE_UNSUPPORTED");
        var definition=definitions.getFirst().definition();validateScope(definition.statBlock());return definition;
    }
    private static void validateScope(MonsterStatBlock block) {
        if(block.size()!=MonsterSize.MEDIUM&&block.size()!=MonsterSize.SMALL)throw invalid("S2_MONSTER_SPACE_UNSUPPORTED");
        if(block.hitPoints()>65535)throw invalid("S2_MONSTER_HEALTH_UNSUPPORTED");
        int proficiency=2+Math.max(0,(block.challengeNumerator()-block.challengeDenominator())/(4*block.challengeDenominator()));
        for(var save:block.savingThrows())if(proficiency>6||save.bonus()-modifier(block,save.abilityKey())!=proficiency)throw invalid("S2_MONSTER_SAVE_UNSUPPORTED");
        for(var trait:block.traits())if(trait.scope()==MonsterTraitScope.UNSUPPORTED)throw invalid("S2_MONSTER_TRAIT_UNSUPPORTED");
        for(var defense:block.damageDefenses())if(defense.qualifier()!=MonsterDefenseQualifier.ALL&&defense.qualifier()!=MonsterDefenseQualifier.NONMAGICAL_ATTACKS)throw invalid("S2_MONSTER_RULE_UNSUPPORTED");
    }
    static MonsterActionProfile action(Map<UUID,Context> roster,EntryIdentity identity) {
        return validateAction(actionEntry(material(roster), identity).actionProfile());
    }
    private static MonsterActionEntry actionEntry(ProductionSchema2Projection material, EntryIdentity identity) {
        var entries=material.projection().entries().stream().filter(FinalEntry::enabled)
                .map(FinalEntry::entry).filter(MonsterActionEntry.class::isInstance).map(MonsterActionEntry.class::cast)
                .filter(e->e.identity().equals(identity)).toList();
        if(entries.size()!=1||!identity.collectionKey().equals("actions"))throw invalid("S2_MONSTER_ACTION_UNSUPPORTED");
        return entries.getFirst();
    }
    static MonsterAttackProfile meleeAttack(Map<UUID, Context> roster, Battle battle, UUID source, EntryIdentity action) {
        MonsterInstance monster = instance(battle, source);
        if (!monster.monster().equals(action.rule())) throw invalid("S2_MONSTER_ACTION_UNSUPPORTED");
        var material = material(roster);
        return validateMeleeAction(actionEntry(material, action).actionProfile(), definition(material, monster.monster()).statBlock());
    }
    private static MonsterAttackProfile validateMeleeAction(MonsterActionProfile profile, MonsterStatBlock block) {
        if (profile.mode() != MonsterActionMode.MELEE_ATTACK_V1
                || !MONSTER_MELEE_ATTACK_MECHANISM.equals(profile.mechanism()) || profile.attackProfile() == null)
            throw invalid("S2_MONSTER_ACTION_UNSUPPORTED");
        if (block.traits().stream().anyMatch(trait -> switch (trait.scope()) {
            case ATTACK_ROLL_PACK_TACTICS_ONLY -> !trait.key().equals("pack_tactics");
            case PERCEPTION_HEARING_SMELL_ONLY -> false;
            default -> true;
        })) throw invalid("S2_MONSTER_TRAIT_UNSUPPORTED");
        return profile.attackProfile();
    }
    private static List<MonsterActionEntry> actions(ProductionSchema2Projection material, RuleIdentity monster) {
        return material.projection().entries().stream().filter(FinalEntry::enabled).map(FinalEntry::entry)
                .filter(MonsterActionEntry.class::isInstance).map(MonsterActionEntry.class::cast)
                .filter(entry -> entry.identity().rule().equals(monster)).toList();
    }
    static List<MonsterActionEntry> opportunityAttacks(Map<UUID, Context> roster, MonsterInstance monster) {
        var material = material(roster);
        List<MonsterActionEntry> melee = new ArrayList<>();
        var block = definition(material, monster.monster()).statBlock();
        for (MonsterActionEntry entry : actions(material, monster.monster())) {
            switch (entry.actionProfile().mode()) {
                case MELEE_ATTACK_V1 -> {
                    validateMeleeAction(entry.actionProfile(), block);
                    melee.add(entry);
                }
                case SAVE_DAMAGE_BATCH_V1 -> validateAction(entry.actionProfile());
                case BLOCKED_METADATA -> throw invalid("S2_MONSTER_REACTION_UNSUPPORTED");
            }
        }
        if (melee.isEmpty()) throw invalid("S2_MONSTER_REACTION_UNSUPPORTED");
        return melee;
    }
    static List<UUID> packTacticsAllies(Map<UUID, Context> roster, Battle battle, UUID source, UUID target) {
        MonsterInstance monster = instance(battle, source);
        var block = definition(roster, monster.monster()).statBlock();
        if (block.traits().stream().noneMatch(trait -> trait.scope() == MonsterTraitScope.ATTACK_ROLL_PACK_TACTICS_ONLY))
            return List.of();
        Combatant attacker = Schema2CombatRules.combatant(battle, source);
        Position position = Schema2CombatRules.combatant(battle, target).position();
        return battle.initiative().stream().map(Initiative::characterId).filter(id -> !source.equals(id))
                .map(id -> Schema2CombatRules.combatant(battle, id))
                .filter(ally -> ally.side() == attacker.side() && ally.position().distanceFeet(position) <= 5
                        && living(roster, battle, ally.characterId()))
                .map(Combatant::characterId).toList();
    }
    static boolean living(Map<UUID, Context> roster, Battle battle, UUID id) {
        Context context = roster.get(id);
        return context == null ? !instance(battle, id).dead() : context.character().hitPoints() > 0 && !context.character().health().dead();
    }
    private static MonsterActionProfile validateAction(MonsterActionProfile p) {
        if(p.mode()!=MonsterActionMode.SAVE_DAMAGE_BATCH_V1||!Objects.equals(p.mechanism(),"HELL_HOUND_FIRE_BREATH_V1")
                ||!new MonsterSavePolicy("ability.dexterity","FIXED_DC",12,"FULL","HALF").equals(p.savePolicy())
                ||!new MonsterDamageProfile(6,6,DamageType.FIRE,"OTHER_DELIVERY","NO_ABILITY_MODIFIER").equals(p.damageProfile())
                ||!new MonsterArea("CONE",15,"EXCLUDE_ORIGIN").equals(p.area())
                ||!new MonsterRecharge(6,5,true,true,true).equals(p.recharge()))throw invalid("S2_MONSTER_ACTION_UNSUPPORTED");
        return p;
    }
    static void validateFrozenSupport(ProductionSchema2Projection material) {
        for (FinalRule row : material.projection().rules()) if (row instanceof FinalMonster monster && monster.enabled()) {
            validateScope(monster.definition().statBlock());
            initialActions(material, monster.identity());
            for (MonsterActionEntry entry : actions(material, monster.identity()))
                if (entry.actionProfile().mode() == MonsterActionMode.MELEE_ATTACK_V1)
                    validateMeleeAction(entry.actionProfile(), monster.definition().statBlock());
        }
    }
    static MonsterInstance initial(Map<UUID,Context> roster,MonsterDeployment deployment) {
        return initial(material(roster), deployment);
    }
    static MonsterInstance initial(ProductionSchema2Projection material,MonsterDeployment deployment) {
        var d=definition(material,deployment.monster());
        return new MonsterInstance(deployment.monsterId(), deployment.monster(), d.statBlock().hitPoints(), false,
                initialActions(material, deployment.monster()));
    }
    private static List<MonsterActionUse> initialActions(ProductionSchema2Projection material, RuleIdentity monster) {
        List<MonsterActionUse> actions=new ArrayList<>();
        for(var row:material.projection().entries())if(row.enabled()&&row.entry() instanceof MonsterActionEntry entry
                &&entry.identity().rule().equals(monster)&&entry.actionProfile().mode()==MonsterActionMode.SAVE_DAMAGE_BATCH_V1) {
            validateAction(entry.actionProfile());actions.add(new MonsterActionUse(entry.identity(),true));
        }
        if(actions.size()>16)throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
        return actions;
    }
    static MonsterInstance instance(Battle battle,UUID id) {return battle.monsters().stream().filter(m->m.monsterId().equals(id)).findFirst().orElseThrow(()->invalid("S2_MONSTER_NOT_FOUND"));}
    static boolean isMonster(Battle battle,UUID id) {return battle.monsters().stream().anyMatch(m->m.monsterId().equals(id));}
    static int modifier(MonsterStatBlock block,String ability) {
        var a=block.abilities();int score=switch(ability) {case "ability.strength"->a.strength();case "ability.dexterity"->a.dexterity();case "ability.constitution"->a.constitution();case "ability.intelligence"->a.intelligence();case "ability.wisdom"->a.wisdom();case "ability.charisma"->a.charisma();default->throw invalid("S2_MONSTER_RULE_UNSUPPORTED");};
        return Math.floorDiv(score-10,2);
    }
    static Battle with(Battle b,List<MonsterInstance> monsters,List<SaveDamageBatch> batches) {
        return new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),b.combatants(),b.initiative(),b.flows(),b.ongoingEffects(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.environmentZones(),b.suppressionCauses(),monsters,batches,b.monsterRests(),b.meleeControl());
    }
    static Battle replace(Battle b,MonsterInstance monster) {return with(b,b.monsters().stream().map(m->m.monsterId().equals(monster.monsterId())?monster:m).toList(),b.saveDamageBatches());}
    static Battle damage(Battle b,UUID id,int amount) {
        var old=instance(b,id);int hp=Math.max(0,old.hitPoints()-amount);
        return replace(b,new MonsterInstance(id,old.monster(),hp,hp==0,old.actionUses()));
    }
    static int effectiveDamage(Map<UUID,Context> roster,Battle b,UUID target,DamageType type,boolean attack,int amount) {
        return Schema2SaveDamageRules.damage(amount,false,damageDefenses(roster,b,target,type,attack));
    }
    static List<DefenseContribution> damageDefenses(Map<UUID,Context> roster, Battle battle, UUID target,
            DamageType type, boolean nonmagicalAttack) {
        if (!isMonster(battle,target)) return List.of();
        var monster = instance(battle,target);
        return definition(roster,monster.monster()).statBlock().damageDefenses().stream()
                .filter(defense -> defense.damageType() == type && (defense.qualifier() == MonsterDefenseQualifier.ALL
                        || nonmagicalAttack && defense.qualifier() == MonsterDefenseQualifier.NONMAGICAL_ATTACKS))
                .map(defense -> new DefenseContribution(monster.monster(),defense.kind().name())).distinct().toList();
    }
    static void validate(Map<UUID,Context> roster,Battle b) {
        Set<UUID> ids=new HashSet<>(roster.keySet());
        for(MonsterInstance m:b.monsters()) {
            uuid(m.monsterId());var d=definition(roster,m.monster());
            if(!ids.add(m.monsterId())||m.hitPoints()<0||m.hitPoints()>d.statBlock().hitPoints()||m.dead()!=(m.hitPoints()==0))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
            var expected=initial(roster,new MonsterDeployment(m.monsterId(),m.monster(),new Position(0,0),0)).actionUses().stream().map(MonsterActionUse::action).toList();
            if(!expected.equals(m.actionUses().stream().map(MonsterActionUse::action).toList()))throw invalid("ACTION_FLOW_FROZEN_DATA_INVALID");
        }
        if(!ids.equals(b.combatants().stream().map(Combatant::characterId).collect(java.util.stream.Collectors.toSet())))throw invalid("ACTION_FLOW_TARGET_MISMATCH");
    }
}
