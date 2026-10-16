# Media device and platform qualification - NOT EXECUTED

This is an execution checklist, not passing evidence. Use owned/public test
assets; keep signed URLs, login cookies and full error output out of artifacts.

## Build

Use JDK 17, Android SDK 36 / Build Tools 36.0.0 and the pinned Gradle 8.14.
Restore the official wrapper only with the supplied checksum-verifying script.

```sh
bash scripts/restore-wrapper.sh --download
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew -PextendedMedia=true :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew -PextendedMedia=true :app:connectedDebugAndroidTest
```

For an already provisioned, network-isolated machine use `scripts/verify-local.sh`
with the same Gradle arguments; it refuses to bootstrap a missing toolchain.

## Native behavior

On API 26, 29, 33, 35 and 36, covering supported ABIs and a 16-KiB-page device:

1. Confirm native Python, QuickJS and FFmpeg/ffprobe initialize from the APK.
   Record artifact SHA-256 and actual runtime versions; do not assume them from
   the Maven coordinate. Confirm an APK update refreshes its extractor.
2. Analyze a finite public resource. Confirm each displayed format/height/FPS,
   audio codec, language and exact/estimated size comes from actual metadata.
3. Choose a separate 1080p video/audio pair. Verify resulting dimensions and both
   tracks; choose a different available resolution and verify again. Do not label
   a site supported just because its page can be opened in WebView.
4. Export source-codec audio, MP3, M4A and Opus. Probe the actual output and listen
   to it; ensure no video track survives audio-only extraction.
5. Cancel analysis while silent, cancel download, and cancel during FFmpeg. Check
   both parent and children exit, no result is published, and temporary storage
   is removed. Repeat with process death and foreground-service timeout.
6. Disconnect, change Wi-Fi/unmetered conditions, exhaust free space, expire a
   signed URL, change a format behind the same ID and inject an HTTP downgrade.
   Verify explicit error state and no false success or automatic quality fallback.
7. Open the optional browser, play media, inspect detected candidates, and send
   an ordinary file to direct downloads. Confirm no JavaScript-to-native bridge,
   no local-file access and no download before user action. Browser cookies are
   not exported: authenticated extraction is NOT a supplied capability.
8. Repeatedly tap Download; simulate database persistence followed by a scheduling
   error. Reuse the same prepared request on retry. Use the explicit additional
   copy action only when a second output is intended.
9. Share/save the actual result via the existing FileProvider/SAF flow. Rotate,
   background and recreate the activity with Arabic RTL, large fonts and TalkBack.

## Live platform record

Maintain one row **per tested platform + resource class + extractor version**:

| Platform/resource | Original page | Probe | Video | Audio-only | Browser discovery | Date/runtime | Result |
|---|---|---|---|---|---|---|---|
| No platform tested in this runner | Not executed | Not executed | Not executed | Not executed | Not executed | N/A | UNVERIFIED |

Generic direct files, non-live HLS and non-live DASH are source paths, not proof
that all their real-world variants work. Session-required pages, impersonation-
only sites, playlists, live recording, protected media and arbitrary blob URLs
are not universally supported. Never turn this checklist into a PASS by inspection.
