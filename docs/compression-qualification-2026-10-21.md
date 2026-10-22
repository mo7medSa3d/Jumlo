# PDF compression for scanned pages — qualification report

Date: 2026-10-21
Scope: the `pdf_compress` tool on photographed (scanned) PDFs.

## Process note

The `ccg` multi-model workflow did **not** run. The `ccg-workflow` runtime is not
installed in this environment and `npx ccg init` is an interactive terminal program
that asks for an API key, so the Codex and Antigravity calls described in the plan
were not executed. The review was performed by independent agents within one
harness. Nothing in this report should be read as independent verification by two
models.

## What was wrong, and what fixed it

The compression pipeline lived in `data/pdf/PdfBoxBackend.kt`. Three separate
defects stacked up:

1. **The installed build could not decode a 300 DPI scan.** In pdfbox-android the
   JPEG fast path in `SampledImageReader.getRGBImage` is only taken when the
   subsampling factor is exactly 1 (verified in the decompiled artifact:
   `185: iload_2 / 186: iconst_1 / 187: if_icmpne 200`). `PdfCompressionPolicy
   .decodeSubsampling` returns **2** for every 300 DPI scan, because
   2550 × 3300 × 4 = 33.66 MB is over the 32 MiB cap and the cap is
   heap-independent. So the fast path was skipped and `from8bit` read the
   *compressed JPEG bytes as raw pixels*. The result was swallowed and the image
   was skipped, which is why the output was the input again: re-saving
   `scan_01pages.pdf` changed it by **-0.03 %**.
2. **The ladder could not go below the chosen resolution.** `profiles()` built its
   steps with `min(cappedDpi, 170 / 135 / 108)`, so every preset of 170 dpi or
   less collapsed onto a single rung. A page that resisted that one rung was left
   alone. The floor is now below the choice and follows the quality the user
   asked for (`PdfCompressionPolicy.ladderFloor`).
3. **Every skip was silent and one bad image killed the job.** The per-image
   failures were collected and then thrown after the loop, so 99 good pages were
   discarded because page 100 had one undecodable image, and the detail was
   mangled to `PROCESSING_FAILED` by `stableError()`'s regular expression.

## What changed

| file | change |
|---|---|
| `data/pdf/PdfImageCodec.kt` | **new** — the byte-level seam: the raw image bytes in, the re-encoded JPEG out. No Android types in the interface, so a host implementation can exist. |
| `data/pdf/AndroidImageCodec.kt` | **new** — `BitmapFactory` in, a scaled JPEG out. The decode is budgeted (`ImageWorkPolicy.requireFits` + a sample size taken from the image header), so a 2550×3300 page never allocates 33.6 MB. |
| `data/pdf/PdfImageCompressor.kt` | **new** — the compressor itself, with no Android types in it, so the same code runs on the host. The ladder is walked **per image**, not per document. `OutOfMemoryError` is caught on purpose, with the reason written next to it. |
| `core/PdfPageGeometry.kt` | the ladder floor is below the chosen resolution; the presets carry the measured reduction range. |
| `data/pdf/PdfBoxBackend.kt` | delegates to the compressor and keeps its old public shape. |
| `data/processing/PdfOperationHandler.kt` | passes the job's bitmap budget; publishes the file even when some pages could not be re-encoded, and reports how many. |
| `ui/components/UserMessages.kt`, `res/values*/error_messages.xml` | two new messages: the compression was partial, or it failed. |

## Gate results

| Gate | Result | Evidence |
|---|---|---|
| **A — shipped build identity** | **PARTIAL** | The baseline artifact is recorded below. The "after" artifact is the APK built from this change; the measurement was run on the host against the same sources, because no Android device is reachable from this environment. The on-device run is still open. |
| **B — memory and OOM on real hardware** | **FAIL (device)** | The decode is now budgeted and an `OutOfMemoryError` is caught per image and reported, and the 200-page case was run on the host (30 pages, no crash). The **device** measurement — the model, the RAM, the Android version, the peak heap — could not be taken: there is no device, emulator or simulator in this environment. |
| **C — acceptance targets from measurements** | **PASS** | The presets are defined by DPI and JPEG quality, each carries the range it measured, the ladder reaches below the chosen resolution, and the stale target size no longer overrides the preset. The numbers are in the table below. |
| **D — CI coverage of the JPEG path** | **PASS, with one caveat** | The full JPEG path now runs on a real 300 DPI scan with no device, in `scripts/test-pdf-compression.sh` (ALL PASS). The caveat: the Android unit-test source set replaces the JDK with `android.jar`, which has no `javax.imageio`, so `BitmapFactory` returns null there. The Gradle unit-test task therefore covers the compressor's decisions with a stub codec (`PdfImageCompressorTest`), and the real decode is covered by the host suite, which runs on a full JDK. |

### The measured numbers

`bash scripts/test-pdf-compression.sh --measure`, on the host, no device. The scan
corpus is generated by the suite (1, 3, 6, 10 and 30 pages, every page a
2550×3300 JPEG on a US Letter page). The photo corpus is the six real phone
photos of paper in `tests/ocr_benchmark/pages/`.

