package com.dndtool.persistence;

import java.util.List;

/** Immutable result shared by startup validation and the host-only diagnostic endpoint. */
public record DatabaseSchemaStatus(
        State state,
        int schemaVersion,
        String scriptName,
        String scriptSha256) {

    public enum State {
        READY,
        JNDI_UNAVAILABLE,
        DATABASE_UNAVAILABLE,
        SCHEMA_MISMATCH,
        PACKAGED_SCRIPT_INVALID,
        MODULE_HASH_MISMATCH
    }

    /** Reports the newest migration after the complete packaged chain has been verified. */
    static DatabaseSchemaStatus ready(List<SchemaMigrations.Expectation> expectations) {
        SchemaMigrations.Expectation latest = expectations.get(expectations.size() - 1);
        return new DatabaseSchemaStatus(
                State.READY,
                latest.version(),
                latest.scriptName(),
                latest.scriptSha256());
    }

    static DatabaseSchemaStatus failure(State state) {
        return new DatabaseSchemaStatus(state, 0, null, null);
    }

    public boolean isReady() {
        return state == State.READY;
    }
}
