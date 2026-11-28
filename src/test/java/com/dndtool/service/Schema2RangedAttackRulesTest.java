package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.HealthState;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.storage.CampaignRuleContext;
import com.dndtool.service.storage.CampaignRuleFixtures;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.Schema2CheckTestFixture.equipment;
import static org.junit.jupiter.api.Assertions.*;

class Schema2RangedAttackRulesTest {
    private static final UUID FIRST_ARROW = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_ARROW = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Test void ordinaryAttackUsesDexterityAndAnExactCarriedArrowAtTheNormalRangeBoundary() {
        var game = new Game("fighter");
        game.armBow(true);
        game.arrow(OTHER_ARROW);
        game.arrow(FIRST_ARROW);
        game.start(new Position(16, 16), true);
        game.begin();

        var shot = game.apply(game.shoot(), 12, 4);
        var attack = game.lastAttack();
        assertEquals(new RangedAttackEvidence(80, 80, 320, false, FIRST_ARROW), attack.rangedAttack());
        assertEquals(1, attack.abilityModifier());
        assertEquals(2, attack.proficiencyBonus());
        assertFalse(attack.disadvantage());
        assertEquals(List.of(4), attack.damageDice());
        assertEquals(5, attack.damageAmount());
        assertEquals(0, attack.attackOrdinal());
        assertEquals(1, game.actor(game.shooter).sequence().performed());
        assertEquals(1, shot.ammunitionUses().size());
        var cost = shot.ammunitionUses().getFirst();
        assertEquals(FIRST_ARROW, cost.itemId());
        assertEquals(attack.frameKey(), cost.frameKey());
        assertEquals(cost.before().version() + 1, cost.after().version());
        assertFalse(game.carries(FIRST_ARROW));
        assertTrue(game.carries(OTHER_ARROW));
    }

    @Test void longRangeIncludesTheMaximumAndSpendsOnAMissButRejectedShotsSpendNothing() {
        var game = new Game("fighter");
        game.armBow(true);
        game.arrow(FIRST_ARROW);
        game.start(new Position(64, 64), true);
        game.begin();
        game.apply(game.shoot(), 20, 1);
        assertEquals(320, game.lastAttack().rangedAttack().distanceFeet());
        assertTrue(game.lastAttack().disadvantage());
        assertEquals(1, game.lastAttack().selectedD20());
        assertFalse(game.lastAttack().hit());
        assertFalse(game.carries(FIRST_ARROW));

        var tooFar = new Game("fighter");
        tooFar.armBow(true);
        tooFar.arrow(FIRST_ARROW);
        tooFar.start(new Position(65, 0), true);
        tooFar.begin();
        tooFar.reject(tooFar.shoot(), "S2_COMBAT_OUT_OF_RANGE");
        assertTrue(tooFar.carries(FIRST_ARROW));
        assertEquals(0, tooFar.actor(tooFar.shooter).sequence().performed());

        var noArrow = new Game("fighter");
        noArrow.armBow(true);
        noArrow.start(new Position(2, 0), true);
        noArrow.begin();
        noArrow.reject(noArrow.shoot(), "S2_COMBAT_AMMUNITION_UNAVAILABLE");
        assertEquals(0, noArrow.actor(noArrow.shooter).sequence().performed());

        var packedBow = new Game("fighter");
        packedBow.armBow(false);
        packedBow.arrow(FIRST_ARROW);
        packedBow.start(new Position(2, 0), true);
        packedBow.begin();
        packedBow.reject(packedBow.shoot(), "S2_COMBAT_WEAPON_NOT_PREPARED");
        assertTrue(packedBow.carries(FIRST_ARROW));
    }

