package com.dndtool.module;


/** Encodes exactly one approved canonical-format version. */
interface ModuleCanonicalEncoder {
    int formatVersion();

    byte[] encode(ModuleCatalog catalog) throws ModuleCanonicalException;
}
