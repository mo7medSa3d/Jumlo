# Icon map

All 47 registry entries have distinct vector geometry, including the three pre-existing disabled entries. Assets are official **Material Symbols Rounded, filled, optical size 24**, converted to Android 24dp vector drawables without a font payload.

Pinned upstream commit: `10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7` in [google/material-design-icons](https://github.com/google/material-design-icons/tree/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7). License: [Apache-2.0](https://github.com/google/material-design-icons/blob/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/LICENSE), bundled in `app/src/main/assets/licenses/material-symbols-Apache-2.0.txt`. Each vector records its source symbol and pinned commit in a comment; exact source URLs are listed below. Most Android vectors use a 960x960 viewport with a translated group for the original SVG negative Y coordinates. Batch Rename uses the original 24x24 path and viewport without a translation.

The production mapping lives in `ui/components/ToolIcons.kt`. The generic document fallback is used only for unknown identifiers; all registered tools have a dedicated mapping. `scripts/tests/test-tool-icons.py` checks registry coverage, mapping coverage, dimensions, nonempty unique geometry, and bundled license. Executed result: PASS (47/47); this is a static asset check, not device visual acceptance.

| Tool ID | Tool | Android vector | Official symbol | Exact source |
|---|---|---|---|---|
| `pdf_to_docx` | PDF to Word | `ic_tool_pdf_to_docx.xml` | `article` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/article/materialsymbolsrounded/article_fill1_24px.svg) |
| `pdf_to_pptx` | PDF to PowerPoint | `ic_tool_pdf_to_pptx.xml` | `slideshow` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/slideshow/materialsymbolsrounded/slideshow_fill1_24px.svg) |
| `pdf_02` | Images to PDF | `ic_tool_pdf_02.xml` | `picture_as_pdf` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/picture_as_pdf/materialsymbolsrounded/picture_as_pdf_fill1_24px.svg) |
| `pdf_03` | Merge PDF | `ic_tool_pdf_03.xml` | `merge` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/merge/materialsymbolsrounded/merge_fill1_24px.svg) |
| `pdf_05` | Split PDF | `ic_tool_pdf_05.xml` | `content_cut` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/content_cut/materialsymbolsrounded/content_cut_fill1_24px.svg) |
| `pdf_04` | Organize Pages | `ic_tool_pdf_04.xml` | `view_module` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/view_module/materialsymbolsrounded/view_module_fill1_24px.svg) |
| `pdf_06` | Extract Pages | `ic_tool_pdf_06.xml` | `file_open` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/file_open/materialsymbolsrounded/file_open_fill1_24px.svg) |
| `pdf_07` | PDF to Images | `ic_tool_pdf_07.xml` | `image` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/image/materialsymbolsrounded/image_fill1_24px.svg) |
| `pdf_01` | View PDF | `ic_tool_pdf_01.xml` | `menu_book` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/menu_book/materialsymbolsrounded/menu_book_fill1_24px.svg) |
| `pdf_compress` | Compress PDF | `ic_tool_pdf_compress.xml` | `compress` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/compress/materialsymbolsrounded/compress_fill1_24px.svg) |
| `pdf_crop` | Crop PDF Pages | `ic_tool_pdf_crop.xml` | `crop` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/crop/materialsymbolsrounded/crop_fill1_24px.svg) |
| `pdf_annotate` | Annotate PDF | `ic_tool_pdf_annotate.xml` | `edit_note` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/edit_note/materialsymbolsrounded/edit_note_fill1_24px.svg) |
| `pdf_stamp` | Watermark & Page Numbers | `ic_tool_pdf_stamp.xml` | `branding_watermark` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/branding_watermark/materialsymbolsrounded/branding_watermark_fill1_24px.svg) |
| `pdf_signature` | Sign PDF | `ic_tool_pdf_signature.xml` | `signature` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/signature/materialsymbolsrounded/signature_fill1_24px.svg) |
| `pdf_image` | Add Image to PDF | `ic_tool_pdf_image.xml` | `add_photo_alternate` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/add_photo_alternate/materialsymbolsrounded/add_photo_alternate_fill1_24px.svg) |
| `pdf_protect` | Password Protect PDF | `ic_tool_pdf_protect.xml` | `lock` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/lock/materialsymbolsrounded/lock_fill1_24px.svg) |
| `pdf_metadata` | PDF Metadata | `ic_tool_pdf_metadata.xml` | `info` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/info/materialsymbolsrounded/info_fill1_24px.svg) |
| `pdf_clean` | Clean Metadata | `ic_tool_pdf_clean.xml` | `shield` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/shield/materialsymbolsrounded/shield_fill1_24px.svg) |
| `pdf_forms` | Fill PDF Form | `ic_tool_pdf_forms.xml` | `assignment` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/assignment/materialsymbolsrounded/assignment_fill1_24px.svg) |
| `pdf_ocr` | Extract Text | `ic_tool_pdf_ocr.xml` | `text_snippet` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/text_snippet/materialsymbolsrounded/text_snippet_fill1_24px.svg) |
| `scan_doc` | Smart Scan | `ic_tool_scan_doc.xml` | `document_scanner` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/document_scanner/materialsymbolsrounded/document_scanner_fill1_24px.svg) |
| `img_01` | Convert Images | `ic_tool_img_01.xml` | `photo_library` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/photo_library/materialsymbolsrounded/photo_library_fill1_24px.svg) |
| `img_02` | Resize Images | `ic_tool_img_02.xml` | `photo_size_select_large` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/photo_size_select_large/materialsymbolsrounded/photo_size_select_large_fill1_24px.svg) |
| `img_03` | Crop, Rotate & Flip | `ic_tool_img_03.xml` | `crop_rotate` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/crop_rotate/materialsymbolsrounded/crop_rotate_fill1_24px.svg) |
| `img_04` | Compress Images | `ic_tool_img_04.xml` | `photo_size_select_small` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/photo_size_select_small/materialsymbolsrounded/photo_size_select_small_fill1_24px.svg) |
| `img_exif` | EXIF Inspector | `ic_tool_img_exif.xml` | `perm_media` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/perm_media/materialsymbolsrounded/perm_media_fill1_24px.svg) |
| `img_watermark` | Watermark Images | `ic_tool_img_watermark.xml` | `texture` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/texture/materialsymbolsrounded/texture_fill1_24px.svg) |
| `img_join` | Join / Collage | `ic_tool_img_join.xml` | `dashboard` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/dashboard/materialsymbolsrounded/dashboard_fill1_24px.svg) |
| `img_adjust` | Adjust & Pad | `ic_tool_img_adjust.xml` | `tune` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/tune/materialsymbolsrounded/tune_fill1_24px.svg) |
| `img_target` | Target Size | `ic_tool_img_target.xml` | `speed` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/speed/materialsymbolsrounded/speed_fill1_24px.svg) |
| `img_compare` | Compare Images | `ic_tool_img_compare.xml` | `compare` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/compare/materialsymbolsrounded/compare_fill1_24px.svg) |
| `img_stitch` | Stitch Screenshots | `ic_tool_img_stitch.xml` | `view_agenda` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/view_agenda/materialsymbolsrounded/view_agenda_fill1_24px.svg) |
| `dl_media` | Video, Audio & File Downloads | `ic_tool_dl_media.xml` | `download` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/download/materialsymbolsrounded/download_fill1_24px.svg) |
| `dl_02` | New Direct Download | `ic_tool_dl_02.xml` | `add_link` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/add_link/materialsymbolsrounded/add_link_fill1_24px.svg) |
| `dl_03` | Batch URL Intake | `ic_tool_dl_03.xml` | `download_for_offline` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/download_for_offline/materialsymbolsrounded/download_for_offline_fill1_24px.svg) |
| `dl_settings` | Download Settings | `ic_tool_dl_settings.xml` | `settings` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/settings/materialsymbolsrounded/settings_fill1_24px.svg) |
| `file_02` | Browse Storage | `ic_tool_file_02.xml` | `folder_open` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/folder_open/materialsymbolsrounded/folder_open_fill1_24px.svg) |
| `file_07` | Create ZIP Archive | `ic_tool_file_07.xml` | `folder_zip` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/folder_zip/materialsymbolsrounded/folder_zip_fill1_24px.svg) |
| `file_08` | Extract ZIP Archive | `ic_tool_file_08.xml` | `unarchive` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/unarchive/materialsymbolsrounded/unarchive_fill1_24px.svg) |
| `file_encrypted_zip` | Encrypted ZIP | `ic_tool_file_encrypted_zip.xml` | `enhanced_encryption` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/enhanced_encryption/materialsymbolsrounded/enhanced_encryption_fill1_24px.svg) |
| `file_batch_rename` | Batch Rename (copies) | `ic_tool_file_batch_rename.xml` | `drive_file_rename_outline` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/drive_file_rename_outline/materialsymbolsrounded/drive_file_rename_outline_fill1_24px.svg) |
| `file_duplicates` | Duplicate Finder | `ic_tool_file_duplicates.xml` | `copy_all` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/copy_all/materialsymbolsrounded/copy_all_fill1_24px.svg) |
| `file_trash` | Managed Trash Info | `ic_tool_file_trash.xml` | `delete` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/delete/materialsymbolsrounded/delete_fill1_24px.svg) |
| `file_drive` | Google Drive Import / Export | `ic_tool_file_drive.xml` | `cloud_sync` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/cloud_sync/materialsymbolsrounded/cloud_sync_fill1_24px.svg) |
| `sha256` | SHA-256 verification | `ic_tool_sha256.xml` | `verified` | [Pinned SVG](https://raw.githubusercontent.com/google/material-design-icons/10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7/symbols/web/verified/materialsymbolsrounded/verified_fill1_24px.svg) |

## Annotation toolbar symbols

These nine additional controls are also official Rounded/filled/24px symbols from the same commit and license. They do not replace or reuse tool-card assets.

| Control | Vector | Symbol |
|---|---|---|
| highlight | `ic_annotation_highlight.xml` | `ink_highlighter` |
| underline | `ic_annotation_underline.xml` | `format_underlined` |
| strikethrough | `ic_annotation_strikethrough.xml` | `format_strikethrough` |
| note | `ic_annotation_note.xml` | `sticky_note_2` |
| square | `ic_annotation_square.xml` | `rectangle` |
| circle | `ic_annotation_circle.xml` | `circle` |
| textbox | `ic_annotation_textbox.xml` | `text_fields` |
| ink | `ic_annotation_ink.xml` | `draw` |
| text | `ic_annotation_text.xml` | `title` |

## Presentation

Tool tiles render a 24dp vector inside a 56dp tinted rounded container. The workspace header uses the same vector inside a 44dp decorative container, with a separate 48dp back target. Icons are tinted through the existing Material color roles; selection does not depend on color alone. The tool name supplies the accessible label, so duplicate decorative image announcements are avoided. Screenshot/TalkBack/200% scaling acceptance remains a device gate.
