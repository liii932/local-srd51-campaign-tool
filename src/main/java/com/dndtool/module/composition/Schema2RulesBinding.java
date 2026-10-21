package com.dndtool.module.composition;

import java.util.List;
import java.util.Objects;

/** Portable rule identity only. This is neither a campaign archive nor execution permission. */
public record Schema2RulesBinding(String canonicalProfile, String compositionAlgorithm,
        List<ProductionSchema2Projection.StoredMember> members, String compositionSha256,
        String finalRulesSha256, String chainSha256) {
    public Schema2RulesBinding {
        if (!ProductionSchema2Projection.CANONICAL_PROFILE.equals(canonicalProfile)
                || !ProductionSchema2Projection.COMPOSITION_ALGORITHM.equals(compositionAlgorithm)
                || members == null || members.isEmpty()
                || members.size() > Schema2CompositionKernel.MAX_PACKAGES) {
            throw new IllegalArgumentException("S2_RULES_BINDING");
        }
        members = List.copyOf(members);
        var keys = new java.util.HashSet<String>();
        for (int i = 0; i < members.size(); i++) {
            var member = members.get(i);
            if (member.moduleKey() == null || !member.moduleKey().matches("[a-z][a-z0-9_]{0,63}")
                    || member.releaseVersion() == null
                    || !member.releaseVersion().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                    || member.role() == null || !keys.add(member.moduleKey())
                    || (i == 0) != (member.role() == Schema2CompositionKernel.Role.BASE)) {
                throw new IllegalArgumentException("S2_RULES_BINDING");
            }
            digest(member.rawZipSha256());
            digest(member.logicalSha256());
        }
        digest(compositionSha256);
        digest(finalRulesSha256);
        digest(chainSha256);
    }

    public static Schema2RulesBinding from(ProductionSchema2Projection projection) {
        Objects.requireNonNull(projection);
        return new Schema2RulesBinding(projection.canonicalProfile(),
                ProductionSchema2Projection.COMPOSITION_ALGORITHM, projection.members(),
                projection.compositionSha256(), projection.finalRulesSha256(), projection.chainSha256());
    }

    /** Exact order and both member digests matter even when the final rule digest is identical. */
    public void verify(ProductionSchema2Projection projection) {
        if (!equals(from(projection))) throw new IllegalArgumentException("S2_RULES_BINDING_MISMATCH");
    }

    private static void digest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("S2_RULES_BINDING");
    }
}
