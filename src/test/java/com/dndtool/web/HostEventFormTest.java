package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class HostEventFormTest {
    private static final Path HOST_VIEW =
            Path.of("src/main/webapp/WEB-INF/views/host.jsp");
    private static final Path HOST_SCRIPT =
            Path.of("src/main/webapp/host/assets/host-event.js");

    @Test
    void hostPageContainsTheCompleteBoundedEventForm() throws Exception {
        String view = Files.readString(HOST_VIEW, StandardCharsets.UTF_8);

        assertTrue(view.contains("/host/assets/host-event.js"));
        for (String id : List.of(
                "host-event-form",
                "host-executor",
                "host-executor-version",
                "host-check-type",
                "host-source",
                "host-manual-name",
                "host-manual-modifier",
                "host-roll-mode",
                "host-dc",
                "host-targets",
                "host-event-result")) {
            assertTrue(view.contains("id=\"" + id + "\""), id);
        }
        for (String checkType : List.of("ABILITY", "SKILL", "SAVING_THROW", "MANUAL")) {
            assertTrue(view.contains("value=\"" + checkType + "\""), checkType);
        }
        for (String rollMode : List.of(
                "roll.normal", "roll.advantage", "roll.disadvantage")) {
            assertTrue(view.contains("value=\"" + rollMode + "\""), rollMode);
        }
    }

    @Test
    void bothBranchesExposeOnlyTheFiveFrozenEffectKeys() throws Exception {
        String view = Files.readString(HOST_VIEW, StandardCharsets.UTF_8);

        for (String effectKey : List.of(
                "effect.adjust_current_hp",
                "effect.grant_module_item",
                "effect.grant_temporary_item",
                "effect.set_entity_position",
                "effect.append_event_message")) {
            assertEquals(2, occurrences(view, "value=\"" + effectKey + "\""), effectKey);
        }
        assertTrue(view.contains("name=\"successEffects\""));
        assertTrue(view.contains("name=\"failureEffects\""));
    }

    @Test
    void clientFormCannotSubmitServerOwnedRollOrAlgorithmFields() throws Exception {
        String view = Files.readString(HOST_VIEW, StandardCharsets.UTF_8);
        String script = Files.readString(HOST_SCRIPT, StandardCharsets.UTF_8);
        String form = view.substring(
                view.indexOf("<form id=\"host-event-form\""),
                view.indexOf("</form>", view.indexOf("<form id=\"host-event-form\"")));

        for (String forbidden : List.of(
                "rolledValue",
                "selectedValue",
                "modifierValue",
                "modifierAlgorithm",
                "selectionAlgorithm",
                "executionAlgorithm",
                "effectImplementation")) {
            assertFalse(form.contains(forbidden), forbidden);
        }
        assertTrue(form.contains("data-endpoint="));
        assertTrue(form.contains("/api/host/events/check"));
        assertTrue(script.contains("fetch("));
        assertTrue(script.contains("X-Request-Digest"));
        assertTrue(script.contains("DND_TOOL_SE_STAGE3_CHECK_PAYLOAD_V1"));
        assertTrue(script.contains("event.preventDefault()"));
    }

    @Test
    void scriptContainsEveryFrozenAbilitySkillAndSaveSource() throws Exception {
        String script = Files.readString(HOST_SCRIPT, StandardCharsets.UTF_8);
        List<String> sources = List.of(
                "ability.strength", "ability.dexterity", "ability.constitution",
                "ability.intelligence", "ability.wisdom", "ability.charisma",
                "skill.acrobatics", "skill.animal_handling", "skill.arcana",
                "skill.athletics", "skill.deception", "skill.history", "skill.insight",
                "skill.intimidation", "skill.investigation", "skill.medicine",
                "skill.nature", "skill.perception", "skill.performance",
                "skill.persuasion", "skill.religion", "skill.sleight_of_hand",
                "skill.stealth", "skill.survival",
                "save.strength", "save.dexterity", "save.constitution",
                "save.intelligence", "save.wisdom", "save.charisma");

        for (String source : sources) assertEquals(1, occurrences(script, source), source);
    }

    @Test
    void formCapturesExecutorAndEveryPossibleTargetVersion() throws Exception {
        String view = Files.readString(HOST_VIEW, StandardCharsets.UTF_8);
        String script = Files.readString(HOST_SCRIPT, StandardCharsets.UTF_8);

        assertTrue(view.contains("name=\"executorExpectedRowVersion\""));
        assertTrue(view.contains("name=\"targetCharacterVersions\""));
        assertTrue(view.contains("expected_row_version"));
        assertTrue(script.contains("executorVersion"));
        assertTrue(script.contains("expectedRowVersion"));
        assertTrue(script.contains("versionPattern"));
    }

    private static int occurrences(String content, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = content.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }
}
