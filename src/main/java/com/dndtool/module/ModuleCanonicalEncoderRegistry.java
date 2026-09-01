package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact canonical-format dispatcher; unknown versions never fall back to another encoder. */
final class ModuleCanonicalEncoderRegistry {
    private final Map<Integer, ModuleCanonicalEncoder> encoders;

    ModuleCanonicalEncoderRegistry() {
        this(List.of(new ModuleCanonicalEncoderV1(), new ModuleCanonicalEncoderV2()));
    }

    ModuleCanonicalEncoderRegistry(List<ModuleCanonicalEncoder> definitions) {
        Map<Integer, ModuleCanonicalEncoder> indexed = new HashMap<>();
        for (ModuleCanonicalEncoder encoder : definitions) {
            if (encoder == null || encoder.formatVersion() <= 0
                    || indexed.putIfAbsent(encoder.formatVersion(), encoder) != null) {
                throw new IllegalArgumentException("Invalid canonical encoder registry");
            }
        }
        encoders = Map.copyOf(indexed);
    }

    byte[] encode(ModuleCatalog catalog) throws ModuleCanonicalException {
        if (catalog == null || catalog.release() == null) {
            throw new ModuleCanonicalException();
        }
        ModuleCanonicalEncoder encoder = encoders.get(
                catalog.release().canonicalFormatVersion());
        if (encoder == null) {
            throw new ModuleCanonicalException();
        }
        return encoder.encode(catalog);
    }
}
