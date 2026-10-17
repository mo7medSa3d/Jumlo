# Screen and action matrix — 2026-10-17

## Scope and status

This inventory describes the current standard-distribution source. It was reconciled against `JumloNavHost`, extracted screens, Settings/Account, workspace settings and the actual capability policy. The 45-definition feature inventory is generated separately; Tools shows **33 entries: 32 processors and a PDF viewer**. Three duplicate navigation helpers are omitted and nine definitions are blocked.

**Platform status for every row: Android API 26/36 interaction execution pending unless an explicit result in RELEASE supersedes it.** Source wiring, host behavior, native compilation and an installed-device interaction are different evidence. No baseline or final device screenshot is fabricated. The inventory was finalized after refactoring; the plan's pre-change on-device inventory/screenshots could not be performed on this host.

Persistence abbreviations: **P** DataStore; **D** Room; **S** saved UI state; **M** process memory; **OS** Android permission/provider state. All interface paths below are relative to `app/src/main/java/com/jumlo/app/`.

## Root shell, navigation and launchers

| ID / surface control | Intent and real destination/handler | Persistence and back/cancel | Expected failure / test source |
|---|---|---|---|
| N01 Launcher/startup | MainActivity loads preferences and workspace recovery before editor navigation | P+D; real loading indicator until ready | Preference/restore failure message; PreferencesPersistenceTest, workspace restoration checks |
| N02 Onboarding confirmation | `finishOnboarding` persists completion | P; first-use modal, explicit continue | Preference write failure; JumloUiTest |
| N03 Home / Tools / Downloads / Files tabs | Typed route via `navigateTab`, single top and saved root state | S; system/NavHost back | Invalid route cannot dispatch jobs; JumloUiTest |
| N04 Width-adaptive rail / fold viewport | WindowInfoTracker separates occluding/separating folds; same root destinations at >=600dp usable pane width | Same commands as dock; absolute window-coordinate placement, RTL tie-break | Pure geometry tests; installed compact/tablet/IME/fold acceptance pending |
| N05 Home app-bar search | Opens Tools search/catalog | S; normal back | JumloUiTest |
| N06 Root account icon | Opens Account & Data modal | S; sheet dismiss/back | AccountHubInteractionTest; actual installed tap pending |
| N07 Non-root back button | Pops route; dirty Workspace opens confirmation first | S+D; native back otherwise | R4 review fix; dirty-back device cases pending |
| N08 System/predictive back | Native route behavior; only completed dirty-workspace gesture opens choices | S; cancelled gesture keeps editor | Device predictive-back acceptance pending |
| N09 Error/undo snackbar | Localized code; managed-trash Undo calls restore | M; dismiss clears message/action | Code allowlist / host trash tests; UI device pending |
| N10 External share intake | ACTION_SEND/SEND_MULTIPLE; `IntakeController`, then awaited recovery and source snapshot import / URL intake | D snapshots; sensitive URL M | Lost grants/invalid URI/limits; SourceAndPublicationTest |
| N11 Workspace deep link | Typed Workspace route using current restored form; unavailable editor is blocked | D; normal dirty-back policy | Capability host tests; unknown ID rejected at submit/enqueue/dispatch |
| N12 File picker | ACTION_OPEN_DOCUMENT, multiple selection, type hint; `intake` | D snapshots; cancel changes nothing | Unsupported/corrupt file, revoked permission, disk limits |
| N13 Photo picker | PickMultipleVisualMedia, <=100 selected URIs; `intake` | D snapshots; cancel changes nothing | Provider/unsupported decode; image fixtures |
| N14 Folder picker | ACTION_OPEN_DOCUMENT_TREE; `acceptTree` takes offered valid grants | OS persisted grant; chosen tree restored | ACCESS_LOST/read-only provider; SAF instrumentation pending |
| N15 Open / share output | ACTION_VIEW / chooser with read grant and ClipData | OS external application; return to current route | No compatible app/access lost; external round trips pending |
| N16 Save as picker | `prepareExport` then ACTION_CREATE_DOCUMENT then `export` | S selected OutputRecord; cancel clears pending action | Same source URI refused; copied byte count/hash verified; export failure does not reprocess |
| N17 Diagnostics save picker | Explicit `prepareErrorLogExport` and `exportErrorLog` | S pending flag; cancel aborts | Provider write failure; sanitized local log tests |
| N18 Notification permission | Runtime launcher / Android app notification settings | OS authoritative, P callback flag; resume rereads OS | Denial/settings return; API-specific device testing pending |

