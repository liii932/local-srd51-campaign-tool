package com.dndtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndtool.persistence.CharacterCardMutationRepository;
import com.dndtool.persistence.CharacterCardRepository;
import com.dndtool.persistence.ModuleCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class CharacterCardServiceTest {
    private static final String CHARACTER_KEY =
            "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";
    private static final String REQUEST_ID =
            "123e4567-e89b-42d3-a456-426614174000";
    private static final String SHA = "a".repeat(64);

    @Test
    void loadBuildsVerifiedCardAndRuntimeDerivedValues() throws Exception {
        CharacterCardService service = service(snapshot(), command -> null);

        CharacterCardService.LoadResult result = service.load(CHARACTER_KEY);

        assertEquals(CharacterCardService.LoadStatus.READY, result.status());
        assertEquals(7, result.card().rowVersion());
        assertEquals(5, result.card().totalLevel());
        assertEquals(3, result.card().proficiencyBonus());
        assertEquals(1, result.card().skills().size());
        assertEquals(3, result.card().skills().getFirst().bonus());
        assertEquals(4, result.card().saves().getFirst().bonus());
        assertEquals("1", result.card().items().getFirst().itemToken());
    }

    @Test
    void loadRejectsFrozenBindingDriftAndIncompleteAuthorityRows() throws Exception {
        CharacterCardRepository.Snapshot valid = snapshot();
        CharacterCardRepository.Binding badBinding = new CharacterCardRepository.Binding(
                valid.binding().savedModuleKey(), valid.binding().savedReleaseVersion(),
                "0".repeat(64), valid.binding().frozenModuleKey(),
                valid.binding().frozenReleaseVersion(), valid.binding().frozenContentSha256(),
                valid.binding().releaseModuleKey(), valid.binding().releaseVersion(),
                valid.binding().releaseContentSha256(), valid.binding().releaseStatus());
        CharacterCardRepository.Snapshot drifted = copy(valid, badBinding, valid.fields());
        CharacterCardRepository.Snapshot incomplete = copy(
                valid, valid.binding(), valid.fields().subList(0, valid.fields().size() - 1));

        assertEquals(CharacterCardService.LoadStatus.MODULE_HASH_MISMATCH,
                service(drifted, command -> null).load(CHARACTER_KEY).status());
        assertEquals(CharacterCardService.LoadStatus.INVALID_STATE,
                service(incomplete, command -> null).load(CHARACTER_KEY).status());
    }

    @Test
    void validFieldMutationPassesOnlyNormalizedAuthoritativeInput() throws Exception {
        AtomicReference<CharacterCardMutationRepository.Command> captured =
                new AtomicReference<>();
        CharacterCardService service = service(snapshot(), command -> {
            captured.set(command);
            return new CharacterCardMutationRepository.Result(
                    CharacterCardMutationRepository.Status.UPDATED, 8L);
        });
        String digest = CharacterCardRequestDigest.sha256(
                CHARACTER_KEY, 7, "SET_FIELD", "ability.strength", "15", "", "");

        CharacterCardService.MutationResult result = service.mutate(
                CHARACTER_KEY, "7", "SET_FIELD", "ability.strength", "15", "", "",
                REQUEST_ID, digest);

        assertEquals(CharacterCardService.MutationStatus.UPDATED, result.status());
        assertEquals(8, result.rowVersion());
        assertEquals(15, captured.get().integerValue());
        assertNull(captured.get().textValue());
        assertEquals(10, captured.get().integerFieldRules().size());
    }

    @Test
    void invalidRangeOrDigestNeverCallsMutationRepository() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        CharacterCardService service = service(snapshot(), command -> {
            called.set(true);
            return null;
        });
        String outOfRangeDigest = CharacterCardRequestDigest.sha256(
                CHARACTER_KEY, 7, "SET_FIELD", "ability.strength", "31", "", "");

        assertEquals(CharacterCardService.MutationStatus.INVALID_REQUEST,
                service.mutate(
                        CHARACTER_KEY, "7", "SET_FIELD", "ability.strength", "31", "", "",
                        REQUEST_ID, outOfRangeDigest).status());
        assertEquals(CharacterCardService.MutationStatus.INVALID_REQUEST,
                service.mutate(
                        CHARACTER_KEY, "7", "SET_FIELD", "ability.strength", "15", "", "",
                        REQUEST_ID, "0".repeat(64)).status());
        assertFalse(called.get());
    }

    @Test
    void moduleAndTemporaryItemsUseReviewedSnapshotsAndNormalizedText() throws Exception {
        List<CharacterCardMutationRepository.Command> captured = new ArrayList<>();
        CharacterCardService service = service(snapshot(), command -> {
            captured.add(command);
            return new CharacterCardMutationRepository.Result(
                    CharacterCardMutationRepository.Status.UPDATED, 8L);
        });
        String moduleDigest = CharacterCardRequestDigest.sha256(
                CHARACTER_KEY, 7, "ADD_MODULE_ITEM", "item.torch", "", "", "2");
        String normalizedName = "Café";
        String temporaryDigest = CharacterCardRequestDigest.sha256(
                CHARACTER_KEY, 7, "ADD_TEMPORARY_ITEM", "",
                normalizedName, "普通物品", "3");

        assertEquals(CharacterCardService.MutationStatus.UPDATED,
                service.mutate(
                        CHARACTER_KEY, "7", "ADD_MODULE_ITEM", "item.torch", "", "", "2",
                        REQUEST_ID, moduleDigest).status());
        assertEquals(CharacterCardService.MutationStatus.UPDATED,
                service.mutate(
                        CHARACTER_KEY, "7", "ADD_TEMPORARY_ITEM", "",
                        "  Cafe\u0301  ", "普通物品", "3",
                        REQUEST_ID, temporaryDigest).status());

        assertEquals("火把", captured.get(0).textValue());
        assertEquals("普通照明用火把", captured.get(0).description());
        assertEquals(2, captured.get(0).integerValue());
        assertEquals(normalizedName, captured.get(1).textValue());
        assertEquals("普通物品", captured.get(1).description());
        assertEquals(3, captured.get(1).integerValue());
    }

    private static CharacterCardService service(
            CharacterCardRepository.Snapshot snapshot,
            CharacterCardMutationRepository mutationRepository) {
        ModuleCatalog catalog = catalog();
        return new CharacterCardService(
                (key, version) -> Optional.of(catalog),
                key -> Optional.of(snapshot),
                mutationRepository,
                ignored -> SHA,
                ignored -> SHA);
    }

    private static CharacterCardRepository.Snapshot snapshot() {
        List<CharacterCardRepository.FieldValue> fields = new ArrayList<>();
        for (ModuleCatalog.FieldDefinition definition : catalog().fieldDefinitions()) {
            long value = switch (definition.fieldKey()) {
                case "ability.strength" -> 8;
                case "ability.dexterity" -> 10;
                case "ability.constitution" -> 12;
                case "ability.intelligence" -> 14;
                case "ability.wisdom" -> 16;
                case "ability.charisma" -> 18;
                default -> ((ModuleCatalog.IntegerValue) definition.defaultValue()).value();
            };
            fields.add(new CharacterCardRepository.FieldValue(
                    definition.fieldKey(), "INTEGER", new ModuleCatalog.IntegerValue(value)));
        }
        CharacterCardRepository.Binding binding = new CharacterCardRepository.Binding(
                "dnd5e2014_srd51_se_v1", "1", SHA,
                "dnd5e2014_srd51_se_v1", "1", SHA,
                "dnd5e2014_srd51_se_v1", "1", SHA, "RELEASED");
        return new CharacterCardRepository.Snapshot(
                CHARACTER_KEY, "PC", "Aria", "ACTIVE", 7, binding, fields,
                List.of(new CharacterCardRepository.ClassLevel("class.fighter", 5)),
                List.of(new CharacterCardRepository.Proficiency(
                        "skill.acrobatics", "proficiency.full")),
                List.of(new CharacterCardRepository.Proficiency(
                        "save.constitution", "proficiency.full")),
                List.of(new CharacterCardRepository.Item(
                        1, "MODULE", "item.torch", "火把", "普通照明用火把", 2, "ACTIVE")));
    }

    private static CharacterCardRepository.Snapshot copy(
            CharacterCardRepository.Snapshot source,
            CharacterCardRepository.Binding binding,
            List<CharacterCardRepository.FieldValue> fields) {
        return new CharacterCardRepository.Snapshot(
                source.characterKey(), source.characterType(), source.characterName(),
                source.characterStatus(), source.rowVersion(), binding, fields,
                source.classLevels(), source.skillProficiencies(), source.saveProficiencies(),
                source.items());
    }

    private static ModuleCatalog catalog() {
        List<ModuleCatalog.FieldDefinition> fields = List.of(
                field("ability.strength", "力量", 10, 1, 30, null, null),
                field("ability.dexterity", "敏捷", 10, 1, 30, null, null),
                field("ability.constitution", "体质", 10, 1, 30, null, null),
                field("ability.intelligence", "智力", 10, 1, 30, null, null),
                field("ability.wisdom", "感知", 10, 1, 30, null, null),
                field("ability.charisma", "魅力", 10, 1, 30, null, null),
                field("hp.maximum", "最大 HP", 10, 1, 999, null, null),
                field("hp.current", "当前 HP", 10, 0, 999, "hp.maximum", null),
                field("armor_class", "AC", 10, 0, 99, null, null),
                field("speed.ground", "速度", 30, 0, 999, null, "英尺"));
        return new ModuleCatalog(
                new ModuleCatalog.Release(
                        "dnd5e2014_srd51_se_v1", "1", 1, "SHA-256", SHA, "RELEASED"),
                List.of(), fields,
                List.of(
                        new ModuleCatalog.ClassDefinition("class.fighter", "战士"),
                        new ModuleCatalog.ClassDefinition("class.wizard", "法师")),
                List.of(
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.none", "NONE", 0, 1, "EXACT"),
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.half", "HALF", 1, 2, "FLOOR"),
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.full", "FULL", 1, 1, "EXACT"),
                        new ModuleCatalog.ProficiencyTier(
                                "proficiency.expertise", "EXPERTISE", 2, 1, "EXACT")),
                List.of(
                        new ModuleCatalog.ProficiencyBonusBand(0, 4, 2),
                        new ModuleCatalog.ProficiencyBonusBand(5, 8, 3),
                        new ModuleCatalog.ProficiencyBonusBand(9, 12, 4),
                        new ModuleCatalog.ProficiencyBonusBand(13, 16, 5),
                        new ModuleCatalog.ProficiencyBonusBand(17, 20, 6)),
                List.of(new ModuleCatalog.SkillDefinition(
                        "skill.acrobatics", "体操", "ability.dexterity")),
                List.of(new ModuleCatalog.SaveDefinition(
                        "save.constitution", "ability.constitution")),
                List.of(new ModuleCatalog.ItemTemplate(
                        "item.torch", "火把", "普通照明用火把")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static ModuleCatalog.FieldDefinition field(
            String key,
            String name,
            long defaultValue,
            long minimum,
            long maximum,
            String dependentMaximum,
            String unit) {
        return new ModuleCatalog.FieldDefinition(
                key, name, "INTEGER", new ModuleCatalog.IntegerValue(defaultValue),
                new ModuleCatalog.IntegerValue(minimum),
                new ModuleCatalog.IntegerValue(maximum), dependentMaximum, unit, name);
    }
}
