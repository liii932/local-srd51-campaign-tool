package com.dndtool.service;

import com.dndtool.module.CharacterProficiencyGrant.GrantKind;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.persistence.Schema2RuntimeJdbcFixture;
import com.dndtool.persistence.Schema2SourceReadFixture;
import com.dndtool.service.Schema2InitialProficiencyRules.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original ZIP through real repositories and planner over SQL proxies; no MySQL or run qualification. */
class Schema2InitialCharacterPlanIntegrationTest {
    private static final String BASE = Schema2GrantFixtures.BASE;
    private static final Identity IDENTITY = new Identity(UUID.fromString("00112233-4455-4677-8899-aabbccddeeff"),
            UUID.fromString("ffeeddcc-bbaa-4988-8766-554433221100"));
    private static final UUID OPERATION = UUID.fromString("01872103-0345-4abc-8def-0123456789ab");
    private static final RuleIdentity RACE = rule("character.race", "race.human");
    private static final RuleIdentity SUBRACE = rule("character.subrace", "subrace.north");
    private static final RuleIdentity BACKGROUND = rule("character.background", "background.sailor");
    private static final RuleIdentity CLASS = rule("character.class", "class.scholar");
    private static final RuleIdentity COMMON = rule("character.language", "language.common");
    private static final RuleIdentity ELVISH = rule("character.language", "language.elvish");
    private static final RuleIdentity SMITH = rule("character.tool", "tool.smith_tools");
    private static final Sources SOURCES = new Sources(RACE, SUBRACE, BACKGROUND, CLASS);
    private static final Request REQUEST = new Request(SOURCES, Map.of(
            slot(RACE, "extra_languages"), List.of(ELVISH), slot(BACKGROUND, "trade_tools"), List.of(SMITH)));

    @Test void originalAuthorBytesReachTheDefaultPlannerOnlyThroughVerifiedTypedSourceAndRuntimeRows() throws Exception {
        var fixture = new Fixture();
        fixture.runtime.beforeAcquire = () -> {
            assertEquals(1, fixture.source.commitCount());
            assertEquals(1, fixture.source.closeCount());
        };
        var transferred = fixture.transfer();
        assertEquals(Schema2SnapshotTransferService.Status.COMMITTED, transferred.status(), transferred.code());
        var actual = transferred.snapshot().projection();
        assertArrayEquals(Schema2GrantFixtures.resource("expected/configuration.json"), actual.configurationJson());
        assertArrayEquals(Schema2GrantFixtures.resource("expected/final-rules.json"), actual.finalRulesJson());
        assertArrayEquals(Schema2GrantFixtures.resource("expected/chains.json"), actual.chainsJson());
        var expected = Schema2GrantFixtures.json(Schema2GrantFixtures.resource("expected/digests.json")).getAsJsonObject();
        assertEquals(expected.get("config").getAsString(), actual.compositionSha256());
        assertEquals(expected.get("rules").getAsString(), actual.finalRulesSha256());
        assertEquals(expected.get("chain").getAsString(), actual.chainSha256());
        assertEquals(6, fixture.runtime.rows("s2_runtime_character_source").size());
        assertEquals(9, fixture.runtime.rows("s2_runtime_proficiency_grant").size());

        fixture.source.unavailable(true);
        int sourceAcquisitions = fixture.source.acquisitionCount(), writes = fixture.runtime.writes;
        int commits = fixture.runtime.commits, rollbacks = fixture.runtime.rollbacks;
        try (Connection reader = fixture.runtime.dataSource().getConnection()) {
            reader.setReadOnly(true);
            reader.setAutoCommit(false);
            var service = new Schema2InitialCharacterPlanService();
            var binding = Schema2RulesBinding.from(actual);
            var plan = service.prepare(reader, IDENTITY, binding, REQUEST);
            assertEquals(SOURCES, plan.sources());
            assertEquals(REQUEST.choices(), plan.choices());
            assertEquals(8, plan.slots().size());
            // Hand-authored expected facts; neither planner nor compositor builds this oracle.
            assertEquals(List.of(
                    fixed(BACKGROUND, "shared_language", GrantCategory.LANGUAGE, COMMON, false),
                    new GrantFact(slot(BACKGROUND, "trade_tools"), GrantCategory.TOOL, GrantKind.CHOICE, SMITH, false),
                    fixed(CLASS, "disabled_tool", GrantCategory.TOOL, rule("character.tool", "tool.herbalism"), true),
                    fixed(CLASS, "lost_language", GrantCategory.LANGUAGE, rule("character.language", "language.dwarvish"), true),
                    new GrantFact(slot(RACE, "extra_languages"), GrantCategory.LANGUAGE, GrantKind.CHOICE, ELVISH, false),
                    fixed(RACE, "native_language", GrantCategory.LANGUAGE, COMMON, false),
                    new GrantFact(new EntryIdentity(RACE, "proficiency_grants", Schema2GrantFixtures.EXT, "extension_language"),
                            GrantCategory.LANGUAGE, GrantKind.FIXED,
                            new RuleIdentity(Schema2GrantFixtures.EXT, "character.language", "language.common"), false),
                    fixed(SUBRACE, "instrument", GrantCategory.TOOL, rule("character.tool", "tool.flute"), false)),
                    plan.grants());
            assertEquals(plan, service.validate(reader, IDENTITY, binding, plan));
            assertTrue(reader.isReadOnly());
            assertFalse(reader.getAutoCommit());
            assertEquals(commits, fixture.runtime.commits);
            assertEquals(rollbacks, fixture.runtime.rollbacks);
            assertEquals(writes, fixture.runtime.writes);
            assertEquals(sourceAcquisitions, fixture.source.acquisitionCount());
            reader.rollback();
        }
    }

