> Applied to the delivery source by root after patch review. The final validation in `verification/printing-upgrade/` supersedes the preparation-only status below.

# Final screen-state patch scope

Prepared only; no active source files were modified.

## Changes

- Files/outputs: filtered-empty action clears the search, type filter and selection rather than importing another file.
- Files/imported sources: distinguish an empty repository from zero search matches; reset search directly.
- Files/trash: additive `trashLoad` state reflects initial OutputStore reconciliation and explicit `reloadTrash()` retries using the existing repository API. Loading/error cannot masquerade as empty trash. Rows/purge controls wait for READY; a filtered-empty card clears search.
- Files/granted folder: additive `treeLoad` state wraps initial restore, accepted folder listing, refresh, and mutation-triggered listing refresh. A small Mutex serializes those provider operations so older results cannot overtake a newer folder selection. Errors offer Retry while the existing Choose folder action remains available for revoked grants. READY distinguishes no selected folder, a genuinely empty folder, and zero search matches; empty folders can be refreshed and search matches can be reset.
- Tools: include the existing Arabic subtitle in search matching.
- Five paired English/Arabic strings are isolated in new `files_states.xml` files.

## Compatibility and verification

Existing public method parameters/defaults remain unchanged. Only additive ViewModel status properties/retry methods are introduced. No new dependencies and no Gradle commands. Resources were parsed as XML; source anchors and unified-patch applicability were checked against the current frozen tree. Full compile/tests and runtime permission/provider behavior remain root's post-apply gate.

The patch does not change provider destructive-action authorization, output storage, print functionality or editor behavior. It does not add fake delays or loading timers.
