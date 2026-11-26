package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.storage.CampaignDomainData.Character;
import com.dndtool.service.storage.CampaignDomainData.State;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

import static com.dndtool.service.Schema2CharacterRuntime.ROLLED_CHECK;

/** Resolves a single shared task while retaining each participant's ordinary check. */
public final class CampaignGroupCheck {
    public static final String ACTION = "group-check";
    public static final int MAX_PARTICIPANTS = 16;
    private static final int MAX_MESSAGE_CODE_POINTS = 4096;

    private CampaignGroupCheck() { }

    public record ParticipantAudit(UUID characterId, UUID checkOperationId) { }

    public record Audit(List<ParticipantAudit> participants, int successNumerator, int successDenominator,
                        int requiredSuccessCount, int successCount, boolean success, String message) {
        public Audit { participants = List.copyOf(participants); }
    }

    record Result(UUID subjectId, List<Character> characters, Audit audit) { }

    private record Prepared(Character character, Command check, UUID operationId) { }

    static Result resolve(State before, CampaignRuleContext context, JsonObject input, UUID rootOperation,
                          Set<UUID> operations, IntUnaryOperator dice) {
        if (!CampaignSpaceCommands.isWorldFocus(before)) {
            throw new StorageException("GROUP_CHECK_WORLD_REQUIRED", "Select the world node where the group is attempting the task");
        }
        CampaignJson.fields(input, "rule", "kind", "abilityKey", "proficiency", "difficultyClass", "adjudication",
                "participants", "successMessage", "failureMessage");
        String successMessage = branchMessage(input, "successMessage");
        String failureMessage = branchMessage(input, "failureMessage");
        RuleIdentity rule = CampaignDomainCodec.fromJson(input.get("rule"), RuleIdentity.class);
        if (!context.checkRule().equals(rule)) {
            throw new StorageException("GROUP_CHECK_RULE_UNSUPPORTED", "Select the campaign's frozen check rule");
        }
        String kind = CampaignJson.string(input, "kind");
        if (!kind.equals("ABILITY") && !kind.equals("SKILL")) {
            throw CampaignJson.bad("A group check requires an active ability or skill check");
        }
        String ability = CampaignJson.string(input, "abilityKey");
        RuleIdentity proficiency = CampaignDomainCodec.fromJson(input.get("proficiency"), RuleIdentity.class);
        long dc = CampaignJson.integer(input, "difficultyClass");
        if (dc > 30) throw CampaignJson.bad("Invalid group check DC");
        String adjudication = CampaignJson.string(input, "adjudication");

        var participants = CampaignJson.array(input, "participants");
        if (participants.size() < 2 || participants.size() > MAX_PARTICIPANTS) {
            throw CampaignJson.bad("Select 2 to 16 distinct rule characters for the group check");
        }
        var prepared = new TreeMap<String, Prepared>();
        Set<UUID> newOperations = new HashSet<>();
        newOperations.add(rootOperation);
        for (var participant : participants) {
            if (!participant.isJsonObject()) throw CampaignJson.bad("Invalid group check participant");
            var row = participant.getAsJsonObject();
            CampaignJson.fields(row, "characterId", "characterVersion", "advantage", "disadvantage");
            String key = CampaignJson.uuid(CampaignJson.string(row, "characterId"));
            if (prepared.containsKey(key)) {
                throw new StorageException("GROUP_CHECK_DUPLICATE_PARTICIPANT", "Select each participant only once");
            }
            UUID id = UUID.fromString(key);
            Character character = before.characters().stream().filter(c -> c.id().equals(id)).findFirst()
                    .orElseThrow(() -> new StorageException("CHARACTER_NOT_FOUND", "Select a rule character in this campaign"));
            CampaignSpaceCommands.requirePlaced(before, id);
            if (character.history().current().version() != CampaignJson.integer(row, "characterVersion")) {
                throw new StorageException("STALE_CHARACTER", "Refresh every group participant's character card");
            }
            CheckInput check = new CheckInput(rule, kind, ability, proficiency, (int) dc,
                    flag(row, "advantage"), flag(row, "disadvantage"), adjudication);
            UUID child = childOperation(rootOperation, id);
            if (operations.contains(child) || !newOperations.add(child)) {
                throw new StorageException("IDEMPOTENCY_CONFLICT", "Group check operation identity is already retained");
            }
            prepared.put(key, new Prepared(character,
                    new Command(ROLLED_CHECK, null, 0, null, null, null, GrowthChoices.EMPTY, null, check), child));
        }

        var definition = context.checkDefinition();
        int numerator = definition.groupSuccessNumerator();
        int denominator = definition.groupSuccessDenominator();
        int required = (prepared.size() * numerator + denominator - 1) / denominator;
        var material = context.projection();
        var engine = new CampaignDomainEngine();
        // The minimum result checks every history, check rule, and event capacity without consuming randomness.
        for (Prepared check : prepared.values()) {
            var character = check.character();
            engine.executeCharacter(material, character.initial(), character.history(), check.check(), check.operationId(), sides -> 1);
        }

        int successes = 0;
        List<ParticipantAudit> results = new ArrayList<>();
        List<CampaignDomainEngine.CharacterRecord> changes = new ArrayList<>();
        for (Prepared check : prepared.values()) {
            var character = check.character();
            var history = engine.executeCharacter(material, character.initial(), character.history(), check.check(), check.operationId(), dice);
            if (history.events().getLast().checkResult().success()) successes++;
            results.add(new ParticipantAudit(character.id(), check.operationId()));
            changes.add(new CampaignDomainEngine.CharacterRecord(character.name(), character.kind(), character.initial(), history));
        }
        boolean success = successes >= required;
        Audit audit = new Audit(results, numerator, denominator, required, successes, success,
                success ? successMessage : failureMessage);
        return new Result(before.space().focus().space().id(), CampaignSpaceCommands.merge(before.characters(), changes), audit);
    }

    private static String branchMessage(JsonObject input, String field) {
        String message = CampaignJson.string(input, field);
        if (!Normalizer.isNormalized(message, Normalizer.Form.NFC)
                || message.codePointCount(0, message.length()) > MAX_MESSAGE_CODE_POINTS) {
            throw CampaignJson.bad("Invalid group check branch message");
        }
        CampaignJson.validString(message);
        return message;
    }

    private static boolean flag(JsonObject input, String field) {
        var value = input.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw CampaignJson.bad("Expected boolean: " + field);
        }
        return value.getAsBoolean();
    }

    private static UUID childOperation(UUID root, UUID character) {
        UUID uuid = UUID.nameUUIDFromBytes(("campaign-group-check/1/" + root + "/" + character).getBytes(StandardCharsets.US_ASCII));
        return new UUID((uuid.getMostSignificantBits() & ~0xf000L) | 0x4000L,
                (uuid.getLeastSignificantBits() & 0x3fffffffffffffffL) | 0x8000000000000000L);
    }
}
