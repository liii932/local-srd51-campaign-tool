package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime.Event;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2InitialCharacterPlanService;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2InitialProficiencyRules;
import com.dndtool.service.Schema2PreparationIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Read-only rule consumers share this verified campaign binding and frozen projection. */
public final class CampaignRuleContext {
    public static final String INTERPRETATION = "DND_CAMPAIGN_SCHEMA_2_V2";
    static final List<String> INITIAL_CLASSES = List.of("fighter", "cleric", "wizard");
    static final List<String> INITIAL_BACKGROUNDS = List.of("acolyte", "custom_artisan");
    private final ProductionSchema2Projection projection;
    private final Schema2RulesBinding binding;
    private final RuleIdentity checkRule;

    CampaignRuleContext(ProductionSchema2Projection projection, RuleIdentity checkRule) {
        this.projection = projection;
        this.binding = Schema2RulesBinding.from(projection);
        this.checkRule = checkRule;
    }

    public String interpretation() { return INTERPRETATION; }
    public Schema2RulesBinding binding() { return binding; }
    public ProductionSchema2Projection projection() { return projection; }
    public RuleIdentity checkRule() { return checkRule; }

    /** Resolves an application template alias to one enabled, fully qualified rule. */
    public RuleIdentity rule(String type, String key) {
        var matches = projection.projection().rules().stream().filter(FinalRule::enabled)
                .map(FinalRule::identity).filter(id -> id.ruleType().equals(type) && id.ruleKey().equals(key)).toList();
        if (matches.isEmpty()) throw new IllegalArgumentException("STORAGE_RULE_UNAVAILABLE");
        if (matches.size() != 1) throw new IllegalArgumentException("STORAGE_RULE_AMBIGUOUS");
        return matches.getFirst();
    }

    /** A bounded default plan; it creates no rows, rolls or execution capability. */
    public InitialPlan initialPlan(UUID campaignId, UUID characterId, Schema2PreparationIdentity preparationIdentity,
            String classKey, String backgroundKey) {
        var prepared = new Schema2InitialCharacterPlanService().prepareMaterial(preparationIdentity,
                binding, projection, initialRequest(classKey, backgroundKey));
        return new InitialPlan(campaignId, characterId, prepared);
    }

    private Schema2InitialProficiencyRules.Request initialRequest(String classKey, String backgroundKey) {
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
        return new Schema2InitialProficiencyRules.Request(sources, choices);
    }

