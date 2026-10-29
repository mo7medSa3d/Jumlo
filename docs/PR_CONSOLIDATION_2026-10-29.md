# 2026-10-29 Jumlo PR consolidation and verification ledger

This work package merges **changes and history** from PRs #17, #18, #19, #21, #22, #26, #28, #29, #30, #31, #32, #33, #34, #35, #37, #38, #39, #40, #42, #44, #45, #46, #50, #51, #53 and #54. Some PRs (#35, #29 and #32) were also merged into main individually while the integration was being assembled; their accepted source code was retained when the latest main was incorporated.

## Conflict resolution

- **Downloader:** #22 supplies the BEST/explicit-resolution-cap preference and backend selection contracts; #26 supplies the optional Advanced UI, with #21's actual selected-quality summary. Never silently discard a previously saved resolution cap.
- **PDF reader:** #30 supplies one bounded, SHA-256-URI-keyed DataStore-backed reader-position implementation; #42's complementary JVM regression assertions are adapted to that implementation. Do not ship two competing stores.
- **Theme:** #34 supplies canonical tokens and WCAG 2.4 sRGB luminance with AA regression checks; #33 contributes strong category-colored tiles and accessible text/icon foreground without replacing contrast math.
- **User preferences:** live haptics and reduce-motion from main are retained; #18 adds a persistent sounds UI; #51 removes only legacy inert global Wi-Fi/concurrency setters without removing per-download Wi-Fi controls. Persistence tests preserve the sounds assertion.
- **Color localization:** #46 covers both preview and picker EN/AR labels, so the narrower #38 duplicate implementation is not reintroduced.
- **Navigation:** four tabs from #19 replace the obsolete five-tab assertions; Downloads remains accessible as a detail route and its job/history capabilities remain.
- **Other fixes:** #54 bounds pathological page-range allocations; #50 blocks bidi filename spoofing; #29 validates byte-range resumption; #32 reports actual PDF compression savings; #31 repairs print reorder selection; #44 keys editor state by tool/source; #45 hides redundant catalog aliases while preserving registered tools.
- **Verification & disclosures:** #37 adds read-only source-inventory checks; #39 adds real API 26/35/36 emulator smoke jobs; #40 provides five-scan preflight instructions; #53 enforces the 45-tool acceptance matrix; #28 corrects privacy disclosures; #35 discloses OCR script limits.

## Release gates — never infer success

- Full-media Android build, four JVM core-module test suites, application unit tests, lintDebug/lintRelease and both compact/release APK size checks, each **<=60,000,000 bytes**.
- Real emulator smoke workflow on API 26, API 35 and API 36. A skipped, cancelled or failed job is **not** a pass.
- Source inventory is informational; a green inventory does not qualify an Android release.
- Device-based PDF scanning, real online downloader source behavior, 200-page stress, foreground/background lifecycle and Play qualification need separate recorded execution.
- Any CI report must name the exact commit SHA and workflow run ID. Do not equate successful old PR checks with current integration CI.
