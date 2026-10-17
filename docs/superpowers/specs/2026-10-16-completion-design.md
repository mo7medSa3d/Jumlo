# Universal Utilities: Real-File Completion and Recovery Design

Date: 2026-10-16
Status: Proposed design for user review. Application implementation has not started in this review.
Target: Native Android, Kotlin and Jetpack Compose. Independent native iOS remains later work.
Delivery goal: One coherent source ZIP with reproducible build instructions and truthful verification evidence, not another simulated application.

## 1. Intent and boundaries

The user wants the uploaded Universal Utilities application completed, strengthened, and returned as a final ZIP. Preserve the four product pillars (PDF, Images, Downloads, Files), Quiet Precision identity, floating dock, filled rounded icons, English/Arabic, Light/Dark, and Hybrid Workspaces. Real user-selected files must drive every operation. No AI, OCR, cloud conversion, mandatory account, WebView replacement, unrelated audio/video editor, or universal protected-platform extractor is introduced.

The existing source ZIP is the implementation baseline. The earlier approved design specifications resolve conflicts with generated marketing copy, mock measurements, and fabricated capabilities. The user's separate Fedora/OpenCode project is not mounted here and must not be overwritten by assumption.

This is an architectural repair, not a cosmetic patch. The UI scaffold can be reused, but the contracts between selected inputs, engine execution, persistent state, results and navigation must change together.

### Completion scope

The completion target includes the entire agreed V1, its supporting flows, and the previously specified expansion inventory. V1 and expansion work remain separate release groups. Completing V1 does not silently mark 25 expansion families complete. Conversely, an unverified expansion must not appear in release navigation merely to claim that every screen works.

The first detailed subsystem to implement is the build/file/job foundation plus a real Images-to-PDF vertical slice. Subsequent subsystems depend on that foundation. Each subsystem needs its own reviewed implementation plan; the boundaries below are dependency order and acceptance contracts, not a claim that implementation has already been authorized or performed.

## 2. Observed baseline, not assumptions

Archive: `UniversalUtilities_Android_Project.zip`

SHA-256: `0b4b618f2a003fd66abe801cd2369b75eaf064a7d94389c4bf28db9efd9e4c99`

The archive contains 55 file members, 588,680 uncompressed bytes. Its ZIP CRC check succeeded. All source files were copied to a separate inspection directory and remain unchanged. The original audit was read, then critical paths were inspected again.

| Source location | Confirmed behavior | Consequence for this design |
|---|---|---|
| `gradlew`, `gradle/wrapper/` | Launcher contains malformed JVM option quoting; wrapper JAR is absent | Restore official, checksum-verified wrapper before claiming a build |
| `UniversalRepository.kt:454,481` | Images-to-PDF and Merge receive empty input lists | Replace display-only models with actual selected-source references |
| `PdfEngine.kt:173-389` | Several operations draw synthetic lecture pages instead of reading the source PDF | Replace engine behavior; do not rename synthetic output as a repair |
| `ImageEngine.kt:45-115` | Conversion creates a bitmap and draws the output name | Decode and transform input bytes; synthetic fixtures only in previews/tests |
| `DownloadEngine.kt:47-93` | HEAD inspection returns invented successful metadata on failure; no GET transfer implementation | Implement a real transfer state machine and streaming writer |
| `EngineOperationWorker.kt:22-30` | Timed progress loop returns success without processing files | Persist and dispatch actual typed operations |
| `FilesAndVaultScreens.kt:902-975` | Vault starts unlocked; button toggles unlocked state without authentication | Replace UI-only security with cryptographic authorization |

Fresh command: `sh ./gradlew --offline :app:assembleDebug`

Observed exit: 1, before Gradle started.

Observed message: `Could not find or load main class "-Xmx64m"`.

The inspection runtime has Java 21 and Kotlin CLI 1.9.0; no Android SDK or Gradle was found in the inspected environment/path locations. It is not the user's Fedora installation. No Android compilation, lint, instrumentation, runtime file processing, or APK verification succeeded during this review. No SDK, Gradle distribution, Maven dependency, or font was downloaded.

## 3. Architectural choice

| Approach | Assessment |
|---|---|
| Retain the current engines and patch their outputs | Rejected: the central data contracts are simulated; patching success messages does not make them real |
| Preserve the Compose design, replace execution/data contracts, use a dedicated offline structural PDF adapter | Recommended: preserves design investment while fixing file integrity and enabling incremental verification |
| Replace the whole application or adopt a commercial document SDK immediately | Not the default: discards reusable work or introduces licensing/cost commitments without approval |

