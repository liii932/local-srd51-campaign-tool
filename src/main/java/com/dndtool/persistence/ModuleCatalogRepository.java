package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.Optional;

/** Read-only access to one immutable logical module projection by stable identity. */
public interface ModuleCatalogRepository {
    /** Returns empty when the requested release identity is not installed. */
    Optional<ModuleCatalog> findByIdentity(String moduleKey, String releaseVersion)
            throws SQLException;
}
