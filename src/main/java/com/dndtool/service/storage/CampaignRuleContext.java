package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2FeatureSupport;
import com.dndtool.service.Schema2InitialCharacterPlanService;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2InitialProficiencyRules;
import com.dndtool.service.Schema2KnownCantripRules;
import com.dndtool.service.Schema2PreparationIdentity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Read-only rule consumers share this verified campaign binding and frozen projection. */
public final class CampaignRuleContext {
    public static final String INTERPRETATION = "DND_CAMPAIGN_SCHEMA_2_V6";
    static final List<String> INITIAL_CLASSES = List.of("fighter", "cleric", "wizard");
    static final List<String> INITIAL_BACKGROUNDS = List.of("acolyte", "custom_artisan");
    private final ProductionSchema2Projection projection;
    private final Schema2RulesBinding binding;
    private final CheckDefinition checkDefinition;
    record CreationAncestry(RuleIdentity race, RuleIdentity subrace) { }
    private final List<CreationAncestry> creationAncestries;

    CampaignRuleContext(ProductionSchema2Projection projection, CheckDefinition checkDefinition) {
        this.projection = projection;
        this.binding = Schema2RulesBinding.from(projection);
        this.checkDefinition = checkDefinition;
        var ancestries = new ArrayList<CreationAncestry>();
        ancestries.add(new CreationAncestry(rule("character.race", "race.human"), null));
        var dwarf = optionalSource("character.race", "race.dwarf");
        var hill = optionalSource("character.subrace", "subrace.hill_dwarf");
        if (dwarf != null) ancestries.add(new CreationAncestry(dwarf.identity(), null));
        if (hill != null) {
            if (dwarf == null || !dwarf.identity().equals(hill.parentRace()))
                throw new IllegalArgumentException("S2_INITIAL_SUBRACE_PARENT");
            ancestries.add(new CreationAncestry(dwarf.identity(), hill.identity()));
        }
        this.creationAncestries = List.copyOf(ancestries);
    }

    public String interpretation() { return INTERPRETATION; }
    public Schema2RulesBinding binding() { return binding; }
    public ProductionSchema2Projection projection() { return projection; }
    public RuleIdentity checkRule() { return checkDefinition.identity(); }
    CheckDefinition checkDefinition() { return checkDefinition; }
    List<CreationAncestry> creationAncestries() { return creationAncestries; }

