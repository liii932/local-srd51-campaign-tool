package com.dndtool.persistence;

import com.dndtool.module.composition.ProductionRuleArtifact;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Original finite spell and Life domain casting mechanisms, checked against every original ZIP. */
final class JdbcSchema2SpellSourceRows {
    private static final String PACKAGE = "artifact_module_key, release_version, author_index";
    private static final String RULE = "module_key, rule_type, rule_key";
    private static final String COMMON = PACKAGE + ", " + RULE + ", display_name, description, source_page";
    private static final String SPELL = COMMON + ", algorithm, author_note";
    private static final String CASTING = COMMON + ", owner_class_module_key, owner_class_rule_type, owner_class_rule_key, required_subclass_module_key, required_subclass_rule_type, required_subclass_rule_key, required_feature_module_key, required_feature_rule_type, required_feature_rule_key, slot_resource_module_key, slot_resource_rule_type, slot_resource_rule_key, bless_module_key, bless_rule_type, bless_rule_key, cure_wounds_module_key, cure_wounds_rule_type, cure_wounds_rule_key, material_item_module_key, material_item_rule_type, material_item_rule_key, ability_key, algorithm, author_note";
    private static final String CHANGES = PACKAGE + ", rule_module_key, rule_type, rule_key, operation, field_key, text_value, integer_value, boolean_value, reference_module_key, reference_rule_type, reference_rule_key";
    private static final Map<String,String> TABLES=Map.of("rule_schema2_spell",SPELL,"rule_schema2_spellcasting",CASTING,"rule_schema2_spell_change",CHANGES);
    private JdbcSchema2SpellSourceRows() { }
    static void store(Connection connection, ProductionRuleArtifact artifact) throws SQLException {
        for (Row row : expected(artifact)) try (var statement = connection.prepareStatement("INSERT INTO " + row.table + " (" + row.columns + ") VALUES (" + String.join(", ", Collections.nCopies(row.values.size(), "?")) + ")")) {
            statement.setQueryTimeout(5);
            for (int i = 0; i < row.values.size(); i++) {
                Object value = row.values.get(i);
                if (value instanceof byte[] bytes) statement.setBytes(i + 1, bytes);
                else if (value instanceof String text) statement.setString(i + 1, text);
                else if (value instanceof Integer number) statement.setInt(i + 1, number);
                else if (value == null) statement.setNull(i + 1, Types.NULL);
                else throw invalid();
            }
            require(statement.executeUpdate() == 1);
        }
    }
    static void verifyAll(Connection connection, Collection<ProductionRuleArtifact> artifacts) throws SQLException {
        List<Row> expected = new ArrayList<>(), actual = new ArrayList<>();
        for (var artifact : artifacts) expected.addAll(expected(artifact));
        for (String table : TABLES.keySet().stream().sorted().toList()) {
            String columns = TABLES.get(table); int maximum = Schema2SourceLedger.MAX_ARTIFACTS * (table.endsWith("_change") ? 4096 : 256);
            try (var statement = connection.prepareStatement("SELECT " + columns + " FROM " + table + " ORDER BY artifact_module_key, release_version, author_index")) {
                statement.setQueryTimeout(5); statement.setMaxRows(maximum + 1);
                try (var result = statement.executeQuery()) {
                    int count = 0;
                    while (result.next()) {
                        require(count++ < maximum); List<Object> values = new ArrayList<>();
                        for (int i = 0; i < columns.split(", ").length; i++) values.add(result.getObject(i + 1));
                        actual.add(new Row(table, columns, values));
                    }
                }
            }
        }
        expected.sort(ORDER); actual.sort(ORDER); require(expected.size() == actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Row wanted = expected.get(i), found = actual.get(i); require(wanted.table.equals(found.table) && wanted.values.size() == found.values.size());
            for (int j = 0; j < wanted.values.size(); j++) require(same(wanted.values.get(j), found.values.get(j)));
        }
    }
    private static List<Row> expected(ProductionRuleArtifact artifact) throws SQLException {
        List<Row> rows = new ArrayList<>(); var author = artifact.authorPackage();
        for (int i = 0; i < author.definitions().size(); i++) {
            Definition definition = author.definitions().get(i); if (!supports(definition.identity().ruleType())) continue;
            List<Object> values = prefix(artifact, author.definitionAuthorIndex(i)); reference(values, definition.identity()); values.add(definition.displayName()); String table;
            if (definition instanceof SpellDefinition spell) {
                table="rule_schema2_spell"; Collections.addAll(values,spell.description(),spell.sourcePage(),ascii(spell.algorithm()),spell.authorNote());
            } else if (definition instanceof SpellcastingDefinition casting) {
                table="rule_schema2_spellcasting"; Collections.addAll(values,casting.description(),casting.sourcePage());
                for (RuleIdentity ref:List.of(casting.ownerClass(),casting.requiredSubclass(),casting.requiredFeature(),casting.slotResource(),casting.bless(),casting.cureWounds(),casting.materialItem())) reference(values,ref);
                Collections.addAll(values,ascii(casting.abilityKey()),ascii(casting.algorithm()),casting.authorNote());
            } else throw invalid();
            rows.add(new Row(table, TABLES.get(table), values));
        }
        for (int i = 0; i < author.changes().size(); i++) {
            Change change = author.changes().get(i); if (!(change.target() instanceof RuleIdentity identity) || !supports(identity.ruleType())) continue;
            List<Object> values = prefix(artifact, author.changeAuthorIndex(i)); reference(values, identity);
            Collections.addAll(values, ascii(change instanceof SetField ? "set_field" : change instanceof ClearField ? "clear_field" : "set_enabled"), ascii(change.field().key()));
            FieldValue value = change instanceof SetField set ? set.value() : change instanceof SetEnabled enabled ? new BooleanValue(enabled.value()) : null;
            Collections.addAll(values, value instanceof TextValue text ? text.value() : null, value instanceof IntegerValue integer ? integer.value() : null, value instanceof BooleanValue bool ? bool.value() ? 1 : 0 : null);
            reference(values, value instanceof IdentityValue ref ? ref.value() : null);
            rows.add(new Row("rule_schema2_spell_change", CHANGES, values));
        }
        return rows;
    }
    private static boolean supports(String type) { return Set.of("adventure.spell", "character.spellcasting").contains(type); }
    private static List<Object> prefix(ProductionRuleArtifact artifact, int index) { return new ArrayList<>(List.of(ascii(artifact.moduleKey()), ascii(artifact.releaseVersion()), index)); }
    private static void reference(List<Object> values, RuleIdentity ref) { Collections.addAll(values, ref == null ? null : ascii(ref.moduleKey()), ref == null ? null : ascii(ref.ruleType()), ref == null ? null : ascii(ref.ruleKey())); }
    private static final Comparator<Row> ORDER = Comparator.comparing((Row row) -> row.table).thenComparing(row -> key(row.values.get(0))).thenComparing(row -> key(row.values.get(1))).thenComparingInt(row -> row.values.get(2) instanceof Number n ? n.intValue() : -1);
    private static String key(Object value) { return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.ISO_8859_1) : ""; }
    private static boolean same(Object expected, Object actual) {
        if (expected instanceof byte[] bytes) return actual instanceof byte[] other && Arrays.equals(bytes, other);
        if (expected instanceof Integer n) return (actual instanceof Byte || actual instanceof Short || actual instanceof Integer || actual instanceof Long) && n.longValue() == ((Number) actual).longValue();
        return Objects.equals(expected, actual);
    }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static void require(boolean value) throws SQLException { if (!value) throw invalid(); }
    private static SQLException invalid() { return new SQLException("S2_SPELL_SOURCE_CORRUPT"); }
    private record Row(String table, String columns, List<Object> values) { }
}
