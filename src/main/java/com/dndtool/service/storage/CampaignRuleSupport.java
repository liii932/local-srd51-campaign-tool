package com.dndtool.service.storage;

import com.dndtool.module.AdvancementValueProfile;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2ArcaneRecoveryRules;
import com.dndtool.service.Schema2CharacterEquipmentRules;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CombatRules;
import com.dndtool.service.Schema2FeatureSupport;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.Schema2SpellRules;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Rule-only support for the local application's current creation and execution entry points. */
public final class CampaignRuleSupport {
    private static final UUID CAMPAIGN = fixtureId(1), CHARACTER = fixtureId(2),
            PREPARATION = fixtureId(3), OPERATION = fixtureId(4);
    private static final Schema2PreparationIdentity IDENTITY = new Schema2PreparationIdentity(fixtureId(5), fixtureId(6));
    private static final Set<String> WIZARD_CASTS = Set.of(Schema2SpellRules.SHIELD, Schema2SpellRules.COUNTERSPELL,
            Schema2SpellRules.BURNING_HANDS);

    private CampaignRuleSupport() { }

    /** The projection must come from strict composition or frozen-material restoration. */
    public static CampaignRuleContext validate(ProductionSchema2Projection projection) {
        if (projection.canonicalByteLength() > ProductionSchema2Projection.MAX_RUNTIME_CANONICAL_BYTES)
            throw unsupported("BUDGET");
        var rules = projection.projection().rules();
        var check = required(rules, FinalCheck.class, "CHECK");
        required(rules, FinalHealth.class, "HEALTH");
        required(rules, FinalRest.class, "REST");
        CampaignRuleContext context;
        try {
            context = new CampaignRuleContext(projection, check.definition());
            Schema2CombatRules.validateFrozenSupport(projection);
            for (var ancestry : context.creationAncestries())
                Schema2CharacterEquipmentRules.validateWalkingSpeed(projection, ancestry.race(), ancestry.subrace());
        } catch (IllegalArgumentException invalid) {
            throw unsupported("COMBAT", invalid);
        }
        var fighter = context.rule("character.class", "class.fighter");
        var cleric = context.rule("character.class", "class.cleric");
        var wizard = context.rule("character.class", "class.wizard");
        var bounds = new HashMap<>(Map.of(fighter, 6, cleric, 1, wizard, 5));
        RuleIdentity rogue = context.initialClasses().contains("rogue") ? context.rule("character.class", "class.rogue") : null;
        if (rogue != null) bounds.put(rogue, 2);
        var progressions = validateGrowth(rules, bounds);
        var life = required(rules, FinalSpellcasting.class, "LIFE_CASTING").definition();
        var prepared = required(rules, FinalWizardSpellcasting.class, "WIZARD_CASTING").definition();
        var lifeSubclass = context.rule("character.subclass", "subclass.life");
        if (!life.ownerClass().equals(cleric) || !life.requiredSubclass().equals(lifeSubclass)
                || !prepared.ownerClass().equals(wizard)) throw unsupported("CASTING_OWNER");
        if (enabled(rules, FinalSubclass.class).stream().anyMatch(row -> row.definition().ownerClass().equals(cleric)
                && !row.identity().equals(lifeSubclass))) throw unsupported("LIFE_SUBCLASS");
        validateFeatures(rules, progressions.get(fighter), rogue == null ? null : progressions.get(rogue), life, prepared);
        int wizardMaximum = progressions.get(wizard).supportedMaximumLevel();
        validateCantrips(projection, cleric, wizard, wizardMaximum);
        validateSpells(projection, life, prepared, wizardMaximum);
        validateCreation(context);
        return context;
    }

