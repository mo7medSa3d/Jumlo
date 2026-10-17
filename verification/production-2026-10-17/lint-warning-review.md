# Independent lint warning triage — 2026-10-17

Scope: read-only inspection of the actual pre-correction lint report in `build/pre-camera140-lint-restrictedapi/`, relevant application callers, and the cached WorkManager 2.10.0 implementation. The report contains **266 warnings, 2 errors and 2 hints**. This triage is not a new test run or a claim that the later lint command passed; final command outcomes belong to `build-report.md` and `docs/RELEASE.md`.

The two errors were restricted `WindowLayoutInfo` constructor calls in `HingeSafeViewport`. The UI owner replaced synthetic library objects with an application-owned list obtained from the public tracker flow. No lint suppression or baseline was added. The native rerun verifies that correction.

## Warning categories

| Category | Warnings |
|---|---:|
| Performance | 166 |
| Correctness | 36 |
| Productivity | 35 |
| Correctness:Messages | 16 |
| Usability:Typography | 6 |
| Security | 5 |
| Usability:Icons | 2 |
| **Total** | **266** |

The largest groups are unused resources (153), KTX suggestions (35), newer dependency suggestions (27), plural candidates (14) and available-space suggestions (10). These counts refer to that preserved report, not an assumed final count.

## Potentially material warnings checked

| Warning | Inspected behavior and conclusion |
|---|---|
| `SourceStore.openRead` / `Recycle` | The method transfers stream ownership to the caller. Current direct consumers use `.use`; the archive factory stream is closed by `ArchiveCore.create` in `core/FileProcessing.kt`. No unclosed current consumption path was established. |
| Two EN/AR format-count warnings | `offline_pledge_short` and `import_subtitle` contain literal `100%`. Neither has a current application call site. No active formatting crash was found. |
| `ProcessingNotification` / `InlinedApi` | An inlined service-type integer is passed to WorkManager's `ForegroundInfo`. Actual WorkManager 2.10.0 bytecode checks SDK >=29 before the platform overload with a foreground-service type; SDK26–28 use the two-argument overload. No unsupported platform call was established. |
| Home mutable-collection state | `HomeScreen` replaces the observed `ArrayList`; editor operations construct new lists. The observed instance is not mutated in place, so these current changes remain observable to Compose. |
| Three trust-manager warnings | Locations are in the transitive `bcpkix-jdk15to18:1.72` JAR. No application reference to those trust managers or custom TLS/hostname validation was found. `AppModule` creates OkHttp with default TLS validation. No reachable application TLS bypass was established in this scope. |
| Cleartext network base configuration | HTTP downloads are intentional and require per-job consent in `HttpPolicy.url`. Transfer policy rejects cleartext credential headers and HTTPS-to-HTTP redirects. The documentation describes this application-level boundary accurately. |
| Webpage JavaScript | Located in the retained `OcrWebHandler` implementation. `webpage_pdf` is blocked by public capability policy, submission and dispatch. This remains an unqualified, gated capability. |
| Available-space suggestions | Existing checks conservatively reject insufficient currently available space. The suggestion to count reclaimable cache space does not identify data loss. |

No additional actionable security, data-loss or P1 defect was established by this focused warning review. This conclusion does not replace the installed-device, migration, performance or distribution criteria in the release guide.
