package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.CampaignArchiveImportIdempotencyRepository;
import com.dndtool.persistence.CampaignArchiveImportRepository;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

final class CampaignArchiveConfirmationServiceTest {
    private static final String REQUEST = "33333333-3333-4333-8333-333333333333";
    private static final String OTHER = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final byte[] CONTENT = CampaignArchiveJsonWriter.write(
            CampaignArchiveExportServiceTest.snapshot()).getBytes(StandardCharsets.UTF_8);
    private static final String FILE_DIGEST = CampaignArchiveDigest.sha256(CONTENT);
    private static final String DIGEST =
            CampaignArchiveConfirmationRequestDigest.sha256(FILE_DIGEST, null);
    private static final long HOST_STATE_EPOCH = 424_242L;
    private static final CampaignArchiveDocument DOCUMENT = document();

    @Test
    void revalidatesThenCompletesImportAndIdempotencyInOneSerializableTransaction()
            throws Exception {
        Fixture fixture = new Fixture();

        CampaignArchiveConfirmationService.Result result = fixture.service().confirm(request());

        assertEquals(CampaignArchiveConfirmationService.Status.COMPLETED, result.status());
        assertEquals(7L, result.campaignId());
        assertFalse(result.replayed());
        assertEquals(List.of(
                        "validate", "idempotency-find", "epoch", "import",
                        "idempotency-complete"),
                fixture.calls);
        assertEquals(1, fixture.commitCount);
        assertEquals(0, fixture.rollbackCount);
        assertTrue(fixture.closed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.isolation);
    }

    @Test
    void digestMustMatchPreviewHeaderAndActualBytesBeforeBorrowingConnection() throws Exception {
        for (CampaignArchiveConfirmationService.Request request : List.of(
                new CampaignArchiveConfirmationService.Request(
                        REQUEST, "0".repeat(64), FILE_DIGEST, null, CONTENT),
                new CampaignArchiveConfirmationService.Request(
                        REQUEST, DIGEST, "0".repeat(64), null, CONTENT),
                new CampaignArchiveConfirmationService.Request(
                        REQUEST, DIGEST, FILE_DIGEST, null,
                        "changed".getBytes(StandardCharsets.UTF_8)))) {
            Fixture fixture = new Fixture();

            CampaignArchiveConfirmationService.Result result = fixture.service().confirm(request);

            assertEquals(CampaignArchiveConfirmationService.Status.DIGEST_MISMATCH,
                    result.status());
            assertNull(result.campaignId());
            assertFalse(fixture.connectionRequested);
            assertTrue(fixture.calls.isEmpty());
        }
    }

    @Test
    void everyValidationFailureRollsBackBeforeIdempotencyOrImport() throws Exception {
        for (CampaignArchiveConfirmationService.Status status : List.of(
                CampaignArchiveConfirmationService.Status.INVALID_ARCHIVE,
                CampaignArchiveConfirmationService.Status.FILE_TOO_LARGE,
                CampaignArchiveConfirmationService.Status.MODULE_UNAVAILABLE,
                CampaignArchiveConfirmationService.Status.MODULE_HASH_MISMATCH,
                CampaignArchiveConfirmationService.Status.INVALID_CATALOG_REFERENCE)) {
            Fixture fixture = new Fixture();
            fixture.validationStatus = status;

            CampaignArchiveConfirmationService.Result result = fixture.service().confirm(request());

            assertEquals(status, result.status());
            assertEquals(List.of("validate"), fixture.calls);
            assertEquals(0, fixture.commitCount);
            assertEquals(1, fixture.rollbackCount);
            assertTrue(fixture.closed);
        }
    }