## Home and Tools

| ID / control | Command/result | Persistence/back | Error/state and coverage |
|---|---|---|---|
| H01 Home search row | Tools route | S; root navigation | No promotional/dead media CTA |
| H02 Common/favorite actions | Four defaults (Images to PDF / Merge PDF / Resize Image / New Download), or locally customized eligible tools; capability check, `chooseTool`, Workspace | P ordered IDs + D recovery; current download Wi-Fi default | Real title/icon per tool; pure favorites policy and authored Home interaction tests |
| H03 Resume draft | Workspace for real selected input list | D saved/recovery data; dirty-back choices | No fake draft when there are no selected inputs |
| H04 Active task row/details | Activity selected job | D Room stage; S selected ID | Task loading/failed/waiting states; real progress only |
| H05 Task overflow | Pause only downloads; cancel active; retry eligible state | D+WorkManager | Cancellation/lease host SQL; device background pending |
| H06 Recent result row | External open of real published record | D | Missing external app/URI grant failure |
| H07 Customize Home actions | Add/remove eligible tools, move up/down, restore defaults, Save or Cancel | S edit buffer; P ordered IDs only on save; no network | Unavailable/unknown/duplicate IDs filtered; maximum four; pure policy + preference tests |
| T01 Search edit/clear | Filters title of eligible tools | S text; no job | Empty results includes Reset filters |
| T02 Category chip / All / Reset | Filters eligible tools and recomputes count | S category | No hardcoded total; static/policy inventory |
| T03 Tool row | Capability check; typed viewer/processor workspace | D selection; dirty-back policy | Blocked/concept entries never inert targets |
| T04 PDF viewer entry | Imports/previews real PDF pages; no process CTA | D references, S page | Invalid PDF/encryption/access errors; preview device tests |

## Workspace and results

