package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Original character-growth, check and rest declarations/operations, independently checked against original ZIPs.
 * This material store grants no installation or game eligibility. The caller owns the transaction. */
public final class JdbcSchema2CharacterSourceRepository {
    private static final String PACKAGE = "artifact_module_key, release_version, author_index";
    private static final String RULE = "module_key, rule_type, rule_key";
    private static final String TARGET = "target_kind, rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key";
    private static final String GRANT = "grant_category, grant_kind, grant_target_module_key, grant_target_rule_key, grant_count";
    private static final String VALUE = "text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key, " + GRANT;
    private static final String SOURCES = PACKAGE + ", " + RULE + ", display_name, description, source_page, parent_race_module_key, parent_race_rule_key, author_note";
    private static final String SLOTS = PACKAGE + ", rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, " + GRANT + ", author_note";
    private static final String CHANGES = PACKAGE + ", " + TARGET + ", operation, field_key, " + VALUE;
    private static final String PROGRESSIONS = PACKAGE + ", " + RULE + ", display_name, description, source_page, owner_class_module_key, owner_class_rule_type, owner_class_rule_key, hit_die_sides, proficiency_bonus_profile, supported_maximum_level, advancement_scope, multiclass_prerequisite_present, author_note";
    private static final String RESOURCES = PACKAGE + ", " + RULE + ", display_name, description, source_page, owner_class_module_key, owner_class_rule_type, owner_class_rule_key, maximum_profile, recovery_profile, execution_mode, author_note";
    private static final String RESTS = PACKAGE + ", " + RULE + ", display_name, description, source_page, algorithm, author_note";
    private static final String SUBCLASSES = PACKAGE + ", " + RULE + ", display_name, description, source_page, owner_class_module_key, owner_class_rule_type, owner_class_rule_key, selection_level, author_note";
    private static final String FEATURES = PACKAGE + ", " + RULE + ", display_name, description, source_page, owner_module_key, owner_rule_type, owner_rule_key, level, feature_kind, execution_mode, execution_algorithm, author_note";
    private static final String FEATS = PACKAGE + ", " + RULE + ", display_name, description, source_page, execution_mode, execution_algorithm, author_note";
    private static final String PROFICIENCIES = PACKAGE + ", " + RULE + ", display_name, description, source_page, category, ability_key, author_note";
    private static final String PROFICIENCY_SETS = PACKAGE + ", " + RULE + ", display_name, category, minimum_enabled_members, author_note";
    private static final String LEVELS = PACKAGE + ", rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, level, asi_available, author_note";
    private static final String PROFICIENCY_MEMBERS = PACKAGE + ", rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, proficiency_module_key, proficiency_rule_type, proficiency_rule_key, author_note";
    private static final String GROWTH_GRANT = "growth_grant_kind, growth_grant_target_module_key, growth_grant_target_rule_type, growth_grant_target_rule_key, growth_grant_count";
    private static final String GROWTH_GRANTS = PACKAGE + ", rule_module_key, rule_type, rule_key, collection_key, module_key, entry_key, " + GROWTH_GRANT + ", author_note";
    private static final String REQUIREMENTS = PACKAGE + ", author_kind, group_index, alternative_index, ability_key, minimum_value";
    private static final String ADVANCEMENT_CHANGES = PACKAGE + ", " + TARGET + ", operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key, " + GROWTH_GRANT + ", prerequisite_value";
    private static final int MAX_SOURCES = 256, MAX_SLOTS = 512, MAX_CHANGES = 4096, MAX_REQUIREMENTS = 1920;

    private JdbcSchema2CharacterSourceRepository() { }

    /** Called after the immutable artifact INSERT, before its control update, in the same transaction. */
    public static void store(Connection connection, ProductionRuleArtifact artifact) throws SQLException {
        require(connection != null && !connection.getAutoCommit() && !connection.isReadOnly());
        var material = expected(Objects.requireNonNull(artifact));
        for (Row row : material) insert(connection, row);
        JdbcSchema2ExecutionSourceRows.store(connection, artifact);
        JdbcSchema2CombatSourceRows.store(connection, artifact);
        JdbcSchema2SpellSourceRows.store(connection, artifact);
        JdbcSchema2WizardSourceRows.store(connection, artifact);
    }

