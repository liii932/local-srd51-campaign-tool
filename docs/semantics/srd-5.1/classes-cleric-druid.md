# Cleric and Druid

Feature acquisition uses levels in the class that grants the feature.
[Proficiency bonus](abilities.md#srd51-core-proficiency-bonus) uses total character level.

<a id="srd51-class-cleric"></a>

## Cleric

`srd51.class.cleric`

Each cleric level grants one d8 Hit Die. At character level 1, maximum hit points are 8 + Constitution modifier. Each later cleric level adds 1d8 or a fixed 5, plus Constitution modifier. Starting proficiencies are light armor, medium armor, shields, simple weapons, Wisdom saves, and Charisma saves. There are no starting tool proficiencies. Choose two skills from History, Insight, Medicine, Persuasion, and Religion. [SRD p. 15, Cleric Class Features](../../references/srd-5.1/pages/page-015.md)

In addition to background equipment, choose a mace or warhammer, scale mail or leather armor or chain mail, a light crossbow with 20 bolts or any simple weapon, and a priest's pack or explorer's pack. Choosing a warhammer or chain mail requires proficiency with that item. Also receive a shield and holy symbol. [SRD p. 15, Cleric Equipment](../../references/srd-5.1/pages/page-015.md)

<a id="srd51-class-cleric-spellcasting"></a>

### Spellcasting

`srd51.class.cleric.spellcasting`

At level 1 choose three cleric cantrips. Add one chosen cleric cantrip each at levels 4 and 10. Use cleric level as input to the [full-caster slot progression](character-advancement.md#srd51-character-full-caster-slots). Choose prepared leveled spells from the [cleric list](spell-lists.md#srd51-spell-list-cleric), in a number equal to cleric level + Wisdom modifier, minimum one. Each spell must be of a level for which you have slots. Casting a prepared spell with a slot expends one slot of its level or higher and leaves the spell prepared. A long rest restores all spent slots. After a long rest you may change your prepared list, spending time in prayer and meditation equal to at least 1 minute per spell level for each spell on the list. Domain spells are prepared separately. [SRD p. 15, The Cleric and Cantrips](../../references/srd-5.1/pages/page-015.md) [SRD p. 16, Preparing and Casting Spells and Domain Spells](../../references/srd-5.1/pages/page-016.md)

Wisdom is the spellcasting ability. Save DC is 8 + proficiency bonus + Wisdom modifier, and spell attack modifier is proficiency bonus + Wisdom modifier. Ritual casting requires the spell to be a prepared cleric spell with the ritual tag. A holy symbol can serve as a focus for cleric spells. [SRD p. 16, Spellcasting Ability, Ritual Casting, and Spellcasting Focus](../../references/srd-5.1/pages/page-016.md)

<a id="srd51-class-cleric-divine-domain"></a>

### Divine Domain

`srd51.class.cleric.divine-domain`

At level 1 choose a domain associated with your deity. It supplies spells and features at level 1, a Channel Divinity choice at level 2, and later features at levels 6, 8, and 17. [Life Domain](#srd51-class-cleric-life-domain) is the domain detailed here. Gain domain spells at the cleric levels set by that domain. Each is always prepared once gained, takes no place in the usual preparation allowance, and counts as a cleric spell for you even when absent from the cleric list. [SRD p. 16, Divine Domain and Domain Spells](../../references/srd-5.1/pages/page-016.md) [SRD p. 17, Life Domain](../../references/srd-5.1/pages/page-017.md)

<a id="srd51-class-cleric-channel-divinity"></a>

### Channel Divinity

`srd51.class.cleric.channel-divinity`

From level 2, expend a use and choose one available effect, such as Turn Undead or your domain's option. Capacity is one use at levels 2–5, two at 6–17, and three at 18–20. A short or long rest restores all spent uses. Use your cleric spell save DC for saves required by a cleric Channel Divinity effect. [SRD p. 16, Channel Divinity](../../references/srd-5.1/pages/page-016.md)

<a id="srd51-class-cleric-turn-undead"></a>

#### Turn Undead

`srd51.class.cleric.turn-undead`

Spend an action and a Channel Divinity use to present your holy symbol and speak a prayer. Every undead within 30 feet that sees or hears you makes a Wisdom save. On failure it is turned for 1 minute, ending sooner if it takes any damage. An undead that fails can instead be destroyed by [Destroy Undead](#srd51-class-cleric-destroy-undead). [SRD p. 16, Turn Undead](../../references/srd-5.1/pages/page-016.md) [SRD p. 17, Destroy Undead](../../references/srd-5.1/pages/page-017.md)

A turned creature spends its turns trying to get as far from you as possible. It cannot willingly move into a space within 30 feet of you and cannot use reactions. Its action can only be Dash or an attempt to escape something that prevents its movement. If it has nowhere to move, it may Dodge. [SRD p. 16, Turn Undead](../../references/srd-5.1/pages/page-016.md)

<a id="srd51-class-cleric-ability-score-improvement"></a>

### Ability Score Improvement

`srd51.class.cleric.ability-score-improvement`

At levels 4, 8, 12, 16, and 19, apply an [Ability Score Improvement](character-advancement.md#srd51-character-ability-score-improvement). [SRD p. 17, Ability Score Improvement](../../references/srd-5.1/pages/page-017.md)

<a id="srd51-class-cleric-destroy-undead"></a>

### Destroy Undead

`srd51.class.cleric.destroy-undead`

A failed save against your Turn Undead instantly destroys the undead if its challenge rating is at or below your threshold. Threshold changes by cleric level are 5 → CR 1/2, 8 → CR 1, 11 → CR 2, 14 → CR 3, and 17 → CR 4. Above the current threshold a failure still turns the creature. [SRD p. 16, Turn Undead](../../references/srd-5.1/pages/page-016.md) [SRD p. 17, Destroy Undead](../../references/srd-5.1/pages/page-017.md)

<a id="srd51-class-cleric-divine-intervention"></a>

### Divine Intervention

`srd51.class.cleric.divine-intervention`

From level 10, use your action to request divine aid and describe the desired assistance. Through level 19 roll percentile dice. If the result is at or below your cleric level, the deity intervenes. At level 20 it intervenes automatically without a roll. The GM determines what happens, with an effect of a cleric or cleric domain spell among the appropriate possibilities. After an intervention you must wait 7 days to use the feature again. If the deity does not intervene, finish a long rest before retrying. [SRD p. 17, Divine Intervention](../../references/srd-5.1/pages/page-017.md)

<a id="srd51-class-cleric-life-domain"></a>

### Life Domain

`srd51.class.cleric.life-domain`

Life grants heavy armor proficiency and Disciple of Life at level 1, Preserve Life at 2, Blessed Healer at 6, Divine Strike at 8, and Supreme Healing at 17. The domain centers on life and healing and is associated with many non-evil deities, especially those concerned with agriculture, sunlight, healing, endurance, homes, or community. Apply the [domain spell rules](#srd51-class-cleric-divine-domain) to these grants. [SRD p. 16, Domain Spells](../../references/srd-5.1/pages/page-016.md) [SRD p. 17, Life Domain and Life Domain Spells](../../references/srd-5.1/pages/page-017.md)

- At level 1 gain [Bless](spells-a-c.md#srd51-spell-bless) and [Cure Wounds](spells-a-c.md#srd51-spell-cure-wounds).
- At level 3 gain [Lesser Restoration](spells-h-m.md#srd51-spell-lesser-restoration) and [Spiritual Weapon](spells-n-s.md#srd51-spell-spiritual-weapon).
- At level 5 gain [Beacon of Hope](spells-a-c.md#srd51-spell-beacon-of-hope) and [Revivify](spells-n-s.md#srd51-spell-revivify).
- At level 7 gain [Death Ward](spells-d-e.md#srd51-spell-death-ward) and [Guardian of Faith](spells-e-g.md#srd51-spell-guardian-of-faith).
- At level 9 gain [Mass Cure Wounds](spells-h-m.md#srd51-spell-mass-cure-wounds) and [Raise Dead](spells-n-s.md#srd51-spell-raise-dead). [SRD p. 17, Life Domain Spells](../../references/srd-5.1/pages/page-017.md)

<a id="srd51-class-cleric-bonus-proficiency"></a>

#### Bonus Proficiency

`srd51.class.cleric.bonus-proficiency`

On choosing Life at level 1, gain heavy armor proficiency. [SRD p. 17, Bonus Proficiency](../../references/srd-5.1/pages/page-017.md)

<a id="srd51-class-cleric-disciple-of-life"></a>

#### Disciple of Life

`srd51.class.cleric.disciple-of-life`

At level 1, gain a bonus to HP your spells restore to creatures. A healing spell qualifies at level 1 or above, and adds 2 + the [level of the actual casting](spellcasting.md#srd51-core-casting-at-a-higher-level) to the healing each creature receives. The normal HP maximum still limits what it regains. [SRD p. 17, Disciple of Life](../../references/srd-5.1/pages/page-017.md) [SRD p. 100, Casting a Spell at a Higher Level](../../references/srd-5.1/pages/page-100.md)

<a id="srd51-class-cleric-preserve-life"></a>

#### Preserve Life

`srd51.class.cleric.preserve-life`

From level 2, use an action and a Channel Divinity use to present your holy symbol. Choose any number of creatures within 30 feet and allocate up to 5 × cleric level hit points of healing among them. Undead and constructs are ineligible. For each chosen creature, this healing can raise current hit points to at most half its hit point maximum. [SRD p. 17, Channel Divinity: Preserve Life](../../references/srd-5.1/pages/page-017.md)

<a id="srd51-class-cleric-blessed-healer"></a>

#### Blessed Healer

`srd51.class.cleric.blessed-healer`

Starting at level 6, you gain healing from a casting that heals another creature. If its [effective spell level](spellcasting.md#srd51-core-casting-at-a-higher-level) is at least 1, you regain HP equal to that level plus 2. [SRD p. 17, Blessed Healer](../../references/srd-5.1/pages/page-017.md) [SRD p. 100, Casting a Spell at a Higher Level](../../references/srd-5.1/pages/page-100.md)

<a id="srd51-class-cleric-divine-strike"></a>

#### Divine Strike

`srd51.class.cleric.divine-strike`

From level 8, once on each of your turns when your weapon attack hits a creature, you may add 1d8 radiant damage to the target. At level 14 the bonus becomes 2d8. [SRD p. 17, Divine Strike](../../references/srd-5.1/pages/page-017.md)

<a id="srd51-class-cleric-supreme-healing"></a>

#### Supreme Healing

`srd51.class.cleric.supreme-healing`

From level 17, when healing with a spell would call for rolling dice to restore hit points, use each die's maximum value. A spell heal of 2d6 + 4 consequently restores 16 hit points before applying the target's hit point maximum. [SRD p. 17, Supreme Healing](../../references/srd-5.1/pages/page-017.md) [SRD p. 18, Supreme Healing continuation](../../references/srd-5.1/pages/page-018.md)

<a id="srd51-class-druid"></a>

## Druid

`srd51.class.druid`

Each druid level grants one d8 Hit Die. At character level 1, maximum hit points are 8 + Constitution modifier. Each later druid level adds 1d8 or a fixed 5, plus Constitution modifier. Starting proficiencies are light armor, medium armor, shields, clubs, daggers, darts, javelins, maces, quarterstaffs, scimitars, sickles, slings, spears, an herbalism kit, Intelligence saves, and Wisdom saves. A druid will not wear metal armor or use metal shields. Choose two skills from Arcana, Animal Handling, Insight, Medicine, Nature, Perception, Religion, and Survival. [SRD p. 19, Druid Class Features](../../references/srd-5.1/pages/page-019.md)

In addition to background equipment, choose a wooden shield or any simple weapon, and choose a scimitar or any simple melee weapon. Also receive leather armor, an explorer's pack, and a druidic focus. [SRD p. 19, Druid Equipment](../../references/srd-5.1/pages/page-019.md)

<a id="srd51-class-druid-druidic"></a>

### Druidic

`srd51.class.druid.druidic`

From level 1, you know the secret Druidic language. You can speak it and leave hidden messages in it. A creature that knows Druidic notices such a message automatically. Other creatures need a successful DC 15 Wisdom (Perception) check to detect its presence and magic to decipher it. [SRD p. 19, Druidic](../../references/srd-5.1/pages/page-019.md)

<a id="srd51-class-druid-spellcasting"></a>

### Spellcasting

`srd51.class.druid.spellcasting`

At level 1 choose two druid cantrips. Add one chosen druid cantrip each at levels 4 and 10. Use druid level as input to the [full-caster slot progression](character-advancement.md#srd51-character-full-caster-slots). Choose prepared leveled spells from the [druid list](spell-lists.md#srd51-spell-list-druid), in a number equal to druid level + Wisdom modifier, minimum one. Each spell must be of a level for which you have slots. Casting a prepared spell with a slot expends one slot of its level or higher and leaves the spell prepared. A long rest restores all spent slots. After a long rest you may change your prepared list, spending at least 1 minute per spell level for each spell on the list in prayer and meditation. Circle spells are prepared separately. [SRD p. 19, The Druid](../../references/srd-5.1/pages/page-019.md) [SRD p. 20, Cantrips and Preparing and Casting Spells](../../references/srd-5.1/pages/page-020.md) [SRD p. 22, Circle Spells continuation](../../references/srd-5.1/pages/page-022.md)

Wisdom is the spellcasting ability. Save DC is 8 + proficiency bonus + Wisdom modifier, and spell attack modifier is proficiency bonus + Wisdom modifier. Ritual casting requires the spell to be a prepared druid spell with the ritual tag. A druidic focus can serve as a focus for druid spells. [SRD p. 20, Spellcasting Ability, Ritual Casting, and Spellcasting Focus](../../references/srd-5.1/pages/page-020.md)

<a id="srd51-class-druid-wild-shape"></a>

### Wild Shape

`srd51.class.druid.wild-shape`

From level 2, use an action and one of two uses to become a beast you have seen. Regain all spent uses at the end of a short or long rest. At levels 2–3 the beast can be at most CR 1/4 and have neither a flying nor swimming speed. At levels 4–7 the cap rises to CR 1/2 and a swimming speed becomes eligible. At level 8 the cap rises to CR 1 and any of the beast's movement speeds are eligible. [SRD p. 20, Wild Shape and Beast Shapes](../../references/srd-5.1/pages/page-020.md)

- **Duration and reversion.** The beast form lasts floor(druid level ÷ 2) hours unless you spend another use to continue. You can revert using a bonus action on your turn. Unconsciousness, dropping to 0 hit points, or death makes you revert automatically. [SRD p. 20, Wild Shape](../../references/srd-5.1/pages/page-020.md)
- **Statistics and proficiencies.** Use the beast's statistics, retaining your alignment, personality, and Intelligence, Wisdom, and Charisma scores. Combine your skill and saving throw proficiencies with the beast's. For a proficiency both have, use the beast's listed bonus if higher than yours. The beast's legendary and lair actions are unavailable. [SRD p. 20, Wild Shape](../../references/srd-5.1/pages/page-020.md)
- **Hit points and Hit Dice.** Use the beast's hit points and Hit Dice while transformed. Reverting returns you to your pre-transformation hit points. If reverting because the beast form dropped to 0 hit points, apply excess damage to the normal form. The hit point drop does not knock you unconscious if the normal form still has positive hit points after that excess. [SRD p. 20, Wild Shape](../../references/srd-5.1/pages/page-020.md) [SRD p. 21, Wild Shape continuation](../../references/srd-5.1/pages/page-021.md)
- **Capabilities.** Speech and tasks requiring hands depend on the beast's capabilities. Wild Shape ordinarily prevents casting spells, but it neither breaks existing concentration by itself nor prevents actions granted by a spell you already cast, such as [Call Lightning](spells-a-c.md#srd51-spell-call-lightning). Retain features from class, ancestry, or other sources if the form can physically use them. Your own special senses are unavailable unless the form has those senses too. [Beast Spells](#srd51-class-druid-beast-spells) and [Archdruid](#srd51-class-druid-archdruid) change spellcasting permissions. [SRD p. 21, Wild Shape continuation and Beast Spells](../../references/srd-5.1/pages/page-021.md) [SRD p. 21, Archdruid](../../references/srd-5.1/pages/page-021.md)
- **Equipment.** Choose per item whether it falls in your space, merges into the form, or is worn and functions normally. The GM decides which items the form can practically wear based on its shape and size. The equipment retains its own shape and size, and items that cannot be worn must drop or merge. Merged items have no effect until you leave the form. [SRD p. 21, Wild Shape continuation](../../references/srd-5.1/pages/page-021.md)

<a id="srd51-class-druid-druid-circle"></a>

### Druid Circle

`srd51.class.druid.druid-circle`

At level 2 choose a druid circle. It supplies features at levels 2, 6, 10, and 14. The SRD details [Circle of the Land](#srd51-class-druid-circle-of-the-land). [SRD p. 21, Druid Circle and Circle of the Land](../../references/srd-5.1/pages/page-021.md) [SRD p. 22, Circle of the Land continuation](../../references/srd-5.1/pages/page-022.md)

<a id="srd51-class-druid-ability-score-improvement"></a>

### Ability Score Improvement

`srd51.class.druid.ability-score-improvement`

At levels 4, 8, 12, 16, and 19, apply an [Ability Score Improvement](character-advancement.md#srd51-character-ability-score-improvement). [SRD p. 21, Ability Score Improvement](../../references/srd-5.1/pages/page-021.md)

<a id="srd51-class-druid-timeless-body"></a>

### Timeless Body

`srd51.class.druid.timeless-body`

From level 18, your body ages 1 year for every 10 years that pass. [SRD p. 21, Timeless Body](../../references/srd-5.1/pages/page-021.md)

<a id="srd51-class-druid-beast-spells"></a>

### Beast Spells

`srd51.class.druid.beast-spells`

From level 18, you can cast druid spells while in a Wild Shape form. You can perform their verbal and somatic components in the form but cannot supply material components. [SRD p. 21, Beast Spells](../../references/srd-5.1/pages/page-021.md)

<a id="srd51-class-druid-archdruid"></a>

### Archdruid

`srd51.class.druid.archdruid`

At level 20, Wild Shape uses become unlimited. In both your normal form and Wild Shape, you can omit verbal and somatic components of druid spells and can omit material components only when they have no cost and the spell does not consume them. [SRD p. 21, Archdruid](../../references/srd-5.1/pages/page-021.md)

<a id="srd51-class-druid-circle-of-the-land"></a>

### Circle of the Land

`srd51.class.druid.circle-of-the-land`

At level 2 gain Bonus Cantrip and Natural Recovery. The land associated with your initiation determines Circle Spells at levels 3, 5, 7, and 9. Land's Stride arrives at level 6, Nature's Ward at 10, and Nature's Sanctuary at 14. [SRD p. 21, Circle of the Land](../../references/srd-5.1/pages/page-021.md) [SRD p. 22, Circle of the Land continuation](../../references/srd-5.1/pages/page-022.md)

<a id="srd51-class-druid-bonus-cantrip"></a>

#### Bonus Cantrip

`srd51.class.druid.bonus-cantrip`

At level 2 learn one chosen druid cantrip in addition to the base class cantrips. [SRD p. 21, Bonus Cantrip](../../references/srd-5.1/pages/page-021.md)

<a id="srd51-class-druid-natural-recovery"></a>

#### Natural Recovery

`srd51.class.druid.natural-recovery`

From level 2, during a short rest meditate and choose expended spell slots to recover. Their combined spell levels must be at most ceil(druid level ÷ 2), and every chosen slot must be below 6th level. After using this recovery, finish a long rest before doing so again. [SRD p. 21, Natural Recovery](../../references/srd-5.1/pages/page-021.md)

<a id="srd51-class-druid-circle-spells"></a>

#### Circle Spells

`srd51.class.druid.circle-spells`

Choose one of the seven lands below. Gain the spell pair in the matching row at each listed druid level. Once gained, each spell is always prepared, outside the ordinary preparation allowance, and counts as a druid spell for you even if missing from the druid list. [SRD p. 21, Circle Spells](../../references/srd-5.1/pages/page-021.md) [SRD p. 22, Circle Spells continuation](../../references/srd-5.1/pages/page-022.md)

| Land | At level 3 | At level 5 | At level 7 | At level 9 |
| --- | --- | --- | --- | --- |
| Arctic | [Hold Person](spells-h-m.md#srd51-spell-hold-person), [Spike Growth](spells-n-s.md#srd51-spell-spike-growth) | [Sleet Storm](spells-n-s.md#srd51-spell-sleet-storm), [Slow](spells-n-s.md#srd51-spell-slow) | [Freedom of Movement](spells-e-g.md#srd51-spell-freedom-of-movement), [Ice Storm](spells-h-m.md#srd51-spell-ice-storm) | [Commune with Nature](spells-a-c.md#srd51-spell-commune-with-nature), [Cone of Cold](spells-a-c.md#srd51-spell-cone-of-cold) |
| Coast | [Mirror Image](spells-h-m.md#srd51-spell-mirror-image), [Misty Step](spells-h-m.md#srd51-spell-misty-step) | [Water Breathing](spells-t-z.md#srd51-spell-water-breathing), [Water Walk](spells-t-z.md#srd51-spell-water-walk) | [Control Water](spells-a-c.md#srd51-spell-control-water), [Freedom of Movement](spells-e-g.md#srd51-spell-freedom-of-movement) | [Conjure Elemental](spells-a-c.md#srd51-spell-conjure-elemental), [Scrying](spells-n-s.md#srd51-spell-scrying) |
| Desert | [Blur](spells-a-c.md#srd51-spell-blur), [Silence](spells-n-s.md#srd51-spell-silence) | [Create Food and Water](spells-a-c.md#srd51-spell-create-food-and-water), [Protection from Energy](spells-n-s.md#srd51-spell-protection-from-energy) | [Blight](spells-a-c.md#srd51-spell-blight), [Hallucinatory Terrain](spells-h-m.md#srd51-spell-hallucinatory-terrain) | [Insect Plague](spells-h-m.md#srd51-spell-insect-plague), [Wall of Stone](spells-t-z.md#srd51-spell-wall-of-stone) |
| Forest | [Barkskin](spells-a-c.md#srd51-spell-barkskin), [Spider Climb](spells-n-s.md#srd51-spell-spider-climb) | [Call Lightning](spells-a-c.md#srd51-spell-call-lightning), [Plant Growth](spells-n-s.md#srd51-spell-plant-growth) | [Divination](spells-d-e.md#srd51-spell-divination), [Freedom of Movement](spells-e-g.md#srd51-spell-freedom-of-movement) | [Commune with Nature](spells-a-c.md#srd51-spell-commune-with-nature), [Tree Stride](spells-t-z.md#srd51-spell-tree-stride) |
| Grassland | [Invisibility](spells-h-m.md#srd51-spell-invisibility), [Pass without Trace](spells-n-s.md#srd51-spell-pass-without-trace) | [Daylight](spells-d-e.md#srd51-spell-daylight), [Haste](spells-h-m.md#srd51-spell-haste) | [Divination](spells-d-e.md#srd51-spell-divination), [Freedom of Movement](spells-e-g.md#srd51-spell-freedom-of-movement) | [Dream](spells-d-e.md#srd51-spell-dream), [Insect Plague](spells-h-m.md#srd51-spell-insect-plague) |
| Mountain | [Spider Climb](spells-n-s.md#srd51-spell-spider-climb), [Spike Growth](spells-n-s.md#srd51-spell-spike-growth) | [Lightning Bolt](spells-h-m.md#srd51-spell-lightning-bolt), [Meld into Stone](spells-h-m.md#srd51-spell-meld-into-stone) | [Stone Shape](spells-n-s.md#srd51-spell-stone-shape), [Stoneskin](spells-n-s.md#srd51-spell-stoneskin) | [Passwall](spells-n-s.md#srd51-spell-passwall), [Wall of Stone](spells-t-z.md#srd51-spell-wall-of-stone) |
| Swamp | [Acid Arrow](spells-a-c.md#srd51-spell-acid-arrow), [Darkness](spells-d-e.md#srd51-spell-darkness) | [Water Walk](spells-t-z.md#srd51-spell-water-walk), [Stinking Cloud](spells-n-s.md#srd51-spell-stinking-cloud) | [Freedom of Movement](spells-e-g.md#srd51-spell-freedom-of-movement), [Locate Creature](spells-h-m.md#srd51-spell-locate-creature) | [Insect Plague](spells-h-m.md#srd51-spell-insect-plague), [Scrying](spells-n-s.md#srd51-spell-scrying) |

<a id="srd51-class-druid-lands-stride"></a>

#### Land's Stride

`srd51.class.druid.lands-stride`

From level 6, nonmagical difficult terrain costs you no extra movement. Pass through nonmagical plants without being slowed or taking damage from their thorns, spines, or similar hazards. Gain advantage on saves against plants magically created or manipulated to impede movement, such as plants from [Entangle](spells-d-e.md#srd51-spell-entangle). [SRD p. 22, Land's Stride](../../references/srd-5.1/pages/page-022.md)

<a id="srd51-class-druid-natures-ward"></a>

#### Nature's Ward

`srd51.class.druid.natures-ward`

From level 10, elementals and fey cannot charm or frighten you. You are immune to poison and disease. [SRD p. 22, Nature's Ward](../../references/srd-5.1/pages/page-022.md)

<a id="srd51-class-druid-natures-sanctuary"></a>

#### Nature's Sanctuary

`srd51.class.druid.natures-sanctuary`

From level 14, a beast or plant creature that attacks you must make a Wisdom save against your druid spell save DC. It knows about this effect before attacking. On failure it must choose another target or the attack automatically misses. A success grants the creature immunity to your effect for 24 hours. [SRD p. 22, Nature's Sanctuary](../../references/srd-5.1/pages/page-022.md)

## Source-checked cases

- **Ordinary.** A cleric of level 3 with Wisdom modifier +3 can prepare six eligible cleric spells. The Life domain's [Bless](spells-a-c.md#srd51-spell-bless), [Cure Wounds](spells-a-c.md#srd51-spell-cure-wounds), [Lesser Restoration](spells-h-m.md#srd51-spell-lesser-restoration), and [Spiritual Weapon](spells-n-s.md#srd51-spell-spiritual-weapon) are prepared in addition to those six. [SRD p. 16, Preparing and Casting Spells and Domain Spells](../../references/srd-5.1/pages/page-016.md) [SRD p. 17, Life Domain Spells](../../references/srd-5.1/pages/page-017.md)
- **Boundary.** A CR 1 undead that fails against a level 8 cleric's Turn Undead is destroyed. A CR 2 undead with the same failure is turned instead. [SRD p. 16, Turn Undead](../../references/srd-5.1/pages/page-016.md) [SRD p. 17, Destroy Undead](../../references/srd-5.1/pages/page-017.md)
- **Exception.** A druid whose Wild Shape has 4 hit points remaining and takes 12 damage reverts and subtracts 8 from the druid's pre-transformation hit points. If those hit points were 9, the druid now has 1 and that hit point drop leaves the druid conscious. [SRD p. 20, Wild Shape](../../references/srd-5.1/pages/page-020.md) [SRD p. 21, Wild Shape continuation](../../references/srd-5.1/pages/page-021.md)
- **Invalid.** A druid of level 3 cannot take a beast form with a swim speed. A Circle of the Land druid of level 12 also cannot recover a 6th-level slot with Natural Recovery even though the combined-level budget is six. [SRD p. 20, Beast Shapes](../../references/srd-5.1/pages/page-020.md) [SRD p. 21, Natural Recovery](../../references/srd-5.1/pages/page-021.md)

## Source coverage

| Source | Destination |
| --- | --- |
| [SRD p. 15, Cleric Class Features and The Cleric](../../references/srd-5.1/pages/page-015.md) · [SRD p. 16, Spellcasting continuation](../../references/srd-5.1/pages/page-016.md) | [Cleric](#srd51-class-cleric), [Cleric Spellcasting](#srd51-class-cleric-spellcasting), [full-caster slot progression](character-advancement.md#srd51-character-full-caster-slots), and cleric level gates above |
| [SRD p. 16, Divine Domain through Turn Undead](../../references/srd-5.1/pages/page-016.md) | [Divine Domain](#srd51-class-cleric-divine-domain), [Channel Divinity](#srd51-class-cleric-channel-divinity), [Turn Undead](#srd51-class-cleric-turn-undead) |
| [SRD p. 17, Ability Score Improvement through Divine Intervention](../../references/srd-5.1/pages/page-017.md) | [Ability Score Improvement](#srd51-class-cleric-ability-score-improvement), [Destroy Undead](#srd51-class-cleric-destroy-undead), [Divine Intervention](#srd51-class-cleric-divine-intervention) |
| [SRD p. 17, Life Domain through Supreme Healing](../../references/srd-5.1/pages/page-017.md) · [SRD p. 18, Supreme Healing continuation](../../references/srd-5.1/pages/page-018.md) | [Life Domain and Life Domain Spells](#srd51-class-cleric-life-domain), [Bonus Proficiency](#srd51-class-cleric-bonus-proficiency), [Disciple of Life](#srd51-class-cleric-disciple-of-life), [Preserve Life](#srd51-class-cleric-preserve-life), [Blessed Healer](#srd51-class-cleric-blessed-healer), [Divine Strike](#srd51-class-cleric-divine-strike), [Supreme Healing](#srd51-class-cleric-supreme-healing) |
| [SRD p. 19, Druid Class Features, The Druid, and Druidic](../../references/srd-5.1/pages/page-019.md) · [SRD p. 20, Spellcasting continuation](../../references/srd-5.1/pages/page-020.md) | [Druid](#srd51-class-druid), [Druidic](#srd51-class-druid-druidic), [Druid Spellcasting](#srd51-class-druid-spellcasting), [full-caster slot progression](character-advancement.md#srd51-character-full-caster-slots), and druid level gates above |
| [SRD p. 20, Wild Shape](../../references/srd-5.1/pages/page-020.md) · [SRD p. 21, Wild Shape continuation through Archdruid](../../references/srd-5.1/pages/page-021.md) | [Wild Shape](#srd51-class-druid-wild-shape), [Druid Circle](#srd51-class-druid-druid-circle), [Ability Score Improvement](#srd51-class-druid-ability-score-improvement), [Timeless Body](#srd51-class-druid-timeless-body), [Beast Spells](#srd51-class-druid-beast-spells), [Archdruid](#srd51-class-druid-archdruid) |
| [SRD p. 21, Circle of the Land](../../references/srd-5.1/pages/page-021.md) · [SRD p. 22, Circle Spells continuation through Nature's Sanctuary](../../references/srd-5.1/pages/page-022.md) | [Circle of the Land](#srd51-class-druid-circle-of-the-land), [Bonus Cantrip](#srd51-class-druid-bonus-cantrip), [Natural Recovery](#srd51-class-druid-natural-recovery), [Circle Spells](#srd51-class-druid-circle-spells), [Land's Stride](#srd51-class-druid-lands-stride), [Nature's Ward](#srd51-class-druid-natures-ward), [Nature's Sanctuary](#srd51-class-druid-natures-sanctuary) |
| [SRD p. 22, Sacred Plants and Wood](../../references/srd-5.1/pages/page-022.md) · [SRD p. 23, Druids and the Gods](../../references/srd-5.1/pages/page-023.md) | The examples of sacred wood, plant symbolism, and druid worship are characterization. Focus permissions are in [Druid Spellcasting](#srd51-class-druid-spellcasting). |

The rendered PDF confirmed the cleric and druid slot and cantrip progressions, Destroy Undead thresholds, Life Domain spells, Wild Shape restrictions, and all Circle Spell pairs on pp. 15, 17, 19–20, and 22.
