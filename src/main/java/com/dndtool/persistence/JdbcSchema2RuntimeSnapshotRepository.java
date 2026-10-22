package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Complete finite INTERNAL material snapshots. Never grants campaign initialization or execution. */
public final class JdbcSchema2RuntimeSnapshotRepository {
    private static final int MAX_RULES = 256, MAX_ENTRIES = 512, MAX_CHAINS = 3072, MAX_STEPS = 4096;
    private static final String TARGET = "target_kind, rule_module_key, rule_type, rule_key, collection_key, entry_module_key, entry_key";
    private static final String STATE = "state, text_value, integer_value, boolean_value, reference_module_key, reference_rule_key, grant_category, grant_kind, grant_target_module_key, grant_target_rule_key, grant_count";

    /** Caller owns the transaction and must roll it back on any failure, including later writes. */
    public Snapshot store(Connection connection, Identity identity, ProductionSchema2Projection projection) throws SQLException {
        require(identity != null && projection != null);
        ProductionSchema2Projection expected;
        try {
            expected = ProductionSchema2Projection.restore(projection.members(), projection.projection().rules(),
                    projection.projection().entries(), projection.projection().chains());
            sameProjection(projection, expected);
        } catch (RuntimeException malformed) { throw invalid(); }
        checkConnection(connection, true);
        insert(connection, "s2_runtime_identity", "run_id, snapshot_id", identity.runBytes(), identity.snapshotBytes());
        var typed = expected.projection();
        int stepCount = typed.chains().stream().mapToInt(chain -> chain.steps().size()).sum();
        insert(connection, "s2_runtime_snapshot", "snapshot_id, run_id, material_scope, canonical_profile, composition_algorithm, composition_sha256, final_rules_sha256, chain_sha256, member_count, rule_count, entry_count, chain_count, step_count",
                identity.snapshotBytes(), identity.runBytes(), ascii("INTERNAL"), ascii(expected.canonicalProfile()),
                ascii(ProductionSchema2Projection.COMPOSITION_ALGORITHM), ascii(expected.compositionSha256()),
                ascii(expected.finalRulesSha256()), ascii(expected.chainSha256()), expected.members().size(), typed.rules().size(),
                typed.entries().size(), typed.chains().size(), stepCount);
        for (int i = 0; i < expected.members().size(); i++) {
            var member = expected.members().get(i);
            insert(connection, "s2_runtime_member", "snapshot_id, load_index, module_key, release_version, role, raw_zip_sha256, logical_sha256",
                    identity.snapshotBytes(), i, ascii(member.moduleKey()), ascii(member.releaseVersion()), ascii(member.role().name()),
                    ascii(member.rawZipSha256()), ascii(member.logicalSha256()));
        }
        for (int i = 0; i < typed.rules().size(); i++) {
            FinalRule row = typed.rules().get(i);
            RuleIdentity key = row.identity();
            if (row instanceof FinalLanguage language) {
                var value = language.definition();
                insert(connection, "s2_runtime_language", "snapshot_id, rule_index, module_key, rule_key, display_name, description, category, source_page, author_note, enabled",
                        identity.snapshotBytes(), i, ascii(key.moduleKey()), ascii(key.ruleKey()), value.displayName(), value.description(),
                        ascii(value.category().name()), value.sourcePage(), value.authorNote(), bit(row.enabled()));
            } else if (row instanceof FinalLanguageSet languageSet) {
                var value = languageSet.definition();
                insert(connection, "s2_runtime_language_set", "snapshot_id, rule_index, module_key, rule_key, display_name, minimum_enabled_members, author_note, enabled",
                        identity.snapshotBytes(), i, ascii(key.moduleKey()), ascii(key.ruleKey()), value.displayName(),
                        value.minimumEnabledMembers(), value.authorNote(), bit(row.enabled()));
            } else if (row instanceof FinalTool tool) {
                var value = tool.definition();
                insert(connection, "s2_runtime_tool", "snapshot_id, rule_index, module_key, rule_key, display_name, description, category, source_page, author_note, enabled",
                        identity.snapshotBytes(), i, ascii(key.moduleKey()), ascii(key.ruleKey()), value.displayName(), value.description(),
                        ascii(value.category().name()), value.sourcePage(), value.authorNote(), bit(row.enabled()));
            } else if (row instanceof FinalToolSet toolSet) {
                var value = toolSet.definition();
                insert(connection, "s2_runtime_tool_set", "snapshot_id, rule_index, module_key, rule_key, display_name, minimum_enabled_members, author_note, enabled",
                        identity.snapshotBytes(), i, ascii(key.moduleKey()), ascii(key.ruleKey()), value.displayName(),
                        value.minimumEnabledMembers(), value.authorNote(), bit(row.enabled()));
            } else if (row instanceof FinalCharacterSource source) {
                if (!key.ruleType().equals("character.subrace")) insertSource(connection, identity, i, source);
            } else {
                throw invalid();
            }
        }
        // Parent races can belong to a later module; all races exist before subrace FK insertion.
        for (int i = 0; i < typed.rules().size(); i++)
            if (typed.rules().get(i) instanceof FinalCharacterSource source
                    && source.identity().ruleType().equals("character.subrace")) insertSource(connection, identity, i, source);
        for (int i = 0; i < typed.entries().size(); i++) {
            var row = typed.entries().get(i); var value = row.entry(); var key = value.identity();
            if (value instanceof ProficiencyGrantEntry grant) {
                List<Object> values = new ArrayList<>(List.of(identity.snapshotBytes(), i, ascii(key.rule().moduleKey()),
                        ascii(key.rule().ruleType()), ascii(key.rule().ruleKey()), ascii(key.collectionKey()), ascii(key.moduleKey()), ascii(key.entryKey())));
                values.addAll(grantValues(grant.grant())); values.add(value.authorNote()); values.add(bit(row.enabled()));
                insert(connection, "s2_runtime_proficiency_grant", "snapshot_id, entry_index, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, grant_category, grant_kind, grant_target_module_key, grant_target_rule_key, grant_count, author_note, enabled", values.toArray());
                continue;
            }
            boolean tool = value instanceof ToolEntry;
            String table = tool ? "s2_runtime_tool_entry" : "s2_runtime_language_entry";
            String reference = tool ? "tool" : "language";
            insert(connection, table, "snapshot_id, entry_index, rule_module_key, rule_key, collection_key, module_key, entry_key, " + reference + "_module_key, " + reference + "_rule_key, author_note, enabled",
                    identity.snapshotBytes(), i, ascii(key.rule().moduleKey()), ascii(key.rule().ruleKey()), ascii(key.collectionKey()),
                    ascii(key.moduleKey()), ascii(key.entryKey()), ascii(value.target().moduleKey()), ascii(value.target().ruleKey()),
                    value.authorNote(), bit(row.enabled()));
        }
        for (int i = 0; i < typed.chains().size(); i++) {
            var chain = typed.chains().get(i);
            List<Object> values = new ArrayList<>(List.of(identity.snapshotBytes(), i));
            values.addAll(targetValues(chain.target())); values.add(ascii(chain.field().key())); values.add(chain.steps().size());
            insert(connection, "s2_runtime_field_chain", "snapshot_id, chain_index, " + TARGET + ", field_key, step_count", values.toArray());
            for (int j = 0; j < chain.steps().size(); j++) {
                var step = chain.steps().get(j); var location = step.authorLocation();
                values = new ArrayList<>(List.of(identity.snapshotBytes(), i, j, ascii(step.op()), ascii(step.provider().moduleKey()),
                        ascii(step.provider().releaseVersion()), step.loadIndex(), ascii(location.rawZipSha256()), ascii(location.path()),
                        location.pointer(), ascii(step.origin())));
                values.addAll(stateValues(step.before())); values.addAll(stateValues(step.after()));
                insert(connection, "s2_runtime_chain_step", "snapshot_id, chain_index, step_index, operation, provider_module_key, provider_release_version, load_index, raw_zip_sha256, author_path, author_pointer, origin, "
                        + prefixedState("before_") + ", " + prefixedState("after_"), values.toArray());
            }
        }
        Snapshot actual = readRows(connection, identity);
        sameProjection(expected, actual.projection());
        return actual;
    }

