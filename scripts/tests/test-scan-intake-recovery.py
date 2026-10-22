from pathlib import Path
import unittest
R=Path(__file__).resolve().parents[2]
class ScanIntakeRecovery(unittest.TestCase):
    def test_import_registers_id_in_non_cancellable_commit_boundary(self):
        source=(R/'app/src/main/java/com/jumlo/app/data/source/SourceStore.kt').read_text()
        self.assertIn('onCommitted: (SourceRef) -> Unit',source)
        self.assertIn('onCommitted(entity.ref())',source)
        self.assertIn('withContext(NonCancellable)',source)
    def test_batch_rollback_survives_parent_cancellation(self):
        view=(R/'app/src/main/java/com/jumlo/app/ui/AppViewModel.kt').read_text()
        self.assertIn('onCommitted = { committed -> copied.add(committed.id) }',view)
        self.assertIn('withContext(NonCancellable)',view)
        self.assertIn('rollback()',view)
if __name__=='__main__':unittest.main()
