# Independent source review — 2026-10-17

## Scope and evidence boundary

Reviewed the working-tree implementation against `docs/approved/2026-10-17-production-execution-plan.md` and the execution ledger. The local source baseline is `dacdfbc938a79fccac21c6c2bf412d31f713689e`; this is the supplied-archive snapshot, not a claim about the remote repository SHA.

The focused review covered `AppViewModel`, `DraftStore`, `MainActivity`, preferences, Account & Data, Settings, temporary cleanup, and their integration with the new navigation, workspace, file/transfer rows, capability policy, publication and managed-trash code. Engine changes and authored tests were sampled independently. No implementation files were edited during that initial review. The later, separately assigned Files implementation is disclosed in the supplemental scope below and is not presented as independent review of the implementer's own code.

The initial sections record source-level findings and one independently executed, focused JVM test run; the supplement records the later scoped run. Neither is an installed-APK interaction result. The build agent owns native Gradle debug/unit/lint/release results. Android instrumented tests were inspected as authored tests; they were not executed by this reviewer. No physical device, emulator, accessibility, visual, or performance pass is claimed.

## Initial assessment — historical five-P1 remediation checkpoint

**The five P1 findings from this source review were corrected in the inspected implementation. No open P1 finding remained from that focused review.** The remediation was independently inspected, and the production settings/secret-rebinding sources at that checkpoint passed all **11 focused JVM tests**. Two follow-up integration gaps found during re-review were also corrected before closure: captioned file shares selecting a download tool, and hidden PNG quality state blocking processing.

This conclusion is scoped to the five findings and their remediation. Native Android build results, actual WorkManager/process-recreation interactions, SAF providers, accessibility and physical-device performance remain subject to their separate verification gates. No additional critical finding was identified in the sampled scope. This is not release approval or a claim of exhaustive correctness.

## Initial findings — retained as the pre-fix record

The line references in this section describe the originally reviewed code. Current remediation evidence and its limits are recorded below.

### R1 — P1: Same-identity download resubmission can discard the newly entered link

**Evidence:** `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt:259–270`, `app/src/main/java/com/jumlo/app/data/jobs/DraftStore.kt:25–31`, `app/src/main/java/com/jumlo/app/data/jobs/JobStore.kt:29–38,84`, and `app/src/main/java/com/jumlo/app/data/processing/DownloadOperationHandler.kt:42`.

The ViewModel creates a fresh ephemeral URL handle, then the durable reservation returns the previous request when its fingerprint matches. That previous request retains the old, process-local handle. Because the returned request ID differs from the candidate ID, `finally` removes the new handle. After a process restart, entering the same URL therefore cannot supply the restored request with usable credentials. This also affects the interruption window after reservation but before enqueue.

There is a second state requirement: startup recovery may already have moved the old job to `WAITING_FOR_INPUT`. `enqueue()` inserts with `IGNORE`, preserving that stage, and `execute()` returns immediately for it. Rebinding a handle alone does not restart such a job.

**Required correction:** Preserve the durable request identity while rebinding the explicitly re-entered link to its persisted handle. Explicitly retry an eligible existing waiting job, or present its recovery state accurately. Do not report an existing waiting/terminal job as newly queued. Preserve the rule that URLs are not persisted in drafts.

**Focused verification:** Exercise both interruption windows: reservation before enqueue, and a created job recovered to `WAITING_FOR_INPUT` after restart. Re-enter the same link once and check that the same job ID uses that link, without a second entry or a duplicate output.

### R2 — P1: The provider Copy action always hits the same-document guard

**Evidence:** `app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt:89`, `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt:348–351`, and `app/src/main/java/com/jumlo/app/data/files/DocumentTreeStore.kt:103–118`.

The visible Copy command passes the selected entry, its existing name, the current folder and `KEEP_BOTH`. The current folder already contains that source under that name, so `existingMatch.uri` equals the source URI. The new `SOURCE_DESTINATION_IDENTICAL` check runs before the `KEEP_BOTH` branch can select a unique name. Every normal same-folder Copy action is therefore rejected.