    /** Requires the complete server-owned run/snapshot binding; reads only the runtime schema. */
    public Snapshot read(Connection connection, Identity identity) throws SQLException {
        require(identity != null); checkConnection(connection, false); return readRows(connection, identity);
    }

    private static Snapshot readRows(Connection connection, Identity identity) throws SQLException {
        try {
            var headers = query(connection, "SELECT s.*, i.run_id AS registered_run_id, i.snapshot_id AS registered_snapshot_id FROM s2_runtime_snapshot s JOIN s2_runtime_identity i ON i.run_id = s.run_id AND i.snapshot_id = s.snapshot_id WHERE s.run_id = ? AND s.snapshot_id = ? LIMIT 2",
                    1, List.of(identity.runBytes(), identity.snapshotBytes()), result -> {
                        exactBytes(result, "run_id", identity.runBytes()); exactBytes(result, "snapshot_id", identity.snapshotBytes());
                        exactBytes(result, "registered_run_id", identity.runBytes()); exactBytes(result, "registered_snapshot_id", identity.snapshotBytes());
                        exact(result, "material_scope", "INTERNAL"); exact(result, "canonical_profile", ProductionSchema2Projection.CANONICAL_PROFILE);
                        exact(result, "composition_algorithm", ProductionSchema2Projection.COMPOSITION_ALGORITHM);
                        return new Header(digest(result, "composition_sha256"), digest(result, "final_rules_sha256"), digest(result, "chain_sha256"),
                                integer(result, "member_count"), integer(result, "rule_count"), integer(result, "entry_count"), integer(result, "chain_count"), integer(result, "step_count"));
                    });
            require(headers.size() == 1); Header header = headers.getFirst();
            require(header.members >= 1 && header.members <= MAX_PACKAGES && header.rules >= 0 && header.rules <= MAX_RULES
                    && header.entries >= 0 && header.entries <= MAX_ENTRIES && header.chains >= 0 && header.chains <= MAX_CHAINS
                    && header.steps >= 0 && header.steps <= MAX_STEPS);
            List<StoredMember> members = ordered(rows(connection, identity, "s2_runtime_member", "load_index", MAX_PACKAGES, result -> {
                return new Indexed<>(integer(result, "load_index"), new StoredMember(binaryAscii(result, "module_key", 64),
                        binaryAscii(result, "release_version", 64), Role.valueOf(binaryAscii(result, "role", 13)),
                        digest(result, "raw_zip_sha256"), digest(result, "logical_sha256")));
            }));
            // Domain rows are independently loaded. A shared ordered index prevents gaps or duplicate rules across typed tables.
            TreeMap<Integer, FinalRule> rules = new TreeMap<>();
            for (var row : rows(connection, identity, "s2_runtime_language", "rule_index", MAX_RULES, result -> {
                RuleIdentity key = new RuleIdentity(binaryAscii(result, "module_key", 64), "character.language", binaryAscii(result, "rule_key", 128));
                return new Indexed<FinalRule>(integer(result, "rule_index"), new FinalLanguage(new LanguageDefinition(key,
                        text(result, "display_name", 120), text(result, "description", 1000), Category.valueOf(binaryAscii(result, "category", 8)),
                        integer(result, "source_page"), nullableText(result, "author_note", 1000)), bool(result, "enabled")));
            })) require(rules.putIfAbsent(row.index(), row.value()) == null);
            for (var row : rows(connection, identity, "s2_runtime_language_set", "rule_index", MAX_RULES, result -> {
                RuleIdentity key = new RuleIdentity(binaryAscii(result, "module_key", 64), "catalog.language_set", binaryAscii(result, "rule_key", 128));
                return new Indexed<FinalRule>(integer(result, "rule_index"), new FinalLanguageSet(new LanguageSetDefinition(key,
                        text(result, "display_name", 120), integer(result, "minimum_enabled_members"), nullableText(result, "author_note", 1000)), bool(result, "enabled")));
            })) require(rules.putIfAbsent(row.index(), row.value()) == null);
            for (var row : rows(connection, identity, "s2_runtime_tool", "rule_index", MAX_RULES, result -> {
                RuleIdentity key = new RuleIdentity(binaryAscii(result, "module_key", 64), "character.tool", binaryAscii(result, "rule_key", 128));
                return new Indexed<FinalRule>(integer(result, "rule_index"), new FinalTool(new ToolDefinition(key,
                        text(result, "display_name", 120), text(result, "description", 1000), ToolCategory.valueOf(binaryAscii(result, "category", 18)),
                        integer(result, "source_page"), nullableText(result, "author_note", 1000)), bool(result, "enabled")));
            })) require(rules.putIfAbsent(row.index(), row.value()) == null);
            for (var row : rows(connection, identity, "s2_runtime_tool_set", "rule_index", MAX_RULES, result -> {
                RuleIdentity key = new RuleIdentity(binaryAscii(result, "module_key", 64), "catalog.tool_set", binaryAscii(result, "rule_key", 128));
                return new Indexed<FinalRule>(integer(result, "rule_index"), new FinalToolSet(new ToolSetDefinition(key,
                        text(result, "display_name", 120), integer(result, "minimum_enabled_members"), nullableText(result, "author_note", 1000)), bool(result, "enabled")));
            })) require(rules.putIfAbsent(row.index(), row.value()) == null);
            for (var row : rows(connection, identity, "s2_runtime_character_source", "rule_index", MAX_RULES, result -> {
                RuleIdentity key = new RuleIdentity(binaryAscii(result, "module_key", 64), binaryAscii(result, "rule_type", 20), binaryAscii(result, "rule_key", 128));
                RuleIdentity parent = nullableReference(result, "parent_race_", "character.race");
                return new Indexed<FinalRule>(integer(result, "rule_index"), new FinalCharacterSource(new CharacterSourceDefinition(key,
                        text(result, "display_name", 120), text(result, "description", 1000), integer(result, "source_page"),
                        parent, nullableText(result, "author_note", 1000)), bool(result, "enabled")));
            })) require(rules.putIfAbsent(row.index(), row.value()) == null);
            require(rules.size() <= MAX_RULES);
            for (int i = 0; i < rules.size(); i++) require(rules.containsKey(i));
            TreeMap<Integer, FinalEntry> entryRows = new TreeMap<>();
            for (var row : rows(connection, identity, "s2_runtime_language_entry", "entry_index", MAX_ENTRIES, result -> {
                EntryIdentity key = new EntryIdentity(new RuleIdentity(binaryAscii(result, "rule_module_key", 64), "catalog.language_set", binaryAscii(result, "rule_key", 128)),
                        binaryAscii(result, "collection_key", 7), binaryAscii(result, "module_key", 64), binaryAscii(result, "entry_key", 64));
                return new Indexed<>(integer(result, "entry_index"), new FinalEntry(new LanguageEntry(key,
                        new RuleIdentity(binaryAscii(result, "language_module_key", 64), "character.language", binaryAscii(result, "language_rule_key", 128)),
                        nullableText(result, "author_note", 1000)), bool(result, "enabled")));
            })) require(entryRows.putIfAbsent(row.index(), row.value()) == null);
            for (var row : rows(connection, identity, "s2_runtime_tool_entry", "entry_index", MAX_ENTRIES, result -> {
                EntryIdentity key = new EntryIdentity(new RuleIdentity(binaryAscii(result, "rule_module_key", 64), "catalog.tool_set", binaryAscii(result, "rule_key", 128)),
                        binaryAscii(result, "collection_key", 7), binaryAscii(result, "module_key", 64), binaryAscii(result, "entry_key", 64));
                return new Indexed<>(integer(result, "entry_index"), new FinalEntry(new ToolEntry(key,
                        new RuleIdentity(binaryAscii(result, "tool_module_key", 64), "character.tool", binaryAscii(result, "tool_rule_key", 128)),
                        nullableText(result, "author_note", 1000)), bool(result, "enabled")));
            })) require(entryRows.putIfAbsent(row.index(), row.value()) == null);
            for (var row : rows(connection, identity, "s2_runtime_proficiency_grant", "entry_index", MAX_ENTRIES, result -> {
                EntryIdentity key = new EntryIdentity(new RuleIdentity(binaryAscii(result, "rule_module_key", 64), binaryAscii(result, "rule_type", 20), binaryAscii(result, "rule_key", 128)),
                        binaryAscii(result, "collection_key", 18), binaryAscii(result, "module_key", 64), binaryAscii(result, "entry_key", 64));
                return new Indexed<>(integer(result, "entry_index"), new FinalEntry(new ProficiencyGrantEntry(key, readGrant(result, ""),
                        nullableText(result, "author_note", 1000)), bool(result, "enabled")));
            })) require(entryRows.putIfAbsent(row.index(), row.value()) == null);
            require(entryRows.size() <= MAX_ENTRIES);
            for (int i = 0; i < entryRows.size(); i++) require(entryRows.containsKey(i));
            List<FinalEntry> entries = List.copyOf(entryRows.values());
            var storedChains = ordered(rows(connection, identity, "s2_runtime_field_chain", "chain_index", MAX_CHAINS, result -> {
                String fieldKey = binaryAscii(result, "field_key", 23); Field field = Arrays.stream(Field.values()).filter(f -> f.key().equals(fieldKey)).findFirst().orElseThrow();
                return new Indexed<>(integer(result, "chain_index"), new ChainHeader(readTarget(result), field, integer(result, "step_count")));
            }));
            var steps = rows(connection, identity, "s2_runtime_chain_step", "chain_index, step_index", MAX_STEPS, result -> {
                String module = binaryAscii(result, "provider_module_key", 64), version = binaryAscii(result, "provider_release_version", 64);
                int chainIndex = integer(result, "chain_index");
                require(chainIndex >= 0 && chainIndex < storedChains.size());
                Field field = storedChains.get(chainIndex).field;
                return new StoredStep(chainIndex, integer(result, "step_index"), new ChainStep(binaryAscii(result, "operation", 11),
                        readState(result, "before_", field), readState(result, "after_", field), new Provider(module, version), integer(result, "load_index"),
                        new Location(module, version, digest(result, "raw_zip_sha256"), binaryAscii(result, "author_path", 240),
                                textAllowEmpty(result, "author_pointer", 4096), null), binaryAscii(result, "origin", 16)));
            });
            List<FieldChain> chains = new ArrayList<>(); int cursor = 0;
            for (int i = 0; i < storedChains.size(); i++) {
                ChainHeader chain = storedChains.get(i); require(chain.count >= 1 && chain.count <= MAX_PACKAGES);
                List<ChainStep> chainSteps = new ArrayList<>();
                for (int j = 0; j < chain.count; j++) {
                    require(cursor < steps.size()); var step = steps.get(cursor++);
                    require(step.chain == i && step.index == j); chainSteps.add(step.value);
                }
                chains.add(new FieldChain(chain.target, chain.field, chainSteps));
            }
            require(cursor == steps.size());
            require(header.members == members.size() && header.rules == rules.size() && header.entries == entries.size()
                    && header.chains == chains.size() && header.steps == steps.size());
            ProductionSchema2Projection rebuilt = ProductionSchema2Projection.restore(members, List.copyOf(rules.values()), entries, chains);
            require(header.composition.equals(rebuilt.compositionSha256()) && header.finalRules.equals(rebuilt.finalRulesSha256()) && header.chain.equals(rebuilt.chainSha256()));
            return new Snapshot(identity, rebuilt);
        } catch (IllegalArgumentException | NoSuchElementException | NullPointerException malformed) { throw invalid(); }
    }

