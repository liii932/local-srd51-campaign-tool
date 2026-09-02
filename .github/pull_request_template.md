## Scope

- Describe the bounded change.

## Preserved contracts

- V001--V017 applied migrations remain unchanged.
- Released `dnd5e2014_srd51_se_v1` identities, digest domains and archive format remain unchanged.
- Loopback-only, single-DM product boundary remains unchanged.
- No secrets, private campaign data, backups, logs or build outputs are included.

## Verification observed

- [ ] Targeted tests:
- [ ] `mvn clean verify`:
- [ ] WAR audit, if production code/resources changed:
- [ ] Documentation-only/template-only change; build not required.

## Database, deployment and external actions

- [ ] No database DDL/DML/migrations/grants were executed.
- [ ] No Tomcat deployment or browser business writes were performed.
- [ ] No GitHub visibility, branch protection, issue, PR, merge or release action was performed beyond this PR.

## Notes for reviewers

- Add reviewer notes or write "None".
