package com.dndtool.persistence;

import com.dndtool.service.CampaignArchiveDocument;
import com.dndtool.service.CampaignArchiveReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** JDBC whole-campaign rebuild; it never commits or rolls back the caller's transaction. */
public final class JdbcCampaignArchiveImportRepository
        implements CampaignArchiveImportRepository {
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private static final String LOCK_RELEASE_SQL = """
            SELECT id
            FROM module_release
            WHERE module_key = ? AND release_version = ?
              AND canonical_format_version = 1
              AND hash_algorithm = 'SHA-256'
              AND content_sha256 = ?
              AND release_status = 'RELEASED'
              AND released_at IS NOT NULL
            FOR SHARE
            """;
    private static final String LOCK_CAMPAIGNS_SQL = """
            SELECT id, campaign_key, campaign_status, host_state_epoch
            FROM campaign
            WHERE campaign_key = ? OR campaign_status = 'ACTIVE'
            ORDER BY id
            FOR UPDATE
            """;
    private static final String LOCK_CHARACTER_IDENTITIES_SQL = """
            SELECT id, campaign_id, character_key
            FROM character_record
            ORDER BY id
            FOR UPDATE
            """;
    private static final String INSERT_CAMPAIGN_SQL = """
            INSERT INTO campaign (
                campaign_key, campaign_name, campaign_status,
                host_state_epoch, row_version, internal_event_tail)
            VALUES (?, ?, ?, ?, 0, ?)
            """;
    private static final String UPDATE_CAMPAIGN_SQL = """
            UPDATE campaign
            SET campaign_name = ?, campaign_status = ?, host_state_epoch = ?,
                row_version = 0, internal_event_tail = ?
            WHERE id = ? AND campaign_key = ?
            """;
    private static final String ARCHIVE_CONFIRMED_CAMPAIGN_SQL = """
            UPDATE campaign
            SET campaign_status = 'ARCHIVED'
            WHERE id = ? AND campaign_key = ? AND campaign_status = 'ACTIVE'
            """;
    private static final String INSERT_BINDING_SQL = """
            INSERT INTO campaign_module (
                campaign_id, module_release_id, frozen_module_key,
                frozen_release_version, frozen_content_sha256)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_CHARACTER_SQL = """
            INSERT INTO character_record (
                campaign_id, module_release_id, character_key, character_type,
                character_name, character_status, saved_module_key,
                saved_release_version, saved_content_sha256, row_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String INSERT_FIELD_SQL = """
            INSERT INTO character_field_value (
                character_id, module_release_id, field_key, value_type,
                text_value, integer_value, decimal_value, boolean_value)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_CLASS_SQL = """
            INSERT INTO character_class_level (
                character_id, module_release_id, class_key, class_level)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_SKILL_SQL = """
            INSERT INTO character_skill_proficiency (
                character_id, module_release_id, skill_key, proficiency_key)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_SAVE_SQL = """
            INSERT INTO character_save_proficiency (
                character_id, module_release_id, save_key, proficiency_key)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_ITEM_SQL = """
            INSERT INTO item_instance (
                character_id, source_kind, module_release_id, item_key,
                item_name, item_description, quantity, item_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_MAP_SQL = """
            INSERT INTO map_instance (campaign_id, module_release_id, map_key, map_type)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_PARTY_SQL = """
            INSERT INTO party_world_position (
                campaign_id, map_instance_id, module_release_id, map_key, node_key)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_BATTLE_SQL = """
            INSERT INTO battle_state (
                campaign_id, map_instance_id, module_release_id, map_key, battle_status)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_PARTICIPANT_SQL = """
            INSERT INTO battle_participant (battle_id, campaign_id, character_id, faction)
            VALUES (?, ?, ?, ?)
            """;
    private static final String INSERT_POSITION_SQL = """
            INSERT INTO entity_position (
                battle_id, campaign_id, active_campaign_id, map_instance_id,
                module_release_id, map_key, character_id, node_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_EVENT_SQL = """
            INSERT INTO game_event (
                campaign_id, event_sequence, event_type, subject_character_id, event_text)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String INSERT_CHECK_SQL = """
            INSERT INTO check_execution (
                game_event_id, campaign_id, module_release_id, executor_character_id,
                event_key, check_key, roll_mode_key, modifier_source_key, manual_name,
                modifier_value, total_value, difficulty_class, check_result)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    // A replacement discards every old child and historical/idempotency row. No child UPSERT or
    // caller-selected subset exists, so a successful result is necessarily a whole rebuild.
    private static final List<String> CLEAR_TARGET_SQL = List.of(
            "DELETE FROM host_operation WHERE campaign_id = ?",
            childDelete("character_feature_adjudication_v2", "feature_adjudication"),
            childDelete("character_resource_recovery_v2", "resource_recovery"),
            childDelete("character_feature_choice_v2", "feature_choice"),
            childDelete("character_feat_state_v2", "feat_state"),
            childDelete("character_multiclass_proficiency_v2", "multiclass_proficiency"),
            childDelete("character_feature_state_v2", "feature_state"),
            childDelete("character_subclass_state_v2", "subclass_state"),
            childDelete("character_ability_score_change_v2", "ability_change"),
            childDelete("character_advancement_choice_v2", "advancement_choice"),
            childDelete("character_level_resource_change_v2", "level_resource_change"),
            childDelete("character_level_advancement_v2", "level_advancement"),
            childDelete("character_class_level_v2", "class_level_v2"),
            childDelete("character_resource_state_v2", "resource_state"),
            childDelete("character_creation_selection_v2", "creation_selection"),
            childDelete("character_creation_snapshot_v2", "creation_snapshot"),
            """
            DELETE parameter_value
            FROM check_effect_parameter_value AS parameter_value
            JOIN check_effect AS planned_effect
              ON planned_effect.id = parameter_value.check_effect_id
            JOIN check_execution AS execution
              ON execution.id = planned_effect.check_execution_id
            WHERE execution.campaign_id = ?
            """,
            """
            DELETE planned_effect
            FROM check_effect AS planned_effect
            JOIN check_execution AS execution
              ON execution.id = planned_effect.check_execution_id
            WHERE execution.campaign_id = ?
            """,
            """
            DELETE roll
            FROM dice_roll AS roll
            JOIN check_execution AS execution
              ON execution.id = roll.check_execution_id
            WHERE execution.campaign_id = ?
            """,
            "DELETE FROM field_change WHERE campaign_id = ?",
            "DELETE FROM check_execution WHERE campaign_id = ?",
            "DELETE FROM entity_position WHERE campaign_id = ?",
            "DELETE FROM battle_participant WHERE campaign_id = ?",
            "DELETE FROM battle_state WHERE campaign_id = ?",
            "DELETE FROM party_world_position WHERE campaign_id = ?",
            "DELETE FROM map_instance WHERE campaign_id = ?",
            """
            DELETE owned_item
            FROM item_instance AS owned_item
            JOIN character_record AS character_root
              ON character_root.id = owned_item.character_id
            WHERE character_root.campaign_id = ?
            """,
            childDelete("character_field_value", "field_value"),
            childDelete("character_class_level", "class_level"),
            childDelete("character_skill_proficiency", "skill_tier"),
            childDelete("character_save_proficiency", "save_tier"),
            "DELETE FROM game_event WHERE campaign_id = ?",
            "DELETE FROM character_record WHERE campaign_id = ?",
            "DELETE FROM campaign_module WHERE campaign_id = ?");

    @Override
    public long importArchive(Connection connection, Command command)
            throws SQLException {
        requireCallerTransaction(connection);
        Objects.requireNonNull(command, "archive import command");
        CampaignArchiveDocument document = command.document();
        validateDocument(document);
        validateConfirmedCampaignKey(command.confirmedArchiveCampaignKey());
        long releaseId = lockRelease(connection, document.module());
        LockedCampaigns locked = lockCampaigns(connection, document.campaign().campaignKey());
        rejectForeignCharacterKeyConflicts(connection, document.characters(), locked.target());
        requireFreshHostStateEpoch(locked, command.hostStateEpoch());
        archiveConfirmedOtherActive(
                connection,
                document.campaign(),
                locked,
                command.confirmedArchiveCampaignKey());

        long eventTail = document.recentEvents().stream()
                .mapToLong(CampaignArchiveDocument.EventSnapshot::eventSequence)
                .max().orElse(0L);
        long campaignId;
        if (locked.target() == null) {
            campaignId = insertCampaign(
                    connection, document.campaign(), command.hostStateEpoch(), eventTail);
        } else {
            campaignId = locked.target().id();
            clearTarget(connection, campaignId);
            updateCampaign(
                    connection, campaignId, document.campaign(), command.hostStateEpoch(),
                    eventTail);
        }
        insertBinding(connection, campaignId, releaseId, document.module());
        Map<String, Long> characters = insertCharacters(
                connection, campaignId, releaseId, document);
        insertCharacterState(connection, releaseId, document, characters);
        insertMaps(connection, campaignId, releaseId, document, characters);
        insertEvents(connection, campaignId, releaseId, document, characters);
        return campaignId;
    }

    private static long lockRelease(
            Connection connection, CampaignArchiveDocument.ModuleReference module)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, LOCK_RELEASE_SQL)) {
            statement.setString(1, module.moduleKey());
            statement.setString(2, module.releaseVersion());
            statement.setString(3, module.contentSha256());
            statement.setMaxRows(2);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw invalidState("Validated module release disappeared");
                long id = positiveLong(result, "id");
                if (result.next()) throw invalidState("Module release identity is not unique");
                return id;
            }
        }
    }

    private static LockedCampaigns lockCampaigns(Connection connection, String targetKey)
            throws SQLException {
        CampaignRow target = null;
        CampaignRow active = null;
        try (PreparedStatement statement = prepare(connection, LOCK_CAMPAIGNS_SQL)) {
            statement.setString(1, targetKey);
            statement.setMaxRows(3);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    CampaignRow row = new CampaignRow(
                            positiveLong(result, "id"),
                            requiredString(result, "campaign_key"),
                            requiredString(result, "campaign_status"),
                            nonNegativeLong(result, "host_state_epoch"));
                    if (targetKey.equals(row.campaignKey())) {
                        if (target != null) throw invalidState("Target campaign is not unique");
                        target = row;
                    }
                    if ("ACTIVE".equals(row.status())) {
                        if (active != null) throw invalidState("Multiple active campaigns exist");
                        active = row;
                    } else if (!"ARCHIVED".equals(row.status())) {
                        throw invalidState("Locked campaign status is invalid");
                    }
                }
            }
        }
        return new LockedCampaigns(target, active);
    }

    private static void archiveConfirmedOtherActive(
            Connection connection,
            CampaignArchiveDocument.Campaign imported,
            LockedCampaigns locked,
            String confirmedArchiveCampaignKey)
            throws SQLException {
        if (!"ACTIVE".equals(imported.campaignStatus())) {
            if (confirmedArchiveCampaignKey != null) {
                throw rejection(Rejection.UNEXPECTED_ARCHIVE_CONFIRMATION);
            }
            return;
        }
        CampaignRow otherActive = locked.active() == null
                || locked.target() != null && locked.active().id() == locked.target().id()
                ? null : locked.active();
        if (otherActive == null) {
            if (confirmedArchiveCampaignKey != null) {
                throw rejection(Rejection.PREVIEW_STATE_CHANGED);
            }
            return;
        }
        if (confirmedArchiveCampaignKey == null) {
            throw rejection(Rejection.ACTIVE_CAMPAIGN_CONFIRMATION_REQUIRED);
        }
        if (!otherActive.campaignKey().equals(confirmedArchiveCampaignKey)) {
            throw rejection(Rejection.PREVIEW_STATE_CHANGED);
        }
        try (PreparedStatement statement = prepare(connection, ARCHIVE_CONFIRMED_CAMPAIGN_SQL)) {
            statement.setLong(1, otherActive.id());
            statement.setString(2, otherActive.campaignKey());
            if (statement.executeUpdate() != 1) {
                throw rejection(Rejection.PREVIEW_STATE_CHANGED);
            }
        }
    }

    private static void rejectForeignCharacterKeyConflicts(
            Connection connection,
            List<CampaignArchiveDocument.CharacterState> importedCharacters,
            CampaignRow target) throws SQLException {
        if (importedCharacters.isEmpty()) return;
        Set<String> importedKeys = new HashSet<>();
        for (CampaignArchiveDocument.CharacterState character : importedCharacters) {
            importedKeys.add(character.characterKey());
        }
        // Lock every existing identity in database-id order. Under SERIALIZABLE this also closes
        // the insertion gap, so another transaction cannot claim an imported global key later.
        Set<String> storedKeys = new HashSet<>();
        try (PreparedStatement statement = prepare(connection, LOCK_CHARACTER_IDENTITIES_SQL);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                positiveLong(result, "id");
                long campaignId = positiveLong(result, "campaign_id");
                String characterKey = requiredString(result, "character_key");
                if (!canonicalUuidV4(characterKey) || !storedKeys.add(characterKey)) {
                    throw invalidState("Stored character identity is invalid or duplicated");
                }
                if (importedKeys.contains(characterKey)
                        && (target == null || campaignId != target.id())) {
                    throw rejection(Rejection.STABLE_IDENTITY_CONFLICT);
                }
            }
        }
    }

    private static RejectionException rejection(Rejection rejection) {
        return new RejectionException(rejection);
    }

    private static void requireFreshHostStateEpoch(LockedCampaigns locked, long candidate)
            throws SQLException {
        if (locked.target() != null && locked.target().hostStateEpoch() == candidate
                || locked.active() != null && locked.active().hostStateEpoch() == candidate) {
            throw invalidState("Generated host state epoch collides with locked host state");
        }
    }

    private static long insertCampaign(
            Connection connection,
            CampaignArchiveDocument.Campaign campaign,
            long hostStateEpoch,
            long eventTail)
            throws SQLException {
        try (PreparedStatement statement = prepareGenerated(connection, INSERT_CAMPAIGN_SQL)) {
            statement.setString(1, campaign.campaignKey());
            statement.setString(2, campaign.campaignName());
            statement.setString(3, campaign.campaignStatus());
            statement.setLong(4, hostStateEpoch);
            statement.setLong(5, eventTail);
            return executeInsert(statement, "campaign");
        }
    }

    private static void clearTarget(Connection connection, long campaignId) throws SQLException {
        for (String sql : CLEAR_TARGET_SQL) {
            try (PreparedStatement statement = prepare(connection, sql)) {
                statement.setLong(1, campaignId);
                statement.executeUpdate();
            }
        }
    }

    private static void updateCampaign(
            Connection connection,
            long campaignId,
            CampaignArchiveDocument.Campaign campaign,
            long hostStateEpoch,
            long eventTail) throws SQLException {
        try (PreparedStatement statement = prepare(connection, UPDATE_CAMPAIGN_SQL)) {
            statement.setString(1, campaign.campaignName());
            statement.setString(2, campaign.campaignStatus());
            statement.setLong(3, hostStateEpoch);
            statement.setLong(4, eventTail);
            statement.setLong(5, campaignId);
            statement.setString(6, campaign.campaignKey());
            executeOne(statement, "campaign root replacement");
        }
    }

    private static void insertBinding(
            Connection connection,
            long campaignId,
            long releaseId,
            CampaignArchiveDocument.ModuleReference module) throws SQLException {
        try (PreparedStatement statement = prepare(connection, INSERT_BINDING_SQL)) {
            statement.setLong(1, campaignId);
            statement.setLong(2, releaseId);
            statement.setString(3, module.moduleKey());
            statement.setString(4, module.releaseVersion());
            statement.setString(5, module.contentSha256());
            executeOne(statement, "campaign module binding");
        }
    }

    private static Map<String, Long> insertCharacters(
            Connection connection,
            long campaignId,
            long releaseId,
            CampaignArchiveDocument document) throws SQLException {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (CampaignArchiveDocument.CharacterState character : document.characters()) {
            try (PreparedStatement statement = prepareGenerated(connection, INSERT_CHARACTER_SQL)) {
                statement.setLong(1, campaignId);
                statement.setLong(2, releaseId);
                statement.setString(3, character.characterKey());
                statement.setString(4, character.characterType());
                statement.setString(5, character.characterName());
                statement.setString(6, character.characterStatus());
                statement.setString(7, document.module().moduleKey());
                statement.setString(8, document.module().releaseVersion());
                statement.setString(9, document.module().contentSha256());
                long id = executeInsert(statement, "character");
                if (ids.putIfAbsent(character.characterKey(), id) != null) {
                    throw invalidState("Duplicate character key reached persistence");
                }
            }
        }
        return Map.copyOf(ids);
    }

    private static void insertCharacterState(
            Connection connection,
            long releaseId,
            CampaignArchiveDocument document,
            Map<String, Long> characters) throws SQLException {
        for (CampaignArchiveDocument.FieldValue value : document.fields()) {
            try (PreparedStatement statement = prepare(connection, INSERT_FIELD_SQL)) {
                statement.setLong(1, characterId(characters, value.characterKey()));
                statement.setLong(2, releaseId);
                statement.setString(3, value.fieldKey());
                statement.setString(4, value.valueType());
                nullableString(statement, 5, value.textValue());
                nullableLong(statement, 6, value.integerValue());
                nullableDecimal(statement, 7, value.decimalValue());
                nullableBoolean(statement, 8, value.booleanValue());
                executeOne(statement, "character field");
            }
        }
        for (CampaignArchiveDocument.ClassLevel value : document.classLevels()) {
            try (PreparedStatement statement = prepare(connection, INSERT_CLASS_SQL)) {
                statement.setLong(1, characterId(characters, value.characterKey()));
                statement.setLong(2, releaseId);
                statement.setString(3, value.classKey());
                statement.setInt(4, value.level());
                executeOne(statement, "class level");
            }
        }
        insertProficiencies(connection, releaseId, document.skillProficiencies(), characters, true);
        insertProficiencies(connection, releaseId, document.saveProficiencies(), characters, false);
        for (CampaignArchiveDocument.ItemState item : document.items()) {
            try (PreparedStatement statement = prepare(connection, INSERT_ITEM_SQL)) {
                statement.setLong(1, characterId(characters, item.characterKey()));
                statement.setString(2, item.sourceKind());
                if ("MODULE".equals(item.sourceKind())) {
                    statement.setLong(3, releaseId);
                    statement.setString(4, item.itemKey());
                } else {
                    statement.setNull(3, Types.BIGINT);
                    statement.setNull(4, Types.VARCHAR);
                }
                statement.setString(5, item.itemName());
                statement.setString(6, item.itemDescription());
                statement.setInt(7, item.quantity());
                statement.setString(8, item.itemStatus());
                executeOne(statement, "item");
            }
        }
    }

    private static void insertProficiencies(
            Connection connection,
            long releaseId,
            List<CampaignArchiveDocument.Proficiency> values,
            Map<String, Long> characters,
            boolean skill) throws SQLException {
        String sql = skill ? INSERT_SKILL_SQL : INSERT_SAVE_SQL;
        for (CampaignArchiveDocument.Proficiency value : values) {
            try (PreparedStatement statement = prepare(connection, sql)) {
                statement.setLong(1, characterId(characters, value.characterKey()));
                statement.setLong(2, releaseId);
                statement.setString(3, value.targetKey());
                statement.setString(4, value.proficiencyKey());
                executeOne(statement, skill ? "skill proficiency" : "save proficiency");
            }
        }
    }

    private static void insertMaps(
            Connection connection,
            long campaignId,
            long releaseId,
            CampaignArchiveDocument document,
            Map<String, Long> characters) throws SQLException {
        for (CampaignArchiveDocument.MapState map : document.maps()) {
            long mapId;
            try (PreparedStatement statement = prepareGenerated(connection, INSERT_MAP_SQL)) {
                statement.setLong(1, campaignId);
                statement.setLong(2, releaseId);
                statement.setString(3, map.mapKey());
                statement.setString(4, map.mapType());
                mapId = executeInsert(statement, "map instance");
            }
            try (PreparedStatement statement = prepare(connection, INSERT_PARTY_SQL)) {
                statement.setLong(1, campaignId);
                statement.setLong(2, mapId);
                statement.setLong(3, releaseId);
                statement.setString(4, map.mapKey());
                statement.setString(5, map.partyNodeKey());
                executeOne(statement, "party position");
            }
            if (map.encounter() != null) {
                insertEncounter(
                        connection, campaignId, releaseId, mapId, map, characters);
            }
        }
    }

    private static void insertEncounter(
            Connection connection,
            long campaignId,
            long releaseId,
            long mapId,
            CampaignArchiveDocument.MapState map,
            Map<String, Long> characters) throws SQLException {
        CampaignArchiveDocument.Encounter encounter = map.encounter();
        long battleId;
        try (PreparedStatement statement = prepareGenerated(connection, INSERT_BATTLE_SQL)) {
            statement.setLong(1, campaignId);
            statement.setLong(2, mapId);
            statement.setLong(3, releaseId);
            statement.setString(4, map.mapKey());
            statement.setString(5, encounter.battleStatus());
            battleId = executeInsert(statement, "battle state");
        }
        for (CampaignArchiveDocument.Participant participant : encounter.participants()) {
            long characterId = characterId(characters, participant.characterKey());
            try (PreparedStatement statement = prepareGenerated(
                    connection, INSERT_PARTICIPANT_SQL)) {
                statement.setLong(1, battleId);
                statement.setLong(2, campaignId);
                statement.setLong(3, characterId);
                statement.setString(4, participant.faction());
                executeInsert(statement, "battle participant");
            }
            try (PreparedStatement statement = prepare(connection, INSERT_POSITION_SQL)) {
                statement.setLong(1, battleId);
                statement.setLong(2, campaignId);
                statement.setLong(3, campaignId);
                statement.setLong(4, mapId);
                statement.setLong(5, releaseId);
                statement.setString(6, map.mapKey());
                statement.setLong(7, characterId);
                statement.setString(8, participant.nodeKey());
                executeOne(statement, "entity position");
            }
        }
    }

    private static void insertEvents(
            Connection connection,
            long campaignId,
            long releaseId,
            CampaignArchiveDocument document,
            Map<String, Long> characters) throws SQLException {
        for (CampaignArchiveDocument.EventSnapshot event : document.recentEvents()) {
            long subjectId = event.subjectCharacterKey() == null
                    ? 0L : characterId(characters, event.subjectCharacterKey());
            long eventId;
            try (PreparedStatement statement = prepareGenerated(connection, INSERT_EVENT_SQL)) {
                statement.setLong(1, campaignId);
                statement.setLong(2, event.eventSequence());
                statement.setString(3, event.eventType());
                if (event.subjectCharacterKey() == null) {
                    statement.setNull(4, Types.BIGINT);
                } else {
                    statement.setLong(4, subjectId);
                }
                nullableString(statement, 5, event.eventText());
                eventId = executeInsert(statement, "game event");
            }
            if (event.check() != null) {
                insertCheck(
                        connection, eventId, campaignId, releaseId, subjectId, event.check());
            }
        }
    }

    private static void insertCheck(
            Connection connection,
            long eventId,
            long campaignId,
            long releaseId,
            long executorId,
            CampaignArchiveDocument.CheckSnapshot check) throws SQLException {
        if (executorId <= 0) throw invalidState("Check executor is missing");
        try (PreparedStatement statement = prepare(connection, INSERT_CHECK_SQL)) {
            statement.setLong(1, eventId);
            statement.setLong(2, campaignId);
            statement.setLong(3, releaseId);
            statement.setLong(4, executorId);
            nullableString(statement, 5, check.eventKey());
            statement.setString(6, check.checkKey());
            statement.setString(7, check.rollModeKey());
            nullableString(statement, 8, check.modifierSourceKey());
            nullableString(statement, 9, check.manualName());
            statement.setInt(10, check.modifierValue());
            statement.setInt(11, check.totalValue());
            statement.setInt(12, check.difficultyClass());
            statement.setString(13, check.checkResult());
            executeOne(statement, "check snapshot");
        }
    }

    private static void validateDocument(CampaignArchiveDocument document) {
        if (document == null || document.formatVersion() != 1
                || document.campaign() == null || document.module() == null
                || !canonicalUuidV4(document.campaign().campaignKey())
                || !List.of("ACTIVE", "ARCHIVED").contains(
                        document.campaign().campaignStatus())
                || document.characters() == null || document.fields() == null
                || document.classLevels() == null || document.skillProficiencies() == null
                || document.saveProficiencies() == null || document.items() == null
                || document.maps() == null || document.recentEvents() == null) {
            throw new IllegalArgumentException("Invalid validated campaign archive document");
        }
        CampaignArchiveReader.Status status = new CampaignArchiveReader().validate(document);
        if (status != CampaignArchiveReader.Status.READY) {
            throw new IllegalArgumentException(
                    "Archive document failed conflict preflight: " + status.name());
        }
    }

    private static void validateConfirmedCampaignKey(String campaignKey) {
        if (campaignKey != null && !canonicalUuidV4(campaignKey)) {
            throw new IllegalArgumentException("Invalid confirmed archive campaign key");
        }
    }

    private static void requireCallerTransaction(Connection connection) throws SQLException {
        if (connection == null || connection.getAutoCommit() || connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
            throw invalidState("Archive import requires a caller-owned serializable transaction");
        }
    }

    private static long characterId(Map<String, Long> characters, String key) throws SQLException {
        Long id = characters.get(key);
        if (id == null || id <= 0) throw invalidState("Archive character reference is unresolved");
        return id;
    }

    private static PreparedStatement prepare(Connection connection, String sql)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private static PreparedStatement prepareGenerated(Connection connection, String sql)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS);
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        return statement;
    }

    private static long executeInsert(PreparedStatement statement, String label)
            throws SQLException {
        executeOne(statement, label);
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw invalidState(label + " generated key is missing");
            long id = keys.getLong(1);
            if (keys.wasNull() || id <= 0 || keys.next()) {
                throw invalidState(label + " generated key is invalid");
            }
            return id;
        }
    }

    private static void executeOne(PreparedStatement statement, String label)
            throws SQLException {
        if (statement.executeUpdate() != 1) {
            throw invalidState(label + " was not written exactly once");
        }
    }

    private static long positiveLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value <= 0) throw invalidState("Invalid positive database id");
        return value;
    }

    private static long nonNegativeLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        if (result.wasNull() || value < 0) throw invalidState("Invalid non-negative value");
        return value;
    }

    private static String requiredString(ResultSet result, String column) throws SQLException {
        String value = result.getString(column);
        if (value == null || value.isBlank()) throw invalidState("Missing campaign lock value");
        return value;
    }

    private static void nullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static void nullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static void nullableDecimal(
            PreparedStatement statement, int index, java.math.BigDecimal value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.DECIMAL);
        else statement.setBigDecimal(index, value);
    }

    private static void nullableBoolean(PreparedStatement statement, int index, Boolean value)
            throws SQLException {
        if (value == null) statement.setNull(index, Types.BOOLEAN);
        else statement.setBoolean(index, value);
    }

    private static boolean canonicalUuidV4(String value) {
        if (value == null) return false;
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) && uuid.version() == 4 && uuid.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String childDelete(String table, String alias) {
        return "DELETE " + alias + " FROM " + table + " AS " + alias
                + " JOIN character_record AS character_root"
                + " ON character_root.id = " + alias + ".character_id"
                + " WHERE character_root.campaign_id = ?";
    }

    private static SQLException invalidState(String message) {
        return new SQLException(message);
    }

    private record CampaignRow(
            long id, String campaignKey, String status, long hostStateEpoch) {
        private CampaignRow {
            Objects.requireNonNull(campaignKey);
            Objects.requireNonNull(status);
        }
    }

    private record LockedCampaigns(CampaignRow target, CampaignRow active) {
    }
}
