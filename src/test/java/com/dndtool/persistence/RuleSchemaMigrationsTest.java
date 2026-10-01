package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RuleSchemaMigrationsTest {
    static final Path SOURCE = Path.of("database/rules/migration/V001__rule-source-schema.sql");

    @Test
    void independentApprovedChainMatchesExactlyTheOfflineSources() throws Exception {
        var chain = RuleSchemaMigrations.expectations();
        try (var files = Files.list(SOURCE.getParent())) {
            assertEquals(chain.stream().map(RuleSchemaMigrations.Expectation::scriptName).sorted().toList(),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
        for (int i = 0; i < chain.size(); i++) {
            var migration = chain.get(i);
            assertEquals("RULES", migration.schemaRole());
            assertEquals(i + 1, migration.version());
            assertEquals(migration.scriptSha256(), RuleSchemaMigrations.canonicalPayloadSha256(
                    Files.readAllBytes(SOURCE.getParent().resolve(migration.scriptName()))));
            Path current=SOURCE.getParent().resolve(migration.scriptName());
            assertTrue(Files.readString(current).substring(Files.readString(current)
                    .indexOf("-- CHECKSUM-SCOPE-END")).contains("'" + migration.scriptSha256() + "'"));
        }
        assertThrows(UnsupportedOperationException.class, () -> chain.clear());
        assertEquals(20, SchemaMigrations.loadExpectations().size());
        assertNull(getClass().getResource("/database/rules/migration/" + SOURCE.getFileName()));
        assertNull(getClass().getResource("/db/migration/" + SOURCE.getFileName()));
    }

    @Test
    void strictUtf8AndUnambiguousMarkersAreRequired() throws Exception {
        assertThrows(CharacterCodingException.class,
                () -> RuleSchemaMigrations.canonicalPayloadSha256(new byte[] {(byte) 0xc3, 0x28}));
        String valid = "-- CHECKSUM-SCOPE-BEGIN\nSELECT 1;\n-- CHECKSUM-SCOPE-END\n";
        String digest = hash(valid);
        assertEquals("17db4fd369edb9244b9f91d9aeed145c3d04ad8ba6e95d06247f07a63527d11a", digest);
        assertEquals(digest, hash(valid.replace("\n", "\r\n")));
        assertEquals(digest, hash(valid.replace("\n", "\r")));
        assertEquals(digest, hash(valid + "outside payload"));
        assertNotEquals(digest, hash(valid.replace("SELECT 1", "SELECT 2")));
        for (String bad : List.of("", valid + "-- CHECKSUM-SCOPE-BEGIN", valid + "-- CHECKSUM-SCOPE-END",
                "-- CHECKSUM-SCOPE-END\n-- CHECKSUM-SCOPE-BEGIN\n", valid.replace("BEGIN\n", "BEGIN "),
                "prefix " + valid, valid.replace("END\n", "END extra\n"),
                "-- CHECKSUM-SCOPE-BEGIN\n-- CHECKSUM-SCOPE-END\n")) {
            assertThrows(SchemaMigrations.PackagedSchemaException.class, () -> hash(bad));
        }
    }

    @Test
    void roleAndFilenameCannotMixWithTheRuntimeChain() {
        var good = RuleSchemaMigrations.expectations().getFirst();
        for (String role : List.of("RUNTIME", "rules", "RULES ")) {
            assertThrows(IllegalArgumentException.class, () -> new RuleSchemaMigrations.Expectation(
                    role, 1, good.scriptName(), good.scriptSha256()));
        }
        for (String name : List.of("V002__rule-source-schema.sql", "../V001__rule-source-schema.sql",
                "V001__rule_source_schema.sql", "V1__rule-source-schema.sql", "V0001__rule-source-schema.sql",
                "V001__" + "a".repeat(246) + ".sql")) {
            assertThrows(IllegalArgumentException.class, () -> new RuleSchemaMigrations.Expectation(
                    "RULES", 1, name, good.scriptSha256()));
        }
    }

    @Test
    void approvedNamesUseAsciiDigitsAndRespectTheLedgerByteLimit() {
        String digest = RuleSchemaMigrations.expectations().getFirst().scriptSha256();
        Locale previous = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar-EG"));
            for (int version : new int[] {1, 999, 1000, Integer.MAX_VALUE}) {
                String digits = Integer.toString(version);
                String name = "V" + "0".repeat(Math.max(0, 3 - digits.length())) + digits + "__schema.sql";
                assertEquals(name, new RuleSchemaMigrations.Expectation("RULES", version, name, digest).scriptName());
            }
            String name = "V001__" + "a".repeat(245) + ".sql";
            assertEquals(255, new RuleSchemaMigrations.Expectation("RULES", 1, name, digest).scriptName().length());
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous);
        }
    }

    private static String hash(String text) throws Exception {
        return RuleSchemaMigrations.canonicalPayloadSha256(text.getBytes(StandardCharsets.UTF_8));
    }
}
