package com.dndtool.service.storage;

import com.dndtool.service.*;
import com.dndtool.service.storage.CampaignDomainData.Character;
import com.google.gson.*;
import java.util.*;
import java.util.function.IntUnaryOperator;
import static com.dndtool.service.storage.CampaignDomainData.*;
import static com.dndtool.service.storage.CampaignSpaceData.*;

/** Finite space intents and the shared execution gate, inside the caller-owned campaign transaction. */
public final class CampaignSpaceCommands {
    private CampaignSpaceCommands() { }
    public static final Set<String> ACTIONS = Set.of("focus-set", "scene-create", "scene-enter", "scene-leave",
            "scene-close", "scene-mode", "space-time", "space-move", "space-unplace", "travel", "position", "monster-create");
    private static final Set<String> ADMIN = Set.of("member-add", "member-update", "control-set", "share-set", "exploration-set", "note", "node-create", "character-create", "manual-create");
    public record Result(CampaignDomainData.State state, UUID subject, UUID combatId) { }

    public static JsonObject focusPayload(CampaignDomainData.State state) {
        JsonObject result = new JsonObject(); Focus focus = state.space().focus();
        result.add("space", refJson(focus.space())); result.addProperty("generation", Long.toString(focus.generation())); return result;
    }
    public static JsonObject refJson(Ref ref) {
        if (ref == null) return null;
        JsonObject result = new JsonObject(); result.addProperty("kind", ref.kind()); result.addProperty("id", ref.id().toString()); return result;
    }
    public static void requireFocus(CampaignDomainData.State state, JsonObject payload) {
        JsonObject focus = CampaignJson.object(payload, "focus"); CampaignJson.fields(focus, "space", "generation");
        Ref expected = focus.get("space").isJsonNull() ? null : readRef(focus.getAsJsonObject("space"));
        if (!Objects.equals(expected, state.space().focus().space())
                || CampaignJson.integer(focus, "generation") != state.space().focus().generation())
            throw new StorageException("STALE_FOCUS", "Refresh this space and its execution focus");
    }
    public static JsonObject input(CampaignDomainData.State state, String action, JsonObject supplied) {
        if (!ADMIN.contains(action) || supplied.has("focus")) requireFocus(state, supplied);
        JsonObject input = supplied.deepCopy(); input.remove("focus"); return input;
    }
    public static boolean administrative(String action) { return ADMIN.contains(action); }
    public static boolean focused(CampaignDomainData.State state, UUID subject) {
        Position p = position(state, subject); return p != null && p.space().equals(state.space().focus().space());
    }
    public static boolean isWorldFocus(CampaignDomainData.State state) {
        return state.space().focus().space() != null && state.space().focus().space().kind().equals("WORLD");
    }
    public static Position position(CampaignDomainData.State state, UUID subject) {
        return state.positions().stream().filter(p -> p.characterId().equals(subject)).findFirst().orElse(null);
    }
    public static Schema2CombatHistory.History currentBattle(CampaignDomainData.State state) {
        Ref ref = state.space().focus().space(); if (ref == null || !ref.kind().equals("SCENE")) return null;
        return state.combats().stream().filter(h -> h.current().combatId().equals(ref.id()) && h.current().status() != Schema2CombatState.Status.ENDED).findFirst().orElse(null);
    }
    public static void requireNoPending(CampaignDomainData.State state) {
        var battle = currentBattle(state);
        if (battle != null && battle.current().activeFlow() != null) throw new StorageException("ACTION_FLOW_BUSY", "Continue the focused scene's pending action first");
    }
    public static void requireCharacter(CampaignDomainData.State state, UUID subject) {
        boolean active = state.characters().stream().anyMatch(c -> c.id().equals(subject) && c.active())
                || state.manual().characters().stream().anyMatch(c -> c.id().equals(subject) && c.current().active())
                || state.space().monsters().stream().anyMatch(m -> m.id().equals(subject));
        if (!active) throw new StorageException("CHARACTER_INACTIVE", "Unknown or inactive spatial subject");
        if (position(state, subject) != null && !focused(state, subject)) throw new StorageException("SPACE_FROZEN", "This subject belongs to a background space");
        requireNoPending(state);
    }
    public static void requirePlaced(CampaignDomainData.State state, UUID subject) {
        requireCharacter(state, subject);
        if (!focused(state, subject)) throw new StorageException("SUBJECT_UNPLACED", "Place this subject before a game action");
    }
    public static List<CampaignDomainEngine.CharacterRecord> focusedRecords(CampaignDomainData.State state) {
        return CampaignDomainValidation.records(state).stream().filter(r -> focused(state, r.initial().characterId())).toList();
    }
    public static CampaignDomainData.State withSpace(CampaignDomainData.State state, CampaignSpaceData.State space, List<Position> positions) {
        return new CampaignDomainData.State(state.material(), state.characters(), state.combats(), state.nodes(), positions,
                state.items(), state.roots(), state.manual(), state.participation(), space);
    }
    public static CampaignSpaceData.State nodeCreated(CampaignSpaceData.State space, UUID id) {
        List<Clock> clocks = new ArrayList<>(space.clocks()); clocks.add(new Clock(Ref.world(id), 0, 0));
        return ordered(space.focus(), space.scenes(), clocks, space.monsters());
    }
    public static CampaignSpaceData.State advance(CampaignSpaceData.State space, long seconds) {
        if (seconds < 0) throw CampaignJson.bad("Negative elapsed time");
        if (seconds == 0) return space;
        Ref focus = space.focus().space(); Clock previous = space.clock(focus);
        List<Clock> clocks = new ArrayList<>(space.clocks()); clocks.remove(previous);
        clocks.add(new Clock(focus, Math.addExact(previous.second(), seconds), Math.addExact(previous.version(), 1)));
        return ordered(space.focus(), space.scenes(), clocks, space.monsters());
    }
    public static CampaignSpaceData.State focus(CampaignSpaceData.State space, Ref ref) {
        space.clock(ref);
        if (ref.kind().equals("SCENE") && space.scene(ref.id()).mode().equals("CLOSED")) throw CampaignJson.bad("Closed scene cannot receive focus");
        return ordered(new Focus(ref, Math.addExact(space.focus().generation(), 1)), space.scenes(), space.clocks(), space.monsters());
    }
    public static CampaignSpaceData.State scene(CampaignSpaceData.State space, Scene scene) {
        List<Scene> scenes = new ArrayList<>(space.scenes()); scenes.removeIf(s -> s.id().equals(scene.id())); scenes.add(scene);
        return ordered(space.focus(), scenes, space.clocks(), space.monsters());
    }
    public static CampaignSpaceData.State ordered(Focus focus, List<Scene> scenes, List<Clock> clocks, List<Monster> monsters) {
        return new CampaignSpaceData.State(focus, scenes.stream().sorted(Comparator.comparing(s -> s.id().toString())).toList(),
                clocks.stream().sorted(Comparator.comparing(c -> c.space().kind() + c.space().id())).toList(),
                monsters.stream().sorted(Comparator.comparing(m -> m.id().toString())).toList());
    }

