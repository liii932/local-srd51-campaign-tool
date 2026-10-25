package com.dndtool.module.composition;

import com.dndtool.module.AdvancementValueProfile;
import com.dndtool.module.ResourceRecoveryProfile;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure, finite schema-2 validation projection. This is neither an installer nor a production
 * qualification gate. Its validation-only encodings never enter persisted canonical digest domains.
 */
public final class Schema2CompositionKernel {
    public static final String HEADER_PATH = "author-package.json";
    public static final String CANONICAL_PROFILE = "validation-json-1";
    public static final String COMPOSITION_ALGORITHM = "validation-schema2-projection-1";
    public static final int MAX_PACKAGES = 4;
    public static final int MAX_AUTHOR_FILES = 16;
    public static final int MAX_HEADER_BYTES = 16384;
    public static final int MAX_FILE_BYTES = 65536;
    public static final int MAX_PACKAGE_BYTES = 1048576;
    public static final int MAX_GRANT_CHOICE_COUNT = 16;

    public enum Stage { PARSE, AUTHOR, GRAPH, APPLY, FINAL, QUALIFICATION }
    public enum Role { BASE, EXTENSION, COMPATIBILITY }
    public enum Category { STANDARD, EXOTIC, SECRET }
    /** Same bounded categories as the existing tool proficiency partition. */
    public enum ToolCategory { ARTISAN, KIT, GAMING_SET, MUSICAL_INSTRUMENT, NAVIGATION, VEHICLE }
    public enum Field {
        DISPLAY_NAME("display_name"), DESCRIPTION("description"), CATEGORY("category"),
        SOURCE_PAGE("source_page"), MINIMUM_ENABLED_MEMBERS("minimum_enabled_members"),
        AUTHOR_NOTE("author_note"), LANGUAGE("language"), TOOL("tool"),
        PARENT_RACE("parent_race"), GRANT("grant"), OWNER_CLASS("owner_class"),
        HIT_DIE_SIDES("hit_die_sides"), PROFICIENCY_BONUS_PROFILE("proficiency_bonus_profile"),
        SUPPORTED_MAXIMUM_LEVEL("supported_maximum_level"), ADVANCEMENT_SCOPE("advancement_scope"),
        MAXIMUM_PROFILE("maximum_profile"), RECOVERY_PROFILE("recovery_profile"),
        EXECUTION_MODE("execution_mode"), MULTICLASS_PREREQUISITE("multiclass_prerequisite"),
        OWNER("owner"), SELECTION_LEVEL("selection_level"), LEVEL("level"), ASI_AVAILABLE("asi_available"),
        FEATURE_KIND("feature_kind"), EXECUTION_ALGORITHM("execution_algorithm"), PREREQUISITE("prerequisite"),
        ABILITY_KEY("ability_key"), PROFICIENCY("proficiency"), GROWTH_GRANT("growth_grant"), ALGORITHM("algorithm"), KIND("kind"), ARMOR_CLASS("armor_class"), STRENGTH_MINIMUM("strength_minimum"),
        STEALTH_DISADVANTAGE("stealth_disadvantage"), HANDS("hands"), STEALTH_SKILL("stealth_skill"),
        DICE_COUNT("dice_count"), DICE_SIDES("dice_sides"), MODIFIER("modifier"), DAMAGE_TYPE("damage_type"), ENABLED("enabled");
        private final String key;
        Field(String key) { this.key = key; }
        public String key() { return key; }
    }

    /** A null identity is intentional until the manifest has supplied a validated identity. */
    public record Location(String moduleKey, String releaseVersion, String rawZipSha256,
                           String path, String pointer, Integer byteOffset) {
        Location child(String key) {
            return new Location(moduleKey, releaseVersion, rawZipSha256, path,
                    (pointer == null ? "" : pointer) + "/" + key.replace("~", "~0").replace("/", "~1"), null);
        }
        Location atByte(int offset) {
            return new Location(moduleKey, releaseVersion, rawZipSha256, path, null, offset);
        }
    }
    public record Diagnostic(String code, Stage stage, String message, List<Location> locations) {
        public Diagnostic { locations = List.copyOf(locations); }
    }
    public static final class Schema2Exception extends IllegalArgumentException {
        private final Diagnostic diagnostic;
        public Schema2Exception(Diagnostic diagnostic) {
            super(diagnostic.message());
            this.diagnostic = Objects.requireNonNull(diagnostic);
        }
        public Diagnostic diagnostic() { return diagnostic; }
    }

