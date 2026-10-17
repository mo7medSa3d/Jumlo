# Jumlo 2026 — Phase 0 source diagnosis (baseline archive)

Scope: the supplied ZIP is **Android-only** (`app/`, `benchmark/`, Kotlin/Compose). There is no Xcode project, Swift/UIKit/SwiftUI implementation or shared KMP iOS target. Native iOS implementation and runtime validation are not possible from this archive.

| Issue | Verified root cause, original source line | Impact / approach |
|---|---|---|
| A • State leakage | `ui/AppViewModel.kt:176–179` `chooseTool` only resets when the ID differs and passes `it.sourceIds`; `navigation/JumloNavHost.kt:112–117` pops workspace without wiping its state; `AppViewModel.kt:113–122` can restore the old workspace. | New empty session on every tool open; explicit handoff function; clear on exit; preserve source archive library separately. |
| B • Media downloaded as HTML | `data/processing/DownloadOperationHandler.kt:61–86` streams a page URL's HTTP entity; `core/CapabilityPolicy.kt:35` blocks `dl_media`; `app/build.gradle.kts:55–59,66–71` restricts media implementation to optional Debug and release always has a stub. | Distinguish direct files from extractor-backed media, reuse existing pinned on-device yt-dlp + FFmpeg implementation, reject unexpected HTML, ship explicit release variants, real-device qualification required. |
| C • Download friction | `ui/downloads/MediaWorkspace.kt:160–200,240–269,314–410` exposes Analyze, browser, mode and radio-based quality selection; `MediaViewModel.kt:105–111` chooses first quality without preference; `ui/workspace/WorkspaceScreen.kt:424–427` asks for Wi-Fi toggles and filename under Advanced. | One URL field, automatic preview/selection, one primary Download action, extras under Advanced, name chosen at publication from metadata. |
| D • Files hidden | `data/output/OutputStore.kt:28–35,58–60` publishes under `context.filesDir/processing/results` and stores FileProvider URI, not MediaStore/Files location; `ui/files/FilesScreen.kt:25–80` only lists private outputs or previously granted providers with alphabetical/recent toggle. | Public, categorized Jumlo files using MediaStore or SAF; descriptive save-location UI, filters and sorting, confirmation, multi-select and trash/Undo. |
| E • Settings | `ui/settings/SettingsScreen.kt:75–79`: download location routes to `onOpenFiles` not a tree picker; `data/preferences/UserPreferencesRepository.kt:33–43` has destination/concurrency prefs that are not wired to saving; `SettingsScreen.kt:72` advertises Wi-Fi only; `navigation/JumloNavHost.kt:97` computes system animation setting only once. | Persist writable destination and actual public publication; remove Wi-Fi UI, connect shared motion/haptics settings, expose meaningful appearance and file options. |
| Navigation/design | `navigation/JumloNavHost.kt:271–278` uses an expanded rail at >=600dp and only four root tabs; `ui/tools/ToolsScreen.kt:31–42` presents uniform list rows; Home and Files lack visual hierarchy. | Five-tab floating dock including Settings at any width; compositional hero+bento tools; theme and spacing tokens, bilingual resource labels. |

## Architecture decision
Keep the existing native on-device media extractor with pinned Python/yt-dlp and FFmpeg on Android; **do not** create a backend with hosting requirements that this source cannot configure, nor claim an iOS runtime that is not present. Direct files use resumable OkHttp streaming, social media uses the extractor. The existing wrapper is large and needs release/device/ABI validation. As of October 2026 even maintained extractors are site-dependent; a green unit test is not proof of site compatibility.

## Ordered execution
1. State isolation, direct/media routing and filename policy, public output publication, persistence/settings.
2. Tokens/typography/nav with 5-tab dock and full-width adaptable layout.
3. Home, Tools, Download, Files, Settings with localized concise actions and real data.
4. Motion and haptics through preference provider + system animation scale.
5. Host tests, static resource checks, Android build/device attempt, deliver updated source ZIP and exact evidence.

Baseline: Kotlin host tests 117/117 PASS under UTF-8 (`LC_ALL=C.UTF-8`); default POSIX locale caused one unrelated Unicode-path host test to fail (116/117). No Android SDK or Gradle distribution detected in this container at baseline; no live device provided.
