# Jumlo 1.6-tools — verification report

Date: 2026-10-23. **The latest native dependency-closure, PDF recovery, inspection-deadline and contrast changes await final rebuilt-artifact and Android qualification results.** Earlier compatibility-stage builds/inspections are historical evidence, not current artifact identities. The initial API 26 run recorded 82 reported passes and eight failures; subsequent focused runs exposed additional concrete failures recorded below. Full tool, live-provider, physical-device and 16 KiB runtime qualification remain separate requirements.

## Scope and evidence rules

The application is rebuilt from the supplied source archive. Implementation, compilation, host execution, emulator execution and physical-device qualification are separate claims. A source guard or host FFmpeg run does not establish Android runtime behavior. An extractor metadata response does not establish a completed media download. The counts below overlap in places and must not be added into a unique-test total.

The original paste-crash stack trace was not supplied and the exact original crash has not been reproduced. Concrete clipboard/native-error boundaries, cancellation, state retention and durable URL ownership were corrected. The initial API 26 run separately reproduced a native-child linker failure caused by omitted compatibility libraries; the app/test process continued. Its packaging correction is documented below. The clipboard regression initially timed out waiting for retry UI, so neither its presence nor the native repair establishes that every pasted URL is crash-free.

## Current rebuild status

<!-- CURRENT_ARTIFACT_RESULTS_START -->

**Pending coordinator refresh after the final candidate rebuild.** The GLib/PCRE closure repair changes FFmpeg payload bytes and pins; the PDF, direct-inspection and contrast changes also postdate the completed artifact snapshot below. An intermediate debug/test/JVM success does not complete compact, AAB or lint qualification. Do not distribute or describe the historical hashes as the latest source build.

| Current source artifact/gate | Status |
|---|---|
| Compact APK identity and 60,000,000-byte gate | Pending final rebuild and inspection |
| Debug app and Android test APK identities | Pending final candidate rebuild after the reviewed PDF follow-up |
| Release AAB identity/signing inspection | Pending final rebuild and inspection |
| Packaging-only baseline comparison | Must use the same corrected native closure/pins as the candidate; final measurement pending |
| Lint and local JUnit snapshot | Last completed results below remain historical until the coordinator records the final build snapshot |

<!-- CURRENT_ARTIFACT_RESULTS_END -->

## Build identity and distribution

- App version: `1.6-tools`, version code `10844`; minSdk 26, compile/targetSdk 36.
- Toolchain: JDK 17, Gradle 8.14, Android Gradle Plugin 8.10.1, Kotlin 2.0.21, SDK/build-tools 36.
- Production architecture remains Compose/Material 3, typed Navigation, Hilt, Room, DataStore, Coroutines/Flow and WorkManager. The merged debug manifest was inspected for `dataSync` and both foreground-service permissions.
- Four Kotlin/JVM modules contain the extracted policies and algorithms. They have no Android imports but are not Kotlin Multiplatform/iOS binaries.
- `compact` uses full R8/resource shrinking and ARM64, signed with the local Android debug key for installation testing. It is not signed with a production release key.
- `release` enables ARM64, density/ABI/language bundle splits and English/Arabic resources. No release signing credentials were supplied. Play-delivered locale switching and store distribution are not qualified here.

### Historical compatibility-stage size comparison

The uploaded archive contained no APK. Building it unchanged failed the native repack checksum gate. The previously measured comparison baseline retains the original application sources and changes only packaging: `tools/repack-native-media.py`, the derived native manifest pins, and native-merge task input tracking in `app/build.gradle.kts`. It includes the same required Android support/glob compatibility libraries as the candidate at that stage and uses the same ARM64 compact variant with R8/resource shrinking. It is a packaging-repaired baseline, not a successful build of the untouched archive.

| Artifact | Exact bytes | Decimal MB | Qualification |
|---|---:|---:|---|
| Original application, packaging-only repair | 55,507,625 | 55.507625 | Built successfully with the same compatibility-library repair; not the unmodified uploaded archive |
| Previous compact APK | 54,678,142 | 54.678142 | Full runtime, ARM64, debug-signed; size gate passed |
| Previous debug APK | 119,596,693 | 119.596693 | ARM64/x86_64 development build; not the distribution-size target |
| Previous Android test APK | 1,234,214 | 1.234214 | Rebuilt instrumentation package; execution is recorded separately |
| Previous release AAB | 60,110,894 | 60.110894 | ARM64 upload container, unsigned; not an installed APK |

Baseline SHA-256: `3fe49ff3ca5e68e7d8b870068e058749a4797cf5955ccf6f88cb1e6f1b27232e`.

Previous SHA-256 identities:

