package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.storage.CampaignExtraAttackIntegrationTest.Fixture;
import com.dndtool.service.storage.CampaignParticipationData.Actor;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignSneakAttackIntegrationTest {
    @TempDir Path temporary;

    @Test void pendingRapierHitReopensForItsControllerAndCommitsOneDamagePacketEvenOnRetry() throws Exception {
        try (var game = new Fixture(temporary)) {
            CampaignWolfBiteIntegrationTest.createWithDeliveredRules(game.service, "细剑与偷袭");
            UUID rogue = rogue(game);
            UUID target = game.character("卫士", "NPC");
            UUID ally = game.character("援手", "PC");
            game.advance(target, 3);
            UUID rapier = item(game, rogue, "item.rapier", "MAIN_HAND");
            UUID owner = game.player(rogue, target);
            UUID observer = game.player(ally);
            game.startBattle("巷口", List.of(at(rogue, 1, 1, 0), at(target, 2, 1, 1), at(ally, 2, 2, 0)), 20, 11, 1);
            game.combat(Command.action(Kind.BEGIN_ATTACK, rogue));
            long hp = game.card(target).hitPoints();
            game.combat(Command.attack(rogue, target, rapier), 15);
            var first = latest(game);
            assertEquals(ally, first.sneakAttack().nearbyEnemyId());
            assertEquals(List.of(), first.damageDice());
            assertEquals(List.of(), first.sneakAttack().damageDice());
            assertEquals(SneakAttackStatus.PENDING, first.sneakAttack().status());
            assertEquals(game.battle().turnNumber(), first.sneakAttack().turnNumber());
            assertEquals(hp, game.card(target).hitPoints());
            assertEquals(List.of(new Roll("ATTACK_D20", rogue, 20, 15)), lastResolution(game).rolls());

            var use = decision(game, Kind.USE_SNEAK_ATTACK, rogue);
            var atHit = game.access();
            var input = game.combatInput(use);
            var controller = Actor.player(owner, rogue);
            assertCode("PLAYER_CONTROL_REQUIRED", () -> game.submit(id(), atHit, input, Actor.player(observer, rogue)));
            var forged = input.deepCopy();
            forged.getAsJsonObject("command").addProperty("damageAmount", "40");
            assertCode("INVALID_FILE", () -> game.submit(id(), atHit, forged, controller));
            assertEquals(atHit, game.access());

            String manual = game.service.save(id(), atHit.epoch(), atHit.revision()).fileKey();
            byte[] saved = Files.readAllBytes(game.service.savedFile(manual));
            assertEquals(atHit.snapshot(), new CampaignCodec().decode(saved).snapshot());
            String ended = game.end();
            rejectChangedWitness(game, saved, rogue);
            game.service.open(ended);
            var reopened = game.access();
            assertEquals(atHit.snapshot(), reopened.snapshot());
            assertEquals(first, latest(game));
            assertCode("STALE_EPOCH", () -> game.submit(id(), atHit, input, controller));
            var stale = input.deepCopy();
            stale.addProperty("combatVersion", Long.toString(game.battle().version() - 1));
            assertCode("STALE_COMBAT", () -> game.submit(id(), reopened, stale, controller));
            String request = id();
            game.failure = CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE;
            game.dice.addAll(List.of(2, 3));
            assertThrows(IllegalStateException.class, () -> game.submit(request, reopened, input, controller));
            assertTrue(game.dice.isEmpty());
            assertEquals(reopened, game.access());
            game.dice.addAll(List.of(2, 3));
            var accepted = game.submit(request, reopened, input, controller);
            var settled = latest(game);
            assertTrue(game.dice.isEmpty());
            assertEquals(15, settled.firstD20());
            assertEquals(List.of(2), settled.damageDice());
            assertEquals(List.of(3), settled.sneakAttack().damageDice());
            assertEquals(SneakAttackStatus.USED, settled.sneakAttack().status());
            assertEquals(5 + settled.abilityModifier(), settled.damageAmount());
            assertEquals(hp - settled.damageAmount(), game.card(target).hitPoints());
            assertEquals(1, lastResolution(game).healthChanges().size());
            assertEquals(List.of(new Roll("DAMAGE", rogue, 8, 2), new Roll("SNEAK_ATTACK_DAMAGE", rogue, 6, 3)),
                    lastResolution(game).rolls());
            assertEquals(controller, game.state().roots().getLast().actor());
            var after = game.access();
            var replay = game.submit(request, reopened, input, controller);
            assertTrue(replay.lastCommand().replayed());
            assertEquals(accepted.lastCommand().acceptedRevision(), replay.lastCommand().acceptedRevision());
            assertEquals(after, game.access());
            assertCode("IDEMPOTENCY_CONFLICT", () -> {
                var changed = input.deepCopy();
                changed.getAsJsonObject("command").addProperty("kind", Kind.DECLINE_SNEAK_ATTACK.name());
                game.submit(request, reopened, changed, controller);
            });

            game.combat(Command.action(Kind.END_TURN, rogue));
            game.combat(Command.move(target, List.of(new Position(3, 1))));
            game.react(Kind.DECLARE_OPPORTUNITY_ATTACK, rogue, rapier, 15);
            assertFalse(game.actor(rogue).reactionAvailable());
            assertTrue(latest(game).opportunityAttack());
            assertEquals(first.sneakAttack().turnNumber() + 1, latest(game).sneakAttack().turnNumber());
            game.combat(decision(game, Kind.USE_SNEAK_ATTACK, rogue), 1, 2);
            assertEquals(hp - settled.damageAmount() - latest(game).damageAmount(), game.card(target).hitPoints());
            game.continueMovement(target);
            assertEquals(new Position(3, 1), game.actor(target).position());
        }
    }

    enum FirstAttack { USE, DECLINE, MISS }

    @ParameterizedTest @EnumSource(FirstAttack.class)
    void readiedEnemyMovementCreatesASecondEligibleHitOnTheSameActualTurn(FirstAttack first) throws Exception {
        try (var game = new Fixture(temporary)) {
            CampaignWolfBiteIntegrationTest.createWithDeliveredRules(game.service, "同回合的两次命中机会");
            UUID target = game.character("撤步的卫士", "NPC");
            UUID rogue = rogue(game);
            UUID ally = game.character("援手", "NPC");
            game.advance(target, 3);
            UUID rapier = item(game, rogue, "item.rapier", "MAIN_HAND");
            game.startBattle("预备移动", List.of(at(target, 2, 1, 1), at(rogue, 1, 1, 0), at(ally, 2, 2, 0)), 20, 11, 1);
            var trigger = new ReadyTrigger(TriggerKind.ATTACK_COMPLETED, rogue, null);
            game.combat(Command.ready(target, new ReadyInput(trigger, ReadyResponseKind.MOVE, null, null,
                    List.of(new Position(3, 1)), null)));
            var ready = game.battle().readyPreparations().getFirst();
            game.combat(Command.action(Kind.END_TURN, target));
            long turn = game.battle().turnNumber();
            game.combat(Command.action(Kind.BEGIN_ATTACK, rogue));
            game.combat(Command.attack(rogue, target, rapier), first == FirstAttack.MISS ? 1 : 15);
            if (first == FirstAttack.USE) {
                game.combat(decision(game, Kind.USE_SNEAK_ATTACK, rogue), 1, 2);
            } else if (first == FirstAttack.DECLINE) {
                game.combat(decision(game, Kind.DECLINE_SNEAK_ATTACK, rogue), 1);
                assertEquals(SneakAttackStatus.DECLINED, latest(game).sneakAttack().status());
            } else {
                assertFalse(latest(game).hit());
                assertNull(latest(game).sneakAttack());
            }
            Flow flow = game.battle().activeFlow();
            Window readyWindow = flow.windows().getLast();
            var chance = readyWindow.opportunities().getFirst();
            assertEquals(target, chance.actorId());
            game.combat(Command.releaseReady(target, ready.readyKey(), flow.flowKey(), flow.leafFrameKey(),
                    readyWindow.windowKey(), chance.opportunityKey()));
            if (first == FirstAttack.USE) {
                game.react(Kind.DECLARE_OPPORTUNITY_ATTACK, rogue, rapier, 16, 2);
                assertNull(latest(game).sneakAttack());
                assertEquals(List.of(2), latest(game).damageDice());
            } else {
                game.react(Kind.DECLARE_OPPORTUNITY_ATTACK, rogue, rapier, 16);
                assertEquals(turn, latest(game).sneakAttack().turnNumber());
                game.combat(decision(game, Kind.USE_SNEAK_ATTACK, rogue), 2, 3);
                assertEquals(List.of(3), latest(game).sneakAttack().damageDice());
            }
            assertEquals(turn, game.battle().turnNumber());
            assertTrue(latest(game).opportunityAttack());
        }
    }

    @Test void shortbowWaitsForFinalShieldResultAndDoublesBothDiceOnACriticalHit() throws Exception {
        try (var game = new Fixture(temporary)) {
            CampaignWolfBiteIntegrationTest.createWithDeliveredRules(game.service, "护盾与远程偷袭");
            UUID rogue = rogue(game);
            game.characterCommand(id(), rogue, new Schema2CharacterRuntime.Command("CHARACTER_ADVANCED", null, 0,
                    null, "FIXED_AVERAGE", game.rule("character.class", "class.rogue"), GrowthChoices.EMPTY));
            UUID wizard = wizard(game);
            var casting = game.rule("character.wizard_spellcasting", "spellcasting.wizard");
            var rules = game.state().material().restore();
            game.characterCommand(id(), wizard, Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_INITIALIZED",
                    new SpellbookInput(casting, rules.initialWizardBook(), rules.initialWizardPrepared())));
            UUID ally = game.character("同伴", "NPC");
            UUID bow = item(game, rogue, "item.shortbow", "BOTH_HANDS");
            item(game, rogue, "item.arrow", null);
            item(game, rogue, "item.arrow", null);
            game.startBattle("射界", List.of(at(rogue, 1, 1, 0), at(wizard, 5, 1, 1), at(ally, 5, 2, 0)), 20, 11, 1);
            game.combat(Command.action(Kind.BEGIN_ATTACK, rogue));
            int ac = new com.dndtool.service.Schema2CharacterEquipmentRules().derive(rules.projection(), game.card(wizard)).armorClass();
            int dex = Math.floorDiv(game.card(rogue).abilities().get("ability.dexterity") - 10, 2);
            int needed = ac - dex - game.card(rogue).proficiencyBonus();
            assertTrue(needed > 1 && needed < 20);
            game.combat(Command.attack(rogue, wizard, bow), needed);
            assertNull(latest(game).sneakAttack());
            assertTrue(latest(game).initialHit());
            Flow flow = game.battle().activeFlow();
            Window window = flow.windows().getLast();
            game.combat(Command.reactionSpell(Kind.DECLARE_SHIELD, wizard,
                    new CastInput(casting, game.rule("adventure.spell", "spell.shield"), List.of(wizard), null),
                    flow.flowKey(), flow.leafFrameKey(), window.windowKey(), window.opportunities().getFirst().opportunityKey()));
            flow = game.battle().activeFlow();
            game.combat(Command.continuation(rogue, flow.flowKey(), flow.leafFrameKey(), window.windowKey()));
            assertFalse(latest(game).hit());
            assertNull(latest(game).sneakAttack());
            assertNull(game.battle().activeFlow());

            game.combat(Command.action(Kind.END_TURN, rogue));
            game.combat(Command.action(Kind.END_TURN, wizard));
            game.combat(Command.action(Kind.END_TURN, ally));
            game.combat(Command.action(Kind.BEGIN_ATTACK, rogue));
            game.combat(Command.attack(rogue, wizard, bow), 20);
            game.react(Kind.PASS_REACTION, wizard, null);
            flow = game.battle().activeFlow();
            game.combat(Command.continuation(rogue, flow.flowKey(), flow.leafFrameKey(), flow.windows().getLast().windowKey()));
            assertTrue(latest(game).critical());
            assertEquals(SneakAttackStatus.PENDING, latest(game).sneakAttack().status());
            assertEquals(2, game.card(rogue).level());
            assertTrue(game.card(rogue).equipment().stream().noneMatch(e -> e.item().ruleKey().equals("item.arrow")));
            game.combat(decision(game, Kind.USE_SNEAK_ATTACK, rogue), 1, 2, 1, 1);
            var critical = latest(game);
            assertEquals(List.of(1, 2), critical.damageDice());
            assertEquals(List.of(1, 1), critical.sneakAttack().damageDice());
            assertEquals(5 + dex, critical.damageAmount());
            assertEquals(1, lastResolution(game).healthChanges().size());
            var after = game.access().snapshot();
            game.service.open(game.end());
            assertEquals(after, game.access().snapshot());
        }
    }

    private static UUID rogue(Fixture game) {
        return CampaignRogueCombatIntegrationTest.rogue(game, "游荡者", "PC", "stealth", "acrobatics");
    }

    private static UUID wizard(Fixture game) {
        var input = new JsonObject();
        input.addProperty("name", "防护法师");
        input.addProperty("kind", "NPC");
        input.addProperty("classKey", "wizard");
        input.addProperty("backgroundKey", "acolyte");
        return UUID.fromString(CampaignSpaceFixtures.command(game.service, "character-create", input).lastCommand().subjectId());
    }

    private static UUID item(Fixture game, UUID actor, String key, String slot) {
        UUID instance = UUID.randomUUID();
        game.equipment(instance.toString(), actor, "ITEM_ACQUIRED",
                new EquipmentInput(instance, game.rule("equipment.item", key), "CARRIED", "取得物品"));
        if (slot != null) game.equipment(id(), actor, "ITEM_EQUIPPED", new EquipmentInput(instance, null, slot, "就绪武器"));
        return instance;
    }

    private static void rejectChangedWitness(Fixture game, byte[] complete, UUID attacker) {
        var file = CampaignJson.read(complete);
        var battle = file.getAsJsonObject("domains").getAsJsonArray("combats").get(0).getAsJsonObject();
        var event = battle.getAsJsonArray("events").asList().getLast().getAsJsonObject();
        for (var state : List.of(battle.getAsJsonObject("current"), event.getAsJsonObject("resolution").getAsJsonObject("after"))) {
            var flow = state.getAsJsonArray("flows").asList().getLast().getAsJsonObject();
            var attempt = flow.getAsJsonArray("attacks").asList().getLast().getAsJsonObject();
            attempt.getAsJsonObject("sneakAttack").addProperty("nearbyEnemyId", attacker.toString());
        }
        assertCode("INVALID_FILE", () -> game.service.importFile(CampaignExtraAttackIntegrationTest.resign(file)));
    }

    private static Command decision(Fixture game, Kind kind, UUID rogue) {
        var flow = game.battle().activeFlow();
        return Command.sneakAttack(kind, rogue, flow.flowKey(), flow.leafFrameKey());
    }

    private static AttackAttempt latest(Fixture game) {
        return game.battle().flows().getLast().attacks().getLast();
    }

    private static Resolution lastResolution(Fixture game) {
        return CampaignSpaceCommands.currentBattle(game.state()).events().getLast().resolution();
    }

    private static Deployment at(UUID subject, int x, int y, int side) {
        return new Deployment(subject, new Position(x, y), side);
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static void assertCode(String code, Executable command) {
        assertEquals(code, assertThrows(StorageException.class, command).code());
    }
}
