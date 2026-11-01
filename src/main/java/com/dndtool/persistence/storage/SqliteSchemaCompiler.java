package com.dndtool.persistence.storage;

import static com.dndtool.persistence.storage.StorageSchemaCatalog.*;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure compiler for the closed sqlite-strict-1 declaration language. */
public final class SqliteSchemaCompiler {
    public List<String> statements(StoragePlan plan) { return plan.createStatements(); }

    static String tableSql(Table table) {
        List<String> entries = new ArrayList<>();
        for (Column column : table.columns()) {
            String sql = quote(column.name()) + " " + sqlType(column);
            if (column.type().equals("text")) sql += " COLLATE BINARY";
            if (!column.nullable()) sql += " NOT NULL";
            if (column.hasDefault()) sql += " DEFAULT " + literal(column.type(), column.defaultValue());
            String name = quote(column.name());
            switch (column.type()) {
                case "int64" -> sql += " CHECK (" + name + " BETWEEN " + column.minimum() + " AND " + column.maximum() + ")";
                case "boolean" -> sql += " CHECK (" + name + " IN (0,1))";
                case "uuid" -> sql += " CHECK (length(" + name + ") = 16)";
                case "digest256" -> sql += " CHECK (length(" + name + ") = 32)";
                case "bytes" -> sql += " CHECK (length(" + name + ") <= " + column.maxLength() + ")";
                case "text" -> sql += " CHECK (length(" + name + ") BETWEEN " + column.minLength() + " AND " + column.maxLength() + ")";
                default -> throw new IllegalArgumentException("Unsupported column type");
            }
            entries.add(sql);
        }
        entries.add("PRIMARY KEY (" + columns(table.primaryKey()) + ")");
        for (Key key : table.uniqueKeys()) entries.add("CONSTRAINT " + quote(key.name()) + " UNIQUE (" + columns(key.columns()) + ")");
        for (ForeignKey fk : table.foreignKeys()) {
            entries.add("CONSTRAINT " + quote(fk.name()) + " FOREIGN KEY (" + columns(fk.columns()) + ") REFERENCES "
                    + quote(fk.target()) + " (" + columns(fk.targetColumns()) + ") ON DELETE NO ACTION ON UPDATE NO ACTION"
                    + (fk.deferred() ? " DEFERRABLE INITIALLY DEFERRED" : ""));
        }
        for (Check check : table.checks()) entries.add("CONSTRAINT " + quote(check.name()) + " CHECK ((" + predicate(check.expression(), table.columnMap()) + ") IS TRUE)");
        return "CREATE TABLE " + quote(table.name()) + " (" + String.join(", ", entries) + ") STRICT, WITHOUT ROWID";
    }

    static String indexSql(Table table, Index index) {
        return "CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX " + quote(index.name())
                + " ON " + quote(table.name()) + " (" + columns(index.columns()) + ")";
    }

    static String sqlType(Column column) {
        return switch (column.type()) {
            case "int64", "boolean" -> "INTEGER";
            case "text" -> "TEXT";
            case "uuid", "digest256", "bytes" -> "BLOB";
            default -> throw new IllegalArgumentException("Unsupported column type");
        };
    }

    static String quote(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }
    static String columns(List<String> columns) { return String.join(", ", columns.stream().map(SqliteSchemaCompiler::quote).toList()); }

    private static String predicate(Map<String, Object> expression, Map<String, Column> columns) {
        String op = (String) expression.get("op");
        return switch (op) {
            case "and", "or" -> "(" + String.join(op.equals("and") ? " AND " : " OR ", array(expression.get("args"), "predicate").stream()
                    .map(child -> "(" + predicate(object(child, "predicate"), columns) + ")").toList()) + ")";
            case "not" -> "NOT (" + predicate(object(expression.get("arg"), "predicate"), columns) + ")";
            case "isNull", "notNull" -> quote((String) expression.get("column")) + (op.equals("isNull") ? " IS NULL" : " IS NOT NULL");
            case "in" -> {
                String name = (String) expression.get("column");
                yield quote(name) + " IN (" + String.join(", ", array(expression.get("values"), "predicate").stream()
                        .map(value -> literal(columns.get(name).type(), value)).toList()) + ")";
            }
            case "eq", "ne", "lt", "le", "gt", "ge" -> {
                String operator = switch (op) { case "eq" -> "="; case "ne" -> "<>"; case "lt" -> "<"; case "le" -> "<="; case "gt" -> ">"; default -> ">="; };
                yield operand(object(expression.get("left"), "predicate")) + " " + operator + " " + operand(object(expression.get("right"), "predicate"));
            }
            default -> throw new IllegalArgumentException("Unsupported predicate");
        };
    }

    private static String operand(Map<String, Object> operand) {
        return operand.containsKey("column") ? quote((String) operand.get("column")) : literal((String) operand.get("type"), operand.get("literal"));
    }

    static String literal(String type, Object value) {
        if (value == null) return "NULL";
        return switch (type) {
            case "int64" -> Long.toString(decimal(value, "literal"));
            case "boolean" -> (Boolean) value ? "1" : "0";
            case "text" -> "'" + ((String) value).replace("'", "''") + "'";
            case "uuid", "digest256", "bytes" -> "X'" + HexFormat.of().formatHex(binary(type, (String) value)) + "'";
            default -> throw new IllegalArgumentException("Unsupported literal");
        };
    }

    static byte[] binary(String type, String value) {
        return switch (type) {
            case "uuid" -> {
                UUID uuid = UUID.fromString(value);
                yield ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
            }
            case "digest256" -> HexFormat.of().parseHex(value);
            case "bytes" -> Base64.getDecoder().decode(value);
            default -> throw new IllegalArgumentException("Unsupported binary type");
        };
    }

    static void bind(PreparedStatement statement, int index, Column column, Object value) throws SQLException {
        column.validate(value, "seed." + column.name());
        if (value == null) statement.setNull(index, switch (column.type()) { case "text" -> Types.VARCHAR; case "int64", "boolean" -> Types.BIGINT; default -> Types.BLOB; });
        else switch (column.type()) {
            case "int64" -> statement.setLong(index, decimal(value, "seed"));
            case "boolean" -> statement.setInt(index, (Boolean) value ? 1 : 0);
            case "text" -> statement.setString(index, (String) value);
            default -> statement.setBytes(index, binary(column.type(), (String) value));
        }
    }
}
