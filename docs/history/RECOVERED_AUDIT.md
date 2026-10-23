# Source audit and implementation inventory

This is an inventory of the inspected source, including retained compatibility code. It is not a device qualification report. Build commands, actual APK sizes, executed tests and their limitations belong in [TEST_REPORT.md](TEST_REPORT.md); all destination types and Back behavior are in [ROUTES.md](ROUTES.md). The overview below is followed by complete tool and preference tables so that a smaller interface is not confused with removed processing features.

## Audit summary

| Area | Observed baseline | Current source |
| --- | --- | --- |
| Navigation | Home/Tools/raised Print/Files/Downloads dock; Settings borrowed Home selection; indicator coordinates did not share the tab cell bounds | Tools/Files/Home/Downloads/Settings; cell-contained animated indicator; Print is a detail; selected tab semantics and RTL mirroring |
| Workspace lifecycle | Nine object routes; active tool and selected job were implicit shared state; tab changes closed the workspace | Fourteen serialized destinations with tool/job/viewer arguments; saved root-tab stacks, one processing workspace under Tools, distinct fresh-entry versus restored-entry behavior |
| Home and discovery | Repeated search, suggestions, recent/frequent tools and download actions; duplicate onboarding dialogs | Four hero actions, real recent files, active jobs and draft continuation; one guide; catalog-only search, concise labels and expandable specialist tools |
| Files | Real imports, outputs, SAF, trash and batch actions already existed; third gallery mode, missing grouping/grid dates, folder preference remote from file management | Existing actions retained; list/grid, legacy mode migration, date/name/size/type sort, filters, date/type grouping, previews, usage and save-folder access |
| Settings | Network restrictions, concurrent-download setting, app motion/haptic controls, extraction server, profile/home customization and duplicate file-mode controls | Appearance, language, save folder, quality defaults, storage/cache/About; compatibility values explicitly inventoried below |
| Visual system | Blue-tinted dark background, outline assets and full extended icon artifact | Pure black background, neutral tonal surfaces, local filled/duotone icon subset and subsetted existing Arabic/Latin fonts |
| Scanner/PDF | Manual camera and optional smart-scanner entry; limited viewer; Office exports absent from catalog | Direct ML Kit multi-page PDF flow with gallery; internal reader; real DOCX/PPTX processing and Latin OCR, with limits described below |
| Work and storage | Existing Room/WorkManager/source/output architecture and real document/image/archive/print features | Architecture retained and strengthened: foreground failure handling, validated publication, durable encrypted download inputs and explicit error/recovery paths |
| Platform separation | Platform-independent policies mostly inside the Android app source tree | Four Kotlin/JVM core modules; Android adapters remain in `app`; this is not an iOS implementation or Kotlin Multiplatform conversion |

Source anchors: [navigation](app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt), [dock](app/src/main/java/com/jumlo/app/ui/components/QuietNavigation.kt), [Home](app/src/main/java/com/jumlo/app/ui/home/HomeScreen.kt), [Tools](app/src/main/java/com/jumlo/app/ui/tools/ToolsScreen.kt), [Files](app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt), [Settings](app/src/main/java/com/jumlo/app/ui/settings/SettingsScreen.kt), [theme](app/src/main/java/com/jumlo/app/ui/theme/Theme.kt).

## Modules and application architecture

The build includes six modules. Their dependency direction is defined in [settings.gradle.kts](settings.gradle.kts) and the module build files.

| Module | Runtime / dependencies | Responsibility |
| --- | --- | --- |
| `app` | Android, Java/Kotlin 17; depends on all four core modules | Compose UI, Android lifecycle and navigation, Hilt, Room/DataStore, workers, provider/MediaStore access, PDFBox/PdfRenderer, ML Kit, native media runtime and printing |
| `core-common` | Kotlin/JVM, Java library, JDK 17; no Android dependency | Shared capability/catalog policy, validation, job lifecycle, external-intake rules, naming and file/source policies |
| `downloader-core` | Kotlin/JVM; API dependency on `core-common` | HTTP/redirect/resume/restart rules, URL/Drive-link handling, transfer and media-format policy, process/native dependency rules and metadata models |
| `document-core` | Kotlin/JVM; API dependency on `core-common` | Page geometry/order/crop, drawing/annotation/layout policies, image-work and OCR-text rules, Office package writer and structural validation |
| `storage-core` | Kotlin/JVM; API dependency on `core-common` | Publication/file-copy policy, collision handling, safe storage paths, trash and temporary-file cleanup |
| `benchmark` | Android test module targeting the app | Macrobenchmark/UI Automator and baseline-profile tooling |

