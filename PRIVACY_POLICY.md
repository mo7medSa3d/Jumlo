# Jumlo privacy policy — publication draft

Draft date: 23 October 2026. **The owner must supply the legal operator name, privacy contact, public policy URL, effective date and any deployed-service terms before publication.** This draft describes the included default configuration; it is not a substitute for reviewing the final shipped app and SDK disclosures.

## Who operates Jumlo

Jumlo is operated by **[operator legal name]**. For privacy questions or requests, contact **[privacy email or contact page]**. Effective date: **[publication date]**.

## Files and information on your device

Jumlo lets you choose documents, images and download links. It stores working copies, processing settings, task history, file metadata, generated results, reusable signature images and recoverable trash in application storage. It also stores your appearance/language choices, selected save folder and usage counts used to order tools. These counts are used locally.

PDF/image processing and the default media extractor run on your device. Jumlo does not automatically upload your document collection to an operator server. Scanner and OCR features use Android/Google Play services; initial component or model installation may need a network connection. The included OCR recognizer supports Latin-script recognition; choosing Arabic as the app language does not enable scanned-Arabic OCR.

Files you export to a folder, share with another app or send to a cloud provider are handled by the destination you select. Its own privacy practices apply. Public exports may remain after uninstalling Jumlo. App-private files and app-specific external files are removed when Android clears the app's data or uninstalls it. Backup/device-transfer is disabled for the app's managed data; export any files you want to keep.

## Download links and network connections

When you inspect or download a link, the source service, its CDNs and thumbnail hosts receive the network requests required for that action. Those requests disclose the resource URL and connection IP and may include required headers. Providers may have their own accounts, restrictions, logging and terms. Download only material you are authorized to save.

Generic file links may use HTTP as well as HTTPS. An HTTP connection does not encrypt the request or transferred content in transit. Optional production extractor-service connections require HTTPS.

The default app includes its extractor locally. If you explicitly configure an optional extractor service, your submitted URL and service credentials are sent to that configured server, which may fetch/process the selected media. No operator-hosted extractor endpoint is configured in the delivered default build. **Before operating such a server, publish its operator, hosting region, retention/deletion policy, security practices and subprocessors here.** Removing the server configuration stops future use; it cannot erase records already retained by a third party.

## Credentials

Download credentials and optional service tokens are protected locally using Android Keystore-backed encryption. Service tokens are cleared when the configured origin changes or the saved credential cannot be decrypted. The app does not intentionally include tokens or full URLs in diagnostic logs. Security also depends on the device and the destination services; encryption is not a promise against every compromised-device threat.

## Permissions

Jumlo uses internet/network access for requested transfers and component downloads, notifications and foreground-service support for visible ongoing tasks, and system file/folder grants for the items you select. Camera access is requested only for the CameraX scanner fallback. The primary Play-services scanner manages its own capture UI. Declining camera access leaves file/gallery import available. Haptics and WorkManager may use vibration, wake-lock and boot-reconciliation permissions.

Jumlo does not request contacts, location, microphone, broad storage-management access or an advertising identifier in the reviewed build. See the complete permission explanation in the app's published support documentation.

## Diagnostics and analytics

The default build has no configured analytics or remote crash-reporting service. It keeps bounded local error records containing error codes or sanitized operation/exception-class/stack-frame information. It does not intentionally record document text, filenames, full URLs or access tokens in those records. You may choose to export diagnostics for support; review the exported file before sharing it.

An optional crash-reporting integration hook is disabled by default. If the operator later enables a reporting SDK, this policy and the store data-safety disclosure must be updated before that release, including recipient, collected fields, purpose and retention.

## Retention, deletion and your choices

You can remove managed outputs, restore or permanently empty managed trash, clear eligible temporary caches, change a save folder, remove service configuration, or clear Android application data. Temporary cleanup preserves active/resumable work and original documents. Files copied outside the app and cloud-provider copies must be deleted at their destination. The app does not automatically delete an original source when creating a converted output or a duplicate report.

Contact **[privacy contact]** for requests concerning information held by the operator. For third-party hosts or your configured extractor service, contact the relevant operator. The default app has no Jumlo account-registration system or operator cloud document account to delete.

## Children and changes

**[Owner: specify the intended audience and any required children's-privacy terms after completing the Play target-audience review.]** Do not publish an unsupported age/audience claim. Material policy changes will be described at **[public policy URL]**, with an updated effective date and any notice required for the release.

## Explicit Google Drive import/export

Jumlo includes an enabled, user-initiated Google Drive v3 transfer tool. When you select a Drive import, Google receives a request for that file and your provided access token; an import copies the selected file to the device. When you select Drive export, Google receives the contents and metadata of the selected files and the provided access token. The token is held through an ephemeral secret handle for the transfer, and the app removes it from the transfer secret store after the operation. Google controls its own retention, access and privacy practices for cloud copies. The presence of this feature is **not** a claim that its account/device integrations have passed final acceptance.

## PDF reading position (2026-10-28 source revision)

Jumlo may remember the last page you viewed in a PDF on this device. It stores a limited number of page positions under hashed document identifiers in app-private storage; it does not upload that reading history. This source text is not a claim that an external policy URL has been published or that Play Console Data safety review is complete.