- Compact APK: `1685cec87c4c462e084355363b8a6cba54295863d36bd302faf9b616502b24ea`.
- Debug APK: `3fa167e8c54ae2e5d2e4c4b35793bb3616453ddf56b305632f6cbd7a736ce4ca`.
- Android test APK: `3c4f882ecbaa1ee02b9cd616b4df7321c6fa018d9662052a4fad471e24e21e4b`.
- Release AAB: `1ef4e19dde3cd436cbfb3de3606eab52ec90eb91f5027757aa9ee635db04f5bb`.

That previous compact APK was **829,483 bytes (1.494359%) smaller** than the packaging-repaired baseline, with **5,321,858 bytes of headroom** below the 60,000,000-byte cap. Decimal MB means 1,000,000 bytes. The AAB exceeds that cap as an upload container; the compact APK is the configured distribution-size gate target. No installed/store-delivered size is inferred from the AAB.

Measurements and hashes were verified at `2026-10-23T02:24:39.223182+00:00` in `reports/final-artifacts.md` and `reports/final-artifacts.json`; all four files remained unchanged during inspection. The updated baseline is recorded in `reports/baseline-size.json` and `reports/baseline-compat-build.log`. Earlier artifact snapshots are preserved as `reports/pre-native-compat-final-artifacts.*` and are historical. The support/glob-stage `final-artifacts.*` snapshot described here is now also historical because it predates the GLib/PCRE closure repair; the authoritative new source pins are in `reports/native-closure-repair.md` and the checked-in manifest.

In that historical snapshot, native archives were the main size contributors: inside the compact APK, FFmpeg occupies **32,206,730 compressed bytes** and Python **12,244,175 compressed bytes**. Their archive lengths before outer APK compression are **32,373,388** and **12,481,633 bytes**, respectively; those are not standalone APK sizes. The complete grouped entry accounting, including ZIP/signing/alignment overhead, is in the artifact report.

Font files were measured separately: **773,168 → 353,508 bytes**, a saving of 419,660 bytes. Latin is 151,900 bytes; Arabic is 201,608 bytes. Subsetting preserves the selected Unicode ranges and shaping closure, pins width to 100 and restricts Latin weights to 400–700. Modified internal families were renamed and the OFL license is included.

## Last completed compatibility-stage build and lint gates

| Gate | Recorded result | Evidence |
|---|---|---|
| Compatibility-repaired candidate: debug app/test assembly, compact gate, release bundle and lint | `BUILD SUCCESSFUL` in **2m 23s**; 42 tasks executed, 161 up-to-date | `reports/candidate-compat-build.log` |
| Original-application baseline with the same packaging-only repair | `BUILD SUCCESSFUL` in **1m 13s**; compact gate completed | `reports/baseline-compat-build.log`, `reports/baseline-size.json` |
| Local JUnit snapshot reused by the compatibility rebuild | **408 tests: 406 passed, zero failures, zero errors, two skipped** across 70 XML files; `:app:testDebugUnitTest UP-TO-DATE` | `reports/candidate-junit-summary.json`, `reports/candidate-junit/`, compatibility build log |
| `:app:lintDebug` | Task completed; **zero errors, 568 warnings** | `reports/candidate-lint-summary.json`, `app/build/reports/lint-results-debug.xml` / `.html` |
| Previous artifact inspection/signature/alignment | Nine tool invocations, all exit zero; signing observations are qualified below | `reports/final-artifacts.json`, `reports/final-check-*.log` |

The compatibility rebuild reused the valid existing local JUnit outputs; it did not execute another 408 cases. All 70 saved XML files were reread and their totals match the summary. The two optional remote-service skips are described below. Rebuilt Android tests and changed packaged bytes still require their separate device execution record.

Earlier integration/build stages are retained as history: `candidate-build.log` (10m 24s), `candidate-packaging-build-2.log` (2m 4s) and `candidate-final-repackage.log` (19s). The first asset-isolation rerun failed on duplicate x86_64 assets before cleanup. Those artifacts preceded the subsequently discovered compatibility-library omission and are not the current distribution identities.

Lint is not warning-free: counts include 398 unused-resource, 63 UseKtx, 30 plural-candidate, 25 dependency-update and 21 usable-space warnings. Three `TrustAllX509TrustManager` findings point into the Bouncy Castle dependency, while one `InsecureBaseConfiguration` and one `Recycle` finding point to the app's network configuration and `SourceStore`. These warnings remain recorded for review; zero lint errors is not a security or resource-lifecycle clearance. Gradle also reports deprecated features requiring attention before an upgrade to Gradle 9.

## Executed host and source gates

