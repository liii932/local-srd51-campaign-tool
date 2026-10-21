package com.dndtool.module.composition;

import com.dndtool.module.composition.Schema2CompositionKernel.AuthorPackage;
import com.dndtool.module.composition.Schema2CompositionKernel.Role;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Verified original ZIP and a finite schema-2 author model; reading grants no execution authority. */
public final class ProductionRuleArtifact {
    public static final int MAX_ZIP_BYTES = Schema2ZipReader.MAX_ZIP_BYTES;
    public static final String CANONICAL_PROFILE = ProductionSchema2Codec.CANONICAL_PROFILE;
    private final AuthorPackage authorPackage;
    private final byte[] logicalJson;
    private final String logicalSha256;
    private final Map<String, byte[]> originalMembers;
    private final long expandedByteLength;

    private ProductionRuleArtifact(Schema2ZipReader.DecodedZip decoded) {
        this.authorPackage = new Schema2CompositionKernel().readArtifact(decoded);
        this.originalMembers = decoded.originalMembers();
        this.expandedByteLength = originalMembers.values().stream().mapToLong(body -> body.length).sum();
        this.logicalJson = ProductionSchema2Codec.canonical(authorPackage.logicalProjection());
        this.logicalSha256 = ProductionSchema2Codec.digest("module-logic", authorPackage.logicalProjection());
    }

    public static ProductionRuleArtifact read(byte[] originalZip) {
        return new ProductionRuleArtifact(Schema2ZipReader.read(originalZip));
    }
    public byte[] originalZip() { return authorPackage.rawZipBytes(); }
    /** Total verified expanded author and metadata bytes; no payload copies are made. */
    public long expandedByteLength() { return expandedByteLength; }
    /** All original author and allowlisted metadata bytes, copied deeply on each call. */
    public Map<String, byte[]> originalMembers() {
        Map<String, byte[]> result = new TreeMap<>();
        originalMembers.forEach((path, body) -> result.put(path, body.clone()));
        return Collections.unmodifiableMap(result);
    }
    public AuthorPackage authorPackage() { return authorPackage; }
    public String moduleKey() { return authorPackage.moduleKey(); }
    public String releaseVersion() { return authorPackage.releaseVersion(); }
    public Role role() { return authorPackage.role(); }
    public String canonicalProfile() { return CANONICAL_PROFILE; }
    public String rawZipSha256() { return authorPackage.rawZipSha256(); }
    public String logicalSha256() { return logicalSha256; }
    public byte[] logicalJson() { return logicalJson.clone(); }
}
