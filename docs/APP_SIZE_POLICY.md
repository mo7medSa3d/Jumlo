# Jumlo: 60 MB APK limit and native-download contract

- **Hard limit:** CI fails when the raw installable APK exceeds **60,000,000 bytes** (decimal MB, not the Actions artifact ZIP).
- **Not bundled:** Python, yt-dlp, native FFmpeg, QuickJS, or disabled ML Kit OCR models.
- **Supported:** HTTP(S) direct media file URLs such as MP4, M4A, WebM, MP3; existing direct PDF/image/file downloads, and the current Google Drive direct-file transfer path. No media is sent to a server for processing.
- **Safety:** HTTP consent, redirected URL validation, host-scoped cookies, bounded storage, output-signature checks and Android track validation remain. Original formats only; a landing-page response is rejected as non-media.
- **Tradeoff:** YouTube/TikTok/Instagram/etc **page** extraction, alternate adaptive resolutions, DASH muxing, MP3 audio transcoding and DRM bypass need other engines and are **not supported** by this compact build. Do not present those features as working or fabricate outputs.
- **OCR/webpage capture:** Already security-gated by `CapabilityPolicy`. The original source is archived at `docs/deferred/OcrWebHandler.kt` outside Android source sets. The installed APK does not include ML Kit model data.
- **Verification:** Main-branch GitHub Actions compile debug, run JVM unit tests and lint, enforce actual APK byte size, scan APK for forbidden native payload names, then upload the APK artifact. Tags and manual dispatch can run full release/R8. Emulator device tests remain opt-in.
- The 60 MB gate is not a substitute for direct-media download testing on real Android devices.
