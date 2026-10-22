# Jumlo Android Utilities App — Audit & Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Audit and rebuild the Jumlo Kotlin Android utilities app (PDF tools + internet downloader) to fix critical crashes, modernize the UI to 2026 Material 3 OLED standards, streamline tools/settings, add PDF → Word (`.docx`), PDF → PowerPoint (`.pptx`), and unbundled ML Kit OCR, and optimize APK size under 60 MB while keeping core modules portable for iOS.

**Architecture:** Single-activity Jetpack Compose app (`MainActivity`) using Hilt DI, Navigation Compose with `@Serializable` type-safe routes, Room v2 (`jumlo.db`), Preferences DataStore, and WorkManager (`EngineOperationWorker` with `foregroundServiceType="dataSync"`). Core business logic (`core/`, `data/pdf/`, `data/media/`) is isolated behind clean Kotlin interfaces (`MediaExtractorEngine`, `PdfDocumentEngine`, `FileStorageEngine`) for future Kotlin Multiplatform (KMP) iOS sharing.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2025.02.00), Material 3, Navigation Compose 2.8.8, Hilt 2.52, Room 2.6.1, WorkManager 2.10.0, OkHttp 4.12.0, Coil 2.7.0, PDFBox-Android 2.0.27.0, Android `PdfRenderer`, ML Kit Document Scanner (`play-services-mlkit-document-scanner:16.0.0-beta1`), Unbundled ML Kit Text Recognition (`play-services-mlkit-text-recognition:19.0.1`), AndroidX Core SplashScreen (`core-splashscreen:1.0.1`).

---

