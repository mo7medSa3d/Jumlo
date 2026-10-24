# Tool qualification handoff

This is an execution template, not a release pass. [tool-matrix.template.csv](tool-matrix.template.csv) contains **188 cases: all 47 registry IDs × normal, large, encrypted and corrupt inputs**. The [registry](../../app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt) and [capability policy](../../core-common/src/main/kotlin/com/jumlo/app/core/CapabilityPolicy.kt) classify them as **39 processing, five navigation and three unavailable**. All 156 processing cases start `NOT_RUN`; the remaining 32 are `N/A` with reasons.

## Evidence checkpoints, separate from matrix results

- **Recovered history:** the [report supplied with the source](../history/RECOVERED_TEST_REPORT.md) describes 408 local JUnit cases: 406 passed, zero failures/errors and two optional remote-service skips across 70 XML files. That report says the compatibility build reused the snapshot. Its `reports/candidate-junit-summary.json` and `reports/candidate-junit/` references are historical paths, not fresh evidence included here.
- **Preserved default host checkpoint:** the [16:56–17:03 run](../../verification/current/host/final-candidate/README.md) contains 472 cases across 82 suites: 466 PASS, zero FAIL and six SKIP. Two optional remote-service cases and four opt-in capture methods were skipped. Its source fingerprint is `cabc30bddabb7e810737fffb07976ef4151749f686b8fba925f57c21a1432bcb`; the combined command exited 1 on an unresolved offline connected-test dependency. This does not establish a candidate-2 full-suite or combined-gate pass.
- **Candidate-2 native pair:** the [matching API 26 run](../../verification/current/device/api26-ui-regression-candidate2/CURATION_NOTES.md) records two unchanged methods PASS, zero FAIL and zero SKIP: `JumloUiTest#warmWorkspaceDeepLinkReachesTheRegisteredDestination` and `StudioGestureInteractionTest#comparisonDividerDragUpdatesItsAccessibleSlider`. The pair covers repeated warm-link/Back-to-Files behavior and four language/theme comparison iterations. Its 109.115-second JUnit duration is test execution time; the other 137 reviewed methods were outside this pair's filter.
- **Candidate-2 host captures:** [attempt 6](../../verification/current/screenshots/attempt6/README.md) records four capture methods PASS, 260 PNGs visually reviewed, 124 blocked states and zero failed states. These are host-rendered static states, not completed Android tool operations. The earlier preview timeout and known heading-only Compare Images card remain disclosed.

The candidate-2 pair and host captures bind to the 747-input fingerprint `6d6663e62a5d76effe2905d17029a309162d3aab3f656f9f0763696ebfabb558`. These checkpoint counts overlap in scope and must not be summed into a new test total or promoted into tool-matrix PASS cells. The separate full mandatory gate and final optimized artifacts were still pending at this documentation checkpoint; consult [TEST_REPORT.md](../../TEST_REPORT.md) for their eventual terminal results.

## Use the matrix

Copy the CSV for each actual device/APK combination. Record API, ABI, model, APK hash, input hashes, measured input bytes/pages/pixels/count, operation settings, UTC, tester, observed result, output hash and evidence files. Input and output fixtures belong outside source; preserve originals. Each row already includes its fixture recipe, expected result and related source paths.

Run normal inputs through the actual entry screen, importer, worker and saved-output workflow. Verify the output with an independent reader/decoder/hash tool and reopen it from the saved location. Check source hashes, cancellation and no misleading success/partial publication. Exercise every exposed mode named in the row; split a row if evidence only covers one mode.

For **large** cases, record an accepted large input below the actual device budget and a separate exceeded-budget boundary. A tiny file, a synthetic counter, or a graceful rejection alone does not prove successful large-input processing. Measure completion, memory/progress and responsiveness.

For **encrypted** cases, PDF processing generally requires a separate unlock operation; test `pdf_protect` with correct and incorrect passwords. JPEG/PNG/WebP do not have a normal password mode: test safe rejection of encrypted containers/mislabeled bytes. Generic downloads, hashing, renaming and ZIP creation operate on opaque bytes and should preserve encrypted payloads without decrypting them.

For **corrupt** cases, parsers/decoders must fail safely. Copy/hash/archive-creation tools may correctly preserve malformed document bytes; test exact-byte behavior and failed reads instead of demanding document validation.

Use `PASS`, `FAIL`, `BLOCKED`, `NOT_RUN`, or `N/A`. `PASS` requires all row assertions plus device/output evidence. Record concrete reasons for `BLOCKED`/`N/A`; missing hardware, skipped tests, unsupported providers and timeouts are never passes. Source availability labels such as `HOST_CORE_TESTED` do not set matrix status. `img_compare` is an enabled processing entry exposed by the flat catalogue; its visible input form and shared comparison-component regression do not establish that its full processing workflow passed.

## Navigation and blocked entries

Their four input rows are `N/A` because they are not dispatcher processing jobs. Their separate UI/gating checks remain **NOT_RUN** here.

