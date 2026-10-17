#!/usr/bin/env bash
# Native verification: one task and one exit status per log, stop at first failure.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd -P)
cd "$ROOT"
MODE=${1:-gate}
LOG_DIR=${JUMLO_BUILD_LOG_DIR:-verification/native}
mkdir -p "$LOG_DIR"
printf 'source_sha=%s\nmode=%s\nextendedMedia=false\n' "${GITHUB_SHA:-local-uncommitted}" "$MODE" > "$LOG_DIR/context.txt"
run_task() {
  local label=$1
  shift
  set +e
  bash ./gradlew --no-daemon --console=plain --stacktrace -PextendedMedia="${JUMLO_NATIVE_EXTENDED_MEDIA:-false}" "$@" 2>&1 | tee "$LOG_DIR/$label.log"
  local code=${PIPESTATUS[0]}
  set -e
  printf '%s\t%s\n' "$label" "$code" >> "$LOG_DIR/exit-codes.tsv"
  return "$code"
}
: > "$LOG_DIR/exit-codes.tsv"
case "$MODE" in
  gate)
    run_task configuration help
    run_task debug :app:assembleDebug
    run_task unit :app:testDebugUnitTest
    run_task lint :app:lintDebug
    run_task release :app:assembleRelease
    run_task release-bundle :app:bundleRelease
    python3 scripts/inspect-standard-artifact.py \
      app/build/outputs/apk/debug/*.apk app/build/outputs/apk/release/*.apk \
      app/build/outputs/bundle/release/*.aab --output "$LOG_DIR/standard-content.json"
    : > "$LOG_DIR/apk-zip-alignment.log"
    for apk in app/build/outputs/apk/debug/*.apk app/build/outputs/apk/release/*.apk; do
      printf 'Checking %s\n' "$apk" | tee -a "$LOG_DIR/apk-zip-alignment.log"
      "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/36.0.0/zipalign" -c -P 16 4 "$apk" 2>&1 | tee -a "$LOG_DIR/apk-zip-alignment.log"
      printf 'PASS\n' | tee -a "$LOG_DIR/apk-zip-alignment.log"
    done
    run_task benchmark-compile :app:assembleBenchmark :benchmark:assembleBenchmark
    run_task base-dependencies :app:dependencies --configuration releaseRuntimeClasspath
    if grep -Eq 'youtubedl-android|io.github.junkfood02|com.arthenica|ffmpeg-kit' "$LOG_DIR/base-dependencies.log"; then
      echo 'ERROR: extended media dependency found in the base release graph' >&2
      exit 1
    fi
    # Prove that opting into the internal debug media build cannot leak
    # native media binaries into the release dependency graph.
    JUMLO_NATIVE_EXTENDED_MEDIA=true run_task extended-media-release-dependencies :app:dependencies --configuration releaseRuntimeClasspath
    if grep -Eq 'youtubedl-android|io.github.junkfood02|com.arthenica|ffmpeg-kit' "$LOG_DIR/extended-media-release-dependencies.log"; then
      echo 'ERROR: debug-only media dependency leaked into extended release' >&2
      exit 1
    fi
    ;;
  instrumentation) run_task instrumentation :app:connectedDebugAndroidTest ;;
  baseline)
    run_task baseline :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.benchmark.BaselineProfileGenerator
    ;;
  benchmark)
    run_task benchmark :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.benchmark.StartupBenchmark
    ;;
  *) echo 'Usage: scripts/verify-native.sh gate|instrumentation|baseline|benchmark' >&2; exit 2 ;;
esac
