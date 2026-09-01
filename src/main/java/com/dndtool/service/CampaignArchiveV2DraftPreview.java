package com.dndtool.service;

import java.util.Objects;
import java.util.Set;

/** Side-effect-free preview for the unactivated format-2 character projection. */
final class CampaignArchiveV2DraftPreview {
    Result preview(byte[] content, Set<String> characterKeys) {
        CampaignArchiveV2CharacterStateCodec.Result read =
                CampaignArchiveV2CharacterStateCodec.read(content, characterKeys);
        if (read.status() != CampaignArchiveV2CharacterStateCodec.Status.READY) {
            return new Result(Status.INVALID_ARCHIVE, null);
        }
        CampaignArchiveV2CharacterState state = read.state();
        Counts counts = new Counts(
                state.stateEvents().size(),
                state.creationSnapshots().size(),
                state.creationSelections().size(),
                state.resources().size(),
                state.classLevels().size(),
                state.subclasses().size(),
                state.features().size(),
                state.featureChoices().size(),
                state.feats().size(),
                state.multiclassProficiencies().size());
        return new Result(Status.READY,
                new Preview(state.eventTail(), counts, CampaignArchiveDigest.sha256(content)));
    }

    enum Status {
        READY,
        INVALID_ARCHIVE
    }

    record Counts(
            int stateEvents,
            int creationSnapshots,
            int creationSelections,
            int resources,
            int classLevels,
            int subclasses,
            int features,
            int featureChoices,
            int feats,
            int multiclassProficiencies) {
    }

    record Preview(long eventTail, Counts counts, String rawPayloadSha256) {
        Preview {
            Objects.requireNonNull(counts, "preview counts");
            if (eventTail < 0 || rawPayloadSha256 == null
                    || !rawPayloadSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid format-2 DRAFT preview");
            }
        }
    }

    record Result(Status status, Preview preview) {
        Result {
            Objects.requireNonNull(status, "preview status");
            if ((status == Status.READY) != (preview != null)) {
                throw new IllegalArgumentException("Preview does not match status");
            }
        }
    }
}
