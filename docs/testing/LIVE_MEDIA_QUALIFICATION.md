# Live downloader qualification

This handoff runs the remaining platform matrix without converting host results
into Android passes. No live platform or device pass is asserted by the template.
The existing app and instrumentation interfaces are used unchanged.

## Prepare local inputs and evidence

Run from the repository root with Python 3.10+ on Linux/macOS. The runner uses only
the Python standard library. It neither installs tools nor invokes Gradle or ADB.

```bash
python3 scripts/tests/test-live-media-qualification.py
python3 scripts/qualify-live-media.py init --out /tmp/jumlo-live/inputs.local.json
```

Edit the local JSON. It contains all seven requested platforms: YouTube, TikTok,
Instagram, Facebook, LinkedIn, Google Drive, and direct public HTTP(S) links.
Supply short, public samples you may test without credentials. Choose representative
media with both video and audio, and multiple actual formats where the provider
offers them. A direct original file may legitimately offer only one source quality.
The runner rejects credentials in URLs and lexical local/private destinations;
this is not a complete DNS-rebinding sandbox.

| Input field | Purpose |
|---|---|
| `video_url` | Video, source-quality and offline cases. Use a finite item, not a playlist or live broadcast. |
| `audio_url` | Optional separate audio sample. The Android audio row falls back to `video_url` when empty. |
| `file_url` | Public non-media document/file for Google Drive and direct-file cases. |
| `invalid_url` | A deliberately missing or deleted public item on the same provider; do not substitute a private item and call an authentication failure a missing-link test. |

Blank URLs stay unexecuted. The runner does not discover accounts, import browser
cookies, send custom credentials, or substitute random social posts. Keep the
filled manifest outside the repository and share redacted evidence when possible.
The checked-in `live-media-inputs.example.json` has the same empty structure.

Targets default to an Android 9/API 28 emulator, an Android 14+/API 34 emulator,
and a physical device. Fill the actual API, ABI, device label and APK SHA-256 before
recording results; the API values in the template are desired targets, not observed
hardware. Use the same APK build where the device ABI permits it. Release is arm64;
emulator x86_64 testing uses the debug build.

```bash
python3 scripts/qualify-live-media.py plan \
  --manifest /tmp/jumlo-live/inputs.local.json \
  --out /tmp/jumlo-live/android-matrix.csv
```

This creates **138 rows**: 35 required platform/case combinations, two additional
document cases, seven workflow cases, and two existing instrumentation gates per
target. Every row begins `NOT_RUN`. `input_ready` describes URL configuration only;
it does not imply a ready device or a successful operation. Reports identify inputs
by SHA-256 without copying their URLs. The CSV template is also checked in as
`live-media-matrix.template.csv`.

## Optional bounded host metadata probes

```bash
python3 scripts/qualify-live-media.py probe \
  --manifest /tmp/jumlo-live/inputs.local.json \
  --platform youtube --platform direct \
  --field video_url --timeout 40 \
  --out /tmp/jumlo-live/host-metadata.json
```

The runner verifies the bundled archive against `extractor.properties`, invokes
the APK-owned bootstrap and its network guard, and requests simulation/metadata
only. It ignores user configuration, disables plugins, uses no cookies and downloads
no media output, subtitles or thumbnails. Requests have a 10-second socket timeout,
no extractor retries, a 5–60 second total deadline per input, a 4 MiB stdout cap and
a 64 KiB diagnostic cap. It stops the child process group on timeout/overflow.
Metadata requests may fetch provider pages and manifests; the limits bound process
time and captured output, not every remote response body's size.

The result records the extractor hash/version, host Python, presence of host JS
tools, whether a proxy environment exists, exit status and sanitized diagnostics.
It never exports signed stream URLs, titles, cookies or full input URLs. A default
host JS engine may differ from Android's packaged QuickJS. Host ffprobe enrichment,
app format selection, WorkManager, Android storage and UI are not exercised here.

Outcome names are deliberately scoped: `HOST_METADATA_RETURNED`,
`HOST_EXTRACTOR_ERROR`, `HOST_METADATA_INVALID`, `HOST_TIMEOUT`,
`HOST_OUTPUT_LIMIT`, or `NOT_RUN_MISSING_URL`. Exit status is zero only when every
requested probe returns parseable metadata. Metadata containing unknown tracks
remains unknown; a generic extractor response is not proof that video or audio
choices work. Host results never update the Android matrix.

## Existing Android instrumentation gates

Build/install the app and its matching androidTest APK using the normal integration
procedure. The commands below assume the debug application ID `com.jumlo.app.debug`
and test package `com.jumlo.app.debug.test`. Set the serial explicitly. Save complete
instrumentation output and check its test counts/failures; a successful ADB shell
exit alone is not a passing test suite.

```bash
export ANDROID_SERIAL=emulator-5554
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class com.jumlo.app.media.PackagedMediaRuntimeDeviceTest \
  -e mediaProcessTimeoutMillis 300000 \
  com.jumlo.app.debug.test/androidx.test.runner.AndroidJUnitRunner

adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class com.jumlo.app.ui.JumloUiTest,com.jumlo.app.ui.FiveTabNavigationTest \
  com.jumlo.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The native fixture runs packaged Python, the pinned extractor, QuickJS, FFmpeg,
ffprobe and Android track inspection without a live provider. Its optional process
timeout accommodates software emulators. It **does not qualify live downloads**.
The existing UI suite covers empty download entry, malformed text, tabs and
recreation. It **does not automate all live platform cases**. Do not mark matrix
rows passed from a class that was skipped on a lightweight build or never started.

## Open each live case through the app's existing share interface

This command only prints a safely quoted ADB command. Review and execute the
printed command on the selected test device. Its output includes the local input
URL, so do not append it to a public report containing sensitive links.

```bash
python3 scripts/qualify-live-media.py intent \
  --manifest /tmp/jumlo-live/inputs.local.json \
  --platform youtube --field video_url --serial emulator-5554
