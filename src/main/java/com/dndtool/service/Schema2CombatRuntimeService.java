package com.dndtool.service;

import com.dndtool.module.composition.Schema2RulesBinding;
import com.dndtool.persistence.*;
import com.dndtool.persistence.JdbcSchema2CombatRuntimeRepository.*;
import com.dndtool.service.Schema2CombatState.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.function.IntUnaryOperator;
import javax.sql.DataSource;

/** INTERNAL combat owner. All combat and character consequences share one guarded transaction. */
public final class Schema2CombatRuntimeService {
    public enum Status { COMMITTED, REPLAYED, REJECTED, UNKNOWN, NOT_COMMITTED }
    public record Result(Status status, String code, Event event) { }
    public record Request(UUID operationId, UUID combatId, UUID campaignId, Schema2RulesBinding rules,
            List<Member> members, long expectedVersion, Map<UUID,Long> expectedCharacterVersions, Command command) {
        public Request {
            for (UUID id : Arrays.asList(operationId,combatId,campaignId)) uuid(id);
            if (Set.of(operationId,combatId,campaignId).size()!=3 || rules==null || members==null || command==null
                    || members.size()<2 || members.size()>JdbcSchema2CombatRuntimeRepository.MAX_CHARACTER_MEMBERS || expectedCharacterVersions==null)
                throw rejected("S2_COMBAT_REQUEST");
            members = members.stream().sorted(Comparator.comparing(Member::characterId, JdbcSchema2CombatRuntimeRepository.UUID_ORDER)).toList();
            Set<UUID> ids = new HashSet<>();
            for (Member member:members) {
                uuid(member.characterId()); uuid(member.preparationOperationId());
                uuid(member.identity().runId()); uuid(member.identity().snapshotId());
                if (!ids.add(member.characterId())) throw rejected("S2_COMBAT_REQUEST");
            }
            if (!ids.equals(expectedCharacterVersions.keySet()) || expectedCharacterVersions.values().stream().anyMatch(v->v==null||v<0||v>=JdbcSchema2CharacterRuntimeRepository.MAX_EVENTS)
                    || (command.kind()==Kind.START ? expectedVersion!=-1 : expectedVersion<0)) throw rejected("S2_COMBAT_REQUEST");
            expectedCharacterVersions = Map.copyOf(expectedCharacterVersions);
        }
        public String digest() { return fingerprint(this); }
    }
    public record Save(History combat, List<JdbcSchema2CharacterRuntimeRepository.History> characters) {
        public Save { characters=List.copyOf(characters); }
    }
    private record Pending(UUID combatId,String digest,Save save,boolean isolated) { }
    private record CharacterRead(Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> histories,
            Map<UUID,Schema2CharacterRuntimeService.Material> materials) { }
    private final DataSource runtime;
    private final IntUnaryOperator dice;
    private final JdbcSchema2CombatRuntimeRepository combats=new JdbcSchema2CombatRuntimeRepository();
    private final JdbcSchema2CharacterRuntimeRepository characters=new JdbcSchema2CharacterRuntimeRepository();
    private final Schema2CharacterRuntimeService characterService;
    private final Schema2CombatRules rules=new Schema2CombatRules();
    private volatile Pending pending;
    public Schema2CombatRuntimeService(DataSource runtime) { this(runtime,sides->new SecureRandom().nextInt(sides)+1); }
    Schema2CombatRuntimeService(DataSource runtime,IntUnaryOperator dice) {
        this.runtime=Objects.requireNonNull(runtime); this.dice=Objects.requireNonNull(dice);
        characterService=new Schema2CharacterRuntimeService(runtime);
    }

