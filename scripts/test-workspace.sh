#!/usr/bin/env bash
set -euo pipefail
JUMLO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
source "$JUMLO_ROOT/scripts/host-tests/embedded-kotlin.sh"
JUMLO_BUILD=$(mktemp -d)
trap 'rm -rf "$JUMLO_BUILD"' EXIT
SOURCES=(
  "$JUMLO_ROOT/app/src/main/java/com/jumlo/app/core/TemporaryCleanup.kt"
  "$JUMLO_ROOT/scripts/host-tests/WorkspaceSafetyMain.kt"
)
BUILD=$JUMLO_BUILD
MAIN_CLASS=com.jumlo.app.hosttest.WorkspaceSafetyMainKt
if command -v kotlinc >/dev/null; then
  kotlinc "${SOURCES[@]}" -jvm-target 17 -include-runtime -d "$JUMLO_BUILD/workspace-safety.jar"
  java -jar "$JUMLO_BUILD/workspace-safety.jar"
else
  embedded_kotlin_run
fi
