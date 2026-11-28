package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.CampaignDomainEngine.CharacterRecord;
import com.dndtool.service.CampaignDomainEngine.MonsterArrival;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.RestInput;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.storage.CampaignDomainCodec;
import com.dndtool.service.storage.CampaignRuleContext;
import com.dndtool.service.storage.CampaignRuleFixtures;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.dndtool.module.composition.Schema2CompositionKernel.DamageType.FIRE;
import static com.dndtool.module.composition.Schema2CompositionKernel.DamageType.RADIANT;
import static com.dndtool.service.Schema2CombatState.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2KnownCantripCombatTest {
    private static final String FIRE_BOLT = "fire_bolt";
    private static final String SACRED_FLAME = "sacred_flame";
    private static final Position ORIGIN = new Position(0,0);

    @Test void fireBoltAtExactRangeUsesEmptySlotsAndNoEquipmentAndRecordsCritDamage() {
        var trial = new Scene();
        UUID caster = trial.add("wizard");
        trial.book(caster);
        UUID target = trial.add("fighter");
        trial.open(List.of(new Deployment(caster,ORIGIN,0),new Deployment(target,new Position(24,0),1)));
        trial.command(Command.reenter(caster,List.of(caster,target)),20,1);
        var burningHands = new CastInput(trial.wizardCasting(),trial.spell("burning_hands"),List.of(),null,1,null,4);
        for (int i=0; i<2; i++) {
            trial.command(Command.cast(caster,burningHands));
            trial.nextTurn(caster);
            trial.nextTurn(target);
        }
        assertEquals(0,trial.character(caster).history().current().resources().stream()
                .filter(resource -> resource.identity().ruleKey().equals("resource.wizard.slot1")).findFirst().orElseThrow().current());
        var before = trial.character(caster).history().current();
        assertTrue(before.equipment().isEmpty());
        var damage = trial.command(trial.cast(caster,"wizard",FIRE_BOLT,target),20,2,3);
        var attack = trial.attack();
        var component = attack.spellAttack().damage();
        assertEquals(120,attack.rangedAttack().distanceFeet());
        assertNull(attack.itemId());
        assertNull(attack.rangedAttack().ammunitionItemId());
        assertEquals(2,attack.abilityModifier());
        assertEquals(2,attack.proficiencyBonus());
        assertTrue(attack.hit());
        assertTrue(attack.critical());
        assertEquals(FIRE,component.damageType());
        assertEquals(List.of(2,3),component.dice());
        assertEquals(5,damage.healthChanges().getFirst().damageAmount());
        assertEquals(before,trial.character(caster).history().current());
        assertTrue(damage.ammunitionUses().isEmpty());
        assertEquals(List.of("ATTACK_D20","FIRE_BOLT_DAMAGE","FIRE_BOLT_DAMAGE"),damage.rolls().stream().map(Roll::purpose).toList());
        assertEquals(CastStatus.RESOLVED,trial.flow().casts().getFirst().status());
        trial.assertRoundTrip();
    }

    @Test void sacredFlameIgnoresPartialCoverAndUsesActualDexterityAgainstWisdomDc() {
        var trial = new Scene();
        UUID caster = trial.add("cleric");
        UUID target = trial.add("wizard");
        trial.open(List.of(new Deployment(caster,ORIGIN,0),new Deployment(target,new Position(12,0),0)));
        trial.command(Command.setObservationCell(new ObservationCellInput(new Position(3,0),false,false,Cover.THREE_QUARTERS)));
        trial.command(Command.reenter(caster,List.of(caster,target)),20,1);
        var state = trial.character(caster).history().current();
        var saves = trial.command(trial.cast(caster,"cleric",SACRED_FLAME,target),10);
        var save = trial.flow().casts().getFirst().sacredFlame();
        assertEquals(12,save.savingThrow().difficultyClass());
        assertEquals(12,save.savingThrow().total());
        assertTrue(save.savingThrow().success());
        assertNull(save.damage());
        assertTrue(saves.healthChanges().isEmpty());
        trial.nextTurn(caster);
        trial.nextTurn(target);
        var fails = trial.command(trial.cast(caster,"cleric",SACRED_FLAME,target),9,4);
        var failed = trial.flow().casts().getFirst().sacredFlame();
        assertFalse(failed.savingThrow().success());
        assertEquals(RADIANT,failed.damage().damageType());
        assertEquals(List.of(4),failed.damage().dice());
        assertEquals(4,fails.healthChanges().getFirst().damageAmount());
        var after = trial.character(caster).history().current();
        assertEquals(state.resources(),after.resources());
        assertEquals(state.knownCantrips(),after.knownCantrips());
        trial.assertRoundTrip();
    }

    @Test void wizardFifthLevelNaturalTwentyDoublesBothDiceBeforeFrozenMonsterImmunity() {
        var trial = new Scene();
        UUID caster = trial.add("wizard");
        trial.book(caster);
        trial.levelFive(caster,false);
        UUID target = UUID.randomUUID();
        var monster = new MonsterDeployment(target,trial.rules.rule("adventure.monster","monster.hell_hound"),new Position(5,0),1);
        trial.open(List.of(new Deployment(caster,ORIGIN,0)),monster);
        trial.command(Command.reenter(caster,List.of(caster,target)),20,1);
        var strike = trial.command(trial.cast(caster,"wizard",FIRE_BOLT,target),20,1,2,3,4);
        assertEquals(3,trial.attack().proficiencyBonus());
        assertEquals(List.of(1,2,3,4),trial.attack().spellAttack().damage().dice());
        assertEquals(10,trial.attack().spellAttack().damage().rawDamage());
        assertEquals(0,trial.attack().damageAmount());
        assertEquals("IMMUNITY",trial.attack().spellAttack().damage().defenses().getFirst().kind());
        assertTrue(strike.healthChanges().isEmpty());
        assertEquals(45,trial.battle().monsters().getFirst().hitPoints());
        trial.assertRoundTrip();
    }

    @Test void shieldCanNegateFireBoltAndCounterspellCanStopItBeforeAttackDice() {
        var shieldTrial = new Scene();
        UUID caster = shieldTrial.add("wizard"), target = shieldTrial.add("wizard");
        shieldTrial.book(target);
        shieldTrial.open(List.of(new Deployment(caster,ORIGIN,0),new Deployment(target,new Position(2,0),1)));
        shieldTrial.command(Command.reenter(caster,List.of(caster,target)),20,1);
        shieldTrial.command(shieldTrial.cast(caster,"wizard",FIRE_BOLT,target),9);
        assertEquals(AttackStatus.HIT_PENDING_DAMAGE,shieldTrial.attack().status());
        assertEquals(TriggerKind.ATTACK_HIT_BEFORE_DAMAGE,shieldTrial.window().trigger().kind());
        shieldTrial.assertRoundTrip();
        shieldTrial.command(shieldTrial.reaction(Kind.DECLARE_SHIELD,target,"shield",target,1,null));
        var resume = shieldTrial.command(shieldTrial.continueFlow(caster));
        assertTrue(resume.rolls().isEmpty());
        assertFalse(shieldTrial.attack().hit());
        assertEquals(9,shieldTrial.attack().selectedD20());
        assertNull(shieldTrial.attack().spellAttack().damage());
        assertEquals(CastStatus.RESOLVED,shieldTrial.flow().casts().getFirst().status());
        shieldTrial.assertRoundTrip();

        var counterTrial = new Scene();
        UUID source = counterTrial.add("wizard"), counter = counterTrial.add("wizard");
        counterTrial.book(counter);
        counterTrial.levelFive(counter,true);
        counterTrial.open(List.of(new Deployment(source,ORIGIN,0),new Deployment(counter,new Position(2,0),1)));
        counterTrial.command(Command.reenter(source,List.of(source,counter)),20,1);
        var started = counterTrial.command(counterTrial.cast(source,"wizard",FIRE_BOLT,counter));
        assertTrue(started.rolls().isEmpty());
        var window = counterTrial.window();
        assertEquals(TriggerKind.CAST_BEFORE_COMPLETION,window.trigger().kind());
        counterTrial.command(counterTrial.reaction(Kind.DECLARE_COUNTERSPELL,counter,"counterspell",source,3,window.ownerFrameKey()));
        assertTrue(counterTrial.command(counterTrial.continueFlow(source)).rolls().isEmpty());
        assertTrue(counterTrial.flow().attacks().isEmpty());
        assertEquals(CastStatus.COUNTERED,counterTrial.flow().casts().getFirst().status());
        assertFalse(Schema2CombatRules.combatant(counterTrial.battle(),source).actionAvailable());
        counterTrial.assertRoundTrip();
    }

    @Test void invalidCantripsFailBeforeDiceAndReadyRemainsOutsideTheCombatCantripScope() {
        var trial = new Scene();
        UUID caster = trial.add("wizard","spell.light","spell.mage_hand","spell.prestidigitation");
        UUID target = trial.add("cleric");
        trial.open(List.of(new Deployment(caster,ORIGIN,0),new Deployment(target,new Position(2,0),1)));
        trial.reject(trial.cast(caster,"wizard",FIRE_BOLT,target),"S2_SPELL_UNAVAILABLE");
        trial.command(Command.reenter(caster,List.of(caster,target)),20,1);
        trial.reject(trial.cast(caster,"wizard",FIRE_BOLT,target),"S2_SPELL_UNAVAILABLE");
        trial.reject(trial.cast(caster,"wizard","light",target),"S2_SPELL_UNAVAILABLE");
        trial.reject(trial.cast(caster,"cleric",SACRED_FLAME,target),"S2_SPELL_UNAVAILABLE");
        var ready = new ReadyInput(new ReadyTrigger(TriggerKind.CAST_COMPLETED,target,null),
                ReadyResponseKind.SPELL,null,null,List.of(),trial.cast(caster,"wizard",FIRE_BOLT,target).castInput());
        trial.reject(Command.ready(caster,ready),"S2_SPELL_READY_CANTRIP_UNSUPPORTED");
        assertTrue(Schema2CombatRules.combatant(trial.battle(),caster).actionAvailable());
    }

    @Test void fireBoltAtAStaleAimMissesEvenWithTwoNaturalTwenties() {
        var trial = new Scene();
        UUID wizard = trial.add("wizard"), hider = trial.add("cleric");
        var remembered = new Position(2,0);
        var next = new Position(3,0);
        trial.open(List.of(new Deployment(wizard,ORIGIN,0),new Deployment(hider,remembered,1)));
        trial.command(Command.setObservationCell(new ObservationCellInput(remembered,true,false,Cover.NONE)));
        trial.command(Command.setObservationCell(new ObservationCellInput(next,true,false,Cover.NONE)));
        trial.command(Command.reenter(hider,List.of(hider,wizard)),20,1);
        trial.command(Command.action(Kind.HIDE,hider),20);
        trial.command(Command.move(hider,List.of(next)));
        trial.nextTurn(hider);
        var result = trial.command(trial.cast(wizard,"wizard",FIRE_BOLT,hider),20,20);
        assertEquals(remembered,trial.attack().aimedPosition());
        assertFalse(trial.attack().targetAtAimedPosition());
        assertTrue(trial.attack().disadvantage());
        assertFalse(trial.attack().hit());
        assertTrue(result.healthChanges().isEmpty());
        assertEquals(2,result.rolls().size());
        trial.assertRoundTrip();
    }

    @ParameterizedTest
    @CsvSource({"false,false,true", "true,false,true", "false,true,true", "true,true,true", "false,false,false"})
    void fireBoltBlindlyMissesAnUnseenDeadTargetWhetherOrNotItRemainsAtTheRememberedCell(
            boolean moved, boolean counterWindow, boolean hidden) {
        var trial = new Scene();
        UUID wizard = trial.add("wizard"), cleric = trial.add("cleric"), target = UUID.randomUUID();
        UUID counter = counterWindow ? trial.add("wizard") : null;
        var deployments = new ArrayList<>(List.of(new Deployment(wizard,ORIGIN,0),
                new Deployment(cleric,new Position(3,5),0)));
        var turnOrder = new ArrayList<>(List.of(target,cleric,wizard));
        if (counterWindow) {
            trial.book(counter);
            trial.levelFive(counter,true);
            deployments.add(new Deployment(counter,new Position(-10,0),0));
            turnOrder.add(counter);
        }
        var remembered = new Position(2,0);
        var next = new Position(3,0);
        var monster = new MonsterDeployment(target,trial.rules.rule("adventure.monster","monster.goblin"),remembered,1);
        trial.open(deployments,monster);
        trial.command(Command.setObservationCell(new ObservationCellInput(remembered,true,false,Cover.NONE)));
        trial.command(Command.setObservationCell(new ObservationCellInput(next,true,false,Cover.NONE)));
        trial.command(Command.reenter(cleric,turnOrder),counterWindow ? new int[]{20,16,10,1} : new int[]{20,16,10});
        if (hidden) trial.command(Command.action(Kind.NIMBLE_HIDE,target),6);
        if (moved) trial.command(Command.move(target,List.of(next)));
        trial.nextTurn(target);
        assertTrue(Schema2ObservationRules.visible(trial.battle(),cleric,target));
        trial.command(trial.cast(cleric,"cleric",SACRED_FLAME,target),1,8);
        assertTrue(trial.battle().monsters().getFirst().dead());
        trial.nextTurn(cleric);

        Command fireBolt = trial.cast(wizard,"wizard",FIRE_BOLT,target);
        var seen = hidden ? Set.of(wizard,cleric) : Set.of(wizard,cleric,target);
        assertEquals(hidden,!Schema2ObservationRules.visible(trial.battle(),wizard,target));
        assertEquals(remembered,Schema2ObservationRules.knownPosition(trial.battle(),wizard,target));
        if (!hidden) {
            assertEquals("S2_HEALTH_DEAD",assertThrows(IllegalArgumentException.class,
                    () -> trial.preflightCandidate(fireBolt,seen)).getMessage());
            trial.reject(fireBolt,"S2_HEALTH_DEAD");
            return;
        }
        assertDoesNotThrow(() -> trial.preflightCandidate(fireBolt,seen));
        Command resolution = fireBolt;
        if (counterWindow) {
            assertTrue(trial.command(fireBolt).rolls().isEmpty());
            var flow = trial.flow();
            var window = trial.window();
            assertEquals(TriggerKind.CAST_BEFORE_COMPLETION,window.trigger().kind());
            var pending = window.opportunities().getFirst();
            trial.assertRoundTrip();
            trial.command(Command.reaction(Kind.PASS_REACTION,counter,null,flow.flowKey(),flow.leafFrameKey(),
                    window.windowKey(),pending.opportunityKey()));
            resolution = trial.continueFlow(wizard);
        }
        var result = trial.command(resolution,20,20);
        var attack = trial.attack();
        assertEquals(remembered,attack.aimedPosition());
        assertFalse(attack.targetAtAimedPosition());
        assertEquals(!moved,attack.spellAttack().unseenDeadAtAim());
        assertEquals(1,attack.armorClass());
        assertEquals(1,attack.finalArmorClass());
        assertFalse(attack.hit());
        assertTrue(attack.disadvantage());
        assertEquals(0,attack.damageAmount());
        assertTrue(result.healthChanges().isEmpty());
        assertEquals(List.of(20,20),result.rolls().stream().map(Roll::value).toList());
        assertFalse(Schema2CombatRules.combatant(trial.battle(),wizard).actionAvailable());
        assertEquals(CastStatus.RESOLVED,trial.flow().casts().getFirst().status());
        trial.assertRoundTrip();
    }

    @Test void sacredFlameDamagesADeployedMonsterUsingItsFrozenDexterity() {
        var trial = new Scene();
        UUID cleric = trial.add("cleric"), target = UUID.randomUUID();
        var monster = new MonsterDeployment(target,trial.rules.rule("adventure.monster","monster.hell_hound"),new Position(4,0),1);
        trial.open(List.of(new Deployment(cleric,ORIGIN,0)),monster);
        trial.command(Command.reenter(cleric,List.of(cleric,target)),20,1);
        var result = trial.command(trial.cast(cleric,"cleric",SACRED_FLAME,target),5,8);
        var outcome = trial.flow().casts().getFirst().sacredFlame();
        assertEquals(1,outcome.savingThrow().abilityModifier());
        assertEquals(8,outcome.damage().effectiveDamage());
        assertEquals(37,trial.battle().monsters().getFirst().hitPoints());
        assertTrue(result.healthChanges().isEmpty());
        trial.assertRoundTrip();
    }

    @Test void fireBoltUsesBlessForTheAttackAndTriggersTheTargetsConcentrationSave() {
        var trial = new Scene();
        UUID cleric = trial.add("cleric"), wizard = trial.add("wizard");
        UUID holySymbol = trial.equip(cleric,"holy_symbol","OFF_HAND");
        trial.open(List.of(new Deployment(cleric,ORIGIN,0),new Deployment(wizard,new Position(2,0),1)));
        trial.command(Command.reenter(cleric,List.of(cleric,wizard)),20,1);
        var lifeDomain = trial.rules.rule("character.spellcasting","spellcasting.life_domain");
        trial.command(Command.cast(cleric,new CastInput(lifeDomain,trial.spell("bless"),List.of(cleric,wizard),holySymbol)));
        trial.nextTurn(cleric);
        var result = trial.command(trial.cast(wizard,"wizard",FIRE_BOLT,cleric),5,1,1,1,1);
        assertTrue(trial.attack().hit());
        assertEquals(1,trial.attack().blessingD4());
        var concentration = trial.flow().savingThrows().getFirst();
        assertEquals(cleric,concentration.subjectId());
        assertEquals(10,concentration.difficultyClass());
        assertFalse(concentration.success());
        assertEquals("CONCENTRATION_SAVE_FAILED",trial.battle().ongoingEffects().getFirst().endReason());
        assertEquals(1,result.healthChanges().getFirst().damageAmount());
        trial.assertRoundTrip();
    }

    @Test void bothCantripsRequireFreeSomaticHandAndTotalCoverPreventsTargetSelection() {
        var trial = new Scene();
        UUID caster = trial.add("wizard"), target = trial.add("cleric");
        trial.equip(caster,"dagger","MAIN_HAND");
        trial.equip(caster,"dagger","OFF_HAND");
        trial.equip(target,"dagger","MAIN_HAND");
        trial.equip(target,"dagger","OFF_HAND");
        trial.open(List.of(new Deployment(caster,ORIGIN,0),new Deployment(target,new Position(2,0),1)));
        trial.command(Command.reenter(caster,List.of(caster,target)),20,1);
        trial.reject(trial.cast(caster,"wizard",FIRE_BOLT,target),"S2_SPELL_COMPONENT");
        trial.nextTurn(caster);
        trial.reject(trial.cast(target,"cleric",SACRED_FLAME,caster),"S2_SPELL_COMPONENT");

        var cover = new Scene();
        UUID cleric = cover.add("cleric"), wizard = cover.add("wizard");
        cover.open(List.of(new Deployment(cleric,ORIGIN,0),new Deployment(wizard,new Position(2,0),1)));
        cover.command(Command.setObservationCell(new ObservationCellInput(new Position(1,0),false,true,Cover.TOTAL)));
        cover.command(Command.reenter(cleric,List.of(cleric,wizard)),20,1);
        cover.reject(cover.cast(cleric,"cleric",SACRED_FLAME,wizard),"S2_SPELL_TARGET");
    }

    private static final class Scene {
        final CampaignDomainEngine engine = new CampaignDomainEngine();
        final CampaignRuleContext rules = CampaignRuleFixtures.rules();
        final UUID campaign = UUID.randomUUID(), scene = UUID.randomUUID();
        final Map<UUID,CharacterRecord> characters = new LinkedHashMap<>();
        Schema2CombatHistory.History history;
        long second;

        UUID add(String classKey,String...cantrips) {
            UUID id = UUID.randomUUID();
            var plan = rules.initialPlan(campaign,id,new Schema2PreparationIdentity(UUID.randomUUID(),UUID.randomUUID()),
                    classKey,"acolyte");
            var choices = cantrips.length==0 ? rules.initialChoices(classKey) : rules.initialChoices(classKey,List.of(cantrips));
            var initial = engine.initial(rules.projection(),plan,UUID.randomUUID(),rules.initialAbilities(classKey),choices,UUID.randomUUID());
            characters.put(id,new CharacterRecord(classKey,"PC",plan,initial));
            return id;
        }

        CharacterRecord character(UUID id) { return characters.get(id); }
        RuleIdentity spell(String name) { return rules.rule("adventure.spell","spell."+name); }
        RuleIdentity wizardCasting() { return rules.rule("character.wizard_spellcasting","spellcasting.wizard"); }

        void book(UUID id) {
            change(id,Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_INITIALIZED",
                    new SpellbookInput(wizardCasting(),rules.initialWizardBook(),rules.initialWizardPrepared())));
        }

        void levelFive(UUID id, boolean counterspell) {
            var learning = List.of(List.of("detect_magic","disguise_self"),List.of("grease","mage_armor"),
                    List.of("magic_missile","sleep"),List.of("counterspell","fog_cloud"));
            for (int level=2; level<=5; level++) {
                var subclass = level==2 ? rules.rule("character.subclass","subclass.evocation") : null;
                var increases = level==4 ? Map.of("ability.intelligence",2) : Map.<String,Integer>of();
                var acquired = level==4 ? List.of(spell("prestidigitation")) : List.<RuleIdentity>of();
                var choices = new GrowthChoices(subclass,increases,null,Map.of(),acquired);
                change(id,new Schema2CharacterRuntime.Command("CHARACTER_ADVANCED",null,0,null,"FIXED_AVERAGE",
                        rules.rule("character.class","class.wizard"),choices));
                change(id,Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_ADVANCED",new SpellbookInput(wizardCasting(),
                        learning.get(level-2).stream().map(this::spell).toList(),rules.initialWizardPrepared())));
            }
            if (counterspell) {
                change(id,rest("REST_STARTED",new RestInput("LONG_REST",null,0,0,Map.of())));
                change(id,rest("TIME_ADVANCED",new RestInput(null,"SLEEP",480,0,Map.of())));
                change(id,rest("REST_COMPLETED",new RestInput(null,null,0,0,Map.of())));
                change(id,Schema2CharacterRuntime.Command.spellbook("SPELLBOOK_PREPARED",new SpellbookInput(wizardCasting(),
                        List.of(),List.of(spell("counterspell")))));
            }
        }

        private static Schema2CharacterRuntime.Command rest(String kind,RestInput input) {
            return new Schema2CharacterRuntime.Command(kind,null,0,null,null,null,GrowthChoices.EMPTY,input);
        }

        UUID equip(UUID id,String itemKey,String hand) {
            UUID item = UUID.randomUUID();
            change(id,Schema2CheckTestFixture.equipment("ITEM_ACQUIRED",item,rules.rule("equipment.item","item."+itemKey),"CARRIED"),item);
            change(id,Schema2CheckTestFixture.equipment("ITEM_EQUIPPED",item,null,hand));
            return item;
        }

        void change(UUID id, Schema2CharacterRuntime.Command command) {
            change(id,command,UUID.randomUUID());
        }

        void change(UUID id, Schema2CharacterRuntime.Command command, UUID operation) {
            var prior = character(id);
            var next = engine.executeCharacter(rules.projection(),prior.initial(),prior.history(),command,operation,
                    sides -> fail("Unexpected character draw d"+sides));
            characters.put(id,new CharacterRecord(prior.name(),prior.kind(),prior.initial(),next));
        }

        void open(List<Deployment> players, MonsterDeployment... monsters) {
            var arrivals = new HashMap<UUID,MonsterArrival>();
            for (var monster : monsters) arrivals.put(monster.monsterId(),
                    new MonsterArrival(engine.initialMonster(rules.projection(),monster),false));
            command(Command.openScene(players,List.of(monsters)),arrivals);
        }

        Command cast(UUID actor,String classKey,String spell,UUID target) {
            return Command.cast(actor,new CastInput(rules.rule("character.class_cantrips","cantrips."+classKey),
                    spell(spell),List.of(target),null,0,null));
        }

        Command reaction(Kind kind,UUID actor,String spell,UUID target,int slot,UUID castFrame) {
            var flow = flow();
            var window = window();
            var pending = window.opportunities().stream().filter(opportunity -> opportunity.status()==OpportunityStatus.PENDING)
                    .findFirst().orElseThrow();
            return Command.reactionSpell(kind,actor,new CastInput(wizardCasting(),spell(spell),List.of(target),null,slot,castFrame),
                    flow.flowKey(),flow.leafFrameKey(),window.windowKey(),pending.opportunityKey());
        }

        Command continueFlow(UUID actor) {
            var flow = flow();
            return new Command(Kind.CONTINUE_FLOW,actor,null,null,List.of(),List.of(),List.of(),
                    flow.flowKey(),flow.leafFrameKey(),window().windowKey(),null);
        }

        Window window() {
            var flow = flow();
            var leaf = flow.frames().stream().filter(frame -> frame.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
            return flow.windows().stream().filter(window -> window.windowKey().equals(leaf.windowKey())).findFirst().orElseThrow();
        }

        void nextTurn(UUID id) { command(Command.action(Kind.END_TURN,id)); }
        Battle battle() { return history.current(); }
        Flow flow() { return battle().flows().getLast(); }
        AttackAttempt attack() { return flow().attacks().getFirst(); }

        Resolution command(Command command,int...values) { return command(command,Map.of(),values); }

        Resolution command(Command command,Map<UUID,MonsterArrival> arriving,int...values) {
            var cursor = new AtomicInteger();
            var result = engine.executeScene(rules.projection(),List.copyOf(characters.values()),history,campaign,scene,second,
                    UUID.randomUUID(),command,arriving,sides -> {
                        assertTrue(cursor.get()<values.length,"Unexpected scene draw d"+sides);
                        return values[cursor.getAndIncrement()];
                    });
            assertEquals(values.length,cursor.get());
            history = result.history();
            second += result.elapsedSeconds();
            result.characters().forEach(character -> characters.put(character.history().current().characterId(),character));
            return history.events().getLast().resolution();
        }

        void reject(Command command,String code) {
            assertEquals(code,assertThrows(IllegalArgumentException.class,() -> command(command)).getMessage());
        }

        void preflightCandidate(Command command,Set<UUID> seen) {
            var roster = new HashMap<UUID,Schema2CombatRules.Context>();
            characters.forEach((id,character) -> roster.put(id,new Schema2CombatRules.Context(rules.projection(),
                    character.initial(),character.history().current(),second)));
            new Schema2CombatRules().preflightCandidate(UUID.randomUUID(),roster,battle(),command,seen);
        }

        void assertRoundTrip() {
            var restored = CampaignDomainCodec.decode(CampaignDomainCodec.encode(history),Schema2CombatHistory.History.class);
            assertEquals(history,restored);
            Schema2CombatHistory.validate(restored);
            history = restored;
        }
    }
}
