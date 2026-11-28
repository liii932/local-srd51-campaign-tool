package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.module.composition.Schema2CompositionKernel.Role;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Schema2CharacterRequestDigestTest {
    @Test void rolledAndPassiveSkillChecksHaveCanonicalCommandDigests() {
        // Independently encoded with Python struct.pack and hashlib. The v1 domain ends in NUL,
        // UUIDs are text, the two binding members keep their order, and version is a big-endian long.
        // UTF-8 byte lengths frame strings including the adjudication. After the empty growth and
        // rest fields, the SKILL payload ends in its length-prefixed GENERAL subject and an empty
        // initial-abilities map follows. Only the command kind differs across the 1104 and 1112 byte vectors.
        var request = skillCheck(id(1), 257);
        assertEquals("90213b1a1ca20dfcf7448910993338bfaddcb9e0e39bcac2a0c3dfab1c3e23e6", request.digest());
        assertEquals("c9b195deba9cf5466e0d354b2451e30446bb6a94fe91a59b48fa90310747ffcd",
                skillCheck(id(1), 257, Schema2CharacterRuntime.PASSIVE_CHECK).digest());
        assertNotEquals(request.digest(), skillCheck(id(1), 258).digest());
    }

    @Test void rejectsAReusedIdentityOrNegativeVersionBeforeComputingADigest() {
        assertEquals("S2_CHARACTER_IDENTITY",
                assertThrows(IllegalArgumentException.class, () -> skillCheck(id(5), 257)).getMessage());
        assertEquals("S2_CHARACTER_REQUEST",
                assertThrows(IllegalArgumentException.class, () -> skillCheck(id(1), -1)).getMessage());
    }

    @Test void chosenCantripsBindTheirExactIdentitiesInCanonicalOrder() {
        var fireBolt = new RuleIdentity("srd51_magic", "adventure.spell", "spell.fire_bolt");
        var light = new RuleIdentity("srd51_magic", "adventure.spell", "spell.light");
        var otherLight = new RuleIdentity("other_magic", light.ruleType(), light.ruleKey());
        assertEquals(advanceWithCantrips(List.of(fireBolt, light)), advanceWithCantrips(List.of(light, fireBolt)));
        assertNotEquals(advanceWithCantrips(List.of(fireBolt, light)), advanceWithCantrips(List.of(fireBolt, otherLight)));
        assertNotEquals(advanceWithCantrips(List.of(fireBolt, light)), advanceWithCantrips(List.of()));
    }

    private static String advanceWithCantrips(List<RuleIdentity> cantrips) {
        var request = skillCheck(id(1), 257);
        var owner = new RuleIdentity("srd51_characters", "character.class", "class.wizard");
        var choices = new GrowthChoices(null, Map.of(), null, Map.of(), cantrips);
        var command = new Command("CHARACTER_ADVANCED", null, 0, null, "FIXED_AVERAGE", owner, choices);
        return new Schema2CharacterRequest(request.operationId(), request.identity(), request.campaignId(),
                request.characterId(), request.preparationOperationId(), request.rules(), request.expectedVersion(),
                command, Map.of()).digest();
    }

    private static Schema2CharacterRequest skillCheck(UUID operation, long version) {
        return skillCheck(operation, version, Schema2CharacterRuntime.ROLLED_CHECK);
    }

    private static Schema2CharacterRequest skillCheck(UUID operation, long version, String kind) {
        var binding = new Schema2RulesBinding(ProductionSchema2Projection.CANONICAL_PROFILE,
                ProductionSchema2Projection.COMPOSITION_ALGORITHM,
                List.of(new StoredMember("base", "1", Role.BASE, "1".repeat(64), "2".repeat(64)),
                        new StoredMember("overlay", "2.1", Role.EXTENSION, "a".repeat(64), "b".repeat(64))),
                "3".repeat(64), "4".repeat(64), "5".repeat(64));
        var check = new CheckInput(new RuleIdentity("base", "adventure.check", "check.srd2014"), "SKILL", "ability.strength",
                new RuleIdentity("base", "character.proficiency", "proficiency.skill.athletics"), 17, true, false, "攀越石墙");
        var command = new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, null, check);
        return new Schema2CharacterRequest(operation, new Schema2PreparationIdentity(id(2), id(3)), id(4), id(5), id(6),
                binding, version, command, Map.of());
    }

    private static UUID id(int index) {
        return UUID.fromString(String.format(Locale.ROOT, "00000000-0000-4000-8000-%012d", index));
    }
}
