# Schema 2 composition fixtures

The nine author files preserve the exact UTF-8 bytes and final LF from section 3
of `docs/design/v2/schema-2-composition-validation.md`. The fixed final projection
comes from section 5. The 31 field chains and 42 steps in `expected/chains.json`
are a complete independent transcription of section 6, including initial absence,
default enabled states, identical-value operations, providers and author locations.

The three `*.zip.hex` resources preserve the exact original ZIP bytes without
adding ignored binary ZIP files. Decode the whitespace-free hexadecimal with
`HexFormat`. Each ZIP was independently built with Python's standard `zipfile`
writer and compared byte for byte with an explicit ZIP32 little-endian header and
directory implementation using the exact section 7 recipe. The Java tests rebuild
that recipe independently and compare both the fixed bytes and all nine section 7
digest constants. Logic/configuration JSON and digests are fixed reviewable
oracles. Tests never read project design documents or ignored workspaces at runtime
and never regenerate expected values through the production implementation.

Mutation tests rebuild author evidence through the public kernel while retaining
the fixed final/logic expectations appropriate to each mutation. Parser token
limits are exercised directly because the author byte cap can reject a token
bomb first. Derived chains have their own verification boundary: a valid result
with more than 256 chains must verify despite the separate author-array limit.

These materials prove only the finite projection contract. Production
qualification must always reject this incomplete base with `S2_BASE_INCOMPLETE`.
