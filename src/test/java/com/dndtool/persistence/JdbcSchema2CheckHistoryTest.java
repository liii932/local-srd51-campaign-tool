package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2CheckHistoryTest {
    private static final JdbcSchema2CharacterRuntimeRepository REPOSITORY = new JdbcSchema2CharacterRuntimeRepository();
    private static final Identity IDENTITY = Identity.random();
    private static final UUID CAMPAIGN = UUID.randomUUID(), CHARACTER = UUID.randomUUID(), PREPARATION = UUID.randomUUID();
    private static final RuleIdentity CLASS = rule("character.class", "class.guard");
    private static final RuleIdentity CHECK = rule("adventure.check", "check.srd2014");
    private static final RuleIdentity TOOL = rule("character.tool", "tool.smith_tools");
    private static final RuleIdentity SKILL = rule("character.proficiency", "proficiency.athletics");
    private static final RuleIdentity RESOURCE = rule("character.resource", "resource.energy");
    private static final Map<String, Integer> ABILITIES = Map.of("ability.strength", 15, "ability.dexterity", 14,
            "ability.constitution", 13, "ability.intelligence", 12, "ability.wisdom", 10, "ability.charisma", 8);

    @Test void typedRowsPreserveEveryInputDiceFactAndDirectRestoration() throws Exception {
        History expected = history(); var fixture = seeded();
        try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
            for (Event event : expected.events()) REPOSITORY.append(transaction.connection(), event);
            transaction.commit();
        }
        assertEquals(expected, REPOSITORY.read(fixture.connection, CHARACTER));
        assertEquals(expected.events().get(3), REPOSITORY.findOperation(fixture.connection, expected.events().get(3).operationId()));
        assertEquals(5, fixture.rows("s2_character_check").size());
        assertEquals(-1, fixture.rows("s2_character_check").getFirst().get("ability_modifier"));
        assertEquals(0, fixture.rows("s2_character_check").getFirst().get("total"));
        assertEquals("攀爬石墙 🧗", fixture.rows("s2_character_check").get(1).get("adjudication"));
        assertEquals(2, expected.events().get(3).checkResult().proficiencyBonus());
        assertTrue(fixture.rows("s2_character_event").stream().allMatch(row -> row.get("hit_die_roll") == null));
        var target = seeded();
        try (var transaction = BusinessWriteTransactions.openInternal(target.dataSource())) { REPOSITORY.restore(transaction.connection(), expected); transaction.commit(); }
        assertEquals(expected, REPOSITORY.read(target.connection, CHARACTER));
        assertEquals(1, target.commits);
    }

    @Test void savedDiceMissingExtraOrForgedResultsFailClosedOnReadback() throws Exception {
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations = List.of(
                f -> f.rows("s2_character_check").removeFirst(),
                f -> f.rows("s2_character_check").add(new LinkedHashMap<>(f.rows("s2_character_check").getFirst())),
                f -> f.rows("s2_character_check").getFirst().put("event_sequence", 1L),
                f -> f.rows("s2_character_check").getFirst().put("first_d20", 0),
                f -> f.rows("s2_character_check").getFirst().put("selected_d20", 2),
                f -> f.rows("s2_character_check").getFirst().put("ability_modifier", 0),
                f -> f.rows("s2_character_check").getFirst().put("success", 1),
                f -> f.rows("s2_character_check").get(2).put("second_d20", null),
                f -> f.rows("s2_character_check").get(2).put("proficiency_bonus", 4),
                f -> f.rows("s2_character_check").getFirst().put("adjudication", "e\u0301"),
                f -> f.rows("s2_character_check").getFirst().put("advantage", 2),
                f -> f.rows("s2_character_event_resource").getLast().put("current_value", 1L));
        for (var mutation : mutations) {
            var fixture = stored(); mutation.accept(fixture);
            assertThrows(SQLException.class, () -> REPOSITORY.read(fixture.connection, CHARACTER));
        }
        var orphan = seeded();
        var row = new LinkedHashMap<>(stored().rows("s2_character_check").getFirst()); orphan.rows("s2_character_check").add(row);
        assertThrows(SQLException.class, () -> REPOSITORY.read(orphan.connection, CHARACTER));
    }

    @Test void completeUnionUnicodeAndDerivedFactsAreValidatedBeforeAnyWrite() throws Exception {
        for (Runnable invalid : List.<Runnable>of(
                () -> input("ABILITY", TOOL, false, false, "理由"),
                () -> input("SKILL", TOOL, false, false, "理由"),
                () -> input("TOOL", CLASS, false, false, "理由"),
                () -> input("ABILITY", null, false, false, " "),
                () -> input("ABILITY", null, false, false, "e\u0301"),
                () -> input("ABILITY", null, false, false, "\ud800"),
                () -> input("ABILITY", null, false, false, "理由\u0080"),
                () -> input("ABILITY", null, false, false, "😀".repeat(501)),
                () -> new CheckInput(CHECK, "ABILITY", "ability.unknown", null, 10, false, false, "理由"),
                () -> new CheckInput(CHECK, "ABILITY", "ability.strength", null, 31, false, false, "理由"),
                () -> new CheckResult(20, null, 20, 2, 0, 99, true, false, false),
                () -> new Command("RESOURCE_SPENT", RESOURCE, 1, null, null, null, GrowthChoices.EMPTY, null, input("ABILITY", null, false, false, "理由"))))
            assertThrows(IllegalArgumentException.class, invalid::run);
        assertEquals(500, input("ABILITY", null, false, false, "😀".repeat(500)).adjudication().codePointCount(0, 1000));
        Event valid = history().events().get(3);
        for (Event invalid : List.of(
                new Event(valid.operationId(), valid.requestDigest(), valid.command(), 1, valid.before(), valid.after(), valid.checkResult()),
                new Event(valid.operationId(), valid.requestDigest(), valid.command(), null, valid.before(), valid.after()),
                new Event(valid.operationId(), valid.requestDigest(), valid.command(), null, valid.before(), valid.after(), new CheckResult(4, 17, 4, 2, 2, 8, false, true, false)),
                new Event(valid.operationId(), valid.requestDigest(), valid.command(), null, valid.before(), state(valid.after().version(), 1), valid.checkResult()),
                new Event(UUID.randomUUID(), "a".repeat(64), new Command("INITIALIZED", null, 0, null, null), null, null, state(0, 2), valid.checkResult()))) {
            var fixture = seeded();
            try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
                assertThrows(SQLException.class, () -> REPOSITORY.append(transaction.connection(), invalid)); transaction.rollback();
            }
            assertEquals(0, fixture.writes);
        }
        BusinessWriteTestSupport.assertRawConnectionRejected(c -> REPOSITORY.restore(c, history()));
    }

    @Test void everyHistoryWriteFailureRollsBackCheckAndCharacterFactsTogether() throws Exception {
        int writes = stored().writes;
        for (int point = 1; point <= writes; point++) {
            var fixture = seeded(); fixture.failWrite = point;
            try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) {
                assertThrows(SQLException.class, () -> REPOSITORY.restore(transaction.connection(), history())); transaction.rollback();
            }
            for (String table : List.of("s2_character_runtime", "s2_character_event", "s2_character_event_state", "s2_character_event_resource", "s2_character_check"))
                assertTrue(fixture.rows(table).isEmpty(), table + " retained at failure " + point);
            assertEquals(1, fixture.rows("s2_initial_plan").size()); assertEquals(0, fixture.commits);
        }
    }

    private static History history() {
        List<Event> events = new ArrayList<>(); State before = state(0, 2);
        events.add(new Event(UUID.randomUUID(), "a".repeat(64), new Command("INITIALIZED", null, 0, null, null), null, null, before));
        List<CheckInput> inputs = List.of(new CheckInput(CHECK, "ABILITY", "ability.charisma", null, 1, false, false, "说服守卫"),
                input("SKILL", SKILL, false, false, "攀爬石墙 🧗"), input("TOOL", TOOL, true, false, "锻造铰链"),
                input("TOOL", rule("character.tool", "tool.cook_utensils"), false, true, "制作晚餐"), input("ABILITY", null, true, true, "抬起闸门"));
        List<CheckResult> results = List.of(new CheckResult(1, null, 1, -1, 0, 0, false, false, false), new CheckResult(20, null, 20, 2, 2, 24, true, false, false),
                new CheckResult(4, 17, 17, 2, 2, 21, true, true, false), new CheckResult(19, 2, 2, 2, 0, 4, false, false, true), new CheckResult(1, null, 1, 2, 0, 3, false, true, true));
        for (int index = 0; index < inputs.size(); index++) {
            State after = state(index + 1, 2);
            events.add(new Event(UUID.randomUUID(), "a".repeat(64), new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, inputs.get(index)), null, before, after, results.get(index)));
            before = after;
        }
        return new History(before, events);
    }
    private static CheckInput input(String kind, RuleIdentity proficiency, boolean advantage, boolean disadvantage, String adjudication) {
        return new CheckInput(CHECK, kind, "ability.strength", proficiency, 10, advantage, disadvantage, adjudication);
    }
    private static State state(long version, long resourceCurrent) {
        List<ProficiencyState> grants = List.of(
                new ProficiencyState(new EntryIdentity(CLASS, "proficiency_grants", "base", "smith_one"), TOOL, 1),
                new ProficiencyState(new EntryIdentity(CLASS, "proficiency_grants", "base", "smith_two"), TOOL, 1),
                new ProficiencyState(new EntryIdentity(rule("character.class_progression", "progression.guard"), "starting_proficiencies", "base", "athletics"), SKILL, 1));
        return new State(IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, CLASS, ABILITIES, 1, 2, 11, 11, List.of(new ClassLevel(CLASS, 1)),
                List.of(new HitDicePool(10, 1, 1)), List.of(), List.of(), List.of(), grants, version, List.of(new ResourceState(RESOURCE, resourceCurrent, 2, false)), RestState.INITIAL);
    }
    private static Schema2RuntimeJdbcFixture stored() throws Exception {
        var fixture = seeded(); try (var transaction = BusinessWriteTransactions.openInternal(fixture.dataSource())) { REPOSITORY.restore(transaction.connection(), history()); transaction.commit(); } return fixture;
    }
    private static Schema2RuntimeJdbcFixture seeded() {
        var fixture = new Schema2RuntimeJdbcFixture();
        fixture.rows("s2_initial_plan").add(new LinkedHashMap<>(Map.of("operation_id", bytes(PREPARATION), "run_id", IDENTITY.runBytes(), "snapshot_id", IDENTITY.snapshotBytes(), "campaign_id", bytes(CAMPAIGN), "character_id", bytes(CHARACTER))));
        fixture.dataSource(); fixture.saveCommitted(); return fixture;
    }
    private static RuleIdentity rule(String type, String key) { return new RuleIdentity("base", type, key); }
    private static byte[] bytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
}
