# Jumlo — current verification report

Date: 2026-10-23. **IN PROGRESS — NOT APPROVED FOR PRODUCTION.**

This report supersedes the report recovered inside the supplied archive. The
recovered report is preserved as `docs/history/RECOVERED_TEST_REPORT.md`; its
claims and artifact identities are not current evidence. Source implementation,
host execution, Android compilation, emulator execution and physical-device
qualification are separate statuses. Counts from overlapping suites must not
be summed.

## Current gates

| Gate | Current evidence | Status |
|---|---|---|
| Untouched uploaded source release build | Fresh `:app:assembleRelease`, 54,878,634 bytes, ARM64 full media runtime | BUILD PASS; runtime NOT VERIFIED |
| Baseline APK identity | SHA-256 `2ce34fb8b56c41f222adb94f023150853e762a671b47b1e604513d1bdff20a35` | MEASURED |
| Final release APK <60,000,000 bytes | Pending final source freeze and rebuild | NOT VERIFIED |
| Current debug/release Kotlin compilation | Both completed in current-validation-4; subsequent review fixes require recompilation | PARTIAL BUILD EVIDENCE |
| Required lint/unit/device command | Attempt failed resolving older offline AndroidX test dependencies; dependencies aligned, rerun pending | NOT PASSED |
| Baseline API 26 install | Fresh 120,034,709-byte debug APK installed successfully | PASS, installation only |
| Reported paste-link crash | Baseline reproduction in progress; original failing URL/stack trace not supplied | ROOT CAUSE NOT VERIFIED |
| Physical device | No physical Android device is attached | NOT VERIFIED |
| Android 14+ | Emulator setup attempted; execution pending | NOT VERIFIED |
| Production signing | Owner release credentials absent; no debug key fallback allowed | BLOCKED |
| Cold start <2 s / no dropped frames | No qualifying mid-range physical device measurement | NOT VERIFIED |
| App-specific baseline profile | Existing files contain comments only; profile generation pending | NOT VERIFIED |
| Live social-platform matrix | No completed fresh end-to-end platform sweep | NOT VERIFIED |
| Store publication | No Play upload or policy approval performed | NOT VERIFIED |

## Fresh host evidence

These rows establish only the scope stated. They do not establish Android
runtime correctness.

| Suite | Observed result | Scope |
|---|---|---|
| Diagnostics | 5 tests passed | Bounded sanitized events, rotation and delegated crash handler |
| Compression estimate regression | Old errors 43.03%, 43.28%, 52.11%; new shared runner 0% on three samples; 30 assertions passed | Real PDFBox, host ImageIO codec |
| Office packages | 14 checks passed | Actual DOCX/PPTX validation, headings/paragraphs/RTL/blank rejection/cancellation |
| Independent Office readers | 3 DOCX, 2 PPTX, 22 images opened | python-docx, python-pptx, Pillow; not Microsoft Office |
| PDF studio checkpoint | 45 JUnit tests passed | Real saved PDF geometry/operators/forms/security; later small additions await final rerun |
| Large-file qualification | 104 pages, 54,517,881-byte source → 9,831,756-byte compressed PDF | Host-only synthetic image corpus, `-Xmx128m` |
| Large-file resource sample | 108,635,464-byte used-heap peak; 269,985,299 bytes total corpus file lengths; RSS unavailable | File-length total includes source, outputs and scratch; not RSS or scratch alone |
| Downloader/publication checkpoint | 55 JUnit tests passed | Host policy/contracts/cancellation; Android providers/Keystore await device tests |
| Media service server-error checkpoint | 20 Python tests passed | Service behavior and sanitized server-error boundary |

## Evidence limitations and pending matrices

All 47 catalogue entries (44 active and three pre-existing capability-disabled
entries) require normal/large/encrypted-or-corrupt/100+ page qualification where
applicable. The seven download platforms require video/audio/quality/invalid/
offline/resume-after-kill qualification. Unless a later row supplies a specific
current artifact, test and device result, these cells are **NOT VERIFIED**.

The unbundled OCR recognizer remains Latin-only. Embedded Arabic Unicode text
and DOCX RTL support are distinct from scanned Arabic OCR, which is unsupported
by this recognizer. Full TalkBack, dynamic-accent contrast, 200% clipping,
CameraX permissions/capture, ML Kit scanner availability, background limits,
low-disk/provider revocation, native ARM64/16 KB runtime and process-kill
publication boundaries remain device gates.

Final exact commands, report counts, artifact sizes/hashes, screenshots and
remaining failures will replace this in-progress status after verification.

