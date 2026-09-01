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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Verifies the ordering and rollback behavior of the card aggregate transaction. */
final class JdbcCharacterCardMutationRepositoryTest {
    private static final String CHARACTER_KEY =
            "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String REQUEST_ID =
            "123e4567-e89b-42d3-a456-426614174000";
    private static final String SHA = "a".repeat(64);

    @Test
    void fieldUpdateCommitsValueAuditVersionAndIdempotencyTogether() throws Exception {
        DatabaseFixture database = new DatabaseFixture();
        JdbcCharacterCardMutationRepository repository =
                new JdbcCharacterCardMutationRepository(database.dataSource());

        CharacterCardMutationRepository.Result result = repository.mutate(
                command("ability.strength", 15));

        assertEquals(CharacterCardMutationRepository.Status.UPDATED, result.status());
        assertEquals(8, result.rowVersion());
        assertTrue(database.committed);
        assertFalse(database.rolledBack);
        assertEquals(List.of(
                "lock-operation", "lock-character", "lock-campaign", "load-binding",
                "lock-fields", "update-field", "advance-event", "insert-event",
                "insert-change", "advance-version", "insert-operation"), database.executed);
        assertTrue(database.autoCommit);
        assertFalse(database.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, database.isolation);
    }

    @Test
    void locksOnlyMutableRootsAndReadsFrozenDefinitionsWithoutForUpdate()
            throws Exception {
        DatabaseFixture database = new DatabaseFixture();

        new JdbcCharacterCardMutationRepository(database.dataSource())
                .mutate(command("ability.strength", 15));

        String characterLock = database.preparedSql.stream()
                .filter(sql -> sql.contains("FROM character_record AS cr"))
                .findFirst().orElseThrow();
        String campaignLock = database.preparedSql.stream()
                .filter(sql -> sql.contains("FROM campaign") && sql.contains("FOR UPDATE"))
                .findFirst().orElseThrow();
        String bindingRead = database.preparedSql.stream()
                .filter(sql -> sql.contains("FROM campaign_module AS cm"))
                .findFirst().orElseThrow();
        assertTrue(characterLock.contains("FOR UPDATE"));
        assertFalse(characterLock.contains("campaign_module"));
        assertFalse(characterLock.contains("JOIN module_release"));
        assertTrue(campaignLock.contains("FOR UPDATE"));
        assertFalse(bindingRead.contains("FOR UPDATE"));
    }

    @Test
    void dependentFieldViolationRollsBackBeforeEventVersionOrOperationWrites() {
        DatabaseFixture database = new DatabaseFixture();
        JdbcCharacterCardMutationRepository repository =
                new JdbcCharacterCardMutationRepository(database.dataSource());

        assertThrows(IllegalArgumentException.class,
                () -> repository.mutate(command("hp.maximum", 5)));

        assertFalse(database.committed);
        assertTrue(database.rolledBack);
        assertEquals(List.of(
                "lock-operation", "lock-character", "lock-campaign", "load-binding",
                "lock-fields"), database.executed);
        assertTrue(database.autoCommit);
        assertFalse(database.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, database.isolation);
    }

    private static CharacterCardMutationRepository.Command command(String field, int value) {
        return new CharacterCardMutationRepository.Command(
                REQUEST_ID, SHA, CHARACTER_KEY, 7,
                CharacterCardMutationRepository.Action.SET_FIELD,
                field, null, "", value,
                "dnd5e2014_srd51_se_v1", "1", SHA, fieldRules());
    }

    private static List<CharacterCardMutationRepository.IntegerFieldRule> fieldRules() {
        return List.of(
                rule("ability.strength", 1, 30, null),
                rule("ability.dexterity", 1, 30, null),
                rule("ability.constitution", 1, 30, null),
                rule("ability.intelligence", 1, 30, null),
                rule("ability.wisdom", 1, 30, null),
                rule("ability.charisma", 1, 30, null),
                rule("hp.maximum", 1, 999, null),
                rule("hp.current", 0, 999, "hp.maximum"),
                rule("armor_class", 0, 99, null),
                rule("speed.ground", 0, 999, null));
    }

    private static CharacterCardMutationRepository.IntegerFieldRule rule(
            String key, long minimum, long maximum, String dependency) {
        return new CharacterCardMutationRepository.IntegerFieldRule(
                key, minimum, maximum, dependency);
    }

    private static final class DatabaseFixture implements InvocationHandler {
        private final List<String> executed = new ArrayList<>();
        private final List<String> preparedSql = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean committed;
        private boolean rolledBack;

