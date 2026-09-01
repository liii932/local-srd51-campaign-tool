package com.dndtool.service;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Generates the stable server-owned identity for a newly created campaign. */
public final class CampaignCreationIdentityFactory {
    private final Supplier<UUID> uuidSupplier;

    /** Uses Java's cryptographically strong random version-4 UUID generator in production. */
    public CampaignCreationIdentityFactory() {
        this(UUID::randomUUID);
    }

    /** Package-private deterministic seam for unit tests. */
    CampaignCreationIdentityFactory(Supplier<UUID> uuidSupplier) {
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier);
    }

    /** Returns one canonical lowercase key; callers cannot provide or replace this value. */
    public String newCampaignKey() {
        UUID uuid = Objects.requireNonNull(uuidSupplier.get(), "generated UUID");
        if (uuid.version() != 4 || uuid.variant() != 2) {
            throw new IllegalStateException("Campaign identity generator returned a non-v4 UUID");
        }
        return uuid.toString();
    }
}
