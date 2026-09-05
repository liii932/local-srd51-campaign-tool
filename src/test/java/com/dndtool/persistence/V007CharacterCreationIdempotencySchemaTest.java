package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class V007CharacterCreationIdempotencySchemaTest {
    private static final String RESOURCE =
            "/db/migration/V007__character_creation_idempotency.sql";

    @Test
    void addsOnlyNullableCharacterResultReferenceAndMetadata() throws Exception {
        String sql = new String(
                getClass().getResourceAsStream(RESOURCE).readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(sql.contains("ADD COLUMN `character_id` BIGINT UNSIGNED NULL"));
        assertTrue(sql.contains("CONSTRAINT `fk_host_operation_character`"));
        assertTrue(sql.contains("REFERENCES `character_record` (`id`)"));
        assertTrue(sql.contains("'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef'"));
        assertFalse(sql.matches("(?ims).*^\\s*USE\\s+.*"));
        assertFalse(sql.matches("(?is).*INSERT\s+INTO\s+`character_record`.*"));
        assertFalse(sql.matches("(?is).*DELETE\s+FROM.*"));
        assertFalse(sql.matches("(?is).*GRANT\s+.*"));
    }
}
