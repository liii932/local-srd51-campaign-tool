# SRD 5.1 semantic specification

This is an independently written English specification of the game behavior in
the repository's [local SRD 5.1](../../references/srd-5.1/README.md), the 2014
rules reference. It includes behavior outside the application's current support.
Official names and mechanical values identify the source rules; explanations and
organization are newly composed. Source attribution and licensing are maintained
in [NOTICE](../../../NOTICE).

**Editorial status: complete and source-reviewed.** The
[coverage ledger](coverage.md) records the disposition of all 403 source pages,
detailed drafting, main-agent source review, and validation. The specification
contains 319 spell entries, 240 magic-item entries, and 319 creature entries
(including two stat blocks embedded in items), alongside the shared rules,
characters, equipment, hazards, and cosmology. The
[source gaps](interpretations.md#missing-source-material) remain explicit.
Runtime implementation support is not assessed here.

## Reading guide

Begin with [conventions](conventions.md) for identifiers, evidence labels and
source citation policy. Read shared checks, exploration, combat, conditions and
spellcasting before individual content. A content entry supplies its own
parameters and exceptions and links to the shared behavior that completes it.

The organization follows semantic dependencies: shared determinations first,
character acquisition and abilities next, then equipment, spells, hazards, magic
items and creatures. Large catalogues use alphabetical bands; coupled class
features and creature families remain together. This avoids both a second copy of
the source's page layout and hundreds of isolated files.

Definitions have stable `srd51.*` identifiers independent of their file locations.
They express game semantics, not Java types, module keys or persisted identifiers.
Precise citations link directly to local pages and identify the relevant section
or entry. Source-defined DM and player choices remain choices.

## Detailed navigation

| Family | Definitions and catalogues |
|---|---|
| Shared determinations | [Abilities and checks](abilities.md), [exploration and rest](exploration.md), [combat](combat.md), [conditions](conditions.md), [spellcasting](spellcasting.md) |
| Character origins and growth | [Ancestries and racial traits](ancestries.md), [backgrounds, personality and inspiration](backgrounds-and-personality.md), [advancement and multiclassing](character-advancement.md), [feats](feats.md) |
| Classes | [Barbarian and Bard](classes-barbarian-bard.md), [Cleric and Druid](classes-cleric-druid.md), [Fighter and Monk](classes-fighter-monk.md), [Paladin and Ranger](classes-paladin-ranger.md), [Rogue and Sorcerer](classes-rogue-sorcerer.md), [Warlock and Wizard](classes-warlock-wizard.md), [class spell lists](spell-lists.md) |
| Mundane equipment | [Coins, armor and weapons](equipment.md), [adventuring gear, tools, transport and services](adventuring-gear.md) |
| Spells | [Complete spell inventory](spell-index.md), [A–C](spells-a-c.md), [D–E](spells-d-e.md), [Expeditious Retreat–Gust of Wind](spells-e-g.md), [H–M](spells-h-m.md), [N–S](spells-n-s.md), [T–Z](spells-t-z.md) |
| Hazards | [Traps](hazards-traps.md), [diseases, madness, objects and poisons](hazards-health.md) |
| Magic items | [Complete item inventory](magic-item-index.md), [shared item rules](magic-items.md), [Adamantine Armor–Cubic Gate](magic-items-a-c.md), [Dagger of Venom–Mirror of Life Trapping](magic-items-d-m.md), [Mithral Armor–Rope of Entanglement](magic-items-m-r.md), [Scarab of Protection–Wings of Flying](magic-items-s-w.md), [sentience and artifacts](magic-item-sentience-artifacts.md) |
| Creatures | [Complete creature inventory](creature-index.md), [stat-block and shared creature rules](creatures.md), [Aboleth–Doppelganger](creatures-a-d.md), [dragons](dragons.md), [Drider–Manticore](creatures-d-m.md), [Medusa–Ogre Zombie](creatures-m-z.md), [miscellaneous Ape–Goat](creatures-misc-a-g.md), [miscellaneous Hawk–Worg](creatures-misc-h-z.md), [NPCs](creatures-npcs.md) |
| Deities and planes | [Cosmology, suggested domains, planar travel and environment](cosmology.md) |

The inventory links identify every named catalogue destination. Each detailed
family also provides a source-to-definition map and source-checked examples.

## Authority and application support

The intended chain is:

`SRD source → semantic specification → mechanism contracts and module declarations → runtime behavior`

This directory establishes the second step. The
[complete product goal](../../rules/srd-5.1-complete.md),
[implementation coverage](../../rules/srd-5.1-coverage.md), and current
[adoption boundaries](../../design/v2/adoption-boundaries.md) have separate jobs.
A definition here neither changes a current contract nor grants executable
support. Project interpretations are labeled explicitly; deliberate application
variants must remain visible in their application contracts.

## Review records

- [Source coverage and review](coverage.md): every source page, detailed
  destinations, exclusions, and independent drafting/review status.
- [Interpretations and evidence gaps](interpretations.md): chosen operational
  readings and source limits; these are distinct from DM discretion.

Only documentation is produced by this specification. Runtime behavior, rule
package formats, database state, deployment and releases require their own work
and verification.
