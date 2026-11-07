# Spellcasting

<a id="srd51-core-cast-spell"></a>
## Cast a spell

`srd51.core.cast-spell`

A spell is a discrete magical effect. Its entry supplies name, level, school, casting time, range, components, duration, and effect. Apply the common casting rules whether the spell comes from a character class, a monster, an item, or another source, subject to the specific rule that grants access. Use the entry for special eligibility, choices, and effects. [SRD pp. 100–101, Spellcasting, What Is a Spell, and Casting a Spell](../../references/srd-5.1/pages/page-100.md) · [SRD p. 101, Casting a Spell](../../references/srd-5.1/pages/page-101.md)

<a id="srd51-core-spell-level"></a>
## Spell level

`srd51.core.spell-level`

Spell levels range from 0 to 9. Level 0 denotes a cantrip. A spell's level describes the magic and is distinct from the caster's character level. Class progression or a particular source of spellcasting controls access. For example, level 9 spells typically become accessible at character level 17. [SRD p. 100, Spell Level](../../references/srd-5.1/pages/page-100.md)

<a id="srd51-core-known-and-prepared-spells"></a>
## Known and prepared spells

`srd51.core.known-and-prepared-spells`

Ordinarily a caster must have the spell available as a known or prepared spell, or have access through a magic item. Class rules determine learning, preparation, and the number of spells held available by level. Bards and sorcerers use limited lists of known spells, while clerics and wizards prepare spells using their respective class rules. Many spellcasting monsters have known spells or other access described in their entries. [SRD p. 100, Known and Prepared Spells](../../references/srd-5.1/pages/page-100.md)

<a id="srd51-core-spell-slots"></a>
## Spell slots

`srd51.core.spell-slots`