| Gate | Observed result | What it establishes |
|---|---|---|
| Shared Kotlin host/core suite | 198 passed, 0 failed | Executed policy/transfer/archive/media fixtures on the host JVM |
| Focused downloader JVM suite | 66 passed, 0 failed, 0 skipped | Routing, real local socket cancellation/sample reads, metadata, resume and naming; overlaps the full JUnit suite |
| Original integrated Gradle JUnit run after rebuild | 408 discovered: 403 passed, 3 failed, 2 skipped | Failures and corrections described below; not the final green gate |
| Recorded clean-snapshot JUnit result | 408 discovered: **406 passed, 0 failures/errors, 2 skipped** | Same 70-file result reused by the compatibility rebuild, not an additional test total; two optional remote-service cases skipped |
| PDF compression host fixture suite | 18 assertions, all pass | Production recompression/downsampling path through a host image codec |
| Office package host suite | 11 checks passed after regression fix | Actual DOCX/PPTX packages, Unicode/paragraphs/relationships/images/budgets/cancellation |
| Independent Office readers | Generated files reopened with python-docx/python-pptx | Structural readability by independent libraries |
| LibreOffice application rendering | 4 positive host packages rendered into the expected 25 pages/slides | Actual headless Writer/Impress import/render; all 19 tool commands exited zero, input hashes unchanged; not Microsoft Office or Android conversion qualification |
| Existing Python source/config guards | 23/23 scripts; 153 unittest methods and 2 standalone scripts pass | Static contracts only; some media source guards overlap the next row |
| Downloader Python gate snapshot | 36 passed | Historical six-suite snapshot: 4 extractor/bootstrap, 4 network guard, 11 integration/source, 7 architecture, 5 actual host FFmpeg, 5 original ELF/ZIP methods; some source guards overlap the broader static suite |
| Native compatibility focused regression | 6 methods passed | Historical support/glob suite; included five previous methods and one new method with four subcases |
| Native dependency-closure regression | 15 methods passed | Current host packaging suite, including prior repack methods; exact-name/alias/transitive closure and all-retained-payload behavior, not 15 additional unique tests on top of previous Python totals; `reports/native-closure-green.log` |
| Live qualification runner | 11 offline tests passed | Input validation, quoting, output bounds, metadata handling and child-group cleanup; two descendant-leak regression subcases failed before the fix; `reports/live-media-cleanup-green.log` |
| UI policy host checks | 7 checks passed | Date grouping and preference/mode policy, not rendered Android UI |
| Durable secret/admission/new-tool regressions | Red failure reproduced, then host checks passed | Ownership/recreation through test adapter, cancellation accounting and tool wiring; Android Keystore is a separate device gate |

The three initial Gradle failures exposed a missing deliberate-hidden catalog entry (`webpage_pdf`) and two tests encoding superseded requirements: a pre-Office catalog limit and the removed HTTP consent control. The catalog now explicitly records the blocked entry. The primary cap remains bounded at 22 and asserts the requested Word/PowerPoint/OCR entries; restored network flags are checked against the new execution policy. The recorded clean-snapshot JUnit rerun passed all three corrected cases; the compatibility rebuild subsequently reused those outputs as up-to-date.

The two skipped `RemoteExtractorServiceLiveTest` cases are `theServiceReportsItsExtractorVersion` and `aDirectMediaLinkIsDownloadedToTheDevice`; `JUMLO_MEDIA_SERVICE` was absent. No backend was deployed for this delivery; the selected default is the on-device engine. Skips are not passes.

The host qualification runner's cleanup regression now covers a leader exiting while its child keeps pipes open and a timeout with a SIGTERM-ignoring child. Both leaked a continuing child heartbeat before the fix; the 11-test green run completed afterward. The fixed runner and test files were mirrored with identical hashes (`reports/live-media-cleanup-mirror.json`). This qualifies those host cleanup cases, not Android process management or live downloads.

The custom compression and Office runners report assertions/checks, not JUnit method counts; their classes are absent from the saved 70-file local JUnit snapshot. Earlier ten-check Office runs are superseded by the 11-check regression run. The source/config aggregate combines 22 passing scripts with its corrected six-method launch-script rerun. These records and focused reruns must not be summed into one unique-test total.

### PDF compression measurement

A deterministic **synthetic** image-heavy PDF measured **3,877,137 → 141,756 bytes** at 96 dpi/JPEG quality 55 (about 96.3% smaller). The suite checks page retention, JPEG recompression, Flate/Decode behavior, corrupt-image warnings, shared-image placement, cancellation and preservation of the original when a candidate grows. This single fixture is not a predicted saving for user PDFs. Text-heavy/already optimized PDFs may shrink little. The UI estimate is advisory; completed output metadata records actual bytes.

The existing deterministic corpus generator was also executed successfully with `scripts/test-pdf-compression.sh --measure` (`reports/pdf-corpus-generation.log`, exit zero, 55.5 seconds including generation). It creates 1/3/6/10/30-page synthetic scans at 2550×3300 pixels and reports **20 host measurements across four presets**. These measurements are not 20 new regression-test passes. For the 30-page, 116,340,967-byte input, it recorded:

