package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.service.Schema2InitialCharacterPlanService.Plan;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.Schema2InitialProficiencyRulesTest.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2InitialCharacterPlanServiceTest {
    private static final Schema2PreparationIdentity IDENTITY = new Schema2PreparationIdentity(UUID.fromString("00112233-4455-4677-8899-aabbccddeeff"),
            UUID.fromString("ffeeddcc-bbaa-4988-8766-554433221100"));

    @Test void exactFrozenMaterialPreservesTheIdentitySourcesAndGrants() {
        var projection = projection();
        var binding = Schema2RulesBinding.from(projection);
        var service = new Schema2InitialCharacterPlanService();
        var plan = service.prepareMaterial(IDENTITY, binding, projection, request());
        assertEquals(IDENTITY, plan.identity());
        assertEquals(binding, plan.binding());
        assertEquals(SOURCES, plan.sources());
        assertEquals(new Schema2InitialProficiencyRules().prepare(projection, request()).grants(), plan.grants());
        assertEquals(plan, service.prepareMaterial(IDENTITY, binding, projection, request()));
        assertThrows(IllegalArgumentException.class, () -> service.prepareMaterial(IDENTITY, binding, null, request()));
    }

    @Test void everyBindingDigestVersionAndMemberOrderMustMatchEvenWhenFinalRulesAreUnchanged() {
        var projection = projection(); var original = Schema2RulesBinding.from(projection);
        var service = new Schema2InitialCharacterPlanService();
        List<Schema2RulesBinding> changed = new ArrayList<>();
        changed.add(binding(original, original.members(), "0".repeat(64), original.finalRulesSha256(), original.chainSha256()));
        changed.add(binding(original, original.members(), original.compositionSha256(), "0".repeat(64), original.chainSha256()));
        changed.add(binding(original, original.members(), original.compositionSha256(), original.finalRulesSha256(), "0".repeat(64)));
        for (String part : List.of("version", "raw", "logical")) {
            var members = new ArrayList<>(original.members()); var first = members.getFirst();
            members.set(0, new ProductionSchema2Projection.StoredMember(first.moduleKey(), part.equals("version") ? "2" : first.releaseVersion(),
                    first.role(), part.equals("raw") ? "0".repeat(64) : first.rawZipSha256(),
                    part.equals("logical") ? "0".repeat(64) : first.logicalSha256()));
            changed.add(binding(original, members, original.compositionSha256(), original.finalRulesSha256(), original.chainSha256()));
        }
        var reordered = new ArrayList<>(original.members()); Collections.swap(reordered, 1, 2);
        changed.add(binding(original, reordered, original.compositionSha256(), original.finalRulesSha256(), original.chainSha256()));
        for (var binding : changed) assertEquals("S2_RULES_BINDING_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> service.prepareMaterial(IDENTITY, binding, projection, request())).getMessage());
    }

    @Test void planCollectionsRemainImmutable() {
        var projection = projection();
        var plan = new Schema2InitialCharacterPlanService()
                .prepareMaterial(IDENTITY, Schema2RulesBinding.from(projection), projection, request());
        assertThrows(UnsupportedOperationException.class, () -> plan.choices().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.choices().get(LANGUAGE_SLOT).clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.slots().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.grants().clear());
        var choices = new HashMap<Schema2CompositionKernel.EntryIdentity, List<Schema2CompositionKernel.RuleIdentity>>();
        plan.choices().forEach((slot, targets) -> choices.put(slot, new ArrayList<>(targets)));
        var slots = new ArrayList<>(plan.slots()); var grants = new ArrayList<>(plan.grants());
        var copied = new Plan(plan.identity(), plan.binding(), plan.sources(), choices, slots, grants);
        choices.get(LANGUAGE_SLOT).clear(); choices.clear(); slots.clear(); grants.clear();
        assertEquals(plan, copied);
    }

    private static Schema2RulesBinding binding(Schema2RulesBinding original, List<ProductionSchema2Projection.StoredMember> members,
            String composition, String rules, String chain) {
        return new Schema2RulesBinding(original.canonicalProfile(), original.compositionAlgorithm(), members, composition, rules, chain);
    }
}
