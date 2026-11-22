package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.service.storage.BundledRuleManifest.Entry;
import com.dndtool.service.storage.BundledRuleManifest.Slot;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Application-delivered standard rules and import candidates for explicit selections. */
public final class BundledCampaignRules {
    public static final String STANDARD = "standard";
    public static final String CHECK_VARIANT = "check-variant";
    public static final String CORE_MODULE = "srd51_runtime";
    public static final String CHARACTER_MODULE = "srd51_characters";
    public static final String HOUSE_RULE_MODULE = "dm_runtime_house_rules";
    private static final List<Slot> STANDARD_SLOTS = List.of(Slot.CORE, Slot.CHARACTERS, Slot.MAGIC, Slot.HOUSE);
    private static final List<Slot> VARIANT_SLOTS = List.of(Slot.CORE, Slot.CHARACTERS, Slot.MAGIC, Slot.CHECK_VARIANT);

    private BundledCampaignRules() { }

    public record BundledCandidate(StoredMember member, String name, boolean available) { }

    public static ProductionSchema2Projection projection() { return projection(STANDARD); }
    public static ProductionSchema2Projection projection(String combination) { return selected(combination).projection(); }
    public static List<ProductionRuleArtifact> artifacts(String combination) { return selected(combination).artifacts(); }

    public static List<BundledCandidate> candidates() {
        Catalog catalog = Delivered.CATALOG;
        if (catalog.manifestFailure() != null) return List.of();
        return Arrays.stream(Slot.values()).map(Delivered::candidate)
                .map(candidate -> new BundledCandidate(candidate.entry().member(), candidate.entry().name(),
                        candidate.failure() == null)).toList();
    }

    public static ProductionRuleArtifact importCandidate(StoredMember member) {
        Catalog catalog = Delivered.CATALOG;
        if (catalog.manifestFailure() != null) throw unavailable("candidate import", catalog.manifestFailure());
        var slot = catalog.entries().entrySet().stream()
                .filter(entry -> entry.getValue().member().equals(member)).map(Map.Entry::getKey)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("INVALID_REQUEST"));
        return Delivered.candidate(slot).requiredArtifact();
    }

    static Optional<ProductionSchema2Projection> standardProjectionFor(List<StoredMember> members) {
        Catalog catalog = Delivered.CATALOG;
        if (catalog.manifestFailure() != null) {
            // The declared labels can exclude a standard binding even when its exact digests are unavailable.
            if (hasStandardLabels(members)) throw unavailable(STANDARD, catalog.manifestFailure());
            return Optional.empty();
        }
        var expected = STANDARD_SLOTS.stream().map(slot -> catalog.entries().get(slot).member()).toList();
        return expected.equals(members) ? Optional.of(projection()) : Optional.empty();
    }

    private static boolean hasStandardLabels(List<StoredMember> members) {
        if (members.size() != STANDARD_SLOTS.size()) return false;
        for (int i = 0; i < STANDARD_SLOTS.size(); i++) {
            if (!STANDARD_SLOTS.get(i).matches(members.get(i))) return false;
        }
        return true;
    }

    private static Bundle selected(String combination) {
        Bundle bundle = switch (combination) {
            case STANDARD -> Standard.BUNDLE;
            case CHECK_VARIANT -> CheckVariant.BUNDLE;
            case null, default -> throw new StorageException("UNSUPPORTED_RULE_SELECTION", "Unknown exact bundled rule combination");
        };
        if (bundle.failure() != null) throw unavailable(combination, bundle.failure());
        return bundle;
    }

    private static StorageException unavailable(String name, Exception cause) {
        return new StorageException("BUNDLED_RULES_UNAVAILABLE", "Bundled rules are unavailable for " + name, cause);
    }

    private static InputStream resource(String fileName) {
        return BundledCampaignRules.class.getResourceAsStream("/storage/rules/" + fileName);
    }

    private static Catalog loadCatalog() {
        try (var source = resource(BundledRuleManifest.RESOURCE)) {
            return new Catalog(BundledRuleManifest.read(source), null);
        } catch (IOException | RuntimeException failure) {
            return new Catalog(Map.of(), failure);
        }
    }

    private static Candidate loadCandidate(Slot slot, Entry entry) {
        try (var source = resource(slot.fileName)) {
            var artifact = BundledRuleManifest.readArtifact(source);
            if (!entry.equals(Entry.from(artifact))) throw new IllegalArgumentException("Bundled ZIP differs from its declared identity");
            return new Candidate(entry, artifact, null);
        } catch (IOException | RuntimeException failure) {
            return new Candidate(entry, null, failure);
        }
    }

    private static Bundle compose(List<Slot> slots) {
        var manifestFailure = Delivered.CATALOG.manifestFailure();
        if (manifestFailure != null) return new Bundle(List.of(), null, manifestFailure);
        try {
            var artifacts = slots.stream().map(slot -> Delivered.candidate(slot).requiredArtifact()).toList();
            var projection = ProductionSchema2Projection.compose(artifacts);
            CampaignRuleSupport.validate(projection);
            return new Bundle(artifacts, projection, null);
        } catch (RuntimeException failure) {
            return new Bundle(List.of(), null, failure);
        }
    }

    private record Candidate(Entry entry, ProductionRuleArtifact artifact, Exception failure) {
        ProductionRuleArtifact requiredArtifact() {
            if (failure != null) throw unavailable(entry.member().moduleKey() + "@" + entry.member().releaseVersion(), failure);
            return artifact;
        }
    }

    private record Bundle(List<ProductionRuleArtifact> artifacts, ProductionSchema2Projection projection, Exception failure) { }
    private record Catalog(Map<Slot, Entry> entries, Exception manifestFailure) { }

    private static final class Delivered {
        private static final Catalog CATALOG = loadCatalog();
        private static final Map<Slot, Candidate> CANDIDATES = new ConcurrentHashMap<>();

        private static Candidate candidate(Slot slot) {
            return CANDIDATES.computeIfAbsent(slot, key -> loadCandidate(key, CATALOG.entries().get(key)));
        }
    }

    private static final class Standard {
        private static final Bundle BUNDLE = compose(STANDARD_SLOTS);
    }

    private static final class CheckVariant {
        private static final Bundle BUNDLE = compose(VARIANT_SLOTS);
    }
}