    private CharacterSourceDefinition optionalSource(String type, String key) {
        var matches = projection.projection().rules().stream()
                .filter(FinalCharacterSource.class::isInstance).map(FinalCharacterSource.class::cast)
                .filter(row -> row.enabled() && row.identity().ruleType().equals(type) && row.identity().ruleKey().equals(key))
                .map(FinalCharacterSource::definition).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("STORAGE_RULE_AMBIGUOUS");
        return matches.isEmpty() ? null : matches.getFirst();
    }

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
        return initialPlan(campaignId, characterId, preparationIdentity, classKey, backgroundKey, "human");
    }

    public InitialPlan initialPlan(UUID campaignId, UUID characterId, Schema2PreparationIdentity preparationIdentity,
            String classKey, String backgroundKey, String raceKey) {
        return initialPlan(campaignId, characterId, preparationIdentity, classKey, backgroundKey, raceKey, null);
    }

    public InitialPlan initialPlan(UUID campaignId, UUID characterId, Schema2PreparationIdentity preparationIdentity,
            String classKey, String backgroundKey, String raceKey, String subraceKey) {
        String race = raceKey != null && raceKey.startsWith("race.") ? raceKey : "race." + raceKey;
        String subrace = subraceKey == null || subraceKey.startsWith("subrace.") ? subraceKey : "subrace." + subraceKey;
        var ancestry = creationAncestries.stream().filter(candidate -> candidate.race().ruleKey().equals(race)
                && java.util.Objects.equals(candidate.subrace() == null ? null : candidate.subrace().ruleKey(), subrace))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        subrace == null ? "STORAGE_RACE_UNAVAILABLE" : "STORAGE_SUBRACE_UNAVAILABLE"));
        var prepared = new Schema2InitialCharacterPlanService().prepareMaterial(preparationIdentity,
                binding, projection, initialRequest(classKey, backgroundKey, ancestry));
        return new InitialPlan(campaignId, characterId, prepared);
    }

    private Schema2InitialProficiencyRules.Request initialRequest(String classKey, String backgroundKey, CreationAncestry ancestry) {
        RuleIdentity race = ancestry.race();
        String chosenClass = classKey(classKey);
        String background = backgroundKey(backgroundKey);
        boolean dwarf = race.ruleKey().equals("race.dwarf");
        RuleIdentity backgroundSource = rule("character.background", "background." + background);
        Map<EntryIdentity, List<RuleIdentity>> choices = new LinkedHashMap<>();
        if (dwarf) choices.put(slot(race, "proficiency_grants", "artisan_tool"),
                List.of(rule("character.tool", "tool.mason_tools")));
        else choices.put(slot(race, "proficiency_grants", "extra_language"),
                List.of(rule("character.language", "language.elvish")));
        if (background.equals("acolyte")) choices.put(slot(backgroundSource, "proficiency_grants", "languages"),
                List.of(rule("character.language", dwarf ? "language.elvish" : "language.dwarvish"),
                        rule("character.language", "language.gnomish")));
        var sources = new Schema2InitialProficiencyRules.Sources(race, ancestry.subrace(), backgroundSource,
                rule("character.class", "class." + chosenClass));
        return new Schema2InitialProficiencyRules.Request(sources, choices);
    }

    /** Build the explicit choices for the default programmatic character template. */
    public GrowthChoices initialChoices(String classKey) {
        return initialChoices(classKey, null);
    }

    /** A supplied list names only spells. The selected class supplies the authoritative source. */
    public GrowthChoices initialChoices(String classKey, List<String> cantripKeys) {
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
                        skills.stream().map(skill -> rule("character.proficiency", "proficiency.skill." + skill)).toList()),
                initialCantrips(startingClass, cantripKeys))
                .withFightingStyle(Schema2FeatureSupport.fightingStyleOption(projection, startingClass));
    }

    /** A Host creation request must name the enabled style selected for this class. */
    GrowthChoices creationChoices(String classKey, List<String> cantripKeys, String fightingStyleKey) {
        GrowthChoices choices = initialChoices(classKey, cantripKeys);
        RuleIdentity style = choices.fightingStyle();
        if (!Objects.equals(fightingStyleKey, style == null ? null : style.ruleKey()))
            throw new IllegalArgumentException("S2_FIGHTING_STYLE_CHOICE");
        return choices;
    }

    private List<RuleIdentity> initialCantrips(RuleIdentity owner, List<String> keys) {
        var profile = projection.classCantrips(owner).orElse(null);
        if (keys == null) return profile == null ? List.of() : Schema2KnownCantripRules.initialDefaults(profile);
        int count = profile == null ? 0 : profile.definition().knownCount(1);
        if (keys.size() != count || new HashSet<>(keys).size() != count)
            throw new IllegalArgumentException("S2_KNOWN_CANTRIP_CHOICES");
        if (count == 0) return List.of();
        return keys.stream().map(key -> {
            var matches = profile.options().stream().map(SpellDefinition::identity)
                    .filter(spell -> spell.ruleKey().equals(key)).toList();
            if (matches.size() != 1) throw new IllegalArgumentException("S2_KNOWN_CANTRIP_CHOICES");
            return matches.getFirst();
        }).toList();
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
    public record MonsterAttackChoice(EntryIdentity action, String name, int reachFeet, int normalRangeFeet, int longRangeFeet) { }
    public record CatalogEntry(String moduleKey, String type, String key, String name, boolean enabled,
                               boolean executable, String description, String abilityKey, String category,
                               Boolean allowAlternateSkillAbility,
                               RuleIdentity parentRace, RuleIdentity fightingStyleClass,
                               boolean creationChoice, boolean spellbookChoice, boolean directResourceSpend,
                               List<String> cantripClasses, List<String> defaultCantripClasses, List<Integer> cantripCounts,
                               List<FieldOverride> overrides, List<MonsterAttackChoice> monsterAttacks, boolean nimbleEscape) {
        public CatalogEntry {
            cantripClasses = List.copyOf(cantripClasses);
            defaultCantripClasses = List.copyOf(defaultCantripClasses);
            cantripCounts = List.copyOf(cantripCounts);
            overrides = List.copyOf(overrides);
            monsterAttacks = List.copyOf(monsterAttacks);
        }
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
        Map<RuleIdentity, List<String>> cantripClasses = new LinkedHashMap<>();
        Map<RuleIdentity, List<String>> defaultCantripClasses = new LinkedHashMap<>();
        Map<RuleIdentity, List<Integer>> cantripCounts = new LinkedHashMap<>();
        var classProgressions = projection.projection().rules().stream().filter(FinalRule::enabled)
                .filter(FinalClassProgression.class::isInstance).map(FinalClassProgression.class::cast)
                .map(FinalClassProgression::definition).collect(Collectors.toUnmodifiableMap(
                        ClassProgressionDefinition::ownerClass, ClassProgressionDefinition::supportedMaximumLevel));
        for (String key : INITIAL_CLASSES) {
            var owner = rule("character.class", "class." + key);
            var profile = projection.classCantrips(owner).orElse(null);
            cantripCounts.put(owner, IntStream.rangeClosed(1, classProgressions.get(owner))
                    .mapToObj(level -> profile == null ? 0 : profile.definition().knownCount(level)).toList());
            if (profile == null) continue;
            for (SpellDefinition option : profile.options())
                cantripClasses.computeIfAbsent(option.identity(), ignored -> new ArrayList<>()).add(owner.ruleKey());
            for (RuleIdentity spell : Schema2KnownCantripRules.initialDefaults(profile))
                defaultCantripClasses.computeIfAbsent(spell, ignored -> new ArrayList<>()).add(owner.ruleKey());
        }
        Set<RuleIdentity> secondWindResources = projection.projection().rules().stream()
                .filter(FinalRule::enabled).filter(FinalFeature.class::isInstance).map(FinalFeature.class::cast)
                .filter(row -> FIGHTER_SECOND_WIND_ALGORITHM.equals(row.definition().executionAlgorithm()))
                .map(row -> fighterSecondWindResource(row.definition().owner())).collect(Collectors.toUnmodifiableSet());
        Map<RuleIdentity, List<MonsterAttackChoice>> monsterAttacks = new LinkedHashMap<>();
        for (FinalEntry row : projection.projection().entries()) {
            if (row.enabled() && row.entry() instanceof MonsterActionEntry action && action.actionProfile().attackProfile() != null) {
                var profile = action.actionProfile().attackProfile();
                monsterAttacks.computeIfAbsent(action.identity().rule(), ignored -> new ArrayList<>())
                        .add(new MonsterAttackChoice(action.identity(), action.actionProfile().displayName(), profile.reachFeet(),
                                profile.normalRangeFeet(), profile.longRangeFeet()));
            }
        }
        List<CatalogEntry> catalog = new ArrayList<>();
        var fighter = rule("character.class", "class.fighter");
        var defense = Schema2FeatureSupport.fightingStyleOption(projection, fighter);
        var surgeResource = fighterActionSurgeResource(fighter);
        for (FinalRule row : projection.projection().rules()) {
            Definition definition = row.definition();
            boolean fightingStyle = row.identity().equals(defense);
            boolean creationChoice = row.enabled() && switch (row.identity().ruleType()) {
                case "character.race" -> creationAncestries.stream().anyMatch(a -> row.identity().equals(a.race()));
                case "character.subrace" -> creationAncestries.stream().anyMatch(a -> row.identity().equals(a.subrace()));
                case "character.class" -> INITIAL_CLASSES.stream().anyMatch(key -> row.identity().ruleKey().equals("class." + key));
                case "character.background" -> INITIAL_BACKGROUNDS.stream().anyMatch(key -> row.identity().ruleKey().equals("background." + key));
                case "character.feature" -> fightingStyle;
                default -> false;
            };
            boolean spellbookChoice = row.enabled() && spellbookChoices.contains(row.identity());
            boolean executable = row.enabled()
                    && !(definition instanceof SpellDefinition spell && spell.algorithm().contains("_METADATA_"))
                    && !(definition instanceof FeatureDefinition feature && (feature.kind() != FeatureKind.FEATURE
                            || feature.executionMode() != FeatureExecution.AUTOMATIC && !fightingStyle))
                    && !(definition instanceof ResourceDefinition resource && resource.executionMode() == ResourceExecution.BLOCKED)
                    && !(definition instanceof FeatDefinition)
                    && !(Set.of("character.race", "character.subrace", "character.class", "character.background").contains(row.identity().ruleType()) && !creationChoice)
                    && !row.identity().ruleType().startsWith("catalog.");
            var proficiency = definition instanceof ProficiencyDefinition value ? value : null;
            boolean directResourceSpend = executable && definition instanceof ResourceDefinition
                    && !secondWindResources.contains(row.identity()) && !surgeResource.equals(row.identity());
            catalog.add(new CatalogEntry(row.identity().moduleKey(), row.identity().ruleType(),
                    row.identity().ruleKey(), definition.displayName(), row.enabled(), executable, description(definition),
                    proficiency == null ? null : proficiency.abilityKey(), proficiency == null ? null : proficiency.category().name(),
                    definition instanceof CheckDefinition check ? check.allowAlternateSkillAbility() : null,
                    definition instanceof CharacterSourceDefinition source ? source.parentRace() : null,
                    fightingStyle ? fighter : null, creationChoice, spellbookChoice, directResourceSpend,
                    cantripClasses.getOrDefault(row.identity(), List.of()),
                    defaultCantripClasses.getOrDefault(row.identity(), List.of()),
                    cantripCounts.getOrDefault(row.identity(), List.of()),
                    overrides.getOrDefault(row.identity(), List.of()),
                    executable ? monsterAttacks.getOrDefault(row.identity(), List.of()) : List.of(),
                    executable && definition instanceof MonsterDefinition monster
                            && monster.statBlock().traits().stream().anyMatch(trait -> trait.isNimbleEscape(row.identity()))));
        }
        return new Metadata("SRD 5.1 finite current-application rules", "FINITE_APPLICATION_SCHEMA_2",
                interpretation(), binding, catalog, List.of(
                    "Standard-array application characters; racial ability increases and full racial traits are unavailable.",
                    "Fighter levels 1–6, Life-domain cleric level 1, Wizard levels 1–5; this is not complete SRD class support.",
                    "A fighter choosing Defense gains +1 AC while wearing armor. From level 2, Action Surge grants one additional action on the fighter's turn per completed short or long rest.",
                    "An acquired Fighter Extra Attack allows up to two weapon attacks within the Attack action on that fighter's turn.",
                    "An acquired Fighter Second Wind spends its own use as a bonus action on that fighter's active turn and heals 1d10 plus Fighter level.",
                    "Background sources currently grant languages/tools, without background skills, features or starting gear.",
                    "Unimplemented fighter features, Evocation Savant and Sculpt Spells remain catalog-only OPTION metadata and are not acquired; Grappler is blocked acquisition metadata.",
                    "Fire Bolt and Sacred Flame are executable class cantrips. Other cantrip options are recorded as known.",
                    "Bless, Cure Wounds, Shield, Counterspell and Burning Hands have bounded leveled casting consumers.",
                    "Multiclass growth acquisition does not authorize combined-caster slot semantics or unsupported multiclass casting.",
                    "Wolf and Hell Hound bites, Hell Hound Fire Breath, Skeleton and Goblin weapons use frozen monster actions. A Goblin with Nimble Escape can hide or disengage as a bonus action.",
                    "The antimagic area and direct vital effects are explicit DM house rules."));
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
            case ArmorMovementDefinition d -> d.description();
            case SubraceHitPointsDefinition d -> d.description();
            case StonecunningDefinition d -> d.description();
            case SpellDefinition d -> d.description();
            case SpellcastingDefinition d -> d.description();
            case WizardSpellcastingDefinition d -> d.description();
            case ClassCantripsDefinition d -> d.description();
            case EnvironmentDefinition d -> d.description();
            case MonsterDefinition d -> d.description();
            case LanguageSetDefinition ignored -> "Finite language candidates; grants retain their exact source slots.";
            case ToolSetDefinition ignored -> "Finite tool candidates; grants retain their exact source slots.";
            case ProficiencySetDefinition ignored -> "Finite class proficiency candidates; the growth consumer checks exact choices.";
        };
    }
}