    public Result execute(Request request) {
        Objects.requireNonNull(request);
        return transact(false,request.combatId(),request.digest(),null,connection->{
            History previous=combats.read(connection,request.combatId());
            if (previous!=null) validate(connection,previous);
            Event replay=combats.findOperation(connection,request.operationId());
            if (replay!=null) {
                if (previous==null || !previous.events().contains(replay) || !replay.requestDigest().equals(request.digest()))
                    throw rejected("S2_COMBAT_OPERATION_CONFLICT");
                return new Result(Status.REPLAYED,"S2_COMBAT_REPLAY",replay);
            }
            if (previous==null) {
                if (request.expectedVersion()!=-1) throw rejected("S2_COMBAT_NOT_FOUND");
                requireNoActiveCombat(connection,request.campaignId());
                if (combats.findByCampaign(connection,request.campaignId()).size()>=JdbcSchema2CombatRuntimeRepository.MAX_EVENTS)
                    throw rejected("S2_COMBAT_CAPACITY");
            } else if (!previous.members().equals(request.members()) || !previous.current().campaignId().equals(request.campaignId()))
                throw rejected("S2_COMBAT_BINDING");
            else if (previous.current().version()!=request.expectedVersion()) throw rejected("S2_COMBAT_STALE_VERSION");
            var read=readCharacters(connection,request.members());
            Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> histories=read.histories();
            Map<UUID,Schema2CombatRules.Context> contexts=contexts(request.members(),histories,request.expectedCharacterVersions(),read.materials());
            for (var context:contexts.values()) request.rules().verify(context.material());
            for (var entry:histories.entrySet()) if (entry.getValue().current().version()!=request.expectedCharacterVersions().get(entry.getKey()))
                throw rejected("S2_COMBAT_STALE_CHARACTER");
            Battle before=previous==null?null:previous.current();
            var prepared=rules.preflight(request.operationId(),request.combatId(),request.campaignId(),contexts,before,request.command());
            reserve(previous,prepared,histories,contexts,request);
            Resolution resolution=rules.resolve(prepared,dice);
            List<UUID> children=new ArrayList<>();
            for (var change:resolution.healthChanges()) {
                var context=contexts.get(change.characterId());
                UUID child=Schema2CombatCharacterFacts.childOperation(request.operationId(),change.characterId(),children.size());
                var command=Schema2CombatCharacterFacts.command(context.material(),request.combatId(),request.operationId(),
                        request.command().actorId()==null?change.characterId():request.command().actorId(),change);
                var state=change.before();
                var childRequest=new Schema2CharacterRuntimeService.Request(child,state.identity(),state.campaignId(),state.characterId(),
                        state.preparationOperationId(),Schema2RulesBinding.from(context.material()),state.version(),command,Map.of());
                if (!Schema2CombatCharacterFacts.verify(context.material(),state,command,change.healthResult(),change.after()).equals(change.after()))
                    throw new SQLException("S2_COMBAT_HEALTH_RESULT");
                characters.append(connection,new JdbcSchema2CharacterRuntimeRepository.Event(child,childRequest.digest(),command,null,
                        change.before(),change.after(),null,change.healthResult()));
                children.add(child);
            }
            List<Dependency> dependencies=request.members().stream().map(m->new Dependency(m.characterId(),request.expectedCharacterVersions().get(m.characterId()))).toList();
            Event event=new Event(request.operationId(),request.digest(),request.command(),dependencies,children,before,resolution);
            combats.append(connection,request.members(),event);
            History committed=combats.read(connection,request.combatId());
            validate(connection,committed);
            return new Result(Status.COMMITTED,"S2_COMBAT_COMMITTED",event);
        });
    }

    /** A GET never passes an opportunity, rerolls a die or advances a movement cursor. */
    public Save read(UUID combatId) throws SQLException {
        try (var session=BusinessWriteTransactions.openResolution(runtime)) {
            History history=combats.read(session.connection(),combatId);
            if (history==null) { session.rollback(); return null; }
            var all=validate(session.connection(),history).histories();
            Save saved=new Save(history,history.members().stream().map(m->all.get(m.characterId())).toList());
            session.rollback(); return saved;
        }
    }

