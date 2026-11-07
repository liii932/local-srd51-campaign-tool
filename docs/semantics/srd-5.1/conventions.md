# Semantic specification conventions

This specification explains the game behavior in the repository's local SRD 5.1.
It is a semantic foundation, not a declaration that the application executes any
particular rule. The dependency direction is source, semantic specification,
application mechanism contracts and module declarations, then runtime behavior.
Existing contracts in `docs/rules/` continue to describe their stated application
scope; an intentional project deviation there is not silently imported here.

## Scope and organization

Every behavior-bearing provision is in scope, including optional rules, player
and DM choices, individual content parameters, progression, prices and capacity,
and the lifecycle of passive and ongoing effects. Pure illustration, names offered
only as narrative examples, and licensing material may be excluded with a reason
in the coverage record. Acquisition and eligibility remain in scope even when a
stat block, spell, or item shares its resolution with another definition.

Files group rules with common dependencies and usable reading size. Core rules
have one primary home. Content catalogues use cohesive families or alphabetical
bands for discovery, not one file per source page or individual entry. Source
pages may therefore map to multiple destinations.

## Identifiers and references

Each independently referable rule or content entry has an explicit HTML anchor
and a stable identifier of the form `srd51.<family>.<name>` displayed in code.
The anchor replaces dots with hyphens, for example `srd51-spell-aid`. Identifiers
are independent of file paths and are not application keys, APIs, or schemas.
Use official English names and lowercase ASCII kebab-case name segments.
Disambiguate repeated feature names with their class or creature family.
For name slugs, remove apostrophes, replace other punctuation or whitespace with
hyphens, and collapse adjacent hyphens: Arcanist's Magic Aura becomes
`arcanists-magic-aura`; Antipathy/Sympathy becomes `antipathy-sympathy`.

Use relative Markdown links to primary definitions. Cite the source immediately
beside each definition and substantive exception, identifying page and section or
entry. From this directory the citation form is
`[SRD p. 102, Concentration](../../references/srd-5.1/pages/page-102.md)`.
Read adjacent pages for continuations. A citation never substitutes for writing
the behavior and all mechanically significant parameters.

## Semantic writing

Write original English explanations organized around decisions and state
transitions. Do not reproduce source sentences, paragraphs, or table layouts.
Preserve names, exact values, timing, mandatory versus optional rules, and all
exceptions. Tables are appropriate for independently organized parameter records;
their organization and prose must not reproduce a source table. Compact notation
is allowed only with a clear legend and enough detail to resolve behavior.

For an active rule state eligibility, trigger, cost, targets, choices, calculation,
outcome branches and subsequent obligations. For a passive rule state what it
changes and when it applies. For an ongoing rule state creation, repeated timing,
duration, suppression, interruption, termination and recovery. Do not invent an
action cost or duration where the source provides none. Link the shared rules
that supply defaults, then state the precise scope of each override.

Separate the following evidence categories explicitly where confusion is possible:

- **Source rule:** an explicit requirement, permission, or source-defined choice.
- **Derived conclusion:** a consequence of cited source provisions, with reasoning.
- **Project interpretation:** a selected operational reading where the source
  supports more than one reading, with the alternatives and reason recorded.
- **DM/player choice:** a decision the source deliberately leaves to that actor;
  it remains a runtime choice, not a universal project outcome.
- **Evidence gap:** missing or unreadable evidence, named precisely with affected
  definitions. Ordinary source discretion is not an evidence gap.

Use examples of normal outcomes, limits, exceptions, and invalid uses. Verify the
expected outcome against cited source passages. Examples are semantic acceptance
cases and do not assert that an executable test or implementation exists.

## Coverage and review

Each detailed file ends with a source coverage section mapping source pages and
their sections/entries to local identifiers, including continuations and justified
exclusions. Each author reads the assigned source in full, checks all entries and
parameters, and records any PDF layout checks and interpretation decisions.

The integrated coverage ledger separately records drafting and main-agent source
review. A file's existence, an identifier, or an entry count does not prove semantic
completeness. Runtime support is always **not assessed by this specification**.
Main review checks the actual prose against source, resolves dependencies, and
returns substantive defects to the author. Completion requires all in-scope
provisions to have complete destinations, not merely a page-level link.
