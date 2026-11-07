package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CombatState.*;
import com.google.gson.*;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Production bundles, public campaign commands, real SQLite and the normal complete-file lifecycle. */
class CampaignDomainsCombatIntegrationTest {
    @TempDir Path temporary;
    private static final Gson VIEW = new Gson();

    @Test void pendingOpportunitySurvivesEndAndOpenThenMovementAndAttackUseTheSameHealthOwner() throws Exception {
        try (var service = new CampaignStorageService(temporary.resolve("opportunity"))) {
            CampaignRuleFixtures.create(service, "真实战斗");
            UUID first = character(service, "甲", "fighter"), second = character(service, "乙", "fighter");
            Map<UUID, UUID> swords = Map.of(first, equip(service, first, "item.longsword", "MAIN_HAND"),
                    second, equip(service, second, "item.longsword", "MAIN_HAND"));
            startScene(service, List.of(new Deployment(first, new Position(1, 0), 0),
                    new Deployment(second, new Position(2, 0), 1)), List.of());
            UUID mover = battle(service).activeActor(), defender = mover.equals(first) ? second : first;
            Position original = actor(battle(service), mover).position();
            Position away = new Position(mover.equals(first) ? 0 : 3, 0);
            combat(service, Command.move(mover, List.of(away)));
            Flow pending = battle(service).activeFlow();
            assertNotNull(pending); assertEquals(WindowStatus.OPEN, pending.windows().getLast().status());
            assertEquals(original, actor(battle(service), mover).position());
            assertEquals(0, actor(battle(service), mover).movementSpentHalfFeet());
            var before = service.current();
            String file = service.end(uuid(), before.epoch(), before.revision()).fileKey();
            var opened = service.open(file);
            assertEquals(before.domain(), opened.domain()); assertEquals(before.history(), opened.history());
            assertEquals(pending, battle(service).activeFlow());

            Window window = pending.windows().getLast();
            Opportunity opportunity = window.opportunities().stream().filter(o -> o.status() == OpportunityStatus.PENDING).findFirst().orElseThrow();
            assertEquals(defender, opportunity.actorId());
            combat(service, Command.reaction(Kind.PASS_REACTION, defender, null, pending.flowKey(), pending.leafFrameKey(),
                    window.windowKey(), opportunity.opportunityKey()));
            Flow closed = battle(service).activeFlow();
            combat(service, Command.continuation(mover, closed.flowKey(), closed.leafFrameKey(), closed.windows().getLast().windowKey()));
            assertNull(battle(service).activeFlow()); assertEquals(away, actor(battle(service), mover).position());
            assertEquals(10, actor(battle(service), mover).movementSpentHalfFeet());

            combat(service, Command.move(mover, List.of(original)));
            assertEquals(20, actor(battle(service), mover).movementSpentHalfFeet());
            long hitPoints = card(service, defender).get("hitPoints").getAsLong();
            combat(service, Command.action(Kind.BEGIN_ATTACK, mover));
            combat(service, Command.attack(mover, defender, swords.get(mover)));
            var attack = battle(service).flows().getLast().attacks().getFirst();
            assertTrue(attack.firstD20() >= 1 && attack.firstD20() <= 20);
            assertEquals(Math.max(0, hitPoints - attack.damageAmount()), card(service, defender).get("hitPoints").getAsLong());
            assertHealthOwners(service,temporary.resolve("opportunity")); assertCommonClock(service);
            JsonObject stable = service.current().domain();
            String secondFile = service.end(uuid(), service.current().epoch(), service.current().revision()).fileKey();
            assertEquals(stable, service.open(secondFile).domain());
        }
    }

