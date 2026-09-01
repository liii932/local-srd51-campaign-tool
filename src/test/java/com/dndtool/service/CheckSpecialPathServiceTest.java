package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.CheckExecutionRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.NoteEventRepository;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the two deliberately exceptional host command preparation paths. */
final class CheckSpecialPathServiceTest {

    @Test
    void manualUsesNoEventTemplateAndApprovesGlobalEffectsBeforeRolling() {
        SequenceSource source = new SequenceSource(12);
        CheckSpecialPathService service = new CheckSpecialPathService(catalog(), source);
        CheckSpecialPathService.ManualRequest request = new CheckSpecialPathService.ManualRequest(
                7L, 4L, 9L, 101L, "  e\u0301preuve  ", 3,
                "roll.normal", 15,
                List.of(
                        "effect.adjust_current_hp",
                        "effect.set_entity_position",
                        "effect.append_event_message"));

        CheckSpecialPathService.ManualPreparation prepared = service.prepareManual(request);

        CheckExecutionRepository.Command command = prepared.persistenceCommand();
        assertEquals("épreuve", command.manualName());
        assertEquals("check.manual", command.checkKey());
        assertNull(command.eventKey());
        assertNull(command.modifierSourceKey());
        assertEquals(15, command.calculation().totalValue());
        assertEquals(List.of(
                "effect.adjust_current_hp", "effect.set_entity_position",
                "effect.append_event_message"),
                prepared.approvedEffectKeys());
        assertEquals(1, source.consumed);
    }

    @Test
    void invalidManualInputOrEffectIsRejectedBeforeRolling() {
        SequenceSource source = new SequenceSource(10);
        CheckSpecialPathService service = new CheckSpecialPathService(catalog(), source);

        assertRejection(CheckSpecialPathService.Rejection.INVALID_REQUEST,
                () -> service.prepareManual(manualRequest("\n", 0, List.of())));
        assertRejection(CheckSpecialPathService.Rejection.INVALID_REQUEST,
                () -> service.prepareManual(manualRequest("测试", 100, List.of())));
        assertRejection(CheckSpecialPathService.Rejection.EFFECT_NOT_ALLOWED,
                () -> service.prepareManual(
                        manualRequest("测试", 0, List.of("effect.unknown"))));
        assertEquals(0, source.consumed);
    }

    @Test
    void noteNormalizesNfcButPreservesSpacing() {
        CheckSpecialPathService service = new CheckSpecialPathService(catalog(), () -> 10);

        NoteEventRepository.Command command = service.prepareNote(
                new CheckSpecialPathService.NoteRequest(7L, 9L, "  cafe\u0301  "));

        assertEquals(7L, command.campaignId());
        assertEquals(9L, command.expectedEventTail());
        assertEquals("  café  ", command.message());
    }

    @Test
    void noteRejectsEmptyControlAndOverlongMessages() {
        CheckSpecialPathService service = new CheckSpecialPathService(catalog(), () -> 10);

        assertRejection(CheckSpecialPathService.Rejection.INVALID_REQUEST,
                () -> service.prepareNote(new CheckSpecialPathService.NoteRequest(7L, 0L, "")));
        assertRejection(CheckSpecialPathService.Rejection.INVALID_REQUEST,
                () -> service.prepareNote(
                        new CheckSpecialPathService.NoteRequest(7L, 0L, "第一行\n第二行")));
        assertRejection(CheckSpecialPathService.Rejection.INVALID_REQUEST,
                () -> service.prepareNote(
                        new CheckSpecialPathService.NoteRequest(7L, 0L, "狼".repeat(501))));
    }

