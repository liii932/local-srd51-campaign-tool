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
public final class SchemaMigrations {
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
    static final int V019_VERSION = 19;
    static final String V019_SCRIPT_NAME = "V019__runtime-language-snapshot.sql";
    static final String V019_APPROVED_SHA256 =
            "6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173";
    static final int V020_VERSION = 20;
    static final String V020_SCRIPT_NAME = "V020__runtime-tool-snapshot.sql";
    static final String V020_APPROVED_SHA256 =
            "cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1";

    static final int V021_VERSION = 21;
    static final String V021_SCRIPT_NAME = "V021__schema2-runtime-snapshot.sql";
    static final String V021_APPROVED_SHA256 = "4c3d2b1e56fc9cb25657c780771953cae305a71428879f970003752c16a00b99";

    static final int V022_VERSION = 22;
    static final String V022_SCRIPT_NAME = "V022__character-proficiency-grants.sql";
    static final String V022_APPROVED_SHA256 = "9debf26264a286cf659a37645ecfef031467b02f0d336391678c3984fa66f25b";

    static final int V023_VERSION = 23;
    static final String V023_SCRIPT_NAME = "V023__schema2-runtime-tools.sql";
    static final String V023_APPROVED_SHA256 = "7451618776976c30cffcff44fd224a2ed83e1644a5ce1e3ed2dd995b8c2b87e0";

    static final int V024_VERSION = 24;
    static final String V024_SCRIPT_NAME = "V024__schema2-character-grants.sql";
    static final String V024_APPROVED_SHA256 = "8920967f4aff141ef8dca0bd8235376126091fe2ba25106fdbc825d589348c57";

    static final int V025_VERSION = 25;
    static final String V025_SCRIPT_NAME = "V025__business-write-guard.sql";
    static final String V025_APPROVED_SHA256 = "cc92de95ac0385208160f58f22d824c654dfef80edbb6151e0220dc882deed33";

    static final int V026_VERSION = 26;
    static final String V026_SCRIPT_NAME = "V026__schema2-initial-plans.sql";
    static final String V026_APPROVED_SHA256 = "06c84d420a59cd39ed35e0c380d8e1378c5c4c1d724f7598471123f6e11bb2fa";

    static final int V027_VERSION = 27;
    static final String V027_SCRIPT_NAME = "V027__schema2-character-runtime.sql";
    static final String V027_APPROVED_SHA256 = "157fb1cebe884e561cd4398a54d7e8ea5713f87bfb3166fd89d6c708c9799b4f";

    static final int V028_VERSION = 28;
    static final String V028_SCRIPT_NAME = "V028__schema2-check-rules.sql";
    static final String V028_APPROVED_SHA256 = "9f19b34938db2afeada93feaeaf6e08340f4f7a02819dc65c68e24350277917a";

    static final int V029_VERSION = 29;
    static final String V029_SCRIPT_NAME = "V029__schema2-character-check-history.sql";
    static final String V029_APPROVED_SHA256 = "f08a4982351a5719418abf27402d0d6bf470d6072f219de0688ef5b5fe2d6c9b";

    static final int V030_VERSION = 30;
    static final String V030_SCRIPT_NAME = "V030__schema2-equipment-health-rules.sql";
    static final String V030_APPROVED_SHA256 = "b41315a2e92c0171c1326a6115ab93d10a1e1f80dff40c23f344e0e893821bc3";

    static final int V031_VERSION = 31;
    static final String V031_SCRIPT_NAME = "V031__schema2-equipment-health-history.sql";
    static final String V031_APPROVED_SHA256 = "1b9f8959ad5c3d6e4b417d2672a7a06cf8df572e9059e4addfde01856e57b79e";

    static final int V032_VERSION = 32;
    static final String V032_SCRIPT_NAME = "V032__schema2-combat-rules.sql";
    static final String V032_APPROVED_SHA256 = "e1bc7521e953b640b42c869f734961d8e9153e2036788468c9ea13db39be5cae";

    static final int V033_VERSION = 33;
    static final String V033_SCRIPT_NAME = "V033__schema2-combat-runtime.sql";
    static final String V033_APPROVED_SHA256 = "df5ddd066e6694d836daf2891ead4271e5ae6e2ed1cb2bf13a009d75d72c83e7";

    static final int V034_VERSION = 34;
    static final String V034_SCRIPT_NAME = "V034__schema2-combat-time-ground.sql";
    static final String V034_APPROVED_SHA256 = "b9872fe91f87cf0d0c8cb2c16e955c6d0e4e566aed52d56071afa89217affe1f";

    static final int V035_VERSION = 35;
    static final String V035_SCRIPT_NAME = "V035__schema2-spell-rules.sql";
    static final String V035_APPROVED_SHA256 = "80765ab1aa82342d0d1d6ed54f38df6caa94b539ddf19d2ca3f4872fd5ecfdaf";

