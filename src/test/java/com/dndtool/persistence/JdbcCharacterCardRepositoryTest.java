package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Exercises the consistent, read-only card aggregate projection. */
final class JdbcCharacterCardRepositoryTest {
    private static final String CHARACTER_KEY =
            "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String SHA = "a".repeat(64);

    @Test
    void readsAllCardPartitionsInOneRepeatableReadSnapshot() throws Exception {
        DatabaseFixture database = new DatabaseFixture(true);
        JdbcCharacterCardRepository repository =
                new JdbcCharacterCardRepository(database.dataSource());

        CharacterCardRepository.Snapshot snapshot =
                repository.findByCharacterKey(CHARACTER_KEY).orElseThrow();

        assertEquals("Aria", snapshot.characterName());
        assertEquals(7, snapshot.rowVersion());
        assertEquals(1, snapshot.fields().size());
        assertEquals(1, snapshot.classLevels().size());
        assertEquals(1, snapshot.skillProficiencies().size());
        assertEquals(1, snapshot.saveProficiencies().size());
        assertEquals(1, snapshot.items().size());
        assertEquals(List.of("root", "fields", "classes", "skills", "saves", "items"),
                database.executed);
        assertTrue(database.committed);
        assertFalse(database.rolledBack);
        assertTrue(database.autoCommit);
        assertFalse(database.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, database.isolation);
    }

    @Test
    void missingCharacterDoesNotReadChildTables() throws Exception {
        DatabaseFixture database = new DatabaseFixture(false);
        JdbcCharacterCardRepository repository =
                new JdbcCharacterCardRepository(database.dataSource());

        Optional<CharacterCardRepository.Snapshot> result =
                repository.findByCharacterKey(CHARACTER_KEY);

        assertTrue(result.isEmpty());
        assertEquals(List.of("root"), database.executed);
        assertTrue(database.committed);
        assertFalse(database.rolledBack);
    }

    private static final class DatabaseFixture implements InvocationHandler {
        private final boolean found;
        private final List<String> executed = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean committed;
        private boolean rolledBack;

        private DatabaseFixture(boolean found) {
            this.found = found;
        }

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
                case "prepareStatement" -> statement((String) arguments[0]);
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

        private PreparedStatement statement(String sql) {
            String operation = operation(sql);
            InvocationHandler handler = (ignored, method, arguments) -> switch (method.getName()) {
                case "setString", "setLong", "setMaxRows", "setQueryTimeout", "close" -> null;
                case "executeQuery" -> {
                    executed.add(operation);
                    yield resultSet(rows(operation));
                }
                default -> defaultValue(method.getReturnType());
            };
            return proxy(PreparedStatement.class, handler);
        }

        private static String operation(String sql) {
            String normalized = sql.replaceAll("\\s+", " ").trim();
            if (normalized.startsWith("SELECT cr.id")) return "root";
            if (normalized.startsWith("SELECT field_key")) return "fields";
            if (normalized.startsWith("SELECT class_key")) return "classes";
            if (normalized.startsWith("SELECT skill_key")) return "skills";
            if (normalized.startsWith("SELECT save_key")) return "saves";
            if (normalized.startsWith("SELECT id, source_kind")) return "items";
            throw new AssertionError(normalized);
        }

        private List<Map<String, Object>> rows(String operation) {
            return switch (operation) {
                case "root" -> found ? List.of(root()) : List.of();
                case "fields" -> List.of(Map.of(
                        "field_key", "ability.strength", "value_type", "INTEGER",
                        "integer_value", 10L));
                case "classes" -> List.of(Map.of(
                        "class_key", "class.fighter", "class_level", 5));
                case "skills" -> List.of(Map.of(
                        "skill_key", "skill.athletics",
                        "proficiency_key", "proficiency.full"));
                case "saves" -> List.of(Map.of(
                        "save_key", "save.strength",
                        "proficiency_key", "proficiency.full"));
                case "items" -> List.of(Map.of(
                        "id", 44L, "source_kind", "MODULE", "item_key", "item.torch",
                        "item_name", "火把", "item_description", "普通照明用火把",
                        "quantity", 2, "item_status", "ACTIVE"));
                default -> throw new AssertionError(operation);
            };
        }

        private static Map<String, Object> root() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 11L);
            row.put("character_key", CHARACTER_KEY);
            row.put("character_type", "PC");
            row.put("character_name", "Aria");
            row.put("character_status", "ACTIVE");
            row.put("row_version", 7L);
            row.put("saved_module_key", "dnd5e2014_srd51_se_v1");
            row.put("saved_release_version", "1");
            row.put("saved_content_sha256", SHA);
            row.put("frozen_module_key", "dnd5e2014_srd51_se_v1");
            row.put("frozen_release_version", "1");
            row.put("frozen_content_sha256", SHA);
            row.put("module_key", "dnd5e2014_srd51_se_v1");
            row.put("release_version", "1");
            row.put("content_sha256", SHA);
            row.put("release_status", "RELEASED");
            return row;
        }
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        final int[] index = {-1};
        final boolean[] wasNull = {false};
        InvocationHandler handler = (ignored, method, arguments) -> switch (method.getName()) {
            case "next" -> ++index[0] < rows.size();
            case "wasNull" -> wasNull[0];
            case "getString", "getLong", "getInt", "getBigDecimal" -> {
                String column = (String) arguments[0];
                Object value = rows.get(index[0]).get(column);
                wasNull[0] = value == null;
                yield switch (method.getName()) {
                    case "getString" -> value == null ? null : value.toString();
                    case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                    case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                    case "getBigDecimal" -> value;
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
