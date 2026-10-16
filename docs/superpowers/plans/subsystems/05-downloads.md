# Persistent Direct Downloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans after the plan set is reviewed. Steps use checkbox (`- [ ]`) syntax. Do not start a separate application.

**Goal:** Download real authorized resources with accurate status and conservative resume semantics.

**Architecture:** Transport and scheduling are separate. One stream per file, two concurrent files by default; Room owns recovery state.

**Tech Stack:** The pinned native Android stack and qualified adapters in the root plan.

**Spec:** `../../specs/2026-10-16-completion-design.md`

## Global Constraints

Inherit every constraint, fixed build version, shared interface and verification rule in `../2026-10-16-completion-plan.md`; it travels with this plan. In particular: preserve originals; no fabricated success; no unapproved local downloads; no font binaries, credentials or caches in deliverables. The exact approved spec is linked above.

## Review Focus

The five cross-cutting failure classes are recorded once in the root plan. Their owning regression tests are assigned to this plan's tasks below; do not substitute generic happy-path tests.

---

### Task D01: Streamed GET, secure redirects and representation-safe resume

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/DownloadEngine.kt`
- Modify: `app/src/main/java/com/universalutilities/app/data/engine/PureDomainAlgorithms.kt`
- Modify: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/java/com/universalutilities/app/data/download/HttpTransfer.kt`
- Create: `app/src/main/java/com/universalutilities/app/model/DownloadSettings.kt`
- Test: `app/src/test/java/com/universalutilities/app/downloads/HttpTransferTest.kt`

**Interfaces:**
- Consumes: F03 staged bytes/output integrity and A01 safe output names; existing OkHttp 4.12.0.
- Produces: `HttpTransfer.inspect(url: String): Preflight`; `transfer(request: DownloadRequest, progress: suspend (Long, Long?) -> Unit): TransferResult`, both suspend. DownloadRequest includes destination partial file, byte offset, validators, expected representation and explicit network/security policy.

- [ ] **Step 1: Write failing regressions.** `headFailureDoesNotInventSuccess`, `valid206AppendsAtExactOffset`, `range200RestartsSafely`, `wrongRangeOrChangedValidatorRejects`, `unknownLengthStaysUnknown` and `redirectDropsSecrets` run against loopback MockWebServer 4.12.0 (test-only dependency, approval if not cached).

```kotlin
assertEquals(referenceSha256, hashDownloadedFile)
assertNull(unknownLength.totalBytes)
assertEquals("RESTART_REQUIRED", changedResource.code)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:testDebugUnitTest --tests 'com.universalutilities.app.downloads.HttpTransferTest'`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** HEAD is optional; actual GET streams through a bounded buffer. For ranges request identity encoding, validate Content-Range/start/end/length, 200/206/416 and strong validators; weak ETags are not used as strong resume proof. An ETag is not a cryptographic digest. Never carry authorization across origins, bypass TLS, or accept HTTPS downgrade silently. HTTP needs per-transfer consent and no credentials. Recheck budgets throughout reads; failures preserve safe partial state but publish no success.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: implement real downloads and safe resume`.

### Task D02: Durable queue, OS scheduling, notifications and UI

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/service/TransferForegroundService.kt`
- Modify: `app/src/main/java/com/universalutilities/app/worker/EngineOperationWorker.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/downloads/DownloadsWorkspaceScreens.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/universalutilities/app/data/download/DownloadCoordinator.kt`
- Create: `app/src/main/java/com/universalutilities/app/service/UserTransferJobService.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/downloads/DownloadsViewModel.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/downloads/DownloadRecoveryTest.kt`
- Test: `app/src/test/java/com/universalutilities/app/downloads/DownloadQueueTest.kt`

**Interfaces:**
- Consumes: D01; persisted job IDs, validators and actual partial files.
- Produces: `DownloadCoordinator.enqueue(settings: DownloadSettings): String`, `pause(id: String): Unit`, `resume(id: String): Unit`, `cancel(id: String, discardPartial: Boolean): Unit`, all suspend; observed Room-backed snapshots.

- [ ] **Step 1: Write failing regressions.** `restartReconcilesPartialLength`, `twoFileConcurrencyLimit`, `pauseCancelsSocketWithoutDeletingOriginal`, `forceStopDoesNotClaimContinuousWork`, `wifiAndMeteredRulesDiffer` and `notificationDeniedKeepsOfflineTools` verify state/policy boundaries.

```kotlin
assertTrue(observedConcurrentTransfers <= 2)
assertEquals(actualPartialLength, recovered.downloadedBytes)
assertFalse(completedStateBeforeVerifiedPublication)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.downloads.DownloadRecoveryTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Use API-34+ user-initiated transfer jobs where eligible; older Android uses a user-started dataSync foreground service with declared permissions and applicable timeouts. Scheduled/deferrable work uses WorkManager constraints, not an always-running promise [R5/R6]. Persist pause/cancel reasons and retry bounds (maximum three transient automatic attempts, then Needs attention). Wi-Fi transport and unmetered policy are separate. Local UI shows real bytes/progress; diagnostic protocol fields are secondary and sanitized.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: add durable download orchestration and recovery`.
