package com.dndtool.persistence;

import static com.dndtool.service.CampaignArchiveV2CharacterState.*;
import static org.junit.jupiter.api.Assertions.*;

import com.dndtool.service.CharacterProficiencyArchiveTestData;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JdbcCampaignArchiveV2CharacterStateRepositoryTest {
    private static final String CHARACTER = "11111111-1111-4111-8111-111111111111";

    @Test
    void exportsActualGrantRowsAndCreationClosureAgainstIndependentFacts() throws Exception {
        Fixture fixture = new Fixture();
        var state = new JdbcCampaignArchiveV2CharacterStateRepository().read(
                fixture.connection(), 7, 9, CharacterProficiencyArchiveTestData.catalog());
        assertEquals(7, state.eventTail());
        assertEquals(List.of(new StateEvent(3, "LEVEL_ONE_CHARACTER_CREATED", CHARACTER)),
                state.stateEvents());
        assertEquals(List.of(new ClassLevel(CHARACTER, "class.fighter", 1)), state.classLevels());
        assertEquals(1, state.creationSnapshots().size());
        assertEquals("race.human", state.creationSnapshots().getFirst().raceKey());
        assertEquals("b".repeat(64), state.creationSnapshots().getFirst().requestDigestSha256());
        assertEquals(4, state.proficiencyGrants().size());
        assertEquals(CharacterProficiencyArchiveTestData.grants().stream().sorted(
                com.dndtool.module.CharacterProficiencyGrant.ORDER).toList(),
                state.proficiencyGrants().stream().map(ProficiencyGrant::grant).toList());
        assertEquals(List.of("language.common", "language.celestial", "tool.smith_tools"),
                state.creationSelections().stream().map(CreationSelection::selectionKey).toList());
        assertTrue(fixture.queries.stream().anyMatch(sql -> sql.contains("character_proficiency_grant_v2")));
        assertFalse(fixture.queries.stream().anyMatch(sql -> sql.contains("LIMIT 50")));
    }

    @Test
    void refusesMissingDuplicateSourceWrongSubjectAndNonConsistentTransaction() throws Exception {
        Fixture missing = new Fixture();
        missing.omitRaceGrant = true;
        assertThrows(SQLException.class, () -> new JdbcCampaignArchiveV2CharacterStateRepository()
                .read(missing.connection(), 7, 9, CharacterProficiencyArchiveTestData.catalog()));
        Fixture subject = new Fixture();
        subject.wrongSubject = true;
        assertThrows(SQLException.class, () -> new JdbcCampaignArchiveV2CharacterStateRepository()
                .read(subject.connection(), 7, 9, CharacterProficiencyArchiveTestData.catalog()));
        Fixture inconsistent = new Fixture();
        inconsistent.isolation = Connection.TRANSACTION_READ_COMMITTED;
        assertThrows(SQLException.class, () -> new JdbcCampaignArchiveV2CharacterStateRepository()
                .read(inconsistent.connection(), 7, 9, CharacterProficiencyArchiveTestData.catalog()));
        assertTrue(inconsistent.queries.isEmpty());
    }

    @Test
    void refusesAsiOrAbilityChangeBeforeProducingAnyCharacterSlice() throws Exception {
        for (boolean choiceOnly : List.of(true, false)) {
            Fixture fixture = new Fixture();
            fixture.hasAsiChoice = choiceOnly;
            fixture.hasAbilityChange = !choiceOnly;
            SQLException failure = assertThrows(SQLException.class, () ->
                    new JdbcCampaignArchiveV2CharacterStateRepository().read(
                            fixture.connection(), 7, 9, CharacterProficiencyArchiveTestData.catalog()));
            assertTrue(failure.getMessage().contains("ASI"));
            String guard = fixture.queries.getLast();
            assertTrue(guard.contains("character_ability_score_change_v2"));
            assertTrue(guard.contains("character_advancement_choice_v2"));
            assertTrue(guard.contains("choice_type = 'ABILITY_SCORE_IMPROVEMENT'"));
            assertFalse(fixture.queries.stream().anyMatch(sql ->
                    sql.contains("FROM character_creation_snapshot_v2 AS state")));
        }
    }

    private static final class Fixture {
        boolean omitRaceGrant;
        boolean wrongSubject;
        int isolation = Connection.TRANSACTION_REPEATABLE_READ;
        boolean hasAsiChoice;
        boolean hasAbilityChange;
        final List<String> queries = new ArrayList<>();

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> false;
                        case "getTransactionIsolation" -> isolation;
                        case "prepareStatement" -> statement((String) args[0]);
                        case "commit", "rollback" -> throw new AssertionError("Caller owns transaction");
                        default -> value(method.getReturnType());
                    });
        }

        private PreparedStatement statement(String sql) {
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "executeQuery" -> { queries.add(sql); yield resultSet(rows(sql)); }
                        case "executeUpdate" -> throw new AssertionError("Export must not write");
                        default -> value(method.getReturnType());
                    });
        }

        private List<Map<String, Object>> rows(String sql) {
            if (sql.contains("FROM module_release")) return List.of(Map.of(
                    "module_key", "dnd5e2014_srd51_se", "release_version", "1",
                    "canonical_format_version", 2, "hash_algorithm", "SHA-256",
                    "content_sha256", "a".repeat(64), "release_status", "DRAFT"));
            if (sql.contains("FROM character_record WHERE campaign_id")) return List.of(Map.of(
                    "id", 11L, "character_key", CHARACTER, "module_release_id", 9L));
            if (sql.contains("SELECT internal_event_tail FROM campaign")) {
                return List.of(Map.of("internal_event_tail", 7L));
            }
            if (sql.contains("SELECT 1 AS unsupported_state")) {
                return hasAsiChoice || hasAbilityChange ? List.of(Map.of("unsupported_state", 1)) : List.of();
            }
            if (sql.contains("FROM character_creation_snapshot_v2 AS state")) {
                Map<String, Object> row = new HashMap<>();
                row.put("character_key", CHARACTER);
                row.put("event_sequence", 3L);
                row.put("event_type", "LEVEL_ONE_CHARACTER_CREATED");
                row.put("event_subject", wrongSubject ? "22222222-2222-4222-8222-222222222222" : CHARACTER);
                row.put("preview_digest_sha256", "a".repeat(64));
                row.put("request_digest_sha256", "b".repeat(64));
                row.put("ability_method_key", "ability.standard_array_v1");
                row.put("race_key", "race.human");
                row.put("background_key", "background.acolyte");
                row.put("class_key", "class.fighter");
                row.put("maximum_hit_points", 10);
                for (String ability : List.of("strength", "dexterity", "constitution",
                        "intelligence", "wisdom", "charisma")) {
                    row.put("base_" + ability, 10);
                    row.put("final_" + ability, 10);
                }
                return List.of(row);
            }
            if (sql.contains("FROM character_creation_selection_v2 AS state")) return List.of(
                    selection("LANGUAGE", 1, "language.common"),
                    selection("LANGUAGE", 2, "language.celestial"), selection("TOOL", 1, "tool.smith_tools"));
            if (sql.contains("FROM character_class_level_v2 AS state")) return List.of(Map.of(
                    "character_key", CHARACTER, "class_key", "class.fighter", "class_level", 1));
            if (sql.contains("FROM character_proficiency_grant_v2")) {
                return CharacterProficiencyArchiveTestData.grants().stream()
                        .filter(grant -> !omitRaceGrant || !grant.sourceType().equals("character.race"))
                        .map(grant -> Map.<String, Object>of("category", grant.category().name(),
                                "source_module_key", grant.sourceModuleKey(), "source_type", grant.sourceType(),
                                "source_key", grant.sourceKey(), "grant_slot_key", grant.grantSlotKey(),
                                "grant_kind", grant.grantKind().name(), "target_module_key", grant.targetModuleKey(),
                                "proficiency_type", grant.category().catalogType(), "proficiency_key", grant.proficiencyKey(),
                                "is_suppressed", 0)).toList();
            }
            return List.of();
        }

        private static Map<String, Object> selection(String kind, int order, String key) {
            return Map.of("character_key", CHARACTER, "selection_kind", kind,
                    "selection_order", order, "selection_key", key);
        }

        private ResultSet resultSet(List<Map<String, Object>> rows) {
            int[] index = {-1};
            boolean[] wasNull = {false};
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {ResultSet.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "next" -> ++index[0] < rows.size();
                        case "wasNull" -> wasNull[0];
                        case "getString", "getLong", "getInt" -> {
                            Object cell = rows.get(index[0]).get(args[0]);
                            wasNull[0] = cell == null;
                            yield switch (method.getName()) {
                                case "getString" -> cell == null ? null : cell.toString();
                                case "getLong" -> cell == null ? 0L : ((Number) cell).longValue();
                                default -> cell == null ? 0 : ((Number) cell).intValue();
                            };
                        }
                        default -> value(method.getReturnType());
                    });
        }

        private static Object value(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
