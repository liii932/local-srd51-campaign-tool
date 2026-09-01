package com.dndtool.service;

import com.dndtool.persistence.ModuleCatalog;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Calculates character values from authoritative runtime inputs and a frozen module catalog.
 *
 * <p>The calculator deliberately has no persistence dependency. Ability modifiers, total level,
 * proficiency bonus, skill bonuses and saving-throw bonuses are derived when requested and are
 * never accepted as caller-supplied final values.
 */
public final class CharacterDerivedValueCalculator {
    private static final int MINIMUM_ABILITY_SCORE = 1;
    private static final int MAXIMUM_ABILITY_SCORE = 30;
    private static final int MAXIMUM_TOTAL_LEVEL = 20;

    private final Map<String, ModuleCatalog.ClassDefinition> classes;
    private final Map<String, ModuleCatalog.ProficiencyTier> proficiencyTiers;
    private final List<ModuleCatalog.ProficiencyBonusBand> proficiencyBonusBands;
    private final Map<String, ModuleCatalog.SkillDefinition> skills;
    private final Map<String, ModuleCatalog.SaveDefinition> saves;

    /** Builds indexes from a catalog which has already passed release-integrity verification. */
    public CharacterDerivedValueCalculator(ModuleCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        classes = uniqueIndex(catalog.classDefinitions(), ModuleCatalog.ClassDefinition::classKey);
        proficiencyTiers = uniqueIndex(
                catalog.proficiencyTiers(), ModuleCatalog.ProficiencyTier::proficiencyKey);
        skills = uniqueIndex(catalog.skillDefinitions(), ModuleCatalog.SkillDefinition::skillKey);
        saves = uniqueIndex(catalog.saveDefinitions(), ModuleCatalog.SaveDefinition::saveKey);
        proficiencyBonusBands = List.copyOf(catalog.proficiencyBonusBands());
        if (proficiencyBonusBands.stream().anyMatch(Objects::isNull)) {
            throw invalidCatalog();
        }
        validateProficiencyBonusBands();
    }

    /** Uses integer floor division so odd scores below 10 retain the 2014 rules result. */
    public static int abilityModifier(int abilityScore) {
        if (abilityScore < MINIMUM_ABILITY_SCORE || abilityScore > MAXIMUM_ABILITY_SCORE) {
            throw new IllegalArgumentException("Ability score must be between 1 and 30");
        }
        return Math.floorDiv(abilityScore - 10, 2);
    }

    /** Adds distinct, known class levels; an empty collection represents total level zero. */
    public int totalLevel(Collection<ClassLevel> classLevels) {
        if (classLevels == null) {
            throw new IllegalArgumentException("Class levels are required");
        }
        int total = 0;
        Set<String> seenClasses = new HashSet<>();
        for (ClassLevel classLevel : classLevels) {
            if (classLevel == null
                    || classLevel.classKey() == null
                    || !classes.containsKey(classLevel.classKey())
                    || !seenClasses.add(classLevel.classKey())
                    || classLevel.level() < 1
                    || classLevel.level() > MAXIMUM_TOTAL_LEVEL
                    || classLevel.level() > MAXIMUM_TOTAL_LEVEL - total) {
                throw new IllegalArgumentException("Invalid class-level input");
            }
            total += classLevel.level();
        }
        return total;
    }

    /** Resolves the one frozen 2014 proficiency band that contains the supplied total level. */
    public int proficiencyBonus(int totalLevel) {
        requireTotalLevel(totalLevel);
        Integer matchedBonus = null;
        for (ModuleCatalog.ProficiencyBonusBand band : proficiencyBonusBands) {
            if (band.minimumTotalLevel() <= totalLevel
                    && totalLevel <= band.maximumTotalLevel()) {
                if (matchedBonus != null || band.bonus() <= 0) {
                    throw invalidCatalog();
                }
                matchedBonus = band.bonus();
            }
        }
        if (matchedBonus == null) {
            throw invalidCatalog();
        }
        return matchedBonus;
    }

    /** Derives the total level first, rather than accepting a client-provided duplicate value. */
    public int proficiencyBonus(Collection<ClassLevel> classLevels) {
        return proficiencyBonus(totalLevel(classLevels));
    }

