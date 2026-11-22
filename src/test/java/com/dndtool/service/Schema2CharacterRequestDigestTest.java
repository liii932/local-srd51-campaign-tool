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
    @Test void skillCheckRetainsItsCanonicalCommandDigest() {
        // Python struct.pack and hashlib produced this 1093-byte vector, confirmed against the
        // pre-extraction candidate. The v1 domain ends in NUL, UUIDs are text, the two binding
        // members keep their order, and version is a big-endian long. String lengths count UTF-8
        // bytes, including the check's adjudication. The SKILL payload follows the empty growth
        // and rest fields without an extra presence byte, and an empty initial-abilities map ends it.
        var request = skillCheck(id(1), 257);
        assertEquals("fc73f35355f2949961f83f530b06b8c4e5bf8b704ebea75a36dec536f8d59a11", request.digest());
        assertNotEquals(request.digest(), skillCheck(id(1), 258).digest());
    }

    @Test void rejectsAReusedIdentityOrNegativeVersionBeforeComputingADigest() {
        assertEquals("S2_CHARACTER_IDENTITY",
                assertThrows(IllegalArgumentException.class, () -> skillCheck(id(5), 257)).getMessage());
        assertEquals("S2_CHARACTER_REQUEST",
                assertThrows(IllegalArgumentException.class, () -> skillCheck(id(1), -1)).getMessage());
    }

    private static Schema2CharacterRequest skillCheck(UUID operation, long version) {
        var binding = new Schema2RulesBinding(ProductionSchema2Projection.CANONICAL_PROFILE,
                ProductionSchema2Projection.COMPOSITION_ALGORITHM,
                List.of(new StoredMember("base", "1", Role.BASE, "1".repeat(64), "2".repeat(64)),
                        new StoredMember("overlay", "2.1", Role.EXTENSION, "a".repeat(64), "b".repeat(64))),
                "3".repeat(64), "4".repeat(64), "5".repeat(64));
        var check = new CheckInput(new RuleIdentity("base", "adventure.check", "check.srd2014"), "SKILL", "ability.strength",
                new RuleIdentity("base", "character.proficiency", "proficiency.skill.athletics"), 17, true, false, "攀越石墙");
        var command = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, check);
        return new Schema2CharacterRequest(operation, new Schema2PreparationIdentity(id(2), id(3)), id(4), id(5), id(6),
                binding, version, command, Map.of());
    }

    private static UUID id(int index) {
        return UUID.fromString(String.format(Locale.ROOT, "00000000-0000-4000-8000-%012d", index));
    }
}
