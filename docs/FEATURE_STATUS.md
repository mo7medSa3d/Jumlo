# Feature status — honest accounting

Statuses: **VERIFIED** = a named JVM/instrumented test passed in CI;
**IMPLEMENTED_UNTESTED** = backend + UI wired, no passing CI test yet;
**PARTIAL** = works only for a documented subset; **REMOVED** = UI entry
removed or made non-actionable.

> At the time of writing, **nothing is VERIFIED**: no CI run had completed
> successfully for this branch (CI was not waited for in this session).
> Statuses must be upgraded only with a CI run ID + test name in this file.
> Do not upgrade on the basis of code inspection.

## PDF (`tasks.json` P01–P03, E01–E11, F04)

| Feature | Status | Evidence / limit |
|---|---|---|
| Images → PDF | IMPLEMENTED_UNTESTED | JVM/instrumented tests existed pre-session; CI not passed |
| Page → PNG/JPEG export | IMPLEMENTED_UNTESTED | PdfRenderer handler; CI pending |
| Merge | IMPLEMENTED_UNTESTED | `PdfStructureCoreTest.merge_concatenates_pages_in_input_order` written, not CI-passed |
| Split | IMPLEMENTED_UNTESTED | `split_produces_one_file_per_group` written |
| Extract pages | IMPLEMENTED_UNTESTED | `extract_preserves_selected_order` written |
| Reorder / delete pages | IMPLEMENTED_UNTESTED | organize order list; `organize_applies_rotation_and_delete` written |
| Rotate pages | IMPLEMENTED_UNTESTED | `rotate_in_place_persists_degrees` written |
| Insert pages | IMPLEMENTED_UNTESTED | `insert_pages_from_another_document` written |
| Crop / page geometry | IMPLEMENTED_UNTESTED | cropBox only; no dedicated test yet |
| Compress (text preserved) | IMPLEMENTED_UNTESTED | `compress_keeps_text_and_shrinks_images` written; only flate 8-bit images downscaled |
| Overlay text | IMPLEMENTED_UNTESTED | Helvetica overlay; no dedicated test yet |
| Note annotations | IMPLEMENTED_UNTESTED | PDAnnotationText; no dedicated test yet |
| Watermark | IMPLEMENTED_UNTESTED | text stamp; no dedicated test yet |
| Page numbers | IMPLEMENTED_UNTESTED | text stamp; no dedicated test yet |
| Signature — imported image | IMPLEMENTED_UNTESTED | stampImage; no dedicated test yet |
| Signature — drawn | IMPLEMENTED_UNTESTED | Compose signature pad → PNG → stampImage; not device-tested |
| Password protect / remove | IMPLEMENTED_UNTESTED | `protect_then_remove_standard_password`, `wrong_password_fails_explicitly` written; owned-files-only is a UI/copy rule, not DRM bypass |
| Metadata edit | IMPLEMENTED_UNTESTED | `metadata_round_trip` written |
| AcroForm fill + flatten | IMPLEMENTED_UNTESTED | `fill_form_and_flatten` written |
| Compare (non-AI) | IMPLEMENTED_UNTESTED | `compare_flags_text_differences` written; text-only comparison |
| Redaction | IMPLEMENTED_UNTESTED | `redaction_removes_text_glyphs_in_region`, `redaction_outside_region_keeps_text` written; complex/multi-stream pages fail the job (`REDACTION_VERIFICATION_FAILED`) instead of faking removal |
| OCR → .txt | IMPLEMENTED_UNTESTED | ML Kit on-device; needs instrumented test |
| OCR → searchable PDF | PARTIAL | invisible text layer with coarse per-page-block positioning; word boxes not preserved |
| Camera scan | PARTIAL | CameraX capture + fixed inset-quad perspective; **no automatic edge detection**, quad is not user-draggable (fixed inset) — UI says so |
| Webpage → PDF | IMPLEMENTED_UNTESTED | WebView print adapter; login/DRM pages may print blank |

## Images (I01–I02, E12–E17)

| Feature | Status | Evidence / limit |
|---|---|---|
| Convert JPEG/PNG/WebP | IMPLEMENTED_UNTESTED | encoder handler; device codec matrix not run |
| Resize / crop / rotate / flip | IMPLEMENTED_UNTESTED | geometry JVM tests existed pre-session |
| EXIF report + stripping | IMPLEMENTED_UNTESTED | report + metadata-stripped re-encode; "selective" removal = report + strip-on-output, not per-tag surgical edit |
| Text watermark | IMPLEMENTED_UNTESTED | no dedicated test yet |
| Logo watermark | IMPLEMENTED_UNTESTED | second selected image; no dedicated test yet |
| Join / collage | IMPLEMENTED_UNTESTED | grid/strip; no dedicated test yet |
| Stitch screenshots | IMPLEMENTED_UNTESTED | vertical strip join; no overlap detection |
| Adjustments (brightness/contrast/saturation) | IMPLEMENTED_UNTESTED | ColorMatrix; no dedicated test yet |
| Padding / canvas | IMPLEMENTED_UNTESTED | white canvas pad in adjust tool |
| Target-size compression | IMPLEMENTED_UNTESTED | quality binary search; best-effort |
| Comparison | IMPLEMENTED_UNTESTED | dimension + mean-difference report |

