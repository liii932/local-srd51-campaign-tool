package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.dndtool.module.CharacterProficiencyGrant;
import com.dndtool.service.CharacterProficiencyArchiveTestData;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JdbcCharacterProficiencyGrantRepositoryTest {
    @Test
    void writesAndReadsEveryProvenanceColumnInsideCallerTransaction() throws Exception {
        Fixture fixture = new Fixture();
        var grant = CharacterProficiencyArchiveTestData.grants().getFirst();
        new JdbcCharacterProficiencyGrantRepository().append(fixture.connection(), 11, 9, grant);
        assertEquals(Map.ofEntries(Map.entry(1, 11L), Map.entry(2, 9L), Map.entry(3, "LANGUAGE"),
                Map.entry(4, "dnd5e2014_srd51_se"), Map.entry(5, "character.race"),
                Map.entry(6, "race.human"), Map.entry(7, "creation.language.fixed"),
                Map.entry(8, "FIXED"), Map.entry(9, "dnd5e2014_srd51_se"),
                Map.entry(10, "character.language"), Map.entry(11, "language.common"),
                Map.entry(12, false)), fixture.writes.getFirst());
        fixture.rows.add(row());
        assertEquals(List.of(grant), new JdbcCharacterProficiencyGrantRepository()
                .read(fixture.connection(), 11, 9));
        assertEquals(Map.of(1, 11L, 2, 9L), fixture.lastReadParameters);
    }

    @Test
    void rejectsMalformedReadbackDuplicateIdentityAndMissingCallerTransaction() throws Exception {
        for (Map<String, Object> malformed : List.of(
                changed("proficiency_type", "character.tool"), changed("is_suppressed", 2),
                changed("grant_kind", "SCRIPT"), changed("source_key", "race.unknown!"))) {
            Fixture fixture = new Fixture();
            fixture.rows.add(malformed);
            assertThrows(SQLException.class, () -> new JdbcCharacterProficiencyGrantRepository()
                    .read(fixture.connection(), 11, 9));
        }
        Fixture duplicate = new Fixture();
        duplicate.rows.add(row());
        duplicate.rows.add(row());
        assertThrows(SQLException.class, () -> new JdbcCharacterProficiencyGrantRepository()
                .read(duplicate.connection(), 11, 9));
        Fixture autoCommit = new Fixture();
        autoCommit.autoCommit = true;
        assertThrows(SQLException.class, () -> new JdbcCharacterProficiencyGrantRepository()
                .append(autoCommit.connection(), 11, 9, CharacterProficiencyArchiveTestData.grants().getFirst()));
        assertTrue(autoCommit.writes.isEmpty());
    }

    private static Map<String, Object> row() {
        return new HashMap<>(Map.of("category", "LANGUAGE", "source_module_key", "dnd5e2014_srd51_se",
                "source_type", "character.race", "source_key", "race.human",
                "grant_slot_key", "creation.language.fixed", "grant_kind", "FIXED",
                "target_module_key", "dnd5e2014_srd51_se", "proficiency_type", "character.language",
                "proficiency_key", "language.common", "is_suppressed", 0));
    }

    private static Map<String, Object> changed(String key, Object value) {
        var row = row();
        row.put(key, value);
        return row;
    }

    private static final class Fixture {
        boolean autoCommit;
        final List<Map<String, Object>> rows = new ArrayList<>();
        final List<Map<Integer, Object>> writes = new ArrayList<>();
        Map<Integer, Object> lastReadParameters;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "prepareStatement" -> statement();
                        case "commit", "rollback" -> throw new AssertionError("Repository owns no transaction");
                        default -> value(method.getReturnType());
                    });
        }

        private PreparedStatement statement() {
            Map<Integer, Object> params = new HashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "setLong", "setString", "setBoolean" -> { params.put((Integer) args[0], args[1]); yield null; }
                        case "executeUpdate" -> { writes.add(Map.copyOf(params)); yield 1; }
                        case "executeQuery" -> { lastReadParameters = Map.copyOf(params); yield resultSet(); }
                        default -> value(method.getReturnType());
                    });
        }

        private ResultSet resultSet() {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {ResultSet.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "next" -> ++index[0] < rows.size();
                        case "wasNull" -> wasNull[0];
                        case "getString", "getInt" -> {
                            Object cell = rows.get(index[0]).get(args[0]);
                            wasNull[0] = cell == null;
                            yield method.getName().equals("getString") ? cell == null ? null : cell.toString()
                                    : cell == null ? 0 : ((Number) cell).intValue();
                        }
                        default -> value(method.getReturnType());
                    });
        }

        private static Object value(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
