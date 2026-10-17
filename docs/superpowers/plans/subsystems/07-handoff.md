# Whole-App Integration, Accessibility and Final Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans after the plan set is reviewed. Steps use checkbox (`- [ ]`) syntax. Do not start a separate application.

**Goal:** Wire real features into the existing Android design, verify supported device behavior and package the final source.

**Architecture:** Feature ViewModels observe real job IDs; the shared navigation, theme and locale layer remain common across all tools.

**Tech Stack:** The pinned native Android stack and qualified adapters in the root plan.

**Spec:** `../../specs/2026-10-16-completion-design.md`

## Global Constraints

Inherit every constraint, fixed build version, shared interface and verification rule in `../2026-10-16-completion-plan.md`; it travels with this plan. In particular: preserve originals; no fabricated success; no unapproved local downloads; no font binaries, credentials or caches in deliverables. The exact approved spec is linked above.

## Review Focus

The five cross-cutting failure classes are recorded once in the root plan. Their owning regression tests are assigned to this plan's tasks below; do not substitute generic happy-path tests.

---

### Task U01: Replace fixture-backed screens and reconcile the design system

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/navigation/UniversalUtilitiesNavHost.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/components/QuietPrecisionComponents.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/theme/Shape.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/home/HomeAndOnboardingScreens.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/tools/ToolsCatalogScreen.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/results/OperationAndResultScreens.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/screens/settings/SettingsScreens.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/catalog/ToolRegistry.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/results/ResultsViewModel.kt`
- Create: `app/src/main/java/com/universalutilities/app/ui/tasks/TaskCenterViewModel.kt`
- Create: `docs/SCREEN_COVERAGE.md`
- Create: `scripts/check-ui-assets.sh`
- Test: `app/src/androidTest/java/com/universalutilities/app/ui/RealNavigationTest.kt`
- Test: `app/src/test/java/com/universalutilities/app/ui/ToolRegistryTest.kt`

**Interfaces:**
- Consumes: All core feature handlers F04/P/I/A/D; E capabilities stay individually gated until verified.
- Produces: `ToolRegistry.enabledTools(): List<UtilityTool>` with 16 V1 entries; feature routes pass draft/source/job IDs. `ResultsViewModel.observe(jobId: String)` displays actual persisted outputs, never a saved mock.

- [ ] **Step 1: Write failing regressions.** `catalogCountsDerivedFromEnabledRegistry`, `resultNavigationRequiresPublishedOutput`, `launchHonorsOnboarding`, `dockHiddenInEditorAndSelection`, and `noProductionFixtureContent`.

```kotlin
assertEquals(16, registry.enabledTools().count { !it.isExpansionConcept })
assertEquals(0xFF4F3DFF.toInt(), lightPrimaryArgb)
assertFalse(successRouteReachedForFailedJob)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.ui.RealNavigationTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Preserve screens, not their sample data. Implement Home/search/favorites/drafts/task center/settings/recovery surfaces and real click routes. Restore background #FAFAF9 and violet #4F3DFF / #8F86FF; scalable 56dp controls and 14sp metadata. Responsive four-label dock honors insets and is replaced for selection. Import authentic Material Symbols Rounded filled vector assets with provenance. Do not include font binaries: permitted IBM Plex acquisition is documented and optional local setup, with a declared system fallback until installed. Do not claim exact font fidelity for a fallback build. Cover all 57 V1 families through explicit real shared states, not inflated screenshot counts.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: connect complete native workflows and design system`.

### Task U02: Actual locale switching, permissions, backups and accessibility

