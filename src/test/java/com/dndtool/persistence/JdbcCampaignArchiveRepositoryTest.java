package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.module.BuiltinModuleHashManifest;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
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

/** Verifies the stable-key-only archive projection and its read transaction. */
final class JdbcCampaignArchiveRepositoryTest {
    private static final String CAMPAIGN_KEY =
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String CHARACTER_KEY =
            "11111111-1111-4111-8111-111111111111";
    private static final String SHA =
            BuiltinModuleHashManifest.DND5E2014_SRD51_SE_V1_SHA256;

    @Test
    void readsCompleteCurrentStateAndChronologicalBoundedDisplayContext()
            throws Exception {
        Fixture fixture = new Fixture();

        CampaignArchiveRepository.Snapshot snapshot = repository(fixture)
                .findByCampaignKey(CAMPAIGN_KEY).orElseThrow();

        assertEquals(CAMPAIGN_KEY, snapshot.campaign().campaignKey());
        assertEquals(1, snapshot.characters().size());
        assertEquals(4, snapshot.fields().size());
        assertEquals(new BigDecimal("12.500000000000000000"),
                snapshot.fields().get(2).decimalValue());
        assertEquals(1, snapshot.classLevels().size());
        assertEquals(1, snapshot.skillProficiencies().size());
        assertEquals(1, snapshot.saveProficiencies().size());
        assertEquals(2, snapshot.items().size());
        assertEquals("node.entry", snapshot.maps().getFirst().partyNodeKey());
        assertEquals("ALLY", snapshot.maps().getFirst().encounter()
                .participants().getFirst().faction());
        assertEquals(List.of(9L, 10L), snapshot.recentEvents().stream()
                .map(CampaignArchiveRepository.EventSnapshot::eventSequence).toList());
        assertEquals(50, fixture.eventMaxRows);
        assertTrue(fixture.preparedSql.stream().noneMatch(sql -> sql.contains("FOR UPDATE")));
        assertEquals(0, fixture.updateAttempts);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.closed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
    }

