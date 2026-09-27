package com.dndtool.module;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Complete language partition only; never a complete or executable rule release. */
public record LanguagePartition(List<Language> languages) {
    public static final String KEY = "character.language";
    private static final Map<String, Category> CATEGORIES = Map.ofEntries(
            Map.entry("language.abyssal", Category.EXOTIC),
            Map.entry("language.celestial", Category.EXOTIC),
            Map.entry("language.common", Category.STANDARD),
            Map.entry("language.deep_speech", Category.EXOTIC),
            Map.entry("language.draconic", Category.EXOTIC),
            Map.entry("language.druidic", Category.SECRET),
            Map.entry("language.dwarvish", Category.STANDARD),
            Map.entry("language.elvish", Category.STANDARD),
            Map.entry("language.giant", Category.STANDARD),
            Map.entry("language.gnomish", Category.STANDARD),
            Map.entry("language.goblin", Category.STANDARD),
            Map.entry("language.halfling", Category.STANDARD),
            Map.entry("language.infernal", Category.EXOTIC),
            Map.entry("language.orc", Category.STANDARD),
            Map.entry("language.primordial", Category.EXOTIC),
            Map.entry("language.sylvan", Category.EXOTIC),
            Map.entry("language.thieves_cant", Category.SECRET),
            Map.entry("language.undercommon", Category.EXOTIC));

    public LanguagePartition {
        if (languages == null || languages.size() != 18) throw invalid();
        // Snapshot the caller's collection before validation and projection.
        languages = new java.util.ArrayList<>(languages);
        if (languages.size() != 18) throw invalid();
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (Language row : languages) {
            if (row == null || !keys.add(row.languageKey()) || !orders.add(row.sortOrder())) {
                throw invalid();
            }
        }
        if (!keys.equals(CATEGORIES.keySet())) throw invalid();
        // All keys are ASCII. Natural order is exactly unsigned UTF-8 order here.
        languages = languages.stream().sorted(Comparator.comparing(Language::languageKey)).toList();
    }

    public enum Category { STANDARD, EXOTIC, SECRET }

    /** Already-normalized domain value. Storage readers must reject, not repair, non-NFC text. */
    public record Language(String languageKey, String displayName, String description,
                           Category category, int sourcePage, int sortOrder) {
        public Language {
            if (languageKey == null || category == null || CATEGORIES.get(languageKey) != category
                    || sourcePage < 3 || sourcePage > 74 || sortOrder < 1 || sortOrder > 18) {
                throw invalid();
            }
            requireText(displayName, 120);
            requireText(description, 1000);
        }
    }

    /** Narrow typed projection; no generic attribute bag or copy of the whole ModuleCatalog. */
    public List<Definition> definitions() {
        return languages.stream().map(row -> new Definition(row.languageKey(), row.displayName(),
                row.description(), row.sortOrder())).toList();
    }

    public List<Attribute> attributes() {
        return languages.stream().flatMap(row -> java.util.stream.Stream.<Attribute>of(
                new CategoryAttribute(row.languageKey(), row.category()),
                new SourcePageAttribute(row.languageKey(), row.sourcePage()))).toList();
    }

    public record Definition(String definitionKey, String displayName, String description, int sortOrder) {
        public String definitionType() { return KEY; }
    }

    public sealed interface Attribute permits CategoryAttribute, SourcePageAttribute {
        String definitionKey();
        String attributeKey();
        String valueType();
        default String definitionType() { return KEY; }
        default int attributeOrder() { return 1; }
    }

    public record CategoryAttribute(String definitionKey, Category value) implements Attribute {
        public String attributeKey() { return "catalog.category"; }
        public String valueType() { return "IDENTIFIER"; }
    }

    public record SourcePageAttribute(String definitionKey, int value) implements Attribute {
        public String attributeKey() { return "source.page"; }
        public String valueType() { return "INTEGER"; }
    }

    static String authorText(String value, int limit) {
        requireScalars(value);
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        requireText(normalized, limit);
        return normalized;
    }

    private static void requireText(String value, int limit) {
        requireScalars(value);
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)
                || value.codePointCount(0, value.length()) > limit || value.isEmpty()
                || value.codePoints().anyMatch(cp -> cp <= 0x1f || cp >= 0x7f && cp <= 0x9f)
                || value.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))) {
            throw invalid();
        }
    }

    static void requireScalars(String value) {
        if (value == null) throw invalid();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) throw invalid();
            } else if (Character.isLowSurrogate(current)) throw invalid();
        }
    }

    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid language author package");
    }
}
