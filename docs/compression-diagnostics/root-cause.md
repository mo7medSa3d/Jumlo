# PDF compression root cause

Location: `app/src/main/java/com/jumlo/app/data/pdf/PdfBoxBackend.kt`, `compress()` / `downscaleAndCompressImage()`.

Original behavior:
- `downscaleAndCompressImage()` was wrapped in `runCatching { ... }.getOrNull()`.
- For scanned pages, images are DCTDecode JPEG streams. The method first tried `image.image` / `image.getImage(...)`, which depends on PDFBox's SampledImageReader and can return null or throw.
- When it returned null, `compress()` fell through to `downscaleFlateImage()`, which only handles single-filter FlateDecode 8-bit DeviceRGB/Gray streams and returns null for JPEG streams.
- Result: JPEG image XObjects were skipped, `replaced` stayed 0, and the saved PDF was essentially unchanged. Exceptions were swallowed, so the UI reported success/no gain instead of an error.

Fix:
- JPEG (suffix "jpg" / DCTDecode) images are now decoded directly from `image.createInputStream()` bytes with `BitmapFactory.decodeByteArray`, then downsampled to the target DPI and re-encoded with `JPEGFactory.createFromImage(..., quality)`.
- Decode failures throw `PDF_JPEG_DECODE_FAILED` instead of being swallowed.
- `compress()` collects per-image errors and throws `PDF_IMAGE_COMPRESSION_FAILED` with details.
- Candidate replacement and page count/order/size preservation logic is unchanged.
