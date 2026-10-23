# WebP native repair: ABI baseline and reproduction

The application carries five WebP replacement libraries for each supported 64-bit native runtime. They repair the known layout of the WebP libraries inside the pinned FFmpeg dependency. Android initialization extracts the dependency archive, verifies the APK-owned replacements, and installs them before starting Python/FFmpeg child processes. See [BundledFfmpegLibraries.kt](../../app/src/media/java/com/jumlo/app/data/media/BundledFfmpegLibraries.kt).

The WebP provenance JSON files below restore missing maintainer metadata from **reading the pinned upstream AAR and existing checked-in binary assets**. That earlier metadata-restoration operation rebuilt/executed no native binary and changed no WebP replacement bytes or pins. Its recorded manifest hash is a historical snapshot. A later compatibility-library correction changed the eight repacked Python/FFmpeg archive pins, and a second closure correction then changed only the four FFmpeg pins; see the final sections below. ELF layout and ABI inspection do not establish execution on a 16 KiB-page Android kernel.

## Checked inputs and derived files

The source AAR is `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`. The inspected cached file matches `ffmpeg.aar.sha256` in the shared [manifest](../../app/src/media/assets/media/native-overrides/manifest.properties):

```text
0a87ffa6cf912b0fe76c1a99b9107f543ee2f247935fae2c71f0822eb7bc5f49
```

The original, unmodified nested FFmpeg payloads also match their `upstreamArchive.sha256` pins:

| AAR member | Bytes | SHA-256 |
| --- | ---: | --- |
| `jni/arm64-v8a/libffmpeg.zip.so` | 35,624,931 | `4664a895283f9f31786be39b0c484c1f96631f02c7c7f9c70a4a4a2da324a092` |
| `jni/x86_64/libffmpeg.zip.so` | 38,595,503 | `79a442979bfc0363fcdd7e47f75daf022891e84fc01f3fdd866af785d22321ef` |

These are upstream archives, not the smaller repacked archive sizes used by the ordinary Gradle build. The repacked archives have separate `ffmpegArchive.sha256` pins.

| File | Purpose and provenance |
| --- | --- |
| [libwebp-original-abi.json](native/libwebp-original-abi.json) | Required input for `rebuild-media-webp.py`: original SONAME, dependency names and exported symbols, derived from the five `usr/lib/<name>` ELF files inside each pinned upstream archive |
| [libwebp-verification.json](native/libwebp-verification.json) | Inspection of existing replacement assets against that baseline, including hash, export counts and 16 KB LOAD/RELRO checks; this is not a compiler build log |
| [libwebp-abi-provenance.json](native/libwebp-abi-provenance.json) | AAR and nested-archive hashes, each original/repair file hash, tool version, extraction rule and hashes of the two derived data files |

### Baseline schema

The JSON is keyed by ABI, then by exact library filename. Each library contains three fields:

```json
{
  "arm64-v8a": {
    "libsharpyuv.so": {
      "soname": "libsharpyuv.so",
      "needed": ["libm.so", "libc.so"],
      "exports": ["<sorted dynamic symbol names>"]
    }
  },
  "x86_64": {}
}
```

The abbreviated example describes the schema; the checked-in file contains both complete ABIs and all ten library records. `soname` is the single `DT_SONAME` string. `needed` contains the `DT_NEEDED` names. `exports` contains defined GLOBAL or WEAK dynamic symbols with DEFAULT or PROTECTED visibility, strips any version suffix after `@`, removes duplicates and sorts the names. This is the same predicate used by [rebuild-media-webp.py](../../scripts/rebuild-media-webp.py).

The source can be independently checked with GNU or LLVM `readelf --dynamic` and `readelf --dyn-syms --wide` on the nested ELF files. Compare AAR/archive hashes before extracting; do not derive the baseline from the replacement assets themselves.

## Existing replacement verification

Both ABIs have identical symbol counts and dependency contracts:

| Library | Original exports | Replacement exports | `DT_NEEDED` |
| --- | ---: | ---: | --- |
| `libsharpyuv.so` | 7 | 7 | `libm.so`, `libc.so` |
| `libwebp.so` | 86 | 86 | `libsharpyuv.so`, `libm.so`, `libc.so` |
| `libwebpdecoder.so` | 46 | 46 | `libc.so` |
| `libwebpdemux.so` | 20 | 20 | `libwebp.so`, `libc.so` |
| `libwebpmux.so` | 27 | 27 | `libwebp.so`, `libc.so` |

