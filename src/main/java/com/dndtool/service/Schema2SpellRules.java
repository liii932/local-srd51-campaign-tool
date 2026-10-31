package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.service.Schema2CombatRules.Context;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CombatState.*;

/** Finite Life Domain and Wizard execution owner. Knowledge, costs and effects come from frozen and acquired facts. */
public final class Schema2SpellRules {
    public static final String CASTING_ALGORITHM = "LIFE_DOMAIN_LEVEL1_V1";
    public static final String BLESS = "SRD_2014_BLESS_V1", CURE_WOUNDS = "SRD_2014_CURE_WOUNDS_V1", SHIELD="SRD_2014_SHIELD_V1", COUNTERSPELL="SRD_2014_COUNTERSPELL_V1";
    public static final int MAX_ONGOING_EFFECTS = 128;
    public static final class Prepared {
        private final SpellcastingDefinition casting;
        private final RuleIdentity castingIdentity,slotResource;
        private final String abilityKey;
        private final SpellDefinition spell;
        private final CastInput input;
        private final State caster;
        private final int healingModifier;
        private final boolean concentration;
        private Prepared(SpellcastingDefinition casting,SpellDefinition spell,CastInput input,State caster,int healingModifier,boolean concentration) {
            this.casting=casting;this.castingIdentity=casting.identity();this.slotResource=casting.slotResource();this.abilityKey=casting.abilityKey();this.spell=spell;this.input=input;this.caster=caster;this.healingModifier=healingModifier;this.concentration=concentration;
        }
        private Prepared(RuleIdentity castingIdentity,RuleIdentity slotResource,String abilityKey,SpellDefinition spell,CastInput input,State caster,int healingModifier,boolean concentration) {
            this.casting=null;this.castingIdentity=castingIdentity;this.slotResource=slotResource;this.abilityKey=abilityKey;this.spell=spell;this.input=input;this.caster=caster;this.healingModifier=healingModifier;this.concentration=concentration;
        }
        public SpellcastingDefinition casting() {return casting;}
        public RuleIdentity castingIdentity() {return castingIdentity;}
        public RuleIdentity slotResource() {return slotResource;}
        public String abilityKey() {return abilityKey;}
        public SpellDefinition spell() {return spell;}
        public CastInput input() {return input;}
        public State caster() {return caster;}
        public int healingModifier() {return healingModifier;}
        public boolean concentration() {return concentration;}
    }

