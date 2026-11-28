package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.service.Schema2CharacterCheckRules;
import com.dndtool.service.Schema2CharacterEquipmentRules;
import com.dndtool.service.Schema2CombatHistory;
import com.dndtool.service.Schema2CombatRules;
import com.dndtool.service.Schema2CombatState;
import com.dndtool.service.Schema2ObservationRules;
import com.google.gson.*;
import java.util.*;
import static com.dndtool.service.storage.CampaignParticipationData.*;

/** A positive field allowlist. No Host DTO, history graph or rule material is serialized here. */
public final class PlayerCampaignProjection {
    public static JsonObject project(CampaignStorageService.AccessSnapshot access, UUID member) {
        var state = access.snapshot().domains();
        requireMember(state, member);
        Set<UUID> own = owned(state, member), visible = visible(state, member);
        JsonObject view = new JsonObject();
        view.addProperty("campaignName", "冒险房间");
        view.addProperty("playerName", state.participation().member(member).name());
        JsonArray cards = new JsonArray();
        for (var c : state.characters()) if (own.contains(c.id())) cards.add(ruleCard(state, c, member));
        for (var c : state.manual().characters()) if (own.contains(c.id())) cards.add(manualCard(state, c.current()));
        view.add("cards", cards);
        JsonObject shared = new JsonObject(); JsonArray summaries = new JsonArray(), scenes = new JsonArray(), logs = new JsonArray();
        visible.stream().filter(id -> !own.contains(id)).sorted().limit(64).forEach(id -> {
            JsonObject c = new JsonObject(); c.addProperty("id", id.toString()); c.addProperty("name", characterName(state, id));
            c.addProperty("description", "主持人共享的角色"); summaries.add(c);
        });
        var focus = state.space().focus().space();
        for (var local : state.space().scenes()) {
            if (scenes.size() == 16) break;
            if (local.mode().equals("CLOSED") || !shared(state, member, ShareKind.SCENE, local.id())) continue;
            var ref = CampaignSpaceData.Ref.scene(local.id());
            var battle = state.combats().stream().map(h -> h.current()).filter(b -> b.combatId().equals(local.id())).findFirst().orElse(null);
            String mode = local.mode().equals("COMBAT") ? "战斗" : "探索";
            scenes.add(spaceView(state, own, visible, visibleMonsters(state, member, local.id()), ref, local.name(), focus, battle, mode));
        }
        for (var node : state.nodes()) if (scenes.size() < 16 && shared(state, member, ShareKind.SCENE, node.id()))
            scenes.add(spaceView(state, own, visible, Set.of(), CampaignSpaceData.Ref.world(node.id()), node.name(), focus, null, "世界节点"));
        List<String> allowedLogs = new ArrayList<>();
        UUID campaign = UUID.fromString(access.snapshot().campaignId());
        for (var root : state.roots()) {
            if (!(shared(state, member, ShareKind.LOG, campaign) || shared(state, member, ShareKind.LOG, root.operationId()))) continue;
            // A shared log is not a member directory or a route to hidden action dependencies.
            if (root.kind().equals("note")) allowedLogs.add(root.after());
            else if (root.actor().characterId() != null && visible.contains(root.actor().characterId()))
                allowedLogs.add(characterName(state, root.actor().characterId()) + "：" + actionLabel(root.kind()));
        }
        allowedLogs.stream().skip(Math.max(0, allowedLogs.size() - 50)).forEach(text -> { JsonObject item = new JsonObject(); item.addProperty("text", text); logs.add(item); });
        shared.add("cards", summaries); shared.add("scenes", scenes); shared.add("logs", logs); view.add("shared", shared);
        view.add("pending", pending(state, member, own));
        return view;
    }
    private static JsonObject spaceView(CampaignDomainData.State state, Set<UUID> own, Set<UUID> visible, Set<UUID> monsters,
            CampaignSpaceData.Ref ref, String name, CampaignSpaceData.Ref focus, Schema2CombatState.Battle battle, String mode) {
        boolean focused = ref.equals(focus);
        JsonObject row = new JsonObject();
        row.addProperty("name", name);
        row.addProperty("description", mode + (focused ? " · 当前执行空间" : " · 后台冻结"));
        row.addProperty("focused", focused);
        var observers = battle == null ? List.<UUID>of() : battle.combatants().stream()
                .map(Schema2CombatState.Combatant::characterId).filter(own::contains).toList();
        if (observers.size() <= 1) {
            addSceneParticipants(row, state, visible, monsters, ref, name, focused, battle, observers.isEmpty() ? null : observers.getFirst());
        } else {
            JsonArray perspectives = new JsonArray();
            for (UUID observer : observers) {
                JsonObject perspective = new JsonObject();
                perspective.addProperty("observer", characterName(state, observer));
                addSceneParticipants(perspective, state, visible, monsters, ref, name, focused, battle, observer);
                perspectives.add(perspective);
            }
            row.add("perspectives", perspectives);
        }
        return row;
    }

