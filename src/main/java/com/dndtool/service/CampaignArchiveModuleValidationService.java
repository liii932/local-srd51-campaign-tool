package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.module.ModuleHashManifest;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Validates a parsed archive against one installed, verified immutable module directory. */
public final class CampaignArchiveModuleValidationService {
    private final ModuleReleaseVerifier releaseVerifier;

    public CampaignArchiveModuleValidationService(ModuleCatalogRepository repository) {
        this(repository, new ModuleContentHasher()::sha256, new BuiltinModuleHashManifest());
    }

    CampaignArchiveModuleValidationService(
            ModuleCatalogRepository repository,
            ModuleReleaseVerifier.DigestComputer hasher,
            ModuleHashManifest manifest) {
        releaseVerifier = new ModuleReleaseVerifier(repository, hasher, manifest);
    }

    /** Performs only read-side release and directory checks; it never mutates campaign state. */
    public Result validate(CampaignArchiveDocument document) throws SQLException {
        if (document == null || document.module() == null) {
            return failure(Status.INVALID_DOCUMENT);
        }
        CampaignArchiveDocument.ModuleReference reference = document.module();
        ModuleReleaseVerifier.Result verified = releaseVerifier.verify(
                reference.moduleKey(), reference.releaseVersion());
        if (verified.status() == ModuleReleaseVerifier.Status.RELEASE_UNAVAILABLE) {
            return failure(Status.MODULE_UNAVAILABLE);
        }
        if (verified.status() == ModuleReleaseVerifier.Status.MODULE_HASH_MISMATCH
                || !ModuleReleaseVerifier.secureEquals(
                        verified.contentSha256(), reference.contentSha256())) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }
        if (!validReferences(document, verified.catalog())) {
            return failure(Status.INVALID_CATALOG_REFERENCE);
        }
        return new Result(Status.READY, verified.catalog());
    }

    private static Result failure(Status status) {
        return new Result(status, null);
    }

    private static boolean validReferences(
            CampaignArchiveDocument document, ModuleCatalog catalog) {
        Map<String, ModuleCatalog.FieldDefinition> fields = index(
                catalog.fieldDefinitions(), ModuleCatalog.FieldDefinition::fieldKey);
        Map<String, ModuleCatalog.ClassDefinition> classes = index(
                catalog.classDefinitions(), ModuleCatalog.ClassDefinition::classKey);
        Map<String, ModuleCatalog.ProficiencyTier> tiers = index(
                catalog.proficiencyTiers(), ModuleCatalog.ProficiencyTier::proficiencyKey);
        Map<String, ModuleCatalog.SkillDefinition> skills = index(
                catalog.skillDefinitions(), ModuleCatalog.SkillDefinition::skillKey);
        Map<String, ModuleCatalog.SaveDefinition> saves = index(
                catalog.saveDefinitions(), ModuleCatalog.SaveDefinition::saveKey);
        Map<String, ModuleCatalog.ItemTemplate> items = index(
                catalog.itemTemplates(), ModuleCatalog.ItemTemplate::itemKey);
        Map<String, ModuleCatalog.MapDefinition> maps = index(
                catalog.mapDefinitions(), ModuleCatalog.MapDefinition::mapKey);
        Map<String, ModuleCatalog.CheckDefinition> checks = index(
                catalog.checkDefinitions(), ModuleCatalog.CheckDefinition::checkKey);
        Map<String, ModuleCatalog.RollMode> rolls = index(
                catalog.rollModes(), ModuleCatalog.RollMode::rollModeKey);
        Map<String, ModuleCatalog.EventTemplate> events = index(
                catalog.eventTemplates(), ModuleCatalog.EventTemplate::eventKey);
        if (fields == null || classes == null || tiers == null || skills == null
                || saves == null || items == null || maps == null || checks == null
                || rolls == null || events == null) {
            return false;
        }

        Map<String, Set<String>> nodesByMap = new HashMap<>();
        for (ModuleCatalog.MapNode node : catalog.mapNodes()) {
            if (node == null || !maps.containsKey(node.mapKey())
                    || node.nodeKey() == null
                    || !nodesByMap.computeIfAbsent(node.mapKey(), ignored -> new HashSet<>())
                            .add(node.nodeKey())) {
                return false;
            }
        }
        Set<String> eventChecks = new HashSet<>();
        for (ModuleCatalog.EventCheck relation : catalog.eventChecks()) {
            if (relation == null || !events.containsKey(relation.eventKey())
                    || !checks.containsKey(relation.checkKey())
                    || !eventChecks.add(relation(relation.eventKey(), relation.checkKey()))) {
                return false;
            }
        }

        Map<String, Map<String, CampaignArchiveDocument.FieldValue>> valuesByCharacter =
                new HashMap<>();
        for (CampaignArchiveDocument.FieldValue value : document.fields()) {
            ModuleCatalog.FieldDefinition definition = fields.get(value.fieldKey());
            Map<String, CampaignArchiveDocument.FieldValue> characterValues =
                    valuesByCharacter.computeIfAbsent(value.characterKey(), ignored -> new HashMap<>());
            if (definition == null || characterValues.putIfAbsent(value.fieldKey(), value) != null
                    || !validFieldValue(value, definition)) {
                return false;
            }
        }
        if (!validDependentMaximums(valuesByCharacter, fields)) {
            return false;
        }

        for (CampaignArchiveDocument.ClassLevel value : document.classLevels()) {
            if (!classes.containsKey(value.classKey())) return false;
        }
        if (!validProficiencies(document.skillProficiencies(), skills.keySet(), tiers.keySet())
                || !validProficiencies(
                        document.saveProficiencies(), saves.keySet(), tiers.keySet())) {
            return false;
        }
        for (CampaignArchiveDocument.ItemState value : document.items()) {
            if ("MODULE".equals(value.sourceKind())) {
                ModuleCatalog.ItemTemplate template = items.get(value.itemKey());
                if (template == null || !template.displayName().equals(value.itemName())
                        || !template.description().equals(value.itemDescription())) {
                    return false;
                }
            }
        }
        for (CampaignArchiveDocument.MapState value : document.maps()) {
            ModuleCatalog.MapDefinition definition = maps.get(value.mapKey());
            Set<String> nodes = nodesByMap.getOrDefault(value.mapKey(), Set.of());
            if (definition == null || !definition.mapType().equals(value.mapType())
                    || !nodes.contains(value.partyNodeKey())) {
                return false;
            }
            if (value.encounter() != null) {
                for (CampaignArchiveDocument.Participant participant
                        : value.encounter().participants()) {
                    if (!nodes.contains(participant.nodeKey())) return false;
                }
            }
        }
        return validEvents(document.recentEvents(), fields.keySet(), skills.keySet(), saves.keySet(),
                checks, rolls, events.keySet(), eventChecks);
    }

    private static boolean validFieldValue(
            CampaignArchiveDocument.FieldValue value,
            ModuleCatalog.FieldDefinition definition) {
        if (!definition.dataType().equals(value.valueType())) return false;
        return switch (definition.dataType()) {
            case "TEXT" -> value.textValue() != null;
            case "BOOLEAN" -> value.booleanValue() != null;
            case "INTEGER" -> value.integerValue() != null
                    && withinInteger(value.integerValue(), definition.minimumValue(), true)
                    && withinInteger(value.integerValue(), definition.maximumValue(), false);
            case "DECIMAL" -> value.decimalValue() != null
                    && withinDecimal(value.decimalValue(), definition.minimumValue(), true)
                    && withinDecimal(value.decimalValue(), definition.maximumValue(), false);
            default -> false;
        };
    }

    private static boolean withinInteger(
            long value, ModuleCatalog.ScalarValue bound, boolean minimum) {
        if (bound == null) return true;
        return bound instanceof ModuleCatalog.IntegerValue integer
                && (minimum ? value >= integer.value() : value <= integer.value());
    }

    private static boolean withinDecimal(
            BigDecimal value, ModuleCatalog.ScalarValue bound, boolean minimum) {
        if (bound == null) return true;
        return bound instanceof ModuleCatalog.DecimalValue decimal
                && (minimum ? value.compareTo(decimal.value()) >= 0
                        : value.compareTo(decimal.value()) <= 0);
    }

    private static boolean validDependentMaximums(
            Map<String, Map<String, CampaignArchiveDocument.FieldValue>> valuesByCharacter,
            Map<String, ModuleCatalog.FieldDefinition> definitions) {
        for (Map<String, CampaignArchiveDocument.FieldValue> values : valuesByCharacter.values()) {
            for (CampaignArchiveDocument.FieldValue value : values.values()) {
                String maximumKey = definitions.get(value.fieldKey()).dependentMaxFieldKey();
                if (maximumKey != null) {
                    CampaignArchiveDocument.FieldValue maximum = values.get(maximumKey);
                    if (maximum == null || !lessThanOrEqual(value, maximum)) return false;
                }
            }
        }
        return true;
    }

    private static boolean lessThanOrEqual(
            CampaignArchiveDocument.FieldValue value,
            CampaignArchiveDocument.FieldValue maximum) {
        if (value.integerValue() != null && maximum.integerValue() != null) {
            return value.integerValue() <= maximum.integerValue();
        }
        return value.decimalValue() != null && maximum.decimalValue() != null
                && value.decimalValue().compareTo(maximum.decimalValue()) <= 0;
    }

    private static boolean validProficiencies(
            List<CampaignArchiveDocument.Proficiency> values,
            Set<String> targets,
            Set<String> tiers) {
        for (CampaignArchiveDocument.Proficiency value : values) {
            if (!targets.contains(value.targetKey()) || !tiers.contains(value.proficiencyKey())) {
                return false;
            }
        }
        return true;
    }

    private static boolean validEvents(
            List<CampaignArchiveDocument.EventSnapshot> snapshots,
            Set<String> fields,
            Set<String> skills,
            Set<String> saves,
            Map<String, ModuleCatalog.CheckDefinition> checks,
            Map<String, ModuleCatalog.RollMode> rolls,
            Set<String> events,
            Set<String> eventChecks) {
        for (CampaignArchiveDocument.EventSnapshot snapshot : snapshots) {
            CampaignArchiveDocument.CheckSnapshot check = snapshot.check();
            if (check == null) {
                if (!events.contains("event.note")) return false;
                continue;
            }
            ModuleCatalog.CheckDefinition definition = checks.get(check.checkKey());
            if (definition == null || !rolls.containsKey(check.rollModeKey())) return false;
            String source = check.modifierSourceKey();
            switch (definition.enumCode()) {
                case "ABILITY" -> {
                    if (!ordinaryCheck(check, source != null && fields.contains(source),
                            events, eventChecks)) return false;
                }
                case "SKILL" -> {
                    if (!ordinaryCheck(check, source != null && skills.contains(source),
                            events, eventChecks)) return false;
                }
                case "SAVING_THROW" -> {
                    if (!ordinaryCheck(check, source != null && saves.contains(source),
                            events, eventChecks)) return false;
                }
                case "MANUAL" -> {
                    if (check.eventKey() != null || source != null || check.manualName() == null) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean ordinaryCheck(
            CampaignArchiveDocument.CheckSnapshot check,
            boolean validSource,
            Set<String> events,
            Set<String> eventChecks) {
        return validSource && check.manualName() == null && check.eventKey() != null
                && events.contains(check.eventKey())
                && eventChecks.contains(relation(check.eventKey(), check.checkKey()));
    }

    private static String relation(String left, String right) {
        return left + "\u0000" + right;
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> keyFunction) {
        Map<String, T> result = new HashMap<>();
        for (T value : values) {
            if (value == null) return null;
            String key = keyFunction.apply(value);
            if (key == null || result.putIfAbsent(key, value) != null) return null;
        }
        return Map.copyOf(result);
    }

    public enum Status {
        READY,
        INVALID_DOCUMENT,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH,
        INVALID_CATALOG_REFERENCE
    }

    public record Result(Status status, ModuleCatalog catalog) {
        public Result {
            Objects.requireNonNull(status);
            if ((status == Status.READY) != (catalog != null)) {
                throw new IllegalArgumentException("Archive catalog does not match status");
            }
        }
    }
}
