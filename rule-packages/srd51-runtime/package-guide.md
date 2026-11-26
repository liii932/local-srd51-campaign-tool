# Finite current-application rules

These schema-2 author sources supply the current SQLite campaign application.
They are a deliberately limited selection of mechanisms, not the complete SRD,
a `RELEASED` complete-rule package, source-database acceptance evidence or an
installer. Loading them verifies their original ZIPs through
`ProductionRuleArtifact.read`, then uses `ProductionSchema2Projection.compose`.
The campaign freezes the complete typed projection, identities and field chains.

The rule library imports and accepts exact package releases for new campaigns.
External ZIPs can supply new content for mechanisms the installed application
already implements without rebuilding the WAR. Using a mechanism for the first
time requires an application release that implements it. An active campaign keeps the exact ordered members and
rules frozen at creation. Opening a saved campaign requires the original ZIPs
for those exact members, explicit acceptance in the local rule library, and an
application that supports the saved mechanisms.

## Modules and dependencies

| Module | Role | Exact direct dependencies | Content |
| --- | --- | --- | --- |
| `srd51_runtime` version `11` | base | none | Language/tool catalogs, bounded race/background sources, proficiencies, skill candidate sets, base walking speed, racial heavy-armor speed policy and rests |
| `srd51_characters` version `11` | extension | `srd51_runtime=11` | Dwarf Stonecunning, Hill Dwarf Dwarven Toughness, Fighter/cleric/wizard growth, resources, equipment, shared heavy-armor movement penalty, rolled and passive checks, health, ordinary combat, Fighter Second Wind and Extra Attack, and Champion Improved Critical |
| `srd51_magic` version `11` | extension | `srd51_runtime=11`, `srd51_characters=11` | Selected spells, Life-domain/Wizard profiles, Arcane Recovery and selected monsters |
| `dm_runtime_house_rules` version `21` | extension | `srd51_runtime=11` | Explicit DM antimagic area and fixed direct vital effects |

The standard combination uses the order above. The check variant keeps the
first three members and selects `dm_runtime_house_rules` version `22` as its
fourth member. Each reference retains its complete module and rule identity.
Resolve references in the campaign's frozen `CampaignRuleContext`.

## Executable scope

The application initializes an exact permutation of the standard array
`8,10,12,13,14,15`. Fighter growth is bounded to class level 6, Life-domain
cleric to class level 1 and Wizard to class level 5. Hit dice are d10/d8/d6;
starting class skill/save/armor/weapon training, later HP, ASI, subclass
acquisition, finite resource capacities and multiclass prerequisites use the
real typed consumers. Fighter ASI levels are 4 and 6; Wizard ASI is level 4.
Multiclass acquisition uses its own restricted proficiency declarations.

Fighters gain Second Wind at level 1. The SQLite application supports this
bonus action for a single-class fighter at levels 1–6 on that fighter's own
active combat turn. It spends one use to heal the fighter by a server-rolled
`1d10 + fighter level`, capped at maximum hit points. It spends the use even
when the fighter is already at maximum hit points. A completed short or long
rest restores the one use outside combat. This is the only executable bonus
action in this selection.
The combat action requires the exact acquired feature and available resource.
Generic resource spending cannot substitute for Second Wind. The MySQL INTERNAL
combat entry point rejects this action.

Normal fighter advancement to class level 5 grants the frozen Extra Attack
feature. A single-class fighter at level 5 or 6 with that grant can make up to
two supported melee attacks in one Attack action on the fighter's own turn.

A single-class fighter gains Improved Critical by choosing Champion at class
level 3. Through level 6, the feature applies to supported melee weapon attacks,
including opportunity attacks and attacks released from Ready. A selected d20
of 19 or 20 scores a critical hit. Under this project's interpretation, a
selected 19 automatically hits even if the total falls short of the target's
Armor Class, including any Shield bonus. Critical hits roll twice the weapon
damage dice and add the fixed damage modifier once.

Human is the default race source and grants Common and one standard language.
The bundled dwarf source grants Common, Dwarvish and one artisan's tool
proficiency. Acolyte grants two standard languages. The custom artisan source is
an explicit project customization with Dwarvish and smith's tools. Background
skill and narrative-feature effects remain outside this scope.

The standard check uses the skill's declared ability. To enable the optional
rule on SRD p. 78, a package can set the check's Boolean field
`allow_alternate_skill_ability` to true. The DM may then choose, for example,
Constitution (Athletics) for a long swim. The chosen ability supplies the
modifier and the selected skill determines ordinary proficiency. The bundled
check variant enables this option and defaults Athletics to Wisdom.

For passive ability, skill and equipped-tool checks (SRD p. 78), the check
definition supplies two required integer fields. The standard
`passive_base_score` is 10 (allowed range 1–20) and
`passive_advantage_adjustment` is 5 (allowed range 0–10).
Add the usual modifiers to the base. Add the adjustment for advantage or
subtract it for disadvantage, and use zero when both apply. A passive check
uses no d20. The bundled check variant uses base 12 and adjustment 3.

