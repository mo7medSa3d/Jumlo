#!/usr/bin/env bash
# Dependency-free host checks only. This is not the Android/Gradle test suite.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
case "${1:-}" in
  "") bash "$ROOT/scripts/test-core.sh" ;;
  --with-media) bash "$ROOT/scripts/test-media-files.sh" ;;
  *) echo "Usage: bash scripts/verify-host.sh [--with-media]" >&2; exit 2 ;;
esac
python3 "$ROOT/scripts/test-media-bootstrap.py"
python3 "$ROOT/scripts/test-storage-sql.py"
python3 "$ROOT/scripts/test-source-static.py"
python3 "$ROOT/scripts/test-redesign-static.py"
python3 "$ROOT/scripts/test-jevlo-brand-motion.py"
bash "$ROOT/scripts/tests/test-verify-local.sh"
bash "$ROOT/scripts/tests/test-launcher.sh"
