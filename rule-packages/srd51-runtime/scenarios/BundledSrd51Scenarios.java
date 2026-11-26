import com.dndtool.service.Schema2CharacterCheckRules;
import com.dndtool.service.Schema2CharacterEquipmentRules;
import com.dndtool.service.Schema2CharacterHealthRules;
import com.dndtool.service.Schema2CharacterProgressionRules;
import com.dndtool.service.Schema2CharacterRuntime.CheckInput;
import com.dndtool.service.Schema2CharacterRuntime.Command;
import com.dndtool.service.Schema2CharacterRuntime.EquipmentInput;
import com.dndtool.service.Schema2CharacterRuntime.FeatureState;
import com.dndtool.service.Schema2CharacterRuntime.GrowthChoices;
import com.dndtool.service.Schema2CharacterRuntime.ResourceState;
import com.dndtool.service.Schema2CharacterRuntime.SpellbookInput;
import com.dndtool.service.Schema2CharacterRuntime.State;
import com.dndtool.service.Schema2CombatRules;
import com.dndtool.service.Schema2CombatState;
import com.dndtool.service.Schema2FeatureSupport;
import com.dndtool.service.Schema2InitialCharacterPlanService.InitialPlan;
import com.dndtool.service.Schema2PreparationIdentity;
import com.dndtool.service.Schema2SpellRules;
import com.dndtool.service.Schema2WizardSpellbookRules;
import com.dndtool.service.storage.CampaignRuleContext;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static com.dndtool.module.composition.Schema2CompositionKernel.*;

/** Authored expectations for the standard SRD 5.1 combination. */
public final class BundledSrd51Scenarios {
    private static final Map<String, Integer> ARRAY = Map.of("ability.strength", 15, "ability.dexterity", 14,
            "ability.constitution", 13, "ability.intelligence", 12, "ability.wisdom", 10, "ability.charisma", 8);

    private final CampaignRuleContext rules;

    private BundledSrd51Scenarios(CampaignRuleContext rules) {
        this.rules = rules;
    }

    public static void main(String[] args) {
        ModuleScenario.run(args, Map.ofEntries(
                scenario("class-templates", BundledSrd51Scenarios::verifyClassTemplates),
                scenario("fighter-growth", BundledSrd51Scenarios::verifyFighterGrowth),
                scenario("hill-dwarf-toughness", BundledSrd51Scenarios::verifyHillDwarfToughness),
                scenario("hill-dwarf-toughness-author-edit", BundledSrd51Scenarios::verifyHillDwarfToughnessAuthorEdit),
                scenario("stonecunning", BundledSrd51Scenarios::verifyStonecunning),
                scenario("stonecunning-variant", BundledSrd51Scenarios::verifyStonecunningVariant),
                scenario("fighter-extra-attack", BundledSrd51Scenarios::verifyFighterExtraAttack),
                scenario("cleric-growth-limit", BundledSrd51Scenarios::verifyClericGrowthLimit),
                scenario("wizard-spellbook", BundledSrd51Scenarios::verifyWizardSpellbook),
                scenario("equipment-profiles", BundledSrd51Scenarios::verifyEquipmentProfiles),
                scenario("plate-armor", BundledSrd51Scenarios::verifyPlateArmor),
                scenario("dwarf-heavy-armor-speed", BundledSrd51Scenarios::verifyDwarfHeavyArmorSpeed),
                scenario("dwarf-speed-author-edit", BundledSrd51Scenarios::verifyDwarfSpeedAuthorEdit),
                scenario("heavy-armor-policy-author-edit", BundledSrd51Scenarios::verifyHeavyArmorPolicyAuthorEdit),
                scenario("weapon-damage", BundledSrd51Scenarios::verifyWeaponDamage),
                scenario("monster-breath-profile", BundledSrd51Scenarios::verifyMonsterBreathProfile),
                scenario("rule-origins", BundledSrd51Scenarios::verifyRuleOrigins),
                scenario("wizard-evocation", BundledSrd51Scenarios::verifyWizardEvocation),
                scenario("burning-hands", BundledSrd51Scenarios::verifyBurningHands)));
    }

    private static Map.Entry<String, Consumer<CampaignRuleContext>> scenario(String id, Consumer<BundledSrd51Scenarios> check) {
        return Map.entry(id, rules -> check.accept(new BundledSrd51Scenarios(rules)));
    }

