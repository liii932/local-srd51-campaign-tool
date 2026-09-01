package com.dndtool.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Minimal read-only projection of every character's saved frozen-module identity. */
public interface CharacterModuleBindingRepository {
    List<Binding> findAll() throws SQLException;

    /** Resolves the saved release identity for one character before a command is prepared. */
    default Optional<Binding> findByCharacterKey(String characterKey) throws SQLException {
        Binding found = null;
        for (Binding binding : findAll()) {
            if (binding != null && characterKey != null
                    && characterKey.equals(binding.characterKey())) {
                if (found != null) throw new SQLException("Duplicate character module binding");
                found = binding;
            }
        }
        return Optional.ofNullable(found);
    }

    record Binding(
            String campaignKey,
            String characterKey,
            String savedModuleKey,
            String savedReleaseVersion,
            String savedContentSha256) {
    }
}