    /** Reads all rows, including unselected/orphan materials, and compares every typed value to the ZIP. */
    public static void verifyAll(Connection connection, Collection<ProductionRuleArtifact> artifacts) throws SQLException {
        require(connection != null && !connection.getAutoCommit());
        List<Row> expected = new ArrayList<>();
        for (var artifact : artifacts) expected.addAll(expected(artifact));
        expected.sort(ORDER);
        List<Row> actual = new ArrayList<>();
        read(connection, "rule_schema2_character_source", SOURCES, MAX_SOURCES, actual);
        read(connection, "rule_schema2_proficiency_grant", SLOTS, MAX_SLOTS, actual);
        read(connection, "rule_schema2_character_change", CHANGES, MAX_CHANGES, actual);
        read(connection, "rule_schema2_class_progression", PROGRESSIONS, MAX_SOURCES, actual);
        read(connection, "rule_schema2_resource", RESOURCES, MAX_SOURCES, actual);
        read(connection, "rule_schema2_advancement_change", ADVANCEMENT_CHANGES, MAX_CHANGES, actual);
        read(connection, "rule_schema2_subclass", SUBCLASSES, MAX_SOURCES, actual);
        read(connection, "rule_schema2_feature", FEATURES, MAX_SOURCES, actual);
        read(connection, "rule_schema2_feat", FEATS, MAX_SOURCES, actual);
        read(connection, "rule_schema2_proficiency", PROFICIENCIES, MAX_SOURCES, actual);
        read(connection, "rule_schema2_proficiency_set", PROFICIENCY_SETS, MAX_SOURCES, actual);
        read(connection, "rule_schema2_class_level", LEVELS, MAX_SLOTS, actual);
        read(connection, "rule_schema2_proficiency_member", PROFICIENCY_MEMBERS, MAX_SLOTS, actual);
        read(connection, "rule_schema2_growth_grant", GROWTH_GRANTS, MAX_SLOTS, actual);
        read(connection, "rule_schema2_ability_requirement", REQUIREMENTS, MAX_REQUIREMENTS, actual);
        read(connection, "rule_schema2_rest", RESTS, MAX_SOURCES, actual);
        read(connection, "rule_schema2_check", RESTS, MAX_SOURCES, actual);
        actual.sort(ORDER);
        require(actual.size() == expected.size());
        for (int i = 0; i < expected.size(); i++) {
            Row wanted = expected.get(i), found = actual.get(i);
            require(wanted.table.equals(found.table) && wanted.columns.equals(found.columns) && wanted.values.size() == found.values.size());
            for (int j = 0; j < wanted.values.size(); j++) require(equal(wanted.values.get(j), found.values.get(j)));
        }
        JdbcSchema2ExecutionSourceRows.verifyAll(connection, artifacts);
        JdbcSchema2CombatSourceRows.verifyAll(connection, artifacts);
        JdbcSchema2SpellSourceRows.verifyAll(connection, artifacts);
        JdbcSchema2WizardSourceRows.verifyAll(connection, artifacts);
    }