    @Test
    void productionValidatorRepeatsStrictReadAndCatalogLookupInsideTransaction()
            throws Exception {
        Fixture malformedFixture = new Fixture();
        byte[] malformed = "{}".getBytes(StandardCharsets.UTF_8);
        String malformedDigest = CampaignArchiveDigest.sha256(malformed);
        CampaignArchiveConfirmationService malformedService = new CampaignArchiveConfirmationService(
                malformedFixture.dataSource(),
                (connection, key, version) -> {
                    throw new AssertionError("Malformed JSON must not reach the module catalog");
                },
                unusedIdempotency(),
                (connection, command) -> { throw new AssertionError("Import must not run"); });

        CampaignArchiveConfirmationService.Result malformedResult = malformedService.confirm(
                new CampaignArchiveConfirmationService.Request(
                        REQUEST,
                        CampaignArchiveConfirmationRequestDigest.sha256(malformedDigest, null),
                        malformedDigest,
                        null,
                        malformed));

        assertEquals(CampaignArchiveConfirmationService.Status.INVALID_ARCHIVE,
                malformedResult.status());
        assertEquals(1, malformedFixture.rollbackCount);

        Fixture missingModuleFixture = new Fixture();
        CampaignArchiveConfirmationService missingModuleService =
                new CampaignArchiveConfirmationService(
                        missingModuleFixture.dataSource(),
                        (connection, key, version) -> {
                            assertFalse(connection.getAutoCommit());
                            assertFalse(connection.isReadOnly());
                            assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                                    connection.getTransactionIsolation());
                            assertEquals(CampaignCreationService.MODULE_KEY, key);
                            assertEquals(CampaignCreationService.RELEASE_VERSION, version);
                            return Optional.empty();
                        },
                        unusedIdempotency(),
                        (connection, command) -> { throw new AssertionError("Import must not run"); });

        CampaignArchiveConfirmationService.Result missingModule =
                missingModuleService.confirm(request());

