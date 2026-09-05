package com.dndtool.service;

import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-owned SRD 5.1 shared spell-slot aggregation for multiclass characters. */
public final class MulticlassSpellSlotRules {
    public static final String PROGRESSION_ATTRIBUTE =
            "class.multiclass_spellcasting_progression";
    private static final Set<String> PROGRESSIONS = Set.of(
            "NONE", "FULL", "HALF_DOWN", "PACT_MAGIC");
    private static final int[][] SHARED_SLOT_TABLE = {
        {},
        {2},
        {3},
        {4, 2},
        {4, 3},
        {4, 3, 2},
        {4, 3, 3},
        {4, 3, 3, 1},
        {4, 3, 3, 2},
        {4, 3, 3, 3, 1},
        {4, 3, 3, 3, 2},
        {4, 3, 3, 3, 2, 1},
        {4, 3, 3, 3, 2, 1},
        {4, 3, 3, 3, 2, 1, 1},
        {4, 3, 3, 3, 2, 1, 1},
        {4, 3, 3, 3, 2, 1, 1, 1},
        {4, 3, 3, 3, 2, 1, 1, 1},
        {4, 3, 3, 3, 2, 1, 1, 1, 1},
        {4, 3, 3, 3, 3, 1, 1, 1, 1},
        {4, 3, 3, 3, 3, 2, 1, 1, 1},
        {4, 3, 3, 3, 3, 2, 2, 1, 1}
    };

    public Prepared prepare(ModuleCatalog catalog, List<ClassLevel> classLevels) {
        if (catalog == null || classLevels == null || classLevels.size() < 2) {
            throw invalid("INVALID_REQUEST");
        }
        Set<String> uniqueClasses = new HashSet<>();
        int totalLevel = 0;
        int sharedSpellcastingClassCount = 0;
        int fullCasterLevels = 0;
        int halfCasterLevels = 0;
        boolean hasPactMagic = false;
        for (ClassLevel classLevel : classLevels) {
            if (classLevel == null || !stableClassKey(classLevel.classKey())
                    || classLevel.level() < 1 || classLevel.level() > 20
                    || !uniqueClasses.add(classLevel.classKey())) {
                throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            }
            totalLevel += classLevel.level();
            if (totalLevel > 20) {
                throw invalid("AUTHORITATIVE_STATE_MISMATCH");
            }
            String progression = progression(catalog, classLevel.classKey());
            switch (progression) {
                case "FULL" -> {
                    sharedSpellcastingClassCount++;
                    fullCasterLevels += classLevel.level();
                }
                case "HALF_DOWN" -> {
                    if (classLevel.level() >= 2) {
                        sharedSpellcastingClassCount++;
                        halfCasterLevels += classLevel.level();
                    }
                }
                case "PACT_MAGIC" -> hasPactMagic = true;
                case "NONE" -> {
                    // This class contributes no shared spellcaster levels.
                }
                default -> throw new AssertionError("validated progression");
            }
        }

        boolean aggregationApplicable = sharedSpellcastingClassCount >= 2;
        int effectiveLevel = aggregationApplicable
                ? fullCasterLevels + halfCasterLevels / 2 : 0;
        List<SpellSlotMaximum> slots = new ArrayList<>();
        if (aggregationApplicable) {
            int[] maximums = SHARED_SLOT_TABLE[effectiveLevel];
            for (int index = 0; index < maximums.length; index++) {
                slots.add(new SpellSlotMaximum(index + 1, maximums[index]));
            }
        }
        return new Prepared(aggregationApplicable, effectiveLevel, slots, hasPactMagic);
    }

    private static String progression(ModuleCatalog catalog, String classKey) {
        if (catalog.catalogDefinitions().stream()
                .filter(row -> "character.class".equals(row.definitionType())
                        && classKey.equals(row.definitionKey()))
                .count() != 1) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        List<ModuleCatalog.CatalogAttribute> rows = catalog.catalogAttributes().stream()
                .filter(row -> "character.class".equals(row.definitionType())
                        && classKey.equals(row.definitionKey())
                        && PROGRESSION_ATTRIBUTE.equals(row.attributeKey()))
                .toList();
        if (rows.size() != 1 || rows.getFirst().attributeOrder() != 1
                || !(rows.getFirst().value()
                        instanceof ModuleCatalog.IdentifierValue identifier)
                || !PROGRESSIONS.contains(identifier.value())) {
            throw invalid("MALFORMED_FROZEN_CATALOG");
        }
        return ((ModuleCatalog.IdentifierValue) rows.getFirst().value()).value();
    }

    private static boolean stableClassKey(String value) {
        return value != null && value.length() <= 128
                && value.matches("class[.][a-z][a-z0-9_]*");
    }

    private static RuleException invalid(String code) {
        return new RuleException(code);
    }

    public record ClassLevel(String classKey, int level) {
    }

    public record SpellSlotMaximum(int spellLevel, int maximum) {
        public SpellSlotMaximum {
            if (spellLevel < 1 || spellLevel > 9 || maximum < 1 || maximum > 4) {
                throw new IllegalArgumentException("Invalid shared spell-slot maximum");
            }
        }
    }

    public record Prepared(boolean sharedAggregationApplicable, int effectiveSpellcasterLevel,
            List<SpellSlotMaximum> sharedSpellSlots, boolean hasPactMagic) {
        public Prepared {
            sharedSpellSlots = List.copyOf(sharedSpellSlots);
        }
    }

    public static final class RuleException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String code;

        RuleException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
