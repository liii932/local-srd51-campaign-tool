package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.DamageType;
import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.Schema2GrantFixtures;
import com.dndtool.module.composition.Schema2MonsterFixtures;
import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.module.composition.Schema2SpellFixtures;
import com.dndtool.module.composition.Schema2WizardFixtures;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.storage.CampaignDomainCodec;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.MONSTER_MELEE_ATTACK_MECHANISM;
import static com.dndtool.module.composition.Schema2MonsterFixtures.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.BASE;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullAbilities;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullPlan;
import static com.dndtool.service.Schema2CombatTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2MonsterAttackRulesTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), HOUND = UUID.randomUUID(), OTHER = UUID.randomUUID();
    private static final EntryIdentity BITE = new EntryIdentity(HELL_HOUND, "actions", "monster_base", "bite");
    private static final EntryIdentity CLUB = new EntryIdentity(COMMONER, "actions", "monster_base", "club");

    @Test void packTacticsUsesLiveAlliesAndCriticalDoublesBothTypedComponentsBeforeDefenses() {
        for (boolean allyIncapacitated : List.of(false, true)) {
            var game = new Fixture(false, false);
            if (allyIncapacitated) {
                Context context = game.roster.get(A);
                var inactive = withHealth(context.character(), 0, Schema2CharacterRuntime.HealthState.INITIAL);
                game.roster.put(A, withState(context, Schema2CombatEquipmentFacts.drop(inactive, game.combat, new Position(1, 1))));
            }
            game.start(List.of(new Deployment(A, new Position(1, 1), 1), new Deployment(B, new Position(4, 4), 0)),
                    List.of(new MonsterDeployment(HOUND, HELL_HOUND, new Position(0, 0), 1),
                            new MonsterDeployment(OTHER, HELL_HOUND, new Position(1, 0), 0)), 1, 2, 20, 3);
            var result = allyIncapacitated ? game.apply(Command.monsterAttack(HOUND, OTHER, BITE), 20, 4, 5, 1, 2, 3, 4)
                    : game.apply(Command.monsterAttack(HOUND, OTHER, BITE), 4, 20, 4, 5, 1, 2, 3, 4);
            AttackAttempt attack = game.lastAttack();
            assertEquals(allyIncapacitated ? List.of() : List.of(A), attack.monsterAttack().packTacticsAllies());
            assertEquals(!allyIncapacitated, attack.advantage());
            assertEquals(20, attack.selectedD20());
            assertTrue(attack.critical());
            assertNull(attack.itemId());
            assertNull(attack.sequenceKey());
            assertEquals(List.of(), attack.damageDice());
            var components = attack.monsterAttack().damageComponents();
            assertEquals(List.of(DamageType.PIERCING, DamageType.FIRE), components.stream().map(AttackDamageComponent::damageType).toList());
            assertEquals(List.of(4, 5), components.getFirst().dice());
            assertEquals(12, components.getFirst().rawDamage());
            assertEquals(12, components.getFirst().effectiveDamage());
            assertEquals(List.of(1, 2, 3, 4), components.getLast().dice());
            assertEquals(10, components.getLast().rawDamage());
            assertEquals(0, components.getLast().effectiveDamage());
            assertEquals(List.of(new DefenseContribution(HELL_HOUND, "IMMUNITY")), components.getLast().defenses());
            assertEquals(12, attack.damageAmount());
            assertEquals(33, Schema2MonsterRules.instance(game.battle, OTHER).hitPoints());
            assertFalse(game.actor(HOUND).actionAvailable());
            assertTrue(game.actor(HOUND).reactionAvailable());
            assertTrue(Schema2MonsterRules.instance(game.battle, HOUND).actionUses().getFirst().available());
            assertTrue(result.healthChanges().isEmpty());
        }
    }

    @Test void frozenReachAndDamageControlBiteAndAnotherMonsterCanUseASingleComponent() {
        var supported = new Fixture(true, false);
        supported.start(List.of(new Deployment(A, new Position(2, 0), 0), new Deployment(B, new Position(4, 4), 0)),
                List.of(new MonsterDeployment(HOUND, HELL_HOUND, new Position(0, 0), 1)), 1, 2, 20);
        supported.apply(Command.monsterAttack(HOUND, A, BITE), 11, 1, 2, 1);
        var result = supported.lastAttack();
        assertEquals(10, result.monsterAttack().reachFeet());
        assertEquals(7, result.monsterAttack().hitBonus());
        assertEquals(9, result.damageAmount());
        assertEquals(2, supported.roster.get(A).character().hitPoints());

        var commoner = new Fixture(false, false);
        commoner.start(List.of(new Deployment(A, new Position(1, 0), 0), new Deployment(B, new Position(1, 1), 1)),
                List.of(new MonsterDeployment(OTHER, COMMONER, new Position(0, 0), 1)), 1, 2, 20);
        commoner.apply(Command.monsterAttack(OTHER, A, CLUB), 12, 3);
        var club = commoner.lastAttack().monsterAttack();
        assertEquals(CLUB, club.action());
        assertTrue(club.packTacticsAllies().isEmpty());
        assertEquals(List.of(new AttackDamageComponent(DamageType.BLUDGEONING, 1, 4, 0, List.of(3), 3, 3, List.of())),
                club.damageComponents());
    }

    @Test void overlappingResistanceQualifiersApplyOnceAndSurviveReplay() {
        var files = Schema2MonsterFixtures.packages();
        var extension = files.getLast();
        var changes = Schema2GrantFixtures.json(extension.get("changes.json")).getAsJsonArray();
        var statBlock = changes.asList().stream().map(row -> row.getAsJsonObject())
                .filter(row -> row.has("field") && row.get("field").getAsString().equals("stat_block")
                        && row.getAsJsonObject("target").get("rule_key").getAsString().equals(HELL_HOUND.ruleKey()))
                .findFirst().orElseThrow().getAsJsonObject("value");
        var defenses = statBlock.getAsJsonArray("damage_defenses");
        for (String qualifier : List.of("ALL", "NONMAGICAL_ATTACKS")) {
            var resistance = new JsonObject();
            resistance.addProperty("kind", "RESISTANCE");
            resistance.addProperty("damage_type", "PIERCING");
            resistance.addProperty("qualifier", qualifier);
            defenses.add(resistance);
        }
        extension.put("changes.json", changes.toString().getBytes(StandardCharsets.UTF_8));
        var game = new Fixture(material(files, false), false);
        game.start(List.of(new Deployment(A, new Position(7, 7), 0), new Deployment(B, new Position(8, 8), 0)),
                List.of(new MonsterDeployment(HOUND, HELL_HOUND, new Position(0, 0), 1),
                        new MonsterDeployment(OTHER, HELL_HOUND, new Position(1, 0), 0)), 1, 2, 20, 3);
        game.apply(Command.monsterAttack(HOUND, OTHER, BITE), 15, 4, 1, 2);
        game.readBack();
        var components = game.lastAttack().monsterAttack().damageComponents();
        assertEquals(7, components.getFirst().rawDamage());
        assertEquals(3, components.getFirst().effectiveDamage());
        assertEquals(List.of(new DefenseContribution(HELL_HOUND, "RESISTANCE")), components.getFirst().defenses());
        assertEquals(0, components.getLast().effectiveDamage());
        assertEquals(42, Schema2MonsterRules.instance(game.battle, OTHER).hitPoints());
    }

    @Test void monsterOpportunityUsesTheExactActionAndStopsOnlyTheUnexecutedMovement() {
        var game = new Fixture(false, false);
        game.start(List.of(new Deployment(A, new Position(0, 0), 0), new Deployment(B, new Position(4, 4), 0)),
                List.of(new MonsterDeployment(HOUND, HELL_HOUND, new Position(0, 1), 1)), 20, 2, 10);
        game.apply(Command.move(A, List.of(new Position(1, 0), new Position(2, 0))));
        assertEquals(new Position(1, 0), game.actor(A).position());
        assertEquals(10, game.actor(A).movementSpentHalfFeet());
        assertEquals(TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE, game.window().trigger().kind());
        assertEquals(BITE, game.opportunity().options().getFirst().monsterAttack());
        game.readBack();
        game.reject(game.opportunityAttack(FIRE_BREATH), "REACTION_OPTION_INVALID");
        assertTrue(game.actor(HOUND).reactionAvailable());
        game.apply(game.opportunityAttack(BITE), 18, 6, 3, 3);
        assertFalse(game.actor(HOUND).reactionAvailable());
        assertEquals(0, game.roster.get(A).character().hitPoints());
        game.apply(game.continueFlow(A));
        var completed = game.battle.flows().getLast();
        assertEquals(new Position(1, 0), game.actor(A).position());
        assertEquals(10, game.actor(A).movementSpentHalfFeet());
        assertEquals(MovementStatus.STOPPED, completed.movement().status());
        assertEquals(1, completed.movement().cursor());
        assertTrue(completed.attacks().getFirst().opportunityAttack());
        assertEquals(0, game.battle.triggerFacts().stream().filter(t -> t.kind() == TriggerKind.MONSTER_ACTION_COMPLETED).count());
    }

    @Test void attackCompletionAndMonsterActionCompletionEachResumeTheSameSettledAttack() {
        var game = new Fixture(false, false);
        game.start(List.of(new Deployment(A, new Position(1, 0), 0), new Deployment(B, new Position(0, 1), 0)),
                List.of(new MonsterDeployment(HOUND, HELL_HOUND, new Position(0, 0), 1)), 20, 15, 5);
        game.apply(game.readyAttack(A, TriggerKind.ATTACK_COMPLETED));
        game.apply(Command.action(Kind.END_TURN, A));
        game.apply(game.readyAttack(B, TriggerKind.MONSTER_ACTION_COMPLETED));
        game.apply(Command.action(Kind.END_TURN, B));
        game.apply(Command.monsterAttack(HOUND, A, BITE), 1);
        assertEquals(TriggerKind.ATTACK_COMPLETED, game.window().trigger().kind());
        assertEquals(A, game.opportunity().actorId());
        AttackAttempt original = game.battle.activeFlow().attacks().getFirst();
        game.apply(game.releaseReady(), 1);
        assertFalse(game.actor(A).reactionAvailable());
        game.apply(game.continueFlow(HOUND));
        assertEquals(TriggerKind.MONSTER_ACTION_COMPLETED, game.window().trigger().kind());
        assertEquals(B, game.opportunity().actorId());
        game.readBack();
        game.apply(game.pass());
        game.apply(game.continueFlow(HOUND));
        assertNull(game.battle.activeFlow());
        assertTrue(game.actor(B).reactionAvailable());
        assertFalse(game.actor(HOUND).actionAvailable());
        assertEquals(original, game.battle.flows().getLast().attacks().getFirst());
    }

    @Test void shieldCanTurnMonsterHitIntoMissAfterAnExactReadBackWithoutDamageDice() {
        var game = new Fixture(false, true);
        game.start(List.of(new Deployment(A, new Position(1, 0), 0), new Deployment(B, new Position(4, 4), 0)),
                List.of(new MonsterDeployment(HOUND, HELL_HOUND, new Position(0, 0), 1)), 1, 2, 20);
        long hp = game.roster.get(A).character().hitPoints();
        game.apply(Command.monsterAttack(HOUND, A, BITE), 8);
        var attack = game.battle.activeFlow().attacks().getFirst();
        assertEquals(AttackStatus.HIT_PENDING_DAMAGE, attack.status());
        assertTrue(attack.monsterAttack().damageComponents().isEmpty());
        game.readBack();
        game.apply(game.shield());
        game.apply(game.continueFlow(HOUND));
        var finalAttack = game.lastAttack();
        assertEquals(8, finalAttack.selectedD20());
        assertEquals(attack.armorClass() + 5, finalAttack.finalArmorClass());
        assertFalse(finalAttack.hit());
        assertTrue(finalAttack.monsterAttack().damageComponents().isEmpty());
        assertEquals(hp, game.roster.get(A).character().hitPoints());
        assertFalse(game.actor(HOUND).actionAvailable());
    }

    @Test void readyCureCanRestoreTheReactorForTheSecondCompletionWindow() {
        var material = material(Schema2MonsterFixtures.spellPackages(), false);
        var game = new Fixture();
        for (UUID actor : List.of(A, B)) {
            var identity = new Schema2PreparationIdentity(UUID.randomUUID(), UUID.randomUUID());
            var plan = fullPlan(material, identity, game.campaign, actor);
            var state = new Schema2CharacterProgressionRules().initial(material, plan, UUID.randomUUID(), fullAbilities(),
                    Schema2SpellFixtures.initialChoices());
            game.roster.put(actor, new Context(material, plan, state));
        }
        game.start(List.of(new Deployment(A, new Position(1, 0), 0), new Deployment(B, new Position(0, 1), 0)),
                List.of(new MonsterDeployment(HOUND, HELL_HOUND, new Position(0, 0), 1)), 20, 15, 5);
        game.apply(Command.ready(A, new ReadyInput(new ReadyTrigger(TriggerKind.MONSTER_ACTION_COMPLETED, HOUND, null),
                ReadyResponseKind.MOVE, null, null, List.of(new Position(1, 1)), null)));
        game.apply(Command.action(Kind.END_TURN, A));
        game.apply(Command.ready(B, new ReadyInput(new ReadyTrigger(TriggerKind.ATTACK_COMPLETED, HOUND, null),
                ReadyResponseKind.SPELL, null, null, List.of(),
                new CastInput(Schema2SpellFixtures.CASTING, Schema2SpellFixtures.CURE_WOUNDS, List.of(A), null))));
        game.apply(Command.action(Kind.END_TURN, B));
        game.apply(Command.monsterAttack(HOUND, A, BITE), 15, 6, 2, 2);
        assertEquals(0, game.roster.get(A).character().hitPoints());
        assertEquals(B, game.opportunity().actorId());
        game.readBack();
        var release = game.releaseReady();
        assertEquals(3, game.rules.preflight(UUID.randomUUID(), game.combat, game.campaign, game.roster,
                game.battle, release).requiredSuffixEvents());
        game.apply(release, 4);
        assertTrue(game.roster.get(A).character().hitPoints() > 0);
        game.apply(game.continueFlow(HOUND));
        assertEquals(TriggerKind.MONSTER_ACTION_COMPLETED, game.window().trigger().kind());
        assertEquals(A, game.opportunity().actorId());
        game.apply(game.releaseReady());
        game.apply(game.continueFlow(HOUND));
        assertEquals(new Position(1, 1), game.actor(A).position());
        assertNull(game.battle.activeFlow());
    }

    private static final class Fixture {
        private final UUID campaign = UUID.randomUUID(), combat = UUID.randomUUID();
        private final Schema2CombatRules rules = new Schema2CombatRules();
        private final Map<UUID, Context> roster = new HashMap<>();
        private Battle battle;

        Fixture() {}

        Fixture(boolean variant, boolean wizard) {
            this(material(variant, wizard), wizard);
        }

        Fixture(ProductionSchema2Projection material, boolean wizard) {
            roster.put(A, wizard ? wizard(material) : context(material,campaign,A));
            roster.put(B, context(material,campaign,B));
        }

        void start(List<Deployment> characters, List<MonsterDeployment> monsters, int... initiative) {
            var order = java.util.stream.Stream.concat(characters.stream().map(Deployment::characterId),
                    monsters.stream().map(MonsterDeployment::monsterId)).toList();
            apply(Command.start(characters, monsters, order), initiative);
        }

        private Context wizard(ProductionSchema2Projection material) {
            var identity = new Schema2PreparationIdentity(UUID.randomUUID(), UUID.randomUUID());
            var sources = new Schema2InitialProficiencyRules.Sources(new RuleIdentity(BASE,"character.race","race.human"),null,
                    new RuleIdentity(BASE,"character.background","background.guard"),Schema2WizardFixtures.WIZARD);
            var preparation = new Schema2InitialCharacterPlanService().prepareMaterial(identity, Schema2RulesBinding.from(material),
                    material, new Schema2InitialProficiencyRules.Request(sources,Map.of()));
            var initial = new Schema2InitialCharacterPlanService.InitialPlan(campaign, A, preparation);
            var state = new Schema2CharacterProgressionRules().initial(material,initial,UUID.randomUUID(),fullAbilities(),GrowthChoices.EMPTY);
            var book = new SpellbookInput(Schema2WizardFixtures.CASTING, Schema2WizardFixtures.INITIAL_BOOK, List.of(Schema2WizardFixtures.SHIELD));
            state = new Schema2WizardSpellbookRules().apply(material,state,Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_INITIALIZED", book));
            return new Context(material,initial,state);
        }

        Resolution apply(Command command, int... dice) {
            UUID operation = UUID.randomUUID();
            var before = battle;
            var prepared = rules.preflight(operation, combat, campaign, roster, before, command);
            var cursor = new AtomicInteger();
            var result = rules.resolve(prepared,sides -> {
                int index = cursor.getAndIncrement();
                assertTrue(index < dice.length, "Unexpected d" + sides);
                return dice[index];
            });
            assertEquals(dice.length,cursor.get());
            rules.verify(operation,combat,campaign,roster,before,command,result);
            for (var change : result.healthChanges()) roster.put(change.characterId(), withState(roster.get(change.characterId()),change.after()));
            battle=result.after();
            return result;
        }

        void reject(Command command, String code) {
            assertEquals(code, assertThrows(IllegalArgumentException.class,
                    () -> rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,command)).getMessage());
        }

        Combatant actor(UUID id) { return Schema2CombatRules.combatant(battle,id); }
        AttackAttempt lastAttack() { return battle.flows().getLast().attacks().getFirst(); }
        Window window() { return battle.activeFlow().windows().getLast(); }
        Opportunity opportunity() { return window().opportunities().stream().filter(o -> o.status() == OpportunityStatus.PENDING).findFirst().orElseThrow(); }
        Command opportunityAttack(EntryIdentity action) {
            return Command.monsterOpportunityAttack(HOUND,action,battle.activeFlow().flowKey(),battle.activeFlow().leafFrameKey(),window().windowKey(),opportunity().opportunityKey());
        }
        Command continueFlow(UUID actor) {
            return new Command(Kind.CONTINUE_FLOW, actor, null, null, List.of(), List.of(), List.of(), battle.activeFlow().flowKey(),
                    battle.activeFlow().leafFrameKey(), window().windowKey(), null);
        }
        Command readyAttack(UUID actor, TriggerKind trigger) {
            UUID item = roster.get(actor).character().equipment().getFirst().itemId();
            return Command.ready(actor, new ReadyInput(new ReadyTrigger(trigger,HOUND,null),ReadyResponseKind.ATTACK,HOUND,item,List.of(),null));
        }
        Command releaseReady() {
            var opportunity=opportunity();
            return Command.releaseReady(opportunity.actorId(),opportunity.options().getFirst().readyKey(),battle.activeFlow().flowKey(),
                    battle.activeFlow().leafFrameKey(),window().windowKey(),opportunity.opportunityKey());
        }
        Command pass() {
            var opportunity=opportunity();
            return Command.reaction(Kind.PASS_REACTION,opportunity.actorId(),null,battle.activeFlow().flowKey(),
                    battle.activeFlow().leafFrameKey(),window().windowKey(),opportunity.opportunityKey());
        }
        Command shield() {
            var opportunity=opportunity();
            var cast = new CastInput(Schema2WizardFixtures.CASTING,Schema2WizardFixtures.SHIELD,List.of(A),null);
            return Command.reactionSpell(Kind.DECLARE_SHIELD,A,cast,battle.activeFlow().flowKey(),battle.activeFlow().leafFrameKey(),
                    window().windowKey(),opportunity.opportunityKey());
        }
        void readBack() {
            var read = CampaignDomainCodec.decode(CampaignDomainCodec.encode(battle), Battle.class);
            assertEquals(battle, read);
            battle = read;
        }
    }

    private static ProductionSchema2Projection material(boolean variant, boolean wizard) {
        var files = wizard ? Schema2MonsterFixtures.appendTo(Schema2WizardFixtures.packages(), false) : Schema2MonsterFixtures.packages();
        return material(files, variant);
    }

    private static ProductionSchema2Projection material(List<Map<String,byte[]>> files, boolean variant) {
        var monsterBase = files.stream().filter(entries -> entries.containsKey("actions.json")).findFirst().orElseThrow();
        var actions = Schema2GrantFixtures.json(monsterBase.get("actions.json")).getAsJsonArray();
        for (var row : actions) {
            var entry = row.getAsJsonObject();
            String key = entry.getAsJsonObject("identity").get("entry_key").getAsString();
            if (key.equals("bite")) {
                attackProfile(entry,variant ? """
                        {"hit_bonus":7,"reach_feet":10,"damage":[{"damage_type":"PIERCING","dice_count":2,"dice_sides":8,"modifier":4},
                        {"damage_type":"FIRE","dice_count":1,"dice_sides":6,"modifier":1}]}""" : """
                        {"hit_bonus":5,"reach_feet":5,"damage":[{"damage_type":"PIERCING","dice_count":1,"dice_sides":8,"modifier":3},
                        {"damage_type":"FIRE","dice_count":2,"dice_sides":6,"modifier":0}]}""");
            } else if (key.equals("club")) {
                attackProfile(entry, """
                        {"hit_bonus":2,"reach_feet":5,"damage":[{"damage_type":"BLUDGEONING","dice_count":1,"dice_sides":4,"modifier":0}]}""");
            }
        }
        monsterBase.put("actions.json",actions.toString().getBytes(StandardCharsets.UTF_8));
        var extension = files.getLast();
        var changes = Schema2GrantFixtures.json(extension.get("changes.json")).getAsJsonArray();
        for (int i=changes.size()-1;i>=0;i--) {
            var change = changes.get(i).getAsJsonObject();
            if (change.get("op").getAsString().equals("set_enabled")
                    && change.getAsJsonObject("target").get("entry_key").getAsString().equals("bite")) changes.remove(i);
        }
        extension.put("changes.json",changes.toString().getBytes(StandardCharsets.UTF_8));
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
    }

    private static void attackProfile(JsonObject entry, String attackProfile) {
        var profile = entry.getAsJsonObject("action_profile");
        profile.addProperty("mode","MELEE_ATTACK_V1");
        profile.addProperty("mechanism",MONSTER_MELEE_ATTACK_MECHANISM);
        profile.add("attack_profile",JsonParser.parseString(attackProfile));
    }
}
