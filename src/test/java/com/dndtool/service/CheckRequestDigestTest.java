package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.dndtool.persistence.CharacterVersionRepository.VersionExpectation;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CheckRequestDigestTest {
    private static final String EXECUTOR = "11111111-1111-1111-1111-111111111111";
    private static final String TARGET_A = "22222222-2222-2222-2222-222222222222";
    private static final String TARGET_B = "33333333-3333-3333-3333-333333333333";

    @Test
    void bindsExecutorAndEveryPossibleTargetVersionInCanonicalOrder() {
        VersionExpectation executor = new VersionExpectation(EXECUTOR, 3L);
        List<VersionExpectation> targets = List.of(
                new VersionExpectation(TARGET_B, 9L),
                new VersionExpectation(TARGET_A, 7L));

        String digest = CheckRequestDigest.sha256("a".repeat(64), executor, targets);

        assertEquals(digest, CheckRequestDigest.sha256(
                "a".repeat(64), executor, targets.reversed()));
        assertNotEquals(digest, CheckRequestDigest.sha256(
                "a".repeat(64), new VersionExpectation(EXECUTOR, 4L), targets));
        assertNotEquals(digest, CheckRequestDigest.sha256(
                "a".repeat(64), executor, List.of(
                        new VersionExpectation(TARGET_A, 8L),
                        new VersionExpectation(TARGET_B, 9L))));
        assertNotEquals(digest, CheckRequestDigest.sha256(
                "b".repeat(64), executor, targets));
    }
}
