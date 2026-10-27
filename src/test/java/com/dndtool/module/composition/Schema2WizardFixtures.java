package com.dndtool.module.composition;

import com.dndtool.service.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatRules.Context;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;

/** Real bounded Wizard sources. Metadata-only spells never authorize their execution. */
public final class Schema2WizardFixtures {
    public static final RuleIdentity WIZARD=id("character.class","class.wizard"), CASTING=id("character.wizard_spellcasting","spellcasting.wizard");
    public static final RuleIdentity WIZARD_PROGRESSION=id("character.class_progression","progression.wizard"), SPELLCASTING=id("character.feature","feature.wizard_spellcasting");
    public static final RuleIdentity SLOT1=id("character.resource","resource.wizard.slot1"), SLOT2=id("character.resource","resource.wizard.slot2"), SLOT3=id("character.resource","resource.wizard.slot3");
    public static final RuleIdentity EVOCATION=id("character.subclass","subclass.evocation"), ENVIRONMENT=id("adventure.environment","environment.antimagic_zone");
    public static final RuleIdentity SHIELD=spell("shield"), COUNTERSPELL=spell("counterspell");
    public static final List<String> METADATA=List.of("alarm","burning_hands","charm_person","color_spray","comprehend_languages","detect_magic","disguise_self","expeditious_retreat","false_life","feather_fall","find_familiar","fog_cloud","grease","mage_armor","magic_missile","sleep","thunderwave");
    public static final List<RuleIdentity> INITIAL_BOOK=List.of(SHIELD,spell("alarm"),spell("burning_hands"),spell("charm_person"),spell("color_spray"),spell("comprehend_languages"));
    private Schema2WizardFixtures() { }
    public static RuleIdentity spell(String name){return id("adventure.spell","spell."+name);}
    private static RuleIdentity id(String type,String key){return new RuleIdentity(EXT,type,key);}
    public static List<Map<String,byte[]>> packages() {
        var files=Schema2CombatTestFixture.packages();var extension=files.get(1);
        JsonArray rows=Schema2GrantFixtures.json(extension.get("rules.json")).getAsJsonArray();
        rows.add(def(WIZARD,52));
        var progression=def(WIZARD_PROGRESSION,52);progression.add("owner_class",jsonIdentity(WIZARD));progression.addProperty("hit_die_sides",6);
        progression.addProperty("proficiency_bonus_profile","1-4:2,5-8:3,9-12:4,13-16:5,17-20:6");progression.addProperty("supported_maximum_level",5);
        progression.addProperty("advancement_scope","CHARACTER_ADVANCEMENT");var prerequisite=new JsonObject();prerequisite.add("all_of",new JsonArray());progression.add("multiclass_prerequisite",prerequisite);rows.add(progression);
        var evocation=def(EVOCATION,54);evocation.add("owner_class",jsonIdentity(WIZARD));evocation.addProperty("selection_level",2);rows.add(evocation);
        rows.add(feature(SPELLCASTING,WIZARD,1,"AUTOMATIC","SRD_2014_WIZARD_SPELLCASTING_V1"));
        rows.add(feature(id("character.feature","feature.arcane_recovery"),WIZARD,1,"BLOCKED","SRD_2014_WIZARD_ARCANE_RECOVERY_V1"));
        rows.add(feature(id("character.feature","feature.evocation_savant"),EVOCATION,2,"BLOCKED","SRD_2014_EVOCATION_SAVANT_V1"));
        rows.add(feature(id("character.feature","feature.sculpt_spells"),EVOCATION,2,"BLOCKED","SRD_2014_SCULPT_SPELLS_V1"));
        for(var pair:List.of(Map.entry(SLOT1,"1:2,2:3,3-20:4"),Map.entry(SLOT2,"3:2,4-20:3"),Map.entry(SLOT3,"5:2,6-20:3"))) {
            var resource=def(pair.getKey(),52);resource.add("owner_class",jsonIdentity(WIZARD));resource.addProperty("maximum_profile",pair.getValue());resource.addProperty("recovery_profile","1-20:LONG_REST");resource.addProperty("execution_mode","AUTOMATIC");rows.add(resource);
        }
        List<String> names=new ArrayList<>(List.of("shield","counterspell"));names.addAll(METADATA);
        for(String name:names) {
            String algorithm="SRD_2014_"+name.toUpperCase(Locale.ROOT)+(METADATA.contains(name)?"_METADATA_V1":"_V1");
            var row=def(spell(name),spellSourcePage(algorithm));row.addProperty("algorithm",algorithm);rows.add(row);
        }
        var casting=def(CASTING,53);casting.add("owner_class",jsonIdentity(WIZARD));casting.add("required_feature",jsonIdentity(SPELLCASTING));
        casting.add("slot_resource",jsonIdentity(SLOT1));casting.add("rank2_slot_resource",jsonIdentity(SLOT2));casting.add("rank3_slot_resource",jsonIdentity(SLOT3));
        casting.addProperty("ability_key","ability.intelligence");casting.addProperty("algorithm","SRD_2014_WIZARD_PREPARED_V1");rows.add(casting);
        var environment=def(ENVIRONMENT,0);environment.addProperty("algorithm","DM_ANTIMAGIC_ZONE_V1");rows.add(environment);
        extension.put("rules.json",bytes(rows.toString()));
        JsonArray entries=Schema2GrantFixtures.json(extension.get("growth.json")).getAsJsonArray();
        for(int level=1;level<=5;level++) {var row=entry(WIZARD_PROGRESSION,"levels","wizard_level_"+level);row.addProperty("level",level);row.addProperty("asi_available",level==4);entries.add(row);}
        for(String name:names) {var row=entry(CASTING,"spells",name);row.add("spell",jsonIdentity(spell(name)));entries.add(row);}
        extension.put("growth.json",bytes(entries.toString()));
        Map<String,byte[]> adjustment=new LinkedHashMap<>();
        adjustment.put(HEADER_PATH,bytes("""
                {"author_schema_version":2,"module_key":"wizard_adjustment","release_version":"1",
                 "package_display_name":"Finite Wizard provenance adjustment","role":"extension",
                 "dependencies":[{"module_key":"growth_extension","supported_versions":["1"]}],
                 "compatible_combinations":[{"growth_extension":"1"}],"load_order_constraints":[],
                 "files":[{"kind":"changes","path":"changes.json"}]}
                """));
        JsonArray changes=new JsonArray();var change=new JsonObject();change.addProperty("op","set_field");change.add("target",jsonIdentity(CASTING));change.addProperty("field","ability_key");change.addProperty("value","ability.intelligence");changes.add(change);
        adjustment.put("changes.json",bytes(changes.toString()));files.add(adjustment);return files;
    }
    public static List<ProductionRuleArtifact> artifacts(){return Schema2GrantFixtures.artifacts(packages());}
    public static ProductionSchema2Projection projection(){return ProductionSchema2Projection.compose(artifacts());}
    public static Context initialContext(ProductionSchema2Projection material,UUID campaign,UUID character) {
        Identity identity=Identity.random();
        var sources=new Schema2InitialProficiencyRules.Sources(new RuleIdentity(BASE,"character.race","race.human"),null,
                new RuleIdentity(BASE,"character.background","background.guard"),WIZARD);
        var prepared=new Schema2InitialCharacterPlanService().prepareMaterial(identity,Schema2RulesBinding.from(material),material,
                new Schema2InitialProficiencyRules.Request(sources,Map.of()));
        var plan=new Schema2InitialCharacterPlanService.InitialPlan(campaign,character,prepared);
        var abilities=Map.of("ability.strength",8,"ability.dexterity",14,"ability.constitution",13,
                "ability.intelligence",15,"ability.wisdom",12,"ability.charisma",10);
        State state=new Schema2CharacterProgressionRules().initial(material,plan,UUID.randomUUID(),abilities,GrowthChoices.EMPTY);
        return new Context(material,plan,state);
    }
    public static Context learnedContext(ProductionSchema2Projection material,UUID campaign,UUID character,int level) {
        if(level<1||level>5)throw new IllegalArgumentException("fixture Wizard level");
        Context context=initialContext(material,campaign,character);State state=context.character();
        var books=new Schema2WizardSpellbookRules();var growth=new Schema2CharacterProgressionRules();
        state=books.apply(material,state,Command.spellbook("SPELLBOOK_INITIALIZED",new SpellbookInput(CASTING,INITIAL_BOOK,List.of(SHIELD))));
        for(int next=2;next<=level;next++) {
            books.requireLearningCompleteBeforeAdvance(material,state);
            GrowthChoices choices=next==2?new GrowthChoices(EVOCATION,Map.of(),null,Map.of())
                    :next==4?new GrowthChoices(null,Map.of("ability.intelligence",2),null,Map.of()):GrowthChoices.EMPTY;
            state=growth.advance(material,growth.prepareAdvance(material,state,WIZARD,next,"FIXED_AVERAGE",choices),null);
            state=books.apply(material,state,Command.spellbook("SPELLBOOK_ADVANCED",new SpellbookInput(CASTING,learnedAt(next),state.spellbook().prepared())));
        }
        return new Context(material,context.initial(),state);
    }
    public static List<RuleIdentity> learnedAt(int level) {
        return switch(level) {
            case 1 -> INITIAL_BOOK;
            case 2 -> List.of(spell("detect_magic"),spell("disguise_self"));
            case 3 -> List.of(spell("expeditious_retreat"),spell("false_life"));
            case 4 -> List.of(spell("feather_fall"),spell("find_familiar"));
            case 5 -> List.of(COUNTERSPELL,spell("fog_cloud"));
            default -> throw new IllegalArgumentException("fixture learning level");
        };
    }
    public static State longRest(ProductionSchema2Projection material,State state) {
        var rest=new Schema2CharacterRestRules();
        state=rest.apply(material,state,restCommand("REST_STARTED",new RestInput("LONG_REST",null,0,0,Map.of())),null);
        state=rest.apply(material,state,restCommand("TIME_ADVANCED",new RestInput(null,"SLEEP",360,0,Map.of())),null);
        state=rest.apply(material,state,restCommand("TIME_ADVANCED",new RestInput(null,"LIGHT",120,0,Map.of())),null);
        return rest.apply(material,state,restCommand("REST_COMPLETED",new RestInput(null,null,0,0,Map.of())),null);
    }
    private static Command restCommand(String kind,RestInput input){return new Command(kind,null,0,null,null,null,GrowthChoices.EMPTY,input);}
    private static JsonObject def(RuleIdentity identity,int page){var r=new JsonObject();r.add("identity",jsonIdentity(identity));r.addProperty("display_name",identity.ruleKey());r.addProperty("description","Bounded Wizard source and closed spell metadata.");r.addProperty("source_page",page);return r;}
    private static JsonObject feature(RuleIdentity identity,RuleIdentity owner,int level,String mode,String algorithm){var r=def(identity,level==1?53:54);r.add("owner",jsonIdentity(owner));r.addProperty("level",level);r.addProperty("feature_kind","FEATURE");r.addProperty("execution_mode",mode);r.addProperty("execution_algorithm",algorithm);return r;}
    private static JsonObject entry(RuleIdentity owner,String collection,String key){var r=new JsonObject();var id=new JsonObject();id.addProperty("kind","entry");id.add("rule",jsonIdentity(owner));id.addProperty("collection_key",collection);id.addProperty("module_key",EXT);id.addProperty("entry_key",key);r.add("identity",id);return r;}
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
}
