package com.dndtool.service;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adds the server-owned identity to an ordinary new-character request.
 *
 * <p>The request type deliberately has no {@code characterKey} component. Callers therefore
 * cannot provide or overwrite the stable identity through the normal creation boundary. The
 * database's global unique constraint remains the final collision guard when persistence is
 * added to this boundary.
 */
public final class CharacterCreationIdentityFactory {
    private final Supplier<UUID> uuidSupplier;

    /** Uses Java's cryptographically strong random version-4 UUID generator in production. */
    public CharacterCreationIdentityFactory() {
        this(UUID::randomUUID);
    }

    /** Package-private deterministic seam for unit tests. */
    CharacterCreationIdentityFactory(Supplier<UUID> uuidSupplier) {
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier);
    }

    /** Returns an immutable prepared character carrying a newly generated canonical key. */
    public PreparedCharacter prepare(NewCharacterRequest request) {
        Objects.requireNonNull(request, "request");
        // Normalize before allocating an identity so an invalid request cannot consume a key.
        String characterName = CharacterNamePolicy.normalize(request.characterName());
        UUID uuid = Objects.requireNonNull(uuidSupplier.get(), "generated UUID");
        if (uuid.version() != 4 || uuid.variant() != 2) {
            throw new IllegalStateException("Character identity generator returned a non-v4 UUID");
        }
        return new PreparedCharacter(
                uuid.toString(), request.campaignKey(), request.characterType(),
                characterName);
    }

    /**
     * Ordinary creation input. The key is intentionally absent and can only be added by
     * {@link #prepare(NewCharacterRequest)}.
     */
    public record NewCharacterRequest(
            String campaignKey, CharacterType characterType, String characterName) {
        public NewCharacterRequest {
            Objects.requireNonNull(campaignKey, "campaignKey");
            Objects.requireNonNull(characterType, "characterType");
            Objects.requireNonNull(characterName, "characterName");
        }
    }

    /** Immutable server-prepared values for the future character creation transaction. */
    public static final class PreparedCharacter {
        private final String characterKey;
        private final String campaignKey;
        private final CharacterType characterType;
        private final String characterName;

        private PreparedCharacter(
                String characterKey,
                String campaignKey,
                CharacterType characterType,
                String characterName) {
            this.characterKey = characterKey;
            this.campaignKey = campaignKey;
            this.characterType = characterType;
            this.characterName = characterName;
        }

        public String characterKey() {
            return characterKey;
        }

        public String campaignKey() {
            return campaignKey;
        }

        public CharacterType characterType() {
            return characterType;
        }

        public String characterName() {
            return characterName;
        }
    }

    /** The two authoritative character kinds stored by the shared character table. */
    public enum CharacterType {
        PC,
        NPC
    }
}
