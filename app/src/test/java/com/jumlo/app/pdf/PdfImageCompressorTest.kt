package com.jumlo.app.pdf

import com.jumlo.app.data.pdf.PdfImageCodec
import com.jumlo.app.data.pdf.RecodedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compressor, driven by a codec that does not need to decode anything.
 *
 * The Android unit-test source set replaces the JDK with `android.jar`, which
 * has no `javax.imageio`, so a real JPEG cannot be decoded here. This test
 * covers the decisions the compressor makes - the ladder, the "only if smaller"
 * rule, the page count - with a codec that returns bytes of a known size. The
 * real decode of a real 300 DPI scan is covered by the host suite
 * (`scripts/test-pdf-compression.sh`), which runs on a full JDK.
 */
class PdfImageCompressorTest {

    private class RecordingCodec(
        private val produced: Int,
        private val failure: String? = null
    ) : PdfImageCodec {
        val calls = mutableListOf<List<Int>>()

        override fun recode(source: ByteArray, maxWidth: Int, maxHeight: Int, quality: Int): RecodedImage? {
            calls += listOf(maxWidth, maxHeight, quality)
            failure?.let { throw IllegalStateException(it) }
            // A JPEG of the requested size, small enough to be smaller than the
            // original stream in the fixture.
            val bytes = ByteArray(produced) { 0x42 }
            return RecodedImage(maxWidth, maxHeight, bytes)
        }
    }

    /** A PDF whose page carries one image stream of [bytes] length. */
    private fun pdfWithImage(bytes: Int): com.tom_roush.pdfbox.pdmodel.PDDocument {
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.LETTER)
        doc.addPage(page)
        val stream = com.tom_roush.pdfbox.pdmodel.common.PDStream(doc)
        stream.createOutputStream().use { it.write(ByteArray(bytes) { 0x11 }) }
        val image = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject(stream, null)
        image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.WIDTH, com.tom_roush.pdfbox.cos.COSInteger.get(2550))
        image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.HEIGHT, com.tom_roush.pdfbox.cos.COSInteger.get(3300))
        image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.COLORSPACE, com.tom_roush.pdfbox.cos.COSName.DEVICERGB)
        image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.BITS_PER_COMPONENT, com.tom_roush.pdfbox.cos.COSInteger.get(8))
        image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.FILTER, com.tom_roush.pdfbox.cos.COSName.DCT_DECODE)
        val resources = com.tom_roush.pdfbox.pdmodel.PDResources()
        resources.put(com.tom_roush.pdfbox.cos.COSName.getPDFName("Im0"), image)
        page.resources = resources
        return doc
    }

    @Test
    fun anImageThatBecomesSmallerIsReplaced() {
        val codec = RecordingCodec(produced = 1024)
        val doc = pdfWithImage(bytes = 200_000)
        val report = com.jumlo.app.data.pdf.PdfImageCompressor.compress(doc, 96, 55, codec = codec)
        assertEquals(1, report.replaced)
        assertTrue(report.skipped.isEmpty())
        assertEquals(1, report.pages)
    }

    @Test
    fun anImageThatStaysTheSameSizeIsLeftAloneAndTheReasonIsRecorded() {
        // The codec returns more bytes than the original, so no rung helps.
        val codec = RecordingCodec(produced = 500_000)
        val doc = pdfWithImage(bytes = 200_000)
        val report = com.jumlo.app.data.pdf.PdfImageCompressor.compress(doc, 96, 55, codec = codec)
        assertEquals(0, report.replaced)
        assertEquals(listOf("Im0: NOT_SMALLER"), report.skipped)
    }

    @Test
    fun theLadderGoesBelowTheChosenResolutionUntilTheImageIsSmaller() {
        // The codec only produces a small image on the third rung, so the
        // compressor has to walk down the ladder to get there.
        val codec = object : PdfImageCodec {
            var call = 0
            override fun recode(source: ByteArray, maxWidth: Int, maxHeight: Int, quality: Int): RecodedImage? {
                call++
                val size = if (call < 3) 500_000 else 1024
                return RecodedImage(maxWidth, maxHeight, ByteArray(size) { 0x42 })
            }
        }
        val doc = pdfWithImage(bytes = 200_000)
        val report = com.jumlo.app.data.pdf.PdfImageCompressor.compress(doc, 96, 55, codec = codec)
        assertEquals(1, report.replaced)
        assertTrue("the ladder must go below the chosen resolution", codec.call >= 3)
    }

    @Test
    fun anImageThatCannotBeDecodedIsReportedAndDoesNotStopTheDocument() {
        val codec = RecordingCodec(produced = 1024, failure = "boom")
        val doc = pdfWithImage(bytes = 200_000)
        val report = com.jumlo.app.data.pdf.PdfImageCompressor.compress(doc, 96, 55, codec = codec)
        assertEquals(0, report.replaced)
        assertEquals(1, report.skipped.size)
        assertTrue(report.skipped.first().contains("boom"))
        assertEquals("the document is still walked", 1, report.pages)
    }
}
