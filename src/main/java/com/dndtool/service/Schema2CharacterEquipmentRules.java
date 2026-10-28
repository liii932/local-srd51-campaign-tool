package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Ordinary equipment instances and preparation outside combat; no attacks, prices or encumbrance. */
public final class Schema2CharacterEquipmentRules {
    private static final Comparator<EquipmentItem> ORDER = Comparator.comparing(value -> value.itemId().toString());
    private static final Set<String> KINDS = Set.of("ITEM_ACQUIRED", "ITEM_REMOVED", "ITEM_EQUIPPED", "ITEM_UNEQUIPPED");
    private final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
    private final Schema2CharacterRestRules rests = new Schema2CharacterRestRules();
    public record Derived(int armorClass, int speedPenalty, boolean castingForbidden,
            boolean physicalCheckDisadvantage, Set<RuleIdentity> stealthSkills) {
        public Derived { stealthSkills = Set.copyOf(stealthSkills); }
    }
    public static boolean handles(Command command) { return KINDS.contains(command.kind()); }

    public State apply(ProductionSchema2Projection material, State before, Command command, UUID operationId) {
        growth.validateState(material, before); rests.validateState(material, before); validateState(material, before);
        requireCompleteScope(material, before);
        if (!handles(command) || before.version() >= JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS - 1)
            throw invalid("S2_EQUIPMENT_COMMAND");
        if (before.hitPoints() == 0 || before.health().dead()) throw invalid("S2_EQUIPMENT_HEALTH");
        if (before.rest().active() != null) throw invalid("S2_EQUIPMENT_REST_ACTIVE");
        requireNoUnownedEffects(material,before);
        var input = command.equipmentInput();
        var next = new ArrayList<>(before.equipment()); long minutes = 0;
        if (command.kind().equals("ITEM_ACQUIRED")) {
            definition(material, input.item());
            if (!input.itemId().equals(operationId) || next.stream().anyMatch(value -> value.itemId().equals(input.itemId())))
                throw invalid("S2_EQUIPMENT_INSTANCE");
            if (next.size() >= JdbcSchema2CharacterRuntimeRepository.MAX_EQUIPMENT) throw invalid("S2_EQUIPMENT_CAPACITY");
            next.add(new EquipmentItem(input.itemId(), input.item(), "CARRIED"));
        } else {
            EquipmentItem existing = next.stream().filter(value -> value.itemId().equals(input.itemId())).findFirst()
                    .orElseThrow(() -> invalid("S2_EQUIPMENT_NOT_FOUND"));
            ItemDefinition item = definition(material, existing.item());
            switch (command.kind()) {
                case "ITEM_REMOVED" -> {
                    if (!existing.location().equals("CARRIED")) throw invalid("S2_EQUIPMENT_PREPARED");
                    next.remove(existing);
                }
                case "ITEM_EQUIPPED" -> {
                    if (!existing.location().equals("CARRIED")) throw invalid("S2_EQUIPMENT_PREPARED");
                    next.remove(existing); next.add(new EquipmentItem(existing.itemId(), existing.item(), input.location()));
                    minutes = switch (item.kind()) { case LIGHT_ARMOR -> 1; case MEDIUM_ARMOR -> 5; case HEAVY_ARMOR -> 10; default -> 0; };
                }
                case "ITEM_UNEQUIPPED" -> {
                    if (existing.location().equals("CARRIED")) throw invalid("S2_EQUIPMENT_NOT_PREPARED");
                    next.remove(existing); next.add(new EquipmentItem(existing.itemId(), existing.item(), "CARRIED"));
                    minutes = switch (item.kind()) { case LIGHT_ARMOR, MEDIUM_ARMOR -> 1; case HEAVY_ARMOR -> 5; default -> 0; };
                }
                default -> throw invalid("S2_EQUIPMENT_COMMAND");
            }
        }
        if (before.rest().minute() > Schema2CharacterRestRules.MAX_MINUTE - minutes) throw invalid("S2_EQUIPMENT_TIME_CAPACITY");
        next.sort(ORDER);
        RestState clock = new RestState(before.rest().minute() + minutes, before.rest().second(),before.rest().lastLongRestEndMinute(),before.rest().lastLongRestEndSecond(), null);
        State after = new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(), before.characterClass(),
                before.abilities(), before.level(), before.proficiencyBonus(), before.hitPoints(), before.maximumHitPoints(), before.classes(),
                before.hitDicePools(), before.subclasses(), before.features(), before.feats(), before.proficiencies(), before.version() + 1,
                before.resources(), clock, next, before.health(), before.spellbook());
        validateState(material, after); return after;
    }

    public void validateState(ProductionSchema2Projection material, State state) {
        if (state.equipment().size() > JdbcSchema2CharacterRuntimeRepository.MAX_EQUIPMENT
                || !state.equipment().equals(state.equipment().stream().sorted(ORDER).toList())) throw invalid("S2_EQUIPMENT_STATE");
        Set<UUID> ids = new HashSet<>(); int armor = 0, shields = 0, main = 0, off = 0;
        for (EquipmentItem instance : state.equipment()) {
            if (!ids.add(instance.itemId())) throw invalid("S2_EQUIPMENT_STATE");
            ItemDefinition definition = definition(material, instance.item());
            if (Set.of("CARRIED","GROUND").contains(instance.location())) continue;
            boolean bodyArmor = armor(definition.kind());
            if (bodyArmor) {
                if (!instance.location().equals("WORN") || ++armor > 1) throw invalid("S2_EQUIPMENT_LOCATION");
            } else {
                if (instance.location().equals("WORN")) throw invalid("S2_EQUIPMENT_LOCATION");
                if ((definition.hands() == 2) != instance.location().equals("BOTH_HANDS")) throw invalid("S2_EQUIPMENT_HANDS");
                if (instance.location().equals("MAIN_HAND") || instance.location().equals("BOTH_HANDS")) main++;
                if (instance.location().equals("OFF_HAND") || instance.location().equals("BOTH_HANDS")) off++;
                if (definition.kind() == EquipmentKind.SHIELD && ++shields > 1) throw invalid("S2_EQUIPMENT_SHIELD");
            }
        }
        if (main > 1 || off > 1) throw invalid("S2_EQUIPMENT_HANDS");
    }

    public Derived derive(ProductionSchema2Projection material, State state) {
        validateState(material, state); requireNoUnownedEffects(material,state);
        int dexterity = Math.floorDiv(state.abilities().get("ability.dexterity") - 10, 2);
        int ac = 10 + dexterity, shield = 0, speed = 0; boolean untrained = false;
        Set<RuleIdentity> stealth = new HashSet<>();
        for (EquipmentItem instance : state.equipment()) {
            if (Set.of("CARRIED","GROUND").contains(instance.location())) continue;
            ItemDefinition item = definition(material, instance.item());
            if (armor(item.kind())) {
                ac = item.armorClass() + switch (item.kind()) { case LIGHT_ARMOR -> dexterity; case MEDIUM_ARMOR -> Math.min(2, dexterity); default -> 0; };
                if (state.abilities().get("ability.strength") < item.strengthMinimum()) speed = 10;
                if (item.stealthDisadvantage()) stealth.add(item.stealthSkill());
            } else if (item.kind() == EquipmentKind.SHIELD) shield = 2;
            if ((armor(item.kind()) || item.kind() == EquipmentKind.SHIELD)
                    && state.proficiencies().stream().noneMatch(fact -> fact.target().equals(item.proficiency()))) untrained = true;
        }
        return new Derived(ac + shield, speed, untrained, untrained, stealth);
    }

    public boolean hasPreparedTool(ProductionSchema2Projection material, State state, RuleIdentity proficiency) {
        validateState(material, state);
        return state.equipment().stream().filter(value -> Set.of("MAIN_HAND","OFF_HAND","BOTH_HANDS").contains(value.location()))
                .map(value -> definition(material, value.item())).anyMatch(value -> value.kind() == EquipmentKind.TOOL && value.proficiency().equals(proficiency));
    }
    private static ItemDefinition definition(ProductionSchema2Projection material, RuleIdentity identity) {
        var matches = material.projection().rules().stream().filter(FinalItem.class::isInstance).map(FinalItem.class::cast)
                .filter(row -> row.identity().equals(identity) && row.enabled()).toList();
        if (matches.size() != 1 || !matches.getFirst().definition().algorithm().equals("SRD_2014_EQUIPMENT_V1")) throw invalid("S2_EQUIPMENT_RULE_UNSUPPORTED");
        return matches.getFirst().definition();
    }
    static void requireCompleteScope(ProductionSchema2Projection material, State before) {
        requireCompleteScope(material, before, "S2_EQUIPMENT_GROWTH_SCOPE");
    }
    static void requireCompleteScope(ProductionSchema2Projection material, State before, String code) {
        for (ClassLevel characterClass : before.classes()) {
            var owners = material.projection().rules().stream().filter(FinalClassProgression.class::isInstance).map(FinalClassProgression.class::cast)
                    .filter(row -> row.enabled() && row.definition().ownerClass().equals(characterClass.characterClass())).toList();
            if (owners.size() != 1 || owners.getFirst().definition().advancementScope() != ProgressionScope.CHARACTER_ADVANCEMENT)
                throw invalid(code);
        }
    }
    static void requireNoUnownedEffects(ProductionSchema2Projection material,State state) {
        Schema2SpellRules.requireSupportedFeatures(material,state,"S2_EQUIPMENT_EFFECT_UNSUPPORTED");
    }
    static void requireNoUnownedEffects(State state) {
        if (!state.features().isEmpty() || !state.feats().isEmpty()) throw invalid("S2_EQUIPMENT_EFFECT_UNSUPPORTED");
    }
    private static boolean armor(EquipmentKind kind) { return kind == EquipmentKind.LIGHT_ARMOR || kind == EquipmentKind.MEDIUM_ARMOR || kind == EquipmentKind.HEAVY_ARMOR; }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
}
