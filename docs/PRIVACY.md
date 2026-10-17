# Jumlo privacy — standard distribution, 2026-10-17

This describes the currently available source paths. It is an implementation statement, not an independent privacy certification or a pre-filled Play Console submission.

### 2026-10-17 security follow-up (not an audited Android APK)

Legacy Room `download_tasks.fullUrl` was capable of holding full signed URLs.
New public DAO `insert`/`insertAll` flows blank the old column before writing,
and the provided database builder calls `LegacyDownloadPrivacyCallback` on open
to clear existing rows while preserving progress and status. The actual SQL
operation passed a host SQLite test. A persistent Room reopen/migration test was
authored but could not run without Android. A previously persisted signed URL may
remain in old SQLite pages, backups, WAL or user-owned exports; do **not** claim
forensic erasure or a guaranteed privacy certification from these changes.

The internal media WebView remains **unqualified and hidden**. Android WebView
may persist website session cookies to its own storage; it must not be promoted
to a publicly supported account-gated downloader until session persistence,
cleanup, process death and unauthorized data reuse are tested and resolved.
An explicit EN/AR rights checkbox now defaults off, and the executor checks the
persisted acceptance again. This is not a platform terms or copyright audit.
Google Drive signed-in account/API operations remain inaccessible by policy;
strict URL and create-new output checks are source and host-only qualifications.

## On-device data

App-private storage contains imported snapshots, Room job/output records, saved workspace settings, auto-recovery, managed results, resumable transfer files, caches and trash. DataStore stores appearance, language, motion, haptic and connection preferences. Settings reads Android's actual notification status. Exports selected by the user remain in the external provider chosen by that user.

Backup is disabled by the manifest/data-extraction rules. There is no Jumlo cloud identity, membership tier, automatic cloud sync, analytics or crash-telemetry service.

The local error log accepts stable codes, optional tool IDs and timestamps. It rejects arbitrary exception text/URLs/paths rather than claiming to sanitize every possible user string. `core/ErrorLog.kt` rotates at a default 256 KiB threshold with one prior file; one append can cross the threshold before the next rotation. Export happens only on the explicit Settings action.

## Sensitive values and recovery

Raw direct-download URLs and session credentials live in process memory through `EphemeralSecretStore`; requests persist opaque handles. Workspace serialization clears password/token fields. A SHA-256 submission fingerprint supports duplicate prevention; raw direct URLs are not stored in that checkpoint. UI text remains ordinary in-memory strings while entered: this is not a claim that every memory copy can be deterministically erased.

After process death a transfer can require URL re-entry. Saved drafts preserve settings and file references, not a logged-in session. Export metadata survives picker recreation through saved state; export retry does not rerun the document transformation.

## Network and external applications

Visible network tools perform single/batch direct downloads initiated by the user. Requests go directly to the entered endpoint and allowed redirects. HTTPS is the default. Unencrypted HTTP requires explicit per-job consent; credential-bearing HTTP and HTTPS-to-HTTP downgrade are rejected by policy. The endpoint's own logging and data practices are outside Jumlo's control.

Google Drive, media extraction, webpage capture and searchable OCR are gated in this delivery. Retained source is not a publicly available feature; media flags do not override the capability policy. No account-connect/browser-login button promises an unavailable service.

Ordinary local document/image/archive processing does not upload document content. Explicit sharing, opening and exporting grant the chosen external application/provider access to the selected file.

## Permissions

| Permission/capability | Purpose in source |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | User-initiated direct downloads and connection constraints |
| `POST_NOTIFICATIONS` | Runtime-permitted job progress notifications |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Long-running work via WorkManager |
| `CAMERA`, optional hardware | Retained internal scanner implementation; no enabled public catalog entry |
| SAF grants | Documents/folders explicitly selected through Android's picker; provider checks still apply |
| Non-exported FileProvider | Per-URI read grants when sharing/opening managed results |

No storage-wide read, contacts, location, microphone or biometric permission is declared. The unused VIBRATE declaration was removed; native view haptics obey the persisted preference and platform settings.

## Deletion

Moving a managed result to trash preserves a journal and recoverable payload. Restore verifies its integrity. Permanent deletion confirms a captured set of IDs; later arrivals are excluded. Original provider-file deletion is a separate confirmed operation requiring delete capability.

Temporary cleanup removes only reproducible seek/extraction cache older than seven days. It never silently empties trash or clears original snapshots, completed outputs, resumed-transfer data or publication staging.

## Evidence boundary

API 26/36 permission/revocation, foreground/background, provider and release-manifest scenarios require installed-app verification. See SECURITY_MODEL for threat boundaries and RELEASE for actual commands and outstanding gates.


## Diagnostic retention correction — current ZIP

The local error log still accepts only a timestamp, code and optional safe tool ID. This follow-up exports the previous rotated log followed by the current log **only** after the explicit export action. Clearing that log removes both files, rather than leaving a silent `.1` archive behind; a 110/110 Kotlin host regression run includes the rotation/privacy tests. The prior document's statement that the error log has one archive remains true. User-exported files in an external provider are no longer app-controlled and must be deleted through that provider separately.

No new runtime Android permissions were introduced in this follow-up. The existing Camera permission stays tied to internal gated scanner code, not an assertion of a verified public scanner. No Play Console data-safety submission has been executed for the updated binary (none was built here).
