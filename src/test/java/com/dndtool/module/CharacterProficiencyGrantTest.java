package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndtool.module.CharacterProficiencyGrant.Category;
import com.dndtool.module.CharacterProficiencyGrant.GrantKind;
import org.junit.jupiter.api.Test;

class CharacterProficiencyGrantTest {
    @Test
    void retainsBothFullOwnersAndSuppressedPreviouslyChosenFacts() {
        CharacterProficiencyGrant grant = new CharacterProficiencyGrant(Category.LANGUAGE,
                "custom_origin", "character.race", "race.elf", "creation.language.choice",
                GrantKind.CHOICE, "custom_languages", "language.sylvan", true);
        assertEquals("custom_origin", grant.sourceModuleKey());
        assertEquals("custom_languages", grant.targetModuleKey());
        assertEquals("character.language", grant.category().catalogType());
        assertEquals(true, grant.suppressed());
    }

    @Test
    void rejectsMalformedCrossCategoryAndUnknownSourceIdentities() {
        assertThrows(IllegalArgumentException.class, () -> new CharacterProficiencyGrant(
                Category.TOOL, "base", "character.race", "race.elf", "creation.tool.fixed",
                GrantKind.FIXED, "base", "language.elvish", false));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProficiencyGrant(
                Category.TOOL, "base", "character.feature", "feature.artisan", "creation.tool.fixed",
                GrantKind.FIXED, "base", "tool.smith_tools", false));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProficiencyGrant(
                Category.TOOL, "base", "character.race", "class.elf", "creation.tool.fixed",
                GrantKind.FIXED, "base", "tool.smith_tools", false));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProficiencyGrant(
                Category.TOOL, "base\u0000", "character.race", "race.elf", "creation.tool.fixed",
                GrantKind.FIXED, "base", "tool.smith_tools", false));
    }
}
