# Design Eval Rubric — Jumlo

Weighted scoring for the Generator → Evaluator design loop. Total possible = 10. Pass threshold default 7.5 (configurable via `--pass-threshold N`).

### Design Quality (weight: 0.35)
Does the composition feel premium and intentional? Balanced, confident, not cluttered. Clear focal point, strong silhouette, harmonious proportions. Reads as high-end, not templated.

### Originality (weight: 0.30)
Avoids category clichés (folders, documents, wrenches, gears, zippers, download arrows, stacked squares). Pushes a creative leap — unusual but coherent form language for "gathered parts into one whole."

### Craft (weight: 0.25)
Execution precision: consistent corner radius (≥60px), clean gaps (≥40px), correct safe-zone containment, exact palette (#4F3DFF / #00A896 / #FFFFFF), teal used in exactly one small area, valid clean SVG (no text/comments/metadata/gradients/raster).

### Functionality (weight: 0.10)
Readable at 48px, grayscale-distinguishable (indigo vs teal luminance differ), works as Android adaptive foreground + full icon, no thin hairlines that vanish at small sizes.

## Pass rule
Weighted score ≥ pass-threshold (default 7.5) to exit the loop; otherwise iterate Generator → Evaluator up to `--max-iterations N` (default 10).
