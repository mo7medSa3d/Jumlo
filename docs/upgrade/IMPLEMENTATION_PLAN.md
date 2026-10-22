# Jumlo: incremental interaction and printing upgrade

## Authority and scope

The user supplied the existing Jumlo source archive and requested phases 0–6. Their subsequent instruction, “applay all phases dont stop”, authorizes implementation and verification without intermediate phase approvals. The original extracted archive is preserved outside this working copy.

Keep the app identity, existing tools, saved settings, Room schema, job publication guarantees, source immutability, and optional media variants. Use the existing Compose, Material 3, PDFBox, coroutine, Hilt, and DataStore dependencies. Add dependencies only when necessary, recording the exact version and reason.

## Design

Refine the existing teal Material 3 system with its current dark and optional dynamic color modes. Use the bundled English and Arabic fonts, logical UI directions, a common motion/elevation/spacing vocabulary, and accessible control sizes. Keep navigation familiar. Add a clearly reachable Print destination alongside files and tools; printer discovery and copies are owned by Android's print dialog, with explicit guidance and service settings when no printer is available.

Document editing uses stable page instances (including duplicates), reversible snapshots, per-instance rotation, selection actions, and drag reordering. Image geometry remains in document coordinates independent of RTL. The paper preview and print renderer share a tested geometry model. Printing is separate from processing jobs: preparing a PDF does not mean a physical page has printed.

## Tasks and phase checkpoints

1. Phase 0: preserve the audit findings, source inventory, and actual baseline verification. The baseline fails Kotlin compilation because QuietTonalButton lacks the enabled parameter supplied by SmartDocumentScanButton; the host suite also has a stale compression expectation and auxiliary checks reference missing fixtures/CI options.
2. Phase 1: restore compilation, centralize existing motion/elevation/radii tokens, correct Arabic font weight registration, preserve dark/dynamic color and RTL. Run debug build and unit tests.
3. Phase 2: connect Print navigation and file entry points, fix external file intake and job notification destinations, preserve bottom navigation and predictive back, improve content transitions. Run build/tests.
4. Phase 3: stable page instance editor, long-press selection/reorder with edge scrolling, per-page rotate/duplicate/delete/print, full undo/redo, transform-aware crop, assistive alternatives, accurate watermark placement. Run editor logic tests and build/tests.
5. Phases 4–5: add real PrintManager/PrintDocumentAdapter output, cancellable preparation, system job tracking/cancel/retry, bounded paper previews, print presets, recent/history entries, favorites and page options. Preserve vector content when feasible and clearly describe any raster path. Run pure geometry/range tests and print adapter tests plus build/tests.
6. Phase 6: fix source-confirmed processing failures (download restart, compression target contract, image memory accounting, form data/flattening, scanner geometry), swipe/undo, async empty/loading/error states, 48dp targets, semantics, haptics and reduced motion. Run the complete available host, Android unit and lint checks. Attempt device validation if the environment supports it; never claim hardware printing, TalkBack, or frame-rate verification without evidence.
7. Review changes against requirements, package updated source and a tested debug APK if available, and record every change, dependency, format extension, verification result and remaining platform limitation.

## Ownership and interfaces

Root owns theme, buttons, navigation, MainActivity, AppViewModel, Files/Home shell, manifest, CI/build scripts and integration. The print implementation owns new printing package/files and its own localized resource files. The editor implementation owns page/visual editor files, WorkspaceDraft/PdfOrganizeSettings and a new pure page editing model. The processing implementation owns existing processing and PDF backend fixes except the editor's settings declaration. Coordinate the organize execution branch through an additive pageEdits property with a legacy fallback.

Print entry contract: com.jumlo.app.printing.PrintSource(path, name, mimeType, pages = emptyList()); PrintPage(sourcePage, rotationDegrees = 0) is one-based. PrintScreen(source, onBack, onPickDocument, modifier) hosts setup and persistent print history. Source-null shows the print home/history and an import action. The editor's optional onPrint callback returns List<Pair<Int,Int>> of source page and relative rotation, preserving ordering and duplicates. Root resolves the immutable source path before opening printing.

## Validation policy

Do not run simultaneous Gradle invocations against this shared workspace. Root records actual checkpoint results. Unit tests must cover observable behavior, especially page mutation/undo, printed geometry/page range mapping, cancel/retry, image size contracts, and form preservation. No passing claims based on static source inspection alone. Device-only behavior remains explicitly unverified if a device cannot be provisioned.
