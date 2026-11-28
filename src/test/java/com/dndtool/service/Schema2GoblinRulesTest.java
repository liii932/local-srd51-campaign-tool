package com.dndtool.service;

import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.storage.BundledCampaignRules;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2GoblinRulesTest {
    @Test void frozenBonusesAndWeaponActionsStayUsableWithTheTypedTrait() {
        var material = BundledCampaignRules.projection();
        var goblin = Schema2MonsterRules.initial(material,
                new MonsterDeployment(UUID.randomUUID(), GOBLIN_MONSTER, new Position(0, 0), 1));
        var houndRule = new RuleIdentity("srd51_magic", "adventure.monster", "monster.hell_hound");
        var hound = Schema2MonsterRules.initial(material,
                new MonsterDeployment(UUID.randomUUID(), houndRule, new Position(3, 0), 1));
        var skeletonRule = new RuleIdentity("srd51_magic", "adventure.monster", "monster.skeleton");
        var skeleton = Schema2MonsterRules.initial(material,
                new MonsterDeployment(UUID.randomUUID(), skeletonRule, new Position(4, 0), 1));
        var observation = new ObservationState(List.of(new ObservationCellInput(new Position(0, 0), true, false,
                Cover.HALF)), List.of(), List.of());
        var roster = Map.of(UUID.randomUUID(), new Context(material, null, null, 0));
        var battle = new Battle(UUID.randomUUID(), UUID.randomUUID(), null, Status.ACTIVE,
                1, 1, 0, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(goblin, hound, skeleton), List.of(), List.of(), MeleeControl.EMPTY, observation);

        assertEquals(6, Schema2MonsterRules.stealthModifier(roster, battle, goblin.monsterId()));
        assertEquals(-1, Schema2MonsterRules.perceptionModifier(roster, battle, goblin.monsterId()));
        assertEquals(9, Schema2MonsterRules.passivePerception(roster, battle, goblin.monsterId()));
        assertEquals(5, Schema2MonsterRules.perceptionModifier(roster, battle, hound.monsterId()));
        assertEquals(2, Schema2MonsterRules.stealthModifier(roster, battle, skeleton.monsterId()));
        assertEquals(GOBLIN_MONSTER, Schema2MonsterRules.nimbleEscapeSource(roster, battle, goblin.monsterId()));
        assertNull(Schema2MonsterRules.nimbleEscapeSource(roster, battle, hound.monsterId()));
        assertEquals(List.of("scimitar"), Schema2MonsterRules.opportunityAttacks(roster, goblin).stream()
                .map(entry -> entry.identity().entryKey()).toList());
        var shortbow = new EntryIdentity(GOBLIN_MONSTER, "actions", GOBLIN_MONSTER.moduleKey(), "shortbow");
        assertTrue(Schema2MonsterRules.attack(roster, battle, goblin.monsterId(), shortbow, false).ranged());
        var after = Schema2MonsterRules.damage(battle, goblin.monsterId(), 2);
        assertEquals(5, Schema2MonsterRules.instance(after, goblin.monsterId()).hitPoints());
        assertEquals(observation, after.observation());
    }
}
