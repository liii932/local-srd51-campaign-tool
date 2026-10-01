# SRD 5.1 character catalog author source

This directory contains the 18-entry `character.language` and 37-entry `character.tool` catalogs for the DRAFT
`dnd5e2014_srd51_se` / `"1"` release. Its slug does not assert complete SRD coverage.

The three authoritative author inputs are `author-package.json`,
`character/languages.json` and `character/tools.json`. Their strict grammar, budgets, baseline and pure projection
are specified in the repository's `docs/rules/language-author-package.md`
([link when read inside the repository](../../docs/rules/language-author-package.md)).
The short descriptions and page 59 are catalog metadata inherited from the existing
catalog, not complete language rules, character choices, scripts or secret-language mechanics.
Tools retain the existing page-70 catalog metadata; they are proficiency references, not
equipment price, weight, crafting or usage rules. See the
[tool catalog contract](../../docs/rules/tool-catalog-partition.md) for the exact field closure.

These sources are kept outside the WAR. Validation proves only PARTITION scope;
it neither installs rules nor approves the DRAFT for execution. The repository author-source
directory has no installation manifest. A generated artifact additionally contains
`installation-manifest.json`; its builder independently inventories all author, documentation
and license files while preserving their exact bytes. The offline procedure is documented
in the repository's `docs/rules/offline-language-installation.md`.

See [notice.md](notice.md) for source attribution and licensing.
