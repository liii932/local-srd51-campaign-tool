package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.GrowthChoices;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2InitialCharacterPlanService;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2InitialProficiencyRules;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Verified finite application rule material. This is not a complete-SRD release or installer. */
public final class BundledCampaignRules {
    public static final String CORE_MODULE = "srd51_runtime";
    public static final String CHARACTER_MODULE = "srd51_characters";
    public static final String MAGIC_MODULE = "srd51_magic";
    public static final String HOUSE_RULE_MODULE = "dm_runtime_house_rules";
    private static final List<String> BUNDLES = List.of("srd51-runtime", "srd51-characters",
            "srd51-magic", "dm-runtime-house-rules");

    private BundledCampaignRules() { }

    public static ProductionSchema2Projection projection() { return Material.PROJECTION; }
    public static List<ProductionRuleArtifact> artifacts() { return Material.ARTIFACTS; }

    /** Resolves the complete identity from the verified catalog, including its original module. */
    public static RuleIdentity rule(String type, String key) {
        RuleIdentity result = Material.IDENTITIES.get(type + "/" + key);
        if (result == null) throw new IllegalArgumentException("STORAGE_RULE_UNAVAILABLE");
        return result;
    }

    public static Metadata metadata() { return Material.METADATA; }

    /** A bounded default plan; it creates no rows, rolls or execution capability. */
    public static InitialPlan initialPlan(UUID campaignId, UUID characterId, Identity preparationIdentity,
            String classKey, String backgroundKey) {
        String chosenClass = classKey(classKey);
        String background = backgroundKey(backgroundKey);
        RuleIdentity human = rule("character.race", "race.human");
        RuleIdentity backgroundSource = rule("character.background", "background." + background);
        Map<EntryIdentity, List<RuleIdentity>> choices = new LinkedHashMap<>();
        choices.put(slot(human, "proficiency_grants", "extra_language"),
                List.of(rule("character.language", "language.elvish")));
        if (background.equals("acolyte")) choices.put(slot(backgroundSource, "proficiency_grants", "languages"),
                List.of(rule("character.language", "language.dwarvish"),
                        rule("character.language", "language.gnomish")));
        var sources = new Schema2InitialProficiencyRules.Sources(human, null, backgroundSource,
                rule("character.class", "class." + chosenClass));
        var prepared = new Schema2InitialCharacterPlanService().prepareMaterial(preparationIdentity,
                Schema2RulesBinding.from(projection()), projection(),
                new Schema2InitialProficiencyRules.Request(sources, choices));
        return new InitialPlan(campaignId, characterId, prepared);
    }

    /** Exact class skill slots and the required first-level Life-domain selection. */
    public static GrowthChoices initialChoices(String classKey) {
        String chosen = classKey(classKey);
        List<String> skills = switch (chosen) {
            case "fighter" -> List.of("athletics", "perception");
            case "cleric" -> List.of("history", "medicine");
            case "wizard" -> List.of("arcana", "investigation");
            default -> throw new IllegalArgumentException("STORAGE_CLASS_UNAVAILABLE");
        };
        RuleIdentity owner = rule("character.class_progression", "progression." + chosen);
        return new GrowthChoices(chosen.equals("cleric") ? rule("character.subclass", "subclass.life") : null,
                Map.of(), null, Map.of(slot(owner, "starting_proficiencies", "skills"),
                        skills.stream().map(skill -> rule("character.proficiency", "proficiency.skill." + skill)).toList()));
    }

    public static List<RuleIdentity> initialWizardBook() {
        return List.of("shield", "burning_hands", "alarm", "charm_person", "color_spray", "comprehend_languages")
                .stream().map(spell -> rule("adventure.spell", "spell." + spell)).toList();
    }

    public static List<RuleIdentity> initialWizardPrepared() {
        return List.of(rule("adventure.spell", "spell.shield"), rule("adventure.spell", "spell.burning_hands"));
    }

    private static EntryIdentity slot(RuleIdentity owner, String collection, String key) {
        return new EntryIdentity(owner, collection, owner.moduleKey(), key);
    }

    private static String classKey(String value) {
        String key = value != null && value.startsWith("class.") ? value.substring(6) : value;
        if (key == null || !List.of("fighter", "cleric", "wizard").contains(key))
            throw new IllegalArgumentException("STORAGE_CLASS_UNAVAILABLE");
        return key;
    }

    private static String backgroundKey(String value) {
        String key = value != null && value.startsWith("background.") ? value.substring(11) : value;
        if (key == null || !List.of("acolyte", "custom_artisan").contains(key))
            throw new IllegalArgumentException("STORAGE_BACKGROUND_UNAVAILABLE");
        return key;
    }