    @Test void realBlessCostAndSharedMonsterDamageSurviveTheRolledPendingCheckpoint() throws Exception {
        try (var service = new CampaignStorageService(temporary.resolve("breath"))) {
            CampaignRuleFixtures.create(service, "共享伤害");
            UUID cleric = character(service, "牧师", "cleric"), fighter = character(service, "战士", "fighter");
            UUID focus = equip(service, cleric, "item.holy_symbol", "OFF_HAND");
            UUID hound = UUID.randomUUID();
            var monster = CampaignRuleFixtures.rules().rule("adventure.monster", "monster.hell_hound");
            startScene(service, List.of(new Deployment(cleric, new Position(1, 0), 0),
                            new Deployment(fighter, new Position(2, 0), 0)),
                    List.of(new MonsterDeployment(hound, monster, new Position(0, 0), 1)));
            advanceTo(service, cleric);
            long slots = clericSlots(service, cleric);
            combat(service, Command.cast(cleric, new CastInput(CampaignRuleFixtures.rules().rule("character.spellcasting", "spellcasting.life_domain"),
                    CampaignRuleFixtures.rules().rule("adventure.spell", "spell.bless"), List.of(cleric, fighter), focus)));
            assertEquals(slots - 1, clericSlots(service, cleric)); assertEquals(1, battle(service).ongoingEffects().size());
            advanceTo(service, hound);
            combat(service, Command.breath(hound, new EntryIdentity(monster, "actions", monster.moduleKey(), "fire_breath"), 0));
            var rolled = battle(service).saveDamageBatches().getFirst();
            assertEquals(BatchStatus.ROLLED, rolled.status()); assertEquals(6, rolled.sharedRoll().dice().size());
            assertTrue(rolled.sharedRoll().dice().stream().allMatch(die -> die >= 1 && die <= 6));
            assertEquals(rolled.sharedRoll().dice().stream().mapToInt(Integer::intValue).sum(), rolled.sharedRoll().rawTotal());
            assertEquals(Set.of(cleric, fighter), new HashSet<>(rolled.targetProof().targets().stream().map(CreatureRef::creatureId).toList()));
            assertTrue(rolled.checks().isEmpty()); assertTrue(rolled.packets().isEmpty());
            Map<UUID, Long> healthBefore = Map.of(cleric, card(service, cleric).get("hitPoints").getAsLong(),
                    fighter, card(service, fighter).get("hitPoints").getAsLong());
            var before = service.current(); String file = service.end(uuid(), before.epoch(), before.revision()).fileKey();
            assertEquals(before.domain(), service.open(file).domain());
            assertEquals(rolled, battle(service).saveDamageBatches().getFirst()); assertEquals(slots - 1, clericSlots(service, cleric));
            Flow pending = battle(service).activeFlow();
            combat(service, Command.continueBreath(hound, pending.flowKey(), pending.leafFrameKey()));
            var applied = battle(service).saveDamageBatches().getFirst();
            assertEquals(BatchStatus.COMPLETED, applied.status()); assertEquals(rolled.sharedRoll(), applied.sharedRoll());
            assertEquals(2, applied.packets().size());
            for (var packet : applied.packets()) {
                UUID target = applied.targetProof().targets().get(packet.targetOrdinal()).creatureId();
                assertEquals(healthBefore.get(target).longValue(), packet.beforeHitPoints());
                assertEquals(packet.afterHitPoints(), card(service, target).get("hitPoints").getAsLong());
            }
            assertEquals(slots - 1, clericSlots(service, cleric));
            assertFalse(battle(service).monsters().getFirst().actionUses().getFirst().available());
            assertHealthOwners(service,temporary.resolve("breath")); assertCommonClock(service);
            var accepted = service.current(); String completed = service.end(uuid(), accepted.epoch(), accepted.revision()).fileKey();
            assertEquals(accepted.domain(), service.open(completed).domain());
        }
    }

