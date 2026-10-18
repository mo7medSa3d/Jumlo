#!/usr/bin/env python3
"""Source-level UX regressions: these do not replace Compose device tests."""
from pathlib import Path
import unittest
R=Path(__file__).resolve().parents[1]/'app/src/main/java/com/jumlo/app/ui'

class DownloadsExperienceContracts(unittest.TestCase):
    def test_downloads_show_live_transfers_before_finished_history(self):
        src=(R/'downloads/DownloadsScreen.kt').read_text()
        self.assertIn('val activeDownloads=downloads.filter(JobSnapshot::isActive)',src)
        self.assertIn('val previousDownloads=downloads.filterNot(JobSnapshot::isActive)',src)
        self.assertIn('items(activeDownloads',src)
        self.assertIn('items(previousDownloads',src)
        self.assertLess(src.index('items(activeDownloads'),src.index('items(previousDownloads'))

    def test_downloads_no_long_hero_and_tall_empty_stat_cards(self):
        src=(R/'downloads/DownloadsScreen.kt').read_text()
        self.assertNotIn('Modifier.weight(1f).height(84.dp)',src)
        self.assertNotIn('RoundedCornerShape(30.dp)',src)
        self.assertIn('download-summary',src)

    def test_import_has_no_technical_authentication_controls(self):
        src=(R/'downloads/MediaWorkspace.kt').read_text()
        for name in ('media_session_btn','PlatformSessionSheet','showSessionSheet','media_cookies_save_btn'):
            self.assertNotIn(name,src)
        self.assertIn('imePadding()',src)
        self.assertIn('asset.thumbnailUrl',src)

    def test_home_and_files_have_one_clear_visual_header(self):
        home=(R/'home/HomeScreen.kt').read_text()
        files=(R/'files/FilesScreen.kt').read_text()
        self.assertNotIn('modifier=Modifier.pressFeedback().fillMaxWidth(),color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(28.dp)',home)
        self.assertNotIn('Text(stringResource(R.string.redesign_storage_folder),style=QuietTheme.typography.metadata',files.split('item("search")',1)[0])

if __name__=='__main__':unittest.main(verbosity=2)
