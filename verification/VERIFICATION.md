> Historical baseline report from the previous source handoff. Current media extension and updated task accounting: `media-upgrade/VERIFICATION.md` and `TASK_STATUS.md`. The counts below describe the earlier snapshot only.

# Universal Utilities - implementation and verification evidence

Date: October 16, 2026. Exact UTC start times, command arrays and exit codes are in `verification/final/*.log` and `verification/final/results.json`.

## Delivery status

**Modified Android source candidate; partial implementation. No Android-build, device, security or release qualification is claimed.**

The task matrix has 41 approved tasks: **16 partial/unqualified and 25 not implemented/gated**. No task is represented as meeting its complete planned qualification boundary. Missing implementation is separate from unavailable native verification. The unavailable Android environment does not mean the remaining feature code exists.

The source replaces the prototype's synthetic operations with actual source/job/output services and real byte-processing adapters. This handoff contains code, tests, logs and a source package, not an APK. The complete approved product plan remains unfinished.

## Checks actually executed

| Check | Actual result | Scope / evidence |
|---|---|---|
| Kotlin core/domain/HTTP suite | 52 passed, 0 failed | `verification/final/core-domain-http.log` |
| Production DAO/migration SQL with host SQLite | 8 passed | `verification/final/storage-sql.log`; NOT Room qualification |
| Narrow static source consistency checks | 8 passed | `verification/final/source-static.log`; NOT Android lint/compilation |
| Offline preflight regression | 2 passed | `verification/final/offline-preflight.log` |
| Wrapper launcher safety regression | 2 passed | `verification/final/launcher-safety.log`; does not validate an absent binary |
| Staged and unstaged source diff whitespace | Exit 0 | `verification/final/source-whitespace.log`; not semantic verification |
| `:app:assembleDebug` | BLOCKED, preflight exit 2 | `verification/final/assemble-debug.log`; Gradle never started |
| `:app:testDebugUnitTest` | BLOCKED, preflight exit 2 | `verification/final/gradle-unit-tests.log`; Gradle unit tests NOT executed |
| `:app:lintDebug` | BLOCKED, preflight exit 2 | `verification/final/android-lint.log`; Android lint NOT executed |
| `:app:connectedDebugAndroidTest` | BLOCKED, preflight exit 2 | `verification/final/android-instrumentation.log`; device tests NOT executed |
| Wrapper launch/version request | BLOCKED, exit 2 | `verification/final/wrapper-launch.log`; official JAR absent |
| Explicit official-wrapper restoration | Failed to resolve host, curl exit 6 | `verification/wrapper-restoration-attempt.log`; no binary obtained |

The host Kotlin suite compiles the actual `core/*.kt`, `model/Models.kt`, `data/engine/PureDomainAlgorithms.kt` and the host test sources. It does not use fake Android classes. It exercises real ZIP files, actual stream hashing and publication/recovery, page-range and geometry calculations, policy checks, and 11 real local HTTP server cases. It does NOT execute Android bitmap codecs, PdfDocument/PdfRenderer, Compose, SAF, Hilt, WorkManager or Room.

The SQL tests extract and execute the actual SQL strings from the Kotlin DAO/migration sources. They check lease ownership, cancelled-state protection, safe retry after cleanup, unique output identity, separate snapshots for the same URI and retained preexisting rows. They do not validate Room's generated schema/migration identity, KSP output or Android SQLite behavior.

The static checks cover XML parsing, referenced default strings, duplicate resource names, removal of the old synthetic engine classes, dispatcher gating, FileProvider scope, a non-exported cancel receiver and absence of font binaries. They are not a substitute for compilation or security review.

## Environment and build blockers

The runner provided Kotlin CLI 1.9.0 and OpenJDK 21.0.12.1. The Android project is pinned to Kotlin 2.0.21 and a JDK 17 build baseline. Host compilation targets JVM bytecode 17; that does not qualify the native compiler/plugin graph.

No usable Gradle 8.14, Android SDK/Build Tools or connected device was available to the verification scripts. `gradle`, `sdkmanager` and `adb` were not on PATH; the Android SDK environment was not configured. These observations concern this execution runner, not the user's computer. No installations, remote repository creation, push, paid service, emulator or user-machine mutation occurred.

The uploaded launcher was broken and the uploaded wrapper JAR was missing. Corrected, hash-verifying project launchers and explicit restoration scripts are supplied. The official wrapper binary is still absent because its download host could not be resolved. Do not report the wrapper as fully restored. The approved SHA-256 pins and exact build requirements are in `docs/dependencies.md`.

The evidence recorder exits 2 while any requested check is blocked; exit 0 is reserved for a run with no failed or blocked checks. See the exact per-command results rather than treating the recorder itself as a build.

## Implemented source boundary

The native candidate includes real URI intake with immutable app-private snapshots; typed, versioned requests and drafts; durable Room job state; a recoverable filesystem publication journal with validated metadata; actual bitmap/PDF adapters; actual ZIP/hash and streamed-download adapters; a foreground WorkManager worker and persisted cancellation; real source/job/output UI flows; granted-folder operations; and exported-file read-back hashing.

