package com.dndtool.service.storage;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CombatState.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

import static com.dndtool.service.storage.CampaignSpaceData.Ref;
import static org.junit.jupiter.api.Assertions.*;

class CampaignMonsterSpaceIntegrationTest {
    private static final IntUnaryOperator NO_DICE = sides -> { throw new AssertionError("Unexpected d" + sides); };
    private final CampaignDomainCommands commands = new CampaignDomainCommands();
    private CampaignData.Snapshot snapshot = CampaignRuleFixtures.snapshot(UUID.randomUUID().toString(), "怪物交接");

    @Test void damagedMonsterRetainsIdentityAndSpentRechargeAcrossScenesAndFile() {
        UUID node = apply("node-create", input("name", "营地"));
        focus(Ref.world(node));
        UUID fighter = apply("character-create", input("name", "战士", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
        apply("position", input("characterId", fighter, "nodeId", node));
        UUID sword = UUID.randomUUID();
        var swordRule = CampaignRuleFixtures.rules().rule("equipment.item", "item.longsword");
        equipment(fighter, sword, "ITEM_ACQUIRED", new EquipmentInput(sword, swordRule, "CARRIED", "DM确认长剑"));
        equipment(fighter, UUID.randomUUID(), "ITEM_EQUIPPED", new EquipmentInput(sword, null, "MAIN_HAND", "DM确认主手"));
        apply("space-time", input("minutes", 2, "adjudication", "战士在营地准备"));

        var rule = CampaignRuleFixtures.rules().rule("adventure.monster", "monster.hell_hound");
        var create = input("name", "地狱犬");
        create.add("monster", CampaignDomainCodec.toJson(rule));
        UUID hound = apply("monster-create", create);
        long fullHp = monster(hound).hitPoints();
        UUID first = scene(node, "第一处");
        enter(first, hound, fighter);
        assertEquals(120, characterSeconds());
        assertEquals(0, state().space().clock(Ref.scene(first)).second());

        var initiatives = List.of(20, 1).iterator();
        combat(Command.reenter(fighter, List.of(fighter, hound)), sides -> initiatives.next());
        assertEquals(fighter, battle().activeActor());
        combat(Command.action(Kind.BEGIN_ATTACK, fighter), NO_DICE);
        combat(Command.attack(fighter, hound, sword), sides -> sides == 20 ? 20 : 1);
        assertTrue(monster(hound).hitPoints() < fullHp);
        assertTrue(monster(hound).hitPoints() > 0);
        combat(Command.action(Kind.END_TURN, fighter), NO_DICE);
        assertEquals(hound, battle().activeActor());
        combat(Command.breath(hound, new EntryIdentity(rule, "actions", rule.moduleKey(), "fire_breath"), 0), sides -> 1);
        var pending = battle().activeFlow();
        assertNotNull(pending);
        assertFalse(monster(hound).actions().getFirst().available());
        combat(Command.continueBreath(hound, pending.flowKey(), pending.leafFrameKey()), sides -> 20);
        assertTrue(state().characters().getFirst().history().current().hitPoints() > 0);
        assertNull(battle().activeFlow());
        combat(Command.action(Kind.END_COMBAT, hound), NO_DICE);
        var damagedAndSpent = monster(hound);
        assertEquals(126, characterSeconds());
        assertEquals(6, state().space().clock(Ref.scene(first)).second());

        leave(hound, fighter);
        assertEquals(Ref.world(node), position(hound).space());
        UUID second = scene(node, "第二处");
        enter(second, hound, fighter);
        assertEquals(damagedAndSpent, monster(hound));
        assertEquals(Ref.scene(second), position(hound).space());
        var deployed = battle().monsters().getFirst();
        assertEquals(hound, deployed.monsterId());
        assertEquals(damagedAndSpent.hitPoints(), deployed.hitPoints());
        assertFalse(deployed.actionUses().getFirst().available());
        assertEquals(126, characterSeconds());
        assertEquals(0, state().space().clock(Ref.scene(second)).second());

        var codec = new CampaignCodec();
        var document = new CampaignData.Document(UUID.randomUUID().toString(), snapshot);
        var decoded = codec.decode(codec.encode(document));
        assertEquals(document, decoded);
        snapshot = decoded.snapshot();

        leave(hound, fighter);
        focus(Ref.scene(second));
        assertTrue(battle().combatants().isEmpty());
        apply("scene-close", new JsonObject());
        assertEquals("CLOSED", state().space().scene(second).mode());
        assertEquals(Ref.world(node), state().space().focus().space());
        assertEquals(damagedAndSpent, monster(hound));
        assertEquals(Ref.world(node), position(hound).space());
    }

    private UUID scene(UUID node, String name) {
        return apply("scene-create", input("nodeId", node, "name", name, "resolution", "RULES", "width", 5, "height", 5));
    }

    private void enter(UUID scene, UUID hound, UUID fighter) {
        var request = input("sceneId", scene);
        var subjects = new JsonArray();
        subjects.add(input("id", hound, "version", position(hound).version(), "x", 0, "y", 0, "side", 1));
        subjects.add(input("id", fighter, "version", position(fighter).version(), "x", 1, "y", 0, "side", 0));
        request.add("subjects", subjects);
        apply("scene-enter", request);
    }

    private void leave(UUID... subjects) {
        var request = new JsonObject();
        var rows = new JsonArray();
        for (UUID id : subjects) rows.add(input("id", id, "version", position(id).version()));
        request.add("subjects", rows);
        apply("scene-leave", request);
    }

    private void focus(Ref target) { apply("focus-set", CampaignSpaceFixtures.target(target)); }

    private void combat(Command command, IntUnaryOperator dice) {
        Battle before = battle();
        var request = input("combatId", before.combatId(), "combatVersion", before.version());
        request.add("command", CampaignDomainCodec.toJson(command));
        apply("combat-command", request, UUID.randomUUID(), dice);
    }

    private void equipment(UUID fighter, UUID operation, String kind, EquipmentInput equipment) {
        var request = input("characterId", fighter, "characterVersion", state().characters().getFirst().history().current().version());
        request.add("command", CampaignDomainCodec.toJson(new Schema2CharacterRuntime.Command(kind, null, 0,
                null, null, null, GrowthChoices.EMPTY, null, null, equipment, null)));
        apply("character-command", request, operation, NO_DICE);
    }

    private UUID apply(String action, JsonObject request) { return apply(action, request, UUID.randomUUID(), NO_DICE); }

    private UUID apply(String action, JsonObject request, UUID operation, IntUnaryOperator dice) {
        var after = commands.apply(snapshot, action, CampaignSpaceFixtures.focused(state(), request), operation, "0".repeat(64), dice);
        snapshot = new CampaignData.Snapshot(snapshot.campaignId(), snapshot.name(), after);
        return after.roots().getLast().subjectId();
    }

    private CampaignDomainData.State state() { return snapshot.domains(); }
    private Battle battle() { return CampaignSpaceCommands.currentBattle(state()).current(); }
    private CampaignDomainData.Position position(UUID subject) { return CampaignSpaceCommands.position(state(), subject); }
    private CampaignSpaceData.Monster monster(UUID id) {
        return state().space().monsters().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }
    private long characterSeconds() {
        var rest = state().characters().getFirst().history().current().rest();
        return rest.minute() * 60 + rest.second();
    }
    private static JsonObject input(Object... pairs) {
        var value = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) value.addProperty((String) pairs[i], pairs[i + 1].toString());
        return value;
    }
}
