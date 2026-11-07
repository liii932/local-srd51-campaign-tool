package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.FinalCheck;
import com.dndtool.service.Schema2PreparationIdentity;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.storage.CampaignDomainData.Material;
import static org.junit.jupiter.api.Assertions.*;

class CampaignRuleContextTest {
    @Test void restoredContextsSupplyTheirOwnInitialGrantsAndCatalogSources() {
        var standard = material(BundledCampaignRules.STANDARD).restore();
        var savedVariant = material(BundledCampaignRules.CHECK_VARIANT);
        var roundTrip = CampaignDomainCodec.decode(CampaignDomainCodec.encode(savedVariant), Material.class);
        assertEquals(savedVariant, roundTrip);
        var variant = roundTrip.restore();
        assertSame(variant, savedVariant.restore());

        assertNotEquals(standard.binding(), variant.binding());
        assertEquals("5", standard.binding().members().getLast().releaseVersion());
        assertEquals("6", variant.binding().members().getLast().releaseVersion());
        assertEquals("language.dwarvish", artisanLanguage(standard));
        assertEquals("language.gnomish", artisanLanguage(variant));

        var standardCatalog = standard.metadata();
        var variantCatalog = variant.metadata();
        assertEquals(savedVariant.interpretation(), variantCatalog.interpretation());
        assertEquals(savedVariant.binding(), variantCatalog.binding());
        var standardAthletics = catalogRule(standardCatalog, "proficiency.skill.athletics");
        var variantAthletics = catalogRule(variantCatalog, "proficiency.skill.athletics");
        assertEquals("ability.strength", standardAthletics.abilityKey());
        assertEquals("ability.wisdom", variantAthletics.abilityKey());
        assertEquals(standardAthletics.moduleKey(), variantAthletics.moduleKey());
        assertTrue(standardAthletics.overrides().isEmpty());
        assertTrue(variantAthletics.overrides().contains(new CampaignRuleContext.FieldOverride("ability_key",
                BundledCampaignRules.HOUSE_RULE_MODULE, "6", null, null)));
        assertTrue(catalogRule(variantCatalog, "background.custom_artisan").overrides().contains(
                new CampaignRuleContext.FieldOverride("grant", BundledCampaignRules.HOUSE_RULE_MODULE, "6",
                        "proficiency_grants", "trade_language")));
    }

    @Test void validTypedMaterialWithoutRequiredMechanismsCannotBeRestoredForExecution() {
        var incomplete = ProductionSchema2Projection.compose(BundledCampaignRules.artifacts().subList(0, 1));
        var restored = ProductionSchema2Projection.restore(incomplete.members(), incomplete.projection().rules(),
                incomplete.projection().entries(), incomplete.projection().chains());
        assertEquals(incomplete.chainSha256(), restored.chainSha256());

        var rejected = assertThrows(StorageException.class, Material.of(incomplete)::restore);
        assertEquals("INVALID_FILE", rejected.code());
        assertEquals("STORAGE_RULE_SUPPORT_CHECK", rejected.getCause().getMessage());
    }

    @Test void supportedBindingRequiresAllItsOwnFrozenRowsAndCurrentInterpretation() {
        var standard = material(BundledCampaignRules.STANDARD);
        var variant = material(BundledCampaignRules.CHECK_VARIANT);
        standard.restore();
        variant.restore();
        var crossBound = new Material(variant.interpretation(), standard.binding(),
                variant.rules(), variant.entries(), variant.chains());
        var truncated = new Material(variant.interpretation(), variant.binding(), variant.rules(), variant.entries(),
                variant.chains().subList(1, variant.chains().size()));
        var malformedRules = new ArrayList<>(variant.rules());
        malformedRules.set(0, new FinalCheck(null, true));
        var malformed = new Material(variant.interpretation(), variant.binding(), malformedRules,
                variant.entries(), variant.chains());
        assertEquals("INVALID_FILE", assertThrows(StorageException.class, crossBound::restore).code());
        assertEquals("INVALID_FILE", assertThrows(StorageException.class, truncated::restore).code());
        assertEquals("INVALID_FILE", assertThrows(StorageException.class, malformed::restore).code());
        assertEquals("INVALID_FILE", assertThrows(StorageException.class, () -> new Material("other_interpretation",
                variant.binding(), variant.rules(), variant.entries(), variant.chains())).code());

        var legacy = CampaignDomainCodec.toJson(standard).getAsJsonObject();
        legacy.remove("interpretation");
        legacy.remove("binding");
        legacy.add("members", CampaignDomainCodec.toJson(standard.binding().members()));
        assertThrows(StorageException.class, () -> CampaignDomainCodec.fromJson(legacy, Material.class));
    }

    private static Material material(String combination) {
        return Material.of(BundledCampaignRules.projection(combination));
    }

    private static String artisanLanguage(CampaignRuleContext context) {
        var plan = context.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), "fighter", "custom_artisan");
        assertEquals(context.binding(), plan.character().binding());
        return plan.character().grants().stream().filter(grant -> grant.slot().entryKey().equals("trade_language"))
                .findFirst().orElseThrow().target().ruleKey();
    }

    private static CampaignRuleContext.CatalogEntry catalogRule(CampaignRuleContext.Metadata metadata, String key) {
        return metadata.catalog().stream().filter(rule -> rule.key().equals(key)).findFirst().orElseThrow();
    }
}
