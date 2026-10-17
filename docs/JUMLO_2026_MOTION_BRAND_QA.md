# Jumlo 2026 — Brand, motion, tactile feedback and QA

**Scope:** This work was performed against the preceding updated `Jumlo-2026-Android-Redesign-Source.zip`, not against a Git remote. Android/Kotlin sources were changed; no iOS app/Xcode project was present. Release gate: **NO-GO pending a native Android build, device tests, and iOS implementation**.

## Phase 0 — Audit / diagnosis

| Area | Actual source root cause / starting point | Decision |
|---|---|---|
| Navigation motion | `ui/navigation/JumloNavHost.kt` had spring fades without meaningful direction, and no gesture-following workspace movement. | Spring slide transitions; track predictive-back progress on workspace; persist dirty confirmation. |
| Press interactions | `ui/components/QuietButtons.kt` had a few native controls, but most taps had no reusable pressed-state visual. | Transform/opacity-only shared `Modifier.pressFeedback()` with spring release. |
| Haptics | `ui/util/AppHaptics.kt` previously used generic feedback without per-pattern intensity, and settings only had an on/off switch. | One Android-native feedback layer with intensity Off / Light / Medium / Strong. |
| Sound | There was no local UI sound asset or playback service. | Six small locally synthesized WAV cues and SoundPool, with silent-mode and volume checks. |
| Files / Settings | `ui/files/FilesScreen.kt` defaulted to LIST, with large amounts of stacked rows; `ui/settings/SettingsScreen.kt` was primarily a single-column preferences list. | Two-column default saved outputs, quick-actions bento and compact preference rows. |
| Brand / icon | Jumlo is the established product identity; the launcher mark needed a clearer visual system. | Consistent Jumlo labeling and a neutral document/download emblem, Android adaptive vectors and iOS icon exports. |
| Typography | `ui/theme/Type.kt` already bundled IBM Plex Sans and IBM Plex Sans Arabic. | Keep existing pinned fonts; refine typography hierarchy and semantic colors, no added fonts. |

### Brand decision

**Jumlo (جملو)** is the product name. The abstract launcher mark depicts files and downloading without wordmark lettering. The Android package identity remains `com.jumlo.app` for upgrade compatibility.

## Implementation plan and executed status

| Phase | Will do | Did | Verified | Next gate |
|---|---|---|---|---|
| **1: Brand and launch** | Choose/name, SVG mark, launcher and platform variants, splash | Selected Jumlo; authored 5 SVGs; Android adaptive/background/monochrome vectors, 1024px light/dark/tinted iOS assets; branded animated Compose overlay and Android 12+ launch configuration | XML + SVG parsing, iOS PNG dimensions, localized brand, SHA-256 packaging checks | Inspect installed icons on API 26, 31, 36; supply actual iOS app for integration |
| **2: Tokens and components** | Upgrade tokens, font scale, interactive surfaces | Violet/light-dark palette; retained IBM Plex bilingual fonts; reused existing 4/8 spacing, rounded tokens; `pressFeedback`, `StaggeredReveal` | 10 sampled AA text contrast pairs ≥4.5 and 14 existing source/localization checks | Physical light/dark screenshots, font-scaling review |
| **3: Layouts** | Grid-led Home, Tools, Download, Files, Settings | Existing Home/Tools bento retained and animated; Downloads hero + live counters, Files grid-first default (old user-selected view preserved), Settings two compact quick controls; no mocked rows | Cross-screen wiring and settings/source tests | Inspect narrow tablet/foldable/large-font screenshots; some secondary screens still use lists |
| **4: Motion** | Spring navigation, indicator, shimmer, press/entrances, predictive back, completion | RTL-driven sliding dock pill, icon spring, route spring slide/fade, staged grid entrance, press scale/highlight, scroll-list virtualized by LazyColumn, reduced-motion-aware progress and completion pulse, predictive-back workspace slide/scale | Kotlin source and motion-wiring checks | Android transition behavior, real gesture/animations, 60/120fps tracing |
| **5: Tactile / sound** | Haptic intensities, sound cues and toggles | Android API fallbacks, event patterns, six original generated tiny sounds, quiet system audio gating, persisted sound/intensity settings, shared button/tab/file-row/chip feedback | WAV headers/duration/count and source wiring checks | Manual hardware vibration, silent-mode and volume checks |
| **6: Quality gate** | Performance, accessibility, RTL, tests, package | Native-safe modifiers, 48dp interactive affordances in shared controls, mirrored dock and predictive back, release asset tests, deterministic integrity-checked source ZIP | Host Kotlin+media/SQL/XML/redesign tests and 14 new Jumlo structural/asset tests | Native assembleDebug/testDebugUnitTest/lint, emulator tests, actual mid-range profile; iOS project missing |

