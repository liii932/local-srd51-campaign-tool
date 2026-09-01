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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcCharacterCreationRepositoryTest {
    private static final String HASH =
            "8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e";
    private static final CharacterCreationRepository.Command COMMAND =
            new CharacterCreationRepository.Command(
                    "123e4567-e89b-42d3-a456-426614174000",
                    "1".repeat(64),
                    "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                    "11111111-2222-4333-8444-555555555555",
                    "NPC",
                    "守卫甲",
                    "dnd5e2014_srd51_se_v1",
                    "1",
                    HASH,
                    null,
                    List.of(new CharacterCreationRepository.FieldValue(
                            "ability.strength", "INTEGER",
                            new ModuleCatalog.IntegerValue(10))),
                    List.of(),
                    List.of(new CharacterCreationRepository.Proficiency(
                            "skill.athletics", "proficiency.none")),
                    List.of(new CharacterCreationRepository.Proficiency(
                            "save.strength", "proficiency.none")));

    @Test
    void commitsRootValuesProficienciesEventAuditAndIdempotencyTogether() throws Exception {
        Fixture fixture = new Fixture();

        CharacterCreationRepository.Result result =
                new JdbcCharacterCreationRepository(fixture.dataSource()).create(COMMAND);

        assertEquals(CharacterCreationRepository.Result.Status.CREATED, result.status());
        assertEquals(COMMAND.characterKey(), result.characterKey());
        assertEquals(0L, result.rowVersion());
        assertEquals(1, fixture.targets.stream().filter("character_record"::equals).count());
        assertEquals(1, fixture.targets.stream().filter("character_field_value"::equals).count());
        assertEquals(1, fixture.targets.stream()
                .filter("character_skill_proficiency"::equals).count());
        assertEquals(1, fixture.targets.stream()
                .filter("character_save_proficiency"::equals).count());
        assertEquals(1, fixture.targets.stream().filter("campaign"::equals).count());
        assertEquals(1, fixture.targets.stream().filter("game_event"::equals).count());
        assertEquals(5, fixture.targets.stream().filter("field_change"::equals).count());
        assertEquals(1, fixture.targets.stream().filter("host_operation"::equals).count());
        assertEquals(101L, fixture.operationValues.get(5));
        assertTrue(fixture.serializable);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertRestored(fixture);
    }

    @Test
    void locksMutableCampaignButReadsImmutableBindingWithoutForUpdate() throws Exception {
        Fixture fixture = new Fixture();

        new JdbcCharacterCreationRepository(fixture.dataSource()).create(COMMAND);

        String campaignLock = fixture.preparedSql.stream()
                .filter(sql -> sql.contains("FROM campaign AS c"))
                .findFirst()
                .orElseThrow();
        String bindingRead = fixture.preparedSql.stream()
                .filter(sql -> sql.contains("FROM campaign_module"))
                .findFirst()
                .orElseThrow();
        assertTrue(campaignLock.contains("FOR UPDATE"));
        assertFalse(campaignLock.contains("campaign_module"));
        assertFalse(bindingRead.contains("FOR UPDATE"));
    }

    @Test
    void additionalModuleFieldAddsOnlyAnotherValueRowAndNeverPreparesDdl()
            throws Exception {
        Fixture fixture = new Fixture();
        CharacterCreationRepository.Command extended = commandWithFields(List.of(
                new CharacterCreationRepository.FieldValue(
                        "ability.strength", "INTEGER",
                        new ModuleCatalog.IntegerValue(10)),
                new CharacterCreationRepository.FieldValue(
                        "note.tactical", "TEXT",
                        new ModuleCatalog.TextValue(""))));

        CharacterCreationRepository.Result result =
                new JdbcCharacterCreationRepository(fixture.dataSource()).create(extended);

        assertEquals(CharacterCreationRepository.Result.Status.CREATED, result.status());
        assertEquals(2, fixture.targets.stream()
                .filter("character_field_value"::equals).count());
        Pattern ddl = Pattern.compile(
                "(?i)\\b(?:CREATE|ALTER|DROP|TRUNCATE)\\s+(?:TABLE|INDEX|TRIGGER)\\b");
        assertTrue(fixture.preparedSql.stream().noneMatch(sql -> ddl.matcher(sql).find()));
    }

    @Test
    void replaysOriginalCharacterWithoutWritingAgain() throws Exception {
        Fixture fixture = new Fixture();
        fixture.operationRows = List.of(Map.of(
                "request_digest_sha256", COMMAND.requestDigestSha256(),
                "operation_type", "CREATE_CHARACTER",
                "character_id", 101L,
                "result_status", "SUCCEEDED"));
        fixture.characterRows = List.of(Map.of(
                "character_key", "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff",
                "row_version", 4L));

        CharacterCreationRepository.Result result =
                new JdbcCharacterCreationRepository(fixture.dataSource()).create(COMMAND);

        assertEquals(CharacterCreationRepository.Result.Status.ALREADY_SUCCEEDED, result.status());
        assertEquals("bbbbbbbb-cccc-4ddd-8eee-ffffffffffff", result.characterKey());
        assertEquals(4L, result.rowVersion());
        assertTrue(fixture.targets.isEmpty());
        assertTrue(fixture.committed);
    }

    @Test
    void rejectsChangedFrozenBindingBeforeAnyWrite() throws Exception {
        Fixture fixture = new Fixture();
        fixture.bindingRows = List.of(bindingRow("0".repeat(64)));

        CharacterCreationRepository.Result result =
                new JdbcCharacterCreationRepository(fixture.dataSource()).create(COMMAND);

        assertEquals(CharacterCreationRepository.Result.Status.MODULE_BINDING_MISMATCH,
                result.status());
        assertTrue(fixture.targets.isEmpty());
        assertTrue(fixture.rolledBack);
        assertRestored(fixture);
    }

    @Test
    void eventFailureRollsBackBeforeAuditAndSucceededOperation() {
        Fixture fixture = new Fixture();
        fixture.failTarget = "game_event";

        assertThrows(SQLException.class,
                () -> new JdbcCharacterCreationRepository(fixture.dataSource()).create(COMMAND));

        assertTrue(fixture.targets.contains("character_record"));
        assertTrue(fixture.targets.contains("game_event"));
        assertFalse(fixture.targets.contains("field_change"));
        assertFalse(fixture.targets.contains("host_operation"));
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
        assertRestored(fixture);
    }

    private static void assertRestored(Fixture fixture) {
        assertTrue(fixture.closed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
    }

    private static Map<String, Object> campaignRow() {
        return Map.of(
                "id", 7L,
                "internal_event_tail", 0L);
    }

    private static Map<String, Object> bindingRow(String hash) {
        return Map.of(
                "module_release_id", 9L,
                "frozen_module_key", COMMAND.moduleKey(),
                "frozen_release_version", COMMAND.releaseVersion(),
                "frozen_content_sha256", hash);
    }

    private static CharacterCreationRepository.Command commandWithFields(
            List<CharacterCreationRepository.FieldValue> fields) {
        return new CharacterCreationRepository.Command(
                COMMAND.requestId(),
                COMMAND.requestDigestSha256(),
                COMMAND.characterKey(),
                COMMAND.campaignKey(),
                COMMAND.characterType(),
                COMMAND.characterName(),
                COMMAND.moduleKey(),
                COMMAND.releaseVersion(),
                COMMAND.contentSha256(),
                COMMAND.templateKey(),
                fields,
                COMMAND.classLevels(),
                COMMAND.skillProficiencies(),
                COMMAND.saveProficiencies());
    }

    private static final class Fixture {
        private List<Map<String, Object>> operationRows = List.of();
        private List<Map<String, Object>> campaignRows = List.of(campaignRow());
        private List<Map<String, Object>> bindingRows = List.of(bindingRow(HASH));
        private List<Map<String, Object>> characterRows = List.of();
        private final List<String> targets = new ArrayList<>();
        private final List<String> preparedSql = new ArrayList<>();
        private final Map<Integer, Object> operationValues = new HashMap<>();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean serializable;
        private boolean committed;
        private boolean rolledBack;
        private boolean closed;
        private String failTarget;

        private DataSource dataSource() {
            Connection connection = proxy(Connection.class, this::connection);
            return proxy(DataSource.class, (ignored, method, arguments) ->
                    "getConnection".equals(method.getName())
                            ? connection : defaultValue(method.getReturnType()));
        }

        private Object connection(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> { autoCommit = (boolean) arguments[0]; yield null; }
                case "isReadOnly" -> readOnly;
                case "setReadOnly" -> { readOnly = (boolean) arguments[0]; yield null; }
                case "getTransactionIsolation" -> isolation;
                case "setTransactionIsolation" -> {
                    isolation = (int) arguments[0];
                    serializable |= isolation == Connection.TRANSACTION_SERIALIZABLE;
                    yield null;
                }
                case "prepareStatement" -> statement((String) arguments[0], arguments);
                case "commit" -> { committed = true; yield null; }
                case "rollback" -> { rolledBack = true; yield null; }
                case "close" -> { closed = true; yield null; }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql, Object[] prepareArguments) {
            preparedSql.add(sql);
            Map<Integer, Object> values = new HashMap<>();
            boolean generated = prepareArguments != null
                    && prepareArguments.length > 1
                    && Integer.valueOf(Statement.RETURN_GENERATED_KEYS).equals(prepareArguments[1]);
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setString", "setLong", "setInt", "setBoolean",
                                "setBigDecimal", "setNull" -> {
                            values.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeQuery" -> resultSet(rowsFor(sql));
                        case "executeUpdate" -> executeUpdate(sql, values);
                        case "getGeneratedKeys" -> generated
                                ? resultSet(List.of(Map.of("1",
                                        sql.contains("game_event") ? 202L : 101L)))
                                : resultSet(List.of());
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private int executeUpdate(String sql, Map<Integer, Object> values) throws SQLException {
            String target = target(sql);
            targets.add(target);
            if ("host_operation".equals(target)) {
                operationValues.putAll(values);
            }
            if (target.equals(failTarget)) {
                throw new SQLException("synthetic failure");
            }
            return 1;
        }

        private List<Map<String, Object>> rowsFor(String sql) {
            if (sql.contains("FROM host_operation")) return operationRows;
            if (sql.contains("FROM campaign AS c")) return campaignRows;
            if (sql.contains("FROM campaign_module")) return bindingRows;
            if (sql.contains("FROM character_record WHERE id")) return characterRows;
            throw new AssertionError("Unexpected query: " + sql);
        }

        private ResultSet resultSet(List<Map<String, Object>> rows) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] < rows.size();
                        case "getString" -> {
                            Object value = value(rows, index[0], arguments[0], wasNull);
                            yield value == null ? null : value.toString();
                        }
                        case "getLong" -> {
                            Object value = value(rows, index[0], arguments[0], wasNull);
                            yield value == null ? 0L : ((Number) value).longValue();
                        }
                        case "wasNull" -> wasNull[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static Object value(
                List<Map<String, Object>> rows, int index, Object column, boolean[] wasNull) {
            String key = column instanceof Integer ? column.toString() : (String) column;
            Object value = rows.get(index).get(key);
            wasNull[0] = value == null;
            return value;
        }

        private static String target(String sql) {
            String normalized = sql.stripLeading();
            String prefix = normalized.startsWith("INSERT INTO ") ? "INSERT INTO " : "UPDATE ";
            int start = prefix.length();
            int end = normalized.indexOf(' ', start);
            return normalized.substring(start, end);
        }
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
