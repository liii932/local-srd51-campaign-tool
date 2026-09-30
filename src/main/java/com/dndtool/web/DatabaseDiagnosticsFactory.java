package com.dndtool.web;

import com.dndtool.persistence.DatabaseSchemaVerifier;
import com.dndtool.persistence.JdbcCampaignModuleBindingRepository;
import com.dndtool.persistence.JdbcCharacterModuleBindingRepository;
import com.dndtool.persistence.JdbcModuleCatalogRepository;
import com.dndtool.service.DatabaseDiagnostics;
import com.dndtool.service.ModuleIntegrityService;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Assembles the legacy single-pool diagnostic capability at the Web boundary. */
final class DatabaseDiagnosticsFactory {
    static final String JNDI_NAME = "java:comp/env/jdbc/DndToolSE";

    private DatabaseDiagnosticsFactory() {
    }

    static DatabaseDiagnostics usingJndi() {
        return usingJndi(InitialContext::doLookup);
    }

    static DatabaseDiagnostics usingJndi(JndiLookup lookup) {
        return new DatabaseDiagnostics(
                () -> {
                    Object resource = lookup.lookup(JNDI_NAME);
                    if (resource instanceof DataSource dataSource) {
                        return dataSource;
                    }
                    throw new NamingException("Configured JNDI resource is not a DataSource");
                },
                new DatabaseSchemaVerifier()::verify,
                dataSource -> new ModuleIntegrityService(
                        new JdbcModuleCatalogRepository(dataSource),
                        new JdbcCampaignModuleBindingRepository(dataSource),
                        new JdbcCharacterModuleBindingRepository(dataSource)).verifyAll());
    }

    @FunctionalInterface
    interface JndiLookup {
        Object lookup(String name) throws NamingException;
    }
}
