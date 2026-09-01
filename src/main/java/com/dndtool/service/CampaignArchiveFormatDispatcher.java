package com.dndtool.service;

import com.dndtool.persistence.CampaignArchiveRepository.Snapshot;

/** Exact archive-format dispatcher; unsupported versions never fall back to format 1. */
final class CampaignArchiveFormatDispatcher {
    boolean supports(int formatVersion) {
        return formatVersion == CampaignArchiveExportService.FORMAT_VERSION;
    }

    String write(Snapshot snapshot, int formatVersion) {
        if (!supports(formatVersion)) {
            throw new IllegalArgumentException("Unsupported campaign archive format");
        }
        return CampaignArchiveJsonWriter.write(snapshot, formatVersion);
    }
}
