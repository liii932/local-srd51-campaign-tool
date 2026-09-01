package com.dndtool.service;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Encodes a server-owned campaign JSON document into the bounded local save-file envelope. */
public final class CampaignSaveFileService {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final String FILE_NAME = "campaign-save.json";
    public static final String MEDIA_TYPE = "application/json";

    /**
     * Applies only the immutable file boundary. The later archive service owns the complete JSON
     * schema and must supply an already validated, server-generated object document.
     */
    public ExportFile encode(String jsonDocument) {
        if (jsonDocument == null || jsonDocument.length() > MAX_BYTES
                || !hasObjectBoundary(jsonDocument)) {
            throw failure(jsonDocument != null && jsonDocument.length() > MAX_BYTES
                    ? ErrorCode.EXPORT_TOO_LARGE : ErrorCode.INVALID_DOCUMENT);
        }

        final ByteBuffer encoded;
        try {
            encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(jsonDocument));
        } catch (CharacterCodingException exception) {
            throw failure(ErrorCode.INVALID_UTF8);
        }
        if (encoded.remaining() > MAX_BYTES) {
            throw failure(ErrorCode.EXPORT_TOO_LARGE);
        }
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return new ExportFile(bytes);
    }

    private static boolean hasObjectBoundary(String value) {
        int first = 0;
        while (first < value.length() && isJsonWhitespace(value.charAt(first))) first++;
        int last = value.length() - 1;
        while (last >= first && isJsonWhitespace(value.charAt(last))) last--;
        // UTF-8 JSON emitted by the application never carries a BOM or a non-object root.
        return first <= last && value.charAt(first) == '{' && value.charAt(last) == '}';
    }

    private static boolean isJsonWhitespace(char value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static ExportException failure(ErrorCode code) {
        return new ExportException(code);
    }

    public enum ErrorCode {
        INVALID_DOCUMENT,
        INVALID_UTF8,
        EXPORT_TOO_LARGE
    }

    /** Stable failure category; document contents and encoding details are never exposed. */
    public static final class ExportException extends IllegalArgumentException {
        private final ErrorCode code;

        private ExportException(ErrorCode code) {
            super(Objects.requireNonNull(code, "error code").name());
            this.code = code;
        }

        public ErrorCode code() {
            return code;
        }
    }

    /** Immutable bytes ready for one attachment response; only the validated encoder creates it. */
    public static final class ExportFile {
        private final byte[] content;

        private ExportFile(byte[] content) {
            this.content = Objects.requireNonNull(content, "save content").clone();
        }

        public byte[] content() {
            return content.clone();
        }

        public int byteLength() {
            return content.length;
        }

        public String fileName() {
            return FILE_NAME;
        }

        public String mediaType() {
            return MEDIA_TYPE;
        }
    }
}
