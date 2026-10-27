package com.dndtool.service;

import com.dndtool.module.composition.*;
import com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.*;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import com.dndtool.service.Schema2CombatRules.Context;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;
import static com.dndtool.module.composition.Schema2ProgressionFixtures.*;
import static com.dndtool.module.composition.Schema2SpellFixtures.*;
import static com.dndtool.service.Schema2CombatState.*;
import static com.dndtool.service.Schema2OngoingEffectRules.*;
import static org.junit.jupiter.api.Assertions.*;

class Schema2OngoingEffectRulesTest {
    static final RuleIdentity ZONE=new RuleIdentity(BASE,"adventure.environment","environment.fixed_antimagic");
    final ProductionSchema2Projection material=zoneMaterial();
    final UUID campaign=UUID.randomUUID(),combat=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    final Map<UUID,Context> roster=Map.of(a,context(a),b,context(b));
    Battle battle=scene();

    static ProductionSchema2Projection zoneMaterial() {
        var files=Schema2SpellFixtures.packages();
        JsonArray rows=JsonParser.parseString(new String(files.getFirst().get("rules.json"),StandardCharsets.UTF_8)).getAsJsonArray();
        JsonObject zone=new JsonObject();JsonObject identity=jsonIdentity(ZONE);
        zone.add("identity",identity);zone.addProperty("display_name","Fixed antimagic environmental zone");
        zone.addProperty("description","A declared environmental mechanism, not an eighth-level spell.");
        zone.addProperty("source_page",0);zone.addProperty("algorithm",ZONE_ALGORITHM);rows.add(zone);
        files.getFirst().put("rules.json",rows.toString().getBytes(StandardCharsets.UTF_8));
        return ProductionSchema2Projection.compose(Schema2GrantFixtures.artifacts(files));
    }
    Context context(UUID id) {
        var initial=fullPlan(material,new Identity(UUID.randomUUID(),UUID.randomUUID()),campaign,id);
        return new Context(material,initial,new Schema2CharacterProgressionRules().initial(material,initial,UUID.randomUUID(),fullAbilities(),initialChoices()));
    }
    Battle scene() {
        var effects=List.of(new OngoingEffect(UUID.randomUUID(),BLESS,a,List.of(a,b),0,60,EffectStatus.ACTIVE,null),
                new OngoingEffect(UUID.randomUUID(),BLESS,b,List.of(a,b),0,60,EffectStatus.ACTIVE,null));
        return new Battle(combat,campaign,Schema2CombatTestFixture.COMBAT,Status.ACTIVE,1,1,0,0,
                List.of(actor(a,new Position(0,0)),actor(b,new Position(10,0))),
                List.of(new Initiative(a,20,0,20,0),new Initiative(b,10,0,10,1)),List.of(),effects,List.of(),List.of(),List.of(),
                List.of(new ConcentrationHolder(a,ConcentrationKind.BLESS_EFFECT,effects.getFirst().effectKey()),
                        new ConcentrationHolder(b,ConcentrationKind.BLESS_EFFECT,effects.getLast().effectKey())),List.of(),List.of());
    }
    static Combatant actor(UUID id,Position position) {return new Combatant(id,position,0,true,true,0,0,false,false,null,false);}
    void zones(EnvironmentZone...zones) {battle=copy(battle,battle.combatants(),List.of(zones));}
    void reconcile() {var result=Schema2OngoingEffectRules.reconcile(battle,UUID.randomUUID());battle=withCauses(battle,result.causes());validate(roster,battle);}
    static Battle copy(Battle b,List<Combatant> actors,List<EnvironmentZone> zones) {
        return new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),actors,b.initiative(),b.flows(),b.ongoingEffects(),b.readyPreparations(),b.triggerFacts(),b.shields(),b.concentrationHolders(),zones,b.suppressionCauses());
    }
    EnvironmentZone zone(Position center) {return createZone(roster,battle,UUID.randomUUID(),new ZoneInput(ZONE,center));}

    @Test void independentReasonsReleaseOneAtATimeWithoutConfusingShadowedSelection() {
        UUID original=battle.ongoingEffects().getFirst().effectKey();var one=zone(new Position(0,0));var two=zone(new Position(1,0));
        zones(one,two);reconcile();
        assertNull(blessing(battle,a));assertEquals(4,battle.suppressionCauses().size());
        assertEquals(2,contributions(battle,a).getFirst().suppressionCauseKeys().size());
        assertEquals(ContributionStatus.SUPPRESSED,contributions(battle,a).getLast().status());
        assertEquals(ContributionStatus.EFFECTIVE,contributions(battle,b).getFirst().status());
        assertEquals(ContributionStatus.SHADOWED,contributions(battle,b).getLast().status());
        zones(endZone(one,UUID.randomUUID()),two);reconcile();
        assertNull(blessing(battle,a));assertEquals(1,contributions(battle,a).getFirst().suppressionCauseKeys().size());
        zones(battle.environmentZones().getFirst(),endZone(two,UUID.randomUUID()));reconcile();
        assertEquals(original,blessing(battle,a).effectKey());assertEquals(60,blessing(battle,a).expiresAtSecond());
        assertEquals(ContributionStatus.SHADOWED,contributions(battle,a).getLast().status());assertEquals(2,battle.concentrationHolders().size());
        assertTrue(battle.suppressionCauses().stream().allMatch(c->c.releasedOperationId()!=null));
    }

    @Test void positionEpisodesAreRetainedAndReentryCreatesNewCauseEvenWithinOneRoot() {
        zones(zone(new Position(0,0)));UUID operation=UUID.randomUUID();
        battle=withCauses(battle,Schema2OngoingEffectRules.reconcile(battle,operation,0).causes());UUID first=battle.suppressionCauses().getFirst().causeKey();
        battle=copy(battle,List.of(actor(a,new Position(3,0)),actor(b,new Position(10,0))),battle.environmentZones());
        battle=withCauses(battle,Schema2OngoingEffectRules.reconcile(battle,operation,1).causes());assertNotNull(blessing(battle,a));
        battle=copy(battle,List.of(actor(a,new Position(0,0)),actor(b,new Position(10,0))),battle.environmentZones());
        battle=withCauses(battle,Schema2OngoingEffectRules.reconcile(battle,operation,2).causes());validate(roster,battle);
        assertEquals(4,battle.suppressionCauses().size());assertNotEquals(first,battle.suppressionCauses().get(2).causeKey());
        assertEquals(SuppressionReleaseReason.LEFT_ZONE,battle.suppressionCauses().getFirst().releaseReason());
    }

    @Test void casterLocationDoesNotRemoveRemoteTargetOrConcentrationButPreventsNewSpell() {
        zones(zone(new Position(0,0)));reconcile();
        assertNotNull(blessing(battle,b));assertEquals(2,battle.concentrationHolders().size());
        assertEquals("S2_SPELL_ANTIMAGIC_ZONE",assertThrows(IllegalArgumentException.class,()->requireCastingAllowed(roster,battle,a)).getMessage());
        assertDoesNotThrow(()->requireCastingAllowed(roster,battle,b));
    }

    @Test void deadlineDuringSuppressionEndsSourceAndClearsEveryIncomingReasonWithoutRevival() {
        zones(zone(new Position(0,0)),zone(new Position(1,0)));reconcile();List<CombatFact> facts=new ArrayList<>();
        battle=Schema2ExplorationRules.expire(battle,60,facts);reconcile();
        assertEquals(2,facts.size());assertTrue(battle.concentrationHolders().isEmpty());
        assertTrue(battle.suppressionCauses().stream().allMatch(c->c.releaseReason()==SuppressionReleaseReason.EFFECT_ENDED));
        zones(battle.environmentZones().stream().map(z->endZone(z,UUID.randomUUID())).toArray(EnvironmentZone[]::new));reconcile();
        assertNull(blessing(battle,a));assertEquals(ContributionStatus.ENDED,contributions(battle,a).getFirst().status());
    }

    @Test void shieldSuppressionHasItsOwnApplicationAndDoesNotAcquireConcentration() {
        UUID shieldKey=UUID.randomUUID();Battle b=battle;
        battle=new Battle(b.combatId(),b.campaignId(),b.combatRule(),b.status(),b.round(),b.turnNumber(),b.activeOrdinal(),b.version(),b.combatants(),b.initiative(),b.flows(),b.ongoingEffects(),b.readyPreparations(),b.triggerFacts(),List.of(new ShieldEffect(shieldKey,UUID.randomUUID(),a,1,3,EffectStatus.ACTIVE,null)),b.concentrationHolders(),b.environmentZones(),b.suppressionCauses());
        zones(zone(new Position(0,0)));reconcile();assertTrue(isSuppressed(battle,shieldKey,a));assertEquals(3,battle.suppressionCauses().size());assertEquals(2,battle.concentrationHolders().size());
        zones(endZone(battle.environmentZones().getFirst(),UUID.randomUUID()));reconcile();assertFalse(isSuppressed(battle,shieldKey,a));assertEquals(EffectStatus.ACTIVE,battle.shields().getFirst().status());
    }

    @Test void malformedMissingDuplicateAndDanglingCausesFailClosedWithoutReadRepair() {
        zones(zone(new Position(0,0)));Battle missing=battle;
        assertThrows(IllegalArgumentException.class,()->validate(roster,missing));reconcile();
        var causes=new ArrayList<>(battle.suppressionCauses());causes.add(causes.getFirst());Battle duplicate=withCauses(battle,causes);
        assertThrows(IllegalArgumentException.class,()->validate(roster,duplicate));
        var old=causes.getFirst();Battle dangling=withCauses(battle,List.of(new SuppressionCause(old.causeKey(),UUID.randomUUID(),old.effectKey(),old.targetId(),old.establishedOperationId(),null,null)));
        assertThrows(IllegalArgumentException.class,()->validate(roster,dangling));
        assertThrows(IllegalArgumentException.class,()->createZone(roster,battle,UUID.randomUUID(),new ZoneInput(new RuleIdentity(BASE,"adventure.environment","environment.unknown"),new Position(0,0))));
        assertEquals(2,battle.suppressionCauses().size());
    }
}