| ID / control | Command/result | Persistence/back | Error/state and coverage |
|---|---|---|---|
| W01 Import files / choose photos | N12/N13 | D immutable snapshots | Initial empty state, busy, partial intake errors |
| W02 Input row / Preview menu | Bounded actual PDF/image preview | S selected preview/page; modal close/back | Cancel obsolete decode, close/recycle resources; no inferred result preview |
| W03 Previous / Next preview page | `preview(sourceId,page)` off main thread | S page; bounds disable invalid direction | Malformed PDF/page/access failure; actual metadata displayed |
| W04 Move up / Move down | `moveSource(index,delta)` | D recovery; list order preserved | Invalid boundaries do not offer action |
| W05 Remove input | `removeSource(id)` | D editor reference only; original snapshot not deleted | Empty selection disables processing |
| W06 URL edit / Paste | `setUrls`, single/batch validation | M sensitive text; filename suggestion | Bad URL/count/HTTP without consent; focus/IME does not silently submit |
| W07 Essential field, option or toggle | `updateForm`, real per-tool mapping below | D safe fields; M secrets only | Arabic digit normalization, bounds/enums/settings validation |
| W08 Advanced sheet / Close | Only tools with settings expose it; same `updateForm` contract | S open state; close/back keeps edits | No ineffective format/name controls for fixed-output reports/comparison |
| W09 Primary execution CTA | Validates then reserves durable submission identity and enqueues real job | D request + WorkManager; S selected job | Disabled empty/invalid/busy; no success until verified publication |
| W10 Duplicate tap/restarted submission | Same fingerprint reuses job ID; fresh URL is rebound to original opaque handle; waiting job explicitly retried | D identity, M secret | SubmissionSecretsTest; Room checkpoint test; device process kill pending |
| W11 Dirty back — Keep editing | Dismiss only | S current editor stays | No data write/delete |
| W12 Dirty back — Save draft | `saveWorkspaceDraft` commits checkpoint+recovery then leaves | D transaction; no raw URL/password persistence | Stay on screen if write fails/new edits arrive |
| W13 Dirty back — Discard | Restore explicit saved checkpoint and durable recovery then leave | D; original files untouched | Write failure leaves route open |
| W14 Unavailable restored tool | Safe unavailable message and leave/choose another route | D preserved data; no dispatch | Policy reason; gated tools cannot execute |
| W15 Watermark/stamp/annotation content | Main editor exposes text, logo choice or page numbers; annotation page | D safe content; logo second input remains explicit | Reject missing text/content or insufficient logo inputs before enqueue; 20-test focused JVM suite and authored component interactions |
| W16 Batch rename name preview | Actual domain algorithm runs on Default; proposed names in input rows plus complete scrollable preview dialog | M derived preview tied to current names/settings; D pattern/start | Invalid pattern/collisions normalized by same algorithm as handler; CTA waits for matching successful preview; originals preserved |
| A01 Activity task row | Actual Room progress/outcomes; localized errors | D; normal back | No fabricated percent when total unknown |
| A02 Active task cancel/pause | JobStore cancellation or pause; WorkManager unique work | D lease fencing/partial files | Known race/infrastructure tests; device kill pending |
| A03 Failed/partial/interrupted/waiting Retry | `retryFailed` same logical job/output keys | D; already-published outputs reused | Waiting is not a dead end; retry input/access/network after recovery |
| A04 Re-enter URL for missing secret/restart input | Replace opaque-handle secret, retry existing job | M sensitive URL; D job | Other waiting cases can retry without redundant URL edit |
| A05 Output overflow | Open, Share, Save as, Use as input, Move to trash | D; N15/N16 or imported snapshot | Publication digest check, access loss, provider failure |
| A06 Start new run | Explicitly clear persisted submission checkpoint then return to editor | D reset awaited before next submit | Intentional reprocessing distinct from export/retry |

### Per-tool settings and input actions

All editable fields map through `WorkspaceDraft.executionSettings` to the handler listed in FEATURE_QUALIFICATION_MATRIX. Safe fields auto-recover; the common import/reorder/remove/validation actions above apply. Fixed-output tools show explanatory content instead of cosmetic editable controls. These rows list the complete option families, including conditional controls; they do not claim every option has been exercised on Android.

