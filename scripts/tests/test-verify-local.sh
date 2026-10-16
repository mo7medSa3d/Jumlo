#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/home" "$T/bin"
for tool in curl wget sdkmanager; do
  printf '#!/bin/sh\necho network >> "%s"\nexit 91\n' "$T/network" > "$T/bin/$tool"
  chmod +x "$T/bin/$tool"
done
set +e
HOME="$T/home" GRADLE_USER_HOME="$T/home/.gradle" GRADLE_BIN= ANDROID_HOME= ANDROID_SDK_ROOT= PATH="$T/bin:$PATH" bash "$ROOT/scripts/verify-local.sh" :app:assembleDebug > "$T/out" 2>&1
status=$?
set -e
[ "$status" -ne 0 ] || { echo 'FAIL missing prerequisites accepted'; exit 1; }
[ ! -e "$T/network" ] || { echo 'FAIL preflight accessed network'; exit 1; }
grep -q BLOCKED "$T/out" || { echo 'FAIL missing explicit BLOCKED state'; cat "$T/out"; exit 1; }
echo 'PASS uncachedDistributionDoesNotAccessNetwork'
echo 'PASS missingPrerequisitesAreBlockedNotPassed'
