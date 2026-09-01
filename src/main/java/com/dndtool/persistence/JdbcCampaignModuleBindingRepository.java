package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Loads frozen campaign bindings without reading campaign names or other private game state. */
public final class JdbcCampaignModuleBindingRepository
        implements CampaignModuleBindingRepository {
    private static final String FIND_ALL_SQL = """
            SELECT c.campaign_key, c.campaign_status,
                   cm.frozen_module_key, cm.frozen_release_version,
                   cm.frozen_content_sha256
            FROM campaign AS c
            LEFT JOIN campaign_module AS cm ON cm.campaign_id = c.id
            ORDER BY c.id
            """;

    private final DataSource dataSource;

    public JdbcCampaignModuleBindingRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public List<Binding> findAll() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL)) {
            statement.setFetchSize(64);
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery()) {
                List<Binding> bindings = new ArrayList<>();
                while (result.next()) {
                    bindings.add(new Binding(
                            result.getString("campaign_key"),
                            result.getString("campaign_status"),
                            result.getString("frozen_module_key"),
                            result.getString("frozen_release_version"),
                            result.getString("frozen_content_sha256")));
                }
                return List.copyOf(bindings);
            }
        }
    }
}