| Tool IDs | Meaningful settings / special actions | Structural/validation evidence |
|---|---|---|
| `pdf_01` | Import, page preview/previous/next only | No processing settings/CTA |
| `pdf_02` | Output name, paper width/height, margin, orientation, fill | Source settings mapping inspected; dedicated images-to-PDF layout/output acceptance pending |
| `pdf_03` | Output name; input order, at least two PDFs | PdfStructureCoreTest; input-count UI |
| `pdf_05` | Output name; split every N or selected ranges; N/ranges conditional | PageSelection/core parser tests |
| `pdf_06` | Output name and selected pages | PDF structure tests |
| `pdf_04` | Output name, explicit page order, pages to rotate and degrees | PDF organize/rotate structure tests |
| `pdf_07` | Pages, DPI, PNG/JPEG, quality, grayscale | Pixel/renderer tests; output filenames derived from page index |
| `pdf_crop` | Output name, pages, normalized rectangle | Crop geometry validation/tests |
| `pdf_compress` | Output name, maximum image DPI, quality | Text/structure preservation tests; unsupported streams bounded |
| `pdf_stamp` | Output name, essential text/page numbering; advanced page selection | At least text or page numbers required; PDF structure + WorkspaceSettingsTest |
| `pdf_annotate` | Output name, essential text/page; advanced x/y, note versus overlay | Nonblank text before enqueue; PDF structure + WorkspaceSettingsTest |
| `pdf_signature` | Output name, page, rectangle; imported image or draw/clear/use signature | SignaturePad in ScanScreen.kt; native output fixture pending |
| `pdf_metadata` | Output name, title/author/subject/keywords; insertion page with extra PDF | Metadata/insertion structure tests |
| `pdf_forms` | Output name, form values JSON/text, flatten flag | AcroForm tests; unsupported forms can fail |
| `pdf_compare` | Two PDF inputs; fixed comparison report | No ignored editable filename |
| `img_01`, `img_04` | Format, quality, prefix | Codec/bitmap tests; selected format support checked |
| `img_02` | Common conversion fields; width/height OR percentage OR longest edge; fit, upscale | WorkspaceSettingsTest and geometry tests |
| `img_03` | Common conversion; crop rectangle, quarter-turn rotation, horizontal/vertical flip | Geometry and pixel tests |
| `img_exif` | Common conversion, remove all/location metadata | EXIF failures are reported rather than swallowed |
| `img_watermark` | Common conversion, essential text/second-image logo choice, advanced opacity | Text or >=2 distinct selected inputs for logo; JVM + ImagePixelTest opacity/small-canvas fixtures |
| `img_join`, `img_stitch` | Common conversion, layout for collage, gap; stitch overlap | Aggregate bitmap budgets, join fixture |
| `img_adjust` | Common conversion, brightness/contrast/saturation/padding | Pixel and budget tests |
| `img_target` | Byte target, prefix; fixed JPEG and automatic quality search | Impossible target fails; no ineffective format/quality controls |
| `img_compare` | Two images; fixed report/heatmap | Alpha/pixel difference tests; no conversion options |
| `file_07` | Archive name, compression level, ordered files | Real ZIP roundtrip and limits |
| `file_08` | Archive input; derived safe entry names | No ineffective output-name or compression setting |
| `sha256` | Optional expected digest | Actual hashing/comparison fixtures |
| `file_batch_rename` | Essential pattern/starting number; current proposed names and full preview dialog before submission | Handler algorithm reused off main; preview never mutates files; originals preserved |
| `file_duplicates` | Selected files; fixed digest-based report | No editable output-name control |
| `dl_02`, `dl_03` | URL(s), output name, Wi-Fi, unmetered, explicit HTTP consent | HttpPolicy + real loopback transfer fixtures |

The blocked definitions have no public settings/submit action. Retained internal scanner/media/Drive code is not counted as a verified visible control. Registry descriptions and technical enum choices retain existing bilingual/technical data; full rendered typography and RTL still require device QA.

## Files

| ID / control | Command/result | Persistence/back | Error/state and coverage |
|---|---|---|---|
| F01 Import / grant folder | N12/N14 | D/OS | Empty/provider access state |
| F02 Search toggle/edit/clear; recent/name order | Local list filtering/sorting | S; no file mutation | Input/output/trash filtering and predictable empty state |
| F03 Results / Imported / Granted folder / Trash chip | Distinct data sources | S; selection cleared on section change | Avoids presenting provider originals as managed results |
| F04 Results select/cancel/check rows/trash selected | Only selected currently present output IDs | M selection; managed trash D | Opening menu does not execute action; ProductionRowInteractionTest |
| F05 Result row/overflow | Open/share/export/reuse/trash | D/OS; N15/N16 | No five inline action buttons |
| F06 Imported row/add to workspace | Add existing snapshot reference and open editor | D recovery | Original URI is not rewritten/deleted |
| F07 Trash row/Restore | Verified restore through OutputStore | D+journal | Corruption/path rejection; host TrashTests |
| F08 Trash Delete / Empty Trash | Capture one/all currently listed IDs, show count and irreversible warning | M confirmation; cancel/back no deletion | Exact-ID purge tests; later arrivals excluded |
| F09 Confirm purge | `purgeTrashIds(capturedSet)` | D/disk | Digest/path revalidation; failures remain visible |
| F10 Folder row/Browse inside | System tree picker initially pointing to chosen child | OS; provider-controlled navigation | Lost grants/provider refusal |
| F11 Provider Open/Import | Only readable entries | OS open or D snapshot | Read-only still permits safe read actions |
| F12 Provider Copy | KEEP_BOTH chooses unique destination name in current writable folder | OS; byte verification | Same-folder copy policy regression; device provider pending |
| F13 Provider Rename dialog | Live normalized name/conflict preview; held write access AND provider flag; sends exactly the displayed safe name | OS; cancel/back abort; latest listing rechecked | Invalid/unchanged/colliding/missing/read-only target cannot confirm; runtime recheck retained; pure rules and authored component tests |
| F14 Provider Delete original dialog | Explicit named confirmation; recheck canDelete | OS irreversible, distinct from managed trash | Cancel leaves original; device deletion boundaries pending |
| F15 New folder dialog | Only writable folder; validate name and create | OS; cancel abort | Invalid name/existing/provider failure |

