## Scope

- Describe the bounded change.

## Preserved contracts

- Applied MySQL migration history and checksums remain unchanged when retaining the database.
- Describe any effect on SQLite campaign rule bindings, format 3 saves, or the separate complete-SRD DRAFT release gate.
- For offline MySQL changes, verify the affected released v1 identities, digest domains and archive contract.
- The single-DM entry remains loopback-only; optional player HTTPS access follows the documented security boundary.
- No secrets, private campaign data, backups, logs or build outputs are included.

## Verification observed

List the checks covering this change and their observed results.

- Application changes — affected tests or build checks.
- Playable rule packages — author scenarios, candidate packages and application WAR used.
- Offline DRAFT language/tool catalog — independent catalog vector result.
- Production code or packaged resources — candidate WAR audit.
- Documentation or templates — content, links and formatting.

## Database, deployment and external actions

- [ ] No database DDL/DML/migrations/grants were executed.
- [ ] No Tomcat deployment or browser business writes were performed.
- List authorized Issue/PR/merge actions separately; identify any repository-setting or release changes.

## Notes for reviewers

- Add reviewer notes or write "None".
