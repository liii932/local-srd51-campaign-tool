package com.dndtool.persistence;

import java.sql.SQLException;

/** Reads the epoch that authorizes commands against the current active host state. */
@FunctionalInterface
public interface HostStateEpochRepository {
    /** Returns zero when there is no active campaign. */
    long currentActiveEpoch() throws SQLException;
}
