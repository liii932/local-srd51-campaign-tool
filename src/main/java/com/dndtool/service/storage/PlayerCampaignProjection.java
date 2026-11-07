package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.service.Schema2CharacterEquipmentRules;
import com.dndtool.service.Schema2CombatState;
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
        for (var c : state.characters()) if (own.contains(c.id())) cards.add(ruleCard(state, c));
        for (var c : state.manual().characters()) if (own.contains(c.id())) cards.add(manualCard(state, c.current()));
        view.add("cards", cards);
        JsonObject shared = new JsonObject(); JsonArray summaries = new JsonArray(), scenes = new JsonArray(), logs = new JsonArray();
        visible.stream().filter(id -> !own.contains(id)).sorted().limit(64).forEach(id -> {
            JsonObject c = new JsonObject(); c.addProperty("id", id.toString()); c.addProperty("name", characterName(state, id));
            c.addProperty("description", "主持人共享的角色"); summaries.add(c);
        });
        for (var history : state.combats()) {
            var battle = history.current();
            if (battle.status() == Schema2CombatState.Status.ENDED || !shared(state, member, ShareKind.SCENE, battle.combatId())) continue;
            JsonObject scene = new JsonObject(); scene.addProperty("name", battle.status() == Schema2CombatState.Status.ACTIVE ? "当前战斗" : "探索场景");
            scene.addProperty("description", "仅显示主持人共享的角色与位置");
            scene.addProperty("turnLabel", battle.status() == Schema2CombatState.Status.ACTIVE
                    ? visible.contains(battle.activeActor()) ? characterName(state, battle.activeActor()) + "的回合" : "等待其他行动者" : "探索中");
            JsonArray participants = new JsonArray();
            List<UUID> order = battle.status() == Schema2CombatState.Status.ACTIVE
                    ? battle.initiative().stream().map(Schema2CombatState.Initiative::characterId).toList()
                    : battle.combatants().stream().map(Schema2CombatState.Combatant::characterId).toList();
            for (UUID id : order) if (visible.contains(id)) {
                var combatant = battle.combatants().stream().filter(c -> c.characterId().equals(id)).findFirst().orElseThrow();
                JsonObject row = new JsonObject(); row.addProperty("name", characterName(state, id));
                row.addProperty("position", combatant.position().x() + ", " + combatant.position().y());
                row.addProperty("current", battle.status() == Schema2CombatState.Status.ACTIVE && id.equals(battle.activeActor())); participants.add(row);
            }
            scene.add("participants", participants); scenes.add(scene);
        }
        for (var node : state.nodes()) if (scenes.size() < 16 && shared(state, member, ShareKind.SCENE, node.id())) {
            JsonObject scene = new JsonObject(); scene.addProperty("name", node.name()); scene.addProperty("description", "主持人共享的地点");
            JsonArray participants = new JsonArray();
            state.positions().stream().filter(p -> p.nodeId().equals(node.id()) && visible.contains(p.characterId())).limit(64).forEach(p -> {
                JsonObject c = new JsonObject(); c.addProperty("name", characterName(state, p.characterId())); c.addProperty("position", node.name()); participants.add(c);
            }); scene.add("participants", participants); scenes.add(scene);
        }
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
        view.add("pending", pending(state, own));
        return view;
    }

    public static JsonObject receipt(CampaignStorageService.AccessSnapshot access, UUID member, CampaignDomainData.Root root) {
        requireMember(access.snapshot().domains(), member);
        if (root.actor().kind() != ActorKind.PLAYER || !member.equals(root.actor().memberId()))
            throw new StorageException("PLAYER_REQUEST_UNAVAILABLE", "Request is unavailable");
        boolean waiting = root.combatId() != null && access.snapshot().domains().combats().stream()
                .filter(c -> c.current().combatId().equals(root.combatId())).flatMap(c -> c.events().stream())
                .filter(e -> e.operationId().equals(root.operationId())).anyMatch(e -> e.after().activeFlow() != null);
        JsonObject result = new JsonObject(); result.addProperty("state", "ACCEPTED");
        result.addProperty("outcome", waiting ? "WAITING" : "COMPLETED");
        result.addProperty("label", waiting ? "操作已接受，当时正在等待后续决定" : "操作已完成");
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
        return state.participation().shares().stream().anyMatch(s -> s.kind() == kind && s.active() && s.subjectId().equals(subject) && s.audience().includes(member));
    }
    static String characterName(CampaignDomainData.State state, UUID id) {
        return state.characters().stream().filter(c -> c.id().equals(id)).map(CampaignDomainData.Character::name).findFirst()
                .orElseGet(() -> state.manual().characters().stream().filter(c -> c.id().equals(id)).map(c -> c.current().name()).findFirst().orElse("角色"));
    }
    static String ruleName(CampaignDomainData.State state, RuleIdentity rule) {
        return state.material().rules().stream().filter(r -> r.identity().equals(rule)).map(r -> r.definition().displayName()).findFirst().orElse("未知规则");
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
    private static JsonObject ruleCard(CampaignDomainData.State domain, CampaignDomainData.Character c) {
        var state = c.history().current(); JsonObject card = base(c.id(), c.name(), c.kind(), state.hitPoints(), state.maximumHitPoints(), state.level());
        card.addProperty("className", ruleName(domain, state.characterClass())); card.addProperty("proficiencyBonus", state.proficiencyBonus());
        card.addProperty("armorClass", new Schema2CharacterEquipmentRules().derive(domain.material().restore(), state).armorClass());
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
        JsonArray features = new JsonArray(); state.features().forEach(f -> features.add(ruleName(domain, f.feature()))); state.feats().forEach(f -> features.add(ruleName(domain, f.feat()))); card.add("features", features);
        if (state.spellbook() != null) {
            JsonObject book = new JsonObject(); JsonArray learned = new JsonArray(), prepared = new JsonArray();
            state.spellbook().learned().forEach(s -> learned.add(ruleName(domain, s.spell()))); state.spellbook().prepared().forEach(s -> prepared.add(ruleName(domain, s)));
            book.add("learned", learned); book.add("prepared", prepared); card.add("spellbook", book);
        }
        JsonArray effects = new JsonArray();
        for (var history : domain.combats()) if (history.current().status() != Schema2CombatState.Status.ENDED) {
            var battle = history.current();
            battle.combatants().stream().filter(p -> p.characterId().equals(c.id())).forEach(p -> { if (p.prone()) effect(effects, "倒地"); if (p.dodging()) effect(effects, "闪避"); });
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
    private static JsonObject pending(CampaignDomainData.State state, Set<UUID> own) {
        JsonObject result = new JsonObject(); boolean hasFlow = false, ownChoice = false;
        for (var history : state.combats()) {
            var battle = history.current(); var flow = battle.activeFlow();
            if (battle.status() == Schema2CombatState.Status.ENDED || flow == null || battle.combatants().stream().noneMatch(c -> own.contains(c.characterId()))) continue;
            hasFlow = true;
            ownChoice |= flow.windows().stream().filter(w -> w.status() == Schema2CombatState.WindowStatus.OPEN)
                    .flatMap(w -> w.opportunities().stream()).anyMatch(o -> o.status() == Schema2CombatState.OpportunityStatus.PENDING && Objects.equals(o.selectionFrameKey(), flow.leafFrameKey()) && own.contains(o.actorId()));
        }
        result.addProperty("active", hasFlow); result.addProperty("own", ownChoice); result.addProperty("title", ownChoice ? "等待你的决定" : hasFlow ? "行动尚未结束" : "当前没有待决选择");
        result.addProperty("description", ownChoice ? "选择本次反应，或明确放弃。" : hasFlow ? "按当前可用选项继续；需要他人决定时请等待。" : "可用行动随当前回合与主持人许可更新。");
        return result;
    }
    private static String actionLabel(String action) { return switch (action) { case "combat-command" -> "行动已记录"; case "character-command", "check-effects", "manual-check" -> "角色操作已记录"; default -> "状态已更新"; }; }
}