    @Test
    void returnsEmptyWithoutReadingAnyCampaignChildren() throws Exception {
        Fixture fixture = new Fixture();
        fixture.noCampaign = true;

        Optional<CampaignArchiveRepository.Snapshot> result =
                repository(fixture).findByCampaignKey(CAMPAIGN_KEY);

        assertTrue(result.isEmpty());
        assertEquals(1, fixture.preparedSql.size());
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void rejectsInvalidKeyBeforeObtainingAConnection() {
        DataSource unused = proxy(DataSource.class, (ignored, method, arguments) -> {
            throw new AssertionError("invalid key must not reach JDBC");
        });

        assertThrows(IllegalArgumentException.class,
                () -> new JdbcCampaignArchiveRepository(unused)
                        .findByCampaignKey("not-a-key"));
    }

    @Test
    void childReadFailureRollsBackAndRestoresPooledConnection() {
        Fixture fixture = new Fixture();
        fixture.failQuery = "character_field_value";

        assertThrows(SQLException.class,
                () -> repository(fixture).findByCampaignKey(CAMPAIGN_KEY));

        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
        assertTrue(fixture.closed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
    }

    @Test
    void invalidTypedFieldAndCrossReleaseItemFailClosed() {
        Fixture invalidField = new Fixture();
        invalidField.invalidTypedField = true;
        assertThrows(SQLException.class,
                () -> repository(invalidField).findByCampaignKey(CAMPAIGN_KEY));
        assertTrue(invalidField.rolledBack);

        Fixture wrongItemRelease = new Fixture();
        wrongItemRelease.wrongItemRelease = true;
        assertThrows(SQLException.class,
                () -> repository(wrongItemRelease).findByCampaignKey(CAMPAIGN_KEY));
        assertTrue(wrongItemRelease.rolledBack);
    }

    @Test
    void excludesIdempotencyDefinitionsAndFullHistoryFromArchiveReads() throws Exception {
        Fixture fixture = new Fixture();

        repository(fixture).findByCampaignKey(CAMPAIGN_KEY).orElseThrow();

        String sql = String.join("\n", fixture.preparedSql)
                .toLowerCase()
                .replaceAll("\\s+", " ");
        for (String forbiddenTable : List.of(
                "host_operation", "field_change", "dice_roll", "check_effect",
                "check_effect_parameter_value", "module_rule_constant",
                "module_field_definition", "module_class_definition",
                "module_skill_definition", "module_save_definition",
                "module_proficiency_tier", "module_entity_template",
                "module_entity_template_value", "module_item_template",
                "module_event_template", "module_event_check", "module_event_effect",
                "module_effect_definition", "module_effect_parameter",
                "module_map_definition", "module_map_node", "module_map_connection")) {
            assertFalse(sql.contains("from " + forbiddenTable)
                    || sql.contains("join " + forbiddenTable), forbiddenTable);
        }
        assertFalse(sql.contains("row_version"));
        assertFalse(sql.contains("host_state_epoch"));
        assertFalse(sql.contains("request_id"));
        assertFalse(sql.contains("request_digest"));
        for (String deferredOrSecret : List.of(
                "session", "csrf", "password", "credential", "private_key",
                "certificate", "tomcat", "local_path", "public_", "player",
                "owner", "approval", "member", "auth_token", "network_epoch")) {
            assertFalse(sql.contains(deferredOrSecret), deferredOrSecret);
        }
        assertTrue(sql.contains("order by event_root.event_sequence desc"));
        assertTrue(sql.contains("limit 50"));
        assertTrue(sql.contains("active_battle.battle_status = 'active'"));
        assertEquals(50, fixture.eventMaxRows);
    }

    private static JdbcCampaignArchiveRepository repository(Fixture fixture) {
        return new JdbcCampaignArchiveRepository(fixture.dataSource());
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
        private boolean invalidTypedField;
        private boolean wrongItemRelease;
        private String failQuery;
        private int eventMaxRows;
        private int updateAttempts;

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
                                throw new SQLException("synthetic archive read failure");
                            }
                            yield query(sql);
                        }
                        case "executeUpdate" -> {
                            updateAttempts++;
                            throw new AssertionError("archive repository must never write");
                        }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet query(String sql) {
            if (sql.contains("FROM campaign AS campaign_root")) {
                return noCampaign ? rows(List.of()) : rows(List.of(campaignRow()));
            }
            if (sql.contains("FROM character_record AS character_root")
                    && !sql.contains("JOIN")) {
                return rows(List.of(row(
                        "character_key", CHARACTER_KEY,
                        "character_type", "PC",
                        "character_name", "Aria",
                        "character_status", "ACTIVE",
                        "saved_module_key", "dnd5e2014_srd51_se_v1",
                        "saved_release_version", "1",
                        "saved_content_sha256", SHA)));
            }
            if (sql.contains("FROM character_field_value AS field_value")) {
                List<Map<String, Object>> values = new ArrayList<>();
                values.add(field("field.a_text", "TEXT", "备注", null, null, null));
                values.add(field("field.b_integer", "INTEGER", null, 12L, null, null));
                values.add(field("field.c_decimal", "DECIMAL", null, null,
                        new BigDecimal("12.500000000000000000"), null));
                values.add(field("field.d_boolean", "BOOLEAN", null, null, null, 1));
                if (invalidTypedField) values.getFirst().put("integer_value", 7L);
                return rows(values);
            }
            if (sql.contains("FROM character_class_level AS class_level")) {
                return rows(List.of(row(
                        "character_key", CHARACTER_KEY,
                        "class_key", "class.fighter",
                        "class_level", 2)));
            }
            if (sql.contains("FROM character_skill_proficiency AS proficiency")) {
                return rows(List.of(row(
                        "character_key", CHARACTER_KEY,
                        "target_key", "skill.perception",
                        "proficiency_key", "proficiency.full")));
            }
            if (sql.contains("FROM character_save_proficiency AS proficiency")) {
                return rows(List.of(row(
                        "character_key", CHARACTER_KEY,
                        "target_key", "save.strength",
                        "proficiency_key", "proficiency.full")));
            }
            if (sql.contains("FROM item_instance AS owned_item")) {
                return rows(List.of(
                        row(
                                "character_key", CHARACTER_KEY,
                                "source_kind", "MODULE",
                                "module_release_id", wrongItemRelease ? 12L : 11L,
                                "item_key", "item.backpack",
                                "item_name", "背包",
                                "item_description", "常用背包",
                                "quantity", 2,
                                "item_status", "ACTIVE"),
                        row(
                                "character_key", CHARACTER_KEY,
                                "source_kind", "TEMPORARY",
                                "module_release_id", null,
                                "item_key", null,
                                "item_name", "钥匙",
                                "item_description", "临时钥匙",
                                "quantity", 1,
                                "item_status", "ARCHIVED")));
            }
            if (sql.contains("FROM map_instance AS map_root")) {
                return rows(List.of(row(
                        "id", 51L,
                        "module_release_id", 11L,
                        "map_key", "map.tavern_cellar",
                        "map_type", "NODE",
                        "party_node_key", "node.entry",
                        "battle_id", 61L,
                        "battle_status", "ACTIVE")));
            }
            if (sql.contains("FROM battle_participant AS participant")) {
                return rows(List.of(row(
                        "character_key", CHARACTER_KEY,
                        "faction", "ALLY",
                        "node_key", "node.cellar")));
            }
            if (sql.contains("FROM game_event AS event_root")) {
                return rows(List.of(
                        row(
                                "event_sequence", 10L,
                                "event_type", "CHECK_EXECUTED",
                                "event_text", "锁已打开。",
                                "subject_character_key", CHARACTER_KEY,
                                "check_id", 71L,
                                "module_release_id", 11L,
                                "event_key", "event.open_lock",
                                "check_key", "check.skill",
                                "roll_mode_key", "roll.normal",
                                "modifier_source_key", "skill.perception",
                                "manual_name", null,
                                "modifier_value", 4,
                                "total_value", 14,
                                "difficulty_class", 12,
                                "check_result", "SUCCESS"),
                        row(
                                "event_sequence", 9L,
                                "event_type", "NOTE",
                                "event_text", "进入地窖。",
                                "subject_character_key", null,
                                "check_id", null,
                                "module_release_id", null,
                                "event_key", null,
                                "check_key", null,
                                "roll_mode_key", null,
                                "modifier_source_key", null,
                                "manual_name", null,
                                "modifier_value", null,
                                "total_value", null,
                                "difficulty_class", null,
                                "check_result", null)));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        private static Map<String, Object> campaignRow() {
            return row(
                    "id", 7L,
                    "campaign_key", CAMPAIGN_KEY,
                    "campaign_name", "测试战役",
                    "campaign_status", "ACTIVE",
                    "module_release_id", 11L,
                    "frozen_module_key", "dnd5e2014_srd51_se_v1",
                    "frozen_release_version", "1",
                    "frozen_content_sha256", SHA,
                    "module_key", "dnd5e2014_srd51_se_v1",
                    "release_version", "1",
                    "canonical_format_version", 1,
                    "hash_algorithm", "SHA-256",
                    "content_sha256", SHA,
                    "release_status", "RELEASED");
        }

        private static Map<String, Object> field(
                String key, String type, String text, Long integer,
                BigDecimal decimal, Integer bool) {
            return row(
                    "character_key", CHARACTER_KEY,
                    "field_key", key,
                    "value_type", type,
                    "text_value", text,
                    "integer_value", integer,
                    "decimal_value", decimal,
                    "boolean_value", bool);
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
                    case "getBigDecimal" -> {
                        Object value = values.get(index[0]).get(arguments[0]);
                        wasNull[0] = value == null;
                        yield value;
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
