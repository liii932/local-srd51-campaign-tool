package com.dndtool.service.storage;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Accurate application-bundled finite rules, separate from the schema-2 source ledger. */
public final class CampaignRules {
    public static final String BASE = "srd51-vitality@1";
    public static final String HEALING = "srd51-healing@1";
    public static final String RUNTIME = "srd51-runtime@1";
    public static final String ALGORITHM = "srd51-capped-heal-1";
    public static final long MAXIMUM_HP = 65535;
    public record RuleModule(String id, String name, List<String> requires, List<String> storageRoots) { }
    public static final List<RuleModule> CATALOG = List.of(
            new RuleModule(BASE, "SRD 5.1 生命值基础（有限规则）", List.of(), List.of("core", "rules", "characters", "history")),
            new RuleModule(HEALING, "SRD 5.1 定额治疗", List.of(BASE), List.of("health")),
            new RuleModule(RUNTIME, "SRD 5.1 当前应用规则：角色、冒险与战斗", List.of(BASE, HEALING), List.of("runtime")));
    private static final Map<String, String> MATERIAL = Map.of(
            BASE, "srd51-vitality@1;character=name,maximumHp,currentHp;minimumCurrentHp=1;maximumHpLimit=65535;no-death-or-growth",
            HEALING, "srd51-healing@1;requires=srd51-vitality@1;algorithm=srd51-capped-heal-1;after=min(maximumHp,currentHp+amount);amount=integer-0..65535");
    private CampaignRules() { }

    public static List<String> validate(List<String> selected) {
        if (selected == null || !(selected.equals(List.of(BASE)) || selected.equals(List.of(BASE, HEALING)) || selected.equals(List.of(BASE, HEALING, RUNTIME)))) {
            throw new StorageException("UNSUPPORTED_RULE_SELECTION", "Select the exact base, optionally followed by healing and then the complete current runtime profile; dependencies and order are explicit");
        }
        return List.copyOf(selected);
    }
    public static Set<String> roots(List<String> selected) {
        validate(selected); Set<String> roots = new TreeSet<>(List.of("core", "rules", "characters", "history"));
        if (selected.contains(HEALING)) roots.add("health");
        if (selected.contains(RUNTIME)) roots.add("runtime");
        return Set.copyOf(roots);
    }
    public static String materialDigest(String module) {
        if (RUNTIME.equals(module)) {
            var material = BundledCampaignRules.projection();
            return CampaignJson.digest("DND-BUNDLED-CAMPAIGN-RULE", (material.compositionSha256() + material.finalRulesSha256() + material.chainSha256()).getBytes(StandardCharsets.US_ASCII));
        }
        String material = MATERIAL.get(module);
        if (material == null) throw new StorageException("UNSUPPORTED_RULE_SELECTION", "Unknown exact bundled rule module");
        return CampaignJson.digest("DND-BUNDLED-VITALITY-RULE", material.getBytes(StandardCharsets.UTF_8));
    }
    public static void hp(long maximum, long current) {
        if (maximum < 1 || maximum > MAXIMUM_HP || current < 1 || current > maximum) throw new StorageException("INVALID_HP", "This profile requires 1 <= current HP <= maximum HP <= 65535");
    }
}
