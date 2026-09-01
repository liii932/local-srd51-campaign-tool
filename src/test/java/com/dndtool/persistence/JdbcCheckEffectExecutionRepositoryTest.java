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
import org.junit.jupiter.api.Test;

/** Verifies selected-branch authority and transaction ownership without a real database. */
final class JdbcCheckEffectExecutionRepositoryTest {
    private static final String CHARACTER_KEY = "11111111-1111-1111-1111-111111111111";

    @Test
    void appliesStoredSuccessBranchAcrossFiveAlgorithms() throws Exception {
        Fixture fixture = new Fixture();
        CheckEffectExecutionRepository.AppliedEffects applied = execute(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, List.of(
                        new CheckEffectExecutionRepository.AdjustCurrentHp(
                                1, 41L, CHARACTER_KEY, -5),
                        new CheckEffectExecutionRepository.GrantModuleItem(
                                2, 41L, CHARACTER_KEY, "item.backpack",
                                "Backpack", "A backpack", 2),
                        new CheckEffectExecutionRepository.GrantTemporaryItem(
                                3, 41L, CHARACTER_KEY, "线索", "一张纸条", 1),
                        position(4, "node.cellar"),
                        new CheckEffectExecutionRepository.AppendEventMessage(
                                5, "检定成功"))),
                branch(CheckEffectPlanRepository.EffectBranch.FAILURE, List.of(
                        new CheckEffectExecutionRepository.AppendEventMessage(
                                1, "不得执行"))));

        assertEquals(CheckEffectPlanRepository.EffectBranch.SUCCESS, applied.branch());
        assertEquals(3, applied.fieldChangeCount());
        assertEquals(1, applied.entityPositionChangeCount());
        assertEquals(List.of(701L, 702L), applied.itemInstanceIds());
        assertEquals(java.util.Set.of(41L), applied.modifiedCharacterIds());
        assertTrue(applied.eventMessageWritten());
        assertEquals(List.of(
                "character_field_value", "field_change", "item_instance",
                "field_change", "item_instance", "field_change",
                "entity_position", "game_event"),
                fixture.writeTargets);
        assertEquals(0L, fixture.writeValues.get(0).get(1));
        assertEquals("item.backpack", fixture.writeValues.get(2).get(3));
        assertEquals("线索", fixture.writeValues.get(4).get(2));
        assertEquals("node.cellar", fixture.writeValues.get(6).get(1));
        assertEquals("检定成功", fixture.writeValues.get(7).get(1));
        String hpLockSql = fixture.preparedSql.stream()
                .filter(sql -> sql.contains("FROM character_field_value"))
                .findFirst()
                .orElseThrow();
        assertFalse(hpLockSql.contains("JOIN character_field_value"));
        assertTrue(hpLockSql.contains("field_key IN ('hp.current', 'hp.maximum')"));
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void executesOnlyTheResultBranchStoredWithTheCheck() throws Exception {
        Fixture fixture = new Fixture();
        fixture.checkResult = "FAILURE";

        CheckEffectExecutionRepository.AppliedEffects applied = execute(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, List.of(
                        new CheckEffectExecutionRepository.AdjustCurrentHp(
                                1, 41L, CHARACTER_KEY, -5))),
                branch(CheckEffectPlanRepository.EffectBranch.FAILURE, List.of(
                        new CheckEffectExecutionRepository.AppendEventMessage(
                                1, "检定失败"))));