    private static List<Row> expected(ProductionRuleArtifact artifact) throws SQLException {
        List<Row> rows = new ArrayList<>(); var author = artifact.authorPackage();
        int sources = 0, slots = 0, changes = 0;
        for (int i = 0; i < author.definitions().size(); i++) {
            Definition definition = author.definitions().get(i);
            if (!(definition instanceof CharacterSourceDefinition) && !advancementType(definition.identity().ruleType())) continue;
            require(++sources <= MAX_SOURCES);
            int index = author.definitionAuthorIndex(i);
            List<Object> values = prefix(artifact, index); rule(values, definition.identity()); values.add(definition.displayName());
            if (definition instanceof CharacterSourceDefinition value) {
                Collections.addAll(values, value.description(), value.sourcePage(), value.parentRace() == null ? null : ascii(value.parentRace().moduleKey()),
                        value.parentRace() == null ? null : ascii(value.parentRace().ruleKey()), value.authorNote());
                rows.add(new Row("rule_schema2_character_source", SOURCES, values));
            } else if (definition instanceof ClassProgressionDefinition value) {
                Collections.addAll(values, value.description(), value.sourcePage()); rule(values, value.ownerClass());
                Collections.addAll(values, value.hitDieSides(), value.proficiencyBonusProfile(), value.supportedMaximumLevel(),
                        ascii(value.advancementScope().name()), value.multiclassPrerequisite() == null ? 0 : 1, value.authorNote());
                rows.add(new Row("rule_schema2_class_progression", PROGRESSIONS, values));
                requirements(rows, artifact, index, "definitions", value.multiclassPrerequisite());
            } else if (definition instanceof ResourceDefinition value) {
                Collections.addAll(values, value.description(), value.sourcePage()); rule(values, value.ownerClass());
                Collections.addAll(values, value.maximumProfile(), value.recoveryProfile(), ascii(value.executionMode().name()), value.authorNote());
                rows.add(new Row("rule_schema2_resource", RESOURCES, values));
            } else if (definition instanceof CheckDefinition value) {
                Collections.addAll(values, value.description(), value.sourcePage(), ascii(value.algorithm()), value.authorNote());
                rows.add(new Row("rule_schema2_check", RESTS, values));
            } else if (definition instanceof RestDefinition value) {
                Collections.addAll(values, value.description(), value.sourcePage(), ascii(value.algorithm()), value.authorNote());
                rows.add(new Row("rule_schema2_rest", RESTS, values));
            } else if (definition instanceof SubclassDefinition value) {
                Collections.addAll(values, value.description(), value.sourcePage()); rule(values, value.ownerClass());
                Collections.addAll(values, value.selectionLevel(), value.authorNote());
                rows.add(new Row("rule_schema2_subclass", SUBCLASSES, values));
            } else if (definition instanceof FeatureDefinition value) {
                Collections.addAll(values, value.description(), value.sourcePage()); rule(values, value.owner());
                Collections.addAll(values, value.level(), ascii(value.kind().name()), ascii(value.executionMode().name()), ascii(value.executionAlgorithm()), value.authorNote());
                rows.add(new Row("rule_schema2_feature", FEATURES, values));
            } else if (definition instanceof FeatDefinition value) {
                Collections.addAll(values, value.description(), value.sourcePage(), ascii(value.executionMode().name()), ascii(value.executionAlgorithm()), value.authorNote());
                rows.add(new Row("rule_schema2_feat", FEATS, values));
                requirements(rows, artifact, index, "definitions", value.prerequisite());
            } else if (definition instanceof ProficiencyDefinition value) {
                Collections.addAll(values, value.description(), value.sourcePage(), ascii(value.category().name()),
                        value.abilityKey() == null ? null : ascii(value.abilityKey()), value.authorNote());
                rows.add(new Row("rule_schema2_proficiency", PROFICIENCIES, values));
            } else if (definition instanceof ProficiencySetDefinition value) {
                Collections.addAll(values, ascii(value.category().name()), value.minimumEnabledMembers(), value.authorNote());
                rows.add(new Row("rule_schema2_proficiency_set", PROFICIENCY_SETS, values));
            } else throw invalid();
        }
        for (int i = 0; i < author.entries().size(); i++) {
            var value = author.entries().get(i);
            if (!(value instanceof ProficiencyGrantEntry) && !(value instanceof ClassLevelEntry)
                    && !(value instanceof ProficiencyMemberEntry) && !(value instanceof GrowthProficiencyGrantEntry)) continue;
            require(++slots <= MAX_SLOTS);
            List<Object> values = prefix(artifact, author.entryAuthorIndex(i)); var key = value.identity();
            Collections.addAll(values, ascii(key.rule().moduleKey()), ascii(key.rule().ruleType()), ascii(key.rule().ruleKey()),
                    ascii(key.collectionKey()), ascii(key.moduleKey()), ascii(key.entryKey()));
            if (value instanceof ProficiencyGrantEntry grant) {
                values.addAll(grant(grant.grant())); values.add(grant.authorNote());
                rows.add(new Row("rule_schema2_proficiency_grant", SLOTS, values));
            } else if (value instanceof ClassLevelEntry level) {
                Collections.addAll(values, level.level(), level.asiAvailable() ? 1 : 0, level.authorNote());
                rows.add(new Row("rule_schema2_class_level", LEVELS, values));
            } else if (value instanceof ProficiencyMemberEntry member) {
                rule(values, member.proficiency()); values.add(member.authorNote());
                rows.add(new Row("rule_schema2_proficiency_member", PROFICIENCY_MEMBERS, values));
            } else if (value instanceof GrowthProficiencyGrantEntry grant) {
                values.addAll(growthGrant(grant.grant())); values.add(grant.authorNote());
                rows.add(new Row("rule_schema2_growth_grant", GROWTH_GRANTS, values));
            }
        }
        for (int i = 0; i < author.changes().size(); i++) {
            var change = author.changes().get(i); var target = change.target();
            RuleIdentity owner = target instanceof RuleIdentity rule ? rule : ((EntryIdentity) target).rule();
            boolean advancement = advancementType(owner.ruleType());
            if (!sourceType(owner.ruleType()) && !advancement) continue;
            require(++changes <= MAX_CHANGES);
            List<Object> values = prefix(artifact, author.changeAuthorIndex(i));
            target(values, target);
            FieldValue value = change instanceof SetField set ? set.value() : change instanceof SetEnabled enabled ? new BooleanValue(enabled.value()) : null;
            Collections.addAll(values, ascii(change instanceof SetField ? "set_field" : change instanceof ClearField ? "clear_field" : "set_enabled"), ascii(change.field().key()));
            Collections.addAll(values, value instanceof TextValue text ? text.value() : null, value instanceof IntegerValue integer ? integer.value() : null,
                    value instanceof BooleanValue bool ? (bool.value() ? 1 : 0) : null,
                    value instanceof IdentityValue ref ? ascii(ref.value().moduleKey()) : null,
                    value instanceof IdentityValue ref ? ascii(ref.value().ruleType()) : null,
                    value instanceof IdentityValue ref ? ascii(ref.value().ruleKey()) : null);
            if (advancement) {
                values.addAll(value instanceof GrowthGrantValue atom ? growthGrant(atom.value()) : Arrays.asList(null, null, null, null, null));
                values.add(value instanceof PrerequisiteValue ? 1 : null);
                rows.add(new Row("rule_schema2_advancement_change", ADVANCEMENT_CHANGES, values));
                if (value instanceof PrerequisiteValue prerequisite) requirements(rows, artifact, author.changeAuthorIndex(i), "changes", prerequisite.value());
            } else {
                values.addAll(value instanceof GrantValue atom ? grant(atom.value()) : Arrays.asList(null, null, null, null, null));
                rows.add(new Row("rule_schema2_character_change", CHANGES, values));
            }
        }
        return rows;
    }

