# Jumlo Privacy Policy (what the app actually does)

## Stored on this device only

- Imported file snapshots (copies under app-private storage), workspace
  drafts, job records and published outputs (Room database + app files).
- Settings (DataStore): theme, language, onboarding, network defaults,
  destination folders, notification grant flag, vault auto-lock timeout.
- In-memory only, never persisted: signed download URLs / auth headers
  (opaque `EphemeralSecretStore` handles), media stream URLs, browser
  session cookies. After process death these must be re-entered.
- Cloud backup and full backup are disabled (`allowBackup=false`,
  `data_extraction_rules.xml` excludes database/files/sharedpref).

## Sent over the network

- Direct downloads: the exact URLs the user enters, fetched with OkHttp.
  HTTP (cleartext) only with explicit per-job consent; HTTPS downgrades and
  credential headers over cleartext stay blocked.
- Media extraction (extended-media builds only, debug only): the submitted
  page/media URL plus the source site's associated media/CDN requests,
  executed on-device. No conversion server is used.
- In-app browser: pages the user opens (WebView). Login cookies stay in
  the WebView and are never exported to the downloader.
- Nothing else leaves the device: no analytics, no crash telemetry, no ads,
  no accounts, no cloud document processing.

## Permissions and why

- `INTERNET`, `ACCESS_NETWORK_STATE`: downloading user-requested files.
- `POST_NOTIFICATIONS`: foreground download/job progress (user-granted,
  Android 13+ runtime prompt with rationale in Settings).
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`: long downloads.
- No location, camera, microphone, contacts, or biometric permission.
  (`VIBRATE` / `USE_BIOMETRIC` were removed: nothing used them.)

## Errors

Failures surface as on-screen messages and per-item job error codes stored
locally. There is no third-party crash reporter and no network telemetry.
