package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.module.composition.Schema2CompositionKernel.Role;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CombatHistory.Member;
import com.dndtool.service.Schema2CombatState.CastInput;
import com.dndtool.service.Schema2CombatState.Command;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Schema2SpellSaveDamageRequestDigestTest {
    private static UUID id(int index) {
        return UUID.fromString(String.format(Locale.ROOT, "00000000-0000-4000-8000-%012d", index));
    }

    private static RuleIdentity rule(String type, String key) {
        return new RuleIdentity("base", type, key);
    }

    private static Schema2CombatRequest request(CastInput cast) {
        var binding = new Schema2RulesBinding(ProductionSchema2Projection.CANONICAL_PROFILE,
                ProductionSchema2Projection.COMPOSITION_ALGORITHM,
                List.of(new StoredMember("base", "1", Role.BASE, "1".repeat(64), "2".repeat(64))),
                "3".repeat(64), "4".repeat(64), "5".repeat(64));
        var members = List.of(new Member(id(4), new Schema2PreparationIdentity(id(6), id(7)), id(8)),
                new Member(id(5), new Schema2PreparationIdentity(id(9), id(10)), id(11)));
        return new Schema2CombatRequest(id(1), id(2), id(3), binding, members, 7,
                Map.of(id(4), 11L, id(5), 13L), Command.cast(id(4), cast));
    }

    @Test void fixedLegacyNoAreaVectorRetainsItsOldBytesAndDirectionsHaveDistinctIntents() {
        // Independent pre-area v3 layout: 890 bytes, UTF-8 text with int length, UUID presence
        // followed by 16 bytes, ordered members 4/5 and no area field at all.
        var old = new CastInput(rule("character.spellcasting", "spellcasting.life_domain"),
                rule("adventure.spell", "spell.bless"), List.of(id(5)), id(12));
        var east = new CastInput(rule("character.wizard_spellcasting", "spellcasting.wizard"),
                rule("adventure.spell", "spell.burning_hands"), List.of(), null, 1, null, 0);
        var northeast = new CastInput(east.castingRule(), east.spell(), List.of(), null, 1, null, 1);
        String original = request(old).digest();
        String eastDigest = request(east).digest();
        assertEquals("0dd3b1a284874b20f2b822de01d7271f3dcc5ba5fd52adf329597ec6d736ff72", original);
        assertNotEquals(original, eastDigest);
        assertNotEquals(eastDigest, request(northeast).digest());
    }
}