The cores use JVM/Java APIs and do not declare native/iOS targets. Android PDF rendering, camera/scanner launch, downloads, secrets, database and printing still require platform adapters for any future iOS app.

### ViewModels and state ownership

There are **five production ViewModel subclasses**: three application/feature ViewModels and two retained editor sessions. The latter do not include “ViewModel” in their class names.

| Class | Owner | State and persistence boundary |
| --- | --- | --- |
| [AppViewModel](app/src/main/java/com/jumlo/app/ui/AppViewModel.kt) | Activity; Hilt `AndroidViewModel` | Central settings, sources, outputs, jobs, trash, storage, processing form, dirty/busy/error states, tool-session epoch, intake and print requests. `DraftStore` persists the processing draft/recovery checkpoint; Room stores durable job/output records. Owns scan publication and preference actions. |
| [MediaViewModel](app/src/main/java/com/jumlo/app/ui/downloads/MediaViewModel.kt) | Media workspace navigation entry; Hilt `ViewModel` | URL text, inspected metadata/formats, actual available quality, audio/video selection, filename, progress and errors. `sessionEpoch` resets explicit fresh entry while preserving a restored tab entry. Unsubmitted text/metadata are session state; submitted URLs are encrypted for durable jobs. |
| [PrintViewModel](app/src/main/java/com/jumlo/app/printing/PrintViewModel.kt) | Print navigation entry; Hilt `AndroidViewModel` with `SavedStateHandle` | Print source/options, page selection/order/rotation, validation, undo/redo, preparation/render lock, service availability and spool status. Repository owns history, presets and favorites; Android PrintManager owns submitted print jobs. |
| [PdfPageEditorSession](app/src/main/java/com/jumlo/app/ui/workspace/PdfPageEditorSession.kt) | Containing workspace navigation entry; Compose `viewModel` | One active document's page edit history and selection. Large lists remain out of the saved-state Bundle. After process death, current pages are seeded from the durable draft and undo history starts fresh. |
| [VisualDocumentEditorSession](app/src/main/java/com/jumlo/app/ui/workspace/VisualDocumentEditorSession.kt) | Containing workspace navigation entry; Compose `viewModel` | One document's annotation/drawing timeline kept in memory across configuration changes, outside the saved-state Bundle. |

Home, Tools, Files, Settings, Storage, Scan and the PDF reader do not each have a separate ViewModel. They consume the existing owners and local Compose state. The nav graph is a single flat graph with root-tab save/restore, not five independent processing drafts.

### Processing and persistence boundaries

