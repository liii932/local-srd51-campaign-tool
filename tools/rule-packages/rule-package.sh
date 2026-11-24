#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ ! -d "$project_root/target/classes/com/dndtool/offline/rules" ]]; then
  printf '%s\n' 'Expected compiled author tooling under target/classes.' >&2
  exit 1
fi
jvm_heap=64m
if [[ ${1:-} == schema2-verify ]]; then
  jvm_heap=256m
fi
exec timeout --signal=TERM --kill-after=5s 120s java "-Xmx$jvm_heap" \
  -cp "$project_root/target/classes" com.dndtool.offline.rules.RulePackageCli "$@"