    private static ProductionRuleArtifact load(String name) {
        try (InputStream source = BundledCampaignRules.class.getResourceAsStream("/storage/rules/" + name + ".zip")) {
            if (source == null) throw new IllegalStateException("STORAGE_BUNDLED_RULE_MISSING");
            byte[] bytes = source.readNBytes(ProductionRuleArtifact.MAX_ZIP_BYTES + 1);
            return ProductionRuleArtifact.read(bytes);
        } catch (IOException failure) {
            throw new IllegalStateException("STORAGE_BUNDLED_RULE_READ", failure);
        }
    }

    public record CatalogEntry(String moduleKey, String type, String key, String name,
                               boolean executable, String description) { }
    public record Metadata(String displayName, String scope, List<CatalogEntry> catalog,
                           List<String> limitations) {
        public Metadata { catalog = List.copyOf(catalog); limitations = List.copyOf(limitations); }
    }

    private static final class Material {
        private static final List<ProductionRuleArtifact> ARTIFACTS = BUNDLES.stream()
                .map(BundledCampaignRules::load).toList();
        private static final ProductionSchema2Projection PROJECTION = ProductionSchema2Projection.compose(ARTIFACTS);
        private static final Map<String, RuleIdentity> IDENTITIES = identities();
        private static final Metadata METADATA = metadata();

        private static Map<String, RuleIdentity> identities() {
            Map<String, RuleIdentity> result = new LinkedHashMap<>();
            for (FinalRule row : PROJECTION.projection().rules()) {
                RuleIdentity key = row.identity();
                if (result.put(key.ruleType() + "/" + key.ruleKey(), key) != null)
                    throw new IllegalStateException("STORAGE_BUNDLED_RULE_AMBIGUOUS");
            }
            return Map.copyOf(result);
        }

        private static Metadata metadata() {
            List<CatalogEntry> catalog = new ArrayList<>();
            for (FinalRule row : PROJECTION.projection().rules()) {
                Definition definition = row.definition();
                boolean executable = row.enabled()
                        && !(definition instanceof SpellDefinition spell && spell.algorithm().contains("_METADATA_"))
                        && !(definition instanceof FeatureDefinition feature && feature.executionMode() != FeatureExecution.AUTOMATIC)
                        && !(definition instanceof FeatDefinition)
                        && !(definition instanceof MonsterDefinition)
                        && !row.identity().ruleType().startsWith("catalog.");
                catalog.add(new CatalogEntry(row.identity().moduleKey(), row.identity().ruleType(),
                        row.identity().ruleKey(), definition.displayName(), executable, description(definition)));
            }
            return new Metadata("SRD 5.1 finite current-application rules", "FINITE_APPLICATION_SCHEMA_2", catalog, List.of(
                    "Standard-array application characters; racial ability increases and full racial traits are unavailable.",
                    "Fighter levels 1–6, Life-domain cleric level 1, Wizard levels 1–5; this is not complete SRD class support.",
                    "Background sources currently grant languages/tools, without background skills, features or starting gear.",
                    "Unimplemented fighter features, Evocation Savant and Sculpt Spells remain catalog-only OPTION metadata and are not acquired; Grappler is blocked acquisition metadata.",
                    "Only Bless, Cure Wounds, Shield, Counterspell and Burning Hands have bounded casting consumers; other spells are metadata.",
                    "Multiclass growth acquisition does not authorize combined-caster slot semantics or unsupported multiclass casting.",
                    "Only Hell Hound Fire Breath executes as a monster action; the antimagic area and direct vital effects are explicit DM house rules."));
        }
    }

    private static String description(Definition value) {
        return switch (value) {
            case LanguageDefinition d -> d.description();
            case ToolDefinition d -> d.description();
            case CharacterSourceDefinition d -> d.description();
            case ClassProgressionDefinition d -> d.description();
            case ResourceDefinition d -> d.description();
            case SubclassDefinition d -> d.description();
            case FeatureDefinition d -> d.description();
            case FeatDefinition d -> d.description();
            case ProficiencyDefinition d -> d.description();
            case RestDefinition d -> d.description();
            case CheckDefinition d -> d.description();
            case ItemDefinition d -> d.description();
            case HealthDefinition d -> d.description();
            case VitalEffectDefinition d -> d.description();
            case CombatDefinition d -> d.description();
            case WeaponAttackDefinition d -> d.description();
            case MovementDefinition d -> d.description();
            case SpellDefinition d -> d.description();
            case SpellcastingDefinition d -> d.description();
            case WizardSpellcastingDefinition d -> d.description();
            case EnvironmentDefinition d -> d.description();
            case MonsterDefinition d -> d.description();
            case LanguageSetDefinition ignored -> "Finite language candidates; grants retain their exact source slots.";
            case ToolSetDefinition ignored -> "Finite tool candidates; grants retain their exact source slots.";
            case ProficiencySetDefinition ignored -> "Finite class proficiency candidates; the growth consumer checks exact choices.";
        };
    }
}
