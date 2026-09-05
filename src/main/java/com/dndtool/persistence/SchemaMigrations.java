package com.dndtool.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Loads the ordered, approved schema migration chain packaged in the application. */
final class SchemaMigrations {
    static final int V001_VERSION = 1;
    static final String V001_SCRIPT_NAME = "V001__stage1_schema.sql";
    static final String V001_APPROVED_SHA256 =
            "29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644";
    static final int V002_VERSION = 2;
    static final String V002_SCRIPT_NAME = "V002__stage2_module_schema.sql";
    static final String V002_APPROVED_SHA256 =
            "55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8";
    static final int V003_VERSION = 3;
    static final String V003_SCRIPT_NAME = "V003__builtin_module_draft.sql";
    static final String V003_APPROVED_SHA256 =
            "2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225";
    static final int V004_VERSION = 4;
    static final String V004_SCRIPT_NAME = "V004__release_builtin_module.sql";
    static final String V004_APPROVED_SHA256 =
            "5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f";
    static final int V005_VERSION = 5;
    static final String V005_SCRIPT_NAME = "V005__stage2_character_event_schema.sql";
    static final String V005_APPROVED_SHA256 =
            "d4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6";
    static final int V006_VERSION = 6;
    static final String V006_SCRIPT_NAME = "V006__stage2_character_field_value_schema.sql";
    static final String V006_APPROVED_SHA256 =
            "f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b";
    static final int V007_VERSION = 7;
    static final String V007_SCRIPT_NAME = "V007__character_creation_idempotency.sql";
    static final String V007_APPROVED_SHA256 =
            "01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef";
    static final int V008_VERSION = 8;
    static final String V008_SCRIPT_NAME = "V008__stage2_simple_item_schema.sql";
    static final String V008_APPROVED_SHA256 =
            "369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709";
    static final int V009_VERSION = 9;
    static final String V009_SCRIPT_NAME = "V009__stage3_check_execution_schema.sql";
    static final String V009_APPROVED_SHA256 =
            "e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c";
    static final int V010_VERSION = 10;
    static final String V010_SCRIPT_NAME = "V010__stage3_node_encounter_schema.sql";
    static final String V010_APPROVED_SHA256 =
            "b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87";
    static final int V011_VERSION = 11;
    static final String V011_SCRIPT_NAME = "V011__complete_character_catalog_draft.sql";
    static final String V011_APPROVED_SHA256 =
            "0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb";
    static final int V012_VERSION = 12;
    static final String V012_SCRIPT_NAME = "V012__level_one_character_creation.sql";
    static final String V012_APPROVED_SHA256 =
            "56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2";
    static final int V013_VERSION = 13;
    static final String V013_SCRIPT_NAME = "V013__level_advancement_hit_dice.sql";
    static final String V013_APPROVED_SHA256 =
            "c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d";
    static final int V014_VERSION = 14;
    static final String V014_SCRIPT_NAME = "V014__class_feature_lifecycle.sql";
    static final String V014_APPROVED_SHA256 =
            "e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361";
    static final int V015_VERSION = 15;
    static final String V015_SCRIPT_NAME = "V015__multiclass_asi_feat_draft.sql";
    static final String V015_APPROVED_SHA256 =
            "550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd";
    static final int V016_VERSION = 16;
    static final String V016_SCRIPT_NAME = "V016__starting_proficiency_baseline_draft.sql";
    static final String V016_APPROVED_SHA256 =
            "0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda";
    static final int V017_VERSION = 17;
    static final String V017_SCRIPT_NAME = "V017__character_archive_v2_origin.sql";
    static final String V017_APPROVED_SHA256 =
            "6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0";
    static final int V018_VERSION = 18;
    static final String V018_SCRIPT_NAME = "V018__multiclass_spell_slot_foundation.sql";
    static final String V018_APPROVED_SHA256 =
            "8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a";

    private static final String RESOURCE_DIRECTORY = "/db/migration/";
    private static final String SCOPE_BEGIN = "-- CHECKSUM-SCOPE-BEGIN";
    private static final String SCOPE_END = "-- CHECKSUM-SCOPE-END";

