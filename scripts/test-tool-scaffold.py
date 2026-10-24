#!/usr/bin/env python3
"""Source contracts for the Phase 1 shared ToolScaffold.

Static: asserts ToolScaffold/ToolProgressSheet/ToolResultScreen exist, reuse the
workspace top bar, apply navigation-bar insets to the bottom bar, and that the
result action model + action_print string are wired. Does not render.
"""
import unittest
from pathlib import Path
from source_paths import source_path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return source_path(relative).read_text(encoding='utf-8')


class ToolScaffoldContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src = read('ui/components/ToolScaffold.kt')

    def test_scaffold_shell_exists_with_required_params(self):
        self.assertIn('fun ToolScaffold(', self.src)
        for param in ('title: String', 'subtitle: String? = null', 'badgeText: String? = null',
                      'pillar: PillarCategory = PillarCategory.PDF', 'onBack: () -> Unit',
                      'bottomActions: @Composable RowScope.() -> Unit',
                      'content: @Composable ColumnScope.() -> Unit'):
            self.assertIn(param, self.src, f'missing param: {param}')

    def test_scaffold_reuses_workspace_top_bar(self):
        self.assertIn('QuietWorkspaceTopBar(', self.src)

    def test_bottom_bar_applies_navigation_bar_insets(self):
        self.assertIn('WindowInsets.navigationBars', self.src)
        self.assertIn('asPaddingValues()', self.src)
        self.assertIn('calculateBottomPadding()', self.src)

    def test_progress_sheet_and_result_screen_exist(self):
        self.assertIn('fun ToolProgressSheet(visible: Boolean', self.src)
        self.assertIn('fun ToolResultScreen(', self.src)
        self.assertIn('data class ToolResultAction(', self.src)

    def test_result_screen_has_action_buttons_and_close(self):
        self.assertIn('actions: List<ToolResultAction>', self.src)
        self.assertIn('action_close', self.src)
        self.assertIn('onClick = action.onClick', self.src)

    def test_action_print_string_both_locales(self):
        en = (ROOT / 'app/src/main/res/values/strings.xml').read_text(encoding='utf-8')
        ar = (ROOT / 'app/src/main/res/values-ar/strings.xml').read_text(encoding='utf-8')
        self.assertIn('action_print', en)
        self.assertIn('action_print', ar)


if __name__ == '__main__':
    unittest.main()
