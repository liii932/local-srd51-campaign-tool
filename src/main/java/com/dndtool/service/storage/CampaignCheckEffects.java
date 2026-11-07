package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.Event;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthInput;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.function.IntUnaryOperator;

import static com.dndtool.service.storage.CampaignDomainData.*;
import com.dndtool.service.storage.CampaignDomainData.Character;

/** One checked conditional command; both complete branches are admitted before any actual die. */
public final class CampaignCheckEffects {
    private static final long MAX_QUANTITY = 1_000_000;
    private static final String HEALTH_ADJUDICATION = "DM预先声明检定分支的直接环境生命效果。";
    private CampaignCheckEffects() { }

    /** Selecting a temporary-HP effect explicitly chooses REPLACE, before its dice are drawn. */
    public record Branch(UUID targetId, RuleIdentity healthEffect, UUID itemId, long itemDelta, String message) {
        public Branch {
            uuid(targetId); if (itemId != null) uuid(itemId);
            if (itemDelta < -MAX_QUANTITY || itemDelta > MAX_QUANTITY || (itemId == null) != (itemDelta == 0))
                throw CampaignJson.bad("A quantity delta needs one existing item and a nonzero bounded amount");
            if (message == null || !Normalizer.isNormalized(message, Normalizer.Form.NFC)
                    || message.codePointCount(0, message.length()) > 4096) throw CampaignJson.bad("Invalid conditional message");
            CampaignJson.validString(message);
        }
    }
    public record Input(UUID characterId, long characterVersion, Command command, Branch success, Branch failure) {
        public Input {
            uuid(characterId);
            if (characterVersion < 0 || command == null || !command.kind().equals("CHECK_RESOLVED")
                    || success == null || failure == null) throw CampaignJson.bad("Expected one typed check and two explicit branches");
        }
    }
    public record Audit(Input input, Map<UUID, Long> characterVersions, UUID checkOperationId,
                        UUID healthOperationId, boolean success, Item itemBefore, Item itemAfter, String message) {
        public Audit {
            if (input == null || characterVersions == null || characterVersions.isEmpty() || characterVersions.size() > 3)
                throw CampaignJson.bad("Incomplete conditional dependencies");
            Map<UUID, Long> versions = new TreeMap<>(Comparator.comparing(UUID::toString));
            characterVersions.forEach((id, version) -> {
                uuid(id); if (version == null || version < 0) throw CampaignJson.bad("Invalid conditional version"); versions.put(id, version);
            });
            characterVersions = Collections.unmodifiableMap(versions);
            uuid(checkOperationId); if (healthOperationId != null) uuid(healthOperationId);
            if (message == null || !Normalizer.isNormalized(message, Normalizer.Form.NFC)
                    || message.codePointCount(0, message.length()) > 4096) throw CampaignJson.bad("Invalid conditional message");
            CampaignJson.validString(message);
        }
    }
    public record Result(State state, UUID subjectId, Audit audit) {
        public Result { if (state == null || audit == null) throw CampaignJson.bad("Missing conditional result"); uuid(subjectId); }
    }
    private record Plan(Branch branch, Command healthCommand, UUID healthOperation,
                        Item itemBefore, Item itemAfter) { }
    private record Prepared(Input input, UUID checkOperation, Map<UUID, Long> versions, Plan success, Plan failure) { }

    /** Input is a plain fixed-field object; command and non-null healthEffect use the typed codec. */
    public static Result resolve(CampaignData.Snapshot snapshot, JsonObject input, UUID rootOperation, IntUnaryOperator dice) {
        if (snapshot.domains() == null) throw new StorageException("RULE_NOT_SELECTED", "Select the current campaign runtime module");
        return resolve(snapshot.domains(), read(input), rootOperation, dice);
    }

    static Result resolve(State before, Input input, UUID rootOperation, IntUnaryOperator dice) {
        return execute(before, prepare(before, input, rootOperation), dice);
    }

