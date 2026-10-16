# AUDIT.md — Universal Utilities Code Audit & Fix Log

## Baseline Snapshot (before fixes)

| Area | Status | Notes |
|------|--------|-------|
| Gradle wrapper JAR | MISSING | `gradle-wrapper.jar` absent; wrapper properties reference Gradle 8.14 with checksum |
| Android build | UNVERIFIED | Has not compiled/run in this environment |
| Tests | UNVERIFIED | Unit tests exist; not executed |
| Lint | UNVERIFIED | Not executed |
| Release build (R8) | UNVERIFIED | Release minify + shrink enabled; untested |
| Design system | PARTIAL | Theme/Type/Shape exist; need verification against DESIGN.md spec |
| 4 dock destinations | PARTIAL | Home/Tools/Downloads/Files implemented in NavHost |
| Home screen | PARTIAL | Pillar cards, quick actions, recent results present |
| Tools screen | STUBBED | "Tools catalog" listed but structural PDF/tools gated |
| Downloads screen | PARTIAL | DL-01, DL-02, DL-03, DL-04 present; DL media gated |
| Files screen | PARTIAL | FILE-01 through FILE-08 partially present |
| Pillar semantic colors | IMPLEMENTED | LightPillarColors / DarkPillarColors defined |
| Dark mode | IMPLEMENTED | Light/Dark color schemes defined |
| RTL / Arabic | PARTIAL | language enum + layout direction implemented |
| Room DB | IMPLEMENTED | AppDatabase + entities + migrations exist |
| WorkManager | IMPLEMENTED | EngineOperationWorker + JobStore |
| DataStore preferences | IMPLEMENTED | UserPreferencesRepository |
| FileProvider | IMPLEMENTED | Configured for exports |
| Share intake | IMPLEMENTED | SEND / SEND_MULTIPLE intent filters |
| Foreground service | IMPLEMENTED | SystemForegroundService dataSync |
| Network security | IMPLEMENTED | network_security_config.xml |
| Data extraction rules | IMPLEMENTED | data_extraction_rules.xml |
| Biometric | DECLARED | Permission in manifest; usage unverified |
| AAB / signing | UNCONFIGURED | Release signing requires keystore.properties |

## Design Reference (from Stitch DESIGN.md)

- Canvas: 412 × 915 dp, 4-unit grid, margins/gutters 20dp
- Colors: background #F9F9F8, primary #340DE9 / button #4F3DFF, surface #FFFFFF
- Typography: IBM Plex Sans (declared as SansSerif fallback in code)
- Shapes: thumbnail 8dp, input 12dp, button 16dp, card 20dp, modal 28dp, dock 32dp
- Dock: 380×80dp, 32dp radius, 12dp offset, 4 slots (Home/Tools/Downloads/Files)
- Active pill: 64×32dp, 20dp radius, primaryContainer fill
- Pillar colors: PDF #BC2926, Images #AC4C08, Downloads #007368, Files #2663D9

## Known Issues to Investigate

1. **Gradle wrapper JAR missing** — build cannot run without it
2. **Font not bundled** — DESIGN.md mandates IBM Plex Sans; code uses `FontFamily.SansSerif`
3. **Vault (XF-05)** — explicitly gated as "unavailable" / "VAULT_BACKEND_UNQUALIFIED"
4. **Media download (dl_media)** — gated behind `-PextendedMedia=true`; GPL-3.0 dependency
5. **Structural PDF ops** — gated as "STRUCTURAL_PDF_BACKEND_UNQUALIFIED"
6. **Debug-only tools** — CapabilityRegistry.enabled() requires BuildConfig.DEBUG
7. **No baseline profile** — missing for startup performance
8. **No CI workflow** — missing GitHub Actions
9. **No release signing config** — keystore.properties not present

---

## Fix Log

| # | Finding | Severity | Root Cause | Fix | Verification |
|---|---------|----------|------------|-----|--------------|