        assertEquals(CheckEffectPlanRepository.EffectBranch.FAILURE, applied.branch());
        assertEquals(List.of("game_event"), fixture.writeTargets);
        assertTrue(applied.modifiedCharacterIds().isEmpty());
        assertTrue(fixture.preparedSql.stream().noneMatch(sql -> sql.contains("FOR UPDATE")));
    }

    @Test
    void clampsHpAndDoesNotAuditANoOp() throws Exception {
        Fixture fixture = new Fixture();
        fixture.currentHp = 0L;

        CheckEffectExecutionRepository.AppliedEffects applied = execute(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, List.of(
                        new CheckEffectExecutionRepository.AdjustCurrentHp(
                                1, 41L, CHARACTER_KEY, -5))),
                emptyFailure());

        assertEquals(0, applied.fieldChangeCount());
        assertTrue(applied.modifiedCharacterIds().isEmpty());
        assertTrue(fixture.writeTargets.isEmpty());
    }

    @Test
    void rejectsMissingOrDuplicateHpRowsBeforeStateWrites() {
        Fixture missing = new Fixture();
        missing.maximumHpPresent = false;
        assertThrows(SQLException.class, () -> execute(missing,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, List.of(
                        new CheckEffectExecutionRepository.AdjustCurrentHp(
                                1, 41L, CHARACTER_KEY, -5))),
                emptyFailure()));
        assertTrue(missing.writeTargets.isEmpty());

        Fixture duplicate = new Fixture();
        duplicate.duplicateCurrentHp = true;
        assertThrows(SQLException.class, () -> execute(duplicate,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, List.of(
                        new CheckEffectExecutionRepository.AdjustCurrentHp(
                                1, 41L, CHARACTER_KEY, -5))),
                emptyFailure()));
        assertTrue(duplicate.writeTargets.isEmpty());
    }

    @Test
    void rejectsMalformedActionsAndInvalidResultBeforeStateWrites() {
        Fixture fixture = new Fixture();
        CheckEffectExecutionRepository.BranchActions malformed = branch(
                CheckEffectPlanRepository.EffectBranch.SUCCESS,
                List.of(new CheckEffectExecutionRepository.AdjustCurrentHp(
                        2, 41L, CHARACTER_KEY, -5)));
        assertThrows(IllegalArgumentException.class,
                () -> execute(fixture, malformed, emptyFailure()));
        assertTrue(fixture.preparedSql.isEmpty());

        fixture.checkResult = "UNKNOWN";
        assertThrows(SQLException.class, () -> execute(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, List.of()),
                emptyFailure()));
        assertTrue(fixture.writeTargets.isEmpty());
    }

    @Test
    void requiresWritableSerializableCallerTransaction() {
        Fixture fixture = new Fixture();
        fixture.autoCommit = true;
        assertThrows(SQLException.class, () -> execute(fixture, emptySuccess(), emptyFailure()));
        fixture.autoCommit = false;
        fixture.readOnly = true;
        assertThrows(SQLException.class, () -> execute(fixture, emptySuccess(), emptyFailure()));
        fixture.readOnly = false;
        fixture.isolation = Connection.TRANSACTION_READ_COMMITTED;
        assertThrows(SQLException.class, () -> execute(fixture, emptySuccess(), emptyFailure()));
        assertTrue(fixture.preparedSql.isEmpty());
    }

    @Test
    void leavesPartialWriteFailureForTheCallerToRollBack() {
        Fixture fixture = new Fixture();
        fixture.failTarget = "field_change";

        assertThrows(SQLException.class, () -> execute(fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, List.of(
                        new CheckEffectExecutionRepository.GrantTemporaryItem(
                                1, 41L, CHARACTER_KEY, "线索", "", 1))),
                emptyFailure()));

        assertEquals(List.of("item_instance", "field_change"), fixture.writeTargets);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void movesOnlyAnExistingActiveParticipantWithoutChangingPartyPosition() throws Exception {
        Fixture fixture = new Fixture();

        CheckEffectExecutionRepository.AppliedEffects applied = execute(
                fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                        List.of(position(1, "node.cellar"))),
                branch(CheckEffectPlanRepository.EffectBranch.FAILURE,
                        List.of(position(1, "node.entry"))));

        assertEquals(0, applied.fieldChangeCount());
        assertEquals(1, applied.entityPositionChangeCount());
        assertEquals(java.util.Set.of(41L), applied.modifiedCharacterIds());
        assertEquals(List.of("entity_position"), fixture.writeTargets);
        assertEquals("node.cellar", fixture.writeValues.getFirst().get(1));
        assertTrue(fixture.preparedSql.stream().noneMatch(
                sql -> sql.contains("party_world_position")
                        || sql.contains("INSERT INTO battle_participant")));
    }

    @Test
    void sameNodePositionIsANoOpAndDoesNotAdvanceCharacterVersion() throws Exception {
        Fixture fixture = new Fixture();
        fixture.currentPositionNode = "node.cellar";

        CheckEffectExecutionRepository.AppliedEffects applied = execute(
                fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                        List.of(position(1, "node.cellar"))),
                emptyFailure());

        assertEquals(0, applied.entityPositionChangeCount());
        assertTrue(applied.modifiedCharacterIds().isEmpty());
        assertTrue(fixture.writeTargets.isEmpty());
    }

    @Test
    void rejectsMissingParticipantBeforeReadingCheckResultOrWriting() {
        Fixture fixture = new Fixture();
        fixture.participantPresent = false;

        assertThrows(SQLException.class, () -> execute(
                fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                        List.of(position(1, "node.cellar"))),
                emptyFailure()));

        assertTrue(fixture.preparedSql.stream().noneMatch(
                sql -> sql.contains("FROM check_execution")));
        assertTrue(fixture.writeTargets.isEmpty());
    }

    @Test
    void rejectsUnknownFrozenNodeBeforeReadingCheckResultOrWriting() {
        Fixture fixture = new Fixture();
        fixture.knownNodes = List.of("node.entry");

        assertThrows(SQLException.class, () -> execute(
                fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                        List.of(position(1, "node.cellar"))),
                emptyFailure()));

        assertTrue(fixture.preparedSql.stream().noneMatch(
                sql -> sql.contains("FROM check_execution")));
        assertTrue(fixture.writeTargets.isEmpty());
    }

    @Test
    void rejectsActiveEncounterFromAnotherFrozenReleaseBeforeCheckResult() {
        Fixture fixture = new Fixture();
        fixture.battleModuleReleaseId = 12L;

        assertThrows(SQLException.class, () -> execute(
                fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                        List.of(position(1, "node.cellar"))),
                emptyFailure()));

        assertTrue(fixture.preparedSql.stream().noneMatch(
                sql -> sql.contains("FROM check_execution")));
        assertTrue(fixture.writeTargets.isEmpty());
    }

    @Test
    void leavesPositionWriteFailureForTheCallerToRollBack() {
        Fixture fixture = new Fixture();
        fixture.failTarget = "entity_position";

        assertThrows(SQLException.class, () -> execute(
                fixture,
                branch(CheckEffectPlanRepository.EffectBranch.SUCCESS,
                        List.of(position(1, "node.cellar"))),
                emptyFailure()));

        assertEquals(List.of("entity_position"), fixture.writeTargets);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    private static CheckEffectExecutionRepository.AppliedEffects execute(
            Fixture fixture,
            CheckEffectExecutionRepository.BranchActions success,
            CheckEffectExecutionRepository.BranchActions failure) throws SQLException {
        return new JdbcCheckEffectExecutionRepository().execute(
                fixture.connection(),
                new CheckEffectExecutionRepository.Command(
                        301L, 7L, 11L, 401L, success, failure));
    }

    private static CheckEffectExecutionRepository.BranchActions branch(
            CheckEffectPlanRepository.EffectBranch branch,
            List<CheckEffectExecutionRepository.Action> actions) {
        return new CheckEffectExecutionRepository.BranchActions(branch, actions);
    }

    private static CheckEffectExecutionRepository.SetEntityPosition position(
            int order, String nodeKey) {
        return new CheckEffectExecutionRepository.SetEntityPosition(
                order, 41L, CHARACTER_KEY, "map.tavern_cellar", nodeKey);
    }

    private static CheckEffectExecutionRepository.BranchActions emptySuccess() {
        return branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, List.of());
    }

    private static CheckEffectExecutionRepository.BranchActions emptyFailure() {
        return branch(CheckEffectPlanRepository.EffectBranch.FAILURE, List.of());
    }

    private static final class Fixture {
        private final List<String> preparedSql = new ArrayList<>();
        private final List<String> writeTargets = new ArrayList<>();
        private final List<Map<Integer, Object>> writeValues = new ArrayList<>();
        private boolean autoCommit;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_SERIALIZABLE;
        private boolean committed;
        private boolean rolledBack;
        private String checkResult = "SUCCESS";
        private long currentHp = 3L;
        private long maximumHp = 10L;
        private boolean maximumHpPresent = true;
        private boolean duplicateCurrentHp;
        private long battleModuleReleaseId = 11L;
        private List<String> knownNodes = List.of("node.entry", "node.cellar");
        private boolean participantPresent = true;
        private String currentPositionNode = "node.entry";
        private long nextItemId = 701L;
        private String failTarget;

        private Connection connection() {
            return proxy(Connection.class, this::connectionCall);
        }

        private Object connectionCall(Object ignored, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "isReadOnly" -> readOnly;
                case "getTransactionIsolation" -> isolation;
                case "prepareStatement" -> statement((String) arguments[0]);
                case "commit" -> { committed = true; yield null; }
                case "rollback" -> { rolledBack = true; yield null; }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql) {
            preparedSql.add(sql);
            Map<Integer, Object> bound = new HashMap<>();
            return proxy(PreparedStatement.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "setLong", "setInt", "setString" -> {
                            bound.put((int) arguments[0], arguments[1]);
                            yield null;
                        }
                        case "executeQuery" -> queryResult(sql, bound);
                        case "executeUpdate" -> {
                            String target = writeTarget(sql);
                            writeTargets.add(target);
                            writeValues.add(new HashMap<>(bound));
                            if (target.equals(failTarget)) {
                                throw new SQLException("synthetic effect write failure");
                            }
                            yield 1;
                        }
                        case "getGeneratedKeys" -> generatedKeys(nextItemId++);
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private ResultSet queryResult(String sql, Map<Integer, Object> bound) {
            if (sql.contains("FROM check_execution")) {
                return rows(List.of(Map.of("check_result", checkResult)));
            }
            if (sql.contains("FROM character_field_value")) {
                List<Map<Object, Object>> hpRows = new ArrayList<>();
                hpRows.add(Map.of("field_key", "hp.current", "integer_value", currentHp));
                if (duplicateCurrentHp) {
                    hpRows.add(Map.of("field_key", "hp.current", "integer_value", currentHp));
                }
                if (maximumHpPresent) {
                    hpRows.add(Map.of("field_key", "hp.maximum", "integer_value", maximumHp));
                }
                return rows(hpRows);
            }
            if (sql.contains("FROM battle_state")) {
                return rows(List.of(Map.of(
                        "id", 601L,
                        "map_instance_id", 501L,
                        "module_release_id", battleModuleReleaseId,
                        "map_key", "map.tavern_cellar")));
            }
            if (sql.contains("FROM module_map_node")) {
                List<Map<Object, Object>> nodes = knownNodes.stream()
                        .filter(bound::containsValue)
                        .map(node -> Map.<Object, Object>of("node_key", node))
                        .toList();
                return rows(nodes);
            }
            if (sql.contains("FROM battle_participant")) {
                if (!participantPresent) return rows(List.of());
                assertEquals(601L, bound.get(1));
                return rows(List.of(Map.of(
                        "character_id", 41L,
                        "node_key", currentPositionNode)));
            }
            throw new AssertionError("Unexpected query: " + sql);
        }

        private static ResultSet generatedKeys(long id) {
            return rows(List.of(Map.of(1, id)));
        }

        private static ResultSet rows(List<Map<Object, Object>> rows) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return proxy(ResultSet.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "next" -> ++index[0] < rows.size();
                        case "getString" -> {
                            Object value = rows.get(index[0]).get(arguments[0]);
                            wasNull[0] = value == null;
                            yield value == null ? null : value.toString();
                        }
                        case "getLong" -> {
                            Object value = rows.get(index[0]).get(arguments[0]);
                            wasNull[0] = value == null;
                            yield value == null ? 0L : ((Number) value).longValue();
                        }
                        case "wasNull" -> wasNull[0];
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private static String writeTarget(String sql) {
            String normalized = sql.stripLeading();
            String keyword = normalized.startsWith("INSERT") ? "INSERT INTO " : "UPDATE ";
            return normalized.substring(keyword.length()).split("\\s+", 2)[0];
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