    @Test
    void malformedManualOrNoteRelationshipsFailAtConstruction() {
        List<ModuleCatalog.EventCheck> manualLink =
                List.of(new ModuleCatalog.EventCheck("event.note", "check.manual"));
        List<ModuleCatalog.EventEffect> extraNoteEffect = List.of(
                new ModuleCatalog.EventEffect("event.note", "effect.append_event_message"),
                new ModuleCatalog.EventEffect("event.note", "effect.adjust_current_hp"));
        List<ModuleCatalog.EffectDefinition> wrongAlgorithm = List.of(
                new ModuleCatalog.EffectDefinition(
                        "effect.adjust_current_hp", "OTHER_V1"),
                new ModuleCatalog.EffectDefinition(
                        "effect.grant_module_item", "GRANT_MODULE_ITEM_V1"),
                new ModuleCatalog.EffectDefinition(
                        "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1"),
                new ModuleCatalog.EffectDefinition(
                        "effect.set_entity_position", "SET_ENTITY_NODE_POSITION_V1"),
                new ModuleCatalog.EffectDefinition(
                        "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1"));

        assertThrows(IllegalStateException.class,
                () -> new CheckSpecialPathService(catalog(manualLink, noteEffects()), () -> 10));
        assertThrows(IllegalStateException.class,
                () -> new CheckSpecialPathService(catalog(List.of(), extraNoteEffect), () -> 10));
        assertThrows(IllegalStateException.class,
                () -> new CheckSpecialPathService(
                        catalog(List.of(), noteEffects(), wrongAlgorithm), () -> 10));
    }

    private static CheckSpecialPathService.ManualRequest manualRequest(
            String name, int modifier, List<String> effects) {
        return new CheckSpecialPathService.ManualRequest(
                7L, 0L, 9L, 101L, name, modifier, "roll.normal", 10, effects);
    }

    private static void assertRejection(
            CheckSpecialPathService.Rejection expected, Runnable action) {
        CheckSpecialPathService.SpecialPathException exception = assertThrows(
                CheckSpecialPathService.SpecialPathException.class, action::run);
        assertEquals(expected, exception.rejection());
    }

    private static ModuleCatalog catalog() {
        return catalog(List.of(), noteEffects());
    }

    private static List<ModuleCatalog.EventEffect> noteEffects() {
        return List.of(new ModuleCatalog.EventEffect(
                "event.note", "effect.append_event_message"));
    }

    private static ModuleCatalog catalog(
            List<ModuleCatalog.EventCheck> eventChecks,
            List<ModuleCatalog.EventEffect> eventEffects) {
        return catalog(eventChecks, eventEffects, effectDefinitions());
    }

    private static List<ModuleCatalog.EffectDefinition> effectDefinitions() {
        return List.of(
                new ModuleCatalog.EffectDefinition(
                        "effect.adjust_current_hp", "ADJUST_CURRENT_HP_CLAMP_V1"),
                new ModuleCatalog.EffectDefinition(
                        "effect.grant_module_item", "GRANT_MODULE_ITEM_V1"),
                new ModuleCatalog.EffectDefinition(
                        "effect.grant_temporary_item", "GRANT_TEMPORARY_ITEM_V1"),
                new ModuleCatalog.EffectDefinition(
                        "effect.set_entity_position", "SET_ENTITY_NODE_POSITION_V1"),
                new ModuleCatalog.EffectDefinition(
                        "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1"));
    }

    private static ModuleCatalog catalog(
            List<ModuleCatalog.EventCheck> eventChecks,
            List<ModuleCatalog.EventEffect> eventEffects,
            List<ModuleCatalog.EffectDefinition> effectDefinitions) {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", null, "RELEASED"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ModuleCatalog.CheckDefinition(
                        "check.manual", "MANUAL", "MANUAL_MODIFIER_V1")),
                List.of(
                        new ModuleCatalog.RollMode(
                                "roll.normal", "NORMAL", 1, "ONLY_CANDIDATE_V1"),
                        new ModuleCatalog.RollMode(
                                "roll.advantage", "ADVANTAGE", 2,
                                "HIGHEST_FIRST_ON_TIE_V1"),
                        new ModuleCatalog.RollMode(
                                "roll.disadvantage", "DISADVANTAGE", 2,
                                "LOWEST_FIRST_ON_TIE_V1")),
                List.of(new ModuleCatalog.EventTemplate("event.note", "记录说明")),
                eventChecks,
                eventEffects,
                effectDefinitions,
                List.of(new ModuleCatalog.EffectParameter(
                        "effect.append_event_message",
                        "message",
                        "TEXT",
                        null,
                        new ModuleCatalog.IntegerValue(1),
                        new ModuleCatalog.IntegerValue(500),
                        "NFC",
                        true,
                        1)),
                List.of(),
                List.of(),
                List.of());
    }

    private static final class SequenceSource implements D20CheckCalculator.D20Source {
        private final ArrayDeque<Integer> values;
        private int consumed;

        private SequenceSource(Integer... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public int roll() {
            consumed++;
            return values.removeFirst();
        }
    }
}
