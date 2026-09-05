# Repository instructions

These instructions apply to automated contributors working in this repository. Communicate clearly, preserve user work, and distinguish guidance from actions that change files or external systems.

## Start from repository evidence

1. Read this file from the repository root.
2. Inspect `git status --short --branch`, staged names, staged diff, unstaged diff, untracked files and remotes.
3. Treat every existing staged, unstaged or untracked change as user-owned unless repository evidence proves otherwise.
4. Read [docs/agent-context.md](docs/agent-context.md) for the compact project map, then read the closest production code, tests and relevant authoritative documents before editing.
5. Treat old conversations, historical reports, handoffs and historical test results only as search hints. Re-establish progress from the current repository, Git state and any explicitly authorized external evidence.
6. Summarize the bounded change, protected contracts and intended verification before implementation.

## Sources of truth

- [docs/agent-context.md](docs/agent-context.md): compact orientation and pointers; it does not override the authorities below.
- [docs/architecture.md](docs/architecture.md): product scope, components, trust and transaction boundaries.
- [docs/rules/srd-5.1.md](docs/rules/srd-5.1.md): frozen v1 stable keys, algorithms, ranges and canonical encoding.
- `src/main/resources/db/migration/`: approved, forward-only schema and seed history.
- [docs/database.md](docs/database.md): migration and account responsibilities.
- [docs/testing.md](docs/testing.md): repeatable verification methods.
- [docs/deployment.md](docs/deployment.md): build, rollback, deployment and HTTP acceptance.

If these sources conflict, compare the relevant migration, frozen rules and current code. Do not silently persist data under an invented interpretation.

## Local command environment

- On Windows, use PowerShell 7 (`pwsh -NoLogo -NoProfile`) as the default shell for repository inspection, builds, tests and scripts.
- Consult the `use-local-tool-paths` registry when available and invoke its verified `pwsh`, Git, Maven, Java, Python, Node.js and ripgrep paths. Use PowerShell's call operator for executable paths containing spaces.
- Write commands in PowerShell syntax and use Windows paths. Do not translate repository or tool paths to `/mnt/*` or rely on WSL command behavior.
- If the agent harness exposes only a tool named `bash`, use that tool only as a transport to start the registered Windows `pwsh`; execute the repository command itself inside PowerShell.
- Use WSL or a POSIX shell only when the task explicitly requires Linux semantics and state that exception before running it. A missing Windows command is not by itself a reason to switch to WSL; use the verified local tool path or perform one narrow lookup.

## Product and security boundaries

- The supported entry is `http://127.0.0.1:8080`; the product is a loopback-only, single-DM tool.
- LAN/public access, HTTPS, player accounts, `/display` and `/api/public/*` remain deferred until separately designed and reviewed.
- Client input is untrusted. Dice, selected candidates, derived modifiers, algorithms, canonical identities, row versions and audit results are server-owned unless a documented API declares a bounded input.
- Validate a complete request, frozen catalog, target set and optimistic versions before consuming randomness or writing audit rows.
- Enforce Unicode policies by code point, normalize to NFC where required and reject C0/C1 controls. Do not substitute Java `String.length()`.
- Unknown, duplicate, unpublished, malformed or unsupported frozen rules fail closed with stable business errors.
- Authoritative state, root event, snapshots, effects, field changes, versions and idempotency results commit or roll back together.
- Repositories designed for a caller-owned transaction must not commit or roll it back.

## Stable contracts

- Never edit, merge, rename or rerun an applied migration. V001—V018 are immutable history; add the next forward-only `V###` migration for an approved schema change.
- Preserve the `RELEASED` `dnd5e2014_srd51_se_v1`, its canonical/archive format values, algorithm keys, field keys, event/effect types and persisted digest domains.
- Existing DRAFT migration files and persisted identifiers remain immutable history, but an unpublished DRAFT is not a compatibility promise. Its catalog, runtime model, APIs, canonical/archive projection and tests may evolve cohesively through reviewed forward migrations until the cross-domain release candidate; do not add compatibility shims for obsolete DRAFT behavior unless an acceptance criterion identifies real data that must survive.
- Keep `dnd5e2014_srd51_se` DRAFT, keep the legacy release as the new-campaign default and do not activate/freeze archive format 2 until the complete character, equipment/adventure, combat/condition, spell and monster/magic-item domains pass the cross-domain release gate.
- Preserve `com.dndtool`, Maven coordinates, `jdbc/DndToolSE` and existing environment-variable contracts unless a dedicated migration plan explicitly changes them.
- Keep database credentials, Connector/J secrets, certificates, Tomcat external configuration, logs, backups and real save data outside Git and WAR.

## Implementation workflow

