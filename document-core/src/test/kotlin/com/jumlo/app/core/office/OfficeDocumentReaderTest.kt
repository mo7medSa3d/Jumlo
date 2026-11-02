package com.jumlo.app.core.office

import java.io.File
import java.util.Base64
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeDocumentReaderTest {
    // 1x1 transparent PNG, so image fixtures are real decodable bytes.
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )

    private fun packageFile(entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("office-test-", ".zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun docxFile(): File {
        val document = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<w:body>
<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>My Doc</w:t></w:r></w:p>
<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter</w:t></w:r></w:p>
<w:p><w:r><w:t>Hello </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>world</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>First item</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>Second item</w:t></w:r></w:p>
<w:tbl><w:tr><w:trPr><w:tblHeader/></w:trPr><w:tc><w:p><w:r><w:t>Name</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>Age</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:p><w:r><w:t>Ada</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>36</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
<w:p><w:r><w:drawing><a:blip xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" r:embed="rId5"/></w:drawing></w:r></w:p>
</w:body></w:document>"""
        val numbering = """<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/></w:lvl></w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num></w:numbering>"""
        val rels = """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/pic.png"/>
</Relationships>"""
        val types = """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="xml" ContentType="application/xml"/><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
        return packageFile(mapOf(
            "[Content_Types].xml" to types.toByteArray(),
            "word/document.xml" to document.toByteArray(),
            "word/numbering.xml" to numbering.toByteArray(),
            "word/_rels/document.xml.rels" to rels.toByteArray(),
            "word/media/pic.png" to png
        ))
    }

    @Test fun docxKeepsStructureListsTablesImagesAndBreaks() {
        val file = docxFile()
        assertEquals(OfficeKind.DOCX, OfficeDocumentReader.detect(file, "report.docx"))
        val doc = OfficeDocumentReader.read(file, OfficeKind.DOCX, 10_000_000)
        val paragraphs = doc.blocks.filterIsInstance<OfficeBlock.ParagraphBlock>().map { it.paragraph }
        assertEquals(1, paragraphs[0].level)
        assertEquals("My Doc", paragraphs[0].text)
        assertEquals(2, paragraphs[1].level)
        assertEquals("Chapter", paragraphs[1].text)
        assertEquals("Hello world", paragraphs[2].text)
        assertEquals(false, paragraphs[2].runs[0].bold)
        assertEquals(true, paragraphs[2].runs[1].bold)
        assertEquals("• ", paragraphs[3].listMarker)
        assertEquals("• ", paragraphs[4].listMarker)
        val table = doc.blocks.filterIsInstance<OfficeBlock.TableBlock>().single()
        assertTrue(table.headerRow)
        assertEquals("Name", table.rows[0][0].text)
        assertEquals("36", table.rows[1][1].text)
        assertTrue(doc.blocks.any { it is OfficeBlock.PageBreak })
        val image = doc.blocks.filterIsInstance<OfficeBlock.ImageBlock>().single()
        assertEquals("png", image.extension)
        assertEquals(png.size, image.bytes.size)
        assertTrue(doc.unsupported.isEmpty())
    }

    @Test fun xlsxResolvesSharedStringsNumbersDatesAndBooleans() {
        val workbook = """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Data" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        val wbRels = """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
        val shared = """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>Name</t></si><si><t>Ada</t></si></sst>"""
        val styles = """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts><numFmt numFmtId="165" formatCode="yyyy-mm-dd"/></numFmts>
<cellXfs><xf numFmtId="0"/><xf numFmtId="165"/></cellXfs></styleSheet>"""
        val sheet = """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
<row><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
<row><c r="A2"><v>42</v></c><c r="B2" s="1"><v>44927</v></c></row>
<row><c r="A3" t="b"><v>1</v></c></row>
</sheetData></worksheet>"""
        val types = """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
</Types>"""
        val file = packageFile(mapOf(
            "[Content_Types].xml" to types.toByteArray(),
            "xl/workbook.xml" to workbook.toByteArray(),
            "xl/_rels/workbook.xml.rels" to wbRels.toByteArray(),
            "xl/sharedStrings.xml" to shared.toByteArray(),
            "xl/styles.xml" to styles.toByteArray(),
            "xl/worksheets/sheet1.xml" to sheet.toByteArray()
        ))
        assertEquals(OfficeKind.XLSX, OfficeDocumentReader.detect(file, "book.xlsx"))
        val doc = OfficeDocumentReader.read(file, OfficeKind.XLSX, 10_000_000)
        assertEquals("Data", (doc.blocks[0] as OfficeBlock.Section).title)
        val table = doc.blocks.filterIsInstance<OfficeBlock.TableBlock>().single()
        assertEquals("Name", table.rows[0][0].text)
        assertEquals("Ada", table.rows[0][1].text)
        assertEquals("42", table.rows[1][0].text)
        assertEquals("2023-01-01", table.rows[1][1].text)
        assertEquals("TRUE", table.rows[2][0].text)
    }

    @Test fun pptxKeepsSlideOrderTitlesAndPictures() {
        val presentation = """<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<p:sldSz cx="12192000" cy="6858000"/><p:sldIdLst><p:sldId r:id="rId2"/><p:sldId r:id="rId3"/></p:sldIdLst></p:presentation>"""
        val presRels = """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
</Relationships>"""
        val slide1 = """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<p:spTree><p:sp><p:txBody><a:p><a:r><a:rPr sz="4000" b="1"/><a:t>Title One</a:t></a:r></a:p>
<a:p><a:r><a:rPr sz="1800"/><a:t>Body text</a:t></a:r></a:p></p:txBody></p:sp>
<p:pic><p:blipFill><a:blip r:embed="rId1"/></p:blipFill></p:pic></p:spTree></p:sld>"""
        val slide1Rels = """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/img.png"/>
</Relationships>"""
        val slide2 = """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
<p:spTree><p:sp><p:txBody><a:p><a:r><a:t>Second slide</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:sld>"""
        val types = """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
</Types>"""
        val file = packageFile(mapOf(
            "[Content_Types].xml" to types.toByteArray(),
            "ppt/presentation.xml" to presentation.toByteArray(),
            "ppt/_rels/presentation.xml.rels" to presRels.toByteArray(),
            "ppt/slides/slide1.xml" to slide1.toByteArray(),
            "ppt/slides/_rels/slide1.xml.rels" to slide1Rels.toByteArray(),
            "ppt/slides/slide2.xml" to slide2.toByteArray(),
            "ppt/media/img.png" to png
        ))
        assertEquals(OfficeKind.PPTX, OfficeDocumentReader.detect(file, "deck.pptx"))
        val doc = OfficeDocumentReader.read(file, OfficeKind.PPTX, 10_000_000)
        assertEquals(960.0, doc.pageWidthPt, 0.01)
        assertEquals(540.0, doc.pageHeightPt, 0.01)
        val paragraphs = doc.blocks.filterIsInstance<OfficeBlock.ParagraphBlock>().map { it.paragraph }
        assertEquals(1, paragraphs[0].level)
        assertEquals("Title One", paragraphs[0].text)
        assertEquals(0, paragraphs[1].level)
        assertEquals("Body text", paragraphs[1].text)
        assertEquals(1, doc.blocks.filterIsInstance<OfficeBlock.ImageBlock>().size)
        assertTrue(doc.blocks.any { it is OfficeBlock.PageBreak })
        assertEquals("Second slide", paragraphs.last().text)
    }

    @Test fun invalidPackagesAndUnknownExtensionsFailClosed() {
        val garbage = File.createTempFile("office-test-", ".bin")
        garbage.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertThrows(IllegalArgumentException::class.java) {
            OfficeDocumentReader.detect(garbage, "notes.txt")
        }
        val empty = packageFile(emptyMap())
        assertThrows(IllegalArgumentException::class.java) {
            OfficeDocumentReader.read(empty, OfficeKind.DOCX, 10_000_000)
        }
    }

    @Test fun docxRejectsUnboundedTableColumnSpansBeforeLayout() {
        val document = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body><w:tbl><w:tr><w:tc><w:tcPr><w:gridSpan w:val="513"/></w:tcPr>
    <w:p><w:r><w:t>Oversized span</w:t></w:r></w:p>
  </w:tc></w:tr></w:tbl></w:body>
</w:document>"""
        val file = packageFile(mapOf("word/document.xml" to document.toByteArray()))
        try {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                OfficeDocumentReader.read(file, OfficeKind.DOCX, 10_000_000)
            }
            assertEquals("OFFICE_SIZE_LIMIT", failure.message)
        } finally {
            file.delete()
        }
    }

    @Test fun officeImageBytesHonorTheCallerMemoryBudget() {
        val file = docxFile()
        try {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                OfficeDocumentReader.read(
                    file, OfficeKind.DOCX, 10_000_000,
                    maxImageBytes = png.size.toLong() - 1L
                )
            }
            assertEquals("OFFICE_SIZE_LIMIT", failure.message)
        } finally {
            file.delete()
        }
    }

    @Test fun officeXmlRejectsDoctypeAndExternalEntities() {
        val secret = File.createTempFile("office-xxe-", ".txt")
        secret.writeText("OFFICE_XXE_SECRET_SHOULD_NEVER_BE_READ")
        val document = """<?xml version="1.0"?>
<!DOCTYPE w:document [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body><w:p><w:r><w:t>&xxe;</w:t></w:r></w:p></w:body>
</w:document>"""
        val file = packageFile(mapOf("word/document.xml" to document.toByteArray()))
        try {
            val failure = assertThrows(IllegalArgumentException::class.java) {
                OfficeDocumentReader.read(file, OfficeKind.DOCX, 10_000_000)
            }
            assertEquals("OFFICE_INVALID", failure.message)
        } finally {
            secret.delete()
            file.delete()
        }
    }

    @Test fun officePackageHonorsCompressedAndExpandedByteBudgets() {
        val largeText = "office package budget test ".repeat(4_000)
        val document = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body><w:p><w:r><w:t>$largeText</w:t></w:r></w:p></w:body>
</w:document>"""
        val file = packageFile(mapOf("word/document.xml" to document.toByteArray()))
        try {
            val compressedBudget = file.length() + 32L
            val readFailure = assertThrows(IllegalArgumentException::class.java) {
                OfficeDocumentReader.read(file, OfficeKind.DOCX, compressedBudget)
            }
            assertEquals("OFFICE_SIZE_LIMIT", readFailure.message)

            val detectFailure = assertThrows(IllegalArgumentException::class.java) {
                OfficeDocumentReader.detect(file, "large.docx", compressedBudget)
            }
            assertEquals("OFFICE_SIZE_LIMIT", detectFailure.message)
        } finally {
            file.delete()
        }
    }
}
