# New professional tools — research, and how they compare

Date: 2026-10-20 · For the Jumlo utility app · Every claim below is from vendor
documentation, published feature matrices, or observed behaviour in the product
documentation set; nothing is inferred from marketing copy alone.

The short version: **Acrobat is the reference for *what a professional PDF tool must
contain*; Lens is the reference for *how a capture-and-edit flow must feel*.** Jumlo's
gap is not that it lacks features — it is that Acrobat's feature set is spread across
paywalled tiers and desktop-first UI, and that a phone-first app can deliver the same
capabilities in a fraction of the interaction cost.

---

## 1. The competitive floor

| Product | What it actually is | Where it wins | Where it is weak for a phone-first utility |
|---|---|---|---|
| **Adobe Acrobat** (mobile) | The reference implementation of the PDF feature set: edit, sign, scan, convert, compress, forms, AI assistant. | Breadth and fidelity; Fill & Sign is genuinely excellent; OCR and AI assistant are the premium hooks. | Almost everything beyond viewing is behind a subscription (OCR capacity, AI). It delegates printing to the system dialog, so it has **no print history, no print favourites and no named print presets**. Desktop-class UI on a phone. |
| **Microsoft Lens** | A capture pipeline, not a document suite: edge detection, perspective correction, clean-up filters, one-tap export to Office/PDF. | The scan experience. It is fast, forgiving, and the filters genuinely work. | It is not a PDF tool. It has no merge, compress, watermark, redact, forms, or viewer. It hands the result to another app. |
| **PDFgear** | Free, no account, no ads, no watermark, OCR in 30+ languages, and a natural-language "Copilot" that can print. | Price. It gives away everything. | Not a print product, and the "print" is one sentence in a chat box. |
| **Xodo / Foxit / iLovePDF / Smallpdf** | Web-first document editors. | Fast, broad, iLovePDF has good page-number and watermark pickers. | All of them delegate printing to the browser or the system dialog. None has print history, favourites or presets. Smallpdf gates hard (one free task/day). |
| **iOS Files / Notes** | Not competitors, but the bar for *file management feel*: gesture-driven, immediate, no chrome. | The interaction standard. | — |

**The uncontested gap.** No commercial app — Adobe, Foxit, PDFgear, Xodo, iLovePDF,
Smallpdf, Samsung Mobile Print, HP, Canon, Epson or Brother — ships **print history +
one-tap reprint + print favourites + named print presets** on mobile. Jumlo already has
all four. That combination is the commercial wedge; everything below is table stakes
that a phone-first app can deliver more cheaply than the desktop incumbents.

---

## 2. The new tools, one by one

### 2.1 E-signature — draw, type or import, place, resize by the corners, reuse

**Research.** Adobe Fill & Sign is the reference: it is free, and it is the reason Adobe
can claim a "sign anything" position. Foxit and Xodo both offer drawn *and* certificate
signatures; iLovePDF, Smallpdf and eFax all offer a visual signature. Adobe Sign/Dropbox
Sign own the *multi-signer routing* half of the market — that part is a server product.

**Gap in Jumlo today.** `pdf_signature` places a drawn or imported signature image in a
fixed rectangle. It cannot be resized after placement, and a signature cannot be saved
for reuse — so signing five documents means drawing it five times.

**What to build.** A signature library: draw / type / import once, save it, then drag it
onto any page and **resize it by dragging a corner handle** with the same visual grammar
as the crop tool. Keep the existing security model — the signature is a session-only
ephemeral secret and never touches the file until the user commits.

**Comparison.** Acrobat: more capable (it can request and track signatures from others —
out of scope, that is a server product). Lens: no signing at all. **Jumlo should match
Acrobat's *local* signing quality and beat it on ergonomics for single-document signing.**

### 2.2 OCR — searchable, copyable, Arabic and English

**Research.** OCR is now table stakes: Foxit, Xodo, Smallpdf, Adobe, PDFgear, iLovePDF
and Adobe Scan all ship it. Smallpdf gates it behind PRO; Adobe gates capacity (5→100
pages). Every one of them runs a desktop-grade OCR engine.

**Gap in Jumlo today.** `pdf_ocr` is implemented but **deliberately gated**
(`SEARCHABLE_OCR_INTEGRITY_QUALIFICATION_PENDING`). ML Kit — already a dependency for
the scanner — returns word *boxes*, not glyph-perfect layout, and a real invisible text
layer needs font subsetting and a CMap.

**What to build.** On-device ML Kit text recognition written into an invisible text
layer, with the honest caveat that it is a *searchability* layer, not a
glyph-identical reproduction. Verify against a reference reader on a device before
shipping. Arabic and English must both be measured, because Arabic shaping and
bidi ordering are where a naive text layer fails.

