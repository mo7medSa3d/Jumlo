#!/usr/bin/env python3
"""Validate Jumlo release qualification rows against the actual source registry/policy.

This checks test planning only. Passing means NO tool has silently disappeared from
the plan; it does NOT mean the tools work or that Android tests have executed.
"""
from collections import Counter, defaultdict
import csv
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt"
POLICY = ROOT / "core-common/src/main/kotlin/com/jumlo/app/core/CapabilityPolicy.kt"
MATRIX = ROOT / "docs/testing/tool-matrix.template.csv"
RETIRED = ROOT / "docs/testing/tool-matrix-retired-ids.csv"
SCENARIOS = {"normal", "large", "encrypted", "corrupt"}


def ids_in_set(source: str, name: str) -> set[str]:
    pat = rf"private val {name}\s*=\s*(?:hostCoreTested\s*\+\s*)?setOf\((.*?)\)"
    match = re.search(pat, source, re.DOTALL)
    if not match:
        raise AssertionError(f"Cannot inspect {name} in CapabilityPolicy")
    without_comments = re.sub(r"//[^\n]*", "", match.group(1))
    return set(re.findall(r'"([^"]+)"', without_comments))


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as file:
        return list(csv.DictReader(file))


def active_ids() -> list[str]:
    source = REGISTRY.read_text(encoding="utf-8")
    return re.findall(r'UtilityTool\(\s*"([^"]+)"', source)


class ToolMatrixContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.registry = active_ids()
        cls.matrix = rows(MATRIX)
        cls.retired = rows(RETIRED)
        src = POLICY.read_text(encoding="utf-8")
        cls.navigation = ids_in_set(src, "navigation")
        cls.processing = ids_in_set(src, "hostCoreTested") | ids_in_set(src, "processing")

    def test_every_current_tool_exactly_four_scenarios(self):
        self.assertEqual(len(self.registry), len(set(self.registry)), "Registry IDs must be unique")
        by_id = defaultdict(list)
        for row in self.matrix:
            by_id[row["tool_id"]].append(row["scenario"])
        self.assertEqual(set(self.registry), set(by_id),
                         "Matrix must exactly match source registry; update on any new/removed tool")
        for tool_id, scenarios in by_id.items():
            self.assertEqual(len(scenarios), 4, tool_id)
            self.assertEqual(set(scenarios), SCENARIOS, tool_id)
        self.assertEqual(len(self.matrix), 4 * len(self.registry))

    def test_every_row_kind_and_status_match_current_source_policy(self):
        for row in self.matrix:
            tool_id = row["tool_id"]
            if tool_id in self.navigation:
                self.assertEqual("NAVIGATION", row["registry_kind"], tool_id)
                self.assertEqual("N/A", row["status"], tool_id)
            else:
                self.assertIn(tool_id, self.processing,
                              f"Registered {tool_id} has no processing/navigation policy")
                self.assertEqual("PROCESSING", row["registry_kind"], tool_id)
                self.assertEqual("NOT_RUN", row["status"], tool_id)
        counts = Counter(row["registry_kind"] for row in self.matrix)
        self.assertEqual(0, counts["UNAVAILABLE"])

    def test_enabled_drive_is_unqualified_processing_not_unavailable(self):
        entries = [row for row in self.matrix if row["tool_id"] == "file_drive"]
        self.assertEqual(4, len(entries))
        for row in entries:
            self.assertEqual(("PROCESSING", "NOT_RUN"),
                             (row["registry_kind"], row["status"]))
            self.assertNotIn("Blocked by CapabilityPolicy", row["reason"])

    def test_retired_ids_are_preserved_outside_active_matrix(self):
        old_ids = {row["tool_id"] for row in self.retired}
        self.assertEqual({"webpage_pdf", "xf_05"}, old_ids)
        self.assertFalse(old_ids & set(self.registry))
        self.assertEqual(8, len(self.retired))
        self.assertTrue(all(row["status"] == "N/A" for row in self.retired))


if __name__ == "__main__":
    unittest.main()
