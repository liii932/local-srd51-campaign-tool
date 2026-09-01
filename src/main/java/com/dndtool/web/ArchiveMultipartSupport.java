package com.dndtool.web;

import com.dndtool.service.CampaignSaveFileService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/** Shared strict single-file multipart boundary for archive preview and confirmation. */
final class ArchiveMultipartSupport {
    static final String ARCHIVE_PART_NAME = "archive";

    private ArchiveMultipartSupport() {
    }

    static Result read(HttpServletRequest request) throws IOException {
        if (!isMultipart(request.getContentType())) {
            return Result.failure(Status.MULTIPART_REQUIRED);
        }
        try {
            Collection<Part> parts = request.getParts();
            if (parts.size() != 1) return Result.failure(Status.INVALID_UPLOAD);
            Part archive = parts.iterator().next();
            if (!ARCHIVE_PART_NAME.equals(archive.getName())
                    || archive.getSubmittedFileName() == null
                    || archive.getSize() < 1) {
                return Result.failure(Status.INVALID_UPLOAD);
            }
            if (archive.getSize() > CampaignSaveFileService.MAX_BYTES) {
                return Result.failure(Status.FILE_TOO_LARGE);
            }
            byte[] content;
            try (InputStream input = archive.getInputStream()) {
                content = input.readNBytes(CampaignSaveFileService.MAX_BYTES + 1);
            }
            if (content.length > CampaignSaveFileService.MAX_BYTES) {
                return Result.failure(Status.FILE_TOO_LARGE);
            }
            if (content.length < 1 || content.length != archive.getSize()) {
                return Result.failure(Status.INVALID_UPLOAD);
            }
            return Result.ready(content);
        } catch (IllegalStateException exception) {
            return Result.failure(Status.FILE_TOO_LARGE);
        } catch (ServletException exception) {
            return Result.failure(Status.INVALID_UPLOAD);
        }
    }

    private static boolean isMultipart(String contentType) {
        return contentType != null
                && contentType.regionMatches(true, 0, "multipart/form-data", 0, 19)
                && contentType.length() > 19
                && contentType.charAt(19) == ';';
    }

    enum Status {
        READY,
        MULTIPART_REQUIRED,
        INVALID_UPLOAD,
        FILE_TOO_LARGE
    }

    record Result(Status status, byte[] content) {
        Result {
            if ((status == Status.READY) != (content != null)) {
                throw new IllegalArgumentException("Multipart result does not match status");
            }
            content = content == null ? null : content.clone();
        }

        @Override
        public byte[] content() {
            return content == null ? null : content.clone();
        }

        private static Result ready(byte[] content) {
            return new Result(Status.READY, content);
        }

        private static Result failure(Status status) {
            if (status == Status.READY) throw new IllegalArgumentException("Invalid failure");
            return new Result(status, null);
        }
    }
}
