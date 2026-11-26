package com.jumlo.app.data.office

import com.jumlo.app.core.office.OfficeBlock
import com.jumlo.app.core.office.OfficeCell
import com.jumlo.app.core.office.OfficeDocument
import com.jumlo.app.core.office.OfficeKind
import com.jumlo.app.core.office.OfficeParagraph
import com.jumlo.app.core.office.OfficeRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentViewerFormatsTest {

    @Test
    fun detectMapsSupportedMimeTypesAndExtensionsAndRejectsLegacyOrBinary() {
        assertEquals(DocumentViewerKind.PDF, DocumentViewerFormats.detect("application/pdf", null))
        assertEquals(DocumentViewerKind.PDF, DocumentViewerFormats.detect(null, "Invoice.PDF"))

        assertEquals(
            DocumentViewerKind.WORD,
            DocumentViewerFormats.detect("application/vnd.openxmlformats-officedocument.wordprocessingml.document", null)
        )
        assertEquals(DocumentViewerKind.WORD, DocumentViewerFormats.detect("application/octet-stream", "proposal.docx"))

        assertEquals(
            DocumentViewerKind.PRESENTATION,
            DocumentViewerFormats.detect("application/vnd.openxmlformats-officedocument.presentationml.presentation", null)
        )
        assertEquals(DocumentViewerKind.PRESENTATION, DocumentViewerFormats.detect(null, "deck.pptx"))

        assertEquals(
            DocumentViewerKind.SPREADSHEET,
            DocumentViewerFormats.detect("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null)
        )
        assertEquals(DocumentViewerKind.SPREADSHEET, DocumentViewerFormats.detect(null, "budget.xlsx"))

        assertEquals(DocumentViewerKind.TEXT, DocumentViewerFormats.detect("text/plain; charset=utf-8", null))
        assertEquals(DocumentViewerKind.TEXT, DocumentViewerFormats.detect("text/markdown", null))
        assertEquals(DocumentViewerKind.TEXT, DocumentViewerFormats.detect("text/csv", null))
        assertEquals(DocumentViewerKind.TEXT, DocumentViewerFormats.detect("text/tab-separated-values", null))
        assertEquals(DocumentViewerKind.TEXT, DocumentViewerFormats.detect("application/json", null))
        for (name in listOf("notes.txt", "README.md", "export.csv", "data.json", "app.log", "config.xml", "index.html", "spec.yaml")) {
            assertEquals(DocumentViewerKind.TEXT, DocumentViewerFormats.detect(null, name))
            assertTrue(DocumentViewerFormats.supportsInAppViewer(null, name))
        }

        assertEquals(DocumentViewerKind.IMAGE, DocumentViewerFormats.detect("image/png", "photo.png"))
        assertEquals(DocumentViewerKind.IMAGE, DocumentViewerFormats.detect(null, "scan.webp"))
        assertEquals(DocumentViewerKind.AUDIO, DocumentViewerFormats.detect("audio/mpeg", "voice.mp3"))
        assertEquals(DocumentViewerKind.AUDIO, DocumentViewerFormats.detect(null, "memo.m4a"))
        assertEquals(DocumentViewerKind.VIDEO, DocumentViewerFormats.detect("video/mp4", "clip.mp4"))
        assertEquals(DocumentViewerKind.ARCHIVE, DocumentViewerFormats.detect("application/zip", "archive.zip"))

        // Legacy binary Office formats remain external-only.
        assertNull(DocumentViewerFormats.detect("application/msword", "legacy.doc"))
        assertNull(DocumentViewerFormats.detect("application/vnd.ms-powerpoint", "legacy.ppt"))
        assertNull(DocumentViewerFormats.detect("application/vnd.ms-excel", "legacy.xls"))
        assertTrue(DocumentViewerFormats.supportsInAppViewer("application/zip", "archive.zip"))
    }

    @Test
    fun detectRejectsBlankOrExtensionlessInputsAndNormalizesPaddedMime() {
        assertNull(DocumentViewerFormats.detect(null, null))
        assertNull(DocumentViewerFormats.detect("   ", "   "))
        assertNull(DocumentViewerFormats.detect(null, "README"))
        assertNull(DocumentViewerFormats.detect(null, "notes."))
        assertEquals(DocumentViewerKind.TEXT, DocumentViewerFormats.detect(" TEXT/CSV ; charset=UTF-8 ", null))
        assertEquals(DocumentViewerKind.WORD, DocumentViewerFormats.detect("   ", "  report.DOCX  "))
    }

    @Test
    fun toLoadedDocumentSplitsPptxSlidesExtractsTitlesAndClampsAspectRatio() {
        val doc = OfficeDocument(
            kind = OfficeKind.PPTX,
            pageWidthPt = 960.0,
            pageHeightPt = 540.0,
            marginPt = 36.0,
            blocks = listOf(
                OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("Intro note")), level = 0)),
                OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("  Q4 Launch  ")), level = 1)),
                OfficeBlock.PageBreak,
                OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("Fallback Title")), level = 0)),
                OfficeBlock.PageBreak // trailing empty slide should be filtered
            ),
            unsupported = listOf("CHART")
        )

        val loaded = DocumentViewerLoader.toLoadedDocument(doc, "deck.pptx", 12_345L) as LoadedDocument.Presentation
        assertEquals("deck.pptx", loaded.displayName)
        assertEquals(12_345L, loaded.sizeBytes)
        assertEquals(16f / 9f, loaded.aspectRatio, 0.001f)
        assertEquals(2, loaded.slides.size)
        assertEquals(0, loaded.slides[0].index)
        assertEquals("Q4 Launch", loaded.slides[0].title)
        assertEquals(2, loaded.slides[0].blocks.size)
        assertEquals(1, loaded.slides[1].index)
        assertEquals("Fallback Title", loaded.slides[1].title)
        assertEquals(listOf("CHART"), loaded.unsupported)

        // Invalid page dimensions default to 16:9; extreme ratios clamp to [0.65f, 2.2f].
        val invalidRatio = DocumentViewerLoader.toLoadedDocument(
            doc.copy(pageWidthPt = 0.0, pageHeightPt = 0.0),
            "deck.pptx",
            100L
        ) as LoadedDocument.Presentation
        assertEquals(16f / 9f, invalidRatio.aspectRatio, 0.001f)

        val ultraWide = DocumentViewerLoader.toLoadedDocument(
            doc.copy(pageWidthPt = 3000.0, pageHeightPt = 500.0),
            "wide.pptx",
            100L
        ) as LoadedDocument.Presentation
        assertEquals(2.2f, ultraWide.aspectRatio, 0.001f)
    }

    @Test
    fun toLoadedDocumentExtractsDocxHeadingsWordCountAndPageBreaks() {
        val doc = OfficeDocument(
            kind = OfficeKind.DOCX,
            pageWidthPt = 612.0,
            pageHeightPt = 792.0,
            marginPt = 56.0,
            blocks = listOf(
                OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("Project Overview")), level = 1)),
                OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("   ")), level = 2)),
                OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("Three simple words")), level = 0)),
                OfficeBlock.TableBlock(
                    rows = listOf(
                        listOf(OfficeCell("Col A"), OfficeCell("Col B")),
                        listOf(OfficeCell("Val 1"), OfficeCell("Val 2"))
                    ),
                    headerRow = true
                ),
                OfficeBlock.PageBreak,
                OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("  Next Steps ")), level = 3))
            ),
            unsupported = listOf("HEADER_FOOTER")
        )

        val loaded = DocumentViewerLoader.toLoadedDocument(doc, "spec.docx", 4096L) as LoadedDocument.Word
        assertEquals("spec.docx", loaded.displayName)
        assertEquals(4096L, loaded.sizeBytes)
        assertEquals(6, loaded.blocks.size)
        assertEquals(
            listOf(
                OutlineHeading(blockIndex = 0, level = 1, text = "Project Overview"),
                OutlineHeading(blockIndex = 5, level = 3, text = "Next Steps")
            ),
            loaded.headings
        )
        // 2 (Project Overview) + 3 (Three simple words) + 8 (table cells) + 2 (Next Steps) = 15
        assertEquals(15, loaded.wordCount)
        assertEquals(1, loaded.pageBreakCount)
        assertEquals(listOf("HEADER_FOOTER"), loaded.unsupported)
    }

    @Test
    fun toLoadedDocumentSplitsXlsxSheetsAndFallsBackWhenNoSectionExists() {
        val table1 = OfficeBlock.TableBlock(listOf(listOf(OfficeCell("A1"))))
        val table2 = OfficeBlock.TableBlock(listOf(listOf(OfficeCell("B1"))))
        val multiSheet = OfficeDocument(
            kind = OfficeKind.XLSX,
            pageWidthPt = 792.0,
            pageHeightPt = 612.0,
            marginPt = 36.0,
            blocks = listOf(
                OfficeBlock.Section("Summary"),
                table1,
                OfficeBlock.Section("Details"),
                table2
            ),
            unsupported = emptyList()
        )

        val loaded = DocumentViewerLoader.toLoadedDocument(multiSheet, "report.xlsx", 2048L) as LoadedDocument.Spreadsheet
        assertEquals(2, loaded.sheets.size)
        assertEquals("Summary", loaded.sheets[0].title)
        assertEquals(listOf(table1), loaded.sheets[0].blocks)
        assertEquals("Details", loaded.sheets[1].title)
        assertEquals(listOf(table2), loaded.sheets[1].blocks)

        val noSection = multiSheet.copy(blocks = listOf(table1))
        val fallback = DocumentViewerLoader.toLoadedDocument(noSection, "report.xlsx", 1024L) as LoadedDocument.Spreadsheet
        assertEquals(1, fallback.sheets.size)
        assertEquals("report.xlsx", fallback.sheets[0].title)
        assertEquals(listOf(table1), fallback.sheets[0].blocks)
    }

    @Test
    fun toLoadedDocumentHandlesEmptyDocxPptxAndXlsxEdgeCases() {
        val emptyDocx = DocumentViewerLoader.toLoadedDocument(
            OfficeDocument(OfficeKind.DOCX, 612.0, 792.0, 56.0, emptyList(), emptyList()),
            "empty.docx",
            0L
        ) as LoadedDocument.Word
        assertTrue(emptyDocx.blocks.isEmpty())
        assertTrue(emptyDocx.headings.isEmpty())
        assertEquals(0, emptyDocx.wordCount)
        assertEquals(0, emptyDocx.pageBreakCount)

        val breakOnlyPptx = DocumentViewerLoader.toLoadedDocument(
            OfficeDocument(OfficeKind.PPTX, 100.0, 1000.0, 36.0, listOf(OfficeBlock.PageBreak, OfficeBlock.PageBreak), emptyList()),
            "empty.pptx",
            0L
        ) as LoadedDocument.Presentation
        assertTrue(breakOnlyPptx.slides.isEmpty())
        assertEquals(0.65f, breakOnlyPptx.aspectRatio, 0.001f)

        val blankTitlePptx = DocumentViewerLoader.toLoadedDocument(
            OfficeDocument(
                OfficeKind.PPTX,
                960.0,
                540.0,
                36.0,
                listOf(OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("   ")), level = 1))),
                emptyList()
            ),
            "blank.pptx",
            64L
        ) as LoadedDocument.Presentation
        assertEquals(1, blankTitlePptx.slides.size)
        assertNull(blankTitlePptx.slides[0].title)

        val emptyXlsx = DocumentViewerLoader.toLoadedDocument(
            OfficeDocument(OfficeKind.XLSX, 792.0, 612.0, 36.0, emptyList(), emptyList()),
            "empty.xlsx",
            0L
        ) as LoadedDocument.Spreadsheet
        assertEquals(1, emptyXlsx.sheets.size)
        assertEquals("empty.xlsx", emptyXlsx.sheets[0].title)
        assertTrue(emptyXlsx.sheets[0].blocks.isEmpty())

        val table1 = OfficeBlock.TableBlock(listOf(listOf(OfficeCell("A1"))))
        val table2 = OfficeBlock.TableBlock(listOf(listOf(OfficeCell("B1"))))
        val leadingAndBlankSection = DocumentViewerLoader.toLoadedDocument(
            OfficeDocument(
                OfficeKind.XLSX,
                792.0,
                612.0,
                36.0,
                listOf(table1, OfficeBlock.Section("   "), table2),
                emptyList()
            ),
            "mixed.xlsx",
            512L
        ) as LoadedDocument.Spreadsheet
        assertEquals(2, leadingAndBlankSection.sheets.size)
        assertEquals("mixed.xlsx", leadingAndBlankSection.sheets[0].title)
        assertEquals(listOf(table1), leadingAndBlankSection.sheets[0].blocks)
        assertEquals("Sheet 2", leadingAndBlankSection.sheets[1].title)
        assertEquals(listOf(table2), leadingAndBlankSection.sheets[1].blocks)
    }

    @Test
    fun parseCsvLinesHandlesQuotedCommasEscapedQuotesAndEmptyFields() {
        val lines = listOf(
            "Name,Location,Note",
            "\"Ada, Lovelace\",\"London, UK\",\"She said \"\"hello\"\"\"",
            "Bob,,\"Trailing\"",
            ""
        )

        val rows = DocumentViewerLoader.parseCsvLines(lines)
        assertEquals(3, rows.size)
        assertEquals(listOf("Name", "Location", "Note"), rows[0])
        assertEquals(listOf("Ada, Lovelace", "London, UK", "She said \"hello\""), rows[1])
        assertEquals(listOf("Bob", "", "Trailing"), rows[2])
    }

    @Test
    fun parseCsvLinesHandlesBlankOnlyMultiLineQuotedAndUnterminatedFields() {
        assertTrue(DocumentViewerLoader.parseCsvLines(emptyList()).isEmpty())
        assertTrue(DocumentViewerLoader.parseCsvLines(listOf("", "")).isEmpty())

        val multiline = DocumentViewerLoader.parseCsvLines(listOf("\"line1", "", "line2\",col2"))
        assertEquals(listOf(listOf("line1\n\nline2", "col2")), multiline)

        val unterminated = DocumentViewerLoader.parseCsvLines(listOf("\"unterminated,cell"))
        assertEquals(listOf(listOf("unterminated,cell\n")), unterminated)
    }
}
