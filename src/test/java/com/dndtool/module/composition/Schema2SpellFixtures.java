package com.dndtool.module.composition;

import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.GrowthChoices;
import com.dndtool.service.Schema2CombatTestFixture;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;

/** Real author ZIP material for the bounded Life domain level-one internal spell consumer. */
public final class Schema2SpellFixtures {
    public static final RuleIdentity CASTING=new RuleIdentity(BASE,"character.spellcasting","spellcasting.life_domain");
    public static final RuleIdentity BLESS=new RuleIdentity(BASE,"adventure.spell","spell.bless");
    public static final RuleIdentity CURE_WOUNDS=new RuleIdentity(BASE,"adventure.spell","spell.cure_wounds");
    public static final RuleIdentity SLOT_RESOURCE=new RuleIdentity(BASE,"character.resource","resource.spell_slot.rank1");
    public static final RuleIdentity HOLY_SYMBOL=new RuleIdentity(BASE,"equipment.item","item.holy_symbol");
    public static final RuleIdentity LIFE_SUBCLASS=SUBCLASS;
    public static final RuleIdentity LIFE_FEATURE=new RuleIdentity(BASE,"character.feature","feature.life_domain_spellcasting");
    private Schema2SpellFixtures() { }
    public static GrowthChoices initialChoices() {
        return new GrowthChoices(LIFE_SUBCLASS,Map.of(),null,Map.of(INITIAL_SKILL_SLOT,List.of(ARCANA)));
    }
    public static List<Map<String,byte[]>> packages() {
        var files=Schema2CombatTestFixture.packages();
        JsonArray rows=Schema2GrantFixtures.json(files.getFirst().get("rules.json")).getAsJsonArray();
        for (JsonElement element:rows) {
            var row=element.getAsJsonObject();var id=row.getAsJsonObject("identity");
            if (id.get("rule_type").getAsString().equals("character.class")&&id.get("rule_key").getAsString().equals(CLASS.ruleKey())) {
                row.addProperty("display_name","Cleric (bounded Life domain level one)");row.addProperty("source_page",15);
            }
            if (id.get("rule_type").getAsString().equals("character.subclass")&&id.get("rule_key").getAsString().equals(LIFE_SUBCLASS.ruleKey())) {
                row.addProperty("display_name","Life Domain");row.addProperty("source_page",17);row.addProperty("selection_level",1);
            }
            if (id.get("rule_type").getAsString().equals("character.class_progression")&&id.get("rule_key").getAsString().equals(PROGRESSION.ruleKey())) row.addProperty("hit_die_sides",8);
        }
        var slots=definition(SLOT_RESOURCE,16);slots.add("owner_class",jsonIdentity(CLASS));slots.addProperty("maximum_profile","1-20:2");
        slots.addProperty("recovery_profile","1-20:LONG_REST");slots.addProperty("execution_mode","AUTOMATIC");rows.add(slots);
        var feature=definition(LIFE_FEATURE,17);feature.add("owner",jsonIdentity(LIFE_SUBCLASS));feature.addProperty("level",1);
        feature.addProperty("feature_kind","FEATURE");feature.addProperty("execution_mode","AUTOMATIC");feature.addProperty("execution_algorithm","LIFE_DOMAIN_LEVEL1_V1");rows.add(feature);
        var symbol=definition(HOLY_SYMBOL,70);symbol.addProperty("kind","HOLY_SYMBOL");symbol.addProperty("armor_class",0);
        symbol.addProperty("strength_minimum",0);symbol.addProperty("stealth_disadvantage",false);symbol.addProperty("hands",1);
        symbol.addProperty("algorithm","SRD_2014_EQUIPMENT_V1");rows.add(symbol);
        var bless=definition(BLESS,122);bless.addProperty("algorithm","SRD_2014_BLESS_V1");rows.add(bless);
        var cure=definition(CURE_WOUNDS,132);cure.addProperty("algorithm","SRD_2014_CURE_WOUNDS_V1");rows.add(cure);
        var casting=definition(CASTING,17);casting.addProperty("algorithm","LIFE_DOMAIN_LEVEL1_V1");casting.addProperty("ability_key","ability.wisdom");
        casting.add("owner_class",jsonIdentity(CLASS));casting.add("required_subclass",jsonIdentity(LIFE_SUBCLASS));casting.add("required_feature",jsonIdentity(LIFE_FEATURE));
        casting.add("slot_resource",jsonIdentity(SLOT_RESOURCE));casting.add("bless",jsonIdentity(BLESS));casting.add("cure_wounds",jsonIdentity(CURE_WOUNDS));
        casting.add("material_item",jsonIdentity(HOLY_SYMBOL));rows.add(casting);
        files.getFirst().put("rules.json",rows.toString().getBytes(StandardCharsets.UTF_8));
        JsonArray changes=Schema2GrantFixtures.json(files.get(1).get("changes.json")).getAsJsonArray();
        var change=new JsonObject();change.addProperty("op","set_field");change.add("target",jsonIdentity(CASTING));
        change.addProperty("field","material_item");change.add("value",jsonIdentity(HOLY_SYMBOL));changes.add(change);
        var note=new JsonObject();note.addProperty("op","set_field");note.add("target",jsonIdentity(BLESS));
        note.addProperty("field","author_note");note.addProperty("value","Explicit supported spell note.");changes.add(note);
        files.get(1).put("changes.json",changes.toString().getBytes(StandardCharsets.UTF_8));return files;
    }
    public static List<ProductionRuleArtifact> artifacts() {return Schema2GrantFixtures.artifacts(packages());}
    public static ProductionSchema2Projection projection() {return ProductionSchema2Projection.compose(artifacts());}
    private static JsonObject definition(RuleIdentity identity,int page) {
        var row=new JsonObject();row.add("identity",jsonIdentity(identity));row.addProperty("display_name",identity.ruleKey());
        row.addProperty("description","Finite Life domain level-one author source.");row.addProperty("source_page",page);return row;
    }
}
