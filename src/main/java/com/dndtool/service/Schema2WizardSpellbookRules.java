package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Sole owner of the finite Wizard's acquired spellbook and prepared-list facts. */
public final class Schema2WizardSpellbookRules {
    private static final Comparator<RuleIdentity> ORDER=Comparator.comparing(RuleIdentity::moduleKey).thenComparing(RuleIdentity::ruleType).thenComparing(RuleIdentity::ruleKey);
    public static boolean handles(Command command) { return JdbcSchema2CharacterRuntimeRepository.spellbookKind(command.kind()); }
    public State apply(ProductionSchema2Projection material,State before,Command command) {
        if(before==null || !handles(command) || before.rest().active()!=null || before.hitPoints()==0 || before.health().dead())throw invalid("S2_SPELLBOOK_COMMAND");
        validateState(material,before);
        SpellbookInput input=command.spellbookInput(); WizardSpellcastingDefinition profile=profile(material,before,input.castingRule());
        int level=before.level(); SpellbookState old=before.spellbook(); var learned=new ArrayList<LearnedSpell>();
        Long restMinute=before.rest().lastLongRestEndMinute(); Integer restSecond=before.rest().lastLongRestEndSecond();
        Long marker=old==null?null:old.preparedAtRestMinute(); Integer markerSecond=old==null?null:old.preparedAtRestSecond();
        List<RuleIdentity> prepared=input.prepared(); long preparationMinutes=0;
        switch(command.kind()) {
            case "SPELLBOOK_INITIALIZED" -> {
                if(old!=null || level!=1 || input.learned().size()!=6)throw invalid("S2_SPELLBOOK_LEARNING");
                marker=restMinute; markerSecond=restSecond;
            }
            case "SPELLBOOK_ADVANCED" -> {
                if(old==null || old.learnedThroughLevel()+1!=level || input.learned().size()!=2 || !input.prepared().equals(old.prepared()))throw invalid("S2_SPELLBOOK_LEARNING");
                learned.addAll(old.learned());
            }
            case "SPELLBOOK_PREPARED" -> {
                if(old==null || old.learnedThroughLevel()!=level || !input.learned().isEmpty() || restMinute==null
                        || Objects.equals(restMinute,marker)&&Objects.equals(restSecond,markerSecond))throw invalid("S2_SPELLBOOK_REST_REQUIRED");
                learned.addAll(old.learned()); marker=restMinute; markerSecond=restSecond;
                for(RuleIdentity spell:prepared)preparationMinutes+=spellLevel(definition(material,spell).algorithm());
            }
            default -> throw invalid("S2_SPELLBOOK_COMMAND");
        }
        for(RuleIdentity spell:input.learned()) {
            requireLearnable(material,profile,spell,level);
            if(learned.stream().anyMatch(k->k.spell().equals(spell)))throw invalid("S2_SPELLBOOK_LEARNING");
            learned.add(new LearnedSpell(spell,level));
        }
        learned.sort(Comparator.comparingInt(LearnedSpell::acquiredAtClassLevel).thenComparing(LearnedSpell::spell,ORDER));
        SpellbookState book=new SpellbookState(input.castingRule(),level,learned,prepared,marker,markerSecond);
        State after=JdbcSchema2CharacterRuntimeRepository.withSpellbook(before,before.version()+1,book);
        if(preparationMinutes>0) {
            RestState r=after.rest(); if(r.minute()>Schema2CharacterRestRules.MAX_MINUTE-preparationMinutes)throw invalid("S2_CHARACTER_TIME");
            after=new State(after.identity(),after.campaignId(),after.characterId(),after.preparationOperationId(),after.characterClass(),after.abilities(),after.level(),after.proficiencyBonus(),after.hitPoints(),after.maximumHitPoints(),after.classes(),after.hitDicePools(),after.subclasses(),after.features(),after.feats(),after.proficiencies(),after.version(),after.resources(),new RestState(r.minute()+preparationMinutes,r.second(),r.lastLongRestEndMinute(),r.lastLongRestEndSecond(),null),after.equipment(),after.health(),book);
        }
        validateState(material,after);return after;
    }
    public void requireLearningCompleteBeforeAdvance(ProductionSchema2Projection material,State state) {
        boolean wizard=material.projection().rules().stream().filter(FinalWizardSpellcasting.class::isInstance).map(FinalWizardSpellcasting.class::cast)
                .anyMatch(p->p.enabled()&&p.definition().ownerClass().equals(state.characterClass()));
        if(wizard && (state.spellbook()==null || state.spellbook().learnedThroughLevel()!=state.level()))throw invalid("S2_SPELLBOOK_LEARNING_REQUIRED");
        if(wizard && state.level()>=5)throw invalid("S2_SPELL_CASTER_UNSUPPORTED");
    }
    public void validateState(ProductionSchema2Projection material,State state) {
        SpellbookState book=state.spellbook(); if(book==null)return;
        WizardSpellcastingDefinition p=profile(material,state,book.castingRule());
        if(book.learnedThroughLevel()>state.level() || book.learnedThroughLevel()<state.level()-1)throw invalid("S2_SPELLBOOK_STATE");
        for(int level=1;level<=book.learnedThroughLevel();level++) {
            final int n=level;
            if(book.learned().stream().filter(k->k.acquiredAtClassLevel()==n).count()!=(level==1?6:2))throw invalid("S2_SPELLBOOK_STATE");
        }
        for(LearnedSpell k:book.learned())requireLearnable(material,p,k.spell(),k.acquiredAtClassLevel());
        if(!book.learned().equals(book.learned().stream().sorted(Comparator.comparingInt(LearnedSpell::acquiredAtClassLevel).thenComparing(LearnedSpell::spell,ORDER)).toList())
                || !book.prepared().equals(book.prepared().stream().sorted(ORDER).toList()) || book.prepared().isEmpty()
                || book.prepared().size()>Math.max(1,Math.floorDiv(state.abilities().get(p.abilityKey())-10,2)+state.level()))throw invalid("S2_SPELLBOOK_PREPARED");
        Set<RuleIdentity> known=new HashSet<>(book.learned().stream().map(LearnedSpell::spell).toList());
        if(!known.containsAll(book.prepared()))throw invalid("S2_SPELLBOOK_PREPARED");
        if(book.preparedAtRestMinute()!=null && (state.rest().lastLongRestEndMinute()==null
                || book.preparedAtRestMinute()*60+book.preparedAtRestSecond()>state.rest().lastLongRestEndMinute()*60+state.rest().lastLongRestEndSecond()))throw invalid("S2_SPELLBOOK_STATE");
    }
    private static WizardSpellcastingDefinition profile(ProductionSchema2Projection material,State state,RuleIdentity identity) {
        var matches=material.projection().rules().stream().filter(FinalWizardSpellcasting.class::isInstance).map(FinalWizardSpellcasting.class::cast).filter(p->p.enabled()&&p.identity().equals(identity)).toList();
        if(matches.size()!=1)throw invalid("S2_SPELL_CASTER_UNSUPPORTED");
        var p=matches.getFirst().definition();
        if(state.level()<1 || state.level()>5 || state.classes().size()!=1 || state.classes().getFirst().level()!=state.level()
                || !state.characterClass().equals(p.ownerClass()) || !state.classes().getFirst().characterClass().equals(p.ownerClass())
                || state.features().stream().noneMatch(f->f.feature().equals(p.requiredFeature())&&f.owner().equals(p.ownerClass())&&f.acquiredAtClassLevel()==1))throw invalid("S2_SPELL_CASTER_UNSUPPORTED");
        return p;
    }
    private static void requireLearnable(ProductionSchema2Projection material,WizardSpellcastingDefinition p,RuleIdentity spell,int level) {
        boolean listed=material.projection().entries().stream().anyMatch(e->e.enabled()&&e.entry() instanceof SpellbookEntry k&&k.identity().rule().equals(p.identity())&&k.spell().equals(spell));
        var d=definition(material,spell); int rank=spellLevel(d.algorithm());
        if(!listed || !wizardSpell(d.algorithm()) || rank<1 || rank>(level+1)/2)throw invalid("S2_SPELLBOOK_LEARNING");
    }
    private static SpellDefinition definition(ProductionSchema2Projection material,RuleIdentity identity) {
        var spells=material.projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).filter(s->s.enabled()&&s.identity().equals(identity)).toList();
        if(spells.size()!=1)throw invalid("S2_SPELL_UNAVAILABLE"); return spells.getFirst().definition();
    }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
