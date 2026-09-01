package com.dndtool.service;

import com.dndtool.persistence.CheckEffectPlanRepository;
import com.dndtool.persistence.CheckEffectPlanRepository.BranchPlan;
import com.dndtool.persistence.CheckEffectPlanRepository.EffectBranch;
import com.dndtool.persistence.CheckEffectPlanRepository.EffectPlan;
import com.dndtool.persistence.JdbcCampaignArchiveRepository;
import com.dndtool.persistence.JdbcCharacterCardMutationRepository;
import com.dndtool.persistence.JdbcCharacterCardRepository;
import com.dndtool.persistence.JdbcCheckEffectPlanRepository;
import com.dndtool.persistence.JdbcCheckExecutionRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.persistence.JdbcCharacterVersionRepository;
import com.dndtool.persistence.JdbcCheckIdempotencyRepository;
import com.dndtool.persistence.JdbcEntityPositionCommandRepository;
import com.dndtool.persistence.JdbcCheckEffectExecutionRepository;
import com.dndtool.persistence.JdbcEncounterCommandRepository;
import com.dndtool.persistence.JdbcEncounterStateRepository;
import com.dndtool.persistence.JdbcHostCommandContextRepository;
import com.dndtool.persistence.EncounterStateRepository;
import com.dndtool.persistence.CharacterVersionRepository.VersionExpectation;
import com.dndtool.service.CheckRequestPolicy.ClientRequest;
import com.dndtool.service.CheckRequestPolicy.EffectRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Server-owned orchestration behind the protected host-command and archive HTTP endpoints. */
public final class HostCommandService {
    private final DataSource dataSource;
    private final HostCommandContextService contextService;
    private final CharacterCardService cardService;
    private final CampaignArchiveExportService archiveService;

    public HostCommandService(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        JdbcModuleCatalogRepository modules = new JdbcModuleCatalogRepository(dataSource);
        contextService = new HostCommandContextService(
                new JdbcHostCommandContextRepository(dataSource), modules);
        cardService = new CharacterCardService(
                modules,
                new JdbcCharacterCardRepository(dataSource),
                new JdbcCharacterCardMutationRepository(dataSource));
        archiveService = new CampaignArchiveExportService(
                new JdbcCampaignArchiveRepository(dataSource));
    }

    public CheckResult executeCheck(CheckRequest request) throws SQLException {
        if (request == null) return CheckResult.failure(Status.INVALID_REQUEST);
        if (request.possibleTargets() == null
                || request.successEffects() == null
                || request.failureEffects() == null) {
            return CheckResult.failure(Status.INVALID_REQUEST);
        }
        ContextResult contextResult = context();
        if (contextResult.status() != Status.READY) {
            return CheckResult.failure(contextResult.status());
        }
        HostCommandContextService.Context context = contextResult.context();
        try {
            CheckRequestPolicy policy = new CheckRequestPolicy(context.catalog());
            CheckRequestPolicy.PreparedRequest successPrepared = policy.prepare(
                    client(request, request.successEffects()));
            CheckRequestPolicy.PreparedRequest failurePrepared = policy.prepare(
                    client(request, request.failureEffects()));
            CheckRequestPolicy.PreparedRequest check = withoutEffects(successPrepared);
            BranchPlan success = branch(EffectBranch.SUCCESS, successPrepared.effects());
            BranchPlan failure = branch(EffectBranch.FAILURE, failurePrepared.effects());
            int modifier = modifier(request, check);

            VersionExpectation executor = new VersionExpectation(
                    request.executorCharacterKey(), request.executorExpectedRowVersion());
            String payloadSha256 = CheckPayloadDigest.sha256(check, success, failure);
            String digest = CheckRequestDigest.sha256(
                    payloadSha256, executor, request.possibleTargets());
            if (!secureEquals(digest, request.requestDigestSha256())) {
                return CheckResult.failure(Status.INVALID_REQUEST);
            }
            CharacterVersionService.Request versions =
                    new CharacterVersionService.Request(
                            context.campaignId(), context.moduleReleaseId(), payloadSha256,
                            digest, executor, request.possibleTargets());
            CheckTransactionService transaction = new CheckTransactionService(
                    dataSource,
                    new D20CheckCalculator(context.catalog()),
                    new CharacterVersionService(
                            new JdbcCharacterVersionRepository()),
                    new JdbcCheckIdempotencyRepository(),
                    new JdbcCheckExecutionRepository(),
                    new JdbcCheckEffectPlanRepository(),
                    new CheckEffectExecutionService(context.catalog()),
                    new JdbcCheckEffectExecutionRepository());
            CheckTransactionService.Result result = transaction.execute(
                    new CheckTransactionService.Request(
                            request.requestId(), versions, check, modifier, success, failure));
            return new CheckResult(map(result.status()), result);
        } catch (CommandException exception) {
            return CheckResult.failure(exception.status);
        } catch (IllegalArgumentException exception) {
            return CheckResult.failure(Status.INVALID_REQUEST);
        } catch (IllegalStateException exception) {
            return CheckResult.failure(Status.INVALID_STATE);
        }
    }

