package com.dndtool.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndtool.service.ModuleIntegrityService;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.naming.NamingException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Verifies readiness ordering and stable failure-state mapping without JNDI or MySQL. */
final class DatabaseDiagnosticsTest {
    private static final DataSource DATA_SOURCE = proxyDataSource();

    @Test
    void readyRequiresSchemaThenModuleIntegrity() {
        AtomicBoolean schemaCalled = new AtomicBoolean();
        AtomicBoolean moduleCalled = new AtomicBoolean();
        DatabaseDiagnostics diagnostics = new DatabaseDiagnostics(
                () -> DATA_SOURCE,
                (dataSource, expectations) -> schemaCalled.set(true),
                dataSource -> {
                    moduleCalled.set(true);
                    return ModuleIntegrityService.Status.READY;
                });

        DatabaseSchemaStatus result = diagnostics.run();

        assertEquals(DatabaseSchemaStatus.State.READY, result.state());
        assertEquals(SchemaMigrations.V017_VERSION, result.schemaVersion());
        assertEquals(SchemaMigrations.V017_SCRIPT_NAME, result.scriptName());
        assertEquals(SchemaMigrations.V017_APPROVED_SHA256, result.scriptSha256());
        assertEquals(true, schemaCalled.get());
        assertEquals(true, moduleCalled.get());
    }

    @Test
    void moduleMismatchHasDedicatedFiniteState() {
        DatabaseDiagnostics diagnostics = new DatabaseDiagnostics(
                () -> DATA_SOURCE,
                (dataSource, expectations) -> { },
                dataSource -> ModuleIntegrityService.Status.MODULE_HASH_MISMATCH);

        assertEquals(DatabaseSchemaStatus.State.MODULE_HASH_MISMATCH,
                diagnostics.run().state());
    }

    @Test
    void schemaFailurePreventsModuleReadsAndSqlFailureStaysGeneric() {
        AtomicBoolean moduleCalled = new AtomicBoolean();
        DatabaseDiagnostics mismatch = new DatabaseDiagnostics(
                () -> DATA_SOURCE,
                (dataSource, expectations) -> {
                    throw new DatabaseSchemaVerifier.SchemaMismatchException();
                },
                dataSource -> {
                    moduleCalled.set(true);
                    return ModuleIntegrityService.Status.READY;
                });
        assertEquals(DatabaseSchemaStatus.State.SCHEMA_MISMATCH, mismatch.run().state());
        assertEquals(false, moduleCalled.get());

        DatabaseDiagnostics unavailable = new DatabaseDiagnostics(
                () -> DATA_SOURCE,
                (dataSource, expectations) -> { },
                dataSource -> { throw new SQLException("secret provider detail"); });
        assertEquals(DatabaseSchemaStatus.State.DATABASE_UNAVAILABLE,
                unavailable.run().state());
    }

    @Test
    void jndiFailurePreventsEveryDatabaseVerifier() {
        AtomicBoolean schemaCalled = new AtomicBoolean();
        DatabaseDiagnostics diagnostics = new DatabaseDiagnostics(
                () -> { throw new NamingException("secret JNDI detail"); },
                (dataSource, expectations) -> schemaCalled.set(true),
                dataSource -> ModuleIntegrityService.Status.READY);

        assertEquals(DatabaseSchemaStatus.State.JNDI_UNAVAILABLE, diagnostics.run().state());
        assertEquals(false, schemaCalled.get());
    }

    private static DataSource proxyDataSource() {
        return (DataSource) java.lang.reflect.Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                (proxy, method, arguments) -> {
                    Class<?> type = method.getReturnType();
                    if (!type.isPrimitive()) return null;
                    if (type == boolean.class) return false;
                    if (type == char.class) return '\0';
                    if (type == float.class || type == double.class) return 0.0;
                    return 0;
                });
    }
}
