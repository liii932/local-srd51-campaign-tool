## Scope

- Describe the bounded change.

## Preserved contracts

- Applied MySQL migration history and checksums remain unchanged when retaining the database.
- Released `dnd5e2014_srd51_se_v1` identities, digest domains and archive format remain unchanged.
- The single-DM entry remains loopback-only; optional player HTTPS access follows the documented security boundary.
- No secrets, private campaign data, backups, logs or build outputs are included.

## Verification observed

- [ ] Targeted tests:
- [ ] Full regression, when required (command, reason and results; valid reports may be reused):
- [ ] WAR audit, if production code/resources changed:
- [ ] Documentation-only/template-only change; build not required.

## Database, deployment and external actions

- [ ] No database DDL/DML/migrations/grants were executed.
- [ ] No Tomcat deployment or browser business writes were performed.
- List authorized Issue/PR/merge actions separately; identify any repository-setting or release changes.

## Notes for reviewers

- Add reviewer notes or write "None".
