package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CheckFixtures;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2CheckSourceRepositoryTest {
    @Test void originalTypedRuleAndEveryOverrideRoundTripAgainstTheExactZip() throws Exception {
        var artifacts = Schema2CheckFixtures.artifacts(); var fixture = stored();
        var row = fixture.rows("rule_schema2_check").getFirst();
        assertEquals(77, row.get("source_page")); assertEquals("Ability check", row.get("display_name"));
        assertArrayEquals(ascii("adventure.check"), (byte[]) row.get("rule_type"));
        assertArrayEquals(ascii("SRD_2014_ABILITY_CHECK_V1"), (byte[]) row.get("algorithm"));
        assertEquals(5, fixture.rows("rule_schema2_advancement_change").size());
        JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection, artifacts);
        assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
        var source = new Schema2SourceReadFixture(artifacts);
        assertEquals(2, source.load(source.selections()).artifacts().size());
    }
    @Test void everyTypedValueMissingExtraAndUnselectedCorruptionFailsClosed() throws Exception {
        for (String table : List.of("rule_schema2_check", "rule_schema2_advancement_change")) {
            for (String column : stored().rows(table).getFirst().keySet()) {
                var fixture = stored(); var row = fixture.rows(table).getFirst(); Object value = row.get(column);
                row.put(column, value instanceof byte[] bytes ? ascii(new String(bytes, StandardCharsets.US_ASCII) + "x") : value instanceof Integer n ? n + 1 : "wrong");
                assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(fixture.connection, Schema2CheckFixtures.artifacts()), column);
            }
            var missing = stored(); missing.rows(table).removeFirst();
            assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(missing.connection, Schema2CheckFixtures.artifacts()));
            var extra = stored(); extra.rows(table).add(new LinkedHashMap<>(extra.rows(table).getFirst()));
            assertThrows(SQLException.class, () -> JdbcSchema2CharacterSourceRepository.verifyAll(extra.connection, Schema2CheckFixtures.artifacts()));
        }
        var unselected = new Schema2SourceReadFixture(Schema2CheckFixtures.artifacts());
        unselected.rows().get("rule_schema2_advancement_change").getFirst().put("text_value", "unselected corruption");
        assertThrows(SQLException.class, () -> unselected.load(unselected.selections().subList(0, 1)));
        assertEquals(0, unselected.commits); assertEquals(1, unselected.rollbacks);
    }
    @Test void failedCheckMaterialWriteRemainsWithinTheCallerTransaction() throws Exception {
        int count = stored().writes;
        for (int failure = 1; failure <= count; failure++) {
            var fixture = new Schema2RuntimeJdbcFixture(); fixture.failWrite = failure;
            assertThrows(SQLException.class, () -> { for (var artifact : Schema2CheckFixtures.artifacts()) JdbcSchema2CharacterSourceRepository.store(fixture.connection, artifact); });
            assertEquals(0, fixture.commits); assertEquals(0, fixture.rollbacks);
            fixture.connection.rollback(); assertTrue(fixture.tables.isEmpty());
        }
    }
    private static Schema2RuntimeJdbcFixture stored() throws Exception {
        var fixture = new Schema2RuntimeJdbcFixture();
        for (var artifact : Schema2CheckFixtures.artifacts()) JdbcSchema2CharacterSourceRepository.store(fixture.connection, artifact);
        return fixture;
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
}
