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
            new Expectation(SCHEMA_ROLE, 2, "V002__tool-catalog.sql", "2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40"));

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
