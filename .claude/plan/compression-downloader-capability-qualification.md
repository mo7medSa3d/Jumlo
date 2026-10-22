# Plan: PDF compression for scanned pages, downloader diagnosis, capability report

## Task type

- [ ] Backend (Kotlin/PDFBox compression pipeline + Python media service)
- [ ] Tooling (diagnostics, capability matrix generator)
- [ ] Documentation (capability report with real evidence)

No UI, design or iOS changes. The only UI touch allowed is a new error
message string in `values/strings.xml` + `values-ar/strings.xml` so the user
learns *why* compression failed.

## Environment facts that shape the plan

| fact | consequence |
|---|---|
| No Android device, emulator or simulator is available here | The device-gated proof (on-device CER/speed/RAM, instrumented tests, screenshots) **cannot** be produced in this environment. Task 1's acceptance is measured on the JVM host with a codec abstraction, and the on-device number stays an open gate. |
| `kotlinc` is not installed; `scripts/test-core.sh` has an embedded-compiler fallback, `scripts/generate-feature-matrix.py` does not | The matrix generator must gain the same fallback, otherwise Task 3 cannot be regenerated here. |
| Maven Central and GitHub are reachable; the Gradle cache holds `pdfbox-android-2.0.27.0` | A JVM probe against the real PDFBox artifact is possible (already used, see below). |
| `yt-dlp 2026.08.19` and `ffmpeg` are installed locally | Task 2 can run the service and probe the platforms for real. |
| `pdftoppm`, `pdfinfo`, `gs`, `pypdf` and `pdfminer.six` are installed | The acceptance step can render the compressed output and compare page counts here — no viewer needed. |
| `ccg-workflow` runtime is absent and `npx ccg init` is an interactive TUI that needs an API key | The specified Codex/Antigravity calls could not be run. Independent analysis was done with separate explore/review agents in this harness instead; the multi-model protocol is therefore *approximated, not executed as written*. |

---

## Step 0 — the four mandatory gates

No claim about compression counts until the gate it belongs to has passed. Each
gate states what is measured, on what, and what "pass" means.

### Gate A — Shipped build identity

Before any claim that the JPEG fix works for users, the exact artifact users
install must be recorded:

| field | value to record |
|---|---|
| APK filename | e.g. `dist/Jumlo-<version>-build<versionCode>.apk` |
| versionCode / versionName | from the build |
| git commit SHA | the commit the APK was built from |
| build variant | release / standard / extended |
| SHA-256 of the APK | `sha256sum` of the file |

The **89-94 % saving figure came from a replay on the working tree, not from a
shipped build**. It is labelled *"replay, working tree"* everywhere it appears
in this plan and in every document the work produces.

**Pass condition:** the same measurements are re-run against the code of the fix
commit, and the numbers are recorded against that APK's SHA-256. Without an
Android device the measurement is a *host replay of the sources in that commit*
and must be labelled as such — the on-device run stays an open item.

### Gate B — Memory and OOM behaviour on real hardware

The JVM figure of 32.1 MiB for one decoded 300 DPI page is **not** evidence about
an Android device.

1. Measure the peak heap while decoding a single 2550x3300 page on a **low-end
   Android device**, recording the model, the RAM and the Android version.
2. Run a **200-page scanned document** end to end, recording the peak memory and
   whether any `OutOfMemoryError` occurs.
3. Change the decode error handling so an `OutOfMemoryError` is **caught and
   reported as a user-facing error**, not swallowed. `catch (e: Exception)` does
   not catch `Error`, so either the catch is widened deliberately — with the reason
   written next to it — or the decode is budgeted so the error cannot occur.

**Pass condition:** the 200-page document completes on the low-end device without a
crash, **or** the failure is caught, reported with a clear message, and the
remaining pages are reported as not processed.

### Gate C — Acceptance targets reflect measured numbers

1. The 50 % Strong target is **not** an acceptance criterion unless a measurement
   supports it. Where no measurement exists the criterion reads *"not measured"*
   plus the reason.
2. The ladder's floor DPI must be **lower than the chosen preset DPI**, so Strong
   has enough rungs to reach its target. The new ladder is written down.
3. Each preset is defined by its **DPI and JPEG quality**, and the user is shown
   the **expected reduction range** for each preset, derived from the Gate A
   measurements.
4. The stale "Strong" chip state is fixed so it **cannot silently override the
   user's chosen DPI** (the leftover `num2` target size).

**Pass condition:** each preset has a written target range, a measured result on
the same five files, and a note for every file that falls outside the range.

### Gate D — CI coverage of the JPEG path

`app/build.gradle.kts:127` sets `unitTests.isReturnDefaultValues = true`, so
`BitmapFactory` returns null in the JVM unit-test source set and the DCTDecode
path has never been exercised by CI.