For all ten files, the replacement SHA-256 matches the existing manifest pin; SONAME and dependency sets match the original; export sets are identical; every LOAD alignment is 16,384 bytes; and the existing inspector accepts LOAD bounds/congruence and RELRO separation. The verification JSON records these checks per file. The native assets and manifest were hash-checked before and after that metadata-generation operation and remained unchanged at that time. The recorded provenance manifest hash therefore identifies the earlier inspection snapshot, not the later compatibility-repaired manifest. The ten WebP replacement hashes remain unchanged.

## Rebuilding the repair

The pinned source/build declarations are:

- libwebp 1.6.0, source archive `libwebp-1.6.0.tar.gz`.
- Archive SHA-256: `e4ab7009bf0629fd11982d4c2aa83964cf244cffba7347ecd39019a9e38c4564`.
- Source URL: `https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.6.0.tar.gz`.
- Android NDK r28c, revision `28.2.13676358`; SDK CMake `3.22.1`; Android API 24; Linux x86_64 build host.
- Python 3.12 for the maintainer script. Ordinary Gradle builds use the checked-in replacements and do not run this native rebuild.

The script's default mode creates a fresh build in its work directory, checks the original ABI contract and ELF layout, and writes a verification report there. It does not replace repository assets. An example using an already obtained source archive is:

```sh
python3 scripts/rebuild-media-webp.py \
  --ndk /path/to/android-ndk-r28c \
  --cmake /path/to/cmake/3.22.1/bin/cmake \
  --source-archive /path/to/libwebp-1.6.0.tar.gz \
  --work-dir /tmp/jumlo-webp-rebuild
```

Add `--check-pins` to require byte identity with the existing replacement hashes. `--write-assets` is a separate explicit maintainer operation that copies a fully verified build and updates pins, license and verification metadata; it was not used to restore this documentation. Review a pin-changing rebuild as a new native artifact and repeat APK and device qualification.

### Byte reproducibility boundary

The supplied source previously referred to a missing document for historical canonical toolchain/work paths. Those original paths and the compiler build logs are not present in the supplied repository. They are not reconstructed here. `/tmp/jumlo-webp-rebuild` is the current script default, not proven historical build provenance. The source/build declarations above and restored ABI baseline support a new verified rebuild; they do not by themselves prove that a new build reproduces the checked-in GNU build IDs or every binary byte. A successful actual `--check-pins` run is required for that claim.

## Packaging boundaries

The runtime asset names remain `media/native-overrides/<abi>/<library>`. The shared manifest, WebP license and ARM64 repairs live under `app/src/media/assets/media/native-overrides/`. The five x86_64 repairs live under `app/src/mediaDebug/assets/media/native-overrides/x86_64/`; [app/build.gradle.kts](../../app/build.gradle.kts) registers that asset directory only for the full-runtime debug build. ARM64 release/compact/benchmark and lightweight builds exclude it. The installer reads only the ABI of the actual FFmpeg executable.

The maintainer script writes each ABI to its matching source directory, and the existing source-asset unit test reads both roots. This directory split changes no native bytes, shared pins, runtime lookup names or archive hashes. The artifact inspector rejects repair assets whose ABI is absent from packaged native libraries.

Do not remove ARM64 repair libraries, the extractor, bootstrap/network guard, JavaScript runtime, Python/FFmpeg runtime data or license notices to save space. They are part of the current installation or execution contract. Native archive pruning is handled separately by [repack-native-media.py](../../tools/repack-native-media.py), which validates dependency closure and archive pins.


## Compatibility-library packaging correction and historical artifact evidence

The initial API 26 x86_64 packaged-runtime test reproduced a native-child linker failure: `libpython.so` could not start because `libandroid-support.so` was missing. The app/instrumentation process continued. This is separate from the original paste crash, which was not reproduced from a supplied stack trace.

The repacker incorrectly classified bundled `libandroid-support.so` and `libandroid-glob.so` as system libraries, excluding them from its dependency closure. Removing only those two names retains the libraries. All eight repack output pins were rederived from the same verified upstream 0.18.1 AARs; the upstream archive and WebP replacement pins did not change. Native merge tasks now declare the repack script and shared pin manifest as inputs. A new ELF32/ELF64 × library regression failed in four subcases before the correction; the six-method suite passed afterward. This was archive repacking, not native compilation or a WebP rebuild.

The current source pins below include the subsequent GLib/PCRE closure correction. Rebuilt artifact inspection and runtime qualification are pending; these are repacked archive pins, not final APK/AAB identities:

