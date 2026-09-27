# SRD 5.1 language author source

This directory contains only the 18-entry `character.language` catalog for the DRAFT
`dnd5e2014_srd51_se` / `"1"` release. Its slug does not assert complete SRD coverage.

The two authoritative author inputs are `author-package.json` and
`character/languages.json`. Their strict grammar, budgets, baseline and pure projection
are specified in the repository's `docs/rules/language-author-package.md`
([link when read inside the repository](../../docs/rules/language-author-package.md)).
The short descriptions and page 59 are catalog metadata inherited from the existing
catalog, not complete language rules, character choices, scripts or secret-language mechanics.

These sources are kept outside the WAR. Validation proves only PARTITION scope;
it neither installs rules nor approves the DRAFT for execution. The repository author-source
directory has no installation manifest. A generated artifact additionally contains
`installation-manifest.json`; its builder independently inventories all author, documentation
and license files while preserving their exact bytes. The offline procedure is documented
in the repository's `docs/rules/offline-language-installation.md`.

See [notice.md](notice.md) for source attribution and licensing.