**Required correction:** Permit a same-folder copy under `KEEP_BOTH`, choose its unique destination name, and retain protection against writing to an existing/source document returned by a provider. Continue refusing unsafe replacement.

**Focused verification:** Copy a document within the displayed folder. Confirm a distinct document is created, both hashes match the original, the original survives, and `REPLACE` cannot overwrite it.

### R3 — P1: Generic workspace fields remain editable when the engine ignores them

**Evidence:** `app/src/main/java/com/jumlo/app/ui/workspace/WorkspaceScreen.kt:158–164,235–239`, `app/src/main/java/com/jumlo/app/model/WorkspaceDraft.kt:46–48,61,69–72`, and `app/src/main/java/com/jumlo/app/data/processing/ImageOperationHandler.kt:209–235`.

The generic form renders an output filename for every non-image tool. Tools including SHA-256, duplicate finding, PDF comparison, PDF-to-images and archive extraction do not consume this field as an output name. Image comparison renders format, quality and prefix controls even though its execution settings are fixed. Target-size compression renders a format selector although its handler always emits JPEG and chooses its own quality. These controls accept edits but cannot deliver their implied result.

**Required correction:** Make field visibility depend on the settings actually consumed by each handler. Where an output policy is fixed, explain it with text instead of an ineffective editor. Apply the same eligibility to essential and Advanced fields.

**Focused verification:** For the affected tools, assert that ignored editors are absent and verify that each remaining editable setting changes the execution request or has an explicitly documented policy.

### R4 — P1: Initial draft restoration can overwrite a newer editor/tool mutation

**Evidence:** `app/src/main/java/com/jumlo/app/MainActivity.kt:44`, `app/src/main/java/com/jumlo/app/ui/AppViewModel.kt:103–114,158–171,182–186`.

The UI becomes editable when preferences load, independently of the suspended workspace queries. `chooseTool()` and `updateForm()` mutate `_form` immediately. Shared-text intake also calls `setUrls()` before waiting for restoration. If an edit/tool choice occurs while the draft queries are pending, the unconditional `_form` assignment from recovery overwrites that newer state. URI import correctly waits for initialization, so the ordering discipline is incomplete rather than absent.

**Required correction:** Gate editable workspace/navigation state until recovery finishes, or serialize initial editor mutations after restoration. Move shared-text intake behind the same initialization boundary. Preserve an already loaded explicit checkpoint if recovery parsing fails.

**Focused verification:** Delay draft reads deliberately, deliver a tool choice/edit or text intake, then release the reads. Verify that the intended mutation survives and that no stale recovery snapshot replaces it.

### R5 — P1: Waiting jobs lost their retry action during row extraction

**Evidence:** `app/src/main/java/com/jumlo/app/ui/components/ProductionRows.kt:69–75`, `app/src/main/java/com/jumlo/app/ui/downloads/DownloadsScreen.kt:44–51`, and `app/src/main/java/com/jumlo/app/data/jobs/JobStore.kt:129`.

The previous `JobCard` offered retry for `WAITING_FOR_INPUT`; the new `TransferRow` excludes that stage from overflow/retry eligibility. The activity screen adds a replacement-input action only for downloads. A PDF/image job waiting on lost source access has no retry action after access is restored. A download waiting only for Wi-Fi unnecessarily requires URL entry even when its in-memory handle is still usable.

**Required correction:** Restore a meaningful retry action for waiting jobs. Keep replacement-link entry available for expired-secret/restart cases, without making it the only recovery route for every waiting condition.

**Focused verification:** Render a waiting PDF/image job and a Wi-Fi-waiting download, then invoke retry after restoring the prerequisite. Check that the same logical job is retried and completed members remain idempotent.

## Focused remediation verification

