package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;
import java.util.Optional;

/** Looks up an independently approved digest by stable release identity and format version. */
public interface ModuleHashManifest {
    Optional<String> expectedSha256(ModuleCatalog.Release release);
}