## Task 1: Build Configuration, Dependencies & SplashScreen API
**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `gradle.properties`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/themes.xml`

**Steps:**
1. Remove `androidx-compose-material-icons-extended` from `gradle/libs.versions.toml` and `app/build.gradle.kts` to save ~6–8 MB of unstripped icon metadata and drawables.
2. Add `androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }` (`1.0.1`) and `play-services-mlkit-text-recognition = { group = "com.google.android.gms", name = "play-services-mlkit-text-recognition", version.ref = "mlkitTextRecognition" }` (`19.0.1`).
3. Enable `android.enableR8.fullMode=true` in `gradle.properties`.
4. Configure `bundle` splits (language + ABI) and `androidResources.localeFilters += listOf("en", "ar")` in `app/build.gradle.kts`, shipping `arm64-v8a` for release and `arm64-v8a` + `x86_64` for debug.
5. Configure `Theme.Jumlo.Starting` using `Theme.SplashScreen` in `themes.xml` and call `installSplashScreen()` in `MainActivity.onCreate()`.

## Task 2: Pure-Kotlin OOXML Builders (`.docx` & `.pptx`), Unbundled OCR, and Real PDF Compression
**Files:**
- Create: `app/src/main/java/com/jumlo/app/core/OoxmlBuilder.kt`
- Create: `app/src/main/java/com/jumlo/app/data/pdf/OcrEngine.kt`
- Create: `app/src/main/java/com/jumlo/app/core/PortableEngines.kt`
- Modify: `app/src/main/java/com/jumlo/app/core/PdfPageGeometry.kt`
- Modify: `app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt`
- Modify: `app/src/main/java/com/jumlo/app/data/processing/OcrWebHandler.kt`

**Steps:**
1. Implement `OoxmlDocxBuilder` to construct spec-compliant Word `.docx` archives (`[Content_Types].xml`, `_rels/.rels`, `word/_rels/document.xml.rels`, `word/styles.xml`, `word/settings.xml`, `word/document.xml`) with paragraph detection, heading heuristics, RTL (`<w:bidi/>`) paragraph formatting for Arabic text, and page breaks.
2. Implement `OoxmlPptxBuilder` to construct spec-compliant PowerPoint `.pptx` archives (16:9 / 4:3 widescreen presentation XML, slide masters, slide layouts, embedded rendered page PNGs in `ppt/media/ page_N.png`, and editable text/notes shapes per slide).
3. Implement `OcrEngine` wrapping `com.google.mlkit.vision.text.TextRecognition` (`TextRecognizerOptions.DEFAULT_OPTIONS`) to run unbundled OCR on rendered PDF page bitmaps when a PDF page has no extractable text layer or when `pdf_ocr` is invoked.
4. Enhance `PdfCompressionPolicy` and `PdfOperationHandler` to support explicit `LOW` (`BEST_QUALITY`), `MEDIUM` (`BALANCED`), and `HIGH` (`STRONG`) compression tiers with real bitmap downsampling + JPEG recompression and before/after byte-size estimation.

## Task 3: Tool Catalogue Cleanup & Registration
**Files:**
- Modify: `app/src/main/java/com/jumlo/app/core/CapabilityRegistry.kt`
- Modify: `app/src/main/java/com/jumlo/app/core/CapabilityPolicy.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/model/ToolCatalogue.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/model/WorkspaceDraft.kt`

**Steps:**
1. Remove low-value/bloated tools (`xf_05` AES Vault, `file_drive` Cloud Relay, `webpage_pdf` Web to PDF, `img_compare` Image Compare, `file_trash` Trash Bin).
2. Register `pdf_to_docx` (PDF → Word `.docx`), `pdf_to_pptx` (PDF → PowerPoint `.pptx`), and `pdf_ocr` (OCR PDF to Searchable PDF / Text).
3. Ensure every tool opens with a fresh `WorkspaceDraft(toolId = id)` in `AppViewModel.selectTool()` so no file from a previous tool carries over.

## Task 4: Fix Critical Link-Pasting Crash & Downloader Flow
**Files:**
- Modify: `app/src/main/java/com/jumlo/app/MainActivity.kt`
- Modify: `app/src/main/java/com/jumlo/app/data/media/MediaProcessRunner.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/viewmodel/MediaViewModel.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/screens/MediaWorkspace.kt`
- Modify: `app/src/main/java/com/jumlo/app/data/media/SocialMediaDirectResolver.kt`

**Steps:**
1. Wrap all `clipboard.getText()` reads in `MediaWorkspace.kt` in `runCatching` and remove initial composition-time clipboard scraping that crashes on restricted Android 14+ clipboards.
2. Guard `Os.kill(-pid, signal)` in `MediaProcessRunner.kt` so it never signals `pid <= 1` or the current process group (`Os.getpgid(0)`), preventing `SIGTERM`/`SIGKILL` from killing the app process itself!
3. Ensure video/audio mode selector and quality/bitrate pickers in `MediaWorkspace.kt` are strictly hidden until `FetchState.Ready` (after media metadata is fetched).
4. Add LinkedIn (`linkedin.com`) and Google Drive (`drive.google.com`) handling to `SocialMediaDirectResolver.kt` and platform detection badges in `MediaWorkspace.kt`.

## Task 5: Smart Scan Direct Launch
**Files:**
- Modify: `app/src/main/java/com/jumlo/app/ui/screens/ScanScreen.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/components/SmartDocumentScanButton.kt`

**Steps:**
1. Update `ScanScreen.kt` so entering Smart Scan immediately launches ML Kit Document Scanner (`GmsDocumentScanning.getClient`) with `SCANNER_MODE_FULL`, `RESULT_FORMAT_PDF`, `RESULT_FORMAT_JPEG`, multi-page capture, and `setGalleryImportAllowed(true)`.
2. Remove the manual preview → capture → correct default flow; keep CameraX only as a fallback if Play Services Document Scanner cannot be initialized on the device.

## Task 6: 5-Slot Navigation (Home Centered), Active Indicator Fix, and Type-Safe Routes
**Files:**
- Modify: `app/src/main/java/com/jumlo/app/ui/components/QuietNavigation.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/components/QuietTopBars.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt`

**Steps:**
1. Update `QuietFloatingDock` to 5 slots in exact order: `0 -> TOOLS`, `1 -> FILES`, `2 -> HOME` (Center), `3 -> DOWNLOADS`, `4 -> SETTINGS`.
2. Fix the active indicator width & offset calculation (`val innerWidth = maxWidth - 12.dp; val segment = innerWidth / 5; val indicatorX = 6.dp + segment * slot`) so the indicator stays strictly contained and centered inside each slot.
3. Remove the top-bar search icon from `QuietRootTopBar`.
4. Add `@Serializable` routes (`HomeRoute`, `ToolsRoute`, `PdfCompressRoute`, `ScanRoute`, `DownloadsRoute`, `FilesRoute`, `SettingsRoute`, `SettingsStorageRoute`, `WorkspaceRoute`, `ActivityRoute`, `PrintRoute`) and preserve per-tab back stacks.

## Task 7: 2026 Visual Design, OLED Dark Mode, Rebuilt PDF Viewer, and Files Grid/List
**Files:**
- Modify: `app/src/main/java/com/jumlo/app/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/components/QuietSurfaces.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/components/ModernToolIcon.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/screens/HomeScreen.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/screens/ToolsScreen.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/screens/WorkspaceScreen.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/screens/FilesScreen.kt`

**Steps:**
1. Set `DarkBackground = Color(0xFF000000)` and neutral gray dark surfaces (`#121214`, `#18181B`, `#222226`, `#2C2C31`) with zero blue tint and WCAG AA contrast.
2. Remove `BorderStroke` across `QuietCard`, `QuietSearchBar`, `FileMenuRow`, `MediaWorkspace`, `FilesScreen`, and `WorkspaceScreen`.
3. Rebuild the PDF viewer (`InputPreview` in `WorkspaceScreen.kt`) with smooth scrolling, pinch-to-zoom (`detectTransformGestures`), horizontal page thumbnail strip, and dark-mode page rendering filter.
4. Restrict `FilesScreen.kt` to `GRID` and `LIST` view modes (removing `GALLERY`), add category grouping (`Group by`), sort/filter, delete/delete all, storage breakdown card, and visible changeable save folder bar.

## Task 8: Settings Simplification & Reactivity
**Files:**
- Modify: `app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/viewmodel/AppViewModel.kt`
- Modify: `app/src/main/java/com/jumlo/app/ui/screens/SettingsScreen.kt`

**Steps:**
1. Remove UI toggles for reduce-motion (animations always enabled), haptics on/off & intensity, Wi-Fi-only downloads, HTTP/HTTPS options, and concurrent downloads.
2. Keep/add Theme (`SYSTEM`, `LIGHT`, `DARK` + Dynamic Color toggle), Language (`en`, `ar` with instant RTL/LTR switching), Save Location (real path + SAF folder picker + reset), Default Video Quality (`1080p`, `720p`, `480p`, `Best`), Default Audio Quality (`MP3 320kbps`, `MP3 192kbps`, `M4A`, `Original`), Storage Usage breakdown, Clear Cache, and About.