    private static Result execute(State before, Prepared prepared, IntUnaryOperator dice) {
        Objects.requireNonNull(dice); Input input = prepared.input();
        var material = before.material().restore().projection(); var engine = new CampaignDomainEngine();
        List<CampaignDomainEngine.CharacterRecord> changed = engine.executeSpaceCharacter(material,
                CampaignSpaceCommands.focusedRecords(before), input.characterId(), input.command(), prepared.checkOperation(), dice);
        Event check = changed.stream().filter(c -> c.initial().characterId().equals(input.characterId()))
                .findFirst().orElseThrow().history().events().getLast();
        boolean success = check.checkResult().success(); Plan chosen = success ? prepared.success() : prepared.failure();
        if (chosen.healthCommand() != null) changed = engine.executeSpaceCharacter(material, changed,
                chosen.branch().targetId(), chosen.healthCommand(), chosen.healthOperation(), dice);
        List<Character> characters = CampaignSpaceCommands.merge(before.characters(), changed);
        List<Item> items = new ArrayList<>(before.items());
        if (chosen.itemAfter() != null) { items.remove(chosen.itemBefore()); items.add(chosen.itemAfter()); }
        items.sort(Comparator.comparing(i -> i.id().toString()));
        State result = new State(before.material(), characters, before.combats(), before.nodes(), before.positions(),
                items, before.roots(), before.manual(), before.participation(), before.space());
        var audit = new Audit(input, prepared.versions(), prepared.checkOperation(), chosen.healthOperation(), success,
                chosen.itemBefore(), chosen.itemAfter(), chosen.branch().message());
        return new Result(result, input.characterId(), audit);
    }

    private static Prepared prepare(State before, Input input, UUID rootOperation) {
        uuid(rootOperation);
        if (before.roots().size() >= 100000) throw new StorageException("CAPACITY", "Complete root history is full");
        CampaignSpaceCommands.requireNoPending(before);
        var focus = before.space().focus().space();
        if (focus != null && focus.kind().equals("SCENE") && before.space().scene(focus.id()).resolution().equals("RULES"))
            throw new StorageException("RULE_SCENE_OWNER_REQUIRED", "The current scene owns checks and their consequences");
        var material = before.material().restore().projection(); var engine = new CampaignDomainEngine();
        Map<UUID, Long> versions = new LinkedHashMap<>();
        for (UUID id : List.of(input.characterId(), input.success().targetId(), input.failure().targetId())) {
            if (!versions.containsKey(id)) versions.put(id, character(before, id).history().current().version());
        }
        if (versions.get(input.characterId()) != input.characterVersion()) throw new StorageException("STALE_CHARACTER", "Refresh the check executor");
        UUID checkOperation = child(rootOperation, "check", input.characterId());
        UUID successHealth = input.success().healthEffect() == null ? null : child(rootOperation, "success-health", input.success().targetId());
        UUID failureHealth = input.failure().healthEffect() == null ? null : child(rootOperation, "failure-health", input.failure().targetId());
        Set<UUID> operations = new HashSet<>();
        before.roots().forEach(r -> operations.add(r.operationId()));
        before.characters().forEach(c -> c.history().events().forEach(e -> operations.add(e.operationId())));
        before.combats().forEach(c -> c.events().forEach(e -> operations.add(e.operationId())));
        for (UUID operation : Arrays.asList(rootOperation, checkOperation, successHealth, failureHealth))
            if (operation != null && !operations.add(operation)) throw new StorageException("IDEMPOTENCY_CONFLICT", "Conditional operation identity is already retained");
        // Check results never mutate HP, equipment or time. This minimum projection reserves its
        // event before proving the health event of either branch, including a same-character target.
        List<CampaignDomainEngine.CharacterRecord> candidate = engine.executeSpaceCharacter(material,
                CampaignSpaceCommands.focusedRecords(before), input.characterId(), input.command(), checkOperation, sides -> 1);
        Plan success = plan(before, material, input.success(), successHealth, candidate, engine);
        Plan failure = plan(before, material, input.failure(), failureHealth, candidate, engine);
        return new Prepared(input, checkOperation, versions, success, failure);
    }