    private record SceneParticipant(UUID subject, Schema2CombatState.Position position, boolean lastKnown) { }

    private static void addSceneParticipants(JsonObject row, CampaignDomainData.State state, Set<UUID> visible,
            Set<UUID> monsters, CampaignSpaceData.Ref ref, String name, boolean focused, Schema2CombatState.Battle battle, UUID observer) {
        Map<UUID, Integer> initiative = new HashMap<>();
        if (battle != null && battle.status() == Schema2CombatState.Status.ACTIVE)
            battle.initiative().forEach(turn -> initiative.put(turn.characterId(), turn.ordinal()));
        UUID actor = initiative.isEmpty() ? null : battle.activeActor();
        Set<UUID> combatants = battle == null ? Set.of() : battle.combatants().stream().map(Schema2CombatState.Combatant::characterId)
                .collect(java.util.stream.Collectors.toSet());
        if (focused && actor != null)
            row.addProperty("turnLabel", (visible.contains(actor) || monsters.contains(actor))
                    && observer != null && Schema2ObservationRules.visible(battle, observer, actor)
                    ? participantName(state, monsters, actor) + "的回合" : "等待其他行动者");
        List<SceneParticipant> placements = new ArrayList<>();
        for (var placement : state.positions()) {
            UUID subject = placement.characterId();
            if (!ref.equals(placement.space()) || (!visible.contains(subject) && !monsters.contains(subject))) continue;
            var grid = placement.grid();
            boolean lastKnown = false;
            if (battle != null) {
                if (observer == null || !combatants.contains(subject)) continue;
                grid = Schema2ObservationRules.knownPosition(battle, observer, subject);
                if (grid == null) continue;
                lastKnown = !Schema2ObservationRules.visible(battle, observer, subject);
            }
            placements.add(new SceneParticipant(subject, grid, lastKnown));
        }
        var order = Comparator.comparing(SceneParticipant::lastKnown)
                .thenComparingInt(p -> p.lastKnown() ? 0 : initiative.getOrDefault(p.subject(), Integer.MAX_VALUE))
                .thenComparing(p -> p.subject().toString());
        JsonArray participants = new JsonArray();
        placements.stream().sorted(order).limit(64).forEach(placement -> {
            JsonObject person = new JsonObject();
            person.addProperty("name", participantName(state, monsters, placement.subject()));
            var grid = placement.position();
            if (placement.lastKnown()) person.addProperty("lastKnown", true);
            person.addProperty("position", grid == null ? name : grid.x() + ", " + grid.y());
            person.addProperty("current", focused && !placement.lastKnown() && placement.subject().equals(actor));
            participants.add(person);
        });
        row.add("participants", participants);
    }

