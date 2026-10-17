# Current ZIP execution — 2026-10-17

## Newest follow-up (working from previously hardened source ZIP)

This section supersedes prior current-session test counts below. Source changes
are **not Android compiled**; the local SDK is absent and Gradle 8.14 could not
download. Actual host results from `followup-2026-10-17/`:

| Test/command | Result | Exact evidence |
|---|---|---|
| `LC_ALL=C.UTF-8 LANG=C.UTF-8 bash scripts/verify-host.sh` | PASS 117 Kotlin / 8 media / 10 SQLite / 14 resource | `followup-2026-10-17/verify-host.log` |
| `bash scripts/test-workspace.sh` | PASS 6 | `followup-2026-10-17/workspace.log` |
| `bash verification/production-2026-10-17/ui/run-home-layout-tests.sh` | PASS 11 | `followup-2026-10-17/home-layout.log` |
| `python3 scripts/test-media-ffmpeg.py` | PASS 5 synthetic | `followup-2026-10-17/media-ffmpeg.log` |
| `python3 scripts/test-release-media-isolation.py` | PASS 5 source assertions | `followup-2026-10-17/release-media.log` |
| `python3 scripts/generate-feature-matrix.py` | exit 0; 45 inventory entries | `followup-2026-10-17/feature-generator.log` |
| `./gradlew --no-daemon --offline :app:assembleDebug` | BLOCKED before compile: `UnknownHostException: services.gradle.org` | `followup-2026-10-17/android-build-attempt.log` |
| Android debug/unit/lint/R8/emulator/screenshots/performance, full PDF/image/ZIP end-to-end | **NOT RUN** | SDK/Gradle/devices unavailable; see report |
| Media live matrix (all named services), Google OAuth/API | **NOT RUN** | no external live qualification; remain gated |

**NO-GO** until all required gates pass and the owner approves media licenses,
font approach, signing and platform enablement. Final report in
`followup-2026-10-17/REPORT.md`.

---

This replaces any blanket PASS claims for **this modified source**. The historical files below `production-2026-10-17/` refer to the original input revision and do **not** qualify the current changes.

| Gate | Current-session result | Actual evidence |
|---|---|---|
| Host Kotlin core (initial, UTF-8) | PASS 108 / 108 | `current-session/verify-host-utf8.log` |
| Host Kotlin core (after diagnostic changes) | PASS 110 / 110 | `current-session/test-core-after.log` |
| Host Kotlin core (final with deterministic property tests) | **PASS 113 / 113** | `current-session/verify-host-final.log` |
| Repeated final auxiliary host checks (media/SQL/source) | **PASS 8 / 8, 9 / 9, 12 / 12** | `current-session/verify-host-final.log` |
| Media URL guard / bootstrap (initial) | PASS 8 / 8 | `current-session/verify-host-utf8.log` |
| SQL fixtures (initial) | PASS 9 / 9 | `current-session/verify-host-utf8.log` |
| Source/locale/font SHA static (initial) | PASS 12 / 12 | `current-session/verify-host-utf8.log` |
| Workspace cleanup | PASS 6 / 6 | `current-session/workspace-safety.log` |
| Home/fold pure policy | PASS 11 / 11 | `current-session/home-layout.log` |
| Host FFmpeg synthetic codec tests | PASS 5 / 5 | `current-session/media-ffmpeg.log` |
| Release media isolation source checks | PASS 3 / 3 | `current-session/release-media-isolation.log` |
| Android SDK and Gradle debug build | BLOCKED / exit 1 before compile | `current-session/native-environment.log` |
| Debug Android unit tests, new real zip4j tests, Android lint | NOT RUN | SDK and wrapper unavailable |
| R8 optimized release APK / AAB, dependency graphs and artifact inspection | NOT RUN | SDK and wrapper unavailable |
| API 26/36 emulator/device, screenshots, real file reader, release signing | NOT RUN | No installed app/device/keystore |
| yt-dlp live video/audio for 10 platforms, official Drive live OAuth | NOT RUN | Network/device qualification missing |

Feature-level statuses: `docs/FEATURE_STATUS.md`. Media live matrix: `docs/MEDIA_CAPABILITY_MATRIX.md`. Release owner gates: `docs/RELEASE.md`.