    private static boolean advancementType(String type) { return Set.of("character.class_progression", "character.resource", "character.subclass", "character.feature", "character.feat", "character.proficiency", "catalog.proficiency_set", "adventure.rest", "adventure.check").contains(type); }
    private static boolean sourceType(String type) { return Set.of("character.race", "character.subrace", "character.background", "character.class").contains(type); }
    private static List<Object> prefix(ProductionRuleArtifact artifact, int index) { return new ArrayList<>(List.of(ascii(artifact.moduleKey()), ascii(artifact.releaseVersion()), index)); }
    private static void rule(List<Object> values, RuleIdentity key) { Collections.addAll(values, ascii(key.moduleKey()), ascii(key.ruleType()), ascii(key.ruleKey())); }
    private static void target(List<Object> values, com.dndtool.module.composition.Schema2CompositionKernel.Identity target) {
        RuleIdentity owner = target instanceof RuleIdentity rule ? rule : ((EntryIdentity) target).rule();
        values.add(ascii(target instanceof RuleIdentity ? "rule" : "entry")); rule(values, owner);
        if (target instanceof EntryIdentity entry) Collections.addAll(values, ascii(entry.collectionKey()), ascii(entry.moduleKey()), ascii(entry.entryKey()));
        else Collections.addAll(values, null, null, null);
    }
    private static List<Object> grant(GrantSpec grant) {
        var target = grant instanceof FixedGrant fixed ? fixed.target() : ((ChoiceGrant) grant).candidates();
        return Arrays.asList(ascii(grant.category().name()), ascii(grant instanceof FixedGrant ? "FIXED" : "CHOICE"), ascii(target.moduleKey()), ascii(target.ruleKey()),
                grant instanceof ChoiceGrant choice ? choice.count() : null);
    }
    private static List<Object> growthGrant(GrowthGrantSpec grant) {
        var target = grant instanceof FixedGrowthGrant fixed ? fixed.target() : ((ChoiceGrowthGrant) grant).candidates();
        return Arrays.asList(ascii(grant instanceof FixedGrowthGrant ? "FIXED" : "CHOICE"), ascii(target.moduleKey()), ascii(target.ruleType()), ascii(target.ruleKey()),
                grant instanceof ChoiceGrowthGrant choice ? choice.count() : null);
    }
    private static void requirements(List<Row> rows, ProductionRuleArtifact artifact, int index, String kind, AbilityPrerequisite prerequisite) throws SQLException {
        if (prerequisite == null) return;
        int count = 0;
        for (int group = 0; group < prerequisite.allOf().size(); group++) {
            var alternatives = prerequisite.allOf().get(group).anyOf();
            for (int alternative = 0; alternative < alternatives.size(); alternative++) {
                require(++count <= 6);
                var minimum = alternatives.get(alternative); List<Object> values = prefix(artifact, index);
                Collections.addAll(values, ascii(kind), group, alternative, ascii(minimum.abilityKey()), minimum.minimum());
                rows.add(new Row("rule_schema2_ability_requirement", REQUIREMENTS, values));
            }
        }
    }
    private static void read(Connection connection, String table, String columns, int perArtifactMaximum, List<Row> output) throws SQLException {
        int maximum = Schema2SourceLedger.MAX_ARTIFACTS * perArtifactMaximum;
        String childOrder = table.equals("rule_schema2_ability_requirement") ? ", author_kind, group_index, alternative_index" : "";
        try (var statement = connection.prepareStatement("SELECT " + columns + " FROM " + table + " ORDER BY artifact_module_key, release_version, author_index" + childOrder)) {
            statement.setQueryTimeout(5); statement.setMaxRows(maximum + 1);
            try (var result = statement.executeQuery()) {
                int count = 0;
                while (result.next()) {
                    require(count++ < maximum);
                    List<Object> values = new ArrayList<>();
                    for (int i = 0; i < columns.split(", ").length; i++) values.add(result.getObject(i + 1));
                    output.add(new Row(table, columns, values));
                }
            }
        }
    }
    private static void insert(Connection connection, Row row) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO " + row.table + " (" + row.columns + ") VALUES (" + String.join(", ", Collections.nCopies(row.values.size(), "?")) + ")")) {
            statement.setQueryTimeout(5);
            for (int i = 0; i < row.values.size(); i++) {
                Object value = row.values.get(i);
                if (value instanceof byte[] bytes) statement.setBytes(i + 1, bytes);
                else if (value instanceof String text) statement.setString(i + 1, text);
                else if (value instanceof Integer integer) statement.setInt(i + 1, integer);
                else if (value == null) statement.setNull(i + 1, Types.NULL);
                else throw invalid();
            }
            require(statement.executeUpdate() == 1);
        }
    }
    private static final Comparator<Row> ORDER = Comparator.comparing((Row row) -> row.table)
            .thenComparing(row -> key(row.values.get(0))).thenComparing(row -> key(row.values.get(1)))
            .thenComparingInt(row -> row.values.get(2) instanceof Number value ? value.intValue() : -1)
            .thenComparing(row -> row.table.equals("rule_schema2_ability_requirement") ? key(row.values.get(3)) : "")
            .thenComparingInt(row -> row.table.equals("rule_schema2_ability_requirement") ? ordinal(row.values.get(4)) : -1)
            .thenComparingInt(row -> row.table.equals("rule_schema2_ability_requirement") ? ordinal(row.values.get(5)) : -1);
    private static int ordinal(Object value) { return value instanceof Number number ? number.intValue() : -1; }
    private static String key(Object value) { return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.ISO_8859_1) : ""; }
    private static boolean equal(Object left, Object right) {
        if (left instanceof byte[] a) return right instanceof byte[] b && Arrays.equals(a, b);
        if (left instanceof Integer a) return (right instanceof Byte || right instanceof Short || right instanceof Integer || right instanceof Long) && a.longValue() == ((Number) right).longValue();
        return Objects.equals(left, right);
    }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static void require(boolean value) throws SQLException { if (!value) throw invalid(); }
    private static SQLException invalid() { return new SQLException("Invalid source schema 2 character material"); }
    private record Row(String table, String columns, List<Object> values) { }
}