### PDF backend decision

Use platform PDF rendering/creation where appropriate, and a separate structural PDF backend for copying/reordering pages, extraction, text access and supported document edits. Rendering existing pages to bitmaps and wrapping those bitmaps in a PDF is not accepted as a transparent substitute for structural editing. Platform API capabilities vary by API level; a newer API is not assumed to exist on the minimum supported Android version. [S2, S3]

The proposed permissive dependency candidate is an Android-compatible PDFBox adapter. The published PdfBox-Android README currently names `2.0.27.0` and an Apache-2.0-licensed main codebase; that is candidate evidence, not a blanket endorsement of its age, codecs or dependencies. [S4] Before pinning, inspect the actual artifact, transitive dependencies, applicable upstream advisories, parser resource behavior and representative documents. Upstream PDFBox explicitly documents resource-exhaustion risks. Some listed 2026 advisories affect its examples module rather than the core library; applicability must be checked rather than inferred from a version string alone. [S5]

No paid license, AGPL requirement, optional legacy codec, NDK installation or remote conversion service is silently accepted. If the candidate cannot meet preservation and security requirements, report the exact failure and review the backend choice; do not rasterize silently or ship an unsafe parser to meet a deadline.

## 4. Shared file and operation foundation

### Input identity

Create an input reference with stable ID, content URI or app-managed file ID, display name, validated media type, discovered byte length, available read/write capabilities, and grant state. Missing metadata remains unknown, not a fabricated value. File display names are never treated as trusted filesystem paths.

Photo Picker, SAF, ACTION_SEND and ACTION_SEND_MULTIPLE converge on the same input repository. Persist grants only when offered. If a provider supplies a non-seekable stream, spool it to a job-owned temporary file under a bounded disk budget. Reconnect unavailable inputs without losing settings. Android providers can be external, remote or read-only; do not claim that every selected file lives in the app sandbox. [S6]

### Durable state

Room owns managed file records, operation records, output records, download records and draft manifests. DataStore owns preferences. ViewModels observe these stores rather than seeded runtime lists. Store raw timestamps and byte counts, not preformatted English labels that become stale after a locale change. Export schemas and write migrations; no destructive migration policy for user data. Exclude sensitive source/output files, vault material, credentials and temporary content from automatic cloud backup and review device-transfer behavior separately. Local-processing promises do not automatically disable OS backup. [S12]

Each operation stores an immutable settings snapshot and input IDs, plus a unique job identity. Worker retries and duplicate UI taps must not create duplicate published results. Short-lived passwords and authentication material are not persisted in ordinary job records.

### Output lifecycle

Use `validate -> reserve name -> stage -> process -> close -> verify -> publish -> record`.

Stage each result under its job identity. Verify the appropriate invariant: parseable PDF with expected pages, decodable image with expected dimensions, finished archive with matching entry data, or completed transfer with validated byte accounting. Publish only complete outputs. Filesystem publication and Room updates require a recovery journal because they are not a single cross-system transaction.

Single-output jobs never publish partial PDFs as success. Multi-output jobs can publish verified members while recording per-item failures. Cancellation cleans this job's incomplete staging files, preserves originals, and offers an explicit choice about completed managed outputs. Coroutine cancellation is propagated rather than swallowed by a generic success fallback.

Exports to SAF providers are separate operations. Keep the internal verified result until export succeeds. A failed export retries export only. Copy/move handles partial completion; a source is deleted only after destination verification and explicit move intent. No universal atomic-move or reversible-delete claim is made.

## 5. Core feature acceptance contracts

### PDF: seven catalog entries, shared controls and states

| Tool | Real implementation requirement | Acceptance evidence |
|---|---|---|
| Viewer | Real source rendering, zoom, lazy thumbnails, page navigation, available outline, digital-text search; protected-input handling | Known text is found in text PDFs, absent text is explained, page content matches fixtures |
| Images to PDF | Real image URIs; EXIF orientation; order/crop/rotation; A4/A5/Letter/custom dimensions; Fit/Fill; margins; grouping; output settings | Page count, image identity, placement and unchanged source hashes |
| Merge | Two or more real PDFs, source ranges, preserved geometry and supported text/vector resources | Selected source page order, searchable text and rendered appearance retained |
| Organize | Real selected pages; reorder/delete/90-degree rotation; draft undo/redo; prevent empty document | Page identity/order/rotation assertions and source preservation |
| Split | Every N, each page, custom disjoint ranges; omissions visible; names and optional ZIP | Correct page groups, page totals and invalid-range rejection |
| Extract | Typed/visual selection; Arabic/Latin number normalization; deduplication; one or many outputs | Selected physical pages only, expected order, no source deletion |
| PDF to images | Real source pages, JPG/PNG, validated DPI, conditional quality, grayscale, original page numbering | Actual raster content, dimensions and output count |

