package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcModuleCatalogRepositoryTest {
    private static final String MODULE_KEY = "dnd5e2014_srd51_se_v1";
    private static final String COMPLETE_MODULE_KEY = "dnd5e2014_srd51_se";
    private static final String RELEASE_VERSION = "1";
    private static final Pattern FROM_TABLE = Pattern.compile("\\bFROM\\s+([a-z0-9_]+)");
    private static final List<String> EXPECTED_QUERY_TABLES = List.of(
            "module_release",
            "module_rule_constant",
            "module_field_definition",
            "module_class_definition",
            "module_proficiency_tier",
            "module_proficiency_bonus_band",
            "module_skill_definition",
            "module_save_definition",
            "module_item_template",
            "module_entity_template",
            "module_entity_template_value",
            "module_entity_template_class_level",
            "module_entity_template_proficiency",
            "module_check_definition",
            "module_roll_mode",
            "module_event_template",
            "module_event_check",
            "module_event_effect",
            "module_effect_definition",
            "module_effect_parameter",
            "module_map_definition",
            "module_map_node",
            "module_map_connection");

    @Test
    void loadsEverySectionInOneReadOnlySnapshotAndRestoresConnection() throws Exception {
        JdbcFixture fixture = JdbcFixture.withRelease();

        Optional<ModuleCatalog> found = new JdbcModuleCatalogRepository(fixture.dataSource())
                .findByIdentity(MODULE_KEY, RELEASE_VERSION);

        assertTrue(found.isPresent());
        ModuleCatalog catalog = found.orElseThrow();
        assertEquals(MODULE_KEY, catalog.release().moduleKey());
        assertEquals("DRAFT", catalog.release().releaseStatus());
        assertEquals(1, catalog.ruleConstants().size());
        ModuleCatalog.IntegerValue integerValue = assertInstanceOf(
                ModuleCatalog.IntegerValue.class,
                catalog.ruleConstants().get(0).value());
        assertEquals(20L, integerValue.value());
        assertTrue(catalog.fieldDefinitions().isEmpty());
        assertTrue(catalog.mapConnections().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> catalog.ruleConstants().add(null));

        assertEquals(23, fixture.queryCount);
        assertEquals(23, fixture.statementCloseCount);
        assertEquals(23, fixture.resultCloseCount);
        assertTrue(fixture.preparedSql.stream().allMatch(JdbcFixture::isReadOnlySql));
        assertEquals(
                EXPECTED_QUERY_TABLES,
                fixture.preparedSql.stream()
                        .map(JdbcModuleCatalogRepositoryTest::fromTable)
                        .toList());
        assertTrue(fixture.readOnlyWasEnabled);
        assertTrue(fixture.repeatableReadWasEnabled);
        assertTrue(fixture.autoCommitWasDisabled);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.connectionClosed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.transactionIsolation);
    }

    @Test
    void missingReleaseReturnsEmptyWithoutReadingDefinitionTables() throws Exception {
        JdbcFixture fixture = JdbcFixture.withoutRelease();

        Optional<ModuleCatalog> found = new JdbcModuleCatalogRepository(fixture.dataSource())
                .findByIdentity(MODULE_KEY, RELEASE_VERSION);

        assertTrue(found.isEmpty());
        assertEquals(1, fixture.queryCount);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertTrue(fixture.connectionClosed);
    }

    @Test
    void canonicalV2LoadsOnlyTheThreeTypedDirectoryPartitions() throws Exception {
        JdbcFixture fixture = JdbcFixture.withV2Release();

        ModuleCatalog catalog = new JdbcModuleCatalogRepository(fixture.dataSource())
                .findByIdentity(COMPLETE_MODULE_KEY, RELEASE_VERSION).orElseThrow();

        assertEquals(2, catalog.release().canonicalFormatVersion());
        assertTrue(catalog.ruleConstants().isEmpty());
        assertTrue(catalog.catalogDefinitions().isEmpty());
        assertTrue(catalog.catalogAttributes().isEmpty());
        assertTrue(catalog.catalogRelations().isEmpty());
        assertEquals(List.of(
                        "module_release",
                        "module_catalog_definition_v2",
                        "module_catalog_attribute_v2",
                        "module_catalog_relation_v2"),
                fixture.preparedSql.stream()
                        .map(JdbcModuleCatalogRepositoryTest::fromTable)
                        .toList());
        assertEquals(4, fixture.queryCount);
        assertTrue(fixture.committed);
        assertFalse(fixture.rolledBack);
    }

    @Test
    void callerOwnedLookupUsesExistingTransactionWithoutCommittingOrClosingIt() throws Exception {
        JdbcFixture fixture = JdbcFixture.withoutRelease();
        fixture.autoCommit = false;

        Optional<ModuleCatalog> found = new JdbcModuleCatalogRepository(fixture.dataSource())
                .findByIdentity(fixture.connection(), MODULE_KEY, RELEASE_VERSION);

        assertTrue(found.isEmpty());
        assertEquals(1, fixture.queryCount);
        assertFalse(fixture.committed);
        assertFalse(fixture.rolledBack);
        assertFalse(fixture.connectionClosed);
    }

    @Test
    void queryFailureRollsBackRestoresStateAndClosesOwnedResources() {
        JdbcFixture fixture = JdbcFixture.withRelease();
        fixture.failAtQuery = 2;

        assertThrows(
                SQLException.class,
                () -> new JdbcModuleCatalogRepository(fixture.dataSource())
                        .findByIdentity(MODULE_KEY, RELEASE_VERSION));

        assertTrue(fixture.rolledBack);
        assertFalse(fixture.committed);
        assertEquals(2, fixture.statementCloseCount);
        assertEquals(1, fixture.resultCloseCount);
        assertTrue(fixture.connectionClosed);
        assertTrue(fixture.autoCommit);
        assertFalse(fixture.readOnly);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, fixture.transactionIsolation);
    }

    @Test
    void invalidIdentityIsRejectedBeforeBorrowingAConnection() {
        JdbcFixture fixture = JdbcFixture.withRelease();

        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcModuleCatalogRepository(fixture.dataSource())
                        .findByIdentity("INVALID", RELEASE_VERSION));

        assertFalse(fixture.connectionRequested);
    }

    private static String fromTable(String sql) {
        Matcher matcher = FROM_TABLE.matcher(sql);
        if (!matcher.find()) {
            throw new AssertionError("SELECT does not name one source table");
        }
        return matcher.group(1);
    }

    /** JDBC proxies provide deterministic transaction and closure evidence without a live DB. */
    private static final class JdbcFixture {
        private static final Map<String, Object> CONSTANT_ROW = Map.of(
                "constant_key", "character.total_level.maximum",
                "value_type", "INTEGER",
                "integer_value", 20L);

        private final boolean releaseAvailable;
        private final int canonicalFormatVersion;
        private final String moduleKey;
        private final List<String> preparedSql = new ArrayList<>();
        private boolean connectionRequested;
        private boolean connectionClosed;
        private boolean readOnly;
        private boolean autoCommit = true;
        private int transactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
        private boolean readOnlyWasEnabled;
        private boolean autoCommitWasDisabled;
        private boolean repeatableReadWasEnabled;
        private boolean committed;
        private boolean rolledBack;
        private int queryCount;
        private int statementCloseCount;
        private int resultCloseCount;
        private int failAtQuery = -1;

        private JdbcFixture(
                boolean releaseAvailable, int canonicalFormatVersion, String moduleKey) {
            this.releaseAvailable = releaseAvailable;
            this.canonicalFormatVersion = canonicalFormatVersion;
            this.moduleKey = moduleKey;
        }

        static JdbcFixture withRelease() {
            return new JdbcFixture(true, 1, MODULE_KEY);
        }

        static JdbcFixture withV2Release() {
            return new JdbcFixture(true, 2, COMPLETE_MODULE_KEY);
        }

        static JdbcFixture withoutRelease() {
            return new JdbcFixture(false, 1, MODULE_KEY);
        }

        DataSource dataSource() {
            Connection connection = connection();
            return proxy(DataSource.class, (proxy, method, arguments) -> {
                if ("getConnection".equals(method.getName())) {
                    connectionRequested = true;
                    return connection;
                }
                return defaultValue(method.getReturnType());
            });
        }

        Connection connection() {
            return proxy(Connection.class, this::handleConnection);
        }

        private Object handleConnection(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "prepareStatement" -> {
                    String sql = (String) arguments[0];
                    preparedSql.add(sql);
                    yield statement(sql);
                }
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> {
                    autoCommit = (boolean) arguments[0];
                    autoCommitWasDisabled |= !autoCommit;
                    yield null;
                }
                case "isReadOnly" -> readOnly;
                case "setReadOnly" -> {
                    readOnly = (boolean) arguments[0];
                    readOnlyWasEnabled |= readOnly;
                    yield null;
                }
                case "getTransactionIsolation" -> transactionIsolation;
                case "setTransactionIsolation" -> {
                    transactionIsolation = (int) arguments[0];
                    repeatableReadWasEnabled |=
                            transactionIsolation == Connection.TRANSACTION_REPEATABLE_READ;
                    yield null;
                }
                case "commit" -> {
                    committed = true;
                    yield null;
                }
                case "rollback" -> {
                    rolledBack = true;
                    yield null;
                }
                case "close" -> {
                    connectionClosed = true;
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            };
        }

        private PreparedStatement statement(String sql) {
            return proxy(PreparedStatement.class, (proxy, method, arguments) -> {
                if ("executeQuery".equals(method.getName())) {
                    queryCount++;
                    if (queryCount == failAtQuery) {
                        throw new SQLException("synthetic query failure");
                    }
                    return resultSet(rowsFor(sql));
                }
                if ("close".equals(method.getName())) {
                    statementCloseCount++;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private List<Map<String, Object>> rowsFor(String sql) {
            if (sql.contains("FROM module_release")) {
                return releaseAvailable ? List.of(Map.of(
                        "id", 7L,
                        "module_key", moduleKey,
                        "release_version", RELEASE_VERSION,
                        "canonical_format_version", canonicalFormatVersion,
                        "hash_algorithm", "SHA-256",
                        "release_status", "DRAFT")) : List.of();
            }
            if (sql.contains("FROM module_rule_constant")) {
                return List.of(CONSTANT_ROW);
            }
            return List.of();
        }

        private ResultSet resultSet(List<Map<String, Object>> rows) {
            final int[] rowIndex = {-1};
            final boolean[] wasNull = {false};
            return proxy(ResultSet.class, (proxy, method, arguments) -> switch (method.getName()) {
                case "next" -> ++rowIndex[0] < rows.size();
                case "getString" -> read(rows, rowIndex[0], arguments[0], String.class, wasNull);
                case "getLong" -> number(rows, rowIndex[0], arguments[0], wasNull).longValue();
                case "getInt" -> number(rows, rowIndex[0], arguments[0], wasNull).intValue();
                case "getBoolean" -> booleanValue(rows, rowIndex[0], arguments[0], wasNull);
                case "getBigDecimal" ->
                        read(rows, rowIndex[0], arguments[0], BigDecimal.class, wasNull);
                case "wasNull" -> wasNull[0];
                case "close" -> {
                    resultCloseCount++;
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
        }

        private static Number number(
                List<Map<String, Object>> rows,
                int rowIndex,
                Object column,
                boolean[] wasNull) {
            Number value = read(rows, rowIndex, column, Number.class, wasNull);
            return value == null ? 0 : value;
        }

        private static boolean booleanValue(
                List<Map<String, Object>> rows,
                int rowIndex,
                Object column,
                boolean[] wasNull) {
            Object value = value(rows, rowIndex, column, wasNull);
            if (value == null) {
                return false;
            }
            return value instanceof Boolean bool ? bool : ((Number) value).intValue() != 0;
        }

        private static <T> T read(
                List<Map<String, Object>> rows,
                int rowIndex,
                Object column,
                Class<T> type,
                boolean[] wasNull) {
            Object value = value(rows, rowIndex, column, wasNull);
            return value == null ? null : type.cast(value);
        }

        private static Object value(
                List<Map<String, Object>> rows,
                int rowIndex,
                Object column,
                boolean[] wasNull) {
            Object value = rows.get(rowIndex).get((String) column);
            wasNull[0] = value == null;
            return value;
        }

        private static boolean isReadOnlySql(String sql) {
            String normalized = sql.stripLeading().toUpperCase(Locale.ROOT);
            return normalized.startsWith("SELECT ")
                    && !normalized.contains(" INSERT ")
                    && !normalized.contains(" UPDATE ")
                    && !normalized.contains(" DELETE ")
                    && !normalized.contains(" MERGE ");
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == byte.class || type == short.class || type == int.class || type == long.class) {
                return 0;
            }
            if (type == float.class || type == double.class) {
                return 0.0;
            }
            if (type == char.class) {
                return '\0';
            }
            return null;
        }
    }
}
