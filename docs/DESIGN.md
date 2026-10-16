# Jumlo Design System — Quiet Precision (as implemented)

This is the design reference the theme sources cite. It describes what the
code actually does today, not a future target.

## Palette (`ui/theme/Color.kt`)

- Brand primary: light `#340DE9`, dark `#8F86FF`. Light background `#F9F9F8`,
  dark background `#0B0B0D` / surface `#131316`.
- Semantic pillar colors per feature family (PDF red, Images amber, Downloads
  teal, Files blue, Vault violet) plus success/warning, each with light and
  dark values in `PillarColors`.
- No dynamic color: Android 12+ wallpaper colors are not used. The fixed
  brand palettes above apply on all OS versions. Dynamic color is deferred;
  see the session report for why.
- WCAG AA contrast of every pair is **not** instrument-verified. Pairs were
  chosen against the brand ramp, not measured on device.

## Typography (`ui/theme/Type.kt`, `res/font/`)

- Latin: bundled IBM Plex Sans variable font (`ibm_plex_sans_variable.ttf`,
  weights 400/500/600 via `ibm_plex_sans.xml`).
- Arabic: bundled `ibm_plex_sans_arabic_regular.ttf` fallback for `ar`.
- Scale maps onto Material 3 type roles (`Material3Typography`): display 28sp,
  screen title 22sp, section title 18sp, body 16sp, metadata 14sp tabular,
  navigation 13sp, badge 12sp.

## Shape and spacing (`ui/theme/Shape.kt`)

- 4-unit grid spacing; Material 3 shapes with medium cards. Components live in
  `ui/components/QuietPrecisionComponents.kt` (a planned split into focused
  files is deferred; see the session report).

## Layout and motion

- Compact-width bottom dock, expanded-width (`>=840dp`) navigation rail.
  Edge-to-edge is enabled; insets are applied per screen, not globally
  audited. Predictive back callback is enabled in the manifest.
- Motion is standard Material 3 motion. Honoring the system "remove
  animations" setting beyond stock behavior is not implemented.
- All user-visible text must come from string resources (`values/` +
  `values-ar/`); hard-coded text in new UI is a bug.
