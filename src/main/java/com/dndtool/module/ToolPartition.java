package com.dndtool.module;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Complete tool partition only; never a complete or executable rule release. */
public record ToolPartition(List<Tool> tools) {
    public static final String KEY = "character.tool";
    private static final Map<String, Category> CATEGORIES = Map.ofEntries(
            Map.entry("tool.alchemist_supplies", Category.ARTISAN),
            Map.entry("tool.bagpipes", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.brewer_supplies", Category.ARTISAN),
            Map.entry("tool.calligrapher_supplies", Category.ARTISAN),
            Map.entry("tool.carpenter_tools", Category.ARTISAN),
            Map.entry("tool.cartographer_tools", Category.ARTISAN),
            Map.entry("tool.cobbler_tools", Category.ARTISAN),
            Map.entry("tool.cook_utensils", Category.ARTISAN),
            Map.entry("tool.dice_set", Category.GAMING_SET),
            Map.entry("tool.disguise_kit", Category.KIT),
            Map.entry("tool.drum", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.dulcimer", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.flute", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.forgery_kit", Category.KIT),
            Map.entry("tool.glassblower_tools", Category.ARTISAN),
            Map.entry("tool.herbalism_kit", Category.KIT),
            Map.entry("tool.horn", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.jeweler_tools", Category.ARTISAN),
            Map.entry("tool.leatherworker_tools", Category.ARTISAN),
            Map.entry("tool.lute", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.lyre", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.mason_tools", Category.ARTISAN),
            Map.entry("tool.navigator_tools", Category.NAVIGATION),
            Map.entry("tool.painter_supplies", Category.ARTISAN),
            Map.entry("tool.pan_flute", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.playing_card_set", Category.GAMING_SET),
            Map.entry("tool.poisoner_kit", Category.KIT),
            Map.entry("tool.potter_tools", Category.ARTISAN),
            Map.entry("tool.shawm", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.smith_tools", Category.ARTISAN),
            Map.entry("tool.thieves_tools", Category.KIT),
            Map.entry("tool.tinker_tools", Category.ARTISAN),
            Map.entry("tool.vehicles_land", Category.VEHICLE),
            Map.entry("tool.vehicles_water", Category.VEHICLE),
            Map.entry("tool.viol", Category.MUSICAL_INSTRUMENT),
            Map.entry("tool.weaver_tools", Category.ARTISAN),
            Map.entry("tool.woodcarver_tools", Category.ARTISAN));

    public ToolPartition {
        if (tools == null || tools.size() != 37) throw invalid();
        // Snapshot the caller's collection before validation and projection.
        tools = new java.util.ArrayList<>(tools);
        if (tools.size() != 37) throw invalid();
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (Tool row : tools) {
            if (row == null || !keys.add(row.toolKey()) || !orders.add(row.sortOrder())) {
                throw invalid();
            }
        }
        if (!keys.equals(CATEGORIES.keySet())) throw invalid();
        // All keys are ASCII. Natural order is exactly unsigned UTF-8 order here.
        tools = tools.stream().sorted(Comparator.comparing(Tool::toolKey)).toList();
    }

    public enum Category { ARTISAN, KIT, GAMING_SET, MUSICAL_INSTRUMENT, NAVIGATION, VEHICLE }

    /** Already-normalized domain value. Storage readers must reject, not repair, non-NFC text. */
    public record Tool(String toolKey, String displayName, String description,
                           Category category, int sourcePage, int sortOrder) {
        public Tool {
            if (toolKey == null || category == null || CATEGORIES.get(toolKey) != category
                    || sourcePage < 3 || sourcePage > 74 || sortOrder < 1 || sortOrder > 37) {
                throw invalid();
            }
            requireText(displayName, 120);
            requireText(description, 1000);
        }
    }

    /** Narrow typed projection; no generic attribute bag or copy of the whole ModuleCatalog. */
    public List<Definition> definitions() {
        return tools.stream().map(row -> new Definition(row.toolKey(), row.displayName(),
                row.description(), row.sortOrder())).toList();
    }

    public List<Attribute> attributes() {
        return tools.stream().flatMap(row -> java.util.stream.Stream.<Attribute>of(
                new CategoryAttribute(row.toolKey(), row.category()),
                new SourcePageAttribute(row.toolKey(), row.sourcePage()))).toList();
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
        return new IllegalArgumentException("Invalid tool author package");
    }
}
