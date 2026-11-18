package com.jumlo.app.pdf
import com.jumlo.app.data.pdf.PdfBoxBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
class MeasureCompressionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun measure() {
        val dir = temporary.newFolder()
        val src = PdfFixtures.imageDoc(File(dir, "scan.pdf"), textPage = true)
        val original = src.length()
        assertTrue("source PDF must be non-empty", original > 0L)
        listOf(96 to 55, 150 to 70, 300 to 88).forEach { (dpi, q) ->
            val out = File(dir, "c-$dpi.pdf")
            PdfBoxBackend.open(src, null).use { doc -> PdfBoxBackend.compressImages(doc, dpi, q); doc.save(out) }
            val pages = PdfBoxBackend.open(out, null).use { it.numberOfPages }
            assertEquals(2, pages)
            assertTrue("compressed output for dpi=$dpi must be non-empty", out.length() > 0L)
        }
        val text = PdfFixtures.simpleDoc(File(dir, "text.pdf"), listOf("only text here"))
        val tout = File(dir, "t.pdf")
        PdfBoxBackend.open(text, null).use { doc -> PdfBoxBackend.compressImages(doc, 150, 70); doc.save(tout) }
        assertTrue("text-only output must be non-empty", tout.isFile && tout.length() > 0L)
    }
}
