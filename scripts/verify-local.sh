#!/usr/bin/env bash
# Deliberately never calls the Wrapper: Wrapper bootstrapping precedes --offline.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
block() { echo "BLOCKED: $*" >&2; exit 2; }
GRADLE=${GRADLE_BIN:-}
if [ -z "$GRADLE" ]; then
  CACHE=${GRADLE_USER_HOME:-${HOME}/.gradle}/wrapper/dists/gradle-8.14-bin
  if [ -d "$CACHE" ]; then
    while IFS= read -r candidate; do GRADLE=$candidate; break; done < <(find "$CACHE" -type f -path '*/gradle-8.14/bin/gradle' 2>/dev/null)
  fi
fi
[ -n "$GRADLE" ] && [ -x "$GRADLE" ] || block 'Trusted cached Gradle 8.14 missing; set GRADLE_BIN. No download was attempted.'
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
command -v "$JAVA" >/dev/null || block 'JDK 17 missing.'
VERSION=$("$JAVA" -version 2>&1)
[[ "$VERSION" =~ version\ \"17\. ]] || block 'This build baseline requires JDK 17. Set JAVA_HOME explicitly.'
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$SDK" ] && [ -f "$SDK/platforms/android-36/android.jar" ] || block 'Android SDK platform 36 missing.'
[ -x "$SDK/build-tools/36.0.0/aapt2" ] || block 'Android Build Tools 36.0.0 missing.'
GVER=$("$GRADLE" --offline --console=plain --version 2>&1) || block 'Cached Gradle cannot launch.'
grep -q '^Gradle 8.14$' <<< "$GVER" || block 'Cached executable is not the pinned Gradle 8.14.'
export ANDROID_HOME="$SDK"
cd "$ROOT"
exec "$GRADLE" --offline --console=plain "$@"