    public static JsonObject receipt(CampaignStorageService.AccessSnapshot access, UUID member, CampaignDomainData.Root root) {
        var domain = access.snapshot().domains();
        requireMember(domain, member);
        if (root.actor().kind() != ActorKind.PLAYER || !member.equals(root.actor().memberId()))
            throw new StorageException("PLAYER_REQUEST_UNAVAILABLE", "Request is unavailable");
        var history = root.combatId() == null ? null : domain.combats().stream()
                .filter(c -> c.current().combatId().equals(root.combatId())).findFirst().orElse(null);
        var event = history == null ? null : history.events().stream()
                .filter(e -> e.operationId().equals(root.operationId())).findFirst().orElse(null);
        var active = event == null ? null : event.after().activeFlow();
        boolean waiting = active != null;
        if (active != null) {
            var leaf = active.frames().stream().filter(f -> f.frameKey().equals(active.leafFrameKey())).findFirst().orElseThrow();
            var sneakAttack = CampaignCombatView.pendingSneakAttack(active, leaf);
            if (sneakAttack != null && !sneakAttack.actorId().equals(root.actor().characterId())) waiting = false;
        }
        JsonObject result = new JsonObject(); result.addProperty("state", "ACCEPTED");
        result.addProperty("outcome", waiting ? "WAITING" : "COMPLETED");
        result.addProperty("label", waiting ? "操作已接受，当时正在等待后续决定" : "操作已完成");
        if (event != null && Objects.equals(event.command().actorId(), root.actor().characterId())) {
            switch (event.command().kind()) {
                case ATTACK, DECLARE_OPPORTUNITY_ATTACK -> addMonsterAttackResult(result, event);
                case USE_SNEAK_ATTACK, DECLINE_SNEAK_ATTACK -> {
                    var flow = event.after().flows().stream().filter(f -> f.flowKey().equals(event.command().flowKey()))
                            .findFirst().orElseThrow();
                    var attack = flow.attacks().stream().filter(a -> a.frameKey().equals(event.command().leafKey()))
                            .findFirst().orElseThrow();
                    result.add("sneakAttackResult", sneakAttackResult(domain, member, history.current(), attack));
                }
                default -> { }
            }
        }
        if (event != null && isCantrip(event)) {
            var flow = event.after().flows().getLast();
            JsonObject outcome = cantripOutcome(domain, event, flow);
            if (outcome != null) result.add("cantripResult", outcome);
        }
        return result;
    }

    private static boolean isCantrip(Schema2CombatHistory.Event event) {
        return event.command().kind() == Schema2CombatState.Kind.CAST && event.command().castInput().slotLevel() == 0;
    }

    private static JsonObject cantripOutcome(CampaignDomainData.State domain, Schema2CombatHistory.Event event,
            Schema2CombatState.Flow flow) {
        var cast = flow.casts().stream().filter(c -> c.frameKey().equals(flow.rootFrameKey())).findFirst().orElseThrow();
        JsonObject result = new JsonObject();
        result.addProperty("spell", ruleName(domain, cast.spell()));
        if (cast.status() == Schema2CombatState.CastStatus.COUNTERED || cast.status() == Schema2CombatState.CastStatus.INTERRUPTED) {
            result.addProperty("outcome", cast.status().name());
            return result;
        }
        UUID target = cast.targets().getFirst();
        UUID damageFrame = cast.frameKey();
        int damage;
        if (cast.sacredFlame() != null) {
            var save = cast.sacredFlame();
            result.addProperty("outcome", save.savingThrow().success() ? "SAVED" : "FAILED_SAVE");
            damage = save.damage() == null ? 0 : save.damage().effectiveDamage();
        } else {
            var attack = flow.attacks().stream().filter(a -> a.spellAttack() != null
                    && a.spellAttack().castFrameKey().equals(cast.frameKey())).findFirst().orElse(null);
            if (attack == null || attack.status() != Schema2CombatState.AttackStatus.RESOLVED) return null;
            result.addProperty("outcome", attack.hit() ? "HIT" : "MISS");
            damageFrame = attack.frameKey();
            damage = attack.damageAmount();
        }
        result.addProperty("damage", damage);
        boolean dropped = false;
        if (damage > 0) {
            var monster = event.before().monsters().stream().filter(m -> m.monsterId().equals(target)).findFirst();
            if (monster.isPresent()) {
                int hp = monster.get().hitPoints();
                dropped = hp > 0 && hp <= damage;
            } else {
                for (var change : event.resolution().healthChanges()) {
                    if (damageFrame.equals(change.frameKey()) && change.characterId().equals(target)
                            && change.before().hitPoints() > 0 && change.after().hitPoints() == 0) dropped = true;
                }
            }
        }
        result.addProperty("dropped", dropped);
        return result;
    }

