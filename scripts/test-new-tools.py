#!/usr/bin/env python3
"""Verify the three tools added 2026-11-01 are wired end to end, statically.

pdf_compare, pdf_redact and office_to_pdf must each appear in: the registry,
the processing policy, the dispatcher, the draft's execution settings, the
tool catalogue, a Tools grid section, and (for compare/redact) the icon map.
Their handler code, reader (office), editor regions (redact) and EN+AR copy
must exist. This is a static wiring gate, not a runtime pick->result test.
Run with no arguments from the repo root.
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ACTIVE_NEW_TOOLS = ("office_to_pdf",)
RETIRED_NEW_TOOLS = ("pdf_compare", "pdf_redact")


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


class NewToolsTest(unittest.TestCase):
    def test_registry_entries_have_bilingual_copy(self):
        registry = read("app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt")
        for tool in ACTIVE_NEW_TOOLS:
            line = next((ln for ln in registry.splitlines() if f'UtilityTool("{tool}"' in ln), None)
            self.assertIsNotNone(line, f"{tool} missing from registry")
            fields = re.findall(r'"([^"]*)"', line)
            self.assertGreaterEqual(len(fields), 7, f"{tool} registry line is incomplete")
            self.assertTrue(fields[2] and fields[3], f"{tool} needs EN+AR titles")
            self.assertTrue(fields[4] and fields[5], f"{tool} needs EN+AR subtitles")
        for tool in RETIRED_NEW_TOOLS:
            self.assertNotIn(f'UtilityTool("{tool}"', registry)

    def test_policy_marks_office_processing_and_retires_compare_redact(self):
        policy = read("core-common/src/main/kotlin/com/jumlo/app/core/CapabilityPolicy.kt")
        processing = policy.split("private val processing =")[1].split("fun availability")[0]
        for tool in ACTIVE_NEW_TOOLS:
            self.assertIn(f'"{tool}"', processing, f"{tool} not in processing set")
        for tool in RETIRED_NEW_TOOLS:
            self.assertNotIn(f'"{tool}"', processing, f"{tool} must remain retired")

    def test_dispatcher_routes_office_to_pdf(self):
        dispatcher = read("app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt")
        body = dispatcher.split("private suspend fun dispatch")[1]
        self.assertIn('"office_to_pdf" -> officeToPdf.execute', body)
        self.assertIn("private val officeToPdf", dispatcher)

    def test_draft_branches_and_defaults(self):
        draft = read("app/src/main/java/com/jumlo/app/model/WorkspaceDraft.kt")
        self.assertIn('"office_to_pdf" ->', draft)
        self.assertIn('"office_to_pdf"->"Document.pdf"', draft.replace(" ", ""))

    def test_catalogue_and_grid_sections(self):
        catalogue = read("core-common/src/main/kotlin/com/jumlo/app/core/ToolCatalogue.kt")
        self.assertIn('"office_to_pdf"', catalogue.split("primary = listOf(")[1].split(")")[0])
        sections = read("app/src/main/java/com/jumlo/app/ui/tools/ToolsScreen.kt")
        self.assertIn('"office_to_pdf"', sections.split('"convert"')[1].split("ToolSection(")[0])

    def test_icon_mappings(self):
        icons = read("app/src/main/java/com/jumlo/app/ui/components/ToolIcons.kt")
        self.assertIn('"office_to_pdf" to R.drawable.ic_tool_pdf_to_docx', icons)

    def test_office_handler_and_reader(self):
        handler = read("app/src/main/java/com/jumlo/app/data/processing/OfficeToPdfOperationHandler.kt")
        self.assertIn('"office_to_pdf"', handler)
        self.assertIn("OfficeDocumentReader.detect", handler)
        self.assertIn("OfficeDocumentReader.read", handler)
        self.assertIn("OFFICE_NO_CONTENT", handler)
        self.assertIn("PDF_VERIFY_FAILED", handler)
        reader = read("document-core/src/main/kotlin/com/jumlo/app/core/office/OfficeDocumentReader.kt")
        for token in ("readDocx", "readXlsx", "readPptx", "disallow-doctype-decl",
                      "OFFICE_INVALID", "OFFICE_SIZE_LIMIT"):
            self.assertIn(token, reader, f"reader missing {token}")
        self.assertIn("OfficeDocumentReaderTest", read(
            "document-core/src/test/kotlin/com/jumlo/app/core/office/OfficeDocumentReaderTest.kt"))

    def test_workspace_wires_office_to_pdf(self):
        screen = read("app/src/main/java/com/jumlo/app/ui/workspace/WorkspaceScreen.kt")
        self.assertIn('"pdf_to_docx","pdf_to_pptx","pdf_ocr","office_to_pdf"', screen.replace(" ", ""))
        self.assertIn("rebuild_office_note", screen)

    def test_message_mappings(self):
        messages = read("app/src/main/java/com/jumlo/app/ui/components/UserMessages.kt")
        for code in ("OFFICE_INVALID", "OFFICE_NO_CONTENT", "OFFICE_RASTER_TEXT",
                     "OFFICE_UNSUPPORTED_CONTENT"):
            self.assertIn(f'code=="{code}"', messages, f"{code} unmapped")

    def test_strings_exist_in_both_locales_and_parse(self):
        needed = {
            "rebuild_tools.xml": ("rebuild_office_note", "rebuild_office_invalid",
                                  "rebuild_office_empty", "rebuild_office_raster",
                                  "rebuild_office_unsupported"),
        }
        for filename, names in needed.items():
            for locale in ("values", "values-ar"):
                path = ROOT / "app/src/main/res" / locale / filename
                tree = ET.parse(path)  # raises on malformed XML
                found = {el.get("name") for el in tree.getroot().iter("string")}
                for name in names:
                    self.assertIn(name, found, f"{name} missing from {locale}/{filename}")


if __name__ == "__main__":
    unittest.main()