    private void verifyClassTemplates() {
        var growth = new Schema2CharacterProgressionRules();
        for (String characterClass : List.of("fighter", "cleric", "wizard")) {
            var plan = rules.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), characterClass, "custom_artisan");
            State state = growth.initial(rules.projection(), plan, UUID.randomUUID(), ARRAY, rules.initialChoices(characterClass));
            expectNumber(switch (characterClass) { case "fighter" -> 11; case "cleric" -> 9; default -> 7; }, state.maximumHitPoints());
            expectNumber(1, state.level());
            expectNumber(2, state.proficiencyBonus());
            assertEquals(rules.rule("character.class", "class." + characterClass), state.characterClass());
            assertTrue(plan.character().grants().stream().anyMatch(grant ->
                    grant.target().equals(rules.rule("character.tool", "tool.smith_tools"))));
            expectNumber(characterClass.equals("cleric") ? 1 : 0, state.subclasses().size());
            if (characterClass.equals("fighter")) {
                var secondWind = (FinalFeature) find("character.feature", "feature.fighter_second_wind");
                expectNumber(24, secondWind.definition().sourcePage());
                assertEquals(FeatureKind.FEATURE, secondWind.definition().kind());
                assertEquals(FeatureExecution.AUTOMATIC, secondWind.definition().executionMode());
                assertEquals(FIGHTER_SECOND_WIND_ALGORITHM, secondWind.definition().executionAlgorithm());
                assertEquals(List.of(new FeatureState(secondWind.identity(), state.characterClass(), 1)), state.features());
                var resource = rules.rule("character.resource", "resource.fighter.second_wind");
                assertEquals(List.of(new ResourceState(resource, 1, 1, false)), state.resources());
                assertTrue(rules.metadata().catalog().stream().anyMatch(row ->
                        row.key().equals(secondWind.identity().ruleKey()) && row.executable()));
            }
        }
        var acolyte = rules.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(), "class.cleric", "background.acolyte");
        expectNumber(4, acolyte.character().grants().size());
        rejection(() -> rules.initialChoices("bard"));
    }

    private void verifyFighterGrowth() {
        var material = rules.projection();
        var growth = new Schema2CharacterProgressionRules();
        RuleIdentity fighter = rules.rule("character.class", "class.fighter");
        var plan = initialPlan("fighter");
        State state = fighterAtLevelFour(growth, plan);
        expectNumber(36, state.maximumHitPoints());
        expectNumber(15, state.abilities().get("ability.constitution"));
        var critical = (FinalFeature) find("character.feature", "feature.champion_improved_critical");
        expectNumber(25, critical.definition().sourcePage());
        assertEquals(CHAMPION_IMPROVED_CRITICAL_ALGORITHM, critical.definition().executionAlgorithm());
        assertEquals(FeatureExecution.AUTOMATIC, critical.definition().executionMode());
        var secondWind = rules.rule("character.feature", "feature.fighter_second_wind");
        assertEquals(List.of(new FeatureState(critical.identity(), rules.rule("character.subclass", "subclass.champion"), 3),
                new FeatureState(secondWind, fighter, 1)), state.features());
        assertTrue(rules.metadata().catalog().stream().anyMatch(row -> row.key().equals(critical.identity().ruleKey()) && row.executable()));
        expectRejection("taking the first wizard level with Intelligence 12", "S2_GROWTH_PREREQUISITE",
                () -> growth.prepareAdvance(material, plan, state, rules.rule("character.class", "class.wizard"),
                        5, "FIXED_AVERAGE", GrowthChoices.EMPTY));
        expectNumber(4, state.level());
    }

    private void verifyHillDwarfToughness() {
        verifyDwarvenToughness(1, 1);
    }

    private void verifyHillDwarfToughnessAuthorEdit() {
        var trait = rules.rule("character.subrace_hit_points", "subrace_hit_points.hill_dwarf");
        for (Field field : List.of(Field.FIRST_LEVEL_BONUS, Field.PER_ADDITIONAL_LEVEL_BONUS)) {
            var chain = rules.projection().projection().chains().stream()
                    .filter(row -> row.target().equals(trait) && row.field() == field).findFirst().orElseThrow();
            assertEquals("dm_runtime_house_rules", chain.steps().getLast().provider().moduleKey());
        }
        verifyDwarvenToughness(2, 0);
    }

    private void verifyDwarvenToughness(int firstLevelBonus, int perAdditionalLevelBonus) {
        var race = rules.rule("character.race", "race.dwarf");
        var subrace = (FinalCharacterSource) find("character.subrace", "subrace.hill_dwarf");
        var trait = (FinalSubraceHitPoints) find("character.subrace_hit_points", "subrace_hit_points.hill_dwarf");
        assertEquals(race, subrace.definition().parentRace());
        assertEquals(subrace.identity(), trait.definition().owner());
        assertEquals(SUBRACE_HIT_POINTS_ALGORITHM, trait.definition().algorithm());
        expectNumber(4, trait.definition().sourcePage());
        expectNumber(firstLevelBonus, trait.definition().firstLevelBonus());
        expectNumber(perAdditionalLevelBonus, trait.definition().perAdditionalLevelBonus());

        UUID campaign = UUID.randomUUID();
        var identity = Schema2PreparationIdentity.random();
        var dwarf = rules.initialPlan(campaign, UUID.randomUUID(), identity, "fighter", "custom_artisan", "dwarf");
        var hillDwarf = rules.initialPlan(campaign, UUID.randomUUID(), identity, "fighter", "custom_artisan",
                "dwarf", "subrace.hill_dwarf");
        assertEquals(race, dwarf.character().sources().race());
        assertEquals(null, dwarf.character().sources().subrace());
        assertEquals(race, hillDwarf.character().sources().race());
        assertEquals(subrace.identity(), hillDwarf.character().sources().subrace());
        verifyToughnessGrowth(dwarf, 0, 0);
        verifyToughnessGrowth(hillDwarf, firstLevelBonus, perAdditionalLevelBonus);
    }

    private void verifyToughnessGrowth(InitialPlan plan, int firstLevelBonus, int perAdditionalLevelBonus) {
        var material = rules.projection();
        var growth = new Schema2CharacterProgressionRules();
        var first = initial(plan, "fighter");
        verifyHitPoints(first, 1, 11 + firstLevelBonus, 0);
        var injured = new Schema2CharacterHealthRules().applyDamage(material, first, 3, false).state();
        var second = advanceFighter(growth, plan, injured, 2, GrowthChoices.EMPTY);
        verifyHitPoints(second, 2, 18 + firstLevelBonus + perAdditionalLevelBonus, 3);
        var champion = new GrowthChoices(rules.rule("character.subclass", "subclass.champion"), Map.of(), null, Map.of());
        var rolled = growth.prepareAdvance(material, plan, second, first.characterClass(), 3, "SERVER_ROLL", champion);
        var third = growth.advance(material, rolled, 3);
        verifyHitPoints(third, 3, 22 + firstLevelBonus + 2 * perAdditionalLevelBonus, 3);
    }

    private static void verifyHitPoints(State state, int level, int maximum, int missing) {
        expectNumber(level, state.level());
        expectNumber(level, state.maximumHitDice());
        expectNumber(maximum, state.maximumHitPoints());
        expectNumber(maximum - missing, state.hitPoints());
        assertEquals(ARRAY, state.abilities());
    }

    private void verifyStonecunning() {
        var trait = (FinalStonecunning) find("character.stonecunning", "stonecunning.dwarf");
        var history = rules.rule("character.proficiency", "proficiency.skill.history");
        assertEquals(rules.rule("character.race", "race.dwarf"), trait.definition().owner());
        assertEquals(history, trait.definition().skill());
        expectNumber(3, trait.definition().sourcePage());
        expectNumber(2, trait.definition().proficiencyMultiplier());

        var dwarf = stonecunningPlan("fighter", "dwarf", null);
        State fighter = initial(dwarf, "fighter");
        assertTrue(fighter.proficiencies().stream().noneMatch(grant -> grant.target().equals(history)));
        verifyStoneworkCheck(dwarf, fighter, CheckInput.STONEWORK_ORIGIN, 4, trait.identity());
        verifyStoneworkCheck(dwarf, fighter, CheckInput.GENERAL, 0, null);

        var hillDwarf = stonecunningPlan("cleric", "dwarf", "subrace.hill_dwarf");
        State cleric = initial(hillDwarf, "cleric");
        assertTrue(cleric.proficiencies().stream().anyMatch(grant -> grant.target().equals(history)));
        verifyStoneworkCheck(hillDwarf, cleric, CheckInput.STONEWORK_ORIGIN, 4, trait.identity());
        verifyStoneworkCheck(hillDwarf, cleric, CheckInput.GENERAL, 2, null);

        var human = stonecunningPlan("cleric", "human", null);
        verifyStoneworkCheck(human, initial(human, "cleric"), CheckInput.STONEWORK_ORIGIN, 2, null);
    }

    private void verifyStonecunningVariant() {
        var trait = (FinalStonecunning) find("character.stonecunning", "stonecunning.dwarf");
        expectNumber(3, trait.definition().proficiencyMultiplier());
        var chain = rules.projection().projection().chains().stream()
                .filter(row -> row.target().equals(trait.identity()) && row.field() == Field.PROFICIENCY_MULTIPLIER)
                .findFirst().orElseThrow();
        assertEquals("dm_runtime_house_rules", chain.steps().getLast().provider().moduleKey());

        var dwarf = stonecunningPlan("fighter", "dwarf", "subrace.hill_dwarf");
        var growth = new Schema2CharacterProgressionRules();
        State fifth = advanceFighter(growth, dwarf, fighterAtLevelFour(growth, dwarf), 5, GrowthChoices.EMPTY);
        expectNumber(3, fifth.proficiencyBonus());
        verifyStoneworkCheck(dwarf, fifth, CheckInput.STONEWORK_ORIGIN, 9, trait.identity());
        verifyStoneworkCheck(dwarf, fifth, CheckInput.GENERAL, 0, null);
    }

    private InitialPlan stonecunningPlan(String characterClass, String race, String subrace) {
        return rules.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(),
                characterClass, "custom_artisan", race, subrace);
    }

    private void verifyStoneworkCheck(InitialPlan plan, State state, String subject, int bonus, RuleIdentity source) {
        var checks = new Schema2CharacterCheckRules();
        var input = new CheckInput(rules.checkRule(), "SKILL", "ability.intelligence",
                rules.rule("character.proficiency", "proficiency.skill.history"), 15, false, false,
                "The DM specifies the subject of the History check.", subject);
        var command = new Command("CHECK_RESOLVED", null, 0, null, null, null, GrowthChoices.EMPTY, null, input);
        var result = checks.resolve(checks.prepare(rules.projection(), plan, state, command), 10, null);
        expectNumber(10, result.firstD20());
        expectNumber(1, result.abilityModifier());
        expectNumber(state.proficiencyBonus(), result.baseProficiencyBonus());
        expectNumber(bonus, result.proficiencyBonus());
        expectNumber(11 + bonus, result.total());
        assertEquals(11 + bonus >= 15, result.success());
        assertEquals(source, result.stonecunningRule());
    }

    private void verifyFighterExtraAttack() {
        var material = rules.projection();
        var growth = new Schema2CharacterProgressionRules();
        var fighter = rules.rule("character.class", "class.fighter");
        var feature = (FinalFeature) find("character.feature", "feature.fighter_extra_attack");
        expectNumber(25, feature.definition().sourcePage());
        assertEquals(FIGHTER_EXTRA_ATTACK_ALGORITHM, feature.definition().executionAlgorithm());
        assertEquals(FeatureExecution.AUTOMATIC, feature.definition().executionMode());
        assertTrue(rules.metadata().catalog().stream().anyMatch(row -> row.key().equals(feature.identity().ruleKey()) && row.executable()));

        var plan = initialPlan("fighter");
        State fourth = fighterAtLevelFour(growth, plan);
        var improvedCritical = fourth.features().getFirst();
        var secondWind = fourth.features().getLast();
        expectNumber(1, Schema2FeatureSupport.attacksPerAttackAction(material, fourth));
        State fifth = advanceFighter(growth, plan, fourth, 5, GrowthChoices.EMPTY);
        expectNumber(3, fifth.proficiencyBonus());
        var acquired = new FeatureState(feature.identity(), fighter, 5);
        assertEquals(List.of(improvedCritical, acquired, secondWind), fifth.features());
        expectNumber(2, Schema2FeatureSupport.attacksPerAttackAction(material, fifth));
        State sixth = advanceFighter(growth, plan, fifth, 6, new GrowthChoices(null, Map.of("ability.strength", 2), null, Map.of()));
        assertEquals(fifth.features(), sixth.features());
        expectNumber(2, Schema2FeatureSupport.attacksPerAttackAction(material, sixth));
    }

    private void verifyClericGrowthLimit() {
        var growth = new Schema2CharacterProgressionRules();
        var plan = initialPlan("cleric");
        State cleric = initial(plan, "cleric");
        expectRejection("advancing the cleric to level two", "S2_GROWTH_LEVEL_UNSUPPORTED",
                () -> growth.prepareAdvance(rules.projection(), plan, cleric, rules.rule("character.class", "class.cleric"),
                        2, "FIXED_AVERAGE", GrowthChoices.EMPTY));
    }

    private void verifyWizardSpellbook() {
        var books = new Schema2WizardSpellbookRules();
        State wizard = initializeSpellbook(books, initial("wizard"));
        expectNumber(6, wizard.spellbook().learned().size());
        expectNumber(2, wizard.spellbook().prepared().size());
        books.validateState(rules.projection(), wizard);
        assertTrue(rules.metadata().catalog().stream().anyMatch(row -> row.key().equals("spell.burning_hands") && row.executable()));
        assertTrue(rules.metadata().catalog().stream().anyMatch(row -> row.key().equals("spell.alarm") && !row.executable()));
        assertTrue(rules.metadata().catalog().stream().anyMatch(row -> row.key().equals("feature.wizard_arcane_recovery") && row.executable()));
        rejection(() -> rules.rule("adventure.spell", "spell.fireball"));
    }

    private void verifyEquipmentProfiles() {
        FinalItem plate = (FinalItem) find("equipment.item", "item.plate");
        expectNumber(18, plate.definition().armorClass());
        expectNumber(15, plate.definition().strengthMinimum());
        FinalWeaponAttack greatsword = (FinalWeaponAttack) find("equipment.weapon_attack", "weapon_attack.greatsword");
        expectNumber(2, greatsword.definition().diceCount());
        expectNumber(6, greatsword.definition().diceSides());
        assertEquals(WeaponAbilityPolicy.STRENGTH, greatsword.definition().abilityPolicy());
        assertEquals(DamageType.SLASHING, greatsword.definition().damageType());
        FinalWeaponAttack rapier = (FinalWeaponAttack) find("equipment.weapon_attack", "weapon_attack.rapier");
        assertEquals(WeaponAbilityPolicy.FINESSE, rapier.definition().abilityPolicy());
    }

    private void verifyMonsterBreathProfile() {
        var breath = rules.projection().projection().entries().stream().map(FinalEntry::entry)
                .filter(MonsterActionEntry.class::isInstance).map(MonsterActionEntry.class::cast)
                .filter(row -> row.identity().entryKey().equals("fire_breath")).findFirst().orElseThrow().actionProfile();
        expectNumber(12, breath.savePolicy().dc());
        expectNumber(6, breath.damageProfile().diceCount());
        expectNumber(15, breath.area().lengthFeet());
        expectNumber(5, breath.recharge().minimumRoll());
    }

    private void verifyRuleOrigins() {
        assertEquals("dm_runtime_house_rules", rules.rule("adventure.environment", "environment.antimagic_zone").moduleKey());
        assertEquals("srd51_magic", rules.rule("adventure.spell", "spell.shield").moduleKey());
    }

    private void verifyPlateArmor() {
        State fighter = initial("fighter");
        var equipment = new Schema2CharacterEquipmentRules();
        UUID plate = UUID.randomUUID();
        fighter = equipment.apply(rules.projection(), fighter, equipmentCommand("ITEM_ACQUIRED", plate,
                rules.rule("equipment.item", "item.plate"), "CARRIED"), plate);
        fighter = equipment.apply(rules.projection(), fighter, equipmentCommand("ITEM_EQUIPPED", plate, null, "WORN"), UUID.randomUUID());
        expectNumber(18, equipment.derive(rules.projection(), fighter).armorClass());
    }

    private void verifyDwarfHeavyArmorSpeed() {
        verifyArmorPair(25, 25, 20);
    }

    private void verifyDwarfSpeedAuthorEdit() {
        verifyArmorPair(30, 30, 20);
    }

    private void verifyHeavyArmorPolicyAuthorEdit() {
        verifyArmorPair(25, 20, 30);
        armoredWizard(UUID.randomUUID(), UUID.randomUUID(), "dwarf", 14, 25, 25);
    }

    private void verifyArmorPair(int dwarfBaseSpeed, int dwarfArmoredSpeed, int humanArmoredSpeed) {
        var movement = (FinalMovement) find("character.movement", "movement.dwarf");
        assertEquals(rules.rule("character.race", "race.dwarf"), movement.definition().owner());
        assertEquals("SRD_2014_WALKING_SPEED_V1", movement.definition().algorithm());
        expectNumber(3, movement.definition().sourcePage());
        expectNumber(dwarfBaseSpeed, movement.definition().walkSpeedFeet());

        UUID campaign = UUID.randomUUID(), dwarf = UUID.randomUUID(), human = UUID.randomUUID();
        var roster = Map.of(dwarf, armoredWizard(campaign, dwarf, "dwarf", 8, dwarfBaseSpeed, dwarfArmoredSpeed),
                human, armoredWizard(campaign, human, "human", 8, 30, humanArmoredSpeed));
        verifyArmoredMovement(campaign, roster, dwarf, human, dwarfArmoredSpeed);
        verifyArmoredMovement(campaign, roster, human, dwarf, humanArmoredSpeed);
    }

    private Schema2CombatRules.Context armoredWizard(UUID campaign, UUID actor, String race, int strength,
            int baseSpeed, int armoredSpeed) {
        var material = rules.projection();
        var plan = rules.initialPlan(campaign, actor, Schema2PreparationIdentity.random(), "wizard", "custom_artisan", race);
        assertEquals(rules.rule("character.race", "race." + race), plan.character().sources().race());
        Map<String, Integer> abilities = new HashMap<>(rules.initialAbilities("wizard"));
        String strengthKey = "ability.strength";
        String oldAbility = abilities.entrySet().stream().filter(entry -> entry.getValue() == strength)
                .findFirst().orElseThrow().getKey();
        int oldStrength = abilities.put(strengthKey, strength);
        abilities.put(oldAbility, oldStrength);
        State initial = new Schema2CharacterProgressionRules().initial(material, plan, UUID.randomUUID(), abilities,
                rules.initialChoices("wizard"));
        expectNumber(strength, initial.abilities().get(strengthKey));
        State wizard = initializeSpellbook(new Schema2WizardSpellbookRules(), initial);
        var equipment = new Schema2CharacterEquipmentRules();
        var plate = rules.rule("equipment.item", "item.plate");
        UUID armor = UUID.randomUUID();
        State carrying = equipment.apply(material, wizard, equipmentCommand("ITEM_ACQUIRED", armor, plate, "CARRIED"), armor);
        expectNumber(baseSpeed, equipment.walkingSpeed(material, plan, carrying));
        State wearing = equipment.apply(material, carrying,
                equipmentCommand("ITEM_EQUIPPED", armor, null, "WORN"), UUID.randomUUID());
        expectNumber(armoredSpeed, equipment.walkingSpeed(material, plan, wearing));
        var armorEffects = equipment.derive(material, wearing);
        expectNumber(18, armorEffects.armorClass());
        assertTrue(armorEffects.castingForbidden());
        assertTrue(armorEffects.physicalCheckDisadvantage());
        assertTrue(armorEffects.stealthSkills().contains(rules.rule("character.proficiency", "proficiency.skill.stealth")));
        return new Schema2CombatRules.Context(material, plan, wearing);
    }

    private void verifyArmoredMovement(UUID campaign, Map<UUID, Schema2CombatRules.Context> roster,
            UUID actor, UUID opponent, int expectedSpeed) {
        var combat = new Schema2CombatRules();
        UUID combatId = UUID.randomUUID();
        int cells = expectedSpeed / 5;
        var battle = startBattle(combat, combatId, campaign, roster, actor, opponent,
                new Schema2CombatState.Position(2, cells), List.of(18, 14, 9, 5));
        assertEquals(List.of(14, 5), battle.initiative().stream().map(Schema2CombatState.Initiative::d20).toList());
        var path = IntStream.rangeClosed(1, cells + 1).mapToObj(y -> new Schema2CombatState.Position(0, y)).toList();
        expectRejection("moving five feet beyond armor-adjusted speed", "S2_COMBAT_MOVEMENT_ALLOWANCE", () -> combat.preflight(
                UUID.randomUUID(), combatId, campaign, roster, battle, Schema2CombatState.Command.move(actor, path)));
        var move = Schema2CombatState.Command.move(actor, path.subList(0, cells));
        var moved = combat.resolve(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, battle, move),
                sides -> { throw new AssertionError("Ordinary movement drew a die"); });
        assertEquals(Schema2CombatState.MovementStatus.COMPLETED, moved.after().flows().getLast().movement().status());

        var casting = rules.rule("character.wizard_spellcasting", "spellcasting.wizard");
        var burningHands = rules.rule("adventure.spell", "spell.burning_hands");
        var spell = new Schema2CombatState.CastInput(casting, burningHands, List.of(), null, 1, null, 0);
        expectRejection("casting after movement in untrained plate armor", "S2_SPELL_ARMOR", () -> combat.preflight(
                UUID.randomUUID(), combatId, campaign, roster, moved.after(), Schema2CombatState.Command.cast(actor, spell)));

        var dashed = turnAction(combat, combatId, campaign, roster, moved.after(), actor, Schema2CombatState.Kind.DASH);
        var mover = activeCombatant(dashed);
        assertEquals(path.get(cells - 1), mover.position());
        expectNumber(expectedSpeed * 2, mover.movementSpentHalfFeet());
        expectNumber(expectedSpeed, mover.movementBonus());
        var prone = turnAction(combat, combatId, campaign, roster, dashed, actor, Schema2CombatState.Kind.DROP_PRONE);
        assertTrue(activeCombatant(prone).prone());
        var standing = turnAction(combat, combatId, campaign, roster, prone, actor, Schema2CombatState.Kind.STAND_UP);
        var standingMover = activeCombatant(standing);
        assertEquals(false, standingMover.prone());
        expectNumber(expectedSpeed, standingMover.movementSpentHalfFeet() - mover.movementSpentHalfFeet());
    }

    private static Schema2CombatState.Combatant activeCombatant(Schema2CombatState.Battle battle) {
        return battle.combatants().stream().filter(row -> row.characterId().equals(battle.activeActor())).findFirst().orElseThrow();
    }

    private static Schema2CombatState.Battle turnAction(Schema2CombatRules combat, UUID combatId, UUID campaign,
            Map<UUID, Schema2CombatRules.Context> roster, Schema2CombatState.Battle battle, UUID actor, Schema2CombatState.Kind kind) {
        var command = Schema2CombatState.Command.action(kind, actor);
        return combat.resolve(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, battle, command),
                sides -> { throw new AssertionError(kind + " drew a die"); }).after();
    }

    private void verifyWeaponDamage() {
        var material = rules.projection();
        UUID campaign = UUID.randomUUID(), attacker = UUID.randomUUID(), target = UUID.randomUUID();
        Schema2PreparationIdentity identity = Schema2PreparationIdentity.random();
        var attackerPlan = rules.initialPlan(campaign, attacker, identity, "fighter", "custom_artisan");
        var targetPlan = rules.initialPlan(campaign, target, identity, "fighter", "custom_artisan");
        var equipment = new Schema2CharacterEquipmentRules();
        UUID greatsword = UUID.randomUUID();
        State carrying = equipment.apply(material, initial(attackerPlan, "fighter"), equipmentCommand("ITEM_ACQUIRED",
                greatsword, rules.rule("equipment.item", "item.greatsword"), "CARRIED"), greatsword);
        var oneHanded = equipmentCommand("ITEM_EQUIPPED", greatsword, null, "MAIN_HAND");
        expectRejection("equipping the greatsword in one hand", "S2_EQUIPMENT_HANDS", () ->
                equipment.apply(material, carrying, oneHanded, UUID.randomUUID()));
        State fighter = equipment.apply(material, carrying,
                equipmentCommand("ITEM_EQUIPPED", greatsword, null, "BOTH_HANDS"), UUID.randomUUID());
        var roster = Map.of(attacker, new Schema2CombatRules.Context(material, attackerPlan, fighter),
                target, new Schema2CombatRules.Context(material, targetPlan, initial(targetPlan, "fighter")));
        var combat = new Schema2CombatRules();
        UUID combatId = UUID.randomUUID();
        var battle = startBattle(combat, combatId, campaign, roster, attacker, target);
        var begin = Schema2CombatState.Command.action(Schema2CombatState.Kind.BEGIN_ATTACK, attacker);
        battle = combat.resolve(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, battle, begin),
                sides -> { throw new AssertionError("Beginning the attack drew a die"); }).after();
        var strike = Schema2CombatState.Command.attack(attacker, target, greatsword);
        var dice = new ArrayDeque<>(List.of(14, 3, 4));
        var result = combat.resolve(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, battle, strike),
                sides -> dice.removeFirst());
        assertTrue(dice.isEmpty());
        assertEquals(List.of(new Schema2CombatState.Roll("ATTACK_D20", attacker, 20, 14),
                new Schema2CombatState.Roll("DAMAGE", attacker, 6, 3),
                new Schema2CombatState.Roll("DAMAGE", attacker, 6, 4)), result.rolls());
        var attack = result.after().flows().getLast().attacks().getFirst();
        assertEquals(greatsword, attack.itemId());
        assertTrue(attack.hit());
        assertEquals(false, attack.critical());
        expectNumber(2, attack.abilityModifier());
        expectNumber(2, attack.proficiencyBonus());
        expectNumber(12, attack.finalArmorClass());
        assertEquals(List.of(3, 4), attack.damageDice());
        expectNumber(9, attack.damageAmount());
        expectNumber(1, result.healthChanges().size());
        var damage = result.healthChanges().getFirst();
        assertEquals(target, damage.characterId());
        expectNumber(11, damage.before().hitPoints());
        expectNumber(9, damage.healthResult().appliedAmount());
        expectNumber(2, damage.after().hitPoints());
    }

    private void verifyWizardEvocation() {
        var material = rules.projection();
        var growth = new Schema2CharacterProgressionRules();
        var books = new Schema2WizardSpellbookRules();
        RuleIdentity casting = rules.rule("character.wizard_spellcasting", "spellcasting.wizard");
        var plan = initialPlan("wizard");
        State wizard = initializeSpellbook(books, initial(plan, "wizard"));
        wizard = growth.advance(material, growth.prepareAdvance(material, plan, wizard,
                rules.rule("character.class", "class.wizard"), 2, "FIXED_AVERAGE",
                new GrowthChoices(rules.rule("character.subclass", "subclass.evocation"), Map.of(), null, Map.of())), null);
        wizard = books.apply(material, wizard, Command.spellbook("SPELLBOOK_ADVANCED", new SpellbookInput(casting,
                List.of(rules.rule("adventure.spell", "spell.detect_magic"), rules.rule("adventure.spell", "spell.disguise_self")),
                rules.initialWizardPrepared())));
        assertEquals(rules.rule("character.subclass", "subclass.evocation"), wizard.subclasses().getFirst().subclass());
        assertTrue(wizard.features().stream().noneMatch(row -> row.feature().equals(rules.rule("character.feature", "feature.sculpt_spells"))));
        books.validateState(material, wizard);
    }

    private void verifyBurningHands() {
        var material = rules.projection();
        UUID campaign = UUID.randomUUID(), wizardId = UUID.randomUUID(), fighterId = UUID.randomUUID();
        Schema2PreparationIdentity identity = Schema2PreparationIdentity.random();
        var wizardPlan = rules.initialPlan(campaign, wizardId, identity, "wizard", "custom_artisan");
        var fighterPlan = rules.initialPlan(campaign, fighterId, identity, "fighter", "custom_artisan");
        State wizard = initializeSpellbook(new Schema2WizardSpellbookRules(), initial(wizardPlan, "wizard"));
        State fighter = initial(fighterPlan, "fighter");
        var roster = Map.of(wizardId, new Schema2CombatRules.Context(material, wizardPlan, wizard),
                fighterId, new Schema2CombatRules.Context(material, fighterPlan, fighter));
        var combat = new Schema2CombatRules();
        UUID combatId = UUID.randomUUID();
        var battle = startBattle(combat, combatId, campaign, roster, wizardId, fighterId);
        RuleIdentity casting = rules.rule("character.wizard_spellcasting", "spellcasting.wizard");
        var burning = new Schema2CombatState.CastInput(casting, rules.rule("adventure.spell", "spell.burning_hands"), List.of(), null, 1, null, 0);
        var spell = new Schema2SpellRules().prepare(roster, battle, wizardId, burning);
        assertEquals(Schema2SpellRules.BURNING_HANDS, spell.spell().algorithm());
        Objects.requireNonNull(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, battle,
                Schema2CombatState.Command.cast(wizardId, burning)));
    }

    private State initializeSpellbook(Schema2WizardSpellbookRules books, State wizard) {
        return books.apply(rules.projection(), wizard, Command.spellbook("SPELLBOOK_INITIALIZED",
                new SpellbookInput(rules.rule("character.wizard_spellcasting", "spellcasting.wizard"),
                        rules.initialWizardBook(), rules.initialWizardPrepared())));
    }

    private static Schema2CombatState.Battle startBattle(Schema2CombatRules combat, UUID combatId, UUID campaign,
            Map<UUID, Schema2CombatRules.Context> roster, UUID actor, UUID target) {
        return startBattle(combat, combatId, campaign, roster, actor, target,
                new Schema2CombatState.Position(1, 0), List.of(18, 9));
    }

    private static Schema2CombatState.Battle startBattle(Schema2CombatRules combat, UUID combatId, UUID campaign,
            Map<UUID, Schema2CombatRules.Context> roster, UUID actor, UUID target,
            Schema2CombatState.Position targetPosition, List<Integer> initiativeDice) {
        var start = Schema2CombatState.Command.start(List.of(
                new Schema2CombatState.Deployment(actor, new Schema2CombatState.Position(0, 0), 0),
                new Schema2CombatState.Deployment(target, targetPosition, 1)), List.of(actor, target));
        var dice = new ArrayDeque<>(initiativeDice);
        var result = combat.resolve(combat.preflight(UUID.randomUUID(), combatId, campaign, roster, null, start),
                sides -> dice.removeFirst());
        assertTrue(dice.isEmpty());
        assertEquals(actor, result.after().activeActor());
        return result.after();
    }

    private static Command equipmentCommand(String kind, UUID item, RuleIdentity rule, String location) {
        return new Command(kind, null, 0, null, null, null, GrowthChoices.EMPTY, null, null,
                new EquipmentInput(item, rule, location, "DM confirms ownership and ordinary noncombat preparation."), null);
    }

    private State fighterAtLevelFour(Schema2CharacterProgressionRules growth, InitialPlan plan) {
        State state = advanceFighter(growth, plan, initial(plan, "fighter"), 2, GrowthChoices.EMPTY);
        var champion = new GrowthChoices(rules.rule("character.subclass", "subclass.champion"), Map.of(), null, Map.of());
        state = advanceFighter(growth, plan, state, 3, champion);
        return advanceFighter(growth, plan, state, 4, new GrowthChoices(null, Map.of("ability.constitution", 2), null, Map.of()));
    }

    private State advanceFighter(Schema2CharacterProgressionRules growth, InitialPlan plan, State state, int level, GrowthChoices choices) {
        var material = rules.projection();
        return growth.advance(material, growth.prepareAdvance(material, plan, state,
                rules.rule("character.class", "class.fighter"), level, "FIXED_AVERAGE", choices), null);
    }

    private InitialPlan initialPlan(String characterClass) {
        return rules.initialPlan(UUID.randomUUID(), UUID.randomUUID(), Schema2PreparationIdentity.random(),
                characterClass, "custom_artisan");
    }

    private State initial(String characterClass) {
        return initial(initialPlan(characterClass), characterClass);
    }

    private State initial(InitialPlan plan, String characterClass) {
        return new Schema2CharacterProgressionRules().initial(rules.projection(), plan, UUID.randomUUID(), ARRAY, rules.initialChoices(characterClass));
    }

    private FinalRule find(String type, String key) {
        return rules.projection().projection().rules().stream().filter(row -> row.identity().equals(rules.rule(type, key)))
                .findFirst().orElseThrow();
    }

    private static void expectNumber(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean matched) {
        if (!matched) throw new AssertionError("Expected the standard module condition to hold");
    }

    private static void expectRejection(String action, String expected, Runnable operation) {
        String actual;
        try {
            operation.run();
            actual = "success";
        } catch (IllegalArgumentException rejected) {
            actual = rejected.getMessage();
        }
        if (!expected.equals(actual)) {
            throw new AssertionError(action + " expected " + expected + ", got " + actual);
        }
    }

    private static IllegalArgumentException rejection(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException rejected) {
            return rejected;
        }
        throw new AssertionError("Expected the application to reject this choice");
    }
}