    /** Applies the catalog's exact or floor integer multiplier to the level-based bonus. */
    public int proficiencyContribution(int totalLevel, String proficiencyKey) {
        requireTotalLevel(totalLevel);
        if (proficiencyKey == null) {
            throw new IllegalArgumentException("Proficiency tier is required");
        }
        ModuleCatalog.ProficiencyTier tier = proficiencyTiers.get(proficiencyKey);
        if (tier == null) {
            throw new IllegalArgumentException("Unknown proficiency tier");
        }
        if (tier.numerator() < 0 || tier.denominator() <= 0) {
            throw invalidCatalog();
        }
        long numerator = (long) proficiencyBonus(totalLevel) * tier.numerator();
        long contribution = switch (tier.roundingAlgorithm()) {
            case "EXACT" -> {
                if (numerator % tier.denominator() != 0) {
                    throw invalidCatalog();
                }
                yield numerator / tier.denominator();
            }
            case "FLOOR" -> Math.floorDiv(numerator, tier.denominator());
            default -> throw invalidCatalog();
        };
        try {
            return Math.toIntExact(contribution);
        } catch (ArithmeticException exception) {
            throw invalidCatalog();
        }
    }

    /** Looks up the skill's frozen governing ability; callers cannot substitute another one. */
    public int skillBonus(
            String skillKey,
            Map<String, Integer> abilityScores,
            Collection<ClassLevel> classLevels,
            String proficiencyKey) {
        if (skillKey == null) {
            throw new IllegalArgumentException("Skill is required");
        }
        ModuleCatalog.SkillDefinition skill = skills.get(skillKey);
        if (skill == null) {
            throw new IllegalArgumentException("Unknown skill");
        }
        return checkBonus(
                skill.abilityFieldKey(), abilityScores, classLevels, proficiencyKey);
    }

    /** Looks up the save's frozen governing ability; callers cannot submit a final bonus. */
    public int savingThrowBonus(
            String saveKey,
            Map<String, Integer> abilityScores,
            Collection<ClassLevel> classLevels,
            String proficiencyKey) {
        if (saveKey == null) {
            throw new IllegalArgumentException("Saving throw is required");
        }
        ModuleCatalog.SaveDefinition save = saves.get(saveKey);
        if (save == null) {
            throw new IllegalArgumentException("Unknown saving throw");
        }
        return checkBonus(save.abilityFieldKey(), abilityScores, classLevels, proficiencyKey);
    }

    private int checkBonus(
            String abilityFieldKey,
            Map<String, Integer> abilityScores,
            Collection<ClassLevel> classLevels,
            String proficiencyKey) {
        if (abilityScores == null || !abilityScores.containsKey(abilityFieldKey)) {
            throw new IllegalArgumentException("Governing ability score is missing");
        }
        Integer score = abilityScores.get(abilityFieldKey);
        if (score == null) {
            throw new IllegalArgumentException("Governing ability score is missing");
        }
        int totalLevel = totalLevel(classLevels);
        return Math.addExact(
                abilityModifier(score), proficiencyContribution(totalLevel, proficiencyKey));
    }

    private static void requireTotalLevel(int totalLevel) {
        if (totalLevel < 0 || totalLevel > MAXIMUM_TOTAL_LEVEL) {
            throw new IllegalArgumentException("Total level must be between 0 and 20");
        }
    }

    private void validateProficiencyBonusBands() {
        for (int level = 0; level <= MAXIMUM_TOTAL_LEVEL; level++) {
            int matches = 0;
            for (ModuleCatalog.ProficiencyBonusBand band : proficiencyBonusBands) {
                if (band.minimumTotalLevel() < 0
                        || band.minimumTotalLevel() > band.maximumTotalLevel()
                        || band.maximumTotalLevel() > MAXIMUM_TOTAL_LEVEL
                        || band.bonus() <= 0) {
                    throw invalidCatalog();
                }
                if (band.minimumTotalLevel() <= level && level <= band.maximumTotalLevel()) {
                    matches++;
                }
            }
            if (matches != 1) {
                throw invalidCatalog();
            }
        }
    }

    private static <T> Map<String, T> uniqueIndex(
            List<T> rows, Function<T, String> keyFunction) {
        Map<String, T> indexed = new HashMap<>();
        for (T row : rows) {
            if (row == null) {
                throw invalidCatalog();
            }
            String key = keyFunction.apply(row);
            if (key == null || key.isBlank() || indexed.putIfAbsent(key, row) != null) {
                throw invalidCatalog();
            }
        }
        return Map.copyOf(indexed);
    }

    private static IllegalStateException invalidCatalog() {
        return new IllegalStateException("Invalid derived-value rules in module catalog");
    }

    /** One authoritative class-level row supplied by the character aggregate. */
    public record ClassLevel(String classKey, int level) {
    }
}