    @Test void onlyALiveAdjacentEnemyDisadvantagesTheShotAtAnotherTarget() {
        for (boolean enemy : List.of(false, true)) for (boolean conscious : List.of(false, true)) {
            var game = new Game("fighter");
            game.armBow(true);
            game.arrow(FIRST_ARROW);
            game.add(game.nearby, "fighter");
            if (!conscious) game.replace(game.nearby, Schema2CombatTestFixture.withHealth(
                    game.roster.get(game.nearby).character(), 0, HealthState.INITIAL));
            game.apply(Command.start(List.of(new Deployment(game.shooter, new Position(0, 0), 0),
                    new Deployment(game.target, new Position(10, 0), 1),
                    new Deployment(game.nearby, new Position(1, 1), enemy ? 1 : 0)),
                    List.of(game.shooter, game.target, game.nearby)), 20, 10, 1);
            game.begin();
            boolean threat = enemy && conscious;
            game.apply(game.shoot(), threat ? new int[] {16, 2} : new int[] {16, 3});
            var attack = game.lastAttack();
            assertEquals(threat, attack.rangedAttack().closeEnemy());
            assertEquals(threat, attack.disadvantage());
            assertEquals(threat ? 2 : 16, attack.selectedD20());
        }
    }

    @Test void closeRangeAndProneAdvantageCancelAndWizardsLackBowProficiency() {
        var game = new Game("wizard", "fighter");
        game.armBow(true);
        game.arrow(FIRST_ARROW);
        game.start(new Position(1, 1), false);
        game.apply(Command.action(Kind.DROP_PRONE, game.target));
        game.apply(Command.action(Kind.END_TURN, game.target));
        game.begin();
        game.apply(game.shoot(), 20, 2, 3);
        var attack = game.lastAttack();
        assertTrue(attack.advantage());
        assertTrue(attack.disadvantage());
        assertNull(attack.secondD20());
        assertEquals(0, attack.proficiencyBonus());
        assertTrue(attack.critical());
        assertEquals(List.of(2, 3), attack.damageDice());
        assertEquals(7, attack.damageAmount());
    }

    @Test void shieldResumesTheSameShotAndChampionNineteenStillDoublesOnlyDamageDice() {
        for (boolean champion : List.of(false, true)) {
            var game = new Game("fighter", "wizard");
            if (champion) game.advanceChampion();
            game.prepareShield();
            game.armBow(true);
            game.arrow(FIRST_ARROW);
            game.start(new Position(4, 4), true);
            game.begin();

            int original = champion ? 19 : 10;
            var launched = game.apply(game.shoot(), original);
            var pending = game.lastAttack();
            assertEquals(AttackStatus.HIT_PENDING_DAMAGE, pending.status());
            assertEquals(champion, pending.critical());
            assertEquals(1, game.actor(game.shooter).sequence().performed());
            assertTrue(game.actor(game.shooter).sequence().closed());
            assertEquals(FIRST_ARROW, launched.ammunitionUses().getFirst().itemId());
            assertFalse(game.carries(FIRST_ARROW));
            game.reject(game.shoot(), "ACTION_FLOW_BUSY");
            long version = game.roster.get(game.shooter).character().version();

            var shield = game.apply(game.shield());
            assertTrue(shield.ammunitionUses().isEmpty());
            assertEquals(pending, game.lastAttack());
            var finish = game.apply(game.continueAttack(), champion ? new int[] {2, 3} : new int[0]);
            assertTrue(finish.ammunitionUses().isEmpty());
            assertEquals(version, game.roster.get(game.shooter).character().version());
            var resolved = game.lastAttack();
            assertEquals(AttackStatus.RESOLVED, resolved.status());
            assertEquals(original, resolved.selectedD20());
            assertEquals(pending.abilityModifier(), resolved.abilityModifier());
            assertEquals(pending.rangedAttack(), resolved.rangedAttack());
            assertEquals(pending.armorClass() + 5, resolved.finalArmorClass());
            assertEquals(champion, resolved.hit());
            assertEquals(champion, resolved.critical());
            assertEquals(champion ? 6 : 0, resolved.damageAmount());
        }
    }

