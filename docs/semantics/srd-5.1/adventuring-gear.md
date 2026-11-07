# Adventuring gear, tools, and services

Each catalogue row gives the sale unit where the source specifies a quantity. Listed weight is for that unit. A dash in the weight field represents the source's unlisted value, and a dash in the use column means the local source supplies no special procedure for that item.

<a id="srd51-equipment-adventuring-gear"></a>
## Adventuring gear

`srd51.equipment.adventuring-gear`

Use the general rules for [object interaction](exploration.md#srd51-core-interacting-with-objects), [using an object](combat.md#srd51-core-use-an-object), and [improvised weapons](equipment.md#srd51-equipment-improvised-weapons) unless a profile specifies its own action or resolution. Where a container lists capacity in both volume and weight, treat each as a limit on what fits. A volume given for liquids and another for solids describe different modes. [SRD pp. 66–70, Adventuring Gear and Container Capacity](../../references/srd-5.1/pages/page-066.md) · [SRD p. 69, Adventuring Gear and Container Capacity](../../references/srd-5.1/pages/page-069.md) · [SRD p. 70, Container Capacity continuation](../../references/srd-5.1/pages/page-070.md)

<a id="srd51-equipment-arcane-focus"></a>
### Arcane focus

`srd51.equipment.arcane-focus`

A sorcerer, warlock, or wizard can use an arcane focus for eligible [spell materials](spellcasting.md#srd51-core-material-component). The listed forms include crystal, orb, rod, staff made for this purpose, and wand. The source also permits similar items designed to channel arcane magic. [SRD pp. 66–67, Arcane Focus](../../references/srd-5.1/pages/page-066.md) · [SRD p. 67, Arcane Focus continuation](../../references/srd-5.1/pages/page-067.md)

<a id="srd51-equipment-druidic-focus"></a>
### Druidic focus

`srd51.equipment.druidic-focus`

A druid can use a druidic focus for eligible materials. Forms can include mistletoe or holly, a specially chosen wooden wand or scepter, a staff taken whole from a living tree, and a totem made with parts of sacred animals. [SRD p. 67, Druidic Focus](../../references/srd-5.1/pages/page-067.md)

<a id="srd51-equipment-holy-symbol"></a>
### Holy symbol

`srd51.equipment.holy-symbol`

A cleric or paladin may use a symbol of a deity or pantheon for eligible spell materials. Use it held in a hand, worn visibly, or borne on a shield. It can be an amulet, emblem, or reliquary. [SRD p. 67, Holy Symbol](../../references/srd-5.1/pages/page-067.md)

### Hazardous and medical supplies

<a id="srd51-equipment-holy-water-hit"></a>
#### Holy water hit requirement

`srd51.equipment.holy-water-hit`

**Project interpretation.** Holy water requires the attack to hit before applying its damage to a fiend or undead. Its entry orders an attack and states eligible creature types for the damage but does not repeat the hit condition. This reading uses the ordinary attack procedure and treats the type requirement as an additional filter. [SRD p. 67, Holy Water](../../references/srd-5.1/pages/page-067.md) · [SRD p. 94, Making an Attack](../../references/srd-5.1/pages/page-094.md)

<a id="srd51-equipment-hazardous-supply-damage-modifiers"></a>
#### Damage from hazardous supplies

`srd51.equipment.hazardous-supply-damage-modifiers`

**Project interpretation.** The direct damage from a successful acid or holy water attack uses the listed dice in place of the ordinary improvised die and retains the [weapon damage modifier](combat.md#srd51-core-damage-roll) used for that attack. For example, an acid hit with Dexterity modifier +3 and dice totaling 7 deals 10 acid damage. The entries call for attacks treating the substances as improvised weapons, and name the new dice without expressly replacing the modifier. An alternative reads the listed direct hit damage as a fixed expression. The alchemist's fire and oil attack rolls instead deliver their substances. Their hit branches supply no additional direct weapon damage. The later damage uses only the listed 1d4 or fixed 5, respectively, without a weapon ability modifier, since it comes from the ongoing substance and requires no new attack. [SRD p. 66, Acid and Alchemist's Fire](../../references/srd-5.1/pages/page-066.md) · [SRD p. 67, Holy Water](../../references/srd-5.1/pages/page-067.md) · [SRD p. 68, Oil](../../references/srd-5.1/pages/page-068.md) · [SRD p. 96, Damage Rolls](../../references/srd-5.1/pages/page-096.md)

<a id="srd51-equipment-basic-poison-potency"></a>
#### Basic poison potency

`srd51.equipment.basic-poison-potency`

**Project interpretation.** Basic poison has its own hit trigger and stays potent on the coated weapon for the stated minute even after a hit. Each coated piece of ammunition can expose its own target on a hit. Unlike the [general Injury poison rule](hazards-health.md#srd51-hazard-injury-poison), the specific entry requires a hit without a separate piercing or slashing damage condition, and names drying as the end of potency without a one-wound depletion condition. Under this reading, a hit reduced to 0 weapon damage still calls for the save, and a later hit with the coated weapon in the same minute calls for a new save. An alternative would apply the general wound and one-dose delivery predicates in addition to the specific minute limit. [SRD p. 68, Basic Poison](../../references/srd-5.1/pages/page-068.md) · [SRD p. 204, Injury](../../references/srd-5.1/pages/page-204.md)

| Item and ID | Weight (lb) and price | Use or capacity | Source |
| --- | --- | --- | --- |
| <a id="srd51-equipment-acid"></a> Acid, vial · `srd51.equipment.acid` | 1, 25 gp | As an action, splash on a creature within 5 feet or throw the vial up to 20 feet and shatter it on impact. Make a ranged attack against the creature or object as with an improvised weapon. A hit deals 2d6 acid damage, using the [hazardous-supply damage interpretation](#srd51-equipment-hazardous-supply-damage-modifiers). | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 66, Acid](../../references/srd-5.1/pages/page-066.md) |
| <a id="srd51-equipment-alchemists-fire"></a> Alchemist's fire, flask · `srd51.equipment.alchemists-fire` | 1, 50 gp | The liquid ignites on contact with air. As an action, throw the flask up to 20 feet, shattering it on impact, and make a ranged improvised-weapon attack against a creature or object. A hit ignites the target for 1d4 fire damage at the start of each of its turns, using [periodic object timing](combat.md#srd51-core-periodic-object-timing) for an object. A creature can use an action to attempt a DC 10 Dexterity check to extinguish the fire and end the damage. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 66, Alchemist's fire](../../references/srd-5.1/pages/page-066.md) |
| <a id="srd51-equipment-antitoxin"></a> Antitoxin, vial · `srd51.equipment.antitoxin` | —, 50 gp | Drinking the vial grants advantage on saves against poison for 1 hour. Undead and constructs gain no benefit. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 66, Antitoxin](../../references/srd-5.1/pages/page-066.md) |
| <a id="srd51-equipment-ball-bearings"></a> Ball bearings, bag of 1,000 · `srd51.equipment.ball-bearings` | 2, 1 gp | As an action, scatter the bag over a level 10-foot square. A creature crossing the area must succeed on a DC 10 Dexterity save or fall prone. Moving through at half speed avoids the save. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Ball bearings](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-caltrops"></a> Caltrops, bag of 20 · `srd51.equipment.caltrops` | 2, 1 gp | As an action, spread one bag over a 5-foot square. A creature entering the area must succeed on a DC 15 Dexterity save or stop moving for that turn and take 1 piercing damage. Taking that damage reduces its walking speed by 10 feet until it regains at least 1 hit point. Moving through at half speed avoids the save. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Caltrops](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-holy-water"></a> Holy water, flask · `srd51.equipment.holy-water` | 1, 25 gp | As an action, splash a creature within 5 feet or throw the flask up to 20 feet, shattering on impact. Make a ranged attack against the creature as an improvised weapon. A hit deals 2d6 radiant damage to a fiend or undead, applying the [hit requirement](#srd51-equipment-holy-water-hit) and [damage interpretation](#srd51-equipment-hazardous-supply-damage-modifiers). A cleric or paladin can make holy water in a 1-hour special ritual that uses 25 gp of powdered silver and expends a 1st-level slot. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Holy water](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-hunting-trap"></a> Hunting trap · `srd51.equipment.hunting-trap` | 25, 5 gp | Use an action to set the steel trap and affix its chain to an immobile object. A creature stepping on the central plate makes a DC 13 Dexterity save. Failure deals 1d4 piercing damage and stops movement. Until freed, movement is bounded by the chain, usually 3 feet. A creature can spend its action on a DC 13 Strength check to free itself or a captive within its reach. Each failed check deals the captive 1 piercing damage. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Hunting trap](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-oil"></a> Oil, 1-pint flask · `srd51.equipment.oil` | 1, 1 sp | A flask holds one pint. Spend an action to coat a creature or object with a ranged improvised-weapon attack, splashing a creature within 5 feet or throwing and breaking the flask up to 20 feet away. For 1 minute after a hit, fire damage to the coated target triggers 5 additional fire damage. Alternatively, pour a flask over a level square 5 feet on a side. If ignited, that square burns for 2 rounds. A creature entering it or ending its turn there takes 5 fire damage, limited to one instance per creature per turn. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Oil](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-basic-poison"></a> Poison, basic, vial · `srd51.equipment.basic-poison` | —, 100 gp | Use an action to apply one vial to one weapon that deals slashing or piercing, or to as many as three pieces of ammunition. For one minute after application, a hit on a creature with coated equipment forces a DC 10 Constitution save. Failure deals 1d4 poison damage. At the end of that minute, the coating dries. Apply the [potency interpretation](#srd51-equipment-basic-poison-potency) to repeat hits. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Poison](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-potion-of-healing"></a> Potion of healing · `srd51.equipment.potion-of-healing` | ½, 50 gp | Spend an action to drink or administer it. The drinker regains 2d4 + 2 hit points. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Potion of healing](../../references/srd-5.1/pages/page-068.md) |

### Ammunition and spellcasting equipment

| Item and ID | Weight (lb) and price | Use or capacity | Source |
| --- | --- | --- | --- |
| <a id="srd51-equipment-arrows"></a> Arrows, 20 · `srd51.equipment.arrows` | 1, 1 gp | Ammunition for bows. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-blowgun-needles"></a> Blowgun needles, 50 · `srd51.equipment.blowgun-needles` | 1, 1 gp | Ammunition for blowguns. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-crossbow-bolts"></a> Crossbow bolts, 20 · `srd51.equipment.crossbow-bolts` | 1½, 1 gp | Ammunition for crossbows. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-sling-bullets"></a> Sling bullets, 20 · `srd51.equipment.sling-bullets` | 1½, 4 cp | Ammunition for slings. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-arcane-focus-crystal"></a> Arcane focus, crystal · `srd51.equipment.arcane-focus-crystal` | 1, 10 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-arcane-focus-orb"></a> Arcane focus, orb · `srd51.equipment.arcane-focus-orb` | 3, 20 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-arcane-focus-rod"></a> Arcane focus, rod · `srd51.equipment.arcane-focus-rod` | 2, 10 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-arcane-focus-staff"></a> Arcane focus, staff · `srd51.equipment.arcane-focus-staff` | 4, 5 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-arcane-focus-wand"></a> Arcane focus, wand · `srd51.equipment.arcane-focus-wand` | 1, 10 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-component-pouch"></a> Component pouch · `srd51.equipment.component-pouch` | 2, 25 gp | Watertight compartmentalized belt pouch that supplies ordinary material components subject to the material component rule. It does not supply components with a listed cost. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Component pouch](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-druidic-focus-mistletoe"></a> Druidic focus, sprig of mistletoe · `srd51.equipment.druidic-focus-mistletoe` | —, 1 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-druidic-focus-totem"></a> Druidic focus, totem · `srd51.equipment.druidic-focus-totem` | —, 1 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-druidic-focus-wooden-staff"></a> Druidic focus, wooden staff · `srd51.equipment.druidic-focus-wooden-staff` | 4, 5 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-druidic-focus-yew-wand"></a> Druidic focus, yew wand · `srd51.equipment.druidic-focus-yew-wand` | 1, 10 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-holy-symbol-amulet"></a> Holy symbol, amulet · `srd51.equipment.holy-symbol-amulet` | 1, 5 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-holy-symbol-emblem"></a> Holy symbol, emblem · `srd51.equipment.holy-symbol-emblem` | —, 5 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-holy-symbol-reliquary"></a> Holy symbol, reliquary · `srd51.equipment.holy-symbol-reliquary` | 2, 5 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-spellbook"></a> Spellbook · `srd51.equipment.spellbook` | 3, 50 gp | Leather-bound book with 100 blank vellum pages suitable for recording wizard spells. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Spellbook](../../references/srd-5.1/pages/page-068.md) |

### Lighting and fire

| Item and ID | Weight (lb) and price | Use or capacity | Source |
| --- | --- | --- | --- |
| <a id="srd51-equipment-candle"></a> Candle · `srd51.equipment.candle` | —, 1 cp | Provides bright light in a 5-foot radius and dim light for 5 feet beyond it, for 1 hour. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Candle](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-lamp"></a> Lamp · `srd51.equipment.lamp` | 1, 5 sp | Burns for 6 hours on one pint of oil, with bright light out to 15 feet and dim light 30 feet beyond. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Lamp](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-bullseye-lantern"></a> Lantern, bullseye · `srd51.equipment.bullseye-lantern` | 2, 10 gp | Burns for 6 hours on one pint of oil, with a 60-foot cone of bright light and dim light for an additional 60 feet. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Lantern](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-hooded-lantern"></a> Lantern, hooded · `srd51.equipment.hooded-lantern` | 2, 5 gp | Burns for 6 hours on one pint of oil, with bright light out to 30 feet and dim light 30 feet beyond. Spend an action to lower the hood and leave only dim light within 5 feet. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Lantern](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-tinderbox"></a> Tinderbox · `srd51.equipment.tinderbox` | 1, 5 sp | Contains flint, fire steel, and tinder. Lighting a torch or abundant exposed fuel takes one action. Lighting another fire takes one minute. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Tinderbox](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-torch"></a> Torch · `srd51.equipment.torch` | 1, 1 cp | Burns for 1 hour. It provides bright light in a 20-foot radius and dim light for 20 feet beyond. A hit with a burning torch in melee deals 1 fire damage. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Torch](../../references/srd-5.1/pages/page-068.md) |

### Containers

| Item and ID | Weight (lb) and price | Use or capacity | Source |
| --- | --- | --- | --- |
| <a id="srd51-equipment-backpack"></a> Backpack · `srd51.equipment.backpack` | 5, 2 gp | Holds up to 1 cubic foot and 30 lb of gear. Items such as rope or a bedroll can be strapped to its outside. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 69, Container Capacity](../../references/srd-5.1/pages/page-069.md) · [SRD p. 70, Container Capacity](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-barrel"></a> Barrel · `srd51.equipment.barrel` | 70, 2 gp | Holds 40 gallons of liquid or 4 cubic feet of solid contents. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 69, Container Capacity](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-basket"></a> Basket · `srd51.equipment.basket` | 2, 4 sp | Holds up to 2 cubic feet and 40 lb of gear. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 69, Container Capacity](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-glass-bottle"></a> Bottle, glass · `srd51.equipment.glass-bottle` | 2, 2 gp | Holds 1½ pints of liquid. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 69, Container Capacity](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-bucket"></a> Bucket · `srd51.equipment.bucket` | 2, 5 cp | Holds 3 gallons of liquid or ½ cubic foot of solid contents. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 69, Container Capacity](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-crossbow-bolt-case"></a> Case, crossbow bolt · `srd51.equipment.crossbow-bolt-case` | 1, 1 gp | Holds up to 20 crossbow bolts. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Case](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-map-or-scroll-case"></a> Case, map or scroll · `srd51.equipment.map-or-scroll-case` | 1, 1 gp | Holds up to 10 rolled sheets of paper or 5 rolled sheets of parchment. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Case](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-chest"></a> Chest · `srd51.equipment.chest` | 25, 5 gp | Holds up to 12 cubic feet and 300 lb of gear. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 69, Container Capacity](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-flask-or-tankard"></a> Flask or tankard · `srd51.equipment.flask-or-tankard` | 1, 2 cp | Holds 1 pint of liquid. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 70, Container Capacity](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-jug-or-pitcher"></a> Jug or pitcher · `srd51.equipment.jug-or-pitcher` | 4, 2 cp | Holds 1 gallon of liquid. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 70, Container Capacity](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-iron-pot"></a> Pot, iron · `srd51.equipment.iron-pot` | 10, 2 gp | Holds 1 gallon of liquid. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 70, Container Capacity](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-pouch"></a> Pouch · `srd51.equipment.pouch` | 1, 5 sp | Holds up to ⅕ cubic foot and 6 lb of gear. It can hold up to 20 sling bullets or 50 blowgun needles. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Pouch](../../references/srd-5.1/pages/page-068.md) · [SRD p. 70, Container Capacity](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-quiver"></a> Quiver · `srd51.equipment.quiver` | 1, 1 gp | Holds up to 20 arrows. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Quiver](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-sack"></a> Sack · `srd51.equipment.sack` | ½, 1 cp | Holds up to 1 cubic foot and 30 lb of gear. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 70, Container Capacity](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-vial"></a> Vial · `srd51.equipment.vial` | —, 1 gp | Holds 4 ounces of liquid. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 70, Container Capacity](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-waterskin"></a> Waterskin · `srd51.equipment.waterskin` | 5 full, 2 sp | Holds 4 pints of liquid. The listed weight is for a full waterskin. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 69, Container Capacity](../../references/srd-5.1/pages/page-069.md) · [SRD p. 70, Container Capacity](../../references/srd-5.1/pages/page-070.md) |

### Travel, workshop, and personal gear

| Item and ID | Weight (lb) and price | Use or capacity | Source |
| --- | --- | --- | --- |
| <a id="srd51-equipment-abacus"></a> Abacus · `srd51.equipment.abacus` | 2, 2 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-bedroll"></a> Bedroll · `srd51.equipment.bedroll` | 7, 1 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-bell"></a> Bell · `srd51.equipment.bell` | —, 1 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-blanket"></a> Blanket · `srd51.equipment.blanket` | 3, 5 sp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-block-and-tackle"></a> Block and tackle · `srd51.equipment.block-and-tackle` | 5, 1 gp | With its pulleys, line, and hook attached appropriately, hoist up to four times your ordinary lift limit. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Block and tackle](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-book"></a> Book · `srd51.equipment.book` | 5, 25 gp | Contains recorded text or pictures, with content chosen for the book. A book for recording spells uses the spellbook profile. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Book](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-chain"></a> Chain, 10 feet · `srd51.equipment.chain` | 10, 5 gp | Has 10 hit points. A DC 20 Strength check can burst it. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Chain](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-chalk"></a> Chalk, one piece · `srd51.equipment.chalk` | —, 1 cp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-climbers-kit"></a> Climber's kit · `srd51.equipment.climbers-kit` | 12, 25 gp | Includes pitons, boot tips, gloves, and a harness. Use an action to anchor yourself. You cannot fall more than 25 feet from the anchor or climb more than 25 feet away until you undo it. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Climber's kit](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-common-clothes"></a> Clothes, common · `srd51.equipment.common-clothes` | 3, 5 sp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-costume"></a> Clothes, costume · `srd51.equipment.costume` | 4, 5 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-fine-clothes"></a> Clothes, fine · `srd51.equipment.fine-clothes` | 6, 15 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-travelers-clothes"></a> Clothes, traveler's · `srd51.equipment.travelers-clothes` | 4, 2 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-crowbar"></a> Crowbar · `srd51.equipment.crowbar` | 5, 2 gp | Grants advantage on a Strength check when leverage from the crowbar can help. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Crowbar](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-fishing-tackle"></a> Fishing tackle · `srd51.equipment.fishing-tackle` | 4, 1 gp | Includes a rod, line, floats, hooks, sinkers, lures, and narrow netting. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Fishing tackle](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-grappling-hook"></a> Grappling hook · `srd51.equipment.grappling-hook` | 4, 2 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-hammer"></a> Hammer · `srd51.equipment.hammer` | 3, 1 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-sledge-hammer"></a> Hammer, sledge · `srd51.equipment.sledge-hammer` | 10, 2 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-healers-kit"></a> Healer's kit · `srd51.equipment.healers-kit` | 3, 5 gp | Contains bandages, salves, and splints with 10 uses. Spend an action and one use to stabilize a creature at 0 hit points without a Wisdom (Medicine) check. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 67, Healer's kit](../../references/srd-5.1/pages/page-067.md) |
| <a id="srd51-equipment-hourglass"></a> Hourglass · `srd51.equipment.hourglass` | 1, 25 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-ink"></a> Ink, 1-ounce bottle · `srd51.equipment.ink` | —, 10 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-ink-pen"></a> Ink pen · `srd51.equipment.ink-pen` | —, 2 cp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-ladder"></a> Ladder, 10 feet · `srd51.equipment.ladder` | 25, 1 sp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-lock"></a> Lock · `srd51.equipment.lock` | 1, 10 gp | Includes a key. Without it, a creature proficient in thieves' tools can pick the lock with a DC 15 Dexterity check. The GM can offer better, more expensive locks. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Lock](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-magnifying-glass"></a> Magnifying glass · `srd51.equipment.magnifying-glass` | —, 100 gp | Grants advantage on checks to appraise or inspect a small or finely detailed item. It can start a fire with light as bright as sunlight, tinder, and about 5 minutes. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Magnifying glass](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-manacles"></a> Manacles · `srd51.equipment.manacles` | 6, 2 gp | Bind a Small or Medium creature and include a key. Escape requires a DC 20 Dexterity check and breaking them requires a DC 20 Strength check. A creature proficient with thieves' tools can pick the lock without its key with a DC 15 Dexterity check. The manacles have 15 hit points. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Manacles](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-mess-kit"></a> Mess kit · `srd51.equipment.mess-kit` | 1, 2 sp | A tin box with a cup and simple cutlery. One half can serve as a pan and the other as a plate or shallow bowl. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Mess kit](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-steel-mirror"></a> Mirror, steel · `srd51.equipment.steel-mirror` | ½, 5 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-paper"></a> Paper, one sheet · `srd51.equipment.paper` | —, 2 sp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-parchment"></a> Parchment, one sheet · `srd51.equipment.parchment` | —, 1 sp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-perfume"></a> Perfume, vial · `srd51.equipment.perfume` | —, 5 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-miners-pick"></a> Pick, miner's · `srd51.equipment.miners-pick` | 10, 2 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-piton"></a> Piton · `srd51.equipment.piton` | ¼, 5 cp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-pole"></a> Pole, 10 feet · `srd51.equipment.pole` | 7, 5 cp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-portable-ram"></a> Ram, portable · `srd51.equipment.portable-ram` | 35, 4 gp | Adds +4 to the Strength check to break down a door. A single other character can help use the ram, also granting advantage on the check. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Ram](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-rations"></a> Rations, 1 day · `srd51.equipment.rations` | 2, 5 sp | Dried food fit for extended travel. Use the Food rule for requirements and deprivation. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Rations](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-robes"></a> Robes · `srd51.equipment.robes` | 4, 1 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-hempen-rope"></a> Rope, hempen, 50 feet · `srd51.equipment.hempen-rope` | 10, 1 gp | The rope has 2 HP. A successful DC 17 Strength check can burst it. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Rope](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-silk-rope"></a> Rope, silk, 50 feet · `srd51.equipment.silk-rope` | 5, 10 gp | The rope has 2 HP. A successful DC 17 Strength check can burst it. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Rope](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-merchants-scale"></a> Scale, merchant's · `srd51.equipment.merchants-scale` | 3, 5 gp | Its balance, pans, and weights measure small loads precisely up to 2 lb, including goods being appraised. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Scale](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-sealing-wax"></a> Sealing wax · `srd51.equipment.sealing-wax` | —, 5 sp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-shovel"></a> Shovel · `srd51.equipment.shovel` | 5, 2 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-signal-whistle"></a> Signal whistle · `srd51.equipment.signal-whistle` | —, 5 cp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-signet-ring"></a> Signet ring · `srd51.equipment.signet-ring` | —, 5 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-soap"></a> Soap · `srd51.equipment.soap` | —, 2 cp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-iron-spikes"></a> Spikes, iron, 10 · `srd51.equipment.iron-spikes` | 5, 1 gp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |
| <a id="srd51-equipment-spyglass"></a> Spyglass · `srd51.equipment.spyglass` | 1, 1,000 gp | The viewed object appears twice as large. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Spyglass](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-two-person-tent"></a> Tent, two-person · `srd51.equipment.two-person-tent` | 20, 2 gp | Shelters two sleepers. | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) · [SRD p. 68, Tent](../../references/srd-5.1/pages/page-068.md) |
| <a id="srd51-equipment-whetstone"></a> Whetstone · `srd51.equipment.whetstone` | 1, 1 cp | — | [SRD p. 69, Adventuring Gear](../../references/srd-5.1/pages/page-069.md) |

<a id="srd51-equipment-equipment-packs"></a>
## Equipment packs

`srd51.equipment.equipment-packs`

A class's starting equipment can grant a pack. A character buying gear may instead purchase a pack at the listed bundled price. [SRD p. 70, Equipment Packs](../../references/srd-5.1/pages/page-070.md)

| Pack and ID | Price and contents | Source |
| --- | --- | --- |
| <a id="srd51-equipment-burglars-pack"></a> Burglar's Pack · `srd51.equipment.burglars-pack` | 16 gp. Backpack, 50 ft hempen rope fastened outside, 1,000 ball bearings in a bag, 10 ft string, bell, 5 candles, crowbar, hammer, 10 pitons, hooded lantern, 2 oil flasks, 5 days of rations, tinderbox, waterskin. | [SRD p. 70, Equipment Packs](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-diplomats-pack"></a> Diplomat's Pack · `srd51.equipment.diplomats-pack` | 39 gp. Chest, 2 map or scroll cases, fine clothes, ink bottle and pen, lamp, 2 oil flasks, 5 sheets of paper, perfume vial, sealing wax, soap. | [SRD p. 70, Equipment Packs](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-dungeoneers-pack"></a> Dungeoneer's Pack · `srd51.equipment.dungeoneers-pack` | 12 gp. Backpack, 50 ft hempen rope fastened outside, crowbar, hammer, 10 pitons, 10 torches, tinderbox, 10 days of rations, waterskin. | [SRD p. 70, Equipment Packs](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-entertainers-pack"></a> Entertainer's Pack · `srd51.equipment.entertainers-pack` | 40 gp. Backpack, bedroll, 2 costumes, 5 candles, 5 days of rations, waterskin, disguise kit. | [SRD p. 70, Equipment Packs](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-explorers-pack"></a> Explorer's Pack · `srd51.equipment.explorers-pack` | 10 gp. Backpack, 50 ft hempen rope fastened outside, bedroll, mess kit, tinderbox, 10 torches, 10 days of rations, waterskin. | [SRD p. 70, Equipment Packs](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-priests-pack"></a> Priest's Pack · `srd51.equipment.priests-pack` | 19 gp. Backpack, blanket, 10 candles, tinderbox, alms box, 2 incense blocks, censer, vestments, 2 days of rations, waterskin. | [SRD p. 70, Equipment Packs](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-scholars-pack"></a> Scholar's Pack · `srd51.equipment.scholars-pack` | 40 gp. Backpack, lore book, ink bottle and pen, 10 parchment sheets, small sand bag, small knife. | [SRD p. 70, Equipment Packs](../../references/srd-5.1/pages/page-070.md) |

<a id="srd51-equipment-tools"></a>
## Tools

`srd51.equipment.tools`

A tool can enable a task such as crafting, forging, or lockpicking. A proficiency granted by ancestry, class, background, feat, or another rule adds your proficiency bonus to checks using that tool. The task determines the ability. For example, fine engraving can use Dexterity while working a resistant material can call for Strength with the same tools. Vehicle proficiencies use the [vehicle rule](#srd51-equipment-vehicle-proficiency). [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) · [SRD p. 71, Vehicle Proficiency](../../references/srd-5.1/pages/page-071.md)

<a id="srd51-equipment-artisans-tools"></a>
### Artisan's tools

`srd51.equipment.artisans-tools`

Each tool type equips one craft and uses its own proficiency. Add the bonus to checks using those tools in that craft. [Downtime crafting](exploration.md#srd51-core-crafting) can require the proficiency. [SRD p. 70, Artisan's Tools](../../references/srd-5.1/pages/page-070.md) · [SRD p. 88, Crafting](../../references/srd-5.1/pages/page-088.md)

<a id="srd51-equipment-gaming-set"></a>
### Gaming sets

`srd51.equipment.gaming-set`

Each type of game set uses a separate proficiency. Apply it to checks to play a game using that set. Dice and cards are examples, and other kinds can exist. [SRD p. 71, Gaming Set](../../references/srd-5.1/pages/page-071.md)

<a id="srd51-equipment-musical-instrument"></a>
### Musical instruments

`srd51.equipment.musical-instrument`

Each instrument type uses a separate proficiency. Apply it to checks to play music with that instrument. A bard may use a musical instrument as a spellcasting focus. [SRD p. 71, Musical Instrument](../../references/srd-5.1/pages/page-071.md)

### Tool profiles

Artisan, Game, and Instrument in the Use column refer to the corresponding tool rules above. Each kit with a distinct procedure states its own proficiency benefit.

| Tool and ID | Weight (lb) and price | Use | Source |
| --- | --- | --- | --- |
| <a id="srd51-equipment-alchemists-supplies"></a> Alchemist's supplies · `srd51.equipment.alchemists-supplies` | 8, 50 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-brewers-supplies"></a> Brewer's supplies · `srd51.equipment.brewers-supplies` | 9, 20 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-calligraphers-supplies"></a> Calligrapher's supplies · `srd51.equipment.calligraphers-supplies` | 5, 10 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-carpenters-tools"></a> Carpenter's tools · `srd51.equipment.carpenters-tools` | 6, 8 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-cartographers-tools"></a> Cartographer's tools · `srd51.equipment.cartographers-tools` | 6, 15 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-cobblers-tools"></a> Cobbler's tools · `srd51.equipment.cobblers-tools` | 5, 5 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-cooks-utensils"></a> Cook's utensils · `srd51.equipment.cooks-utensils` | 8, 1 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-glassblowers-tools"></a> Glassblower's tools · `srd51.equipment.glassblowers-tools` | 5, 30 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-jewelers-tools"></a> Jeweler's tools · `srd51.equipment.jewelers-tools` | 2, 25 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-leatherworkers-tools"></a> Leatherworker's tools · `srd51.equipment.leatherworkers-tools` | 5, 5 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-masons-tools"></a> Mason's tools · `srd51.equipment.masons-tools` | 8, 10 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-painters-supplies"></a> Painter's supplies · `srd51.equipment.painters-supplies` | 5, 10 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-potters-tools"></a> Potter's tools · `srd51.equipment.potters-tools` | 3, 10 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-smiths-tools"></a> Smith's tools · `srd51.equipment.smiths-tools` | 8, 20 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-tinkers-tools"></a> Tinker's tools · `srd51.equipment.tinkers-tools` | 10, 50 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-weavers-tools"></a> Weaver's tools · `srd51.equipment.weavers-tools` | 5, 1 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-woodcarvers-tools"></a> Woodcarver's tools · `srd51.equipment.woodcarvers-tools` | 5, 1 gp | Artisan | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-dice-set"></a> Dice set · `srd51.equipment.dice-set` | —, 1 sp | Game | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-playing-card-set"></a> Playing card set · `srd51.equipment.playing-card-set` | —, 5 sp | Game | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-bagpipes"></a> Bagpipes · `srd51.equipment.bagpipes` | 6, 30 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-drum"></a> Drum · `srd51.equipment.drum` | 3, 6 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-dulcimer"></a> Dulcimer · `srd51.equipment.dulcimer` | 10, 25 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-flute"></a> Flute · `srd51.equipment.flute` | 1, 2 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-lute"></a> Lute · `srd51.equipment.lute` | 2, 35 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-lyre"></a> Lyre · `srd51.equipment.lyre` | 2, 30 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-horn"></a> Horn · `srd51.equipment.horn` | 2, 3 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-pan-flute"></a> Pan flute · `srd51.equipment.pan-flute` | 2, 12 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-shawm"></a> Shawm · `srd51.equipment.shawm` | 1, 2 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-viol"></a> Viol · `srd51.equipment.viol` | 1, 30 gp | Instrument | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) |
| <a id="srd51-equipment-disguise-kit"></a> Disguise kit · `srd51.equipment.disguise-kit` | 3, 25 gp | Cosmetics, hair dye, and props for altering physical appearance. Apply the proficiency to checks to create visual disguises. | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) · [SRD p. 71, Disguise kit](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-forgery-kit"></a> Forgery kit · `srd51.equipment.forgery-kit` | 5, 15 gp | Papers, parchment, pens, ink, seals, wax, metal leaf, and other supplies to forge physical documents. Apply the proficiency to checks to create physical document forgeries. | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) · [SRD p. 71, Forgery kit](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-herbalism-kit"></a> Herbalism kit · `srd51.equipment.herbalism-kit` | 3, 5 gp | Instruments such as clippers, mortar and pestle, pouches, and vials for making remedies and potions. Apply the proficiency to checks to identify or use herbs. Proficiency is required to make antitoxin or potions of healing. | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) · [SRD p. 71, Herbalism kit](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-navigators-tools"></a> Navigator's tools · `srd51.equipment.navigators-tools` | 2, 25 gp | Instruments for sea navigation. Proficiency enables plotting a course and following charts and applies to checks to avoid getting lost at sea. | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) · [SRD p. 71, Navigator's tools](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-poisoners-kit"></a> Poisoner's kit · `srd51.equipment.poisoners-kit` | 2, 50 gp | Vials, chemicals, and other equipment for making poisons. Proficiency applies to checks to craft or use poisons. | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) · [SRD p. 71, Poisoner's kit](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-thieves-tools"></a> Thieves' tools · `srd51.equipment.thieves-tools` | 1, 25 gp | Contains a file, lock picks, a handled mirror, narrow scissors, and pliers. Proficiency applies to checks to disarm traps or open locks. | [SRD p. 70, Tools](../../references/srd-5.1/pages/page-070.md) · [SRD p. 71, Thieves' tools](../../references/srd-5.1/pages/page-071.md) |

<a id="srd51-equipment-mounts-and-vehicles"></a>
## Mounts and vehicles

`srd51.equipment.mounts-and-vehicles`

For a carriage, cart, chariot, sled, or wagon, total the draft animals' base carrying capacities and multiply by five. That total includes the vehicle's own weight. A draft horse can pull 2,700 lb in total, leaving room for 2,300 lb of load with a 400 lb wagon. Flying and aquatic mounts are rarely sold. Acquiring one can involve rearing it, negotiating with it, or making a bargain with a powerful entity. Apply the [travel](exploration.md#srd51-core-travel-mounts-and-vehicles) or [combat](combat.md#srd51-core-mounted-combat) rules as relevant. [SRD p. 71, Mounts and Vehicles](../../references/srd-5.1/pages/page-071.md) · [SRD p. 72, Mounts and Other Animals and Tack](../../references/srd-5.1/pages/page-072.md)

| Mount and ID | Carrying capacity (lb), speed (ft), and price | Source |
| --- | --- | --- |
| <a id="srd51-equipment-camel"></a> Camel · `srd51.equipment.camel` | 480 lb · 50 ft · 50 gp | [SRD p. 71, Mounts and Other Animals](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-donkey-or-mule"></a> Donkey or mule · `srd51.equipment.donkey-or-mule` | 420 lb · 40 ft · 8 gp | [SRD p. 71, Mounts and Other Animals](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-elephant"></a> Elephant · `srd51.equipment.elephant` | 1,320 lb · 40 ft · 200 gp | [SRD p. 71, Mounts and Other Animals](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-draft-horse"></a> Horse, draft · `srd51.equipment.draft-horse` | 540 lb · 40 ft · 50 gp | [SRD p. 72, Mounts and Other Animals](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-riding-horse"></a> Horse, riding · `srd51.equipment.riding-horse` | 480 lb · 60 ft · 75 gp | [SRD p. 72, Mounts and Other Animals](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-mastiff"></a> Mastiff · `srd51.equipment.mastiff` | 195 lb · 40 ft · 25 gp | [SRD p. 72, Mounts and Other Animals](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-pony"></a> Pony · `srd51.equipment.pony` | 225 lb · 40 ft · 30 gp | [SRD p. 72, Mounts and Other Animals](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-warhorse"></a> Warhorse · `srd51.equipment.warhorse` | 540 lb · 60 ft · 400 gp | [SRD p. 72, Mounts and Other Animals](../../references/srd-5.1/pages/page-072.md) |

<a id="srd51-equipment-barding"></a>
### Barding

`srd51.equipment.barding`

Any listed armor type can be purchased as protective armor fitted to an animal. It costs four times and weighs twice as much as the corresponding humanoid armor. For example, barding using leather armor costs 40 gp and weighs 20 lb. [SRD p. 71, Barding](../../references/srd-5.1/pages/page-071.md) · [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) · [SRD p. 64, Armor](../../references/srd-5.1/pages/page-064.md)

### Tack, drawn vehicles, and care

| Item or service and ID | Weight (lb), price, and effect | Source |
| --- | --- | --- |
| <a id="srd51-equipment-bit-and-bridle"></a> Bit and bridle · `srd51.equipment.bit-and-bridle` | 1, 2 gp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-carriage"></a> Carriage · `srd51.equipment.carriage` | 600, 100 gp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-cart"></a> Cart · `srd51.equipment.cart` | 200, 15 gp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-chariot"></a> Chariot · `srd51.equipment.chariot` | 100, 250 gp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-feed"></a> Feed, one day · `srd51.equipment.feed` | 10, 5 cp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-exotic-saddle"></a> Saddle, exotic · `srd51.equipment.exotic-saddle` | 40, 60 gp · Required for an aquatic or flying mount. | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) · [SRD p. 71, Saddles](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-military-saddle"></a> Saddle, military · `srd51.equipment.military-saddle` | 30, 20 gp · Grants advantage on checks to remain mounted. | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) · [SRD p. 71, Saddles](../../references/srd-5.1/pages/page-071.md) |
| <a id="srd51-equipment-pack-saddle"></a> Saddle, pack · `srd51.equipment.pack-saddle` | 15, 5 gp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-riding-saddle"></a> Saddle, riding · `srd51.equipment.riding-saddle` | 25, 10 gp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-saddlebags"></a> Saddlebags · `srd51.equipment.saddlebags` | 8, 4 gp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-sled"></a> Sled · `srd51.equipment.sled` | 300, 20 gp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-stabling"></a> Stabling, one day · `srd51.equipment.stabling` | —, 5 sp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-wagon"></a> Wagon · `srd51.equipment.wagon` | 400, 35 gp | [SRD p. 72, Tack, Harness, and Drawn Vehicles](../../references/srd-5.1/pages/page-072.md) |

<a id="srd51-equipment-vehicle-proficiency"></a>
### Vehicle proficiency

`srd51.equipment.vehicle-proficiency`

Add proficiency bonus to a check to control a vehicle in difficult conditions if proficient with that kind of vehicle, land or water. [SRD p. 70, Tools, Vehicles](../../references/srd-5.1/pages/page-070.md) · [SRD p. 71, Vehicle Proficiency](../../references/srd-5.1/pages/page-071.md)

<a id="srd51-equipment-rowed-vessels"></a>
### Rowed vessels

`srd51.equipment.rowed-vessels`

Keelboats and rowboats are used on rivers and lakes. Add the current's speed when moving downstream, typically +3 mph. A significant current prevents rowing upstream, though draft animals on shore can pull a vessel upstream. A rowboat weighs 100 lb when carried overland. [SRD p. 71, Rowed Vessels](../../references/srd-5.1/pages/page-071.md)

| Vessel and ID | Speed (mph) and price | Source |
| --- | --- | --- |
| <a id="srd51-equipment-galley"></a> Galley · `srd51.equipment.galley` | 4 mph · 30,000 gp | [SRD p. 72, Waterborne Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-keelboat"></a> Keelboat · `srd51.equipment.keelboat` | 1 mph · 3,000 gp | [SRD p. 72, Waterborne Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-longship"></a> Longship · `srd51.equipment.longship` | 3 mph · 10,000 gp | [SRD p. 72, Waterborne Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-rowboat"></a> Rowboat · `srd51.equipment.rowboat` | 1½ mph · 50 gp | [SRD p. 72, Waterborne Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-sailing-ship"></a> Sailing ship · `srd51.equipment.sailing-ship` | 2 mph · 10,000 gp | [SRD p. 72, Waterborne Vehicles](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-warship"></a> Warship · `srd51.equipment.warship` | 2½ mph · 25,000 gp | [SRD p. 72, Waterborne Vehicles](../../references/srd-5.1/pages/page-072.md) |

<a id="srd51-equipment-trade-goods"></a>
## Trade goods

`srd51.equipment.trade-goods`

Merchants can exchange trade goods without coins. These goods keep their full market value under the [resale rule](equipment.md#srd51-equipment-selling-treasure). Guilds and governing authorities can control which merchants can offer goods or services, set prices for trades they control, and grant charter rights for routes, ports, or products. Other wealth can include land, taxes, or resource rights. [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) · [SRD p. 62, Selling Treasure, Trade Goods](../../references/srd-5.1/pages/page-062.md)

| Trade unit and ID | Value and source |
| --- | --- |
| <a id="srd51-equipment-trade-chicken"></a> Chicken, one · `srd51.equipment.trade-chicken` | 2 cp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-cow"></a> Cow, one · `srd51.equipment.trade-cow` | 10 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-goat"></a> Goat, one · `srd51.equipment.trade-goat` | 1 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-ox"></a> Ox, one · `srd51.equipment.trade-ox` | 15 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-pig"></a> Pig, one · `srd51.equipment.trade-pig` | 3 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-sheep"></a> Sheep, one · `srd51.equipment.trade-sheep` | 2 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-wheat"></a> Wheat, 1 lb · `srd51.equipment.trade-wheat` | 1 cp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-flour"></a> Flour, 1 lb · `srd51.equipment.trade-flour` | 2 cp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-salt"></a> Salt, 1 lb · `srd51.equipment.trade-salt` | 5 cp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-ginger"></a> Ginger, 1 lb · `srd51.equipment.trade-ginger` | 1 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-cinnamon"></a> Cinnamon, 1 lb · `srd51.equipment.trade-cinnamon` | 2 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-pepper"></a> Pepper, 1 lb · `srd51.equipment.trade-pepper` | 2 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-cloves"></a> Cloves, 1 lb · `srd51.equipment.trade-cloves` | 3 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-saffron"></a> Saffron, 1 lb · `srd51.equipment.trade-saffron` | 15 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-canvas"></a> Canvas, 1 sq yd · `srd51.equipment.trade-canvas` | 1 sp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-cotton-cloth"></a> Cotton cloth, 1 sq yd · `srd51.equipment.trade-cotton-cloth` | 5 sp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-linen"></a> Linen, 1 sq yd · `srd51.equipment.trade-linen` | 5 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-silk"></a> Silk, 1 sq yd · `srd51.equipment.trade-silk` | 10 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-iron"></a> Iron, 1 lb · `srd51.equipment.trade-iron` | 1 sp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-copper"></a> Copper, 1 lb · `srd51.equipment.trade-copper` | 5 sp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-silver"></a> Silver, 1 lb · `srd51.equipment.trade-silver` | 5 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-gold"></a> Gold, 1 lb · `srd51.equipment.trade-gold` | 50 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |
| <a id="srd51-equipment-trade-platinum"></a> Platinum, 1 lb · `srd51.equipment.trade-platinum` | 500 gp · [SRD p. 72, Trade Goods](../../references/srd-5.1/pages/page-072.md) |

<a id="srd51-equipment-lifestyle-expenses"></a>
## Lifestyle expenses

`srd51.equipment.lifestyle-expenses`

At the start of a week or month, choose a lifestyle and pay its daily price multiplied by the period's days. The choice can change each period. The price covers food, drink, shelter, other daily needs, and equipment upkeep. The GM can use lifestyle to determine opportunities, social contacts, risk of crime, and exposure to intrigue. A 30-day modest lifestyle costs 30 gp. [SRD pp. 72–73, Lifestyle Expenses](../../references/srd-5.1/pages/page-072.md) · [SRD p. 73, Lifestyle Expenses continuation](../../references/srd-5.1/pages/page-073.md)

| Lifestyle and ID | Daily price and circumstances | Source |
| --- | --- | --- |
| <a id="srd51-equipment-lifestyle-wretched"></a> Wretched · `srd51.equipment.lifestyle-wretched` | No listed price. Unsheltered or dependent on charity, with severe insecurity and little social standing. | [SRD p. 72, Lifestyle Expenses](../../references/srd-5.1/pages/page-072.md) · [SRD p. 73, Wretched](../../references/srd-5.1/pages/page-073.md) |
| <a id="srd51-equipment-lifestyle-squalid"></a> Squalid · `srd51.equipment.lifestyle-squalid` | 1 sp. Basic shelter under harsh conditions, little social standing or legal protection. | [SRD p. 72, Lifestyle Expenses](../../references/srd-5.1/pages/page-072.md) · [SRD p. 73, Squalid](../../references/srd-5.1/pages/page-073.md) |
| <a id="srd51-equipment-lifestyle-poor"></a> Poor · `srd51.equipment.lifestyle-poor` | 2 sp. Simple food and lodging, some legal protection and persistent exposure to crime and disease. | [SRD p. 73, Lifestyle Expenses and Poor](../../references/srd-5.1/pages/page-073.md) |
| <a id="srd51-equipment-lifestyle-modest"></a> Modest · `srd51.equipment.lifestyle-modest` | 1 gp. Adequate food and water, clean simple housing, and routine equipment upkeep. | [SRD p. 73, Lifestyle Expenses and Modest](../../references/srd-5.1/pages/page-073.md) |
| <a id="srd51-equipment-lifestyle-comfortable"></a> Comfortable · `srd51.equipment.lifestyle-comfortable` | 2 gp. Better clothing and lodging, easy upkeep, and contact with skilled and merchant circles. | [SRD p. 73, Lifestyle Expenses and Comfortable](../../references/srd-5.1/pages/page-073.md) |
| <a id="srd51-equipment-lifestyle-wealthy"></a> Wealthy · `srd51.equipment.lifestyle-wealthy` | 4 gp. Luxurious lodging, possible servants, and access to wealthy circles without a claim to nobility. | [SRD p. 73, Lifestyle Expenses and Wealthy](../../references/srd-5.1/pages/page-073.md) |
| <a id="srd51-equipment-lifestyle-aristocratic"></a> Aristocratic · `srd51.equipment.lifestyle-aristocratic` | At least 10 gp. Excellent lodging, food, clothing, servants, and connections to powerful people, with an elevated risk of political intrigue. | [SRD p. 73, Lifestyle Expenses and Aristocratic](../../references/srd-5.1/pages/page-073.md) |

<a id="srd51-equipment-self-sufficiency"></a>
### Self-sufficiency

`srd51.equipment.self-sufficiency`

A character can maintain equipment and supply their own food and shelter in the wild without coin, at the cost of between-adventure time. Spending that time practicing a profession supports the equivalent of a poor lifestyle. A character proficient in Survival can support a comfortable equivalent. The separate [downtime profession rule](exploration.md#srd51-core-practicing-a-profession) applies to working to support a lifestyle. [SRD p. 73, Self-Sufficiency](../../references/srd-5.1/pages/page-073.md) · [SRD p. 88, Practicing a Profession](../../references/srd-5.1/pages/page-088.md)

<a id="srd51-equipment-food-drink-and-lodging"></a>
## Food, drink, and lodging

`srd51.equipment.food-drink-and-lodging`

The following amounts apply when purchasing individually. Food and lodging are already included when paying lifestyle expenses. [SRD p. 73, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-073.md)

| Lifestyle | Meals for a day | One night at an inn | Source |
| --- | --- | --- | --- |
| Squalid | 3 cp | 7 cp | [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| Poor | 6 cp | 1 sp | [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| Modest | 3 sp | 5 sp | [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| Comfortable | 5 sp | 8 sp | [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| Wealthy | 8 sp | 2 gp | [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| Aristocratic | 2 gp | 4 gp | [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |

| Purchase and ID | Price and source |
| --- | --- |
| <a id="srd51-equipment-ale-gallon"></a> Ale, one gallon · `srd51.equipment.ale-gallon` | 2 sp · [SRD p. 73, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-073.md) |
| <a id="srd51-equipment-ale-mug"></a> Ale, one mug · `srd51.equipment.ale-mug` | 4 cp · [SRD p. 73, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-073.md) |
| <a id="srd51-equipment-banquet"></a> Banquet, per person · `srd51.equipment.banquet` | 10 gp · [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-bread"></a> Bread, one loaf · `srd51.equipment.bread` | 2 cp · [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-cheese"></a> Cheese, a hunk · `srd51.equipment.cheese` | 1 sp · [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-meat"></a> Meat, a chunk · `srd51.equipment.meat` | 3 sp · [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-common-wine"></a> Wine, one pitcher of common · `srd51.equipment.common-wine` | 2 sp · [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-fine-wine"></a> Wine, one bottle of fine · `srd51.equipment.fine-wine` | 10 gp · [SRD p. 74, Food, Drink, and Lodging](../../references/srd-5.1/pages/page-074.md) |

<a id="srd51-equipment-services"></a>
## Services

`srd51.equipment.services`

Hirelings can perform tasks or act on the characters' behalf. Skilled work involves a weapon, skill, tool, or other proficiency. The stated skilled rate is a minimum and specialists can demand more. Untrained hirelings handle tasks without special skill. A stronghold's long-term arrangements can include lodging as part of pay. [SRD p. 74, Services](../../references/srd-5.1/pages/page-074.md)

| Service and ID | Rate and source |
| --- | --- |
| <a id="srd51-equipment-coach-cab"></a> Coach cab · `srd51.equipment.coach-cab` | 3 cp per mile between towns, or 1 cp within a city · [SRD p. 74, Services](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-skilled-hireling"></a> Skilled hireling · `srd51.equipment.skilled-hireling` | At least 2 gp per day · [SRD p. 74, Services](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-untrained-hireling"></a> Untrained hireling · `srd51.equipment.untrained-hireling` | 2 sp per day · [SRD p. 74, Services](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-messenger"></a> Messenger · `srd51.equipment.messenger` | 2 cp per mile · [SRD p. 74, Services](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-road-or-gate-toll"></a> Road or gate toll · `srd51.equipment.road-or-gate-toll` | 1 cp · [SRD p. 74, Services](../../references/srd-5.1/pages/page-074.md) |
| <a id="srd51-equipment-ships-passage"></a> Ship's passage · `srd51.equipment.ships-passage` | 1 sp per mile · [SRD p. 74, Services](../../references/srd-5.1/pages/page-074.md) |

<a id="srd51-equipment-spellcasting-services"></a>
### Spellcasting services

`srd51.equipment.spellcasting-services`

There is no fixed fee schedule for hiring a caster. Higher-level casting is usually harder to find and more expensive. A relatively common 1st- or 2nd-level spell can generally be arranged in a city or town for 10–50 gp plus expensive components. Higher-level casting can require a large city or a specialized institution, and a willing caster may demand a favor such as a dangerous retrieval or delivery in place of payment. [SRD p. 74, Spellcasting Services](../../references/srd-5.1/pages/page-074.md)

**Acceptance cases.** Crossing an area of ball bearings at half speed skips its save, but crossing it at full speed requires the DC 10 Dexterity save. A successful DC 13 Strength check on the hunting trap frees a reachable captive. A three-mile messenger errand costs 6 cp at the listed rate. A mount large enough and otherwise eligible for riding underwater still requires an exotic saddle. [SRD p. 67, Ball Bearings and Hunting Trap](../../references/srd-5.1/pages/page-067.md) · [SRD p. 71, Saddles](../../references/srd-5.1/pages/page-071.md) · [SRD p. 74, Services](../../references/srd-5.1/pages/page-074.md)

## Source coverage

| SRD page | Sections and destinations |
| --- | --- |
| [66](../../references/srd-5.1/pages/page-066.md) | Adventuring Gear introduction, Acid, Alchemist's Fire, Antitoxin, Arcane Focus opening → [adventuring gear](#srd51-equipment-adventuring-gear) and named entries. Weapons table → [equipment](equipment.md#weapon-profiles). |
| [67](../../references/srd-5.1/pages/page-067.md) | Arcane Focus continuation and Ball Bearings through Hunting Trap, including Druidic Focus and Holy Symbol → [adventuring gear](#srd51-equipment-adventuring-gear) and named entries. |
| [68](../../references/srd-5.1/pages/page-068.md) | Lamp through Torch, including use of oil, poison, potions, restraints, lighting, and capacities → [adventuring gear](#srd51-equipment-adventuring-gear) and named entries. |
| [69](../../references/srd-5.1/pages/page-069.md) | Both columns of item prices, weights, and quantities → [adventuring gear](#srd51-equipment-adventuring-gear) rows. First six container capacities → [Containers](#containers). |
| [70](../../references/srd-5.1/pages/page-070.md) | Remaining capacities and backpack footnote → [Containers](#containers). All seven pack contents and prices → [packs](#srd51-equipment-equipment-packs). Tool mechanics, artisan rules and table → [tools](#srd51-equipment-tools) and [tool profiles](#tool-profiles). |
| [71](../../references/srd-5.1/pages/page-071.md) | Disguise, forgery, gaming, herbalism, instruments, navigation, poisoner, and thieves tool rules → [tools](#srd51-equipment-tools) and named entries. Vehicle, barding, saddle, rowed vessel rules and initial animal rows → [mounts and vehicles](#srd51-equipment-mounts-and-vehicles) and named entries. |
| [72](../../references/srd-5.1/pages/page-072.md) | Remaining animal rows, all tack, land and water vehicle prices and statistics → [mounts and vehicles](#srd51-equipment-mounts-and-vehicles) and named entries. Trade Goods → [trade goods](#srd51-equipment-trade-goods). Expenses and Lifestyle Expenses opening → [lifestyles](#srd51-equipment-lifestyle-expenses). |
| [73](../../references/srd-5.1/pages/page-073.md) | Lifestyle costs and social circumstances, Self-Sufficiency, Food Drink and Lodging opening and ale prices → [lifestyles](#srd51-equipment-lifestyle-expenses), [self-sufficiency](#srd51-equipment-self-sufficiency), [food, drink, and lodging](#srd51-equipment-food-drink-and-lodging). |
| [74](../../references/srd-5.1/pages/page-074.md) | Remaining food, drink, and lodging prices → [food, drink, and lodging](#srd51-equipment-food-drink-and-lodging). Service price table and rules → [services](#srd51-equipment-services), [spellcasting services](#srd51-equipment-spellcasting-services). |

Narrative examples of particular food, lifestyles, commissions, and favor requests add no independent costs or eligibility.

PDF layout checks covered the two gear columns and start of Container Capacity on p. 69, the container continuation, pack sidebar and tool table on p. 70, the animal table split across pp. 71–72, the tack, vessel and trade tables on p. 72, and the price tables split across pp. 72–74.
