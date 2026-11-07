package com.dndtool.service.storage;

import com.dndtool.module.AdvancementValueProfile;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2ArcaneRecoveryRules;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CombatRules;
import com.dndtool.service.Schema2FeatureSupport;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.Schema2SpellRules;
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
        var context = new CampaignRuleContext(projection, check.identity());
        try {
            Schema2CombatRules.validateFrozenSupport(projection, context.rule("character.race", "race.human"));
        } catch (IllegalArgumentException invalid) {
            throw unsupported("COMBAT", invalid);
        }
        var fighter = context.rule("character.class", "class.fighter");
        var cleric = context.rule("character.class", "class.cleric");
        var wizard = context.rule("character.class", "class.wizard");
        var progressions = validateGrowth(rules, Map.of(fighter, 6, cleric, 1, wizard, 5));
        var life = required(rules, FinalSpellcasting.class, "LIFE_CASTING").definition();
        var prepared = required(rules, FinalWizardSpellcasting.class, "WIZARD_CASTING").definition();
        var lifeSubclass = context.rule("character.subclass", "subclass.life");
        if (!life.ownerClass().equals(cleric) || !life.requiredSubclass().equals(lifeSubclass)
                || !prepared.ownerClass().equals(wizard)) throw unsupported("CASTING_OWNER");
        if (enabled(rules, FinalSubclass.class).stream().anyMatch(row -> row.definition().ownerClass().equals(cleric)
                && !row.identity().equals(lifeSubclass))) throw unsupported("LIFE_SUBCLASS");
        validateFeatures(rules, progressions.get(fighter), life, prepared);
        validateSpells(projection, life, prepared, progressions.get(wizard).supportedMaximumLevel());
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

    private static void validateFeatures(List<FinalRule> rules, ClassProgressionDefinition fighter, SpellcastingDefinition life,
            WizardSpellcastingDefinition wizard) {
        int arcaneRecovery = 0;
        for (var row : enabled(rules, FinalFeature.class)) {
            var definition = row.definition();
            if (definition.kind() == FeatureKind.OPTION && definition.executionMode() != FeatureExecution.AUTOMATIC)
                continue;
            if (row.identity().equals(life.requiredFeature()) || row.identity().equals(wizard.requiredFeature()))
                continue;
            if (FIGHTER_EXTRA_ATTACK_ALGORITHM.equals(definition.executionAlgorithm())) {
                if (!definition.owner().equals(fighter.ownerClass())) throw unsupported("FEATURE_OWNER");
                if (definition.level() > fighter.supportedMaximumLevel()) throw unsupported("FEATURE_RANGE");
                continue;
            }
            if (definition.kind() == FeatureKind.FEATURE
                    && Schema2ArcaneRecoveryRules.ALGORITHM.equals(definition.executionAlgorithm())
                    && definition.owner().equals(wizard.ownerClass()) && definition.level() == 1
                    && ++arcaneRecovery == 1) continue;
            throw unsupported("FEATURE_OWNER");
        }
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
            if (!WIZARD_CASTS.contains(spell.algorithm()) || !listed.contains(spell.identity())
                    || spellLevel(spell.algorithm()) > (maximumLevel + 1) / 2) throw unsupported("SPELL_OWNER");
        }
        for (int level = 1; level <= maximumLevel; level++) {
            int rank = (level + 1) / 2;
            long learnable = listed.stream().map(spells::get).filter(spell -> spellLevel(spell.algorithm()) <= rank).count();
            if (learnable < 6 + 2L * (level - 1)) throw unsupported("SPELLBOOK_CHOICES");
        }
    }

    private static void validateCreation(CampaignRuleContext context) {
        var engine = new CampaignDomainEngine();
        try {
            for (String characterClass : CampaignRuleContext.INITIAL_CLASSES) {
                var choices = context.initialChoices(characterClass);
                var abilities = context.initialAbilities(characterClass);
                for (String background : CampaignRuleContext.INITIAL_BACKGROUNDS) {
                    var plan = context.initialPlan(CAMPAIGN, CHARACTER, IDENTITY, characterClass, background);
                    var history = engine.initial(context.projection(), plan, PREPARATION, abilities, choices, OPERATION);
                    Schema2FeatureSupport.requireSupportedFeatures(context.projection(), history.current(), "S2_FEATURE_OWNER");
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
