package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.service.Schema2CharacterRuntimeService.*;
import com.dndtool.testing.IsolatedJvmTest;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real preparation-to-check execution and direct recovery. JDBC fixture does not claim MySQL evidence. */
class Schema2CharacterCheckServiceTest {
    @Test void preparedInitializedSkillToolAndAbilityFactsSurviveSourceLossReplayAndDirectRecovery() throws Exception {
        var fixture = new Schema2CheckTestFixture(); fixture.initialize(); fixture.prepareTools(); fixture.source.unavailable(true);
        fixture.nextRolls.addAll(List.of(4, 18));
        Request skill = fixture.request(check("SKILL", "ability.strength", ATHLETICS, 22, true, false));
        var resolved = fixture.execute(skill); committed(resolved);
        assertEquals(new CheckResult(4, 18, 18, 2, 2, 22, true, true, false), resolved.event().checkResult());
        assertNull(resolved.event().hitDieRoll()); assertEquals(2, fixture.rolls);
        fixture.nextRolls.add(1);
        var tool = fixture.commit(check("TOOL", "ability.wisdom", TOOL, 0, true, true));
        assertEquals(new CheckResult(1, null, 1, -1, 2, 2, true, true, true), tool.event().checkResult());
        fixture.nextRolls.add(20);
        assertFalse(fixture.commit(check("ABILITY", "ability.wisdom", null, 30, false, false)).event().checkResult().success());
        var beforeCheck = resolved.event().before(); var afterCheck = resolved.event().after();
        assertEquals(beforeCheck.version() + 1, afterCheck.version());
        assertEquals(beforeCheck.hitPoints(), afterCheck.hitPoints()); assertEquals(beforeCheck.maximumHitPoints(), afterCheck.maximumHitPoints());
        assertEquals(beforeCheck.rest(), afterCheck.rest()); assertEquals(beforeCheck.hitDicePools(), afterCheck.hitDicePools());
        assertEquals(beforeCheck.resources(), afterCheck.resources()); assertEquals(beforeCheck.proficiencies(), afterCheck.proficiencies());
        int writes = fixture.runtime.writes;
        assertEquals(Status.REPLAYED, fixture.service.execute(skill).status());
        assertEquals(resolved.event(), fixture.service.inspect(skill).event());
        assertEquals(writes, fixture.runtime.writes); assertEquals(4, fixture.rolls);
        History history = fixture.service.read(CHARACTER);
        var target = new Schema2CheckTestFixture(); target.source.unavailable(true);
        committed(target.service.restore(history)); assertEquals(history, target.service.read(CHARACTER));
        assertEquals(0, target.rolls); assertEquals(Status.REPLAYED, target.service.inspectRestore(history).status());
        assertTrue(target.runtime.rows("campaign").isEmpty());
    }

    @Test void staleRuleSourceCommandAndOperationMismatchesRejectBeforeAnyDiceOrWrites() throws Exception {
        var fixture = new Schema2CheckTestFixture(); fixture.initialize();
        Request valid = fixture.request(check("SKILL", "ability.strength", ATHLETICS, 10, false, false));
        committed(fixture.execute(valid)); int writes = fixture.runtime.writes;
        var stale = new Request(UUID.randomUUID(), IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, fixture.binding, 0, valid.command(), Map.of());
        assertEquals("S2_CHARACTER_STALE_VERSION", fixture.service.execute(stale).code());
        var conflict = new Request(valid.operationId(), IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, fixture.binding, 0,
                check("SKILL", "ability.strength", ATHLETICS, 11, false, false), Map.of());
        assertEquals("S2_CHARACTER_OPERATION_CONFLICT", fixture.service.execute(conflict).code());
        var badBinding = new Schema2RulesBinding(fixture.binding.canonicalProfile(), fixture.binding.compositionAlgorithm(),
                fixture.binding.members(), "0".repeat(64), fixture.binding.finalRulesSha256(), fixture.binding.chainSha256());
        var mismatch = new Request(UUID.randomUUID(), IDENTITY, CAMPAIGN, CHARACTER, PREPARATION, badBinding,
                fixture.state.version(), valid.command(), Map.of());
        assertEquals("S2_RULES_BINDING_MISMATCH", fixture.service.execute(mismatch).code());
        var badRule = new CheckInput(new Schema2CompositionKernel.RuleIdentity(EXT, "adventure.check", "check.srd2014"),
                "ABILITY", "ability.strength", null, 10, false, false, "DM");
        assertEquals("S2_CHECK_RULE_UNSUPPORTED", fixture.service.execute(fixture.request(check(badRule))).code());
        assertEquals("S2_CHECK_SKILL_UNSUPPORTED", fixture.service.execute(fixture.request(check("SKILL", "ability.dexterity", ATHLETICS, 10, false, false))).code());
        assertEquals(writes, fixture.runtime.writes); assertEquals(1, fixture.rolls);
    }

