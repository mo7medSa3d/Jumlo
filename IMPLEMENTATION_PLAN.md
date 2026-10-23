# Jumlo Production Hardening Implementation Plan

> For agentic workers: execute the supplied `PRODUCTION_BRIEF.md` continuously; preserve existing features and record blocked evidence. Root coordinates integration and one independent final review.

**Goal:** repair the recovered source, validate current outputs, and deliver the complete source with honest production gates.
**Architecture:** retain Kotlin/Compose/M3, reusable core modules, existing PDFBox/streaming OOXML and swappable extractor interface. No backend deployment is authorized or necessary. Keep the local full-media runtime until measured alternatives justify a change.
**Tech stack:** pinned Gradle8.14/AGP8.10.1/Kotlin2.0.21, SDK36/min26, Room/DataStore/WorkManager.
**Spec:** `PRODUCTION_BRIEF.md`.

## Global constraints

Release ARM64 APK <60 MB (decimal cap 60,000,000 bytes); do not remove functionality to fit. Current artifact/device evidence only. Mark untested items NOT VERIFIED. No secrets in source/logs, no debug release key, no publishing. User explicitly authorized reasonable decisions and opted out of intermediate questions.

## Review focus

1. Activity recreation/pasted malformed or private URLs must preserve input and expose a localized error.
2. Low disk and revoked save folder must never report a successful public save.
3. Coroutine cancellation must propagate; catch boundaries must not hide fatal VM errors.
4. Arabic/200% font scale and gestures must keep controls visible and touchable.
5. Corrupt/encrypted/large documents must not publish empty or invalid output.

## Tasks and ownership

- [x] Audit before source edits: source inventory, dependency/build/privacy risks, 47-tool current UI map in AUDIT.md.
- [x] Root: configure isolated current toolchain, build original release for size, collect logs and establish emulator feasibility.
- [x] Root: add sanitized crash events/hook and debug StrictMode; cover event privacy/failure delegation; fix unhandled AppViewModel/PrintViewModel launches and preference errors.
- [x] Downloader agent: extractor error taxonomy, naming extensions, backend error responses/privacy logging and storage failure propagation; extend behavioral regressions. Interfaces stay existing MediaExtractor and OutputStore contracts unless coordinated.
- [x] Document agent: shared measured compression preview, structured DOCX headings/RTL, corruption mapping and PDF permissions; real compressor/OOXML regression outputs and independent parsers.
- [x] UI agent: unique official ic_tool_* vectors, flat usage-sorted Tools screen, synchronized Home actions, inline functional editors/gestures/signatures and test specs; produce TOOL_UI_SPEC.md and ICON_MAP.md.
- [x] Root integration: wire measured compression and permissions into AppViewModel/UI; preserve scanner, deep links, share intake/back stacks; add production signature alpha assertion through real renderer.
- [ ] Root: environment signing support and explicit release gates, archive/native/APK contributor analysis, reviewed CI command, screenshot evidence pipeline and benchmark status.
- [ ] Verification: run host/JVM regressions, `:app:lintRelease :app:testDebugUnitTest :app:connectedDebugAndroidTest`, optimized release APK/AAB, supplied matrices where environment permits. Capture real failure logs and fix confirmed issues; no screenshot fabrication.
- [ ] Root documentation: decisions, route map, privacy, permissions, EN/AR store draft, changelog, test matrix/results, iOS plan and release checklist; distinguish existing behavior vs added/fixed and remaining work.
- [ ] Independent review, inspect exact final diff, package complete source and reports, exclude caches/generated bulky intermediates/signing secrets, verify ZIP checksums and save final artifact.

Tests are behavior-focused; implement a failing regression before nontrivial fixes when executable. Source wiring checks do not replace runtime evidence. Missing hardware/account/authorization gates remain explicit blockers, never silently skipped.

## Qualification note

Checked implementation tasks record completed source work and host-level regression work only. Android acceptance, production signing, all-platform/live-network coverage, physical-device performance and screenshot matrices remain governed by the current evidence in TEST_REPORT.md; an implementation checkbox does not waive those gates.
