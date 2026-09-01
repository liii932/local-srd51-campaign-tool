package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.persistence.CampaignArchivePreviewRepository;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class CampaignArchivePreviewServiceTest {
    private static final byte[] CONTENT = "raw archive bytes".getBytes(StandardCharsets.UTF_8);
    private static final String OTHER_KEY = "ffffffff-eeee-4ddd-8ccc-bbbbbbbbbbbb";

    @Test
    void createsBoundReadOnlyPreviewWithEveryObjectCount() throws Exception {
        CampaignArchiveDocument document = validDocument();
        AtomicInteger reads = new AtomicInteger();
        CampaignArchivePreviewRepository repository = key -> {
            reads.incrementAndGet();
            assertEquals(document.campaign().campaignKey(), key);
            return new CampaignArchivePreviewRepository.Snapshot(null, null);
        };

        CampaignArchivePreviewService.Result result = service(document, repository)
                .preview(CONTENT);

        assertEquals(CampaignArchiveUploadValidationService.Status.READY, result.status());
        CampaignArchivePreviewService.Preview preview = result.preview();
        assertEquals(CampaignArchivePreviewService.PreviewMode.CREATE, preview.mode());
        assertEquals(document.campaign().campaignKey(), preview.campaign().campaignKey());
        assertEquals(document.campaign().campaignName(), preview.campaign().campaignName());
        assertEquals(document.campaign().campaignStatus(), preview.campaign().campaignStatus());
        assertEquals(new CampaignArchivePreviewService.ObjectCounts(
                1, 4, 1, 1, 1, 2, 1, 1, 1, 2, 1), preview.counts());
        assertEquals(CampaignArchivePreviewService.ActiveCampaignImpact.NONE,
                preview.activeCampaignImpact());
        assertNull(preview.activeCampaign());
        assertEquals(CampaignArchiveDigest.sha256(CONTENT), preview.rawFileSha256());
        assertTrue(preview.irreversibleWarning());
        assertEquals(1, reads.get());
    }

    @Test
    void replacementAndAllActiveCampaignImpactsAreExplicit() throws Exception {
        CampaignArchiveDocument active = validDocument();
        String targetKey = active.campaign().campaignKey();
        CampaignArchivePreviewRepository.CampaignState targetActive = state(
                targetKey, "现有目标", "ACTIVE");
        CampaignArchivePreviewRepository.CampaignState targetArchived = state(
                targetKey, "现有目标", "ARCHIVED");
        CampaignArchivePreviewRepository.CampaignState otherActive = state(
                OTHER_KEY, "另一活动战役", "ACTIVE");

        assertPreview(active, new CampaignArchivePreviewRepository.Snapshot(
                        targetArchived, otherActive),
                CampaignArchivePreviewService.PreviewMode.REPLACE,
                CampaignArchivePreviewService.ActiveCampaignImpact.OTHER_WILL_BE_ARCHIVED);
        assertPreview(active, new CampaignArchivePreviewRepository.Snapshot(
                        targetActive, targetActive),
                CampaignArchivePreviewService.PreviewMode.REPLACE,
                CampaignArchivePreviewService.ActiveCampaignImpact.TARGET_REMAINS_ACTIVE);

        CampaignArchiveDocument archived = withStatus(active, "ARCHIVED");
        assertPreview(archived, new CampaignArchivePreviewRepository.Snapshot(
                        targetActive, targetActive),
                CampaignArchivePreviewService.PreviewMode.REPLACE,
                CampaignArchivePreviewService.ActiveCampaignImpact.TARGET_WILL_BE_ARCHIVED);
        assertPreview(archived, new CampaignArchivePreviewRepository.Snapshot(
                        targetArchived, otherActive),
                CampaignArchivePreviewService.PreviewMode.REPLACE,
                CampaignArchivePreviewService.ActiveCampaignImpact.OTHER_REMAINS_ACTIVE);
    }

    @Test
    void invalidArchiveStopsBeforeCampaignStateRead() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        CampaignArchivePreviewService service = new CampaignArchivePreviewService(
                ignored -> new CampaignArchiveUploadValidationService.ValidatedArchive(
                        CampaignArchiveUploadValidationService.Status.INVALID_ARCHIVE, null),
                key -> {
                    reads.incrementAndGet();
                    return new CampaignArchivePreviewRepository.Snapshot(null, null);
                });

        CampaignArchivePreviewService.Result result = service.preview(CONTENT);

        assertEquals(CampaignArchiveUploadValidationService.Status.INVALID_ARCHIVE,
                result.status());
        assertNull(result.preview());
        assertEquals(0, reads.get());
    }

    @Test
    void validationAndReadFailuresPropagateWithoutAFalsePreview() {
        CampaignArchivePreviewService validationFailure = new CampaignArchivePreviewService(
                ignored -> { throw new SQLException("synthetic validation read failure"); },
                key -> { throw new AssertionError("campaign state must not be read"); });
        assertThrows(SQLException.class, () -> validationFailure.preview(CONTENT));

        CampaignArchivePreviewService stateFailure = service(validDocument(),
                key -> { throw new SQLException("synthetic preview read failure"); });
        assertThrows(SQLException.class, () -> stateFailure.preview(CONTENT));
    }

    private static void assertPreview(
            CampaignArchiveDocument document,
            CampaignArchivePreviewRepository.Snapshot state,
            CampaignArchivePreviewService.PreviewMode mode,
            CampaignArchivePreviewService.ActiveCampaignImpact impact) throws Exception {
        CampaignArchivePreviewService.Preview preview = service(document, key -> state)
                .preview(CONTENT).preview();
        assertEquals(mode, preview.mode());
        assertEquals(impact, preview.activeCampaignImpact());
        assertEquals(state.active(), preview.activeCampaign());
    }

    private static CampaignArchivePreviewService service(
            CampaignArchiveDocument document, CampaignArchivePreviewRepository repository) {
        return new CampaignArchivePreviewService(
                ignored -> new CampaignArchiveUploadValidationService.ValidatedArchive(
                        CampaignArchiveUploadValidationService.Status.READY, document),
                repository);
    }

    private static CampaignArchivePreviewRepository.CampaignState state(
            String key, String name, String status) {
        return new CampaignArchivePreviewRepository.CampaignState(key, name, status);
    }

    private static CampaignArchiveDocument validDocument() {
        String json = CampaignArchiveJsonWriter.write(CampaignArchiveExportServiceTest.snapshot());
        CampaignArchiveReader.Result read = new CampaignArchiveReader().read(
                json.getBytes(StandardCharsets.UTF_8));
        assertEquals(CampaignArchiveReader.Status.READY, read.status());
        return read.document();
    }

    private static CampaignArchiveDocument withStatus(
            CampaignArchiveDocument source, String status) {
        return new CampaignArchiveDocument(
                source.formatVersion(),
                new CampaignArchiveDocument.Campaign(
                        source.campaign().campaignKey(), source.campaign().campaignName(), status),
                source.module(), source.characters(), source.fields(), source.classLevels(),
                source.skillProficiencies(), source.saveProficiencies(), source.items(),
                source.maps(), source.recentEvents());
    }
}
