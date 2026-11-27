package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterCheckRules;
import com.dndtool.service.Schema2CharacterCheckRules.ContestOutcome;
import com.dndtool.service.Schema2CharacterCheckRules.PreparedRoll;
import com.dndtool.service.Schema2CharacterCheckRules.RollInput;
import com.dndtool.service.storage.CampaignDomainData.State;
import com.google.gson.JsonObject;
import java.text.Normalizer;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

import static com.dndtool.module.composition.Schema2CompositionKernel.MAX_CONTEST_WINNING_MARGIN;
import static com.dndtool.module.composition.Schema2CompositionKernel.MIN_CONTEST_WINNING_MARGIN;

/** One world-node adjudication, with both sides' rolls owned by the same campaign root. */
public final class CampaignContestCheck {
    public static final String ACTION = "contest-check";
    private static final int MAX_ADJUDICATION_CODE_POINTS = 500;

    private CampaignContestCheck() { }

    public record SideResult(UUID characterId, int firstD20, Integer secondD20, int selectedD20,
                             int abilityModifier, int proficiencyBonus, int total,
                             boolean effectiveAdvantage, boolean effectiveDisadvantage) { }

    public record Audit(SideResult initiator, SideResult responder, int minimumWinningMargin, ContestOutcome outcome) { }

    private record SideInput(UUID characterId, long version, RollInput roll) { }

    static Audit resolve(State before, CampaignRuleContext context, JsonObject input, IntUnaryOperator dice) {
        if (!CampaignSpaceCommands.isWorldFocus(before)) {
            throw new StorageException("CONTEST_CHECK_WORLD_REQUIRED", "Select the world node where the contest takes place");
        }
        CampaignJson.fields(input, "rule", "adjudication", "initiator", "responder");
        String adjudication = CampaignJson.string(input, "adjudication");
        if (adjudication.isBlank() || !Normalizer.isNormalized(adjudication, Normalizer.Form.NFC)
                || adjudication.codePointCount(0, adjudication.length()) > MAX_ADJUDICATION_CODE_POINTS) {
            throw CampaignJson.bad("Invalid contest adjudication");
        }
        CampaignJson.validString(adjudication);
        RuleIdentity rule = CampaignDomainCodec.fromJson(input.get("rule"), RuleIdentity.class);
        if (!context.checkRule().equals(rule)) {
            throw new StorageException("CONTEST_CHECK_RULE_UNSUPPORTED", "Select the campaign's frozen check rule");
        }
        int margin = context.checkDefinition().contestMinimumWinningMargin();
        if (margin < MIN_CONTEST_WINNING_MARGIN || margin > MAX_CONTEST_WINNING_MARGIN) {
            throw new StorageException("CONTEST_CHECK_RULE_UNSUPPORTED", "Invalid frozen contest winning margin");
        }
        SideInput initiator = readSide(CampaignJson.object(input, "initiator"), rule);
        SideInput responder = readSide(CampaignJson.object(input, "responder"), rule);
        if (initiator.characterId().equals(responder.characterId())) {
            throw new StorageException("CONTEST_CHECK_DUPLICATE_PARTICIPANT", "Select two distinct contest participants");
        }

        var rules = new Schema2CharacterCheckRules();
        var material = context.projection();
        PreparedRoll preparedInitiator = prepare(before, material, rules, initiator);
        PreparedRoll preparedResponder = prepare(before, material, rules, responder);
        SideResult first = roll(rules, initiator.characterId(), preparedInitiator, dice);
        SideResult second = roll(rules, responder.characterId(), preparedResponder, dice);
        return new Audit(first, second, margin, Schema2CharacterCheckRules.compareContest(first.total(), second.total(), margin));
    }

    private static SideInput readSide(JsonObject row, RuleIdentity rule) {
        CampaignJson.fields(row, "characterId", "characterVersion", "kind", "abilityKey", "proficiency", "advantage", "disadvantage");
        UUID id = UUID.fromString(CampaignJson.uuid(CampaignJson.string(row, "characterId")));
        RuleIdentity proficiency = CampaignDomainCodec.fromJson(row.get("proficiency"), RuleIdentity.class);
        RollInput roll = new RollInput(rule, CampaignJson.string(row, "kind"), CampaignJson.string(row, "abilityKey"),
                proficiency, flag(row, "advantage"), flag(row, "disadvantage"));
        return new SideInput(id, CampaignJson.integer(row, "characterVersion"), roll);
    }

    private static PreparedRoll prepare(State before, ProductionSchema2Projection material, Schema2CharacterCheckRules rules,
                                        SideInput input) {
        var character = before.characters().stream().filter(entry -> entry.id().equals(input.characterId())).findFirst()
                .orElseThrow(() -> new StorageException("CHARACTER_NOT_FOUND", "Select a rule character in this campaign"));
        CampaignSpaceCommands.requirePlaced(before, character.id());
        var current = character.history().current();
        if (current.version() != input.version()) {
            throw new StorageException("STALE_CHARACTER", "Refresh both contest participants' character cards");
        }
        return rules.prepareContestRoll(material, current, input.roll());
    }

    private static SideResult roll(Schema2CharacterCheckRules rules, UUID id, PreparedRoll prepared, IntUnaryOperator dice) {
        int first = dice.applyAsInt(20);
        Integer second = prepared.secondDieRequired() ? dice.applyAsInt(20) : null;
        var score = rules.resolveRoll(prepared, first, second);
        return new SideResult(id, score.firstD20(), score.secondD20(), score.selectedD20(), score.abilityModifier(),
                score.proficiencyBonus(), score.total(), score.effectiveAdvantage(), score.effectiveDisadvantage());
    }

    private static boolean flag(JsonObject row, String field) {
        var value = row.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw CampaignJson.bad("Expected boolean: " + field);
        }
        return value.getAsBoolean();
    }
}
