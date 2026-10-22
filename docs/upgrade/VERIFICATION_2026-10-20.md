# Verification report — Jumlo, 2026-10-20 (session 2)

Everything below was **executed** in this environment unless a line says *not executed*.
Raw logs live next to this file in `verification/2026-10-20-session2/`.

## Environment

| Item | Value |
| --- | --- |
| JDK | Temurin 17 (the toolchain requires 17; the system JDK 25 cannot be parsed by Kotlin 2.0.21) |
| Android SDK | platform 36, build-tools 36.0.0, platform-tools (installed this session) |
| Gradle | 8.14 (wrapper distribution already cached) |
| AGP / Kotlin | 8.10.1 / 2.0.21 |
| Device / emulator | **none** — no physical device attached, and the emulator system image was not downloaded (bandwidth) |

## Executed results

| Gate | Command | Result |
| --- | --- | --- |
| Host core + static suite | `bash scripts/verify-host.sh` | **PASS** — 181 Kotlin core checks, plus the Python/static/shell suites (media bootstrap, network guard, universal-media architecture, downloads density, storage SQL, source static, redesign static, premium UX, neon UI, visual editor, watermark studio, smart-scan wiring, local profile, output workflow, flagship reliability, large PDF, brand motion, phase 1–4 regressions, launcher, verify-local). Exit 0. |
| Android unit tests | `./gradlew :app:testDebugUnitTest` | **PASS** — 235 tests, 0 failures, 0 errors. |
| Lint | `./gradlew :app:lintDebug` | **PASS** — 0 errors, 395 warnings (mostly `UnusedResources` 232, `UseKtx` 60, `GradleDependency` 27). |
| Release build (R8) | `./gradlew :app:assembleRelease` | **PASS** — `app-release-unsigned.apk`, **10,614,149 bytes** (10.1 MB), far below the 60 MB APK policy. `lintVitalRelease` passed. |
| Debug build | `./gradlew :app:assembleDebug` | **PASS** — `app-debug.apk`, see `verification/2026-10-20-session2/apk-sha256.txt`. |
| Release lint vital | included in the release build | **PASS** |

### Regression found and fixed by the project's own gate

`scripts/test-universal-media-architecture.py` failed before this session: the CI workflow had
lost the opt-in extended-media job that its contract test requires
(`inputs.extended_media`, `-PextendedMedia=true`, `Jumlo-Extended-Media-Debug-APK-*`).
The workflow input and the separate experimental artifact job were restored, and the host
suite now passes end to end.

## Behaviour changes (deliberate, with new tests)

| Change | Why | Test |
| --- | --- | --- |
| A server that answers a ranged request with `200` restarts the transfer from byte 0 instead of failing the job and parking it in `WAITING_FOR_INPUT`. | A download could not finish until the user retried it by hand. The partial bytes are discarded, never appended, so the file cannot be corrupted. | `TransferTests.kt` — "real 200 response to Range restarts from zero instead of appending"; `StreamTransferResumeTest.kt`. |
| A job whose items were all *unchanged* is no longer reported as "completed" with no output. | The user saw a success with nothing produced. The row now shows the localized reason. | `JobStore` outcome accounting + `ProductionRows` reason line. |

## New tests added this session

| Test | Covers |
| --- | --- |
| `PdfStructureCoreTest.split_preserves_form_bookmarks_and_document_information` | split keeps AcroForm fields, bookmarks and document info |
| `PdfStructureCoreTest.extract_keeps_the_form_widget_on_the_retained_page` | extracted page keeps its form widget |
| `PdfStructureCoreTest.organize_with_a_duplicated_page_keeps_a_valid_document` | duplicated page produces a valid document with independent rotations |
| `TransferAndNamingPolicyTest` (13 checks) | typed file names with spaces/non-ASCII survive; complete-transfer detection; percentage resize above 100 %; margin larger than the page is clamped; page range past the last page is clamped; batch URL status detection; output-count budget |
| `StreamTransferResumeTest` (4 checks) | ranged request answered with `200`; resume validators still required |
| `PdfFixtures.catalogDoc` | new fixture with a form field, a bookmark and document information |

## Not executed (no device or emulator available)

These are **unverified on hardware** and must not be reported as passing:

- `connectedDebugAndroidTest` — no emulator image and no physical device.
- Camera capture, ML Kit text recognition, real perspective-correction pixels.
- Physical printing (spooler, paper size, duplex, page count on paper).
- TalkBack / Switch Access focus order, large-text layout, RTL rendering on a device.
- Frame pacing, cold start and memory measurements.
- Screenshots of the running app — **no screenshot could be produced**, because rendering the
  app requires a device.
- Live media providers, Google Drive account import/export, OAuth.

The Android SDK and the offline `kotlinc` shim were installed this session so that the
builds, the JVM tests and the host suite could actually run instead of being asserted from
source reading.