    static final int V036_VERSION = 36;
    static final String V036_SCRIPT_NAME = "V036__schema2-spell-runtime.sql";
    static final String V036_APPROVED_SHA256 = "d92de4fe6fd7b76abd3a8b4b63dce7ab98bb38352d714b9a34a61498b1f7b689";

    static final int V037_VERSION = 37;
    static final String V037_SCRIPT_NAME = "V037__schema2-wizard-environment-rules.sql";
    static final String V037_APPROVED_SHA256 = "7937263eb20e92adab82785aed6dead9ad99bd5fa46c941505c483bbc0f68cfb";

    static final int V038_VERSION = 38;
    static final String V038_SCRIPT_NAME = "V038__schema2-reactions-and-ongoing-scenes.sql";
    static final String V038_APPROVED_SHA256 = "3449378f943deea0fee5b3711c13f2a67fde7edaafe0646f6b650a513de37ee5";

    static final int V039_VERSION = 39;
    static final String V039_SCRIPT_NAME = "V039__schema2-monster-rules.sql";
    static final String V039_APPROVED_SHA256 = "1128fbaf4955634a04e206e40a5af0a3e532fc2035511d140ce53a3a9bd71ea4";

    static final int V040_VERSION = 40;
    static final String V040_SCRIPT_NAME = "V040__schema2-monster-breath-runtime.sql";
    static final String V040_APPROVED_SHA256 = "a9fa6c6c3afa348cccdf01c1786103ff94cd7ac8b663389242bd4b49555d6abf";

    static final int V041_VERSION = 41;
    static final String V041_SCRIPT_NAME = "V041__schema2-arcane-recovery.sql";
    static final String V041_APPROVED_SHA256 = "3de32aff220aadec90836612ef207c43bb6675b3e3e59cc5e81a2ca3ab7c1a71";

    static final int V042_VERSION = 42;
    static final String V042_SCRIPT_NAME = "V042__schema2-spell-save-damage.sql";
    static final String V042_APPROVED_SHA256 = "192321005586c993faa0bc8832d28a2dfb44e6674a47fb1db8406e284be12e8f";

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
            new Definition(V018_VERSION, V018_SCRIPT_NAME, V018_APPROVED_SHA256),
            new Definition(V019_VERSION, V019_SCRIPT_NAME, V019_APPROVED_SHA256),
            new Definition(V020_VERSION, V020_SCRIPT_NAME, V020_APPROVED_SHA256),
            new Definition(V021_VERSION, V021_SCRIPT_NAME, V021_APPROVED_SHA256),
            new Definition(V022_VERSION, V022_SCRIPT_NAME, V022_APPROVED_SHA256),
            new Definition(V023_VERSION, V023_SCRIPT_NAME, V023_APPROVED_SHA256),
            new Definition(V024_VERSION, V024_SCRIPT_NAME, V024_APPROVED_SHA256),
            new Definition(V025_VERSION, V025_SCRIPT_NAME, V025_APPROVED_SHA256),
            new Definition(V026_VERSION, V026_SCRIPT_NAME, V026_APPROVED_SHA256),
            new Definition(V027_VERSION, V027_SCRIPT_NAME, V027_APPROVED_SHA256),
            new Definition(V028_VERSION, V028_SCRIPT_NAME, V028_APPROVED_SHA256),
            new Definition(V029_VERSION, V029_SCRIPT_NAME, V029_APPROVED_SHA256),
            new Definition(V030_VERSION, V030_SCRIPT_NAME, V030_APPROVED_SHA256),
            new Definition(V031_VERSION, V031_SCRIPT_NAME, V031_APPROVED_SHA256),
            new Definition(V032_VERSION, V032_SCRIPT_NAME, V032_APPROVED_SHA256),
            new Definition(V033_VERSION, V033_SCRIPT_NAME, V033_APPROVED_SHA256),
            new Definition(V034_VERSION, V034_SCRIPT_NAME, V034_APPROVED_SHA256),
            new Definition(V035_VERSION, V035_SCRIPT_NAME, V035_APPROVED_SHA256),
            new Definition(V036_VERSION, V036_SCRIPT_NAME, V036_APPROVED_SHA256),
            new Definition(V037_VERSION, V037_SCRIPT_NAME, V037_APPROVED_SHA256),
            new Definition(V038_VERSION, V038_SCRIPT_NAME, V038_APPROVED_SHA256),
            new Definition(V039_VERSION, V039_SCRIPT_NAME, V039_APPROVED_SHA256),
            new Definition(V040_VERSION, V040_SCRIPT_NAME, V040_APPROVED_SHA256),
            new Definition(V041_VERSION, V041_SCRIPT_NAME, V041_APPROVED_SHA256),
            new Definition(V042_VERSION, V042_SCRIPT_NAME, V042_APPROVED_SHA256));

    private SchemaMigrations() {
    }

    public static List<Expectation> loadExpectations() throws PackagedSchemaException {
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

    public record Expectation(int version, String scriptName, String scriptSha256) {
    }

    /** Deliberately carries no file contents or paths that could leak through a response. */
    public static final class PackagedSchemaException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