## Architecture and dependency impact

- **No new Gradle libraries** were introduced. Compose `animate*AsState`, spring, native Activity predictive back and Android `VibrationEffect`/`HapticFeedbackConstants` are already present as platform/compiler artifacts. The audio engine uses Android `SoundPool`, not a new package.
- Six original synthesized **mono PCM WAV** clips at 22.05 kHz (tap/toggle/start/success/error/delete), totaling under 60 KiB. Generator: `scripts/generate-jumlo-sounds.py`. They do not use recordings, samples or third-party sound effects. **Sound source/license:** original generated sound design supplied with this deliverable, dedicated to CC0 1.0 for reuse; no attribution required.
- Android source retains existing pinned IBM Plex fonts and their existing OFL license as documented in `docs/DEPENDENCY_NOTICES.md`.
- All new user-facing text keys live in paired `res/values/jumlo.xml` and `res/values-ar/jumlo.xml`.
- `Download/Jumlo` is the public category root. Only SAF tree URIs are recognized as user-selected download folders; other persisted path values resolve to the current default. Saved output records and their public URIs are neither deleted nor relocated.

## Verification evidence and limits

Executed on this source revision:

- `LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8' bash scripts/verify-host.sh`
- `python3 scripts/test-jumlo-brand-motion.py`
- `bash ./gradlew :app:assembleDebug --offline` **attempted and blocked** because Gradle 8.14 is not installed and `services.gradle.org` cannot be resolved (recorded in `verification/jumlo-2026/android-build-blocked.log`). The environment also lacks `ANDROID_HOME`, Android SDK, `adb` and emulator.
- `scripts/package-source.py` checks archive SHA-256, each file's SHA-256 and CRC; source ZIP integrity can be proven without an Android build.

**These tests do not prove** app compilation, iOS implementation, tactile/sound playback, Android screen reader, API-level icon clipping, silent mode, frame pacing, 60/120 fps, or live external media downloading. All such checks remain **NOT EXECUTED**. Old compiled artifacts/logs from the prior supplied archive should not be cited as evidence for this new revision.

### Remaining limitations and risks

- iOS *app*, iOS haptic/sound/toggle code, iOS splash, and iOS asset-catalog integration are **not implemented**: the archive has no Swift, Xcode, Flutter, or React Native iOS application. Only editable SVG + exported iOS icon assets are provided.
- `SharedTransitionLayout` is present, but individual list-to-detail **shared-element bounds/hero morphs are not fully bound** across all destinations.
- Central controls and principal screen actions receive press/haptic feedback; **not literally every legacy dialog, menu, icon and action across all secondary screens** has a bespoke press animation or feedback cue. This needs an interactive accessibility sweep.
- No benchmarking/profile traces were captured; **zero dropped frames cannot be claimed**.
- Haptic events for completion currently dispatch from observable in-app job transitions, not as a background-service notification if UI is absent. Automatic feedback while fully backgrounded is not promised.
- Existing functional downloader limitations and live third-party extraction are inherited and not independently reverified by this motion/brand pass.

### Mandatory release-device checklist

1. Assemble an Android debug APK, run JVM and instrumentation tests, lint and R8 release compile.
2. Check 26/30/31/34/36: adaptive icon masks + monochrome, native launch and Compose splash, app upgrade data, deep links, old and new folders.
3. Tap every visible control on Home/Tools/Download/Files/Settings/Workspace/Activity/Scan, and retest Off/Light/Medium/Strong, Sounds off/on, physical mute and system vibration disabled.
4. Verify Android gesture back on edited and unedited workspaces, full Arabic RTL, font scaling at 1.3/2.0, TalkBack semantics, target sizes, and screen rotation/fold.
5. Profile cold/warm startup, scrolling, download animation and preview at 60/120Hz with `Macrobenchmark`, Perfetto and system frame stats on mid-range hardware. Reject release if meaningful jank remains.
6. Integrate exported brand assets into a real iOS target **when a native iOS source project is supplied**; verify iOS Reduce Motion, Dynamic Type, dark/tinted icon variants and haptic/audio silence settings.

**Release recommendation: NO-GO until above gates pass.**
