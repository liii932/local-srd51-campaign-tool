package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.ProductionSchema2Projection.StoredMember;
import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
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

    @Test void fixedV5SpellVectorsRetainDistinctAreaDirections() {
        // Independently encoded with big-endian integers, length-prefixed UTF-8 and UUID presence
        // followed by 16 bytes. The 893-byte Bless vector ends with three absent intent branches.
        var bless = new CastInput(rule("character.spellcasting", "spellcasting.life_domain"),
                rule("adventure.spell", "spell.bless"), List.of(id(5)), id(12));
        var east = new CastInput(rule("character.wizard_spellcasting", "spellcasting.wizard"),
                rule("adventure.spell", "spell.burning_hands"), List.of(), null, 1, null, 0);
        var northeast = new CastInput(east.castingRule(), east.spell(), List.of(), null, 1, null, 1);
        assertEquals("b29d19d9bb2497c5d98e4998199defa087db247b4d108e394ba77bf87ecd42be", request(Command.cast(id(4), bless)).digest());
        assertEquals("9b220e7a46ef31bae2284521fdb31bd1abe496ae260a2139a40eedd3459d5c1d", request(Command.cast(id(4), east)).digest());
        assertEquals("79c54f4c025a88358706c95b85a3ff1679f207b1d4fa39a0302119e1aeae2d42", request(Command.cast(id(4), northeast)).digest());
    }

    @Test void fixedV5VectorsBindTheExactMeleeAndDragIntents() {
        var grab = Command.melee(Kind.GRAPPLE, id(4), id(5), null, null,
                new MeleeInput(id(12), 0, null, HandSlot.OFF_HAND, null, null));
        assertEquals("4b9ffa933d5fe095462ea620da5716a39a12ebf291b9c088b0c250316c2a77a2", request(grab).digest());
        var push = Command.melee(Kind.SHOVE_PUSH, id(4), id(5), null, null,
                new MeleeInput(id(12), 1, null, null, null, new Position(3, -1)));
        assertEquals("aa5f2cb0bfc097c190b95c6e01657374edb77993f1fbac24c1b8aa8275d1e297", request(push).digest());
        var athletics = choice(ContestSkill.ATHLETICS, null);
        var acrobatics = choice(ContestSkill.ACROBATICS, null);
        assertEquals("d60ec0f102355258c0a18db4adcaf1ca0cb344e375da4c5c4975786508df3b46", request(athletics).digest());
        assertEquals("75aefec89df1543559a81183bfa672988e2c14a78c0465880198f213cddbc630", request(acrobatics).digest());
        assertNotEquals(request(acrobatics).digest(), request(choice(ContestSkill.ACROBATICS, -1)).digest());

        var path = List.of(new Position(2, 0), new Position(3, 0));
        var carried = List.of(new Position(2, 1), new Position(3, 1));
        var drag = Command.move(id(4), path, new DragInput(id(12), carried));
        String dragDigest = request(drag).digest();
        assertEquals("8488fc8dc7775f6cd2c09b41efcf23fd5064ab0b390cd4cdb582f9fe57dd1a9c", dragDigest);
        assertNotEquals(dragDigest, request(Command.move(id(4), path)).digest());
        assertNotEquals(dragDigest, request(Command.move(id(4), path, new DragInput(id(13), carried))).digest());
        assertNotEquals(dragDigest, request(Command.move(id(4), path, new DragInput(id(12), carried.reversed()))).digest());
    }

    @Test void fixedV5MonsterVectorsBindTheActionAndOpportunity() {
        var hound = new RuleIdentity("srd51_magic", "adventure.monster", "monster.hell_hound");
        var bite = new EntryIdentity(hound, "actions", "srd51_magic", "bite");
        var breath = new EntryIdentity(hound, "actions", "srd51_magic", "fire_breath");
        String attack = request(Command.monsterAttack(id(4), id(5), bite)).digest();
        assertEquals("91413f3bd2e4fa0c86a8c2e1785099a2959ee15385cfd1469f5238c1645e3ae2", attack);
        assertNotEquals(attack, request(Command.monsterAttack(id(4), id(5), breath)).digest());
        String reaction = request(Command.monsterOpportunityAttack(id(4), bite, id(12), id(13), id(14), id(15))).digest();
        assertEquals("0fef665cdd4d7f210873fdacfc3253e8f3a01678293ddb2e858120da4dc07f40", reaction);
        assertNotEquals(reaction, request(Command.monsterOpportunityAttack(id(4), bite, id(12), id(13), id(14), id(16))).digest());
    }

    private static Command choice(ContestSkill skill, Integer ordinal) {
        return Command.melee(Kind.CHOOSE_MELEE_DEFENSE, id(4), null, id(12), id(13),
                new MeleeInput(null, ordinal, null, null, skill, null));
    }
}
