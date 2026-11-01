package com.dndtool.service.storage;

/** Stable, bounded-profile failures suitable for the local Host. */
public final class StorageException extends RuntimeException {
    private final String code;

    public StorageException(String code, String message) {
        super(message);
        this.code = code;
    }

    public StorageException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() { return code; }
}