    public EncounterResult initializeEncounter(EncounterRequest request) throws SQLException {
        if (request == null) return EncounterResult.failure(Status.INVALID_REQUEST);
        if (request.participants() == null) {
            return EncounterResult.failure(Status.INVALID_REQUEST);
        }
        ContextResult contextResult = context();
        if (contextResult.status() != Status.READY) {
            return EncounterResult.failure(contextResult.status());
        }
        HostCommandContextService.Context context = contextResult.context();
        try {
            EncounterCommandTransactionService transaction =
                    new EncounterCommandTransactionService(
                            dataSource,
                            new EncounterStateService(context.catalog()),
                            new JdbcEncounterStateRepository(),
                            new JdbcEncounterCommandRepository());
            EncounterCommandTransactionService.Result result = transaction.initialize(
                    new EncounterCommandTransactionService.Request(
                            request.requestId(), request.requestDigestSha256(),
                            context.campaignId(), context.campaignKey(),
                            context.moduleReleaseId(), request.partyNodeKey(),
                            request.participants()));
            return new EncounterResult(
                    result.status() == EncounterCommandTransactionService.Status.COMPLETED
                            ? Status.COMPLETED
                            : result.status() == EncounterCommandTransactionService.Status
                                    .IDEMPOTENCY_CONFLICT
                                    ? Status.IDEMPOTENCY_CONFLICT : Status.INVALID_REQUEST,
                    result);
        } catch (IllegalArgumentException exception) {
            return EncounterResult.failure(Status.INVALID_REQUEST);
        } catch (EncounterStateRepository.ModuleHashMismatchException exception) {
            return EncounterResult.failure(Status.MODULE_HASH_MISMATCH);
        } catch (IllegalStateException exception) {
            return EncounterResult.failure(Status.INVALID_STATE);
        }
    }

    public PositionResult position(PositionRequest request) throws SQLException {
        if (request == null) return PositionResult.failure(Status.INVALID_REQUEST);
        ContextResult contextResult = context();
        if (contextResult.status() != Status.READY) {
            return PositionResult.failure(contextResult.status());
        }
        HostCommandContextService.Context context = contextResult.context();
        try {
            EntityPositionTransactionService transaction =
                    new EntityPositionTransactionService(
                            dataSource,
                            new EntityPositionService(context.catalog()),
                            new CharacterVersionService(
                                    new JdbcCharacterVersionRepository()),
                            new JdbcEntityPositionCommandRepository());
            EntityPositionTransactionService.Result result = transaction.position(
                    new EntityPositionService.Request(
                            request.requestId(), context.campaignId(),
                            context.moduleReleaseId(), context.campaignKey(),
                            request.characterKey(), request.expectedRowVersion(),
                            request.nodeKey(), request.requestDigestSha256()));
            return new PositionResult(map(result.status()), result);
        } catch (IllegalArgumentException exception) {
            return PositionResult.failure(Status.INVALID_REQUEST);
        } catch (IllegalStateException exception) {
            return PositionResult.failure(Status.INVALID_STATE);
        }
    }

