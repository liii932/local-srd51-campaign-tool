package com.dndtool.service;

import com.dndtool.service.Schema2CombatRules.Context;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CombatState.*;

/** Shared finite saving-throw basis: actual grants, current equipment and the current effect winner. */
final class Schema2SavingThrowRules {
    record Basis(String ability, int modifier, int proficiency, boolean advantage, boolean disadvantage,
            boolean autoFailure, UUID blessingEffectKey) { }
    private Schema2SavingThrowRules() { }
    static int proficiency(Context context,String ability) {
        Set<RuleIdentity> acquired=new HashSet<>();context.character().proficiencies().forEach(p->acquired.add(p.target()));
        return context.material().projection().rules().stream().filter(FinalProficiency.class::isInstance)
                .map(FinalProficiency.class::cast).anyMatch(p->p.enabled()&&p.definition().category()==ProficiencyCategory.SAVING_THROW
                    &&ability.equals(p.definition().abilityKey())&&acquired.contains(p.identity()))?context.character().proficiencyBonus():0;
    }
    static Basis prepare(Map<UUID,Context> roster,Battle battle,CreatureRef subject,String ability) {
        var actor=battle.combatants().stream().filter(a->a.characterId().equals(subject.creatureId())).findFirst().orElseThrow(()->invalid("ACTION_FLOW_TARGET_MISMATCH"));
        boolean dex=ability.equals("ability.dexterity");
        if(subject.kind()==CreatureKind.MONSTER) {
            var m=Schema2MonsterRules.instance(battle,subject.creatureId());var block=Schema2MonsterRules.definition(roster,m.monster()).statBlock();
            int mod=Schema2MonsterRules.modifier(block,ability);var bonuses=block.savingThrows().stream().filter(s->s.abilityKey().equals(ability)).toList();
            if(bonuses.size()>1)throw invalid("S2_MONSTER_RULE_UNSUPPORTED");
            return new Basis(ability,mod,bonuses.isEmpty()?0:bonuses.getFirst().bonus()-mod,dex&&actor.dodging()&&m.hitPoints()>0&&block.walkSpeedFeet()>0,false,dex&&m.hitPoints()==0,null);
        }
        var c=roster.get(subject.creatureId());if(c==null)throw invalid("ACTION_FLOW_TARGET_MISMATCH");
        Schema2SpellRules.requireSupportedFeatures(c.material(),c.character(),"S2_CHECK_EFFECT_UNSUPPORTED");
        int mod=Math.floorDiv(c.character().abilities().get(ability)-10,2);var derived=new Schema2CharacterEquipmentRules().derive(c.material(),c.character());
        var bless=Schema2SpellRules.blessing(battle,subject.creatureId());
        return new Basis(ability,mod,proficiency(c,ability),dex&&actor.dodging()&&c.character().hitPoints()>0&&new Schema2CombatRules().speed(c)>0,
                (dex||ability.equals("ability.strength"))&&derived.physicalCheckDisadvantage(),dex&&c.character().hitPoints()==0,bless==null?null:bless.effectKey());
    }
    static BatchCheck resolve(Basis basis,int ordinal,String purpose,UUID subject,int dc,Schema2ActionFlowRules.Roller dice) {
        if(basis.autoFailure())return new BatchCheck(ordinal,purpose,basis.ability(),CheckMode.AUTO_FAILURE,null,null,basis.modifier(),basis.proficiency(),basis.advantage(),basis.disadvantage(),null,null,dc,0,false);
        int first=dice.draw(purpose+"_D20",subject,20);Integer second=basis.advantage()!=basis.disadvantage()?dice.draw(purpose+"_SECOND_D20",subject,20):null;
        int selected=second==null?first:basis.advantage()?Math.max(first,second):Math.min(first,second);
        Integer d4=basis.blessingEffectKey()==null?null:dice.draw(purpose+"_BLESS_D4",subject,4);
        int total=selected+basis.modifier()+basis.proficiency()+(d4==null?0:d4);
        return new BatchCheck(ordinal,purpose,basis.ability(),CheckMode.ROLL,first,second,basis.modifier(),basis.proficiency(),basis.advantage(),basis.disadvantage(),basis.blessingEffectKey(),d4,dc,total,total>=dc);
    }
}