    private static Plan plan(State before, ProductionSchema2Projection material, Branch branch, UUID healthOperation,
            List<CampaignDomainEngine.CharacterRecord> afterCheck, CampaignDomainEngine engine) {
        Item oldItem = null, newItem = null;
        if (branch.itemId() != null) {
            oldItem = before.items().stream().filter(item -> item.id().equals(branch.itemId())).findFirst()
                    .orElseThrow(() -> CampaignJson.bad("Conditional item does not exist"));
            if (!oldItem.characterId().equals(branch.targetId())) throw CampaignJson.bad("Conditional item has a different owner");
            long quantity = Math.addExact(oldItem.quantity(), branch.itemDelta());
            if (quantity < 0 || quantity > MAX_QUANTITY) throw CampaignJson.bad("Conditional item quantity is outside bounds");
            newItem = new Item(oldItem.id(), oldItem.characterId(), oldItem.name(), quantity,oldItem.sourceKey(),oldItem.description(),oldItem.active());
        }
        Command health = branch.healthEffect() == null ? null : healthCommand(material, branch.healthEffect());
        if (health != null) engine.executeSpaceCharacter(material, afterCheck, branch.targetId(), health, healthOperation, sides -> 1);
        return new Plan(branch, health, healthOperation, oldItem, newItem);
    }

    private static Command healthCommand(ProductionSchema2Projection material, RuleIdentity effect) {
        var health = material.projection().rules().stream().filter(FinalHealth.class::isInstance).map(FinalHealth.class::cast)
                .filter(FinalHealth::enabled).toList();
        var effects = material.projection().rules().stream().filter(FinalVitalEffect.class::isInstance).map(FinalVitalEffect.class::cast)
                .filter(row -> row.enabled() && row.identity().equals(effect)).toList();
        if (health.size() != 1 || effects.size() != 1) throw CampaignJson.bad("Unsupported conditional health rule");
        String temporary = effects.getFirst().definition().kind() == VitalEffectKind.TEMP_HP ? "REPLACE" : null;
        return new Command("HEALTH_EFFECT_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null,
                new HealthInput(health.getFirst().identity(), effect, temporary, HEALTH_ADJUDICATION));
    }

    static Input read(JsonObject input) {
        if (input == null) throw CampaignJson.bad("Missing conditional request");
        CampaignJson.fields(input, "characterId", "characterVersion", "command", "success", "failure");
        return new Input(UUID.fromString(CampaignJson.uuid(CampaignJson.string(input, "characterId"))), CampaignJson.integer(input, "characterVersion"),
                CampaignDomainCodec.fromJson(input.get("command"), Command.class), readBranch(CampaignJson.object(input, "success")), readBranch(CampaignJson.object(input, "failure")));
    }
    private static Branch readBranch(JsonObject branch) {
        CampaignJson.fields(branch, "targetId", "healthEffect", "itemId", "itemDelta", "message");
        String delta = CampaignJson.string(branch, "itemDelta");
        if (!delta.matches("0|-?[1-9][0-9]*")) throw CampaignJson.bad("Expected canonical signed quantity delta");
        final long quantity; try { quantity = Long.parseLong(delta); } catch (NumberFormatException invalid) { throw CampaignJson.bad("Quantity delta overflow"); }
        return new Branch(UUID.fromString(CampaignJson.uuid(CampaignJson.string(branch, "targetId"))),
                branch.get("healthEffect").isJsonNull() ? null : CampaignDomainCodec.fromJson(branch.get("healthEffect"), RuleIdentity.class),
                optionalUuid(branch, "itemId"), quantity, CampaignJson.string(branch, "message"));
    }
    private static UUID optionalUuid(JsonObject input, String key) { return input.get(key).isJsonNull() ? null : UUID.fromString(CampaignJson.uuid(CampaignJson.string(input, key))); }
    private static Character character(State state, UUID id) {
        Character result = state.characters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(() -> CampaignJson.bad("Conditional target does not exist"));
        if (!result.active()) throw new StorageException("CHARACTER_INACTIVE", "Restore every declared conditional target first");
        CampaignSpaceCommands.requirePlaced(state, id);
        return result;
    }
    private static UUID child(UUID root, String purpose, UUID target) {
        UUID derived = UUID.nameUUIDFromBytes(("campaign-check-effects/1/" + root + "/" + purpose + "/" + target).getBytes(StandardCharsets.US_ASCII));
        return new UUID((derived.getMostSignificantBits() & ~0xf000L) | 0x4000L,
                (derived.getLeastSignificantBits() & 0x3fffffffffffffffL) | 0x8000000000000000L);
    }
    private static void uuid(UUID value) { if (value == null) throw CampaignJson.bad("Missing UUID"); CampaignJson.uuid(value.toString()); }
}
