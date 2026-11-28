package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterRuntime.*;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CharacterRuntime.PASSIVE_CHECK;
import static com.dndtool.service.Schema2CharacterRuntime.checkKind;
import static com.dndtool.service.Schema2CharacterRuntime.validCheckSelection;

/** SRD ability, skill and tool checks in the closed INTERNAL run. */
public final class Schema2CharacterCheckRules {
    public static final String ALGORITHM = "SRD_2014_ABILITY_CHECK_V1";
    private final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
    private final Schema2CharacterRestRules rests = new Schema2CharacterRestRules();
    private final Schema2CharacterEquipmentRules equipment = new Schema2CharacterEquipmentRules();
    private final Schema2CharacterHealthRules health = new Schema2CharacterHealthRules();

    public record RollInput(RuleIdentity rule, String kind, String abilityKey, RuleIdentity proficiency,
                            boolean advantage, boolean disadvantage) {
        public RollInput {
            if (!validCheckSelection(rule, kind, abilityKey, proficiency)) throw invalid("S2_CHARACTER_CHECK_INPUT");
        }
    }

    public static final class PreparedRoll {
        private final CheckDefinition definition;
        private final int abilityModifier, proficiencyBonus;
        private final RuleIdentity stonecunningRule;
        private final boolean effectiveAdvantage, effectiveDisadvantage;
        private PreparedRoll(CheckDefinition definition, int abilityModifier, int proficiencyBonus, RuleIdentity stonecunningRule,
                             boolean effectiveAdvantage, boolean effectiveDisadvantage) {
            this.definition = definition;
            this.abilityModifier = abilityModifier;
            this.proficiencyBonus = proficiencyBonus;
            this.stonecunningRule = stonecunningRule;
            this.effectiveAdvantage = effectiveAdvantage;
            this.effectiveDisadvantage = effectiveDisadvantage;
        }
        public boolean secondDieRequired() { return effectiveAdvantage != effectiveDisadvantage; }
    }

    public record RolledScore(int firstD20, Integer secondD20, int selectedD20, int abilityModifier,
                              int proficiencyBonus, int total, boolean effectiveAdvantage, boolean effectiveDisadvantage) { }

    public enum ContestOutcome { INITIATOR_WINS, RESPONDER_WINS, UNCHANGED }

    /** The caller checks history capacity before obtaining any dice for an ordinary check. */
    public static final class PreparedCheck {
        private final CheckInput input;
        private final PreparedRoll roll;
        private final Integer passiveBaseScore, passiveAdjustment;
        private final State capacityState;
        private PreparedCheck(CheckInput input, PreparedRoll roll, State capacityState,
                Integer passiveBaseScore, Integer passiveAdjustment) {
            this.input = input;
            this.roll = roll;
            this.capacityState = capacityState;
            this.passiveBaseScore = passiveBaseScore;
            this.passiveAdjustment = passiveAdjustment;
        }
        public boolean isPassive() { return passiveBaseScore != null; }
        public boolean secondDieRequired() { return !isPassive() && roll.secondDieRequired(); }
        public State capacityState() { return capacityState; }
    }

    public PreparedCheck prepare(ProductionSchema2Projection material, State before, Command command) {
        return prepare(material, null, before, command);
    }

    public PreparedCheck prepare(ProductionSchema2Projection material, InitialPlan initial, State before, Command command) {
        if (!checkKind(command.kind()) || command.checkInput() == null
                || before.version() >= Schema2CharacterRuntime.MAX_EVENTS - 1)
            throw invalid("S2_CHECK_COMMAND");
        CheckInput input = command.checkInput();
        PreparedRoll roll = prepareRoll(material, initial, before, new RollInput(input.rule(), input.kind(),
                input.abilityKey(), input.proficiency(), input.advantage(), input.disadvantage()), input.subject());
        boolean passive = PASSIVE_CHECK.equals(command.kind());
        return new PreparedCheck(input, roll, withNextVersion(before), passive ? roll.definition.passiveBaseScore() : null,
                passive ? passiveAdjustment(roll) : null);
    }

    public PreparedRoll prepareContestRoll(ProductionSchema2Projection material, State before, RollInput input) {
        if (input == null || !Set.of("ABILITY", "SKILL").contains(input.kind())) throw invalid("S2_CONTEST_CHECK_INPUT");
        health.validateState(before);
        return prepareRoll(material, null, before, input, CheckInput.GENERAL);
    }