    @Test void hiddenShooterIgnoresUnseeingDodgeAndAdjacentHostilityAndRevealsOnAMiss() {
        var game = new Game("fighter");
        game.armBow(true);
        game.arrow(FIRST_ARROW);
        game.add(game.nearby, "fighter");
        Position firingCell = new Position(0, 0);
        game.apply(Command.start(List.of(new Deployment(game.shooter, firingCell, 0),
                new Deployment(game.target, new Position(3, 0), 1), new Deployment(game.nearby, new Position(0, 1), 1)),
                List.of(game.shooter, game.target, game.nearby)), 20, 10, 2);
        game.cell(firingCell, true, false, Cover.NONE);
        game.apply(Command.action(Kind.HIDE, game.shooter), 20);
        game.apply(Command.action(Kind.END_TURN, game.shooter));
        game.apply(Command.action(Kind.DODGE, game.target));
        game.apply(Command.action(Kind.END_TURN, game.target));
        game.apply(Command.action(Kind.END_TURN, game.nearby));
        assertFalse(Schema2ObservationRules.visible(game.battle, game.target, game.shooter));
        assertFalse(Schema2ObservationRules.visible(game.battle, game.nearby, game.shooter));

        game.begin();
        game.apply(game.shoot(), 1, 2);
        var attack = game.lastAttack();
        assertTrue(attack.attackerSeesTarget());
        assertFalse(attack.targetSeesAttacker());
        assertTrue(attack.advantage());
        assertFalse(attack.disadvantage());
        assertFalse(attack.rangedAttack().closeEnemy());
        assertFalse(attack.hit());
        assertTrue(Schema2ObservationRules.visible(game.battle, game.target, game.shooter));
        assertTrue(Schema2ObservationRules.visible(game.battle, game.nearby, game.shooter));
    }

    @Test void hiddenTargetsCanBeAttackedAtTheRememberedCellWithoutTrackingTheirMovement() {
        for (boolean moved : List.of(false, true)) {
            var game = new Game("fighter", "wizard");
            game.prepareShield();
            game.armBow(true);
            game.arrow(FIRST_ARROW);
            Position remembered = new Position(2, 0), next = new Position(3, 0);
            game.start(remembered, false);
            game.cell(remembered, true, false, Cover.NONE);
            game.cell(next, true, false, Cover.NONE);
            game.apply(Command.action(Kind.HIDE, game.target), 20);
            if (moved) game.apply(Command.move(game.target, List.of(next)));
            game.apply(Command.action(Kind.END_TURN, game.target));
            assertEquals(remembered, Schema2ObservationRules.knownPosition(game.battle, game.shooter, game.target));

            game.begin();
            var shot = game.apply(game.shoot(), 20, 20);
            var attack = game.lastAttack();
            assertEquals(remembered, attack.aimedPosition());
            assertEquals(10, attack.rangedAttack().distanceFeet());
            assertEquals(!moved, attack.targetAtAimedPosition());
            assertFalse(attack.attackerSeesTarget());
            assertTrue(attack.disadvantage());
            assertEquals(20, attack.selectedD20());
            assertEquals(!moved, attack.initialHit());
            assertEquals(!moved, attack.critical());
            assertEquals(!moved, game.battle.activeFlow() != null);
            assertFalse(game.carries(FIRST_ARROW));
            assertTrue(shot.healthChanges().isEmpty());
            assertFalse(Schema2ObservationRules.visible(game.battle, game.shooter, game.target));
        }
    }

    @Test void aStaleAimInTheShootersCellStillCannotHitTheHiddenTarget() {
        var game = new Game("fighter");
        game.armBow(true);
        game.arrow(FIRST_ARROW);
        Position remembered = new Position(2, 0), next = new Position(3, 0);
        game.start(remembered, false);
        game.cell(remembered, true, false, Cover.NONE);
        game.cell(next, true, false, Cover.NONE);
        game.apply(Command.action(Kind.HIDE, game.target), 20);
        game.apply(Command.move(game.target, List.of(next)));
        game.apply(Command.action(Kind.END_TURN, game.target));
        game.apply(Command.move(game.shooter, List.of(new Position(1, 0), remembered)));
        game.begin();

        var shot = game.apply(game.shoot(), 20, 20);
        assertEquals(0, game.lastAttack().rangedAttack().distanceFeet());
        assertFalse(game.lastAttack().initialHit());
        assertFalse(game.lastAttack().hit());
        assertTrue(shot.healthChanges().isEmpty());
        assertFalse(game.carries(FIRST_ARROW));
    }

