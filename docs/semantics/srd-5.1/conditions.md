# Conditions

<a id="srd51-core-condition-lifecycle"></a>
## Condition lifecycle

`srd51.core.condition-lifecycle`

A spell, class feature, monster action, or other effect can give a creature a condition. Each instance persists for the imposing effect's specified duration or until countered. Multiple instances of the same condition keep their individual lifetimes, but the condition has the same effect while any instance remains. [Exhaustion](#srd51-condition-exhaustion) specifically accumulates levels instead. [SRD p. 358, Conditions and Exhaustion](../../references/srd-5.1/pages/page-358.md)

<a id="srd51-condition-blinded"></a>
## Blinded

`srd51.condition.blinded`

The creature cannot see and automatically fails ability checks that require sight. Its attack rolls have disadvantage and attack rolls against it have advantage. [Heavily obscured areas](exploration.md#srd51-core-vision-and-light) can make a creature effectively blinded for an attempt to see something in the area. [SRD p. 358, Blinded](../../references/srd-5.1/pages/page-358.md) · [SRD p. 86, Vision and Light](../../references/srd-5.1/pages/page-086.md)

<a id="srd51-condition-charmed"></a>
## Charmed

`srd51.condition.charmed`

The affected creature cannot attack its charmer or target the charmer with a harmful ability or magical effect. The charmer has advantage on ability checks to interact socially with the affected creature. The effect imposing the condition supplies the charmer and any behavior beyond these restrictions and benefits. If the creature is charmed by two effects with distinct charmers, apply the source-relative restrictions and social benefit to each applicable charmer. [SRD p. 358, Conditions and Charmed](../../references/srd-5.1/pages/page-358.md)

<a id="srd51-condition-deafened"></a>
## Deafened

`srd51.condition.deafened`

The creature cannot hear and automatically fails ability checks that require hearing. A check using another sense remains eligible if the task can be accomplished using that sense. [SRD p. 358, Deafened](../../references/srd-5.1/pages/page-358.md)

<a id="srd51-condition-exhaustion"></a>
## Exhaustion

`srd51.condition.exhaustion`

Exhaustion is a six-level condition. Each applicable effect adds the level or levels it specifies, including to a creature that already has exhaustion. Apply the provision at the current level and every provision below it. Sources can include special abilities, starvation, and other environmental hazards. [SRD p. 358, Exhaustion](../../references/srd-5.1/pages/page-358.md)

- **1:** Apply disadvantage to ability checks.
- **2:** Halve the creature's speed.
- **3:** Apply disadvantage to attack rolls and saving throws as well.
- **4:** Halve the creature's hit point maximum.
- **5:** Set the creature's speed to 0.
- **6:** The creature dies.

An effect that removes exhaustion reduces the level by the amount it specifies. When the level falls below 1, all exhaustion effects end. On finishing a qualifying [long rest](exploration.md#srd51-core-long-rest), reduce exhaustion by one if the creature has also eaten some food and drunk some liquid. Exhaustion caused by deprivation remains subject to the [full food and water prerequisite](exploration.md#srd51-core-food-and-water). Death at level 6 is an outcome of reaching that level. Ordinary removal of a level is not itself a method for returning a dead creature to life. [SRD p. 358, Exhaustion](../../references/srd-5.1/pages/page-358.md) · [SRD p. 86, Food and Water](../../references/srd-5.1/pages/page-086.md) · [SRD p. 97, Healing](../../references/srd-5.1/pages/page-097.md)

**Acceptance cases.** A creature at level 2 that gains one level has half speed and disadvantage on checks, attacks, and saves. If an eligible long rest reduces it from level 3 to 2, the attack and save disadvantages end and the other penalties remain. A long rest with neither food nor drink cannot produce this reduction. [SRD p. 358, Exhaustion](../../references/srd-5.1/pages/page-358.md)

<a id="srd51-condition-frightened"></a>
## Frightened

`srd51.condition.frightened`

Check distance and visibility separately. Voluntary movement cannot decrease the creature's distance to what frightened it, even with that source out of sight. Apply disadvantage to its checks and attack rolls only while it has line of sight to the source. Forced movement can carry it closer. [SRD p. 358, Frightened](../../references/srd-5.1/pages/page-358.md)

<a id="srd51-condition-grappled"></a>
## Grappled

`srd51.condition.grappled`

The creature's speed becomes 0 and speed bonuses cannot benefit it. The condition ends if the grappler becomes [incapacitated](#srd51-condition-incapacitated) or an effect takes the grappled creature beyond the reach of the grappler or grappling effect. The [grapple](combat.md#srd51-core-grappling) rule also allows escape and voluntary release. A speed bonus cannot enable standing up while the grapple persists. [SRD p. 358, Grappled](../../references/srd-5.1/pages/page-358.md) · [SRD p. 95, Grappling](../../references/srd-5.1/pages/page-095.md) · [SRD p. 92, Being Prone continuation](../../references/srd-5.1/pages/page-092.md)

<a id="srd51-condition-incapacitated"></a>
## Incapacitated

`srd51.condition.incapacitated`

The creature cannot take actions or reactions. Losing access to actions also prevents [bonus actions](combat.md#srd51-core-bonus-action). Incapacitation also ends any [concentration](spellcasting.md#srd51-core-concentration) and any [grapple](#srd51-condition-grappled) it is maintaining, removes the benefits of its [Dodge](combat.md#srd51-core-dodge), and prevents it from causing [close-combat disadvantage to a ranged attacker](combat.md#srd51-core-ranged-attacks-in-close-combat). Incapacitated itself supplies no movement or speech restriction. Several other conditions include incapacitated and also impose their own restrictions. [SRD p. 358, Incapacitated and Grappled](../../references/srd-5.1/pages/page-358.md) · [SRD p. 91, Bonus Actions continuation](../../references/srd-5.1/pages/page-091.md) · [SRD p. 93, Dodge](../../references/srd-5.1/pages/page-093.md) · [SRD p. 95, Ranged Attacks in Close Combat](../../references/srd-5.1/pages/page-095.md) · [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md)

**Acceptance case.** A caster with a [readied spell](combat.md#srd51-core-ready) becomes incapacitated. The caster loses concentration immediately and the spell dissipates. The caster cannot react if the trigger completes while incapacitation remains. Even if the condition ends before the trigger, the earlier concentration loss has already disposed of the held spell. [SRD p. 94, Ready continuation](../../references/srd-5.1/pages/page-094.md) · [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md) · [SRD p. 358, Incapacitated](../../references/srd-5.1/pages/page-358.md)

<a id="srd51-condition-invisible"></a>
## Invisible

`srd51.condition.invisible`

Seeing the creature requires appropriate magic or a special sense. Treat the creature as heavily obscured for hiding. Its noise or tracks may reveal its location even without seeing it. Its attack rolls have advantage and attacks against it have disadvantage. [Hiding](abilities.md#srd51-core-hiding) and [unseen attackers and targets](combat.md#srd51-core-unseen-attackers-and-targets) separately govern discovery and attacks based on who can see or locate a creature. [SRD p. 358, Invisible](../../references/srd-5.1/pages/page-358.md) · [SRD pp. 80–81, Hiding](../../references/srd-5.1/pages/page-080.md) · [SRD p. 81, Hiding continuation](../../references/srd-5.1/pages/page-081.md) · [SRD p. 94, Unseen Attackers and Targets](../../references/srd-5.1/pages/page-094.md)

**Project interpretation.** Apply the attack modifiers in the condition as a separate provision. Detecting an invisible creature or being able to see it removes only the modifiers that depend on whether it is unseen, unless a rule also negates the condition's own modifiers. The two clauses in the condition give different predicates, one qualified by sight aids and the other framed as an effect of having the condition. An alternative is to treat both clauses as implementing the advantage and disadvantage of being unseen. The source does not express that dependency in the second clause. [SRD p. 358, Invisible](../../references/srd-5.1/pages/page-358.md) · [SRD p. 94, Unseen Attackers and Targets](../../references/srd-5.1/pages/page-094.md)

<a id="srd51-condition-paralyzed"></a>
## Paralyzed

`srd51.condition.paralyzed`

The creature is [incapacitated](#srd51-condition-incapacitated) and cannot move or speak. It automatically fails Strength and Dexterity saves. Attack rolls against it have advantage. An attack that hits it is a critical hit if the attacker is within 5 feet. The critical effect requires a hit, and its distance threshold includes exactly 5 feet. [SRD p. 358, Paralyzed](../../references/srd-5.1/pages/page-358.md) · [SRD p. 359, Paralyzed continuation](../../references/srd-5.1/pages/page-359.md)

<a id="srd51-condition-petrified"></a>
## Petrified

`srd51.condition.petrified`

Petrification makes the creature inanimate and solid, ordinarily as stone. Include its worn and carried nonmagical objects in the transformation. Multiply its weight by ten and pause aging. It loses awareness and the ability to speak or move, and gains [incapacitated](#srd51-condition-incapacitated). Resolve attacks against it with advantage and its Strength or Dexterity saves as automatic failures. It resists every damage type. It is immune to poison and disease during petrification. Afflictions already present pause and can resume once petrification ends. [SRD p. 359, Petrified](../../references/srd-5.1/pages/page-359.md)

<a id="srd51-condition-poisoned"></a>
## Poisoned

`srd51.condition.poisoned`

The creature has disadvantage on attack rolls and ability checks. [SRD p. 359, Poisoned](../../references/srd-5.1/pages/page-359.md)

<a id="srd51-condition-prone"></a>
## Prone

`srd51.condition.prone`

The creature can crawl or spend movement to stand and end the condition. [Prone movement](combat.md#srd51-core-being-prone) supplies movement costs, eligibility to stand, and magical movement such as teleportation. The prone creature has disadvantage on its attack rolls. An attacker within 5 feet has advantage on an attack roll against it. A more distant attacker has disadvantage, including when making a melee attack with extended reach. [SRD p. 359, Prone](../../references/srd-5.1/pages/page-359.md) · [SRD pp. 91–92, Being Prone](../../references/srd-5.1/pages/page-091.md) · [SRD p. 92, Being Prone continuation](../../references/srd-5.1/pages/page-092.md)

<a id="srd51-condition-restrained"></a>
## Restrained

`srd51.condition.restrained`

The creature's speed becomes 0 and speed bonuses cannot benefit it. Its attack rolls and Dexterity saving throws have disadvantage. Attack rolls against it have advantage. [SRD p. 359, Restrained](../../references/srd-5.1/pages/page-359.md)

<a id="srd51-condition-stunned"></a>
## Stunned

`srd51.condition.stunned`

The creature is [incapacitated](#srd51-condition-incapacitated), cannot move, and can only speak falteringly. It automatically fails Strength and Dexterity saves. Attack rolls against it have advantage. [SRD p. 359, Stunned](../../references/srd-5.1/pages/page-359.md)

<a id="srd51-condition-unconscious"></a>
## Unconscious

`srd51.condition.unconscious`

The creature is [incapacitated](#srd51-condition-incapacitated), cannot move or speak, and is unaware of its surroundings. It drops what it is holding and falls [prone](#srd51-condition-prone). It automatically fails Strength and Dexterity saves and attack rolls against it have advantage. A hit against it is a critical hit if the attacker is within 5 feet. When unconsciousness ends, the creature still needs to stand if prone. If the creature became unconscious from damage reducing it to 0 hit points, [regaining any hit points](combat.md#srd51-core-dropping-to-zero-hit-points) ends that unconsciousness. Other causes can supply other ending conditions. [SRD p. 359, Unconscious and Prone](../../references/srd-5.1/pages/page-359.md) · [SRD p. 98, Falling Unconscious](../../references/srd-5.1/pages/page-098.md)

**Acceptance cases.** Two independent sources of Poisoned grant one disadvantage effect, and ending only the first still leaves the creature poisoned while the second persists. A paralyzed creature fails a Dexterity save automatically even with a large bonus. An attacker 10 feet away that hits an unconscious creature does not score an automatic critical hit from the condition. Its disadvantage from the target's Prone and advantage from Unconscious cancel, absent an independent change to the applicable predicates. [SRD p. 358, Conditions and Paralyzed](../../references/srd-5.1/pages/page-358.md) · [SRD p. 359, Poisoned, Prone and Unconscious](../../references/srd-5.1/pages/page-359.md) · [SRD p. 76, Advantage and Disadvantage](../../references/srd-5.1/pages/page-076.md)

## Source coverage

| SRD page | Sections and destinations |
| --- | --- |
| [358](../../references/srd-5.1/pages/page-358.md) | Appendix PH-A introduction and duplicate instances → [condition lifecycle](#srd51-core-condition-lifecycle). Exhaustion sidebar, including level 6 and the remaining text in the right column → [Exhaustion](#srd51-condition-exhaustion). Individual rules → [Blinded](#srd51-condition-blinded), [Charmed](#srd51-condition-charmed), [Deafened](#srd51-condition-deafened), [Frightened](#srd51-condition-frightened), [Grappled](#srd51-condition-grappled), [Incapacitated](#srd51-condition-incapacitated), [Invisible](#srd51-condition-invisible), and [Paralyzed](#srd51-condition-paralyzed). |
| [359](../../references/srd-5.1/pages/page-359.md) | [Paralyzed](#srd51-condition-paralyzed) critical hit continuation, [Petrified](#srd51-condition-petrified), [Poisoned](#srd51-condition-poisoned), [Prone](#srd51-condition-prone), [Restrained](#srd51-condition-restrained), [Stunned](#srd51-condition-stunned), and [Unconscious](#srd51-condition-unconscious). |

Examples used only to illustrate the source rules are replaced by the acceptance cases. The full Exhaustion sidebar and the cross-page final bullet of Paralyzed have primary destinations.

PDF layout checks covered the Exhaustion sidebar across both columns on p. 358, including the level 6 row above its continuation.
