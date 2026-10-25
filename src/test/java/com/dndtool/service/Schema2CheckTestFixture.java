package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original author bytes and real preparation, retained snapshot, initialization and JDBC consumers. */
final class Schema2CheckTestFixture {
    static final Identity IDENTITY = new Identity(UUID.fromString("30912233-4455-4677-8899-aabbccddeeff"), UUID.fromString("d0eeddcc-bbaa-4988-8766-554433221100"));
    static final UUID PREPARATION = UUID.fromString("30872103-0345-4abc-8def-0123456789ab");
    static final UUID CAMPAIGN = UUID.fromString("42345678-1234-4678-8234-123456789abc");
    static final UUID CHARACTER = UUID.fromString("cbcdef12-1234-4678-8234-123456789abc");
    static final RuleIdentity CHECK = new RuleIdentity(BASE, "adventure.check", "check.srd2014");
    static final RuleIdentity TOOL = new RuleIdentity(BASE, "character.tool", "tool.smith_tools");
    static final RuleIdentity OTHER_TOOL = new RuleIdentity(EXT, "character.tool", "tool.smith_tools");
    static final RuleIdentity OTHER_SKILL = new RuleIdentity(EXT, "character.proficiency", "proficiency.skill.athletics");
    static final RuleIdentity STEALTH = new RuleIdentity(BASE, "character.proficiency", "proficiency.skill.stealth");
    static final RuleIdentity HEAVY_PROFICIENCY = new RuleIdentity(BASE, "character.proficiency", "proficiency.armor.heavy");
    static final RuleIdentity SHIELD_PROFICIENCY = new RuleIdentity(BASE, "character.proficiency", "proficiency.armor.shield");
    static final RuleIdentity ITEM_TOOL = new RuleIdentity(BASE, "equipment.item", "item.smith_tools");
    static final RuleIdentity OTHER_ITEM_TOOL = new RuleIdentity(EXT, "equipment.item", "item.other_smith_tools");
    static final RuleIdentity ITEM_LIGHT = new RuleIdentity(BASE, "equipment.item", "item.leather");
    static final RuleIdentity ITEM_MEDIUM = new RuleIdentity(BASE, "equipment.item", "item.scale_mail");
    static final RuleIdentity ITEM_HEAVY = new RuleIdentity(BASE, "equipment.item", "item.plate");
    static final RuleIdentity ITEM_SHIELD = new RuleIdentity(BASE, "equipment.item", "item.shield");
    static final RuleIdentity ITEM_WEAPON = new RuleIdentity(BASE, "equipment.item", "item.weapon");
    static final RuleIdentity ITEM_TWO_HANDED = new RuleIdentity(BASE, "equipment.item", "item.two_handed");
    static final RuleIdentity HEALTH = new RuleIdentity(BASE, "adventure.health", "health.srd2014");
    static final RuleIdentity DAMAGE = new RuleIdentity(BASE, "adventure.vital_effect", "vital_effect.damage");
    static final RuleIdentity MASSIVE_DAMAGE = new RuleIdentity(BASE, "adventure.vital_effect", "vital_effect.massive_damage");
    static final RuleIdentity HEALING = new RuleIdentity(BASE, "adventure.vital_effect", "vital_effect.healing");
    static final RuleIdentity TEMPORARY = new RuleIdentity(BASE, "adventure.vital_effect", "vital_effect.temporary");
    static final RuleIdentity ZERO_DAMAGE = new RuleIdentity(BASE, "adventure.vital_effect", "vital_effect.zero_damage");
    static final GrowthChoices INITIAL_CHOICES = new GrowthChoices(null, Map.of(), null, Map.of(INITIAL_SKILL_SLOT, List.of(ARCANA)));

    final Schema2SourceReadFixture source;
    final Schema2RuntimeJdbcFixture runtime = new Schema2RuntimeJdbcFixture();
    final ProductionSchema2Projection material;
    final Schema2RulesBinding binding;
    final Schema2CharacterRuntimeService service;
    final ArrayDeque<Integer> nextRolls = new ArrayDeque<>();
    State state;
    int rolls;