    private static Map<RuleIdentity, ClassProgressionDefinition> validateGrowth(List<FinalRule> rules,
            Map<RuleIdentity, Integer> templateBounds) {
        var progressions = enabled(rules, FinalClassProgression.class).stream().map(FinalClassProgression::definition)
                .collect(Collectors.toUnmodifiableMap(ClassProgressionDefinition::ownerClass, row -> row));
        if (!progressions.keySet().equals(templateBounds.keySet())) throw unsupported("GROWTH_OWNERS");
        for (var progression : progressions.values())
            if (progression.advancementScope() != ProgressionScope.CHARACTER_ADVANCEMENT
                    || progression.supportedMaximumLevel() > templateBounds.get(progression.ownerClass()))
                throw unsupported("GROWTH_RANGE");
        for (var row : enabled(rules, FinalSubclass.class)) {
            var definition = row.definition();
            var progression = progressions.get(definition.ownerClass());
            if (progression == null || definition.selectionLevel() > progression.supportedMaximumLevel())
                throw unsupported("SUBCLASS_RANGE");
        }
        int supportedResources = 0;
        for (var row : enabled(rules, FinalResource.class)) {
            var definition = row.definition();
            var progression = progressions.get(definition.ownerClass());
            if (progression == null) throw unsupported("RESOURCE_OWNER");
            var capacity = AdvancementValueProfile.parse(definition.maximumProfile());
            boolean reachable = IntStream.rangeClosed(1, progression.supportedMaximumLevel())
                    .mapToObj(level -> capacity.atLevel(level, 5))
                    .anyMatch(value -> value.unlimited() || value.maximum() > 0);
            if (!reachable) continue;
            if (definition.executionMode() != ResourceExecution.AUTOMATIC) throw unsupported("RESOURCE_EXECUTION");
            if (++supportedResources > Schema2CharacterRuntime.MAX_RESOURCES) throw unsupported("RESOURCE_BUDGET");
        }
        return progressions;
    }

    private static void validateFeatures(List<FinalRule> rules, ClassProgressionDefinition fighter,
            ClassProgressionDefinition rogue, SpellcastingDefinition life, WizardSpellcastingDefinition wizard) {
        int arcaneRecovery = 0;
        Set<String> rogueFeatures = new HashSet<>();
        for (var row : enabled(rules, FinalFeature.class)) {
            var definition = row.definition();
            if (definition.kind() == FeatureKind.OPTION && definition.executionMode() != FeatureExecution.AUTOMATIC)
                continue;
            if (row.identity().equals(life.requiredFeature()) || row.identity().equals(wizard.requiredFeature()))
                continue;
            if (ROGUE_EXPERTISE_ALGORITHM.equals(definition.executionAlgorithm())
                    || ROGUE_CUNNING_ACTION_ALGORITHM.equals(definition.executionAlgorithm())) {
                if (rogue == null || !definition.owner().equals(rogue.ownerClass())
                        || definition.level() > rogue.supportedMaximumLevel() || !rogueFeatures.add(definition.executionAlgorithm()))
                    throw unsupported("ROGUE_FEATURES");
                continue;
            }
            if (FIGHTER_EXTRA_ATTACK_ALGORITHM.equals(definition.executionAlgorithm())
                    || FIGHTER_SECOND_WIND_ALGORITHM.equals(definition.executionAlgorithm())
                    || FIGHTER_DEFENSE_ALGORITHM.equals(definition.executionAlgorithm())
                    || FIGHTER_ACTION_SURGE_ALGORITHM.equals(definition.executionAlgorithm())) {
                if (!definition.owner().equals(fighter.ownerClass())) throw unsupported("FEATURE_OWNER");
                if (definition.level() > fighter.supportedMaximumLevel()) throw unsupported("FEATURE_RANGE");
                continue;
            }
            if (CHAMPION_IMPROVED_CRITICAL_ALGORITHM.equals(definition.executionAlgorithm())) {
                if (enabled(rules, FinalSubclass.class).stream().noneMatch(subclass ->
                        subclass.identity().equals(definition.owner()) && subclass.definition().ownerClass().equals(fighter.ownerClass())))
                    throw unsupported("FEATURE_OWNER");
                if (definition.level() > fighter.supportedMaximumLevel()) throw unsupported("FEATURE_RANGE");
                continue;
            }
            if (definition.kind() == FeatureKind.FEATURE
                    && Schema2ArcaneRecoveryRules.ALGORITHM.equals(definition.executionAlgorithm())
                    && definition.owner().equals(wizard.ownerClass()) && definition.level() == 1
                    && ++arcaneRecovery == 1) continue;
            throw unsupported("FEATURE_OWNER");
        }
        if (rogue != null && rogueFeatures.size() != 2) throw unsupported("ROGUE_FEATURES");
    }

