package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EntityPositionServiceTest {
    private static final String REQUEST = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String CHARACTER = "11111111-1111-4111-8111-111111111111";

    @Test
    void preparesAnyFrozenMapNodeWithoutConsultingConnections() {
        EntityPositionService service = new EntityPositionService(catalog());

        EntityPositionService.PreparedRequest prepared = service.prepare(
                request("node.cellar_floor", 3L));

        assertEquals("map.tavern_cellar", prepared.mapKey());
        assertEquals("node.cellar_floor", prepared.nodeKey());
        assertEquals(CHARACTER, prepared.versionRequest().executor().characterKey());
        assertEquals(3L, prepared.versionRequest().executor().expectedRowVersion());
        assertEquals(List.of(), prepared.versionRequest().possibleTargets());
        assertEquals(64, prepared.requestDigestSha256().length());
    }

    @Test
    void digestChangesWithDestinationOrExpectedVersion() {
        EntityPositionService service = new EntityPositionService(catalog());

        String base = service.prepare(request("node.cellar_floor", 3L)).requestDigestSha256();

        assertNotEquals(base,
                service.prepare(request("node.cellar_stairs", 3L)).requestDigestSha256());
        assertNotEquals(base,
                service.prepare(request("node.cellar_floor", 4L)).requestDigestSha256());
    }

    @Test
    void protectedHttpDigestUsesStableCampaignKeyAndRejectsMismatch() {
        EntityPositionService service = new EntityPositionService(catalog());
        String campaign = "22222222-2222-4222-8222-222222222222";
        String payload = EntityPositionRequestDigest.canonicalPayloadSha256(
                campaign, CHARACTER, "map.tavern_cellar", "node.cellar_floor");
        String digest = CheckRequestDigest.sha256(payload,
                new com.dndtool.persistence.CharacterVersionRepository.VersionExpectation(
                        CHARACTER, 3L), List.of());

        EntityPositionService.PreparedRequest prepared = service.prepare(
                new EntityPositionService.Request(
                        REQUEST, 7L, 11L, campaign, CHARACTER, 3L,
                        "node.cellar_floor", digest));

        assertEquals(digest, prepared.requestDigestSha256());
        assertEquals(payload, prepared.versionRequest().canonicalPayloadSha256());
        assertThrows(EntityPositionService.DirectPositionException.class,
                () -> service.prepare(new EntityPositionService.Request(
                        REQUEST, 7L, 11L, campaign, CHARACTER, 3L,
                        "node.cellar_floor", "b".repeat(64))));
    }

    @Test
    void rejectsUnknownNodeAndMalformedIdentity() {
        EntityPositionService service = new EntityPositionService(catalog());

        EntityPositionService.DirectPositionException unknown = assertThrows(
                EntityPositionService.DirectPositionException.class,
                () -> service.prepare(request("node.unknown", 3L)));
        assertEquals(EntityPositionService.Rejection.NODE_NOT_FOUND, unknown.rejection());

        EntityPositionService.DirectPositionException malformed = assertThrows(
                EntityPositionService.DirectPositionException.class,
                () -> service.prepare(new EntityPositionService.Request(
                        REQUEST.toUpperCase(), 7L, 11L, CHARACTER, 3L, "node.cellar_floor")));
        assertEquals(EntityPositionService.Rejection.INVALID_REQUEST, malformed.rejection());
    }

    @Test
    void failsClosedWhenFrozenMapOrNodesDrift() {
        ModuleCatalog base = catalog();
        assertThrows(IllegalStateException.class, () -> new EntityPositionService(copy(
                base,
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "GRID")),
                base.mapNodes())));
        assertThrows(IllegalStateException.class, () -> new EntityPositionService(copy(
                base, base.mapDefinitions(), List.of())));
    }

    private static EntityPositionService.Request request(String nodeKey, long version) {
        return new EntityPositionService.Request(
                REQUEST, 7L, 11L, CHARACTER, version, nodeKey);
    }

    private static ModuleCatalog catalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256",
                        "a".repeat(64), "RELEASED"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.cellar_stairs", "Stairs"),
                        new ModuleCatalog.MapNode(
                                "map.tavern_cellar", "node.cellar_floor", "Floor")),
                // Connections deliberately omit floor-to-stairs: direct positioning ignores paths.
                List.of(new ModuleCatalog.MapConnection(
                        "map.tavern_cellar", "node.cellar_stairs", "node.cellar_stairs")));
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
