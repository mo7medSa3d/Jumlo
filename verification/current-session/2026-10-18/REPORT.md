## Follow-up: automatic browser profiles and downloader UX, 2026-10-18

**Release verdict: NO-GO.** This revision builds on the source candidate documented below. New functionality is bounded, public media discovery; social-video sites are **not** claimed as supported providers or independently verified.

### Changes

- Downloader UI: removed in-app browser, cookies import, custom header/User-Agent sheet and stale account-login instructions; rebuilt error copy (EN/AR) and retained sensitive legacy session data locally without exfiltration.
- Data path: built-in mobile/desktop HTTP profiles selected per hostname. Only safe fallback: 406/415 negotiation, or HTTP 200 HTML that yields no verified media; **no** 401/403/429 profile retries. Follow-up profile is honored at source preview, media probe, direct media transfer, and generic file transfer. Existing custom User-Agent cannot override fallback.
- Passive HTML extraction: OpenGraph video/audio URLs and JSON-LD `contentUrl`, including extensionless signed media, are candidates; server media response and signature validation still gate download and publication. HTTPS/local-host metadata checks and bounded HTML reads.
- UX: compact Home and Files headers, Downloads primary import action, compact transfer summary, active-first list. File processing, PDF editing and themes are preserved from the previous candidate.
- Added `scripts/test-downloads-density.py` and integrated it into the host verification entrypoint.

### Fresh evidence

| Command | Result | Meaning |
|---|---|---|
| `LANG=C.UTF-8 LC_ALL=C.UTF-8 bash scripts/verify-host.sh` | **exit 0**: **141 Kotlin host passed**; 11 media static, 4 UX static, 10 SQLite, 14 static integrity, 12 previous UI wiring, 14 brand/motion, launch safeguard checks passed | Kotlin policy and bounded parser / local HTTP transfer invariants, static source wiring; **not** Android UI/runtime |
| `python3 scripts/test-downloads-density.py` | **exit 0**: 4/4 | Static composition/ordering contracts; no screenshots or interaction testing |
| `./gradlew --offline :app:assembleDebug` | **exit 1**: Gradle wrapper attempted to download 8.14 but `services.gradle.org` was unreachable | Android compilation unverified; **not** a compiler error diagnosis |
| Live Instagram/TikTok/YouTube/Facebook/LinkedIn URL => media preview => real downloaded playable output | **NOT EXECUTED** | **No verified platform coverage** |
| Android 26/36, font scale, RTL, TalkBack, background transfer/process death, 16KB native compatibility, APK size | **NOT EXECUTED** | Release gate remains open |

### Real support matrix

| Capability | Current code | Proof |
|---|---|---|
| Direct downloadable HTTPS files | MIME/format inspection, streaming transfer, magic validation and staged publication | Host-controlled HTTP loopback only |
| Public HTML5 pages with OpenGraph/JSON-LD direct playable sources | Passive extractor and two bounded HTTP profiles | Kotlin parser tests + source wiring only |
| Public YouTube, Instagram, TikTok, Facebook, LinkedIn page extractors | **Not implemented as provider-specific extractors** | No live source/site verification |
| HLS/DASH, separate A/V muxing | **Not implemented** | No |
| Authenticated accounts | Previous site-scoped cookies retained in app-private jar; manual browser and cookies UI removed | No account/provider integration test; no new sign-in UX |
| DRM/paywalls and protected access | **Not bypassed** | Intentionally unsupported |

### Engineering decisions and caveats

Rotating a request User-Agent does **not** execute JavaScript, decrypt DRM, or recreate a real browser TLS fingerprint. A full provider-aware runtime such as `yt-dlp` would require Python on Android and—according to current upstream recommendations—FFmpeg plus a JavaScript runtime for comprehensive YouTube extraction, with substantial package, ABI, licensing and source-policy qualification. See https://github.com/yt-dlp/yt-dlp and https://developers.google.com/youtube/terms/developer-policies . The Play-distributed APK budget remains 60 MB; no false optimization or unapproved executable updater was introduced. Android's long user-initiated downloads may need UIDT JobScheduler on API 34+; current WorkManager behavior must be verified in a device and is not certified here: https://developer.android.com/develop/background-work/background-tasks/data-transfer-options .

### Provenance

Derived from the user-supplied Jumlo ZIP and local flagship source candidate; no `.git` history in this worktree. No GitHub commit, push, PR, workflow run or APK created. The exact end-to-end consumer behavior is **not** verified; preserve this source-only status in handoff.

---

# Jumlo 2026-10-18 source-change verification

**Verdict: SOURCE CANDIDATE / NO-GO for release** — no Android SDK, Gradle distribution, Android emulator, or installed test device was available in this execution. All prior bundled native logs describe different source revisions and cannot qualify these edits. No Android binary was produced. The source archive is a working-code delivery, not an APK.

## Provenance

Input source archive: `Jumlo-main.zip`, SHA-256 `5d60b822ad328c00995c96d333e4b870efb55586dc370887cc251af890d16663`.
Checked remote main read-only: `mo7medSa3d/Jumlo` at `3044f345b1f9f2e529c51fff8845cd52022b3089` (2026-10-17). The uploaded ZIP was used as the editable source; no claim is made that it exactly matches this remote SHA. No GitHub push, commit, or workflow run was performed.

