# Jumlo Production Completion — Working Plan (2026-10-22)

## Status @ 2026-10-22 late (post-size-engineering)
- **Compact APK: 57,292,693 bytes ≤ 60,000,000 — PASS** (baseline 67,856,706 → −10,564,013).
- `inspect-media-artifact.py` on the real compact APK: PASS (extractor pin 2026.10.21, webp overrides, 212 nested ELFs verified).
- Static contracts: universal-media-architecture OK, source-static OK (schema-provenance.md created), brand-motion OK (brand restored from HEAD), test-workspace.sh OK (shared embedded Kotlin helper), ci.yml rewritten to media-default + explicit lite input.
- verify-full gradle gate: verifyCompactApkSize PASS after repack (REPACKED_OK ×8, pins validated).
- IN PROGRESS: full `scripts/verify-host.sh` (background); then commits, source zip, engineering report.

## Size engineering (DONE — measured deltas on compact arm64)
- 67,856,706 → 60,547,681: AAR duplicate `res/raw/ytdlp` overridden by 394-byte valid stub (−3,170,332); `org/bouncycastle/pqc/**` resource excludes (bcprov dead post-quantum parameter data ≈ −4.14 MB).
- 60,547,681 → 57,292,693: deterministic closure repack of `libffmpeg.zip.so` (35,624,931→33,838,431) and `libpython.zip.so` (14,305,904→12,756,971) via `tools/repack-native-media.py`; digests pinned for all four ABIs in `manifest.properties` (`{abi}.ffmpegArchive.sha256`, `{abi}.pythonArchive.sha256`, `{abi}.upstreamArchive.sha256` provenance); mergeNativeLibs doLast hook fails the build on pin drift.

## In-flight migration to finish (media runtime now default + compact variant)
- [x] ci.yml lite_build + both inspect scripts + `Jumlo-Media-Debug-APK-` (test-universal-media-architecture.py OK).
- [x] docs/media/UNIVERSAL_ENGINE.md recreated (GPL-3.0, Play, yt-dlp, APK, 16 KB, NOT VERIFIED, YouTube).
- [x] Remaining static-check failures fixed (brand, schema-provenance, workspace Kotlin fallback).

## Then
- [ ] Full gradle verification (verify-host.sh running).
- [ ] Commit state in coherent commits; package clean source zip + verified APK + engineering report.

## Review focus (spec-implied failure modes)
- Webpage-as-media: MediaOutputPolicy.inspect container sniff at publish — pinned by app tests/media policy host tests.
- Partial download publish: StreamTransfer never publishes partials; journal ETag resume path (RemoteExtractorRuntime) — pinned by host tests.
- 60MB cap: verifyCompactApkSize on signed compact artifact.
- CI/test/build/docs consistency after architecture flip.
