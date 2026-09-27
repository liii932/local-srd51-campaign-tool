#!/usr/bin/env bash
set -euo pipefail
# Use a reviewed Maven build from this checkout. No download/build/migration happens here.
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ ! -d "$project_root/target/classes/com/dndtool/offline/rules" ]]; then
  printf '%s\n' 'Compile the project first: mvn -DskipTests compile' >&2
  exit 1
fi
if [[ ${1:-} == build || ${1:-} == verify ]]; then
  classpath="$project_root/target/classes"
else
  : "${DND_RULE_INSTALLER_JDBC_JAR:?Set the absolute path to the independently supplied Connector/J jar}"
  [[ "$DND_RULE_INSTALLER_JDBC_JAR" = /* && -f "$DND_RULE_INSTALLER_JDBC_JAR" ]] || exit 1
  classpath="$project_root/target/classes:$DND_RULE_INSTALLER_JDBC_JAR"
fi
# Hard process boundary, including input IO and driver calls; no worker can continue in this JVM.
# A killed attempt retains its pre-DML pending ticket and needs independent server-side isolation.
exec timeout --signal=TERM --kill-after=5s 120s java -Xmx64m -cp "$classpath" com.dndtool.offline.rules.RulePackageCli "$@"
