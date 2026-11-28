package com.dndtool.module.composition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2RangedWeaponCompositionTest {
    private static final String CHARACTERS = "srd51_characters";
    private static final String MAGIC = "srd51_magic";
    private static final RuleIdentity BOW = new RuleIdentity(CHARACTERS, "equipment.item", "item.shortbow");
    private static final RuleIdentity ARROW = new RuleIdentity(CHARACTERS, "equipment.item", "item.arrow");
    private static final RuleIdentity ATTACK = new RuleIdentity(CHARACTERS, "equipment.weapon_attack", "weapon_attack.shortbow");
    private static final RuleIdentity PROFICIENCY = new RuleIdentity(CHARACTERS, "character.proficiency", "proficiency.weapon.shortbow");
    private static final RuleIdentity SKELETON = new RuleIdentity(MAGIC, "adventure.monster", "monster.skeleton");

    @Test void freezesRangedAndMeleeProfilesAndExactProficiencyGrants() throws IOException {
        var material = compose(packages());
        var bow = ((FinalItem) rule(material, BOW)).definition();
        assertEquals(EquipmentKind.RANGED_WEAPON, bow.kind());
        assertEquals(2, bow.hands());
        assertEquals(PROFICIENCY, bow.proficiency());
        var arrow = ((FinalItem) rule(material, ARROW)).definition();
        assertEquals(EquipmentKind.AMMUNITION, arrow.kind());
        assertEquals(0, arrow.hands());
        assertNull(arrow.proficiency());
        var attack = ((FinalWeaponAttack) rule(material, ATTACK)).definition();
        assertEquals(new WeaponAttackDefinition(ATTACK, attack.displayName(), attack.description(), 66, BOW,
                WeaponAbilityPolicy.DEXTERITY, 1, 6, DamageType.PIERCING, 0, 80, 320, ARROW,
                RANGED_WEAPON_ATTACK_ALGORITHM, null), attack);
        var grants = material.projection().entries().stream().filter(FinalEntry::enabled).map(FinalEntry::entry)
                .filter(GrowthProficiencyGrantEntry.class::isInstance).map(GrowthProficiencyGrantEntry.class::cast)
                .filter(grant -> new FixedGrowthGrant(PROFICIENCY).equals(grant.grant()))
                .map(Entry::identity).map(id -> id.rule().ruleKey() + "/" + id.collectionKey()).collect(Collectors.toSet());
        assertEquals(Set.of("progression.fighter/starting_proficiencies", "progression.fighter/multiclass_proficiencies",
                "progression.cleric/starting_proficiencies"), grants);

        var monster = ((FinalMonster) rule(material, SKELETON)).definition();
        assertEquals(13, monster.statBlock().hitPoints());
        assertEquals(13, monster.statBlock().armorClass());
        var damage = List.of(new MonsterAttackDamage(DamageType.PIERCING, 1, 6, 2));
        var shortbow = action(material, "shortbow");
        assertEquals(MonsterActionMode.RANGED_ATTACK_V1, shortbow.mode());
        assertEquals(MONSTER_RANGED_ATTACK_MECHANISM, shortbow.mechanism());
        assertEquals(new MonsterAttackProfile(4, 0, 80, 320, damage, null), shortbow.attackProfile());
        var shortsword = action(material, "shortsword");
        assertEquals(MonsterActionMode.MELEE_ATTACK_V1, shortsword.mode());
        assertEquals(new MonsterAttackProfile(4, 5, damage, null), shortsword.attackProfile());

        var output = restore(material);
        assertArrayEquals(material.finalRulesJson(), output.finalRulesJson());
        assertArrayEquals(material.chainsJson(), output.chainsJson());
        assertEquals(attack, ((FinalWeaponAttack) rule(output, ATTACK)).definition());
        assertEquals(shortbow, action(output, "shortbow"));
    }

    @Test void rangeOverrideChangesFrozenRulesAndRetainsTheOriginalDeclaration() throws IOException {
        var files = packages();
        var original = compose(files);
        Schema2GrantFixtures.edit(files, 3, "changes.json", rows -> {
            var change = new JsonObject();
            change.addProperty("op", "set_field");
            change.add("target", Schema2GrantFixtures.rule(CHARACTERS, ATTACK.ruleType(), ATTACK.ruleKey()));
            change.addProperty("field", "normal_range_feet");
            change.addProperty("value", 100);
            rows.getAsJsonArray().add(change);
        });
        var changed = compose(files);
        assertNotEquals(original.finalRulesSha256(), changed.finalRulesSha256());
        var restored = restore(changed);
        assertEquals(100, ((FinalWeaponAttack) rule(restored, ATTACK)).definition().normalRangeFeet());
        assertArrayEquals(changed.chainsJson(), restored.chainsJson());
        var chain = restored.projection().chains().stream().filter(row -> row.target().equals(ATTACK)
                && row.field() == Field.NORMAL_RANGE_FEET).findFirst().orElseThrow();
        assertEquals(List.of(new IntegerValue(80), new IntegerValue(100)),
                chain.steps().stream().map(step -> step.after().value()).toList());
        assertEquals("dm_runtime_house_rules", chain.steps().getLast().provider().moduleKey());
    }

    @Test void laterOverrideCannotRepairAnInvalidOriginalAmmunitionReferenceInFrozenRows() throws IOException {
        var material = compose(packages());
        var projection = material.projection();
        var chains = new ArrayList<>(projection.chains());
        var chain = chains.stream().filter(row -> row.target().equals(ATTACK) && row.field() == Field.AMMUNITION)
                .findFirst().orElseThrow();
        var initial = chain.steps().getFirst();
        var invalid = new FieldState(StateKind.PRESENT, new IdentityValue(BOW));
        var extension = material.members().getLast();
        var location = new Location(extension.moduleKey(), extension.releaseVersion(), extension.rawZipSha256(),
                "changes.json", "/17", null);
        chains.set(chains.indexOf(chain), new FieldChain(ATTACK, Field.AMMUNITION, List.of(
                new ChainStep("initial", initial.before(), invalid, initial.provider(), initial.loadIndex(),
                        initial.authorLocation(), "declared"),
                new ChainStep("set_field", invalid, initial.after(), new Provider(extension.moduleKey(), extension.releaseVersion()),
                        material.members().size() - 1, location, "explicit"))));
        var rejected = assertThrows(Schema2Exception.class, () -> ProductionSchema2Projection.restore(material.members(),
                projection.rules(), projection.entries(), chains));
        assertEquals("S2_STORED_INVALID", rejected.diagnostic().code());
    }

    @Test void rejectsInvalidPlayerGeometryEquipmentAndReferences() throws IOException {
        reject(1, "rules.json", ATTACK.ruleKey(), row -> row.remove("normal_range_feet"));
        reject(1, "rules.json", ATTACK.ruleKey(), row -> row.addProperty("normal_range_feet", 81));
        reject(1, "rules.json", ATTACK.ruleKey(), row -> row.addProperty("long_range_feet", 80));
        reject(1, "rules.json", ATTACK.ruleKey(), row -> row.addProperty("long_range_feet", 605));
        reject(1, "rules.json", ATTACK.ruleKey(), row -> row.addProperty("reach_feet", 5));
        reject(1, "rules.json", ATTACK.ruleKey(), row -> row.addProperty("ability_policy", "FINESSE"));
        reject(1, "rules.json", ATTACK.ruleKey(), row -> row.addProperty("algorithm", "SCRIPT"));
        reject(1, "rules.json", ATTACK.ruleKey(), row -> row.getAsJsonObject("ammunition").addProperty("rule_key", BOW.ruleKey()));
        reject(1, "rules.json", ATTACK.ruleKey(), row -> row.getAsJsonObject("ammunition").addProperty("rule_key", "item.missing"));
        reject(1, "rules.json", BOW.ruleKey(), row -> row.addProperty("hands", 1));
        reject(1, "rules.json", BOW.ruleKey(), row -> row.addProperty("kind", "WEAPON"));
        reject(1, "rules.json", ARROW.ruleKey(), row -> row.addProperty("hands", 1));
        reject(1, "rules.json", ARROW.ruleKey(), row -> row.add("proficiency",
                Schema2GrantFixtures.rule(CHARACTERS, PROFICIENCY.ruleType(), PROFICIENCY.ruleKey())));
    }

    @Test void rejectsMismatchedOrMixedMonsterActionAtoms() throws IOException {
        reject(2, "entries.json", "shortbow", row -> row.getAsJsonObject("action_profile")
                .addProperty("mechanism", MONSTER_MELEE_ATTACK_MECHANISM));
        reject(2, "entries.json", "shortbow", row -> attackProfile(row).remove("long_range_feet"));
        reject(2, "entries.json", "shortbow", row -> attackProfile(row).addProperty("normal_range_feet", 0));
        reject(2, "entries.json", "shortbow", row -> attackProfile(row).addProperty("long_range_feet", 75));
        reject(2, "entries.json", "shortbow", row -> attackProfile(row).addProperty("reach_feet", 5));
        reject(2, "entries.json", "shortbow", row -> attackProfile(row).add("on_hit", new JsonObject()));
        reject(2, "entries.json", "shortsword", row -> attackProfile(row).addProperty("normal_range_feet", 80));
    }

    private static JsonObject attackProfile(JsonObject row) {
        return row.getAsJsonObject("action_profile").getAsJsonObject("attack_profile");
    }

    private static void reject(int index, String file, String key, Consumer<JsonObject> mutation) throws IOException {
        var files = packages();
        String identityKey = file.equals("rules.json") ? "rule_key" : "entry_key";
        Schema2GrantFixtures.edit(files, index, file, root -> mutation.accept(root.getAsJsonArray().asList().stream()
                .map(JsonElement::getAsJsonObject).filter(row -> key.equals(row.getAsJsonObject("identity").get(identityKey).getAsString()))
                .findFirst().orElseThrow()));
        assertThrows(Schema2Exception.class, () -> compose(files), key);
    }

    private static FinalRule rule(ProductionSchema2Projection material, RuleIdentity id) {
        return material.projection().rules().stream().filter(rule -> rule.identity().equals(id)).findFirst().orElseThrow();
    }

    private static MonsterActionProfile action(ProductionSchema2Projection material, String key) {
        var identity = new EntryIdentity(SKELETON, "actions", MAGIC, key);
        return material.projection().entries().stream().map(FinalEntry::entry).filter(entry -> entry.identity().equals(identity))
                .map(MonsterActionEntry.class::cast).findFirst().orElseThrow().actionProfile();
    }

    private static ProductionSchema2Projection compose(List<Map<String, byte[]>> files) {
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
    }

    private static ProductionSchema2Projection restore(ProductionSchema2Projection material) {
        var projection = material.projection();
        return ProductionSchema2Projection.restore(material.members(), projection.rules(), projection.entries(), projection.chains());
    }

    private static List<Map<String, byte[]>> packages() throws IOException {
        List<Map<String, byte[]>> files = new ArrayList<>();
        for (String name : List.of("srd51-runtime", "srd51-characters", "srd51-magic", "dm-runtime-check-variant")) {
            var packageFiles = new LinkedHashMap<String, byte[]>();
            try (var source = Schema2RangedWeaponCompositionTest.class.getResourceAsStream("/storage/rules/" + name + ".zip")) {
                assertNotNull(source, name);
                try (var archive = new ZipInputStream(source)) {
                    for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry())
                        packageFiles.put(entry.getName(), archive.readAllBytes());
                }
            }
            files.add(packageFiles);
        }
        return files;
    }
}
