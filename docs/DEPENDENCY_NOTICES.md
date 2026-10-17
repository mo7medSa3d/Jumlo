# Dependency notices and licensing decision

> **Current hardened ZIP follow-up:** The SHA-256 allowlist for the two
> bundled IBM Plex fonts passed the static check. The optional yt-dlp native
> payload is never updated from an unpinned network source by the source
> `refreshExtractor` action; it restores the copy packaged in the APK.
> Its embedded executable version/hash was **not inspected on a built APK**.
> Host `/usr/bin/ffmpeg` synthetic 5/5 does **not** prove Android FFmpeg
> `-buildconf`, binary license, codecs or redistribution obligations.
> No media service was live-tested. Debug-only dependency/source isolation
> passed 5/5 source assertions, **not** Gradle resolved-graph or AAB inspection.
> Do not publish optional media without owner approval of all GPL/LGPL,
> font attribution, artifact and platform legal/technical gates.

Updated 2026-10-17. Source-declared direct dependency notice inventory for the
base build, reconciled with `gradle/libs.versions.toml`. Exact transitive and
distribution obligations still require review of the shipped artifacts; this
table is not a complete SBOM or a blanket license approval.

| Dependency | Version | License |
|---|---|---|
| Android Gradle Plugin | 8.10.1 | Apache-2.0 |
| Kotlin / compose plugin / serialization plugin | 2.0.21 | Apache-2.0 |
| KSP | 2.0.21-1.0.28 | Apache-2.0 |
| AndroidX core-ktx, appcompat, lifecycle, activity-compose | see toml | Apache-2.0 |
| Compose BOM 2024.11.00 (ui, graphics, material3, icons, window-size-class) | BOM | Apache-2.0 |
| navigation-compose 2.8.4 | 2.8.4 | Apache-2.0 |
| Hilt (android, compiler) 2.54; hilt-navigation-compose / hilt-work 1.2.0 | see toml | Apache-2.0 |
| Room 2.7.2; DataStore preferences 1.1.1; WorkManager 2.10.0 | see toml | Apache-2.0 |
| OkHttp 4.12.0 | 4.12.0 | Apache-2.0 |
| Coil-compose 2.7.0 | 2.7.0 | Apache-2.0 |
| kotlinx-coroutines / serialization-json | 1.9.0 / 1.7.3 | Apache-2.0 |
| exifinterface, documentfile, window | see toml | Apache-2.0 |
| JUnit 4.13.2 (tests only) | 4.13.2 | EPL-1.0 |
| androidx.test junit / espresso (instrumented tests only) | see toml | Apache-2.0 |
| IBM Plex Sans + IBM Plex Sans Arabic (`res/font/`) | bundled | SIL OFL-1.1 |
| Gradle 8.14 wrapper / distribution | 8.14 | Apache-2.0 |

## Extended-media payload — DECISION REQUIRED, do not ship

`-PextendedMedia=true` adds `io.github.junkfood02.youtubedl-android`
`library` + `ffmpeg` `0.18.1`, whose wrapper is GPL-3.0 and whose upstream
release is labeled prerelease. The packaged payload further contains yt-dlp,
Python, QuickJS and FFmpeg binaries with their own licenses/obligations,
which were not independently qualified here.

The standard release must exclude this payload; the flag defaults off and
the current public policy hides/rejects `dl_media` even in an extended build.
Actual APK/AAB and dependency inspection outcomes are recorded in RELEASE.
No extended build is qualified for distribution. Before any future change,
the owner must review the exact wrapper/native build flags, combined-work
licenses, source availability and notices, and record the distribution
decision. Replacing a wrapper or setting a feature flag alone does not resolve
the licenses of the native payload it carries.

No cookie export and no DRM/encryption bypass is implemented, and none may
be added as part of resolving the above.

## Existing processing dependencies (base build)

| Dependency | Version | License |
|---|---|---|
| pdfbox-android (`com.tom-roush:pdfbox-android`) | 2.0.27.0 | Apache-2.0 |
| zip4j (`net.lingala.zip4j:zip4j`) | 2.11.5 | Apache-2.0 |
| CameraX (`camera-lifecycle/camera2/view`) | 1.4.0 | Apache-2.0 |
| ML Kit text-recognition (`com.google.mlkit:text-recognition`) | 16.0.1 | Apache-2.0 / ML Kit ToS; bundled Latin model, on-device |

Fonts decision (recorded): the two IBM Plex TTFs (SIL OFL-1.1) are bundled
deliberately for the approved native typography and pinned by SHA-256
(`ibm_plex_sans_variable.ttf` 3b031aa4…, `ibm_plex_sans_arabic_regular.ttf`
6f611412…). `scripts/test-source-static.py` now allows exactly these two files
by hash and rejects any other font binary. Font license files remain included;
no runtime font download is required.

Media payload licensing (Phase 2 research, owner sign-off still required):
yt-dlp itself is Unlicense/public-domain. The Android wrapper used by the
optional `-PextendedMedia=true` build (`io.github.junkfood02.youtubedl-android`
0.18.1) is GPL-3.0 and bundles FFmpeg; FFmpeg builds are LGPL-2.1+ or GPL
depending on enabled encoders, and this wrapper's FFmpeg must be treated as
**GPL** until its exact build flags are audited. Source/notices and any
combined-work obligations must be assessed against those exact artifacts.
Current public availability stays OFF in all build modes.


## Current follow-up: media packaging and license limitations (2026-10-17)

The current changed `app/build.gradle.kts` restricts the optional `youtubedl-android` library/FFmpeg 0.18.1 artifacts to **`debugImplementation`** and takes its `src/media/java` factory only for explicitly opted-in debug builds. Both release and benchmark variants use the `src/base/java` no-media factory and `BuildConfig.EXTENDED_MEDIA=false`; the default base graph remains media-free. The new source test `scripts/test-release-media-isolation.py` passed 3/3. A Gradle `releaseRuntimeClasspath` inspection with both media flag values was added to `scripts/verify-native.sh gate` and **has not been run** in this session. Actual current APK/AAB contents therefore remain UNVERIFIED.

External license documentation consulted (not substitutes for binary inspection):

- FFmpeg licensing and distribution checklist: https://www.ffmpeg.org/legal.html — LGPL-2.1-or-later default; enabling GPL components changes the binary's obligations. Concrete proof requires actual bundled FFmpeg build configuration, source/notice and hashes.
- Maven Central metadata for the pinned `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1` **declares GPL-3.0**, as do the corresponding wrapper library and common artifacts: https://central.sonatype.com/artifact/io.github.junkfood02.youtubedl-android/ffmpeg/0.18.1 and https://central.sonatype.com/artifact/io.github.junkfood02.youtubedl-android/library/0.18.1 . This is a package-level license declaration, not proof of the FFmpeg native build flags.
- yt-dlp upstream licensing: https://github.com/yt-dlp/yt-dlp#licensing — its source is Unlicense, but some bundled executables include independently licensed parts and may be GPLv3+. The pinned Android AAR's embedded yt-dlp and third-party artifacts still need a binary-level SBOM/provenance review.

**Owner decision remains open:** do not distribute the extended media runtime, enable `dl_media`, or label any service supported until the exact binary's GPL/LGPL obligations, third-party sources, required notices, release artifact graph, current security gates and live platform/device tests have been approved. The 5/5 local FFmpeg fixture pass applies only to host `/usr/bin/ffmpeg`, NOT to the Android AAR. IBM Plex remains allowlisted exclusively via the two exact hashes in `scripts/test-source-static.py` (check reproduced in this session); SIL OFL obligations and attribution still apply.
