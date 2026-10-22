package com.jumlo.app.pdf

import com.jumlo.app.data.pdf.PdfBoxBackend
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.junit.Assert.*
import org.junit.Test

class PdfCompressionBudgetTest {
    @Test fun flateDecodeHonorsTheCallerBudgetAndLeavesTheImageIntact() {
        PDDocument().use { doc ->
            val page = PDPage().also { doc.addPage(it) }
            val raw = ByteArray(1200 * 1200).also { java.util.Random(12).nextBytes(it) }
            val stream = PDStream(doc).also { it.createOutputStream(COSName.FLATE_DECODE).use { out -> out.write(raw) } }
            val image = PDImageXObject(stream, null).apply {
                width = 1200; height = 1200; bitsPerComponent = 8
                cosObject.setItem(COSName.COLORSPACE, COSName.DEVICEGRAY)
            }
            page.resources = PDResources().apply { put(COSName.getPDFName("scan"), image) }
            val report = PdfBoxBackend.compressImagesWithReport(doc, 72, 85, maxBitmapBytes = 128 * 1024)
            assertEquals("Raw Flate pixels must not bypass the job's memory budget", 0, report.replaced)
            assertSame(image.cosObject, page.resources.getXObject(COSName.getPDFName("scan")).cosObject)
            assertTrue(report.skipped.any { it.contains("BUDGET") })
            assertArrayEquals(raw, image.createInputStream().use { it.readBytes() })
        }
    }
}
