# Jumlo — the catalogue, the navigation and the surface

Date: 2026-10-21 · Follows the product review: *"the problem is not a shortage of tools, it is
that the app shows a large number of tools with the same weight, while some are rarely used,
some are duplicated, and some should be part of a bigger tool."*

---

## 1. The audit

The registry carries **45 capabilities**. Before this pass the Tools screen drew a flat
grid of every one of them, grouped only by category, with every tile the same size and
the same importance. The home page drew a second flat grid of the same tools. That is
the "dozens of icons with the same weight" problem, measured.

What the review asked for, and what the code now does:

| Review | Decision | Where it lives now |
|---|---|---|
| EXIF inspector | merged | Images → more (folded) |
| PDF metadata | merged | PDF → more (folded) |
| Image comparison | hidden | out of the catalogue, still in the registry and in the search |
| PDF comparison | hidden | removed last turn, with its dead backend |
| Target size | merged | Images → Compress |
| Adjust and pad | merged | Images → Edit |
| Crop, rotate, flip | merged | Images → Edit |
| Resize | merged | Images → Edit |
| PDF crop / reorder / extract | merged | PDF → Organize pages |
| Add image to PDF | merged | PDF → more (folded) |
| Fill PDF form | merged | PDF → more (folded) |
| Direct download + batch | merged | Downloads → one manager |
| Scan document | kept and improved | its own workspace |
| Metadata cleaner | kept and improved | PDF → Clean the authoring trail |

**Nothing was deleted.** The registry still holds all 45 capabilities, the search still
finds every one of them, and every one of them still runs. What changed is the surface.

---

## 2. The catalogue

`app/src/main/java/com/jumlo/app/core/ToolCatalogue.kt` (new) is the single place that
decides what the app shows.

* **18 tiles** across five workspaces, against 45 before.
* Every capability is either a **primary** tile, a **merged** action inside a workspace,
  or **deliberately hidden** (the comparison specialists and the navigation-only
  destinations that live in their own screens).

| Workspace | Tiles | Folded into it |
|---|---|---|
| **PDF** | Merge, Split, Organize pages, Compress, Convert, Sign, Clean | Images→PDF, Extract pages, Crop pages, Add image, Fill form, Annotate, Watermark, Properties, Password, View, OCR, Webpage→PDF |
| **Scan** | Scan document | OCR (gated) |
| **Images** | Edit, Resize, Compress, Convert, Join, Watermark | Adjust and pad, Target size, Stitch, EXIF |
| **Files** | Archive, Duplicates, Batch rename | Extract archive, Encrypted archive, SHA-256 |
| **Downloads** | Download manager | Batch links, Media, Download settings |

The Tools screen draws one labelled section per workspace: the primary tools as tiles, the
folded operations as a row of chips underneath. A person sees *six* PDF functions, not
sixteen.

### The tests that lock it down

`app/src/test/java/com/jumlo/app/core/ToolCatalogueTest.kt` (new, nine cases) fails if:

* a capability is listed that is not in the registry (a typo would make a tool
  unreachable),
* a capability is listed twice,
* the visible surface grows past twenty tiles,
* a workspace loses its way in,
* a registered capability ends up in no workspace at all,
* the scanner or the metadata cleaner disappear.

Two of these tests caught real defects while the work was being done: `pdf_stamp`
(watermark) and `sha256` were in the registry but in no workspace, so both would have
become unreachable.

---

## 3. The navigation

The bottom bar was a floating glass pill with five slots that included both *Home* and
*Tools* (the same screen to the user) and no Downloads at all.

**Now:** a flat bar with a divider — Home, Tools, Files, Downloads — and the scan/print
action in the middle as the prominent primary action. Settings moved off the bar (there
is no account system behind a "me" tab) and is reached from the home screen, where it
already was. Printing stays an action inside the document screen rather than a
destination of its own.

The home page now shows **four quick actions** (six on a wide screen) and a short list of
relevant tools with a way into the rest, instead of a second flat wall of tiles.

---

## 4. The UI defects the review named

| Complaint | Fix |
|---|---|
| Five icons across a narrow screen | The catalogue grid is **three across** on a phone, four on a tablet, six on a wide screen, and it drops to two with large text. |
| Tool names breaking over two lines | One line with an ellipsis, centred. |
| Rows disappearing under the bottom bar | The lists keep the dock-height bottom buffer; the dock is a flat bar now, so it covers less. |
| Large gaps in Settings, arrows far from the text | The chevron follows the text instead of being pushed to the far edge. |
| Glassmorphism, gradients, heavy cards | The tool tile is a flat surface with a solid tinted container; the hero card is flat; the dock has a divider instead of a shadow. |
| Every icon the same colour | `ui/theme/ToolPalette.kt` (new): a twelve-hue palette, one hue per tool chosen from its id, so the colour is stable and the grid can be scanned. |

---

## 5. What is deliberately not done, and why

| Asked for | Why not, yet |
|---|---|
| **ML Kit Document Scanner** | It needs a Play Services dependency, a first-time model download, and it would break the app's offline-first promise. The app already detects edges and corrects the perspective on-device with CameraX, and the *filters* (shadow removal, handwriting enhancement) are the part worth adding. |
| **Arabic OCR** | ML Kit's text recognition v2 is Latin-only. Arabic needs Tesseract with a local model — a large dependency and an accuracy problem that needs real measurement. `pdf_ocr` therefore stays **gated** rather than shipping a search that silently fails on Arabic. |
| **Batch processing, PDF search and bookmarks, the unified image editor, HEIC/WebP** | Real work that is not started. The catalogue now has the right shape to hang them on: batch belongs to Files, search and bookmarks belong to the PDF workspace, the image editor to Images. |
| **iOS** | There is no Swift source in this repository and no Swift toolchain on this machine. The iOS icon set is correct and opaque; the client would have to be written. |

---

## 6. Verification

| Suite | Result |
|---|---|
| JVM unit tests | 320 passed, 0 failures (nine new catalogue cases) |
| Host Kotlin suite | 181 passed, 0 failures |
| Static architecture checks | 19 suites green |
| Lint | 0 errors |
| Builds | debug, release, benchmark and the instrumented source set all build; the `-PextendedMedia=true` variant compiles |
| Artifact gate | PASS |
| Downloader, live | 2 passed against the running extraction service |
| Downloader, platform matrix | 5 platforms and both direct-link types work; see the downloader document |
