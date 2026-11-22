package com.dndtool.service;

import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.service.Schema2CombatHistory.Member;
import com.dndtool.service.Schema2CombatState.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Validated combat operation and its canonical request digest. */
public record Schema2CombatRequest(UUID operationId, UUID combatId, UUID campaignId, Schema2RulesBinding rules,
        List<Member> members, long expectedVersion, Map<UUID,Long> expectedCharacterVersions, Command command,
        boolean spaceOwned) {
    public Schema2CombatRequest(UUID operationId, UUID combatId, UUID campaignId, Schema2RulesBinding rules,
            List<Member> members, long expectedVersion, Map<UUID,Long> expectedCharacterVersions, Command command) {
        this(operationId, combatId, campaignId, rules, members, expectedVersion, expectedCharacterVersions, command, false);
    }
    public Schema2CombatRequest {
        for (UUID id : Arrays.asList(operationId,combatId,campaignId)) uuid(id);
        if (operationId.equals(campaignId) || combatId.equals(campaignId) || rules==null || members==null || command==null
                || operationId.equals(combatId) && (!spaceOwned || command.kind()!=Kind.OPEN_SCENE)
                || !spaceOwned && (members.size()<2 || Schema2CombatRules.placementCommand(command))
                || members.size()>Schema2CombatHistory.MAX_CHARACTER_MEMBERS || expectedCharacterVersions==null)
            throw rejected("S2_COMBAT_REQUEST");
        members = members.stream().sorted(Comparator.comparing(Member::characterId, Schema2CombatHistory.UUID_ORDER)).toList();
        Set<UUID> ids = new HashSet<>();
        for (Member member:members) {
            uuid(member.characterId()); uuid(member.preparationOperationId());
            uuid(member.identity().runId()); uuid(member.identity().snapshotId());
            if (!ids.add(member.characterId())) throw rejected("S2_COMBAT_REQUEST");
        }
        if (!ids.equals(expectedCharacterVersions.keySet()) || expectedCharacterVersions.values().stream().anyMatch(v->v==null||v<0||v>=Schema2CharacterRuntime.MAX_EVENTS)
                || (command.kind()==Kind.START || command.kind()==Kind.OPEN_SCENE ? expectedVersion!=-1 : expectedVersion<0)) throw rejected("S2_COMBAT_REQUEST");
        expectedCharacterVersions = Map.copyOf(expectedCharacterVersions);
    }
    public String digest() { return fingerprint(this); }

    private static IllegalArgumentException rejected(String code) { return new IllegalArgumentException(code); }

    private static String fingerprint(Schema2CombatRequest request) {
        try {
            var bytes=new ByteArrayOutputStream(); var out=new DataOutputStream(bytes);
            text(out,"local-dnd/schema2-combat-command/3"); id(out,request.operationId()); id(out,request.combatId()); id(out,request.campaignId());
            var binding=request.rules(); text(out,binding.canonicalProfile()); text(out,binding.compositionAlgorithm());
            out.writeInt(binding.members().size());
            for (var m:binding.members()) { text(out,m.moduleKey()); text(out,m.releaseVersion()); text(out,m.role().name()); text(out,m.rawZipSha256()); text(out,m.logicalSha256()); }
            text(out,binding.compositionSha256()); text(out,binding.finalRulesSha256()); text(out,binding.chainSha256());
            out.writeLong(request.expectedVersion()); out.writeInt(request.members().size());
            for (Member member:request.members()) { id(out,member.characterId()); id(out,member.identity().runId()); id(out,member.identity().snapshotId()); id(out,member.preparationOperationId()); out.writeLong(request.expectedCharacterVersions().get(member.characterId())); }
            Command command=request.command(); text(out,command.kind().name()); id(out,command.actorId()); id(out,command.targetId()); id(out,command.itemId());
            out.writeInt(command.path().size()); for (Position p:command.path()) { out.writeInt(p.x()); out.writeInt(p.y()); }
            out.writeInt(command.deployments().size()); for (Deployment d:command.deployments()) { id(out,d.characterId()); out.writeInt(d.position().x()); out.writeInt(d.position().y()); out.writeInt(d.side()); }
            out.writeInt(command.tieBreakOrder().size()); for (UUID id:command.tieBreakOrder()) id(out,id);
            id(out,command.flowKey()); id(out,command.leafKey()); id(out,command.windowKey()); id(out,command.opportunityKey());
            cast(out,command.castInput());
            ReadyInput ready=command.readyInput();out.writeBoolean(ready!=null);
            if(ready!=null) {
                text(out,ready.trigger().kind().name());id(out,ready.trigger().subjectId());position(out,ready.trigger().position());
                text(out,ready.responseKind().name());id(out,ready.targetId());id(out,ready.itemId());
                out.writeInt(ready.path().size());for(Position point:ready.path())position(out,point);cast(out,ready.spell());
            }
            id(out,command.readyKey());ZoneInput zone=command.zoneInput();out.writeBoolean(zone!=null);
            if(zone!=null){ruleIdentity(out,zone.rule());position(out,zone.center());}id(out,command.zoneKey());
            var health=command.healthInput();out.writeBoolean(health!=null);
            if(health!=null){ruleIdentity(out,health.rule());ruleIdentity(out,health.effect());text(out,Objects.toString(health.temporaryChoice(),""));text(out,health.adjudication());}
            out.writeInt(command.seconds());
            out.writeInt(command.monsterDeployments().size());for(MonsterDeployment d:command.monsterDeployments()) {id(out,d.monsterId());ruleIdentity(out,d.monster());position(out,d.position());out.writeInt(d.side());}
            BreathInput breath=command.breathInput();out.writeBoolean(breath!=null);if(breath!=null) {entryIdentity(out,breath.action());out.writeInt(breath.direction());}
            MonsterRestInput rest=command.monsterRestInput();out.writeBoolean(rest!=null);if(rest!=null) {id(out,rest.restKey());text(out,rest.kind().name());}
            out.flush(); return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException|NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void cast(DataOutputStream out,CastInput cast)throws IOException {
        out.writeBoolean(cast!=null);if(cast==null)return;
        ruleIdentity(out,cast.castingRule());ruleIdentity(out,cast.spell());out.writeInt(cast.targets().size());
        for(UUID target:cast.targets())id(out,target);id(out,cast.materialItemId());out.writeInt(cast.slotLevel());id(out,cast.targetCastFrameKey());
        // Retain every pre-area request byte. The new branch has no legacy empty-target form.
        if(cast.coneDirection()!=null){text(out,"S2_SPELL_CONE_DIRECTION_V1");out.writeInt(cast.coneDirection());}
    }
    private static void entryIdentity(DataOutputStream out,com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity identity)throws IOException {
        ruleIdentity(out,identity.rule());text(out,identity.collectionKey());text(out,identity.moduleKey());text(out,identity.entryKey());
    }
    private static void ruleIdentity(DataOutputStream out,com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity identity)throws IOException {
        out.writeBoolean(identity!=null);if(identity!=null){text(out,identity.moduleKey());text(out,identity.ruleType());text(out,identity.ruleKey());}
    }
    private static void position(DataOutputStream out,Position point)throws IOException {out.writeBoolean(point!=null);if(point!=null){out.writeInt(point.x());out.writeInt(point.y());}}
    private static void text(DataOutputStream out,String value) throws IOException { byte[] bytes=value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes); }
    private static void id(DataOutputStream out,UUID id) throws IOException { out.writeBoolean(id!=null); if(id!=null) { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); } }
    private static void uuid(UUID id) { if(id==null||id.version()!=4||id.variant()!=2) throw rejected("S2_COMBAT_IDENTITY"); }
}