**Files:**
- Modify: `app/src/main/java/com/universalutilities/app/MainActivity.kt`
- Modify: `app/src/main/java/com/universalutilities/app/data/preferences/UserPreferencesRepository.kt`
- Modify: `app/src/main/java/com/universalutilities/app/ui/theme/Theme.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ar/strings.xml`
- Modify: `app/src/main/res/xml/locales_config.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `app/src/main/res/xml/network_security_config.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/universalutilities/app/ui/settings/LocaleController.kt`
- Create: `docs/DEVICE_TEST_MATRIX.md`
- Test: `app/src/androidTest/java/com/universalutilities/app/ui/LocaleAccessibilityTest.kt`
- Test: `app/src/androidTest/java/com/universalutilities/app/security/BackupAndGrantTest.kt`

**Interfaces:**
- Consumes: U01 real drafts/routes, persisted preferences and platform permission state.
- Produces: `LocaleController.apply(languageTag: String?): Unit` using actual Android/appcompat locale support; context-sensitive permission flows; verified backup exclusions.

- [ ] **Step 1: Write failing regressions.** `localeChangePreservesDraftAndSwitchesResources`, `rtlDoesNotReversePdfContent`, `twoHundredPercentTextRemainsReachable`, `backupDoesNotIncludeUserContent` and `permissionDenialKeepsUnrelatedToolsUsable`.

```kotlin
assertEquals(savedDraftId, restoredDraftId)
assertEquals("ar", activeResourceLocale.language)
assertFalse(backupRulesIncludeSensitiveContent)
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.ui.LocaleAccessibilityTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Use actual locale resources, not LayoutDirection alone; remove hardcoded live English labels, support system language/theme defaults and predictive back with dirty-draft decisions. Test EN/AR both themes, mixed filenames/ranges, 360/412dp, wide/landscape, keyboard, 1x/1.3x/2x text, TalkBack and reduced motion. Inspect backup/cloud/device-transfer exclusions and least-privilege URI sharing. No unsupported all-files permission request. Run BackupAndGrantTest and document OEM/device-transfer limitations rather than universal privacy guarantees.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `feat: verify localization accessibility and privacy boundaries`.

### Task U03: Fault injection, release evidence and final ZIP

**Files:**
- Modify: `.gitignore`
- Modify: `app/proguard-rules.pro`
- Modify: `app/build.gradle.kts`
- Create: `README.md`
- Create: `VERIFICATION.md`
- Create: `FEATURE_STATUS.md`
- Create: `THIRD_PARTY_NOTICES.md`
- Create: `scripts/package-source.py`
- Create: `scripts/verify-release.sh`
- Create: `verification/manifest.json`
- Test: `app/src/androidTest/java/com/universalutilities/app/integration/RealFileAcceptanceTest.kt`
- Test: `scripts/tests/test-package-source.py`

**Interfaces:**
- Consumes: All preceding tasks and their actual logs; connected-device authorization; real backend qualification evidence.
- Produces: One source ZIP with checksum manifest; debug APK only when actually built/inspected; accurate Source/Build/Device/Security/Release status fields.

- [ ] **Step 1: Write failing regressions.** `noOriginalChangedAcrossFullWorkflow`, `processDeathDuringPublicationIsRecoverable`, `retryNeverDuplicatesResults`, `fontSecretsCachesExcludedFromPackage`, and `unverifiedFeaturePreventsCompleteStatus`.

```kotlin
assertEquals(sourceHashesBefore, sourceHashesAfter)
assertEquals(expectedOutputHashesAndCounts, measuredOutputs)
assertFalse(packageEntries.any { it.endsWith(".ttf") || it.contains("keystore.properties") })
```

- [ ] **Step 2: Verify red.** `scripts/verify-local.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.universalutilities.app.integration.RealFileAcceptanceTest`. Expected: the regression fails; missing tooling is BLOCKED, not red.
- [ ] **Step 3: Implement the declared interfaces.** Run real assembleDebug, testDebugUnitTest and lintDebug, targeted instrumented tests, controlled loopback transfer tests, real file corpus and device lifecycle/biometric tests. Review R8/minified behavior separately; signing requires user-owned keys, never generate production credentials silently. Package only after required gates have actual evidence. source ZIP includes complete wrapper/tests/migrations/notices/instructions but excludes fonts, SDK/caches/secrets. Test extraction and checksums; label any blocked capability/source candidate honestly and do not call it the requested completed app. Keep the untouched input archive separately.
- [ ] **Step 4: Verify green.** Re-run Step 2 and the task's other named test classes, then the build gate. Require exit 0 and the assertions above; retain actual logs.
- [ ] **Step 5: Review and commit the verified diff.** Message: `release: verify real workflows and package source`.