    private static String uuid() { return UUID.randomUUID().toString(); }
    private static JsonObject input(String... values) {
        JsonObject object = new JsonObject(); for (int i = 0; i < values.length; i += 2) object.addProperty(values[i], values[i + 1]); return object;
    }
    private static void command(CampaignStorageService service, String action, JsonObject input) { command(service, uuid(), action, input); }
    private static void command(CampaignStorageService service, String request, String action, JsonObject input) {
        var current = service.current(); input.add("focus", CampaignSpaceCommands.focusPayload(service.accessSnapshot().snapshot().domains()));
        service.domainCommand(request, current.epoch(), current.revision(), action, input);
    }
    private static UUID character(CampaignStorageService service, String name, String profession) {
        command(service, "character-create", input("name", name, "kind", "PC", "classKey", profession, "backgroundKey", "acolyte"));
        return UUID.fromString(service.current().domain().getAsJsonArray("characters").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(card -> card.get("name").getAsString().equals(name)).findFirst().orElseThrow().get("id").getAsString());
    }
    private static JsonObject card(CampaignStorageService service, UUID id) {
        return service.current().domain().getAsJsonArray("characters").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(card -> card.get("id").getAsString().equals(id.toString())).findFirst().orElseThrow();
    }
    private static UUID equip(CampaignStorageService service, UUID character, String item, String location) {
        UUID operation = UUID.randomUUID();
        characterCommand(service, character, new Schema2CharacterRuntime.Command("ITEM_ACQUIRED", null, 0, null, null, null,
                GrowthChoices.EMPTY, null, null, new EquipmentInput(operation, CampaignRuleFixtures.rules().rule("equipment.item", item), "CARRIED", "DM确认物品归属。"), null), operation.toString());
        characterCommand(service, character, new Schema2CharacterRuntime.Command("ITEM_EQUIPPED", null, 0, null, null, null,
                GrowthChoices.EMPTY, null, null, new EquipmentInput(operation, null, location, "DM确认非战斗装备准备。"), null), uuid());
        return operation;
    }
    private static void characterCommand(CampaignStorageService service, UUID character, Schema2CharacterRuntime.Command command, String operation) {
        JsonObject input = input("characterId", character.toString(), "characterVersion", card(service, character).get("version").getAsString());
        input.add("command", CampaignDomainCodec.toJson(command)); command(service, operation, "character-command", input);
    }
    private static Battle battle(CampaignStorageService service) {
        return VIEW.fromJson(service.current().domain().getAsJsonArray("combats").get(0).getAsJsonObject().get("current"), Battle.class);
    }
    private static Combatant actor(Battle battle, UUID id) { return battle.combatants().stream().filter(actor -> actor.characterId().equals(id)).findFirst().orElseThrow(); }
    private static void advanceTo(CampaignStorageService service, UUID actor) {
        int participants = battle(service).initiative().size();
        for (int i = 0; i < participants && !battle(service).activeActor().equals(actor); i++)
            combat(service, Command.action(Kind.END_TURN, battle(service).activeActor()));
        assertEquals(actor, battle(service).activeActor());
    }
    private static void startScene(CampaignStorageService service, List<Deployment> characters, List<MonsterDeployment> monsters) {
        String node = uuid(), scene = uuid();
        command(service, node, "node-create", input("name", "场景入口"));
        JsonObject target = new JsonObject(); target.add("target", CampaignSpaceCommands.refJson(CampaignSpaceData.Ref.world(UUID.fromString(node))));
        command(service, "focus-set", target);
        JsonArray subjects = new JsonArray();
        for (var character : characters) {
            command(service, "position", input("characterId", character.characterId().toString(), "nodeId", node));
            subjects.add(input("id", character.characterId().toString(), "version", "0", "x", Integer.toString(character.position().x()), "y", Integer.toString(character.position().y()), "side", Integer.toString(character.side())));
        }
        for (var monster : monsters) {
            JsonObject create = input("name", "猎犬"); create.add("monster", CampaignDomainCodec.toJson(monster.monster()));
            command(service, monster.monsterId().toString(), "monster-create", create);
            subjects.add(input("id", monster.monsterId().toString(), "version", "0", "x", Integer.toString(monster.position().x()), "y", Integer.toString(monster.position().y()), "side", Integer.toString(monster.side())));
        }
        command(service, scene, "scene-create", input("name", "局部场景", "nodeId", node, "width", "20", "height", "20", "resolution", "RULES"));
        JsonObject enter = input("sceneId", scene); enter.add("subjects", subjects); command(service, "scene-enter", enter);
        List<UUID> ties = new ArrayList<>(); characters.forEach(c -> ties.add(c.characterId())); monsters.forEach(m -> ties.add(m.monsterId()));
        combat(service, Command.reenter(characters.getFirst().characterId(), ties));
    }
    private static void combat(CampaignStorageService service, Command command) {
        Battle current = battle(service);
        JsonObject input = input("combatId", current.combatId().toString(), "combatVersion", Long.toString(current.version()));
        input.add("command", CampaignDomainCodec.toJson(command)); command(service, "combat-command", input);
    }
    private static long clericSlots(CampaignStorageService service, UUID id) {
        var resource = CampaignRuleFixtures.rules().rule("character.resource", "resource.cleric.slot1");
        return card(service, id).getAsJsonObject("state").getAsJsonArray("resources").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(r -> r.getAsJsonObject("identity").get("ruleKey").getAsString().equals(resource.ruleKey())).findFirst().orElseThrow().get("current").getAsLong();
    }
    private static void assertHealthOwners(CampaignStorageService service,Path root) throws SQLException {
        JsonArray characters=service.current().domain().getAsJsonArray("characters");assertFalse(characters.isEmpty());
        Map<UUID,Long> actual=new HashMap<>();
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+root.resolve("runs").resolve(service.status().runId()).resolve("run.db"));Statement statement=c.createStatement();ResultSet rows=statement.executeQuery("SELECT character_id,hit_points FROM characters_runtime_character")){
            while(rows.next()){byte[] bytes=rows.getBytes(1);assertEquals(16,bytes.length);ByteBuffer b=ByteBuffer.wrap(bytes);actual.put(new UUID(b.getLong(),b.getLong()),rows.getLong(2));}
        }
        assertEquals(characters.size(),actual.size());
        for(JsonElement e:characters){JsonObject card=e.getAsJsonObject();long hp=card.get("hitPoints").getAsLong();assertEquals(hp,card.getAsJsonObject("state").get("hitPoints").getAsLong());assertEquals(hp,card.getAsJsonObject("history").getAsJsonObject("current").get("hitPoints").getAsLong());assertEquals(hp,actual.get(UUID.fromString(card.get("id").getAsString())).longValue());}
    }
    private static void assertCommonClock(CampaignStorageService service) {
        List<JsonObject> clocks = service.current().domain().getAsJsonArray("characters").asList().stream().map(JsonElement::getAsJsonObject)
                .map(c -> c.getAsJsonObject("state").getAsJsonObject("rest")).toList();
        for (JsonObject clock : clocks) {
            assertEquals(clocks.getFirst().get("minute"), clock.get("minute")); assertEquals(clocks.getFirst().get("second"), clock.get("second"));
        }
    }
}
