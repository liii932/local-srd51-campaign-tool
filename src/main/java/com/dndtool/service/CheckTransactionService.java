package com.dndtool.service;

import com.dndtool.persistence.CheckEffectPlanRepository;
import com.dndtool.persistence.CheckExecutionRepository;
import com.dndtool.persistence.CharacterVersionRepository;
import com.dndtool.persistence.CheckIdempotencyRepository;
import com.dndtool.persistence.CheckEffectExecutionRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/** Owns the single transaction that turns one validated host command check into authoritative state. */
public final class CheckTransactionService {
    private final DataSource dataSource;
    private final D20CheckCalculator calculator;
    private final CharacterVersionService versionService;
    private final CheckIdempotencyRepository idempotencyRepository;
    private final CheckExecutionRepository checkRepository;
    private final CheckEffectPlanRepository effectPlanRepository;
    private final CheckEffectExecutionService effectService;
    private final CheckEffectExecutionRepository effectRepository;

    public CheckTransactionService(
            DataSource dataSource,
            D20CheckCalculator calculator,
            CharacterVersionService versionService,
            CheckIdempotencyRepository idempotencyRepository,
            CheckExecutionRepository checkRepository,
            CheckEffectPlanRepository effectPlanRepository,
            CheckEffectExecutionService effectService,
            CheckEffectExecutionRepository effectRepository) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.calculator = Objects.requireNonNull(calculator);
        this.versionService = Objects.requireNonNull(versionService);
        this.idempotencyRepository = Objects.requireNonNull(idempotencyRepository);
        this.checkRepository = Objects.requireNonNull(checkRepository);
        this.effectPlanRepository = Objects.requireNonNull(effectPlanRepository);
        this.effectService = Objects.requireNonNull(effectService);
        this.effectRepository = Objects.requireNonNull(effectRepository);
    }

    /** Commits the check, effects, versions and replay root as one authoritative operation. */
    public Result execute(Request request) throws SQLException {
        validate(request);
        Set<String> requiredTargetKeys = effectService.requiredTargetKeys(
                request.success(), request.failure());
        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);

                // Lock the global request identity before versions or randomness so a replay
                // cannot fail on a later row version or consume another die candidate.
                CheckIdempotencyRepository.Lookup existing = idempotencyRepository.find(
                        connection, new CheckIdempotencyRepository.Command(
                                request.requestId(),
                                request.versionRequest().requestDigestSha256(),
                                request.versionRequest().campaignId()));
                if (existing.status() == CheckIdempotencyRepository.Status.CONFLICT) {
                    connection.rollback();
                    restore(connection, original);
                    return Result.idempotencyConflict();
                }
                if (existing.status() == CheckIdempotencyRepository.Status.REPLAY) {
                    connection.commit();
                    restore(connection, original);
                    return Result.replayed(existing.replay());
                }

                CharacterVersionService.Result<WorkResult> versioned =
                        versionService.executeLocked(
                                connection,
                                request.versionRequest(),
                                requiredTargetKeys,
                                (transaction, scope) -> executeLocked(
                                        transaction, scope, request));
                if (versioned.status() != CharacterVersionRepository.Status.LOCKED) {
                    connection.rollback();
                    restore(connection, original);
                    return Result.rejected(
                            versioned.status(), versioned.rejectedCharacterKey(),
                            versioned.currentRowVersion());
                }
                WorkResult work = versioned.value();
                idempotencyRepository.complete(
                        connection, new CheckIdempotencyRepository.Completion(
                                request.requestId(),
                                request.versionRequest().requestDigestSha256(),
                                request.versionRequest().campaignId(),
                                work.savedCheck().gameEventId()));
                connection.commit();
                restore(connection, original);
                return Result.completed(
                        work.savedCheck(), work.calculation(), work.appliedEffects(),
                        versioned.advancedRowVersions());
            } catch (SQLException | RuntimeException exception) {
                rollbackAndRestore(connection, original, exception);
                throw exception;
            }
        }
    }

    private CharacterVersionService.LockedWorkResult<WorkResult> executeLocked(
            Connection connection,
            CharacterVersionRepository.LockedScope scope,
            Request request) throws SQLException {
        List<CheckEffectExecutionService.TargetCharacter> targets =
                scope.charactersById().stream()
                        .map(character -> new CheckEffectExecutionService.TargetCharacter(
                                character.id(), character.characterKey()))
                        .toList();
        // Full effect validation precedes the first server random value and every authoritative write.
        CheckEffectExecutionService.PreparedBranches branches =
                effectService.prepareBranches(targets, request.success(), request.failure());
        // Active encounter membership and every possible destination are locked and checked
        // before the server consumes a die candidate or creates the root event.
        effectRepository.preflightPositions(
                connection,
                new CheckEffectExecutionRepository.PositionPreflight(
                        scope.campaignId(), scope.moduleReleaseId(),
                        branches.success(), branches.failure()));
        D20CheckCalculator.Result calculation = calculator.calculate(
                request.check().rollModeKey(), request.modifierValue(),
                request.check().difficultyClass());
        CheckExecutionRepository.SavedCheck saved = checkRepository.append(
                connection, new CheckExecutionRepository.Command(
                        scope.campaignId(), scope.expectedEventTail(), scope.moduleReleaseId(),
                        scope.executor().id(), eventKey(request.check()), request.check().checkKey(),
                        request.check().modifierSourceKey(), request.check().manualName(),
                        calculation));
        effectPlanRepository.append(connection, new CheckEffectPlanRepository.Command(
                saved.checkExecutionId(), scope.moduleReleaseId(),
                request.success(), request.failure()));
        CheckEffectExecutionRepository.AppliedEffects applied = effectRepository.execute(
                connection, branches.command(
                        saved.checkExecutionId(), scope.campaignId(), scope.moduleReleaseId(),
                        saved.gameEventId()));
        WorkResult result = new WorkResult(saved, calculation, applied);
        return new CharacterVersionService.LockedWorkResult<>(
                result, applied.modifiedCharacterIds());
    }

    private static String eventKey(CheckRequestPolicy.PreparedRequest check) {
        return switch (check.checkEnumCode()) {
            case "ABILITY" -> "event.ability_check";
            case "SKILL" -> "event.skill_check";
            case "SAVING_THROW" -> "event.saving_throw";
            case "MANUAL" -> null;
            default -> throw new IllegalArgumentException("Invalid prepared host command check type");
        };
    }

    private static void validate(Request request) {
        if (request == null || request.versionRequest() == null || request.check() == null
                || request.success() == null || request.failure() == null
                || request.check().effects() == null || !request.check().effects().isEmpty()) {
            throw new IllegalArgumentException("Invalid host command transaction request");
        }
        if (!isCanonicalUuid(request.requestId())
                || request.versionRequest().campaignId() <= 0
                || request.versionRequest().moduleReleaseId() <= 0) {
            throw new IllegalArgumentException("Invalid host command transaction identity");
        }
        validatePreparedCheck(request.check(), request.modifierValue());
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void validatePreparedCheck(
            CheckRequestPolicy.PreparedRequest check, int modifierValue) {
        if (modifierValue < -99 || modifierValue > 99
                || check.difficultyClass() < 0 || check.difficultyClass() > 60) {
            throw new IllegalArgumentException("Invalid prepared host command check values");
        }
        String expectedKey;
        String expectedAlgorithm;
        String sourcePrefix;
        switch (check.checkEnumCode()) {
            case "ABILITY" -> {
                expectedKey = "check.ability";
                expectedAlgorithm = "ABILITY_MODIFIER_V1";
                sourcePrefix = "ability.";
            }
            case "SKILL" -> {
                expectedKey = "check.skill";
                expectedAlgorithm = "SKILL_BONUS_V1";
                sourcePrefix = "skill.";
            }
            case "SAVING_THROW" -> {
                expectedKey = "check.saving_throw";
                expectedAlgorithm = "SAVING_THROW_BONUS_V1";
                sourcePrefix = "save.";
            }
            case "MANUAL" -> {
                expectedKey = "check.manual";
                expectedAlgorithm = "MANUAL_MODIFIER_V1";
                sourcePrefix = null;
            }
            default -> throw new IllegalArgumentException("Invalid prepared host command check type");
        }
        if (!expectedKey.equals(check.checkKey())
                || !expectedAlgorithm.equals(check.modifierAlgorithm())) {
            throw new IllegalArgumentException("Invalid prepared host command check algorithm");
        }
        if (sourcePrefix == null) {
            if (check.modifierSourceKey() != null || check.manualModifier() == null
                    || check.manualModifier() != modifierValue || check.manualName() == null
                    || !check.manualName().equals(
                            CheckTextPolicy.normalizeManualName(check.manualName()))) {
                throw new IllegalArgumentException("Invalid prepared manual check");
            }
        } else if (check.modifierSourceKey() == null
                || !check.modifierSourceKey().startsWith(sourcePrefix)
                || check.manualModifier() != null || check.manualName() != null) {
            throw new IllegalArgumentException("Invalid prepared derived check source");
        }
        boolean validRoll = switch (check.rollModeKey()) {
            case "roll.normal" -> "NORMAL".equals(check.rollModeEnumCode())
                    && check.candidateCount() == 1
                    && "ONLY_CANDIDATE_V1".equals(check.selectionAlgorithm());
            case "roll.advantage" -> "ADVANTAGE".equals(check.rollModeEnumCode())
                    && check.candidateCount() == 2
                    && "HIGHEST_FIRST_ON_TIE_V1".equals(check.selectionAlgorithm());
            case "roll.disadvantage" -> "DISADVANTAGE".equals(check.rollModeEnumCode())
                    && check.candidateCount() == 2
                    && "LOWEST_FIRST_ON_TIE_V1".equals(check.selectionAlgorithm());
            default -> false;
        };
        if (!validRoll) throw new IllegalArgumentException("Invalid prepared host command roll mode");
        eventKey(check);
    }

    private static void rollbackAndRestore(
            Connection connection, ConnectionState original, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
        try {
            restore(connection, original);
        } catch (SQLException restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    private static void restore(Connection connection, ConnectionState original)
            throws SQLException {
        connection.setAutoCommit(original.autoCommit());
        connection.setReadOnly(original.readOnly());
        connection.setTransactionIsolation(original.isolation());
    }

    public record Request(
            String requestId,
            CharacterVersionService.Request versionRequest,
            CheckRequestPolicy.PreparedRequest check,
            int modifierValue,
            CheckEffectPlanRepository.BranchPlan success,
            CheckEffectPlanRepository.BranchPlan failure) {
    }

    public record Result(
            Status status,
            CheckExecutionRepository.SavedCheck savedCheck,
            D20CheckCalculator.Result calculation,
            CheckEffectExecutionRepository.AppliedEffects appliedEffects,
            Map<Long, Long> advancedRowVersions,
            boolean replayed,
            String rejectedCharacterKey,
            Long currentRowVersion) {
        public Result {
            Objects.requireNonNull(status, "transaction result status is required");
            advancedRowVersions = Map.copyOf(advancedRowVersions);
        }

        private static Result completed(
                CheckExecutionRepository.SavedCheck savedCheck,
                D20CheckCalculator.Result calculation,
                CheckEffectExecutionRepository.AppliedEffects appliedEffects,
                Map<Long, Long> versions) {
            return new Result(Status.COMPLETED, savedCheck, calculation, appliedEffects,
                    versions, false, null, null);
        }

        private static Result replayed(CheckIdempotencyRepository.Replay replay) {
            // Effect/write details are not re-executed response data; the durable immutable check
            // identity, candidate rolls and computed outcome are returned exactly as persisted.
            return new Result(Status.COMPLETED, replay.savedCheck(), replay.calculation(), null,
                    Map.of(), true, null, null);
        }

        private static Result idempotencyConflict() {
            return new Result(Status.IDEMPOTENCY_CONFLICT, null, null, null,
                    Map.of(), false, null, null);
        }

        private static Result rejected(
                CharacterVersionRepository.Status status,
                String characterKey,
                Long currentVersion) {
            return new Result(Status.valueOf(status.name()), null, null, null,
                    Map.of(), false, characterKey, currentVersion);
        }
    }

    public enum Status {
        COMPLETED,
        CAMPAIGN_NOT_FOUND,
        CHARACTER_NOT_FOUND,
        CHARACTER_INVALID,
        MODULE_HASH_MISMATCH,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT
    }

    private record WorkResult(
            CheckExecutionRepository.SavedCheck savedCheck,
            D20CheckCalculator.Result calculation,
            CheckEffectExecutionRepository.AppliedEffects appliedEffects) {
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
