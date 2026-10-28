# Privacy, permissions and distribution review

Review date: 23 October 2026. This is a source and policy review, not a Play approval. Runtime results and unresolved release gates are in [TEST_REPORT.md](TEST_REPORT.md). The full media engine remains included.

## Manifest permissions

The merged debug manifest was inspected during this execution. Release must be checked again with the final AAB/APK, including dependency-contributed permissions.

| Permission | Purpose and request point | Scope / qualification |
| --- | --- | --- |
| `INTERNET` | User-requested downloads, remote thumbnails, optional extractor service, Play-services scanner/OCR model installation, explicitly selected Drive operations | No background clipboard polling or analytics upload is configured. Network providers still receive the request and connection IP. |
| `ACCESS_NETWORK_STATE` | Actionable transfer status and scheduling | Normal permission; a stale connectivity snapshot must not reject completed local muxing/publication. |
| `POST_NOTIFICATIONS` | Progress and cancellation controls for background jobs on Android 13+ | Runtime request is tied to the relevant notification setting/flow. Denial must be tested on Android 14+. |
| `FOREGROUND_SERVICE` | WorkManager foreground jobs | The merged `SystemForegroundService` declares `dataSync`. |
| `FOREGROUND_SERVICE_DATA_SYNC` | Transfers and user-requested file import/export/processing on Android 14+ | Must match the Play Console foreground-service declaration and demonstration video. |
| `CAMERA` | CameraX document-scanning fallback | Requested only when entering the fallback. The primary Play-services document scanner does not need this app permission. Camera hardware is optional. Denial retains gallery/file import. |
| `VIBRATE` | System haptic feedback on supported actions | No user-facing haptic intensity/toggle is retained. |
| `WAKE_LOCK` | Added by WorkManager for scheduled work | Normal permission; avoid additional application-owned indefinite wake locks. |
| `RECEIVE_BOOT_COMPLETED` | Added by WorkManager to reconcile persisted work | Does not grant permission to start unrestricted foreground services at boot. Verify scheduling/timeout behavior on current Android. |
| `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX-generated signature permission for internal receiver isolation | Not a user data permission. |

There is no `MANAGE_EXTERNAL_STORAGE`, broad photos/media-read permission, legacy storage-write permission, contacts, location, microphone, account-list permission, advertising ID, accessibility service or notification-listener permission in the inspected manifest. The print-service `<queries>` entry exposes only compatible print services; it does not request visibility of all installed applications.

## Files and storage

Input is selected through the system picker, share sheet, photo picker or a persisted SAF folder grant. Imported working snapshots, drafts, job state, private validated outputs, signature PNGs and recoverable trash stay in app storage. Export/share uses a non-exported FileProvider with explicit URI grants.

Android 10+ default public publication uses MediaStore. A user-selected folder uses SAF and a persisted URI grant. On Android 8–9 the default external app-specific Downloads directory is removed with the app; a user-selected SAF destination is the durable shared-folder option. Document the distinction in the UI and verify revoked grants, provider failures, duplicates and low storage on device.

A failed public save now retains the verified private output, records `publicSaveError`, exposes a partial/failed destination state, and supports retry/export. A private file must never be described as successfully saved to an unavailable chosen folder.

Source deletion and public-file deletion are separate from trashing a managed output. Clearing temporary cache preserves originals, published results, drafts, signatures and resumable jobs. The application disables cloud backup and excludes app data from device transfer in `data_extraction_rules.xml`; users must explicitly export files they want to retain.

## Credentials, URLs and diagnostics

Submitted download URLs/secrets use the existing encrypted durable-secret store. Optional extractor bearer tokens now use AES-GCM with a dedicated Android Keystore key; migration removes the old plaintext preference atomically. Corrupt ciphertext, key loss or a failed preference snapshot clears the usable in-memory token. Changing backend origin clears the old token; same-origin path changes retain it. Requests consume one atomic endpoint/token snapshot. No production cleartext backend endpoint, embedded URL credentials, endpoint query or fragment is accepted. Debug-only loopback HTTP remains available for local service tests.

Keystore keys are non-exportable through the application API; hardware-backed storage depends on the device and is not claimed universally. Source: [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

The structured crash record contains a timestamp, fixed operation scope, exception class, fatal flag and bounded class/method frames. It excludes exception messages, URLs, query strings, tokens, filenames, document text and thread names. Two bounded private log generations are retained. The existing error-code log also remains local. Export requires a user action. The default crash handler still delegates to Android after recording a fatal event; it does not pretend a corrupted process can safely continue.

`CRASH_REPORTING_ENABLED` defaults to false. `CrashReportSink` is an integration hook that accepts sanitized events; no Firebase/Crashlytics SDK or configured upload endpoint is bundled by this change. Enabling a reporter later requires its own disclosure, SDK/data-safety review and testing. Debug StrictMode output is development-only; do not ship captured device logs or user documents in store assets.

## Network data flow

| User action | Recipient / data |
| --- | --- |
| Inspect/download a link with the default local extractor | The selected provider/CDN receives URL/path/query, necessary HTTP headers and the connection IP. Metadata and selected media are processed locally. |
| Display a remote media thumbnail | The thumbnail host receives its image request and connection IP. |
| Configure and use an optional extractor backend | The configured server receives the submitted media URL and bearer token and processes the requested media. Its operator must specify retention, hosting and deletion policies. No server was deployed in this delivery. |
| First scanner/OCR use | Google Play services may retrieve models/UI components. Document processing uses the selected Android/ML Kit APIs; consult their current data disclosures. |
| Explicit export/share or Drive handoff | Only the files/URIs selected by the user go to the chosen provider/application. The legacy Drive tool remains blocked pending its own OAuth/privacy qualification. |

Generic cleartext HTTP file downloads remain a deliberate compatibility path controlled by transfer policy; there is no global HTTP/HTTPS preference or separate per-job HTTP-consent dialog. HTTP requests and their contents are not encrypted in transit. HTTPS must not silently downgrade, and bearer credentials must not cross origins. Optional production extractor backends require HTTPS. The broad Android network-security setting needed for generic HTTP links is not evidence that every network caller may use HTTP.

## Play policy and platform gates

| Risk | Implementation / compliant path | Release gate |
| --- | --- | --- |
| Social-platform downloads and intellectual property | Keep the extractor interface and full engine. Limit marketed/authorized use to content the user owns or has explicit downloading rights to; use provider-approved APIs/licensed integrations where required. Moving yt-dlp to a server does not resolve rights or terms. | Obtain a documented platform/rights review and confirm Play eligibility for the enabled experience. A disclaimer alone is insufficient. Do not market downloading copyrighted content without permission. |
| Foreground-service time budgets | User-visible jobs, typed `dataSync` service, cancellation, durable progress and explicit resume/retry state | Android 15+ background `dataSync` time limits and Android 16 job quotas require device testing. Consider user-initiated data-transfer jobs if measured workloads exceed WorkManager quotas; do not promise unlimited background execution. |
| Target API | `compileSdk=36`, `targetSdk=36`, `minSdk=26` | Recheck Play requirements at upload. Current requirement for ordinary new apps/updates is API 36. |
| Native runtime and 16 KB pages | Pinned full ARM64 native payload; repacker and `inspect-media-artifact.py` validate nested ELF alignment/closure | Final artifact inspection plus actual ARM64 16 KB device execution. Static ELF checks do not establish runtime compatibility. |
| Data safety and privacy policy | Local-first processing, minimal permissions, encrypted credentials, no default analytics/crash upload | Publish an owner-completed privacy URL and complete the Console form against the actual shipped SDKs, providers and optional service configuration. |
| Unqualified legacy tools | Existing Webpage→PDF, Drive and vault entries remain blocked rather than being described as tested | Security/privacy/backend qualification is required before enabling them. This change did not remove previously enabled functionality. |

Primary policy references, checked on the review date:

- [Google Play intellectual property](https://support.google.com/googleplay/android-developer/answer/9888072)
- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Foreground-service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)
- [Long-running WorkManager jobs and Android 16 quotas](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [16 KB page support](https://developer.android.com/guide/practices/page-sizes)
- [ML Kit document scanner](https://developers.google.com/ml-kit/vision/doc-scanner/android)

No Play Console submission, rights determination, physical-device qualification or legal approval is claimed.

## 2026-10-28 — Direct-download custom-header redirect isolation

User-entered custom request headers can include private origin context, so during direct-file inspection their scope is now limited to the original HTTP(S) origin; redirects to other schemes/hosts/ports receive no original custom headers. WebView cookie lookup still happens for the currently requested HTTPS URL, not by copying the original host's cookie string. This source safeguard is pending GitHub CI and network/device verification; it does not establish that all external media providers respect these boundaries.

- A user-defined User-Agent may identify an account/browser profile; it is likewise not forwarded to cross-origin redirect targets. The default browser profile remains available so legitimate file redirects can proceed.
