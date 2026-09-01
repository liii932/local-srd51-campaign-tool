package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Loads an immutable module projection without taking ownership of the caller's transaction. */
@FunctionalInterface
public interface TransactionalModuleCatalogRepository {
    Optional<ModuleCatalog> findByIdentity(
            Connection connection, String moduleKey, String releaseVersion) throws SQLException;
}