| Finding | Source-inspection outcome | Execution evidence and remaining limits |
|---|---|---|
| **R1 — corrected** | `SubmissionSecrets.kt:14–24` checks compatible request settings, copies the newly entered URL into the original persisted handle, and clears the temporary character array. `AppViewModel.submit()` invokes it before enqueue, explicitly retries an existing `WAITING_FOR_INPUT` job, and removes rebound secrets for completed/cancelled jobs. Request IDs/settings remain immutable. | Both `SubmissionSecretsTest` cases passed in the independent JVM run: restored old handle survives candidate-handle removal, and different network/cleartext policy cannot reuse the identity. Full ViewModel → Room → WorkManager process-death/lease timing was source-inspected, not executed on Android. |
| **R2 — corrected** | `DocumentTreeStore.kt:104–111` calls `CopyDestinationPolicy` before creating the document. `KEEP_BOTH` resolves a distinct name even when the requested name is the source's name; unsafe replacement and same-document moves remain blocked. The post-create URI collision guard remains. | Reviewer inspected the production policy, its consumer, real-file regressions and raw logs. `engine-copy-red.log` records the original failure (106 passed / 2 failed); `engine-copy-green-full.log` records 108 passed / 0 failed plus all auxiliary suites passing. The fixtures preserve both source and pre-existing-copy hashes. This is real local-file/policy evidence, not Android SAF-provider execution. |
| **R3 — corrected** | The workspace now renders filename/conversion/Advanced controls only for applicable tools. Image comparison has no ineffective conversion editor. Target-size processing exposes byte target/prefix and explains its fixed JPEG policy. `WorkspaceDraft` normalizes ignored target/PNG quality state while keeping meaningful JPEG and retained-EXIF quality validation. | All nine current `WorkspaceSettingsTest` tests passed independently. They include stale hidden target/PNG settings, meaningful retained-EXIF quality, invalid visible JPEG quality, defaults and resize-mode validation. The authored target-field Compose test was inspected but not executed. |
| **R4 — corrected** | `MainActivity.kt:35–46` gates the navigation/editor on both loaded preferences and `workspaceReady`. Intake waits for draft initialization. Only text-only HTTP(S) input can select a download tool; file-share captions cannot reset the active form. The explicit checkpoint is assigned to `_form` before recovery is parsed, so a recovery-read failure retains that checkpoint. | These ordering and fallback changes were verified directly in source. A delayed Room-read/process-recreation Android interaction test was not executed. The caption-routing regression noticed during remediation was corrected and re-inspected before closure. |
| **R5 — corrected** | `TransferRow` includes `WAITING_FOR_INPUT` in overflow visibility and retry eligibility. The activity screen separately offers link replacement for expired-secret/restart conditions, while ordinary waiting jobs can retry without another link entry. | `WorkspaceRecoveryInteractionTest` now contains waiting-PDF and waiting-download retry-callback tests. Their source was inspected; device execution is pending. The existing SQLite retry/lease gate is covered by the retained host suite, which is distinct from a full Android job-store interaction test. |

### Independent JVM run

The reviewer compiled the current production sources with **Kotlin 2.0.21**, its real serialization compiler plugin, **kotlinx.serialization core/JSON 1.7.3**, **JUnit 4.13.2**, Hamcrest 1.3 and `javax.inject` 1. No Android or serialization stubs were used. Production compiler inputs were `Validation.kt`, `CapabilityPolicy.kt`, `ResumePolicy.kt`, `OperationContracts.kt`, `WorkspaceDraft.kt`, `Settings.kt`, `WorkBudget.kt`, `SubmissionSecrets.kt` and `EphemeralSecretStore.kt`, together with the two existing test classes.

Executed test entry point:

```text
org.junit.runner.JUnitCore
  com.jumlo.app.model.WorkspaceSettingsTest
  com.jumlo.app.model.SubmissionSecretsTest
```

**Observed result: exit 0, `OK (11 tests)`**, comprising nine workspace-settings tests and two submission-secret tests. The raw output is retained in `review-focused-jvm.log`. That historical implementation/test hash checkpoint is preserved in `review-source-sha256-initial.txt`; later scoped evidence is recorded separately below.

The initial six-test engine settings log predates the additional PNG/EXIF cases. The independent eleven-test run is the evidence for those final cases. Neither this run nor the source review substitutes for the full native build or device qualification.

## Reviewed improvements and remaining gates

