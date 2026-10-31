package com.dndtool.persistence;

import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import com.dndtool.persistence.JdbcSchema2RuntimeSnapshotRepository.Identity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.sql.*;
import java.util.*;

/** Append-only, typed character execution facts. The caller owns the guarded transaction. */
public final class JdbcSchema2CharacterRuntimeRepository {
    public static final int MAX_EVENTS = 128, MAX_RESOURCES = 32;
    public static final List<String> ABILITIES = List.of("ability.strength", "ability.dexterity", "ability.constitution", "ability.intelligence", "ability.wisdom", "ability.charisma");
    private static final Set<String> KINDS = Set.of("INITIALIZED", "CHARACTER_ADVANCED", "STATISTICS_ADVANCED", "RESOURCE_SPENT", "RESOURCES_RECOVERED", "REST_STARTED", "TIME_ADVANCED", "REST_COMPLETED", "HIT_DIE_SPENT", "REST_ENDED", "CHECK_RESOLVED", "ITEM_ACQUIRED", "ITEM_REMOVED", "ITEM_EQUIPPED", "ITEM_UNEQUIPPED", "HEALTH_EFFECT_RESOLVED", "DEATH_SAVE_RESOLVED", "COMBAT_DAMAGE_APPLIED", "COMBAT_DEATH_SAVE_RESOLVED", "COMBAT_TIME_ADVANCED", "COMBAT_RESOURCE_SPENT", "COMBAT_HEALING_APPLIED", "COMBAT_ENVIRONMENT_HEALTH", "SPELLBOOK_INITIALIZED", "SPELLBOOK_ADVANCED", "SPELLBOOK_PREPARED");
    private static final Comparator<RuleIdentity> RULE_ORDER = Comparator.comparing(RuleIdentity::moduleKey).thenComparing(RuleIdentity::ruleType).thenComparing(RuleIdentity::ruleKey);
    private static final Comparator<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity> SLOT_ORDER = Comparator.comparing(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity::rule, RULE_ORDER).thenComparing(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity::collectionKey).thenComparing(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity::moduleKey).thenComparing(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity::entryKey);
    private static final Comparator<ResourceState> RESOURCE_ORDER = Comparator.comparing((ResourceState r) -> r.identity().moduleKey()).thenComparing(r -> r.identity().ruleType()).thenComparing(r -> r.identity().ruleKey());