| ABI | Repacked archive | Bytes | SHA-256 |
|---|---|---:|---|
| arm64-v8a | FFmpeg | 32,584,382 | `7b06532c6255aa6d0ef3eca644c8a4e7d684776b3d8788fda8645502d6a94b20` |
| arm64-v8a | Python | 12,481,633 | `08755274968729cf539d5299b474cf1f3b151236521eee54a87e2ad690fb37bd` |
| x86_64 | FFmpeg | 35,488,596 | `0ff9518b52f4225379d1570cf34530a753c72957c730c44e3ebb0fa2ac6cfcb8` |
| x86_64 | Python | 12,271,932 | `26f4eef13e58b51070c01ae71117be307d64154137bf391c19d318a82f40c814` |

The complete eight-archive pin manifest also retains the two 32-bit ABI derivations for consistency; those ABIs are excluded by the current application filters. Do not overwrite the historical WebP provenance JSON to imply that its original inspection used these later repack pins.

The previous support/glob-stage APK/AAB inspection recorded **218 effective ELF payloads in compact and 438 in debug**. Those counts and artifact hashes predate the PCRE restoration; final rebuilt-artifact inspection is pending. Across compact, debug and release AAB, all **eight artifact/ABI/archive observations** verified the restored library's presence, archive pin and ELF layout; both compatibility library types depend only on `libc.so`. Compact/AAB contain ARM64 runtime/override assets only, while debug contains ARM64 and x86_64. These are packaging observations, not eight runtime executions.

Five original WebP libraries remain in each ARM64/x86_64 FFmpeg archive. Per ABI their 18 LOAD segments have alignment below 16 KiB, and 13 have offset/address incongruence modulo 16 KiB. The replacement-aware guard validates the APK-owned replacements and excludes those five originals from the effective-library checks. Neither that policy nor successful SDK ZIP alignment inspection establishes native startup on a 16 KiB Android kernel.

The upstream Python/FFmpeg installers invalidate extracted packages using archive byte length, and the corrected lengths differ. Upgraded-install re-extraction/startup without clearing application data remains a separate device check; no new hash-based extraction invalidator was added. Consult [TEST_REPORT.md](../../TEST_REPORT.md) for the current device record. Detailed evidence is in the delivery reports `native-compat-repair.md`, `native-compat-derivation.json`, `native-compat-red.log`, `native-compat-green.log` and `final-artifacts.json` / `.md`.

## Exact-name and transitive-alias closure correction

The next API 26 native run progressed through Python/yt-dlp/QuickJS assertions but FFmpeg failed to link because `libpcre2-8.so` was absent. The old graph skipped symlinks and mishandled hyphens/dots in the GLib chain `libglib-2.0.so → libglib-2.0.so.0 → libglib-2.0.so.0.8600.0`; it retained the real versioned GLib ELF while dropping its PCRE dependency.

The final repacker uses exact DT_NEEDED filenames and physical per-archive nodes, validates alias paths/cycles/targets, and includes dependencies of every retained ELF, including dynamic modules and versioned files. Both proposed archives are reopened and their complete retained payloads validated before either is written. Only the verified API 26 public-library subset is exempt; absent bundled backports are not relabeled as system libraries. The old `libgcc_s.so` exemption was removed.

The minimal payload difference restores PCRE and removes the two unused V4L aliases that previously pointed to pruned targets. All other aliases, prior retained ELF bytes and Python archive bytes remain unchanged. Original AAR/nested-archive hashes and all ten WebP replacement hashes remain unchanged. All four FFmpeg repack pins were derived again from the same verified upstream AARs.

The independent static audit checked **872 retained ELF instances, 408 symlink instances and 2,695 DT_NEEDED edges** across four upstream ABIs, with zero unresolved dependencies. Every ELF dependency list agreed with host readelf; all eight archives passed CRC and byte-idempotence checks. Restored ARM64/x86_64 PCRE files require only `libc.so`, have four 16,384-byte LOAD alignments each, and pass the existing RELRO inspector. These are header/dependency observations, not native execution on a 16 KiB kernel. The original five unaligned WebP files per shipped ABI are still replaced by the existing APK-owned overlays at initialization.

Fifteen focused Python test methods passed after recorded red regressions; those methods include earlier repack tests and must not be added to previous suite totals as distinct new tests. Evidence: delivery reports `native-closure-repair.md`, `native-closure-derivation.json`, `native-closure-green.log`, `native-closure-api26-public-libraries.txt` and `api26-native-rerun.log`. The FFmpeg archive lengths change again, invoking the existing length-based extraction policy; final installed upgrade/startup/mux and fresh artifact verification remain required.