    public Result inspect(Request request) {
        return transact(true,request.combatId(),request.digest(),null,connection->{
            History history=combats.read(connection,request.combatId());
            if (history!=null) validate(connection,history);
            Event event=combats.findOperation(connection,request.operationId());
            if (event!=null) {
                if (history==null||!history.events().contains(event)||!event.requestDigest().equals(request.digest())) throw rejected("S2_COMBAT_OPERATION_CONFLICT");
                return new Result(Status.REPLAYED,"S2_COMBAT_REPLAY",event);
            }
            Pending known=pending;
            boolean proven=known!=null&&known.isolated()&&known.save()==null&&known.combatId().equals(request.combatId())&&known.digest().equals(request.digest());
            return new Result(proven?Status.NOT_COMMITTED:Status.UNKNOWN,proven?"S2_COMBAT_NOT_COMMITTED":"S2_COMBAT_OUTCOME_UNPROVEN",null);
        });
    }

    /** Direct recovery against retained preparations. Restores facts, never re-executes commands. */
    public Result restore(Save save) {
        try { validateSaveShape(save); } catch (SQLException|RuntimeException invalid) { return new Result(Status.REJECTED,"S2_COMBAT_RESTORE_INVALID",null); }
        UUID id=save.combat().current().combatId(); String digest=save.combat().events().getLast().requestDigest();
        return transact(false,id,digest,save,connection->{
            validateSaved(connection,save);
            History existing=combats.read(connection,id);
            if (existing!=null) {
                validate(connection,existing);
                if (!existing.equals(save.combat()) || !sameCharacters(connection,save)) throw rejected("S2_COMBAT_RESTORE_CONFLICT");
                return new Result(Status.REPLAYED,"S2_COMBAT_RESTORE_REPLAY",existing.events().getLast());
            }
            requireNoActiveCombat(connection,save.combat().current().campaignId());
            for (var history:save.characters()) {
                var found=characters.read(connection,history.current().characterId());
                if (found!=null) { if (!found.equals(history)) throw rejected("S2_COMBAT_RESTORE_CONFLICT"); }
                else characters.restore(connection,history);
            }
            combats.restore(connection,save.combat());
            validate(connection,combats.read(connection,id));
            return new Result(Status.COMMITTED,"S2_COMBAT_RESTORED",save.combat().events().getLast());
        });
    }

    public Result inspectRestore(Save save) {
        try { validateSaveShape(save); } catch (SQLException|RuntimeException invalid) { return new Result(Status.REJECTED,"S2_COMBAT_RESTORE_INVALID",null); }
        UUID id=save.combat().current().combatId(); String digest=save.combat().events().getLast().requestDigest();
        return transact(true,id,digest,save,connection->{
            validateSaved(connection,save);
            History found=combats.read(connection,id);
            if (found!=null) {
                validate(connection,found);
                if (!found.equals(save.combat())||!sameCharacters(connection,save)) throw rejected("S2_COMBAT_RESTORE_CONFLICT");
                return new Result(Status.REPLAYED,"S2_COMBAT_RESTORE_REPLAY",found.events().getLast());
            }
            Pending known=pending; boolean proven=known!=null&&known.isolated()&&save.equals(known.save());
            return new Result(proven?Status.NOT_COMMITTED:Status.UNKNOWN,proven?"S2_COMBAT_NOT_COMMITTED":"S2_COMBAT_OUTCOME_UNPROVEN",null);
        });
    }