        DataSource dataSource() {
            return proxy(DataSource.class, (ignored, method, arguments) ->
                    "getConnection".equals(method.getName())
                            ? proxy(Connection.class, this)
                            : defaultValue(method.getReturnType()));
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> {
                    autoCommit = (boolean) arguments[0];
                    yield null;
                }
                case "isReadOnly" -> readOnly;
                case "setReadOnly" -> {
                    readOnly = (boolean) arguments[0];
                    yield null;
                }
                case "getTransactionIsolation" -> isolation;
                case "setTransactionIsolation" -> {
                    isolation = (int) arguments[0];
                    yield null;
                }
                case "prepareStatement" -> statement((String) arguments[0], arguments);
                case "commit" -> {
                    committed = true;
                    yield null;
                }
                case "rollback" -> {
                    rolledBack = true;
                    yield null;
                }
                case "close" -> null;
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql, Object[] prepareArguments) {
            preparedSql.add(sql);
            String operation = operation(sql);
            Map<Integer, Object> parameters = new HashMap<>();
            InvocationHandler handler = (ignored, method, arguments) -> switch (method.getName()) {
                case "setString", "setLong", "setInt", "setNull" -> {
                    parameters.put((int) arguments[0], arguments[1]);
                    yield null;
                }
                case "setMaxRows", "setQueryTimeout", "close" -> null;
                case "executeQuery" -> {
                    executed.add(operation);
                    yield resultSet(rows(operation));
                }
                case "executeUpdate" -> {
                    executed.add(operation);
                    yield 1;
                }
                case "getGeneratedKeys" -> resultSet(List.of(Map.of("1", 50L)));
                default -> defaultValue(method.getReturnType());
            };
            return proxy(PreparedStatement.class, handler);
        }

        private static String operation(String sql) {
            String normalized = sql.replaceAll("\\s+", " ").trim();
            if (normalized.startsWith("SELECT request_digest_sha256")) return "lock-operation";
            if (normalized.startsWith("SELECT cr.id")) return "lock-character";
            if (normalized.startsWith("SELECT internal_event_tail")) return "lock-campaign";
            if (normalized.startsWith("SELECT cm.module_release_id")) return "load-binding";
            if (normalized.startsWith("SELECT field_key")) return "lock-fields";
            if (normalized.startsWith("UPDATE character_field_value")) return "update-field";
            if (normalized.startsWith("UPDATE campaign SET internal_event_tail")) {
                return "advance-event";
            }
            if (normalized.startsWith("INSERT INTO game_event")) return "insert-event";
            if (normalized.startsWith("INSERT INTO field_change")) return "insert-change";
            if (normalized.startsWith("UPDATE character_record SET row_version")) {
                return "advance-version";
            }
            if (normalized.startsWith("INSERT INTO host_operation")) {
                return "insert-operation";
            }
            throw new AssertionError(normalized);
        }

        private static List<Map<String, Object>> rows(String operation) {
            return switch (operation) {
                case "lock-operation" -> List.of();
                case "lock-character" -> List.of(characterRow());
                case "lock-campaign" -> List.of(Map.of("internal_event_tail", 4L));
                case "load-binding" -> List.of(bindingRow());
                case "lock-fields" -> fieldRows();
                default -> throw new AssertionError(operation);
            };
        }

        private static Map<String, Object> characterRow() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 11L);
            row.put("campaign_id", 22L);
            row.put("module_release_id", 33L);
            row.put("row_version", 7L);
            row.put("saved_module_key", "dnd5e2014_srd51_se_v1");
            row.put("saved_release_version", "1");
            row.put("saved_content_sha256", SHA);
            return row;
        }

        private static Map<String, Object> bindingRow() {
            Map<String, Object> row = new HashMap<>();
            row.put("frozen_release_id", 33L);
            row.put("frozen_module_key", "dnd5e2014_srd51_se_v1");
            row.put("frozen_release_version", "1");
            row.put("frozen_content_sha256", SHA);
            row.put("module_key", "dnd5e2014_srd51_se_v1");
            row.put("release_version", "1");
            row.put("content_sha256", SHA);
            row.put("release_status", "RELEASED");
            return row;
        }

        private static List<Map<String, Object>> fieldRows() {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (CharacterCardMutationRepository.IntegerFieldRule rule : fieldRules()) {
                long value = switch (rule.fieldKey()) {
                    case "hp.maximum", "hp.current" -> 10;
                    default -> 10;
                };
                rows.add(Map.of(
                        "field_key", rule.fieldKey(),
                        "value_type", "INTEGER",
                        "integer_value", value));
            }
            return rows;
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        final int[] index = {-1};
        final boolean[] wasNull = {false};
        InvocationHandler handler = (ignored, method, arguments) -> switch (method.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "wasNull" -> wasNull[0];
            case "getString", "getLong", "getInt" -> {
                Object key = arguments[0];
                String column = key instanceof Integer ? Integer.toString((Integer) key) : (String) key;
                Object value = rows.get(index[0]).get(column);
                wasNull[0] = value == null;
                yield switch (method.getName()) {
                    case "getString" -> value == null ? null : value.toString();
                    case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                    case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                    default -> throw new AssertionError();
                };
            }
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        };
        return proxy(ResultSet.class, handler);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }
}
