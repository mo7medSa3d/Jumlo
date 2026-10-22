package com.jumlo.app.hosttest

import com.jumlo.app.data.pdf.PdfImageCompressor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

/**
 * The compression measurement, on the scanned corpus.
 *
 * Two corpora, because the reduction depends on what the input already is:
 *
 *  - **the scan corpus**: one, three, six, ten and thirty pages, every page a
 *    2550x3300 JPEG on a US Letter page, which is what a phone photo of paper
 *    looks like.
 *  - **the photo corpus**: the real phone photos in the repository, about
 *    1000x1400 pixels, which are already at a low resolution and therefore
 *    resist the higher steps of the ladder.
 *
 * The scans are generated here rather than checked in, so the numbers can be
 * reproduced by anyone running the suite.
 */
fun main(args: Array<String>) {
    val dir = File(System.getProperty("java.io.tmpdir"), "jumlo-compression-measure").also { it.mkdirs() }
    val presets = listOf(
        Triple("Best quality", 240, 90),
        Triple("Balanced", 180, 75),
        Triple("Small file", 135, 58),
        Triple("Strong", 108, 45)
    )
    val scanCounts = listOf(1, 3, 6, 10, 30)

    val corpora = mutableListOf<Pair<String, List<File>>>()
    corpora += "scans (2550x3300, 300 dpi)" to scanCounts.map { pages ->
        val file = File(dir, "scan_${pages.toString().padStart(2, '0')}pages.pdf")
        if (!file.exists()) scanPdf(file, List(pages) { 2550 to 3300 })
        file
    }
    val photos = File("tests/ocr_benchmark/pages").listFiles { f -> f.name.startsWith("photo_") && f.name.endsWith(".jpg") }
    if (photos != null && photos.isNotEmpty()) {
        val file = File(dir, "photos.pdf")
        if (!file.exists()) photoPdf(file, photos.sortedBy { it.name })
        corpora += "real phone photos (about 1000x1400)" to listOf(file)
    }

    for ((label, files) in corpora) {
        println()
        println("# $label")
        println()
        println("| file | pages | preset | before | after | saved | seconds |")
        println("|---|---:|---|---:|---:|---:|---:|")
        for (file in files) {
            val before = file.length()
            for ((name, dpi, quality) in presets) {
                val output = File(file.parentFile, "${file.nameWithoutExtension}-$name.pdf")
                val started = System.nanoTime()
                var pages = 0
                PDDocument.load(file).use { doc ->
                    pages = doc.numberOfPages
                    PdfImageCompressor.compress(doc, dpi, quality, codec = HostImageCodec())
                    doc.save(output)
                }
                val seconds = (System.nanoTime() - started) / 1_000_000_000.0
                val saved = 100.0 * (1.0 - output.length().toDouble() / before)
                println(
                    "| ${file.name} | $pages | $name ($dpi dpi / q$quality) | $before | ${output.length()} | " +
                        "${"%.1f".format(saved)}% | ${"%.1f".format(seconds)} |"
                )
            }
        }
    }
}

/** The real phone photos of paper, one page each. */
fun photoPdf(target: File, photos: List<File>): File {
    PDDocument().use { doc ->
        photos.forEachIndexed { index, photo ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(
                com.tom_roush.pdfbox.pdmodel.common.PDRectangle(612f, 792f)
            )
            doc.addPage(page)
            val bytes = photo.readBytes()
            val stream = com.tom_roush.pdfbox.pdmodel.common.PDStream(doc)
            stream.createOutputStream().use { it.write(bytes) }
            val image = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject(stream, null)
            val size = javax.imageio.ImageIO.read(bytes.inputStream())
            image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.WIDTH, com.tom_roush.pdfbox.cos.COSInteger.get((size?.width ?: 1000).toLong()))
            image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.HEIGHT, com.tom_roush.pdfbox.cos.COSInteger.get((size?.height ?: 1400).toLong()))
            image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.COLORSPACE, com.tom_roush.pdfbox.cos.COSName.DEVICERGB)
            image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.BITS_PER_COMPONENT, com.tom_roush.pdfbox.cos.COSInteger.get(8))
            image.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.FILTER, com.tom_roush.pdfbox.cos.COSName.DCT_DECODE)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { content ->
                content.drawImage(image, 0f, 0f, 612f, 792f)
            }
        }
        doc.save(target)
    }
    return target
}