    public static final int MAX_CLASSES = 20, MAX_HIT_DICE_POOLS = 4, MAX_FEATURES = 64, MAX_FEATS = 20, MAX_GRANTS = 64;
    public static final int MAX_HISTORY_CHILD_ROWS = 32768;
    public static final int MAX_EQUIPMENT = 32, MAX_HEALTH_DICE = 20;
    private static final Set<String> EQUIPMENT_LOCATIONS = Set.of("CARRIED", "WORN", "MAIN_HAND", "OFF_HAND", "BOTH_HANDS");
    public record EquipmentItem(UUID itemId, RuleIdentity item, String location, UUID groundCombatId, Integer groundX, Integer groundY) {
        public EquipmentItem(UUID itemId,RuleIdentity item,String location) { this(itemId,item,location,null,null,null); }
        public EquipmentItem {
            if (!validUuid(itemId) || !validRule(item, "equipment.item") || location == null || !(EQUIPMENT_LOCATIONS.contains(location)||location.equals("GROUND")))
                throw new IllegalArgumentException("S2_CHARACTER_EQUIPMENT_ITEM");
            if (location.equals("GROUND") ? !validUuid(groundCombatId) || groundX==null || groundY==null
                    || Math.abs((long)groundX)>10000 || Math.abs((long)groundY)>10000
                    : groundCombatId!=null || groundX!=null || groundY!=null) throw new IllegalArgumentException("S2_CHARACTER_EQUIPMENT_ITEM");
        }
    }
    public record HealthState(int temporaryHitPoints, int deathSuccesses, int deathFailures, boolean stable, boolean dead, Long stableRecoveryMinute, Integer stableRecoverySecond) {
        public HealthState(int temporaryHitPoints,int deathSuccesses,int deathFailures,boolean stable,boolean dead,Long stableRecoveryMinute) {
            this(temporaryHitPoints,deathSuccesses,deathFailures,stable,dead,stableRecoveryMinute,stableRecoveryMinute==null?null:0);
        }
        public static final HealthState INITIAL = new HealthState(0, 0, 0, false, false, null);
        public HealthState {
            if ((stableRecoveryMinute==null)!=(stableRecoverySecond==null) || stableRecoverySecond!=null&&(stableRecoverySecond<0||stableRecoverySecond>59)
                    || temporaryHitPoints < 0 || temporaryHitPoints > 65535 || deathSuccesses < 0 || deathSuccesses > 2 || deathFailures < 0 || deathFailures > 2
                    || stableRecoveryMinute != null && (stableRecoveryMinute < 1 || stableRecoveryMinute > 1000000000L))
                throw new IllegalArgumentException("S2_CHARACTER_HEALTH_STATE");
        }
    }
    public record ResourceState(RuleIdentity identity, long current, long maximum, boolean unlimited) { }
    public record ClassLevel(RuleIdentity characterClass, int level) { }
    public record HitDicePool(int sides, int current, int maximum) { }
    public record SubclassState(RuleIdentity characterClass, RuleIdentity subclass, int acquiredAtClassLevel) { }
    public record FeatureState(RuleIdentity feature, RuleIdentity owner, int acquiredAtClassLevel) { }
    public record FeatState(RuleIdentity feat, RuleIdentity characterClass, int acquiredAtClassLevel) { }
    public record ProficiencyState(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity sourceSlot, RuleIdentity target, int acquiredAtTotalLevel) { }
    public record RestAttempt(RuleIdentity rule, String kind, long startMinute, int hitPointsAtStart,
            long sleepMinutes, long lightMinutes, long strenuousMinutes, String phase, String lastActivity, long consecutiveStrenuousMinutes, boolean interrupted) { }
    public record RestState(long minute, int second, Long lastLongRestEndMinute, Integer lastLongRestEndSecond, RestAttempt active) {
        public RestState(long minute,Long lastLongRestEndMinute,RestAttempt active) { this(minute,0,lastLongRestEndMinute,lastLongRestEndMinute==null?null:0,active); }
        public RestState {
            if(second<0||second>59||(lastLongRestEndMinute==null)!=(lastLongRestEndSecond==null)
                    ||lastLongRestEndSecond!=null&&(lastLongRestEndSecond<0||lastLongRestEndSecond>59)) throw new IllegalArgumentException("S2_CHARACTER_TIME");
        }
        public static final RestState INITIAL = new RestState(0, null, null);
    }
    public record RestInput(String restKind, String activity, long minutes, int hitDieSides,
            Map<Integer, Integer> recoveredHitDice, Map<Integer, Integer> recoveredSpellSlots) {
        public RestInput(String restKind, String activity, long minutes, int hitDieSides, Map<Integer, Integer> recoveredHitDice) {
            this(restKind, activity, minutes, hitDieSides, recoveredHitDice, Map.of());
        }
        public RestInput {
            boolean valid = (restKind == null || restKind.equals("SHORT_REST") || restKind.equals("LONG_REST"))
                    && (activity == null || Set.of("SLEEP", "LIGHT", "STRENUOUS", "NONE").contains(activity))
                    && minutes >= 0 && minutes <= 1440 && (hitDieSides == 0 || Set.of(6, 8, 10, 12).contains(hitDieSides)) && recoveredHitDice != null && recoveredHitDice.size() <= 4;
            var recovered = new TreeMap<Integer, Integer>(); int total = 0;
            if (valid) for (var entry : recoveredHitDice.entrySet()) {
                if (entry.getKey() == null || !Set.of(6, 8, 10, 12).contains(entry.getKey()) || entry.getValue() == null || entry.getValue() < 1 || entry.getValue() > 20) { valid = false; break; }
                recovered.put(entry.getKey(), entry.getValue()); total += entry.getValue();
            }
            if (!valid || total > 20) throw new IllegalArgumentException("S2_CHARACTER_REST_INPUT");
            recoveredHitDice = Collections.unmodifiableMap(recovered);
            if (recoveredSpellSlots == null || recoveredSpellSlots.size() > 5)
                throw new IllegalArgumentException("S2_ARCANE_RECOVERY_CHOICE");
            var slots = new TreeMap<Integer, Integer>();
            for (var entry : recoveredSpellSlots.entrySet()) {
                if (entry.getKey() == null || entry.getKey() < 1 || entry.getKey() > 5
                        || entry.getValue() == null || entry.getValue() < 1 || entry.getValue() > 4)
                    throw new IllegalArgumentException("S2_ARCANE_RECOVERY_CHOICE");
                slots.put(entry.getKey(), entry.getValue());
            }
            recoveredSpellSlots = Collections.unmodifiableMap(slots);
        }
    }
    public record LearnedSpell(RuleIdentity spell, int acquiredAtClassLevel) {
        public LearnedSpell { if (!validRule(spell,"adventure.spell") || acquiredAtClassLevel<1 || acquiredAtClassLevel>5) throw new IllegalArgumentException("S2_SPELLBOOK_STATE"); }
    }
    public record SpellbookState(RuleIdentity castingRule, int learnedThroughLevel, List<LearnedSpell> learned,
            List<RuleIdentity> prepared, Long preparedAtRestMinute, Integer preparedAtRestSecond, Long lastArcaneRecoveryDay) {
        public SpellbookState(RuleIdentity castingRule, int learnedThroughLevel, List<LearnedSpell> learned,
                List<RuleIdentity> prepared, Long preparedAtRestMinute, Integer preparedAtRestSecond) {
            this(castingRule, learnedThroughLevel, learned, prepared, preparedAtRestMinute, preparedAtRestSecond, null);
        }
        public SpellbookState {
            if (!validRule(castingRule,"character.wizard_spellcasting") || learnedThroughLevel<1 || learnedThroughLevel>5
                    || learned==null || learned.size()>14 || learned.stream().anyMatch(Objects::isNull)
                    || prepared==null || prepared.size()>15 || prepared.stream().anyMatch(r->!validRule(r,"adventure.spell"))
                    || (preparedAtRestMinute==null)!=(preparedAtRestSecond==null)
                    || preparedAtRestMinute!=null&&(preparedAtRestMinute<0 || preparedAtRestSecond<0 || preparedAtRestSecond>59)
                    || lastArcaneRecoveryDay!=null&&(lastArcaneRecoveryDay<0 || lastArcaneRecoveryDay>1_000_000_000L/1440))
                throw new IllegalArgumentException("S2_SPELLBOOK_STATE");
            learned=List.copyOf(learned); prepared=List.copyOf(prepared);
            if (learned.size()!=4+2*learnedThroughLevel || new HashSet<>(learned.stream().map(LearnedSpell::spell).toList()).size()!=learned.size()
                    || new HashSet<>(prepared).size()!=prepared.size() || prepared.stream().anyMatch(r->!validRule(r,"adventure.spell")))
                throw new IllegalArgumentException("S2_SPELLBOOK_STATE");
        }
    }
    public record SpellbookInput(RuleIdentity castingRule,List<RuleIdentity> learned,List<RuleIdentity> prepared) {
        public SpellbookInput {
            if(!validRule(castingRule,"character.wizard_spellcasting") || learned==null || prepared==null || learned.size()>6 || prepared.size()>15
                    || learned.stream().anyMatch(r->!validRule(r,"adventure.spell")) || prepared.stream().anyMatch(r->!validRule(r,"adventure.spell")))
                throw new IllegalArgumentException("S2_SPELLBOOK_COMMAND");
            learned=learned.stream().sorted(RULE_ORDER).toList(); prepared=prepared.stream().sorted(RULE_ORDER).toList();
            if(new HashSet<>(learned).size()!=learned.size() || new HashSet<>(prepared).size()!=prepared.size()
                    || learned.stream().anyMatch(r->!validRule(r,"adventure.spell")) || prepared.stream().anyMatch(r->!validRule(r,"adventure.spell")))
                throw new IllegalArgumentException("S2_SPELLBOOK_COMMAND");
        }
    }
    public record State(Identity identity, UUID campaignId, UUID characterId, UUID preparationOperationId,
            RuleIdentity characterClass, Map<String, Integer> abilities, int level, int proficiencyBonus,
            long hitPoints, long maximumHitPoints, List<ClassLevel> classes, List<HitDicePool> hitDicePools,
            List<SubclassState> subclasses, List<FeatureState> features, List<FeatState> feats,
            List<ProficiencyState> proficiencies, long version, List<ResourceState> resources, RestState rest,
            List<EquipmentItem> equipment, HealthState health, SpellbookState spellbook) {
        public State(Identity identity, UUID campaignId, UUID characterId, UUID preparationOperationId, RuleIdentity characterClass,
                Map<String,Integer> abilities,int level,int proficiencyBonus,long hitPoints,long maximumHitPoints,
                List<ClassLevel> classes,List<HitDicePool> hitDicePools,List<SubclassState> subclasses,List<FeatureState> features,
                List<FeatState> feats,List<ProficiencyState> proficiencies,long version,List<ResourceState> resources,RestState rest,
                List<EquipmentItem> equipment,HealthState health) {
            this(identity,campaignId,characterId,preparationOperationId,characterClass,abilities,level,proficiencyBonus,hitPoints,maximumHitPoints,
                classes,hitDicePools,subclasses,features,feats,proficiencies,version,resources,rest,equipment,health,null);
        }
        public State(Identity identity, UUID campaignId, UUID characterId, UUID preparationOperationId, RuleIdentity characterClass,
                Map<String, Integer> abilities, int level, int proficiencyBonus, long hitPoints, long maximumHitPoints,
                List<ClassLevel> classes, List<HitDicePool> hitDicePools, List<SubclassState> subclasses, List<FeatureState> features,
                List<FeatState> feats, List<ProficiencyState> proficiencies, long version, List<ResourceState> resources, RestState rest) {
            this(identity, campaignId, characterId, preparationOperationId, characterClass, abilities, level, proficiencyBonus, hitPoints, maximumHitPoints,
                    classes, hitDicePools, subclasses, features, feats, proficiencies, version, resources, rest, List.of(), HealthState.INITIAL);
        }
        public State {
            abilities = Map.copyOf(abilities); classes = List.copyOf(classes); hitDicePools = List.copyOf(hitDicePools);
            subclasses = List.copyOf(subclasses); features = List.copyOf(features); feats = List.copyOf(feats);
            proficiencies = List.copyOf(proficiencies); resources = List.copyOf(resources); equipment = List.copyOf(equipment);
        }
        public int hitDice() { return hitDicePools.stream().mapToInt(HitDicePool::current).sum(); }
        public int maximumHitDice() { return hitDicePools.stream().mapToInt(HitDicePool::maximum).sum(); }
    }
    public record GrowthChoices(RuleIdentity subclass, Map<String, Integer> abilityIncreases, RuleIdentity feat,
            Map<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity, List<RuleIdentity>> proficiencyChoices) {
        public static final GrowthChoices EMPTY = new GrowthChoices(null, Map.of(), null, Map.of());
        public GrowthChoices {
            if (abilityIncreases == null || proficiencyChoices == null || subclass != null && !validRule(subclass, "character.subclass")
                    || feat != null && !validRule(feat, "character.feat") || abilityIncreases.size() > 2 || proficiencyChoices.size() > MAX_GRANTS)
                throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
            var increases = new TreeMap<String, Integer>(); int total = 0;
            for (var entry : abilityIncreases.entrySet()) {
                if (entry.getKey() == null || !ABILITIES.contains(entry.getKey()) || entry.getValue() == null || entry.getValue() < 1 || entry.getValue() > 2)
                    throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
                increases.put(entry.getKey(), entry.getValue()); total += entry.getValue();
            }
            if (total != 0 && total != 2 || feat != null && total != 0) throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
            abilityIncreases = Collections.unmodifiableMap(increases);
            var choices = new TreeMap<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity, List<RuleIdentity>>(SLOT_ORDER);
            for (var entry : proficiencyChoices.entrySet()) {
                if (!validSlot(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty() || entry.getValue().size() > 16)
                    throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
                List<RuleIdentity> targets = new ArrayList<>(entry.getValue());
                for (RuleIdentity target : targets) if (!validProficiency(target)) throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
                targets.sort(RULE_ORDER); if (new HashSet<>(targets).size() != targets.size()) throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
                choices.put(entry.getKey(), List.copyOf(targets));
            }
            if (choices.values().stream().mapToInt(List::size).sum() > MAX_GRANTS) throw new IllegalArgumentException("S2_CHARACTER_CHOICES");
            proficiencyChoices = Collections.unmodifiableMap(choices);
        }
    }
    public record CheckInput(RuleIdentity rule, String kind, String abilityKey, RuleIdentity proficiency,
            int difficultyClass, boolean advantage, boolean disadvantage, String adjudication) {
        public CheckInput {
            boolean valid = validRule(rule, "adventure.check") && kind != null && abilityKey != null && ABILITIES.contains(abilityKey)
                    && difficultyClass >= 0 && difficultyClass <= 30 && validAdjudication(adjudication);
            if (valid) valid = switch (kind) {
                case "ABILITY" -> proficiency == null;
                case "SKILL" -> validRule(proficiency, "character.proficiency");
                case "TOOL" -> validRule(proficiency, "character.tool") && validPrefix(proficiency.ruleKey(), "tool");
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("S2_CHARACTER_CHECK_INPUT");
        }
    }
    public record CheckResult(int firstD20, Integer secondD20, int selectedD20, int abilityModifier,
            int proficiencyBonus, int total, boolean success, boolean effectiveAdvantage, boolean effectiveDisadvantage) {
        public CheckResult {
            if (firstD20 < 1 || firstD20 > 20 || secondD20 != null && (secondD20 < 1 || secondD20 > 20)
                    || selectedD20 < 1 || selectedD20 > 20 || abilityModifier < -5 || abilityModifier > 10
                    || proficiencyBonus < 0 || proficiencyBonus > 6 || total != selectedD20 + abilityModifier + proficiencyBonus)
                throw new IllegalArgumentException("S2_CHARACTER_CHECK_RESULT");
        }
    }
    public record EquipmentInput(UUID itemId, RuleIdentity item, String location, String adjudication) {
        public EquipmentInput {
            if (!validUuid(itemId) || item != null && !validRule(item, "equipment.item") || location != null && !EQUIPMENT_LOCATIONS.contains(location) || !validAdjudication(adjudication))
                throw new IllegalArgumentException("S2_CHARACTER_EQUIPMENT_INPUT");
        }
    }
    public record HealthInput(RuleIdentity rule, RuleIdentity effect, String temporaryChoice, String adjudication) {
        public HealthInput {
            if (!validRule(rule, "adventure.health") || effect != null && !validRule(effect, "adventure.vital_effect")
                    || temporaryChoice != null && !Set.of("KEEP", "REPLACE").contains(temporaryChoice) || !validAdjudication(adjudication))
                throw new IllegalArgumentException("S2_CHARACTER_HEALTH_INPUT");
        }
    }
    public record HealthResult(List<Integer> dice, Integer deathD20, Integer recoveryD4, int rolledAmount, int appliedAmount) {
        public HealthResult {
            if (dice == null || dice.size() > MAX_HEALTH_DICE || dice.stream().anyMatch(value -> value == null || value < 1 || value > 12)
                    || deathD20 != null && (deathD20 < 1 || deathD20 > 20) || recoveryD4 != null && (recoveryD4 < 1 || recoveryD4 > 4)
                    || rolledAmount < 0 || appliedAmount < 0)
                throw new IllegalArgumentException("S2_CHARACTER_HEALTH_RESULT");
            dice = List.copyOf(dice);
        }
    }
    /** Causal reference to the sole combat owner; never accepted as a public character command. */
    public record CombatInput(UUID combatId, UUID combatOperationId, UUID frameKey, UUID actorId,
            int damageAmount, boolean critical, int secondsAdvanced) {
        public CombatInput(UUID combatId,UUID combatOperationId,UUID frameKey,UUID actorId,int damageAmount,boolean critical) {
            this(combatId,combatOperationId,frameKey,actorId,damageAmount,critical,0);
        }
        public CombatInput {
            if (!validUuid(combatId) || !validUuid(combatOperationId) || !validUuid(frameKey) || !validUuid(actorId)
                    || damageAmount < 0 || damageAmount > 65535 || secondsAdvanced<0 || secondsAdvanced>28800) throw new IllegalArgumentException("S2_CHARACTER_COMBAT_INPUT");
        }
    }
    public record Command(String kind, RuleIdentity resource, long amount, String recoveryTrigger, String hpChoice,
            RuleIdentity targetClass, GrowthChoices growthChoices, RestInput restInput, CheckInput checkInput,
            EquipmentInput equipmentInput, HealthInput healthInput, CombatInput combatInput, SpellbookInput spellbookInput) {
        public Command(String kind, RuleIdentity resource,long amount,String recoveryTrigger,String hpChoice,
                RuleIdentity targetClass,GrowthChoices growthChoices,RestInput restInput,CheckInput checkInput,
                EquipmentInput equipmentInput,HealthInput healthInput,CombatInput combatInput) {
            this(kind,resource,amount,recoveryTrigger,hpChoice,targetClass,growthChoices,restInput,checkInput,equipmentInput,healthInput,combatInput,null);
        }
        public static Command spellbook(String kind,SpellbookInput input) { return new Command(kind,null,0,null,null,null,GrowthChoices.EMPTY,null,null,null,null,null,input); }
        public Command(String kind, RuleIdentity resource, long amount, String recoveryTrigger, String hpChoice,
                RuleIdentity targetClass, GrowthChoices growthChoices, RestInput restInput, CheckInput checkInput,
                EquipmentInput equipmentInput, HealthInput healthInput) {
            this(kind, resource, amount, recoveryTrigger, hpChoice, targetClass, growthChoices, restInput, checkInput,
                    equipmentInput, healthInput, null);
        }
        public Command(String kind, RuleIdentity resource, long amount, String recoveryTrigger, String hpChoice, RuleIdentity targetClass, GrowthChoices growthChoices, RestInput restInput, CheckInput checkInput) {
            this(kind, resource, amount, recoveryTrigger, hpChoice, targetClass, growthChoices, restInput, checkInput, null, null);
        }
        public Command(String kind, RuleIdentity resource, long amount, String recoveryTrigger, String hpChoice, RuleIdentity targetClass, GrowthChoices growthChoices, RestInput restInput) {
            this(kind, resource, amount, recoveryTrigger, hpChoice, targetClass, growthChoices, restInput, null);
        }
        public Command(String kind, RuleIdentity resource, long amount, String recoveryTrigger, String hpChoice, RuleIdentity targetClass, GrowthChoices growthChoices) {
            this(kind, resource, amount, recoveryTrigger, hpChoice, targetClass, growthChoices, null);
        }
        public Command(String kind, RuleIdentity resource, long amount, String recoveryTrigger, String hpChoice) {
            this(kind, resource, amount, recoveryTrigger, hpChoice, null, GrowthChoices.EMPTY, null);
        }
        public Command {
            boolean valid = kind != null && amount >= 0 && amount <= 1000000 && growthChoices != null;
            if (valid) valid = switch (kind) {
                case "INITIALIZED" -> resource == null && amount == 0 && recoveryTrigger == null && hpChoice == null && targetClass == null && growthChoices.feat() == null && growthChoices.abilityIncreases().isEmpty();
                case "CHARACTER_ADVANCED" -> resource == null && amount == 0 && recoveryTrigger == null && ("FIXED_AVERAGE".equals(hpChoice) || "SERVER_ROLL".equals(hpChoice)) && validRule(targetClass, "character.class");
                case "STATISTICS_ADVANCED" -> resource == null && amount == 0 && recoveryTrigger == null && ("FIXED_AVERAGE".equals(hpChoice) || "SERVER_ROLL".equals(hpChoice)) && targetClass == null && growthChoices.equals(GrowthChoices.EMPTY);
                case "RESOURCE_SPENT", "COMBAT_RESOURCE_SPENT" -> validRule(resource, "character.resource") && amount >= 1 && recoveryTrigger == null && hpChoice == null && targetClass == null && growthChoices.equals(GrowthChoices.EMPTY);
                case "RESOURCES_RECOVERED" -> resource == null && amount == 0 && ("SHORT_REST".equals(recoveryTrigger) || "LONG_REST".equals(recoveryTrigger)) && hpChoice == null && targetClass == null && growthChoices.equals(GrowthChoices.EMPTY);
                default -> false;
            };
            if (restKind(kind)) {
                valid = resource == null && amount == 0 && recoveryTrigger == null && hpChoice == null && targetClass == null && GrowthChoices.EMPTY.equals(growthChoices) && restInput != null;
                if (valid) valid = switch (kind) {
                    case "REST_STARTED" -> restInput.restKind() != null && restInput.activity() == null && restInput.minutes() == 0 && restInput.hitDieSides() == 0 && restInput.recoveredHitDice().isEmpty();
                    case "TIME_ADVANCED" -> restInput.restKind() == null && restInput.activity() != null && restInput.minutes() >= 1 && restInput.hitDieSides() == 0 && restInput.recoveredHitDice().isEmpty();
                    case "REST_COMPLETED" -> restInput.restKind() == null && restInput.activity() == null && restInput.minutes() == 0 && restInput.hitDieSides() == 0;
                    case "HIT_DIE_SPENT" -> restInput.restKind() == null && restInput.activity() == null && restInput.minutes() == 0 && restInput.hitDieSides() != 0 && restInput.recoveredHitDice().isEmpty();
                    case "REST_ENDED" -> restInput.restKind() == null && restInput.activity() == null && restInput.minutes() == 0 && restInput.hitDieSides() == 0 && restInput.recoveredHitDice().isEmpty();
                    default -> false;
                };
                if (restInput != null && !"REST_COMPLETED".equals(kind)) valid &= restInput.recoveredSpellSlots().isEmpty();
            } else valid &= restInput == null;
            if ("CHECK_RESOLVED".equals(kind)) valid = resource == null && amount == 0 && recoveryTrigger == null && hpChoice == null
                    && targetClass == null && GrowthChoices.EMPTY.equals(growthChoices) && restInput == null && checkInput != null;
            else valid &= checkInput == null;
            if (equipmentKind(kind)) {
                valid = resource == null && amount == 0 && recoveryTrigger == null && hpChoice == null && targetClass == null
                        && GrowthChoices.EMPTY.equals(growthChoices) && restInput == null && checkInput == null && equipmentInput != null && healthInput == null;
                if (valid) valid = switch (kind) {
                    case "ITEM_ACQUIRED" -> equipmentInput.item() != null && "CARRIED".equals(equipmentInput.location());
                    case "ITEM_EQUIPPED" -> equipmentInput.item() == null && equipmentInput.location() != null && !equipmentInput.location().equals("CARRIED");
                    case "ITEM_REMOVED", "ITEM_UNEQUIPPED" -> equipmentInput.item() == null && equipmentInput.location() == null;
                    default -> false;
                };
            } else valid &= equipmentInput == null;
            if (healthKind(kind)) valid = resource == null && amount == 0 && recoveryTrigger == null && hpChoice == null && targetClass == null
                    && GrowthChoices.EMPTY.equals(growthChoices) && restInput == null && checkInput == null && equipmentInput == null && healthInput != null
                    && (Set.of("HEALTH_EFFECT_RESOLVED","COMBAT_ENVIRONMENT_HEALTH").contains(kind) ? healthInput.effect() != null : healthInput.effect() == null && healthInput.temporaryChoice() == null);
            else valid &= healthInput == null;
            if (combatKind(kind)) valid &= combatInput != null && ("COMBAT_RESOURCE_SPENT".equals(kind)
                    ? healthInput == null && combatInput.damageAmount() == 0 && !combatInput.critical()
                    : healthInput != null && (kind.equals("COMBAT_ENVIRONMENT_HEALTH") ? healthInput.effect()!=null && !combatInput.critical() : healthInput.effect() == null && healthInput.temporaryChoice() == null))
                    && (!kind.equals("COMBAT_DEATH_SAVE_RESOLVED") || combatInput.damageAmount() == 0 && !combatInput.critical());
            else valid &= combatInput == null;
            if (combatInput != null) valid &= kind.equals("COMBAT_TIME_ADVANCED")
                    ? combatInput.secondsAdvanced()>=1 && combatInput.secondsAdvanced()<=28800 && combatInput.damageAmount()==0 && !combatInput.critical()
                    : combatInput.secondsAdvanced()==0;
            if (spellbookKind(kind)) valid = resource==null && amount==0 && recoveryTrigger==null && hpChoice==null && targetClass==null
                    && GrowthChoices.EMPTY.equals(growthChoices) && restInput==null && checkInput==null && equipmentInput==null && healthInput==null
                    && combatInput==null && spellbookInput!=null;
            else valid &= spellbookInput==null;
            if (!valid) throw new IllegalArgumentException("S2_CHARACTER_COMMAND");
        }
    }
    public record Event(UUID operationId, String requestDigest, Command command, Integer hitDieRoll, State before, State after, CheckResult checkResult, HealthResult healthResult) {
        public Event(UUID operationId, String requestDigest, Command command, Integer hitDieRoll, State before, State after, CheckResult checkResult) {
            this(operationId, requestDigest, command, hitDieRoll, before, after, checkResult, null);
        }
        public Event(UUID operationId, String requestDigest, Command command, Integer hitDieRoll, State before, State after) {
            this(operationId, requestDigest, command, hitDieRoll, before, after, null);
        }
        public String kind() { return command.kind(); }
    }
    public record History(State current, List<Event> events) {
        public History { events = List.copyOf(events); }
    }

    /** No source connection or rules execution is used to reconstruct committed facts. */
    public History read(Connection connection, UUID characterId) throws SQLException {
        uuid(characterId);
        List<Root> roots = query(connection, "s2_character_runtime", "character_id", bytes(characterId), "character_id", 1, row -> {
            require(id(row, "character_id").equals(characterId));
            return new Root(new Identity(id(row, "run_id"), id(row, "snapshot_id")), id(row, "campaign_id"), characterId, id(row, "preparation_operation_id"));
        });
        if (roots.isEmpty()) {
            for (String table : List.of("s2_character_event", "s2_character_event_state", "s2_character_event_resource", "s2_character_event_class", "s2_character_event_hit_die", "s2_character_event_subclass", "s2_character_event_feature", "s2_character_event_feat", "s2_character_event_proficiency", "s2_character_command_ability", "s2_character_command_proficiency", "s2_character_command_hit_die", "s2_character_arcane_recovery_choice", "s2_character_check", "s2_character_event_equipment", "s2_character_equipment_command", "s2_character_health_event", "s2_character_health_die", "s2_character_combat_input", "s2_character_event_learned_spell", "s2_character_event_prepared_spell", "s2_character_spellbook_command", "s2_character_spellbook_choice"))
                require(query(connection, table, "character_id", bytes(characterId), "event_sequence", 1, row -> true).isEmpty());
            return null;
        }
        Root root = roots.getFirst(); verifyPlan(connection, root);
        List<Header> headers = new ArrayList<>();
        query(connection, "s2_character_event", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            require(number(row, "event_sequence") == headers.size() + 1);
            headers.add(new Header(id(row, "operation_id"), ascii(row, "request_digest", 64), ascii(row, "event_kind", 32),
                    nullableRule(row, "resource"), number(row, "resource_amount"), nullableAscii(row, "recovery_trigger", 16), nullableAscii(row, "hp_choice", 64),
                    nullableRule(row, "target_class"), nullableRule(row, "chosen_subclass"), nullableRule(row, "chosen_feat"), integer(row, "ability_choice_count"), integer(row, "proficiency_choice_count"), nullableAscii(row, "rest_kind", 16), nullableAscii(row, "rest_activity", 16), number(row, "rest_minutes"), integer(row, "rest_hit_die_sides"), integer(row, "recovered_hit_die_count"),
                    row.getObject("hit_die_roll") == null ? null : integer(row, "hit_die_roll"), integer(row, "arcane_recovery_choice_count")));
            return true;
        });
        require(!headers.isEmpty());
        Map<Long, CheckFacts> checks = new HashMap<>();
        query(connection, "s2_character_check", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            long sequence = eventSequence(row, headers.size());
            Object adjudication = row.getObject("adjudication"); require(adjudication instanceof String);
            int advantage = integer(row, "advantage"), disadvantage = integer(row, "disadvantage"), success = integer(row, "success");
            int effectiveAdvantage = integer(row, "effective_advantage"), effectiveDisadvantage = integer(row, "effective_disadvantage");
            require(advantage <= 1 && disadvantage <= 1 && success <= 1 && effectiveAdvantage <= 1 && effectiveDisadvantage <= 1);
            CheckInput input = new CheckInput(rule(row, "check"), ascii(row, "check_kind", 8), ascii(row, "ability_key", 32),
                    nullableRule(row, "proficiency"), integer(row, "difficulty_class"), advantage == 1, disadvantage == 1, (String) adjudication);
            CheckResult result = new CheckResult(integer(row, "first_d20"), row.getObject("second_d20") == null ? null : integer(row, "second_d20"),
                    integer(row, "selected_d20"), signedInteger(row, "ability_modifier"), integer(row, "proficiency_bonus"), signedInteger(row, "total"), success == 1, effectiveAdvantage == 1, effectiveDisadvantage == 1);
            require(checks.putIfAbsent(sequence, new CheckFacts(input, result)) == null); return true;
        });
        Map<Long, EquipmentInput> equipmentCommands = new HashMap<>();
        query(connection, "s2_character_equipment_command", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            long sequence = eventSequence(row, headers.size());
            EquipmentInput input = new EquipmentInput(id(row, "item_id"), nullableRule(row, "item"), nullableAscii(row, "location", 16), text(row, "adjudication"));
            require(equipmentCommands.putIfAbsent(sequence, input) == null); return true;
        });
        Map<Long, List<Integer>> healthDice = new HashMap<>();
        query(connection, "s2_character_health_die", "character_id", bytes(characterId), "event_sequence, die_index", MAX_EVENTS * MAX_HEALTH_DICE, row -> {
            long sequence = eventSequence(row, headers.size()); var values = healthDice.computeIfAbsent(sequence, ignored -> new ArrayList<>());
            require(integer(row, "die_index") == values.size() && values.size() < MAX_HEALTH_DICE); values.add(integer(row, "die_value")); return true;
        });
        Map<Long, HealthFacts> healthEvents = new HashMap<>();
        query(connection, "s2_character_health_event", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            long sequence = eventSequence(row, headers.size()); List<Integer> values = healthDice.remove(sequence); if (values == null) values = List.of();
            require(values.size() == integer(row, "die_count"));
            HealthInput input = new HealthInput(rule(row, "health"), nullableRule(row, "effect"), nullableAscii(row, "temporary_choice", 8), text(row, "adjudication"));
            HealthResult result = new HealthResult(values, row.getObject("death_d20") == null ? null : integer(row, "death_d20"),
                    row.getObject("recovery_d4") == null ? null : integer(row, "recovery_d4"), integer(row, "rolled_amount"), integer(row, "applied_amount"));
            require(healthEvents.putIfAbsent(sequence, new HealthFacts(input, result)) == null); return true;
        });
        Map<Long, CombatInput> combatInputs = new HashMap<>();
        query(connection, "s2_character_combat_input", "character_id", bytes(characterId), "event_sequence", MAX_EVENTS, row -> {
            long sequence = eventSequence(row, headers.size());
            require(id(row, "operation_id").equals(headers.get((int) sequence - 1).operation));
            int critical = integer(row, "critical"); require(critical <= 1);
            CombatInput input = new CombatInput(id(row, "combat_id"), id(row, "combat_operation_id"), id(row, "frame_key"),
                    id(row, "actor_id"), integer(row, "damage_amount"), critical == 1, integer(row,"seconds_advanced"));
            require(combatInputs.putIfAbsent(sequence, input) == null); return true;
        });
        require(healthDice.isEmpty());
        Map<Long, Map<String, Integer>> abilityChoices = new HashMap<>();
        query(connection, "s2_character_command_ability", "character_id", bytes(characterId), "event_sequence, choice_index", MAX_EVENTS * 2, row -> {
            long sequence = eventSequence(row, headers.size()); var choices = abilityChoices.computeIfAbsent(sequence, ignored -> new LinkedHashMap<>());
            require(integer(row, "choice_index") == choices.size() && choices.size() < 2 && choices.putIfAbsent(ascii(row, "ability_key", 32), integer(row, "increase_value")) == null); return true;
        });
        Map<Long, List<ChoiceTarget>> proficiencyChoices = new HashMap<>();
        query(connection, "s2_character_command_proficiency", "character_id", bytes(characterId), "event_sequence, choice_index", MAX_EVENTS * MAX_GRANTS, row -> {
            long sequence = eventSequence(row, headers.size()); var choices = proficiencyChoices.computeIfAbsent(sequence, ignored -> new ArrayList<>());
            require(integer(row, "choice_index") == choices.size() && choices.size() < MAX_GRANTS);
            choices.add(new ChoiceTarget(slot(row), rule(row, "target"))); return true;
        });
        Map<Long, Map<Integer, Integer>> recoveredDice = new HashMap<>();
        query(connection, "s2_character_command_hit_die", "character_id", bytes(characterId), "event_sequence, pool_index", MAX_EVENTS * 4, row -> {
            long sequence = eventSequence(row, headers.size()); var recovered = recoveredDice.computeIfAbsent(sequence, ignored -> new LinkedHashMap<>());
            require(integer(row, "pool_index") == recovered.size() && recovered.size() < 4 && recovered.putIfAbsent(integer(row, "sides"), integer(row, "recovered_value")) == null); return true;
        });
        Map<Long, Map<Integer, Integer>> recoveredSlots = new HashMap<>();
        query(connection, "s2_character_arcane_recovery_choice", "character_id", bytes(characterId), "event_sequence, slot_rank", MAX_EVENTS * 5, row -> {
            long sequence = eventSequence(row, headers.size());
            var recovered = recoveredSlots.computeIfAbsent(sequence, ignored -> new LinkedHashMap<>());
            require(recovered.size() < 5 && recovered.putIfAbsent(integer(row, "slot_rank"), integer(row, "recovered_value")) == null);
            return true;
        });
        var classes = readChildren(connection, characterId, "class", "class_index", MAX_CLASSES, headers.size(), row -> new ClassLevel(rule(row, "class"), integer(row, "class_level")));
        var dice = readChildren(connection, characterId, "hit_die", "pool_index", MAX_HIT_DICE_POOLS, headers.size(), row -> new HitDicePool(integer(row, "sides"), integer(row, "current_value"), integer(row, "maximum_value")));
        var subclasses = readChildren(connection, characterId, "subclass", "subclass_index", MAX_CLASSES, headers.size(), row -> new SubclassState(rule(row, "class"), rule(row, "subclass"), integer(row, "acquired_class_level")));
        var features = readChildren(connection, characterId, "feature", "feature_index", MAX_FEATURES, headers.size(), row -> new FeatureState(rule(row, "feature"), rule(row, "owner"), integer(row, "acquired_class_level")));
        var feats = readChildren(connection, characterId, "feat", "feat_index", MAX_FEATS, headers.size(), row -> new FeatState(rule(row, "feat"), rule(row, "class"), integer(row, "acquired_class_level")));
        var proficiencies = readChildren(connection, characterId, "proficiency", "proficiency_index", MAX_GRANTS, headers.size(), row -> new ProficiencyState(slot(row), rule(row, "target"), integer(row, "acquired_total_level")));
        var equipment = readChildren(connection, characterId, "equipment", "equipment_index", MAX_EQUIPMENT, headers.size(), row -> new EquipmentItem(id(row, "item_id"), rule(row, "item"), ascii(row, "location", 16),
                row.getObject("ground_combat_id")==null?null:id(row,"ground_combat_id"),
                row.getObject("ground_x")==null?null:signedInteger(row,"ground_x"),row.getObject("ground_y")==null?null:signedInteger(row,"ground_y")));

        var learnedSpells=readChildren(connection,characterId,"learned_spell","spell_index",14,headers.size(),row->new LearnedSpell(rule(row,"spell"),integer(row,"acquired_class_level")));
        var preparedSpells=readChildren(connection,characterId,"prepared_spell","spell_index",15,headers.size(),row->rule(row,"spell"));
        Map<Long,List<RuleIdentity>> learnedChoices=new HashMap<>(),preparedChoices=new HashMap<>();
        query(connection,"s2_character_spellbook_choice","character_id",bytes(characterId),"event_sequence, choice_kind, spell_index",MAX_EVENTS*21,row->{
            long seq=eventSequence(row,headers.size()); String kind=ascii(row,"choice_kind",8);require(Set.of("LEARNED","PREPARED").contains(kind));
            var choices=(kind.equals("LEARNED")?learnedChoices:preparedChoices).computeIfAbsent(seq,k->new ArrayList<>());
            require(integer(row,"spell_index")==choices.size() && choices.size()<(kind.equals("LEARNED")?6:15));choices.add(rule(row,"spell"));return true;
        });
        Map<Long,SpellbookInput> spellbookCommands=new HashMap<>();
        query(connection,"s2_character_spellbook_command","character_id",bytes(characterId),"event_sequence",MAX_EVENTS,row->{
            long seq=eventSequence(row,headers.size());var learned=learnedChoices.remove(seq);var prepared=preparedChoices.remove(seq);
            if(learned==null)learned=List.of();if(prepared==null)prepared=List.of();
            require(integer(row,"learned_count")==learned.size() && integer(row,"prepared_count")==prepared.size());
            SpellbookInput input=new SpellbookInput(rule(row,"casting"),learned,prepared);
            require(input.learned().equals(learned)&&input.prepared().equals(prepared)&&spellbookCommands.putIfAbsent(seq,input)==null);return true;
        });
        require(learnedChoices.isEmpty()&&preparedChoices.isEmpty());
        Map<StateKey, List<ResourceState>> resourceRows = new HashMap<>();
        query(connection, "s2_character_event_resource", "character_id", bytes(characterId), "event_sequence, phase, resource_index", MAX_EVENTS * MAX_RESOURCES * 2, row -> {
            StateKey key = stateKey(row, headers.size());
            List<ResourceState> resources = resourceRows.computeIfAbsent(key, ignored -> new ArrayList<>());
            require(integer(row, "resource_index") == resources.size() && resources.size() < MAX_RESOURCES);
            int unlimited = integer(row, "unlimited"); require(unlimited <= 1);
            resources.add(new ResourceState(rule(row, "resource"), number(row, "current_value"), number(row, "maximum_value"), unlimited == 1));
            return true;
        });
        Map<StateKey, State> states = new HashMap<>();
        query(connection, "s2_character_event_state", "character_id", bytes(characterId), "event_sequence, phase", MAX_EVENTS * 2, row -> {
            StateKey key = stateKey(row, headers.size());
            List<ResourceState> resources = resourceRows.remove(key); if (resources == null) resources = List.of();
            require(integer(row, "resource_count") == resources.size());
            Map<String, Integer> abilities = new LinkedHashMap<>();
            for (String ability : ABILITIES) abilities.put(ability, integer(row, ability.substring("ability.".length())));
            State state = new State(root.identity, root.campaign, root.character, root.preparation, rule(row, "class"), abilities,
                    integer(row, "total_level"), integer(row, "proficiency_bonus"), number(row, "hit_points"), number(row, "maximum_hit_points"),
                    take(classes, key, integer(row, "class_count")), take(dice, key, integer(row, "hit_die_count")), take(subclasses, key, integer(row, "subclass_count")),
                    take(features, key, integer(row, "feature_count")), take(feats, key, integer(row, "feat_count")), take(proficiencies, key, integer(row, "proficiency_count")), number(row, "row_version"), resources, readRest(row),
                    take(equipment, key, integer(row, "equipment_count")), readHealth(row), readSpellbook(row,take(learnedSpells,key,integer(row,"learned_spell_count")),take(preparedSpells,key,integer(row,"prepared_spell_count"))));
            require(states.putIfAbsent(key, state) == null); return true;
        });
        require(resourceRows.isEmpty() && classes.isEmpty() && dice.isEmpty() && subclasses.isEmpty() && features.isEmpty() && feats.isEmpty() && proficiencies.isEmpty() && equipment.isEmpty() && learnedSpells.isEmpty() && preparedSpells.isEmpty() && states.size() == headers.size() * 2 - 1);
        List<Event> events = new ArrayList<>();
        for (int index = 0; index < headers.size(); index++) {
            Header header = headers.get(index);
            State before = index == 0 ? null : states.remove(new StateKey(index + 1, "BEFORE"));
            State after = states.remove(new StateKey(index + 1, "AFTER"));
            require(after != null && (index == 0 || before != null));
            Map<String, Integer> increases = abilityChoices.remove(index + 1L); if (increases == null) increases = Map.of();
            List<ChoiceTarget> chosen = proficiencyChoices.remove(index + 1L); if (chosen == null) chosen = List.of();
            require(increases.size() == header.abilities && chosen.size() == header.proficiencies);
            var selected = new TreeMap<com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity, List<RuleIdentity>>(SLOT_ORDER);
            for (ChoiceTarget choice : chosen) selected.computeIfAbsent(choice.slot, ignored -> new ArrayList<>()).add(choice.target);
            try {
                GrowthChoices growth = new GrowthChoices(header.subclass, increases, header.feat, selected);
                List<ChoiceTarget> canonical = new ArrayList<>();
                growth.proficiencyChoices().forEach((slot, targets) -> targets.forEach(target -> canonical.add(new ChoiceTarget(slot, target))));
                require(canonical.equals(chosen) && new ArrayList<>(growth.abilityIncreases().keySet()).equals(new ArrayList<>(increases.keySet())));
                Map<Integer, Integer> recovered = recoveredDice.remove(index + 1L); if (recovered == null) recovered = Map.of();
                require(recovered.size() == header.recoveredDice);
                Map<Integer, Integer> slots = recoveredSlots.remove(index + 1L); if (slots == null) slots = Map.of();
                require(slots.size() == header.recoveredSlots);
                RestInput input = null;
                if (restKind(header.kind)) {
                    input = new RestInput(header.restKind, header.activity, header.minutes, header.dieSides, recovered, slots);
                    require(new ArrayList<>(input.recoveredHitDice().keySet()).equals(new ArrayList<>(recovered.keySet())));
                    require(new ArrayList<>(input.recoveredSpellSlots().keySet()).equals(new ArrayList<>(slots.keySet())));
                } else require(header.restKind == null && header.activity == null && header.minutes == 0 && header.dieSides == 0 && recovered.isEmpty() && slots.isEmpty());
                CheckFacts check = checks.remove(index + 1L);
                require("CHECK_RESOLVED".equals(header.kind) == (check != null));
                EquipmentInput equipmentInput = equipmentCommands.remove(index + 1L); HealthFacts health = healthEvents.remove(index + 1L);
                require(equipmentKind(header.kind) == (equipmentInput != null) && healthKind(header.kind) == (health != null));
                CombatInput combat = combatInputs.remove(index + 1L); require(combatKind(header.kind) == (combat != null));
                SpellbookInput book=spellbookCommands.remove(index+1L);require(spellbookKind(header.kind)==(book!=null));
                Command command = new Command(header.kind, header.resource, header.amount, header.trigger, header.hp, header.targetClass, growth, input, check == null ? null : check.input(), equipmentInput, health == null ? null : health.input(), combat, book);
                events.add(new Event(header.operation, header.digest, command, header.roll, before, after, check == null ? null : check.result(), health == null ? null : health.result()));
            } catch (IllegalArgumentException malformed) { throw new SQLException("S2_CHARACTER_RUNTIME_CORRUPT", malformed); }
        }
        require(states.isEmpty() && abilityChoices.isEmpty() && proficiencyChoices.isEmpty() && recoveredDice.isEmpty() && recoveredSlots.isEmpty() && checks.isEmpty() && equipmentCommands.isEmpty() && healthEvents.isEmpty() && combatInputs.isEmpty() && spellbookCommands.isEmpty());
        History history = new History(events.getLast().after(), events); validate(history); return history;
    }

    private static StateKey stateKey(ResultSet row, int maximum) throws SQLException {
        long sequence = number(row, "event_sequence"); String phase = ascii(row, "phase", 6);
        require(sequence >= 1 && sequence <= maximum && (phase.equals("AFTER") || phase.equals("BEFORE") && sequence > 1));
        return new StateKey(sequence, phase);
    }

    private static long eventSequence(ResultSet row, int maximum) throws SQLException { long sequence = number(row, "event_sequence"); require(sequence >= 1 && sequence <= maximum); return sequence; }
    private static <T> Map<StateKey, List<T>> readChildren(Connection connection, UUID character, String suffix, String index, int maximum, int events, Reader<T> decoder) throws SQLException {
        Map<StateKey, List<T>> result = new HashMap<>();
        query(connection, "s2_character_event_" + suffix, "character_id", bytes(character), "event_sequence, phase, " + index, MAX_EVENTS * maximum * 2, row -> {
            StateKey key = stateKey(row, events); var values = result.computeIfAbsent(key, ignored -> new ArrayList<>());
            require(integer(row, index) == values.size() && values.size() < maximum); values.add(decoder.read(row)); return true;
        }); return result;
    }
    private static <T> List<T> take(Map<StateKey, List<T>> rows, StateKey key, int count) throws SQLException {
        List<T> result = rows.remove(key); if (result == null) result = List.of(); require(result.size() == count); return result;
    }

    public UUID findByRun(Connection connection, UUID runId) throws SQLException {
        uuid(runId); List<UUID> characters = query(connection, "s2_character_runtime", "run_id", bytes(runId), "character_id", 1, row -> id(row, "character_id"));
        return characters.isEmpty() ? null : characters.getFirst();
    }
    public History readRunCharacter(Connection connection, UUID runId) throws SQLException { UUID character = findByRun(connection, runId); return character == null ? null : read(connection, character); }

    public Event findOperation(Connection connection, UUID operationId) throws SQLException {
        uuid(operationId);
        List<UUID> characters = query(connection, "s2_character_event", "operation_id", bytes(operationId), "event_sequence", 1, row -> id(row, "character_id"));
        if (characters.isEmpty()) return null;
        History history = read(connection, characters.getFirst());
        List<Event> matches = history.events().stream().filter(event -> event.operationId().equals(operationId)).toList();
        require(matches.size() == 1); return matches.getFirst();
    }

    /** Appends exactly one real transition, including initialization's nonempty root event. */
    public void append(Connection connection, Event event) throws SQLException {
        BusinessWriteTransactions.requireGuarded(connection); validateEvent(event);
        History previous = read(connection, event.after().characterId());
        require((previous == null) == (event.before() == null));
        require(previous == null || previous.current().equals(event.before()));
        require(findOperation(connection, event.operationId()) == null);
        List<Event> events = previous == null ? new ArrayList<>() : new ArrayList<>(previous.events()); events.add(event);
        History expected = new History(event.after(), events); validate(expected);
        if (previous == null) insertRoot(connection, root(event.after()));
        insertEvent(connection, event);
        require(expected.equals(read(connection, event.after().characterId())));
    }

    /** Restores complete validated state and immutable history directly, without re-running commands. */
    public void restore(Connection connection, History history) throws SQLException {
        BusinessWriteTransactions.requireGuarded(connection); validate(history);
        require(read(connection, history.current().characterId()) == null);
        for (Event event : history.events()) require(findOperation(connection, event.operationId()) == null);
        insertRoot(connection, root(history.current()));
        for (Event event : history.events()) insertEvent(connection, event);
        require(history.equals(read(connection, history.current().characterId())));
    }

    public static void checkCandidateCapacity(History previous, State candidate) throws SQLException { checkCandidateCapacity(previous, candidate, null); }
    public static void checkCandidateCapacity(History previous, State candidate, Command command) throws SQLException {
        validateState(candidate);
        long rows = stateRows(candidate) + (previous == null ? 0 : stateRows(previous.current())) + (command == null ? MAX_GRANTS + 6 : commandRows(command));
        if (previous != null) {
            require(previous.events().size() < MAX_EVENTS);
            for (Event event : previous.events()) rows += stateRows(event.after()) + (event.before() == null ? 0 : stateRows(event.before())) + eventRows(event);
        }
        require(rows <= MAX_HISTORY_CHILD_ROWS);
    }
    /** Combat changes health, slot values, clock and equipment location without adding state rows. */
    public static void checkCombatCapacity(History history, int additionalEvents) throws SQLException {
        require(additionalEvents >= 0 && history.events().size() + additionalEvents <= MAX_EVENTS);
        long used = 0;
        for (Event event : history.events()) used += stateRows(event.after())
                + (event.before() == null ? 0 : stateRows(event.before())) + eventRows(event);
        long perEvent = stateRows(history.current()) * 2 + 2 + MAX_HEALTH_DICE;
        require(used + perEvent * additionalEvents <= MAX_HISTORY_CHILD_ROWS);
    }
    private static long stateRows(State state) { return 1L + state.classes().size() + state.hitDicePools().size() + state.subclasses().size() + state.features().size() + state.feats().size() + state.proficiencies().size() + state.resources().size() + state.equipment().size() + (state.spellbook()==null?0:state.spellbook().learned().size()+state.spellbook().prepared().size()); }
    private static long commandRows(Command command) { return command.growthChoices().abilityIncreases().size() + command.growthChoices().proficiencyChoices().values().stream().mapToInt(List::size).sum() + (command.restInput() == null ? 0 : command.restInput().recoveredHitDice().size() + command.restInput().recoveredSpellSlots().size()) + (command.checkInput() == null ? 0 : 1) + (command.equipmentInput() == null ? 0 : 1) + (command.healthInput() == null ? 0 : 1 + MAX_HEALTH_DICE) + (command.combatInput() == null ? 0 : 1) + (command.spellbookInput()==null?0:1+command.spellbookInput().learned().size()+command.spellbookInput().prepared().size()); }
    private static long eventRows(Event event) { return commandRows(event.command()) - (event.healthResult() == null ? 0 : MAX_HEALTH_DICE - event.healthResult().dice().size()); }
    public static void validate(History history) throws SQLException {
        require(history != null && !history.events().isEmpty() && history.events().size() <= MAX_EVENTS);
        Set<UUID> operations = new HashSet<>(), acquiredItems = new HashSet<>(); State previous = null; long rows = 0;
        for (Event event : history.events()) {
            validateEvent(event); require(operations.add(event.operationId()) && Objects.equals(previous, event.before())); previous = event.after();
            if (event.kind().equals("ITEM_ACQUIRED")) require(event.operationId().equals(event.command().equipmentInput().itemId()) && acquiredItems.add(event.command().equipmentInput().itemId()));
            rows += stateRows(event.after()) + (event.before() == null ? 0 : stateRows(event.before())) + eventRows(event);
        }
        require(previous.equals(history.current()) && rows <= MAX_HISTORY_CHILD_ROWS);
    }
    public static boolean spellbookKind(String kind) { return kind!=null && Set.of("SPELLBOOK_INITIALIZED","SPELLBOOK_ADVANCED","SPELLBOOK_PREPARED").contains(kind); }
    private static boolean restKind(String kind) { return kind != null && Set.of("REST_STARTED", "TIME_ADVANCED", "REST_COMPLETED", "HIT_DIE_SPENT", "REST_ENDED").contains(kind); }
    private static boolean equipmentKind(String kind) { return kind != null && Set.of("ITEM_ACQUIRED", "ITEM_REMOVED", "ITEM_EQUIPPED", "ITEM_UNEQUIPPED").contains(kind); }
    public static boolean combatKind(String kind) { return kind != null && Set.of("COMBAT_DAMAGE_APPLIED", "COMBAT_DEATH_SAVE_RESOLVED", "COMBAT_TIME_ADVANCED", "COMBAT_RESOURCE_SPENT", "COMBAT_HEALING_APPLIED", "COMBAT_ENVIRONMENT_HEALTH").contains(kind); }
    private static boolean healthKind(String kind) { return "HEALTH_EFFECT_RESOLVED".equals(kind) || "DEATH_SAVE_RESOLVED".equals(kind) || combatKind(kind) && !"COMBAT_RESOURCE_SPENT".equals(kind); }
    private static boolean advancement(String kind) { return kind.equals("CHARACTER_ADVANCED") || kind.equals("STATISTICS_ADVANCED"); }
    private static void validateEvent(Event event) throws SQLException {
        require(event != null && event.command() != null); uuid(event.operationId());
        require(event.requestDigest() != null && event.requestDigest().matches("[0-9a-f]{64}") && KINDS.contains(event.kind()));
        validateState(event.after()); State after = event.after(), before = event.before(); Command command = event.command();
        require("CHECK_RESOLVED".equals(event.kind()) == (event.checkResult() != null));
        require(healthKind(event.kind()) == (event.healthResult() != null));
        require(!Set.of(after.identity().runId(), after.identity().snapshotId(), after.campaignId(), after.characterId(), after.preparationOperationId()).contains(event.operationId()));
        if (before == null) require(event.kind().equals("INITIALIZED") && after.version() == 0 && after.level() == 1 && event.hitDieRoll() == null && after.equipment().isEmpty() && after.health().equals(HealthState.INITIAL) && after.spellbook()==null);
        else {
            validateState(before); require(!event.kind().equals("INITIALIZED") && root(before).equals(root(after)) && before.characterClass().equals(after.characterClass()) && after.version() == before.version() + 1);
            if (!equipmentKind(event.kind()) && !Set.of("COMBAT_DAMAGE_APPLIED","COMBAT_ENVIRONMENT_HEALTH").contains(event.kind())) require(before.equipment().equals(after.equipment()));
            if (!healthKind(event.kind()) && !Set.of("TIME_ADVANCED", "REST_COMPLETED", "HIT_DIE_SPENT").contains(event.kind())) require(before.health().equals(after.health()));
            boolean arcaneRecovery = command.restInput() != null && !command.restInput().recoveredSpellSlots().isEmpty();
            if (arcaneRecovery) {
                require(event.kind().equals("REST_COMPLETED") && before.spellbook() != null && after.spellbook() != null
                        && before.rest().active() != null && before.rest().active().kind().equals("SHORT_REST")
                        && before.rest().active().phase().equals("IN_PROGRESS") && !before.rest().active().interrupted()
                        && before.rest().minute() - before.rest().active().startMinute() >= 60
                        && after.rest().active() != null && after.rest().active().phase().equals("SPENDING_HIT_DICE"));
                long day = before.rest().minute() / 1440;
                require(!Objects.equals(before.spellbook().lastArcaneRecoveryDay(), day)
                        && Objects.equals(after.spellbook().lastArcaneRecoveryDay(), day)
                        && before.spellbook().equals(withArcaneRecoveryDay(after.spellbook(), before.spellbook().lastArcaneRecoveryDay())));
            } else if (!spellbookKind(event.kind())) require(Objects.equals(before.spellbook(),after.spellbook()));
            if (spellbookKind(event.kind())) {
                require(event.hitDieRoll()==null && before.rest().active()==null && after.spellbook()!=null
                        && sameCore(before,withSpellbook(after,after.version(),before.spellbook())) && before.hitPoints()==after.hitPoints()
                        && sameRestExceptMinute(before.rest(),after.rest()) && after.rest().minute()>=before.rest().minute()
                        && Objects.equals(before.spellbook()==null?null:before.spellbook().lastArcaneRecoveryDay(), after.spellbook().lastArcaneRecoveryDay()));
            } else if (advancement(event.kind())) {
                require(before.rest().equals(after.rest()));
                require(after.level() == before.level() + 1 && after.features().containsAll(before.features()) && after.feats().containsAll(before.feats()) && after.subclasses().containsAll(before.subclasses()) && after.proficiencies().containsAll(before.proficiencies()));
                RuleIdentity target = command.targetClass() == null ? before.characterClass() : command.targetClass();
                Map<RuleIdentity, Integer> old = new HashMap<>(); before.classes().forEach(value -> old.put(value.characterClass(), value.level()));
                for (ClassLevel value : after.classes()) require(value.level() == old.getOrDefault(value.characterClass(), 0) + (value.characterClass().equals(target) ? 1 : 0));
                require(after.classes().stream().map(ClassLevel::characterClass).toList().containsAll(old.keySet()));
                require(event.hitDieRoll() == null || event.hitDieRoll() >= 1 && event.hitDieRoll() <= 20);
                require("SERVER_ROLL".equals(command.hpChoice()) == (event.hitDieRoll() != null));
                if (event.kind().equals("STATISTICS_ADVANCED")) require(before.abilities().equals(after.abilities()) && before.classes().size() == after.classes().size());
            } else if (event.kind().equals("COMBAT_RESOURCE_SPENT")) {
                require(event.hitDieRoll() == null && before.rest().active() == null && before.rest().equals(after.rest())
                        && before.hitPoints() == after.hitPoints() && before.health().equals(after.health())
                        && sameCoreExceptResources(before, after));
                require(before.resources().size() == after.resources().size()); boolean spent = false;
                for (int i = 0; i < before.resources().size(); i++) {
                    ResourceState old = before.resources().get(i), next = after.resources().get(i);
                    if (old.identity().equals(command.resource())) {
                        require(!spent && old.identity().equals(next.identity()) && !old.unlimited() && !next.unlimited()
                                && old.maximum() == next.maximum() && old.current() >= command.amount()
                                && next.current() == old.current() - command.amount()); spent = true;
                    } else require(old.equals(next));
                }
                require(spent);
            } else if (equipmentKind(event.kind()) || healthKind(event.kind())) {
                require(event.hitDieRoll() == null && before.rest().active() == null && sameCore(before, after));
                if (equipmentKind(event.kind())) require(before.hitPoints() == after.hitPoints() && before.health().equals(after.health())
                        && after.rest().minute() >= before.rest().minute() && sameRestExceptMinute(before.rest(), after.rest()));
                else {
                    require(event.kind().equals("COMBAT_TIME_ADVANCED") || before.rest().equals(after.rest())); HealthResult result = event.healthResult();
                    require((event.kind().equals("DEATH_SAVE_RESOLVED") || event.kind().equals("COMBAT_DEATH_SAVE_RESOLVED")) ? result.deathD20() != null
                            && (event.kind().equals("COMBAT_DEATH_SAVE_RESOLVED") ? result.dice().size() <= 1 && result.dice().stream().allMatch(d -> d <= 4) : result.dice().isEmpty())
                            : result.deathD20() == null && result.recoveryD4() == null);
                }
            } else if (event.kind().equals("CHECK_RESOLVED")) {
                require(event.hitDieRoll() == null && before.rest().active() == null && before.equals(withVersion(after, before.version())));
                CheckInput input = command.checkInput(); CheckResult result = event.checkResult();
                require((result.effectiveAdvantage() != result.effectiveDisadvantage()) == (result.secondD20() != null));
                int selected = result.secondD20() == null ? result.firstD20() : result.effectiveAdvantage()
                        ? Math.max(result.firstD20(), result.secondD20()) : Math.min(result.firstD20(), result.secondD20());
                int proficiency = input.proficiency() != null && before.proficiencies().stream().anyMatch(value -> value.target().equals(input.proficiency())) ? before.proficiencyBonus() : 0;
                require(result.selectedD20() == selected && result.abilityModifier() == Math.floorDiv(before.abilities().get(input.abilityKey()) - 10, 2)
                        && result.proficiencyBonus() == proficiency && result.success() == (result.total() >= input.difficultyClass()));
            } else if (restKind(event.kind())) {
                require(before.abilities().equals(after.abilities()) && before.level() == after.level() && before.classes().equals(after.classes()) && before.subclasses().equals(after.subclasses()) && before.features().equals(after.features()) && before.feats().equals(after.feats()) && before.proficiencies().equals(after.proficiencies()));
                require(event.kind().equals("HIT_DIE_SPENT") ? event.hitDieRoll() != null && event.hitDieRoll() >= 1 && event.hitDieRoll() <= command.restInput().hitDieSides() : event.hitDieRoll() == null);
            } else require(before.rest().equals(after.rest()) && before.abilities().equals(after.abilities()) && before.level() == after.level() && before.proficiencyBonus() == after.proficiencyBonus()
                    && before.hitPoints() == after.hitPoints() && before.maximumHitPoints() == after.maximumHitPoints() && before.classes().equals(after.classes()) && before.hitDicePools().equals(after.hitDicePools())
                    && before.subclasses().equals(after.subclasses()) && before.features().equals(after.features()) && before.feats().equals(after.feats()) && before.proficiencies().equals(after.proficiencies()) && event.hitDieRoll() == null);
        }
    }
    private static boolean sameCore(State before, State after) {
        return before.equals(new State(after.identity(), after.campaignId(), after.characterId(), after.preparationOperationId(), after.characterClass(),
                after.abilities(), after.level(), after.proficiencyBonus(), before.hitPoints(), after.maximumHitPoints(), after.classes(), after.hitDicePools(),
                after.subclasses(), after.features(), after.feats(), after.proficiencies(), before.version(), after.resources(), before.rest(), before.equipment(), before.health(), after.spellbook()));
    }
    private static boolean sameCoreExceptResources(State before, State after) {
        return before.equals(new State(after.identity(), after.campaignId(), after.characterId(), after.preparationOperationId(), after.characterClass(),
                after.abilities(), after.level(), after.proficiencyBonus(), after.hitPoints(), after.maximumHitPoints(), after.classes(), after.hitDicePools(),
                after.subclasses(), after.features(), after.feats(), after.proficiencies(), before.version(), before.resources(), after.rest(), after.equipment(), after.health(), after.spellbook()));
    }
    private static SpellbookState readSpellbook(ResultSet row,List<LearnedSpell> learned,List<RuleIdentity> prepared)throws SQLException {
        RuleIdentity casting=nullableRule(row,"spellbook_casting");
        if(casting==null) {
            require(row.getObject("spellbook_learned_level")==null && row.getObject("spellbook_prepared_rest_minute")==null
                    && row.getObject("spellbook_prepared_rest_second")==null && row.getObject("arcane_recovery_day")==null && learned.isEmpty() && prepared.isEmpty());return null;
        }
        return new SpellbookState(casting,integer(row,"spellbook_learned_level"),learned,prepared,
                row.getObject("spellbook_prepared_rest_minute")==null?null:number(row,"spellbook_prepared_rest_minute"),
                row.getObject("spellbook_prepared_rest_second")==null?null:integer(row,"spellbook_prepared_rest_second"),
                row.getObject("arcane_recovery_day")==null?null:number(row,"arcane_recovery_day"));
    }
    public static SpellbookState withArcaneRecoveryDay(SpellbookState book, Long day) {
        return new SpellbookState(book.castingRule(), book.learnedThroughLevel(), book.learned(), book.prepared(),
                book.preparedAtRestMinute(), book.preparedAtRestSecond(), day);
    }
    private static boolean sameRestExceptMinute(RestState before, RestState after) {
        return before.second()==after.second() && Objects.equals(before.lastLongRestEndMinute(), after.lastLongRestEndMinute())
                && Objects.equals(before.lastLongRestEndSecond(),after.lastLongRestEndSecond()) && Objects.equals(before.active(), after.active());
    }
    private static State withVersion(State state, long version) {
        return new State(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId(), state.characterClass(), state.abilities(),
                state.level(), state.proficiencyBonus(), state.hitPoints(), state.maximumHitPoints(), state.classes(), state.hitDicePools(), state.subclasses(),
                state.features(), state.feats(), state.proficiencies(), version, state.resources(), state.rest(), state.equipment(), state.health(), state.spellbook());
    }
    public static State withSpellbook(State s,long version,SpellbookState book) {
        return new State(s.identity(),s.campaignId(),s.characterId(),s.preparationOperationId(),s.characterClass(),s.abilities(),s.level(),s.proficiencyBonus(),s.hitPoints(),s.maximumHitPoints(),s.classes(),s.hitDicePools(),s.subclasses(),s.features(),s.feats(),s.proficiencies(),version,s.resources(),s.rest(),s.equipment(),s.health(),book);
    }
    private static void validateState(State state) throws SQLException {
        require(state != null && state.identity() != null); uuid(state.campaignId()); uuid(state.characterId()); uuid(state.preparationOperationId());
        require(new HashSet<>(List.of(state.identity().runId(), state.identity().snapshotId(), state.campaignId(), state.characterId(), state.preparationOperationId())).size() == 5);
        require(validRule(state.characterClass(), "character.class"));
        require(state.health() != null && state.equipment().size() <= MAX_EQUIPMENT);
        RestState rest = state.rest(); require(rest != null && rest.minute() >= 0 && rest.minute() <= 1000000000L && (rest.lastLongRestEndMinute() == null || rest.lastLongRestEndMinute() >= 0 && rest.lastLongRestEndMinute() <= rest.minute()));
        require(state.spellbook() == null || state.spellbook().lastArcaneRecoveryDay() == null
                || state.spellbook().lastArcaneRecoveryDay() <= rest.minute() / 1440);
        if (rest.active() != null) {
            RestAttempt attempt = rest.active(); require(validRule(attempt.rule(), "adventure.rest") && Set.of("SHORT_REST", "LONG_REST").contains(attempt.kind()) && attempt.startMinute() >= 0 && attempt.startMinute() <= rest.minute()
                    && attempt.hitPointsAtStart() >= 0 && attempt.hitPointsAtStart() <= 65535 && attempt.sleepMinutes() >= 0 && attempt.lightMinutes() >= 0 && attempt.strenuousMinutes() >= 0
                    && attempt.sleepMinutes() <= 1000000000L && attempt.lightMinutes() <= 1000000000L && attempt.strenuousMinutes() <= 1000000000L
                    && attempt.sleepMinutes() + attempt.lightMinutes() + attempt.strenuousMinutes() <= rest.minute() - attempt.startMinute()
                    && (attempt.lastActivity() == null || Set.of("SLEEP", "LIGHT", "STRENUOUS").contains(attempt.lastActivity())) && attempt.consecutiveStrenuousMinutes() >= 0 && attempt.consecutiveStrenuousMinutes() <= attempt.strenuousMinutes()
                    && ("IN_PROGRESS".equals(attempt.phase()) || "SPENDING_HIT_DICE".equals(attempt.phase()) && "SHORT_REST".equals(attempt.kind())));
        }
        HealthState health = state.health();
        if (health.dead()) require(state.hitPoints() == 0 && health.deathSuccesses() == 0 && health.deathFailures() == 0 && !health.stable() && health.stableRecoveryMinute() == null);
        else if (state.hitPoints() > 0) require(health.deathSuccesses() == 0 && health.deathFailures() == 0 && !health.stable() && health.stableRecoveryMinute() == null);
        else if (health.stable()) require(health.deathSuccesses() == 0 && health.deathFailures() == 0 && health.stableRecoveryMinute() != null && (health.stableRecoveryMinute() > rest.minute() || health.stableRecoveryMinute()==rest.minute() && health.stableRecoverySecond()>rest.second()));
        else require(health.stableRecoveryMinute() == null);
        String previousItem = null; int worn = 0, mainHand = 0, offHand = 0;
        for (EquipmentItem item : state.equipment()) {
            require(item != null && (previousItem == null || previousItem.compareTo(item.itemId().toString()) < 0)); previousItem = item.itemId().toString();
            if (item.location().equals("WORN")) worn++;
            if (Set.of("MAIN_HAND", "BOTH_HANDS").contains(item.location())) mainHand++;
            if (Set.of("OFF_HAND", "BOTH_HANDS").contains(item.location())) offHand++;
        }
        require(worn <= 1 && mainHand <= 1 && offHand <= 1);
        require(state.abilities().keySet().equals(Set.copyOf(ABILITIES)) && state.abilities().values().stream().allMatch(value -> value != null && value >= 1 && value <= 30));
        require(state.level() >= 1 && state.level() <= 20 && state.proficiencyBonus() == 2 + (state.level() - 1) / 4 && state.maximumHitPoints() >= 1 && state.maximumHitPoints() <= 65535 && state.hitPoints() >= 0 && state.hitPoints() <= state.maximumHitPoints()
                && state.version() >= 0 && state.version() < MAX_EVENTS && state.resources().size() <= MAX_RESOURCES && !state.classes().isEmpty() && state.classes().size() <= MAX_CLASSES
                && !state.hitDicePools().isEmpty() && state.hitDicePools().size() <= MAX_HIT_DICE_POOLS && state.subclasses().size() <= MAX_CLASSES && state.features().size() <= MAX_FEATURES && state.feats().size() <= MAX_FEATS && state.proficiencies().size() <= MAX_GRANTS);
        Map<RuleIdentity, Integer> levels = new HashMap<>(); RuleIdentity previousClass = null; int total = 0;
        for (ClassLevel value : state.classes()) { require(value != null && validRule(value.characterClass(), "character.class") && value.level() >= 1 && value.level() <= 20 && (previousClass == null || RULE_ORDER.compare(previousClass, value.characterClass()) < 0)); levels.put(value.characterClass(), value.level()); previousClass = value.characterClass(); total += value.level(); }
        require(total == state.level() && levels.containsKey(state.characterClass()));
        int previousSides = 0, maximumDice = 0;
        for (HitDicePool pool : state.hitDicePools()) { require(pool != null && Set.of(6, 8, 10, 12).contains(pool.sides()) && pool.sides() > previousSides && pool.maximum() >= 1 && pool.maximum() <= 20 && pool.current() >= 0 && pool.current() <= pool.maximum()); previousSides = pool.sides(); maximumDice += pool.maximum(); }
        require(maximumDice == total);
        Map<RuleIdentity, RuleIdentity> subclassOwners = new HashMap<>(); previousClass = null;
        for (SubclassState value : state.subclasses()) { require(value != null && validRule(value.characterClass(), "character.class") && validRule(value.subclass(), "character.subclass") && levels.containsKey(value.characterClass()) && value.acquiredAtClassLevel() >= 1 && value.acquiredAtClassLevel() <= levels.get(value.characterClass()) && (previousClass == null || RULE_ORDER.compare(previousClass, value.characterClass()) < 0) && subclassOwners.putIfAbsent(value.subclass(), value.characterClass()) == null); previousClass = value.characterClass(); }
        RuleIdentity previous = null;
        for (FeatureState value : state.features()) { require(value != null && validRule(value.feature(), "character.feature") && value.owner() != null && (validRule(value.owner(), "character.class") || validRule(value.owner(), "character.subclass"))); RuleIdentity owner = value.owner().ruleType().equals("character.class") ? value.owner() : subclassOwners.get(value.owner()); require(owner != null && levels.containsKey(owner) && value.acquiredAtClassLevel() >= 1 && value.acquiredAtClassLevel() <= levels.get(owner) && (previous == null || RULE_ORDER.compare(previous, value.feature()) < 0)); previous = value.feature(); }
        previous = null;
        for (FeatState value : state.feats()) { require(value != null && validRule(value.feat(), "character.feat") && validRule(value.characterClass(), "character.class") && levels.containsKey(value.characterClass()) && value.acquiredAtClassLevel() >= 1 && value.acquiredAtClassLevel() <= levels.get(value.characterClass()) && (previous == null || RULE_ORDER.compare(previous, value.feat()) < 0)); previous = value.feat(); }
        ProficiencyState previousGrant = null;
        for (ProficiencyState value : state.proficiencies()) { require(value != null && validProficiencySourceSlot(value.sourceSlot()) && validProficiency(value.target()) && value.acquiredAtTotalLevel() >= 1 && value.acquiredAtTotalLevel() <= state.level()); if (previousGrant != null) { int compare = SLOT_ORDER.compare(previousGrant.sourceSlot(), value.sourceSlot()); require(compare < 0 || compare == 0 && RULE_ORDER.compare(previousGrant.target(), value.target()) < 0); } previousGrant = value; }
        ResourceState previousResource = null;
        for (ResourceState resource : state.resources()) { require(resource != null && validRule(resource.identity(), "character.resource") && resource.current() >= 0 && resource.maximum() >= 0 && resource.maximum() <= 1000000 && resource.current() <= resource.maximum() && (!resource.unlimited() || resource.current() == 0 && resource.maximum() == 0)); require(previousResource == null || RESOURCE_ORDER.compare(previousResource, resource) < 0); previousResource = resource; }
    }
    private static Root root(State state) { return new Root(state.identity(), state.campaignId(), state.characterId(), state.preparationOperationId()); }
    private void insertRoot(Connection connection, Root root) throws SQLException {
        verifyPlan(connection, root); require(findByRun(connection, root.identity.runId()) == null);
        insert(connection, "s2_character_runtime", "character_id, campaign_id, run_id, snapshot_id, preparation_operation_id", bytes(root.character), bytes(root.campaign), root.identity.runBytes(), root.identity.snapshotBytes(), bytes(root.preparation));
    }
    private static void verifyPlan(Connection connection, Root root) throws SQLException {
        List<Boolean> plans = query(connection, "s2_initial_plan", "operation_id", bytes(root.preparation), "operation_id", 1, row -> {
            require(root.character.equals(id(row, "character_id")) && root.campaign.equals(id(row, "campaign_id"))
                    && root.identity.runId().equals(id(row, "run_id")) && root.identity.snapshotId().equals(id(row, "snapshot_id"))); return true;
        }); require(plans.size() == 1);
    }
    private static void insertEvent(Connection connection, Event event) throws SQLException {
        State state = event.after(); Command command = event.command(); GrowthChoices growth = command.growthChoices();
        List<Object> values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1, bytes(event.operationId()), ascii(event.requestDigest()), ascii(event.kind())));
        values.addAll(ruleValues(command.resource())); values.add(command.amount()); values.add(command.recoveryTrigger() == null ? null : ascii(command.recoveryTrigger())); values.add(command.hpChoice() == null ? null : ascii(command.hpChoice()));
        values.addAll(ruleValues(command.targetClass())); values.addAll(ruleValues(growth.subclass())); values.addAll(ruleValues(growth.feat())); values.add(growth.abilityIncreases().size()); values.add((int) growth.proficiencyChoices().values().stream().mapToLong(List::size).sum()); RestInput input = command.restInput();
        Collections.addAll(values, input == null || input.restKind() == null ? null : ascii(input.restKind()), input == null || input.activity() == null ? null : ascii(input.activity()), input == null ? 0L : input.minutes(), input == null ? 0 : input.hitDieSides(), input == null ? 0 : input.recoveredHitDice().size(), event.hitDieRoll(), input == null ? 0 : input.recoveredSpellSlots().size());
        insert(connection, "s2_character_event", "character_id, event_sequence, operation_id, request_digest, event_kind, resource_module, resource_type, resource_key, resource_amount, recovery_trigger, hp_choice, target_class_module, target_class_type, target_class_key, chosen_subclass_module, chosen_subclass_type, chosen_subclass_key, chosen_feat_module, chosen_feat_type, chosen_feat_key, ability_choice_count, proficiency_choice_count, rest_kind, rest_activity, rest_minutes, rest_hit_die_sides, recovered_hit_die_count, hit_die_roll, arcane_recovery_choice_count", values.toArray());
        if (input != null) for (var choice : input.recoveredSpellSlots().entrySet())
            insert(connection, "s2_character_arcane_recovery_choice", "character_id, event_sequence, slot_rank, recovered_value",
                    bytes(state.characterId()), state.version() + 1, choice.getKey(), choice.getValue());
        if(command.spellbookInput()!=null) {
            SpellbookInput book=command.spellbookInput();values=new ArrayList<>(List.of(bytes(state.characterId()),state.version()+1));values.addAll(ruleValues(book.castingRule()));Collections.addAll(values,book.learned().size(),book.prepared().size());
            insert(connection,"s2_character_spellbook_command","character_id, event_sequence, casting_module, casting_type, casting_key, learned_count, prepared_count",values.toArray());
            for(String kind:List.of("LEARNED","PREPARED")) {
                var spells=kind.equals("LEARNED")?book.learned():book.prepared();
                for(int n=0;n<spells.size();n++) {values=new ArrayList<>(List.of(bytes(state.characterId()),state.version()+1,ascii(kind),n));values.addAll(ruleValues(spells.get(n)));
                    insert(connection,"s2_character_spellbook_choice","character_id, event_sequence, choice_kind, spell_index, spell_module, spell_type, spell_key",values.toArray());}
            }
        }
        if (command.checkInput() != null) {
            CheckInput check = command.checkInput(); CheckResult result = event.checkResult();
            values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1));
            values.addAll(ruleValues(check.rule())); Collections.addAll(values, ascii(check.kind()), ascii(check.abilityKey())); values.addAll(ruleValues(check.proficiency()));
            Collections.addAll(values, check.difficultyClass(), check.advantage() ? 1 : 0, check.disadvantage() ? 1 : 0, check.adjudication(),
                    result.firstD20(), result.secondD20(), result.selectedD20(), result.abilityModifier(), result.proficiencyBonus(), result.total(), result.success() ? 1 : 0,
                    result.effectiveAdvantage() ? 1 : 0, result.effectiveDisadvantage() ? 1 : 0);
            insert(connection, "s2_character_check", "character_id, event_sequence, check_module, check_type, check_key, check_kind, ability_key, proficiency_module, proficiency_type, proficiency_key, difficulty_class, advantage, disadvantage, adjudication, first_d20, second_d20, selected_d20, ability_modifier, proficiency_bonus, total, success, effective_advantage, effective_disadvantage", values.toArray());
        }
        if (command.equipmentInput() != null) {
            EquipmentInput inputEquipment = command.equipmentInput(); values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1, bytes(inputEquipment.itemId())));
            values.addAll(ruleValues(inputEquipment.item())); Collections.addAll(values, inputEquipment.location() == null ? null : ascii(inputEquipment.location()), inputEquipment.adjudication());
            insert(connection, "s2_character_equipment_command", "character_id, event_sequence, item_id, item_module, item_type, item_key, location, adjudication", values.toArray());
        }
        if (command.healthInput() != null) {
            HealthInput healthInput = command.healthInput(); HealthResult result = event.healthResult(); values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1));
            values.addAll(ruleValues(healthInput.rule())); values.addAll(ruleValues(healthInput.effect()));
            Collections.addAll(values, healthInput.temporaryChoice() == null ? null : ascii(healthInput.temporaryChoice()), healthInput.adjudication(), result.dice().size(), result.deathD20(), result.recoveryD4(), result.rolledAmount(), result.appliedAmount());
            insert(connection, "s2_character_health_event", "character_id, event_sequence, health_module, health_type, health_key, effect_module, effect_type, effect_key, temporary_choice, adjudication, die_count, death_d20, recovery_d4, rolled_amount, applied_amount", values.toArray());
            for (int i = 0; i < result.dice().size(); i++) insert(connection, "s2_character_health_die", "character_id, event_sequence, die_index, die_value", bytes(state.characterId()), state.version() + 1, i, result.dice().get(i));
        }
        if (command.combatInput() != null) {
            CombatInput combat = command.combatInput();
            insert(connection, "s2_character_combat_input", "operation_id, character_id, event_sequence, combat_id, combat_operation_id, frame_key, actor_id, damage_amount, critical, seconds_advanced",
                    bytes(event.operationId()), bytes(state.characterId()), state.version() + 1, bytes(combat.combatId()),
                    bytes(combat.combatOperationId()), bytes(combat.frameKey()), bytes(combat.actorId()), combat.damageAmount(), combat.critical() ? 1 : 0, combat.secondsAdvanced());
        }
        int index = 0;
        for (var choice : growth.abilityIncreases().entrySet()) insert(connection, "s2_character_command_ability", "character_id, event_sequence, choice_index, ability_key, increase_value", bytes(state.characterId()), state.version() + 1, index++, ascii(choice.getKey()), choice.getValue());
        index = 0;
        for (var slot : growth.proficiencyChoices().entrySet()) for (RuleIdentity target : slot.getValue()) {
            values = new ArrayList<>(List.of(bytes(state.characterId()), state.version() + 1, index++)); values.addAll(slotValues(slot.getKey())); values.addAll(ruleValues(target));
            insert(connection, "s2_character_command_proficiency", "character_id, event_sequence, choice_index, " + SLOT_COLUMNS + ", target_module, target_type, target_key", values.toArray());
        }
        index = 0;
        if (input != null) for (var pool : input.recoveredHitDice().entrySet()) insert(connection, "s2_character_command_hit_die", "character_id, event_sequence, pool_index, sides, recovered_value", bytes(state.characterId()), state.version() + 1, index++, pool.getKey(), pool.getValue());
        if (event.before() != null) insertState(connection, state.version() + 1, "BEFORE", event.before());
        insertState(connection, state.version() + 1, "AFTER", state);
    }
    private static final String SLOT_COLUMNS = "source_module, source_type, source_key, collection_key, slot_module, slot_key";
    private static List<Object> ruleValues(RuleIdentity identity) { return identity == null ? Arrays.asList(null, null, null) : List.of(ascii(identity.moduleKey()), ascii(identity.ruleType()), ascii(identity.ruleKey())); }
    private static List<Object> slotValues(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot) { return List.of(ascii(slot.rule().moduleKey()), ascii(slot.rule().ruleType()), ascii(slot.rule().ruleKey()), ascii(slot.collectionKey()), ascii(slot.moduleKey()), ascii(slot.entryKey())); }
    private static com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot(ResultSet row) throws SQLException { return new com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity(rule(row, "source"), ascii(row, "collection_key", 32), ascii(row, "slot_module", 64), ascii(row, "slot_key", 64)); }
    private static void insertState(Connection connection, long sequence, String phase, State state) throws SQLException {
        List<Object> values = new ArrayList<>(List.of(bytes(state.characterId()), sequence, ascii(phase))); values.addAll(ruleValues(state.characterClass()));
        for (String ability : ABILITIES) values.add(state.abilities().get(ability));
        Collections.addAll(values, state.level(), state.proficiencyBonus(), state.hitPoints(), state.maximumHitPoints(), state.version(), state.resources().size(), state.classes().size(), state.hitDicePools().size(), state.subclasses().size(), state.features().size(), state.feats().size(), state.proficiencies().size());
        RestState rest = state.rest(); RestAttempt attempt = rest.active();
        Collections.addAll(values, rest.minute(), rest.lastLongRestEndMinute()); values.addAll(ruleValues(attempt == null ? null : attempt.rule()));
        Collections.addAll(values, attempt == null ? null : ascii(attempt.kind()), attempt == null ? null : attempt.startMinute(), attempt == null ? null : attempt.hitPointsAtStart(),
                attempt == null ? null : attempt.sleepMinutes(), attempt == null ? null : attempt.lightMinutes(), attempt == null ? null : attempt.strenuousMinutes(), attempt == null ? null : ascii(attempt.phase()),
                attempt == null || attempt.lastActivity() == null ? null : ascii(attempt.lastActivity()), attempt == null ? null : attempt.consecutiveStrenuousMinutes(), attempt == null ? null : attempt.interrupted() ? 1 : 0);
        HealthState health = state.health(); Collections.addAll(values, state.equipment().size(), health.temporaryHitPoints(), health.deathSuccesses(), health.deathFailures(), health.stable() ? 1 : 0, health.dead() ? 1 : 0, health.stableRecoveryMinute(), rest.second(), rest.lastLongRestEndSecond(), health.stableRecoverySecond());
        SpellbookState book=state.spellbook(); values.addAll(ruleValues(book==null?null:book.castingRule()));
        Collections.addAll(values,book==null?null:book.learnedThroughLevel(),book==null?0:book.learned().size(),book==null?0:book.prepared().size(),book==null?null:book.preparedAtRestMinute(),book==null?null:book.preparedAtRestSecond(),book==null?null:book.lastArcaneRecoveryDay());
        insert(connection, "s2_character_event_state", "character_id, event_sequence, phase, class_module, class_type, class_key, strength, dexterity, constitution, intelligence, wisdom, charisma, total_level, proficiency_bonus, hit_points, maximum_hit_points, row_version, resource_count, class_count, hit_die_count, subclass_count, feature_count, feat_count, proficiency_count, minute, last_long_rest_end_minute, rest_module, rest_type, rest_key, rest_kind, rest_start_minute, rest_start_hit_points, sleep_minutes, light_minutes, strenuous_minutes, rest_phase, last_activity, consecutive_strenuous_minutes, interrupted, equipment_count, temporary_hit_points, death_successes, death_failures, stable, dead, stable_recovery_minute, rest_second, last_long_rest_end_second, stable_recovery_second, spellbook_casting_module, spellbook_casting_type, spellbook_casting_key, spellbook_learned_level, learned_spell_count, prepared_spell_count, spellbook_prepared_rest_minute, spellbook_prepared_rest_second, arcane_recovery_day", values.toArray());
        int i = 0;
        if(book!=null) {
            for(LearnedSpell value:book.learned()) {values=childValues(state,sequence,phase,i++);values.addAll(ruleValues(value.spell()));values.add(value.acquiredAtClassLevel());
                insert(connection,"s2_character_event_learned_spell","character_id, event_sequence, phase, spell_index, spell_module, spell_type, spell_key, acquired_class_level",values.toArray());}
            i=0;
            for(RuleIdentity value:book.prepared()) {values=childValues(state,sequence,phase,i++);values.addAll(ruleValues(value));
                insert(connection,"s2_character_event_prepared_spell","character_id, event_sequence, phase, spell_index, spell_module, spell_type, spell_key",values.toArray());}
            i=0;
        }
        for (ResourceState value : state.resources()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.identity())); Collections.addAll(values, value.current(), value.maximum(), value.unlimited() ? 1 : 0); insert(connection, "s2_character_event_resource", "character_id, event_sequence, phase, resource_index, resource_module, resource_type, resource_key, current_value, maximum_value, unlimited", values.toArray()); }
        i = 0;
        for (ClassLevel value : state.classes()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.characterClass())); values.add(value.level()); insert(connection, "s2_character_event_class", "character_id, event_sequence, phase, class_index, class_module, class_type, class_key, class_level", values.toArray()); }
        i = 0;
        for (HitDicePool value : state.hitDicePools()) insert(connection, "s2_character_event_hit_die", "character_id, event_sequence, phase, pool_index, sides, current_value, maximum_value", bytes(state.characterId()), sequence, ascii(phase), i++, value.sides(), value.current(), value.maximum());
        i = 0;
        for (SubclassState value : state.subclasses()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.characterClass())); values.addAll(ruleValues(value.subclass())); values.add(value.acquiredAtClassLevel()); insert(connection, "s2_character_event_subclass", "character_id, event_sequence, phase, subclass_index, class_module, class_type, class_key, subclass_module, subclass_type, subclass_key, acquired_class_level", values.toArray()); }
        i = 0;
        for (FeatureState value : state.features()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.feature())); values.addAll(ruleValues(value.owner())); values.add(value.acquiredAtClassLevel()); insert(connection, "s2_character_event_feature", "character_id, event_sequence, phase, feature_index, feature_module, feature_type, feature_key, owner_module, owner_type, owner_key, acquired_class_level", values.toArray()); }
        i = 0;
        for (FeatState value : state.feats()) { values = childValues(state, sequence, phase, i++); values.addAll(ruleValues(value.feat())); values.addAll(ruleValues(value.characterClass())); values.add(value.acquiredAtClassLevel()); insert(connection, "s2_character_event_feat", "character_id, event_sequence, phase, feat_index, feat_module, feat_type, feat_key, class_module, class_type, class_key, acquired_class_level", values.toArray()); }
        i = 0;
        for (ProficiencyState value : state.proficiencies()) { values = childValues(state, sequence, phase, i++); values.addAll(slotValues(value.sourceSlot())); values.addAll(ruleValues(value.target())); values.add(value.acquiredAtTotalLevel()); insert(connection, "s2_character_event_proficiency", "character_id, event_sequence, phase, proficiency_index, " + SLOT_COLUMNS + ", target_module, target_type, target_key, acquired_total_level", values.toArray()); }
        i = 0;
        for (EquipmentItem value : state.equipment()) { values = childValues(state, sequence, phase, i++); values.add(bytes(value.itemId())); values.addAll(ruleValues(value.item())); Collections.addAll(values,ascii(value.location()),value.groundCombatId()==null?null:bytes(value.groundCombatId()),value.groundX(),value.groundY()); insert(connection, "s2_character_event_equipment", "character_id, event_sequence, phase, equipment_index, item_id, item_module, item_type, item_key, location, ground_combat_id, ground_x, ground_y", values.toArray()); }
    }
    private static List<Object> childValues(State state, long sequence, String phase, int index) { return new ArrayList<>(List.of(bytes(state.characterId()), sequence, ascii(phase), index)); }
    private static RestState readRest(ResultSet row) throws SQLException {
        RuleIdentity rule = nullableRule(row, "rest"); RestAttempt attempt = null;
        if (rule == null) {
            for (String column : List.of("rest_kind", "rest_start_minute", "rest_start_hit_points", "sleep_minutes", "light_minutes", "strenuous_minutes", "rest_phase", "last_activity", "consecutive_strenuous_minutes", "interrupted")) require(row.getObject(column) == null);
        } else {
            int interrupted = integer(row, "interrupted"); require(interrupted <= 1);
            attempt = new RestAttempt(rule, ascii(row, "rest_kind", 16), number(row, "rest_start_minute"), integer(row, "rest_start_hit_points"), number(row, "sleep_minutes"), number(row, "light_minutes"), number(row, "strenuous_minutes"), ascii(row, "rest_phase", 24), nullableAscii(row, "last_activity", 16), number(row, "consecutive_strenuous_minutes"), interrupted == 1);
        }
        return new RestState(number(row, "minute"),integer(row,"rest_second"), row.getObject("last_long_rest_end_minute") == null ? null : number(row, "last_long_rest_end_minute"), row.getObject("last_long_rest_end_second")==null?null:integer(row,"last_long_rest_end_second"), attempt);
    }
    private static HealthState readHealth(ResultSet row) throws SQLException {
        int stable = integer(row, "stable"), dead = integer(row, "dead"); require(stable <= 1 && dead <= 1);
        return new HealthState(integer(row, "temporary_hit_points"), integer(row, "death_successes"), integer(row, "death_failures"), stable == 1, dead == 1,
                row.getObject("stable_recovery_minute") == null ? null : number(row, "stable_recovery_minute"),row.getObject("stable_recovery_second")==null?null:integer(row,"stable_recovery_second"));
    }
    private static String text(ResultSet row, String key) throws SQLException { Object value = row.getObject(key); require(value instanceof String); return (String) value; }
    private static RuleIdentity nullableRule(ResultSet row, String prefix) throws SQLException { if (row.getObject(prefix + "_module") == null) { require(row.getObject(prefix + "_type") == null && row.getObject(prefix + "_key") == null); return null; } return rule(row, prefix); }
    private static RuleIdentity rule(ResultSet row, String prefix) throws SQLException { return new RuleIdentity(ascii(row, prefix + "_module", 64), ascii(row, prefix + "_type", 32), ascii(row, prefix + "_key", 128)); }
    private static void insert(Connection connection, String table, String columns, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table + " (" + columns + ") VALUES (" + String.join(", ", Collections.nCopies(values.length, "?")) + ")")) {
            statement.setQueryTimeout(5);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value == null) statement.setNull(i + 1, Types.NULL);
                else if (value instanceof byte[] binary) statement.setBytes(i + 1, binary);
                else if (value instanceof Integer number) statement.setInt(i + 1, number);
                else if (value instanceof Long number) statement.setLong(i + 1, number);
                else if (value instanceof String text) statement.setString(i + 1, text);
                else throw corrupt();
            } require(statement.executeUpdate() == 1);
        }
    }
    private static <T> List<T> query(Connection connection, String table, String key, byte[] value, String order, int maximum, Reader<T> reader) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + key + " = ? ORDER BY " + order + " LIMIT " + (maximum + 1))) {
            statement.setQueryTimeout(5); statement.setMaxRows(maximum + 1); statement.setBytes(1, value);
            try (ResultSet row = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (row.next()) { require(result.size() < maximum && Arrays.equals(binary(row, key), value));
                    try { result.add(reader.read(row)); } catch (IllegalArgumentException | NullPointerException malformed) { throw new SQLException("S2_CHARACTER_RUNTIME_CORRUPT", malformed); }
                } return result;
            }
        }
    }
    private static String ascii(ResultSet row, String key, int maximum) throws SQLException {
        byte[] value = binary(row, key); require(value.length >= 1 && value.length <= maximum);
        for (byte b : value) require(b >= 0x21 && b <= 0x7e); return new String(value, StandardCharsets.US_ASCII);
    }
    private static String nullableAscii(ResultSet row, String key, int maximum) throws SQLException { return row.getObject(key) == null ? null : ascii(row, key, maximum); }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static byte[] binary(ResultSet row, String key) throws SQLException { Object value = row.getObject(key); require(value instanceof byte[]); return (byte[]) value; }
    private static long number(ResultSet row, String key) throws SQLException { Object value = row.getObject(key); require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long); long result = ((Number) value).longValue(); require(result >= 0); return result; }
    private static int integer(ResultSet row, String key) throws SQLException { long value = number(row, key); require(value <= Integer.MAX_VALUE); return (int) value; }
    private static int signedInteger(ResultSet row, String key) throws SQLException { Object value = row.getObject(key); require(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long); long result = ((Number) value).longValue(); require(result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE); return (int) result; }
    private static byte[] bytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    private static UUID id(ResultSet row, String key) throws SQLException { byte[] value = binary(row, key); require(value.length == 16); ByteBuffer b = ByteBuffer.wrap(value); UUID id = new UUID(b.getLong(), b.getLong()); uuid(id); return id; }
    private static boolean validProficiency(RuleIdentity target) { return validRule(target, "character.proficiency") || validRule(target, "character.tool"); }
    private static boolean validProficiencySourceSlot(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot) {
        if (validSlot(slot)) return true;
        return slot != null && slot.rule() != null && Set.of("character.race", "character.subrace", "character.background", "character.class").contains(slot.rule().ruleType())
                && validRule(slot.rule(), slot.rule().ruleType()) && "proficiency_grants".equals(slot.collectionKey())
                && slot.moduleKey() != null && slot.moduleKey().matches("[a-z][a-z0-9_]{0,63}") && slot.entryKey() != null && slot.entryKey().matches("[a-z][a-z0-9_]{0,63}");
    }
    private static boolean validSlot(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot) {
        return slot != null && validRule(slot.rule(), "character.class_progression") && Set.of("starting_proficiencies", "multiclass_proficiencies").contains(slot.collectionKey())
                && slot.moduleKey() != null && slot.moduleKey().matches("[a-z][a-z0-9_]{0,63}") && slot.entryKey() != null && slot.entryKey().matches("[a-z][a-z0-9_]{0,63}");
    }
    private static boolean validRule(RuleIdentity identity, String type) {
        return identity != null && type.equals(identity.ruleType()) && identity.moduleKey() != null && identity.moduleKey().matches("[a-z][a-z0-9_]{0,63}")
                && identity.ruleKey() != null && identity.ruleKey().length() <= 128 && identity.ruleKey().matches("[a-z][a-z0-9_.]{0,127}")
                && (switch (type) { case "character.resource" -> validPrefix(identity.ruleKey(), "resource"); case "character.class_progression" -> validPrefix(identity.ruleKey(), "progression"); case "character.subclass" -> validPrefix(identity.ruleKey(), "subclass"); case "character.feature" -> validPrefix(identity.ruleKey(), "feature"); case "character.feat" -> validPrefix(identity.ruleKey(), "feat"); case "character.proficiency" -> validPrefix(identity.ruleKey(), "proficiency"); case "adventure.rest" -> validPrefix(identity.ruleKey(), "rest"); case "adventure.check" -> validPrefix(identity.ruleKey(), "check"); case "equipment.item" -> validPrefix(identity.ruleKey(), "item"); case "adventure.health" -> validPrefix(identity.ruleKey(), "health"); case "adventure.vital_effect" -> validPrefix(identity.ruleKey(), "vital_effect"); default -> true; });
    }
    private static boolean validAdjudication(String value) {
        if (value == null || !Normalizer.isNormalized(value, Normalizer.Form.NFC)
                || value.isBlank() || value.codePointCount(0, value.length()) > 500) return false;
        for (int index = 0; index < value.length();) {
            char unit = value.charAt(index);
            if (Character.isHighSurrogate(unit)) { if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) return false; }
            else if (Character.isLowSurrogate(unit)) return false;
            int point = value.codePointAt(index);
            if (point < 0x20 || point >= 0x7f && point <= 0x9f) return false;
            index += Character.charCount(point);
        }
        return true;
    }
    private static boolean validPrefix(String key, String prefix) { return key.matches(prefix + "\\.[a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)*"); }
    private static boolean validUuid(UUID value) { return value != null && value.version() == 4 && value.variant() == 2; }
    private static void uuid(UUID value) throws SQLException { require(validUuid(value)); }
    private static void require(boolean valid) throws SQLException { if (!valid) throw corrupt(); }
    private static SQLException corrupt() { return new SQLException("S2_CHARACTER_RUNTIME_CORRUPT"); }
    private record StateKey(long sequence, String phase) { }
    private record Header(UUID operation, String digest, String kind, RuleIdentity resource, long amount, String trigger, String hp, RuleIdentity targetClass, RuleIdentity subclass, RuleIdentity feat, int abilities, int proficiencies, String restKind, String activity, long minutes, int dieSides, int recoveredDice, Integer roll, int recoveredSlots) { }
    private record ChoiceTarget(com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity slot, RuleIdentity target) { }
    private record CheckFacts(CheckInput input, CheckResult result) { }
    private record HealthFacts(HealthInput input, HealthResult result) { }
    private record Root(Identity identity, UUID campaign, UUID character, UUID preparation) { }
    @FunctionalInterface private interface Reader<T> { T read(ResultSet row) throws SQLException; }
}
