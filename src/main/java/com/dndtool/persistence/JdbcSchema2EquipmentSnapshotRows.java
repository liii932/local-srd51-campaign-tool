package com.dndtool.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Closed equipment/direct-health typed rules in the caller-owned snapshot transaction. */
final class JdbcSchema2EquipmentSnapshotRows {
    private JdbcSchema2EquipmentSnapshotRows() { }
    static boolean supports(FinalRule row) { return row instanceof FinalItem || row instanceof FinalHealth || row instanceof FinalVitalEffect; }
    static void store(Connection connection, Identity id, int index, FinalRule row) throws SQLException {
        List<Object> values = new ArrayList<>(List.of(id.snapshotBytes(), index, ascii(row.identity().moduleKey()), ascii(row.identity().ruleKey()), row.definition().displayName()));
        String table, columns;
        if (row instanceof FinalItem v) {
            var d = v.definition(); table = "equipment_item"; columns = "kind, armor_class, strength_minimum, stealth_disadvantage, hands, proficiency_module, proficiency_type, proficiency_key, stealth_skill_module, stealth_skill_type, stealth_skill_key, algorithm";
            Collections.addAll(values, d.description(), d.sourcePage(), ascii(d.kind().name()), d.armorClass(), d.strengthMinimum(), d.stealthDisadvantage() ? 1 : 0, d.hands()); reference(values, d.proficiency()); reference(values, d.stealthSkill()); values.add(ascii(d.algorithm()));
        } else if (row instanceof FinalHealth v) {
            var d = v.definition(); table = "health"; columns = "algorithm"; Collections.addAll(values, d.description(), d.sourcePage(), ascii(d.algorithm()));
        } else if (row instanceof FinalVitalEffect v) {
            var d = v.definition(); table = "vital_effect"; columns = "kind, dice_count, dice_sides, modifier, damage_type, algorithm";
            Collections.addAll(values, d.description(), d.sourcePage(), ascii(d.kind().name()), d.diceCount(), d.diceSides(), d.modifier(), d.damageType() == null ? null : ascii(d.damageType().name()), ascii(d.algorithm()));
        } else throw invalid();
        Collections.addAll(values, row.definition().authorNote(), row.enabled() ? 1 : 0);
        columns = "snapshot_id, rule_index, module_key, rule_key, display_name, description, source_page, " + columns + ", author_note, enabled";
        try (var statement = connection.prepareStatement("INSERT INTO s2_runtime_" + table + " (" + columns + ") VALUES (" + String.join(", ", Collections.nCopies(values.size(), "?")) + ")")) {
            statement.setQueryTimeout(5);
            for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                if (value instanceof byte[] bytes) statement.setBytes(i + 1, bytes);
                else if (value instanceof String text) statement.setString(i + 1, text);
                else if (value instanceof Integer n) statement.setInt(i + 1, n);
                else if (value == null) statement.setNull(i + 1, Types.NULL);
                else throw invalid();
            }
            require(statement.executeUpdate() == 1);
        }
    }
    static Map<Integer, FinalRule> read(Connection connection, Identity id) throws SQLException {
        Map<Integer, FinalRule> result = new TreeMap<>();
        for (String table : List.of("equipment_item", "health", "vital_effect")) {
            try (var statement = connection.prepareStatement("SELECT * FROM s2_runtime_" + table + " WHERE snapshot_id = ? ORDER BY rule_index LIMIT 257")) {
                statement.setQueryTimeout(5); statement.setMaxRows(257); statement.setBytes(1, id.snapshotBytes());
                try (var rows = statement.executeQuery()) {
                    int count = 0;
                    while (rows.next()) {
                        require(count++ < 256); Object storedId = rows.getObject("snapshot_id"); require(storedId instanceof byte[] && Arrays.equals((byte[]) storedId, id.snapshotBytes()));
                        RuleIdentity identity = new RuleIdentity(ascii(rows,"module_key",64), table.equals("equipment_item") ? "equipment.item" : "adventure." + table, ascii(rows,"rule_key",128));
                        String name = text(rows,"display_name",120), description = text(rows,"description",1000), note = nullableText(rows,"author_note",1000), algorithm = ascii(rows,"algorithm",64);
                        int page = number(rows,"source_page",0,1000), index = number(rows,"rule_index",0,255); boolean enabled = bool(rows,"enabled"); FinalRule rule;
                        try {
                            rule = switch (table) {
                                case "equipment_item" -> new FinalItem(new ItemDefinition(identity,name,description,page,EquipmentKind.valueOf(ascii(rows,"kind",16)),number(rows,"armor_class",0,20),number(rows,"strength_minimum",0,15),bool(rows,"stealth_disadvantage"),number(rows,"hands",0,2),reference(rows,"proficiency",true),reference(rows,"stealth_skill",true),algorithm,note),enabled);
                                case "health" -> new FinalHealth(new HealthDefinition(identity,name,description,page,algorithm,note),enabled);
                                default -> new FinalVitalEffect(new VitalEffectDefinition(identity,name,description,page,VitalEffectKind.valueOf(ascii(rows,"kind",16)),number(rows,"dice_count",0,20),number(rows,"dice_sides",0,12),signedModifier(rows,"modifier"),rows.getObject("damage_type") == null ? null : DamageType.valueOf(ascii(rows,"damage_type",16)),algorithm,note),enabled);
                            };
                        } catch (IllegalArgumentException | NullPointerException malformed) { throw new SQLException("S2_EQUIPMENT_SNAPSHOT_CORRUPT", malformed); }
                        require(result.putIfAbsent(index, rule) == null);
                    }
                }
            }
        }
        return result;
    }
    private static void reference(List<Object> values, RuleIdentity ref) { Collections.addAll(values,ref==null?null:ascii(ref.moduleKey()),ref==null?null:ascii(ref.ruleType()),ref==null?null:ascii(ref.ruleKey())); }
    private static RuleIdentity reference(ResultSet rows, String prefix, boolean nullable) throws SQLException {
        if (rows.getObject(prefix+"_module")==null) { require(nullable && rows.getObject(prefix+"_type")==null && rows.getObject(prefix+"_key")==null); return null; }
        return new RuleIdentity(ascii(rows,prefix+"_module",64),ascii(rows,prefix+"_type",32),ascii(rows,prefix+"_key",128));
    }
    private static int signedModifier(ResultSet rows, String column) throws SQLException { return number(rows,column,-1000,1000); }
    private static int number(ResultSet rows, String column, int min, int max) throws SQLException { Object raw=rows.getObject(column); require(raw instanceof Byte||raw instanceof Short||raw instanceof Integer||raw instanceof Long); long n=((Number)raw).longValue(); require(n>=min&&n<=max); return (int)n; }
    private static boolean bool(ResultSet rows, String column) throws SQLException { return number(rows,column,0,1)==1; }
    private static String ascii(ResultSet rows, String column, int max) throws SQLException { Object raw=rows.getObject(column); require(raw instanceof byte[]); byte[] bytes=(byte[])raw; require(bytes.length>0&&bytes.length<=max); for(byte b:bytes)require(b>=0x21&&b<=0x7e); return new String(bytes,StandardCharsets.US_ASCII); }
    private static String text(ResultSet rows, String column, int max) throws SQLException { Object raw=rows.getObject(column); require(raw instanceof String); String text=(String)raw; require(!text.isEmpty()&&text.codePointCount(0,text.length())<=max); return text; }
    private static String nullableText(ResultSet rows, String column, int max) throws SQLException { return rows.getObject(column)==null?null:text(rows,column,max); }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private static void require(boolean valid) throws SQLException { if(!valid)throw invalid(); }
    private static SQLException invalid() { return new SQLException("S2_EQUIPMENT_SNAPSHOT_CORRUPT"); }
}