| Component | Source responsibility |
| --- | --- |
| [JumloApplication](app/src/main/java/com/jumlo/app/JumloApplication.kt) / [AppModule](app/src/main/java/com/jumlo/app/di/AppModule.kt) | Hilt graph, PDFBox resource initialization, HiltWorkerFactory, Room, OkHttp, work budget, media factory and encrypted-secret adapter |
| [AppDatabase](app/src/main/java/com/jumlo/app/data/local/AppDatabase.kt) | Room database `universal_utilities_sandbox.db`, schema version 2: Source, Job, Output and Draft entities, plus retained ManagedFile and DownloadTask legacy tables. Explicit migration and legacy download privacy cleanup. |
| [SourceStore](app/src/main/java/com/jumlo/app/data/source/SourceStore.kt) | Validated imported input records, immutable owned copies, provider grant and seekable-input handling |
| [DraftStore](app/src/main/java/com/jumlo/app/data/jobs/DraftStore.kt) | Durable workspace state/recovery and submission reservation/fingerprint |
| [JobStore](app/src/main/java/com/jumlo/app/data/jobs/JobStore.kt) | Durable request/progress records, unique `job:<id>` WorkManager scheduling, pause/cancel/retry/recovery and generation/lease coordination. Download admission uses the fixed scheduler policy; connected networks are allowed. |
| [EngineOperationWorker](app/src/main/java/com/jumlo/app/worker/EngineOperationWorker.kt) | Promotes long work to the data-sync foreground service, records infrastructure failures and propagates cancellation |
| [OperationDispatcher](app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt) | Checks capability and schema before routing to PDF, Office, image, archive, direct download, media, file-extras or OCR handlers. PDF/image paths share `BitmapWorkGate`. |
| [OutputStore](app/src/main/java/com/jumlo/app/data/output/OutputStore.kt) / [PublicOutputStore](app/src/main/java/com/jumlo/app/data/output/PublicOutputStore.kt) | Reserve, stage, inspect/verify and publish real output files; canonical private copies, default `Download/Jumlo` publication or selected SAF folder, explicit fallback messages, trash and reconciliation |
| [EphemeralSecretStore](app/src/main/java/com/jumlo/app/data/source/EphemeralSecretStore.kt) / [EncryptedDownloadSecrets](app/src/main/java/com/jumlo/app/data/source/EncryptedDownloadSecrets.kt) | Document/archive passwords stay in memory. Submitted download URLs use Android Keystore AES-GCM storage under no-backup storage; terminal cleanup removes them, while paused/retryable work may retain them. Bounded persistence reads and character-array cleanup are implemented. |
| [PrintRepository](app/src/main/java/com/jumlo/app/printing/PrintRepository.kt) | Printable snapshots, print history/presets/favorites and adapter preparation; printing is an Android PrintManager flow, not a simulated background conversion |

Scanner output follows `SmartScanResultStore → AppViewModel.publishScannedPdf → OutputStore`; publication is not represented as a fake queued job. The scanner validates/copies its result off the main thread. Navigation opens the result only if the same Scan entry remains active. The reader uses one serialized `PdfReaderSession`, a bounded 32 MB page cache and visible-page rendering. All framework renderer creation now passes through `PdfRendererFactory`; SDK 26–27 receives strict, disk-bounded PDFBox preflight after actual same-process rejection poisoned later valid sessions. Android 9 source removes the stale-error checks. Parser disagreement and later native content errors remain limits; source guards and regression presence do not establish completed recovery qualification.

The normal `extendedMedia=true` build packages the local extractor, FFmpeg and JavaScript runtime. Runtime composition may also use a previously configured remote service and direct-link fallback. `-PextendedMedia=false` is an explicit developer build without the packaged platform extractor; it must not be described as equivalent platform coverage. The native repack task and digest pins qualify packaged bytes, not live websites. Its system-library exclusions now retain bundled `libandroid-support.so` and `libandroid-glob.so` after the initial API 26 native-child linker failure exposed their omission. A subsequent FFmpeg linker failure exposed missing PCRE behind the GLib versioned-alias chain. The repacker now preserves exact DT_NEEDED names, follows physical archive-local aliases, includes every retained ELF in closure and validates both actual proposed payloads before writing. It restores PCRE and removes only the two previously dangling unused V4L aliases; Python bytes are unchanged. Native merge tasks track the repack script and pin manifest as inputs. The regenerated archive pins and current packaging checks do not by themselves qualify upgraded-install extraction or device execution; that history is in `TEST_REPORT.md`.

The initial direct HTTP/DNS inspection has a shared 20-second response budget across redirect attempts, after focused clipboard evidence showed analysis still pending beyond the existing UI assertion. WebView cookie initialization is synchronous and cannot be interrupted by that budget. The selected dock pill now uses primary/onPrimary; enabled unselected favorite glyphs use 0.75 opacity. The documented 144-pair static contrast review is a source calculation with explicit scope, not runtime accessibility certification.

## Screens and routes

The **five root tabs** are Tools, Files, Home, Downloads and Settings. The **nine additional destinations** are the processing tool, scanner, print setup, PDF viewer, selected job, all activity, Settings detail, Storage detail and legacy workspace. All fourteen use serialization route types; [ROUTES.md](ROUTES.md) lists their exact names, arguments, entry points and dock behavior.

Feature UI remains inside its destination: file menus/selection/trash, conversion chooser, processing presets, PDF page and visual editors, signature pad, media-format choices, print preview/history, preference dialogs and About/privacy. System SAF/photo/scanner/share/print screens are not fake Compose routes.

