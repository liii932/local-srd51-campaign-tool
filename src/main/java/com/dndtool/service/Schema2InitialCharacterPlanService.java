package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2InitialProficiencyRules.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds read-only initial plans bound to an exact preparation identity and production rules. */
public final class Schema2InitialCharacterPlanService {
    private final Schema2InitialProficiencyRules rules = new Schema2InitialProficiencyRules();

    /** Builds a bounded plan from supplied material after verifying its production rules binding. */
    public Plan prepareMaterial(Schema2PreparationIdentity identity, Schema2RulesBinding binding,
            ProductionSchema2Projection material, Request request) {
        if (identity == null || binding == null || material == null || request == null)
            throw new IllegalArgumentException("S2_INITIAL_REQUEST");
        binding.verify(material);
        Prepared prepared = rules.prepare(material, request);
        return new Plan(identity, Schema2RulesBinding.from(material), prepared.sources(),
                prepared.choices(), prepared.slots(), prepared.grants());
    }

    /** Snapshot-bound INTERNAL plan retaining its local runtime identity, not a portable archive. */
    public record Plan(Schema2PreparationIdentity identity, Schema2RulesBinding binding, Sources sources,
                       Map<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity,
                               List<com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity>> choices,
                       List<SlotFact> slots, List<GrantFact> grants) {
        public Plan {
            if (identity == null || binding == null) throw new IllegalArgumentException("S2_INITIAL_REQUEST");
            Prepared immutable = new Prepared(sources, choices, slots, grants);
            choices = immutable.choices();
            slots = immutable.slots();
            grants = immutable.grants();
        }
    }

    /** Planned identities are preparation facts; no campaign or character row exists because of them. */
    public record InitialPlan(UUID campaignId, UUID characterId, Plan character) {
        public InitialPlan {
            if (!uuid4(campaignId) || !uuid4(characterId) || campaignId.equals(characterId) || character == null
                    || campaignId.equals(character.identity().runId()) || campaignId.equals(character.identity().snapshotId())
                    || characterId.equals(character.identity().runId()) || characterId.equals(character.identity().snapshotId()))
                throw new IllegalArgumentException("S2_INITIAL_IDENTITY");
        }
        private static boolean uuid4(UUID value) { return value != null && value.version() == 4 && value.variant() == 2; }
    }
}
