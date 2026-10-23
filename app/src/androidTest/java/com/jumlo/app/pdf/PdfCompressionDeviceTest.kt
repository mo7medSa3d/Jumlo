package com.jumlo.app.pdf

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.data.jobs.WorkBudget
import com.jumlo.app.data.pdf.AndroidImageCodec
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The compression path, measured on the device it runs on.
 *
 * The five scanned files are pushed to the app's own external files directory
 * before the run. The report is written next to them, so the numbers can be read
 * back with `adb pull` together with `dumpsys meminfo`.
 */
@RunWith(AndroidJUnit4::class)
class PdfCompressionDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val budget = WorkBudget().maxBitmapBytes

    private fun benchDir(): File = File(context.getExternalFilesDir(null), "bench").also { it.mkdirs() }

    private fun reportFile(): File = File(benchDir(), "device-compression-report.txt")

    /** Only the five scanned fixtures, not the outputs of an earlier run. */
    private fun fixtures(): List<File> =
        listOf(1, 3, 6, 10, 30).map { pages ->
            File(benchDir(), "scan_${pages.toString().padStart(2, '0')}pages.pdf")
        }

    private fun verifyCompressedOutput(
        output: File,
        inputBytes: Long,
        expectedPages: Int,
        replaced: Int,
        skipped: Int
    ) {
        assertTrue("scan images must be recompressed", replaced > 0)
        assertEquals("valid scan images must not be skipped", 0, skipped)
        assertTrue("the output must be a nonempty PDF", output.isFile && output.length() > 0L)
        assertTrue("the compressed scan must be smaller than its input", output.length() < inputBytes)
        PdfBoxBackend.open(output, null).use { doc ->
            assertEquals("the saved PDF must retain every page", expectedPages, doc.numberOfPages)
            for (page in doc.pages) {
                assertTrue("every saved scan page must retain its content", page.hasContents())
                val resources = page.resources
                assertTrue("every saved scan page must retain its image", resources != null &&
                    resources.xObjectNames.any { resources.getXObject(it) is PDImageXObject })
            }
        }
    }

    /** The peak of the Java heap, sampled while the work runs. */
    private class HeapWatch {
        private val runtime = Runtime.getRuntime()
        var peakJava = 0L
            private set
        var peakNative = 0L
            private set

        fun sample() {
            val used = runtime.totalMemory() - runtime.freeMemory()
            if (used > peakJava) peakJava = used
            val native = Debug.getNativeHeapAllocatedSize()
            if (native > peakNative) peakNative = native
        }
    }

    @Test
    fun theScannedCorpusIsCompressedOnThisDevice() {
        val files = fixtures()
        val missing = files.filterNot { it.isFile && it.length() > 0L }
        assertTrue("all five scanned fixtures must be pushed to the device: ${missing.map { it.name }}", missing.isEmpty())
        val presets = listOf(
            Triple("Strong", 108, 45),
            Triple("Medium", 180, 75),
            Triple("High", 240, 90)
        )
        val lines = mutableListOf<String>()
        val failures = mutableListOf<String>()
        lines += "device=${android.os.Build.MODEL} android=${android.os.Build.VERSION.RELEASE} " +
            "abi=${android.os.Build.SUPPORTED_ABIS.first()} heapLimit=" +
            "${System.getProperty("java.vm.heaplimit")} budgetBytes=$budget"
        for ((name, dpi, quality) in presets) {
            for (file in files) {
                val output = File(benchDir(), "${file.nameWithoutExtension}-$name-out.pdf")
                val expectedPages = file.name.removePrefix("scan_").removeSuffix("pages.pdf").toInt()
                val before = file.length()
                val watch = HeapWatch()
                val started = System.nanoTime()
                var replaced = 0
                var skipped = 0
                var pages = 0
                var failed = ""
                try {
                    PdfBoxBackend.open(file, null).use { doc ->
                        pages = doc.numberOfPages
                        assertEquals("the fixture must contain its advertised page count", expectedPages, pages)
                        val report = PdfBoxBackend.compressImagesWithReport(
                            doc, dpi, quality,
                            onPage = { done, _ -> watch.sample() },
                            maxBitmapBytes = budget,
                            codec = AndroidImageCodec(budget)
                        )
                        replaced = report.replaced
                        skipped = report.skipped.size
                        watch.sample()
                        doc.save(output)
                    }
                    verifyCompressedOutput(output, before, expectedPages, replaced, skipped)
                } catch (failure: Throwable) {
                    failed = "${failure::class.simpleName}: ${failure.message}"
                }
                val seconds = (System.nanoTime() - started) / 1_000_000_000.0
                val after = if (output.exists()) output.length() else 0L
                val saving = if (before > 0 && after > 0) 100.0 * (1.0 - after.toDouble() / before) else 0.0
                val line = "RESULT file=${file.name} preset=$name dpi=$dpi q=$quality pages=$pages " +
                    "replaced=$replaced skipped=$skipped before=$before after=$after " +
                    "saved=${"%.1f".format(saving)}% seconds=${"%.1f".format(seconds)} " +
                    "peakJavaBytes=${watch.peakJava} peakNativeBytes=${watch.peakNative} " +
                    (if (failed.isEmpty()) "ok" else "failed=$failed")
                lines += line
                if (failed.isNotEmpty()) failures += line
            }
        }
        val report = reportFile()
        report.writeText(lines.joinToString("\n") + "\n")
        assertTrue("the report must be written", report.exists())
        assertTrue("every scanned corpus result must succeed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun aTwoHundredPageScanIsProcessedOnThisDevice() {
        val source = File(benchDir(), "scan_10pages.pdf")
        assertTrue("the ten-page scanned fixture must be pushed to the device", source.isFile && source.length() > 0L)
        // The 200 page document is built from the ten page scan: twenty copies of
        // it, put together with PDFBox on the device itself.
        val big = File(benchDir(), "scan_200pages.pdf")
        if (!big.exists()) {
            // The merger copies the page resources, which importPage does not:
            // a document made with importPage keeps the page but loses the photo.
            val merger = com.tom_roush.pdfbox.multipdf.PDFMergerUtility()
            repeat(20) { merger.addSource(source.absolutePath) }
            merger.destinationFileName = big.absolutePath
            merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())
        }
        val watch = HeapWatch()
        val started = System.nanoTime()
        var pages = 0
        var replaced = 0
        var skipped = 0
        var failed = ""
        val output = File(benchDir(), "scan_200pages-Strong-out.pdf")
        try {
            PdfBoxBackend.open(big, null).use { doc ->
                pages = doc.numberOfPages
                val report = PdfBoxBackend.compressImagesWithReport(
                    doc, 108, 45,
                    onPage = { _, _ -> watch.sample() },
                    maxBitmapBytes = budget,
                    codec = AndroidImageCodec(budget)
                )
                replaced = report.replaced
                skipped = report.skipped.size
                watch.sample()
                doc.save(output)
            }
            verifyCompressedOutput(output, big.length(), 200, replaced, skipped)
        } catch (failure: Throwable) {
            failed = "${failure::class.simpleName}: ${failure.message}"
        }
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        val before = big.length()
        val after = if (output.exists()) output.length() else 0L
        val saving = if (before > 0 && after > 0) 100.0 * (1.0 - after.toDouble() / before) else 0.0
        val line = "RESULT200 file=scan_200pages.pdf pages=$pages replaced=$replaced skipped=$skipped " +
            "before=$before after=$after saved=${"%.1f".format(saving)}% seconds=${"%.1f".format(seconds)} " +
            "peakJavaBytes=${watch.peakJava} peakNativeBytes=${watch.peakNative} " +
            (if (failed.isEmpty()) "ok" else "failed=$failed")
        val report = reportFile()
        report.appendText(line + "\n")
        assertEquals("the document must have two hundred pages", 200, pages)
        assertTrue("the compression must finish: $line", failed.isEmpty())
    }

    @Test
    fun aDocumentWithAnUnreadableImageIsReportedAndTheRestSurvives() {
        val broken = File(benchDir(), "broken.pdf")
        if (!broken.exists()) {
            PDDocument().use { doc ->
                val page = PDPage(PDRectangle.LETTER)
                doc.addPage(page)
                val stream = com.tom_roush.pdfbox.pdmodel.common.PDStream(doc)
                stream.createOutputStream().use { it.write(ByteArray(4096) { (it * 31 % 251).toByte() }) }
                val image = PDImageXObject(stream, null)
                image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.WIDTH, com.tom_roush.pdfbox.cos.COSInteger.get(2550))
                image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.HEIGHT, com.tom_roush.pdfbox.cos.COSInteger.get(3300))
                image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.COLORSPACE, com.tom_roush.pdfbox.cos.COSName.DEVICERGB)
                image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.BITS_PER_COMPONENT, com.tom_roush.pdfbox.cos.COSInteger.get(8))
                image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.FILTER, com.tom_roush.pdfbox.cos.COSName.DCT_DECODE)
                val resources = com.tom_roush.pdfbox.pdmodel.PDResources()
                resources.put(com.tom_roush.pdfbox.cos.COSName.getPDFName("Im0"), image)
                page.resources = resources
                doc.save(broken)
            }
        }
        var skipped = 0
        var failed = ""
        PdfBoxBackend.open(broken, null).use { doc ->
            try {
                val report = PdfBoxBackend.compressImagesWithReport(
                    doc, 108, 45, maxBitmapBytes = budget, codec = AndroidImageCodec(budget)
                )
                skipped = report.skipped.size
            } catch (failure: Throwable) {
                failed = "${failure::class.simpleName}: ${failure.message}"
            }
        }
        val line = "RESULT broken skipped=$skipped ${if (failed.isEmpty()) "ok" else "failed=$failed"}"
        reportFile().appendText(line + "\n")
        assertTrue("the unreadable image must be reported: $line", skipped > 0 || failed.isNotEmpty())
    }
}