Candidate tools are Images to PDF, PDF preview, PDF to images, four image operations, direct/batch downloads, folder browsing, ZIP create/extract and SHA-256 verification. These descriptions identify code paths, NOT device-verified features. Release routes and dispatch are gated, while debug builds permit exercising implemented candidates. No unimplemented card reaches a simulated success fallback.

Several instrumentation tests were authored for source snapshots/publication, bitmap geometry, archive adapters and foreground notification safety. A Gradle unit test covers typed resize settings. They have not run. Additional planned tests still need to be written, as recorded in `TASK_STATUS.md`.

## Observed regressions fixed

Historical red/green logs are retained. Do not add their counts together; they are reruns of evolving suites.

- Conflicting publication retries previously left disposable staging that broke later recovery. The rejected stage is removed while the original digest/output remains intact.
- Metadata is now journaled before file publication; recovery does not infer media validity from a raw download/archive member's claimed MIME type.
- Case-insensitive keep-both naming, padding without upscaling, and ZIP directory payload accounting each had observed failing host cases before the corrected behavior passed.
- Fabricated legacy HTTP status/size defaults were removed. Malformed hosts, reversed ranges, scheme normalization and overflowing range endpoints have passing regression coverage.
- Resume rejects 200-to-Range, invalid/changed validators, bad ranges, truncation and budget excess. It now also rejects a changed final redirect target, even when that target happens to use the same ETag and length. Only the final URL's digest is persisted.
- The production SQL retry query failed its regression while a prior owner still held a lease. Retrying now requires the lease to have been released, preventing overlap with old-worker cleanup.

The first launcher-test draft incorrectly used an AND-list under shell errexit and produced an invalid apparent pass. `launcher-invalid-harness.log` is NOT accepted evidence. The corrected harness then demonstrated the original launcher's unsafe JVM execution and passed after the launcher changes. Early Kotlin syntax/classpath failures likewise do not count as behavioral red tests. Their distinction is recorded in `progress.md`.

A bare-kotlinc whole-source diagnostic without Android dependencies is archived under `verification/exploratory`; it failed and is explicitly non-qualifying. Native compilation/integration defects may still be present.

## Remaining product and qualification gaps

Structural PDF merging/splitting/organizing/extraction and the qualified preservation backend are missing. Advanced PDF editing, forms, signatures, redaction, camera capture, most image expansions, scheduled/multipart and browser capture features, batch rename, exact duplicates, managed trash, encrypted archives and authenticated vault are not completed implementations.

The replacement UI reuses the original theme/components but does not retain every approved detailed screen/control behavior. English/Arabic preference switching exists, but new controls are not fully localized. No visual rendering, accessibility, device rotation/recreation, provider-failure or complete OS-version matrix was executed.

Publication recovery passed the specified host interruption/idempotency cases. It is not proven power-loss atomic: directory fsync behavior and multi-file/Room device fault injection remain unqualified; a corrupt journal can block reconciliation. Global reconciliation may rehash many outputs. Snapshot/output/partial-download retention and full cleanup controls remain incomplete. Native codec and blocking HTTP calls are not guaranteed to cancel immediately. Modern foreground/job quotas still require device qualification. These are open boundaries, not hidden passing tests.

Final review was an author self-review because no independent reviewer was available. No full dependency resolution/transitive license inventory or independent security review occurred. Vault, encrypted archive, redaction and other unqualified features remain closed.

## Rulings and consequences

| Execution decision | Consequence / cost |
|---|---|
| Work in an isolated copy and deliver a source ZIP; preserve the uploaded originals | No automatic integration into a remote repository or the user's local project |
| Use installed host tools only; do not install the Android stack on the user's machine | Native build, plugin, device and release confidence remain unavailable |
| Snapshot every imported source, including persistable URIs | Jobs use stable bytes and originals are preserved; extra owned storage is consumed |
| Keep signed URLs in memory and bind resume to original/final URL digests | Process death requires re-entry; changed or refreshed links conservatively require a new download |
| Replace synthetic routes with live workspaces while reusing theme/components | Detailed approved screen parity is incomplete |
| Expose implemented candidates only in debug; enforce release gating in dispatch too | This is not a usable release build until native qualification is completed |
| Never manufacture a wrapper JAR; provide explicit hash-verified restoration | One explicit restoration step on a suitable network-enabled build machine is still required |
| Keep notification progress indeterminate while activity progress uses real units | No notification percentage is claimed |
| Keep the isolated implementation branch/source rather than merging or pushing | Source changes remain recoverable; no remote CI run exists |

## Reproduction and package integrity

From the extracted project root, `bash scripts/verify-host.sh` reruns the available host checks without installing/downloading tools. `python3 scripts/run-verification.py` also captures requested native checks and returns an explicit blocked exit where appropriate. Its Git-only diff check is reported as NOT_APPLICABLE when run from a ZIP without repository metadata. Android setup/build commands are in `README.md`.

`verification/input-integrity.json` records the exact uploaded archive hashes. `SOURCE_SHA256SUMS` records package-member hashes, excluding itself to avoid recursion. The external ZIP SHA-256 is supplied alongside the source ZIP. Final archive CRC/member-hash verification and host replay from an extracted archive are recorded in the external package-check report. No APK, signing material, Android SDK/Gradle caches or font binaries are delivered.
