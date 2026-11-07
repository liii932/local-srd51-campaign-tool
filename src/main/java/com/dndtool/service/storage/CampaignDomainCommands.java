package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.Schema2CombatState;
import com.google.gson.*;
import java.util.*;
import java.util.function.IntUnaryOperator;
import static com.dndtool.service.storage.CampaignDomainData.*;
import com.dndtool.service.storage.CampaignDomainData.Character;

/** Validated application commands. All results remain private until the caller commits the common transaction. */
public final class CampaignDomainCommands {
    private final CampaignDomainEngine engine = new CampaignDomainEngine();
    public State apply(CampaignData.Snapshot snapshot, String action, JsonObject input, UUID operationId, String intent, IntUnaryOperator dice) {
        return apply(snapshot, action, input, operationId, intent, dice, snapshot.domains() == null ? CampaignParticipationData.Actor.dm(null, null) : CampaignParticipationCommands.actualActor(snapshot.domains(), action, input));
    }
    public State apply(CampaignData.Snapshot snapshot, String action, JsonObject supplied, UUID operationId, String intent, IntUnaryOperator random, CampaignParticipationData.Actor actor) {
        State before = snapshot.domains();
        List<CampaignSpaceData.Die> rolls = new ArrayList<>();
        IntUnaryOperator dice = sides -> { int value = random.applyAsInt(sides); rolls.add(new CampaignSpaceData.Die(sides, value)); return value; };
        if (before == null) throw new StorageException("RULE_NOT_SELECTED", "Select the current campaign runtime module");
        if (before.roots().size() >= 100000) throw new StorageException("CAPACITY", "Complete root history is full");
        if (before.roots().stream().anyMatch(r -> r.operationId().equals(operationId))) throw new StorageException("IDEMPOTENCY_CONFLICT", "This business identity already belongs to retained history");
        CampaignParticipationCommands.validateActor(before, action, supplied, actor);
        JsonObject input = CampaignSpaceCommands.input(before, action, supplied);
        CampaignParticipationCommands.requireExecutionPermission(before, action, input, actor);
        var context = before.material().restore();
        var material = context.projection();
        List<Character> characters = new ArrayList<>(before.characters());
        var combats = new ArrayList<>(before.combats()); var nodes = new ArrayList<>(before.nodes());
        var positions = new ArrayList<>(before.positions()); var items = new ArrayList<>(before.items());
        var manual = before.manual(); var participation = before.participation();
        var space = before.space();
        UUID subject = UUID.fromString(snapshot.campaignId()), combatId = null; String oldText = "", newText = "";
        if (CampaignSpaceCommands.ACTIONS.contains(action)) {
            var result = CampaignSpaceCommands.resolve(snapshot, action, input, operationId, dice);
            var changed = result.state(); subject = result.subject(); combatId = result.combatId();
            characters = new ArrayList<>(changed.characters()); combats = new ArrayList<>(changed.combats());
            positions = new ArrayList<>(changed.positions()); space = changed.space();
        } else switch (action) {
            case "member-add", "member-update", "control-set", "share-set", "exploration-set" -> {
                var result = CampaignParticipationCommands.resolve(before, action, input, operationId, UUID.fromString(snapshot.campaignId()));
                participation = result.state(); subject = result.subjectId(); newText = CampaignDomainCodec.text(result.audit());
            }
            case "manual-create", "manual-profile", "manual-field", "manual-class", "manual-proficiency", "manual-item", "manual-check" -> {
                var result=CampaignManualCommands.resolve(before,action,input,operationId,dice);
                manual=result.state().manual();positions=new ArrayList<>(result.state().positions());items=new ArrayList<>(result.state().items());
                subject=result.subjectId();newText=CampaignDomainCodec.text(result.audit());
            }
            case "character-create" -> {
                CampaignJson.fields(input, "name", "kind", "classKey", "backgroundKey");
                if (characters.size() >= 16) throw new StorageException("CAPACITY", "Campaign roster is full");
                String name = CampaignJson.name(s(input, "name")), kind = s(input, "kind"), classKey = s(input, "classKey");
                if (!Set.of("PC", "NPC").contains(kind)) throw CampaignJson.bad("Unsupported character kind");
                subject = child(operationId, "character");
                Schema2PreparationIdentity identity = characters.isEmpty() ? new Schema2PreparationIdentity(child(operationId, "rule-run"), child(operationId, "rule-snapshot")) : characters.getFirst().initial().character().identity();
                var initial = context.initialPlan(UUID.fromString(snapshot.campaignId()), subject, identity, classKey, s(input, "backgroundKey"));
                Map<String, Integer> abilities = context.initialAbilities(classKey);
                var history = engine.initial(material, initial, child(operationId, "preparation"), abilities, context.initialChoices(classKey), operationId);
                var character = new Character(name, kind, true, initial, history); characters.add(character);
                oldText = "null"; newText = CampaignDomainCodec.text(new Profile(subject, name, kind, true));
            }
            case "character-profile" -> {
                CampaignJson.fields(input, "characterId", "name", "kind", "active");
                subject = id(input, "characterId"); int index = characterIndex(characters, subject); Character c = characters.get(index);
                if (c.active()) CampaignSpaceCommands.requireCharacter(before, subject);
                else CampaignSpaceCommands.requireNoPending(before);
                boolean active = bool(input, "active"); String name = CampaignJson.name(s(input, "name")), kind = s(input, "kind");
                if (!active && CampaignSpaceCommands.position(before, subject) != null) throw new StorageException("SUBJECT_PLACED", "Leave locatable play before archiving this character");
                oldText = CampaignDomainCodec.text(new Profile(subject, c.name(), c.kind(), c.active()));
                newText = CampaignDomainCodec.text(new Profile(subject, name, kind, active));
                characters.set(index, new Character(name, kind, active, c.initial(), c.history()));
            }
            case "character-command" -> {
                CampaignJson.fields(input, "characterId", "characterVersion", "command");
                subject = id(input, "characterId"); Character target = characters.get(characterIndex(characters, subject));
                if (!target.active()) throw new StorageException("CHARACTER_INACTIVE", "Restore this character first");
                if (target.history().current().version() != CampaignJson.integer(input, "characterVersion")) throw new StorageException("STALE_CHARACTER", "Refresh the character card");
                var command = CampaignDomainCodec.fromJson(input.get("command"), Schema2CharacterRuntime.Command.class);
                CampaignSpaceCommands.requireCharacter(before, subject);
                boolean unplaced = CampaignSpaceCommands.position(before, subject) == null;
                if (unplaced && !Set.of("ITEM_ACQUIRED", "ITEM_REMOVED", "ITEM_EQUIPPED", "ITEM_UNEQUIPPED", "CHARACTER_ADVANCED", "STATISTICS_ADVANCED", "SPELLBOOK_INITIALIZED", "SPELLBOOK_ADVANCED", "SPELLBOOK_PREPARED").contains(command.kind()))
                    throw new StorageException("SUBJECT_UNPLACED", "Place the character before executing game actions");
                if (!unplaced && CampaignSpaceCommands.currentBattle(before) != null) throw new StorageException("RULE_SCENE_OWNER_REQUIRED", "Use the current rule scene for character actions");
                if (command.kind().equals("ITEM_ACQUIRED")) {
                    var e = command.equipmentInput();
                    command = new Schema2CharacterRuntime.Command(command.kind(), null, 0, null, null, null, command.growthChoices(), null, null,
                            new Schema2CharacterRuntime.EquipmentInput(operationId, e.item(), e.location(), e.adjudication()), null);
                }
                var members = unplaced ? List.of(new CampaignDomainEngine.CharacterRecord(target.name(), target.kind(), target.initial(), target.history())) : CampaignSpaceCommands.focusedRecords(before);
                var changed = engine.executeSpaceCharacter(material, members, subject, command, operationId, dice);
                characters = new ArrayList<>(CampaignSpaceCommands.merge(characters, changed));
                long elapsed = personalSecond(characters.get(characterIndex(characters, subject))) - personalSecond(target);
                if (elapsed > 0) space = CampaignSpaceCommands.advance(space, elapsed);
            }
            case "adjudicated-check" -> {
                var result = CampaignAdjudicationCommands.resolve(snapshot, input, operationId, dice);
                characters = new ArrayList<>(result.state().characters()); positions = new ArrayList<>(result.state().positions()); items = new ArrayList<>(result.state().items());
                subject = result.subjectId(); newText = CampaignDomainCodec.text(result.audit());
            }
            case "check-effects" -> {
                var result = CampaignCheckEffects.resolve(snapshot, input, operationId, dice);
                characters = new ArrayList<>(result.state().characters());
                positions = new ArrayList<>(result.state().positions());
                items = new ArrayList<>(result.state().items());
                subject = result.subjectId();
                newText = CampaignDomainCodec.text(result.audit());
            }
            case "combat-command" -> {
                CampaignJson.fields(input, "combatId", "combatVersion", "command");
                var command = CampaignDomainCodec.fromJson(input.get("command"), Schema2CombatState.Command.class);
                var existing = CampaignSpaceCommands.currentBattle(before);
                if (existing == null || input.get("combatId").isJsonNull() || !existing.current().combatId().equals(id(input, "combatId"))) throw new StorageException("SPACE_FROZEN", "Only the focused rule scene can execute");
                long version = signed(s(input, "combatVersion"));
                if (existing.current().version() != version) throw new StorageException("STALE_COMBAT", "Refresh scene state");
                if (Set.of("START", "OPEN_SCENE", "SET_SCENE_ROSTER", "CLOSE_SCENE").contains(command.kind().name())) throw CampaignJson.bad("Use the explicit spatial lifecycle command");
                combatId = existing.current().combatId(); subject = combatId;
                preflightGrid(before, command);
                var changed = CampaignSpaceCommands.sceneCommand(snapshot.campaignId(), before, combatId, operationId, command, dice);
                characters = new ArrayList<>(changed.characters()); combats = new ArrayList<>(changed.combats()); positions = new ArrayList<>(changed.positions()); space = changed.space();
            }
            case "note" -> {
                CampaignJson.fields(input, "text"); newText = s(input, "text"); CampaignJson.validString(newText);
                if (newText.isBlank() || newText.codePointCount(0, newText.length()) > 4096) throw CampaignJson.bad("Note must contain 1–4096 code points");
            }
            case "node-create" -> {
                CampaignJson.fields(input, "name");
                if (nodes.size() >= 10000) throw new StorageException("CAPACITY", "World node capacity reached");
                subject = operationId;
                Node node = new Node(subject, CampaignJson.name(s(input, "name")), List.of()); nodes.add(node); oldText = "null"; newText = CampaignDomainCodec.text(node);
                space = CampaignSpaceCommands.nodeCreated(space, subject);
            }
            case "node-link" -> {
                CampaignJson.fields(input, "from", "to"); subject = id(input, "from"); UUID target = id(input, "to");
                if (!CampaignSpaceCommands.isWorldFocus(before) || !space.focus().space().id().equals(subject)) throw new StorageException("SPACE_FROZEN", "Edit routes at the focused source node");
                Node old = nodes.stream().filter(n -> n.id().equals(id(input, "from"))).findFirst().orElseThrow(() -> CampaignJson.bad("Node not found"));
                if (target.equals(subject) || nodes.stream().noneMatch(n -> n.id().equals(target)) || old.adjacent().contains(target)) throw CampaignJson.bad("Invalid edge");
                List<UUID> adjacent = new ArrayList<>(old.adjacent()); adjacent.add(target); adjacent.sort(Comparator.comparing(UUID::toString));
                Node node = new Node(subject, old.name(), adjacent); nodes.set(nodes.indexOf(old), node); oldText = CampaignDomainCodec.text(old); newText = CampaignDomainCodec.text(node);
            }
            case "item" -> {
                CampaignJson.fields(input, "itemId", "characterId", "name", "quantity");
                UUID owner = id(input, "characterId"); owner(before,owner);
                CampaignSpaceCommands.requireCharacter(before, owner);
                Item old = input.get("itemId").isJsonNull() ? null : items.stream().filter(i -> i.id().equals(id(input, "itemId"))).findFirst().orElseThrow(() -> CampaignJson.bad("Item not found"));
                if (old == null && items.size() >= 10000) throw new StorageException("CAPACITY", "Campaign item capacity reached");
                if (old != null && !old.characterId().equals(owner)) throw CampaignJson.bad("Item owner differs");
                subject = old == null ? operationId : old.id();
                long quantity=CampaignJson.integer(input,"quantity");boolean manualOwner=manual.characters().stream().anyMatch(c->c.id().equals(owner));
                if(manualOwner&&(quantity<1||quantity>999||old!=null&&!old.name().equals(s(input,"name"))))throw CampaignJson.bad("Manual simple item bounds or immutable name differ");
                Item item = old==null?new Item(subject, owner, s(input, "name"), quantity):new Item(subject,owner,s(input,"name"),quantity,old.sourceKey(),old.description(),old.active());
                if (old != null) items.remove(old); items.add(item); oldText = CampaignDomainCodec.text(old); newText = CampaignDomainCodec.text(item);
                manual=CampaignManualData.touch(manual,owner);
            }
            default -> throw new StorageException("UNSUPPORTED_COMMAND", "Unsupported campaign command");
        }
        Set<UUID> oldOperations = new HashSet<>(); before.characters().forEach(c -> c.history().events().forEach(e -> oldOperations.add(e.operationId())));
        List<UUID> childOperations = characters.stream().flatMap(c -> c.history().events().stream()).map(Schema2CharacterRuntime.Event::operationId).filter(id -> !oldOperations.contains(id)).sorted(Comparator.comparing(UUID::toString)).toList();
        characters.sort(Comparator.comparing(c -> c.id().toString())); combats.sort(Comparator.comparing(c -> c.current().combatId().toString()));
        nodes.sort(Comparator.comparing(n -> n.id().toString())); positions.sort(Comparator.comparing(p -> p.characterId().toString())); items.sort(Comparator.comparing(i -> i.id().toString()));
        var audit = new CampaignSpaceData.Audit(before.space(), space, before.positions(), positions, new String(CampaignJson.canonical(supplied), java.nio.charset.StandardCharsets.UTF_8));
        List<Root> roots = new ArrayList<>(before.roots()); roots.add(new Root(roots.size() + 1L, operationId, action, subject, intent, oldText, newText, childOperations, combatId, actor, audit, rolls));
        participation = CampaignParticipationCommands.afterSpaceChange(before.space(), space, before.positions(), positions, participation);
        var after = new State(before.material(), characters, combats, nodes, positions, items, roots,manual,participation,space);
        CampaignParticipationCommands.validateCurrent(after);
        return after;
    }
    private static int characterIndex(List<Character> characters, UUID id) {
        for (int i = 0; i < characters.size(); i++) if (characters.get(i).id().equals(id)) return i;
        throw new StorageException("CHARACTER_NOT_FOUND", "Character not found in this campaign");
    }
    private static long personalSecond(Character c) { return c.history().current().rest().minute() * 60 + c.history().current().rest().second(); }
    private static UUID child(UUID operation, String purpose) {
        UUID value = UUID.nameUUIDFromBytes(("campaign-space-identity/1/" + operation + "/" + purpose).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return new UUID((value.getMostSignificantBits() & ~0xf000L) | 0x4000L, (value.getLeastSignificantBits() & 0x3fffffffffffffffL) | 0x8000000000000000L);
    }
    static void preflightGrid(State state, Schema2CombatState.Command command) {
        var ref = state.space().focus().space(); var scene = state.space().scene(ref.id());
        List<Schema2CombatState.Position> path = command.path();
        if (command.kind() == Schema2CombatState.Kind.READY_MOVE && command.readyInput() != null)
            path = command.readyInput().path();
        if (command.kind() == Schema2CombatState.Kind.RELEASE_READY) {
            var battle = CampaignSpaceCommands.currentBattle(state);
            if (battle != null) path = battle.current().readyPreparations().stream()
                    .filter(ready -> ready.readyKey().equals(command.readyKey()) && ready.actorId().equals(command.actorId()))
                    .map(ready -> ready.input().path()).findFirst().orElse(List.of());
        }
        for (var cell : path) {
            CampaignSpaceCommands.checkCell(scene, cell);
            if (state.positions().stream().anyMatch(p -> p.space().equals(ref) && !p.characterId().equals(command.actorId()) && Objects.equals(p.grid(), cell))) throw new StorageException("SCENE_CELL_OCCUPIED", "The complete scene occupancy blocks this path");
        }
        boolean untyped = state.positions().stream().filter(p -> p.space().equals(ref)).anyMatch(p -> state.manual().characters().stream().anyMatch(c -> c.id().equals(p.characterId())));
        if (untyped && (command.kind() == Schema2CombatState.Kind.MONSTER_BREATH || command.castInput() != null && command.castInput().coneDirection() != null))
            throw new StorageException("SCENE_RULE_FACTS_REQUIRED", "Use DM adjudication for an area action involving untyped scene participants");
        if (command.zoneInput() != null) CampaignSpaceCommands.checkCell(scene, command.zoneInput().center());
    }
    private static void owner(State state,UUID id){
        var manual=state.manual().characters().stream().filter(c->c.id().equals(id)).findFirst();
        if(manual.isPresent()){if(!manual.get().current().active())throw new StorageException("CHARACTER_INACTIVE","Restore the free character card first");return;}
        var character=state.characters().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(()->new StorageException("CHARACTER_NOT_FOUND","Character not found in this campaign"));
        if(!character.active())throw new StorageException("CHARACTER_INACTIVE","Restore the character first");
    }
    private static String s(JsonObject o, String key) { return CampaignJson.string(o, key); }
    private static UUID id(JsonObject o, String key) { return UUID.fromString(CampaignJson.uuid(s(o, key))); }
    private static boolean bool(JsonObject o, String key) {
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isBoolean()) throw CampaignJson.bad("Expected boolean"); return o.get(key).getAsBoolean();
    }
    private static long signed(String value) {
        if (!value.matches("-1|0|[1-9][0-9]*")) throw CampaignJson.bad("Invalid version"); return Long.parseLong(value);
    }
}