## Files (A01–A02, E18, E21–E24)

| Feature | Status | Evidence / limit |
|---|---|---|
| ZIP create / extract | IMPLEMENTED_UNTESTED | pre-existing handlers + host tests |
| Encrypted ZIP create / extract | IMPLEMENTED_UNTESTED | zip4j AES-256; JVM `EncryptedZipTest` (round-trip + wrong password) written; ZIP-slip validated before extraction (`ZipSlipTest`) |
| Batch rename | IMPLEMENTED_UNTESTED | preview in handler (`batchRename_applies_pattern_and_uniquifies` written); renamed **copies** only, originals untouched |
| Duplicate detection (SHA-256) | IMPLEMENTED_UNTESTED | `duplicateGroups_finds_exact_groups` written; report only, no deletion |
| Managed trash | PARTIAL | no trash store; published outputs remain recoverable in Files until user deletes them. This is **not** a move-to-trash implementation — rename in UI/docs must not claim otherwise |
| SHA-256 verification | IMPLEMENTED_UNTESTED | pre-existing handler |

## Downloads / media (D01–D02, Phase 2)

| Feature | Status | Evidence / limit |
|---|---|---|
| Direct URL download + resume | IMPLEMENTED_UNTESTED | resume only with matching range+validator; otherwise restart-required error |
| Media analysis / format picker / audio-only | IMPLEMENTED_UNTESTED | extended-media build only (`-PextendedMedia=true`), debug-gated; **no live platform tested** |
| All 11 platforms | UNVERIFIED | see `docs/MEDIA_CAPABILITY_MATRIX.md`; UI must keep showing UNVERIFIED until a live CI/device result exists |

## Backend / platform (Phase 3–5)

| Item | Status | Evidence / limit |
|---|---|---|
| Room schema export | PARTIAL | v2 exported; **v1 JSON missing** — MigrationTestHelper blocked; manual migration test written (`DatabaseMigrationAndDaoTest`) |
| DAO tests | IMPLEMENTED_UNTESTED | instrumented `processingDao_roundTrips_jobs_outputs_sources` written |
| Process-death / interrupted publication | IMPLEMENTED_UNTESTED | JVM `PublicationRecoveryTest`: interrupted publication recovered by `reconcile()`, incomplete stage never published, publish idempotent. WorkManager process-death during download/merge/scan still lacks dedicated fault-injection tests |
| Local error log + export | IMPLEMENTED_UNTESTED | `ErrorLog` (codes only, sanitized, JVM `ErrorLogTest`) + Settings export action |
| Navigation Compose type-safe routes | IMPLEMENTED_UNTESTED | `@Serializable` routes + NavHost, tab saveState/restoreState, `jumlo://workspace` deep link, singleTop share-intent handling; not CI/device verified |
| Baseline Profile / macrobenchmark | IMPLEMENTED_UNTESTED | `:benchmark` module (cold-start MacrobenchmarkRule, BaselineProfileRule generator), profileinstaller in `:app`; targets (<1s cold, <5% jank) are goals, **not measured results** |
| Screenshot tests (light/dark/en/ar) | PARTIAL | semantics-level four-config instrumented tests (`ScreenConfigSemanticsTest`); golden-image Paparazzi tests NOT added (goldens cannot be recorded without a device/CI run) |
| Full Arabic localization of new screens | PARTIAL | scan/signature screens localized en+ar this session; NavHost workspace field labels still hardcoded English (MissingTranslation lint is now an error for resource-based strings, but hardcoded Compose strings need a manual resource pass) |
| CI | IMPLEMENTED_UNTESTED | build+unit+lint+release job and API 26/36 emulator job configured; not yet seen green |

## Removed / gated

| Item | Status | Reason |
|---|---|---|
| Encrypted vault (E25) | REMOVED (gated card, no action) | no qualified crypto backend |
| Automatic scan edge detection | REMOVED (not offered) | untested; only fixed-quad correction offered |
| Release-build processing | GATED | `BuildConfig.DEBUG` capability gate until CI + device qualification |
