#!/usr/bin/env python3
"""Non-runtime source contract checks for the 2026 redesign; not an Android compile."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
K=ROOT/'app/src/main/java/com/jumlo/app'
def code(file): return (K/file).read_text()
class RedesignWiringTests(unittest.TestCase):
    def test_tool_switch_is_new_empty_session(self):
        vm=code('ui/AppViewModel.kt')
        start=vm.index('fun chooseTool(id:String)')
        self.assertIn('WorkspaceDraft.forTool(id,emptyList())',vm[start:start+550])
        self.assertIn('closeToolSession()',vm)
        self.assertIn('_sessionEpoch.value++',vm)
    def test_media_screen_reentry_resets_url(self):
        self.assertIn('LaunchedEffect(sessionEpoch)',code('ui/downloads/MediaWorkspace.kt'))
        self.assertIn('fun beginSession(initialUrl:String)',code('ui/downloads/MediaViewModel.kt'))
    def test_naming_is_automatic_in_workspace(self):
        self.assertNotIn('field_output_filename',code('ui/workspace/WorkspaceScreen.kt'))
        self.assertIn('AutoOutputName.fromSource',code('ui/AppViewModel.kt'))
    def test_download_is_extractor_or_real_direct_file(self):
        vm=code('ui/downloads/MediaViewModel.kt')
        self.assertIn('DownloadLinkPolicy.isDirect',vm)
        self.assertIn('runtime.inspect(',vm)
        self.assertIn('"dl_02" else "dl_media"',vm)
        self.assertIn('unexpectedHtml',code('data/processing/DownloadOperationHandler.kt'))
    def test_media_partial_is_resumable(self):
        handler=code('data/media/MediaOperationHandler.kt')
        self.assertIn('if (completed) directory.deleteRecursively()',handler)
        self.assertIn('"--continue"',code('core/media/MediaPolicies.kt'))
        self.assertNotIn('"--no-continue"',code('core/media/MediaPolicies.kt'))
    def test_public_files_use_platform_output_providers(self):
        store=code('data/output/PublicOutputStore.kt')
        for token in ('MediaStore.Downloads.EXTERNAL_CONTENT_URI','DocumentFile.fromTreeUri','Download/Jumlo'):
            self.assertTrue(token in (store+code('core/StoragePaths.kt')),token)
        self.assertIn('publicUri',code('model/OperationContracts.kt'))
    def test_settings_picker_and_concurrency_are_connected(self):
        nav=code('ui/navigation/JumloNavHost.kt')
        self.assertIn('Intent.ACTION_OPEN_DOCUMENT_TREE',nav)
        self.assertIn('viewModel.setDownloadDestinationFolder',nav)
        self.assertIn('DownloadAdmission',code('data/jobs/JobStore.kt'))
        self.assertIn('setFileViewMode',code('ui/settings/SettingsScreen.kt'))
    def test_five_tabs_legacy_rail_unused(self):
        models=code('model/Models.kt')
        self.assertIn('SETTINGS("settings",',models)
        self.assertNotIn('QuietExpandedNavigationRail',code('ui/navigation/JumloNavHost.kt'))
        self.assertNotIn('QuietExpandedNavigationRail(',code('ui/components/QuietNavigation.kt'))
    def test_reduce_motion_and_haptics_are_composition_preferences(self):
        act=code('MainActivity.kt')
        self.assertIn('LocalReducedMotion provides',act)
        self.assertIn('LocalAppHapticsEnabled provides',act)
        self.assertIn('LocalReducedMotion.current',code('ui/components/QuietStates.kt'))
        self.assertIn('LocalReducedMotion.current',code('ui/components/QuietButtons.kt'))
    def test_english_and_arabic_redesign_strings(self):
        en=(ROOT/'app/src/main/res/values/redesign.xml').read_text()
        ar=(ROOT/'app/src/main/res/values-ar/redesign.xml').read_text()
        for key in ('redesign_custom_saved_path','redesign_save_fallback'):
            self.assertIn(f'name="{key}"',en)
            self.assertIn(f'name="{key}"',ar)
if __name__=='__main__': unittest.main(verbosity=2)