    private static List<Object> targetValues(com.dndtool.module.composition.Schema2CompositionKernel.Identity identity) {
        if (identity instanceof RuleIdentity rule) return Arrays.asList(ascii("rule"), ascii(rule.moduleKey()), ascii(rule.ruleType()), ascii(rule.ruleKey()), null, null, null);
        var entry = (EntryIdentity) identity;
        return List.of(ascii("entry"), ascii(entry.rule().moduleKey()), ascii(entry.rule().ruleType()), ascii(entry.rule().ruleKey()),
                ascii(entry.collectionKey()), ascii(entry.moduleKey()), ascii(entry.entryKey()));
    }
    private static com.dndtool.module.composition.Schema2CompositionKernel.Identity readTarget(ResultSet result) throws SQLException {
        var rule = new RuleIdentity(binaryAscii(result, "rule_module_key", 64), binaryAscii(result, "rule_type", 20), binaryAscii(result, "rule_key", 128));
        String kind = binaryAscii(result, "target_kind", 5);
        if (kind.equals("rule")) {
            require(result.getObject("collection_key") == null && result.getObject("entry_module_key") == null && result.getObject("entry_key") == null); return rule;
        }
        require(kind.equals("entry"));
        return new EntryIdentity(rule, binaryAscii(result, "collection_key", 18), binaryAscii(result, "entry_module_key", 64), binaryAscii(result, "entry_key", 64));
    }
    private static List<Object> stateValues(FieldState state) {
        FieldValue value = state.value();
        List<Object> values = new ArrayList<>(Arrays.asList(ascii(state.state().name()), value instanceof TextValue text ? text.value() : null,
                value instanceof IntegerValue integer ? integer.value() : null, value instanceof BooleanValue bool ? bit(bool.value()) : null,
                value instanceof IdentityValue reference ? ascii(reference.value().moduleKey()) : null,
                value instanceof IdentityValue reference ? ascii(reference.value().ruleKey()) : null));
        values.addAll(value instanceof GrantValue grant ? grantValues(grant.value()) : Arrays.asList(null, null, null, null, null));
        return values;
    }
    private static FieldState readState(ResultSet result, String prefix, Field field) throws SQLException {
        StateKind kind = StateKind.valueOf(binaryAscii(result, prefix + "state", 13));
        Object text = result.getObject(prefix + "text_value"), integer = result.getObject(prefix + "integer_value"), bool = result.getObject(prefix + "boolean_value"),
                module = result.getObject(prefix + "reference_module_key"), key = result.getObject(prefix + "reference_rule_key");
        boolean grant = false;
        for (String column : List.of("grant_category", "grant_kind", "grant_target_module_key", "grant_target_rule_key", "grant_count"))
            grant |= result.getObject(prefix + column) != null;
        int count = (text == null ? 0 : 1) + (integer == null ? 0 : 1) + (bool == null ? 0 : 1) + (module == null && key == null ? 0 : 1) + (grant ? 1 : 0);
        if (kind != StateKind.PRESENT) { require(count == 0); return new FieldState(kind, null); }
        require(count == 1);
        FieldValue value;
        if (text != null) value = new TextValue(text(result, prefix + "text_value", 1000));
        else if (integer != null) value = new IntegerValue(integer(result, prefix + "integer_value"));
        else if (bool != null) value = new BooleanValue(bool(result, prefix + "boolean_value"));
        else if (grant) { require(field == Field.GRANT); value = new GrantValue(readGrant(result, prefix)); }
        else {
            require(field == Field.LANGUAGE || field == Field.TOOL || field == Field.PARENT_RACE);
            value = new IdentityValue(new RuleIdentity(binaryAscii(result, prefix + "reference_module_key", 64),
                    field == Field.TOOL ? "character.tool" : field == Field.PARENT_RACE ? "character.race" : "character.language", binaryAscii(result, prefix + "reference_rule_key", 128)));
        }
        return new FieldState(kind, value);
    }
    private static void insertSource(Connection connection, Identity identity, int index, FinalCharacterSource source) throws SQLException {
        var value = source.definition(); var key = source.identity(); var parent = value.parentRace();
        insert(connection, "s2_runtime_character_source", "snapshot_id, rule_index, module_key, rule_type, rule_key, display_name, description, source_page, parent_race_module_key, parent_race_rule_type, parent_race_rule_key, author_note, enabled",
                identity.snapshotBytes(), index, ascii(key.moduleKey()), ascii(key.ruleType()), ascii(key.ruleKey()), value.displayName(), value.description(), value.sourcePage(),
                parent == null ? null : ascii(parent.moduleKey()), parent == null ? null : ascii(parent.ruleType()), parent == null ? null : ascii(parent.ruleKey()), value.authorNote(), bit(source.enabled()));
    }
    private static RuleIdentity nullableReference(ResultSet result, String prefix, String type) throws SQLException {
        Object module = result.getObject(prefix + "module_key"), ruleType = result.getObject(prefix + "rule_type"), key = result.getObject(prefix + "rule_key");
        if (module == null) { require(ruleType == null && key == null); return null; }
        exact(result, prefix + "rule_type", type);
        return new RuleIdentity(binaryAscii(result, prefix + "module_key", 64), type, binaryAscii(result, prefix + "rule_key", 128));
    }
    private static List<Object> grantValues(GrantSpec grant) {
        RuleIdentity target = grant instanceof FixedGrant fixed ? fixed.target() : ((ChoiceGrant) grant).candidates();
        return Arrays.asList(ascii(grant.category().name()), ascii(grant instanceof FixedGrant ? "FIXED" : "CHOICE"),
                ascii(target.moduleKey()), ascii(target.ruleKey()), grant instanceof ChoiceGrant choice ? choice.count() : null);
    }
    private static GrantSpec readGrant(ResultSet result, String prefix) throws SQLException {
        GrantCategory category = GrantCategory.valueOf(binaryAscii(result, prefix + "grant_category", 8));
        String kind = binaryAscii(result, prefix + "grant_kind", 6);
        require(kind.equals("FIXED") || kind.equals("CHOICE"));
        String type = category == GrantCategory.LANGUAGE ? kind.equals("FIXED") ? "character.language" : "catalog.language_set"
                : kind.equals("FIXED") ? "character.tool" : "catalog.tool_set";
        RuleIdentity target = new RuleIdentity(binaryAscii(result, prefix + "grant_target_module_key", 64), type, binaryAscii(result, prefix + "grant_target_rule_key", 128));
        if (kind.equals("FIXED")) { require(result.getObject(prefix + "grant_count") == null); return new FixedGrant(category, target); }
        int count = integer(result, prefix + "grant_count"); require(count >= 1 && count <= MAX_GRANT_CHOICE_COUNT);
        return new ChoiceGrant(category, target, count);
    }
    private static String prefixedState(String prefix) { return Arrays.stream(STATE.split(", ")).map(column -> prefix + column).collect(java.util.stream.Collectors.joining(", ")); }
    private static void sameProjection(ProductionSchema2Projection expected, ProductionSchema2Projection actual) throws SQLException {
        require(Arrays.equals(expected.configurationJson(), actual.configurationJson()) && Arrays.equals(expected.finalRulesJson(), actual.finalRulesJson()) && Arrays.equals(expected.chainsJson(), actual.chainsJson()));
    }
    private static void checkConnection(Connection connection, boolean write) throws SQLException {
        require(connection != null && !connection.getAutoCommit() && (!write || !connection.isReadOnly()));
        int isolation = connection.getTransactionIsolation();
        require(isolation == Connection.TRANSACTION_READ_COMMITTED || isolation == Connection.TRANSACTION_REPEATABLE_READ || isolation == Connection.TRANSACTION_SERIALIZABLE);
        require(query(connection, "SELECT CAST(DATABASE() AS BINARY) AS schema_name", 1, List.of(), result -> { exact(result, "schema_name", "dnd_tool_se"); return true; }).size() == 1);
    }
    private static void insert(Connection connection, String table, String columns, Object... values) throws SQLException {
        String placeholders = String.join(", ", Collections.nCopies(values.length, "?"));
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")")) {
            statement.setQueryTimeout(5); bind(statement, Arrays.asList(values)); require(statement.executeUpdate() == 1);
        }
    }
    private static <T> List<T> rows(Connection connection, Identity identity, String table, String order, int maximum, Mapper<T> mapper) throws SQLException {
        return query(connection, "SELECT * FROM " + table + " WHERE snapshot_id = ? ORDER BY " + order + " LIMIT " + (maximum + 1), maximum,
                List.of(identity.snapshotBytes()), result -> { exactBytes(result, "snapshot_id", identity.snapshotBytes()); return mapper.map(result); });
    }
    private static <T> List<T> query(Connection connection, String sql, int maximum, List<?> values, Mapper<T> mapper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5); statement.setMaxRows(maximum + 1); bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>(); while (result.next()) { require(rows.size() < maximum); rows.add(mapper.map(result)); } return rows;
            }
        }
    }
    private static void bind(PreparedStatement statement, List<?> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof byte[] bytes) statement.setBytes(i + 1, bytes);
            else if (value instanceof String text) statement.setString(i + 1, text);
            else if (value instanceof Integer integer) statement.setInt(i + 1, integer);
            else if (value == null) statement.setNull(i + 1, Types.NULL);
            else throw invalid();
        }
    }
    private static <T> List<T> ordered(List<Indexed<T>> rows) throws SQLException {
        List<T> result = new ArrayList<>(); for (int i = 0; i < rows.size(); i++) { require(rows.get(i).index == i); result.add(rows.get(i).value); } return result;
    }
    private static int integer(ResultSet result, String column) throws SQLException {
        Object raw = result.getObject(column); require(raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long);
        long value = ((Number) raw).longValue(); require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE); return (int) value;
    }
    private static boolean bool(ResultSet result, String column) throws SQLException { int value = integer(result, column); require(value == 0 || value == 1); return value == 1; }
    private static byte[] binary(ResultSet result, String column) throws SQLException { Object value = result.getObject(column); require(value instanceof byte[]); return (byte[]) value; }
    private static void exactBytes(ResultSet result, String column, byte[] value) throws SQLException { require(Arrays.equals(value, binary(result, column))); }
    private static void exact(ResultSet result, String column, String value) throws SQLException { exactBytes(result, column, ascii(value)); }
    private static String digest(ResultSet result, String column) throws SQLException { String value = binaryAscii(result, column, 64); require(value.matches("[0-9a-f]{64}")); return value; }
    private static String binaryAscii(ResultSet result, String column, int maximum) throws SQLException {
        byte[] value = binary(result, column); require(value.length > 0 && value.length <= maximum); for (byte item : value) require(item >= 0x21 && item <= 0x7e); return new String(value, StandardCharsets.US_ASCII);
    }
    private static String nullableText(ResultSet result, String column, int maximum) throws SQLException { return result.getObject(column) == null ? null : text(result, column, maximum); }
    private static String text(ResultSet result, String column, int maximum) throws SQLException { String value = textAllowEmpty(result, column, maximum); require(!value.isEmpty()); return value; }
    private static String textAllowEmpty(ResultSet result, String column, int maximum) throws SQLException {
        Object value = result.getObject(column);
        require(value instanceof String && ((String) value).codePointCount(0, ((String) value).length()) <= maximum);
        return (String) value;
    }
    private static int bit(boolean value) { return value ? 1 : 0; }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static void require(boolean condition) throws SQLException { if (!condition) throw invalid(); }
    private static SQLException invalid() { return new SQLException("Invalid schema 2 runtime snapshot"); }
    @FunctionalInterface private interface Mapper<T> { T map(ResultSet result) throws SQLException; }
    private record Indexed<T>(int index, T value) { }
    private record Header(String composition, String finalRules, String chain, int members, int rules, int entries, int chains, int steps) { }
    private record ChainHeader(com.dndtool.module.composition.Schema2CompositionKernel.Identity target, Field field, int count) { }
    private record StoredStep(int chain, int index, ChainStep value) { }
    public record Snapshot(Identity identity, ProductionSchema2Projection projection) { }

    /** Distinct server-owned UUIDv4 identities, stored in exact network byte order. */
    public record Identity(UUID runId, UUID snapshotId) {
        public Identity { if (!valid(runId) || !valid(snapshotId) || runId.equals(snapshotId)) throw new IllegalArgumentException("Invalid schema 2 runtime identity"); }
        public static Identity random() { UUID run = UUID.randomUUID(), snapshot; do { snapshot = UUID.randomUUID(); } while (snapshot.equals(run)); return new Identity(run, snapshot); }
        public static Identity fromBytes(byte[] run, byte[] snapshot) { return new Identity(decode(run), decode(snapshot)); }
        public byte[] runBytes() { return encode(runId); }
        public byte[] snapshotBytes() { return encode(snapshotId); }
        private static boolean valid(UUID value) { return value != null && value.version() == 4 && value.variant() == 2; }
        private static UUID decode(byte[] bytes) { if (bytes == null || bytes.length != 16) throw new IllegalArgumentException("Invalid UUID"); var buffer = ByteBuffer.wrap(bytes); UUID value = new UUID(buffer.getLong(), buffer.getLong()); if (!valid(value)) throw new IllegalArgumentException("Invalid UUID"); return value; }
        private static byte[] encode(UUID value) { return ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array(); }
    }
}
