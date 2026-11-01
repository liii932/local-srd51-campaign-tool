#!/usr/bin/env bash
# Disposable local acceptance server; production deployment follows docs/deployment.md.
set -euo pipefail

if [[ $# -ne 1 || "$1" != /* ]]; then
  echo 'Usage: bash tools/storage/run-local.sh /absolute/private/test-root' >&2
  exit 2
fi
storage_test_root="$1"
storage_project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd -- "$storage_project_root"

mvn -q -DskipTests test-compile dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile=target/storage-loop-classpath.txt
storage_classpath="target/test-classes:target/classes:$(<target/storage-loop-classpath.txt)"
exec java --enable-native-access=ALL-UNNAMED -cp "$storage_classpath" \
  com.dndtool.web.StorageLoopLauncher "$storage_test_root"
