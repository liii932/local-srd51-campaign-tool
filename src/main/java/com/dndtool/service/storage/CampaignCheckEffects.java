package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.Command;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.Event;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.GrowthChoices;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthInput;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.History;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository;
import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2CombatState;
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
    public record Branch(UUID targetId, RuleIdentity healthEffect, UUID itemId, long itemDelta,
                         UUID nodeId, String message) {
        public Branch {
            uuid(targetId); if (itemId != null) uuid(itemId); if (nodeId != null) uuid(nodeId);
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
                        UUID healthOperationId, boolean success, Item itemBefore, Item itemAfter,
                        Position positionBefore, Position positionAfter, String message) {
        public Audit {
            if (input == null || characterVersions == null || characterVersions.isEmpty() || characterVersions.size() > 16)
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
                        Item itemBefore, Item itemAfter, Position positionBefore, Position positionAfter) { }
    private record Prepared(Input input, UUID checkOperation, Map<UUID, Long> versions, Plan success, Plan failure) { }
    private record Die(int sides, int value) { }

    /** Input is a plain fixed-field object; command and non-null healthEffect use the typed codec. */
    public static Result resolve(CampaignData.Snapshot snapshot, JsonObject input, UUID rootOperation, IntUnaryOperator dice) {
        CampaignData.validate(snapshot);
        if (snapshot.domains() == null) throw new StorageException("RULE_NOT_SELECTED", "Select the current campaign runtime module");
        return resolve(snapshot.domains(), read(input), rootOperation, dice);
    }

    private static Result resolve(State before, Input input, UUID rootOperation, IntUnaryOperator dice) {
        return execute(before, prepare(before, input, rootOperation), dice);
    }

    private static Result execute(State before, Prepared prepared, IntUnaryOperator dice) {
        Objects.requireNonNull(dice); Input input = prepared.input();
        var material = before.material().restore(); var engine = new CampaignDomainEngine();
        List<CampaignDomainEngine.CharacterRecord> changed = engine.executeCampaignCharacter(material,
                CampaignDomainValidation.records(before), before.combats(), input.characterId(), input.command(), prepared.checkOperation(), dice);
        Event check = changed.stream().filter(c -> c.initial().characterId().equals(input.characterId()))
                .findFirst().orElseThrow().history().events().getLast();
        boolean success = check.checkResult().success(); Plan chosen = success ? prepared.success() : prepared.failure();
        if (chosen.healthCommand() != null) changed = engine.executeCampaignCharacter(material, changed, before.combats(),
                chosen.branch().targetId(), chosen.healthCommand(), chosen.healthOperation(), dice);
        List<Character> characters = merge(before.characters(), changed);
        List<Item> items = new ArrayList<>(before.items());
        if (chosen.itemAfter() != null) { items.remove(chosen.itemBefore()); items.add(chosen.itemAfter()); }
        List<Position> positions = new ArrayList<>(before.positions());
        if (chosen.positionAfter() != null) { if (chosen.positionBefore() != null) positions.remove(chosen.positionBefore()); positions.add(chosen.positionAfter()); }
        items.sort(Comparator.comparing(i -> i.id().toString())); positions.sort(Comparator.comparing(p -> p.characterId().toString()));
        State result = new State(before.material(), characters, before.combats(), before.nodes(), positions, items, before.roots(), before.manual());
        var audit = new Audit(input, prepared.versions(), prepared.checkOperation(), chosen.healthOperation(), success,
                chosen.itemBefore(), chosen.itemAfter(), chosen.positionBefore(), chosen.positionAfter(), chosen.branch().message());
        return new Result(result, input.characterId(), audit);
    }

    private static Prepared prepare(State before, Input input, UUID rootOperation) {
        uuid(rootOperation);
        if (before.roots().size() >= 100000) throw new StorageException("CAPACITY", "Complete root history is full");
        if (before.combats().stream().anyMatch(c -> c.current().status() != Schema2CombatState.Status.ENDED))
            throw new StorageException("SCENE_ACTIVE", "The current scene owns checks and their consequences");
        if(CampaignManualData.active(before.manual()))throw new StorageException("SCENE_ACTIVE","The simple encounter owns this campaign");
        var material = before.material().restore(); var engine = new CampaignDomainEngine();
        engine.validateCampaign(material, CampaignDomainValidation.records(before), before.combats());
        Character executor = character(before, input.characterId());
        if (executor.history().current().version() != input.characterVersion()) throw new StorageException("STALE_CHARACTER", "Refresh the check executor");
        Map<UUID, Long> versions = new LinkedHashMap<>(); before.characters().forEach(c -> versions.put(c.id(), c.history().current().version()));
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
        List<CampaignDomainEngine.CharacterRecord> candidate = engine.executeCampaignCharacter(material,
                CampaignDomainValidation.records(before), before.combats(), input.characterId(), input.command(), checkOperation, sides -> 1);
        Plan success = plan(before, material, input.success(), successHealth, candidate, engine);
        Plan failure = plan(before, material, input.failure(), failureHealth, candidate, engine);
        return new Prepared(input, checkOperation, versions, success, failure);
    }

    private static Plan plan(State before, ProductionSchema2Projection material, Branch branch, UUID healthOperation,
            List<CampaignDomainEngine.CharacterRecord> afterCheck, CampaignDomainEngine engine) {
        character(before, branch.targetId());
        Item oldItem = null, newItem = null;
        if (branch.itemId() != null) {
            oldItem = before.items().stream().filter(item -> item.id().equals(branch.itemId())).findFirst()
                    .orElseThrow(() -> CampaignJson.bad("Conditional item does not exist"));
            if (!oldItem.characterId().equals(branch.targetId())) throw CampaignJson.bad("Conditional item has a different owner");
            long quantity = Math.addExact(oldItem.quantity(), branch.itemDelta());
            if (quantity < 0 || quantity > MAX_QUANTITY) throw CampaignJson.bad("Conditional item quantity is outside bounds");
            newItem = new Item(oldItem.id(), oldItem.characterId(), oldItem.name(), quantity,oldItem.sourceKey(),oldItem.description(),oldItem.active());
        }
        Position oldPosition = null, newPosition = null;
        if (branch.nodeId() != null) {
            if (before.nodes().stream().noneMatch(node -> node.id().equals(branch.nodeId()))) throw CampaignJson.bad("Conditional node does not exist");
            oldPosition = before.positions().stream().filter(position -> position.characterId().equals(branch.targetId())).findFirst().orElse(null);
            newPosition = new Position(branch.targetId(), branch.nodeId());
        }
        Command health = branch.healthEffect() == null ? null : healthCommand(material, branch.healthEffect());
        if (health != null) engine.executeCampaignCharacter(material, afterCheck, before.combats(), branch.targetId(), health, healthOperation, sides -> 1);
        return new Plan(branch, health, healthOperation, oldItem, newItem, oldPosition, newPosition);
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

    /**
     * Validates one retained root at its historical prefix using only retained dice, then advances
     * the caller's metadata reconstruction maps. These maps change only after all checks pass.
     */
    public static void validateAudit(State retained, Root root, Audit audit, Map<UUID, Node> nodes,
            Map<UUID, Item> items, Map<UUID, Position> positions) {
        validateAudit(retained, root, audit, nodes, items, positions, "check-effects", CampaignDomainCodec.text(audit));
    }
    static void validateAudit(State retained, Root root, Audit audit, Map<UUID, Node> nodes,
            Map<UUID, Item> items, Map<UUID, Position> positions, String rootKind, String rootAfter) {
        if (root == null || audit == null || !root.kind().equals(rootKind) || !root.before().isEmpty()
                || root.combatId() != null || !root.subjectId().equals(audit.input().characterId())
                || !root.after().equals(rootAfter) || root.sequence() > retained.roots().size()
                || !retained.roots().get(Math.toIntExact(root.sequence() - 1)).equals(root)) throw CampaignJson.bad("Invalid conditional root");
        State before = prefix(retained, Math.toIntExact(root.sequence() - 1), nodes, items, positions);
        Prepared prepared = prepare(before, audit.input(), root.operationId());
        if (!prepared.checkOperation().equals(audit.checkOperationId()) || !prepared.versions().equals(audit.characterVersions()))
            throw CampaignJson.bad("Conditional dependencies differ from their historical prefix");
        Event check = event(retained, audit.checkOperationId());
        if (check.checkResult() == null || !check.command().equals(audit.input().command()) || check.checkResult().success() != audit.success())
            throw CampaignJson.bad("Conditional outcome differs from the accepted check");
        Plan selected = audit.success() ? prepared.success() : prepared.failure();
        if (!Objects.equals(selected.healthOperation(), audit.healthOperationId())) throw CampaignJson.bad("Conditional health owner differs");
        ArrayDeque<Die> draws = new ArrayDeque<>();
        draws.add(new Die(20, check.checkResult().firstD20()));
        if (check.checkResult().secondD20() != null) draws.add(new Die(20, check.checkResult().secondD20()));
        if (selected.healthCommand() != null) {
            Event health = event(retained, selected.healthOperation());
            if (health.healthResult() == null || !health.command().equals(selected.healthCommand())) throw CampaignJson.bad("Missing conditional health facts");
            var effect = before.material().restore().projection().rules().stream().filter(FinalVitalEffect.class::isInstance).map(FinalVitalEffect.class::cast)
                    .filter(rule -> rule.identity().equals(selected.branch().healthEffect())).findFirst().orElseThrow();
            for (int die : health.healthResult().dice()) draws.add(new Die(effect.definition().diceSides(), die));
        }
        Result expected = execute(before, prepared, sides -> {
            Die draw = draws.pollFirst();
            if (draw == null || draw.sides() != sides) throw CampaignJson.bad("Conditional retained dice differ");
            return draw.value();
        });
        if (!draws.isEmpty() || !expected.audit().equals(audit)) throw CampaignJson.bad("Conditional audit arithmetic or metadata differs");
        Set<UUID> old = new HashSet<>(); before.characters().forEach(c -> c.history().events().forEach(e -> old.add(e.operationId())));
        List<UUID> children = expected.state().characters().stream().flatMap(c -> c.history().events().stream()).map(Event::operationId).filter(id -> !old.contains(id)).toList();
        if (new HashSet<>(root.characterOperations()).size() != root.characterOperations().size()
                || !new HashSet<>(root.characterOperations()).equals(new HashSet<>(children))) throw CampaignJson.bad("Conditional child ownership differs");
        Map<UUID, Item> afterItems = new HashMap<>(items); Map<UUID, Position> afterPositions = new HashMap<>(positions);
        if (audit.itemAfter() != null) afterItems.put(audit.itemAfter().id(), audit.itemAfter());
        if (audit.positionAfter() != null) afterPositions.put(audit.positionAfter().characterId(), audit.positionAfter());
        State actual = prefix(retained, Math.toIntExact(root.sequence()), nodes, afterItems, afterPositions);
        if (!actual.characters().equals(expected.state().characters()) || !actual.combats().equals(expected.state().combats()))
            throw CampaignJson.bad("Conditional character consequences differ from the one accepted branch");
        if (audit.itemAfter() != null) items.put(audit.itemAfter().id(), audit.itemAfter());
        if (audit.positionAfter() != null) positions.put(audit.positionAfter().characterId(), audit.positionAfter());
    }

    private static State prefix(State retained, int count, Map<UUID, Node> nodes, Map<UUID, Item> items, Map<UUID, Position> positions) {
        List<Root> roots = retained.roots().subList(0, count);
        Set<UUID> operations = new HashSet<>(), combatOperations = new HashSet<>(); Map<UUID, Profile> profiles = new HashMap<>();
        for (Root root : roots) {
            operations.addAll(root.characterOperations());
            if (root.combatId() != null) combatOperations.add(root.operationId());
            if (root.kind().equals("character-create") || root.kind().equals("character-profile")) {
                Profile profile = CampaignDomainCodec.decode(root.after().getBytes(StandardCharsets.UTF_8), Profile.class);
                profiles.put(profile.characterId(), profile);
            }
        }
        List<Character> characters = new ArrayList<>();
        for (Character retainedCharacter : retained.characters()) {
            List<Event> events = new ArrayList<>(); boolean suffix = false;
            for (Event event : retainedCharacter.history().events()) {
                if (operations.contains(event.operationId())) { if (suffix) throw CampaignJson.bad("Conditional dependency is not a history prefix"); events.add(event); }
                else suffix = true;
            }
            if (events.isEmpty()) continue;
            Profile profile = profiles.remove(retainedCharacter.id());
            if (profile == null) throw CampaignJson.bad("Conditional prefix lacks a character profile");
            characters.add(new Character(profile.name(), profile.kind(), profile.active(), retainedCharacter.initial(), new History(events.getLast().after(), events)));
        }
        if (!profiles.isEmpty()) throw CampaignJson.bad("Conditional prefix lacks initial character facts");
        List<JdbcSchema2CombatRuntimeRepository.History> combats = new ArrayList<>();
        for (var combat : retained.combats()) {
            var events = combat.events().stream().filter(e -> combatOperations.contains(e.operationId())).toList();
            if (!events.isEmpty()) {
                if (!combat.events().subList(0, events.size()).equals(events)) throw CampaignJson.bad("Conditional scene dependency is not a prefix");
                combats.add(new JdbcSchema2CombatRuntimeRepository.History(events.getLast().after(), combat.members(), events));
            }
        }
        CampaignManualData.State manual=CampaignManualData.State.empty();
        for(Root root:roots){if(root.kind().startsWith("manual-"))manual=CampaignManualCommands.prefix(manual,CampaignDomainCodec.decode(root.after().getBytes(StandardCharsets.UTF_8),CampaignManualCommands.Audit.class));else if(root.kind().equals("position")||root.kind().equals("item")){UUID owner=root.kind().equals("position")?CampaignDomainCodec.decode(root.after().getBytes(StandardCharsets.UTF_8),Position.class).characterId():CampaignDomainCodec.decode(root.after().getBytes(StandardCharsets.UTF_8),Item.class).characterId();manual=CampaignManualData.touch(manual,owner);}}
        return new State(retained.material(), characters, combats, sorted(nodes.values(), n -> n.id().toString()),
                sorted(positions.values(), p -> p.characterId().toString()), sorted(items.values(), i -> i.id().toString()), roots,manual);
    }

    static Input read(JsonObject input) {
        if (input == null) throw CampaignJson.bad("Missing conditional request");
        CampaignJson.fields(input, "characterId", "characterVersion", "command", "success", "failure");
        return new Input(UUID.fromString(CampaignJson.uuid(CampaignJson.string(input, "characterId"))), CampaignJson.integer(input, "characterVersion"),
                CampaignDomainCodec.fromJson(input.get("command"), Command.class), readBranch(CampaignJson.object(input, "success")), readBranch(CampaignJson.object(input, "failure")));
    }
    private static Branch readBranch(JsonObject branch) {
        CampaignJson.fields(branch, "targetId", "healthEffect", "itemId", "itemDelta", "nodeId", "message");
        String delta = CampaignJson.string(branch, "itemDelta");
        if (!delta.matches("0|-?[1-9][0-9]*")) throw CampaignJson.bad("Expected canonical signed quantity delta");
        final long quantity; try { quantity = Long.parseLong(delta); } catch (NumberFormatException invalid) { throw CampaignJson.bad("Quantity delta overflow"); }
        return new Branch(UUID.fromString(CampaignJson.uuid(CampaignJson.string(branch, "targetId"))),
                branch.get("healthEffect").isJsonNull() ? null : CampaignDomainCodec.fromJson(branch.get("healthEffect"), RuleIdentity.class),
                optionalUuid(branch, "itemId"), quantity, optionalUuid(branch, "nodeId"), CampaignJson.string(branch, "message"));
    }
    private static UUID optionalUuid(JsonObject input, String key) { return input.get(key).isJsonNull() ? null : UUID.fromString(CampaignJson.uuid(CampaignJson.string(input, key))); }
    private static Character character(State state, UUID id) {
        Character result = state.characters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(() -> CampaignJson.bad("Conditional target does not exist"));
        if (!result.active()) throw new StorageException("CHARACTER_INACTIVE", "Restore every declared conditional target first");
        return result;
    }
    private static Event event(State state, UUID operation) {
        var matches = state.characters().stream().flatMap(c -> c.history().events().stream()).filter(e -> e.operationId().equals(operation)).toList();
        if (matches.size() != 1) throw CampaignJson.bad("Conditional event is missing or duplicated"); return matches.getFirst();
    }
    private static UUID child(UUID root, String purpose, UUID target) {
        UUID derived = UUID.nameUUIDFromBytes(("campaign-check-effects/1/" + root + "/" + purpose + "/" + target).getBytes(StandardCharsets.US_ASCII));
        return new UUID((derived.getMostSignificantBits() & ~0xf000L) | 0x4000L,
                (derived.getLeastSignificantBits() & 0x3fffffffffffffffL) | 0x8000000000000000L);
    }
    private static List<Character> merge(List<Character> original, List<CampaignDomainEngine.CharacterRecord> changed) {
        List<Character> result = new ArrayList<>();
        for (Character character : original) {
            var replacement = changed.stream().filter(c -> c.initial().characterId().equals(character.id())).findFirst().orElseThrow();
            result.add(new Character(character.name(), character.kind(), character.active(), character.initial(), replacement.history()));
        }
        return result;
    }
    private static <T> List<T> sorted(Collection<T> values, java.util.function.Function<T, String> key) { return values.stream().sorted(Comparator.comparing(key)).toList(); }
    private static void uuid(UUID value) { if (value == null) throw CampaignJson.bad("Missing UUID"); CampaignJson.uuid(value.toString()); }
}
