# Workspace and state integration — 2026-10-17

## Implemented

- Distinct explicit saved workspace and automatic recovery records; sensitive passwords/URLs excluded from durable serialization.
- Startup waits for both preferences and workspace restoration. The explicit checkpoint is the fallback if automatic recovery cannot be parsed. Captioned file shares do not become download requests.
- Save/Discard navigation waits for the durable transaction; dirty work intercepts completed predictive-back gestures only. Export state uses SavedStateHandle.
- Durable submission fingerprint/checkpoint prevents accidental duplicate jobs after recreation. Freshly reentered download input is rebound to the original opaque handle, and waiting jobs explicitly retry. Unused secrets are removed.
- Busy scopes do not race a single Boolean; storage refresh cancels obsolete work. Only expired reproducible cache is eligible for cleanup, with symlink/age/cancellation boundaries.
- Grouped persistent Settings, truthful Account & Data, actual notification status, current Wi-Fi default for new workspaces, and shared haptic preference consumption.
- Essential watermark text/logo selection, PDF stamp text/page numbers, and annotation text/page; model validation rejects missing content before dispatch. Hidden PNG/target quality fields do not invalidate a job, while meaningful JPEG/EXIF quality remains validated.
- Batch rename computes the same `PureDomainAlgorithms.batchRenamePreview` used by the handler on Dispatchers.Default. A result carries its full names/settings identity: stale results cannot enable a changed request. Proposed names appear in input rows and a full scrollable preview before creating copies. No file is changed by previewing.

## Actual evidence

| Scope | Result | Evidence |
|---|---|---|
| Temporary cleanup on real filesystem | 6 PASS | `workspace-green.log` |
| Independent original remediation JVM review | 11 PASS at that source revision | `review-focused-jvm.log`, `review-report.md` |
| Expanded current workspace/secret validation | RED: 20 executed, 5 failed; GREEN: 20 PASS | `workspace-essentials-red.log`, `workspace-essentials-green.log` |
| Current source/resource consistency | 12 PASS | Native/host evidence recorded separately |

The expanded run compiled real production Kotlin with the real serialization compiler plugin, serialization 1.7.3 and JUnit 4.13.2. It used no Android or serialization stub. Its 20 cases comprise 18 workspace-settings tests and two submission-secret tests; they overlap the earlier 11 and the native unit suite.

Compose regression sources cover waiting-job Retry, target-size fields, watermark content/mode, and annotation text/page. Room regression sources cover preference persistence, saved/recovery isolation, sanitization and durable submission identity. Authored test source or successful compilation is not an installed test pass.

## Remaining verification

Full Android process death/WorkManager lease timing, external provider/grant loss, Back gesture cancellation, orientation/font/locale recreation, notification denial, real output roundtrips, input-method/fold behavior and visual acceptance need connected-device runs. This report does not claim those results. Final native command outcomes are in `build-report.md` and `docs/RELEASE.md`.
