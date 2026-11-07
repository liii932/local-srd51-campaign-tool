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
            CampaignRuleFixtures.create(service, "共同事务");
            storageCommand(service, "character-create", object("name", "甲", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
            storageCommand(service, "character-create", object("name", "乙", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
            UUID executor = UUID.fromString(viewCharacter(service, "甲").get("id").getAsString());
            UUID target = UUID.fromString(viewCharacter(service, "乙").get("id").getAsString());
            storageCommand(service, "node-create", object("name", "营地"));
            UUID first = service.accessSnapshot().snapshot().domains().roots().getLast().subjectId();
            storageCommand(service, "focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(first)));
            storageCommand(service, "position", object("characterId", executor.toString(), "nodeId", first.toString()));
            storageCommand(service, "position", object("characterId", target.toString(), "nodeId", first.toString()));
            JsonObject itemInput = object("characterId", target.toString(), "name", "绳索", "quantity", "3"); itemInput.add("itemId", JsonNull.INSTANCE);
            storageCommand(service, "item", itemInput);
            UUID item = UUID.fromString(service.current().domain().getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString());
            JsonObject input = object("characterId", executor.toString(), "characterVersion", viewCharacter(service, "甲").get("version").getAsString());
            input.add("command", CampaignDomainCodec.toJson(check(0)));
            input.add("success", branch(target, "vital_effect.dm_damage", item, "2", "成功消息"));
            input.add("failure", branch(target, "vital_effect.dm_healing", item, "-1", "失败消息"));
            input.add("focus", CampaignSpaceCommands.focusPayload(service.accessSnapshot().snapshot().domains()));
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
        assertEquals(before.domains().positions(), result.state().positions());
        assertEquals("成功消息", result.audit().message());
        assertEquals(2, character(result.state(), f.a).history().events().size());
        assertEquals(2, character(result.state(), f.b).history().events().size());
        assertEquals(result.audit(), CampaignDomainCodec.decode(CampaignDomainCodec.encode(result.audit()), Audit.class));

        State retained = f.accept(f.input(10), operation);
        assertEquals(result.audit(), audit(retained.roots().getLast()));
        assertEquals(result.state().items(), retained.items());
        assertEquals(result.state().positions(), retained.positions());
        assertEquals(before.domains().space(), retained.space());
    }

    @Test void failureUsesOnlyItsDeclaredHealthInventoryAndMessage() {
        Fixture f = new Fixture(); f.damage(f.b);
        long initialHp = character(f.snapshot.domains(), f.b).history().current().hitPoints();
        List<Integer> draws = new ArrayList<>();
        Result result = CampaignCheckEffects.resolve(f.snapshot, f.input(10), UUID.randomUUID(), die -> { draws.add(die); return 1; });
        assertEquals(List.of(20), draws); assertFalse(result.audit().success());
        assertEquals(initialHp + 5, character(result.state(), f.b).history().current().hitPoints());
        assertEquals(2, item(result.state(), f.item).quantity());
        assertEquals(f.snapshot.domains().positions(), result.state().positions());
        assertEquals("失败消息", result.audit().message());
    }

    @Test void invalidUnselectedBranchesAndStaleExecutorRejectBeforeAnyDie() {
        Fixture f = new Fixture(); JsonObject original = f.input(0);
        List<JsonObject> invalid = new ArrayList<>();
        JsonObject removedPosition = original.deepCopy(); removedPosition.getAsJsonObject("failure").addProperty("nodeId", f.secondNode.toString()); invalid.add(removedPosition);
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

    @Test void sameCharacterHealthHasConsecutiveVersionsAndCompleteValidationRejectsOutcomeForgery() {
        Fixture f = new Fixture(); JsonObject input = f.input(0);
        for (String branch : List.of("success", "failure")) {
            JsonObject value = input.getAsJsonObject(branch); value.addProperty("targetId", f.a.toString());
            value.add("itemId", JsonNull.INSTANCE); value.addProperty("itemDelta", "0");
        }
        UUID operation = UUID.randomUUID(); Result result = CampaignCheckEffects.resolve(f.snapshot, input, operation, sides -> 20);
        var history = character(result.state(), f.a).history();
        assertEquals(List.of("INITIALIZED", "CHECK_RESOLVED", "HEALTH_EFFECT_RESOLVED"), history.events().stream().map(Schema2CharacterRuntime.Event::kind).toList());
        assertEquals(history.events().get(1).after(), history.events().getLast().before());
        State retained = f.accept(input, operation); Root root = retained.roots().getLast();
        Audit good = result.audit(); Audit forged = new Audit(good.input(), good.characterVersions(), good.checkOperationId(), good.healthOperationId(),
                !good.success(), good.itemBefore(), good.itemAfter(), good.message());
        Root altered = new Root(root.sequence(), root.operationId(), root.kind(), root.subjectId(), root.intent(), root.before(),
                CampaignDomainCodec.text(forged), root.characterOperations(), null, root.actor(), root.space(), root.rolls());
        List<Root> roots = new ArrayList<>(retained.roots()); roots.set(roots.size() - 1, altered);
        State bad = new State(retained.material(), retained.characters(), retained.combats(), retained.nodes(), retained.positions(),
                retained.items(), roots, retained.manual(), retained.participation(), retained.space());
        assertThrows(StorageException.class, () -> CampaignData.validate(snapshot(f.snapshot.campaignId(), bad)));
    }

    @Test void retainedAuditUsesHistoricalFocusAndItemQuantity() {
        Fixture f = new Fixture(); var original = f.snapshot;
        State accepted = f.accept(f.input(0), UUID.randomUUID()); Root check = accepted.roots().getLast();
        f.snapshot = snapshot(original.campaignId(), accepted);
        f.apply("item", object("itemId", f.item.toString(), "characterId", f.b.toString(), "name", "绳索", "quantity", "99"));
        f.apply("focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(f.secondNode)));
        assertEquals(f.snapshot, CampaignData.validate(f.snapshot));
        assertEquals(5, audit(check).itemAfter().quantity());
        assertEquals(99, item(f.snapshot.domains(), f.item).quantity());
    }

    @Test void inactiveDeclaredTargetAndRuleSceneOwnerBlockBeforeDice() {
        Fixture inactive = new Fixture(); JsonObject profile = object("characterId", inactive.b.toString(), "name", "乙", "kind", "PC");
        inactive.apply("space-unplace", object("characterId", inactive.b.toString(), "version", "0", "adjudication", "退出行动范围。"));
        profile.addProperty("active", false); inactive.apply("character-profile", profile);
        assertThrows(RuntimeException.class, () -> CampaignCheckEffects.resolve(inactive.snapshot, inactive.input(0), UUID.randomUUID(),
                sides -> { throw new AssertionError("Inactive branch reached RNG"); }));

        Fixture scene = new Fixture();
        scene.enter("RULES");
        assertEquals("RULE_SCENE_OWNER_REQUIRED", assertThrows(StorageException.class,
                () -> CampaignCheckEffects.resolve(scene.snapshot, scene.input(0), UUID.randomUUID(),
                        sides -> { throw new AssertionError("Rule scene reached ordinary RNG"); })).code());
    }

    @Test void unselectedBranchCannotAffectABackgroundSubject() {
        Fixture f = new Fixture();
        f.apply("focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(f.secondNode)));
        f.apply("character-create", object("name", "背景角色", "kind", "PC", "classKey", "fighter", "backgroundKey", "acolyte"));
        UUID background = f.snapshot.domains().roots().getLast().subjectId();
        f.place(background, f.secondNode);
        f.apply("focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(f.firstNode)));
        JsonObject input = f.input(0);
        input.add("failure", branch(background, "vital_effect.dm_damage", null, "0", "不能跨空间结算"));
        assertEquals("SPACE_FROZEN", assertThrows(StorageException.class,
                () -> CampaignCheckEffects.resolve(f.snapshot, input, UUID.randomUUID(),
                        sides -> { throw new AssertionError("Cross-space branch reached RNG"); })).code());
        State retained = f.accept(f.input(0), UUID.randomUUID());
        assertEquals(character(f.snapshot.domains(), background), character(retained, background));
        assertEquals(Set.of(f.a, f.b), audit(retained.roots().getLast()).characterVersions().keySet());
    }

    @Test void unselectedHealthBranchReservesItsTargetHistoryBeforeTheCheckDie() {
        Fixture f = new Fixture();
        Command check = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null,
                new CheckInput(CampaignRuleFixtures.rules().rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, 0, false, false, "DM确认有限检定。"));
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
            apply("focus-set", CampaignSpaceFixtures.target(CampaignSpaceData.Ref.world(firstNode)));
            place(a, firstNode);
            place(b, firstNode);
            JsonObject itemInput = object("characterId", b.toString(), "name", "绳索", "quantity", "3"); itemInput.add("itemId", JsonNull.INSTANCE);
            apply("item", itemInput); item = snapshot.domains().items().getFirst().id();
        }
        void apply(String action, JsonObject input) {
            State state = new CampaignDomainCommands().apply(snapshot, action, CampaignSpaceFixtures.focused(snapshot.domains(), input),
                    UUID.randomUUID(), "0".repeat(64), sides -> 10);
            snapshot = snapshot(snapshot.campaignId(), state);
        }
        void place(UUID subject, UUID node) {
            apply("position", object("characterId", subject.toString(), "nodeId", node.toString()));
        }
        void enter(String resolution) {
            apply("scene-create", object("nodeId", firstNode.toString(), "name", "桥下",
                    "resolution", resolution, "width", "4", "height", "4"));
            UUID scene = snapshot.domains().roots().getLast().subjectId();
            JsonObject input = object("sceneId", scene.toString());
            JsonArray subjects = new JsonArray();
            subjects.add(object("id", a.toString(), "version", "0", "x", "0", "y", "0", "side", "0"));
            subjects.add(object("id", b.toString(), "version", "0", "x", "1", "y", "0", "side", "1"));
            input.add("subjects", subjects);
            apply("scene-enter", input);
        }
        void damage(UUID target) {
            var health = new Command("HEALTH_EFFECT_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, null, null,
                    new Schema2CharacterRuntime.HealthInput(CampaignRuleFixtures.rules().rule("adventure.health", "health.srd2014"),
                            CampaignRuleFixtures.rules().rule("adventure.vital_effect", "vital_effect.dm_damage"), null, "DM确认直接环境伤害。"));
            JsonObject input = object("characterId", target.toString(), "characterVersion", Long.toString(character(snapshot.domains(), target).history().current().version()));
            input.add("command", CampaignDomainCodec.toJson(health)); apply("character-command", input);
        }
        JsonObject input(int difficulty) {
            JsonObject input = object("characterId", a.toString(), "characterVersion", Long.toString(character(snapshot.domains(), a).history().current().version()));
            input.add("command", CampaignDomainCodec.toJson(check(difficulty)));
            input.add("success", branch(b, "vital_effect.dm_damage", item, "2", "成功消息"));
            input.add("failure", branch(b, "vital_effect.dm_healing", item, "-1", "失败消息"));
            return input;
        }
        State accept(JsonObject input, UUID operation) {
            State state = new CampaignDomainCommands().apply(snapshot, "check-effects", CampaignSpaceFixtures.focused(snapshot.domains(), input),
                    operation, "0".repeat(64), sides -> 20);
            return CampaignData.validate(snapshot(snapshot.campaignId(), state)).domains();
        }
    }
    private static CampaignData.Snapshot snapshot(String id, State state) {
        return new CampaignData.Snapshot(id, "分支检定", state);
    }
    private static Command check(int difficulty) {
        return new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null,
                new CheckInput(CampaignRuleFixtures.rules().rule("adventure.check", "check.srd2014"), "ABILITY", "ability.strength", null, difficulty, false, false, "DM事前确认检定与两个结果分支。"));
    }
    private static void storageCommand(CampaignStorageService service, String action, JsonObject input) {
        var current = service.accessSnapshot();
        service.domainCommand(UUID.randomUUID().toString(), current.epoch(), current.revision(), action, CampaignSpaceFixtures.focused(current.snapshot().domains(), input));
    }
    private static JsonObject viewCharacter(CampaignStorageService service, String name) {
        return service.current().domain().getAsJsonArray("characters").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(c -> c.get("name").getAsString().equals(name)).findFirst().orElseThrow();
    }
    private static JsonObject object(String... pairs) { JsonObject result = new JsonObject(); for (int i = 0; i < pairs.length; i += 2) result.addProperty(pairs[i], pairs[i + 1]); return result; }
    private static JsonObject branch(UUID target, String health, UUID item, String delta, String message) {
        JsonObject result = object("targetId", target.toString(), "itemDelta", delta, "message", message);
        result.add("healthEffect", health == null ? JsonNull.INSTANCE : CampaignDomainCodec.toJson(CampaignRuleFixtures.rules().rule("adventure.vital_effect", health)));
        result.add("itemId", item == null ? JsonNull.INSTANCE : new JsonPrimitive(item.toString()));
        return result;
    }
    private static Character character(State state, UUID id) { return state.characters().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(); }
    private static Item item(State state, UUID id) { return state.items().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow(); }
    private static Audit audit(Root root) {
        return CampaignDomainCodec.decode(root.after().getBytes(java.nio.charset.StandardCharsets.UTF_8), Audit.class);
    }
}