    /** Exact class skill slots and the required first-level Life-domain selection. */
    public GrowthChoices initialChoices(String classKey) {
        String chosen = classKey(classKey);
        List<String> skills = switch (chosen) {
            case "fighter" -> List.of("athletics", "perception");
            case "cleric" -> List.of("history", "medicine");
            case "wizard" -> List.of("arcana", "investigation");
            default -> throw new IllegalArgumentException("STORAGE_CLASS_UNAVAILABLE");
        };
        RuleIdentity startingClass = rule("character.class", "class." + chosen);
        RuleIdentity owner = projection.projection().rules().stream().filter(FinalClassProgression.class::isInstance)
                .map(FinalClassProgression.class::cast).filter(FinalClassProgression::enabled)
                .filter(row -> row.definition().ownerClass().equals(startingClass)).map(FinalRule::identity)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("STORAGE_RULE_UNAVAILABLE"));
        return new GrowthChoices(chosen.equals("cleric") ? rule("character.subclass", "subclass.life") : null,
                Map.of(), null, Map.of(slot(owner, "starting_proficiencies", "skills"),
                        skills.stream().map(skill -> rule("character.proficiency", "proficiency.skill." + skill)).toList()));
    }

    public List<RuleIdentity> initialWizardBook() {
        return List.of("shield", "burning_hands", "alarm", "charm_person", "color_spray", "comprehend_languages")
                .stream().map(spell -> rule("adventure.spell", "spell." + spell)).toList();
    }

    public List<RuleIdentity> initialWizardPrepared() {
        return List.of(rule("adventure.spell", "spell.shield"), rule("adventure.spell", "spell.burning_hands"));
    }

    public Map<String, Integer> initialAbilities(String classKey) {
        return switch (classKey(classKey)) {
            case "fighter" -> Map.of("ability.strength", 15, "ability.dexterity", 13, "ability.constitution", 14,
                    "ability.intelligence", 8, "ability.wisdom", 12, "ability.charisma", 10);
            case "cleric" -> Map.of("ability.strength", 13, "ability.dexterity", 10, "ability.constitution", 14,
                    "ability.intelligence", 8, "ability.wisdom", 15, "ability.charisma", 12);
            case "wizard" -> Map.of("ability.strength", 8, "ability.dexterity", 14, "ability.constitution", 13,
                    "ability.intelligence", 15, "ability.wisdom", 12, "ability.charisma", 10);
            default -> throw new IllegalArgumentException("STORAGE_CLASS_UNAVAILABLE");
        };
    }

    void validateInitialTemplate(InitialPlan retained, Event initialized) {
        var sources = retained.character().sources();
        String characterClass = sources.characterClass().ruleKey();
        var expected = initialRequest(characterClass, sources.background().ruleKey());
        // The domain engine independently validates the plan's grants, slots and binding.
        if (!expected.sources().equals(sources) || !expected.choices().equals(retained.character().choices())
                || !"INITIALIZED".equals(initialized.kind())
                || !initialAbilities(characterClass).equals(initialized.after().abilities())
                || !initialChoices(characterClass).equals(initialized.command().growthChoices()))
            throw new IllegalArgumentException("STORAGE_INITIAL_TEMPLATE_MISMATCH");
    }

    private EntryIdentity slot(RuleIdentity owner, String collection, String key) {
        var matches = projection.projection().entries().stream().filter(FinalEntry::enabled)
                .map(row -> row.entry().identity()).filter(id -> id.rule().equals(owner)
                        && id.collectionKey().equals(collection) && id.entryKey().equals(key)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("STORAGE_INITIAL_SLOT_UNAVAILABLE");
        return matches.getFirst();
    }

    private static String classKey(String value) {
        String key = value != null && value.startsWith("class.") ? value.substring(6) : value;
        if (key == null || !INITIAL_CLASSES.contains(key))
            throw new IllegalArgumentException("STORAGE_CLASS_UNAVAILABLE");
        return key;
    }

    private static String backgroundKey(String value) {
        String key = value != null && value.startsWith("background.") ? value.substring(11) : value;
        if (key == null || !INITIAL_BACKGROUNDS.contains(key))
            throw new IllegalArgumentException("STORAGE_BACKGROUND_UNAVAILABLE");
        return key;
    }

    public record FieldOverride(String field, String moduleKey, String releaseVersion,
                                String collection, String entryKey) { }
    public record CatalogEntry(String moduleKey, String type, String key, String name, boolean enabled,
                               boolean executable, String description, String abilityKey, String category,
                               boolean creationChoice, boolean spellbookChoice,
                               List<FieldOverride> overrides) {
        public CatalogEntry { overrides = List.copyOf(overrides); }
    }
    public record Metadata(String displayName, String scope, String interpretation, Schema2RulesBinding binding,
                           List<CatalogEntry> catalog, List<String> limitations) {
        public Metadata { catalog = List.copyOf(catalog); limitations = List.copyOf(limitations); }
    }

    public Metadata metadata() {
        Map<RuleIdentity, List<FieldOverride>> overrides = new LinkedHashMap<>();
        for (FieldChain chain : projection.projection().chains()) {
            if (chain.steps().size() == 1) continue;
            var provider = chain.steps().getLast().provider();
            EntryIdentity entry = chain.target() instanceof EntryIdentity target ? target : null;
            RuleIdentity owner = entry == null ? (RuleIdentity) chain.target() : entry.rule();
            overrides.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new FieldOverride(
                    chain.field().key(), provider.moduleKey(), provider.releaseVersion(),
                    entry == null ? null : entry.collectionKey(), entry == null ? null : entry.entryKey()));
        }
        Set<RuleIdentity> wizardProfiles = projection.projection().rules().stream()
                .filter(row -> row.enabled() && row instanceof FinalWizardSpellcasting)
                .map(FinalRule::identity).collect(Collectors.toUnmodifiableSet());
        Set<RuleIdentity> spellbookChoices = projection.projection().entries().stream().filter(FinalEntry::enabled)
                .map(FinalEntry::entry).filter(SpellbookEntry.class::isInstance).map(SpellbookEntry.class::cast)
                .filter(entry -> wizardProfiles.contains(entry.identity().rule())).map(SpellbookEntry::spell)
                .collect(Collectors.toUnmodifiableSet());
        List<CatalogEntry> catalog = new ArrayList<>();
        for (FinalRule row : projection.projection().rules()) {
            Definition definition = row.definition();
            boolean creationChoice = row.enabled() && switch (row.identity().ruleType()) {
                case "character.class" -> INITIAL_CLASSES.stream().anyMatch(key -> row.identity().ruleKey().equals("class." + key));
                case "character.background" -> INITIAL_BACKGROUNDS.stream().anyMatch(key -> row.identity().ruleKey().equals("background." + key));
                default -> false;
            };
            boolean spellbookChoice = row.enabled() && spellbookChoices.contains(row.identity());
            boolean executable = row.enabled()
                    && !(definition instanceof SpellDefinition spell && spell.algorithm().contains("_METADATA_"))
                    && !(definition instanceof FeatureDefinition feature && (feature.kind() != FeatureKind.FEATURE
                            || feature.executionMode() != FeatureExecution.AUTOMATIC))
                    && !(definition instanceof ResourceDefinition resource && resource.executionMode() == ResourceExecution.BLOCKED)
                    && !(definition instanceof FeatDefinition)
                    && !(Set.of("character.class", "character.background").contains(row.identity().ruleType()) && !creationChoice)
                    && !row.identity().ruleType().startsWith("catalog.");
            var proficiency = definition instanceof ProficiencyDefinition value ? value : null;
            catalog.add(new CatalogEntry(row.identity().moduleKey(), row.identity().ruleType(),
                    row.identity().ruleKey(), definition.displayName(), row.enabled(), executable, description(definition),
                    proficiency == null ? null : proficiency.abilityKey(), proficiency == null ? null : proficiency.category().name(),
                    creationChoice, spellbookChoice,
                    overrides.getOrDefault(row.identity(), List.of())));
        }
        return new Metadata("SRD 5.1 finite current-application rules", "FINITE_APPLICATION_SCHEMA_2",
                interpretation(), binding, catalog, List.of(
                    "Standard-array application characters; racial ability increases and full racial traits are unavailable.",
                    "Fighter levels 1–6, Life-domain cleric level 1, Wizard levels 1–5; this is not complete SRD class support.",
                    "Fighter Extra Attack remains catalog-only metadata; an Attack action allows one supported melee attack.",
                    "Background sources currently grant languages/tools, without background skills, features or starting gear.",
                    "Unimplemented fighter features, Evocation Savant and Sculpt Spells remain catalog-only OPTION metadata and are not acquired; Grappler is blocked acquisition metadata.",
                    "Only Bless, Cure Wounds, Shield, Counterspell and Burning Hands have bounded casting consumers; other spells are metadata.",
                    "Multiclass growth acquisition does not authorize combined-caster slot semantics or unsupported multiclass casting.",
                    "Only Hell Hound Fire Breath executes as a monster action; the antimagic area and direct vital effects are explicit DM house rules."));
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
