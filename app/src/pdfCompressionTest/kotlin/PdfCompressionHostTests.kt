package com.jumlo.app.hosttest

import com.jumlo.app.data.pdf.PdfImageCodec
import com.jumlo.app.data.pdf.PdfImageCompressor
import com.jumlo.app.data.pdf.RecodedImage
import com.tom_roush.pdfbox.cos.COSInteger
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.ImageOutputStream

/**
 * The PDF compression path, on a real JPEG, without a device.
 *
 * The Android unit-test source set replaces the JDK with `android.jar`, which has
 * no `javax.imageio`, so `BitmapFactory` returns null there and the scanner case
 * had no coverage at all. This suite runs the same compressor through the
 * byte-level codec seam with an ImageIO codec, on a real 300 DPI scan.
 */

/** The host side of the codec seam: ImageIO in, a scaled JPEG out. */
class HostImageCodec(private val maxBitmapBytes: Long = Long.MAX_VALUE) : PdfImageCodec {
    override fun recode(source: ByteArray, maxWidth: Int, maxHeight: Int, quality: Int): RecodedImage? {
        val decoded = ImageIO.read(source.inputStream()) ?: return null
        val sample = sampleSize(decoded.width, decoded.height)
        val scaled = if (sample > 1) {
            val small = BufferedImage(
                (decoded.width / sample).coerceAtLeast(1),
                (decoded.height / sample).coerceAtLeast(1),
                BufferedImage.TYPE_INT_RGB
            )
            val graphics = small.createGraphics()
            graphics.drawImage(decoded, 0, 0, small.width, small.height, null)
            graphics.dispose()
            small
        } else {
            decoded
        }
        val factor = minOf(
            1.0,
            minOf(maxWidth.toDouble() / scaled.width.coerceAtLeast(1), maxHeight.toDouble() / scaled.height.coerceAtLeast(1))
        )
        val width = maxOf(1, (scaled.width * factor).toInt())
        val height = maxOf(1, (scaled.height * factor).toInt())
        if (width.toLong() * height * 4L > maxBitmapBytes) return null
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = out.createGraphics()
        graphics.drawImage(scaled, 0, 0, width, height, null)
        graphics.dispose()
        return RecodedImage(width, height, encode(out, quality))
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var step = 1
        while (width.toLong() * height * 4L / (step.toLong() * step) > maxBitmapBytes && step < 64) step *= 2
        return step
    }

    private fun encode(image: BufferedImage, quality: Int): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val parameter = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality.coerceIn(10, 95) / 100f
        }
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(image, null, null), parameter)
        }
        writer.dispose()
        return out.toByteArray()
    }
}

/** A noisy photo-like JPEG. Noise keeps the encoder from making it tiny. */
fun photoJpeg(width: Int, height: Int, seed: Long): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val random = java.util.Random(seed)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val base = 200 + (y * 40 / height)
            image.setRGB(x, y, (base shl 16) or ((base + random.nextInt(30)) shl 8) or (base - random.nextInt(30)))
        }
    }
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val parameter = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = 0.9f
    }
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { stream ->
        writer.output = stream
        writer.write(null, IIOImage(image, null, null), parameter)
    }
    writer.dispose()
    return out.toByteArray()
}

/** A PDF whose pages each carry one JPEG image, the way a scan looks. */
fun scanPdf(target: File, pages: List<Pair<Int, Int>>): File {
    PDDocument().use { doc ->
        pages.forEachIndexed { index, (pixels, height) ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            val jpeg = photoJpeg(pixels, height, 7L + index)
            val stream = PDStream(doc)
            stream.createOutputStream().use { it.write(jpeg) }
            val image = PDImageXObject(stream, null)
            image.cosObject.setItem(COSName.WIDTH, COSInteger.get(pixels.toLong()))
            image.cosObject.setItem(COSName.HEIGHT, COSInteger.get(height.toLong()))
            image.cosObject.setItem(COSName.COLORSPACE, COSName.DEVICERGB)
            image.cosObject.setItem(COSName.BITS_PER_COMPONENT, COSInteger.get(8))
            image.cosObject.setItem(COSName.FILTER, COSName.DCT_DECODE)
            val resources = PDResources()
            resources.put(COSName.getPDFName("Im$index"), image)
            page.resources = resources
            PDPageContentStream(doc, page).use { content -> content.drawImage(image, 0f, 0f, 612f, 792f) }
        }
        doc.save(target)
    }
    return target
}

private fun imageBytes(doc: PDDocument, page: Int): ByteArray {
    val resources = doc.getPage(page).resources
    val name = resources.xObjectNames.first()
    return (resources.getXObject(name) as PDImageXObject).createInputStream().use { it.readBytes() }
}

