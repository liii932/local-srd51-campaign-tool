# Creatures from Medusa through Ogre Zombie

Each profile lists the creature's base statistics and its separate trait, action, reaction, and
legendary options. Attack reach and range are measured in feet. In an attack entry, +N is the
attack bonus, and a damage expression includes the listed average and dice. AC is Armor Class,
HP is hit points, CR is challenge rating, and XP is experience points. A tag or defense applies
as stated in its profile. The shared [creature rules](creatures.md#srd51-creature-rules-stat-block)
supply the common resolution, including [recharge and daily uses](creatures.md#srd51-creature-rules-limited-usage)
and [legendary actions](creatures.md#srd51-creature-rules-legendary-actions). The
[creature index](creature-index.md) lists the other profiles.

<a id="srd51-creature-medusa"></a>

## Medusa

`srd51.creature.medusa`

- **Profile.** Medium monstrosity, lawful evil. AC 15 (natural armor). HP 127 (17d8 + 51). Speed 30 ft. CR 6 (2,300 XP).
- **Ability scores.** Str 10 (+0), Dex 15 (+2), Con 16 (+3), Int 12 (+1), Wis 13 (+1), Cha 15 (+2).
- **Skills.** Deception +5, Insight +4, Perception +4, Stealth +5.
- **Senses.** darkvision 60 ft, passive Perception 14.
- **Languages.** Common.

- <a id="srd51-creature-medusa-petrifying-gaze"></a> **Trait — Petrifying Gaze.** `srd51.creature.medusa.petrifying-gaze` When a creature starts its turn within 30 feet and can see the medusa's
  eyes, the medusa may force a DC 14 Constitution save if it sees the creature and is not incapacitated.
  Failure by at least 5 petrifies immediately. Any other failure restrains as the target begins turning
  to stone. At the end of the target's next turn, a repeat save ends the effect on success or petrifies
  on failure. [Greater Restoration](spells-e-g.md#srd51-spell-greater-restoration) or other magic can
  undo petrification. Unless surprised, a creature may avert its eyes at its turn start to avoid that
  save and cannot see the medusa until the start of its next turn. If it looks at the medusa before
  then, it saves immediately. The medusa suffers its own gaze if it sees its reflection on a polished
  surface within 30 feet in bright light.
- <a id="srd51-creature-medusa-multiattack"></a> **Action — Multiattack.** `srd51.creature.medusa.multiattack` Make either one Snake Hair and two Shortsword attacks, or two Longbow
  attacks.
- <a id="srd51-creature-medusa-snake-hair"></a> **Action — Snake Hair.** `srd51.creature.medusa.snake-hair` Melee weapon attack, +5 to hit, 5-foot reach, one creature. Hit deals 4 (1d4
  + 2) piercing plus 14 (4d6) poison.
- <a id="srd51-creature-medusa-shortsword"></a> **Action — Shortsword.** `srd51.creature.medusa.shortsword` Melee weapon attack, +5, reach 5 feet, one target. Hit deals 5 (1d6 + 2)
  piercing.
- <a id="srd51-creature-medusa-longbow"></a> **Action — Longbow.** `srd51.creature.medusa.longbow` Ranged weapon attack, +5, range 150/600 feet, one target. Hit deals 6 (1d8 + 2)
  piercing plus 7 (2d6) poison.

[SRD p. 330, Medusa](../../references/srd-5.1/pages/page-330.md) · [SRD p. 150, Greater Restoration](../../references/srd-5.1/pages/page-150.md)

<a id="srd51-creature-dust-mephit"></a>

## Dust Mephit

`srd51.creature.dust-mephit`

- **Profile.** Small elemental, neutral evil. AC 12. HP 17 (5d6). Speed 30 ft, fly 30 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 5 (−3), Dex 14 (+2), Con 10 (+0), Int 9 (−1), Wis 11 (+0), Cha 10 (+0).
- **Skills.** Perception +2, Stealth +4.
- **Vulnerabilities.** fire.
- **Damage immunity.** poison.
- **Condition immunity.** poisoned.
- **Senses.** darkvision 60 ft, passive Perception 12.
- **Languages.** Auran, Terran.

- <a id="srd51-creature-dust-mephit-death-burst"></a> **Trait — Death Burst.** `srd51.creature.dust-mephit.death-burst` Its death blasts dust within 5 feet. Each creature there makes a DC 10 Constitution save.
  Failure causes blindness for up to 1 minute, ending early on a successful repeat save during
  one of that creature's turns. **Project interpretation.** Allow one repeat at a point it chooses during each of its own
  turns, with no action cost. The trait says on its turn and supplies neither an exact moment nor an
  action requirement. Forcing a turn-end save would add the timing that Blinding Breath states
  explicitly.
- <a id="srd51-creature-dust-mephit-innate-spellcasting"></a> **Trait — Innate Spellcasting.** `srd51.creature.dust-mephit.innate-spellcasting` Charisma. Cast [Sleep](spells-n-s.md#srd51-spell-sleep) once per day
  without material components.
- <a id="srd51-creature-dust-mephit-claws"></a> **Action — Claws.** `srd51.creature.dust-mephit.claws` Melee weapon attack, +4, reach 5 feet, one creature. Hit deals 4 (1d4 + 2)
  slashing.
- <a id="srd51-creature-dust-mephit-blinding-breath"></a> **Action — Blinding Breath.** `srd51.creature.dust-mephit.blinding-breath` Recharge 6. Creatures in a 15-foot cone save Dexterity DC 10 or are
  blinded for 1 minute. Each may repeat the save at its turn end, recovering on success.

[SRD p. 330, Dust Mephit](../../references/srd-5.1/pages/page-330.md) · [SRD p. 331, Dust Mephit continuation](../../references/srd-5.1/pages/page-331.md) · [SRD p. 180, Sleep](../../references/srd-5.1/pages/page-180.md)

<a id="srd51-creature-ice-mephit"></a>

## Ice Mephit

`srd51.creature.ice-mephit`

- **Profile.** Small elemental, neutral evil. AC 11. HP 21 (6d6). Speed 30 ft, fly 30 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 7 (−2), Dex 13 (+1), Con 10 (+0), Int 9 (−1), Wis 11 (+0), Cha 12 (+1).
- **Skills.** Perception +2, Stealth +3.
- **Vulnerabilities.** bludgeoning, fire.
- **Damage immunity.** cold, poison.
- **Condition immunity.** poisoned.
- **Senses.** darkvision 60 ft, passive Perception 12.
- **Languages.** Aquan, Auran.

- <a id="srd51-creature-ice-mephit-death-burst"></a> **Trait — Death Burst.** `srd51.creature.ice-mephit.death-burst` On death, each creature within 5 feet saves Dexterity DC 10 against 4 (1d8)
  slashing damage, halved on success.
- <a id="srd51-creature-ice-mephit-false-appearance"></a> **Trait — False Appearance.** `srd51.creature.ice-mephit.false-appearance` [False appearance](creatures.md#srd51-creature-rules-false-appearance)
  as an ordinary shard of ice.
- <a id="srd51-creature-ice-mephit-innate-spellcasting"></a> **Trait — Innate Spellcasting.** `srd51.creature.ice-mephit.innate-spellcasting` Charisma. Cast [Fog Cloud](spells-e-g.md#srd51-spell-fog-cloud) once
  per day without material components.
- <a id="srd51-creature-ice-mephit-claws"></a> **Action — Claws.** `srd51.creature.ice-mephit.claws` Melee weapon attack, +3, reach 5 feet, one creature. Hit deals 3 (1d4 + 1)
  slashing plus 2 (1d4) cold.
- <a id="srd51-creature-ice-mephit-frost-breath"></a> **Action — Frost Breath.** `srd51.creature.ice-mephit.frost-breath` Recharge 6. Creatures in a 15-foot cone save Dexterity DC 10 against 5
  (2d4) cold damage, halved on success.

[SRD p. 331, Ice Mephit](../../references/srd-5.1/pages/page-331.md) · [SRD p. 146, Fog Cloud](../../references/srd-5.1/pages/page-146.md)

<a id="srd51-creature-magma-mephit"></a>

## Magma Mephit

`srd51.creature.magma-mephit`

- **Profile.** Small elemental, neutral evil. AC 11. HP 22 (5d6 + 5). Speed 30 ft, fly 30 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 8 (−1), Dex 12 (+1), Con 12 (+1), Int 7 (−2), Wis 10 (+0), Cha 10 (+0).
- **Skills.** Stealth +3.
- **Vulnerabilities.** cold.
- **Damage immunity.** fire, poison.
- **Condition immunity.** poisoned.
- **Senses.** darkvision 60 ft, passive Perception 10.
- **Languages.** Ignan, Terran.

- <a id="srd51-creature-magma-mephit-death-burst"></a> **Trait — Death Burst.** `srd51.creature.magma-mephit.death-burst` On death, creatures within 5 feet save Dexterity DC 11 against 7 (2d6) fire
  damage, halved on success.
- <a id="srd51-creature-magma-mephit-false-appearance"></a> **Trait — False Appearance.** `srd51.creature.magma-mephit.false-appearance` [False appearance](creatures.md#srd51-creature-rules-false-appearance)
  as an ordinary heap of magma.
- <a id="srd51-creature-magma-mephit-innate-spellcasting"></a> **Trait — Innate Spellcasting.** `srd51.creature.magma-mephit.innate-spellcasting` Charisma, save DC 10. Cast [Heat
  Metal](spells-h-m.md#srd51-spell-heat-metal) once per day without material components.
- <a id="srd51-creature-magma-mephit-claws"></a> **Action — Claws.** `srd51.creature.magma-mephit.claws` Melee weapon attack, +3, reach 5 feet, one creature. Hit deals 3 (1d4 + 1)
  slashing plus 2 (1d4) fire.
- <a id="srd51-creature-magma-mephit-fire-breath"></a> **Action — Fire Breath.** `srd51.creature.magma-mephit.fire-breath` Recharge 6. Creatures in a 15-foot cone save Dexterity DC 11 against 7 (2d6)
  fire damage, halved on success.

[SRD p. 331, Magma Mephit](../../references/srd-5.1/pages/page-331.md) · [SRD p. 153, Heat Metal](../../references/srd-5.1/pages/page-153.md)

<a id="srd51-creature-steam-mephit"></a>

## Steam Mephit

`srd51.creature.steam-mephit`

- **Profile.** Small elemental, neutral evil. AC 10. HP 21 (6d6). Speed 30 ft, fly 30 ft. CR 1/4 (50 XP).
- **Ability scores.** Str 5 (−3), Dex 11 (+0), Con 10 (+0), Int 11 (+0), Wis 10 (+0), Cha 12 (+1).
- **Damage immunity.** fire, poison.
- **Condition immunity.** poisoned.
- **Senses.** darkvision 60 ft, passive Perception 10.
- **Languages.** Aquan, Ignan.

- <a id="srd51-creature-steam-mephit-death-burst"></a> **Trait — Death Burst.** `srd51.creature.steam-mephit.death-burst` On death, creatures within 5 feet take 4 (1d8) fire damage unless they pass
  DC 10 Dexterity saves.
- <a id="srd51-creature-steam-mephit-innate-spellcasting"></a> **Trait — Innate Spellcasting.** `srd51.creature.steam-mephit.innate-spellcasting` Charisma. Cast [Blur](spells-a-c.md#srd51-spell-blur) once per day
  without material components.
- <a id="srd51-creature-steam-mephit-claws"></a> **Action — Claws.** `srd51.creature.steam-mephit.claws` Melee weapon attack, +2, reach 5 feet, one creature. Hit deals 2 (1d4) slashing
  plus 2 (1d4) fire.
- <a id="srd51-creature-steam-mephit-steam-breath"></a> **Action — Steam Breath.** `srd51.creature.steam-mephit.steam-breath` Recharge 6. Creatures in a 15-foot cone save Dexterity DC 10 against 4
  (1d8) fire damage, halved on success.

[SRD p. 331, Steam Mephit](../../references/srd-5.1/pages/page-331.md) · [SRD p. 332, Steam Mephit continuation](../../references/srd-5.1/pages/page-332.md) · [SRD p. 123, Blur](../../references/srd-5.1/pages/page-123.md)

<a id="srd51-creature-merfolk"></a>

## Merfolk

`srd51.creature.merfolk`

- **Profile.** Medium humanoid (merfolk), neutral. AC 11. HP 11 (2d8 + 2). Speed 10 ft, swim 40 ft. CR 1/8 (25 XP).
- **Ability scores.** Str 10 (+0), Dex 13 (+1), Con 12 (+1), Int 11 (+0), Wis 11 (+0), Cha 12 (+1).
- **Skills.** Perception +2.
- **Senses.** passive Perception 12.
- **Languages.** Aquan, Common.

- <a id="srd51-creature-merfolk-amphibious"></a> **Trait — Amphibious.** `srd51.creature.merfolk.amphibious` [Amphibious](creatures.md#srd51-creature-rules-amphibious).
- <a id="srd51-creature-merfolk-spear"></a> **Action — Spear.** `srd51.creature.merfolk.spear` Melee or ranged weapon attack, +2, reach 5 feet or range 20/60 feet, one target.
  Hit deals 3 (1d6) piercing, or 4 (1d8) with a two-handed melee attack.

[SRD p. 332, Merfolk](../../references/srd-5.1/pages/page-332.md)

<a id="srd51-creature-merrow"></a>

## Merrow

`srd51.creature.merrow`

- **Profile.** Large monstrosity, chaotic evil. AC 13 (natural armor). HP 45 (6d10 + 12). Speed 10 ft, swim 40 ft. CR 2 (450 XP).
- **Ability scores.** Str 18 (+4), Dex 10 (+0), Con 15 (+2), Int 8 (−1), Wis 10 (+0), Cha 9 (−1).
- **Senses.** darkvision 60 ft, passive Perception 10.
- **Languages.** Abyssal, Aquan.

- <a id="srd51-creature-merrow-amphibious"></a> **Trait — Amphibious.** `srd51.creature.merrow.amphibious` [Amphibious](creatures.md#srd51-creature-rules-amphibious).
- <a id="srd51-creature-merrow-multiattack"></a> **Action — Multiattack.** `srd51.creature.merrow.multiattack` One Bite and one Claws or Harpoon.
- <a id="srd51-creature-merrow-bite"></a> **Action — Bite.** `srd51.creature.merrow.bite` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 8 (1d8 + 4) piercing.
- <a id="srd51-creature-merrow-claws"></a> **Action — Claws.** `srd51.creature.merrow.claws` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 9 (2d4 + 4) slashing.
- <a id="srd51-creature-merrow-harpoon"></a> **Action — Harpoon.** `srd51.creature.merrow.harpoon` Melee or ranged weapon attack, +6, reach 5 feet or range 20/60 feet, one target.
  Hit deals 11 (2d6 + 4) piercing. A Huge or smaller creature contests the merrow's Strength with its
  Strength. If the target loses, pull it up to 20 feet toward the merrow.

[SRD p. 332, Merrow](../../references/srd-5.1/pages/page-332.md)

<a id="srd51-creature-mimic"></a>

## Mimic

`srd51.creature.mimic`

- **Profile.** Medium monstrosity (shapechanger), neutral. AC 12 (natural armor). HP 58 (9d8 + 18). Speed 15 ft. CR 2 (450 XP).
- **Ability scores.** Str 17 (+3), Dex 12 (+1), Con 15 (+2), Int 5 (−3), Wis 13 (+1), Cha 8 (−1).
- **Skills.** Stealth +5.
- **Damage immunity.** acid.
- **Condition immunity.** prone.
- **Senses.** darkvision 60 ft, passive Perception 11.
- **Languages.** None.

- <a id="srd51-creature-mimic-shapechanger"></a> **Trait — Shapechanger.** `srd51.creature.mimic.shapechanger` As an action become an object or return to its amorphous true form.
  Statistics stay fixed and gear does not transform. Death restores the true form.
- <a id="srd51-creature-mimic-adhesive"></a> **Trait — Adhesive.** `srd51.creature.mimic.adhesive` In object form, the mimic sticks to anything that touches it. A Huge or smaller
  creature adhering to it is grappled, escape DC 13. Checks to escape that grapple have disadvantage.
- <a id="srd51-creature-mimic-false-appearance"></a> **Trait — False Appearance.** `srd51.creature.mimic.false-appearance` While in object form, [false
  appearance](creatures.md#srd51-creature-rules-false-appearance) as an ordinary object.
- <a id="srd51-creature-mimic-grappler"></a> **Trait — Grappler.** `srd51.creature.mimic.grappler` Attacks against a creature the mimic grapples have advantage.
- <a id="srd51-creature-mimic-pseudopod"></a> **Action — Pseudopod.** `srd51.creature.mimic.pseudopod` Melee weapon attack, +5, reach 5 feet, one target. Hit deals 7 (1d8 + 3)
  bludgeoning. In object form, also apply Adhesive.
- <a id="srd51-creature-mimic-bite"></a> **Action — Bite.** `srd51.creature.mimic.bite` Melee weapon attack, +5, reach 5 feet, one target. Hit deals 7 (1d8 + 3) piercing
  plus 4 (1d8) acid.

[SRD p. 332, Mimic](../../references/srd-5.1/pages/page-332.md) · [SRD p. 333, Mimic continuation](../../references/srd-5.1/pages/page-333.md) · [SRD p. 95, Grappling](../../references/srd-5.1/pages/page-095.md) · [SRD p. 358, Grappled](../../references/srd-5.1/pages/page-358.md)

<a id="srd51-creature-minotaur"></a>

## Minotaur

`srd51.creature.minotaur`

- **Profile.** Large monstrosity, chaotic evil. AC 14 (natural armor). HP 76 (9d10 + 27). Speed 40 ft. CR 3 (700 XP).
- **Ability scores.** Str 18 (+4), Dex 11 (+0), Con 16 (+3), Int 6 (−2), Wis 16 (+3), Cha 9 (−1).
- **Skills.** Perception +7.
- **Senses.** darkvision 60 ft, passive Perception 17.
- **Languages.** Abyssal.

- <a id="srd51-creature-minotaur-charge"></a> **Trait — Charge.** `srd51.creature.minotaur.charge` Move at least 10 feet straight toward a target and hit it with Gore on the same
  turn to add 9 (2d8) piercing damage. A hit creature also saves Strength DC 14 or is pushed up to 10
  feet away and knocked prone.
- <a id="srd51-creature-minotaur-labyrinthine-recall"></a> **Trait — Labyrinthine Recall.** `srd51.creature.minotaur.labyrinthine-recall` Perfect memory of paths it has traveled. It automatically succeeds on
  [Maze](spells-h-m.md#srd51-spell-maze)'s escape check.
- <a id="srd51-creature-minotaur-reckless"></a> **Trait — Reckless.** `srd51.creature.minotaur.reckless` At turn start it can give itself advantage on all melee weapon attack rolls that
  turn. Then attacks against it have advantage until its next turn starts.
- <a id="srd51-creature-minotaur-greataxe"></a> **Action — Greataxe.** `srd51.creature.minotaur.greataxe` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 17 (2d12 + 4)
  slashing.
- <a id="srd51-creature-minotaur-gore"></a> **Action — Gore.** `srd51.creature.minotaur.gore` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 13 (2d8 + 4) piercing.

[SRD p. 333, Minotaur](../../references/srd-5.1/pages/page-333.md) · [SRD p. 163, Maze](../../references/srd-5.1/pages/page-163.md)

<a id="srd51-creature-mummy"></a>

## Mummy

`srd51.creature.mummy`

- **Profile.** Medium undead, lawful evil. AC 11 (natural armor). HP 58 (9d8 + 18). Speed 20 ft. CR 3 (700 XP).
- **Ability scores.** Str 16 (+3), Dex 8 (−1), Con 15 (+2), Int 6 (−2), Wis 10 (+0), Cha 12 (+1).
- **Saves.** Wis +2.
- **Vulnerabilities.** fire.
- **Resistances.** bludgeoning, piercing, and slashing from nonmagical attacks.
- **Damage immunity.** necrotic, poison.
- **Condition immunity.** charmed, exhaustion, frightened, paralyzed, poisoned.
- **Senses.** darkvision 60 ft, passive Perception 10.
- **Languages.** the languages it knew in life.

- <a id="srd51-creature-mummy-multiattack"></a> **Action — Multiattack.** `srd51.creature.mummy.multiattack` It may use Dreadful Glare and makes one Rotting Fist attack.
- <a id="srd51-creature-mummy-rotting-fist"></a> **Action — Rotting Fist.** `srd51.creature.mummy.rotting-fist` Melee weapon attack, +5, reach 5 feet, one target. Hit deals 10 (2d6 + 3)
  bludgeoning plus 10 (3d6) necrotic. A hit creature saves Constitution DC 12 or contracts [mummy
  rot](#srd51-creature-mummy-rot).
- <a id="srd51-creature-mummy-dreadful-glare"></a> **Action — Dreadful Glare.** `srd51.creature.mummy.dreadful-glare` Choose a creature it sees within 60 feet. If the target sees the mummy it
  saves Wisdom DC 11 against the magical glare. Failure frightens until the end of the mummy's next
  turn. Failure by at least 5 also paralyzes for that time. Success gives 24-hour immunity to this
  action from mummies. A mummy lord's glare remains effective.

<a id="srd51-creature-mummy-rot"></a>

### Mummy rot

`srd51.creature.mummy-rot`

The curse prevents HP recovery and reduces maximum HP by 10 (3d6) per elapsed 24 hours. A maximum
reduced to 0 by this curse kills the creature and turns its body to dust. [Remove
Curse](spells-n-s.md#srd51-spell-remove-curse) or other magic can end the curse.

**Derived conclusion.** The curse's maximum-HP penalty and recovery prohibition end when the curse
ends. This restores the capacity for HP and healing without itself restoring current HP or returning
a dead creature to life. Use the [ordinary HP maximum](combat.md#srd51-core-hit-points) during the
curse.

**Project interpretation.** Track one active mummy rot curse per target, with its first 24-hour tick
measured from application. Another failed save against mummy rot while infected neither adds a second
clock nor restarts this one. Both profiles impose the same named curse and give no stacking or refresh
rule. Counting separate infections would apply more than one maximum loss per elapsed interval.

[SRD p. 333, Mummy](../../references/srd-5.1/pages/page-333.md) · [SRD p. 334, Mummy continuation and
Mummy Lord](../../references/srd-5.1/pages/page-334.md).

[SRD p. 333, Mummy](../../references/srd-5.1/pages/page-333.md) · [SRD p. 334, Mummy continuation](../../references/srd-5.1/pages/page-334.md) · [SRD p. 174, Remove Curse](../../references/srd-5.1/pages/page-174.md)

<a id="srd51-creature-mummy-lord"></a>

## Mummy Lord

`srd51.creature.mummy-lord`

- **Profile.** Medium undead, lawful evil. AC 17 (natural armor). HP 97 (13d8 + 39). Speed 20 ft. CR 15 (13,000 XP).
- **Ability scores.** Str 18 (+4), Dex 10 (+0), Con 17 (+3), Int 11 (+0), Wis 18 (+4), Cha 16 (+3).
- **Saves.** Con +8, Int +5, Wis +9, Cha +8.
- **Skills.** History +5, Religion +5.
- **Vulnerabilities.** fire.
- **Damage immunity.** necrotic, poison; bludgeoning, piercing, and slashing from nonmagical attacks.
- **Condition immunity.** charmed, exhaustion, frightened, paralyzed, poisoned.
- **Senses.** darkvision 60 ft, passive Perception 14.
- **Languages.** the languages it knew in life.

- <a id="srd51-creature-mummy-lord-magic-resistance"></a> **Trait — Magic Resistance.** `srd51.creature.mummy-lord.magic-resistance` [Magic Resistance](creatures.md#srd51-creature-rules-magic-resistance).
- <a id="srd51-creature-mummy-lord-rejuvenation"></a> **Trait — Rejuvenation.** `srd51.creature.mummy-lord.rejuvenation` If the heart remains intact after destruction, the mummy lord returns active
  with full HP in 24 hours in a new body within 5 feet of the heart.
- <a id="srd51-creature-mummy-lord-spellcasting"></a> **Trait — Spellcasting.** `srd51.creature.mummy-lord.spellcasting` Level-10 Wisdom cleric casting, save DC 17 and spell attacks +9. Cantrips at
  will [Sacred Flame](spells-n-s.md#srd51-spell-sacred-flame),
  [Thaumaturgy](spells-t-z.md#srd51-spell-thaumaturgy). Level 1 (4 slots)
  [Command](spells-a-c.md#srd51-spell-command), [Guiding Bolt](spells-e-g.md#srd51-spell-guiding-bolt),
  [Shield of Faith](spells-n-s.md#srd51-spell-shield-of-faith). Level 2 (3) [Hold
  Person](spells-h-m.md#srd51-spell-hold-person), [Silence](spells-n-s.md#srd51-spell-silence),
  [Spiritual Weapon](spells-n-s.md#srd51-spell-spiritual-weapon). Level 3 (3) [Animate
  Dead](spells-a-c.md#srd51-spell-animate-dead), [Dispel Magic](spells-d-e.md#srd51-spell-dispel-magic).
  Level 4 (3) [Divination](spells-d-e.md#srd51-spell-divination), [Guardian of
  Faith](spells-e-g.md#srd51-spell-guardian-of-faith). Level 5 (2)
  [Contagion](spells-a-c.md#srd51-spell-contagion), [Insect
  Plague](spells-h-m.md#srd51-spell-insect-plague). Level 6 (1) [Harm](spells-h-m.md#srd51-spell-harm).
- <a id="srd51-creature-mummy-lord-multiattack"></a> **Action — Multiattack.** `srd51.creature.mummy-lord.multiattack` It may use Dreadful Glare and makes one Rotting Fist attack.
- <a id="srd51-creature-mummy-lord-rotting-fist"></a> **Action — Rotting Fist.** `srd51.creature.mummy-lord.rotting-fist` Melee weapon attack, +9, reach 5 feet, one target. Hit deals 14 (3d6 + 4)
  bludgeoning plus 21 (6d6) necrotic. A creature hit saves Constitution DC 16 or contracts [mummy
  rot](#srd51-creature-mummy-rot).
- <a id="srd51-creature-mummy-lord-dreadful-glare"></a> **Action — Dreadful Glare.** `srd51.creature.mummy-lord.dreadful-glare` Choose a creature it sees within 60 feet. If the target sees the mummy
  lord it saves Wisdom DC 16 against the magic. Failure frightens until the end of the mummy lord's next
  turn. Failure by at least 5 also paralyzes for that time. Success gives 24-hour immunity to Dreadful
  Glare from mummies and mummy lords.
- <a id="srd51-creature-mummy-lord-legendary-actions"></a> **Legendary budget — Legendary Actions.** `srd51.creature.mummy-lord.legendary-actions` Three per round, with one option at the end of another creature's turn and
  spent actions recovering at the start of its own turn.
- <a id="srd51-creature-mummy-lord-attack"></a> **Legendary option — Attack (1).** `srd51.creature.mummy-lord.attack` One Rotting Fist or Dreadful Glare.
- <a id="srd51-creature-mummy-lord-blinding-dust"></a> **Legendary option — Blinding Dust (1).** `srd51.creature.mummy-lord.blinding-dust` Each creature within 5 feet saves Constitution DC 16 or is
  blinded until the end of its next turn.
- <a id="srd51-creature-mummy-lord-blasphemous-word"></a> **Legendary option — Blasphemous Word (2).** `srd51.creature.mummy-lord.blasphemous-word` Each non-undead creature within 10 feet that hears the
  magical utterance saves Constitution DC 16 or is stunned through the end of the mummy lord's next
  turn.
- <a id="srd51-creature-mummy-lord-channel-negative-energy"></a> **Legendary option — Channel Negative Energy (2).** `srd51.creature.mummy-lord.channel-negative-energy` Creatures within 60 feet cannot regain HP until
  the end of the mummy lord's next turn. This reaches behind barriers and around corners.
- <a id="srd51-creature-mummy-lord-whirlwind-of-sand"></a> **Legendary option — Whirlwind of Sand (2).** `srd51.creature.mummy-lord.whirlwind-of-sand` Become a magical whirlwind, move up to 60 feet, and
  return to normal. During the move it keeps its gear, is immune to damage, and cannot be grappled,
  petrified, knocked prone, restrained, or stunned.

[SRD p. 334, Mummy Lord](../../references/srd-5.1/pages/page-334.md) · [SRD p. 335, Mummy Lord continuation](../../references/srd-5.1/pages/page-335.md) · [SRD p. 176, Sacred Flame](../../references/srd-5.1/pages/page-176.md) · [SRD p. 187, Thaumaturgy](../../references/srd-5.1/pages/page-187.md) · [SRD p. 125, Command](../../references/srd-5.1/pages/page-125.md) · [SRD p. 151, Guiding Bolt](../../references/srd-5.1/pages/page-151.md) · [SRD p. 179, Shield of Faith](../../references/srd-5.1/pages/page-179.md) · [SRD p. 154, Hold Person](../../references/srd-5.1/pages/page-154.md) · [SRD p. 179, Silence](../../references/srd-5.1/pages/page-179.md) · [SRD p. 182, Spiritual Weapon](../../references/srd-5.1/pages/page-182.md) · [SRD p. 115, Animate Dead](../../references/srd-5.1/pages/page-115.md) · [SRD p. 136, Dispel Magic](../../references/srd-5.1/pages/page-136.md) · [SRD p. 136, Divination](../../references/srd-5.1/pages/page-136.md) · [SRD p. 150, Guardian of Faith](../../references/srd-5.1/pages/page-150.md) · [SRD p. 129, Contagion](../../references/srd-5.1/pages/page-129.md) · [SRD p. 157, Insect Plague](../../references/srd-5.1/pages/page-157.md) · [SRD p. 153, Harm](../../references/srd-5.1/pages/page-153.md) · [SRD p. 333, Mummy and Rotting Fist](../../references/srd-5.1/pages/page-333.md)

<a id="srd51-creature-guardian-naga"></a>

## Guardian Naga

`srd51.creature.guardian-naga`

- **Profile.** Large monstrosity, lawful good. AC 18 (natural armor). HP 127 (15d10 + 45). Speed 40 ft. CR 10 (5,900 XP).
- **Ability scores.** Str 19 (+4), Dex 18 (+4), Con 16 (+3), Int 16 (+3), Wis 19 (+4), Cha 18 (+4).
- **Saves.** Dex +8, Con +7, Int +7, Wis +8, Cha +8.
- **Damage immunity.** poison.
- **Condition immunity.** charmed, poisoned.
- **Senses.** darkvision 60 ft, passive Perception 14.
- **Languages.** Celestial, Common.

- <a id="srd51-creature-guardian-naga-rejuvenation"></a> **Trait — Rejuvenation.** `srd51.creature.guardian-naga.rejuvenation` Use [naga rejuvenation](#srd51-creature-rules-naga-rejuvenation).
- <a id="srd51-creature-guardian-naga-spellcasting"></a> **Trait — Spellcasting.** `srd51.creature.guardian-naga.spellcasting` Level-11 Wisdom cleric casting, save DC 16 and spell attacks +8. Requires
  verbal components only. Cantrips at will [Mending](spells-h-m.md#srd51-spell-mending), [Sacred
  Flame](spells-n-s.md#srd51-spell-sacred-flame), [Thaumaturgy](spells-t-z.md#srd51-spell-thaumaturgy).
  Level 1 (4 slots) [Command](spells-a-c.md#srd51-spell-command), [Cure
  Wounds](spells-a-c.md#srd51-spell-cure-wounds), [Shield of
  Faith](spells-n-s.md#srd51-spell-shield-of-faith). Level 2 (3) [Calm
  Emotions](spells-a-c.md#srd51-spell-calm-emotions), [Hold
  Person](spells-h-m.md#srd51-spell-hold-person). Level 3 (3) [Bestow
  Curse](spells-a-c.md#srd51-spell-bestow-curse),
  [Clairvoyance](spells-a-c.md#srd51-spell-clairvoyance). Level 4 (3)
  [Banishment](spells-a-c.md#srd51-spell-banishment), [Freedom of
  Movement](spells-e-g.md#srd51-spell-freedom-of-movement). Level 5 (2) [Flame
  Strike](spells-e-g.md#srd51-spell-flame-strike), [Geas](spells-e-g.md#srd51-spell-geas). Level 6 (1)
  [True Seeing](spells-t-z.md#srd51-spell-true-seeing).
- <a id="srd51-creature-guardian-naga-bite"></a> **Action — Bite.** `srd51.creature.guardian-naga.bite` Melee weapon attack, +8, reach 10 feet, one creature. Hit deals 8 (1d8 + 4)
  piercing, then Constitution DC 15 against 45 (10d8) poison, half on success.
- <a id="srd51-creature-guardian-naga-spit-poison"></a> **Action — Spit Poison.** `srd51.creature.guardian-naga.spit-poison` Ranged weapon attack, +8, range 15/30 feet, one creature. On hit,
  Constitution DC 15 against 45 (10d8) poison, half on success.

[SRD p. 335, Guardian Naga](../../references/srd-5.1/pages/page-335.md) · [SRD p. 193, Wish](../../references/srd-5.1/pages/page-193.md) · [SRD p. 164, Mending](../../references/srd-5.1/pages/page-164.md) · [SRD p. 176, Sacred Flame](../../references/srd-5.1/pages/page-176.md) · [SRD p. 187, Thaumaturgy](../../references/srd-5.1/pages/page-187.md) · [SRD p. 125, Command](../../references/srd-5.1/pages/page-125.md) · [SRD p. 132, Cure Wounds](../../references/srd-5.1/pages/page-132.md) · [SRD p. 179, Shield of Faith](../../references/srd-5.1/pages/page-179.md) · [SRD p. 123, Calm Emotions](../../references/srd-5.1/pages/page-123.md) · [SRD p. 154, Hold Person](../../references/srd-5.1/pages/page-154.md) · [SRD p. 121, Bestow Curse](../../references/srd-5.1/pages/page-121.md) · [SRD p. 124, Clairvoyance](../../references/srd-5.1/pages/page-124.md) · [SRD p. 120, Banishment](../../references/srd-5.1/pages/page-120.md) · [SRD p. 147, Freedom of Movement](../../references/srd-5.1/pages/page-147.md) · [SRD p. 145, Flame Strike](../../references/srd-5.1/pages/page-145.md) · [SRD p. 148, Geas](../../references/srd-5.1/pages/page-148.md) · [SRD p. 189, True Seeing](../../references/srd-5.1/pages/page-189.md)

<a id="srd51-creature-spirit-naga"></a>

## Spirit Naga

`srd51.creature.spirit-naga`

- **Profile.** Large monstrosity, chaotic evil. AC 15 (natural armor). HP 75 (10d10 + 20). Speed 40 ft. CR 8 (3,900 XP).
- **Ability scores.** Str 18 (+4), Dex 17 (+3), Con 14 (+2), Int 16 (+3), Wis 15 (+2), Cha 16 (+3).
- **Saves.** Dex +6, Con +5, Wis +5, Cha +6.
- **Damage immunity.** poison.
- **Condition immunity.** charmed, poisoned.
- **Senses.** darkvision 60 ft, passive Perception 12.
- **Languages.** Abyssal, Common.

- <a id="srd51-creature-spirit-naga-rejuvenation"></a> **Trait — Rejuvenation.** `srd51.creature.spirit-naga.rejuvenation` Use [naga rejuvenation](#srd51-creature-rules-naga-rejuvenation).
- <a id="srd51-creature-spirit-naga-spellcasting"></a> **Trait — Spellcasting.** `srd51.creature.spirit-naga.spellcasting` Level-10 Intelligence wizard casting, save DC 14 and spell attacks +6.
  Requires verbal components only. Cantrips at will [Mage Hand](spells-h-m.md#srd51-spell-mage-hand),
  [Minor Illusion](spells-h-m.md#srd51-spell-minor-illusion), [Ray of
  Frost](spells-n-s.md#srd51-spell-ray-of-frost). Level 1 (4 slots) [Charm
  Person](spells-a-c.md#srd51-spell-charm-person), [Detect
  Magic](spells-d-e.md#srd51-spell-detect-magic), [Sleep](spells-n-s.md#srd51-spell-sleep). Level 2 (3)
  [Detect Thoughts](spells-d-e.md#srd51-spell-detect-thoughts), [Hold
  Person](spells-h-m.md#srd51-spell-hold-person). Level 3 (3) [Lightning
  Bolt](spells-h-m.md#srd51-spell-lightning-bolt), [Water
  Breathing](spells-t-z.md#srd51-spell-water-breathing). Level 4 (3)
  [Blight](spells-a-c.md#srd51-spell-blight), [Dimension
  Door](spells-d-e.md#srd51-spell-dimension-door). Level 5 (2) [Dominate
  Person](spells-d-e.md#srd51-spell-dominate-person).
- <a id="srd51-creature-spirit-naga-bite"></a> **Action — Bite.** `srd51.creature.spirit-naga.bite` Melee weapon attack, +7, reach 10 feet, one creature. Hit deals 7 (1d6 + 4)
  piercing, then Constitution DC 13 against 31 (7d8) poison, half on success.

[SRD p. 335, Spirit Naga](../../references/srd-5.1/pages/page-335.md) · [SRD p. 336, Spirit Naga continuation](../../references/srd-5.1/pages/page-336.md) · [SRD p. 193, Wish](../../references/srd-5.1/pages/page-193.md) · [SRD p. 160, Mage Hand](../../references/srd-5.1/pages/page-160.md) · [SRD p. 164, Minor Illusion](../../references/srd-5.1/pages/page-164.md) · [SRD p. 174, Ray of Frost](../../references/srd-5.1/pages/page-174.md) · [SRD p. 124, Charm Person](../../references/srd-5.1/pages/page-124.md) · [SRD p. 134, Detect Magic](../../references/srd-5.1/pages/page-134.md) · [SRD p. 180, Sleep](../../references/srd-5.1/pages/page-180.md) · [SRD p. 135, Detect Thoughts](../../references/srd-5.1/pages/page-135.md) · [SRD p. 154, Hold Person](../../references/srd-5.1/pages/page-154.md) · [SRD p. 159, Lightning Bolt](../../references/srd-5.1/pages/page-159.md) · [SRD p. 191, Water Breathing](../../references/srd-5.1/pages/page-191.md) · [SRD p. 122, Blight](../../references/srd-5.1/pages/page-122.md) · [SRD p. 135, Dimension Door](../../references/srd-5.1/pages/page-135.md) · [SRD p. 138, Dominate Person](../../references/srd-5.1/pages/page-138.md)

<a id="srd51-creature-nightmare"></a>

## Nightmare

`srd51.creature.nightmare`

- **Profile.** Large fiend, neutral evil. AC 13 (natural armor). HP 68 (8d10 + 24). Speed 60 ft, fly 90 ft. CR 3 (700 XP).
- **Ability scores.** Str 18 (+4), Dex 15 (+2), Con 16 (+3), Int 10 (+0), Wis 13 (+1), Cha 15 (+2).
- **Damage immunity.** fire.
- **Senses.** passive Perception 11.
- **Languages.** understands Abyssal, Common, and Infernal but can’t speak.

- <a id="srd51-creature-nightmare-confer-fire-resistance"></a> **Trait — Confer Fire Resistance.** `srd51.creature.nightmare.confer-fire-resistance` It may grant fire resistance to any creature riding it.
  **Project interpretation.** The grant costs no action and lasts only while that recipient rides
  the nightmare. The trait ties eligibility to riding and supplies no separate duration. Treating
  riding as only an initial requirement would give the resistance an unspecified lifetime.
- <a id="srd51-creature-nightmare-illumination"></a> **Trait — Illumination.** `srd51.creature.nightmare.illumination` Bright light within 10 feet and dim light for 10 more.
- <a id="srd51-creature-nightmare-hooves"></a> **Action — Hooves.** `srd51.creature.nightmare.hooves` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 13 (2d8 + 4)
  bludgeoning plus 7 (2d6) fire.
- <a id="srd51-creature-nightmare-ethereal-stride"></a> **Action — Ethereal Stride.** `srd51.creature.nightmare.ethereal-stride` Magically cross between the Material and Ethereal Planes in either direction. The nightmare can
  bring as many as three willing companions, each within 5 feet of it.

[SRD p. 336, Nightmare](../../references/srd-5.1/pages/page-336.md)

<a id="srd51-creature-ogre"></a>

## Ogre

`srd51.creature.ogre`

- **Profile.** Large giant, chaotic evil. AC 11 (hide armor). HP 59 (7d10 + 21). Speed 40 ft. CR 2 (450 XP).
- **Ability scores.** Str 19 (+4), Dex 8 (−1), Con 16 (+3), Int 5 (−3), Wis 7 (−2), Cha 7 (−2).
- **Senses.** darkvision 60 ft, passive Perception 8.
- **Languages.** Common, Giant.

- <a id="srd51-creature-ogre-greatclub"></a> **Action — Greatclub.** `srd51.creature.ogre.greatclub` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 13 (2d8 + 4)
  bludgeoning.
- <a id="srd51-creature-ogre-javelin"></a> **Action — Javelin.** `srd51.creature.ogre.javelin` Melee or ranged weapon attack, +6, reach 5 feet or range 30/120 feet, one
  target. Hit deals 11 (2d6 + 4) piercing.

[SRD p. 336, Ogre](../../references/srd-5.1/pages/page-336.md)

<a id="srd51-creature-oni"></a>

## Oni

`srd51.creature.oni`

- **Profile.** Large giant, lawful evil. AC 16 (chain mail). HP 110 (13d10 + 39). Speed 30 ft, fly 30 ft. CR 7 (2,900 XP).
- **Ability scores.** Str 19 (+4), Dex 11 (+0), Con 16 (+3), Int 14 (+2), Wis 12 (+1), Cha 15 (+2).
- **Saves.** Dex +3, Con +6, Wis +4, Cha +5.
- **Skills.** Arcana +5, Deception +8, Perception +4.
- **Senses.** darkvision 60 ft, passive Perception 14.
- **Languages.** Common, Giant.

- <a id="srd51-creature-oni-innate-spellcasting"></a> **Trait — Innate Spellcasting.** `srd51.creature.oni.innate-spellcasting` Charisma, save DC 13, no material components. At will
  [Darkness](spells-d-e.md#srd51-spell-darkness),
  [Invisibility](spells-h-m.md#srd51-spell-invisibility). Once per day each [Charm
  Person](spells-a-c.md#srd51-spell-charm-person), [Cone of
  Cold](spells-a-c.md#srd51-spell-cone-of-cold), [Gaseous Form](spells-e-g.md#srd51-spell-gaseous-form),
  [Sleep](spells-n-s.md#srd51-spell-sleep).
- <a id="srd51-creature-oni-magic-weapons"></a> **Trait — Magic Weapons.** `srd51.creature.oni.magic-weapons` [Magic Weapons](creatures.md#srd51-creature-rules-magic-weapons).
- <a id="srd51-creature-oni-regeneration"></a> **Trait — Regeneration.** `srd51.creature.oni.regeneration` Regain 10 HP at turn start if at least 1 HP remains.
- <a id="srd51-creature-oni-multiattack"></a> **Action — Multiattack.** `srd51.creature.oni.multiattack` Make two attacks with eligible Claw or Glaive attacks. **Project
  interpretation.** Choose the weapon independently for each attack. The source names two eligible
  weapons and does not fix a combination, although its either/or phrasing can be read as one weapon
  choice for the pair. Claw still requires oni form.
- <a id="srd51-creature-oni-claw"></a> **Action — Claw.** `srd51.creature.oni.claw` Oni form only. Melee weapon attack, +7, reach 5 feet, one target. Hit deals 8 (1d8
  + 4) slashing.
- <a id="srd51-creature-oni-glaive"></a> **Action — Glaive.** `srd51.creature.oni.glaive` Melee weapon attack, +7, reach 10 feet, one target. Hit deals 15 (2d10 + 4)
  slashing, or 9 (1d10 + 4) in Small or Medium form.
- <a id="srd51-creature-oni-change-shape"></a> **Action — Change Shape.** `srd51.creature.oni.change-shape` Magically become a Small or Medium humanoid, a Large giant, or the oni's
  true form. Only size changes among its statistics. The glaive alone changes among its equipment,
  shrinking to suit the humanoid form. Death restores the oni's true form and the glaive's original
  size.

[SRD p. 336, Oni](../../references/srd-5.1/pages/page-336.md) · [SRD p. 337, Oni continuation](../../references/srd-5.1/pages/page-337.md) · [SRD p. 133, Darkness](../../references/srd-5.1/pages/page-133.md) · [SRD p. 157, Invisibility](../../references/srd-5.1/pages/page-157.md) · [SRD p. 124, Charm Person](../../references/srd-5.1/pages/page-124.md) · [SRD p. 127, Cone of Cold](../../references/srd-5.1/pages/page-127.md) · [SRD p. 148, Gaseous Form](../../references/srd-5.1/pages/page-148.md) · [SRD p. 180, Sleep](../../references/srd-5.1/pages/page-180.md)

<a id="srd51-creature-black-pudding"></a>

## Black Pudding

`srd51.creature.black-pudding`

- **Profile.** Large ooze, unaligned. AC 7. HP 85 (10d10 + 30). Speed 20 ft, climb 20 ft. CR 4 (1,100 XP).
- **Ability scores.** Str 16 (+3), Dex 5 (−3), Con 16 (+3), Int 1 (−5), Wis 6 (−2), Cha 1 (−5).
- **Damage immunity.** acid, cold, lightning, slashing.
- **Condition immunity.** blinded, charmed, deafened, exhaustion, frightened, prone.
- **Senses.** blindsight 60 ft (blind beyond this radius), passive Perception 8.
- **Languages.** None.

- <a id="srd51-creature-black-pudding-amorphous"></a> **Trait — Amorphous.** `srd51.creature.black-pudding.amorphous` Apply [Amorphous](#srd51-creature-rules-amorphous).
- <a id="srd51-creature-black-pudding-corrosive-form"></a> **Trait — Corrosive Form.** `srd51.creature.black-pudding.corrosive-form` A creature touching it or hitting it with a melee attack from within 5
  feet takes 4 (1d8) acid. Apply [corrosion](#srd51-creature-rules-corrosion) to nonmagical metal or
  wood weapons and ammunition that hit. In one round it can dissolve its way through a 2-inch layer of either nonmagical metal or wood.
- <a id="srd51-creature-black-pudding-spider-climb"></a> **Trait — Spider Climb.** `srd51.creature.black-pudding.spider-climb` Apply [Spider Climb](creatures.md#srd51-creature-rules-spider-climb).
- <a id="srd51-creature-black-pudding-pseudopod"></a> **Action — Pseudopod.** `srd51.creature.black-pudding.pseudopod` Melee weapon attack, +5, reach 5 feet, one target. Hit deals 6 (1d6 + 3)
  bludgeoning plus 18 (4d8) acid. Apply [armor corrosion](#srd51-creature-rules-corrosion) to any
  nonmagical armor the target wears.
- <a id="srd51-creature-black-pudding-split"></a> **Reaction — Split.** `srd51.creature.black-pudding.split` Apply [ooze splitting](#srd51-creature-rules-ooze-splitting), producing
  two Black Puddings.

[SRD p. 337, Black Pudding](../../references/srd-5.1/pages/page-337.md)

<a id="srd51-creature-gelatinous-cube"></a>

## Gelatinous Cube

`srd51.creature.gelatinous-cube`

- **Profile.** Large ooze, unaligned. AC 6. HP 84 (8d10 + 40). Speed 15 ft. CR 2 (450 XP).
- **Ability scores.** Str 14 (+2), Dex 3 (−4), Con 20 (+5), Int 1 (−5), Wis 6 (−2), Cha 1 (−5).
- **Condition immunity.** blinded, charmed, deafened, exhaustion, frightened, prone.
- **Senses.** blindsight 60 ft (blind beyond this radius), passive Perception 8.
- **Languages.** None.

- <a id="srd51-creature-gelatinous-cube-ooze-cube"></a> **Trait — Ooze Cube.** `srd51.creature.gelatinous-cube.ooze-cube` It fills its space. A creature entering that space is subject to Engulf with
  disadvantage on the save. Creatures inside are visible but have total cover. From within 5 feet, a
  creature can spend an action to pull a creature or object out using a DC 12 Strength check. The
  attempt deals 10 (3d6) acid damage to the rescuer. The cube holds at most one Large creature or four
  Medium or smaller creatures.
- <a id="srd51-creature-gelatinous-cube-transparent"></a> **Trait — Transparent.** `srd51.creature.gelatinous-cube.transparent` Even in plain sight, noticing a cube that has neither moved nor attacked
  requires a successful DC 15 Wisdom (Perception) check. A creature trying to enter its space while
  unaware of it is surprised by it.
- <a id="srd51-creature-gelatinous-cube-pseudopod"></a> **Action — Pseudopod.** `srd51.creature.gelatinous-cube.pseudopod` Melee weapon attack, +4, reach 5 feet, one creature. Hit deals 10 (3d6) acid.
- <a id="srd51-creature-gelatinous-cube-engulf"></a> **Action — Engulf.** `srd51.creature.gelatinous-cube.engulf` Move up to its speed and enter spaces of Large or smaller creatures. On entering
  a creature's space, require Dexterity DC 12. A successful creature can be pushed 5 feet back or to a
  side. Declining the push has the same effect as failure. Failure deals 10 (3d6) acid and the cube
  engulfs the creature in its space. The captive cannot breathe, is restrained, moves with the cube, and
  takes 21 (6d6) acid at the start of each of the cube's turns. It can spend an action on a DC 12
  Strength check, escaping on success to a chosen space within 5 feet of the cube.

[SRD p. 337, Gelatinous Cube](../../references/srd-5.1/pages/page-337.md) · [SRD p. 338, Gelatinous Cube continuation](../../references/srd-5.1/pages/page-338.md)

<a id="srd51-creature-gray-ooze"></a>

## Gray Ooze

`srd51.creature.gray-ooze`

- **Profile.** Medium ooze, unaligned. AC 8. HP 22 (3d8 + 9). Speed 10 ft, climb 10 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 12 (+1), Dex 6 (−2), Con 16 (+3), Int 1 (−5), Wis 6 (−2), Cha 2 (−4).
- **Skills.** Stealth +2.
- **Resistances.** acid, cold, fire.
- **Condition immunity.** blinded, charmed, deafened, exhaustion, frightened, prone.
- **Senses.** blindsight 60 ft (blind beyond this radius), passive Perception 8.
- **Languages.** None.

- <a id="srd51-creature-gray-ooze-amorphous"></a> **Trait — Amorphous.** `srd51.creature.gray-ooze.amorphous` Apply [Amorphous](#srd51-creature-rules-amorphous).
- <a id="srd51-creature-gray-ooze-corrode-metal"></a> **Trait — Corrode Metal.** `srd51.creature.gray-ooze.corrode-metal` Apply [corrosion](#srd51-creature-rules-corrosion) to nonmagical metal
  weapons and ammunition that hit. It can eat through 2-inch-thick nonmagical metal in 1 round.
- <a id="srd51-creature-gray-ooze-false-appearance"></a> **Trait — False Appearance.** `srd51.creature.gray-ooze.false-appearance` [False appearance](creatures.md#srd51-creature-rules-false-appearance)
  as an oily pool or wet rock.
- <a id="srd51-creature-gray-ooze-pseudopod"></a> **Action — Pseudopod.** `srd51.creature.gray-ooze.pseudopod` Melee weapon attack, +3, reach 5 feet, one target. Hit deals 4 (1d6 + 1)
  bludgeoning plus 7 (2d6) acid. Apply [armor corrosion](#srd51-creature-rules-corrosion) to any
  nonmagical metal armor the target wears.

[SRD p. 338, Gray Ooze](../../references/srd-5.1/pages/page-338.md)

<a id="srd51-creature-ochre-jelly"></a>

## Ochre Jelly

`srd51.creature.ochre-jelly`

- **Profile.** Large ooze, unaligned. AC 8. HP 45 (6d10 + 12). Speed 10 ft, climb 10 ft. CR 2 (450 XP).
- **Ability scores.** Str 15 (+2), Dex 6 (−2), Con 14 (+2), Int 2 (−4), Wis 6 (−2), Cha 1 (−5).
- **Resistances.** acid.
- **Damage immunity.** lightning, slashing.
- **Condition immunity.** blinded, charmed, deafened, exhaustion, frightened, prone.
- **Senses.** blindsight 60 ft (blind beyond this radius), passive Perception 8.
- **Languages.** None.

- <a id="srd51-creature-ochre-jelly-amorphous"></a> **Trait — Amorphous.** `srd51.creature.ochre-jelly.amorphous` Apply [Amorphous](#srd51-creature-rules-amorphous).
- <a id="srd51-creature-ochre-jelly-spider-climb"></a> **Trait — Spider Climb.** `srd51.creature.ochre-jelly.spider-climb` Apply [Spider Climb](creatures.md#srd51-creature-rules-spider-climb).
- <a id="srd51-creature-ochre-jelly-pseudopod"></a> **Action — Pseudopod.** `srd51.creature.ochre-jelly.pseudopod` Melee weapon attack, +4, reach 5 feet, one target. Hit deals 9 (2d6 + 2)
  bludgeoning plus 3 (1d6) acid.
- <a id="srd51-creature-ochre-jelly-split"></a> **Reaction — Split.** `srd51.creature.ochre-jelly.split` Apply [ooze splitting](#srd51-creature-rules-ooze-splitting), producing
  two Ochre Jellies.

[SRD p. 338, Ochre Jelly](../../references/srd-5.1/pages/page-338.md) · [SRD p. 339, Ochre Jelly continuation](../../references/srd-5.1/pages/page-339.md)

<a id="srd51-creature-orc"></a>

## Orc

`srd51.creature.orc`

- **Profile.** Medium humanoid (orc), chaotic evil. AC 13 (hide armor). HP 15 (2d8 + 6). Speed 30 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 16 (+3), Dex 12 (+1), Con 16 (+3), Int 7 (−2), Wis 11 (+0), Cha 10 (+0).
- **Skills.** Intimidation +2.
- **Senses.** darkvision 60 ft, passive Perception 10.
- **Languages.** Common, Orc.

- <a id="srd51-creature-orc-aggressive"></a> **Trait — Aggressive.** `srd51.creature.orc.aggressive` As a bonus action move up to its speed toward a hostile creature it can see.
- <a id="srd51-creature-orc-greataxe"></a> **Action — Greataxe.** `srd51.creature.orc.greataxe` Melee weapon attack, +5, reach 5 feet, one target. Hit deals 9 (1d12 + 3)
  slashing.
- <a id="srd51-creature-orc-javelin"></a> **Action — Javelin.** `srd51.creature.orc.javelin` Melee or ranged weapon attack, +5, reach 5 feet or range 30/120 feet, one
  target. Hit deals 6 (1d6 + 3) piercing.

[SRD p. 339, Orc](../../references/srd-5.1/pages/page-339.md)

<a id="srd51-creature-otyugh"></a>

## Otyugh

`srd51.creature.otyugh`

- **Profile.** Large aberration, neutral. AC 14 (natural armor). HP 114 (12d10 + 48). Speed 30 ft. CR 5 (1,800 XP).
- **Ability scores.** Str 16 (+3), Dex 11 (+0), Con 19 (+4), Int 6 (−2), Wis 13 (+1), Cha 6 (−2).
- **Saves.** Con +7.
- **Senses.** darkvision 120 ft, passive Perception 11.
- **Languages.** Otyugh.

- <a id="srd51-creature-otyugh-limited-telepathy"></a> **Trait — Limited Telepathy.** `srd51.creature.otyugh.limited-telepathy` Magically send simple messages and images to a creature within 120 feet
  that understands a language. This connection does not let the recipient answer telepathically.
- <a id="srd51-creature-otyugh-multiattack"></a> **Action — Multiattack.** `srd51.creature.otyugh.multiattack` One Bite and two Tentacle attacks.
- <a id="srd51-creature-otyugh-bite"></a> **Action — Bite.** `srd51.creature.otyugh.bite` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 12 (2d8 + 3) piercing.
  A creature hit saves Constitution DC 15 against disease. On failure, it is poisoned until cured. Every
  elapsed 24 hours it repeats the save, losing 5 (1d10) maximum HP on failure or curing the disease on
  success. The maximum-HP loss lasts until cure. If it reduces maximum HP to 0, the creature dies.
- <a id="srd51-creature-otyugh-tentacle"></a> **Action — Tentacle.** `srd51.creature.otyugh.tentacle` Melee weapon attack, +6, reach 10 feet, one target. Hit deals 7 (1d8 + 3)
  bludgeoning plus 4 (1d8) piercing. A target of Medium size or smaller is grappled with escape DC 13
  and restrained until that grapple ends. Each of its two tentacles can hold one target.
- <a id="srd51-creature-otyugh-tentacle-slam"></a> **Action — Tentacle Slam.** `srd51.creature.otyugh.tentacle-slam` Slam grappled creatures against one another or a solid surface. Each saves
  Constitution DC 14. Failure deals 10 (2d6 + 3) bludgeoning and stuns until the end of the otyugh's
  next turn. Success halves damage and avoids the stun.

[SRD p. 339, Otyugh](../../references/srd-5.1/pages/page-339.md)

<a id="srd51-creature-owlbear"></a>

## Owlbear

`srd51.creature.owlbear`

- **Profile.** Large monstrosity, unaligned. AC 13 (natural armor). HP 59 (7d10 + 21). Speed 40 ft. CR 3 (700 XP).
- **Ability scores.** Str 20 (+5), Dex 12 (+1), Con 17 (+3), Int 3 (−4), Wis 12 (+1), Cha 7 (−2).
- **Skills.** Perception +3.
- **Senses.** darkvision 60 ft, passive Perception 13.
- **Languages.** None.

- <a id="srd51-creature-owlbear-keen-sight-and-smell"></a> **Trait — Keen Sight and Smell.** `srd51.creature.owlbear.keen-sight-and-smell` [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for
  sight and smell.
- <a id="srd51-creature-owlbear-multiattack"></a> **Action — Multiattack.** `srd51.creature.owlbear.multiattack` One Beak and one Claws.
- <a id="srd51-creature-owlbear-beak"></a> **Action — Beak.** `srd51.creature.owlbear.beak` Melee weapon attack, +7, reach 5 feet, one creature. Hit deals 10 (1d10 + 5)
  piercing.
- <a id="srd51-creature-owlbear-claws"></a> **Action — Claws.** `srd51.creature.owlbear.claws` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 14 (2d8 + 5)
  slashing.

[SRD p. 339, Owlbear](../../references/srd-5.1/pages/page-339.md) · [SRD p. 340, Owlbear continuation](../../references/srd-5.1/pages/page-340.md)

<a id="srd51-creature-pegasus"></a>

## Pegasus

`srd51.creature.pegasus`

- **Profile.** Large celestial, chaotic good. AC 12. HP 59 (7d10 + 21). Speed 60 ft, fly 90 ft. CR 2 (450 XP).
- **Ability scores.** Str 18 (+4), Dex 15 (+2), Con 16 (+3), Int 10 (+0), Wis 15 (+2), Cha 13 (+1).
- **Saves.** Dex +4, Wis +4, Cha +3.
- **Skills.** Perception +6.
- **Senses.** passive Perception 16.
- **Languages.** understands Celestial, Common, Elvish, and Sylvan but can’t speak.

- <a id="srd51-creature-pegasus-hooves"></a> **Action — Hooves.** `srd51.creature.pegasus.hooves` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 11 (2d6 + 4)
  bludgeoning.

[SRD p. 340, Pegasus](../../references/srd-5.1/pages/page-340.md)

<a id="srd51-creature-pseudodragon"></a>

## Pseudodragon

`srd51.creature.pseudodragon`

- **Profile.** Tiny dragon, neutral good. AC 13 (natural armor). HP 7 (2d4 + 2). Speed 15 ft, fly 60 ft. CR 1/4 (50 XP).
- **Ability scores.** Str 6 (−2), Dex 15 (+2), Con 13 (+1), Int 10 (+0), Wis 12 (+1), Cha 10 (+0).
- **Skills.** Perception +3, Stealth +4.
- **Senses.** blindsight 10 ft, darkvision 60 ft, passive Perception 13.
- **Languages.** understands Common and Draconic but can’t speak.

- <a id="srd51-creature-pseudodragon-keen-senses"></a> **Trait — Keen Senses.** `srd51.creature.pseudodragon.keen-senses` [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for sight,
  hearing, and smell.
- <a id="srd51-creature-pseudodragon-magic-resistance"></a> **Trait — Magic Resistance.** `srd51.creature.pseudodragon.magic-resistance` [Magic Resistance](creatures.md#srd51-creature-rules-magic-resistance).
- <a id="srd51-creature-pseudodragon-limited-telepathy"></a> **Trait — Limited Telepathy.** `srd51.creature.pseudodragon.limited-telepathy` Magically communicate simple ideas, feelings, and images with a
  creature within 100 feet that understands a language.
- <a id="srd51-creature-pseudodragon-bite"></a> **Action — Bite.** `srd51.creature.pseudodragon.bite` Melee weapon attack, +4, reach 5 feet, one target. Hit deals 4 (1d4 + 2) piercing.
- <a id="srd51-creature-pseudodragon-sting"></a> **Action — Sting.** `srd51.creature.pseudodragon.sting` Melee weapon attack, +4, reach 5 feet, one creature. Hit deals 4 (1d4 + 2)
  piercing and forces Constitution DC 11. Failure poisons for 1 hour. Failure by 5 or more also causes
  unconsciousness for that time or until the victim takes damage or another creature spends an action
  shaking it awake. Waking early leaves the poison in effect for the rest of its duration.

[SRD p. 340, Pseudodragon](../../references/srd-5.1/pages/page-340.md)

<a id="srd51-creature-purple-worm"></a>

## Purple Worm

`srd51.creature.purple-worm`

- **Profile.** Gargantuan monstrosity, unaligned. AC 18 (natural armor). HP 247 (15d20 + 90). Speed 50 ft, burrow 30 ft. CR 15 (13,000 XP).
- **Ability scores.** Str 28 (+9), Dex 7 (−2), Con 22 (+6), Int 1 (−5), Wis 8 (−1), Cha 4 (−3).
- **Saves.** Con +11, Wis +4.
- **Senses.** blindsight 30 ft, tremorsense 60 ft, passive Perception 9.
- **Languages.** None.

- <a id="srd51-creature-purple-worm-tunneler"></a> **Trait — Tunneler.** `srd51.creature.purple-worm.tunneler` Burrow through solid rock at half burrowing speed, leaving a tunnel 10 feet
  across.
- <a id="srd51-creature-purple-worm-multiattack"></a> **Action — Multiattack.** `srd51.creature.purple-worm.multiattack` One Bite and one Tail Stinger.
- <a id="srd51-creature-purple-worm-bite"></a> **Action — Bite.** `srd51.creature.purple-worm.bite` Melee weapon attack, +9, reach 10 feet, one target. Hit deals 22 (3d8 + 9)
  piercing. A Large or smaller creature saves Dexterity DC 19 or is swallowed. A swallowed creature is
  blinded and restrained, has total cover from external attacks and effects, and takes 21 (6d6) acid at
  the start of each worm turn. If one swallowed creature deals at least 30 damage to the worm on one
  turn, the worm saves Constitution DC 21 at that turn's end. Failure regurgitates every swallowed
  creature, each prone within 10 feet. If the worm dies, the restraint ends and a captive may spend 20
  feet of movement to escape the corpse, exiting prone.
- <a id="srd51-creature-purple-worm-tail-stinger"></a> **Action — Tail Stinger.** `srd51.creature.purple-worm.tail-stinger` Melee weapon attack, +9, reach 10 feet, one creature. Hit deals 19 (3d6 +
  9) piercing, then Constitution DC 19 against 42 (12d6) poison, half on success.

[SRD p. 340, Purple Worm](../../references/srd-5.1/pages/page-340.md) · [SRD p. 341, Purple Worm continuation](../../references/srd-5.1/pages/page-341.md)

<a id="srd51-creature-rakshasa"></a>

## Rakshasa

`srd51.creature.rakshasa`

- **Profile.** Medium fiend, lawful evil. AC 16 (natural armor). HP 110 (13d8 + 52). Speed 40 ft. CR 13 (10,000 XP).
- **Ability scores.** Str 14 (+2), Dex 17 (+3), Con 18 (+4), Int 13 (+1), Wis 16 (+3), Cha 20 (+5).
- **Skills.** Deception +10, Insight +8.
- **Vulnerabilities.** piercing from magic weapons wielded by good creatures.
- **Damage immunity.** bludgeoning, piercing, and slashing from nonmagical attacks.
- **Senses.** darkvision 60 ft, passive Perception 13.
- **Languages.** Common, Infernal.

- <a id="srd51-creature-rakshasa-limited-magic-immunity"></a> **Trait — Limited Magic Immunity.** `srd51.creature.rakshasa.limited-magic-immunity` A spell of level 6 or lower cannot affect or detect it unless it
  allows that. It saves with advantage against all other spells and magical effects.
- <a id="srd51-creature-rakshasa-innate-spellcasting"></a> **Trait — Innate Spellcasting.** `srd51.creature.rakshasa.innate-spellcasting` Charisma, save DC 18 and spell attacks +10, no material components.
  At will [Detect Thoughts](spells-d-e.md#srd51-spell-detect-thoughts), [Disguise
  Self](spells-d-e.md#srd51-spell-disguise-self), [Mage Hand](spells-h-m.md#srd51-spell-mage-hand),
  [Minor Illusion](spells-h-m.md#srd51-spell-minor-illusion). Three times per day each [Charm
  Person](spells-a-c.md#srd51-spell-charm-person), [Detect
  Magic](spells-d-e.md#srd51-spell-detect-magic),
  [Invisibility](spells-h-m.md#srd51-spell-invisibility), [Major
  Image](spells-h-m.md#srd51-spell-major-image), [Suggestion](spells-n-s.md#srd51-spell-suggestion).
  Once per day each [Dominate Person](spells-d-e.md#srd51-spell-dominate-person),
  [Fly](spells-e-g.md#srd51-spell-fly), [Plane Shift](spells-n-s.md#srd51-spell-plane-shift), [True
  Seeing](spells-t-z.md#srd51-spell-true-seeing).
- <a id="srd51-creature-rakshasa-multiattack"></a> **Action — Multiattack.** `srd51.creature.rakshasa.multiattack` Two Claw attacks.
- <a id="srd51-creature-rakshasa-claw"></a> **Action — Claw.** `srd51.creature.rakshasa.claw` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 9 (2d6 + 2) slashing
  and curses a creature. Its rests bring disturbing images and dreams, and it gains no benefits from
  completing a short or long rest until [Remove Curse](spells-n-s.md#srd51-spell-remove-curse) or
  similar magic lifts the curse.

[SRD p. 341, Rakshasa](../../references/srd-5.1/pages/page-341.md) · [SRD p. 135, Detect Thoughts](../../references/srd-5.1/pages/page-135.md) · [SRD p. 135, Disguise Self](../../references/srd-5.1/pages/page-135.md) · [SRD p. 160, Mage Hand](../../references/srd-5.1/pages/page-160.md) · [SRD p. 164, Minor Illusion](../../references/srd-5.1/pages/page-164.md) · [SRD p. 124, Charm Person](../../references/srd-5.1/pages/page-124.md) · [SRD p. 134, Detect Magic](../../references/srd-5.1/pages/page-134.md) · [SRD p. 157, Invisibility](../../references/srd-5.1/pages/page-157.md) · [SRD p. 162, Major Image](../../references/srd-5.1/pages/page-162.md) · [SRD p. 183, Suggestion](../../references/srd-5.1/pages/page-183.md) · [SRD p. 138, Dominate Person](../../references/srd-5.1/pages/page-138.md) · [SRD p. 146, Fly](../../references/srd-5.1/pages/page-146.md) · [SRD p. 168, Plane Shift](../../references/srd-5.1/pages/page-168.md) · [SRD p. 189, True Seeing](../../references/srd-5.1/pages/page-189.md) · [SRD p. 174, Remove Curse](../../references/srd-5.1/pages/page-174.md)

<a id="srd51-creature-remorhaz"></a>

## Remorhaz

`srd51.creature.remorhaz`

- **Profile.** Huge monstrosity, unaligned. AC 17 (natural armor). HP 195 (17d12 + 85). Speed 30 ft, burrow 20 ft. CR 11 (7,200 XP).
- **Ability scores.** Str 24 (+7), Dex 13 (+1), Con 21 (+5), Int 4 (−3), Wis 10 (+0), Cha 5 (−3).
- **Damage immunity.** cold, fire.
- **Senses.** darkvision 60 ft, tremorsense 60 ft, passive Perception 10.
- **Languages.** None.

- <a id="srd51-creature-remorhaz-heated-body"></a> **Trait — Heated Body.** `srd51.creature.remorhaz.heated-body` Apply [Heated Body](creatures.md#srd51-creature-rules-heated-body) for
  10 (3d6) fire damage.
- <a id="srd51-creature-remorhaz-bite"></a> **Action — Bite.** `srd51.creature.remorhaz.bite` Melee weapon attack, +11, reach 10 feet, one target. Hit deals 40 (6d10 + 7)
  piercing plus 10 (3d6) fire. A hit creature is grappled with escape DC 17 and restrained until the
  grapple ends. While holding it, the remorhaz cannot bite another target.
- <a id="srd51-creature-remorhaz-swallow"></a> **Action — Swallow.** `srd51.creature.remorhaz.swallow` Make one Bite on a Medium or smaller creature it grapples. A hit deals Bite
  damage, swallows the target, and ends the grapple. A swallowed creature is blinded and restrained, has
  total cover from external attacks and effects, and takes 21 (6d6) acid at the start of each remorhaz
  turn. If a creature inside deals at least 30 damage to the remorhaz on one turn, the remorhaz saves
  Constitution DC 15 at that turn's end. Failure regurgitates all swallowed creatures, which land prone
  within 10 feet. On its death, swallowed creatures cease to be restrained by it and can spend 15 feet
  of movement to exit the corpse prone.

[SRD p. 341, Remorhaz](../../references/srd-5.1/pages/page-341.md) · [SRD p. 342, Remorhaz continuation](../../references/srd-5.1/pages/page-342.md)

<a id="srd51-creature-roc"></a>

## Roc

`srd51.creature.roc`

- **Profile.** Gargantuan monstrosity, unaligned. AC 15 (natural armor). HP 248 (16d20 + 80). Speed 20 ft, fly 120 ft. CR 11 (7,200 XP).
- **Ability scores.** Str 28 (+9), Dex 10 (+0), Con 20 (+5), Int 3 (−4), Wis 10 (+0), Cha 9 (−1).
- **Saves.** Dex +4, Con +9, Wis +4, Cha +3.
- **Skills.** Perception +4.
- **Senses.** passive Perception 14.
- **Languages.** None.

- <a id="srd51-creature-roc-keen-sight"></a> **Trait — Keen Sight.** `srd51.creature.roc.keen-sight` [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for sight.
- <a id="srd51-creature-roc-multiattack"></a> **Action — Multiattack.** `srd51.creature.roc.multiattack` One Beak and one Talons.
- <a id="srd51-creature-roc-beak"></a> **Action — Beak.** `srd51.creature.roc.beak` Melee weapon attack, +13, reach 10 feet, one target. Hit deals 27 (4d8 + 9)
  piercing.
- <a id="srd51-creature-roc-talons"></a> **Action — Talons.** `srd51.creature.roc.talons` Melee weapon attack, +13, reach 5 feet, one target. Hit deals 23 (4d6 + 9)
  slashing and grapples with escape DC 19. The captive is restrained and the roc cannot use Talons
  against another target until the grapple ends.

[SRD p. 342, Roc](../../references/srd-5.1/pages/page-342.md)

<a id="srd51-creature-roper"></a>

## Roper

`srd51.creature.roper`

- **Profile.** Large monstrosity, neutral evil. AC 20 (natural armor). HP 93 (11d10 + 33). Speed 10 ft, climb 10 ft. CR 5 (1,800 XP).
- **Ability scores.** Str 18 (+4), Dex 8 (−1), Con 17 (+3), Int 7 (−2), Wis 16 (+3), Cha 6 (−2).
- **Skills.** Perception +6, Stealth +5.
- **Senses.** darkvision 60 ft, passive Perception 16.
- **Languages.** None.

- <a id="srd51-creature-roper-false-appearance"></a> **Trait — False Appearance.** `srd51.creature.roper.false-appearance` [False appearance](creatures.md#srd51-creature-rules-false-appearance)
  as a normal cave formation.
- <a id="srd51-creature-roper-grasping-tendrils"></a> **Trait — Grasping Tendrils.** `srd51.creature.roper.grasping-tendrils` Have up to six tendrils, each targetable and having AC 20, 10 HP, and
  poison and psychic damage immunity. Destroying one does not harm the roper and it can grow a
  replacement on its next turn. A creature can also break one by spending an action and succeeding on a
  DC 15 Strength check.
- <a id="srd51-creature-roper-spider-climb"></a> **Trait — Spider Climb.** `srd51.creature.roper.spider-climb` Apply [Spider Climb](creatures.md#srd51-creature-rules-spider-climb).
- <a id="srd51-creature-roper-multiattack"></a> **Action — Multiattack.** `srd51.creature.roper.multiattack` Four Tendril attacks, then Reel, then one Bite.
- <a id="srd51-creature-roper-bite"></a> **Action — Bite.** `srd51.creature.roper.bite` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 22 (4d8 + 4) piercing.
- <a id="srd51-creature-roper-tendril"></a> **Action — Tendril.** `srd51.creature.roper.tendril` Melee weapon attack, +7, reach 50 feet, one creature. Hit grapples with escape
  DC 15. Until escape, the captive is restrained and has disadvantage on Strength checks and saves, and
  that tendril cannot attack a different target.
- <a id="srd51-creature-roper-reel"></a> **Action — Reel.** `srd51.creature.roper.reel` Pull each grappled creature up to 25 feet straight toward the roper.

[SRD p. 342, Roper](../../references/srd-5.1/pages/page-342.md) · [SRD p. 343, Roper continuation](../../references/srd-5.1/pages/page-343.md)

<a id="srd51-creature-rust-monster"></a>

## Rust Monster

`srd51.creature.rust-monster`

- **Profile.** Medium monstrosity, unaligned. AC 14 (natural armor). HP 27 (5d8 + 5). Speed 40 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 13 (+1), Dex 12 (+1), Con 13 (+1), Int 2 (−4), Wis 13 (+1), Cha 6 (−2).
- **Senses.** darkvision 60 ft, passive Perception 11.
- **Languages.** None.

- <a id="srd51-creature-rust-monster-iron-scent"></a> **Trait — Iron Scent.** `srd51.creature.rust-monster.iron-scent` Pinpoint ferrous metal within 30 feet by scent.
- <a id="srd51-creature-rust-monster-rust-metal"></a> **Trait — Rust Metal.** `srd51.creature.rust-monster.rust-metal` Apply [corrosion](#srd51-creature-rules-corrosion) to nonmagical metal
  weapons and ammunition that hit.
- <a id="srd51-creature-rust-monster-bite"></a> **Action — Bite.** `srd51.creature.rust-monster.bite` Melee weapon attack, +3, reach 5 feet, one target. Hit deals 5 (1d8 + 1) piercing.
- <a id="srd51-creature-rust-monster-antennae"></a> **Action — Antennae.** `srd51.creature.rust-monster.antennae` Touch a nonmagical ferrous metal object it can see within 5 feet. If neither
  worn nor carried, destroy a 1-foot cube of the object. Otherwise the wearer or carrier can prevent
  contact with a successful DC 11 Dexterity save. Apply [armor corrosion](#srd51-creature-rules-corrosion)
  to touched armor. A touched shield loses 1 from its AC bonus permanently and cumulatively and is
  destroyed at +0. A held weapon touched takes the Rust Metal weapon penalty immediately.

[SRD p. 343, Rust Monster](../../references/srd-5.1/pages/page-343.md)

<a id="srd51-creature-sahuagin"></a>

## Sahuagin

`srd51.creature.sahuagin`

- **Profile.** Medium humanoid (sahuagin), lawful evil. AC 12 (natural armor). HP 22 (4d8 + 4). Speed 30 ft, swim 40 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 13 (+1), Dex 11 (+0), Con 12 (+1), Int 12 (+1), Wis 13 (+1), Cha 9 (−1).
- **Skills.** Perception +5.
- **Senses.** darkvision 120 ft, passive Perception 15.
- **Languages.** Sahuagin.

- <a id="srd51-creature-sahuagin-blood-frenzy"></a> **Trait — Blood Frenzy.** `srd51.creature.sahuagin.blood-frenzy` Advantage on melee attack rolls against creatures below maximum HP.
- <a id="srd51-creature-sahuagin-limited-amphibiousness"></a> **Trait — Limited Amphibiousness.** `srd51.creature.sahuagin.limited-amphibiousness` Breathe air and water, but submerge at least once every 4 hours to
  avoid suffocating.
- <a id="srd51-creature-sahuagin-shark-telepathy"></a> **Trait — Shark Telepathy.** `srd51.creature.sahuagin.shark-telepathy` Magically command a shark within 120 feet through limited telepathy.
- <a id="srd51-creature-sahuagin-multiattack"></a> **Action — Multiattack.** `srd51.creature.sahuagin.multiattack` One melee Bite and one melee Claws or Spear attack.
- <a id="srd51-creature-sahuagin-bite"></a> **Action — Bite.** `srd51.creature.sahuagin.bite` Melee weapon attack, +3, reach 5 feet, one target. Hit deals 3 (1d4 + 1) piercing.
- <a id="srd51-creature-sahuagin-claws"></a> **Action — Claws.** `srd51.creature.sahuagin.claws` Melee weapon attack, +3, reach 5 feet, one target. Hit deals 3 (1d4 + 1) slashing.
- <a id="srd51-creature-sahuagin-spear"></a> **Action — Spear.** `srd51.creature.sahuagin.spear` Melee or ranged weapon attack, +3, reach 5 feet or range 20/60 feet, one target.
  Hit deals 4 (1d6 + 1) piercing, or 5 (1d8 + 1) in two-handed melee use.

[SRD p. 343, Sahuagin](../../references/srd-5.1/pages/page-343.md)

<a id="srd51-creature-salamander"></a>

## Salamander

`srd51.creature.salamander`

- **Profile.** Large elemental, neutral evil. AC 15 (natural armor). HP 90 (12d10 + 24). Speed 30 ft. CR 5 (1,800 XP).
- **Ability scores.** Str 18 (+4), Dex 14 (+2), Con 15 (+2), Int 11 (+0), Wis 10 (+0), Cha 12 (+1).
- **Vulnerabilities.** cold.
- **Resistances.** bludgeoning, piercing, and slashing from nonmagical attacks.
- **Damage immunity.** fire.
- **Senses.** darkvision 60 ft, passive Perception 10.
- **Languages.** Ignan.

- <a id="srd51-creature-salamander-heated-body"></a> **Trait — Heated Body.** `srd51.creature.salamander.heated-body` Apply [Heated Body](creatures.md#srd51-creature-rules-heated-body) for
  7 (2d6) fire damage.
- <a id="srd51-creature-salamander-heated-weapons"></a> **Trait — Heated Weapons.** `srd51.creature.salamander.heated-weapons` Metal melee weapons it wields add 3 (1d6) fire on a hit. The listed Spear
  damage already includes this.
- <a id="srd51-creature-salamander-multiattack"></a> **Action — Multiattack.** `srd51.creature.salamander.multiattack` One Spear and one Tail.
- <a id="srd51-creature-salamander-spear"></a> **Action — Spear.** `srd51.creature.salamander.spear` Melee or ranged weapon attack, +7, reach 5 feet or range 20/60 feet, one target.
  Hit deals 11 (2d6 + 4) piercing, or 13 (2d8 + 4) in two-handed melee use, plus 3 (1d6) fire.
- <a id="srd51-creature-salamander-tail"></a> **Action — Tail.** `srd51.creature.salamander.tail` Melee weapon attack, +7, reach 10 feet, one target. Hit deals 11 (2d6 + 4)
  bludgeoning plus 7 (2d6) fire and grapples with escape DC 14. Until the grapple ends the target is
  restrained and the salamander cannot target another creature with Tail. It may make subsequent
  Tail attacks against the grappled target hit automatically.

[SRD p. 344, Salamander](../../references/srd-5.1/pages/page-344.md)

<a id="srd51-creature-satyr"></a>

## Satyr

`srd51.creature.satyr`

- **Profile.** Medium fey, chaotic neutral. AC 14 (leather armor). HP 31 (7d8). Speed 40 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 12 (+1), Dex 16 (+3), Con 11 (+0), Int 12 (+1), Wis 10 (+0), Cha 14 (+2).
- **Skills.** Perception +2, Performance +6, Stealth +5.
- **Senses.** passive Perception 12.
- **Languages.** Common, Elvish, Sylvan.

- <a id="srd51-creature-satyr-magic-resistance"></a> **Trait — Magic Resistance.** `srd51.creature.satyr.magic-resistance` [Magic Resistance](creatures.md#srd51-creature-rules-magic-resistance).
- <a id="srd51-creature-satyr-ram"></a> **Action — Ram.** `srd51.creature.satyr.ram` Melee weapon attack, +3, reach 5 feet, one target. Hit deals 6 (2d4 + 1)
  bludgeoning.
- <a id="srd51-creature-satyr-shortsword"></a> **Action — Shortsword.** `srd51.creature.satyr.shortsword` Melee weapon attack, +5, reach 5 feet, one target. Hit deals 6 (1d6 + 3)
  piercing.
- <a id="srd51-creature-satyr-shortbow"></a> **Action — Shortbow.** `srd51.creature.satyr.shortbow` Ranged weapon attack, +5, range 80/320 feet, one target. Hit deals 6 (1d6 + 3)
  piercing.

[SRD p. 344, Satyr](../../references/srd-5.1/pages/page-344.md)

<a id="srd51-creature-shadow"></a>

## Shadow

`srd51.creature.shadow`

- **Profile.** Medium undead, chaotic evil. AC 12. HP 16 (3d8 + 3). Speed 40 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 6 (−2), Dex 14 (+2), Con 13 (+1), Int 6 (−2), Wis 10 (+0), Cha 8 (−1).
- **Skills.** Stealth +4 (+6 in dim light or darkness).
- **Vulnerabilities.** radiant.
- **Resistances.** acid, cold, fire, lightning, thunder; bludgeoning, piercing, and slashing from nonmagical attacks.
- **Damage immunity.** necrotic, poison.
- **Condition immunity.** exhaustion, frightened, grappled, paralyzed, petrified, poisoned, prone, restrained.
- **Senses.** darkvision 60 ft, passive Perception 10.
- **Languages.** None.

- <a id="srd51-creature-shadow-amorphous"></a> **Trait — Amorphous.** `srd51.creature.shadow.amorphous` Apply [Amorphous](#srd51-creature-rules-amorphous).
- <a id="srd51-creature-shadow-shadow-stealth"></a> **Trait — Shadow Stealth.** `srd51.creature.shadow.shadow-stealth` Hide as a bonus action while in dim light or darkness.
- <a id="srd51-creature-shadow-sunlight-weakness"></a> **Trait — Sunlight Weakness.** `srd51.creature.shadow.sunlight-weakness` Sunlight imposes disadvantage on its attack rolls, ability checks, and
  saves.
- <a id="srd51-creature-shadow-strength-drain"></a> **Action — Strength Drain.** `srd51.creature.shadow.strength-drain` Melee weapon attack, +4, reach 5 feet, one creature. Hit deals 9 (2d6 +
  2) necrotic and reduces Strength by 1d4. Strength reduced to 0 by this attack kills. Otherwise the
  reduction lasts through the target's next completed short or long rest. If a non-evil humanoid dies
  from the attack, a shadow rises from the corpse 1d4 hours later.

[SRD p. 344, Shadow](../../references/srd-5.1/pages/page-344.md) · [SRD p. 345, Shadow continuation](../../references/srd-5.1/pages/page-345.md)

<a id="srd51-creature-shambling-mound"></a>

## Shambling Mound

`srd51.creature.shambling-mound`

- **Profile.** Large plant, unaligned. AC 15 (natural armor). HP 136 (16d10 + 48). Speed 20 ft, swim 20 ft. CR 5 (1,800 XP).
- **Ability scores.** Str 18 (+4), Dex 8 (−1), Con 16 (+3), Int 5 (−3), Wis 10 (+0), Cha 5 (−3).
- **Skills.** Stealth +2.
- **Resistances.** cold, fire.
- **Damage immunity.** lightning.
- **Condition immunity.** blinded, deafened, exhaustion.
- **Senses.** blindsight 60 ft (blind beyond this radius), passive Perception 10.
- **Languages.** None.

- <a id="srd51-creature-shambling-mound-lightning-absorption"></a> **Trait — Lightning Absorption.** `srd51.creature.shambling-mound.lightning-absorption` Apply [Damage Absorption](creatures.md#srd51-creature-rules-damage-absorption) to lightning.
  See [ooze splitting](#srd51-creature-rules-ooze-splitting) for a different effect that also checks
  the incoming amount before immunity. [SRD p. 345, Shambling Mound, Lightning Absorption](../../references/srd-5.1/pages/page-345.md)
- <a id="srd51-creature-shambling-mound-multiattack"></a> **Action — Multiattack.** `srd51.creature.shambling-mound.multiattack` Two Slam attacks. If both hit one Medium or smaller target, it is grappled
  with escape DC 14 and the mound uses Engulf on it.
- <a id="srd51-creature-shambling-mound-slam"></a> **Action — Slam.** `srd51.creature.shambling-mound.slam` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 13 (2d8 + 4)
  bludgeoning.
- <a id="srd51-creature-shambling-mound-engulf"></a> **Action — Engulf.** `srd51.creature.shambling-mound.engulf` Engulf a Medium or smaller creature it grapples. The captive is blinded,
  restrained, and unable to breathe, moves with the mound, and at each of the mound's turn starts saves
  Constitution DC 14 or takes 13 (2d8 + 4) bludgeoning. The mound can hold only one engulfed creature.
  **Project interpretation.** Engulf keeps the underlying grapple and ends when it ends. The captive
  may use the ordinary [monster grapple escape](creatures.md#srd51-creature-rules-grapple) at DC 14.
  Engulf starts on a grappled target and neither terminates the grapple nor provides another way out.
  A reading with independent confinement would leave no defined escape check.

[SRD p. 345, Shambling Mound](../../references/srd-5.1/pages/page-345.md)

<a id="srd51-creature-shield-guardian"></a>

## Shield Guardian

`srd51.creature.shield-guardian`

- **Profile.** Large construct, unaligned. AC 17 (natural armor). HP 142 (15d10 + 60). Speed 30 ft. CR 7 (2,900 XP).
- **Ability scores.** Str 18 (+4), Dex 8 (−1), Con 18 (+4), Int 7 (−2), Wis 10 (+0), Cha 3 (−4).
- **Damage immunity.** poison.
- **Condition immunity.** charmed, exhaustion, frightened, paralyzed, poisoned.
- **Senses.** blindsight 10 ft, darkvision 60 ft, passive Perception 10.
- **Languages.** understands commands given in any language but can’t speak.

- <a id="srd51-creature-shield-guardian-bound"></a> **Trait — Bound.** `srd51.creature.shield-guardian.bound` The guardian is magically linked to an amulet. On the same plane, its wearer can
  call the guardian to itself telepathically, and the guardian knows the amulet's distance and
  direction. If within 60 feet of the wearer, use the same preserve-total split as [damage
  transfer](creatures.md#srd51-creature-rules-damage-transfer). The amount transferred to the guardian
  is explicitly rounded up and the wearer keeps the remainder.
  **Project interpretation.** Apply the split to each typed damage amount after the wearer's damage
  adjustments and before either recipient's temporary HP. The phrase "damage the wearer takes" puts
  the wearer's mitigation first, and temporary HP then buffers damage received. The guardian receives
  its portion with the original damage type and can apply its own defenses.
- <a id="srd51-creature-shield-guardian-regeneration"></a> **Trait — Regeneration.** `srd51.creature.shield-guardian.regeneration` At the start of its turn, regain 10 HP if it has at least 1 HP.
- <a id="srd51-creature-shield-guardian-spell-storing"></a> **Trait — Spell Storing.** `srd51.creature.shield-guardian.spell-storing` A spellcaster wearing the amulet can cast a spell of level 4 or lower on
  the guardian. The cast has no immediate effect and replaces any previously stored spell. The
  wearer's command or a situation set by the original caster triggers release. The guardian casts
  with the original parameters and no components, emptying the store.
  **Project interpretation.** Pay normal casting costs on deposit and treat the guardian as the
  storage recipient. Check actual spell targets when released. As in [stored item
  spells](magic-items.md#srd51-item-rules-stored-spells), retain the original caster's casting level,
  spellcasting ability, DC and attack bonus. The guardian, as the releasing caster, maintains any
  required [concentration](spellcasting.md#srd51-core-concentration). The trigger itself costs no
  separate action and authorizes the guardian to begin casting at its first legal opportunity under
  the spell's [normal casting time](spellcasting.md#srd51-core-cast-spell). A reaction spell still
  needs its own reaction trigger. An alternative makes the spell resolve instantly with the
  storage trigger, but the feature explicitly waives components and gives no casting-time override.
- <a id="srd51-creature-shield-guardian-multiattack"></a> **Action — Multiattack.** `srd51.creature.shield-guardian.multiattack` Two Fist attacks.
- <a id="srd51-creature-shield-guardian-fist"></a> **Action — Fist.** `srd51.creature.shield-guardian.fist` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 11 (2d6 + 4)
  bludgeoning.
- <a id="srd51-creature-shield-guardian-shield"></a> **Reaction — Shield.** `srd51.creature.shield-guardian.shield` When a creature attacks the amulet wearer and the guardian is within 5 feet of
  the wearer, give the wearer +2 AC against that attack.

[SRD p. 345, Shield Guardian](../../references/srd-5.1/pages/page-345.md) · [SRD p. 346, Shield Guardian continuation](../../references/srd-5.1/pages/page-346.md)

<a id="srd51-creature-skeleton"></a>

## Skeleton

`srd51.creature.skeleton`

- **Profile.** Medium undead, lawful evil. AC 13 (armor scraps). HP 13 (2d8 + 4). Speed 30 ft. CR 1/4 (50 XP).
- **Ability scores.** Str 10 (+0), Dex 14 (+2), Con 15 (+2), Int 6 (−2), Wis 8 (−1), Cha 5 (−3).
- **Vulnerabilities.** bludgeoning.
- **Damage immunity.** poison.
- **Condition immunity.** exhaustion, poisoned.
- **Senses.** darkvision 60 ft, passive Perception 9.
- **Languages.** understands all languages it knew in life but can’t speak.

- <a id="srd51-creature-skeleton-shortsword"></a> **Action — Shortsword.** `srd51.creature.skeleton.shortsword` Melee weapon attack, +4, reach 5 feet, one target. Hit deals 5 (1d6 + 2)
  piercing.
- <a id="srd51-creature-skeleton-shortbow"></a> **Action — Shortbow.** `srd51.creature.skeleton.shortbow` Ranged weapon attack, +4, range 80/320 feet, one target. Hit deals 5 (1d6 + 2)
  piercing.

[SRD p. 346, Skeleton](../../references/srd-5.1/pages/page-346.md)

<a id="srd51-creature-minotaur-skeleton"></a>

## Minotaur Skeleton

`srd51.creature.minotaur-skeleton`

- **Profile.** Large undead, lawful evil. AC 12 (natural armor). HP 67 (9d10 + 18). Speed 40 ft. CR 2 (450 XP).
- **Ability scores.** Str 18 (+4), Dex 11 (+0), Con 15 (+2), Int 6 (−2), Wis 8 (−1), Cha 5 (−3).
- **Vulnerabilities.** bludgeoning.
- **Damage immunity.** poison.
- **Condition immunity.** exhaustion, poisoned.
- **Senses.** darkvision 60 ft, passive Perception 9.
- **Languages.** understands Abyssal but can’t speak.

- <a id="srd51-creature-minotaur-skeleton-charge"></a> **Trait — Charge.** `srd51.creature.minotaur-skeleton.charge` Move at least 10 feet straight toward a target and hit with Gore on that turn to
  add 9 (2d8) piercing. A hit creature also saves Strength DC 14 or is pushed up to 10 feet away and
  knocked prone.
- <a id="srd51-creature-minotaur-skeleton-greataxe"></a> **Action — Greataxe.** `srd51.creature.minotaur-skeleton.greataxe` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 17 (2d12 + 4)
  slashing.
- <a id="srd51-creature-minotaur-skeleton-gore"></a> **Action — Gore.** `srd51.creature.minotaur-skeleton.gore` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 13 (2d8 + 4) piercing.

[SRD p. 346, Minotaur Skeleton](../../references/srd-5.1/pages/page-346.md)

<a id="srd51-creature-warhorse-skeleton"></a>

## Warhorse Skeleton

`srd51.creature.warhorse-skeleton`

- **Profile.** Large undead, lawful evil. AC 13 (barding scraps). HP 22 (3d10 + 6). Speed 60 ft. CR 1/2 (100 XP).
- **Ability scores.** Str 18 (+4), Dex 12 (+1), Con 15 (+2), Int 2 (−4), Wis 8 (−1), Cha 5 (−3).
- **Vulnerabilities.** bludgeoning.
- **Damage immunity.** poison.
- **Condition immunity.** exhaustion, poisoned.
- **Senses.** darkvision 60 ft, passive Perception 9.
- **Languages.** None.

- <a id="srd51-creature-warhorse-skeleton-hooves"></a> **Action — Hooves.** `srd51.creature.warhorse-skeleton.hooves` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 11 (2d6 + 4)
  bludgeoning.

[SRD p. 346, Warhorse Skeleton](../../references/srd-5.1/pages/page-346.md)

<a id="srd51-creature-specter"></a>

## Specter

`srd51.creature.specter`

- **Profile.** Medium undead, chaotic evil. AC 12. HP 22 (5d8). Speed 0 ft, fly 50 ft (hover). CR 1 (200 XP).
- **Ability scores.** Str 1 (−5), Dex 14 (+2), Con 11 (+0), Int 10 (+0), Wis 10 (+0), Cha 11 (+0).
- **Resistances.** acid, cold, fire, lightning, thunder; bludgeoning, piercing, and slashing from nonmagical attacks.
- **Damage immunity.** necrotic, poison.
- **Condition immunity.** charmed, exhaustion, grappled, paralyzed, petrified, poisoned, prone, restrained, unconscious.
- **Senses.** darkvision 60 ft, passive Perception 10.
- **Languages.** understands all languages it knew in life but can’t speak.

- <a id="srd51-creature-specter-incorporeal-movement"></a> **Trait — Incorporeal Movement.** `srd51.creature.specter.incorporeal-movement` Treat passage through creatures and objects as difficult terrain.
  Ending its turn in an object deals it 5 (1d10) force damage.
- <a id="srd51-creature-specter-sunlight-sensitivity"></a> **Trait — Sunlight Sensitivity.** `srd51.creature.specter.sunlight-sensitivity` In sunlight it has disadvantage on attack rolls and sight-based
  Wisdom (Perception) checks.
- <a id="srd51-creature-specter-life-drain"></a> **Action — Life Drain.** `srd51.creature.specter.life-drain` Melee spell attack, +4, reach 5 feet, one creature. Hit deals 10 (3d6)
  necrotic. On a failed DC 10 Constitution save, the target's maximum HP decreases by the damage it took
  until it completes a long rest. It dies if its maximum reaches 0 this way.

[SRD p. 346, Specter](../../references/srd-5.1/pages/page-346.md) · [SRD p. 347, Specter continuation](../../references/srd-5.1/pages/page-347.md)

<a id="srd51-creature-androsphinx"></a>

## Androsphinx

`srd51.creature.androsphinx`

- **Profile.** Large monstrosity, lawful neutral. AC 17 (natural armor). HP 199 (19d10 + 95). Speed 40 ft, fly 60 ft. CR 17 (18,000 XP).
- **Ability scores.** Str 22 (+6), Dex 10 (+0), Con 20 (+5), Int 16 (+3), Wis 18 (+4), Cha 23 (+6).
- **Saves.** Dex +6, Con +11, Int +9, Wis +10.
- **Skills.** Arcana +9, Perception +10, Religion +15.
- **Damage immunity.** psychic; bludgeoning, piercing, and slashing from nonmagical attacks.
- **Condition immunity.** charmed, frightened.
- **Senses.** truesight 120 ft, passive Perception 20.
- **Languages.** Common, Sphinx.

- <a id="srd51-creature-androsphinx-inscrutable"></a> **Trait — Inscrutable.** `srd51.creature.androsphinx.inscrutable` Immune to emotion-sensing and mind-reading effects and any divination spell
  it refuses. Wisdom (Insight) checks about its motives or sincerity have disadvantage.
- <a id="srd51-creature-androsphinx-magic-weapons"></a> **Trait — Magic Weapons.** `srd51.creature.androsphinx.magic-weapons` [Magic Weapons](creatures.md#srd51-creature-rules-magic-weapons).
- <a id="srd51-creature-androsphinx-spellcasting"></a> **Trait — Spellcasting.** `srd51.creature.androsphinx.spellcasting` Level-12 Wisdom cleric casting, save DC 18 and spell attacks +10, no
  material components. Cantrips at will [Sacred Flame](spells-n-s.md#srd51-spell-sacred-flame), [Spare
  the Dying](spells-n-s.md#srd51-spell-spare-the-dying),
  [Thaumaturgy](spells-t-z.md#srd51-spell-thaumaturgy). Level 1 (4 slots)
  [Command](spells-a-c.md#srd51-spell-command), [Detect Evil and
  Good](spells-d-e.md#srd51-spell-detect-evil-and-good), [Detect
  Magic](spells-d-e.md#srd51-spell-detect-magic). Level 2 (3) [Lesser
  Restoration](spells-h-m.md#srd51-spell-lesser-restoration), [Zone of
  Truth](spells-t-z.md#srd51-spell-zone-of-truth). Level 3 (3) [Dispel
  Magic](spells-d-e.md#srd51-spell-dispel-magic), [Tongues](spells-t-z.md#srd51-spell-tongues). Level 4
  (3) [Banishment](spells-a-c.md#srd51-spell-banishment), [Freedom of
  Movement](spells-e-g.md#srd51-spell-freedom-of-movement). Level 5 (2) [Flame
  Strike](spells-e-g.md#srd51-spell-flame-strike), [Greater
  Restoration](spells-e-g.md#srd51-spell-greater-restoration). Level 6 (1) [Heroes'
  Feast](spells-h-m.md#srd51-spell-heroes-feast).
- <a id="srd51-creature-androsphinx-multiattack"></a> **Action — Multiattack.** `srd51.creature.androsphinx.multiattack` Two Claw attacks.
- <a id="srd51-creature-androsphinx-claw"></a> **Action — Claw.** `srd51.creature.androsphinx.claw` Melee weapon attack, +12, reach 5 feet, one target. Hit deals 17 (2d10 + 6)
  slashing.
- <a id="srd51-creature-androsphinx-roar"></a> **Action — Roar.** `srd51.creature.androsphinx.roar` Three uses per long rest, in order. Each creature within 500 feet that can hear
  saves against the magical roar. First, Wisdom DC 18 or frightened for 1 minute. An affected creature
  may repeat at the end of each of its own turns and recover on success. Second, Wisdom DC 18 or
  deafened and frightened for 1 minute. A creature frightened by it is also paralyzed and may repeat
  at the end of each of its own turns, ending the effect on success. Third, Constitution DC 18 or take 44 (8d10) thunder and fall prone.
  Success halves damage without knocking it prone.
- <a id="srd51-creature-androsphinx-legendary-actions"></a> **Legendary budget — Legendary Actions.** `srd51.creature.androsphinx.legendary-actions` Three, restored at the start of its turn. Use one option at the end of another
  creature's turn.
- <a id="srd51-creature-androsphinx-claw-attack"></a> **Legendary option — Claw Attack (1).** `srd51.creature.androsphinx.claw-attack` One Claw.
- <a id="srd51-creature-androsphinx-teleport"></a> **Legendary option — Teleport (2).** `srd51.creature.androsphinx.teleport` Magically move itself and worn and carried equipment up to 120
  feet into an unoccupied space it can see.
- <a id="srd51-creature-androsphinx-cast-a-spell"></a> **Legendary option — Cast a Spell (3).** `srd51.creature.androsphinx.cast-a-spell` Cast a prepared spell and spend a slot normally.

[SRD p. 347, Androsphinx](../../references/srd-5.1/pages/page-347.md) · [SRD p. 348, Androsphinx continuation](../../references/srd-5.1/pages/page-348.md) · [SRD p. 176, Sacred Flame](../../references/srd-5.1/pages/page-176.md) · [SRD p. 181, Spare the Dying](../../references/srd-5.1/pages/page-181.md) · [SRD p. 187, Thaumaturgy](../../references/srd-5.1/pages/page-187.md) · [SRD p. 125, Command](../../references/srd-5.1/pages/page-125.md) · [SRD p. 134, Detect Evil and Good](../../references/srd-5.1/pages/page-134.md) · [SRD p. 134, Detect Magic](../../references/srd-5.1/pages/page-134.md) · [SRD p. 158, Lesser Restoration](../../references/srd-5.1/pages/page-158.md) · [SRD p. 193, Zone of Truth](../../references/srd-5.1/pages/page-193.md) · [SRD p. 136, Dispel Magic](../../references/srd-5.1/pages/page-136.md) · [SRD p. 187, Tongues](../../references/srd-5.1/pages/page-187.md) · [SRD p. 120, Banishment](../../references/srd-5.1/pages/page-120.md) · [SRD p. 147, Freedom of Movement](../../references/srd-5.1/pages/page-147.md) · [SRD p. 145, Flame Strike](../../references/srd-5.1/pages/page-145.md) · [SRD p. 150, Greater Restoration](../../references/srd-5.1/pages/page-150.md) · [SRD p. 154, Heroes’ Feast](../../references/srd-5.1/pages/page-154.md)

<a id="srd51-creature-gynosphinx"></a>

## Gynosphinx

`srd51.creature.gynosphinx`

- **Profile.** Large monstrosity, lawful neutral. AC 17 (natural armor). HP 136 (16d10 + 48). Speed 40 ft, fly 60 ft. CR 11 (7,200 XP).
- **Ability scores.** Str 18 (+4), Dex 15 (+2), Con 16 (+3), Int 18 (+4), Wis 18 (+4), Cha 18 (+4).
- **Skills.** Arcana +12, History +12, Perception +8, Religion +8.
- **Resistances.** bludgeoning, piercing, and slashing from nonmagical attacks.
- **Damage immunity.** psychic.
- **Condition immunity.** charmed, frightened.
- **Senses.** truesight 120 ft, passive Perception 18.
- **Languages.** Common, Sphinx.

- <a id="srd51-creature-gynosphinx-inscrutable"></a> **Trait — Inscrutable.** `srd51.creature.gynosphinx.inscrutable` Apply [Inscrutable](#srd51-creature-androsphinx-inscrutable).
- <a id="srd51-creature-gynosphinx-magic-weapons"></a> **Trait — Magic Weapons.** `srd51.creature.gynosphinx.magic-weapons` [Magic Weapons](creatures.md#srd51-creature-rules-magic-weapons).
- <a id="srd51-creature-gynosphinx-spellcasting"></a> **Trait — Spellcasting.** `srd51.creature.gynosphinx.spellcasting` Level-9 Intelligence wizard casting, save DC 16 and spell attacks +8, no
  material components. Cantrips at will [Mage Hand](spells-h-m.md#srd51-spell-mage-hand), [Minor
  Illusion](spells-h-m.md#srd51-spell-minor-illusion),
  [Prestidigitation](spells-n-s.md#srd51-spell-prestidigitation). Level 1 (4 slots) [Detect
  Magic](spells-d-e.md#srd51-spell-detect-magic), [Identify](spells-h-m.md#srd51-spell-identify),
  [Shield](spells-n-s.md#srd51-spell-shield). Level 2 (3)
  [Darkness](spells-d-e.md#srd51-spell-darkness), [Locate
  Object](spells-h-m.md#srd51-spell-locate-object), [Suggestion](spells-n-s.md#srd51-spell-suggestion).
  Level 3 (3) [Dispel Magic](spells-d-e.md#srd51-spell-dispel-magic), [Remove
  Curse](spells-n-s.md#srd51-spell-remove-curse), [Tongues](spells-t-z.md#srd51-spell-tongues). Level 4
  (3) [Banishment](spells-a-c.md#srd51-spell-banishment), [Greater
  Invisibility](spells-e-g.md#srd51-spell-greater-invisibility). Level 5 (1) [Legend
  Lore](spells-h-m.md#srd51-spell-legend-lore).
- <a id="srd51-creature-gynosphinx-multiattack"></a> **Action — Multiattack.** `srd51.creature.gynosphinx.multiattack` Two Claw attacks.
- <a id="srd51-creature-gynosphinx-claw"></a> **Action — Claw.** `srd51.creature.gynosphinx.claw` Melee weapon attack, +8, reach 5 feet, one target. Hit deals 13 (2d8 + 4) slashing.
- <a id="srd51-creature-gynosphinx-legendary-actions"></a> **Legendary budget — Legendary Actions.** `srd51.creature.gynosphinx.legendary-actions` Three, restored at the start of its turn. Use one option at the end of another
  creature's turn.
- <a id="srd51-creature-gynosphinx-claw-attack"></a> **Legendary option — Claw Attack (1).** `srd51.creature.gynosphinx.claw-attack` One Claw.
- <a id="srd51-creature-gynosphinx-teleport"></a> **Legendary option — Teleport (2).** `srd51.creature.gynosphinx.teleport` Magically move itself and worn and carried equipment up to 120
  feet into an unoccupied space it can see.
- <a id="srd51-creature-gynosphinx-cast-a-spell"></a> **Legendary option — Cast a Spell (3).** `srd51.creature.gynosphinx.cast-a-spell` Cast a prepared spell and spend a slot normally.

[SRD p. 348, Gynosphinx](../../references/srd-5.1/pages/page-348.md) · [SRD p. 160, Mage Hand](../../references/srd-5.1/pages/page-160.md) · [SRD p. 164, Minor Illusion](../../references/srd-5.1/pages/page-164.md) · [SRD p. 170, Prestidigitation](../../references/srd-5.1/pages/page-170.md) · [SRD p. 134, Detect Magic](../../references/srd-5.1/pages/page-134.md) · [SRD p. 155, Identify](../../references/srd-5.1/pages/page-155.md) · [SRD p. 179, Shield](../../references/srd-5.1/pages/page-179.md) · [SRD p. 133, Darkness](../../references/srd-5.1/pages/page-133.md) · [SRD p. 159, Locate Object](../../references/srd-5.1/pages/page-159.md) · [SRD p. 183, Suggestion](../../references/srd-5.1/pages/page-183.md) · [SRD p. 136, Dispel Magic](../../references/srd-5.1/pages/page-136.md) · [SRD p. 174, Remove Curse](../../references/srd-5.1/pages/page-174.md) · [SRD p. 187, Tongues](../../references/srd-5.1/pages/page-187.md) · [SRD p. 120, Banishment](../../references/srd-5.1/pages/page-120.md) · [SRD p. 150, Greater Invisibility](../../references/srd-5.1/pages/page-150.md) · [SRD p. 158, Legend Lore](../../references/srd-5.1/pages/page-158.md)

<a id="srd51-creature-sprite"></a>

## Sprite

`srd51.creature.sprite`

- **Profile.** Tiny fey, neutral good. AC 15 (leather armor). HP 2 (1d4). Speed 10 ft, fly 40 ft. CR 1/4 (50 XP).
- **Ability scores.** Str 3 (−4), Dex 18 (+4), Con 10 (+0), Int 14 (+2), Wis 13 (+1), Cha 11 (+0).
- **Skills.** Perception +3, Stealth +8.
- **Senses.** passive Perception 13.
- **Languages.** Common, Elvish, Sylvan.

- <a id="srd51-creature-sprite-longsword"></a> **Action — Longsword.** `srd51.creature.sprite.longsword` Melee weapon attack, +2, reach 5 feet, one target. Hit deals 1 slashing.
- <a id="srd51-creature-sprite-shortbow"></a> **Action — Shortbow.** `srd51.creature.sprite.shortbow` Ranged weapon attack, +6, range 40/160 feet, one target. Hit deals 1 piercing
  and requires Constitution DC 10. Failure poisons for 1 minute. A result of 5 or less also makes the
  poisoned creature unconscious for that time or until it takes damage or another creature spends an
  action shaking it awake.
- <a id="srd51-creature-sprite-heart-sight"></a> **Action — Heart Sight.** `srd51.creature.sprite.heart-sight` Touch a creature and learn its present emotional state by magic. A failed DC
  10 Charisma save also reveals its alignment. Celestials, fiends, and undead fail automatically.
- <a id="srd51-creature-sprite-invisibility"></a> **Action — Invisibility.** `srd51.creature.sprite.invisibility` The sprite and its worn and carried equipment turn invisible until it
  attacks, casts, or loses concentration as if concentrating on a spell.

[SRD p. 348, Sprite](../../references/srd-5.1/pages/page-348.md) · [SRD p. 349, Sprite continuation](../../references/srd-5.1/pages/page-349.md)

<a id="srd51-creature-stirge"></a>

## Stirge

`srd51.creature.stirge`

- **Profile.** Tiny beast, unaligned. AC 14 (natural armor). HP 2 (1d4). Speed 10 ft, fly 40 ft. CR 1/8 (25 XP).
- **Ability scores.** Str 4 (−3), Dex 16 (+3), Con 11 (+0), Int 2 (−4), Wis 8 (−1), Cha 6 (−2).
- **Senses.** darkvision 60 ft, passive Perception 9.
- **Languages.** None.

- <a id="srd51-creature-stirge-blood-drain"></a> **Action — Blood Drain.** `srd51.creature.stirge.blood-drain` Melee weapon attack, +5, reach 5 feet, one creature. Hit deals 5 (1d4 + 3)
  piercing and attaches the stirge. While attached it makes no attacks. The victim instead loses 5 (1d4
  + 3) HP to blood loss at the start of each stirge turn. The stirge can detach using 5 feet of movement
  and does so after draining 10 HP or the victim dies. Any creature, including the victim, can spend an
  action detaching it.
  **Project interpretation.** The turn-start loss reduces current HP directly, with the same
  [non-damage boundary](combat.md#srd51-core-dropping-to-zero-hit-points) as [Infernal
  Wound](creatures.md#srd51-creature-rules-infernal-wound). Count only this blood loss toward 10 HP,
  because the trait separates it from the initial piercing hit. Once that threshold is met or the
  target dies, the stirge detaches at the first opportunity it can spend the required 5 feet of
  movement. Until then, ordinary attachment effects continue. Another creature can spend an action
  to detach it before it moves.

[SRD p. 349, Stirge](../../references/srd-5.1/pages/page-349.md)

<a id="srd51-creature-succubus-incubus"></a>

## Succubus/Incubus

`srd51.creature.succubus-incubus`

- **Profile.** Medium fiend (shapechanger), neutral evil. AC 15 (natural armor). HP 66 (12d8 + 12). Speed 30 ft, fly 60 ft. CR 4 (1,100 XP).
- **Ability scores.** Str 8 (−1), Dex 17 (+3), Con 13 (+1), Int 15 (+2), Wis 12 (+1), Cha 20 (+5).
- **Skills.** Deception +9, Insight +5, Perception +5, Persuasion +9, Stealth +7.
- **Resistances.** cold, fire, lightning, poison; bludgeoning, piercing, and slashing from nonmagical attacks.
- **Senses.** darkvision 60 ft, passive Perception 15.
- **Languages.** Abyssal, Common, Infernal, telepathy 60 ft.

- <a id="srd51-creature-succubus-incubus-telepathic-bond"></a> **Trait — Telepathic Bond.** `srd51.creature.succubus-incubus.telepathic-bond` Its telepathy with a creature it has charmed ignores distance and planar
  separation.
- <a id="srd51-creature-succubus-incubus-shapechanger"></a> **Trait — Shapechanger.** `srd51.creature.succubus-incubus.shapechanger` As an action become a Small or Medium humanoid or return to true form. Lose
  flying speed without wings. Only size and speed may change. Gear stays unchanged. Death restores the
  true form.
- <a id="srd51-creature-succubus-incubus-claw"></a> **Action — Claw.** `srd51.creature.succubus-incubus.claw` Fiend form only. Melee weapon attack, +5, reach 5 feet, one target. Hit deals 6
  (1d6 + 3) slashing.
- <a id="srd51-creature-succubus-incubus-charm"></a> **Action — Charm.** `srd51.creature.succubus-incubus.charm` Choose one humanoid it sees within 30 feet. A failed DC 15 Wisdom save charms for
  1 day and makes the victim obey verbal or telepathic commands. Harm to the victim or a suicidal order
  permits a repeat save and ends the effect on success. A successful save or the charm ending gives the
  victim immunity to this fiend's Charm for 24 hours. Charming a new creature ends the previous charm.
- <a id="srd51-creature-succubus-incubus-draining-kiss"></a> **Action — Draining Kiss.** `srd51.creature.succubus-incubus.draining-kiss` Kiss a willing creature or one charmed by the fiend. It saves Constitution
  DC 15 against 32 (5d10 + 5) psychic damage, half on success. Reduce maximum HP by damage taken until
  its next completed long rest. Reduction to 0 maximum HP kills.
- <a id="srd51-creature-succubus-incubus-etherealness"></a> **Action — Etherealness.** `srd51.creature.succubus-incubus.etherealness` Cross magically between the Material and Ethereal Planes.

[SRD p. 349, Succubus/Incubus](../../references/srd-5.1/pages/page-349.md) · [SRD p. 350, Succubus/Incubus continuation](../../references/srd-5.1/pages/page-350.md)

<a id="srd51-creature-tarrasque"></a>

## Tarrasque

`srd51.creature.tarrasque`

- **Profile.** Gargantuan monstrosity (titan), unaligned. AC 25 (natural armor). HP 676 (33d20 + 330). Speed 40 ft. CR 30 (155,000 XP).
- **Ability scores.** Str 30 (+10), Dex 11 (+0), Con 30 (+10), Int 3 (−4), Wis 11 (+0), Cha 11 (+0).
- **Saves.** Int +5, Wis +9, Cha +9.
- **Damage immunity.** fire, poison; bludgeoning, piercing, and slashing from nonmagical attacks.
- **Condition immunity.** charmed, frightened, paralyzed, poisoned.
- **Senses.** blindsight 120 ft, passive Perception 10.
- **Languages.** None.

- <a id="srd51-creature-tarrasque-legendary-resistance"></a> **Trait — Legendary Resistance.** `srd51.creature.tarrasque.legendary-resistance` [Legendary
  Resistance](creatures.md#srd51-creature-rules-legendary-resistance), three uses per day.
- <a id="srd51-creature-tarrasque-magic-resistance"></a> **Trait — Magic Resistance.** `srd51.creature.tarrasque.magic-resistance` [Magic Resistance](creatures.md#srd51-creature-rules-magic-resistance).
- <a id="srd51-creature-tarrasque-reflective-carapace"></a> **Trait — Reflective Carapace.** `srd51.creature.tarrasque.reflective-carapace` When targeted by [Magic
  Missile](spells-h-m.md#srd51-spell-magic-missile), a line spell, or a spell requiring a ranged attack
  roll, roll d6. The tarrasque is unaffected on every result. A 6 also sends the effect back at its
  caster as if it originated from the tarrasque and targeted that caster.
  **Project interpretation.** Roll once when a casting or a new use of an ongoing spell first
  targets the tarrasque. Handle all rays or darts assigned to it by that use together. A later
  [Sunbeam](spells-n-s.md#srd51-spell-sunbeam) action, for example, creates a new targeting event
  and a new roll. On 6, direct the affected portion only to the caster and resolve any attack or
  save with the original spell parameters. Other targets keep their original effects. The trait
  keys to the spell targeting the tarrasque. Rolling for each ray or dart, keeping a single result
  for an entire persistent casting, and redirecting portions aimed elsewhere each change that scope.
- <a id="srd51-creature-tarrasque-siege-monster"></a> **Trait — Siege Monster.** `srd51.creature.tarrasque.siege-monster` [Siege Monster](creatures.md#srd51-creature-rules-siege-monster).
- <a id="srd51-creature-tarrasque-multiattack"></a> **Action — Multiattack.** `srd51.creature.tarrasque.multiattack` It can first use Frightful Presence. Then make one Bite or Swallow, two
  Claws, one Horns, and one Tail.
- <a id="srd51-creature-tarrasque-bite"></a> **Action — Bite.** `srd51.creature.tarrasque.bite` Melee weapon attack, +19, reach 10 feet, one target. Hit deals 36 (4d12 + 10)
  piercing. A creature hit is grappled with escape DC 20 and restrained until the grapple ends. The
  tarrasque cannot bite another target while this grapple holds.
- <a id="srd51-creature-tarrasque-claw"></a> **Action — Claw.** `srd51.creature.tarrasque.claw` Melee weapon attack, +19, reach 15 feet, one target. Hit deals 28 (4d8 + 10)
  slashing.
- <a id="srd51-creature-tarrasque-horns"></a> **Action — Horns.** `srd51.creature.tarrasque.horns` Melee weapon attack, +19, reach 10 feet, one target. Hit deals 32 (4d10 + 10)
  piercing.
- <a id="srd51-creature-tarrasque-tail"></a> **Action — Tail.** `srd51.creature.tarrasque.tail` Melee weapon attack, +19, reach 20 feet, one target. Hit deals 24 (4d6 + 10)
  bludgeoning. A hit creature falls prone unless it succeeds on a DC 20 Strength save.
- <a id="srd51-creature-tarrasque-frightful-presence"></a> **Action — Frightful Presence.** `srd51.creature.tarrasque.frightful-presence` Choose creatures within 120 feet that are aware of the tarrasque.
  They save Wisdom DC 17 or are frightened for 1 minute. A creature may repeat the save at its turn end
  with disadvantage if the tarrasque is within line of sight. Success ends the effect on it. Saving
  successfully or the effect ending gives immunity to this tarrasque's presence for 24 hours.
- <a id="srd51-creature-tarrasque-swallow"></a> **Action — Swallow.** `srd51.creature.tarrasque.swallow` Bite a Large or smaller creature already grappled by it. A hit deals Bite
  damage, swallows the creature, and ends the grapple. A swallowed creature is blinded, restrained, and
  has total cover against external attacks and effects. It takes 56 (16d6) acid at each tarrasque turn
  start. If one creature inside deals at least 60 damage to the tarrasque on a turn, it saves
  Constitution DC 20 at that turn's end. Failure regurgitates all swallowed creatures, prone within 10
  feet. Death ends the restraint and lets each captive spend 30 feet of movement to exit the corpse
  prone.
- <a id="srd51-creature-tarrasque-legendary-actions"></a> **Legendary budget — Legendary Actions.** `srd51.creature.tarrasque.legendary-actions` Three, restored at its turn start. Use one option at the end of another
  creature's turn.
- <a id="srd51-creature-tarrasque-attack"></a> **Legendary option — Attack (1).** `srd51.creature.tarrasque.attack` One Claw or Tail.
- <a id="srd51-creature-tarrasque-move"></a> **Legendary option — Move (1).** `srd51.creature.tarrasque.move` Move up to half its speed.
- <a id="srd51-creature-tarrasque-chomp"></a> **Legendary option — Chomp (2).** `srd51.creature.tarrasque.chomp` One Bite or Swallow.

[SRD p. 350, Tarrasque](../../references/srd-5.1/pages/page-350.md) · [SRD p. 351, Tarrasque continuation](../../references/srd-5.1/pages/page-351.md) · [SRD p. 161, Magic Missile](../../references/srd-5.1/pages/page-161.md)

<a id="srd51-creature-treant"></a>

## Treant

`srd51.creature.treant`

- **Profile.** Huge plant, chaotic good. AC 16 (natural armor). HP 138 (12d12 + 60). Speed 30 ft. CR 9 (5,000 XP).
- **Ability scores.** Str 23 (+6), Dex 8 (−1), Con 21 (+5), Int 12 (+1), Wis 16 (+3), Cha 12 (+1).
- **Vulnerabilities.** fire.
- **Resistances.** bludgeoning, piercing.
- **Senses.** passive Perception 13.
- **Languages.** Common, Druidic, Elvish, Sylvan.

- <a id="srd51-creature-treant-false-appearance"></a> **Trait — False Appearance.** `srd51.creature.treant.false-appearance` [False appearance](creatures.md#srd51-creature-rules-false-appearance)
  as an ordinary tree.
- <a id="srd51-creature-treant-siege-monster"></a> **Trait — Siege Monster.** `srd51.creature.treant.siege-monster` [Siege Monster](creatures.md#srd51-creature-rules-siege-monster).
- <a id="srd51-creature-treant-multiattack"></a> **Action — Multiattack.** `srd51.creature.treant.multiattack` Two Slam attacks.
- <a id="srd51-creature-treant-slam"></a> **Action — Slam.** `srd51.creature.treant.slam` Melee weapon attack, +10, reach 5 feet, one target. Hit deals 16 (3d6 + 6)
  bludgeoning.
- <a id="srd51-creature-treant-rock"></a> **Action — Rock.** `srd51.creature.treant.rock` Ranged weapon attack, +10, range 60/180 feet, one target. Hit deals 28 (4d10 + 6)
  bludgeoning.
- <a id="srd51-creature-treant-animate-trees"></a> **Action — Animate Trees.** `srd51.creature.treant.animate-trees` Once per day, animate one or two trees it sees within 60 feet. Each is an
  ally and uses treant statistics with Intelligence and Charisma 1, no speech, and only the Slam action
  option. Animation ends after 1 day, when the tree or treant dies, when the treant is more than 120
  feet from it, or when the treant ends it as a bonus action. It takes root when possible after
  animation ends.

[SRD p. 351, Treant](../../references/srd-5.1/pages/page-351.md)

<a id="srd51-creature-troll"></a>

## Troll

`srd51.creature.troll`

- **Profile.** Large giant, chaotic evil. AC 15 (natural armor). HP 84 (8d10 + 40). Speed 30 ft. CR 5 (1,800 XP).
- **Ability scores.** Str 18 (+4), Dex 13 (+1), Con 20 (+5), Int 7 (−2), Wis 9 (−1), Cha 7 (−2).
- **Skills.** Perception +2.
- **Senses.** darkvision 60 ft, passive Perception 12.
- **Languages.** Giant.

- <a id="srd51-creature-troll-keen-smell"></a> **Trait — Keen Smell.** `srd51.creature.troll.keen-smell` [Keen Senses](creatures.md#srd51-creature-rules-keen-senses) for smell.
- <a id="srd51-creature-troll-regeneration"></a> **Trait — Regeneration.** `srd51.creature.troll.regeneration` Regain 10 HP at turn start. Taking acid or fire damage suppresses that next
  start's regeneration. It dies only if it starts its turn with 0 HP and cannot regenerate.
- <a id="srd51-creature-troll-multiattack"></a> **Action — Multiattack.** `srd51.creature.troll.multiattack` One Bite and two Claws.
- <a id="srd51-creature-troll-bite"></a> **Action — Bite.** `srd51.creature.troll.bite` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 7 (1d6 + 4) piercing.
- <a id="srd51-creature-troll-claw"></a> **Action — Claw.** `srd51.creature.troll.claw` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 11 (2d6 + 4) slashing.

[SRD p. 351, Troll](../../references/srd-5.1/pages/page-351.md)

<a id="srd51-creature-unicorn"></a>

## Unicorn

`srd51.creature.unicorn`

- **Profile.** Large celestial, lawful good. AC 12. HP 67 (9d10 + 18). Speed 50 ft. CR 5 (1,800 XP).
- **Ability scores.** Str 18 (+4), Dex 14 (+2), Con 15 (+2), Int 11 (+0), Wis 17 (+3), Cha 16 (+3).
- **Damage immunity.** poison.
- **Condition immunity.** charmed, paralyzed, poisoned.
- **Senses.** darkvision 60 ft, passive Perception 13.
- **Languages.** Celestial, Elvish, Sylvan, telepathy 60 ft.

- <a id="srd51-creature-unicorn-charge"></a> **Trait — Charge.** `srd51.creature.unicorn.charge` Move at least 20 feet straight toward a target and hit with Horn on the same turn
  to add 9 (2d8) piercing. A hit creature must pass a DC 15 Strength save or fall prone.
- <a id="srd51-creature-unicorn-innate-spellcasting"></a> **Trait — Innate Spellcasting.** `srd51.creature.unicorn.innate-spellcasting` Charisma, save DC 14, no components. At will [Detect Evil and
  Good](spells-d-e.md#srd51-spell-detect-evil-and-good),
  [Druidcraft](spells-d-e.md#srd51-spell-druidcraft), [Pass without
  Trace](spells-n-s.md#srd51-spell-pass-without-trace). Once per day each [Calm
  Emotions](spells-a-c.md#srd51-spell-calm-emotions), [Dispel Evil and
  Good](spells-d-e.md#srd51-spell-dispel-evil-and-good), [Entangle](spells-d-e.md#srd51-spell-entangle).
- <a id="srd51-creature-unicorn-magic-resistance"></a> **Trait — Magic Resistance.** `srd51.creature.unicorn.magic-resistance` [Magic Resistance](creatures.md#srd51-creature-rules-magic-resistance).
- <a id="srd51-creature-unicorn-magic-weapons"></a> **Trait — Magic Weapons.** `srd51.creature.unicorn.magic-weapons` [Magic Weapons](creatures.md#srd51-creature-rules-magic-weapons).
- <a id="srd51-creature-unicorn-multiattack"></a> **Action — Multiattack.** `srd51.creature.unicorn.multiattack` One Hooves and one Horn.
- <a id="srd51-creature-unicorn-hooves"></a> **Action — Hooves.** `srd51.creature.unicorn.hooves` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 11 (2d6 + 4)
  bludgeoning.
- <a id="srd51-creature-unicorn-horn"></a> **Action — Horn.** `srd51.creature.unicorn.horn` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 8 (1d8 + 4) piercing.
- <a id="srd51-creature-unicorn-healing-touch"></a> **Action — Healing Touch.** `srd51.creature.unicorn.healing-touch` Three times per day. Touch another creature with its horn to restore 11
  (2d8 + 2) HP magically, cure all its diseases, and neutralize all its poisons.
- <a id="srd51-creature-unicorn-teleport"></a> **Action — Teleport.** `srd51.creature.unicorn.teleport` Once per day. Magically take itself and up to three willing creatures it sees
  within 5 feet, with their worn and carried equipment, to a familiar location within 1 mile.
- <a id="srd51-creature-unicorn-legendary-actions"></a> **Legendary budget — Legendary Actions.** `srd51.creature.unicorn.legendary-actions` Three, restored at its turn start. Use one option at the end of another
  creature's turn.
- <a id="srd51-creature-unicorn-legendary-hooves"></a> **Legendary option — Hooves (1).** `srd51.creature.unicorn.legendary-hooves` One Hooves attack.
- <a id="srd51-creature-unicorn-shimmering-shield"></a> **Legendary option — Shimmering Shield (2).** `srd51.creature.unicorn.shimmering-shield` Give itself or a creature it sees within 60 feet +2 AC
  magically through the end of the unicorn's next turn.
- <a id="srd51-creature-unicorn-heal-self"></a> **Legendary option — Heal Self (3).** `srd51.creature.unicorn.heal-self` Regain 11 (2d8 + 2) HP magically.

[SRD p. 351, Unicorn](../../references/srd-5.1/pages/page-351.md) · [SRD p. 352, Unicorn continuation](../../references/srd-5.1/pages/page-352.md) · [SRD p. 134, Detect Evil and Good](../../references/srd-5.1/pages/page-134.md) · [SRD p. 138, Druidcraft](../../references/srd-5.1/pages/page-138.md) · [SRD p. 167, Pass without Trace](../../references/srd-5.1/pages/page-167.md) · [SRD p. 123, Calm Emotions](../../references/srd-5.1/pages/page-123.md) · [SRD p. 136, Dispel Evil and Good](../../references/srd-5.1/pages/page-136.md) · [SRD p. 140, Entangle](../../references/srd-5.1/pages/page-140.md)

<a id="srd51-creature-vampire"></a>

## Vampire

`srd51.creature.vampire`

- **Profile.** Medium undead (shapechanger), lawful evil. AC 16 (natural armor). HP 144 (17d8 + 68). Speed 30 ft. CR 13 (10,000 XP).
- **Ability scores.** Str 18 (+4), Dex 18 (+4), Con 18 (+4), Int 17 (+3), Wis 15 (+2), Cha 18 (+4).
- **Saves.** Dex +9, Wis +7, Cha +9.
- **Skills.** Perception +7, Stealth +9.
- **Resistances.** necrotic; bludgeoning, piercing, and slashing from nonmagical attacks.
- **Senses.** darkvision 120 ft, passive Perception 17.
- **Languages.** the languages it knew in life.

- <a id="srd51-creature-vampire-shapechanger"></a> **Trait — Shapechanger.** `srd51.creature.vampire.shapechanger` Outside sunlight and running water, use an action to become a Tiny bat or
  Medium mist, or return to true form. As a bat it cannot speak, walks 5 feet, and flies 30 feet. Other
  than size and speed its statistics remain. Worn gear transforms but carried gear does not. It returns
  to its true form on death. In mist it is weightless, flies 20 feet, hovers, can enter and stop in a
  hostile creature's space, and fits where air passes without squeezing. Water blocks mist. It cannot
  act, speak, or manipulate objects in mist form. It has advantage on Strength, Dexterity, and
  Constitution saves and immunity to nonmagical damage other than damage from sunlight.
  **Project interpretation.** Above 0 HP, the vampire may use this trait's reversion action in mist,
  subject to the ordinary sunlight and running-water restriction. This specific permission to return
  to true form supplies an exception to mist's action ban. A blanket ban would prevent voluntary
  reversion even though Shapechanger lists it. Misty Escape expressly prevents voluntary reversion
  at 0 HP.
- <a id="srd51-creature-vampire-legendary-resistance"></a> **Trait — Legendary Resistance.** `srd51.creature.vampire.legendary-resistance` [Legendary
  Resistance](creatures.md#srd51-creature-rules-legendary-resistance), three uses per day.
- <a id="srd51-creature-vampire-misty-escape"></a> **Trait — Misty Escape.** `srd51.creature.vampire.misty-escape` At 0 HP outside its resting place, become mist instead of falling
  unconscious if outside sunlight and running water. If it cannot become mist, it is destroyed. At 0 HP
  in mist it cannot revert voluntarily and is destroyed unless it reaches its resting place in 2 hours.
  On arrival return to vampire form, paralyzed while below 1 HP. After 1 hour there at 0 HP, regain 1
  HP.
- <a id="srd51-creature-vampire-regeneration"></a> **Trait — Regeneration.** `srd51.creature.vampire.regeneration` Regain 20 HP at turn start if at least 1 HP remains and it is in neither
  sunlight nor running water. Radiant or holy-water damage prevents regeneration at the next turn start.
- <a id="srd51-creature-vampire-spider-climb"></a> **Trait — Spider Climb.** `srd51.creature.vampire.spider-climb` Apply [Spider Climb](creatures.md#srd51-creature-rules-spider-climb).
- <a id="srd51-creature-vampire-vampire-weaknesses"></a> **Trait — Vampire Weaknesses.** `srd51.creature.vampire.vampire-weaknesses` Apply the [shared weaknesses](#srd51-creature-vampire-weaknesses). A
  qualifying wooden stake paralyzes this vampire until removed.
- <a id="srd51-creature-vampire-multiattack"></a> **Action — Multiattack.** `srd51.creature.vampire.multiattack` Vampire form only. Two attacks, at most one Bite.
- <a id="srd51-creature-vampire-unarmed-strike"></a> **Action — Unarmed Strike.** `srd51.creature.vampire.unarmed-strike` Vampire form only. Melee weapon attack, +9, reach 5 feet, one creature.
  On hit deal 8 (1d8 + 4) bludgeoning or grapple with escape DC 18 instead.
- <a id="srd51-creature-vampire-bite"></a> **Action — Bite.** `srd51.creature.vampire.bite` Vampire or bat form only. Melee weapon attack, +9, reach 5 feet, one creature that
  is willing, grappled by the vampire, incapacitated, or restrained. Hit deals 7 (1d6 + 4) piercing plus
  10 (3d6) necrotic. Lower maximum HP by the necrotic damage taken and heal the vampire by that same
  amount. The reduction lasts through a completed long rest. If maximum HP reaches 0 this way, the
  target dies. A humanoid killed this way and buried in the ground rises the following night as a
  controlled [Vampire Spawn](#srd51-creature-vampire-spawn).
- <a id="srd51-creature-vampire-charm"></a> **Action — Charm.** `srd51.creature.vampire.charm` Choose one humanoid it sees within 30 feet. A target that also sees the vampire
  saves Wisdom DC 17 against the magic. Failure charms it, treats the vampire as a trusted friend it
  should heed and protect, and makes it willing for Bite. The target interprets the vampire's requests
  and actions as favorably as it can while retaining its own control. Each harmful act by the vampire or
  its companions lets the target repeat the save and recover on success. Otherwise the effect ends after
  24 hours, on the vampire's destruction, on planar separation, or when the vampire ends it as a bonus
  action.
- <a id="srd51-creature-vampire-children-of-the-night"></a> **Action — Children of the Night.** `srd51.creature.vampire.children-of-the-night` Once per day while the sun is down, call 2d4 [Swarm of
  Bats](creatures-misc-h-z.md#srd51-creature-swarm-of-bats) or [Swarm of
  Rats](creatures-misc-h-z.md#srd51-creature-swarm-of-rats) groups of the chosen kind. Outdoors, it can
  call 3d6 [Wolf](creatures-misc-h-z.md#srd51-creature-wolf) instead. They arrive in 1d4 rounds as
  allies following spoken commands. They stay for 1 hour or leave earlier if the vampire dies or
  dismisses them as a bonus action.
- <a id="srd51-creature-vampire-legendary-actions"></a> **Legendary budget — Legendary Actions.** `srd51.creature.vampire.legendary-actions` Three, restored at its turn start. Use one option at the end of another
  creature's turn.
- <a id="srd51-creature-vampire-move"></a> **Legendary option — Move (1).** `srd51.creature.vampire.move` Move up to its speed without provoking opportunity attacks.
- <a id="srd51-creature-vampire-legendary-unarmed-strike"></a> **Legendary option — Unarmed Strike (1).** `srd51.creature.vampire.legendary-unarmed-strike` Use Unarmed Strike.
- <a id="srd51-creature-vampire-legendary-bite"></a> **Legendary option — Bite (2).** `srd51.creature.vampire.legendary-bite` Use Bite.

<a id="srd51-creature-vampire-weaknesses"></a>

### Vampire weaknesses

`srd51.creature.vampire-weaknesses`

A vampire or vampire spawn needs an occupant's invitation to enter a residence. It takes 20 acid damage
for ending its own turn in running water and 20 radiant damage for starting its own turn in sunlight. In sunlight it
has disadvantage on attack rolls and ability checks. Apply the profile's stake outcome if someone pierces its heart with a wooden piercing weapon
while the creature lies incapacitated in its resting place.

[SRD p. 353, Vampire Weaknesses](../../references/srd-5.1/pages/page-353.md) · [SRD p. 354, Vampire
Spawn](../../references/srd-5.1/pages/page-354.md).

[SRD p. 352, Vampire](../../references/srd-5.1/pages/page-352.md) · [SRD p. 353, Vampire continuation](../../references/srd-5.1/pages/page-353.md) · [SRD p. 354, Vampire Spawn](../../references/srd-5.1/pages/page-354.md) · [SRD p. 389, Swarm of Bats](../../references/srd-5.1/pages/page-389.md) · [SRD p. 390, Swarm of Rats](../../references/srd-5.1/pages/page-390.md) · [SRD p. 393, Wolf](../../references/srd-5.1/pages/page-393.md)

<a id="srd51-creature-vampire-spawn"></a>

## Vampire Spawn

`srd51.creature.vampire-spawn`

- **Profile.** Medium undead, neutral evil. AC 15 (natural armor). HP 82 (11d8 + 33). Speed 30 ft. CR 5 (1,800 XP).
- **Ability scores.** Str 16 (+3), Dex 16 (+3), Con 16 (+3), Int 11 (+0), Wis 10 (+0), Cha 12 (+1).
- **Saves.** Dex +6, Wis +3.
- **Skills.** Perception +3, Stealth +6.
- **Resistances.** necrotic; bludgeoning, piercing, and slashing from nonmagical attacks.
- **Senses.** darkvision 60 ft, passive Perception 13.
- **Languages.** the languages it knew in life.

- <a id="srd51-creature-vampire-spawn-regeneration"></a> **Trait — Regeneration.** `srd51.creature.vampire-spawn.regeneration` Regain 10 HP at turn start with at least 1 HP and in neither sunlight nor
  running water. Radiant or holy-water damage suppresses regeneration at the next turn start.
- <a id="srd51-creature-vampire-spawn-spider-climb"></a> **Trait — Spider Climb.** `srd51.creature.vampire-spawn.spider-climb` Apply [Spider Climb](creatures.md#srd51-creature-rules-spider-climb).
- <a id="srd51-creature-vampire-spawn-vampire-weaknesses"></a> **Trait — Vampire Weaknesses.** `srd51.creature.vampire-spawn.vampire-weaknesses` Apply the [shared weaknesses](#srd51-creature-vampire-weaknesses). A
  qualifying wooden stake destroys a vampire spawn.
- <a id="srd51-creature-vampire-spawn-multiattack"></a> **Action — Multiattack.** `srd51.creature.vampire-spawn.multiattack` Two attacks, at most one Bite.
- <a id="srd51-creature-vampire-spawn-claws"></a> **Action — Claws.** `srd51.creature.vampire-spawn.claws` Melee weapon attack, +6, reach 5 feet, one creature. On hit deal 8 (2d4 + 3)
  slashing or grapple with escape DC 13 instead.
- <a id="srd51-creature-vampire-spawn-bite"></a> **Action — Bite.** `srd51.creature.vampire-spawn.bite` Melee weapon attack, +6, reach 5 feet, one creature that is willing, grappled by
  the spawn, incapacitated, or restrained. Hit deals 6 (1d6 + 3) piercing plus 7 (2d6) necrotic. Lower
  maximum HP by the necrotic damage taken and heal the spawn by that amount. The reduction lasts through
  the target's next long rest. A maximum reduced to 0 this way kills the target.

[SRD p. 354, Vampire Spawn](../../references/srd-5.1/pages/page-354.md)

<a id="srd51-creature-wight"></a>

## Wight

`srd51.creature.wight`

- **Profile.** Medium undead, neutral evil. AC 14 (studded leather). HP 45 (6d8 + 18). Speed 30 ft. CR 3 (700 XP).
- **Ability scores.** Str 15 (+2), Dex 14 (+2), Con 16 (+3), Int 10 (+0), Wis 13 (+1), Cha 15 (+2).
- **Skills.** Perception +3, Stealth +4.
- **Resistances.** necrotic; bludgeoning, piercing, and slashing from nonmagical attacks that aren’t silvered.
- **Damage immunity.** poison.
- **Condition immunity.** exhaustion, poisoned.
- **Senses.** darkvision 60 ft, passive Perception 13.
- **Languages.** the languages it knew in life.

- <a id="srd51-creature-wight-sunlight-sensitivity"></a> **Trait — Sunlight Sensitivity.** `srd51.creature.wight.sunlight-sensitivity` Sunlight imposes disadvantage on attack rolls and sight-based Wisdom
  (Perception) checks.
- <a id="srd51-creature-wight-multiattack"></a> **Action — Multiattack.** `srd51.creature.wight.multiattack` Two Longsword attacks or two Longbow attacks. One Longsword attack can be
  replaced by Life Drain.
- <a id="srd51-creature-wight-life-drain"></a> **Action — Life Drain.** `srd51.creature.wight.life-drain` Melee weapon attack, +4, reach 5 feet, one creature. Hit deals 5 (1d6 + 2)
  necrotic. Failure on Constitution DC 13 reduces maximum HP by the damage taken through the victim's
  next completed long rest. A maximum reduced to 0 this way kills. A humanoid killed by this attack
  rises after 24 hours as a [Zombie](#srd51-creature-zombie) controlled by the wight unless restored to
  life or its body is destroyed. The wight cannot control more than twelve zombies at once.
  **Project interpretation.** If it is already at the cap when a victim rises, the new zombie rises
  uncontrolled and existing control persists. The rise is automatic, and the separate cap limits
  control. The source supplies no replacement or suppression instruction.
- <a id="srd51-creature-wight-longsword"></a> **Action — Longsword.** `srd51.creature.wight.longsword` Melee weapon attack, +4, reach 5 feet, one target. Hit deals 6 (1d8 + 2)
  slashing or 7 (1d10 + 2) with two hands.
- <a id="srd51-creature-wight-longbow"></a> **Action — Longbow.** `srd51.creature.wight.longbow` Ranged weapon attack, +4, range 150/600 feet, one target. Hit deals 6 (1d8 + 2)
  piercing.

[SRD p. 354, Wight](../../references/srd-5.1/pages/page-354.md) · [SRD p. 355, Wight continuation](../../references/srd-5.1/pages/page-355.md) · [SRD p. 356, Zombie](../../references/srd-5.1/pages/page-356.md)

<a id="srd51-creature-will-o-wisp"></a>

## Will-o’-Wisp

`srd51.creature.will-o-wisp`

- **Profile.** Tiny undead, chaotic evil. AC 19. HP 22 (9d4). Speed 0 ft, fly 50 ft (hover). CR 2 (450 XP).
- **Ability scores.** Str 1 (−5), Dex 28 (+9), Con 10 (+0), Int 13 (+1), Wis 14 (+2), Cha 11 (+0).
- **Resistances.** acid, cold, fire, necrotic, thunder; bludgeoning, piercing, and slashing from nonmagical attacks.
- **Damage immunity.** lightning, poison.
- **Condition immunity.** exhaustion, grappled, paralyzed, poisoned, prone, restrained, unconscious.
- **Senses.** darkvision 120 ft, passive Perception 12.
- **Languages.** the languages it knew in life.

- <a id="srd51-creature-will-o-wisp-consume-life"></a> **Trait — Consume Life.** `srd51.creature.will-o-wisp.consume-life` As a bonus action choose one visible living creature at 0 HP within 5 feet.
  It saves Constitution DC 10 against the magic or dies. If it dies, the wisp regains 10 (3d6) HP.
- <a id="srd51-creature-will-o-wisp-ephemeral"></a> **Trait — Ephemeral.** `srd51.creature.will-o-wisp.ephemeral` It cannot wear or carry anything.
- <a id="srd51-creature-will-o-wisp-incorporeal-movement"></a> **Trait — Incorporeal Movement.** `srd51.creature.will-o-wisp.incorporeal-movement` It can pass through creatures and objects as difficult terrain. It
  takes 5 (1d10) force damage for ending a turn inside an object.
- <a id="srd51-creature-will-o-wisp-variable-illumination"></a> **Trait — Variable Illumination.** `srd51.creature.will-o-wisp.variable-illumination` Set a bright-light radius of 5 to 20 feet, with the same additional
  reach of dim light. Change the radius as a bonus action.
- <a id="srd51-creature-will-o-wisp-shock"></a> **Action — Shock.** `srd51.creature.will-o-wisp.shock` Melee spell attack, +4, reach 5 feet, one creature. Hit deals 9 (2d8) lightning.
- <a id="srd51-creature-will-o-wisp-invisibility"></a> **Action — Invisibility.** `srd51.creature.will-o-wisp.invisibility` Magically make the wisp and its light invisible until it attacks, uses
  Consume Life, or loses concentration as on a spell.

[SRD p. 355, Will-o’-Wisp](../../references/srd-5.1/pages/page-355.md)

<a id="srd51-creature-wraith"></a>

## Wraith

`srd51.creature.wraith`

- **Profile.** Medium undead, neutral evil. AC 13. HP 67 (9d8 + 27). Speed 0 ft, fly 60 ft (hover). CR 5 (1,800 XP).
- **Ability scores.** Str 6 (−2), Dex 16 (+3), Con 16 (+3), Int 12 (+1), Wis 14 (+2), Cha 15 (+2).
- **Resistances.** acid, cold, fire, lightning, thunder; bludgeoning, piercing, and slashing from nonmagical attacks that aren’t silvered.
- **Damage immunity.** necrotic, poison.
- **Condition immunity.** charmed, exhaustion, grappled, paralyzed, petrified, poisoned, prone, restrained.
- **Senses.** darkvision 60 ft, passive Perception 12.
- **Languages.** the languages it knew in life.

- <a id="srd51-creature-wraith-incorporeal-movement"></a> **Trait — Incorporeal Movement.** `srd51.creature.wraith.incorporeal-movement` Pass through creatures and objects as difficult terrain. Ending a
  turn inside an object deals it 5 (1d10) force.
- <a id="srd51-creature-wraith-sunlight-sensitivity"></a> **Trait — Sunlight Sensitivity.** `srd51.creature.wraith.sunlight-sensitivity` Sunlight imposes disadvantage on attack rolls and sight-based Wisdom
  (Perception) checks.
- <a id="srd51-creature-wraith-life-drain"></a> **Action — Life Drain.** `srd51.creature.wraith.life-drain` Melee weapon attack, +6, reach 5 feet, one creature. Hit deals 21 (4d8 + 3)
  necrotic. Failure on Constitution DC 14 lowers maximum HP by damage taken through the target's next
  completed long rest. A maximum reduced to 0 this way kills.
- <a id="srd51-creature-wraith-create-specter"></a> **Action — Create Specter.** `srd51.creature.wraith.create-specter` Choose a humanoid within 10 feet killed violently at most 1 minute ago.
  Its spirit becomes a controlled [Specter](#srd51-creature-specter) in the corpse's space or the
  nearest unoccupied space. The wraith can control up to seven specters.
  **Project interpretation.** At the cap, the wraith cannot use Create Specter until capacity opens.
  This optional action creates a controlled specter and supplies no uncontrolled creation or
  automatic replacement branch.

[SRD p. 355, Wraith](../../references/srd-5.1/pages/page-355.md) · [SRD p. 356, Wraith continuation](../../references/srd-5.1/pages/page-356.md) · [SRD p. 346, Specter](../../references/srd-5.1/pages/page-346.md)

<a id="srd51-creature-wyvern"></a>

## Wyvern

`srd51.creature.wyvern`

- **Profile.** Large dragon, unaligned. AC 13 (natural armor). HP 110 (13d10 + 39). Speed 20 ft, fly 80 ft. CR 6 (2,300 XP).
- **Ability scores.** Str 19 (+4), Dex 10 (+0), Con 16 (+3), Int 5 (−3), Wis 12 (+1), Cha 6 (−2).
- **Skills.** Perception +4.
- **Senses.** darkvision 60 ft, passive Perception 14.
- **Languages.** None.

- <a id="srd51-creature-wyvern-multiattack"></a> **Action — Multiattack.** `srd51.creature.wyvern.multiattack` One Bite and one Stinger. While flying, Claws can replace one of them.
- <a id="srd51-creature-wyvern-bite"></a> **Action — Bite.** `srd51.creature.wyvern.bite` Melee weapon attack, +7, reach 10 feet, one creature. Hit deals 11 (2d6 + 4)
  piercing.
- <a id="srd51-creature-wyvern-claws"></a> **Action — Claws.** `srd51.creature.wyvern.claws` Melee weapon attack, +7, reach 5 feet, one target. Hit deals 13 (2d8 + 4)
  slashing.
- <a id="srd51-creature-wyvern-stinger"></a> **Action — Stinger.** `srd51.creature.wyvern.stinger` Melee weapon attack, +7, reach 10 feet, one creature. Hit deals 11 (2d6 + 4)
  piercing and calls for Constitution DC 15 against 24 (7d6) poison, half on success.

[SRD p. 356, Wyvern](../../references/srd-5.1/pages/page-356.md)

<a id="srd51-creature-xorn"></a>

## Xorn

`srd51.creature.xorn`

- **Profile.** Medium elemental, neutral. AC 19 (natural armor). HP 73 (7d8 + 42). Speed 20 ft, burrow 20 ft. CR 5 (1,800 XP).
- **Ability scores.** Str 17 (+3), Dex 10 (+0), Con 22 (+6), Int 11 (+0), Wis 10 (+0), Cha 11 (+0).
- **Skills.** Perception +6, Stealth +3.
- **Resistances.** piercing and slashing from nonmagical attacks that aren’t adamantine.
- **Senses.** darkvision 60 ft, tremorsense 60 ft, passive Perception 16.
- **Languages.** Terran.

- <a id="srd51-creature-xorn-earth-glide"></a> **Trait — Earth Glide.** `srd51.creature.xorn.earth-glide` Burrow through nonmagical unworked earth and stone without disturbing it.
- <a id="srd51-creature-xorn-stone-camouflage"></a> **Trait — Stone Camouflage.** `srd51.creature.xorn.stone-camouflage` Dexterity (Stealth) checks to hide in rocky terrain have advantage.
- <a id="srd51-creature-xorn-treasure-sense"></a> **Trait — Treasure Sense.** `srd51.creature.xorn.treasure-sense` Pinpoint precious metals or stones such as gems and coins within 60 feet
  by scent.
- <a id="srd51-creature-xorn-multiattack"></a> **Action — Multiattack.** `srd51.creature.xorn.multiattack` Three Claw attacks and one Bite.
- <a id="srd51-creature-xorn-claw"></a> **Action — Claw.** `srd51.creature.xorn.claw` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 6 (1d6 + 3) slashing.
- <a id="srd51-creature-xorn-bite"></a> **Action — Bite.** `srd51.creature.xorn.bite` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 13 (3d6 + 3) piercing.

[SRD p. 356, Xorn](../../references/srd-5.1/pages/page-356.md)

<a id="srd51-creature-zombie"></a>

## Zombie

`srd51.creature.zombie`

- **Profile.** Medium undead, neutral evil. AC 8. HP 22 (3d8 + 9). Speed 20 ft. CR 1/4 (50 XP).
- **Ability scores.** Str 13 (+1), Dex 6 (−2), Con 16 (+3), Int 3 (−4), Wis 6 (−2), Cha 5 (−3).
- **Saves.** Wis +0.
- **Damage immunity.** poison.
- **Condition immunity.** poisoned.
- **Senses.** darkvision 60 ft, passive Perception 8.
- **Languages.** understands the languages it knew in life but can’t speak.

- <a id="srd51-creature-zombie-undead-fortitude"></a> **Trait — Undead Fortitude.** `srd51.creature.zombie.undead-fortitude` [Undead Fortitude](#srd51-creature-undead-fortitude).
- <a id="srd51-creature-zombie-slam"></a> **Action — Slam.** `srd51.creature.zombie.slam` Melee weapon attack, +3, reach 5 feet, one target. Hit deals 4 (1d6 + 1)
  bludgeoning.

<a id="srd51-creature-undead-fortitude"></a>

### Undead Fortitude

`srd51.creature.undead-fortitude`

When damage would reduce the zombie or ogre zombie to 0 HP, it makes a Constitution save with DC 5 plus
the damage taken. Success leaves it at 1 HP instead. Radiant damage or damage from a critical hit
bypasses this save.

[SRD p. 356, Zombie](../../references/srd-5.1/pages/page-356.md) · [SRD p. 357, Zombie continuation and
Ogre Zombie](../../references/srd-5.1/pages/page-357.md).

[SRD p. 356, Zombie](../../references/srd-5.1/pages/page-356.md) · [SRD p. 357, Zombie continuation](../../references/srd-5.1/pages/page-357.md) · [SRD p. 98, Monsters and Death](../../references/srd-5.1/pages/page-098.md)

<a id="srd51-creature-ogre-zombie"></a>

## Ogre Zombie

`srd51.creature.ogre-zombie`

- **Profile.** Large undead, neutral evil. AC 8. HP 85 (9d10 + 36). Speed 30 ft. CR 2 (450 XP).
- **Ability scores.** Str 19 (+4), Dex 6 (−2), Con 18 (+4), Int 3 (−4), Wis 6 (−2), Cha 5 (−3).
- **Saves.** Wis +0.
- **Damage immunity.** poison.
- **Condition immunity.** poisoned.
- **Senses.** darkvision 60 ft, passive Perception 8.
- **Languages.** understands Common and Giant but can’t speak.

- <a id="srd51-creature-ogre-zombie-undead-fortitude"></a> **Trait — Undead Fortitude.** `srd51.creature.ogre-zombie.undead-fortitude` [Undead Fortitude](#srd51-creature-undead-fortitude).
- <a id="srd51-creature-ogre-zombie-morningstar"></a> **Action — Morningstar.** `srd51.creature.ogre-zombie.morningstar` Melee weapon attack, +6, reach 5 feet, one target. Hit deals 13 (2d8 + 4)
  piercing.

[SRD p. 357, Ogre Zombie](../../references/srd-5.1/pages/page-357.md) · [SRD p. 98, Monsters and Death](../../references/srd-5.1/pages/page-098.md)

## Shared profile mechanics

<a id="srd51-creature-rules-amorphous"></a>

### Amorphous

`srd51.creature-rules.amorphous`

A creature with this trait moves through an opening at least 1 inch wide without the usual squeezing
costs or penalties. [SRD p. 337, Black Pudding](../../references/srd-5.1/pages/page-337.md) · [SRD p. 338, Gray Ooze](../../references/srd-5.1/pages/page-338.md) · [SRD p. 339, Ochre Jelly continuation](../../references/srd-5.1/pages/page-339.md) · [SRD p. 344, Shadow](../../references/srd-5.1/pages/page-344.md)

<a id="srd51-creature-rules-corrosion"></a>

### Corrosion

`srd51.creature-rules.corrosion`

An eligible weapon that hits applies its ordinary damage first. Then apply a permanent, cumulative
−1 to its damage rolls. Destroy it at −5. An eligible piece of ammunition is destroyed after its hit
deals damage. The triggering profile specifies weapon and ammunition materials and can also provide
a corrosion trigger without a weapon hit. When the profile corrodes eligible armor, permanently
reduce the AC that armor provides by 1 each time and destroy it if that AC reaches 10.
[SRD p. 337, Black Pudding](../../references/srd-5.1/pages/page-337.md) · [SRD p. 338, Gray Ooze](../../references/srd-5.1/pages/page-338.md) · [SRD p. 343, Rust Monster](../../references/srd-5.1/pages/page-343.md)

<a id="srd51-creature-rules-ooze-splitting"></a>

### Ooze splitting

`srd51.creature-rules.ooze-splitting`

As a reaction when subjected to lightning or slashing damage, an eligible ooze of at least Medium
size and with at least 10 current HP splits into two of its kind. Each is one size smaller and has half the original's current HP rounded down. **Project
interpretation.** Use that result as both maximum and starting current HP. The entry assigns the
new creatures their hit point value, so treating it as only current HP would let them heal up to
an unrelated maximum from the original profile. Check whether a positive
amount of the triggering damage type would reach the ooze before its immunity. The two profiles are
immune to both types and say "subjected to," so reading this as a requirement to take positive damage
after immunity would disable the reaction. Immunity still prevents HP loss. An effect that would
[already deal 0](combat.md#srd51-core-damage-roll) to the ooze before immunity supplies no trigger. [SRD p. 96, Damage Rolls](../../references/srd-5.1/pages/page-096.md) · [SRD p. 337, Black Pudding](../../references/srd-5.1/pages/page-337.md) · [SRD p. 338, Ochre Jelly](../../references/srd-5.1/pages/page-338.md) · [SRD p. 339, Ochre Jelly continuation](../../references/srd-5.1/pages/page-339.md)

<a id="srd51-creature-rules-naga-rejuvenation"></a>

### Naga rejuvenation

`srd51.creature-rules.naga-rejuvenation`

On death, schedule the naga to return alive at full HP after 1d6 days. Only
[Wish](spells-t-z.md#srd51-spell-wish) can prevent this feature from returning it to life.
[SRD p. 335, Guardian Naga and Spirit Naga](../../references/srd-5.1/pages/page-335.md) · [SRD p. 193, Wish](../../references/srd-5.1/pages/page-193.md)

## Examples

- A creature that is not surprised can avert its eyes at turn start rather than risk Medusa's save. If it looks before its next turn, the save is immediate. A total of 9 against DC 14 petrifies, while 10 restrains and starts the repeat-save phase. [SRD p. 330, Medusa](../../references/srd-5.1/pages/page-330.md).
- A mummy's glare does not affect a target that cannot see it. Successful resistance to that glare does not protect against a mummy lord's glare. [SRD p. 334, Mummy and Mummy Lord](../../references/srd-5.1/pages/page-334.md).
- A creature entering an occupied Gelatinous Cube space makes the Engulf save at disadvantage. A rescuer within 5 feet uses an action and DC 12 Strength check, taking acid damage for the attempt. [SRD p. 337, Gelatinous Cube](../../references/srd-5.1/pages/page-337.md) · [SRD p. 338, Gelatinous Cube continuation](../../references/srd-5.1/pages/page-338.md).
- A Medium Black Pudding at 11 HP that has its reaction can split after a slashing hit despite its immunity. It takes no slashing damage and becomes two Small puddings with 5 current and maximum HP each. At 9 HP the reaction is unavailable. [SRD p. 337, Black Pudding](../../references/srd-5.1/pages/page-337.md)
- A tarrasque roll of 5 against Magic Missile cancels its assigned darts. A 6 instead redirects them to the caster. A dart already assigned to a third creature remains aimed there in both cases. [SRD p. 350, Tarrasque and Reflective Carapace](../../references/srd-5.1/pages/page-350.md) · [SRD p. 161, Magic Missile](../../references/srd-5.1/pages/page-161.md)
- A vampire reduced to 0 HP outside its resting place in direct sunlight is destroyed because it cannot become mist. A wooden piercing weapon driven into the incapacitated vampire's heart at its resting place instead paralyzes it while the weapon remains. The same stake condition destroys a vampire spawn. [SRD p. 353, Vampire and Misty Escape](../../references/srd-5.1/pages/page-353.md) · [SRD p. 354, Vampire Spawn](../../references/srd-5.1/pages/page-354.md).
- A zombie reduced to 0 HP by 7 nonradiant damage from a noncritical hit makes a DC 12 Constitution save. On success it stays at 1 HP. A critical or radiant hit reducing it to 0 does not permit that save. [SRD p. 356, Zombie](../../references/srd-5.1/pages/page-356.md) · [SRD p. 357, Zombie continuation](../../references/srd-5.1/pages/page-357.md).

## Source coverage

The Manticore continuation at the start of p. 330 belongs to the preceding band. Group labels
such as Mephits, Mummies, Nagas, Oozes, Skeletons, Sphinxes, Vampires, and Zombies organize the
stat blocks. These pages give vampire and vampire spawn profiles. They do not include
a player-character vampire rule, vampire class variants, or lair or regional effects for the
profiles listed here.

PDF layout checks cover the Medusa and mephit entries, the Mummy Lord's spells and legendary
options, the split Gelatinous Cube, the sphinxes' spell lists and legendary actions, and vampire
and vampire spawn across the p. 352–354 boundary.

| Page | Source entries and destination IDs |
| --- | --- |
| [330](../../references/srd-5.1/pages/page-330.md) | Medusa → [`srd51.creature.medusa`](#srd51-creature-medusa)<br>Dust Mephit → [`srd51.creature.dust-mephit`](#srd51-creature-dust-mephit) |
| [331](../../references/srd-5.1/pages/page-331.md) | Dust Mephit continuation → [`srd51.creature.dust-mephit`](#srd51-creature-dust-mephit)<br>Ice Mephit → [`srd51.creature.ice-mephit`](#srd51-creature-ice-mephit)<br>Magma Mephit → [`srd51.creature.magma-mephit`](#srd51-creature-magma-mephit)<br>Steam Mephit → [`srd51.creature.steam-mephit`](#srd51-creature-steam-mephit) |
| [332](../../references/srd-5.1/pages/page-332.md) | Steam Mephit continuation → [`srd51.creature.steam-mephit`](#srd51-creature-steam-mephit)<br>Merfolk → [`srd51.creature.merfolk`](#srd51-creature-merfolk)<br>Merrow → [`srd51.creature.merrow`](#srd51-creature-merrow)<br>Mimic → [`srd51.creature.mimic`](#srd51-creature-mimic) |
| [333](../../references/srd-5.1/pages/page-333.md) | Mimic continuation → [`srd51.creature.mimic`](#srd51-creature-mimic)<br>Minotaur → [`srd51.creature.minotaur`](#srd51-creature-minotaur)<br>Mummy → [`srd51.creature.mummy`](#srd51-creature-mummy) |
| [334](../../references/srd-5.1/pages/page-334.md) | Mummy continuation → [`srd51.creature.mummy`](#srd51-creature-mummy)<br>Mummy Lord → [`srd51.creature.mummy-lord`](#srd51-creature-mummy-lord) |
| [335](../../references/srd-5.1/pages/page-335.md) | Mummy Lord continuation → [`srd51.creature.mummy-lord`](#srd51-creature-mummy-lord)<br>Guardian Naga → [`srd51.creature.guardian-naga`](#srd51-creature-guardian-naga)<br>Spirit Naga → [`srd51.creature.spirit-naga`](#srd51-creature-spirit-naga) |
| [336](../../references/srd-5.1/pages/page-336.md) | Spirit Naga continuation → [`srd51.creature.spirit-naga`](#srd51-creature-spirit-naga)<br>Nightmare → [`srd51.creature.nightmare`](#srd51-creature-nightmare)<br>Ogre → [`srd51.creature.ogre`](#srd51-creature-ogre)<br>Oni → [`srd51.creature.oni`](#srd51-creature-oni) |
| [337](../../references/srd-5.1/pages/page-337.md) | Oni continuation → [`srd51.creature.oni`](#srd51-creature-oni)<br>Black Pudding → [`srd51.creature.black-pudding`](#srd51-creature-black-pudding)<br>Gelatinous Cube → [`srd51.creature.gelatinous-cube`](#srd51-creature-gelatinous-cube) |
| [338](../../references/srd-5.1/pages/page-338.md) | Gelatinous Cube continuation → [`srd51.creature.gelatinous-cube`](#srd51-creature-gelatinous-cube)<br>Gray Ooze → [`srd51.creature.gray-ooze`](#srd51-creature-gray-ooze)<br>Ochre Jelly → [`srd51.creature.ochre-jelly`](#srd51-creature-ochre-jelly) |
| [339](../../references/srd-5.1/pages/page-339.md) | Ochre Jelly continuation → [`srd51.creature.ochre-jelly`](#srd51-creature-ochre-jelly)<br>Orc → [`srd51.creature.orc`](#srd51-creature-orc)<br>Otyugh → [`srd51.creature.otyugh`](#srd51-creature-otyugh)<br>Owlbear → [`srd51.creature.owlbear`](#srd51-creature-owlbear) |
| [340](../../references/srd-5.1/pages/page-340.md) | Owlbear continuation → [`srd51.creature.owlbear`](#srd51-creature-owlbear)<br>Pegasus → [`srd51.creature.pegasus`](#srd51-creature-pegasus)<br>Pseudodragon → [`srd51.creature.pseudodragon`](#srd51-creature-pseudodragon)<br>Purple Worm → [`srd51.creature.purple-worm`](#srd51-creature-purple-worm) |
| [341](../../references/srd-5.1/pages/page-341.md) | Purple Worm continuation → [`srd51.creature.purple-worm`](#srd51-creature-purple-worm)<br>Rakshasa → [`srd51.creature.rakshasa`](#srd51-creature-rakshasa)<br>Remorhaz → [`srd51.creature.remorhaz`](#srd51-creature-remorhaz) |
| [342](../../references/srd-5.1/pages/page-342.md) | Remorhaz continuation → [`srd51.creature.remorhaz`](#srd51-creature-remorhaz)<br>Roc → [`srd51.creature.roc`](#srd51-creature-roc)<br>Roper → [`srd51.creature.roper`](#srd51-creature-roper) |
| [343](../../references/srd-5.1/pages/page-343.md) | Roper continuation → [`srd51.creature.roper`](#srd51-creature-roper)<br>Rust Monster → [`srd51.creature.rust-monster`](#srd51-creature-rust-monster)<br>Sahuagin → [`srd51.creature.sahuagin`](#srd51-creature-sahuagin) |
| [344](../../references/srd-5.1/pages/page-344.md) | Salamander → [`srd51.creature.salamander`](#srd51-creature-salamander)<br>Satyr → [`srd51.creature.satyr`](#srd51-creature-satyr)<br>Shadow → [`srd51.creature.shadow`](#srd51-creature-shadow) |
| [345](../../references/srd-5.1/pages/page-345.md) | Shadow continuation → [`srd51.creature.shadow`](#srd51-creature-shadow)<br>Shambling Mound → [`srd51.creature.shambling-mound`](#srd51-creature-shambling-mound)<br>Shield Guardian → [`srd51.creature.shield-guardian`](#srd51-creature-shield-guardian) |
| [346](../../references/srd-5.1/pages/page-346.md) | Shield Guardian continuation → [`srd51.creature.shield-guardian`](#srd51-creature-shield-guardian)<br>Skeleton → [`srd51.creature.skeleton`](#srd51-creature-skeleton)<br>Minotaur Skeleton → [`srd51.creature.minotaur-skeleton`](#srd51-creature-minotaur-skeleton)<br>Warhorse Skeleton → [`srd51.creature.warhorse-skeleton`](#srd51-creature-warhorse-skeleton)<br>Specter → [`srd51.creature.specter`](#srd51-creature-specter) |
| [347](../../references/srd-5.1/pages/page-347.md) | Specter continuation → [`srd51.creature.specter`](#srd51-creature-specter)<br>Androsphinx → [`srd51.creature.androsphinx`](#srd51-creature-androsphinx) |
| [348](../../references/srd-5.1/pages/page-348.md) | Androsphinx continuation → [`srd51.creature.androsphinx`](#srd51-creature-androsphinx)<br>Gynosphinx → [`srd51.creature.gynosphinx`](#srd51-creature-gynosphinx)<br>Sprite → [`srd51.creature.sprite`](#srd51-creature-sprite) |
| [349](../../references/srd-5.1/pages/page-349.md) | Sprite continuation → [`srd51.creature.sprite`](#srd51-creature-sprite)<br>Stirge → [`srd51.creature.stirge`](#srd51-creature-stirge)<br>Succubus/Incubus → [`srd51.creature.succubus-incubus`](#srd51-creature-succubus-incubus) |
| [350](../../references/srd-5.1/pages/page-350.md) | Succubus/Incubus continuation → [`srd51.creature.succubus-incubus`](#srd51-creature-succubus-incubus)<br>Tarrasque → [`srd51.creature.tarrasque`](#srd51-creature-tarrasque) |
| [351](../../references/srd-5.1/pages/page-351.md) | Tarrasque continuation → [`srd51.creature.tarrasque`](#srd51-creature-tarrasque)<br>Treant → [`srd51.creature.treant`](#srd51-creature-treant)<br>Troll → [`srd51.creature.troll`](#srd51-creature-troll)<br>Unicorn → [`srd51.creature.unicorn`](#srd51-creature-unicorn) |
| [352](../../references/srd-5.1/pages/page-352.md) | Unicorn continuation → [`srd51.creature.unicorn`](#srd51-creature-unicorn)<br>Vampire → [`srd51.creature.vampire`](#srd51-creature-vampire) |
| [353](../../references/srd-5.1/pages/page-353.md) | Vampire continuation → [`srd51.creature.vampire`](#srd51-creature-vampire) |
| [354](../../references/srd-5.1/pages/page-354.md) | Vampire Spawn → [`srd51.creature.vampire-spawn`](#srd51-creature-vampire-spawn)<br>Wight → [`srd51.creature.wight`](#srd51-creature-wight) |
| [355](../../references/srd-5.1/pages/page-355.md) | Wight continuation → [`srd51.creature.wight`](#srd51-creature-wight)<br>Will-o’-Wisp → [`srd51.creature.will-o-wisp`](#srd51-creature-will-o-wisp)<br>Wraith → [`srd51.creature.wraith`](#srd51-creature-wraith) |
| [356](../../references/srd-5.1/pages/page-356.md) | Wraith continuation → [`srd51.creature.wraith`](#srd51-creature-wraith)<br>Wyvern → [`srd51.creature.wyvern`](#srd51-creature-wyvern)<br>Xorn → [`srd51.creature.xorn`](#srd51-creature-xorn)<br>Zombie → [`srd51.creature.zombie`](#srd51-creature-zombie) |
| [357](../../references/srd-5.1/pages/page-357.md) | Zombie continuation → [`srd51.creature.zombie`](#srd51-creature-zombie)<br>Ogre Zombie → [`srd51.creature.ogre-zombie`](#srd51-creature-ogre-zombie) |
