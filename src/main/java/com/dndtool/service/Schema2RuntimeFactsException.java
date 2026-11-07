package com.dndtool.service;

/** Invalid typed runtime history or an exhausted history budget. */
public final class Schema2RuntimeFactsException extends RuntimeException {
    Schema2RuntimeFactsException(String message) {
        super(message);
    }

    Schema2RuntimeFactsException(String message, Throwable cause) {
        super(message, cause);
    }
}
