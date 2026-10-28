package com.dndtool.service;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatRules.Context;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.Schema2CombatState.Command;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2SpellFixtures.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullAbilities;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.fullPlan;
import static com.dndtool.service.Schema2CombatTestFixture.withState;
import static com.dndtool.service.Schema2CombatTestFixture.withHealth;
import static com.dndtool.service.Schema2CheckTestFixture.equipment;
import static com.dndtool.service.Schema2CheckTestFixture.ITEM_WEAPON;
import static org.junit.jupiter.api.Assertions.*;

class Schema2SpellRulesTest {
    private final Schema2CombatRules rules=new Schema2CombatRules();
    private final ProductionSchema2Projection material=projection();
    private final UUID campaign=UUID.randomUUID(),combat=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    private final Map<UUID,Context> roster=new HashMap<>();
    private Battle battle;
    Schema2SpellRulesTest() {roster.put(a,context(a));roster.put(b,context(b));}
    private Context context(UUID id) {
        var initial=fullPlan(material,new Identity(UUID.randomUUID(),UUID.randomUUID()),campaign,id);
        State state=new Schema2CharacterProgressionRules().initial(material,initial,UUID.randomUUID(),fullAbilities(),initialChoices());
        Context c=new Context(material,initial,state); UUID symbol=UUID.randomUUID();
        var equipmentRules=new Schema2CharacterEquipmentRules();
        state=equipmentRules.apply(material,state,equipment("ITEM_ACQUIRED",symbol,HOLY_SYMBOL,"CARRIED"),symbol);
        state=equipmentRules.apply(material,state,equipment("ITEM_EQUIPPED",symbol,null,"OFF_HAND"),UUID.randomUUID());
        return withState(c,state);
    }
    private UUID arm(UUID actor) {
        Context c=roster.get(actor);UUID item=UUID.randomUUID();var eq=new Schema2CharacterEquipmentRules();
        State state=eq.apply(material,c.character(),equipment("ITEM_ACQUIRED",item,ITEM_WEAPON,"CARRIED"),item);
        state=eq.apply(material,state,equipment("ITEM_EQUIPPED",item,null,"MAIN_HAND"),UUID.randomUUID());
        roster.put(actor,withState(c,state));return item;
    }
    private UUID symbol(UUID actor) {return roster.get(actor).character().equipment().stream().filter(i -> i.item().equals(HOLY_SYMBOL)).findFirst().orElseThrow().itemId();}
    private Command bless(UUID actor,UUID...targets) {return Command.cast(actor,new CastInput(CASTING,BLESS,List.of(targets),symbol(actor)));}
    private Command cure(UUID actor,UUID target) {return Command.cast(actor,new CastInput(CASTING,CURE_WOUNDS,List.of(target),null));}
    private Resolution apply(Command command,int...values) {
        return applyOperation(UUID.randomUUID(),command,values);
    }
    private Resolution applyOperation(UUID op,Command command,int...values) {
        var p=rules.preflight(op,combat,campaign,roster,battle,command);AtomicInteger cursor=new AtomicInteger();
        var result=rules.resolve(p,sides -> {assertTrue(cursor.get()<values.length,"unexpected d"+sides);return values[cursor.getAndIncrement()];});
        assertEquals(values.length,cursor.get());rules.verify(op,combat,campaign,roster,battle,command,result);
        for (HealthChange c:result.healthChanges()) roster.put(c.characterId(),withState(roster.get(c.characterId()),c.after()));
        battle=result.after();return result;
    }
    private void start() {apply(Command.start(List.of(new Deployment(a,new Position(0,0),0),new Deployment(b,new Position(1,0),1)),List.of(a,b)),20,10);}
    private void turn(UUID actor) {apply(Command.action(Kind.END_TURN,actor));}
    private long slots(UUID actor) {return roster.get(actor).character().resources().stream().filter(r -> r.identity().equals(SLOT_RESOURCE)).findFirst().orElseThrow().current();}
    private void rejected(Command command,String code) {assertEquals(code,assertThrows(IllegalArgumentException.class,() -> rules.preflight(UUID.randomUUID(),combat,campaign,roster,battle,command)).getMessage());}

