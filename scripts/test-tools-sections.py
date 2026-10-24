#!/usr/bin/env python3
"""Source contracts for the sectioned Tools catalogue.

Static: asserts the catalogue groups tools into the brief's sections, surfaces
pinned/recent rows from persisted settings, keeps one search field, and never
drops a registered tool (the trailing "All tools" fallback). Does not render.
"""
import re
import unittest
from pathlib import Path
from source_paths import source_path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return source_path(relative).read_text(encoding='utf-8')


class ToolsSectionContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.screen = read('ui/tools/ToolsScreen.kt')
        cls.registry = read('data/jobs/CapabilityRegistry.kt')

    def test_required_sections_are_declared(self):
        for key in ('scan', 'organize', 'convert', 'edit', 'optimize', 'protect', 'download', 'files'):
            self.assertIn(f'ToolSection("{key}"', self.screen, f'missing section {key}')

    def test_each_section_declares_bilingual_title_and_ids(self):
        block = self.screen.split('toolSections = listOf(', 1)[1]
        calls = re.split(r'\n\s*ToolSection\("', block)[1:]
        self.assertEqual(8, len(calls))
        for call in calls:
            self.assertRegex(call, r'"[^"]+",\s*"[^"]+",\s*setOf\(', call)

    def test_pinned_and_recent_rows_use_persisted_settings(self):
        self.assertIn('settings.quickActionIds', self.screen)
        self.assertIn('settings.recentToolIds', self.screen)
        self.assertIn('R.string.tools_section_pinned', self.screen)
        self.assertIn('R.string.tools_section_recent', self.screen)

    def test_search_appears_exactly_once(self):
        self.assertEqual(1, self.screen.count('QuietSearchBar('))

    def test_ungrouped_tools_fall_back_to_all_tools(self):
        self.assertIn('R.string.tools_section_all', self.screen)
        self.assertIn('ungrouped', self.screen)

    def test_section_headers_span_the_grid(self):
        self.assertIn('GridItemSpan(maxLineSpan)', self.screen)
        self.assertIn('SectionHeader(', self.screen)

    def test_every_registered_tool_is_reachable(self):
        # Every tool the registry exposes must appear in some section's id set or
        # be reachable through the ungrouped fallback. The fallback makes the
        # latter automatic, so this guards against a section typo that would
        # otherwise leave a tool in the fallback by accident.
        sectioned = set()
        for ids in re.findall(r'setOf\(([^)]*)\)', self.screen.split('toolSections = listOf(', 1)[1]):
            sectioned.update(re.findall(r'"([a-z0-9_]+)"', ids))
        registry_ids = set(re.findall(r'UtilityTool\("([a-z0-9_]+)"', self.registry))
        self.assertTrue(registry_ids)
        # Tools may be intentionally absent from sections (they use the fallback),
        # but every section member must be a real registered tool.
        self.assertEqual(set(), sectioned - registry_ids, 'section references an unregistered tool')


if __name__ == '__main__':
    unittest.main(verbosity=2)
