# Decisions — 24 October 2026

## 2026-10-28 — WP06 release gate preservation

Normal Android CI runs both `lintDebug` and `lintRelease`, JVM tests, the full-media compact and release APK 60MB byte-limit checks, and the Python source-contract check. Preserve KSP2 serial worker flags from merged PR #36; never disable the media runtime or skip the size gate to get green CI. No Android UI/route changes.


**2026-10-28 WP03 Settings decision:** Persisted haptics (Off/Light/Medium/Strong) and in-app Reduce motion are genuine controls. In Activity, `LocalReducedMotion` combines user setting with system reduced-motion via logical OR; haptic activity locals consume persisted values. Keep Android system motion policy authoritative. Preserve Wi-Fi-only visibility policy independently.


1. Use only the uploaded recoverable ZIP as product source. Reuse installed toolchain binaries and dependency cache, never prior build/test claims. The current run may populate the shared installed dependency cache to avoid a duplicate multi-gigabyte cache; source and artifact identities stay isolated.
2. Execute without further design approval because the supplied brief explicitly delegates decisions and asks not to pause. Preserve current architecture and repair verified gaps.
3. Keep full on-device media extraction by default while measuring its real APK impact; the optional backend stays behind the existing extractor interface and is not deployed. A network service does not automatically settle content rights or platform terms.
4. No feature is labelled runtime-working without this execution's build and device evidence. Physical-device/Play Console checks cannot be fabricated.
5. Keep prior audit/test reports only as clearly labeled recovered history. The current root reports supersede their status claims.
6. Preserve Android accessibility animation-scale behavior even though the in-app reduce-motion setting is removed; no additional in-app motion toggle will be exposed.

## Implementation choices

7. Make every registered tool functional and reachable. file_drive (Google Drive Import/Export) is enabled: it has a real Drive v3 implementation (FileExtrasHandler.googleDrive) and is reachable from the Tools catalogue. webpage_pdf and xf_05 are removed: both were unimplemented dead routes (webpage_pdf was dispatched to OcrWebHandler, which rejects any toolId other than pdf_ocr; xf_05 was an expansion concept with no handler and no cryptographic backend). They are deleted from the registry, dispatch, workspace form, UI, strings and icons rather than left exposed as blocked. The registry now holds 45 tools, all enabled and visible. This supersedes the prior "do not promote unqualified implementations" stance at the owner's explicit request to make all tools work; the Drive workflow remains token-gated (user-supplied token, Android Keystore-bound) and no vault or web-render feature is claimed as implemented. Public Drive direct-file links remain a separate downloader capability.
8. Lift the 60,000,000-byte APK size cap. The on-device yt-dlp/Python/FFmpeg/QuickJS runtime is retained; the prior untouched build was 54,878,634 bytes. The size assertions in verifyCompactApkSize (<=60MB) and verifyReleaseApkSize (<60MB) are removed so the full feature set is never silently trimmed; the ARM64-only native-library and assets/media/yt-dlp payload checks are retained as correctness gates. Backend-only extraction would move native weight off-device but introduce hosting, operating cost, credentials, data processing and availability obligations. The optional interface remains swappable; no backend is deployed by this task.
9. Bind optional backend credentials to a normalized HTTPS origin, encrypt them with an Android Keystore AES-GCM key and expose URL/token as one atomic configuration snapshot. Changing origin clears the previous token. A debug-only loopback HTTP exception supports local tests. Unreadable encrypted credentials require re-entry; plaintext fallback is forbidden.
10. Retain a validated app-private output even when its public destination fails. Persist that private record before external publication, expose the destination failure and retry the public copy. Coroutine cancellation uses explicit URI ownership and bounded commit cleanup. A provider and Room are separate systems: do not claim exactly-once publication across force-kill without a persistent reconciliation protocol.
11. Retry/release database mutations are fenced by the worker owner. Eligible paused/failed/waiting jobs may atomically reclaim an expired lease; a still-active owner cannot be stolen. A failed draft reset blocks the current submission, while the next explicit New run retries persistence after the previous attempt completes.
12. Generate compression estimates by running the same bounded compressor as export. Cancellation drops stale measurements and the UI shows loading. This consumes real CPU and temporary storage, so no universal sub-300 ms completion promise is made. Host codec measurements and Android codec measurements are qualified separately.
13. Preserve paragraphs, conservative font-evidence headings and embedded Arabic Unicode/RTL in DOCX. Use a rendered page image plus an extracted text layer in PPTX. Reject all-blank DOCX outputs. Complex tables/columns are not reconstructed, and the installed unbundled ML Kit recognizer does not provide scanned Arabic OCR.
14. Keep ML Kit's direct multipage scanner as the first choice. Add CameraX only as a fallback when Play services cannot launch the scanner. Request camera permission only for that fallback, retain an optional camera feature, and decode one bounded EXIF-normalized page at a time when building its PDF.
15. Use official Material Symbols Rounded filled 24dp vectors from the pinned upstream commit in ICON_MAP.md, with one distinct icon per registry tool. Keep source attribution and Apache 2.0 license. The complete persisted usage ranking controls the flat grid; navigation-only tools also count as usage.
16. Serialize tool and scan navigation origins. The outgoing entry retains its own tool ID for shared-element identity; Home and Tools use different transition namespaces. Back removes the completed entry before restoring its calling tab. Runtime/predictive-Back acceptance still requires Android tests.
17. Keep the existing font subsets and neutral pure-black dark design. Use tonal controls instead of outlined controls. Do not claim WCAG certification from source token calculations alone; device rendering, large fonts and TalkBack are separate checks.
18. Record only timestamp, validated operation category, exception class and bounded validated stack frames. Exclude throwable messages, names, document content, full URLs and tokens. Keep bounded app-private logs; the optional Crashlytics-compatible sink is off by default. Debug StrictMode is diagnostic and does not replace device testing.
19. Release signing accepts only complete owner-supplied environment variables or ignored properties. An absent key produces an unsigned artifact and blocks the publishable-release gate. A compact/debug-signed test APK is never a production signing substitute. Automated alias checks cannot prove ownership; inspect the actual certificate before publishing.
20. Keep R8 full mode, resource shrinking, ARM64 release and en/ar bundle splits. The full-runtime, ABI and strict decimal-byte size gates fail the build if violated. AAB container size is not the APK size gate. Native ELF/ZIP inspection is distinct from execution on ARM64 and 16 KB hardware.
21. Use genuine Roborazzi activity rendering with real local fixtures for screenshot evidence. External scanner/account/live metadata states receive explicit blocked rows. Host images are not Android-device evidence. Preserve failing logs and distinguish skipped tests from passes.
22. Run the available API 26 emulator under software emulation because KVM is absent. Treat its timings, System UI stalls and frame counts as unsuitable for mid-range-device performance acceptance. No physical device is attached; cold start, thermal behavior, camera capture and physical-device qualification remain release gates.
23. Keep the current version name/code while preparing a source candidate. The owner must select an unused monotonic versionCode in Play Console before release. Draft privacy/store documents deliberately retain owner/contact/policy-URL placeholders until factual information is supplied.
24. Save the complete revised source and evidence as the source deliverable. Exclude toolchain caches, local signing keys, local.properties and build intermediates. No repository push, external service deployment, Play upload or publication is part of this task.