1. Write or update focused tests for normal behavior, boundaries, malformed frozen data, stale versions/idempotency, transaction failure and forbidden partial writes as applicable.
2. Make the smallest cohesive production change. Reuse existing services, repositories, event sequencing, error mapping and security filters.
3. Treat compatibility as scoped work, not the default priority: protect RELEASED/persisted contracts, but prefer completing and integrating the current DRAFT model over preserving superseded DRAFT behavior.
4. Keep Servlet/API parsing, service validation, persistence and module-catalog responsibilities separated.
5. Run targeted tests first, then `mvn clean verify` after code, resource or migration changes.
6. When production code or packaged resources change, audit the WAR for byte size, SHA-256, entry count, required assets and forbidden/sensitive files. Review configuration/resource diffs for embedded secrets.
7. Update public documentation with stable procedures, not changing PIDs, timestamps, personal paths or one-off execution logs.

## Public repository content

- Write public documentation as current contracts and repeatable procedures. Do not preserve migration timelines, dated acceptance claims, local IP addresses, certificate failures, terminal transcripts or machine-specific paths as project guidance.
- Keep community entry files at the repository root. Put durable documentation under `docs/`, verification SQL under `database/verify/`, account grants under `database/grants/`, integration-test setup under `database/test/`, external configuration examples under `config/` and reusable maintenance tools under `tools/`.
- Do not move result captures, logs, table dumps, temporary recovery scripts or obsolete planning backups into `legacy`, `archive` or compatibility directories. Extract durable rules first, then delete material with no long-term value.
- Use conventional root filenames; use lowercase ASCII kebab-case for documentation, scripts, helper SQL and directories; use responsibility-based PascalCase names for Java types. Applied Flyway filenames are exempt and immutable.
- Treat code renames as domain changes: check destination conflicts and atomically update filenames, type names, imports, tests, registrations, reflection strings, configuration and documentation. Do not mechanically remove `Stage` from persisted operation names, digest domains or migration references.
- GitHub Issues and Milestones are the durable home for unfinished public work. Before deleting an unchecked todo, identify its remaining items and confirm that they are completed, obsolete, deferred by current scope or transferred to GitHub; never claim an external transfer without evidence.
- A normal deletion removes a file only from the current tree, not from reachable Git history. If credentials, private data or other material requiring historical removal is found, stop and request a dedicated incident and history-rewrite plan.

## Database and deployment

- Do not execute DML, DDL, migrations, grants or real business writes without explicit authorization for that external state change.
- Migrator, application, read-only verifier and integration-test accounts have distinct responsibilities; never broaden the application account as a shortcut.
- A SQL file in the repository is not evidence that it ran. Migration, verification, grant and deployment are separate checkpoints.
- Follow [docs/deployment.md](docs/deployment.md); do not invent a second deployment method.
- Build and audit before deployment. Back up the complete active root application before replacement and verify candidate/active hashes.
- Do not start or stop Tomcat, replace `ROOT.war` or perform browser writes without the required authorization and recovery boundary.

## Git hygiene

- Never use reset, checkout, clean or destructive bulk formatting to make a dirty tree look clean.
- Inspect both staged and unstaged diffs; a file can contain older staged content and newer working-tree edits.
- Stage only the authorized change. Re-add a previously staged file after editing so the index is not stale.
- Before reporting staging complete, run `git diff --cached --check`, list staged names and identify pre-existing staged work.
- Do not commit, amend, push, rebase, create/switch branches, rewrite history or force-push without explicit authorization.
- Do not stage build outputs, logs, secrets, backups or private captured data.

## GitHub collaboration

- Treat fetching, pushing, opening or updating a pull request, creating Issues or Milestones, merging and deleting remote branches as separate external checkpoints. Perform only the checkpoints the user has authorized.
- Prefer one descriptive branch and one cohesive pull request for a bounded migration. Automated contributor branches use the `codex/` prefix unless the user specifies another name.
- Before pushing, fetch the intended remote, confirm the base branch has not advanced unexpectedly, verify the working tree is clean and push only the named branch. Never use `--all`, `--mirror` or force push as a shortcut, and never publish backup/original refs.
- A pull request description must state scope, preserved contracts, tests actually observed, WAR audit when applicable and database/deployment actions explicitly not performed. “No conflicts” is not evidence that tests or review passed.
- Review the final GitHub diff and required checks before merging. After merge, fetch with pruning and fast-forward the local base branch; delete only the exact merged feature branch. For a squash merge, verify tree or patch equivalence before removing a branch whose commit is not an ancestor of the base.

## Completion report

Report the completed behavior and invariant, targeted/full test results actually observed, WAR audit when applicable, database/deployment actions performed or explicitly not performed, documentation changes, Git staging state and any remaining user decision.
