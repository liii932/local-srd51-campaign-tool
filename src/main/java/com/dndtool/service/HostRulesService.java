package com.dndtool.service;

import com.dndtool.module.BuiltinModuleHashManifest;
import com.dndtool.module.BuiltinModuleReleaseRegistry;
import com.dndtool.module.ModuleContentHasher;
import com.dndtool.persistence.CampaignModuleBindingRepository;
import com.dndtool.persistence.ModuleCatalog;
import com.dndtool.persistence.ModuleCatalogRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-only, fail-closed projection of the active campaign's frozen rule catalog. */
public final class HostRulesService {
    private static final int MAX_QUERY_CODE_POINTS = 80;

    private final CampaignModuleBindingRepository bindingRepository;
    private final ReleaseLookup releaseLookup;
    private final BuiltinModuleReleaseRegistry releaseRegistry;

    public HostRulesService(
            ModuleCatalogRepository moduleRepository,
            CampaignModuleBindingRepository bindingRepository) {
        this(bindingRepository,
                releaseLookup(Objects.requireNonNull(moduleRepository, "moduleRepository")),
                new BuiltinModuleReleaseRegistry());
    }

    HostRulesService(
            CampaignModuleBindingRepository bindingRepository,
            ReleaseLookup releaseLookup,
            BuiltinModuleReleaseRegistry releaseRegistry) {
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "bindingRepository");
        this.releaseLookup = Objects.requireNonNull(releaseLookup, "releaseLookup");
        this.releaseRegistry = Objects.requireNonNull(releaseRegistry, "releaseRegistry");
    }

    public Result load(String rawQuery, String rawType) throws SQLException {
        Search search = parseSearch(rawQuery, rawType);
        if (search == null) return failure(Status.INVALID_REQUEST);

        CampaignModuleBindingRepository.Binding active = null;
        for (CampaignModuleBindingRepository.Binding binding : bindingRepository.findAll()) {
            if (binding == null || !isKnownCampaignStatus(binding.campaignStatus())) {
                return failure(Status.INVALID_STATE);
            }
            if ("ACTIVE".equals(binding.campaignStatus())) {
                if (active != null) return failure(Status.INVALID_STATE);
                active = binding;
            }
        }
        if (active == null) return failure(Status.NO_ACTIVE_CAMPAIGN);

        BuiltinModuleReleaseRegistry.Resolution resolution = releaseRegistry.resolveReleased(
                active.frozenModuleKey(), active.frozenReleaseVersion());
        if (resolution.status() != BuiltinModuleReleaseRegistry.ResolutionStatus.READY) {
            return failure(Status.MODULE_UNAVAILABLE);
        }
        BuiltinModuleReleaseRegistry.Descriptor descriptor = resolution.descriptor();
        if (!ModuleReleaseVerifier.secureEquals(
                descriptor.contentSha256(), active.frozenContentSha256())) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }

        ModuleReleaseVerifier.Result verified = releaseLookup.verify(
                active.frozenModuleKey(), active.frozenReleaseVersion());
        if (verified.status() == ModuleReleaseVerifier.Status.RELEASE_UNAVAILABLE) {
            return failure(Status.MODULE_UNAVAILABLE);
        }
        if (verified.status() != ModuleReleaseVerifier.Status.READY) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }
        if (!ModuleReleaseVerifier.secureEquals(
                active.frozenContentSha256(), verified.contentSha256())
                || !validVerifiedCatalog(verified.catalog(), descriptor)) {
            return failure(Status.MODULE_HASH_MISMATCH);
        }

        List<RuleEntry> entries = project(verified.catalog()).stream()
                .filter(entry -> search.type() == null || entry.type() == search.type())
                .filter(entry -> matches(entry, search.query()))
                .toList();
        return new Result(
                Status.READY,
                new CatalogView(
                        descriptor.moduleKey(),
                        descriptor.releaseVersion(),
                        descriptor.canonicalFormatVersion(),
                        search.query(),
                        search.type(),
                        entries));
    }

    private static ReleaseLookup releaseLookup(ModuleCatalogRepository repository) {
        ModuleReleaseVerifier verifier = new ModuleReleaseVerifier(
                repository,
                new ModuleContentHasher()::sha256,
                new BuiltinModuleHashManifest());
        return verifier::verify;
    }

    private static boolean isKnownCampaignStatus(String status) {
        return "ACTIVE".equals(status) || "ARCHIVED".equals(status);
    }

    private static boolean validVerifiedCatalog(
            ModuleCatalog catalog,
            BuiltinModuleReleaseRegistry.Descriptor descriptor) {
        if (catalog == null || catalog.release() == null) return false;
        ModuleCatalog.Release release = catalog.release();
        return descriptor.moduleKey().equals(release.moduleKey())
                && descriptor.releaseVersion().equals(release.releaseVersion())
                && descriptor.canonicalFormatVersion() == release.canonicalFormatVersion()
                && descriptor.hashAlgorithm().equals(release.hashAlgorithm())
                && "RELEASED".equals(release.releaseStatus())
                && ModuleReleaseVerifier.secureEquals(
                        descriptor.contentSha256(), release.contentSha256());
    }

    private static Search parseSearch(String rawQuery, String rawType) {
        String query = rawQuery == null ? "" : rawQuery;
        if (!validUnicodeText(query) || containsControls(query)) return null;
        query = Normalizer.normalize(query.strip(), Normalizer.Form.NFC);
        if (query.codePointCount(0, query.length()) > MAX_QUERY_CODE_POINTS) return null;

        RuleType type = null;
        if (rawType != null && !rawType.isBlank()) {
            if (!validUnicodeText(rawType) || containsControls(rawType)) return null;
            try {
                type = RuleType.valueOf(rawType.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        return new Search(query, type);
    }

    private static boolean validUnicodeText(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(++index))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsControls(String value) {
        return value.codePoints().anyMatch(codePoint ->
                codePoint <= 0x1f || (codePoint >= 0x7f && codePoint <= 0x9f));
    }

    private static boolean matches(RuleEntry entry, String query) {
        if (query.isEmpty()) return true;
        String needle = query.toLowerCase(Locale.ROOT);
        return entry.key().toLowerCase(Locale.ROOT).contains(needle)
                || entry.displayName().toLowerCase(Locale.ROOT).contains(needle)
                || entry.summary().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static List<RuleEntry> project(ModuleCatalog catalog) {
        List<RuleEntry> entries = new ArrayList<>();
        for (ModuleCatalog.RuleConstant value : catalog.ruleConstants()) {
            add(entries, RuleType.RULE_CONSTANT, value.constantKey(), value.constantKey(),
                    scalar(value.value()));
        }
        for (ModuleCatalog.FieldDefinition value : catalog.fieldDefinitions()) {
            add(entries, RuleType.FIELD, value.fieldKey(), value.displayName(),
                    value.description());
        }
        for (ModuleCatalog.ClassDefinition value : catalog.classDefinitions()) {
            add(entries, RuleType.CLASS, value.classKey(), value.displayName(), "职业定义");
        }
        for (ModuleCatalog.ProficiencyTier value : catalog.proficiencyTiers()) {
            add(entries, RuleType.PROFICIENCY_TIER, value.proficiencyKey(), value.enumCode(),
                    value.numerator() + "/" + value.denominator()
                            + " · " + value.roundingAlgorithm());
        }
        for (ModuleCatalog.SkillDefinition value : catalog.skillDefinitions()) {
            add(entries, RuleType.SKILL, value.skillKey(), value.displayName(),
                    "属性：" + value.abilityFieldKey());
        }
        for (ModuleCatalog.SaveDefinition value : catalog.saveDefinitions()) {
            add(entries, RuleType.SAVE, value.saveKey(), value.saveKey(),
                    "属性：" + value.abilityFieldKey());
        }
        for (ModuleCatalog.ItemTemplate value : catalog.itemTemplates()) {
            add(entries, RuleType.ITEM, value.itemKey(), value.displayName(),
                    value.description());
        }
        for (ModuleCatalog.EntityTemplate value : catalog.entityTemplates()) {
            add(entries, RuleType.ENTITY_TEMPLATE, value.templateKey(), value.displayName(),
                    "实体模板");
        }
        for (ModuleCatalog.CheckDefinition value : catalog.checkDefinitions()) {
            add(entries, RuleType.CHECK, value.checkKey(), value.enumCode(),
                    "修正算法：" + value.modifierAlgorithm());
        }
        for (ModuleCatalog.RollMode value : catalog.rollModes()) {
            add(entries, RuleType.ROLL_MODE, value.rollModeKey(), value.enumCode(),
                    "候选骰：" + value.candidateCount()
                            + " · 选择算法：" + value.selectionAlgorithm());
        }
        for (ModuleCatalog.EventTemplate value : catalog.eventTemplates()) {
            add(entries, RuleType.EVENT, value.eventKey(), value.displayName(), "事件模板");
        }
        for (ModuleCatalog.EffectDefinition value : catalog.effectDefinitions()) {
            add(entries, RuleType.EFFECT, value.effectKey(), value.effectKey(),
                    "执行算法：" + value.executionAlgorithm());
        }
        for (ModuleCatalog.MapDefinition value : catalog.mapDefinitions()) {
            add(entries, RuleType.MAP, value.mapKey(), value.mapKey(),
                    "地图类型：" + value.mapType());
        }
        for (ModuleCatalog.MapNode value : catalog.mapNodes()) {
            add(entries, RuleType.MAP_NODE, value.nodeKey(), value.displayName(),
                    "地图：" + value.mapKey());
        }
        return List.copyOf(entries);
    }

    private static void add(
            List<RuleEntry> entries,
            RuleType type,
            String key,
            String displayName,
            String summary) {
        entries.add(new RuleEntry(type, key, displayName, summary));
    }

    private static String scalar(ModuleCatalog.ScalarValue value) {
        return switch (value) {
            case ModuleCatalog.TextValue text -> text.value();
            case ModuleCatalog.IdentifierValue identifier -> identifier.value();
            case ModuleCatalog.IntegerValue integer -> Long.toString(integer.value());
            case ModuleCatalog.DecimalValue decimal -> decimal(decimal.value());
            case ModuleCatalog.BooleanValue bool -> Boolean.toString(bool.value());
        };
    }

    private static String decimal(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static Result failure(Status status) {
        return new Result(status, null);
    }

    public record Result(Status status, CatalogView catalog) {
        public Result {
            Objects.requireNonNull(status, "status");
            if ((status == Status.READY) != (catalog != null)) {
                throw new IllegalArgumentException("Rule catalog does not match status");
            }
        }
    }

    public record CatalogView(
            String moduleKey,
            String releaseVersion,
            int canonicalFormatVersion,
            String query,
            RuleType selectedType,
            List<RuleEntry> entries) {
        public CatalogView {
            Objects.requireNonNull(moduleKey, "moduleKey");
            Objects.requireNonNull(releaseVersion, "releaseVersion");
            Objects.requireNonNull(query, "query");
            entries = List.copyOf(entries);
        }
    }

    public record RuleEntry(RuleType type, String key, String displayName, String summary) {
        public RuleEntry {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(summary, "summary");
        }
    }

    public enum RuleType {
        RULE_CONSTANT,
        FIELD,
        CLASS,
        PROFICIENCY_TIER,
        SKILL,
        SAVE,
        ITEM,
        ENTITY_TEMPLATE,
        CHECK,
        ROLL_MODE,
        EVENT,
        EFFECT,
        MAP,
        MAP_NODE
    }

    public enum Status {
        READY,
        INVALID_REQUEST,
        NO_ACTIVE_CAMPAIGN,
        MODULE_UNAVAILABLE,
        MODULE_HASH_MISMATCH,
        INVALID_STATE
    }

    @FunctionalInterface
    interface ReleaseLookup {
        ModuleReleaseVerifier.Result verify(String moduleKey, String releaseVersion)
                throws SQLException;
    }

    private record Search(String query, RuleType type) {
    }
}
