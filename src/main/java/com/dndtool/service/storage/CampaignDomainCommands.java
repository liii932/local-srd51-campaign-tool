package com.dndtool.service.storage;

import com.dndtool.service.CampaignDomainEngine;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
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
        State before = snapshot.domains();
        if (before == null) throw new StorageException("RULE_NOT_SELECTED", "Select the current campaign runtime module");
        if (before.roots().stream().anyMatch(r -> r.operationId().equals(operationId))) throw new StorageException("IDEMPOTENCY_CONFLICT", "This business identity already belongs to retained history");
        var material = before.material().restore();
        List<Character> characters = new ArrayList<>(before.characters());
        var combats = new ArrayList<>(before.combats()); var nodes = new ArrayList<>(before.nodes());
        var positions = new ArrayList<>(before.positions()); var items = new ArrayList<>(before.items());
        var manual = before.manual();
        UUID subject = UUID.fromString(snapshot.campaignId()), combatId = null; String oldText = "", newText = "";
        switch (action) {
            case "manual-create", "manual-profile", "manual-field", "manual-class", "manual-proficiency", "manual-item", "manual-check",
                    "manual-encounter-start", "manual-encounter-members", "manual-encounter-close", "manual-party-position" -> {
                var result=CampaignManualCommands.resolve(before,action,input,operationId,dice);
                manual=result.state().manual();positions=new ArrayList<>(result.state().positions());items=new ArrayList<>(result.state().items());
                subject=result.subjectId();newText=CampaignDomainCodec.text(result.audit());
            }
            case "character-create" -> {
                CampaignJson.fields(input, "name", "kind", "classKey", "backgroundKey"); requireNoScene(before);
                if (characters.size() >= 16) throw new StorageException("CAPACITY", "Campaign roster is full");
                if (characters.stream().anyMatch(c -> c.history().current().rest().minute() != 0 || c.history().current().rest().second() != 0)) throw new StorageException("ROSTER_ALREADY_STARTED", "Create the complete initial roster before advancing campaign time");
                String name = CampaignJson.name(s(input, "name")), kind = s(input, "kind"), classKey = s(input, "classKey");
                if (!Set.of("PC", "NPC").contains(kind)) throw CampaignJson.bad("Unsupported character kind");
                subject = UUID.randomUUID();
                Identity identity = characters.isEmpty() ? new Identity(UUID.randomUUID(), UUID.randomUUID()) : characters.getFirst().initial().character().identity();
                var initial = BundledCampaignRules.initialPlan(UUID.fromString(snapshot.campaignId()), subject, identity, classKey, s(input, "backgroundKey"));
                Map<String, Integer> abilities = switch (classKey.replace("class.", "")) {
                    case "fighter" -> Map.of("ability.strength", 15, "ability.dexterity", 13, "ability.constitution", 14, "ability.intelligence", 8, "ability.wisdom", 12, "ability.charisma", 10);
                    case "cleric" -> Map.of("ability.strength", 13, "ability.dexterity", 10, "ability.constitution", 14, "ability.intelligence", 8, "ability.wisdom", 15, "ability.charisma", 12);
                    case "wizard" -> Map.of("ability.strength", 8, "ability.dexterity", 14, "ability.constitution", 13, "ability.intelligence", 15, "ability.wisdom", 12, "ability.charisma", 10);
                    default -> throw CampaignJson.bad("Unsupported initial class");
                };
                var history = engine.initial(material, initial, UUID.randomUUID(), abilities, BundledCampaignRules.initialChoices(classKey), operationId);
                var character = new Character(name, kind, true, initial, history); characters.add(character);
                oldText = "null"; newText = CampaignDomainCodec.text(new Profile(subject, name, kind, true));
            }
            case "character-profile" -> {
                CampaignJson.fields(input, "characterId", "name", "kind", "active"); requireNoScene(before);
                subject = id(input, "characterId"); int index = characterIndex(characters, subject); Character c = characters.get(index);
                boolean active = bool(input, "active"); String name = CampaignJson.name(s(input, "name")), kind = s(input, "kind");
                oldText = CampaignDomainCodec.text(new Profile(subject, c.name(), c.kind(), c.active()));
                newText = CampaignDomainCodec.text(new Profile(subject, name, kind, active));
                characters.set(index, new Character(name, kind, active, c.initial(), c.history()));
            }
            case "character-command" -> {
                if(CampaignManualData.active(manual))throw new StorageException("SCENE_ACTIVE","The simple encounter owns this campaign");
                CampaignJson.fields(input, "characterId", "characterVersion", "command");
                subject = id(input, "characterId"); Character target = characters.get(characterIndex(characters, subject));
                if (!target.active()) throw new StorageException("CHARACTER_INACTIVE", "Restore this character first");
                if (target.history().current().version() != CampaignJson.integer(input, "characterVersion")) throw new StorageException("STALE_CHARACTER", "Refresh the character card");
                var command = CampaignDomainCodec.fromJson(input.get("command"), JdbcSchema2CharacterRuntimeRepository.Command.class);
                if (command.kind().equals("ITEM_ACQUIRED")) {
                    var e = command.equipmentInput();
                    command = new JdbcSchema2CharacterRuntimeRepository.Command(command.kind(), null, 0, null, null, null, command.growthChoices(), null, null,
                            new JdbcSchema2CharacterRuntimeRepository.EquipmentInput(operationId, e.item(), e.location(), e.adjudication()), null);
                }
                var changed = engine.executeCampaignCharacter(material, CampaignDomainValidation.records(before), before.combats(), subject, command, operationId, dice);
                characters = merge(characters, changed);
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
                if(CampaignManualData.active(manual))throw new StorageException("SCENE_ACTIVE","The simple encounter owns this campaign");
                CampaignJson.fields(input, "combatId", "combatVersion", "command");
                var command = CampaignDomainCodec.fromJson(input.get("command"), Schema2CombatState.Command.class);
                var existing = input.get("combatId").isJsonNull() ? null : combats.stream().filter(c -> c.current().combatId().equals(id(input, "combatId"))).findFirst().orElseThrow(() -> new StorageException("COMBAT_NOT_FOUND", "Scene not found"));
                long version = signed(s(input, "combatVersion"));
                if (existing == null ? version != -1 || command.kind() != Schema2CombatState.Kind.START : existing.current().version() != version) throw new StorageException("STALE_COMBAT", "Refresh scene state");
                if (existing == null && combats.stream().anyMatch(c -> c.current().status() != Schema2CombatState.Status.ENDED)) throw new StorageException("SCENE_ACTIVE", "Another scene owns this campaign");
                if (characters.stream().anyMatch(c -> !c.active())) throw new StorageException("INACTIVE_ROSTER", "Restore the full roster before entering a shared scene");
                combatId = existing == null ? UUID.randomUUID() : existing.current().combatId(); subject = combatId;
                var result = engine.executeCombat(material, CampaignDomainValidation.records(before), existing, combatId, operationId, command, dice);
                if (existing != null) combats.remove(existing); combats.add(result.history()); characters = merge(characters, result.characters());
            }
            case "note" -> {
                CampaignJson.fields(input, "text"); newText = s(input, "text"); CampaignJson.validString(newText);
                if (newText.isBlank() || newText.codePointCount(0, newText.length()) > 4096) throw CampaignJson.bad("Note must contain 1–4096 code points");
            }
            case "node-create" -> {
                CampaignJson.fields(input, "name"); subject = UUID.randomUUID();
                Node node = new Node(subject, CampaignJson.name(s(input, "name")), List.of()); nodes.add(node); oldText = "null"; newText = CampaignDomainCodec.text(node);
            }
            case "node-link" -> {
                CampaignJson.fields(input, "from", "to"); subject = id(input, "from"); UUID target = id(input, "to");
                Node old = nodes.stream().filter(n -> n.id().equals(id(input, "from"))).findFirst().orElseThrow(() -> CampaignJson.bad("Node not found"));
                if (target.equals(subject) || nodes.stream().noneMatch(n -> n.id().equals(target)) || old.adjacent().contains(target)) throw CampaignJson.bad("Invalid edge");
                List<UUID> adjacent = new ArrayList<>(old.adjacent()); adjacent.add(target); adjacent.sort(Comparator.comparing(UUID::toString));
                Node node = new Node(subject, old.name(), adjacent); nodes.set(nodes.indexOf(old), node); oldText = CampaignDomainCodec.text(old); newText = CampaignDomainCodec.text(node);
            }
            case "position" -> {
                CampaignJson.fields(input, "characterId", "nodeId"); requireNoRichScene(before); subject = id(input, "characterId"); owner(before,subject);
                boolean manualOwner=manual.characters().stream().anyMatch(c->c.id().equals(id(input,"characterId")));
                boolean member=manual.encounters().stream().filter(e->e.status().equals("ACTIVE")).flatMap(e->e.participants().stream()).anyMatch(p->p.characterId().equals(id(input,"characterId")));
                if((manualOwner||CampaignManualData.active(manual))&&!member)throw CampaignJson.bad("Position requires an existing simple encounter member");
                UUID nodeId = id(input, "nodeId"); if (nodes.stream().noneMatch(n -> n.id().equals(nodeId))) throw CampaignJson.bad("Node not found");
                Position old = positions.stream().filter(p -> p.characterId().equals(id(input, "characterId"))).findFirst().orElse(null);
                Position position = new Position(subject, nodeId); if (old != null) positions.remove(old); positions.add(position);
                oldText = CampaignDomainCodec.text(old); newText = CampaignDomainCodec.text(position);
                manual=CampaignManualData.touch(manual,subject);
            }
            case "item" -> {
                CampaignJson.fields(input, "itemId", "characterId", "name", "quantity"); requireNoRichScene(before);
                UUID owner = id(input, "characterId"); owner(before,owner);
                Item old = input.get("itemId").isJsonNull() ? null : items.stream().filter(i -> i.id().equals(id(input, "itemId"))).findFirst().orElseThrow(() -> CampaignJson.bad("Item not found"));
                if (old != null && !old.characterId().equals(owner)) throw CampaignJson.bad("Item owner differs");
                subject = old == null ? UUID.randomUUID() : old.id();
                long quantity=CampaignJson.integer(input,"quantity");boolean manualOwner=manual.characters().stream().anyMatch(c->c.id().equals(owner));
                if(manualOwner&&(quantity<1||quantity>999||old!=null&&!old.name().equals(s(input,"name"))))throw CampaignJson.bad("Manual simple item bounds or immutable name differ");
                Item item = old==null?new Item(subject, owner, s(input, "name"), quantity):new Item(subject,owner,s(input,"name"),quantity,old.sourceKey(),old.description(),old.active());
                if (old != null) items.remove(old); items.add(item); oldText = CampaignDomainCodec.text(old); newText = CampaignDomainCodec.text(item);
                manual=CampaignManualData.touch(manual,owner);
            }
            default -> throw new StorageException("UNSUPPORTED_COMMAND", "Unsupported campaign command");
        }
        Set<UUID> oldOperations = new HashSet<>(); before.characters().forEach(c -> c.history().events().forEach(e -> oldOperations.add(e.operationId())));
        List<UUID> childOperations = characters.stream().flatMap(c -> c.history().events().stream()).map(JdbcSchema2CharacterRuntimeRepository.Event::operationId).filter(id -> !oldOperations.contains(id)).sorted(Comparator.comparing(UUID::toString)).toList();
        List<Root> roots = new ArrayList<>(before.roots()); roots.add(new Root(roots.size() + 1L, operationId, action, subject, intent, oldText, newText, childOperations, combatId));
        characters.sort(Comparator.comparing(c -> c.id().toString())); combats.sort(Comparator.comparing(c -> c.current().combatId().toString()));
        nodes.sort(Comparator.comparing(n -> n.id().toString())); positions.sort(Comparator.comparing(p -> p.characterId().toString())); items.sort(Comparator.comparing(i -> i.id().toString()));
        return new State(before.material(), characters, combats, nodes, positions, items, roots,manual);
    }
    private static List<Character> merge(List<Character> previous, List<CampaignDomainEngine.CharacterRecord> updated) {
        List<Character> result = new ArrayList<>();
        for (Character original : previous) {
            var replacement = updated.stream().filter(c -> c.initial().characterId().equals(original.id())).findFirst().orElseThrow(() -> CampaignJson.bad("Incomplete character consequence set"));
            result.add(new Character(original.name(), original.kind(), original.active(), replacement.initial(), replacement.history()));
        }
        return result;
    }
    private static int characterIndex(List<Character> characters, UUID id) {
        for (int i = 0; i < characters.size(); i++) if (characters.get(i).id().equals(id)) return i;
        throw new StorageException("CHARACTER_NOT_FOUND", "Character not found in this campaign");
    }
    private static void requireNoScene(State state) {
        if(CampaignManualData.active(state.manual()))throw new StorageException("SCENE_ACTIVE","The simple encounter owns this campaign");
        requireNoRichScene(state);
    }
    private static void requireNoRichScene(State state) {
        if (state.combats().stream().anyMatch(c -> c.current().status() != Schema2CombatState.Status.ENDED)) throw new StorageException("SCENE_ACTIVE", "Use the current scene owner for this operation");
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
