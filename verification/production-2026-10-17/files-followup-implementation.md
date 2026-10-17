# Files follow-up implementation and verification — 2026-10-17

## Role and scope

After completing the independent review, the reviewer received a separate implementation assignment for two Files P2 findings. This document records that implementation and its tests; it is **not an independent review of the implementer's own changes**. The earlier five-P1 review and its eleven-test checkpoint remain historical evidence in `review-report.md` and `review-focused-jvm.log`.

Owned changes are `data/files/DocumentModels.kt`, `DocumentTreeStore.kt`, `ui/files/FilesScreen.kt`, the existing provider-rename dialog block in `JumloNavHost.kt`, isolated EN/AR `files_recovery.xml` resources, and the focused test files. Navigation shell/fold work and workspace content settings were implemented by other agents.

## Behavior changed

- `DocumentEntry` and `DocumentListing` are now pure immutable value types in `DocumentModels.kt`. Rename/delete availability combines the provider's support flag with the application's current write access. A read-only grant cannot advertise either mutation merely because the provider supports it.
- `ProviderFileRow` shows overflow only when its menu contains an actual action. An unreadable directory no longer exposes an empty menu; file mutation entries use the effective capabilities.
- `ProviderRenameDialog` observes the current listing and renders the exact NFC-normalized filename that it submits. Empty/unsafe/oversized names, existing sibling names, an unchanged name, missing source access and unavailable rename permission keep confirmation disabled. EN/AR explanatory text is provided, while filenames retain LTR presentation.
- The dialog does not replace execution-time checks. The writer still lists the current children, validates the name, rejects collisions, rechecks write access and provider support, and checks the rename result. Its collision comparison now also handles canonical Unicode equivalence, using the same comparison as the preview. Delete-original confirmation, source-deletion checks and copy verification are preserved.

## Executed evidence

1. **Permission regression, before implementation:** the actual pre-change `DocumentEntry` declaration was extracted verbatim into a temporary Android-free compilation unit. `DocumentEntryPermissionsTest` ran **3 tests, 1 failed**, showing that a read-only source still advertised provider-supported rename. Raw output: `files-permissions-red.log`.
2. **Rename policy red check:** the ten new rename tests were run against a temporary test-only representation of the previous dialog's unconditional raw-text confirmation. **10 tests, 8 failed.** This is policy-level evidence, not execution of the previous Android dialog. Raw output: `files-preview-red-policy.log`.
3. **Current production model tests:** Kotlin 2.0.21 compiled the real `DocumentModels.kt`, `Validation.kt` and `CapabilityPolicy.kt` with JUnit 4.13.2. **13 tests passed, exit 0**; no Android stubs were used. Raw output: `files-focused-jvm.log`. The initial standalone compiler invocation omitted the existing `CapabilityPolicy` dependency and consequently could not load the test classes; that harness setup failure is retained in `files-focused-setup-error.log` and is not counted as a code-regression result.
4. **Combined supplemental production run:** the current workspace/secret tests and Files model tests compiled together using the real serialization compiler/runtime. **33 tests passed, exit 0** (20 workspace/secret tests plus 13 Files tests). The exact compiler arguments, source hashes and output are retained in `review-supplemental-jvm.log`.
5. **Existing complete host command and resumed checks:** `scripts/verify-host.sh` under the default sandbox produced 96 passing core tests and 12 local-HTTP fixture failures because socket access was denied (`files-host-suite.log`). Re-running with loopback access produced **108 passing core tests, 8 passing media guards and 9 passing SQLite tests**, then stopped on one source-consistency failure in the concurrently authored `app/src/hostTest/kotlin/HomeLayoutPolicyTests.kt`: its comment-only placeholder did not start with the required package declaration. Raw output: `files-host-suite-loopback.log`. The owner replaced that marker with a valid package declaration and comments pointing to the actual standalone policy tests. The resumed **12 source-consistency tests passed**, and both existing launcher/preflight scripts passed all four checks (`files-source-static-final.log`, `files-host-launchers.log`). All host-command stages therefore passed across these runs; the interrupted initial command itself is not represented as a single successful invocation.

## Authored Android tests and limits

`ProviderFilesInteractionTest` contains four tests against the actual production components: read-only row actions, empty directory overflow, collision repair followed by exact normalized-name submission, and permission changes while the rename dialog is open. These tests were authored, not executed on Android. Final native compilation/unit/lint/release outcomes belong to the build agent's final reports. Real SAF-provider behavior, permission-revocation timing and installed interaction/accessibility acceptance remain device gates.

Files production source was frozen after the focused tests. The independent supplemental review of the other agent's workspace changes is recorded separately in `review-report.md`.
