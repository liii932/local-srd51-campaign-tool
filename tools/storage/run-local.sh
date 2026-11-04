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

echo '[1/3] 检查本地启动环境…'
for storage_tool in mvn java; do
  if ! command -v "$storage_tool" >/dev/null 2>&1; then
    echo "无法启动：缺少 $storage_tool，请先准备 Java 21+ 和 Maven。" >&2
    exit 2
  fi
done
if [[ -e "$storage_test_root" && ! -d "$storage_test_root" ]]; then
  echo "无法启动：存储根不是目录：$storage_test_root" >&2
  exit 2
fi

echo '[2/3] 编译并准备启动依赖（首次运行可能需要几分钟）…'
storage_build_started=$SECONDS
if mvn -B -ntp -DskipTests test-compile dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile=target/storage-loop-classpath.txt; then
  echo "编译准备完成（$((SECONDS - storage_build_started)) 秒）。"
else
  storage_build_status=$?
  echo "编译准备失败（退出码 $storage_build_status），请查看上方 Maven 错误。" >&2
  exit "$storage_build_status"
fi
storage_classpath="target/test-classes:target/classes:$(<target/storage-loop-classpath.txt)"
echo '[3/3] 启动本地战役应用，等待就绪提示：http://127.0.0.1:8080/host/storage'
exec java --enable-native-access=ALL-UNNAMED -cp "$storage_classpath" \
  com.dndtool.web.StorageLoopLauncher "$storage_test_root"