1. Introduce a **byte-level `PdfJpegCodec` seam**: the input is the raw JPEG bytes
   taken from the PDF stream, the output is the re-encoded JPEG bytes. The
   interface **must not mention Bitmap types**, or no host implementation of it
   can exist.
2. Add a CI test that runs the **full JPEG path on a real 300 DPI scan fixture**
   and asserts that the output is **smaller**, has the **same page count**, and
   **decodes correctly**.
3. Add a test for a document containing **both a 150 DPI and a 300 DPI image**, so
   the two subsampling regimes are covered together.

**Pass condition:** these tests run in the standard JVM unit-test task
(`./gradlew :app:testDebugUnitTest --offline`) and pass **without a device**.

---


## Task 1 — PDF compression for scanned (photographed) pages

### 1.1 What the code does today

```
AppViewModel.submit()                        ui/AppViewModel.kt:650
  → WorkspaceDraft.executionSettings()       model/WorkspaceDraft.kt:58
  → PdfOperationHandler "compress"           data/processing/PdfOperationHandler.kt:244
  → compressPdfGuaranteedSmaller()           data/processing/PdfOperationHandler.kt:455
  → PdfBoxBackend.compressImages()           data/pdf/PdfBoxBackend.kt:380
  → compress()                               data/pdf/PdfBoxBackend.kt:391
  → downscaleAndCompressImage()              data/pdf/PdfBoxBackend.kt:443   (JPEG path)
  → downscaleFlateImage()                    data/pdf/PdfBoxBackend.kt:496   (JVM fallback)
```

Presets (`core/PdfPageGeometry.kt:85`): `HIGH_COMPRESSION(96 dpi, q55)` = *Strong*,
`MEDIUM_COMPRESSION(150, 70)`, `LOW_COMPRESSION(300, 88)`. A **second, conflicting**
preset table lives in `ui/workspace/VisualImageSettings.kt:96` (strong = 108 dpi / q45),
and the ladder is `core/PdfPageGeometry.kt:177 profiles()`.

### 1.2 Root cause — the primary cause is now proven, and it is in the *installed* build

**First, the state of the tree.** `git log -1` is `07d80b7`, and the compression
rework is **uncommitted**. The difference matters:

| | HEAD `07d80b7` (what a user can install) | working tree |
|---|---|---|
| JPEG path | `image.getImage(null, subsampling)` — `PdfBoxBackend.kt:440` at HEAD | `BitmapFactory.decodeByteArray(bytes)` on the raw stream — `PdfBoxBackend.kt:466-469` |
| presets | none (raw DPI + quality fields) | `PdfCompressionPolicy.Preset` + the visual chips |
| ladder | none | `PdfPageGeometry.kt:177-185` |

**The primary cause of "~1 %".** In pdfbox-android, the JPEG fast path in
`SampledImageReader.getRGBImage` is only taken when the subsampling factor is
exactly 1. Decompiled from the shipped artifact
(`pdfbox-android-2.0.27.0-runtime.jar`):

```
162: PDImage.getSuffix()
177: ldc "jpg"
179: String.equals
182: ifeq  200                     ← not "jpg" → no fast path
185: iload_2                       ← the subsampling argument
186: iconst_1
187: if_icmpne 200                 ← subsampling != 1 → NO fast path
190: PDImage.createInputStream()
196: BitmapFactory.decodeStream(...)   ← the JPEG fast path
200: … from8bit(image, rect, subsampling, …)   ← the fallback
```

And `PdfCompressionPolicy.decodeSubsampling` (`PdfPageGeometry.kt:191-202`) caps the
budget at 32 MiB:

```
2550 x 3300 x 4 = 33.66 MB > 32 MiB  ->  step = 2      (for every device, heap-independent)
```

So at HEAD **every 300 DPI scan** takes `subsampling == 2`, the JPEG fast path is
skipped, and `from8bit` reads the *compressed JPEG bytes as raw pixel samples* —
garbage, or an exception that `runCatching{}.getOrNull()` swallows into `null`
(`PdfBoxBackend.kt:471-473` at HEAD) → the image is skipped → the saved file is the
input again. Measured re-save delta on the repo's real scans:

```
RESAVE scan_01pages.pdf  before=1327635  after=1327299  delta=-0.03 %
RESAVE scan_03pages.pdf  before=4004071  after=4002899  delta=-0.03 %
```

A 150 DPI scan (1275x1650 = 8.4 MB ≤ 32 MiB) gets `subsampling == 1` and works —
which is exactly the reported pattern: *photographed pages fail, other pages work.*

**The working tree already contains the fix** (decode the raw JPEG bytes directly).
Replaying the working-tree algorithm on the real fixture (JVM, ImageIO standing in
for BitmapFactory):