/** Real Flate raster, including PDF sample-to-color mapping when requested. */
private fun flateImage(doc: PDDocument, width: Int, height: Int, inverted: Boolean = false): PDImageXObject {
    val bytes = ByteArray(width * height)
    java.util.Random(19).nextBytes(bytes)
    val stream = PDStream(doc)
    stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(bytes) }
    return PDImageXObject(stream, null).also { image ->
        image.width = width
        image.height = height
        image.bitsPerComponent = 8
        image.cosObject.setItem(COSName.COLORSPACE, COSName.DEVICEGRAY)
        if (inverted) image.cosObject.setItem(COSName.DECODE, COSArray().apply {
            add(COSFloat(1f)); add(COSFloat(0f))
        })
    }
}

private fun addImagePage(doc: PDDocument, image: PDImageXObject, box: PDRectangle = PDRectangle.LETTER): PDPage =
    PDPage(box).also { page ->
        doc.addPage(page)
        page.resources = PDResources().apply { put(COSName.getPDFName("Im0"), image) }
        PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, box.width, box.height) }
    }

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String = "") {
    if (condition) {
        println("PASS $name")
    } else {
        failures++
        println("FAIL $name${if (detail.isEmpty()) "" else ": $detail"}")
    }
}

fun main() {
    val dir = Files.createTempDirectory("pdf-compression-host").toFile().also { it.deleteOnExit() }

    // 1. A real 300 DPI scan: smaller output, same page count, still a JPEG.
    run {
        val source = scanPdf(File(dir, "scan300.pdf"), listOf(2550 to 3300))
        val output = File(dir, "scan300-out.pdf")
        val before = source.length()
        var replaced = 0
        PDDocument.load(source).use { doc ->
            replaced = PdfImageCompressor.compress(doc, 96, 55, codec = HostImageCodec()).replaced
            doc.save(output)
        }
        check("a 300 dpi scan is re-encoded", replaced == 1, "replaced=$replaced")
        check("the output is smaller", output.length() < before, "${before} -> ${output.length()}")
        PDDocument.load(output).use { doc ->
            check("the page count is kept", doc.numberOfPages == 1)
            val decoded = ImageIO.read(imageBytes(doc, 0).inputStream())
            check("the output still decodes as a JPEG", decoded != null)
            check("the image was scaled to the target", decoded?.width == 816 && decoded?.height == 1056,
                "${decoded?.width}x${decoded?.height}")
        }
        val saved = 100.0 * (1.0 - output.length().toDouble() / before)
        println("MEASURED scan300dpi 96dpi/q55 saved=${"%.1f".format(saved)}% before=$before after=${output.length()}")
    }

    // 2. One document holding a 150 DPI and a 300 DPI image.
    run {
        val source = scanPdf(File(dir, "mixed-dpi.pdf"), listOf(1275 to 1650, 2550 to 3300))
        val output = File(dir, "mixed-dpi-out.pdf")
        var replaced = 0
        PDDocument.load(source).use { doc ->
            replaced = PdfImageCompressor.compress(doc, 150, 70, codec = HostImageCodec()).replaced
            doc.save(output)
        }
        check("both images of the document are re-encoded", replaced == 2, "replaced=$replaced")
        PDDocument.load(output).use { doc ->
            check("both pages are kept", doc.numberOfPages == 2)
            val first = ImageIO.read(imageBytes(doc, 0).inputStream())
            val second = ImageIO.read(imageBytes(doc, 1).inputStream())
            check("the 150 dpi page is scaled to the target", first?.width == 1275, "${first?.width}")
            check("the 300 dpi page is scaled to the target", second?.width == 1275, "${second?.width}")
        }
    }

    // 3. A page whose image cannot be decoded: the rest of the document survives.
    run {
        val source = File(dir, "broken.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            val stream = PDStream(doc)
            stream.createOutputStream().use { it.write(ByteArray(4096) { (it * 31 % 251).toByte() }) }
            val image = PDImageXObject(stream, null)
            image.cosObject.setItem(COSName.WIDTH, COSInteger.get(2550))
            image.cosObject.setItem(COSName.HEIGHT, COSInteger.get(3300))
            image.cosObject.setItem(COSName.COLORSPACE, COSName.DEVICERGB)
            image.cosObject.setItem(COSName.BITS_PER_COMPONENT, COSInteger.get(8))
            image.cosObject.setItem(COSName.FILTER, COSName.DCT_DECODE)
            val resources = PDResources()
            resources.put(COSName.getPDFName("Im0"), image)
            page.resources = resources
            doc.save(source)
        }
        var report: PdfImageCompressor.Report? = null
        PDDocument.load(source).use { doc -> report = PdfImageCompressor.compress(doc, 96, 55, codec = HostImageCodec()) }
        check("an unreadable image is reported", report != null && report.skipped.isNotEmpty(),
            report?.skipped?.toString() ?: "no report")
        check("the document is still walked", report != null && report.pages == 1)
    }

    // 4. Flate images can have an inverted /Decode color mapping. Resampling
    // must preserve it or a negative scan becomes a positive (and vice versa).
    run {
        PDDocument().use { doc ->
            val original = flateImage(doc, 1200, 1200, inverted = true)
            val page = addImagePage(doc, original)
            val report = PdfImageCompressor.compress(doc, 72, 90, codec = HostImageCodec())
            val result = page.resources.getXObject(COSName.getPDFName("Im0")) as PDImageXObject
            check("a large Flate scan is downsampled", report.replaced == 1 && result.width < original.width)
            val mapping = result.cosObject.getDictionaryObject(COSName.DECODE) as? COSArray
            check("Flate sample colors keep their PDF Decode mapping",
                mapping?.toFloatArray()?.contentEquals(floatArrayOf(1f, 0f)) == true)
            val source = File(dir, "inverted-flate-compressed.pdf")
            doc.save(source)
            PDDocument.load(source).use { reopened ->
                val persisted = reopened.getPage(0).resources.getXObject(COSName.getPDFName("Im0")) as PDImageXObject
                check("the Decode mapping survives serialization", persisted.cosObject.getDictionaryObject(COSName.DECODE) != null)
            }
        }
    }

    // 5. A no-op Flate recompression must not be reported as an improvement.
    run {
        PDDocument().use { doc ->
            val original = flateImage(doc, 8, 8)
            val page = addImagePage(doc, original)
            val report = PdfImageCompressor.compress(doc, 150, 90, codec = HostImageCodec())
            check("an equal-or-larger Flate stream is preserved", report.replaced == 0 &&
                page.resources.getXObject(COSName.getPDFName("Im0")).cosObject === original.cosObject)
        }
    }

    // 6. The same image can be used on a thumbnail page and a full-size page.
    // First-page traversal order must not dictate the resolution of both pages.
    run {
        val fixture = scanPdf(File(dir, "shared-source.pdf"), listOf(2550 to 3300))
        PDDocument.load(fixture).use { doc ->
            val fullPage = doc.getPage(0)
            val thumbnail = PDPage(PDRectangle(100f, 100f))
            thumbnail.resources = fullPage.resources
            doc.pages.insertBefore(thumbnail, fullPage)
            PdfImageCompressor.compress(doc, 96, 70, codec = HostImageCodec())
            val decoded = ImageIO.read(imageBytes(doc, 1).inputStream())
            check("a shared scan retains the largest page resolution", decoded?.width == 816 && decoded.height == 1056,
                "${decoded?.width}x${decoded?.height}")
        }
    }

    // 7. JPEG bytes alone do not contain a PDF's /Decode mapping. A byte codec
    // cannot reproduce these colors, so this image must remain untouched.
    run {
        val fixture = scanPdf(File(dir, "jpeg-decode-source.pdf"), listOf(1275 to 1650))
        PDDocument.load(fixture).use { doc ->
            val image = doc.getPage(0).resources.getXObject(COSName.getPDFName("Im0")) as PDImageXObject
            image.cosObject.setItem(COSName.DECODE, COSArray().apply {
                repeat(3) { add(COSFloat(1f)); add(COSFloat(0f)) }
            })
            val bytes = imageBytes(doc, 0)
            val report = PdfImageCompressor.compress(doc, 96, 55, codec = HostImageCodec())
            check("a JPEG with an external Decode mapping is preserved", report.replaced == 0 && bytes.contentEquals(imageBytes(doc, 0)))
        }
    }

    // 8. A cancellation thrown across the codec seam must stop the operation,
    // not be reclassified as an unsupported image and followed by publication.
    run {
        val fixture = scanPdf(File(dir, "cancel-source.pdf"), listOf(1275 to 1650))
        val cancellation = java.util.concurrent.CancellationException("cancel compression")
        PDDocument.load(fixture).use { doc ->
            val error = runCatching {
                PdfImageCompressor.compress(doc, 96, 55, codec = PdfImageCodec { _, _, _, _ -> throw cancellation })
            }.exceptionOrNull()
            check("codec cancellation propagates unchanged", error === cancellation)
        }
    }

    println(if (failures == 0) "ALL PASS" else "$failures FAILED")
    if (failures > 0) kotlin.system.exitProcess(1)
}
