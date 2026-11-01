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
NEW_TOOLS = ("pdf_compare", "pdf_redact", "office_to_pdf")


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


class NewToolsTest(unittest.TestCase):
    def test_registry_entries_have_bilingual_copy(self):
        registry = read("app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt")
        for tool in NEW_TOOLS:
            line = next((ln for ln in registry.splitlines() if f'UtilityTool("{tool}"' in ln), None)
            self.assertIsNotNone(line, f"{tool} missing from registry")
            fields = re.findall(r'"([^"]*)"', line)
            # id, route, titleEn, titleAr, subtitleEn, subtitleAr, icon
            # (the pillar is an unquoted enum, so it is not a quoted field).
            self.assertGreaterEqual(len(fields), 7, f"{tool} registry line is incomplete")
            self.assertTrue(fields[2] and fields[3], f"{tool} needs EN+AR titles")
            self.assertTrue(fields[4] and fields[5], f"{tool} needs EN+AR subtitles")

    def test_policy_marks_new_tools_processing(self):
        policy = read("core-common/src/main/kotlin/com/jumlo/app/core/CapabilityPolicy.kt")
        processing = policy.split("private val processing =")[1].split("fun availability")[0]
        for tool in NEW_TOOLS:
            self.assertIn(f'"{tool}"', processing, f"{tool} not in processing set")

    def test_dispatcher_routes_new_tools(self):
        dispatcher = read("app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt")
        body = dispatcher.split("private suspend fun dispatch")[1]
        self.assertIn('"pdf_compare","pdf_redact"', body.replace(" ", ""))
        self.assertIn('"office_to_pdf" -> officeToPdf.execute', body)
        self.assertIn("private val officeToPdf", dispatcher)

    def test_draft_branches_and_defaults(self):
        draft = read("app/src/main/java/com/jumlo/app/model/WorkspaceDraft.kt")
        for tool in NEW_TOOLS:
            self.assertIn(f'"{tool}" ->', draft, f"{tool} missing executionSettings branch")
        self.assertIn("val redactRegions", draft)
        self.assertIn('"pdf_compare"->"Comparison.txt"', draft.replace(" ", ""))
        self.assertIn('"office_to_pdf"->"Document.pdf"', draft.replace(" ", ""))

    def test_catalogue_and_grid_sections(self):
        catalogue = read("core-common/src/main/kotlin/com/jumlo/app/core/ToolCatalogue.kt")
        self.assertIn('"office_to_pdf"', catalogue.split("primary = listOf(")[1].split(")")[0])
        merged = catalogue.split("merged = listOf(")[1]
        self.assertIn('"pdf_compare"', merged)
        self.assertIn('"pdf_redact"', merged)
        sections = read("app/src/main/java/com/jumlo/app/ui/tools/ToolsScreen.kt")
        self.assertIn('"pdf_compare"', sections.split('"organize"')[1].split("ToolSection(")[0])
        self.assertIn('"office_to_pdf"', sections.split('"convert"')[1].split("ToolSection(")[0])
        self.assertIn('"pdf_redact"', sections.split('"edit"')[1].split("ToolSection(")[0])

    def test_icon_mappings(self):
        icons = read("app/src/main/java/com/jumlo/app/ui/components/ToolIcons.kt")
        self.assertIn('"pdf_compare" to R.drawable.ic_tool_img_compare', icons)
        self.assertIn('"pdf_redact" to R.drawable.ic_prism_shield', icons)

    def test_pdf_handler_methods(self):
        handler = read("app/src/main/java/com/jumlo/app/data/processing/PdfOperationHandler.kt")
        self.assertIn('"pdf_compare" -> compare(request,context)', handler)
        self.assertIn('"pdf_redact" -> redact(request,context)', handler)
        self.assertIn("private suspend fun compare(", handler)
        self.assertIn("private suspend fun redact(", handler)
        self.assertIn("PdfBoxBackend.compare(a,b)", handler)
        self.assertIn("PdfBoxBackend.redact(doc,regionsByPage)", handler)
        self.assertIn("pageFallbacks", handler)
        self.assertIn("importPage(doc.getPage(index))", handler)

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

    def test_redact_editor_regions(self):
        editor = read("app/src/main/java/com/jumlo/app/ui/workspace/VisualDocumentEditor.kt")
        self.assertIn('tool=="pdf_redact"', editor)
        self.assertIn("visual_redact_add_region", editor)
        self.assertIn("redactRegions=it.redactRegions", editor)
        screen = read("app/src/main/java/com/jumlo/app/ui/workspace/WorkspaceScreen.kt")
        self.assertIn('"pdf_to_docx","pdf_to_pptx","pdf_ocr","office_to_pdf"', screen.replace(" ", ""))
        self.assertIn("rebuild_office_note", screen)
        count_valid = screen.split("val countValid=when(tool)")[1].split("val renameInput")[0]
        self.assertIn('"pdf_redact"', count_valid)

    def test_message_mappings(self):
        messages = read("app/src/main/java/com/jumlo/app/ui/components/UserMessages.kt")
        for code in ("OFFICE_INVALID", "OFFICE_NO_CONTENT", "OFFICE_RASTER_TEXT",
                     "OFFICE_UNSUPPORTED_CONTENT", "PDF_COMPARE_DIFFERENT",
                     "REDACT_PARTIAL", "INVALID_REDACT_REGION"):
            self.assertIn(f'code=="{code}"', messages, f"{code} unmapped")

    def test_strings_exist_in_both_locales_and_parse(self):
        needed = {
            "rebuild_tools.xml": ("rebuild_office_note", "rebuild_office_invalid",
                                  "rebuild_office_empty", "rebuild_office_raster",
                                  "rebuild_office_unsupported"),
            "error_messages.xml": ("pdf_compare_different", "redact_partial"),
            "visual_editor.xml": ("visual_redact_add_region", "visual_redact_region_count",
                                  "visual_redact_remove_region", "visual_redact_clear_regions",
                                  "visual_redact_need_region"),
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
