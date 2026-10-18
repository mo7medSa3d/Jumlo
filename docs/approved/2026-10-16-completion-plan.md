# Jumlo Complete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline implementation after this plan is reviewed. Steps use checkbox (`- [ ]`) syntax for tracking. If an independent reviewer is available later, use it; do not invent subagent execution or independent review.

**Goal:** Replace the uploaded prototype's fabricated operations with real, recoverable native Android features and return one honestly verified source ZIP.

**Architecture:** Preserve usable Compose layouts and route identities while replacing the simulated repository with source, job and output services. Typed per-tool settings feed real operation handlers; Room owns durable state, and file publication uses a recoverable journal. Structural PDF edits use a separately qualified offline backend rather than bitmap substitution.

**Tech Stack:** Kotlin, Compose, Hilt, Room, DataStore, platform graphics/SAF, OkHttp, WorkManager and task-appropriate transfer execution; qualified permissive PDF/cryptographic/archive adapters where required.

**Planning status:** Design approved; implementation plan review pending. No source repair or Android build is claimed.

**Spec:** `../specs/2026-10-16-completion-design.md` (approved by the user's message "approve complete all work"). The copied spec is unchanged historical source; this document is the subsequent implementation plan, awaiting review.

## Global Constraints

- Native Kotlin/Jetpack Compose, one Android application, minimum API 26; independent native iOS remains later work.
- Preserve the four pillars, Hybrid Workspaces, English/Arabic and Light/Dark; no AI/OCR, cloud conversion, WebView replacement, mandatory account or platform scraping.
- Preserve originals. Never fabricate inputs, HTTP success, progress, output files or verification evidence.
- Build candidate: JDK 17, AGP 8.10.1, Gradle 8.14 `-bin`, Kotlin/Compose compiler plugin 2.0.21, KSP 2.0.21-1.0.27, compile/target SDK 36, Build Tools 36.0.0. Retain other pins unless an evidenced failure/security finding requires a change.
- No installs/downloads on the user's Fedora machine without itemized approval; no paid/AGPL dependency, NDK, emulator, new remote repository, cache deletion or dependency refresh by default.
- No credentials, private files, absolute SDK paths, SDK/Gradle caches, or font binaries in the final ZIP.
- Build-verified, device-verified, security-reviewed and release-ready are separate evidence states. Missing checks block the corresponding completion claim.

## Review Focus

1. A provider revokes access or returns non-seekable/truncated bytes: keep the draft and originals, and report the actual failed input. Owned by F02/F04/A01.
2. A process stops between output close, file publication and database recording: recovery publishes at most one verified result. Owned by F03/D02/U03.
3. Reused resources, page rotation/boxes, forms, transparency or unsupported PDF filters: preserve supported content or reject before mutation. Owned by P01/P02/E01/E11.
4. A hostile server, archive, filename or oversized image supplies misleading metadata: enforce limits while reading actual bytes, not only during preflight. Owned by D01/A02/I01.
5. Authentication expires, an encrypted file is tampered with, or locale/theme changes during work: sensitive bytes stay protected and ordinary drafts stay coherent. Owned by E25/U02.

## Current evidence and execution boundary

Source ZIP SHA-256: `0b4b618f2a003fd66abe801cd2369b75eaf064a7d94389c4bf28db9efd9e4c99`; 55 file members. The actual archive was read again for this plan. Existing code has 30 production Kotlin files and two Kotlin test files, missing wrapper JAR, fabricated engine output and seeded repository data. This is not an incremental polish of a working product.

The available container has Java/Javac and Kotlin CLI on PATH. No `gradle`, `sdkmanager` or `adb` command was found, and Android SDK environment variables are unset. That is not the user's Fedora installation and does not prove that the user's tools are missing. Do not assume access to `/home/mo7amed_saad/Projects/UniversalUtilities`. No Android compilation or product implementation has occurred in this planning stage.

Execute in an isolated copy/version-control branch based on the source ZIP, retaining an untouched original. No force-push, remote repository change, user-machine installation, purchase or store publication is authorized by this plan. Inline execution here is the recommended method; the user reviews this plan before execution begins.

## Subsystem plans and order

| Order | Plan | Task IDs | Independently testable completion boundary |
|---|---|---|---|
| 1 | [Foundation and first real PDF](subsystems/01-foundation.md) | F01-F04 | Trusted build; actual intake; durable publication; Images-to-PDF round trip |
| 2 | [PDF core](subsystems/02-pdf.md) | P01-P03 | Qualified backend; text-preserving structural operations; viewer and image exports |
| 3 | [Images](subsystems/03-images.md) | I01-I02 | Real conversion/compression and geometric operations, including batches |
| 4 | [Files and archives](subsystems/04-files.md) | A01-A02 | Actual provider-aware mutations and bounded ZIP round trips |
| 5 | [Downloads](subsystems/05-downloads.md) | D01-D02 | Streamed HTTP, safe resume, durable queue and OS-aware recovery |
| 6 | [Full expansion inventory](subsystems/06-expansions.md) | E01-E25 | All 25 families have implementation/testing owners; security gates are explicit |
| 7 | [Whole-app integration and delivery](subsystems/07-handoff.md) | U01-U03 | Live UI wiring, localization, real-device gates and source package |

Run U01's shared visual work alongside F04 only after its dependencies exist; finish U01/U02 after the feature handlers are available. A dependency block does not authorize replacing an engine with a stub and calling the feature complete.

## Source-aware file map

All Kotlin paths below are under `app/src/main/java/com/jumlo/app/` unless explicitly rooted elsewhere.

| Existing area | Planned ownership/change |
|---|---|
| `model/Models.kt` | Keep display enums only where useful; move persistent identities/settings/outcomes to `model/OperationContracts.kt` and feature settings files |
| `data/repository/UniversalRepository.kt` | Remove seeded data and monolithic fake execution; replace with `data/source/SourceStore.kt`, `data/jobs/JobStore.kt`, `data/output/OutputStore.kt` and thin feature repositories |
| `data/local/AppDatabase.kt` | Database v2, explicit migration from v1, exported schema, raw metadata and unique output identity |
| `data/engine/PdfEngine.kt`, `ImageEngine.kt` | Real handlers using input streams, typed settings and verified staging outputs |
| `data/engine/DownloadEngine.kt` | Real HTTP transport only; scheduling in `data/download/DownloadCoordinator.kt` |
| `data/engine/ArchiveAndFileEngine.kt` | Real bytes and bounded archive operations; provider mutations in `data/files/DocumentOperations.kt` |
| `data/engine/CryptoVaultEngine.kt` | Replace UI-flag security with authenticated key access and reviewed streaming file encryption |
| `ui/AppViewModel.kt` | Stop unconditional completion callbacks; feature state holders observe persisted job/result IDs |
| `worker/EngineOperationWorker.kt`, `service/TransferForegroundService.kt` | Real dispatch and progress; no timed simulated success |
| `ui/screens/*`, `ui/navigation/JumloNavHost.kt` | Preserve visual investment; replace fixture-backed controls and result routing |
| `ui/theme/*`, `ui/components/QuietPrecisionComponents.kt` | Reconcile exact approved tokens, scalable controls and authentic filled symbol assets |
| `MainActivity.kt`, manifest, preferences, resources | Real share intake, permissions, app locales, first launch and backup exclusions |
| `gradlew*`, `gradle/*`, build scripts | Official verified wrapper and compatible locked graph; buildable from CLI |

## Shared interfaces: freeze these names before implementation

F02 and F03 create the following contracts. Later plans consume them unchanged. File and job IDs are opaque UUID strings; raw filenames never become identity.

```kotlin
// model/OperationContracts.kt
// Each of the data classes below is serializable where stored as JSON.
data class SourceRef(
    val id: String, val uri: String, val displayName: String,
    val mimeType: String?, val sizeBytes: Long?,
    val readable: Boolean, val writable: Boolean, val persistedReadGrant: Boolean,
    val modifiedAtEpochMs: Long?
)
data class JobRequest(
    val id: String, val toolId: String, val sourceIds: List<String>,
    val schemaVersion: Int, val settingsJson: String, val submittedAtEpochMs: Long
)
enum class JobStage { VALIDATING, QUEUED, RUNNING, FINALIZING, COMPLETED,
    PARTIAL, FAILED, CANCELLED, INTERRUPTED, WAITING_FOR_INPUT }
data class JobProgress(val stage: JobStage, val completedUnits: Long, val totalUnits: Long?)
data class OutputMetadata(val pageCount: Int? = null, val width: Int? = null,
    val height: Int? = null, val entryCount: Int? = null)
data class OutputTicket(val jobId: String, val itemKey: String,
    val stageFile: java.io.File, val displayName: String, val mimeType: String)
data class OutputRecord(val id: String, val jobId: String, val itemKey: String,
    val uri: String, val displayName: String, val mimeType: String,
    val sizeBytes: Long, val sha256: String, val metadata: OutputMetadata,
    val createdAtEpochMs: Long)
enum class ItemStatus { CREATED, FAILED, CANCELLED, SKIPPED, UNCHANGED, LARGER }
data class ItemOutcome(val itemKey: String, val status: ItemStatus,
    val outputId: String? = null, val errorCode: String? = null)
```

`SourceStore.inspect(uri: String, offeredGrantFlags: Int): SourceRef`, `openRead(sourceId: String): InputStream` and `withSeekable<T>(sourceId: String, jobId: String, block: suspend (File) -> T): T` are suspend functions. Their caller closes returned streams; the seekable helper owns temporary files and enforces disk budgets. URI grants are persisted only when offered. No guessed `file://` conversion for provider content.

`OutputStore.reserve(jobId: String, itemKey: String, displayName: String, mimeType: String): OutputTicket`, `publish(ticket: OutputTicket, validate: suspend (File) -> OutputMetadata): OutputRecord`, `discardIncomplete(jobId: String): Unit` and `reconcile(): Unit` are suspend functions. `publish` closes/validates staged data, computes actual bytes/hash, journals publication, and enforces unique `(jobId,itemKey)` outputs. Repeat publication returns the existing matching output; a different digest for the same key is an integrity error, not an overwrite.

`JobStore.enqueue(request: JobRequest): String`, `cancel(jobId: String): Unit` and `retryFailed(jobId: String): String` are suspend functions; `observe(jobId: String): Flow<JobSnapshot>` observes durable state. `JobSnapshot(request, progress, outcomes, updatedAtEpochMs)` includes the fields above. Retrying failed items uses stable logical output keys and must not reproduce successful members. A new settings revision gets a new job identity.

`OperationHandler.execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome>` is suspending. `ExecutionContext(sources: SourceStore, outputs: OutputStore, budget: WorkBudget, report: suspend (JobProgress) -> Unit, checkCancellation: () -> Unit)` supplies the only IO/progress publication services. The dispatcher matches `toolId` and decodes a versioned, tool-specific settings data class; unknown schema/tool values fail closed.

`WorkBudget(maxBitmapBytes: Long, maxTemporaryBytes: Long, maxExpandedArchiveBytes: Long, maxArchiveEntries: Int, minFreeBytes: Long)` is injected. The default bitmap allowance is `min(Runtime.maxMemory()/3, 128 MiB)` for aggregate live work, not per bitmap. Reserve 32 MiB free disk, default 10,000 archive entries and depth 32, and cap expanded bytes at the smaller of 2 GiB and current available space after reservation. UI explains limits; tests inject much smaller budgets. No hidden output downscaling to fit a budget.

All IO failures are stable error codes rendered as localized copy; exception logs exclude credentials and document content. Cancellation is rethrown after safe cleanup and never becomes a successful `Result`.

See [CONTRACTS.md](../../../CONTRACTS.md) for the exact supplemental value types and secret/session rules.

## Build/test command contract

F01 implements `scripts/verify-local.sh` as a no-network preflight plus a launcher of cached Gradle. It checks the actual distribution first, because the Wrapper may bootstrap before `--offline` takes effect [R2]. Set `GRADLE_BIN` to a trusted cached 8.14 executable when necessary. Missing tools/cache are reported with `BLOCKED` and nonzero exit before network access; do not declare tests passing.

After the preflight, commands in the child plans mean real Gradle invocations:

- JVM: `scripts/verify-local.sh :app:testDebugUnitTest --tests 'fully.qualified.TestClass'`
- Android: `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=fully.qualified.TestClass`
- Build/lint: `scripts/verify-local.sh :app:assembleDebug :app:lintDebug`

The script always supplies `--offline --console=plain`, preserves exit codes/logs and never runs `clean` or `--refresh-dependencies`. Online dependency resolution is a separate approved action with exact artifacts and estimated transfer costs. Device tasks require a real authorized device; no emulator is installed by this plan.

## Fixture and evidence rules

Use Kotlin tests against production code, with test-only fixture helpers under the corresponding `test`/`androidTest` source sets. Generate small known images/PDFs/archives during test setup; no personal documents or font binaries. Fixture PDF pages contain unique text tokens (`FIRST`, `SECOND`, `THIRD`) and distinct non-text marks; selected text, page order and visible marks must survive. Bitmap fixtures encode position in color so crop/rotation errors are observable, not just dimension differences. Each test captures source hashes before/after.

All tasks use red/green checks. A missing toolchain is BLOCKED, not the expected red regression. Log actual commands, exit codes and artifact paths in `verification/`. No lines-of-code count or delimiter scan substitutes for compilation. Record security-sensitive features separately and do not enable a release capability before its tests pass.

## Full scope and delivery

The [coverage register](../../../coverage.json) maps the 16 V1 tool entries and every XP/XI/XD/XF family to implementation tasks. Shared screens also have owners. Feature implementation and availability are distinct: an unsupported codec is reported honestly, while a missing required feature remains unfinished. Security gates are not permission to advertise incomplete functionality as done.

The final application archive is created only by U03 after evidence review. Include source, official wrapper, migrations, tests, instructions, notices, capability register and real verification results. Exclude SDK/Gradle caches, fonts, credentials and local SDK paths. An APK is included only when actually built, inspected and checksummed. No app ZIP is produced by this planning delivery. This plan set is reviewed together once; tasks do not require repetitive scope approvals, but a destructive action, paid dependency or material backend change still needs an explicit decision.

## Reference decisions

References and current primary-source checks are in [SOURCES.md](../../../SOURCES.md). API 36 is the approved project target; this plan makes no unverified claim about the current Play submission deadline. A PDF backend, Tink and archive adapter are dependency candidates with explicit qualification tasks, not existing installed dependencies. Do not let reference browsing trigger downloads of SDKs or application libraries.
