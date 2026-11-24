package com.dndtool.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LevelOneRuleProfileTest {
    @Test
    void parsesCanonicalFixedAndChoiceRules() {
        LevelOneRuleProfile profile = LevelOneRuleProfile.parse(
                "bonus=constitution+2|language=common,dwarvish|subrace=required|tool=1:brewer_supplies,mason_tools,smith_tools");

        assertEquals(2, profile.abilityBonuses().get("constitution"));
        assertEquals(2, profile.languages().size());
        assertTrue(profile.subraceRequired());
        assertEquals(1, profile.toolChoice().count());
        assertEquals(3, profile.toolChoice().candidates().size());
    }

    @Test
    void rejectsDuplicateUnorderedUnknownAndControlledProfiles() {
        assertThrows(IllegalArgumentException.class,
                () -> LevelOneRuleProfile.parse("language=dwarvish,common"));
        assertThrows(IllegalArgumentException.class,
                () -> LevelOneRuleProfile.parse("skill=arcana|skill=history"));
        assertThrows(IllegalArgumentException.class,
                () -> LevelOneRuleProfile.parse("skill=1:arcana,history|skill=1:arcana,history"));
        assertThrows(IllegalArgumentException.class,
                () -> LevelOneRuleProfile.parse("skill=arcana|hp=8"));
        assertThrows(IllegalArgumentException.class,
                () -> LevelOneRuleProfile.parse("unknown=value"));
        assertThrows(IllegalArgumentException.class,
                () -> LevelOneRuleProfile.parse("language=common\u0000"));
    }
}
