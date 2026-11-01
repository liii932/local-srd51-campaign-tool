package com.dndtool.persistence.storage;

/** Stable declaration diagnostics contain a category and an application-resource path. */
public final class StorageSchemaException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String path;

    StorageSchemaException(String code, String path) {
        super(code + " at " + path);
        this.code = code;
        this.path = path;
    }

    public String code() { return code; }
    public String path() { return path; }
}