| Host preset | Output bytes | Reported saving | Processing time on this host |
|---|---:|---:|---:|
| Best quality, 240 dpi / JPEG 90 | 70,042,481 | 39.8% | 5.7 s |
| Balanced, 180 dpi / JPEG 75 | 21,729,854 | 81.3% | 4.3 s |
| Small file, 135 dpi / JPEG 58 | 8,954,914 | 92.3% | 3.4 s |
| Strong, 108 dpi / JPEG 45 | 4,701,007 | 96.0% | 3.2 s |

The synthetic noise/compression characteristics are deliberately reproducible. These timings and savings are host observations, not Android speed/memory measurements, general savings promises, visual-fidelity certification or evidence of the 200-page device case.

### Office and OCR scope

DOCX tests inspect real package XML, text paragraphs, line/page breaks, Unicode, package relationships and cancellation without overwriting an older output. PPTX tests inspect slide order, images and package relationships. An independent review reproduced a late-validation cancellation failure, then the writer and handler were fixed and the 11-check host suite passed.

DOCX preserves basic text structure, not complex tables, columns or original page geometry. PPTX stores a rendered page image and editable extracted-text layer, not reconstructed slide shapes. Text already embedded in Arabic PDFs remains Unicode. The selected unbundled recognizer supports **Latin OCR only**, so scanned Arabic is unsupported. Microsoft Word/PowerPoint applications and downloaded ML Kit recognition on real hardware remain separate gates.

### Office application rendering observation

The installed **LibreOfficeDev 26.8.0.0.alpha0**, commit `2c87e51eeaa2b413ff4ae097b2705eea1995d8e5`, opened the four original positive host fixtures. Writer rendered `paragraphs.docx` into two pages and `unicode.docx` into one; Impress rendered `pages.pptx` into two slides and `streamed.pptx` into twenty. All six distinct full-page visuals were inspected. The twenty repeated slides have byte-identical PNG output. Latin/Arabic paragraphs, the bullet, page break, intentional replacement character and emoji are visible; the image slides render.

The original two-slide fixture reuses a 120×180 portrait JPEG while declaring landscape geometry on its second slide, producing visible stretching there. That fixture does not qualify general aspect-ratio fidelity. The PPTX text layer is deliberately transparent and is absent from the exported PDF; interactive selection/editing was not tested. This alpha LibreOffice application check is not Microsoft Word/PowerPoint, OCR, complex-layout or Android end-to-end qualification. All seven original input/sentinel hashes remain unchanged. Evidence: `reports/office-render-validation/REPORT.md`, `render-results.json`, rendered PDFs/PNGs and exact command logs. The 25-page count is not an additional test-method total.

### SDK 26–27 PDF rejection and recovery guard

Once the reader class actually ran, the affected 11-entry rerun recorded a valid 30-page reader failure as `PDF_ENCRYPTED` and a valid three-page scanner-copy password error immediately after the rejected-PDF case. Independent Poppler/pypdf inspection of the exact pulled files established that both are unencrypted. Running the two positive methods alone in a fresh process passed both. Official AOSP 8.0/8.1 source retains stale PDFium last-error checks after successful initialization/page-count/close calls; Android 9 removes those checks. Evidence and source URLs/hashes are in `reports/pdf-reader-api26-failures/diagnosis.md`, `guard-handoff.md`, `host-inspection.json` and the AOSP provenance JSONs; fresh positives are in `reports/api26-pdf-fresh-positive.log`.

All production constructors now route through `PdfRendererFactory`; its SDK 26–27 preflight uses strict PDFBox parsing with disk-bounded temporary storage before entering framework PDFium. It distinguishes encrypted and invalid PDFs, preserves descriptor ownership/offsets and parses once per renderer session. The same-process regression asserts valid→encrypted refusal→valid→corrupt refusal→valid through reader, scan and rasterizer entry points, with exact error codes and unchanged input bytes. Three reviewed follow-up cases target parser-read cancellation, actual scratch-cap error classification and deletion of a private copy when renderer close throws. Their final compiled/device results remain pending.

**Remaining limit:** PDFBox and PDFium can disagree about a document, and a later native page-content error can still affect the old framework. This guard cannot reset already-poisoned framework state and is not universal corrupt-PDF containment. No hidden APIs, permanent sentinel renderer, native patch or test-order workaround is used.

## Native payload and manifest inspection

Native repacking was repaired with behavioral ELF32/ELF64 and ZIP tests. It validates source hashes, maps virtual string-table addresses through load segments, retains dynamically loaded Python/OpenSSL dependencies and preserves retained compressed ZIP records. Hash checks remain enabled.