    @Test void coverChangesTheHitThresholdAndTheOriginalBonusStaysThroughShield() {
        for (Cover cover : List.of(Cover.HALF, Cover.THREE_QUARTERS)) {
            var game = new Game("wizard");
            game.prepareShield();
            game.armBow(true);
            game.arrow(FIRST_ARROW);
            Position defenseCell = new Position(4, 0);
            game.start(defenseCell, true);
            game.cell(defenseCell, false, false, cover);
            int baseAc = game.equipment.derive(game.material, game.roster.get(game.target).character()).armorClass();
            game.begin();
            var launched = game.apply(game.shoot(), baseAc + cover.armorClassBonus() - 3);
            var pending = game.lastAttack();
            assertEquals(baseAc + cover.armorClassBonus(), pending.armorClass());
            assertEquals(cover, pending.targetCover());
            assertTrue(pending.initialHit());
            assertEquals(1, launched.rolls().size());
            game.apply(game.shield());
            var finished = game.apply(game.continueAttack());
            var resolved = game.lastAttack();
            assertEquals(baseAc + cover.armorClassBonus() + 5, resolved.finalArmorClass());
            assertEquals(cover, resolved.targetCover());
            assertEquals(defenseCell, resolved.aimedPosition());
            assertEquals(pending.selectedD20(), resolved.selectedD20());
            assertFalse(resolved.hit());
            assertTrue(finished.rolls().isEmpty());
            assertTrue(finished.healthChanges().isEmpty());
        }
    }

    @Test void totalCoverStopsDirectAttacksBeforeSpendingAnArrowOrAttackOrdinal() {
        var game = new Game("fighter");
        game.armBow(true);
        game.arrow(FIRST_ARROW);
        Position defenseCell = new Position(4, 0);
        game.start(defenseCell, true);
        game.cell(new Position(2, 0), false, false, Cover.TOTAL);
        assertEquals(defenseCell, Schema2ObservationRules.knownPosition(game.battle, game.shooter, game.target));
        game.begin();
        game.reject(game.shoot(), "S2_COMBAT_TOTAL_COVER");
        assertTrue(game.carries(FIRST_ARROW));
        assertEquals(0, game.actor(game.shooter).sequence().performed());
        assertTrue(game.battle.flows().isEmpty());
    }

