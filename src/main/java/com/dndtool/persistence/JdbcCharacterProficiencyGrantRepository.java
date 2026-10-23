package com.dndtool.persistence;

import com.dndtool.module.CharacterProficiencyGrant;
import com.dndtool.module.ModuleCatalog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Retains complete grant facts in the caller's transaction; never commits or rolls back. */
public final class JdbcCharacterProficiencyGrantRepository {
    private static final String INSERT_SQL = """
            INSERT INTO character_proficiency_grant_v2 (
                character_id, module_release_id, category, source_module_key, source_type,
                source_key, grant_slot_key, grant_kind, target_module_key, proficiency_type,
                proficiency_key, is_suppressed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String READ_SQL = """
            SELECT category, source_module_key, source_type, source_key, grant_slot_key,
                   grant_kind, target_module_key, proficiency_type, proficiency_key, is_suppressed
            FROM character_proficiency_grant_v2
            WHERE character_id = ? AND module_release_id = ?
            """;

    public void append(Connection connection, long characterId, long moduleReleaseId,
            CharacterProficiencyGrant grant) throws SQLException {
        BusinessWriteTransactions.requireGuarded(connection);
        requireTransaction(connection, characterId, moduleReleaseId);
        Objects.requireNonNull(grant, "proficiency grant");
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setQueryTimeout(5);
            statement.setLong(1, characterId);
            statement.setLong(2, moduleReleaseId);
            statement.setString(3, grant.category().name());
            statement.setString(4, grant.sourceModuleKey());
            statement.setString(5, grant.sourceType());
            statement.setString(6, grant.sourceKey());
            statement.setString(7, grant.grantSlotKey());
            statement.setString(8, grant.grantKind().name());
            statement.setString(9, grant.targetModuleKey());
            statement.setString(10, grant.category().catalogType());
            statement.setString(11, grant.proficiencyKey());
            statement.setBoolean(12, grant.suppressed());
            if (statement.executeUpdate() != 1) throw invalid();
        }
    }

    public List<CharacterProficiencyGrant> read(
            Connection connection, long characterId, long moduleReleaseId) throws SQLException {
        requireTransaction(connection, characterId, moduleReleaseId);
        List<CharacterProficiencyGrant> grants = new ArrayList<>();
        Set<List<String>> identities = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(READ_SQL)) {
            statement.setQueryTimeout(5);
            statement.setLong(1, characterId);
            statement.setLong(2, moduleReleaseId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    CharacterProficiencyGrant.Category category = CharacterProficiencyGrant.Category
                            .valueOf(text(rows, "category"));
                    if (!category.catalogType().equals(text(rows, "proficiency_type"))) throw invalid();
                    int suppressed = rows.getInt("is_suppressed");
                    if (rows.wasNull() || suppressed < 0 || suppressed > 1) throw invalid();
                    CharacterProficiencyGrant grant = new CharacterProficiencyGrant(category,
                            text(rows, "source_module_key"), text(rows, "source_type"),
                            text(rows, "source_key"), text(rows, "grant_slot_key"),
                            CharacterProficiencyGrant.GrantKind.valueOf(text(rows, "grant_kind")),
                            text(rows, "target_module_key"), text(rows, "proficiency_key"),
                            suppressed == 1);
                    if (!identities.add(List.of(grant.category().name(), grant.sourceModuleKey(),
                            grant.sourceType(), grant.sourceKey(), grant.grantSlotKey(),
                            grant.targetModuleKey(), grant.proficiencyKey()))) throw invalid();
                    grants.add(grant);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Invalid proficiency grant projection", exception);
        }
        grants.sort(CharacterProficiencyGrant.ORDER);
        return List.copyOf(grants);
    }

    /**
     * Checks identity continuity only. The caller must supply an immutable catalog whose complete
     * contents were independently validated; matching release metadata is not content verification.
     */
    static void verifyCatalog(Connection connection, long moduleReleaseId, ModuleCatalog catalog)
            throws SQLException {
        String sql = "SELECT module_key, release_version, canonical_format_version, hash_algorithm, "
                + "content_sha256, release_status FROM module_release WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            statement.setLong(1, moduleReleaseId);
            try (ResultSet row = statement.executeQuery()) {
                var release = Objects.requireNonNull(catalog, "catalog").release();
                if (!row.next() || !release.moduleKey().equals(text(row, "module_key"))
                        || !release.releaseVersion().equals(text(row, "release_version"))
                        || release.canonicalFormatVersion() != row.getInt("canonical_format_version")
                        || row.wasNull()
                        || !release.hashAlgorithm().equals(text(row, "hash_algorithm"))
                        || !Objects.equals(release.contentSha256(), row.getString("content_sha256"))
                        || !release.releaseStatus().equals(text(row, "release_status"))
                        || row.next()) throw invalid();
            }
        }
    }

    private static String text(ResultSet rows, String field) throws SQLException {
        String value = rows.getString(field);
        if (value == null) throw invalid();
        return value;
    }

    private static void requireTransaction(Connection connection, long characterId,
            long moduleReleaseId) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        if (connection.getAutoCommit() || characterId <= 0 || moduleReleaseId <= 0) throw invalid();
    }

    private static SQLException invalid() {
        return new SQLException("Invalid proficiency grant state or caller transaction");
    }
}