    @Test void missingTypedAuthorGrantStopsBeforeRuntimeAcquisition() {
        var fixture = new Fixture();
        assertFalse(fixture.source.rows().get("rule_schema2_proficiency_grant").isEmpty());
        fixture.source.rows().get("rule_schema2_proficiency_grant").removeFirst();
        var result = fixture.transfer();
        assertEquals(Schema2SnapshotTransferService.Status.NOT_EXECUTED, result.status());
        assertNull(result.snapshot());
        assertEquals(0, fixture.runtime.acquisitions);
        assertEquals(0, fixture.runtime.writes);
    }

    @Test void alteredRuntimeGrantCannotPublishOrRevalidateAnInitialPlan() throws Exception {
        var fixture = new Fixture();
        var result = fixture.transfer();
        assertEquals(Schema2SnapshotTransferService.Status.COMMITTED, result.status(), result.code());
        var binding = Schema2RulesBinding.from(result.snapshot().projection());
        var service = new Schema2InitialCharacterPlanService();
        try (Connection reader = fixture.runtime.dataSource().getConnection()) {
            reader.setReadOnly(true);
            reader.setAutoCommit(false);
            var original = service.prepare(reader, IDENTITY, binding, REQUEST);
            var row = fixture.runtime.rows("s2_runtime_proficiency_grant").stream()
                    .filter(r -> r.get("grant_count") != null).findFirst().orElseThrow();
            row.put("grant_count", 16);
            int writes = fixture.runtime.writes, commits = fixture.runtime.commits;
            assertThrows(SQLException.class, () -> service.prepare(reader, IDENTITY, binding, REQUEST));
            assertThrows(SQLException.class, () -> service.validate(reader, IDENTITY, binding, original));
            assertEquals(writes, fixture.runtime.writes);
            assertEquals(commits, fixture.runtime.commits);
            reader.rollback();
        }
    }

    private static GrantFact fixed(RuleIdentity source, String key, GrantCategory category, RuleIdentity target, boolean suppressed) {
        return new GrantFact(slot(source, key), category, GrantKind.FIXED, target, suppressed);
    }
    private static RuleIdentity rule(String type, String key) { return new RuleIdentity(BASE, type, key); }
    private static EntryIdentity slot(RuleIdentity source, String key) {
        return new EntryIdentity(source, "proficiency_grants", BASE, key);
    }
    private static final class Fixture {
        final Schema2SourceReadFixture source = new Schema2SourceReadFixture(Schema2GrantFixtures.fixedArtifacts());
        final Schema2RuntimeJdbcFixture runtime = new Schema2RuntimeJdbcFixture();
        final Schema2SnapshotTransferService service = new Schema2SnapshotTransferService(source.dataSource(), runtime.dataSource());
        Schema2SnapshotTransferService.Result transfer() {
            return service.transfer(new Schema2SnapshotTransferService.Request(OPERATION, IDENTITY, source.selections(), null));
        }
    }
}
