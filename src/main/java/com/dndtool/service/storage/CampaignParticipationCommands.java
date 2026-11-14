package com.dndtool.service.storage;

import com.dndtool.service.Schema2CombatState;
import com.google.gson.*;
import java.util.*;
import static com.dndtool.service.storage.CampaignParticipationData.*;

/** DM-owned, bounded participation mutations. History uses the same transition validator as execution. */
public final class CampaignParticipationCommands {
    private CampaignParticipationCommands() { }
    public sealed interface Request permits MemberAdd, MemberUpdate, ControlSet, ShareSet, ExplorationSet { }
    public record MemberAdd(String name) implements Request { }
    public record MemberUpdate(UUID memberId, long memberVersion, String name, boolean active) implements Request { }
    public record ControlSet(UUID characterId, long controlVersion, UUID memberId) implements Request { }
    public record ShareSet(UUID shareId, long shareVersion, ShareKind kind, UUID subjectId, Audience audience, boolean active) implements Request { }
    public record ExplorationSet(UUID characterId, long permissionVersion, UUID sceneId, Set<String> actions, boolean active) implements Request { public ExplorationSet { actions = Collections.unmodifiableSet(new TreeSet<>(actions)); } }
    public record Audit(Request request, State before, State after) { public Audit { if (request == null || before == null || after == null) throw CampaignJson.bad("Incomplete participation audit"); } }
    public record Result(State state, UUID subjectId, Audit audit) { }
    public static boolean supports(String action) { return Set.of("member-add", "member-update", "control-set", "share-set", "exploration-set").contains(action); }
    public static Result resolve(CampaignDomainData.State before, String action, JsonObject input, UUID operation, UUID campaign) {
        Request request = parse(action, input);
        return transition(before.participation(), request, operation, campaign, profiles(before), before.manual(),
                before.combats().stream().map(c -> c.current()).toList(), before.nodes(), before.roots(), before.space(), before.positions());
    }
    private static Result transition(State previous, Request request, UUID operation, UUID campaign,
            Map<UUID,CampaignDomainData.Profile> profiles, CampaignManualData.State manual, List<Schema2CombatState.Battle> battles,
            Collection<CampaignDomainData.Node> nodes, List<CampaignDomainData.Root> roots, CampaignSpaceData.State space,
            Collection<CampaignDomainData.Position> positions) {
        List<Member> members = new ArrayList<>(previous.members()); List<Control> controls = new ArrayList<>(previous.controls());
        List<Share> shares = new ArrayList<>(previous.shares()); List<Exploration> exploration = new ArrayList<>(previous.exploration()); UUID subject;
        switch (request) {
            case MemberAdd r -> {
                if (members.stream().anyMatch(m -> m.id().equals(operation))) throw CampaignJson.bad("Member key cannot be reused");
                subject = operation; members.add(new Member(subject, r.name(), true, 1));
            }
            case MemberUpdate r -> {
                Member old = previous.member(r.memberId()); version(old.version(), r.memberVersion()); subject = old.id();
                if (old.active() && !r.active()) {
                    Set<UUID> affected = new HashSet<>(); controls.stream().filter(c -> old.id().equals(c.memberId())).forEach(c -> affected.add(c.characterId()));
                    exploration = invalidate(exploration, affected);
                }
                members.remove(old); members.add(new Member(old.id(), r.name(), r.active(), Math.addExact(old.version(), 1)));
            }
            case ControlSet r -> {
                CampaignDomainData.Profile pc = profile(profiles, manual, r.characterId());
                if (!pc.active() || !pc.kind().equals("PC")) throw new StorageException("CONTROL_PC_REQUIRED", "Only an active PC may be assigned");
                if (r.memberId() != null && !previous.member(r.memberId()).active()) throw new StorageException("MEMBER_INACTIVE", "Reactivate this member before assigning control");
                Control old = previous.control(r.characterId()); version(old == null ? 0 : old.version(), r.controlVersion());
                if (battles.stream().anyMatch(b -> b.status() != Schema2CombatState.Status.ENDED && b.activeFlow() != null && b.combatants().stream().anyMatch(c -> c.characterId().equals(r.characterId())))) throw new StorageException("CONTROL_FLOW_PENDING", "Resolve the affected action and reaction window before transferring control");
                if (r.memberId() != null && controls.stream().filter(c -> r.memberId().equals(c.memberId()) && !c.characterId().equals(r.characterId())).count() >= 32) throw new StorageException("CONTROL_CAPACITY", "A member may control at most 32 PCs");
                exploration = invalidate(exploration, Set.of(r.characterId()));
                subject = r.characterId(); controls.remove(old); controls.add(new Control(subject, r.memberId(), Math.addExact(r.controlVersion(), 1)));
            }
            case ShareSet r -> {
                Share old = r.shareId() == null ? null : shares.stream().filter(s -> s.id().equals(r.shareId())).findFirst().orElseThrow(() -> CampaignJson.bad("Unknown share policy"));
                version(old == null ? 0 : old.version(), r.shareVersion());
                for (UUID member : r.audience().memberIds()) previous.member(member);
                switch (r.kind()) {
                    case CHARACTER -> profile(profiles, manual, r.subjectId());
                    case SCENE -> { if (space.scenes().stream().noneMatch(s -> s.id().equals(r.subjectId())) && nodes.stream().noneMatch(n -> n.id().equals(r.subjectId()))) throw CampaignJson.bad("Unknown scene share target"); }
                    case LOG -> { if (!campaign.equals(r.subjectId()) && roots.stream().noneMatch(root -> root.operationId().equals(r.subjectId()))) throw CampaignJson.bad("Unknown log share target"); }
                }
                subject = old == null ? operation : old.id(); shares.remove(old); shares.add(new Share(subject, r.kind(), r.subjectId(), r.audience(), r.active(), Math.addExact(r.shareVersion(), 1)));
            }
            case ExplorationSet r -> {
                CampaignDomainData.Profile pc = profile(profiles, manual, r.characterId());
                Exploration old = exploration.stream().filter(e -> e.characterId().equals(r.characterId())).findFirst().orElse(null);
                version(old == null ? 0 : old.version(), r.permissionVersion()); subject = r.characterId();
                if (r.active()) {
                    if (!pc.active() || !pc.kind().equals("PC") || !profiles.containsKey(r.characterId()))
                        throw CampaignJson.bad("Exploration permission requires an active rule-managed PC");
                    var place = positions.stream().filter(p -> p.characterId().equals(r.characterId())).findFirst().orElseThrow(() -> CampaignJson.bad("Exploration permission requires a placed PC"));
                    if (!place.space().id().equals(r.sceneId())) throw CampaignJson.bad("Exploration permission must match the PC's current space");
                    if (place.space().kind().equals("WORLD") ? !r.actions().equals(Set.of("CHARACTER_COMMAND"))
                            : space.scenes().stream().noneMatch(s -> s.id().equals(r.sceneId()) && s.mode().equals("EXPLORATION") && s.resolution().equals("RULES")))
                        throw CampaignJson.bad("Unsupported exploration permission for this space");
                } else if (old == null || !Objects.equals(old.sceneId(), r.sceneId()) || !old.actions().equals(r.actions())) {
                    throw CampaignJson.bad("Revoke the exact existing exploration permission");
                }
                exploration.remove(old); exploration.add(new Exploration(subject, r.sceneId(), r.actions(), r.active(), Math.addExact(r.permissionVersion(), 1)));
            }
        }
        members.sort(Comparator.comparing(m -> m.id().toString())); controls.sort(Comparator.comparing(c -> c.characterId().toString()));
        shares.sort(Comparator.comparing(s -> s.id().toString())); exploration.sort(Comparator.comparing(e -> e.characterId().toString()));
        State after = new State(members, controls, shares, exploration); return new Result(after, subject, new Audit(request, previous, after));
    }

