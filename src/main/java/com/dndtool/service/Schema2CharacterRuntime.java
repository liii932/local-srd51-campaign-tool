package com.dndtool.service;

import com.dndtool.module.composition.Schema2CompositionKernel.EntryIdentity;
import com.dndtool.module.composition.Schema2CompositionKernel.RuleIdentity;
import java.text.Normalizer;
import java.util.*;

/** Typed character state, commands and immutable execution history. */
public final class Schema2CharacterRuntime {
    private Schema2CharacterRuntime() { }

    public static final int MAX_EVENTS = 128, MAX_RESOURCES = 32;
    public static final List<String> ABILITIES = List.of("ability.strength", "ability.dexterity", "ability.constitution", "ability.intelligence", "ability.wisdom", "ability.charisma");
    private static final Set<String> KINDS = Set.of("INITIALIZED", "CHARACTER_ADVANCED", "STATISTICS_ADVANCED", "RESOURCE_SPENT", "RESOURCES_RECOVERED", "REST_STARTED", "TIME_ADVANCED", "REST_COMPLETED", "HIT_DIE_SPENT", "REST_ENDED", "CHECK_RESOLVED", "ITEM_ACQUIRED", "ITEM_REMOVED", "ITEM_EQUIPPED", "ITEM_UNEQUIPPED", "HEALTH_EFFECT_RESOLVED", "DEATH_SAVE_RESOLVED", "COMBAT_DAMAGE_APPLIED", "COMBAT_DEATH_SAVE_RESOLVED", "COMBAT_TIME_ADVANCED", "COMBAT_RESOURCE_SPENT", "COMBAT_HEALING_APPLIED", "COMBAT_ENVIRONMENT_HEALTH", "SPELLBOOK_INITIALIZED", "SPELLBOOK_ADVANCED", "SPELLBOOK_PREPARED");
    private static final Comparator<RuleIdentity> RULE_ORDER = Comparator.comparing(RuleIdentity::moduleKey).thenComparing(RuleIdentity::ruleType).thenComparing(RuleIdentity::ruleKey);
    public static final Comparator<EntryIdentity> SLOT_ORDER = Comparator.comparing(EntryIdentity::rule, RULE_ORDER)
            .thenComparing(EntryIdentity::collectionKey).thenComparing(EntryIdentity::moduleKey).thenComparing(EntryIdentity::entryKey);
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
    public record ProficiencyState(EntryIdentity sourceSlot, RuleIdentity target, int acquiredAtTotalLevel) { }
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
    public record State(Schema2PreparationIdentity identity, UUID campaignId, UUID characterId, UUID preparationOperationId,
            RuleIdentity characterClass, Map<String, Integer> abilities, int level, int proficiencyBonus,
            long hitPoints, long maximumHitPoints, List<ClassLevel> classes, List<HitDicePool> hitDicePools,
            List<SubclassState> subclasses, List<FeatureState> features, List<FeatState> feats,
            List<ProficiencyState> proficiencies, long version, List<ResourceState> resources, RestState rest,
            List<EquipmentItem> equipment, HealthState health, SpellbookState spellbook) {
        public State(Schema2PreparationIdentity identity, UUID campaignId, UUID characterId, UUID preparationOperationId, RuleIdentity characterClass,
                Map<String,Integer> abilities,int level,int proficiencyBonus,long hitPoints,long maximumHitPoints,
                List<ClassLevel> classes,List<HitDicePool> hitDicePools,List<SubclassState> subclasses,List<FeatureState> features,
                List<FeatState> feats,List<ProficiencyState> proficiencies,long version,List<ResourceState> resources,RestState rest,
                List<EquipmentItem> equipment,HealthState health) {
            this(identity,campaignId,characterId,preparationOperationId,characterClass,abilities,level,proficiencyBonus,hitPoints,maximumHitPoints,
                classes,hitDicePools,subclasses,features,feats,proficiencies,version,resources,rest,equipment,health,null);
        }
        public State(Schema2PreparationIdentity identity, UUID campaignId, UUID characterId, UUID preparationOperationId, RuleIdentity characterClass,
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
            Map<EntryIdentity, List<RuleIdentity>> proficiencyChoices) {
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
            var choices = new TreeMap<EntryIdentity, List<RuleIdentity>>(SLOT_ORDER);
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

    public static void checkCandidateCapacity(History previous, State candidate) { checkCandidateCapacity(previous, candidate, null); }
    public static void checkCandidateCapacity(History previous, State candidate, Command command) {
        validateState(candidate);
        long rows = stateRows(candidate) + (previous == null ? 0 : stateRows(previous.current())) + (command == null ? MAX_GRANTS + 6 : commandRows(command));
        if (previous != null) {
            require(previous.events().size() < MAX_EVENTS);
            for (Event event : previous.events()) rows += stateRows(event.after()) + (event.before() == null ? 0 : stateRows(event.before())) + eventRows(event);
        }
        require(rows <= MAX_HISTORY_CHILD_ROWS);
    }
    /** Combat changes health, slot values, clock and equipment location without adding state rows. */
    public static void checkCombatCapacity(History history, int additionalEvents) {
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
    public static void validate(History history) {
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
    public static boolean restKind(String kind) { return kind != null && Set.of("REST_STARTED", "TIME_ADVANCED", "REST_COMPLETED", "HIT_DIE_SPENT", "REST_ENDED").contains(kind); }
    public static boolean equipmentKind(String kind) { return kind != null && Set.of("ITEM_ACQUIRED", "ITEM_REMOVED", "ITEM_EQUIPPED", "ITEM_UNEQUIPPED").contains(kind); }
    public static boolean combatKind(String kind) { return kind != null && Set.of("COMBAT_DAMAGE_APPLIED", "COMBAT_DEATH_SAVE_RESOLVED", "COMBAT_TIME_ADVANCED", "COMBAT_RESOURCE_SPENT", "COMBAT_HEALING_APPLIED", "COMBAT_ENVIRONMENT_HEALTH").contains(kind); }
    public static boolean healthKind(String kind) { return "HEALTH_EFFECT_RESOLVED".equals(kind) || "DEATH_SAVE_RESOLVED".equals(kind) || combatKind(kind) && !"COMBAT_RESOURCE_SPENT".equals(kind); }
    private static boolean advancement(String kind) { return kind.equals("CHARACTER_ADVANCED") || kind.equals("STATISTICS_ADVANCED"); }
    public static void validateEvent(Event event) {
        require(event != null && event.command() != null); uuid(event.operationId());
        require(event.requestDigest() != null && event.requestDigest().matches("[0-9a-f]{64}") && KINDS.contains(event.kind()));
        validateState(event.after()); State after = event.after(), before = event.before(); Command command = event.command();
        require("CHECK_RESOLVED".equals(event.kind()) == (event.checkResult() != null));
        require(healthKind(event.kind()) == (event.healthResult() != null));
        require(!Set.of(after.identity().runId(), after.identity().snapshotId(), after.campaignId(), after.characterId(), after.preparationOperationId()).contains(event.operationId()));
        if (before == null) require(event.kind().equals("INITIALIZED") && after.version() == 0 && after.level() == 1 && event.hitDieRoll() == null && after.equipment().isEmpty() && after.health().equals(HealthState.INITIAL) && after.spellbook()==null);
        else {
            validateState(before); require(!event.kind().equals("INITIALIZED") && sameRoot(before, after) && before.characterClass().equals(after.characterClass()) && after.version() == before.version() + 1);
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
    private static void validateState(State state) {
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
    private static boolean validProficiency(RuleIdentity target) { return validRule(target, "character.proficiency") || validRule(target, "character.tool"); }
    private static boolean validProficiencySourceSlot(EntryIdentity slot) {
        if (validSlot(slot)) return true;
        return slot != null && slot.rule() != null && Set.of("character.race", "character.subrace", "character.background", "character.class").contains(slot.rule().ruleType())
                && validRule(slot.rule(), slot.rule().ruleType()) && "proficiency_grants".equals(slot.collectionKey())
                && slot.moduleKey() != null && slot.moduleKey().matches("[a-z][a-z0-9_]{0,63}") && slot.entryKey() != null && slot.entryKey().matches("[a-z][a-z0-9_]{0,63}");
    }
    private static boolean validSlot(EntryIdentity slot) {
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
    public static boolean validUuid(UUID value) { return value != null && value.version() == 4 && value.variant() == 2; }
    private static void uuid(UUID value) { require(validUuid(value)); }
    private static void require(boolean valid) { if (!valid) throw new Schema2RuntimeFactsException("S2_CHARACTER_RUNTIME_CORRUPT"); }

    private static boolean sameRoot(State before, State after) {
        return before.identity().equals(after.identity()) && before.campaignId().equals(after.campaignId())
                && before.characterId().equals(after.characterId()) && before.preparationOperationId().equals(after.preparationOperationId());
    }
}
