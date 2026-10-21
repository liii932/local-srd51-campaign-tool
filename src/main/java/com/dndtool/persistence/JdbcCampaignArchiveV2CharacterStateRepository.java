package com.dndtool.persistence;

import static com.dndtool.service.CampaignArchiveV2CharacterState.*;

import com.dndtool.module.ModuleCatalog;
import com.dndtool.service.CampaignArchiveV2CharacterState;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the complete internal DRAFT character slice in one caller-owned consistent transaction. */
public final class JdbcCampaignArchiveV2CharacterStateRepository {
    /**
     * Requires a reliably validated immutable catalog. Header matching does not validate its contents.
     * This slice rejects ASI state because it cannot restore current ability scores.
     */
    public CampaignArchiveV2CharacterState read(Connection connection, long campaignId,
            long moduleReleaseId, ModuleCatalog catalog) throws SQLException {
        if (connection == null || connection.getAutoCommit() || campaignId <= 0
                || moduleReleaseId <= 0 || catalog == null) throw invalid();
        int isolation = connection.getTransactionIsolation();
        if (isolation != Connection.TRANSACTION_REPEATABLE_READ
                && isolation != Connection.TRANSACTION_SERIALIZABLE) throw invalid();
        JdbcCharacterProficiencyGrantRepository.verifyCatalog(connection, moduleReleaseId, catalog);
        Map<String, Long> characters = new HashMap<>();
        try (PreparedStatement statement = prepare(connection,
                "SELECT id, character_key, module_release_id FROM character_record WHERE campaign_id = ?",
                campaignId); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                if (number(rows, "module_release_id") != moduleReleaseId
                        || characters.putIfAbsent(text(rows, "character_key"), number(rows, "id")) != null) {
                    throw invalid();
                }
            }
        }
        long tail;
        try (PreparedStatement statement = prepare(connection,
                "SELECT internal_event_tail FROM campaign WHERE id = ?",
                campaignId); ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw invalid();
            tail = number(rows, "internal_event_tail");
            if (tail < 0 || rows.next()) throw invalid();
        }
        requireSupportedAbilities(connection, campaignId, moduleReleaseId);
        Map<Long, StateEvent> events = new HashMap<>();
        List<CreationSnapshot> snapshots = query(connection, campaignId, moduleReleaseId,
                "character_creation_snapshot_v2", true, rows -> {
                    long event = event(rows, events);
                    List<AbilityScore> abilities = new ArrayList<>();
                    for (String ability : List.of("strength", "dexterity", "constitution",
                            "intelligence", "wisdom", "charisma")) {
                        abilities.add(new AbilityScore("ability." + ability,
                                integer(rows, "base_" + ability), integer(rows, "final_" + ability)));
                    }
                    return new CreationSnapshot(text(rows, "character_key"), event,
                            text(rows, "preview_digest_sha256"), text(rows, "request_digest_sha256"),
                            text(rows, "ability_method_key"), text(rows, "race_key"),
                            rows.getString("subrace_key"), text(rows, "background_key"),
                            text(rows, "class_key"), abilities, integer(rows, "maximum_hit_points"));
                });
        List<CreationSelection> selections = query(connection, campaignId, moduleReleaseId,
                "character_creation_selection_v2", false, rows -> new CreationSelection(
                        text(rows, "character_key"), text(rows, "selection_kind"),
                        integer(rows, "selection_order"), text(rows, "selection_key")));
        List<ResourceState> resources = query(connection, campaignId, moduleReleaseId,
                "character_resource_state_v2", false, rows -> new ResourceState(
                        text(rows, "character_key"), text(rows, "resource_key"),
                        number(rows, "current_value"), number(rows, "maximum_value"),
                        bool(rows, "is_unlimited")));
        List<ClassLevel> levels = query(connection, campaignId, moduleReleaseId,
                "character_class_level_v2", false, rows -> new ClassLevel(
                        text(rows, "character_key"), text(rows, "class_key"), integer(rows, "class_level")));
        List<SubclassState> subclasses = query(connection, campaignId, moduleReleaseId,
                "character_subclass_state_v2", true, rows -> new SubclassState(
                        text(rows, "character_key"), text(rows, "class_key"), text(rows, "subclass_key"),
                        integer(rows, "selected_at_class_level"), event(rows, events)));
        List<FeatureState> features = query(connection, campaignId, moduleReleaseId,
                "character_feature_state_v2", true, rows -> new FeatureState(
                        text(rows, "character_key"), text(rows, "feature_key"),
                        integer(rows, "acquired_at_class_level"), text(rows, "execution_mode"),
                        text(rows, "execution_algorithm"), event(rows, events)));
        List<FeatureChoice> choices = query(connection, campaignId, moduleReleaseId,
                "character_feature_choice_v2", true, rows -> new FeatureChoice(
                        text(rows, "character_key"), text(rows, "source_feature_key"),
                        integer(rows, "choice_order"), text(rows, "choice_type"),
                        text(rows, "choice_key"), event(rows, events)));
        List<FeatState> feats = query(connection, campaignId, moduleReleaseId,
                "character_feat_state_v2", true, rows -> new FeatState(
                        text(rows, "character_key"), text(rows, "feat_key"), event(rows, events)));
        List<MulticlassProficiency> multiclass = query(connection, campaignId, moduleReleaseId,
                "character_multiclass_proficiency_v2", true, rows -> new MulticlassProficiency(
                        text(rows, "character_key"), text(rows, "class_key"),
                        text(rows, "proficiency_key"), event(rows, events)));
        List<ProficiencyGrant> grants = new ArrayList<>();
        JdbcCharacterProficiencyGrantRepository grantRepository = new JdbcCharacterProficiencyGrantRepository();
        for (var entry : characters.entrySet()) {
            for (var grant : grantRepository.read(connection, entry.getValue(), moduleReleaseId)) {
                grants.add(new ProficiencyGrant(entry.getKey(), grant));
            }
        }
        try {
            return new CampaignArchiveV2CharacterState(2, tail, List.copyOf(events.values()), snapshots,
                    selections, resources, levels, subclasses, features, choices, feats, multiclass,
                    grants).validated(Set.copyOf(characters.keySet()), catalog);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new SQLException("Invalid internal character archive projection", exception);
        }
    }

    private static void requireSupportedAbilities(Connection connection, long campaignId,
            long moduleReleaseId) throws SQLException {
        String sql = """
                SELECT 1 AS unsupported_state
                FROM character_record AS character_root
                WHERE character_root.campaign_id = ? AND character_root.module_release_id = ?
                  AND (EXISTS (
                    SELECT 1 FROM character_ability_score_change_v2 AS ability_change
                    WHERE ability_change.character_id = character_root.id
                      AND ability_change.module_release_id = character_root.module_release_id)
                  OR EXISTS (
                    SELECT 1 FROM character_advancement_choice_v2 AS advancement_choice
                    WHERE advancement_choice.character_id = character_root.id
                      AND advancement_choice.module_release_id = character_root.module_release_id
                      AND advancement_choice.choice_type = 'ABILITY_SCORE_IMPROVEMENT'))
                LIMIT 1
                """;
        try (PreparedStatement statement = prepare(connection, sql, campaignId)) {
            statement.setLong(2, moduleReleaseId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    throw new SQLException("ASI current ability state is not supported by this archive slice");
                }
            }
        }
    }

    private static <T> List<T> query(Connection connection, long campaignId, long moduleReleaseId,
            String table, boolean includeEvent, Decoder<T> decoder) throws SQLException {
        boolean creation = table.equals("character_creation_snapshot_v2");
        String sql = "SELECT state_row.*, character_root.character_key"
                + (includeEvent ? ", event_root.event_sequence, event_root.event_type, subject_root.character_key AS event_subject" : "")
                + " FROM " + table + " AS state_row JOIN character_record AS character_root ON character_root.id = state_row.character_id"
                + " AND character_root.module_release_id = state_row.module_release_id"
                + (includeEvent ? " LEFT JOIN game_event AS event_root ON "
                        + (creation ? "event_root.subject_character_id = character_root.id AND event_root.event_type = 'LEVEL_ONE_CHARACTER_CREATED'"
                                : "event_root.id = state_row.acquired_event_id")
                        + " AND event_root.campaign_id = character_root.campaign_id"
                        + " LEFT JOIN character_record AS subject_root ON subject_root.id = event_root.subject_character_id"
                        : "")
                + " WHERE character_root.campaign_id = ? AND state_row.module_release_id = ?";
        List<T> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(connection, sql, campaignId)) {
            statement.setLong(2, moduleReleaseId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) values.add(decoder.decode(rows));
            }
        }
        return List.copyOf(values);
    }

    private static long event(ResultSet rows, Map<Long, StateEvent> events) throws SQLException {
        long sequence = number(rows, "event_sequence");
        String subject = text(rows, "event_subject");
        if (!subject.equals(text(rows, "character_key"))) throw invalid();
        StateEvent value = new StateEvent(sequence, text(rows, "event_type"), subject);
        StateEvent previous = events.putIfAbsent(sequence, value);
        if (previous != null && !previous.equals(value)) throw invalid();
        return sequence;
    }

    private static PreparedStatement prepare(Connection connection, String sql, long campaignId)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(5);
        statement.setLong(1, campaignId);
        return statement;
    }

    private static String text(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        if (value == null) throw invalid();
        return value;
    }

    private static long number(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        if (rows.wasNull()) throw invalid();
        return value;
    }

    private static int integer(ResultSet rows, String column) throws SQLException {
        return Math.toIntExact(number(rows, column));
    }

    private static boolean bool(ResultSet rows, String column) throws SQLException {
        int value = integer(rows, column);
        if (value < 0 || value > 1) throw invalid();
        return value == 1;
    }

    private static SQLException invalid() {
        return new SQLException("Invalid internal character archive state or transaction");
    }

    @FunctionalInterface
    private interface Decoder<T> { T decode(ResultSet rows) throws SQLException; }
}
