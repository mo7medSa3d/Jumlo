# Engine, capability and integrity report — 2026-10-17

## Status and evidence boundary

Implementation is integrated locally. Standard capability availability is now explicit and independent of DEBUG. This is **not a release-qualification claim**. Android/device output behavior, provider failures, force-stop, actual process kill, full disk, HEIC/OEM codecs, API 26/API 36 and accessibility/device behavior remain dependent on native/device evidence recorded by the other workstreams. The requested exit criterion of complete device-backed coverage for every advertised release action is not met merely by these host tests.

Final complete host command observed:

```sh
PATH=/workspace/scratch/2b70f1dcaa30/toolchains/jumlo/kotlinc/bin:$PATH bash scripts/verify-host.sh
```

**PASS (exit 0)**, using Kotlin 2.0.21 and authorized local loopback sockets. Raw log: `engine-copy-green-full.log` (post-review final). It records **108 core/real-file/process/HTTP tests passed, 0 failed**, followed by **8 media-bootstrap tests, 9 SQLite storage tests, 12 source-consistency tests**, and all four launcher/offline-preflight cases passing. Scoped `git diff --check` also passed for the owned changes.

Regression evidence is retained: `engine-regressions-red.log` reproduced Unicode publication recovery failure; `engine-resume-red.log` recorded 101 passed / 1 failed for stale-offset resumed-transfer retry; `engine-unicode-archive-red.log` reproduced Unicode-equivalent ZIP collisions. All three now pass in the final full suite. The earliest attempt was blocked because Kotlin CLI was absent (`engine-policy-red.log`). Initial 12 HTTP failures were `SocketException: Operation not permitted` from sandbox loopback restrictions; the authorized local-socket reruns passed. Those raw logs remain preserved rather than being overwritten with success claims.

## Capability policy and interfaces

- `core/CapabilityPolicy.kt` is the common audit surface, returning `visible`, `enabled`, nullable `reason`, `kind`, and evidence category. Unknown IDs fail closed.
- `CapabilityRegistry` exposes `availability(id)`, `visible(id)`, `enabled(id)`, `reason(id)`, and `canProcess(id)` for UI and dispatch.
- Navigation-only PDF viewer, storage explorer, download settings, and managed trash are enabled navigation routes. `canProcess` rejects them, preventing accidental jobs with no real handler.
- Enqueue and dispatcher both enforce `canProcess`. Source/UI work uses the same availability wrapper.
- PDF redaction/protection, encrypted ZIP, Google Drive, extended media, vault, searchable OCR and webpage rendering remain hidden and blocked. Unimplemented scan and unknown tools are also blocked. OCR/webpage were additionally gated after concrete silent text-loss and WebView lifecycle/security findings; fixing a subset of their source defects is not treated as qualification.
- Host evidence categories describe core algorithms only for archives, hashing and direct transfer. Native transformations are `SOURCE_IMPLEMENTED`; navigation remains `DEVICE_PENDING`. There is no new DEBUG bypass or release-ready assertion.

## Concrete defects repaired

### Publication, retries and trash

- Publication now normalizes Unicode display names before reserving/journaling. Previously a decomposed name could be published under one path but recovered under a different normalized path. A real-file regression reproduced the failure and now passes.
- `OutputStore.find` rechecks actual published length and SHA-256 before returning an existing successful result. A stale Room row no longer lets missing/corrupted output be reported as a retry success.
- New `ManagedTrashStorage` stores payload under a fixed separate name. Old trash could overwrite an output named `record.json` or `journal.properties` with its own metadata.
- Durable TRASH/RESTORE intent is written and synced before movement. Recovery completes interrupted file/journal movements; restore intent is retained until the Room record is acknowledged. Tests reconstruct prepared/interrupted transition states and reopen storage. These are process-boundary simulations, not claims of killed Android processes or power-loss tests.
- Existing v1 trash is migrated only after its identity/bytes match the stored digest. Existing corrupt/colliding legacy entries fail closed and are preserved; bytes already overwritten by the old implementation cannot be reconstructed.
- `purgeTrashIds(Set<String>)` only deletes the confirmed ID snapshot. It validates canonical UUID/path identity, rejects symlinks and inconsistent states, and checks payload size/hash before deletion. The old bulk/null deletion entry point now rejects requests. New items added after confirmation cannot be swept into a broad purge.
- Output publication/trash mutations are serialized in the singleton store. Source originals are never moved into managed output trash.
- Hash-handler retries now skip existing results instead of appending a duplicate outcome and rerunning the same output.

