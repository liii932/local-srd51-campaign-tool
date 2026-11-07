# Spells D–E

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

<a id="srd51-spell-dancing-lights"></a>

## Dancing Lights

`srd51.spell.dancing-lights`

Evocation cantrip · 1 action · 120 feet · V, S, M (a bit of phosphorus or wychwood, or a glowworm) · Concentration up to 1 minute.

Create up to four torch-sized hovering lights in range, resembling torches, lanterns, or orbs. You can
combine all four into one vaguely humanoid Medium glow. Each light sheds dim light in a 10-foot
radius. On your turn, spend a bonus action to move the lights up to 60 feet to a spot still in range.
Each light must be within 20 feet of another light from this casting, and exceeding range extinguishes
a light.

**Project interpretation.** Check spacing between separate lights when at least two remain.
Allow a solitary light and the combined form. Applying the proximity rule to a sole light
literally would make the stated one-light choice unusable. The combined form represents four
lights occupying one position and requires no separate nearby light.

[SRD p. 133, Dancing Lights](../../references/srd-5.1/pages/page-133.md)

<a id="srd51-spell-darkness"></a>

## Darkness

`srd51.spell.darkness`

Level 2 evocation · 1 action · 60 feet · V, M (bat fur and either a drop of pitch or a piece of coal) · Concentration up to 10 minutes.

