# Device gates and downloader work — 2026-10-21

Scope: Gate B compression evidence from a real device, plus the media-downloader
defect fixes and their tests. All numbers below carry their source and build
identity; where a number is missing, the reason is stated.

## Provenance

| Item | Value |
| --- | --- |
| Instrumentation | `app/src/androidTest/java/com/jumlo/app/pdf/PdfCompressionDeviceTest.kt` |
| Device | Samsung Galaxy Tab A7 Lite SM-T505 (`gta4lxx`), serial `R52N80B5KRY`, Android 17, `arm64-v8a` |
| Budget | `WorkBudget().maxBitmapBytes` = 67,108,864 B (64 MB) |
| Report source | `device-compression-report.txt`, written **on the device** by the test and pulled over `adb pull` (session artifacts `/tmp/opencode/device-report.txt`, `/tmp/opencode/device-report2.txt`) |
| Tree at the run | `6f32d7c` ("Media: token setting reachable, device evidence, ignore rules, corpus"); raw Gradle run log was not preserved, only the pulled device report |
| Resume pending | A re-run on the merged tree `6a9c4a5` is **not measured yet**: the USB link flapped twice at re-attach and the bench fixture re-push failed mid-transfer (fixtures located at `/tmp/opencode/scratchpad/bigscans/scan_*.pdf`) |

## Gate B — compression on the device (measured)

### The scanned corpus, three presets (run 2, `scan_*.pdf` sources)

Presets: Strong (108 dpi, q45), Medium (180 dpi, q75), High (240 dpi, q90) —
the three quality presets the UI offers.

| File | Pages | Strong (dpi108/q45) | Medium (180/75) | High (240/90) |
| --- | --- | --- | --- | --- |
| scan_01pages.pdf | 1 | 1,327,635 → 80,340 B, **93.9 %**, 0.3 s | 1,327,635 → 299,659 B, **77.4 %**, 0.3 s | 1,327,635 → 742,190 B, **44.1 %**, 0.4 s |
| scan_03pages.pdf | 3 | 4,004,071 → 239,635 B, **94.0 %**, 0.8 s | 4,004,071 → 884,409 B, **77.9 %**, 0.9 s | 4,004,071 → 2,211,985 B, **44.8 %**, 1.1 s |
| scan_06pages.pdf | 6 | 8,091,697 → 494,113 B, **93.9 %**, 1.4 s | 8,091,697 → 1,810,510 B, **77.6 %**, 1.8 s | 8,091,697 → 4,520,350 B, **44.1 %**, 2.3 s |
| scan_10pages.pdf | 10 | 13,259,533 → 796,746 B, **94.0 %**, 2.2 s | 13,259,533 → 2,945,397 B, **77.8 %**, 3.2 s | 13,259,533 → 7,374,135 B, **44.4 %**, 3.7 s |
| scan_30pages.pdf | 30 | 40,247,692 → 2,411,327 B, **94.0 %**, 7.4 s | 40,247,692 → 8,936,537 B, **77.8 %**, 9.3 s | 40,247,692 → 22,441,231 B, **44.2 %**, 11.5 s |

Every image was replaced (`replaced = pages`, `skipped = 0`) across all fifteen
runs; every run ended `ok`.

### Peak heap while compressing

Sampled with `HeapWatch` (Runtime + `Debug.getNativeHeapAllocatedSize()`) on
every page callback and after the run:

- Strong: peak Java ≤ 12,109,040 B (scan_30pages), native ≈ 6.19 MB
- Medium: peak Java ≤ 11,764,976 B (scan_30pages), native ≈ 6.19 MB
- High: peak Java ≤ 12,584,176 B (scan_30pages), native ≈ 6.20 MB
- All far below the 64 MB `WorkBudget` byte ceiling.

### The 200-page case (run 2)

The first 200-page attempt (run 1) exposed a real defect, kept here because it
is evidence: the document was assembled with `importPage` and the copies **kept
the pages but lost the photos** (`before=47,680 B, replaced=0`) — a negative
saving of −4.7 %. The test was then corrected to build the document with
`PDFMergerUtility` (which clones page resources), and run 2 measured the real
worst case:

```
RESULT200 file=scan_200pages.pdf pages=200 replaced=200 skipped=0
before=265,107,546 after=15,935,205 saved=94.0% seconds=44.7
peakJavaBytes=20,391,152 peakNativeBytes=6,205,824 ok
```

200 pages in 44.7 s, 94.0 % smaller, peak Java 20.4 MB — inside the budget.

### Unreadable-image resilience

`broken.pdf` (letter page with a deliberately corrupt 4096-byte DCT stream):
`replaced=0 skipped=1` under Strong/Medium/High; the source document passes
through unchanged (`before=4,806 after=4,806`) instead of failing the whole
job. The in-test assertion (`skipped > 0 || failed`) held.

### Idempotency observation worth keeping

Re-compressing an already-compressed output stays at or near zero saving and
the ladder skips what cannot shrink further (e.g. `scan_10pages-Strong-out`
under Strong: −0.1 % guard, `skipped=10` under Medium) — the ladder floor and
the `betterCandidate()` test do their job on hardware.

## Downloader defects — fixed and tested

| Defect | Fix | Commit | Verification |
| --- | --- | --- | --- |
| `service.py` audio conversion wrote the converted file next to the input (`-o` path lost) and `MediaStream` validator results were not forwarded to the client | audio conversion `-o` path + validator forwarding | `95bcf61` | service suite 4/4 passed (`tools/media-service` tests) |
| Client offered muxed "137+140" (video-only + audio) formats but the remote extractor picked single-file only | muxed selector forwarded through `RemoteExtractorRuntime.kt` | `038ec4e` | JVM unit suite green (`RemoteExtractorRuntimeTest`) |
| Media-service token setting existed in the service but had no UI wiring | `pref_media_service_token` (bilingual), `AppSettings.mediaServiceToken`, `AppViewModel.setMediaServiceToken`, `settingsFlow` field | `6f32d7c` | `:app:testDebugUnitTest` green |

## Tooling snapshot at doc time

- `:app:compileDebugKotlin` — BUILD SUCCESSFUL on the merged tree
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL
- `bash scripts/verify-host.sh` — `RESULT passed=181 failed=0`, `ALL PASS`, exit 0
- Merge commit: `6a9c4a5` (35 conflicts resolved; policy documented in its message)
- Publication: the local lineage (`95bcf61`, `038ec4e`, `6f32d7c`, merge `6a9c4a5`,
  bench commit) is consolidated for publication into the single commit that
  carries the same final tree; `tests/ocr_benchmark` media fixtures are not
  published (local-only, `.gitignored`). The fix SHAs above remain the
  provenance of where each defect fix was verified locally.

## Honest gaps

- The raw `com.android.tradefed` console log of the instrumented run was not
  preserved; the pulled `device-compression-report.txt` (the test's own
  artifact) is the record. Exact APK SHA-256 for that run is unknown; the
  device gate was taken against the debug variant at `6f32d7c`.
- Re-run on `6a9c4a5` pending: USB link unstable at time of writing
  (`lsusb` empty between flaps); fixtures ready for re-push.