    @Test void missingDisabledRulesAcquiredFeaturesAndActiveRestFailBeforeDiceOrWrites() throws Exception {
        for (String mode : List.of("missing", "disabled", "features", "normal")) {
            var fixture = new Schema2CheckTestFixture(mode); fixture.initialize();
            if (mode.equals("normal")) fixture.commit(new Command("REST_STARTED", null, 0, null, null,
                    null, GrowthChoices.EMPTY, new RestInput("SHORT_REST", null, 0, 0, Map.of())));
            int writes = fixture.runtime.writes;
            var result = fixture.service.execute(fixture.request(check("ABILITY", "ability.strength", null, 10, false, false)));
            assertEquals(Status.REJECTED, result.status(), result.code());
            assertEquals(mode.equals("features") ? "S2_CHECK_EFFECT_UNSUPPORTED" : mode.equals("normal") ? "S2_CHECK_REST_ACTIVE" : "S2_CHECK_RULE_UNSUPPORTED", result.code());
            assertEquals(writes, fixture.runtime.writes); assertEquals(0, fixture.rolls);
        }
    }

    @Test void directRestoreRejectsTamperedInputResultAndStateWithoutDrawingDice() throws Exception {
        var fixture = new Schema2CheckTestFixture(); fixture.initialize();
        fixture.commit(check("SKILL", "ability.strength", ATHLETICS, 10, false, false));
        History history = fixture.service.read(CHARACTER); Event last = history.events().getLast();
        var target = new Schema2CheckTestFixture(); int writes = target.runtime.writes;
        var changedCommand = check("SKILL", "ability.strength", ATHLETICS, 11, false, false);
        var result = last.checkResult();
        for (Event changed : List.of(new Event(last.operationId(), last.requestDigest(), changedCommand, null, last.before(), last.after(), result),
                new Event(last.operationId(), last.requestDigest(), last.command(), null, last.before(), last.after(),
                        new CheckResult(result.firstD20(), null, result.selectedD20(), result.abilityModifier(), result.proficiencyBonus(), result.total(), !result.success(), result.effectiveAdvantage(), result.effectiveDisadvantage())))) {
            var events = new ArrayList<>(history.events()); events.set(events.size() - 1, changed);
            assertEquals("S2_CHARACTER_RESTORE_INVALID", target.service.restore(new History(history.current(), events)).code());
        }
        assertEquals(writes, target.runtime.writes); assertEquals(0, target.rolls); assertFalse(BusinessWriteTransactions.isBlocked());
    }

    @Test void failedTypedCheckInsertRollsBackWholeEventAndFreezesFurtherWrites() throws Exception {
        IsolatedJvmTest.run(FaultProbe.class, "rollback");
    }
    @Test void malformedRetainedFactsRejectBeforeRandomnessOrWrites() throws Exception {
        IsolatedJvmTest.run(FaultProbe.class, "corrupt");
    }
    @Test void lostCheckCommitResponseUsesOnlyOriginalReadOnlyFacts() throws Exception {
        for (String outcome : List.of("COMMITTED", "ROLLED_BACK")) IsolatedJvmTest.run(FaultProbe.class, "unknown", outcome);
    }

    public static final class FaultProbe {
        public static void main(String[] args) throws Exception {
            var fixture = new Schema2CheckTestFixture(); fixture.initialize(); fixture.prepareTools();
            Request request = fixture.request(check("TOOL", "ability.dexterity", TOOL, 12, true, false));
            History before = fixture.service.read(CHARACTER);
            if (args[0].equals("unknown")) {
                fixture.runtime.lostCommit = Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);
                var result = fixture.service.execute(request);
                assertEquals(Status.UNKNOWN, result.status(), result.code()); assertNull(result.event()); assertEquals(2, fixture.rolls);
                fixture.source.unavailable(true); int writes = fixture.runtime.writes;
                assertEquals(args[1].equals("COMMITTED") ? Status.REPLAYED : Status.NOT_COMMITTED, fixture.service.inspect(request).status());
                assertEquals(writes, fixture.runtime.writes); assertEquals(2, fixture.rolls); assertTrue(BusinessWriteTransactions.isBlocked());
                if (args[1].equals("ROLLED_BACK")) assertEquals(Status.UNKNOWN,
                        new Schema2CharacterRuntimeService(fixture.runtime.dataSource()).inspect(request).status());
            } else if (args[0].equals("corrupt")) {
                committed(fixture.execute(request));
                fixture.runtime.rows("s2_character_check").getFirst().put("total", 999); fixture.runtime.saveCommitted();
                int writes = fixture.runtime.writes;
                assertEquals(Status.REJECTED, fixture.service.execute(fixture.request(request.command())).status());
                assertEquals(writes, fixture.runtime.writes); assertEquals(2, fixture.rolls); assertTrue(BusinessWriteTransactions.isBlocked());
            } else {
                fixture.runtime.afterWrite = db -> { if (!db.rows("s2_character_check").isEmpty()) throw new IllegalStateException("after typed result insert"); };
                assertEquals(Status.REJECTED, fixture.service.execute(request).status());
                assertEquals(before, fixture.service.read(CHARACTER)); assertTrue(fixture.runtime.rows("s2_character_check").isEmpty()); assertEquals(2, fixture.rolls);
                assertTrue(BusinessWriteTransactions.isBlocked()); int writes = fixture.runtime.writes;
                assertEquals(Status.REJECTED, fixture.service.execute(request).status());
                assertEquals(writes, fixture.runtime.writes); assertEquals(2, fixture.rolls);
            }
        }
    }
    private static void committed(Result result) { assertEquals(Status.COMMITTED, result.status(), result.code()); }
}