    private static void validateSpells(ProductionSchema2Projection projection, SpellcastingDefinition life,
            WizardSpellcastingDefinition wizard, int maximumLevel) {
        var spells = enabled(projection.projection().rules(), FinalSpell.class).stream().map(FinalSpell::definition)
                .collect(Collectors.toUnmodifiableMap(SpellDefinition::identity, row -> row));
        var listed = projection.projection().entries().stream().filter(FinalEntry::enabled).map(FinalEntry::entry)
                .filter(SpellbookEntry.class::isInstance).map(SpellbookEntry.class::cast)
                .filter(entry -> entry.identity().rule().equals(wizard.identity()))
                .map(SpellbookEntry::spell).filter(spells::containsKey).collect(Collectors.toUnmodifiableSet());
        for (var spell : spells.values()) {
            if (spell.algorithm().contains("_METADATA_") || spell.identity().equals(life.bless())
                    || spell.identity().equals(life.cureWounds())) continue;
            if (Set.of(FIRE_BOLT_ALGORITHM, SACRED_FLAME_ALGORITHM).contains(spell.algorithm())) continue;
            if (!WIZARD_CASTS.contains(spell.algorithm()) || !listed.contains(spell.identity())
                    || spellLevel(spell.algorithm()) > (maximumLevel + 1) / 2) throw unsupported("SPELL_OWNER");
        }
        for (int level = 1; level <= maximumLevel; level++) {
            int rank = (level + 1) / 2;
            long learnable = listed.stream().map(spells::get).filter(spell -> spellLevel(spell.algorithm()) <= rank).count();
            if (learnable < 6 + 2L * (level - 1)) throw unsupported("SPELLBOOK_CHOICES");
        }
    }

    private static void validateCantrips(ProductionSchema2Projection projection, RuleIdentity cleric,
            RuleIdentity wizard, int wizardMaximum) {
        var profiles = enabled(projection.projection().rules(), FinalClassCantrips.class).stream()
                .map(row -> row.definition().ownerClass()).toList();
        if (profiles.size() != 2 || !Set.copyOf(profiles).equals(Set.of(cleric, wizard))) throw unsupported("CANTRIP_OWNER");
        for (RuleIdentity owner : List.of(cleric, wizard)) {
            var profile = projection.classCantrips(owner).orElseThrow();
            boolean isWizard = owner.equals(wizard);
            String executable = isWizard ? FIRE_BOLT_ALGORITHM : SACRED_FLAME_ALGORITHM;
            if (profile.definition().knownCount(1) != 3
                    || profile.options().size() < Math.max(4, profile.definition().knownCount(isWizard ? wizardMaximum : 1))
                    || profile.options().stream().map(spell -> spell.identity().ruleKey()).distinct().count() != profile.options().size()
                    || profile.options().stream().anyMatch(spell -> spellLevel(spell.algorithm()) != 0
                            || !spell.algorithm().contains("_METADATA_") && !executable.equals(spell.algorithm())))
                throw unsupported("CANTRIP_CHOICES");
        }
    }

    private static void validateCreation(CampaignRuleContext context) {
        var engine = new CampaignDomainEngine();
        try {
            for (String characterClass : context.initialClasses()) {
                var choices = context.initialChoices(characterClass);
                var abilities = context.initialAbilities(characterClass);
                for (String background : CampaignRuleContext.INITIAL_BACKGROUNDS) {
                    for (var ancestry : context.creationAncestries()) {
                        var plan = context.initialPlan(CAMPAIGN, CHARACTER, IDENTITY, characterClass, background,
                                ancestry.race().ruleKey(), ancestry.subrace() == null ? null : ancestry.subrace().ruleKey());
                        var history = engine.initial(context.projection(), plan, PREPARATION, abilities, choices, OPERATION);
                        Schema2FeatureSupport.requireSupportedFeatures(context.projection(), history.current(), "S2_FEATURE_OWNER");
                    }
                }
            }
        } catch (IllegalArgumentException invalid) {
            throw unsupported("INITIAL_TEMPLATE", invalid);
        }
    }

    private static <T extends FinalRule> T required(List<FinalRule> rules, Class<T> type, String scope) {
        List<T> matches = enabled(rules, type);
        if (matches.size() != 1) throw unsupported(scope);
        return matches.getFirst();
    }

    private static <T extends FinalRule> List<T> enabled(List<FinalRule> rules, Class<T> type) {
        return rules.stream().filter(FinalRule::enabled).filter(type::isInstance).map(type::cast).toList();
    }

    private static UUID fixtureId(long suffix) {
        return new UUID(0x4000L, Long.MIN_VALUE | suffix);
    }

    private static IllegalArgumentException unsupported(String scope) {
        return unsupported(scope, null);
    }

    private static IllegalArgumentException unsupported(String scope, Throwable cause) {
        return new IllegalArgumentException("STORAGE_RULE_SUPPORT_" + scope, cause);
    }
}
