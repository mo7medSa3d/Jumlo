#!/usr/bin/env bash
set -euo pipefail
REPO_DIR=$(cd "$(dirname "$0")/../../.." && pwd)
command -v kotlinc >/dev/null || { echo 'Kotlin CLI required'; exit 2; }
UI_TEST_BUILD=$(mktemp -d)
trap 'rm -rf "$UI_TEST_BUILD"' EXIT
kotlinc "$REPO_DIR/app/src/main/java/com/jumlo/app/ui/home/HomeActionPolicy.kt" \
  "$REPO_DIR/app/src/main/java/com/jumlo/app/ui/adaptive/ViewportPolicy.kt" \
  "$REPO_DIR/verification/production-2026-10-17/ui/HomeLayoutPolicyTests.kt" \
  -include-runtime -d "$UI_TEST_BUILD/tests.jar"
java -jar "$UI_TEST_BUILD/tests.jar"
