package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.CheckDefinition;
import com.dndtool.module.composition.Schema2CompositionKernel.Field;
import com.dndtool.module.composition.Schema2CompositionKernel.FinalCheck;
import com.dndtool.module.composition.Schema2CompositionKernel.GroupSuccessThreshold;
import com.dndtool.module.composition.Schema2CompositionKernel.IntegerValue;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2PreparationIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
        assertEquals("9", standard.binding().members().getLast().releaseVersion());
        assertEquals("10", variant.binding().members().getLast().releaseVersion());
        assertEquals("language.dwarvish", artisanLanguage(standard));
        assertEquals("language.gnomish", artisanLanguage(variant));

        var standardCatalog = standard.metadata();
        var variantCatalog = variant.metadata();
        assertEquals(savedVariant.interpretation(), variantCatalog.interpretation());
        assertEquals(savedVariant.binding(), variantCatalog.binding());
        assertEquals(Boolean.FALSE, catalogRule(standardCatalog, "check.srd2014").allowAlternateSkillAbility());
        var variantCheck = catalogRule(variantCatalog, "check.srd2014");
        assertEquals(Boolean.TRUE, variantCheck.allowAlternateSkillAbility());
        assertTrue(variantCheck.overrides().contains(new CampaignRuleContext.FieldOverride("allow_alternate_skill_ability",
                BundledCampaignRules.HOUSE_RULE_MODULE, "10", null, null)));
        var standardAthletics = catalogRule(standardCatalog, "proficiency.skill.athletics");
        var variantAthletics = catalogRule(variantCatalog, "proficiency.skill.athletics");
        assertEquals("ability.strength", standardAthletics.abilityKey());
        assertEquals("ability.wisdom", variantAthletics.abilityKey());
        assertEquals(standardAthletics.moduleKey(), variantAthletics.moduleKey());
        assertTrue(standardAthletics.overrides().isEmpty());
        assertTrue(variantAthletics.overrides().contains(new CampaignRuleContext.FieldOverride("ability_key",
                BundledCampaignRules.HOUSE_RULE_MODULE, "10", null, null)));
        assertTrue(catalogRule(variantCatalog, "background.custom_artisan").overrides().contains(
                new CampaignRuleContext.FieldOverride("grant", BundledCampaignRules.HOUSE_RULE_MODULE, "10",
                        "proficiency_grants", "trade_language")));
    }

    @Test void restoresCheckParametersAndTheirCompleteSourcesFromPortableMaterial() {
        var standard = material(BundledCampaignRules.STANDARD).restore();
        var saved = material(BundledCampaignRules.CHECK_VARIANT);
        var variant = CampaignDomainCodec.decode(CampaignDomainCodec.encode(saved), Material.class).restore();
        assertEquals(new GroupSuccessThreshold(1, 2), checkDefinition(standard).groupSuccessThreshold());
        assertEquals(new GroupSuccessThreshold(2, 3), checkDefinition(variant).groupSuccessThreshold());
        assertEquals(1, checkDefinition(standard).contestMinimumWinningMargin());
        assertEquals(2, checkDefinition(variant).contestMinimumWinningMargin());
        var chain = variant.projection().projection().chains().stream()
                .filter(row -> row.target().equals(variant.checkRule()) && row.field() == Field.GROUP_SUCCESS_THRESHOLD)
                .findFirst().orElseThrow();
        assertEquals(List.of(BundledCampaignRules.CHARACTER_MODULE, BundledCampaignRules.HOUSE_RULE_MODULE),
                chain.steps().stream().map(step -> step.provider().moduleKey()).toList());
        assertEquals(List.of(new GroupSuccessThreshold(1, 2), new GroupSuccessThreshold(2, 3)),
                chain.steps().stream().map(step -> step.after().value()).toList());
        var contest = variant.projection().projection().chains().stream()
                .filter(row -> row.target().equals(variant.checkRule()) && row.field() == Field.CONTEST_MINIMUM_WINNING_MARGIN)
                .findFirst().orElseThrow();
        assertEquals(List.of(BundledCampaignRules.CHARACTER_MODULE, BundledCampaignRules.HOUSE_RULE_MODULE),
                contest.steps().stream().map(step -> step.provider().moduleKey()).toList());
        assertEquals(List.of(new IntegerValue(1), new IntegerValue(2)),
                contest.steps().stream().map(step -> step.after().value()).toList());
    }

    private static CheckDefinition checkDefinition(CampaignRuleContext context) {
        return context.projection().projection().rules().stream().filter(FinalCheck.class::isInstance)
                .map(FinalCheck.class::cast).filter(rule -> rule.identity().equals(context.checkRule()))
                .findFirst().orElseThrow().definition();
    }

    @Test void initialPlanKeepsItsPortableTagsAndFieldsOnSameVersionReopen() {
        var context = material(BundledCampaignRules.CHECK_VARIANT).restore();
        UUID campaign = UUID.randomUUID();
        UUID character = UUID.randomUUID();
        var preparation = Schema2PreparationIdentity.random();
        var plan = context.initialPlan(campaign, character, preparation, "fighter", "custom_artisan");
        byte[] encoded = CampaignDomainCodec.encode(plan);
        var portable = CampaignJson.read(encoded);
        assertEquals(Set.of("type", "campaignId", "characterId", "character"), portable.keySet());
        assertEquals("initial.InitialPlan", portable.get("type").getAsString());
        assertEquals(campaign.toString(), portable.get("campaignId").getAsString());
        assertEquals(character.toString(), portable.get("characterId").getAsString());

        var characterPlan = portable.getAsJsonObject("character");
        assertEquals(Set.of("type", "identity", "binding", "sources", "choices", "slots", "grants"), characterPlan.keySet());
        assertEquals("initial.Plan", characterPlan.get("type").getAsString());
        assertEquals(CampaignDomainCodec.toJson(preparation), characterPlan.get("identity"));
        assertEquals(CampaignDomainCodec.toJson(context.binding()), characterPlan.get("binding"));

        var restored = CampaignDomainCodec.decode(encoded, InitialPlan.class);
        assertEquals(plan, restored);
        assertArrayEquals(encoded, CampaignDomainCodec.encode(restored));
    }

    @Test void validTypedMaterialWithoutRequiredMechanismsCannotBeRestoredForExecution() {
        var incomplete = ProductionSchema2Projection.compose(BundledCampaignRules.artifacts(BundledCampaignRules.STANDARD).subList(0, 1));
        var restored = ProductionSchema2Projection.restore(incomplete.members(), incomplete.projection().rules(),
                incomplete.projection().entries(), incomplete.projection().chains());
        assertEquals(incomplete.chainSha256(), restored.chainSha256());

        var rejected = assertThrows(StorageException.class, Material.of(incomplete)::restore);
        assertEquals("INVALID_FILE", rejected.code());
        assertEquals("STORAGE_RULE_SUPPORT_CHECK", rejected.getCause().getMessage());
    }

    @Test void playableBindingAndRestorationRequireAnEnabledHeavyArmorMovementRule() throws Exception {
        var artifacts = new ArrayList<>(BundledCampaignRules.artifacts(BundledCampaignRules.STANDARD));
        var armorMovement = CampaignRuleSupportTest.authorRow(BundledCampaignRules.CHARACTER_MODULE, "rules.json",
                "rule_key", "armor_movement.heavy_armor");
        artifacts.add(CampaignRuleSupportTest.extension("disable_armor_movement", artifacts, List.of(), List.of(),
                List.of(CampaignRuleSupportTest.disabled(armorMovement))));
        var typed = ProductionSchema2Projection.compose(artifacts);
        var unsupported = assertThrows(IllegalArgumentException.class, () -> CampaignRuleSupport.validate(typed));
        assertEquals("STORAGE_RULE_SUPPORT_COMBAT", unsupported.getMessage());
        assertEquals("ACTION_FLOW_RULE_UNSUPPORTED", unsupported.getCause().getMessage());

        var rejected = assertThrows(StorageException.class, Material.of(typed)::restore);
        assertEquals("INVALID_FILE", rejected.code());
        assertEquals("STORAGE_RULE_SUPPORT_COMBAT", rejected.getCause().getMessage());
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
        assertEquals("INVALID_FILE", assertThrows(StorageException.class, () -> new Material("DND_CAMPAIGN_SCHEMA_2_V2",
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
