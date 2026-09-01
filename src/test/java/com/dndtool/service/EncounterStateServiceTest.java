package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.EncounterStateRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EncounterStateServiceTest {
    private static final String CHARACTER_A = "11111111-1111-4111-8111-111111111111";
    private static final String CHARACTER_B = "22222222-2222-4222-8222-222222222222";

    @Test
    void preparesOnlyTheFrozenTavernCellarMapAndCanonicalParticipantOrder() {
        EncounterStateService service = new EncounterStateService(catalog());

        EncounterStateRepository.Command command = service.prepare(
                7L,
                11L,
                "node.cellar_stairs",
                List.of(
                        participant(CHARACTER_B, "node.cellar_floor"),
                        participant(CHARACTER_A, "node.cellar_floor")));

        assertEquals("dnd5e2014_srd51_se_v1", command.moduleKey());
        assertEquals("1", command.releaseVersion());
        assertEquals("a".repeat(64), command.contentSha256());
        assertEquals("map.tavern_cellar", command.mapKey());
        assertEquals("NODE", command.mapType());
        assertEquals(List.of(CHARACTER_A, CHARACTER_B),
                command.participants().stream()
                        .map(EncounterStateRepository.ParticipantPlacement::characterKey)
                        .toList());
    }

    @Test
    void rejectsUnknownPartyOrParticipantNodes() {
        EncounterStateService service = new EncounterStateService(catalog());

        EncounterStateService.EncounterStateException partyFailure = assertThrows(
                EncounterStateService.EncounterStateException.class,
                () -> service.prepare(7L, 11L, "node.unknown", List.of()));
        assertEquals(EncounterStateService.Rejection.NODE_NOT_FOUND, partyFailure.rejection());

        EncounterStateService.EncounterStateException participantFailure = assertThrows(
                EncounterStateService.EncounterStateException.class,
                () -> service.prepare(7L, 11L, "node.cellar_stairs",
                        List.of(participant(CHARACTER_A, "node.unknown"))));
        assertEquals(EncounterStateService.Rejection.NODE_NOT_FOUND,
                participantFailure.rejection());
    }

    @Test
    void rejectsDuplicateOrMalformedParticipantIdentity() {
        EncounterStateService service = new EncounterStateService(catalog());

        EncounterStateService.EncounterStateException duplicate = assertThrows(
                EncounterStateService.EncounterStateException.class,
                () -> service.prepare(7L, 11L, "node.cellar_stairs", List.of(
                        participant(CHARACTER_A, "node.cellar_floor"),
                        participant(CHARACTER_A, "node.cellar_stairs"))));
        assertEquals(EncounterStateService.Rejection.DUPLICATE_PARTICIPANT,
                duplicate.rejection());

        EncounterStateService.EncounterStateException malformed = assertThrows(
                EncounterStateService.EncounterStateException.class,
                () -> service.prepare(7L, 11L, "node.cellar_stairs",
                        List.of(participant(
                                "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA", "node.cellar_floor"))));
        assertEquals(EncounterStateService.Rejection.INVALID_REQUEST, malformed.rejection());
    }

    @Test
    void failsClosedWhenFrozenMapDefinitionDrifts() {
        ModuleCatalog base = catalog();
        assertThrows(IllegalStateException.class, () -> new EncounterStateService(copy(
                base,
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "GRID")),
                base.mapNodes())));
        assertThrows(IllegalStateException.class, () -> new EncounterStateService(copy(
                base,
                List.of(
                        new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE"),
                        new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                base.mapNodes())));
    }

    @Test
    void failsClosedOnDuplicateOrMissingFrozenNodes() {
        ModuleCatalog base = catalog();
        ModuleCatalog.MapNode duplicate = base.mapNodes().getFirst();
        assertThrows(IllegalStateException.class, () -> new EncounterStateService(copy(
                base,
                base.mapDefinitions(),
                List.of(duplicate, duplicate))));
        assertThrows(IllegalStateException.class, () -> new EncounterStateService(copy(
                base,
                base.mapDefinitions(),
                List.of(new ModuleCatalog.MapNode("map.other", "node.other", "Other")))));
    }

    private static EncounterStateService.ParticipantRequest participant(
            String characterKey,
            String nodeKey) {
        return new EncounterStateService.ParticipantRequest(
                characterKey, EncounterStateRepository.Faction.ALLY, nodeKey);
    }

    private static ModuleCatalog catalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", "a".repeat(64), "RELEASED"),
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
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.cellar_stairs", "Cellar stairs"),
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.cellar_floor", "Cellar floor")),
                List.of());
    }

    private static ModuleCatalog copy(
            ModuleCatalog catalog,
            List<ModuleCatalog.MapDefinition> maps,
            List<ModuleCatalog.MapNode> nodes) {
        return new ModuleCatalog(
                catalog.release(), catalog.ruleConstants(), catalog.fieldDefinitions(),
                catalog.classDefinitions(), catalog.proficiencyTiers(),
                catalog.proficiencyBonusBands(), catalog.skillDefinitions(),
                catalog.saveDefinitions(), catalog.itemTemplates(), catalog.entityTemplates(),
                catalog.entityTemplateValues(), catalog.entityTemplateClassLevels(),
                catalog.entityTemplateProficiencies(), catalog.checkDefinitions(),
                catalog.rollModes(), catalog.eventTemplates(), catalog.eventChecks(),
                catalog.eventEffects(), catalog.effectDefinitions(), catalog.effectParameters(),
                maps, nodes, catalog.mapConnections());
    }
}
