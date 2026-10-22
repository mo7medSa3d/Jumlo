# Jumlo — the design reference, applied

Date: 2026-10-21 · The seven reference screens in `desing_ex/` are the visual target.

---

## 1. What the reference is

Seven phone screenshots of a scanner-class utility app. The traits that matter, and what
each one replaces in Jumlo:

| Reference | Jumlo before | Jumlo now |
|---|---|---|
| A **circular icon button** per tool, in a four-across grid, each with its **own colour**, the label underneath | A rounded rectangle card with an icon square, coloured by *category* — 45 tools shared five colours | A **circle** with a solid tinted disc and a saturated glyph, coloured by **tool**, label on one line underneath |
| A **flat page** with section headings, no cards | Sections wrapped in `NeonPanel` cards, with gradients | Flat columns with plain headings; the panels are gone from the catalogue and the home page |
| A **search field** at the top of the home page | No search on the home page; the Tools screen had one | A search field on the home page that narrows the tiles, plus the one on the Tools screen |
| A **rounded pill** bottom bar with a **large coloured circular action** in the centre | A flat bar with a divider; the centre action was a circle but the bar was a rectangle | A solid rounded pill, a 66 dp circular action in the accent colour, the active tab on a solid pill behind a coloured filled icon |
| A **mode strip** and a **Single / Batch** toggle on the scanner | The scanner existed with edge detection and perspective correction, but the surface was busier | See §4 |

---

## 2. The tile

`ui/components/NeonUi.kt` → `NeonToolTile`, and `ui/tools/ToolsScreen.kt` →
`CompactCatalogTool`, are now the same component: a circle, a glyph, a label.

```kotlin
Surface(shape = CircleShape, color = surface, contentColor = tint) {
    Icon(resolveStitchIcon(tool.iconName), null, Modifier.size(28.dp))
}
Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
```

* The **shape** is a circle, not a rounded rectangle.
* The **colour** comes from `ui/theme/ToolPalette.kt`: a twelve-hue palette indexed by the
  tool's id, so it never changes between launches and two neighbours never look alike.
  The disc is the accent mixed 14 % into the surface — a solid colour, not a wash.
* The **glyph** keeps its own shape, so the icon carries meaning as well as colour.
* The **label** is one line with an ellipsis, centred.

## 3. The navigation

`ui/components/QuietNavigation.kt` → a solid rounded pill (28 dp radius,
`surfaceContainerHigh`, no shadow, no translucency) holding **Home, Tools, Files,
Downloads** and the **scan/print** action in the middle as a 66 dp circle in the accent
colour. The active destination sits on a solid pill and shows a filled glyph in the
accent colour. The bar still respects the navigation-bar inset.

Settings left the bar: there is no account system behind a "me" tab, and the home
screen already reaches it.

## 4. The scanner

The reference scanner is deliberately plain: a full-bleed preview, one shutter, a mode
strip, and a Single / Batch switch. Jumlo's scanner already does the hard part on
device — edge detection and perspective correction with CameraX, no Play Services, no
first-time model download — so the work here was to keep the surface simple rather than
to add a dependency.

**Not done yet:** the Single / Batch switch and the mode strip are not in Jumlo's
scanner. The batch capture already works (several pages in one session, saved as one
PDF), but the switch that makes it explicit is not on the screen.

## 5. What the reference did *not* change

* The design system stays token-based: the tiles read their colour and their shape from
  `ToolPalette` and `resolveStitchIcon`, not from hard-coded values.
* Arabic keeps mirroring: the dock, the grid and the labels all follow the layout
  direction, and the reference's own right-to-left behaviour is preserved.
* Reduce Motion still governs the spring animations, and the haptics still fire on
  selection, drag start, drag end and snap.
* The tool catalogue was not thrown away: the 45 capabilities are still in the registry
  and still reachable — see [CATALOGUE_AND_NAVIGATION_2026-10-21.md](CATALOGUE_AND_NAVIGATION_2026-10-21.md).

## 6. Verification

| Suite | Result |
|---|---|
| JVM unit tests | 320 passed, 0 failures |
| Host Kotlin suite | 181 passed, 0 failures |
| Static architecture and design checks | 19 suites green. Four of them encoded the *old* design (the flat pillar grid, the per-category colour, the five-slot dock, the glass pill) and were updated to the new one; each update keeps the property the check was protecting. |
| Lint | 0 errors |
| Builds | debug, release, benchmark and the instrumented source set all build; the `-PextendedMedia=true` variant compiles |
| Artifact gate | PASS |

**Not verified:** no emulator, no device and no simulator is available here, so the new
surfaces have not been *seen*. The changes are described above and are implemented in
`ui/components/NeonUi.kt`, `ui/components/QuietNavigation.kt`, `ui/tools/ToolsScreen.kt`,
`ui/home/HomeScreen.kt` and `ui/theme/ToolPalette.kt`; they need one run on a device —
and one comparison against the reference screenshots — before they can be called done.