    @Test void blessUsesRealDomainFeatureSlotAndHeldMaterialAndCreatesTerminalTypedCast() {
        start();var result=apply(bless(a,a,b));
        assertEquals(1,slots(a));assertEquals(1,result.healthChanges().size());assertNull(result.healthChanges().getFirst().healthResult());
        assertEquals(SLOT_RESOURCE,result.healthChanges().getFirst().resource());
        assertFalse(battle.combatants().stream().filter(c -> c.characterId().equals(a)).findFirst().orElseThrow().actionAvailable());
        Flow flow=battle.flows().getLast();assertEquals(FrameKind.CAST,flow.frames().getFirst().kind());assertEquals(FlowStatus.COMPLETED,flow.status());
        assertNull(battle.activeFlow());assertTrue(result.rolls().isEmpty());assertEquals(List.of(a,b),flow.casts().getFirst().targets());
        var effect=battle.ongoingEffects().getFirst();assertEquals(60,effect.expiresAtSecond()-effect.startedAtSecond());assertEquals(EffectStatus.ACTIVE,effect.status());
    }
    @Test void selfCureUsesActualWisdomPlusDiscipleOfLifeAndSequentialCharacterVersions() {
        Context c=roster.get(a);roster.put(a,withState(c,withHealth(c.character(),1,HealthState.INITIAL)));start();
        var result=apply(cure(a,a),4);
        assertEquals(2,result.healthChanges().size());var cost=result.healthChanges().getFirst();var healing=result.healthChanges().getLast();
        assertEquals(cost.after(),healing.before());assertEquals(cost.before().version()+2,healing.after().version());assertEquals(7,healing.after().hitPoints());
        assertEquals(6,healing.healthResult().rolledAmount());assertEquals(List.of(4),healing.healthResult().dice());
        var cast=battle.flows().getLast().casts().getFirst();assertEquals(2,cast.healingModifier());assertEquals(6,cast.requestedHealing());assertEquals(1,slots(a));
        assertTrue(battle.ongoingEffects().isEmpty());
    }
    @Test void completeInvalidTargetsAndMissingComponentsFailBeforeCostAndRandomness() {
        start();long prior=slots(a);Battle original=battle;
        rejected(bless(a,a,UUID.randomUUID()),"S2_SPELL_TARGET");
        rejected(Command.cast(a,new CastInput(CASTING,BLESS,List.of(a),UUID.randomUUID())),"S2_SPELL_COMPONENT");
        rejected(Command.cast(a,new CastInput(CASTING,CURE_WOUNDS,List.of(a,b),null)),"S2_SPELL_TARGET");
        assertSame(original,battle);assertEquals(prior,slots(a));
        assertThrows(IllegalArgumentException.class,() -> new CastInput(CASTING,BLESS,List.of(a,a),symbol(a)));
    }
    @Test void somaticOnlyCureNeedsFreeHandButBlessUsesItsHeldFocusHand() {
        arm(a);start();rejected(cure(a,a),"S2_SPELL_COMPONENT");apply(bless(a,a));
    }
    @Test void blessAddsOneD4ToRealAttackAndDoesNotTurnNaturalOneIntoHit() {
        UUID weapon=arm(b);start();apply(bless(a,b));turn(a);apply(Command.action(Kind.BEGIN_ATTACK,b));
        apply(Command.attack(b,a,weapon),5,4,1,20);
        var attack=battle.flows().getLast().attacks().getFirst();assertTrue(attack.hit());assertEquals(4,attack.blessingD4());assertNotNull(attack.blessingEffectKey());
        turn(b);turn(a);apply(Command.action(Kind.BEGIN_ATTACK,b));apply(Command.attack(b,a,weapon),1,4);
        assertFalse(battle.flows().getLast().attacks().getFirst().hit());
    }
    @Test void damageConcentrationSaveUsesBlessAndFailureEndsEffect() {
        UUID weapon=arm(b);start();apply(bless(a,a));turn(a);apply(Command.action(Kind.BEGIN_ATTACK,b));
        apply(Command.attack(b,a,weapon),10,1,5,4);
        var save=battle.flows().getLast().savingThrows().getFirst();assertEquals(10,save.total());assertTrue(save.success());assertEquals(EffectStatus.ACTIVE,battle.ongoingEffects().getFirst().status());
        turn(b);turn(a);apply(Command.action(Kind.BEGIN_ATTACK,b));apply(Command.attack(b,a,weapon),10,1,1,1);
        assertFalse(battle.flows().getLast().savingThrows().getFirst().success());assertEquals("CONCENTRATION_SAVE_FAILED",battle.ongoingEffects().getFirst().endReason());
    }
    @Test void unconsciousCasterEndsConcentrationWithoutRollingAnotherSave() {
        UUID weapon=arm(b);Context c=roster.get(a);roster.put(a,withState(c,withHealth(c.character(),2,HealthState.INITIAL)));
        start();apply(bless(a,a));turn(a);apply(Command.action(Kind.BEGIN_ATTACK,b));apply(Command.attack(b,a,weapon),10,1);
        assertEquals(0,roster.get(a).character().hitPoints());assertEquals("INCAPACITATED",battle.ongoingEffects().getFirst().endReason());
        assertTrue(battle.flows().getLast().savingThrows().isEmpty());
    }
    @Test void sameSpellOverlapShadowsOneSourceAndRestoresItWithoutRefreshingClock() {
        start();apply(bless(a,a,b));turn(a);apply(bless(b,a,b));
        assertEquals(2,battle.ongoingEffects().stream().filter(e -> e.status()==EffectStatus.ACTIVE).count());
        var source=Schema2SpellRules.blessing(battle,a);UUID other=source.casterId().equals(a)?b:a;
        long expires=battle.ongoingEffects().stream().filter(e -> e.casterId().equals(other)).findFirst().orElseThrow().expiresAtSecond();
        apply(Command.action(Kind.END_CONCENTRATION,source.casterId()));
        assertEquals(other,Schema2SpellRules.blessing(battle,a).casterId());assertEquals(expires,Schema2SpellRules.blessing(battle,a).expiresAtSecond());
    }
    @Test void sameSecondTieUsesCreationEventOrderEvenWhenUuidOrderIsReversed() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        while (effectKey(first).toString().compareTo(effectKey(second).toString())<0) second=UUID.randomUUID();
        start();applyOperation(first,bless(a,a,b));turn(a);applyOperation(second,bless(b,a,b));
        assertEquals(battle.ongoingEffects().getFirst().startedAtSecond(),battle.ongoingEffects().getLast().startedAtSecond());
        assertTrue(battle.ongoingEffects().getFirst().effectKey().toString().compareTo(battle.ongoingEffects().getLast().effectKey().toString())>0);
        assertEquals(a,Schema2SpellRules.blessing(battle,a).casterId());
    }
    private static UUID effectKey(UUID operation) {
        UUID frame=key(operation,"cast-0");return key(frame,"ongoing-effect");
    }
    private static UUID key(UUID owner,String role) {
        UUID value=UUID.nameUUIDFromBytes((owner+":"+role).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new UUID((value.getMostSignificantBits()&~0xF000L)|0x4000L,value.getLeastSignificantBits());
    }
    @Test void replacementEndsPriorConcentrationAndDurationExpiresOnExistingSecondsClock() {
        start();apply(bless(a,a));turn(a);turn(b);apply(bless(a,b));
        assertEquals("REPLACED_BY_CONCENTRATION",battle.ongoingEffects().getFirst().endReason());assertEquals(0,slots(a));
    }
    @Test void explorationRetainsLiveSpellAndOnlyExplicitTimeExpiresItBeforeSceneClose() {
        start();apply(bless(a,a));turn(a);apply(Command.action(Kind.END_COMBAT,b));
        assertEquals(Status.EXPLORATION,battle.status());assertEquals(6,Schema2SpellRules.second(roster.get(a).character()));assertEquals(EffectStatus.ACTIVE,battle.ongoingEffects().getFirst().status());
        rejected(Command.action(Kind.CLOSE_SCENE,b),"S2_SCENE_OBLIGATIONS_PENDING");
        apply(Command.advanceTime(b,54));assertEquals(60,Schema2SpellRules.second(roster.get(a).character()));assertEquals("DURATION_EXPIRED",battle.ongoingEffects().getFirst().endReason());assertTrue(battle.concentrationHolders().isEmpty());
        apply(Command.action(Kind.CLOSE_SCENE,b));assertEquals(Status.ENDED,battle.status());assertEquals(60,Schema2SpellRules.second(roster.get(a).character()));
    }
    @Test void explorationCastAdvancesOneSixSecondIntervalThenStartsTheFullSpellDuration() {
        start();apply(Command.action(Kind.END_COMBAT,a));assertEquals(6,Schema2SpellRules.second(roster.get(a).character()));
        var cast=apply(bless(a,a));assertEquals(12,Schema2SpellRules.second(roster.get(a).character()));assertEquals(1,slots(a));
        var effect=battle.ongoingEffects().getFirst();assertEquals(12,effect.startedAtSecond());assertEquals(72,effect.expiresAtSecond());
        assertEquals(2,cast.healthChanges().stream().filter(HealthChange::timeAdvance).count());assertTrue(cast.healthChanges().stream().filter(HealthChange::timeAdvance).allMatch(c->c.seconds()==6));
        apply(Command.advanceTime(b,59));assertEquals(EffectStatus.ACTIVE,battle.ongoingEffects().getFirst().status());
        apply(Command.advanceTime(b,1));assertEquals("DURATION_EXPIRED",battle.ongoingEffects().getFirst().endReason());assertEquals(1,slots(a));assertTrue(battle.concentrationHolders().isEmpty());
    }
    @Test void blessDeathSaveAddsBonusAndPreservesRawNaturalOneAndTwenty() {
        Context c=roster.get(b);roster.put(b,withState(c,Schema2CombatEquipmentFacts.drop(withHealth(c.character(),0,HealthState.INITIAL),combat,new Position(1,0))));
        start();apply(bless(a,b));apply(Command.action(Kind.END_TURN,a),6,4);
        assertEquals(1,roster.get(b).character().health().deathSuccesses());
        var save=battle.flows().getLast().savingThrows().getFirst();assertEquals("DEATH",save.kind());assertEquals(10,save.total());
        turn(b);apply(Command.action(Kind.END_TURN,a),1,4);assertEquals(2,roster.get(b).character().health().deathFailures());
        turn(b);apply(Command.action(Kind.END_TURN,a),20,1);assertEquals(1,roster.get(b).character().hitPoints());
    }
    @Test void exhaustedSlotsAndUndeclaredSpellsFailClosed() {
        start();apply(cure(a,a),1);turn(a);turn(b);apply(cure(a,a),1);turn(a);turn(b);
        rejected(cure(a,a),"S2_SPELL_SLOT_UNAVAILABLE");
        var unknown=new com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity(BLESS.moduleKey(),"adventure.spell","spell.unpublished");
        // Resolve declaration before cost: an unknown spell remains unavailable even with no slots.
        rejected(Command.cast(a,new CastInput(CASTING,unknown,List.of(a),null)),"S2_SPELL_UNAVAILABLE");
    }
    @Test void sourceIdentityAndTargetDistanceCannotBeInventedByTheCaller() {
        start();
        var unknown=new com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity(CASTING.moduleKey(),"character.spellcasting","spellcasting.unpublished");
        rejected(Command.cast(a,new CastInput(unknown,BLESS,List.of(a),symbol(a))),"S2_SPELL_CASTER_UNSUPPORTED");
        Combatant far=battle.combatants().stream().filter(c -> c.characterId().equals(b)).findFirst().orElseThrow();
        var actors=battle.combatants().stream().map(c -> c.characterId().equals(b)?new Combatant(b,new Position(7,0),far.side(),far.actionAvailable(),far.reactionAvailable(),0,0,false,false,null,false):c).toList();
        Battle separated=new Battle(battle.combatId(),battle.campaignId(),battle.combatRule(),battle.status(),battle.round(),battle.turnNumber(),battle.activeOrdinal(),battle.version(),actors,battle.initiative(),battle.flows(),battle.ongoingEffects());
        assertEquals("S2_SPELL_TARGET",assertThrows(IllegalArgumentException.class,() -> rules.preflight(UUID.randomUUID(),combat,campaign,roster,separated,bless(a,b))).getMessage());
    }
    @Test void frozenEffectDurationAndContributionCannotBeForgedWithRetainedDice() {
        start();UUID operation=UUID.randomUUID();Command command=bless(a,a);Battle before=battle;
        var saved=rules.resolve(rules.preflight(operation,combat,campaign,roster,before,command),sides -> fail("Bless must not roll"));
        OngoingEffect e=saved.after().ongoingEffects().getFirst();
        var forged=new OngoingEffect(e.effectKey(),e.spell(),e.casterId(),e.targets(),e.startedAtSecond(),e.expiresAtSecond()+6,e.status(),null);
        Battle after=Schema2SpellRules.withEffects(saved.after(),List.of(forged));
        assertThrows(IllegalArgumentException.class,() -> rules.verify(operation,combat,campaign,roster,before,command,new Resolution(after,saved.healthChanges(),saved.rolls(),saved.facts())));
        assertThrows(IllegalArgumentException.class,() -> rules.validateState(after));
    }
    @Test void castAndEndConcentrationUseExistingBusyGate() {
        UUID weapon=arm(b);start();apply(bless(a,a));
        apply(Command.move(a,List.of(new Position(-1,0))));assertNotNull(battle.activeFlow());
        rejected(cure(a,a),"ACTION_FLOW_BUSY");rejected(Command.action(Kind.END_CONCENTRATION,a),"ACTION_FLOW_BUSY");
        assertNotNull(weapon);
    }
}
