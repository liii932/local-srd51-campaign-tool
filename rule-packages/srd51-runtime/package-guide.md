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
| `srd51_runtime` version `14` | base | none | Language/tool catalogs, bounded race/background sources, proficiencies, skill candidate sets, base walking speed, racial heavy-armor speed policy and rests |
| `srd51_characters` version `16` | extension | `srd51_runtime=14` | Dwarf Stonecunning, Hill Dwarf Dwarven Toughness, Fighter, Rogue, Cleric and Wizard growth, resources, equipment, shared heavy-armor movement penalty, rolled, passive, group and opposed checks, Rogue Expertise and Cunning Action, health, melee and ranged weapon attacks, Fighter Defense, Second Wind, Action Surge and Extra Attack, and Champion Improved Critical |
| `srd51_magic` version `20` | extension | `srd51_runtime=14`, `srd51_characters=16` | Selected spells, Cleric and Wizard cantrip choices, Life-domain/Wizard profiles, Arcane Recovery and selected monsters, including Goblin and Skeleton |
| `dm_runtime_house_rules` version `29` | extension | `srd51_runtime=14` | Explicit DM antimagic area and fixed direct vital effects |

The standard combination uses the order above. The check variant keeps the
first three members and selects `dm_runtime_house_rules` version `30` as its
fourth member. Each reference retains its complete module and rule identity.
Resolve references in the campaign's frozen `CampaignRuleContext`.

## Executable scope

The application initializes an exact permutation of the standard array
`8,10,12,13,14,15`. Fighter growth is bounded to class level 6, Life-domain
cleric to class level 1, Wizard to class level 5 and Rogue to class level 2. Hit dice are d10/d8/d6;
starting class skill/save/armor/weapon training, later HP, ASI, subclass
acquisition, finite resource capacities and multiclass prerequisites use the
real typed consumers. Fighter ASI levels are 4 and 6; Wizard ASI is level 4.
Multiclass acquisition uses its own restricted proficiency declarations.

Fighters select Defense when creating a level 1 fighter in the supported
profile. The frozen choice grants +1 Armor Class while wearing light, medium
or heavy armor. A shield contributes its normal bonus independently, and a
shield alone leaves Defense inactive.

Fighters gain Second Wind at level 1. The SQLite application supports this
bonus action for a single-class fighter at levels 1–6 on that fighter's own
active combat turn. It spends one use to heal the fighter by a server-rolled
`1d10 + fighter level`, capped at maximum hit points. It spends the use even
when the fighter is already at maximum hit points. A completed short or long
rest restores the one use outside combat.
The combat action requires the exact acquired feature and available resource.
Generic resource spending cannot substitute for Second Wind. The MySQL INTERNAL
combat entry point rejects this action.

Normal fighter advancement to class level 2 grants Action Surge. On the
fighter's own active combat turn, it spends its own once-per-rest resource to
grant one additional ordinary action for that turn. It leaves the bonus
action, reaction and movement budgets intact. A subsequent Dash adds the
ordinary speed-based movement allowance. A short or long rest restores the
use.

Normal fighter advancement to class level 5 grants the frozen Extra Attack
feature. A single-class fighter at level 5 or 6 with that grant can make up to
two supported weapon attacks in one Attack action on the fighter's own turn.
An additional Attack action starts a separate sequence with its own two attacks.

A single-class fighter gains Improved Critical by choosing Champion at class
level 3. Through level 6, the feature applies to supported weapon attacks,
including melee opportunity attacks and melee attacks released from Ready. A selected d20
of 19 or 20 scores a critical hit. Under this project's interpretation, a
selected 19 automatically hits even if the total falls short of the target's
Armor Class, including any Shield bonus. Critical hits roll twice the weapon
damage dice and add the fixed damage modifier once.

A single-class Rogue starts with d8 Hit Dice and selects four skills from
the eleven frozen class candidates. The class supplies its starting light
armor and weapon proficiencies, proficiency with thieves’ tools, and Dexterity
and Intelligence saving throw proficiencies. The weapon data names the full
class proficiency selection, with individual bindings for the supported simple
weapons.

At level 1, Expertise requires two different proficient skills, or one
proficient skill and proficiency with thieves’ tools. A matching rolled or
passive check doubles the proficiency bonus. To check with thieves’ tools,
the character first prepares the matching item using the normal equipment
rules. At level 2, Cunning Action allows Dash, Disengage or Hide with the
Rogue’s own combat turn bonus action. The supported Rogue level range is
1–2. Sneak Attack, third level and multiclass Rogue are outside this profile.

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

A group check (SRD p. 79) resolves one individual check per participant.
The group succeeds when the number of successes multiplied by the denominator
is at least the participant count multiplied by the numerator. The required
`group_success_threshold` field is a complete object with integer
`numerator` and `denominator` values in lowest terms and requires
`1 <= numerator <= denominator <= 10`. Standard rules require at least half
of participants to succeed. The bundled check variant requires at least two
thirds. Authors override the whole threshold in one `set_field` operation.

An opposed check (SRD p. 77) compares the two participants' totals. The
`contest_minimum_winning_margin` check field requires an integer from 1 through 3.
A participant wins if their total exceeds the other total by at least that
margin. The prior situation continues when neither reaches the margin. The
standard requires a lead of 1. The bundled check variant requires 2.

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

