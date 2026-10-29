# Release checklist

- [ ] Verify new Settings haptic Off/Light/Medium/Strong and Reduce motion on API 26–36 devices: persistence across process recreation, actual haptic feedback, system motion preference dominance and RTL/large-font accessibility. Run `PreferencesPersistenceTest` on Android; CI JVM tests alone do not establish physical-device behavior.


This is a release gate, not a claim of Play approval. Consult the current [TEST_REPORT.md](TEST_REPORT.md) for executed commands and artifact hashes. Unchecked gates remain release blockers even when an APK builds.

## Owner and signing

- [ ] Supply the owner's upload/release keystore. Do not reuse a debug key or commit passwords, keystores, service credentials or signing material.
- [ ] Configure `JUMLO_KEYSTORE_PATH`, `JUMLO_STORE_PASSWORD`, `JUMLO_KEY_ALIAS`, `JUMLO_KEY_PASSWORD`, or an ignored complete `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Environment values take precedence.
- [ ] Confirm the application ID, Play ownership, next unused `versionCode`, display `versionName`, update signing lineage and Play App Signing setup.
- [ ] Run `./gradlew :app:verifyPublishableRelease` with real credentials. The task requires the complete runtime, release APK size below 60,000,000 bytes, ARM64-only native contents, AAB, release lint and a non-debug signing configuration. It does not upload anything.
- [ ] Independently verify certificate identity with `apksigner verify --verbose --print-certs`; a keystore filename/alias check cannot establish ownership.
- [ ] Save the signed APK/AAB hashes and final exact compressed-size breakdown. Re-run the size gate after signing and any asset change.

`release` is deliberately unsigned without credentials. `compact` is an optimized ARM64 installer signed with the local debug key for testing, not a production signing substitute.

## Automated build gates

- [ ] Fresh `./gradlew :app:lintRelease :app:testDebugUnitTest :app:connectedDebugAndroidTest` completes with no unresolved correctness/policy findings.
- [ ] Before the unfiltered connected run, complete the [fixture/storage preflight](verification/current/device/preflight-source/README.md): stage all five scan inputs in the installed debug app's `files/bench`, record their hashes, and check guest space plus host growth for the 200-page case. Preserve any method filters, SDK skips and opt-in screenshot exclusions in the final results.
- [ ] Build `:app:assembleDebugAndroidTest`, `:app:verifyReleaseApkSize` and `:app:bundleRelease`; inspect the final merged permissions and the ABI/language splits.
- [ ] Run the host transfer/storage/diagnostic/Office/document regression scripts and keep their current logs. Do not treat inherited reports as fresh results.
- [ ] Record/verify the Roborazzi empty/loaded matrix for every tool in English/Arabic and light/dark, then visually review all images. Review the separate blocked-state manifest.
- [ ] Run drag/pinch/rotation, alpha-channel signature, AcroForm overlay, crop, page reorder, cache, grant-loss and public-save recovery instrumentation tests.
- [ ] Validate final nested native ELF payloads with `scripts/inspect-media-artifact.py` and execute them on an actual ARM64 device, including 16 KB page-size coverage.

## Device and performance gates

- [ ] Complete the minimum-supported Android 8/9 and Android 14+ matrices on an emulator and at least one authorized physical device. Add current Android 15/16 timeout/quota checks for the target API.
- [ ] Reproduce the originally reported paste crash with its exact input/device, preserve sanitized logcat, confirm a causal fix, and repeat after rotation/recreation. A safe error boundary is not proof of the original cause.
- [ ] Run all seven downloader platforms against owner-authorized fixtures: video, audio, actual quality choices, invalid/private/removed/age-limited links, offline state, pause/resume/cancel and process-kill recovery. Record unsupported source capabilities honestly.
- [ ] Run each file tool on ordinary, >50 MB, >100-page, encrypted and corrupt inputs where applicable. Confirm input hashes are unchanged, output can be reopened, partial stages are removed and no empty file is published.
- [ ] Capture a measured app-specific Baseline Profile on a supported rooted/profile-generation environment and package it; comments or historical rules are not a measured profile.
- [ ] Run `:benchmark:connectedBenchmarkAndroidTest` on a declared mid-range physical device. Cold-start gate: under 2 seconds. Record frame timing while scrolling both Tools and populated Files; an empty list or software-emulator timing is not the requested performance evidence.
- [ ] Verify 200% font scale, TalkBack traversal/actions, 48 dp touch targets, contrast, RTL gesture direction, keyboard behavior, predictive Back and per-tab state restoration.

## Manual smoke checklist

- [ ] WP14: Confirm current tool matrix covers every registered ID × four scenarios (45×4), with 160 processing NOT_RUN and 20 navigation N/A until real evidence. Run authorized Drive transfer cases; never count the archived IDs or N/A cases as PASS.

- [ ] Verify UI sounds on/off with normal, silent and muted Android system modes; relaunch and check restored setting, Arabic RTL and large text.

- [ ] WP06: Compress already optimized input and assert unchanged status, with no false partial-savings text.
- [ ] WP06: Compress a scan with some skipped streams; assert partial warning only alongside verified reduction and preserved pages.
- [ ] WP06: Run all five scan benchmarks and 200-page memory test with real devices, hashes, storage limits, and outputs.

| Flow | Required observation |
| --- | --- |
| Fresh launch | Nonblocking splash, usable Home, clear onboarding dismissal, no crash/ANR. |
| Navigation | Tools–Files–Home–Downloads–Settings order; centered contained indicator in both directions; Settings detail returns to its caller; each root restores its state. |
| Intake | PDF/image/ZIP/text share, multiple files, malformed URI/Parcelable, warm deep link, no compatible external viewer, revoked provider grant. |
| Downloads | Paste does not terminate the app; metadata precedes selectors; chosen format matches available source; progress and cancellation are actionable; private and public output status are distinguished. |
| Document studios | Preview corresponds to saved settings; text/logo transforms and multiple signature positions survive submission; undo/redo and page scope affect the intended pages. |
| Scanner | Primary scanner opens directly; gallery works; unavailable Play services uses CameraX; permission denial, Back, reorder/delete, rotation and capture-in-flight are safe. |
| Conversion | DOCX paragraphs/headings/Arabic text and PPTX page images/text reopen in independent parsers and actual target Office apps; limitations are visible before running. |
| Files | List/grid, thumbnails/date/size, sort/filter/group, rename preview, exact duplicate groups, delete/restore/purge and changeable save folder show real current data. |
| Settings | Theme/language/quality/save-folder persist, storage totals update, eligible temporary files are removed while source/output/job data is retained. |
| Failure recovery | Low storage, corrupt/encrypted document, network loss, stopped worker, public-provider failure and app relaunch preserve a clear recoverable state. |

## Play Console and policy

- [ ] Complete the social-download rights/platform-terms review for every enabled/advertised provider. Preserve functionality in the source while obtaining a compliant authorization/integration route; hosting an extractor does not resolve the policy risk.
- [ ] Review full native/runtime licensing and distribution notices, encryption/export questions if applicable, and the retained license assets.
- [ ] Fill the real operator/contact/effective-date fields in `PRIVACY_POLICY.md`, publish an accessible HTTPS policy page, and link it in the app/store.
- [ ] Complete Data safety from the actual released SDKs/configuration, including optional backend and model/thumbnail requests; do not infer “no data collected” merely from local document processing.
- [ ] Complete foreground-service declarations, target audience/content rating, app-access instructions, testing-track requirements and any permissions declarations requested by Console.
- [ ] Review English/Arabic listing text and actual screenshot assets. Do not advertise unqualified legacy tools, guaranteed downloads, scanned-Arabic OCR or exact Office-layout conversion.
- [ ] Run Play internal testing/pre-launch report on the signed AAB, resolve crashes/ANRs/accessibility findings, and review staged rollout/rollback procedures.

No publishing, external messaging, service deployment or signing-key creation for production was requested or performed.

## 2026-10-28 — Reader position acceptance

- [ ] Verify last page persists after returning from Files, app relaunch and process death; test restored page bounds when the PDF is replaced by a shorter document.
- [ ] Verify bookmark write/read errors do not crash or prevent PDF opening, with no raw URI in app-private preference file.
- [ ] Measure 200-page reader scrolling and memory on a mid-range physical Android device.

## WP07 print selection release gate

- [ ] Test stale/duplicate saved page order with page rotation, moving and selection preservation on a real Android emulator/device. Inspect final printed PDF, preview, orientation and copies; JVM tests do not qualify the OS print dialog.

## 2026-10-28 — CI trigger deduplication

- [ ] Confirm exactly one full Android build per new WP pull-request synchronization, plus normal main/tag/manual triggers; verify no required status context disappeared.
- [ ] Confirm the `test-ci-event-matrix.py` regression and existing KSP serialization test execute before Gradle without bypassing any assemble, unit, lint or size gate.

## 2026-10-28 — CI release shrinker memory guard

PR/main full-media CI is serialized and now grants the Gradle worker 4 GB max heap to avoid the observed R8 Java-heap exhaustion (PR #31 run 40644691679). This is resource configuration only: do not bypass minification, lintRelease, full-media payload/ABI checks, or the 60 MB compact/release limits. Keep the current-head CI result attached to the release candidate; earlier successful runs do not validate later changes.

- [ ] WP13: Confirm no Settings option suggests global Wi-Fi-only or concurrency customization; verify per-job Wi-Fi flag still reaches a real download job and fixed 2-task scheduler remains enforced.

- [ ] Execute `ScanPreviewBitmapOwnershipTest` on actual Android emulator/device; 100-page capture/gallery scroll, reorder and remove, repeatedly rotate/recreate and inspect memory/heap for native Bitmap leaks.

- [ ] Before the unfiltered connected run, follow [fixture/storage preflight](verification/current/device/preflight-source/README.md): generate and hash all five scans, stage the verified inputs, and record guest/host disk budget for 200-page testing. Any missing input remains BLOCKED.


### Pure Kotlin module gates (PR #41)

The four core modules have dedicated JVM test suites included with the full media CI gate. Post-integration CI remains required.
