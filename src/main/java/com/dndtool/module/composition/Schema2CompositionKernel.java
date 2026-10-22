package com.dndtool.module.composition;

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
        PARENT_RACE("parent_race"), GRANT("grant"), ENABLED("enabled");
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
    public sealed interface Definition permits LanguageDefinition, LanguageSetDefinition, ToolDefinition, ToolSetDefinition, CharacterSourceDefinition {
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
    public enum GrantCategory { LANGUAGE, TOOL }
    public sealed interface GrantSpec permits FixedGrant, ChoiceGrant { GrantCategory category(); }
    public record FixedGrant(GrantCategory category, RuleIdentity target) implements GrantSpec { }
    public record ChoiceGrant(GrantCategory category, RuleIdentity candidates, int count) implements GrantSpec { }
    public sealed interface Entry permits LanguageEntry, ToolEntry, ProficiencyGrantEntry {
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
    public sealed interface FieldValue permits TextValue, IntegerValue, IdentityValue, BooleanValue, GrantValue { }
    public record TextValue(String value) implements FieldValue { }
    public record IntegerValue(int value) implements FieldValue { }
    public record IdentityValue(RuleIdentity value) implements FieldValue { }
    public record BooleanValue(boolean value) implements FieldValue { }
    public record GrantValue(GrantSpec value) implements FieldValue { }
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
    public sealed interface FinalRule permits FinalLanguage, FinalLanguageSet, FinalTool, FinalToolSet, FinalCharacterSource {
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
                        || (after.state() == StateKind.ABSENT && (chain.field() != Field.AUTHOR_NOTE || after.value() != null))
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
                    boolean proficiencySource = chain.target() instanceof RuleIdentity rule && sourceType(rule.ruleType())
                            || chain.target() instanceof EntryIdentity entry && sourceType(entry.rule().ruleType());
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
                    if ((chain.target() instanceof RuleIdentity rule && sourceType(rule.ruleType())
                            || chain.target() instanceof EntryIdentity entry && sourceType(entry.rule().ruleType()))
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
                            ? fixed.target() : ((ChoiceGrant) value.value()).candidates()) : null;
                if (reference != null) {
                    Integer referenceDeclaration = declarations.get(reference);
                    if (referenceDeclaration == null || referenceDeclaration > step.loadIndex())
                        throw fail("S2_STORED_INVALID", Stage.FINAL, "Stored reference precedes its declaration", source);
                }
            }
        }
        if (seen.size() != objects.values().stream().mapToInt(o -> o.fields.size()).sum())
            throw fail("S2_STORED_INVALID", Stage.FINAL, "Stored field chains are incomplete", source);
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
            }
            for (Located<Entry> row : pack.entries) {
                Entry value = row.value;
                Field reference = referenceField(value.identity());
                checkReference(pack, value.identity().rule(), value.identity().rule().ruleType(), objects, row.location.child("identity").child("rule"));
                if (value instanceof ProficiencyGrantEntry grant)
                    checkGrantReference(pack, grant.grant(), objects, row.location.child("grant"));
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
        boolean catalogItem = identity.ruleType().equals("character.language") || identity.ruleType().equals("character.tool");
        boolean characterSource = sourceType(identity.ruleType());
        Set<String> required = catalogItem
                ? Set.of("identity", "display_name", "description", "category", "source_page")
                : characterSource ? (identity.ruleType().equals("character.subrace")
                    ? Set.of("identity", "display_name", "description", "source_page", "parent_race")
                    : Set.of("identity", "display_name", "description", "source_page"))
                : Set.of("identity", "display_name", "minimum_enabled_members");
        Map<String, Object> row = shape(input, required, Set.of("author_note"), source);
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
            default -> new CharacterSourceDefinition(identity, displayName,
                    text(row.get("description"), 1000, source.child("description")),
                    integer(row.get("source_page"), 3, 74, source.child("source_page")),
                    identity.ruleType().equals("character.subrace")
                            ? typedReference(row.get("parent_race"), "character.race", source.child("parent_race")) : null, note);
        };
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

    private static Entry entry(Object input, Location source) {
        Map<String, Object> initial = object(input, source);
        Identity identity = identity(required(initial, "identity", source), source.child("identity"));
        if (!(identity instanceof EntryIdentity entry)) throw fail("S2_TYPE", Stage.AUTHOR, "Entry declaration requires an entry identity", source.child("identity"));
        Field reference = referenceField(entry);
        Map<String, Object> row = shape(input, Set.of("identity", reference.key()), Set.of("author_note"), source);
        String note = row.containsKey("author_note") ? text(row.get("author_note"), 1000, source.child("author_note")) : null;
        if (reference == Field.GRANT) return new ProficiencyGrantEntry(entry, grant(row.get("grant"), source.child("grant")), note);
        RuleIdentity target = typedReference(row.get(reference.key()), referenceType(reference), source.child(reference.key()));
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
            if (field != Field.AUTHOR_NOTE) throw fail("S2_FIELD_NOT_CLEARABLE", Stage.AUTHOR, "Only author_note can be cleared", source.child("field"));
            return new ClearField(target, field);
        }
        Object value = row.get("value");
        FieldValue typed = switch (field) {
            case DISPLAY_NAME -> new TextValue(text(value, 120, source.child("value")));
            case DESCRIPTION, AUTHOR_NOTE -> new TextValue(text(value, 1000, source.child("value")));
            case CATEGORY -> new TextValue(((RuleIdentity) target).ruleType().equals("character.tool")
                    ? toolCategory(value, source.child("value")).name() : category(value, source.child("value")).name());
            case SOURCE_PAGE -> new IntegerValue(integer(value, 3, 74, source.child("value")));
            case MINIMUM_ENABLED_MEMBERS -> new IntegerValue(integer(value, 1, 128, source.child("value")));
            case LANGUAGE, TOOL, PARENT_RACE -> new IdentityValue(typedReference(value, referenceType(field), source.child("value")));
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
        boolean catalog = rule.ruleType().equals("catalog.language_set") || rule.ruleType().equals("catalog.tool_set");
        if (!catalog && !sourceType(rule.ruleType()))
            throw fail("S2_TYPE", Stage.AUTHOR, "Entry owner requires a catalog set or character source", source.child("rule").child("rule_type"));
        String collection = string(row.get("collection_key"), source.child("collection_key"));
        if (!collection.equals(catalog ? "members" : "proficiency_grants"))
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
        if (!Set.of("character.language", "catalog.language_set", "character.tool", "catalog.tool_set").contains(type) && !sourceType(type))
            throw fail("S2_TYPE", Stage.AUTHOR, "Unsupported finite rule type", source.child("rule_type"));
        String key = identifier(row.get("rule_key"), "[a-z][a-z0-9_.]{0,127}", source.child("rule_key"));
        return new RuleIdentity(module, type, key);
    }

    private static RuleIdentity typedReference(Object input, String type, Location source) {
        RuleIdentity identity = ruleIdentity(input, source);
        if (!identity.ruleType().equals(type)) throw fail("S2_TYPE", Stage.AUTHOR, "Reference requires " + type, source.child("rule_type"));
        return identity;
    }

    private static Field referenceField(EntryIdentity identity) {
        return switch (identity.rule().ruleType()) {
            case "catalog.language_set" -> Field.LANGUAGE;
            case "catalog.tool_set" -> Field.TOOL;
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
            default -> throw new IllegalArgumentException("Field is not a catalog reference");
        };
    }

    private static boolean sourceType(String type) {
        return Set.of("character.race", "character.subrace", "character.background", "character.class").contains(type);
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
        if (!identity.ruleType().equals(type)) throw fail("S2_TYPE", Stage.APPLY, "Reference has the wrong domain type", source);
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

    private static void finalValidation(Map<Identity, MutableObject> objects) {
        Map<RuleIdentity, Set<RuleIdentity>> candidates = new HashMap<>();
        for (MutableObject object : objects.values()) {
            if (object.identity instanceof RuleIdentity rule && rule.ruleType().equals("character.subrace")) {
                RuleIdentity parent = ((IdentityValue) object.fields.get(Field.PARENT_RACE).value()).value();
                if (!parent.ruleType().equals("character.race") || !objects.containsKey(parent))
                    throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Subrace requires a real parent race", object.source);
            }
            if (!(object.identity instanceof EntryIdentity identity)) continue;
            MutableObject owner = objects.get(identity.rule());
            Field reference = referenceField(identity);
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
                    || !candidate.ruleType().equals(referenceType(reference))) {
                throw fail("S2_REFERENCE_MISSING", Stage.FINAL, "Final entry ownership and typed catalog references must remain valid", object.source);
            }
            if (enabled(object) && enabled(target)) candidates.computeIfAbsent(identity.rule(), ignored -> new HashSet<>()).add(candidate);
        }
        for (MutableObject object : objects.values()) {
            if (object.identity instanceof RuleIdentity identity
                    && (identity.ruleType().equals("catalog.language_set") || identity.ruleType().equals("catalog.tool_set"))) {
                int minimum = ((IntegerValue) object.fields.get(Field.MINIMUM_ENABLED_MEMBERS).value()).value();
                if (candidates.getOrDefault(identity, Set.of()).size() < minimum) {
                    throw fail("S2_CANDIDATES_INSUFFICIENT", Stage.FINAL, "Distinct enabled catalog candidates are below the declared minimum", object.source);
                }
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
        if (identity instanceof EntryIdentity entry) return List.of(referenceField(entry), Field.AUTHOR_NOTE);
        String type = ((RuleIdentity) identity).ruleType();
        if (type.equals("character.language") || type.equals("character.tool")) {
            return List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.CATEGORY, Field.SOURCE_PAGE, Field.AUTHOR_NOTE);
        }
        if (sourceType(type)) return type.equals("character.subrace")
                ? List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE, Field.PARENT_RACE, Field.AUTHOR_NOTE)
                : List.of(Field.DISPLAY_NAME, Field.DESCRIPTION, Field.SOURCE_PAGE, Field.AUTHOR_NOTE);
        return List.of(Field.DISPLAY_NAME, Field.MINIMUM_ENABLED_MEMBERS, Field.AUTHOR_NOTE);
    }
    private static Map<Field, FieldState> definitionFields(Definition value) {
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
        result.put(referenceField(value.identity()), present(value instanceof ProficiencyGrantEntry grant
                ? new GrantValue(grant.grant()) : new IdentityValue(value.target())));
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
        int minimum = ((IntegerValue) fields.get(Field.MINIMUM_ENABLED_MEMBERS).value()).value();
        return identity.ruleType().equals("catalog.language_set")
                ? new FinalLanguageSet(new LanguageSetDefinition(identity, textField(fields, Field.DISPLAY_NAME), minimum, noteField(fields)), enabled)
                : new FinalToolSet(new ToolSetDefinition(identity, textField(fields, Field.DISPLAY_NAME), minimum, noteField(fields)), enabled);
    }
    private static Entry finalEntry(EntryIdentity identity, Map<Field, FieldState> fields) {
        Field reference = referenceField(identity);
        if (reference == Field.GRANT) return new ProficiencyGrantEntry(identity, ((GrantValue) fields.get(Field.GRANT).value()).value(), noteField(fields));
        RuleIdentity target = ((IdentityValue) fields.get(reference).value()).value();
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
        } else result.put("minimum_enabled_members", value instanceof LanguageSetDefinition set
                ? set.minimumEnabledMembers() : ((ToolSetDefinition) value).minimumEnabledMembers());
        if (value.authorNote() != null) result.put("author_note", value.authorNote());
        return result;
    }
    private static Map<String, Object> entryJson(Entry value) {
        Map<String, Object> result = json("identity", identityJson(value.identity()));
        if (value instanceof ProficiencyGrantEntry grant) result.put("grant", grantJson(grant.grant()));
        else result.put(value instanceof LanguageEntry ? "language" : "tool", identityJson(value.target()));
        if (value.authorNote() != null) result.put("author_note", value.authorNote());
        return result;
    }
    private static Map<String, Object> grantJson(GrantSpec value) {
        if (value instanceof FixedGrant fixed) return json("category", fixed.category().name(), "kind", "FIXED", "target", identityJson(fixed.target()));
        ChoiceGrant choice = (ChoiceGrant) value;
        return json("category", choice.category().name(), "kind", "CHOICE", "candidates", identityJson(choice.candidates()), "count", choice.count());
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
