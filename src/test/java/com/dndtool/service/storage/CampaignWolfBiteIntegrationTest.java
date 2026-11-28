package com.dndtool.service.storage;

import com.dndtool.module.composition.ProductionRuleArtifact;
import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.storage.BundledRuleManifest.Slot;
import com.dndtool.service.storage.CampaignExtraAttackIntegrationTest.Fixture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.dndtool.module.composition.Schema2CompositionKernel.DamageType.PIERCING;
import static com.dndtool.service.Schema2CombatState.*;
import static com.dndtool.service.storage.CampaignSpaceData.Ref;
import static com.dndtool.service.storage.CampaignStorageService.Boundary;
import static org.junit.jupiter.api.Assertions.*;

class CampaignWolfBiteIntegrationTest {
    private static final String MAGIC = "srd51_magic";
    private static final String WOLF = "monster.wolf";
    private static final String STRENGTH = "ability.strength";
    private static final String WOLF_VARIANT = "dm_runtime_wolf_variant";
    private static final String WAR_PROPERTY = "dnd.test.wolfWar";
    private static final String VARIANT_PROPERTY = "dnd.test.wolfVariant";
    private static final String WAR_RULES = "WEB-INF/classes/storage/rules/";
    private static final Path DELIVERED_RULES = Path.of("src", "main", "resources", "storage", "rules");
    private static final Path WOLF_OVERRIDE = Path.of("rule-packages", "srd51-runtime", "dm-runtime-wolf-variant", "changes.json");
    private static final List<Slot> STANDARD_SLOTS = List.of(Slot.CORE, Slot.CHARACTERS, Slot.MAGIC, Slot.HOUSE);
    private static final Position START = new Position(2, 2);
    private static final Position CARRIED_START = new Position(2, 1);
    private static final List<Position> ROUTE = List.of(new Position(2, 3), new Position(3, 4), new Position(4, 5));
    private static final List<Position> CARRIED_ROUTE = List.of(START, ROUTE.get(0), ROUTE.get(1));
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {11, 12})
    void frozenBiteDcDeterminesPostureWithIdenticalDiceAndContinuesAfterReopen(int dc) throws Exception {
        try (var fixture = new Fixture(temporary.resolve("ordinary-" + dc))) {
            create(fixture, dc);
            UUID observer = fixture.character("巡林战士", "PC");
            UUID world = PlayerSpaceFixture.placeAtWorld(fixture.service, observer);
            UUID wolf = wolf(fixture, "袭击的狼"), target = wolf(fixture, "被咬的狼");
            fixture.startBattle("狼群相遇", observer, List.of(new Deployment(wolf, cell(1, 2), 0),
                    new Deployment(target, START, 1), new Deployment(observer, cell(7, 7), 1)), 20, 10, 1);
            assertEquals(11, monster(fixture, target).hitPoints());
            fixture.combat(Command.monsterAttack(wolf, target, bite(fixture)), 12, 2, 3, 10);

            boolean fallen = dc == 12;
            AttackAttempt strike = attack(fixture);
            assertBite(fixture, strike, wolf, target, false, 12, dc, 10, 1, 0);
            assertEquals(11, strike.monsterAttack().onHit().savingThrow().total());
            assertEquals(fallen ? OnHitOutcome.PRONE_APPLIED : OnHitOutcome.SAVE_SUCCEEDED,
                    strike.monsterAttack().onHit().outcome());
            assertEquals(4, monster(fixture, target).hitPoints());
            assertFalse(monster(fixture, target).dead());
            assertEquals(fallen, fixture.actor(target).prone());
            assertEquals(fallen, monster(fixture, target).prone());
            assertFalse(fixture.actor(wolf).actionAvailable());
            assertTrue(fixture.actor(wolf).reactionAvailable());
            assertEquals(List.of(12, 2, 3, 10), lastDraws(fixture));
            assertEquals(1, fixture.state().roots().getLast().rolls().stream().filter(d -> d.sides() == 20 && d.value() == 10).count());

            if (fallen) reopen(fixture, file -> assertForgedFilesRejected(fixture, file));
            else reopen(fixture);
            assertEquals(strike, attack(fixture));
            assertEquals(4, monster(fixture, target).hitPoints());
            assertEquals(fallen, fixture.actor(target).prone());
            if (fallen) {
                fixture.leaveBattle(wolf, target, observer);
                assertEquals(Ref.world(world), position(fixture, target).space());
                assertTrue(monster(fixture, target).prone());
                fixture.startBattle("另一片林地", observer, List.of(new Deployment(target, START, 0),
                        new Deployment(wolf, cell(1, 2), 1), new Deployment(observer, cell(7, 7), 1)), 20, 10, 1);
                assertTrue(fixture.actor(target).prone());
                assertEquals(4, monster(fixture, target).hitPoints());
                fixture.combat(Command.action(Kind.STAND_UP, target));
                assertFalse(fixture.actor(target).prone());
                assertFalse(monster(fixture, target).prone());
                assertEquals(40, fixture.actor(target).movementSpentHalfFeet());
                assertTrue(fixture.actor(target).actionAvailable());
                assertTrue(fixture.actor(target).reactionAvailable());
                reopen(fixture);
                assertFalse(fixture.actor(target).prone());
            } else {
                fixture.combat(Command.action(Kind.END_TURN, wolf));
                fixture.combat(Command.monsterAttack(target, wolf, bite(fixture)), 1);
                assertFalse(attack(fixture).hit());
                assertNull(attack(fixture).monsterAttack().onHit());
                assertEquals(List.of(1), lastDraws(fixture));
                fixture.combat(Command.action(Kind.END_TURN, target));
                fixture.combat(Command.action(Kind.END_TURN, observer));
                assertEquals(wolf, fixture.battle().activeActor());
            }
            assertEquals(List.of(), lastDraws(fixture));
        }
    }

    @Test void shieldRaisesArmorFromThirteenToEighteenBeforeAnyDamageOrStrengthSave() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("shield"))) {
            create(fixture, 11);
            UUID wizard = subject(fixture, "character-create", fields("name", "防护法师", "kind", "PC", "classKey", "wizard", "backgroundKey", "acolyte"));
            RuleIdentity casting = prepareWizard(fixture, wizard);
            RuleIdentity shield = fixture.rule("adventure.spell", "spell.shield");
            PlayerSpaceFixture.placeAtWorld(fixture.service, wizard);
            UUID wolf = wolf(fixture, "林地狼");
            fixture.startBattle("护盾术", wizard, List.of(new Deployment(wolf, cell(1, 2), 0), new Deployment(wizard, START, 1)), 20, 1);
            long hp = fixture.card(wizard).hitPoints(), slots = slots(fixture, wizard);
            fixture.combat(Command.monsterAttack(wolf, wizard, bite(fixture)), 12);
            AttackAttempt pending = attack(fixture);
            assertEquals(AttackStatus.HIT_PENDING_DAMAGE, pending.status());
            assertTrue(pending.initialHit());
            assertEquals(16, pending.selectedD20() + pending.monsterAttack().hitBonus());
            assertEquals(13, pending.armorClass());
            assertEquals(List.of(12), lastDraws(fixture));
            assertNull(pending.monsterAttack().onHit());
            assertEquals(hp, fixture.card(wizard).hitPoints());
            reopen(fixture);
            assertEquals(pending, attack(fixture));

            Flow flow = fixture.battle().activeFlow();
            Window window = flow.windows().getLast();
            Opportunity offer = opportunity(window, wizard);
            assertEquals(ReactionKind.SHIELD, offer.options().getFirst().kind());
            fixture.combat(Command.reactionSpell(Kind.DECLARE_SHIELD, wizard, new CastInput(casting, shield, List.of(wizard), null),
                    flow.flowKey(), flow.leafFrameKey(), window.windowKey(), offer.opportunityKey()));
            assertEquals(slots - 1, slots(fixture, wizard));
            assertFalse(fixture.actor(wizard).reactionAvailable());
            assertEquals(List.of(), lastDraws(fixture));
            var beforeReopen = fixture.access();
            reopen(fixture);
            Command finish = continuation(fixture, wolf);
            JsonObject input = fixture.combatInput(finish);
            var before = fixture.access();
            assertCode("STALE_EPOCH", () -> fixture.submit(id(), beforeReopen, input, null));
            var staleCombat = input.deepCopy();
            staleCombat.addProperty("combatVersion", Long.toString(fixture.battle().version() - 1));
            assertCode("STALE_COMBAT", () -> fixture.submit(id(), before, staleCombat, null));
            assertEquals(before, fixture.access());

            String request = id();
            var applied = fixture.submit(request, before, input, null);
            assertReplay(fixture, request, before, input, applied);
            AttackAttempt resolved = attack(fixture);
            assertEquals(pending.frameKey(), resolved.frameKey());
            assertEquals(AttackStatus.RESOLVED, resolved.status());
            assertEquals(12, resolved.firstD20());
            assertTrue(resolved.initialHit());
            assertFalse(resolved.hit());
            assertEquals(18, resolved.finalArmorClass());
            assertEquals(List.of(), resolved.monsterAttack().damageComponents());
            assertEquals(0, resolved.damageAmount());
            assertNull(resolved.monsterAttack().onHit());
            assertEquals(hp, fixture.card(wizard).hitPoints());
            assertFalse(fixture.actor(wizard).prone());
            assertEquals(slots - 1, slots(fixture, wizard));
            assertEquals(List.of(), lastDraws(fixture));
            var history = CampaignSpaceCommands.currentBattle(fixture.state());
            assertEquals(1, history.events().stream().filter(e -> e.resolution().rolls().stream().anyMatch(r -> r.value() == 12)).count());
        }
    }

    @Test void savedOpportunityKeepsTheGrappleAndRechecksTheFullSuffixAtCrawlCost() throws Exception {
        try (var fixture = new Fixture(temporary.resolve("opportunity"))) {
            create(fixture, 11);
            UUID mover = fixture.character("抓者", "PC"), carried = fixture.character("受擒队友", "PC");
            PlayerSpaceFixture.placeAtWorld(fixture.service, mover, carried);
            UUID wolf = wolf(fixture, "出口处的狼");
            fixture.startBattle("拖带与借机啮咬", List.of(new Deployment(mover, START, 0),
                    new Deployment(carried, CARRIED_START, 0), new Deployment(wolf, cell(1, 2), 1)), 20, 10, 1);
            UUID grapple = grapple(fixture, mover, carried);
            fixture.combat(Command.move(mover, ROUTE, new DragInput(grapple, CARRIED_ROUTE)));
            var moverPrefix = position(fixture, mover);
            var carriedPrefix = position(fixture, carried);
            assertEquals(ROUTE.getFirst(), moverPrefix.grid());
            assertEquals(CARRIED_ROUTE.getFirst(), carriedPrefix.grid());
            assertEquals(new DragPlan(grapple, carried, CARRIED_START, CARRIED_ROUTE, 30), movement(fixture).drag());
            assertEquals(1, movement(fixture).cursor());
            assertEquals(10, movement(fixture).spentHalfFeet());
            assertEquals(List.of(), lastDraws(fixture));
            Flow savedFlow = fixture.battle().activeFlow();
            reopen(fixture);
            assertEquals(savedFlow, fixture.battle().activeFlow());

            Window window = savedFlow.windows().getLast();
            assertEquals(TriggerKind.MOVEMENT_LEAVE_REACH_BEFORE, window.trigger().kind());
            Opportunity offer = opportunity(window, wolf);
            assertEquals(bite(fixture), offer.options().getFirst().monsterAttack());
            var command = Command.monsterOpportunityAttack(wolf, bite(fixture), savedFlow.flowKey(), savedFlow.leafFrameKey(),
                    window.windowKey(), offer.opportunityKey());
            var input = fixture.combatInput(command);
            var before = fixture.access();
            var forged = input.deepCopy();
            forged.getAsJsonObject("command").getAsJsonObject("monsterAttackInput").addProperty("dc", "1");
            assertCode("INVALID_FILE", () -> fixture.submit(id(), before, forged, null));
            assertEquals(before, fixture.access());

            String request = id();
            long hp = fixture.card(mover).hitPoints();
            boolean wolfActionAvailable = fixture.actor(wolf).actionAvailable();
            fixture.dice.addAll(List.of(12, 2, 3, 2));
            assertLateRollback(fixture, request, before, input);
            assertEquals(hp, fixture.card(mover).hitPoints());
            assertFalse(fixture.actor(mover).prone());
            assertTrue(fixture.actor(wolf).reactionAvailable());
            assertTrue(activeGrapple(fixture, grapple));
            assertEquals(moverPrefix, position(fixture, mover));
            assertEquals(carriedPrefix, position(fixture, carried));
            assertEquals(10, fixture.actor(mover).movementSpentHalfFeet());

            fixture.dice.addAll(List.of(12, 2, 3, 2));
            var biteApplied = fixture.submit(request, before, input, null);
            assertTrue(fixture.dice.isEmpty());
            assertReplay(fixture, request, before, input, biteApplied);
            assertBite(fixture, attack(fixture), wolf, mover, true, 12, 11, 2, 2, 2);
            assertEquals(OnHitOutcome.PRONE_APPLIED, attack(fixture).monsterAttack().onHit().outcome());
            assertEquals(List.of(12, 2, 3, 2), lastDraws(fixture));
            assertEquals(hp - 7, fixture.card(mover).hitPoints());
            assertTrue(fixture.card(mover).hitPoints() > 0);
            assertTrue(fixture.actor(mover).prone());
            assertFalse(fixture.actor(wolf).reactionAvailable());
            assertEquals(wolfActionAvailable, fixture.actor(wolf).actionAvailable());
            assertTrue(activeGrapple(fixture, grapple));

            fixture.continueMovement(mover);
            assertEquals(MovementStatus.STOPPED, movement(fixture).status());
            assertEquals("SUFFIX_UNAVAILABLE", movement(fixture).stopReason());
            assertEquals(1, movement(fixture).cursor());
            assertEquals(moverPrefix, position(fixture, mover));
            assertEquals(carriedPrefix, position(fixture, carried));
            assertEquals(10, fixture.actor(mover).movementSpentHalfFeet());
            assertTrue(activeGrapple(fixture, grapple));
            assertEquals(List.of(), lastDraws(fixture));

            String crawlRequest = id();
            var beforeCrawl = fixture.access();
            var crawl = fixture.combatInput(Command.move(mover, List.of(ROUTE.get(1)), new DragInput(grapple, List.of(CARRIED_ROUTE.get(1)))));
            assertLateRollback(fixture, crawlRequest, beforeCrawl, crawl);
            assertEquals(moverPrefix, position(fixture, mover));
            assertEquals(carriedPrefix, position(fixture, carried));
            var crawled = fixture.submit(crawlRequest, beforeCrawl, crawl, null);
            assertReplay(fixture, crawlRequest, beforeCrawl, crawl, crawled);
            assertEquals(ROUTE.get(1), position(fixture, mover).grid());
            assertEquals(CARRIED_ROUTE.get(1), position(fixture, carried).grid());
            assertEquals(moverPrefix.version() + 1, position(fixture, mover).version());
            assertEquals(carriedPrefix.version() + 1, position(fixture, carried).version());
            assertEquals(30, fixture.actor(mover).movementSpentHalfFeet());
            assertEquals(0, fixture.actor(carried).movementSpentHalfFeet());
            assertEquals(20, movement(fixture).spentHalfFeet());
            assertEquals(MovementStatus.COMPLETED, movement(fixture).status());
            assertTrue(activeGrapple(fixture, grapple));
            reopen(fixture);
            assertTrue(fixture.actor(mover).prone());
            assertTrue(activeGrapple(fixture, grapple));
        }
    }

    private static void create(Fixture fixture, int dc) throws Exception {
        String warPath = System.getProperty(WAR_PROPERTY);
        String variantPath = System.getProperty(VARIANT_PROPERTY);
        assertEquals(warPath == null, variantPath == null, "Supply both " + WAR_PROPERTY + " and " + VARIANT_PROPERTY);
        var artifacts = standardArtifacts(warPath);
        if (dc != 11) {
            ProductionRuleArtifact variant;
            if (variantPath == null) {
                var overrides = JsonParser.parseString(Files.readString(WOLF_OVERRIDE)).getAsJsonArray().asList().stream()
                        .map(JsonElement::getAsJsonObject).toList();
                variant = CampaignRuleSupportTest.extension(WOLF_VARIANT, artifacts, List.of(), List.of(), overrides);
            } else variant = ProductionRuleArtifact.read(Files.readAllBytes(Path.of(variantPath)));
            assertEquals(WOLF_VARIANT, variant.moduleKey());
            assertEquals(variantPath == null ? "1" : "3", variant.releaseVersion());
            artifacts.add(variant);
        }
        createWithRules(fixture.service, "狼啮咬验收", artifacts);
    }

    static void createWithDeliveredRules(CampaignStorageService service, String name) throws IOException {
        createWithRules(service, name, standardArtifacts(null));
    }

    private static void createWithRules(CampaignStorageService service, String name, List<ProductionRuleArtifact> artifacts) {
        var library = service.ruleLibrary();
        var members = artifacts.stream().map(artifact -> {
            var member = library.importArtifact(id(), artifact.originalZip()).imported().member();
            assertEquals(BundledRuleManifest.Entry.from(artifact).member(), member);
            library.accept(id(), member, LocalRuleLibrary.REVIEW_PROTOCOL, "local-dm");
            return member;
        }).toList();
        var preview = service.previewRules(members);
        assertEquals(members, preview.binding().members());
        service.create(name, members);
        assertEquals(preview.binding(), service.accessSnapshot().snapshot().domains().material().binding());
    }

    private static List<ProductionRuleArtifact> standardArtifacts(String warPath) throws IOException {
        var artifacts = new ArrayList<ProductionRuleArtifact>();
        if (warPath == null) {
            for (Slot slot : STANDARD_SLOTS)
                artifacts.add(ProductionRuleArtifact.read(Files.readAllBytes(DELIVERED_RULES.resolve(slot.fileName))));
            return artifacts;
        }
        try (var war = new ZipFile(warPath); var manifest = warRule(war, BundledRuleManifest.RESOURCE)) {
            var declared = BundledRuleManifest.read(manifest);
            for (Slot slot : STANDARD_SLOTS) {
                try (var rawRule = warRule(war, slot.fileName)) {
                    var artifact = BundledRuleManifest.readArtifact(rawRule);
                    assertEquals(declared.get(slot), BundledRuleManifest.Entry.from(artifact));
                    artifacts.add(artifact);
                }
            }
        }
        return artifacts;
    }

    private static InputStream warRule(ZipFile war, String filename) throws IOException {
        var entry = war.getEntry(WAR_RULES + filename);
        assertNotNull(entry, "Candidate WAR is missing " + WAR_RULES + filename);
        return war.getInputStream(entry);
    }

    private static RuleIdentity prepareWizard(Fixture fixture, UUID wizard) {
        var rules = fixture.state().material().restore();
        var casting = fixture.rule("character.wizard_spellcasting", "spellcasting.wizard");
        var prepared = rules.initialWizardPrepared();
        wizardBook(fixture, wizard, "SPELLBOOK_INITIALIZED", new SpellbookInput(casting, rules.initialWizardBook(), prepared));
        var spells = List.of(List.of("detect_magic", "disguise_self"), List.of("grease", "mage_armor"), List.of("magic_missile", "sleep"));
        for (int level = 2; level <= 4; level++) {
            var subclass = level == 2 ? fixture.rule("character.subclass", "subclass.evocation") : null;
            var increases = level == 4 ? Map.of("ability.dexterity", 2) : Map.<String, Integer>of();
            characterCommand(fixture, wizard, new Schema2CharacterRuntime.Command("CHARACTER_ADVANCED", null, 0,
                    null, "FIXED_AVERAGE", fixture.rule("character.class", "class.wizard"), new GrowthChoices(subclass, increases, null, Map.of())));
            var learned = spells.get(level - 2).stream().map(key -> fixture.rule("adventure.spell", "spell." + key)).toList();
            wizardBook(fixture, wizard, "SPELLBOOK_ADVANCED", new SpellbookInput(casting, learned, prepared));
        }
        return casting;
    }

    private static void wizardBook(Fixture fixture, UUID wizard, String kind, SpellbookInput input) {
        characterCommand(fixture, wizard, Schema2CharacterRuntime.Command.spellbook(kind, input));
    }

    private static void characterCommand(Fixture fixture, UUID character, Schema2CharacterRuntime.Command command) {
        var input = fields("characterId", character, "characterVersion", fixture.card(character).version());
        input.add("command", CampaignDomainCodec.toJson(command));
        CampaignSpaceFixtures.command(fixture.service, "character-command", input);
    }

    private static void assertBite(Fixture fixture, AttackAttempt attack, UUID wolf, UUID target, boolean opportunity,
            int attackDie, int dc, int saveDie, int strength, int proficiency) {
        assertEquals(wolf, attack.actorId());
        assertEquals(target, attack.targetId());
        assertEquals(AttackStatus.RESOLVED, attack.status());
        assertEquals(opportunity, attack.opportunityAttack());
        assertTrue(attack.hit());
        assertFalse(attack.critical());
        assertEquals(attackDie, attack.firstD20());
        assertNull(attack.secondD20());
        assertEquals(7, attack.damageAmount());
        var evidence = attack.monsterAttack();
        assertEquals(bite(fixture), evidence.action());
        assertEquals(4, evidence.hitBonus());
        assertEquals(5, evidence.reachFeet());
        assertEquals(List.of(), evidence.packTacticsAllies());
        assertEquals(List.of(new AttackDamageComponent(PIERCING, 2, 4, 2, List.of(2, 3), 7, 7, List.of())), evidence.damageComponents());
        var onHit = evidence.onHit();
        assertEquals(dc, onHit.difficultyClass());
        assertEquals(saveDie, onHit.selectedD20());
        assertFalse(onHit.alreadyProne());
        assertNull(onHit.immunitySource());
        var save = onHit.savingThrow();
        assertEquals("MONSTER_HIT_STR", save.purpose());
        assertEquals(STRENGTH, save.abilityKey());
        assertEquals(CheckMode.ROLL, save.mode());
        assertEquals(saveDie, save.firstD20());
        assertNull(save.secondD20());
        assertEquals(strength, save.abilityModifier());
        assertEquals(proficiency, save.proficiencyBonus());
        assertEquals(dc, save.difficultyClass());
        assertEquals(saveDie + strength + proficiency, save.total());
        assertEquals(save.total() >= dc, save.success());
    }

    private static void assertLateRollback(Fixture fixture, String request, CampaignStorageService.AccessSnapshot at, JsonObject input) {
        fixture.failure = Boundary.AFTER_CHARACTER_UPDATE;
        assertEquals("injected transaction failure", assertThrows(IllegalStateException.class,
                () -> fixture.submit(request, at, input, null)).getMessage());
        assertEquals(at, fixture.access());
        assertTrue(fixture.dice.isEmpty());
        var actor = CampaignParticipationCommands.actualActor(at.snapshot().domains(), "combat-command", input);
        assertTrue(fixture.service.exactDomainReceipt(request, at.runId(), at.revision(), "combat-command", input, actor, ignored -> {}).isEmpty());
    }

    private static void assertReplay(Fixture fixture, String request, CampaignStorageService.AccessSnapshot at, JsonObject input,
            CampaignStorageService.CampaignView accepted) {
        var replay = fixture.submit(request, at, input, null);
        assertTrue(replay.lastCommand().replayed());
        assertEquals(accepted.lastCommand().acceptedRevision(), replay.lastCommand().acceptedRevision());
        assertEquals(accepted.domain(), replay.domain());
        assertEquals(1, fixture.state().roots().stream().filter(root -> root.operationId().toString().equals(request)).count());
    }

    private static void assertForgedFilesRejected(Fixture fixture, byte[] complete) {
        var forged = CampaignJson.read(complete);
        var history = forged.getAsJsonObject("domains").getAsJsonArray("combats").get(0).getAsJsonObject();
        var current = history.getAsJsonObject("current");
        var evidence = current.getAsJsonArray("flows").asList().getLast().getAsJsonObject().getAsJsonArray("attacks").get(0)
                .getAsJsonObject().getAsJsonObject("monsterAttack").getAsJsonObject("onHit");
        assertEquals("10", evidence.get("selectedD20").getAsString());
        evidence.addProperty("selectedD20", "9");
        history.getAsJsonArray("events").asList().getLast().getAsJsonObject().getAsJsonObject("resolution").add("after", current.deepCopy());

        var malformed = CampaignJson.read(complete);
        var entries = malformed.getAsJsonObject("domains").getAsJsonObject("material").getAsJsonArray("entries");
        var frozenBite = entries.asList().stream().map(row -> row.getAsJsonObject().getAsJsonObject("entry")).filter(entry -> {
            var identity = entry.getAsJsonObject("identity");
            return WOLF.equals(identity.getAsJsonObject("rule").get("ruleKey").getAsString())
                    && "bite".equals(identity.get("entryKey").getAsString());
        }).findFirst().orElseThrow();
        var frozenSave = frozenBite.getAsJsonObject("actionProfile").getAsJsonObject("attackProfile").getAsJsonObject("onHit");
        assertEquals("12", frozenSave.get("dc").getAsString());
        frozenSave.addProperty("dc", "0");

        var before = fixture.service.status();
        var files = fixture.service.savedCampaigns();
        assertCode("INVALID_FILE", () -> fixture.service.importFile(resign(forged)));
        assertCode("INVALID_DOMAIN_DOCUMENT", () -> fixture.service.importFile(resign(malformed)));
        assertEquals(before, fixture.service.status());
        assertEquals(files, fixture.service.savedCampaigns());
    }

    private static byte[] resign(JsonObject file) {
        var integrity = file.remove("integrity").getAsJsonObject();
        file.addProperty("fileKey", id());
        integrity.addProperty("digest", CampaignJson.digest(CampaignCodec.FILE_DIGEST_DOMAIN, CampaignJson.canonical(file)));
        file.add("integrity", integrity);
        return CampaignJson.canonical(file);
    }

    private static void reopen(Fixture fixture) throws Exception {
        reopen(fixture, ignored -> {});
    }

    private static void reopen(Fixture fixture, Consumer<byte[]> inspectEndedFile) throws Exception {
        var before = fixture.access();
        String saved = fixture.service.save(id(), before.epoch(), before.revision()).fileKey();
        String ended = fixture.end();
        var manual = Files.readAllBytes(fixture.service.savedFile(saved));
        var complete = Files.readAllBytes(fixture.service.savedFile(ended));
        var codec = new CampaignCodec();
        assertEquals(before.snapshot(), codec.decode(manual).snapshot());
        assertEquals(before.snapshot(), codec.decode(complete).snapshot());
        inspectEndedFile.accept(complete);
        fixture.service.open(ended);
        assertNotEquals(before.epoch(), fixture.access().epoch());
        assertEquals(before.snapshot(), fixture.access().snapshot());
    }

    private static UUID grapple(Fixture fixture, UUID mover, UUID carried) {
        fixture.combat(Command.action(Kind.BEGIN_ATTACK, mover));
        var sequence = fixture.actor(mover).sequence();
        fixture.combat(Command.melee(Kind.GRAPPLE, mover, carried, null, null,
                new MeleeInput(sequence.sequenceKey(), 0, null, HandSlot.MAIN_HAND, null, null)));
        var flow = fixture.battle().activeFlow();
        fixture.combat(Command.melee(Kind.CHOOSE_MELEE_DEFENSE, carried, null, flow.flowKey(), flow.leafFrameKey(),
                new MeleeInput(null, null, null, null, ContestSkill.ACROBATICS, null)), 19, 2);
        var relation = fixture.battle().meleeControl().grapples().getLast();
        assertTrue(relation.active());
        return relation.grappleKey();
    }

    private static boolean activeGrapple(Fixture fixture, UUID grapple) {
        return fixture.battle().meleeControl().grapples().stream().anyMatch(r -> r.grappleKey().equals(grapple) && r.active());
    }

    private static UUID wolf(Fixture fixture, String name) {
        var input = fields("name", name);
        input.add("monster", CampaignDomainCodec.toJson(fixture.rule("adventure.monster", WOLF)));
        return subject(fixture, "monster-create", input);
    }

    private static EntryIdentity bite(Fixture fixture) {
        return new EntryIdentity(fixture.rule("adventure.monster", WOLF), "actions", MAGIC, "bite");
    }

    private static AttackAttempt attack(Fixture fixture) {
        return fixture.battle().flows().stream().flatMap(flow -> flow.attacks().stream()).reduce((a, b) -> b).orElseThrow();
    }

    private static Movement movement(Fixture fixture) {
        return fixture.battle().flows().stream().flatMap(flow -> flow.movements().stream()).reduce((a, b) -> b).orElseThrow();
    }

    private static Command continuation(Fixture fixture, UUID actor) {
        var flow = fixture.battle().activeFlow();
        var leaf = flow.frames().stream().filter(frame -> frame.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
        return new Command(Kind.CONTINUE_FLOW, actor, null, null, List.of(), List.of(), List.of(),
                flow.flowKey(), leaf.frameKey(), leaf.windowKey(), null);
    }

    private static Opportunity opportunity(Window window, UUID actor) {
        return window.opportunities().stream().filter(o -> o.actorId().equals(actor) && o.status() == OpportunityStatus.PENDING)
                .findFirst().orElseThrow();
    }

    private static List<Integer> lastDraws(Fixture fixture) {
        var event = CampaignSpaceCommands.currentBattle(fixture.state()).events().getLast();
        return event.resolution().rolls().stream().map(Roll::value).toList();
    }

    private static CampaignSpaceData.Monster monster(Fixture fixture, UUID id) {
        return fixture.state().space().monsters().stream().filter(monster -> monster.id().equals(id)).findFirst().orElseThrow();
    }

    private static CampaignDomainData.Position position(Fixture fixture, UUID id) {
        return CampaignSpaceCommands.position(fixture.state(), id);
    }

    private static long slots(Fixture fixture, UUID wizard) {
        return fixture.card(wizard).resources().stream().filter(r -> r.identity().ruleKey().equals("resource.wizard.slot1")).findFirst().orElseThrow().current();
    }

    private static Position cell(int x, int y) { return new Position(x, y); }

    private static String id() { return UUID.randomUUID().toString(); }

    private static UUID subject(Fixture fixture, String action, JsonObject input) {
        return UUID.fromString(CampaignSpaceFixtures.command(fixture.service, action, input).lastCommand().subjectId());
    }

    private static JsonObject fields(Object... pairs) {
        var input = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) input.addProperty((String) pairs[i], pairs[i + 1].toString());
        return input;
    }

    private static void assertCode(String code, Executable call) {
        var error = assertThrows(StorageException.class, call);
        assertEquals(code, error.code(), error.getMessage());
    }
}
