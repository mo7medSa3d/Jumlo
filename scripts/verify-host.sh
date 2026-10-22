#!/usr/bin/env bash
# Dependency-free host checks only. This is not the Android/Gradle test suite.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
case "${1:-}" in
  "") bash "$ROOT/scripts/test-core.sh"
     # The PDF compression path, on a real JPEG, without a device.
     bash "$ROOT/scripts/test-pdf-compression.sh" ;;
  --with-media) bash "$ROOT/scripts/test-media-files.sh" ;;
  *) echo "Usage: bash scripts/verify-host.sh [--with-media]" >&2; exit 2 ;;
esac
python3 "$ROOT/scripts/test-media-bootstrap.py"
python3 "$ROOT/scripts/test-media-network-guard.py"
python3 "$ROOT/scripts/test-media-packaged-runtime.py"
python3 "$ROOT/scripts/test-universal-media-architecture.py"
python3 "$ROOT/scripts/test-release-media-isolation.py"
python3 "$ROOT/scripts/tests/test-media-artifact.py"
python3 "$ROOT/scripts/test-downloads-density.py"
python3 "$ROOT/scripts/test-storage-sql.py"
python3 "$ROOT/scripts/test-source-static.py"
python3 "$ROOT/scripts/test-redesign-static.py"
python3 "$ROOT/scripts/test-premium-ux.py"
python3 "$ROOT/scripts/test-neon-ui.py"
python3 "$ROOT/scripts/test-visual-editor.py"
python3 "$ROOT/scripts/test-watermark-studio.py"
python3 "$ROOT/scripts/test-smart-scan-wiring.py"
python3 "$ROOT/scripts/test-local-profile.py"
python3 "$ROOT/scripts/test-output-workflow-wiring.py"
python3 "$ROOT/scripts/test-flagship-reliability-wiring.py"
python3 "$ROOT/scripts/test-large-pdf-wiring.py"
python3 "$ROOT/scripts/test-jumlo-brand-motion.py"
python3 "$ROOT/scripts/test-phase1-to-4-regressions.py"
bash "$ROOT/scripts/tests/test-verify-local.sh"
bash "$ROOT/scripts/tests/test-launcher.sh"
