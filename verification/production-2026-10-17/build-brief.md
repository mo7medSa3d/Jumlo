# Approved execution task
Repository: /workspace/scratch/2b70f1dcaa30/work/Jumlo-main
User explicitly asks to implement the supplied plan on supplied source ZIP and return ZIP. Work locally; no remote commit/push or deployment. Preserve native Kotlin/Compose and existing user data. No invented test evidence. Your changes are shared with other agents; edit ONLY owned paths, notify parent for interface changes. Do not spawn additional agents or commit. Use meaningful behavior tests for risk-bearing changes, run available checks, retain raw logs. Distinguish passed/failed/unavailable. Report changed paths, root causes, tests, limits and follow-ups in report file.

## Ownership
Root Gradle/settings/catalog/wrapper, app/build.gradle.kts, app/proguard-rules.pro, benchmark/, .github/, app/schemas/, data/local/, androidTest DatabaseMigrationAndDaoTest.kt, scripts build/verification launcher only. Do not modify UI or engine handlers; report compilation errors there to parent. Own local Android toolchain discovery/setup in scratch; avoid modifying system. Try native build and test seriously. Before downloads verify network, storage and runtimes. Build logs in verification/production-2026-10-17/build/. Use isolated task-specific Gradle home in writable scratch.
## Task
Fix B01/B03/B05/B06. One consistent AGP plugin source, restore benchmark, default debug signing, valid release minification/resource shrinking and unsigned distinction; wrapper checksum. Recover AUTHENTIC Room v1 schema via available historical GitHub source; if not possible do not invent identity hash/schema, document and strengthen migration tests against known authentic source. Configure fast-fail CI compile+unit+lint before API26/36 instrumentation; retain artifacts/evidence; actual baseline profile tasks with no fake prewritten claims. Preserve licensing exclusion of extended media. Probe/configure/build initial source; do final build after integration when parent requests.
 — Make native build & CI trustworthy (P0)

- [ ] Resolve `com.android.test` AGP version/classpath conflict with one consistent plugin source, then include `:benchmark` again and verify Gradle configuration succeeds.
- [ ] Check Gradle wrapper integrity and actual wrapper invocation; verify compatible AGP/Kotlin/KSP/Compose/Hilt/Room versions rather than updating everything blindly. Review Compose BOM `2024.11.00` for modernization after stabilization.
- [ ] Verify tracked Room v1 schema against authentic prior app schema and run migration tests on SQLite and Android Room. Correct documentation.
- [ ] Fix debug signing: use reproducible default debug keystore or CI-generated temporary key. Keep release signing secrets out of repository.
- [ ] Run separate failing commands: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleRelease`, and `:app:connectedDebugAndroidTest` on API 26 + 36. Diagnose Kotlin, KSP, Android manifests and resource errors in order; never disable tests to force green.
- [ ] Make CI fast fail: only run Android-emulator matrix after Gradle compilation/tests/lint gate is green; preserve logs, JUnit XML, lint HTML, debug APK, release unsigned/signed distinction and screenshots as artifacts.
- [ ] Enforce `main` quality at every push: green CI on exactly the pushed SHA, no silent retries that disguise reproducible source errors.

**Exit:** all required native CI jobs pass on the exact commit; benchmark module configured and executed; no workaround by commenting out coverage.


 — Security, privacy, compliance and release readiness

- [ ] Threat-model file import/export, URI grants, archive extraction, PDF parsing/redaction, WebView, remote URLs/redirects, cloud token lifecycle and temp-file retention.
- [ ] Verify native code/FFmpeg/GPL licensing and feature flags. **Do not ship extended-media binaries** in base release. Verify manifest/DEX/assets/dependency graph in built AAB and publish accurate privacy policy.
- [ ] Confirm API 36 Play target and Android 16 edge-to-edge behavior; test API 26 compatibility. Audit target-SDK permission/foreground-service changes.
- [ ] Generate native release with R8/minification, resource shrinking, valid signing, reproducible versioning and release notes. Run release smoke and app update/migration on data seeded with an older version.
- [ ] Add security and accessibility regression tests to CI; archive evidence and actionable failure classification. No fake 'verified' badges.

**Exit:** Play release candidate with complete reproducible build log, actual AAB/APK, verified manifest, dependency/license reports, no open P0/P1 and a signed release checklist.


Report path: verification/production-2026-10-17/build-report.md
