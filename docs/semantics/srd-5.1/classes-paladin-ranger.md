# Paladin and Ranger

Feature acquisition uses levels in the class that grants the feature.
[Proficiency bonus](abilities.md#srd51-core-proficiency-bonus) uses total character level.

<a id="srd51-class-paladin"></a>

## Paladin

`srd51.class.paladin`

Each paladin level grants one d10 Hit Die. At character level 1, maximum hit points are 10 + Constitution modifier. Each later paladin level adds 1d10 or a fixed 6, plus Constitution modifier. Starting proficiencies are all armor, shields, simple weapons, martial weapons, Wisdom saves, and Charisma saves. There are no starting tool proficiencies. Choose two skills from Athletics, Insight, Intimidation, Medicine, Persuasion, and Religion. [SRD p. 30, Paladin Class Features](../../references/srd-5.1/pages/page-030.md)

In addition to background equipment, choose a martial weapon and shield or two martial weapons, five javelins or any simple melee weapon, and a priest's pack or an explorer's pack. Also receive chain mail and a holy symbol. [SRD p. 30, Paladin Equipment](../../references/srd-5.1/pages/page-030.md)

<a id="srd51-class-paladin-divine-sense"></a>

### Divine Sense

`srd51.class.paladin.divine-sense`

From level 1, use an action to open Divine Sense through the end of your next turn. Within 60 feet you learn the location and creature type of each celestial, fiend, and undead that is outside total cover. It does not reveal their identities. The sense also detects the presence of consecrated or desecrated places and objects within 60 feet, such as those affected by [Hallow](spells-h-m.md#srd51-spell-hallow). You have 1 + Charisma modifier uses, restored on finishing a long rest. [SRD p. 30, Divine Sense](../../references/srd-5.1/pages/page-030.md)

**Derived conclusion.** The number of available uses is max(0, 1 + Charisma modifier). The formula has no minimum-one exception, and a zero or negative result permits no activations. [SRD p. 30, Divine Sense](../../references/srd-5.1/pages/page-030.md)

<a id="srd51-class-paladin-lay-on-hands"></a>

### Lay on Hands

`srd51.class.paladin.lay-on-hands`

From level 1, your healing pool holds 5 × paladin level points and refills on completing a long rest. As an action touch one creature and spend up to the points remaining to restore that many hit points. Alternatively, spend 5 points per disease cured or poison neutralized on the target, treating several with a single action if the pool can pay for each. Undead and constructs are unaffected. [SRD p. 31, Lay on Hands](../../references/srd-5.1/pages/page-031.md)

<a id="srd51-class-paladin-fighting-style"></a>

### Fighting Style

`srd51.class.paladin.fighting-style`

At level 2 choose one of [Defense](classes-fighter-monk.md#srd51-class-fighter-defense), [Dueling](classes-fighter-monk.md#srd51-class-fighter-dueling), [Great Weapon Fighting](classes-fighter-monk.md#srd51-class-fighter-great-weapon-fighting), and [Protection](classes-fighter-monk.md#srd51-class-fighter-protection). A style cannot be selected twice even if you receive another choice. [SRD p. 31, Fighting Style](../../references/srd-5.1/pages/page-031.md)

<a id="srd51-class-paladin-spellcasting"></a>

### Spellcasting

`srd51.class.paladin.spellcasting`

From level 2, prepare paladin spells in a number equal to Charisma modifier + floor(paladin level ÷ 2), minimum one. Each must be on the [paladin list](spell-lists.md#srd51-spell-list-paladin) and of a level for which you have slots. Use the [paladin and ranger slot progression](#srd51-class-paladin-ranger-spell-slot-progression). Casting a prepared leveled spell with a slot spends a slot of its level or higher and leaves it prepared. Completing a long rest restores all spent slots and permits changing the prepared list. A new list requires prayer and meditation for at least 1 minute per spell level for each listed spell. Oath spells are always prepared separately. [SRD p. 30, The Paladin](../../references/srd-5.1/pages/page-030.md) [SRD p. 31, Preparing and Casting Spells](../../references/srd-5.1/pages/page-031.md) [SRD p. 32, Oath Spells](../../references/srd-5.1/pages/page-032.md)

Charisma is the spellcasting ability. Save DC is 8 + proficiency bonus + Charisma modifier. Spell attack modifier is proficiency bonus + Charisma modifier. A holy symbol can serve as a focus for paladin spells. [SRD p. 31, Spellcasting Ability and Spellcasting Focus](../../references/srd-5.1/pages/page-031.md)

<a id="srd51-class-paladin-divine-smite"></a>

### Divine Smite

`srd51.class.paladin.divine-smite`

From level 2, after hitting a creature with a melee weapon attack you may spend one spell slot to add radiant damage. With a slot of level L, the number of d8s is min(L + 1, 5). Add one more d8 if the creature is undead or a fiend. This is damage in addition to the weapon's damage. [SRD p. 31, Divine Smite](../../references/srd-5.1/pages/page-031.md) [SRD p. 32, Divine Smite continuation](../../references/srd-5.1/pages/page-032.md)

<a id="srd51-class-paladin-divine-health"></a>

### Divine Health

`srd51.class.paladin.divine-health`

From level 3, you are immune to disease. [SRD p. 32, Divine Health](../../references/srd-5.1/pages/page-032.md)

<a id="srd51-class-paladin-sacred-oath"></a>

### Sacred Oath

`srd51.class.paladin.sacred-oath`

At level 3, choose and swear the oath binding you as a paladin. Levels before 3 are preparatory. The oath grants features at levels 3, 7, 15, and 20. The SRD details [Oath of Devotion](#srd51-class-paladin-oath-of-devotion). Each oath spell gained at its listed class level is always prepared, takes no place in the normal preparation allowance, and counts as a paladin spell for you even when absent from the paladin list. [SRD p. 32, Sacred Oath and Oath Spells](../../references/srd-5.1/pages/page-032.md) [SRD p. 33, Oath of Devotion Spells](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-channel-divinity"></a>

#### Channel Divinity

`srd51.class.paladin.channel-divinity`

At level 3, your oath grants options for Channel Divinity. Choose one such option when you expend your use. Finish a short or long rest before another use. A paladin Channel Divinity effect that calls for a saving throw uses your paladin spell save DC. [SRD p. 32, Channel Divinity](../../references/srd-5.1/pages/page-032.md) [SRD p. 33, Oath of Devotion Channel Divinity](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-ability-score-improvement"></a>

### Ability Score Improvement

`srd51.class.paladin.ability-score-improvement`

At levels 4, 8, 12, 16, and 19, apply an [Ability Score Improvement](character-advancement.md#srd51-character-ability-score-improvement). [SRD p. 32, Ability Score Improvement](../../references/srd-5.1/pages/page-032.md)

<a id="srd51-class-paladin-extra-attack"></a>

### Extra Attack

`srd51.class.paladin.extra-attack`

From level 5, take up to two attacks whenever you take the Attack action on your turn. [SRD p. 32, Extra Attack](../../references/srd-5.1/pages/page-032.md)

<a id="srd51-class-paladin-aura-of-protection"></a>

### Aura of Protection

`srd51.class.paladin.aura-of-protection`

From level 6, while you are conscious, you and friendly creatures within 10 feet of you add your Charisma modifier (minimum +1) to saving throws. At level 18 the radius becomes 30 feet. [SRD p. 32, Aura of Protection](../../references/srd-5.1/pages/page-032.md)

<a id="srd51-class-paladin-aura-of-courage"></a>

### Aura of Courage

`srd51.class.paladin.aura-of-courage`

From level 10, while you are conscious, you and friendly creatures within 10 feet of you cannot be frightened. At level 18 the radius becomes 30 feet. [SRD p. 32, Aura of Courage](../../references/srd-5.1/pages/page-032.md)

**Project interpretation.** For both Aura of Courage and Aura of Devotion, protection prevents a new frightened or charmed condition of the protected kind and suppresses an existing one while the aura protects the creature. An existing effect’s duration and independent termination rules still run. Its condition resumes on loss of protection if the effect still applies. The source gives ongoing immunity while the paladin is conscious and the creature is in range, without a provision ending the underlying effect. Treating entry as an immediate cure would make the protection permanent for effects already active at entry. [SRD p. 32, Aura of Courage](../../references/srd-5.1/pages/page-032.md) [SRD p. 33, Aura of Devotion](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-improved-divine-smite"></a>

### Improved Divine Smite

`srd51.class.paladin.improved-divine-smite`

From level 11, when you hit a creature with a melee weapon it takes 1d8 additional radiant damage. If you also use Divine Smite on the hit, include both sources of additional damage. [SRD p. 32, Improved Divine Smite](../../references/srd-5.1/pages/page-032.md)

<a id="srd51-class-paladin-cleansing-touch"></a>

### Cleansing Touch

`srd51.class.paladin.cleansing-touch`

From level 14, spend an action to end one spell affecting you or one willing creature you touch. Your uses equal your Charisma modifier, minimum one. A long rest restores all spent uses. [SRD p. 32, Cleansing Touch](../../references/srd-5.1/pages/page-032.md)

<a id="srd51-class-paladin-oath-of-devotion"></a>

### Oath of Devotion

`srd51.class.paladin.oath-of-devotion`

This oath grants two Channel Divinity choices at level 3, Aura of Devotion at 7, Purity of Spirit at 15, and Holy Nimbus at 20. Apply the [oath spell rules](#srd51-class-paladin-sacred-oath) to the spell grants below. [SRD p. 32, Sacred Oath and Oath of Devotion](../../references/srd-5.1/pages/page-032.md) [SRD p. 33, Oath of Devotion continuation](../../references/srd-5.1/pages/page-033.md)

- At level 3 gain [Protection from Evil and Good](spells-n-s.md#srd51-spell-protection-from-evil-and-good) and [Sanctuary](spells-n-s.md#srd51-spell-sanctuary).
- At level 5 gain [Lesser Restoration](spells-h-m.md#srd51-spell-lesser-restoration) and [Zone of Truth](spells-t-z.md#srd51-spell-zone-of-truth).
- At level 9 gain [Beacon of Hope](spells-a-c.md#srd51-spell-beacon-of-hope) and [Dispel Magic](spells-d-e.md#srd51-spell-dispel-magic).
- At level 13 gain [Freedom of Movement](spells-e-g.md#srd51-spell-freedom-of-movement) and [Guardian of Faith](spells-e-g.md#srd51-spell-guardian-of-faith).
- At level 17 gain [Commune](spells-a-c.md#srd51-spell-commune) and [Flame Strike](spells-e-g.md#srd51-spell-flame-strike). [SRD p. 33, Oath of Devotion Spells](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-tenets-of-devotion"></a>

#### Tenets of Devotion

`srd51.class.paladin.tenets-of-devotion`

The precise wording and restrictions can vary. The oath requires truthfulness and keeping your word under **Honesty**. **Courage** requires a willingness to act while allowing prudence. **Compassion** calls for aid, defense of vulnerable people, punishment of those who threaten them, and measured mercy toward enemies. **Honor** requires fair treatment and setting an example, pursuing the greatest good with the least harm. **Duty** requires accountability, protection of those in your care, and obedience to legitimate authority. [SRD p. 33, Tenets of Devotion](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-sacred-weapon"></a>

#### Sacred Weapon

`srd51.class.paladin.sacred-weapon`

From level 3, as an action expend Channel Divinity and imbue one weapon you hold. For up to 1 minute you add your Charisma modifier (minimum +1) to attack rolls made with that weapon. The weapon becomes magical for the duration if it was nonmagical. It emits bright light in a 20-foot radius and dim light for another 20 feet. The effect ends if you fall unconscious, cease holding or carrying the weapon, or end the effect on your turn as part of another action. [SRD p. 33, Channel Divinity: Sacred Weapon](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-turn-the-unholy"></a>

#### Turn the Unholy

`srd51.class.paladin.turn-the-unholy`

From level 3, use an action and a Channel Divinity use to present your holy symbol and speak a prayer. Every fiend or undead within 30 feet that can see or hear you makes a Wisdom save. On a failure it is turned for 1 minute or until it takes damage. Apply the same restrictions as [cleric Turn Undead](classes-cleric-druid.md#srd51-class-cleric-turn-undead) while it is turned. A paladin using Turn the Unholy has no Destroy Undead provision. [SRD p. 33, Channel Divinity: Turn the Unholy](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-aura-of-devotion"></a>

#### Aura of Devotion

`srd51.class.paladin.aura-of-devotion`

From level 7, while you are conscious, you and friendly creatures within 10 feet of you cannot be charmed. At level 18 the radius becomes 30 feet. Apply the [shared aura interpretation](#srd51-class-paladin-aura-of-courage) to existing charm effects. [SRD p. 33, Aura of Devotion](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-purity-of-spirit"></a>

#### Purity of Spirit

`srd51.class.paladin.purity-of-spirit`

From level 15, you are continuously under the effects of [Protection from Evil and Good](spells-n-s.md#srd51-spell-protection-from-evil-and-good). [SRD p. 33, Purity of Spirit](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-holy-nimbus"></a>

#### Holy Nimbus

`srd51.class.paladin.holy-nimbus`

At level 20, use an action to emit sunlight for 1 minute. Bright light extends 30 feet from you, and dim light extends another 30 feet. Each enemy creature that starts a turn in the bright light takes 10 radiant damage. Throughout the duration, you have advantage on saves against spells cast by fiends or undead. Finish a long rest to use the feature again. [SRD p. 33, Holy Nimbus](../../references/srd-5.1/pages/page-033.md)

<a id="srd51-class-paladin-breaking-your-oath"></a>

#### Breaking Your Oath

`srd51.class.paladin.breaking-your-oath`

Breaking a vow normally leads a paladin to seek absolution from a cleric of the same faith or a paladin of the same order. An overnight prayer vigil, fasting, or similar penance are possible signs of repentance. A rite of confession and forgiveness gives a fresh start. For a willful breach with no repentance, the GM may require the paladin to abandon this class and adopt another. That consequence is discretionary. [SRD p. 33, Breaking Your Oath](../../references/srd-5.1/pages/page-033.md) [SRD p. 34, Breaking Your Oath continuation](../../references/srd-5.1/pages/page-034.md)

<a id="srd51-class-ranger"></a>

## Ranger

`srd51.class.ranger`

Each ranger level grants one d10 Hit Die. At character level 1, maximum hit points are 10 + Constitution modifier. Each later ranger level adds 1d10 or a fixed 6, plus Constitution modifier. Starting proficiencies are light armor, medium armor, shields, simple weapons, martial weapons, Strength saves, and Dexterity saves. There are no starting tool proficiencies. Choose three skills from Animal Handling, Athletics, Insight, Investigation, Nature, Perception, Stealth, and Survival. [SRD p. 35, Ranger Class Features](../../references/srd-5.1/pages/page-035.md)

In addition to background equipment, choose scale mail or leather armor, two shortswords or two simple melee weapons, and a dungeoneer's pack or an explorer's pack. Also receive a longbow and a quiver of 20 arrows. [SRD p. 35, Ranger Equipment](../../references/srd-5.1/pages/page-035.md)

<a id="srd51-class-ranger-favored-enemy"></a>

### Favored Enemy

`srd51.class.ranger.favored-enemy`

At level 1 choose one favored enemy type from aberrations, beasts, celestials, constructs, dragons, elementals, fey, fiends, giants, monstrosities, oozes, plants, and undead. Alternatively, choose two humanoid races. For a selected enemy, apply advantage both to tracking with Wisdom (Survival) and to recalling information with Intelligence. Learn one chosen language spoken by a selected favored enemy if any speak a language. At levels 6 and 14 choose an additional favored enemy and associated language. These later choices should reflect monsters you have encountered. [SRD p. 35, Favored Enemy](../../references/srd-5.1/pages/page-035.md) [SRD p. 36, Favored Enemy continuation](../../references/srd-5.1/pages/page-036.md)

<a id="srd51-class-ranger-natural-explorer"></a>

### Natural Explorer

`srd51.class.ranger.natural-explorer`

At level 1 choose favored terrain from arctic, coast, desert, forest, grassland, mountain, and swamp. Choose one additional type at level 6 and one at level 10. On an Intelligence or Wisdom check about favored terrain that uses a skill in which you are proficient, double the proficiency contribution. [SRD p. 36, Natural Explorer](../../references/srd-5.1/pages/page-036.md)

On travel lasting at least 1 hour in favored terrain, apply the following benefits. [SRD p. 36, Natural Explorer](../../references/srd-5.1/pages/page-036.md)

- Difficult terrain does not slow the group.
- The group can become lost only through magical means.
- You stay alert for danger while doing another travel task such as foraging, navigating, or tracking.
- Traveling alone, you may move stealthily at a normal pace.
- Foraging yields twice your normal amount of food.
- Tracking other creatures reveals their exact number and sizes and how long ago they passed through.

<a id="srd51-class-ranger-fighting-style"></a>

### Fighting Style

`srd51.class.ranger.fighting-style`

At level 2 choose one of [Archery](classes-fighter-monk.md#srd51-class-fighter-archery), [Defense](classes-fighter-monk.md#srd51-class-fighter-defense), [Dueling](classes-fighter-monk.md#srd51-class-fighter-dueling), and [Two-Weapon Fighting](classes-fighter-monk.md#srd51-class-fighter-two-weapon-fighting). A style cannot be selected twice even if you receive another choice. [SRD p. 36, Fighting Style](../../references/srd-5.1/pages/page-036.md)

<a id="srd51-class-ranger-spellcasting"></a>

### Spellcasting

`srd51.class.ranger.spellcasting`

At level 2 learn two chosen 1st-level spells from the [ranger spell list](spell-lists.md#srd51-spell-list-ranger). Gain one more chosen ranger spell at each odd ranger level from 3 through 19, reaching eleven spells known. A spell you learn must be of a level for which the class has slots. Each time you gain a ranger level you may replace one known ranger spell with another ranger-list spell of a level for which the class has slots. Use the [paladin and ranger slot progression](#srd51-class-paladin-ranger-spell-slot-progression). To cast a leveled ranger spell with a slot, spend a slot of its level or higher. Completing a long rest restores all expended slots. [SRD p. 35, The Ranger](../../references/srd-5.1/pages/page-035.md) [SRD p. 36, Spell Slots and Spells Known of 1st Level and Higher](../../references/srd-5.1/pages/page-036.md)

Wisdom is the spellcasting ability. Save DC is 8 + proficiency bonus + Wisdom modifier. Spell attack modifier is proficiency bonus + Wisdom modifier. [SRD p. 36, Spellcasting Ability](../../references/srd-5.1/pages/page-036.md)

<a id="srd51-class-paladin-ranger-spell-slot-progression"></a>

### Paladin and ranger slot progression

`srd51.class.paladin-ranger.spell-slot-progression`

This single-class progression applies to each class separately. For each spell level, entries are class level → slot capacity, which persists until the next listed change. Before the first entry there are no slots of that spell level. [SRD p. 30, The Paladin](../../references/srd-5.1/pages/page-030.md) [SRD p. 35, The Ranger](../../references/srd-5.1/pages/page-035.md)

| Spell level | Slot-capacity changes |
| --- | --- |
| 1st | 2 → 2, 3 → 3, 5 → 4 |
| 2nd | 5 → 2, 7 → 3 |
| 3rd | 9 → 2, 11 → 3 |
| 4th | 13 → 1, 15 → 2, 17 → 3 |
| 5th | 17 → 1, 19 → 2 |

<a id="srd51-class-ranger-ranger-archetype"></a>

### Ranger Archetype

`srd51.class.ranger.ranger-archetype`

Choose an archetype at level 3. It grants features at levels 3, 7, 11, and 15. The SRD details [Hunter](#srd51-class-ranger-hunter). [SRD p. 37, Ranger Archetype and Hunter](../../references/srd-5.1/pages/page-037.md) [SRD p. 38, Hunter continuation](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-primeval-awareness"></a>

### Primeval Awareness

`srd51.class.ranger.primeval-awareness`

From level 3, spend an action and one ranger spell slot. For 1 minute per level of that slot, sense the presence of aberrations, celestials, dragons, elementals, fey, fiends, and undead within 1 mile of you, or up to 6 miles when you are in favored terrain. The feature supplies presence information without locations or counts. [SRD p. 37, Primeval Awareness](../../references/srd-5.1/pages/page-037.md)

<a id="srd51-class-ranger-ability-score-improvement"></a>

### Ability Score Improvement

`srd51.class.ranger.ability-score-improvement`

At levels 4, 8, 12, 16, and 19, apply an [Ability Score Improvement](character-advancement.md#srd51-character-ability-score-improvement). [SRD p. 37, Ability Score Improvement](../../references/srd-5.1/pages/page-037.md)

<a id="srd51-class-ranger-extra-attack"></a>

### Extra Attack

`srd51.class.ranger.extra-attack`

From level 5, take up to two attacks whenever you take the Attack action on your turn. [SRD p. 37, Extra Attack](../../references/srd-5.1/pages/page-037.md)

<a id="srd51-class-ranger-lands-stride"></a>

### Land's Stride

`srd51.class.ranger.lands-stride`

From level 8, use the [Land's Stride behavior](classes-cleric-druid.md#srd51-class-druid-lands-stride). It removes the extra movement cost of nonmagical difficult terrain, permits passage through nonmagical plants without slowing or damage from thorns and similar plant hazards, and gives advantage on saves against plants magically created or manipulated to impede movement. [SRD p. 37, Land's Stride](../../references/srd-5.1/pages/page-037.md)

<a id="srd51-class-ranger-hide-in-plain-sight"></a>

### Hide in Plain Sight

`srd51.class.ranger.hide-in-plain-sight`

From level 10, spend 1 minute making camouflage from fresh mud, dirt, plants, soot, and other natural materials to which you have access. Then you may attempt to hide by pressing against a solid surface at least as tall and wide as you. You gain +10 to Dexterity (Stealth) checks while remaining there without moving or taking actions. Moving, taking an action, or taking a reaction requires applying fresh camouflage before getting the benefit again. [SRD p. 37, Hide in Plain Sight](../../references/srd-5.1/pages/page-037.md)

<a id="srd51-class-ranger-vanish"></a>

### Vanish

`srd51.class.ranger.vanish`

From level 14, you may take the Hide action as a bonus action on your turn. Nonmagical tracking cannot follow you unless you choose to leave a trail. [SRD p. 37, Vanish](../../references/srd-5.1/pages/page-037.md)

<a id="srd51-class-ranger-feral-senses"></a>

### Feral Senses

`srd51.class.ranger.feral-senses`

From level 18, when you attack a creature you cannot see, that lack of sight does not impose disadvantage under the [unseen target rule](combat.md#srd51-core-unseen-attackers-and-targets). Other sources of disadvantage still apply, including the [Invisible condition’s independent modifier](conditions.md#srd51-condition-invisible) under the shared interpretation. You also know the location of each invisible creature within 30 feet if it is not hidden from you and you are neither blinded nor deafened. [SRD p. 37, Feral Senses](../../references/srd-5.1/pages/page-037.md) [SRD p. 94, Unseen Attackers and Targets](../../references/srd-5.1/pages/page-094.md) [SRD p. 358, Invisible](../../references/srd-5.1/pages/page-358.md)

<a id="srd51-class-ranger-foe-slayer"></a>

### Foe Slayer

`srd51.class.ranger.foe-slayer`

From level 20, once on each of your turns you may add your Wisdom modifier to either the attack roll or the damage roll of an attack against a favored enemy. You can decide before or after the relevant roll, up to the point its effects are applied. [SRD p. 37, Foe Slayer](../../references/srd-5.1/pages/page-037.md)

<a id="srd51-class-ranger-hunter"></a>

### Hunter

`srd51.class.ranger.hunter`

Hunter grants one Hunter's Prey option at level 3, one Defensive Tactics option at 7, one Multiattack option at 11, and one Superior Hunter's Defense option at 15. [SRD p. 37, Hunter and Hunter's Prey](../../references/srd-5.1/pages/page-037.md) [SRD p. 38, Hunter continuation](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-hunters-prey"></a>

#### Hunter's Prey

`srd51.class.ranger.hunters-prey`

Choose one at level 3. [SRD p. 37, Hunter's Prey](../../references/srd-5.1/pages/page-037.md) [SRD p. 38, Hunter's Prey continuation](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-colossus-slayer"></a>

##### Colossus Slayer

`srd51.class.ranger.colossus-slayer`

When your weapon attack hits a creature whose current hit points are below its maximum, add 1d8 damage. The bonus damage is limited to once per turn. [SRD p. 38, Colossus Slayer](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-giant-killer"></a>

##### Giant Killer

`srd51.class.ranger.giant-killer`

Immediately after a Large or larger creature within 5 feet hits or misses you with an attack, you may spend a reaction to attack it if you can see it. [SRD p. 38, Giant Killer](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-horde-breaker"></a>

##### Horde Breaker

`srd51.class.ranger.horde-breaker`

Once on each of your turns when you make a weapon attack, you may make another attack with the same weapon. The new target must be a different creature within 5 feet of the original target and in the weapon's range. [SRD p. 38, Horde Breaker](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-defensive-tactics"></a>

#### Defensive Tactics

`srd51.class.ranger.defensive-tactics`

Choose one at level 7. [SRD p. 38, Defensive Tactics](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-escape-the-horde"></a>

##### Escape the Horde

`srd51.class.ranger.escape-the-horde`

Opportunity attacks against you have disadvantage. [SRD p. 38, Escape the Horde](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-multiattack-defense"></a>

##### Multiattack Defense

`srd51.class.ranger.multiattack-defense`

After a creature hits you with an attack, your AC increases by 4 against subsequent attacks by that creature through the rest of the turn. [SRD p. 38, Multiattack Defense](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-steel-will"></a>

##### Steel Will

`srd51.class.ranger.steel-will`

Gain advantage on saving throws against being frightened. [SRD p. 38, Steel Will](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-multiattack"></a>

#### Multiattack

`srd51.class.ranger.multiattack`

Choose one at level 11. Each option uses an action in its own right. [SRD p. 38, Multiattack](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-volley"></a>

##### Volley

`srd51.class.ranger.volley`

Choose a point you can see within your weapon's range. Make a ranged attack against any number of creatures you choose within 10 feet of that point. Use ammunition and a separate attack roll for each target. [SRD p. 38, Volley](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-whirlwind-attack"></a>

##### Whirlwind Attack

`srd51.class.ranger.whirlwind-attack`

Make a melee attack against any number of chosen creatures within 5 feet of you, rolling separately for each target. [SRD p. 38, Whirlwind Attack](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-superior-hunters-defense"></a>

#### Superior Hunter's Defense

`srd51.class.ranger.superior-hunters-defense`

Choose one at level 15. [SRD p. 38, Superior Hunter's Defense](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-evasion"></a>

##### Evasion

`srd51.class.ranger.evasion`

Apply the [Evasion rule](classes-fighter-monk.md#srd51-class-monk-evasion). [SRD p. 38, Evasion](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-stand-against-the-tide"></a>

##### Stand Against the Tide

`srd51.class.ranger.stand-against-the-tide`

When a hostile creature misses you with a melee attack, spend a reaction to make it repeat the same attack against a different creature you choose. It cannot be the attacker itself. [SRD p. 38, Stand Against the Tide](../../references/srd-5.1/pages/page-038.md)

<a id="srd51-class-ranger-uncanny-dodge"></a>

##### Uncanny Dodge

`srd51.class.ranger.uncanny-dodge`

Apply the [Uncanny Dodge rule](classes-rogue-sorcerer.md#srd51-class-rogue-uncanny-dodge). [SRD p. 38, Uncanny Dodge](../../references/srd-5.1/pages/page-038.md)

## Source-checked cases

- **Ordinary.** A level 5 paladin with Charisma modifier +2 prepares four paladin spells of 1st or 2nd level, separately from any oath spells. Casting one with an eligible slot does not remove it from preparation. [SRD p. 30, The Paladin](../../references/srd-5.1/pages/page-030.md) [SRD p. 31, Preparing and Casting Spells](../../references/srd-5.1/pages/page-031.md) [SRD p. 32, Oath Spells](../../references/srd-5.1/pages/page-032.md)
- **Boundary.** A conscious level 18 paladin grants Aura of Protection to a friendly creature 30 feet away. A Charisma modifier of 0 still produces a +1 bonus. If the paladin falls unconscious, the bonus ceases. [SRD p. 32, Aura of Protection](../../references/srd-5.1/pages/page-032.md)
- **Exception.** Spending a 5th-level slot on Divine Smite adds 5d8 radiant damage to a hit against a giant, or 6d8 against a fiend. [SRD p. 31, Divine Smite](../../references/srd-5.1/pages/page-031.md) [SRD p. 32, Divine Smite continuation](../../references/srd-5.1/pages/page-032.md)
- **Invalid.** A ranger cannot use Horde Breaker against the original target, even if that target is still in range. A miss can trigger it on the ranger's turn, provided the extra attack targets a different eligible creature. [SRD p. 38, Horde Breaker](../../references/srd-5.1/pages/page-038.md)

## Source coverage

| Source | Destination |
| --- | --- |
| [SRD p. 30, Paladin Class Features, The Paladin, and Divine Sense](../../references/srd-5.1/pages/page-030.md) | [Paladin](#srd51-class-paladin), [Divine Sense](#srd51-class-paladin-divine-sense), [Slot progression](#srd51-class-paladin-ranger-spell-slot-progression), and paladin level gates above |
| [SRD p. 31, Lay on Hands through Divine Smite](../../references/srd-5.1/pages/page-031.md) · [SRD p. 32, Divine Smite continuation](../../references/srd-5.1/pages/page-032.md) | [Lay on Hands](#srd51-class-paladin-lay-on-hands), [Fighting Style](#srd51-class-paladin-fighting-style), [Paladin Spellcasting](#srd51-class-paladin-spellcasting), [Divine Smite](#srd51-class-paladin-divine-smite) |
| [SRD p. 32, Divine Health through Cleansing Touch](../../references/srd-5.1/pages/page-032.md) | [Divine Health](#srd51-class-paladin-divine-health), [Sacred Oath and Oath Spells](#srd51-class-paladin-sacred-oath), [Channel Divinity](#srd51-class-paladin-channel-divinity), [Ability Score Improvement](#srd51-class-paladin-ability-score-improvement), [Extra Attack](#srd51-class-paladin-extra-attack), [Aura of Protection](#srd51-class-paladin-aura-of-protection), [Aura of Courage](#srd51-class-paladin-aura-of-courage), [Improved Divine Smite](#srd51-class-paladin-improved-divine-smite), [Cleansing Touch](#srd51-class-paladin-cleansing-touch) |
| [SRD p. 32, Sacred Oaths and Oath of Devotion](../../references/srd-5.1/pages/page-032.md) · [SRD p. 33, Oath of Devotion continuation and Breaking Your Oath](../../references/srd-5.1/pages/page-033.md) · [SRD p. 34, Breaking Your Oath continuation](../../references/srd-5.1/pages/page-034.md) | [Oath of Devotion and its spells](#srd51-class-paladin-oath-of-devotion), [Tenets](#srd51-class-paladin-tenets-of-devotion), [Sacred Weapon](#srd51-class-paladin-sacred-weapon), [Turn the Unholy](#srd51-class-paladin-turn-the-unholy), [Aura of Devotion](#srd51-class-paladin-aura-of-devotion), [Purity of Spirit](#srd51-class-paladin-purity-of-spirit), [Holy Nimbus](#srd51-class-paladin-holy-nimbus), [Breaking Your Oath](#srd51-class-paladin-breaking-your-oath) |
| [SRD p. 35, Ranger Class Features, The Ranger, and Favored Enemy](../../references/srd-5.1/pages/page-035.md) · [SRD p. 36, Favored Enemy continuation through Spellcasting](../../references/srd-5.1/pages/page-036.md) | [Ranger](#srd51-class-ranger), [Favored Enemy](#srd51-class-ranger-favored-enemy), [Natural Explorer](#srd51-class-ranger-natural-explorer), [Fighting Style](#srd51-class-ranger-fighting-style), [Ranger Spellcasting](#srd51-class-ranger-spellcasting), [Slot progression](#srd51-class-paladin-ranger-spell-slot-progression), and ranger level gates above |
| [SRD p. 37, Ranger Archetype through Foe Slayer](../../references/srd-5.1/pages/page-037.md) | [Ranger Archetype](#srd51-class-ranger-ranger-archetype), [Primeval Awareness](#srd51-class-ranger-primeval-awareness), [Ability Score Improvement](#srd51-class-ranger-ability-score-improvement), [Extra Attack](#srd51-class-ranger-extra-attack), [Land's Stride](#srd51-class-ranger-lands-stride), [Hide in Plain Sight](#srd51-class-ranger-hide-in-plain-sight), [Vanish](#srd51-class-ranger-vanish), [Feral Senses](#srd51-class-ranger-feral-senses), [Foe Slayer](#srd51-class-ranger-foe-slayer) |
| [SRD p. 37, Hunter and Hunter's Prey](../../references/srd-5.1/pages/page-037.md) · [SRD p. 38, Hunter's Prey continuation through Superior Hunter's Defense](../../references/srd-5.1/pages/page-038.md) | [Hunter](#srd51-class-ranger-hunter), [Hunter's Prey](#srd51-class-ranger-hunters-prey), [Defensive Tactics](#srd51-class-ranger-defensive-tactics), [Multiattack](#srd51-class-ranger-multiattack), [Superior Hunter's Defense](#srd51-class-ranger-superior-hunters-defense) |

The order's appearance, example names, and other class flavor describe characterization. The oath's duties, discretionary breach consequences, and favored enemy selection guidance are included above. The rendered PDF confirmed the Paladin and Ranger tables on pp. 30 and 35 and the Devotion spell and oath-breach continuations on pp. 33–34.
