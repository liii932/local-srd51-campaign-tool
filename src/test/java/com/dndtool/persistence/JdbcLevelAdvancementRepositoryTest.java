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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import com.dndtool.service.CharacterAdvancementChoiceRules;
import com.dndtool.service.ClassFeatureRules;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcLevelAdvancementRepositoryTest {
    private static final String HASH = "a".repeat(64);
    private static final LevelAdvancementRepository.ResourceState HP =
            state("resource.hit_points", 20, 20);
    private static final LevelAdvancementRepository.ResourceState HIT_DIE =
            state("resource.hit_dice.d10", 1, 1);
    private static final LevelAdvancementRepository.ResourceState SECOND_WIND =
            state("resource.fighter.second_wind", 1, 1);
    private static final LevelAdvancementRepository.PreviewContext CONTEXT =
            new LevelAdvancementRepository.PreviewContext(
                    "11111111-2222-4333-8444-555555555555",
                    "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", 9, 4,
                    "dnd5e2014_srd51_se", "1", HASH, "class.fighter", 1, 1, 14, 8,
                    List.of(SECOND_WIND, HIT_DIE, HP),
                    List.of(new LevelAdvancementRepository.ClassLevel("class.fighter", 1)),
                    Map.of("ability.strength", 10, "ability.dexterity", 10,
                            "ability.constitution", 14, "ability.intelligence", 10,
                            "ability.wisdom", 10, "ability.charisma", 8),
                    Set.of(), Set.of("armor.heavy", "armor.light", "armor.medium",
                            "armor.shield", "weapon.martial", "weapon.simple"),
                    Map.of(), Set.of());
    private static final LevelAdvancementRepository.Command COMMAND =
            new LevelAdvancementRepository.Command(
                    "123e4567-e89b-42d3-a456-426614174000", "b".repeat(64),
                    "c".repeat(64), CONTEXT, "SERVER_ROLL", 2, 10, 2, 2, 2,
                    List.of(
                            new LevelAdvancementRepository.ResourceChange(
                                    "resource.fighter.action_surge", null,
                                    state("resource.fighter.action_surge", 1, 1)),
                            new LevelAdvancementRepository.ResourceChange(
                                    "resource.hit_dice.d10", HIT_DIE,
                                    state("resource.hit_dice.d10", 2, 2))));

    @Test
    void validatesLocksThenRollsAndCommitsAllAuthoritativeRowsTogether() throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger rolls = new AtomicInteger();

        LevelAdvancementRepository.Result result = new JdbcLevelAdvancementRepository(
                fixture.dataSource()).confirm(COMMAND, (sides, modifier) -> {
                    rolls.incrementAndGet();
                    assertTrue(fixture.lockedResources);
                    return new LevelAdvancementRepository.HitPointResolution(7, 9);
                });

        assertEquals(LevelAdvancementRepository.Result.Status.ADVANCED, result.status());
        assertEquals(1, rolls.get());
        for (String target : List.of("character_class_level_v2",
                "character_resource_state_v2", "character_record", "campaign",
                "game_event", "character_level_advancement_v2",
                "character_level_resource_change_v2", "field_change", "host_operation")) {
            assertTrue(fixture.targets.contains(target), target);
        }
        assertTrue(fixture.serializable);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.closed);
    }

    @Test
    void extendedAdvancementPersistsFeatureUnlockInTheSameTransaction() throws Exception {
        Fixture fixture = new Fixture();
        LevelAdvancementRepository.Command command = extendedCommand();

        LevelAdvancementRepository.Result result = new JdbcLevelAdvancementRepository(
                fixture.dataSource()).confirm(command, (sides, modifier) ->
                        new LevelAdvancementRepository.HitPointResolution(7, 9));

        assertEquals(LevelAdvancementRepository.Result.Status.ADVANCED, result.status());
        assertTrue(fixture.targets.contains("character_feature_state_v2"));
        assertTrue(fixture.targets.contains("field_change"));
        assertTrue(fixture.targets.contains("host_operation"));
        assertTrue(fixture.committed);
    }

    @Test
    void featureWriteFailureRollsBackEveryExtendedAdvancementWrite() {
        Fixture fixture = new Fixture();
        fixture.failTarget = "character_feature_state_v2";

        assertThrows(SQLException.class, () -> new JdbcLevelAdvancementRepository(
                fixture.dataSource()).confirm(extendedCommand(), (sides, modifier) ->
                        new LevelAdvancementRepository.HitPointResolution(7, 9)));

        assertTrue(fixture.targets.contains("character_feature_state_v2"));
        assertFalse(fixture.targets.contains("host_operation"));
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
    }

    @Test
    void staleFeatureStateRollsBackBeforeRandomnessOrAnyWrite() throws Exception {
        Fixture fixture = new Fixture();
        fixture.featureRows = List.of(Map.of("feature_key", "feature.fighter.second_wind"));
        AtomicInteger rolls = new AtomicInteger();

        LevelAdvancementRepository.Result result = new JdbcLevelAdvancementRepository(
                fixture.dataSource()).confirm(extendedCommand(), (sides, modifier) -> {
                    rolls.incrementAndGet();
                    return new LevelAdvancementRepository.HitPointResolution(7, 9);
                });

        assertEquals(LevelAdvancementRepository.Result.Status.AUTHORITATIVE_STATE_MISMATCH,
                result.status());
        assertEquals(0, rolls.get());
        assertTrue(fixture.targets.isEmpty());
        assertTrue(fixture.rolledBack);
    }

    @Test
    void staleRowVersionRollsBackBeforeRandomnessOrAnyWrite() throws Exception {
        Fixture fixture = new Fixture();
        fixture.rowVersion = 5;
        AtomicInteger rolls = new AtomicInteger();

        LevelAdvancementRepository.Result result = new JdbcLevelAdvancementRepository(
                fixture.dataSource()).confirm(COMMAND, (sides, modifier) -> {
                    rolls.incrementAndGet();
                    return new LevelAdvancementRepository.HitPointResolution(1, 3);
                });

        assertEquals(LevelAdvancementRepository.Result.Status.STALE_ROW_VERSION, result.status());
        assertEquals(0, rolls.get());
        assertTrue(fixture.targets.isEmpty());
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
    }

    @Test
    void snapshotFailureRollsBackLevelResourcesEventAuditAndIdempotency() {
        Fixture fixture = new Fixture();
        fixture.failTarget = "character_level_advancement_v2";

        assertThrows(SQLException.class, () -> new JdbcLevelAdvancementRepository(
                fixture.dataSource()).confirm(COMMAND,
                        (sides, modifier) ->
                                new LevelAdvancementRepository.HitPointResolution(7, 9)));

        assertTrue(fixture.targets.contains("character_class_level_v2"));
        assertTrue(fixture.targets.contains("character_resource_state_v2"));
        assertTrue(fixture.targets.contains("game_event"));
        assertTrue(fixture.targets.contains("character_level_advancement_v2"));
        assertFalse(fixture.targets.contains("character_level_resource_change_v2"));
        assertFalse(fixture.targets.contains("field_change"));
        assertFalse(fixture.targets.contains("host_operation"));
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
    }

    @Test
    void idempotentReplayReturnsSavedRollAndDigestConflictNeverRerolls() throws Exception {
        Fixture replay = new Fixture();
        replay.operationDigest = "b".repeat(64);
        AtomicInteger rolls = new AtomicInteger();

        LevelAdvancementRepository.Result preflight = new JdbcLevelAdvancementRepository(
                replay.dataSource()).findCompleted(COMMAND.requestId(),
                        COMMAND.requestDigestSha256()).orElseThrow();
        assertEquals(LevelAdvancementRepository.Result.Status.ALREADY_SUCCEEDED,
                preflight.status());
        assertEquals(7, preflight.hitDieRoll());

        LevelAdvancementRepository.Result replayed = new JdbcLevelAdvancementRepository(
                replay.dataSource()).confirm(COMMAND, (sides, modifier) -> {
                    rolls.incrementAndGet();
                    return new LevelAdvancementRepository.HitPointResolution(1, 3);
                });

        assertEquals(LevelAdvancementRepository.Result.Status.ALREADY_SUCCEEDED,
                replayed.status());
        assertEquals(7, replayed.hitDieRoll());
        assertEquals(9, replayed.hitPointIncrease());
        assertEquals(0, rolls.get());
        assertTrue(replay.targets.isEmpty());

        Fixture conflict = new Fixture();
        conflict.operationDigest = "d".repeat(64);
        LevelAdvancementRepository.Result rejected = new JdbcLevelAdvancementRepository(
                conflict.dataSource()).confirm(COMMAND, (sides, modifier) -> {
                    rolls.incrementAndGet();
                    return new LevelAdvancementRepository.HitPointResolution(1, 3);
                });
        assertEquals(LevelAdvancementRepository.Result.Status.IDEMPOTENCY_CONFLICT,
                rejected.status());
        assertEquals(0, rolls.get());
        assertTrue(conflict.targets.isEmpty());
    }

    private static LevelAdvancementRepository.Command extendedCommand() {
        CharacterAdvancementChoiceRules.Prepared choice =
                new CharacterAdvancementChoiceRules.Prepared("class.fighter", 1, false,
                        Map.of("class.fighter", 2), CONTEXT.abilityScores(), Map.of(), null,
                        List.of());
        ClassFeatureRules.Transition transition = new ClassFeatureRules.Transition(
                null, null, List.of(new ClassFeatureRules.FeatureRule(
                        "feature.fighter.action_surge", "character.class", "class.fighter", 2,
                        "ACTIVE", "AUTOMATIC", "ACTION_SURGE_V1")));
        return new LevelAdvancementRepository.Command(
                COMMAND.requestId(), COMMAND.requestDigestSha256(), COMMAND.previewDigestSha256(),
                CONTEXT, COMMAND.hpChoiceAlgorithm(), COMMAND.targetLevel(), COMMAND.hitDieSides(),
                COMMAND.constitutionModifier(), COMMAND.previousProficiencyBonus(),
                COMMAND.newProficiencyBonus(), COMMAND.resourceChanges(), choice, transition);
    }

    private static LevelAdvancementRepository.ResourceState state(
            String key, long current, long maximum) {
        return new LevelAdvancementRepository.ResourceState(key, current, maximum, false);
    }

    private static final class Fixture {
        private final List<String> targets = new ArrayList<>();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean serializable;
        private boolean committed;
        private boolean rolledBack;
        private boolean closed;
        private boolean lockedResources;
        private long rowVersion = 4;
        private String operationDigest;
        private String failTarget;
        private List<Map<String, Object>> featureRows = List.of();

        DataSource dataSource() {
            Connection connection = proxy(Connection.class, this::connection);
            return proxy(DataSource.class, (ignored, method, arguments) ->
                    "getConnection".equals(method.getName()) ? connection
                            : defaultValue(method.getReturnType()));
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
            boolean generated = prepareArguments != null && prepareArguments.length > 1
                    && Integer.valueOf(Statement.RETURN_GENERATED_KEYS).equals(prepareArguments[1]);
            return proxy(PreparedStatement.class, (ignored, method, arguments) -> switch (
                    method.getName()) {
                case "setString", "setLong", "setInt", "setBoolean", "setNull",
                        "setQueryTimeout", "setMaxRows" -> null;
                case "executeQuery" -> resultSet(rows(sql));
                case "executeUpdate" -> update(sql);
                case "addBatch" -> null;
                case "executeBatch" -> new int[] {update(sql)};
                case "getGeneratedKeys" -> generated
                        ? resultSet(List.of(Map.of("1", 202L))) : resultSet(List.of());
                default -> defaultValue(method.getReturnType());
            });
        }

        private int update(String sql) throws SQLException {
            String target = target(sql);
            targets.add(target);
            if (target.equals(failTarget)) throw new SQLException("synthetic failure");
            return 1;
        }

        private List<Map<String, Object>> rows(String sql) {
            if (sql.contains("FROM host_operation")) {
                return operationDigest == null ? List.of() : List.of(Map.of(
                        "request_digest_sha256", operationDigest,
                        "operation_type", "ADVANCE_CHARACTER_LEVEL",
                        "character_id", 101L, "game_event_id", 202L,
                        "result_status", "SUCCEEDED"));
            }
            if (sql.contains("FROM character_level_advancement_v2 AS a")) {
                return List.of(Map.of("character_key", CONTEXT.characterKey(),
                        "new_row_version", 5L, "hit_die_roll", 7,
                        "hit_point_increase", 9));
            }
            if (sql.contains("WHERE cr.character_key") && sql.contains("FOR UPDATE")) {
                return List.of(Map.of("id", 101L, "campaign_id", 7L,
                        "module_release_id", 9L, "row_version", rowVersion,
                        "internal_event_tail", 9L));
            }
            if (sql.contains("FROM campaign_module")) return List.of(Map.of(
                    "frozen_module_key", "dnd5e2014_srd51_se",
                    "frozen_release_version", "1", "frozen_content_sha256", HASH));
            if (sql.contains("class.starting_proficiency_profile")) return List.of(Map.of(
                    "proficiency_profile",
                    "armor.heavy,armor.light,armor.medium,armor.shield,weapon.martial,weapon.simple"));
            if (sql.contains("FROM character_creation_snapshot_v2")) return List.of(Map.of(
                    "strength", 10, "dexterity", 10, "constitution", 14,
                    "intelligence", 10, "wisdom", 10, "charisma", 8));
            if (sql.contains("FROM character_class_level_v2")) return List.of(Map.of(
                    "class_key", "class.fighter", "class_level", 1));
            if (sql.contains("FROM character_feat_state_v2")) return List.of();
            if (sql.contains("FROM character_subclass_state_v2")) return List.of();
            if (sql.contains("FROM character_feature_state_v2")) return featureRows;
            if (sql.contains("FROM character_creation_selection_v2")) return List.of();
            if (sql.contains("FROM character_resource_state_v2")) {
                lockedResources |= sql.contains("FOR UPDATE");
                return List.of(resourceRow(SECOND_WIND), resourceRow(HIT_DIE), resourceRow(HP));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        private static Map<String, Object> resourceRow(
                LevelAdvancementRepository.ResourceState state) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resource_key", state.resourceKey());
            row.put("current_value", state.currentValue());
            row.put("maximum_value", state.maximumValue());
            row.put("is_unlimited", state.unlimited());
            return row;
        }

        private ResultSet resultSet(List<Map<String, Object>> rows) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (ignored, method, arguments) -> switch (
                    method.getName()) {
                case "next" -> ++index[0] < rows.size();
                case "getString" -> {
                    Object value = value(rows, index[0], arguments[0], wasNull);
                    yield value == null ? null : value.toString();
                }
                case "getLong" -> {
                    Object value = value(rows, index[0], arguments[0], wasNull);
                    yield value == null ? 0L : ((Number) value).longValue();
                }
                case "getInt" -> {
                    Object value = value(rows, index[0], arguments[0], wasNull);
                    yield value == null ? 0 : ((Number) value).intValue();
                }
                case "getBoolean" -> {
                    Object value = value(rows, index[0], arguments[0], wasNull);
                    yield value != null && (Boolean) value;
                }
                case "wasNull" -> wasNull[0];
                default -> defaultValue(method.getReturnType());
            });
        }

        private static Object value(List<Map<String, Object>> rows, int index, Object column,
                boolean[] wasNull) {
            Object value = rows.get(index).get(column.toString());
            wasNull[0] = value == null;
            return value;
        }

        private static String target(String sql) {
            String normalized = sql.stripLeading();
            String prefix = normalized.startsWith("INSERT INTO ") ? "INSERT INTO " : "UPDATE ";
            int start = prefix.length();
            return normalized.substring(start, normalized.indexOf(' ', start));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class || type == long.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == double.class || type == float.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
}
