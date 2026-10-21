# Independent schema 2 tool composition vectors

This finite, mixed language/tool scenario exercises `character.tool`, `catalog.tool_set`
and typed `members.tool` alongside the existing language types. The base contains all 37
real tool proficiency catalog entries. Their display name, description, six-value category
and source page are independently transcribed from the same handwritten field matrix as
`ToolCatalogOracle`; schema 2 does not carry the schema 1 partition's `sort_order` field.
The bounded schema 2 author model also permits new tools owned by extensions. These
fixtures grant no COMPLETE, accepted release or mechanism execution qualification.

The base defines a tool set requiring 37 distinct enabled tools and one separate language
set. The extension adds a second member referencing an existing tool, changes a description
and source page, clears a note, disables a tool and a member, and temporarily raises the
minimum to 38. The compatibility package restores the tool/member and the minimum of 37,
redirects the extension member to an existing vehicle tool, clears that member's note,
records an identical-value category operation, and adds a set note. Counting membership
rows instead of distinct enabled targets would incorrectly satisfy a minimum of 38.

`tools_base`, `tools_extension` and `tools_compatibility` contain the exact UTF-8 JSON
source bytes with final LF. Their `*.zip.hex` files contain fixed real ZIP32 DEFLATED
transports, independently written with Python `zipfile.ZipInfo(path, (2001, 2, 3, 4, 5, 6))`,
`create_system=3`, `external_attr=0o100644 << 16` and `ZIP_DEFLATED`. The physical first
entry is `NOTICE` with bytes `Finite schema-2 tools fixture. No qualification.\n`; the
remaining paths reverse the header and declared files. Decode lowercase hex after
stripping its final LF. Tests never regenerate these expected ZIPs with production code.

`expected/` contains independently calculated production logical projections, ordered
configuration, all final typed rows, and every source chain: 40 rules, 39 entries,
353 chains and 366 steps. Expected JSON uses Python `json.dumps(ensure_ascii=False,
sort_keys=True, separators=(',', ':'))` with no ending LF. Digests use independent Python
`hashlib.sha256`, prefix `b'local-dnd/schema2-production/1/'`, ASCII tags `module-logic`,
`composition-config`, `final-rules` or `full-chain`, a zero byte and the exact JSON payload.
The unprefixed raw digest binds each fixed ZIP. The expected bytes and digest data are
fixed resources, never generated through the implementation under test.

`Schema2ToolCompositionTest` compares every original tool field, every fixed production
byte and digest, and the stored restore path. Mutation cases reject mixed domains,
unknown/generic fields, malformed text and integer values, undeclared or indirect
references, insufficient distinct enabled candidates, noncanonical stored fields,
incomplete chains and references predating their declaration. Existing schema 2
language vectors remain separate and retain their exact byte/digest contracts.
