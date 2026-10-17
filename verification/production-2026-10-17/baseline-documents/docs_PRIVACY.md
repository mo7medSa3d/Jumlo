# Jumlo Privacy Policy (what the app actually does)

## Stored on this device only

- Imported file snapshots (copies under app-private storage), workspace
  drafts, job records and published outputs (Room database + app files).
- Settings (DataStore): theme, language, onboarding, network defaults,
  destination folders, notification grant flag, first-use media terms
  acknowledgement (`mediaTermsAccepted`).
- In-memory only, never persisted: PDF/ZIP passwords, signed download URLs /
  auth headers, and Google Drive OAuth2 access tokens (held only as opaque
  `EphemeralSecretStore` handles and zeroized after use), media stream URLs,
  and in-app browser session cookies. After process death these must be
  re-entered.
- Sanitized local error log (`filesDir/logs/error.log`, max 64 KiB): strips
  query strings, bearer tokens, passwords, and file system paths before
  writing; exported only when the user explicitly taps **Export error log** in
  Settings.
- Cloud backup and full backup are disabled (`allowBackup=false`,
  `data_extraction_rules.xml` and `backup_rules.xml` exclude
  database/files/sharedpref).

## Sent over the network

- Direct downloads: the exact URLs the user enters, fetched with OkHttp.
  HTTP (cleartext) only with explicit per-job consent; HTTPS downgrades and
  credential headers over cleartext stay blocked.
- Google Drive import/export (`file_drive` workspace): connects directly to the
  official Google Drive v3 API (`https://www.googleapis.com/drive/v3/files` and
  `https://www.googleapis.com/upload/drive/v3/files`) using a session-only
  OAuth2 token obtained via the Android system account picker (`AccountManager`)
  with scope `oauth2:https://www.googleapis.com/auth/drive.file`. Only files
  the signed-in user can access are downloaded or uploaded.
- Media extraction (extended-media debug builds only): the submitted
  page/media URL plus the source site's associated media/CDN requests,
  executed on-device. No conversion server is used.
- In-app browser: pages the user opens (WebView). Login cookies stay in
  the WebView and are never exported or logged.
- Webpage-to-PDF loads the HTTPS URL you enter in an on-device WebView and
  renders it locally to PDF via Android's PrintDocumentAdapter.
- Nothing else leaves the device: no analytics, no crash telemetry, no ads,
  no Jumlo cloud accounts, no cloud document processing.

## Permissions and why

- `INTERNET`, `ACCESS_NETWORK_STATE`: downloading user-requested files,
  Google Drive official API transfer, webpage-to-PDF capture, and (in debug
  extended-media builds) on-device media extraction.
- `POST_NOTIFICATIONS`: foreground download/job progress (user-granted,
  Android 13+ runtime prompt with rationale in Settings).
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`: long downloads and
  processing jobs.
- `CAMERA` (`android.hardware.camera` optional): only the user-opened
  document-scan screen (`ScanScreen`, runtime-granted). Captures stay in
  app-private cache and become local snapshots; no camera data is uploaded.
- No location, microphone, contacts, storage-wide read, vibrate, or biometric
  permission. (`VIBRATE` and `USE_BIOMETRIC` were audited and excluded because
  no feature uses them.)

## On-device ML Kit OCR

- OCR uses bundled ML Kit on-device text recognition; page images and extracted
  text never leave the device.
