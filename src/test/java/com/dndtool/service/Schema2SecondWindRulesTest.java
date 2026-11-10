package com.dndtool.service;

import com.dndtool.service.CampaignDomainEngine.CharacterRecord;
import com.dndtool.service.CampaignDomainEngine.CombatResult;
import com.dndtool.service.Schema2CharacterRuntime.ClassLevel;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.storage.BundledCampaignRules;
import com.dndtool.service.storage.CampaignRuleContext;
import com.dndtool.service.storage.CampaignRuleSupport;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.SECOND_WIND_SQLITE_REQUIRED;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SecondWindRulesTest {
    private final CampaignDomainEngine domain = new CampaignDomainEngine();
    private final Schema2CombatRules combat = new Schema2CombatRules();
    private final CampaignRuleContext rules = CampaignRuleSupport.validate(BundledCampaignRules.projection());
    private final UUID campaign = UUID.randomUUID(), scene = UUID.randomUUID();
    private final CharacterRecord fighter = character("fighter"), cleric = character("cleric");
    private final UUID actor = fighter.initial().characterId(), opponent = cleric.initial().characterId();

    @Test void normalGrantCanSpendAtFullHealthAfterTheRegularActionAndRetainsItsRoll() {
        var resource = rules.rule("character.resource", "resource.fighter.second_wind");
        assertEquals(resource, Schema2FeatureSupport.secondWindResource(rules.projection(), fighter.history().current()));
        var dashed = apply(start(), Kind.DASH, actor);
        assertFalse(dashed.history().current().combatants().stream().filter(member -> member.characterId().equals(actor)).findFirst().orElseThrow().actionAvailable());
        var result = apply(dashed, Kind.SECOND_WIND, actor, 10, 10);
        var event = result.history().events().getLast();
        assertEquals(dashed.history().current().combatants(), event.after().combatants());
        assertEquals(List.of(new Roll("SECOND_WIND_HEALING", actor, 10, 10)), event.resolution().rolls());
        var cost = event.resolution().healthChanges().getFirst();
        var heal = event.resolution().healthChanges().getLast();
        assertEquals(resource, cost.resource());
        assertEquals(1, cost.resourceAmount());
        assertEquals(cost.after(), heal.before());
        assertEquals(new Schema2CharacterRuntime.HealthResult(List.of(10), null, null, 11, 0), heal.healthResult());
        rejected("S2_RESOURCE_INSUFFICIENT", () -> apply(result, Kind.SECOND_WIND, actor));
        rejected("S2_RESOURCE_FEATURE_OWNER_REQUIRED", () -> new Schema2CharacterProgressionRules()
                .spendOrdinary(rules.projection(), fighter.history().current(), resource, 1));
    }

    @Test void higherDiceBelongOnlyToSecondWindAndRequireItsOriginalRecipientAndAmount() {
        var event = apply(start(), Kind.SECOND_WIND, actor, 10, 9).history().events().getLast();
        var heal = event.resolution().healthChanges().getLast();
        assertEquals(new Schema2CharacterRuntime.HealthResult(List.of(9), null, null, 10, 0), heal.healthResult());
        for (int die : List.of(9, 10)) {
            rejectsHealing(heal.before(), event.operationId(), UUID.randomUUID(), actor, die, die + 1);
        }
        rejectsHealing(heal.before(), event.operationId(), heal.frameKey(), opponent, 9, 10);
        rejectsHealing(cleric.history().current(), event.operationId(), heal.frameKey(), opponent, 9, 10);
        rejectsHealing(heal.before(), event.operationId(), heal.frameKey(), actor, 9, 11);
    }

    @Test void useRequiresTheCurrentConsciousActorAndAnAcquiredSingleClassFeature() {
        var started = start();
        rejected("S2_COMBAT_NOT_CURRENT_TURN", () -> apply(started, Kind.SECOND_WIND, opponent));
        var clericTurn = apply(started, Kind.END_TURN, actor);
        rejected("S2_COMBAT_SECOND_WIND_UNAVAILABLE", () -> apply(clericTurn, Kind.SECOND_WIND, opponent));

        State state = fighter.history().current();
        var down = new Schema2CharacterHealthRules().applyDamage(rules.projection(), state, (int) state.hitPoints(), false).state();
        var roster = Map.of(actor, new Context(rules.projection(), fighter.initial(), down),
                opponent, new Context(rules.projection(), cleric.initial(), cleric.history().current()));
        rejected("S2_COMBAT_ACTOR_INACTIVE", () -> combat.preflight(UUID.randomUUID(), scene, campaign, roster,
                started.history().current(), Command.action(Kind.SECOND_WIND, actor)));

        State mixed = new State(state.identity(), campaign, actor, state.preparationOperationId(), state.characterClass(),
                state.abilities(), 2, state.proficiencyBonus(), state.hitPoints(), state.maximumHitPoints(),
                List.of(new ClassLevel(state.characterClass(), 1), new ClassLevel(cleric.history().current().characterClass(), 1)),
                state.hitDicePools(), state.subclasses(), state.features(), state.feats(), state.proficiencies(), state.version(),
                state.resources(), state.rest(), state.equipment(), state.health());
        rejected("S2_COMBAT_FEATURE_UNSUPPORTED", () -> Schema2FeatureSupport.secondWindResource(rules.projection(), mixed));
    }

    @Test void mysqlExecutionAndRestorationRejectBeforeConnectionOrDice() {
        var started = start();
        var versions = started.characters().stream().collect(Collectors.toMap(row -> row.initial().characterId(),
                row -> row.history().current().version()));
        var command = Command.action(Kind.SECOND_WIND, actor);
        var request = new Schema2CombatRuntimeService.Request(UUID.randomUUID(), scene, campaign, rules.binding(),
                started.history().members(), started.history().current().version(), versions, command);
        DataSource inaccessible = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (proxy, method, arguments) -> fail("Unexpected JDBC call " + method.getName()));
        var service = new Schema2CombatRuntimeService(inaccessible, sides -> fail("Unexpected d" + sides));
        assertEquals(SECOND_WIND_SQLITE_REQUIRED, service.execute(request).code());
        assertEquals(SECOND_WIND_SQLITE_REQUIRED, service.inspect(request).code());

        var used = apply(started, Kind.SECOND_WIND, actor, 10, 1);
        var histories = used.history().members().stream().map(member -> used.characters().stream()
                .filter(row -> row.initial().characterId().equals(member.characterId())).findFirst().orElseThrow().history()).toList();
        var save = new Schema2CombatRuntimeService.Save(used.history(), histories);
        assertEquals(SECOND_WIND_SQLITE_REQUIRED, service.restore(save).code());
        assertEquals(SECOND_WIND_SQLITE_REQUIRED, service.inspectRestore(save).code());
    }

    private CharacterRecord character(String characterClass) {
        var initial = rules.initialPlan(campaign, UUID.randomUUID(), Schema2PreparationIdentity.random(), characterClass, "acolyte");
        var history = domain.initial(rules.projection(), initial, UUID.randomUUID(), rules.initialAbilities(characterClass),
                rules.initialChoices(characterClass), UUID.randomUUID());
        return new CharacterRecord(characterClass, "PC", initial, history);
    }

    private CombatResult start() {
        var command = Command.start(List.of(new Deployment(actor, new Position(0, 0), 0),
                new Deployment(opponent, new Position(1, 0), 1)), List.of(actor, opponent));
        return domain.executeCombat(rules.projection(), List.of(fighter, cleric), null, scene, UUID.randomUUID(), command, sides -> 10);
    }

    private CombatResult apply(CombatResult before, Kind kind, UUID subject) {
        return domain.executeCombat(rules.projection(), before.characters(), before.history(), scene, UUID.randomUUID(),
                Command.action(kind, subject), sides -> fail("Unexpected d" + sides));
    }

    private CombatResult apply(CombatResult before, Kind kind, UUID subject, int expectedSides, int roll) {
        return domain.executeCombat(rules.projection(), before.characters(), before.history(), scene, UUID.randomUUID(),
                Command.action(kind, subject), sides -> {
                    assertEquals(expectedSides, sides);
                    return roll;
                });
    }

    private void rejectsHealing(State before, UUID operationId, UUID frame, UUID source, int die, int requested) {
        var healed = new Schema2CharacterHealthRules().applyHealing(before, requested);
        var result = new Schema2CharacterRuntime.HealthResult(List.of(die), null, null, requested, healed.result().appliedAmount());
        var change = new HealthChange(before.characterId(), before, healed.state(), frame, requested,
                false, result, false, false, 0, null, 0, true);
        var command = Schema2CombatCharacterFacts.command(rules.projection(), scene, operationId, source, change);
        rejected("S2_COMBAT_CHARACTER_FACT", () -> Schema2CombatCharacterFacts.verify(rules.projection(), before,
                command, result, healed.state()));
    }

    private static void rejected(String code, Executable action) {
        assertEquals(code, assertThrows(IllegalArgumentException.class, action).getMessage());
    }
}
