# Spells A–C

Each spell's casting line gives level and school, casting time, range, components, and duration in
that order. V, S, and M mean verbal, somatic, and material. A listed price requires the named
material, and materials are consumed only where specified. A ritual tag uses the general ritual
rules. Unmodified casting and resolution use the shared
[casting procedure](spellcasting.md#srd51-core-cast-spell),
[slots](spellcasting.md#srd51-core-spell-slots),
[components](spellcasting.md#srd51-core-spell-components),
[range](spellcasting.md#srd51-core-spell-range),
[targets](spellcasting.md#srd51-core-spell-targets),
[areas](spellcasting.md#srd51-core-spell-area),
[spell saves](spellcasting.md#srd51-core-spell-saving-throw),
[spell attack rolls](spellcasting.md#srd51-core-spell-attack-roll),
[duration](spellcasting.md#srd51-core-spell-duration), and
[concentration](spellcasting.md#srd51-core-concentration) rules.
HP means hit points, AC means Armor Class, and CR means challenge rating.
Find spell names and class access lists in the [spell index](spell-index.md).

<a id="srd51-spell-acid-arrow"></a>

## Acid Arrow

`srd51.spell.acid-arrow`

Level 2 evocation · 1 action · 90 feet · V, S, M (powdered rhubarb leaf and an adder's stomach) · Instantaneous.

Make a ranged spell attack against one target in range. On a hit, deal 4d4 acid damage now and 2d4
acid damage at the end of that target's next turn. A miss deals half the immediate damage and skips
the later damage. Each slot level above 2 adds 1d4 to each of the two damage rolls before resolving
the applicable hit or miss branch.

[SRD p. 114, Acid Arrow](../../references/srd-5.1/pages/page-114.md)

<a id="srd51-spell-acid-splash"></a>

## Acid Splash

`srd51.spell.acid-splash`

Conjuration cantrip · 1 action · 60 feet · V, S · Instantaneous.

Choose one creature or a pair of creatures. Each must be in range and the pair can be at most 5 feet
apart. Each creature independently saves with Dexterity, taking acid damage only on failure. The
damage is 1d6, rising to 2d6, 3d6, and 4d6 at caster levels 5, 11, and 17 respectively.

[SRD p. 114, Acid Splash](../../references/srd-5.1/pages/page-114.md)

<a id="srd51-spell-aid"></a>

## Aid

`srd51.spell.aid`

Level 2 abjuration · 1 action · 30 feet · V, S, M (a tiny strip of white cloth) · 8 hours.

Pick up to three creatures in range. Each has its hit point maximum and current hit points increased
by the same amount for the duration. The increase is 5 per target, with another 5 per slot level above
2.

**Project interpretation.** At expiry subtract Aid's bonus from both the maximum and the
then-current hit points, with current hit points floored at 0. This removes both temporary
increases while preserving intervening healing or damage. Restoring only the maximum could let
the current hit point increase persist beyond the stated duration. This expiry deals no damage.
If the subtraction takes a creature to 0, use the general
[0 HP transition](combat.md#srd51-core-dropping-to-zero-hit-points) to make it unconscious
without overflow damage or damage-triggered effects. The source introduces the 0 HP transition
generally and gives damage-specific outcomes separately.

[SRD p. 114, Aid](../../references/srd-5.1/pages/page-114.md) · [SRD p. 97, Hit Points and Dropping to 0 Hit Points](../../references/srd-5.1/pages/page-097.md) · [SRD p. 98, Instant Death and Falling Unconscious](../../references/srd-5.1/pages/page-098.md)

<a id="srd51-spell-alarm"></a>

## Alarm

`srd51.spell.alarm`

Level 1 abjuration (ritual) · 1 minute · 30 feet · V, S, M (a tiny bell and a piece of fine silver wire) · 8 hours.

Ward a door, window, or area in range no larger than a 20-foot cube. At casting, choose any creatures
to exempt and select the alert mode. A creature of Tiny size or larger that has no exemption triggers
the alarm whenever it enters or touches the ward. Mental mode sends you a mental ping within 1 mile of
the ward, waking you if asleep. Audible mode sounds like a handbell for 10 seconds and can be heard
within 60 feet.

[SRD p. 114, Alarm](../../references/srd-5.1/pages/page-114.md)

<a id="srd51-spell-alter-self"></a>

## Alter Self

`srd51.spell.alter-self`

Level 2 transmutation · 1 action · Self · V, S · Concentration up to 1 hour.

Select a mode at casting. While the spell persists, spend an action to switch from your current mode
to a different one.

- **Aquatic Adaptation.** Gills let you breathe underwater, and your webbed body has a swimming speed equal to your walking speed.
- **Change Appearance.** Design your looks and voice, including height, weight, facial features, hair length, coloring, and distinctive traits. A different apparent race is possible. Your statistics, size category, and basic body plan stay fixed. In this mode you can spend an action to set a new appearance.
- **Natural Weapons.** Choose a bodily weapon such as claws or fangs. Your unarmed strikes use 1d6 damage of the bludgeoning, piercing, or slashing type appropriate to that weapon, and you have proficiency in them. The weapon is magical, and provides a +1 bonus to its attack and damage rolls.

[SRD p. 114, Alter Self](../../references/srd-5.1/pages/page-114.md) · [SRD p. 115, Alter Self continuation](../../references/srd-5.1/pages/page-115.md)

<a id="srd51-spell-animal-friendship"></a>

## Animal Friendship

`srd51.spell.animal-friendship`

Level 1 enchantment · 1 action · 30 feet · V, S, M (a morsel of food) · 24 hours.

Pick a beast you can see in range that can also see and hear you. Intelligence 4 or higher causes the
spell to fail on that beast. Otherwise it saves with Wisdom and is charmed by you for the duration on
failure. The spell ends if you or one of your companions harms the target.

**Evidence gap.** The PDF's scaling sentence breaks after “one additional beast,” leaving a stray
letter before “level above 1st.”

**Project interpretation.** Allow one extra beast per slot level above 1. Read the missing words as
“for each slot” because the preceding clause names the 2nd-level-or-higher slot and the surviving
terms give the increment and baseline.

[SRD p. 115, Animal Friendship](../../references/srd-5.1/pages/page-115.md)

<a id="srd51-spell-animal-messenger"></a>

## Animal Messenger

`srd51.spell.animal-messenger`

Level 2 enchantment (ritual) · 1 action · 30 feet · V, S, M (a morsel of food) · 24 hours.

Choose a Tiny beast you can see in range, a place you have previously visited, and a general
description identifying the intended recipient. Speak up to 25 words. The beast travels toward the
chosen place for the duration, at roughly 50 miles per 24 hours if flying or 25 miles per 24 hours
otherwise. On arrival it repeats your words in your voice exclusively to a creature matching the
description. If it fails to reach the place before expiry, the message is lost and the beast heads
back to the casting site. Add 48 hours per slot level above 2.

[SRD p. 115, Animal Messenger](../../references/srd-5.1/pages/page-115.md)

<a id="srd51-spell-animal-shapes"></a>

## Animal Shapes

`srd51.spell.animal-shapes`

Level 8 transmutation · 1 action · 30 feet · V, S · Concentration up to 24 hours.

Choose any number of willing creatures you can see in range. Assign each a beast form of Large size or
smaller and CR 4 or lower. On later turns you can spend an action to assign new forms to creatures
still affected. The forms may differ by creature.

The beast statistics replace the target's statistics except its alignment and Intelligence, Wisdom,
and Charisma scores. It uses the form's hit points and can act only as the form permits. It cannot
speak or cast spells. Its gear merges into the body and confers no benefits or ability to use, wield,
or activate it. The target reverts when the duration ends, when it reaches 0 hit points, or when it
dies. On reverting, restore the hit points it had before transforming, then apply any excess damage
from reaching 0 in the beast form. Reverting at 0 in beast form causes no unconsciousness if the
carryover leaves the normal form above 0.

[SRD p. 115, Animal Shapes](../../references/srd-5.1/pages/page-115.md)

<a id="srd51-spell-animate-dead"></a>

## Animate Dead

`srd51.spell.animate-dead`

Level 3 necromancy · 1 minute · 10 feet · V, S, M (a drop of blood, a piece of flesh, and a pinch of bone dust) · Instantaneous.

Animate a pile of bones or corpse in range from a Medium or Small humanoid. Bones produce a
[skeleton](creatures-m-z.md#srd51-creature-skeleton) and a corpse produces a
[zombie](creatures-m-z.md#srd51-creature-zombie). The DM has the stat block. Control lasts 24 hours. On your turn you
may spend a bonus action to give a mental order to any or all creatures you created with this spell
within 60 feet, with the same order for each. Specify the next turn's action and movement or give a
continuing assignment. An assignment persists through completion. Without orders, a servant only
defends itself against hostile creatures.

At expiry the undead ceases following your orders. Before its control window expires, recast on
existing servants to renew control for 24 hours over up to four creatures you previously animated with
this spell. This use replaces creation. Each slot level above 3 raises the creation or renewal count
by two. Each undead comes from separate remains.

[SRD p. 115, Animate Dead](../../references/srd-5.1/pages/page-115.md) · [SRD p. 116, Animate Dead continuation](../../references/srd-5.1/pages/page-116.md) · [SRD p. 346, Skeleton](../../references/srd-5.1/pages/page-346.md) · [SRD p. 356, Zombie](../../references/srd-5.1/pages/page-356.md) · [SRD p. 357, Zombie continuation](../../references/srd-5.1/pages/page-357.md)

<a id="srd51-spell-animate-objects"></a>

## Animate Objects

`srd51.spell.animate-objects`

Level 5 transmutation · 1 action · 120 feet · V, S · Concentration up to 1 minute.

Choose unattended nonmagical objects in range using a budget of 10. Unattended means neither worn nor
carried. Tiny and Small each cost 1, Medium 2, Large 4, and Huge 8. Larger objects are ineligible.
Each chosen object becomes a construct under your control until it drops to 0 hit points or the spell
ends. At 0 it reverts to the original object and excess damage carries over. Add two to the budget per
slot level above 5.

| Size | Selection cost | Strength / Dexterity | Slam attack bonus and damage | Armor Class / hit points |
| --- | ---: | --- | --- | --- |
| Tiny | 1 | 4 / 18 | +8, 1d4 + 4 | 18 / 20 |
| Small | 1 | 6 / 14 | +6, 1d8 + 2 | 16 / 25 |
| Medium | 2 | 10 / 12 | +5, 2d6 + 1 | 13 / 40 |
| Large | 4 | 14 / 10 | +6, 2d10 + 2 | 10 / 50 |
| Huge | 8 | 18 / 6 | +8, 2d12 + 4 | 10 / 80 |

All forms have Constitution 10, Intelligence 3, Wisdom 3, and Charisma 1. Each has blindsight to 30
feet and is blind beyond that radius. Speed is 30 feet. Without usable locomotor limbs, it instead
flies at 30 feet and can hover. An object fastened securely to a surface or larger object has speed 0.

Spend a bonus action to command any or all your creations within 500 feet mentally. The chosen
constructs all get the same order. You can specify next-turn movement and action or assign a broader
task that persists until fulfilled. A creature without an order only defends itself against hostile
creatures. An attack order lets it make one melee slam against a creature within 5 feet. The default
damage is bludgeoning, with the DM able to choose piercing or slashing to suit the object.

[SRD p. 116, Animate Objects](../../references/srd-5.1/pages/page-116.md)

<a id="srd51-spell-antilife-shell"></a>

## Antilife Shell

`srd51.spell.antilife-shell`

Level 5 abjuration · 1 action · Self (10-foot radius) · V, S · Concentration up to 1 hour.

The barrier moves with you, centered on you. It keeps creatures other than undead and constructs from
crossing or reaching through. A barred creature may still cast spells or attack through the barrier
using a ranged or reach weapon. Your movement ends the spell if it would force a barred creature
across the barrier.

[SRD p. 116, Antilife Shell](../../references/srd-5.1/pages/page-116.md) · [SRD p. 117, Antilife Shell continuation](../../references/srd-5.1/pages/page-117.md)

<a id="srd51-spell-antimagic-field"></a>

## Antimagic Field

`srd51.spell.antimagic-field`

Level 8 abjuration · 1 action · Self (10-foot-radius sphere) · V, S, M (a pinch of powdered iron or iron filings) · Concentration up to 1 hour.

An invisible sphere follows you. Casting is blocked inside it. Except for effects made by an artifact
or deity, magic is suppressed within the sphere. Suppression preserves the passage of a duration and
consumes a slot spent to cast a suppressed spell.

- **Effect coverage.** A targeted magical effect fails to affect a creature or object in the sphere. An existing active effect on a creature or object is suppressed while that subject is inside. Overlapping magical areas lose only their covered portions.
- **Items and attacks.** Magic item properties and powers are suppressed inside. For a magic weapon, a wielder or target inside suffices to suppress them. A magic weapon or piece of ammunition that exits entirely regains its magic upon exit.
- **Travel and spaces.** Teleportation and planar travel fail if either endpoint lies inside. A portal to another place, world, or plane, or an opening to an extradimensional space, closes temporarily while inside.
- **Created or summoned subjects.** A magically summoned or created creature or object disappears while inside the sphere. It reappears immediately after the space it occupied leaves the sphere, including when the field ends.
- **Other magic removal.** Dispel Magic and comparable magical effects have no effect on the sphere. Overlapping Antimagic Field spheres coexist.

[SRD p. 117, Antimagic Field](../../references/srd-5.1/pages/page-117.md) · [SRD p. 136, Dispel Magic](../../references/srd-5.1/pages/page-136.md)

<a id="srd51-spell-antipathy-sympathy"></a>

## Antipathy/Sympathy

`srd51.spell.antipathy-sympathy`

Level 8 enchantment · 1 hour · 60 feet · V, S, M (vinegar-soaked lump of alum for Antipathy, or a drop of honey for Sympathy) · 10 days.

Choose a Huge or smaller object or creature, or an area no larger than a 200-foot cube, within range.
Specify a kind of intelligent creature and choose the aura mode. A creature of that kind must make a
Wisdom save when it can see the aura's target or comes within 60 feet.

- **Antipathy.** On failure, either proximity within 60 feet or sight of the target keeps the creature frightened. Its movement must take it to the nearest safe position out of sight of the target. Going beyond 60 feet and losing sight together remove the frightened condition. Until released from the effect, renewed sight or proximity restores that fear.
- **Sympathy.** On failure, the creature must spend its movement
  on each of its own turns approaching until it enters the target area or comes within reach of the target. It then cannot move away willingly. If the target harms it, it can make another Wisdom save to end the effect.

A creature affected by the aura makes a Wisdom save at the end of its own turn when it is both beyond 60
feet and unable to see the target. It also gets a further Wisdom save every 24 hours while affected
and the spell persists. A successful release save ends that creature's effect and reveals the magical
cause of the feeling. Success against the effect grants immunity to it for 1 minute, after which it
may affect the creature again.

[SRD p. 117, Antipathy/Sympathy](../../references/srd-5.1/pages/page-117.md) · [SRD p. 118, Antipathy/Sympathy continuation](../../references/srd-5.1/pages/page-118.md)

<a id="srd51-spell-arcane-eye"></a>

## Arcane Eye

`srd51.spell.arcane-eye`

Level 4 divination · 1 action · 30 feet · V, S, M (a bit of bat fur) · Concentration up to 1 hour.

Create an invisible, hovering magical eye in range. The eye has normal vision, darkvision to 30 feet,
and vision in all directions. You receive what it sees mentally. Each action you spend on it moves it
up to 30 feet in any direction. It can go beyond casting range on the same plane. Solid barriers stop
it unless an opening at least 1 inch in diameter permits passage.

[SRD p. 118, Arcane Eye](../../references/srd-5.1/pages/page-118.md)

<a id="srd51-spell-arcane-hand"></a>

## Arcane Hand

`srd51.spell.arcane-hand`

Level 5 evocation · 1 action · 120 feet · V, S, M (an eggshell and a snakeskin glove) · Concentration up to 1 minute.

Create a Large hand of force in an unoccupied space you can see in range. The hand mimics your hand's
movement. It is an object with AC 20, HP equal to your hit point maximum, Strength 26 (+8), and
Dexterity 10 (+0). It does not fill its space. The spell ends if the hand reaches 0 HP. At casting and
as a bonus action on subsequent turns, you may move it up to 60 feet and then select one effect.

- **Clenched Fist.** Attack one creature or object within 5 feet of the hand with a melee spell attack using your statistics. A hit deals 4d8 force damage.
- **Forceful Hand.** Choose a creature within 5 feet of the hand and a push direction. Contest its Strength (Athletics) with the hand's Strength check, with advantage against a Medium or smaller target. Success pushes up to 5 feet plus 5 feet per point of your spellcasting ability modifier. The hand follows to stay within 5 feet.
- **Grasping Hand.** Attempt a [grapple](combat.md#srd51-core-grappling) on a Huge or smaller creature within 5 feet of the hand, using the hand's Strength and gaining advantage on its check against Medium or smaller creatures. While it holds a creature, you can spend a bonus action to crush that creature for 2d6 plus your spellcasting ability modifier in bludgeoning damage.
- **Interposing Hand.** Choose a creature. The hand moves to stay between you and it until a different order, giving you half cover against it. That creature cannot cross the hand's space with Strength 26 or less. With Strength above 26, it can move through the space toward you, treating it as difficult terrain.

Each slot level above 5 adds 2d8 to Clenched Fist damage and 2d6 to Grasping Hand damage.

[SRD p. 118, Arcane Hand](../../references/srd-5.1/pages/page-118.md) · [SRD p. 95, Grappling](../../references/srd-5.1/pages/page-095.md) · [SRD p. 96, Cover](../../references/srd-5.1/pages/page-096.md)

<a id="srd51-spell-arcane-lock"></a>

## Arcane Lock

`srd51.spell.arcane-lock`

Level 2 abjuration · 1 action · Touch · V, S, M (gold dust worth at least 25 gp, consumed) · Until dispelled.

Touch a closed entry or container opening, such as a door, window, gate, or chest, to lock it magically. You and anyone designated at casting may open it normally. You may set a password that suppresses the lock for 1 minute when spoken within 5 feet. [Knock](spells-h-m.md#srd51-spell-knock) suppresses the lock for 10 minutes. The ward otherwise blocks passage until broken, dispelled, or suppressed. While active, it raises the DC to break the object or pick any lock on it by 10.

[SRD p. 118, Arcane Lock](../../references/srd-5.1/pages/page-118.md) · [SRD p. 119, Arcane Lock continuation](../../references/srd-5.1/pages/page-119.md) · [SRD p. 158, Knock](../../references/srd-5.1/pages/page-158.md)

<a id="srd51-spell-arcane-sword"></a>

## Arcane Sword

`srd51.spell.arcane-sword`

Level 7 evocation · 1 action · 60 feet · V, S, M (a miniature platinum sword with copper-and-zinc grip and pommel, worth 250 gp) · Concentration up to 1 minute.

Create a hovering force blade in range. On creation, make a melee spell attack against one target
within 5 feet of it. A hit deals 3d10 force damage. While the sword persists, you can spend a bonus
action on each of your turns to move it up to 20 feet to a visible spot and repeat the attack against a target
within 5 feet of its new location. You may select the previous target again.

[SRD p. 119, Arcane Sword](../../references/srd-5.1/pages/page-119.md)

<a id="srd51-spell-arcanists-magic-aura"></a>

## Arcanist's Magic Aura

`srd51.spell.arcanists-magic-aura`

Level 2 illusion · 1 action · Touch · V, S, M (a small square of silk) · 24 hours.

Touch a willing creature or an object that another creature is neither wearing nor carrying. Choose
one or both deceptions. Applying the same effect to the same target every day for 30 days extends it
until dispelled.

- **False Aura.** Change what aura detection reveals. An object can appear magical or nonmagical, or its aura can seem to come from a chosen school. On an object you may also make the false magic apparent to anyone handling it.
- **Mask.** Choose a creature type for detection magic and magical triggers such as Divine Sense and [Symbol](spells-n-s.md#srd51-spell-symbol). Those effects treat the target as having the selected type. **Project interpretation.** The result clause also mentions alignment, but the spell gives the caster a choice of creature type only and does not specify an alignment selection or a mapping from type to alignment. A separate false alignment would require inventing one of those inputs.

[SRD p. 119, Arcanist's Magic Aura](../../references/srd-5.1/pages/page-119.md) · [SRD p. 30, Divine Sense](../../references/srd-5.1/pages/page-030.md) · [SRD p. 134, Detect Magic](../../references/srd-5.1/pages/page-134.md) · [SRD p. 184, Symbol](../../references/srd-5.1/pages/page-184.md) · [SRD p. 185, Symbol continuation](../../references/srd-5.1/pages/page-185.md)

<a id="srd51-spell-astral-projection"></a>

## Astral Projection

`srd51.spell.astral-projection`

Level 9 necromancy · 1 hour · 10 feet · V, S, M (for each affected creature, a jacinth worth at least 1,000 gp and an ornately carved silver bar worth at least 100 gp, both consumed) · Special duration.

Project yourself and up to eight willing creatures within range into the Astral Plane. Casting from
that plane fails and wastes the casting. Each abandoned physical body becomes unconscious and
suspended, with no need for air or food and no aging. Each astral incarnation copies its creature's
statistics and possessions. Damage and effects on it are separate from those on the physical body and
do not follow the creature on return.

A silver cord runs from between the projection's shoulders and turns invisible after 1 foot. The
intact cord leads back to the body. Only an effect expressly capable of severing the cord can do so.
Severance separates soul and body and kills the creature immediately. Astral forms can travel freely
in the Astral Plane and use its portals to other planes. On entering a new plane or reentering the
casting plane this way, the body and possessions travel along the cord and the creature enters its
body there.

You can dismiss the spell for yourself and all companions as an action. A successful [Dispel Magic](spells-d-e.md#srd51-spell-dispel-magic) on either body or dropping either body to 0 HP ends the effect for that creature. When it ends with the cord intact, that creature returns to its physical body and awakens. If you return prematurely, companions remain projected and must get back independently, usually by dropping to 0 HP.

[SRD p. 119, Astral Projection](../../references/srd-5.1/pages/page-119.md) · [SRD p. 120, Astral Projection continuation](../../references/srd-5.1/pages/page-120.md) · [SRD p. 136, Dispel Magic](../../references/srd-5.1/pages/page-136.md) · [SRD p. 363, The Transitive Planes](../../references/srd-5.1/pages/page-363.md) · [SRD p. 364, Astral Plane](../../references/srd-5.1/pages/page-364.md)

<a id="srd51-spell-augury"></a>

## Augury

`srd51.spell.augury`

Level 2 divination (ritual) · 1 minute · Self · V, S, M (marked sticks, bones, or comparable tokens worth at least 25 gp) · Instantaneous.

Specify a course of action you plan to take within the next 30 minutes. Use the divining tools to
receive an otherworldly omen chosen by the DM. Weal means favorable, woe unfavorable, weal and woe
mixed, and nothing neither particularly favorable nor unfavorable. The reading excludes possible
changes of circumstance, such as additional spells or party changes. After the first casting before
your next long rest, each further casting raises the chance of a random reading by 25 percentage
points. The DM rolls secretly.

[SRD p. 120, Augury](../../references/srd-5.1/pages/page-120.md)

<a id="srd51-spell-awaken"></a>

## Awaken

`srd51.spell.awaken`

Level 5 transmutation · 8 hours · Touch · V, S, M (an agate worth at least 1,000 gp, consumed) · Instantaneous.

Trace magical pathways in the agate during casting, then touch a Huge or smaller beast or plant. It
must lack an Intelligence score or have one of 3 or lower. Its Intelligence becomes 10 and it can
speak one language you know. A plant gains movement of its own limbs, roots, vines, or equivalent
structures, and humanlike senses. The DM determines suitable plant statistics, such as those for an
[awakened shrub](creatures-misc-a-g.md#srd51-creature-awakened-shrub) or
[awakened tree](creatures-misc-a-g.md#srd51-creature-awakened-tree). The awakened being is charmed by you for 30 days or until you or a companion
harms it. When the charm ends it decides whether to remain friendly based on its treatment during the
charm.

[SRD p. 120, Awaken](../../references/srd-5.1/pages/page-120.md) · [SRD p. 366, Awakened Shrub and Awakened Tree](../../references/srd-5.1/pages/page-366.md)

<a id="srd51-spell-bane"></a>

## Bane

`srd51.spell.bane`

Level 1 enchantment · 1 action · 30 feet · V, S, M (a drop of blood) · Concentration up to 1 minute.

Choose up to three creatures you can see in range. Each rolls a Charisma save. A creature that fails
rolls and subtracts 1d4 each time it makes an attack roll or saving throw before the spell ends. Each
slot level above 1 permits one additional target.

[SRD p. 120, Bane](../../references/srd-5.1/pages/page-120.md)

<a id="srd51-spell-banishment"></a>

## Banishment

`srd51.spell.banishment`

Level 4 abjuration · 1 action · 60 feet · V, S, M (an item distasteful to the target) · Concentration up to 1 minute.

Choose a visible creature in range. Each slot level above 4 permits one additional creature. Each
chosen creature makes a Charisma save. On failure, resolve by its native plane.

- **Native to the current plane.** The creature is incapacitated on a harmless demiplane until the spell ends. It then returns to its original space or the nearest unoccupied space if the original is occupied.
- **Native elsewhere.** The creature returns to its home plane with a faint pop. If the spell ends before 1 minute elapses, it returns to its original space or the nearest unoccupied one. If the full minute elapses, it stays on its home plane.

[SRD p. 120, Banishment](../../references/srd-5.1/pages/page-120.md) · [SRD p. 121, Banishment continuation](../../references/srd-5.1/pages/page-121.md)

<a id="srd51-spell-barkskin"></a>

## Barkskin

`srd51.spell.barkskin`

Level 2 transmutation · 1 action · Touch · V, S, M (a handful of oak bark) · Concentration up to 1 hour.

Touch a willing creature. Its skin resembles bark and its AC has a floor of 16 for the duration,
including when it wears armor.

[SRD p. 121, Barkskin](../../references/srd-5.1/pages/page-121.md)

<a id="srd51-spell-beacon-of-hope"></a>

## Beacon of Hope

`srd51.spell.beacon-of-hope`

Level 3 abjuration · 1 action · 30 feet · V, S · Concentration up to 1 minute.

Choose any number of creatures in range. For the duration each gains advantage on Wisdom saves and
death saves. Whenever a target receives healing, use the maximum possible amount from that healing,
subject to the ordinary hit point maximum.

[SRD p. 121, Beacon of Hope](../../references/srd-5.1/pages/page-121.md)

<a id="srd51-spell-bestow-curse"></a>

## Bestow Curse

`srd51.spell.bestow-curse`

Level 3 necromancy · 1 action · Touch · V, S · Concentration up to 1 minute.

Touch a creature and choose a curse. The target acquires it on a failed Wisdom save. Your choices are disadvantage on checks and saves for one ability score you select, disadvantage on attack rolls against you, a Wisdom save at the start of each of its turns that makes it spend its action doing nothing on failure, or an extra 1d8 necrotic damage to it from your attacks and spells. The DM can allow and adjudicate a different curse of no greater power. [Remove Curse](spells-n-s.md#srd51-spell-remove-curse) ends the curse.

At slot 4, concentration lasts up to 10 minutes. Slots 5 and 6 give 8 hours, slots 7 and 8 give 24
hours, and slot 9 lasts until dispelled. Slots 5 through 9 remove the concentration requirement.

[SRD p. 121, Bestow Curse](../../references/srd-5.1/pages/page-121.md) · [SRD p. 174, Remove Curse](../../references/srd-5.1/pages/page-174.md) · [SRD p. 175, Remove Curse continuation](../../references/srd-5.1/pages/page-175.md)

<a id="srd51-spell-black-tentacles"></a>

## Black Tentacles

`srd51.spell.black-tentacles`

Level 4 conjuration · 1 action · 90 feet · V, S, M (part of a giant octopus or giant squid tentacle) · Concentration up to 1 minute.

Fill a 20-foot square of ground you can see in range with tentacles. Its ground is difficult terrain.
A creature entering the area for the first time on a turn or starting its turn there makes a Dexterity
save. Failure deals 3d6 bludgeoning damage and restrains it through the spell's end unless it breaks
free. A creature already restrained by these tentacles takes 3d6 bludgeoning damage for starting its
turn in the area. A restrained creature can use its action to attempt a Strength or Dexterity check of
its choice against your spell save DC, freeing itself on success.

**Project interpretation.** An already-restrained creature starting its turn in the area takes the
single automatic 3d6 hit instead of making a Dexterity save for that start. The ordinary trigger
already covers the start of a turn. Treating the more specific clause as an additional hit could
deal 6d6 at that one instant and would duplicate the ordinary restraint result.

[SRD p. 121, Black Tentacles](../../references/srd-5.1/pages/page-121.md)

<a id="srd51-spell-blade-barrier"></a>

## Blade Barrier

`srd51.spell.blade-barrier`

Level 6 evocation · 1 action · 90 feet · V, S · Concentration up to 10 minutes.

Place a vertical blade wall in range. Choose a straight wall up to 100 feet long or a ring up to 60
feet in diameter. Either is 20 feet high and 5 feet thick. It provides three-quarters cover to
creatures behind it and creates difficult terrain in its own space. Each creature entering that space
for the first time on a turn or starting its turn in it makes a Dexterity save. Failure deals 6d10
slashing damage, and success halves it.

[SRD p. 122, Blade Barrier](../../references/srd-5.1/pages/page-122.md)

<a id="srd51-spell-bless"></a>

## Bless

`srd51.spell.bless`

Level 1 enchantment · 1 action · 30 feet · V, S, M (a sprinkling of holy water) · Concentration up to 1 minute.

Choose up to three creatures in range. Each may roll and add 1d4 to every attack roll or saving throw
it makes while the spell persists. Each slot level above 1 increases the permitted count by one.

[SRD p. 122, Bless](../../references/srd-5.1/pages/page-122.md)

<a id="srd51-spell-blight"></a>

## Blight

`srd51.spell.blight`

Level 4 necromancy · 1 action · 30 feet · V, S · Instantaneous.

Target a creature or plant you can see in range. Undead and constructs are unaffected. Ordinarily a
Constitution save halves 8d8 necrotic damage on success. Plant creatures and magical plants save with
disadvantage and use the maximum possible damage for the relevant outcome. Nonmagical plants that are
not creatures die without a save. Each slot level above 4 adds 1d8 to the damage.

[SRD p. 122, Blight](../../references/srd-5.1/pages/page-122.md)

<a id="srd51-spell-blindness-deafness"></a>

## Blindness/Deafness

`srd51.spell.blindness-deafness`

Level 2 necromancy · 1 action · 30 feet · V · 1 minute.

Choose a visible creature in range. On a failed Constitution save it becomes blinded or deafened, as
you choose, for the duration. At the end of each of its turns it can repeat the Constitution save.
You can target one additional creature per slot level above 2.

**Project interpretation.** Each failed initial save lets you choose that target's condition.
Each successful repeat ends only that target's condition. The singular source clause says that
the spell ends, which settles the one-target case. In a higher-slot casting, applying a creature's
save to its own condition preserves the separate save and outcome for each target. A literal
whole-casting end would also clear other creatures that never passed a save.

[SRD p. 122, Blindness/Deafness](../../references/srd-5.1/pages/page-122.md)

<a id="srd51-spell-blink"></a>

## Blink

`srd51.spell.blink`

Level 3 transmutation · 1 action · Self · V, S · 1 minute.

Casting while already on the Ethereal Plane fails and wastes the casting. At the end of each of your
turns, roll a d20. An 11 or higher takes you from the current plane to the Ethereal Plane. From there
you can see the departure plane in gray tones up to 60 feet and still hear it. You can affect and be
affected only by creatures on the Ethereal Plane. Others cannot perceive or interact with you unless
they possess a capability that permits it.

At the start of your next turn, or on spell expiry while still Ethereal, pick an unoccupied return
space visible to you within 10 feet of your departure space. When no unoccupied space is available in
that range, return to the nearest unoccupied space and randomly resolve any distance tie. You can
dismiss the spell as an action.

[SRD p. 122, Blink](../../references/srd-5.1/pages/page-122.md) · [SRD p. 123, Blink continuation](../../references/srd-5.1/pages/page-123.md) · [SRD p. 363, Ethereal Plane](../../references/srd-5.1/pages/page-363.md)

<a id="srd51-spell-blur"></a>

## Blur

`srd51.spell.blur`

Level 2 illusion · 1 action · Self · V · Concentration up to 1 minute.

Your wavering appearance imposes disadvantage on attack rolls against you. Exempt an attacker that can
operate without using sight, such as one using blindsight, or can see through illusions, such as one
using truesight.

[SRD p. 123, Blur](../../references/srd-5.1/pages/page-123.md)

<a id="srd51-spell-branding-smite"></a>

## Branding Smite

`srd51.spell.branding-smite`

Level 2 evocation · 1 bonus action · Self · V · Concentration up to 1 minute.

Your first weapon hit on a creature before expiry deals 2d6 extra radiant damage. The hit makes an
invisible target visible. Until the spell ends the target sheds dim light within 5 feet of itself and
cannot become invisible. Each slot level above 2 adds 1d6 radiant damage.

[SRD p. 123, Branding Smite](../../references/srd-5.1/pages/page-123.md)

<a id="srd51-spell-burning-hands"></a>

## Burning Hands

`srd51.spell.burning-hands`

Level 1 evocation · 1 action · Self (15-foot cone) · V, S · Instantaneous.

With your thumbs touching and fingers spread, project fire through a 15-foot cone. Every creature in
it makes a Dexterity save against 3d6 fire damage, taking half on success. The flames ignite flammable
objects in the cone that are neither worn nor carried. Add 1d6 damage per slot level above 1.

[SRD p. 123, Burning Hands](../../references/srd-5.1/pages/page-123.md)

<a id="srd51-spell-call-lightning"></a>

## Call Lightning

`srd51.spell.call-lightning`

Level 3 conjuration · 1 action · 120 feet · V, S · Concentration up to 10 minutes.

Form a cloud cylinder 10 feet tall and 60 feet in radius, with its center at a visible point 100 feet
directly overhead. The spell fails if you cannot see a point that can accommodate the cloud. When cast
outdoors in stormy weather, use the existing storm instead of forming a new cloud and add 1d10 to each
bolt.

At casting, strike a point you can see within range. Each creature within 5 feet of the point makes a
Dexterity save against 3d10 lightning damage, with half damage on success. You can spend your action
on each of your turns before expiry to strike the same or another eligible point. Add 1d10 damage per slot
level above 3.

[SRD p. 123, Call Lightning](../../references/srd-5.1/pages/page-123.md)

<a id="srd51-spell-calm-emotions"></a>

## Calm Emotions

`srd51.spell.calm-emotions`

Level 2 enchantment · 1 action · 60 feet · V, S · Concentration up to 1 minute.

Center a 20-foot-radius sphere on a point in range. Each humanoid inside makes a Charisma save and may
elect to fail. For each failure, choose either to suppress effects making it charmed or frightened, or
to make it indifferent toward specified creatures it was hostile toward. Suppressed effects resume at
the end of the spell if their durations remain. Indifference ends early if it is attacked, harmed by a
spell, or sees a friend harmed. On spell expiry its hostility returns unless the DM decides otherwise.

[SRD p. 123, Calm Emotions](../../references/srd-5.1/pages/page-123.md) · [SRD p. 124, Calm Emotions continuation](../../references/srd-5.1/pages/page-124.md)

<a id="srd51-spell-chain-lightning"></a>

## Chain Lightning

`srd51.spell.chain-lightning`

Level 6 evocation · 1 action · 150 feet · V, S, M (a bit of fur, one of a piece of amber, glass, or a crystal rod, and three silver pins) · Instantaneous.

Choose an initial target you can see in range. From there, up to three secondary bolts each strike a
different target within 30 feet of the initial target. A creature or object can be selected for either
role and can receive only one bolt. Each target makes a Dexterity save against 10d8 lightning damage,
halved on success. Every slot level above 6 permits one additional secondary bolt from the initial
target.

[SRD p. 124, Chain Lightning](../../references/srd-5.1/pages/page-124.md)

<a id="srd51-spell-charm-person"></a>

## Charm Person

`srd51.spell.charm-person`

Level 1 enchantment · 1 action · 30 feet · V, S · 1 hour.

Choose a humanoid you can see in range. It makes a Wisdom save with advantage if you or your
companions are fighting it. Failure makes it charmed by you, regarding you as a friendly acquaintance,
until spell expiry or until you or your companions harm it. When the spell ends, it knows you charmed
it. Add one target per slot level above 1. At targeting, each pair of chosen creatures must be at most
30 feet apart.

[SRD p. 124, Charm Person](../../references/srd-5.1/pages/page-124.md)

<a id="srd51-spell-chill-touch"></a>

## Chill Touch

`srd51.spell.chill-touch`

Necromancy cantrip · 1 action · 120 feet · V, S · 1 round.

Create a spectral hand in the space of a creature in range and make a ranged spell attack against it.
On a hit, deal 1d8 necrotic damage. The hand clings to the creature and prevents hit point recovery
until the start of your next turn. If the target is undead, a hit also gives it disadvantage on
attacks against you until the end of your next turn. Damage rises to 2d8, 3d8, and 4d8 at caster
levels 5, 11, and 17 respectively.

[SRD p. 124, Chill Touch](../../references/srd-5.1/pages/page-124.md)

<a id="srd51-spell-circle-of-death"></a>

## Circle of Death

`srd51.spell.circle-of-death`

Level 6 necromancy · 1 action · 150 feet · V, S, M (powder of a crushed black pearl worth at least 500 gp) · Instantaneous.

Center a 60-foot-radius sphere on a point in range. Each creature in it makes a Constitution save
against 8d6 necrotic damage, halving the damage on success. Add 2d6 per slot level above 6.

[SRD p. 124, Circle of Death](../../references/srd-5.1/pages/page-124.md)

<a id="srd51-spell-clairvoyance"></a>

## Clairvoyance

`srd51.spell.clairvoyance`

Level 3 divination · 10 minutes · 1 mile · V, S, M (focus worth at least 100 gp, a jeweled horn for hearing or glass eye for sight) · Concentration up to 10 minutes.

Place a stationary invisible sensor in range somewhere you have previously seen or visited, or in an obvious unseen location such as just behind a door. The sensor cannot be attacked or otherwise interacted with. Choose hearing or sight at casting. Use that sense as if you occupied the sensor's position, and switch between the two modes as an action. A creature able to see the sensor, for example via truesight or [See Invisibility](spells-n-s.md#srd51-spell-see-invisibility), sees a glowing intangible orb about the size of your fist.

[SRD p. 124, Clairvoyance](../../references/srd-5.1/pages/page-124.md) · [SRD p. 125, Clairvoyance continuation](../../references/srd-5.1/pages/page-125.md) · [SRD p. 86, Truesight](../../references/srd-5.1/pages/page-086.md) · [SRD p. 177, See Invisibility](../../references/srd-5.1/pages/page-177.md)

<a id="srd51-spell-clone"></a>

## Clone

`srd51.spell.clone`

Level 8 necromancy · 1 hour · Touch · V, S, M (a diamond worth at least 1,000 gp and at least 1 cubic inch of the living original's flesh, both consumed, plus a vessel worth at least 2,000 gp with a sealable lid and room for a Medium creature) · Instantaneous.

The duplicate forms inside the sealed vessel and reaches full size and maturity in 120 days. You may
choose a younger version. It remains inert indefinitely if the vessel is undisturbed. After maturity,
if the original dies and its soul is free and willing to return, the soul enters the clone. The clone
duplicates the original's physical identity, personality, memories, and abilities, and receives no
equipment from the original. Existing remains of the original become inert and cannot subsequently be
restored to life because the soul has transferred.

[SRD p. 125, Clone](../../references/srd-5.1/pages/page-125.md)

<a id="srd51-spell-cloudkill"></a>

## Cloudkill

`srd51.spell.cloudkill`

Level 5 conjuration · 1 action · 120 feet · V, S · Concentration up to 10 minutes.

Center a poisonous fog sphere with a 20-foot radius on a point in range. It spreads around corners and
heavily obscures its area. Strong wind disperses the fog and ends the spell. When a creature first
enters the fog on a turn or starts its turn there, it makes a Constitution save against 5d8 poison
damage, with half damage on success. Holding its breath or having no need to breathe gives no
exemption.

At the start of each of your turns the fog rolls 10 feet farther from you along the ground. It seeks
the lowest level and can pour down openings. Add 1d8 damage per slot level above 5.

[SRD p. 125, Cloudkill](../../references/srd-5.1/pages/page-125.md)

<a id="srd51-spell-color-spray"></a>

## Color Spray

`srd51.spell.color-spray`

Level 1 illusion · 1 action · Self (15-foot cone) · V, S, M (a pinch of powder or sand colored red, yellow, and blue) · 1 round.

Roll 6d10 as a hit point budget. Exclude unconscious creatures and creatures that cannot see. Process
the remaining creatures in the cone from lowest to highest current hit points. If a creature fits
within the remaining budget, blind it until the spell ends and subtract its current hit points before
proceeding. Once a creature costs more than the balance, none of the remaining creatures can be
affected. Add 2d10 to the initial roll per slot level above 1. **Project interpretation.** The DM orders creatures tied on current hit points before spending
the budget on that tie. The source gives neither the caster nor a random roll priority within
equal hit points. DM adjudication resolves the tie without introducing a new roll or changing
the eligibility of any untied creature.

[SRD p. 125, Color Spray](../../references/srd-5.1/pages/page-125.md)

<a id="srd51-spell-command"></a>

## Command

`srd51.spell.command`

Level 1 enchantment · 1 action · 60 feet · V · 1 round.

Speak a one-word order to a creature you can see in range. An undead target, a target that cannot
understand your language, or a directly harmful order yields no effect. A creature that fails its
Wisdom save obeys on its next turn. If it cannot comply, the spell ends. The DM determines how an
order outside the standard choices plays out.

- **Approach.** Move toward you by the shortest, most direct route and end the turn if the target comes within 5 feet of you.
- **Drop.** Drop everything held and end the turn.
- **Flee.** Spend the turn getting away from you by the fastest available means.
- **Grovel.** Fall prone and end the turn.
- **Halt.** Forgo movement and actions. A flyer stays in the air if it can, flying the minimum distance necessary to avoid falling.

Add one creature per slot level above 1. At selection, every pair of targets must be within 30 feet of
one another.

[SRD p. 125, Command](../../references/srd-5.1/pages/page-125.md) · [SRD p. 126, Command continuation](../../references/srd-5.1/pages/page-126.md)

<a id="srd51-spell-commune"></a>

## Commune

`srd51.spell.commune`

Level 5 divination (ritual) · 1 minute · Self · V, S, M (incense and a vial of holy or unholy water) · 1 minute.

Contact your deity or a divine representative. Ask up to three yes-or-no questions before expiry.
Answers are correct. A matter outside the deity's knowledge can yield an unclear answer. If a single
word would mislead or run against the deity's interests, the DM may reply with a short phrase. Every
casting after the first before your next long rest adds 25 percentage points to the chance of
receiving no answer. The DM rolls secretly.

[SRD p. 126, Commune](../../references/srd-5.1/pages/page-126.md)

<a id="srd51-spell-commune-with-nature"></a>

## Commune with Nature

`srd51.spell.commune-with-nature`

Level 5 divination (ritual) · 1 minute · Self · V, S · Instantaneous.

Learn up to three facts of your choice about the surroundings. Outdoors the reach is 3 miles. A cave
or similar natural underground site gives a 300-foot radius. The spell fails where construction has
displaced nature, including towns and dungeons. Choose among information about the landscape and waterways, common inhabitants or natural
resources (people, animals, plants, or minerals), buildings, planar influence, and the presence of
powerful beings of the celestial, elemental, fey, fiend, or undead type.

[SRD p. 126, Commune with Nature](../../references/srd-5.1/pages/page-126.md)

<a id="srd51-spell-comprehend-languages"></a>

## Comprehend Languages

`srd51.spell.comprehend-languages`

Level 1 divination (ritual) · 1 action · Self · V, S, M (a pinch of soot and salt) · 1 hour.

You understand the literal meaning of spoken language you hear and written language you see while
touching its surface. Reading takes about 1 minute per page. The spell does not decode a hidden
message or interpret a glyph outside a written language.

[SRD p. 126, Comprehend Languages](../../references/srd-5.1/pages/page-126.md)

<a id="srd51-spell-compulsion"></a>

## Compulsion

`srd51.spell.compulsion`

Level 4 enchantment · 1 action · 30 feet · V, S · Concentration up to 1 minute.

Choose visible creatures in range that can hear you. Each makes a Wisdom save. Creatures that cannot
be charmed succeed automatically. On each of your turns you can spend a bonus action to designate a
horizontal direction relative to you. On its next turn, each creature still affected by its failed
save must spend as much movement as possible traveling that way. It may act before moving and repeat
the Wisdom save after moving to end its effect on success. Compelled movement can provoke opportunity
attacks. A creature may avoid entering an obviously deadly hazard.

[SRD p. 126, Compulsion](../../references/srd-5.1/pages/page-126.md) · [SRD p. 127, Compulsion continuation](../../references/srd-5.1/pages/page-127.md)

<a id="srd51-spell-cone-of-cold"></a>

## Cone of Cold

`srd51.spell.cone-of-cold`

Level 5 evocation · 1 action · Self (60-foot cone) · V, S, M (a small crystal or glass cone) · Instantaneous.

Each creature in the cone makes a Constitution save against 8d8 cold damage, halving damage on
success. A creature killed by this spell stays a frozen statue until it thaws. Add 1d8 damage per slot
level above 5.

[SRD p. 127, Cone of Cold](../../references/srd-5.1/pages/page-127.md)

<a id="srd51-spell-confusion"></a>

## Confusion

`srd51.spell.confusion`

Level 4 enchantment · 1 action · 90 feet · V, S, M (three nut shells) · Concentration up to 1 minute.

Choose a point in range for a 10-foot-radius sphere. At casting, every creature inside makes a Wisdom
save. Failure suppresses reactions for as long as the creature is affected. At the start of each of
its turns it rolls a d10 to choose that turn's behavior.

- **1.** Use all movement in a random direction, determined by assigning eight directions to a d8 and rolling it. Take no action.
- **2 through 6.** Stay still and take no actions.
- **7 or 8.** Use the action for a melee attack on a random creature within reach. Do nothing that turn if reach contains no creature.
- **9 or 10.** Move and act normally for the turn, with the spell's separate reaction prohibition still in effect.

At the end of each of its turns, an affected target may repeat the Wisdom save, ending the effect on
itself if successful. The sphere gains 5 feet of radius per slot level above 4.

[SRD p. 127, Confusion](../../references/srd-5.1/pages/page-127.md)

<a id="srd51-spell-conjure-animals"></a>

## Conjure Animals

`srd51.spell.conjure-animals`

Level 3 conjuration · 1 action · 60 feet · V, S · Concentration up to 1 hour.

Fey spirits in beast form appear in unoccupied spaces you can see in range and count as fey as well as
beasts. Select a group size of 1, 2, 4, or 8, with respective maximum CR per beast of 2, 1, 1/2, or
1/4. The DM has their statistics. Each disappears at 0 HP or spell end. They are friendly toward you
and your companions. Roll group initiative and give the group its own turns. They obey your verbal
commands without an action cost to you. In the absence of orders they only defend themselves against
hostile creatures.

Slots 5, 7, and 9 respectively double, triple, and quadruple the selected group size while keeping
that option's CR cap.

See the [shared conjuration choices](#srd51-spell-conjuration-choices) for selecting the
specific creatures and reading listed slot levels.

[SRD p. 127, Conjure Animals](../../references/srd-5.1/pages/page-127.md)

<a id="srd51-spell-conjure-celestial"></a>

## Conjure Celestial

`srd51.spell.conjure-celestial`

Level 7 conjuration · 1 minute · 90 feet · V, S · Concentration up to 1 hour.

Summon one celestial of CR 4 or lower to an unoccupied space you can see in range. It is friendly to
you and your companions. The DM has its statistics. It rolls its own initiative, takes its own turns,
and follows your verbal commands that do not violate its alignment at no action cost to you. Without
commands it only defends itself against hostile creatures. It disappears at 0 HP or spell end. Slot 9
raises the CR limit to 5.

See the [shared conjuration choices](#srd51-spell-conjuration-choices) for selecting the
specific creatures and reading listed slot levels.

[SRD p. 127, Conjure Celestial](../../references/srd-5.1/pages/page-127.md) · [SRD p. 128, Conjure Celestial continuation](../../references/srd-5.1/pages/page-128.md)

<a id="srd51-spell-conjure-elemental"></a>

## Conjure Elemental

`srd51.spell.conjure-elemental`

Level 5 conjuration · 1 minute · 90 feet · V, S, M (burning incense for air, soft clay for earth, sulfur and phosphorus for fire, or water and sand for water) · Concentration up to 1 hour.

Identify an area within range where a 10-foot cube is occupied by air, earth, fire, or water.
Summon an elemental of that kind, CR 5 or lower, into an unoccupied space within 10 feet of the source.
The DM has its statistics. It is friendly to you and your companions, rolls its own initiative, takes
its own turns, and obeys your verbal commands at no action cost. Without orders it only defends itself
against hostile creatures.

Normally the elemental disappears at 0 HP or spell end. If concentration is broken, the still-present
elemental instead becomes uncontrolled and hostile toward you and your companions, and might attack.
You cannot dismiss an uncontrolled elemental, which disappears 1 hour after summoning. Each slot level
above 5 raises the CR cap by one.

See the [shared conjuration choices](#srd51-spell-conjuration-choices) for selecting the
specific creatures and reading listed slot levels.

[SRD p. 128, Conjure Elemental](../../references/srd-5.1/pages/page-128.md)

<a id="srd51-spell-conjure-fey"></a>

## Conjure Fey

`srd51.spell.conjure-fey`

Level 6 conjuration · 1 minute · 90 feet · V, S · Concentration up to 1 hour.

Summon one fey or a fey spirit in beast form, CR 6 or lower either way, to an unoccupied space you can
see in range. The DM has its statistics. It is friendly to you and your companions, rolls its own
initiative, takes its own turns, and obeys your verbal commands at no action cost provided they
respect its alignment. Without orders it only defends itself against hostile creatures.

Normally it disappears at 0 HP or spell end. If concentration is broken, the still-present creature
instead becomes uncontrolled and hostile toward you and your companions, and might attack. You cannot
dismiss it while uncontrolled, and it disappears 1 hour after summoning. Each slot level above 6
raises the CR cap by one.

See the [shared conjuration choices](#srd51-spell-conjuration-choices) for selecting the
specific creatures and reading listed slot levels.

[SRD p. 128, Conjure Fey](../../references/srd-5.1/pages/page-128.md)

<a id="srd51-spell-conjure-minor-elementals"></a>

## Conjure Minor Elementals

`srd51.spell.conjure-minor-elementals`

Level 4 conjuration · 1 minute · 90 feet · V, S · Concentration up to 1 hour.

Elementals appear in unoccupied spaces you can see in range. Select a group size of 1, 2, 4, or 8,
with a respective per-elemental CR cap of 2, 1, 1/2, or 1/4. The DM has their statistics. Each
disappears at 0 HP or spell end. They are friendly to you and your companions. Roll a group initiative
and give the group its own turns. Your verbal orders require no action from you. Without orders the
elementals only defend themselves against hostile creatures.

Slots 6 and 8 respectively double and triple the chosen count without changing the option's CR cap.

See the [shared conjuration choices](#srd51-spell-conjuration-choices) for selecting the
specific creatures and reading listed slot levels.

[SRD p. 128, Conjure Minor Elementals](../../references/srd-5.1/pages/page-128.md) · [SRD p. 129, Conjure Minor Elementals continuation](../../references/srd-5.1/pages/page-129.md)

<a id="srd51-spell-conjure-woodland-beings"></a>

## Conjure Woodland Beings

`srd51.spell.conjure-woodland-beings`

Level 4 conjuration · 1 action · 60 feet · V, S, M (one holly berry per creature summoned) · Concentration up to 1 hour.

Fey appear in unoccupied spaces you can see in range. Select a group size of 1, 2, 4, or 8, with a
respective per-fey CR cap of 2, 1, 1/2, or 1/4. The DM has their statistics. Each disappears at 0 HP
or spell end. They are friendly to you and your companions. Roll group initiative and give the group
its own turns. They obey verbal orders at no action cost to you. Without orders they only defend
themselves against hostile creatures.

Slots 6 and 8 respectively double and triple the selected count without increasing the option's CR
cap.

See the [shared conjuration choices](#srd51-spell-conjuration-choices) for selecting the
specific creatures and reading listed slot levels.

[SRD p. 129, Conjure Woodland Beings](../../references/srd-5.1/pages/page-129.md)

<a id="srd51-spell-conjuration-choices"></a>

### Shared conjuration choices

`srd51.spell.conjuration-choices`

**Project interpretation.** The caster chooses a listed quantity and CR option or elemental
source where the entry grants that choice. For Conjure Fey the caster chooses fey or fey spirit
in beast form. The DM selects the specific creatures that fit the option. The source gives the
DM the statistics and grants the caster particular summoning options, but never explicitly
assigns stat-block selection. This reading keeps the granted choices and uses the DM's role to
resolve the remaining identity. Letting the caster name the stat block would extend the choice
beyond the options actually specified.

**Project interpretation.** For the quantity multipliers of Conjure Animals, Conjure Minor
Elementals, and Conjure Woodland Beings, apply only the named slots. For example, a 6th-level
Conjure Animals uses its ordinary quantity. The source describes an increase with certain
higher slots and then names exact slot levels. Treating those levels as thresholds would give
an unstated multiplier to intervening slots. This differs from Conjure Elemental and Conjure
Fey, whose CR increases expressly apply to every slot above their base levels.

[SRD p. 127, Conjure Animals and Conjure Celestial](../../references/srd-5.1/pages/page-127.md) · [SRD p. 128, Conjure Celestial continuation, Conjure Elemental, Conjure Fey, and Conjure Minor Elementals](../../references/srd-5.1/pages/page-128.md) · [SRD p. 129, Conjure Minor Elementals continuation and Conjure Woodland Beings](../../references/srd-5.1/pages/page-129.md).

<a id="srd51-spell-contact-other-plane"></a>

## Contact Other Plane

`srd51.spell.contact-other-plane`

Level 5 divination (ritual) · 1 minute · Self · V · 1 minute.

Reach out mentally to an intelligence on another plane. On casting, make a DC 15 Intelligence save. Failure deals 6d6 psychic damage and imposes insanity until you finish a long rest or receive [Greater Restoration](spells-e-g.md#srd51-spell-greater-restoration). This insanity prevents taking actions, understanding speech, and reading, and limits your speech to gibberish.

On success you may ask up to five questions before the spell ends. The DM replies with one word per
question, including an unclear answer for information the entity lacks. A short phrase is permitted if
a one-word reply would mislead.

[SRD p. 129, Contact Other Plane](../../references/srd-5.1/pages/page-129.md) · [SRD p. 150, Greater Restoration](../../references/srd-5.1/pages/page-150.md)

<a id="srd51-spell-contagion"></a>

## Contagion

`srd51.spell.contagion`

Level 5 necromancy · 1 action · Touch · V, S · 7 days.

Make a melee spell attack on a creature within your reach. On a hit, afflict it with one listed
disease of your choice. It must make a Constitution save at the end of each of its turns. Upon three
failures the disease lasts for the duration and saves stop. Upon three successes the target recovers
and the spell ends. These are counts, with no requirement of consecutive outcomes. Disease removal and
amelioration apply because the induced disease is natural.

- **Blinding Sickness.** Blinded and disadvantaged on Wisdom checks and saves.
- **Filth Fever.** Disadvantage on Strength checks, Strength saves, and Strength-based attack rolls.
- **Flesh Rot.** Disadvantage on Charisma checks and vulnerability to all damage.
- **Mindfire.** Disadvantage on Intelligence checks and saves. In combat it behaves as under [Confusion](#srd51-spell-confusion).
- **Seizure.** Disadvantage on Dexterity checks, Dexterity saves, and Dexterity-based attack rolls.
- **Slimy Doom.** Disadvantage on Constitution checks and saves. Any damage it takes stuns it until the end of its next turn.

**Project interpretation.** Mindfire imports Confusion's combat behavior, including its reaction
restriction and start-of-turn d10, but does not run a separate Confusion effect. The reference
says how the creature behaves during combat. Treating Confusion's repeat Wisdom save as a cure
or suppression would introduce an additional recovery route absent from Contagion. The disease
still uses its own Constitution saves and disease-removal rules.

[SRD p. 129, Contagion](../../references/srd-5.1/pages/page-129.md) · [SRD p. 130, Contagion continuation](../../references/srd-5.1/pages/page-130.md) · [SRD p. 127, Confusion](../../references/srd-5.1/pages/page-127.md)

<a id="srd51-spell-contingency"></a>

## Contingency

`srd51.spell.contingency`

Level 6 evocation · 10 minutes · Self · V, S, M (an ivory statuette of yourself decorated with gems, worth at least 1,500 gp) · 10 days.

Choose a spell you can cast of level 5 or lower that normally takes 1 action and can target you. Cast
it as part of Contingency and expend both slots. Define a triggering circumstance during casting. The
chosen spell is deferred. Immediately after the first occurrence of the circumstance, it takes effect
on you alone even if it could normally include others and even if you would prefer to suppress it.
Contingency then ends.

Recasting Contingency ends any previous one on you. It also ends if you stop carrying the statuette on
your person or the duration expires.

**Project interpretation.** The long casting follows the ordinary concentration rule for casting
times longer than one action. After completion, the dormant contingent spell requires no
concentration. A contingent concentration spell starts its own concentration when triggered,
ending any conflicting concentration then. The contingent spell takes effect at the trigger,
so concentrating throughout the dormant interval would impose a cost before its effect exists.
A caster who cannot concentrate at the trigger cannot keep the contingent effect active.

[SRD p. 130, Contingency](../../references/srd-5.1/pages/page-130.md) · [SRD p. 101, Longer Casting Times](../../references/srd-5.1/pages/page-101.md) · [SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md)

<a id="srd51-spell-continual-flame"></a>

## Continual Flame

`srd51.spell.continual-flame`

Level 2 evocation · 1 action · Touch · V, S, M (ruby dust worth 50 gp, consumed) · Until dispelled.

An object you touch gains [torch](adventuring-gear.md#srd51-equipment-torch) brightness, with bright light in a 20-foot radius and dim light for 20 feet farther. It gives no heat and needs no oxygen. You can
cover or conceal it, and smothering or quenching cannot put it out.

[SRD p. 130, Continual Flame](../../references/srd-5.1/pages/page-130.md) · [SRD p. 68, Torch](../../references/srd-5.1/pages/page-068.md)

<a id="srd51-spell-control-water"></a>

## Control Water

`srd51.spell.control-water`

Level 4 transmutation · 1 action · 300 feet · V, S, M (a drop of water and a pinch of dust) · Concentration up to 10 minutes.

Choose a cube up to 100 feet per side to control its freestanding water. Select a mode at casting. On
your turn you may spend an action to repeat it or choose another mode.

- **Flood.** Raise standing water in the area by up to 20 feet, spilling onto dry land where there is shore. In a large body of water the effect instead produces a 20-foot wave that crosses the area and crashes. Huge or smaller vehicles in its path travel with it to the opposite side, and each Huge or smaller vehicle it strikes has a 25 percent chance to capsize. Raised levels last until expiry or a switch of mode. A wave repeats at the start of your next turn while Flood is in effect.
- **Part Water.** Create a trench across the area between walls of parted water. It persists until spell end or a mode switch. Over the following round, water refills the trench and returns to the normal level.
- **Redirect Flow.** Direct flowing water within the cube as you choose, even over barriers or up walls. Its flow outside follows local terrain. The redirection lasts until spell end or a mode switch.
- **Whirlpool.** Requires water at least 50 feet square and 25 feet deep. A vortex centered in the area is 25 feet tall, 5 feet wide at its base, and up to 50 feet wide at its top. It pulls creatures and objects in the water within 25 feet of it 10 feet toward it. A creature can swim away by succeeding on a Strength (Athletics) check against your spell save DC. On first entry each turn or on starting its turn in the vortex, a creature makes a Strength save. Failure deals 2d8 bludgeoning damage and traps it in the vortex until spell end unless it escapes. Success halves damage and does not trap it. A trapped creature may use its action to try the swim-away check with disadvantage. An object takes 2d8 bludgeoning damage the first time it enters on a turn and each round it remains.

**Project interpretation.** Apply the whirlpool's pull when you select that mode and at the
start of each of your later turns while it persists. The source gives the affected objects and
creatures the same pull and supplies no initiative for objects, so this uses the caster's turn
as a common clock instead of inventing object turns or continuously pulling during movement.
For an object already in the vortex at the start of your turn, apply its per-round 2d8 if it
has remained there since its entry or previous per-round hit. A newly pulled-in object uses the
entry damage trigger. This preserves the separate first-entry-on-a-turn and continuing damage
clauses without treating the same entry as both.

[SRD p. 130, Control Water](../../references/srd-5.1/pages/page-130.md) · [SRD p. 131, Control Water continuation](../../references/srd-5.1/pages/page-131.md)

<a id="srd51-spell-control-weather"></a>

## Control Weather

`srd51.spell.control-weather`

Level 8 transmutation · 10 minutes · Self (5-mile radius) · V, S, M (burning incense and bits of earth and wood mixed in water) · Concentration up to 8 hours.

Cast outdoors to control weather within 5 miles of you. The DM determines the initial weather from
climate and season. At casting, you may change precipitation, temperature, or wind. Move each changed
condition by one stage in either direction from its current stage. You can also redirect the wind when
changing it. Each change takes 1d4 × 10 minutes to take hold. Once it does, you may change conditions
again. Going somewhere without a clear path to the sky ends the spell early. At spell end the weather
gradually returns to normal.

Each sequence below runs from stage 1 upward. Adjacent entries are one step apart.

- **Precipitation.** Clear → light clouds → overcast or ground fog → rain, hail, or snow → torrential rain, driving hail, or blizzard.
- **Temperature.** Unbearable heat → hot → warm → cool → cold → arctic cold.
- **Wind.** Calm → moderate wind → strong wind → gale → storm.

[SRD p. 131, Control Weather](../../references/srd-5.1/pages/page-131.md)

<a id="srd51-spell-counterspell"></a>

## Counterspell

`srd51.spell.counterspell`

Level 3 abjuration · 1 reaction when you see a creature within 60 feet casting a spell · 60 feet · S · Instantaneous.

Interrupt the casting in progress. If the opposing spell's level is no higher than the slot used for
Counterspell, that spell fails automatically and has no effect. Otherwise make an ability check with
your spellcasting ability against DC 10 plus the opposing spell's level. Success makes that spell fail
without effect.

[SRD p. 131, Counterspell](../../references/srd-5.1/pages/page-131.md)

<a id="srd51-spell-create-food-and-water"></a>

## Create Food and Water

`srd51.spell.create-food-and-water`

Level 3 conjuration · 1 action · 30 feet · V, S · Instantaneous.

The spell supplies enough for 15 humanoids or 5 steeds for 24 hours. It produces 30 gallons of
water and 45 pounds of food. Place them on the ground or in containers you choose in range. The food is nourishing though bland and spoils
after 24 hours. The clean water does not spoil.

[SRD p. 131, Create Food and Water](../../references/srd-5.1/pages/page-131.md)

<a id="srd51-spell-create-or-destroy-water"></a>

## Create or Destroy Water

`srd51.spell.create-or-destroy-water`

Level 1 transmutation · 1 action · 30 feet · V, S, M (a drop of water to create, or a few grains of sand to destroy) · Instantaneous.

Choose creation or destruction. Creation makes up to 10 gallons of clean water in an open container in
range, or rain in a 30-foot cube in range that puts out exposed flames. Destruction removes up to 10
gallons of water from an open container in range, or clears fog in a 30-foot cube in range. For each
slot level above 1, add 10 gallons in container mode or 5 feet to each edge in cube mode.

[SRD p. 132, Create or Destroy Water](../../references/srd-5.1/pages/page-132.md)

<a id="srd51-spell-create-undead"></a>

## Create Undead

`srd51.spell.create-undead`

Level 6 necromancy · 1 minute · 10 feet · V, S, M (a clay pot of grave dirt, a clay pot of brackish water, and a 150 gp black onyx stone for each corpse) · Instantaneous.

Cast only at night. At slot 6, animate up to three Medium or Small humanoid corpses in range into
ghouls. The DM has their statistics. On each of your turns, a bonus action lets you mentally command
any or all the creatures this spell animated that are within 120 feet of you. Issue the same order to
all recipients, choosing their next-turn actions and movement or assigning a general task they pursue
until complete. Without an order they only defend themselves from hostile creatures.

Control expires after 24 hours and previous orders stop binding the undead. Before expiry, you can
recast on existing servants to renew their control for another 24 hours. The renewal substitutes for
animation. The slot determines the casting's permitted count and kind.

- **Slot 6.** Up to three [ghouls](creatures-d-m.md#srd51-creature-ghoul).
- **Slot 7.** Up to four ghouls.
- **Slot 8.** Up to five ghouls or two [ghasts](creatures-d-m.md#srd51-creature-ghast) or [wights](creatures-m-z.md#srd51-creature-wight).
- **Slot 9.** Up to six ghouls, three ghasts or wights, or two [mummies](creatures-m-z.md#srd51-creature-mummy).

[SRD p. 132, Create Undead](../../references/srd-5.1/pages/page-132.md) · [SRD p. 311, Ghast](../../references/srd-5.1/pages/page-311.md) · [SRD p. 312, Ghoul](../../references/srd-5.1/pages/page-312.md) · [SRD p. 333, Mummy](../../references/srd-5.1/pages/page-333.md) · [SRD p. 354, Wight](../../references/srd-5.1/pages/page-354.md)

<a id="srd51-spell-creation"></a>

## Creation

`srd51.spell.creation`

Level 5 illusion · 1 minute · 30 feet · V, S, M (a tiny piece of the same kind of matter as the intended object) · Special duration.

Draw on Shadowfell matter to create a nonliving object in range made of plant matter or mineral
material. You must have seen both the form and the material before. The size limit is a cube with
5-foot edges at slot 5, increasing by 5 feet per slot level above 5. Any other spell using the created
matter as a material component fails.

Use the shortest duration among the materials present. Plant matter lasts 1 day. Stone or crystal
lasts 12 hours, precious metal 1 hour, gems 10 minutes, and adamantine or mithral 1 minute.

[SRD p. 132, Creation](../../references/srd-5.1/pages/page-132.md)

<a id="srd51-spell-cure-wounds"></a>

## Cure Wounds

`srd51.spell.cure-wounds`

Level 1 evocation · 1 action · Touch · V, S · Instantaneous.

A creature you touch regains 1d8 plus your spellcasting ability modifier in hit points. Undead and
constructs gain no benefit. Add 1d8 healing per slot level above 1.

[SRD p. 132, Cure Wounds](../../references/srd-5.1/pages/page-132.md) · [SRD p. 133, Cure Wounds continuation](../../references/srd-5.1/pages/page-133.md)

## Examples

- A creature starts its turn still restrained by Black Tentacles while in the square. Under the start-of-turn interpretation it takes 3d6 bludgeoning damage automatically and then may spend its action on the escape check. It does not also roll the ordinary Dexterity save for that start. [SRD p. 121, Black Tentacles](../../references/srd-5.1/pages/page-121.md)
- A slot-3 Acid Arrow that misses applies half of its 5d4 initial acid damage and no delayed damage. [SRD p. 114, Acid Arrow](../../references/srd-5.1/pages/page-114.md)
- Acid Splash can select a pair exactly 5 feet apart when both are in range. If either is 61 feet from the caster, the pair is ineligible even when both stand together. [SRD p. 114, Acid Splash](../../references/srd-5.1/pages/page-114.md)
- A creature with 12 of 20 hit points goes to 22 of 30 when it receives Aid from a 3rd-level slot. If it later takes 7 damage and receives no other hit point changes, it has 5 of 20 when the spell ends under the expiry interpretation. [SRD p. 114, Aid](../../references/srd-5.1/pages/page-114.md)
- A Huge and a Medium unattended nonmagical object exactly use the slot-5 Animate Objects budget. Adding one Tiny object would exceed that budget. [SRD p. 116, Animate Objects and Animated Object Statistics](../../references/srd-5.1/pages/page-116.md)
- A creature native to the caster’s plane returns when Banishment ends at the full minute. One native elsewhere remains on its own plane after the full minute, but would return after an end 30 seconds into the effect. [SRD p. 120, Banishment](../../references/srd-5.1/pages/page-120.md) · [SRD p. 121, Banishment continuation](../../references/srd-5.1/pages/page-121.md)
- A beast whose Intelligence is exactly 4 cannot be charmed by Animal Friendship. A beast with Intelligence 3 can still resist with its Wisdom save. [SRD p. 115, Animal Friendship](../../references/srd-5.1/pages/page-115.md)
- A slot-3 Counterspell interrupts a visible creature’s slot-4 spell within 60 feet only if its spellcasting ability check reaches DC 14. A slot-4 Counterspell interrupts that same casting automatically. [SRD p. 100, Casting a Spell at a Higher Level](../../references/srd-5.1/pages/page-100.md) · [SRD p. 131, Counterspell](../../references/srd-5.1/pages/page-131.md)
- A concentration break 10 minutes after Conjure Elemental makes the elemental hostile and uncontrolled. If it survives, it has 50 minutes left before it disappears. [SRD p. 128, Conjure Elemental](../../references/srd-5.1/pages/page-128.md)

## Source coverage

The descriptions on pp. 114–132 and the Cure Wounds continuation on p. 133 are covered here. Animated
Object Statistics belongs to Animate Objects, and the precipitation, temperature, and wind scales
belong to Control Weather. The page 114 heading “Spell Descriptions” introduces the catalogue.
Narrative examples and cosmetic descriptions with no rule effect are omitted.

Checked the local PDF against the text for the Animal Friendship and Animal Messenger scaling lines
(p. 115), Animated Object Statistics (p. 116), Arcane Hand and Antipathy/Sympathy branches (p. 118),
Confusion across both columns (p. 127), Contagion (pp. 129–130), Control Weather’s three scales (p.
131), and Creation’s material durations (p. 132).

| Page | Source entries and destination IDs |
| --- | --- |
| [114](../../references/srd-5.1/pages/page-114.md) | Acid Arrow → [`srd51.spell.acid-arrow`](#srd51-spell-acid-arrow)<br>Acid Splash → [`srd51.spell.acid-splash`](#srd51-spell-acid-splash)<br>Aid → [`srd51.spell.aid`](#srd51-spell-aid)<br>Alarm → [`srd51.spell.alarm`](#srd51-spell-alarm)<br>Alter Self → [`srd51.spell.alter-self`](#srd51-spell-alter-self) |
| [115](../../references/srd-5.1/pages/page-115.md) | Alter Self continuation → [`srd51.spell.alter-self`](#srd51-spell-alter-self)<br>Animal Friendship → [`srd51.spell.animal-friendship`](#srd51-spell-animal-friendship)<br>Animal Messenger → [`srd51.spell.animal-messenger`](#srd51-spell-animal-messenger)<br>Animal Shapes → [`srd51.spell.animal-shapes`](#srd51-spell-animal-shapes)<br>Animate Dead → [`srd51.spell.animate-dead`](#srd51-spell-animate-dead) |
| [116](../../references/srd-5.1/pages/page-116.md) | Animate Dead continuation → [`srd51.spell.animate-dead`](#srd51-spell-animate-dead)<br>Animate Objects → [`srd51.spell.animate-objects`](#srd51-spell-animate-objects)<br>Antilife Shell → [`srd51.spell.antilife-shell`](#srd51-spell-antilife-shell) |
| [117](../../references/srd-5.1/pages/page-117.md) | Antilife Shell continuation → [`srd51.spell.antilife-shell`](#srd51-spell-antilife-shell)<br>Antimagic Field → [`srd51.spell.antimagic-field`](#srd51-spell-antimagic-field)<br>Antipathy/Sympathy → [`srd51.spell.antipathy-sympathy`](#srd51-spell-antipathy-sympathy) |
| [118](../../references/srd-5.1/pages/page-118.md) | Antipathy/Sympathy continuation → [`srd51.spell.antipathy-sympathy`](#srd51-spell-antipathy-sympathy)<br>Arcane Eye → [`srd51.spell.arcane-eye`](#srd51-spell-arcane-eye)<br>Arcane Hand → [`srd51.spell.arcane-hand`](#srd51-spell-arcane-hand)<br>Arcane Lock → [`srd51.spell.arcane-lock`](#srd51-spell-arcane-lock) |
| [119](../../references/srd-5.1/pages/page-119.md) | Arcane Lock continuation → [`srd51.spell.arcane-lock`](#srd51-spell-arcane-lock)<br>Arcane Sword → [`srd51.spell.arcane-sword`](#srd51-spell-arcane-sword)<br>Arcanist's Magic Aura → [`srd51.spell.arcanists-magic-aura`](#srd51-spell-arcanists-magic-aura)<br>Astral Projection → [`srd51.spell.astral-projection`](#srd51-spell-astral-projection) |
| [120](../../references/srd-5.1/pages/page-120.md) | Astral Projection continuation → [`srd51.spell.astral-projection`](#srd51-spell-astral-projection)<br>Augury → [`srd51.spell.augury`](#srd51-spell-augury)<br>Awaken → [`srd51.spell.awaken`](#srd51-spell-awaken)<br>Bane → [`srd51.spell.bane`](#srd51-spell-bane)<br>Banishment → [`srd51.spell.banishment`](#srd51-spell-banishment) |
| [121](../../references/srd-5.1/pages/page-121.md) | Banishment continuation → [`srd51.spell.banishment`](#srd51-spell-banishment)<br>Barkskin → [`srd51.spell.barkskin`](#srd51-spell-barkskin)<br>Beacon of Hope → [`srd51.spell.beacon-of-hope`](#srd51-spell-beacon-of-hope)<br>Bestow Curse → [`srd51.spell.bestow-curse`](#srd51-spell-bestow-curse)<br>Black Tentacles → [`srd51.spell.black-tentacles`](#srd51-spell-black-tentacles) |
| [122](../../references/srd-5.1/pages/page-122.md) | Blade Barrier → [`srd51.spell.blade-barrier`](#srd51-spell-blade-barrier)<br>Bless → [`srd51.spell.bless`](#srd51-spell-bless)<br>Blight → [`srd51.spell.blight`](#srd51-spell-blight)<br>Blindness/Deafness → [`srd51.spell.blindness-deafness`](#srd51-spell-blindness-deafness)<br>Blink → [`srd51.spell.blink`](#srd51-spell-blink) |
| [123](../../references/srd-5.1/pages/page-123.md) | Blink continuation → [`srd51.spell.blink`](#srd51-spell-blink)<br>Blur → [`srd51.spell.blur`](#srd51-spell-blur)<br>Branding Smite → [`srd51.spell.branding-smite`](#srd51-spell-branding-smite)<br>Burning Hands → [`srd51.spell.burning-hands`](#srd51-spell-burning-hands)<br>Call Lightning → [`srd51.spell.call-lightning`](#srd51-spell-call-lightning)<br>Calm Emotions → [`srd51.spell.calm-emotions`](#srd51-spell-calm-emotions) |
| [124](../../references/srd-5.1/pages/page-124.md) | Calm Emotions continuation → [`srd51.spell.calm-emotions`](#srd51-spell-calm-emotions)<br>Chain Lightning → [`srd51.spell.chain-lightning`](#srd51-spell-chain-lightning)<br>Charm Person → [`srd51.spell.charm-person`](#srd51-spell-charm-person)<br>Chill Touch → [`srd51.spell.chill-touch`](#srd51-spell-chill-touch)<br>Circle of Death → [`srd51.spell.circle-of-death`](#srd51-spell-circle-of-death)<br>Clairvoyance → [`srd51.spell.clairvoyance`](#srd51-spell-clairvoyance) |
| [125](../../references/srd-5.1/pages/page-125.md) | Clairvoyance continuation → [`srd51.spell.clairvoyance`](#srd51-spell-clairvoyance)<br>Clone → [`srd51.spell.clone`](#srd51-spell-clone)<br>Cloudkill → [`srd51.spell.cloudkill`](#srd51-spell-cloudkill)<br>Color Spray → [`srd51.spell.color-spray`](#srd51-spell-color-spray)<br>Command → [`srd51.spell.command`](#srd51-spell-command) |
| [126](../../references/srd-5.1/pages/page-126.md) | Command continuation → [`srd51.spell.command`](#srd51-spell-command)<br>Commune → [`srd51.spell.commune`](#srd51-spell-commune)<br>Commune with Nature → [`srd51.spell.commune-with-nature`](#srd51-spell-commune-with-nature)<br>Comprehend Languages → [`srd51.spell.comprehend-languages`](#srd51-spell-comprehend-languages)<br>Compulsion → [`srd51.spell.compulsion`](#srd51-spell-compulsion) |
| [127](../../references/srd-5.1/pages/page-127.md) | Compulsion continuation → [`srd51.spell.compulsion`](#srd51-spell-compulsion)<br>Cone of Cold → [`srd51.spell.cone-of-cold`](#srd51-spell-cone-of-cold)<br>Confusion → [`srd51.spell.confusion`](#srd51-spell-confusion)<br>Conjure Animals → [`srd51.spell.conjure-animals`](#srd51-spell-conjure-animals)<br>Conjure Celestial → [`srd51.spell.conjure-celestial`](#srd51-spell-conjure-celestial) |
| [128](../../references/srd-5.1/pages/page-128.md) | Conjure Celestial continuation → [`srd51.spell.conjure-celestial`](#srd51-spell-conjure-celestial)<br>Conjure Elemental → [`srd51.spell.conjure-elemental`](#srd51-spell-conjure-elemental)<br>Conjure Fey → [`srd51.spell.conjure-fey`](#srd51-spell-conjure-fey)<br>Conjure Minor Elementals → [`srd51.spell.conjure-minor-elementals`](#srd51-spell-conjure-minor-elementals) |
| [129](../../references/srd-5.1/pages/page-129.md) | Conjure Minor Elementals continuation → [`srd51.spell.conjure-minor-elementals`](#srd51-spell-conjure-minor-elementals)<br>Conjure Woodland Beings → [`srd51.spell.conjure-woodland-beings`](#srd51-spell-conjure-woodland-beings)<br>Contact Other Plane → [`srd51.spell.contact-other-plane`](#srd51-spell-contact-other-plane)<br>Contagion → [`srd51.spell.contagion`](#srd51-spell-contagion) |
| [130](../../references/srd-5.1/pages/page-130.md) | Contagion continuation → [`srd51.spell.contagion`](#srd51-spell-contagion)<br>Contingency → [`srd51.spell.contingency`](#srd51-spell-contingency)<br>Continual Flame → [`srd51.spell.continual-flame`](#srd51-spell-continual-flame)<br>Control Water → [`srd51.spell.control-water`](#srd51-spell-control-water) |
| [131](../../references/srd-5.1/pages/page-131.md) | Control Water continuation → [`srd51.spell.control-water`](#srd51-spell-control-water)<br>Control Weather → [`srd51.spell.control-weather`](#srd51-spell-control-weather)<br>Counterspell → [`srd51.spell.counterspell`](#srd51-spell-counterspell)<br>Create Food and Water → [`srd51.spell.create-food-and-water`](#srd51-spell-create-food-and-water) |
| [132](../../references/srd-5.1/pages/page-132.md) | Create or Destroy Water → [`srd51.spell.create-or-destroy-water`](#srd51-spell-create-or-destroy-water)<br>Create Undead → [`srd51.spell.create-undead`](#srd51-spell-create-undead)<br>Creation → [`srd51.spell.creation`](#srd51-spell-creation)<br>Cure Wounds → [`srd51.spell.cure-wounds`](#srd51-spell-cure-wounds) |
| [133](../../references/srd-5.1/pages/page-133.md) | Cure Wounds continuation → [`srd51.spell.cure-wounds`](#srd51-spell-cure-wounds) |