**Comparison.** Acrobat: better fidelity, subscription-gated. Lens: captures text but
does not produce a searchable PDF. **Jumlo's angle is on-device privacy and zero-cost
Arabic search — but only if the layer is honest about its limits.**

### 2.3 Redaction — remove the content, not cover it

**Research.** Adobe Acrobat's "Redact" and Foxit's redaction both *destroy* content;
painting a black box is explicitly called out as not redaction. PaperCut treats redaction
as a compliance feature.

**Gap in Jumlo today.** `pdf_redact` exists and is now **released** (it was gated until
`PdfSecurityQualificationTest` proved the word is physically absent from the saved
bytes). It still has no annotation-style workflow — the region is set numerically in the
settings sheet rather than drawn.

**What to build.** Draw the region (the visual crop editor already exists), and make the
redaction semantics explicit in the UI: *content removed, not hidden*.

**Comparison.** Acrobat: same semantics, worse ergonomics on a phone (numeric region).
Lens: no redaction. **Jumlo's version is already stronger on the guarantee and should be
stronger on the interaction.**

### 2.4 Annotations — highlight, underline, strikethrough, note, freehand, shapes, text box

**Research.** Every serious editor has at least highlight, underline, note and freehand.
Acrobat adds strikethrough, shapes and text boxes; Xodo and Foxit match it.

**Gap in Jumlo today.** `pdf_annotate` writes a single **text or note** annotation at a
position. There is no highlight, no underline, no strikethrough, no shape and no freehand.

**What to build.** A tool palette over the existing visual editor, each kind mapped to a
real PDF annotation so the result is a genuine, selectable, comment-aware PDF. Undo and
redo must cover every one of them.

**Comparison.** Acrobat: the reference for annotation *types*; again desktop-first.
Lens: none. **Jumlo can match the type list with direct manipulation on a page.**

### 2.5 Form filling — detect the fields, tap to fill

**Research.** Adobe's forms engine is the reference; DocuSign and Dropbox Sign own the
signing workflow. Browser-based PDF forms are the everyday need.

**Gap in Jumlo today.** `pdf_forms` fills AcroForm fields **by name** from a JSON blob.
The backend can already list the fields (`listFormFields`) and detect widgets
(`PDAnnotationWidget`), but the user has to type field names.

**What to build.** Detect the fields, lay them out as tappable targets over the page
preview, and let the user tap a field and type into it. Flattening is already optional
and should stay.

**Comparison.** Acrobat: fills and signs; Lens: none. **Jumlo should make filling a
five-field form a fifteen-second job.**

### 2.6 Page editor — grid, drag-to-reorder, drag-to-another-file, multi-select

**Research.** Acrobat's Organise Pages and every "combine" flow assume a desktop file
manager. On a phone the useful gesture is *move a page from one document to another*,
which is the operation people actually want (pull the signed page out of the packet).

**Gap in Jumlo today.** `pdf_04` has a real page grid with drag-to-reorder, rotation,
deletion and duplication. It does **not** let a page be dragged into a second document,
and multi-select exists only inside the organise tool.

**What to build.** Multi-select across documents, and a drag that can cross into a second
file in the queue.

**Comparison.** Acrobat: desktop only. Lens: none. **This is a genuine phone-first
opportunity — Acrobat cannot do a cross-document drag at all.**

### 2.7 Batch processing — one operation, many files

**Research.** PaperCut, PrinterLogic and ezeep all expose batch or fleet operations.
On mobile, Brother and Samsung offer "select all pages" for scans; nobody offers
cross-document batch processing on a phone.

**Gap in Jumlo today.** `file_batch_rename` is a copy-based rename with a preview.
There is no "apply this compression/watermark to these twenty files".

**What to build.** Select many files, choose one operation, apply it to all with a
per-file result (some succeed, some cannot) and a single undo.

**Comparison.** Acrobat: "Combine" only. Lens: none. **Jumlo's batch is a real SMB need
that no mobile PDF app covers.**

### 2.8 Templates and branding — a company profile applied everywhere

**Research.** Zoho Books/Invoice ship module templates with 1/2/3-copy printing; Zebra
PrintConnect merges variable data into centrally managed label templates; iLovePDF has
page-number and watermark pickers. The common thread: a saved *profile* (logo, name,
footer) reused across documents.

**Gap in Jumlo today.** `pdf_stamp` applies a watermark with per-run parameters. There
is no saved profile.

**What to build.** A saved brand profile (logo, display name, footer line) applied to
watermarks and exports in one tap.

**Comparison.** Acrobat: Acrobat Sign has "brand templates"; desktop Acrobat has no
profile concept. Lens: none. **The reusable profile is the missing piece in every
document app.**

### 2.9 Secure sharing — password, expiry, destination