    static void requireNoActiveCombat(Connection connection,UUID campaign) throws SQLException {
        if (new JdbcSchema2CombatRuntimeRepository().findByCampaign(connection,campaign).stream().anyMatch(h->h.current().status()!=Schema2CombatState.Status.ENDED))
            throw rejected("S2_CHARACTER_COMBAT_ACTIVE");
    }
    static void validateCharacterReferences(Connection connection,JdbcSchema2CharacterRuntimeRepository.History history,DataSource runtime) throws SQLException {
        validateCharacterReferences(connection,history);
        var service=new Schema2CombatRuntimeService(runtime); Set<UUID> owners=new HashSet<>();
        for(var child:history.events()) if(child.command().combatInput()!=null) owners.add(child.command().combatInput().combatId());
        for(UUID owner:owners) service.validate(connection,service.combats.read(connection,owner));
    }
    static void validateCharacterReferences(Connection connection,JdbcSchema2CharacterRuntimeRepository.History history) throws SQLException {
        var combats=new JdbcSchema2CombatRuntimeRepository();
        for (var child:history.events()) if (child.command().combatInput()!=null) {
            var input=child.command().combatInput(); Event owner=combats.findOperation(connection,input.combatOperationId());
            if (owner==null || !owner.after().combatId().equals(input.combatId()) || !owner.characterOperations().contains(child.operationId()))
                throw new SQLException("S2_COMBAT_CHARACTER_REFERENCE");
            if(input.secondsAdvanced()>60&&owner.command().kind()!=Kind.COMPLETE_MONSTER_REST)throw new SQLException("S2_COMBAT_CHARACTER_REFERENCE");
            int index=owner.characterOperations().indexOf(child.operationId());
            var change=owner.resolution().healthChanges().get(index);
            if (!change.characterId().equals(history.current().characterId()) || !change.before().equals(child.before())
                    || !change.after().equals(child.after()) || !change.frameKey().equals(input.frameKey())
                    || !Objects.equals(change.healthResult(),child.healthResult())
                    || !Objects.equals(input.actorId(),owner.command().actorId()==null?change.characterId():owner.command().actorId())
                    || input.damageAmount()!=change.damageAmount() || input.critical()!=change.critical() || input.secondsAdvanced()!=change.seconds()) throw new SQLException("S2_COMBAT_CHARACTER_REFERENCE");
        }
    }

