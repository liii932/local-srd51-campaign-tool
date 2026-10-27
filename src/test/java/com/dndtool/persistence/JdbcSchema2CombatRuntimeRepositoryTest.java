package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.Event;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.History;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.service.Schema2CombatState.Command;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcSchema2CombatRuntimeRepositoryTest {
    private static final JdbcSchema2CombatRuntimeRepository REPO = new JdbcSchema2CombatRuntimeRepository();
    private static final JdbcSchema2CharacterRuntimeRepository CHARACTERS = new JdbcSchema2CharacterRuntimeRepository();
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000000001"), B = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID CAMPAIGN = UUID.randomUUID(), COMBAT = UUID.randomUUID(), ITEM = UUID.randomUUID();
    private static final Identity IA = Identity.random(), IB = Identity.random();
    private static final UUID PA = UUID.randomUUID(), PB = UUID.randomUUID();
    private static final RuleIdentity CLASS = rule("character.class","class.guard"), COMBAT_RULE = rule("adventure.combat","combat.srd2014"), HEALTH = rule("adventure.health","health.srd2014");
    private static final List<Member> MEMBERS = List.of(new Member(A,IA,PA),new Member(B,IB,PB));
    private static final List<Dependency> DEPS = List.of(new Dependency(A,0),new Dependency(B,0));

    @Test void pendingWindowCompletePathAndImmutableAcceptedResultRoundTripAndRestore() throws Exception {
        History expected = pendingHistory(); var fixture = stored(expected);
        assertEquals(expected,REPO.read(fixture.connection,COMBAT));
        assertEquals(expected.events().get(1),REPO.findOperation(fixture.connection,expected.events().get(1).operationId()));
        assertEquals(FlowStatus.ACTIVE,REPO.findOperation(fixture.connection,expected.events().get(1).operationId()).after().activeFlow().status());
        assertNull(expected.current().activeFlow());
        assertEquals(20,expected.current().combatants().getFirst().movementSpentHalfFeet());
        assertEquals(WindowStatus.CONSUMED,expected.current().flows().getFirst().windows().getFirst().status());
        assertEquals(List.of(expected),REPO.findByCampaign(fixture.connection,CAMPAIGN));
        var target = seeded();try(var tx=BusinessWriteTransactions.openInternal(target.dataSource())){REPO.restore(tx.connection(),expected);tx.commit();}
        assertEquals(expected,REPO.read(target.connection,COMBAT));
        assertEquals(1,CHARACTERS.read(target.connection,A).events().size());
        assertEquals(1,CHARACTERS.read(target.connection,B).events().size());
    }

    @Test void attackDiceAndHealthCauseReadTheExistingCharacterOwnerWithoutDuplicatingHp() throws Exception {
        var fixture=seeded(); Battle initial=battle(0,List.of(),new Position(0,0),0);
        var start=start(initial); UUID op=UUID.randomUUID(),frame=UUID.randomUUID(),flow=UUID.randomUUID(),childOp=UUID.randomUUID();
        State before=CHARACTERS.read(fixture.connection,B).current(),after=character(B,IB,PB,1,0);
        var childCommand=new JdbcSchema2CharacterRuntimeRepository.Command("COMBAT_DAMAGE_APPLIED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,
                new HealthInput(HEALTH,null,null,"战斗伤害"),new CombatInput(COMBAT,op,frame,A,11,false));
        var child=new JdbcSchema2CharacterRuntimeRepository.Event(childOp,"c".repeat(64),childCommand,null,before,after,null,new HealthResult(List.of(),null,null,11,11));
        var attempt=new AttackAttempt(frame,A,B,ITEM,null,0,false,15,null,15,2,2,10,false,false,false,true,List.of(9),11);
        var f=new Flow(flow,frame,null,FlowStatus.COMPLETED,List.of(new Frame(frame,null,FrameKind.ATTACK,frame,Invocation.ROOT,FrameStatus.TERMINAL,Stage.ATTACK_RESOLVED,null,null)),List.of(),null,List.of(attempt));
        Battle ordinary=battle(1,List.of(f),new Position(0,0),0);
        var target=ordinary.combatants().get(1);
        Battle finalState=new Battle(ordinary.combatId(),ordinary.campaignId(),ordinary.combatRule(),ordinary.status(),ordinary.round(),ordinary.turnNumber(),ordinary.activeOrdinal(),ordinary.version(),
                List.of(ordinary.combatants().getFirst(),new Combatant(B,target.position(),target.side(),target.actionAvailable(),target.reactionAvailable(),0,0,false,false,null,true)),ordinary.initiative(),ordinary.flows());
        var health=new HealthChange(B,before,after,frame,11,false,child.healthResult(),false);
        Event attack=new Event(op,"b".repeat(64),Command.attack(A,B,ITEM),DEPS,List.of(childOp),initial,
                new Resolution(finalState,List.of(health),List.of(new Roll("ATTACK_D20",A,20,15),new Roll("DAMAGE",A,12,9)),List.of(new CombatFact("ATTACK_RESOLVED",A,frame,null))));
        try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){REPO.append(tx.connection(),MEMBERS,start);CHARACTERS.append(tx.connection(),child);REPO.append(tx.connection(),attack);tx.commit();}
        var read=REPO.read(fixture.connection,COMBAT);
        assertEquals(attack,read.events().getLast());assertEquals(List.of(9),read.current().flows().getFirst().attacks().getFirst().damageDice());
        assertTrue(read.current().combatants().get(1).prone());
        assertEquals(0,CHARACTERS.read(fixture.connection,B).current().hitPoints());
        assertFalse(fixture.rows("s2_combat_actor").getFirst().containsKey("hit_points"));
        fixture.rows("s2_character_combat_input").getFirst().put("combat_operation_id",bytes(UUID.randomUUID()));
        assertThrows(SQLException.class,()->REPO.read(fixture.connection,COMBAT));
    }

    @Test void roundTimeReferencesTheCharacterClockAndPreservesSixSecondResults() throws Exception {
        var fixture=seeded();Battle initial=battle(0,List.of(),new Position(0,0),0);
        Battle next=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,1,2,1,1,initial.combatants(),initial.initiative(),List.of());
        Event firstEnd=new Event(UUID.randomUUID(),"b".repeat(64),Command.action(Kind.END_TURN,A),DEPS,List.of(),initial,new Resolution(next,List.of(),List.of(),List.of()));
        UUID op=UUID.randomUUID();List<HealthChange>changes=new ArrayList<>();List<UUID>children=new ArrayList<>();
        try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){
            REPO.append(tx.connection(),MEMBERS,start(initial));REPO.append(tx.connection(),firstEnd);
            for(Member member:MEMBERS){
                State before=CHARACTERS.read(tx.connection(),member.characterId()).current();
                State after=new State(before.identity(),before.campaignId(),before.characterId(),before.preparationOperationId(),before.characterClass(),before.abilities(),before.level(),before.proficiencyBonus(),before.hitPoints(),before.maximumHitPoints(),before.classes(),before.hitDicePools(),before.subclasses(),before.features(),before.feats(),before.proficiencies(),1,before.resources(),new RestState(0,6,null,null,null),before.equipment(),before.health());
                UUID child=UUID.randomUUID(),frame=UUID.randomUUID();HealthResult health=new HealthResult(List.of(),null,null,0,0);
                var command=new JdbcSchema2CharacterRuntimeRepository.Command("COMBAT_TIME_ADVANCED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,new HealthInput(HEALTH,null,null,"战斗回合时间"),new CombatInput(COMBAT,op,frame,B,0,false,6));
                CHARACTERS.append(tx.connection(),new JdbcSchema2CharacterRuntimeRepository.Event(child,"c".repeat(64),command,null,before,after,null,health));
                children.add(child);changes.add(new HealthChange(member.characterId(),before,after,frame,0,false,health,false,true,6));
            }
            Battle round=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,2,3,0,2,initial.combatants(),initial.initiative(),List.of());
            REPO.append(tx.connection(),new Event(op,"d".repeat(64),Command.action(Kind.END_TURN,B),DEPS,children,next,new Resolution(round,changes,List.of(),List.of())));tx.commit();
        }
        var read=REPO.read(fixture.connection,COMBAT);assertEquals(changes,read.events().getLast().resolution().healthChanges());
        assertTrue(read.events().getLast().resolution().healthChanges().stream().allMatch(c->c.timeAdvance()&&c.seconds()==6));
        assertEquals(6,CHARACTERS.read(fixture.connection,A).current().rest().second());
        assertEquals(6,CHARACTERS.read(fixture.connection,B).current().rest().second());
    }

    @Test void everyCombatWriteFailureRollsBackRootWindowPathAndAcceptedHistory() throws Exception {
        History history=pendingHistory();var full=stored(history);int writes=full.writes-seeded().writes;
        assertTrue(writes>20);
        for(int point=1;point<=writes;point++){
            var fixture=seeded();fixture.failWrite=fixture.writes+point;
            try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){assertThrows(SQLException.class,()->REPO.restore(tx.connection(),history));tx.rollback();}
            assertTrue(fixture.rows("s2_combat_runtime").isEmpty(),"root retained at "+point);
            assertTrue(fixture.rows("s2_combat_event").isEmpty(),"event retained at "+point);
            assertTrue(fixture.rows("s2_combat_window").isEmpty(),"window retained at "+point);
            assertEquals(2,fixture.rows("s2_character_runtime").size());
        }
    }

    @Test void missingSurplusWrongIndicesAndCrossGraphRowsFailClosed() throws Exception {
        List<Consumer<Schema2RuntimeJdbcFixture>> mutations=List.of(
            f->f.rows("s2_combat_member").getFirst().put("member_index",1),
            f->f.rows("s2_combat_dependency").removeFirst(),
            f->f.rows("s2_combat_command_point").removeFirst(),
            f->f.rows("s2_combat_state").add(new LinkedHashMap<>(f.rows("s2_combat_state").getFirst())),
            f->f.rows("s2_combat_actor").getFirst().put("actor_index",7),
            f->f.rows("s2_combat_initiative").getFirst().put("total",99),
            f->f.rows("s2_combat_frame").getFirst().put("parent_frame_key",bytes(UUID.randomUUID())),
            f->f.rows("s2_combat_reaction_option").removeFirst(),
            f->f.rows("s2_combat_movement_point").getFirst().put("point_index",4),
            f->f.rows("s2_combat_window").getFirst().put("owner_frame_key",bytes(UUID.randomUUID())),
            f->f.rows("s2_combat_roll").getFirst().put("die_value",21));
        History history=pendingHistory();for(var mutation:mutations){var fixture=stored(history);mutation.accept(fixture);assertThrows(SQLException.class,()->REPO.read(fixture.connection,COMBAT));}
        var orphan=seeded();orphan.rows("s2_combat_frame").add(new LinkedHashMap<>(stored(history).rows("s2_combat_frame").getFirst()));
        assertThrows(SQLException.class,()->REPO.read(orphan.connection,COMBAT));
    }

    @Test void candidateAndMandatorySuffixCapacityRejectBeforeAnyMutation() throws Exception {
        var history=pendingHistory();
        assertDoesNotThrow(()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(null,null,8,2,3));
        assertDoesNotThrow(()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(null,null,16,16,0));
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(history,history.current(),128,2,3));
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(history,history.current(),30,16,64));
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(null,null,16,16,64));
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(null,null,-1,2,1));
        var fixture=seeded();assertThrows(SQLException.class,()->REPO.restore(fixture.connection,history));
        assertTrue(fixture.rows("s2_combat_event").isEmpty());
    }

    @Test void appendRequiresExactAdjacentStateAndRejectsReusedOperations() throws Exception {
        History history=pendingHistory();var fixture=stored(history);
        int writes=fixture.writes;
        try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){
            assertThrows(SQLException.class,()->REPO.append(tx.connection(),history.events().get(1)));tx.rollback();
        }
        assertEquals(writes,fixture.writes);
        List<Member> reversed=new ArrayList<>(MEMBERS);Collections.reverse(reversed);
        assertThrows(SQLException.class,()->JdbcSchema2CombatRuntimeRepository.validate(new History(history.current(),reversed,history.events())));
    }

    @Test void castSlotCostHealingAndOngoingInstancesKeepTypedCharacterOwnership() throws Exception {
        for(boolean healing:List.of(false,true)) {
            var scenario=spellScenario(healing);var fixture=spellStored(scenario);
            assertEquals(scenario.history(),REPO.read(fixture.connection,COMBAT));
            var cast=scenario.history().current().flows().getFirst().casts().getFirst();
            assertEquals(List.of(A),cast.targets());assertEquals(1,CHARACTERS.read(fixture.connection,A).current().resources().getFirst().current());
            assertEquals(healing?11:6,CHARACTERS.read(fixture.connection,A).current().hitPoints());
            assertEquals(healing?List.of(4):List.of(),cast.healingDice());
            assertFalse(fixture.rows("s2_combat_cast").getFirst().containsKey("hit_points"));
            var target=spellStored(scenario); // restore below starts from the same complete character facts only.
            for(String table:new ArrayList<>(target.tables.keySet()))if(table.startsWith("s2_combat_"))target.rows(table).clear();
            target.saveCommitted();
            try(var tx=BusinessWriteTransactions.openInternal(target.dataSource())){REPO.restore(tx.connection(),scenario.history());tx.commit();}
            assertEquals(scenario.history(),REPO.read(target.connection,COMBAT));
        }
    }

    @Test void typedSpellRowsRejectMissingExtraUnpairedBonusesAndBrokenCauseBeforeRecovery() throws Exception {
        var blessing=spellScenario(false);
        record Corruption(String name,Consumer<Schema2RuntimeJdbcFixture> apply) { }
        List<Corruption> corruptions=List.of(
                new Corruption("1 missing cast command",f->f.rows("s2_combat_cast_command").clear()),
                new Corruption("2 missing command target",f->f.rows("s2_combat_cast_command_target").clear()),
                new Corruption("3 missing ongoing effect",f->f.rows("s2_combat_ongoing_effect").clear()),
                new Corruption("4 discontinuous ongoing target",f->f.rows("s2_combat_ongoing_target").getFirst().put("target_index",5)),
                new Corruption("5 cast slot differs from character cost",f->f.rows("s2_combat_cast").getFirst().put("slot_key","resource.forged".getBytes(java.nio.charset.StandardCharsets.US_ASCII))),
                new Corruption("6 duplicate cast target",f->f.rows("s2_combat_cast_target").add(new LinkedHashMap<>(f.rows("s2_combat_cast_target").getFirst()))),
                new Corruption("7 missing saving throw",f->f.rows("s2_combat_saving_throw").clear()),
                new Corruption("8 blessing effect without d4",f->f.rows("s2_combat_saving_throw").getFirst().put("blessing_d4",null)),
                new Corruption("9 wrong character cause operation",f->f.rows("s2_character_combat_input").getFirst().put("combat_operation_id",bytes(UUID.randomUUID()))));
        for(var corruption:corruptions){var fixture=spellStored(blessing);corruption.apply().accept(fixture);assertThrows(SQLException.class,()->REPO.read(fixture.connection,COMBAT),corruption.name());}
        var healing=spellScenario(true);var fixture=spellStored(healing);fixture.rows("s2_combat_cast_die").clear();
        assertThrows(SQLException.class,()->REPO.read(fixture.connection,COMBAT));
    }

    @Test void everySpellGraphWriteFailureRollsBackNewTypedRows() throws Exception {
        var scenario=spellScenario(false);var complete=spellStored(scenario);
        int writes=complete.tables.entrySet().stream().filter(e->e.getKey().startsWith("s2_combat_")).mapToInt(e->e.getValue().size()).sum();
        for(int point=1;point<=writes;point++) {
            var fixture=spellStored(scenario);
            for(String table:new ArrayList<>(fixture.tables.keySet()))if(table.startsWith("s2_combat_"))fixture.rows(table).clear();
            fixture.saveCommitted();fixture.failWrite=fixture.writes+point;
            try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){assertThrows(SQLException.class,()->REPO.restore(tx.connection(),scenario.history()));tx.rollback();}
            assertTrue(fixture.rows("s2_combat_runtime").isEmpty());assertTrue(fixture.rows("s2_combat_cast").isEmpty());
            assertTrue(fixture.rows("s2_combat_ongoing_effect").isEmpty());assertTrue(fixture.rows("s2_combat_saving_throw").isEmpty());
            assertEquals(1,CHARACTERS.read(fixture.connection,A).current().resources().getFirst().current());
        }
    }

    @Test void completeReactionAndSuppressionGraphRoundTripsWithoutLosingEitherIndependentCause() throws Exception {
        var scenario=extendedScenario();var fixture=spellStored(scenario);
        assertEquals(scenario.history(),REPO.read(fixture.connection,COMBAT));
        Battle current=REPO.read(fixture.connection,COMBAT).current();assertEquals(Status.EXPLORATION,current.status());
        assertEquals(2,current.environmentZones().size());assertEquals(2,current.suppressionCauses().size());
        assertEquals(1,current.suppressionCauses().stream().filter(c->c.releasedOperationId()==null).count());
        Flow flow=current.flows().getFirst();assertEquals(2,flow.movements().size());assertEquals(ReactionKind.SHIELD,flow.windows().getFirst().opportunities().getFirst().options().getFirst().kind());
        assertEquals(1,flow.abilityChecks().size());assertEquals(1,flow.readyReleases().size());assertEquals(1,current.shields().size());assertEquals(1,current.readyPreparations().size());
        for(String table:List.of("s2_combat_reaction_option","s2_combat_ready_input","s2_combat_ready_point","s2_combat_window_trigger","s2_combat_ready_release","s2_combat_ability_check","s2_combat_ready","s2_combat_trigger","s2_combat_shield","s2_combat_concentration","s2_combat_environment","s2_combat_suppression"))assertFalse(fixture.rows(table).isEmpty(),table);
        var restored=spellStored(scenario);for(String table:new ArrayList<>(restored.tables.keySet()))if(table.startsWith("s2_combat_"))restored.rows(table).clear();restored.saveCommitted();
        try(var tx=BusinessWriteTransactions.openInternal(restored.dataSource())){REPO.restore(tx.connection(),scenario.history());tx.commit();}
        assertEquals(scenario.history(),REPO.read(restored.connection,COMBAT));
    }
    @Test void newTypedGraphRejectsMissingCauseHiddenOptionWrongCountsAndBrokenOwner() throws Exception {
        var scenario=extendedScenario();
        List<Consumer<Schema2RuntimeJdbcFixture>> bad=List.of(
            f->f.rows("s2_combat_suppression").removeLast(),
            f->f.rows("s2_combat_reaction_option").add(new LinkedHashMap<>(f.rows("s2_combat_reaction_option").getFirst())),
            f->f.rows("s2_combat_movement_point").getLast().put("movement_index",77),
            f->f.rows("s2_combat_state").getLast().put("holder_count",0),
            f->f.rows("s2_combat_ready_point").removeFirst(),
            f->f.rows("s2_combat_window_trigger").getFirst().put("trigger_key",bytes(UUID.randomUUID())),
            f->f.rows("s2_combat_concentration").getLast().put("source_key",bytes(UUID.randomUUID())),
            f->f.rows("s2_combat_environment").getLast().put("created_operation_id",bytes(UUID.randomUUID())));
        for(var mutation:bad){var fixture=spellStored(scenario);mutation.accept(fixture);assertThrows(SQLException.class,()->REPO.read(fixture.connection,COMBAT));}
    }
    @Test void heldSpellInputAndOriginalCastCompletionKeepOneCostAndTheRealReleaseHealthFrame() throws Exception {
        var scenario=heldSpellScenario();var fixture=spellStored(scenario);History read=REPO.read(fixture.connection,COMBAT);
        assertEquals(scenario.history(),read);assertEquals(1,read.events().stream().flatMap(e->e.resolution().healthChanges().stream()).filter(c->c.resource()!=null).count());
        var cast=read.current().flows().getFirst().casts().getFirst();var change=read.events().getLast().resolution().healthChanges().getFirst();
        assertEquals(CastStatus.RESOLVED,cast.status());assertNotEquals(cast.frameKey(),change.frameKey());assertEquals(List.of(4),cast.healingDice());
        assertEquals(read.current().readyPreparations().getFirst().readyKey(),cast.readyKey());assertTrue(read.current().concentrationHolders().isEmpty());
        assertFalse(fixture.rows("s2_combat_ready_target").isEmpty());assertEquals(1,CHARACTERS.read(fixture.connection,A).current().resources().getFirst().current());
        var target=spellStored(scenario);for(String table:new ArrayList<>(target.tables.keySet()))if(table.startsWith("s2_combat_"))target.rows(table).clear();target.saveCommitted();
        try(var tx=BusinessWriteTransactions.openInternal(target.dataSource())){REPO.restore(tx.connection(),scenario.history());tx.commit();}assertEquals(read,REPO.read(target.connection,COMBAT));
    }
    private static SpellScenario heldSpellScenario()throws Exception {
        var original=spellScenario(true);var originalCast=original.history().current().flows().getFirst().casts().getFirst();
        Battle initial=original.history().events().getFirst().after();Event costRoot=original.history().events().getLast();UUID readyKey=UUID.randomUUID(),moveOp=UUID.randomUUID(),releaseOp=UUID.randomUUID();
        UUID moveFlow=UUID.randomUUID(),moveFrame=UUID.randomUUID(),movementKey=UUID.randomUUID(),window=UUID.randomUUID(),opportunity=UUID.randomUUID(),selection=UUID.randomUUID(),releaseFrame=UUID.randomUUID(),triggerKey=UUID.randomUUID();
        var castInput=new CastInput(CASTING,originalCast.spell(),List.of(A),null);
        var readyInput=new ReadyInput(new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER,B,new Position(1,1)),ReadyResponseKind.SPELL,null,null,List.of(),castInput);
        var held=new CastAttempt(originalCast.frameKey(),A,CASTING,originalCast.spell(),List.of(A),SLOT,1,null,List.of(),2,0,0,null,CastStatus.HELD,null,readyKey);
        var castFlow=original.history().current().flows().getFirst();
        var heldFlow=new Flow(castFlow.flowKey(),castFlow.rootFrameKey(),null,FlowStatus.COMPLETED,castFlow.frames(),List.of(),List.of(),List.of(),List.of(held),List.of(),List.of(),List.of());
        var preparation=new ReadyPreparation(readyKey,A,1,3,readyInput,ReadyStatus.ARMED,held.frameKey(),null);
        Battle heldState=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,1,1,0,1,initial.combatants(),initial.initiative(),List.of(heldFlow),List.of(),List.of(preparation),List.of(),List.of(),List.of(new ConcentrationHolder(A,ConcentrationKind.READY_SPELL,readyKey)),List.of(),List.of());
        var costChange=costRoot.resolution().healthChanges().getFirst();var costChild=original.children().getFirst();
        Event prepared=new Event(costRoot.operationId(),"b".repeat(64),Command.ready(A,readyInput),DEPS,List.of(costChild.operationId()),initial,new Resolution(heldState,List.of(costChange),List.of(),List.of()));
        var deps=List.of(new Dependency(A,1),new Dependency(B,0));
        Battle nextTurn=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,1,2,1,2,heldState.combatants(),heldState.initiative(),heldState.flows(),List.of(),heldState.readyPreparations(),List.of(),List.of(),heldState.concentrationHolders(),List.of(),List.of());
        Event turn=new Event(UUID.randomUUID(),"a".repeat(64),Command.action(Kind.END_TURN,A),deps,List.of(),heldState,new Resolution(nextTurn,List.of(),List.of(),List.of()));
        var trigger=new TriggerFact(triggerKey,TriggerKind.CREATURE_ENTER_CELL_AFTER,moveFrame,B,null,new Position(1,1),0);
        var option=new ReactionOption(ReactionKind.READY_RELEASE,null,null,null,0,readyKey);
        var chance=new Opportunity(opportunity,A,0,List.of(option),OpportunityStatus.PENDING,selection,null,null);
        var moveFrames=List.of(new Frame(moveFrame,null,FrameKind.MOVEMENT,movementKey,Invocation.ROOT,FrameStatus.WAITING_WINDOW,Stage.READY_TRIGGER_AFTER,window,opportunity),new Frame(selection,moveFrame,FrameKind.REACTION_SELECTION,opportunity,Invocation.CONTINUATION,FrameStatus.PENDING,Stage.READY_TRIGGER_AFTER,window,opportunity));
        var move=new Movement(movementKey,B,new Position(0,1),List.of(new Position(1,1)),1,10,MovementStatus.PENDING,null);
        var waiting=new Flow(moveFlow,moveFrame,selection,FlowStatus.ACTIVE,moveFrames,List.of(new Window(window,moveFrame,0,0,WindowStatus.OPEN,List.of(chance),trigger)),List.of(move),List.of(),List.of(),List.of(),List.of(),List.of());
        var actors=List.of(initial.combatants().getFirst(),new Combatant(B,new Position(1,1),1,true,true,10,0,false,false,null));
        Battle paused=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,1,2,1,3,actors,initial.initiative(),List.of(heldFlow,waiting),List.of(),List.of(preparation),List.of(trigger),List.of(),heldState.concentrationHolders(),List.of(),List.of());
        Event movement=new Event(moveOp,"d".repeat(64),Command.move(B,List.of(new Position(1,1))),deps,List.of(),nextTurn,new Resolution(paused,List.of(),List.of(),List.of()));
        var resolved=new CastAttempt(held.frameKey(),A,CASTING,held.spell(),List.of(A),SLOT,1,null,List.of(4),2,6,5,null,CastStatus.RESOLVED,null,readyKey);
        var finishedCast=new Flow(heldFlow.flowKey(),heldFlow.rootFrameKey(),null,FlowStatus.COMPLETED,heldFlow.frames(),List.of(),List.of(),List.of(),List.of(resolved),List.of(),List.of(),List.of());
        var frames=new ArrayList<>(moveFrames);frames.set(0,new Frame(moveFrame,null,FrameKind.MOVEMENT,movementKey,Invocation.ROOT,FrameStatus.RUNNABLE,Stage.MOVEMENT_CONTINUE,null,null));frames.set(1,new Frame(selection,moveFrame,FrameKind.REACTION_SELECTION,opportunity,Invocation.CONTINUATION,FrameStatus.DECIDED,Stage.READY_TRIGGER_AFTER,window,opportunity));frames.add(new Frame(releaseFrame,selection,FrameKind.READY_RELEASE,readyKey,Invocation.REACTION,FrameStatus.TERMINAL,Stage.READY_RELEASE_RESOLVED,null,null));
        var completeChance=new Opportunity(opportunity,A,0,List.of(option),OpportunityStatus.COMPLETED,selection,releaseFrame,null);
        var resumed=new Flow(moveFlow,moveFrame,moveFrame,FlowStatus.ACTIVE,frames,List.of(new Window(window,moveFrame,0,0,WindowStatus.CLOSED,List.of(completeChance),trigger)),List.of(move),List.of(),List.of(),List.of(),List.of(new ReadyRelease(releaseFrame,readyKey,triggerKey,true,"SPELL_RELEASED")),List.of());
        Battle after=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,1,2,1,4,actors,initial.initiative(),List.of(finishedCast,resumed),List.of(),List.of(new ReadyPreparation(readyKey,A,1,3,readyInput,ReadyStatus.RELEASED,held.frameKey(),null)),List.of(trigger),List.of(),List.of(),List.of(),List.of());
        var priorHeal=original.children().getLast();var healthInput=priorHeal.command().healthInput();
        var healCommand=new JdbcSchema2CharacterRuntimeRepository.Command("COMBAT_HEALING_APPLIED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,healthInput,new CombatInput(COMBAT,releaseOp,releaseFrame,A,6,false));
        var healChild=new JdbcSchema2CharacterRuntimeRepository.Event(UUID.randomUUID(),"e".repeat(64),healCommand,null,priorHeal.before(),priorHeal.after(),null,priorHeal.healthResult());
        var healing=new HealthChange(A,priorHeal.before(),priorHeal.after(),releaseFrame,6,false,priorHeal.healthResult(),false,false,0,null,0,true);
        var released=new Event(releaseOp,"f".repeat(64),Command.releaseReady(A,readyKey,moveFlow,selection,window,opportunity),deps,List.of(healChild.operationId()),paused,new Resolution(after,List.of(healing),List.of(),List.of()));
        return new SpellScenario(new History(after,MEMBERS,List.of(original.history().events().getFirst(),prepared,turn,movement,released)),List.of(costChild,healChild));
    }

    private static SpellScenario extendedScenario()throws Exception {
        var original=spellScenario(false);Battle before=original.history().current();UUID operation=UUID.randomUUID();
        Flow old=before.flows().getFirst();UUID root=old.rootFrameKey(),child=UUID.randomUUID(),selection=UUID.randomUUID(),window=UUID.randomUUID(),ready=UUID.randomUUID(),trigger=UUID.randomUUID();
        TriggerFact fact=new TriggerFact(trigger,TriggerKind.ATTACK_COMPLETED,root,A,B,new Position(0,0),0);
        var options=List.of(new ReactionOption(ReactionKind.SHIELD,null,rule("character.wizard_spellcasting","spellcasting.wizard"),rule("adventure.spell","spell.shield"),1,null));
        var opportunity=new Opportunity(UUID.randomUUID(),B,0,options,OpportunityStatus.COMPLETED,selection,child,null);
        var frames=new ArrayList<>(old.frames());frames.add(new Frame(child,root,FrameKind.READY_RELEASE,ready,Invocation.REACTION,FrameStatus.TERMINAL,Stage.READY_RELEASE_RESOLVED,null,null));frames.add(new Frame(selection,root,FrameKind.REACTION_SELECTION,opportunity.opportunityKey(),Invocation.CONTINUATION,FrameStatus.DECIDED,Stage.ATTACK_RESOLVED,window,opportunity.opportunityKey()));
        List<Movement> movements=List.of(new Movement(UUID.randomUUID(),A,new Position(0,0),List.of(new Position(1,0)),1,10,MovementStatus.COMPLETED,null,false,0),new Movement(UUID.randomUUID(),B,new Position(0,1),List.of(new Position(1,1),new Position(2,1)),2,20,MovementStatus.COMPLETED,null,true,30));
        var flow=new Flow(old.flowKey(),root,null,FlowStatus.COMPLETED,frames,List.of(new Window(window,root,0,0,WindowStatus.CONSUMED,List.of(opportunity),fact)),movements,old.attacks(),old.casts(),old.savingThrows(),List.of(new ReadyRelease(child,ready,trigger,true,"COMPLETED")),List.of(new AbilityCheckAttempt(root,A,"COUNTERSPELL","ability.intelligence",12,2,0,13,14,true)));
        var readyInput=new ReadyInput(new ReadyTrigger(TriggerKind.CREATURE_ENTER_CELL_AFTER,B,new Position(1,1)),ReadyResponseKind.MOVE,null,null,List.of(new Position(1,0)),null);
        var preparation=new ReadyPreparation(ready,A,1,3,readyInput,ReadyStatus.RELEASED,null,"RELEASED");
        var zoneRule=rule("adventure.environment","environment.fixed_antimagic");UUID z1=UUID.randomUUID(),z2=UUID.randomUUID(),effect=before.ongoingEffects().getFirst().effectKey();
        var zones=List.of(new EnvironmentZone(z1,zoneRule,new Position(0,0),operation,null),new EnvironmentZone(z2,zoneRule,new Position(3,0),operation,null));
        var causes=List.of(new SuppressionCause(UUID.randomUUID(),z1,effect,A,operation,null,null),new SuppressionCause(UUID.randomUUID(),z2,effect,A,operation,operation,SuppressionReleaseReason.LEFT_ZONE));
        Battle after=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.EXPLORATION,1,1,0,2,before.combatants(),before.initiative(),List.of(flow),before.ongoingEffects(),List.of(preparation),List.of(fact),List.of(new ShieldEffect(UUID.randomUUID(),root,B,1,3,EffectStatus.ENDED,"DURATION_EXPIRED")),before.concentrationHolders(),zones,causes);
        var command=new Command(Kind.CREATE_ENVIRONMENT_ZONE,A,null,null,List.of(),List.of(),List.of(),null,null,null,null,null,null,null,new ZoneInput(zoneRule,new Position(0,0)),null);
        var event=new Event(operation,"f".repeat(64),command,List.of(new Dependency(A,1),new Dependency(B,0)),List.of(),before,new Resolution(after,List.of(),List.of(),List.of()));
        var events=new ArrayList<>(original.history().events());events.add(event);return new SpellScenario(new History(after,MEMBERS,events),original.children());
    }

    private record SpellScenario(History history,List<JdbcSchema2CharacterRuntimeRepository.Event> children) { }
    private static final RuleIdentity SLOT=rule("character.resource","resource.spell_slot_1"), CASTING=rule("character.spellcasting","spellcasting.cleric");
    private static SpellScenario spellScenario(boolean healing) throws Exception {
        RuleIdentity spell=rule("adventure.spell",healing?"spell.cure_wounds":"spell.bless");
        UUID operation=UUID.randomUUID(),frame=UUID.randomUUID(),flow=UUID.randomUUID(),effect=healing?null:UUID.randomUUID();
        Battle initial=battle(0,List.of(),new Position(0,0),0);State before=withSlots(character(A,IA,PA,0,6),2);
        State spent=withSlots(character(A,IA,PA,1,6),1);
        var resourceCommand=new JdbcSchema2CharacterRuntimeRepository.Command("COMBAT_RESOURCE_SPENT",SLOT,1,null,null,null,GrowthChoices.EMPTY,null,null,null,null,new CombatInput(COMBAT,operation,frame,A,0,false));
        var cost=new JdbcSchema2CharacterRuntimeRepository.Event(UUID.randomUUID(),"c".repeat(64),resourceCommand,null,before,spent);
        List<JdbcSchema2CharacterRuntimeRepository.Event> children=new ArrayList<>(List.of(cost));
        List<HealthChange> changes=new ArrayList<>(List.of(new HealthChange(A,before,spent,frame,0,false,null,false,false,0,SLOT,1,false)));
        if(healing) {
            State healed=withSlots(character(A,IA,PA,2,11),1);HealthResult result=new HealthResult(List.of(4),null,null,6,5);
            var command=new JdbcSchema2CharacterRuntimeRepository.Command("COMBAT_HEALING_APPLIED",null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,new HealthInput(HEALTH,null,null,"法术治疗"),new CombatInput(COMBAT,operation,frame,A,6,false));
            children.add(new JdbcSchema2CharacterRuntimeRepository.Event(UUID.randomUUID(),"d".repeat(64),command,null,spent,healed,null,result));
            changes.add(new HealthChange(A,spent,healed,frame,6,false,result,false,false,0,null,0,true));
        }
        var cast=new CastAttempt(frame,A,CASTING,spell,List.of(A),SLOT,1,null,healing?List.of(4):List.of(),healing?2:0,healing?6:0,healing?5:0,effect);
        List<SavingThrowAttempt> saves=healing?List.of():List.of(new SavingThrowAttempt(frame,A,"CONCENTRATION","ability.constitution",12,1,0,effect,3,10,16,true));
        var typedFlow=new Flow(flow,frame,null,FlowStatus.COMPLETED,List.of(new Frame(frame,null,FrameKind.CAST,frame,Invocation.ROOT,FrameStatus.TERMINAL,Stage.CAST_RESOLVED,null,null)),List.of(),null,List.of(),List.of(cast),saves);
        Battle after=new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,1,1,0,1,initial.combatants(),initial.initiative(),List.of(typedFlow),healing?List.of():List.of(new OngoingEffect(effect,spell,A,List.of(A),0,60,EffectStatus.ACTIVE,null)),List.of(),List.of(),List.of(),healing?List.of():List.of(new ConcentrationHolder(A,ConcentrationKind.BLESS_EFFECT,effect)),List.of(),List.of());
        Event accepted=new Event(operation,"b".repeat(64),new Command(Kind.CAST,A,null,null,List.of(),List.of(),List.of(),null,null,null,null,new CastInput(CASTING,spell,List.of(A),null)),DEPS,children.stream().map(JdbcSchema2CharacterRuntimeRepository.Event::operationId).toList(),initial,new Resolution(after,changes,List.of(),List.of()));
        return new SpellScenario(new History(after,MEMBERS,List.of(start(initial),accepted)),children);
    }
    private static Schema2RuntimeJdbcFixture spellStored(SpellScenario scenario)throws Exception {
        var fixture=new Schema2RuntimeJdbcFixture();for(Member m:MEMBERS)fixture.rows("s2_initial_plan").add(new LinkedHashMap<>(Map.of("operation_id",bytes(m.preparationOperationId()),"run_id",m.identity().runBytes(),"snapshot_id",m.identity().snapshotBytes(),"campaign_id",bytes(CAMPAIGN),"character_id",bytes(m.characterId()))));
        fixture.dataSource();fixture.saveCommitted();
        try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())) {
            for(Member m:MEMBERS){State state=character(m.characterId(),m.identity(),m.preparationOperationId(),0,m.characterId().equals(A)?6:11);if(m.characterId().equals(A))state=withSlots(state,2);CHARACTERS.append(tx.connection(),new JdbcSchema2CharacterRuntimeRepository.Event(UUID.randomUUID(),"e".repeat(64),new JdbcSchema2CharacterRuntimeRepository.Command("INITIALIZED",null,0,null,null),null,null,state));}
            for(var child:scenario.children())CHARACTERS.append(tx.connection(),child);
            REPO.restore(tx.connection(),scenario.history());tx.commit();
        }
        return fixture;
    }
    private static State withSlots(State state,long value) {
        return new State(state.identity(),state.campaignId(),state.characterId(),state.preparationOperationId(),state.characterClass(),state.abilities(),state.level(),state.proficiencyBonus(),state.hitPoints(),state.maximumHitPoints(),state.classes(),state.hitDicePools(),state.subclasses(),state.features(),state.feats(),state.proficiencies(),state.version(),List.of(new ResourceState(SLOT,value,2,false)),state.rest(),state.equipment(),state.health());
    }

    private static History pendingHistory(){
        Battle initial=battle(0,List.of(),new Position(0,0),0);Event first=start(initial);
        UUID flow=UUID.randomUUID(),root=UUID.randomUUID(),movement=UUID.randomUUID(),window=UUID.randomUUID(),opportunity=UUID.randomUUID(),selection=UUID.randomUUID();
        List<Position>path=List.of(new Position(1,0),new Position(2,0));
        var rootWaiting=new Frame(root,null,FrameKind.MOVEMENT,movement,Invocation.ROOT,FrameStatus.WAITING_WINDOW,Stage.MOVEMENT_LEAVE_REACH_BEFORE,window,null);
        var select=new Frame(selection,root,FrameKind.REACTION_SELECTION,opportunity,Invocation.REACTION,FrameStatus.PENDING,Stage.MOVEMENT_LEAVE_REACH_BEFORE,window,opportunity);
        var chance=new Opportunity(opportunity,B,0,List.of(new ReactionOption(ReactionKind.OPPORTUNITY_ATTACK,ITEM,null,null,0,null)),OpportunityStatus.PENDING,selection,null,null);
        var waiting=new Flow(flow,root,selection,FlowStatus.ACTIVE,List.of(rootWaiting,select),List.of(new Window(window,root,0,0,WindowStatus.OPEN,List.of(chance))),new Movement(movement,A,new Position(0,0),path,0,0,MovementStatus.PENDING,null),List.of());
        Battle paused=battle(1,List.of(waiting),new Position(0,0),0);
        Event move=new Event(UUID.randomUUID(),"b".repeat(64),Command.move(A,path),DEPS,List.of(),initial,new Resolution(paused,List.of(),List.of(),List.of(new CombatFact("MOVEMENT_PAUSED",A,window,null))));
        var decided=new Frame(selection,root,FrameKind.REACTION_SELECTION,opportunity,Invocation.REACTION,FrameStatus.DECIDED,Stage.MOVEMENT_LEAVE_REACH_BEFORE,window,opportunity);
        var passed=new Opportunity(opportunity,B,0,List.of(new ReactionOption(ReactionKind.OPPORTUNITY_ATTACK,ITEM,null,null,0,null)),OpportunityStatus.PASSED,selection,null,"PASS");
        var closed=new Flow(flow,root,root,FlowStatus.ACTIVE,List.of(rootWaiting,decided),List.of(new Window(window,root,0,0,WindowStatus.CLOSED,List.of(passed))),waiting.movement(),List.of());
        Battle afterPass=battle(2,List.of(closed),new Position(0,0),0);
        Event pass=new Event(UUID.randomUUID(),"c".repeat(64),Command.reaction(Kind.PASS_REACTION,B,null,flow,selection,window,opportunity),DEPS,List.of(),paused,new Resolution(afterPass,List.of(),List.of(),List.of(new CombatFact("REACTION_PASSED",B,opportunity,"PASS"))));
        var terminal=new Frame(root,null,FrameKind.MOVEMENT,movement,Invocation.ROOT,FrameStatus.TERMINAL,Stage.MOVEMENT_COMPLETED,window,null);
        var completed=new Flow(flow,root,null,FlowStatus.COMPLETED,List.of(terminal,decided),List.of(new Window(window,root,0,0,WindowStatus.CONSUMED,List.of(passed))),new Movement(movement,A,new Position(0,0),path,2,10,MovementStatus.COMPLETED,null),List.of());
        Battle finalState=battle(3,List.of(completed),new Position(2,0),20);
        Event continued=new Event(UUID.randomUUID(),"d".repeat(64),Command.continuation(A,flow,root,window),DEPS,List.of(),afterPass,new Resolution(finalState,List.of(),List.of(),List.of(new CombatFact("MOVEMENT_COMPLETED",A,movement,null))));
        return new History(finalState,MEMBERS,List.of(first,move,pass,continued));
    }
    private static Event start(Battle initial){return new Event(UUID.randomUUID(),"a".repeat(64),Command.start(List.of(new Deployment(A,new Position(0,0),0),new Deployment(B,new Position(0,1),1)),List.of()),DEPS,List.of(),null,
            new Resolution(initial,List.of(),List.of(new Roll("INITIATIVE",A,20,15),new Roll("INITIATIVE",B,20,10)),List.of(new CombatFact("COMBAT_STARTED",null,null,null))));}
    private static Battle battle(long version,List<Flow>flows,Position a,int spent){return new Battle(COMBAT,CAMPAIGN,COMBAT_RULE,Status.ACTIVE,1,1,0,version,List.of(new Combatant(A,a,0,true,true,spent,0,false,false,null),new Combatant(B,new Position(0,1),1,true,true,0,0,false,false,null)),List.of(new Initiative(A,15,2,17,0),new Initiative(B,10,2,12,1)),flows);}
    private static Schema2RuntimeJdbcFixture stored(History history)throws Exception{var fixture=seeded();try(var tx=BusinessWriteTransactions.openInternal(fixture.dataSource())){REPO.restore(tx.connection(),history);tx.commit();}return fixture;}
    private static Schema2RuntimeJdbcFixture seeded()throws Exception{
        var f=new Schema2RuntimeJdbcFixture();for(Member m:MEMBERS)f.rows("s2_initial_plan").add(new LinkedHashMap<>(Map.of("operation_id",bytes(m.preparationOperationId()),"run_id",m.identity().runBytes(),"snapshot_id",m.identity().snapshotBytes(),"campaign_id",bytes(CAMPAIGN),"character_id",bytes(m.characterId()))));
        f.dataSource();f.saveCommitted();try(var tx=BusinessWriteTransactions.openInternal(f.dataSource())){for(Member m:MEMBERS){State s=character(m.characterId(),m.identity(),m.preparationOperationId(),0,11);var e=new JdbcSchema2CharacterRuntimeRepository.Event(UUID.randomUUID(),"e".repeat(64),new JdbcSchema2CharacterRuntimeRepository.Command("INITIALIZED",null,0,null,null),null,null,s);CHARACTERS.restore(tx.connection(),new JdbcSchema2CharacterRuntimeRepository.History(s,List.of(e)));}tx.commit();}return f;
    }
    private static State character(UUID id,Identity identity,UUID preparation,long version,long hp){return new State(identity,CAMPAIGN,id,preparation,CLASS,Map.of("ability.strength",15,"ability.dexterity",14,"ability.constitution",13,"ability.intelligence",12,"ability.wisdom",10,"ability.charisma",8),1,2,hp,11,List.of(new ClassLevel(CLASS,1)),List.of(new HitDicePool(10,1,1)),List.of(),List.of(),List.of(),List.of(),version,List.of(),RestState.INITIAL,List.of(),HealthState.INITIAL);}
    private static RuleIdentity rule(String type,String key){return new RuleIdentity("base",type,key);}
    private static byte[]bytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
}
