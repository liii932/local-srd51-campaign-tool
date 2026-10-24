package com.dndtool.module.composition;

import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2InitialCharacterPlanService;
import com.dndtool.service.Schema2InitialProficiencyRules;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Finite internal statistics/resource author material, never a complete SRD class or base. */
public final class Schema2ProgressionFixtures {
    public static final String BASE = "growth_base", EXT = "growth_extension";
    public static final RuleIdentity CLASS = new RuleIdentity(BASE, "character.class", "class.guard");
    public static final RuleIdentity PROGRESSION = new RuleIdentity(BASE, "character.class_progression", "progression.guard");
    public static final RuleIdentity ENERGY = new RuleIdentity(BASE, "character.resource", "resource.energy");
    public static final RuleIdentity EXT_ENERGY = new RuleIdentity(EXT, "character.resource", "resource.energy");
    public static final RuleIdentity LATER = new RuleIdentity(BASE, "character.resource", "resource.later");
    public static final RuleIdentity ALT_CLASS = new RuleIdentity(EXT, "character.class", "class.guard");
    public static final RuleIdentity ALT_PROGRESSION = new RuleIdentity(EXT, "character.class_progression", "progression.guard");
    public static final RuleIdentity SUBCLASS = new RuleIdentity(BASE, "character.subclass", "subclass.guard");
    public static final RuleIdentity ALT_SUBCLASS = new RuleIdentity(EXT, "character.subclass", "subclass.guard");
    public static final RuleIdentity GRAPPLER = new RuleIdentity(BASE, "character.feat", "feat.grappler");
    public static final RuleIdentity ATHLETICS = new RuleIdentity(BASE, "character.proficiency", "proficiency.skill.athletics");
    public static final RuleIdentity ACROBATICS = new RuleIdentity(BASE, "character.proficiency", "proficiency.skill.acrobatics");
    public static final RuleIdentity ARCANA = new RuleIdentity(BASE, "character.proficiency", "proficiency.skill.arcana");
    public static final RuleIdentity WEAPON = new RuleIdentity(BASE, "character.proficiency", "proficiency.weapon.training");
    public static final RuleIdentity ALT_WEAPON = new RuleIdentity(EXT, "character.proficiency", "proficiency.weapon.training");
    public static final RuleIdentity LIGHT_ARMOR = new RuleIdentity(BASE, "character.proficiency", "proficiency.armor.light");
    public static final RuleIdentity SKILL_SET = new RuleIdentity(BASE, "catalog.proficiency_set", "proficiency_set.skills");
    public static final EntryIdentity INITIAL_SKILL_SLOT = new EntryIdentity(PROGRESSION, "starting_proficiencies", BASE, "skill");
    public static final EntryIdentity MULTICLASS_SKILL_SLOT = new EntryIdentity(ALT_PROGRESSION, "multiclass_proficiencies", EXT, "skill");
    private Schema2ProgressionFixtures() { }

