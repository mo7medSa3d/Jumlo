# Jumlo — updated hardened source continuation (2026-10-17)

## Executive result

**NO-GO / SOURCE ZIP ONLY.** Operated on a separate extraction of
`Jumlo_Updated_Source_Hardened_2026-10-17.zip`; the input ZIP was not modified
and no git remote was used. The Android app has **not** been compiled, installed,
opened in a reference file reader, profiled or tested on API 26/36 during this
execution. No APK, AAB or Play-ready build is claimed.

## Commands actually executed and results

| Command | Exit/result | Log |
|---|---|---|
| `LC_ALL=C.UTF-8 LANG=C.UTF-8 bash scripts/verify-host.sh` | 0; **117/117** Kotlin host, **8/8** guard, **10/10** SQLite SQL, **14/14** source checks | `verify-host.log` |
| `bash scripts/test-workspace.sh` | 0; **6/6** | `workspace.log` |
| `bash verification/production-2026-10-17/ui/run-home-layout-tests.sh` | 0; **11/11** | `home-layout.log` |
| `python3 scripts/test-media-ffmpeg.py` | 0; **5/5** host-binary/synthetic codec + real ffprobe reader checks | `media-ffmpeg.log` |
| `python3 scripts/test-release-media-isolation.py` | 0; **5/5** static source assertions | `release-media.log` |
| `python3 scripts/generate-feature-matrix.py` | 0; generated 45 source capability classifications | `feature-generator.log` |
| `./gradlew --no-daemon --offline :app:assembleDebug` | exit **1**, Gradle wrapper download failed before Android compile | `android-build-attempt.log` |

Host environment: Java **21.0.12**, Kotlin CLI **1.9.0** (not CI's 2.0.21),
Python, SQLite and system FFmpeg. `ANDROID_HOME` / `ANDROID_SDK_ROOT` unset;
Android SDK and Gradle distribution not cached; distribution download failed
with `java.net.UnknownHostException: services.gradle.org`. See `environment.txt`.
Passing host Kotlin source is **not** a result of Android KSP/Hilt/Room/Compose
compilation. The actual 8/10/14 tests are per-suite, not Android JVM unit tests.

## Changes to the existing source

1. Corrected PDF crop coordinates (screen top-left to PDF bottom-left), pure
   validated geometry and host regressions. Android PdfBox output not produced.
2. Corrected images-to-PDF memory accounting: one bitmap resident/recycled per
   page; reject excessive input count. Android PDF output not produced.
3. Encrypted ZIP checks AES-256 central directory headers (reject plaintext,
   weak/ZipCrypto method), rejects unsafe member names before extraction,
   preserves existing destination, writes under a sibling stage. Authored
   real zip4j JUnit tests for traversal, case/Unicode collisions, password
   and byte/entry limits: NOT EXECUTED, SDK absent.
4. Added strict official Google Drive/Docs HTTPS ID parsing; rejected arbitrary
   text, fake domains, userinfo, HTTP, fragments and nondefault ports by
   actual Kotlin host tests. Bounded API metadata/error reads, create-new-only
   destination writes with `FileChannel(CREATE_NEW)` and guarded cleanup.
   OAuth, official API live, and Android JUnit transfer tests: NOT EXECUTED.
5. New Room DAO insertion paths scrub signed legacy `fullUrl`, with an
   on-open callback to scrub preexisting rows without deleting job progress.
   Actual SQL test passed, persistent Room/device reopen test authored but
   **NOT EXECUTED**. Old disk/WAL/backups may retain old data; not forensic erase.
6. Removed media automatic terms acceptance; explicit localized rights checkbox
   defaults to false; persisted consent checked by the media operation handler.
   Disallowed unpinned yt-dlp online updates; optional binary remains debug-only
   in source and wholly gated by policy. Source gates 5/5, no live platforms.
7. Added a CI synthetic FFmpeg host gate, source regression checks for consent,
   bundled extraction, callback and create-new output, and documented all
   unchanged security/release barriers.

Full every-file list in root `CHANGELOG.md` of this ZIP.

## Required external verification still NOT DONE

- **Native:** real Gradle 8.14 + Android SDK API 36, debug compile,
  testDebugUnitTest (PdfBox/zip4j/CameraX/ML Kit APIs), lint MissingTranslation,
  R8 release APK/AAB, KSP/Hilt/Room generated code, variant dependency graph,
  16KB ELF/APK checks, API 26/36 device/emulator instrumentation.
- **Feature end-to-end:** every PDF, image, file/archive/OCR/scanning/WebView
  operation on representative user-selected inputs, published outputs,
  independent PDF/image/ZIP readers, negative text-extraction after redaction,
  text preservation after compression, CameraX image quad acceptance.
- **Persistence/recovery:** authentic pre-v2 Room database migration,
  process death during publish/download/merge/scan, cancellation, SAF
  permission revocation, low disk, duplicate enqueue, signed URL removal and
  database file/WAL examination.
- **UI:** full theme-token application, all screens' EN/AR, light/dark,
  200%-font/RTL/TalkBack/predictive-back, screenshot goldens, actual
  baseline profile and physical device cold start/jank thresholds.
- **Media:** yt-dlp/FFmpeg binary resolution, exact mobile FFmpeg `-buildconf`
  and licensing, 10 named services video/audio live cases, Drive OAuth and
  official API transfers, authorized in-app WebView session security.
- **Owner/Play:** app signing key (never in ZIP), versioning/publish workflow,
  Play data-safety declarations, final privacy/licensing and release approvals.

The existing `.github/workflows/ci.yml` runs native Android gates and API 26/36
emulators **when launched in a connected CI environment**. No workflow was
triggered from this uploaded, non-git source archive; no remote green was
observed. A CI workflow file by itself is not proof of a passed build.

## Sign-off required

Media GPL/LGPL obligations and exact FFmpeg binary configuration; approval of
pinned-hash IBM Plex SIL OFL inclusion; release media gating (retain OFF until
verified); individual media platform/Drive VERIFIED status (all UNVERIFIED);
owner-signed release and Play privacy responses. The published capability
matrix (`docs/FEATURE_STATUS.md`) has **zero verified Android E2E features**.
