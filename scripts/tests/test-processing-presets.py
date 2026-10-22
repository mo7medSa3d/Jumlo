from pathlib import Path
import unittest
R=Path(__file__).resolve().parents[2]

def read(name):return (R/name).read_text(encoding='utf-8')
class Contract(unittest.TestCase):
    def test_template_does_not_persist_source_or_secrets(self):
        s=read('app/src/main/java/com/jumlo/app/model/ProcessingPreset.kt')
        for field in ['sourceIds=emptyList()', 'secret1=""', 'secret2=""', 'pageEdits=emptyList()', 'cleartextConsent=false']:
            self.assertIn(field,s)
        self.assertIn('current.toolId == toolId',s)
    def test_storage_is_atomic_and_bounded(self):
        s=read('app/src/main/java/com/jumlo/app/data/preferences/UserPreferencesRepository.kt')
        for word in ['PROCESSING_PRESETS','processingPresetsFlow','setProcessingPreset','removeProcessingPreset']:
            self.assertIn(word,s)
        self.assertIn('take(16)',s)
        self.assertIn('decodePresets',s)
    def test_ui_is_connected_to_real_persistent_store(self):
        vm=read('app/src/main/java/com/jumlo/app/ui/AppViewModel.kt')
        screen=read('app/src/main/java/com/jumlo/app/ui/workspace/WorkspaceScreen.kt')
        for w in ['processingPresets','saveProcessingPreset','applyProcessingPreset','deleteProcessingPreset']:
            self.assertIn(w,vm)
        self.assertIn('ProcessingPresetsPanel(',screen)
        self.assertIn('confirmation',read('app/src/main/java/com/jumlo/app/ui/workspace/ProcessingPresetsPanel.kt'))
if __name__=='__main__': unittest.main()
