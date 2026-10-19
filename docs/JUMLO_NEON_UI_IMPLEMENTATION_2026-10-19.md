# Jumlo 2026 — Reference-Inspired UI Implementation

## Scope and identity

The October 19 design pass updates native Android Jetpack Compose source on `main`. It follows the user's reference screens stylistically (near-black charcoal, teal accent, rounded panels, floating scan control, genuine file thumbnails) without duplicating another application's branding or inventing membership, account information, cloud storage, or unavailable conversion tools. Product name **Jumlo**, package ID **com.jumlo.app**, saved data, link scheme, repository architecture and tool execution entry points remain unchanged.

The supplied Git repository contains an Android app and iOS icon exports, not a functioning iOS application. No iOS screen behavior is claimed.

## Screens, actions, and architecture

| Screen | Redesign | Genuine actions retained |
|---|---|---|
| Home | Teal scan hero, configurable two-column quick tools, recent file thumbnail carousel, actual favorites and active tasks | Camera scan; tool opening; Recent output open; Files; Downloads; tool customization; resume draft |
| Tools | Grouped capability-driven tiles, search/filter chips, recent and popular tools from preferences | Available PDF/image/download/file tools only. Unimplemented example conversions are intentionally absent |
| Documents / Files | Taller 2-column files with real PDF page-1 previews (bounded 240px rasterization) and real image thumbnails, selection stroke, floating actions | Import; search/filter; favorites; multi-share; ZIP; PDF merge; images-to-PDF; move to trash, restore and purge; rename/copy provider entries; folder management |
| Download | One-action hero and two compact activity metrics above existing queue/history | Actual media/direct download tool, batch intake, pause/resume, cancel/retry |
| Me / Settings | Actual local workspace and storage summary, Files shortcut and existing preferences | Theme/accent/language/font, haptics, sounds, motion, location, storage, notification and cleanup settings |
| Dock | Four destinations plus centered real Scan button: Home / Documents / Scan / Tools / Me | Downloads remains routable from Home and Tools; settings continue on Me; RTL placement follows Compose layout direction |

## Visual and performance choices

- Primary accent defaults to curated teal; existing user-selected theme and accents are preserved.
- Charcoal dark palette, high-contrast teal, restrained gold tertiary accent; IBM Plex and IBM Plex Arabic fonts are retained, no third-party animation framework.
- Spring-based dock movement, existing spring transitions and press animations all observe Reduced Motion.
- PDF previews are rendered from persisted output URIs, off the UI thread, with a bounded bitmap to avoid full-page memory spikes. Failed PDF preview falls back to an icon, not fabricated imagery.
- Screen-width-based grid sizing and lazy lists preserve small-screen reachability and large-screen capacity.
- Selected files use platform Android multiple-URI sharing with temporary read grants. Only supported batch workflows are shown.

## Verification and limitations

New static contracts are in `scripts/test-neon-ui.py`, and existing palette/navigation/wiring contracts were updated. Use `bash scripts/verify-host.sh` and `bash scripts/verify-native.sh` in a toolchain-equipped environment, then review GitHub Actions at the final commit SHA. No real device, emulator, downloaded APK or 60/120 fps profiling is available in the current execution container. These require Android CI and device validation.

## Not added

No fake user name, fake premium subscription, fake cloud privileges, Word/Excel/PPT converters without genuine engine support, iOS screen, or image-only placeholder implementation.