    public ExportResult exportActive() throws SQLException {
        ContextResult contextResult = context();
        if (contextResult.status() != Status.READY) {
            return ExportResult.failure(contextResult.status());
        }
        CampaignArchiveExportService.Result result = archiveService.export(
                contextResult.context().campaignKey());
        return switch (result.status()) {
            case READY -> new ExportResult(Status.READY, result.file());
            case INVALID_REQUEST -> ExportResult.failure(Status.INVALID_REQUEST);
            case NOT_FOUND -> ExportResult.failure(Status.CAMPAIGN_NOT_FOUND);
            case INVALID_STATE -> ExportResult.failure(Status.INVALID_STATE);
            case EXPORT_TOO_LARGE -> ExportResult.failure(Status.EXPORT_TOO_LARGE);
        };
    }

    private int modifier(
            CheckRequest request, CheckRequestPolicy.PreparedRequest prepared)
            throws SQLException {
        if ("MANUAL".equals(prepared.checkEnumCode())) return prepared.manualModifier();
        CharacterCardService.LoadResult loaded = cardService.load(request.executorCharacterKey());
        if (loaded.status() != CharacterCardService.LoadStatus.READY) {
            throw new CommandException(switch (loaded.status()) {
                case INVALID_REQUEST -> Status.INVALID_REQUEST;
                case NOT_FOUND -> Status.CHARACTER_NOT_FOUND;
                case MODULE_UNAVAILABLE -> Status.MODULE_UNAVAILABLE;
                case MODULE_HASH_MISMATCH -> Status.MODULE_HASH_MISMATCH;
                case INVALID_STATE -> Status.INVALID_STATE;
                case READY -> throw new AssertionError();
            });
        }
        CharacterCardService.Card card = loaded.card();
        if (!"ACTIVE".equals(card.characterStatus())) {
            throw new CommandException(Status.CHARACTER_INVALID);
        }
        return switch (prepared.checkEnumCode()) {
            case "ABILITY" -> card.fields().stream()
                    .filter(field -> field.fieldKey().equals(prepared.modifierSourceKey()))
                    .map(CharacterCardService.FieldView::modifier)
                    .filter(Objects::nonNull).findFirst()
                    .orElseThrow(() -> new CommandException(Status.INVALID_STATE));
            case "SKILL" -> card.skills().stream()
                    .filter(value -> value.targetKey().equals(prepared.modifierSourceKey()))
                    .mapToInt(CharacterCardService.ProficiencyView::bonus)
                    .findFirst()
                    .orElseThrow(() -> new CommandException(Status.INVALID_STATE));
            case "SAVING_THROW" -> card.saves().stream()
                    .filter(value -> value.targetKey().equals(prepared.modifierSourceKey()))
                    .mapToInt(CharacterCardService.ProficiencyView::bonus)
                    .findFirst()
                    .orElseThrow(() -> new CommandException(Status.INVALID_STATE));
            default -> throw new IllegalArgumentException("Invalid derived check type");
        };
    }

    private ContextResult context() throws SQLException {
        HostCommandContextService.Result loaded = contextService.loadActive();
        return new ContextResult(switch (loaded.status()) {
            case READY -> Status.READY;
            case CAMPAIGN_NOT_FOUND -> Status.CAMPAIGN_NOT_FOUND;
            case MODULE_UNAVAILABLE -> Status.MODULE_UNAVAILABLE;
            case MODULE_HASH_MISMATCH -> Status.MODULE_HASH_MISMATCH;
            case INVALID_STATE -> Status.INVALID_STATE;
        }, loaded.context());
    }

    private static ClientRequest client(CheckRequest request, List<EffectRequest> effects) {
        return new ClientRequest(
                request.checkKey(), request.rollModeKey(), request.modifierSourceKey(),
                request.manualModifier(), request.manualName(), request.difficultyClass(), effects);
    }

    private static CheckRequestPolicy.PreparedRequest withoutEffects(
            CheckRequestPolicy.PreparedRequest value) {
        return new CheckRequestPolicy.PreparedRequest(
                value.checkKey(), value.checkEnumCode(), value.modifierAlgorithm(),
                value.rollModeKey(), value.rollModeEnumCode(), value.selectionAlgorithm(),
                value.candidateCount(), value.modifierSourceKey(), value.manualModifier(),
                value.manualName(), value.difficultyClass(), List.of());
    }

    private static BranchPlan branch(
            EffectBranch branch, List<CheckRequestPolicy.PreparedEffect> effects) {
        List<EffectPlan> plans = java.util.stream.IntStream.range(0, effects.size())
                .mapToObj(index -> new EffectPlan(index + 1, effects.get(index)))
                .toList();
        return new BranchPlan(branch, plans);
    }

