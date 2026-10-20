# Jumlo — CamScanner-inspired interactive mobile document tools

Date: 2026-10-20 · Feature branch: `feat/interactive-pdf-ux-20261020`

## Ground truth from current repository

This implementation **modifies the existing Android Kotlin / Jetpack Compose app**; it does not create screenshots or fake actions. The existing PDF/image processing routes, Home/File/Scan navigation, and private/offline-first processing remain intact.

### Implemented in this branch

1. **Tools catalog**: adaptive 3–6 column icon grid, compact tap targets, scrollable categories, search, recent/frequent tools and RTL labels. Uses the real `CapabilityRegistry`; blocked or missing capabilities are not shown.
2. **PDF Watermark studio**: real page-preview rendering, on-document drag placement, presets, single/tiled styles, color swatches, opacity/size/rotation sliders, current/all-page selection, and page-number toggle. No raw numeric typing is needed.
3. **Real watermark processing**: validated style flows via `WorkspaceDraft` → serializable `PdfStampSettings` → `PdfOperationHandler` → `PdfBoxBackend`. Arabic/unicode text uses on-device font shaping and is embedded as a bounded transparent watermark image rather than replacing unsupported characters with spaces. Existing PDF page contents and text are **not rasterized**.
4. **Reliability**: one/two-document input-count guard in the workspace for source-limited tools; localized actionable notice instead of submitting known-invalid source counts. Output is still published only via existing verified output store.
5. **Interactive local profile**: the previously decorative profile avatar now opens a real local-document/used-space summary and the Files screen. No fabricated account name, paid status or cloud sync is displayed. A real online account backend remains out of scope.
6. **Smart scanning**: optional Google ML Kit on-device document scanning flow with automatic edge detection, filters and multi-page JPEG capture into the existing Images → PDF workspace. Play services may need a first-run download and an eligible device; the existing CameraX manual/interactive crop flow remains the fallback. **Not device-verified yet.**
7. **Contracts**: a pure Kotlin style test and source/bilingual wiring test are added to the existing host verifier. Android builds and device output-render assertions remain necessary; source checks are **not** device proof.

### Compression audit (existing current main implementation, not newly invented)

- `PdfOperationHandler.compressPdfGuaranteedSmaller` enumerates profiles, validates every candidate by reopening and checking page count, and returns the smallest valid file or the untouched source.
- `PdfBoxBackend.compressImages` transcodes supported scan image XObjects (including nested Form XObjects), subsamples oversized scans and preserves vector/text pages. 1-bit, mask, alpha and non-qualifying assets are deliberately preserved.
- **JPEG quality 50 does not mean PDF bytes decrease by 50%.** Vector/text-heavy or already-optimized files may not shrink materially. This is not silently reported as a success.
- Next objective is measuring real 50 MB/100 MB scanned PDF datasets with byte savings, PSNR/SSIM, PDF text retention, disk/memory use and device API26/36 results.

## Benchmark sources / feature decisions

- [Android official drag controls](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-composables)
- [Android ML Kit document scanner](https://developers.google.com/ml-kit/vision/doc-scanner/android): on-device auto-capture, edge detection, filters, shadow/stain removal; Google Play services may download models on first use. Candidate for opt-in scanner improvement, **not yet integrated**.
- [Adobe Acrobat mobile](https://www.adobe.com/acrobat/mobile/acrobat-reader.html): benchmark for PDF organizing and document workflows; proprietary APIs/features require review.
- [CamScanner](https://www.camscanner.com/): UX inspiration for page preview, scan flow, watermark studio and compact tool discovery. We do not reuse proprietary assets or branding.

## Capability status — never mislabel a stub as a feature

- Existing source-backed: PDF split/merge/organize/thumbnail workflows, visual crop/signature/image overlay, JPG/PNG/WebP, scan with CameraX, PDF/image compression, watermarks, file tools and direct downloader.
- Blocked by qualification/security on main: production OCR/searchable PDF, PDF redaction/protection, encrypted ZIP, Drive API and webpage→PDF. Restore only after deterministic qualification.
- Not added by this branch: offline high-fidelity Word/Excel/PPT conversion, automated smart erase/photo restoration, full CamScanner camera parity or Apple iOS app. These require licensed engines/models, reliable on-device implementation and device tests.

## Native verification still required before merge

- `bash scripts/verify-host.sh` including `scripts/test-watermark-studio.py`.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon` with SDK36, followed by actual APK artifact verification.
- On-device (API26/36): portrait/landscape, 1/300+ page PDFs, Arabic/Latin Unicode watermarks, transparency, tile/rotation placement, preview vs PDF exported alignment, TalkBack focus, RTL, dark/light, file-output integrity.
- Large PDF performance: 4–8 GiB heap constrained device, disk spill, OOM/memory pressure and failure cancellation.
- Never merge to main or claim production readiness solely from source-contract results.