    public static Result resolve(CampaignData.Snapshot snapshot, String action, JsonObject input, UUID operation, IntUnaryOperator dice) {
        CampaignDomainData.State before = snapshot.domains(); CampaignDomainData.State state = before;
        Ref current = before.space().focus().space(); UUID subject = UUID.fromString(snapshot.campaignId()), combatId = null;
        if (!action.equals("focus-set")) requireNoPending(before);
        switch (action) {
            case "focus-set" -> {
                CampaignJson.fields(input, "target"); Ref target = readRef(CampaignJson.object(input, "target"));
                state = withSpace(before, focus(before.space(), target), before.positions()); subject = target.id();
            }
            case "scene-create" -> {
                CampaignJson.fields(input, "name", "nodeId", "width", "height", "resolution");
                UUID anchor = id(input, "nodeId"); requireWorld(before, anchor);
                if (before.space().scenes().size() >= 128) throw new StorageException("CAPACITY", "Scene capacity reached");
                subject = operation;
                Scene created = new Scene(subject, s(input, "name"), anchor, integer(input, "width"), integer(input, "height"), s(input, "resolution"), "EXPLORATION", 0);
                CampaignSpaceData.State space = scene(before.space(), created); List<Clock> clocks = new ArrayList<>(space.clocks()); clocks.add(new Clock(Ref.scene(subject), 0, 0));
                state = withSpace(before, ordered(space.focus(), space.scenes(), clocks, space.monsters()), before.positions());
                if (created.resolution().equals("RULES")) {
                    state = sceneRoster(snapshot.campaignId(), before, state, subject, operation, dice); combatId = subject;
                }
            }
            case "position" -> {
                CampaignJson.fields(input, "characterId", "nodeId"); subject = id(input, "characterId"); UUID node = id(input, "nodeId"); requireWorld(before, node); requireCharacter(before, subject);
                if (position(before, subject) != null) throw new StorageException("SUBJECT_ALREADY_PLACED", "Use an explicit space transfer for a placed subject");
                state = withSpace(before, before.space(), replace(before.positions(), new Position(subject, current, null, 0, nextPlacementVersion(before, subject))));
            }
            case "travel" -> {
                CampaignJson.fields(input, "targetNodeId", "minutes", "adjudication", "subjects"); requireWorld(before, current == null ? null : current.id());
                UUID target = id(input, "targetNodeId"); List<Position> selected = selected(before, input, false);
                Node source = before.nodes().stream().filter(n -> n.id().equals(current.id())).findFirst().orElseThrow();
                if (!source.adjacent().contains(target)) throw new StorageException("WORLD_ROUTE_INVALID", "The selected destination has no outgoing route from this node");
                long minutes = minutes(input); reason(input); transferable(before, selected);
                state = elapsed(before, minutes, operation, dice);
                List<Position> positions = new ArrayList<>(state.positions());
                for (Position old : selected) positions = replace(positions, new Position(old.characterId(), Ref.world(target), null, old.side(), old.version() + 1));
                state = withSpace(state, focus(state.space(), Ref.world(target)), positions); subject = target;
            }
            case "scene-enter" -> {
                CampaignJson.fields(input, "sceneId", "subjects"); UUID destination = id(input, "sceneId"); Scene target = before.space().scene(destination); requireWorld(before, target.nodeId());
                if (!target.mode().equals("EXPLORATION")) throw new StorageException("SCENE_MODE_CONFLICT", "Enter an idle exploration scene");
                requireIdleScene(before, destination); List<Position> selected = selected(before, input, true); transferable(before, selected);
                List<Position> positions = new ArrayList<>(before.positions());
                JsonArray requested = CampaignJson.array(input, "subjects");
                for (int i = 0; i < selected.size(); i++) {
                    JsonObject row = requested.get(i).getAsJsonObject(); Position old = selected.get(i);
                    var grid = new Schema2CombatState.Position(integer(row, "x"), integer(row, "y")); checkCell(target, grid);
                    positions = replace(positions, new Position(old.characterId(), Ref.scene(destination), grid, integer(row, "side"), old.version() + 1));
                }
                uniqueCells(positions); state = withSpace(before, focus(before.space(), Ref.scene(destination)), positions);
                if (target.resolution().equals("RULES")) { state = sceneRoster(snapshot.campaignId(), before, state, destination, operation, dice); combatId = destination; }
                subject = destination;
            }
            case "scene-leave" -> {
                CampaignJson.fields(input, "subjects"); Scene source = requireScene(before); if (!source.mode().equals("EXPLORATION")) throw new StorageException("SCENE_MODE_CONFLICT", "End combat before leaving the scene");
                List<Position> selected = selected(before, input, false); transferable(before, selected);
                List<Position> positions = new ArrayList<>(before.positions());
                for (Position old : selected) positions = replace(positions, new Position(old.characterId(), Ref.world(source.nodeId()), null, old.side(), old.version() + 1));
                state = withSpace(before, focus(before.space(), Ref.world(source.nodeId())), positions);
                if (source.resolution().equals("RULES")) { state = sceneRoster(snapshot.campaignId(), before, state, source.id(), operation, dice); combatId = source.id(); }
                subject = source.id();
            }
            case "scene-close" -> {
                CampaignJson.fields(input); Scene source = requireScene(before);
                if (before.positions().stream().anyMatch(p -> p.space().equals(current))) throw new StorageException("SCENE_OCCUPIED", "Move every subject out before closing a scene");
                requireIdleScene(before, source.id());
                if (source.resolution().equals("RULES")) {
                    state = sceneCommand(snapshot.campaignId(), before, source.id(), operation, Schema2CombatState.Command.action(Schema2CombatState.Kind.CLOSE_SCENE, null), dice); combatId = source.id();
                }
                state = withSpace(state, focus(scene(state.space(), new Scene(source.id(), source.name(), source.nodeId(), source.width(), source.height(), source.resolution(), "CLOSED", source.version() + 1)), Ref.world(source.nodeId())), state.positions()); subject = source.id();
            }
            case "scene-mode" -> {
                CampaignJson.fields(input, "mode"); Scene source = requireScene(before); String mode = s(input, "mode");
                if (!source.resolution().equals("DM") || !Set.of("EXPLORATION", "COMBAT").contains(mode) || source.mode().equals(mode)) throw CampaignJson.bad("Use the scene's declared resolution mode");
                state = withSpace(before, scene(before.space(), new Scene(source.id(), source.name(), source.nodeId(), source.width(), source.height(), "DM", mode, source.version() + 1)), before.positions()); subject = source.id();
            }
            case "space-time" -> {
                CampaignJson.fields(input, "minutes", "adjudication"); requireDirectClock(before); reason(input); long minutes = minutes(input);
                if (minutes == 0) throw CampaignJson.bad("Time advance must be positive"); state = elapsed(before, minutes, operation, dice); subject = current.id();
            }
            case "space-unplace" -> {
                CampaignJson.fields(input, "characterId", "version", "adjudication"); subject = id(input, "characterId"); requireWorld(before, current == null ? null : current.id());
                Position old = checkedPosition(before, subject, CampaignJson.integer(input, "version")); transferable(before, List.of(old)); reason(input);
                if (before.characters().stream().filter(c -> c.id().equals(old.characterId())).anyMatch(c -> c.history().current().health().stable())) throw new StorageException("SPACE_OBLIGATION_PENDING", "Resolve a timed recovery before leaving locatable play");
                List<Position> positions = new ArrayList<>(before.positions()); positions.remove(old); state = withSpace(before, before.space(), positions);
            }
            case "space-move" -> {
                CampaignJson.fields(input, "characterId", "version", "path", "minutes", "adjudication"); Scene scene = requireScene(before); subject = id(input, "characterId");
                Position old = checkedPosition(before, subject, CampaignJson.integer(input, "version")); reason(input);
                boolean free = before.manual().characters().stream().anyMatch(c -> c.id().equals(old.characterId()));
                if (!scene.resolution().equals("DM") && !free) throw new StorageException("RULE_SCENE_OWNER_REQUIRED", "Use the rule scene's movement command");
                long minutes = minutes(input); if (scene.resolution().equals("RULES") && minutes != 0) throw CampaignJson.bad("Rule scene time must be advanced by its owner");
                var from = old.grid(); JsonArray path = CampaignJson.array(input, "path"); if (path.isEmpty() || path.size() > 64) throw CampaignJson.bad("Invalid movement path");
                for (var element : path) { JsonObject cell = element.getAsJsonObject(); CampaignJson.fields(cell, "x", "y"); var next = new Schema2CombatState.Position(integer(cell, "x"), integer(cell, "y")); checkCell(scene, next);
                    if (from.distanceFeet(next) != 5 || occupied(before.positions(), old.space(), next, old.characterId())) throw new StorageException("SCENE_PATH_INVALID", "A path step is not adjacent or is occupied"); from = next; }
                state = elapsed(before, minutes, operation, dice);
                state = withSpace(state, state.space(), replace(state.positions(), new Position(subject, old.space(), from, old.side(), old.version() + 1)));
            }
            case "monster-create" -> {
                if (before.space().monsters().size() >= 1024) throw new StorageException("CAPACITY", "Campaign monster capacity reached");
                CampaignJson.fields(input, "name", "monster"); if (current == null || !current.kind().equals("WORLD")) throw CampaignJson.bad("Create a monster at a world node before entering a scene");
                var rule = CampaignDomainCodec.fromJson(input.get("monster"), com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity.class);
                subject = operation; var deployment = new Schema2CombatState.MonsterDeployment(subject, rule, new Schema2CombatState.Position(0, 0), 0);
                var instance = new CampaignDomainEngine().initialMonster(before.material().restore().projection(), deployment);
                List<Monster> monsters = new ArrayList<>(before.space().monsters()); monsters.add(new Monster(subject, s(input, "name"), rule, instance.hitPoints(), instance.dead(), instance.actionUses(), 0));
                state = withSpace(before, ordered(before.space().focus(), before.space().scenes(), before.space().clocks(), monsters), replace(before.positions(), new Position(subject, current, null, 0, 0)));
            }
            default -> throw new StorageException("UNSUPPORTED_COMMAND", "Unsupported spatial command");
        }
        return new Result(state, subject, combatId);
    }

