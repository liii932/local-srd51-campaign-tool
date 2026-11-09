package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.FeatureState;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignImprovedCriticalIntegrationTest {
    private static final String FEATURE = "feature.champion_improved_critical";
    private static final String STRENGTH = "ability.strength";
    private static final String WIZARD_SLOT = "resource.wizard.slot1";
    @TempDir Path temporary;

    @Test void onlyTheSelectedWeaponDieUsesTheFeatureAcquiredAtChampionLevelThree() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("growth"))) {
            fixture.create();
            UUID fighter = fixture.character("战士", "PC", "fighter");
            UUID guard = fixture.character("守卫", "NPC", "fighter");
            UUID dagger = fixture.dagger(fighter);
            fixture.advanceFighter(fighter, 2);
            assertTrue(fixture.improvedCritical(fighter).isEmpty());
            fixture.startBattle(fighter, guard);
            assertStrike(fixture.strike(fighter, guard, dagger, 19, 1), 19, true, false, List.of(1), 3);
            fixture.leaveBattle(fighter, guard);

            fixture.advanceFighter(fighter, 3);
            assertEquals(List.of(new FeatureState(fixture.rule("character.feature", FEATURE),
                    fixture.rule("character.subclass", "subclass.champion"), 3)), fixture.improvedCritical(fighter));
            PlayerSpaceFixture.placeAtWorld(fixture.service, fighter, guard);
            var check = new CheckInput(fixture.rule("adventure.check", "check.srd2014"), "ABILITY",
                    STRENGTH, null, 25, false, false, "主持人确认力量检定");
            fixture.withDice(() -> fixture.characterCommand(id(), fighter,
                    new Schema2CharacterRuntime.Command("CHECK_RESOLVED", null, 0,
                            null, null, null, GrowthChoices.EMPTY, null, check)), 19);
            var checkResult = fixture.history(fighter).events().getLast().checkResult();
            assertEquals(19, checkResult.selectedD20());
            assertEquals(21, checkResult.total());
            assertFalse(checkResult.success());

            fixture.startBattle(fighter, guard);
            assertStrike(fixture.strike(fighter, guard, dagger, 18, 1), 18, true, false, List.of(1), 3);
            fixture.combat(Command.action(Kind.END_TURN, fighter));
            fixture.combat(Command.action(Kind.DODGE, guard));
            fixture.combat(Command.action(Kind.END_TURN, guard));
            var discarded = fixture.strike(fighter, guard, dagger, 19, 2);
            assertEquals(19, discarded.firstD20());
            assertEquals(2, discarded.secondD20());
            assertTrue(discarded.disadvantage());
            assertStrike(discarded, 2, false, false, List.of(), 0);

            fixture.nextRound(fighter, guard);
            assertStrike(fixture.strike(fighter, guard, dagger, 20, 1, 1), 20, true, true, List.of(1, 1), 4);
            fixture.nextRound(fighter, guard);
            assertStrike(fixture.strike(fighter, guard, dagger, 1), 1, false, false, List.of(), 0);
        }
    }

    @Test void pendingCriticalSurvivesCompleteFilesAndShieldThenDamageCommitsExactlyOnce() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("shield"))) {
            fixture.create();
            UUID fighter = fixture.character("冠军", "PC", "fighter");
            UUID wizard = fixture.character("法师", "NPC", "wizard");
            UUID dagger = fixture.dagger(fighter);
            fixture.advanceFighter(fighter, 3);
            var casting = fixture.rule("character.wizard_spellcasting", "spellcasting.wizard");
            var shield = fixture.rule("adventure.spell", "spell.shield");
            fixture.characterCommand(id(), wizard, Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_INITIALIZED",
                    new SpellbookInput(casting, fixture.rules.initialWizardBook(), fixture.rules.initialWizardPrepared())));
            fixture.startBattle(fighter, wizard);
            long hitPoints = fixture.card(wizard).hitPoints(), slots = fixture.slots(wizard);
            fixture.combat(Command.action(Kind.BEGIN_ATTACK, fighter));
            var begin = Command.attack(fighter, wizard, dagger);
            var before = fixture.access();
            var forged = fixture.combatInput(begin);
            forged.getAsJsonObject("command").addProperty("critical", true);
            assertCode("INVALID_FILE", () -> fixture.submit(id(), before, forged));
            assertEquals(before, fixture.access());
            fixture.combat(begin, 19);
            var pending = fixture.latestAttack();
            assertEquals(AttackStatus.HIT_PENDING_DAMAGE, pending.status());
            assertTrue(pending.initialHit());
            assertStrike(pending, 19, true, true, List.of(), 0);
            fixture.assertRolls(new CampaignSpaceData.Die(20, 19));
            assertEquals(hitPoints, fixture.card(wizard).hitPoints());
            assertEquals(slots, fixture.slots(wizard));

            var waitingForShield = fixture.access();
            String manual = fixture.service.save(id(), waitingForShield.epoch(), waitingForShield.revision()).fileKey();
            String ended = fixture.end();
            for (String file : List.of(manual, ended)) {
                assertEquals(waitingForShield.snapshot(),
                        new CampaignCodec().decode(Files.readAllBytes(fixture.service.savedFile(file))).snapshot());
            }
            fixture.service.open(ended);
            assertNotEquals(waitingForShield.epoch(), fixture.access().epoch());
            assertEquals(waitingForShield.snapshot(), fixture.access().snapshot());
            assertEquals(pending, fixture.latestAttack());

            var flow = fixture.battle().activeFlow();
            var window = flow.windows().getLast();
            var opportunity = pendingReaction(window, wizard);
            assertEquals(ReactionKind.SHIELD, opportunity.options().getFirst().kind());
            fixture.combat(Command.reactionSpell(Kind.DECLARE_SHIELD, wizard,
                    new CastInput(casting, shield, List.of(wizard), null),
                    flow.flowKey(), flow.leafFrameKey(), window.windowKey(), opportunity.opportunityKey()));
            assertEquals(slots - 1, fixture.slots(wizard));
            assertFalse(fixture.actor(wizard).reactionAvailable());
            assertEquals(pending, fixture.latestAttack());
            assertEquals(hitPoints, fixture.card(wizard).hitPoints());
            fixture.assertRolls();

            var paidShield = fixture.access();
            fixture.service.open(fixture.end());
            var afterShield = fixture.access();
            assertNotEquals(paidShield.epoch(), afterShield.epoch());
            assertEquals(paidShield.snapshot(), afterShield.snapshot());
            assertEquals(pending, fixture.latestAttack());
            assertEquals(hitPoints, fixture.card(wizard).hitPoints());
            assertEquals(slots - 1, fixture.slots(wizard));
            assertFalse(fixture.actor(wizard).reactionAvailable());

            var finish = fixture.combatInput(fixture.continuation(Kind.CONTINUE_FLOW, fighter));
            assertCode("STALE_EPOCH", () -> fixture.submit(id(), paidShield, finish));
            var stale = finish.deepCopy();
            stale.addProperty("combatVersion", Long.toString(fixture.battle().version() - 1));
            assertCode("STALE_COMBAT", () -> fixture.submit(id(), afterShield, stale));
            assertEquals(afterShield, fixture.access());

            String request = id();
            fixture.failAfterUpdate = true;
            var fault = fixture.withDice(() -> assertThrows(IllegalStateException.class,
                    () -> fixture.submit(request, afterShield, finish)), 2, 1);
            assertEquals("critical damage transaction failure", fault.getMessage());
            assertEquals(afterShield, fixture.access());
            assertEquals(pending, fixture.latestAttack());
            assertEquals(hitPoints, fixture.card(wizard).hitPoints());
            assertEquals(slots - 1, fixture.slots(wizard));

            var result = fixture.withDice(() -> fixture.submit(request, afterShield, finish), 1, 2);
            var resolved = fixture.latestAttack();
            assertEquals(AttackStatus.RESOLVED, resolved.status());
            assertEquals(pending.frameKey(), resolved.frameKey());
            assertEquals(pending.firstD20(), resolved.firstD20());
            assertEquals(pending.armorClass() + 5, resolved.finalArmorClass());
            assertStrike(resolved, 19, true, true, List.of(1, 2), 5);
            assertEquals(hitPoints - 5, fixture.card(wizard).hitPoints());
            assertEquals(slots - 1, fixture.slots(wizard));
            assertEquals("COMBAT_DAMAGE_APPLIED", fixture.history(wizard).events().getLast().kind());
            assertTrue(fixture.history(wizard).events().getLast().command().combatInput().critical());
            fixture.assertRolls(new CampaignSpaceData.Die(4, 1), new CampaignSpaceData.Die(4, 2));
            assertNull(fixture.battle().activeFlow());

            var replay = fixture.submit(request, afterShield, finish);
            assertTrue(replay.lastCommand().replayed());
            assertEquals(result.lastCommand().acceptedRevision(), replay.lastCommand().acceptedRevision());
            assertEquals(result.history(), replay.history());
            assertEquals(result.domain(), replay.domain());
            assertEquals(1, fixture.state().roots().stream().filter(root -> root.operationId().toString().equals(request)).count());
            var committed = fixture.access().snapshot();
            String finalFile = fixture.end();
            byte[] complete = Files.readAllBytes(fixture.service.savedFile(finalFile));
            assertTamperedFilesRejected(fixture.service, complete);
            fixture.service.open(finalFile);
            assertEquals(committed, fixture.access().snapshot());

            long fighterHp = fixture.card(fighter).hitPoints();
            fixture.combat(Command.action(Kind.END_TURN, fighter));
            var burningHands = fixture.rule("adventure.spell", "spell.burning_hands");
            fixture.combat(Command.cast(wizard, new CastInput(casting, burningHands, List.of(), null, 1, null, 4)),
                    3, 4, 4);
            var spellFlow = fixture.battle().activeFlow();
            fixture.combat(Command.continueBreath(wizard, spellFlow.flowKey(), spellFlow.leafFrameKey()), 19);
            var spellDamage = fixture.battle().saveDamageBatches().getFirst();
            assertEquals(burningHands, spellDamage.spellSource().spell());
            assertEquals(List.of(3, 4, 4), spellDamage.sharedRoll().dice());
            assertEquals(19, spellDamage.checks().getFirst().firstD20());
            assertTrue(spellDamage.checks().getFirst().success());
            assertEquals(5, spellDamage.packets().getFirst().damageAmount());
            assertEquals(fighterHp - 5, fixture.card(fighter).hitPoints());
        }
    }

    @Test void levelSixUsesTheSameCriticalRuleForOpportunityAndReadiedWeaponAttacks() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("reactions"))) {
            fixture.create();
            UUID fighter = fixture.character("资深冠军", "PC", "fighter");
            UUID guard = fixture.character("守卫", "NPC", "fighter");
            UUID dagger = fixture.dagger(fighter);
            fixture.advanceFighter(fighter, 6);
            fixture.advanceFighter(guard, 2);
            assertEquals(List.of(3), fixture.improvedCritical(fighter).stream().map(FeatureState::acquiredAtClassLevel).toList());
            fixture.startBattle(fighter, guard);
            fixture.combat(Command.action(Kind.END_TURN, fighter));
            fixture.combat(Command.move(guard, List.of(new Position(3, 0))));
            var flow = fixture.battle().activeFlow();
            var window = flow.windows().getLast();
            var opportunity = pendingReaction(window, fighter);
            fixture.combat(Command.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK, fighter, dagger,
                    flow.flowKey(), flow.leafFrameKey(), window.windowKey(), opportunity.opportunityKey()), 19, 1, 2);
            fixture.combat(fixture.continuation(Kind.CONTINUE_MOVEMENT, guard));
            var opportunityAttack = fixture.latestAttack();
            assertTrue(opportunityAttack.opportunityAttack());
            assertNull(opportunityAttack.sequenceKey());
            assertStrike(opportunityAttack, 19, true, true, List.of(1, 2), 7);
            assertFalse(fixture.actor(fighter).reactionAvailable());

            fixture.combat(Command.action(Kind.END_TURN, guard));
            var trigger = new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER, guard, new Position(2, 0));
            fixture.combat(Command.ready(fighter, new ReadyInput(trigger, ReadyResponseKind.ATTACK, guard, dagger, List.of(), null)));
            var ready = fixture.battle().readyPreparations().getFirst();
            assertEquals(ReadyStatus.ARMED, ready.status());
            fixture.combat(Command.action(Kind.END_TURN, fighter));
            fixture.combat(Command.move(guard, List.of(new Position(2, 0))));
            flow = fixture.battle().activeFlow();
            window = flow.windows().getLast();
            var release = pendingReaction(window, fighter);
            fixture.combat(Command.releaseReady(fighter, ready.readyKey(), flow.flowKey(), flow.leafFrameKey(),
                    window.windowKey(), release.opportunityKey()), 19, 2, 1);
            fixture.combat(fixture.continuation(Kind.CONTINUE_MOVEMENT, guard));
            var readiedAttack = fixture.latestAttack();
            assertFalse(readiedAttack.opportunityAttack());
            assertNull(readiedAttack.sequenceKey());
            assertStrike(readiedAttack, 19, true, true, List.of(2, 1), 7);
            assertEquals("RELEASED", fixture.battle().flows().getLast().readyReleases().getFirst().outcome());
            assertFalse(fixture.actor(fighter).reactionAvailable());
        }
    }

    private static void assertStrike(AttackAttempt attack, int selected, boolean hit, boolean critical,
            List<Integer> damage, int total) {
        assertEquals(selected, attack.selectedD20());
        assertEquals(hit, attack.hit());
        assertEquals(critical, attack.critical());
        assertEquals(damage, attack.damageDice());
        assertEquals(total, attack.damageAmount());
    }

    private static void assertTamperedFilesRejected(CampaignStorageService service, byte[] complete) {
        var before = service.status();
        var material = CampaignJson.read(complete);
        var feature = material.getAsJsonObject("domains").getAsJsonObject("material").getAsJsonArray("rules").asList().stream()
                .map(JsonElement::getAsJsonObject).map(rule -> rule.getAsJsonObject("definition"))
                .filter(definition -> FEATURE.equals(definition.getAsJsonObject("identity").get("ruleKey").getAsString()))
                .findFirst().orElseThrow();
        feature.addProperty("executionAlgorithm", "UNKNOWN_WEAPON_CRITICAL_ALGORITHM");
        assertCode("INVALID_FILE", () -> service.importFile(resign(material)));

        var missingFeature = CampaignJson.read(complete);
        var championHistory = missingFeature.getAsJsonObject("domains").getAsJsonArray("characters").asList().stream()
                .map(JsonElement::getAsJsonObject).filter(character -> character.get("kind").getAsString().equals("PC"))
                .findFirst().orElseThrow().getAsJsonObject("history");
        assertTrue(removeFeature(championHistory.getAsJsonObject("current")));
        for (var row : championHistory.getAsJsonArray("events")) {
            var event = row.getAsJsonObject();
            if (event.get("before").isJsonObject()) removeFeature(event.getAsJsonObject("before"));
            removeFeature(event.getAsJsonObject("after"));
        }
        assertCode("INVALID_FILE", () -> service.importFile(resign(missingFeature)));

        var audit = CampaignJson.read(complete);
        var history = audit.getAsJsonObject("domains").getAsJsonArray("combats").get(0).getAsJsonObject();
        var current = history.getAsJsonObject("current");
        var strike = current.getAsJsonArray("flows").asList().getLast().getAsJsonObject()
                .getAsJsonArray("attacks").get(0).getAsJsonObject();
        assertTrue(strike.get("critical").getAsBoolean());
        strike.addProperty("critical", false);
        history.getAsJsonArray("events").asList().getLast().getAsJsonObject().getAsJsonObject("resolution")
                .add("after", current.deepCopy());
        assertCode("INVALID_FILE", () -> service.importFile(resign(audit)));
        assertEquals(before, service.status());
    }

    private static boolean removeFeature(JsonObject state) {
        return state.getAsJsonArray("features").asList().removeIf(row ->
                FEATURE.equals(row.getAsJsonObject().getAsJsonObject("feature").get("ruleKey").getAsString()));
    }

    private static byte[] resign(JsonObject file) {
        var integrity = file.remove("integrity").getAsJsonObject();
        file.addProperty("fileKey", id());
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
        file.add("integrity", integrity);
        return CampaignJson.canonical(file);
    }

    private static Opportunity pendingReaction(Window window, UUID actor) {
        var opportunity = window.opportunities().stream().filter(o -> o.status() == OpportunityStatus.PENDING)
                .findFirst().orElseThrow();
        assertEquals(actor, opportunity.actorId());
        return opportunity;
    }

    private static void assertCode(String code, Executable call) {
        var failure = assertThrows(StorageException.class, call);
        assertEquals(code, failure.code(), failure.getMessage());
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static JsonObject fields(Object... pairs) {
        var input = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) input.addProperty((String) pairs[i], pairs[i + 1].toString());
        return input;
    }

    private static final class Fixture implements AutoCloseable {
        final CampaignStorageService service;
        final ArrayDeque<Integer> dice = new ArrayDeque<>();
        CampaignRuleContext rules;
        boolean failAfterUpdate;

        Fixture(Path root) throws IOException {
            service = new CampaignStorageService(root, boundary -> {
                if (boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE && failAfterUpdate) {
                    failAfterUpdate = false;
                    throw new IllegalStateException("critical damage transaction failure");
                }
            }, sides -> {
                Integer value = dice.poll();
                assertNotNull(value, "Unexpected live d" + sides);
                assertTrue(value >= 1 && value <= sides, "Invalid scripted d" + sides);
                return value;
            });
        }

        void create() {
            var library = service.ruleLibrary();
            var members = BundledCampaignRules.artifacts().stream().map(artifact -> {
                var imported = library.importArtifact(id(), artifact.originalZip()).imported().member();
                var review = library.review(imported.moduleKey(), imported.releaseVersion());
                assertEquals(imported, review.imported().member());
                assertNull(review.acceptance());
                library.accept(id(), review.imported().member(), LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
                return imported;
            }).toList();
            var preview = service.previewRules(members);
            assertEquals(members, preview.binding().members());
            assertTrue(preview.catalog().stream().anyMatch(rule -> rule.key().equals(FEATURE) && rule.executable()));
            service.create("冠军重击验收", members);
            assertEquals(preview.binding(), state().material().binding());
            rules = state().material().restore();
        }

        CampaignStorageService.AccessSnapshot access() { return service.accessSnapshot(); }
        CampaignDomainData.State state() { return access().snapshot().domains(); }
        Battle battle() { return CampaignSpaceCommands.currentBattle(state()).current(); }
        AttackAttempt latestAttack() { return battle().flows().getLast().attacks().getLast(); }
        Combatant actor(UUID id) {
            return battle().combatants().stream().filter(actor -> actor.characterId().equals(id)).findFirst().orElseThrow();
        }
        Schema2CharacterRuntime.History history(UUID id) {
            return state().characters().stream().filter(card -> card.id().equals(id)).findFirst().orElseThrow().history();
        }
        Schema2CharacterRuntime.State card(UUID id) { return history(id).current(); }
        RuleIdentity rule(String type, String key) { return rules.rule(type, key); }
        List<FeatureState> improvedCritical(UUID id) {
            return card(id).features().stream().filter(feature -> feature.feature().ruleKey().equals(FEATURE)).toList();
        }
        long slots(UUID wizard) {
            return card(wizard).resources().stream().filter(r -> r.identity().ruleKey().equals(WIZARD_SLOT))
                    .findFirst().orElseThrow().current();
        }
        UUID character(String name, String kind, String classKey) {
            return UUID.fromString(CampaignSpaceFixtures.command(service, "character-create",
                    fields("name", name, "kind", kind, "classKey", classKey, "backgroundKey", "acolyte")).lastCommand().subjectId());
        }
        void advanceFighter(UUID actor, int target) {
            for (int level = card(actor).level() + 1; level <= target; level++) {
                var subclass = level == 3 ? rule("character.subclass", "subclass.champion") : null;
                var abilities = level == 4 || level == 6 ? Map.of(STRENGTH, 2) : Map.<String, Integer>of();
                characterCommand(id(), actor, new Schema2CharacterRuntime.Command("CHARACTER_ADVANCED", null, 0,
                        null, "FIXED_AVERAGE", rule("character.class", "class.fighter"),
                        new GrowthChoices(subclass, abilities, null, Map.of())));
                assertEquals(level, card(actor).level());
            }
        }
        UUID dagger(UUID actor) {
            UUID item = UUID.randomUUID();
            equipment(item.toString(), actor, "ITEM_ACQUIRED",
                    new EquipmentInput(item, rule("equipment.item", "item.dagger"), "CARRIED", "主持人确认取得匕首"));
            equipment(id(), actor, "ITEM_EQUIPPED", new EquipmentInput(item, null, "MAIN_HAND", "主持人确认装备匕首"));
            return item;
        }
        void equipment(String request, UUID actor, String kind, EquipmentInput equipment) {
            characterCommand(request, actor, new Schema2CharacterRuntime.Command(kind, null, 0, null, null,
                    null, GrowthChoices.EMPTY, null, null, equipment, null));
        }
        CampaignStorageService.CampaignView characterCommand(String request, UUID actor, Schema2CharacterRuntime.Command command) {
            var input = fields("characterId", actor, "characterVersion", card(actor).version());
            input.add("command", CampaignDomainCodec.toJson(command));
            return CampaignSpaceFixtures.command(service, request, "character-command", input);
        }
        void startBattle(UUID fighter, UUID target) {
            dice.addAll(List.of(20, 10));
            PlayerSpaceFixture.beginBattle(service, fighter, target);
            assertTrue(dice.isEmpty());
            assertEquals(fighter, battle().activeActor());
        }
        void leaveBattle(UUID fighter, UUID target) {
            combat(Command.action(Kind.END_COMBAT, fighter));
            var before = state();
            var subjects = new JsonArray();
            for (UUID actor : List.of(fighter, target)) {
                subjects.add(fields("id", actor, "version", CampaignSpaceCommands.position(before, actor).version()));
            }
            var leave = new JsonObject();
            leave.add("subjects", subjects);
            CampaignSpaceFixtures.command(service, "scene-leave", leave);
        }
        void nextRound(UUID fighter, UUID target) {
            combat(Command.action(Kind.END_TURN, fighter));
            combat(Command.action(Kind.END_TURN, target));
        }
        AttackAttempt strike(UUID fighter, UUID target, UUID item, Integer... rolls) {
            combat(Command.action(Kind.BEGIN_ATTACK, fighter));
            combat(Command.attack(fighter, target, item), rolls);
            return latestAttack();
        }
        Command continuation(Kind kind, UUID actor) {
            var flow = battle().activeFlow();
            var frame = flow.frames().stream().filter(f -> f.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
            return new Command(kind, actor, null, null, List.of(), List.of(), List.of(),
                    flow.flowKey(), flow.leafFrameKey(), frame.windowKey(), null);
        }
        void combat(Command command, Integer... rolls) {
            withDice(() -> submit(id(), access(), combatInput(command)), rolls);
        }
        <T> T withDice(Supplier<T> action, Integer... rolls) {
            assertTrue(dice.isEmpty());
            dice.addAll(List.of(rolls));
            T result = action.get();
            assertTrue(dice.isEmpty(), "Not all scripted dice were drawn");
            return result;
        }
        JsonObject combatInput(Command command) {
            var battle = battle();
            var input = fields("combatId", battle.combatId(), "combatVersion", battle.version());
            input.add("command", CampaignDomainCodec.toJson(command));
            return CampaignSpaceFixtures.focused(state(), input);
        }
        CampaignStorageService.CampaignView submit(String request, CampaignStorageService.AccessSnapshot at, JsonObject input) {
            return service.domainCommand(request, at.epoch(), at.revision(), "combat-command", input);
        }
        void assertRolls(CampaignSpaceData.Die... rolls) {
            assertEquals(List.of(rolls), state().roots().getLast().rolls());
        }
        String end() {
            var at = access();
            return service.end(id(), at.epoch(), at.revision()).fileKey();
        }
        @Override public void close() throws IOException { service.close(); }
    }
}
