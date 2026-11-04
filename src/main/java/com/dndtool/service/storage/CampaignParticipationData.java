package com.dndtool.service.storage;

import java.util.*;

/** Portable campaign participation facts; credentials and run authorizations have no fields here. */
public final class CampaignParticipationData {
    private CampaignParticipationData() { }
    public enum ActorKind { DM, PLAYER }
    public enum ShareKind { CHARACTER, SCENE, LOG }
    public enum AudienceKind { ALL, SPECIFIC }
    public record Actor(ActorKind kind, UUID memberId, UUID representedMemberId, UUID characterId) {
        public Actor {
            if (kind == null || kind == ActorKind.DM && memberId != null || kind == ActorKind.PLAYER && (memberId == null || characterId == null || representedMemberId != null)) throw CampaignJson.bad("Invalid actual command actor");
        }
        public static Actor player(UUID member, UUID character) { return new Actor(ActorKind.PLAYER, member, null, character); }
        public static Actor dm(UUID character, UUID represented) { return new Actor(ActorKind.DM, null, represented, character); }
    }
    public record Member(UUID id, String name, boolean active, long version) {
        public Member { uuid(id); name = com.dndtool.service.CharacterNamePolicy.normalize(name); positive(version); }
    }
    public record Control(UUID characterId, UUID memberId, long version) {
        public Control { uuid(characterId); if (memberId != null) uuid(memberId); positive(version); }
    }
    public record Audience(AudienceKind kind, List<UUID> memberIds) {
        public Audience {
            if (kind == null || memberIds == null) throw CampaignJson.bad("Missing share audience");
            memberIds = List.copyOf(memberIds);
            if (memberIds.size() > 64 || new HashSet<>(memberIds).size() != memberIds.size() || !memberIds.equals(memberIds.stream().sorted(Comparator.comparing(UUID::toString)).toList())
                    || kind == AudienceKind.ALL && !memberIds.isEmpty() || kind == AudienceKind.SPECIFIC && memberIds.isEmpty()) throw CampaignJson.bad("Invalid bounded share audience");
        }
        public boolean includes(UUID member) { return kind == AudienceKind.ALL || memberIds.contains(member); }
    }
    public record Share(UUID id, ShareKind kind, UUID subjectId, Audience audience, boolean active, long version) {
        public Share { uuid(id); uuid(subjectId); if (kind == null || audience == null) throw CampaignJson.bad("Missing typed share"); positive(version); }
    }
    public record Exploration(UUID characterId, UUID sceneId, Set<String> actions, boolean active, long version) {
        public Exploration {
            uuid(characterId); if (sceneId != null) uuid(sceneId); actions = Collections.unmodifiableSet(new TreeSet<>(actions)); positive(version);
            if (actions.isEmpty() || !Set.of("CHARACTER_COMMAND", "EXPLORATION_MOVE", "END_CONCENTRATION").containsAll(actions)) throw CampaignJson.bad("Unsupported exploration permission");
        }
    }
    public record State(List<Member> members, List<Control> controls, List<Share> shares, List<Exploration> exploration) {
        public State {
            members = List.copyOf(members); controls = List.copyOf(controls); shares = List.copyOf(shares); exploration = List.copyOf(exploration);
            if (members.size() > 64 || controls.size() > 10016 || shares.size() > 1024 || exploration.size() > 10016) throw CampaignJson.bad("Participation capacity exceeded");
            unique(members.stream().map(Member::id).toList()); unique(controls.stream().map(Control::characterId).toList());
            unique(shares.stream().map(Share::id).toList()); unique(exploration.stream().map(Exploration::characterId).toList());
        }
        public static State empty() { return new State(List.of(), List.of(), List.of(), List.of()); }
        public Member member(UUID id) { return members.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow(() -> new StorageException("MEMBER_NOT_FOUND", "Campaign member not found")); }
        public Control control(UUID character) { return controls.stream().filter(c -> c.characterId().equals(character)).findFirst().orElse(null); }
        public UUID controller(UUID character) { Control c = control(character); return c == null ? null : c.memberId(); }
    }
    private static void unique(List<UUID> ids) { if (new HashSet<>(ids).size() != ids.size()) throw CampaignJson.bad("Duplicate participation identity"); }
    private static void uuid(UUID value) { if (value == null) throw CampaignJson.bad("Missing participation identity"); CampaignJson.uuid(value.toString()); }
    private static void positive(long value) { if (value < 1) throw CampaignJson.bad("Invalid participation version"); }
}
