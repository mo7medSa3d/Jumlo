#!/usr/bin/env python3
"""Contract tests for FilesScreen folders, iOS-style drag-and-drop, and smart folder actions."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/jumlo/app"


class FilesFoldersAndDragDropTests(unittest.TestCase):
    def test_model_and_datastore_persist_folders_and_assignments(self):
        models = (APP / "model/Models.kt").read_text(encoding="utf-8")
        self.assertIn("data class FileFolder(", models)
        self.assertIn("val fileFolders: List<FileFolder> = emptyList()", models)
        self.assertIn("val outputFolderIds: Map<String, String> = emptyMap()", models)

        prefs = (APP / "data/preferences/UserPreferencesRepository.kt").read_text(encoding="utf-8")
        for token in ("FILE_FOLDERS", "OUTPUT_FOLDER_IDS", "fun createFileFolder(",
                      "fun renameFileFolder(", "fun deleteFileFolder(", "fun moveOutputsToFolder("):
            self.assertIn(token, prefs)

        vm = (APP / "ui/AppViewModel.kt").read_text(encoding="utf-8")
        for token in ("fun createFileFolder(", "fun renameFileFolder(",
                      "fun deleteFileFolder(", "fun moveOutputsToFolder("):
            self.assertIn(token, vm)

    def test_drag_and_drop_engine_supports_long_press_drag_and_ghost(self):
        dnd = (APP / "ui/files/FileDragDrop.kt").read_text(encoding="utf-8")
        for token in ("class FileDragDropState", "detectDragGesturesAfterLongPress",
                      "fun Modifier.fileDropTarget(", "fun Modifier.fileDragSource(",
                      "fun FloatingDragGhost(", "ROOT_DROP_TARGET_ID"):
            self.assertIn(token, dnd)

    def test_folder_components_provide_smart_actions_and_page_long_press_sheet(self):
        comp = (APP / "ui/files/FileFolderComponents.kt").read_text(encoding="utf-8")
        for token in ("fun FolderShelf(", "fun FolderBreadcrumbBar(",
                      "fun FolderActionsSheet(", "fun PageQuickActionsSheet(",
                      "fun FolderNameDialog(", "fun MoveToFolderDialog(",
                      "LocalReducedMotion", "QuietTheme.shapes."):
            self.assertIn(token, comp)
        self.assertNotIn("RoundedCornerShape(", comp)

    def test_files_screen_wires_drag_drop_page_long_press_and_smart_workflows(self):
        screen = (APP / "ui/files/FilesScreen.kt").read_text(encoding="utf-8")
        for token in ("rememberFileDragDropState()", "detectTapGestures(", "pageActionsOpen = true",
                      "FolderShelf(", "FolderBreadcrumbBar(", "FolderActionsSheet(",
                      "PageQuickActionsSheet(", "MoveToFolderDialog(", "FloatingDragGhost(",
                      "fileDragSource(", '"file_07"', '"pdf_03"', '"pdf_02"', '"img_04"'):
            self.assertIn(token, screen)

    def test_bilingual_strings_parity_and_xml_valid(self):
        en_root = ET.parse(ROOT / "app/src/main/res/values/strings.xml").getroot()
        ar_root = ET.parse(ROOT / "app/src/main/res/values-ar/strings.xml").getroot()
        en_keys = {el.attrib["name"] for el in en_root if el.tag == "string"}
        ar_keys = {el.attrib["name"] for el in ar_root if el.tag == "string"}
        self.assertEqual(en_keys, ar_keys)
        for req in ("files_folders_heading", "files_folder_action_zip",
                    "files_folder_action_merge_pdf", "files_folder_action_images_pdf",
                    "files_page_actions_title", "files_move_to_folder"):
            self.assertIn(req, en_keys)
            self.assertIn(req, ar_keys)


if __name__ == "__main__":
    unittest.main(verbosity=2)