| IDs | Why processing is N/A; separate check |
| --- | --- |
| `pdf_01` | Reader route. Open normal/large/encrypted/corrupt PDFs; verify scrolling, zoom, thumbnails, dark mode and safe failure. |
| `scan_doc` | ML Kit launcher. On supported hardware verify camera/gallery, multiple pages, cancellation/model availability, saved PDF and source preservation. |
| `dl_settings` | Settings route. Persist changes and verify later downloads use them. |
| `file_02` | Files route. Verify granted/read-only/revoked provider access and actual file actions. |
| `file_trash` | Files/trash route. Verify recoverable removal and restoration with matching hashes. |
| `file_drive` | Enabled: real Drive v3 workflow, user token-gated. Verify import/export with a user-supplied OAuth token; device/account/privacy qualification remains outstanding. Public Drive URL downloading is separately covered by `dl_02`. |

## Existing related tests: scope matters

The CSV gives exact repository-relative sources per tool. These tests cover selected layers and edge cases, not every matrix cell. A test source or a passing JVM fixture is not Android execution evidence.

| Area | Host/local JUnit fixtures | Android instrumentation sources; execution unclaimed here |
| --- | --- | --- |
| PDF structure, forms, passwords | [PdfStructureCoreTest](../../app/src/test/java/com/jumlo/app/pdf/PdfStructureCoreTest.kt), [PdfFormReliabilityTest](../../app/src/test/java/com/jumlo/app/pdf/PdfFormReliabilityTest.kt), [PdfSecurityQualificationTest](../../app/src/test/java/com/jumlo/app/pdf/PdfSecurityQualificationTest.kt) | [PdfHandlerReliabilityTest](../../app/src/androidTest/java/com/jumlo/app/data/PdfHandlerReliabilityTest.kt): selected drawing, export-budget and interrupted-stage cases. |
| Compression/overlays/metadata | [PdfCompressionHostTests](../../app/src/pdfCompressionTest/kotlin/PdfCompressionHostTests.kt): real JPEG via host ImageIO; [PdfOverlayCoordinatesTest](../../app/src/test/java/com/jumlo/app/pdf/PdfOverlayCoordinatesTest.kt), [PdfMetadataCleanerTest](../../app/src/test/java/com/jumlo/app/pdf/PdfMetadataCleanerTest.kt) | [PdfCompressionDeviceTest](../../app/src/androidTest/java/com/jumlo/app/pdf/PdfCompressionDeviceTest.kt): scan corpus, 200 pages, unreadable image. |
| Office/OCR | [OfficePackageHostTests](../../app/src/officeTest/kotlin/OfficePackageHostTests.kt): OOXML structure, Unicode, streaming and cancellation; not a Microsoft Office device test. | [PdfOfficeHandlerTest](../../app/src/androidTest/java/com/jumlo/app/data/PdfOfficeHandlerTest.kt), [NativeOcrCancellationTest](../../app/src/androidTest/java/com/jumlo/app/data/NativeOcrCancellationTest.kt). Digital-text fixture success does not qualify downloaded OCR models or scanned-page recognition. |
| Reader/scan | [ScanGeometryTest](../../app/src/test/java/com/jumlo/app/core/ScanGeometryTest.kt): geometry only. | [PdfReaderAndScanStoreTest](../../app/src/androidTest/java/com/jumlo/app/data/PdfReaderAndScanStoreTest.kt), [PdfViewerInteractionTest](../../app/src/androidTest/java/com/jumlo/app/ui/PdfViewerInteractionTest.kt). Store-copy tests do not exercise the live scanner UI. |
| Images | [ImageWorkPolicyTest](../../app/src/test/java/com/jumlo/app/core/ImageWorkPolicyTest.kt), [ImagePdfLayoutTest](../../app/src/test/java/com/jumlo/app/core/ImagePdfLayoutTest.kt): planning/budgets. | [ImagePixelTest](../../app/src/androidTest/java/com/jumlo/app/data/ImagePixelTest.kt), [ImageProcessingReliabilityTest](../../app/src/androidTest/java/com/jumlo/app/data/ImageProcessingReliabilityTest.kt): pixel correctness and real bitmap allocation failures. |
| Archives/renaming/duplicates | [ArchiveCoreReliabilityTest](../../app/src/test/java/com/jumlo/app/data/files/ArchiveCoreReliabilityTest.kt), [EncryptedZipSafetyTest](../../app/src/test/java/com/jumlo/app/data/files/EncryptedZipSafetyTest.kt), [PureDomainAlgorithmsTest](../../app/src/test/java/com/jumlo/app/domain/PureDomainAlgorithmsTest.kt) | [ArchiveHandlerTest](../../app/src/androidTest/java/com/jumlo/app/data/ArchiveHandlerTest.kt): actual imported payloads, stage restart and AES publication. |
| Storage/shared publication | [PublicationAndErrorLogTest](../../app/src/test/java/com/jumlo/app/core/PublicationAndErrorLogTest.kt), [VerifiedFileCopyTest](../../app/src/test/java/com/jumlo/app/core/VerifiedFileCopyTest.kt) | [SourceAndPublicationTest](../../app/src/androidTest/java/com/jumlo/app/data/SourceAndPublicationTest.kt): snapshots, integrity, identity, trash/restore; [ProviderFilesInteractionTest](../../app/src/androidTest/java/com/jumlo/app/ui/ProviderFilesInteractionTest.kt): provider UI permissions. |

Download/provider/background cases also require [LIVE_MEDIA_QUALIFICATION.md](LIVE_MEDIA_QUALIFICATION.md). Its host metadata probes and offline native fixtures do not qualify live downloads. Record final candidate rerun and device outcomes in the release test report before promoting any row.
