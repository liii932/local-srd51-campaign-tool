package com.dndtool.persistence.storage;

import static com.dndtool.persistence.storage.StorageSchemaCatalogTest.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSchemaTest {
    @TempDir Path temporary;

    @Test
    void realDriverCreatesSixScalarTypesAndReadsBackFullStructureAfterReopen() throws Exception {
        StoragePlan plan = read(SCHEMA).plan(Set.of("characters"));
        String url = "jdbc:sqlite:" + temporary.resolve("campaign.sqlite");
        try (Connection connection = open(url)) {
            execute(connection, "BEGIN IMMEDIATE");
            plan.create(connection);
            populate(connection);
            plan.verify(connection);
            execute(connection, "COMMIT");
        }
        try (Connection connection = open(url)) {
            plan.verify(connection);
            assertEquals("3.50.3", scalar(connection, "SELECT sqlite_version()"));
            assertEquals("O'Brien", scalar(connection, "SELECT name FROM characters_character"));
            assertEquals("0", scalar(connection, "SELECT archived FROM characters_character"));
            assertEquals("16", scalar(connection, "SELECT length(id) FROM characters_character"));
            assertEquals("32", scalar(connection, "SELECT length(digest) FROM characters_character"));
            assertEquals("00FF01", scalar(connection, "SELECT hex(payload) FROM characters_character"));
            assertEquals("2", scalar(connection, "SELECT count(*) FROM core_kind"));
            assertThrows(SQLException.class, () -> plan.create(connection));
            assertEquals("2", scalar(connection, "SELECT count(*) FROM core_kind"));
        }
    }

    @Test
    void constraintsAndForeignKeysActuallyRejectMalformedData() throws Exception {
        StoragePlan plan = read(SCHEMA).plan(Set.of("characters"));
        try (Connection connection = open("jdbc:sqlite::memory:")) {
            execute(connection, "BEGIN IMMEDIATE");
            plan.create(connection);
            populate(connection);
            for (String update : List.of(
                    "UPDATE characters_character SET hp_current=-1",
                    "UPDATE characters_character SET hp_current=11",
                    "UPDATE characters_character SET hp_current=1.5",
                    "UPDATE characters_character SET archived=2",
                    "UPDATE characters_character SET id=zeroblob(15)",
                    "UPDATE characters_character SET digest=zeroblob(31)",
                    "UPDATE characters_character SET payload=zeroblob(9)",
                    "UPDATE characters_character SET kind='XX'",
                    "UPDATE characters_character SET name=''")) {
                assertThrows(SQLException.class, () -> execute(connection, update), update);
            }
            plan.verify(connection);
            execute(connection, "ROLLBACK");
            assertEquals("0", scalar(connection, "SELECT count(*) FROM sqlite_schema"));
        }
    }

    @Test
    void everyDdlIndexAndSeedFaultRollsBackTheActualDatabase() throws Exception {
        StoragePlan plan = read(SCHEMA).plan(Set.of("characters"));
        int mutatingStatements = plan.createStatements().size() + 2;
        for (int fault = 1; fault <= mutatingStatements; fault++) {
            try (Connection real = open("jdbc:sqlite::memory:")) {
                execute(real, "BEGIN IMMEDIATE");
                Connection injecting = failMutation(real, fault);
                assertThrows(SQLException.class, () -> plan.create(injecting));
                execute(real, "ROLLBACK");
                assertEquals("0", scalar(real, "SELECT count(*) FROM sqlite_schema"), "fault " + fault);
            }
        }
    }

    @Test
    void verifierRejectsAddedChangedAndMissingObjectsWithoutRepair() throws Exception {
        StoragePlan plan = read(SCHEMA).plan(Set.of("characters"));
        for (String mutation : List.of(
                "CREATE TABLE extra (id INTEGER PRIMARY KEY) STRICT",
                "CREATE VIEW extra_view AS SELECT kind FROM core_kind",
                "CREATE TRIGGER extra_trigger AFTER INSERT ON core_kind BEGIN SELECT 1; END",
                "ALTER TABLE core_kind ADD COLUMN extra TEXT",
                "DROP TABLE core_campaign",
                "DROP INDEX idx_characters_kind")) {
            try (Connection connection = open("jdbc:sqlite::memory:")) {
                execute(connection, "BEGIN IMMEDIATE");
                plan.create(connection);
                execute(connection, mutation);
                SQLException failure = assertThrows(SQLException.class, () -> plan.verify(connection), mutation);
                assertEquals("DND_SCHEMA", failure.getSQLState());
                assertThrows(SQLException.class, () -> plan.create(connection));
                execute(connection, "ROLLBACK");
            }
        }
    }

    @Test
    void verifierRejectsChangedMissingAndExtraSeedsEvenWhenCountsMatch() throws Exception {
        StoragePlan plan = read(SCHEMA).plan(Set.of("characters"));
        for (String mutation : List.of("UPDATE core_kind SET kind='XYZ' WHERE kind='NPC'", "DELETE FROM core_kind WHERE kind='NPC'", "INSERT INTO core_kind VALUES ('XX')")) {
            try (Connection connection = open("jdbc:sqlite::memory:")) {
                execute(connection, "BEGIN IMMEDIATE");
                plan.create(connection);
                execute(connection, mutation);
                assertEquals("STORAGE_STATIC_SEED", assertThrows(SQLException.class, () -> plan.verify(connection)).getMessage());
                execute(connection, "ROLLBACK");
            }
        }
    }

    @Test
    void verifierRejectsDifferentCheckDefaultIndexAndAdditionalConstraintSql() throws Exception {
        StoragePlan expected = read(SCHEMA).plan(Set.of("characters"));
        JsonObject additionalConstraint = schema();
        character(additionalConstraint).getAsJsonArray("checks").add(JsonParser.parseString(
                "{\"name\":\"ck_characters_name\",\"expression\":{\"op\":\"notNull\",\"column\":\"name\"}}"));
        for (String variant : List.of(
                SCHEMA.replace("\"op\":\"le\"", "\"op\":\"lt\""),
                SCHEMA.replace("O'Brien", "Other"),
                SCHEMA.replace("\"unique\":false", "\"unique\":true"),
                additionalConstraint.toString())) {
            try (Connection connection = open("jdbc:sqlite::memory:")) {
                execute(connection, "BEGIN IMMEDIATE");
                read(variant).plan(Set.of("characters")).create(connection);
                String sql = scalar(connection, "SELECT sql FROM sqlite_schema WHERE name='characters_character'");
                assertEquals("STORAGE_OBJECT_DEFINITION", assertThrows(SQLException.class, () -> expected.verify(connection)).getMessage());
                assertEquals(sql, scalar(connection, "SELECT sql FROM sqlite_schema WHERE name='characters_character'"));
                execute(connection, "ROLLBACK");
            }
        }
    }

    @Test
    void independentForeignKeyCheckDetectsOrphanThatIntegrityCheckDoesNotCover() throws Exception {
        StoragePlan plan = read(SCHEMA).plan(Set.of("characters"));
        try (Connection connection = open("jdbc:sqlite::memory:")) {
            execute(connection, "BEGIN IMMEDIATE"); plan.create(connection); populate(connection); execute(connection, "COMMIT");
            execute(connection, "PRAGMA foreign_keys=OFF");
            execute(connection, "DELETE FROM core_campaign");
            execute(connection, "PRAGMA foreign_keys=ON");
            assertEquals("ok", scalar(connection, "PRAGMA integrity_check"));
            assertEquals("STORAGE_FOREIGN_KEY_DATA", assertThrows(SQLException.class, () -> plan.verify(connection)).getMessage());
        }
    }

    @Test
    void requiresForeignKeysAndRejectsTemporaryOrAttachedObjects() throws Exception {
        StoragePlan plan = read(SCHEMA).plan(Set.of("characters"));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            execute(connection, "PRAGMA foreign_keys=OFF");
            assertEquals("STORAGE_FOREIGN_KEYS_DISABLED", assertThrows(SQLException.class, () -> plan.create(connection)).getMessage());
            assertEquals("0", scalar(connection, "SELECT count(*) FROM sqlite_schema"));
        }
        for (String mutation : List.of("CREATE TEMP TABLE core_kind (kind TEXT)", "ATTACH ':memory:' AS foreign_database")) {
            try (Connection connection = open("jdbc:sqlite::memory:")) {
                execute(connection, "BEGIN IMMEDIATE"); plan.create(connection); execute(connection, "COMMIT");
                execute(connection, mutation);
                assertThrows(SQLException.class, () -> plan.verify(connection));
            }
        }
    }

    @Test
    void declaredDeferredCyclesAreSupportedButImmediateCycleIsRejectedBeforeDdl() throws Exception {
        String cycle = """
            {"declarationVersion":1,"applicationModel":"campaign-spaces-3","storageModel":"sqlite-campaign-3","dialect":"sqlite-strict-1","modules":[
              {"id":"core","dependsOn":[],"tables":[
                {"name":"core_b","columns":[{"name":"id","type":"uuid","nullable":false}],"primaryKey":["id"],"uniqueKeys":[],"foreignKeys":[{"name":"fk_core_b_a","columns":["id"],"references":{"table":"core_a","columns":["id"]},"onDelete":"NO_ACTION","onUpdate":"NO_ACTION","deferred":true}],"checks":[],"indexes":[]},
                {"name":"core_a","columns":[{"name":"id","type":"uuid","nullable":false}],"primaryKey":["id"],"uniqueKeys":[],"foreignKeys":[{"name":"fk_core_a_b","columns":["id"],"references":{"table":"core_b","columns":["id"]},"onDelete":"NO_ACTION","onUpdate":"NO_ACTION","deferred":true}],"checks":[],"indexes":[]}
              ],"seeds":[{"table":"core_a","rows":[{"id":"d158ee4d-96d5-4d6b-9cf9-c83514da69b8"}]},{"table":"core_b","rows":[{"id":"d158ee4d-96d5-4d6b-9cf9-c83514da69b8"}]}]}
            ]}
            """;
        StoragePlan plan = read(cycle).plan(Set.of("core"));
        assertEquals(List.of("core_a", "core_b"), plan.tableNames());
        try (Connection connection = open("jdbc:sqlite::memory:")) {
            execute(connection, "BEGIN IMMEDIATE"); plan.create(connection); plan.verify(connection); execute(connection, "COMMIT");
        }
        assertCode(cycle.replaceFirst("\"deferred\":true", "\"deferred\":false"), "IMMEDIATE_FK_CYCLE");
    }

    @Test
    void predicateCompilationRejectsSqlNullAndAllowsExplicitNullableBranches() throws Exception {
        JsonObject root = schema();
        JsonObject check = character(root).getAsJsonArray("checks").get(0).getAsJsonObject();
        check.add("expression", JsonParser.parseString("{\"op\":\"eq\",\"left\":{\"column\":\"digest\"},\"right\":{\"literal\":\"" + "a".repeat(64) + "\",\"type\":\"digest256\"}}"));
        StoragePlan plan = read(root.toString()).plan(Set.of("characters"));
        try (Connection connection = open("jdbc:sqlite::memory:")) {
            execute(connection, "BEGIN IMMEDIATE"); plan.create(connection); populate(connection);
            assertThrows(SQLException.class, () -> execute(connection, "UPDATE characters_character SET digest=NULL"));
            execute(connection, "ROLLBACK");
        }
        JsonObject expression = check.getAsJsonObject("expression");
        check.add("expression", JsonParser.parseString("{\"op\":\"or\",\"args\":[{\"op\":\"isNull\",\"column\":\"digest\"}," + expression + "]}"));
        plan = read(root.toString()).plan(Set.of("characters"));
        try (Connection connection = open("jdbc:sqlite::memory:")) {
            execute(connection, "BEGIN IMMEDIATE"); plan.create(connection); populate(connection);
            execute(connection, "UPDATE characters_character SET digest=NULL");
            plan.verify(connection); execute(connection, "ROLLBACK");
        }
    }

    @Test
    void bundledProductionClosureCompilesAndVerifiesUsingActualDriver() throws Exception {
        StoragePlan plan = StorageSchemaCatalog.bundled().plan(Set.of("runtime"));
        assertTrue(plan.moduleIds().containsAll(Set.of("core", "rules-runtime", "characters-runtime", "history-runtime", "runtime")));
        try (Connection connection = open("jdbc:sqlite::memory:")) {
            execute(connection, "BEGIN IMMEDIATE"); plan.create(connection); plan.verify(connection); execute(connection, "ROLLBACK");
            assertEquals("0", scalar(connection, "SELECT count(*) FROM sqlite_schema"));
        }
    }

    static Connection open(String url) throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        execute(connection, "PRAGMA foreign_keys=ON");
        return connection;
    }

    static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next()); String value = rows.getString(1); assertFalse(rows.next()); return value;
        }
    }

    private static void populate(Connection connection) throws SQLException {
        byte[] key = SqliteSchemaCompiler.binary("uuid", UUID);
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO core_campaign VALUES (?, ?)")) {
            insert.setBytes(1, key); insert.setString(2, "冒险😀"); insert.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO characters_character (id,campaign_id,kind,hp_max,hp_current,digest,payload) VALUES (?,?,'PC',10,7,?,?)")) {
            insert.setBytes(1, key); insert.setBytes(2, key); insert.setBytes(3, HexFormat.of().parseHex("a".repeat(64))); insert.setBytes(4, new byte[]{0, (byte) 255, 1}); insert.executeUpdate();
        }
    }

    private static Connection failMutation(Connection real, int at) {
        int[] count = {0};
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            Object value = invoke(real, method, args);
            if (method.getName().equals("createStatement") || method.getName().equals("prepareStatement")) {
                Class<?> type = value instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (statement, operation, parameters) -> {
                    boolean mutation = operation.getName().equals("executeUpdate") || (operation.getName().equals("execute") && parameters != null && parameters.length > 0
                            && parameters[0] instanceof String sql && sql.startsWith("CREATE "));
                    if (mutation && ++count[0] == at) throw new SQLException("injected mutation failure");
                    return invoke(value, operation, parameters);
                });
            }
            return value;
        });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] arguments) throws Throwable {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