```

The generated command uses `ACTION_SEND`, MIME `text/plain`, `EXTRA_TEXT`, and
`com.jumlo.app.MainActivity`. Use `--package com.jumlo.app` for the release app.
This is a launch helper, not an instrumentation assertion and not a clipboard test.
Also exercise the actual Paste button and OS paste action. Paste on both a fresh
workspace and a warm workspace, because the reported crash concerned pasting.

### Required assertions for each provider

| Case | Procedure and evidence required for PASS |
|---|---|
| Video | Paste/share the valid URL. No picker appears before metadata. Record detected provider, title and actual selected format/quality. Download to completion, open the saved file and inspect real video/audio tracks and duration. A completed notification alone is insufficient. |
| Audio | Choose Audio only after metadata. Exercise source/original and supported MP3/M4A/Opus outputs as offered by the full build; record actual format and bitrate selections. Inspect the resulting file: an audio track is present, a video track is absent, and playback works. Use separate evidence per output tested. |
| Quality | Record the formats offered by this item at test time. Select two distinct available qualities if present and inspect output dimensions/track identity. Verify the saved default chooses an available quality and never creates a fictional option. A single-quality direct source must show exactly its real option; document that limitation. |
| Invalid | Use the configured missing/deleted link and also paste malformed text. The app remains open, shows an actionable failure or disabled action, does not enqueue a fictitious success and does not publish HTML as media. Preserve the actual provider/error outcome. |
| Offline | With a valid configured link, disable all device networking before inspection: app stays open and reports a recoverable state. Then inspect online, start a download and interrupt connectivity; restore it and verify retry/resume to one valid output without duplicates. Restore the original network state afterward. |

For Drive/direct `file` rows, download a real PDF/document/archive as original bytes,
record the expected content type and checksum, reopen the file, and confirm a login
or permission HTML page was not published as a document. Add a separate Drive
permission-denied sample when available; do not confuse it with an unsupported URL.

### Workflow rows

Each workflow row requires **both** the packaged media route and a generic direct
transfer. The template uses YouTube and direct inputs as references; if a different
public provider is usable for the media route, record its input digest and provider
in the evidence. A provider block is `BLOCKED`, not a workflow pass.

| Row | Required observation |
|---|---|
| `paste_recreate` | Actual clipboard paste, IME/OS paste, repeated paste, tab return and Activity recreation keep the app alive and preserve completed metadata/selected quality/name for the same session. A newly opened tool is empty. |
| `pause_resume` | Pause during active bytes and during a slow response, observe the paused state, then resume to a valid single output. Record whether the server supported ranges or required restart. |
| `cancel` | Cancel during transfer and finalization. No later completed job/usable partial output appears; the UI remains usable. Verify the next independent job can start. |
| `background` | Start a sufficiently long sample, press Home and lock/unlock. Foreground notification and progress remain coherent and the completed file opens. Capture any foreground-service exception from logcat. |
| `process_death` | Exercise OS-style app process death while a queued/paused job can be recovered; relaunch and verify persisted URL ownership/retry without reentry. Record the exact method. Force-stop has different OS scheduling semantics and is not a substitute for ordinary process death. |
| `duplicate_names` | Download the same sanitized title twice and a title containing punctuation/non-Latin text. Both valid outputs exist with distinct names and correct extensions; no previous file is overwritten. |
| `save_folder` | Exercise default MediaStore behavior on API 29+, legacy behavior on API 28, and a user-chosen SAF tree. Reopen the app and verify the persisted location and actual file visibility. |

Repeat visual observations in English/LTR and Arabic/RTL, with light/dark themes.
Record cases that were not exercised; an aggregate workflow PASS requires all of
its stated steps on that target.

## Recording and accepting results

Edit a copy of the generated CSV. Allowed status vocabulary is `NOT_RUN`, `PASS`,
`FAIL`, `BLOCKED`, or `NOT_APPLICABLE`; record a reason for the last two. Include UTC,
tester, real device/build identity, selected formats, inspected output tracks,
output hash and evidence filenames. Keep logs/screenshots/track-probe output beside
the CSV. For a crash, retain the full exception stack and the action immediately
before it. Redact URLs/tokens before distributing logcat.

Do not label an unsupported source, provider challenge, authentication requirement,
missing device, skipped test or timeout PASS. When metadata fails, retain its exact
network/provider outcome and retry later with a currently accessible public sample.
No host result is automatically promoted to device or physical-device success.

### Primary sample references

Public sample inputs should be verified when chosen. Two useful primary references
for small metadata-only smoke probes are the [W3C video example](https://www.w3.org/html/wiki/Elements/video),
which lists the Sintel trailer on `media.w3.org`, and [NASA's SLS highlights](https://www.nasa.gov/wp-content/uploads/2023/06/sls-4507-sls-highlights-oct-mar-2023-508-web.pdf),
which links the short YouTube propellant-loading video `bTvCr6K_GCI`. Availability
still needs to be measured. Older yt-dlp examples using `BaW_jenozKc` were reported
unavailable in [the upstream issue](https://github.com/yt-dlp/yt-dlp/issues/12263);
the runner deliberately does not hardcode such a sample as a passing prerequisite.
