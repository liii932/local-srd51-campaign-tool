package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Ordinary SRD ability, default-ability skill and tool checks in the closed INTERNAL run. */
public final class Schema2CharacterCheckRules {
    public static final String ALGORITHM = "SRD_2014_ABILITY_CHECK_V1";
    private final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
    private final Schema2CharacterRestRules rests = new Schema2CharacterRestRules();
    private final Schema2CharacterEquipmentRules equipment = new Schema2CharacterEquipmentRules();

    /** Only full preflight can create this capability; the caller checks history capacity before RNG. */
    public static final class PreparedCheck {
        private final CheckInput input;
        private final int abilityModifier, proficiencyBonus;
        private final boolean effectiveAdvantage, effectiveDisadvantage;
        private final State capacityState;
        private PreparedCheck(CheckInput input, int abilityModifier, int proficiencyBonus, State capacityState,
                boolean effectiveAdvantage, boolean effectiveDisadvantage) {
            this.input = input; this.abilityModifier = abilityModifier;
            this.proficiencyBonus = proficiencyBonus; this.capacityState = capacityState;
            this.effectiveAdvantage = effectiveAdvantage; this.effectiveDisadvantage = effectiveDisadvantage;
        }
        public boolean secondDieRequired() { return effectiveAdvantage != effectiveDisadvantage; }
        public State capacityState() { return capacityState; }
    }

    public PreparedCheck prepare(ProductionSchema2Projection material, State before, Command command) {
        growth.validateState(material, before); rests.validateState(material, before);
        if (!"CHECK_RESOLVED".equals(command.kind()) || command.checkInput() == null
                || before.version() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS - 1)
            throw invalid("S2_CHECK_COMMAND");
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
        if (!before.features().isEmpty() || !before.feats().isEmpty()) throw invalid("S2_CHECK_EFFECT_UNSUPPORTED");
        var definitions = material.projection().rules().stream().filter(FinalCheck.class::isInstance)
                .map(FinalCheck.class::cast).filter(FinalCheck::enabled).toList();
        CheckInput input = command.checkInput();
        if (definitions.size() != 1 || !ALGORITHM.equals(definitions.getFirst().definition().algorithm())
                || !definitions.getFirst().identity().equals(input.rule())) throw invalid("S2_CHECK_RULE_UNSUPPORTED");
        if (input.kind().equals("SKILL")) {
            var targets = material.projection().rules().stream().filter(FinalProficiency.class::isInstance)
                    .map(FinalProficiency.class::cast).filter(row -> row.identity().equals(input.proficiency())).toList();
            if (targets.size() != 1 || !targets.getFirst().enabled()
                    || targets.getFirst().definition().category() != ProficiencyCategory.SKILL
                    || !input.abilityKey().equals(targets.getFirst().definition().abilityKey()))
                throw invalid("S2_CHECK_SKILL_UNSUPPORTED");
        } else if (input.kind().equals("TOOL")) {
            var targets = material.projection().rules().stream().filter(FinalTool.class::isInstance)
                    .map(FinalTool.class::cast).filter(row -> row.identity().equals(input.proficiency())).toList();
            if (targets.size() != 1 || !targets.getFirst().enabled()) throw invalid("S2_CHECK_TOOL_UNSUPPORTED");
            if (!equipment.hasPreparedTool(material, before, input.proficiency())) throw invalid("S2_CHECK_TOOL_UNAVAILABLE");
        }
        int modifier = Math.floorDiv(before.abilities().get(input.abilityKey()) - 10, 2);
        // Multiple genuine grant sources remain history facts; a target contributes PB once.
        int proficiency = input.proficiency() != null && before.proficiencies().stream()
                .anyMatch(fact -> fact.target().equals(input.proficiency())) ? before.proficiencyBonus() : 0;
        var derived = equipment.derive(material, before);
        boolean physical = input.abilityKey().equals("ability.strength") || input.abilityKey().equals("ability.dexterity");
        boolean disadvantage = input.disadvantage() || physical && derived.physicalCheckDisadvantage()
                || input.kind().equals("SKILL") && derived.stealthSkills().contains(input.proficiency());
        return new PreparedCheck(input, modifier, proficiency, withNextVersion(before), input.advantage(), disadvantage);
    }

    /** No random source is reachable here; also used to verify retained results during recovery. */
    public CheckResult resolve(PreparedCheck prepared, int firstD20, Integer secondD20) {
        if (prepared == null || firstD20 < 1 || firstD20 > 20
                || prepared.secondDieRequired() != (secondD20 != null)
                || secondD20 != null && (secondD20 < 1 || secondD20 > 20)) throw invalid("S2_CHECK_RANDOM_RESULT");
        int selected = secondD20 == null ? firstD20 : prepared.effectiveAdvantage
                ? Math.max(firstD20, secondD20) : Math.min(firstD20, secondD20);
        int total = selected + prepared.abilityModifier + prepared.proficiencyBonus;
        return new CheckResult(firstD20, secondD20, selected, prepared.abilityModifier, prepared.proficiencyBonus,
                total, total >= prepared.input.difficultyClass(), prepared.effectiveAdvantage, prepared.effectiveDisadvantage);
    }

    public State verify(ProductionSchema2Projection material, State before, Command command, CheckResult result) {
        PreparedCheck prepared = prepare(material, before, command);
        if (result == null || !resolve(prepared, result.firstD20(), result.secondD20()).equals(result))
            throw invalid("S2_CHECK_RESULT_CORRUPT");
        return prepared.capacityState();
    }

    private static State withNextVersion(State before) {
        return new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(),
                before.characterClass(), before.abilities(), before.level(), before.proficiencyBonus(), before.hitPoints(),
                before.maximumHitPoints(), before.classes(), before.hitDicePools(), before.subclasses(), before.features(),
                before.feats(), before.proficiencies(), before.version() + 1, before.resources(), before.rest(), before.equipment(), before.health());
    }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
