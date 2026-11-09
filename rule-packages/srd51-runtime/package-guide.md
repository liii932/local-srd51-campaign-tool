# Finite current-application rules

These schema-2 author sources supply the current SQLite campaign application.
They are a deliberately limited selection of mechanisms, not the complete SRD,
a `RELEASED` complete-rule package, source-database acceptance evidence or an
installer. Loading them verifies their original ZIPs through
`ProductionRuleArtifact.read`, then uses `ProductionSchema2Projection.compose`.
The campaign freezes the complete typed projection, identities and field chains.

The rule library imports and accepts exact package releases for new campaigns.
External ZIPs can supply new content for mechanisms the installed application
already implements without rebuilding the WAR. Improved Critical requires an
application release that implements its algorithm. A campaign retains the exact
ordered members and rules frozen when it was created.

## Modules and dependencies

| Module | Role | Exact direct dependencies | Content |
| --- | --- | --- | --- |
| `srd51_runtime` version `4` | base | none | Language/tool catalogs, bounded race/background sources, proficiencies, skill candidate sets, walking speed and rests |
| `srd51_characters` version `4` | extension | `srd51_runtime=4` | Fighter/cleric/wizard growth, resources, equipment, checks, health, ordinary combat, Fighter Extra Attack and Champion Improved Critical |
| `srd51_magic` version `4` | extension | `srd51_runtime=4`, `srd51_characters=4` | Selected spells, Life-domain/Wizard profiles, Arcane Recovery and selected monsters |
| `dm_runtime_house_rules` version `7` | extension | `srd51_runtime=4` | Explicit DM antimagic area and fixed direct vital effects |

The standard combination uses the order above. The check variant keeps the
first three members and selects `dm_runtime_house_rules` version `8` as its
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

Human sources grant Common and one standard language. Dwarf sources also
demonstrate a real artisan-tool choice. Acolyte grants two standard language
choices. The custom artisan source is an explicit project customization with
Dwarvish and smith's tools. Initial source facts currently include languages
and tools only: background skill and narrative-feature effects are not added.

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
traits, dwarf poison defenses or its heavy-armor speed exception, background
skill/feature effects, starting equipment packages, cantrips, general cleric
preparation, spell transcription, versatile/thrown weapon modes or full class
feature execution. Fighter Fighting Style, Second Wind and Action Surge are
catalog-only `OPTION` metadata. Fighter resources track uses and
recovery without executing those effects. Evocation Savant and Sculpt Spells
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

Numeric material is selected from local SRD pages 3/5 (race sources), 15–17
(cleric/Life), 24–25 (fighter), 52–54 (Wizard), 56–58 (advancement/multiclass/
Grappler), 59–61 (languages/backgrounds), 63–66/70 (equipment/tools), 77–85
(checks/ability mappings), 87 (rests), 90–99 (combat/health), the spell-specific
pages recorded in the author rows, 321–322 (Hell Hound), 333–334 (Mummy) and
398 (Commoner). The character-source page field is currently limited to 3–74;
skill declarations use page 56 for advancement proficiency and record the
78–85 ability-mapping reference in their author notes.
