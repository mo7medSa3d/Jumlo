# Universal media engine (bundled yt-dlp + FFmpeg runtime)

Jumlo's media runtime ships a self-contained yt-dlp extraction stack inside
the APK: a native Python 3 interpreter, FFmpeg/ffprobe, and the yt-dlp
extractor payload, with a native tiny QuickJS fallback (`libqjs`) for the
routing layer. The Java/Kotlin integration is derived from the
`youtubedl-android` library family (junkfood02 0.18.1, Apache-2.0).

## Licenses and distribution

- yt-dlp is released under **GPL-3.0** (Unlicense wrappers aside, the
  extractor engine is GPL). Shipping its payload inside an APK is a GPL
  distribution act; keep the license text with the payload and link this
  document from the app's about screen.
- FFmpeg inside the runtime keeps its own LGPL/GPL component set generated
  by the upstream Termux build; the on-device use stays linked behind the
  unpacked `usr/` tree of `packages/ffmpeg`.
- Google **Play** policy: an app that downloads videos from third-party
  platforms must not copy-protect or claim rights it does not have; the
  downloader must respect the platform terms per site. Jumlo discloses
  supported sites dynamically and never bundles credentials; **YouTube**
  is among the sites yt-dlp supports, but Jumlo makes no guarantee of any
  particular site's continued compatibility and does not bypass DRMs.

## Engine selection

`MediaRuntimeFactory` wires, in order:

1. `PackagedMediaRuntime` — the bundled yt-dlp/FFmpeg runtime restored from
   sha-pinned payloads (`refreshBundledPayload()`), private per-app
   workspace only (no absolute paths, no shell interpolation).
2. `NativeDirectMediaRuntime(client, sessions)` — the direct in-process
   engine (metadata/filters/stream transfer) for formats the bundled stack
   cannot or should not touch.
3. `RoutingMediaRuntime(...)` — for unsupported sites it reports
   `MEDIA_SITE_UNSUPPORTED` instead of guessing.

## APK size and 16 KB page alignment

The full media APK is bounded by `:app:verifyCompactApkSize` at 60,000,000
bytes for the arm64 **compact** build. The payload zips are deterministic
closure-verified repacks (`tools/repack-native-media.py`): only libraries
reachable from the shipped executables' dynamic-link graph remain
(`REPACKED_OK` lines in the build log; pins live in
`app/src/media/assets/media/native-overrides/manifest.properties`).

Android's **16 KB** page-size program-header alignment for ELF64 libraries
is a packaging property only — this tree validates PT_LOAD alignment with
`scripts/inspect-media-artifact.py` and `scripts/inspect-standard-artifact.py`.
On-device behavior across 16 KB page kernels is **NOT VERIFIED** here
(no emulator on the verification host): they remain separate checks that a
device test run must claim.

## Lite build

`-PextendedMedia=false` builds the lite APK without any yt-dlp/FFmpeg
payload (`src/base/java` runtime, standard artifact inspected by
`scripts/inspect-standard-artifact.py`, which rejects media payloads). The
default build is the full media runtime (`extendedMedia=true`).
