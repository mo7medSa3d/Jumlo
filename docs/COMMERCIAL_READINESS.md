# Jumlo — Commercial workflow iteration (2026-10-18)

## Evidence and baseline

Starting archive: `Jumlo-Universal-Media-Source-2026-10-18.zip` SHA-256 `f27ad591494c89156da547ba63388178c89f5e3853953707e70586de2c2e7022`. The working copy was extracted from that archive, **not** silently reset to the older GitHub `main`. No GitHub push/PR was attempted. `com.jumlo.app`, `jumlo` identity, DataStore name and Room v2 schema are unchanged.

`LANG=C.UTF-8 LC_ALL=C.UTF-8 bash scripts/verify-host.sh`: **exit 0**, 149/149 Kotlin host tests, 11 media-bootstrap, 4 media-network, 7 extended-media architecture, 4 download-density, 10 storage SQL, 14 source/resource, 12 redesign wiring, 14 output-workflow wiring, 14 brand/motion, plus shell prerequisite checks. Host checks exercise pure core algorithms, static integration contracts, and sample file workflows; they **do not** compile/install the Android app. The new Android instrumentation tests are authored but not executed.

`./gradlew --offline :app:assembleDebug --no-daemon`: **exit 1 before compilation** with `UnknownHostException: services.gradle.org`, because Gradle 8.14 is not installed and this runtime has no reachable distribution download. Android SDK is also not installed in this container. No new APK, AAB, device screenshot, bundle size, native 16 KB result, live provider result, or Gradle JUnit pass exists for this archive. Release disposition: **NO-GO**.

## Delivered improvements (source implementation, device acceptance pending)

| Improvement | Actual end-to-end source route | Verification and remaining issue |
|---|---|---|
| Correct `Use as input` | Files/Activity results -> `OutputWorkflowPolicy.reuseTool(mime)` -> ViewModel -> target workspace | Host policy tests + UI routing contract; device not run |
| Batch saved-output workflows | Files multi-select -> ZIP / PDF merge / images-to-PDF -> SHA-verified `OutputStore.find` -> new immutable `SourceStore.inspect` copies -> existing WorkManager pipeline | 100 item / 4 GiB aggregate limits, duplicate/type checks; host policy + wiring checks; output on Android not run |
| Batch imported-source workflows | Imported Files multi-select -> compatible tool -> reopen existing owned source snapshots -> workspace | Source-batch RED→GREEN wiring test and source validation; device not run |
| Favorites | Home shortcuts + Files favorite filter/grid/list controls -> DataStore bounded UUID list | Host toggle/dedupe/eviction tests + wiring checks; device persistence not run |
| SAF MIME recovery | Provider imports and local scan imports -> `SourceMediaType` signature check (32 bytes) -> accurate workspace routing when provider says null/octet-stream | Pure Kotlin test: PDF, JPEG, PNG, WebP, GIF, ZIP, HEIC, AVIF, MP4, M4A; android instrumentation test authored but not run |
| Unsuccessful multi-output handoff | ViewModel -> delete **only** newly created inbound source copies, retain saved result bytes and metadata | Rollback code and Android instrumentation authored; not run on Android |
| Adaptive file layout | 2/3/4 columns by width; no mandatory page number/size changes introduced | Host structural test only; 200% font/TalkBack/emulator unverified |

## Commercial/product readiness

This is a **native, primarily offline utility app**, not a SaaS service with recurring cloud value. That is a feature for privacy and margins, but it means **monthly subscriptions should not be introduced merely to re-label an offline utility**. A realistic offering is free basic conversions and a one-time paid Pro unlock (high-value batch processing, non-destructive workflows, advanced document options); charge subscriptions only for recurring verified benefits such as genuinely delivered maintenance/service. The actual Pro SKU, country-specific prices, tax, cancellation/refund policy and store metadata are **not configured here**.

**No premium paywall or payment simulation was introduced.** On Google Play, sales of in-app digital functionality normally require Google Play Billing; verify real purchase state on a trusted backend before granting entitlements, handle PENDING / PURCHASED / refunds / cancellations and Play acknowledgements, and use RTDN when operating subscriptions. A local toggle, hardcoded `isPro=true`, fake purchase confirmation, or unverified token would harm paying users and is unacceptable.

Sources (checked 2026-10-18):
- Google Play Payments policy: https://support.google.com/googleplay/android-developer/answer/9858738
- Android Billing implementation: https://developer.android.com/google/play/billing/integrate
- Billing backend / purchase lifecycle: https://developer.android.com/google/play/billing/backend

### Prioritized acceptance gates before the app can take money

1. Make `:app:assembleDebug`, `:app:testDebugUnitTest`, lint, R8 release and APK/AAB artifact checks pass on the exact delivered source using a networked, supported Android toolchain; verify all output journeys on API 26 and API 36 devices.
2. Test imported and published file batches with **real PDFs/photos** for preserved pages, right order, thumbnails, valid outputs, correct location/open/share, and failure/rollback, including URI permission revocation, low storage, process death and Arabic filenames.
3. Validate optional yt-dlp/FFmpeg variant's real provider capability and licensing (GPL obligations), size/ABI/16 KB compliance; do not sell 'supports every platform' claims without live licensed test evidence or expect all platforms to remain stable.
4. Implement **actual** Play Billing and a purchase-verification/entitlement backend before enabling Pro access restrictions. Configure real Play product IDs and listing, refunds/restore, Play testing, customer support and privacy disclosures.
5. Run full RTL/LTR, accessibility/keyboard/TalkBack, small phones/tablets, performance/memory/battery and crash-free session tests. Record exact APK sizes separately for lite and extended media; offline services stay local.

## Exact code pointers

- `app/src/main/java/com/jumlo/app/core/OutputWorkflowPolicy.kt`
- `app/src/main/java/com/jumlo/app/core/SourceMediaType.kt`
- `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt`
- `app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/home/HomeScreen.kt`
- `app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt`
- `app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt`
- `app/src/main/java/com/jumlo/app/data/source/SourceStore.kt`
- `app/src/main/java/com/jumlo/app/data/local/ProcessingEntities.kt`
- `app/src/hostTest/kotlin/CoreTests.kt` (RED/GREEN host tests)
- `scripts/test-output-workflow-wiring.py` (RED/GREEN static checks; now invoked by `scripts/verify-host.sh`)
- `app/src/androidTest/java/com/jumlo/app/data/SourceAndPublicationTest.kt` (new unexecuted device tests)

The 45-feature historical inventory is maintained in `docs/FEATURE_STATUS.md`; this document describes only this new set of source changes and is not a claim that every legacy tool is fixed.
