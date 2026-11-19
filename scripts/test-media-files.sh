#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
for executable in ffmpeg ffprobe python3; do
  command -v "$executable" >/dev/null || { echo "BLOCKED: $executable not installed; real media tests not executed"; exit 2; }
done
UU_TEST_REAL_MEDIA=1 bash "$ROOT/scripts/test-core.sh"
