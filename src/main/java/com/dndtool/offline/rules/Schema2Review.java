package com.dndtool.offline.rules;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded readable review plus the exact original ZIP, including non-logical license metadata. */
final class Schema2Review {
    static final int MAX_BYTES=2*1024*1024;
    private Schema2Review() { }
    static byte[] create(MaintenanceEvidence evidence,JdbcSchema2RuleSource.Control control,
            JdbcSchema2RuleSource.Installed installed)throws IOException {
        var text=new StringBuilder("Schema 2 local DM content review / protocol 1\n");
        text.append("This review grants no complete-base, combination, runtime, release or archive qualification.\n");
        text.append("target=").append(evidence.target()).append("\nlineage=").append(evidence.lineage())
                .append("\nexpected_source_version=").append(control.version())
                .append("\nexpected_installations=").append(control.installations())
                .append("\nexpected_acceptances=").append(control.acceptances())
                .append("\nmodule_key=").append(installed.identity().moduleKey())
                .append("\nrelease_version=").append(installed.identity().releaseVersion())
                .append("\nrole=").append(installed.role()).append("\nauthor_schema_version=2\nartifact_protocol_version=1\ncanonical_profile=")
                .append(installed.canonicalProfile()).append("\ninstalled_operation_id=").append(installed.operationId())
                .append("\ninstalled_operation_digest=").append(installed.operationDigest())
                .append("\ninstallation_sequence=").append(installed.sequence())
                .append("\nraw_zip_sha256=").append(installed.rawZipSha256())
                .append("\nlogical_sha256=").append(installed.logicalSha256())
                .append("\nraw_zip_byte_length=").append(installed.byteLength())
                .append("\n\nComplete parsed logical declarations (production canonical JSON):\n")
                .append(new String(installed.artifact().logicalJson(),StandardCharsets.UTF_8)).append('\n');
        text.append("\nEvery original member follows. Content is JSON quoted so control characters cannot affect the review viewer; escapes represent exact Unicode characters. The companion .review.zip retains exact original bytes.\n");
        for(var entry:installed.artifact().originalMembers().entrySet()) {
            text.append("\nPATH ").append(entry.getKey()).append("; bytes=").append(entry.getValue().length)
                    .append("; raw_sha256=").append(RuleArtifact.sha256(entry.getValue())).append('\n')
                    .append(OfflineJson.quote(new String(entry.getValue(),StandardCharsets.UTF_8))).append('\n');
        }
        byte[] review=text.toString().getBytes(StandardCharsets.UTF_8);
        if(review.length>MAX_BYTES)throw new IOException("Review byte budget exceeded");return review;
    }
}
