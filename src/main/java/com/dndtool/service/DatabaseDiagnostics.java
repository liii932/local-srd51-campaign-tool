package com.dndtool.service;

import com.dndtool.persistence.DatabaseSchemaStatus;
import com.dndtool.persistence.DatabaseSchemaVerifier;
import com.dndtool.persistence.SchemaMigrations;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Performs the same read-only readiness check at startup and on host demand. */
public final class DatabaseDiagnostics {
    private final DataSourceLocator dataSourceLocator;
    private final SchemaVerifier schemaVerifier;
    private final ModuleVerifier moduleVerifier;

    public DatabaseDiagnostics(
            DataSourceLocator dataSourceLocator,
            SchemaVerifier schemaVerifier,
            ModuleVerifier moduleVerifier) {
        this.dataSourceLocator = Objects.requireNonNull(dataSourceLocator);
        this.schemaVerifier = Objects.requireNonNull(schemaVerifier);
        this.moduleVerifier = Objects.requireNonNull(moduleVerifier);
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

    @FunctionalInterface
    public interface DataSourceLocator {
        DataSource locate() throws NamingException;
    }

    @FunctionalInterface
    public interface SchemaVerifier {
        void verify(DataSource dataSource, List<SchemaMigrations.Expectation> expectations)
                throws SQLException, DatabaseSchemaVerifier.SchemaMismatchException;
    }

    @FunctionalInterface
    public interface ModuleVerifier {
        ModuleIntegrityService.Status verify(DataSource dataSource) throws SQLException;
    }
}
