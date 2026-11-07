package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.storage.CampaignCheckEffects.*;
import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.dndtool.service.storage.CampaignDomainData.*;
import com.dndtool.service.storage.CampaignDomainData.Character;
import static org.junit.jupiter.api.Assertions.*;

class CampaignCheckEffectsTest {
    @TempDir Path temporary;

    @Test void commonSqliteTransactionRollsBackAllEffectsAndWholeFileReopensItsAudit() throws Exception {
        boolean[] fail = {false};
        try (var service = new CampaignStorageService(temporary.resolve("conditional"), boundary -> {
            if (fail[0] && boundary == CampaignStorageService.Boundary.AFTER_CHARACTER_UPDATE) throw new IllegalStateException("conditional fault");
        })) {
            service.create("共同事务", List.of(CampaignRules.BASE, CampaignRules.HEALING, CampaignRules.RUNTIME));
            storageCommand(service, "character-create", object("name", "甲", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
            storageCommand(service, "character-create", object("name", "乙", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
            UUID executor = UUID.fromString(viewCharacter(service, "甲").get("id").getAsString());
            UUID target = UUID.fromString(viewCharacter(service, "乙").get("id").getAsString());
            storageCommand(service, "node-create", object("name", "营地"));
            UUID first = UUID.fromString(service.current().domain().getAsJsonArray("nodes").get(0).getAsJsonObject().get("id").getAsString());
            storageCommand(service, "node-create", object("name", "洞穴"));
            UUID second = service.current().domain().getAsJsonArray("nodes").asList().stream().map(JsonElement::getAsJsonObject)
                    .map(n -> UUID.fromString(n.get("id").getAsString())).filter(id -> !id.equals(first)).findFirst().orElseThrow();
            storageCommand(service, "position", object("characterId", target.toString(), "nodeId", first.toString()));
            JsonObject itemInput = object("characterId", target.toString(), "name", "绳索", "quantity", "3"); itemInput.add("itemId", JsonNull.INSTANCE);
            storageCommand(service, "item", itemInput);
            UUID item = UUID.fromString(service.current().domain().getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString());
            JsonObject input = object("characterId", executor.toString(), "characterVersion", viewCharacter(service, "甲").get("version").getAsString());
            input.add("command", CampaignDomainCodec.toJson(check(0)));
            input.add("success", branch(target, "vital_effect.dm_damage", item, "2", second, "成功消息"));
            input.add("failure", branch(target, "vital_effect.dm_healing", item, "-1", first, "失败消息"));
            var before = service.current(); fail[0] = true;
            assertThrows(RuntimeException.class, () -> storageCommand(service, "check-effects", input));
            fail[0] = false;
            assertEquals(before.domain(), service.current().domain()); assertEquals(before.revision(), service.current().revision());
            String request = UUID.randomUUID().toString();
            var accepted = service.domainCommand(request, before.epoch(), before.revision(), "check-effects", input);
            assertEquals(6, viewCharacter(service, "乙").get("hitPoints").getAsLong());
            assertEquals(5, accepted.domain().getAsJsonArray("items").get(0).getAsJsonObject().get("quantity").getAsLong());
            assertTrue(service.domainCommand(request, before.epoch(), before.revision(), "check-effects", input).lastCommand().replayed());
            assertEquals(accepted.domain(), service.current().domain());
            String file = service.end(UUID.randomUUID().toString(), service.current().epoch(), service.current().revision()).fileKey();
            var opened = service.open(file);
            assertEquals(accepted.domain(), opened.domain()); assertEquals(accepted.history(), opened.history());
        }
    }

    @Test void oneActualCheckAppliesTheSuccessBranchAndPreservesTypedAdjacentAudit() {
        Fixture f = new Fixture(); var before = f.snapshot; UUID operation = UUID.randomUUID();
        List<Integer> sides = new ArrayList<>();
        Result result = CampaignCheckEffects.resolve(before, f.input(10), operation, die -> { sides.add(die); return 20; });
        assertEquals(List.of(20), sides);
        assertEquals(before.domains().roots(), result.state().roots());
        assertEquals(f.a, result.subjectId()); assertTrue(result.audit().success());
        assertEquals(6, character(result.state(), f.b).history().current().hitPoints());
        assertEquals(5, item(result.state(), f.item).quantity());
        assertEquals(f.secondNode, position(result.state(), f.b).nodeId());
        assertEquals("成功消息", result.audit().message());
        assertEquals(2, character(result.state(), f.a).history().events().size());
        assertEquals(2, character(result.state(), f.b).history().events().size());
        assertEquals(result.audit(), CampaignDomainCodec.decode(CampaignDomainCodec.encode(result.audit()), Audit.class));

        State retained = f.accept(result, operation);
        var items = items(before.domains()); var positions = positions(before.domains());
        CampaignCheckEffects.validateAudit(retained, retained.roots().getLast(), result.audit(), nodes(before.domains()), items, positions);
        assertEquals(item(result.state(), f.item), items.get(f.item));
        assertEquals(position(result.state(), f.b), positions.get(f.b));
        assertCommonClock(retained);
    }

    @Test void failureUsesOnlyItsDeclaredHealthInventoryPositionAndMessage() {
        Fixture f = new Fixture(); f.damage(f.b);
        long initialHp = character(f.snapshot.domains(), f.b).history().current().hitPoints();
        List<Integer> draws = new ArrayList<>();
        Result result = CampaignCheckEffects.resolve(f.snapshot, f.input(10), UUID.randomUUID(), die -> { draws.add(die); return 1; });
        assertEquals(List.of(20), draws); assertFalse(result.audit().success());
        assertEquals(initialHp + 5, character(result.state(), f.b).history().current().hitPoints());
        assertEquals(2, item(result.state(), f.item).quantity());
        assertEquals(f.firstNode, position(result.state(), f.b).nodeId());
        assertEquals("失败消息", result.audit().message());
    }

    @Test void invalidUnselectedBranchesAndStaleExecutorRejectBeforeAnyDie() {
        Fixture f = new Fixture(); JsonObject original = f.input(0);
        List<JsonObject> invalid = new ArrayList<>();
        JsonObject unknownNode = original.deepCopy(); unknownNode.getAsJsonObject("failure").addProperty("nodeId", UUID.randomUUID().toString()); invalid.add(unknownNode);
        JsonObject underflow = original.deepCopy(); underflow.getAsJsonObject("failure").addProperty("itemDelta", "-4"); invalid.add(underflow);
        JsonObject overflow = original.deepCopy(); overflow.getAsJsonObject("failure").addProperty("itemDelta", "1000000"); invalid.add(overflow);
        JsonObject wrongTarget = original.deepCopy(); wrongTarget.getAsJsonObject("failure").addProperty("targetId", f.a.toString()); invalid.add(wrongTarget);
        JsonObject badRule = original.deepCopy(); badRule.getAsJsonObject("failure").add("healthEffect", CampaignDomainCodec.toJson(
                new RuleIdentity(BundledCampaignRules.HOUSE_RULE_MODULE, "adventure.vital_effect", "vital_effect.unknown"))); invalid.add(badRule);
        JsonObject stale = original.deepCopy(); stale.addProperty("characterVersion", "1"); invalid.add(stale);
        JsonObject extra = original.deepCopy(); extra.getAsJsonObject("failure").addProperty("selectedD20", "20"); invalid.add(extra);
        for (JsonObject input : invalid) {
            int[] dice = {0};
            assertThrows(RuntimeException.class, () -> CampaignCheckEffects.resolve(f.snapshot, input, UUID.randomUUID(), sides -> { dice[0]++; return 20; }));
            assertEquals(0, dice[0]); assertEquals(3, item(f.snapshot.domains(), f.item).quantity());
        }
    }

    @Test void sameCharacterHealthHasConsecutiveVersionsAndAuditRejectsForgeryWithoutChangingMaps() {
        Fixture f = new Fixture(); JsonObject input = f.input(0);
        for (String branch : List.of("success", "failure")) {
            JsonObject value = input.getAsJsonObject(branch); value.addProperty("targetId", f.a.toString());
            value.add("itemId", JsonNull.INSTANCE); value.addProperty("itemDelta", "0");
        }
        UUID operation = UUID.randomUUID(); Result result = CampaignCheckEffects.resolve(f.snapshot, input, operation, sides -> 20);
        var history = character(result.state(), f.a).history();
        assertEquals(List.of("INITIALIZED", "CHECK_RESOLVED", "HEALTH_EFFECT_RESOLVED"), history.events().stream().map(Schema2CharacterRuntime.Event::kind).toList());
        assertEquals(history.events().get(1).after(), history.events().getLast().before());
        State retained = f.accept(result, operation); Root root = retained.roots().getLast();
        Audit good = result.audit(); Audit forged = new Audit(good.input(), good.characterVersions(), good.checkOperationId(), good.healthOperationId(),
                !good.success(), good.itemBefore(), good.itemAfter(), good.positionBefore(), good.positionAfter(), good.message());
        Root altered = new Root(root.sequence(), root.operationId(), root.kind(), root.subjectId(), root.intent(), root.before(),
                CampaignDomainCodec.text(forged), root.characterOperations(), null, root.actor());
        List<Root> roots = new ArrayList<>(retained.roots()); roots.set(roots.size() - 1, altered);
        State bad = new State(retained.material(), retained.characters(), retained.combats(), retained.nodes(), retained.positions(), retained.items(), roots);
        var items = items(f.snapshot.domains()); var positions = positions(f.snapshot.domains());
        var oldItems = new HashMap<>(items); var oldPositions = new HashMap<>(positions);
        assertThrows(RuntimeException.class, () -> CampaignCheckEffects.validateAudit(bad, altered, forged, nodes(f.snapshot.domains()), items, positions));
        assertEquals(oldItems, items); assertEquals(oldPositions, positions);
    }

    @Test void retainedAuditUsesTheHistoricalMetadataPrefixRatherThanFutureCurrentValues() {
        Fixture f = new Fixture(); var original = f.snapshot;
        UUID operation = UUID.randomUUID(); Result result = CampaignCheckEffects.resolve(original, f.input(0), operation, sides -> 20);
        State accepted = f.accept(result, operation); Root check = accepted.roots().getLast();
        f.snapshot = snapshot(original.campaignId(), accepted);
        f.apply("item", object("itemId", f.item.toString(), "characterId", f.b.toString(), "name", "绳索", "quantity", "99"));
        var items = items(original.domains()); var positions = positions(original.domains());
        CampaignCheckEffects.validateAudit(f.snapshot.domains(), check, result.audit(), nodes(original.domains()), items, positions);
        assertEquals(5, items.get(f.item).quantity()); assertEquals(99, item(f.snapshot.domains(), f.item).quantity());
    }

    @Test void inactiveDeclaredTargetAndAnActiveSceneBlockBeforeDice() {
        Fixture inactive = new Fixture(); JsonObject profile = object("characterId", inactive.b.toString(), "name", "乙", "kind", "PC");
        profile.addProperty("active", false); inactive.apply("character-profile", profile);
        assertThrows(RuntimeException.class, () -> CampaignCheckEffects.resolve(inactive.snapshot, inactive.input(0), UUID.randomUUID(),
                sides -> { throw new AssertionError("Inactive branch reached RNG"); }));

        Fixture scene = new Fixture(); JsonObject start = object("combatVersion", "-1"); start.add("combatId", JsonNull.INSTANCE);
        start.add("command", CampaignDomainCodec.toJson(com.dndtool.service.Schema2CombatState.Command.start(List.of(
                new com.dndtool.service.Schema2CombatState.Deployment(scene.a, new com.dndtool.service.Schema2CombatState.Position(0, 0), 0),
                new com.dndtool.service.Schema2CombatState.Deployment(scene.b, new com.dndtool.service.Schema2CombatState.Position(1, 0), 1)), List.of(scene.a, scene.b))));
        scene.apply("combat-command", start);
        assertThrows(RuntimeException.class, () -> CampaignCheckEffects.resolve(scene.snapshot, scene.input(0), UUID.randomUUID(),
                sides -> { throw new AssertionError("Active scene reached ordinary RNG"); }));
    }

    @Test void unselectedHealthBranchReservesItsTargetHistoryBeforeTheCheckDie() {
        Fixture f = new Fixture();
        Command check = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null,
                new CheckInput(BundledCampaignRules.rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, 0, false, false, "DM确认有限检定。"));
        for (int i = 1; i < Schema2CharacterRuntime.MAX_EVENTS; i++) {
            JsonObject input = object("characterId", f.b.toString(), "characterVersion", Long.toString(character(f.snapshot.domains(), f.b).history().current().version()));
            input.add("command", CampaignDomainCodec.toJson(check)); f.apply("character-command", input);
        }
        assertEquals(Schema2CharacterRuntime.MAX_EVENTS, character(f.snapshot.domains(), f.b).history().events().size());
        JsonObject input = f.input(0);
        input.getAsJsonObject("success").add("healthEffect", JsonNull.INSTANCE);
        assertThrows(RuntimeException.class, () -> CampaignCheckEffects.resolve(f.snapshot, input, UUID.randomUUID(),
                sides -> { throw new AssertionError("Full unselected target reached RNG"); }));
    }

    private static final class Fixture {
        CampaignData.Snapshot snapshot = snapshot(UUID.randomUUID().toString(), State.initial(BundledCampaignRules.projection()));
        UUID a, b, firstNode, secondNode, item;
        Fixture() {
            apply("character-create", object("name", "甲", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
            a = snapshot.domains().characters().getFirst().id();
            apply("character-create", object("name", "乙", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
            b = snapshot.domains().characters().stream().filter(c -> !c.id().equals(a)).findFirst().orElseThrow().id();
            apply("node-create", object("name", "营地")); firstNode = snapshot.domains().nodes().getFirst().id();
            apply("node-create", object("name", "洞穴")); secondNode = snapshot.domains().nodes().stream().filter(n -> !n.id().equals(firstNode)).findFirst().orElseThrow().id();
            apply("position", object("characterId", b.toString(), "nodeId", firstNode.toString()));
            JsonObject itemInput = object("characterId", b.toString(), "name", "绳索", "quantity", "3"); itemInput.add("itemId", JsonNull.INSTANCE);
            apply("item", itemInput); item = snapshot.domains().items().getFirst().id();
        }
        void apply(String action, JsonObject input) {
            State state = new CampaignDomainCommands().apply(snapshot, action, input, UUID.randomUUID(), "0".repeat(64), sides -> 10);
            snapshot = snapshot(snapshot.campaignId(), state);
        }
        void damage(UUID target) {
            var health = new Command("HEALTH_EFFECT_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null,
                    new Schema2CharacterRuntime.HealthInput(BundledCampaignRules.rule("adventure.health", "health.srd2014"),
                            BundledCampaignRules.rule("adventure.vital_effect", "vital_effect.dm_damage"), null, "DM确认直接环境伤害。"));
            JsonObject input = object("characterId", target.toString(), "characterVersion", Long.toString(character(snapshot.domains(), target).history().current().version()));
            input.add("command", CampaignDomainCodec.toJson(health)); apply("character-command", input);
        }
        JsonObject input(int difficulty) {
            JsonObject input = object("characterId", a.toString(), "characterVersion", Long.toString(character(snapshot.domains(), a).history().current().version()));
            input.add("command", CampaignDomainCodec.toJson(check(difficulty)));
            input.add("success", branch(b, "vital_effect.dm_damage", item, "2", secondNode, "成功消息"));
            input.add("failure", branch(b, "vital_effect.dm_healing", item, "-1", firstNode, "失败消息"));
            return input;
        }
        State accept(Result result, UUID operation) {
            Set<UUID> old = new HashSet<>(); snapshot.domains().characters().forEach(c -> c.history().events().forEach(e -> old.add(e.operationId())));
            var children = result.state().characters().stream().flatMap(c -> c.history().events().stream()).map(Schema2CharacterRuntime.Event::operationId)
                    .filter(id -> !old.contains(id)).sorted(Comparator.comparing(UUID::toString)).toList();
            List<Root> roots = new ArrayList<>(result.state().roots());
            roots.add(new Root(roots.size() + 1L, operation, "check-effects", result.subjectId(), "0".repeat(64), "", CampaignDomainCodec.text(result.audit()), children, null, CampaignParticipationData.Actor.dm(result.subjectId(), null)));
            State state = result.state(); return new State(state.material(), state.characters(), state.combats(), state.nodes(), state.positions(), state.items(), roots);
        }
    }
    private static CampaignData.Snapshot snapshot(String id, State state) {
        return new CampaignData.Snapshot(id, "分支检定", List.of(CampaignRules.BASE, CampaignRules.HEALING, CampaignRules.RUNTIME), List.of(),
                state.roots().stream().map(CampaignDomainValidation::summary).toList(), state);
    }
    private static Command check(int difficulty) {
        return new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null,
                new CheckInput(BundledCampaignRules.rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, difficulty, false, false, "DM事前确认检定与两个结果分支。"));
    }
    private static void storageCommand(CampaignStorageService service, String action, JsonObject input) {
        var current = service.current(); service.domainCommand(UUID.randomUUID().toString(), current.epoch(), current.revision(), action, input);
    }
    private static JsonObject viewCharacter(CampaignStorageService service, String name) {
        return service.current().domain().getAsJsonArray("characters").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(c -> c.get("name").getAsString().equals(name)).findFirst().orElseThrow();
    }
    private static JsonObject object(String... pairs) { JsonObject result = new JsonObject(); for (int i = 0; i < pairs.length; i += 2) result.addProperty(pairs[i], pairs[i + 1]); return result; }
    private static JsonObject branch(UUID target, String health, UUID item, String delta, UUID node, String message) {
        JsonObject result = object("targetId", target.toString(), "itemDelta", delta, "message", message);
        result.add("healthEffect", health == null ? JsonNull.INSTANCE : CampaignDomainCodec.toJson(BundledCampaignRules.rule("adventure.vital_effect", health)));
        result.add("itemId", item == null ? JsonNull.INSTANCE : new JsonPrimitive(item.toString())); result.add("nodeId", node == null ? JsonNull.INSTANCE : new JsonPrimitive(node.toString())); return result;
    }
    private static Character character(State state, UUID id) { return state.characters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(); }
    private static Item item(State state, UUID id) { return state.items().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow(); }
    private static Position position(State state, UUID id) { return state.positions().stream().filter(p -> p.characterId().equals(id)).findFirst().orElseThrow(); }
    private static Map<UUID, Node> nodes(State state) { Map<UUID, Node> map = new HashMap<>(); state.nodes().forEach(n -> map.put(n.id(), n)); return map; }
    private static Map<UUID, Item> items(State state) { Map<UUID, Item> map = new HashMap<>(); state.items().forEach(i -> map.put(i.id(), i)); return map; }
    private static Map<UUID, Position> positions(State state) { Map<UUID, Position> map = new HashMap<>(); state.positions().forEach(p -> map.put(p.characterId(), p)); return map; }
    private static void assertCommonClock(State state) {
        var clock = state.characters().getFirst().history().current().rest();
        for (var c : state.characters()) { assertEquals(clock.minute(), c.history().current().rest().minute()); assertEquals(clock.second(), c.history().current().rest().second()); }
    }
}
