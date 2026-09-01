package com.dndtool.persistence;

import static com.dndtool.service.CampaignArchiveV2CharacterState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.service.CampaignArchiveV2CharacterState;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JdbcCampaignArchiveV2CharacterStateImportRepositoryTest {
    private static final String CHARACTER = "11111111-1111-4111-8111-111111111111";

    @Test
    void appendsEveryCurrentPartitionWithArchiveOriginsInsideCallerTransaction()
            throws Exception {
        Fixture fixture = new Fixture();
        CampaignArchiveV2CharacterStateImportRepository.Command command = command();

        new JdbcCampaignArchiveV2CharacterStateImportRepository()
                .append(fixture.connection(), command);

        assertEquals(12, fixture.executedSql.size());
        assertTrue(fixture.executedSql.stream().anyMatch(sql ->
                sql.contains("INSERT INTO game_event")));
        assertTrue(fixture.executedSql.stream().anyMatch(sql ->
                sql.contains("character_creation_snapshot_v2")));
        assertTrue(fixture.executedSql.stream().anyMatch(sql ->
                sql.contains("character_feature_choice_v2")));
        assertTrue(fixture.executedSql.stream().anyMatch(sql ->
                sql.contains("character_feat_state_v2")
                        && sql.contains("'ARCHIVE_RESTORE'")));
        assertTrue(fixture.executedSql.stream().anyMatch(sql ->
                sql.contains("character_multiclass_proficiency_v2")
                        && sql.contains("'ARCHIVE_RESTORE'")));
        assertEquals(0, fixture.commitCount);
        assertEquals(0, fixture.rollbackCount);
    }

    @Test
    void requiresCallerTransactionAndLeavesFailureRollbackToCaller() throws Exception {
        Fixture autoCommit = new Fixture();
        autoCommit.autoCommit = true;
        assertThrows(SQLException.class, () ->
                new JdbcCampaignArchiveV2CharacterStateImportRepository()
                        .append(autoCommit.connection(), command()));
        assertTrue(autoCommit.executedSql.isEmpty());

        Fixture failed = new Fixture();
        failed.failAt = 7;
        SQLException exception = assertThrows(SQLException.class, () ->
                new JdbcCampaignArchiveV2CharacterStateImportRepository()
                        .append(failed.connection(), command()));
        assertEquals("injected format-2 JDBC failure", exception.getMessage());
        assertEquals(0, failed.commitCount);
        assertEquals(0, failed.rollbackCount);
        assertFalse(failed.executedSql.isEmpty());
    }

    private static CampaignArchiveV2CharacterStateImportRepository.Command command() {
        return new CampaignArchiveV2CharacterStateImportRepository.Command(
                7, 9, Map.of(CHARACTER, 11L), state());
    }

    private static CampaignArchiveV2CharacterState state() {
        List<AbilityScore> abilities = List.of(
                new AbilityScore("ability.strength", 15, 16),
                new AbilityScore("ability.dexterity", 14, 14),
                new AbilityScore("ability.constitution", 13, 14),
                new AbilityScore("ability.intelligence", 12, 12),
                new AbilityScore("ability.wisdom", 10, 10),
                new AbilityScore("ability.charisma", 8, 8));
        return new CampaignArchiveV2CharacterState(
                2, 12,
                List.of(
                        new StateEvent(1, "LEVEL_ONE_CHARACTER_CREATED", CHARACTER),
                        new StateEvent(8, "CHARACTER_LEVEL_ADVANCED", CHARACTER)),
                List.of(new CreationSnapshot(
                        CHARACTER, 1, "a".repeat(64), "b".repeat(64),
                        "ability.standard_array_v1", "race.human", null,
                        "background.acolyte", "class.fighter", abilities, 12)),
                List.of(new CreationSelection(CHARACTER, "SKILL", 1, "skill.athletics")),
                List.of(new ResourceState(
                        CHARACTER, "resource.hit_points", 12, 12, false)),
                List.of(
                        new ClassLevel(CHARACTER, "class.fighter", 4),
                        new ClassLevel(CHARACTER, "class.rogue", 1)),
                List.of(new SubclassState(
                        CHARACTER, "class.fighter", "subclass.champion", 3, 8)),
                List.of(new FeatureState(
                        CHARACTER, "feature.fighter.fighting_style", 1,
                        "DM_ADJUDICATION", "DM_ADJUDICATION_FIGHTING_STYLE_V1", 1)),
                List.of(new FeatureChoice(
                        CHARACTER, "feature.fighter.fighting_style", 1,
                        "character.feature", "feature.fighter.fighting_style.archery", 1)),
                List.of(new FeatState(CHARACTER, "feat.grappler", 8)),
                List.of(new MulticlassProficiency(
                        CHARACTER, "class.rogue", "proficiency.armor.light", 8)));
    }

    private static final class Fixture {
        private final List<String> executedSql = new ArrayList<>();
        private boolean autoCommit;
        private int executionCount;
        private int failAt = -1;
        private int generatedId = 100;
        private int commitCount;
        private int rollbackCount;

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "prepareStatement" -> statement((String) args[0],
                                args.length > 1 && args[1] instanceof Integer value
                                        && value == Statement.RETURN_GENERATED_KEYS);
                        case "commit" -> { commitCount++; yield null; }
                        case "rollback" -> { rollbackCount++; yield null; }
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement statement(String sql, boolean generated) {
            return (PreparedStatement) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {PreparedStatement.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "executeUpdate" -> {
                            executedSql.add(sql);
                            if (++executionCount == failAt) {
                                throw new SQLException("injected format-2 JDBC failure");
                            }
                            yield 1;
                        }
                        case "getGeneratedKeys" -> resultSet(generated ? ++generatedId : 0);
                        case "close", "setQueryTimeout", "setLong", "setInt", "setString",
                                "setNull" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet resultSet(long id) {
            class Cursor { int index; }
            Cursor cursor = new Cursor();
            return (ResultSet) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "next" -> ++cursor.index == 1 && id > 0;
                        case "getLong" -> id;
                        case "wasNull" -> false;
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