### API 26 compatibility-library failure and correction

The initial `PackagedMediaRuntimeDeviceTest` failed with `MEDIA_PROCESS_HANDSHAKE`. Logcat identifies the underlying error: `libpython.so` could not start because `libandroid-support.so` was missing, followed by SIGABRT in the native child. The app/instrumentation process continued. This is a reproduced native subprocess failure, separate from the originally reported paste crash.

The repacker had incorrectly listed `libandroid-support.so` and `libandroid-glob.so` as system libraries, dropping those bundled dependencies from its closure. Removing only those two exclusions retains the actual libraries. The eight repack output pins were derived from the same verified upstream 0.18.1 AARs, and native merge tasks now declare the script/pin manifest as inputs. The new regression failed in four ELF32/ELF64 × library-name subcases before the correction; the six-method suite then passed. No native recompilation, extractor change or WebP replacement-byte change was required. Evidence: `reports/native-compat-repair.md`, `native-compat-red.log`, `native-compat-green.log`, `native-compat-derivation.json` and `api26-native-runtime-logcat.log`.

The earlier compatibility-stage artifact inspection verified **eight artifact/ABI/archive observations** across compact, debug and AAB: each restored support/glob library is present, each archive matches its source pin and each restored ELF passes the layout checks. All eight observations passed; the restored libraries depend only on `libc.so`. These are repeated observations of the shipping ABI payloads, not eight distinct runtime executions.

The upstream installers compare archive byte length with their stored extraction version. The support/glob rerun progressed through Python, yt-dlp and QuickJS assertions without clearing app data, then exposed the separate FFmpeg dependency below. This is existing byte-length invalidation, not a newly added hash-based invalidator; complete installed-runtime qualification remains pending.

### GLib/PCRE transitive dependency failure and correction

The next API 26 native rerun reached FFmpeg, whose linker reported missing `libpcre2-8.so`. The repacker had omitted the dependency after conflating/skipping the physical GLib alias chain `libglib-2.0.so → libglib-2.0.so.0 → libglib-2.0.so.0.8600.0`. Exact DT_NEEDED names and physical per-archive nodes now traverse every retained ELF and relative alias; both proposed archives are reopened and checked before either is written. Only verified API 26 public libraries are exempt; `libgcc_s.so` is no longer exempt.

The final payload change restores PCRE and removes the two unused V4L aliases whose targets were already absent. It preserves all other aliases and Python bytes. All eight source archives were re-derived from the same pinned AARs; only the four FFmpeg pins change. Independent retained-payload/readelf checks covered **872 ELF instances, 408 symlink instances and 2,695 dependency edges**, with zero unresolved dependencies; all eight archives passed CRC and byte-idempotence checks. These per-ABI inspection counts are not tests or Android executions. The 15-method host suite passed after recorded red regressions. Evidence: `reports/native-closure-repair.md`, `native-closure-derivation.json`, `native-closure-green.log`, and `api26-native-rerun.log`.

The new FFmpeg archive lengths request extraction under the existing upstream cache policy. Rebuilt installed FFmpeg startup, local selection/muxing and provider behavior are still separate device gates. The previous artifact hashes and effective-ELF counts below predate this closure correction.

### Previous artifact observations before the closure correction

Read-only verification ran nine tool invocations, all with zero exit status (`reports/final-artifacts.json` and `reports/final-check-*.log`). Compact and debug passed the media guard, including extractor/version pins, CRCs, replacement hashes and ABI isolation, with no duplicate ZIP entries. The test APK and AAB passed ZIP CRC/duplicate-name inspection; the AAB's 21 media asset/component/hash checks were true.

| Previously inspected artifact | Native/override ABIs | Effective ELF payloads checked | Signature/alignment observation |
|---|---|---:|---|
| Compact APK | ARM64 only | **218** | `apksigner` verified Android Debug certificate; SDK `zipalign -c -P 16 -v 4` passed |
| Debug APK | ARM64 and x86_64 | **438** | Same debug certificate; SDK zipalign check passed |
| Android test APK | No native ABI payload | N/A | Same debug certificate; SDK zipalign check passed |
| Release AAB | ARM64 only | Separate bundle asset/hash inspection | `jarsigner` explicitly reported **unsigned**; zero JAR signing entries |

The APK certificate SHA-256 is `48040189a9cd47a6e3368ba49be989b23e1ac006ce85118d30b22c7cfabf8f60` (`C=US, O=Android, CN=Android Debug`). A zero `jarsigner` inspection exit does not mean the AAB is signed. The compact APK is an installation-testing artifact; the AAB is not a signed production upload deliverable.