## Executed, independently scoped verification

| Command | Observed result | Log |
|---|---|---|
| `LANG=C.UTF-8 LC_ALL=C.UTF-8 bash scripts/verify-host.sh` | **EXIT 0**; 135 Kotlin algorithm/transfer tests; 6 media source tests; 10 real SQLite structural/queries tests; 14 source/resource checks; 12 redesign source-wiring checks; 14 brand/motion source checks; four shell safeguard checks | `host-verification.log` |
| Kotlin fidelity-preset test before fix | **EXIT 1**, unresolved `PdfCompressionPolicy.profiles` | `quality-preset-failing-test.log` |
| Kotlin full-bitmap memory guard before fix | **EXIT 1**, unresolved `PdfCompressionPolicy.canDecodeImage` | `memory-guard-failing-test.log` |
| Source contract for PDF preview state, real audio label, accurate error before fix | **EXIT 1**, expected source behavior absent | `pdf-preview-failing-test.log` |

**Limit:** Host tests are CLI Kotlin algorithms, loopback HTTP/filesystem transfers, SQLite queries, and code/resource checks. They are not an installed Android test. No PDFBox/Compose runtime compilation or real output opener tested here. The structural SQLite v1 fixture is generated from current checked-in declarations and is **not an authentic historic migration export**. The host media transfer tests use local controlled fixtures and do not prove any live provider.

## Source architecture and modifications

```
Import/Android ACTION_SEND -> MainActivity/NavHost -> WorkspaceDraft / MediaViewModel
   -> persisted JobRequest / WorkManager -> OperationDispatcher
       -> PdfOperationHandler / DownloadOperationHandler / MediaOperationHandler
           -> PdfBoxBackend / PdfPageGeometry / Html5MediaDiscovery / StreamTransfer
               -> staged output -> OutputStore validation / publish -> Room -> Activity / Files
```

- PDF page editor now renders lazily scaled thumbnails, with drag-to-reorder, selected page extract/split/delete, accessibility move controls, and reorder undo/redo. The editor maps to existing serialized form settings, rather than changing the database schema. It lacks device-verified drag behavior, multi-page drag grouping and a complete per-page rotation history.
- Image-to-PDF AUTO measures the decoded EXIF-normalized input and selects proportionate page geometry; A4, Letter and Custom remain optional. Native output geometry requires testing.
- PDF compression no longer converts the entire document into page screenshots. Instead it attempts bounded embedded-image recompression, checks output can be opened and retains the page count; failure preserves the source. Large image decode now has a heap-dependent preallocation guard and masked XObjects are kept as-is. **Image stream recompression can still be lossy**. Validation does not establish image fidelity, text-layer equivalence, annotation/form fidelity or full playable/viewable results; these must be tested on device and independently compared.
- Static HTML5 media discovery is bounded and passive, requires a successfully probed real original file, verifies signature after transfer, and does not invent quality/resolution/audio information. Real HTTP loopback transfer tests pass. This is **not** YouTube extraction, Javascript/dynamic video extraction, DRM, HLS/DASH or muxing. No live-provider transfer evidence.
- Activity groups running jobs, recent published outputs and other history; visual QA on devices is pending. Corrupt PDF thumbnails now have an error state, and the media badge correctly labels unprobed audio.

## Media category matrix

| Category | Current source implementation | Verified here | Remaining limitations |
|---|---|---|---|
| Direct HTTPS media | HEAD/range probe, bounded stream transfer, structural magic validation | Local/loopback policy and transfer tests only | No live file or Android playback/output test |
| Static HTML5 pages with discoverable MP4/MP3/WebM links | Passive parser + per-URL HEAD/range probe + user choice | Kotlin discovery tests only | No live external HTML5 site/device integration |
| Authenticated media | Existing session-aware headers for authorized sources | Not executed | Real OAuth/login/session expiry tests missing |
| HLS/DASH/muxing | Not implemented in lightweight base | No | Need authorized manifest parser and transport/mux qualification |
| YouTube/TikTok/Instagram/etc. | No supported maintained bundled runtime in release | No | Dynamic/extractor/provider terms and license qualification missing |
| DRM/protected/paid | Intentionally not bypassed | N/A | Unsupported by design |

## Mandatory unexecuted gates

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`, APK/AAB size, artifact signature, Android 26/36 and 16KB device installation, UI/RTL/TalkBack gestures and process recreation, original PDF vs compressed PDF visual/text validation, memory/CPU/jank measurements, live rights-clear media playback/quality, SAF revocation and WorkManager background behavior, migrations from authentic historic Room database, SHA-pinned remote CI. **Do not use this candidate as a production release.**

Research: https://developer.android.com/develop/background-work/background-tasks/data-transfer-options ; https://developer.android.com/develop/background-work/background-tasks/uidt ; https://developer.android.com/guide/practices/page-sizes ; https://developer.android.com/jetpack/androidx/releases/pdf . These motivated keeping existing WorkManager, avoiding new unqualified native runtimes and requiring 16KB/SDK checks before any new dependency. AndroidX PDF compatibility with API26 needs version-by-version qualification; platform PdfRenderer remains in place.