Ordinary slot casting of a leveled spell expends one available slot of its level or higher. Class progression provides the slot counts and levels, with warlock slots supplied by Pact Magic. Finishing a [long rest](exploration.md#srd51-core-long-rest) restores expended slots. A specific feature can restore slots under other circumstances or authorize casting without a slot. Cantrips and ritual castings have their own exceptions. [SRD p. 100, Spell Slots](../../references/srd-5.1/pages/page-100.md) · [SRD p. 46, Pact Magic](../../references/srd-5.1/pages/page-046.md) · [SRD p. 101, Cantrips and Rituals](../../references/srd-5.1/pages/page-101.md)

<a id="srd51-core-casting-at-a-higher-level"></a>
### Casting at a higher level

`srd51.core.casting-at-a-higher-level`

When a spell uses a slot higher than its normal level, its level for that casting equals the slot's level. If its entry specifies a scaling benefit, apply it to the chosen level. A spell without a scaling benefit still takes on the slot's level for that casting. A 3rd-level spell can use a 4th-level slot, and a 2nd-level slot is ineligible. A 9th-level spell normally requires a 9th-level slot. [SRD p. 100, Spell Slots and Casting a Spell at a Higher Level](../../references/srd-5.1/pages/page-100.md)

<a id="srd51-core-casting-in-armor"></a>
## Casting in armor

`srd51.core.casting-in-armor`

To cast a spell while wearing armor, the caster must have proficiency with the armor worn. Supplying components or casting a cantrip does not override this requirement. [SRD p. 100, Casting in Armor](../../references/srd-5.1/pages/page-100.md)

<a id="srd51-core-cantrips"></a>
## Cantrips

`srd51.core.cantrips`

A level 0 spell available to the caster as a cantrip can be cast at will, without preparation and without expending a spell slot. Its own casting time, components, and other requirements still apply. [SRD p. 101, Cantrips and Casting a Spell](../../references/srd-5.1/pages/page-101.md)

<a id="srd51-core-rituals"></a>
## Rituals

`srd51.core.rituals`

A spell tagged ritual can be cast normally. To choose ritual casting, the caster also needs a feature that grants that mode and must have the spell known or prepared unless that feature specifies a different requirement. For example, Wizard Ritual Casting uses the spellbook exception. A ritual adds 10 minutes to the normal casting time, uses no slot, and cannot use higher-level slot scaling. Apply [longer casting](#srd51-core-longer-casting-times) and ordinary components. A 1-minute spell cast as a ritual takes 11 minutes. A spell with no ritual tag is ineligible for this procedure. [SRD p. 101, Rituals and Longer Casting Times](../../references/srd-5.1/pages/page-101.md)

<a id="srd51-core-spell-casting-time"></a>
## Casting time

`srd51.core.spell-casting-time`

The spell's entry controls whether casting uses one action, a bonus action, a reaction, or a longer interval. For a one-action spell in combat, spend your action. For a reaction spell, use an available [reaction](combat.md#srd51-core-reaction) at the event specified by that spell. An arbitrary event cannot substitute for that trigger. [SRD pp. 93, Cast a Spell](../../references/srd-5.1/pages/page-093.md) · [SRD p. 101, Casting Time and Reactions](../../references/srd-5.1/pages/page-101.md)

<a id="srd51-core-bonus-action-spell"></a>
### Bonus-action casting

`srd51.core.bonus-action-spell`

Cast a bonus-action spell on your turn by spending your available bonus action. During that same turn, the only other spell allowed is a cantrip with a casting time of 1 action. The restriction applies whichever spell is attempted first. It can also bar a reaction spell if the reaction would occur on that same turn. A subsequent turn in the round is outside the restriction. A creature unable to take actions cannot take bonus actions. [SRD p. 101, Bonus Action](../../references/srd-5.1/pages/page-101.md) · [SRD pp. 90–91, Bonus Actions and Reactions](../../references/srd-5.1/pages/page-090.md) · [SRD p. 91, Bonus Actions continuation and Reactions](../../references/srd-5.1/pages/page-091.md)

**Acceptance case.** On a turn when a caster has cast a spell using a bonus action, a one-action cantrip remains eligible if the caster has an action. A one-action leveled spell and a reaction spell on that turn are both ineligible. A reaction on a later creature's turn can use a reaction spell if its own requirements are met. [SRD p. 101, Bonus Action and Reactions](../../references/srd-5.1/pages/page-101.md) · [SRD p. 91, Reactions](../../references/srd-5.1/pages/page-091.md)

<a id="srd51-core-longer-casting-times"></a>
### Longer casting times

`srd51.core.longer-casting-times`

For a casting longer than one action or reaction, spend your action on casting each turn throughout the casting time and maintain concentration during the attempt. This includes ritual castings. If concentration breaks before completion, the spell fails without expending a slot. A subsequent attempt begins from the start. [SRD p. 101, Longer Casting Times](../../references/srd-5.1/pages/page-101.md)

**Project interpretation.** Have the required material components available during a long casting. Consume a component designated for consumption on completing the casting unless the spell specifies other timing. On an interrupted attempt, the casting never completes, so the ordinary consumed component remains available. The source expressly protects the slot and calls the component consumed by the spell, but does not specify when consumption occurs or a refund. Consuming it at the beginning is another reading. A spell held with Ready has already been cast, so consumption for that spell takes place at the initial casting before the hold. [SRD p. 101, Longer Casting Times and Components](../../references/srd-5.1/pages/page-101.md) · [SRD p. 102, Material](../../references/srd-5.1/pages/page-102.md) · [SRD pp. 93–94, Ready](../../references/srd-5.1/pages/page-093.md) · [SRD p. 94, Ready continuation](../../references/srd-5.1/pages/page-094.md)

<a id="srd51-core-spell-range"></a>
## Range

`srd51.core.spell-range`

Use the range entry to determine whether a target or origin is eligible when the spell is cast. A listed distance bounds that distance in feet. Touch requires touching the eligible creature or object specified by the spell, and can include touching yourself if eligible. Self can restrict an effect to the caster or identify the caster as the required origin of a spell area such as a cone or line. After casting, range does not constrain the effect unless the spell says it does. For example, an ongoing effect can continue when its target leaves the initial range unless its entry imposes a continuing limit. See [Ready](combat.md#srd51-core-ready) for a held spell's deferred effect. [SRD p. 101, Range](../../references/srd-5.1/pages/page-101.md) · [SRD pp. 93–94, Ready](../../references/srd-5.1/pages/page-093.md) · [SRD p. 94, Ready continuation](../../references/srd-5.1/pages/page-094.md)

<a id="srd51-core-spell-components"></a>
## Components

`srd51.core.spell-components`

The caster must supply each physical component denoted in the spell's entry. Inability to provide any required component prevents casting. [SRD p. 101, Components](../../references/srd-5.1/pages/page-101.md)

<a id="srd51-core-verbal-component"></a>
### Verbal (V)

`srd51.core.verbal-component`

The caster produces the required sounds with their requisite pitch and resonance. Gagging or magical silence prevents satisfying V. [SRD p. 101, Verbal](../../references/srd-5.1/pages/page-101.md)

<a id="srd51-core-somatic-component"></a>
### Somatic (S)

`srd51.core.somatic-component`

Perform the spell's gestures with free use of at least one hand. For a spell requiring both S and M, the hand used for its materials or focus can also perform the gestures. [SRD p. 101, Somatic](../../references/srd-5.1/pages/page-101.md) · [SRD p. 102, Somatic continuation and Material](../../references/srd-5.1/pages/page-102.md)

<a id="srd51-core-material-component"></a>
### Material (M)

`srd51.core.material-component`

Use the object or substance named in the entry. A component pouch or applicable spellcasting focus can replace ordinary materials. If a component has a listed cost, the caster must have that specific component. If casting consumes a component, supply that component for each casting. Use a free hand to reach materials or hold a focus. [SRD p. 102, Material](../../references/srd-5.1/pages/page-102.md)

**Acceptance cases.** A caster with a focus and both a V requirement and a gag cannot cast. A focus does not supply a gem with a listed price. An unpriced reusable material can be supplied by an applicable focus, and its hand can perform S in a spell that also uses M. A spell that consumes an indicated reagent requires a fresh supply for the next casting. [SRD p. 101, Components and Verbal](../../references/srd-5.1/pages/page-101.md) · [SRD p. 102, Material](../../references/srd-5.1/pages/page-102.md)

<a id="srd51-core-spell-duration"></a>
## Duration

`srd51.core.spell-duration`

Duration states the spell's persistence. It can be a time interval, a termination condition such as dispelling or destruction, or a period capped by concentration. An instantaneous spell's magic exists for an instant. Damage, healing, creation, or alteration from such a spell can remain afterward, with no ongoing spell to dispel. [SRD p. 102, Duration, Instantaneous, and Concentration](../../references/srd-5.1/pages/page-102.md)

<a id="srd51-core-concentration"></a>
## Concentration

`srd51.core.concentration`

A spell marked concentration in its Duration entry persists only while the caster maintains concentration and only up to the specified duration. The caster can drop concentration at any time without an action. Normal movement and attacking leave it intact. Maintaining a spell, holding a [readied spell](combat.md#srd51-core-ready), and carrying out a [long casting](#srd51-core-longer-casting-times) use the same limit of one concentration obligation per caster. Taking on a new spell's concentration ends the preceding concentration, including when the new spell requires concentration only for its hold or casting time. Other spells can persist concurrently when they impose no concentration obligation. [SRD pp. 93–94, Ready](../../references/srd-5.1/pages/page-093.md) · [SRD p. 94, Ready continuation](../../references/srd-5.1/pages/page-094.md) · [SRD p. 101, Longer Casting Times](../../references/srd-5.1/pages/page-101.md) · [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md)

- **Taking damage.** For each source of damage the concentrating caster takes, make a [Constitution saving throw](abilities.md#srd51-core-saving-throw). Its DC is the greater of 10 and half the damage taken from that source. Success maintains concentration and failure breaks it. Do not aggregate damage from separate sources into one DC. [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md)
- **Environmental disruption.** The GM can require a DC 10 Constitution saving throw to maintain concentration in a disruptive environment, for example when rough seas wash over the caster. [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md)
- **Incapacitation or death.** Either ends concentration immediately without a save. If an effect imposes a condition that includes [incapacitated](conditions.md#srd51-condition-incapacitated), that suffices. The end of the condition cannot restart the spell. [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md) · [SRD pp. 358–359, Conditions](../../references/srd-5.1/pages/page-358.md) · [SRD p. 359, Conditions continuation](../../references/srd-5.1/pages/page-359.md)

Loss of concentration ends a maintained spell, dissipates held magic before a Ready release, or aborts an unfinished long casting. Each operation's own rule determines the consequences for casting resources. [SRD p. 94, Ready continuation](../../references/srd-5.1/pages/page-094.md) · [SRD p. 101, Longer Casting Times](../../references/srd-5.1/pages/page-101.md) · [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md)

**Project interpretation.** For damage-based concentration saves, round half the damage down before comparing with 10. The concentration provision gives no rounding direction, and p. 76 expressly floors a division for a different d20 calculation. This choice uses the same integer convention for the DC. An alternative is to compare directly to the fractional threshold, which makes 21 damage require a save total of 11 instead of 10. [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md) · [SRD p. 76, Ability Scores and Modifiers](../../references/srd-5.1/pages/page-076.md)

**Acceptance cases.** Damage of 18 and 24 from two sources yields separate DC 10 and DC 12 saves. Failure on either ends the spell, with no further concentration saves for that ended spell. Voluntarily ending concentration requires no action. See [Ready](combat.md#srd51-core-ready) for the effect of incapacitation before release. [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md) · [SRD p. 94, Ready continuation](../../references/srd-5.1/pages/page-094.md)

<a id="srd51-core-spell-targets"></a>
## Targets

`srd51.core.spell-targets`

The spell identifies eligible creatures, objects, or a point of origin. Choose the required number of eligible targets in range. A creature may be unaware it was targeted if no effect is perceptible, subject to the spell's specific rules. When the spell allows a creature of your choice, you may select yourself unless the spell requires a hostile or different creature. You may also include yourself when you are an eligible target within the area of your own spell. [SRD p. 101, Range](../../references/srd-5.1/pages/page-101.md) · [SRD p. 102, Targets and Targeting Yourself](../../references/srd-5.1/pages/page-102.md)

<a id="srd51-core-clear-path-to-target"></a>
### Clear path

`srd51.core.clear-path-to-target`

Targeting requires a clear path. A target behind [total cover](combat.md#srd51-core-cover) is ineligible. If you select an unseen area origin and an obstruction lies between you and the point, the origin appears on the near side of the obstruction. Individual spells can require sight, independently of the clear-path requirement. [SRD p. 102, A Clear Path to the Target](../../references/srd-5.1/pages/page-102.md)

<a id="srd51-core-spell-area"></a>
## Areas of effect

`srd51.core.spell-area`

Use the spell's specified shape, dimensions, and origin. An origin is usually a point in space, but a spell can use a creature or object. For an otherwise included location to be affected, an unblocked straight line must extend to it from the origin. Total cover is required to block that line. Determine this propagation from the origin separately from the caster's clear path to the chosen origin. [SRD pp. 102–103, Areas of Effect](../../references/srd-5.1/pages/page-102.md) · [SRD p. 103, Areas of Effect continuation](../../references/srd-5.1/pages/page-103.md)

<a id="srd51-core-cone"></a>
### Cone

`srd51.core.cone`

Choose the direction from the origin and use the specified maximum length. At any point along the length, the cone's full width equals the distance from the origin. Exclude the origin unless the caster chooses to include it. [SRD p. 103, Cone](../../references/srd-5.1/pages/page-103.md)

<a id="srd51-core-cube"></a>
### Cube

`srd51.core.cube`

Use the specified side length for all three dimensions. Select the origin on any face. Exclude the origin unless the caster chooses to include it. [SRD p. 103, Cube](../../references/srd-5.1/pages/page-103.md)

<a id="srd51-core-cylinder"></a>
### Cylinder

`srd51.core.cylinder`

Place the origin at the center of a circle of the specified radius, either on the ground or at the top of the effect. The effect extends to the circle's perimeter and then upward from the base or downward from the top for the specified height. Include the origin. [SRD p. 103, Cylinder](../../references/srd-5.1/pages/page-103.md)

<a id="srd51-core-line"></a>
### Line

`srd51.core.line`

Extend a straight path from the origin for the specified length and use the given width. Exclude the origin unless the caster chooses to include it. [SRD p. 103, Line](../../references/srd-5.1/pages/page-103.md)

<a id="srd51-core-sphere"></a>
### Sphere

`srd51.core.sphere`

Select the origin at the center and extend outward by the stated radius. Include the origin. [SRD p. 103, Sphere](../../references/srd-5.1/pages/page-103.md)

**Acceptance cases.** The origin of a 20-foot cone is excluded by default, and the cone is 10 feet wide 10 feet along its length. A point within a sphere's radius is excluded if total cover blocks its straight line to the origin. A barrier between the caster and a direct creature target prevents that targeting if it gives total cover, even when the target's location is known. An unseen area origin behind an obstruction is instead placed on its near side. [SRD p. 102, A Clear Path to the Target](../../references/srd-5.1/pages/page-102.md) · [SRD p. 103, Areas of Effect continuation and Cone](../../references/srd-5.1/pages/page-103.md)

<a id="srd51-core-spell-saving-throw"></a>
## Spell saving throws

`srd51.core.spell-saving-throw`

The spell specifies whether the target makes a [saving throw](abilities.md#srd51-core-saving-throw), the ability used, and the result of success and failure. The caster's spell save DC is 8 + the spellcasting ability modifier + proficiency bonus + special modifiers. The casting source determines which ability to use and can supply an explicit DC. For an ability modifier of +4 and proficiency +3 with no other modifier, the spell save DC is 15. [SRD p. 103, Saving Throws](../../references/srd-5.1/pages/page-103.md)

<a id="srd51-core-spell-attack-roll"></a>
## Spell attack rolls

`srd51.core.spell-attack-roll`

When a spell calls for an attack, its spell attack bonus is the caster's spellcasting ability modifier plus proficiency bonus. Use the [attack roll](combat.md#srd51-core-attack-roll) procedure and apply relevant special modifiers. A ranged spell attack has the same [close-combat disadvantage](combat.md#srd51-core-ranged-attacks-in-close-combat) as another ranged attack, when a hostile creature within 5 feet can see the attacker and is able to act as specified by that rule. A spell that instead calls for a save uses the save procedure. [SRD p. 103, Attack Rolls](../../references/srd-5.1/pages/page-103.md) · [SRD p. 95, Ranged Attacks in Close Combat](../../references/srd-5.1/pages/page-095.md)

<a id="srd51-core-schools-of-magic"></a>
## Schools of magic

`srd51.core.schools-of-magic`

Spells are classified into eight schools, which other features may reference. School membership by itself supplies no general resolution modifier. The categories describe typical behavior. [SRD p. 103, The Schools of Magic](../../references/srd-5.1/pages/page-103.md) · [SRD p. 104, Schools continuation](../../references/srd-5.1/pages/page-104.md)

- **Abjuration** covers protection and wards, including barriers, negation of harmful effects, punishment of trespassers, or banishment.
- **Conjuration** covers bringing creatures or objects from elsewhere, teleportation, and creating objects or effects.
- **Divination** reveals information such as secrets, future possibilities, hidden locations, illusions, and distant people or places.
- **Enchantment** influences minds and behavior, from induced friendship to direct control.
- **Evocation** channels magical energy into effects such as elemental blasts or healing.
- **Illusion** deceives senses or minds, including false perceptions, hidden realities, and false memories. Some effects are shared perceptions and others occur in a specific creature's mind.
- **Necromancy** manipulates life and death, including life reserves, draining vitality, creating undead, and restoring life. The source describes creating undead by necromancy as a nongood act and frequent use of such spells as evil conduct. It specifies no casting eligibility check for this guidance.
- **Transmutation** alters properties of creatures, objects, or the environment, including transformation, physical enhancement, animation, and accelerated healing.

<a id="srd51-core-combining-magical-effects"></a>
## Combining magical effects

`srd51.core.combining-magical-effects`

Effects from different spells combine during overlapping durations. For multiple castings of the same spell, apply only the most potent effect while the durations overlap, for example the largest applicable bonus. The rule resolves overlapping effects and does not cancel the durations of the other castings. When the stronger one ends, a still-active weaker one can apply. Two castings of the same spell granting an identical bonus never grant that bonus twice. [SRD p. 104, Combining Magical Effects](../../references/srd-5.1/pages/page-104.md)

**Project interpretation.** For each recipient affected by overlapping castings of the same spell, take the applicable effects from one casting as a unit. This avoids constructing a combined benefit from different castings. Compare potency using the spell's stated effects. If the spell and its effects give no ranking for two equally potent or incomparable candidates, apply the most recently completed casting among those candidates. An earlier casting can continue affecting other recipients that the new one does not affect. The source selects by potency and gives no tie-breaker or instruction to combine independently selected pieces of the same spell. Use any specific spell rule governing overlap before these defaults. [SRD p. 104, Combining Magical Effects](../../references/srd-5.1/pages/page-104.md)

## Source coverage

| SRD page | Sections and destinations |
| --- | --- |
| [100](../../references/srd-5.1/pages/page-100.md) | Spellcasting and What Is a Spell → [cast a spell](#srd51-core-cast-spell). Spell Level and Known and Prepared Spells → [level](#srd51-core-spell-level) and [access](#srd51-core-known-and-prepared-spells). Spell Slots, Casting a Spell at a Higher Level, and Casting in Armor sidebar → [slots](#srd51-core-spell-slots), [higher level](#srd51-core-casting-at-a-higher-level), and [armor](#srd51-core-casting-in-armor). |
| [101](../../references/srd-5.1/pages/page-101.md) | Cantrips and Rituals → [cantrips](#srd51-core-cantrips), [rituals](#srd51-core-rituals). Casting a Spell → [cast a spell](#srd51-core-cast-spell). Casting Time, Bonus Action, Reactions across columns, Longer Casting Times, and Range → [time](#srd51-core-spell-casting-time), [bonus action](#srd51-core-bonus-action-spell), [longer casting](#srd51-core-longer-casting-times), [range](#srd51-core-spell-range). Components, Verbal, and Somatic opening → [components](#srd51-core-spell-components) and subsections. |
| [102](../../references/srd-5.1/pages/page-102.md) | Somatic continuation and Material → [somatic](#srd51-core-somatic-component), [material](#srd51-core-material-component). Duration, Instantaneous, and Concentration across columns → [duration](#srd51-core-spell-duration), [concentration](#srd51-core-concentration). Targets, A Clear Path to the Target, Targeting Yourself, and Areas of Effect opening → [targets](#srd51-core-spell-targets), [clear path](#srd51-core-clear-path-to-target), [areas](#srd51-core-spell-area). |
| [103](../../references/srd-5.1/pages/page-103.md) | Areas continuation and all five shapes → [areas](#srd51-core-spell-area) and shape subsections. Saving Throws and Attack Rolls → [spell saves](#srd51-core-spell-saving-throw), [spell attacks](#srd51-core-spell-attack-roll). The Schools of Magic sidebar begins → [schools](#srd51-core-schools-of-magic). |
| [104](../../references/srd-5.1/pages/page-104.md) | Illusion continuation, Necromancy and Transmutation → [schools](#srd51-core-schools-of-magic). Combining Magical Effects → [combining effects](#srd51-core-combining-magical-effects). |

Descriptions of magic's metaphysical imagery and historical spell provenance, and the source's named example characters, supply no casting operations. The example results that illustrate those operations have equivalent definitions or acceptance cases above.

PDF layout checks covered the Casting in Armor sidebar on p. 100 and the Schools of Magic sidebar and cross-column area rules on p. 103.
