# Miscellaneous creatures from Ape through Goat

These profiles use [shared creature rules](creatures.md) for attacks, recharge, senses, and normal damage resolution. Melee and ranged weapon labels specify attack type. A range pair gives normal then long range. Where an attack targets one creature, it requires a creature. Apply each action's own limits in addition to its range. Multiattack and other profiled attacks are creature actions unless a feature grants their use at another time.

## Shared actions and traits

<a id="srd51-creature-rules-charge"></a>

### Charge damage

`srd51.creature-rules.charge`

First move at least 20 feet straight toward a target and then hit it with the specified attack on that turn. Apply the listed extra damage to that target. A creature target also rolls the listed Strength save and falls [prone](conditions.md#srd51-condition-prone) on failure. The extra damage applies whether the target is a creature or an object, subject to the attack's eligibility and the target's defenses. **Derived conclusion.** For Elk and Giant Elk, the extra dice are part of the triggering Ram hit and use its bludgeoning damage type. [SRD p. 369, Boar, Charge continuation](../../references/srd-5.1/pages/page-369.md) [SRD p. 372, Elk, Charge and Ram](../../references/srd-5.1/pages/page-372.md) [SRD p. 375, Giant Elk, Charge and Ram](../../references/srd-5.1/pages/page-375.md) [SRD p. 376, Giant Goat, Charge](../../references/srd-5.1/pages/page-376.md) [SRD p. 379, Giant Sea Horse, Charge](../../references/srd-5.1/pages/page-379.md) [SRD p. 381, Goat, Charge](../../references/srd-5.1/pages/page-381.md) [SRD p. 388, Rhinoceros, Charge](../../references/srd-5.1/pages/page-388.md)

<a id="srd51-creature-rules-charge-and-bonus-attack"></a>

### Charging knockdown and follow-up

`srd51.creature-rules.charge-and-bonus-attack`

For Pounce and Trampling Charge, move at least 20 feet straight toward a creature and then hit it with the named attack on the same turn. The target rolls the listed Strength save and falls prone on failure. If it is prone after that sequence, the charger may spend its bonus action on the indicated attack against that same target, subject to the follow-up attack's own reach and target rules. [SRD p. 372, Elephant, Trampling Charge](../../references/srd-5.1/pages/page-372.md) [SRD p. 383, Lion, Pounce](../../references/srd-5.1/pages/page-383.md) [SRD p. 384, Mammoth, Trampling Charge](../../references/srd-5.1/pages/page-384.md) [SRD p. 385, Panther, Pounce](../../references/srd-5.1/pages/page-385.md) [SRD p. 388, Saber-Toothed Tiger, Pounce](../../references/srd-5.1/pages/page-388.md) [SRD p. 392, Tiger, Pounce and Warhorse, Trampling Charge](../../references/srd-5.1/pages/page-392.md)

<a id="srd51-creature-rules-relentless"></a>

### Relentless

`srd51.creature-rules.relentless`

With the trait available, if one instance of damage is no higher than the profile's threshold and would take the creature from positive HP to 0 HP, set it to 1 HP instead and expend the trait. A [short or long rest](creatures.md#srd51-creature-rules-limited-usage) restores it. [SRD p. 369, Boar, Relentless](../../references/srd-5.1/pages/page-369.md) [SRD p. 373, Giant Boar, Relentless](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-rules-flyby"></a>

### Flyby

`srd51.creature-rules.flyby`

Flying out of an enemy's reach does not trigger an [opportunity attack](combat.md#srd51-core-opportunity-attack) from that enemy. Apply normal opportunity attack rules when leaving its reach by other movement. [SRD p. 372, Flying Snake, Flyby](../../references/srd-5.1/pages/page-372.md) [SRD p. 377, Giant Owl, Flyby](../../references/srd-5.1/pages/page-377.md) [SRD p. 385, Owl, Flyby](../../references/srd-5.1/pages/page-385.md)

<a id="srd51-creature-rules-swallow"></a>

### Frog and toad Swallow

`srd51.creature-rules.swallow`

Use the Swallow action on a target of the allowed size that the creature is currently grappling. Make one Bite attack. On a hit, resolve the Bite's damage, swallow the target, and end the grapple. The swallower can contain at most one target. A swallowed target is [blinded](conditions.md#srd51-condition-blinded) and [restrained](conditions.md#srd51-condition-restrained), has [total cover](combat.md#srd51-core-cover) against outside attacks and effects, and takes the profile's acid damage at the start of each of the swallower's turns. The swallower's death immediately ends this restraint. The swallowed creature can spend the listed movement to leave the corpse and comes out prone. **Project interpretation.** During that exit interval, blindness and total cover remain until the swallowed creature leaves because it is still inside the body. The dead creature no longer takes turns, so it applies no further start-of-turn acid damage. The text explicitly ends restraint on death. Ending all swallowed effects on death would let outside attacks reach a creature still inside the body. [SRD p. 376, Giant Frog, Bite and Swallow](../../references/srd-5.1/pages/page-376.md) [SRD p. 380, Giant Toad, Bite and Swallow](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-rules-sure-footed"></a>

### Sure-Footed

`srd51.creature-rules.sure-footed`

Roll Strength and Dexterity saves with advantage against an effect that would knock this creature prone. The trait applies to those saves and has no effect on a check to keep balance or on a Dexterity save against an unrelated effect. [SRD p. 376, Giant Goat, Sure-Footed](../../references/srd-5.1/pages/page-376.md) [SRD p. 381, Goat, Sure-Footed](../../references/srd-5.1/pages/page-381.md) [SRD p. 384, Mule, Sure-Footed](../../references/srd-5.1/pages/page-384.md)

<a id="srd51-creature-rules-underwater-camouflage"></a>

### Underwater Camouflage

`srd51.creature-rules.underwater-camouflage`

Gain advantage on Dexterity (Stealth) checks while underwater. [SRD p. 377, Giant Octopus, Underwater Camouflage](../../references/srd-5.1/pages/page-377.md) [SRD p. 385, Octopus, Underwater Camouflage](../../references/srd-5.1/pages/page-385.md)

<a id="srd51-creature-rules-water-breathing"></a>

### Water Breathing

`srd51.creature-rules.water-breathing`

The creature can breathe only underwater. A separate Hold Breath trait, where present, supplies its out-of-water breath duration. [SRD p. 377, Giant Octopus, Hold Breath and Water Breathing](../../references/srd-5.1/pages/page-377.md) [SRD p. 379, Giant Sea Horse and Giant Shark, Water Breathing](../../references/srd-5.1/pages/page-379.md) [SRD p. 385, Octopus, Hold Breath and Water Breathing](../../references/srd-5.1/pages/page-385.md) [SRD p. 389, Sea Horse, Water Breathing](../../references/srd-5.1/pages/page-389.md)

<a id="srd51-creature-rules-ink-cloud"></a>

### Octopus Ink Cloud

`srd51.creature-rules.ink-cloud`

While underwater and with this action available, release an ink cloud in the listed radius centered on the octopus's location. The area is [heavily obscured](exploration.md#srd51-core-vision-and-light) for 1 minute unless a significant current disperses it earlier. After releasing it, the octopus may spend a bonus action to Dash. The action recharges on a short or long rest. **Project interpretation.** The ink stays in the area created at release. This gives effect to the named area and current-based dispersal while the octopus dashes away. Reading the octopus as a moving center would carry the ink along until the duration ends, and the text supplies no such movement rule. [SRD p. 377, Giant Octopus, Ink Cloud](../../references/srd-5.1/pages/page-377.md) [SRD p. 385, Octopus, Ink Cloud](../../references/srd-5.1/pages/page-385.md)

<a id="srd51-creature-rules-blood-frenzy"></a>

### Blood Frenzy

`srd51.creature-rules.blood-frenzy`

Gain advantage on melee attack rolls against creatures whose current hit points are below their maximum. [SRD p. 379, Giant Shark, Blood Frenzy](../../references/srd-5.1/pages/page-379.md) [SRD p. 382, Hunter Shark, Blood Frenzy](../../references/srd-5.1/pages/page-382.md) [SRD p. 387, Quipper, Blood Frenzy](../../references/srd-5.1/pages/page-387.md) [SRD p. 390, Swarm of Quippers, Blood Frenzy](../../references/srd-5.1/pages/page-390.md)

<a id="srd51-creature-rules-debilitating-venom"></a>

### Debilitating venom outcome

`srd51.creature-rules.debilitating-venom`

When the specific effect listed on a profile reduces a creature to 0 HP, the target becomes [stable](combat.md#srd51-core-stabilizing-a-creature) and is [poisoned](conditions.md#srd51-condition-poisoned) for 1 hour. It is [paralyzed](conditions.md#srd51-condition-paralyzed) while poisoned by this effect. Healing it above 0 HP does not remove that poison or its paralysis. A creature immune to the poisoned condition still takes any damage allowed by its damage defenses and can become stable at 0. It is never poisoned by this effect, so the associated paralysis does not occur. For the named bites and sting, the reduction to 0 must come from the poison damage, including half damage on a successful save. Swarm of Centipedes has its own trigger. **Project interpretation.** Apply the specific stable outcome in place of death from the qualifying damage, including [massive damage](combat.md#srd51-core-dropping-to-zero-hit-points). The effect assigns the target a stable condition at 0, which requires it to survive. Applying instant death first would prevent that stated outcome. This protection does not extend to a different damage instance. Death from an earlier damage component is not reversed. Curing this poison ends its linked paralysis. [SRD p. 374, Giant Centipede, Bite](../../references/srd-5.1/pages/page-374.md) [SRD p. 379, Giant Spider, Bite](../../references/srd-5.1/pages/page-379.md) [SRD p. 381, Giant Wasp, Sting continuation and Giant Wolf Spider, Bite](../../references/srd-5.1/pages/page-381.md) [SRD p. 386, Phase Spider, Bite](../../references/srd-5.1/pages/page-386.md) [SRD p. 391, Variant Insect Swarms, Swarm of Centipedes](../../references/srd-5.1/pages/page-391.md)

<a id="srd51-creature-rules-bite-disease-clock"></a>

### Recurring bite diseases

`srd51.creature-rules.bite-disease-clock`

Track the maximum HP lost to each disease separately. Lowering the [HP maximum](combat.md#srd51-core-hit-points) caps current HP at the new maximum. A cure ends the disease and any associated maximum HP penalty without restoring current HP or reviving a creature killed by the disease. The bite entries specify whether a tick calls for a save or reduces the maximum automatically. **Project interpretation.** A creature has at most one active instance of each distinct disease. Start the first 24-hour interval at infection. A further failed initial save against that same disease leaves the existing clock and penalty in place. The entries set a recurring interval for the ongoing disease and give no separate stacking or refresh behavior. Separate clocks would make repeat exposure drain maximum HP more than once per interval. [SRD p. 96, Hit Points](../../references/srd-5.1/pages/page-096.md) [SRD p. 370, Death Dog, Bite](../../references/srd-5.1/pages/page-370.md) [SRD p. 378, Variant Diseased Giant Rats](../../references/srd-5.1/pages/page-378.md)

## Creature profiles

<a id="srd51-creature-ape"></a>

## Ape

`srd51.creature.ape`

**Profile.** Medium beast · unaligned · CR 1/2 (100 XP) · 19 average HP (3d8 + 6) · AC 12 · movement walk 30 ft., climb 30 ft.

**Abilities.** Str 16 (+3) · Dex 14 (+2) · Con 14 (+2) · Int 6 (−2) · Wis 12 (+1) · Cha 7 (−2). Skill bonuses Athletics +5, Perception +3.

**Awareness and defenses.** Passive Perception 13. Languages none. [SRD p. 366, Ape](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-ape-multiattack"></a> **Action — Multiattack.** `srd51.creature.ape.multiattack` Make two Fist attacks. [SRD p. 366, Ape, Multiattack](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-ape-fist"></a> **Action — Fist.** `srd51.creature.ape.fist` Melee weapon, +5, reach 5 feet, one target. A hit deals 6 (1d6 + 3) bludgeoning damage. [SRD p. 366, Ape, Fist](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-ape-rock"></a> **Action — Rock.** `srd51.creature.ape.rock` Ranged weapon, +5, normal/long range 25/50 feet, one target. A hit deals 6 (1d6 + 3) bludgeoning damage. [SRD p. 366, Ape, Rock](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-awakened-shrub"></a>

## Awakened Shrub

`srd51.creature.awakened-shrub`

**Profile.** Small plant · unaligned · CR 0 (10 XP) · 10 average HP (3d6) · AC 9 · movement walk 20 ft.

**Abilities.** Str 3 (−4) · Dex 8 (−1) · Con 11 (+0) · Int 10 (+0) · Wis 10 (+0) · Cha 6 (−2).

**Awareness and defenses.** Passive Perception 10. Languages one language known by its creator. Defenses include vulnerable to fire · resists piercing. [SRD p. 366, Awakened Shrub](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-awakened-shrub-false-appearance"></a> **Trait — False Appearance.** `srd51.creature.awakened-shrub.false-appearance` Apply [False Appearance](creatures.md#srd51-creature-rules-false-appearance) as an ordinary shrub while motionless. [SRD p. 366, Awakened Shrub, False Appearance](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-awakened-shrub-rake"></a> **Action — Rake.** `srd51.creature.awakened-shrub.rake` Melee weapon, +1, reach 5 feet, one target. A hit deals 1 (1d4 − 1) slashing damage. [SRD p. 366, Awakened Shrub, Rake](../../references/srd-5.1/pages/page-366.md)

**Profile notes.** An ordinary shrub gains sentience and mobility through [Awaken](spells-a-c.md#srd51-spell-awaken) or similar magic. [SRD p. 366, Awakened Shrub, description](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-awakened-tree"></a>

## Awakened Tree

`srd51.creature.awakened-tree`

**Profile.** Huge plant · unaligned · CR 2 (450 XP) · 59 average HP (7d12 + 14) · AC 13 (natural armor) · movement walk 20 ft.

**Abilities.** Str 19 (+4) · Dex 6 (−2) · Con 15 (+2) · Int 10 (+0) · Wis 10 (+0) · Cha 7 (−2).

**Awareness and defenses.** Passive Perception 10. Languages one language known by its creator. Defenses include vulnerable to fire · resists bludgeoning, piercing. [SRD p. 366, Awakened Tree](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-awakened-tree-false-appearance"></a> **Trait — False Appearance.** `srd51.creature.awakened-tree.false-appearance` Apply [False Appearance](creatures.md#srd51-creature-rules-false-appearance) as an ordinary tree while motionless. [SRD p. 366, Awakened Tree, False Appearance](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-awakened-tree-slam"></a> **Action — Slam.** `srd51.creature.awakened-tree.slam` Melee weapon, +6, reach 10 feet, one target. A hit deals 14 (3d6 + 4) bludgeoning damage. [SRD p. 366, Awakened Tree, Slam](../../references/srd-5.1/pages/page-366.md)

**Profile notes.** An ordinary tree gains sentience and mobility through [Awaken](spells-a-c.md#srd51-spell-awaken) or similar magic. [SRD p. 366, Awakened Tree, description](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-creature-axe-beak"></a>

## Axe Beak

`srd51.creature.axe-beak`

**Profile.** Large beast · unaligned · CR 1/4 (50 XP) · 19 average HP (3d10 + 3) · AC 11 · movement walk 50 ft.

**Abilities.** Str 14 (+2) · Dex 12 (+1) · Con 12 (+1) · Int 2 (−4) · Wis 10 (+0) · Cha 5 (−3).

**Awareness and defenses.** Passive Perception 10. Languages none. [SRD p. 366, Axe Beak](../../references/srd-5.1/pages/page-366.md) [SRD p. 367, Axe Beak continuation](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-axe-beak-beak"></a> **Action — Beak.** `srd51.creature.axe-beak.beak` Melee weapon, +4, reach 5 feet, one target. A hit deals 6 (1d8 + 2) slashing damage. [SRD p. 367, Axe Beak, Beak](../../references/srd-5.1/pages/page-367.md)

**Profile notes.** A flightless bird that tends to attack unfamiliar creatures that approach it. [SRD p. 367, Axe Beak, description](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-baboon"></a>

## Baboon

`srd51.creature.baboon`

**Profile.** Small beast · unaligned · CR 0 (10 XP) · 3 average HP (1d6) · AC 12 · movement walk 30 ft., climb 30 ft.

**Abilities.** Str 8 (−1) · Dex 14 (+2) · Con 11 (+0) · Int 4 (−3) · Wis 12 (+1) · Cha 6 (−2).

**Awareness and defenses.** Passive Perception 11. Languages none. [SRD p. 367, Baboon](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-baboon-pack-tactics"></a> **Trait — Pack Tactics.** `srd51.creature.baboon.pack-tactics` Apply [Pack Tactics](creatures.md#srd51-creature-rules-pack-tactics). [SRD p. 367, Baboon, Pack Tactics](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-baboon-bite"></a> **Action — Bite.** `srd51.creature.baboon.bite` Melee weapon, +1, reach 5 feet, one target. A hit deals 1 (1d4 − 1) piercing damage. [SRD p. 367, Baboon, Bite](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-badger"></a>

## Badger

`srd51.creature.badger`

**Profile.** Tiny beast · unaligned · CR 0 (10 XP) · 3 average HP (1d4 + 1) · AC 10 · movement walk 20 ft., burrow 5 ft.

**Abilities.** Str 4 (−3) · Dex 11 (+0) · Con 12 (+1) · Int 2 (−4) · Wis 12 (+1) · Cha 5 (−3).

**Awareness and defenses.** Darkvision 30 ft., passive Perception 11. Languages none. [SRD p. 367, Badger](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-badger-keen-smell"></a> **Trait — Keen Smell.** `srd51.creature.badger.keen-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for smell. [SRD p. 367, Badger, Keen Smell](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-badger-bite"></a> **Action — Bite.** `srd51.creature.badger.bite` Melee weapon, +2, reach 5 feet, one target. A hit deals 1 piercing damage. [SRD p. 367, Badger, Bite](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-bat"></a>

## Bat

`srd51.creature.bat`

**Profile.** Tiny beast · unaligned · CR 0 (10 XP) · 1 average HP (1d4 − 1) · AC 12 · movement walk 5 ft., fly 30 ft.

**Abilities.** Str 2 (−4) · Dex 15 (+2) · Con 8 (−1) · Int 2 (−4) · Wis 12 (+1) · Cha 4 (−3).

**Awareness and defenses.** Blindsight 60 ft., passive Perception 11. Languages none. [SRD p. 367, Bat](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-bat-echolocation"></a> **Trait — Echolocation.** `srd51.creature.bat.echolocation` Apply [Echolocation](creatures.md#srd51-creature-rules-echolocation). [SRD p. 367, Bat, Echolocation](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-bat-keen-hearing"></a> **Trait — Keen Hearing.** `srd51.creature.bat.keen-hearing` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for hearing. [SRD p. 367, Bat, Keen Hearing](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-bat-bite"></a> **Action — Bite.** `srd51.creature.bat.bite` Melee weapon, +0, reach 5 feet, one creature. A hit deals 1 piercing damage. [SRD p. 367, Bat, Bite](../../references/srd-5.1/pages/page-367.md)

<a id="srd51-creature-black-bear"></a>

## Black Bear

`srd51.creature.black-bear`

**Profile.** Medium beast · unaligned · CR 1/2 (100 XP) · 19 average HP (3d8 + 6) · AC 11 (natural armor) · movement walk 40 ft., climb 30 ft.

**Abilities.** Str 15 (+2) · Dex 10 (+0) · Con 14 (+2) · Int 2 (−4) · Wis 12 (+1) · Cha 7 (−2). Skill bonuses Perception +3.

**Awareness and defenses.** Passive Perception 13. Languages none. [SRD p. 367, Black Bear](../../references/srd-5.1/pages/page-367.md) [SRD p. 368, Black Bear continuation](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-black-bear-keen-smell"></a> **Trait — Keen Smell.** `srd51.creature.black-bear.keen-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for smell. [SRD p. 368, Black Bear, Keen Smell](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-black-bear-multiattack"></a> **Action — Multiattack.** `srd51.creature.black-bear.multiattack` Make one Bite attack and one Claws attack. [SRD p. 368, Black Bear, Multiattack](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-black-bear-bite"></a> **Action — Bite.** `srd51.creature.black-bear.bite` Melee weapon, +3, reach 5 feet, one target. A hit deals 5 (1d6 + 2) piercing damage. [SRD p. 368, Black Bear, Bite](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-black-bear-claws"></a> **Action — Claws.** `srd51.creature.black-bear.claws` Melee weapon, +3, reach 5 feet, one target. A hit deals 7 (2d4 + 2) slashing damage. [SRD p. 368, Black Bear, Claws](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-blink-dog"></a>

## Blink Dog

`srd51.creature.blink-dog`

**Profile.** Medium fey · lawful good · CR 1/4 (50 XP) · 22 average HP (4d8 + 4) · AC 13 · movement walk 40 ft.

**Abilities.** Str 12 (+1) · Dex 17 (+3) · Con 12 (+1) · Int 10 (+0) · Wis 13 (+1) · Cha 11 (+0). Skill bonuses Perception +3, Stealth +5.

**Awareness and defenses.** Passive Perception 13. Languages Blink Dog, understands Sylvan but can’t speak it. [SRD p. 368, Blink Dog](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-blink-dog-keen-hearing-and-smell"></a> **Trait — Keen Hearing and Smell.** `srd51.creature.blink-dog.keen-hearing-and-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for hearing and smell. [SRD p. 368, Blink Dog, Keen Hearing and Smell](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-blink-dog-bite"></a> **Action — Bite.** `srd51.creature.blink-dog.bite` Melee weapon, +3, reach 5 feet, one target. A hit deals 4 (1d6 + 1) piercing damage. [SRD p. 368, Blink Dog, Bite](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-blink-dog-teleport"></a> **Action — Teleport (Recharge 4–6).** `srd51.creature.blink-dog.teleport` Magically move up to 40 feet to an unoccupied space the blink dog can see, bringing worn and carried equipment. It may make one Bite attack before or after teleporting. [SRD p. 368, Blink Dog, Teleport](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-blood-hawk"></a>

## Blood Hawk

`srd51.creature.blood-hawk`

**Profile.** Small beast · unaligned · CR 1/8 (25 XP) · 7 average HP (2d6) · AC 12 · movement walk 10 ft., fly 60 ft.

**Abilities.** Str 6 (−2) · Dex 14 (+2) · Con 10 (+0) · Int 3 (−4) · Wis 14 (+2) · Cha 5 (−3). Skill bonuses Perception +4.

**Awareness and defenses.** Passive Perception 14. Languages none. [SRD p. 368, Blood Hawk](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-blood-hawk-keen-sight"></a> **Trait — Keen Sight.** `srd51.creature.blood-hawk.keen-sight` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for sight. [SRD p. 368, Blood Hawk, Keen Sight](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-blood-hawk-pack-tactics"></a> **Trait — Pack Tactics.** `srd51.creature.blood-hawk.pack-tactics` Apply [Pack Tactics](creatures.md#srd51-creature-rules-pack-tactics). [SRD p. 368, Blood Hawk, Pack Tactics](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-blood-hawk-beak"></a> **Action — Beak.** `srd51.creature.blood-hawk.beak` Melee weapon, +4, reach 5 feet, one target. A hit deals 4 (1d4 + 2) piercing damage. [SRD p. 368, Blood Hawk, Beak](../../references/srd-5.1/pages/page-368.md)

**Profile notes.** Blood hawks often flock and hunt together, attacking a broad range of prey. [SRD p. 368, Blood Hawk, description](../../references/srd-5.1/pages/page-368.md)

<a id="srd51-creature-boar"></a>

## Boar

`srd51.creature.boar`

**Profile.** Medium beast · unaligned · CR 1/4 (50 XP) · 11 average HP (2d8 + 2) · AC 11 (natural armor) · movement walk 40 ft.

**Abilities.** Str 13 (+1) · Dex 11 (+0) · Con 12 (+1) · Int 2 (−4) · Wis 9 (−1) · Cha 5 (−3).

**Awareness and defenses.** Passive Perception 9. Languages none. [SRD p. 368, Boar](../../references/srd-5.1/pages/page-368.md) [SRD p. 369, Boar continuation](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-boar-charge"></a> **Trait — Charge.** `srd51.creature.boar.charge` Apply [Charge](creatures-misc-a-g.md#srd51-creature-rules-charge) after a Tusk hit. Add 3 (1d6) slashing damage and use DC 11 Strength for the possible prone result. [SRD p. 368, Boar, Charge](../../references/srd-5.1/pages/page-368.md) [SRD p. 369, Boar, Charge continuation](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-boar-relentless"></a> **Trait — Relentless (Recharges after a Short or Long Rest).** `srd51.creature.boar.relentless` Apply [Relentless](creatures-misc-a-g.md#srd51-creature-rules-relentless) with a damage threshold of 7. [SRD p. 369, Boar, Relentless](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-boar-tusk"></a> **Action — Tusk.** `srd51.creature.boar.tusk` Melee weapon, +3, reach 5 feet, one target. A hit deals 4 (1d6 + 1) slashing damage. [SRD p. 369, Boar, Tusk](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-brown-bear"></a>

## Brown Bear

`srd51.creature.brown-bear`

**Profile.** Large beast · unaligned · CR 1 (200 XP) · 34 average HP (4d10 + 12) · AC 11 (natural armor) · movement walk 40 ft., climb 30 ft.

**Abilities.** Str 19 (+4) · Dex 10 (+0) · Con 16 (+3) · Int 2 (−4) · Wis 13 (+1) · Cha 7 (−2). Skill bonuses Perception +3.

**Awareness and defenses.** Passive Perception 13. Languages none. [SRD p. 369, Brown Bear](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-brown-bear-keen-smell"></a> **Trait — Keen Smell.** `srd51.creature.brown-bear.keen-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for smell. [SRD p. 369, Brown Bear, Keen Smell](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-brown-bear-multiattack"></a> **Action — Multiattack.** `srd51.creature.brown-bear.multiattack` Make one Bite attack and one Claws attack. [SRD p. 369, Brown Bear, Multiattack](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-brown-bear-bite"></a> **Action — Bite.** `srd51.creature.brown-bear.bite` Melee weapon, +5, reach 5 feet, one target. A hit deals 8 (1d8 + 4) piercing damage. [SRD p. 369, Brown Bear, Bite](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-brown-bear-claws"></a> **Action — Claws.** `srd51.creature.brown-bear.claws` Melee weapon, +5, reach 5 feet, one target. A hit deals 11 (2d6 + 4) slashing damage. [SRD p. 369, Brown Bear, Claws](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-camel"></a>

## Camel

`srd51.creature.camel`

**Profile.** Large beast · unaligned · CR 1/8 (25 XP) · 15 average HP (2d10 + 4) · AC 9 · movement walk 50 ft.

**Abilities.** Str 16 (+3) · Dex 8 (−1) · Con 14 (+2) · Int 2 (−4) · Wis 8 (−1) · Cha 5 (−3).

**Awareness and defenses.** Passive Perception 9. Languages none. [SRD p. 369, Camel](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-camel-bite"></a> **Action — Bite.** `srd51.creature.camel.bite` Melee weapon, +5, reach 5 feet, one target. A hit deals 2 (1d4) bludgeoning damage. [SRD p. 369, Camel, Bite](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-cat"></a>

## Cat

`srd51.creature.cat`

**Profile.** Tiny beast · unaligned · CR 0 (10 XP) · 2 average HP (1d4) · AC 12 · movement walk 40 ft., climb 30 ft.

**Abilities.** Str 3 (−4) · Dex 15 (+2) · Con 10 (+0) · Int 3 (−4) · Wis 12 (+1) · Cha 7 (−2). Skill bonuses Perception +3, Stealth +4.

**Awareness and defenses.** Passive Perception 13. Languages none. [SRD p. 369, Cat](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-cat-keen-smell"></a> **Trait — Keen Smell.** `srd51.creature.cat.keen-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for smell. [SRD p. 369, Cat, Keen Smell](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-cat-claws"></a> **Action — Claws.** `srd51.creature.cat.claws` Melee weapon, +0, reach 5 feet, one target. A hit deals 1 slashing damage. [SRD p. 369, Cat, Claws](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-constrictor-snake"></a>

## Constrictor Snake

`srd51.creature.constrictor-snake`

**Profile.** Large beast · unaligned · CR 1/4 (50 XP) · 13 average HP (2d10 + 2) · AC 12 · movement walk 30 ft., swim 30 ft.

**Abilities.** Str 15 (+2) · Dex 14 (+2) · Con 12 (+1) · Int 1 (−5) · Wis 10 (+0) · Cha 3 (−4).

**Awareness and defenses.** Blindsight 10 ft., passive Perception 10. Languages none. [SRD p. 369, Constrictor Snake](../../references/srd-5.1/pages/page-369.md) [SRD p. 370, Constrictor Snake continuation](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-constrictor-snake-bite"></a> **Action — Bite.** `srd51.creature.constrictor-snake.bite` Melee weapon, +4, reach 5 feet, one creature. A hit deals 5 (1d6 + 2) piercing damage. [SRD p. 369, Constrictor Snake, Bite](../../references/srd-5.1/pages/page-369.md)

<a id="srd51-creature-constrictor-snake-constrict"></a> **Action — Constrict.** `srd51.creature.constrictor-snake.constrict` Melee weapon, +4, reach 5 feet, one creature. A hit deals 6 (1d8 + 2) bludgeoning damage. The hit also grapples the target (escape DC 14). Until the grapple ends, the target is restrained and the attacker cannot Constrict another target. [SRD p. 370, Constrictor Snake, Constrict](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-crab"></a>

## Crab

`srd51.creature.crab`

**Profile.** Tiny beast · unaligned · CR 0 (10 XP) · 2 average HP (1d4) · AC 11 (natural armor) · movement walk 20 ft., swim 20 ft.

**Abilities.** Str 2 (−4) · Dex 11 (+0) · Con 10 (+0) · Int 1 (−5) · Wis 8 (−1) · Cha 2 (−4). Skill bonuses Stealth +2.

**Awareness and defenses.** Blindsight 30 ft., passive Perception 9. Languages none. [SRD p. 370, Crab](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-crab-amphibious"></a> **Trait — Amphibious.** `srd51.creature.crab.amphibious` Apply [Amphibious](creatures.md#srd51-creature-rules-amphibious). [SRD p. 370, Crab, Amphibious](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-crab-claw"></a> **Action — Claw.** `srd51.creature.crab.claw` Melee weapon, +0, reach 5 feet, one target. A hit deals 1 bludgeoning damage. [SRD p. 370, Crab, Claw](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-crocodile"></a>

## Crocodile

`srd51.creature.crocodile`

**Profile.** Large beast · unaligned · CR 1/2 (100 XP) · 19 average HP (3d10 + 3) · AC 12 (natural armor) · movement walk 20 ft., swim 30 ft.

**Abilities.** Str 15 (+2) · Dex 10 (+0) · Con 13 (+1) · Int 2 (−4) · Wis 10 (+0) · Cha 5 (−3). Skill bonuses Stealth +2.

**Awareness and defenses.** Passive Perception 10. Languages none. [SRD p. 370, Crocodile](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-crocodile-hold-breath"></a> **Trait — Hold Breath.** `srd51.creature.crocodile.hold-breath` It can hold its breath for 15 minutes. [SRD p. 370, Crocodile, Hold Breath](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-crocodile-bite"></a> **Action — Bite.** `srd51.creature.crocodile.bite` Melee weapon, +4, reach 5 feet, one creature. A hit deals 7 (1d10 + 2) piercing damage. The hit also grapples the target (escape DC 12). Until the grapple ends, the target is restrained and the attacker cannot Bite another target. [SRD p. 370, Crocodile, Bite](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-death-dog"></a>

## Death Dog

`srd51.creature.death-dog`

**Profile.** Medium monstrosity · neutral evil · CR 1 (200 XP) · 39 average HP (6d8 + 12) · AC 12 · movement walk 40 ft.

**Abilities.** Str 15 (+2) · Dex 14 (+2) · Con 14 (+2) · Int 3 (−4) · Wis 13 (+1) · Cha 6 (−2). Skill bonuses Perception +5, Stealth +4.

**Awareness and defenses.** Darkvision 120 ft., passive Perception 15. Languages none. [SRD p. 370, Death Dog](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-death-dog-two-headed"></a> **Trait — Two-Headed.** `srd51.creature.death-dog.two-headed` Gain advantage on Wisdom (Perception) checks and on saves against being blinded, charmed, deafened, frightened, stunned, or knocked unconscious. [SRD p. 370, Death Dog, Two-Headed](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-death-dog-multiattack"></a> **Action — Multiattack.** `srd51.creature.death-dog.multiattack` Make two Bite attacks. [SRD p. 370, Death Dog, Multiattack](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-death-dog-bite"></a> **Action — Bite.** `srd51.creature.death-dog.bite` Melee weapon, +4, reach 5 feet, one target. A hit deals 5 (1d6 + 2) piercing damage. For a creature target, roll a DC 12 Constitution save against disease. On failure it contracts a disease that keeps it poisoned until cured. While infected it rolls that save once every 24 hours. Each failure subtracts 5 (1d10) from its HP maximum until cure. Reaching a maximum of 0 this way kills it. Use the [recurring bite disease clock](#srd51-creature-rules-bite-disease-clock). [SRD p. 370, Death Dog, Bite](../../references/srd-5.1/pages/page-370.md)

<a id="srd51-creature-deer"></a>

## Deer

`srd51.creature.deer`

**Profile.** Medium beast · unaligned · CR 0 (10 XP) · 4 average HP (1d8) · AC 13 · movement walk 50 ft.

**Abilities.** Str 11 (+0) · Dex 16 (+3) · Con 11 (+0) · Int 2 (−4) · Wis 14 (+2) · Cha 5 (−3).

**Awareness and defenses.** Passive Perception 12. Languages none. [SRD p. 370, Deer](../../references/srd-5.1/pages/page-370.md) [SRD p. 371, Deer continuation](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-deer-bite"></a> **Action — Bite.** `srd51.creature.deer.bite` Melee weapon, +2, reach 5 feet, one target. A hit deals 2 (1d4) piercing damage. [SRD p. 371, Deer, Bite](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-dire-wolf"></a>

## Dire Wolf

`srd51.creature.dire-wolf`

**Profile.** Large beast · unaligned · CR 1 (200 XP) · 37 average HP (5d10 + 10) · AC 14 (natural armor) · movement walk 50 ft.

**Abilities.** Str 17 (+3) · Dex 15 (+2) · Con 15 (+2) · Int 3 (−4) · Wis 12 (+1) · Cha 7 (−2). Skill bonuses Perception +3, Stealth +4.

**Awareness and defenses.** Passive Perception 13. Languages none. [SRD p. 371, Dire Wolf](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-dire-wolf-keen-hearing-and-smell"></a> **Trait — Keen Hearing and Smell.** `srd51.creature.dire-wolf.keen-hearing-and-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for hearing and smell. [SRD p. 371, Dire Wolf, Keen Hearing and Smell](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-dire-wolf-pack-tactics"></a> **Trait — Pack Tactics.** `srd51.creature.dire-wolf.pack-tactics` Apply [Pack Tactics](creatures.md#srd51-creature-rules-pack-tactics). [SRD p. 371, Dire Wolf, Pack Tactics](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-dire-wolf-bite"></a> **Action — Bite.** `srd51.creature.dire-wolf.bite` Melee weapon, +5, reach 5 feet, one target. A hit deals 10 (2d6 + 3) piercing damage. A creature target must also succeed on a DC 13 Strength save or fall prone. [SRD p. 371, Dire Wolf, Bite](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-draft-horse"></a>

## Draft Horse

`srd51.creature.draft-horse`

**Profile.** Large beast · unaligned · CR 1/4 (50 XP) · 19 average HP (3d10 + 3) · AC 10 · movement walk 40 ft.

**Abilities.** Str 18 (+4) · Dex 10 (+0) · Con 12 (+1) · Int 2 (−4) · Wis 11 (+0) · Cha 7 (−2).

**Awareness and defenses.** Passive Perception 10. Languages none. [SRD p. 371, Draft Horse](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-draft-horse-hooves"></a> **Action — Hooves.** `srd51.creature.draft-horse.hooves` Melee weapon, +6, reach 5 feet, one target. A hit deals 9 (2d4 + 4) bludgeoning damage. [SRD p. 371, Draft Horse, Hooves](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-eagle"></a>

## Eagle

`srd51.creature.eagle`

**Profile.** Small beast · unaligned · CR 0 (10 XP) · 3 average HP (1d6) · AC 12 · movement walk 10 ft., fly 60 ft.

**Abilities.** Str 6 (−2) · Dex 15 (+2) · Con 10 (+0) · Int 2 (−4) · Wis 14 (+2) · Cha 7 (−2). Skill bonuses Perception +4.

**Awareness and defenses.** Passive Perception 14. Languages none. [SRD p. 371, Eagle](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-eagle-keen-sight"></a> **Trait — Keen Sight.** `srd51.creature.eagle.keen-sight` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for sight. [SRD p. 371, Eagle, Keen Sight](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-eagle-talons"></a> **Action — Talons.** `srd51.creature.eagle.talons` Melee weapon, +4, reach 5 feet, one target. A hit deals 4 (1d4 + 2) slashing damage. [SRD p. 371, Eagle, Talons](../../references/srd-5.1/pages/page-371.md)

<a id="srd51-creature-elephant"></a>

## Elephant

`srd51.creature.elephant`

**Profile.** Huge beast · unaligned · CR 4 (1,100 XP) · 76 average HP (8d12 + 24) · AC 12 (natural armor) · movement walk 40 ft.

**Abilities.** Str 22 (+6) · Dex 9 (−1) · Con 17 (+3) · Int 3 (−4) · Wis 11 (+0) · Cha 6 (−2).

**Awareness and defenses.** Passive Perception 10. Languages none. [SRD p. 371, Elephant](../../references/srd-5.1/pages/page-371.md) [SRD p. 372, Elephant continuation](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-elephant-trampling-charge"></a> **Trait — Trampling Charge.** `srd51.creature.elephant.trampling-charge` Apply [the charging knockdown rule](creatures-misc-a-g.md#srd51-creature-rules-charge-and-bonus-attack) using Gore as the triggering hit and DC 12 Strength. If the target is prone after the hit and save, the bonus attack is Stomp. [SRD p. 372, Elephant, Trampling Charge](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-elephant-gore"></a> **Action — Gore.** `srd51.creature.elephant.gore` Melee weapon, +8, reach 5 feet, one target. A hit deals 19 (3d8 + 6) piercing damage. [SRD p. 372, Elephant, Gore](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-elephant-stomp"></a> **Action — Stomp.** `srd51.creature.elephant.stomp` Melee weapon, +8, reach 5 feet, one prone creature. A hit deals 22 (3d10 + 6) bludgeoning damage. [SRD p. 372, Elephant, Stomp](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-elk"></a>

## Elk

`srd51.creature.elk`

**Profile.** Large beast · unaligned · CR 1/4 (50 XP) · 13 average HP (2d10 + 2) · AC 10 · movement walk 50 ft.

**Abilities.** Str 16 (+3) · Dex 10 (+0) · Con 12 (+1) · Int 2 (−4) · Wis 10 (+0) · Cha 6 (−2).

**Awareness and defenses.** Passive Perception 10. Languages none. [SRD p. 372, Elk](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-elk-charge"></a> **Trait — Charge.** `srd51.creature.elk.charge` Apply [Charge](creatures-misc-a-g.md#srd51-creature-rules-charge) after a Ram hit. Add 7 (2d6) bludgeoning damage and use DC 13 Strength for the possible prone result. [SRD p. 372, Elk, Charge](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-elk-ram"></a> **Action — Ram.** `srd51.creature.elk.ram` Melee weapon, +5, reach 5 feet, one target. A hit deals 6 (1d6 + 3) bludgeoning damage. [SRD p. 372, Elk, Ram](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-elk-hooves"></a> **Action — Hooves.** `srd51.creature.elk.hooves` Melee weapon, +5, reach 5 feet, one prone creature. A hit deals 8 (2d4 + 3) bludgeoning damage. [SRD p. 372, Elk, Hooves](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-flying-snake"></a>

## Flying Snake

`srd51.creature.flying-snake`

**Profile.** Tiny beast · unaligned · CR 1/8 (25 XP) · 5 average HP (2d4) · AC 14 · movement walk 30 ft., fly 60 ft., swim 30 ft.

**Abilities.** Str 4 (−3) · Dex 18 (+4) · Con 11 (+0) · Int 2 (−4) · Wis 12 (+1) · Cha 5 (−3).

**Awareness and defenses.** Blindsight 10 ft., passive Perception 11. Languages none. [SRD p. 372, Flying Snake](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-flying-snake-flyby"></a> **Trait — Flyby.** `srd51.creature.flying-snake.flyby` Apply [Flyby](creatures-misc-a-g.md#srd51-creature-rules-flyby). [SRD p. 372, Flying Snake, Flyby](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-flying-snake-bite"></a> **Action — Bite.** `srd51.creature.flying-snake.bite` Melee weapon, +6, reach 5 feet, one target. A hit deals 1 piercing plus 7 (3d4) poison damage. [SRD p. 372, Flying Snake, Bite](../../references/srd-5.1/pages/page-372.md)

**Profile notes.** People sometimes train these winged snakes to deliver scrolls carried in their coils. [SRD p. 372, Flying Snake, description](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-frog"></a>

## Frog

`srd51.creature.frog`

**Profile.** Tiny beast · unaligned · CR 0 (0 XP) · 1 average HP (1d4 − 1) · AC 11 · movement walk 20 ft., swim 20 ft.

**Abilities.** Str 1 (−5) · Dex 13 (+1) · Con 8 (−1) · Int 1 (−5) · Wis 8 (−1) · Cha 3 (−4). Skill bonuses Perception +1, Stealth +3.

**Awareness and defenses.** Darkvision 30 ft., passive Perception 11. Languages none. [SRD p. 372, Frog](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-frog-amphibious"></a> **Trait — Amphibious.** `srd51.creature.frog.amphibious` Apply [Amphibious](creatures.md#srd51-creature-rules-amphibious). [SRD p. 372, Frog, Amphibious](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-frog-standing-leap"></a> **Trait — Standing Leap.** `srd51.creature.frog.standing-leap` Long jump up to 10 feet and high jump up to 5 feet, with or without a running start. [SRD p. 372, Frog, Standing Leap](../../references/srd-5.1/pages/page-372.md)

**Profile notes.** This profile has no effective attacks and can also represent an ordinary toad. [SRD p. 372, Frog, description](../../references/srd-5.1/pages/page-372.md)

<a id="srd51-creature-giant-ape"></a>

## Giant Ape

`srd51.creature.giant-ape`

**Profile.** Huge beast · unaligned · CR 7 (2,900 XP) · 157 average HP (15d12 + 60) · AC 12 · movement walk 40 ft., climb 40 ft.

**Abilities.** Str 23 (+6) · Dex 14 (+2) · Con 18 (+4) · Int 7 (−2) · Wis 12 (+1) · Cha 7 (−2). Skill bonuses Athletics +9, Perception +4.

**Awareness and defenses.** Passive Perception 14. Languages none. [SRD p. 373, Giant Ape](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-ape-multiattack"></a> **Action — Multiattack.** `srd51.creature.giant-ape.multiattack` Make two Fist attacks. [SRD p. 373, Giant Ape, Multiattack](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-ape-fist"></a> **Action — Fist.** `srd51.creature.giant-ape.fist` Melee weapon, +9, reach 10 feet, one target. A hit deals 22 (3d10 + 6) bludgeoning damage. [SRD p. 373, Giant Ape, Fist](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-ape-rock"></a> **Action — Rock.** `srd51.creature.giant-ape.rock` Ranged weapon, +9, normal/long range 50/100 feet, one target. A hit deals 30 (7d6 + 6) bludgeoning damage. [SRD p. 373, Giant Ape, Rock](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-badger"></a>

## Giant Badger

`srd51.creature.giant-badger`

**Profile.** Medium beast · unaligned · CR 1/4 (50 XP) · 13 average HP (2d8 + 4) · AC 10 · movement walk 30 ft., burrow 10 ft.

**Abilities.** Str 13 (+1) · Dex 10 (+0) · Con 15 (+2) · Int 2 (−4) · Wis 12 (+1) · Cha 5 (−3).

**Awareness and defenses.** Darkvision 30 ft., passive Perception 11. Languages none. [SRD p. 373, Giant Badger](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-badger-keen-smell"></a> **Trait — Keen Smell.** `srd51.creature.giant-badger.keen-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for smell. [SRD p. 373, Giant Badger, Keen Smell](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-badger-multiattack"></a> **Action — Multiattack.** `srd51.creature.giant-badger.multiattack` Make one Bite attack and one Claws attack. [SRD p. 373, Giant Badger, Multiattack](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-badger-bite"></a> **Action — Bite.** `srd51.creature.giant-badger.bite` Melee weapon, +3, reach 5 feet, one target. A hit deals 4 (1d6 + 1) piercing damage. [SRD p. 373, Giant Badger, Bite](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-badger-claws"></a> **Action — Claws.** `srd51.creature.giant-badger.claws` Melee weapon, +3, reach 5 feet, one target. A hit deals 6 (2d4 + 1) slashing damage. [SRD p. 373, Giant Badger, Claws](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-bat"></a>

## Giant Bat

`srd51.creature.giant-bat`

**Profile.** Large beast · unaligned · CR 1/4 (50 XP) · 22 average HP (4d10) · AC 13 · movement walk 10 ft., fly 60 ft.

**Abilities.** Str 15 (+2) · Dex 16 (+3) · Con 11 (+0) · Int 2 (−4) · Wis 12 (+1) · Cha 6 (−2).

**Awareness and defenses.** Blindsight 60 ft., passive Perception 11. Languages none. [SRD p. 373, Giant Bat](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-bat-echolocation"></a> **Trait — Echolocation.** `srd51.creature.giant-bat.echolocation` Apply [Echolocation](creatures.md#srd51-creature-rules-echolocation). [SRD p. 373, Giant Bat, Echolocation](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-bat-keen-hearing"></a> **Trait — Keen Hearing.** `srd51.creature.giant-bat.keen-hearing` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for hearing. [SRD p. 373, Giant Bat, Keen Hearing](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-bat-bite"></a> **Action — Bite.** `srd51.creature.giant-bat.bite` Melee weapon, +4, reach 5 feet, one creature. A hit deals 5 (1d6 + 2) piercing damage. [SRD p. 373, Giant Bat, Bite](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-boar"></a>

## Giant Boar

`srd51.creature.giant-boar`

**Profile.** Large beast · unaligned · CR 2 (450 XP) · 42 average HP (5d10 + 15) · AC 12 (natural armor) · movement walk 40 ft.

**Abilities.** Str 17 (+3) · Dex 10 (+0) · Con 16 (+3) · Int 2 (−4) · Wis 7 (−2) · Cha 5 (−3).

**Awareness and defenses.** Passive Perception 8. Languages none. [SRD p. 373, Giant Boar](../../references/srd-5.1/pages/page-373.md) [SRD p. 374, Giant Boar continuation](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-boar-charge"></a> **Trait — Charge.** `srd51.creature.giant-boar.charge` Apply [Charge](creatures-misc-a-g.md#srd51-creature-rules-charge) after a Tusk hit. Add 7 (2d6) slashing damage and use DC 13 Strength for the possible prone result. [SRD p. 373, Giant Boar, Charge](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-boar-relentless"></a> **Trait — Relentless (Recharges after a Short or Long Rest).** `srd51.creature.giant-boar.relentless` Apply [Relentless](creatures-misc-a-g.md#srd51-creature-rules-relentless) with a damage threshold of 10. [SRD p. 373, Giant Boar, Relentless](../../references/srd-5.1/pages/page-373.md)

<a id="srd51-creature-giant-boar-tusk"></a> **Action — Tusk.** `srd51.creature.giant-boar.tusk` Melee weapon, +5, reach 5 feet, one target. A hit deals 10 (2d6 + 3) slashing damage. [SRD p. 374, Giant Boar, Tusk](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-centipede"></a>

## Giant Centipede

`srd51.creature.giant-centipede`

**Profile.** Small beast · unaligned · CR 1/4 (50 XP) · 4 average HP (1d6 + 1) · AC 13 (natural armor) · movement walk 30 ft., climb 30 ft.

**Abilities.** Str 5 (−3) · Dex 14 (+2) · Con 12 (+1) · Int 1 (−5) · Wis 7 (−2) · Cha 3 (−4).

**Awareness and defenses.** Blindsight 30 ft., passive Perception 8. Languages none. [SRD p. 374, Giant Centipede](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-centipede-bite"></a> **Action — Bite.** `srd51.creature.giant-centipede.bite` Melee weapon, +4, reach 5 feet, one creature. A hit deals 4 (1d4 + 2) piercing damage. The target makes a DC 11 Constitution save and takes 10 (3d6) poison damage on failure, and none on success. If that poison damage reduces the target to 0 hit points, apply the [debilitating venom outcome](creatures-misc-a-g.md#srd51-creature-rules-debilitating-venom). [SRD p. 374, Giant Centipede, Bite](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-constrictor-snake"></a>

## Giant Constrictor Snake

`srd51.creature.giant-constrictor-snake`

**Profile.** Huge beast · unaligned · CR 2 (450 XP) · 60 average HP (8d12 + 8) · AC 12 · movement walk 30 ft., swim 30 ft.

**Abilities.** Str 19 (+4) · Dex 14 (+2) · Con 12 (+1) · Int 1 (−5) · Wis 10 (+0) · Cha 3 (−4). Skill bonuses Perception +2.

**Awareness and defenses.** Blindsight 10 ft., passive Perception 12. Languages none. [SRD p. 374, Giant Constrictor Snake](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-constrictor-snake-bite"></a> **Action — Bite.** `srd51.creature.giant-constrictor-snake.bite` Melee weapon, +6, reach 10 feet, one creature. A hit deals 11 (2d6 + 4) piercing damage. [SRD p. 374, Giant Constrictor Snake, Bite](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-constrictor-snake-constrict"></a> **Action — Constrict.** `srd51.creature.giant-constrictor-snake.constrict` Melee weapon, +6, reach 5 feet, one creature. A hit deals 13 (2d8 + 4) bludgeoning damage. The hit also grapples the target (escape DC 16). Until the grapple ends, the target is restrained and the attacker cannot Constrict another target. [SRD p. 374, Giant Constrictor Snake, Constrict](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-crab"></a>

## Giant Crab

`srd51.creature.giant-crab`

**Profile.** Medium beast · unaligned · CR 1/8 (25 XP) · 13 average HP (3d8) · AC 15 (natural armor) · movement walk 30 ft., swim 30 ft.

**Abilities.** Str 13 (+1) · Dex 15 (+2) · Con 11 (+0) · Int 1 (−5) · Wis 9 (−1) · Cha 3 (−4). Skill bonuses Stealth +4.

**Awareness and defenses.** Blindsight 30 ft., passive Perception 9. Languages none. [SRD p. 374, Giant Crab](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-crab-amphibious"></a> **Trait — Amphibious.** `srd51.creature.giant-crab.amphibious` Apply [Amphibious](creatures.md#srd51-creature-rules-amphibious). [SRD p. 374, Giant Crab, Amphibious](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-crab-claw"></a> **Action — Claw.** `srd51.creature.giant-crab.claw` Melee weapon, +3, reach 5 feet, one target. A hit deals 4 (1d6 + 1) bludgeoning damage. The hit grapples the target (escape DC 11). Each of the creature’s two claws can grapple one target. [SRD p. 374, Giant Crab, Claw](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-crocodile"></a>

## Giant Crocodile

`srd51.creature.giant-crocodile`

**Profile.** Huge beast · unaligned · CR 5 (1,800 XP) · 85 average HP (9d12 + 27) · AC 14 (natural armor) · movement walk 30 ft., swim 50 ft.

**Abilities.** Str 21 (+5) · Dex 9 (−1) · Con 17 (+3) · Int 2 (−4) · Wis 10 (+0) · Cha 7 (−2). Skill bonuses Stealth +5.

**Awareness and defenses.** Passive Perception 10. Languages none. [SRD p. 374, Giant Crocodile](../../references/srd-5.1/pages/page-374.md) [SRD p. 375, Giant Crocodile continuation](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-crocodile-hold-breath"></a> **Trait — Hold Breath.** `srd51.creature.giant-crocodile.hold-breath` It can hold its breath for 30 minutes. [SRD p. 374, Giant Crocodile, Hold Breath](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-crocodile-multiattack"></a> **Action — Multiattack.** `srd51.creature.giant-crocodile.multiattack` Make one Bite attack and one Tail attack. [SRD p. 374, Giant Crocodile, Multiattack](../../references/srd-5.1/pages/page-374.md)

<a id="srd51-creature-giant-crocodile-bite"></a> **Action — Bite.** `srd51.creature.giant-crocodile.bite` Melee weapon, +8, reach 5 feet, one target. A hit deals 21 (3d10 + 5) piercing damage. The hit also grapples the target (escape DC 16). Until the grapple ends, the target is restrained and the attacker cannot Bite another target. [SRD p. 375, Giant Crocodile, Bite](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-crocodile-tail"></a> **Action — Tail.** `srd51.creature.giant-crocodile.tail` Melee weapon, +8, reach 10 feet, one target outside the crocodile’s grapple. A hit deals 14 (2d8 + 5) bludgeoning damage. A creature target must also succeed on a DC 16 Strength save or fall prone. [SRD p. 375, Giant Crocodile, Tail](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-eagle"></a>

## Giant Eagle

`srd51.creature.giant-eagle`

**Profile.** Large beast · neutral good · CR 1 (200 XP) · 26 average HP (4d10 + 4) · AC 13 · movement walk 10 ft., fly 80 ft.

**Abilities.** Str 16 (+3) · Dex 17 (+3) · Con 13 (+1) · Int 8 (−1) · Wis 14 (+2) · Cha 10 (+0). Skill bonuses Perception +4.

**Awareness and defenses.** Passive Perception 14. Languages Giant Eagle, understands Common and Auran but can’t speak them. [SRD p. 375, Giant Eagle](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-eagle-keen-sight"></a> **Trait — Keen Sight.** `srd51.creature.giant-eagle.keen-sight` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for sight. [SRD p. 375, Giant Eagle, Keen Sight](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-eagle-multiattack"></a> **Action — Multiattack.** `srd51.creature.giant-eagle.multiattack` Make one Beak attack and one Talons attack. [SRD p. 375, Giant Eagle, Multiattack](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-eagle-beak"></a> **Action — Beak.** `srd51.creature.giant-eagle.beak` Melee weapon, +5, reach 5 feet, one target. A hit deals 6 (1d6 + 3) piercing damage. [SRD p. 375, Giant Eagle, Beak](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-eagle-talons"></a> **Action — Talons.** `srd51.creature.giant-eagle.talons` Melee weapon, +5, reach 5 feet, one target. A hit deals 10 (2d6 + 3) slashing damage. [SRD p. 375, Giant Eagle, Talons](../../references/srd-5.1/pages/page-375.md)

**Profile notes.** A breeding pair normally has up to four eggs or young in its nest. Use the [Eagle](#srd51-creature-eagle) profile for its young. [SRD p. 375, Giant Eagle, description](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-elk"></a>

## Giant Elk

`srd51.creature.giant-elk`

**Profile.** Huge beast · unaligned · CR 2 (450 XP) · 42 average HP (5d12 + 10) · AC 14 (natural armor) · movement walk 60 ft.

**Abilities.** Str 19 (+4) · Dex 16 (+3) · Con 14 (+2) · Int 7 (−2) · Wis 14 (+2) · Cha 10 (+0). Skill bonuses Perception +4.

**Awareness and defenses.** Passive Perception 14. Languages Giant Elk, understands Common, Elvish, and Sylvan but can’t speak them. [SRD p. 375, Giant Elk](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-elk-charge"></a> **Trait — Charge.** `srd51.creature.giant-elk.charge` Apply [Charge](creatures-misc-a-g.md#srd51-creature-rules-charge) after a Ram hit. Add 7 (2d6) bludgeoning damage and use DC 14 Strength for the possible prone result. [SRD p. 375, Giant Elk, Charge](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-elk-ram"></a> **Action — Ram.** `srd51.creature.giant-elk.ram` Melee weapon, +6, reach 10 feet, one target. A hit deals 11 (2d6 + 4) bludgeoning damage. [SRD p. 375, Giant Elk, Ram](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-elk-hooves"></a> **Action — Hooves.** `srd51.creature.giant-elk.hooves` Melee weapon, +6, reach 5 feet, one prone creature. A hit deals 22 (4d8 + 4) bludgeoning damage. [SRD p. 375, Giant Elk, Hooves](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-fire-beetle"></a>

## Giant Fire Beetle

`srd51.creature.giant-fire-beetle`

**Profile.** Small beast · unaligned · CR 0 (10 XP) · 4 average HP (1d6 + 1) · AC 13 (natural armor) · movement walk 30 ft.

**Abilities.** Str 8 (−1) · Dex 10 (+0) · Con 12 (+1) · Int 1 (−5) · Wis 7 (−2) · Cha 3 (−4).

**Awareness and defenses.** Blindsight 30 ft., passive Perception 8. Languages none. [SRD p. 375, Giant Fire Beetle](../../references/srd-5.1/pages/page-375.md) [SRD p. 376, Giant Fire Beetle continuation](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-fire-beetle-illumination"></a> **Trait — Illumination.** `srd51.creature.giant-fire-beetle.illumination` Shed bright light to 10 feet and dim light for 10 more feet. Its luminous glands keep shedding this light for 1d6 days after death. [SRD p. 376, Giant Fire Beetle, description](../../references/srd-5.1/pages/page-376.md) [SRD p. 375, Giant Fire Beetle, Illumination](../../references/srd-5.1/pages/page-375.md)

<a id="srd51-creature-giant-fire-beetle-bite"></a> **Action — Bite.** `srd51.creature.giant-fire-beetle.bite` Melee weapon, +1, reach 5 feet, one target. A hit deals 2 (1d6 − 1) slashing damage. [SRD p. 376, Giant Fire Beetle, Bite](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-frog"></a>

## Giant Frog

`srd51.creature.giant-frog`

**Profile.** Medium beast · unaligned · CR 1/4 (50 XP) · 18 average HP (4d8) · AC 11 · movement walk 30 ft., swim 30 ft.

**Abilities.** Str 12 (+1) · Dex 13 (+1) · Con 11 (+0) · Int 2 (−4) · Wis 10 (+0) · Cha 3 (−4). Skill bonuses Perception +2, Stealth +3.

**Awareness and defenses.** Darkvision 30 ft., passive Perception 12. Languages none. [SRD p. 376, Giant Frog](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-frog-amphibious"></a> **Trait — Amphibious.** `srd51.creature.giant-frog.amphibious` Apply [Amphibious](creatures.md#srd51-creature-rules-amphibious). [SRD p. 376, Giant Frog, Amphibious](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-frog-standing-leap"></a> **Trait — Standing Leap.** `srd51.creature.giant-frog.standing-leap` Long jump up to 20 feet and high jump up to 10 feet, with or without a running start. [SRD p. 376, Giant Frog, Standing Leap](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-frog-bite"></a> **Action — Bite.** `srd51.creature.giant-frog.bite` Melee weapon, +3, reach 5 feet, one target. A hit deals 4 (1d6 + 1) piercing damage. The hit also grapples the target (escape DC 11). Until the grapple ends, the target is restrained and the attacker cannot Bite another target. [SRD p. 376, Giant Frog, Bite](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-frog-swallow"></a> **Action — Swallow.** `srd51.creature.giant-frog.swallow` Apply [Swallow](creatures-misc-a-g.md#srd51-creature-rules-swallow) to a Small or smaller target the creature grapples. A swallowed target takes 5 (2d4) acid damage at the start of each turn of the creature that swallowed it. Exiting its corpse costs 5 feet of movement. [SRD p. 376, Giant Frog, Swallow](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-goat"></a>

## Giant Goat

`srd51.creature.giant-goat`

**Profile.** Large beast · unaligned · CR 1/2 (100 XP) · 19 average HP (3d10 + 3) · AC 11 (natural armor) · movement walk 40 ft.

**Abilities.** Str 17 (+3) · Dex 11 (+0) · Con 12 (+1) · Int 3 (−4) · Wis 12 (+1) · Cha 6 (−2).

**Awareness and defenses.** Passive Perception 11. Languages none. [SRD p. 376, Giant Goat](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-goat-charge"></a> **Trait — Charge.** `srd51.creature.giant-goat.charge` Apply [Charge](creatures-misc-a-g.md#srd51-creature-rules-charge) after a Ram hit. Add 5 (2d4) bludgeoning damage and use DC 13 Strength for the possible prone result. [SRD p. 376, Giant Goat, Charge](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-goat-sure-footed"></a> **Trait — Sure-Footed.** `srd51.creature.giant-goat.sure-footed` Apply [Sure-Footed](creatures-misc-a-g.md#srd51-creature-rules-sure-footed). [SRD p. 376, Giant Goat, Sure-Footed](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-goat-ram"></a> **Action — Ram.** `srd51.creature.giant-goat.ram` Melee weapon, +5, reach 5 feet, one target. A hit deals 8 (2d4 + 3) bludgeoning damage. [SRD p. 376, Giant Goat, Ram](../../references/srd-5.1/pages/page-376.md)

<a id="srd51-creature-giant-hyena"></a>

## Giant Hyena

`srd51.creature.giant-hyena`

**Profile.** Large beast · unaligned · CR 1 (200 XP) · 45 average HP (6d10 + 12) · AC 12 · movement walk 50 ft.

**Abilities.** Str 16 (+3) · Dex 14 (+2) · Con 14 (+2) · Int 2 (−4) · Wis 12 (+1) · Cha 7 (−2). Skill bonuses Perception +3.

**Awareness and defenses.** Passive Perception 13. Languages none. [SRD p. 376, Giant Hyena](../../references/srd-5.1/pages/page-376.md) [SRD p. 377, Giant Hyena continuation](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-hyena-rampage"></a> **Trait — Rampage.** `srd51.creature.giant-hyena.rampage` Apply [Rampage](creatures.md#srd51-creature-rules-rampage). [SRD p. 376, Giant Hyena, Rampage](../../references/srd-5.1/pages/page-376.md) [SRD p. 377, Giant Hyena, Rampage continuation](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-hyena-bite"></a> **Action — Bite.** `srd51.creature.giant-hyena.bite` Melee weapon, +5, reach 5 feet, one target. A hit deals 10 (2d6 + 3) piercing damage. [SRD p. 377, Giant Hyena, Bite](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-lizard"></a>

## Giant Lizard

`srd51.creature.giant-lizard`

**Profile.** Large beast · unaligned · CR 1/4 (50 XP) · 19 average HP (3d10 + 3) · AC 12 (natural armor) · movement walk 30 ft., climb 30 ft.

**Abilities.** Str 15 (+2) · Dex 12 (+1) · Con 13 (+1) · Int 2 (−4) · Wis 10 (+0) · Cha 5 (−3).

**Awareness and defenses.** Darkvision 30 ft., passive Perception 10. Languages none. [SRD p. 377, Giant Lizard](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-lizard-bite"></a> **Action — Bite.** `srd51.creature.giant-lizard.bite` Melee weapon, +4, reach 5 feet, one target. A hit deals 6 (1d8 + 2) piercing damage. [SRD p. 377, Giant Lizard, Bite](../../references/srd-5.1/pages/page-377.md)

**Profile notes.** It can be ridden or used as a draft animal. Lizardfolk keep them as pets, and subterranean peoples use them as mounts and pack animals. [SRD p. 377, Giant Lizard, description](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-octopus"></a>

## Giant Octopus

`srd51.creature.giant-octopus`

**Profile.** Large beast · unaligned · CR 1 (200 XP) · 52 average HP (8d10 + 8) · AC 11 · movement walk 10 ft., swim 60 ft.

**Abilities.** Str 17 (+3) · Dex 13 (+1) · Con 13 (+1) · Int 4 (−3) · Wis 10 (+0) · Cha 4 (−3). Skill bonuses Perception +4, Stealth +5.

**Awareness and defenses.** Darkvision 60 ft., passive Perception 14. Languages none. [SRD p. 377, Giant Octopus](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-octopus-hold-breath"></a> **Trait — Hold Breath.** `srd51.creature.giant-octopus.hold-breath` It can hold its breath for 1 hour while out of water. [SRD p. 377, Giant Octopus, Hold Breath](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-octopus-underwater-camouflage"></a> **Trait — Underwater Camouflage.** `srd51.creature.giant-octopus.underwater-camouflage` Apply [Underwater Camouflage](creatures-misc-a-g.md#srd51-creature-rules-underwater-camouflage). [SRD p. 377, Giant Octopus, Underwater Camouflage](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-octopus-water-breathing"></a> **Trait — Water Breathing.** `srd51.creature.giant-octopus.water-breathing` Apply [Water Breathing](creatures-misc-a-g.md#srd51-creature-rules-water-breathing). [SRD p. 377, Giant Octopus, Water Breathing](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-octopus-tentacles"></a> **Action — Tentacles.** `srd51.creature.giant-octopus.tentacles` Melee weapon, +5, reach 15 feet, one target. A hit deals 10 (2d6 + 3) bludgeoning damage. A creature target is also grappled (escape DC 16). Until the grapple ends, the target is restrained and the attacker cannot use Tentacles on another target. [SRD p. 377, Giant Octopus, Tentacles](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-octopus-ink-cloud"></a> **Action — Ink Cloud (Recharges after a Short or Long Rest).** `srd51.creature.giant-octopus.ink-cloud` Apply [Ink Cloud](creatures-misc-a-g.md#srd51-creature-rules-ink-cloud) with a 20-foot radius. [SRD p. 377, Giant Octopus, Ink Cloud](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-owl"></a>

## Giant Owl

`srd51.creature.giant-owl`

**Profile.** Large beast · neutral · CR 1/4 (50 XP) · 19 average HP (3d10 + 3) · AC 12 · movement walk 5 ft., fly 60 ft.

**Abilities.** Str 13 (+1) · Dex 15 (+2) · Con 12 (+1) · Int 8 (−1) · Wis 13 (+1) · Cha 10 (+0). Skill bonuses Perception +5, Stealth +4.

**Awareness and defenses.** Darkvision 120 ft., passive Perception 15. Languages Giant Owl, understands Common, Elvish, and Sylvan but can’t speak them. [SRD p. 377, Giant Owl](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-owl-flyby"></a> **Trait — Flyby.** `srd51.creature.giant-owl.flyby` Apply [Flyby](creatures-misc-a-g.md#srd51-creature-rules-flyby). [SRD p. 377, Giant Owl, Flyby](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-owl-keen-hearing-and-sight"></a> **Trait — Keen Hearing and Sight.** `srd51.creature.giant-owl.keen-hearing-and-sight` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for hearing and sight. [SRD p. 377, Giant Owl, Keen Hearing and Sight](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-owl-talons"></a> **Action — Talons.** `srd51.creature.giant-owl.talons` Melee weapon, +3, reach 5 feet, one target. A hit deals 8 (2d6 + 1) slashing damage. [SRD p. 377, Giant Owl, Talons](../../references/srd-5.1/pages/page-377.md)

<a id="srd51-creature-giant-poisonous-snake"></a>

## Giant Poisonous Snake

`srd51.creature.giant-poisonous-snake`

**Profile.** Medium beast · unaligned · CR 1/4 (50 XP) · 11 average HP (2d8 + 2) · AC 14 · movement walk 30 ft., swim 30 ft.

**Abilities.** Str 10 (+0) · Dex 18 (+4) · Con 13 (+1) · Int 2 (−4) · Wis 10 (+0) · Cha 3 (−4). Skill bonuses Perception +2.

**Awareness and defenses.** Blindsight 10 ft., passive Perception 12. Languages none. [SRD p. 378, Giant Poisonous Snake](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-poisonous-snake-bite"></a> **Action — Bite.** `srd51.creature.giant-poisonous-snake.bite` Melee weapon, +6, reach 10 feet, one target. A hit deals 6 (1d4 + 4) piercing damage. The target makes a DC 11 Constitution save and takes 10 (3d6) poison damage on failure, half on success. [SRD p. 378, Giant Poisonous Snake, Bite](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-rat"></a>

## Giant Rat

`srd51.creature.giant-rat`

**Profile.** Small beast · unaligned · CR 1/8 (25 XP) · 7 average HP (2d6) · AC 12 · movement walk 30 ft.

**Abilities.** Str 7 (−2) · Dex 15 (+2) · Con 11 (+0) · Int 2 (−4) · Wis 10 (+0) · Cha 4 (−3).

**Awareness and defenses.** Darkvision 60 ft., passive Perception 10. Languages none. [SRD p. 378, Giant Rat](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-rat-keen-smell"></a> **Trait — Keen Smell.** `srd51.creature.giant-rat.keen-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for smell. [SRD p. 378, Giant Rat, Keen Smell](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-rat-pack-tactics"></a> **Trait — Pack Tactics.** `srd51.creature.giant-rat.pack-tactics` Apply [Pack Tactics](creatures.md#srd51-creature-rules-pack-tactics). [SRD p. 378, Giant Rat, Pack Tactics](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-rat-bite"></a> **Action — Bite.** `srd51.creature.giant-rat.bite` Melee weapon, +4, reach 5 feet, one target. A hit deals 4 (1d4 + 2) piercing damage. [SRD p. 378, Giant Rat, Bite](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-rat-diseased-variant"></a> **Variant — Diseased Giant Rat.** `srd51.creature.giant-rat.diseased-variant` Keep this profile at CR 1/8 (25 XP) and replace only its normal Bite with Diseased Bite. [SRD p. 378, Variant Diseased Giant Rats](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-rat-diseased-bite"></a> **Action — Diseased Bite.** `srd51.creature.giant-rat.diseased-bite` Melee weapon, +4, reach 5 feet, one target. A hit deals 4 (1d4 + 2) piercing damage. A creature target that fails a DC 10 Constitution save contracts a disease. While diseased it can regain hit points only by magic and loses 3 (1d6) from its hit point maximum every 24 hours, without another save. It dies if the reduction reaches a maximum of 0. Use the [recurring bite disease clock](#srd51-creature-rules-bite-disease-clock). **Project interpretation.** Apply the shared maximum-restoration outcome on cure. The source introduces the healing restriction and the reductions with the same ‘until the disease is cured’ clause. A narrower reading would only stop future reductions. [SRD p. 378, Giant Rat, variant Bite](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-scorpion"></a>

## Giant Scorpion

`srd51.creature.giant-scorpion`

**Profile.** Large beast · unaligned · CR 3 (700 XP) · 52 average HP (7d10 + 14) · AC 15 (natural armor) · movement walk 40 ft.

**Abilities.** Str 15 (+2) · Dex 13 (+1) · Con 15 (+2) · Int 1 (−5) · Wis 9 (−1) · Cha 3 (−4).

**Awareness and defenses.** Blindsight 60 ft., passive Perception 9. Languages none. [SRD p. 378, Giant Scorpion](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-scorpion-multiattack"></a> **Action — Multiattack.** `srd51.creature.giant-scorpion.multiattack` Make two Claw attacks and one Sting attack. [SRD p. 378, Giant Scorpion, Multiattack](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-scorpion-claw"></a> **Action — Claw.** `srd51.creature.giant-scorpion.claw` Melee weapon, +4, reach 5 feet, one target. A hit deals 6 (1d8 + 2) bludgeoning damage. The hit grapples the target (escape DC 12). Each of the creature’s two claws can grapple one target. [SRD p. 378, Giant Scorpion, Claw](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-scorpion-sting"></a> **Action — Sting.** `srd51.creature.giant-scorpion.sting` Melee weapon, +4, reach 5 feet, one creature. A hit deals 7 (1d10 + 2) piercing damage. The target makes a DC 12 Constitution save and takes 22 (4d10) poison damage on failure, half on success. [SRD p. 378, Giant Scorpion, Sting](../../references/srd-5.1/pages/page-378.md)

<a id="srd51-creature-giant-sea-horse"></a>

## Giant Sea Horse

`srd51.creature.giant-sea-horse`

**Profile.** Large beast · unaligned · CR 1/2 (100 XP) · 16 average HP (3d10) · AC 13 (natural armor) · movement walk 0 ft., swim 40 ft.

**Abilities.** Str 12 (+1) · Dex 15 (+2) · Con 11 (+0) · Int 2 (−4) · Wis 12 (+1) · Cha 5 (−3).

**Awareness and defenses.** Passive Perception 11. Languages none. [SRD p. 378, Giant Sea Horse](../../references/srd-5.1/pages/page-378.md) [SRD p. 379, Giant Sea Horse continuation](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-sea-horse-charge"></a> **Trait — Charge.** `srd51.creature.giant-sea-horse.charge` Apply [Charge](creatures-misc-a-g.md#srd51-creature-rules-charge) after a Ram hit. Add 7 (2d6) bludgeoning damage and use DC 11 Strength for the possible prone result. [SRD p. 379, Giant Sea Horse, Charge](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-sea-horse-water-breathing"></a> **Trait — Water Breathing.** `srd51.creature.giant-sea-horse.water-breathing` Apply [Water Breathing](creatures-misc-a-g.md#srd51-creature-rules-water-breathing). [SRD p. 379, Giant Sea Horse, Water Breathing](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-sea-horse-ram"></a> **Action — Ram.** `srd51.creature.giant-sea-horse.ram` Melee weapon, +3, reach 5 feet, one target. A hit deals 4 (1d6 + 1) bludgeoning damage. [SRD p. 379, Giant Sea Horse, Ram](../../references/srd-5.1/pages/page-379.md)

**Profile notes.** Aquatic elves train these animals as mounts. [SRD p. 379, Giant Sea Horse, description](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-shark"></a>

## Giant Shark

`srd51.creature.giant-shark`

**Profile.** Huge beast · unaligned · CR 5 (1,800 XP) · 126 average HP (11d12 + 55) · AC 13 (natural armor) · movement walk 0 ft., swim 50 ft.

**Abilities.** Str 23 (+6) · Dex 11 (+0) · Con 21 (+5) · Int 1 (−5) · Wis 10 (+0) · Cha 5 (−3). Skill bonuses Perception +3.

**Awareness and defenses.** Blindsight 60 ft., passive Perception 13. Languages none. [SRD p. 379, Giant Shark](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-shark-blood-frenzy"></a> **Trait — Blood Frenzy.** `srd51.creature.giant-shark.blood-frenzy` Apply [Blood Frenzy](creatures-misc-a-g.md#srd51-creature-rules-blood-frenzy). [SRD p. 379, Giant Shark, Blood Frenzy](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-shark-water-breathing"></a> **Trait — Water Breathing.** `srd51.creature.giant-shark.water-breathing` Apply [Water Breathing](creatures-misc-a-g.md#srd51-creature-rules-water-breathing). [SRD p. 379, Giant Shark, Water Breathing](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-shark-bite"></a> **Action — Bite.** `srd51.creature.giant-shark.bite` Melee weapon, +9, reach 5 feet, one target. A hit deals 22 (3d10 + 6) piercing damage. [SRD p. 379, Giant Shark, Bite](../../references/srd-5.1/pages/page-379.md)

**Profile notes.** A typical specimen is 30 feet long, inhabits deep seas, and may attack whales and ships as prey. [SRD p. 379, Giant Shark, description](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-spider"></a>

## Giant Spider

`srd51.creature.giant-spider`

**Profile.** Large beast · unaligned · CR 1 (200 XP) · 26 average HP (4d10 + 4) · AC 14 (natural armor) · movement walk 30 ft., climb 30 ft.

**Abilities.** Str 14 (+2) · Dex 16 (+3) · Con 12 (+1) · Int 2 (−4) · Wis 11 (+0) · Cha 4 (−3). Skill bonuses Stealth +7.

**Awareness and defenses.** Blindsight 10 ft., darkvision 60 ft., passive Perception 10. Languages none. [SRD p. 379, Giant Spider](../../references/srd-5.1/pages/page-379.md) [SRD p. 380, Giant Spider continuation](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-spider-spider-climb"></a> **Trait — Spider Climb.** `srd51.creature.giant-spider.spider-climb` Apply [Spider Climb](creatures.md#srd51-creature-rules-spider-climb). [SRD p. 379, Giant Spider, Spider Climb](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-spider-web-sense"></a> **Trait — Web Sense.** `srd51.creature.giant-spider.web-sense` Apply [Web Sense](creatures.md#srd51-creature-rules-web-sense). [SRD p. 379, Giant Spider, Web Sense](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-spider-web-walker"></a> **Trait — Web Walker.** `srd51.creature.giant-spider.web-walker` Apply [Web Walker](creatures.md#srd51-creature-rules-web-walker). [SRD p. 379, Giant Spider, Web Walker](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-spider-bite"></a> **Action — Bite.** `srd51.creature.giant-spider.bite` Melee weapon, +5, reach 5 feet, one creature. A hit deals 7 (1d8 + 3) piercing damage. The target makes a DC 11 Constitution save and takes 9 (2d8) poison damage on failure, half on success. If that poison damage reduces the target to 0 hit points, apply the [debilitating venom outcome](creatures-misc-a-g.md#srd51-creature-rules-debilitating-venom). [SRD p. 379, Giant Spider, Bite](../../references/srd-5.1/pages/page-379.md)

<a id="srd51-creature-giant-spider-web"></a> **Action — Web (Recharge 5–6).** `srd51.creature.giant-spider.web` Ranged weapon, +5, normal/long range 30/60 feet, one creature. A hit restrains the creature in webbing. The creature can spend an action on a DC 12 Strength check, bursting the web on success. The web can be attacked and destroyed. It has AC 10, 5 hit points, fire vulnerability, and bludgeoning, poison, and psychic immunity. [SRD p. 379, Giant Spider, Web](../../references/srd-5.1/pages/page-379.md)

**Profile notes.** These spiders spin webs to trap prey as well as firing strands with the Web action. They favor underground ceilings and crevices for lairs. [SRD p. 380, Giant Spider, description](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-toad"></a>

## Giant Toad

`srd51.creature.giant-toad`

**Profile.** Large beast · unaligned · CR 1 (200 XP) · 39 average HP (6d10 + 6) · AC 11 · movement walk 20 ft., swim 40 ft.

**Abilities.** Str 15 (+2) · Dex 13 (+1) · Con 13 (+1) · Int 2 (−4) · Wis 10 (+0) · Cha 3 (−4).

**Awareness and defenses.** Darkvision 30 ft., passive Perception 10. Languages none. [SRD p. 380, Giant Toad](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-toad-amphibious"></a> **Trait — Amphibious.** `srd51.creature.giant-toad.amphibious` Apply [Amphibious](creatures.md#srd51-creature-rules-amphibious). [SRD p. 380, Giant Toad, Amphibious](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-toad-standing-leap"></a> **Trait — Standing Leap.** `srd51.creature.giant-toad.standing-leap` Long jump up to 20 feet and high jump up to 10 feet, with or without a running start. [SRD p. 380, Giant Toad, Standing Leap](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-toad-bite"></a> **Action — Bite.** `srd51.creature.giant-toad.bite` Melee weapon, +4, reach 5 feet, one target. A hit deals 7 (1d10 + 2) piercing plus 5 (1d10) poison damage. The hit also grapples the target (escape DC 13). Until the grapple ends, the target is restrained and the attacker cannot Bite another target. [SRD p. 380, Giant Toad, Bite](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-toad-swallow"></a> **Action — Swallow.** `srd51.creature.giant-toad.swallow` Apply [Swallow](creatures-misc-a-g.md#srd51-creature-rules-swallow) to a Medium or smaller target the creature grapples. A swallowed target takes 10 (3d6) acid damage at the start of each turn of the creature that swallowed it. Exiting its corpse costs 5 feet of movement. [SRD p. 380, Giant Toad, Swallow](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-vulture"></a>

## Giant Vulture

`srd51.creature.giant-vulture`

**Profile.** Large beast · neutral evil · CR 1 (200 XP) · 22 average HP (3d10 + 6) · AC 10 · movement walk 10 ft., fly 60 ft.

**Abilities.** Str 15 (+2) · Dex 10 (+0) · Con 15 (+2) · Int 6 (−2) · Wis 12 (+1) · Cha 7 (−2). Skill bonuses Perception +3.

**Awareness and defenses.** Passive Perception 13. Languages understands Common but can’t speak. [SRD p. 380, Giant Vulture](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-vulture-keen-sight-and-smell"></a> **Trait — Keen Sight and Smell.** `srd51.creature.giant-vulture.keen-sight-and-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for sight and smell. [SRD p. 380, Giant Vulture, Keen Sight and Smell](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-vulture-pack-tactics"></a> **Trait — Pack Tactics.** `srd51.creature.giant-vulture.pack-tactics` Apply [Pack Tactics](creatures.md#srd51-creature-rules-pack-tactics). [SRD p. 380, Giant Vulture, Pack Tactics](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-vulture-multiattack"></a> **Action — Multiattack.** `srd51.creature.giant-vulture.multiattack` Make one Beak attack and one Talons attack. [SRD p. 380, Giant Vulture, Multiattack](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-vulture-beak"></a> **Action — Beak.** `srd51.creature.giant-vulture.beak` Melee weapon, +4, reach 5 feet, one target. A hit deals 7 (2d4 + 2) piercing damage. [SRD p. 380, Giant Vulture, Beak](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-vulture-talons"></a> **Action — Talons.** `srd51.creature.giant-vulture.talons` Melee weapon, +4, reach 5 feet, one target. A hit deals 9 (2d6 + 2) slashing damage. [SRD p. 380, Giant Vulture, Talons](../../references/srd-5.1/pages/page-380.md)

**Profile notes.** It may attack wounded prey or stalk a thirsty, starving creature for days. [SRD p. 380, Giant Vulture, description](../../references/srd-5.1/pages/page-380.md)

<a id="srd51-creature-giant-wasp"></a>

## Giant Wasp

`srd51.creature.giant-wasp`

**Profile.** Medium beast · unaligned · CR 1/2 (100 XP) · 13 average HP (3d8) · AC 12 · movement walk 10 ft., fly 50 ft.

**Abilities.** Str 10 (+0) · Dex 14 (+2) · Con 10 (+0) · Int 1 (−5) · Wis 10 (+0) · Cha 3 (−4).

**Awareness and defenses.** Passive Perception 10. Languages none. [SRD p. 380, Giant Wasp](../../references/srd-5.1/pages/page-380.md) [SRD p. 381, Giant Wasp continuation](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-giant-wasp-sting"></a> **Action — Sting.** `srd51.creature.giant-wasp.sting` Melee weapon, +4, reach 5 feet, one creature. A hit deals 5 (1d6 + 2) piercing damage. The target makes a DC 11 Constitution save and takes 10 (3d6) poison damage on failure, half on success. If that poison damage reduces the target to 0 hit points, apply the [debilitating venom outcome](creatures-misc-a-g.md#srd51-creature-rules-debilitating-venom). [SRD p. 380, Giant Wasp, Sting](../../references/srd-5.1/pages/page-380.md) [SRD p. 381, Giant Wasp, Sting continuation](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-giant-weasel"></a>

## Giant Weasel

`srd51.creature.giant-weasel`

**Profile.** Medium beast · unaligned · CR 1/8 (25 XP) · 9 average HP (2d8) · AC 13 · movement walk 40 ft.

**Abilities.** Str 11 (+0) · Dex 16 (+3) · Con 10 (+0) · Int 4 (−3) · Wis 12 (+1) · Cha 5 (−3). Skill bonuses Perception +3, Stealth +5.

**Awareness and defenses.** Darkvision 60 ft., passive Perception 13. Languages none. [SRD p. 381, Giant Weasel](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-giant-weasel-keen-hearing-and-smell"></a> **Trait — Keen Hearing and Smell.** `srd51.creature.giant-weasel.keen-hearing-and-smell` Apply [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for hearing and smell. [SRD p. 381, Giant Weasel, Keen Hearing and Smell](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-giant-weasel-bite"></a> **Action — Bite.** `srd51.creature.giant-weasel.bite` Melee weapon, +5, reach 5 feet, one target. A hit deals 5 (1d4 + 3) piercing damage. [SRD p. 381, Giant Weasel, Bite](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-giant-wolf-spider"></a>

## Giant Wolf Spider

`srd51.creature.giant-wolf-spider`

**Profile.** Medium beast · unaligned · CR 1/4 (50 XP) · 11 average HP (2d8 + 2) · AC 13 · movement walk 40 ft., climb 40 ft.

**Abilities.** Str 12 (+1) · Dex 16 (+3) · Con 13 (+1) · Int 3 (−4) · Wis 12 (+1) · Cha 4 (−3). Skill bonuses Perception +3, Stealth +7.

**Awareness and defenses.** Blindsight 10 ft., darkvision 60 ft., passive Perception 13. Languages none. [SRD p. 381, Giant Wolf Spider](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-giant-wolf-spider-spider-climb"></a> **Trait — Spider Climb.** `srd51.creature.giant-wolf-spider.spider-climb` Apply [Spider Climb](creatures.md#srd51-creature-rules-spider-climb). [SRD p. 381, Giant Wolf Spider, Spider Climb](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-giant-wolf-spider-web-sense"></a> **Trait — Web Sense.** `srd51.creature.giant-wolf-spider.web-sense` Apply [Web Sense](creatures.md#srd51-creature-rules-web-sense). [SRD p. 381, Giant Wolf Spider, Web Sense](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-giant-wolf-spider-web-walker"></a> **Trait — Web Walker.** `srd51.creature.giant-wolf-spider.web-walker` Apply [Web Walker](creatures.md#srd51-creature-rules-web-walker). [SRD p. 381, Giant Wolf Spider, Web Walker](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-giant-wolf-spider-bite"></a> **Action — Bite.** `srd51.creature.giant-wolf-spider.bite` Melee weapon, +3, reach 5 feet, one creature. A hit deals 4 (1d6 + 1) piercing damage. The target makes a DC 11 Constitution save and takes 7 (2d6) poison damage on failure, half on success. If that poison damage reduces the target to 0 hit points, apply the [debilitating venom outcome](creatures-misc-a-g.md#srd51-creature-rules-debilitating-venom). [SRD p. 381, Giant Wolf Spider, Bite](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-goat"></a>

## Goat

`srd51.creature.goat`

**Profile.** Medium beast · unaligned · CR 0 (10 XP) · 4 average HP (1d8) · AC 10 · movement walk 40 ft.

**Abilities.** Str 12 (+1) · Dex 10 (+0) · Con 11 (+0) · Int 2 (−4) · Wis 10 (+0) · Cha 5 (−3).

**Awareness and defenses.** Passive Perception 10. Languages none. [SRD p. 381, Goat](../../references/srd-5.1/pages/page-381.md) [SRD p. 382, Goat continuation](../../references/srd-5.1/pages/page-382.md)

<a id="srd51-creature-goat-charge"></a> **Trait — Charge.** `srd51.creature.goat.charge` Apply [Charge](creatures-misc-a-g.md#srd51-creature-rules-charge) after a Ram hit. Add 2 (1d4) bludgeoning damage and use DC 10 Strength for the possible prone result. [SRD p. 381, Goat, Charge](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-goat-sure-footed"></a> **Trait — Sure-Footed.** `srd51.creature.goat.sure-footed` Apply [Sure-Footed](creatures-misc-a-g.md#srd51-creature-rules-sure-footed). [SRD p. 381, Goat, Sure-Footed](../../references/srd-5.1/pages/page-381.md)

<a id="srd51-creature-goat-ram"></a> **Action — Ram.** `srd51.creature.goat.ram` Melee weapon, +3, reach 5 feet, one target. A hit deals 3 (1d4 + 1) bludgeoning damage. [SRD p. 382, Goat, Ram](../../references/srd-5.1/pages/page-382.md)

## Worked cases

- **Ordinary.** An elephant moves 20 feet straight toward a creature and hits it with Gore. On a failed DC 12 Strength save, the target falls prone. The elephant may spend its bonus action on Stomp if that same target remains prone and within 5 feet. [SRD p. 372, Elephant, Trampling Charge, Gore and Stomp](../../references/srd-5.1/pages/page-372.md)
- **Boundary.** A boar with Relentless available and 7 HP takes 7 damage and drops to 1 HP, expending the trait. An 8-damage hit would exceed the threshold. [SRD p. 369, Boar, Relentless](../../references/srd-5.1/pages/page-369.md)
- **Exception.** A giant centipede's piercing damage takes a target to 0 before the poison damage is resolved. The poison damage did not reduce it to 0, so the special stabilization and hour of poison do not trigger. [SRD p. 374, Giant Centipede, Bite](../../references/srd-5.1/pages/page-374.md)
- **Invalid.** A giant frog grappling a Medium creature cannot use Swallow on it. A giant toad can use its version on a Medium creature it is grappling. [SRD p. 376, Giant Frog, Swallow](../../references/srd-5.1/pages/page-376.md) [SRD p. 380, Giant Toad, Swallow](../../references/srd-5.1/pages/page-380.md)
- **Continuation.** Killing a giant fire beetle leaves its glands glowing for 1d6 days. The listed illumination remains bright for 10 feet and dim for the next 10 feet. [SRD p. 375, Giant Fire Beetle, Illumination](../../references/srd-5.1/pages/page-375.md) [SRD p. 376, Giant Fire Beetle, description](../../references/srd-5.1/pages/page-376.md)

## Source coverage

| Source page | Profiles and continuations |
| --- | --- |
| [SRD p. 366, Miscellaneous Creatures](../../references/srd-5.1/pages/page-366.md) | [Ape](#srd51-creature-ape) · [Awakened Shrub](#srd51-creature-awakened-shrub) · [Awakened Tree](#srd51-creature-awakened-tree) · [Axe Beak](#srd51-creature-axe-beak) |
| [SRD p. 367, Miscellaneous Creatures](../../references/srd-5.1/pages/page-367.md) | [Axe Beak](#srd51-creature-axe-beak) continuation · [Baboon](#srd51-creature-baboon) · [Badger](#srd51-creature-badger) · [Bat](#srd51-creature-bat) · [Black Bear](#srd51-creature-black-bear) |
| [SRD p. 368, Miscellaneous Creatures](../../references/srd-5.1/pages/page-368.md) | [Black Bear](#srd51-creature-black-bear) continuation · [Blink Dog](#srd51-creature-blink-dog) · [Blood Hawk](#srd51-creature-blood-hawk) · [Boar](#srd51-creature-boar) |
| [SRD p. 369, Miscellaneous Creatures](../../references/srd-5.1/pages/page-369.md) | [Boar](#srd51-creature-boar) continuation · [Brown Bear](#srd51-creature-brown-bear) · [Camel](#srd51-creature-camel) · [Cat](#srd51-creature-cat) · [Constrictor Snake](#srd51-creature-constrictor-snake) |
| [SRD p. 370, Miscellaneous Creatures](../../references/srd-5.1/pages/page-370.md) | [Constrictor Snake](#srd51-creature-constrictor-snake) continuation · [Crab](#srd51-creature-crab) · [Crocodile](#srd51-creature-crocodile) · [Death Dog](#srd51-creature-death-dog) · [Deer](#srd51-creature-deer) |
| [SRD p. 371, Miscellaneous Creatures](../../references/srd-5.1/pages/page-371.md) | [Deer](#srd51-creature-deer) continuation · [Dire Wolf](#srd51-creature-dire-wolf) · [Draft Horse](#srd51-creature-draft-horse) · [Eagle](#srd51-creature-eagle) · [Elephant](#srd51-creature-elephant) |
| [SRD p. 372, Miscellaneous Creatures](../../references/srd-5.1/pages/page-372.md) | [Elephant](#srd51-creature-elephant) continuation · [Elk](#srd51-creature-elk) · [Flying Snake](#srd51-creature-flying-snake) · [Frog](#srd51-creature-frog) |
| [SRD p. 373, Miscellaneous Creatures](../../references/srd-5.1/pages/page-373.md) | [Giant Ape](#srd51-creature-giant-ape) · [Giant Badger](#srd51-creature-giant-badger) · [Giant Bat](#srd51-creature-giant-bat) · [Giant Boar](#srd51-creature-giant-boar) |
| [SRD p. 374, Miscellaneous Creatures](../../references/srd-5.1/pages/page-374.md) | [Giant Boar](#srd51-creature-giant-boar) continuation · [Giant Centipede](#srd51-creature-giant-centipede) · [Giant Constrictor Snake](#srd51-creature-giant-constrictor-snake) · [Giant Crab](#srd51-creature-giant-crab) · [Giant Crocodile](#srd51-creature-giant-crocodile) |
| [SRD p. 375, Miscellaneous Creatures](../../references/srd-5.1/pages/page-375.md) | [Giant Crocodile](#srd51-creature-giant-crocodile) continuation · [Giant Eagle](#srd51-creature-giant-eagle) · [Giant Elk](#srd51-creature-giant-elk) · [Giant Fire Beetle](#srd51-creature-giant-fire-beetle) |
| [SRD p. 376, Miscellaneous Creatures](../../references/srd-5.1/pages/page-376.md) | [Giant Fire Beetle](#srd51-creature-giant-fire-beetle) continuation · [Giant Frog](#srd51-creature-giant-frog) · [Giant Goat](#srd51-creature-giant-goat) · [Giant Hyena](#srd51-creature-giant-hyena) |
| [SRD p. 377, Miscellaneous Creatures](../../references/srd-5.1/pages/page-377.md) | [Giant Hyena](#srd51-creature-giant-hyena) continuation · [Giant Lizard](#srd51-creature-giant-lizard) · [Giant Octopus](#srd51-creature-giant-octopus) · [Giant Owl](#srd51-creature-giant-owl) |
| [SRD p. 378, Miscellaneous Creatures](../../references/srd-5.1/pages/page-378.md) | [Giant Poisonous Snake](#srd51-creature-giant-poisonous-snake) · [Giant Rat](#srd51-creature-giant-rat) · [Giant Scorpion](#srd51-creature-giant-scorpion) · [Giant Sea Horse](#srd51-creature-giant-sea-horse) |
| [SRD p. 379, Miscellaneous Creatures](../../references/srd-5.1/pages/page-379.md) | [Giant Sea Horse](#srd51-creature-giant-sea-horse) continuation · [Giant Shark](#srd51-creature-giant-shark) · [Giant Spider](#srd51-creature-giant-spider) |
| [SRD p. 380, Miscellaneous Creatures](../../references/srd-5.1/pages/page-380.md) | [Giant Spider](#srd51-creature-giant-spider) continuation · [Giant Toad](#srd51-creature-giant-toad) · [Giant Vulture](#srd51-creature-giant-vulture) · [Giant Wasp](#srd51-creature-giant-wasp) |
| [SRD p. 381, Miscellaneous Creatures](../../references/srd-5.1/pages/page-381.md) | [Giant Wasp](#srd51-creature-giant-wasp) continuation · [Giant Weasel](#srd51-creature-giant-weasel) · [Giant Wolf Spider](#srd51-creature-giant-wolf-spider) · [Goat](#srd51-creature-goat) |
| [SRD p. 382, Miscellaneous Creatures](../../references/srd-5.1/pages/page-382.md) | [Goat](#srd51-creature-goat) continuation |
