# Android PDF compression: fixture and storage preflight

**Source instructions only — NOT A DEVICE TEST RESULT.** This file restores the
preflight link used by [README.md](../../../../README.md) and
[RELEASE_CHECKLIST.md](../../../../RELEASE_CHECKLIST.md).

The gate is `PdfCompressionDeviceTest`, implemented at
`app/src/androidTest/java/com/jumlo/app/pdf/PdfCompressionDeviceTest.kt`.
It fails when its five input PDFs are missing; skipping the test or substituting
a synthetic PASS is not accepted. The host corpus recipe and Android codec are
distinct qualifications. None of the commands here has been executed by this
document.

## 1. Generate and identify the five scan inputs on a full JDK

Use JDK 17, the repository's Gradle dependency cache (including
`pdfbox-android` and Kotlin compiler), and free host disk space. Run from the
repository root:

```bash
bash scripts/test-pdf-compression.sh --measure
CORPUS="${TMPDIR:-/tmp}/jumlo-compression-measure"
find "$CORPUS" -maxdepth 1 -name 'scan_*pages.pdf' -type f -print
for pages in 01 03 06 10 30; do
  test -s "$CORPUS/scan_${pages}pages.pdf" || exit 1
  sha256sum "$CORPUS/scan_${pages}pages.pdf"
done
```

The generator uses the JVM's `java.io.tmpdir`, normally `/tmp` on Linux.
If `TMPDIR` differs from that JVM property, find the actual
`jumlo-compression-measure` directory and set `CORPUS` accordingly.
The generated PDFs have 1/3/6/10/30 2550×3300 JPEG scan pages. **No fixed
SHA-256 values are supplied in this repository**: every fresh corpus must record
its own hashes alongside the benchmark output. Do not present hashes from a
different run as fixtures used here. Host `--measure` output alone does not
qualify Android images, foreground services or device memory.

## 2. Verify device/host capacity and stage exactly those inputs

Build the debug and instrumentation APKs on a configured Android SDK 36
installation, install the debug app, and connect one authorized emulator/device:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell df -h /data /sdcard
df -h "$HOME/.android/avd" /tmp
```

The unfiltered suite also generates a **200-page scan** from 20 copies of the
10-page fixture. Allow for the original ten-page PDF, merged 200-page input,
compressed output, temporary PDFBox files and concurrent guest/host growth.
A guest budget of roughly **4–4.5 GiB** was discussed for prior testing, but
that is a planning allowance, **not measured peak usage or an assurance**.
If capacity is insufficient, mark this gate **BLOCKED: insufficient guest/host
space** and do not run it with missing fixtures.

The instrumentation target uses the app's own `getExternalFilesDir(null)/bench`,
normally the following directory for the `debug` package:

```bash
DEST=/sdcard/Android/data/com.jumlo.app.debug/files/bench
adb shell mkdir -p "$DEST"
for pages in 01 03 06 10 30; do
  adb push "$CORPUS/scan_${pages}pages.pdf" "$DEST/scan_${pages}pages.pdf"
  adb shell sha256sum "$DEST/scan_${pages}pages.pdf"
done
```

Compare the on-device hashes with the generated host SHA-256 manifest before
testing. On Android versions or providers where shell access to
`Android/data` is denied, do **not** claim staging succeeded; use an explicitly
granted app-private transfer mechanism or mark **BLOCKED: fixture staging**.
Do not stage previously compressed `*-out.pdf` outputs in place of inputs.

## 3. Run, collect and qualify the device measurements

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jumlo.app.pdf.PdfCompressionDeviceTest
adb pull "$DEST/device-compression-report.txt" device-compression-report.txt
adb shell dumpsys meminfo com.jumlo.app.debug > device-meminfo.txt
```

The source instrumentation must pass all of:
- `theScannedCorpusIsCompressedOnThisDevice`: five inputs × three preset
  levels, actual before/after bytes, strict reduction, retained page count,
  embedded images and no skipped valid images
- `aTwoHundredPageScanIsProcessedOnThisDevice`: retained 200-page scan,
  no out-of-memory failure and measured peak Java/native memory
- `aDocumentWithAnUnreadableImageIsReportedAndTheRestSurvives`:
  corrupt image stream is surfaced, not silently reported as fully optimized

Record the exact source commit, API level/ABI, app/test APK hashes, Gradle
command, generated corpus input hashes, test XML result, device report,
`dumpsys meminfo`, emulator/disk configuration and any skipped methods.
Use `adb pull` only for actual produced files. The helper's Java/native heap
sampling is not a full system-wide peak; distinguish it from `dumpsys` and
host QCOW/disk growth. Reopen and visually inspect generated pages.

## 4. Qualification outcome

Status remains **NOT RUN** until those commands produce matching-artifact
device evidence. A missing input, unauthorized device, absent emulator, failure
to stage files, service installation problem, insufficient disk or unexecuted
test is **BLOCKED**, never PASS. File hashes are generated per run and should be
entered into `TEST_REPORT.md` with the corresponding CI/artifact link.
