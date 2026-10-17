# Build and dependency status

The approved pins are retained except the plan's AGP update to 8.10.1 and SDK/target/build-tools 36 configuration. The pinned graph has NOT resolved or compiled in this execution environment. This file is not a dependency security audit or complete transitive SBOM.

| Component | Candidate pin |
|---|---|
| Java build baseline | JDK 17 |
| Gradle distribution | 8.14 binary ZIP |
| Android Gradle Plugin | 8.10.1 |
| Kotlin / Compose compiler plugin | 2.0.21 |
| KSP | 2.0.21-1.0.27 |
| Compile / target SDK | 36 |
| Build Tools | 36.0.0 |
| Minimum Android API | 26 |
| WorkManager | 2.10.0 |
| Room | 2.6.1 |
| OkHttp | 4.12.0 |
| Serialization JSON | 1.7.3 |

Other direct pins are in `gradle/libs.versions.toml`. No structural PDF, archive-encryption or vault library has been added/qualified. Platform PdfDocument/PdfRenderer/Bitmap APIs implement only the documented candidate operations.

## Wrapper trust boundary

The original upload had no wrapper JAR and a broken launcher. The JAR remains absent because the runner could not resolve the download host. The replacement project launchers verify the official JAR before executing it; they are custom safety launchers, not copies claimed to be Gradle's generated scripts. They pass arguments without evaluating shell option strings. Use `JAVA_TOOL_OPTIONS` for JVM options.

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

Enable explicitly with `-PextendedMedia=true`. The default base build does not
resolve the new native runtime and reports the media card as unavailable.

| Component | Source-declared version | Observed qualification |
|---|---|---|
| `io.github.junkfood02.youtubedl-android:library` | 0.18.1 | Tag/source reviewed; Maven artifact not resolved here |
| `io.github.junkfood02.youtubedl-android:ffmpeg` | 0.18.1 | Initialization source reviewed; APK/ABI not built here |
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
