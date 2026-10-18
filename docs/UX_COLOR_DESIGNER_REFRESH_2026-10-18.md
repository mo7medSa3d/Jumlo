# Jumlo — Color Designer UI/UX refresh

Date: 2026-10-18
Source: `Jumlo-Superpowers-Audited-2026-10-18.zip` (the previously delivered audited source)
Target: native Android application (`com.jumlo.app`), Jetpack Compose Material 3
Release classification: **SOURCE CANDIDATE, NOT PRODUCTION QUALIFIED**

## Product direction

A purposeful, calmer 2026 design system for an offline-first PDF, images, downloads, and file utilities app. Keep one visible primary action per workflow, maintain fast direct access to every tool, avoid large stacks of decoration, and retain existing file-processing behavior.

The Color Designer plugin supplied an editable palette with these design anchors:

| UI role | Light | Dark |
| --- | --- | --- |
| App canvas | `#F7F7FC` | `#11111D` |
| Primary surface | `#FFFFFF` | `#201F30` |
| Primary text | `#17152B` | `#F6F3FF` |
| Secondary text | `#666178` | `#C2BED2` |
| Primary action | `#5638D8` | `#B6A5FF` |
| Primary action text | `#FFFFFF` | `#201047` |
| Active selection container | `#E9E3FF` | `#413077` |
| Container outlines | `#DDD9E9` | `#44425A` |

Category accents (PDF red, images amber, download green, files blue, vault violet) use paired tonal containers. Status semantic colors retain distinct error and success pairs. Alternate user-selected accent colors and Android system dynamic color remain available.

## Implementation

- **Shared tokens:** Updated Material 3 light/dark schemes and pillar colors in `ui/theme/Color.kt`; unified card, field, button, sheet, and navigation shapes in `ui/theme/Shape.kt`. This propagates to routes built with shared components.
- **Navigation and discovery:** Improved Home featured-action tiles and category accents, centered Home/Tools content within desktop/tablet-safe width caps, and made Tools grid change between two, three, or four columns according to available width. Floating dock retains five destinations and screen-reader names at larger font scales.
- **Downloads:** Added visible one-tap Pause / Resume / Retry for the appropriate media transfer state, readable progress percentages, direct URL keyboard Go action, an actionable empty-state hint, and a bounded-width media workspace. The destructive Cancel command remains in the contextual menu.
- **Files:** Promoted Import as the visible primary action, retained access to the saved folder, improved file row distinction and localized type labels, and kept public/private saved-file notices intact.
- **Settings:** Replaced the duplicated haptics shortcut with Files, while keeping the real haptic preferences in the grouped setting list; improved preference-row touch targets and category icons.
- **Shared UI:** Refined searchable fields, local icon tiles, empty/error-state hierarchy, subdued card boundaries, and brand-consistent splash gradients. Motion continues to honor the existing reduced-motion setting.
- **Localization:** Added `premium_ui.xml` in `values` and `values-ar`; resource names and format parameters are paired for both locales. No new font dependency.

## Scope and evidence

The changes are limited to Compose UI, theme, translated messages, and verification scripts. No PDF processing algorithm, downloader transport implementation, storage schema, or repository identity was intentionally changed.

- `python3 scripts/test-premium-ux.py` checks 15 source contracts including light/dark foreground contrast, responsive cap ordering, root-screen actions, dock accessibility, and launch identity. These are **source-level tests**, not screenshots or device tests.
- `bash scripts/verify-host.sh` completed successfully (exit 0): **181 Kotlin host tests passed, 0 failed; 15 premium UX source checks passed**, and the configured Python and shell suites completed. Two non-fatal Kotlin compiler warnings were emitted in unchanged host/core code.
- `values` and `values-ar` include **723 matching named entries**; XML parsed without errors.
- Visual layout, touch feedback, TalkBack, RTL font scaling, UI frame timing, notification permission flows, and large-file processing **have not been validated on physical devices**.
- `./gradlew :app:assembleDebug` and Android lint/instrumented tests require a trusted Gradle 8.14 installation, Android SDK platform 36, and a configured environment; Android SDK and the Gradle distribution were unavailable for this session. The checked-in official Gradle wrapper JAR passed its SHA-256 integrity check.
- This source does **not** contain an iOS application. Existing social-platform media extraction restrictions remain unresolved. It is not an App Store / Play Store release.

## Android device acceptance steps

1. Build debug with SDK platform 36 and trusted Gradle 8.14, then run Android lint and unit tests.
2. Test at 360dp, 420dp, 600dp, and tablet widths in light and dark themes; verify every screen scrolls and primary buttons are reachable above the dock/keyboard.
3. Set Android font size to its maximum and select Arabic; verify labels, input focus, TalkBack names, RTL arrows, and no content clipped.
4. Run fresh install, upgrade from existing app data, navigation/back, PDF split/merge, image conversion, downloader pause/resume, and file import/restore; verify saved destinations and cancel recovery.
5. Profile startup, scroll frames, large document memory, and low-storage error paths before any release claim.