A ranged `equipment.weapon_attack` declares `SRD_2014_RANGED_WEAPON_ATTACK_V1`,
`ability_policy` `DEXTERITY`, physical damage dice, `normal_range_feet`,
`long_range_feet` and an `ammunition` rule reference. Both ranges use positive
five-foot increments through 600 feet, and long range exceeds normal range.
Use `set_field` with `normal_range_feet` to override its threshold. The weapon
item has kind `RANGED_WEAPON`, two hands and a weapon proficiency. The referenced
ammunition item has kind `AMMUNITION`, zero hands and no proficiency. The shortbow
uses 1d6 piercing damage, range 80/320 feet and one arrow per attack roll.
Fighters gain its specific proficiency at first level or when multiclassing
into Fighter. Clerics gain it when Cleric is their starting class.

Bless, Cure Wounds, Shield, Counterspell and Burning Hands use their registered
bounded runtime algorithms. Life-domain casting covers its two always-prepared
first-level spells and Disciple of Life. Wizard spellbook initialization,
learning, preparation, ranks 1–3 and actual short-rest Arcane Recovery use their
real consumers. Other cataloged Wizard spells are **metadata only**, usable for
book learning/preparation but rejected by casting consumers.

The class cantrip sources use `character.class_cantrips` and a dedicated
`cantrips` entry collection. Clerics choose three of Guidance, Light, Sacred
Flame and Thaumaturgy at first level. Wizards choose three of Fire Bolt, Light,
Mage Hand and Prestidigitation at first level, and a fourth at class level 4.
Each entry references an exact level-zero spell definition. Fire Bolt and
Sacred Flame can be cast when known and use no spell slot. Other cantrip options
are catalog metadata. Cantrip choices stay independent of the Wizard spellbook.

The Fire Bolt and Sacred Flame definitions each declare a complete
`cantrip_profile`. The supported profiles fix range, die sides, damage type,
the character level 5 increase from one to two dice, and whether cover can add
to a saving throw. Fire Bolt is a ranged spell attack against a creature within
120 feet using a d10 and fire damage. Sacred Flame requires a visible creature
within 60 feet and deals d8 radiant damage on a failed Dexterity save. Cover
adds no bonus to that save. Both use one action and verbal and somatic components.

Hell Hound Bite uses a +5 melee attack at 5 feet and deals 1d8 + 3 piercing
plus 2d6 fire on a hit. Its typed attack profile also supplies the hound's
opportunity attack. Pack Tactics uses a non-incapacitated ally within 5 feet
of the target. Fire Breath uses a 15-foot cone, a DC 12 Dexterity save and
6d6 fire damage, halved on success. Recharge is d6 on 5–6 at turn start and
resets on rests. Mummy and Commoner actions remain catalog metadata.

Wolf has Pack Tactics and Keen Hearing and Smell. Its Bite uses a +4 melee
attack at 5 feet, deals 2d4 + 2 piercing damage on a hit and calls for a DC 11
Strength saving throw to avoid becoming prone. Its `attack_profile.on_hit` is
optional and accepts only `ability_key: "ability.strength"`, an integer `dc`
from 1 to 30 and `fail_outcome: "PRONE"`.

Skeleton has two weapon actions. Shortsword uses the melee profile with 5-foot
reach. Shortbow uses mode `RANGED_ATTACK_V1` and mechanism
`MONSTER_RANGED_ATTACK_V1`. Its `attack_profile` has a frozen `hit_bonus` of +4,
`normal_range_feet` 80, `long_range_feet` 320 and a `damage` row of 1d6 + 2
piercing. The Shortsword and Shortbow each use the same frozen hit bonus and
damage. To change a monster action, replace its entire `action_profile`.

Goblin is a Small humanoid with AC 15, 7 hit points, a speed of 30 feet, a +6
Stealth modifier and passive Perception 9. Scimitar is a +4 melee attack at 5
feet for 1d6 + 2 slashing damage. Shortbow is a +4 ranged attack at 80/320 feet
for 1d6 + 2 piercing damage. The `nimble_escape` trait on
`srd51_magic/adventure.monster/monster.goblin` uses
`NIMBLE_ESCAPE_BONUS_HIDE_DISENGAGE_V1`. It lets the Goblin spend its bonus
action to Hide or Disengage on its own turn. Trait text is for display. The
stock PC skills use `proficiency.skill.stealth` with Dexterity and
`proficiency.skill.perception` with Wisdom. A monster uses a listed skill
modifier as its entire bonus and its ability modifier for an unlisted skill.
Its passive Perception uses the value declared in its frozen stat block.

The `dm-runtime-monster-variant` and `dm-runtime-wolf-variant` author examples
replace the full Bite profile for Hell Hound or Wolf after the standard
combination. The Wolf example changes the saving throw DC to 12. Both have
independent author scenarios.

The DM extension's fixed direct damage/healing/temporary-HP profiles are
project-authored examples, not SRD spells or items. Its antimagic area is an
explicit DM house rule, separate from the SRD Antimagic Field spell.

## Unsupported boundaries

This selection does not supply racial ability increases, complete racial
traits, dwarf poison defenses, background
skill/feature effects, starting equipment packages, general cleric
preparation, spell transcription, versatile/thrown weapon modes or full class
feature execution. Defense is the supported Fighter Fighting Style option.
Evocation Savant and Sculpt Spells
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
(cleric/Life), 24–25 (Fighter), 39–40 (Rogue), 52–54 (Wizard), 56–58 (advancement/multiclass/
Grappler), 59–61 (languages/backgrounds), 63–69/70 (equipment/tools), 77–85
(checks/ability mappings), 87 (rests), 90–99 (combat/health), the spell-specific
pages recorded in the author rows, 315 (Goblin), 321–322 (Hell Hound), 333–334 (Mummy),
346 (Skeleton), 393 (Wolf) and 398 (Commoner). The character-source page field is currently limited to 3–74;
skill declarations use page 56 for advancement proficiency and record the
78–85 ability-mapping reference in their author notes.
