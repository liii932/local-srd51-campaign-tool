# Abilities, checks, and saving throws

<a id="srd51-core-ability-scores"></a>
## Ability scores and modifiers

`srd51.core.ability-scores`

A creature has six abilities. Strength measures physical power, Dexterity agility and reflexes, Constitution endurance, Intelligence reason and memory, Wisdom perception and intuition, and Charisma personal presence and social effectiveness. The score represents capability including training. The ordinary human average is 10–11 and the usual upper human score is 18. Adventurers can reach 20 and monsters and divine beings can reach 30. The score-to-modifier domain is 1–30. Subtract 10, divide by 2, and round toward negative infinity to obtain the modifier. Thus scores 1 and 30 give −5 and +10, and a score of 9 gives −1. Abilities ordinarily affect [checks](#srd51-core-ability-check), [saves](#srd51-core-saving-throw), and [attacks](combat.md#srd51-core-attack-roll) through the modifier. Rules such as [carrying capacity](#srd51-core-lifting-and-carrying) and [jumping](exploration.md#srd51-core-jumping) can instead use the score itself. [SRD p. 76, Using Ability Scores and Ability Scores and Modifiers](../../references/srd-5.1/pages/page-076.md) · [SRD pp. 79–80, Lifting and Carrying](../../references/srd-5.1/pages/page-079.md) · [SRD p. 80, Lifting and Carrying continuation](../../references/srd-5.1/pages/page-080.md) · [SRD p. 85, Jumping](../../references/srd-5.1/pages/page-085.md)

<a id="srd51-core-advantage-disadvantage"></a>
## Advantage and disadvantage

`srd51.core.advantage-disadvantage`

For an ability check, saving throw, or attack roll, determine whether any applicable circumstance grants advantage and whether any imposes disadvantage. If only one applies, roll two d20s and take the higher for advantage or lower for disadvantage. If both apply or neither applies, roll a single d20. Multiple sources of the same kind never add more dice or outweigh an opposite source. When a rule allows a reroll and you rolled two d20s because of advantage or disadvantage, choose one eligible die to reroll. Features, spells, actions, inspiration, and GM adjudication can supply advantage or disadvantage. [SRD pp. 76–77, Advantage and Disadvantage](../../references/srd-5.1/pages/page-076.md) · [SRD p. 77, Advantage and Disadvantage continuation](../../references/srd-5.1/pages/page-077.md)

**Acceptance cases.** Two sources of advantage against one of disadvantage still yield one d20. A reroll that applies only to a result of 1 can replace a 1 among the two dice, but cannot replace both dice. A [passive check](#srd51-core-passive-check) changes by ±5 for uncanceled advantage or disadvantage instead of adding a die. [SRD p. 76, Advantage and Disadvantage](../../references/srd-5.1/pages/page-076.md) · [SRD p. 78, Passive Checks](../../references/srd-5.1/pages/page-078.md)

<a id="srd51-core-proficiency-bonus"></a>
## Proficiency bonus

`srd51.core.proficiency-bonus`

[Character advancement](character-advancement.md#srd51-character-advancement) supplies the proficiency bonus for a character's level. Monster stat blocks already incorporate their proficiency bonus where applicable. A single roll or other number receives the bonus at most once, even if several proficiencies or effects apply. If a rule permits multiplying or dividing the bonus, apply the scaling at most once. A multiplier for an ability check cannot supply a bonus to a check that would otherwise have no proficiency bonus. Attack rolls and saving throws ordinarily use an unscaled bonus, but a feature that permits scaling follows the same limits. [SRD p. 77, Proficiency Bonus](../../references/srd-5.1/pages/page-077.md) · [SRD p. 56, Character Advancement](../../references/srd-5.1/pages/page-056.md)

<a id="srd51-core-ability-check"></a>
## Ability checks

`srd51.core.ability-check`

The GM calls for a check for an attempted nonattack task whose success is uncertain. The GM chooses the relevant ability and sets the DC unless the governing rule supplies it. Typical DCs progress in steps of 5 from very easy at 5, through easy at 10, medium at 15, hard at 20, and very hard at 25, to nearly impossible at 30. Roll a d20 subject to advantage or disadvantage, add the ability modifier and any applicable proficiency, bonuses, or penalties, and succeed if the total reaches or exceeds the DC. Failure means either no progress or progress with a setback selected by the GM. Specific actions and effects may define their own outcomes. [SRD p. 77, Ability Checks and Typical Difficulty Classes](../../references/srd-5.1/pages/page-077.md)

**Acceptance case.** A total of 15 against DC 15 succeeds. With no applicable proficiency and an ability modifier of +4, a d20 result of 20 totals 24, which fails against DC 25. The automatic-hit rule for attack rolls does not apply to this check. [SRD p. 77, Ability Checks](../../references/srd-5.1/pages/page-077.md) · [SRD p. 94, Rolling 1 or 20](../../references/srd-5.1/pages/page-094.md)

<a id="srd51-core-contest"></a>
## Contests

`srd51.core.contest`

Use a contest for directly opposed efforts, such as competing for an objective only one participant can claim or resisting another participant's action. Each participant makes the check appropriate to their own effort with the usual modifiers. Compare the two totals in place of a fixed DC. The higher total wins and either accomplishes or prevents the action. A tie preserves the state before the contest, which can favor the participant defending that state. [SRD p. 77, Contests](../../references/srd-5.1/pages/page-077.md)

<a id="srd51-core-skill-proficiency"></a>
## Skill proficiency

`srd51.core.skill-proficiency`

An applicable skill specifies the kind of task within an ability check. The GM can request a skill or approve a player's proposal. A creature proficient in that skill applies its proficiency bonus. A creature without that proficiency still makes the underlying ability check unless the task has an independent prerequisite. Character creation grants starting skill proficiencies, and monsters list applicable skills in their stat blocks. The normal pairings and uses appear under each ability below. Constitution has no default associated skill. [SRD pp. 77–78, Skills](../../references/srd-5.1/pages/page-077.md) · [SRD p. 78, Skills continuation](../../references/srd-5.1/pages/page-078.md)

<a id="srd51-core-skills-with-different-abilities"></a>
### Variant: Skills with Different Abilities

`srd51.core.skills-with-different-abilities`

Under this variant, the GM may request or allow an unusual ability and skill pairing appropriate to the task. Use the modifier of the named ability and apply the skill proficiency normally. For example, if the difficulty of a sustained row is stamina and the GM allows Constitution (Athletics), use Constitution with proficiency in Athletics. Athletics does not by default apply to Constitution checks. [SRD p. 78, Variant: Skills with Different Abilities](../../references/srd-5.1/pages/page-078.md)

<a id="srd51-core-passive-check"></a>
## Passive checks

`srd51.core.passive-check`

A passive check uses a score of 10 plus all modifiers to the ordinary check and uses no roll. Add 5 for advantage or subtract 5 for disadvantage, applying normal cancellation. A passive score can represent repeated performance or let the GM determine an outcome without revealing that a check is taking place. Hiding and exploration can specifically compare against passive Wisdom (Perception). For a Wisdom modifier of +1 and applicable proficiency of +3, the passive score is 14, or 9 with uncanceled disadvantage. [SRD pp. 78–79, Passive Checks](../../references/srd-5.1/pages/page-078.md) · [SRD p. 79, Passive Checks continuation](../../references/srd-5.1/pages/page-079.md) · [SRD p. 81, Passive Perception](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-working-together"></a>
## Working together

`srd51.core.working-together`

When collaboration is productive and a helper could attempt the task independently, the participant leading the effort or with the highest relevant ability modifier can make the check with advantage. In combat, task assistance requires the [Help action](combat.md#srd51-core-help). Where a task has a proficiency prerequisite, the helper must also meet it. For example, a creature without thieves' tools proficiency cannot help pick a lock that requires it. A task that cannot be made easier by sharing the effort also receives no such help. [SRD p. 79, Working Together](../../references/srd-5.1/pages/page-079.md)

<a id="srd51-core-group-check"></a>
## Group checks

`srd51.core.group-check`

The GM can choose a group check when the group faces a common outcome. Every member makes the check. If at least half succeed, the group succeeds. Otherwise, the group fails. For five characters crossing unstable ground as a group, three individual successes suffice and two do not. [SRD p. 79, Group Checks](../../references/srd-5.1/pages/page-079.md)

<a id="srd51-core-strength"></a>
## Strength

`srd51.core.strength`

Apply a Strength check to uncertain feats of brute force, such as forcing a jammed object open, breaking restraints, lifting or holding a load, or forcing the body through a tight space. Strength is the normal modifier for [melee weapon attack and damage rolls](combat.md#srd51-core-attack-roll). Specific weapon properties may override that choice. [SRD p. 79, Strength Checks and Attack Rolls and Damage](../../references/srd-5.1/pages/page-079.md) · [SRD p. 94, Modifiers to the Roll](../../references/srd-5.1/pages/page-094.md)

<a id="srd51-core-athletics"></a>
### Athletics

`srd51.core.athletics`

Strength (Athletics) applies to challenging climbs, jumps, or swims, including holding on against interference, a leap beyond ordinary capability or with a stunt, and keeping afloat or resisting submersion in dangerous or opposed conditions. The ordinary costs and distances are in [special movement](exploration.md#srd51-core-special-movement). [SRD p. 79, Athletics](../../references/srd-5.1/pages/page-079.md)

<a id="srd51-core-lifting-and-carrying"></a>
### Lifting and carrying

`srd51.core.lifting-and-carrying`

For a Small or Medium creature, multiply Strength score by 15 to find carrying capacity in pounds. The push, drag, and lift limit is twice carrying capacity. For Tiny creatures halve both weights. For Large, Huge, and Gargantuan creatures use 2, 4, and 8 times the Medium values respectively. While pushing or dragging above carrying capacity but within the push/drag limit, speed drops to 5 feet. That provision is specific to pushing or dragging. For a Large creature with Strength 12, carrying capacity is 360 pounds and the push, drag, or lift limit is 720 pounds. Dragging 361 pounds invokes the speed provision. [SRD pp. 79–80, Lifting and Carrying](../../references/srd-5.1/pages/page-079.md) · [SRD p. 80, Push, Drag, or Lift and Size and Strength](../../references/srd-5.1/pages/page-080.md)

<a id="srd51-core-encumbrance"></a>
### Variant: Encumbrance

`srd51.core.encumbrance`

When this optional variant is used, ignore the Armor table's Strength column. Carried weight above 5 × Strength score reduces speed by 10 feet while encumbered. Above 10 × Strength score and up to carrying capacity, the creature is instead heavily encumbered, reducing speed by 20 feet and imposing disadvantage on checks, attacks, and saves that use Strength, Dexterity, or Constitution. The thresholds are strict. At Strength 10, a Medium creature carrying 50 pounds incurs neither penalty, carrying 100 pounds incurs the 10-foot reduction, and carrying 101 pounds incurs the heavy penalty. The source specifies the variant thresholds by Strength score and refers separately to maximum carrying capacity. [SRD p. 80, Variant: Encumbrance](../../references/srd-5.1/pages/page-080.md)

<a id="srd51-core-dexterity"></a>
## Dexterity

`srd51.core.dexterity`

A Dexterity check covers uncertain quick, quiet, or precise movement and maintaining footing. Other uses can include steering under demanding conditions, picking locks, disabling traps, tying or slipping bonds, manipulating an instrument, or performing fine craftwork. Dexterity normally supplies the ability modifier for ranged weapon attacks and damage, and is also an option for a finesse melee weapon. Armor can allow part or all of the Dexterity modifier to affect AC. A Dexterity check sets [initiative](combat.md#srd51-core-initiative). [SRD p. 80, Dexterity Checks, Attack Rolls and Damage, Armor Class, and Initiative](../../references/srd-5.1/pages/page-080.md)

<a id="srd51-core-acrobatics"></a>
### Acrobatics

`srd51.core.acrobatics`

Dexterity (Acrobatics) applies to balance and difficult footing, including unstable or slippery surfaces, and may be called for when attempting acrobatic stunts. [SRD p. 80, Acrobatics](../../references/srd-5.1/pages/page-080.md)

<a id="srd51-core-sleight-of-hand"></a>
### Sleight of Hand

`srd51.core.sleight-of-hand`

Dexterity (Sleight of Hand) applies to manual deception or concealment, such as planting an item unnoticed, hiding it on your person, or removing another creature's possessions without detection. [SRD p. 80, Sleight of Hand](../../references/srd-5.1/pages/page-080.md)

<a id="srd51-core-stealth"></a>
### Stealth

`srd51.core.stealth`

Dexterity (Stealth) applies when a creature tries to conceal itself, slip past observers, depart unnoticed, or approach without being heard or seen. See [hiding](#srd51-core-hiding) for the result's lifetime and how other creatures detect it. [SRD pp. 80–81, Stealth and Hiding](../../references/srd-5.1/pages/page-080.md) · [SRD p. 81, Hiding continuation](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-hiding"></a>
### Hiding

`srd51.core.hiding`

The GM decides whether circumstances support hiding. A creature that sees you clearly cannot be hidden from by this procedure. An [invisible](conditions.md#srd51-condition-invisible) creature can always try, but its sound and signs of passage can reveal it. On an attempt make Dexterity (Stealth). Keep the result until discovered or you cease hiding. An active searcher's Wisdom (Perception) check contests that result. For an observer that is not searching, compare it with the observer's [passive Wisdom (Perception)](#srd51-core-passive-check). Your noise can give away your position. Light and obscuration affect what can be found by sight. [SRD pp. 80–81, Hiding](../../references/srd-5.1/pages/page-080.md) · [SRD p. 81, Hiding continuation and Passive Perception](../../references/srd-5.1/pages/page-081.md)

**Project interpretation.** Treat a passive Wisdom (Perception) score that equals the Stealth total as noticing the hider. The source asks for a comparison without stating the equality result. This reading treats the Stealth total as a threshold to detect and follows the ordinary rule that a check reaching a DC succeeds. Another reading treats passive detection as a contest and preserves the previous state on equality. The source explicitly calls active searching a contest, so an active search that ties a previously hidden creature's Stealth total leaves it hidden. [SRD p. 81, Hiding and Passive Perception](../../references/srd-5.1/pages/page-081.md) · [SRD p. 77, Ability Checks and Contests](../../references/srd-5.1/pages/page-077.md)

**Acceptance case.** A creature is hidden from a guard with passive Wisdom (Perception) 13 after obtaining Stealth 15. The guard's active search total of 15 leaves the situation unchanged, but a later search with a total of 16 detects the hider. A separate observer with passive Wisdom (Perception) 15 notices the hider on equality under the passive interpretation. [SRD p. 81, Hiding and Passive Perception](../../references/srd-5.1/pages/page-081.md) · [SRD p. 77, Contests](../../references/srd-5.1/pages/page-077.md)

Most combatants remain alert and will see you when you leave hiding to approach them. The GM may allow an approach to a distracted creature while still hidden, which can yield an advantaged attack before discovery. See [Hide](combat.md#srd51-core-hide) and [Unseen Attackers and Targets](combat.md#srd51-core-unseen-attackers-and-targets) for the combat action and revelation upon attacking. [SRD p. 81, Hiding continuation](../../references/srd-5.1/pages/page-081.md) · [SRD p. 93, Hide](../../references/srd-5.1/pages/page-093.md) · [SRD p. 94, Unseen Attackers and Targets](../../references/srd-5.1/pages/page-094.md)

<a id="srd51-core-constitution"></a>
## Constitution

`srd51.core.constitution`

Constitution checks are uncommon and no skill is normally paired with them. The GM can call for one when pushing beyond normal endurance, including extended breath-holding, marching or labor without rest, sleep deprivation, going without food or water, or drinking a large quantity at once. Use the specific [environmental rules](exploration.md#srd51-core-suffocating) where their conditions apply. [SRD p. 81, Constitution Checks](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-constitution-and-hit-points"></a>
### Constitution and hit points

`srd51.core.constitution-and-hit-points`

Normally add the Constitution modifier when rolling a Hit Die to determine hit points. If the modifier changes, adjust the hit point maximum as though the current modifier had applied since 1st level. Thus a 6th-level character whose modifier goes up by 1 increases their maximum by 6, and a change that reduces the modifier by 1 reduces the maximum by 6. When leveling and changing the modifier together, use the new modifier for the new level and apply the difference to the previous levels. [SRD p. 81, Hit Points](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-intelligence"></a>
## Intelligence

`srd51.core.intelligence`

Intelligence checks cover learning, reasoning, and recall. The GM may use a general Intelligence check for tasks such as nonverbal communication, appraising valuables, assembling a disguise, forging a record, remembering a trade fact, or playing a game of skill. Wizards use Intelligence as their [spellcasting ability](spellcasting.md#srd51-core-spell-saving-throw). [SRD p. 81, Intelligence Checks](../../references/srd-5.1/pages/page-081.md) · [SRD p. 82, Intelligence Spellcasting Ability](../../references/srd-5.1/pages/page-082.md)

<a id="srd51-core-arcana"></a>
### Arcana

`srd51.core.arcana`

Intelligence (Arcana) recalls magical lore such as spells, magic items, occult symbols and traditions, other planes, and planar inhabitants. [SRD p. 81, Arcana](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-history"></a>
### History

`srd51.core.history`

Intelligence (History) recalls events, famous figures, kingdoms, disputes and wars, and past civilizations. [SRD p. 81, History](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-investigation"></a>
### Investigation

`srd51.core.investigation`

Intelligence (Investigation) finds and interprets clues to make deductions, such as locating a hidden object from evidence, identifying the weapon that caused a wound, discovering a structural weakness, or researching a particular fact in documents. [SRD p. 81, Investigation](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-nature"></a>
### Nature

`srd51.core.nature`

Intelligence (Nature) recalls lore about landforms, plants, animals, weather, and natural cycles. [SRD p. 81, Nature](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-religion"></a>
### Religion

`srd51.core.religion`

Intelligence (Religion) recalls lore about gods, religious rites, hierarchies and symbols, and the practices of cults. [SRD p. 81, Religion](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-wisdom"></a>
## Wisdom

`srd51.core.wisdom`

Wisdom checks cover attunement to one's surroundings, interpreting behavior and emotion, and attending to the injured. The GM can call for a general Wisdom check for an intuitive course of action or discerning if an apparently dead or living creature is undead. Clerics, druids, and rangers use Wisdom as their [spellcasting ability](spellcasting.md#srd51-core-spell-saving-throw). [SRD p. 82, Wisdom Checks and Wisdom Spellcasting Ability](../../references/srd-5.1/pages/page-082.md)

<a id="srd51-core-animal-handling"></a>
### Animal Handling

`srd51.core.animal-handling`

Wisdom (Animal Handling) can resolve uncertainty about calming a domestic animal, preventing a mount from becoming frightened, or interpreting an animal's intentions. Use the check when attempting a risky maneuver while controlling a mount. [SRD p. 82, Animal Handling](../../references/srd-5.1/pages/page-082.md)

<a id="srd51-core-insight"></a>
### Insight

`srd51.core.insight`

Wisdom (Insight) reads intentions from behavior such as body language, speech, and changes in habits. It can reveal deception or help anticipate another creature's next move. [SRD p. 82, Insight](../../references/srd-5.1/pages/page-082.md)

<a id="srd51-core-medicine"></a>
### Medicine

`srd51.core.medicine`

Wisdom (Medicine) can diagnose an illness or attempt to [stabilize a dying creature](combat.md#srd51-core-stabilizing-a-creature). [SRD p. 82, Medicine](../../references/srd-5.1/pages/page-082.md)

<a id="srd51-core-perception"></a>
### Perception

`srd51.core.perception`

Wisdom (Perception) detects the presence of things using available senses. It includes listening through barriers or for quiet movement, and spotting concealed or easily missed details or threats. The task and [visibility](exploration.md#srd51-core-vision-and-light) determine which senses are relevant. [SRD p. 82, Perception](../../references/srd-5.1/pages/page-082.md) · [SRD p. 81, What Can You See?](../../references/srd-5.1/pages/page-081.md)

<a id="srd51-core-survival"></a>
### Survival

`srd51.core.survival`

Wisdom (Survival) covers tracking, hunting, guiding a party through wilderness, recognizing signs of local creatures, forecasting weather, and avoiding natural hazards. [SRD p. 82, Survival](../../references/srd-5.1/pages/page-082.md)

<a id="srd51-core-charisma"></a>
## Charisma

`srd51.core.charisma`

Charisma checks cover social influence, entertaining others, leaving an impression, deception, or navigating social uncertainty. The GM can use a general Charisma check to locate someone with information or blend into a crowd to learn what people discuss. Bards, paladins, sorcerers, and warlocks use Charisma as their [spellcasting ability](spellcasting.md#srd51-core-spell-saving-throw). [SRD pp. 82–83, Charisma Checks and Charisma Spellcasting Ability](../../references/srd-5.1/pages/page-082.md) · [SRD p. 83, Charisma Checks continuation and Spellcasting Ability](../../references/srd-5.1/pages/page-083.md)

<a id="srd51-core-deception"></a>
### Deception

`srd51.core.deception`

Charisma (Deception) conceals truth through words or actions, including misleading phrasing, false assurances, convincing lies, maintaining a disguise socially, or misleading participants in a wager. [SRD p. 82, Deception](../../references/srd-5.1/pages/page-082.md)

<a id="srd51-core-intimidation"></a>
### Intimidation

`srd51.core.intimidation`

Charisma (Intimidation) tries to influence another creature through threats, hostile conduct, or violence. The [variant skill pairing](#srd51-core-skills-with-different-abilities) can use another ability if justified by the attempt. [SRD p. 82, Intimidation](../../references/srd-5.1/pages/page-082.md) · [SRD p. 78, Variant: Skills with Different Abilities](../../references/srd-5.1/pages/page-078.md)

<a id="srd51-core-performance"></a>
### Performance

`srd51.core.performance`

Charisma (Performance) determines the ability to entertain an audience, whether by music, dance, acting, stories, or another medium. [SRD p. 82, Performance](../../references/srd-5.1/pages/page-082.md)

<a id="srd51-core-persuasion"></a>
### Persuasion

`srd51.core.persuasion`

Charisma (Persuasion) applies to good-faith influence through tact or social grace, such as making courteous requests, observing etiquette, negotiating a peace, or inspiring a crowd. [SRD pp. 82–83, Persuasion](../../references/srd-5.1/pages/page-082.md) · [SRD p. 83, Persuasion continuation](../../references/srd-5.1/pages/page-083.md)

<a id="srd51-core-saving-throw"></a>
## Saving throws

`srd51.core.saving-throw`

An effect ordinarily calls for a save when a creature faces harm. The effect supplies the ability, DC, and result branches. Roll a d20, add the corresponding ability modifier and the proficiency bonus if proficient in saves using that ability, and apply situational adjustments, advantage, or disadvantage. Reaching the DC succeeds. Successful saves often avoid or reduce the harm, but the particular effect determines the result. Class choice grants proficiency in at least two saves. Some monsters have save proficiencies reflected in their stat blocks. See [spell saves](spellcasting.md#srd51-core-spell-saving-throw) for the spell DC formula and [death saves](combat.md#srd51-core-death-saving-throws) for a save without an ability score. [SRD p. 76, Using Ability Scores](../../references/srd-5.1/pages/page-076.md) · [SRD p. 83, Saving Throws](../../references/srd-5.1/pages/page-083.md) · [SRD p. 103, Saving Throws](../../references/srd-5.1/pages/page-103.md) · [SRD p. 98, Death Saving Throws](../../references/srd-5.1/pages/page-098.md)

## Source coverage

| SRD page | Sections and destinations |
| --- | --- |
| [76](../../references/srd-5.1/pages/page-076.md) | Using Ability Scores and modifier table → [scores](#srd51-core-ability-scores). Advantage and Disadvantage → [advantage](#srd51-core-advantage-disadvantage). |
| [77](../../references/srd-5.1/pages/page-077.md) | Advantage continuation → [advantage](#srd51-core-advantage-disadvantage). Proficiency Bonus → [proficiency](#srd51-core-proficiency-bonus). Ability Checks and Typical DCs → [checks](#srd51-core-ability-check). Contests → [contests](#srd51-core-contest). Skills opening → [skill proficiency](#srd51-core-skill-proficiency). |
| [78](../../references/srd-5.1/pages/page-078.md) | Skills continuation and pairings → [skill proficiency](#srd51-core-skill-proficiency) and each ability's skills. Variant → [different abilities](#srd51-core-skills-with-different-abilities). Passive Checks → [passive checks](#srd51-core-passive-check). |
| [79](../../references/srd-5.1/pages/page-079.md) | Passive continuation → [passive checks](#srd51-core-passive-check). Working Together and Group Checks → [cooperation](#srd51-core-working-together) and [group checks](#srd51-core-group-check). Using Each Ability introduces the six ability sections. Strength, Athletics, attack/damage and carrying → [Strength](#srd51-core-strength), [Athletics](#srd51-core-athletics) and [carrying](#srd51-core-lifting-and-carrying). |
| [80](../../references/srd-5.1/pages/page-080.md) | Strength continuation and variant → [carrying](#srd51-core-lifting-and-carrying) and [encumbrance](#srd51-core-encumbrance). Dexterity, skills, attack/damage, AC and initiative → [Dexterity](#srd51-core-dexterity) and its skill subsections. Hiding begins → [hiding](#srd51-core-hiding). |
| [81](../../references/srd-5.1/pages/page-081.md) | Hiding continuation, Passive Perception, and What Can You See? → [hiding](#srd51-core-hiding). Constitution and HP → [Constitution](#srd51-core-constitution), [Constitution and hit points](#srd51-core-constitution-and-hit-points). Intelligence checks and skills → [Intelligence](#srd51-core-intelligence) and its skill subsections. |
| [82](../../references/srd-5.1/pages/page-082.md) | Intelligence spellcasting → [Intelligence](#srd51-core-intelligence). Wisdom checks, skills and spellcasting → [Wisdom](#srd51-core-wisdom) and its skill subsections. Charisma checks and skills begin → [Charisma](#srd51-core-charisma) and its skill subsections. |
| [83](../../references/srd-5.1/pages/page-083.md) | Charisma continuation, Persuasion, and spellcasting → [Charisma](#srd51-core-charisma), [Persuasion](#srd51-core-persuasion). Saving Throws → [saves](#srd51-core-saving-throw). |

The source's illustrative characters, objects, and scene setups carry no independent requirements. Equivalent uses and newly written cases preserve the applicable behavior.

PDF layout checks covered the score and modifier table across both columns of p. 76 and the typical DC table on p. 77.