- Managed-trash confirmation captures exact IDs; newly arriving trash is not silently added to the confirmed deletion set. Storage revalidates entries before destructive deletion.
- Temporary cleanup is limited to old `cache/seek` and `cache/extract` entries. It excludes sources, published outputs, job staging, resumable transfers and managed trash, and it avoids following symlinks.
- Export-picker targets and diagnostic-export intent are held in `SavedStateHandle`; the source export checks for an identical URI and verifies the target's byte count/hash. An on-device recreation test is still required.
- Account & Data uses a truthful local-workspace presentation, and its visible destinations are connected to Files, Settings and informational dialogs.
- Availability is shared by catalog and dispatcher, while sensitive unqualified handlers remain blocked. Actual device qualification must remain distinct from source availability.

The absence of device hardware is a verification limitation, not a code defect. The plan's API 26/36 interaction/migration, real-device performance, accessibility, visual acceptance and final release gates remain pending unless separately supported by actual results. Native build evidence must describe the final source revision, and authored instrumented tests must not be reported as executed tests.

## Supplemental scope — plan completeness and workspace remediation

The later plan comparison identified local favorites/ranking and fold/hinge handling as explicit unfinished requirements (approved-plan lines 93 and 134), plus three actionable Files/workspace usability gaps. A fully aggregate immutable `UiState` remains a reasonable architectural follow-up: plan line 143 states a preference and requires profiling before optimization, while the extracted screens already use targeted lifecycle-aware Flow collection.

The same reviewer was then assigned to implement effective Files capabilities and provider rename preview. Those changes and their tests are recorded in `files-followup-implementation.md`; no independent-review claim is made for that implementation. Favorites/fold work and final native gates remain under their separate owners' reports.

### Independent inspection of the parent's frozen workspace remediation

No new P1/P2 issue was found in this scoped change:

- **Required content is visible and validated before submission.** `WorkspaceScreen.kt:205–215,308–325` exposes watermark text/logo selection, annotation text/page, stamp text/page-number selection and batch rename pattern/start in essential settings. `WorkspaceDraft.kt:55–61,73–85` rejects missing required text, fewer than two distinct logo inputs and oversized rename patterns. The existing main readiness expression evaluates those serialized settings before enabling submission. The meaningful settings still map to their actual image/PDF handler inputs; hidden watermark text is not required in logo mode.
- **Batch rename now has an actual pre-submission preview.** `WorkspaceScreen.kt:141–158` calls the production `PureDomainAlgorithms.batchRenamePreview` on `Dispatchers.Default`, binds the result to its current input names and serialized pattern/start, and requires a matching successful result before enabling the primary action. Inline names and the scrollable full mapping at lines 174 and 252–265 consume that same result. The handler also uses the same production naming algorithm, at `FileExtrasHandler.kt:46–48`; original files remain inputs to a copy operation.
- **Evidence stays separated.** The parent's `workspace-essentials-red.log` and `workspace-essentials-green.log` were inspected. The latter records 20 passing actual production JVM tests. The reviewer then independently compiled those current sources together with the Files model tests using Kotlin 2.0.21, the real serialization compiler/runtime, JUnit 4.13.2, Hamcrest and `javax.inject`: **exit 0, `OK (33 tests)`**, comprising 20 workspace/secret cases and 13 Files cases. Exact arguments, source hashes and output are retained in `review-supplemental-jvm.log`.

The added `ContentToolSettings` Compose tests were inspected as authored source, not executed Android evidence. No delayed Compose/Room/WorkManager timing, real provider, visual or accessibility acceptance is inferred from the JVM run. Supplemental source hashes were captured after the scoped review in `review-supplemental-source-sha256.txt` and the refreshed current `review-source-sha256.txt`; the original eleven-test checkpoint remains preserved separately. These current manifests cover the 23 listed supplemental files, not a claim of independent review of unrelated concurrent navigation/favorites changes.

The existing host stages were also exercised: 108 core, eight media guards and nine SQLite tests passed with local loopback access. One comment-only host-test marker tripped the source consistency rule; after its owner supplied a valid package declaration, all twelve source checks and both launcher/preflight scripts passed. Raw initial failures and final stage results remain in the separately named `files-*` logs, as explained in `files-followup-implementation.md`. These are host results, not native Android execution.
