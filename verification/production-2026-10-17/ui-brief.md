# Approved execution task
Repository: /workspace/scratch/2b70f1dcaa30/work/Jumlo-main
User explicitly asks to implement the supplied plan on supplied source ZIP and return ZIP. Work locally; no remote commit/push or deployment. Preserve native Kotlin/Compose and existing user data. No invented test evidence. Your changes are shared with other agents; edit ONLY owned paths, notify parent for interface changes. Do not spawn additional agents or commit. Use meaningful behavior tests for risk-bearing changes, run available checks, retain raw logs. Distinguish passed/failed/unavailable. Report changed paths, root causes, tests, limits and follow-ups in report file.

## Ownership
ui/navigation/JumloNavHost.kt, ui/components/QuietPrecisionComponents.kt and new component files, new ui/home/, tools/, files/, workspace/, downloads screens (preserve existing MediaViewModel/MediaWorkspace), ui/theme/, new res/values/production_ui.xml + values-ar/production_ui.xml, UI instrumentation tests. Parent owns AppViewModel/MainActivity/preferences, AccountHubModal.kt and new SettingsScreen.kt plus parent_strings.xml resources. Do not touch those except agree interfaces.
## Task
Execute Phases 3/5/6 UI using approved quiet violet native design. Extract nav's major screen composables into meaningful files; per-screen Flow collection; adaptive width not landscape heuristic. Home no media hero/fake stats, working 2x2 tools plus genuine recents/tasks; account entry opens existing modal (parent rewriting) and avoid duplicate settings icon. Tools catalog eligible tools only with live counts/search/categories; no no-op click handlers. Files/downloads clean rows+overflow, contextual bulk selection and validated actions. Workspace actual preview, essential fields and advanced sheet, one bottom action, clear results/errors, input ordering; unsaved-back keep/save/discard supported via parent VM methods to agree. Purge confirmation both individual/all is necessary; parent handles Settings purge UI but you handle Files. Use string resources EN/AR for new/changed UI; if files aren't localized yet migrate touched copy. Reuse IBM fonts already present, rounded icons, >=48dp touch, WCAG colors, RTL technical values LTR. No pretend screenshots or device proof. Keep existing capabilities available internally and gated registry. Agent engine changing policy; default API enabled(id) remains, can use tools.filter{enabled}. Send integration requirements to parent early.
 — New information architecture: minimal premium utility app

**Design direction:** Quiet Precision x restrained Material 3 Expressive; *premium SaaS simplicity*, not a fake dashboard or web UI. Preserve native Kotlin/Compose, violet visual identity, local-first experience and four destinations. The user sees at most one main commitment action per screen.

### Home

- [ ] Compact branded app bar: Jumlo + search + genuine Account & Data destination; one Settings entry in the hub (avoid duplicative settings and account actions if the design would clutter).
- [ ] Search field and a calm 2x2 (or adaptive) set of **working** frequent actions: Images to PDF, Merge PDF, Resize/Compress Image, New Direct Download. Smart reorder/favorites local only.
- [ ] Replace bulky welcome/hero/status/stats cards with contextual active task and recent files/drafts (only when real data exists), concise empty states and nonintrusive help.
- [ ] Show task name + minimal context + single intent. No 3-line promotional subtitles, colorful card rainbow, giant banners or tier/status decorations.

### Tools

- [ ] Search, compact category filters and single trusted count based on eligible tools.
- [ ] Category sections with a clear icon, a short name, optional one-line purpose; prefer rows in large catalog and optional grid for four favorites.
- [ ] Remove dead icons, concept entries, arbitrary colored boxes and duplicate download/file operations that already deep-link to their respective places.

### Downloads / Files

- [ ] Use clean file/transfer rows with icon, filename, meaningful metadata, progress/state and a trailing overflow menu instead of five inline action buttons.
- [ ] Short selection toolbar for multi-select; bulk actions only when valid. Show sort/search/filter on demand. Distinguish outputs, app-imported files, SAF-granted folders, and managed trash.
- [ ] Show only operations granted by storage provider; non-destructive defaults and explicit irreversible-action confirmations.

