#!/usr/bin/env bash
set -euo pipefail
JUMLO_ROOT=$(cd "$(dirname "$0")/.." && pwd)
command -v kotlinc >/dev/null || { echo 'BLOCKED: Kotlin CLI not available'; exit 2; }
JUMLO_BUILD=$(mktemp -d)
trap 'rm -rf "$JUMLO_BUILD"' EXIT
kotlinc "$JUMLO_ROOT/app/src/main/java/com/jumlo/app/core/TemporaryCleanup.kt" \
  "$JUMLO_ROOT/scripts/host-tests/WorkspaceSafetyMain.kt" \
  -jvm-target 17 -include-runtime -d "$JUMLO_BUILD/workspace-safety.jar"
java -jar "$JUMLO_BUILD/workspace-safety.jar"
