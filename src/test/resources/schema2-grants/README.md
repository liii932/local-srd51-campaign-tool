# Independent schema 2 proficiency grant vectors

The three finite custom packages model initial proficiency metadata. Human, North human,
Sailor, Scholar, Elf and Retired are fixture sources; their page 10 metadata is not a
complete SRD race, subrace, background or class implementation. Language targets use
source page 59 and tool targets page 70. These materials grant no installation,
acceptance, COMPLETE or execution authority.

The base declares five original language/tool targets, Smith tools as a distinct usable
tool choice, two catalog sets, four real source identities and seven source slots.
The extension adds its own Common language (the same display name and rule key as the
base language, with a different complete identity), two real sources, extra members and
slots. Compatibility restores the subrace parent and atomic grant values, and disables
the extension's Retired source after its declaration package. Final state
retains a disabled source, disabled fixed slot, disabled language/tool targets, duplicate
catalog targets and duplicate fixed grant provenance.

The positive initial request selects base `race.human`, `subrace.north`,
`background.sailor` and `class.scholar`. It chooses base `language.elvish` for the Human
`extra_languages` slot and base `tool.smith_tools` for Sailor `trade_tools`. Human's
native Common and Sailor's shared Common remain two independent source facts; extension
Common remains a distinct target. North human supplies Flute. Scholar's disabled tool
slot and unavailable Dwarvish retain suppressed fixed facts.

Every source JSON file has a final LF. Fixed real ZIP32 DEFLATED files are independently
written with Python `zipfile.ZipInfo(path, (2001, 2, 3, 4, 5, 6))`, `create_system=3`,
`external_attr=0o100644 << 16` and `ZIP_DEFLATED`. Physical first entry `NOTICE` contains
`Finite schema-2 proficiency grants fixture. No qualification.\n`; remaining author
paths use reversed declaration order. Decode lowercase hex after stripping its final LF.

Expected JSON uses Python `json.dumps(ensure_ascii=False, sort_keys=True,
separators=(',', ':'))`, with no final LF. The oracle builds logical fields directly from
the handwritten author JSON, sorts complete identities, records declared fields and
default/absent states at original array locations, and applies the explicit operations
only to declarations from preceding packages.
It does not load Java or invoke the implementation under test. It fixes 15 final rules,
18 final entries, 135 complete field chains and 152 steps. Independent `hashlib.sha256`
uses prefix `b'local-dnd/schema2-production/1/'`, the ASCII tag `module-logic`,
`composition-config`, `final-rules` or `full-chain`, one zero byte, and canonical bytes.
Raw ZIP digests use the exact unprefixed transport bytes.

Run `python3 src/test/resources/schema2-grants/oracle.py` from the repository root to
independently reproduce and compare all ten generated artifacts. The script uses only
the Python standard library and never reads expected JSON to calculate an expectation.
For an intentional fixture change, `--write` replaces only this directory's ZIP hex
and expected JSON files; review their complete semantic changes before accepting them.

`Schema2GrantFixtures` exposes the fixed artifacts, source files and expected resources
to core, source-ledger, runtime-transfer and initial-plan tests. Existing language and
tool vector directories remain separate and keep their exact byte/digest contracts.