    /** Every acquired influence must have this exact published owner and real acquisition facts. */
    public static void requireSupportedFeatures(ProductionSchema2Projection material, State state, String code) {
        if (!state.feats().isEmpty()) throw invalid(code);
        if (state.features().isEmpty()) return;
        var life=material.projection().rules().stream().filter(FinalSpellcasting.class::isInstance).map(FinalSpellcasting.class::cast).filter(FinalSpellcasting::enabled).filter(p->owns(p.definition(),state)).toList();
        var wizard=material.projection().rules().stream().filter(FinalWizardSpellcasting.class::isInstance).map(FinalWizardSpellcasting.class::cast).filter(FinalWizardSpellcasting::enabled).filter(p->ownsWizard(p.definition(),state)).toList();
        if(life.size()+wizard.size()!=1)throw invalid(code);
        RuleIdentity required=life.isEmpty()?wizard.getFirst().definition().requiredFeature():life.getFirst().definition().requiredFeature();
        if(!life.isEmpty() && state.features().size()!=1)throw invalid(code);
        for(FeatureState acquired:state.features()) {
            var rows=material.projection().rules().stream().filter(FinalFeature.class::isInstance).map(FinalFeature.class::cast).filter(f->f.enabled()&&f.identity().equals(acquired.feature())).toList();
            if(rows.size()!=1)throw invalid(code);FeatureDefinition feature=rows.getFirst().definition();
            if(!feature.owner().equals(acquired.owner()) || feature.level()!=acquired.acquiredAtClassLevel())throw invalid(code);
            if(feature.identity().equals(required)) {
                if(feature.executionMode()!=FeatureExecution.AUTOMATIC || !Set.of(CASTING_ALGORITHM,"SRD_2014_WIZARD_SPELLCASTING_V1").contains(feature.executionAlgorithm()))throw invalid(code);
            } else {
                if(wizard.isEmpty())throw invalid(code);
                var profile=wizard.getFirst().definition();
                if(feature.executionAlgorithm().equals("SRD_2014_WIZARD_ARCANE_RECOVERY_V1")) {
                    if(!feature.owner().equals(profile.ownerClass()) || feature.level()!=1
                            ||!Set.of(FeatureExecution.BLOCKED,FeatureExecution.AUTOMATIC).contains(feature.executionMode()))throw invalid(code);
                } else if(Set.of("SRD_2014_EVOCATION_SAVANT_V1","SRD_2014_SCULPT_SPELLS_V1").contains(feature.executionAlgorithm())) {
                    var subclasses=material.projection().rules().stream().filter(FinalSubclass.class::isInstance).map(FinalSubclass.class::cast).filter(r->r.enabled()&&r.identity().equals(feature.owner())&&r.definition().ownerClass().equals(profile.ownerClass())&&r.definition().selectionLevel()==2).toList();
                    if(feature.executionMode()!=FeatureExecution.BLOCKED || feature.level()!=2 || subclasses.size()!=1 || state.subclasses().stream().noneMatch(r->r.characterClass().equals(profile.ownerClass())&&r.subclass().equals(feature.owner())&&r.acquiredAtClassLevel()==2))throw invalid(code);
                } else throw invalid(code);
                // Recovery and spellbook copying do not run during this scene. Sculpt Spells only modifies evocation saving throws;
                // the finite wizard executable set consists exclusively of abjuration Shield and Counterspell.
            }
        }
    }
    private static boolean ownsWizard(WizardSpellcastingDefinition p,State state) {
        return "SRD_2014_WIZARD_PREPARED_V1".equals(p.algorithm()) && "ability.intelligence".equals(p.abilityKey())
                && state.level()>=1 && state.level()<=5 && state.classes().size()==1 && state.classes().getFirst().level()==state.level()
                && state.classes().getFirst().characterClass().equals(p.ownerClass())
                && state.features().stream().anyMatch(f->f.feature().equals(p.requiredFeature())&&f.owner().equals(p.ownerClass())&&f.acquiredAtClassLevel()==1);
    }
    private static boolean owns(SpellcastingDefinition p, State state) {
        return CASTING_ALGORITHM.equals(p.algorithm()) && "ability.wisdom".equals(p.abilityKey())
                && state.level()==1 && state.classes().size()==1 && state.classes().getFirst().level()==1
                && state.classes().getFirst().characterClass().equals(p.ownerClass())
                && state.subclasses().size()==1 && state.subclasses().getFirst().characterClass().equals(p.ownerClass())
                && state.subclasses().getFirst().subclass().equals(p.requiredSubclass())
                && state.subclasses().getFirst().acquiredAtClassLevel()==1
                && state.features().stream().anyMatch(f -> f.feature().equals(p.requiredFeature())
                    && f.owner().equals(p.requiredSubclass()) && f.acquiredAtClassLevel()==1);
    }