    private CharacterRead readCharacters(Connection c,List<Member> members) throws SQLException {
        Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> found=new LinkedHashMap<>();
        Map<UUID,Schema2CharacterRuntimeService.Material> materials=new LinkedHashMap<>();
        for (Member member:members) {
            var history=characters.read(c,member.characterId());
            if (history==null) throw rejected("S2_COMBAT_CHARACTER_NOT_FOUND");
            var state=history.current();
            if (!member.identity().equals(state.identity()) || !member.preparationOperationId().equals(state.preparationOperationId())) throw rejected("S2_COMBAT_BINDING");
            var material=characterService.material(c,member.identity(),member.preparationOperationId());
            characterService.validateHistory(material,history); materials.put(member.characterId(),material);
            validateCharacterReferences(c,history); found.put(member.characterId(),history);
        }
        return new CharacterRead(found,materials);
    }
    private Map<UUID,Schema2CharacterRuntimeService.Material> readMaterials(Connection c,List<Member> members) throws SQLException {
        Map<UUID,Schema2CharacterRuntimeService.Material> materials=new LinkedHashMap<>();
        for(Member member:members) materials.put(member.characterId(),characterService.material(c,member.identity(),member.preparationOperationId()));
        return materials;
    }
    private Map<UUID,Schema2CombatRules.Context> contexts(List<Member> members,
            Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> histories,Map<UUID,Long> versions,
            Map<UUID,Schema2CharacterRuntimeService.Material> materials) {
        Map<UUID,Schema2CombatRules.Context> result=new LinkedHashMap<>();
        for (Member member:members) {
            var history=histories.get(member.characterId()); Long version=versions.get(member.characterId());
            if (history==null||version==null||version<0||version>=history.events().size()) throw rejected("S2_COMBAT_STALE_CHARACTER");
            var state=history.events().get(Math.toIntExact(version)).after();
            var material=materials.get(member.characterId());
            result.put(member.characterId(),new Schema2CombatRules.Context(material.projection(),material.initial(),state));
        }
        return result;
    }
    private CharacterRead validate(Connection c,History history) throws SQLException {
        JdbcSchema2CombatRuntimeRepository.validate(history);
        var all=readCharacters(c,history.members()); validateEvents(history,all.histories(),all.materials());
        return all;
    }
    private void validateEvents(History history,Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> all,
            Map<UUID,Schema2CharacterRuntimeService.Material> materials) throws SQLException {
        try {
            Map<UUID,Long> preceding=null;
            for (Event event:history.events()) {
                Map<UUID,Long> versions=new LinkedHashMap<>(); event.dependencies().forEach(d->versions.put(d.characterId(),d.version()));
                if (preceding!=null&&!preceding.equals(versions)) throw rejected("S2_COMBAT_DEPENDENCY_HISTORY");
                // Materials were physically verified for this validation call. Only character state varies by event.
                var roster=contexts(history.members(),all,versions,materials);
                var binding=Schema2RulesBinding.from(roster.values().iterator().next().material());
                Request request=new Request(event.operationId(),history.current().combatId(),history.current().campaignId(),binding,
                        history.members(),event.before()==null?-1:event.before().version(),versions,event.command());
                if (!request.digest().equals(event.requestDigest())) throw rejected("S2_COMBAT_REQUEST_HISTORY");
                rules.verify(event.operationId(),request.combatId(),request.campaignId(),roster,event.before(),event.command(),event.resolution());
                for (int i=0;i<event.resolution().healthChanges().size();i++) {
                    var change=event.resolution().healthChanges().get(i);
                    UUID child=Schema2CombatCharacterFacts.childOperation(event.operationId(),change.characterId(),i);
                    if (!event.characterOperations().get(i).equals(child)) throw rejected("S2_COMBAT_CHARACTER_REFERENCE");
                    var actual=all.get(change.characterId()).events().get(Math.toIntExact(change.after().version()));
                    var childCommand=Schema2CombatCharacterFacts.command(roster.get(change.characterId()).material(),request.combatId(),event.operationId(),
                            event.command().actorId()==null?change.characterId():event.command().actorId(),change);
                    if (!actual.operationId().equals(child)||!actual.command().equals(childCommand)||!actual.before().equals(change.before())
                            ||!actual.after().equals(change.after())||!Objects.equals(actual.healthResult(),change.healthResult())) throw rejected("S2_COMBAT_CHARACTER_REFERENCE");
                    versions.put(change.characterId(),change.after().version());
                }
                preceding=versions;
            }
            if (history.current().status()!=Schema2CombatState.Status.ENDED) for (var entry:all.entrySet())
                if (entry.getValue().current().version()!=preceding.get(entry.getKey())) throw rejected("S2_COMBAT_DEPENDENCY_HISTORY");
        } catch (IllegalArgumentException|IndexOutOfBoundsException invalid) { throw new SQLException("S2_COMBAT_HISTORY_CORRUPT",invalid); }
    }
    private static void validateSaveShape(Save save) throws SQLException {
        if (save==null||save.combat()==null||save.characters().size()!=save.combat().members().size()) throw new SQLException("S2_COMBAT_RESTORE_INVALID");
        JdbcSchema2CombatRuntimeRepository.validate(save.combat());
        if (!save.characters().stream().map(h->h.current().characterId()).toList().equals(save.combat().members().stream().map(Member::characterId).toList()))
            throw new SQLException("S2_COMBAT_RESTORE_INVALID");
        for (var history:save.characters()) JdbcSchema2CharacterRuntimeRepository.validate(history);
    }
    private void validateSaved(Connection c,Save save) throws SQLException {
        Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> all=new LinkedHashMap<>();
        for (var history:save.characters()) { characterService.validateRestoration(c,history); all.put(history.current().characterId(),history); }
        try { validateEvents(save.combat(),all,readMaterials(c,save.combat().members())); } catch (SQLException|RuntimeException invalid) { throw rejected("S2_COMBAT_RESTORE_INVALID"); }
    }
    private boolean sameCharacters(Connection c,Save save) throws SQLException {
        for (var history:save.characters()) if (!history.equals(characters.read(c,history.current().characterId()))) return false;
        return true;
    }