Center a 15-foot-radius sphere of magical darkness on a point in range. Darkness spreads around
corners, blocks ordinary darkvision, and cannot be lit by nonmagical light. A point on an object
you hold or on an object neither worn nor carried moves with that object. Completely covering the
source with an opaque object blocks its darkness. Any overlap with light from a spell of level 2 or
lower dispels the light-producing spell. [Daylight](#srd51-spell-daylight) supplies the reciprocal
dispelling interaction for this darkness. [Devil’s Sight](classes-warlock-wizard.md#srd51-class-warlock-devils-sight) and
[truesight](exploration.md#srd51-core-truesight) can see into magical darkness within their own ranges.

[SRD p. 133, Darkness](../../references/srd-5.1/pages/page-133.md) · [SRD p. 49, Devil’s Sight](../../references/srd-5.1/pages/page-049.md) · [SRD p. 86, Truesight](../../references/srd-5.1/pages/page-086.md)

<a id="srd51-spell-darkvision"></a>

## Darkvision

`srd51.spell.darkvision`

Level 2 transmutation · 1 action · Touch · V, S, M (a pinch of dried carrot or an agate) · 8 hours.

Touch a willing creature to give it [darkvision](exploration.md#srd51-core-darkvision) to 60 feet for the duration. This sight remains subject to specific rules for magical darkness, such as [Darkness](#srd51-spell-darkness).

[SRD p. 133, Darkvision](../../references/srd-5.1/pages/page-133.md) · [SRD p. 86, Darkvision](../../references/srd-5.1/pages/page-086.md)

<a id="srd51-spell-daylight"></a>

## Daylight

`srd51.spell.daylight`

Level 3 evocation · 1 action · 60 feet · V, S · 1 hour.

Choose a point in range. It radiates bright light in a 60-foot-radius sphere and dim light for 60 feet
farther. A point on an object you hold or an object neither worn nor carried moves with that object.
Completely enclosing the affected object in an opaque cover blocks its light. Overlapping any part of
this light with darkness created by a spell of level 3 or lower dispels the darkness-producing spell.

[SRD p. 133, Daylight](../../references/srd-5.1/pages/page-133.md)

<a id="srd51-spell-death-ward"></a>

## Death Ward

`srd51.spell.death-ward`

Level 4 abjuration · 1 action · Touch · V, S · 8 hours.

Ward a touched creature against the first qualifying event. If damage would first reduce it to 0 HP,
set it to 1 HP instead and end the ward. If the ward is still active when an effect would kill the
creature instantly without dealing damage, negate that effect against it and end the ward.

[SRD p. 133, Death Ward](../../references/srd-5.1/pages/page-133.md)

<a id="srd51-spell-delayed-blast-fireball"></a>

## Delayed Blast Fireball

`srd51.spell.delayed-blast-fireball`

Level 7 evocation · 1 action · 150 feet · V, S, M (a tiny ball of bat guano and sulfur) · Concentration up to 1 minute.

Place a glowing bead at a point in range. Its base damage is 12d6 plus 1d6 per slot level above 7. At
the end of each of your turns, add 1d6 if the bead remains intact. The bead detonates when the spell
ends, including when you stop concentrating or lose concentration.

A creature touching the bead before expiry makes a Dexterity save. Failure ends the spell and
detonates the bead immediately. Success lets the creature throw the bead up to 40 feet. If the thrown
bead hits a creature or solid object, the spell ends and it detonates there.

The blast fills a 20-foot-radius sphere centered on the detonation point and spreads around corners.
Each creature in it saves with Dexterity against the accumulated fire damage, taking half on success.
The blast also damages objects and ignites flammable objects that are neither worn nor carried.

[SRD p. 133, Delayed Blast Fireball](../../references/srd-5.1/pages/page-133.md) · [SRD p. 134, Delayed Blast Fireball continuation](../../references/srd-5.1/pages/page-134.md)

<a id="srd51-spell-demiplane"></a>

## Demiplane

`srd51.spell.demiplane`

Level 8 conjuration · 1 action · 60 feet · S · 1 hour.

Place a shadow door on a flat solid surface you can see in range. A Medium creature can pass through
freely. When opened, it leads to a demiplane. A new demiplane looks like an empty 30-foot cube of wood
or stone. Instead of making a new one, you may connect to one you previously created with this spell,
or to another caster's demiplane if you know its nature and contents. At expiry, the door vanishes on
both sides and creatures and objects left inside are trapped there.

[SRD p. 134, Demiplane](../../references/srd-5.1/pages/page-134.md)

<a id="srd51-spell-detect-evil-and-good"></a>

## Detect Evil and Good

`srd51.spell.detect-evil-and-good`

Level 1 divination · 1 action · Self · V, S · Concentration up to 10 minutes.

Within 30 feet of you, sense the presence and location of aberrations, celestials, elementals, fey,
fiends, and undead. Also sense whether a magically consecrated or desecrated place or object is within
that distance. Apply the [detection barrier limits](#srd51-spell-detection-barriers).

[SRD p. 134, Detect Evil and Good](../../references/srd-5.1/pages/page-134.md)

<a id="srd51-spell-detect-magic"></a>

## Detect Magic

`srd51.spell.detect-magic`

Level 1 divination (ritual) · 1 action · Self · V, S · Concentration up to 10 minutes.

Sense whether magic is present within 30 feet of you. After sensing it, you can spend an action to see faint auras on visible creatures or objects bearing magic in that area and learn the magic's school if it has one. Apply the [detection barrier limits](#srd51-spell-detection-barriers). [Arcanist's Magic Aura](spells-a-c.md#srd51-spell-arcanists-magic-aura) can falsify the aura.

[SRD p. 134, Detect Magic](../../references/srd-5.1/pages/page-134.md) · [SRD p. 119, Arcanist’s Magic Aura](../../references/srd-5.1/pages/page-119.md)

<a id="srd51-spell-detect-poison-and-disease"></a>

## Detect Poison and Disease

`srd51.spell.detect-poison-and-disease`

Level 1 divination (ritual) · 1 action · Self · V, S, M (a yew leaf) · Concentration up to 10 minutes.

Within 30 feet of you, sense the presence and location of poisons, poisonous creatures, and diseases
and identify each one's kind. Apply the [detection barrier limits](#srd51-spell-detection-barriers).

[SRD p. 134, Detect Poison and Disease](../../references/srd-5.1/pages/page-134.md)

<a id="srd51-spell-detection-barriers"></a>

### Detection barrier limits

`srd51.spell.detection-barriers`

Detect Evil and Good, Detect Magic, and Detect Poison and Disease normally sense through physical
barriers. Even thin lead sheeting defeats all three. The thickness threshold for other listed
barriers depends on the material. Use 1 inch for common metal, 12 inches for stone, and 3 feet for
wood or dirt. [SRD p. 134, Detect Evil and Good, Detect Magic, and Detect Poison and Disease](../../references/srd-5.1/pages/page-134.md)

<a id="srd51-spell-detect-thoughts"></a>

## Detect Thoughts

`srd51.spell.detect-thoughts`

Level 2 divination · 1 action · Self · V, S, M (a copper piece) · Concentration up to 1 minute.

A creature with Intelligence 3 or lower or no spoken language is ineligible for both search and
reading.

- **Read.** On casting or by spending your action on a later turn, focus on one eligible creature you can see within 30 feet. Learn what is foremost in its mind. Questions spoken to it can steer these thoughts. Spend an action to shift focus or probe the same mind more deeply.
- **Probe.** The target makes a Wisdom save. Success ends the spell. On failure, learn its reasoning if any, emotional state, and one major preoccupation. The target notices the probe in either case. While your attention stays on it, it can spend an action on its turn to contest your Intelligence check with its own. Its success ends the spell.
- **Search.** On casting or as an action during the duration, scan for eligible minds within 30 feet, even unseen ones. The search penetrates barriers except 2 feet of rock, 2 inches of metal other than lead, or a thin sheet of lead. For the remainder of the spell you can read minds detected this way without seeing them while they remain within 30 feet.

[SRD p. 135, Detect Thoughts](../../references/srd-5.1/pages/page-135.md)

<a id="srd51-spell-dimension-door"></a>

## Dimension Door

`srd51.spell.dimension-door`

Level 4 conjuration · 1 action · 500 feet · V · Instantaneous.

Teleport to an exact spot in range you can see, visualize, or specify by distance and direction. You
can take objects within your carrying capacity. You can take one willing creature of your size or
smaller that is within 5 feet at casting. It can bring gear within its own carrying capacity. If a
creature or object occupies the intended destination, the teleport fails and each traveler takes 4d6
force damage.

[SRD p. 135, Dimension Door](../../references/srd-5.1/pages/page-135.md)

<a id="srd51-spell-disguise-self"></a>

## Disguise Self

`srd51.spell.disguise-self`

Level 1 illusion · 1 action · Self · V, S · 1 hour.

Set the appearance of your body, clothing, armor, weapons, and other possessions on your person. The
illusion permits an apparent height change of up to 1 foot in either direction and any apparent build,
while keeping the basic arrangement of limbs. Other appearance details are yours to choose. Physical
interaction exposes discrepancies because the illusion does not alter actual surfaces. A creature
inspecting your appearance can spend its action on an Intelligence (Investigation) check against your
spell save DC to detect the disguise. It persists until expiry or dismissal using your action.

[SRD p. 135, Disguise Self](../../references/srd-5.1/pages/page-135.md)

<a id="srd51-spell-disintegrate"></a>

## Disintegrate

`srd51.spell.disintegrate`

Level 6 transmutation · 1 action · 60 feet · V, S, M (a lodestone and a pinch of dust) · Instantaneous.

Aim at a creature, object, or creation of magical force you can see in range.

- **Creature.** A Dexterity save prevents the effect on success. On failure, deal 10d6 + 40 force damage plus 3d6 per slot level above 6. If this damage reduces the creature to 0 HP, its body and everything worn or carried except magic items become fine gray dust. Only [True Resurrection](spells-t-z.md#srd51-spell-true-resurrection) or [Wish](spells-t-z.md#srd51-spell-wish) can return it to life.
- **Nonmagical object or creation of force.** Automatically disintegrate the entire target if it is Large or smaller. For a Huge or larger target, disintegrate a 10-foot-cube portion.
- **Magic item.** No effect.

[SRD p. 135, Disintegrate](../../references/srd-5.1/pages/page-135.md) · [SRD p. 136, Disintegrate continuation](../../references/srd-5.1/pages/page-136.md) · [SRD p. 189, True Resurrection](../../references/srd-5.1/pages/page-189.md) · [SRD p. 193, Wish](../../references/srd-5.1/pages/page-193.md)

<a id="srd51-spell-dispel-evil-and-good"></a>

## Dispel Evil and Good

`srd51.spell.dispel-evil-and-good`

Level 5 abjuration · 1 action · Self · V, S, M (holy water or powdered silver and iron) · Concentration up to 1 minute.

While protected, impose disadvantage on an attack roll against you if its attacker is undead,
celestial, elemental, fey, or fiend. Using either special function as an action ends it early.

- **Break Enchantment.** Touch a creature you can reach that a creature of one of those five types has charmed, frightened, or possessed. End the charm, fear, or possession from those creatures.
- **Dismissal.** Make a melee spell attack on a creature of one of those types within reach. On a hit it makes a Charisma save. Failure sends it to its home plane if it is elsewhere. An undead sent away goes to the Shadowfell and a fey to the Feywild.

[SRD p. 136, Dispel Evil and Good](../../references/srd-5.1/pages/page-136.md)

<a id="srd51-spell-dispel-magic"></a>

## Dispel Magic

`srd51.spell.dispel-magic`

Level 3 abjuration · 1 action · 120 feet · V, S · Instantaneous.

Choose one creature, object, or magical effect in range. Each spell on that target whose level is at most the slot spent on Dispel Magic ends automatically. Check separately for each higher-level spell using your spellcasting ability against DC 10 plus the spell's level. Each success ends the corresponding spell. Specific protections such as [Antimagic Field](spells-a-c.md#srd51-spell-antimagic-field)'s immunity still apply.

[SRD p. 136, Dispel Magic](../../references/srd-5.1/pages/page-136.md) · [SRD p. 117, Antimagic Field](../../references/srd-5.1/pages/page-117.md)

<a id="srd51-spell-divination"></a>

## Divination

`srd51.spell.divination`

Level 4 divination (ritual) · 1 action · Self · V, S, M (incense and a sacrificial offering appropriate to your religion, together worth at least 25 gp and consumed) · Instantaneous.

Ask a god or its servants one question about a specific goal, event, or activity to occur within the
next 7 days. The DM answers truthfully, possibly as an omen, short phrase, or cryptic rhyme. The
reading excludes possible changes of circumstance such as further spellcasting or a change of
companions. Before your next long rest, each casting beyond the first adds 25 percentage points to the
chance of a random reading. The DM rolls secretly.

[SRD p. 136, Divination](../../references/srd-5.1/pages/page-136.md)

<a id="srd51-spell-divine-favor"></a>

## Divine Favor

`srd51.spell.divine-favor`

Level 1 evocation · 1 bonus action · Self · V, S · Concentration up to 1 minute.

While the spell lasts, each of your weapon attack hits deals 1d4 additional radiant damage.

[SRD p. 136, Divine Favor](../../references/srd-5.1/pages/page-136.md) · [SRD p. 137, Divine Favor continuation](../../references/srd-5.1/pages/page-137.md)

<a id="srd51-spell-divine-word"></a>

## Divine Word

`srd51.spell.divine-word`

Level 7 evocation · 1 bonus action · 30 feet · V · Instantaneous.

Choose any number of creatures you can see in range. The ones that hear you make Charisma saves. On
failure, the hit point outcome depends on current HP.

- **51 or more.** No hit-point-based outcome.
- **41 through 50.** Deafened for 1 minute.
- **31 through 40.** Deafened and blinded for 10 minutes.
- **21 through 30.** Blinded, deafened, and stunned for 1 hour.
- **20 or fewer.** Dies immediately.

**Project interpretation.** The source names a single outcome despite listing overlapping upper bounds. Apply the lowest qualifying threshold, which gives each current hit point total one outcome. Separately, a celestial, elemental, fey, or fiend that fails returns to its plane of origin if away from it and cannot return to your current plane for 24 hours without [Wish](spells-t-z.md#srd51-spell-wish). This planar clause applies at any hit point total.

[SRD p. 137, Divine Word](../../references/srd-5.1/pages/page-137.md) · [SRD p. 193, Wish](../../references/srd-5.1/pages/page-193.md)

<a id="srd51-spell-dominate-beast"></a>

## Dominate Beast

`srd51.spell.dominate-beast`

Level 4 enchantment · 1 action · 60 feet · V, S · Concentration up to 1 minute.

Target a beast you can see in range and apply the [shared domination procedure](#srd51-spell-domination-procedure). Slot 5 extends concentration to up to 10 minutes, slot 6 to up to 1 hour, and slots 7 through 9 to up to 8 hours.

<a id="srd51-spell-domination-procedure"></a>

### Domination procedure

`srd51.spell.domination-procedure`

The target makes a Wisdom save with advantage if you or creatures friendly to you are fighting it.
Failure makes it charmed by you for the duration. While charmed, it is telepathically linked to you
while on the same plane. When conscious, you can issue simple general orders through the link without
spending an action. It tries its best to obey and, once it completes an order without further
direction, protects and preserves itself as well as it can.

Spending your action gives you direct control through the end of your following turn. In this
mode, you select the creature's actions and it cannot act beyond what you allow. Forcing it to use its reaction during this
control also costs your reaction. Every time the target takes damage it repeats the Wisdom save and
ends the spell on success.

**Project interpretation.** Apply the fighting advantage only on the initial save. The repeat
clause gives a new Wisdom save on damage without mentioning the initial advantage. Carrying
that advantage forward would apply a condition from the targeting step to a separately
specified recovery roll.

[SRD p. 137, Dominate Beast and Dominate Monster](../../references/srd-5.1/pages/page-137.md) · [SRD p. 138, Dominate Person](../../references/srd-5.1/pages/page-138.md).

[SRD p. 137, Dominate Beast](../../references/srd-5.1/pages/page-137.md)

<a id="srd51-spell-dominate-monster"></a>

## Dominate Monster

`srd51.spell.dominate-monster`

Level 8 enchantment · 1 action · 60 feet · V, S · Concentration up to 1 hour.

Target any creature you can see in range and apply the [shared domination procedure](#srd51-spell-domination-procedure). Slot 9 extends concentration to up to 8 hours.

[SRD p. 137, Dominate Monster](../../references/srd-5.1/pages/page-137.md) · [SRD p. 138, Dominate Monster continuation](../../references/srd-5.1/pages/page-138.md)

<a id="srd51-spell-dominate-person"></a>

## Dominate Person

`srd51.spell.dominate-person`

Level 5 enchantment · 1 action · 60 feet · V, S · Concentration up to 1 minute.

Target a humanoid you can see in range and apply the [shared domination procedure](#srd51-spell-domination-procedure). Slot 6 extends concentration to up to 10 minutes, slot 7 to up to 1 hour, and slots 8 and 9 to up to 8 hours.

[SRD p. 138, Dominate Person](../../references/srd-5.1/pages/page-138.md)

<a id="srd51-spell-dream"></a>

## Dream

`srd51.spell.dream`

Level 5 illusion · 1 minute · Special range · V, S, M (a handful of sand, a dab of ink, and a quill from a sleeping bird) · 8 hours.

Choose a creature you know on your plane. Dream cannot contact creatures that do not sleep,
including elves with [Trance](ancestries.md#srd51-ancestry-elf-trance). You or a willing creature you
touch becomes the messenger, entering a trance. The messenger can perceive its surroundings while in trance but
cannot take actions or move. If the target is awake, the messenger knows and may wait for it to sleep
or end the spell. When the target is asleep, the messenger appears in its dream and can converse as
long as it sleeps within the spell's duration. The messenger can shape the dream setting and its
contents, and can leave the trance at any time to end the spell early. The target remembers the dream
perfectly upon waking.

You may make the messenger appear terrifying and monstrous. In this mode it can deliver up to 10
words, then the target makes a Wisdom save. Failure causes a nightmare that lasts for the target's
sleep, deprives it of all benefits of that rest, and deals 3d6 psychic damage on waking. Possession of
a physical part of the target, such as a lock of hair or nail clipping, imposes disadvantage on the
save.

[SRD p. 138, Dream](../../references/srd-5.1/pages/page-138.md) · [SRD p. 4, Trance](../../references/srd-5.1/pages/page-004.md)

<a id="srd51-spell-druidcraft"></a>

## Druidcraft

`srd51.spell.druidcraft`

Transmutation cantrip · 1 action · 30 feet · V, S · Instantaneous.

Create one effect in range.

- A tiny harmless sensory sign predicts the next 24 hours of weather at your location and lasts for 1 round.
- A flower blossoms, seed pod opens, or leaf bud blooms instantly.
- A harmless instantaneous sensory effect fits within a 5-foot cube.
- A candle, torch, or small campfire lights or goes out immediately.

[SRD p. 138, Druidcraft](../../references/srd-5.1/pages/page-138.md) · [SRD p. 139, Druidcraft continuation](../../references/srd-5.1/pages/page-139.md)

<a id="srd51-spell-earthquake"></a>

## Earthquake

`srd51.spell.earthquake`

Level 8 evocation · 1 action · 500 feet · V, S, M (a pinch of dirt, a piece of rock, and a lump of clay) · Concentration up to 1 minute.

Choose a visible point on the ground in range. Tremors affect a 100-foot-radius circle centered there,
turning the ground into difficult terrain and shaking creatures and structures in contact with it. A
concentrating creature on the affected ground must make a Constitution save and loses concentration on
failure. **Project interpretation.** Make this Constitution save when the creature first satisfies
both predicates together and each time it becomes eligible again. This covers a grounded
creature already concentrating at casting, a concentrating creature newly contacting the
affected ground, and a grounded creature beginning concentration. The source gives this
check no cadence. The explicit end-of-turn repetition belongs to the separate Dexterity
save, so continuous eligibility alone does not trigger another Constitution save.
At casting and at the end of each of your turns while you concentrate, grounded creatures in the area make
Dexterity saves and fall prone on failure.

The DM determines the following terrain-dependent effects.

- **Fissures.** At the start of your next turn after casting, 1d6 fissures open where the DM places them. Each is 10 feet wide, 1d10 × 10 feet deep, and runs across the area from edge to opposite edge. A creature standing where one opens makes a Dexterity save. Failure drops it in. Success moves it with the fissure's edge. A structure over a fissure collapses automatically.
- **Structures.** At casting and at the start of each of your turns until expiry, deal 50 bludgeoning damage to each grounded structure in the area. A structure collapses if it reaches 0 HP. Each creature within a distance of the collapsing structure equal to half its height makes a Dexterity save. Failure deals 5d6 bludgeoning damage, knocks it prone, and buries it. Success halves damage and avoids both other consequences. Escaping rubble costs an action and a successful DC 20 Strength (Athletics) check. The DM may adjust the DC to suit the rubble.

[SRD p. 139, Earthquake](../../references/srd-5.1/pages/page-139.md)

<a id="srd51-spell-eldritch-blast"></a>

## Eldritch Blast

`srd51.spell.eldritch-blast`

Evocation cantrip · 1 action · 120 feet · V, S · Instantaneous.

Direct a beam at a creature in range. Each beam gets its own ranged spell attack and deals 1d10 force
damage on a hit. The spell produces 1 beam initially, 2 at caster level 5, 3 at 11, and 4 at 17.
Multiple beams may share a target or be split among creatures in range.

[SRD p. 139, Eldritch Blast](../../references/srd-5.1/pages/page-139.md)

<a id="srd51-spell-enhance-ability"></a>

## Enhance Ability

`srd51.spell.enhance-ability`

Level 2 transmutation · 1 action · Touch · V, S, M (fur or a feather from a beast) · Concentration up to 1 hour.

Touch a creature and choose one enhancement for the duration. Each slot level above 2 lets you target
one more creature.

- **Bear's Endurance.** Advantage on Constitution checks and 2d6 temporary hit points that disappear at spell end.
- **Bull's Strength.** Advantage on Strength checks and twice the carrying capacity.
- **Cat's Grace.** Advantage on Dexterity checks, plus immunity to falling damage from falls of up to 20 feet unless it is incapacitated.
- **Eagle's Splendor.** Advantage on Charisma checks.
- **Fox's Cunning.** Advantage on Intelligence checks.
- **Owl's Wisdom.** Advantage on Wisdom checks.

[SRD p. 139, Enhance Ability](../../references/srd-5.1/pages/page-139.md)

<a id="srd51-spell-enlarge-reduce"></a>

## Enlarge/Reduce

`srd51.spell.enlarge-reduce`

Level 2 transmutation · 1 action · 30 feet · V, S, M (a pinch of powdered iron) · Concentration up to 1 minute.

Choose a visible creature or an object you can see in range that is neither worn nor carried. An
unwilling target can avoid the effect by succeeding on a Constitution save. An affected creature's
worn and carried possessions resize with it. Any dropped item immediately resumes its ordinary size.

- **Enlarge.** Double all dimensions, multiply weight by eight, and raise size by one category. If space limits growth, grow only as large as fits. The target has advantage on Strength checks and saves until expiry. Its weapons grow with it and its attacks using them deal 1d4 additional damage while they remain enlarged.
- **Reduce.** Halve all dimensions, reduce weight to one eighth, and lower size by one category. The target has disadvantage on Strength checks and saves until expiry. Its weapons shrink with it and attacks using them deal 1d4 less damage, to a minimum of 1, while they remain reduced.

[SRD p. 140, Enlarge/Reduce](../../references/srd-5.1/pages/page-140.md)

<a id="srd51-spell-entangle"></a>

## Entangle

`srd51.spell.entangle`

Level 1 conjuration · 1 action · 90 feet · V, S · Concentration up to 1 minute.

From a point in range, fill a 20-foot square of ground with plants. The area is difficult terrain
until expiry, when the plants wilt. Each creature already in the area when you cast makes a Strength
save and is restrained by the plants on failure until the spell ends or it frees itself. An affected
creature can use its action for a Strength check against your spell save DC, escaping the restraint on
success. A creature arriving later encounters the difficult terrain without an entry-triggered save.

[SRD p. 140, Entangle](../../references/srd-5.1/pages/page-140.md)

<a id="srd51-spell-enthrall"></a>

## Enthrall

`srd51.spell.enthrall`

Level 2 enchantment · 1 action · 60 feet · V, S · 1 minute.

Choose creatures you can see in range that can hear you. Each makes a Wisdom save. Creatures immune to
charm succeed automatically. A creature you or your companions are fighting saves with advantage.
Failure imposes disadvantage on Wisdom (Perception) checks to perceive any creature besides you. For
that target the penalty ends at expiry or when it can no longer hear you. Your incapacitation or loss
of ability to speak ends the spell.

[SRD p. 140, Enthrall](../../references/srd-5.1/pages/page-140.md)

<a id="srd51-spell-etherealness"></a>

## Etherealness

`srd51.spell.etherealness`

Level 7 transmutation · 1 action · Self · V, S · Up to 8 hours.

Enter the Border Ethereal at its overlap with the casting plane. Casting on the Ethereal Plane or a
plane without an Ethereal border has no effect. Remain until expiry or until you spend an action to
dismiss the spell. You can move in any direction. Vertical movement costs two feet of movement per
foot traveled. You still hear the departure plane and see it in gray to a limit of 60 feet.

You can affect and be affected only by creatures on the Ethereal Plane. Creatures outside it cannot
perceive or interact with you without an ability or magic permitting it. Ignore non-Ethereal objects
and effects, including objects you can see on the departure plane.

At spell end you immediately return to your departure plane at your current spot. If that spot
contains a solid object or creature, shift to the nearest unoccupied space you can occupy and take
force damage equal to twice the feet moved. Slots 8 and 9 can affect up to 3 and 6 willing creatures
respectively, including you, within 10 feet of you when cast.

[SRD p. 140, Etherealness](../../references/srd-5.1/pages/page-140.md) · [SRD p. 141, Etherealness continuation](../../references/srd-5.1/pages/page-141.md) · [SRD p. 363, Ethereal Plane](../../references/srd-5.1/pages/page-363.md) · [SRD p. 364, The Outer Planes](../../references/srd-5.1/pages/page-364.md)

## Examples

- Daylight dispels a base-level Darkness if the areas overlap. Darkvision by itself cannot see through that Darkness, even in range of the enhanced sense. [SRD p. 133, Darkness, Darkvision, and Daylight](../../references/srd-5.1/pages/page-133.md)
- An unspent Death Ward intercepts an effect that kills instantly without damage. When it has already replaced an earlier reduction to 0 HP, the same later effect receives no protection from that ward. [SRD p. 133, Death Ward](../../references/srd-5.1/pages/page-133.md)
- A Dimension Door destination exactly 500 feet away can be specified without seeing it. If it contains a solid object, both the caster and an eligible passenger take 4d6 force damage and stay where they were. [SRD p. 135, Dimension Door](../../references/srd-5.1/pages/page-135.md)
- A sleeping target whose Intelligence is 3 may be contacted by Dream if it meets that spell’s conditions, but Detect Thoughts cannot find or read its mind. [SRD p. 135, Detect Thoughts](../../references/srd-5.1/pages/page-135.md) · [SRD p. 138, Dream](../../references/srd-5.1/pages/page-138.md)
- Entering Entangle after the cast costs difficult-terrain movement without triggering the spell’s Strength save. A creature already there can be restrained on the casting save and later spend an action trying the escape check. [SRD p. 140, Entangle](../../references/srd-5.1/pages/page-140.md)
- Ending Etherealness inside stone shunts a creature to the nearest unoccupied space it can occupy. If that requires 15 feet of displacement, it takes 30 force damage. [SRD p. 141, Etherealness continuation](../../references/srd-5.1/pages/page-141.md)

## Source coverage

This file includes entries beginning on pp. 133–140 and the Etherealness continuation on p. 141. Cure
Wounds continues from the previous band. Expeditious Retreat and later entries belong to the following
band. Narrative examples and cosmetic descriptions with no rule effect are omitted.

Checked the local PDF against the text for the overlapping Divine Word thresholds and spell
continuations (p. 137), and Earthquake’s concentration and fissure paragraphs and split collapse
procedure (p. 139).

| Page | Source entries and destination IDs |
| --- | --- |
| [133](../../references/srd-5.1/pages/page-133.md) | Dancing Lights → [`srd51.spell.dancing-lights`](#srd51-spell-dancing-lights)<br>Darkness → [`srd51.spell.darkness`](#srd51-spell-darkness)<br>Darkvision → [`srd51.spell.darkvision`](#srd51-spell-darkvision)<br>Daylight → [`srd51.spell.daylight`](#srd51-spell-daylight)<br>Death Ward → [`srd51.spell.death-ward`](#srd51-spell-death-ward)<br>Delayed Blast Fireball → [`srd51.spell.delayed-blast-fireball`](#srd51-spell-delayed-blast-fireball) |
| [134](../../references/srd-5.1/pages/page-134.md) | Delayed Blast Fireball continuation → [`srd51.spell.delayed-blast-fireball`](#srd51-spell-delayed-blast-fireball)<br>Demiplane → [`srd51.spell.demiplane`](#srd51-spell-demiplane)<br>Detect Evil and Good → [`srd51.spell.detect-evil-and-good`](#srd51-spell-detect-evil-and-good)<br>Detect Magic → [`srd51.spell.detect-magic`](#srd51-spell-detect-magic)<br>Detect Poison and Disease → [`srd51.spell.detect-poison-and-disease`](#srd51-spell-detect-poison-and-disease)<br>Shared detection obstructions → [`srd51.spell.detection-barriers`](#srd51-spell-detection-barriers) |
| [135](../../references/srd-5.1/pages/page-135.md) | Detect Thoughts → [`srd51.spell.detect-thoughts`](#srd51-spell-detect-thoughts)<br>Dimension Door → [`srd51.spell.dimension-door`](#srd51-spell-dimension-door)<br>Disguise Self → [`srd51.spell.disguise-self`](#srd51-spell-disguise-self)<br>Disintegrate → [`srd51.spell.disintegrate`](#srd51-spell-disintegrate) |
| [136](../../references/srd-5.1/pages/page-136.md) | Disintegrate continuation → [`srd51.spell.disintegrate`](#srd51-spell-disintegrate)<br>Dispel Evil and Good → [`srd51.spell.dispel-evil-and-good`](#srd51-spell-dispel-evil-and-good)<br>Dispel Magic → [`srd51.spell.dispel-magic`](#srd51-spell-dispel-magic)<br>Divination → [`srd51.spell.divination`](#srd51-spell-divination)<br>Divine Favor → [`srd51.spell.divine-favor`](#srd51-spell-divine-favor) |
| [137](../../references/srd-5.1/pages/page-137.md) | Divine Favor continuation → [`srd51.spell.divine-favor`](#srd51-spell-divine-favor)<br>Divine Word → [`srd51.spell.divine-word`](#srd51-spell-divine-word)<br>Dominate Beast → [`srd51.spell.dominate-beast`](#srd51-spell-dominate-beast)<br>Dominate Monster → [`srd51.spell.dominate-monster`](#srd51-spell-dominate-monster) |
| [138](../../references/srd-5.1/pages/page-138.md) | Dominate Monster continuation → [`srd51.spell.dominate-monster`](#srd51-spell-dominate-monster)<br>Dominate Person → [`srd51.spell.dominate-person`](#srd51-spell-dominate-person)<br>Dream → [`srd51.spell.dream`](#srd51-spell-dream)<br>Druidcraft → [`srd51.spell.druidcraft`](#srd51-spell-druidcraft) |
| [139](../../references/srd-5.1/pages/page-139.md) | Druidcraft continuation → [`srd51.spell.druidcraft`](#srd51-spell-druidcraft)<br>Earthquake → [`srd51.spell.earthquake`](#srd51-spell-earthquake)<br>Eldritch Blast → [`srd51.spell.eldritch-blast`](#srd51-spell-eldritch-blast)<br>Enhance Ability → [`srd51.spell.enhance-ability`](#srd51-spell-enhance-ability) |
| [140](../../references/srd-5.1/pages/page-140.md) | Enlarge/Reduce → [`srd51.spell.enlarge-reduce`](#srd51-spell-enlarge-reduce)<br>Entangle → [`srd51.spell.entangle`](#srd51-spell-entangle)<br>Enthrall → [`srd51.spell.enthrall`](#srd51-spell-enthrall)<br>Etherealness → [`srd51.spell.etherealness`](#srd51-spell-etherealness) |
| [141](../../references/srd-5.1/pages/page-141.md) | Etherealness continuation → [`srd51.spell.etherealness`](#srd51-spell-etherealness) |