| corpus | Best quality (240 dpi / q90) | Balanced (180 / q75) | Small file (135 / q58) | Strong (108 / q45) |
|---|---:|---:|---:|---:|
| 300 dpi scans (1–30 pages) | 39.8 % | 81.3 % | 92.3 % | **96.0 %** |
| real phone photos (≈120 dpi) | 0.0 % | 11.3 % | 9.0 % | **28.1 %** |

The Strong preset clears the 50 % target on every scanned file in the corpus
(96.0 %). It does **not** clear it on the low-resolution phone photos (28.1 %),
and the range published to the user says so.

Before and after, per file, on the 30-page scan:

| file | pages | preset | before | after | saved | seconds |
|---|---:|---|---:|---:|---:|---:|
| scan_01pages.pdf | 1 | Strong | 3,877,137 | 156,999 | 96.0 % | 0.2 |
| scan_03pages.pdf | 3 | Strong | 11,632,826 | 470,286 | 96.0 % | 0.6 |
| scan_06pages.pdf | 6 | Strong | 23,270,525 | 940,533 | 96.0 % | 1.2 |
| scan_10pages.pdf | 10 | Strong | 38,785,435 | 1,567,141 | 96.0 % | 1.8 |
| scan_30pages.pdf | 30 | Strong | 116,340,967 | 4,701,007 | 96.0 % | 5.6 |

The times are host times. A phone is slower; the device number is not measured.

### The artifact

| field | value |
|---|---|
| baseline APK | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| baseline SHA-256 | `4d42e7c46b2d9b7ff2bc221acca1fc5e3ebdb22650cb5607e7c8b13171de470e` |
| baseline size | 10,637,693 bytes |
| versionCode / versionName | 10842 / 1.4-media-candidate |
| git commit of the baseline | `07d80b716c1a01524d55eac2e15c2a15546712cb` (the working tree was dirty) |
| variant | release (unsigned) |
| the "after" artifact | `app/build/outputs/apk/release/app-release-unsigned.apk`, built from commit `871dea8` |
| the "after" SHA-256 | `e51e7f3d3a01038be5c62ef1501db565757c62c01ab4538f7ea599d902c8f05b` |
| the "after" size | 10,635,901 bytes (the policy allows 60 MB) |
| the "after" variant | release (unsigned) |

The APK is **not byte-reproducible**: two builds of the same sources gave
`395b5d19…` and `e51e7f3d…` with the same size, because the archive carries a
build timestamp. The SHA-256 therefore identifies *the artifact*, and the commit
identifies *the sources*; the measurement was run against those sources.

The release variant was rebuilt after the palette refresh, so the SHA above is
the artifact that also carries the new 2026 palette.

## The tests

| suite | result |
|---|---|
| `./gradlew :app:testDebugUnitTest --offline` | 325 tests, 0 failures, 2 skipped (the two live media-service tests need `JUMLO_MEDIA_SERVICE`) |
| `bash scripts/verify-host.sh` | exit 0 — 181 Kotlin host cases, the new PDF compression suite, and the 16 Python suites |
| `bash scripts/test-pdf-compression.sh` | ALL PASS — the full JPEG path on a real 300 DPI scan, a document with a 150 dpi and a 300 dpi image, and an unreadable image that is reported without stopping the document |

### Two tests were changed, and why

`app/src/test/java/com/jumlo/app/core/PdfCompressionPolicyTest.kt` and
`app/src/hostTest/kotlin/CoreTests.kt` asserted the *old* ladder:

```kotlin
assertEquals(listOf(240 to 90), PdfCompressionPolicy.profiles(240, 90))
assertEquals(listOf(240 to 75, 170 to 75), PdfCompressionPolicy.profiles(240, 75))
```

Those assertions encoded the defect: the floor was the chosen resolution, so a
preset of 170 dpi or less had exactly one rung. They now assert that the ladder
reaches below the chosen resolution while keeping the quality the user asked
for. The test that says the quality is never changed on the way down is kept and
strengthened.

`PdfStructureCoreTest.compress_keeps_text_and_shrinks_images` failed to compile
during the change because `PdfBoxBackend.compress` had been replaced by the new
entry points. The old name was restored as a thin delegate rather than the test
being changed, because the test is right: the text must survive and the file
must get smaller.

## What still does not work

- **No device, emulator or simulator is reachable from this environment.** The
  on-device peak heap for one 300 DPI page, the 200-page document on a low-end
  phone, the on-device time and the on-device reduction are all **not measured**.
  The host numbers are a proxy, not a substitute.
- **A scanned PDF with an `/SMask` or a one-bit raster is left alone**, on
  purpose, and the reason is recorded. The tool reports it as a partial
  compression; it does not flatten the mask.
- **The low-resolution photo case** (a page whose photo is already at about
  120 dpi) cannot reach 50 % with the Strong preset: the image is already small
  enough that re-encoding it at a lower quality is the only lever left, and the
  ladder stops at 60 dpi.
- **The service-side audio path is still broken** (`--get-url` with `-x` never
  converts), and the client still never asks for a muxed format. Those are in
  the downloader report, not fixed here.
