package com.dndtool.service;

/** The bounded healing owner used by the internal rest consumer; it does not resolve damage. */
public final class Schema2CharacterHealthRules {
    private Schema2CharacterHealthRules() { }

    public record Healing(long before, long maximum, long requested, long applied, long after) { }

    public static Healing heal(long current, long maximum, long requested) {
        if (maximum < 1 || maximum > 65535 || current < 0 || current > maximum || requested < 0 || requested > 65535)
            throw new IllegalArgumentException("S2_HEALTH_HEALING");
        long applied = Math.min(requested, maximum - current);
        return new Healing(current, maximum, requested, applied, current + applied);
    }
}
