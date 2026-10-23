package com.jumlo.app.pdf

import com.jumlo.app.data.pdf.PdfPageText
import com.jumlo.app.core.office.OfficePackageWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile

class PdfPageTextTest {
    @Test fun largerPdfTextBecomesAHeadingWithNormalBodyAndUnchangedInput() {
        val directory=java.nio.file.Files.createTempDirectory("pdf-heading-").toFile()
        try {
            val source=File(directory,"heading.pdf")
            fixture(source)
            val original=source.readBytes()
            PdfPageText.open(source).use { document ->
                val page=PdfPageText.extractStructured(document,0)
                assertEquals(1,page.paragraphs.count {it.headingLevel>0})
                assertEquals(1,page.paragraphs.first().headingLevel)
                assertTrue(page.paragraphs.drop(1).all {it.headingLevel==0})
                assertEquals("Document heading",page.paragraphs.first().text)
                assertTrue(page.paragraphs.drop(1).joinToString("\n") {it.text}.contains("normal text size"))
                val output=File(directory,"heading.docx")
                OfficePackageWriter.writeStructuredDocx(output,1,{page})
                ZipFile(output).use {zip ->
                    val xml=zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().use {it.readText()}
                    assertTrue(xml.contains("w:pStyle w:val=\"Heading1\""))
                    assertTrue(xml.contains("Document heading"))
                }
            }
            assertArrayEquals(original,source.readBytes())
        } finally {directory.deleteRecursively()}
    }

    @Test fun extractionCancellationIsNotReclassifiedAsInvalidPdf() {
        val file=File.createTempFile("pdf-text-cancel-",".pdf")
        try {
            fixture(file)
            val cancellation=CancellationException("cancel while reading glyphs")
            PdfPageText.open(file).use { document ->
                var calls=0
                val error=runCatching {PdfPageText.extractStructured(document,0) {if(++calls==5) throw cancellation}}.exceptionOrNull()
                assertSame(cancellation,error)
            }
        } finally {file.delete()}
    }

    @Test fun invalidPdfUsesTheStableDocumentError() {
        val file=File.createTempFile("pdf-text-corrupt-",".pdf").apply {writeText("%PDF-1.7 invalid")}
        try {assertEquals("PDF_INVALID",runCatching {PdfPageText.open(file).close()}.exceptionOrNull()?.message)}
        finally {file.delete()}
    }

    private fun fixture(file:File) {
        PDDocument().use { document ->
            val page=PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document,page).use { content ->
                content.beginText();content.setFont(PDType1Font.HELVETICA_BOLD,24f)
                content.newLineAtOffset(40f,730f);content.showText("Document heading");content.endText()
                content.beginText();content.setFont(PDType1Font.HELVETICA,12f)
                content.newLineAtOffset(40f,680f)
                content.showText("This body paragraph establishes the normal text size for heading recovery.")
                content.newLineAtOffset(0f,-18f)
                content.showText("Its words remain editable and must not be promoted into heading styles.")
                content.endText()
            }
            document.save(file)
        }
    }
}
