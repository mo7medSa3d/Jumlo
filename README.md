# Universal Utilities - Android real-processing candidate

**Status: partial implementation; NOT Android-build-verified, NOT device-verified, NOT release-ready.**

This is the modified native Android source from the supplied work-in-progress archive. The simulated repository/engines/screens were removed from the production source set. The replacement uses actual file bytes and Android APIs, but the full approved 41-task plan is not complete. See `verification/TASK_STATUS.md` for each task and `verification/VERIFICATION.md` for executed checks and blockers.

## Media downloader extension in this package

This package adds source-backed media analysis, actual format selection, exact
video/audio pairing, source-codec audio or MP3/M4A/Opus export, an embedded browser,
and integration with the existing job/publication system. It does not assert
that every platform or URL works. No live platform has been tested in this runner.

The native media runtime is an explicit optional dependency boundary:

```sh
bash scripts/restore-wrapper.sh --download
./gradlew -PextendedMedia=true :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Without `-PextendedMedia=true`, the base source set explicitly disables media
extraction. Candidate tools also remain debug-only until native qualification.
The upstream 0.18.1 wrapper is marked prerelease and GPL-3.0; review the native
payloads and distribution obligations before shipping. No binary or relicensing
of the owner's existing source is part of this handoff.

To replay all available host checks, including five real FFmpeg/ffprobe cases:

```sh
bash scripts/verify-host.sh --with-media
```

This additionally requires installed FFmpeg/ffprobe and Python; it downloads
nothing. Current evidence is **`verification/media-upgrade/VERIFICATION.md`**.
The historical base report and logs remain preserved. Task accounting is now
17 partial/unqualified and 24 not implemented/gated, out of 41 approved tasks.

## Implemented in the candidate source

- SAF/Photo Picker/share intake, immutable local source snapshots, versioned workspace drafts, opaque IDs, Room-backed jobs and outcomes, foreground execution and cancellation.
- Images to PDF, local PDF preview, and PDF page exports to PNG/JPEG. Structural PDF operations are unavailable rather than rasterized substitutes.
- JPEG/PNG/WebP encoding, EXIF orientation normalization, resize/fit/fill/pad/stretch, crop/quarter-turn/flip and measured-size compression outcomes. Candidate output policy is sRGB with metadata removed; metadata-preserving conversion is not claimed.
- Actual ZIP creation/extraction with measured limits, path confinement, duplicate-name handling and cleanup; SHA-256 calculation/comparison.
- Actual streamed HTTP GET, relative redirects, bounded writes, unknown sizes kept unknown, and conservative resume bound to range, validator, length and the observed final URL's digest. Signed URLs remain in memory; after process death the user must re-enter them. Changed links require a new job.
- Actual granted-folder browsing, folder creation, guarded rename, confirmed file deletion, read-only open/share, and Save As with read-back SHA-256 verification.

The four-pillar UI reuses the original Quiet Precision theme/components but does not yet preserve all approved detailed screen behavior. All 46 added media controls have English/Arabic resources; older new controls are not fully localized. The registered candidate tools are enabled only in debug builds. Release routing and dispatch stay gated pending native qualification. Vault, structural PDF edits and most expansion families have no enabled implementation.

## Run the checks available without Android tooling

Requires an existing Kotlin CLI/JDK, Python 3 and Bash. It does not install/download anything:

```bash
bash scripts/verify-host.sh
```

This compiles and executes the actual dependency-free Kotlin/domain source, uses real local HTTP fixtures and file IO, executes the actual DAO SQL with SQLite, and runs narrow source/launcher checks. It does not replace Android compilation, Room/KSP verification, lint, or device testing.

To rerun the historical base verification recorder (core checks, not the real-media extension; this overwrites `verification/final`, not the current extension evidence):

```bash
python3 scripts/run-verification.py
```

Exit 0 means no failures or blocked checks; exit 1 means a failed check; exit 2 means qualification is blocked. Detailed outcomes go to `verification/final/results.json`.

## Android build prerequisites and commands

Approved candidate baseline: JDK 17, Gradle 8.14, AGP 8.10.1, Kotlin/Compose plugin 2.0.21, KSP 2.0.21-1.0.27, Android SDK/target 36, Build Tools 36.0.0, minimum API 26. The runner used for this handoff had no usable Gradle/Android SDK or device and could not resolve the wrapper download host. The official wrapper JAR is consequently NOT included.

On a build machine you have already provisioned and authorized for network access, explicitly restore only the checksum-verified official wrapper:

```bash
bash scripts/restore-wrapper.sh --download
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