**Research.** Adobe Acrobat's "Send for comments / Protect" and the whole secure-document
category (AirPrint-style enterprise, PaperCut secure release) are **server products**:
expiry enforcement needs a server to check, or a PDF extension that most readers ignore.

**Gap in Jumlo today.** Password protection now works locally (`pdf_protect` is released).
There is no expiry and no share-with-expiry.

**What to build.** Be honest about the platform limit: a password is enforceable by any
reader because it is part of the PDF encryption; an **expiry date is not** — it is a
convention that only a cooperating reader honours. The honest product is: password
protection (real), a visible "expires on" stamp in the document metadata and footer, and
clear copy that the expiry is advisory. Export to Drive/Dropbox/OneDrive is a share
sheet, not a feature — it should be the system picker plus the SAF targets, not a
bespoke uploader.

**Comparison.** Acrobat: real secure delivery, because Adobe runs the server.
Lens: none. **Jumlo can do the on-device half honestly and must not pretend about the
server half.**

### 2.10 Document comparison — what differs

**Research.** Acrobat and Foxit both diff two documents and report changed pages;
Acrobat goes to word level. Diff tools (Beyond Compare, `diff`) are the desktop
reference.

**Gap in Jumlo today.** `pdf_compare` exists and reports the first differing page plus a
per-page text verdict. It does not show a side-by-side view.

**What to build.** A side-by-side page viewer with the differing regions highlighted,
built on the existing page-text extraction.

**Comparison.** Acrobat: the reference. Lens: none. **Jumlo already has the verdict
engine; the missing half is presentation.**

### 2.11 Metadata cleaner — strip author, location, tracking

**Research.** Metadata leaks are a routine finding in document-sharing reviews: the
author, the GPS coordinates of a phone camera, the editing software, the creation
timestamp. "Document sanitiser" is an established commercial category.

**Gap in Jumlo today.** `pdf_metadata` *writes* title/author/subject/keywords. There is
no cleaner.

**What to build.** One action that removes the authoring trail — author, creator,
producer, creation and modification dates, and the XMP metadata — and reports exactly
which keys it removed. The PDF needs a real "scrub" pass, not just clearing the visible
fields: the document information dictionary, the XMP packet, and any embedded file
history.

**Comparison.** Acrobat: ExifTool-style scrubbing is not a first-class Acrobat feature;
it is an enterprise/DLP concern. Lens: none. **Small, high-trust, and almost nobody in
the consumer app market does it properly — a good differentiator.**

---

## 3. The interaction standard the brief asks for

| Requirement | Why it is the right call | Current state |
|---|---|---|
| Drag and drop as the primary verb | Every one of the tools above is a spatial task. A number field is a fallback, not a primary. | Crop, watermark, redact, signature placement, image overlay and page reorder are already direct. The compression tool still has a DPI field. |
| No typing coordinates, margins, DPI or page numbers | Typing "0.18" is asking the user to do arithmetic the app can do. | Margins, crop and quality are visual. **DPI and page numbers are still numeric fields.** |
| A compression slider with a live size estimate | One gesture, one number, immediate feedback. | **Done**: the slider replaces the typed DPI and quality fields, and the estimate comes from the real embedded-image bytes of the source. The estimate is labelled an estimate; the result card reports the real size. |
| Snapping guides with haptics at snap points | Alignment by eye is imprecise; guides make it exact and the haptics confirm it. | **Done**: edges snap to the centre, the thirds and the borders, the guide is drawn and the snap fires a haptic tick. |
| Undo and redo for every transformation, with a history panel | A history *panel* makes undo discoverable; hidden undo is not a feature. | **Done**: the visual editor lists every recorded step and restores it directly; undo/redo also exist in the watermark, page and print editors. |
| Multi-select by long press, two-finger zoom and rotate | The gesture vocabulary of every professional mobile editor. | **Done**: long-press multi-select exists in Files and the page editor, and two fingers now turn the preview (bounded to 45°). |

**A hard rule for the compression estimate.** A size estimate must be labelled an
estimate. The tool may only ever *report* the real output size, and the real size is
the only number that counts.

---

## 4. What this means for the build order

1. **Finish what is promised before adding what is new.** The audit found two shipped
   tools invisible to users (`pdf_protect`, `pdf_redact`), a test suite that could not
   run, and a build that died with an unexplained version string. Those are fixed.
2. **The annotation and signature work is the highest-value new build**, because the
   backend already knows how to place objects on a page, the visual editor already
   supports direct manipulation, and the missing piece is the tool palette.
3. **Metadata cleaning, batch processing and the brand profile are cheap and
   differentiated** — three features a price-led consumer app gives away, done properly.
4. **OCR and secure expiry are the two that must not be rushed.** Both need a device and
   a reference reader to qualify honestly.
