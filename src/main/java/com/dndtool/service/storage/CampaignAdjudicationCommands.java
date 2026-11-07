package com.dndtool.service.storage;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.IntUnaryOperator;
import static com.dndtool.service.storage.CampaignAdjudicationData.*;
import static com.dndtool.service.storage.CampaignDomainData.*;

/** An explicit DM click supplies this new command; proposals never execute themselves. */
public final class CampaignAdjudicationCommands {
    private CampaignAdjudicationCommands() { }
    public record Result(State state, UUID subjectId, Audit audit) { }
    public static Result resolve(CampaignData.Snapshot snapshot, JsonObject input, UUID operation, IntUnaryOperator dice) {
        CampaignJson.fields(input, "proposalId", "memberId", "proposalText", "reason", "type", "check");
        Decision decision = new Decision(UUID.fromString(CampaignJson.uuid(CampaignJson.string(input,"proposalId"))), UUID.fromString(CampaignJson.uuid(CampaignJson.string(input,"memberId"))),
                CampaignJson.string(input,"proposalText"), CampaignJson.string(input,"reason"), Type.valueOf(CampaignJson.string(input,"type")), CampaignCheckEffects.read(CampaignJson.object(input,"check")));
        requireUndecided(snapshot.domains().roots(), decision.proposalId());
        validateProvenance(snapshot.domains().participation(), decision);
        var result = CampaignCheckEffects.resolve(snapshot.domains(), decision.check(), operation, dice);
        return new Result(result.state(), result.subjectId(), new Audit(decision, result.audit()));
    }
    private static void requireUndecided(List<Root> roots, UUID proposalId) {
        for (Root earlier : roots) if (earlier.kind().equals("adjudicated-check")) {
            Audit accepted = CampaignDomainCodec.decode(earlier.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), Audit.class);
            if (accepted.decision().proposalId().equals(proposalId)) throw new StorageException("PROPOSAL_ALREADY_DECIDED", "This proposal already has a durable DM decision");
        }
    }
    private static void validateProvenance(CampaignParticipationData.State participation, Decision decision) {
        if (!participation.member(decision.memberId()).active() || !decision.memberId().equals(participation.controller(decision.check().characterId()))) throw new StorageException("ADJUDICATION_CONTROL", "The proposal member must currently control the acting PC");
    }
}
