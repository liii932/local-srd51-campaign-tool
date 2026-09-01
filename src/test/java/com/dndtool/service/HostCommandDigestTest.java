package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.dndtool.persistence.CheckEffectPlanRepository.BranchPlan;
import com.dndtool.persistence.CheckEffectPlanRepository.EffectBranch;
import com.dndtool.persistence.CheckEffectPlanRepository.EffectPlan;
import com.dndtool.persistence.EncounterStateRepository.Faction;
import com.dndtool.service.CheckRequestPolicy.IntegerValue;
import com.dndtool.service.CheckRequestPolicy.PreparedEffect;
import com.dndtool.service.CheckRequestPolicy.PreparedParameter;
import com.dndtool.service.CheckRequestPolicy.PreparedRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

final class HostCommandDigestTest {
    @Test
    void checkPayloadBindsBothOrderedBranchesAndTypedValues() {
        PreparedRequest check = new PreparedRequest(
                "check.ability", "ABILITY", "ABILITY_MODIFIER_V1",
                "roll.normal", "NORMAL", "ONLY_CANDIDATE_V1", 1,
                "ability.strength", null, null, 12, List.of());
        PreparedEffect hp = new PreparedEffect(
                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1", List.of(
                        new PreparedParameter("target_character", 1, "REFERENCE",
                                new CheckRequestPolicy.ReferenceValue(
                                        "11111111-1111-4111-8111-111111111111")),
                        new PreparedParameter("amount", 2, "INTEGER", new IntegerValue(-3))));
        BranchPlan success = new BranchPlan(EffectBranch.SUCCESS,
                List.of(new EffectPlan(1, hp)));
        BranchPlan failure = new BranchPlan(EffectBranch.FAILURE, List.of());

        String digest = CheckPayloadDigest.sha256(check, success, failure);

        assertEquals(digest, CheckPayloadDigest.sha256(check, success, failure));
        PreparedEffect changed = new PreparedEffect(
                hp.effectKey(), hp.executionAlgorithm(), List.of(
                        hp.parameters().get(0),
                        new PreparedParameter("amount", 2, "INTEGER", new IntegerValue(-4))));
        assertNotEquals(digest, CheckPayloadDigest.sha256(
                check, new BranchPlan(EffectBranch.SUCCESS,
                        List.of(new EffectPlan(1, changed))), failure));
    }

    @Test
    void encounterDigestSortsParticipantsButBindsFactionAndNode() {
        var first = new EncounterStateService.ParticipantRequest(
                "11111111-1111-4111-8111-111111111111", Faction.ALLY, "node.entry");
        var second = new EncounterStateService.ParticipantRequest(
                "22222222-2222-4222-8222-222222222222", Faction.ENEMY, "node.cellar");
        String campaign = "33333333-3333-4333-8333-333333333333";

        String digest = EncounterRequestDigest.sha256(
                campaign, "node.common_room", List.of(second, first));

        assertEquals(digest, EncounterRequestDigest.sha256(
                campaign, "node.common_room", List.of(first, second)));
        assertNotEquals(digest, EncounterRequestDigest.sha256(
                campaign, "node.common_room", List.of(
                        first,
                        new EncounterStateService.ParticipantRequest(
                                second.characterKey(), Faction.NEUTRAL, second.nodeKey()))));
    }
}