## Complete tool inventory

Authoritative inputs are [ToolCatalogue](core-common/src/main/kotlin/com/jumlo/app/core/ToolCatalogue.kt), [CapabilityRegistry](app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt), [CapabilityPolicy](core-common/src/main/kotlin/com/jumlo/app/core/CapabilityPolicy.kt), [WorkspaceScreen](app/src/main/java/com/jumlo/app/ui/workspace/WorkspaceScreen.kt) and the dispatcher. The registry exposes **47 IDs**, including the appended `sha256` entry. The catalog exposes **40: 22 primary and 18 advanced**. “Advanced” means reachable in an expandable category, not removed. Availability/evidence enums are policy metadata, not a replacement for current test logs.

### PDF and scanner: 12 primary, 9 advanced

| ID | Catalog | Operation / source boundary |
| --- | --- | --- |
| `pdf_to_docx` | Primary | PDF to DOCX paragraphs/page breaks; selectable text first, supported OCR fallback |
| `pdf_to_pptx` | Primary | PPTX slide per PDF page, page image plus editable text when available |
| `pdf_ocr` | Primary | PDF/image text extraction using existing text and Latin OCR; UTF-8 TXT and supported searchable-PDF output |
| `pdf_02` | Primary | Images to PDF |
| `pdf_03` | Primary | Merge PDFs/ranges |
| `pdf_05` | Primary | Split by page count or ranges |
| `pdf_04` | Primary | Reorder, rotate and remove pages |
| `pdf_compress` | Primary | PDF compression/downsampling with text retained where applicable |
| `pdf_07` | Primary | PDF pages to JPG/PNG |
| `pdf_signature` | Primary | Draw/import and place a signature |
| `pdf_clean` | Primary | Remove PDF authoring/metadata trail |
| `scan_doc` | Primary, Scanner group | Navigation to direct ML Kit scan; PDF only, gallery import, up to 100 pages |
| `pdf_06` | Advanced | Extract selected pages |
| `pdf_crop` | Advanced | Page/range crop box |
| `pdf_image` | Advanced | Add and place an image/logo |
| `pdf_forms` | Advanced | Fill AcroForm fields and optional flattening |
| `pdf_annotate` | Advanced | Highlight, underline, notes, shapes, text and ink |
| `pdf_stamp` | Advanced | Text watermark and page numbering |
| `pdf_metadata` | Advanced | Edit document properties |
| `pdf_protect` | Advanced | Standard PDF password protection/removal using supplied credentials |
| `pdf_01` | Advanced | Reader/import workspace; navigation capability, no processing job |

### Images: 6 primary, 4 advanced

| ID | Catalog | Operation |
| --- | --- | --- |
| `img_03` | Primary | Crop, rotate and flip |
| `img_02` | Primary | Resize by dimensions/percentage |
| `img_04` | Primary | Image compression |
| `img_01` | Primary | Batch JPG/PNG/WebP conversion |
| `img_join` | Primary | Grid/strip collage |
| `img_watermark` | Primary | Text/logo watermark |
| `img_adjust` | Advanced | Brightness, contrast, saturation and padding |
| `img_target` | Advanced | JPEG compression toward a byte target |
| `img_stitch` | Advanced | Vertical screenshot stitching |
| `img_exif` | Advanced | Inspect and selectively remove EXIF metadata |

### Files: 3 primary, 3 advanced

| ID | Catalog | Operation |
| --- | --- | --- |
| `file_07` | Primary | Create ZIP |
| `file_duplicates` | Primary | Exact duplicate report using SHA-256 |
| `file_batch_rename` | Primary | Preview naming pattern and publish renamed copies |
| `file_08` | Advanced | Extract ZIP with path/collision checks |
| `file_encrypted_zip` | Advanced | AES-encrypted ZIP creation/extraction |
| `sha256` | Advanced | Compute/compare actual file digests |

### Downloads: 1 primary, 2 advanced

| ID | Catalog | Operation |
| --- | --- | --- |
| `dl_media` | Primary | Inspect links and offer actual video/audio/file formats and qualities |
| `dl_02` | Advanced | Single direct download, including supported public Google Drive file links; uses the common media/direct entry UI |
| `dl_03` | Advanced | Validate and queue a batch of HTTP/HTTPS URLs |