Preservation has an explicit support policy for annotations, forms, bookmarks, encryption, page boxes and signatures. Reject unsupported preservation cases before writing rather than silently stripping content. Any intentional lossy transformation is separately named and disclosed. Correct password access is not password cracking. No original-text editing or OCR is inferred from an overlay-text feature.

### Images: four catalog entries, all with batch support

Convert reads actual JPEG/PNG/WebP and supported HEIC input. It exposes only supported encoders, preserves alpha in compatible formats, and applies the chosen matte when writing JPEG. PNG never gets a fake lossy-quality slider. Animated/multipage data requires explicit supported-frame handling or an unsupported message.

Resize supports longest edge, width/height, bounding box and percentage, with aspect-lock and no-upscale default. Fit, Fill/crop, Pad and Stretch have different tested geometry. Crop/rotate/flip applies normalized crop geometry to actual source pixels and preserves per-image overrides. Compression encodes a real comparison sample when showing an estimate, then measures final bytes; larger output is a legitimate reported result, not fake savings.

Decode bounds before allocation; bound active bitmap memory; use preview-resolution work for previews and source-resolution work for output. Normalize orientation before selectively stripping identifying metadata. Do not discard essential color handling under a privacy label. Wide-gamut/HDR and codec limitations must be explicit.

Batch scope is always visible. Each result reports Created, Failed, Cancelled, Skipped or Unchanged where applicable. The 18-success/2-failure screen is a test fixture only; production counts are derived from actual jobs.

### Downloads: direct and batch

Implement streamed GET requests with real filesystem output, persisted queue, notification state, cancellation, restart and conditional resume. HEAD is optional metadata discovery, never proof of successful transfer. Unknown size remains unknown. Failed network access never returns synthetic HTTP 200, TLS, ETag or size values.

Resume validates representation identity and received range boundaries. Handle 200-to-Range safely, 206 with correct Content-Range, 416, missing validators, changed resources and expired authorization. An ETag is an HTTP validator, not automatically a cryptographic digest. [S7] Do not forward credentials across origins or bypass certificate checks. Explicit cleartext support requires per-transfer consent and no insecure credential handling.

Default to two concurrent files with one stream per file. Multipart acceleration is a separate extension, not mandatory four-stream telemetry. Separate Wi-Fi-only from unmetered-only policies. Show actual bytes/speed and an indeterminate state when length is absent. Use task-appropriate Android background APIs with distinct behavior for system interruption, user cancellation, force-stop and reboot. [S8]

### Files: browse, ZIP, unzip plus contextual operations

Browse managed results/downloads and user-granted folders. Implement search/sort/list/grid, real metadata, new folder, rename, multiselect, compatible contextual tools, copy/move/delete, export and share. Clearing history, deleting files, clearing drafts and clearing safe cache are distinct operations with separately tested effects.

ZIP creation streams selected file bytes and never substitutes descriptive text. Extraction enforces sandbox confinement, safe paths, duplicate-entry/collision rules, expanded-size/entry-count budgets, cancellation and verified entry completion. Do not trust archive header sizes as the only resource limit. Reject traversal, absolute paths and symlink escape; output-name validation also protects generated filenames. Keep-both is the default; Skip does not overwrite; Replace requires explicit scope.

## 6. Expansion work remains explicit

The three expansion labs already present in source are included in the completion effort, not accepted as implemented just because their screens exist:

- PDF compression/watermark/numbering: transform the actual document, preserve supported text/vector content, disclose image downsampling, and measure output size. Do not include arbitrary AcroForm flattening without a tested supported-form policy.
- Image collage/watermark: compose the selected source pixels with real layout, gutter, ratio, crop and alpha-aware overlay settings; validate extreme output dimensions.
- Local encrypted vault: start locked; perform cryptographically authorized unlock; encrypt actual files with authenticated encryption, persist required metadata safely, detect tampering, recover coherently after interruption and relock on defined lifecycle/timeout boundaries. Hardware backing is detected and described truthfully. A Compose boolean is not an authentication boundary. [S9]

The remaining approved expansion inventory is retained, grouped as follows. It is not silently represented as a few generic lab screens:

