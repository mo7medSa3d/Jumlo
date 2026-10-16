#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
command -v kotlinc >/dev/null || { echo 'BLOCKED: Kotlin CLI not available'; exit 2; }
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
mapfile -t SOURCES < <(find "$ROOT/app/src/main/java/com/jumlo/app/core" "$ROOT/app/src/hostTest/kotlin" -name '*.kt' | sort)
SOURCES+=("$ROOT/app/src/main/java/com/jumlo/app/model/Models.kt" "$ROOT/app/src/main/java/com/jumlo/app/data/engine/PureDomainAlgorithms.kt")
kotlinc "${SOURCES[@]}" -jvm-target 17 -include-runtime -d "$BUILD/core-tests.jar"
java -jar "$BUILD/core-tests.jar"
