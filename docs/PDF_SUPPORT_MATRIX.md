# PDF support matrix (source state, NOT device-verified)

Backend: PDFBox-Android 2.0.27.0 (Apache-2.0), see `docs/DEPENDENCY_NOTICES.md`.
Eligible operations use the Room/WorkManager pipeline with verified publication. The table describes retained handler source and its limits. Actual current build/JVM results are in `docs/RELEASE.md`; Android device behavior remains separately unverified.

**Public availability override:** password protection/removal, redaction, searchable OCR, webpage capture and scanner entries are hidden and blocked by `CapabilityPolicy`. Their rows below describe retained implementation, not an enabled product promise. `pdf_01` is a read-only viewer and never queues a processing job. See FEATURE_QUALIFICATION_MATRIX for the authoritative entry list.

| Operation | Status | Implementation & Limits |
|---|---|---|
| View / pages→PNG,JPEG / images→PDF | Implemented | `PdfRenderer`-based rasterization and incremental `PdfDocument` page generation |
| Merge, split, extract, organize (reorder/delete/insert), rotate | Implemented (`PdfBoxBackend` + `PdfStructureCoreTest`) | `merge`, `split`, `extract`, `organize`, `rotate`, and 2-PDF `insert` (via `insertAtPage` in `pdf_metadata`) |
| Compress | Implemented (`PdfBoxBackend.compressImages` + `PdfStructureCoreTest`) | Downscales Flate-encoded 8-bit RGB/Gray embedded images above target DPI while keeping text and vector streams untouched |
| Crop / page geometry | Implemented (`PdfBoxBackend.setCrop` + `PdfStructureCoreTest`) | Sets normalized `PDRectangle(x0, y0, width, height)` on page `cropBox` |
| Annotations & overlay text | Implemented (`PdfBoxBackend.overlayText` / `addTextAnnotation` + `PdfStructureCoreTest`) | Supports both visible Helvetica stream overlay and collapsible `PDAnnotationText` note annotations |
| Watermark & page numbers | Implemented (`PdfBoxBackend.stampWatermarkAndNumbers` + `PdfStructureCoreTest`) | Extended graphics state alpha watermark stamp and bottom-center page numbering |
| Signature (drawn + imported) | Implemented (`SignaturePad in ScanScreen.kt` + `PdfBoxBackend.stampImage`) | Drawn signature saved as PNG snapshot or imported image stamped onto normalized page box |
| Password protect/remove | Implemented (`PdfBoxBackend.protect` / `removeProtection` + `PdfStructureCoreTest`) | AES-128 standard security; session-only password handles via `EphemeralSecretStore` with `CharArray` zeroization in `finally`; owned files only |
| Metadata edit | Implemented (`PdfBoxBackend.setMetadata` + `PdfStructureCoreTest`) | Edits title, author, subject, keywords, creator, and producer |
| AcroForm fill + flatten | Implemented (`PdfBoxBackend.fillForm` + `PdfStructureCoreTest`) | Populates text/checkbox/radio fields; explicit flatten stamps widget appearance streams and removes `/AcroForm` |
| Compare (non-AI) | Implemented (`PdfBoxBackend.compareText` + `PdfStructureCoreTest`) | Deterministic page-by-page extracted text comparison report |
| Redaction | Implemented with verification (`PdfBoxBackend.redact` + `PdfStructureCoreTest`) | Rewrites content-stream text operators (`Tj`, `TJ`, `'`, `"`) inside target normalized rect, draws black redaction bars, and re-extracts text to verify complete removal |
| OCR → searchable PDF + `.txt` | Implemented (`OcrWebHandler.ocr` + `PdfBoxBackend.stampInvisibleText`) | On-device ML Kit text recognition exports `.txt` and stamps `RenderingMode.NEITHER` invisible searchable text layer |
| Scan | Implemented (`ScanScreen.kt` + `Perspective.warpQuad`) | CameraX capture with automatic luminance-gradient quad edge detection (`detectDocumentQuad`), interactive 4-corner adjustment, and homography warp |
| Webpage→PDF | Implemented (`OcrWebHandler.webpage`) | Local WebView print adapter with HTTPS enforcement (`HttpPolicy.url`) |

Encrypted PDFs: only `pdf_protect` (`removeProtection = true`) opens encrypted PDFs using the session-supplied password. Other PDF tools reject encrypted inputs with `PDF_ENCRYPTED`.
