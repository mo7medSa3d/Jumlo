# Compression before/after (host JVM, Flate image fixture)

Source: `MeasureCompressionTest` on `PdfFixtures.imageDoc` (2 pages, 600x400 raw Flate RGB image + text page).

| Profile | Input bytes | Output bytes | Pages | Saved |
|---|---:|---:|---:|---:|
| 96 DPI / quality 55 (Strong) | 721710 | 588805 | 2 | 18.42% |
| 150 DPI / quality 70 (Medium) | 721710 | 634967 | 2 | 12.02% |
| 300 DPI / quality 88 (High) | 721710 | 721710 | 2 | 0.00% |

Scanned JPEG inputs were diagnosed with `scripts/pdf-image-diagnostics.py`; see `docs/compression-diagnostics/`. The JPEG recompression path was fixed in code but could not be measured on the JVM host because Android `BitmapFactory` returns null under unit tests. It must be verified on device.
