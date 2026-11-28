package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CombatState.MonsterActionUse;
import java.util.*;

/** Spatial authority. Character elapsed time is personal; each space owns its own paused clock. */
public final class CampaignSpaceData {
    private CampaignSpaceData() { }
    public static final long MAX_SECOND = 60_000_000_000L;
    public record Ref(String kind, UUID id) {
        public Ref { if (!Set.of("WORLD", "SCENE").contains(kind)) throw CampaignJson.bad("Unknown space kind"); uuid(id); }
        public static Ref world(UUID id) { return new Ref("WORLD", id); }
        public static Ref scene(UUID id) { return new Ref("SCENE", id); }
    }
    public record Focus(Ref space, long generation) {
        public Focus { if (generation < 0) throw CampaignJson.bad("Invalid focus generation"); }
    }
    public record ScenePlacement(long placementVersion, long x, long y) {
        public ScenePlacement {
            if (placementVersion < 0) throw CampaignJson.bad("Invalid expected placement version");
        }
    }
    public record Scene(UUID id, String name, UUID nodeId, int width, int height,
                        String resolution, String mode, long version) {
        static final int MAX_DIMENSION = 201;

        public Scene {
            uuid(id); uuid(nodeId); CampaignJson.name(name);
            if (width < 1 || width > MAX_DIMENSION || height < 1 || height > MAX_DIMENSION || version < 0
                    || !Set.of("DM", "RULES").contains(resolution)
                    || !Set.of("EXPLORATION", "COMBAT", "CLOSED").contains(mode)) throw CampaignJson.bad("Invalid local scene");
        }
    }
    public record Clock(Ref space, long second, long version) {
        public Clock { if (space == null || second < 0 || second > MAX_SECOND || version < 0) throw CampaignJson.bad("Invalid space clock"); }
    }
    /** Health, action uses and posture survive scene transfers. Position owns the grid cell. */
    public record Monster(UUID id, String name, RuleIdentity rule, int hitPoints, boolean dead, boolean prone,
                          List<MonsterActionUse> actions, long version) {
        public Monster {
            uuid(id); CampaignJson.name(name); Objects.requireNonNull(rule); actions = List.copyOf(actions);
            if (hitPoints < 0 || version < 0 || dead != (hitPoints == 0)) throw CampaignJson.bad("Invalid persistent monster");
        }
    }
    public record State(Focus focus, List<Scene> scenes, List<Clock> clocks, List<Monster> monsters) {
        public State {
            Objects.requireNonNull(focus); scenes = List.copyOf(scenes); clocks = List.copyOf(clocks); monsters = List.copyOf(monsters);
        }
        public static State empty() { return new State(new Focus(null, 0), List.of(), List.of(), List.of()); }
        public Scene scene(UUID id) { return scenes.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow(() -> CampaignJson.bad("Unknown scene")); }
        public Clock clock(Ref ref) { return clocks.stream().filter(c -> c.space().equals(ref)).findFirst().orElseThrow(() -> CampaignJson.bad("Unknown space clock")); }
    }
    /** Complete spatial prefix proof is retained with the root; it never contains executable callbacks. */
    public record Audit(State before, State after, List<CampaignDomainData.Position> beforePositions,
                        List<CampaignDomainData.Position> afterPositions, String request) {
        public Audit {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            beforePositions = List.copyOf(beforePositions); afterPositions = List.copyOf(afterPositions);
            CampaignJson.validString(request);
        }
    }
    /** Derived deadline projection; the health owner retains the single personal deadline fact. */
    public record Deadline(UUID subjectId, Ref space, long personalDeadline, long spaceDeadline) { }
    public record Die(int sides, int value) {
        public Die { if (sides < 2 || sides > 1000 || value < 1 || value > sides) throw CampaignJson.bad("Invalid retained root die"); }
    }
    private static void uuid(UUID id) { if (id == null) throw CampaignJson.bad("Missing spatial identity"); CampaignJson.uuid(id.toString()); }
}