### Transfers and source intake

- A resumed transfer which appended bytes and then encountered an I/O interruption no longer automatically retries the original stale offset. It preserves appended bytes and the original I/O failure so a subsequent user retry can use the current length and validated journal. A regression failed before the fix and passes after it.
- Existing real loopback fixtures cover byte-exact GET/resume, unknown length, retryable 503, changed validators, incorrect 200-to-range, redirects/final-URL binding, truncation, 404 and streamed byte budgets.
- Cleartext consent defaults to false in workspace drafts; HTTP requires explicit consent. Structural PDF draft names now end in `.pdf` rather than `download.bin`.
- Intake cleanup checks whether a cancelled DAO operation already committed before deleting a new immutable snapshot. Uncertain database state preserves bytes. Source parsing/decoding still validates actual structures; MIME metadata is not substituted for bytes.

### Images and bounded processing

- Join/stitch accounts for all resident source bitmaps, explicitly copies before recycling decoded wrappers, and accounts for sources during output encoding. Previously immutable `createBitmap(source)` could alias a soon-recycled bitmap, and aggregate resident memory was unbounded by the per-image budget.
- Logo watermarking budgets source + logo + output, applies the selected opacity, handles small canvases, and rejects missing watermark content rather than producing an unchanged success. Compare-image decode accounts for both images; comparison uses bounded scanlines and includes alpha.
- Image extras and batch rename rethrow cancellation. Publication checks outer cancellation after blocking image work. Rename copying is chunked with cancellation and disk/byte checks.
- EXIF attribute-write errors are no longer swallowed. Impossible image target-size requests fail explicitly rather than returning an oversized result as if the target were reached.
- Hardcoded `verifiedFixtureIds` were removed from codec capability declarations; source support is not fabricated execution evidence.
- PDF image downscaling checks decoded byte allocation before constructing the raw byte array.

### Provider operations and gated OCR

- Document listings expose root write availability and actual provider rename/delete support flags. Mutations revalidate the flags. Delete is scoped to an entry in the selected tree.
- Copy first resolves KEEP_BOTH names through `CopyDestinationPolicy`, so a same-folder copy receives a distinct name. Same-document moves and destructive REPLACE collisions are rejected because SAF offers no general atomic-replacement guarantee. The post-create provider URI collision guard remains in place. It preserves a verified destination when source deletion fails. Provider metadata is read before deleting a move source, preventing a metadata failure from destroying both copies.
- Internal OCR uses the selected DPI, propagates cancellation, and no longer substitutes/truncates characters or swallows invisible-text failures. A bounded text policy rejects text unsupported by the bundled font; complete text output remains available when the PDF step fails. OCR and webpage routes remain blocked until native integrity/security qualification.

## Tests added or expanded

Host/core tests cover capability gates/navigation distinction, Unicode publication recovery, Unicode archive collisions, inert Unix symlink ZIP members, trash reserved filenames, exact-ID purge/path traversal, corruption refusal, interrupted trash/restore transitions, interrupted resumed transfer, and bounded lossless OCR text acceptance. Existing real-file archive roundtrips, malicious traversal, budgets, cancellation and durable publication tests were rerun.

Android regressions were added to `ImagePixelTest` (alpha comparison, aggregate join budget, logo opacity/small-image behavior) and `SourceAndPublicationTest` (reserved-name trash byte-exact restore and missing published output rejection). JVM `WorkspaceSettingsTest` checks explicit cleartext consent and PDF names. **Device execution is not claimed here.** Native compilation/unit/device outcomes belong to `build-report.md` and its retained raw logs.

## Owned changes

Core: `CopyDestinationPolicy.kt`, `CapabilityPolicy.kt`, `ManagedTrashStorage.kt`, `OcrTextPolicy.kt`, `Publication.kt`, `StreamTransfer.kt`, `Validation.kt`, `FileProcessing.kt`.

