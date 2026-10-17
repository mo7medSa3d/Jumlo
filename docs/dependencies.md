# Build and dependency status

Updated 2026-10-17. The native dependency graph was exercised with an isolated full JDK/SDK/Gradle installation. Actual processor failures required the narrow compatibility changes below; final command outcomes are in [RELEASE.md](RELEASE.md). This is a direct-dependency inventory, not a complete transitive security or licensing qualification.

| Component | Source pin |
|---|---|
| Java build baseline | JDK 17 |
| Gradle distribution | 8.14 binary ZIP |
| Android Gradle Plugin | 8.10.1 |
| Kotlin / Compose compiler plugin | 2.0.21 |
| KSP | 2.0.21-1.0.28; KSP2 enabled |
| Compile / target SDK | 36 |
| Build Tools | 36.0.0 |
| Minimum Android API | 26 |
| WorkManager | 2.10.0 |
| Room | 2.7.2 |
| Hilt Android / compiler / Gradle plugin | 2.54 |
| OkHttp | 4.12.0 |
| Serialization JSON | 1.7.3 |

Other direct pins are in `gradle/libs.versions.toml`. CameraX is narrowly updated from 1.3.4 to 1.4.0 after actual APK inspection found 4KB-aligned 64-bit image-processing libraries in the older artifact. The official 1.4.0 AAR has 16KB ELF load alignment; final built-artifact results and device limits are recorded in RELEASE. Existing PDFBox Android, Zip4j, CameraX and ML Kit dependencies remain in the source. Their presence does not enable every internal capability: the public policy independently gates high-risk PDF/ZIP, scanner, OCR, webpage, Drive and media functionality.

Repeated KSP1 output collisions were reproduced. KSP2 then exposed Room 2.6.1's JVM-signature failure, Hilt 2.52's missing KSP2 task setup, and a missing KSP 1.0.27 task argument API required by Hilt 2.54. Room 2.7.2, Hilt 2.54 and matching KSP 1.0.28 address those specific boundaries while retaining Kotlin 2.0.21 and the other major pins. The actual generated Room v2 schema stayed byte-identical. Primary upstream references and class inspection are retained in `verification/production-2026-10-17/build/processor-compatibility-sources.json`; the build report records actual execution, including failed attempts.

## Wrapper trust boundary

The checked wrapper JAR and Gradle distribution were verified during this execution, and the actual wrapper was invoked. Project launchers verify the official JAR before executing it; they are custom safety launchers, not copies claimed to be Gradle's generated scripts. They pass arguments without evaluating shell option strings. The Unix launcher has executable mode 0755. Use `JAVA_TOOL_OPTIONS` for JVM options.

`scripts/restore-wrapper.sh --download` and `scripts/restore-wrapper.ps1 -Download` are explicit network opt-ins. Neither is invoked automatically by verification. A user-provided official binary can instead be supplied with `--from PATH` or `-From PATH`. The resulting JAR must match:

```
7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172
```

The pinned Gradle 8.14 binary distribution checksum is:

```
61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa
```

Official reference: https://gradle.org/release-checksums/
Official wrapper documentation: https://docs.gradle.org/8.14/userguide/gradle_wrapper.html
Official AGP compatibility: https://developer.android.com/build/releases/past-releases/agp-8-10-0-release-notes

No source ZIP can substitute for SDK/compiler/device verification. A checksum is not a passing Android build.

## Long-running work

The candidate uses WorkManager foreground execution with a `dataSync` service declaration and a non-exported cancellation receiver. Notification progress is intentionally indeterminate; actual progress lives in the persisted activity view. Modern OS scheduling quotas and service-start restrictions still apply; this is not a claim of indefinite background execution. Android 16 user-initiated transfer-job qualification and a complete scheduling test matrix remain open.

Official reference: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running

## Optional media build added on October 16, 2026

An internal qualification build can explicitly request `-PextendedMedia=true`.
The default base build excludes that native runtime. The current public
capability policy hides/rejects media in all build modes; the flag alone does
not qualify or expose it.

| Component | Source-declared version | Observed qualification |
|---|---|---|
| `io.github.junkfood02.youtubedl-android:library` | 0.18.1 | Excluded from the standard distribution; optional native qualification open |
| `io.github.junkfood02.youtubedl-android:ffmpeg` | 0.18.1 | Excluded from the standard distribution; optional APK/ABI qualification open |
| Python / yt-dlp / QuickJS / FFmpeg native payloads | Those supplied by the above AARs | Exact packaged binary versions/hashes not inspected; not independently qualified |

Upstream labels 0.18.1 a **pre-release** and the wrapper repository's tagged
LICENSE is GPL-3.0. The native FFmpeg build and transitive components need their
own notices, source availability and distribution review. No dependency binary,
paid subscription or blanket change to the owner's source license is supplied
by this ZIP. The build switch is a scope boundary, not a license waiver.

Primary references checked during this work:
- https://github.com/yausername/youtubedl-android/releases/tag/0.18.1
- https://github.com/yausername/youtubedl-android/blob/0.18.1/LICENSE
- https://github.com/yausername/youtubedl-android/blob/0.18.1/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt
- https://github.com/yausername/youtubedl-android/blob/0.18.1/ffmpeg/src/main/java/com/yausername/ffmpeg/FFmpeg.kt
- https://github.com/yt-dlp/yt-dlp#format-selection
- https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion

The Android adapter deliberately does not call upstream's unbounded-output
`execute` wrapper. It uses the production `MediaProcessRunner` and a trusted
APK-supplied Python launcher, with bounded output, cancellation, process-group
termination and a deadline. It refreshes the extractor from the installed APK
instead of running automatic remote code updates. The urllib transport guard
uses upstream internal interfaces; compatibility with the actual bundled
extractor must be tested before release. No certificate bypass is enabled.

No live platform, authenticated session, ABI, 16-KiB-page device, foreground quota,
Google Play distribution or native code security qualification was performed.


Update 2026-10-17: the `-PextendedMedia=true` flag now only changes the **debug** variant's media runtime/dependencies. `release` and `benchmark` always use the no-media factory. Native Gradle graph verification is pending and required before any claim about built artifact contents. See DEPENDENCY_NOTICES.md.
