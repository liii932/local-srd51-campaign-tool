package com.dndtool.service;

import com.dndtool.persistence.CampaignArchiveImportIdempotencyRepository;
import com.dndtool.persistence.CampaignArchiveImportRepository;
import com.dndtool.persistence.ModuleCatalogRepository;
import com.dndtool.persistence.TransactionalModuleCatalogRepository;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import javax.sql.DataSource;

/**
 * Revalidates confirmed archive bytes and owns the single transaction used by the later whole
 * campaign import. The loopback host filter remains responsible for Session and CSRF checks.
 */
public final class CampaignArchiveConfirmationService {
    private static final SecureRandom HOST_STATE_EPOCH_RANDOM = new SecureRandom();

    private final DataSource dataSource;
    private final ArchiveValidator validator;
    private final CampaignArchiveImportIdempotencyRepository idempotencyRepository;
    private final LongSupplier hostStateEpochSupplier;
    private final ConfirmedImportWork importWork;

    public CampaignArchiveConfirmationService(
            DataSource dataSource,
            TransactionalModuleCatalogRepository moduleRepository,
            CampaignArchiveImportIdempotencyRepository idempotencyRepository,
            CampaignArchiveImportRepository importRepository) {
        this(
                dataSource,
                transactionalValidator(Objects.requireNonNull(moduleRepository)),
                idempotencyRepository,
                CampaignArchiveConfirmationService::randomHostStateEpoch,
                importWork(Objects.requireNonNull(importRepository)));
    }

