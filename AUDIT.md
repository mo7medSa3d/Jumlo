# AUDIT.md — Jumlo current truth (this session, branch `arena/ee36ee56-jumlo`)

Supersedes all earlier "passed" claims. Reproduced HERE, not inherited.

## Environment reality

This sandbox has **no JDK, no kotlinc, no Android SDK/emulator**, and network
egress only to github.com / registry.npmjs.org / pypi.org (Google/Maven/Gradle
hosts blocked). Therefore **no Gradle build, lint, JVM test, or instrumented
test was executed in this session**. CI (`.github/workflows/ci.yml`, incl. new
API 26 + 36 emulator job) is the first place the tree will actually compile.
Do NOT call this release-ready.

## Verified here

| Check | Result |
|---|---|
| `gradle-wrapper.jar` SHA-256 | `7d3a4ac4…df296172` (matches the official Gradle 8.14 wrapper JAR hash recorded previously; not re-downloaded — distribution host blocked) |
| `python3 scripts/test-source-static.py` | 7/8 pass; 1 pre-existing failure (committed IBM Plex TTFs vs `test_no_font_binaries_in_project_sources`) |
| `:app:assembleDebug/testDebugUnitTest/lintDebug/assembleRelease` | NOT RUN (no JDK/SDK) |
| Instrumented tests API 26 / 36 | NOT RUN (no emulator) |
| Live media platform matrix | NOT RUN — see `docs/MEDIA_CAPABILITY_MATRIX.md` |

## Implemented this session (source only, unverified by compiler)

Structural PDF (merge/split/organize/extract/rotate/crop/compress/stamp/
annotate/signature/metadata/protect/forms/compare/redact-with-verification)
via existing `PdfBoxBackend` + new handler/UI wiring; image expansions
(EXIF report+strip, watermark, join/stitch, adjust/pad, target-size,
compare); file expansions (batch rename as renamed copies, duplicate
SHA-256 report, AES encrypted ZIP via zip4j, trash = recoverable-outputs
report, no auto-delete); ML Kit OCR → .txt + searchable PDF (coarse text
positioning); WebView webpage→PDF; CameraX manual scan (fixed-quad
perspective, no auto edge detection); drawn signature pad.
Passwords: session handles only; drafts strip secret fields before saving.

Known code risks to check at first compile: PdfBox-Android API surface used
in stamps/redaction, zip4j parameters API, CameraX lifecycle binding cast,
Compose Canvas path handling in the signature pad, recursive `seekAll`
helper suspension. JVM tests exist for the PDF backend
(`PdfStructureCoreTest`) but were not executed here.
