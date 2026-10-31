package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2WizardFixtures.*;
import static com.dndtool.service.Schema2ArcaneRecoveryTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ArcaneRecoveryRulesTest {
    @Test void levelOneThroughFiveRecoverOnlySelectedExpendedSlotsWithRoundedUpBudget() {
        var material = material();
        for (int level = 1; level <= 5; level++) {
            State initial = learnedContext(material, UUID.randomUUID(), UUID.randomUUID(), level).character();
            int budget = (level + 1) / 2;
            State spent = spend(material, initial, 1, budget);
            State recovered = shortRest(material, spent, Map.of(1, budget));
            assertEquals(slot(initial, 1), slot(recovered, 1));
            assertEquals(0L, recovered.spellbook().lastArcaneRecoveryDay());
            assertEquals("SPENDING_HIT_DICE", recovered.rest().active().phase());
            assertEquals(spent.hitPoints(), recovered.hitPoints());
            assertEquals(spent.hitDicePools(), recovered.hitDicePools());
        }
        State fifth = learnedContext(material, UUID.randomUUID(), UUID.randomUUID(), 5).character();
        State spent = spend(material, spend(material, spend(material, fifth, 1, 2), 2, 1), 3, 1);
        State recovered = shortRest(material, spent, Map.of(1, 1, 2, 1));
        assertEquals(slot(spent, 1).current() + 1, slot(recovered, 1).current());
        assertEquals(slot(fifth, 2), slot(recovered, 2));
        assertEquals(slot(spent, 3), slot(recovered, 3));
        State third = shortRest(material, spend(material, fifth, 3, 1), Map.of(3, 1));
        assertEquals(slot(fifth, 3), slot(third, 3));
    }

    @Test void completedRestIsRequiredAndIllegalAllocationsDoNotConsumeDailyUse() {
        var material = material();
        State initial = learnedContext(material, UUID.randomUUID(), UUID.randomUUID(), 3).character();
        State spent = spend(material, spend(material, initial, 1, 3), 2, 1);
        assertCode("S2_REST_NOT_ACTIVE", () -> apply(material, spent, finish(Map.of(1, 1))));
        State started = apply(material, spent, start("SHORT_REST"));
        State early = apply(material, started, time("LIGHT", 59));
        assertCode("S2_REST_REQUIREMENTS", () -> apply(material, early, finish(Map.of(1, 1))));
        State ready = apply(material, early, time("LIGHT", 1));
        for (var choice : List.of(Map.of(1, 3), Map.of(1, 1, 2, 1), Map.of(2, 2), Map.of(3, 1), Map.of(4, 1), Map.of(5, 1)))
            assertCode("S2_ARCANE_RECOVERY_CHOICE", () -> apply(material, ready, finish(choice)));
        assertNull(ready.spellbook().lastArcaneRecoveryDay());
        State result = apply(material, ready, finish(Map.of(2, 1)));
        assertEquals(slot(initial, 2), slot(result, 2));
        assertCode("S2_REST_NOT_ACTIVE", () -> apply(material, result, finish(Map.of(1, 1))));
        State uninterrupted = apply(material, started, time("STRENUOUS", 1));
        State interrupted = apply(material, uninterrupted, time("LIGHT", 60));
        assertCode("S2_REST_REQUIREMENTS", () -> apply(material, interrupted, finish(Map.of(1, 1))));
        assertThrows(IllegalArgumentException.class, () -> finish(Map.of(6, 1)));
        assertThrows(IllegalArgumentException.class, () -> finish(Map.of(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> rest("REST_STARTED", new RestInput("SHORT_REST", null, 0, 0, Map.of(), Map.of(1, 1))));
        assertThrows(IllegalArgumentException.class, () -> new Command("REST_STARTED", null, 0, null, null));
    }

    @Test void emptyChoiceDeclinesBenefitAndLongRestCannotPretendToBeShortRest() {
        var material = material();
        State initial = learnedContext(material, UUID.randomUUID(), UUID.randomUUID(), 1).character();
        State spent = spend(material, initial, 1, 1);
        State declined = shortRest(material, spent, Map.of());
        assertEquals(slot(spent, 1), slot(declined, 1)); assertNull(declined.spellbook().lastArcaneRecoveryDay());
        State ended = apply(material, declined, end());
        State recovered = shortRest(material, ended, Map.of(1, 1)); assertEquals(0L, recovered.spellbook().lastArcaneRecoveryDay());
        State longStarted = apply(material, spent, start("LONG_REST"));
        State longReady = apply(material, longStarted, time("SLEEP", 480));
        assertCode("S2_ARCANE_RECOVERY_REST_REQUIRED", () -> apply(material, longReady, finish(Map.of(1, 1))));
    }

    @Test void campaignDaysResetAtExactMidnightAndLongRestDoesNotGrantAnotherUse() {
        var material = material();
        State initial = learnedContext(material, UUID.randomUUID(), UUID.randomUUID(), 1).character();
        State used = shortRest(material, spend(material, initial, 1, 1), Map.of(1, 1));
        State ended = apply(material, used, end());
        State rested = Schema2WizardFixtures.longRest(material, ended);
        assertEquals(0L, rested.spellbook().lastArcaneRecoveryDay());
        State spent = spend(material, rested, 1, 1);
        State started = apply(material, spent, start("SHORT_REST"));
        State ready = apply(material, started, time("LIGHT", 899));
        assertEquals(1439, ready.rest().minute());
        assertCode("S2_ARCANE_RECOVERY_DAILY_LIMIT", () -> apply(material, ready, finish(Map.of(1, 1))));
        State midnight = apply(material, ready, time("LIGHT", 1));
        State recovered = apply(material, midnight, finish(Map.of(1, 1)));
        assertEquals(1440, recovered.rest().minute()); assertEquals(1L, recovered.spellbook().lastArcaneRecoveryDay());
    }

    @Test void blockedMetadataMissingKnowledgeAndForgedDailyFactsFailClosed() {
        var blocked = Schema2WizardFixtures.projection();
        State old = learnedContext(blocked, UUID.randomUUID(), UUID.randomUUID(), 1).character();
        assertCode("S2_ARCANE_RECOVERY_UNSUPPORTED", () -> shortRest(blocked, spend(blocked, old, 1, 1), Map.of(1, 1)));
        var material = material();
        State initial = initialContext(material, UUID.randomUUID(), UUID.randomUUID()).character();
        assertCode("S2_ARCANE_RECOVERY_UNSUPPORTED", () -> shortRest(material, spend(material, initial, 1, 1), Map.of(1, 1)));
        State known = learnedContext(material, UUID.randomUUID(), UUID.randomUUID(), 1).character();
        State forged = JdbcSchema2CharacterRuntimeRepository.withSpellbook(known, known.version(),
                JdbcSchema2CharacterRuntimeRepository.withArcaneRecoveryDay(known.spellbook(), 1L));
        assertCode("S2_ARCANE_RECOVERY_STATE", () -> new Schema2WizardSpellbookRules().validateState(material, forged));
    }

    @Test void missingFrozenFeatureAndUnknownAuthorModeCannotAcquireRecoveryByName() {
        var files = Schema2ArcaneRecoveryTestFixture.packages();
        var rows = Schema2GrantFixtures.json(files.get(1).get("rules.json")).getAsJsonArray();
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index).getAsJsonObject();
            if (row.has("execution_algorithm") && row.get("execution_algorithm").getAsString().equals(Schema2ArcaneRecoveryRules.ALGORITHM)) {
                row.addProperty("execution_mode", "UNKNOWN");
                files.get(1).put("rules.json", rows.toString().getBytes(StandardCharsets.UTF_8));
                assertThrows(IllegalArgumentException.class, () -> ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files)));
                rows.remove(index); break;
            }
        }
        files.get(1).put("rules.json", rows.toString().getBytes(StandardCharsets.UTF_8));
        var material = ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
        State known = learnedContext(material, UUID.randomUUID(), UUID.randomUUID(), 1).character();
        assertCode("S2_ARCANE_RECOVERY_UNSUPPORTED", () -> shortRest(material, spend(material, known, 1, 1), Map.of(1, 1)));
    }

    private static void assertCode(String code, Runnable action) {
        assertEquals(code, assertThrows(IllegalArgumentException.class, action::run).getMessage());
    }
}
