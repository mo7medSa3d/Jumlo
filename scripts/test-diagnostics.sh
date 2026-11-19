#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
SOURCES=("$ROOT/core-common/src/main/kotlin/com/jumlo/app/core/diagnostics/DiagnosticEvent.kt" "$ROOT/scripts/host-tests/DiagnosticsMain.kt")
MAIN_CLASS=com.jumlo.app.hosttest.DiagnosticsMainKt
source "$ROOT/scripts/host-tests/embedded-kotlin.sh"
embedded_kotlin_run
