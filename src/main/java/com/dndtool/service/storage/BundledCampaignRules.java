package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2RulesBinding;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Application-delivered import candidates; their presence never grants acceptance. */
public final class BundledCampaignRules {
    public static final String STANDARD = "standard";
    public static final String CHECK_VARIANT = "check-variant";
    public static final String CORE_MODULE = "srd51_runtime";
    public static final String CHARACTER_MODULE = "srd51_characters";
    public static final String MAGIC_MODULE = "srd51_magic";
    public static final String HOUSE_RULE_MODULE = "dm_runtime_house_rules";

    private BundledCampaignRules() { }

    public static ProductionSchema2Projection projection() { return projection(STANDARD); }
    public static List<ProductionRuleArtifact> artifacts() { return artifacts(STANDARD); }

    public static ProductionSchema2Projection projection(String combination) { return selected(combination).projection(); }
    public static List<ProductionRuleArtifact> artifacts(String combination) { return selected(combination).artifacts(); }

    public static List<ProductionRuleArtifact> artifactsAll() {
        var all = new ArrayList<>(artifacts(STANDARD));
        all.add(artifacts(CHECK_VARIANT).getLast());
        return List.copyOf(all);
    }

    private static Bundle selected(String combination) {
        Bundle bundle = combination == null ? null : Combinations.BUNDLES.get(combination);
        if (bundle == null)
            throw new StorageException("UNSUPPORTED_RULE_SELECTION", "Unknown exact bundled rule combination");
        return bundle;
    }

    private static ProductionRuleArtifact load(String name) {
        try (InputStream source = BundledCampaignRules.class.getResourceAsStream("/storage/rules/" + name + ".zip")) {
            if (source == null) throw new IllegalStateException("STORAGE_BUNDLED_RULE_MISSING");
            byte[] bytes = source.readNBytes(ProductionRuleArtifact.MAX_ZIP_BYTES + 1);
            return ProductionRuleArtifact.read(bytes);
        } catch (IOException failure) {
            throw new IllegalStateException("STORAGE_BUNDLED_RULE_READ", failure);
        }
    }

    private record Bundle(List<ProductionRuleArtifact> artifacts, ProductionSchema2Projection projection,
                          Schema2RulesBinding binding) { }

    private static final class Combinations {
        private static final List<ProductionRuleArtifact> SHARED = List.of(
                load("srd51-runtime"), load("srd51-characters"), load("srd51-magic"));
        private static final Map<String, Bundle> BUNDLES = Map.of(
                STANDARD, bundle("dm-runtime-house-rules"),
                CHECK_VARIANT, bundle("dm-runtime-check-variant"));

        private static Bundle bundle(String fourth) {
            var selected = new ArrayList<>(SHARED);
            selected.add(load(fourth));
            var projection = ProductionSchema2Projection.compose(selected);
            return new Bundle(List.copyOf(selected), projection, Schema2RulesBinding.from(projection));
        }
    }
}