    public static List<Map<String, byte[]>> packages() {
        var base = new LinkedHashMap<String, byte[]>();
        base.put(HEADER_PATH, bytes("""
                {"author_schema_version":2,"module_key":"growth_base","release_version":"1",
                 "package_display_name":"Internal statistics base","role":"base","dependencies":[],
                 "compatible_combinations":[{}],"load_order_constraints":[],
                 "files":[{"kind":"definitions","path":"rules.json"}]}
                """));
        JsonArray definitions = new JsonArray();
        definitions.add(source("character.race", "race.human"));
        definitions.add(source("character.background", "background.guard"));
        definitions.add(source("character.class", "class.guard"));
        JsonObject progression = source("character.class_progression", "progression.guard");
        progression.add("owner_class", jsonIdentity(CLASS));
        progression.addProperty("hit_die_sides", 10);
        progression.addProperty("proficiency_bonus_profile", "1-4:2,5-8:3,9-12:4,13-16:5,17-20:6");
        progression.addProperty("supported_maximum_level", 3);
        progression.addProperty("advancement_scope", "STATISTICS_AND_RESOURCES_ONLY");
        definitions.add(progression);
        definitions.add(resource(ENERGY, "1-2:2,3-20:3", "1-20:SHORT_REST", "AUTOMATIC"));
        definitions.add(resource(LATER, "3-20:CHARISMA_MODIFIER_MINIMUM_ONE", "1-20:LONG_REST", "AUTOMATIC"));
        definitions.add(resource(new RuleIdentity(BASE, "character.resource", "resource.blocked"),
                "1-20:1", "1-20:LONG_REST", "BLOCKED"));
        base.put("rules.json", bytes(definitions.toString()));
        var extension = new LinkedHashMap<String, byte[]>();
        extension.put(HEADER_PATH, bytes("""
                {"author_schema_version":2,"module_key":"growth_extension","release_version":"1",
                 "package_display_name":"Internal statistics extension","role":"extension",
                 "dependencies":[{"module_key":"growth_base","supported_versions":["1"]}],
                 "compatible_combinations":[{"growth_base":"1"}],"load_order_constraints":[],
                 "files":[{"kind":"definitions","path":"rules.json"},{"kind":"changes","path":"changes.json"}]}
                """));
        JsonArray extensionDefinitions = new JsonArray();
        extensionDefinitions.add(resource(EXT_ENERGY, "1-20:CLASS_LEVEL", "1-20:SHORT_REST", "AUTOMATIC"));
        extension.put("rules.json", bytes(extensionDefinitions.toString()));
        JsonArray changes = new JsonArray();
        changes.add(change(ENERGY, "maximum_profile", "1-2:3,3-20:4"));
        changes.add(change(ENERGY, "recovery_profile", "1-20:LONG_REST"));
        extension.put("changes.json", bytes(changes.toString()));
        return new ArrayList<>(List.of(base, extension));
    }
    public static List<ProductionRuleArtifact> artifacts() { return Schema2GrantFixtures.artifacts(packages()); }
    public static ProductionSchema2Projection projection() { return ProductionSchema2Projection.compose(artifacts()); }
    public static List<Map<String, byte[]>> fullPackages() {
        var files = packages();
        JsonArray base = Schema2GrantFixtures.json(files.get(0).get("rules.json")).getAsJsonArray();
        JsonObject growth = base.get(3).getAsJsonObject();
        growth.addProperty("advancement_scope", "CHARACTER_ADVANCEMENT"); growth.addProperty("supported_maximum_level", 6);
        growth.add("multiclass_prerequisite", prerequisite("ability.strength", "ability.dexterity"));
        base.get(5).getAsJsonObject().addProperty("maximum_profile", "1-20:CHARISMA_MODIFIER_MINIMUM_ONE");
        base.get(6).getAsJsonObject().addProperty("maximum_profile", "20:1");
        base.add(subclass(SUBCLASS, CLASS, 3));
        base.add(feature(new RuleIdentity(BASE, "character.feature", "feature.training"), CLASS, 1, "FEATURE", "AUTOMATIC", "AUTOMATIC_RESOURCE_LIFECYCLE_V1"));
        base.add(feature(new RuleIdentity(BASE, "character.feature", "feature.blocked"), CLASS, 2, "FEATURE", "BLOCKED", "BLOCKED_SPELL_SYSTEM_V1"));
        base.add(feature(new RuleIdentity(BASE, "character.feature", "feature.option"), CLASS, 2, "OPTION", "BLOCKED", "BLOCKED_DOWNSTREAM_SYSTEM_V1"));
        base.add(feature(new RuleIdentity(BASE, "character.feature", "feature.subclass"), SUBCLASS, 3, "FEATURE", "DM_ADJUDICATION", "BOUNDED_DM_ADJUDICATION_V1"));
        JsonObject feat = definition(GRAPPLER); feat.add("prerequisite", prerequisite("ability.strength"));
        feat.addProperty("execution_mode", "BLOCKED"); feat.addProperty("execution_algorithm", "BLOCKED_DOWNSTREAM_SYSTEM_V1"); base.add(feat);
        base.add(proficiency(ATHLETICS, "SKILL", "ability.strength")); base.add(proficiency(ACROBATICS, "SKILL", "ability.dexterity"));
        base.add(proficiency(ARCANA, "SKILL", "ability.intelligence")); base.add(proficiency(WEAPON, "WEAPON", null));
        base.add(proficiency(LIGHT_ARMOR, "ARMOR", null));
        JsonObject set = new JsonObject(); set.add("identity", jsonIdentity(SKILL_SET)); set.addProperty("display_name", "Skill candidates");
        set.addProperty("category", "SKILL"); set.addProperty("minimum_enabled_members", 3); base.add(set);
        JsonObject rest = definition(new RuleIdentity(BASE, "adventure.rest", "rest.srd2014"));
        rest.addProperty("source_page", 87); rest.addProperty("algorithm", "SRD_2014_REST_V1"); base.add(rest);
        files.get(0).put("rules.json", bytes(base.toString()));
        JsonArray extension = Schema2GrantFixtures.json(files.get(1).get("rules.json")).getAsJsonArray();
        extension.get(0).getAsJsonObject().add("owner_class", jsonIdentity(ALT_CLASS));
        extension.get(0).getAsJsonObject().addProperty("maximum_profile", "1-20:CHARISMA_MODIFIER_MINIMUM_ONE");
        extension.add(definition(ALT_CLASS));
        JsonObject altGrowth = growth.deepCopy(); altGrowth.add("identity", jsonIdentity(ALT_PROGRESSION)); altGrowth.add("owner_class", jsonIdentity(ALT_CLASS));
        altGrowth.addProperty("hit_die_sides", 6); altGrowth.addProperty("supported_maximum_level", 4);
        altGrowth.add("multiclass_prerequisite", prerequisite("ability.charisma")); extension.add(altGrowth);
        extension.add(subclass(ALT_SUBCLASS, ALT_CLASS, 1));
        extension.add(feature(new RuleIdentity(EXT, "character.feature", "feature.training"), ALT_CLASS, 1, "FEATURE", "BLOCKED", "BLOCKED_DOWNSTREAM_SYSTEM_V1"));
        extension.add(proficiency(ALT_WEAPON, "WEAPON", null));
        files.get(1).put("rules.json", bytes(extension.toString()));
        JsonArray baseEntries = new JsonArray();
        for (int level = 1; level <= 6; level++) baseEntries.add(level(PROGRESSION, BASE, level, level == 4 || level == 6));
        for (RuleIdentity skill : List.of(ATHLETICS, ACROBATICS, ARCANA)) {
            JsonObject member = entry(new EntryIdentity(SKILL_SET, "members", BASE, skill.ruleKey().substring(skill.ruleKey().lastIndexOf('.') + 1)));
            member.add("proficiency", jsonIdentity(skill)); baseEntries.add(member);
        }
        baseEntries.add(fixed(new EntryIdentity(PROGRESSION, "starting_proficiencies", BASE, "armor"), LIGHT_ARMOR));
        baseEntries.add(fixed(new EntryIdentity(PROGRESSION, "starting_proficiencies", BASE, "weapon"), WEAPON));
        baseEntries.add(choice(INITIAL_SKILL_SLOT));
        JsonArray altEntries = new JsonArray();
        for (int level = 1; level <= 4; level++) altEntries.add(level(ALT_PROGRESSION, EXT, level, level == 4));
        altEntries.add(fixed(new EntryIdentity(ALT_PROGRESSION, "multiclass_proficiencies", EXT, "armor"), LIGHT_ARMOR));
        altEntries.add(fixed(new EntryIdentity(ALT_PROGRESSION, "multiclass_proficiencies", EXT, "weapon"), ALT_WEAPON));
        altEntries.add(choice(MULTICLASS_SKILL_SLOT));
        for (int index = 0; index < 2; index++) {
            JsonObject header = Schema2GrantFixtures.json(files.get(index).get(HEADER_PATH)).getAsJsonObject();
            JsonObject declaration = new JsonObject(); declaration.addProperty("kind", "entries"); declaration.addProperty("path", "growth.json");
            header.getAsJsonArray("files").add(declaration); files.get(index).put(HEADER_PATH, bytes(header.toString()));
            files.get(index).put("growth.json", bytes((index == 0 ? baseEntries : altEntries).toString()));
        }
        return files;
    }
    public static List<ProductionRuleArtifact> fullArtifacts() { return Schema2GrantFixtures.artifacts(fullPackages()); }
    public static ProductionSchema2Projection fullProjection() { return ProductionSchema2Projection.compose(fullArtifacts()); }
    public static Schema2InitialCharacterPlanService.InitialPlan fullPlan(ProductionSchema2Projection material,
            Identity identity, UUID campaign, UUID character) { return initialPlan(material, identity, campaign, character); }
    public static Map<String, Integer> fullAbilities() {
        return Map.of("ability.strength", 15, "ability.dexterity", 12, "ability.constitution", 13,
                "ability.intelligence", 10, "ability.wisdom", 8, "ability.charisma", 14);
    }
    public static com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.GrowthChoices fullInitialChoices() {
        return new com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.GrowthChoices(null, Map.of(), null, Map.of(INITIAL_SKILL_SLOT, List.of(ATHLETICS)));
    }
    public static com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.GrowthChoices fullMulticlassChoices() {
        return new com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.GrowthChoices(ALT_SUBCLASS, Map.of(), null, Map.of(MULTICLASS_SKILL_SLOT, List.of(ACROBATICS)));
    }
    public static Schema2InitialCharacterPlanService.InitialPlan initialPlan(ProductionSchema2Projection material,
            Identity identity, UUID campaign, UUID character) {
        var sources = new Schema2InitialProficiencyRules.Sources(new RuleIdentity(BASE, "character.race", "race.human"),
                null, new RuleIdentity(BASE, "character.background", "background.guard"), CLASS);
        var plan = new Schema2InitialCharacterPlanService().prepareMaterial(identity, Schema2RulesBinding.from(material),
                material, new Schema2InitialProficiencyRules.Request(sources, Map.of()));
        return new Schema2InitialCharacterPlanService.InitialPlan(campaign, character, plan);
    }
    public static Map<String, Integer> abilities() {
        return Map.of("ability.strength", 15, "ability.dexterity", 14, "ability.constitution", 13,
                "ability.intelligence", 12, "ability.wisdom", 10, "ability.charisma", 8);
    }
    public static JsonObject jsonIdentity(RuleIdentity identity) {
        return Schema2GrantFixtures.rule(identity.moduleKey(), identity.ruleType(), identity.ruleKey());
    }
    private static JsonObject source(String type, String key) {
        JsonObject row = new JsonObject(); row.add("identity", jsonIdentity(new RuleIdentity(BASE, type, key)));
        row.addProperty("display_name", key); row.addProperty("description", "Internal finite statistics capability.");
        row.addProperty("source_page", 56); return row;
    }
    private static JsonObject resource(RuleIdentity identity, String maximum, String recovery, String execution) {
        JsonObject row = source(identity.ruleType(), identity.ruleKey()); row.add("identity", jsonIdentity(identity));
        row.add("owner_class", jsonIdentity(CLASS)); row.addProperty("maximum_profile", maximum);
        row.addProperty("recovery_profile", recovery); row.addProperty("execution_mode", execution); return row;
    }
    private static JsonObject change(RuleIdentity identity, String field, String value) {
        JsonObject row = new JsonObject(); row.addProperty("op", "set_field"); row.add("target", jsonIdentity(identity));
        row.addProperty("field", field); row.addProperty("value", value); return row;
    }
    private static JsonObject definition(RuleIdentity identity) { var row = source(identity.ruleType(), identity.ruleKey()); row.add("identity", jsonIdentity(identity)); return row; }
    private static JsonObject subclass(RuleIdentity identity, RuleIdentity owner, int level) {
        var row = definition(identity); row.add("owner_class", jsonIdentity(owner)); row.addProperty("selection_level", level); return row;
    }
    private static JsonObject feature(RuleIdentity identity, RuleIdentity owner, int level, String kind, String mode, String algorithm) {
        var row = definition(identity); row.add("owner", jsonIdentity(owner)); row.addProperty("level", level); row.addProperty("feature_kind", kind);
        row.addProperty("execution_mode", mode); row.addProperty("execution_algorithm", algorithm); return row;
    }
    private static JsonObject proficiency(RuleIdentity identity, String category, String ability) {
        var row = definition(identity); row.addProperty("category", category); if (ability != null) row.addProperty("ability_key", ability); return row;
    }
    private static JsonObject prerequisite(String... alternatives) {
        var root = new JsonObject(); var groups = new JsonArray(); root.add("all_of", groups);
        if (alternatives.length > 0) {
            var group = new JsonObject(); var terms = new JsonArray(); group.add("any_of", terms); groups.add(group);
            for (String ability : alternatives) { var term = new JsonObject(); term.addProperty("ability", ability); term.addProperty("minimum", 13); terms.add(term); }
        }
        return root;
    }
    private static JsonObject entry(EntryIdentity identity) {
        var row = new JsonObject(); var key = new JsonObject(); key.addProperty("kind", "entry"); key.add("rule", jsonIdentity(identity.rule()));
        key.addProperty("collection_key", identity.collectionKey()); key.addProperty("module_key", identity.moduleKey()); key.addProperty("entry_key", identity.entryKey()); row.add("identity", key); return row;
    }
    private static JsonObject level(RuleIdentity owner, String module, int level, boolean asi) {
        var row = entry(new EntryIdentity(owner, "levels", module, "level_" + level)); row.addProperty("level", level); row.addProperty("asi_available", asi); return row;
    }
    private static JsonObject fixed(EntryIdentity slot, RuleIdentity target) {
        var row = entry(slot); var grant = new JsonObject(); grant.addProperty("kind", "FIXED"); grant.add("target", jsonIdentity(target)); row.add("growth_grant", grant); return row;
    }
    private static JsonObject choice(EntryIdentity slot) {
        var row = entry(slot); var grant = new JsonObject(); grant.addProperty("kind", "CHOICE"); grant.add("candidates", jsonIdentity(SKILL_SET)); grant.addProperty("count", 1); row.add("growth_grant", grant); return row;
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
