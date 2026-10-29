#!/usr/bin/env bash
# Android emulator runner executes its "script" via /bin/sh.
# Explicitly invoke this file with bash to preserve strict pipefail semantics.
set -euo pipefail
chmod +x gradlew
trap 'adb logcat -d -v time > device-logcat.txt 2>/dev/null || true' EXIT
# system_server can still be finishing startup after adb reports boot complete.
# Verify the real package service before attempting an instrumentation install.
# Emulator boot completion does not imply that ActivityManager/PackageManager
# has finished initializing, especially on API 26 without hardware acceleration.
# Use the portable 'pm path' command (also supported on old Android releases),
# and validate an actual system package rather than merely checking boot flags.
ready=0
for attempt in $(seq 1 180); do
  boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$boot" == "1" ]] &&
     adb shell pm path com.android.settings 2>/dev/null | grep -q '^package:'; then
    ready=1
    break
  fi
  if (( attempt % 15 == 0 )); then
    echo "Waiting for Android package service (attempt ${attempt}/180; boot=${boot:-unavailable})"
    adb devices -l || true
  fi
  sleep 2
done
if [[ "$ready" != "1" ]]; then
  echo 'Android emulator package manager not ready; no device tests executed' >&2
  adb devices -l >&2 || true
  adb shell getprop sys.boot_completed >&2 || true
  adb shell service check package >&2 || true
  adb shell pm path com.android.settings >&2 || true
  adb shell dumpsys activity processes 2>&1 | head -n 70 >&2 || true
  exit 1
fi
./gradlew -PextendedMedia=true :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.app.ui.JumloUiTest,com.jumlo.app.ui.WorkspaceRecoveryInteractionTest,com.jumlo.app.ui.ToolCatalogueInteractionTest,com.jumlo.app.ui.ScreenConfigSemanticsTest,com.jumlo.app.ui.PdfViewerInteractionTest,com.jumlo.app.ui.StudioGestureInteractionTest,com.jumlo.app.data.PreferencesPersistenceTest,com.jumlo.app.data.PdfHandlerReliabilityTest,com.jumlo.app.data.PdfOfficeHandlerTest,com.jumlo.app.data.ImageProcessingReliabilityTest,com.jumlo.app.data.ArchiveHandlerTest,com.jumlo.app.data.SourceAndPublicationTest,com.jumlo.app.data.PdfReaderAndScanStoreTest,com.jumlo.app.printing.JumloPrintAdapterTest \
  --no-parallel --max-workers=1 --no-daemon --console=plain 2>&1 | tee android-smoke.log
