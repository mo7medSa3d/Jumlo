# File Operations, Exports and Safe Archives Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans after the plan set is reviewed. Steps use checkbox (`- [ ]`) syntax. Do not start a separate application.

**Goal:** Make file actions change actual bytes safely without confusing history, cache and content.

**Architecture:** DocumentOperations separates managed files from provider documents. Archive handlers stream through the same source/output services.

**Tech Stack:** The pinned native Android stack and qualified adapters in the root plan.

**Spec:** `../../specs/2026-10-16-completion-design.md`

## Global Constraints

Inherit every constraint, fixed build version, shared interface and verification rule in `../2026-10-16-completion-plan.md`; it travels with this plan. In particular: preserve originals; no fabricated success; no unapproved local downloads; no font binaries, credentials or caches in deliverables. The exact approved spec is linked above.

## Review Focus

The five cross-cutting failure classes are recorded once in the root plan. Their owning regression tests are assigned to this plan's tasks below; do not substitute generic happy-path tests.

---

### Task A01: Actual browsing, provider-aware mutation and export

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/repository/UniversalRepository.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/files/FilesAndVaultScreens.kt`
- Modify: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/java/com/universalutilities/app/data/files/DocumentOperations.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/files/FilesViewModel.kt`
- Create: `app/src/main/java/com/universalutilities/app/data/files/SafeNames.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/files/DocumentOperationsTest.kt`
- Test: `app/src/test/java/com/universalutilities/app/files/SafeNamesTest.kt`

**Interfaces:**
- Consumes: F02 source capabilities, F03 output journal and source IDs.
- Produces: `DocumentOperations.list(locationUri: String): List<SourceRef>`; `copy(sourceId: String, targetUri: String, policy: CollisionPolicy): ItemOutcome`; `move(...)` with the same parameters; `rename(sourceId: String, basename: String): SourceRef`; `delete(sourceId: String): Unit`, all suspend.

- [ ] **Step 1: Write failing regressions.** `failedMoveKeepsSource`, `readOnlyProviderHasNoWriteAction`, `historyClearKeepsBytes`, `cacheClearKeepsDraftInputs` and `unsafeOutputNameRejected` use a controlled provider with injected write/delete failures.

```kotlin
assertTrue(sourceStillExistsAfterFailedMove)
assertEquals(originalHash, hashAfterHistoryClear)
assertThrows(IllegalArgumentException::class.java) { validateBasename("../outside.pdf") }
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.files.DocumentOperationsTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Use actual content URIs and provider capabilities. Keep-both reserves a new name; Replace is explicit and provider-aware. Copy verifies written byte count/hash where readable; delete source only after verified Move and user intent. A provider that cannot support required verification reports Copy complete / source retained rather than false Move success. Share only approved FileProvider paths. Lists/search/sort derive from accessible records. Add filename constraints without treating Android names as universal filesystem paths.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement safe file actions and export`.

### Task A02: Bounded real ZIP creation and extraction

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/ArchiveAndFileEngine.kt`
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/PureDomainAlgorithms.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/files/FilesAndVaultScreens.kt`
- Create: `app/src/main/java/com/universalutilities/app/data/files/ArchiveBudget.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/ArchiveSettings.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/files/ArchiveRoundTripTest.kt`
- Test: `app/src/test/java/com/universalutilities/app/files/ArchivePathPolicyTest.kt`

**Interfaces:**
- Consumes: F03 staging/publish, A01 safe names and provider byte sources.
- Produces: `ArchiveAndFileEngine.createZip(request: JobRequest, context: ExecutionContext): List<ItemOutcome>` and `extractZip(request: JobRequest, context: ExecutionContext): List<ItemOutcome>`.

- [ ] **Step 1: Write failing regressions.** `roundTripMatchesEveryEntry`, `rejectTraversalAndDuplicateTargets`, `streamLimitIgnoresFalseHeaderSize` and `cancellationPublishesNoIncompleteEntry` use small generated archives and an injected 32 KiB expanded budget.

```kotlin
assertEquals(inputHashesByPath, extractedHashesByPath)
assertFalse(outsideFileExists)
assertEquals("ARCHIVE_LIMIT", oversizedResult.errorCode)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.files.ArchiveRoundTripTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Stream real inputs; missing sources fail. Enforce path normalization/confinement, no symlink restoration, no traversal/absolute entries, no accidental duplicate-target overwrite, actual expanded byte/entry/depth budgets and safe rollback. ZIP levels 1/6/9 report real final size, not predicted savings. Validate CRC/data and complete entries; encrypted/unsupported archives get honest errors until E06 qualifies a codec. Never write explanatory text as file content.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement safe byte preserving zip workflows`.