### Seven registry entries outside the catalog

| ID | Actual status |
| --- | --- |
| `img_compare` | Processing implementation retained and capability enabled, but no normal catalog tile |
| `file_02` | Navigation compatibility; storage browsing is in Files |
| `file_trash` | Navigation compatibility; recoverable outputs/trash are in Files |
| `dl_settings` | Navigation compatibility to Settings; no separate network-settings tool |
| `webpage_pdf` | Hidden/blocked: `WEBPAGE_RENDER_SECURITY_QUALIFICATION_PENDING` |
| `file_drive` | Hidden/blocked: `DRIVE_PRIVACY_QUALIFICATION_PENDING`; authenticated Drive integration is distinct from supported public direct-download links |
| `xf_05` | Hidden/blocked private-vault concept: `VAULT_BACKEND_UNQUALIFIED` |

Two additional dispatcher IDs must not be mistaken for catalog tools: `pdf_compare` has processing-policy/handler support but no registry entry, while `pdf_redact` has a retained handler branch but fails the capability gate. Unknown IDs fail closed. Print, storage management, output reuse, share/save-as, trash/restore/purge and Favorites remain real screen actions rather than catalog IDs.

## Settings and migration inventory

The visible Settings screen contains theme, wallpaper accents on API 31+, language, save folder, default video/audio quality, storage, cache and About. About includes guide/privacy/diagnostics actions. File display mode is controlled in Files; print options and per-download format selection stay in their feature screens.

All **25 preference keys** in [UserPreferencesRepository](app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt) are listed below. “Retained” does not imply that a switch is still visible or that the value still controls behavior.

| Stored key | Current UI and behavior |
| --- | --- |
| `theme_mode` | Visible: SYSTEM/LIGHT/DARK; unknown values fall back to SYSTEM |
| `language` | Visible: ENGLISH/ARABIC; initial default follows whether the system locale is Arabic |
| `accent_color` | Visible wallpaper-accent switch reuses DYNAMIC/BLUE on API 31+; earlier VIOLET/TEAL/CORAL values remain readable. Dark surfaces stay neutral. |
| `download_dest_folder` | Visible save folder; validated SAF tree or canonical default folder; Files exposes the same selection |
| `default_video_quality` | Visible BEST/1080/720/480, default 1080; selection is constrained by inspected formats |
| `default_audio_quality` | Visible 320/192/128 kbps, default 192; no invented source-format availability |
| `file_view_mode` | Files list/grid control; old GALLERY, missing and unknown values map to GRID |
| `has_completed_onboarding` | Guide completion/replay state; one guide surface |
| `local_processing_presets_v1` | Active workspace presets, versioned and validated, at most 16 entries |
| `output_names_v1` | Active output display-name overrides |
| `favorite_output_ids` | Active Files favorites/filter |
| `notifications_granted` | Notification-permission bookkeeping; not a Settings toggle |
| `quick_action_ids` | Retained compatibility data; current Home uses the fixed four hero actions |
| `recent_tools` | Still recorded; repeated recent-tool sections removed from Home/Tools |
| `tool_counts` | Still recorded; repeated most-used sections removed from Home/Tools |
| `respect_reduced_motion` | Retained key/setter; settings flow fixes the removed preference to false. MainActivity follows the Android animator setting independently. |
| `haptic_on_commit` | Retained and decoded; MainActivity/common UI use the standard enabled behavior, without the old user-facing setting |
| `haptic_intensity` | Retained and decoded; MainActivity supplies LIGHT, without an intensity control |
| `ui_sounds` | Hidden retained preference still controls `LocalAppSoundsEnabled`; default true |
| `max_concurrent_downloads` | Retained key/setter; settings flow and scheduler use the fixed policy of two |
| `wifi_only_default` | Retained key/setter; effective default is false and download scheduling allows connected networks |
| `font_scale` | Hidden retained SMALL/NORMAL/LARGE multiplier remains active on top of Android font scale |
| `media_service_url_v1` | Hidden legacy configuration snapshot can still supply the optional remote extractor; normal build defaults to the packaged runtime |
| `media_service_token_v1` | Hidden legacy service-auth preference retained in DataStore; distinct from the encrypted per-job download-URL store |
| `media_rights_accepted` | Compatibility flow/setter retained; current media submission has no rights modal or withdrawal control |

