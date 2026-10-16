# Design Brief — Jumlo (modern redesign)

## App / product
Jumlo — an all-in-one mobile utilities toolkit (files, PDFs, images, archives, downloads). Core idea: "everything gathered into one place." Name evokes Arabic "جملة" = a whole / the complete set.

## Goal
Redesign the Jumlo app mark (and supporting visual language) to feel premium, modern, and distinctive. Communicate "the whole made of connected parts" rather than any single tool.

## Constraints
- Canvas: `viewBox="0 0 1024 1024"`, no `<text>`, no letters inside the icon.
- Concept: several distinct, rounded modules that lock together into one solid, complete form — a gathering gesture, smaller parts sealing into a larger body. One unified silhouette, visibly separate parts.
- Avoid: literal folders, PDF pages, documents, wrenches, gears, hammers, zip-file zippers, download arrows, generic stacked-squares placeholder look.
- No gradients, shadows, transparency, strokes < 40px, embedded raster images.
- Palette (exactly): primary #4F3DFF (indigo), accent #00A896 (teal, ONE small area only), white #FFFFFF.
- Safe zone: central 66% of canvas (x,y between 174 and 850).
- All corner radii ≥ 60px; smallest gap between shapes ≥ 40px (survives 48px).
- Must stay readable in grayscale (indigo and teal must differ in luminance).
- Outputs: `Jumlo_Icon.svg` (full, white background) and `Jumlo_Icon_foreground.svg` (transparent, Android adaptive foreground layer).

## Success
A single bold mark that reads as "one complete toolkit" at 48px, distinct from generic file-manager icons, passes the eval-rubric pass threshold.
