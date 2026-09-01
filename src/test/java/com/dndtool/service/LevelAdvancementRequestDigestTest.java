package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LevelAdvancementRequestDigestTest {
    @Test
    void matchesFrozenConfirmationVectorAndCommitsToEveryBoundedChoice() {
        String character = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
        String preview = "c".repeat(64);
        String digest = LevelAdvancementRequestDigest.sha256(
                character, 2, "SERVER_ROLL", preview);

        assertEquals("1cecd81eb4bc831a711964597ef3c4e9d247bcce2246fb8a32c8e99dafd83eda",
                digest);
        assertNotEquals(digest, LevelAdvancementRequestDigest.sha256(
                character, 3, "SERVER_ROLL", preview));
        assertNotEquals(digest, LevelAdvancementRequestDigest.sha256(
                character, 2, "FIXED_AVERAGE", preview));
        assertNotEquals(digest, LevelAdvancementRequestDigest.sha256(
                character, 2, "SERVER_ROLL", "d".repeat(64)));
    }

    @Test
    void v2DigestIsIndependentOfAsiAndProficiencyInputOrder() {
        String character = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
        String preview = "c".repeat(64);
        Map<String, Integer> first = new LinkedHashMap<>();
        first.put("ability.wisdom", 1);
        first.put("ability.strength", 1);
        Map<String, Integer> second = new LinkedHashMap<>();
        second.put("ability.strength", 1);
        second.put("ability.wisdom", 1);
        LevelAdvancementRules.Request left = new LevelAdvancementRules.Request(
                character, 4, "FIXED_AVERAGE", "class.fighter", null, first, null,
                List.of("skill.perception", "tool.lute"));
        LevelAdvancementRules.Request right = new LevelAdvancementRules.Request(
                character, 4, "FIXED_AVERAGE", "class.fighter", null, second, null,
                List.of("tool.lute", "skill.perception"));

        assertEquals(LevelAdvancementRequestDigest.sha256(left, preview),
                LevelAdvancementRequestDigest.sha256(right, preview));
        LevelAdvancementRules.Request subclass = new LevelAdvancementRules.Request(
                character, 4, "FIXED_AVERAGE", "class.fighter", "subclass.champion",
                second, null, List.of("tool.lute", "skill.perception"));
        assertNotEquals(LevelAdvancementRequestDigest.sha256(left, preview),
                LevelAdvancementRequestDigest.sha256(subclass, preview));
    }
}
