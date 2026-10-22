package com.jumlo.app.pdf
import com.jumlo.app.data.pdf.PdfBoxBackend
import org.junit.Test
import java.io.File
class MeasureCompressionTest {
    @Test fun measure() {
        val dir = File(System.getProperty("java.io.tmpdir"), "measure-${System.nanoTime()}").apply { mkdirs() }
        val src = PdfFixtures.imageDoc(File(dir, "scan.pdf"), textPage = true)
        val original = src.length()
        println("ORIGINAL bytes=$original")
        listOf(96 to 55, 150 to 70, 300 to 88).forEach { (dpi, q) ->
            val out = File(dir, "c-$dpi.pdf")
            PdfBoxBackend.open(src, null).use { doc -> PdfBoxBackend.compressImages(doc, dpi, q); doc.save(out) }
            val pages = PdfBoxBackend.open(out, null).use { it.numberOfPages }
            println("dpi=$dpi q=$q -> bytes=${out.length()} pages=$pages saved=${100.0 * (original - out.length()) / original}%")
        }
        val text = PdfFixtures.simpleDoc(File(dir, "text.pdf"), listOf("only text here"))
        val tout = File(dir, "t.pdf")
        PdfBoxBackend.open(text, null).use { doc -> PdfBoxBackend.compressImages(doc, 150, 70); doc.save(tout) }
        println("TEXT-ONLY original=${text.length()} -> ${tout.length()}")
        dir.deleteRecursively()
    }
}
