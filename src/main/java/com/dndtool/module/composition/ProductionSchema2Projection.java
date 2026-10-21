package com.dndtool.module.composition;

import java.util.Arrays;
import java.util.List;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Production encoding of the finite typed projection. A successful projection is not acceptance. */
public final class ProductionSchema2Projection {
    public static final String CANONICAL_PROFILE = ProductionSchema2Codec.CANONICAL_PROFILE;
    public static final String COMPOSITION_ALGORITHM = ProductionSchema2Codec.COMPOSITION_ALGORITHM;
    public static final long MAX_RUNTIME_CANONICAL_BYTES = 8L * 1024 * 1024;
    private final Projection projection;
    private final List<StoredMember> members;
    private final byte[] configurationJson;
    private final byte[] finalRulesJson;
    private final byte[] chainsJson;
    private final String compositionSha256;
    private final String finalRulesSha256;
    private final String chainSha256;

    private ProductionSchema2Projection(List<StoredMember> members, Projection projection) {
        this.projection = projection;
        this.members = List.copyOf(members);
        Object configuration = json("author_schema_version", 2, "canonical_profile", CANONICAL_PROFILE,
                "composition_algorithm", COMPOSITION_ALGORITHM, "members", members.stream().map(member ->
                        json("module_key", member.moduleKey(), "release_version", member.releaseVersion(),
                                "role", roleName(member.role()), "raw_zip_sha256", member.rawZipSha256(),
                                "logical_sha256", member.logicalSha256())).toList());
        Object finalRules = json("rules", projection.rules().stream().map(Schema2CompositionKernel::finalRuleJson).toList(),
                "entries", projection.entries().stream().map(Schema2CompositionKernel::finalEntryJson).toList());
        this.configurationJson = ProductionSchema2Codec.canonical(configuration);
        this.finalRulesJson = ProductionSchema2Codec.canonical(finalRules);
        this.compositionSha256 = ProductionSchema2Codec.digest("composition-config", configuration);
        this.finalRulesSha256 = ProductionSchema2Codec.digest("final-rules", finalRules);
        Object envelope = json("composition_sha256", compositionSha256,
                "chains", projection.chains().stream().map(Schema2CompositionKernel::chainJson).toList());
        this.chainsJson = ProductionSchema2Codec.canonical(envelope);
        this.chainSha256 = ProductionSchema2Codec.digest("full-chain", envelope);
    }

    public static ProductionSchema2Projection compose(List<ProductionRuleArtifact> artifacts) {
        if (artifacts != null && artifacts.size() > MAX_PACKAGES) {
            throw new Schema2Exception(new Diagnostic("S2_BUDGET", Stage.PARSE, "Package count budget exceeded", List.of()));
        }
        if (artifacts == null || artifacts.stream().anyMatch(java.util.Objects::isNull)) {
            throw new Schema2Exception(new Diagnostic("S2_SHAPE", Stage.GRAPH, "Missing selected production artifact", List.of()));
        }
        List<ProductionRuleArtifact> selected = List.copyOf(artifacts);
        Projection projection = new Schema2CompositionKernel().compose(selected.stream().map(ProductionRuleArtifact::authorPackage).toList());
        return new ProductionSchema2Projection(selected.stream().map(artifact -> new StoredMember(artifact.moduleKey(),
                artifact.releaseVersion(), artifact.role(), artifact.rawZipSha256(), artifact.logicalSha256())).toList(), projection);
    }

    /** Reconstructs the complete typed chains and production configuration before comparing bytes. */
    public static void verifyChains(List<ProductionRuleArtifact> artifacts, byte[] chainsJson) {
        ProductionSchema2Projection expected = compose(artifacts);
        if (chainsJson == null || !Arrays.equals(expected.chainsJson, chainsJson)) {
            throw new Schema2Exception(new Diagnostic("S2_CHAIN_MISMATCH", Stage.FINAL,
                    "Production chain evidence differs from original declarations and operations",
                    List.of(new Location(null, null, null, "$chains", "", null))));
        }
    }

    /** Independently rebuilds production bytes from complete typed runtime rows, without source artifacts. */
    public static ProductionSchema2Projection restore(List<StoredMember> members, List<FinalRule> rules,
                                                      List<FinalEntry> entries, List<FieldChain> chains) {
        ProductionSchema2Projection restored = new ProductionSchema2Projection(members,
                Schema2CompositionKernel.restoreStored(members, rules, entries, chains));
        if (restored.canonicalByteLength() > MAX_RUNTIME_CANONICAL_BYTES) {
            throw new Schema2Exception(new Diagnostic("S2_BUDGET", Stage.FINAL, "Runtime canonical byte budget exceeded", List.of()));
        }
        return restored;
    }

    public record StoredMember(String moduleKey, String releaseVersion, Role role,
                               String rawZipSha256, String logicalSha256) { }

    public long canonicalByteLength() { return (long) configurationJson.length + finalRulesJson.length + chainsJson.length; }
    public List<StoredMember> members() { return members; }
    public Projection projection() { return projection; }
    public String canonicalProfile() { return CANONICAL_PROFILE; }
    public byte[] configurationJson() { return configurationJson.clone(); }
    public byte[] finalRulesJson() { return finalRulesJson.clone(); }
    public byte[] chainsJson() { return chainsJson.clone(); }
    public String compositionSha256() { return compositionSha256; }
    public String finalRulesSha256() { return finalRulesSha256; }
    public String chainSha256() { return chainSha256; }
}
