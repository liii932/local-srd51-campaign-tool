package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.persistence.CampaignModuleBindingRepository;
import com.dndtool.persistence.ModuleCatalog;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class HostRulesServiceTest {
    private static final String SHA = BuiltinModuleReleaseRegistry.LEGACY_CONTENT_SHA256;

    @Test
    void loadsOnlyTheActiveCampaignsVerifiedReleasedCatalog() throws Exception {
        ModuleCatalog catalog = catalog();
        HostRulesService service = service(
                List.of(binding("ACTIVE", BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, SHA)),
                (moduleKey, releaseVersion) -> ready(catalog));

        HostRulesService.Result result = service.load(null, null);

        assertEquals(HostRulesService.Status.READY, result.status());
        assertEquals(BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY,
                result.catalog().moduleKey());
        assertEquals("1", result.catalog().releaseVersion());
        assertEquals(1, result.catalog().canonicalFormatVersion());
        assertEquals("", result.catalog().query());
        assertEquals(null, result.catalog().selectedType());
        assertTrue(result.catalog().entries().stream().anyMatch(entry ->
                entry.type() == HostRulesService.RuleType.FIELD
                        && entry.key().equals("ability.strength")
                        && entry.displayName().equals("力量")));
        assertTrue(result.catalog().entries().stream().anyMatch(entry ->
                entry.type() == HostRulesService.RuleType.CLASS
                        && entry.key().equals("class.fighter")));
    }

    @Test
    void searchesNfcTextAndStableKeysWithinAClosedTypeFilter() throws Exception {
        HostRulesService service = service(
                List.of(binding("ACTIVE", BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, SHA)),
                (moduleKey, releaseVersion) -> ready(catalog()));

        HostRulesService.Result byName = service.load("  力量  ", "field");
        HostRulesService.Result byKey = service.load("FIGHTER", "CLASS");

        assertEquals(List.of("ability.strength"), byName.catalog().entries().stream()
                .map(HostRulesService.RuleEntry::key).toList());
        assertEquals("力量", byName.catalog().query());
        assertEquals(HostRulesService.RuleType.FIELD, byName.catalog().selectedType());
        assertEquals(List.of("class.fighter"), byKey.catalog().entries().stream()
                .map(HostRulesService.RuleEntry::key).toList());
    }

    @Test
    void draftMalformedAndUnknownFrozenReleasesStayInvisible() throws Exception {
        AtomicInteger releaseReads = new AtomicInteger();
        HostRulesService.ReleaseLookup lookup = (moduleKey, releaseVersion) -> {
            releaseReads.incrementAndGet();
            return ready(catalog());
        };

        for (CampaignModuleBindingRepository.Binding binding : List.of(
                binding("ACTIVE", BuiltinModuleReleaseRegistry.COMPLETE_MODULE_KEY, SHA),
                binding("ACTIVE", "INVALID", SHA),
                binding("ACTIVE", "module.unknown", SHA))) {
            HostRulesService.Result result = service(List.of(binding), lookup)
                    .load(null, null);
            assertEquals(HostRulesService.Status.MODULE_UNAVAILABLE, result.status());
        }
        assertEquals(0, releaseReads.get());
    }

    @Test
    void frozenDigestAndVerifiedReleaseFailuresFailClosed() throws Exception {
        HostRulesService mismatch = service(
                List.of(binding("ACTIVE", BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY,
                        "0".repeat(64))),
                (moduleKey, releaseVersion) -> ready(catalog()));
        assertEquals(HostRulesService.Status.MODULE_HASH_MISMATCH,
                mismatch.load(null, null).status());

        HostRulesService unavailable = service(
                List.of(binding("ACTIVE", BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, SHA)),
                (moduleKey, releaseVersion) -> new ModuleReleaseVerifier.Result(
                        ModuleReleaseVerifier.Status.RELEASE_UNAVAILABLE, null, null));
        assertEquals(HostRulesService.Status.MODULE_UNAVAILABLE,
                unavailable.load(null, null).status());

        HostRulesService changed = service(
                List.of(binding("ACTIVE", BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, SHA)),
                (moduleKey, releaseVersion) -> new ModuleReleaseVerifier.Result(
                        ModuleReleaseVerifier.Status.MODULE_HASH_MISMATCH, null, null));
        assertEquals(HostRulesService.Status.MODULE_HASH_MISMATCH,
                changed.load(null, null).status());
    }

    @Test
    void invalidSearchAndAmbiguousActiveStateAreRejectedBeforeCatalogProjection()
            throws Exception {
        AtomicInteger releaseReads = new AtomicInteger();
        HostRulesService service = service(
                List.of(binding("ACTIVE", BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, SHA)),
                (moduleKey, releaseVersion) -> {
                    releaseReads.incrementAndGet();
                    return ready(catalog());
                });

        assertEquals(HostRulesService.Status.INVALID_REQUEST,
                service.load("bad\u0001query", null).status());
        assertEquals(HostRulesService.Status.INVALID_REQUEST,
                service.load("x".repeat(81), null).status());
        assertEquals(HostRulesService.Status.INVALID_REQUEST,
                service.load(null, "UNKNOWN").status());
        assertEquals(0, releaseReads.get());

        HostRulesService duplicate = service(
                List.of(
                        binding("ACTIVE", BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, SHA),
                        new CampaignModuleBindingRepository.Binding(
                                "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff", "ACTIVE",
                                BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, "1", SHA)),
                (moduleKey, releaseVersion) -> ready(catalog()));
        assertEquals(HostRulesService.Status.INVALID_STATE,
                duplicate.load(null, null).status());
    }

    @Test
    void noActiveCampaignDoesNotInventTheDefaultRelease() throws Exception {
        AtomicInteger releaseReads = new AtomicInteger();
        HostRulesService service = service(
                List.of(binding("ARCHIVED", BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, SHA)),
                (moduleKey, releaseVersion) -> {
                    releaseReads.incrementAndGet();
                    return ready(catalog());
                });

        assertEquals(HostRulesService.Status.NO_ACTIVE_CAMPAIGN,
                service.load(null, null).status());
        assertEquals(0, releaseReads.get());
    }

    private static HostRulesService service(
            List<CampaignModuleBindingRepository.Binding> bindings,
            HostRulesService.ReleaseLookup lookup) {
        return new HostRulesService(
                () -> bindings, lookup, new BuiltinModuleReleaseRegistry());
    }

    private static CampaignModuleBindingRepository.Binding binding(
            String status, String moduleKey, String frozenSha) {
        return new CampaignModuleBindingRepository.Binding(
                "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", status,
                moduleKey, "1", frozenSha);
    }

    private static ModuleReleaseVerifier.Result ready(ModuleCatalog catalog) {
        return new ModuleReleaseVerifier.Result(
                ModuleReleaseVerifier.Status.READY, catalog, SHA);
    }

    private static ModuleCatalog catalog() {
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        BuiltinModuleReleaseRegistry.LEGACY_MODULE_KEY, "1", 1,
                        "SHA-256", SHA, "RELEASED"),
                List.of(new ModuleCatalog.RuleConstant(
                        "character.total_level.maximum", "INTEGER",
                        new ModuleCatalog.IntegerValue(20))),
                List.of(new ModuleCatalog.FieldDefinition(
                        "ability.strength", "力量", "INTEGER",
                        new ModuleCatalog.IntegerValue(10),
                        new ModuleCatalog.IntegerValue(1),
                        new ModuleCatalog.IntegerValue(30),
                        null, null, "基础属性")),
                List.of(new ModuleCatalog.ClassDefinition("class.fighter", "战士")),
                List.of(new ModuleCatalog.ProficiencyTier(
                        "proficiency.full", "FULL", 1, 1, "EXACT")),
                List.of(),
                List.of(new ModuleCatalog.SkillDefinition(
                        "skill.athletics", "运动", "ability.strength")),
                List.of(new ModuleCatalog.SaveDefinition(
                        "save.strength", "ability.strength")),
                List.of(new ModuleCatalog.ItemTemplate(
                        "item.rope_hempen_50ft", "50 英尺麻绳", "普通麻绳")),
                List.of(new ModuleCatalog.EntityTemplate("npc.guard", "守卫")),
                List.of(), List.of(), List.of(),
                List.of(new ModuleCatalog.CheckDefinition(
                        "check.ability", "ABILITY", "ABILITY_MODIFIER_V1")),
                List.of(new ModuleCatalog.RollMode(
                        "roll.normal", "NORMAL", 1, "ONLY_CANDIDATE_V1")),
                List.of(new ModuleCatalog.EventTemplate("event.note", "记录说明")),
                List.of(), List.of(),
                List.of(new ModuleCatalog.EffectDefinition(
                        "effect.append_event_message", "APPEND_EVENT_MESSAGE_V1")),
                List.of(),
                List.of(new ModuleCatalog.MapDefinition("map.tavern_cellar", "NODE")),
                List.of(new ModuleCatalog.MapNode(
                        "map.tavern_cellar", "node.entry", "酒馆入口")),
                List.of());
    }
}