    private PreparedRoll prepareRoll(ProductionSchema2Projection material, InitialPlan initial, State before,
                                     RollInput input, String subject) {
        growth.validateState(material, before); rests.validateState(material, before);
        if (before.rest().active() != null) throw invalid("S2_CHECK_REST_ACTIVE");
        if (before.hitPoints() == 0 || before.health().dead()) throw invalid("S2_CHECK_HEALTH");
        for (ClassLevel characterClass : before.classes()) {
            var owners = material.projection().rules().stream().filter(FinalClassProgression.class::isInstance)
                    .map(FinalClassProgression.class::cast)
                    .filter(row -> row.enabled() && row.definition().ownerClass().equals(characterClass.characterClass())).toList();
            if (owners.size() != 1 || owners.getFirst().definition().advancementScope() != ProgressionScope.CHARACTER_ADVANCEMENT)
                throw invalid("S2_CHECK_GROWTH_SCOPE");
        }
        // Obtaining a feature/feat is not an execution owner for its check modifiers, rerolls,
        // restrictions or advantage. This slice accepts no acquired unresolved influence.
        Schema2FeatureSupport.requireSupportedFeatures(material, before, "S2_CHECK_EFFECT_UNSUPPORTED");
        var definitions = material.projection().rules().stream().filter(FinalCheck.class::isInstance)
                .map(FinalCheck.class::cast).filter(FinalCheck::enabled).toList();
        if (definitions.size() != 1) throw invalid("S2_CHECK_RULE_UNSUPPORTED");
        var definition = definitions.getFirst().definition();
        if (!ALGORITHM.equals(definition.algorithm()) || !definition.identity().equals(input.rule()))
            throw invalid("S2_CHECK_RULE_UNSUPPORTED");
        if (input.kind().equals("SKILL")) {
            var targets = material.projection().rules().stream().filter(FinalProficiency.class::isInstance)
                    .map(FinalProficiency.class::cast).filter(row -> row.identity().equals(input.proficiency())).toList();
            if (targets.size() != 1 || !targets.getFirst().enabled()
                    || targets.getFirst().definition().category() != ProficiencyCategory.SKILL)
                throw invalid("S2_CHECK_SKILL_UNSUPPORTED");
            if (!definition.allowAlternateSkillAbility() && !input.abilityKey().equals(targets.getFirst().definition().abilityKey()))
                throw invalid("S2_CHECK_SKILL_UNSUPPORTED");
        } else if (input.kind().equals("TOOL")) {
            var targets = material.projection().rules().stream().filter(FinalTool.class::isInstance)
                    .map(FinalTool.class::cast).filter(row -> row.identity().equals(input.proficiency())).toList();
            if (targets.size() != 1 || !targets.getFirst().enabled()) throw invalid("S2_CHECK_TOOL_UNSUPPORTED");
            if (!equipment.hasPreparedTool(material, before, input.proficiency())) throw invalid("S2_CHECK_TOOL_UNAVAILABLE");
        }
        int modifier = Math.floorDiv(before.abilities().get(input.abilityKey()) - 10, 2);
        var stonecunning = subject.equals(CheckInput.STONEWORK_ORIGIN) ? stonecunning(material, initial, before) : null;
        int proficiency = stonecunning == null ? Schema2CharacterRuntime.ordinaryCheckProficiency(before, input.proficiency())
                : before.proficiencyBonus() * stonecunning.proficiencyMultiplier();
        var derived = equipment.derive(material, before);
        boolean dexterity = input.abilityKey().equals("ability.dexterity");
        boolean physical = dexterity || input.abilityKey().equals("ability.strength");
        boolean disadvantage = input.disadvantage() || physical && derived.physicalCheckDisadvantage()
                || dexterity && input.kind().equals("SKILL") && derived.stealthSkills().contains(input.proficiency());
        return new PreparedRoll(definition, modifier, proficiency, stonecunning == null ? null : stonecunning.identity(),
                input.advantage(), disadvantage);
    }

    private static StonecunningDefinition stonecunning(ProductionSchema2Projection material, InitialPlan initial, State before) {
        if (initial == null || !initial.campaignId().equals(before.campaignId()) || !initial.characterId().equals(before.characterId())
                || !initial.character().identity().equals(before.identity())
                || !initial.character().sources().characterClass().equals(before.characterClass()))
            throw invalid("S2_CHECK_INITIAL_BINDING");
        Schema2CharacterProgressionRules.verifyInitialPlan(material, initial);
        var enabled = material.projection().rules().stream().filter(FinalStonecunning.class::isInstance)
                .map(FinalStonecunning.class::cast).filter(FinalStonecunning::enabled).toList();
        if (enabled.isEmpty()) return null;
        if (enabled.size() != 1) throw invalid("S2_CHECK_STONECUNNING_RULE");
        var definition = enabled.getFirst().definition();
        if (!STONECUNNING_ALGORITHM.equals(definition.algorithm()) || !STONECUNNING_DWARF.equals(definition.owner())
                || !STONECUNNING_HISTORY.equals(definition.skill()) || definition.proficiencyMultiplier() < STONECUNNING_MIN_MULTIPLIER
                || definition.proficiencyMultiplier() > STONECUNNING_MAX_MULTIPLIER)
            throw invalid("S2_CHECK_STONECUNNING_RULE");
        return definition.owner().equals(initial.character().sources().race()) ? definition : null;
    }

