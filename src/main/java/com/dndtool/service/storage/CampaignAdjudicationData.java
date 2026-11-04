package com.dndtool.service.storage;

import java.text.Normalizer;
import java.util.UUID;

/** Durable DM decisions, distinct from unaccepted player communication proposals. */
public final class CampaignAdjudicationData {
    private CampaignAdjudicationData() { }
    public enum Type { ABILITY_CHECK }
    public record Decision(UUID proposalId, UUID memberId, String proposalText, String reason, Type adjudicationType, CampaignCheckEffects.Input check) {
        public Decision {
            if (proposalId == null || memberId == null || adjudicationType == null || check == null) throw CampaignJson.bad("Incomplete adjudication decision");
            CampaignJson.uuid(proposalId.toString()); CampaignJson.uuid(memberId.toString()); text(proposalText, 2000); text(reason, 500);
            var input = check.command().checkInput();
            if (adjudicationType != Type.ABILITY_CHECK || input == null || !"ABILITY".equals(input.kind()) || !reason.equals(input.adjudication())) throw CampaignJson.bad("Unsupported adjudication type or reason binding");
        }
    }
    public record Audit(Decision decision, CampaignCheckEffects.Audit result) {
        public Audit { if (decision == null || result == null || !decision.check().equals(result.input())) throw CampaignJson.bad("Adjudication result differs from explicit decision"); }
    }
    private static void text(String text, int maximum) {
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > maximum || !Normalizer.isNormalized(text, Normalizer.Form.NFC)) throw CampaignJson.bad("Invalid adjudication text");
        CampaignJson.validString(text);
    }
}