    CampaignArchiveConfirmationService(
            DataSource dataSource,
            ArchiveValidator validator,
            CampaignArchiveImportIdempotencyRepository idempotencyRepository,
            LongSupplier hostStateEpochSupplier,
            ConfirmedImportWork importWork) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.validator = Objects.requireNonNull(validator, "archive validator");
        this.idempotencyRepository = Objects.requireNonNull(
                idempotencyRepository, "import idempotency repository");
        this.hostStateEpochSupplier = Objects.requireNonNull(
                hostStateEpochSupplier, "host state epoch supplier");
        this.importWork = Objects.requireNonNull(importWork, "confirmed import work");
    }

    /**
     * Requires the request digest, preview digest and actual raw bytes to match before borrowing a
     * connection, then repeats strict JSON and frozen-catalog validation inside the write snapshot.
     */
    public Result confirm(Request request) throws SQLException {
        validateRequest(request);
        byte[] content = request.content();
        String actualFileDigest = CampaignArchiveDigest.sha256(content);
        String actualRequestDigest = CampaignArchiveConfirmationRequestDigest.sha256(
                actualFileDigest, request.confirmedArchiveCampaignKey());
        if (!secureEquals(actualFileDigest, request.previewFileSha256())
                || !secureEquals(actualRequestDigest, request.requestDigestSha256())) {
            return Result.rejected(Status.DIGEST_MISMATCH);
        }

        try (Connection connection = dataSource.getConnection()) {
            ConnectionState original = ConnectionState.capture(connection);
            try {
                connection.setReadOnly(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                connection.setAutoCommit(false);

                // Confirmation is deliberately not allowed to trust the earlier preview snapshot.
                ValidatedArchive validated = validator.validate(connection, content);
                if (validated.status() != Status.READY) {
                    connection.rollback();
                    restore(connection, original);
                    return Result.rejected(validated.status());
                }

                CampaignArchiveImportIdempotencyRepository.Lookup existing =
                        idempotencyRepository.find(
                                connection,
                                new CampaignArchiveImportIdempotencyRepository.Command(
                                        request.requestId(), request.requestDigestSha256()));
                if (existing.status()
                        == CampaignArchiveImportIdempotencyRepository.Status.CONFLICT) {
                    connection.rollback();
                    restore(connection, original);
                    return Result.rejected(Status.IDEMPOTENCY_CONFLICT);
                }
                if (existing.status()
                        == CampaignArchiveImportIdempotencyRepository.Status.REPLAY) {
                    connection.commit();
                    restore(connection, original);
                    return Result.completed(existing.campaignId(), true);
                }

                long hostStateEpoch = nextHostStateEpoch();
                long campaignId;
                try {
                    campaignId = importWork.execute(
                            connection,
                            validated.document(),
                            request.confirmedArchiveCampaignKey(),
                            hostStateEpoch);
                } catch (CampaignArchiveImportRepository.RejectionException rejection) {
                    connection.rollback();
                    restore(connection, original);
                    return Result.rejected(switch (rejection.rejection()) {
                        case ACTIVE_CAMPAIGN_CONFIRMATION_REQUIRED ->
                                Status.ACTIVE_CAMPAIGN_CONFIRMATION_REQUIRED;
                        case PREVIEW_STATE_CHANGED -> Status.PREVIEW_STATE_CHANGED;
                        case UNEXPECTED_ARCHIVE_CONFIRMATION ->
                                Status.UNEXPECTED_ARCHIVE_CONFIRMATION;
                        case STABLE_IDENTITY_CONFLICT -> Status.STABLE_IDENTITY_CONFLICT;
                    });
                }
                if (campaignId <= 0) {
                    throw new SQLException("Confirmed import returned an invalid campaign id");
                }
                idempotencyRepository.complete(
                        connection,
                        new CampaignArchiveImportIdempotencyRepository.Completion(
                                request.requestId(), request.requestDigestSha256(), campaignId));
                connection.commit();
                restore(connection, original);
                return Result.completed(campaignId, false);
            } catch (SQLException | RuntimeException failure) {
                rollbackAndRestore(connection, original, failure);
                throw failure;
            }
        }
    }

    private static ArchiveValidator transactionalValidator(
            TransactionalModuleCatalogRepository repository) {
        CampaignArchiveReader reader = new CampaignArchiveReader();
        return (connection, content) -> {
            CampaignArchiveReader.Result read = reader.read(content);
            if (read.status() != CampaignArchiveReader.Status.READY) {
                return ValidatedArchive.rejected(
                        read.status() == CampaignArchiveReader.Status.FILE_TOO_LARGE
                                ? Status.FILE_TOO_LARGE : Status.INVALID_ARCHIVE);
            }
            ModuleCatalogRepository lookup = (moduleKey, releaseVersion) ->
                    repository.findByIdentity(connection, moduleKey, releaseVersion);
            CampaignArchiveModuleValidationService.Result module =
                    new CampaignArchiveModuleValidationService(lookup).validate(read.document());
            Status status = switch (module.status()) {
                case READY -> Status.READY;
                case MODULE_UNAVAILABLE -> Status.MODULE_UNAVAILABLE;
                case MODULE_HASH_MISMATCH -> Status.MODULE_HASH_MISMATCH;
                case INVALID_CATALOG_REFERENCE -> Status.INVALID_CATALOG_REFERENCE;
                case INVALID_DOCUMENT -> Status.INVALID_ARCHIVE;
            };
            return status == Status.READY
                    ? ValidatedArchive.ready(read.document())
                    : ValidatedArchive.rejected(status);
        };
    }

    private static ConfirmedImportWork importWork(CampaignArchiveImportRepository repository) {
        return (connection, document, confirmedArchiveCampaignKey, hostStateEpoch) ->
                repository.importArchive(
                        connection,
                        new CampaignArchiveImportRepository.Command(
                                document, confirmedArchiveCampaignKey, hostStateEpoch));
    }

    private long nextHostStateEpoch() {
        long value = hostStateEpochSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("Host state epoch generator returned a non-positive value");
        }
        return value;
    }

    private static long randomHostStateEpoch() {
        return HOST_STATE_EPOCH_RANDOM.nextLong(Long.MAX_VALUE) + 1L;
    }

    private static void validateRequest(Request request) {
        if (request == null || !canonicalUuid(request.requestId())
                || !sha256(request.requestDigestSha256())
                || !sha256(request.previewFileSha256())
                || request.confirmedArchiveCampaignKey() != null
                && !canonicalCampaignKey(request.confirmedArchiveCampaignKey())
                || request.content() == null) {
            throw new IllegalArgumentException("Invalid archive confirmation request");
        }
    }

    private static boolean canonicalUuid(String value) {
        if (value == null || value.length() != 36) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean canonicalCampaignKey(String value) {
        if (!canonicalUuid(value)) return false;
        UUID uuid = UUID.fromString(value);
        return uuid.version() == 4 && uuid.variant() == 2;
    }

    private static boolean sha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean secureEquals(String left, String right) {
        return MessageDigest.isEqual(
                HexFormat.of().parseHex(left), HexFormat.of().parseHex(right));
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
            String requestDigestSha256,
            String previewFileSha256,
            String confirmedArchiveCampaignKey,
            byte[] content) {
        public Request {
            content = content == null ? null : content.clone();
        }

        @Override
        public byte[] content() {
            return content == null ? null : content.clone();
        }
    }

    public record Result(Status status, Long campaignId, boolean replayed) {
        public Result {
            Objects.requireNonNull(status, "confirmation status");
            if (status == Status.READY
                    || (status == Status.COMPLETED) != (campaignId != null)
                    || campaignId != null && campaignId <= 0
                    || replayed && status != Status.COMPLETED) {
                throw new IllegalArgumentException("Invalid archive confirmation result");
            }
        }

        private static Result completed(long campaignId, boolean replayed) {
            return new Result(Status.COMPLETED, campaignId, replayed);
        }

        private static Result rejected(Status status) {
            return new Result(status, null, false);
        }
    }

    public enum Status {
        READY,
        COMPLETED,
        DIGEST_MISMATCH,
        INVALID_ARCHIVE,
        FILE_TOO_LARGE,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH,
        INVALID_CATALOG_REFERENCE,
        IDEMPOTENCY_CONFLICT,
        ACTIVE_CAMPAIGN_CONFIRMATION_REQUIRED,
        PREVIEW_STATE_CHANGED,
        UNEXPECTED_ARCHIVE_CONFIRMATION,
        STABLE_IDENTITY_CONFLICT
    }

    @FunctionalInterface
    public interface ConfirmedImportWork {
        /** Performs the later whole-campaign import without owning the supplied transaction. */
        long execute(
                Connection connection,
                CampaignArchiveDocument document,
                String confirmedArchiveCampaignKey,
                long hostStateEpoch) throws SQLException;
    }

    @FunctionalInterface
    interface ArchiveValidator {
        ValidatedArchive validate(Connection connection, byte[] content) throws SQLException;
    }

    record ValidatedArchive(Status status, CampaignArchiveDocument document) {
        ValidatedArchive {
            Objects.requireNonNull(status, "confirmation validation status");
            if ((status == Status.READY) != (document != null)) {
                throw new IllegalArgumentException("Confirmation document does not match status");
            }
        }

        static ValidatedArchive ready(CampaignArchiveDocument document) {
            return new ValidatedArchive(Status.READY, Objects.requireNonNull(document));
        }

        static ValidatedArchive rejected(Status status) {
            if (status == Status.READY || status == Status.COMPLETED) {
                throw new IllegalArgumentException("Invalid confirmation rejection status");
            }
            return new ValidatedArchive(status, null);
        }
    }

    private record ConnectionState(boolean autoCommit, boolean readOnly, int isolation) {
        private static ConnectionState capture(Connection connection) throws SQLException {
            return new ConnectionState(
                    connection.getAutoCommit(), connection.isReadOnly(),
                    connection.getTransactionIsolation());
        }
    }
}