    /** No random source is reachable here; also used to verify retained results during recovery. */
    public CheckResult resolve(PreparedCheck prepared, int firstD20, Integer secondD20) {
        if (prepared == null || prepared.isPassive()) throw invalid("S2_CHECK_RANDOM_RESULT");
        RolledScore score = resolveRoll(prepared.roll, firstD20, secondD20);
        return new CheckResult(score.firstD20(), score.secondD20(), score.selectedD20(), score.abilityModifier(), score.proficiencyBonus(),
                score.total(), score.total() >= prepared.input.difficultyClass(), score.effectiveAdvantage(), score.effectiveDisadvantage(),
                prepared.capacityState.proficiencyBonus(), prepared.roll.stonecunningRule);
    }

    public RolledScore resolveRoll(PreparedRoll prepared, int firstD20, Integer secondD20) {
        if (prepared == null || firstD20 < 1 || firstD20 > 20
                || prepared.secondDieRequired() != (secondD20 != null)
                || secondD20 != null && (secondD20 < 1 || secondD20 > 20)) throw invalid("S2_CHECK_RANDOM_RESULT");
        int selected = secondD20 == null ? firstD20 : prepared.effectiveAdvantage
                ? Math.max(firstD20, secondD20) : Math.min(firstD20, secondD20);
        int total = selected + prepared.abilityModifier + prepared.proficiencyBonus;
        return new RolledScore(firstD20, secondD20, selected, prepared.abilityModifier, prepared.proficiencyBonus,
                total, prepared.effectiveAdvantage, prepared.effectiveDisadvantage);
    }

    int passiveScore(PreparedRoll prepared) {
        return prepared.definition.passiveBaseScore() + prepared.abilityModifier + prepared.proficiencyBonus
                + passiveAdjustment(prepared);
    }

    private static int passiveAdjustment(PreparedRoll roll) {
        int adjustment = roll.definition.passiveAdvantageAdjustment();
        return (roll.effectiveAdvantage ? adjustment : 0) - (roll.effectiveDisadvantage ? adjustment : 0);
    }

    public static ContestOutcome compareContest(int initiatorTotal, int responderTotal, int minimumWinningMargin) {
        if (minimumWinningMargin < MIN_CONTEST_WINNING_MARGIN || minimumWinningMargin > MAX_CONTEST_WINNING_MARGIN)
            throw invalid("S2_CONTEST_WINNING_MARGIN");
        long difference = (long) initiatorTotal - responderTotal;
        if (difference >= minimumWinningMargin) return ContestOutcome.INITIATOR_WINS;
        if (difference <= -minimumWinningMargin) return ContestOutcome.RESPONDER_WINS;
        return ContestOutcome.UNCHANGED;
    }

    public CheckResult resolvePassive(PreparedCheck prepared) {
        if (prepared == null || !prepared.isPassive()) throw invalid("S2_CHECK_RANDOM_RESULT");
        var roll = prepared.roll;
        int total = prepared.passiveBaseScore + roll.abilityModifier + roll.proficiencyBonus + prepared.passiveAdjustment;
        return new CheckResult(null, null, null, roll.abilityModifier, roll.proficiencyBonus, total,
                total >= prepared.input.difficultyClass(), roll.effectiveAdvantage, roll.effectiveDisadvantage,
                prepared.capacityState.proficiencyBonus(), roll.stonecunningRule, prepared.passiveBaseScore, prepared.passiveAdjustment);
    }

    public State verify(ProductionSchema2Projection material, State before, Command command, CheckResult result) {
        return verify(material, null, before, command, result);
    }

    public State verify(ProductionSchema2Projection material, InitialPlan initial, State before, Command command, CheckResult result) {
        PreparedCheck prepared = prepare(material, initial, before, command);
        if (result == null || prepared.isPassive() != (result.passiveBaseScore() != null))
            throw invalid("S2_CHECK_RESULT_CORRUPT");
        CheckResult expected = prepared.isPassive() ? resolvePassive(prepared)
                : resolve(prepared, result.firstD20(), result.secondD20());
        if (!expected.equals(result))
            throw invalid("S2_CHECK_RESULT_CORRUPT");
        return prepared.capacityState();
    }

    private static State withNextVersion(State before) {
        return new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(),
                before.characterClass(), before.abilities(), before.level(), before.proficiencyBonus(), before.hitPoints(),
                before.maximumHitPoints(), before.classes(), before.hitDicePools(), before.subclasses(), before.features(),
                before.feats(), before.proficiencies(), before.version() + 1, before.resources(), before.rest(), before.equipment(), before.health(), before.spellbook(), before.knownCantrips());
    }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
