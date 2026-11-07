# Diseases, madness, objects, and poisons

<a id="srd51-hazard-diseases"></a>
## Diseases

`srd51.hazard.diseases`

Resolve infection, affected creatures, transmission, symptoms, and cure by the specific disease. A disease can affect any kind of creature, including constructs or undead, if its own eligibility allows it. Diseases can affect only a particular ancestry or creature type and do not inherently cross between them. The GM can change the sample diseases' DCs, incubation times, or other parameters to fit the campaign. A small outbreak may be treatable with a spell such as [Lesser Restoration](spells-h-m.md#srd51-spell-lesser-restoration), while larger outbreaks can require investigating the disease, stopping its spread, and securing a cure. [SRD p. 199, Diseases and Sample Diseases](../../references/srd-5.1/pages/page-199.md)

<a id="srd51-hazard-cackle-fever"></a>
### Cackle Fever

`srd51.hazard.cackle-fever`

The disease affects humanoids, with gnomes immune. Fever and disorientation appear 1d4 hours after infection and grant one [exhaustion](conditions.md#srd51-condition-exhaustion) level that cannot be removed before cure. A greatly stressful event, such as entering combat, taking damage, becoming afraid, or having a nightmare, requires a Constitution save using the current laughter DC, initially 13. Failure deals 5 (1d10) psychic damage and imposes [incapacitated](conditions.md#srd51-condition-incapacitated) with laughter for one minute. The creature may repeat the save at the end of each of its turns, ending that fit and its incapacitation on success. [SRD p. 199, Cackle Fever](../../references/srd-5.1/pages/page-199.md)

A susceptible humanoid starting a turn within 10 feet of a creature in a fit makes a DC 10 Constitution save. Failure infects it, and success makes it immune to fits from that particular carrier for 24 hours. At the end of each long rest the infected creature may make a Constitution save against its current recovery DC, initially 13. Success reduces both the recovery DC and the laughter DC by the same 1d6. When the reduced DC reaches 0 the creature recovers. A third failed recovery save gives one randomly selected [indefinite madness](#srd51-hazard-indefinite-madness). The source sets no consecutive-failure requirement. [SRD p. 199, Cackle Fever continuation](../../references/srd-5.1/pages/page-199.md)

**Project interpretation.** Apply the stress and recovery procedures after symptoms manifest. The disease's provisions place them after the onset description, although the individual clauses refer to an infected creature. Treat the psychic damage from resolving a fit as part of that response and do not use it to recursively trigger another save from the same disease. Otherwise the response to one stress event could generate a chain of saves and damage with no stated limit. A recovery reduction that would pass below 0 also cures the disease, treating 0 as the end of the DC progression. Cure removes the restriction on recovering the fever's exhaustion level. The granted level otherwise follows ordinary exhaustion recovery, and the granted indefinite madness has its own cure. [SRD p. 199, Cackle Fever](../../references/srd-5.1/pages/page-199.md) · [SRD p. 358, Exhaustion](../../references/srd-5.1/pages/page-358.md) · [SRD p. 202, Curing Madness](../../references/srd-5.1/pages/page-202.md)

<a id="srd51-hazard-sewer-plague"></a>
### Sewer Plague

`srd51.hazard.sewer-plague`

A humanoid bitten by a carrier or contacting contaminated filth or waste must succeed on a DC 11 Constitution save or become infected. Sewers, refuse, and stagnant swamps are typical sources, and creatures there can carry it. Fatigue and cramps appear after 1d4 days. The disease adds one exhaustion level, halves healing from spent Hit Dice, and prevents hit point recovery from finishing a long rest. [SRD p. 199, Sewer Plague](../../references/srd-5.1/pages/page-199.md)

At the end of each long rest the infected creature makes a mandatory DC 11 Constitution save. Failure adds one exhaustion level. Success lowers exhaustion one level and cures the disease if that reduction puts exhaustion below level 1. **Project interpretation.** Apply the disease's long-rest procedures after symptoms manifest. Determine the completed rest's healing while the disease still applies, then resolve its save before any independent food-and-drink reduction of exhaustion. Treat a successful one-level reduction at an already-zero level as reaching the cure threshold without tracking negative exhaustion. This gives the disease's save an opportunity to cure at the threshold and keeps the ordinary exhaustion recovery provision. The source does not state a relative order for these end-of-rest rules. For half Hit Die healing use the greatest whole value no greater than half the ordinary healing, consistent with the integer convention for other halved hit point changes. [SRD p. 199, Sewer Plague](../../references/srd-5.1/pages/page-199.md) · [SRD p. 87, Short Rest and Long Rest](../../references/srd-5.1/pages/page-087.md) · [SRD p. 358, Exhaustion](../../references/srd-5.1/pages/page-358.md)

<a id="srd51-hazard-sight-rot"></a>
### Sight Rot

`srd51.hazard.sight-rot`

A beast or humanoid drinking contaminated water makes a DC 15 Constitution save and contracts Sight Rot on failure. One day later blurred vision imposes −1 on attack rolls and ability checks that depend on sight. The penalty increases by 1 at the end of each long rest after symptoms begin. At −5 the creature is [blinded](conditions.md#srd51-condition-blinded) until magic restores its sight, such as [Lesser Restoration](spells-h-m.md#srd51-spell-lesser-restoration) or [Heal](spells-h-m.md#srd51-spell-heal). [SRD p. 199, Sight Rot](../../references/srd-5.1/pages/page-199.md) · [SRD p. 200, Sight Rot continuation](../../references/srd-5.1/pages/page-200.md)

Eyebright, a rare flower found in some swamps, can be made into one dose of eye ointment in one hour by a character proficient with an herbalism kit. A dose applied to the eyes before a long rest prevents that rest from worsening the disease. Three doses cure it entirely. The source gives no consecutive-dose requirement. [SRD p. 200, Sight Rot continuation](../../references/srd-5.1/pages/page-200.md)

**Project interpretation.** The full three-dose cure also ends the disease's sight penalty and restores sight if it has already blinded the creature. The blindness passage supplies magic as the ordinary sight-restoring remedy, and the specific ointment procedure states it cures the disease entirely. A narrower reading would stop disease progression with ointment while still requiring a separate magical restoration of vision. [SRD p. 200, Sight Rot continuation](../../references/srd-5.1/pages/page-200.md)

**Acceptance cases.** A gnome is ineligible for Cackle Fever infection. A humanoid succeeding on its contagion save against one carrier can still be exposed to a different carrier during the next 24 hours. An untreated Sight Rot penalty of −4 worsens to −5 after a long rest and blinds the creature. Ointment before that rest holds the penalty at −4. [SRD p. 199, Cackle Fever and Sight Rot](../../references/srd-5.1/pages/page-199.md) · [SRD p. 200, Sight Rot continuation](../../references/srd-5.1/pages/page-200.md)

<a id="srd51-hazard-madness"></a>
## Madness

`srd51.hazard.madness`

The GM can use madness rules for horrific or extraordinary stress, and may use them in place of an insanity-producing spell's own effect. Triggers can include magic, disease, poison, planar phenomena, and using or attuning to an artifact. Resistance usually uses a Wisdom or Charisma save, with the inducing effect determining the actual requirement. Choose the severity supplied by the source effect or GM. Mundane sources typically give short-term madness. More severe or cumulative causes can give long-term or indefinite madness. Select from the appropriate d100 outcomes below. [SRD p. 201, Madness, Going Mad, and Madness Effects](../../references/srd-5.1/pages/page-201.md)

<a id="srd51-hazard-short-term-madness"></a>
### Short-term madness

`srd51.hazard.short-term-madness`

The selected effect lasts 1d10 minutes unless the outcome ends it earlier. All ranges include their endpoints. [SRD p. 201, Short-Term Madness](../../references/srd-5.1/pages/page-201.md)

- **Loss of control:** **01–20** gives mental withdrawal and paralysis, broken early by any damage. **81–90** imposes Stunned. **91–100** imposes Unconscious.
- **Communication and perception:** **21–30** incapacitates the character and occupies the time with screams, laughter, or tears. **41–50** replaces ordinary speech with babbling and prevents casting. **61–70** brings hallucinations that give disadvantage on checks.
- **Compelled behavior:** **31–40** imposes Frightened and requires action and movement each round to flee its source. **51–60** requires using the action each round to attack the nearest creature. **71–75** compels obedience to anyone's directions unless obviously self-destructive. **76–80** imposes a powerful appetite for something abnormal, such as earth or refuse.

<a id="srd51-hazard-long-term-madness"></a>
### Long-term madness

`srd51.hazard.long-term-madness`

The selected effect lasts 1d10 × 10 hours. [SRD p. 201, Long-Term Madness](../../references/srd-5.1/pages/page-201.md)

- **Behavior and attachment:** **01–10** compels repetition of one activity, for instance washing or counting. **31–40** causes intense revulsion toward something, often the madness's cause, under the Antipathy behavior of [Antipathy/Sympathy](spells-a-c.md#srd51-spell-antipathy-sympathy). **46–55** fixes the character's attachment on a person or thing as a lucky charm. Beyond 30 feet from it, the character has disadvantage on attacks, checks, and saves.
- **Perception and cognition:** **11–20** gives hallucinations and disadvantage on checks. **21–30** gives paranoia and disadvantage on Wisdom and Charisma checks. **41–45** requires choosing a potion whose effects the character imagines experiencing. **76–85** keeps the character's identity, ancestry traits, and class features intact but removes recognition of other people and all memories from before onset.
- **Physical and reactive impairments:** **56–65** imposes Blinded with 25% probability or Deafened with 75%. **66–75** gives tremors or tics, imposing disadvantage on attacks, checks, and saves involving Strength or Dexterity. **86–90** calls for a DC 15 Wisdom save each time the character takes damage. Failure brings the failed-save [Confusion](spells-a-c.md#srd51-spell-confusion) effect for 1 minute. **91–95** removes speech. **96–100** imposes Unconscious and prevents waking by either damage or jostling.

<a id="srd51-hazard-indefinite-madness"></a>
### Indefinite madness

`srd51.hazard.indefinite-madness`

The outcome adds a character flaw until cured. The flaw describes behavior or belief. Apply an additional mechanical constraint only when a rule supplies one. [SRD p. 202, Indefinite Madness](../../references/srd-5.1/pages/page-202.md)

- **Convictions about self and others:** **01–15** associates drunkenness with preserving sanity. **51–55** is distress about constant judgment by others. **56–70** is a conviction of being the most intelligent, wise, strong, fast, and beautiful person the character knows. **71–80** is certainty that powerful enemies hunt the character, with agents watching everywhere. **81–85** allows trust in exactly one companion that only the character can see.
- **Conduct toward people and possessions:** **16–25** keeps every found item. **26–30** models dress, mannerisms, and name on a particular acquaintance. **31–35** treats distorted, embellished, or false accounts as necessary to interest other people. **96–100** takes pleasure in killing people.
- **Priorities and reactions:** **36–45** pursues one goal to the exclusion of all else. **46–50** has difficulty caring about anything happening nearby. **86–95** responds unseriously to everything, with increasing amusement as a situation becomes more serious.

<a id="srd51-hazard-curing-madness"></a>
### Curing madness

`srd51.hazard.curing-madness`

[Calm Emotions](spells-a-c.md#srd51-spell-calm-emotions) can suppress effects for the applicable spell duration. [Lesser Restoration](spells-h-m.md#srd51-spell-lesser-restoration) can end short- or long-term madness. Depending on the cause, [Remove Curse](spells-n-s.md#srd51-spell-remove-curse) or [Dispel Evil and Good](spells-d-e.md#srd51-spell-dispel-evil-and-good) may work. Cure indefinite madness with [Greater Restoration](spells-e-g.md#srd51-spell-greater-restoration) or more powerful magic. Suppression keeps the underlying duration in progress. The effect resumes when suppression ends if its own duration or cure has not ended it. [SRD p. 202, Curing Madness](../../references/srd-5.1/pages/page-202.md)

<a id="srd51-hazard-objects"></a>
## Damaging objects

`srd51.hazard.objects`

These rules apply to individual inanimate objects such as a book, door, or window. For a building or vehicle assembled from many objects, adjudicate its constituent parts as applicable. Given enough time and the appropriate tools, characters can destroy a destructible object. When time matters, the GM can assign AC and HP and choose immunities, resistances, or vulnerabilities suited to the material and method. Its AC measures difficulty of dealing damage on a strike. Its HP measures remaining structural integrity. At 0 HP it breaks. [SRD p. 203, Objects and Statistics for Objects](../../references/srd-5.1/pages/page-203.md) · [SRD p. 87, Interacting with Objects](../../references/srd-5.1/pages/page-087.md)

Objects are immune to poison and psychic damage. They automatically fail Strength and Dexterity saves and are immune to effects requiring other saving throws. The GM can choose a damaging method's suitability. Fire may effectively destroy a large cloth object, while a sword may break before harming a thick stone wall. [SRD p. 203, Objects and Damage Types](../../references/srd-5.1/pages/page-203.md) · [SRD p. 87, Interacting with Objects](../../references/srd-5.1/pages/page-087.md)

<a id="srd51-hazard-object-statistics"></a>
### Suggested object statistics

`srd51.hazard.object-statistics`

Suggested material ACs are 11 for cloth, paper, and rope, 13 for crystal, glass, and ice, 15 for wood and bone, 17 for stone, 19 for iron and steel, 21 for mithral, and 23 for adamantine. Choose HP by size and resilience, using the listed fixed value or dice. For fragile Tiny, Small, Medium, and Large objects respectively, the values are 2 (1d4), 3 (1d6), 4 (1d8), and 5 (1d10). For resilient objects of those sizes, use 5 (2d4), 10 (3d6), 18 (4d8), and 27 (5d10). A relatively small component's destruction can suffice to break a larger object. [SRD p. 203, Object Armor Class and Object Hit Points](../../references/srd-5.1/pages/page-203.md)

<a id="srd51-hazard-huge-and-gargantuan-objects"></a>
### Huge and Gargantuan objects

`srd51.hazard.huge-and-gargantuan-objects`

The GM can decide how long the object withstands a particular force or track its hit points. If tracking HP, divide the object into sections of Large or smaller size and track each section separately. Loss of a section can destroy the whole structure, as when a statue topples after one leg breaks. Ordinary weapons may be ineffective against a massive object, while a suitable hazard or spell can still destroy it. [SRD p. 203, Huge and Gargantuan Objects](../../references/srd-5.1/pages/page-203.md)

<a id="srd51-hazard-object-damage-threshold"></a>
### Damage threshold

`srd51.hazard.object-damage-threshold`

When an object has a threshold, a single attack or effect must deal at least that amount for the object to take damage normally. Smaller amounts are superficial and reduce no HP. Meeting the threshold applies the damage normally, without deducting the threshold as a buffer. Separate attacks do not add together to meet it. **Project interpretation.** Compare the damage the object would take after the other applicable damage adjustments, excluding the threshold itself. The source uses the amount the object takes, but gives no explicit order for this comparison. Another reading checks the original roll first. [SRD p. 203, Damage Threshold](../../references/srd-5.1/pages/page-203.md) · [SRD p. 97, Damage Resistance and Vulnerability](../../references/srd-5.1/pages/page-097.md)

<a id="srd51-hazard-poisons"></a>
## Poisons

`srd51.hazard.poisons`

Poison is illegal in most societies in the setting. The type controls exposure, and the particular poison defines its save, effects, and duration. Poison damage and the Poisoned condition are separate outcomes when the poison lists them separately. Values given for each sample are gp per dose and its listed fixed damage value with dice, where applicable. See also [basic poison](adventuring-gear.md#srd51-equipment-basic-poison) for its specific rule and duration. [SRD pp. 204–205, Poisons and Sample Poisons](../../references/srd-5.1/pages/page-204.md) · [SRD p. 205, Sample Poisons continuation](../../references/srd-5.1/pages/page-205.md) · [SRD p. 68, Basic Poison](../../references/srd-5.1/pages/page-068.md)

<a id="srd51-hazard-contact-poison"></a>
### Contact

`srd51.hazard.contact-poison`

Apply to an object. It remains potent until touched or washed off. Contact with exposed skin exposes a creature to the poison's effect. [SRD p. 204, Contact](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-ingested-poison"></a>
### Ingested

`srd51.hazard.ingested-poison`

The poison ordinarily requires swallowing a full dose, which can be delivered in food or liquid. The GM can give a partial dose a reduced effect, for example allowing advantage on the save or halving damage on a failed save. [SRD p. 204, Ingested](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-inhaled-poison"></a>
### Inhaled

`srd51.hazard.inhaled-poison`

Releasing the powder or gas exposes creatures in a 5-foot cube. The cloud then dissipates immediately. Holding one's breath does not prevent exposure, because the substance affects parts such as the eyes or nasal membranes. [SRD p. 204, Inhaled](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-injury-poison"></a>
### Injury

`srd51.hazard.injury-poison`

Apply to a weapon, ammunition, trap component, or other object that deals slashing or piercing damage. It remains potent until delivered in a wound or washed away. Taking piercing or slashing damage from the coated object exposes the creature. [SRD p. 204, Injury](../../references/srd-5.1/pages/page-204.md)

[Basic poison](adventuring-gear.md#srd51-equipment-basic-poison) uses its specific hit trigger and potency duration under the interpretation there. [SRD p. 68, Basic Poison](../../references/srd-5.1/pages/page-068.md) · [SRD p. 204, Injury](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-assassins-blood"></a>
### Assassin's Blood

`srd51.hazard.assassins-blood`

Ingested, 150 gp. Exposure requires a DC 10 Constitution save. Failure deals 6 (1d12) poison damage and poisons the creature for 24 hours. Success deals half damage without imposing the condition. [SRD p. 204, Poisons and Assassin's Blood](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-burnt-othur-fumes"></a>
### Burnt Othur Fumes

`srd51.hazard.burnt-othur-fumes`

Inhaled, 500 gp. Succeed on a DC 13 Constitution save on exposure to avoid the effect. Failure deals 10 (3d6) poison damage and requires another save at the start of each of the creature's turns. Each subsequent failure deals 3 (1d6) poison damage. The ongoing effect ends after three successful saves, with no requirement that they be consecutive. [SRD p. 204, Poisons and Burnt Othur Fumes](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-crawler-mucus"></a>
### Crawler Mucus

`srd51.hazard.crawler-mucus`

Contact, 200 gp. Harvest from a dead or incapacitated crawler. Exposure requires a DC 13 Constitution save. Failure poisons the creature for 1 minute and paralyzes it while poisoned by this effect. The creature can repeat the save at the end of each of its turns and ends the effect on success. [SRD p. 204, Poisons and Crawler Mucus](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-drow-poison"></a>
### Drow Poison

`srd51.hazard.drow-poison`

Injury, 200 gp. Typically made only by drow far from sunlight. Exposure requires a DC 13 Constitution save. Failure poisons the creature for 1 hour. If the save total misses the DC by 5 or more, the creature also falls unconscious while poisoned by this dose. Damage or another creature spending an action shaking it awake ends the unconsciousness early. Waking leaves the underlying Poisoned duration in progress. [SRD p. 204, Poisons and Drow Poison](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-essence-of-ether"></a>
### Essence of Ether

`srd51.hazard.essence-of-ether`

Inhaled, 300 gp. Exposure requires a DC 15 Constitution save. Failure poisons the creature for 8 hours, with unconsciousness while poisoned by this dose. Damage or another creature spending an action shaking it awake ends the unconsciousness early. Waking leaves the underlying Poisoned duration in progress. [SRD p. 204, Poisons and Essence of Ether](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-malice"></a>
### Malice

`srd51.hazard.malice`

Inhaled, 250 gp. Exposure requires a DC 15 Constitution save. Failure poisons the creature for 1 hour. It is blinded while poisoned by this dose. [SRD p. 204, Poisons and Malice](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-midnight-tears"></a>
### Midnight Tears

`srd51.hazard.midnight-tears`

Ingested, 1,500 gp. The poison takes effect at midnight after ingestion, unless neutralized beforehand. At that time make a DC 17 Constitution save, taking 31 (9d6) poison damage on failure or half on success. [SRD p. 204, Poisons and Midnight Tears](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-oil-of-taggit"></a>
### Oil of Taggit

`srd51.hazard.oil-of-taggit`

Contact, 400 gp. Exposure requires a DC 13 Constitution save. Failure poisons the creature for 24 hours, with unconsciousness while poisoned by this dose. Taking damage wakes it early, without ending the underlying Poisoned duration. [SRD p. 204, Poisons and Oil of Taggit](../../references/srd-5.1/pages/page-204.md)

<a id="srd51-hazard-pale-tincture"></a>
### Pale Tincture

`srd51.hazard.pale-tincture`

Ingested, 250 gp. On exposure, succeed on a DC 16 Constitution save or take 3 (1d6) poison damage and become poisoned. The poisoned creature repeats the save every 24 hours and takes another 3 (1d6) poison damage on each failure. Damage from this poison cannot be healed while the effect lasts. Seven successful saves end the effect, without any consecutive-success requirement, after which healing the damage is possible. [SRD p. 204, Poisons](../../references/srd-5.1/pages/page-204.md) · [SRD p. 205, Pale Tincture](../../references/srd-5.1/pages/page-205.md)

<a id="srd51-hazard-purple-worm-poison"></a>
### Purple Worm Poison

`srd51.hazard.purple-worm-poison`

Injury, 2,000 gp. Harvest from a dead or incapacitated purple worm. Exposure requires a DC 19 Constitution save, dealing 42 (12d6) poison damage on failure or half on success. [SRD p. 204, Poisons](../../references/srd-5.1/pages/page-204.md) · [SRD p. 205, Purple Worm Poison](../../references/srd-5.1/pages/page-205.md)

<a id="srd51-hazard-serpent-venom"></a>
### Serpent Venom

`srd51.hazard.serpent-venom`

Injury, 200 gp. Harvest from a dead or incapacitated giant poisonous snake. Exposure requires a DC 11 Constitution save, dealing 10 (3d6) poison damage on failure or half on success. [SRD p. 204, Poisons](../../references/srd-5.1/pages/page-204.md) · [SRD p. 205, Serpent Venom](../../references/srd-5.1/pages/page-205.md)

<a id="srd51-hazard-torpor"></a>
### Torpor

`srd51.hazard.torpor`

Ingested, 600 gp. Exposure requires a DC 15 Constitution save. Failure poisons the creature for 4d6 hours. It is incapacitated while poisoned by this dose. [SRD p. 204, Poisons](../../references/srd-5.1/pages/page-204.md) · [SRD p. 205, Torpor](../../references/srd-5.1/pages/page-205.md)

<a id="srd51-hazard-truth-serum"></a>
### Truth Serum

`srd51.hazard.truth-serum`

Ingested, 150 gp. Exposure requires a DC 11 Constitution save. Failure poisons the creature for 1 hour. While poisoned by the dose, it cannot knowingly speak a lie, as under [Zone of Truth](spells-t-z.md#srd51-spell-zone-of-truth). The poison has its own exposure and duration and needs no spell area. [SRD p. 204, Poisons](../../references/srd-5.1/pages/page-204.md) · [SRD p. 205, Truth Serum](../../references/srd-5.1/pages/page-205.md)

<a id="srd51-hazard-wyvern-poison"></a>
### Wyvern Poison

`srd51.hazard.wyvern-poison`

Injury, 1,200 gp. Harvest from a dead or incapacitated wyvern. Exposure requires a DC 15 Constitution save, dealing 24 (7d6) poison damage on failure or half on success. [SRD p. 204, Poisons](../../references/srd-5.1/pages/page-204.md) · [SRD p. 205, Wyvern Poison](../../references/srd-5.1/pages/page-205.md)

**Acceptance cases.** Under Drow Poison, a save total of 8 triggers both conditions while a 9 imposes only Poisoned. A creature in an inhaled poison's cube cannot avoid exposure by holding its breath. Midnight Tears neutralized before midnight deals no damage. Two separate 9-damage attacks against a threshold of 10 deal no damage, and one attack of exactly 10 can deal all 10. A short-term madness roll of 20 can end on damage, while a roll of 21 uses the incapacitation outcome. [SRD p. 201, Short-Term Madness](../../references/srd-5.1/pages/page-201.md) · [SRD p. 203, Damage Threshold](../../references/srd-5.1/pages/page-203.md) · [SRD p. 204, Inhaled, Drow Poison, and Midnight Tears](../../references/srd-5.1/pages/page-204.md)

## Source coverage

| SRD page | Sections and destinations |
| --- | --- |
| [199](../../references/srd-5.1/pages/page-199.md) | Diseases and Sample Diseases → [diseases](#srd51-hazard-diseases). [Cackle Fever](#srd51-hazard-cackle-fever) across both columns, [Sewer Plague](#srd51-hazard-sewer-plague), [Sight Rot](#srd51-hazard-sight-rot) opening. |
| [200](../../references/srd-5.1/pages/page-200.md) | [Sight Rot](#srd51-hazard-sight-rot) penalty progression, blindness, magic restoration and Eyebright treatment continuation. |
| [201](../../references/srd-5.1/pages/page-201.md) | Madness, Going Mad, and Madness Effects → [madness](#srd51-hazard-madness). Both columns of [short-term outcomes](#srd51-hazard-short-term-madness), and all [long-term outcomes](#srd51-hazard-long-term-madness). |
| [202](../../references/srd-5.1/pages/page-202.md) | All [indefinite outcomes](#srd51-hazard-indefinite-madness) and [curing madness](#srd51-hazard-curing-madness). |
| [203](../../references/srd-5.1/pages/page-203.md) | Objects, statistics, both tables, Huge and Gargantuan Objects, Objects and Damage Types, and Damage Threshold → [objects](#srd51-hazard-objects) and all subsections. |
| [204](../../references/srd-5.1/pages/page-204.md) | Poisons, four exposure types, every price and type, and first eight sample poisons → [poisons](#srd51-hazard-poisons) and their named entries. |
| [205](../../references/srd-5.1/pages/page-205.md) | Remaining sample poisons from [Pale Tincture](#srd51-hazard-pale-tincture) to [Wyvern Poison](#srd51-hazard-wyvern-poison). |

The disease and damage vignettes illustrate campaign choices and physical adjudication. Their disease-specific, procedural, and numeric rules are retained.

PDF layout checks covered all d100 intervals across both columns of p. 201 and the indefinite outcomes on p. 202, both object statistics tables on p. 203, and the poison exposure types and price table on p. 204.
