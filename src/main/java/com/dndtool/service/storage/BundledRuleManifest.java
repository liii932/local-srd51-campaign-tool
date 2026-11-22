package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.module.composition.Schema2CompositionKernel.Role;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.Map;

/** Build-generated identities for the fixed ZIP slots shipped with the application. */
final class BundledRuleManifest {
    static final String RESOURCE = "bundled-members.tsv";
    static final String HEADER = "bundled-members-v1";
    private static final int MAX_BYTES = 8192;

    private BundledRuleManifest() { }

    enum Slot {
        CORE("srd51-runtime.zip", BundledCampaignRules.CORE_MODULE, Role.BASE),
        CHARACTERS("srd51-characters.zip", BundledCampaignRules.CHARACTER_MODULE, Role.EXTENSION),
        MAGIC("srd51-magic.zip", "srd51_magic", Role.EXTENSION),
        HOUSE("dm-runtime-house-rules.zip", BundledCampaignRules.HOUSE_RULE_MODULE, Role.EXTENSION),
        CHECK_VARIANT("dm-runtime-check-variant.zip", BundledCampaignRules.HOUSE_RULE_MODULE, Role.EXTENSION);

        final String fileName;
        private final String moduleKey;
        private final Role role;

        Slot(String fileName, String moduleKey, Role role) {
            this.fileName = fileName;
            this.moduleKey = moduleKey;
            this.role = role;
        }

        boolean matches(StoredMember member) {
            return moduleKey.equals(member.moduleKey()) && role == member.role();
        }
    }

    record Entry(StoredMember member, String name) {
        static Entry from(ProductionRuleArtifact artifact) {
            return new Entry(new StoredMember(artifact.moduleKey(), artifact.releaseVersion(), artifact.role(),
                    artifact.rawZipSha256(), artifact.logicalSha256()), artifact.authorPackage().packageDisplayName());
        }

        void validate(Slot slot) {
            if (!slot.matches(member) || !member.releaseVersion().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                    || !member.rawZipSha256().matches("[0-9a-f]{64}")
                    || !member.logicalSha256().matches("[0-9a-f]{64}")
                    || name.codePointCount(0, name.length()) > 120
                    || name.codePoints().anyMatch(Character::isISOControl)
                    || name.codePoints().allMatch(point -> Character.isWhitespace(point) || Character.isSpaceChar(point))
                    || !Normalizer.isNormalized(name, Normalizer.Form.NFC)) {
                throw new IllegalArgumentException("Invalid bundled rule identity for " + slot.fileName);
            }
        }
    }

    static Map<Slot, Entry> read(InputStream source) throws IOException {
        if (source == null) throw new IOException("Missing bundled rule manifest");
        byte[] bytes = source.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Bundled rule manifest exceeds its byte budget");
        String[] rows = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().split("\n", -1);
        Slot[] slots = Slot.values();
        if (rows.length != slots.length + 2 || !HEADER.equals(rows[0]) || !rows[rows.length - 1].isEmpty())
            throw new IllegalArgumentException("Unexpected bundled rule manifest format");
        var entries = new EnumMap<Slot, Entry>(Slot.class);
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i];
            String[] columns = rows[i + 1].split("\t", -1);
            if (columns.length != 7 || !slot.fileName.equals(columns[0]))
                throw new IllegalArgumentException("Unexpected bundled rule manifest slot " + slot.fileName);
            var entry = new Entry(new StoredMember(columns[1], columns[2], Role.valueOf(columns[3]),
                    columns[4], columns[5]), columns[6]);
            entry.validate(slot);
            entries.put(slot, entry);
        }
        requireDistinctHouseReleases(entries);
        return Map.copyOf(entries);
    }

    static void requireDistinctHouseReleases(Map<Slot, Entry> entries) {
        if (entries.get(Slot.HOUSE).member().releaseVersion().equals(entries.get(Slot.CHECK_VARIANT).member().releaseVersion()))
            throw new IllegalArgumentException("Bundled house rules must have distinct releases");
    }

    static ProductionRuleArtifact readArtifact(InputStream source) throws IOException {
        if (source == null) throw new IOException("Missing bundled rule ZIP");
        return ProductionRuleArtifact.read(source.readNBytes(ProductionRuleArtifact.MAX_ZIP_BYTES + 1));
    }
}