### Shared Hybrid Workspace

- [ ] File(s) selected first -> dominant actual preview -> 2-4 essential settings -> Advanced one-sheet -> sticky safe-area primary CTA (`Create PDF`, `Merge 3 files`, etc.).
- [ ] Show source/target names, destination, expected count; metadata and output size only after measurement.
- [ ] All editors share import/reorder/remove/retry/draft/resume conventions. Do not stack multiple bottom bars or many nested cards.
- [ ] Handle all states: first-use, loading, invalid input, busy, partial success, failed, retry, completed and unsaved-back.

**Exit:** compare every route with screenshots at 360dp, 412dp, tablet and landscape; UX review confirms one clear intent per screen with no redundant CTA.


 — Design system, localization, motion & responsiveness

- [ ] Adopt semantic tokens rather than one-off per-tool literal gradients/colors. Suggested approved baseline: warm neutral light `#FAFAF9`, deep near-black dark `#0B0B0D`, quiet violet primary `#4F3DFF` (dark equivalent), category colors used sparingly in icons/metadata. Verify contrast on *resolved* surfaces.
- [ ] Material 3 Expressive **selectively**: modern menu/sheet/navigation behavior, improved state transitions, real button hierarchy and tactile feedback. No motion for motion's sake.
- [ ] One licensed icon family (Material Symbols Rounded), consistent 24dp optical size and >=48dp effective tap regions; IBM Plex Sans + IBM Plex Sans Arabic with local assets and notices.
- [ ] Adaptive compact/medium/expanded width; avoid hard-coded landscape-as-tablet shortcut. Account for hinge/folds, IME, safe areas, gesture and 3-button nav.
- [ ] EN/AR localization through string resources; do not mix hand-written branch strings with resources. RTL mirroring of navigation, **not** document pages, bytes, filenames or technical values.
- [ ] Test dark/light, dynamic content, text at 200% scaling, TalkBack, reduced motion, color contrast target WCAG AA (4.5:1 normal text, 3:1 essential non-text), keyboard focus and captions/tooltips.

**Exit:** screenshot golden set approved for all screens/states and interaction states; accessibility violations fixed/triaged before release.

## Phase 6 — Architecture and measurable performance

- [ ] Split `ui/navigation/JumloNavHost.kt` into small route composables and navigation shell (`home/`, `tools/`, `files/`, `downloads/`, `settings/`, `account/`, `workspace/`) while keeping behavior/saved state. Split reusable components by meaning; don't create abstractions without a use case.
- [ ] Prefer screen-owned, immutable `UiState`, targeted Flow collection, `collectAsStateWithLifecycle`, stable item keys/content types, `remember` for expensive derivations, and `derivedStateOf` only for rapidly changing state. Profile before optimizing.
- [ ] Keep image/pdf decode and file operations off main thread; sample thumbnails, cap decode buffers, cancel obsolete preview work, close `PdfRenderer`/streams, avoid repeated giant bitmap copies and background-memory leaks.
- [ ] Keep WorkManager and Room state durable across activity recreation/process death; validate controlled concurrency and disk/memory budgets during bulk operations.
- [ ] Fix `:benchmark` module and use Macrobenchmark + Baseline Profiles on actual representative hardware, at **release-like** optimization; compare cold start TTID/TTFD, warm start, frame timing, scroll/navigation jank, memory and APK size against baseline.
- [ ] Suggested initial SLOs (calibrate after physical measurements): cold TTID median <=1.2s on designated mid-range device, p90 <=2.0s; no frozen frames (>700ms); frame jank <5% on routine navigation/scroll; zero ANRs in acceptance scenarios; memory bounded during worst supported inputs and no OOM. Document device, versions and test method. Targets are **goals, not observed measurements**.

**Exit:** reproducible performance report (`docs/PERFORMANCE_REPORT.md`), charts/traces, performance regression tests, improved or justified metrics on ≥1 real mid-range device; no blocking work on UI thread.


Report path: verification/production-2026-10-17/ui-report.md
