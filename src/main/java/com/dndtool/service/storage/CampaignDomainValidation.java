package com.dndtool.service.storage;

import com.dndtool.service.CampaignDomainEngine;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.dndtool.service.storage.CampaignDomainData.*;
import static com.dndtool.service.storage.CampaignSpaceData.Ref;

/** Pure verification of retained prefixes and results. No live randomness, writes or resumed actions. */
public final class CampaignDomainValidation {
    private CampaignDomainValidation() { }
    // Immutable, fully compared prefixes only. Decoded/tampered facts still have to equal the
    // retained result; this cache never substitutes a digest or skips a new root consequence.
    private static final Deque<CampaignData.Snapshot> VERIFIED = new ArrayDeque<>();
    private static synchronized State verifiedPrefix(CampaignData.Snapshot snapshot) {
        State best = null;
        for (var cached : VERIFIED) {
            State value = cached.domains();
            if (cached.campaignId().equals(snapshot.campaignId()) && cached.name().equals(snapshot.name())
                    && value.material().equals(snapshot.domains().material())
                    && value.roots().size() <= snapshot.domains().roots().size()
                    && snapshot.domains().roots().subList(0, value.roots().size()).equals(value.roots())
                    && (best == null || value.roots().size() > best.roots().size())) best = value;
        }
        return best;
    }
    private static synchronized void remember(CampaignData.Snapshot snapshot) {
        for (var cached : VERIFIED) {
            int length = snapshot.domains().roots().size();
            if (cached.campaignId().equals(snapshot.campaignId()) && cached.name().equals(snapshot.name())
                    && cached.domains().material().equals(snapshot.domains().material())
                    && cached.domains().roots().size() >= length
                    && cached.domains().roots().subList(0, length).equals(snapshot.domains().roots())) return;
        }
        VERIFIED.removeIf(cached -> cached.campaignId().equals(snapshot.campaignId()));
        VERIFIED.addLast(snapshot);
        while (VERIFIED.size() > 2) VERIFIED.removeFirst();
    }
    public static List<CampaignDomainEngine.CharacterRecord> records(State state) {
        return state.characters().stream().map(c -> new CampaignDomainEngine.CharacterRecord(c.name(), c.kind(), c.initial(), c.history())).toList();
    }

    static Set<UUID> retainedEventOperations(State state) {
        Set<UUID> existing = new HashSet<>();
        for (Root root : state.roots()) {
            existing.add(root.operationId());
            existing.addAll(root.characterOperations());
        }
        for (var character : state.characters()) for (var event : character.history().events())
            existing.add(event.operationId());
        for (var history : state.combats()) for (var event : history.events()) {
            existing.add(event.operationId());
            existing.addAll(event.characterOperations());
        }
        return existing;
    }

    /** Joins recorded ownership to actual histories before a cached or replayed prefix is used. */
    private static void validateEventIdentities(State state) {
        Map<UUID, Root> roots = new HashMap<>();
        Map<UUID, Root> characterOwners = new HashMap<>();
        Set<UUID> missingScenes = new HashSet<>(), sceneOperations = new HashSet<>();
        for (Root root : state.roots()) {
            if (roots.putIfAbsent(root.operationId(), root) != null)
                throw CampaignJson.bad("Duplicate campaign root operation");
            if (root.combatId() != null) missingScenes.add(root.operationId());
            for (UUID child : root.characterOperations()) if (characterOwners.putIfAbsent(child, root) != null)
                throw CampaignJson.bad("Character operation belongs to more than one root occurrence");
        }
        for (var history : state.combats()) {
            if (history.current() == null || history.current().combatId() == null)
                throw CampaignJson.bad("Scene history has no identity");
            UUID scene = history.current().combatId();
            for (var event : history.events()) {
                Root root = roots.get(event.operationId());
                if (root == null || !scene.equals(root.combatId()) || !sceneOperations.add(event.operationId()))
                    throw CampaignJson.bad("Scene operation has no unique campaign root");
                if (event.characterOperations().size() != root.characterOperations().size()
                        || !new HashSet<>(event.characterOperations()).equals(new HashSet<>(root.characterOperations())))
                    throw CampaignJson.bad("Scene operation children differ from its campaign root");
                missingScenes.remove(event.operationId());
            }
        }
        if (!missingScenes.isEmpty()) throw CampaignJson.bad("Campaign root has no scene operation");
        for (var character : state.characters()) for (var event : character.history().events()) {
            UUID operation = event.operationId();
            Root owner = characterOwners.remove(operation), root = roots.get(operation);
            // An INITIALIZED or direct character event can carry its own root's ID.
            if (owner == null || (root != null && root != owner) || sceneOperations.contains(operation)
                    || event.command() == null)
                throw CampaignJson.bad("Character operation has no unique campaign root");
            var combat = event.command().combatInput();
            if (owner.combatId() == null ? combat != null : combat == null
                    || !owner.combatId().equals(combat.combatId()) || !owner.operationId().equals(combat.combatOperationId()))
                throw CampaignJson.bad("Combat character operation has a different scene or root");
        }
        if (!characterOwners.isEmpty()) throw CampaignJson.bad("Campaign root has no character operation");
    }

