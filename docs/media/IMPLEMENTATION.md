# Media downloader extension - execution addendum
User-requested outcome: browse/paste/share a resource, inspect actual media formats, choose video quality or audio-only, and save a real file. Existing offline PDF/image/file tools remain unchanged. No claim that every URL on every platform is downloadable.

1. Add typed media metadata, selection, format/codec pairing, exact format expressions, safe output naming, explicit live/playlist/error handling; test policy before integration.
2. Run a pinned on-device yt-dlp/Python/QuickJS + FFmpeg adapter with bounded process I/O, deadlines, cancellation and isolated files. No shell-interpreted user arguments, no certificate bypass, no remote conversion server. Media sources are re-inspected before execution; mismatched identity/formats fail explicitly. Media retries restart safely rather than blindly appending changed segments.
3. Wire it into existing jobs/publication and a localized native quality-selection screen, with optional browser discovery and Android share intake. Keep direct files on the existing validated HTTP path. Capture URLs remain ephemeral. Do not export browsing cookies silently.
4. Run available host tests, real media processing using installed FFmpeg, native preflight and whole-source consistency checks. Deliver changed source, exact evidence, feature-gap inventory and limitations. Native build/device and real-platform support are distinct checks.

Dependency ruling: `-PextendedMedia=true` enables the separately declared media runtime source set/dependencies. The default base build keeps its prior dependency boundary. GPL-3.0 upstream wrapper + FFmpeg build licensing require review before distribution; this source handoff is not publication or a licensing decision for the owner.


Follow-up: `-PextendedMedia=true` is now **debug-only**; it cannot select the media implementation for release or benchmark. This source-level safety gate is tested statically, but the Gradle release dependency graph check and live platform qualification are **not yet executed**.