The x86_64 WebP replacement assets belong only to the full-runtime debug source set. The previously inspected compact/release artifacts contain ARM64 overrides only; debug contains both ABIs. Final rebuilt-artifact inspection remains pending. Relocating these WebP assets did not change their bytes. The later support/glob correction did change the Python/FFmpeg archive pins. The inspected merged debug manifest declares min26/target36, WorkManager `dataSync` and both foreground permissions.

Five original WebP libraries remain inside each upstream ARM64/x86_64 FFmpeg archive. Per ABI, their 18 LOAD segments have 4 KiB alignment and 13 are incongruent modulo 16 KiB. The current guard checks the separately pinned replacements and excludes the five replaced originals per ABI from its effective-library count; initialization installs the replacements before starting native children. Do not describe every packaged ELF as 16 KiB-aligned. ZIP placement, ELF LOAD/RELRO and replacement-byte inspection do not prove successful loading on a 16 KiB Android kernel. See `docs/media/NATIVE_16KB_REPAIR.md` for the historical WebP provenance and current packaging correction.

## Android execution and UI qualification

### Initial API 26 run — retained failure history

The initial full instrumentation run used the **API 26 / Android 8 x86_64 software emulator, with 4,096-byte pages**, from **02:08:32 to 02:19:16 UTC on 2026-10-23**. It completed **90 runner entries: 82 reported passes and eight failures**, with a terminal JUnit summary and no incomplete started entries. The shell command exited zero, which does not make a failing instrumentation suite pass. Evidence: `reports/api26-instrumentation-run.json`, `reports/api26-instrumentation-summary.json` and `reports/api26-instrumentation.log`.

**The 82 reported passes include an unexercised 200-page case.** The old `aTwoHundredPageScanIsProcessedOnThisDevice` silently returned when its scan fixture was missing. Its runner status supplies no large-document processing evidence. Preserve the recorded count and this caveat; do not relabel that historical entry as an explicit skip that the runner never recorded. The scan-corpus test separately failed its missing-fixture assertion.

| Failed entry | Observed failure | Correction / qualification boundary |
|---|---|---|
| `PdfReaderAndScanStoreTest#initializationError` | Three expression-body coroutine methods had Boolean return descriptors; JUnit requires void. | Added explicit `runBlocking<Unit>` without changing assertions. All 95 compiled test methods were audited; the other 92 were void. No reader/scan-store method in that invalid class qualified its behavior. The provider-pipe case also requires API 29+. |
| `PackagedMediaRuntimeDeviceTest#packagedRuntimeStartsSelectsAndMuxesRealTracks` | Python child exited before the PID handshake; linker reports missing support library. | Compatibility repack repaired as described above. Upgraded-install startup and complete native fixture execution require the corrected run. |
| `PdfCompressionDeviceTest#theScannedCorpusIsCompressedOnThisDevice` | Required scan fixtures absent. | Existing deterministic corpus generated; the corrected test requires all five inputs and validates each output. No compression-performance conclusion follows from this setup failure. |
| `JumloUiTest#imageToolWithoutInputsShowsImportAndKeepsExecuteDisabled` | Expected input-prompt semantics node absent. | Scroll the existing lazy-list prompt into view, then assert visibility; disabled Process assertion retained. |
| `JumloUiTest#warmWorkspaceDeepLinkReachesTheRegisteredDestination` | ActivityScenario teardown timed out; route-body assertion did not report failure. | Retain real VIEW delivery and route assertion; restore original launch intent in `finally` for the pinned lifecycle matcher. A complete test pass still requires rerun. |
| `JumloUiTest#existingDownloadSessionSurvivesTabsAndFreshEntryIsEmpty` | Editable-field node missing; no wrong retained text value was established. | Scroll the actual field into the semantics tree before unchanged exact-text assertions. |
| `JumloUiTest#clipboardPasteKeepsWorkspaceUsableAfterRecoverableInspectionFailure` | Retry-hint wait exceeded 30 seconds after the actual Paste/initial input checks. | Scroll the loading/error lazy item before the same wait. The earlier log does not choose between an offscreen completed error and continuing initial HTTP inspection, or establish a paste crash. |
| `ProcessingNotificationTest#foregroundNoticeIsPrivateAndHasCancelAction` | Expected service type 1, actual 0 on API 26. | Assert legacy type 0 below API 29 and `DATA_SYNC` on API 29+, retaining privacy/ongoing/action/URL checks. |

The initial compiled inventory had **95 test methods in 30 classes**; it is not the executed-case count. The reviewed PDF follow-up expands source eligibility to **99 methods in 31 classes**: 96 on API 26 and 97 on API 34 under the declared SDK filters; final discovery/execution must still be recorded. SDK filters and class initialization affect discovery/execution. The initial run recorded successful **five Office handler, two native OCR cancellation, six PDF handler, one viewer interaction and one unreadable-image entries**. These are subsets of the 82 reported passes, not extra tests. They cover digital-text DOCX/PPTX behavior, source preservation/refusal/cancellation, selected PDF paths and viewer controls. They do not qualify real scanner capture, downloaded OCR recognition, scanned Arabic, Microsoft Office applications, the missing corpus or 200-page processing.

