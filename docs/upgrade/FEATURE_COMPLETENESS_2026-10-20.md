# Feature-completeness matrix — Jumlo, 2026-10-20 (session 2)

Scope: the 46 catalog ids in `CapabilityRegistry.tools`, plus the navigation and printing
surfaces. **This is a source- and JVM-evidence inventory, not device certification.**
Evidence markers:

| Marker | Meaning |
| --- | --- |
| **S** | source inspection of the real path |
| **H** | executed host/JVM policy test (`scripts/verify-host.sh`, 181 checks) |
| **P** | executed real-PDF JVM backend fixture (`PdfStructureCoreTest`) |
| **U** | executed Android unit test (`./gradlew :app:testDebugUnitTest`, 235 tests) |
| **B** | executed Gradle build/lint (debug + release APK) |
| **D pending** | requires a device or emulator — **not executed in this environment** |

Availability and qualification stay separate: a tool can be shipped and still be unqualified
for a specific device, paper size or provider.

## PDF catalog

| ID | Feature | Availability | State after this session | Evidence / remaining limit |
| --- | --- | --- | --- | --- |
| `pdf_02` | Images to PDF | visible | Working. A margin larger than the page is now clamped instead of failing the whole run; the page geometry and placement are unchanged. | U (layout clamp), S. **D pending**: rendered output. |
| `pdf_03` | Merge PDF | visible | Working, unchanged. | S. |
| `pdf_04` | Organize Pages | visible | Working. **Fixed:** reorder/rotate/duplicate now keep the source catalog (bookmarks, form fields, document info) instead of dropping it; a legacy rotation that is not a quarter turn is rejected instead of writing an invalid `/Rotate`. | P, U. **D pending**: drag/zoom interaction. |
| `pdf_05` | Split PDF | visible | Working. **Fixed:** split parts keep bookmarks and AcroForm fields; every published part is re-opened and its page count verified; a range that reaches past the last page is clamped. | P, U. |
| `pdf_06` | Extract Pages | visible | Working. **Fixed:** extracted documents keep the source catalog; out-of-range page ranges are clamped. | P, U. |
| `pdf_07` | PDF to Images | visible | Working. **Fixed:** exported page files are named `Document - 003.png` instead of embedding an Arabic word in an English file name. | U. **D pending**: renderer pixels. |
| `pdf_01` | View PDF | visible | Working, unchanged. | S. |
| `pdf_compress` | Compress PDF | visible | Working. **Fixed:** when no candidate is smaller, the output is reported as *unchanged* (`PDF_NOT_COMPRESSIBLE`) instead of a byte-identical copy labelled "Compressed". | U. |
| `pdf_crop` | Crop PDF Pages | visible | Working, unchanged. | P. |
| `pdf_annotate` | Annotate PDF | visible | Working, unchanged. | P. |
| `pdf_stamp` | Watermark & Page Numbers | visible | Working, unchanged. | P. |
| `pdf_signature` | Sign PDF | visible | Working. **Fixed:** a placed signature/photo is re-encoded with its EXIF orientation applied, so a portrait image is no longer embedded sideways. | S, U. **D pending**: viewer rendering. |
| `pdf_image` | Add Image to PDF | visible | Working. **Fixed:** same EXIF normalisation as the signature path. | S, U. |
| `pdf_metadata` | PDF Metadata | visible | Working. **Fixed:** a blank field no longer erases existing title/author/subject/keywords; inserting a second document keeps the base catalog instead of rebuilding an empty one; an out-of-range insert page is rejected. | U. |
| `pdf_forms` | Fill PDF Form | visible | Working, unchanged. | P. |
| `pdf_compare` | Compare PDFs | visible | Working. **Fixed:** the report now contains the differing page text, not only the verdict. | S, U. |
| `pdf_protect` | Password Protect PDF | **visible (released 2026-10-20)** | Backend retained and **fixed**: a protected file is verified with the PDF backend instead of the renderer (which cannot open an encrypted PDF), and a wrong password reports `PDF_PASSWORD_INCORRECT` instead of a generic failure. The security gate is now satisfied by `PdfSecurityQualificationTest`, which proves the protect/unlock round trip on the JVM. | S, P. |
| `pdf_redact` | Redact PDF | **visible (released 2026-10-20)** | The security gate is now satisfied by `PdfSecurityQualificationTest`, which proves the redacted word is physically absent from the saved output and that untouched pages keep their text. | S, P. |
| `pdf_ocr` | OCR to searchable PDF | hidden | Unchanged, still a stub. | S. |
| `webpage_pdf` | Webpage to PDF | hidden | Unchanged, still a stub. | S. |
| `scan_doc` | Scan Document | **now visible** | Working. Camera capture, automatic document-quad detection, draggable corners, perspective correction and multi-page capture into Images to PDF. **New in this session:** the implemented tool is listed in the Tools catalog instead of only being reachable from Home. | S, H (geometry), U. **D pending**: camera, ML Kit, real perspective pixels. |

