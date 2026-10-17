# Execution ledger — plan: docs/approved/2026-10-17-production-execution-plan.md

Source: supplied Jumlo-main(2).zip, SHA-256 37c3d4445c6455a1c0da2cd036fd6b597781a4eea4944bba4b0ee6eca794952e. No claim that the ZIP matches remote main or audited SHA. Local snapshot commit is for diff review only.

## Decisions
- Ruling: execute supplied archive and return ZIP; no remote push — current explicit user request governs the older plan's main-branch procedure. Cost: CI for a newly pushed SHA cannot be produced in this archive-only task.
- Ruling: supplied plan constitutes implementation/design authorization; preserve its native local-first scope and proceed without further approval.
- Ruling: isolated extracted copy with local baseline snapshot provides source protection; a second worktree is unnecessary.
- Ruling: physical-device evidence cannot be fabricated; attempt available native gates and report remaining release gates accurately.

## Interface preflight
| Producer | Consumer | Decision |
|---|---|---|
| Capability policy | Home/tools/workspace/dispatcher | Keep enabled(id); add explicit availability metadata consistently. |
| ViewModel draft APIs | Workspace navigation | Parent owns state/draft; UI agrees callback contract before integration. |
| OutputStore trash | Files + Settings | Confirm captured exact IDs and revalidate deletion; originals excluded. |
| Theme/resources | All new screens | UI agent owns production_ui.xml; parent uses account_settings.xml. |
| Gradle/schema | All application code + tests | Build agent owns configuration and later reports source diagnostics. |

## Work status
Source, engine, UI and state repairs are implemented. Five independent P1 findings were corrected and re-reviewed. The additional source work includes local ordered Home choices, physical hinge avoidance, provider-aware file actions, exact rename previews and essential workspace validation. Native diagnostics prompted narrow processor compatibility repairs, CameraX 1.4.0 for the observed ELF64 alignment defect, and replacement of restricted window-layout constructors with public flow data.

The actual core host suite passed 108 cases. Expanded workspace/secret tests passed 20, Files policy tests passed 13, and the combined independent check passed the same 33 cases. Home/fold policy tests passed 11 and cleanup filesystem tests passed 6. These are scoped counts, not an additive count of unique tests. Host media/SQLite/source/launcher checks and red/green evidence are retained in the individual reports. The independent lint-warning triage found no additional actionable P1/security/data-loss defect in its inspected scope; see `lint-warning-review.md`.

Final native command outcomes, actual JUnit XML, artifact inspections and hashes are recorded under `build/final/` and summarized in `build-report.md` and `docs/RELEASE.md`. Earlier successful and failed diagnostics are separate evidence, not a blanket final-source pass. Physical-device and historical-install criteria remain open as described below.

Additional rulings:
- Authentic Room v1 was not found in supplied source or historical paths. Keep the gap explicit and use a labeled structural fixture, never fabricated v1 identity.
- OCR/webpage plus high-risk cryptographic/Drive/media and unimplemented entries remain blocked despite retained internal source.
- No physical benchmark, installed API26/36, exact-remote-SHA CI or private signing evidence can be asserted on this host.
- The current registry has 45 definitions: 32 processing, 4 navigation/viewer and 9 gated. Tools shows 33 entries after excluding 3 duplicate navigation helpers.
- Numeric WorkBudget properties alone are not global OutputStore caps. Qualification docs state actual handler-enforced limits and remaining native resource tests.
- A native build encountered a transient `.rsync-tmp` path while the workspace synchronized generated outputs. Only local generated build directories/project-state cache were relocated to `/tmp` by a recorded scratch init script. Delivered Gradle configuration and normal CI output paths do not depend on that environment workaround.
- Release packaging reproduced invalid method flags on class entries in the three original handwritten ART profiles. Each original and its hash are preserved; all three active files were replaced in place with comment-only pointers and zero rules because unlinking had been undone by workspace synchronization. Only affected packaging gates were repeated after this profile correction; no measured profile was invented.
- The package is a source delivery. It excludes local toolchains, build caches, private keys and generated application binaries while retaining actual verification logs and scripts.
