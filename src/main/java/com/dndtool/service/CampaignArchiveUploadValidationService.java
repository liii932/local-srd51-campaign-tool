package com.dndtool.service;

import com.dndtool.persistence.ModuleCatalogRepository;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Runs the complete read-only validation available before an import preview may inspect database
 * campaign state. It deliberately returns no archive contents to the HTTP layer.
 */
public final class CampaignArchiveUploadValidationService {
    private final CampaignArchiveReader reader;
    private final CampaignArchiveModuleValidationService moduleValidation;

    public CampaignArchiveUploadValidationService(ModuleCatalogRepository repository) {
        this(new CampaignArchiveReader(), new CampaignArchiveModuleValidationService(repository));
    }

    CampaignArchiveUploadValidationService(
            CampaignArchiveReader reader,
            CampaignArchiveModuleValidationService moduleValidation) {
        this.reader = Objects.requireNonNull(reader, "archive reader");
        this.moduleValidation = Objects.requireNonNull(moduleValidation, "module validation");
    }

    /** Validates untrusted bytes and the referenced immutable catalog without writing state. */
    public Result validate(byte[] content) throws SQLException {
        return new Result(validateForPreview(content).status());
    }

    /** Package-private handoff keeps the validated document inside the service layer. */
    ValidatedArchive validateForPreview(byte[] content) throws SQLException {
        CampaignArchiveReader.Result read = reader.read(content);
        if (read.status() != CampaignArchiveReader.Status.READY) {
            boolean unavailable = read.status() == CampaignArchiveReader.Status.UNSUPPORTED_RELEASE
                    || read.status() == CampaignArchiveReader.Status.UNSUPPORTED_FORMAT;
            return new ValidatedArchive(
                    read.status() == CampaignArchiveReader.Status.FILE_TOO_LARGE
                            ? Status.FILE_TOO_LARGE
                            : unavailable ? Status.MODULE_UNAVAILABLE : Status.INVALID_ARCHIVE,
                    null);
        }

        CampaignArchiveModuleValidationService.Result module =
                moduleValidation.validate(read.document());
        Status status = switch (module.status()) {
            case READY -> Status.READY;
            case MODULE_UNAVAILABLE -> Status.MODULE_UNAVAILABLE;
            case MODULE_HASH_MISMATCH -> Status.MODULE_HASH_MISMATCH;
            case INVALID_CATALOG_REFERENCE -> Status.INVALID_CATALOG_REFERENCE;
            case INVALID_DOCUMENT -> Status.INVALID_ARCHIVE;
        };
        return new ValidatedArchive(
                status, status == Status.READY ? read.document() : null);
    }

    public enum Status {
        READY,
        INVALID_ARCHIVE,
        FILE_TOO_LARGE,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH,
        INVALID_CATALOG_REFERENCE
    }

    public record Result(Status status) {
        public Result {
            Objects.requireNonNull(status, "upload validation status");
        }
    }

    record ValidatedArchive(Status status, CampaignArchiveDocument document) {
        ValidatedArchive {
            Objects.requireNonNull(status, "validated archive status");
            if ((status == Status.READY) != (document != null)) {
                throw new IllegalArgumentException("Archive document does not match status");
            }
        }
    }
}
