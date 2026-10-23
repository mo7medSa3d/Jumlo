You are a principal Android engineer and product designer. Take my Kotlin + Jetpack Compose + Material 3 app (Jumlo: PDF tools, image tools, file tools, and a media downloader) from its current state to a production-ready release that can pass Google Play review. You own the outcome end to end: audit, fix, build, test, and document. Do not stop to ask questions unless you are truly blocked. Make reasonable decisions, record them in DECISIONS.md, and move on.

Hard rules:
- Never claim something works unless you built it and tested it on a device or emulator. Any item without evidence is marked NOT VERIFIED in TEST_REPORT.md.
- Do not reduce functionality to make things easier. Fix it or document the blocker.
- Keep the release arm64-v8a APK under 60 MB. Report the size before and after, with the top size contributors.

## Phase 0: Audit (produce AUDIT.md first)
- List every screen, route, tool, ViewModel, setting, and dependency.
- Check minSdk, targetSdk, compileSdk, build types, signing config, ProGuard/R8 rules, and manifest permissions.
- Mark each item as: broken, redundant, dead code, unused dependency, or missing for production.
- Flag Play Store policy risks (permissions, foreground service types, storage access, and any YouTube/TikTok/Instagram download features that conflict with platform terms or Play policy). Propose a compliant option for each risk.

## Phase 1: Stability and crashes (highest priority)
- Pasting a video link must never close the app. Find the real root cause using logcat and reproduce it before fixing. Check for main-thread I/O, network calls on the wrong dispatcher, unhandled coroutine exceptions, missing foregroundServiceType (required on Android 14+), clipboard access on restricted Android versions, and state lost on Activity recreation.
- Add a global crash handler and structured error logging. Add a Crashlytics-compatible hook (behind a flag) so production crashes can be seen.
- Every coroutine launch in a ViewModel must handle exceptions and surface a user-facing error state.
- Run StrictMode in debug builds to catch main-thread violations.

## Phase 2: Downloader
- Support YouTube, TikTok, Instagram, Facebook, LinkedIn, Google Drive, and generic direct file links.
- Flow: paste link → auto-detect platform and media type → fetch metadata → show rich media card (thumbnail, title, duration, platform) → user picks Video (real available quality list) or Audio (real format and bitrate list) → download with progress, pause, resume, cancel, and background continuation → save to the chosen folder.
- Never show quality or audio pickers before metadata is fetched.
- Auto-name outputs with sanitized titles, extension handling, and duplicate-safe naming.
- Extractor architecture: evaluate (a) a backend service wrapping yt-dlp behind a clean API and (b) on-device yt-dlp with arm64-v8a only. Choose one, document the trade-off with measured APK impact, and keep the extractor behind an interface so it can be swapped.
- Map every extractor failure to a clear, localized, actionable message (private content, removed, network, unsupported, age-restricted, rate-limited).
- Save via MediaStore on Android 10+ and via SAF with persisted URI permissions for a user-chosen folder.
- Test every platform × (video, audio, quality selection, invalid link, offline, resume after kill).

## Phase 3: PDF and image engine
- Compression: implement real Low / Medium / High levels with image downsampling and JPEG recompression. Show a live before and after size estimate, and verify the estimate is within 15% of the real output for three sample files.
- PDF → Word (.docx): extract text with PDFBox, preserve paragraphs and headings, handle Arabic RTL, use OCR (ML Kit, unbundled) for scanned pages, and produce a file that opens correctly in Word. Validate with python-docx or an equivalent parser.
- PDF → PowerPoint (.pptx): one slide per page with the rendered page image and an extracted text layer. Validate with python-pptx.
- Merge, split, organize pages, images to PDF, crop, OCR, and protect/unlock must all work end to end.
- Every conversion must state its limits to the user. Never output a broken or empty file. Validate every output before publishing.
- Large files (100+ pages, 50+ MB) must not crash or exhaust memory. Use temp-file-backed PDF handling and streaming.
- Encrypted and corrupt inputs must produce a clear error, never a crash.

