#!/usr/bin/env bash
# Disposable loopback HTTPS prototype; installed/LAN deployment follows docs/deployment.md.
set -euo pipefail
if [[ $# -ne 1 || "$1" != /* ]]; then
  echo 'Usage: bash tools/storage/run-player-local.sh /absolute/disposable/storage-root' >&2
  exit 2
fi
player_test_root="$1"
player_project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd -- "$player_project_root"
mvn -B -ntp -DskipTests test-compile dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile=target/storage-loop-classpath.txt
player_classpath="target/test-classes:target/classes:$(<target/storage-loop-classpath.txt)"
exec java --enable-native-access=ALL-UNNAMED -cp "$player_classpath" \
  com.dndtool.web.PlayerLoopLauncher "$player_test_root"
