package com.dndtool.service.storage;

import com.dndtool.service.CampaignDomainEngine;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.dndtool.service.storage.CampaignDomainData.*;

/** Whole-campaign validation joins root history, typed domain histories and current projections. */
public final class CampaignDomainValidation {
    private CampaignDomainValidation() { }
    public static List<CampaignDomainEngine.CharacterRecord> records(State state) {
        return state.characters().stream().map(c -> new CampaignDomainEngine.CharacterRecord(c.name(), c.kind(), c.initial(), c.history())).toList();
    }
    public static CampaignData.Snapshot validate(CampaignData.Snapshot snapshot) {
        State state = snapshot.domains();
        if (!snapshot.rules().contains(CampaignRules.RUNTIME) || !snapshot.characters().isEmpty()
                || state.characters().size() > 16 || state.combats().size() > 128 || state.roots().size() > 100000
                || state.nodes().size() > 10000 || state.items().size() > 10000)
            throw CampaignJson.bad("Unsupported campaign domain selection or capacity");
        var material = state.material().restore();
        var bundled = BundledCampaignRules.projection();
        if (!material.compositionSha256().equals(bundled.compositionSha256()) || !material.finalRulesSha256().equals(bundled.finalRulesSha256()) || !material.chainSha256().equals(bundled.chainSha256()))
            throw CampaignJson.bad("Exact current-version rule material differs");
        UUID campaign = UUID.fromString(snapshot.campaignId());
        Set<UUID> characterIds = new HashSet<>(), operations = new HashSet<>(), rootOperations = new HashSet<>(), combatOperations = new HashSet<>();
        Long clock = null;
        for (var character : state.characters()) {
            if (!characterIds.add(character.id()) || !campaign.equals(character.initial().campaignId()) || !character.id().equals(character.history().current().characterId())) throw CampaignJson.bad("Character ownership differs");
            var current = character.history().current();
            long second = Math.addExact(Math.multiplyExact(current.rest().minute(), 60), current.rest().second());
            if (clock != null && clock != second) throw CampaignJson.bad("Campaign clocks disagree");
            clock = second;
            for (var event : character.history().events()) if (!operations.add(event.operationId())) throw CampaignJson.bad("Duplicate character event identity");
        }
        for(var character:state.manual().characters())if(!characterIds.add(character.id()))throw CampaignJson.bad("Manual/rule character identity collision");
        Set<UUID> battleIds = new HashSet<>();
        for (var combat : state.combats()) {
            if (!battleIds.add(combat.current().combatId()) || !campaign.equals(combat.current().campaignId())) throw CampaignJson.bad("Scene ownership differs");
            for (var e : combat.events()) if (!combatOperations.add(e.operationId())) throw CampaignJson.bad("Duplicate scene event identity");
        }
        new CampaignDomainEngine().validateCampaign(material, records(state), state.combats());
        Map<UUID, Profile> profiles = new HashMap<>(); Map<UUID, Node> nodes = new HashMap<>();
        Map<UUID, Position> positions = new HashMap<>(); Map<UUID, Item> items = new HashMap<>();
        Set<UUID> referencedOperations = new HashSet<>(), referencedCombats = new HashSet<>();
        Map<UUID,Integer> characterCursors=new HashMap<>(),combatCursors=new HashMap<>();
        Map<UUID,com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.Event> eventsById=new HashMap<>();
        state.characters().forEach(c->{characterCursors.put(c.id(),0);c.history().events().forEach(e->eventsById.put(e.operationId(),e));});
        state.combats().forEach(c->combatCursors.put(c.current().combatId(),0));
        CampaignManualData.State manual=CampaignManualData.State.empty();
        CampaignParticipationData.State participation = CampaignParticipationData.State.empty();
        Map<UUID,com.dndtool.service.Schema2CombatState.Battle> historicalBattles = new HashMap<>();
        Map<UUID,com.dndtool.service.Schema2CombatState.Status> sceneStates=new HashMap<>();
        long sequence = 0;
        if (snapshot.history().size() != state.roots().size()) throw CampaignJson.bad("Root history count differs");
        for (Root root : state.roots()) {
            if (root.sequence() != ++sequence || !rootOperations.add(root.operationId())) throw CampaignJson.bad("Root history is not contiguous and unique");
            if (!summary(root).equals(snapshot.history().get(Math.toIntExact(sequence - 1)))) throw CampaignJson.bad("Domain root summary differs");
            CampaignParticipationCommands.validateHistoricalActor(state, root, participation, profiles, manual);
            var sceneBefore = List.copyOf(historicalBattles.values()); var manualBefore = manual;
            boolean richBusy=sceneStates.values().stream().anyMatch(status->status!=com.dndtool.service.Schema2CombatState.Status.ENDED);
            if(richBusy&&(root.kind().startsWith("manual-")||Set.of("character-create","character-profile","character-command","position","item","check-effects","adjudicated-check").contains(root.kind())))throw CampaignJson.bad("Root bypasses its active scene owner");
            Set<UUID> pending=new HashSet<>(root.characterOperations());
            if(pending.size()!=root.characterOperations().size())throw CampaignJson.bad("Repeated child inside one root");
            for(var character:state.characters()){
                int cursor=characterCursors.get(character.id());var events=character.history().events();
                while(cursor<events.size()&&pending.remove(events.get(cursor).operationId()))cursor++;
                characterCursors.put(character.id(),cursor);
            }
            if(!pending.isEmpty())throw CampaignJson.bad("Root consumes a future or noncontiguous character event");
            for (UUID operation : root.characterOperations()) if (!operations.contains(operation) || !referencedOperations.add(operation)) throw CampaignJson.bad("Character child lacks a unique root");
            if(root.combatId()!=null){
                var combat=state.combats().stream().filter(c->c.current().combatId().equals(root.combatId())).findFirst().orElseThrow(()->CampaignJson.bad("Missing root scene"));
                int cursor=combatCursors.get(root.combatId());
                if(cursor>=combat.events().size()||!combat.events().get(cursor).operationId().equals(root.operationId()))throw CampaignJson.bad("Root consumes a future or noncontiguous scene event");
                if(cursor==0&&richBusy)throw CampaignJson.bad("Two historical scene owners overlap");
                sceneStates.put(root.combatId(),combat.events().get(cursor).after().status());
                historicalBattles.put(root.combatId(),combat.events().get(cursor).after());
                combatCursors.put(root.combatId(),cursor+1);
                if(!referencedCombats.add(root.operationId()))throw CampaignJson.bad("Scene child lacks a unique root");
            }
            if(!Set.of("character-create","character-command","combat-command","check-effects","adjudicated-check").contains(root.kind())
                    &&(!root.characterOperations().isEmpty()||root.combatId()!=null))throw CampaignJson.bad("Metadata root cannot own typed execution events");
            switch (root.kind()) {
                case "member-add", "member-update", "control-set", "share-set", "exploration-set" -> {
                    participation = CampaignParticipationCommands.validateAudit(root, document(root.after(), CampaignParticipationCommands.Audit.class), participation, campaign, profiles, manual, List.copyOf(historicalBattles.values()), nodes.values(), state.roots().subList(0, Math.toIntExact(root.sequence() - 1)));
                }
                case "character-create", "character-profile" -> {
                    if(CampaignManualData.active(manual))throw CampaignJson.bad("Rule-character profile bypasses the simple scene owner");
                    Profile after = document(root.after(), Profile.class); Profile before = profiles.get(root.subjectId());
                    if (!root.subjectId().equals(after.characterId()) || !characterIds.contains(after.characterId()) || !CampaignDomainCodec.text(before).equals(root.before())
                            || root.kind().equals("character-create") != (before == null)) throw CampaignJson.bad("Profile history adjacency differs");
                    if(root.kind().equals("character-create")){
                        var initialized=eventsById.get(root.operationId());
                        if(!after.active()||root.characterOperations().size()!=1||!root.characterOperations().contains(root.operationId())||initialized==null||!initialized.kind().equals("INITIALIZED")||!initialized.after().characterId().equals(root.subjectId()))throw CampaignJson.bad("Character creation root differs from initial facts");
                    }
                    CampaignJson.name(after.name()); if (!Set.of("PC", "NPC").contains(after.kind())) throw CampaignJson.bad("Profile kind differs");
                    profiles.put(after.characterId(), after);
                }
                case "node-create", "node-link" -> {
                    Node after = document(root.after(), Node.class); Node before = nodes.get(root.subjectId());
                    if (!root.subjectId().equals(after.id()) || !CampaignDomainCodec.text(before).equals(root.before()) || root.kind().equals("node-create") != (before == null)) throw CampaignJson.bad("Node history adjacency differs");
                    if(before==null){if(!after.adjacent().isEmpty())throw CampaignJson.bad("New node cannot contain prelinked edges");}
                    else{
                        Set<UUID> added=new HashSet<>(after.adjacent());added.removeAll(before.adjacent());
                        if(!after.name().equals(before.name())||after.adjacent().size()!=before.adjacent().size()+1||!after.adjacent().containsAll(before.adjacent())||added.size()!=1
                                ||new HashSet<>(after.adjacent()).size()!=after.adjacent().size()||!after.adjacent().equals(after.adjacent().stream().sorted(Comparator.comparing(UUID::toString)).toList())
                                ||added.contains(after.id())||!nodes.keySet().containsAll(added))throw CampaignJson.bad("Node link must add exactly one existing edge without changing its node");
                    }
                    nodes.put(after.id(), after);
                }
                case "position" -> {
                    Position after = document(root.after(), Position.class);
                    if (!nodes.containsKey(after.nodeId())||!known(profiles,manual,after.characterId())||!root.subjectId().equals(after.characterId()) || !CampaignDomainCodec.text(positions.get(after.characterId())).equals(root.before())) throw CampaignJson.bad("Position history adjacency or node prefix differs");
                    if((manual.characters().stream().anyMatch(c->c.id().equals(after.characterId()))||CampaignManualData.active(manual))&&!member(manual,after.characterId()))throw CampaignJson.bad("Position root lacks an active simple participant");
                    positions.put(after.characterId(), after);
                    manual=CampaignManualData.touch(manual,after.characterId());
                }
                case "item" -> {
                    Item after = document(root.after(), Item.class);
                    if(!known(profiles,manual,after.characterId())||!root.subjectId().equals(after.id()) || !CampaignDomainCodec.text(items.get(after.id())).equals(root.before())) throw CampaignJson.bad("Item history adjacency differs");
                    if(manual.characters().stream().anyMatch(c->c.id().equals(after.characterId()))&&(after.quantity()<1||after.quantity()>999))throw CampaignJson.bad("Manual item quantity differs");
                    Item before=items.get(after.id());
                    if(before==null){if(after.sourceKey()!=null||!after.description().isEmpty()||!after.active())throw CampaignJson.bad("Ordinary new item contains unsupported provenance");}
                    else if(!before.characterId().equals(after.characterId())||!Objects.equals(before.sourceKey(),after.sourceKey())||!before.description().equals(after.description())||before.active()!=after.active()
                            ||manual.characters().stream().anyMatch(c->c.id().equals(after.characterId()))&&!before.name().equals(after.name()))throw CampaignJson.bad("Ordinary item update changes its owner or provenance");
                    items.put(after.id(), after);
                    manual=CampaignManualData.touch(manual,after.characterId());
                }
                case "adjudicated-check" -> {
                    if (CampaignManualData.active(manual) || !root.before().isEmpty()) throw CampaignJson.bad("Unexpected adjudication scene owner");
                    CampaignAdjudicationCommands.validateAudit(state, root, document(root.after(), CampaignAdjudicationData.Audit.class), participation, nodes, items, positions);
                }
                case "check-effects" -> {
                    if (CampaignManualData.active(manual)||!root.before().isEmpty()) throw CampaignJson.bad("Unexpected check-effects owner or before document");
                    CampaignCheckEffects.validateAudit(state, root, document(root.after(), CampaignCheckEffects.Audit.class), nodes, items, positions);
                }
                case "note" -> { if (!root.before().isEmpty() || root.after().isBlank() || root.after().codePointCount(0, root.after().length()) > 4096) throw CampaignJson.bad("Invalid note"); }
                case "character-command" -> {
                    if(CampaignManualData.active(manual))throw CampaignJson.bad("Rule-character command bypasses the simple scene owner");
                    var event=eventsById.get(root.operationId());
                    if(!root.before().isEmpty()||!root.after().isEmpty()||root.combatId()!=null||event==null||!root.characterOperations().contains(root.operationId())||!event.after().characterId().equals(root.subjectId())||event.kind().equals("INITIALIZED")||com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.combatKind(event.kind()))throw CampaignJson.bad("Invalid ordinary character root");
                    long delta=second(event.after())-second(event.before());
                    for(UUID child:root.characterOperations())if(!child.equals(root.operationId())){
                        var value=eventsById.get(child);
                        if(delta<=0||value==null||value.after().characterId().equals(root.subjectId())||!value.kind().equals("TIME_ADVANCED")
                                ||!child.equals(CampaignDomainEngine.campaignTimeChildOperation(root.operationId(),value.after().characterId()))||second(value.after())-second(value.before())!=delta)
                            throw CampaignJson.bad("Ordinary root contains an unrelated child instead of its clock continuation");
                    }
                }
                case "combat-command" -> {
                    if (CampaignManualData.active(manual)||!root.before().isEmpty() || !root.after().isEmpty()||root.combatId()==null||!root.combatId().equals(root.subjectId())) throw CampaignJson.bad("Unexpected scene command metadata");
                    var history=state.combats().stream().filter(c->c.current().combatId().equals(root.combatId())).findFirst().orElseThrow();
                    var event=history.events().get(combatCursors.get(root.combatId())-1);
                    if(!new HashSet<>(event.characterOperations()).equals(new HashSet<>(root.characterOperations()))||event.characterOperations().size()!=root.characterOperations().size())throw CampaignJson.bad("Scene causal children differ from their root");
                }
                default -> {
                    if(!root.kind().startsWith("manual-"))throw CampaignJson.bad("Unsupported domain root kind");
                    manual=CampaignManualCommands.validateAudit(state,root,document(root.after(),CampaignManualCommands.Audit.class),manual,profiles,nodes,items,positions);
                }
            }
            participation = CampaignParticipationCommands.afterSceneChange(sceneBefore, List.copyOf(historicalBattles.values()), manualBefore, manual, participation);
            CampaignParticipationCommands.validateCurrent(profiles, manual, participation);
            Long prefixSecond=null;
            for(var character:state.characters()){
                int cursor=characterCursors.get(character.id());if(cursor==0)continue;
                long value=second(character.history().events().get(cursor-1).after());
                if(prefixSecond!=null&&prefixSecond!=value)throw CampaignJson.bad("Root leaves campaign clocks inconsistent");prefixSecond=value;
            }
        }
        if (!operations.equals(referencedOperations) || !combatOperations.equals(referencedCombats)) throw CampaignJson.bad("Orphan domain history");
        if (!participation.equals(state.participation())) throw CampaignJson.bad("Participation current state differs from full root history");
        CampaignParticipationCommands.validateCurrent(state);
        if(!manual.equals(state.manual()))throw CampaignJson.bad("Manual current/initial state differs from full root history");
        if(CampaignManualData.active(manual)&&state.combats().stream().anyMatch(c->c.current().status()!=com.dndtool.service.Schema2CombatState.Status.ENDED))throw CampaignJson.bad("Two scene owners are active");
        for (var c : state.characters()) if (!new Profile(c.id(), c.name(), c.kind(), c.active()).equals(profiles.remove(c.id()))) throw CampaignJson.bad("Current profile differs from history");
        if (!profiles.isEmpty() || !same(nodes.values(), state.nodes()) || !same(positions.values(), state.positions()) || !same(items.values(), state.items())) throw CampaignJson.bad("Current world differs from complete history");
        for (Node node : state.nodes()) if (new HashSet<>(node.adjacent()).size() != node.adjacent().size() || node.adjacent().contains(node.id()) || !nodes.keySet().containsAll(node.adjacent())) throw CampaignJson.bad("Invalid graph references");
        for (Position position : state.positions()) if (!characterIds.contains(position.characterId()) || !nodes.containsKey(position.nodeId())) throw CampaignJson.bad("Invalid position references");
        for (Item item : state.items()) if (!characterIds.contains(item.characterId())) throw CampaignJson.bad("Invalid item owner");
        if(manual.partyNodeId()!=null&&!nodes.containsKey(manual.partyNodeId()))throw CampaignJson.bad("Unknown party node");
        for(var encounter:manual.encounters())for(var participant:encounter.participants()){
            if(!characterIds.contains(participant.characterId()))throw CampaignJson.bad("Unknown retained simple participant");
            if(encounter.status().equals("ACTIVE")&&(!known(profilesFor(state),manual,participant.characterId())||!positions.containsKey(participant.characterId())))throw CampaignJson.bad("Active simple participant lacks a current position");
        }
        return snapshot;
    }
    private static boolean same(Collection<?> actual, Collection<?> expected) { return actual.size() == expected.size() && new HashSet<>(actual).equals(new HashSet<>(expected)); }
    private static boolean known(Map<UUID,Profile> profiles,CampaignManualData.State manual,UUID id){return profiles.containsKey(id)&&profiles.get(id).active()||manual.characters().stream().anyMatch(c->c.id().equals(id)&&c.current().active());}
    private static boolean member(CampaignManualData.State manual,UUID id){return manual.encounters().stream().filter(e->e.status().equals("ACTIVE")).flatMap(e->e.participants().stream()).anyMatch(p->p.characterId().equals(id));}
    private static Map<UUID,Profile> profilesFor(State state){Map<UUID,Profile> result=new HashMap<>();state.characters().forEach(c->result.put(c.id(),new Profile(c.id(),c.name(),c.kind(),c.active())));return result;}
    private static long second(com.dndtool.persistence.JdbcSchema2CharacterRuntimeRepository.State state){return Math.addExact(Math.multiplyExact(state.rest().minute(),60),state.rest().second());}
    private static <T> T document(String value, Class<T> type) { return CampaignDomainCodec.decode(value.getBytes(StandardCharsets.UTF_8), type); }
    public static CampaignData.EventFact summary(Root root) { return new CampaignData.EventFact(root.sequence(), "DOMAIN_COMMAND", root.subjectId().toString(), 0, 0, 0, 0, "", 0); }
}
