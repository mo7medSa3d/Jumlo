# Navigation and route inventory

> WP07: No routes changed. Static source candidate inventory is informational and cannot certify that a button, route or gesture works.

> WP03 adds an accessible Interface sounds switch to the existing Settings screen without changing the navigation graph.

> WP06: The existing `pdf_compress` route preserves the same page-selection and job flow. Only measured result status/warnings change; no navigation or device qualification is claimed.

**2026-10-28 WP11 downloader redirects:** No route changes; download processing/probe requests now pass initial URL origin to header policy. Cross-origin redirect targets never inherit custom user header values. HTTP/network device qualification pending.


## 2026-10-28 — WP06 CI contract (no route changes)

Navigation remains unchanged by this CI-only work package. The build adds debug lint and a source-size assertion to existing release/compact artifact checks while retaining serialized KSP2. Device/Back/RTL verification remains separate.


**2026-10-28 WP03 settings controls:** SettingsRoute and SettingsDetailRoute now expose persisted haptic intensity and Reduce motion alongside existing controls. No new navigation route; device acceptance is pending. Back/recreation tests remain required.


**WP01 2026-10-28:** Navigation is unchanged. CI release gating was restored (see [Actions #40639445934](https://github.com/mo7medSa3d/Jumlo/actions/runs/40639445934)); full route/device acceptance remains NOT VERIFIED. See [AUDIT_2.md](docs/AUDIT_2.md) for route inventory.


> Release qualification is tracked in TEST_REPORT.md. The 2026-10-28 release gate patch does not add/remove any navigation routes. It does not certify route interactions.


This document describes the current source navigation contract. Executed build and device results belong in [TEST_REPORT.md](TEST_REPORT.md). A registered route or a written UI test is not evidence that a physical-device flow passed.

Authoritative implementation: [JumloNavHost.kt](app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt), [QuietNavigation.kt](app/src/main/java/com/jumlo/app/ui/components/QuietNavigation.kt), [MainActivity.kt](app/src/main/java/com/jumlo/app/MainActivity.kt), and [AndroidManifest.xml](app/src/main/AndroidManifest.xml).

## Four primary tabs and Downloads detail

The four bottom tabs are **Home · Tools · Files · Settings**, mirrored for Arabic RTL. Downloads remains Back-navigable as a detail, with a permanent Home history action even when there are no active jobs; the legacy enum is kept for compatibility.

| English position | Route | Serial name | Screen | Responsibility |
| --- | --- | --- | --- | --- |
| 1 | `HomeRoute` | `home` | `HomeScreen` | Home actions, active jobs, Downloads history, recent files |
| 2 | `ToolsRoute` | `tools` | `ToolsScreen` | Searchable ranked tool catalogue |
| 3 | `FilesRoute` | `files` | `FilesScreen` | Output/import/SAF storage, trash |
| 4 | `SettingsRoute` | `settings` | `SettingsScreen` | Appearance, language, haptics, save settings |

The indicator stays inside the four tab cells with `Role.Tab`; Downloads and Print are detail routes excluded from the dock.

## Complete destination inventory

All fourteen destinations are Kotlin-serialization route types. The names below are `@SerialName` values; Navigation generates the argument-bearing route pattern. Call `navigate(ToolRoute(id))` or the other typed constructors rather than constructing escaped route strings. `UtilityTool.route` contains legacy descriptive identifiers and is not the authoritative NavHost destination.

| Type and arguments | Stable serial name | Destination content | Entry points | Dock |
| --- | --- | --- | --- | --- |
| `HomeRoute` | `home` | `HomeScreen` | App start and Home tab | Yes |
| `ToolsRoute` | `tools` | `ToolsScreen` | Tools tab and Home Explore tools | Yes |
| `FilesRoute` | `files` | `FilesScreen` | Files tab, Home recent-files action, legacy storage navigation | Yes |
| `DownloadsRoute` | `downloads` | `DownloadsScreen` | Permanent Home history and active downloads | No, Back detail |
| `SettingsRoute` | `settings` | `SettingsScreen` | Settings tab | Yes |
| `ToolRoute(toolId: String, transitionOrigin: String = "tools")` | `tools/run` | `WorkspaceScreen`, `ViewerWorkspace`, or `MediaWorkspace` | Catalog tools, Home conversion/compression/download, explicit source/output handoff, shared links, repeat run | Yes; Tools stack |
| `ScanRoute(transitionOrigin: String = "tools")` | `tools/scan` | `ScanScreen` / ML Kit launcher | Home Scan, `scan_doc`, onboarding Scan | No |
| `PrintRoute` | `tools/print` | `PrintScreen` | Printable output/source actions and page-editor print handoff | No |
| `ViewerRoute(uri: String, title: String, returnTab: String? = null)` | `files/viewer` | `PdfViewerScreen` (immersive, full-screen) | Open PDF output, granted-provider PDF, completed scan | No |
| `JobRoute(jobId: String)` | `downloads/job` | `ActivityScreen` filtered to one job | Job rows, successful queue submission, notification job ID | Yes; caller's selected tab |
| `ActivityRoute` | `downloads/activity` | `ActivityScreen` with all jobs | Show all from a job detail and activity navigation without a selected job | Yes; caller's selected tab |
| `SettingsDetailRoute` | `settings/details` | `SettingsScreen` | Toolbar settings shortcut and legacy `dl_settings` action | No |
| `StorageRoute` | `settings/storage` | `StorageScreen` | Storage usage in Files or Settings | No |
| `WorkspaceRoute` | `workspace` | Existing workspace dispatch | Compatibility deep link `jumlo://workspace` | Yes; compatibility caller context |

`JobRoute` and `ViewerRoute` remain on the caller's stack. Their serial-name prefix does not force a tab switch. The graph is one flat typed NavHost with saved root-tab stacks, not five nested navigation graphs.

The `viewer` route is full-screen: the shell suppresses both the bottom dock and the workspace top bar, and `PdfViewerScreen` owns its own slim, auto-hiding chrome (see the `pdf_01` entry in [TOOL_UI_SPEC.md](TOOL_UI_SPEC.md)).

## Tab, Back and state behavior

- `HomeRoute` is the NavHost start destination. Root switches use `popUpTo(HomeRoute) { saveState = true }`, `launchSingleTop = true`, and `restoreState = true`.
- New processing-tool entry calls `AppViewModel.chooseTool(toolId)` and navigates through Tools to `ToolRoute(toolId)`. The existing workspace is replaced on explicit new entry. A tab tap saves/restores its stack without closing the tool session.
- `ToolRoute` and `ScanRoute` retain the originating tab. Back removes the finished tool entry before restoring that tab, so Home actions return to Home and Tools actions return to Tools. A completed scan passes its origin to the result viewer. Shared icon keys use the same origin namespace, and each workspace header uses its entry's serialized tool ID.
- Navigation-only catalogue entries (`scan_doc`, `dl_settings`, `file_02`, `file_trash`) record usage without resetting a processing draft. Ordinary processing entries record usage through `chooseTool`.
- All ordinary new processing workspaces and scanner entries use the Tools stack. This gives the existing single `AppViewModel` workspace one owner. It does not create five concurrent processing drafts.
- `ToolRoute` reads its serialized `toolId`. Reconciliation waits for `workspaceReady` and verifies the active back-stack entry before selecting a different tool. An exiting/restored entry cannot reset a newly opened tool.
- In-page search, filters and selection use saveable Compose state; Files waits until records are loaded before pruning restored selection. The processing draft/recovery checkpoint is persisted by `DraftStore`. Media metadata and unsent URL text stay in its ViewModel session; submitted URLs use encrypted persistence for the durable job.
- Toolbar Settings uses `SettingsDetailRoute`, so Back returns to the actual caller. Settings tab is a root and does not borrow Home's selection indicator.
- Details pop to the preceding entry. Back from a dirty processing workspace opens Save and leave / Keep editing / Discard and leave. The session is closed only after leaving that workspace, not during a root-tab switch.
- A saveable history of root tabs handles Back from a visited root. When there is no app-managed previous entry/history, normal activity Back applies. If a detail cannot pop, the app restores Home.
- `PredictiveBackHandler` collects gesture progress for detail transitions and tab-history Back. The manifest enables `OnBackInvokedCallback`. Motion follows the Android animator setting through `LocalReducedMotion`; there is no user-facing app motion toggle. Actual predictive-gesture qualification must be read from the test report. `SharedTransitionLayout` spans the typed destinations; card and workspace-header icons share a stable tool key, destination visibility scope and spring bounds animation. Home and Tools use distinct origin keys, carried in the typed tool route, to avoid a third participant during a root switch. Header identity is pinned to the destination tool argument while an outgoing destination animates. Standalone previews fall back to normal icons.
- The dock is a Scaffold bottom bar, not an overlay. It appears on four roots, workspaces and activity; Downloads has its own Back navigation. Detail toolbars provide Back, and root toolbars contain no search button.

## Important action flows

| Action | Source behavior |
| --- | --- |
| Scan | Switch to Tools, open `ScanRoute`, launch ML Kit directly with gallery import and PDF/JPEG output; fall back to lifecycle-bound CameraX with a page queue when Play services cannot launch the scanner. A private validated copy is published through `OutputStore`. The reader replaces Scan only if that exact Scan entry remains current; saving after Back does not reopen it. Publication is a ViewModel action, not a fabricated WorkManager job. |
| Home Convert | Opens a local chooser for Images→PDF, PDF→Word, PDF→PowerPoint, PDF→images or image conversion; then dispatches the real tool ID. |
| Home Compress | Fresh `ToolRoute("pdf_compress")`. |
| Home Download / Downloads New | Fresh `ToolRoute("dl_media")`; quality and conversion choices depend on inspected source metadata. |
| View PDF tool | `ToolRoute("pdf_01")` uses `ViewerWorkspace` to select/import a source and embed the same immersive bounded PDF reader. It is a navigation capability, not a processing job. |
| Open saved PDF | `ViewerRoute(output.uri, output.displayName)` opens the full-screen immersive reader over the verified private output. Other file types use an Android external viewer with the available public/private URI. |
| Use an output/source in a tool | AppViewModel explicitly prepares input IDs/snapshots before opening the workspace. This is the supported nonempty-entry handoff. |
| Print | AppViewModel prepares a printable immutable source and emits a print request; `PrintViewModel` handles setup/history and the Android print adapter. |
| Job completion | `JobRoute(jobId)` shows durable status and only available output records; opening a PDF uses the internal viewer. |
| Save folder | Android `ACTION_OPEN_DOCUMENT_TREE`; the validated persistable grant and destination preference are used by both Files and Settings. |

## External entry points and permissions

| Entry | Handling |
| --- | --- |
| Launcher | `MainActivity`, Android SplashScreen API, then Home/restored navigation |
| `jumlo://workspace` | Cold launch retains the manifest VIEW/BROWSABLE filter and typed `navDeepLink<WorkspaceRoute>` registration. For an already-running activity, the lifecycle-bound `ACTION_VIEW` listener invokes the refreshed `latestOpenWorkspace` lambda and canonical `openWorkspace` path, preserving the caller origin in `ToolRoute` on the Tools stack. Cold-link execution remains a separate runtime gate. |
| `ACTION_SEND` | PDF, images, ZIP or text; `IntakeController` gathers grant flags, ClipData and shared text, then AppViewModel validates/imports or selects the appropriate link flow |
| `ACTION_SEND_MULTIPLE` | Multiple PDFs, images or ZIPs with the same intake/grant handling |
| New intent | `singleTop` activity calls the same intake/notification path. The NavHost installs/removes its workspace VIEW listener with `DisposableEffect`; malformed external content becomes a localized error instead of terminating the activity |
| Notification | Validated `job_id` is delivered to `JobRoute(jobId)`; a notification does not select a job through mutable UI text |
| File/photo chooser | SAF or system photo picker; compatibility MIME selection depends on the active tool |
| Cross-app drag/drop | Files/workspace target accepts content URIs, acquires a platform drag grant, and retains it until asynchronous intake completes or the Activity is destroyed |
| Export/share/external viewer | Android create-document/share/view intents with URI grants; unavailable provider/app errors use the normal message path |

No additional public deep links are registered for the other serial names.

## UI state that is not a route

Home conversion chooser, onboarding guide, unsaved-workspace confirmation, file sorting/grouping/rename/delete/details/purge dialogs, preference choice dialogs, About/privacy, processing presets/advanced controls, signature pad, PDF page controls and media format choices are composable state inside the containing destination. The primary Android scanner, file picker, photo picker, share chooser and print-service UI are platform-owned activities/dialogs. CameraX fallback is an app-owned scanner screen with runtime camera permission and a bounded page queue.

## Navigation verification references

[FourTabNavigationTest.kt](app/src/androidTest/java/com/jumlo/app/ui/FourTabNavigationTest.kt) covers four-tab RTL, selection and indicator bounds, while `MainDockContractTest` and `JumloUiTest` cover root policy and Downloads Back. [JumloUiTest.kt](app/src/androidTest/java/com/jumlo/app/ui/JumloUiTest.kt) covers actual-activity entry, tab/recreation restoration, Back chains and warm workspace deep-link delivery. [PreferencesPersistenceTest.kt](app/src/androidTest/java/com/jumlo/app/data/PreferencesPersistenceTest.kt) covers real preference storage. The existence of these tests does not imply execution; consult [TEST_REPORT.md](TEST_REPORT.md) for the commands, environment and outcomes.

## 2026-10-28 — WP02a scope note

Appearance picker remains within `SettingsRoute`/`SettingsDetailRoute` (no new route). Persisted hex accent acceptance does not alter tab navigation or the route graph. Device/RTL navigation status unchanged.

## 2026-10-28 — WP08 CI compiler gate

No navigation, locale, settings, or tool-entry contract changes. Worker serialization only affects the CI build used to qualify routes; runtime UI acceptance remains NOT VERIFIED.

## WP09 automatic emulator route smoke — 2026-10-28

An additive GitHub Actions UI matrix now targets API 26, API 35 and API 36 for real `JumloUiTest`, workspace recovery, catalogue, viewer, gesture, localized component semantics and persisted preferences. No route code changed; test results must come from matching Actions jobs, not this declaration. Full scanner and external provider journeys remain unverified.

## WP10 Tools visibility correction

No typed routes were removed. `ToolsCatalogue` default grid, pinned and recent rows honor `isListed`; explicit search may locate `img_compare`. Navigation aliases remain accessible via dedicated tabs.

> WP14: The current tool matrix tracks 45 registered IDs, not historical IDs; 5 navigation-only routes are N/A for processing but still require UI smoke. No navigation was pruned.

**2026-10-28 WP06:** AppearanceColorPicker within Settings renders persisted accent preset names in selected EN/AR locale. No new route, no color key/schema change; device/RTL testing pending.

> WP13: No route or visible setting was removed; only obsolete hidden preference write methods were eliminated.

## 2026-10-28 — WP05 reader lifecycle

ViewerRoute keeps the same serialized arguments and caller-stack return behavior. On each document open, the viewer restores the last-read page from an app-private SHA-256-keyed DataStore instead of an in-memory map. This does not grant additional filesystem rights or change any route. Device acceptance pending.

## WP07 print page manager state recovery

The existing `PrintRoute` remains unchanged. PrintPageSelection.normalizedOrder aligns stale persisted permutations with the visible source-page order before drag/reorder/rotation and preserves selected pages. Android print-provider UI still unverified.

- WP04 download restart now fails safe for compressed/legacy partials; resumes only validator-verified representation bytes. This does not change navigation.
**WP04 2026-10-28:** route graph unchanged. A durable `dl_02`/`dl_03` transfer may recover transient mid-stream bytes with HTTP Range + validator in `StreamTransfer`; UI job navigation is unchanged. Android pause/retry/notification acceptance remains NOT VERIFIED.

## 2026-10-28 — WP-04 ephemeral workspace state isolation

No typed destinations or navigation contracts change. Within `ToolRoute`, the source preview, selected page source, batch-rename preview and Advanced UI state now have explicit tool/source keys. New input queues should not retain unrelated prior previews; Android UI test evidence is still required to qualify it.

## 2026-10-28 — WP12 filename validation

No navigation or route changes. File rename, archive extraction and automatically generated output names continue through `SafeNames`; invisible bidi-spoof formatting is now invalid. Arabic and Hebrew names remain supported.

**2026-10-28 WP08 scanner ownership:** `ScanRoute` unchanged; CameraX fallback thumbnail rows dispose native preview Bitmap on removal/off-screen disposal. Android camera and gallery operations are not qualified by this source change.

> WP05: Existing `file_drive` route and data-flow behavior are unchanged, but user-facing EN/AR disclosure now accurately distinguishes local edits from explicit network/cloud actions.

> WP09 restores missing Android compression fixture instructions; navigation unchanged and real 200-page device qualification remains pending.

## WP13 grouped page-range validation

No route changes. PDF page-group entry now detects oversized expanded selections before list allocation and surfaces localized PAGE_SELECTION_LIMIT guidance instead of crashing due to repeated ranges.

## 2026-10-28 — WP-03 OCR script compatibility notice

No route changes. The existing `ToolRoute("pdf_ocr")` workspace shows a persistent English/Arabic script-support notice before recognition. This is guidance, not Arabic scanned-text recognition.

**2026-10-28 WP03 capability contract:** `file_trash` stays a Files navigation action and never enters an operation handler; `pdf_compare` and `pdf_redact` ghost IDs are not enabled registered processing routes. No user-facing route deleted.


### Pure Kotlin module gates (PR #41)

The four core modules have dedicated JVM test suites included with the full media CI gate. Post-integration CI remains required.
