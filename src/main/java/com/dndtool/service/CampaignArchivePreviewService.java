package com.dndtool.service;

import com.dndtool.persistence.CampaignArchivePreviewRepository;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.sql.SQLException;
import java.util.Objects;

/** Builds a database-read-only import preview after all file and module checks succeed. */
public final class CampaignArchivePreviewService {
    private final ArchiveValidator validator;
    private final CampaignArchivePreviewRepository repository;

    public CampaignArchivePreviewService(
            ModuleCatalogRepository moduleRepository,
            CampaignArchivePreviewRepository previewRepository) {
        CampaignArchiveUploadValidationService validation =
                new CampaignArchiveUploadValidationService(moduleRepository);
        this.validator = validation::validateForPreview;
        this.repository = Objects.requireNonNull(previewRepository, "preview repository");
    }

    CampaignArchivePreviewService(
            ArchiveValidator validator, CampaignArchivePreviewRepository repository) {
        this.validator = Objects.requireNonNull(validator, "archive validator");
        this.repository = Objects.requireNonNull(repository, "preview repository");
    }

    /** File validation always completes before the first campaign-state database read. */
    public Result preview(byte[] content) throws SQLException {
        CampaignArchiveUploadValidationService.ValidatedArchive validated =
                validator.validate(content);
        if (validated.status() != CampaignArchiveUploadValidationService.Status.READY) {
            return new Result(validated.status(), null);
        }

        CampaignArchiveDocument document = validated.document();
        CampaignArchivePreviewRepository.Snapshot state =
                repository.inspect(document.campaign().campaignKey());
        PreviewMode mode = state.target() == null ? PreviewMode.CREATE : PreviewMode.REPLACE;
        ActiveCampaignImpact impact = impact(document.campaign(), state.active());
        Preview preview = new Preview(
                mode,
                new CampaignSummary(
                        document.campaign().campaignKey(),
                        document.campaign().campaignName(),
                        document.campaign().campaignStatus()),
                counts(document),
                impact,
                state.active(),
                CampaignArchiveDigest.sha256(content),
                true);
        return new Result(CampaignArchiveUploadValidationService.Status.READY, preview);
    }

    private static ActiveCampaignImpact impact(
            CampaignArchiveDocument.Campaign imported,
            CampaignArchivePreviewRepository.CampaignState active) {
        if (active == null) return ActiveCampaignImpact.NONE;
        boolean targetIsActive = imported.campaignKey().equals(active.campaignKey());
        boolean importIsActive = "ACTIVE".equals(imported.campaignStatus());
        if (targetIsActive) {
            return importIsActive
                    ? ActiveCampaignImpact.TARGET_REMAINS_ACTIVE
                    : ActiveCampaignImpact.TARGET_WILL_BE_ARCHIVED;
        }
        return importIsActive
                ? ActiveCampaignImpact.OTHER_WILL_BE_ARCHIVED
                : ActiveCampaignImpact.OTHER_REMAINS_ACTIVE;
    }

    private static ObjectCounts counts(CampaignArchiveDocument document) {
        int encounters = 0;
        int participants = 0;
        for (CampaignArchiveDocument.MapState map : document.maps()) {
            if (map.encounter() != null) {
                encounters++;
                participants += map.encounter().participants().size();
            }
        }
        int checks = 0;
        for (CampaignArchiveDocument.EventSnapshot event : document.recentEvents()) {
            if (event.check() != null) checks++;
        }
        return new ObjectCounts(
                document.characters().size(),
                document.fields().size(),
                document.classLevels().size(),
                document.skillProficiencies().size(),
                document.saveProficiencies().size(),
                document.items().size(),
                document.maps().size(),
                encounters,
                participants,
                document.recentEvents().size(),
                checks);
    }

    @FunctionalInterface
    interface ArchiveValidator {
        CampaignArchiveUploadValidationService.ValidatedArchive validate(byte[] content)
                throws SQLException;
    }

    public enum PreviewMode {
        CREATE,
        REPLACE
    }

    public enum ActiveCampaignImpact {
        NONE,
        TARGET_REMAINS_ACTIVE,
        TARGET_WILL_BE_ARCHIVED,
        OTHER_REMAINS_ACTIVE,
        OTHER_WILL_BE_ARCHIVED
    }

    public record CampaignSummary(
            String campaignKey, String campaignName, String campaignStatus) {
        public CampaignSummary {
            Objects.requireNonNull(campaignKey, "campaign key");
            Objects.requireNonNull(campaignName, "campaign name");
            if (!java.util.Set.of("ACTIVE", "ARCHIVED").contains(campaignStatus)) {
                throw new IllegalArgumentException("Invalid campaign status");
            }
        }
    }

    public record ObjectCounts(
            int characters,
            int fields,
            int classLevels,
            int skillProficiencies,
            int saveProficiencies,
            int items,
            int maps,
            int encounters,
            int participants,
            int recentEvents,
            int checks) {
        public ObjectCounts {
            if (characters < 0 || fields < 0 || classLevels < 0
                    || skillProficiencies < 0 || saveProficiencies < 0 || items < 0
                    || maps < 0 || encounters < 0 || participants < 0
                    || recentEvents < 0 || checks < 0) {
                throw new IllegalArgumentException("Negative preview count");
            }
        }
    }

    public record Preview(
            PreviewMode mode,
            CampaignSummary campaign,
            ObjectCounts counts,
            ActiveCampaignImpact activeCampaignImpact,
            CampaignArchivePreviewRepository.CampaignState activeCampaign,
            String rawFileSha256,
            boolean irreversibleWarning) {
        public Preview {
            Objects.requireNonNull(mode, "preview mode");
            Objects.requireNonNull(campaign, "preview campaign");
            Objects.requireNonNull(counts, "preview counts");
            Objects.requireNonNull(activeCampaignImpact, "active campaign impact");
            Objects.requireNonNull(rawFileSha256, "raw file digest");
            boolean targetImpact = activeCampaignImpact == ActiveCampaignImpact.TARGET_REMAINS_ACTIVE
                    || activeCampaignImpact == ActiveCampaignImpact.TARGET_WILL_BE_ARCHIVED;
            boolean otherImpact = activeCampaignImpact == ActiveCampaignImpact.OTHER_REMAINS_ACTIVE
                    || activeCampaignImpact == ActiveCampaignImpact.OTHER_WILL_BE_ARCHIVED;
            if (!rawFileSha256.matches("[0-9a-f]{64}") || !irreversibleWarning) {
                throw new IllegalArgumentException("Invalid preview binding");
            }
            if ((activeCampaignImpact == ActiveCampaignImpact.NONE) != (activeCampaign == null)
                    || targetImpact && !campaign.campaignKey().equals(
                            activeCampaign.campaignKey())
                    || otherImpact && campaign.campaignKey().equals(
                            activeCampaign.campaignKey())) {
                throw new IllegalArgumentException("Invalid active campaign impact");
            }
        }
    }

    public record Result(
            CampaignArchiveUploadValidationService.Status status, Preview preview) {
        public Result {
            Objects.requireNonNull(status, "preview status");
            if ((status == CampaignArchiveUploadValidationService.Status.READY)
                    != (preview != null)) {
                throw new IllegalArgumentException("Preview does not match status");
            }
        }
    }
}