    private static void addLatestCantrip(JsonObject card, CampaignDomainData.State domain, UUID member, UUID caster) {
        var history = CampaignSpaceCommands.currentBattle(domain);
        if (history == null || !shared(domain, member, ShareKind.SCENE, history.current().combatId())) return;
        for (var entry : history.events().reversed()) {
            if (!isCantrip(entry) || !caster.equals(entry.command().actorId())) continue;
            boolean submitted = domain.roots().stream().anyMatch(root -> root.operationId().equals(entry.operationId())
                    && root.actor().kind() == ActorKind.PLAYER && member.equals(root.actor().memberId()));
            if (!submitted) continue;
            UUID target = entry.command().castInput().targets().getFirst();
            if (!visible(domain, member).contains(target) && !visibleMonsters(domain, member, history.current().combatId()).contains(target)) return;
            var rootFlow = entry.after().flows().getLast();
            for (var step : history.events()) {
                if (step.after().version() < entry.after().version()) continue;
                var flow = step.after().flows().stream().filter(f -> f.flowKey().equals(rootFlow.flowKey())).findFirst().orElseThrow();
                var outcome = cantripOutcome(domain, step, flow);
                if (outcome != null) {
                    card.add("lastCantrip", outcome);
                    return;
                }
            }
            return;
        }
    }

    private static void addMonsterAttackResult(JsonObject receipt, Schema2CombatHistory.Event event) {
        var flow = event.after().flows().getLast();
        UUID frame = event.command().kind() == Schema2CombatState.Kind.ATTACK ? flow.rootFrameKey()
                : flow.frames().stream().filter(f -> f.kind() == Schema2CombatState.FrameKind.ATTACK
                        && event.command().leafKey().equals(f.parentFrameKey())).findFirst().orElseThrow().frameKey();
        var attack = flow.attacks().stream().filter(a -> a.frameKey().equals(frame)).findFirst().orElseThrow();
        if (attack.status() != Schema2CombatState.AttackStatus.RESOLVED) return;
        var target = event.after().monsters().stream().filter(m -> m.monsterId().equals(attack.targetId())).findFirst().orElse(null);
        if (target == null) return;
        JsonObject result = new JsonObject();
        result.addProperty("hit", attack.hit());
        result.addProperty("damage", attack.damageAmount());
        result.addProperty("dropped", attack.hit() && target.dead());
        receipt.add("attackResult", result);
    }

    private static JsonObject sneakAttackHit(Schema2CombatState.AttackAttempt attack) {
        JsonObject result = new JsonObject();
        result.addProperty("attackTotal", CampaignCombatView.weaponAttackTotal(attack));
        result.addProperty("critical", attack.critical());
        return result;
    }

    private static JsonObject sneakAttackResult(CampaignDomainData.State domain, UUID member,
            Schema2CombatState.Battle battle, Schema2CombatState.AttackAttempt attack) {
        JsonObject result = sneakAttackHit(attack);
        result.addProperty("used", attack.sneakAttack().status() == Schema2CombatState.SneakAttackStatus.USED);
        UUID target = attack.targetId();
        if ((attack.rangedAttack() == null || shared(domain, member, ShareKind.SCENE, battle.combatId()))
                && (visible(domain, member).contains(target) || visibleMonsters(domain, member, battle.combatId()).contains(target))
                && Schema2ObservationRules.visible(battle, attack.actorId(), target))
            result.addProperty("damage", attack.damageAmount());
        return result;
    }