## Image catalog

| ID | Feature | Availability | State after this session | Evidence / remaining limit |
| --- | --- | --- | --- | --- |
| `img_01` | Convert Images | visible | Working. The bitmap budget is now half the app heap (capped), so a 24 MP camera photo is processed instead of rejected. | U. |
| `img_02` | Resize Images | visible | Working. **Fixed:** a percentage above 100 % was silently clamped back to the source size; an explicit percentage now scales up as asked, matching the project's own reference algorithm. | U. |
| `img_03` | Crop, Rotate & Flip | visible | Working, unchanged. | S. |
| `img_04` | Compress Images | visible | Working. **Fixed:** an already optimal image is reported as *unchanged* instead of a silent zero-output "completed" job, and the UI states that compression stores WebP. | U. |
| `img_exif` | EXIF Inspector | visible | Working, unchanged. | S. |
| `img_watermark` | Watermark Images | visible | Working. **Fixed:** the output numbering no longer skips a number when a logo source is used. | S. |
| `img_join` | Join / Collage | visible | Working, unchanged. | S. |
| `img_adjust` | Adjust & Pad | visible | Working, unchanged. | S. |
| `img_target` | Target Size | visible | Working, unchanged. | S. |
| `img_compare` | Compare Images | visible | Working. **Fixed:** the advertised visual difference map is now a real second output (a PNG with changed pixels highlighted) instead of only a text report. | S. **D pending**: rendered pixels. |
| `img_stitch` | Stitch Screenshots | visible | Working, unchanged. | S. |

## Download catalog

| ID | Feature | Availability | State after this session | Evidence / remaining limit |
| --- | --- | --- | --- | --- |
| `dl_media` | Direct media download | visible | Working, unchanged. | S. |
| `dl_02` | New Direct Download | visible | Working. **Fixed:** a typed file name with spaces or non-ASCII characters was parsed as a URI and lost, so the file was published as `download.pdf`; the name is now preserved. A transfer that finished but was interrupted before publication is published instead of downloaded again. | U, H. |
| `dl_03` | Batch URL Intake | visible | Working. **Fixed:** the batch parser that the tool advertises is now wired to the screen — every line is validated while typing, duplicates are reported and skipped at submit, and unsupported schemes are counted. | U, H. |
| `dl_settings` | Download Settings | visible | Working, unchanged. | S. |

## File catalog

| ID | Feature | Availability | State after this session | Evidence / remaining limit |
| --- | --- | --- | --- | --- |
| `file_02` | Browse Storage | visible | Working, unchanged. | S. |
| `file_07` | Create ZIP | visible | Working, unchanged. | H. |
| `file_08` | Extract ZIP | visible | Working, unchanged. | H. |
| `file_encrypted_zip` | Encrypted ZIP | hidden | Unchanged, still gated. | S. |
| `file_batch_rename` | Batch Rename (copies) | visible | Working, unchanged. | H. |
| `file_duplicates` | Duplicate Finder | visible | Working, unchanged. | H. |
| `file_trash` | Managed Trash | visible | Working. The job summary now shows *why* an item produced no file. | U. |
| `file_drive` | Google Drive Import / Export | hidden | Unchanged, still gated. | S. |
| `xf_05` | Private Vault | hidden | Unchanged, still a stub. | S. |
| `sha256` | SHA-256 verification | visible | Working, unchanged. | H. |

## Cross-cutting surfaces

| Area | State after this session | Evidence / remaining limit |
| --- | --- | --- |
| Job results | **Fixed:** a job whose items were all *unchanged* was reported as "completed" with no explanation. Non-published items now make the job partial/failed and the row shows the localized reason (for example "the image is already smaller"). | U. |
| CI | **Fixed:** the workflow had lost the opt-in extended-media job its own test contract requires; `scripts/test-universal-media-architecture.py` failed before this session. The `extended_media` input and the separate `Jumlo-Extended-Media-Debug-APK-*` artifact job are restored. | H. |
| Printing | Real `PrintManager` submission, preview, ranges, paper and margin handling. Page selection/reorder/rotation in the print preview was added in this working tree by a parallel change set and is covered by its own tests. | U. **D pending**: spooler, paper, physical output. |
| RTL / Arabic | Every string added in this session exists in English and Arabic. File names no longer depend on the display language. | H, S. |
| Accessibility | The batch-URL field now reports counts and problems in text, not only by colour. | S. **D pending**: TalkBack. |

## Not qualified in this environment

No Android device or emulator was available (the emulator image download was stopped to
conserve bandwidth), so **no instrumentation test, screenshot, camera capture, TalkBack
pass, physical print or frame-time measurement was executed**. Everything marked
**D pending** above is unverified on hardware. The APK builds, the unit tests and the host
suite pass; that is the strongest evidence this environment can produce.
