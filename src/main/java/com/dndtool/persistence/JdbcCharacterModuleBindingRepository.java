package com.dndtool.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Reads only stable identities and saved hashes; names and character state stay outside diagnostics. */
public final class JdbcCharacterModuleBindingRepository
        implements CharacterModuleBindingRepository {
    private static final String FIND_ALL_SQL = """
            SELECT campaign_root.campaign_key, character_root.character_key,
                   character_root.saved_module_key,
                   character_root.saved_release_version,
                   character_root.saved_content_sha256
            FROM character_record AS character_root
            JOIN campaign AS campaign_root ON campaign_root.id = character_root.campaign_id
            ORDER BY character_root.id
            """;

    private final DataSource dataSource;

    public JdbcCharacterModuleBindingRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
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
                            result.getString("character_key"),
                            result.getString("saved_module_key"),
                            result.getString("saved_release_version"),
                            result.getString("saved_content_sha256")));
                }
                return List.copyOf(bindings);
            }
        }
    }
}
