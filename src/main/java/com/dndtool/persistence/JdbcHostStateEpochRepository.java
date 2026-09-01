package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/** Minimal read used by the host request boundary before a protected write is dispatched. */
public final class JdbcHostStateEpochRepository implements HostStateEpochRepository {
    private static final String CURRENT_SQL = """
            SELECT host_state_epoch
            FROM campaign
            WHERE campaign_status = 'ACTIVE'
            ORDER BY id
            """;
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private final DataSource dataSource;

    public JdbcHostStateEpochRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public long currentActiveEpoch() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(CURRENT_SQL)) {
            statement.setMaxRows(2);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return 0L;
                long epoch = result.getLong("host_state_epoch");
                if (result.wasNull() || epoch < 0 || result.next()) {
                    throw new SQLException("Active host state epoch is invalid or not unique");
                }
                return epoch;
            }
        }
    }
}
