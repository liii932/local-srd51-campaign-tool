package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CombatHistory;
import com.dndtool.service.storage.CampaignExtraAttackIntegrationTest.Fixture;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignRogueCombatIntegrationTest {
    private static final List<String> SKILLS = List.of("acrobatics", "athletics", "perception", "stealth");
    @TempDir Path temporary;

    @Test void cunningActionAndExpertiseUseTheOriginalBudgetsChecksAndHistory() throws Exception {
        try (var game = new Fixture(temporary.resolve("rogue-combat"))) {
            CampaignWolfBiteIntegrationTest.createWithDeliveredRules(game.service, "游荡者战斗验收");
            UUID rogue = rogue(game, "斥候", "PC", "acrobatics", "stealth");
            UUID scout = rogue(game, "守望者", "NPC", "perception", "athletics");
            UUID guard = game.character("守卫", "NPC");
            var growth = new Schema2CharacterRuntime.Command("CHARACTER_ADVANCED", null, 0,
                    null, "FIXED_AVERAGE", game.rule("character.class", "class.rogue"), GrowthChoices.EMPTY);
            var grow = new JsonObject();
            grow.addProperty("characterId", rogue.toString());
            grow.addProperty("characterVersion", Long.toString(game.card(rogue).version()));
            grow.add("command", CampaignDomainCodec.toJson(growth));
            CampaignSpaceFixtures.command(game.service, "character-command", grow);
            assertEquals(2, game.card(rogue).level());

            Position cover = new Position(1, 1);
            game.startBattle("藏身角落", List.of(new Deployment(rogue, cover, 0),
                    new Deployment(scout, new Position(2, 1), 1),
                    new Deployment(guard, new Position(1, 2), 1)), 20, 10, 1);
            game.combat(Command.setObservationCell(new ObservationCellInput(cover, true, false, Cover.NONE)));
            var dash = Command.action(Kind.CUNNING_DASH, rogue);
            var disengage = Command.action(Kind.CUNNING_DISENGAGE, rogue);
            var hide = Command.action(Kind.CUNNING_HIDE, rogue);
            var start = game.actor(rogue);
            var card = game.card(rogue);
            game.combat(dash);
            var paid = game.actor(rogue);
            assertEquals(start.actionsRemaining(), paid.actionsRemaining());
            assertEquals(0, paid.bonusActionsRemaining());
            assertEquals(start.reactionAvailable(), paid.reactionAvailable());
            assertEquals(start.position(), paid.position());
            assertEquals(start.movementSpentHalfFeet(), paid.movementSpentHalfFeet());
            assertEquals(card, game.card(rogue));
            assertEquals(30, paid.movementBonus());
            assertTrue(last(game).resolution().rolls().isEmpty());
            game.reject(disengage, "S2_COMBAT_BONUS_ACTION_UNAVAILABLE");
            game.reject(hide, "S2_COMBAT_BONUS_ACTION_UNAVAILABLE");
            game.combat(Command.action(Kind.DASH, rogue));
            assertEquals(60, game.actor(rogue).movementBonus());
            assertEquals(0, game.actor(rogue).actionsRemaining());

            game.combat(Command.action(Kind.END_TURN, rogue));
            game.reject(dash, "S2_COMBAT_NOT_CURRENT_TURN");
            game.reject(Command.action(Kind.CUNNING_DASH, scout), "S2_CUNNING_ACTION_UNAVAILABLE");
            game.combat(Command.action(Kind.END_TURN, scout));
            game.combat(Command.action(Kind.END_TURN, guard));
            assertEquals(1, game.actor(rogue).bonusActionsRemaining());
            var beforeHide = game.access();
            var input = game.combatInput(hide);
            String request = UUID.randomUUID().toString();
            game.failure = CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE;
            game.dice.add(12);
            assertThrows(IllegalStateException.class, () -> game.submit(request, beforeHide, input, null));
            assertTrue(game.dice.isEmpty());
            assertEquals(beforeHide, game.access());
            game.dice.add(12);
            var accepted = game.submit(request, beforeHide, input, null);
            assertTrue(game.dice.isEmpty());
            var replay = game.submit(request, beforeHide, input, null);
            assertTrue(replay.lastCommand().replayed());
            assertEquals(accepted.lastCommand().acceptedRevision(), replay.lastCommand().acceptedRevision());
            var hidden = game.battle().observation().checks().getLast();
            assertEquals(Kind.CUNNING_HIDE, hidden.kind());
            assertFalse(hidden.nimbleEscape());
            assertEquals(4, hidden.roll().skillBonus());
            assertEquals(12, hidden.roll().firstD20());
            assertEquals(10 + abilityModifier(game, scout, "ability.wisdom") + 4, score(hidden, scout));
            assertEquals(10 + abilityModifier(game, guard, "ability.wisdom") + 2, score(hidden, guard));
            assertEquals(List.of(new Roll("OBSERVATION_STEALTH", rogue, 20, 12)), last(game).resolution().rolls());
            assertEquals(1, game.actor(rogue).actionsRemaining());
            assertEquals(0, game.actor(rogue).bonusActionsRemaining());
            assertTrue(hidden.contests().stream().allMatch(ObservationContest::hiddenAfter));

            var beforeSave = game.access();
            var file = game.end();
            var complete = Files.readAllBytes(game.service.savedFile(file));
            assertEquals(beforeSave.snapshot(), new CampaignCodec().decode(complete).snapshot());
            rejectAlteredExpertise(game, complete);
            game.service.open(file);
            assertEquals(beforeSave.snapshot(), game.access().snapshot());
            game.reject(dash, "S2_COMBAT_BONUS_ACTION_UNAVAILABLE");
            game.combat(Command.action(Kind.END_TURN, rogue));
            game.combat(Command.action(Kind.SEARCH, scout), 20);
            var found = game.battle().observation().checks().getLast();
            assertEquals(4, found.roll().skillBonus());
            assertEquals(hidden.roll().total(), score(found, rogue));
            assertFalse(found.contests().getFirst().hiddenAfter());

            game.combat(Command.action(Kind.END_TURN, scout));
            game.combat(Command.action(Kind.END_TURN, guard));
            game.combat(disengage);
            assertTrue(game.actor(rogue).disengaged());
            assertTrue(game.actor(rogue).actionAvailable());
            assertTrue(last(game).resolution().rolls().isEmpty());
            game.combat(Command.action(Kind.END_TURN, rogue));
            game.combat(Command.action(Kind.BEGIN_ATTACK, scout));
            var sequence = game.actor(scout).sequence();
            game.combat(Command.melee(Kind.GRAPPLE, scout, rogue, null, null,
                    new MeleeInput(sequence.sequenceKey(), 0, null, HandSlot.MAIN_HAND, null, null)));
            game.reject(hide, "ACTION_FLOW_BUSY");
            var pending = game.battle().activeFlow();
            game.combat(Command.melee(Kind.CHOOSE_MELEE_DEFENSE, rogue, null, pending.flowKey(), pending.leafFrameKey(),
                    new MeleeInput(null, null, null, null, ContestSkill.ACROBATICS, null)), 20, 2);
            var contest = game.battle().meleeControl().contests().getLast();
            assertEquals(4, contest.responder().proficiencyBonus());
            assertEquals(2, contest.responder().firstD20());
        }
    }

    private static void rejectAlteredExpertise(Fixture game, byte[] complete) {
        var changed = CampaignJson.read(complete);
        var combat = changed.getAsJsonObject("domains").getAsJsonArray("combats").get(0).getAsJsonObject();
        var event = combat.getAsJsonArray("events").asList().getLast().getAsJsonObject();
        for (var state : List.of(combat.getAsJsonObject("current"),
                event.getAsJsonObject("resolution").getAsJsonObject("after"))) {
            var check = state.getAsJsonObject("observation").getAsJsonArray("checks").asList().getLast().getAsJsonObject();
            var roll = check.getAsJsonObject("roll");
            roll.addProperty("skillBonus", "2");
            roll.addProperty("total", Integer.toString(roll.get("total").getAsInt() - 2));
        }
        var integrity = changed.remove("integrity").getAsJsonObject();
        changed.addProperty("fileKey", UUID.randomUUID().toString());
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(changed)));
        changed.add("integrity", integrity);
        assertEquals("INVALID_FILE", assertThrows(StorageException.class,
                () -> game.service.importFile(CampaignJson.canonical(changed))).code());
    }

    private static UUID rogue(Fixture game, String name, String kind, String... expertise) {
        JsonObject input = new JsonObject();
        input.addProperty("name", name);
        input.addProperty("kind", kind);
        input.addProperty("classKey", "rogue");
        input.addProperty("backgroundKey", "acolyte");
        input.add("skillKeys", skills(SKILLS));
        input.add("expertiseKeys", skills(List.of(expertise)));
        return UUID.fromString(CampaignSpaceFixtures.command(game.service, "character-create", input).lastCommand().subjectId());
    }

    private static JsonArray skills(List<String> names) {
        JsonArray result = new JsonArray();
        names.forEach(name -> result.add("proficiency.skill." + name));
        return result;
    }

    private static int score(ObservationCheck check, UUID target) {
        return check.contests().stream().filter(row -> row.counterpartId().equals(target)).findFirst().orElseThrow().opposingScore();
    }

    private static int abilityModifier(Fixture game, UUID subject, String ability) {
        return Math.floorDiv(game.card(subject).abilities().get(ability) - 10, 2);
    }

    private static Schema2CombatHistory.Event last(Fixture game) {
        return CampaignSpaceCommands.currentBattle(game.state()).events().getLast();
    }
}
