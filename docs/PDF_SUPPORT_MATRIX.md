# PDF candidate support matrix

No Android PDF operation has been built or run on a device in this execution. These rows describe source implementations, not completed qualification.

| Operation | Source implementation | Important limitations |
|---|---|---|
| View imported PDF | PdfRenderer page preview | Bounded display bitmap; no text search/selection or advanced reader parity |
| Images to PDF | Real decoded, EXIF-normalized images drawn to PdfDocument | Fit/fill, page size, margin and orientation; conservative bitmap budget; no device round trip performed |
| PDF pages to PNG/JPEG | Real PdfRenderer pixels | Selected pages, DPI and grayscale; max 1,000 files per request; page-range parser rejects overflow; no device raster test performed |
| Merge/split/reorder/extract PDF pages | NOT IMPLEMENTED | No raster substitution; closed gate |
| Optimize/crop PDF structure | NOT IMPLEMENTED | Closed gate |
| Forms, annotations, signatures, security | NOT IMPLEMENTED | Closed gate |
| Content-removing redaction | NOT IMPLEMENTED | Closed gate; no visual overlay presented as redaction |

Encryption/password-required PDFs are not claimed supported by the platform renderer configuration here. Platform parsing of hostile documents, process isolation, accessibility, large-file behavior, and device-specific codec behavior have not been qualified.
