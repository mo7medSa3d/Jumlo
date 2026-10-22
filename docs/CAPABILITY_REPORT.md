# Capability report

Refreshed 2026-10-21. The rule is the same as before: a tool is only **yes** when
it was run end to end on a real input and the output was checked. Everything else
is **not verified**, and the reason is stated.

**What changed in this refresh.** Only the compression tool and the media service
were exercised. The rest of the rows are unchanged, because nothing about them
was run: the rules that would mark them **yes** need a device, and this
environment has no device, emulator or simulator.

| Tool | Works end to end | Evidence | Known failure |
|---|---|---|---|
| pdf_02 images_to_pdf | not verified | - | - |
| pdf_03 merge_pdf | not verified | - | - |
| pdf_05 split_pdf | not verified | - | - |
| pdf_04 organize_pages | not verified | - | - |
| pdf_06 extract_pages | not verified | - | - |
| pdf_07 pdf_to_images | not verified | - | - |
| pdf_01 pdf_viewer | not verified | - | - |
| pdf_compress | partly | `bash scripts/test-pdf-compression.sh --measure` on five generated 300 dpi scans and the six real phone photos in `tests/ocr_benchmark/pages`: Strong 96.0 % smaller on every scan, 28.1 % on the low-resolution photos; `scripts/test-pdf-compression.sh` ALL PASS | The compression itself was run on the host, not through the app: no device is reachable. A page with an `/SMask` or a one-bit raster is left alone on purpose, and is reported as a partial compression. The on-device number is not measured. |
| pdf_crop | not verified | - | - |
| pdf_annotate | not verified | - | - |
| pdf_stamp | not verified | - | - |
| pdf_signature | not verified | - | - |
| pdf_image | not verified | - | - |
| pdf_protect | not verified | `PdfSecurityQualificationTest` exists and passes, but the tool was not run end to end on a real input in this session | - |
| pdf_metadata | not verified | - | - |
| pdf_clean | not verified | `PdfMetadataCleanerTest` exists and passes, but the tool was not run end to end on a real input in this session | - |
| pdf_forms | not verified | - | - |
| pdf_ocr | not verified | - | The capability is blocked in `CapabilityPolicy` (`SEARCHABLE_OCR_INTEGRITY_QUALIFICATION_PENDING`) and no OCR engine is wired into the app |
| scan_doc | not verified | - | - |
| webpage_pdf | no | `CapabilityPolicy` marks it unavailable | `WEBPAGE_RENDER_SECURITY_QUALIFICATION_PENDING` |
| img_01 convert_images | not verified | - | - |
| img_02 resize_images | not verified | - | - |
| img_03 crop_rotate_flip | not verified | - | - |
| img_04 compress_images | not verified | - | - |
| img_exif | not verified | - | - |
| img_watermark | not verified | - | - |
| img_join | not verified | - | - |
| img_adjust | not verified | - | - |
| img_target | not verified | - | - |
| img_compare | not verified | - | The tool is kept out of the catalogue on purpose (`ToolCatalogue.hidden`) |
| img_stitch | not verified | - | - |
| dl_media | partly | `POST /extract` against the running service on real URLs: YouTube, TikTok, Facebook, a direct MP4 and a direct MP3 all returned HTTP 200 with formats — see `docs/media-service-diagnosis-2026-10-21.md` | The service was run, not the app: the client side is covered only by `RemoteExtractorRuntimeTest`, which fakes the service over a loopback socket. Instagram returns 422 `MEDIA_NO_USABLE_FORMAT` (a login wall). The audio path in the service converts nothing, and the client never asks for a muxed format. |
| dl_02 add_single_url | partly | The same direct MP4 and MP3 URLs returned HTTP 200 with formats from the running service | As above: the app was not run on a device |
| dl_03 batch_url_intake | not verified | - | - |
| dl_settings | not verified | - | - |
| file_02 browse_storage | not verified | - | - |
| file_07 create_zip | not verified | - | - |
| file_08 extract_zip | not verified | - | - |
| file_encrypted_zip | no | `CapabilityPolicy` marks it unavailable | `ENCRYPTED_ZIP_QUALIFICATION_PENDING` |
| file_batch_rename | not verified | - | - |
| file_duplicates | not verified | - | - |
| file_trash | not verified | - | - |
| file_drive | no | `CapabilityPolicy` marks it unavailable | `DRIVE_PRIVACY_QUALIFICATION_PENDING` |
| xf_05 secure_vault | no | `CapabilityPolicy` marks it unavailable | `VAULT_BACKEND_UNQUALIFIED` |
| sha256 | not verified | - | - |

## The suites that were run

| command | result |
|---|---|
| `./gradlew :app:testDebugUnitTest --offline --no-daemon --console=plain --no-build-cache` | BUILD SUCCESSFUL — 325 tests, 0 failures, 2 skipped (the two live media-service tests need `JUMLO_MEDIA_SERVICE`) |
| `bash scripts/verify-host.sh` | exit 0 — 181 Kotlin host cases, the PDF compression suite (ALL PASS), 16 Python suites, 0 failures |
| `bash scripts/test-pdf-compression.sh` | ALL PASS — the full JPEG path on a real 300 DPI scan, a document with a 150 dpi and a 300 dpi image, and an unreadable image that is reported without stopping the document |
| `bash scripts/test-pdf-compression.sh --measure` | the before/after table in `docs/compression-qualification-2026-10-21.md` |

## Process note

The `ccg` multi-model workflow did **not** run. The `ccg-workflow` runtime is
not installed in this environment and `npx ccg init` is an interactive terminal
program that asks for an API key, so the Codex and Antigravity calls described in
the plan were not executed. The review was performed by independent agents within
one harness. Nothing in this report should be read as independent verification by
two models.

## Media service health

```
$ curl -sS http://127.0.0.1:8731/health
{"ok": true, "ytdlp": "2026.08.19", "ffmpeg": true}
```

The extractor is the latest release (`2026.08.19`, published 2026-08-19).