    public Prepared prepare(Map<UUID,Context> roster, Battle battle, UUID actor, CastInput input) {
        return prepare(roster,battle,actor,input,false);
    }
    public Prepared prepare(Map<UUID,Context> roster, Battle battle, UUID actor, CastInput input, boolean reaction) {
        Context context = roster.get(actor);
        if (context==null || input==null) throw invalid("S2_SPELL_COMMAND");
        State caster=context.character(); requireSupportedFeatures(context.material(),caster,"S2_SPELL_EFFECT_UNSUPPORTED");
        Schema2OngoingEffectRules.requireCastingAllowed(roster,battle,actor);
        if(input.castingRule().ruleType().equals("character.wizard_spellcasting"))return prepareWizard(roster,battle,actor,input,reaction);
        if(reaction || input.slotLevel()!=1 || input.targetCastFrameKey()!=null)throw invalid("S2_SPELL_UNAVAILABLE");
        var profiles=context.material().projection().rules().stream().filter(FinalSpellcasting.class::isInstance)
                .map(FinalSpellcasting.class::cast).filter(p -> p.enabled() && p.identity().equals(input.castingRule())).toList();
        if (profiles.size()!=1 || !owns(profiles.getFirst().definition(),caster)) throw invalid("S2_SPELL_CASTER_UNSUPPORTED");
        SpellcastingDefinition profile=profiles.getFirst().definition();
        var spells=context.material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast)
                .filter(s -> s.enabled() && s.identity().equals(input.spell())).toList();
        if (spells.size()!=1) throw invalid("S2_SPELL_UNAVAILABLE");
        SpellDefinition spell=spells.getFirst().definition();
        boolean bless=profile.bless().equals(input.spell()) && BLESS.equals(spell.algorithm());
        boolean cure=profile.cureWounds().equals(input.spell()) && CURE_WOUNDS.equals(spell.algorithm());
        if (!bless && !cure) throw invalid("S2_SPELL_UNAVAILABLE");
        var resources=caster.resources().stream().filter(r -> r.identity().equals(profile.slotResource())).toList();
        if (resources.size()!=1 || resources.getFirst().unlimited() || resources.getFirst().maximum()!=2 || resources.getFirst().current()<1)
            throw invalid("S2_SPELL_SLOT_UNAVAILABLE");
        if (caster.hitPoints()==0 || caster.health().dead()) throw invalid("S2_COMBAT_ACTOR_INACTIVE");
        if (new Schema2CharacterEquipmentRules().derive(context.material(),caster).castingForbidden()) throw invalid("S2_SPELL_ARMOR");
        boolean free=caster.equipment().stream().noneMatch(i -> i.location().equals("BOTH_HANDS"))
                && (caster.equipment().stream().noneMatch(i -> i.location().equals("MAIN_HAND"))
                    || caster.equipment().stream().noneMatch(i -> i.location().equals("OFF_HAND")));
        if (bless) {
            if (input.materialItemId()==null || caster.equipment().stream().noneMatch(i -> i.itemId().equals(input.materialItemId())
                    && i.item().equals(profile.materialItem()) && Set.of("MAIN_HAND","OFF_HAND").contains(i.location())))
                throw invalid("S2_SPELL_COMPONENT");
            if (battle.ongoingEffects().size()>=MAX_ONGOING_EFFECTS) throw invalid("ACTION_FLOW_LIMIT_EXCEEDED");
            if (second(caster)>Schema2CharacterRestRules.MAX_MINUTE*60-(battle.status()==Status.EXPLORATION?66:60)) throw invalid("S2_SPELL_TIME_CAPACITY");
        } else if (!free || input.materialItemId()!=null) throw invalid("S2_SPELL_COMPONENT");
        if (input.targets().isEmpty() || input.targets().size()>(bless?3:1) || new HashSet<>(input.targets()).size()!=input.targets().size())
            throw invalid("S2_SPELL_TARGET");
        Combatant origin=member(battle,actor);
        for (UUID id:input.targets()) {
            Context destination=roster.get(id);
            if (destination==null || origin.position().distanceFeet(member(battle,id).position())>(bless?30:5)) throw invalid("S2_SPELL_TARGET");
            if (cure && destination.character().health().dead()) throw invalid("S2_HEALTH_DEAD");
            if (cure) {
                requireSupportedFeatures(destination.material(),destination.character(),"S2_HEALTH_EFFECT_UNSUPPORTED");
                if (destination.character().version()>=JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS-(id.equals(actor)?2:1))
                    throw invalid("ACTION_FLOW_RESERVATION_INSUFFICIENT");
            }
        }
        if (caster.version()>=JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS-1) throw invalid("ACTION_FLOW_RESERVATION_INSUFFICIENT");
        return new Prepared(profile,spell,input,caster,Math.floorDiv(caster.abilities().get(profile.abilityKey())-10,2)+3,bless);
    }
    private Prepared prepareWizard(Map<UUID,Context> roster,Battle battle,UUID actor,CastInput input,boolean reaction) {
        Context context=roster.get(actor);State caster=context.character();new Schema2WizardSpellbookRules().validateState(context.material(),caster);
        var profiles=context.material().projection().rules().stream().filter(FinalWizardSpellcasting.class::isInstance).map(FinalWizardSpellcasting.class::cast).filter(p->p.enabled()&&p.identity().equals(input.castingRule())&&ownsWizard(p.definition(),caster)).toList();
        if(profiles.size()!=1)throw invalid("S2_SPELL_CASTER_UNSUPPORTED");
        var profile=profiles.getFirst().definition();
        var spells=context.material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).filter(s->s.enabled()&&s.identity().equals(input.spell())).toList();
        if(spells.size()!=1 || !Set.of(SHIELD,COUNTERSPELL).contains(spells.getFirst().definition().algorithm()) || !reaction)throw invalid("S2_SPELL_UNAVAILABLE");
        var book=caster.spellbook();
        if(book==null || !book.castingRule().equals(input.castingRule()) || book.learnedThroughLevel()!=caster.level()
                || book.learned().stream().noneMatch(s->s.spell().equals(input.spell())) || !book.prepared().contains(input.spell()))throw invalid("S2_SPELL_UNAVAILABLE");
        boolean shield=SHIELD.equals(spells.getFirst().definition().algorithm());
        if(input.slotLevel()!=(shield?1:3) || !shield&&caster.level()<5)throw invalid("S2_SPELL_SLOT_UNAVAILABLE");
        RuleIdentity resource=shield?profile.slotResource():profile.rank3SlotResource();
        var resources=caster.resources().stream().filter(r->r.identity().equals(resource)).toList();
        if(resources.size()!=1 || resources.getFirst().unlimited() || resources.getFirst().current()<1)throw invalid("S2_SPELL_SLOT_UNAVAILABLE");
        if(caster.hitPoints()==0 || caster.health().dead())throw invalid("S2_COMBAT_ACTOR_INACTIVE");
        if(new Schema2CharacterEquipmentRules().derive(context.material(),caster).castingForbidden())throw invalid("S2_SPELL_ARMOR");
        boolean free=caster.equipment().stream().noneMatch(i->i.location().equals("BOTH_HANDS")) && (caster.equipment().stream().noneMatch(i->i.location().equals("MAIN_HAND"))||caster.equipment().stream().noneMatch(i->i.location().equals("OFF_HAND")));
        if(!free || input.materialItemId()!=null)throw invalid("S2_SPELL_COMPONENT");
        if(shield) {
            if(!input.targets().equals(List.of(actor)) || input.targetCastFrameKey()!=null)throw invalid("S2_SPELL_TARGET");
        } else {
            CastAttempt target=findCast(battle,input.targetCastFrameKey());
            if(target==null || target.status()!=CastStatus.PENDING || target.actorId().equals(actor) || !input.targets().equals(List.of(target.actorId())) || member(battle,actor).position().distanceFeet(member(battle,target.actorId()).position())>60)throw invalid("S2_SPELL_TARGET");
        }
        if(caster.version()>=JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS-1)throw invalid("ACTION_FLOW_RESERVATION_INSUFFICIENT");
        return new Prepared(input.castingRule(),resource,profile.abilityKey(),spells.getFirst().definition(),input,caster,0,false);
    }
    public List<ReactionOption> reactionOptions(Map<UUID,Context> roster,Battle battle,UUID actor,ReactionKind kind,UUID targetCast) {
        Context c=roster.get(actor);List<ReactionOption> result=new ArrayList<>();
        for(var rule:c.material().projection().rules())if(rule instanceof FinalWizardSpellcasting p && p.enabled() && ownsWizard(p.definition(),c.character())) {
            for(var candidate:c.material().projection().rules())if(candidate instanceof FinalSpell s && s.enabled() && (kind==ReactionKind.SHIELD?SHIELD:COUNTERSPELL).equals(s.definition().algorithm())) {
                CastAttempt target=targetCast==null?null:findCast(battle,targetCast);
                var input=new CastInput(p.identity(),s.identity(),List.of(target==null?actor:target.actorId()),null,kind==ReactionKind.SHIELD?1:3,targetCast);
                try {prepare(roster,battle,actor,input,true);result.add(new ReactionOption(kind,null,p.identity(),s.identity(),input.slotLevel(),null));}
                catch(IllegalArgumentException unavailable) { if(!Set.of("S2_SPELL_UNAVAILABLE","S2_SPELL_SLOT_UNAVAILABLE","S2_COMBAT_ACTOR_INACTIVE","S2_SPELL_ARMOR","S2_SPELL_COMPONENT","S2_SPELL_TARGET","S2_SPELL_ANTIMAGIC_ZONE").contains(unavailable.getMessage()))throw unavailable; }
            }
        }
        return List.copyOf(result);
    }
    public static CastAttempt findCast(Battle battle,UUID frame) {
        if(frame==null)return null;
        return battle.flows().stream().flatMap(f->f.casts().stream()).filter(c->c.frameKey().equals(frame)).findFirst().orElse(null);
    }
    public static int spellLevel(Context context,RuleIdentity spell) {
        var s=context.material().projection().rules().stream().filter(FinalSpell.class::isInstance).map(FinalSpell.class::cast).filter(r->r.enabled()&&r.identity().equals(spell)).toList();
        if(s.size()!=1)throw invalid("S2_SPELL_UNAVAILABLE");
        return Schema2CompositionKernel.spellLevel(s.getFirst().definition().algorithm());
    }
    public State spend(Prepared p) {
        List<ResourceState> resources=p.caster().resources().stream().map(r -> r.identity().equals(p.slotResource())
                ?new ResourceState(r.identity(),r.current()-1,r.maximum(),r.unlimited()):r).toList();
        return copy(p.caster(),p.caster().hitPoints(),p.caster().health(),p.caster().version()+1,resources);
    }
    public static State copy(State s,long hp,HealthState health,long version,List<ResourceState> resources) {
        return new State(s.identity(),s.campaignId(),s.characterId(),s.preparationOperationId(),s.characterClass(),s.abilities(),s.level(),
                s.proficiencyBonus(),hp,s.maximumHitPoints(),s.classes(),s.hitDicePools(),s.subclasses(),s.features(),s.feats(),s.proficiencies(),
                version,resources,s.rest(),s.equipment(),health,s.spellbook());
    }
    public static long second(State state) { return state.rest().minute()*60+state.rest().second(); }
    public static OngoingEffect blessing(Battle battle, UUID target) {
        // This execution slice accepts one complete Bless identity. Append order is the source-creation event order;
        // tied potency selects the earliest source without deleting or restarting SHADOWED instances.
        return Schema2OngoingEffectRules.blessing(battle,target);
    }
    public static Battle end(Battle b, UUID caster,String reason,List<CombatFact> facts) {
        ConcentrationHolder holder=b.concentrationHolders().stream().filter(h->h.actorId().equals(caster)).findFirst().orElse(null);
        if(holder==null)return b;
        List<OngoingEffect> effects=new ArrayList<>();
        for(OngoingEffect e:b.ongoingEffects()) {
            if(holder.kind()==ConcentrationKind.BLESS_EFFECT && e.effectKey().equals(holder.sourceKey()) && e.status()==EffectStatus.ACTIVE) {
                e=new OngoingEffect(e.effectKey(),e.spell(),e.casterId(),e.targets(),e.startedAtSecond(),e.expiresAtSecond(),EffectStatus.ENDED,reason);
                facts.add(new CombatFact("CONCENTRATION_ENDED",caster,e.effectKey(),reason));
            }
            effects.add(e);
        }
        List<ReadyPreparation> ready=b.readyPreparations().stream().map(r->holder.kind()==ConcentrationKind.READY_SPELL && r.readyKey().equals(holder.sourceKey()) && Set.of(ReadyStatus.PREPARING,ReadyStatus.ARMED).contains(r.status())?new ReadyPreparation(r.readyKey(),r.actorId(),r.declaredTurnNumber(),r.expiresAtTurnNumber(),r.input(),ReadyStatus.LOST,r.heldCastFrameKey(),reason):r).toList();
        return new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),b.combatants(),b.initiative(),b.flows(),effects,ready,b.triggerFacts(),b.shields(),b.concentrationHolders().stream().filter(h->!h.actorId().equals(caster)).toList(),b.environmentZones(),b.suppressionCauses(),b.monsters(),b.saveDamageBatches(),b.monsterRests());
    }
    public static Battle withEffects(Battle b,List<OngoingEffect> effects) {
        return new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),b.combatants(),b.initiative(),b.flows(),effects,b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),b.environmentZones(),b.suppressionCauses(),b.monsters(),b.saveDamageBatches(),b.monsterRests());
    }
    private static Combatant member(Battle b,UUID id) {return b.combatants().stream().filter(c -> c.characterId().equals(id)).findFirst().orElseThrow(() -> invalid("S2_SPELL_TARGET"));}
    private static IllegalArgumentException invalid(String code) {return new IllegalArgumentException(code);}
}
