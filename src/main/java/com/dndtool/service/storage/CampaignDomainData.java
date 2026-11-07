package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CombatHistory;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import java.util.*;

/** Complete logical campaign aggregates; physical run IDs and HTTP receipts are kept by core_control. */
public final class CampaignDomainData {
    private CampaignDomainData() { }
    public record Material(String interpretation, Schema2RulesBinding binding, List<FinalRule> rules,
                           List<FinalEntry> entries, List<FieldChain> chains) {
        private static final int MAX_RESTORED_CONTEXTS = 2;
        private static final LinkedHashMap<Material, CampaignRuleContext> RECENT =
                new LinkedHashMap<>(MAX_RESTORED_CONTEXTS, 1f, true);

        public Material {
            if (!CampaignRuleContext.INTERPRETATION.equals(interpretation) || binding == null
                    || rules == null || entries == null || chains == null)
                throw CampaignJson.bad("Unsupported campaign rule interpretation or missing exact material");
            rules = List.copyOf(rules);
            entries = List.copyOf(entries);
            chains = List.copyOf(chains);
        }
        public static Material of(ProductionSchema2Projection projection) {
            return new Material(CampaignRuleContext.INTERPRETATION, Schema2RulesBinding.from(projection),
                    projection.projection().rules(), projection.projection().entries(), projection.projection().chains());
        }
        public CampaignRuleContext restore() {
            // The complete immutable rows and chains are part of the cache key.
            synchronized (RECENT) {
                var cached = RECENT.get(this);
                if (cached != null) return cached;
            }
            var verified = restoreFrozen();
            synchronized (RECENT) {
                var retained = RECENT.computeIfAbsent(this, ignored -> verified);
                if (RECENT.size() > MAX_RESTORED_CONTEXTS) RECENT.pollFirstEntry();
                return retained;
            }
        }
        private CampaignRuleContext restoreFrozen() {
            try {
                var restored = ProductionSchema2Projection.restore(binding.members(), rules, entries, chains);
                binding.verify(restored);
                return CampaignRuleSupport.validate(restored);
            } catch (IllegalArgumentException | NullPointerException failure) {
                throw new StorageException("INVALID_FILE", "Exact current-version rule material differs", failure);
            }
        }
    }
    public record Character(String name, String kind, boolean active, InitialPlan initial,
                            Schema2CharacterRuntime.History history) {
        public Character { CampaignJson.name(name); if (!Set.of("PC", "NPC").contains(kind) || initial == null || history == null) throw CampaignJson.bad("Invalid character profile"); }
        public UUID id() { return initial.characterId(); }
    }
    public record Node(UUID id, String name, List<UUID> adjacent) {
        public Node { CampaignJson.uuid(id.toString()); CampaignJson.name(name); adjacent = List.copyOf(adjacent); }
    }
    public record Position(UUID characterId, CampaignSpaceData.Ref space,
                           com.dndtool.service.Schema2CombatState.Position grid, int side, long version) {
        public Position {
            if (characterId == null || space == null || version < 0 || side < 0 || side > 15
                    || space.kind().equals("WORLD") != (grid == null)) throw CampaignJson.bad("Invalid unique placement");
            CampaignJson.uuid(characterId.toString());
        }
        public UUID nodeId() { return space.kind().equals("WORLD") ? space.id() : null; }
        public UUID sceneId() { return space.kind().equals("SCENE") ? space.id() : null; }
    }
    public record Profile(UUID characterId, String name, String kind, boolean active) { }
    public record Item(UUID id, UUID characterId, String name, long quantity, String sourceKey, String description, boolean active) {
        public Item(UUID id, UUID characterId, String name, long quantity) { this(id, characterId, name, quantity, null, "", true); }
        public Item {
            CampaignJson.uuid(id.toString()); CampaignJson.uuid(characterId.toString()); CampaignJson.name(name);
            if (quantity < 0 || quantity > 1000000 || description == null || description.codePointCount(0, description.length()) > 500
                    || !java.text.Normalizer.isNormalized(description, java.text.Normalizer.Form.NFC)) throw CampaignJson.bad("Item quantity or description outside range");
            CampaignJson.validString(description);
            if (sourceKey != null && CampaignManualData.catalog().items().stream().noneMatch(i -> i.key().equals(sourceKey)&&i.name().equals(name)&&i.description().equals(description))) throw CampaignJson.bad("Unknown or changed simple item template");
        }
    }
    public record Root(long sequence, UUID operationId, String kind, UUID subjectId, String intent,
                       String before, String after, List<UUID> characterOperations, UUID combatId, CampaignParticipationData.Actor actor, CampaignSpaceData.Audit space, List<CampaignSpaceData.Die> rolls) {
        public Root {
            if (actor == null || space == null || sequence < 1 || operationId == null || subjectId == null || kind == null || intent == null || !intent.matches("[0-9a-f]{64}")) throw CampaignJson.bad("Invalid domain root");
            CampaignJson.uuid(operationId.toString()); CampaignJson.uuid(subjectId.toString());
            CampaignJson.validString(before); CampaignJson.validString(after);
            characterOperations = List.copyOf(characterOperations);
            rolls = List.copyOf(rolls);
        }
    }
    public record World(List<Node> nodes, List<Position> positions, List<Item> items, CampaignManualData.State manual, CampaignSpaceData.State space) {
        public World { nodes = List.copyOf(nodes); positions = List.copyOf(positions); items = List.copyOf(items); if (manual == null || space == null) throw CampaignJson.bad("Missing spatial or manual domain"); }
    }
    public record State(Material material, List<Character> characters, List<Schema2CombatHistory.History> combats,
                        List<Node> nodes, List<Position> positions, List<Item> items, List<Root> roots, CampaignManualData.State manual, CampaignParticipationData.State participation, CampaignSpaceData.State space) {
        public State {
            if (material == null || manual == null || participation == null || space == null) throw CampaignJson.bad("Missing exact rules, space or manual domain");
            characters = List.copyOf(characters); combats = List.copyOf(combats); nodes = List.copyOf(nodes);
            positions = List.copyOf(positions); items = List.copyOf(items); roots = List.copyOf(roots);
        }
        public static State initial(ProductionSchema2Projection material) { return new State(Material.of(material), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), CampaignManualData.State.empty(), CampaignParticipationData.State.empty(), CampaignSpaceData.State.empty()); }
    }
}
