# Approved execution task
Repository: /workspace/scratch/2b70f1dcaa30/work/Jumlo-main
User explicitly asks to implement the supplied plan on supplied source ZIP and return ZIP. Work locally; no remote commit/push or deployment. Preserve native Kotlin/Compose and existing user data. No invented test evidence. Your changes are shared with other agents; edit ONLY owned paths, notify parent for interface changes. Do not spawn additional agents or commit. Use meaningful behavior tests for risk-bearing changes, run available checks, retain raw logs. Distinguish passed/failed/unavailable. Report changed paths, root causes, tests, limits and follow-ups in report file.

## Ownership
core/, data/jobs/ except DraftStore.kt (parent), data/processing/, data/output/, data/source/, data/pdf/, data/files/, worker/, model/ except settings model. Find actual relevant paths. Own engine Android/host tests and new tests; coordinate scripts/test-core.sh changes with parent. DO NOT edit AppViewModel, MainActivity, UI, resources, Gradle, database/schema or docs other than report.
## Task
Fix release availability with an explicit auditable per-tool policy independent of DEBUG. UI must be able to use visible/enabled and reason consistently, including navigation-only tools. Keep advanced redaction/protection, encrypted ZIP, Google Drive and unavailable/vault/extended media hidden/blocked in standard release pending qualification. Implemented low-risk processing should work in release builds and be qualified honestly as source/host/native as evidence permits (overall release stays blocked if device proof absent). Share policy API immediately with UI agent/root. Audit all released handlers vs workspace options for data-integrity failures: safe publication, originals untouched, cancel/recovery, input MIME validation, archive malicious entries/collisions, decode budgets, URL redirect/resume integrity, path grants. Fix concrete defects with real behavior tests; no fabricated processing/progress. Root will implement dirty draft and purge UI; expose exact-id trash deletion/revalidation APIs if needed.
 — Functional completeness, reliability and data integrity

- [ ] Wire *every release-visible capability* from `ToolGridCard`/Home/deeplink to a typed workspace, validation, real job handler, progress, cancel, result, reopen, share/export, and recovery path.
- [ ] Implement a single source of truth for capability availability across Home/Tools/Downloads/Files/dispatch. Remove empty click handlers; do not show inaccessible tools as tappable.
- [ ] Use real-file fixture suites: scanned and digital PDFs, rotated/multipage/large/transparent images, HEIC where supported, protected/corrupt PDFs, archives with collisions/symlinks/zip bombs, direct transfer with HTTP partial/resume behavior and slow/error connections.
- [ ] Compare input/output bytes or structural invariants after all operations; original inputs must remain untouched. Never report success before a verified output is durably published.
- [ ] Test process death at write, finalization and publication phases; unique jobs, idempotent retries, cancellation fencing, disk full, SAF permission revocation, missing/moved files, app force stop and provider errors.
- [ ] Gate destructive operations: managed trash purge requires explicit confirmation, delete-original requires consent and provider capability verification, rename/move collisions must be previewed, export retry must not reprocess.
- [ ] Maintain the bounded local-first contract: no unsolicited uploads, external accounts optional, no fake network status, progress or document transformation. Respect licensed/authorized download scope and GPL media exclusion in the base release.
- [ ] Keep advanced PDF redaction/protection, encrypted ZIP and Google Drive transfer unavailable to release users until independent integrity/privacy/security tests pass.

**Exit:** 100% of advertised release actions have test-backed real output + error/recovery behavior. No unimplemented command can be reached by tap, share intent or direct route.


Report path: verification/production-2026-10-17/engine-report.md