25. Use the same Android image encoder for conversion/compression/target-size previews and exports. A bounded preview can be a reduced-resolution sample and must show that limitation, its actual encoded byte count, format and dimensions. It is not presented as an exact full-resolution target-size result.
26. Allow CameraX’s native dependency on `libjnigraphics.so`: the Android NDK documents it as a public API since API8, below this app’s minSdk26. Keep rejecting unknown native libraries and preserve strict closure and page-alignment checks; static inspection does not establish device execution.
27. Run the CI emulator job on ordinary push/PR events as well as manually requested runs; the manual workflow may explicitly opt out. Enforce full-media release size, AAB and lint in the build job. This configuration change is not evidence of a completed remote CI run.
28. Keep explicit ownership of an input stream until a dispatcher handoff delivers it to its caller. If cancellation discards that result, close the unclaimed stream on the IO dispatcher inside a non-cancellable cleanup boundary. Preserve the initiating failure and suppress a cleanup failure. This addresses a controlled API edge reproduced in deterministic tests; it is not a claim of a routinely observed leak.
29. Use count-neutral English/Arabic labels for active quantity messages that lacked quantity resources, preserving all existing formatting placeholders and call sites. Leave ordinals, already-neutral labels and unused legacy strings unchanged. XML/key/format checks establish resource consistency; final lint and rendered UI checks remain separate.
30. Qualify builds in an isolated temporary output and Gradle project-state directory. Stale generated DEX files reappeared alongside current files in the synchronized workspace and caused a duplicate-class failure without a duplicated dependency; the exact restoring process was not proved. An environment-only init script isolates generated files, after which D8 passed; no dependency or functionality was removed. Keep the failed intermediate logs and identify mixed-source compilation separately from behavioral regression evidence.
31. Serialize heavyweight build, screenshot and emulator work when necessary in the available 8 GB/no-KVM environment. Preserve emulator userdata across bounded boot attempts. A device showing up in ADB is not a completed boot, an installation is not a successful flow, and an emulator infrastructure shutdown failure is not an application crash.
32. Pin the benchmark module to Build Tools 36.0.0, matching the application module and installed SDK. Its implicit AGP default requested absent Build Tools 35.0.0 before task execution; the explicit shared toolchain removes that configuration mismatch. Benchmark compilation remains separate from performance measurement.
33. Resolve the benchmark's missing pinned dependencies from the project's existing official Google/Maven Central repositories without changing dependency versions. The network-enabled isolated run compiled the benchmark Kotlin module; it did not execute a benchmark or generate an application profile.
34. Keep host screenshot failures as evidence. Robolectric 4.14.1 does not provide a functioning native PDFRenderer for this harness: API35 failed in the host FileDescriptor boundary, and inspected older framework native methods were inert. Import fixtures on a route that does not start previews, verify the actual visible tool title before capture, and mark native-PDF host states unavailable. Use an opt-in Android instrumentation capture path for real native PDF UI; do not substitute placeholder pixels or count stale-route captures as successful states.
35. Correct product defects demonstrated by the actual English/light captures in one bounded change: restore the batch-rename icon's original 24-coordinate viewport, use the archive extraction action label, use a neutral starting counter label, and make five shared English/Arabic range labels match their existing controls and execution domains. Preserve the official icon path, resource keys, numeric domains and tool behavior. Rebuild and inspect the corrected snapshot before treating earlier artifact or screenshot results as its evidence.
36. Run native screenshot variants only by explicit instrumentation arguments on dedicated clean app data; the ordinary suite skips these four methods. Preserve captures before any test-data reset and keep their 96 per-variant UI states separate from tool-operation results. Stage the five original compression PDFs by exact hash, allow for the generated 200-page document and simultaneous PDFBox scratch files, and observe guest free space plus host QCOW growth before the heavy lane. A configured 6 GiB partition does not establish available space; a storage-limited filtered run remains partial. The preflight budget is conservative source-derived planning, not a measured memory, disk or performance result.
37. Observe software-emulator readiness through fresh transport, SDK, boot-complete and native package/activity service checks. On API 26, the Java `pm` wrapper exceeded the former observer's eight-second allowance while supported `cmd package list packages -f android` and `cmd activity get-current-user` returned usable results. Two native observations on 2026-10-23 at 16:37:29 and 16:37:39 UTC confirmed the exact framework package line, boot completion and user 0 without installing or testing Jumlo. This is infrastructure readiness, not app performance. The observed 541,687,808 free data bytes before installation cannot hold the approximately 775 MB generated compression input; any filtered execution on this target leaves the complete heavy/connected gate blocked. Preserve prior failed observer samples and later app execution as separate evidence.

