package com.dndtool.persistence;

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
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import com.dndtool.service.ClassFeatureRules;
import org.junit.jupiter.api.Test;

class JdbcLevelOneCharacterCreationRepositoryTest {
    private static final String HASH = "a".repeat(64);
    private static final LevelOneCharacterCreationRepository.Command COMMAND =
            new LevelOneCharacterCreationRepository.Command(
                    "123e4567-e89b-42d3-a456-426614174000", "b".repeat(64),
                    "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                    "11111111-2222-4333-8444-555555555555", "守卫",
                    "dnd5e2014_srd51_se", "1", HASH, 7L, "c".repeat(64),
                    "race.dwarf", "subrace.hill_dwarf", "background.acolyte",
                    "class.fighter",
                    Map.of("ability.strength", 15, "ability.dexterity", 12,
                            "ability.constitution", 14, "ability.intelligence", 10,
                            "ability.wisdom", 13, "ability.charisma", 8),
                    Map.of("ability.strength", 15, "ability.dexterity", 12,
                            "ability.constitution", 16, "ability.intelligence", 10,
                            "ability.wisdom", 14, "ability.charisma", 8),
                    List.of(new LevelOneCharacterCreationRepository.Selection(
                            "SKILL", "skill.athletics")), 13, 10,
                    List.of(
                            new LevelOneCharacterCreationRepository.InitialResource(
                                    "resource.hit_dice.d10", 1, 1, false),
                            new LevelOneCharacterCreationRepository.InitialResource(
                                    "resource.fighter.second_wind", 1, 1, false)), null);

    @Test
    void commitsRootSnapshotSelectionsResourceEventAuditAndIdempotencyTogether()
            throws Exception {
        Fixture fixture = new Fixture();

        new JdbcLevelOneCharacterCreationRepository(fixture.dataSource()).confirm(COMMAND);

        for (String target : List.of("character_record", "character_creation_snapshot_v2",
                "character_creation_selection_v2", "character_class_level_v2",
                "character_resource_state_v2",
                "campaign", "game_event", "field_change", "host_operation")) {
            assertTrue(fixture.targets.contains(target), target);
        }
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.serializable);
        assertTrue(fixture.closed);
    }

    @Test
    void creationPersistsInitialSubclassAndFeaturesWithTheRootEvent() throws Exception {
        Fixture fixture = new Fixture();
        ClassFeatureRules.Transition transition = new ClassFeatureRules.Transition(
                "subclass.life", "subclass.life", List.of(
                        new ClassFeatureRules.FeatureRule("feature.cleric.spellcasting",
                                "character.class", "class.cleric", 1, "BASE", "BLOCKED",
                                "BLOCKED_SPELL_SYSTEM_V1")));
        LevelOneCharacterCreationRepository.Command command = copyWithTransition(transition);

        new JdbcLevelOneCharacterCreationRepository(fixture.dataSource()).confirm(command);

        assertTrue(fixture.targets.contains("character_subclass_state_v2"));
        assertTrue(fixture.targets.contains("character_feature_state_v2"));
        assertTrue(fixture.targets.contains("host_operation"));
        assertTrue(fixture.committed);
    }

    @Test
    void initialFeatureFailureRollsBackTheCompleteCreation() {
        Fixture fixture = new Fixture();
        fixture.failTarget = "character_feature_state_v2";
        ClassFeatureRules.Transition transition = new ClassFeatureRules.Transition(
                null, null, List.of(new ClassFeatureRules.FeatureRule(
                        "feature.fighter.second_wind", "character.class", "class.fighter", 1,
                        "BASE", "AUTOMATIC", "AUTOMATIC_RESOURCE_LIFECYCLE_V1")));

        assertThrows(SQLException.class, () ->
                new JdbcLevelOneCharacterCreationRepository(fixture.dataSource())
                        .confirm(copyWithTransition(transition)));

        assertTrue(fixture.targets.contains("character_feature_state_v2"));
        assertFalse(fixture.targets.contains("host_operation"));
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
    }

    @Test
    void snapshotFailureRollsBackBeforeResourceEventAndOperation() {
        Fixture fixture = new Fixture();
        fixture.failTarget = "character_creation_snapshot_v2";

        assertThrows(SQLException.class, () ->
                new JdbcLevelOneCharacterCreationRepository(fixture.dataSource()).confirm(COMMAND));

        assertTrue(fixture.targets.contains("character_record"));
        assertTrue(fixture.targets.contains("character_creation_snapshot_v2"));
        assertFalse(fixture.targets.contains("character_resource_state_v2"));
        assertFalse(fixture.targets.contains("game_event"));
        assertFalse(fixture.targets.contains("host_operation"));
        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
    }

    private static LevelOneCharacterCreationRepository.Command copyWithTransition(
            ClassFeatureRules.Transition transition) {
        return new LevelOneCharacterCreationRepository.Command(COMMAND.requestId(),
                COMMAND.requestDigestSha256(), COMMAND.characterKey(), COMMAND.campaignKey(),
                COMMAND.characterName(), COMMAND.moduleKey(), COMMAND.releaseVersion(),
                COMMAND.contentSha256(), COMMAND.expectedEventTail(),
                COMMAND.previewDigestSha256(), COMMAND.raceKey(), COMMAND.subraceKey(),
                COMMAND.backgroundKey(), "class.cleric", COMMAND.baseAbilityScores(),
                COMMAND.finalAbilityScores(), COMMAND.selections(), COMMAND.maximumHitPoints(),
                COMMAND.hitDieSides(), COMMAND.initialResources(), transition);
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
        private String failTarget;

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
            return proxy(PreparedStatement.class, (ignored, method, arguments) -> switch (method.getName()) {
                case "setString", "setLong", "setInt", "setBoolean", "setNull",
                        "setQueryTimeout" -> null;
                case "executeQuery" -> resultSet(rows(sql));
                case "executeUpdate" -> update(sql);
                case "addBatch" -> null;
                case "executeBatch" -> new int[] {update(sql)};
                case "getGeneratedKeys" -> generated ? resultSet(List.of(Map.of("1",
                        sql.contains("game_event") ? 202L : 101L))) : resultSet(List.of());
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
            if (sql.contains("FROM host_operation")) return List.of();
            if (sql.contains("FROM campaign\n")) return List.of(Map.of(
                    "id", 7L, "internal_event_tail", 7L));
            if (sql.contains("FROM campaign_module")) return List.of(Map.of(
                    "module_release_id", 9L, "frozen_module_key", "dnd5e2014_srd51_se",
                    "frozen_release_version", "1", "frozen_content_sha256", HASH));
            throw new AssertionError("Unexpected query: " + sql);
        }

        private ResultSet resultSet(List<Map<String, Object>> rows) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (ignored, method, arguments) -> switch (method.getName()) {
                case "next" -> ++index[0] < rows.size();
                case "getString" -> value(rows, index[0], arguments[0], wasNull).toString();
                case "getLong" -> {
                    Object value = value(rows, index[0], arguments[0], wasNull);
                    yield value == null ? 0L : ((Number) value).longValue();
                }
                case "wasNull" -> wasNull[0];
                default -> defaultValue(method.getReturnType());
            });
        }

        private static Object value(List<Map<String, Object>> rows, int index, Object column,
                boolean[] wasNull) {
            String key = column instanceof Integer ? column.toString() : column.toString();
            Object value = rows.get(index).get(key);
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
        if (type == int.class || type == long.class || type == short.class || type == byte.class) return 0;
        if (type == double.class || type == float.class) return 0.0;
        if (type == char.class) return '\0';
        return null;
    }
}
