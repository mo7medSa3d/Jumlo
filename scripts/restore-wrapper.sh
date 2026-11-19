#!/usr/bin/env bash
# Explicit, checksum-verified restoration only; not called by the build/preflight.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd -P)
DEST="$ROOT/gradle/wrapper/gradle-wrapper.jar"
EXPECTED=7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172
URL=https://raw.githubusercontent.com/gradle/gradle/v8.14.0/gradle/wrapper/gradle-wrapper.jar
hash() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d ' ' -f 1
  elif command -v shasum >/dev/null; then shasum -a 256 "$1" | cut -d ' ' -f 1
  else echo 'BLOCKED: SHA256_TOOL_MISSING' >&2; return 2; fi
}
if [ "${1:-}" = --check ]; then
  [ -f "$DEST" ] && [ "$(hash "$DEST")" = "$EXPECTED" ] || { echo 'BLOCKED: official wrapper missing or checksum mismatch'; exit 2; }
  echo 'PASS official Gradle 8.14 wrapper checksum'; exit 0
fi
if ! { [ "$#" -eq 1 ] && [ "$1" = --download ]; } && ! { [ "$#" -eq 2 ] && [ "$1" = --from ]; }; then
  echo 'Usage: scripts/restore-wrapper.sh --check | --from /path/to/official.jar | --download' >&2; exit 2
fi
mkdir -p "$(dirname "$DEST")"
TEMP=$(mktemp "$DEST.XXXXXX")
trap 'rm -f "$TEMP"' EXIT
if [ "$1" = --from ]; then
  cp "$2" "$TEMP"
else
  command -v curl >/dev/null || { echo 'BLOCKED: curl missing'; exit 2; }
  curl --fail --location --proto '=https' --proto-redir '=https' --tlsv1.2 --connect-timeout 10 --max-time 90 --output "$TEMP" "$URL"
fi
[ "$(hash "$TEMP")" = "$EXPECTED" ] || { echo 'WRAPPER_INTEGRITY_FAILED: downloaded/input bytes were rejected' >&2; exit 2; }
chmod 644 "$TEMP"
mv -f "$TEMP" "$DEST"
echo 'Restored official Gradle 8.14 wrapper; checksum verified. No SDK or Gradle distribution was installed.'