The strengthened compression tests preserve their scenarios and preset thresholds. Missing inputs now fail explicitly. Each valid generated-scan result must recompress images, skip none, produce a nonempty smaller PDF, reopen with the expected page count and retain contents/image resources. Diagnostics are written before aggregate failure assertion. The 200-page case applies the same checks. The corpus report overwrites its output while the 200-page case appends, so runs must preserve their ordering or collect separate reports. Source/correction evidence is in `reports/android-test-signature-fix-summary.json`, `reports/compression-device-truthfulness-summary.json` and `reports/clipboard-inspection-diagnosis.md`.

### Subsequent recorded partitions and source corrections

The affected API 26 rerun completed **11 entries: six passes and five failures** (`reports/api26-affected-rerun-summary.json`). The passing entries were rejected-PDF refusal, empty download workspace/invalid input, empty image-tool UI, warm deep link, tool-search state, and notification behavior. The two plain-PDF failures exposed the framework state problem above. Settings Back and download-session navigation failed with a window-focus exception; the focused Settings Back rerun subsequently passed. Clipboard retry still exceeded its unchanged 30-second assertion; focused UI evidence showed continuing analysis rather than an observed paste crash. These overlapping partitions are not added to the initial 82 reported passes.

The direct HTTP/DNS inspection now has a **20-second shared response budget across redirects**, using the existing cancellable HTTP call. The synchronous WebView cookie initialization remains non-interruptible; elapsed initialization is deducted before a call. No test timeout was increased. Its post-change device result is pending (`reports/direct-inspection-deadline-fix.json`); the earlier `clipboard-inspection-diagnosis.md` describes the pre-deadline source. Native FFmpeg, PDF recovery and clipboard corrections must all be rerun on the final candidate.

A separate numeric source review corrected the selected dock pill and wholly translucent enabled favorite glyphs. All **144 documented comparisons** meet their assigned text/graphic thresholds after the source changes (`reports/ui-contrast-review.md` / `.json`). This covers declared static colors and alpha composition, not wallpaper accents, every control/transition, TalkBack, font scaling or an Android accessibility certification.

### Corrected Android run — coordinator update block

<!-- CORRECTED_ANDROID_RESULTS_START -->

**Final corrected candidate qualification is pending.** Earlier affected/focused outcomes are recorded above; they do not qualify the latest closure/PDF/deadline/contrast source changes or the reviewed PDF follow-up. The coordinating task owns the final rebuild, installation, upgraded-runtime extraction checks, fixture push, instrumentation and captured outputs. Compilation and static payload inspection do not fill these execution cells.

| Target | Corrected/current execution status |
|---|---|
| API 26 / Android 8 x86_64 software emulator | Final candidate rerun pending. Initial 82/8, affected 6/5 and fresh PDF 2/2 history remains above; replace only from final logs and fixture evidence. |
| API 34 / Android 14 Google APIs software emulator | Pending; no corrected-run result recorded. |
| Physical Android device | No connected physical device available; not run. |
| ARM64 compact installer on physical hardware | Not run. |
| 16 KiB page-size Android target | Not run. |

<!-- CORRECTED_ANDROID_RESULTS_END -->

This environment has no KVM acceleration. Earlier infrastructure attempts included Gradle-daemon/workspace interruptions and API 34 system-service/watchdog failures. These are retained in the logs; they are neither application successes nor established application crashes. The actual initial API 26 failures and source corrections are recorded separately above.

The UI source/config checks cover five-tab ordering, contained indicators, neutral dark tokens, local vectors, removed settings, font pins, scoped FileProvider paths and direct scanner wiring. No full accessibility certification, physical predictive gesture, real print-service output, scanner camera capture, cross-app drag-and-drop or Play-delivered language behavior is inferred from those guards or a subset of emulator tests.

## Live provider matrix

All seven requested providers remain explicit Android qualification targets. The checked-in [live matrix](docs/testing/live-media-matrix.template.csv) contains **138 `NOT_RUN` template rows** across three desired device profiles, including 35 provider×case combinations per target, document cases and lifecycle/storage workflows. Use [the runner and instructions](docs/testing/LIVE_MEDIA_QUALIFICATION.md) with current public samples and record actual results in a copy. Android 8/API 26 may replace the Android 9 desired target to exercise minSdk.

The pinned bundled **yt-dlp 2026.10.21** returned parseable metadata for one public sample from each provider. All seven host observations were `HOST_METADATA_RETURNED`, extractor exit zero. They do not establish completed Android downloads or output quality.

