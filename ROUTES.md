# Navigation and route inventory

> Release qualification is tracked in TEST_REPORT.md. The 2026-10-28 release gate patch does not add/remove any navigation routes. It does not certify route interactions.


This document describes the current source navigation contract. Executed build and device results belong in [TEST_REPORT.md](TEST_REPORT.md). A registered route or a written UI test is not evidence that a physical-device flow passed.

Authoritative implementation: [JumloNavHost.kt](app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt), [QuietNavigation.kt](app/src/main/java/com/jumlo/app/ui/components/QuietNavigation.kt), [MainActivity.kt](app/src/main/java/com/jumlo/app/MainActivity.kt), and [AndroidManifest.xml](app/src/main/AndroidManifest.xml).

## Five main tabs

The logical order is **Tools · Files · Home · Downloads · Settings**. Home occupies the middle cell. Arabic mirrors the row while keeping Home centered. `MainDockTabs` is the explicit order; the order of the legacy `DockTab` enum is not the navigation order.

| Position in English | Route type | Stable serial name | Screen | Main responsibilities |
| --- | --- | --- | --- | --- |
| 1 | `ToolsRoute` | `tools` | `ToolsScreen` | Sectioned icon/name catalog (Scan & Capture, Organize PDF, Convert, Edit & Sign, Optimize, Protect, Download, Files) with Pinned/Recent rows and one search field; usage ranking orders tiles within a section |
| 2 | `FilesRoute` | `files` | `FilesScreen` | Outputs, imported sources, granted folders and trash; list/grid, sort/filter/group, selection and file actions |
| 3 | `HomeRoute` | `home` | `HomeScreen` | Start destination; Scan/Convert/Compress/Download, draft continuation, active jobs and recent files |
| 4 | `DownloadsRoute` | `downloads` | `DownloadsScreen` | New download entry, current transfers, previous downloads and verified outputs |
| 5 | `SettingsRoute` | `settings` | `SettingsScreen` | Appearance (theme mode incl. AMOLED, App color picker, dynamic color), language, save folder, quality defaults, storage, cache and About |

The indicator is drawn inside its destination cell. Each cell has `Role.Tab`, selection semantics and a retained spoken label at large font scales. Print is a detail screen; `DockTab.PRINT` remains a compatibility value and is excluded from `MainDockTabs`.

## Complete destination inventory

All fourteen destinations are Kotlin-serialization route types. The names below are `@SerialName` values; Navigation generates the argument-bearing route pattern. Call `navigate(ToolRoute(id))` or the other typed constructors rather than constructing escaped route strings. `UtilityTool.route` contains legacy descriptive identifiers and is not the authoritative NavHost destination.

| Type and arguments | Stable serial name | Destination content | Entry points | Dock |
| --- | --- | --- | --- | --- |
| `HomeRoute` | `home` | `HomeScreen` | App start and Home tab | Yes |
| `ToolsRoute` | `tools` | `ToolsScreen` | Tools tab and Home Explore tools | Yes |
| `FilesRoute` | `files` | `FilesScreen` | Files tab, Home recent-files action, legacy storage navigation | Yes |
| `DownloadsRoute` | `downloads` | `DownloadsScreen` | Downloads tab and Home active-jobs action | Yes |
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
- The dock is a Scaffold bottom bar, not an overlay. It appears on roots, processing workspaces and activity screens. Detail toolbars provide Back, and root toolbars contain no search button.

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

[FiveTabNavigationTest.kt](app/src/androidTest/java/com/jumlo/app/ui/FiveTabNavigationTest.kt) covers tab order, RTL, selection and indicator bounds. [JumloUiTest.kt](app/src/androidTest/java/com/jumlo/app/ui/JumloUiTest.kt) covers actual-activity entry, tab/recreation restoration, Back chains and warm workspace deep-link delivery. [PreferencesPersistenceTest.kt](app/src/androidTest/java/com/jumlo/app/data/PreferencesPersistenceTest.kt) covers real preference storage. The existence of these tests does not imply execution; consult [TEST_REPORT.md](TEST_REPORT.md) for the commands, environment and outcomes.

## 2026-10-28 — WP06 visual-only change

No navigation route or tab behavior changed. ToolsRoute now uses more saturated category-colored tool tiles and stronger semantic-color section headers. The token review uses Compose previews only, not a new production route.