    public static CampaignData.Snapshot validate(CampaignData.Snapshot snapshot) {
        State retained = snapshot.domains();
        if (retained.characters().size() > 16 || retained.combats().size() > 128 || retained.roots().size() > 100000
                || retained.nodes().size() > 10000 || retained.items().size() > 10000 || retained.space().monsters().size() > 1024)
            throw CampaignJson.bad("Campaign domain capacity exceeded");
        validateEventIdentities(retained);
        State prefix = verifiedPrefix(snapshot);
        if (prefix == null) prefix = State.initial(retained.material().restore().projection());
        var reducer = new CampaignDomainCommands();
        for (Root root : retained.roots().subList(prefix.roots().size(), retained.roots().size())) {
            if (root.sequence() != prefix.roots().size() + 1L || !root.space().before().equals(prefix.space())
                    || !root.space().beforePositions().equals(prefix.positions())) throw CampaignJson.bad("Spatial history is not an exact contiguous prefix");
            var input = CampaignJson.read(root.space().request().getBytes(StandardCharsets.UTF_8));
            ArrayDeque<CampaignSpaceData.Die> original = new ArrayDeque<>(root.rolls());
            try {
                State checked = reducer.apply(new CampaignData.Snapshot(snapshot.campaignId(), snapshot.name(), prefix), root.kind(), input,
                        root.operationId(), root.intent(), sides -> {
                            var die = original.pollFirst();
                            if (die == null || die.sides() != sides) throw CampaignJson.bad("Retained root dice do not match this stage");
                            return die.value();
                        }, root.actor());
                if (!original.isEmpty() || !checked.roots().getLast().equals(root)) throw CampaignJson.bad("Root result differs from its exact intent, prefix and original dice");
                validateSpace(checked); prefix = checked;
            } catch (StorageException | IllegalArgumentException | ArithmeticException invalid) {
                throw new StorageException("INVALID_FILE", "Retained campaign root is invalid", invalid);
            }
        }
        if (!prefix.equals(retained)) throw CampaignJson.bad("Complete facts differ from retained root consequences");
        validateSpace(retained); remember(snapshot); return snapshot;
    }
    /** Independent joins verify every current spatial projection, including inactive retained scenes. */
    public static void validateSpace(State state) {
        Set<UUID> nodes = new HashSet<>(), scenes = new HashSet<>(), subjects = new HashSet<>(), placed = new HashSet<>();
        Set<Ref> clocks = new HashSet<>(), expectedClocks = new HashSet<>();
        for (Node node : state.nodes()) { if (!nodes.add(node.id())) throw CampaignJson.bad("Duplicate world node"); expectedClocks.add(Ref.world(node.id())); }
        for (Node node : state.nodes()) if (new HashSet<>(node.adjacent()).size() != node.adjacent().size()
                || node.adjacent().contains(node.id()) || !nodes.containsAll(node.adjacent())) throw CampaignJson.bad("Invalid world route graph");
        for (var scene : state.space().scenes()) {
            if (!scenes.add(scene.id()) || nodes.contains(scene.id()) || !nodes.contains(scene.nodeId())) throw CampaignJson.bad("Invalid scene anchor or identity");
            expectedClocks.add(Ref.scene(scene.id()));
        }
        for (var clock : state.space().clocks()) if (!clocks.add(clock.space())) throw CampaignJson.bad("Duplicate clock authority");
        if (!clocks.equals(expectedClocks)) throw CampaignJson.bad("Missing or orphan space clock");
        for (var c : state.characters()) if (!subjects.add(c.id())) throw CampaignJson.bad("Duplicate character");
        for (var c : state.manual().characters()) if (!subjects.add(c.id())) throw CampaignJson.bad("Duplicate free card");
        for (var m : state.space().monsters()) if (!subjects.add(m.id())) throw CampaignJson.bad("Duplicate monster");
        for (Position p : state.positions()) {
            if (!placed.add(p.characterId()) || !subjects.contains(p.characterId()) || !clocks.contains(p.space())) throw CampaignJson.bad("Invalid unique placement");
            if (p.grid() != null) {
                var scene = state.space().scene(p.space().id());
                if (scene.mode().equals("CLOSED")) throw CampaignJson.bad("Closed scene retains an occupant");
                CampaignSpaceCommands.checkCell(scene, p.grid());
            }
        }
        CampaignSpaceCommands.uniqueCells(state.positions());
        Ref focus = state.space().focus().space();
        if (focus == null ? !state.positions().isEmpty() : !clocks.contains(focus)
                || focus.kind().equals("SCENE") && state.space().scene(focus.id()).mode().equals("CLOSED")) throw CampaignJson.bad("Invalid campaign focus");
        Set<UUID> actualScenes = new HashSet<>();
        for (var history : state.combats()) {
            var battle = history.current(); UUID id = battle.combatId();
            if (!actualScenes.add(id) || !scenes.contains(id)) throw CampaignJson.bad("Orphan rule scene");
            var scene = state.space().scene(id);
            String mode = switch (battle.status()) { case ACTIVE -> "COMBAT"; case EXPLORATION -> "EXPLORATION"; case ENDED -> "CLOSED"; };
            if (!scene.resolution().equals("RULES") || !scene.mode().equals(mode)) throw CampaignJson.bad("Scene and combat mode disagree");
            Set<UUID> expected = new HashSet<>(), actual = new HashSet<>();
            for (Position p : state.positions()) if (p.space().equals(Ref.scene(id))
                    && state.manual().characters().stream().noneMatch(c -> c.id().equals(p.characterId()))) expected.add(p.characterId());
            for (var c : battle.combatants()) {
                Position p = CampaignSpaceCommands.position(state, c.characterId());
                if (!actual.add(c.characterId()) || p == null || !p.space().equals(Ref.scene(id))
                        || !p.grid().equals(c.position()) || p.side() != c.side()) throw CampaignJson.bad("Battle roster differs from unique placements");
            }
            if (!actual.equals(expected)) throw CampaignJson.bad("Rule scene roster is incomplete");
            for (var m : battle.monsters()) {
                var canonical = state.space().monsters().stream().filter(v -> v.id().equals(m.monsterId())).findFirst().orElseThrow(() -> CampaignJson.bad("Unknown battle monster"));
                if (!canonical.rule().equals(m.monster()) || canonical.hitPoints() != m.hitPoints() || canonical.dead() != m.dead() || !canonical.actions().equals(m.actionUses())) throw CampaignJson.bad("Monster facts differ from their campaign owner");
            }
        }
        for (var scene : state.space().scenes()) if (scene.resolution().equals("RULES") && !actualScenes.contains(scene.id())) throw CampaignJson.bad("Rule scene lacks process history");
        for (var c : state.characters()) if (c.history().current().health().stable()) {
            Position p = CampaignSpaceCommands.position(state, c.id()); if (p == null) throw CampaignJson.bad("Timed health obligation lacks a time domain");
            var value = c.history().current(); long now = value.rest().minute() * 60 + value.rest().second();
            long due = value.health().stableRecoveryMinute() * 60 + value.health().stableRecoverySecond();
            if (due <= now || Math.addExact(state.space().clock(p.space()).second(), due - now) > CampaignSpaceData.MAX_SECOND) throw CampaignJson.bad("Invalid derived spatial health deadline");
        }
        CampaignParticipationCommands.validateCurrent(state);
    }
}
