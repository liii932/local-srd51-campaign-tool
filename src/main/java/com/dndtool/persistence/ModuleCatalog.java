package com.dndtool.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Immutable logical projection of one module release.
 *
 * <p>The aggregate deliberately excludes database IDs, timestamps and runtime data. Release
 * status and the stored digest remain available as verification metadata, but the canonical
 * encoder must use only the four release identity fields defined by the rules specification.
 */
public record ModuleCatalog(
        Release release,
        List<RuleConstant> ruleConstants,
        List<FieldDefinition> fieldDefinitions,
        List<ClassDefinition> classDefinitions,
        List<ProficiencyTier> proficiencyTiers,
        List<ProficiencyBonusBand> proficiencyBonusBands,
        List<SkillDefinition> skillDefinitions,
        List<SaveDefinition> saveDefinitions,
        List<ItemTemplate> itemTemplates,
        List<EntityTemplate> entityTemplates,
        List<EntityTemplateValue> entityTemplateValues,
        List<EntityTemplateClassLevel> entityTemplateClassLevels,
        List<EntityTemplateProficiency> entityTemplateProficiencies,
        List<CheckDefinition> checkDefinitions,
        List<RollMode> rollModes,
        List<EventTemplate> eventTemplates,
        List<EventCheck> eventChecks,
        List<EventEffect> eventEffects,
        List<EffectDefinition> effectDefinitions,
        List<EffectParameter> effectParameters,
        List<MapDefinition> mapDefinitions,
        List<MapNode> mapNodes,
        List<MapConnection> mapConnections,
        List<CatalogDefinition> catalogDefinitions,
        List<CatalogAttribute> catalogAttributes,
        List<CatalogRelation> catalogRelations) {

    public ModuleCatalog {
        Objects.requireNonNull(release);
        ruleConstants = List.copyOf(ruleConstants);
        fieldDefinitions = List.copyOf(fieldDefinitions);
        classDefinitions = List.copyOf(classDefinitions);
        proficiencyTiers = List.copyOf(proficiencyTiers);
        proficiencyBonusBands = List.copyOf(proficiencyBonusBands);
        skillDefinitions = List.copyOf(skillDefinitions);
        saveDefinitions = List.copyOf(saveDefinitions);
        itemTemplates = List.copyOf(itemTemplates);
        entityTemplates = List.copyOf(entityTemplates);
        entityTemplateValues = List.copyOf(entityTemplateValues);
        entityTemplateClassLevels = List.copyOf(entityTemplateClassLevels);
        entityTemplateProficiencies = List.copyOf(entityTemplateProficiencies);
        checkDefinitions = List.copyOf(checkDefinitions);
        rollModes = List.copyOf(rollModes);
        eventTemplates = List.copyOf(eventTemplates);
        eventChecks = List.copyOf(eventChecks);
        eventEffects = List.copyOf(eventEffects);
        effectDefinitions = List.copyOf(effectDefinitions);
        effectParameters = List.copyOf(effectParameters);
        mapDefinitions = List.copyOf(mapDefinitions);
        mapNodes = List.copyOf(mapNodes);
        mapConnections = List.copyOf(mapConnections);
        catalogDefinitions = List.copyOf(catalogDefinitions);
        catalogAttributes = List.copyOf(catalogAttributes);
        catalogRelations = List.copyOf(catalogRelations);
    }

    /** Keeps canonical-v1 fixtures and consumers source-compatible with the frozen 23 sections. */
    public ModuleCatalog(
            Release release,
            List<RuleConstant> ruleConstants,
            List<FieldDefinition> fieldDefinitions,
            List<ClassDefinition> classDefinitions,
            List<ProficiencyTier> proficiencyTiers,
            List<ProficiencyBonusBand> proficiencyBonusBands,
            List<SkillDefinition> skillDefinitions,
            List<SaveDefinition> saveDefinitions,
            List<ItemTemplate> itemTemplates,
            List<EntityTemplate> entityTemplates,
            List<EntityTemplateValue> entityTemplateValues,
            List<EntityTemplateClassLevel> entityTemplateClassLevels,
            List<EntityTemplateProficiency> entityTemplateProficiencies,
            List<CheckDefinition> checkDefinitions,
            List<RollMode> rollModes,
            List<EventTemplate> eventTemplates,
            List<EventCheck> eventChecks,
            List<EventEffect> eventEffects,
            List<EffectDefinition> effectDefinitions,
            List<EffectParameter> effectParameters,
            List<MapDefinition> mapDefinitions,
            List<MapNode> mapNodes,
            List<MapConnection> mapConnections) {
        this(
                release,
                ruleConstants,
                fieldDefinitions,
                classDefinitions,
                proficiencyTiers,
                proficiencyBonusBands,
                skillDefinitions,
                saveDefinitions,
                itemTemplates,
                entityTemplates,
                entityTemplateValues,
                entityTemplateClassLevels,
                entityTemplateProficiencies,
                checkDefinitions,
                rollModes,
                eventTemplates,
                eventChecks,
                eventEffects,
                effectDefinitions,
                effectParameters,
                mapDefinitions,
                mapNodes,
                mapConnections,
                List.of(),
                List.of(),
                List.of());
    }

    /** Scalar values preserve the canonical type tag instead of flattening values to strings. */
    public sealed interface ScalarValue
            permits TextValue, IdentifierValue, IntegerValue, DecimalValue, BooleanValue {
    }

    public record TextValue(String value) implements ScalarValue {
        public TextValue {
            Objects.requireNonNull(value);
        }
    }

    public record IdentifierValue(String value) implements ScalarValue {
        public IdentifierValue {
            Objects.requireNonNull(value);
        }
    }

    public record IntegerValue(long value) implements ScalarValue {
    }

    public record DecimalValue(BigDecimal value) implements ScalarValue {
        public DecimalValue {
            Objects.requireNonNull(value);
        }
    }

    public record BooleanValue(boolean value) implements ScalarValue {
    }

    /** contentSha256 may be null while DRAFT; status and digest are not canonical hash input. */
    public record Release(
            String moduleKey,
            String releaseVersion,
            int canonicalFormatVersion,
            String hashAlgorithm,
            String contentSha256,
            String releaseStatus) {
    }

    public record RuleConstant(String constantKey, String valueType, ScalarValue value) {
    }

    /** Nullable bounds and references are emitted explicitly as NULL by the canonical encoder. */
    public record FieldDefinition(
            String fieldKey,
            String displayName,
            String dataType,
            ScalarValue defaultValue,
            ScalarValue minimumValue,
            ScalarValue maximumValue,
            String dependentMaxFieldKey,
            String unit,
            String description) {
    }

    public record ClassDefinition(String classKey, String displayName) {
    }

    public record ProficiencyTier(
            String proficiencyKey,
            String enumCode,
            int numerator,
            int denominator,
            String roundingAlgorithm) {
    }

    public record ProficiencyBonusBand(int minimumTotalLevel, int maximumTotalLevel, int bonus) {
    }

    public record SkillDefinition(
            String skillKey, String displayName, String abilityFieldKey) {
    }

    public record SaveDefinition(String saveKey, String abilityFieldKey) {
    }

    public record ItemTemplate(String itemKey, String displayName, String description) {
    }

    public record EntityTemplate(String templateKey, String displayName) {
    }

    public record EntityTemplateValue(
            String templateKey, String fieldKey, String valueType, ScalarValue value) {
    }

    public record EntityTemplateClassLevel(String templateKey, String classKey, int level) {
    }

    public record EntityTemplateProficiency(
            String templateKey,
            String targetKind,
            String targetKey,
            String proficiencyKey) {
    }

    public record CheckDefinition(
            String checkKey, String enumCode, String modifierAlgorithm) {
    }

    public record RollMode(
            String rollModeKey,
            String enumCode,
            int candidateCount,
            String selectionAlgorithm) {
    }

    public record EventTemplate(String eventKey, String displayName) {
    }

    public record EventCheck(String eventKey, String checkKey) {
    }

    public record EventEffect(String eventKey, String effectKey) {
    }

    public record EffectDefinition(String effectKey, String executionAlgorithm) {
    }

    public record EffectParameter(
            String effectKey,
            String parameterKey,
            String dataType,
            String referenceKind,
            ScalarValue minimumValue,
            ScalarValue maximumValue,
            String textNormalization,
            Boolean rejectControlCharacters,
            int parameterOrder) {
    }

    public record MapDefinition(String mapKey, String mapType) {
    }

    public record MapNode(String mapKey, String nodeKey, String displayName) {
    }

    public record MapConnection(
            String mapKey, String endpointLowKey, String endpointHighKey) {
    }

    /** Generic canonical-v2 definition row; domain meaning is closed by the v2 validator. */
    public record CatalogDefinition(
            String definitionType,
            String definitionKey,
            String displayName,
            String description,
            int sortOrder) {
    }

    /** Ordered, typed canonical-v2 attribute; repeated keys use increasing attributeOrder. */
    public record CatalogAttribute(
            String definitionType,
            String definitionKey,
            String attributeKey,
            int attributeOrder,
            String valueType,
            ScalarValue value) {
    }

    /** Directed canonical-v2 cross-reference between two definitions. */
    public record CatalogRelation(
            String sourceType,
            String sourceKey,
            String relationType,
            String targetType,
            String targetKey,
            int relationOrder) {
    }
}