38. Route an already-running activity's supported `jumlo://workspace` intent through the existing canonical `openWorkspace` path, preserving the calling tab in ToolRoute. The exact host trace showed the old compatibility-only graph lost its calling Files entry; the cached Navigation 2.8.4 implementation explains its inclusive graph replacement. An actual API 26 regression then timed out returning to Files after the visible Back action. Preserve the registered cold-launch compatibility route and manifest unchanged: retained source support is not a cold-link runtime verification. The strengthened existing method checks two warm deliveries, Back to Files and no stale workspace on Tools. At that intermediate checkpoint its corrected-source green run remained pending; decision 40 records the later matching-artifact result.
39. Define image-comparison progress as the original-image fraction in both directions. In RTL, put the original on the right and mirror clipping, divider position and drag coordinates so the existing localized badges and Slider agree. Actual API 26 red evidence showed the Arabic original-side pixel was green instead of the expected blue after preceding English assertions. Keep the expanded native regression unchanged through the fix; its four language/theme configurations remain iterations of one method. Give only the batch-rename machine-pattern value LTR text direction, retaining Arabic labels and unchanged pattern execution. Applied source corrections and preserved pre-fix screenshots alone did not establish a green rerun or corrected rendered-state acceptance; decision 40 records the later scoped native and host results.

40. Refresh the warm workspace callback with a lambda in `rememberUpdatedState`, rather than a local function reference. The first canonical-routing correction was insufficient: the unchanged API 26 test still failed Back-to-Files while the comparison regression passed. A subsequent host diagnostic passed its capture assertions but recorded an actual Files root opening `ToolRoute(..., transitionOrigin=home)` before MainActivity stored the incoming Intent, then Back returning Home. The exact Kotlin 2.0.21 function-reference equality ignores captured tab fields; Compose 1.7.5 structural state equality therefore retained the initial callback. Preserve the complete 470-event trace, dependency bytecode and original native failure. Restore the temporary observer byte-for-byte and keep native assertions/timeouts unchanged. The later matching candidate-2 API 26 run now records 2 PASS / 0 FAIL / 0 SKIP in 109.115 seconds: the warm-link method repeats Files → workspace → visible Back twice and verifies no stale workspace on Tools, while the comparison method covers English/Arabic × light/dark within one method. The app SHA is `51f9ccef05dd7065d6e9dd0a3faf7a72802b8a924130da49daab4db17cd68e59`, test SHA `02e9bd40e25373c8e55cf865a6b8a6a9ff09eb9dd256e6b05f67aa637dfcb5f5`, and 747-input source fingerprint `6d6663e62a5d76effe2905d17029a309162d3aab3f656f9f0763696ebfabb558`. Preserve the earlier native failures separately; see [actual native pair](verification/current/device/api26-ui-regression-candidate2/CURATION_NOTES.md). Separately, [host screenshot attempt 6](verification/current/screenshots/attempt6/README.md) passed all four unchanged capture methods, with all 260 recorded PNGs visually reviewed and 124 states explicitly blocked. The known LOW Compare Images heading-only card remains disclosed. [Attempt 5](verification/current/screenshots/attempt5/README.md) retains the prior Arabic-light preview timeout; the successful unchanged retry does not explain its intermittent cause. These scoped results supersede the earlier pending checkpoints without establishing cold-link, full-suite, physical-device, complete tool-operation or performance acceptance.

