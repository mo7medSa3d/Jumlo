#!/usr/bin/env python3
"""Behavior tests for the read-only source candidate inventory."""
from pathlib import Path
import json
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_source import inventory, scan_source, write_report


class SourceAuditTests(unittest.TestCase):
    def test_todo_and_fixme_include_exact_line_numbers(self):
        findings = scan_source(
            "app/src/main/java/example/Tools.kt",
            "fun foo() {}\n// TODO: wire real execution\n// FIXME: record failure\n",
        )
        self.assertEqual([("todo_marker", 2), ("todo_marker", 3)],
                         [(x.kind, x.line) for x in findings])

    def test_empty_handlers_detected_across_line_breaks(self):
        findings = scan_source(
            "app/src/main/java/example/Tools.kt",
            "Button(onClick = {\n    }) {}\nSwitch(checked=true, onCheckedChange = { })",
        )
        self.assertEqual([("empty_callback", 1), ("empty_callback", 3)],
                         [(x.kind, x.line) for x in findings])

    def test_theme_tokens_not_mistaken_for_hardcoded_chrome_bypass(self):
        theme = scan_source("app/src/main/java/com/jumlo/app/ui/theme/Color.kt",
                            "val Primary = Color(0xFF5566FF)")
        self.assertEqual([], theme)
        chrome = scan_source("app/src/main/java/com/jumlo/app/ui/files/FilesScreen.kt",
                             "val badge = Color(0xFF5566FF)")
        self.assertEqual(1, len(chrome))
        self.assertEqual("hardcoded_color", chrome[0].kind)

    def test_inline_text_detected_but_resource_text_not_flagged(self):
        src = 'Text("Hardcoded")\nText(stringResource(R.string.label))\n'
        findings = scan_source("app/src/main/java/example/Screen.kt", src)
        self.assertEqual([1], [x.line for x in findings if x.kind == "hardcoded_ui_text"])

    def test_generated_reports_are_reproducible_and_include_source_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            src = root / "app/src/main/java/example/Tool.kt"
            src.parent.mkdir(parents=True)
            src.write_text("// TODO: add test\nval value = Color(0xFF121212)\n", encoding="utf-8")
            report = inventory(root)
            self.assertEqual(1, report["source_files_scanned"])
            self.assertEqual(2, report["total_candidates"])
            json_path = root / "build/reports/audit.json"
            md_path = root / "build/reports/audit.md"
            write_report(report, json_path, md_path)
            parsed = json.loads(json_path.read_text(encoding="utf-8"))
            self.assertEqual(report, parsed)
            self.assertIn("Tool.kt:1", md_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