    public sealed interface Identity permits RuleIdentity, EntryIdentity {
        String moduleKey();
    }
    public record RuleIdentity(String moduleKey, String ruleType, String ruleKey) implements Identity { }
    public record EntryIdentity(RuleIdentity rule, String collectionKey, String moduleKey,
                                String entryKey) implements Identity { }
    public sealed interface Definition permits LanguageDefinition, LanguageSetDefinition, ToolDefinition, ToolSetDefinition, CharacterSourceDefinition, ClassProgressionDefinition, ResourceDefinition, SubclassDefinition, FeatureDefinition, FeatDefinition, ProficiencyDefinition, ProficiencySetDefinition, RestDefinition, CheckDefinition, ItemDefinition, HealthDefinition, VitalEffectDefinition {
        RuleIdentity identity();
        String displayName();
        String authorNote();
    }
    public record LanguageDefinition(RuleIdentity identity, String displayName, String description,
                                     Category category, int sourcePage, String authorNote) implements Definition { }
    public record LanguageSetDefinition(RuleIdentity identity, String displayName,
                                        int minimumEnabledMembers, String authorNote) implements Definition { }
    public record ToolDefinition(RuleIdentity identity, String displayName, String description,
                                 ToolCategory category, int sourcePage, String authorNote) implements Definition { }
    public record ToolSetDefinition(RuleIdentity identity, String displayName,
                                    int minimumEnabledMembers, String authorNote) implements Definition { }
    /** Initial proficiency source metadata, not a complete race, background or class domain. */
    public record CharacterSourceDefinition(RuleIdentity identity, String displayName, String description,
                                            int sourcePage, RuleIdentity parentRace, String authorNote) implements Definition { }
    /** Explicitly bounded statistics/resource support; never an assertion of complete class advancement. */
    public enum ProgressionScope { STATISTICS_AND_RESOURCES_ONLY, CHARACTER_ADVANCEMENT }
    public enum ResourceExecution { AUTOMATIC, BLOCKED }
    public record ClassProgressionDefinition(RuleIdentity identity, String displayName, String description,
            int sourcePage, RuleIdentity ownerClass, int hitDieSides, String proficiencyBonusProfile,
            int supportedMaximumLevel, ProgressionScope advancementScope, AbilityPrerequisite multiclassPrerequisite,
            String authorNote) implements Definition { }
    public record ResourceDefinition(RuleIdentity identity, String displayName, String description,
            int sourcePage, RuleIdentity ownerClass, String maximumProfile, String recoveryProfile,
            ResourceExecution executionMode, String authorNote) implements Definition { }
    public record AbilityMinimum(String abilityKey, int minimum) { }
    public record RequirementGroup(List<AbilityMinimum> anyOf) {
        public RequirementGroup { anyOf = List.copyOf(anyOf); }
    }
    public record AbilityPrerequisite(List<RequirementGroup> allOf) {
        public AbilityPrerequisite { allOf = List.copyOf(allOf); }
    }
    public enum FeatureKind { FEATURE, OPTION }
    public enum FeatureExecution { AUTOMATIC, DM_ADJUDICATION, BLOCKED }
    public enum ProficiencyCategory { SKILL, ARMOR, WEAPON, TOOL }
    public record SubclassDefinition(RuleIdentity identity, String displayName, String description, int sourcePage,
            RuleIdentity ownerClass, int selectionLevel, String authorNote) implements Definition { }
    public record FeatureDefinition(RuleIdentity identity, String displayName, String description, int sourcePage,
            RuleIdentity owner, int level, FeatureKind kind, FeatureExecution executionMode,
            String executionAlgorithm, String authorNote) implements Definition { }
    public record FeatDefinition(RuleIdentity identity, String displayName, String description, int sourcePage,
            AbilityPrerequisite prerequisite, FeatureExecution executionMode, String executionAlgorithm,
            String authorNote) implements Definition { }
    public record ProficiencyDefinition(RuleIdentity identity, String displayName, String description, int sourcePage,
            ProficiencyCategory category, String abilityKey, String authorNote) implements Definition { }
    public record ProficiencySetDefinition(RuleIdentity identity, String displayName, ProficiencyCategory category,
            int minimumEnabledMembers, String authorNote) implements Definition { }
    public record RestDefinition(RuleIdentity identity, String displayName, String description, int sourcePage,
            String algorithm, String authorNote) implements Definition { }
    public record CheckDefinition(RuleIdentity identity, String displayName, String description, int sourcePage,
            String algorithm, String authorNote) implements Definition { }
    public enum EquipmentKind { LIGHT_ARMOR, MEDIUM_ARMOR, HEAVY_ARMOR, SHIELD, TOOL, WEAPON }
    public enum VitalEffectKind { DAMAGE, HEALING, TEMP_HP }
    public enum DamageType { ACID, BLUDGEONING, COLD, FIRE, FORCE, LIGHTNING, NECROTIC, PIERCING, POISON, PSYCHIC, RADIANT, SLASHING, THUNDER }
    public record ItemDefinition(RuleIdentity identity, String displayName, String description, int sourcePage,
            EquipmentKind kind, int armorClass, int strengthMinimum, boolean stealthDisadvantage, int hands,
            RuleIdentity proficiency, RuleIdentity stealthSkill, String algorithm, String authorNote) implements Definition { }
    public record HealthDefinition(RuleIdentity identity, String displayName, String description, int sourcePage,
            String algorithm, String authorNote) implements Definition { }
    public record VitalEffectDefinition(RuleIdentity identity, String displayName, String description, int sourcePage,
            VitalEffectKind kind, int diceCount, int diceSides, int modifier, DamageType damageType,
            String algorithm, String authorNote) implements Definition { }
    public sealed interface GrowthGrantSpec permits FixedGrowthGrant, ChoiceGrowthGrant { }
    public record FixedGrowthGrant(RuleIdentity target) implements GrowthGrantSpec { }
    public record ChoiceGrowthGrant(RuleIdentity candidates, int count) implements GrowthGrantSpec { }
    public enum GrantCategory { LANGUAGE, TOOL }
    public sealed interface GrantSpec permits FixedGrant, ChoiceGrant { GrantCategory category(); }
    public record FixedGrant(GrantCategory category, RuleIdentity target) implements GrantSpec { }
    public record ChoiceGrant(GrantCategory category, RuleIdentity candidates, int count) implements GrantSpec { }
    public sealed interface Entry permits LanguageEntry, ToolEntry, ProficiencyGrantEntry, ClassLevelEntry, ProficiencyMemberEntry, GrowthProficiencyGrantEntry {
        EntryIdentity identity();
        String authorNote();
        RuleIdentity target();
    }
    public record LanguageEntry(EntryIdentity identity, RuleIdentity language, String authorNote) implements Entry {
        @Override public RuleIdentity target() { return language; }
    }
    public record ToolEntry(EntryIdentity identity, RuleIdentity tool, String authorNote) implements Entry {
        @Override public RuleIdentity target() { return tool; }
    }
    public record ProficiencyGrantEntry(EntryIdentity identity, GrantSpec grant, String authorNote) implements Entry {
        @Override public RuleIdentity target() { return grant instanceof FixedGrant fixed ? fixed.target() : ((ChoiceGrant) grant).candidates(); }
    }
    public record ClassLevelEntry(EntryIdentity identity, int level, boolean asiAvailable, String authorNote) implements Entry {
        @Override public RuleIdentity target() { return identity.rule(); }
    }
    public record ProficiencyMemberEntry(EntryIdentity identity, RuleIdentity proficiency, String authorNote) implements Entry {
        @Override public RuleIdentity target() { return proficiency; }
    }
    public record GrowthProficiencyGrantEntry(EntryIdentity identity, GrowthGrantSpec grant, String authorNote) implements Entry {
        @Override public RuleIdentity target() { return grant instanceof FixedGrowthGrant fixed ? fixed.target() : ((ChoiceGrowthGrant) grant).candidates(); }
    }
    public sealed interface FieldValue permits TextValue, IntegerValue, IdentityValue, BooleanValue, GrantValue, PrerequisiteValue, GrowthGrantValue { }
    public record TextValue(String value) implements FieldValue { }
    public record IntegerValue(int value) implements FieldValue { }
    public record IdentityValue(RuleIdentity value) implements FieldValue { }
    public record BooleanValue(boolean value) implements FieldValue { }
    public record GrantValue(GrantSpec value) implements FieldValue { }
    public record PrerequisiteValue(AbilityPrerequisite value) implements FieldValue { }
    public record GrowthGrantValue(GrowthGrantSpec value) implements FieldValue { }
    public sealed interface Change permits SetField, ClearField, SetEnabled {
        Identity target();
        Field field();
    }
    public record SetField(Identity target, Field field, FieldValue value) implements Change { }
    public record ClearField(Identity target, Field field) implements Change { }
    public record SetEnabled(Identity target, boolean value) implements Change {
        @Override public Field field() { return Field.ENABLED; }
    }
    public record Dependency(String moduleKey, List<String> supportedVersions) {
        public Dependency { supportedVersions = List.copyOf(supportedVersions); }
    }
    public record OrderConstraint(String before, String after) { }
    public record Provider(String moduleKey, String releaseVersion) { }
    public enum StateKind { UNINITIALIZED, ABSENT, PRESENT }
    public record FieldState(StateKind state, FieldValue value) { }
    public record ChainStep(String op, FieldState before, FieldState after, Provider provider,
                            int loadIndex, Location authorLocation, String origin) { }
    public record FieldChain(Identity target, Field field, List<ChainStep> steps) {
        public FieldChain { steps = List.copyOf(steps); }
    }
    public sealed interface FinalRule permits FinalLanguage, FinalLanguageSet, FinalTool, FinalToolSet, FinalCharacterSource, FinalClassProgression, FinalResource, FinalSubclass, FinalFeature, FinalFeat, FinalProficiency, FinalProficiencySet, FinalRest, FinalCheck, FinalItem, FinalHealth, FinalVitalEffect {
        Definition definition();
        RuleIdentity identity();
        boolean enabled();
    }
    public record FinalLanguage(LanguageDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalLanguageSet(LanguageSetDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalTool(ToolDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalToolSet(ToolSetDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalCharacterSource(CharacterSourceDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalClassProgression(ClassProgressionDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalResource(ResourceDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalSubclass(SubclassDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalFeature(FeatureDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalFeat(FeatDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalProficiency(ProficiencyDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalProficiencySet(ProficiencySetDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalRest(RestDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalCheck(CheckDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalItem(ItemDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalHealth(HealthDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalVitalEffect(VitalEffectDefinition definition, boolean enabled) implements FinalRule {
        @Override public RuleIdentity identity() { return definition.identity(); }
    }
    public record FinalEntry(Entry entry, boolean enabled) { }

    /** Only the strict reader can create package evidence. All source arrays are private snapshots. */
    public static final class AuthorPackage {
        private final String moduleKey;
        private final String releaseVersion;
        private final String packageDisplayName;
        private final Role role;
        private final List<Dependency> dependencies;
        private final List<Map<String, String>> combinations;
        private final List<Located<OrderConstraint>> constraints;
        private final List<Located<Definition>> definitions;
        private final List<Located<Entry>> entries;
        private final List<Located<Change>> changes;
        private final byte[] rawZip;
        private final byte[] logicalJson;
        private final Object logicalProjection;
        private final String rawZipSha256;
        private final String logicalSha256;
        private final Location headerLocation;

        private AuthorPackage(String moduleKey, String releaseVersion, String packageDisplayName, Role role,
                              List<Dependency> dependencies, List<Map<String, String>> combinations,
                              List<Located<OrderConstraint>> constraints, List<Located<Definition>> definitions,
                              List<Located<Entry>> entries, List<Located<Change>> changes,
                              byte[] rawZip, Object logic, Location headerLocation) {
            this.moduleKey = moduleKey;
            this.releaseVersion = releaseVersion;
            this.packageDisplayName = packageDisplayName;
            this.role = role;
            this.dependencies = List.copyOf(dependencies);
            this.combinations = combinations.stream().map(Map::copyOf).toList();
            this.constraints = List.copyOf(constraints);
            this.definitions = List.copyOf(definitions);
            this.entries = List.copyOf(entries);
            this.changes = List.copyOf(changes);
            this.rawZip = rawZip;
            this.logicalProjection = logic;
            this.logicalJson = Schema2ValidationCodec.canonical(logic);
            this.rawZipSha256 = Schema2ValidationCodec.sha256(rawZip);
            this.logicalSha256 = Schema2ValidationCodec.digest("logic", logic);
            this.headerLocation = headerLocation;
        }
        public String moduleKey() { return moduleKey; }
        public String releaseVersion() { return releaseVersion; }
        public String packageDisplayName() { return packageDisplayName; }
        public Role role() { return role; }
        public List<Dependency> dependencies() { return dependencies; }
        public List<Map<String, String>> compatibleCombinations() { return combinations; }
        public List<OrderConstraint> loadOrderConstraints() { return constraints.stream().map(Located::value).toList(); }
        public List<Definition> definitions() { return definitions.stream().map(Located::value).toList(); }
        public List<Entry> entries() { return entries.stream().map(Located::value).toList(); }
        public List<Change> changes() { return changes.stream().map(Located::value).toList(); }
        /** Original position across every same-kind file in ASCII path order, before identity sorting. */
        public int definitionAuthorIndex(int canonicalIndex) { return authorIndex(definitions, canonicalIndex); }
        public int entryAuthorIndex(int canonicalIndex) { return authorIndex(entries, canonicalIndex); }
        public int changeAuthorIndex(int canonicalIndex) { return authorIndex(changes, canonicalIndex); }
        private static <T> int authorIndex(List<Located<T>> rows, int canonicalIndex) {
            Location selected = rows.get(canonicalIndex).location();
            int selectedRow = Integer.parseInt(selected.pointer().substring(1));
            return (int) rows.stream().filter(row -> row.location().path().compareTo(selected.path()) < 0
                    || row.location().path().equals(selected.path())
                    && Integer.parseInt(row.location().pointer().substring(1)) < selectedRow).count();
        }
        public String rawZipSha256() { return rawZipSha256; }
        public String logicalSha256() { return logicalSha256; }
        public byte[] rawZipBytes() { return rawZip.clone(); }
        public byte[] logicalJson() { return logicalJson.clone(); }
        Object logicalProjection() { return logicalProjection; }
    }

    /** Successful finite projection; it carries no installation, acceptance or execution authority. */
    public static final class Projection {
        private final List<FinalRule> rules;
        private final List<FinalEntry> entries;
        private final List<FieldChain> chains;
        private final byte[] configurationJson;
        private final byte[] finalRulesJson;
        private final byte[] chainsJson;
        private final String compositionSha256;
        private final String finalRulesSha256;
        private final String chainSha256;

        private Projection(List<FinalRule> rules, List<FinalEntry> entries, List<FieldChain> chains,
                           Object configuration, Object finalRules) {
            this.rules = List.copyOf(rules);
            this.entries = List.copyOf(entries);
            this.chains = List.copyOf(chains);
            this.configurationJson = Schema2ValidationCodec.canonical(configuration);
            this.finalRulesJson = Schema2ValidationCodec.canonical(finalRules);
            this.compositionSha256 = Schema2ValidationCodec.digest("config", configuration);
            this.finalRulesSha256 = Schema2ValidationCodec.digest("rules", finalRules);
            Object envelope = json("composition_sha256", compositionSha256,
                    "chains", chains.stream().map(Schema2CompositionKernel::chainJson).toList());
            this.chainsJson = Schema2ValidationCodec.canonical(envelope);
            this.chainSha256 = Schema2ValidationCodec.digest("chain", envelope);
        }
        public List<FinalRule> rules() { return rules; }
        public List<FinalEntry> entries() { return entries; }
        public List<FieldChain> chains() { return chains; }
        public byte[] configurationJson() { return configurationJson.clone(); }
        public byte[] finalRulesJson() { return finalRulesJson.clone(); }
        public byte[] chainsJson() { return chainsJson.clone(); }
        public String compositionSha256() { return compositionSha256; }
        public String finalRulesSha256() { return finalRulesSha256; }
        public String chainSha256() { return chainSha256; }
    }

    /** Validates stored finite typed rows and full provenance before restoring canonical encodings. */
    static Projection restoreStored(List<ProductionSchema2Projection.StoredMember> members, List<FinalRule> rules,
                                    List<FinalEntry> entries, List<FieldChain> chains) {
        Location source = new Location(null, null, null, "$runtime", "", null);
        if (members == null || members.isEmpty() || members.size() > MAX_PACKAGES || rules == null
                || entries == null || chains == null || rules.size() > 256 || entries.size() > 512
                || chains.size() > 3072) throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid stored projection budget", source);
        Set<String> modules = new HashSet<>();
        for (int i = 0; i < members.size(); i++) {
            var member = members.get(i);
            if (member == null || member.role() == null || (i == 0) != (member.role() == Role.BASE)
                    || !modules.add(moduleKey(member.moduleKey(), source))
                    || !version(member.releaseVersion(), source).equals(member.releaseVersion())
                    || member.rawZipSha256() == null || !member.rawZipSha256().matches("[0-9a-f]{64}")
                    || member.logicalSha256() == null || !member.logicalSha256().matches("[0-9a-f]{64}")) {
                throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid stored ordered member", source);
            }
        }
        if (!rules.equals(rules.stream().sorted(Comparator.comparing(FinalRule::identity, RULE_ORDER)).toList())
                || !entries.equals(entries.stream().sorted(Comparator.comparing(e -> e.entry().identity(), ENTRY_ORDER)).toList())
                || !chains.equals(chains.stream().sorted(Comparator.comparing(FieldChain::target, IDENTITY_ORDER)
                        .thenComparing(c -> c.field().key())).toList())) {
            throw fail("S2_STORED_INVALID", Stage.FINAL, "Stored rows are not in canonical identity order", source);
        }
        Map<Identity, MutableObject> objects = new HashMap<>();
        for (FinalRule row : rules) {
            Definition definition = row.definition();
            Definition checked = definition(definitionJson(definition), source);
            if (!definition.equals(checked)) throw fail("S2_STORED_INVALID", Stage.FINAL, "Stored text is not canonical", source);
            Map<Field, FieldState> fields = definitionFields(checked);
            fields.put(Field.ENABLED, present(new BooleanValue(row.enabled())));
            if (objects.putIfAbsent(row.identity(), new MutableObject(row.identity(), fields, source)) != null)
                throw fail("S2_STORED_INVALID", Stage.FINAL, "Duplicate stored rule", source);
        }
        for (FinalEntry row : entries) {
            Entry checked = entry(entryJson(row.entry()), source);
            if (!checked.equals(row.entry())) throw fail("S2_STORED_INVALID", Stage.FINAL, "Stored text is not canonical", source);
            Map<Field, FieldState> fields = entryFields(checked);
            fields.put(Field.ENABLED, present(new BooleanValue(row.enabled())));
            if (objects.putIfAbsent(row.entry().identity(), new MutableObject(row.entry().identity(), fields, source)) != null)
                throw fail("S2_STORED_INVALID", Stage.FINAL, "Duplicate stored entry", source);
        }
        finalValidation(objects);
        Map<FieldKey, Boolean> seen = new HashMap<>();
        Map<Identity, Map<Field, FieldState>> initialFields = new HashMap<>();
        Map<Identity, Integer> declarations = new HashMap<>();
        Map<Identity, Location> sourceDeclarations = new HashMap<>();
        int[] definitionCounts = new int[members.size()], entryCounts = new int[members.size()], changeCounts = new int[members.size()];
        int stepCount = 0;
        for (FieldChain chain : chains) {
            MutableObject object = objects.get(chain.target());
            if (object == null || !object.fields.containsKey(chain.field()) || chain.steps().isEmpty()
                    || chain.steps().size() > MAX_PACKAGES || seen.put(new FieldKey(chain.target(), chain.field()), true) != null)
                throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid stored field chain", source);
            FieldState previous = new FieldState(StateKind.UNINITIALIZED, null);
            int previousIndex = -1;
            for (int n = 0; n < chain.steps().size(); n++) {
                ChainStep step = chain.steps().get(n);
                if (++stepCount > 4096 || step.loadIndex() < 0 || step.loadIndex() >= members.size()
                        || step.loadIndex() <= previousIndex || !previous.equals(step.before()))
                    throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid stored chain continuity", source);
                var member = members.get(step.loadIndex());
                Location location = step.authorLocation();
                if (!new Provider(member.moduleKey(), member.releaseVersion()).equals(step.provider()) || location == null
                        || !member.moduleKey().equals(location.moduleKey()) || !member.releaseVersion().equals(location.releaseVersion())
                        || !member.rawZipSha256().equals(location.rawZipSha256()) || location.byteOffset() != null
                        || location.path() == null || location.path().length() > 240
                        || !location.path().matches("[A-Za-z0-9][A-Za-z0-9._/-]*")
                        || Arrays.stream(location.path().split("/", -1)).anyMatch(segment -> segment.isEmpty() || segment.endsWith(".") || !segment.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
                        || !storedPointer(location.pointer()))
                    throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid stored provenance location", source);
                FieldState after = step.after();
                if (after == null || after.state() == null || after.state() == StateKind.UNINITIALIZED
                        || (after.state() == StateKind.ABSENT && (!optionalField(chain.field()) || after.value() != null
                            || (n > 0 && !clearableField(chain.field()))))
                        || (after.state() == StateKind.PRESENT && after.value() == null))
                    throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid stored field state", source);
                if (after.state() == StateKind.PRESENT) {
                    Change checked = chain.field() == Field.ENABLED
                            ? change(json("op", "set_enabled", "target", identityJson(chain.target()), "value", valueJson(after.value())), source)
                            : change(json("op", "set_field", "target", identityJson(chain.target()), "field", chain.field().key(), "value", valueJson(after.value())), source);
                    FieldValue checkedValue = checked instanceof SetField set ? set.value() : new BooleanValue(((SetEnabled) checked).value());
                    if (!checkedValue.equals(after.value())) throw fail("S2_STORED_INVALID", Stage.FINAL, "Noncanonical stored value", source);
                }
                if (n == 0) {
                    String origin = chain.field() == Field.ENABLED ? "default_enabled" : after.state() == StateKind.ABSENT ? "omitted_optional" : "declared";
                    if (!step.op().equals("initial") || !step.origin().equals(origin) || !chain.target().moduleKey().equals(member.moduleKey())
                            || (chain.field() == Field.ENABLED && !after.equals(present(new BooleanValue(true)))))
                        throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid stored declaration", source);
                    initialFields.computeIfAbsent(chain.target(), ignored -> new LinkedHashMap<>()).put(chain.field(), after);
                    boolean proficiencySource = chain.target() instanceof RuleIdentity rule && tracedCharacterType(rule.ruleType())
                            || chain.target() instanceof EntryIdentity entry && tracedCharacterType(entry.rule().ruleType());
                    if (proficiencySource) {
                        String pointer = location.pointer();
                        String suffix = "/" + chain.field().key();
                        if (origin.equals("declared")) {
                            if (!pointer.endsWith(suffix)) throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid atomic source field location", source);
                            pointer = pointer.substring(0, pointer.length() - suffix.length());
                        }
                        if (HEADER_PATH.equals(location.path()) || !storedArrayElement(pointer,
                                chain.target() instanceof RuleIdentity ? 64 : 128))
                            throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid proficiency declaration location", source);
                        Location declarationLocation = new Location(location.moduleKey(), location.releaseVersion(), location.rawZipSha256(), location.path(), pointer, null);
                        Location previousLocation = sourceDeclarations.putIfAbsent(chain.target(), declarationLocation);
                        if (previousLocation != null && !previousLocation.equals(declarationLocation))
                            throw fail("S2_STORED_INVALID", Stage.FINAL, "Proficiency fields split across declaration locations", source);
                    }
                    Integer declaration = declarations.putIfAbsent(chain.target(), step.loadIndex());
                    if (declaration != null && declaration != step.loadIndex()) throw fail("S2_STORED_INVALID", Stage.FINAL, "Split stored declaration", source);
                    if (declaration == null) {
                        if (chain.target() instanceof RuleIdentity) definitionCounts[step.loadIndex()]++;
                        else entryCounts[step.loadIndex()]++;
                    }
                } else {
                    String op = chain.field() == Field.ENABLED ? "set_enabled" : after.state() == StateKind.ABSENT ? "clear_field" : "set_field";
                    if (!step.op().equals(op) || !step.origin().equals("explicit"))
                        throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid stored modification", source);
                    if ((chain.target() instanceof RuleIdentity rule && tracedCharacterType(rule.ruleType())
                            || chain.target() instanceof EntryIdentity entry && tracedCharacterType(entry.rule().ruleType()))
                            && (HEADER_PATH.equals(location.path()) || !storedArrayElement(location.pointer(), 256)))
                        throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid proficiency operation location", source);
                    changeCounts[step.loadIndex()]++;
                }
                previous = after;
                previousIndex = step.loadIndex();
            }
            if (!previous.equals(object.fields.get(chain.field()))) throw fail("S2_STORED_INVALID", Stage.FINAL, "Stored final value differs from chain", source);
        }
        for (FieldChain chain : chains) {
            int declaration = declarations.get(chain.target());
            if (chain.target() instanceof EntryIdentity entry) {
                Integer ownerDeclaration = declarations.get(entry.rule());
                if (ownerDeclaration == null || ownerDeclaration > declaration)
                    throw fail("S2_STORED_INVALID", Stage.FINAL, "Entry owner was not yet declared", source);
            }
            for (ChainStep step : chain.steps()) {
                RuleIdentity reference = step.after().value() instanceof IdentityValue value ? value.value()
                        : step.after().value() instanceof GrantValue value ? (value.value() instanceof FixedGrant fixed
                            ? fixed.target() : ((ChoiceGrant) value.value()).candidates())
                        : step.after().value() instanceof GrowthGrantValue value ? growthTarget(value.value()) : null;
                if (reference != null) {
                    Integer referenceDeclaration = declarations.get(reference);
                    if (referenceDeclaration == null || referenceDeclaration > step.loadIndex())
                        throw fail("S2_STORED_INVALID", Stage.FINAL, "Stored reference precedes its declaration", source);
                }
            }
        }
        if (seen.size() != objects.values().stream().mapToInt(o -> o.fields.size()).sum())
            throw fail("S2_STORED_INVALID", Stage.FINAL, "Stored field chains are incomplete", source);
        for (var declaration : initialFields.entrySet()) {
            Map<String, Object> encoded = json("identity", identityJson(declaration.getKey()));
            for (Field field : domainFields(declaration.getKey())) {
                FieldState state = declaration.getValue().get(field);
                if (state.state() == StateKind.PRESENT) encoded.put(field.key(), valueJson(state.value()));
            }
            try {
                if (declaration.getKey() instanceof RuleIdentity) {
                    Definition original = definition(encoded, source);
                    if (original instanceof ItemDefinition item) {
                        Map<Identity, MutableObject> originalReferences = new HashMap<>();
                        int declarationIndex = declarations.get(declaration.getKey());
                        for (RuleIdentity target : item.stealthSkill() == null ? List.of(item.proficiency()) : List.of(item.proficiency(), item.stealthSkill())) {
                            Map<Field, FieldState> prior = new HashMap<>();
                            for (FieldChain chain : chains) if (chain.target().equals(target))
                                for (ChainStep step : chain.steps()) if (step.loadIndex() < declarationIndex || (step.loadIndex() == declarationIndex && step.op().equals("initial"))) prior.put(chain.field(), step.after());
                            if (!prior.isEmpty()) originalReferences.put(target, new MutableObject(target, prior, source));
                        }
                        validateEquipmentReferences(new FinalItem(item, true), originalReferences, source);
                    }
                } else entry(encoded, source);
            } catch (Schema2Exception malformed) {
                throw fail("S2_STORED_INVALID", Stage.FINAL, "Invalid original stored declaration", source);
            }
        }
        for (int i = 0; i < members.size(); i++) if (definitionCounts[i] > 64 || entryCounts[i] > 128 || changeCounts[i] > 256)
            throw fail("S2_STORED_INVALID", Stage.FINAL, "Stored member budget exceeded", source);
        Map<String, Object> configuration = json("author_schema_version", 2, "canonical_profile", CANONICAL_PROFILE,
                "composition_algorithm", COMPOSITION_ALGORITHM, "members", members.stream().map(member ->
                        json("module_key", member.moduleKey(), "release_version", member.releaseVersion(), "role", roleName(member.role()),
                                "raw_zip_sha256", member.rawZipSha256(), "logical_sha256", member.logicalSha256())).toList());
        Object finalRules = json("rules", rules.stream().map(Schema2CompositionKernel::finalRuleJson).toList(),
                "entries", entries.stream().map(Schema2CompositionKernel::finalEntryJson).toList());
        Map<String, Object> productionConfiguration = new LinkedHashMap<>(configuration);
        productionConfiguration.put("canonical_profile", ProductionSchema2Projection.CANONICAL_PROFILE);
        productionConfiguration.put("composition_algorithm", ProductionSchema2Projection.COMPOSITION_ALGORITHM);
        Object productionChains = json("composition_sha256", "0".repeat(64),
                "chains", chains.stream().map(Schema2CompositionKernel::chainJson).toList());
        long runtimeBytes = ProductionSchema2Codec.canonicalByteLength(productionConfiguration)
                + ProductionSchema2Codec.canonicalByteLength(finalRules) + ProductionSchema2Codec.canonicalByteLength(productionChains);
        if (runtimeBytes > ProductionSchema2Projection.MAX_RUNTIME_CANONICAL_BYTES)
            throw fail("S2_BUDGET", Stage.FINAL, "Runtime canonical byte budget exceeded", source);
        return new Projection(rules, entries, chains, configuration, finalRules);
    }

    private static boolean storedArrayElement(String pointer, int maximumRows) {
        return pointer.matches("/(0|[1-9][0-9]{0,2})")
                && Integer.parseInt(pointer.substring(1)) < maximumRows;
    }

    /** Bounded JSON Pointer validation uses no recursive regular-expression engine. */
    private static boolean storedPointer(String pointer) {
        if (pointer == null || pointer.length() > 8192 || (!pointer.isEmpty() && pointer.charAt(0) != '/')) return false;
        int codePoints = 0;
        for (int index = 0; index < pointer.length(); index++) {
            char value = pointer.charAt(index);
            if (++codePoints > 4096 || value < 0x20 || (value >= 0x7f && value <= 0x9f)) return false;
            if (value == '~') {
                if (++index >= pointer.length() || (pointer.charAt(index) != '0' && pointer.charAt(index) != '1')
                        || ++codePoints > 4096) return false;
            } else if (Character.isHighSurrogate(value)) {
                if (++index >= pointer.length() || !Character.isLowSurrogate(pointer.charAt(index))) return false;
            } else if (Character.isLowSurrogate(value)) return false;
        }
        return true;
    }

    private record Located<T>(T value, Location location) { }
    private record FieldKey(Identity target, Field field) { }
    private record FileDeclaration(String kind, String path) { }

    /** Reads one virtual author-file set; this does not read a general ZIP or the filesystem. */
    public AuthorPackage read(Map<String, byte[]> files) {
        checkRawBudget(files);
        return readSnapshot(snapshot(files));
    }

    /** Checks every raw input length before cloning or decoding any selected package. */
    public Projection composeFiles(List<Map<String, byte[]>> packages) {
        checkSelectionSize(packages);
        for (Map<String, byte[]> files : packages) checkRawBudget(files);
        List<Map<String, byte[]>> snapshots = new ArrayList<>();
        for (Map<String, byte[]> files : packages) snapshots.add(snapshot(files));
        List<AuthorPackage> selected = new ArrayList<>();
        for (Map<String, byte[]> files : snapshots) selected.add(readSnapshot(files));
        return compose(selected);
    }

    public Projection compose(List<AuthorPackage> packages) {
        checkSelectionSize(packages);
        List<AuthorPackage> selected = List.copyOf(packages);
        graph(selected);
        Map<Identity, MutableObject> objects = new HashMap<>();
        Map<FieldKey, List<ChainStep>> chains = new HashMap<>();
        for (int index = 0; index < selected.size(); index++) {
            AuthorPackage pack = selected.get(index);
            Set<Identity> newlyDeclared = new HashSet<>();
            for (Located<Definition> row : pack.definitions) {
                Definition value = row.value;
                addObject(objects, chains, value.identity(), definitionFields(value), pack, index,
                        row.location, newlyDeclared);
            }
            for (Located<Entry> row : pack.entries) {
                Entry value = row.value;
                addObject(objects, chains, value.identity(), entryFields(value), pack, index,
                        row.location, newlyDeclared);
            }
            // All of this package's declarations are visible before any reference is resolved.
            for (Located<Definition> row : pack.definitions) {
                if (row.value instanceof CharacterSourceDefinition source && source.parentRace() != null)
                    checkReference(pack, source.parentRace(), "character.race", objects, row.location.child("parent_race"));
                if (row.value instanceof ClassProgressionDefinition progression)
                    checkReference(pack, progression.ownerClass(), "character.class", objects, row.location.child("owner_class"));
                if (row.value instanceof ResourceDefinition resource)
                    checkReference(pack, resource.ownerClass(), "character.class", objects, row.location.child("owner_class"));
                if (row.value instanceof SubclassDefinition subclass)
                    checkReference(pack, subclass.ownerClass(), "character.class", objects, row.location.child("owner_class"));
                if (row.value instanceof FeatureDefinition feature)
                    checkReference(pack, feature.owner(), "character.class|character.subclass", objects, row.location.child("owner"));
                if (row.value instanceof ItemDefinition item) {
                    checkReference(pack, item.proficiency(), referenceType(Field.PROFICIENCY), objects, row.location.child("proficiency"));
                    if (item.stealthSkill() != null) checkReference(pack, item.stealthSkill(), referenceType(Field.STEALTH_SKILL), objects, row.location.child("stealth_skill"));
                    validateEquipmentReferences(new FinalItem(item, true), objects, row.location);
                }
            }
            for (Located<Entry> row : pack.entries) {
                Entry value = row.value;
                Field reference = referenceField(value.identity());
                checkReference(pack, value.identity().rule(), value.identity().rule().ruleType(), objects, row.location.child("identity").child("rule"));
                if (value instanceof ClassLevelEntry) continue;
                if (value instanceof ProficiencyGrantEntry grant)
                    checkGrantReference(pack, grant.grant(), objects, row.location.child("grant"));
                else if (value instanceof GrowthProficiencyGrantEntry grant)
                    checkGrowthGrantReference(pack, grant.grant(), objects, row.location.child("growth_grant"));
                else checkReference(pack, value.target(), referenceType(reference), objects, row.location.child(reference.key()));
            }
            for (Located<Change> row : pack.changes) {
                Change change = row.value;
                directIdentity(pack, change.target(), row.location.child("target"));
                MutableObject target = objects.get(change.target());
                if (target == null) throw fail("S2_TARGET_MISSING", Stage.APPLY, "Modification target does not exist", row.location);
                if (newlyDeclared.contains(change.target())) {
                    throw fail("S2_TARGET_NOT_PREVIOUS", Stage.APPLY, "A package cannot modify its own new declaration", row.location);
                }
                if (change.target() instanceof EntryIdentity entry) {
                    checkReference(pack, entry.rule(), entry.rule().ruleType(), objects, row.location.child("target").child("rule"));
                }
                FieldState after;
                String op;
                if (change instanceof SetField set) {
                    if (set.value() instanceof IdentityValue identity) {
                        checkReference(pack, identity.value(), referenceType(set.field()), objects, row.location.child("value"));
                    } else if (set.value() instanceof GrantValue grant) {
                        checkGrantReference(pack, grant.value(), objects, row.location.child("value"));
                    } else if (set.value() instanceof GrowthGrantValue grant) {
                        checkGrowthGrantReference(pack, grant.value(), objects, row.location.child("value"));
                    }
                    after = present(set.value());
                    op = "set_field";
                } else if (change instanceof ClearField) {
                    after = absent();
                    op = "clear_field";
                } else {
                    after = present(new BooleanValue(((SetEnabled) change).value()));
                    op = "set_enabled";
                }
                FieldState before = target.fields.get(change.field());
                target.fields.put(change.field(), after);
                chains.get(new FieldKey(change.target(), change.field())).add(new ChainStep(op, before, after,
                        provider(pack), index, row.location, "explicit"));
            }
        }
        finalValidation(objects);
        List<MutableObject> sorted = objects.values().stream().sorted(Comparator.comparing(o -> o.identity, IDENTITY_ORDER)).toList();
        List<FinalRule> rules = new ArrayList<>();
        List<FinalEntry> entries = new ArrayList<>();
        for (MutableObject object : sorted) {
            if (object.identity instanceof RuleIdentity rule) rules.add(finalRule(rule, object.fields));
            else entries.add(new FinalEntry(finalEntry((EntryIdentity) object.identity, object.fields), enabled(object)));
        }
        List<FieldChain> completeChains = chains.entrySet().stream()
                .sorted(Comparator.<Map.Entry<FieldKey, List<ChainStep>>, Identity>comparing(e -> e.getKey().target, IDENTITY_ORDER)
                        .thenComparing(e -> e.getKey().field.key()))
                .map(e -> new FieldChain(e.getKey().target, e.getKey().field, e.getValue())).toList();
        Object configuration = json("author_schema_version", 2, "canonical_profile", CANONICAL_PROFILE,
                "composition_algorithm", COMPOSITION_ALGORITHM, "members", selected.stream().map(pack ->
                        json("module_key", pack.moduleKey, "release_version", pack.releaseVersion,
                                "role", roleName(pack.role), "raw_zip_sha256", pack.rawZipSha256,
                                "logical_sha256", pack.logicalSha256)).toList());
        Object finalRules = json("rules", rules.stream().map(Schema2CompositionKernel::finalRuleJson).toList(),
                "entries", entries.stream().map(Schema2CompositionKernel::finalEntryJson).toList());
        return new Projection(rules, entries, completeChains, configuration, finalRules);
    }

    /**
     * Verifies the canonical chain-envelope bytes exported by this kernel, reconstructing every
     * required step from original packages. Other JSON layouts are not an evidence format here.
     * Derived evidence is bounded by that reconstruction, rather than author-file array budgets.
     */
    public void verifyChains(List<AuthorPackage> packages, byte[] chainEnvelopeJson) {
        Location source = new Location(null, null, null, "$chains", "", null);
        if (chainEnvelopeJson == null) throw fail("S2_SHAPE", Stage.AUTHOR, "Missing chain evidence", source);
        Projection expected = compose(packages);
        if (chainEnvelopeJson.length != expected.chainsJson.length
                || !Arrays.equals(expected.chainsJson, chainEnvelopeJson.clone())) {
            throw fail("S2_CHAIN_MISMATCH", Stage.FINAL, "Complete chain evidence differs from original declarations and operations", source);
        }
    }

    /** Every projection in this finite catalog context lacks complete production base evidence. */
    public void requireProductionQualification(Projection projection) {
        Objects.requireNonNull(projection);
        throw fail("S2_BASE_INCOMPLETE", Stage.QUALIFICATION,
                "The finite validation projection is not a complete production base");
    }

    private static void checkSelectionSize(List<?> packages) {
        if (packages == null) throw fail("S2_SHAPE", Stage.GRAPH, "Missing selected package list");
        if (packages.size() > MAX_PACKAGES) throw fail("S2_BUDGET", Stage.PARSE, "Package count budget exceeded");
        if (packages.isEmpty()) throw fail("S2_LOAD_ORDER", Stage.GRAPH, "A projection requires one first base");
        for (int i = 0; i < packages.size(); i++) {
            if (packages.get(i) == null) throw fail("S2_SHAPE", Stage.GRAPH, "Missing selected package",
                    new Location(null, null, null, "$selection", "/" + i, null));
        }
    }

    private static void checkRawBudget(Map<String, byte[]> files) {
        Location source = new Location(null, null, null, HEADER_PATH, "", null);
        if (files == null) throw fail("S2_FILE_SET", Stage.AUTHOR, "Missing author file set", source);
        if (files.size() > MAX_AUTHOR_FILES + 1) throw fail("S2_BUDGET", Stage.PARSE, "Author file count budget exceeded", source.atByte(0));
        long total = 0;
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String path = entry.getKey();
            if (path == null || path.isEmpty()) throw fail("S2_FILE_SET", Stage.AUTHOR, "Author file path must be an ASCII name", source);
            if (path.length() > 4096) throw fail("S2_BUDGET", Stage.PARSE, "Author path string budget exceeded", source.atByte(0));
            if (!path.chars().allMatch(c -> c >= 0x20 && c <= 0x7e)) throw fail("S2_FILE_SET", Stage.AUTHOR, "Author file path must be ASCII", source);
            byte[] bytes = entry.getValue();
            Location file = new Location(null, null, null, path, "", null);
            if (bytes == null) throw fail("S2_FILE_SET", Stage.AUTHOR, "Missing author file bytes", file);
            int maximum = HEADER_PATH.equals(path) ? MAX_HEADER_BYTES : MAX_FILE_BYTES;
            if (bytes.length > maximum) throw fail("S2_BUDGET", Stage.PARSE, "Author file byte budget exceeded", file.atByte(0));
            total += bytes.length;
            if (total > MAX_PACKAGE_BYTES) throw fail("S2_BUDGET", Stage.PARSE, "Package byte budget exceeded", file.atByte(0));
        }
        if (!files.containsKey(HEADER_PATH)) throw fail("S2_FILE_SET", Stage.AUTHOR, "Missing author manifest", source);
    }

    private static Map<String, byte[]> snapshot(Map<String, byte[]> files) {
        Map<String, byte[]> result = new TreeMap<>();
        files.forEach((path, bytes) -> result.put(path, bytes.clone()));
        return Collections.unmodifiableMap(result);
    }

    private AuthorPackage readSnapshot(Map<String, byte[]> files) {
        return readSnapshot(files, Schema2ValidationCodec.rawZip(files));
    }

    /** Only a verified ZIP reader capability can bind author files to original transport bytes. */
    AuthorPackage readArtifact(Schema2ZipReader.DecodedZip artifact) {
        try {
            checkRawBudget(artifact.authorFiles());
        } catch (Schema2Exception failure) {
            Diagnostic diagnostic = failure.diagnostic();
            String rawSha = Schema2ValidationCodec.sha256(artifact.originalZip());
            throw new Schema2Exception(new Diagnostic(diagnostic.code(), diagnostic.stage(), diagnostic.message(),
                    diagnostic.locations().stream().map(source -> new Location(source.moduleKey(), source.releaseVersion(),
                            rawSha, source.path(), source.pointer(), source.byteOffset())).toList()));
        }
        return readSnapshot(artifact.authorFiles(), artifact.originalZip());
    }

    private AuthorPackage readSnapshot(Map<String, byte[]> files, byte[] rawZip) {
        String rawSha = Schema2ValidationCodec.sha256(rawZip);
        Location anonymous = new Location(null, null, rawSha, HEADER_PATH, "", null);
        Object parsedHeader = Schema2StrictJson.parse(files.get(HEADER_PATH), anonymous);
        Location decodedSource = manifestSource(parsedHeader, anonymous);
        Map<String, Object> header = shape(parsedHeader,
                Set.of("author_schema_version", "module_key", "release_version", "package_display_name", "role",
                        "dependencies", "compatible_combinations", "load_order_constraints", "files"), Set.of(), decodedSource);
        int schema = integer(header.get("author_schema_version"), 0, Integer.MAX_VALUE, decodedSource.child("author_schema_version"));
        if (schema != 2) throw fail("S2_SCHEMA_UNSUPPORTED", Stage.AUTHOR, "Only author schema 2 belongs to this reader", decodedSource.child("author_schema_version"));
        String module = moduleKey(header.get("module_key"), decodedSource.child("module_key"));
        String version = version(header.get("release_version"), decodedSource.child("release_version"));
        Location root = new Location(module, version, rawSha, HEADER_PATH, "", null);
        String displayName = text(header.get("package_display_name"), 120, root.child("package_display_name"));
        Role role = switch (string(header.get("role"), root.child("role"))) {
            case "base" -> Role.BASE;
            case "extension" -> Role.EXTENSION;
            case "compatibility" -> Role.COMPATIBILITY;
            default -> throw fail("S2_TYPE", Stage.AUTHOR, "Unknown package role", root.child("role"));
        };
        List<Dependency> dependencies = dependencies(header.get("dependencies"), module, root.child("dependencies"));
        List<Map<String, String>> combinations = combinations(header.get("compatible_combinations"), dependencies, root.child("compatible_combinations"));
        List<Located<OrderConstraint>> constraints = constraints(header.get("load_order_constraints"), module, dependencies,
                root.child("load_order_constraints"));
        if ((role == Role.BASE && (!dependencies.isEmpty() || !constraints.isEmpty()))
                || (role != Role.BASE && dependencies.isEmpty())) {
            throw fail("S2_SHAPE", Stage.AUTHOR, "Base has no dependencies; other roles require direct dependencies", root.child("dependencies"));
        }
        List<FileDeclaration> declarations = files(header.get("files"), files.keySet(), root.child("files"));
        List<Located<Definition>> definitions = new ArrayList<>();
        List<Located<Entry>> entries = new ArrayList<>();
        List<Located<Change>> changes = new ArrayList<>();
        Map<Identity, Location> identities = new HashMap<>();
        Map<FieldKey, Location> changedFields = new HashMap<>();
        for (FileDeclaration declaration : declarations) {
            Location file = new Location(module, version, rawSha, declaration.path, "", null);
            List<?> rows = array(Schema2StrictJson.parse(files.get(declaration.path), file), file);
            for (int i = 0; i < rows.size(); i++) {
                Location location = file.child(Integer.toString(i));
                switch (declaration.kind) {
                    case "definitions" -> {
                        if (definitions.size() >= 64) throw fail("S2_BUDGET", Stage.PARSE, "Definition count budget exceeded", location);
                        Definition value = definition(rows.get(i), location);
                        originalOwner(module, value.identity(), location.child("identity").child("module_key"));
                        duplicateIdentity(identities, value.identity(), location);
                        definitions.add(new Located<>(value, location));
                    }
                    case "entries" -> {
                        if (entries.size() >= 128) throw fail("S2_BUDGET", Stage.PARSE, "Entry count budget exceeded", location);
                        Entry value = entry(rows.get(i), location);
                        originalOwner(module, value.identity(), location.child("identity").child("module_key"));
                        duplicateIdentity(identities, value.identity(), location);
                        entries.add(new Located<>(value, location));
                    }
                    case "changes" -> {
                        if (changes.size() >= 256) throw fail("S2_BUDGET", Stage.PARSE, "Change count budget exceeded", location);
                        Change value = change(rows.get(i), location);
                        Location previous = changedFields.putIfAbsent(new FieldKey(value.target(), value.field()), location);
                        if (previous != null) throw fail("S2_DUPLICATE_FIELD", Stage.AUTHOR, "A package modifies one target field twice", previous, location);
                        changes.add(new Located<>(value, location));
                    }
                    default -> throw new AssertionError(declaration.kind);
                }
            }
        }
        definitions.sort(Comparator.comparing(row -> row.value.identity(), RULE_ORDER));
        entries.sort(Comparator.comparing(row -> row.value.identity(), ENTRY_ORDER));
        changes.sort(Comparator.<Located<Change>, Identity>comparing(row -> row.value.target(), IDENTITY_ORDER)
                .thenComparing(row -> row.value.field().key()));
        Map<String, Object> logic = json("author_schema_version", 2, "module_key", module,
                "release_version", version, "role", roleName(role),
                "dependencies", dependencies.stream().sorted(Comparator.comparing(Dependency::moduleKey))
                        .map(dependency -> json("module_key", dependency.moduleKey(),
                                "supported_versions", dependency.supportedVersions().stream().sorted().toList())).toList(),
                "compatible_combinations", combinations.stream().sorted(Schema2CompositionKernel::compareJson).toList(),
                "load_order_constraints", constraints.stream().map(Located::value)
                        .sorted(Comparator.comparing(OrderConstraint::before).thenComparing(OrderConstraint::after))
                        .map(edge -> json("before", edge.before(), "after", edge.after())).toList(),
                "definitions", definitions.stream().map(row -> definitionJson(row.value)).toList(),
                "entries", entries.stream().map(row -> entryJson(row.value)).toList(),
                "changes", changes.stream().map(row -> changeJson(row.value)).toList());
        return new AuthorPackage(module, version, displayName, role, dependencies, combinations,
                constraints, definitions, entries, changes, rawZip, logic, root);
    }

    private static Location manifestSource(Object parsedHeader, Location source) {
        if (!(parsedHeader instanceof Map<?, ?> row)) return source;
        String module = row.get("module_key") instanceof String value && value.matches("[a-z][a-z0-9_]{0,63}") ? value : null;
        String version = row.get("release_version") instanceof String value && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") ? value : null;
        return new Location(module, version, source.rawZipSha256(), source.path(), source.pointer(), source.byteOffset());
    }

    private static List<Dependency> dependencies(Object input, String module, Location source) {
        List<?> rows = array(input, source);
        List<Dependency> result = new ArrayList<>();
        Map<String, Location> seen = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Location rowSource = source.child(Integer.toString(i));
            Map<String, Object> row = shape(rows.get(i), Set.of("module_key", "supported_versions"), Set.of(), rowSource);
            String key = moduleKey(row.get("module_key"), rowSource.child("module_key"));
            if (key.equals(module)) throw fail("S2_SHAPE", Stage.AUTHOR, "Self dependency is forbidden", rowSource.child("module_key"));
            Location previous = seen.putIfAbsent(key, rowSource);
            if (previous != null) throw fail("S2_SHAPE", Stage.AUTHOR, "Duplicate dependency", previous, rowSource);
            List<?> versions = array(row.get("supported_versions"), rowSource.child("supported_versions"));
            if (versions.isEmpty()) throw fail("S2_SHAPE", Stage.AUTHOR, "Supported versions must be nonempty", rowSource.child("supported_versions"));
            List<String> supported = new ArrayList<>();
            Map<String, Location> seenVersions = new HashMap<>();
            for (int j = 0; j < versions.size(); j++) {
                Location valueSource = rowSource.child("supported_versions").child(Integer.toString(j));
                String value = version(versions.get(j), valueSource);
                Location old = seenVersions.putIfAbsent(value, valueSource);
                if (old != null) throw fail("S2_SHAPE", Stage.AUTHOR, "Duplicate supported version", old, valueSource);
                supported.add(value);
            }
            result.add(new Dependency(key, supported));
        }
        return result;
    }

    private static List<Map<String, String>> combinations(Object input, List<Dependency> dependencies, Location source) {
        List<?> rows = array(input, source);
        if (rows.isEmpty()) throw fail("S2_COMBINATION_SHAPE", Stage.AUTHOR, "Compatible combinations must be nonempty", source);
        Set<String> dependencyKeys = new HashSet<>();
        Map<String, Set<String>> projections = new HashMap<>();
        for (Dependency dependency : dependencies) {
            dependencyKeys.add(dependency.moduleKey());
            projections.put(dependency.moduleKey(), new HashSet<>());
        }
        List<Map<String, String>> result = new ArrayList<>();
        Map<Map<String, String>, Location> seen = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Location location = source.child(Integer.toString(i));
            if (!(rows.get(i) instanceof Map<?, ?> row) || !row.keySet().equals(dependencyKeys)) {
                throw fail("S2_COMBINATION_SHAPE", Stage.AUTHOR, "A combination must map every direct dependency exactly once", location);
            }
            Map<String, String> values = new TreeMap<>();
            for (String key : dependencyKeys) {
                String value = version(row.get(key), location.child(key));
                values.put(key, value);
                projections.get(key).add(value);
            }
            Location previous = seen.putIfAbsent(values, location);
            if (previous != null) throw fail("S2_COMBINATION_SHAPE", Stage.AUTHOR, "Duplicate compatible combination", previous, location);
            result.add(Map.copyOf(values));
        }
        for (int i = 0; i < dependencies.size(); i++) {
            Dependency dependency = dependencies.get(i);
            if (!new HashSet<>(dependency.supportedVersions()).equals(projections.get(dependency.moduleKey()))) {
                Location field = new Location(source.moduleKey(), source.releaseVersion(), source.rawZipSha256(),
                        source.path(), "/dependencies/" + i + "/supported_versions", null);
                throw fail("S2_VERSION_SET_MISMATCH", Stage.AUTHOR, "Supported versions must equal the complete combination projection", field, source);
            }
        }
        return result;
    }

    private static List<Located<OrderConstraint>> constraints(Object input, String module,
                                                             List<Dependency> dependencies, Location source) {
        Set<String> endpoints = new HashSet<>();
        endpoints.add(module);
        for (Dependency dependency : dependencies) endpoints.add(dependency.moduleKey());
        List<?> rows = array(input, source);
        List<Located<OrderConstraint>> result = new ArrayList<>();
        Map<OrderConstraint, Location> seen = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Location location = source.child(Integer.toString(i));
            Map<String, Object> row = shape(rows.get(i), Set.of("before", "after"), Set.of(), location);
            String before = moduleKey(row.get("before"), location.child("before"));
            String after = moduleKey(row.get("after"), location.child("after"));
            if (!endpoints.contains(before)) throw fail("S2_ORDER_ENDPOINT", Stage.AUTHOR, "Order endpoint is not self or a direct dependency", location.child("before"));
            if (!endpoints.contains(after)) throw fail("S2_ORDER_ENDPOINT", Stage.AUTHOR, "Order endpoint is not self or a direct dependency", location.child("after"));
            if (before.equals(after)) throw fail("S2_ORDER_CYCLE", Stage.AUTHOR, "Self order constraint is forbidden", location);
            OrderConstraint edge = new OrderConstraint(before, after);
            Location previous = seen.putIfAbsent(edge, location);
            if (previous != null) throw fail("S2_DUPLICATE_CONSTRAINT", Stage.AUTHOR, "Duplicate explicit order constraint", previous, location);
            result.add(new Located<>(edge, location));
        }
        return result;
    }

    private static List<FileDeclaration> files(Object input, Set<String> actualFiles, Location source) {
        List<?> rows = array(input, source);
        if (rows.size() > MAX_AUTHOR_FILES) throw fail("S2_BUDGET", Stage.PARSE, "Author file count budget exceeded", source);
        Map<String, Location> paths = new HashMap<>();
        List<FileDeclaration> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Location location = source.child(Integer.toString(i));
            Map<String, Object> row = shape(rows.get(i), Set.of("kind", "path"), Set.of(), location);
            String kind = string(row.get("kind"), location.child("kind"));
            if (!Set.of("definitions", "entries", "changes").contains(kind)) throw fail("S2_TYPE", Stage.AUTHOR, "Unknown author file kind", location.child("kind"));
            String path = string(row.get("path"), location.child("path"));
            if (path.isEmpty() || !path.chars().allMatch(c -> c >= 0x20 && c <= 0x7e) || HEADER_PATH.equals(path)) {
                throw fail("S2_FILE_SET", Stage.AUTHOR, "Author path must name one ASCII data file", location.child("path"));
            }
            Location previous = paths.putIfAbsent(path, location);
            if (previous != null) throw fail("S2_FILE_SET", Stage.AUTHOR, "Duplicate author file path", previous, location);
            result.add(new FileDeclaration(kind, path));
        }
        Set<String> expected = new HashSet<>(paths.keySet());
        expected.add(HEADER_PATH);
        if (!expected.equals(actualFiles)) throw fail("S2_FILE_SET", Stage.AUTHOR, "Manifest and actual author files differ", source);
        // Source file order has no semantic priority, including duplicate diagnostics and budgets.
        result.sort(Comparator.comparing(FileDeclaration::path));
        return result;
    }

    private static Definition definition(Object input, Location source) {
        Map<String, Object> initial = object(input, source);
        RuleIdentity identity = ruleIdentity(required(initial, "identity", source), source.child("identity"));
        if (executionType(identity.ruleType())) return executionDefinition(input, identity, source);
        if (newAdvancementType(identity.ruleType()))
            return advancementDefinition(input, identity, source);
        boolean catalogItem = identity.ruleType().equals("character.language") || identity.ruleType().equals("character.tool");
        boolean characterSource = sourceType(identity.ruleType());
        Set<String> required = identity.ruleType().equals("character.class_progression")
                ? Set.of("identity", "display_name", "description", "source_page", "owner_class", "hit_die_sides",
                        "proficiency_bonus_profile", "supported_maximum_level", "advancement_scope")
                : identity.ruleType().equals("character.resource")
                ? Set.of("identity", "display_name", "description", "source_page", "owner_class",
                        "maximum_profile", "recovery_profile", "execution_mode")
                : catalogItem
                ? Set.of("identity", "display_name", "description", "category", "source_page")
                : characterSource ? (identity.ruleType().equals("character.subrace")
                    ? Set.of("identity", "display_name", "description", "source_page", "parent_race")
                    : Set.of("identity", "display_name", "description", "source_page"))
                : Set.of("identity", "display_name", "minimum_enabled_members");
        Map<String, Object> row = shape(input, required, identity.ruleType().equals("character.class_progression")
                ? Set.of("author_note", "multiclass_prerequisite") : Set.of("author_note"), source);
        String displayName = text(row.get("display_name"), 120, source.child("display_name"));
        String note = row.containsKey("author_note") ? text(row.get("author_note"), 1000, source.child("author_note")) : null;
        return switch (identity.ruleType()) {
            case "character.language" -> new LanguageDefinition(identity, displayName,
                    text(row.get("description"), 1000, source.child("description")),
                    category(row.get("category"), source.child("category")),
                    integer(row.get("source_page"), 3, 74, source.child("source_page")), note);
            case "character.tool" -> new ToolDefinition(identity, displayName,
                    text(row.get("description"), 1000, source.child("description")),
                    toolCategory(row.get("category"), source.child("category")),
                    integer(row.get("source_page"), 3, 74, source.child("source_page")), note);
            case "catalog.language_set" -> new LanguageSetDefinition(identity, displayName,
                    integer(row.get("minimum_enabled_members"), 1, 128, source.child("minimum_enabled_members")), note);
            case "catalog.tool_set" -> new ToolSetDefinition(identity, displayName,
                    integer(row.get("minimum_enabled_members"), 1, 128, source.child("minimum_enabled_members")), note);
            case "character.class_progression" -> {
                ProgressionScope scope = enumValue(ProgressionScope.class, row.get("advancement_scope"), source.child("advancement_scope"));
                if (scope == ProgressionScope.CHARACTER_ADVANCEMENT && !row.containsKey("multiclass_prerequisite"))
                    throw fail("S2_SHAPE", Stage.AUTHOR, "Character advancement requires an explicit multiclass prerequisite", source.child("multiclass_prerequisite"));
                yield new ClassProgressionDefinition(identity, displayName,
                    text(row.get("description"), 1000, source.child("description")),
                    integer(row.get("source_page"), 3, 74, source.child("source_page")),
                    typedReference(row.get("owner_class"), "character.class", source.child("owner_class")),
                    hitDieSides(row.get("hit_die_sides"), source.child("hit_die_sides")),
                    profile(Field.PROFICIENCY_BONUS_PROFILE, row.get("proficiency_bonus_profile"), source.child("proficiency_bonus_profile")),
                    integer(row.get("supported_maximum_level"), 1, 20, source.child("supported_maximum_level")),
                    scope,
                    row.containsKey("multiclass_prerequisite") ? prerequisite(row.get("multiclass_prerequisite"), source.child("multiclass_prerequisite")) : null, note);
            }
            case "character.resource" -> new ResourceDefinition(identity, displayName,
                    text(row.get("description"), 1000, source.child("description")),
                    integer(row.get("source_page"), 3, 74, source.child("source_page")),
                    typedReference(row.get("owner_class"), "character.class", source.child("owner_class")),
                    profile(Field.MAXIMUM_PROFILE, row.get("maximum_profile"), source.child("maximum_profile")),
                    profile(Field.RECOVERY_PROFILE, row.get("recovery_profile"), source.child("recovery_profile")),
                    enumValue(ResourceExecution.class, row.get("execution_mode"), source.child("execution_mode")), note);
            default -> new CharacterSourceDefinition(identity, displayName,
                    text(row.get("description"), 1000, source.child("description")),
                    integer(row.get("source_page"), 3, 74, source.child("source_page")),
                    identity.ruleType().equals("character.subrace")
                            ? typedReference(row.get("parent_race"), "character.race", source.child("parent_race")) : null, note);
        };
    }

    private static boolean executionType(String type) {
        return Set.of("equipment.item", "adventure.health", "adventure.vital_effect").contains(type);
    }
    private static Definition executionDefinition(Object input, RuleIdentity identity, Location source) {
        Set<String> required = new HashSet<>(Set.of("identity", "display_name", "description", "source_page", "algorithm"));
        Set<String> optional = new HashSet<>(Set.of("author_note"));
        if (identity.ruleType().equals("equipment.item")) {
            required.addAll(Set.of("kind", "armor_class", "strength_minimum", "stealth_disadvantage", "hands", "proficiency"));
            optional.add("stealth_skill");
        } else if (identity.ruleType().equals("adventure.vital_effect")) {
            required.addAll(Set.of("kind", "dice_count", "dice_sides", "modifier")); optional.add("damage_type");
        }
        var row = shape(input, required, optional, source);
        String name = text(row.get("display_name"), 120, source.child("display_name"));
        String description = text(row.get("description"), 1000, source.child("description"));
        int page = sourcePage(identity.ruleType(), row.get("source_page"), source.child("source_page"));
        String algorithm = mechanismAlgorithm(identity.ruleType(), row.get("algorithm"), source.child("algorithm"));
        String note = row.containsKey("author_note") ? text(row.get("author_note"), 1000, source.child("author_note")) : null;
        if (identity.ruleType().equals("adventure.health")) return new HealthDefinition(identity, name, description, page, algorithm, note);
        if (identity.ruleType().equals("equipment.item")) {
            EquipmentKind kind = enumValue(EquipmentKind.class, row.get("kind"), source.child("kind"));
            int ac = integer(row.get("armor_class"), 0, 20, source.child("armor_class"));
            int strength = integer(row.get("strength_minimum"), 0, 15, source.child("strength_minimum"));
            int hands = integer(row.get("hands"), 0, 2, source.child("hands"));
            boolean stealth = booleanAtom(row.get("stealth_disadvantage"), source.child("stealth_disadvantage"));
            RuleIdentity proficiency = typedReference(row.get("proficiency"), kind == EquipmentKind.TOOL ? "character.tool" : "character.proficiency", source.child("proficiency"));
            RuleIdentity skill = row.containsKey("stealth_skill") ? typedReference(row.get("stealth_skill"), "character.proficiency", source.child("stealth_skill")) : null;
            boolean armor = Set.of(EquipmentKind.LIGHT_ARMOR, EquipmentKind.MEDIUM_ARMOR, EquipmentKind.HEAVY_ARMOR).contains(kind);
            if (stealth != (skill != null) || (armor ? hands != 0 || ac < 10 || !Set.of(0,13,15).contains(strength)
                    || (kind != EquipmentKind.HEAVY_ARMOR && strength != 0)
                    : strength != 0 || stealth || (kind == EquipmentKind.SHIELD ? hands != 1 || ac != 2 : hands < 1 || ac != 0)))
                throw fail("S2_TYPE", Stage.AUTHOR, "Equipment kind and complete mechanics disagree", source);
            return new ItemDefinition(identity,name,description,page,kind,ac,strength,stealth,hands,proficiency,skill,algorithm,note);
        }
        VitalEffectKind kind = enumValue(VitalEffectKind.class, row.get("kind"), source.child("kind"));
        int count = integer(row.get("dice_count"), 0, 20, source.child("dice_count"));
        int sides = integer(row.get("dice_sides"), 0, 12, source.child("dice_sides"));
        int modifier = integer(row.get("modifier"), -1000, 1000, source.child("modifier"));
        DamageType damage = row.containsKey("damage_type") ? enumValue(DamageType.class, row.get("damage_type"), source.child("damage_type")) : null;
        if ((kind == VitalEffectKind.DAMAGE) != (damage != null) || (count == 0 ? sides != 0 : !Set.of(4,6,8,10,12).contains(sides)))
            throw fail("S2_TYPE", Stage.AUTHOR, "Vital effect kind and complete dice mechanics disagree", source);
        return new VitalEffectDefinition(identity,name,description,page,kind,count,sides,modifier,damage,algorithm,note);
    }
    private static boolean booleanAtom(Object value, Location source) {
        if (!(value instanceof Boolean result)) throw fail("S2_TYPE", Stage.AUTHOR, "Expected boolean", source);
        return result;
    }

    private static Definition advancementDefinition(Object input, RuleIdentity identity, Location source) {
        Set<String> required = new HashSet<>(Set.of("identity", "display_name"));
        if (!identity.ruleType().equals("catalog.proficiency_set")) required.addAll(Set.of("description", "source_page"));
        required.addAll(switch (identity.ruleType()) {
            case "character.subclass" -> Set.of("owner_class", "selection_level");
            case "character.feature" -> Set.of("owner", "level", "feature_kind", "execution_mode", "execution_algorithm");
            case "character.feat" -> Set.of("prerequisite", "execution_mode", "execution_algorithm");
            case "character.proficiency" -> Set.of("category");
            case "adventure.rest", "adventure.check" -> Set.of("algorithm");
            default -> Set.of("category", "minimum_enabled_members");
        });
        var row = shape(input, required, identity.ruleType().equals("character.proficiency")
                ? Set.of("author_note", "ability_key") : Set.of("author_note"), source);
        String name = text(row.get("display_name"), 120, source.child("display_name"));
        String note = row.containsKey("author_note") ? text(row.get("author_note"), 1000, source.child("author_note")) : null;
        ProficiencyCategory category = row.containsKey("category") ? proficiencyCategory(identity.ruleType(), row.get("category"), source.child("category")) : null;
        if (identity.ruleType().equals("catalog.proficiency_set")) return new ProficiencySetDefinition(identity, name, category,
                integer(row.get("minimum_enabled_members"), 1, 128, source.child("minimum_enabled_members")), note);
        String description = text(row.get("description"), 1000, source.child("description"));
        int page = sourcePage(identity.ruleType(), row.get("source_page"), source.child("source_page"));
        if (identity.ruleType().equals("adventure.rest"))
            return new RestDefinition(identity, name, description, page, mechanismAlgorithm(identity.ruleType(), row.get("algorithm"), source.child("algorithm")), note);
        if (identity.ruleType().equals("adventure.check"))
            return new CheckDefinition(identity, name, description, page, mechanismAlgorithm(identity.ruleType(), row.get("algorithm"), source.child("algorithm")), note);
        if (identity.ruleType().equals("character.subclass")) return new SubclassDefinition(identity, name, description, page,
                typedReference(row.get("owner_class"), "character.class", source.child("owner_class")),
                integer(row.get("selection_level"), 1, 20, source.child("selection_level")), note);
        if (identity.ruleType().equals("character.proficiency")) {
            String ability = row.containsKey("ability_key") ? abilityKey(row.get("ability_key"), source.child("ability_key")) : null;
            if ((category == ProficiencyCategory.SKILL) != (ability != null))
                throw fail("S2_TYPE", Stage.AUTHOR, "Only skill proficiencies declare an ability", source);
            return new ProficiencyDefinition(identity, name, description, page, category, ability, note);
        }
        FeatureExecution mode = featureExecution(identity.ruleType(), row.get("execution_mode"), source.child("execution_mode"));
        String algorithm = executionAlgorithmAtom(identity.ruleType(), row.get("execution_algorithm"), source.child("execution_algorithm"));
        if (!executionAlgorithms(mode).contains(algorithm))
            throw fail("S2_TYPE", Stage.AUTHOR, "Feature mode and algorithm disagree", source.child("execution_algorithm"));
        if (identity.ruleType().equals("character.feat")) {
            return new FeatDefinition(identity, name, description, page, prerequisite(row.get("prerequisite"), source.child("prerequisite")), mode, algorithm, note);
        }
        return new FeatureDefinition(identity, name, description, page,
                typedReference(row.get("owner"), "character.class|character.subclass", source.child("owner")),
                integer(row.get("level"), 1, 20, source.child("level")), enumValue(FeatureKind.class, row.get("feature_kind"), source.child("feature_kind")), mode, algorithm, note);
    }

    private static String abilityKey(Object value, Location source) {
        String key = string(value, source);
        if (!Set.of("ability.strength", "ability.dexterity", "ability.constitution", "ability.intelligence", "ability.wisdom", "ability.charisma").contains(key))
            throw fail("S2_TYPE", Stage.AUTHOR, "Unknown ability key", source);
        return key;
    }

    private static ProficiencyCategory proficiencyCategory(String ruleType, Object value, Location source) {
        ProficiencyCategory category = enumValue(ProficiencyCategory.class, value, source);
        if (ruleType.equals("character.proficiency") && category == ProficiencyCategory.TOOL)
            throw fail("S2_TYPE", Stage.AUTHOR, "Tool proficiency requires a character.tool identity", source);
        return category;
    }

    private static FeatureExecution featureExecution(String ruleType, Object value, Location source) {
        FeatureExecution mode = enumValue(FeatureExecution.class, value, source);
        if (ruleType.equals("character.feat") && mode != FeatureExecution.BLOCKED)
            throw fail("S2_TYPE", Stage.AUTHOR, "Feat execution is currently blocked; acquisition is separate", source);
        return mode;
    }

    private static Set<String> executionAlgorithms(FeatureExecution mode) {
        return switch (mode) {
            case AUTOMATIC -> Set.of("AUTOMATIC_RESOURCE_LIFECYCLE_V1");
            case DM_ADJUDICATION -> Set.of("BOUNDED_SUBCLASS_SELECTION_V1", "BOUNDED_FEATURE_SELECTION_V1", "BOUNDED_DM_ADJUDICATION_V1");
            case BLOCKED -> Set.of("BLOCKED_ISSUE_8_V1", "BLOCKED_SPELL_SYSTEM_V1", "BLOCKED_DOWNSTREAM_SYSTEM_V1");
        };
    }

    private static String executionAlgorithmAtom(String ruleType, Object value, Location source) {
        String algorithm = identifier(value, "[A-Z][A-Z0-9_]{0,127}", source);
        boolean allowed = ruleType.equals("character.feat") ? algorithm.equals("BLOCKED_DOWNSTREAM_SYSTEM_V1")
                : Arrays.stream(FeatureExecution.values()).anyMatch(mode -> executionAlgorithms(mode).contains(algorithm));
        if (!allowed) throw fail("S2_TYPE", Stage.AUTHOR, "Unsupported execution algorithm for this rule type", source);
        return algorithm;
    }

    private static AbilityPrerequisite prerequisite(Object value, Location source) {
        var root = shape(value, Set.of("all_of"), Set.of(), source);
        var groups = array(root.get("all_of"), source.child("all_of"));
        if (groups.size() > 6) throw fail("S2_BUDGET", Stage.AUTHOR, "Ability prerequisite group budget", source);
        List<RequirementGroup> result = new ArrayList<>(); Set<String> abilities = new HashSet<>();
        for (int g = 0; g < groups.size(); g++) {
            Location groupSource = source.child("all_of").child(Integer.toString(g));
            var alternatives = array(shape(groups.get(g), Set.of("any_of"), Set.of(), groupSource).get("any_of"), groupSource.child("any_of"));
            if (alternatives.isEmpty() || alternatives.size() > 2) throw fail("S2_BUDGET", Stage.AUTHOR, "Ability alternative budget", groupSource);
            List<AbilityMinimum> terms = new ArrayList<>();
            for (int a = 0; a < alternatives.size(); a++) {
                Location termSource = groupSource.child("any_of").child(Integer.toString(a));
                var term = shape(alternatives.get(a), Set.of("ability", "minimum"), Set.of(), termSource);
                String key = abilityKey(term.get("ability"), termSource.child("ability"));
                if (!abilities.add(key)) throw fail("S2_TYPE", Stage.AUTHOR, "Duplicate prerequisite ability", termSource);
                terms.add(new AbilityMinimum(key, integer(term.get("minimum"), 1, 20, termSource.child("minimum"))));
            }
            result.add(new RequirementGroup(terms));
        }
        return new AbilityPrerequisite(result);
    }

    private static boolean optionalField(Field field) {
        return Set.of(Field.AUTHOR_NOTE, Field.ABILITY_KEY, Field.MULTICLASS_PREREQUISITE, Field.STEALTH_SKILL, Field.DAMAGE_TYPE).contains(field);
    }

    private static boolean clearableField(Field field) { return Set.of(Field.AUTHOR_NOTE, Field.STEALTH_SKILL, Field.DAMAGE_TYPE).contains(field); }

    private static int hitDieSides(Object value, Location source) {
        int sides = integer(value, 6, 12, source);
        if (!Set.of(6, 8, 10, 12).contains(sides)) throw fail("S2_TYPE", Stage.AUTHOR, "Unsupported hit die", source);
        return sides;
    }

    private static String profile(Field field, Object value, Location source) {
        String result = text(value, field == Field.RECOVERY_PROFILE ? 200 : 1000, source);
        try {
            if (field == Field.PROFICIENCY_BONUS_PROFILE) AdvancementValueProfile.parseProficiencyBonus(result);
            else if (field == Field.MAXIMUM_PROFILE) AdvancementValueProfile.parse(result);
            else ResourceRecoveryProfile.parse(result);
        } catch (IllegalArgumentException invalid) {
            throw fail("S2_TYPE", Stage.AUTHOR, "Malformed complete resource or proficiency profile", source);
        }
        return result;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Object value, Location source) {
        try { return Enum.valueOf(type, string(value, source)); }
        catch (IllegalArgumentException invalid) { throw fail("S2_TYPE", Stage.AUTHOR, "Unsupported " + type.getSimpleName(), source); }
    }

    private static GrantSpec grant(Object input, Location source) {
        Map<String, Object> initial = object(input, source);
        String category = string(required(initial, "category", source), source.child("category"));
        GrantCategory typedCategory;
        try { typedCategory = GrantCategory.valueOf(category); }
        catch (IllegalArgumentException invalid) { throw fail("S2_TYPE", Stage.AUTHOR, "Unknown proficiency grant category", source.child("category")); }
        String kind = string(required(initial, "kind", source), source.child("kind"));
        String targetType = typedCategory == GrantCategory.LANGUAGE ? "character.language" : "character.tool";
        if (kind.equals("FIXED")) {
            Map<String, Object> row = shape(input, Set.of("category", "kind", "target"), Set.of(), source);
            return new FixedGrant(typedCategory, typedReference(row.get("target"), targetType, source.child("target")));
        }
        if (kind.equals("CHOICE")) {
            Map<String, Object> row = shape(input, Set.of("category", "kind", "candidates", "count"), Set.of(), source);
            String setType = typedCategory == GrantCategory.LANGUAGE ? "catalog.language_set" : "catalog.tool_set";
            return new ChoiceGrant(typedCategory, typedReference(row.get("candidates"), setType, source.child("candidates")),
                    integer(row.get("count"), 1, MAX_GRANT_CHOICE_COUNT, source.child("count")));
        }
        throw fail("S2_TYPE", Stage.AUTHOR, "Unknown proficiency grant kind", source.child("kind"));
    }

    private static GrowthGrantSpec growthGrant(Object input, Location source) {
        var initial = object(input, source);
        String kind = string(required(initial, "kind", source), source.child("kind"));
        if (kind.equals("FIXED")) {
            var row = shape(input, Set.of("kind", "target"), Set.of(), source);
            return new FixedGrowthGrant(typedReference(row.get("target"), "character.proficiency|character.tool", source.child("target")));
        }
        if (kind.equals("CHOICE")) {
            var row = shape(input, Set.of("kind", "candidates", "count"), Set.of(), source);
            return new ChoiceGrowthGrant(typedReference(row.get("candidates"), "catalog.proficiency_set", source.child("candidates")),
                    integer(row.get("count"), 1, MAX_GRANT_CHOICE_COUNT, source.child("count")));
        }
        throw fail("S2_TYPE", Stage.AUTHOR, "Unknown growth proficiency grant kind", source);
    }

    private static Entry entry(Object input, Location source) {
        Map<String, Object> initial = object(input, source);
        Identity identity = identity(required(initial, "identity", source), source.child("identity"));
        if (!(identity instanceof EntryIdentity entry)) throw fail("S2_TYPE", Stage.AUTHOR, "Entry declaration requires an entry identity", source.child("identity"));
        if (entry.collectionKey().equals("levels")) {
            var row = shape(input, Set.of("identity", "level", "asi_available"), Set.of("author_note"), source);
            if (!(row.get("asi_available") instanceof Boolean asi)) throw fail("S2_TYPE", Stage.AUTHOR, "ASI marker requires boolean", source.child("asi_available"));
            return new ClassLevelEntry(entry, integer(row.get("level"), 1, 20, source.child("level")), asi,
                    row.containsKey("author_note") ? text(row.get("author_note"), 1000, source.child("author_note")) : null);
        }
        Field reference = referenceField(entry);
        Map<String, Object> row = shape(input, Set.of("identity", reference.key()), Set.of("author_note"), source);
        String note = row.containsKey("author_note") ? text(row.get("author_note"), 1000, source.child("author_note")) : null;
        if (reference == Field.GRANT) return new ProficiencyGrantEntry(entry, grant(row.get("grant"), source.child("grant")), note);
        if (reference == Field.GROWTH_GRANT) return new GrowthProficiencyGrantEntry(entry, growthGrant(row.get("growth_grant"), source.child("growth_grant")), note);
        RuleIdentity target = typedReference(row.get(reference.key()), referenceType(reference), source.child(reference.key()));
        if (reference == Field.PROFICIENCY) return new ProficiencyMemberEntry(entry, target, note);
        return reference == Field.LANGUAGE ? new LanguageEntry(entry, target, note) : new ToolEntry(entry, target, note);
    }

    private static Change change(Object input, Location source) {
        Map<String, Object> initial = object(input, source);
        String op = string(required(initial, "op", source), source.child("op"));
        Set<String> required = switch (op) {
            case "set_field" -> Set.of("op", "target", "field", "value");
            case "clear_field" -> Set.of("op", "target", "field");
            case "set_enabled" -> Set.of("op", "target", "value");
            default -> throw fail("S2_TYPE", Stage.AUTHOR, "Unknown modification operation", source.child("op"));
        };
        Map<String, Object> row = shape(input, required, Set.of(), source);
        Identity target = identity(row.get("target"), source.child("target"));
        if (op.equals("set_enabled")) {
            if (!(row.get("value") instanceof Boolean value)) throw fail("S2_TYPE", Stage.AUTHOR, "Enabled requires a JSON boolean", source.child("value"));
            return new SetEnabled(target, value);
        }
        String key = string(row.get("field"), source.child("field"));
        Field field = null;
        for (Field item : domainFields(target)) if (item.key().equals(key)) field = item;
        if (field == null) throw fail("S2_FIELD_UNKNOWN", Stage.AUTHOR, "Unknown atomic domain field", source.child("field"));
        if (op.equals("clear_field")) {
            if (!clearableField(field)) throw fail("S2_FIELD_NOT_CLEARABLE", Stage.AUTHOR, "Field does not permit clearing", source.child("field"));
            return new ClearField(target, field);
        }
        Object value = row.get("value");
        FieldValue typed = switch (field) {
            case DISPLAY_NAME -> new TextValue(text(value, 120, source.child("value")));
            case DESCRIPTION, AUTHOR_NOTE -> new TextValue(text(value, 1000, source.child("value")));
            case CATEGORY -> new TextValue(((RuleIdentity) target).ruleType().equals("character.tool")
                    ? toolCategory(value, source.child("value")).name()
                    : Set.of("character.proficiency", "catalog.proficiency_set").contains(((RuleIdentity) target).ruleType())
                        ? proficiencyCategory(((RuleIdentity) target).ruleType(), value, source.child("value")).name() : category(value, source.child("value")).name());
            case SOURCE_PAGE -> new IntegerValue(sourcePage(((RuleIdentity) target).ruleType(), value, source.child("value")));
            case MINIMUM_ENABLED_MEMBERS -> new IntegerValue(integer(value, 1, 128, source.child("value")));
            case LANGUAGE, TOOL, PARENT_RACE, OWNER_CLASS, OWNER, PROFICIENCY, STEALTH_SKILL -> new IdentityValue(typedReference(value, referenceType(field), source.child("value")));
            case HIT_DIE_SIDES -> new IntegerValue(hitDieSides(value, source.child("value")));
            case SUPPORTED_MAXIMUM_LEVEL -> new IntegerValue(integer(value, 1, 20, source.child("value")));
            case PROFICIENCY_BONUS_PROFILE, MAXIMUM_PROFILE, RECOVERY_PROFILE -> new TextValue(profile(field, value, source.child("value")));
            case ADVANCEMENT_SCOPE -> new TextValue(enumValue(ProgressionScope.class, value, source.child("value")).name());
            case EXECUTION_MODE -> new TextValue(((RuleIdentity) target).ruleType().equals("character.resource")
                    ? enumValue(ResourceExecution.class, value, source.child("value")).name()
                    : featureExecution(((RuleIdentity) target).ruleType(), value, source.child("value")).name());
            case SELECTION_LEVEL, LEVEL -> new IntegerValue(integer(value, 1, 20, source.child("value")));
            case ASI_AVAILABLE -> {
                if (!(value instanceof Boolean booleanValue)) throw fail("S2_TYPE", Stage.AUTHOR, "ASI marker requires boolean", source.child("value"));
                yield new BooleanValue(booleanValue);
            }
            case FEATURE_KIND -> new TextValue(enumValue(FeatureKind.class, value, source.child("value")).name());
            case EXECUTION_ALGORITHM -> new TextValue(executionAlgorithmAtom(((RuleIdentity) target).ruleType(), value, source.child("value")));
            case ABILITY_KEY -> new TextValue(abilityKey(value, source.child("value")));
            case MULTICLASS_PREREQUISITE, PREREQUISITE -> new PrerequisiteValue(prerequisite(value, source.child("value")));
            case GROWTH_GRANT -> new GrowthGrantValue(growthGrant(value, source.child("value")));
            case KIND -> new TextValue(((RuleIdentity) target).ruleType().equals("equipment.item")
                    ? enumValue(EquipmentKind.class, value, source.child("value")).name() : enumValue(VitalEffectKind.class, value, source.child("value")).name());
            case ARMOR_CLASS -> new IntegerValue(integer(value, 0, 20, source.child("value")));
            case STRENGTH_MINIMUM -> { int minimum = integer(value, 0, 15, source.child("value")); if (!Set.of(0,13,15).contains(minimum)) throw fail("S2_TYPE", Stage.AUTHOR, "Unsupported armor strength minimum", source); yield new IntegerValue(minimum); }
            case STEALTH_DISADVANTAGE -> new BooleanValue(booleanAtom(value, source.child("value")));
            case HANDS -> new IntegerValue(integer(value, 0, 2, source.child("value")));
            case DICE_COUNT -> new IntegerValue(integer(value, 0, 20, source.child("value")));
            case DICE_SIDES -> { int sides = integer(value, 0, 12, source.child("value")); if (!Set.of(0,4,6,8,10,12).contains(sides)) throw fail("S2_TYPE", Stage.AUTHOR, "Unsupported vital die", source); yield new IntegerValue(sides); }
            case MODIFIER -> new IntegerValue(integer(value, -1000, 1000, source.child("value")));
            case DAMAGE_TYPE -> new TextValue(enumValue(DamageType.class, value, source.child("value")).name());
            case ALGORITHM -> new TextValue(mechanismAlgorithm(((RuleIdentity) target).ruleType(), value, source.child("value")));
            case GRANT -> new GrantValue(grant(value, source.child("value")));
            case ENABLED -> throw new AssertionError("Enabled is not a domain field");
        };
        return new SetField(target, field, typed);
    }

    private static Identity identity(Object input, Location source) {
        Map<String, Object> row = object(input, source);
        String kind = string(required(row, "kind", source), source.child("kind"));
        if (kind.equals("rule")) return ruleIdentity(input, source);
        if (!kind.equals("entry")) throw fail("S2_TYPE", Stage.AUTHOR, "Unknown identity kind", source.child("kind"));
        row = shape(input, Set.of("kind", "rule", "collection_key", "module_key", "entry_key"), Set.of(), source);
        RuleIdentity rule = ruleIdentity(row.get("rule"), source.child("rule"));
        boolean catalog = Set.of("catalog.language_set", "catalog.tool_set", "catalog.proficiency_set").contains(rule.ruleType());
        boolean progression = rule.ruleType().equals("character.class_progression");
        if (!catalog && !sourceType(rule.ruleType()) && !progression)
            throw fail("S2_TYPE", Stage.AUTHOR, "Entry owner requires a catalog set or character source", source.child("rule").child("rule_type"));
        String collection = string(row.get("collection_key"), source.child("collection_key"));
        if (!(progression ? Set.of("levels", "starting_proficiencies", "multiclass_proficiencies").contains(collection)
                : collection.equals(catalog ? "members" : "proficiency_grants")))
            throw fail("S2_TYPE", Stage.AUTHOR, "Unknown collection key for this owner type", source.child("collection_key"));
        String owner = moduleKey(row.get("module_key"), source.child("module_key"));
        String key = identifier(row.get("entry_key"), "[a-z][a-z0-9_]{0,63}", source.child("entry_key"));
        return new EntryIdentity(rule, collection, owner, key);
    }

    private static RuleIdentity ruleIdentity(Object input, Location source) {
        Map<String, Object> row = shape(input, Set.of("kind", "module_key", "rule_type", "rule_key"), Set.of(), source);
        if (!string(row.get("kind"), source.child("kind")).equals("rule")) throw fail("S2_TYPE", Stage.AUTHOR, "Expected a rule identity", source.child("kind"));
        String module = moduleKey(row.get("module_key"), source.child("module_key"));
        String type = string(row.get("rule_type"), source.child("rule_type"));
        if (!Set.of("character.language", "catalog.language_set", "character.tool", "catalog.tool_set",
                "character.class_progression", "character.resource", "character.subclass", "character.feature",
                "character.feat", "character.proficiency", "catalog.proficiency_set", "adventure.rest", "adventure.check", "equipment.item", "adventure.health", "adventure.vital_effect").contains(type) && !sourceType(type))
            throw fail("S2_TYPE", Stage.AUTHOR, "Unsupported finite rule type", source.child("rule_type"));
        String key = identifier(row.get("rule_key"), "[a-z][a-z0-9_.]{0,127}", source.child("rule_key"));
        String prefix = switch (type) {
            case "character.resource" -> "resource";
            case "character.class_progression" -> "progression";
            case "character.subclass" -> "subclass";
            case "character.feature" -> "feature";
            case "character.feat" -> "feat";
            case "character.proficiency" -> "proficiency";
            case "catalog.proficiency_set" -> "proficiency_set";
            case "adventure.rest" -> "rest";
            case "adventure.check" -> "check";
            case "equipment.item" -> "item";
            case "adventure.health" -> "health";
            case "adventure.vital_effect" -> "vital_effect";
            default -> null;
        };
        if (prefix != null && !key.matches(prefix + "[.][a-z][a-z0-9_]*(?:[.][a-z0-9_]+)*"))
            throw fail("S2_TYPE", Stage.AUTHOR, "New character rules require their domain prefix and nonempty key segments", source.child("rule_key"));
        return new RuleIdentity(module, type, key);
    }

    private static int sourcePage(String type, Object input, Location source) {
        return switch (type) {
            case "adventure.rest" -> integer(input, 87, 87, source);
            case "adventure.check" -> integer(input, 77, 77, source);
            case "equipment.item" -> integer(input, 62, 73, source);
            case "adventure.health" -> integer(input, 98, 98, source);
            case "adventure.vital_effect" -> integer(input, 96, 99, source);
            default -> integer(input, 3, 74, source);
        };
    }
    private static String mechanismAlgorithm(String type, Object input, Location source) {
        String expected = switch (type) {
            case "adventure.check" -> "SRD_2014_ABILITY_CHECK_V1";
            case "adventure.rest" -> "SRD_2014_REST_V1";
            case "equipment.item" -> "SRD_2014_EQUIPMENT_V1";
            case "adventure.health" -> "SRD_2014_HEALTH_V1";
            case "adventure.vital_effect" -> "DIRECT_VITAL_EFFECT_V1";
            default -> throw fail("S2_TYPE", Stage.AUTHOR, "Unsupported mechanism domain", source);
        };
        String actual = string(input, source);
        if (!expected.equals(actual)) throw fail("S2_TYPE", Stage.AUTHOR, "Unsupported mechanism algorithm", source);
        return actual;
    }

    private static RuleIdentity typedReference(Object input, String type, Location source) {
        RuleIdentity identity = ruleIdentity(input, source);
        if (!Arrays.asList(type.split("[|]")).contains(identity.ruleType())) throw fail("S2_TYPE", Stage.AUTHOR, "Reference requires " + type, source.child("rule_type"));
        return identity;
    }

    private static Field referenceField(EntryIdentity identity) {
        return switch (identity.rule().ruleType()) {
            case "catalog.language_set" -> Field.LANGUAGE;
            case "catalog.tool_set" -> Field.TOOL;
            case "catalog.proficiency_set" -> Field.PROFICIENCY;
            case "character.class_progression" -> identity.collectionKey().equals("levels") ? Field.LEVEL : Field.GROWTH_GRANT;
            default -> {
                if (sourceType(identity.rule().ruleType())) yield Field.GRANT;
                throw new IllegalArgumentException("Unsupported entry owner type");
            }
        };
    }
    private static String referenceType(Field field) {
        return switch (field) {
            case LANGUAGE -> "character.language";
            case TOOL -> "character.tool";
            case PARENT_RACE -> "character.race";
            case OWNER_CLASS -> "character.class";
            case OWNER -> "character.class|character.subclass";
            case PROFICIENCY -> "character.proficiency|character.tool";
            case STEALTH_SKILL -> "character.proficiency";
            default -> throw new IllegalArgumentException("Field is not a catalog reference");
        };
    }

    private static boolean sourceType(String type) {
        return Set.of("character.race", "character.subrace", "character.background", "character.class").contains(type);
    }

    private static boolean tracedCharacterType(String type) {
        return sourceType(type) || Set.of("character.class_progression", "character.resource", "character.subclass",
                "character.feature", "character.feat", "character.proficiency", "catalog.proficiency_set", "adventure.rest", "adventure.check", "equipment.item", "adventure.health", "adventure.vital_effect").contains(type);
    }

    private static void originalOwner(String module, Identity identity, Location source) {
        if (!identity.moduleKey().equals(module)) throw fail("S2_ORIGINAL_OWNER", Stage.AUTHOR, "New declarations must retain this package's original owner", source);
    }

    private static void duplicateIdentity(Map<Identity, Location> identities, Identity identity, Location location) {
        Location previous = identities.putIfAbsent(identity, location);
        if (previous != null) throw fail("S2_DUPLICATE_IDENTITY", Stage.AUTHOR, "Duplicate declaration identity", previous, location);
    }

    private static Map<String, Object> shape(Object input, Set<String> required, Set<String> optional, Location source) {
        Map<String, Object> row = object(input, source);
        for (String field : row.keySet()) {
            if (!required.contains(field) && !optional.contains(field)) throw fail("S2_SHAPE", Stage.AUTHOR, "Unknown field", source.child(field));
            if (row.get(field) == Schema2StrictJson.NullValue.INSTANCE) throw fail("S2_SHAPE", Stage.AUTHOR, "Null is not an author value", source.child(field));
        }
        for (String field : required.stream().sorted().toList()) {
            if (!row.containsKey(field)) throw fail("S2_SHAPE", Stage.AUTHOR, "Missing required field", source.child(field));
        }
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object input, Location source) {
        if (!(input instanceof Map<?, ?>)) throw fail("S2_SHAPE", Stage.AUTHOR, "Expected an object", source);
        return (Map<String, Object>) input;
    }
    private static Object required(Map<String, Object> row, String field, Location source) {
        if (!row.containsKey(field) || row.get(field) == Schema2StrictJson.NullValue.INSTANCE) throw fail("S2_SHAPE", Stage.AUTHOR, "Missing or null required field", source.child(field));
        return row.get(field);
    }
    private static List<?> array(Object input, Location source) {
        if (!(input instanceof List<?> rows)) throw fail("S2_SHAPE", Stage.AUTHOR, "Expected an array", source);
        return rows;
    }
    private static String string(Object input, Location source) {
        if (!(input instanceof String value)) throw fail("S2_TYPE", Stage.AUTHOR, "Expected an exact string", source);
        return value;
    }
    private static String identifier(Object input, String regex, Location source) {
        String value = string(input, source);
        if (!value.matches(regex)) throw fail("S2_TYPE", Stage.AUTHOR, "Malformed exact ASCII identifier", source);
        return value;
    }
    private static String moduleKey(Object input, Location source) { return identifier(input, "[a-z][a-z0-9_]{0,63}", source); }
    private static String version(Object input, Location source) { return identifier(input, "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", source); }
    private static int integer(Object input, int minimum, int maximum, Location source) {
        if (!(input instanceof Integer value) || value < minimum || value > maximum) throw fail("S2_TYPE", Stage.AUTHOR, "Expected an integer in the field range", source);
        return value;
    }
    private static Category category(Object input, Location source) {
        String value = string(input, source);
        try { return Category.valueOf(value); }
        catch (IllegalArgumentException error) { throw fail("S2_TYPE", Stage.AUTHOR, "Unknown language category", source); }
    }
    private static ToolCategory toolCategory(Object input, Location source) {
        String value = string(input, source);
        try { return ToolCategory.valueOf(value); }
        catch (IllegalArgumentException error) { throw fail("S2_TYPE", Stage.AUTHOR, "Unknown tool category", source); }
    }
    private static String text(Object input, int maximum, Location source) {
        String value = string(input, source);
        boolean nonBlank = false;
        for (int i = 0; i < value.length();) {
            char unit = value.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) throw fail("S2_TEXT", Stage.AUTHOR, "Isolated Unicode surrogate", source);
            } else if (Character.isLowSurrogate(unit)) throw fail("S2_TEXT", Stage.AUTHOR, "Isolated Unicode surrogate", source);
            int point = value.codePointAt(i);
            if (point <= 0x1f || (point >= 0x7f && point <= 0x9f)) throw fail("S2_TEXT", Stage.AUTHOR, "Control characters are not author text", source);
            if (!Character.isWhitespace(point) && !Character.isSpaceChar(point)) nonBlank = true;
            i += Character.charCount(point);
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        int count = normalized.codePointCount(0, normalized.length());
        if (!nonBlank || count < 1 || count > maximum) throw fail("S2_TEXT", Stage.AUTHOR, "Author text is blank or exceeds its code point range", source);
        return normalized;
    }

    private static void graph(List<AuthorPackage> packages) {
        Map<String, AuthorPackage> selected = new LinkedHashMap<>();
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < packages.size(); i++) {
            AuthorPackage pack = packages.get(i);
            AuthorPackage previous = selected.putIfAbsent(pack.moduleKey, pack);
            if (previous != null) {
                throw fail("S2_DUPLICATE_MODULE", Stage.GRAPH, "A module occurs more than once in the selected list",
                        selection(previous, indices.get(pack.moduleKey)), selection(pack, i));
            }
            indices.put(pack.moduleKey, i);
        }
        long baseCount = packages.stream().filter(pack -> pack.role == Role.BASE).count();
        if (baseCount != 1 || packages.getFirst().role != Role.BASE) {
            throw fail("S2_LOAD_ORDER", Stage.GRAPH, "Exactly one base must be the first selected package", selection(packages.getFirst(), 0));
        }
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (String module : selected.keySet()) edges.put(module, new LinkedHashSet<>());
        Map<OrderConstraint, Location> explicit = new HashMap<>();
        for (AuthorPackage pack : packages) {
            Map<String, String> tuple = new TreeMap<>();
            for (int i = 0; i < pack.dependencies.size(); i++) {
                Dependency dependency = pack.dependencies.get(i);
                Location source = pack.headerLocation.child("dependencies").child(Integer.toString(i));
                AuthorPackage target = selected.get(dependency.moduleKey());
                if (target == null) throw fail("S2_DEPENDENCY_MISSING", Stage.GRAPH, "A declared dependency is absent from selection", source);
                if (!dependency.supportedVersions().contains(target.releaseVersion)) {
                    throw fail("S2_COMBINATION_UNSUPPORTED", Stage.GRAPH, "The selected exact dependency version is unsupported", source.child("supported_versions"));
                }
                tuple.put(target.moduleKey, target.releaseVersion);
                edges.get(target.moduleKey).add(pack.moduleKey);
            }
            if (!pack.combinations.contains(tuple)) {
                throw fail("S2_COMBINATION_UNSUPPORTED", Stage.GRAPH, "The selected joint version combination is unsupported", pack.headerLocation.child("compatible_combinations"));
            }
            for (Located<OrderConstraint> row : pack.constraints) {
                OrderConstraint edge = row.value;
                Location previous = explicit.putIfAbsent(edge, row.location);
                if (previous != null) throw fail("S2_DUPLICATE_CONSTRAINT", Stage.GRAPH, "Duplicate explicit order constraint", previous, row.location);
                edges.get(edge.before()).add(edge.after());
            }
        }
        Map<String, Integer> incoming = new HashMap<>();
        for (String module : edges.keySet()) incoming.put(module, 0);
        edges.values().forEach(next -> next.forEach(module -> incoming.merge(module, 1, Integer::sum)));
        ArrayDeque<String> ready = new ArrayDeque<>();
        incoming.forEach((module, count) -> { if (count == 0) ready.add(module); });
        int visited = 0;
        while (!ready.isEmpty()) {
            String module = ready.remove();
            visited++;
            for (String next : edges.get(module)) if (incoming.merge(next, -1, Integer::sum) == 0) ready.add(next);
        }
        if (visited != packages.size()) {
            List<Location> cycleSources = new ArrayList<>();
            for (AuthorPackage pack : packages) {
                if (incoming.get(pack.moduleKey) > 0) cycleSources.add(pack.headerLocation.child("load_order_constraints"));
            }
            throw new Schema2Exception(new Diagnostic("S2_ORDER_CYCLE", Stage.GRAPH, "Dependencies and order constraints contain a cycle", cycleSources));
        }
        for (Map.Entry<String, Set<String>> edge : edges.entrySet()) {
            for (String next : edge.getValue()) {
                if (indices.get(edge.getKey()) >= indices.get(next)) {
                    throw fail("S2_LOAD_ORDER", Stage.GRAPH, "Selected order violates a dependency or explicit constraint",
                            selection(selected.get(edge.getKey()), indices.get(edge.getKey())), selection(selected.get(next), indices.get(next)));
                }
            }
        }
    }

    private static Location selection(AuthorPackage pack, int index) {
        return new Location(pack.moduleKey, pack.releaseVersion, pack.rawZipSha256, "$selection", "/" + index, null);
    }

    private static final class MutableObject {
        private final Identity identity;
        private final Map<Field, FieldState> fields;
        private final Location source;
        private MutableObject(Identity identity, Map<Field, FieldState> fields, Location source) {
            this.identity = identity;
            this.fields = fields;
            this.source = source;
        }
    }

    private static void addObject(Map<Identity, MutableObject> objects, Map<FieldKey, List<ChainStep>> chains,
                                  Identity identity, Map<Field, FieldState> fields, AuthorPackage pack,
                                  int index, Location source, Set<Identity> newlyDeclared) {
        MutableObject previous = objects.putIfAbsent(identity, new MutableObject(identity, fields, source));
        if (previous != null) throw fail("S2_DUPLICATE_IDENTITY", Stage.APPLY, "A visible original identity is already declared", previous.source, source);
        newlyDeclared.add(identity);
        for (Map.Entry<Field, FieldState> field : fields.entrySet()) {
            Field key = field.getKey();
            FieldState state = field.getValue();
            String origin = key == Field.ENABLED ? "default_enabled"
                    : state.state() == StateKind.ABSENT ? "omitted_optional" : "declared";
            Location location = origin.equals("declared") ? source.child(key.key()) : source;
            List<ChainStep> steps = new ArrayList<>();
            steps.add(new ChainStep("initial", new FieldState(StateKind.UNINITIALIZED, null), state,
                    provider(pack), index, location, origin));
            chains.put(new FieldKey(identity, key), steps);
        }
    }

    private static void directIdentity(AuthorPackage pack, Identity identity, Location source) {
        if (identity instanceof EntryIdentity entry) directModule(pack, entry.rule().moduleKey(), source.child("rule").child("module_key"));
        directModule(pack, identity.moduleKey(), source.child("module_key"));
    }
    private static void directModule(AuthorPackage pack, String module, Location source) {
        if (!module.equals(pack.moduleKey) && pack.dependencies.stream().noneMatch(dependency -> dependency.moduleKey().equals(module))) {
            throw fail("S2_DIRECT_DEPENDENCY", Stage.APPLY, "Every external original owner requires a direct dependency", source);
        }
    }
    private static void checkReference(AuthorPackage pack, RuleIdentity identity, String type,
                                       Map<Identity, MutableObject> objects, Location source) {
        directIdentity(pack, identity, source);
        MutableObject target = objects.get(identity);
        if (target == null) throw fail("S2_REFERENCE_MISSING", Stage.APPLY, "A reference must exist in this package or a preceding package", source);
        if (!Arrays.asList(type.split("[|]")).contains(identity.ruleType())) throw fail("S2_TYPE", Stage.APPLY, "Reference has the wrong domain type", source);
    }

    private static void checkGrantReference(AuthorPackage pack, GrantSpec grant,
                                            Map<Identity, MutableObject> objects, Location source) {
        if (grant instanceof FixedGrant fixed)
            checkReference(pack, fixed.target(), fixed.category() == GrantCategory.LANGUAGE ? "character.language" : "character.tool",
                    objects, source.child("target"));
        else {
            ChoiceGrant choice = (ChoiceGrant) grant;
            checkReference(pack, choice.candidates(), choice.category() == GrantCategory.LANGUAGE ? "catalog.language_set" : "catalog.tool_set",
                    objects, source.child("candidates"));
        }
    }

    private static RuleIdentity growthTarget(GrowthGrantSpec grant) {
        return grant instanceof FixedGrowthGrant fixed ? fixed.target() : ((ChoiceGrowthGrant) grant).candidates();
    }
    private static void checkGrowthGrantReference(AuthorPackage pack, GrowthGrantSpec grant,
            Map<Identity, MutableObject> objects, Location source) {
        checkReference(pack, growthTarget(grant), grant instanceof FixedGrowthGrant
                ? "character.proficiency|character.tool" : "catalog.proficiency_set", objects,
                source.child(grant instanceof FixedGrowthGrant ? "target" : "candidates"));
    }

    private static void validateEquipmentReferences(FinalItem item, Map<Identity, MutableObject> objects, Location source) {
        var d = item.definition(); MutableObject proficiency = objects.get(d.proficiency());
        if (proficiency == null || (item.enabled() && !enabled(proficiency))) throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Equipment proficiency is not available", source);
        String category = d.proficiency().ruleType().equals("character.tool") ? "TOOL" : textField(proficiency.fields, Field.CATEGORY);
        String expected = d.kind() == EquipmentKind.TOOL ? "TOOL" : d.kind() == EquipmentKind.WEAPON ? "WEAPON" : "ARMOR";
        if (!expected.equals(category)) throw fail("S2_TYPE", Stage.FINAL, "Equipment proficiency category differs from kind", source);
        if (d.stealthSkill() != null) {
            MutableObject skill = objects.get(d.stealthSkill());
            if (skill == null || (item.enabled() && !enabled(skill)) || !"SKILL".equals(textField(skill.fields, Field.CATEGORY))
                    || !"ability.dexterity".equals(textField(skill.fields, Field.ABILITY_KEY)))
                throw fail("S2_TYPE", Stage.FINAL, "Stealth disadvantage requires its exact enabled Dexterity skill", source);
        }
    }

    private static void finalValidation(Map<Identity, MutableObject> objects) {
        Map<RuleIdentity, Set<RuleIdentity>> candidates = new HashMap<>();
        Set<RuleIdentity> progressionOwners = new HashSet<>();
        Map<RuleIdentity, Map<Integer, Boolean>> levels = new HashMap<>();
        Map<RuleIdentity, Integer> subclassSelectionLevels = new HashMap<>();
        int enabledRests = 0, enabledChecks = 0, enabledHealth = 0;
        for (MutableObject object : objects.values()) {
            if (object.identity instanceof RuleIdentity rule && newAdvancementType(rule.ruleType())) {
                FinalRule checked = finalRule(rule, object.fields);
                if (checked instanceof FinalHealth && checked.enabled() && ++enabledHealth > 1)
                    throw fail("S2_HEALTH_AMBIGUOUS", Stage.FINAL, "At most one health algorithm may be enabled", object.source);
                if (checked instanceof FinalItem item) validateEquipmentReferences(item, objects, object.source);
                if (checked instanceof FinalCheck && checked.enabled() && ++enabledChecks > 1)
                    throw fail("S2_CHECK_AMBIGUOUS", Stage.FINAL, "At most one check algorithm may be enabled", object.source);
                if (checked instanceof FinalRest && checked.enabled() && ++enabledRests > 1)
                    throw fail("S2_REST_AMBIGUOUS", Stage.FINAL, "At most one rest algorithm may be enabled", object.source);
                if (checked instanceof FinalSubclass subclass && !objects.containsKey(subclass.definition().ownerClass()))
                    throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Subclass class owner is missing", object.source);
                if (checked instanceof FinalSubclass subclass && checked.enabled()) {
                    Integer prior = subclassSelectionLevels.putIfAbsent(subclass.definition().ownerClass(), subclass.definition().selectionLevel());
                    if (prior != null && prior != subclass.definition().selectionLevel())
                        throw fail("S2_SUBCLASS_LEVEL_MISMATCH", Stage.FINAL, "Enabled subclasses of one class require the same selection level", object.source);
                }
                if (checked instanceof FinalFeature feature) {
                    MutableObject owner = objects.get(feature.definition().owner());
                    if (owner == null) throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Feature owner is missing", object.source);
                    if (feature.definition().owner().ruleType().equals("character.subclass")
                            && feature.definition().level() < ((IntegerValue) owner.fields.get(Field.SELECTION_LEVEL).value()).value())
                        throw fail("S2_TYPE", Stage.FINAL, "Subclass feature predates subclass selection", object.source);
                }
            }
            if (object.identity instanceof RuleIdentity rule
                    && Set.of("character.class_progression", "character.resource").contains(rule.ruleType())) {
                RuleIdentity owner = ((IdentityValue) object.fields.get(Field.OWNER_CLASS).value()).value();
                if (!owner.ruleType().equals("character.class") || !objects.containsKey(owner))
                    throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Class growth and resources require a real class source", object.source);
                if (rule.ruleType().equals("character.class_progression") && enabled(object) && !progressionOwners.add(owner))
                    throw fail("S2_PROGRESSION_AMBIGUOUS", Stage.FINAL, "A class has more than one enabled progression", object.source);
                if (rule.ruleType().equals("character.class_progression")
                        && textField(object.fields, Field.ADVANCEMENT_SCOPE).equals(ProgressionScope.CHARACTER_ADVANCEMENT.name())
                        && object.fields.get(Field.MULTICLASS_PREREQUISITE).state() != StateKind.PRESENT)
                    throw fail("S2_TYPE", Stage.FINAL, "Character advancement requires an explicit multiclass prerequisite", object.source);
            }
            if (object.identity instanceof RuleIdentity rule && rule.ruleType().equals("character.subrace")) {
                RuleIdentity parent = ((IdentityValue) object.fields.get(Field.PARENT_RACE).value()).value();
                if (!parent.ruleType().equals("character.race") || !objects.containsKey(parent))
                    throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Subrace requires a real parent race", object.source);
            }
            if (!(object.identity instanceof EntryIdentity identity)) continue;
            MutableObject owner = objects.get(identity.rule());
            Field reference = referenceField(identity);
            if (reference == Field.LEVEL) {
                if (owner == null) throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Growth level owner is missing", object.source);
                int level = ((IntegerValue) object.fields.get(Field.LEVEL).value()).value();
                if (level == 1 && ((BooleanValue) object.fields.get(Field.ASI_AVAILABLE).value()).value()
                        && textField(owner.fields, Field.ADVANCEMENT_SCOPE).equals(ProgressionScope.CHARACTER_ADVANCEMENT.name()))
                    throw fail("S2_GROWTH_INITIAL_ASI_UNSUPPORTED", Stage.FINAL, "Initial ASI is not a supported growth declaration", object.source);
                if (levels.computeIfAbsent(identity.rule(), ignored -> new HashMap<>()).put(level, enabled(object)) != null)
                    throw fail("S2_TYPE", Stage.FINAL, "Duplicate class growth level", object.source);
                if (level > ((IntegerValue) owner.fields.get(Field.SUPPORTED_MAXIMUM_LEVEL).value()).value())
                    throw fail("S2_TYPE", Stage.FINAL, "Growth level exceeds the supported range", object.source);
                continue;
            }
            if (reference == Field.GROWTH_GRANT) {
                GrowthGrantSpec grant = ((GrowthGrantValue) object.fields.get(Field.GROWTH_GRANT).value()).value();
                if (owner == null || !objects.containsKey(growthTarget(grant)))
                    throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Growth grant target is missing", object.source);
                continue;
            }
            if (reference == Field.GRANT) {
                GrantSpec grant = ((GrantValue) object.fields.get(Field.GRANT).value()).value();
                RuleIdentity target = grant instanceof FixedGrant fixed ? fixed.target() : ((ChoiceGrant) grant).candidates();
                if (owner == null || !identity.collectionKey().equals("proficiency_grants") || !objects.containsKey(target))
                    throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Final proficiency source and grant references must remain valid", object.source);
                continue;
            }
            RuleIdentity candidate = ((IdentityValue) object.fields.get(reference).value()).value();
            MutableObject target = objects.get(candidate);
            if (owner == null || !identity.collectionKey().equals("members") || target == null
                    || !Arrays.asList(referenceType(reference).split("[|]")).contains(candidate.ruleType())) {
                throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Final entry ownership and typed catalog references must remain valid", object.source);
            }
            if (reference == Field.PROFICIENCY) {
                String expectedCategory = textField(owner.fields, Field.CATEGORY);
                String actualCategory = candidate.ruleType().equals("character.tool") ? "TOOL" : textField(target.fields, Field.CATEGORY);
                if (!expectedCategory.equals(actualCategory)) throw fail("S2_TYPE", Stage.FINAL, "Proficiency set member category differs", object.source);
            }
            if (enabled(object) && enabled(target)) candidates.computeIfAbsent(identity.rule(), ignored -> new HashSet<>()).add(candidate);
        }
        for (MutableObject object : objects.values()) {
            if (object.identity instanceof RuleIdentity identity
                    && Set.of("catalog.language_set", "catalog.tool_set", "catalog.proficiency_set").contains(identity.ruleType())) {
                int minimum = ((IntegerValue) object.fields.get(Field.MINIMUM_ENABLED_MEMBERS).value()).value();
                if (candidates.getOrDefault(identity, Set.of()).size() < minimum) {
                    throw fail("S2_CANDIDATES_INSUFFICIENT", Stage.FINAL, "Distinct enabled catalog candidates are below the declared minimum", object.source);
                }
            }
            if (object.identity instanceof RuleIdentity identity && identity.ruleType().equals("character.class_progression")
                    && textField(object.fields, Field.ADVANCEMENT_SCOPE).equals(ProgressionScope.CHARACTER_ADVANCEMENT.name())) {
                int maximum = ((IntegerValue) object.fields.get(Field.SUPPORTED_MAXIMUM_LEVEL).value()).value();
                Map<Integer, Boolean> declared = levels.getOrDefault(identity, Map.of());
                if (declared.size() != maximum || java.util.stream.IntStream.rangeClosed(1, maximum)
                        .anyMatch(level -> !Boolean.TRUE.equals(declared.get(level))))
                    throw fail("S2_GROWTH_LEVELS_INCOMPLETE", Stage.FINAL, "Character growth requires every enabled level row", object.source);
            }
            if (object.identity instanceof EntryIdentity identity && referenceField(identity) == Field.GROWTH_GRANT
                    && enabled(object) && enabled(objects.get(identity.rule()))) {
                GrowthGrantSpec grant = ((GrowthGrantValue) object.fields.get(Field.GROWTH_GRANT).value()).value();
                if (grant instanceof ChoiceGrowthGrant choice && (!enabled(objects.get(choice.candidates()))
                        || candidates.getOrDefault(choice.candidates(), Set.of()).size() < choice.count()))
                    throw fail("S2_CANDIDATES_INSUFFICIENT", Stage.FINAL, "Growth choice requires enough enabled candidates", object.source);
            }
            if (object.identity instanceof EntryIdentity identity && referenceField(identity) == Field.GRANT
                    && enabled(object) && enabled(objects.get(identity.rule()))) {
                GrantSpec grant = ((GrantValue) object.fields.get(Field.GRANT).value()).value();
                if (grant instanceof ChoiceGrant choice && (!enabled(objects.get(choice.candidates()))
                        || candidates.getOrDefault(choice.candidates(), Set.of()).size() < choice.count()))
                    throw fail("S2_CANDIDATES_INSUFFICIENT", Stage.FINAL, "Enabled proficiency choice requires an enabled set with enough distinct enabled targets", object.source);
            }
        }
    }

    private static List<Field> domainFields(Identity identity) {
        if (identity instanceof EntryIdentity entry) return entry.collectionKey().equals("levels")
                ? List.of(Field.LEVEL, Field.ASI_AVAILABLE, Field.AUTHOR_NOTE) : List.of(referenceField(entry), Field.AUTHOR_NOTE);
        String type = ((RuleIdentity) identity).ruleType();
        if (type.equals("character.language") || type.equals("character.tool")) {
            return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.CATEGORY, Field.SOURCE_PAGE, Field.AUTHOR_NOTE);
        }
        if (type.equals("character.class_progression")) return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION,
                Field.SOURCE_PAGE, Field.OWNER_CLASS, Field.HIT_DIE_SIDES, Field.PROFICIENCY_BONUS_PROFILE,
                Field.SUPPORTED_MAXIMUM_LEVEL, Field.ADVANCEMENT_SCOPE, Field.MULTICLASS_PREREQUISITE, Field.AUTHOR_NOTE);
        if (type.equals("character.resource")) return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION,
                Field.SOURCE_PAGE, Field.OWNER_CLASS, Field.MAXIMUM_PROFILE, Field.RECOVERY_PROFILE,
                Field.EXECUTION_MODE, Field.AUTHOR_NOTE);
        if (type.equals("character.subclass")) return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE,
                Field.OWNER_CLASS, Field.SELECTION_LEVEL, Field.AUTHOR_NOTE);
        if (type.equals("character.feature")) return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE,
                Field.OWNER, Field.LEVEL, Field.FEATURE_KIND, Field.EXECUTION_MODE, Field.EXECUTION_ALGORITHM, Field.AUTHOR_NOTE);
        if (type.equals("character.feat")) return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE,
                Field.PREREQUISITE, Field.EXECUTION_MODE, Field.EXECUTION_ALGORITHM, Field.AUTHOR_NOTE);
        if (type.equals("character.proficiency")) return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE,
                Field.CATEGORY, Field.ABILITY_KEY, Field.AUTHOR_NOTE);
        if (type.equals("catalog.proficiency_set")) return List.of(Field.DISPLAY_NAME, Field.CATEGORY, Field.MINIMUM_ENABLED_MEMBERS, Field.AUTHOR_NOTE);
        if (type.equals("equipment.item")) return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE, Field.KIND, Field.ARMOR_CLASS, Field.STRENGTH_MINIMUM, Field.STEALTH_DISADVANTAGE, Field.HANDS, Field.PROFICIENCY, Field.STEALTH_SKILL, Field.ALGORITHM, Field.AUTHOR_NOTE);
        if (type.equals("adventure.vital_effect")) return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE, Field.KIND, Field.DICE_COUNT, Field.DICE_SIDES, Field.MODIFIER, Field.DAMAGE_TYPE, Field.ALGORITHM, Field.AUTHOR_NOTE);
        if (type.equals("adventure.rest") || type.equals("adventure.check") || type.equals("adventure.health")) return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE, Field.ALGORITHM, Field.AUTHOR_NOTE);
        if (sourceType(type)) return type.equals("character.subrace")
                ? List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE, Field.PARENT_RACE, Field.AUTHOR_NOTE)
                : List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE, Field.AUTHOR_NOTE);
        return List.of(Field.DISPLAY_NAME, Field.MINIMUM_ENABLED_MEMBERS, Field.AUTHOR_NOTE);
    }
    private static Map<Field, FieldState> definitionFields(Definition value) {
        if (newAdvancementType(value.identity().ruleType())) {
            Map<Field, FieldState> fields = new LinkedHashMap<>();
            Map<String, Object> encoded = definitionJson(value);
            for (Field field : domainFields(value.identity())) fields.put(field, !encoded.containsKey(field.key()) ? absent()
                    : present(((SetField) change(json("op", "set_field", "target", identityJson(value.identity()),
                            "field", field.key(), "value", encoded.get(field.key())), new Location(null, null, null, "$definition", "", null))).value()));
            fields.put(Field.ENABLED, present(new BooleanValue(true)));
            return fields;
        }
        Map<Field, FieldState> result = new LinkedHashMap<>();
        result.put(Field.DISPLAY_NAME, present(new TextValue(value.displayName())));
        if (value instanceof LanguageDefinition language) {
            result.put(Field.DESCRIPTION, present(new TextValue(language.description())));
            result.put(Field.CATEGORY, present(new TextValue(language.category().name())));
            result.put(Field.SOURCE_PAGE, present(new IntegerValue(language.sourcePage())));
        } else if (value instanceof ToolDefinition tool) {
            result.put(Field.DESCRIPTION, present(new TextValue(tool.description())));
            result.put(Field.CATEGORY, present(new TextValue(tool.category().name())));
            result.put(Field.SOURCE_PAGE, present(new IntegerValue(tool.sourcePage())));
        } else if (value instanceof CharacterSourceDefinition source) {
            result.put(Field.DESCRIPTION, present(new TextValue(source.description())));
            result.put(Field.SOURCE_PAGE, present(new IntegerValue(source.sourcePage())));
            if (source.parentRace() != null) result.put(Field.PARENT_RACE, present(new IdentityValue(source.parentRace())));
        } else if (value instanceof ClassProgressionDefinition progression) {
            result.put(Field.DESCRIPTION, present(new TextValue(progression.description())));
            result.put(Field.SOURCE_PAGE, present(new IntegerValue(progression.sourcePage())));
            result.put(Field.OWNER_CLASS, present(new IdentityValue(progression.ownerClass())));
            result.put(Field.HIT_DIE_SIDES, present(new IntegerValue(progression.hitDieSides())));
            result.put(Field.PROFICIENCY_BONUS_PROFILE, present(new TextValue(progression.proficiencyBonusProfile())));
            result.put(Field.SUPPORTED_MAXIMUM_LEVEL, present(new IntegerValue(progression.supportedMaximumLevel())));
            result.put(Field.ADVANCEMENT_SCOPE, present(new TextValue(progression.advancementScope().name())));
            result.put(Field.MULTICLASS_PREREQUISITE, progression.multiclassPrerequisite() == null ? absent()
                    : present(new PrerequisiteValue(progression.multiclassPrerequisite())));
        } else if (value instanceof ResourceDefinition resource) {
            result.put(Field.DESCRIPTION, present(new TextValue(resource.description())));
            result.put(Field.SOURCE_PAGE, present(new IntegerValue(resource.sourcePage())));
            result.put(Field.OWNER_CLASS, present(new IdentityValue(resource.ownerClass())));
            result.put(Field.MAXIMUM_PROFILE, present(new TextValue(resource.maximumProfile())));
            result.put(Field.RECOVERY_PROFILE, present(new TextValue(resource.recoveryProfile())));
            result.put(Field.EXECUTION_MODE, present(new TextValue(resource.executionMode().name())));
        } else {
            int minimum = value instanceof LanguageSetDefinition set ? set.minimumEnabledMembers() : ((ToolSetDefinition) value).minimumEnabledMembers();
            result.put(Field.MINIMUM_ENABLED_MEMBERS, present(new IntegerValue(minimum)));
        }
        result.put(Field.AUTHOR_NOTE, value.authorNote() == null ? absent() : present(new TextValue(value.authorNote())));
        result.put(Field.ENABLED, present(new BooleanValue(true)));
        return result;
    }
    private static Map<Field, FieldState> entryFields(Entry value) {
        Map<Field, FieldState> result = new LinkedHashMap<>();
        if (value instanceof ClassLevelEntry level) {
            result.put(Field.LEVEL, present(new IntegerValue(level.level())));
            result.put(Field.ASI_AVAILABLE, present(new BooleanValue(level.asiAvailable())));
        } else result.put(referenceField(value.identity()), present(value instanceof ProficiencyGrantEntry grant
                ? new GrantValue(grant.grant()) : value instanceof GrowthProficiencyGrantEntry growth
                    ? new GrowthGrantValue(growth.grant()) : new IdentityValue(value.target())));
        result.put(Field.AUTHOR_NOTE, value.authorNote() == null ? absent() : present(new TextValue(value.authorNote())));
        result.put(Field.ENABLED, present(new BooleanValue(true)));
        return result;
    }
    private static FieldState present(FieldValue value) { return new FieldState(StateKind.PRESENT, value); }
    private static FieldState absent() { return new FieldState(StateKind.ABSENT, null); }
    private static Provider provider(AuthorPackage pack) { return new Provider(pack.moduleKey, pack.releaseVersion); }
    private static boolean enabled(MutableObject value) { return ((BooleanValue) value.fields.get(Field.ENABLED).value()).value(); }
    private static String textField(Map<Field, FieldState> fields, Field field) { return ((TextValue) fields.get(field).value()).value(); }
    private static String noteField(Map<Field, FieldState> fields) {
        return fields.get(Field.AUTHOR_NOTE).state() == StateKind.ABSENT ? null : textField(fields, Field.AUTHOR_NOTE);
    }
    private static FinalRule finalRule(RuleIdentity identity, Map<Field, FieldState> fields) {
        boolean enabled = ((BooleanValue) fields.get(Field.ENABLED).value()).value();
        if (newAdvancementType(identity.ruleType())) {
            Map<String, Object> encoded = json("identity", identityJson(identity));
            for (Field field : domainFields(identity)) if (fields.get(field).state() == StateKind.PRESENT)
                encoded.put(field.key(), valueJson(fields.get(field).value()));
            Definition definition = definition(encoded, new Location(null, null, null, "$final", "", null));
            if (definition instanceof SubclassDefinition value) return new FinalSubclass(value, enabled);
            if (definition instanceof FeatureDefinition value) return new FinalFeature(value, enabled);
            if (definition instanceof FeatDefinition value) return new FinalFeat(value, enabled);
            if (definition instanceof ProficiencyDefinition value) return new FinalProficiency(value, enabled);
            if (definition instanceof RestDefinition value) return new FinalRest(value, enabled);
            if (definition instanceof CheckDefinition value) return new FinalCheck(value, enabled);
            if (definition instanceof ItemDefinition value) return new FinalItem(value, enabled);
            if (definition instanceof HealthDefinition value) return new FinalHealth(value, enabled);
            if (definition instanceof VitalEffectDefinition value) return new FinalVitalEffect(value, enabled);
            return new FinalProficiencySet((ProficiencySetDefinition) definition, enabled);
        }
        if (identity.ruleType().equals("character.language")) {
            return new FinalLanguage(new LanguageDefinition(identity, textField(fields, Field.DISPLAY_NAME),
                    textField(fields, Field.DESCRIPTION), Category.valueOf(textField(fields, Field.CATEGORY)),
                    ((IntegerValue) fields.get(Field.SOURCE_PAGE).value()).value(), noteField(fields)), enabled);
        }
        if (identity.ruleType().equals("character.tool")) {
            return new FinalTool(new ToolDefinition(identity, textField(fields, Field.DISPLAY_NAME),
                    textField(fields, Field.DESCRIPTION), ToolCategory.valueOf(textField(fields, Field.CATEGORY)),
                    ((IntegerValue) fields.get(Field.SOURCE_PAGE).value()).value(), noteField(fields)), enabled);
        }
        if (sourceType(identity.ruleType())) return new FinalCharacterSource(new CharacterSourceDefinition(identity,
                textField(fields, Field.DISPLAY_NAME), textField(fields, Field.DESCRIPTION),
                ((IntegerValue) fields.get(Field.SOURCE_PAGE).value()).value(),
                identity.ruleType().equals("character.subrace") ? ((IdentityValue) fields.get(Field.PARENT_RACE).value()).value() : null,
                noteField(fields)), enabled);
        if (identity.ruleType().equals("character.class_progression")) return new FinalClassProgression(
                new ClassProgressionDefinition(identity, textField(fields, Field.DISPLAY_NAME), textField(fields, Field.DESCRIPTION),
                        ((IntegerValue) fields.get(Field.SOURCE_PAGE).value()).value(),
                        ((IdentityValue) fields.get(Field.OWNER_CLASS).value()).value(),
                        ((IntegerValue) fields.get(Field.HIT_DIE_SIDES).value()).value(),
                        textField(fields, Field.PROFICIENCY_BONUS_PROFILE),
                        ((IntegerValue) fields.get(Field.SUPPORTED_MAXIMUM_LEVEL).value()).value(),
                        ProgressionScope.valueOf(textField(fields, Field.ADVANCEMENT_SCOPE)),
                        fields.get(Field.MULTICLASS_PREREQUISITE).state() == StateKind.ABSENT ? null
                                : ((PrerequisiteValue) fields.get(Field.MULTICLASS_PREREQUISITE).value()).value(), noteField(fields)), enabled);
        if (identity.ruleType().equals("character.resource")) return new FinalResource(
                new ResourceDefinition(identity, textField(fields, Field.DISPLAY_NAME), textField(fields, Field.DESCRIPTION),
                        ((IntegerValue) fields.get(Field.SOURCE_PAGE).value()).value(),
                        ((IdentityValue) fields.get(Field.OWNER_CLASS).value()).value(), textField(fields, Field.MAXIMUM_PROFILE),
                        textField(fields, Field.RECOVERY_PROFILE), ResourceExecution.valueOf(textField(fields, Field.EXECUTION_MODE)),
                        noteField(fields)), enabled);
        int minimum = ((IntegerValue) fields.get(Field.MINIMUM_ENABLED_MEMBERS).value()).value();
        return identity.ruleType().equals("catalog.language_set")
                ? new FinalLanguageSet(new LanguageSetDefinition(identity, textField(fields, Field.DISPLAY_NAME), minimum, noteField(fields)), enabled)
                : new FinalToolSet(new ToolSetDefinition(identity, textField(fields, Field.DISPLAY_NAME), minimum, noteField(fields)), enabled);
    }
    private static Entry finalEntry(EntryIdentity identity, Map<Field, FieldState> fields) {
        Field reference = referenceField(identity);
        if (reference == Field.LEVEL) return new ClassLevelEntry(identity, ((IntegerValue) fields.get(Field.LEVEL).value()).value(),
                ((BooleanValue) fields.get(Field.ASI_AVAILABLE).value()).value(), noteField(fields));
        if (reference == Field.GROWTH_GRANT) return new GrowthProficiencyGrantEntry(identity, ((GrowthGrantValue) fields.get(Field.GROWTH_GRANT).value()).value(), noteField(fields));
        if (reference == Field.GRANT) return new ProficiencyGrantEntry(identity, ((GrantValue) fields.get(Field.GRANT).value()).value(), noteField(fields));
        RuleIdentity target = ((IdentityValue) fields.get(reference).value()).value();
        if (reference == Field.PROFICIENCY) return new ProficiencyMemberEntry(identity, target, noteField(fields));
        return reference == Field.LANGUAGE ? new LanguageEntry(identity, target, noteField(fields)) : new ToolEntry(identity, target, noteField(fields));
    }

    private static final Comparator<RuleIdentity> RULE_ORDER = Comparator.comparing(RuleIdentity::moduleKey)
            .thenComparing(RuleIdentity::ruleType).thenComparing(RuleIdentity::ruleKey);
    private static final Comparator<EntryIdentity> ENTRY_ORDER = Comparator.comparing(EntryIdentity::rule, RULE_ORDER)
            .thenComparing(EntryIdentity::collectionKey).thenComparing(EntryIdentity::moduleKey).thenComparing(EntryIdentity::entryKey);
    private static final Comparator<Identity> IDENTITY_ORDER = (a, b) -> {
        if (a instanceof EntryIdentity ae && b instanceof EntryIdentity be) return ENTRY_ORDER.compare(ae, be);
        if (a instanceof RuleIdentity ar && b instanceof RuleIdentity br) return RULE_ORDER.compare(ar, br);
        return a instanceof EntryIdentity ? -1 : 1; // ASCII kind order: entry, rule.
    };
    private static int compareJson(Object a, Object b) {
        return Arrays.compareUnsigned(Schema2ValidationCodec.canonical(a), Schema2ValidationCodec.canonical(b));
    }
    static String roleName(Role role) {
        return switch (role) { case BASE -> "base"; case EXTENSION -> "extension"; case COMPATIBILITY -> "compatibility"; };
    }
    static Map<String, Object> json(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
    private static Map<String, Object> identityJson(Identity identity) {
        if (identity instanceof RuleIdentity rule) return json("kind", "rule", "module_key", rule.moduleKey(),
                "rule_type", rule.ruleType(), "rule_key", rule.ruleKey());
        EntryIdentity entry = (EntryIdentity) identity;
        return json("kind", "entry", "rule", identityJson(entry.rule()), "collection_key", entry.collectionKey(),
                "module_key", entry.moduleKey(), "entry_key", entry.entryKey());
    }
    private static Map<String, Object> definitionJson(Definition value) {
        Map<String, Object> result = json("identity", identityJson(value.identity()), "display_name", value.displayName());
        if (value instanceof LanguageDefinition language) {
            result.put("description", language.description());
            result.put("category", language.category().name());
            result.put("source_page", language.sourcePage());
        } else if (value instanceof ToolDefinition tool) {
            result.put("description", tool.description());
            result.put("category", tool.category().name());
            result.put("source_page", tool.sourcePage());
        } else if (value instanceof CharacterSourceDefinition source) {
            result.put("description", source.description());
            result.put("source_page", source.sourcePage());
            if (source.parentRace() != null) result.put("parent_race", identityJson(source.parentRace()));
        } else if (value instanceof ClassProgressionDefinition progression) {
            result.put("description", progression.description());
            result.put("source_page", progression.sourcePage());
            result.put("owner_class", identityJson(progression.ownerClass()));
            result.put("hit_die_sides", progression.hitDieSides());
            result.put("proficiency_bonus_profile", progression.proficiencyBonusProfile());
            result.put("supported_maximum_level", progression.supportedMaximumLevel());
            result.put("advancement_scope", progression.advancementScope().name());
            if (progression.multiclassPrerequisite() != null) result.put("multiclass_prerequisite", prerequisiteJson(progression.multiclassPrerequisite()));
        } else if (value instanceof ResourceDefinition resource) {
            result.put("description", resource.description());
            result.put("source_page", resource.sourcePage());
            result.put("owner_class", identityJson(resource.ownerClass()));
            result.put("maximum_profile", resource.maximumProfile());
            result.put("recovery_profile", resource.recoveryProfile());
            result.put("execution_mode", resource.executionMode().name());
        } else if (value instanceof SubclassDefinition subclass) {
            result.put("description", subclass.description()); result.put("source_page", subclass.sourcePage());
            result.put("owner_class", identityJson(subclass.ownerClass())); result.put("selection_level", subclass.selectionLevel());
        } else if (value instanceof FeatureDefinition feature) {
            result.put("description", feature.description()); result.put("source_page", feature.sourcePage());
            result.put("owner", identityJson(feature.owner())); result.put("level", feature.level());
            result.put("feature_kind", feature.kind().name()); result.put("execution_mode", feature.executionMode().name());
            result.put("execution_algorithm", feature.executionAlgorithm());
        } else if (value instanceof FeatDefinition feat) {
            result.put("description", feat.description()); result.put("source_page", feat.sourcePage());
            result.put("prerequisite", prerequisiteJson(feat.prerequisite())); result.put("execution_mode", feat.executionMode().name());
            result.put("execution_algorithm", feat.executionAlgorithm());
        } else if (value instanceof ProficiencyDefinition proficiency) {
            result.put("description", proficiency.description()); result.put("source_page", proficiency.sourcePage());
            result.put("category", proficiency.category().name());
            if (proficiency.abilityKey() != null) result.put("ability_key", proficiency.abilityKey());
        } else if (value instanceof ProficiencySetDefinition set) {
            result.put("category", set.category().name()); result.put("minimum_enabled_members", set.minimumEnabledMembers());
        } else if (value instanceof ItemDefinition item) {
            result.put("description", item.description()); result.put("source_page", item.sourcePage()); result.put("algorithm", item.algorithm());
            result.put("kind", item.kind().name()); result.put("armor_class", item.armorClass()); result.put("strength_minimum", item.strengthMinimum());
            result.put("stealth_disadvantage", item.stealthDisadvantage()); result.put("hands", item.hands()); result.put("proficiency", identityJson(item.proficiency()));
            if (item.stealthSkill() != null) result.put("stealth_skill", identityJson(item.stealthSkill()));
        } else if (value instanceof HealthDefinition health) {
            result.put("description", health.description()); result.put("source_page", health.sourcePage()); result.put("algorithm", health.algorithm());
        } else if (value instanceof VitalEffectDefinition effect) {
            result.put("description", effect.description()); result.put("source_page", effect.sourcePage()); result.put("algorithm", effect.algorithm());
            result.put("kind", effect.kind().name()); result.put("dice_count", effect.diceCount()); result.put("dice_sides", effect.diceSides()); result.put("modifier", effect.modifier());
            if (effect.damageType() != null) result.put("damage_type", effect.damageType().name());
        } else if (value instanceof CheckDefinition check) {
            result.put("description", check.description()); result.put("source_page", check.sourcePage()); result.put("algorithm", check.algorithm());
        } else if (value instanceof RestDefinition rest) {
            result.put("description", rest.description()); result.put("source_page", rest.sourcePage()); result.put("algorithm", rest.algorithm());
        } else result.put("minimum_enabled_members", value instanceof LanguageSetDefinition set
                ? set.minimumEnabledMembers() : ((ToolSetDefinition) value).minimumEnabledMembers());
        if (value.authorNote() != null) result.put("author_note", value.authorNote());
        return result;
    }
    private static Map<String, Object> entryJson(Entry value) {
        Map<String, Object> result = json("identity", identityJson(value.identity()));
        if (value instanceof ProficiencyGrantEntry grant) result.put("grant", grantJson(grant.grant()));
        else if (value instanceof ClassLevelEntry level) { result.put("level", level.level()); result.put("asi_available", level.asiAvailable()); }
        else if (value instanceof ProficiencyMemberEntry member) result.put("proficiency", identityJson(member.proficiency()));
        else if (value instanceof GrowthProficiencyGrantEntry grant) result.put("growth_grant", growthGrantJson(grant.grant()));
        else result.put(value instanceof LanguageEntry ? "language" : "tool", identityJson(value.target()));
        if (value.authorNote() != null) result.put("author_note", value.authorNote());
        return result;
    }
    private static Map<String, Object> grantJson(GrantSpec value) {
        if (value instanceof FixedGrant fixed) return json("category", fixed.category().name(), "kind", "FIXED", "target", identityJson(fixed.target()));
        ChoiceGrant choice = (ChoiceGrant) value;
        return json("category", choice.category().name(), "kind", "CHOICE", "candidates", identityJson(choice.candidates()), "count", choice.count());
    }
    private static Map<String, Object> prerequisiteJson(AbilityPrerequisite value) {
        return json("all_of", value.allOf().stream().map(group -> json("any_of", group.anyOf().stream()
                .map(term -> json("ability", term.abilityKey(), "minimum", term.minimum())).toList())).toList());
    }
    private static Map<String, Object> growthGrantJson(GrowthGrantSpec value) {
        if (value instanceof FixedGrowthGrant fixed) return json("kind", "FIXED", "target", identityJson(fixed.target()));
        ChoiceGrowthGrant choice = (ChoiceGrowthGrant) value;
        return json("kind", "CHOICE", "candidates", identityJson(choice.candidates()), "count", choice.count());
    }
    private static boolean newAdvancementType(String type) {
        return Set.of("character.subclass", "character.feature", "character.feat", "character.proficiency", "catalog.proficiency_set", "adventure.rest", "adventure.check", "equipment.item", "adventure.health", "adventure.vital_effect").contains(type);
    }
    private static Map<String, Object> changeJson(Change value) {
        if (value instanceof SetField set) return json("op", "set_field", "target", identityJson(set.target()),
                "field", set.field().key(), "value", valueJson(set.value()));
        if (value instanceof ClearField clear) return json("op", "clear_field", "target", identityJson(clear.target()), "field", clear.field().key());
        return json("op", "set_enabled", "target", identityJson(value.target()), "value", ((SetEnabled) value).value());
    }
    static Map<String, Object> finalRuleJson(FinalRule value) {
        Definition definition = value.definition();
        Map<String, Object> result = definitionJson(definition);
        result.put("enabled", value.enabled());
        return result;
    }
    static Map<String, Object> finalEntryJson(FinalEntry value) {
        Map<String, Object> result = entryJson(value.entry());
        result.put("enabled", value.enabled());
        return result;
    }
    private static Object valueJson(FieldValue value) {
        if (value instanceof TextValue text) return text.value();
        if (value instanceof IntegerValue integer) return integer.value();
        if (value instanceof IdentityValue identity) return identityJson(identity.value());
        if (value instanceof GrantValue grant) return grantJson(grant.value());
        if (value instanceof PrerequisiteValue predicate) return prerequisiteJson(predicate.value());
        if (value instanceof GrowthGrantValue grant) return growthGrantJson(grant.value());
        return ((BooleanValue) value).value();
    }
    private static Map<String, Object> stateJson(FieldState state) {
        return switch (state.state()) {
            case UNINITIALIZED -> json("state", "uninitialized");
            case ABSENT -> json("state", "absent");
            case PRESENT -> json("state", "present", "value", valueJson(state.value()));
        };
    }
    static Map<String, Object> chainJson(FieldChain value) {
        return json("target", identityJson(value.target()), "field", value.field().key(), "steps", value.steps().stream()
                .map(step -> json("op", step.op(), "before", stateJson(step.before()), "after", stateJson(step.after()),
                        "provider", json("module_key", step.provider().moduleKey(), "release_version", step.provider().releaseVersion()),
                        "load_index", step.loadIndex(), "author_location", json("raw_zip_sha256", step.authorLocation().rawZipSha256(),
                                "path", step.authorLocation().path(), "pointer", step.authorLocation().pointer()), "origin", step.origin())).toList());
    }
    private static Schema2Exception fail(String code, Stage stage, String message, Location... locations) {
        return new Schema2Exception(new Diagnostic(code, stage, message, List.of(locations)));
    }
}
