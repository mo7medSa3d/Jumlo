# Security and data-integrity review — 2026-10-17

## Trust boundaries

The app trusts its own validated Room records and app-private immutable snapshots only after checking path/identity/bytes. Incoming URIs, MIME metadata, filenames, archives/PDFs/images, remote responses, provider capability claims and persisted recovery records are inputs to validate. WorkManager/Room/publication transactions cross process and cancellation boundaries. No server-side safety service is assumed.

| Boundary / threat | Current control | Evidence / residual requirement |
|---|---|---|
| Import traversal, disappearing or oversized URI | Immutable local snapshot; safe filename; file/count/byte/free-space guards; preserve uncertain committed data on cancellation | SourceAndPublicationTest; device grant loss, slow/cloud providers and full disk still pending |
| PDF/image decompression or excessive memory | Sampled decode, per-job bitmap budget, aggregate multi-image accounting, bounded raw PDF image allocation, cancellation | Host geometry + Android pixel/structure fixtures; malformed native parsers/OOM on real devices still required |
| Output success before durable data | Stage/journal, structural checks, hash/size publication, unique logical keys, retry revalidation of actual files | Host Publication tests, Room lease SQL, Android publication source tests |
| Duplicate submissions/restarts | Durable request identity reserved before enqueue; explicit new-run reset; restored secret handle binding and waiting-stage retry | WorkspaceDraftPersistenceTest and SubmissionSecretsTest; killed-process Android scenario pending |
| Cancellation versus late worker completion | Durable lease owner, heartbeat, stage/cancel fencing; cooperative checks | SQL/core coverage; forced process kill and service lifecycle pending |
| ZIP traversal, Unicode collisions, bombs and symlink metadata | Validated paths, normalized collision naming, entry/count/expanded-byte/ratio limits; inert regular extraction rather than following symlink metadata | Real archive fixtures and red/green regressions; encrypted ZIP stays gated |
| Unsafe trash filenames / interrupted move | Fixed separate payload name and journal; durable intent; byte/hash validation; serialized publication/trash changes | Reserved metadata filenames, corruption refusal and interrupted transition host tests |
| Overbroad cleanup/purge | Exact captured IDs with confirmation; canonical/path/symlink/digest checks; cache cleanup allowlist and age limit | Real-file TemporaryCleanup/TrashTests; actual UI cancel and device filesystem behavior pending |
| Original provider deletion / overwrite | Explicit delete-original confirmation and provider flag recheck; KEEP_BOTH copy naming; reject unsafe REPLACE and identical move; verified copied bytes retained on delete failure | Shared destination policy host tests; independent SAF provider behavior pending |
| Transfer truncation/resume substitution | HTTP policy, size limits, validators/range response validation, final URL binding, digest metadata and verified publication | Real loopback tests include interruption, truncation, changed validators and redirects |
| URL/credential leakage | No raw URL/password in drafts or WorkManager; opaque handles, local code-only log; explicit export only | ErrorLog and submission secret tests; UI strings remain in memory and cannot all be zeroized |
| WebView/OAuth/redaction/protection risk | Public capability, enqueue and dispatcher gates block unqualified operations | Retained code is not a security attestation; each needs independent native qualification before enabling |
| Optional executable/media licensing | Standard graph excludes optional payload; qualification flags do not make public policy permissive | Built-artifact/graph evidence in RELEASE; external platforms/licensing remain unqualified |

The Android network security base configuration permits cleartext so the explicit HTTP choice can work; consent/downgrade checks are enforced by application policy. It must not be described as manifest-level blanket HTTP blocking. Gated WebView/media/Drive paths do not obtain public authorization from that configuration.

## Bounded work

Active limits include bitmap buffers at most `min(maxHeap/3, 128 MiB)`, archive input/expanded bytes up to 2 GiB, 10,000 archive entries, depth 32, direct transfer bytes up to 2 GiB and a 32 MiB free-space guard. Copy-based batch rename enforces its 512 MiB temporary budget. The model also declares `maxOutputFiles=1000` and `maxTemporaryBytes=512 MiB`, but these are **not global OutputStore publication caps** and must not be described as uniformly enforced. Actual handler limits are recorded in FEATURE_QUALIFICATION_MATRIX; large PDF/page-output counts remain part of native resource qualification.

These are source limits, not measured peak-RSS guarantees. Bitmap work has one dispatcher permit and direct-transfer work has two. Native libraries/OS allocations require hardware profiling.

Temporary cleanup only visits old reproducible `cache/seek` and `cache/extract` data without following links. It excludes snapshots, outputs, staging, inbound data, transfers and trash. Managed-trash corruption that predates this repair is preserved/fails closed; overwritten historic bytes cannot be invented or repaired by a migration claim.

## Release gates

Keep protection, redaction, encrypted ZIP, Drive, media, searchable OCR, webpage rendering and unimplemented vault/scanner entries unavailable. Independently test actual PDF hidden/nested content, cryptographic round trips, provider consent/revocation/collisions, redirect/private-network behavior in the intended product scope, malicious archives, codec inputs and credential lifecycle before changing policy.

Run API 26/36 foreground/background, notifications, force stop, app recreation, low disk, actual old-app update and signed release installation. Existing host crash fixtures simulate interrupted disk states; they are not power-loss or Android process-kill results. The authentic Room v1 identity remains unavailable and blocks that release criterion. Do not treat source review or native compilation as zero-risk/complete-security proof.
