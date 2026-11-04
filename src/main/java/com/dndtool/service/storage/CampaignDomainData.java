package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import java.util.*;

/** Complete logical campaign aggregates; physical run IDs and HTTP receipts are kept by core_control. */
public final class CampaignDomainData {
    private CampaignDomainData() { }
    public record Material(List<ProductionSchema2Projection.StoredMember> members, List<FinalRule> rules,
                           List<FinalEntry> entries, List<FieldChain> chains) {
        public Material { members = List.copyOf(members); rules = List.copyOf(rules); entries = List.copyOf(entries); chains = List.copyOf(chains); }
        public static Material of(ProductionSchema2Projection p) { return new Material(p.members(), p.projection().rules(), p.projection().entries(), p.projection().chains()); }
        public ProductionSchema2Projection restore() {
            ProductionSchema2Projection expected = BundledCampaignRules.projection();
            if (!members.equals(expected.members()) || !rules.equals(expected.projection().rules())
                    || !entries.equals(expected.projection().entries()) || !chains.equals(expected.projection().chains()))
                throw CampaignJson.bad("Exact current-version rule material differs");
            return expected;
        }
    }
    public record Character(String name, String kind, boolean active, InitialPlan initial,
                            JdbcSchema2CharacterRuntimeRepository.History history) {
        public Character { CampaignJson.name(name); if (!Set.of("PC", "NPC").contains(kind) || initial == null || history == null) throw CampaignJson.bad("Invalid character profile"); }
        public UUID id() { return initial.characterId(); }
    }
    public record Node(UUID id, String name, List<UUID> adjacent) {
        public Node { CampaignJson.uuid(id.toString()); CampaignJson.name(name); adjacent = List.copyOf(adjacent); }
    }
    public record Position(UUID characterId, UUID nodeId) { }
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
                       String before, String after, List<UUID> characterOperations, UUID combatId, CampaignParticipationData.Actor actor) {
        public Root {
            if (actor == null || sequence < 1 || operationId == null || subjectId == null || kind == null || intent == null || !intent.matches("[0-9a-f]{64}")) throw CampaignJson.bad("Invalid domain root");
            CampaignJson.uuid(operationId.toString()); CampaignJson.uuid(subjectId.toString());
            CampaignJson.validString(before); CampaignJson.validString(after);
            characterOperations = List.copyOf(characterOperations);
        }
    }
    public record World(List<Node> nodes, List<Position> positions, List<Item> items, CampaignManualData.State manual) {
        public World(List<Node> nodes, List<Position> positions, List<Item> items) { this(nodes, positions, items, CampaignManualData.State.empty()); }
        public World { nodes = List.copyOf(nodes); positions = List.copyOf(positions); items = List.copyOf(items); if (manual == null) throw CampaignJson.bad("Missing manual domain"); }
    }
    public record State(Material material, List<Character> characters, List<JdbcSchema2CombatRuntimeRepository.History> combats,
                        List<Node> nodes, List<Position> positions, List<Item> items, List<Root> roots, CampaignManualData.State manual, CampaignParticipationData.State participation) {
        public State(Material material, List<Character> characters, List<JdbcSchema2CombatRuntimeRepository.History> combats, List<Node> nodes, List<Position> positions, List<Item> items, List<Root> roots, CampaignManualData.State manual) {
            this(material, characters, combats, nodes, positions, items, roots, manual, CampaignParticipationData.State.empty());
        }
        public State(Material material, List<Character> characters, List<JdbcSchema2CombatRuntimeRepository.History> combats,
                     List<Node> nodes, List<Position> positions, List<Item> items, List<Root> roots) {
            this(material, characters, combats, nodes, positions, items, roots, CampaignManualData.State.empty());
        }
        public State {
            if (material == null || manual == null || participation == null) throw CampaignJson.bad("Missing exact rules or manual domain");
            characters = List.copyOf(characters); combats = List.copyOf(combats); nodes = List.copyOf(nodes);
            positions = List.copyOf(positions); items = List.copyOf(items); roots = List.copyOf(roots);
        }
        public static State initial(ProductionSchema2Projection material) { return new State(Material.of(material), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()); }
    }
}