        assertEquals(CampaignArchiveConfirmationService.Status.MODULE_UNAVAILABLE,
                missingModule.status());
        assertEquals(1, missingModuleFixture.rollbackCount);
    }

    @Test
    void sameRequestAndDigestRevalidatesThenReturnsOriginalCampaignWithoutImportingAgain()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.lookup = CampaignArchiveImportIdempotencyRepository.Lookup.replay(19L);

        CampaignArchiveConfirmationService.Result result = fixture.service().confirm(request());

        assertEquals(CampaignArchiveConfirmationService.Status.COMPLETED, result.status());
        assertEquals(19L, result.campaignId());
        assertTrue(result.replayed());
        assertEquals(List.of("validate", "idempotency-find"), fixture.calls);
        assertEquals(1, fixture.commitCount);
        assertEquals(0, fixture.rollbackCount);
    }

    @Test
    void reusedRequestIdWithDifferentDigestRollsBackBeforeImport() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lookup = CampaignArchiveImportIdempotencyRepository.Lookup.conflict();

        CampaignArchiveConfirmationService.Result result = fixture.service().confirm(request());

        assertEquals(CampaignArchiveConfirmationService.Status.IDEMPOTENCY_CONFLICT,
                result.status());
        assertEquals(List.of("validate", "idempotency-find"), fixture.calls);
        assertEquals(0, fixture.commitCount);
        assertEquals(1, fixture.rollbackCount);
    }

    @Test
    void confirmedArchiveKeyIsDigestBoundAndPassedToTheImportBoundary() throws Exception {
        Fixture fixture = new Fixture();
        fixture.expectedConfirmedArchiveCampaignKey = OTHER;
        fixture.expectedRequestDigest =
                CampaignArchiveConfirmationRequestDigest.sha256(FILE_DIGEST, OTHER);

        CampaignArchiveConfirmationService.Result result =
                fixture.service().confirm(request(OTHER));

        assertEquals(CampaignArchiveConfirmationService.Status.COMPLETED, result.status());
        assertEquals(List.of(
                        "validate", "idempotency-find", "epoch", "import",
                        "idempotency-complete"),
                fixture.calls);

        Fixture changed = new Fixture();
        CampaignArchiveConfirmationService.Request alteredDecision =
                new CampaignArchiveConfirmationService.Request(
                        REQUEST, DIGEST, FILE_DIGEST, OTHER, CONTENT);
        CampaignArchiveConfirmationService.Result rejected =
                changed.service().confirm(alteredDecision);
        assertEquals(CampaignArchiveConfirmationService.Status.DIGEST_MISMATCH,
                rejected.status());
        assertFalse(changed.connectionRequested);
    }

    @Test
    void stableImportRejectionsRollBackWithoutCompletingIdempotency() throws Exception {
        for (CampaignArchiveImportRepository.Rejection rejection :
                CampaignArchiveImportRepository.Rejection.values()) {
            Fixture fixture = new Fixture();
            fixture.importRejection = rejection;

            CampaignArchiveConfirmationService.Result result =
                    fixture.service().confirm(request());

            CampaignArchiveConfirmationService.Status expected = switch (rejection) {
                case ACTIVE_CAMPAIGN_CONFIRMATION_REQUIRED ->
                        CampaignArchiveConfirmationService.Status
                                .ACTIVE_CAMPAIGN_CONFIRMATION_REQUIRED;
                case PREVIEW_STATE_CHANGED ->
                        CampaignArchiveConfirmationService.Status.PREVIEW_STATE_CHANGED;
                case UNEXPECTED_ARCHIVE_CONFIRMATION ->
                        CampaignArchiveConfirmationService.Status
                                .UNEXPECTED_ARCHIVE_CONFIRMATION;
                case STABLE_IDENTITY_CONFLICT ->
                        CampaignArchiveConfirmationService.Status.STABLE_IDENTITY_CONFLICT;
            };
            assertEquals(expected, result.status());
            assertEquals(List.of("validate", "idempotency-find", "epoch", "import"),
                    fixture.calls);
            assertEquals(1, fixture.rollbackCount);
            assertEquals(0, fixture.commitCount);
        }
    }

    @Test
    void importOrIdempotencyFailureRollsBackAndNeverReportsCompletion() {
        for (String failure : List.of("import", "idempotency-complete")) {
            Fixture fixture = new Fixture();
            fixture.failure = failure;

            SQLException exception = assertThrows(SQLException.class,
                    () -> fixture.service().confirm(request()), failure);

            assertTrue(exception.getMessage().contains("synthetic"), failure);
            assertEquals(0, fixture.commitCount, failure);
            assertEquals(1, fixture.rollbackCount, failure);
            assertTrue(fixture.closed, failure);
            List<String> stages = List.of(
                    "validate", "idempotency-find", "epoch", "import",
                    "idempotency-complete");
            assertEquals(stages.subList(0, stages.indexOf(failure) + 1), fixture.calls, failure);
        }
    }

    @Test
    void invalidGeneratedHostStateEpochRollsBackBeforeImport() {
        Fixture fixture = new Fixture();
        fixture.generatedHostStateEpoch = 0L;

        assertThrows(IllegalStateException.class,
                () -> fixture.service().confirm(request()));

        assertEquals(List.of("validate", "idempotency-find", "epoch"), fixture.calls);
        assertEquals(0, fixture.commitCount);
        assertEquals(1, fixture.rollbackCount);
    }

    @Test
    void malformedConfirmationIdentityIsRejectedBeforeConnection() {
        Fixture fixture = new Fixture();

        assertThrows(IllegalArgumentException.class, () -> fixture.service().confirm(
                new CampaignArchiveConfirmationService.Request(
                        "not-a-uuid", DIGEST, FILE_DIGEST, null, CONTENT)));
        assertThrows(IllegalArgumentException.class, () -> fixture.service().confirm(
                new CampaignArchiveConfirmationService.Request(
                        REQUEST, DIGEST, FILE_DIGEST, null, null)));
        assertThrows(IllegalArgumentException.class, () -> fixture.service().confirm(
                new CampaignArchiveConfirmationService.Request(
                        REQUEST, DIGEST, FILE_DIGEST, "not-a-key", CONTENT)));

        assertFalse(fixture.connectionRequested);
        assertTrue(fixture.calls.isEmpty());
    }

    private static CampaignArchiveConfirmationService.Request request() {
        return request(null);
    }

    private static CampaignArchiveConfirmationService.Request request(
            String confirmedArchiveCampaignKey) {
        return new CampaignArchiveConfirmationService.Request(
                REQUEST,
                CampaignArchiveConfirmationRequestDigest.sha256(
                        FILE_DIGEST, confirmedArchiveCampaignKey),
                FILE_DIGEST,
                confirmedArchiveCampaignKey,
                CONTENT);
    }

    private static CampaignArchiveImportIdempotencyRepository unusedIdempotency() {
        return new CampaignArchiveImportIdempotencyRepository() {
            @Override
            public Lookup find(Connection connection, Command command) {
                throw new AssertionError("Idempotency must follow successful validation");
            }

            @Override
            public void complete(Connection connection, Completion completion) {
                throw new AssertionError("Completion must follow actual import work");
            }
        };
    }

    private static CampaignArchiveDocument document() {
        CampaignArchiveReader.Result read = new CampaignArchiveReader().read(CONTENT);
        if (read.status() != CampaignArchiveReader.Status.READY) {
            throw new IllegalStateException("Confirmation test archive is invalid");
        }
        return read.document();
    }

    private static final class Fixture {
        private final List<String> calls = new ArrayList<>();
        private boolean connectionRequested;
        private boolean closed;
        private boolean autoCommit = true;
        private boolean readOnly;
        private int isolation = Connection.TRANSACTION_READ_COMMITTED;
        private int commitCount;
        private int rollbackCount;
        private String failure;
        private String expectedRequestDigest = DIGEST;
        private String expectedConfirmedArchiveCampaignKey;
        private long generatedHostStateEpoch = HOST_STATE_EPOCH;
        private CampaignArchiveImportRepository.Rejection importRejection;
        private CampaignArchiveConfirmationService.Status validationStatus =
                CampaignArchiveConfirmationService.Status.READY;
        private CampaignArchiveImportIdempotencyRepository.Lookup lookup =
                CampaignArchiveImportIdempotencyRepository.Lookup.fresh();

        private CampaignArchiveConfirmationService service() {
            return new CampaignArchiveConfirmationService(
                    dataSource(),
                    (connection, content) -> {
                        calls.add("validate");
                        assertFalse(connection.getAutoCommit());
                        assertFalse(connection.isReadOnly());
                        assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                                connection.getTransactionIsolation());
                        assertEquals(FILE_DIGEST, CampaignArchiveDigest.sha256(content));
                        return validationStatus == CampaignArchiveConfirmationService.Status.READY
                                ? CampaignArchiveConfirmationService.ValidatedArchive.ready(DOCUMENT)
                                : CampaignArchiveConfirmationService.ValidatedArchive.rejected(
                                        validationStatus);
                    },
                    new CampaignArchiveImportIdempotencyRepository() {
                        @Override
                        public Lookup find(Connection connection, Command command) {
                            calls.add("idempotency-find");
                            assertEquals(REQUEST, command.requestId());
                            assertEquals(expectedRequestDigest, command.requestDigestSha256());
                            return lookup;
                        }

                        @Override
                        public void complete(Connection connection, Completion completion)
                                throws SQLException {
                            calls.add("idempotency-complete");
                            if ("idempotency-complete".equals(failure)) {
                                throw new SQLException("synthetic idempotency failure");
                            }
                            assertEquals(7L, completion.campaignId());
                        }
                    },
                    () -> {
                        calls.add("epoch");
                        return generatedHostStateEpoch;
                    },
                    (connection, confirmed, confirmedArchiveCampaignKey, hostStateEpoch) -> {
                        calls.add("import");
                        if (importRejection != null) {
                            throw new CampaignArchiveImportRepository.RejectionException(
                                    importRejection);
                        }
                        if ("import".equals(failure)) {
                            throw new SQLException("synthetic import failure");
                        }
                        assertEquals(DOCUMENT, confirmed);
                        assertEquals(expectedConfirmedArchiveCampaignKey,
                                confirmedArchiveCampaignKey);
                        assertEquals(HOST_STATE_EPOCH, hostStateEpoch);
                        return 7L;
                    });
        }

        private DataSource dataSource() {
            Connection connection = proxy(Connection.class, (ignored, method, arguments) ->
                    switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> { autoCommit = (boolean) arguments[0]; yield null; }
                        case "isReadOnly" -> readOnly;
                        case "setReadOnly" -> { readOnly = (boolean) arguments[0]; yield null; }
                        case "getTransactionIsolation" -> isolation;
                        case "setTransactionIsolation" -> {
                            isolation = (int) arguments[0];
                            yield null;
                        }
                        case "commit" -> { commitCount++; yield null; }
                        case "rollback" -> { rollbackCount++; yield null; }
                        case "close" -> { closed = true; yield null; }
                        default -> defaultValue(method.getReturnType());
                    });
            return proxy(DataSource.class, (ignored, method, arguments) -> {
                if ("getConnection".equals(method.getName())) {
                    connectionRequested = true;
                    return connection;
                }
                return defaultValue(method.getReturnType());
            });
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
