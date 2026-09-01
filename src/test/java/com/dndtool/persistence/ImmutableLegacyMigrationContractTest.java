package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Keeps the already-applied V001-V010 identities and approved payloads immutable. */
final class ImmutableLegacyMigrationContractTest {
    private static final List<String> LEGACY_SCRIPTS = List.of(
            "V001__stage1_schema.sql",
            "V002__stage2_module_schema.sql",
            "V003__builtin_module_draft.sql",
            "V004__release_builtin_module.sql",
            "V005__stage2_character_event_schema.sql",
            "V006__stage2_character_field_value_schema.sql",
            "V007__character_creation_idempotency.sql",
            "V008__stage2_simple_item_schema.sql",
            "V009__stage3_check_execution_schema.sql",
            "V010__stage3_node_encounter_schema.sql");

    @Test
    void approvedLegacyPrefixRemainsExactlyV001ThroughV010() throws Exception {
        List<SchemaMigrations.Expectation> expectations =
                SchemaMigrations.loadExpectations();

        assertEquals(LEGACY_SCRIPTS,
                expectations.subList(0, 10).stream().map(
                        SchemaMigrations.Expectation::scriptName).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                expectations.subList(0, 10).stream().map(
                        SchemaMigrations.Expectation::version).toList());
    }
}
