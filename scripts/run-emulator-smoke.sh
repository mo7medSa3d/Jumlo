#!/usr/bin/env bash
# Android emulator runner executes its "script" via /bin/sh.
# Explicitly invoke this file with bash to preserve strict pipefail semantics.
set -euo pipefail
chmod +x gradlew
trap 'adb logcat -d -v time > device-logcat.txt 2>/dev/null || true' EXIT
./gradlew -PextendedMedia=true :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.app.ui.JumloUiTest,com.jumlo.app.ui.WorkspaceRecoveryInteractionTest,com.jumlo.app.ui.ToolCatalogueInteractionTest,com.jumlo.app.ui.ScreenConfigSemanticsTest,com.jumlo.app.ui.PdfViewerInteractionTest,com.jumlo.app.ui.StudioGestureInteractionTest,com.jumlo.app.data.PreferencesPersistenceTest,com.jumlo.app.data.PdfHandlerReliabilityTest,com.jumlo.app.data.PdfOfficeHandlerTest,com.jumlo.app.data.ImageProcessingReliabilityTest,com.jumlo.app.data.ArchiveHandlerTest,com.jumlo.app.data.SourceAndPublicationTest,com.jumlo.app.data.PdfReaderAndScanStoreTest,com.jumlo.app.printing.JumloPrintAdapterTest \
  --no-parallel --max-workers=1 --no-daemon --console=plain 2>&1 | tee android-smoke.log