    /**
     * This list is the application schema contract. Add one entry for each reviewed
     * migration; never change an entry whose migration has already been applied.
     */
    private static final List<Definition> APPROVED_MIGRATIONS = List.of(
            new Definition(V001_VERSION, V001_SCRIPT_NAME, V001_APPROVED_SHA256),
            new Definition(V002_VERSION, V002_SCRIPT_NAME, V002_APPROVED_SHA256),
            new Definition(V003_VERSION, V003_SCRIPT_NAME, V003_APPROVED_SHA256),
            new Definition(V004_VERSION, V004_SCRIPT_NAME, V004_APPROVED_SHA256),
            new Definition(V005_VERSION, V005_SCRIPT_NAME, V005_APPROVED_SHA256),
            new Definition(V006_VERSION, V006_SCRIPT_NAME, V006_APPROVED_SHA256),
            new Definition(V007_VERSION, V007_SCRIPT_NAME, V007_APPROVED_SHA256),
            new Definition(V008_VERSION, V008_SCRIPT_NAME, V008_APPROVED_SHA256),
            new Definition(V009_VERSION, V009_SCRIPT_NAME, V009_APPROVED_SHA256),
            new Definition(V010_VERSION, V010_SCRIPT_NAME, V010_APPROVED_SHA256),
            new Definition(V011_VERSION, V011_SCRIPT_NAME, V011_APPROVED_SHA256),
            new Definition(V012_VERSION, V012_SCRIPT_NAME, V012_APPROVED_SHA256),
            new Definition(V013_VERSION, V013_SCRIPT_NAME, V013_APPROVED_SHA256),
            new Definition(V014_VERSION, V014_SCRIPT_NAME, V014_APPROVED_SHA256),
            new Definition(V015_VERSION, V015_SCRIPT_NAME, V015_APPROVED_SHA256),
            new Definition(V016_VERSION, V016_SCRIPT_NAME, V016_APPROVED_SHA256),
            new Definition(V017_VERSION, V017_SCRIPT_NAME, V017_APPROVED_SHA256),
            new Definition(V018_VERSION, V018_SCRIPT_NAME, V018_APPROVED_SHA256));

    private SchemaMigrations() {
    }

    static List<Expectation> loadExpectations() throws PackagedSchemaException {
        validateManifest();

        List<Expectation> expectations = new ArrayList<>(APPROVED_MIGRATIONS.size());
        for (Definition definition : APPROVED_MIGRATIONS) {
            String actualSha256 = canonicalPayloadSha256(loadSql(definition.scriptName()));
            if (!definition.approvedSha256().equals(actualSha256)) {
                throw new PackagedSchemaException();
            }
            expectations.add(new Expectation(
                    definition.version(), definition.scriptName(), actualSha256));
        }
        return List.copyOf(expectations);
    }

    private static String loadSql(String scriptName) throws PackagedSchemaException {
        try (InputStream input = SchemaMigrations.class.getResourceAsStream(
                RESOURCE_DIRECTORY + scriptName)) {
            if (input == null) {
                throw new PackagedSchemaException();
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new PackagedSchemaException();
        }
    }

    /** Rejects accidental gaps, reordered versions, unsafe names and malformed approved hashes. */
    private static void validateManifest() throws PackagedSchemaException {
        if (APPROVED_MIGRATIONS.isEmpty()) {
            throw new PackagedSchemaException();
        }

        for (int index = 0; index < APPROVED_MIGRATIONS.size(); index++) {
            Definition definition = APPROVED_MIGRATIONS.get(index);
            int expectedVersion = index + 1;
            String expectedPrefix = "V%03d__".formatted(expectedVersion);
            boolean invalid = definition.version() != expectedVersion
                    || !definition.scriptName().startsWith(expectedPrefix)
                    || !definition.scriptName().endsWith(".sql")
                    || definition.scriptName().contains("/")
                    || definition.scriptName().contains("\\")
                    || !definition.approvedSha256().matches("[0-9a-f]{64}");
            if (invalid) {
                throw new PackagedSchemaException();
            }
        }
    }

    /**
     * Hashes only the text between the checksum markers. Line endings are normalized so a
     * checkout using CRLF and one using LF describe the same migration.
     */
    static String canonicalPayloadSha256(String rawSql) throws PackagedSchemaException {
        String normalized = rawSql.replace("\r\n", "\n").replace('\r', '\n');
        int begin = normalized.indexOf(SCOPE_BEGIN);
        int end = normalized.indexOf(SCOPE_END);

        boolean markersInvalid = begin < 0
                || end <= begin
                || normalized.indexOf(SCOPE_BEGIN, begin + SCOPE_BEGIN.length()) >= 0
                || normalized.indexOf(SCOPE_END, end + SCOPE_END.length()) >= 0;
        if (markersInvalid) {
            throw new PackagedSchemaException();
        }

        int payloadStart = begin + SCOPE_BEGIN.length();
        if (payloadStart >= normalized.length()
                || normalized.charAt(payloadStart) != '\n'
                || end == 0
                || normalized.charAt(end - 1) != '\n') {
            throw new PackagedSchemaException();
        }

        // Exclude the line breaks adjacent to the markers from the approved payload.
        String payload = normalized.substring(payloadStart + 1, end - 1);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by every supported Java runtime.
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Definition(int version, String scriptName, String approvedSha256) {
    }

    record Expectation(int version, String scriptName, String scriptSha256) {
    }

    /** Deliberately carries no file contents or paths that could leak through a response. */
    static final class PackagedSchemaException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
