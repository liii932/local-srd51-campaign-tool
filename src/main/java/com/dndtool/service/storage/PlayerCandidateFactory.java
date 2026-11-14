package com.dndtool.service.storage;

import com.dndtool.service.Schema2CharacterRuntime;

import com.dndtool.module.composition.ProductionSchema2Projection;
import com.dndtool.module.composition.Schema2CompositionKernel.*;
import com.dndtool.service.*;
import com.google.gson.*;
import java.util.*;
import static com.dndtool.service.Schema2CombatState.*;

/** Finite server-authored choices. Candidate preparation never consumes game randomness. */
public final class PlayerCandidateFactory {
    public record Candidate(String id, String label, String characterId, String action, JsonObject input) {
        public Candidate { input = input.deepCopy(); }
        @Override public JsonObject input() { return input.deepCopy(); }
    }
    private static final int MAX_CHOICES = 1024;
    private static final List<Kind> TURN_ACTIONS = List.of(Kind.END_TURN, Kind.BEGIN_ATTACK, Kind.FORFEIT_ATTACKS,
            Kind.DASH, Kind.DISENGAGE, Kind.DODGE, Kind.DROP_PRONE, Kind.STAND_UP, Kind.SECOND_WIND, Kind.END_CONCENTRATION);
    private static final String[] DIRECTIONS = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
    private static final int[][] STEPS = {{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};

    public static List<Candidate> candidates(CampaignStorageService.AccessSnapshot access, UUID player) {
        var domain = access.snapshot().domains(); PlayerCampaignProjection.requireMember(domain, player);
        Set<UUID> own = new LinkedHashSet<>(PlayerCampaignProjection.owned(domain, player));
        own.removeIf(id -> !CampaignSpaceCommands.focused(domain, id));
        if (own.isEmpty()) return List.of();
        Set<UUID> visible = PlayerCampaignProjection.visible(domain, player);
        var material = domain.material().restore().projection(); List<Candidate> result = new ArrayList<>();
        var history = CampaignSpaceCommands.currentBattle(domain);
        if (history == null) {
            if (CampaignSpaceCommands.isWorldFocus(domain)) for (var character : domain.characters())
                if (own.contains(character.id()) && CampaignParticipationCommands.permitted(domain, character.id(), "CHARACTER_COMMAND"))
                    characterChoices(result, domain, material, character);
            return List.copyOf(result);
        }
        Battle battle = history.current(); Map<UUID, Schema2CombatRules.Context> roster = new LinkedHashMap<>();
        var scene = CampaignSpaceData.Ref.scene(battle.combatId());
        long sceneSecond = domain.space().clock(scene).second();
        Set<UUID> combatants = new HashSet<>(battle.combatants().stream().map(Combatant::characterId).toList());
        domain.characters().stream()
                .filter(c -> combatants.contains(c.id()) && CampaignSpaceCommands.focused(domain, c.id()))
                .forEach(c -> roster.put(c.id(), new Schema2CombatRules.Context(material, c.initial(), c.history().current(), sceneSecond)));
        var factory = new BattleChoices(result, domain, battle, roster);
        if (battle.activeFlow() != null) {
            Flow flow = battle.activeFlow(); Frame leaf = flow.frames().stream().filter(f -> f.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
            Window window = flow.windows().stream().filter(w -> Objects.equals(w.windowKey(), leaf.windowKey())).findFirst().orElse(null);
            if (window != null && window.status() == WindowStatus.OPEN) {
                var opportunity = window.opportunities().stream().filter(o -> o.status() == OpportunityStatus.PENDING && Objects.equals(o.selectionFrameKey(), leaf.frameKey())).findFirst().orElse(null);
                if (opportunity != null && own.contains(opportunity.actorId())) {
                    UUID actor = opportunity.actorId();
                    factory.add(Command.reaction(Kind.PASS_REACTION, actor, null, flow.flowKey(), leaf.frameKey(), window.windowKey(), opportunity.opportunityKey()), "放弃本次反应");
                    factory.add(Command.reaction(Kind.ADVANCE_WINDOW, actor, null, flow.flowKey(), leaf.frameKey(), window.windowKey(), opportunity.opportunityKey()), "确认本次机会已不可用");
                    for (ReactionOption option : opportunity.options()) switch (option.kind()) {
                        case OPPORTUNITY_ATTACK -> factory.add(Command.reaction(Kind.DECLARE_OPPORTUNITY_ATTACK, actor, option.itemId(), flow.flowKey(), leaf.frameKey(), window.windowKey(), opportunity.opportunityKey()), "借机攻击");
                        case READY_RELEASE -> factory.add(Command.releaseReady(actor, option.readyKey(), flow.flowKey(), leaf.frameKey(), window.windowKey(), opportunity.opportunityKey()), "释放准备动作");
                        case SHIELD, COUNTERSPELL -> {
                            boolean counter = option.kind() == ReactionKind.COUNTERSPELL;
                            var cast = new CastInput(option.castingRule(), option.spell(), List.of(actor), null, option.slotLevel(), counter ? window.trigger().sourceFrameKey() : null);
                            factory.add(Command.reactionSpell(counter ? Kind.DECLARE_COUNTERSPELL : Kind.DECLARE_SHIELD, actor, cast, flow.flowKey(), leaf.frameKey(), window.windowKey(), opportunity.opportunityKey()),
                                    PlayerCampaignProjection.ruleName(domain, option.spell()) + "（" + option.slotLevel() + "环）");
                        }
                    }
                }
            }
            for (UUID actor : own) {
                factory.add(Command.reaction(Kind.CONTINUE_FLOW, actor, null, flow.flowKey(), leaf.frameKey(), leaf.windowKey(), null), "继续结算当前行动");
                factory.add(Command.continuation(actor, flow.flowKey(), leaf.frameKey(), leaf.windowKey()), "继续本次移动");
            }
            return List.copyOf(result);
        }
        for (UUID actor : own) {
            if (!roster.containsKey(actor)) continue;
            if (battle.status() == Status.ACTIVE && actor.equals(battle.activeActor())) {
                TURN_ACTIONS.forEach(kind -> factory.add(Command.action(kind, actor), label(kind)));
                for (UUID target : visible) if (!target.equals(actor) && roster.containsKey(target))
                    for (var item : roster.get(actor).character().equipment())
                        factory.add(Command.attack(actor, target, item.itemId()), "攻击 " + PlayerCampaignProjection.characterName(domain, target) + " · " + PlayerCampaignProjection.ruleName(domain, item.item()));
                factory.moves(actor, false); factory.spells(actor, visible);
            } else if (battle.status() == Status.EXPLORATION) {
                if (CampaignParticipationCommands.permitted(domain, actor, "EXPLORATION_MOVE")) {
                    factory.add(Command.action(Kind.DROP_PRONE, actor), label(Kind.DROP_PRONE));
                    factory.add(Command.action(Kind.STAND_UP, actor), label(Kind.STAND_UP));
                    factory.moves(actor, true);
                }
                if (CampaignParticipationCommands.permitted(domain, actor, "END_CONCENTRATION")) factory.add(Command.action(Kind.END_CONCENTRATION, actor), "结束专注");
                // Spell use in exploration must be explicitly granted for this exact scene.
                if (CampaignParticipationCommands.permitted(domain, actor, "CHARACTER_COMMAND")) factory.spells(actor, visible);
            }
        }
        return List.copyOf(result);
    }

    private static void characterChoices(List<Candidate> result, CampaignDomainData.State domain, ProductionSchema2Projection material, CampaignDomainData.Character character) {
        var state = character.history().current();
        for (var item : state.equipment()) {
            if (item.location().equals("GROUND")) continue;
            List<String> locations = item.location().equals("CARRIED") ? List.of("MAIN_HAND", "OFF_HAND", "BOTH_HANDS", "WORN") : List.of("CARRIED");
            for (String location : locations) {
                boolean unequip = location.equals("CARRIED");
                var command = new Schema2CharacterRuntime.Command(unequip ? "ITEM_UNEQUIPPED" : "ITEM_EQUIPPED", null, 0, null, null, null,
                        Schema2CharacterRuntime.GrowthChoices.EMPTY, null, null,
                        new Schema2CharacterRuntime.EquipmentInput(item.itemId(), null, unequip ? null : location, "玩家调整自己的装备"), null);
                try { new Schema2CharacterEquipmentRules().apply(material, state, command, UUID.randomUUID()); }
                catch (IllegalArgumentException unavailable) { continue; }
                addCharacter(result, domain, character, command, (unequip ? "卸下 " : "装备 ") + PlayerCampaignProjection.ruleName(domain, item.item()) + (unequip ? "" : " · " + locationLabel(location)));
            }
        }
        for (var resource : state.resources()) {
            try { new Schema2CharacterProgressionRules().spendOrdinary(material, state, resource.identity(), 1); }
            catch (IllegalArgumentException unavailable) { continue; }
            addCharacter(result, domain, character, new Schema2CharacterRuntime.Command("RESOURCE_SPENT", resource.identity(), 1, null, null),
                    "使用 1 次 " + PlayerCampaignProjection.ruleName(domain, resource.identity()));
        }
    }
    private static void addCharacter(List<Candidate> result, CampaignDomainData.State domain, CampaignDomainData.Character character, Schema2CharacterRuntime.Command command, String label) {
        JsonObject input = new JsonObject(); input.addProperty("characterId", character.id().toString()); input.addProperty("characterVersion", Long.toString(character.history().current().version()));
        input.add("focus", CampaignSpaceCommands.focusPayload(domain));
        input.add("command", CampaignDomainCodec.toJson(command)); add(result, label, character.id(), "character-command", input);
    }
    private static void add(List<Candidate> result, String label, UUID actor, String action, JsonObject input) {
        if (result.size() >= MAX_CHOICES) return;
        JsonObject identity = new JsonObject(); identity.addProperty("action", action); identity.add("input", input);
        String id = CampaignJson.digest("DND-PLAYER-CANDIDATE", CampaignJson.canonical(identity));
        if (result.stream().noneMatch(c -> c.id().equals(id))) result.add(new Candidate(id, label, actor.toString(), action, input));
    }
    private static String locationLabel(String value) { return switch (value) { case "MAIN_HAND" -> "主手"; case "OFF_HAND" -> "副手"; case "BOTH_HANDS" -> "双手"; default -> "穿戴"; }; }
    private static String label(Kind kind) { return switch (kind) {
        case END_TURN -> "结束我的回合"; case BEGIN_ATTACK -> "开始攻击动作"; case FORFEIT_ATTACKS -> "放弃剩余攻击";
        case DASH -> "疾走"; case DISENGAGE -> "撤离"; case DODGE -> "闪避";
        case DROP_PRONE -> "主动卧倒"; case STAND_UP -> "起身";
        case SECOND_WIND -> "回气（附赠动作）"; case END_CONCENTRATION -> "结束专注"; default -> "继续行动";
    }; }
    private static final class BattleChoices {
        final List<Candidate> result; final CampaignDomainData.State domain; final Battle battle; final Map<UUID, Schema2CombatRules.Context> roster;
        final Schema2CombatRules rules = new Schema2CombatRules();
        BattleChoices(List<Candidate> result, CampaignDomainData.State domain, Battle battle, Map<UUID, Schema2CombatRules.Context> roster) {
            this.result = result; this.domain = domain; this.battle = battle; this.roster = roster;
        }
        void add(Command command, String label) {
            if (result.size() >= MAX_CHOICES) return;
            try {
                CampaignDomainCommands.preflightGrid(domain, command);
                rules.preflight(UUID.randomUUID(), battle.combatId(), battle.campaignId(), roster, battle, command);
            }
            catch (StorageException | IllegalArgumentException unavailable) { return; }
            JsonObject input = new JsonObject(); input.addProperty("combatId", battle.combatId().toString()); input.addProperty("combatVersion", Long.toString(battle.version()));
            input.add("focus", CampaignSpaceCommands.focusPayload(domain));
            input.add("command", CampaignDomainCodec.toJson(command)); PlayerCandidateFactory.add(result, label, command.actorId(), "combat-command", input);
        }
        void moves(UUID actor, boolean exploration) {
            var combatant = battle.combatants().stream().filter(c -> c.characterId().equals(actor)).findFirst().orElse(null); if (combatant == null) return;
            String movement = combatant.prone() ? "匍匐" : "移动";
            for (int i = 0; i < STEPS.length; i++) {
                int x = combatant.position().x() + STEPS[i][0], y = combatant.position().y() + STEPS[i][1];
                if (Math.abs(x) > 10000 || Math.abs(y) > 10000) continue;
                List<Position> path = List.of(new Position(x, y));
                add(exploration ? Command.exploreMove(actor, path) : Command.move(actor, path), "向" + DIRECTIONS[i] + movement + "一格");
            }
        }
        void spells(UUID actor, Set<UUID> visible) {
            var state = roster.get(actor).character();
            List<UUID> targets = visible.stream().filter(roster::containsKey).sorted().toList();
            for (var rule : domain.material().rules()) {
                if (!rule.enabled()) continue;
                if (rule instanceof FinalSpellcasting casting && state.classes().stream().anyMatch(c -> c.characterClass().equals(casting.definition().ownerClass()))) {
                    for (UUID target : targets) add(Command.cast(actor, new CastInput(casting.identity(), casting.definition().cureWounds(), List.of(target), null)), PlayerCampaignProjection.ruleName(domain, casting.definition().cureWounds()) + "：" + PlayerCampaignProjection.characterName(domain, target));
                    for (var item : state.equipment()) if (item.item().equals(casting.definition().materialItem())) {
                        for (int a = 0; a < targets.size(); a++) {
                            bless(actor, casting, item.itemId(), List.of(targets.get(a)));
                            for (int b = a + 1; b < targets.size(); b++) {
                                bless(actor, casting, item.itemId(), List.of(targets.get(a), targets.get(b)));
                                for (int c = b + 1; c < targets.size(); c++) bless(actor, casting, item.itemId(), List.of(targets.get(a), targets.get(b), targets.get(c)));
                            }
                        }
                    }
                } else if (rule instanceof FinalWizardSpellcasting casting && state.spellbook() != null && state.spellbook().castingRule().equals(casting.identity())) {
                    for (RuleIdentity spell : state.spellbook().prepared()) {
                        var definition = domain.material().rules().stream().filter(r -> r.identity().equals(spell) && r instanceof FinalSpell).map(r -> (FinalSpell) r).findFirst().orElseThrow().definition();
                        if (!definition.algorithm().equals(Schema2SpellRules.BURNING_HANDS)) continue;
                        for (int level = 1; level <= 3; level++) for (int direction = 0; direction < 8; direction++)
                            add(Command.cast(actor, new CastInput(casting.identity(), spell, List.of(), null, level, null, direction)), definition.displayName() + " · 方向 " + direction + " · " + level + "环");
                    }
                }
            }
        }
        void bless(UUID actor, FinalSpellcasting casting, UUID material, List<UUID> targets) {
            add(Command.cast(actor, new CastInput(casting.identity(), casting.definition().bless(), targets, material)),
                    PlayerCampaignProjection.ruleName(domain, casting.definition().bless()) + "：" + String.join("、", targets.stream().map(id -> PlayerCampaignProjection.characterName(domain, id)).toList()));
        }
    }
}