    static void requireMember(CampaignDomainData.State state, UUID member) {
        if (state == null || state.participation().members().stream().noneMatch(m -> m.id().equals(member) && m.active()))
            throw new StorageException("PLAYER_ACCESS_DENIED", "Player access is unavailable");
    }
    static Set<UUID> owned(CampaignDomainData.State state, UUID member) {
        Set<UUID> ids = new LinkedHashSet<>();
        state.participation().controls().stream().filter(c -> member.equals(c.memberId())).forEach(c -> ids.add(c.characterId()));
        ids.removeIf(id -> state.characters().stream().noneMatch(c -> c.id().equals(id) && c.active() && c.kind().equals("PC"))
                && state.manual().characters().stream().noneMatch(c -> c.id().equals(id) && c.current().active() && c.current().kind().equals("PC")));
        return Collections.unmodifiableSet(ids);
    }
    static Set<UUID> visible(CampaignDomainData.State state, UUID member) {
        Set<UUID> ids = new LinkedHashSet<>(owned(state, member));
        state.participation().shares().stream().filter(s -> s.kind() == ShareKind.CHARACTER && s.active() && s.audience().includes(member)).forEach(s -> ids.add(s.subjectId()));
        return Collections.unmodifiableSet(ids);
    }
    static boolean shared(CampaignDomainData.State state, UUID member, ShareKind kind, UUID subject) {
        return state.participation().shared(member, kind, subject);
    }
    static Set<UUID> visibleMonsters(CampaignDomainData.State state, UUID member, UUID sceneId) {
        return CampaignParticipationCommands.visibleMonsters(state, member, sceneId);
    }
    private static String participantName(CampaignDomainData.State state, Set<UUID> monsters, UUID id) {
        return monsters.contains(id) ? monsterName(state, id) : characterName(state, id);
    }
    static String monsterName(CampaignDomainData.State state, UUID id) {
        return state.space().monsters().stream().filter(monster -> monster.id().equals(id)).map(CampaignSpaceData.Monster::name)
                .findFirst().orElseThrow(() -> CampaignJson.bad("Unknown campaign monster"));
    }
    static String characterName(CampaignDomainData.State state, UUID id) {
        return state.characters().stream().filter(c -> c.id().equals(id)).map(CampaignDomainData.Character::name).findFirst()
                .orElseGet(() -> state.manual().characters().stream().filter(c -> c.id().equals(id)).map(c -> c.current().name()).findFirst().orElse("角色"));
    }
    static String ruleName(CampaignDomainData.State state, RuleIdentity rule) {
        return state.material().rules().stream().filter(r -> r.identity().equals(rule)).map(r -> r.definition().displayName()).findFirst().orElse("未知规则");
    }
    static JsonArray expertise(CampaignDomainData.State domain, ProductionSchema2Projection material, Schema2CharacterRuntime.State state) {
        JsonArray entries = new JsonArray();
        for (var target : state.expertise()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", ruleName(domain, target));
            entry.addProperty("proficiencyContribution", Schema2CharacterCheckRules.checkProficiencyBonus(material, state, target));
            entries.add(entry);
        }
        return entries;
    }
    private static JsonObject base(UUID id, String name, String kind, long hp, long maximum, int level) {
        JsonObject card = new JsonObject(); card.addProperty("id", id.toString()); card.addProperty("name", name); card.addProperty("kind", kind);
        card.addProperty("currentHp", hp); card.addProperty("maximumHp", maximum); card.addProperty("level", level); return card;
    }
    private static JsonArray abilities(Map<String, Integer> values) {
        JsonArray a = new JsonArray(); List<String> names = List.of("力量", "敏捷", "体质", "智力", "感知", "魅力");
        for (int i = 0; i < Schema2CharacterRuntime.ABILITIES.size(); i++) {
            Integer value = values.get(Schema2CharacterRuntime.ABILITIES.get(i));
            if (value == null) continue;
            JsonObject row = new JsonObject(); row.addProperty("label", names.get(i)); row.addProperty("value", value); row.addProperty("modifier", Math.floorDiv(value - 10, 2)); a.add(row);
        } return a;
    }
    private static JsonObject ruleCard(CampaignDomainData.State domain, CampaignDomainData.Character c, UUID member) {
        var state = c.history().current(); JsonObject card = base(c.id(), c.name(), c.kind(), state.hitPoints(), state.maximumHitPoints(), state.level());
        card.addProperty("className", ruleName(domain, state.characterClass())); card.addProperty("proficiencyBonus", state.proficiencyBonus());
        var material = domain.material().restore().projection();
        var equipmentRules = new Schema2CharacterEquipmentRules();
        card.addProperty("armorClass", equipmentRules.derive(material, state).armorClass());
        card.addProperty("speed", equipmentRules.walkingSpeed(material, c.initial(), state));
        card.add("abilities", abilities(state.abilities()));
        JsonObject health = new JsonObject(); health.addProperty("temporaryHp", state.health().temporaryHitPoints());
        health.addProperty("deathSuccesses", state.health().deathSuccesses()); health.addProperty("deathFailures", state.health().deathFailures());
        health.addProperty("dead", state.health().dead()); health.addProperty("stable", state.health().stable()); card.add("health", health);
        JsonArray resources = new JsonArray();
        for (var r : state.resources()) { JsonObject row = new JsonObject(); row.addProperty("label", ruleName(domain, r.identity())); row.addProperty("current", r.current()); row.addProperty("maximum", r.maximum()); row.addProperty("unlimited", r.unlimited()); resources.add(row); }
        for (var r : state.hitDicePools()) { JsonObject row = new JsonObject(); row.addProperty("label", "生命骰 d" + r.sides()); row.addProperty("current", r.current()); row.addProperty("maximum", r.maximum()); resources.add(row); }
        card.add("resources", resources);
        JsonArray equipment = items(domain, c.id());
        for (var item : state.equipment()) { JsonObject row = new JsonObject(); row.addProperty("name", ruleName(domain, item.item())); row.addProperty("quantity", 1); row.addProperty("location", location(item.location())); equipment.add(row); }
        card.add("equipment", equipment);
        JsonArray proficiencies = new JsonArray(); state.proficiencies().forEach(p -> proficiencies.add(ruleName(domain, p.target()))); card.add("proficiencies", proficiencies);
        card.add("expertise", expertise(domain, material, state));
        JsonArray features = new JsonArray(); state.features().forEach(f -> features.add(ruleName(domain, f.feature()))); state.feats().forEach(f -> features.add(ruleName(domain, f.feat()))); card.add("features", features);
        if (!state.knownCantrips().isEmpty()) {
            JsonArray known = new JsonArray();
            for (var acquisition : state.knownCantrips()) {
                var spell = material.classCantrips(acquisition.characterClass()).orElseThrow().options().stream()
                        .filter(s -> s.identity().equals(acquisition.spell())).findFirst().orElseThrow();
                JsonObject row = new JsonObject();
                row.addProperty("name", spell.displayName());
                row.addProperty("source", ruleName(domain, acquisition.characterClass()));
                row.addProperty("executable", spell.cantripProfile() != null);
                known.add(row);
            }
            card.add("knownCantrips", known);
            addLatestCantrip(card, domain, member, c.id());
        }
        if (state.spellbook() != null) {
            JsonObject book = new JsonObject(); JsonArray learned = new JsonArray(), prepared = new JsonArray();
            state.spellbook().learned().forEach(s -> learned.add(ruleName(domain, s.spell()))); state.spellbook().prepared().forEach(s -> prepared.add(ruleName(domain, s)));
            book.add("learned", learned); book.add("prepared", prepared); card.add("spellbook", book);
        }
        JsonArray effects = new JsonArray();
        for (var history : domain.combats()) if (history.current().status() != Schema2CombatState.Status.ENDED) {
            var battle = history.current();
            for (var actor : battle.combatants()) if (actor.characterId().equals(c.id())) {
                if (actor.prone()) effect(effects, "倒地");
                if (actor.dodging()) effect(effects, "闪避");
                if (battle.status() == Schema2CombatState.Status.ACTIVE) {
                    var context = new Schema2CombatRules.Context(material, c.initial(), state);
                    card.add("turnBudget", CampaignCombatView.turnBudget(Map.of(c.id(), context), battle, actor));
                }
            }
            battle.ongoingEffects().stream().filter(e -> e.status() == Schema2CombatState.EffectStatus.ACTIVE && e.targets().contains(c.id())).forEach(e -> effect(effects, ruleName(domain, e.spell())));
            if (battle.concentrationHolders().stream().anyMatch(h -> h.actorId().equals(c.id()))) effect(effects, "专注中");
        } card.add("effects", effects); return card;
    }
    private static JsonObject manualCard(CampaignDomainData.State domain, CampaignManualData.Card c) {
        JsonObject card = base(c.id(), c.name(), c.kind(), c.fields().get("hp.current"), c.fields().get("hp.maximum"), c.totalLevel());
        card.addProperty("className", "主持人维护的角色卡"); card.addProperty("armorClass", c.fields().get("armor_class")); card.addProperty("speed", c.fields().get("speed.ground"));
        card.addProperty("proficiencyBonus", c.proficiencyBonus()); card.add("abilities", abilities(c.fields())); card.add("equipment", items(domain, c.id()));
        JsonArray proficiencies = new JsonArray();
        CampaignManualData.catalog().skills().forEach(s -> { if (!c.skills().get(s.key()).equals("NONE")) proficiencies.add(s.name() + " · " + c.skills().get(s.key())); });
        CampaignManualData.catalog().saves().forEach(s -> { if (!c.saves().get(s.key()).equals("NONE")) proficiencies.add(s.name() + " · " + c.saves().get(s.key())); });
        card.add("proficiencies", proficiencies); card.add("resources", new JsonArray()); card.add("effects", new JsonArray()); return card;
    }
    private static JsonArray items(CampaignDomainData.State domain, UUID owner) {
        JsonArray rows = new JsonArray(); domain.items().stream().filter(i -> i.characterId().equals(owner) && i.active()).limit(128).forEach(i -> {
            JsonObject row = new JsonObject(); row.addProperty("name", i.name()); row.addProperty("quantity", i.quantity()); row.addProperty("location", "随身物品"); rows.add(row);
        }); return rows;
    }
    private static String location(String location) { return switch (location) { case "MAIN_HAND" -> "主手"; case "OFF_HAND" -> "副手"; case "BOTH_HANDS" -> "双手"; case "WORN" -> "穿戴"; case "GROUND" -> "落地"; default -> "携带"; }; }
    private static void effect(JsonArray rows, String label) { JsonObject row = new JsonObject(); row.addProperty("label", label); rows.add(row); }
    private static JsonObject pending(CampaignDomainData.State state, UUID member, Set<UUID> own) {
        JsonObject result = new JsonObject(); boolean hasFlow = false, ownChoice = false, ownDefense = false, frozen = false;
        String opportunityTarget = null;
        Schema2CombatState.AttackAttempt ownSneakAttack = null;
        var focus = state.space().focus().space();
        for (var history : state.combats()) {
            var battle = history.current(); var flow = battle.activeFlow();
            if (battle.status() == Schema2CombatState.Status.ENDED || flow == null || battle.combatants().stream().noneMatch(c -> own.contains(c.characterId()))) continue;
            var leaf = flow.frames().stream().filter(f -> f.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
            var sneakAttack = CampaignCombatView.pendingSneakAttack(flow, leaf);
            if (sneakAttack != null && !own.contains(sneakAttack.actorId())) continue;
            var window = flow.windows().stream().filter(w -> w.windowKey().equals(leaf.windowKey())).findFirst().orElse(null);
            var opportunity = currentOpportunity(window, flow.leafFrameKey());
            UUID mover = CampaignParticipationCommands.opportunityMonster(battle, window);
            if (mover != null && !canViewMonsterOpportunity(state, member, battle, own, mover, opportunity)) continue;
            if (!CampaignSpaceData.Ref.scene(battle.combatId()).equals(focus)) { frozen = true; continue; }
            hasFlow = true;
            if (sneakAttack != null) ownSneakAttack = sneakAttack;
            ownDefense |= battle.meleeControl().contests().stream().anyMatch(contest -> contest.outcome() == null
                    && contest.frameKey().equals(flow.rootFrameKey()) && own.contains(contest.targetId()));
            if (opportunity != null && own.contains(opportunity.actorId())) {
                ownChoice = true;
                if (mover != null) {
                    var cell = Schema2ObservationRules.knownPosition(battle, opportunity.actorId(), mover);
                    opportunityTarget = monsterName(state, mover) + "（" + cell.x() + ", " + cell.y() + "）将离开你的触及范围。可以借机攻击或放弃。";
                }
            }
        }
        ownChoice |= ownDefense || ownSneakAttack != null;
        if (ownSneakAttack != null) result.add("sneakAttack", sneakAttackHit(ownSneakAttack));
        result.addProperty("active", hasFlow); result.addProperty("own", ownChoice); result.addProperty("frozen", frozen);
        result.addProperty("title", ownChoice ? "等待你的决定" : hasFlow ? "行动尚未结束" : frozen ? "后台行动已冻结" : "当前没有待决选择");
        result.addProperty("description", ownSneakAttack != null ? "本次攻击已命中。请选择是否使用偷袭。"
                : ownDefense ? "选择力量（运动）或敏捷（杂技）进行防守。" : opportunityTarget != null ? opportunityTarget
                : ownChoice ? "选择本次反应，或明确放弃。" : hasFlow ? "请按当前可用选项继续，或等待其他人决定。"
                : frozen ? "主持人切回该空间后可继续原行动。" : "可用行动随当前回合与主持人许可更新。");
        return result;
    }
    private static String actionLabel(String action) { return switch (action) { case "combat-command" -> "行动已记录"; case "character-command", "check-effects", "manual-check" -> "角色操作已记录"; default -> "状态已更新"; }; }

    static boolean canViewOpportunity(CampaignDomainData.State state, UUID member, Schema2CombatState.Battle battle,
            Set<UUID> observers, UUID mover) {
        var flow = battle.activeFlow();
        if (flow == null) return false;
        var leaf = flow.frames().stream().filter(f -> f.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
        var window = flow.windows().stream().filter(w -> w.windowKey().equals(leaf.windowKey())).findFirst().orElse(null);
        return mover.equals(CampaignParticipationCommands.opportunityMonster(battle, window))
                && canViewMonsterOpportunity(state, member, battle, observers, mover, currentOpportunity(window, leaf.frameKey()));
    }

    private static Schema2CombatState.Opportunity currentOpportunity(Schema2CombatState.Window window, UUID leaf) {
        if (window == null || window.status() != Schema2CombatState.WindowStatus.OPEN) return null;
        return window.opportunities().stream().filter(option -> option.status() == Schema2CombatState.OpportunityStatus.PENDING
                && Objects.equals(option.selectionFrameKey(), leaf)).findFirst().orElse(null);
    }

    private static boolean canViewMonsterOpportunity(CampaignDomainData.State state, UUID member,
            Schema2CombatState.Battle battle, Set<UUID> observers, UUID mover, Schema2CombatState.Opportunity opportunity) {
        return opportunity != null && observers.contains(opportunity.actorId())
                && visibleMonsters(state, member, battle.combatId()).contains(mover)
                && Schema2ObservationRules.visible(battle, opportunity.actorId(), mover);
    }
}