## Phase 4: Smart Scan
- Smart Scan opens the multi-page scanner directly, with no preview → capture → correct detour.
- Use ML Kit Document Scanner (multi-page, PDF and JPEG output, gallery import) as the primary engine. Keep CameraX only as a fallback when Play services is unavailable.

## Phase 5: Navigation and routing
- Bottom navigation with 5 slots: Tools, Files, Home (center), Downloads, Settings.
- The active indicator must be contained, centered, and animated in both LTR and RTL. Fix any overflow.
- Remove the search icon from top bars. Search lives inside the screens that need it.
- ROUTES.md lists every route using type-safe Navigation Compose arguments.
- System back and predictive back return to the previous screen. Settings returns to the screen that opened it. Each tab keeps its own back stack and state.
- Deep links and share-intent intake (Android share sheet to the app) must work without crashing.

## Phase 6: Tools UI and interaction
- Every tool has its own icon from Material Symbols (Rounded, filled, 24dp), converted to vector drawables named ic_tool_*. Each icon sits in a tinted rounded container with a strong accent. Map icons to tools in ICON_MAP.md. No tool shares an icon with another, and no tool is text-only.
- Tools grid: a flat grid with no category headers or text groups. Cards contain only the icon and the tool name. Order by usage (tracked in DataStore), with a sensible default for new users. Include one search field at the top of the Tools screen.
- Each tool opens into an interactive workspace with a real page or image preview. Advanced settings are inline interactive controls (sliders with live values, segmented buttons, toggles, swatches, chips, visual pickers). Plain number entry is allowed only for exact page ranges, and it must come with a visual page picker.
- Live preview for every tool that changes a file, updating within 300 ms or showing a clear loading state.
- Watermark studio (PDF and image): full-size preview with pinch zoom. The watermark is a selectable object with a visible frame and handles. Support drag, pinch-to-resize, rotate, and tap-to-select. Options: text or image, font, size, color, opacity, rotation presets, tile or single placement, all pages or selected pages, and placement presets.
- Signature: a transparent-background drawing pad that outputs PNG with alpha (verify with a pixel test). Pen thickness, ink color, undo, redo, clear, and a reusable signature library. After signing, the signature is placed on the real page with drag, pinch, rotate, and handles. Support multiple signatures and a page picker.
- Annotations: a bottom toolbar with a clear icon per tool (highlight, underline, strikethrough, note, shape, ink, text), color and thickness controls, and undo/redo.
- Split: page thumbnail grid, tap or drag to mark ranges, with a live output file count.
- Merge: reorderable PDF cards with first-page thumbnails and drag handles, with a live total page count.
- Organize pages: thumbnail grid with drag reorder, rotate, delete, and visible undo.
- Crop: interactive crop rectangle with corner and edge handles over the real page, supporting per-page and range cropping.
- Images to PDF: drag-reorderable thumbnails, visual page-size cards (A4, Letter, Auto, Custom), and a margin slider with a live preview.
- Compress: three large preset cards (Low, Medium, High) with before and after sizes.
- Image target size, crop and rotate, adjust (with a before and after split preview), and collage (visual layout picker with drag placement).
- Protect PDF: password field with show/hide, strength meter, and permission toggles.
- Forms: render the real form fields over the page and let the user tap to fill.
- Metadata: a clean form beside the document preview.
- Duplicates and batch rename: result cards with thumbnails. Batch rename shows a live preview list and pattern chips ({n}, {date}, {name}).
- OCR: page on the left, recognized text on the right, with copy and export actions.
- Every empty state has an illustration or icon and one clear call to action. No plain-text empty states.
- Every workspace has one consistent header: tool icon, tool name, back button.
- Shared-element transitions from tool card to workspace. Spring-based motion. Touch targets at least 48dp. Gestures work in LTR and RTL.