    private void reserve(History previous,Schema2CombatRules.Prepared prepared,
            Map<UUID,JdbcSchema2CharacterRuntimeRepository.History> histories,Map<UUID,Schema2CombatRules.Context> contexts,Request request) {
        try {
            Battle before=prepared.before(); Command command=request.command();
            int suffix=prepared.requiredSuffixEvents(); boolean closing=command.kind()==Kind.CLOSE_SCENE;
            int closeEvents=closing?0:1; // explicit terminal scene close
            if(!closing && (before==null || before.status()==Schema2CombatState.Status.ACTIVE))closeEvents++;
            if(!closing && before!=null) {
                closeEvents+=(int)before.monsterRests().stream().filter(r->r.status()==MonsterRestStatus.ACTIVE).count();
                closeEvents+=(int)before.concentrationHolders().stream().map(ConcentrationHolder::actorId).distinct().count();
                closeEvents+=(int)before.environmentZones().stream().filter(z->z.endedOperationId()==null).count();
                if(before.shields().stream().anyMatch(e->e.status()==EffectStatus.ACTIVE)
                        || before.readyPreparations().stream().anyMatch(r->r.status()==ReadyStatus.ARMED||r.status()==ReadyStatus.PREPARING))closeEvents+=histories.size();
            }
            if(Set.of(Kind.CAST,Kind.READY_SPELL,Kind.CREATE_ENVIRONMENT_ZONE,Kind.BEGIN_MONSTER_REST).contains(command.kind()))closeEvents++;
            if(Set.of(Kind.READY_ATTACK,Kind.READY_MOVE,Kind.READY_SPELL,Kind.DECLARE_SHIELD).contains(command.kind()))closeEvents+=histories.size();
            int remaining=command.kind()==Kind.MOVE?command.path().size():before==null||before.activeFlow()==null?0:
                    before.activeFlow().movements().stream().filter(m->m.status()==MovementStatus.PENDING).mapToInt(m->m.path().size()-m.cursor()).sum();
            JdbcSchema2CombatRuntimeRepository.checkReservedCapacity(previous,before,suffix+closeEvents,before==null?command.deployments().size()+command.monsterDeployments().size():before.combatants().size(),remaining);
            // The mandatory path may pass all optional reactions. Already accepted attacks/casts
            // still need their damage/healing and root exploration-time children before returning.
            Map<UUID,Integer> required=new HashMap<>();for(UUID id:histories.keySet())required.put(id,0);
            if(before!=null && before.activeFlow()!=null) {
                for(var attack:before.activeFlow().attacks())if(attack.status()==AttackStatus.HIT_PENDING_DAMAGE)required.merge(attack.targetId(),1,Integer::sum);
                for(var cast:before.activeFlow().casts())if(cast.status()==CastStatus.PENDING)for(UUID target:cast.targets())required.merge(target,1,Integer::sum);
                if(before.status()==Schema2CombatState.Status.EXPLORATION)for(UUID id:histories.keySet())required.merge(id,1,Integer::sum);
            }
            for(UUID id:prepared.possibleHealthTargets())required.merge(id,2,Integer::sum);
            for(var entry:histories.entrySet()) {
                int closure=closing?0:3+(before==null?0:(int)before.monsterRests().stream().filter(r->r.status()==MonsterRestStatus.ACTIVE).count())+(command.kind()==Kind.BEGIN_MONSTER_REST?1:0); // ending this round and any pending turn-bound facts
                JdbcSchema2CharacterRuntimeRepository.checkCombatCapacity(entry.getValue(),required.get(entry.getKey())+closure);
            }
        } catch (SQLException|IllegalArgumentException exhausted) { throw rejected("S2_COMBAT_CAPACITY"); }
    }

