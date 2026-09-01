package com.dndtool.module;

/** Indicates that a module projection cannot be represented by its declared canonical format. */
public final class ModuleCanonicalException extends Exception {
    private static final long serialVersionUID = 1L;

    public ModuleCanonicalException() {
        // Deliberately omit database values so callers cannot leak rule content in errors.
        super("Invalid module canonical projection");
    }
}