| Provider / sample | Returned formats | Known video / audio formats | Reported duration | Reported short edge(s) | Diagnostic / limit |
|---|---:|---:|---:|---|---|
| YouTube — NASA `bTvCr6K_GCI` | 35 | 22 / 10 | 61 s | 144–2160 px | Three other entries are storyboards; warnings about absent default JS runtime and malformed m3u response. |
| TikTok — `@particleclara` | 12 | 11 / 12 | 7 s | 576, 720, 1080 px | No impersonation target available warning; metadata returned. |
| Instagram — `@kazaneflowerboy`, `CHNZ_YHFPwl` | 9 | 5 / 1 | Not reported | 240, 328, 514, 720 px | No diagnostic; some entries have unknown tracks. |
| Facebook — NASA Johnson | 7 | 4 / 1 | 19.233 s | 360, 540, 720, 1080 px | No diagnostic; some entries have unknown tracks. |
| LinkedIn — ESA Connectivity | 2 | 0 / 0 known | Not reported | Not reported | MP4 entries lack codec/dimension metadata; tracks remain unknown. |
| Google Drive — Daycraft AI sample | 4 | 2 / 2 | 21.399 s | 360 px | No diagnostic; one muxed entry reports a very small filesize, with stream validity untested. |
| Direct — W3C Sintel MP4 | 1 | 0 / 0 known | Not reported | Not reported | Generic container response; tracks remain unknown. |

Video/audio format counts can overlap for muxed entries. Zero **known** tracks means missing metadata here, not proof of absent tracks. Durations, dimensions and sizes are extractor reports, not measurements of downloaded output. No completed media download, selected-quality verification, conversion, playback, invalid/offline, pause/resume, background or physical-device result is supplied by this table.

Both host runs used Linux/Python 3.12.14, no login/cookies/media download, 40-second per-input deadlines, 10-second socket timeouts, no retries, a 4 MiB stdout cap and a 64 KiB diagnostic cap. The five new probes ran sequentially in about 9.55 seconds, recording 58,008 KiB (56.6 MiB) maximum child RSS. Node was present, Deno/QuickJS absent, and a proxy environment was present. Android's packaged QuickJS, app format selection, WorkManager and MediaStore/SAF were not exercised. Separate host FFmpeg fixtures verify real merge/transcode outputs, not provider downloads.

Evidence: `reports/live-media-host-probes.json` for direct/YouTube; `reports/public-provider-host-preflight.raw.json` and `reports/public-provider-host-preflight.json` for the five new observations, exact commands, identities and diagnostics; `reports/public-provider-fixture-candidates.json` for primary-page provenance. The latter's original discovery-time `NOT_RUN` fields precede the later metadata probe. Every raw result field was preserved in the supplemented preflight report. These host outcomes do not update Android matrix status.

## Remaining limits and release gates

- Complete the corrected Android execution record above, preserving the initial failures and fixture caveat. The [tool qualification matrix](docs/testing/tool-matrix.template.csv) retains 156 unexecuted processing cases across 39 tools and four input scenarios; 32 navigation/blocked rows have explicit N/A reasons, with separate UI/gating checks still required. No all-tools pass is claimed.
- Review the 568 recorded lint warnings; successful lint completion is not a warning-free result.
- Run the provider matrix on current accessible samples and at least one physical device. Record errors/blocked providers honestly; authentication or deleted content is not a successful download.
- Verify actual foreground/background/pause/resume/process-death behavior, persisted SAF folders and default MediaStore publication on Android14+.
- Open representative generated DOCX/PPTX files in Microsoft Word and PowerPoint. Test real scanned pages with the downloaded OCR model and the scanner UI.
- Supply the production signing key and qualify the signed ARM64 release and Play-delivered language behavior before publication.
- JVM module extraction is complete in source; native iOS adapters and Kotlin Multiplatform migration are future work described in `iOS_PLAN.md`.

## Reproduction

Use JDK17 and the pinned wrapper with Android SDK36. Keep resource-heavy emulator and R8 runs separate on constrained hosts.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
./gradlew :app:verifyCompactApkSize :app:bundleRelease :app:lintDebug
python3 scripts/inspect-media-artifact.py app/build/outputs/apk/compact/app-compact.apk
./gradlew :app:connectedDebugAndroidTest
python3 scripts/tests/test-live-media-qualification.py
```

Additional host suites are `scripts/test-core.sh`, `scripts/test-pdf-compression.sh` and `scripts/test-office-packages.sh`. Office's standalone script accepts `JUMLO_KOTLIN_LIB` pointing to a Gradle/Kotlin compiler library directory. Its independent Python reader check requires python-docx and python-pptx. The Android source includes tests that require Play services or skip explicitly unavailable fixtures; preserve those statuses in reported counts.