| IDs | Family contracts retained for separate subsystem designs |
|---|---|
| XP-01..11 | PDF optimization; crop/page geometry; annotation; watermark/numbering; drawn signature; authorized protection/removal; metadata/insertion; manual document capture; supported forms/flatten; non-AI comparison; genuine content-removing redaction |
| XI-01..06 | Image metadata; watermark/text; combine/collage; adjustments/border/canvas; best-effort target-size/comparison; manual stitching and explicit codec capability matrix |
| XD-01..03 | SHA-256 comparison; conditional scheduling/multipart; authorized browser downloads and webpage-to-PDF |
| XF-01..05 | Batch rename; exact duplicates/scoped storage; favorites/managed trash; encrypted/extra archive formats; local vault |

These groups need distinct acceptance tests and honest release gating. Redaction cannot be satisfied with black rectangles. A drawn signature is not a certificate signature. Matching a checksum is not malware scanning. Managed trash does not imply universal external-provider restoration. Unsupported codecs remain capabilities, not false promises.

No new paid/cloud dependency is approved by this document. Original PDF text editing, Office round trips, certified-signature workflows and universal repair remain outside the already agreed implementation contract rather than being secretly added to make the app sound complete.

## 7. UI and presentation contract

Reuse existing usable Composables and route identities; repair state ownership rather than rebuild every screen. Replace the monolithic repository/ViewModel coupling with feature-facing state holders over common file/job services. Keep one native activity and navigation graph with stable source/job IDs, not names as identity.

Restore Light background `#FAFAF9`, primary `#4F3DFF`; Dark background `#0B0B0D`, primary `#8F86FF`; approved content/container pairs; readable 14sp metadata, 16sp body and 56dp minimum main controls. Floating dock has four labeled destinations with safe insets; hide it in editors and replace it during multiselect. Its width is responsive, not 380dp on every device.

Use consistent Material Symbols Rounded filled vector assets. IBM Plex Sans/Arabic remain the design font targets; font binaries are not included in this deliverable. Record permitted local asset acquisition/setup explicitly and never claim exact font parity while a fallback is in use.

First launch honors an unset onboarding preference. Use actual locale/resource support rather than LayoutDirection alone; preserve drafts across locale/theme changes. [S10] Test English/Arabic, long names, bidi ranges/URLs, 360/412dp, expanded windows, keyboard, TalkBack, reduced motion and increased font size. Pure design screenshots are not proof of live Android UI behavior.

Release screens have no demo documents, fake storage totals, fabricated throughput or illustrative progress. Preview/test fixtures are isolated from production source sets. The catalog derives counts from enabled tools: 7 PDF + 4 image + 2 download + 3 file entries; preferences are not an extra advertised utility. Enabled expansion entries are counted separately.

## 8. Build and bandwidth contract

Use the user's reported JDK 17 and SDK at `$HOME/adb` when building on Fedora; do not hardcode that path in committed project files. No Android Studio installation is required. SDK 36 and Build Tools 36.0.0 are the intended locally available inputs.

Proposed API-36-compatible build baseline: AGP 8.10.1, Gradle 8.14 binary distribution, JDK 17, retaining Kotlin 2.0.21 and matching Compose compiler plugin initially. AGP 8.10 supports API 36 and has Gradle 8.11.1/JDK 17 minimums. This satisfies those declared minimums but is not a claim that this application's complete dependency graph has built. [S1] Hilt/KSP/Room/Compose compatibility must be exercised, not inferred from table entries. Only change a dependency when a reproduced failure, security issue or required capability justifies it.

Restore official wrapper scripts/JAR and verify provenance/checksums. Commit the wrapper JAR; pin distribution checksum; use `-bin`, not `-all`. The wrapper can download its own Gradle distribution before Gradle processes build options, so `--offline` alone is not a first-launch no-network guarantee. Check for the required cached distribution first or use a cached trusted executable. [S11]

No installs, cache deletion, dependency refreshes, new repositories or large downloads on the user's machine without an itemized approval. No SDK/NDK/emulator/Gradle caches in the source ZIP. Infrastructure downloads in another runner must also be recorded; they do not make the final source ZIP self-contained or offline-bootstrappable.

## 9. Decomposed engineering work

| Subsystem | Inputs/dependencies | Completion boundary |
|---|---|---|
| Foundation and Images-to-PDF slice | Existing source, approved tokens, build tooling | Trusted build, source intake, durable jobs, safe output and one real round trip |
| PDF structural engine | Foundation, approved backend decision | Viewer/edit/export paths with preservation corpus |
| Image engine | Foundation | Real conversion/geometric operations/compression/batches |
| File/archive engine | Foundation | Provider-aware operations and bounded archive IO |
| Transfer execution | Foundation, network policy | Real HTTP streaming/resume/cancel and persistent recovery |
| Security-sensitive labs | Foundation and dedicated threat model | Real authenticated vault and individually verified expansion capabilities |
| Whole-product completion | All preceding implementations | UI parity, localization, integration, fault injection and final packaging |

