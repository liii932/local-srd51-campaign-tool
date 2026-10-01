package com.dndtool.persistence;

import com.dndtool.module.CharacterCatalogPartition;
import com.dndtool.module.ToolPartition;
import com.dndtool.persistence.JdbcRuntimeLanguageSnapshotRepository.Identity;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/** Writes a fresh two-domain PARTITION in one caller-owned transaction. No source capability. */
public final class JdbcRuntimeCharacterCatalogRepository {
    private final JdbcRuntimeLanguageSnapshotRepository languages = new JdbcRuntimeLanguageSnapshotRepository();
    private static final String INSERT = """
            INSERT INTO runtime_rule_tool (snapshot_id, tool_key, display_name,
                description, category, source_page, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String READ = """
            SELECT snapshot_id, tool_key, display_name, description, category, source_page, sort_order
            FROM runtime_rule_tool WHERE snapshot_id = ? ORDER BY tool_key
            """;

    /** Any failure requires the caller to roll back the entire operation, including registration. */
    public Snapshot append(Connection connection, Identity identity, CharacterCatalogPartition partition) throws SQLException {
        Objects.requireNonNull(partition);
        // Complete validation precedes the first INSERT, including both domain constructors.
        var expected = new CharacterCatalogPartition(partition.languages(), new ToolPartition(partition.tools().tools()));
        languages.append(connection, identity, expected.languages());
        for (var tool : expected.tools().tools()) {
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                statement.setQueryTimeout(5);
                statement.setBytes(1, identity.snapshotBytes());
                statement.setBytes(2, ascii(tool.toolKey()));
                statement.setString(3, tool.displayName());
                statement.setString(4, tool.description());
                statement.setBytes(5, ascii(tool.category().name()));
                statement.setInt(6, tool.sourcePage());
                statement.setInt(7, tool.sortOrder());
                require(statement.executeUpdate() == 1);
            }
        }
        Snapshot actual = read(connection, identity);
        require(expected.equals(actual.partition()));
        return actual;
    }

    public Snapshot read(Connection connection, Identity identity) throws SQLException {
        // The language reader verifies transaction, schema, UUID pair, registration and immutable head.
        var language = languages.read(connection, identity).partition();
        var tools = new ArrayList<ToolPartition.Tool>(37);
        try (PreparedStatement statement = connection.prepareStatement(READ)) {
            statement.setQueryTimeout(5);
            statement.setMaxRows(38);
            statement.setBytes(1, identity.snapshotBytes());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    require(tools.size() < 37);
                    require(result.getObject("snapshot_id") instanceof byte[] bytes
                            && Arrays.equals(identity.snapshotBytes(), bytes));
                    try {
                        tools.add(new ToolPartition.Tool(binary(result,"tool_key",128),
                                text(result,"display_name",120),text(result,"description",1000),
                                ToolPartition.Category.valueOf(binary(result,"category",18)),
                                integer(result,"source_page"),integer(result,"sort_order")));
                    } catch (IllegalArgumentException failure) { throw invalid(); }
                }
            }
        }
        try { return new Snapshot(identity,new CharacterCatalogPartition(language,new ToolPartition(tools))); }
        catch (IllegalArgumentException failure) { throw invalid(); }
    }

    private static String text(ResultSet row,String column,int maximum) throws SQLException {
        Object value=row.getObject(column);
        require(value instanceof String text && text.length()<=maximum*2);
        return (String)value; // Tool checks Unicode scalars, NFC, controls and code points.
    }
    private static String binary(ResultSet row,String column,int maximum) throws SQLException {
        Object value=row.getObject(column);require(value instanceof byte[]);
        byte[] bytes=(byte[])value;require(bytes.length>0 && bytes.length<=maximum);
        for(byte b:bytes)require(b>=0x21 && b<=0x7e);
        return new String(bytes,StandardCharsets.US_ASCII);
    }
    private static int integer(ResultSet row,String column) throws SQLException {
        Object value=row.getObject(column);
        require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long);
        long number=((Number)value).longValue();require(number>=Integer.MIN_VALUE && number<=Integer.MAX_VALUE);
        return (int)number;
    }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private static void require(boolean valid) throws SQLException { if(!valid)throw invalid(); }
    private static SQLException invalid() { return new SQLException("Invalid runtime character catalog partition"); }
    public record Snapshot(Identity identity, CharacterCatalogPartition partition) {}
}
