#!/usr/bin/env python3
"""Supplementary static wiring checks; not a substitute for Android compilation or UI tests."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]

def read(path): return (root/path).read_text(encoding='utf8')
checks = {
  'Files selection offers ZIP/PDF batch handoff': all(t in read('app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt') for t in ('"file_07"','"pdf_03"','"pdf_02"','onOutputWorkflow','NeonBatchAction')),
  'Previously imported sources can be selected in batches': all(t in read('app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt') for t in ('onSourceWorkflow','selectedSources','selectingSources')) and 'prepareSourceBatchWorkflow' in read('app/src/main/java/com/jumlo/app/ui/AppViewModel.kt'),
  'Favorites filter is coupled to persistent setting': all(t in read('app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt') for t in ('files_favorites','settings.favoriteOutputIds','toggleOutputFavorite')),
  'Output workflow validates published integrity before importing': all(t in read('app/src/main/java/com/jumlo/app/ui/AppViewModel.kt') for t in ('prepareOutputWorkflow','OutputWorkflowPolicy.validate','outputStore.find','sources.inspect')),
  'Failed multi-import is rolled back': all(t in read('app/src/main/java/com/jumlo/app/ui/AppViewModel.kt') for t in ('discardNewImport','NonCancellable')),
  'Source reuse is selected by MIME, not hardcoded PDF creator': all(t in read('app/src/main/java/com/jumlo/app/ui/navigation/JumloNavHost.kt') for t in ('prepareSourceWorkflow','OutputWorkflowPolicy.reuseTool','prepareOutputWorkflow')),
  'Favorite flag persists through DataStore': all(t in read('app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt') for t in ('FAVORITE_OUTPUT_IDS','toggleFavoriteOutput','OutputFavorites.toggle')),
  # The current Home specification calls for recent files; favorites stay in Files.
  'Home recent files use real outputs and open the selected record': all(t in read('app/src/main/java/com/jumlo/app/ui/home/HomeScreen.kt') for t in ('items(outputs.take(8)', 'NeonOutputPreview(output', 'onOutput(output)', 'fileDateLabel(output.createdAtEpochMs)')),
  'Files grid adapts to its safe pane and font scale': all(t in read('app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt') for t in ('BoxWithConstraints','maxWidth.value','density.fontScale','coerceIn(1,4)')),
  'Size and invalid batch have actionable text': all(t in read('app/src/main/java/com/jumlo/app/ui/components/UserMessages.kt') for t in ('code=="BUNDLE_TOO_LARGE"','code=="BUNDLE_INVALID_SELECTION"')) and all('name="files_workflow_size"' in read(f'app/src/main/res/{d}/strings.xml') for d in ('values','values-ar')),
  'New workflow errors have localized explanations': 'BUNDLE_INCOMPATIBLE_SELECTION' in read('app/src/main/java/com/jumlo/app/ui/components/UserMessages.kt') and all('name="files_workflow_incompatible"' in read(f'app/src/main/res/{d}/strings.xml') for d in ('values','values-ar')),
  'Generic SAF imports are recognized from bounded file magic': all(t in read('app/src/main/java/com/jumlo/app/data/source/SourceStore.kt') for t in ('SourceMediaType.resolve','ByteArray(32)','resolver.getType(parsed)')),
  'No database schema bump for the independent favorite key': 'version = 2' in read('app/src/main/java/com/jumlo/app/data/local/AppDatabase.kt'),
  'New strings exist in EN/AR': all(t in read('app/src/main/res/values/strings.xml') and t in read('app/src/main/res/values-ar/strings.xml') for t in ('name="files_bundle_selected"','name="files_favorites"','name="files_merge_selected"','name="files_images_to_pdf_selected"')),
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL')+' '+name)
raise SystemExit(0 if all(checks.values()) else 1)