    @Test void opportunityAttackUsesTheVisibleDepartureCellAndThenResumesTheSameMovement() {
        for (boolean hiddenAtDeparture : List.of(false, true)) {
            var game = new Game("fighter");
            game.armDagger(game.target);
            Position departure = new Position(2, 0), boundary = new Position(3, 0), finish = new Position(4, 0);
            game.apply(Command.start(List.of(new Deployment(game.shooter, departure, 0),
                    new Deployment(game.target, new Position(1, 0), 1)), List.of(game.shooter, game.target)), 20, 2);
            game.cell(departure, true, false, Cover.HALF);
            game.cell(boundary, true, true, Cover.NONE);
            game.cell(finish, true, true, Cover.NONE);
            if (hiddenAtDeparture) game.apply(Command.action(Kind.HIDE, game.shooter), 20);

            game.apply(Command.move(game.shooter, List.of(boundary, finish)));
            UUID movementKey = game.battle.flows().getLast().movements().getFirst().movementKey();
            if (!hiddenAtDeparture) {
                assertEquals(departure, game.actor(game.shooter).position());
                var flow = game.battle.activeFlow();
                var window = game.activeWindow();
                var option = window.opportunities().getFirst();
                assertEquals(game.target, option.actorId());
                game.apply(Command.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK, game.target, option.options().getFirst().itemId(),
                        flow.flowKey(), flow.leafFrameKey(), window.windowKey(), option.opportunityKey()), 1);
                assertEquals(Cover.HALF, game.lastAttack().targetCover());
                assertEquals(departure, game.lastAttack().aimedPosition());
                game.apply(game.continueFlow(game.shooter));
            } else {
                assertTrue(game.battle.flows().getLast().windows().isEmpty());
                assertTrue(game.actor(game.target).reactionAvailable());
            }
            assertNull(game.battle.activeFlow());
            assertEquals(finish, game.actor(game.shooter).position());
            assertEquals(departure, Schema2ObservationRules.knownPosition(game.battle, game.target, game.shooter));
            var movement = game.battle.flows().getLast().movements().getFirst();
            assertEquals(movementKey, movement.movementKey());
            assertEquals(2, movement.cursor());
        }
    }

    @Test void aReadiedMeleeAttackAfterShieldCanDropTheBowWithoutStoppingTheLaunchedShot() {
        var game = new Game("wizard");
        game.prepareShield();
        game.armBow(true);
        game.arrow(FIRST_ARROW);
        game.replace(game.shooter, Schema2CombatTestFixture.withHealth(game.roster.get(game.shooter).character(), 3, HealthState.INITIAL));
        game.add(game.nearby, "fighter");
        UUID dagger = game.armDagger(game.nearby);
        game.apply(Command.start(List.of(new Deployment(game.shooter, new Position(0, 0), 0),
                new Deployment(game.target, new Position(4, 0), 1), new Deployment(game.nearby, new Position(0, 1), 1)),
                List.of(game.nearby, game.shooter, game.target)), 10, 2, 20);
        var trigger = new ReadyTrigger(TriggerKind.CAST_COMPLETED, game.target, null);
        game.apply(Command.ready(game.nearby, new ReadyInput(trigger, ReadyResponseKind.ATTACK, game.shooter, dagger, List.of(), null)));
        game.apply(Command.action(Kind.END_TURN, game.nearby));
        game.begin();
        game.apply(game.shoot(), 20, 20);
        var pending = game.lastAttack();
        game.apply(game.shield());

        Flow flow = game.battle.activeFlow();
        Window window = game.activeWindow();
        var opportunity = window.opportunities().getFirst();
        var ready = game.battle.readyPreparations().getFirst();
        game.apply(Command.releaseReady(game.nearby, ready.readyKey(), flow.flowKey(), flow.leafFrameKey(),
                window.windowKey(), opportunity.opportunityKey()), 15, 3);
        assertEquals(0, game.roster.get(game.shooter).character().hitPoints());
        assertTrue(game.roster.get(game.shooter).character().equipment().stream()
                .anyMatch(item -> item.itemId().equals(game.bow) && "GROUND".equals(item.location())));

        game.apply(game.continueFlow(game.target));
        var finished = game.apply(game.continueFlow(game.shooter), 2, 3);
        assertTrue(finished.ammunitionUses().isEmpty());
        assertEquals(pending.rangedAttack(), game.lastAttack().rangedAttack());
        assertEquals(List.of(2, 3), game.lastAttack().damageDice());
        assertEquals(6, game.lastAttack().damageAmount());
    }

    @Test void bowsAreExcludedFromCharacterReadyAndFromEitherSidesOpportunityOptions() {
        var game = new Game("fighter");
        game.armBow(true);
        game.arrow(FIRST_ARROW);
        game.start(new Position(1, 0), true);
        var trigger = new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER, game.target, new Position(2, 0));
        game.reject(Command.ready(game.shooter, new ReadyInput(trigger, ReadyResponseKind.ATTACK, game.target,
                game.bow, List.of(), null)), "S2_COMBAT_MELEE_WEAPON_REQUIRED");
        game.apply(Command.action(Kind.END_TURN, game.shooter));
        game.apply(Command.move(game.target, List.of(new Position(2, 0))));
        assertNull(game.battle.activeFlow());
        assertTrue(game.actor(game.shooter).reactionAvailable());
        assertTrue(game.carries(FIRST_ARROW));

        var monsters = new Game("fighter");
        monsters.startWithSkeleton(new Position(1, 0), false);
        monsters.apply(Command.move(monsters.shooter, List.of(new Position(-1, 0))));
        Flow flow = monsters.battle.activeFlow();
        Window window = flow.windows().getLast();
        var opportunity = window.opportunities().getFirst();
        assertEquals(List.of("shortsword"), opportunity.options().stream().map(option -> option.monsterAttack().entryKey()).toList());
        monsters.reject(Command.monsterOpportunityAttack(monsters.nearby, monsters.skeletonAction("shortbow"),
                flow.flowKey(), flow.leafFrameKey(), window.windowKey(), opportunity.opportunityKey()), "REACTION_OPTION_INVALID");
    }

    @Test void monsterRangedAttackUsesItsOwnFrozenProfileAndActionWithNoCharacterAmmunition() {
        var game = new Game("fighter");
        game.startWithSkeleton(new Position(17, 0), true);
        var shot = game.apply(Command.monsterAttack(game.nearby, game.shooter, game.skeletonAction("shortbow")),
                18, 12, 4);
        assertTrue(shot.ammunitionUses().isEmpty());
        var attack = game.lastAttack();
        assertEquals(new RangedAttackEvidence(85, 80, 320, false, null), attack.rangedAttack());
        assertEquals(4, attack.monsterAttack().hitBonus());
        assertEquals(0, attack.monsterAttack().reachFeet());
        assertEquals(12, attack.selectedD20());
        assertEquals(6, attack.damageAmount());
        assertFalse(game.actor(game.nearby).actionAvailable());
        assertTrue(game.actor(game.nearby).reactionAvailable());
    }

    private static final class Game {
        final UUID campaign = UUID.randomUUID(), combatId = UUID.randomUUID();
        final UUID shooter = UUID.randomUUID(), target = UUID.randomUUID(), nearby = UUID.randomUUID(), bow = UUID.randomUUID();
        final Map<UUID, Context> roster = new HashMap<>();
        final Schema2CombatRules combat = new Schema2CombatRules();
        final Schema2CharacterEquipmentRules equipment = new Schema2CharacterEquipmentRules();
        final Schema2CharacterProgressionRules growth = new Schema2CharacterProgressionRules();
        final CampaignRuleContext catalog = CampaignRuleFixtures.rules();
        final ProductionSchema2Projection material = catalog.projection();
        Battle battle;

        Game(String targetClass) { this("fighter", targetClass); }
        Game(String shooterClass, String targetClass) {
            add(shooter, shooterClass);
            add(target, targetClass);
        }
        void add(UUID actor, String classKey) {
            var plan = catalog.initialPlan(campaign, actor, Schema2PreparationIdentity.random(), classKey, "acolyte");
            var state = growth.initial(material, plan, UUID.randomUUID(), catalog.initialAbilities(classKey), catalog.initialChoices(classKey));
            roster.put(actor, new Context(material, plan, state));
        }
        void replace(UUID actor, State state) {
            roster.put(actor, new Context(material, roster.get(actor).initial(), state));
        }
        void advanceChampion() {
            var source = roster.get(shooter);
            var fighter = catalog.rule("character.class", "class.fighter");
            var champion = catalog.rule("character.subclass", "subclass.champion");
            var second = growth.advance(material, growth.prepareAdvance(material, source.initial(), source.character(), fighter,
                    2, "FIXED_AVERAGE", GrowthChoices.EMPTY), null);
            var choices = new GrowthChoices(champion, Map.of(), null, Map.of());
            replace(shooter, growth.advance(material, growth.prepareAdvance(material, source.initial(), second, fighter,
                    3, "FIXED_AVERAGE", choices), null));
        }
        void prepareShield() {
            var casting = catalog.rule("character.wizard_spellcasting", "spellcasting.wizard");
            var book = new SpellbookInput(casting, catalog.initialWizardBook(), catalog.initialWizardPrepared());
            replace(target, new Schema2WizardSpellbookRules().apply(material, roster.get(target).character(),
                    Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_INITIALIZED", book)));
        }
        void armBow(boolean equip) {
            var state = acquire(bow, "item.shortbow");
            if (equip) replace(shooter, equipment.apply(material, state, equipment("ITEM_EQUIPPED", bow, null, "BOTH_HANDS"),
                    UUID.randomUUID()));
        }
        UUID armDagger(UUID actor) {
            UUID instance = UUID.randomUUID();
            var dagger = catalog.rule("equipment.item", "item.dagger");
            var state = equipment.apply(material, roster.get(actor).character(),
                    equipment("ITEM_ACQUIRED", instance, dagger, "CARRIED"), instance);
            replace(actor, equipment.apply(material, state, equipment("ITEM_EQUIPPED", instance, null, "MAIN_HAND"),
                    UUID.randomUUID()));
            return instance;
        }
        void arrow(UUID item) { acquire(item, "item.arrow"); }
        State acquire(UUID id, String ruleKey) {
            var item = catalog.rule("equipment.item", ruleKey);
            var state = equipment.apply(material, roster.get(shooter).character(), equipment("ITEM_ACQUIRED", id, item, "CARRIED"), id);
            replace(shooter, state);
            return state;
        }
        boolean carries(UUID item) {
            return roster.get(shooter).character().equipment().stream()
                    .anyMatch(e -> e.itemId().equals(item) && "CARRIED".equals(e.location()));
        }
        void start(Position targetPosition, boolean shooterFirst) {
            apply(Command.start(List.of(new Deployment(shooter, new Position(0, 0), 0), new Deployment(target, targetPosition, 1)),
                    List.of(shooter, target)), shooterFirst ? 20 : 2, shooterFirst ? 2 : 20);
        }
        void startWithSkeleton(Position position, boolean monsterFirst) {
            apply(Command.start(List.of(new Deployment(shooter, new Position(0, 0), 0),
                    new Deployment(target, new Position(40, 40), 0)),
                    List.of(new MonsterDeployment(nearby, skeleton(), position, 1)), List.of(shooter, target, nearby)),
                    monsterFirst ? 10 : 20, 2, monsterFirst ? 20 : 10);
        }
        RuleIdentity skeleton() { return catalog.rule("adventure.monster", "monster.skeleton"); }
        EntryIdentity skeletonAction(String key) {
            return new EntryIdentity(skeleton(), "actions", skeleton().moduleKey(), key);
        }
        void begin() { apply(Command.action(Kind.BEGIN_ATTACK, shooter)); }
        void cell(Position position, boolean hideAllowed, boolean blocksSight, Cover cover) {
            apply(Command.setObservationCell(new ObservationCellInput(position, hideAllowed, blocksSight, cover)));
        }
        Command shoot() { return Command.attack(shooter, target, bow); }
        Command shield() {
            Flow flow = battle.activeFlow();
            Window window = flow.windows().getLast();
            var opportunity = window.opportunities().getFirst();
            var casting = catalog.rule("character.wizard_spellcasting", "spellcasting.wizard");
            var shield = catalog.rule("adventure.spell", "spell.shield");
            return Command.reactionSpell(Kind.DECLARE_SHIELD, target, new CastInput(casting, shield, List.of(target), null),
                    flow.flowKey(), flow.leafFrameKey(), window.windowKey(), opportunity.opportunityKey());
        }
        Window activeWindow() {
            Flow flow = battle.activeFlow();
            var leaf = flow.frames().stream().filter(frame -> frame.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
            return flow.windows().stream().filter(window -> window.windowKey().equals(leaf.windowKey())).findFirst().orElseThrow();
        }
        Command continueAttack() { return continueFlow(shooter); }
        Command continueFlow(UUID actor) {
            Flow flow = battle.activeFlow();
            return new Command(Kind.CONTINUE_FLOW, actor, null, null, List.of(), List.of(), List.of(),
                    flow.flowKey(), flow.leafFrameKey(), activeWindow().windowKey(), null);
        }
        Combatant actor(UUID id) { return Schema2CombatRules.combatant(battle, id); }
        AttackAttempt lastAttack() { return battle.flows().getLast().attacks().getFirst(); }
        void reject(Command command, String code) {
            assertEquals(code, assertThrows(IllegalArgumentException.class,
                    () -> combat.preflight(UUID.randomUUID(), combatId, campaign, roster, battle, command)).getMessage());
        }
        Resolution apply(Command command, int... rolls) {
            UUID operation = UUID.randomUUID();
            var dice = new ArrayDeque<>(Arrays.stream(rolls).boxed().toList());
            var prepared = combat.preflight(operation, combatId, campaign, roster, battle, command);
            var result = combat.resolve(prepared, sides -> {
                assertFalse(dice.isEmpty(), "Unexpected d" + sides);
                return dice.removeFirst();
            });
            assertTrue(dice.isEmpty(), "Unused dice " + dice);
            combat.verify(operation, combatId, campaign, roster, battle, command, result);
            for (AmmunitionUse use : result.ammunitionUses()) replace(use.characterId(), use.after());
            for (HealthChange change : result.healthChanges()) replace(change.characterId(), change.after());
            battle = result.after();
            return result;
        }
    }
}