    private static Status map(CheckTransactionService.Status status) {
        return switch (status) {
            case COMPLETED -> Status.COMPLETED;
            case CAMPAIGN_NOT_FOUND -> Status.CAMPAIGN_NOT_FOUND;
            case CHARACTER_NOT_FOUND -> Status.CHARACTER_NOT_FOUND;
            case CHARACTER_INVALID -> Status.CHARACTER_INVALID;
            case MODULE_HASH_MISMATCH -> Status.MODULE_HASH_MISMATCH;
            case VERSION_CONFLICT -> Status.VERSION_CONFLICT;
            case IDEMPOTENCY_CONFLICT -> Status.IDEMPOTENCY_CONFLICT;
        };
    }

    private static Status map(EntityPositionTransactionService.Status status) {
        return switch (status) {
            case COMPLETED -> Status.COMPLETED;
            case CAMPAIGN_NOT_FOUND -> Status.CAMPAIGN_NOT_FOUND;
            case CHARACTER_NOT_FOUND -> Status.CHARACTER_NOT_FOUND;
            case CHARACTER_INVALID -> Status.CHARACTER_INVALID;
            case MODULE_HASH_MISMATCH -> Status.MODULE_HASH_MISMATCH;
            case VERSION_CONFLICT -> Status.VERSION_CONFLICT;
            case IDEMPOTENCY_CONFLICT -> Status.IDEMPOTENCY_CONFLICT;
        };
    }

    private static boolean secureEquals(String expected, String supplied) {
        return supplied != null && supplied.matches("[0-9a-f]{64}")
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        supplied.getBytes(StandardCharsets.US_ASCII));
    }

    public record CheckRequest(
            String requestId,
            String requestDigestSha256,
            String executorCharacterKey,
            long executorExpectedRowVersion,
            String checkKey,
            String rollModeKey,
            String modifierSourceKey,
            Integer manualModifier,
            String manualName,
            int difficultyClass,
            List<VersionExpectation> possibleTargets,
            List<EffectRequest> successEffects,
            List<EffectRequest> failureEffects) {
        public CheckRequest {
            possibleTargets = possibleTargets == null ? null : List.copyOf(possibleTargets);
            successEffects = successEffects == null ? null : List.copyOf(successEffects);
            failureEffects = failureEffects == null ? null : List.copyOf(failureEffects);
        }
    }

    public record EncounterRequest(
            String requestId,
            String requestDigestSha256,
            String partyNodeKey,
            List<EncounterStateService.ParticipantRequest> participants) {
        public EncounterRequest {
            participants = participants == null ? null : List.copyOf(participants);
        }
    }

    public record PositionRequest(
            String requestId,
            String requestDigestSha256,
            String characterKey,
            long expectedRowVersion,
            String nodeKey) {
    }

    public record CheckResult(Status status, CheckTransactionService.Result result) {
        private static CheckResult failure(Status status) { return new CheckResult(status, null); }
    }

    public record EncounterResult(
            Status status, EncounterCommandTransactionService.Result result) {
        private static EncounterResult failure(Status status) {
            return new EncounterResult(status, null);
        }
    }

    public record PositionResult(
            Status status, EntityPositionTransactionService.Result result) {
        private static PositionResult failure(Status status) {
            return new PositionResult(status, null);
        }
    }

    public record ExportResult(
            Status status, CampaignSaveFileService.ExportFile file) {
        private static ExportResult failure(Status status) { return new ExportResult(status, null); }
    }

    public enum Status {
        READY,
        COMPLETED,
        INVALID_REQUEST,
        CAMPAIGN_NOT_FOUND,
        CHARACTER_NOT_FOUND,
        CHARACTER_INVALID,
        VERSION_CONFLICT,
        IDEMPOTENCY_CONFLICT,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH,
        INVALID_STATE,
        EXPORT_TOO_LARGE
    }

    private record ContextResult(
            Status status, HostCommandContextService.Context context) {
    }

    private static final class CommandException extends IllegalArgumentException {
        private final Status status;

        private CommandException(Status status) {
            this.status = status;
        }
    }
}
