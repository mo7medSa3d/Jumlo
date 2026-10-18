# SDD ledger - plan: docs/approved/2026-10-16-completion-plan.md

Ruling: Work in an isolated extracted copy, without modifying the uploaded archive or any remote repository.

F01: PARTIAL. Offline preflight regression passes (2 checks). Baseline wrapper failed before compilation. Gradle/Android SDK unavailable; DNS denied in this runner. No Android build is claimed.
Ruling: No SDK/emulator/toolchain download or installation on the user's machine. Existing host Kotlin/JDK run dependency-free production-core tests; this is not Android qualification.
Pre-flight: F02 source IDs -> F03 jobs -> F04/I01/A02/D01 handlers all consume opaque IDs; source display names never become identity.
Pre-flight: Publication cannot be atomic with Room; the durable file journal remains authoritative until Room reconciliation.
Core groundwork: 31/31 host checks pass after observed 0/31 red on unimplemented contracts. Real ZIP IO, byte/path/memory budgets, SHA256, page selection, geometry, resume validation, publication/recovery. These are partial task checks, not completion of all named Android tests.
Root cause fixed: a rejected conflicting retry left disposable staging behind, causing later recovery to reject an otherwise valid published output. Conflicting staging is removed while the original digest/output is preserved.

Source/job/output contracts and explicit migration added; Android execution remains blocked.

HTTP core: 9 real loopback HTTP tests RED -> GREEN (40/40 total). The first draft used two overly broad exception assertions; tightened them and reran the red suite before implementation.
Review fixes: case-insensitive keep-both, PAD canvas without upscaling, and nonempty ZIP directory bodies: 3 failing behavioral tests -> 43/43 core suite passing.
Ruling: Each intake now creates an immutable local snapshot, including persistable URIs. Reimport cannot mutate earlier jobs. Cost: additional app-owned storage; no implicit cleanup of source originals.
Android intake/archive tests were written first, but execution is BLOCKED at the offline preflight, not a passing Android test or demonstrated Android red/green cycle.
Android byte handlers: added SourceStore/OutputStore backed ZIP create/extract, SHA-256 and download handlers. Existing 43 core tests exercise the shared byte engines; Android adapters and UI are NOT device-verified.
Ruling: Resume URL re-entry must match the in-memory URL's persisted digest. Changed/renewed signed URLs require a new download instead of guessing representation identity. Cost: conservative restart for expired signed links.
Ruling: Removed prototype screens, repository, engine implementations and fake service from the production source set. Reused the original Quiet Precision theme, dock, cards and tool identities for real-data workspaces. Cost: the approved detailed per-screen UI parity is incomplete; this is not a claim of U01 completion.
Ruling: Debug builds may exercise implemented candidate handlers; release route gates remain closed until Android qualification. Structural PDF edits, vault and unimplemented expansions never have an enabled fallback.

Additional domain verification: included the real legacy DTO/domain files in the host compilation, caught and fixed two syntax errors introduced while removing fabricated defaults, then observed 4 behavior failures (scheme case, fabricated preflight metadata, malformed host, reversed ranges). Fixed all four; 47/47 production-core/domain checks now pass.
Publication metadata: observed 47 passing / 2 failing behavior checks, then 49/49 passing. Validated metadata is part of the durable journal and immutable output identity. Recovery no longer attempts to parse raw downloads/archive members solely because of a claimed MIME type.
Launcher regression: the first shell-test draft used an AND-list incorrectly under errexit; its apparent pass was INVALID. Corrected the harness, observed original launcher execute the trap JVM (exit 99), then fixed launchers. 2/2 launcher checks pass. Official wrapper JAR is still absent; opted-in, checksum-pinned restore scripts are supplied; no fabricated binary is included.
Foreground support: added WorkManager setForeground, private notification, non-exported persistent-cancel receiver, and a truthful worker-start failure state. Instrumentation test authored; execution BLOCKED by absent build prerequisites. No Android runtime claim.
Final review: self-review (no subagent tool). Referenced code-reviewer template was unavailable; reviewed requirements, safety, integration, tests and release qualification directly.
Review source fixes (Android verification BLOCKED): unknown provider sizes remain null; rename refuses visible sibling collisions; prompt-cancelled previews recycle undelivered frames; retry cannot release a still-owned lease; bitmap/PDF handlers share one memory admission slot; release gating also applies inside the dispatcher; cancellation receiver failures do not crash the process.
Ruling: Do not substitute a home-grown or guessed wrapper JAR. Supply strict hash-verifying launchers plus explicit restoration scripts; F01 remains partial until the official binary is present. Cost: one explicit wrapper restoration on a network-enabled build machine.
Ruling: Notification progress stays indeterminate; actual byte/page/item progress is in the activity screen. Cost: no notification percentage, rather than inventing progress.
SQL verification: executed the exact production DAO/migration SQL using host SQLite (not Room). The pre-fix retry query failed 1/8 checks; requiring leaseOwner IS NULL before retry made all 8 pass. This avoids a new attempt starting while the prior worker still cleans up.
Parser verification: overflowing range endpoints were silently reduced to the first page. Observed 1 failing behavior check; now rejected explicitly.
HTTP identity review: 2 more real loopback tests caught missing final-URL binding. Resume now additionally requires the SHA-256 of the previously observed final URL, as well as byte-range/validator/length checks. Redirect target changes cannot append data merely because ETag/length happen to match. The digest, not the signed URL, is persisted. Final core/domain/HTTP suite: 52/52 passing.
Source-level resource guard (not device-verified): PDF raster output counts are capped; OutOfMemoryError becomes a failed job instead of leaving a perpetual RUNNING record.
Typed editor regression tests authored for resize-mode ambiguity and mismatched percentage limits; Gradle execution BLOCKED. Source now validates the same 400% cap as the engine and does not require unused pixel fields in percentage/longest-edge mode. This is not a passing Gradle test claim.
Final task accounting: 41 approved tasks; 16 partial/unqualified, 25 not implemented/gated, zero fully qualified. Missing implementation and blocked native verification are separate.
Ruling: Preserve the isolated implementation branch and package its current source; no merge/push/remote CI was authorized. Cost: the handoff is a source artifact, not an integrated release.

Delivery verification: fresh recorder run passed 52 core/domain/HTTP tests, 8 host SQL checks, 8 static checks and 4 shell regression checks. All five native/wrapper requests remain BLOCKED; recorder exit 2 is intentional. The Git-only check is explicitly not applicable to an extracted ZIP. Packaging preserves this evidence and excludes repository metadata/build caches/binaries.
