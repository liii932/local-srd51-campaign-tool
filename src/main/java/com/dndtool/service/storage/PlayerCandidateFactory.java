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
    public record ExplorationRoute(int x, int y, int maxSteps, int stepCostFeet, Integer width, Integer height) { }
    public record Candidate(String id, String label, String characterId, String action, JsonObject input,
                            ExplorationRoute route) {
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
        Battle battle = history.current();
        var scene = CampaignSpaceData.Ref.scene(battle.combatId());
        boolean sharedScene = PlayerCampaignProjection.shared(domain, player, CampaignParticipationData.ShareKind.SCENE, scene.id());
        Set<UUID> visibleMonsters = PlayerCampaignProjection.visibleMonsters(domain, player, scene.id());
        Set<UUID> knownPositions = new HashSet<>(own);
        if (sharedScene) knownPositions.addAll(visible);
        knownPositions.addAll(visibleMonsters);
        var roster = CampaignCombatView.roster(domain, battle, material);
        var factory = new BattleChoices(result, domain, battle, roster, sharedScene, knownPositions);
        factory.relationChoices(own, visible);
        if (battle.activeFlow() != null) {
            Flow flow = battle.activeFlow(); Frame leaf = flow.frames().stream().filter(f -> f.frameKey().equals(flow.leafFrameKey())).findFirst().orElseThrow();
            Window window = flow.windows().stream().filter(w -> Objects.equals(w.windowKey(), leaf.windowKey())).findFirst().orElse(null);
            UUID mover = CampaignParticipationCommands.opportunityMonster(battle, window);
            if (mover != null && !PlayerCampaignProjection.canViewOpportunity(domain, player, battle, own, mover))
                return List.copyOf(result);
            if (leaf.kind() == FrameKind.DOMAIN_CHOICE) {
                var contest = battle.meleeControl().contests().stream().filter(c -> leaf.ownerKey().equals(c.choiceKey()))
                        .findFirst().orElseThrow();
                if (!own.contains(contest.targetId())) return List.copyOf(result);
                for (ContestSkill skill : ContestSkill.values()) {
                    factory.add(Command.melee(Kind.CHOOSE_MELEE_DEFENSE, contest.targetId(), null,
                            flow.flowKey(), leaf.frameKey(), new MeleeInput(null, null, null, null, skill, null)),
                            skill == ContestSkill.ATHLETICS ? "用力量（运动）防守" : "用敏捷（杂技）防守");
                }
                return List.copyOf(result);
            }
            if (window != null && window.status() == WindowStatus.OPEN) {
                var opportunity = window.opportunities().stream().filter(o -> o.status() == OpportunityStatus.PENDING && Objects.equals(o.selectionFrameKey(), leaf.frameKey())).findFirst().orElse(null);
                if (opportunity != null && own.contains(opportunity.actorId())
                        && (mover == null || Schema2ObservationRules.visible(battle, opportunity.actorId(), mover))) {
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
                if (sharedScene) {
                    factory.add(Command.action(Kind.HIDE, actor), "躲藏（动作）");
                    factory.add(Command.action(Kind.SEARCH, actor), "搜索（动作）");
                }
                factory.attacks(actor, visible, visibleMonsters);
                factory.attackReplacements(actor, visible);
                factory.moves(actor); factory.spells(actor, visible);
            } else if (battle.status() == Status.EXPLORATION) {
                if (CampaignParticipationCommands.permitted(domain, actor, "EXPLORATION_MOVE")) {
                    factory.add(Command.action(Kind.DROP_PRONE, actor), label(Kind.DROP_PRONE));
                    factory.add(Command.action(Kind.STAND_UP, actor), label(Kind.STAND_UP));
                    factory.route(actor);
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
        add(result, label, actor, action, input, null);
    }
    private static void add(List<Candidate> result, String label, UUID actor, String action, JsonObject input, ExplorationRoute route) {
        if (result.size() >= MAX_CHOICES) return;
        JsonObject identity = new JsonObject(); identity.addProperty("action", action); identity.addProperty("actor", actor.toString()); identity.add("input", input);
        String id = CampaignJson.digest("DND-PLAYER-CANDIDATE", CampaignJson.canonical(identity));
        if (result.stream().noneMatch(c -> c.id().equals(id))) result.add(new Candidate(id, label, actor.toString(), action, input, route));
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
        final boolean sharedScene; final Set<UUID> sharedPositions;
        final Map<UUID, Set<UUID>> visibleOccupants = new HashMap<>();
        final Schema2CombatRules rules = new Schema2CombatRules();
        BattleChoices(List<Candidate> result, CampaignDomainData.State domain, Battle battle,
                Map<UUID, Schema2CombatRules.Context> roster, boolean sharedScene, Set<UUID> knownPositions) {
            this.result = result; this.domain = domain; this.battle = battle; this.roster = roster;
            this.sharedScene = sharedScene; this.sharedPositions = knownPositions;
        }
        private Set<UUID> visibleOccupants(UUID observer) {
            return visibleOccupants.computeIfAbsent(observer, id -> {
                Set<UUID> result = new HashSet<>();
                for (var subject : battle.combatants()) {
                    if (sharedPositions.contains(subject.characterId()) && Schema2ObservationRules.visible(battle, id, subject.characterId()))
                        result.add(subject.characterId());
                }
                return result;
            });
        }
        void add(Command command, String label) {
            if (result.size() >= MAX_CHOICES) return;
            try {
                Set<UUID> knownPositions = visibleOccupants(command.actorId());
                CampaignDomainCommands.preflightGrid(domain, command, sharedScene, knownPositions::contains);
                rules.preflightCandidate(UUID.randomUUID(), roster, battle, command, knownPositions);
            }
            catch (StorageException | IllegalArgumentException unavailable) { return; }
            JsonObject input = combatInput();
            input.add("command", CampaignDomainCodec.toJson(command)); PlayerCandidateFactory.add(result, label, command.actorId(), "combat-command", input);
        }
        private JsonObject combatInput() {
            JsonObject input = new JsonObject();
            input.addProperty("combatId", battle.combatId().toString());
            input.addProperty("combatVersion", Long.toString(battle.version()));
            input.add("focus", CampaignSpaceCommands.focusPayload(domain));
            return input;
        }
        void route(UUID actor) {
            Schema2ExplorationRules.MoveBudget budget;
            try { budget = Schema2ExplorationRules.moveBudget(roster, battle, actor); }
            catch (IllegalArgumentException unavailable) { return; }
            if (budget.maxSteps() == 0) return;
            var scene = domain.space().scene(battle.combatId());
            if (sharedScene && scene.width() == 1 && scene.height() == 1) return;
            var route = new ExplorationRoute(budget.start().x(), budget.start().y(), budget.maxSteps(), budget.stepCostFeet(),
                    sharedScene ? scene.width() : null, sharedScene ? scene.height() : null);
            PlayerCandidateFactory.add(result, budget.stepCostFeet() == 10 ? "规划探索匍匐路线" : "规划探索移动路线",
                    actor, "combat-command", combatInput(), route);
        }
        void relationChoices(Set<UUID> own, Set<UUID> visible) {
            int hiddenSources = 0;
            var flow = battle.activeFlow();
            for (var relation : battle.meleeControl().grapples()) {
                if (!relation.active()) continue;
                if (own.contains(relation.grapplerId())) {
                    add(Command.melee(Kind.RELEASE_GRAPPLE, relation.grapplerId(), null,
                            flow == null ? null : flow.flowKey(), flow == null ? null : flow.leafFrameKey(),
                            new MeleeInput(null, null, relation.grappleKey(), null, null, null)),
                            "释放" + locationLabel(relation.hand().name()) + "抓住的目标");
                }
                if (flow == null && own.contains(relation.targetId()) && relation.targetId().equals(battle.activeActor())) {
                    String source = visible.contains(relation.grapplerId())
                            ? PlayerCampaignProjection.characterName(domain, relation.grapplerId())
                            : "未共享的抓者 " + (++hiddenSources);
                    for (ContestSkill skill : ContestSkill.values())
                        add(Command.melee(Kind.ESCAPE_GRAPPLE, relation.targetId(), null, null, null,
                                new MeleeInput(null, null, relation.grappleKey(), null, skill, null)),
                                (skill == ContestSkill.ATHLETICS ? "用力量（运动）挣脱" : "用敏捷（杂技）挣脱") + " · " + source);
                }
            }
        }

        void attacks(UUID actor, Set<UUID> visible, Set<UUID> visibleMonsters) {
            var source = battle.combatants().stream().filter(c -> actor.equals(c.characterId())).findFirst().orElseThrow();
            if (source.sequence() == null || source.sequence().closed()) return;
            var context = roster.get(actor);
            for (var item : context.character().equipment()) {
                if (!Set.of("MAIN_HAND", "OFF_HAND", "BOTH_HANDS").contains(item.location())) continue;
                WeaponAttackDefinition weapon;
                try { weapon = rules.weapon(context, item.itemId()); }
                catch (IllegalArgumentException unavailable) { continue; }
                if (weapon.ranged() && !sharedScene) continue;
                for (var target : battle.combatants()) {
                    UUID id = target.characterId();
                    boolean monster = visibleMonsters.contains(id);
                    if (id.equals(actor) || !monster && !(visible.contains(id) && roster.containsKey(id))) continue;
                    Position aim = Schema2ObservationRules.knownPosition(battle, actor, id);
                    boolean inSight = Schema2ObservationRules.visible(battle, actor, id);
                    if (aim == null || !inSight && !sharedScene) continue;
                    String name = monster ? PlayerCampaignProjection.monsterName(domain, id) : PlayerCampaignProjection.characterName(domain, id);
                    if (monster || !inSight) name += "（" + (inSight ? "" : "最后已知 ") + aim.x() + ", " + aim.y() + "）";
                    String label = (weapon.ranged() ? "射击 " : "攻击 ") + name + " · " + PlayerCampaignProjection.ruleName(domain, item.item());
                    if (weapon.ranged()) {
                        int feet = source.position().distanceFeet(aim);
                        var band = Schema2CombatRules.rangedDistance(feet, weapon.normalRangeFeet(), weapon.longRangeFeet());
                        if (band == Schema2CombatRules.RangedDistance.OUT_OF_RANGE) continue;
                        label += " · " + feet + " 尺（" + (band == Schema2CombatRules.RangedDistance.LONG ? "长射程" : "正常射程") + "）";
                    }
                    add(Command.attack(actor, id, item.itemId()), label);
                }
            }
        }

        void attackReplacements(UUID actor, Set<UUID> visible) {
            var attacker = battle.combatants().stream().filter(c -> actor.equals(c.characterId())).findFirst().orElseThrow();
            var sequence = attacker.sequence();
            if (sequence == null || sequence.closed()) return;
            for (var target : battle.combatants()) {
                UUID id = target.characterId();
                if (id.equals(actor) || !visible.contains(id) || !roster.containsKey(id) || !Schema2ObservationRules.visible(battle, actor, id)
                        || attacker.position().distanceFeet(target.position()) != 5) continue;
                String name = PlayerCampaignProjection.characterName(domain, id);
                for (HandSlot hand : HandSlot.values()) {
                    add(Command.melee(Kind.GRAPPLE, actor, id, null, null, new MeleeInput(sequence.sequenceKey(),
                            sequence.performed(), null, hand, null, null)), locationLabel(hand.name()) + "擒抱 " + name);
                }
                add(Command.melee(Kind.SHOVE_PRONE, actor, id, null, null, new MeleeInput(sequence.sequenceKey(),
                        sequence.performed(), null, null, null, null)), "推倒 " + name);
                Position destination = Schema2MeleeControlRules.pushDestination(attacker.position(), target.position());
                add(Command.melee(Kind.SHOVE_PUSH, actor, id, null, null, new MeleeInput(sequence.sequenceKey(),
                        sequence.performed(), null, null, null, destination)), "推开 " + name);
            }
        }

        void moves(UUID actor) {
            var combatant = battle.combatants().stream().filter(c -> c.characterId().equals(actor)).findFirst().orElse(null); if (combatant == null) return;
            String movement = combatant.prone() ? "匍匐" : "移动";
            for (int i = 0; i < STEPS.length; i++) {
                int x = combatant.position().x() + STEPS[i][0], y = combatant.position().y() + STEPS[i][1];
                if (Math.abs(x) > 10000 || Math.abs(y) > 10000) continue;
                List<Position> path = List.of(new Position(x, y));
                add(Command.move(actor, path), "向" + DIRECTIONS[i] + movement + "一格");
                for (var held : battle.meleeControl().grapples()) {
                    if (held.active() && held.grapplerId().equals(actor)) {
                        var drag = new DragInput(held.grappleKey(), List.of(combatant.position()));
                        add(Command.move(actor, path, drag), "向" + DIRECTIONS[i] + "拖带" + locationLabel(held.hand().name()) + "目标一格");
                    }
                }
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
