package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Verifies the bounded, non-locking host overview read transaction. */
final class JdbcHostOverviewRepositoryTest {
    @Test
    void readsCampaignCharactersItemsChecksTopologyAndEncounterConsistently() throws Exception {
        Fixture fixture = new Fixture();

        HostOverviewRepository.Snapshot snapshot = repository(fixture)
                .findActive().orElseThrow();

        assertEquals("Campaign", snapshot.campaign().campaignName());
        assertEquals(1, snapshot.characters().size());
        assertEquals(4L, snapshot.characters().getFirst().currentHp());
        assertEquals(1, snapshot.items().size());
        assertEquals(1, snapshot.events().size());
        assertEquals(14, snapshot.events().getFirst().totalValue());
        assertEquals("node.entry", snapshot.map().partyNodeKey());
        assertEquals(2, snapshot.map().nodes().size());
        assertEquals(1, snapshot.map().connections().size());
        assertEquals("ACTIVE", snapshot.encounter().battleStatus());
        assertEquals("ALLY", snapshot.encounter().participants().getFirst().faction());
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.closed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
        assertTrue(fixture.preparedSql.stream().noneMatch(sql -> sql.contains("FOR UPDATE")));
        assertEquals(20, fixture.eventMaxRows);
    }

    @Test
    void freshRuntimeReconstructsTheCompleteAuthoritativeOverviewFromTheDatabase()
            throws Exception {
        Fixture firstRuntime = new Fixture();
        HostOverviewRepository.Snapshot beforeRestart = repository(firstRuntime)
                .findActive().orElseThrow();

        // A new repository and DataSource fixture model a fresh Tomcat lifecycle. No value from
        // the first JVM-side projection is supplied to the second read.
        Fixture restartedRuntime = new Fixture();
        HostOverviewRepository.Snapshot restored = repository(restartedRuntime)
                .findActive().orElseThrow();

        assertEquals(beforeRestart, restored);
        assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                restored.campaign().campaignKey());
        assertEquals(1, restored.characters().size());
        assertEquals(1, restored.items().size());
        assertEquals(1, restored.events().size());
        assertEquals("The lock opens.", restored.events().getFirst().eventText());
        assertTrue(restored.map().instantiated());
        assertEquals("node.entry", restored.map().partyNodeKey());
        assertEquals("ACTIVE", restored.encounter().battleStatus());
        assertEquals(1, restored.encounter().participants().size());
        assertEquals(9, restartedRuntime.preparedSql.size());
        assertTrue(restartedRuntime.committed);
        assertFalse(restartedRuntime.rolledBack);
    }

    @Test
    void returnsEmptyWithoutReadingCampaignPrivateChildren() throws Exception {
        Fixture fixture = new Fixture();
        fixture.noCampaign = true;

        Optional<HostOverviewRepository.Snapshot> result =
                repository(fixture).findActive();

        assertTrue(result.isEmpty());
        assertEquals(1, fixture.preparedSql.size());
        assertTrue(fixture.committed);
    }

    @Test
    void rejectsMultipleActiveCampaignsAndRollsBack() {
        Fixture fixture = new Fixture();
        fixture.duplicateCampaign = true;

        assertThrows(SQLException.class, () -> repository(fixture).findActive());

        assertFalse(fixture.committed);
        assertTrue(fixture.rolledBack);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
    }

    @Test
    void rollsBackAndRestoresConnectionWhenAChildReadFails() {
        Fixture fixture = new Fixture();
        fixture.failQuery = "module_map_connection";

        assertThrows(SQLException.class, () -> repository(fixture).findActive());

        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
        assertTrue(fixture.closed);
    }

    private static JdbcHostOverviewRepository repository(Fixture fixture) {
        return new JdbcHostOverviewRepository(fixture.dataSource());
    }

    private static final class Fixture {
        private final List<String> preparedSql = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean committed;
        private boolean rolledBack;
        private boolean closed;
        private boolean noCampaign;
        private boolean duplicateCampaign;
        private String failQuery;
        private int eventMaxRows;

        private DataSource dataSource() {
            return proxy(DataSource.class, (ignored, method, arguments) ->
                    "getConnection".equals(method.getName())
                            ? connection() : defaultValue(method.getReturnType()));
        }

        private Connection connection() {
            return proxy(Connection.class, this::connectionCall);
        }

        private Object connectionCall(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "isReadOnly" -> readOnly;
                case "getTransactionIsolation" -> isolation;
                case "setAutoCommit" -> {
                    autoCommit = (boolean) arguments[0];
                    yield null;
                }
                case "setReadOnly" -> {
                    readOnly = (boolean) arguments[0];
                    yield null;
                }
                case "setTransactionIsolation" -> {
                    isolation = (int) arguments[0];
                    yield null;
                }
                case "prepareStatement" -> statement((String) arguments[0]);
                case "commit" -> {
                    committed = true;
                    yield null;
                }
                case "rollback" -> {
                    rolledBack = true;
                    yield null;
                }
                case "close" -> {
                    closed = true;
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql) {
            preparedSql.add(sql);
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setLong", "setString", "setQueryTimeout" -> null;
                        case "setMaxRows" -> {
                            if (sql.contains("FROM game_event AS event_root")) {
                                eventMaxRows = (int) arguments[0];
                            }
                            yield null;
                        }
                        case "executeQuery" -> {
                            if (failQuery != null && sql.contains(failQuery)) {
                                throw new SQLException("synthetic overview read failure");
                            }
                            yield query(sql);
                        }
                        case "executeUpdate" -> throw new AssertionError(
                                "Overview repository must never write");
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet query(String sql) {
            if (sql.contains("FROM campaign AS campaign_root")) {
                if (noCampaign) return rows(List.of());
                List<Map<String, Object>> result = new ArrayList<>();
                result.add(campaignRow());
                if (duplicateCampaign) result.add(campaignRow());
                return rows(result);
            }
            if (sql.contains("GROUP BY character_root.id")) {
                return rows(List.of(row(
                        "id", 41L,
                        "character_key", "11111111-1111-4111-8111-111111111111",
                        "character_type", "NPC",
                        "character_name", "Guard",
                        "character_status", "ACTIVE",
                        "row_version", 3L,
                        "current_hp", 4L,
                        "maximum_hp", 11L,
                        "armor_class", 16L,
                        "item_count", 1)));
            }
            if (sql.contains("FROM item_instance AS owned_item")) {
                return rows(List.of(row(
                        "character_key", "11111111-1111-4111-8111-111111111111",
                        "character_name", "Guard",
                        "source_kind", "MODULE",
                        "item_key", "item.backpack",
                        "item_name", "Backpack",
                        "item_description", "Useful",
                        "quantity", 1,
                        "item_status", "ACTIVE")));
            }
            if (sql.contains("FROM game_event AS event_root")) {
                return rows(List.of(row(
                        "event_sequence", 9L,
                        "event_type", "CHECK_EXECUTED",
                        "event_text", "The lock opens.",
                        "subject_name", "Guard",
                        "check_key", "check.skill",
                        "manual_name", null,
                        "roll_mode_key", "roll.normal",
                        "modifier_source_key", "skill.perception",
                        "modifier_value", 4,
                        "total_value", 14,
                        "difficulty_class", 12,
                        "check_result", "SUCCESS")));
            }
            if (sql.contains("FROM module_map_definition")) {
                return rows(List.of(row("map_type", "NODE")));
            }
            if (sql.contains("FROM module_map_connection AS connection_root")) {
                return rows(List.of(row(
                        "endpoint_low_key", "node.cellar",
                        "low_name", "Cellar",
                        "endpoint_high_key", "node.entry",
                        "high_name", "Entry")));
            }
            if (sql.contains("FROM module_map_node")) {
                return rows(List.of(
                        row("node_key", "node.cellar", "display_name", "Cellar"),
                        row("node_key", "node.entry", "display_name", "Entry")));
            }
            if (sql.contains("FROM map_instance AS map_instance_root")) {
                return rows(List.of(row(
                        "id", 51L,
                        "party_node_key", "node.entry",
                        "battle_id", 61L,
                        "battle_status", "ACTIVE")));
            }
            if (sql.contains("FROM battle_participant AS participant")) {
                return rows(List.of(row(
                        "character_key", "11111111-1111-4111-8111-111111111111",
                        "character_name", "Guard",
                        "character_type", "NPC",
                        "faction", "ALLY",
                        "node_key", "node.cellar",
                        "node_name", "Cellar")));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        private static Map<String, Object> campaignRow() {
            return row(
                    "id", 7L,
                    "campaign_key", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    "campaign_name", "Campaign",
                    "campaign_status", "ACTIVE",
                    "row_version", 0L,
                    "host_state_epoch", 0L,
                    "module_release_id", 11L,
                    "frozen_module_key", "dnd5e2014_srd51_se_v1",
                    "frozen_release_version", "1",
                    "frozen_content_sha256", "a".repeat(64),
                    "module_key", "dnd5e2014_srd51_se_v1",
                    "release_version", "1",
                    "content_sha256", "a".repeat(64),
                    "release_status", "RELEASED");
        }
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }

    private static ResultSet rows(List<Map<String, Object>> values) {
        int[] index = {-1};
        boolean[] wasNull = {false};
        return proxy(ResultSet.class, (ignored, method, arguments) ->
                switch (method.getName()) {
                    case "next" -> ++index[0] < values.size();
                    case "getString" -> {
                        Object value = values.get(index[0]).get(arguments[0]);
                        wasNull[0] = value == null;
                        yield value == null ? null : value.toString();
                    }
                    case "getLong" -> {
                        Object value = values.get(index[0]).get(arguments[0]);
                        wasNull[0] = value == null;
                        yield value == null ? 0L : ((Number) value).longValue();
                    }
                    case "getInt" -> {
                        Object value = values.get(index[0]).get(arguments[0]);
                        wasNull[0] = value == null;
                        yield value == null ? 0 : ((Number) value).intValue();
                    }
                    case "wasNull" -> wasNull[0];
                    default -> defaultValue(method.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class || type == short.class || type == int.class || type == long.class) {
            return 0;
        }
        if (type == float.class || type == double.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
}
