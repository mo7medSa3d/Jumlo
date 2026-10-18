#!/usr/bin/env bash
set -euo pipefail
# The Unicode filename tests create real filesystem entries. Some CI shells
# start in a POSIX/ASCII locale, where the JVM cannot encode those paths.
if locale -a 2>/dev/null | grep -qiE '^C\.(utf8|UTF-8)$'; then
  export LANG=C.UTF-8 LC_ALL=C.UTF-8
fi
ROOT=$(cd "$(dirname "$0")/.." && pwd)
command -v kotlinc >/dev/null || { echo 'BLOCKED: Kotlin CLI not available'; exit 2; }
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
mapfile -t SOURCES < <(find "$ROOT/app/src/main/java/com/jumlo/app/core" "$ROOT/app/src/hostTest/kotlin" -name '*.kt' | sort)
SOURCES+=("$ROOT/app/src/main/java/com/jumlo/app/model/Models.kt" "$ROOT/app/src/main/java/com/jumlo/app/data/engine/PureDomainAlgorithms.kt")
kotlinc "${SOURCES[@]}" -jvm-target 17 -include-runtime -d "$BUILD/core-tests.jar"
java -jar "$BUILD/core-tests.jar"
