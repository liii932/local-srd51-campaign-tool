package com.dndtool.module;

import com.dndtool.persistence.ModuleCatalog;

/** Encodes exactly one approved canonical-format version. */
interface ModuleCanonicalEncoder {
    int formatVersion();

    byte[] encode(ModuleCatalog catalog) throws ModuleCanonicalException;
}