    private static String fingerprint(Request request) {
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
    private static IllegalArgumentException rejected(String code) { return new IllegalArgumentException(code); }
    @FunctionalInterface private interface Work { Result run(Connection c) throws SQLException; }
    private Result transact(boolean inspection, UUID combatId, String digest, Save restoration, Work work) {
        try (var admission = inspection ? BusinessWriteTransactions.enterResolution() : BusinessWriteTransactions.enter()) {
            BusinessWriteTransactions.Session session = null;
            Result result;
            try {
                session = inspection ? admission.openResolution(runtime) : admission.openInternal(runtime);
                result = work.run(session.connection());
                if (result.status() == Status.COMMITTED) session.commit(); else session.rollback();
            } catch (SQLException | RuntimeException failure) {
                if (session != null && session.outcome() == BusinessWriteTransactions.Outcome.ACTIVE) {
                    try { session.rollback(); } catch (SQLException | RuntimeException rollback) { suppress(failure, rollback); }
                    catch (Error fatal) {
                        suppress(fatal, failure); fatal(session, fatal);
                        if (session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN && !inspection)
                            pending = new Pending(combatId, digest, restoration, BusinessWriteTransactions.isResolutionAvailable());
                        throw fatal;
                    }
                }
                if (session != null && session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN) {
                    if (!inspection) pending = new Pending(combatId, digest, restoration, BusinessWriteTransactions.isResolutionAvailable());
                    result = new Result(Status.UNKNOWN, "S2_COMBAT_COMMIT_UNKNOWN", null);
                } else {
                    if (!(failure instanceof IllegalArgumentException) && session != null) BusinessWriteTransactions.invalidate();
                    String code = failure.getMessage();
                    if (code == null || !code.matches("(?:S2_|BW_|ACTION_|REACTION_)[A-Z_]+")) code = "S2_COMBAT_RUNTIME_FAILURE";
                    result = new Result(inspection && !(failure instanceof IllegalArgumentException) ? Status.UNKNOWN : Status.REJECTED,
                            code, null);
                }
            } catch (Error failure) {
                fatal(session, failure);
                if (session != null && session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN && !inspection)
                    pending = new Pending(combatId, digest, restoration, BusinessWriteTransactions.isResolutionAvailable());
                throw failure;
            }
            if (session != null) {
                try { session.close(); }
                catch (SQLException | RuntimeException cleanup) {
                    if (session.outcome() == BusinessWriteTransactions.Outcome.UNKNOWN) {
                        if (!inspection) pending = new Pending(combatId, digest, restoration, BusinessWriteTransactions.isResolutionAvailable());
                        return new Result(Status.UNKNOWN, "S2_COMBAT_COMMIT_UNKNOWN", null);
                    }
                    return new Result(result.status(), "S2_COMBAT_CONNECTION_FAILURE", result.event());
                }
            }
            return result;
        } catch (SQLException unavailable) {
            return new Result(inspection ? Status.UNKNOWN : Status.REJECTED, unavailable.getMessage(), null);
        }
    }

    private static void fatal(BusinessWriteTransactions.Session session, Error failure) {
        BusinessWriteTransactions.invalidate();
        if (session == null) return;
        if (session.outcome() == BusinessWriteTransactions.Outcome.ACTIVE) {
            try { session.rollback(); } catch (SQLException | RuntimeException | Error cleanup) { suppress(failure, cleanup); }
        }
        try { session.close(); } catch (SQLException | RuntimeException | Error cleanup) { suppress(failure, cleanup); }
    }
    private static void suppress(Throwable original, Throwable cleanup) { if (original != cleanup) original.addSuppressed(cleanup); }
}