41. Reorganize the Tools screen into the brief's sections rather than the previous
flat usage-ranked grid. Sections (Scan & Capture, Organize PDF, Convert, Edit &
Sign, Optimize, Protect, Download, Files) are emitted as full-width headers inside
the single keyed `LazyVerticalGrid`; Pinned and Recent rows are fed by the
persisted `quickActionIds` and `recentToolIds`, and one search field collapses
the catalogue to a flat keyed list while searching. A trailing "All tools" group
catches any tool not listed in a section, so registering a new tool can never
hide it. Usage ranking still orders tiles within a section. EN/AR section titles
are inline (matching `PillarCategory`) to avoid a resource-parity hazard. The
two instrumentation callers of `ToolsCatalogue` were updated to the new
signature; their assertions now check ranking within a section.

42. Strip document metadata during compression. The brief lists "strip metadata"
as a compression-ladder step, and `PdfBoxBackend.scrubMetadata` already removes
the info dictionary keys and the XMP stream reference. The shared
`PdfCompressionRunner` now calls it on each document before `save`, so both the
measured preview and the published output drop author/device/tracking data.
The explicit Clean Metadata tool remains the way to review or preserve a
title/author. This is a pure-PDFBox, page-preserving operation and cannot
invalidate the document.

43. Expand the downloader qualification matrix to every routed platform. The app
routes 17 platform families through `DownloadLinkPolicy`; the plan previously
covered 7. The plan now covers all 17 (YouTube, TikTok, Instagram, Facebook,
LinkedIn, X/Twitter, SoundCloud, Vimeo, Reddit, Twitch, Dailymotion, Pinterest,
Bilibili, Threads, Snapchat, Google Drive, direct). `PLATFORM_META` binds each
plan key to its badge and representative hosts, and
`test-platform-matrix-parity.py` asserts the plan is a superset of the routing
domains, has no unrouted host, and that every host resolves to its declared
badge. The matrix is a qualification plan: every case starts `NOT_RUN` and
requires owner-supplied public URLs. It is not a claim of extraction support.

44. Do not execute destructive tool removal in this session. Removing a tool
requires deleting its route, `CapabilityRegistry` entry, EN/AR strings, icon
asset and dispatch wiring, none of which can be build-verified without a JDK and
Android SDK. The prune analysis and per-tool disposition are recorded in
`AUDIT.md` (merge `img_target` into `img_04`; fold `dl_02`/`dl_03` into
`dl_media`; drop the `file_02`, `file_trash` and `dl_settings` navigation
aliases; remove `img_stitch`, `img_compare`, `sha256`). No route, string or asset
was left dead. This defers removal to a build-verified pass rather than shipping
an unverifiable deletion.

45. Record the environment blocker explicitly. This sandbox has no JDK, Gradle
distribution, Android SDK, emulator/KVM or physical device, and the Gradle/Maven/
Android hosts are network-blocked. No Android build, lint, unit, instrumentation
or size-gate command could run. All evidence produced here is a Python host suite
or a source/wiring contract; nothing is an Android runtime PASS. The last measured
release-APK size (54,878,634 bytes) is inherited from the supplied baseline and is
not a current measurement.

**2026-10-28 WP08 scanner:** Only decoded thumbnail Bitmaps belong to their Compose rows; release on removal/scroll/dismissal using `DisposeOwnedPreviewBitmap`. Never recycle input originals or active processing outputs. Android device/heap behavior remains a separate test gate.

## WP13 split page-range allocation policy

The explicit PDF grouped-range parser limits its expanded page positions to 100,000 per operation (distinct from actual document page count), preventing unbounded boxed duplicate pages from short repeated inputs. This is a technical memory-safety limit, not feature pruning; regular 200-page selection and Arabic numeral syntax remain supported.


### Pure Kotlin module gates (PR #41)

The four core modules have dedicated JVM test suites included with the full media CI gate. Post-integration CI remains required.