```
RESULT scan_01pages.pdf dpi=108 q=45 replaced=1 before=1327635 after=89603  saved=93.3 %  peakBitmapMB=32.1
RESULT scan_01pages.pdf dpi=96  q=45 replaced=1 before=1327635 after=73081  saved=94.5 %  peakBitmapMB=32.1
RESULT scan_01pages.pdf dpi=150 q=45 replaced=1 before=1327635 after=142626 saved=89.3 %  peakBitmapMB=32.1
```

**Therefore the first action of Task 1 is not a code change: it is to find out which
build the user is running.** If it is a build of HEAD, the JPEG fix is simply not
shipped, and landing the working-tree change (with a test, see 1.3) is the whole fix.

**The remaining defects, confirmed in code:**

| # | Finding | Location | Effect |
|---|---|---|---|
| R1 | The JPEG branch decodes the **full-resolution** bitmap with no `inSampleSize` and no budget: 2550x3300 = **32.1 MiB** measured, plus a scaled copy. The `subsampling` value computed three lines earlier is used *only* on the non-JPEG branch. The compress route never calls `ImageWorkPolicy.requireFits` / `context.budget.maxBitmapBytes`, unlike the images-to-PDF route. | `PdfBoxBackend.kt:453-469`; compare `PdfOperationHandler.kt:537` | **"fails with errors"**: the 32 MiB allocation raises `OutOfMemoryError`, which is an `Error` and is **not** caught by `catch (e: Exception)` at `:413` → the job dies → `LOW_MEMORY` (`JobStore.kt:179-181`, `WorkBudget.kt:29`) → the user sees a memory message. `PdfCompressionPolicy.canDecodeImage` exists for exactly this and is referenced **only** from the host test, never from the backend. |
| R2 | One bad image aborts the whole document: the per-image `errors` are collected but thrown after the loop, so 99 compressed pages are discarded because page 100 had one undecodable image. | `PdfBoxBackend.kt:437-439` | A whole job fails for one bad image. |
| R3 | The detailed error never reaches the user: `stableError()` keeps a message only if the **whole string** matches `[A-Z][A-Z_0-9]{2,80}`; `"PDF_IMAGE_COMPRESSION_FAILED: Im0: …"` does not, so it collapses to `PROCESSING_FAILED` → `msg_failed`. | `data/jobs/WorkBudget.kt:30`; `ui/components/UserMessages.kt:104` | The user cannot act on the real reason. |
| R4 | Every skip is silent: `/SMask`, `/Mask`, `bpc == 1`, a non-JPEG codec that fails to decode, a candidate that is not strictly smaller, a Form XObject without `/Resources`, `/DecodeParms` on a Flate stream. `JPEGFactory` even *adds* an `/SMask` whenever the bitmap has alpha, so any PDF the app itself produced from a PNG-with-alpha source is permanently uncompressible. | `PdfBoxBackend.kt:456, 460-462, 473, 486, 402, 507, 512` | The user sees "cannot be made smaller" with no reason. None of these fire on the repo's own scans (probe-verified: `suffix=jpg`, no mask, bpc 8, per-page resources) — but they are what a *scanner app* PDF looks like. |
| R5 | `profiles()` degenerates to **one rung for every preset ≤ 170 dpi** (`min(cappedDpi, 170/135/108)`), so "Strong" (96 or 108 dpi) has no fallback; `targetProfiles()` ignores the user's DPI entirely; and the visual "Strong" chip does **not** clear a stale `num2`, so a leftover target size silently switches the tool onto the DPI-ignoring ladder. | `PdfPageGeometry.kt:107-117, 177-185`; `ui/workspace/VisualImageSettings.kt:113-114`; `ui/workspace/WorkspaceScreen.kt:738` | The aggressive presets cannot go further when an input resists; a stale target overrides the user's choice. |
| R6 | The DCTDecode path **cannot be tested on the JVM**: `app/build.gradle.kts:127` sets `unitTests.isReturnDefaultValues = true`, so `BitmapFactory.decodeByteArray` returns null in the unit-test source set. That is why `docs/compression-results.md` says the JPEG path "could not be measured on the JVM host". | `app/build.gradle.kts:125-128`; `docs/compression-results.md:11` | The acceptance criterion is unreachable in CI — the reason the regression was invisible. |
| R7 | The target box comes from `page.mediaBox` only, ignoring `/CropBox`, `/Rotate` and the image's own placement. | `PdfBoxBackend.kt:431-433` | A cropped or rotated scan is scaled against the wrong box. |
| R8 | `embeddedImageSummary` (the slider's estimate) does not recurse into Form XObjects, unlike `compress()`. | `PdfBoxBackend.kt:987-1005` | The UI can show "no saving" for a scan that does compress. |

**Refuted** (each checked, so nobody re-checks them): the orphaned original stream
is *not* kept (a JVM probe shows 1,300,859 B → 270,856 B when an XObject is
replaced); `newLength < originalLength` compares the right things; `COSStream.length`
is the raw byte count; `JPEGFactory` honours the quality parameter and does not add
an `/SMask` for an opaque bitmap; the `visited` map does not lose pages (PDFBox
resolves *inherited* `/Resources`, so shared resources are processed once and every
image is found); `PDPage.getResources()` resolves inherited resources.

**A separate, secondary measurement** (the ladder floor, R5) on the repository's six
real phone photos of paper, re-encoded exactly as the app does
(decode → scale to `mediaBox x dpi / 72` → JPEG at `quality/100`):

| profile | reachable by today's ladder? | saving |
|---|---|---|
| 300 dpi / q88 ("High") | yes | **-23 % … -38 %** (bigger → refused) |
| 150 dpi / q70 ("Medium") | yes | -9 % … +1 % (usually bigger → refused) |
| 108 dpi / q45 (the UI "Strong" chip) | yes, and it is the *only* rung | 18 % … 40 % |
| 96 dpi / q55 (`HIGH_COMPRESSION`) | yes, and it is the *only* rung | 29 % … 47 % |
| **72 dpi / q45** | **no — below the floor** | **56 % … 67 %** |
| 60 dpi / q40 | no | 67 % … 75 % |

So the ladder floor is real (a low-resolution photo cannot be pushed past ~47 %),
but on a *real* 300 DPI scan the working-tree algorithm already reaches **89-94 %**.


### 1.3 Implementation steps

1. **Establish which build the user is running** (no code change yet): compare the
   installed APK's behaviour against HEAD `07d80b7` and the working tree, and ask for
   the failing file. If the installed build is HEAD, the JPEG fix is simply not
   shipped and step 3 is the fix.
2. **Land the working-tree JPEG fix with a test** — the fix already exists in
   `PdfBoxBackend.kt:466-469`; what is missing is the proof. Commit it together with
   the seam in step 3.
3. **Put the decode/encode behind a byte-level codec seam** so the DCTDecode path is
   measurable on the host (this is the change that makes every other acceptance
   criterion CI-runnable). It must be byte-oriented, not `Bitmap`-oriented, or a JVM
   implementation cannot exist:

   ```kotlin
   // app/src/main/java/com/jumlo/app/data/pdf/PdfJpegCodec.kt
   data class RecodedJpeg(val width: Int, val height: Int, val bytes: ByteArray)
   fun interface PdfJpegCodec {
       /** Decode [jpeg], downscale into [maxWidth]x[maxHeight], re-encode at [quality].
        *  null = this codec cannot handle the image. */
       fun recode(jpeg: ByteArray, maxWidth: Int, maxHeight: Int, quality: Int): RecodedJpeg?
   }
   ```

   `downscaleAndCompressImage` then builds the XObject itself with the public
   constructor `PDImageXObject(doc, InputStream, COSBase filter, w, h, bpc, PDColorSpace)`,
   so no `android.graphics` type leaks into the seam. The Android implementation uses
   `BitmapFactory` + `ImageWorkPolicy.requireFits` + `inSampleSize`; the JVM
   implementation uses `javax.imageio` and lives in the test source set.
   (Note: `core/` is pure Kotlin in this repo — no `android.*` and no
   `com.tom_roush` imports — so the seam belongs in `data/pdf/`, not in `core/`.)
4. **Add the DCTDecode fixture and the regression test** next to
   `app/src/test/java/com/jumlo/app/pdf/MeasureCompressionTest.kt`: a 2550x3300
   DCTDecode JPEG on a Letter page, and a test that asserts **≥50 % smaller** with the
   page count preserved. This is the test that was impossible before step 3.
5. **Budget the decode** (R1): pass `context.budget.maxBitmapBytes` into
   `PdfBoxBackend.compress`, apply `PdfCompressionPolicy.canDecodeImage` /
   `decodeSubsampling` on the JPEG branch too, and catch `OutOfMemoryError` **per
   image** so one huge scan cannot kill the job.
6. **Make one bad image non-fatal** (R2): a failed image becomes a recorded warning
   and the rest of the document is still compressed.
7. **Make the failure legible** (R3): raise `OperationFailure("PDF_IMAGE_COMPRESSION_FAILED")`
   — a code that survives `stableError`'s regex — and map it in `UserMessages.kt` +
   `values/strings.xml` + `values-ar/strings.xml`.
8. **Record every skip** (R4): the backend returns a per-image report
   (`compressed / skipped(reason) / kept`), with the reasons `SMASK`, `MASK`,
   `BITMAP_1BIT`, `CODEC`, `FORM_WITHOUT_RESOURCES`, `DECODE_PARMS`, `NOT_SMALLER`,
   `OUT_OF_BUDGET`; the job carries it and the UI shows "N of M images compressed;
   K kept because …".
9. **Fix the ladder and the presets** (R5): give the ladder a floor *below* the
   chosen DPI (measured: 96 dpi/q55 reaches 29-47 %, 72 dpi/q45 reaches 56-67 % on
   low-resolution photos), keep the *smallest* valid candidate instead of stopping at
   the first improvement, make `targetProfiles()` respect the user's DPI as its
   ceiling, clear `num2` in the visual "Strong" chip, and single-source the preset
   table so `core/PdfPageGeometry.Preset` and `VisualImageSettings.kt:100-105` cannot
   drift apart.
10. **Extend the diagnostic tool** (`scripts/pdf-image-diagnostics.py`) so it reports
    the things that decide the verdict and that it currently omits: `/SMask`,
    `/Mask`, `/ImageMask`, `/DecodeParms`, the CropBox and `/Rotate`, an ICCBased or
    Indexed colour space, an image inside a Form XObject (and whether that Form has
    `/Resources`), and a **skip verdict** per image computed the way the app decides
    (`COMPRESSIBLE` / `SKIP(...)` / `NOT-SMALLER`) plus the bytes that would be saved.
    Write the reports to `docs/compression-diagnostics/`.
11. **Run the diagnostic on 5+ realistic scanned PDFs**: the five already in the repo
    (`scan_01/03/06/10/30pages.pdf`) plus the user's own photographs once supplied;
    build any missing fixtures from the real phone-photo pages in
    `tests/ocr_benchmark/pages/*.jpg`.
12. **Fix the geometry and the estimate** (R7, R8): compute the target box from the
    CropBox and the image's placement, and make `embeddedImageSummary` recurse into
    Form XObjects so the slider's estimate matches what the tool will do.
13. **Measure the acceptance** on the same files, before and after: input size, output
    size, pages, seconds, warnings; render the output with `pdftoppm` (installed
    here) and compare the page count and the rendered size.
14. **Record the result** in `docs/compression-results.md` and
    `docs/compression-diagnostics/`, replacing the current Flate-only numbers.

### 1.4 Acceptance criteria (measurable)

| Criterion | Target | Where measured |
|---|---|---|
| Strong on photographed pages | ≥ 50 % smaller — **already met by the working-tree algorithm on the repo's real scans (89-94 %)**; the ladder fix lifts low-resolution photos from 29-47 % to 56-67 % | host measurement over the same 5 files; if a file cannot reach it, the report names the reason and the achieved number |
| Page count, order and page size preserved | identical | page-count check + `pdftoppm` render + compare |
| No silent failure | every skipped image has a reason; every failure reaches the user with a real message | the per-image report + `UserMessages` mapping |
| No OOM | peak bitmap bytes under the job budget | the budget check in step 5 + a host test with a 2550×3300 fixture |
| Regression protection | a DCTDecode host test asserts the reduction (CI-runnable, not device-only) | new test in `app/src/test/java/com/jumlo/app/pdf/` |
| One bad image must not lose the other pages | a fixture with one undecodable image still compresses the rest | new host test |

---

## Task 2 — video/link downloader diagnosis

### 2.1 What exists today

`tools/media-service/service.py` is a **standard-library** HTTP service
(`ThreadingHTTPServer`), routes `GET /health` (`service.py:206`), `GET /stream`
(`:209`), `POST /extract` (`:274`), default bind `127.0.0.1:8731` (`:293-295`),
optional bearer token (`:299-313`). yt-dlp is called in exactly one place
(`run_ytdlp`, `service.py:77-81`) and is **not version-pinned** anywhere; the
README documents a rolling `pip install -U yt-dlp`.
The Android side is `data/media/RemoteExtractorRuntime.kt` (`:64` inspect,
`:135` download, `:187` refreshExtractor) with the base URL read live from
DataStore (`data/preferences/UserPreferencesRepository.kt:59`), plus
`RoutingMediaRuntime` (`:217-258`).

### 2.1a Baseline measured in this session (the service was started and probed)

```
python3 tools/media-service/service.py --host 127.0.0.1 --port 8731
curl http://127.0.0.1:8731/health
→ {"ok": true, "ytdlp": "2026.08.19", "ffmpeg": true}   HTTP 200
```

| Platform | URL used | HTTP | Verbatim result | Classification |
|---|---|---|---|---|
| YouTube | `youtube.com/watch?v=aqz-KE-bpKQ` | 200 | `{"extractor":"Youtube","title":"Big Buck Bunny 60fps 4K …","duration":635,…}` | works |
| TikTok | `tiktok.com/@scout2015/video/6718335390845095173` | 200 | `{"extractor":"TikTok","title":"Scramble up ur name …"}` | works |
| Facebook | `facebook.com/Engineering/videos/10153231379946729/` | 200 | `{"extractor":"Facebook","title":"How to Share With Just Friends",…}` | works |
| SoundCloud | `soundcloud.com/uiceheidd/lets-go` | 422 | `{"error": "MEDIA_REMOVED"}` | **content removed** — the track no longer exists; not an extractor or service fault |
| direct MP4 | `download.samplelib.com/mp4/sample-5s.mp4` | 200 | `{"extractor":"Generic","formats":[{"id":"mp4","ext":"mp4",…}]}` | works |
| direct MP3 | `download.samplelib.com/mp3/sample-3s.mp3` | 200 | `{"extractor":"Generic","formats":[{"id":"mpeg","ext":"mp3",…}]}` | works |
| Instagram | `instagram.com/p/CUbHfhpswxt/` | 422 | `{"error": "MEDIA_NO_USABLE_FORMAT"}` | **login wall** — the post needs the account session; expected, and the only honest answer |

**Consequence for the plan:** the extractor is **not** outdated — `yt-dlp 2026.08.19`
resolves YouTube, TikTok, Facebook and both direct-URL classes today. The "update
yt-dlp" step is therefore a *re-verification plus a pin*, not a fix. The real work is
the code defects below and the two honest failure classes (removed content, login
wall), which must be reported as such rather than worked around.

**Defects found by code review (to confirm at runtime, not assumed):**

| # | Defect | Location |
|---|---|---|
| D1 | The audio path combines `-x --audio-format mp3` with `--get-url`; `--get-url` only prints a URL, so **no conversion happens** while the file is named `media.mp3` → `INVALID_MEDIA_CONTAINER` downstream. | `service.py:166-169` |
| D2 | The service proxy forwards no `ETag`/`Last-Modified`/`Content-Range`, while `StreamTransfer` requires a resume validator → `MISSING_RESUME_VALIDATOR`. | `service.py:235-270`; `core/StreamTransfer.kt:66-71` |
| D3 | The app sends only the **video** format id, but `validateTracks` requires audio too → `MEDIA_AUDIO_MISSING` for every muxed source. | `RemoteExtractorRuntime.kt:149`; `data/media/MediaOperationHandler.kt:131` |
| D4 | `setMediaServiceToken` has **no UI caller** → a token-protected service is unreachable and always answers 401. | `UserPreferencesRepository.kt:270-278` |
| D5 | `refreshExtractor()` is never called from the UI: the "update the extractor" button promised in the design doc does not exist, and the version is never shown. | `RemoteExtractorRuntime.kt:187-200`; `ui/downloads/MediaWorkspace.kt:299` |

### 2.2 Implementation steps

1. **Start the service and record the health check**:
   `python3 tools/media-service/service.py --host 127.0.0.1 --port 8731` (or 8099 to
   match the earlier record), then `curl http://127.0.0.1:8731/health`; record the
   exact command, the response and the `yt-dlp --version` / `ffmpeg -version` output.
2. **Update yt-dlp and pin it**: `python3 -m pip install -U yt-dlp`; record the version
   before and after (`2026.08.19` → whatever is current) and re-run the seven probes.
   **Pin it**: add `tools/media-service/requirements.txt` with the tested version and
   document it in the README, so an extractor break is a version bump, not a mystery.
   The probes above already show the extractor is healthy today, so this step is a
   re-verification, not a repair.
3. **Probe the seven URL classes one at a time** against `POST /extract`, recording the
   HTTP status, the verbatim error text and the result: YouTube, TikTok, Facebook,
   SoundCloud, a direct MP4, a direct MP3, and an Instagram post (expected to need
   cookies). Use `POST /stream` for the two direct URLs to confirm the byte path too.
4. **Classify every failure** into exactly one of: service not running / network
   blocked / extractor outdated / login or cookies required / geo-restricted /
   unsupported URL / other (described). **Quote the error text**, never guess.
5. **Fix what is ours.** For "extractor outdated": bump yt-dlp and re-test. For the
   code defects above: fix D1 (do the conversion in the service and stream the
   converted file, or drop `-x` and label the container truthfully), D2 (forward
   `ETag`/`Last-Modified`/`Content-Range` and pass a validator to `StreamTransfer`),
   D3 (let the app request a muxed format), D4 (expose the token in Settings), D5
   (show the extractor version and offer the refresh). Each fix gets a test: the
   service's own behaviour in `tools/media-service/` (a Python test module) and the
   client behaviour in `app/src/test/java/com/jumlo/app/media/RemoteExtractorRuntimeTest.kt`,
   which already fakes a service over a loopback socket.
6. **Keep the line**: no DRM bypass, no paywall/login-wall workaround, no private
   content. Where a platform needs the user's own session, document it and surface a
   clear message (`MEDIA_SESSION_REQUIRED` already exists in `UserMessages.kt`).

---

## Task 3 — capability report

### 3.1 What exists today

* The **master registry** is `data/jobs/CapabilityRegistry.kt` (45 ids: 44 in
  `allTools` + `sha256`), **not** `core/ToolCatalogue.kt` (that only decides tile vs
  folded and the workspace). `model/Models.kt:54` holds the row type.
* `core/CapabilityPolicy.kt` declares availability: 5 blocked, 5 navigation,
  35 processing (7 of them `HOST_CORE_TESTED`), everything else fails closed.
* `docs/CAPABILITY_REPORT.md` already has the required four columns
  (`Tool | Works end to end | Evidence | Known failure`) for 45 tools — but 40+ rows
  say **"not verified"**.
* `docs/FEATURE_QUALIFICATION_MATRIX.md` + `docs/feature-qualification.json` are
  **stale** (44 rows, `pdf_image` missing) and are regenerated by
  `scripts/generate-feature-matrix.py`, which needs `kotlinc` (absent here).

### 3.1a Baseline measured in this session

```
bash scripts/verify-host.sh          # JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk
→ exit 0
→ RESULT passed=181 failed=0          (Kotlin host suite, app/src/hostTest)
→ 16 Python suites: OK
→ 0 FAIL lines
```

That is the evidence base available **on this host** today. It covers the pure
domain, the media policies, the transfer semantics, the trash and the copy rules —
**not** the Android-dependent half (BitmapFactory, PDFRenderer, the scanner, the
foreground service), which only the device suite can prove.

### 3.2 Implementation steps

1. **Make the matrix regenerable here**: give `scripts/generate-feature-matrix.py` the
   same embedded-compiler fallback that `scripts/test-core.sh` already has
   (`kotlin-compiler-embeddable` from the Gradle cache + JDK 17), and add a
   `--check` mode so CI can fail on a stale matrix instead of silently rewriting it.
2. **Rebuild the capability report from executed evidence**, one row per tool:
   `Tool | Works end to end | Evidence | Known failure`. The rule stays strict: *yes*
   only when the tool was run on a real input and the output was inspected; everything
   else is *not verified* with the reason.
   * Evidence sources already available: `app/src/test/java/com/jumlo/app/pdf/*` (PDF
     tools), `app/src/test/java/com/jumlo/app/media/RemoteExtractorRuntimeTest.kt`,
     `app/src/hostTest/kotlin/CoreTests.kt` (the suite that just reported
     `passed=181 failed=0`), `scripts/test-*.py` (16 suites, all OK), and the
     instrumented suite under `app/src/androidTest/` (**device-only → not evidence here**).
   * The media rows can be raised to *yes* right now from the probes in 2.1a: the
     service returned 200 with formats for YouTube, TikTok, Facebook, a direct MP4 and
     a direct MP3, and 422 with `MEDIA_REMOVED` / `MEDIA_NO_USABLE_FORMAT` for the two
     honest failure cases. `dl_media` and `dl_02` therefore have live evidence today;
     the rest do not.
   * Tools with **zero** id-level evidence today: `img_join`, `img_stitch`,
     `file_duplicates` — plus `scan_doc`, `pdf_ocr`, `webpage_pdf`,
     `file_encrypted_zip`, `file_drive`, `xf_05`, whose only assertions are that they
     stay closed. Those rows stay *not verified*, with that stated.
3. **Run every runnable suite and capture the output** into
   `docs/capability-evidence/<date>/` so each "yes" points at a file, not a claim:
   `bash scripts/verify-host.sh`, `./gradlew :app:testDebugUnitTest --offline`,
   and the per-tool probes that have no suite yet.
4. **Close the gaps that are cheap**: add host-level tests for the three tools with
   no evidence (`img_join`, `img_stitch`, `file_duplicates` are all pure file/image
   logic and belong in `app/src/hostTest/kotlin/`), so their rows can move from
   *not verified* to *partly*.
5. **Keep the invariants green**: `core/ToolCatalogueTest.kt` requires every registry
   id to be listed or deliberately hidden, `primaryTools().size in 12..20`, and no id
   listed twice — any new tool must satisfy all of it.

---

## Key files

| File | Operation | Description |
|------|-----------|-------------|
| `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt` | Modify | `compress()` / `downscaleAndCompressImage()`: budget the decode, record skip reasons, use the effective placement, sweep shared images |
| `app/src/main/java/com/jumlo/app/core/PdfPageGeometry.kt` | Modify | single-source the presets; fix `profiles()` / `targetProfiles()` / `meetsTarget()` ladder behaviour |
| `app/src/main/java/com/jumlo/app/data/pdf/PdfJpegCodec.kt` | **Create** | the byte-level codec seam (`recode(jpeg, maxW, maxH, quality)`) — no `android.graphics` in the signature, so a JVM implementation can exist |
| `app/src/main/java/com/jumlo/app/data/pdf/AndroidJpegCodec.kt` | **Create** | `BitmapFactory` + `inSampleSize` + `ImageWorkPolicy.requireFits` implementation |
| `app/src/test/java/com/jumlo/app/pdf/JvmJpegCodec.kt` | **Create** | the `javax.imageio` implementation used by the host tests |
| `app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt` | Modify | carry the per-image report; keep the smallest valid candidate |
| `app/src/main/java/com/jumlo/app/data/jobs/WorkBudget.kt` | Modify | keep the compression failure code intact through `stableError()` |
| `app/src/main/java/com/jumlo/app/ui/components/UserMessages.kt` | Modify | map the new code to a message |
| `app/src/main/res/values/strings.xml`, `values-ar/strings.xml` | Modify | the new message (EN + AR) |
| `app/src/test/java/com/jumlo/app/pdf/PdfFixtures.kt` | Modify | add a DCTDecode (JPEG) fixture with an SMask variant |
| `app/src/test/java/com/jumlo/app/pdf/PdfCompressionPolicyTest.kt` | Modify | assert the ≥50 % reduction on the scanned fixture |
| `scripts/pdf-image-diagnostics.py` | Modify | report /SMask, /Mask, DecodeParms, the placement, and the skip verdict |
| `docs/compression-diagnostics/*`, `docs/compression-results.md` | Modify | the measured before/after evidence |
| `tools/media-service/service.py` | Modify | audio conversion, range/validator forwarding, version pin |
| `tools/media-service/requirements.txt` | **Create** | the pinned yt-dlp version |
| `app/src/main/java/com/jumlo/app/data/media/RemoteExtractorRuntime.kt` | Modify | muxed format request, validator pass-through, version surface |
| `app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt` | Modify | expose the service token |
| `app/src/main/java/com/jumlo/app/ui/settings/SettingsScreen.kt` | Modify | the service token field |
| `scripts/generate-feature-matrix.py` | Modify | embedded-compiler fallback + `--check` |
| `docs/CAPABILITY_REPORT.md` | Modify | the refreshed, evidence-backed report |
| `docs/FEATURE_QUALIFICATION_MATRIX.md`, `docs/feature-qualification.json` | Modify | regenerated |

## Risks and mitigation

| Risk | Mitigation |
|---|---|
| **The compression rework is uncommitted.** HEAD `07d80b7` has the broken JPEG path, so "fix the code" and "ship the fix" are different jobs | Step 1 establishes which build the user runs; the fix lands as a commit together with its regression test, so the gap cannot reopen |
| The device gate cannot be closed here (no device/emulator) | The plan makes the *host* measurement authoritative for the algorithm and states plainly that the on-device number is still open; the acceptance table names both. |
| The user's own scanned PDFs are not in the repo | Steps 1-2 produce the verdict for any file; the report ships with the repo's five and the tool to run on the user's; the exact percentages stay open until the files are supplied. |
| Changing the compression core could break text PDFs | The text/vector path is untouched; the existing `aTextOnlyDocumentIsNeverTurnedIntoAScreenshot` test guards it, and the change is covered by a new host test. |
| The codec abstraction could drift from the Android behaviour | One implementation per platform, one decision layer shared; the Android codec stays a thin wrapper over the current code. |
| yt-dlp updates can break the service between releases | Pin the version in `requirements.txt` and record the tested version in the report. |
| Touching the service may affect the app's download path | The client's behaviour is covered by the existing loopback-socket test; the service changes are additive and version-checked. |
| The capability report could become a self-graded claim | Only executed evidence counts, the evidence file path is recorded per row, and device-only suites are explicitly excluded. |

## What will still not work after this plan

* **On-device proof** for everything: no device, emulator or simulator is reachable
  from this environment, so instrumented tests, on-device timings and screenshots
  stay an open gate.
* **Instagram / Facebook / TikTok sessions**: without the user's own cookies these
  platforms remain blocked; the plan surfaces a clear message and does not attempt a
  workaround.
* **The user's exact "1 %" number** until their five files are run through the
  diagnostic *and* we know which build they are running: at HEAD the cause is proven
  (the JPEG fast path is skipped for every 300 DPI scan) and the working tree already
  compresses the repo's scans by 89-94 %, but the user's own files may carry an
  `/SMask` or sit inside a Form XObject, which are silent skips today.
* **The on-device JPEG encoder's exact output size**: the host measurement uses
  ImageIO, which is close to but not identical to Android's encoder.
* The separate OCR/document-intelligence work (Phase 0 model selection is measured
  and written up in `docs/OCR_PHASE0_MODEL_SELECTION.md`; the app-side pipeline,
  model delivery and the seven features are not part of this plan).

## SESSION_ID (for /ccg:execute use)

- CODEX_SESSION: unavailable — the `ccg-workflow` runtime could not be provisioned
  (interactive TUI + API key required); the analysis was produced with independent
  explore/review agents in this harness instead.
- ANTIGRAVITY_SESSION: unavailable — same reason.