    Schema2CheckTestFixture() throws Exception { this("normal"); }
    Schema2CheckTestFixture(String mode) throws Exception {
        var artifacts = Schema2GrantFixtures.artifacts(packages(mode));
        material = ProductionSchema2Projection.compose(artifacts); binding = Schema2RulesBinding.from(material);
        source = new Schema2SourceReadFixture(artifacts);
        var character = new Schema2InitialProficiencyRules.Request(new Schema2InitialProficiencyRules.Sources(
                new RuleIdentity(BASE, "character.race", "race.human"), null,
                new RuleIdentity(BASE, "character.background", "background.guard"), CLASS), Map.of());
        var prepared = new Schema2InitializationPreparationService(source.dataSource()).prepare(
                new Schema2InitializationPreparationService.Request(PREPARATION, IDENTITY, CAMPAIGN, CHARACTER,
                        source.selections(), null, character));
        var accepted = new Schema2SnapshotTransferService(source.dataSource(), runtime.dataSource()).transferPrepared(prepared);
        assertEquals(Schema2SnapshotTransferService.Status.COMMITTED, accepted.status(), accepted.code());
        service = new Schema2CharacterRuntimeService(runtime.dataSource(), sides -> {
            assertTrue(Set.of(4, 6, 8, 10, 12, 20).contains(sides)); rolls++; return nextRolls.isEmpty() ? Math.min(10, sides) : nextRolls.removeFirst();
        });
    }
    void initialize() { commit(new Command("INITIALIZED", null, 0, null, null, null, INITIAL_CHOICES)); }
    Schema2CharacterRuntimeService.Request request(Command command) {
        return new Schema2CharacterRuntimeService.Request(UUID.randomUUID(), IDENTITY, CAMPAIGN, CHARACTER,
                PREPARATION, binding, state == null ? -1 : state.version(), command, state == null ? fullAbilities() : Map.of());
    }
    Schema2CharacterRuntimeService.Result execute(Schema2CharacterRuntimeService.Request request) {
        var result = service.execute(request);
        if (result.status() == Schema2CharacterRuntimeService.Status.COMMITTED) state = result.event().after();
        return result;
    }
    Schema2CharacterRuntimeService.Result commit(Command command) {
        var result = execute(request(command)); assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED, result.status(), result.code()); return result;
    }
    UUID acquire(RuleIdentity item) {
        UUID operation = UUID.randomUUID();
        var command = equipment("ITEM_ACQUIRED", operation, item, "CARRIED");
        var request = new Schema2CharacterRuntimeService.Request(operation, IDENTITY, CAMPAIGN, CHARACTER, PREPARATION,
                binding, state.version(), command, Map.of());
        var result = execute(request); assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED, result.status(), result.code()); return operation;
    }
    void prepareTools() {
        UUID first = acquire(ITEM_TOOL); commit(equipment("ITEM_EQUIPPED", first, null, "MAIN_HAND"));
        UUID other = acquire(OTHER_ITEM_TOOL); commit(equipment("ITEM_EQUIPPED", other, null, "OFF_HAND"));
    }
    static State withPreparedTools(State before) {
        var items = List.of(new EquipmentItem(UUID.fromString("11111111-1111-4111-8111-111111111111"), ITEM_TOOL, "MAIN_HAND"),
                new EquipmentItem(UUID.fromString("22222222-2222-4222-8222-222222222222"), OTHER_ITEM_TOOL, "OFF_HAND"));
        return new State(before.identity(), before.campaignId(), before.characterId(), before.preparationOperationId(), before.characterClass(),
                before.abilities(), before.level(), before.proficiencyBonus(), before.hitPoints(), before.maximumHitPoints(), before.classes(), before.hitDicePools(),
                before.subclasses(), before.features(), before.feats(), before.proficiencies(), before.version(), before.resources(), before.rest(), items, before.health());
    }
    static Command equipment(String kind, UUID id, RuleIdentity item, String location) {
        return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, null, null,
                new EquipmentInput(id, item, location, "DM 确认物品归属及非战斗准备。"), null);
    }
    static Command effect(RuleIdentity effect, String temporaryChoice) {
        return new Command("HEALTH_EFFECT_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null,
                new HealthInput(HEALTH, effect, temporaryChoice, "DM 确认这是直接单目标环境效果。"));
    }
    static Command deathSave() {
        return new Command("DEATH_SAVE_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null,
                new HealthInput(HEALTH, null, null, "DM 明确要求进行一次手动死亡豁免。"));
    }
    static Command check(String kind, String ability, RuleIdentity proficiency, int dc, boolean advantage, boolean disadvantage) {
        return check(new CheckInput(CHECK, kind, ability, proficiency, dc, advantage, disadvantage, "DM 判定该任务的属性、熟练和难度。"));
    }
    static Command check(CheckInput input) { return new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, input); }
    static List<Map<String, byte[]>> packages(String mode) {
        var files = fullPackages();
        JsonArray definitions = Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
        if (!mode.equals("features")) for (int index = definitions.size() - 1; index >= 0; index--)
            if (definitions.get(index).getAsJsonObject().getAsJsonObject("identity").get("rule_type").getAsString().equals("character.feature")) definitions.remove(index);
        if (!mode.equals("missing")) {
            JsonObject check = definition(CHECK, 77); check.addProperty("algorithm", Schema2CharacterCheckRules.ALGORITHM); definitions.add(check);
        }
        var tool = definition(TOOL, 70); tool.addProperty("category", "ARTISAN"); definitions.add(tool);
        var stealth = definition(STEALTH, 56); stealth.addProperty("category", "SKILL"); stealth.addProperty("ability_key", "ability.dexterity"); definitions.add(stealth);
        for (RuleIdentity proficiency : List.of(HEAVY_PROFICIENCY, SHIELD_PROFICIENCY)) {
            var row = definition(proficiency, 56); row.addProperty("category", "ARMOR"); definitions.add(row);
        }
        definitions.add(item(ITEM_TOOL, "TOOL", 0, 0, false, 1, TOOL));
        definitions.add(item(ITEM_LIGHT, "LIGHT_ARMOR", 11, 0, false, 0, LIGHT_ARMOR));
        definitions.add(item(ITEM_MEDIUM, "MEDIUM_ARMOR", 14, 0, true, 0, LIGHT_ARMOR));
        definitions.add(item(ITEM_HEAVY, "HEAVY_ARMOR", 18, 15, true, 0, HEAVY_PROFICIENCY));
        definitions.add(item(ITEM_SHIELD, "SHIELD", 2, 0, false, 1, SHIELD_PROFICIENCY));
        definitions.add(item(ITEM_WEAPON, "WEAPON", 0, 0, false, 1, WEAPON));
        definitions.add(item(ITEM_TWO_HANDED, "WEAPON", 0, 0, false, 2, WEAPON));
        var health = definition(HEALTH, 98); health.addProperty("algorithm", "SRD_2014_HEALTH_V1"); definitions.add(health);
        definitions.add(vital(DAMAGE, "DAMAGE", 0, 0, 6)); definitions.add(vital(MASSIVE_DAMAGE, "DAMAGE", 0, 0, 30));
        definitions.add(vital(HEALING, "HEALING", 2, 4, 1)); definitions.add(vital(TEMPORARY, "TEMP_HP", 0, 0, 5));
        definitions.add(vital(ZERO_DAMAGE, "DAMAGE", 1, 6, -10));
        files.getFirst().put("rules.json", bytes(definitions.toString()));
        JsonArray extension = Schema2GrantFixtures.json(files.get(1).get("rules.json")).getAsJsonArray();
        var otherTool = definition(OTHER_TOOL, 70); otherTool.addProperty("category", "ARTISAN"); extension.add(otherTool);
        extension.add(item(OTHER_ITEM_TOOL, "TOOL", 0, 0, false, 1, OTHER_TOOL));
        var otherSkill = definition(OTHER_SKILL, 56); otherSkill.addProperty("category", "SKILL"); otherSkill.addProperty("ability_key", "ability.strength"); extension.add(otherSkill);
        files.get(1).put("rules.json", bytes(extension.toString()));
        JsonArray entries = Schema2GrantFixtures.json(files.getFirst().get("growth.json")).getAsJsonArray();
        for (String slot : List.of("athletics_a", "athletics_b")) {
            var grant = new JsonObject(); grant.addProperty("kind", "FIXED"); grant.add("target", jsonIdentity(ATHLETICS));
            var row = entry(new EntryIdentity(PROGRESSION, "starting_proficiencies", BASE, slot)); row.add("growth_grant", grant); entries.add(row);
        }
        for (RuleIdentity owner : List.of(new RuleIdentity(BASE, "character.race", "race.human"), new RuleIdentity(BASE, "character.background", "background.guard"))) {
            var grant = new JsonObject(); grant.addProperty("kind", "FIXED"); grant.addProperty("category", "TOOL"); grant.add("target", jsonIdentity(TOOL));
            var row = entry(new EntryIdentity(owner, "proficiency_grants", BASE, "smith_training")); row.add("grant", grant); entries.add(row);
        }
        files.getFirst().put("growth.json", bytes(entries.toString()));
        if (mode.equals("disabled")) {
            JsonArray changes = Schema2GrantFixtures.json(files.get(1).get("changes.json")).getAsJsonArray();
            var change = new JsonObject(); change.addProperty("op", "set_enabled"); change.add("target", jsonIdentity(CHECK)); change.addProperty("value", false); changes.add(change);
            files.get(1).put("changes.json", bytes(changes.toString()));
        }
        return files;
    }
    private static JsonObject definition(RuleIdentity identity, int page) {
        var value = new JsonObject(); value.add("identity", jsonIdentity(identity)); value.addProperty("display_name", identity.ruleKey());
        value.addProperty("description", "Bounded ordinary internal checks."); value.addProperty("source_page", page); return value;
    }
    private static JsonObject item(RuleIdentity identity, String kind, int ac, int strength, boolean stealth, int hands, RuleIdentity proficiency) {
        var value = definition(identity, 64); value.addProperty("kind", kind); value.addProperty("armor_class", ac);
        value.addProperty("strength_minimum", strength); value.addProperty("stealth_disadvantage", stealth); value.addProperty("hands", hands);
        value.add("proficiency", jsonIdentity(proficiency)); if (stealth) value.add("stealth_skill", jsonIdentity(STEALTH));
        value.addProperty("algorithm", "SRD_2014_EQUIPMENT_V1"); return value;
    }
    private static JsonObject vital(RuleIdentity identity, String kind, int count, int sides, int modifier) {
        var value = definition(identity, 98); value.addProperty("kind", kind); value.addProperty("dice_count", count);
        value.addProperty("dice_sides", sides); value.addProperty("modifier", modifier);
        if (kind.equals("DAMAGE")) value.addProperty("damage_type", "BLUDGEONING");
        value.addProperty("algorithm", "DIRECT_VITAL_EFFECT_V1"); return value;
    }
    private static JsonObject entry(EntryIdentity identity) {
        var root = new JsonObject(); var value = new JsonObject(); value.addProperty("kind", "entry"); value.add("rule", jsonIdentity(identity.rule()));
        value.addProperty("collection_key", identity.collectionKey()); value.addProperty("module_key", identity.moduleKey()); value.addProperty("entry_key", identity.entryKey()); root.add("identity", value); return root;
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
