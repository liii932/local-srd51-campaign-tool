package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.dndtool.persistence.DatabaseSchemaStatus;
import com.dndtool.persistence.DatabaseSchemaVerifier;
import com.dndtool.persistence.SchemaMigrations;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.naming.NamingException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Verifies readiness ordering and stable failure-state mapping without JNDI or MySQL. */
final class DatabaseDiagnosticsTest {
    private static final DataSource DATA_SOURCE = proxyDataSource();

    @Test
    void readyRequiresSchemaThenModuleIntegrity() throws Exception {
        AtomicBoolean schemaCalled = new AtomicBoolean();
        AtomicBoolean moduleCalled = new AtomicBoolean();
        DatabaseDiagnostics diagnostics = new DatabaseDiagnostics(
                () -> DATA_SOURCE,
                (dataSource, expectations) -> schemaCalled.set(true),
                dataSource -> {
                    assertEquals(true, schemaCalled.get());
                    assertSame(DATA_SOURCE, dataSource);
                    moduleCalled.set(true);
                    return ModuleIntegrityService.Status.READY;
                });

        DatabaseSchemaStatus result = diagnostics.run();

        assertEquals(DatabaseSchemaStatus.State.READY, result.state());
        SchemaMigrations.Expectation latest = SchemaMigrations.loadExpectations().getLast();
        assertEquals(latest.version(), result.schemaVersion());
        assertEquals(latest.scriptName(), result.scriptName());
        assertEquals(latest.scriptSha256(), result.scriptSha256());
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

    @Test
    void schemaConnectionFailurePreventsModuleReads() {
        AtomicBoolean moduleCalled = new AtomicBoolean();
        DatabaseDiagnostics diagnostics = new DatabaseDiagnostics(
                () -> DATA_SOURCE,
                (dataSource, expectations) -> { throw new SQLException("secret SQL detail"); },
                dataSource -> {
                    moduleCalled.set(true);
                    return ModuleIntegrityService.Status.READY;
                });

        assertEquals(new DatabaseSchemaStatus(
                DatabaseSchemaStatus.State.DATABASE_UNAVAILABLE, 0, null, null), diagnostics.run());
        assertEquals(false, moduleCalled.get());
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