Legacy per-job Wi-Fi/unmetered/HTTP fields remain deserializable in old draft/settings models. Current execution normalizes removed restrictions rather than allowing an old hidden preference to block transfers. HTTP/HTTPS URL and redirect safety validation still applies. File sort/filter/group/selection use saveable screen state; print presets/history/favorites use `PrintRepository` and `SavedStateHandle`, not the above preference table.

## Design, assets and accessibility source changes

| Area | Current source decision |
| --- | --- |
| Color | OLED background `#000000`; neutral dark surfaces `#101010`, `#151515`, `#1D1D1D`, `#262626`, `#303030`; neutral light surfaces. Dynamic color changes accents without tinting the neutral surface ladder. |
| Components | Tonal cards/buttons/chips, larger spacing and clear type hierarchy; common borders and toolbar search removed; dock occupies Scaffold space. |
| Icons | Local filled/duotone vectors with action-specific shapes: 91 vector glyphs and 98 compatibility properties. Production imports use `com.jumlo.app.ui.icons`; no extended Material Icons dependency. The brand mark remains a separate asset. |
| Typography | The existing Latin/Arabic fonts are subsetted, not replaced with a downloaded family. Two TTFs total 353,508 bytes; script retains required Latin/Arabic/diacritic/punctuation shaping coverage and the 400–700 weight range where supported. Modified internal names are Jumlo Sans/Jumlo Sans Arabic; original resource identifiers remain stable. See [subset-fonts.py](tools/subset-fonts.py). |
| Semantics/RTL | Tab roles, selection and labels; minimum touch targets; logical start/end layouts, mirrored navigation and localized dates. Files sorting uses radio semantics, current mode exposes state description, and restored selection waits for loaded data. |
| Motion | System animator policy; reduced-motion transitions use no/snap motion. Predictive Back handler exists, but physical gesture qualification requires runtime evidence. |
| External input | SAF/photo-picker intake and content-URI drag/drop; temporary drag grants outlive asynchronous import and release on completion or Activity destruction. |

### Removed dependencies and retained inactive code

| Item | Disposition |
| --- | --- |
| `material-icons-extended` | Dependency and version-catalog alias removed; used symbols supplied locally |
| CameraX camera2/lifecycle/view | Dependencies and aliases removed with the manual camera scan flow |
| CAMERA permission | Removed; scanner launch is owned by Play services |
| READ/WRITE_EXTERNAL_STORAGE permissions | Removed; SAF inputs and scoped/default output adapters cover current storage paths |
| Bundled text-recognition artifact | Replaced by the unbundled Play-services Latin OCR dependency; no claim that first-use OCR works without its model |
| Custom post-content splash | Activity now uses the Android SplashScreen API with bounded readiness hold/exit. The old `JumloSplash` composable definition remains inactive. |
| Account/profile modal, Home action editor, accent picker | Definitions and compatibility data remain, but current navigation does not expose these old surfaces. They are not claimed as deleted code. |
| Large Office library | None added; DOCX/PPTX use the small ZIP/XML writer in `document-core` with structural validation |

## Dependency and build inventory

Pinned versions come from [libs.versions.toml](gradle/libs.versions.toml), [app/build.gradle.kts](app/build.gradle.kts) and [benchmark/build.gradle.kts](benchmark/build.gradle.kts). This table records declarations, not a transitive license/security certification.