    public static State afterSpaceChange(CampaignSpaceData.State before, CampaignSpaceData.State after,
            Collection<CampaignDomainData.Position> positionsBefore, Collection<CampaignDomainData.Position> positionsAfter, State participation) {
        if (participation.exploration().isEmpty()) return participation;
        Map<UUID, CampaignSpaceData.Ref> oldPositions = new HashMap<>(), newPositions = new HashMap<>();
        positionsBefore.forEach(p -> oldPositions.put(p.characterId(), p.space()));
        positionsAfter.forEach(p -> newPositions.put(p.characterId(), p.space()));
        Map<UUID, String> oldModes = new HashMap<>(), newModes = new HashMap<>();
        before.scenes().forEach(s -> oldModes.put(s.id(), s.mode()));
        after.scenes().forEach(s -> newModes.put(s.id(), s.mode()));
        Set<UUID> ids = new HashSet<>();
        for (Exploration permit : participation.exploration()) {
            if (!Objects.equals(oldPositions.get(permit.characterId()), newPositions.get(permit.characterId()))
                    || !Objects.equals(oldModes.get(permit.sceneId()), newModes.get(permit.sceneId()))) ids.add(permit.characterId());
        }
        if (ids.isEmpty()) return participation;
        return new State(participation.members(), participation.controls(), participation.shares(), invalidate(participation.exploration(), ids));
    }
    static boolean permitted(CampaignDomainData.State state, UUID character, String action) {
        var focus = state.space().focus().space();
        return focus != null && CampaignSpaceCommands.focused(state, character)
                && state.participation().exploration().stream().anyMatch(p -> p.characterId().equals(character)
                        && p.active() && focus.id().equals(p.sceneId()) && p.actions().contains(action));
    }
    public static void requireExecutionPermission(CampaignDomainData.State state, String action, JsonObject input, Actor actor) {
        if (actor.kind() != ActorKind.PLAYER) return;
        UUID character = actor.characterId();
        if (!CampaignSpaceCommands.focused(state, character)) throw playerPermissionDenied();
        if (action.equals("character-command")) {
            var command = CampaignDomainCodec.fromJson(input.get("command"), com.dndtool.service.Schema2CharacterRuntime.Command.class);
            if (!CampaignSpaceCommands.isWorldFocus(state) || !permitted(state, character, "CHARACTER_COMMAND")
                    || !Set.of("ITEM_EQUIPPED", "ITEM_UNEQUIPPED", "RESOURCE_SPENT").contains(command.kind()))
                throw playerPermissionDenied();
            return;
        }
        var current = CampaignSpaceCommands.currentBattle(state);
        if (!action.equals("combat-command") || current == null) throw playerPermissionDenied();
        var command = CampaignDomainCodec.fromJson(input.get("command"), Schema2CombatState.Command.class);
        var battle = current.current();
        if (battle.activeFlow() != null) return; // Scene preflight accepts only the entitled current leaf.
        if (battle.status() == Schema2CombatState.Status.EXPLORATION) {
            String required = switch (command.kind()) {
                case CAST -> "CHARACTER_COMMAND";
                case DROP_PRONE, EXPLORATION_MOVE, STAND_UP -> "EXPLORATION_MOVE";
                case END_CONCENTRATION -> "END_CONCENTRATION";
                default -> throw playerPermissionDenied();
            };
            if (!permitted(state, character, required)) throw playerPermissionDenied();
        } else if (!Set.of(Schema2CombatState.Kind.END_TURN, Schema2CombatState.Kind.BEGIN_ATTACK,
                Schema2CombatState.Kind.FORFEIT_ATTACKS, Schema2CombatState.Kind.ATTACK, Schema2CombatState.Kind.CAST,
                Schema2CombatState.Kind.MOVE, Schema2CombatState.Kind.DASH, Schema2CombatState.Kind.DISENGAGE,
                Schema2CombatState.Kind.DODGE, Schema2CombatState.Kind.DROP_PRONE, Schema2CombatState.Kind.STAND_UP,
                Schema2CombatState.Kind.SECOND_WIND, Schema2CombatState.Kind.END_CONCENTRATION).contains(command.kind()))
            throw playerPermissionDenied();
    }
    private static StorageException playerPermissionDenied() {
        return new StorageException("PLAYER_COMMAND_FORBIDDEN", "This action is outside the player's current execution permission");
    }
    private static List<Exploration> invalidate(List<Exploration> permissions, Set<UUID> characters) {
        return new ArrayList<>(permissions.stream().map(e -> e.active() && characters.contains(e.characterId()) ? new Exploration(e.characterId(), e.sceneId(), e.actions(), false, Math.addExact(e.version(), 1)) : e).toList());
    }
    public static void validateCurrent(CampaignDomainData.State state) {
        validateCurrent(profiles(state), state.manual(), state.participation());
    }
    public static void validateCurrent(Map<UUID,CampaignDomainData.Profile> profiles, CampaignManualData.State manual, State participation) {
        for (Control control : participation.controls()) {
            var pc = profile(profiles, manual, control.characterId());
            if (control.memberId() != null) {
                participation.member(control.memberId());
                if (!pc.active() || !pc.kind().equals("PC")) throw new StorageException("CONTROL_PC_REQUIRED", "Release player control before changing this PC profile");
            }
        }
    }
    public static UUID characterFor(String action, JsonObject input) {
        if (supports(action) || action.endsWith("create") || action.equals("character-profile") || action.equals("manual-profile")) return null;
        if (action.equals("adjudicated-check")) return id(CampaignJson.object(input,"check"), "characterId");
        if (action.equals("combat-command")) return CampaignDomainCodec.fromJson(input.get("command"), Schema2CombatState.Command.class).actorId();
        if (input.has("characterId") && !input.get("characterId").isJsonNull()) return id(input, "characterId");
        return null;
    }
    public static Actor actualActor(CampaignDomainData.State before, String action, JsonObject input) {
        UUID character = characterFor(action, input); return Actor.dm(character, character == null ? null : before.participation().controller(character));
    }
    public static void validateActor(CampaignDomainData.State before, String action, JsonObject input, Actor actor) {
        UUID character = characterFor(action, input);
        if (!Objects.equals(character, actor.characterId())) throw new StorageException("ACTOR_MISMATCH", "Actual acting character differs from command");
        validateActor(before.participation(), actor);
        if (actor.kind() == ActorKind.PLAYER && !Set.of("character-command", "combat-command").contains(action)) throw new StorageException("PLAYER_COMMAND_FORBIDDEN", "This operation requires the DM");
    }
    public static void validateActor(State participation, Actor actor) {
        UUID controller = actor.characterId() == null ? null : participation.controller(actor.characterId());
        if (actor.kind() == ActorKind.DM) { if (!Objects.equals(controller, actor.representedMemberId())) throw CampaignJson.bad("DM represented member differs from historical control"); }
        else if (!participation.member(actor.memberId()).active() || !Objects.equals(controller, actor.memberId())) throw new StorageException("PLAYER_CONTROL_REQUIRED", "This member does not currently control the acting PC");
    }
    private static Map<UUID,CampaignDomainData.Profile> profiles(CampaignDomainData.State state) {
        Map<UUID,CampaignDomainData.Profile> values = new HashMap<>(); state.characters().forEach(c -> values.put(c.id(), new CampaignDomainData.Profile(c.id(), c.name(), c.kind(), c.active()))); return values;
    }
    private static CampaignDomainData.Profile profile(Map<UUID,CampaignDomainData.Profile> profiles, CampaignManualData.State manual, UUID id) {
        var profile = profiles.get(id); if (profile != null) return profile;
        var card = manual.characters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(() -> CampaignJson.bad("Unknown participation character")).current();
        return new CampaignDomainData.Profile(card.id(), card.name(), card.kind(), card.active());
    }
    private static void version(long actual, long expected) { if (expected < 0 || actual != expected) throw new StorageException("STALE_PARTICIPATION", "Refresh participation settings"); }
    private static Request parse(String action, JsonObject input) {
        return switch (action) {
            case "member-add" -> { CampaignJson.fields(input, "name"); yield new MemberAdd(com.dndtool.service.CharacterNamePolicy.normalize(CampaignJson.string(input, "name"))); }
            case "member-update" -> { CampaignJson.fields(input, "memberId", "memberVersion", "name", "active"); yield new MemberUpdate(id(input,"memberId"), CampaignJson.integer(input,"memberVersion"), com.dndtool.service.CharacterNamePolicy.normalize(CampaignJson.string(input,"name")), bit(input,"active")); }
            case "control-set" -> { CampaignJson.fields(input,"characterId","controlVersion","memberId"); yield new ControlSet(id(input,"characterId"),CampaignJson.integer(input,"controlVersion"),nullableId(input,"memberId")); }
            case "share-set" -> { CampaignJson.fields(input,"shareId","shareVersion","kind","subjectId","audience","active"); yield new ShareSet(nullableId(input,"shareId"),CampaignJson.integer(input,"shareVersion"),ShareKind.valueOf(CampaignJson.string(input,"kind")),id(input,"subjectId"),CampaignDomainCodec.fromJson(input.get("audience"),Audience.class),bit(input,"active")); }
            case "exploration-set" -> { CampaignJson.fields(input,"characterId","permissionVersion","sceneId","actions","active"); Set<String> actions = new HashSet<>(); for(JsonElement item : CampaignJson.array(input,"actions")) { if(!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString() || !actions.add(item.getAsString())) throw CampaignJson.bad("Invalid exploration actions"); } yield new ExplorationSet(id(input,"characterId"),CampaignJson.integer(input,"permissionVersion"),nullableId(input,"sceneId"),actions,bit(input,"active")); }
            default -> throw CampaignJson.bad("Unknown participation command");
        };
    }
    private static UUID id(JsonObject input,String key) { return UUID.fromString(CampaignJson.uuid(CampaignJson.string(input,key))); }
    private static UUID nullableId(JsonObject input,String key) { return input.get(key).isJsonNull() ? null : id(input,key); }
    private static boolean bit(JsonObject input,String key) { if(!input.get(key).isJsonPrimitive() || !input.getAsJsonPrimitive(key).isBoolean()) throw CampaignJson.bad("Expected participation boolean"); return input.get(key).getAsBoolean(); }
}