## Account & Data and Settings

| ID / control | Command/result | Persistence/back | Error/state and coverage |
|---|---|---|---|
| C01 My files | Dismiss hub and open Files | S | AccountHubInteractionTest exact single callback |
| C02 Storage usage | Measured source/output/partial/temp/trash totals dialog | Refreshed disk state; dismiss returns to hub | Scans off main; not a quota/tier claim |
| C03 Settings | Dismiss hub and open Settings | S | AccountHubInteractionTest exact single callback |
| C04 Privacy | Local information dialog | Close/back returns to hub | Current actual available behavior |
| C05 Help/About | Version/help/data-recovery information dialog | Close/back returns to hub | No unavailable service action |
| S01 Theme row / System, Light, Dark choice / Cancel | Persist theme choice; observable theme | P; cancel no write | PreferencesPersistenceTest; rendered theme device pending |
| S02 Language row / English, Arabic / Cancel | Persist language, application locale and RTL | P; initial preference gate avoids false intermediate locale | Locale recreation/device QA pending |
| S03 Reduced motion switch | Persist; respects system-disabled animations and navigation/preview paths | P | Animated/safe-area acceptance pending |
| S04 Haptic feedback switch | Persist; provided to all shared controls through CompositionLocal | P | Physical tactile check pending |
| S05 Wi-Fi default switch | Persist default for new download workspace | P+D when applied; existing job request immutable | Workspace default/mapping tests |
| S06 Save location row | Opens managed Files; per-result Save as chooses external destination | S+OS export | Does not pretend to set an unused global external destination |
| S07 Notifications row | N18; actual OS status on resume | OS authoritative | Denied/permanently denied scenarios require device validation |
| S08 Storage row | Refresh measurement then show category totals | M measured state | Permission/IO failure reported |
| S09 Folder access row | System folder picker / re-grant | OS | Revocation/provider errors |
| S10 Clear temporary row / confirm/cancel | Explicitly removes expired reproducible cache only | Disk; cancel unchanged | Six actual TemporaryCleanup file tests including symlinks/cancel |
| S11 Empty trash row / confirm/cancel | Count + captured IDs; disabled when empty/busy | D/disk | Same exact-ID contract as Files; UI cancellation pending device |
| S12 Privacy / Help/About rows | Current information dialogs | Close/back to Settings | No fake login/account/tier |
| S13 Export diagnostics | N17 explicit external save | OS | Stable-code log sanitization; no unsolicited transmission |

## State and acceptance checklist

Loading, empty, invalid input, busy, real progress, partial success, failure, waiting for input, interruption, cancellation, completed outputs and dirty-back paths are represented above. Repeated taps, process death, font/locale changes, lost grants, low storage, external export failure and malicious/corrupt inputs remain acceptance scenarios, not assumed passes.

For each visible route run light/dark, EN/AR, 360dp/412dp/tablet/landscape, 200% font, TalkBack, keyboard/IME, gesture and three-button navigation on API 26/36. Record the application/source hash, device/API, fixture hashes, expected/actual result and screenshot/video path. `ScreenConfigSemanticsTest` covers component fixtures only; it is not a complete route golden set. See RELEASE for the precise executed-test boundary and SECURITY_MODEL/PERFORMANCE_REPORT for remaining non-UI gates.
