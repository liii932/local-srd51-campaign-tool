package com.dndtool.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Independent RULES schema approval metadata. Offline SQL is deliberately not a WAR resource. */
public final class RuleSchemaMigrations {
    public static final String SCHEMA_ROLE = "RULES";
    public static final String DEFAULT_SCHEMA = "dnd_tool_rules";
    private static final List<Expectation> APPROVED = List.of(new Expectation(
            SCHEMA_ROLE, 1, "V001__rule-source-schema.sql",
            "003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e"),
            new Expectation(SCHEMA_ROLE, 2, "V002__tool-catalog.sql", "2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40"),
            new Expectation(SCHEMA_ROLE, 3, "V003__schema2-source-ledger.sql", "4166a7f4b4d8670a8300c4129ce566e177b824dda24c58ee3d208f9880048cdf"),
            new Expectation(SCHEMA_ROLE, 4, "V004__schema2-character-grant-source.sql", "9578db2143bfb14eb077e403d5395bb3089a7ab6e54f6926f42b7dbcac4e9269"),
            new Expectation(SCHEMA_ROLE, 5, "V005__schema2-advancement-source.sql", "2dfdecf8cb30fcc71f7e011550ce3b32334340aab5ff546f4e71e53e13147e5c"),
            new Expectation(SCHEMA_ROLE, 6, "V006__schema2-check-source.sql", "9c495e9af9ce06b1e98fba9e44ef159cebbe8ef5f058254e54f8f83a0c7aca30"),
            new Expectation(SCHEMA_ROLE, 7, "V007__schema2-equipment-health-source.sql", "d5bc25f01389e7811280b97c36202b066142efb6ee5521cbb7fccb0def25de45"),
            new Expectation(SCHEMA_ROLE, 8, "V008__schema2-combat-source.sql", "eb0bf9e4675cfed03ec5e5e57f026b57e6bd3409edf98eaf6a9e6e680485cfe2"),
            new Expectation(SCHEMA_ROLE, 9, "V009__schema2-spell-source.sql", "7ffe0707112cf3051d0a80c346360be5e4f933f8046830321fd3ac4f68196cfc"),
            new Expectation(SCHEMA_ROLE, 10, "V010__schema2-wizard-environment-source.sql", "3386950595e0c24eade84c6b9b1c38145ffe8aefcb786b6cc66f663ebd2c3d74"),
            new Expectation(SCHEMA_ROLE, 11, "V011__schema2-monster-source.sql", "fc939ad72e2495b60db8a710bc03c12a8c2c47d3c0e560a394d337c9a05cf6c2"),
            new Expectation(SCHEMA_ROLE, 12, "V012__schema2-spell-save-damage-source.sql", "441d112dc778b0c06ed3e39beab81958aa7af17f328459dd3c3f1768f4b7806f"));

    private RuleSchemaMigrations() {
    }

    public static List<Expectation> expectations() {
        return APPROVED;
    }

    /** Strict decoding for new offline sources; preserves the existing runtime checksum semantics. */
    static String canonicalPayloadSha256(byte[] sql)
            throws CharacterCodingException, SchemaMigrations.PackagedSchemaException {
        String decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(sql)).toString();
        String normalized = decoded.replace("\r\n", "\n").replace('\r', '\n');
        String begin = "-- CHECKSUM-SCOPE-BEGIN";
        String end = "-- CHECKSUM-SCOPE-END";
        if (normalized.lines().filter(begin::equals).count() != 1
                || normalized.lines().filter(end::equals).count() != 1
                || normalized.indexOf(end) <= normalized.indexOf(begin) + begin.length() + 1) {
            throw new SchemaMigrations.PackagedSchemaException();
        }
        return SchemaMigrations.canonicalPayloadSha256(normalized);
    }

    public record Expectation(String schemaRole, int version, String scriptName, String scriptSha256) {
        public Expectation {
            if (!SCHEMA_ROLE.equals(schemaRole) || version < 1 || scriptName == null
                    || scriptName.length() > 255
                    || !scriptName.matches(String.format(Locale.ROOT,
                            "V%03d__[a-z][a-z0-9]*(?:-[a-z0-9]+)*[.]sql", version))
                    || scriptSha256 == null || !scriptSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid approved rules schema metadata");
            }
        }
    }
}
