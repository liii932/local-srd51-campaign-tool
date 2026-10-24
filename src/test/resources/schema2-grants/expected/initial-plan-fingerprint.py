"""Independent stdlib oracle for the fixed INTERNAL initial-plan transfer fingerprint.

Run with Python 3; reads only the independently fixed configuration/digest resources.
Sources, selected slots, all eight slot atoms, choices and grant facts below are hand-authored.
It imports no production classes and never invokes a planner or compositor.
"""
from pathlib import Path
import hashlib
import json
import struct

root = Path(__file__).parent
configuration = json.loads((root / "configuration.json").read_text())
digests = json.loads((root / "digests.json").read_text())
payload = bytearray(b"local-dnd/schema2-initial-plan-transfer/1\0")


def integer(value):
    payload.extend(struct.pack(">i", value))


def text(value):
    encoded = value.encode("utf-8")
    integer(len(encoded))
    payload.extend(encoded)


def rule(value):
    for part in value:
        text(part)


def slot(value):
    rule(value[0])
    for part in ("proficiency_grants", value[1], value[2]):
        text(part)


def identity(kind, key, module="grants_base"):
    return module, kind, key


race = identity("character.race", "race.human")
subrace = identity("character.subrace", "subrace.north")
background = identity("character.background", "background.sailor")
character_class = identity("character.class", "class.scholar")
common = identity("character.language", "language.common")
dwarvish = identity("character.language", "language.dwarvish")
elvish = identity("character.language", "language.elvish")
smith = identity("character.tool", "tool.smith_tools")
herbalism = identity("character.tool", "tool.herbalism")
flute = identity("character.tool", "tool.flute")
foreign_common = identity("character.language", "language.common", "grants_extension")

# In the fixed canonical slot order: owner identity, slot owner/key, category,
# kind, full atom target, enabled, optional count, factual target, suppressed.
slots = [
    ((background, "grants_base", "shared_language"), "LANGUAGE", "FIXED", common, True, None, common, False),
    ((background, "grants_base", "trade_tools"), "TOOL", "CHOICE", identity("catalog.tool_set", "tool_set.initial"), True, 1, smith, False),
    ((character_class, "grants_base", "disabled_tool"), "TOOL", "FIXED", herbalism, False, None, herbalism, True),
    ((character_class, "grants_base", "lost_language"), "LANGUAGE", "FIXED", dwarvish, True, None, dwarvish, True),
    ((race, "grants_base", "extra_languages"), "LANGUAGE", "CHOICE", identity("catalog.language_set", "language_set.initial"), True, 1, elvish, False),
    ((race, "grants_base", "native_language"), "LANGUAGE", "FIXED", common, True, None, common, False),
    ((race, "grants_extension", "extension_language"), "LANGUAGE", "FIXED", foreign_common, True, None, foreign_common, False),
    ((subrace, "grants_base", "instrument"), "TOOL", "FIXED", flute, True, None, flute, False),
]

for value in ("01872103-0345-4abc-8def-0123456789ab", "CAPTURE",
              "00112233-4455-4677-8899-aabbccddeeff", "ffeeddcc-bbaa-4988-8766-554433221100",
              "schema2-production-json-1", "schema2-production-projection-1"):
    text(value)
integer(3)
for member in configuration["members"]:
    for value in (member["module_key"], member["release_version"], member["role"].upper(),
                  member["raw_zip_sha256"], member["logical_sha256"]):
        text(value)
for value in (digests["config"], digests["rules"], digests["chain"],
              "12345678-1234-4678-8234-123456789abc", "abcdef12-1234-4678-8234-123456789abc"):
    text(value)
integer(4)
for source in (race, subrace, background, character_class):
    rule(source)
integer(8)
for key, category, kind, target, enabled, count, _, _ in slots:
    slot(key)
    text(category)
    payload.extend(struct.pack("?", enabled))
    text(kind)
    rule(target)
    if kind == "CHOICE":
        integer(count)
integer(2)
for key, target in ((slots[1][0], smith), (slots[4][0], elvish)):
    slot(key)
    integer(1)
    rule(target)
integer(8)
for key, category, kind, _, _, _, target, suppressed in slots:
    slot(key)
    text(category)
    text(kind)
    rule(target)
    payload.extend(struct.pack("?", suppressed))
print(hashlib.sha256(payload).hexdigest())
