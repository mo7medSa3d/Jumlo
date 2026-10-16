# Dependency notices and licensing decision

Base build (no flags) dependency licenses, from `gradle/libs.versions.toml`:

| Dependency | Version | License |
|---|---|---|
| Android Gradle Plugin | 8.10.1 | Apache-2.0 |
| Kotlin / compose plugin / serialization plugin | 2.0.21 | Apache-2.0 |
| KSP | 2.0.21-1.0.27 | Apache-2.0 |
| AndroidX core-ktx, appcompat, lifecycle, activity-compose | see toml | Apache-2.0 |
| Compose BOM 2024.11.00 (ui, graphics, material3, icons, window-size-class) | BOM | Apache-2.0 |
| navigation-compose 2.8.4 | 2.8.4 | Apache-2.0 |
| Hilt (android, compiler) 2.52; hilt-navigation-compose / hilt-work 1.2.0 | see toml | Apache-2.0 |
| Room 2.6.1; DataStore preferences 1.1.1; WorkManager 2.10.0 | see toml | Apache-2.0 |
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

Decision for the release train (needs owner sign-off):

- Base release builds MUST NOT include this payload (current state: they do
  not; the flag defaults off and `dl_media` is disabled without it).
- No `-PextendedMedia=true` build ships to any channel until the owner
  records one of: (a) accept GPL-3.0 for the whole app and publish
  corresponding source + notices, (b) replace the payload with a
  permissively-licensed implementation, or (c) keep the feature
  debug/qualification-only permanently.

No cookie export and no DRM/encryption bypass is implemented, and none may
be added as part of resolving the above.
