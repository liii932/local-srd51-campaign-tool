package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.service.CampaignSaveFileService.ErrorCode;
import com.dndtool.service.CampaignSaveFileService.ExportException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class CampaignSaveFileServiceTest {
    private final CampaignSaveFileService service = new CampaignSaveFileService();

    @Test
    void emitsExactUtf8WithoutBomOrArchiveSignature() {
        String json = "{\"name\":\"本机战役\"}";
        CampaignSaveFileService.ExportFile file = service.encode(json);

        assertEquals("campaign-save.json", file.fileName());
        assertEquals("application/json", file.mediaType());
        assertArrayEquals(json.getBytes(StandardCharsets.UTF_8), file.content());
        assertFalse(file.content()[0] == (byte) 0xef
                && file.content()[1] == (byte) 0xbb
                && file.content()[2] == (byte) 0xbf);
        assertFalse(file.content()[0] == 'P' && file.content()[1] == 'K');
    }

    @Test
    void acceptsAnObjectExactlyAtSixteenMebibytes() {
        String prefix = "{\"data\":\"";
        String suffix = "\"}";
        int payloadLength = CampaignSaveFileService.MAX_BYTES
                - prefix.getBytes(StandardCharsets.UTF_8).length
                - suffix.getBytes(StandardCharsets.UTF_8).length;

        CampaignSaveFileService.ExportFile file =
                service.encode(prefix + "a".repeat(payloadLength) + suffix);

        assertEquals(CampaignSaveFileService.MAX_BYTES, file.byteLength());
    }

    @Test
    void rejectsAsciiDocumentOneByteOverLimitBeforeEncoding() {
        String prefix = "{\"data\":\"";
        String suffix = "\"}";
        int payloadLength = CampaignSaveFileService.MAX_BYTES
                - prefix.length() - suffix.length() + 1;

        assertFailure(
                ErrorCode.EXPORT_TOO_LARGE,
                () -> service.encode(prefix + "a".repeat(payloadLength) + suffix));
    }

    @Test
    void enforcesTheLimitOnEncodedBytesForMultibyteText() {
        String json = "{\"data\":\"" + "界".repeat(CampaignSaveFileService.MAX_BYTES / 3) + "\"}";

        assertFailure(ErrorCode.EXPORT_TOO_LARGE, () -> service.encode(json));
    }

    @Test
    void rejectsMalformedUnicodeBomAndNonObjectRoots() {
        assertFailure(ErrorCode.INVALID_UTF8, () -> service.encode("{\"x\":\"\ud800\"}"));
        assertFailure(ErrorCode.INVALID_DOCUMENT, () -> service.encode("\ufeff{}"));
        assertFailure(ErrorCode.INVALID_DOCUMENT, () -> service.encode("[]"));
        assertFailure(ErrorCode.INVALID_DOCUMENT, () -> service.encode("   "));
    }

    @Test
    void returnedBytesAreDefensiveCopies() {
        CampaignSaveFileService.ExportFile file = service.encode("{}");
        byte[] first = file.content();
        first[0] = 'x';

        assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), file.content());
    }

    private static void assertFailure(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        ExportException exception = assertThrows(ExportException.class, action);
        assertEquals(code, exception.code());
    }
}
