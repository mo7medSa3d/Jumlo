# PDF support matrix (source state, NOT device-verified)

Backend: PDFBox-Android 2.0.27.0 (Apache-2.0), see DEPENDENCY_NOTICES.md.
All operations run through the Room/WorkManager job pipeline with verified
publication. Everything below is **implemented in source, not compiled or
device-verified in this environment** (no JDK/Android SDK available here).

| Operation | Status | Limits |
|---|---|---|
| View / pages→PNG,JPEG / images→PDF | implemented earlier | PdfRenderer-based |
| Merge, split, extract, organize (reorder/delete/insert via order list), rotate | implemented (`PdfBoxBackend` + JVM tests in `PdfStructureCoreTest`) | insert = order-list inclusion of another doc's pages via `insert()` backend op (tested); UI organize uses order list |
| Compress | implemented | only flate 8-bit RGB/gray images downscaled; text untouched; other codecs pass through |
| Crop / page geometry | implemented | cropBox only, normalized coords |
| Annotations & overlay text | implemented | Helvetica overlay or note annotation |
| Watermark & page numbers | implemented | text stamp, fixed position/size |
| Signature (drawn + imported) | implemented | drawn PNG via signature pad, or imported image; fixed normalized rect |
| Password protect/remove | implemented | AES-128 standard security; passwords session-only (handles), owner password required; only for files you own/rights to modify |
| Metadata edit | implemented | title/author/subject/keywords/creator/producer |
| AcroForm fill + flatten | implemented | text/checkbox/radio; flatten paints widget appearances, drops form |
| Compare (non-AI) | implemented | extracted-text comparison, first differing page |
| Redaction | implemented with verification | operator-level removal + black bars + post-check; complex/multi-stream pages fail loudly instead of faking; leftover-text check fails the job |
| OCR → searchable PDF + .txt | implemented | ML Kit on-device; invisible text layer with coarse (per-page block) positioning — word boxes are NOT preserved; .txt is the precise extract |
| Scan | implemented | CameraX, manual alignment, fixed inset-quad perspective correction; NO auto edge detection |
| Webpage→PDF | implemented | WebView print adapter; login/DRM/JS-heavy pages may print blank |

Encrypted PDFs: only `pdf_protect` (remove) opens them after you supply the
password in-session. Other tools reject encrypted input rather than guessing.
