package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.CheckEffectPlanRepository;
import com.dndtool.persistence.CheckExecutionRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.CharacterVersionRepository;
import com.dndtool.persistence.CheckIdempotencyRepository;
import com.dndtool.persistence.CheckEffectExecutionRepository;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class CheckTransactionServiceTest {
    private static final String EXECUTOR = "11111111-1111-1111-1111-111111111111";
    private static final String TARGET = "22222222-2222-2222-2222-222222222222";
    private static final String REQUEST = "33333333-3333-3333-3333-333333333333";
    private static final String PAYLOAD = "a".repeat(64);

    @Test
    void commitsAllAuthoritativeWritesAndVersionsExactlyOnce() throws Exception {
        Fixture fixture = new Fixture();

        CheckTransactionService.Result result = fixture.service().execute(
                fixture.request(validSuccessPlan()));

        assertEquals(CheckTransactionService.Status.COMPLETED, result.status());
        assertEquals(201L, result.savedCheck().checkExecutionId());
        assertEquals(Set.of(11L), result.appliedEffects().modifiedCharacterIds());
        assertEquals(Map.of(11L, 5L), result.advancedRowVersions());
        assertEquals(List.of(
                "idempotency-find", "lock", "position-preflight",
                "check", "plan", "effect", "version",
                "idempotency-complete"), fixture.calls);
        assertFalse(result.replayed());
        assertEquals(1, fixture.randomCount);
        assertEquals(1, fixture.commitCount);
        assertEquals(0, fixture.rollbackCount);
        assertTrue(fixture.closed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
    }

    @Test
    void rollsBackWhenAnyPersistenceStageFails() {
        for (String failure : List.of(
                "position-preflight", "check", "plan", "effect", "version",
                "idempotency-complete")) {
            Fixture fixture = new Fixture();
            fixture.failStage = failure;

            SQLException exception = assertThrows(SQLException.class,
                    () -> fixture.service().execute(fixture.request(validSuccessPlan())), failure);

            assertTrue(exception.getMessage().contains("synthetic"), failure);
            assertEquals(0, fixture.commitCount, failure);
            assertEquals(1, fixture.rollbackCount, failure);
            assertTrue(fixture.closed, failure);
            assertTrue(fixture.autoCommit, failure);
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation, failure);
            assertEquals("position-preflight".equals(failure) ? 0 : 1,
                    fixture.randomCount, failure);
            List<String> stages = List.of(
                    "idempotency-find", "lock", "position-preflight",
                    "check", "plan", "effect", "version",
                    "idempotency-complete");
            assertEquals(stages.subList(0, stages.indexOf(failure) + 1), fixture.calls, failure);
        }
    }

    @Test
    void invalidEffectRollsBackBeforeRandomOrAuthoritativeWrite() {
        Fixture fixture = new Fixture();
        CheckEffectPlanRepository.BranchPlan malformed = branch(
                CheckEffectPlanRepository.EffectBranch.SUCCESS,
                hpEffect(1000L));

        assertThrows(CheckEffectExecutionService.EffectExecutionException.class,
                () -> fixture.service().execute(fixture.request(malformed)));

        assertEquals(List.of("idempotency-find", "lock"), fixture.calls);
        assertEquals(0, fixture.randomCount);
        assertEquals(0, fixture.commitCount);
        assertEquals(1, fixture.rollbackCount);
    }

    @Test
    void invalidActiveParticipantFailsPositionPreflightBeforeRandomOrEvent() {
        Fixture fixture = new Fixture();
        fixture.failStage = "position-preflight";
        CheckEffectPlanRepository.BranchPlan positionPlan = branch(
                CheckEffectPlanRepository.EffectBranch.SUCCESS, positionEffect());

        assertThrows(SQLException.class,
                () -> fixture.service().execute(fixture.request(positionPlan)));

        assertEquals(List.of(
                "idempotency-find", "lock", "position-preflight"), fixture.calls);
        assertEquals(0, fixture.randomCount);
        assertEquals(0, fixture.commitCount);
        assertEquals(1, fixture.rollbackCount);
    }

    @Test
    void missingTargetVersionRollsBackBeforeLockOrRandom() {
        Fixture fixture = new Fixture();
        CheckTransactionService.Request valid = fixture.request(validSuccessPlan());
        CharacterVersionService.Request missingTarget = versionRequest(List.of());
        CheckTransactionService.Request request = new CheckTransactionService.Request(
                REQUEST, missingTarget, valid.check(), valid.modifierValue(),
                valid.success(), valid.failure());

        assertThrows(IllegalArgumentException.class, () -> fixture.service().execute(request));

        assertEquals(List.of("idempotency-find"), fixture.calls);
        assertEquals(0, fixture.randomCount);
        assertEquals(1, fixture.rollbackCount);
    }

    @Test
    void versionConflictRollsBackWithoutRandomDiceOrEvents() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lockResult = CharacterVersionRepository.LockResult.rejected(
                CharacterVersionRepository.Status.VERSION_CONFLICT, TARGET, 5L);

        CheckTransactionService.Result result = fixture.service().execute(
                fixture.request(validSuccessPlan()));

        assertEquals(CheckTransactionService.Status.VERSION_CONFLICT, result.status());
        assertEquals(TARGET, result.rejectedCharacterKey());
        assertEquals(5L, result.currentRowVersion());
        assertEquals(List.of("idempotency-find", "lock"), fixture.calls);
        assertEquals(0, fixture.randomCount);
        assertEquals(0, fixture.commitCount);
        assertEquals(1, fixture.rollbackCount);
    }

    @Test
    void savedModuleHashMismatchRollsBackBeforeRandomDiceOrEvents() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lockResult = CharacterVersionRepository.LockResult.rejected(
                CharacterVersionRepository.Status.MODULE_HASH_MISMATCH, null, null);

        CheckTransactionService.Result result = fixture.service().execute(
                fixture.request(validSuccessPlan()));

        assertEquals(CheckTransactionService.Status.MODULE_HASH_MISMATCH,
                result.status());
        assertEquals(List.of("idempotency-find", "lock"), fixture.calls);
        assertEquals(0, fixture.randomCount);
        assertEquals(0, fixture.commitCount);
        assertEquals(1, fixture.rollbackCount);
    }

    @Test
    void forgedPreparedCheckIsRejectedBeforeConnectionOrRandom() {
        Fixture fixture = new Fixture();
        CheckTransactionService.Request valid = fixture.request(validSuccessPlan());
        CheckRequestPolicy.PreparedRequest forged =
                new CheckRequestPolicy.PreparedRequest(
                        "check.ability", "ABILITY", "CLIENT_ALGORITHM",
                        "roll.normal", "NORMAL", "ONLY_CANDIDATE_V1", 1,
                        "ability.strength", null, null, 10, List.of());

        assertThrows(IllegalArgumentException.class, () -> fixture.service().execute(
                new CheckTransactionService.Request(
                        REQUEST, valid.versionRequest(), forged, valid.modifierValue(),
                        valid.success(), valid.failure())));

        assertTrue(fixture.calls.isEmpty());
        assertEquals(0, fixture.randomCount);
        assertEquals(0, fixture.rollbackCount);
        assertFalse(fixture.closed);
    }

    @Test
    void sameRequestRetryReturnsOriginalResultAndRollsOnlyOnce() throws Exception {
        Fixture fixture = new Fixture();
        CheckTransactionService.Result original = fixture.service().execute(
                fixture.request(validSuccessPlan()));
        fixture.lookup = CheckIdempotencyRepository.Lookup.replay(
                new CheckIdempotencyRepository.Replay(
                        original.savedCheck(), original.calculation()));

        CheckTransactionService.Result result = fixture.service().execute(
                fixture.request(validSuccessPlan()));

        assertEquals(CheckTransactionService.Status.COMPLETED, result.status());
        assertTrue(result.replayed());
        assertEquals(201L, result.savedCheck().checkExecutionId());
        assertSame(original.calculation(), result.calculation());
        assertEquals(List.of(
                "idempotency-find", "lock", "position-preflight",
                "check", "plan", "effect", "version",
                "idempotency-complete", "idempotency-find"), fixture.calls);
        assertEquals(1, fixture.randomCount);
        assertEquals(2, fixture.commitCount);
        assertEquals(0, fixture.rollbackCount);
        assertTrue(result.advancedRowVersions().isEmpty());
    }

    @Test
    void reusedRequestIdWithDifferentDigestIsRejectedBeforeAnyAuthoritativeWork()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.lookup = CheckIdempotencyRepository.Lookup.conflict();

        CheckTransactionService.Result result = fixture.service().execute(
                fixture.request(validSuccessPlan()));

        assertEquals(CheckTransactionService.Status.IDEMPOTENCY_CONFLICT, result.status());
        assertEquals(List.of("idempotency-find"), fixture.calls);
        assertEquals(0, fixture.randomCount);
        assertEquals(0, fixture.commitCount);
        assertEquals(1, fixture.rollbackCount);
    }

    private static CheckEffectPlanRepository.BranchPlan validSuccessPlan() {
        return branch(CheckEffectPlanRepository.EffectBranch.SUCCESS, hpEffect(-2L));
    }

    private static CheckEffectPlanRepository.BranchPlan branch(
            CheckEffectPlanRepository.EffectBranch branch,
            CheckRequestPolicy.PreparedEffect... effects) {
        List<CheckEffectPlanRepository.EffectPlan> plans = new ArrayList<>();
        for (int index = 0; index < effects.length; index++) {
            plans.add(new CheckEffectPlanRepository.EffectPlan(index + 1, effects[index]));
        }
        return new CheckEffectPlanRepository.BranchPlan(branch, plans);
    }

    private static CheckRequestPolicy.PreparedEffect hpEffect(long amount) {
        return new CheckRequestPolicy.PreparedEffect(
                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1", List.of(
                        new CheckRequestPolicy.PreparedParameter(
                                "target_character", 1, "REFERENCE",
                                new CheckRequestPolicy.ReferenceValue(TARGET)),
                        new CheckRequestPolicy.PreparedParameter(
                                "amount", 2, "INTEGER",
                        new CheckRequestPolicy.IntegerValue(amount))));
    }

    private static CheckRequestPolicy.PreparedEffect positionEffect() {
        return new CheckRequestPolicy.PreparedEffect(
                "effect.set_entity_position", "SET_ENTITY_NODE_POSITION_V1", List.of(
                        new CheckRequestPolicy.PreparedParameter(
                                "target_character", 1, "REFERENCE",
                                new CheckRequestPolicy.ReferenceValue(TARGET)),
                        new CheckRequestPolicy.PreparedParameter(
                                "map", 2, "REFERENCE",
                                new CheckRequestPolicy.ReferenceValue("map.tavern_cellar")),
                        new CheckRequestPolicy.PreparedParameter(
                                "node", 3, "REFERENCE",
                                new CheckRequestPolicy.ReferenceValue("node.entry"))));
    }

    private static CheckRequestPolicy.PreparedRequest preparedCheck() {
        return new CheckRequestPolicy.PreparedRequest(
                "check.ability", "ABILITY", "ABILITY_MODIFIER_V1",
                "roll.normal", "NORMAL", "ONLY_CANDIDATE_V1", 1,
                "ability.strength", null, null, 10, List.of());
    }

    private static CharacterVersionService.Request versionRequest(
            List<CharacterVersionRepository.VersionExpectation> targets) {
        CharacterVersionRepository.VersionExpectation executor =
                new CharacterVersionRepository.VersionExpectation(EXECUTOR, 2L);
        return new CharacterVersionService.Request(
                7L, 11L, PAYLOAD,
                CheckRequestDigest.sha256(PAYLOAD, executor, targets),
                executor, targets);
    }

    private static ModuleCatalog catalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1,
                        "SHA-256", "a".repeat(64), "RELEASED"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(
                        new ModuleCatalog.RollMode(
                                "roll.normal", "NORMAL", 1, "ONLY_CANDIDATE_V1"),
                        new ModuleCatalog.RollMode(
                                "roll.advantage", "ADVANTAGE", 2,
                                "HIGHEST_FIRST_ON_TIE_V1"),
                        new ModuleCatalog.RollMode(
                                "roll.disadvantage", "DISADVANTAGE", 2,
                                "LOWEST_FIRST_ON_TIE_V1")),
                List.of(), List.of(), List.of(),
                List.of(
                        new ModuleCatalog.EffectDefinition(
                                "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.grant_module_item", "GRANT_MODULE_ITEM_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.set_entity_position", "SET_ENTITY_NODE_POSITION_V1"),
                        new ModuleCatalog.EffectDefinition(
                                "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1")),
                List.of(),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.entry", "Entry"),
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.cellar", "Cellar")),
                List.of());
    }

    private static final class Fixture
            implements CharacterVersionRepository,
                    CheckIdempotencyRepository,
                    CheckExecutionRepository,
                    CheckEffectPlanRepository,
                    CheckEffectExecutionRepository {
        private final List<String> calls = new ArrayList<>();
        private final Connection connection = connectionProxy();
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private int commitCount;
        private int rollbackCount;
        private int randomCount;
        private boolean closed;
        private String failStage;
        private Lookup lookup = Lookup.fresh();
        private LockResult lockResult = LockResult.locked(new LockedScope(
                7L, 11L, 30L,
                new LockedCharacter(10L, EXECUTOR, 7L, 11L, 2L),
                List.of(
                        new LockedCharacter(10L, EXECUTOR, 7L, 11L, 2L),
                        new LockedCharacter(11L, TARGET, 7L, 11L, 4L))));

        private CheckTransactionService service() {
            ModuleCatalog catalog = catalog();
            D20CheckCalculator calculator = new D20CheckCalculator(catalog, () -> {
                randomCount++;
                return 15;
            });
            return new CheckTransactionService(
                    dataSource(), calculator, new CharacterVersionService(this),
                    this, this, this, new CheckEffectExecutionService(catalog), this);
        }

        private CheckTransactionService.Request request(
                CheckEffectPlanRepository.BranchPlan success) {
            List<CharacterVersionRepository.VersionExpectation> targets = List.of(
                    new CharacterVersionRepository.VersionExpectation(TARGET, 4L));
            return new CheckTransactionService.Request(
                    REQUEST, versionRequest(targets), preparedCheck(), 3,
                    success, branch(CheckEffectPlanRepository.EffectBranch.FAILURE));
        }

        private DataSource dataSource() {
            return (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getConnection" -> connection;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        private Connection connectionProxy() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "isReadOnly" -> readOnly;
                        case "getTransactionIsolation" -> isolation;
                        case "setAutoCommit" -> { autoCommit = (boolean) arguments[0]; yield null; }
                        case "setReadOnly" -> { readOnly = (boolean) arguments[0]; yield null; }
                        case "setTransactionIsolation" -> {
                            isolation = (int) arguments[0]; yield null;
                        }
                        case "commit" -> { commitCount++; yield null; }
                        case "rollback" -> { rollbackCount++; yield null; }
                        case "close" -> { closed = true; yield null; }
                        case "isClosed" -> closed;
                        default -> defaultValue(method.getReturnType());
                    });
        }

        @Override
        public LockResult lockBeforeRoll(Connection connection, LockCommand command) {
            assertSame(this.connection, connection);
            calls.add("lock");
            return lockResult;
        }

        @Override
        public Lookup find(Connection connection, CheckIdempotencyRepository.Command command)
                throws SQLException {
            assertSame(this.connection, connection);
            calls.add("idempotency-find");
            fail("idempotency-find");
            assertEquals(REQUEST, command.requestId());
            assertEquals(7L, command.campaignId());
            return lookup;
        }

        @Override
        public void complete(
                Connection connection, CheckIdempotencyRepository.Completion completion)
                throws SQLException {
            assertSame(this.connection, connection);
            calls.add("idempotency-complete");
            fail("idempotency-complete");
            assertEquals(REQUEST, completion.requestId());
            assertEquals(101L, completion.gameEventId());
        }

        @Override
        public Map<Long, Long> advanceModifiedVersions(
                Connection connection, LockedScope scope, Set<Long> modifiedCharacterIds)
                throws SQLException {
            assertSame(this.connection, connection);
            calls.add("version");
            fail("version");
            assertEquals(Set.of(11L), modifiedCharacterIds);
            return Map.of(11L, 5L);
        }

        @Override
        public SavedCheck append(Connection connection, CheckExecutionRepository.Command command)
                throws SQLException {
            assertSame(this.connection, connection);
            calls.add("check");
            fail("check");
            assertEquals(30L, command.expectedEventTail());
            assertEquals(10L, command.executorCharacterId());
            return new SavedCheck(101L, 201L, 31L);
        }

        @Override
        public SavedPlan append(Connection connection, CheckEffectPlanRepository.Command command)
                throws SQLException {
            assertSame(this.connection, connection);
            calls.add("plan");
            fail("plan");
            assertEquals(201L, command.checkExecutionId());
            return new SavedPlan(201L, List.of());
        }

        @Override
        public void preflightPositions(
                Connection connection,
                CheckEffectExecutionRepository.PositionPreflight preflight)
                throws SQLException {
            assertSame(this.connection, connection);
            calls.add("position-preflight");
            fail("position-preflight");
            assertEquals(7L, preflight.campaignId());
            assertEquals(11L, preflight.moduleReleaseId());
        }

        @Override
        public AppliedEffects execute(
                Connection connection, CheckEffectExecutionRepository.Command command)
                throws SQLException {
            assertSame(this.connection, connection);
            calls.add("effect");
            fail("effect");
            assertEquals(201L, command.checkExecutionId());
            return new AppliedEffects(
                    EffectBranch.SUCCESS, 1, 0, List.of(), Set.of(11L), false);
        }

        private void fail(String stage) throws SQLException {
            if (stage.equals(failStage)) throw new SQLException("synthetic " + stage + " failure");
        }
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
