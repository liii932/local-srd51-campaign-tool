package com.dndtool.persistence;

import com.dndtool.service.ModuleIntegrityService;
import java.sql.SQLException;
import java.util.List;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Performs the same read-only readiness check at startup and on host demand. */
public final class DatabaseDiagnostics {
    static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private final DataSourceLocator dataSourceLocator;
    private final SchemaVerifier schemaVerifier;
    private final ModuleVerifier moduleVerifier;

    DatabaseDiagnostics(
            DataSourceLocator dataSourceLocator,
            SchemaVerifier schemaVerifier,
            ModuleVerifier moduleVerifier) {
        this.dataSourceLocator = dataSourceLocator;
        this.schemaVerifier = schemaVerifier;
        this.moduleVerifier = moduleVerifier;
    }

    public static DatabaseDiagnostics usingJndi() {
        DatabaseSchemaVerifier schemaVerifier = new DatabaseSchemaVerifier();
        return new DatabaseDiagnostics(
                DatabaseDiagnostics::lookupDataSource,
                schemaVerifier::verify,
                dataSource -> ModuleIntegrityService.using(dataSource).verifyAll());
    }

    public DatabaseSchemaStatus run() {
        final List<SchemaMigrations.Expectation> expectedMigrations;
        try {
            expectedMigrations = SchemaMigrations.loadExpectations();
        } catch (SchemaMigrations.PackagedSchemaException exception) {
            return DatabaseSchemaStatus.failure(DatabaseSchemaStatus.State.PACKAGED_SCRIPT_INVALID);
        }

        final DataSource dataSource;
        try {
            dataSource = dataSourceLocator.locate();
        } catch (NamingException exception) {
            return DatabaseSchemaStatus.failure(DatabaseSchemaStatus.State.JNDI_UNAVAILABLE);
        }

        try {
            schemaVerifier.verify(dataSource, expectedMigrations);
            if (moduleVerifier.verify(dataSource)
                    != ModuleIntegrityService.Status.READY) {
                return DatabaseSchemaStatus.failure(
                        DatabaseSchemaStatus.State.MODULE_HASH_MISMATCH);
            }
            return DatabaseSchemaStatus.ready(expectedMigrations);
        } catch (DatabaseSchemaVerifier.SchemaMismatchException exception) {
            return DatabaseSchemaStatus.failure(DatabaseSchemaStatus.State.SCHEMA_MISMATCH);
        } catch (SQLException exception) {
            return DatabaseSchemaStatus.failure(DatabaseSchemaStatus.State.DATABASE_UNAVAILABLE);
        }
    }

    private static DataSource lookupDataSource() throws NamingException {
        Object resource = InitialContext.doLookup(JNDI_NAME);
        if (resource instanceof DataSource dataSource) {
            return dataSource;
        }
        throw new NamingException("Configured JNDI resource is not a DataSource");
    }

    @FunctionalInterface
    interface DataSourceLocator {
        DataSource locate() throws NamingException;
    }

    @FunctionalInterface
    interface SchemaVerifier {
        void verify(DataSource dataSource, List<SchemaMigrations.Expectation> expectations)
                throws SQLException, DatabaseSchemaVerifier.SchemaMismatchException;
    }

    @FunctionalInterface
    interface ModuleVerifier {
        ModuleIntegrityService.Status verify(DataSource dataSource) throws SQLException;
    }
}