Windows PowerShell equivalent:

```powershell
.\scripts\restore-wrapper.ps1 -Download
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

For an existing trusted Gradle 8.14 plus JDK 17/SDK 36 and cached dependencies, `scripts/verify-local.sh :app:assembleDebug` never attempts downloads. Supply `GRADLE_BIN`, `JAVA_HOME` and `ANDROID_HOME` in your own environment as needed; no absolute SDK path is packaged.

With an authorized connected test device, run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

These native commands are instructions for verification, not a claim that they passed. Android compilation/integration defects may remain. No APK, signing key, emulator, SDK or Gradle distribution is in this source package.

## Architecture and source map

`data/source` owns intake and snapshots. `model/OperationContracts.kt` and `model/WorkspaceDraft.kt` separate execution requests from editor state. `data/jobs` owns dispatch, budgets and durable job state. `data/output` adapts the tested recoverable publication journal to Room and FileProvider. `data/processing` implements native adapters; `core` contains the host-tested byte engines/policies. `ui/navigation` and `ui/AppViewModel.kt` connect the live state to Compose. `data/files` uses SAF providers rather than guessed filesystem paths.

The original plan/spec/subsystem documents are archived under `docs/superpowers`; their historical "review pending" wording is superseded by the user's explicit execution approval. `docs/approved/tasks.json` supplies stable task IDs. Execution status is in the verification documents, not the historical planning text.

## Important remaining work

Structural PDF backend/operations, advanced PDF features, most image expansions, camera capture, webpage-to-PDF capture, multipart/scheduled transfer features, batch rename, duplicates, managed trash, encrypted archives and authenticated vault are unfinished. The detailed task report identifies the exact boundary. Enabling their cards without implementations/qualification is not a fix.

Process-stop/OS-version/device/provider matrices, complete localization/accessibility, retention/cleanup controls, full Room migration validation, dependent-library/license qualification and an independent security review remain open. Publication is process-recoverable in the tested core scenarios, not proven power-loss atomic; one corrupt journal may block reconciliation. Source snapshots/partial downloads consume owned storage and comprehensive cleanup is unfinished. Blocking network/native codec calls are not guaranteed to stop immediately on cancellation.

## Media-specific boundaries

Media links/format metadata are analyzed on-device through the declared runtime;
there is no conversion server. Source-required CDN/API requests still use the
network. Signed links and browser candidates are not persisted in job settings;
process death requires a fresh analysis. The browser does not export login
cookies. Single finite resources only: no playlist manager, bounded live recorder,
arbitrary blob capture, impersonation transport or encrypted-source bypass is
implemented. The current transport adapter depends on yt-dlp internal interfaces
and must be exercised with the actual packaged payload before release.

Only resolutions reported by the source are offered; a changed format fails
instead of silently downgrading. Audio encoding bitrate is not a promise of
better source quality. Media retries restart their owned unpublished workspace;
this is not continuous pause/resume. Direct-file transfers retain the separately
tested conservative range/validator resume behavior. The media workspace has an
8-GiB polling limit and a minimum-free-space guard, not a kernel disk quota.
Long-running Android jobs, codec/container/device support and native initialization
still need qualification. APK updates refresh the packaged extractor in source,
but that update path has not run here. Final review is an author self-review.
