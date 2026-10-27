package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.HealthInput;
import com.dndtool.persistence.BusinessWriteTransactions;
import com.dndtool.persistence.Schema2RuntimeJdbcFixture;
import com.dndtool.testing.IsolatedJvmTest;
import com.dndtool.service.Schema2CombatState.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2SpellFixtures.*;
import static com.dndtool.service.Schema2CheckTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2ExplorationRuntimeServiceTest {
    static final RuleIdentity ZONE=new RuleIdentity(Schema2ProgressionFixtures.BASE,"adventure.environment","environment.fixed_antimagic");
    static List<Map<String,byte[]>> packages() {
        var packages=Schema2SpellFixtures.packages();var rows=Schema2GrantFixtures.json(packages.getFirst().get("rules.json")).getAsJsonArray();
        JsonObject zone=new JsonObject();zone.add("identity",Schema2ProgressionFixtures.jsonIdentity(ZONE));
        zone.addProperty("display_name","Fixed antimagic zone");zone.addProperty("description","DM environmental mechanism");zone.addProperty("source_page",0);zone.addProperty("algorithm","DM_ANTIMAGIC_ZONE_V1");rows.add(zone);
        packages.getFirst().put("rules.json",rows.toString().getBytes(StandardCharsets.UTF_8));return packages;
    }
    static Schema2CombatRuntimeServiceTest.Fixture fixture(boolean initialize)throws Exception {
        var f=new Schema2CombatRuntimeServiceTest.Fixture(initialize,packages(),initialChoices());
        if(initialize)for(UUID actor:List.of(f.a,f.b)) {
            UUID focus=Schema2SpellRuntimeServiceTest.focus(actor);var member=f.members.stream().filter(m->m.characterId().equals(actor)).findFirst().orElseThrow();
            var request=new Schema2CharacterRuntimeService.Request(focus,member.identity(),f.campaign,actor,member.preparationOperationId(),f.binding,f.character(actor).version(),equipment("ITEM_ACQUIRED",focus,HOLY_SYMBOL,"CARRIED"),Map.of());
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(request).status());
            assertEquals(Schema2CharacterRuntimeService.Status.COMMITTED,f.characterService.execute(f.characterRequest(actor,equipment("ITEM_EQUIPPED",focus,null,"OFF_HAND"))).status());
        }
        return f;
    }
    @Test void endingCombatRetainsEffectsAndIndependentZonesRestoreAndReleaseByTarget()throws Exception {
        var f=fixture(true);f.start();f.commit(Schema2SpellRuntimeServiceTest.bless(f,f.a,List.of(f.a,f.b)));
        f.commit(Command.action(Kind.END_COMBAT,f.a));assertEquals(Status.EXPLORATION,f.battle.status());assertEquals(6,Schema2SpellRules.second(f.character(f.a)));
        long expiry=f.battle.ongoingEffects().getFirst().expiresAtSecond();
        f.commit(Command.createZone(f.a,new ZoneInput(ZONE,new Position(0,0))));f.commit(Command.createZone(f.a,new ZoneInput(ZONE,new Position(0,0))));
        assertEquals(4,f.battle.suppressionCauses().stream().filter(c->c.releasedOperationId()==null).count());
        var saved=f.service.read(f.combat);var restored=fixture(false);restored.source.unavailable(true);
        var result=restored.service.restore(saved);assertEquals(Schema2CombatRuntimeService.Status.COMMITTED,result.status(),result.code());restored.battle=saved.combat().current();assertEquals(saved,restored.service.read(f.combat));
        UUID first=restored.battle.environmentZones().getFirst().zoneKey(),second=restored.battle.environmentZones().getLast().zoneKey();
        restored.commit(Command.endZone(restored.a,first));assertNull(Schema2OngoingEffectRules.blessing(restored.battle,restored.a));
        restored.commit(Command.exploreMove(restored.a,List.of(new Position(0,1),new Position(0,2),new Position(0,3))));
        assertNotNull(Schema2OngoingEffectRules.blessing(restored.battle,restored.a));assertNull(Schema2OngoingEffectRules.blessing(restored.battle,restored.b));
        assertEquals(expiry,restored.battle.ongoingEffects().getFirst().expiresAtSecond());assertEquals(12,Schema2SpellRules.second(restored.character(restored.a)));
        int writes=restored.runtime.writes,rolls=restored.rolls;
        var ordinary=restored.characterService.execute(restored.characterRequest(restored.a,effect(TEMPORARY,"REPLACE")));
        assertEquals(Schema2CharacterRuntimeService.Status.REJECTED,ordinary.status());assertEquals(writes,restored.runtime.writes);assertEquals(rolls,restored.rolls);
        restored.commit(Command.endZone(restored.a,second));restored.commit(Command.advanceTime(restored.a,48));assertEquals(EffectStatus.ENDED,restored.battle.ongoingEffects().getFirst().status());
        restored.commit(Command.action(Kind.CLOSE_SCENE,restored.a));assertEquals(Status.ENDED,restored.battle.status());
    }
    @Test void liveZoneRejectsSceneCloseBeforeDiceOrWritesAndCanStillBeEnded()throws Exception {
        var f=fixture(true);f.start();f.commit(Command.action(Kind.END_COMBAT,f.a));
        f.commit(Command.createZone(f.a,new ZoneInput(ZONE,new Position(0,0))));
        var before=f.service.read(f.combat);int writes=f.runtime.writes,rolls=f.rolls;
        var rejected=f.service.execute(f.request(Command.action(Kind.CLOSE_SCENE,f.a)));
        assertEquals(Schema2CombatRuntimeService.Status.REJECTED,rejected.status());
        assertEquals("S2_SCENE_OBLIGATIONS_PENDING",rejected.code());
        assertEquals(writes,f.runtime.writes);assertEquals(rolls,f.rolls);
        assertEquals(before,f.service.read(f.combat));assertFalse(BusinessWriteTransactions.isBlocked());
        f.commit(Command.endZone(f.a,f.battle.environmentZones().getFirst().zoneKey()));
        f.commit(Command.action(Kind.CLOSE_SCENE,f.a));assertEquals(Status.ENDED,f.battle.status());
    }
    @Test void environmentalHealthKeepsRealSourceDiceAndConcentrationInTheSameTransaction()throws Exception {
        var f=fixture(true);f.start();f.commit(Schema2SpellRuntimeServiceTest.bless(f,f.a,List.of(f.a)));
        f.commit(Command.action(Kind.END_COMBAT,f.a));
        f.commit(Command.worldHealth(f.a,f.a,new HealthInput(HEALTH,TEMPORARY,"REPLACE","DM places a published environmental effect")));
        assertEquals(5,f.character(f.a).health().temporaryHitPoints());f.nextRolls.addAll(List.of(1,1));
        var request=f.request(Command.worldHealth(f.a,f.a,new HealthInput(HEALTH,DAMAGE,null,"DM applies the declared environmental damage")));
        var accepted=f.commit(request);assertEquals(0,f.character(f.a).health().temporaryHitPoints());assertEquals(EffectStatus.ENDED,f.battle.ongoingEffects().getFirst().status());
        var child=f.characterService.read(f.a).events().getLast();assertEquals("COMBAT_ENVIRONMENT_HEALTH",child.kind());assertEquals(DAMAGE,child.command().healthInput().effect());
        assertEquals(6,child.healthResult().rolledAmount());assertEquals(accepted.event().operationId(),child.command().combatInput().combatOperationId());
        int writes=f.runtime.writes,rolls=f.rolls;assertEquals(Schema2CombatRuntimeService.Status.REPLAYED,f.service.execute(request).status());assertEquals(writes,f.runtime.writes);assertEquals(rolls,f.rolls);
        var saved=f.service.read(f.combat);var restored=fixture(false);assertEquals(Schema2CombatRuntimeService.Status.COMMITTED,restored.service.restore(saved).status());assertEquals(saved,restored.service.read(f.combat));assertEquals(0,restored.rolls);
    }
    @Test void zoneAndCauseWritesRollbackTogether()throws Exception {
        for(String table:List.of("s2_combat_environment","s2_combat_suppression"))
            IsolatedJvmTest.run(FaultProbe.class,"rollback",table);
    }
    @Test void unknownZoneCommitIsInspectedWithoutRecreatingCauses()throws Exception {
        for(String outcome:List.of("COMMITTED","ROLLED_BACK"))IsolatedJvmTest.run(FaultProbe.class,"unknown",outcome);
    }
    public static final class FaultProbe {
        public static void main(String[] args)throws Exception {
            var f=fixture(true);f.start();f.commit(Schema2SpellRuntimeServiceTest.bless(f,f.a,List.of(f.a,f.b)));
            f.commit(Command.action(Kind.END_COMBAT,f.a));
            var request=f.request(Command.createZone(f.a,new ZoneInput(ZONE,new Position(0,0))));
            var before=f.service.read(f.combat);int rolls=f.rolls;
            if(args[0].equals("rollback")) {
                int count=f.runtime.rows(args[1]).size();
                f.runtime.afterWrite=db->{if(db.rows(args[1]).size()>count)throw new IllegalStateException("injected scene persistence failure");};
                assertEquals(Schema2CombatRuntimeService.Status.REJECTED,f.service.execute(request).status());
                assertEquals(before,f.service.read(f.combat));
            } else {
                f.runtime.lostCommit=Schema2RuntimeJdbcFixture.LostCommit.valueOf(args[1]);
                assertEquals(Schema2CombatRuntimeService.Status.UNKNOWN,f.service.execute(request).status());
                f.source.unavailable(true);int writes=f.runtime.writes;
                assertEquals(args[1].equals("COMMITTED")?Schema2CombatRuntimeService.Status.REPLAYED:Schema2CombatRuntimeService.Status.NOT_COMMITTED,f.service.inspect(request).status());
                assertEquals(writes,f.runtime.writes);assertTrue(BusinessWriteTransactions.isBlocked());
            }
            assertEquals(rolls,f.rolls);
        }
    }
}