Engine/data: `data/jobs/CapabilityRegistry.kt`, `JobStore.kt`, `OperationDispatcher.kt`; `data/output/OutputStore.kt`; `data/files/DocumentTreeStore.kt`; `data/processing/BitmapTransforms.kt`, `ImageOperationHandler.kt`, `ArchiveOperationHandler.kt`, `FileExtrasHandler.kt`, `OcrWebHandler.kt`; `data/pdf/PdfBoxBackend.kt`; `data/source/SourceStore.kt`; `model/WorkspaceDraft.kt`.

Tests: `hostTest/kotlin/CoreTests.kt`, `TransferTests.kt`, `TrashTests.kt`, `CopyDestinationTests.kt`; Android `ImagePixelTest.kt`, `SourceAndPublicationTest.kt`; unit `WorkspaceSettingsTest.kt`.

## Remaining limits

No assertion is made of 100% coverage or release readiness. Missing evidence includes real Android process death during Room/publication transitions, cancellation under provider backpressure, disk-full behavior, revoked SAF grants, cloud/remote provider errors, exhaustive scanned/digital/protected/corrupt PDF fixtures through actual workspaces, HEIC on supported devices, every exposed native option's pixel/structural invariants, and complete reopen/share/export recovery. Gated features require independent qualification before changing policy. Existing source originals remain immutable snapshots; no source deletion was added without the separate move/delete consent flow.


## Independent-review correction: same-folder Copy

The independent reviewer correctly found that the newly added source/destination guard rejected the visible same-folder KEEP_BOTH Copy action before unique-name selection. The guard was extracted into `CopyDestinationPolicy` and reproduced by real-file host tests: `engine-copy-red.log` records 106 passed / 2 failed, including `SOURCE_DESTINATION_IDENTICAL` for the requested same-folder copy and its SKIP case.

The corrected policy permits same-folder copy, selects an unused case-insensitive name first, rejects same-document move, returns no operation for SKIP, and continues rejecting destructive replacement. `DocumentTreeStore` consumes this policy before provider creation and keeps its separate post-create guard against a provider returning the source or any existing document. Real local fixtures verify distinct output naming, output/input byte equality, unchanged source SHA-256, unchanged pre-existing-copy SHA-256, and blocked replacement/move behavior. These prove the production destination policy and real local file invariants; they do not claim Android SAF provider execution. The full post-fix `bash scripts/verify-host.sh` command passed (exit 0): `engine-copy-green-full.log` records **108 core tests passed, 0 failed**, followed by all auxiliary suites passing. The prior pre-review full run (`engine-full-host-final.log`) recorded 105 passed / 0 failed; it did not yet cover this reviewer finding.

## Target-size draft correction and independently executed JVM settings tests

Root/UI review identified that target-size processing always selects JPEG and searches quality internally, yet `WorkspaceDraft.executionSettings()` evaluated the generic conversion settings. A stale hidden quality string could therefore reject the request despite having no effect on processing. A direct run of production `WorkspaceDraft` and generated serializers reproduced `INVALID_NUMERIC_SETTING` (`engine-target-draft-red.log`).

The `img_target` branch now constructs `ImageConversionSettings(format="JPEG", quality=85, namePrefix=prefix)` explicitly, preserving the requested byte target and prefix while ignoring irrelevant hidden format/quality state. `WorkspaceSettingsTest.targetSizeIgnoresStaleHiddenFormatAndQuality` covers this behavior.

**PASS: all six `WorkspaceSettingsTest` JUnit tests executed**, independently of Android Gradle, using Kotlin 2.0.21's serialization compiler plugin, actual kotlinx.serialization JSON/core 1.7.3 and JUnit 4.13.2. The compiled sources were production `Validation.kt`, `CapabilityPolicy.kt`, `WorkspaceDraft.kt`, `Settings.kt` and the existing JVM test class. Invocation was `org.junit.runner.JUnitCore com.jumlo.app.model.WorkspaceSettingsTest`; raw result `engine-workspace-junit-green.log` records `OK (6 tests)`. This also directly verifies opt-in cleartext defaults, PDF output-name defaults, and resize validation. It is JVM execution evidence, not Android/device evidence. The full host suite remains the post-review 108-pass run because this final model-only change is outside that suite's compiled source set.
