#!/usr/bin/env python3
"""Source wiring contracts only; NOT a substitute for an Android compilation or UI test."""
from pathlib import Path
import re
import unittest
R=Path(__file__).resolve().parents[1]
SRC=R/'app/src/main/java/com/jumlo/app'
def source(path): return (SRC/path).read_text()
class FlagshipReliabilityWiringTests(unittest.TestCase):
    def test_split_every_page_chip_selects_every_mode(self):
        editor=source('ui/workspace/PdfPageEditor.kt')
        chunk=editor.split('item("each-page")',1)[1].split('item(',1)[0]
        # Group mode uses the same flag, so "every page" is selected only for
        # a one-page group. Merely checking flag1 would mislabel larger groups.
        # Styling now supplies border before the named selection argument. Check
        # the selection contract, not the old positional argument spelling.
        self.assertRegex(chunk,r'FilterChip\s*\([^{}]*\bselected\s*=\s*\(\s*draft\.flag1\s*&&\s*every\s*==\s*1\s*\)')
        self.assertRegex(chunk,r'flag1\s*=\s*true')
        self.assertRegex(chunk,r'flag2\s*=\s*false')
        self.assertRegex(chunk,r'num1\s*=\s*"1"')
        self.assertIn('selectionEnabled = !(split && draft.flag1)',editor)

    def test_platform_splash_is_bounded_by_readiness_and_does_not_delay_intake(self):
        activity=source('MainActivity.kt')
        # The SplashScreen API replaced the old delayed Compose overlay.
        self.assertIn('installSplashScreen()',activity)
        self.assertIn('splash.setKeepOnScreenCondition',activity)
        self.assertIn('SystemClock.elapsedRealtime() - startedAt < 900L',activity)
        self.assertIn('!appViewModel.settings.value.isLoaded || !appViewModel.workspaceReady.value',activity)
        self.assertIn('setOnExitAnimationListener',activity)
        self.assertIn('.withEndAction { provider.remove() }.start()',activity)
        self.assertNotIn('delay(',activity)
        self.assertIn('if (savedInstanceState == null) receiveIntent(intent)',activity)
        self.assertIn('override fun onNewIntent(intent: Intent)',activity)
        self.assertIn('receiveIntent(intent)',activity.split('override fun onNewIntent',1)[1])

    def test_zip_extraction_is_capped_before_extraction(self):
        archive=source('data/processing/ArchiveOperationHandler.kt')
        part=archive.split('private suspend fun extract(',1)[1].split('private suspend fun hashes(',1)[0]
        self.assertIn('maxOutputFiles',part.split('ArchiveCore.extract(',1)[1].split(')',1)[0])

    def test_social_share_text_accepts_captions_without_cookie_ui(self):
        share=source('ui/AppViewModel.kt')
        self.assertIn('MediaLinks.sharedUrls(text)',share)
        self.assertIn('DownloadLinkPolicy.prefersMediaAnalysis(lines.single())',share)
        self.assertIn('MEDIA_BATCH_ANALYSIS_REQUIRED',share)
        self.assertIn('return@let',share)
        ui=source('ui/downloads/MediaWorkspace.kt')
        self.assertNotIn('Cookie',ui)

    def test_visual_split_boundaries_persist_without_page_number_workarounds(self):
        editor=source('ui/workspace/PdfPageEditor.kt')
        self.assertIn('jumlo_split_marker_hint',editor)
        self.assertIn('jumlo_split_parts_preview',editor)
        self.assertIn('jumlo_clear_markers',editor)
        draft=source('model/WorkspaceDraft.kt')
        self.assertIn('"BOUNDARIES"',draft)
        handler=source('data/processing/PdfOperationHandler.kt')
        self.assertIn('PdfSplitPlan.splitAfter(',handler)

if __name__=='__main__': unittest.main(verbosity=2)
