#!/usr/bin/env python3
"""Phase 5 verification: static contract for the Kotlin unit-test suites.

kotlinc/Gradle cannot run in this sandbox, so this asserts the JUnit test files
exist and cover the required areas (page-op logic + color-scheme contrast). In a
proper environment these run via `./gradlew :app:testDebugUnitTest`.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return (ROOT / relative).read_text(encoding='utf-8')


class KotlinUnitTestContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.page = read('app/src/test/java/com/jumlo/app/core/PageOperationsTest.kt')
        cls.color = read('app/src/test/java/com/jumlo/app/ui/theme/M3ColorFactoryTest.kt')

    def test_page_operations_test_exists_and_covers_required_areas(self):
        self.assertIn('class PageOperationsTest', self.page)
        self.assertIn('@Test', self.page)
        for area in ('move', 'rotate', 'deleteSelected', 'extractSelected', 'splitEvery',
                     'merge', 'undo', 'redo', 'PageSelection.parse', 'PdfPageOrder.parse',
                     'PdfSplitPlan.splitAfter'):
            self.assertIn(area, self.page, f'page-op test missing coverage for {area}')

    def test_page_operations_test_uses_junit4(self):
        self.assertIn('import org.junit.Test', self.page)
        self.assertIn('import org.junit.Assert.*', self.page)

    def test_color_test_exists_and_covers_contrast_and_scheme(self):
        self.assertIn('class M3ColorFactoryTest', self.color)
        self.assertIn('@Test', self.color)
        for area in ('contrast(', 'ensureContrast(', 'seedFor(', 'hexColor(', 'scheme(',
                     'meetsAA(', 'isValidAccent(', 'hsvOf(', 'amoled'):
            self.assertIn(area, self.color, f'color test missing coverage for {area}')

    def test_color_test_uses_compose_color(self):
        self.assertIn('import androidx.compose.ui.graphics.Color', self.color)

    def test_both_tests_have_multiple_cases(self):
        self.assertGreaterEqual(self.page.count('@Test'), 15, 'page-op suite should have many cases')
        self.assertGreaterEqual(self.color.count('@Test'), 10, 'color suite should have many cases')

    def test_no_android_runtime_required(self):
        # These are pure-logic / pure-Compose tests; they must not need a device.
        for f in (self.page, self.color):
            self.assertNotIn('AndroidJUnit4', f)
            self.assertNotIn('@RunWith(AndroidJUnit4', f)


if __name__ == '__main__':
    unittest.main()
