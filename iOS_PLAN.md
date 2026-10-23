# iOS port plan

## Current state

This repository contains an Android app and four **Android-free Kotlin/JVM modules, not KMP-ready modules**. Their build files use `kotlin.jvm`, `java-library`, and JDK 17. There are no Apple targets, `commonMain`/`iosMain` source sets, or iOS app in this delivery. The module split makes dependencies easier to isolate; it does not establish Kotlin/Native compatibility. See [settings](settings.gradle.kts) and the module build files below.

| Existing module | What it contains | Remaining platform work |
| --- | --- | --- |
| [core-common](core-common/build.gradle.kts) | Validation, job/output policies, naming, budgets, file processing | Separate portable decisions from Java files, hashing, ZIP, URI/normalization and UUID utilities. |
| [downloader-core](downloader-core/build.gradle.kts) | Link/format selection, resume rules, streaming transfer, media process policies | Replace Java streams/files, threads, `ProcessBuilder`, and native ELF assumptions. |
| [document-core](document-core/build.gradle.kts) | Page/image geometry, edit plans, OCR text policy, DOCX/PPTX package generation | Port Java ZIP, SAX/XML and `java.nio` publication; provide native rendering, extraction and OCR. |
| [storage-core](storage-core/build.gradle.kts) | Publication/recovery, verified copying, trash and cleanup | Replace Java file/channel locks, atomic moves, properties and filesystem traversal. |

The last three modules depend on `core-common`. Android implementations of Compose, WorkManager, Room/DataStore, SAF/MediaStore, PDFBox-Android/PdfRenderer and ML Kit remain in `app`.

## Phase 1 — Establish a small shared contract

Move independently tested models and decision logic to KMP `commonMain` first: job transitions, format selection, naming rules and page geometry. Keep JVM implementations behind interfaces while adding Apple targets and a macOS build/test job. Select the minimum iOS version and a SwiftUI shell at this checkpoint. Preserve the five-tab information architecture; sharing the Android Compose UI is a separate decision.

Use these actual boundaries as starting points:

- [`HttpTransport` / `StreamTransfer`](downloader-core/src/main/kotlin/com/jumlo/app/core/StreamTransfer.kt) currently expose `InputStream` and `File`. Define platform-neutral transfer commands, progress, cancellation and persisted task identifiers; preserve ETag/Range/integrity rules.
- [`MediaRuntime`](app/src/main/java/com/jumlo/app/data/media/MediaRuntime.kt) is still in `app` and accepts a `File` output directory. Extract its metadata/selection contract before adding an iOS implementation.
- [`PdfImageCodec`](app/src/main/java/com/jumlo/app/data/pdf/PdfImageCodec.kt) already uses byte arrays. Its PDF parser and bitmap implementation still need native adapters.
- [`OfficePackageWriter`](document-core/src/main/kotlin/com/jumlo/app/core/office/OfficePackageWriter.kt) accepts one page of text/image data at a time, but writes Java ZIP/XML to a `File`. Preserve that streaming contract and cancellation behavior when porting it.
- [`DurablePublication`](storage-core/src/main/kotlin/com/jumlo/app/core/Publication.kt) is a concrete JVM class, not an existing portable storage interface. Extract publication/recovery operations without weakening staged-write and collision guarantees.

Use ordinary interfaces or narrowly scoped `expect`/`actual` declarations as described in [Kotlin's platform API guidance](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html). Gate this phase on shared tests executing on JVM and Apple targets.

## Phase 2 — Deliver native files, PDF and direct downloads

Use [FileManager](https://developer.apple.com/documentation/foundation/filemanager) for sandbox storage, with native document import/export, security-scoped external access and a persistent job/publication journal. Store credentials through an iOS secret-store adapter. Retain source files until an output has been validated and published.

Use a [background URLSession](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background) for HTTP(S) file downloads. Reconcile saved task identifiers after relaunch and publish completed temporary files through the storage adapter. Background scheduling, resume data and user force-quit behavior require iOS-specific handling; a WorkManager state machine alone does not supply it.

Use [PDFKit](https://developer.apple.com/documentation/pdfkit/pdfview) for viewing and [page text access](https://developer.apple.com/documentation/pdfkit/pdfpage), [Vision](https://developer.apple.com/documentation/vision/recognizing-text-in-images) for OCR, and [VisionKit document scanning](https://developer.apple.com/documentation/visionkit/vndocumentcameraviewcontroller) for multipage camera capture. Add native image import and PDF generation. Check scanner availability and OCR languages on the selected OS/device.

Prototype merge/split, annotations and compression separately. PDFKit availability does not prove parity with the current PDFBox image recompression path: measure output size and verify retained text, page geometry and document features. Port Office packaging through compatible ZIP/XML adapters and rerun the same DOCX/PPTX fixtures. Keep its present scope: basic editable Word text, and rendered PowerPoint pages with a text layer.

## Phase 3 — Decide the media extraction deployment

The Android yt-dlp/Python/FFmpeg/QuickJS payload and Java subprocess runner cannot be reused as the current iOS integration. A local iOS engine needs a separate packaging, native-library, background execution and distribution feasibility prototype; do not promise it as a mechanical port.

The pragmatic alternative is a service behind the extracted `MediaRuntime` contract: inspect a submitted URL, return actual formats, prepare the selected output, and provide a download URL consumed by background URLSession. Establish hosting, authentication, URL/credential privacy, expiry, operating cost and failure behavior before selecting it. The existing `tools/media-service` is a development adapter, not a deployed production service. Direct file downloads can ship independently of this decision.

## Phase 4 — Qualify on iPhone and iPad

Gate release on real-device evidence: background/relaunch recovery, cancellation, low storage, interrupted publication, resume validation, large/encrypted/corrupt PDFs, scan ordering, OCR language availability, Arabic RTL/English LTR, source integrity, and opening exported DOCX/PPTX in target Office applications. Record memory, scrolling and artifact-size measurements on declared devices and OS versions. Retain Android regression gates throughout.

**Delivery boundary:** this document is a phased proposal. No iOS binary, native adapter, KMP compilation, or iOS runtime test is claimed by this Android delivery.