| Group | Declared dependencies / versions |
| --- | --- |
| Toolchain | AGP 8.10.1; Kotlin 2.0.21; KSP 2.0.21-1.0.28; Java 17; compile/target SDK 36; build tools 36.0.0; min SDK 26 |
| Android/UI | Core KTX 1.15.0; AppCompat 1.7.0; Activity Compose 1.9.3; Lifecycle 2.8.7; Compose BOM 2024.11.00, Material3/UI/graphics/window-size/tooling; Navigation Compose 2.8.4; SplashScreen 1.0.1 |
| Dependency injection | Hilt 2.54; AndroidX Hilt navigation/work/compiler 1.2.0 |
| Persistence/work | Room 2.7.2; DataStore preferences 1.1.1; WorkManager 2.10.0 |
| Networking/images | OkHttp 4.12.0; Coil 2.7.0; ExifInterface 1.3.7; DocumentFile 1.0.1; Window 1.3.0 |
| Kotlin runtime | Coroutines 1.9.0; kotlinx serialization JSON 1.7.3 |
| Documents/archive | PDFBox Android 2.0.27.0; Zip4j 2.11.5 |
| Scanner/OCR | Play-services ML Kit document scanner 16.0.0; unbundled text recognition 19.0.1 |
| Media | youtubedl-android library + FFmpeg 0.18.1 in the normal build; packaged/pinned extractor and JavaScript runtime assets |
| Performance | ProfileInstaller 1.4.1; macrobenchmark 1.3.4; UI Automator 2.3.0 |
| Tests only | JUnit 4.13.2; AndroidX JUnit 1.2.1; Espresso 3.6.1; Compose UI test via the BOM; Room testing; coroutine test; Turbine 1.2.0 |

Release and compact builds enable R8 and resource shrinking and target ARM64; debug includes ARM64 and x86_64. The five x86_64 native repair assets live under `app/src/mediaDebug/assets`, included only in the full-runtime debug build; common assets retain ARM64 repairs and shared metadata. AAB ABI/density/language splits are enabled, with Arabic/English resources. Compact uses debug signing for personal installation; it is not production release signing. `verifyCompactApkSize` checks the actual full-runtime compact APK against **60,000,000 bytes** and rejects `extendedMedia=false` as a shortcut. Whether the current artifact passes is an executed-result question for the test report.

## Explicit limits and verification boundaries

- **Office conversion:** Four original host-generated packages were opened/rendered by the installed LibreOffice alpha application, with preserved input hashes and documented synthetic-fixture limitations; this does not establish Microsoft Office or Android end-to-end compatibility. DOCX reconstructs editable paragraphs/page breaks, not arbitrary original tables/layout. PPTX preserves page appearance using an image per slide and adds editable text when available; it does not reconstruct every PDF object as a native PowerPoint shape. See [PdfOfficeOperationHandler](app/src/main/java/com/jumlo/app/data/processing/PdfOfficeOperationHandler.kt) and [OfficePackageWriter](document-core/src/main/kotlin/com/jumlo/app/core/office/OfficePackageWriter.kt).
- **OCR:** the included recognition path is Latin. Existing selectable Arabic PDF text can be extracted; Arabic scanned-image OCR is not implemented by that model. UTF-8 text is preserved; searchable-PDF output is conditional on representable font text rather than emitting corrupt text. Play services/model availability and first download remain runtime dependencies.
- **Scanner:** ML Kit/Play-services availability is required. Launch, cancellation, retry and result-publication errors have real UI paths; the removed manual camera is not a hidden fallback claim.
- **Media:** formats/qualities come from inspection. Platform URLs, authentication/geo restrictions, cookies, changing upstream extractors and server behavior require live qualification. Host parsing and packaged-byte verification do not establish platform success.
- **Storage/printing:** revoked SAF grants, external-provider failures, available print services and physical output require runtime qualification. Source review confirms explicit paths and errors, not successful physical printing or every provider combination.
- **Accessibility/navigation:** semantics, RTL resources, layout rules and tests exist. They do not certify every device width, font scale, TalkBack sequence, interruption or predictive gesture without executed observations.

Navigation/Files tests include [FiveTabNavigationTest](app/src/androidTest/java/com/jumlo/app/ui/FiveTabNavigationTest.kt), [JumloUiTest](app/src/androidTest/java/com/jumlo/app/ui/JumloUiTest.kt), [PreferencesPersistenceTest](app/src/androidTest/java/com/jumlo/app/data/PreferencesPersistenceTest.kt) and [FileGroupingTest](app/src/test/java/com/jumlo/app/ui/files/FileGroupingTest.kt). Broader tests include real database, output, downloader, document, Office and printing boundaries. Their presence is source evidence only. The authoritative executed/pass/fail/not-run record is [TEST_REPORT.md](TEST_REPORT.md).
