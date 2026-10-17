# Native UI implementation report — 2026-10-17

## Delivered

Substantial native Compose refactor: `JumloNavHost.kt` reduced from a roughly 1600-line inline screen monolith to a navigation/launcher shell. Added Home, Tools, Files, Downloads/Activity and Workspace screen files. Shared actionable rows are in `ProductionRows.kt`. Account/settings APIs are integrated with the parent-owned implementations.

Home now has the approved four working frequent actions, catalog search, real tasks/drafts/recents. Tools use the enabled/visible registry, live filtered count and keyed rows. Files have separate sources/results/provider/trash views, optional search/sort, valid contextual output bulk trash, clean overflow actions and exact-ID irreversible purge confirmation. Provider rename/delete/create/copy controls honor the engine agent’s provider flags. Downloads use transfer rows with valid overflow controls and selection/cancellation limited to active transfers.

Workspace uses actual image/PDF preview, source ordering/removal, essential fields plus a complete advanced sheet, one bottom action, loading/disabled/minimum-input states, existing execution-settings/URL validation and obvious MIME checks without silently dropping inputs, and preserved detailed outcomes/retry. The shell blocks restored unqualified editors. Only dirty workspace back intercepts predictive-back completion; save/discard waits for durable callbacks. Normal Navigation Compose back remains native. Navigation-only file/settings tools do not mutate the processing form.

Quiet violet/neutral semantic colors, explicit bundled Arabic font use, LTR technical text, 48dp account control, width-based rail and content layout, no duplicate root settings icon or fake hero statistics. New copy uses paired English/Arabic resources. Sixty production UI strings and thirty localized message strings have matching locale keys. Full implementation notes and remaining acceptance gates are in `docs/DESIGN.md`.

## Evidence

- PASSED: XML parsing, 518 unique string keys per locale at check time, 60 production UI + 30 message key pairs, existence of referenced string resources, static regressions for dashboard removal, immediate purge removal and width adaptation. Raw output: `ui/static-validation.log`.
- PASSED: numeric opaque semantic token contrast: 18 text pairs >=4.5:1, two outline/surface pairs >=3:1. Raw output: `ui/contrast-tokens.log`. This is not whole-screen rendered accessibility verification.
- Added/updated instrumentation: `JumloUiTest.kt`, `ProductionRowInteractionTest.kt`; clarified scope of existing `ScreenConfigSemanticsTest.kt`. Device execution pending build agent/toolchain results.
- PASSED: `git diff --check` for owned tracked UI/design paths (empty `ui/diff-check.log`, exit 0).
- Final native compilation outcomes are recorded in `build-report.md` and `docs/RELEASE.md`; this UI report records its scoped checks.

## Limitations and release gates

No physical device screenshots, 360/412/tablet/landscape golden approval, TalkBack review, font-200% route acceptance, fold-hinge validation, predictive-back device acceptance or measured startup/jank/memory evidence. Existing advanced fields are preserved but not all less-frequent editors have individually redesigned essential settings. Local favorites selection/reorder is implemented in the follow-up below; automated ranking is deliberately absent. New screen collections are targeted, but screen aggregate immutable UiState migration is incomplete. Common engine errors now have localized actionable messages; unknown errors use a localized generic fallback. Existing media workspace remains internal and untouched; policy controls exposure.

## Owned changed paths

- `ui/navigation/JumloNavHost.kt`
- `ui/components/QuietPrecisionComponents.kt`, new `ProductionRows.kt`, `UserMessages.kt` and focused navigation/topbar/button/state/file/fixture component files
- new `ui/home/HomeScreen.kt`, `ui/tools/ToolsScreen.kt`, `ui/files/FilesScreen.kt`
- new `ui/downloads/DownloadsScreen.kt`, `ui/workspace/WorkspaceScreen.kt`
- `ui/theme/Color.kt`, `Type.kt`, `Theme.kt`
- new EN/AR `production_ui.xml` and `error_messages.xml`
- UI instrumentation files listed above; `docs/DESIGN.md`; this report and raw UI logs.

No remote actions or commits performed.

## Independent review follow-up

R3: essential/Advanced controls now match actual handler consumption. Target-size image compression exposes target bytes and filename prefix, explains fixed JPEG/automatic quality, and hides ineffective format/quality fields. Parent corrected the other ignored name/format controls and added meaningful SHA/PDF-image essentials. R5: waiting jobs retain their retry action; optional URL replacement remains separate. Added `WorkspaceRecoveryInteractionTest.kt`: waiting PDF and waiting download overflow-to-retry, plus target-setting UI edits through execution-request serialization. These are authored tests, not device-executed evidence. Resource compiler first-build unresolved IDs were confirmed present on disk; native build agent identified concurrent R generation as the likely stale-input cause and is retrying cleanly.

Additional consumed-control review: PNG quality editors hidden; EXIF preservation explains fixed JPEG instead of offering an ignored format; width/height editors hide when percentage/longest-edge resizing is active.

## Favorites and hinge plan-completion follow-up

Implemented `HomeActionPolicy`, inline `HomeActionEditor`, ordered DataStore `quick_action_ids` persistence using the existing `AppSettingsState.quickActionIds`, and a narrow ViewModel setter with normal preference failure reporting. No draft/signing/schema/engine changes. Eligibility excludes navigation and unavailable tools. The four original defaults remain the fallback. Arbitrary eligible favorites display their actual title/icon.

Added `ViewportPolicy` and `HingeSafeViewport`: lifecycle-aware AndroidX Window folding-feature collection, separating/FULL-occlusion handling, actual root-window pixel coordinates, clamped offscreen geometry, zero-width folding boundaries, horizontal/vertical splitting, largest-safe-pane policy and absolute placement with RTL tie-breaking. Shell width adaptation uses the selected pane. Physical folding hardware/modal-window/posture validation remains open.

Executed test-first pure policy checks: RED 9 failed/2 passed against constant old behavior; GREEN 11/11 passed. Reproducible runner: `ui/run-home-layout-tests.sh`; source: `ui/HomeLayoutPolicyTests.kt`; logs: `ui/home-layout-red.log` and `ui/home-layout-green.log`. Authored device tests: `HomeActionsPersistenceTest` and `HomeActionEditorInteractionTest`. They are not claimed as executed. Static locale/reference checks passed (`ui/favorites-hinge-static.log`).

Full host regression command (`bash scripts/test-core.sh`, Kotlin CLI on PATH) ran after policy changes. Final result: 96 passed, 12 failed. All 12 failures were real-HTTP fixture sockets rejected with `SocketException: Operation not permitted` in this restricted worker, not asserted product mismatches. A failed initial attempt also revealed standalone test auto-discovery in the host suite; the standalone tests were moved to the verification runner. No full-suite pass is claimed. Final raw log: `ui/core-regression-final.log`; earlier failure traces retained. Failing names: real HTTP GET writes exact bytes and measured size; transient 503 retries with backoff and succeeds; unknown HTTP size stays unknown; real HTTP resume validates range and appends once; real changed ETag leaves prior partial bytes untouched; real 200 response to Range does not append; HTTP redirects resolve relative locations; HTTP error cannot create successful output; truncated HTTP body is a failure; streamed HTTP enforces actual byte budget; HTTP metadata binds resume to the observed final URL without storing its secret; resume rejects a changed redirect target even with the same ETag and length. Independent reviewer/root may have separate full-suite results from their authorized environment.

Production source/resources/tests frozen and communicated to root and build agent. Native final compilation belongs to aggregate build evidence.
