package com.dndtool.module;

import java.util.Comparator;
import java.util.Objects;

/** One retained language or tool grant, including its exact owning source and slot. */
public record CharacterProficiencyGrant(
        Category category,
        String sourceModuleKey,
        String sourceType,
        String sourceKey,
        String grantSlotKey,
        GrantKind grantKind,
        String targetModuleKey,
        String proficiencyKey,
        boolean suppressed) {
    public static final Comparator<CharacterProficiencyGrant> ORDER = Comparator
            .comparing(CharacterProficiencyGrant::category)
            .thenComparing(CharacterProficiencyGrant::sourceModuleKey)
            .thenComparing(CharacterProficiencyGrant::sourceType)
            .thenComparing(CharacterProficiencyGrant::sourceKey)
            .thenComparing(CharacterProficiencyGrant::grantSlotKey)
            .thenComparing(CharacterProficiencyGrant::grantKind)
            .thenComparing(CharacterProficiencyGrant::targetModuleKey)
            .thenComparing(CharacterProficiencyGrant::proficiencyKey)
            .thenComparing(CharacterProficiencyGrant::suppressed);

    public CharacterProficiencyGrant {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(grantKind, "grantKind");
        module(sourceModuleKey);
        module(targetModuleKey);
        String sourcePrefix = switch (Objects.requireNonNull(sourceType, "sourceType")) {
            case "character.race" -> "race.";
            case "character.subrace" -> "subrace.";
            case "character.background" -> "background.";
            case "character.class" -> "class.";
            default -> throw new IllegalArgumentException("Unsupported proficiency source type");
        };
        key(sourceKey, sourcePrefix);
        key(proficiencyKey, category == Category.LANGUAGE ? "language." : "tool.");
        key(grantSlotKey, "");
    }

    public enum Category {
        LANGUAGE("character.language"), TOOL("character.tool");
        private final String catalogType;
        Category(String catalogType) { this.catalogType = catalogType; }
        public String catalogType() { return catalogType; }
    }

    public enum GrantKind { FIXED, CHOICE }

    private static void module(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid grant module identity");
        }
    }

    private static void key(String value, String prefix) {
        if (value == null || value.length() > 128 || !value.startsWith(prefix)
                || !value.matches("[a-z][a-z0-9_]*(?:[.][a-z0-9_]+)+")) {
            throw new IllegalArgumentException("Invalid grant rule or slot identity");
        }
    }
}
