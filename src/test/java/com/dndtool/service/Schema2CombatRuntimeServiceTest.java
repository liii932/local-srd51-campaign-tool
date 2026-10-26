package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.Member;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatRuntimeService.*;
import com.dndtool.service.Schema2CombatRuntimeService.Status;
import com.dndtool.service.Schema2CombatState.*;
import com.dndtool.testing.IsolatedJvmTest;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2CombatRuntimeServiceTest {
    @Test void roundClockAndIncomingDeathSaveAppendTwoOrderedChildrenForTheSameCharacter() throws Exception {
        Fixture f=new Fixture(true); f.start(); f.commit(Command.move(f.a,List.of(new Position(-1,0))));
        f.nextRolls.addAll(List.of(20,8,8)); f.commit(f.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK));
        assertEquals(0,f.character(f.a).hitPoints()); f.commit(f.continuation());
        assertEquals(MovementStatus.STOPPED,f.battle.flows().getLast().movement().status());
        f.commit(Command.action(Kind.END_TURN,f.a)); f.nextRolls.add(20);
        var round=f.commit(Command.action(Kind.END_TURN,f.b));
        assertEquals(3,round.event().characterOperations().size());
        assertEquals(3,new HashSet<>(round.event().characterOperations()).size());
        var changes=round.event().resolution().healthChanges();
        assertTrue(changes.getFirst().timeAdvance()); assertTrue(changes.getLast().deathSave());
        assertEquals(changes.getFirst().after(),changes.getLast().before());
        assertEquals(1,f.character(f.a).hitPoints()); assertEquals(6,f.character(f.a).rest().second());
        Save save=f.service.read(f.combat); Fixture restored=new Fixture(false);
        assertEquals(Status.COMMITTED,restored.service.restore(save).status()); assertEquals(save,restored.service.read(f.combat));
    }
    @Test void unconsciousDropWakeProneAndRoundSecondsRemainOneRecoverableCharacterHistory() throws Exception {
        Fixture f=new Fixture(true); f.start(); f.commit(Command.action(Kind.BEGIN_ATTACK,f.a));
        f.nextRolls.addAll(List.of(20,8,8)); f.commit(Command.attack(f.a,f.b,f.items.get(f.a)));
        assertEquals(0,f.character(f.b).hitPoints()); assertTrue(f.actor(f.b).prone());
        var dropped=f.character(f.b).equipment().getFirst();
        assertEquals("GROUND",dropped.location()); assertEquals(f.combat,dropped.groundCombatId());
        assertEquals(1,dropped.groundX()); assertEquals(0,dropped.groundY());
        f.nextRolls.add(20); f.commit(Command.action(Kind.END_TURN,f.a));
        assertEquals(1,f.character(f.b).hitPoints()); assertTrue(f.actor(f.b).prone());
        f.commit(Command.action(Kind.STAND_UP,f.b)); assertFalse(f.actor(f.b).prone());
        assertEquals(30,f.actor(f.b).movementSpentHalfFeet());
        var nextRound=f.commit(Command.action(Kind.END_TURN,f.b));
        assertEquals(2,nextRound.event().characterOperations().size());
        for(UUID id:List.of(f.a,f.b)) { assertEquals(0,f.character(id).rest().minute()); assertEquals(6,f.character(id).rest().second()); }
        assertEquals("GROUND",f.character(f.b).equipment().getFirst().location());
        Save save=f.service.read(f.combat); Fixture restored=new Fixture(false);
        assertEquals(Status.COMMITTED,restored.service.restore(save).status()); assertEquals(save,restored.service.read(f.combat)); assertEquals(0,restored.rolls);
    }
    @Test void actualOpportunityAttackCommitsBeforeMovementAndResumesExactlyOnce() throws Exception {
        Fixture f=new Fixture(true); f.start();
        long hp=f.character(f.a).hitPoints();
        Request move=f.request(Command.move(f.a,List.of(new Position(-1,0))));
        var paused=f.commit(move); Flow flow=f.battle.activeFlow();
        assertNotNull(flow); assertEquals(new Position(0,0),f.actor(f.a).position());
        assertEquals(0,f.actor(f.a).movementSpentHalfFeet());
        assertEquals(WindowStatus.OPEN,flow.windows().getLast().status());
        int rolls=f.rolls,writes=f.runtime.writes;
        assertEquals(Status.REPLAYED,f.service.execute(move).status()); assertEquals(rolls,f.rolls); assertEquals(writes,f.runtime.writes);
        var oldCharacter=f.character(f.a);
        var ordinary=f.characterRequest(f.a,new JdbcSchema2CharacterRuntimeRepository.Command("RESOURCE_SPENT",ENERGY,1,null,null));
        assertEquals("S2_CHARACTER_COMBAT_ACTIVE",f.characterService.execute(ordinary).code());
        assertEquals(oldCharacter,f.character(f.a));
        f.nextRolls.addAll(List.of(15,1));
        Request reaction=f.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK);
        var hit=f.commit(reaction);
        assertEquals(1,hit.event().resolution().healthChanges().size());
        assertTrue(f.character(f.a).hitPoints()<hp); assertFalse(f.actor(f.b).reactionAvailable());
        assertEquals(new Position(0,0),f.actor(f.a).position());
        assertEquals(WindowStatus.CLOSED,f.battle.activeFlow().windows().getLast().status());
        rolls=f.rolls; writes=f.runtime.writes;
        assertEquals(Status.REPLAYED,f.service.execute(reaction).status()); assertEquals(rolls,f.rolls); assertEquals(writes,f.runtime.writes);
        Request resume=f.continuation(); f.commit(resume);
        assertNull(f.battle.activeFlow()); assertEquals(new Position(-1,0),f.actor(f.a).position()); assertEquals(10,f.actor(f.a).movementSpentHalfFeet());
        assertEquals(WindowStatus.CONSUMED,f.battle.flows().getLast().windows().getLast().status());
        assertEquals(Status.REPLAYED,f.service.execute(resume).status()); assertEquals(rolls,f.rolls);
        assertEquals(Status.REJECTED,f.service.execute(f.request(resume.command())).status());
        assertEquals(paused.event(),f.service.inspect(move).event());
        f.commit(Command.action(Kind.END_TURN,f.a)); assertTrue(f.actor(f.b).reactionAvailable());
    }

    @Test void pendingSaveRestoresWithoutSourceDiceOrImplicitPassAndRejectsForgery() throws Exception {
        Fixture f=new Fixture(true); f.start(); f.commit(Command.move(f.a,List.of(new Position(-1,0))));
        Save pending=f.service.read(f.combat);
        Fixture restored=new Fixture(false); restored.source.unavailable(true);
        assertEquals(Status.COMMITTED,restored.service.restore(pending).status()); assertEquals(0,restored.rolls);
        assertEquals(pending,restored.service.read(f.combat));
        assertEquals(Status.REPLAYED,restored.service.inspectRestore(pending).status());
        restored.battle=pending.combat().current(); restored.commit(restored.reaction(Kind.PASS_REACTION));
        assertEquals(WindowStatus.CLOSED,restored.battle.activeFlow().windows().getLast().status());
        Save closed=restored.service.read(f.combat);
        Fixture closedTarget=new Fixture(false); assertEquals(Status.COMMITTED,closedTarget.service.restore(closed).status());
        closedTarget.battle=closed.combat().current(); closedTarget.commit(closedTarget.continuation());
        assertEquals(new Position(-1,0),closedTarget.actor(f.a).position()); assertEquals(0,closedTarget.rolls);
        var last=pending.combat().events().getLast(); var deps=new ArrayList<>(last.dependencies());
        deps.set(0,new JdbcSchema2CombatRuntimeRepository.Dependency(deps.getFirst().characterId(),0));
        var altered=new JdbcSchema2CombatRuntimeRepository.Event(last.operationId(),last.requestDigest(),last.command(),deps,last.characterOperations(),last.before(),last.resolution());
        var events=new ArrayList<>(pending.combat().events()); events.set(events.size()-1,altered);
        Save forged=new Save(new JdbcSchema2CombatRuntimeRepository.History(pending.combat().current(),pending.combat().members(),events),pending.characters());
        Fixture reject=new Fixture(false); int writes=reject.runtime.writes;
        assertEquals(Status.REJECTED,reject.service.restore(forged).status()); assertEquals(writes,reject.runtime.writes);
        assertFalse(BusinessWriteTransactions.isBlocked());
    }

    @Test void ordinaryAttackUsesActualInventoryAcAndHealthAndDoesNotSpendActionTwice() throws Exception {
        Fixture f=new Fixture(true); f.start(); f.commit(Command.action(Kind.BEGIN_ATTACK,f.a));
        assertFalse(f.actor(f.a).actionAvailable());
        f.nextRolls.addAll(List.of(20,1,1));
        var hit=f.commit(Command.attack(f.a,f.b,f.items.get(f.a)));
        AttackAttempt attack=f.battle.flows().getLast().attacks().getFirst();
        assertTrue(attack.hit()); assertTrue(attack.critical()); assertEquals(List.of(1,1),attack.damageDice());
        assertEquals(1,f.actor(f.a).sequence().performed()); assertTrue(f.actor(f.a).sequence().closed());
        var child=f.characterService.read(f.b).events().getLast();
        assertEquals("COMBAT_DAMAGE_APPLIED",child.kind()); assertEquals(hit.event().operationId(),child.command().combatInput().combatOperationId());
        int rolls=f.rolls,writes=f.runtime.writes;
        assertEquals(Status.REJECTED,f.service.execute(f.request(Command.attack(f.a,f.b,f.items.get(f.a)))).status());
        assertEquals(rolls,f.rolls); assertEquals(writes,f.runtime.writes);
        var childRequest=new Schema2CharacterRuntimeService.Request(UUID.randomUUID(),child.after().identity(),f.campaign,f.b,
                child.after().preparationOperationId(),f.binding,child.after().version(),child.command(),Map.of());
        assertEquals("S2_CHARACTER_COMBAT_OWNER_REQUIRED",f.characterService.execute(childRequest).code());
        Save saved=f.service.read(f.combat); Fixture restored=new Fixture(false);
        assertEquals(Status.COMMITTED,restored.service.restore(saved).status()); assertEquals(saved,restored.service.read(f.combat)); assertEquals(0,restored.rolls);
        f.commit(Command.action(Kind.END_TURN,f.a));
        f.commit(Command.action(Kind.END_COMBAT,f.b));
        assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(f.characterRequest(f.a,
                new JdbcSchema2CharacterRuntimeRepository.Command("RESOURCE_SPENT",ENERGY,1,null,null))).status());
    }

    @Test void staleVersionsUnknownTokensBadPathAndCapacityRejectBeforeRandomnessOrWrites() throws Exception {
        Fixture f=new Fixture(true); f.start(); Request stale=f.request(Command.action(Kind.DASH,f.a));
        f.commit(Command.action(Kind.DISENGAGE,f.a)); int rolls=f.rolls,writes=f.runtime.writes;
        assertEquals("S2_COMBAT_STALE_VERSION",f.service.execute(stale).code());
        assertEquals(Status.REJECTED,f.service.execute(f.request(Command.move(f.a,List.of(new Position(10,0))))).status());
        var request=f.request(Command.move(f.a,List.of(new Position(-1,0)))); var versions=new HashMap<>(request.expectedCharacterVersions()); versions.put(f.a,0L);
        assertEquals("S2_COMBAT_STALE_CHARACTER",f.service.execute(new Request(request.operationId(),request.combatId(),request.campaignId(),request.rules(),
                request.members(),request.expectedVersion(),versions,request.command())).code());
        assertEquals(rolls,f.rolls); assertEquals(writes,f.runtime.writes);
        f.commit(Command.move(f.a,List.of(new Position(-1,0)))); assertNull(f.battle.activeFlow());
        assertEquals(rolls,f.rolls);
    }

    @Test void damageAndCombatFailuresRollBackTogether() throws Exception {
        for(String table:List.of("s2_character_combat_input","s2_combat_attack","s2_combat_event")) IsolatedJvmTest.run(FaultProbe.class,"rollback",table);
    }
    @Test void unknownCommitHasReadOnlyOriginalOutcomeResolution() throws Exception {
        for(String outcome:List.of("COMMITTED","ROLLED_BACK")) IsolatedJvmTest.run(FaultProbe.class,"unknown",outcome);
    }
    @Test void missingFrozenHistoryOrCausalChildStopsBeforeAnyMoreDice() throws Exception {
        for(String table:List.of("s2_combat_opportunity","s2_character_combat_input")) IsolatedJvmTest.run(FaultProbe.class,"corrupt",table);
    }
    public static final class FaultProbe {
        public static void main(String[] args) throws Exception {
            Fixture f=new Fixture(true); f.start(); f.commit(Command.move(f.a,List.of(new Position(-1,0))));
            Request reaction=f.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK); Save before=f.service.read(f.combat);
            f.nextRolls.addAll(List.of(15,1)); int rolls=f.rolls;
            if(args[0].equals("rollback")) {
                int count=f.runtime.rows(args[1]).size();
                f.runtime.afterWrite=db->{if(db.rows(args[1]).size()>count)throw new IllegalStateException("combat injected write failure");};
                assertEquals(Status.REJECTED,f.service.execute(reaction).status()); assertEquals(before,f.service.read(f.combat));
                assertEquals(rolls+2,f.rolls);
            } else if(args[0].equals("unknown")) {
                f.runtime.lostCommit=Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);
                assertEquals(Status.UNKNOWN,f.service.execute(reaction).status()); f.source.unavailable(true);
                int writes=f.runtime.writes;
                assertEquals(args[1].equals("COMMITTED")?Status.REPLAYED:Status.NOT_COMMITTED,f.service.inspect(reaction).status());
                assertEquals(writes,f.runtime.writes); assertEquals(rolls+2,f.rolls); assertTrue(BusinessWriteTransactions.isBlocked());
            } else {
                f.commit(reaction); Request continuation=f.continuation(); assertFalse(f.runtime.rows(args[1]).isEmpty()); f.runtime.rows(args[1]).clear(); f.runtime.saveCommitted();
                int writes=f.runtime.writes; rolls=f.rolls;
                assertEquals(Status.REJECTED,f.service.execute(continuation).status());
                assertEquals(writes,f.runtime.writes); assertEquals(rolls,f.rolls);
                assertThrows(java.sql.SQLException.class,()->f.service.read(f.combat));
            }
        }
    }

    static final class Fixture {
        final UUID a=UUID.fromString("10000000-0000-4000-8000-000000000001"),b=UUID.fromString("20000000-0000-4000-8000-000000000002");
        final UUID campaign=UUID.fromString("30000000-0000-4000-8000-000000000003"),combat=UUID.fromString("40000000-0000-4000-8000-000000000004");
        final ProductionSchema2Projection material=Schema2CombatTestFixture.material();
        final Schema2RulesBinding binding=Schema2RulesBinding.from(material);
        final Schema2SourceReadFixture source=new Schema2SourceReadFixture(Schema2GrantFixtures.artifacts(Schema2CombatTestFixture.packages()));
        final Schema2RuntimeJdbcFixture runtime=new Schema2RuntimeJdbcFixture();
        final Schema2CharacterRuntimeService characterService=new Schema2CharacterRuntimeService(runtime.dataSource());
        final List<Member> members=new ArrayList<>(); final Map<UUID,UUID> items=new HashMap<>();
        final ArrayDeque<Integer> nextRolls=new ArrayDeque<>(); int rolls; Battle battle;
        final Schema2CombatRuntimeService service=new Schema2CombatRuntimeService(runtime.dataSource(),sides->{rolls++;return nextRolls.isEmpty()?(sides==20?10:1):nextRolls.removeFirst();});
        Fixture(boolean initialize) throws Exception {
            int ordinal=0;
            for(UUID character:List.of(a,b)) {
                ordinal++; Identity identity=new Identity(stable(ordinal,1),stable(ordinal,2)); UUID preparation=stable(ordinal,3);
                members.add(new Member(character,identity,preparation));
                var selection=new Schema2InitialProficiencyRules.Request(new Schema2InitialProficiencyRules.Sources(
                        new RuleIdentity(BASE,"character.race","race.human"),null,new RuleIdentity(BASE,"character.background","background.guard"),CLASS),Map.of());
                var prepared=new Schema2InitializationPreparationService(source.dataSource()).prepare(new Schema2InitializationPreparationService.Request(
                        preparation,identity,campaign,character,source.selections(),null,selection));
                var transfer=new Schema2SnapshotTransferService(source.dataSource(),runtime.dataSource()).transferPrepared(prepared);
                assertEquals(Schema2SnapshotTransferService.Status.COMMITTED,transfer.status(),transfer.code());
                if(initialize) {
                    var initial=new JdbcSchema2CharacterRuntimeRepository.Command("INITIALIZED",null,0,null,null,null,INITIAL_CHOICES);
                    var created=characterService.execute(new Schema2CharacterRuntimeService.Request(UUID.randomUUID(),identity,campaign,character,preparation,binding,-1,initial,fullAbilities()));
                    assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,created.status(),created.code());
                    UUID item=stable(ordinal,4); items.put(character,item);
                    var acquired=new Schema2CharacterRuntimeService.Request(item,identity,campaign,character,preparation,binding,character(character).version(),equipment("ITEM_ACQUIRED",item,ITEM_WEAPON,"CARRIED"),Map.of());
                    assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,characterService.execute(acquired).status());
                    assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,characterService.execute(characterRequest(character,equipment("ITEM_EQUIPPED",item,null,"MAIN_HAND"))).status());
                } else items.put(character,stable(ordinal,4));
            }
        }
        static UUID stable(int ordinal,int kind) { return UUID.fromString(String.format("50000000-0000-4000-8000-%012d",ordinal*10+kind)); }
        JdbcSchema2CharacterRuntimeRepository.State character(UUID id)throws Exception{return characterService.read(id).current();}
        Schema2CharacterRuntimeService.Request characterRequest(UUID id,JdbcSchema2CharacterRuntimeRepository.Command command)throws Exception {
            Member m=members.stream().filter(x->x.characterId().equals(id)).findFirst().orElseThrow();
            return new Schema2CharacterRuntimeService.Request(UUID.randomUUID(),m.identity(),campaign,id,m.preparationOperationId(),binding,character(id).version(),command,Map.of());
        }
        Request request(Command command)throws Exception {
            Map<UUID,Long> versions=new LinkedHashMap<>(); for(Member m:members)versions.put(m.characterId(),character(m.characterId()).version());
            return new Request(UUID.randomUUID(),combat,campaign,binding,members,battle==null?-1:battle.version(),versions,command);
        }
        Result commit(Command command)throws Exception{return commit(request(command));}
        Result commit(Request request) {var result=service.execute(request);assertEquals(Status.COMMITTED,result.status(),result.code());battle=result.event().after();return result;}
        void start()throws Exception {commit(Command.start(List.of(new Deployment(a,new Position(0,0),0),new Deployment(b,new Position(1,0),1)),List.of(a,b)));}
        Combatant actor(UUID id){return battle.combatants().stream().filter(x->x.characterId().equals(id)).findFirst().orElseThrow();}
        Request reaction(Kind kind)throws Exception {
            Flow flow=battle.activeFlow();Window window=flow.windows().getLast();Opportunity opportunity=window.opportunities().stream().filter(o->o.status()==OpportunityStatus.PENDING).findFirst().orElseThrow();
            return request(Command.reaction(kind,opportunity.actorId(),kind==Kind.DECLARE_OPPORTUNITY_ATTACK?items.get(opportunity.actorId()):null,
                    flow.flowKey(),flow.leafFrameKey(),window.windowKey(),opportunity.opportunityKey()));
        }
        Request continuation()throws Exception {Flow flow=battle.activeFlow();return request(Command.continuation(flow.movement().actorId(),flow.flowKey(),flow.leafFrameKey(),flow.windows().getLast().windowKey()));}
    }
}
