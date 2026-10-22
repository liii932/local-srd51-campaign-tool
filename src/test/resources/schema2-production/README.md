# Independent schema-2 production encoding vectors

These vectors reuse the exact author file bytes and finite identities from `schema2-composition/`.
They do not alter the validation vectors or claim completeness, acceptance, or execution support.

Each `*.zip.hex` is a fixed real ZIP32 DEFLATED transport created independently with Python's
`zipfile`, using `ZipInfo(path, (2001, 2, 3, 4, 5, 6))`, `create_system=3`,
`external_attr=0o100644 << 16`, and `compress_type=ZIP_DEFLATED`. All other ZipInfo attributes
are their standard empty/zero values. The first physical entry is `NOTICE` with exact UTF-8 bytes
`Finite schema-2 transport fixture. No qualification.\n`; subsequent entries are the reverse of
`author-package.json` followed by the manifest's declared file list. Bytes are encoded as lowercase
hex followed by LF. The bytes are fixed; tests never regenerate expected archives using production code.

`expected/*-logic.json` retains the independent typed author projections from the validation
fixtures. `configuration.json` uses profile `schema2-production-json-1`, algorithm
`schema2-production-projection-1`, real transport hashes and production logical hashes. `final-rules.json`
retains the independent expected final typed fields. `chains.json` retains every independent expected
chain step, substitutes the corresponding real transport hash into each author location, and binds
the production configuration digest.

All expected projection files use UTF-8 JSON with Python `json.dumps(ensure_ascii=False,
sort_keys=True, separators=(',', ':'))`, with no ending LF. `digests.json` was calculated independently
with Python `hashlib.sha256`, prefix `b'local-dnd/schema2-production/1/'`, ASCII tags `module-logic`,
`composition-config`, `final-rules`, `full-chain`, one zero byte, then the corresponding exact JSON.
Raw ZIP hashes have no prefix. Expected bytes and digests are checked in as data; tests do not invoke
the production implementation to generate their expectations.
