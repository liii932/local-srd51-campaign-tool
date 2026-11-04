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
    public static String action(Request request) {
        return switch (request) { case MemberAdd ignored -> "member-add"; case MemberUpdate ignored -> "member-update"; case ControlSet ignored -> "control-set"; case ShareSet ignored -> "share-set"; case ExplorationSet ignored -> "exploration-set"; };
    }
    public static Result resolve(CampaignDomainData.State before, String action, JsonObject input, UUID operation, UUID campaign) {
        Request request = parse(action, input);
        return transition(before.participation(), request, operation, campaign, profiles(before), before.manual(), before.combats().stream().map(c -> c.current()).toList(), before.nodes(), before.roots());
    }
    public static State validateAudit(CampaignDomainData.Root root, Audit audit, State current, UUID campaign,
            Map<UUID,CampaignDomainData.Profile> profiles, CampaignManualData.State manual, List<Schema2CombatState.Battle> battles,
            Collection<CampaignDomainData.Node> nodes, List<CampaignDomainData.Root> roots) {
        if (!root.kind().equals(action(audit.request())) || !root.before().isEmpty() || !audit.before().equals(current)) throw CampaignJson.bad("Participation audit adjacency differs");
        Result expected = transition(current, audit.request(), root.operationId(), campaign, profiles, manual, battles, nodes, roots);
        if (!expected.subjectId().equals(root.subjectId()) || !expected.audit().equals(audit)) throw CampaignJson.bad("Participation audit does not match its typed transition");
        return expected.state();
    }
    private static Result transition(State previous, Request request, UUID operation, UUID campaign,
            Map<UUID,CampaignDomainData.Profile> profiles, CampaignManualData.State manual, List<Schema2CombatState.Battle> battles,
            Collection<CampaignDomainData.Node> nodes, List<CampaignDomainData.Root> roots) {
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
                    case SCENE -> { if (battles.stream().noneMatch(b -> b.combatId().equals(r.subjectId())) && nodes.stream().noneMatch(n -> n.id().equals(r.subjectId())) && manual.encounters().stream().noneMatch(e -> e.id().equals(r.subjectId()))) throw CampaignJson.bad("Unknown scene share target"); }
                    case LOG -> { if (!campaign.equals(r.subjectId()) && roots.stream().noneMatch(root -> root.operationId().equals(r.subjectId()))) throw CampaignJson.bad("Unknown log share target"); }
                }
                subject = old == null ? operation : old.id(); shares.remove(old); shares.add(new Share(subject, r.kind(), r.subjectId(), r.audience(), r.active(), Math.addExact(r.shareVersion(), 1)));
            }
            case ExplorationSet r -> {
                CampaignDomainData.Profile pc = profile(profiles, manual, r.characterId());
                if (!pc.active() || !pc.kind().equals("PC")) throw CampaignJson.bad("Exploration permission requires an active PC");
                if (r.sceneId() != null && battles.stream().noneMatch(b -> b.combatId().equals(r.sceneId()) && b.status() == Schema2CombatState.Status.EXPLORATION)
                        && nodes.stream().noneMatch(n -> n.id().equals(r.sceneId()))) throw CampaignJson.bad("Unknown current exploration scope");
                Exploration old = exploration.stream().filter(e -> e.characterId().equals(r.characterId())).findFirst().orElse(null);
                version(old == null ? 0 : old.version(), r.permissionVersion()); subject = r.characterId();
                exploration.remove(old); exploration.add(new Exploration(subject, r.sceneId(), r.actions(), r.active(), Math.addExact(r.permissionVersion(), 1)));
            }
        }
        members.sort(Comparator.comparing(m -> m.id().toString())); controls.sort(Comparator.comparing(c -> c.characterId().toString()));
        shares.sort(Comparator.comparing(s -> s.id().toString())); exploration.sort(Comparator.comparing(e -> e.characterId().toString()));
        State after = new State(members, controls, shares, exploration); return new Result(after, subject, new Audit(request, previous, after));
    }

    public static State afterSceneChange(List<Schema2CombatState.Battle> before, List<Schema2CombatState.Battle> after,
            CampaignManualData.State manualBefore, CampaignManualData.State manualAfter, State participation) {
        Map<UUID,Schema2CombatState.Status> old = new HashMap<>(); before.forEach(b -> old.put(b.combatId(), b.status()));
        boolean changed = !Objects.equals(manualBefore.partyNodeId(), manualAfter.partyNodeId()) || after.stream().anyMatch(b -> old.get(b.combatId()) != b.status())
                || !manualBefore.encounters().stream().map(e -> e.id() + ":" + e.status()).toList().equals(manualAfter.encounters().stream().map(e -> e.id() + ":" + e.status()).toList());
        if (!changed) return participation;
        Set<UUID> ids = new HashSet<>(); participation.exploration().forEach(e -> ids.add(e.characterId()));
        return new State(participation.members(), participation.controls(), participation.shares(), invalidate(participation.exploration(), ids));
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
    public static UUID historicalCharacter(CampaignDomainData.State state, CampaignDomainData.Root root) {
        if (supports(root.kind()) || root.kind().equals("character-create") || root.kind().equals("character-profile") || root.kind().equals("manual-create") || root.kind().equals("manual-profile")) return null;
        if (root.kind().equals("adjudicated-check")) return CampaignDomainCodec.decode(root.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), CampaignAdjudicationData.Audit.class).decision().check().characterId();
        if (root.kind().equals("combat-command")) return state.combats().stream().flatMap(c -> c.events().stream()).filter(e -> e.operationId().equals(root.operationId())).findFirst().orElseThrow(() -> CampaignJson.bad("Missing scene actor event")).command().actorId();
        if (root.kind().equals("character-command") || root.kind().equals("position")) return root.subjectId();
        if (root.kind().equals("item")) return CampaignDomainCodec.decode(root.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), CampaignDomainData.Item.class).characterId();
        if (root.kind().equals("check-effects")) return CampaignDomainCodec.decode(root.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), CampaignCheckEffects.Audit.class).input().characterId();
        if (root.kind().startsWith("manual-")) {
            var request = CampaignDomainCodec.decode(root.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), CampaignManualCommands.Audit.class).request();
            return switch (request) {
                case CampaignManualCommands.FieldEdit r -> r.characterId(); case CampaignManualCommands.ClassEdit r -> r.characterId();
                case CampaignManualCommands.ProficiencyEdit r -> r.characterId(); case CampaignManualCommands.ItemEdit r -> r.characterId();
                case CampaignManualCommands.Check r -> r.characterId(); default -> null;
            };
        }
        return null;
    }
    public static void validateHistoricalActor(CampaignDomainData.State state, CampaignDomainData.Root root, State participation,
            Map<UUID,CampaignDomainData.Profile> profiles, CampaignManualData.State manual) {
        if (!Objects.equals(historicalCharacter(state, root), root.actor().characterId())) throw CampaignJson.bad("Historical command actor differs from typed command");
        validateActor(participation, root.actor());
        if (root.actor().kind() == ActorKind.PLAYER) {
            if (!Set.of("character-command", "combat-command").contains(root.kind())) throw CampaignJson.bad("Player history contains a DM management operation");
            var pc = profile(profiles, manual, root.actor().characterId());
            if (!pc.active() || !pc.kind().equals("PC")) throw CampaignJson.bad("Historical player actor is not an active PC");
        }
    }
    public static Map<UUID,CampaignDomainData.Profile> profiles(CampaignDomainData.State state) {
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
