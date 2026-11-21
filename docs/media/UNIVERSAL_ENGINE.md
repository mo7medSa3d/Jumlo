# Media extraction and downloads

## Chosen architecture

The default Android build retains **on-device yt-dlp**, a native Python runtime,
FFmpeg/ffprobe, and QuickJS from the pinned junkfood02 youtubedl-android 0.18.1
artifacts. The Android-free selection, URL, transfer, resume, naming, and subprocess
policies live in `downloader-core`. Android adapters implement `MediaRuntime`.
This seam can be replaced without changing the downloader UI or job model.

A backend would remove most Python/FFmpeg bytes from the APK and permit server-side
extractor updates. It would also require a deployed service, bandwidth and operating
costs, availability monitoring, and sending submitted URLs and media through that
service. No such production service was supplied. The repository's optional
`tools/media-service` is a development adapter, not a deployed production dependency.
Keeping the existing local architecture avoids adding that requirement. Actual APK
size remains an artifact gate, not a number inferred from source dependencies.

The full factory tries these adapters in order:

1. `PackagedMediaRuntime`: the APK-pinned extractor and media tools.
2. `RemoteExtractorRuntime`: an optional configured service; a missing configuration
   falls through. The simplified Settings UI does not require service setup.
3. `NativeDirectMediaRuntime`: original direct files and passive HTML5 discovery.

`RoutingMediaRuntime` remembers which adapter supplied the inspected metadata.
Restricted, private, rate-limited or authentication failures stay explicit errors.
It does not silently substitute another quality or invent streams.

## Metadata before selection

Pasting a public HTTP(S) link triggers inspection on an IO dispatcher. Plain documents
and images use the direct file path. Direct video/audio links continue automatically
to media inspection. Video/audio and quality controls appear only after metadata.
Video defaults select an available source format at or below the saved resolution
when possible; audio encoding defaults are applied after inspection.

The generic yt-dlp file extractor may report a container with no track information.
For that case, Jumlo fetches at most a 2 MiB head and 2 MiB tail through cancellable
OkHttp requests. A sparse local sample preserves MP4 seek offsets. A local-only,
time-bounded ffprobe invocation measures actual tracks, resolution, duration and
bitrate. Network protocols and playlist demuxers are excluded from that invocation.
Album artwork is not advertised as a video track. No audio track is inferred merely
from an MP4 filename. If a server does not support ranges or metadata lies outside
the sample, inspection may remain unavailable; the verified direct file can still
be downloaded as its original file without fabricated quality controls.

Source URLs and headers remain transient metadata. Selected job settings contain
opaque URL handles, source identity, exact format IDs and a selection fingerprint.
Workers re-inspect before transfer and reject a changed selection. Signed download
URLs are encrypted in Android no-backup storage for process-death recovery; Room
and WorkManager retain only handles. JobStore releases URL ownership only after a
durable terminal transition. User credentials are not exported to the native engine.

OkHttp cancellation remains active until the response body closes, including a
stalled read. Resume uses validators and byte ranges; a server returning a fresh
200 response resets old validators. Native process cancellation stops its process
group and postprocessors. Partial files remain private until validated publication.

## APK size, integrity and native packaging

The arm64 compact APK has a **60,000,000-byte** verification gate. The release and
emulator ABI configuration is owned by the app build; x86_64 is for debug testing.
Common media assets and ARM64 repairs live in `app/src/media/assets`; the five
x86_64 repairs live in `app/src/mediaDebug/assets`, registered only for full-runtime
debug builds. The artifact inspector rejects repair assets for absent native ABIs,
and the lightweight-artifact inspector rejects the native repair asset directory.
The native shrinker `tools/repack-native-media.py` validates both upstream archive
hashes and resulting archive hashes. It follows ELF32/ELF64 `DT_NEEDED` entries with
virtual-to-file address mapping, including dependencies of retained Python/OpenSSL
modules loaded dynamically. Removed entries are link-time artifacts, unused tool
libraries and CPython test modules. Runtime code, TLS data and licenses stay present.

Retained ZIP compressed records are copied verbatim. Only central-directory offsets
change, so output pins do not depend on a local zlib compression version. Pins and
upstream Maven AAR hashes live in `app/src/media/assets/media/native-overrides/manifest.properties`.
The repaired arm64 payload sizes measured during this work are 32,368,236 bytes for
FFmpeg's archive and 12,472,797 bytes for Python's archive. These are payload sizes,
not the final APK size; the final artifact report owns that measurement.

The five APK-owned WebP replacements retain their individual source/build hashes
and 16 KB ELF alignment checks. A packaging alignment check is not an execution
test on a 16 KB kernel. Native/device behavior remains a separate verification gate.

## Licensing references

The [yt-dlp README](https://github.com/yt-dlp/yt-dlp/blob/master/README.md#license)
states that its source uses the **Unlicense**. It separately describes the Unix
zipimport executable's ISC/MIT components and GPL-3.0 requirements of certain
PyInstaller release bundles. The earlier claim that all yt-dlp source is GPL-3.0
was incorrect. FFmpeg, Python, QuickJS, youtubedl-android, and their bundled libraries
retain their own licenses; a wrapper's license does not replace those notices.
Google Play distribution requirements and provider terms are separate from the
extractor implementation and are not certified by this document.

## Verification boundary

Host and JVM fixtures verify transfer behavior, real sample probing, format
selection, native error containment, and archive integrity. YouTube, TikTok,
Instagram, Facebook, LinkedIn, Google Drive and arbitrary public direct URLs still
require the requested live platform/device matrix. They are **NOT VERIFIED** merely
because an extractor is present. The supplied pasting crash had no device stack
trace; defensive fixes and injected-failure tests do not establish its original
on-device cause. See the project test report for actual build and device outcomes.

`-PextendedMedia=false` retains the explicit lightweight build in `src/base/java`
without Python/FFmpeg payloads. It provides original direct/HTML5 media support,
with capabilities and error messages reflecting that smaller engine.
