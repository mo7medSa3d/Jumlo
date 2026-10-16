# Inline execution ledger - plan: docs/media/IMPLEMENTATION.md
Baseline: provided Android source ZIP, SHA256 3efe3b8f63d8ec050a1f695a3c356b6f3b1d8bda68b93180c16e82fa67ec1f4a.
Baseline host verification: 52 core/HTTP, 8 SQL, 8 static, 4 shell checks passed in this session.
Ruling: the current explicit media-downloader request supersedes the earlier spec's media-extractor exclusion. Preserve all existing pillars; add a downloader, not an unrelated editor. Cost: a new native runtime and separate qualification obligations.
Ruling: perform the work in an isolated source copy, with no push/publish or changes to the user's machine. Cost: source ZIP requires integration/build elsewhere.
Ruling: native media dependency is declared as a separately enabled build option, not silently imposed on the base product's licensing. Cost: the full media build needs -PextendedMedia=true and transitive licensing/device qualification. No license of the user's existing source is changed.
Pre-flight: DownloadSettings is consumed by JobStore, retry, worker and draft; extend it compatibly. New media settings must never contain raw URLs, cookies or signed stream endpoints.
Pre-flight: the shared dispatcher owns concurrency; media publication uses existing durable output IDs. New capture UI must not replace the app or bypass capability gates.
Tasks: M01 pure selection/command/output policies; M02 bounded process runtime and Android adapter; M03 native UI/browser/worker integration; M04 real host media tests + project audit + package.
M01: core capability and 22 media policy checks had observed behavioral RED (52/23) then GREEN (75/0). Full logs retained.
M02: process supervision had observed behavioral RED (75/10) then GREEN (85/0). Eight production Python URL/redirect guard cases had observed RED then GREEN.
M03: two format-identity checks observed RED (one failing) then GREEN (87/0). Native parser tests are written but not executed; Android implementation is not fully qualified.
M04: production process supervisor/output signatures were exercised with real host FFmpeg/ffprobe: five additional real-media cases PASS, combined Kotlin suite 92/0. This is not a live-platform or Android-native runtime test.
Ruling: the media transport uses the urllib handler so URL validation, TLS-downgrade rejection and cross-origin credential stripping have one enforcement point. Cost: sites requiring browser impersonation or other transport handlers may fail; transport-adapter compatibility still requires native tests.
Ruling: the direct file path remains separate and no browser login cookies are silently exported. Cost: session-required resources may need further explicit session integration; the current build reports that state, not successful download.
Ruling: original live streams/playlists remain outside finite single-file jobs. Cost: playlist selection and bounded live recording are not supplied by this extension.
M03 static: initial source check reported 45 missing keys because it read only strings.xml, not Android's other values XML files. Corrected the harness to aggregate all values files and check cross-file duplicate keys. Static suite 8/0. This was a harness limitation, not a passing Android build.
Final review: self-review (no independent subagent/reviewer available). Important findings under test: reserved format IDs could act as selector aliases; a schedule exception after database persistence could mint a second request on user retry. Native adapter review also identified that upstream initialization leaves an older unpacked extractor across APK updates.

Final: fixed selector aliasing and uncertain-enqueue duplicate identities; observed review-regressions RED (88 passed / 2 failed), then GREEN (95 / 0 including five real-media cases). These tests cover core policies/identity, not Android scheduling.
Final: fixed recursive metadata-parser resource risk with a quote/escape-aware depth and byte guard; observed json-guard RED (91 / 1), then full GREEN (97 Kotlin / 0, network guard 8 / 0, actual SQL 8 / 0, static 8 / 0, shell 4 / 0).
Final: native APK extractor refresh uses AtomicFile and the APK resource; native path changed by source review only, not an executed Android regression. It remains UNQUALIFIED.
Final: all 46 new media resource keys translated in English and Arabic. Older product localization and visual/device qualification remain incomplete.
Final: base build, media build, Gradle unit, Android lint, instrumentation and wrapper launch requested; all BLOCKED at actual preflight, exit 2. Gradle never started. No APK.
Final: full-product inventory remains open: 17 partial/unqualified and 24 not implemented/gated of 41. Media work does not silently mark unrelated utilities complete.
Final: minor (deferred): compact control rows have no small-screen/large-font rendering evidence; adjust after actual Android visual qualification.
Final: minor (deferred): byte-count and technical codec labels use locale-neutral units; existing non-media localization remains unfinished.

Final packaging review: normalized generated CSV line endings after a whitespace-only failure; source/docs staged diff check exited 0. Raw evidence logs are preserved and excluded from source whitespace checking.