These are connected subprojects, not independent throwaway apps. A later implementation plan specifies exact file changes, regression tests, test-first steps and execution method. No implementation action is considered approved solely because this design exists.

## 10. Required verification

Tests must exercise the production path, not a Python reimplementation of Kotlin or a scan for balanced delimiters. Every new defect fix begins with a regression test that fails against the defective behavior.

| Gate | Minimum evidence |
|---|---|
| Build | Official wrapper verification; actual assembleDebug exit 0; valid APK path and checksum |
| Static quality | Actual lint and Kotlin compilation output; no blanket suppression to obtain green |
| Pure domain | Reversed/out-of-range pages rejected; numeral normalization; URL validation; collision/geometry/path tests |
| File integrity | Known input hashes unchanged; real PDF text/page order and image content assertions; invalid input cannot report success |
| Persistence | Process restart, interrupted publication and retry reconciliation without duplicate outputs |
| Downloads | Controlled HTTP fixtures for no length, no range, valid resume, wrong range, changed file, redirects, TLS refusal, cancellation and disk-full |
| Archives | Byte-for-byte round trips; traversal/duplicate/large expansion rejection; safe failure cleanup |
| UI | Connected Android smoke and instrumentation; correct real input/result navigation; screenshots for themes/RTL/large text |
| Vault/redaction | Dedicated security tests before enabling; no success badge based on API names or a UI flag |

Use `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` and device tests when available, preserving actual logs and exit codes. Inspect the assembled APK; do not equate successful source generation with a build. Memory/performance claims require device measurements, and background behavior requires actual OS testing.

The current review proves only the archive/source observations and the failed wrapper baseline. It proves none of these completion gates.

## 11. Final package contract

After reviewed implementation and verification, the requested ZIP contains one standard Android project, complete wrapper, pinned build configuration, application source/resources, tests, capability inventory, setup instructions, dependency/license notices, and concise executed verification evidence. Include checksums for packaged files and any built APK supplied separately.

Exclude API keys, signing secrets, personal files, absolute local SDK configuration, SDK/NDK/emulator distributions, Gradle caches, font binaries and irrelevant design archives. Do not require a paid service, web processing or an IDE to run the documented CLI build. A source ZIP will still need the declared toolchain and dependencies; do not advertise it as a fully offline installation kit.

A package with unexecuted mandatory checks is explicitly an unverified source candidate, not a completed production application. Source-complete, build-verified, device-verified and store-release-ready are separate statuses. Future capability gates are stated, not hidden behind disabled buttons and a claim of full completion.

## 12. Approval requested

Approve the source-preserving repair architecture, the offline permissive PDF-backend evaluation, the full feature-scope register and the verification/packaging contract. No purchase, destructive action, remote repository update or install on the user's Fedora machine is included in this approval. The next artifact is the detailed implementation plan, followed by implementation under its reviewed execution method.

## Sources checked for this design

[S1] Android Gradle Plugin 8.10 release notes: https://developer.android.com/build/releases/agp-8-10-0-release-notes

[S2] Android PdfRenderer reference: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer

[S3] Android PdfDocument reference: https://developer.android.com/reference/android/graphics/pdf/PdfDocument

[S4] PdfBox-Android README, including candidate artifact and license: https://github.com/TomRoush/PdfBox-Android

[S5] Apache PDFBox security model and advisories: https://pdfbox.apache.org/security.html

[S6] Android Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files

[S7] HTTP validators and range requests, RFC 9110 sections 8.8, 13 and 14: https://www.rfc-editor.org/rfc/rfc9110.html

[S8] Android background data transfer options: https://developer.android.com/develop/background-work/background-tasks/data-transfer-options

[S9] Android Keystore and hardware-backed/authentication policies: https://developer.android.com/privacy-and-security/keystore

[S10] Android per-app language preferences: https://developer.android.com/guide/topics/resources/app-languages

[S11] Gradle Wrapper files, bootstrap, checksums and binary distribution: https://docs.gradle.org/current/userguide/gradle_wrapper.html

[S12] Android automatic backup and data-extraction rules: https://developer.android.com/identity/data/autobackup

Source evidence is the uploaded ZIP and the companion source audit. Proposed architecture and acceptance decisions are recommendations, not claims that those source files already implement them.
