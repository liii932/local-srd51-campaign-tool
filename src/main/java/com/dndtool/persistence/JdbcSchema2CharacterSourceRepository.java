package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Original character-source declarations and operations, independently checked against original ZIPs.
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
    private static final int MAX_SOURCES = 256, MAX_SLOTS = 512, MAX_CHANGES = 4096;

    private JdbcSchema2CharacterSourceRepository() { }

    /** Called after the immutable artifact INSERT, before its control update, in the same transaction. */
    public static void store(Connection connection, ProductionRuleArtifact artifact) throws SQLException {
        require(connection != null && !connection.getAutoCommit() && !connection.isReadOnly());
        var material = expected(Objects.requireNonNull(artifact));
        for (Row row : material) insert(connection, row);
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
        actual.sort(ORDER);
        require(actual.size() == expected.size());
        for (int i = 0; i < expected.size(); i++) {
            Row wanted = expected.get(i), found = actual.get(i);
            require(wanted.table.equals(found.table) && wanted.columns.equals(found.columns) && wanted.values.size() == found.values.size());
            for (int j = 0; j < wanted.values.size(); j++) require(equal(wanted.values.get(j), found.values.get(j)));
        }
    }

    private static List<Row> expected(ProductionRuleArtifact artifact) throws SQLException {
        List<Row> rows = new ArrayList<>(); var author = artifact.authorPackage();
        int sources = 0, slots = 0, changes = 0;
        for (int i = 0; i < author.definitions().size(); i++) {
            if (!(author.definitions().get(i) instanceof CharacterSourceDefinition value)) continue;
            require(++sources <= MAX_SOURCES);
            List<Object> values = prefix(artifact, author.definitionAuthorIndex(i)); rule(values, value.identity());
            Collections.addAll(values, value.displayName(), value.description(), value.sourcePage(),
                    value.parentRace() == null ? null : ascii(value.parentRace().moduleKey()),
                    value.parentRace() == null ? null : ascii(value.parentRace().ruleKey()), value.authorNote());
            rows.add(new Row("rule_schema2_character_source", SOURCES, values));
        }
        for (int i = 0; i < author.entries().size(); i++) {
            if (!(author.entries().get(i) instanceof ProficiencyGrantEntry value)) continue;
            require(++slots <= MAX_SLOTS);
            List<Object> values = prefix(artifact, author.entryAuthorIndex(i)); var key = value.identity();
            Collections.addAll(values, ascii(key.rule().moduleKey()), ascii(key.rule().ruleType()), ascii(key.rule().ruleKey()),
                    ascii(key.collectionKey()), ascii(key.moduleKey()), ascii(key.entryKey()));
            values.addAll(grant(value.grant())); values.add(value.authorNote());
            rows.add(new Row("rule_schema2_proficiency_grant", SLOTS, values));
        }
        for (int i = 0; i < author.changes().size(); i++) {
            var change = author.changes().get(i); var target = change.target();
            RuleIdentity owner = target instanceof RuleIdentity rule ? rule : ((EntryIdentity) target).rule();
            if (!sourceType(owner.ruleType())) continue;
            require(++changes <= MAX_CHANGES);
            List<Object> values = prefix(artifact, author.changeAuthorIndex(i)); target(values, target);
            FieldValue value = change instanceof SetField set ? set.value() : change instanceof SetEnabled enabled ? new BooleanValue(enabled.value()) : null;
            Collections.addAll(values, ascii(change instanceof SetField ? "set_field" : change instanceof ClearField ? "clear_field" : "set_enabled"), ascii(change.field().key()));
            Collections.addAll(values, value instanceof TextValue text ? text.value() : null, value instanceof IntegerValue integer ? integer.value() : null,
                    value instanceof BooleanValue bool ? (bool.value() ? 1 : 0) : null,
                    value instanceof IdentityValue ref ? ascii(ref.value().moduleKey()) : null,
                    value instanceof IdentityValue ref ? ascii(ref.value().ruleType()) : null,
                    value instanceof IdentityValue ref ? ascii(ref.value().ruleKey()) : null);
            values.addAll(value instanceof GrantValue atom ? grant(atom.value()) : Arrays.asList(null, null, null, null, null));
            rows.add(new Row("rule_schema2_character_change", CHANGES, values));
        }
        return rows;
    }

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
    private static void read(Connection connection, String table, String columns, int perArtifactMaximum, List<Row> output) throws SQLException {
        int maximum = Schema2SourceLedger.MAX_ARTIFACTS * perArtifactMaximum;
        try (var statement = connection.prepareStatement("SELECT " + columns + " FROM " + table + " ORDER BY artifact_module_key, release_version, author_index")) {
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
            .thenComparingInt(row -> row.values.get(2) instanceof Number value ? value.intValue() : -1);
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