## Phase 7: Visual design system
- Material 3 with a custom color scheme. Dynamic color is optional and off by default.
- Clean, bold, spacious layout. No borders on cards, buttons, text fields, or text. Use tonal surfaces and soft elevation.
- Consistent corner radii. One strong modern font family that supports Arabic and English (IBM Plex Sans Arabic, Tajawal, or Cairo, paired with Inter or Manrope). Bundle only the weights used, subset the fonts.
- Dark mode uses pure black (#000000) backgrounds with neutral grays and no blue tint. Meet WCAG AA contrast in every theme.
- Home screen: hero section with quick actions (Scan, Convert, Compress, Download), recent files, and clear sections, all kept in sync with the top used tools.
- Files screen: grid and list modes only. File cards show thumbnail, name, size, and date. Include sort, filter, group, delete, delete all, a storage breakdown, and a visible save folder the user can change.
- Launch screen: SplashScreen API with a short (≤1.5 s) animated launch that never blocks the app.
- Animations are always on. There is no reduce-motion option.

## Phase 8: Settings
- Keep: theme (light / dark / system), language (Arabic / English with correct RTL and LTR), save location, default video and audio quality, storage usage (real values that update), clear cache (verified to delete temp files), about, and version.
- Remove: reduce motion, haptics on/off and intensity, Wi-Fi-only downloads, HTTP/HTTPS options, and concurrent download count.
- Settings must be reachable from its tab and return cleanly.

## Phase 9: Production readiness
- Release build: R8 full mode, resource shrinking, Android App Bundle, ABI splits (arm64-v8a for release, x86_64 for debug only), and language splits (en, ar only).
- Signing: release keystore configured through environment variables or a gitignored properties file. Never commit secrets. Debug key must not be used for release.
- Permissions: request the minimum set. Justify each one in PRIVACY_AND_PERMISSIONS.md. Remove any permission not used.
- Privacy policy draft (PRIVACY_POLICY.md) covering the data the app stores locally and any network calls to an extractor backend.
- Accessibility: content descriptions on all icons, TalkBack labels on controls, font scaling up to 200% without clipping, and contrast checks.
- Performance: cold start under 2 s on a mid-range device. No dropped frames during scrolling of the tools and files grids. Check with Macrobenchmark and Baseline Profiles, and include a baseline profile in the build.
- Logging: no PII or full URLs in logs. Redact query strings and tokens.
- Store listing assets: app name, short and full description in Arabic and English, feature list, and a plan for screenshots.

## Phase 10: Testing (mandatory)
- Unit tests: download routing, extractor error mapping, PDF compression policy, OOXML builders, file naming, and storage paths (JUnit, Turbine for Flow).
- Compose UI tests: navigation, back behavior, tab state preservation, and each main user flow.
- Screenshot tests (Roborazzi or Paparazzi) for every tool's empty and loaded states, and the watermark and signature studios, in light and dark modes and in LTR and Arabic RTL.
- Interaction tests for drag, pinch, and rotate on watermark and signature objects.
- Device tests on an emulator and at least one physical device, on minSdk (Android 8.0/9) and on Android 14+.
- Test matrix: each download platform × (video, audio, quality, invalid link, offline, resume after kill). Each tool × (normal file, large file, encrypted or corrupt file, 100+ pages).
- Run `./gradlew :app:lintRelease :app:testDebugUnitTest :app:connectedDebugAndroidTest` and fix every error. Address warnings that affect correctness or Play policy.
- Run a manual smoke test checklist before release.

## Deliverables (all in the repo root)
- AUDIT.md: audit findings and risk list
- DECISIONS.md: every architectural and product decision with reasons
- ROUTES.md: complete route map
- TOOL_UI_SPEC.md: one section per tool (icon, primary interaction, controls, preview, empty state)
- ICON_MAP.md: tool ID → icon → source and license
- PRIVACY_AND_PERMISSIONS.md and PRIVACY_POLICY.md
- PLAY_STORE_LISTING.md: Arabic and English copy
- SCREENSHOTS/: light and dark, LTR and RTL, for every tool
- CHANGELOG.md: fixed, added, changed, removed
- TEST_REPORT.md: pass/fail per test, measured APK size before and after, top size contributors, performance numbers, and a NOT VERIFIED list for anything not tested
- iOS_PLAN.md: how the shared core will be reused on iOS
- RELEASE_CHECKLIST.md: items to complete before publishing (keystore, Play Console setup, policy declarations, data safety form)

Finish with a short executive summary: what is production-ready, what is not, and the top three risks that remain.