Dwarf and Hill Dwarf can apply Stonecunning when the DM identifies a supported
Intelligence (History) check about the origin of stonework. The standard
`character.stonecunning` declaration uses `SRD_2014_DWARF_STONECUNNING_V1`,
requires the exact Dwarf and Intelligence (History) definitions, and uses
`proficiency_multiplier=2`. The declared multiplier replaces any ordinary
proficiency contribution for that check, including when the character already
has History proficiency. Authors can set the multiplier from 1 through 3.
The check variant sets it to 3.

The Hill Dwarf source inherits Dwarf languages, tool training and walking speed.
Its `character.subrace_hit_points` rule uses `SRD_2014_SUBRACE_HIT_POINTS_V1`
with a separate `first_level_bonus` and `per_additional_level_bonus`. The
bundled values are each 1, for a Hill Dwarf's initial HP and every subsequent
supported level, whether level-up HP uses a fixed average or a server roll.
Each field accepts integers from 0 through 5. Additional HP uses the selected
subrace from the frozen creation plan. The supported ability array is unchanged.

Each `character.movement` rule defines its owner's `walk_speed_feet` and
`heavy_armor_speed_policy` using `SRD_2014_WALKING_SPEED_V1`. The bundled human
profile uses 30 feet and `APPLY`. The bundled dwarf profile uses 25 feet and
`IGNORE`. If policy is `APPLY` and worn heavy armor requires more Strength than
the character has, the shared `armor_movement.heavy_armor` rule deducts its
`heavy_armor_speed_penalty_feet` (10 in this combination), to a minimum of zero.
Wearable armor declares its own `strength_minimum`. Armor Class, Stealth
disadvantage and penalties for wearing armor without proficiency apply
independently.

Armor profiles use leather 11, scale mail 14, chain mail 16 with Strength 13,
plate 18 with Strength 15, and shield +2. Scale mail, chain mail and plate
impose Stealth disadvantage. Melee profiles are rapier 1d8 piercing/finesse,
dagger 1d4 piercing/finesse, mace 1d6 bludgeoning, longsword one-hand 1d8
slashing and greatsword two-hand 2d6 slashing, all with 5-foot reach.

Bless, Cure Wounds, Shield, Counterspell and Burning Hands use their registered
bounded runtime algorithms. Life-domain casting covers its two always-prepared
first-level spells and Disciple of Life. Wizard spellbook initialization,
learning, preparation, ranks 1–3 and actual short-rest Arcane Recovery use their
real consumers. Other cataloged Wizard spells are **metadata only**, usable for
book learning/preparation but rejected by casting consumers.

Hell Hound Fire Breath uses a 15-foot cone, DC 12 Dexterity save and 6d6 fire
damage, half on success; recharge is d6 on 5–6 at turn start and resets on
rests. Mummy defenses and the Common-speaking commoner are selected exact
stat blocks. All other monster actions are explicitly blocked metadata.

The DM extension's fixed direct damage/healing/temporary-HP profiles are
project-authored examples, not SRD spells or items. Its antimagic area is an
explicit DM house rule, separate from the SRD Antimagic Field spell.

## Unsupported boundaries

This selection does not supply racial ability increases, complete racial
traits, dwarf poison defenses, background
skill/feature effects, starting equipment packages, cantrips, general cleric
preparation, spell transcription, versatile/thrown weapon modes or full class
feature execution. Fighter Fighting Style and Action Surge are catalog-only
`OPTION` metadata. The Action Surge resource tracks uses and recovery without
granting an additional action. Evocation Savant and Sculpt Spells
are also catalog-only `OPTION` metadata and are not acquired, while the
Evocation subclass identity is retained. Grappler is blocked acquisition
metadata; its unresolved influence remains subject to the consumer rejection
boundaries. These profiles do not claim complete standard fighter or Wizard
builds. No consumer's rejection of unknown acquired influences is weakened.

Growth acquisition does not authorize casting with multiple spellcasting
classes. The existing bounded casting consumers reject unsupported multiclass
casting; no combined caster-level slot table is invented here. Unsupported
mechanisms must continue to fail closed in their owning consumer.

## Sources and reproducible resources

The author JSON files are the source of truth. `build-bundles.py` packs only
their declared members plus this guide and the attribution notice into STORED
ZIP32 resources under `src/main/resources/storage/rules/`. Sorted member names,
fixed DOS timestamps, fixed regular-file mode, no comments and no extra fields
make the resource bytes deterministic. Run the script with Python 3; `--check`
compares the expected archive bytes with the checked-in resources without writes.

Numeric material is selected from local SRD pages 3–5 (race sources and Dwarven Toughness), 15–17
(cleric/Life), 24–25 (fighter), 52–54 (Wizard), 56–58 (advancement/multiclass/
Grappler), 59–61 (languages/backgrounds), 63–66/70 (equipment/tools), 77–85
(checks/ability mappings), 87 (rests), 90–99 (combat/health), the spell-specific
pages recorded in the author rows, 321–322 (Hell Hound), 333–334 (Mummy) and
398 (Commoner). The character-source page field is currently limited to 3–74;
skill declarations use page 56 for advancement proficiency and record the
78–85 ability-mapping reference in their author notes.
