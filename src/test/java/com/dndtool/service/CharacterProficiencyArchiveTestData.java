package com.dndtool.service;

import com.dndtool.module.CharacterProficiencyGrant;
import com.dndtool.module.CharacterProficiencyGrant.Category;
import com.dndtool.module.CharacterProficiencyGrant.GrantKind;
import com.dndtool.module.ModuleCatalog;
import java.util.List;

/** Independently declared facts for a duplicate fixed grant and finite language/tool choices. */
public final class CharacterProficiencyArchiveTestData {
    public static final String MODULE = "dnd5e2014_srd51_se";
    private CharacterProficiencyArchiveTestData() { }

    public static List<CharacterProficiencyGrant> grants() {
        return List.of(
                new CharacterProficiencyGrant(Category.LANGUAGE, MODULE, "character.race",
                        "race.human", "creation.language.fixed", GrantKind.FIXED,
                        MODULE, "language.common", false),
                new CharacterProficiencyGrant(Category.LANGUAGE, MODULE, "character.class",
                        "class.fighter", "creation.language.fixed", GrantKind.FIXED,
                        MODULE, "language.common", false),
                new CharacterProficiencyGrant(Category.LANGUAGE, MODULE, "character.background",
                        "background.acolyte", "creation.language.choice", GrantKind.CHOICE,
                        MODULE, "language.celestial", false),
                new CharacterProficiencyGrant(Category.TOOL, MODULE, "character.background",
                        "background.acolyte", "creation.tool.fixed", GrantKind.FIXED,
                        MODULE, "tool.smith_tools", false));
    }

    public static ModuleCatalog catalog() {
        List<ModuleCatalog.CatalogDefinition> definitions = List.of(
                definition("character.race", "race.human"),
                definition("character.background", "background.acolyte"),
                definition("character.class", "class.fighter"),
                definition("character.language", "language.common"),
                definition("character.language", "language.celestial"),
                definition("character.language", "language.draconic"),
                definition("character.tool", "tool.smith_tools"));
        List<ModuleCatalog.CatalogAttribute> attributes = List.of(
                profile("character.race", "race.human", "language=common"),
                profile("character.background", "background.acolyte",
                        "language=1:celestial,draconic|tool=smith_tools"),
                profile("character.class", "class.fighter", "language=common"));
        return new ModuleCatalog(new ModuleCatalog.Release(MODULE, "1", 2,
                "SHA-256", "a".repeat(64), "DRAFT"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                definitions, attributes, List.of());
    }

    private static ModuleCatalog.CatalogDefinition definition(String type, String key) {
        String name = switch (key) {
            case "race.human" -> "人类";
            case "class.fighter" -> "战士";
            case "background.acolyte" -> "侍僧";
            case "language.common" -> "通用语";
            case "language.celestial" -> "天界语";
            case "language.draconic" -> "龙语";
            case "tool.smith_tools" -> "铁匠工具";
            default -> throw new IllegalArgumentException("Unknown fixture definition");
        };
        return new ModuleCatalog.CatalogDefinition(type, key, name, name, 1);
    }

    private static ModuleCatalog.CatalogAttribute profile(String type, String key, String profile) {
        return new ModuleCatalog.CatalogAttribute(type, key, "creation.level_one_profile", 1,
                "TEXT", new ModuleCatalog.TextValue(profile));
    }
}