    private static CampaignDomainData.State elapsed(CampaignDomainData.State before, long minutes, UUID operation, IntUnaryOperator dice) {
        if (minutes == 0) return before;
        var advanced = advance(before.space(), Math.multiplyExact(minutes, 60));
        List<CampaignDomainEngine.CharacterRecord> members = focusedRecords(before), changed = members;
        if (!members.isEmpty()) {
            UUID target = members.getFirst().initial().characterId(); var rest = members.getFirst().history().current().rest();
            var command = new Schema2CharacterRuntime.Command("TIME_ADVANCED", null, 0, null, null, null, Schema2CharacterRuntime.GrowthChoices.EMPTY,
                    new Schema2CharacterRuntime.RestInput(null, rest.active() == null ? "NONE" : "LIGHT", minutes, 0, Map.of()));
            changed = new CampaignDomainEngine().executeSpaceCharacter(before.material().restore().projection(), members, target, command, operation, dice);
        }
        return new CampaignDomainData.State(before.material(), merge(before.characters(), changed), before.combats(), before.nodes(), before.positions(), before.items(), before.roots(), before.manual(), before.participation(), advanced);
    }
    public static CampaignDomainData.State sceneCommand(String campaign, CampaignDomainData.State before, UUID scene, UUID operation, Schema2CombatState.Command command, IntUnaryOperator dice) {
        var previous = before.combats().stream().filter(h -> h.current().combatId().equals(scene)).findFirst().orElse(null);
        var records = CampaignDomainValidation.records(before).stream().filter(r -> { Position p = position(before, r.initial().characterId()); return p != null && p.space().equals(Ref.scene(scene)); }).toList();
        var result = new CampaignDomainEngine().executeScene(before.material().restore().projection(), records, previous, UUID.fromString(campaign), scene, before.space().clock(Ref.scene(scene)).second(), operation, command, dice);
        return applyScene(before, scene, result);
    }
    private static CampaignDomainData.State sceneRoster(String campaign, CampaignDomainData.State before, CampaignDomainData.State after, UUID scene, UUID operation, IntUnaryOperator dice) {
        var previous = before.combats().stream().filter(h -> h.current().combatId().equals(scene)).findFirst().orElse(null);
        Set<UUID> union = new HashSet<>();
        for (Position p : before.positions()) if (p.space().equals(Ref.scene(scene))) union.add(p.characterId());
        for (Position p : after.positions()) if (p.space().equals(Ref.scene(scene))) union.add(p.characterId());
        var records = CampaignDomainValidation.records(after).stream().filter(r -> union.contains(r.initial().characterId())).toList();
        Set<UUID> rules = new HashSet<>(); after.characters().forEach(c -> rules.add(c.id()));
        var characters = after.positions().stream().filter(p -> p.space().equals(Ref.scene(scene)) && rules.contains(p.characterId())).map(p -> new Schema2CombatState.Deployment(p.characterId(), p.grid(), p.side())).toList();
        Map<UUID,Schema2CombatState.MonsterInstance> instances = new HashMap<>(); List<Schema2CombatState.MonsterDeployment> monsters = new ArrayList<>();
        for (Monster monster : after.space().monsters()) { Position p = position(after, monster.id()); if (p != null && p.space().equals(Ref.scene(scene))) {
            monsters.add(new Schema2CombatState.MonsterDeployment(monster.id(), monster.rule(), p.grid(), p.side()));
            if (previous == null || previous.current().monsters().stream().noneMatch(m -> m.monsterId().equals(monster.id()))) instances.put(monster.id(), new Schema2CombatState.MonsterInstance(monster.id(), monster.rule(), monster.hitPoints(), monster.dead(), monster.actions()));
        } }
        var command = previous == null ? Schema2CombatState.Command.openScene(characters, monsters) : Schema2CombatState.Command.sceneRoster(characters, monsters);
        var result = new CampaignDomainEngine().executeScene(after.material().restore().projection(), records, previous, UUID.fromString(campaign), scene, after.space().clock(Ref.scene(scene)).second(), operation, command, instances, dice);
        return applyScene(after, scene, result);
    }
    public static CampaignDomainData.State applyScene(CampaignDomainData.State before, UUID sceneId, CampaignDomainEngine.CombatResult result) {
        List<Schema2CombatHistory.History> combats = new ArrayList<>(before.combats()); combats.removeIf(h -> h.current().combatId().equals(sceneId)); combats.add(result.history()); combats.sort(Comparator.comparing(h -> h.current().combatId().toString()));
        List<Position> positions = new ArrayList<>(before.positions());
        for (var combatant : result.history().current().combatants()) {
            Position old = position(before, combatant.characterId()); if (old == null || !old.space().equals(Ref.scene(sceneId))) throw CampaignJson.bad("Scene result references an unplaced participant");
            if (!old.grid().equals(combatant.position()) || old.side() != combatant.side()) positions = replace(positions, new Position(old.characterId(), old.space(), combatant.position(), combatant.side(), old.version() + 1));
        }
        Scene oldScene = before.space().scene(sceneId); String mode = switch (result.history().current().status()) { case ACTIVE -> "COMBAT"; case EXPLORATION -> "EXPLORATION"; case ENDED -> "CLOSED"; };
        CampaignSpaceData.State space = before.space(); if (!oldScene.mode().equals(mode)) space = scene(space, new Scene(oldScene.id(), oldScene.name(), oldScene.nodeId(), oldScene.width(), oldScene.height(), oldScene.resolution(), mode, oldScene.version() + 1));
        if (result.elapsedSeconds() > 0) {
            Clock clock = space.clock(Ref.scene(sceneId)); List<Clock> clocks = new ArrayList<>(space.clocks()); clocks.remove(clock); clocks.add(new Clock(clock.space(), Math.addExact(clock.second(), result.elapsedSeconds()), clock.version() + 1));
            space = ordered(space.focus(), space.scenes(), clocks, space.monsters());
        }
        List<Monster> monsters = new ArrayList<>(space.monsters());
        for (var instance : result.history().current().monsters()) {
            Monster old = monsters.stream().filter(m -> m.id().equals(instance.monsterId())).findFirst().orElseThrow(() -> CampaignJson.bad("Unknown campaign monster"));
            if (old.hitPoints() != instance.hitPoints() || old.dead() != instance.dead() || !old.actions().equals(instance.actionUses())) { monsters.remove(old); monsters.add(new Monster(old.id(), old.name(), old.rule(), instance.hitPoints(), instance.dead(), instance.actionUses(), old.version() + 1)); }
        }
        space = ordered(space.focus(), space.scenes(), space.clocks(), monsters);
        return new CampaignDomainData.State(before.material(), merge(before.characters(), result.characters()), combats, before.nodes(), positions, before.items(), before.roots(), before.manual(), before.participation(), space);
    }
    public static List<Character> merge(List<Character> original, List<CampaignDomainEngine.CharacterRecord> changes) {
        return original.stream().map(c -> changes.stream().filter(r -> r.initial().characterId().equals(c.id())).findFirst().map(r -> new Character(c.name(), c.kind(), c.active(), c.initial(), r.history())).orElse(c)).toList();
    }
    private static List<Position> selected(CampaignDomainData.State state, JsonObject input, boolean grid) {
        JsonArray array = CampaignJson.array(input, "subjects"); if (array.isEmpty() || array.size() > 64) throw CampaignJson.bad("Expected a complete bounded subject selection");
        List<Position> selected = new ArrayList<>(); Set<UUID> ids = new HashSet<>();
        for (JsonElement element : array) { if (!element.isJsonObject()) throw CampaignJson.bad("Invalid subject selection"); JsonObject row = element.getAsJsonObject();
            if (grid) CampaignJson.fields(row, "id", "version", "x", "y", "side"); else CampaignJson.fields(row, "id", "version");
            UUID id = id(row, "id"); if (!ids.add(id)) throw CampaignJson.bad("Duplicate transfer subject"); selected.add(checkedPosition(state, id, CampaignJson.integer(row, "version"))); }
        return selected;
    }
    private static Position checkedPosition(CampaignDomainData.State state, UUID id, long version) {
        requirePlaced(state, id); Position old = position(state, id); if (old.version() != version) throw new StorageException("STALE_POSITION", "Refresh this subject's placement"); return old;
    }
    private static void transferable(CampaignDomainData.State state, List<Position> selected) {
        Set<UUID> ids = new HashSet<>(); selected.forEach(p -> ids.add(p.characterId()));
        for (Character c : state.characters()) if (ids.contains(c.id()) && c.history().current().rest().active() != null) throw new StorageException("SPACE_OBLIGATION_PENDING", "Finish the active rest before transferring");
        for (var history : state.combats()) {
            var battle = history.current(); if (battle.status() == Schema2CombatState.Status.ENDED) continue;
            boolean affected = battle.combatants().stream().anyMatch(c -> ids.contains(c.characterId())); if (!affected) continue;
            if (battle.status() == Schema2CombatState.Status.ACTIVE || battle.activeFlow() != null
                    || battle.ongoingEffects().stream().anyMatch(e -> e.status() == Schema2CombatState.EffectStatus.ACTIVE && (ids.contains(e.casterId()) || e.targets().stream().anyMatch(ids::contains)))
                    || battle.shields().stream().anyMatch(e -> e.status() == Schema2CombatState.EffectStatus.ACTIVE && ids.contains(e.actorId()))
                    || battle.monsterRests().stream().anyMatch(r -> ids.contains(r.monsterId()) && r.status() == Schema2CombatState.MonsterRestStatus.ACTIVE)
                    || battle.concentrationHolders().stream().anyMatch(h -> ids.contains(h.actorId())))
                throw new StorageException("SPACE_OBLIGATION_PENDING", "A scene action, turn, effect or rest prevents this transfer");
            for (var c : state.characters()) if (ids.contains(c.id()) && c.history().current().equipment().stream().anyMatch(e -> battle.combatId().equals(e.groundCombatId())))
                throw new StorageException("SPACE_OBLIGATION_PENDING", "Resolve scene-bound equipment before transferring");
        }
    }
    private static void requireIdleScene(CampaignDomainData.State state, UUID id) {
        var battle = state.combats().stream().filter(h -> h.current().combatId().equals(id)).findFirst().orElse(null);
        if (battle != null && (battle.current().activeFlow() != null || battle.current().status() == Schema2CombatState.Status.ACTIVE)) throw new StorageException("SPACE_OBLIGATION_PENDING", "The destination or source scene has a pending obligation");
    }
    private static Scene requireScene(CampaignDomainData.State state) { Ref ref = state.space().focus().space(); if (ref == null || !ref.kind().equals("SCENE")) throw CampaignJson.bad("A local scene must have focus"); return state.space().scene(ref.id()); }
    private static void requireWorld(CampaignDomainData.State state, UUID node) { if (!isWorldFocus(state) || !state.space().focus().space().id().equals(node)) throw new StorageException("SPACE_ANCHOR_MISMATCH", "The actual source must be the focused world node"); }
    private static void requireDirectClock(CampaignDomainData.State state) { if (state.space().focus().space() == null || !isWorldFocus(state) && !requireScene(state).resolution().equals("DM")) throw new StorageException("RULE_SCENE_OWNER_REQUIRED", "Use the focused rule scene's time command"); }
    public static void checkCell(Scene scene, Schema2CombatState.Position p) { if (p == null || p.x() < 0 || p.y() < 0 || p.x() >= scene.width() || p.y() >= scene.height()) throw new StorageException("SCENE_GRID_BOUNDS", "Cell lies outside the scene grid"); }
    public static boolean occupied(List<Position> positions, Ref space, Schema2CombatState.Position cell, UUID except) { return positions.stream().anyMatch(p -> p.space().equals(space) && !p.characterId().equals(except) && Objects.equals(p.grid(), cell)); }
    public static void uniqueCells(List<Position> positions) { Set<String> seen = new HashSet<>(); for (Position p : positions) if (p.grid() != null && !seen.add(p.space().id() + ":" + p.grid().x() + ":" + p.grid().y())) throw new StorageException("SCENE_CELL_OCCUPIED", "Two subjects cannot occupy the same cell"); }
    private static long nextPlacementVersion(CampaignDomainData.State state, UUID subject) {
        for (int i = state.roots().size() - 1; i >= 0; i--) {
            var audit = state.roots().get(i).space();
            var prior = java.util.stream.Stream.concat(audit.afterPositions().stream(), audit.beforePositions().stream())
                    .filter(p -> p.characterId().equals(subject)).mapToLong(Position::version).max();
            if (prior.isPresent()) return Math.addExact(prior.getAsLong(), 1);
        }
        return 0;
    }
    public static List<Position> replace(List<Position> original, Position replacement) { List<Position> positions = new ArrayList<>(original); positions.removeIf(p -> p.characterId().equals(replacement.characterId())); positions.add(replacement); positions.sort(Comparator.comparing(p -> p.characterId().toString())); return positions; }
    public static Ref readRef(JsonObject input) { CampaignJson.fields(input, "kind", "id"); return new Ref(s(input, "kind"), id(input, "id")); }
    private static long minutes(JsonObject input) { long value = CampaignJson.integer(input, "minutes"); if (value > 1440) throw CampaignJson.bad("DM time exceeds one day's bounded command"); return value; }
    private static void reason(JsonObject input) { String value = s(input, "adjudication"); CampaignJson.validString(value); if (value.isBlank() || value.codePointCount(0, value.length()) > 500 || !java.text.Normalizer.isNormalized(value, java.text.Normalizer.Form.NFC)) throw CampaignJson.bad("DM adjudication needs 1–500 code points"); }
    private static String s(JsonObject input, String key) { return CampaignJson.string(input, key); }
    private static UUID id(JsonObject input, String key) { return UUID.fromString(CampaignJson.uuid(s(input, key))); }
    private static int integer(JsonObject input, String key) { return Math.toIntExact(CampaignJson.integer(input, key)); }
}
