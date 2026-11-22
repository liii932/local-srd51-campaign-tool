package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.service.storage.BundledRuleManifest.Entry;
import com.dndtool.service.storage.BundledRuleManifest.Slot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;

/** Offline build entry point for the application and test-fixture manifests. */
public final class BundledRuleManifestGenerator {
    private BundledRuleManifestGenerator() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2)
            throw new IllegalArgumentException("Usage: BundledRuleManifestGenerator <zip-directory> <output-file>");
        Path directory = Path.of(args[0]);
        var rows = new StringBuilder(BundledRuleManifest.HEADER).append('\n');
        var entries = new EnumMap<Slot, Entry>(Slot.class);
        for (Slot slot : Slot.values()) {
            Entry entry;
            try (var source = Files.newInputStream(directory.resolve(slot.fileName))) {
                entry = Entry.from(BundledRuleManifest.readArtifact(source));
            }
            entry.validate(slot);
            entries.put(slot, entry);
            StoredMember member = entry.member();
            rows.append(String.join("\t", slot.fileName, member.moduleKey(), member.releaseVersion(), member.role().name(),
                    member.rawZipSha256(), member.logicalSha256(), entry.name())).append('\n');
        }
        BundledRuleManifest.requireDistinctHouseReleases(entries);
        Path output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, rows);
    }
}
