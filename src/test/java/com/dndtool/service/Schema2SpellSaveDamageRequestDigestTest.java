package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.module.composition.Schema2CompositionKernel.Role;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CombatHistory.Member;
import com.dndtool.service.Schema2CombatState.*;
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

    private static Schema2CombatRequest request(Command command) {
        var binding = new Schema2RulesBinding(ProductionSchema2Projection.CANONICAL_PROFILE,
                ProductionSchema2Projection.COMPOSITION_ALGORITHM,
                List.of(new StoredMember("base", "1", Role.BASE, "1".repeat(64), "2".repeat(64))),
                "3".repeat(64), "4".repeat(64), "5".repeat(64));
        var members = List.of(new Member(id(4), new Schema2PreparationIdentity(id(6), id(7)), id(8)),
                new Member(id(5), new Schema2PreparationIdentity(id(9), id(10)), id(11)));
        return new Schema2CombatRequest(id(1), id(2), id(3), binding, members, 7,
                Map.of(id(4), 11L, id(5), 13L), command);
    }

    @Test void fixedV4SpellVectorsRetainDistinctAreaDirections() {
        // Independently encoded with big-endian integers, length-prefixed UTF-8 and UUID presence
        // followed by 16 bytes. The 892-byte Bless vector ends with two absent v4 intent branches.
        var bless = new CastInput(rule("character.spellcasting", "spellcasting.life_domain"),
                rule("adventure.spell", "spell.bless"), List.of(id(5)), id(12));
        var east = new CastInput(rule("character.wizard_spellcasting", "spellcasting.wizard"),
                rule("adventure.spell", "spell.burning_hands"), List.of(), null, 1, null, 0);
        var northeast = new CastInput(east.castingRule(), east.spell(), List.of(), null, 1, null, 1);
        assertEquals("0895a5a6124c97231ad66fdfd56c6c82a872526f7c73ac4cced24a7ed227f903", request(Command.cast(id(4), bless)).digest());
        assertEquals("204fa583d088792466af982e4b615ae3e83f4fe32c414aed9d13c73aab75bbef", request(Command.cast(id(4), east)).digest());
        assertEquals("84f9b26e4414f828c15131e70243ffbd8262dfd7a78e884f0ac5f43b80cb9374", request(Command.cast(id(4), northeast)).digest());
    }

    @Test void fixedV4VectorsBindTheExactMeleeAndDragIntents() {
        var grab = Command.melee(Kind.GRAPPLE, id(4), id(5), null, null,
                new MeleeInput(id(12), 0, null, HandSlot.OFF_HAND, null, null));
        assertEquals("a78b07de7ef66a1a7ac581b70f1e5d3d9b3ccf2c5baa1ed6a1e07bcb3741aa61", request(grab).digest());
        var push = Command.melee(Kind.SHOVE_PUSH, id(4), id(5), null, null,
                new MeleeInput(id(12), 1, null, null, null, new Position(3, -1)));
        assertEquals("4759b229f78ebe097c13c7bc01d76e41b288ecf76c25eb814f06af2847e4b958", request(push).digest());
        var athletics = choice(ContestSkill.ATHLETICS, null);
        var acrobatics = choice(ContestSkill.ACROBATICS, null);
        assertEquals("ebd3352aeca8fbab8c1d4975bf6e82829e0fc832e93d7dd401dce29851aa51a0", request(athletics).digest());
        assertEquals("ba918262a3ef2ed95aa0a8871c7cde3e3afef83104ea816ddff9a12957b62ed1", request(acrobatics).digest());
        assertNotEquals(request(acrobatics).digest(), request(choice(ContestSkill.ACROBATICS, -1)).digest());

        var path = List.of(new Position(2, 0), new Position(3, 0));
        var carried = List.of(new Position(2, 1), new Position(3, 1));
        var drag = Command.move(id(4), path, new DragInput(id(12), carried));
        String dragDigest = request(drag).digest();
        assertEquals("b678e05d6eb5a3424cb4c84f60718e38cf96cf62e3e21ceca3fdec45aedf1b1d", dragDigest);
        assertNotEquals(dragDigest, request(Command.move(id(4), path)).digest());
        assertNotEquals(dragDigest, request(Command.move(id(4), path, new DragInput(id(13), carried))).digest());
        assertNotEquals(dragDigest, request(Command.move(id(4), path, new DragInput(id(12), carried.reversed()))).digest());
    }

    private static Command choice(ContestSkill skill, Integer ordinal) {
        return Command.melee(Kind.CHOOSE_MELEE_DEFENSE, id(4), null, id(12), id(13),
                new MeleeInput(null, ordinal, null, null, skill, null));
    }
}
