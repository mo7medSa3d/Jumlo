package com.jumlo.app.data.pdf

import com.jumlo.app.core.PageTextComparison
import com.jumlo.app.core.PdfCompressionPolicy
import com.jumlo.app.core.PdfImagePlacementPolicy
import com.jumlo.app.core.PdfWatermarkStyle
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDPushButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/** Box coordinates are [x0, y0, x1, y1] in PDF user space (y grows upward). */
data class PageBox(val media: List<Float>, val crop: List<Float>, val rotationDegrees: Int)
data class PdfInfo(val pageCount: Int, val pageBoxes: List<PageBox>, val hasDigitalText: Boolean, val capabilities: Set<String>, val warnings: List<String>)

class PdfInput(val file: File, val password: CharArray? = null) {
    override fun toString(): String = "PdfInput(${file.name})"
    override fun equals(other: Any?): Boolean = other is PdfInput && other.file == file
    override fun hashCode(): Int = file.hashCode()
}

data class OrganizeOp(val sourceIndex: Int, val rotationDegrees: Int = 0)
data class PageGroup(val first: Int, val count: Int)

/** Normalized rectangle with a TOP-LEFT origin; converted once at the PDF boundary. */
class RedactRegion(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init { require(left in 0.0..1.0 && top in 0.0..1.0 && right in 0.0..1.0 && bottom in 0.0..1.0 && left < right && top < bottom) { "INVALID_REDACT_REGION" } }
}

sealed interface FormEntry {
    data class Text(val value: String) : FormEntry
    data class Check(val on: Boolean) : FormEntry
    data class Radio(val index: Int) : FormEntry
}

enum class CompareVerdict { SAME, DIFFERENT }
data class CompareResult(val pageCountA: Int, val pageCountB: Int, val verdict: CompareVerdict, val firstDifferentPage: Int?)

data class RedactResult(
    val removedTextChunks: Int,
    val removedImages: Int,
    val removedAnnotations: Int,
    val pageFallbacks: List<Int>,
    val leftoverText: Map<Int, List<String>>
)

/** Declared capabilities of this backend; drives UI gating and the support matrix. */
object PdfCapabilityPolicy {
    val capabilities: Set<String> = setOf(
        "MERGE", "SPLIT", "EXTRACT", "REORDER", "ROTATE", "DELETE", "INSERT",
        "CROP_BOX", "METADATA", "PROTECT_STANDARD", "REMOVE_PROTECTION",
        "COMPRESS_IMAGE_DOWNSCALE", "COMPARE_TEXT", "REDACT_OPERATOR_LEVEL",
        "FORM_FILL_TEXT_CHECKBOX_RADIO", "FORM_FLATTEN", "FORM_LIST",
        "STAMP_TEXT", "STAMP_IMAGE", "ANNOTATION_TEXT", "OCR_INVISIBLE_TEXT"
    )
}

/**
 * Structural PDF backend on PDFBox (Apache-2.0). Page indexes are zero-based unless
 * the caller is UI text; passwords are session-only via EphemeralSecretStore handles.
 */
object PdfBoxBackend {

    fun open(file: File, password: CharArray?): PDDocument =
        PDDocument.load(file, password?.let { String(it) } ?: "", MemoryUsageSetting.setupTempFileOnly())

    fun info(doc: PDDocument): PdfInfo {
        val boxes = doc.pages.map { page ->
            val media = page.mediaBox
            val crop = page.cropBox ?: media
            PageBox(
                listOf(media.lowerLeftX, media.lowerLeftY, media.upperRightX, media.upperRightY),
                listOf(crop.lowerLeftX, crop.lowerLeftY, crop.upperRightX, crop.upperRightY),
                page.rotation
            )
        }
        // Probe on demand; never materialize every page's extracted text for a
        // potentially many-thousand-page document just to classify it.
        val hasText = (0 until doc.numberOfPages).any { pageText(doc,it).isNotBlank() }
        val blank = !hasText
        val warnings = mutableListOf<String>()
        if (doc.isEncrypted) warnings += "WARNING_DOCUMENT_IS_ENCRYPTED"
        if (doc.numberOfPages > 0 && blank) warnings += "WARNING_NO_SELECTABLE_TEXT"
        return PdfInfo(doc.numberOfPages, boxes, !blank, PdfCapabilityPolicy.capabilities, warnings)
    }

    fun pageText(doc: PDDocument, zeroBasedIndex: Int): String {
        require(zeroBasedIndex in 0 until doc.numberOfPages) { "INVALID_PAGE_INDEX" }
        return PDFTextStripper().apply {
            sortByPosition = false
            startPage = zeroBasedIndex + 1
            endPage = zeroBasedIndex + 1
        }.getText(doc).trim()
    }

    // Retained for bounded consumers that explicitly need every page's text.
    fun pageTexts(doc: PDDocument): List<String> = (0 until doc.numberOfPages).map { pageText(doc,it) }

    /** Redaction verification must inspect every page but must not concatenate
     * a possibly multi-GB extracted text stream in memory. */
    fun containsText(doc: PDDocument, needle: String): Boolean =
        PageTextComparison.anyPageContains(doc.numberOfPages, needle) { pageText(doc,it) }

    fun merge(inputs: List<PdfInput>, out: File) {
        require(inputs.isNotEmpty()) { "NO_PDF_INPUTS" }
        val merger = PDFMergerUtility()
        inputs.forEach { merger.addSource(it.file) }
        merger.destinationFileName = out.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
    }

    fun split(doc: PDDocument, groups: List<PageGroup>, destinations: List<File>): List<File> {
        val pageCount = doc.numberOfPages
        require(groups.isNotEmpty() && groups.size == destinations.size) { "INVALID_SPLIT_GROUPS" }
        return groups.mapIndexed { index, group ->
            require(group.first >= 0 && group.count > 0 && group.first.toLong() + group.count <= pageCount) { "INVALID_SPLIT_GROUP" }
            val pages = (group.first until group.first + group.count).map { doc.getPage(it) to 0 }
            writeAssembled(pages, destinations[index])
        }
    }

    fun extract(doc: PDDocument, zeroBasedPages: List<Int>, out: File): File {
        organize(doc, zeroBasedPages.map { OrganizeOp(it) }, out)
        return out
    }

    fun organize(doc: PDDocument, ops: List<OrganizeOp>, out: File) {
        val pageCount = doc.numberOfPages
        require(ops.isNotEmpty() && ops.all { it.sourceIndex in 0 until pageCount }) { "INVALID_PAGE_INDEX" }
        writeAssembled(ops.map { op -> doc.getPage(op.sourceIndex) to op.rotationDegrees }, out)
    }

    fun insert(doc: PDDocument, other: PdfInput, atZeroIndex: Int, out: File) {
        require(atZeroIndex in 0..doc.pages.count) { "INVALID_INSERT_POSITION" }
        open(other.file, other.password).use { source ->
            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { target ->
                for (index in 0..doc.numberOfPages) {
                    if (index == atZeroIndex) {
                        for (otherIndex in 0 until source.numberOfPages) target.importPage(source.getPage(otherIndex))
                    }
                    if (index < doc.numberOfPages) target.importPage(doc.getPage(index))
                }
                target.save(out)
            }
        }
    }

    private fun writeAssembled(ordered: List<Pair<PDPage, Int>>, out: File): File {
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { target ->
            ordered.forEach { (page, _) -> target.importPage(page) }
            ordered.forEachIndexed { index, (_, degrees) -> rotateInside(target.getPage(index), degrees) }
            target.save(out)
        }
        return out
    }

    private fun rotateInside(page: PDPage, degrees: Int) {
        if (degrees % 360 == 0) return
        page.rotation = ((page.rotation + degrees) % 360 + 360) % 360
    }

    fun rotate(doc: PDDocument, zeroBasedPages: List<Int>, degrees: Int) {
        require(zeroBasedPages.all { it in 0 until doc.pages.count }) { "INVALID_PAGE_INDEX" }
        require(degrees % 90 == 0) { "INVALID_ROTATION" }
        zeroBasedPages.forEach { rotateInside(doc.getPage(it), degrees) }
    }

    fun setCrop(doc: PDDocument, zeroBasedPage: Int, left: Double, top: Double, right: Double, bottom: Double) {
        require(zeroBasedPage in 0 until doc.pages.count) { "INVALID_PAGE_INDEX" }
        val page = doc.getPage(zeroBasedPage)
        val media = page.mediaBox
        val box = com.jumlo.app.core.PdfCropCoordinates.fromTopLeft(
            media.lowerLeftX, media.lowerLeftY, media.width, media.height,
            left, top, right, bottom
        )
        page.cropBox = PDRectangle(box.x, box.y, box.width, box.height)
    }

    fun setMetadata(doc: PDDocument, values: Map<String, String>) {
        val info = doc.documentInformation
        values["title"]?.let { info.title = it }
        values["author"]?.let { info.author = it }
        values["subject"]?.let { info.subject = it }
        values["keywords"]?.let { info.keywords = it }
        values["creator"]?.let { info.creator = it }
        values["producer"]?.takeIf { it.isNotEmpty() }?.let { info.producer = it }
    }

    fun metadata(doc: PDDocument): Map<String, String> {
        val info = doc.documentInformation
        val result = mutableMapOf<String, String>()
        fun add(key: String, value: String) { if (value.isNotEmpty()) result[key] = value }
        add("title", info.title ?: "")
        add("author", info.author ?: "")
        add("subject", info.subject ?: "")
        add("keywords", info.keywords ?: "")
        add("creator", info.creator ?: "")
        add("producer", info.producer ?: "")
        return result
    }

    /** AES-128 with owner/user split. Passwords are never persisted by this layer. */
    fun protect(doc: PDDocument, userPassword: CharArray, ownerPassword: CharArray, out: File) {
        require(ownerPassword.isNotEmpty()) { "OWNER_PASSWORD_REQUIRED" }
        val permission = AccessPermission()
        permission.setCanModify(false)
        permission.setCanPrint(true)
        val policy = StandardProtectionPolicy(String(ownerPassword), String(userPassword), permission)
        policy.encryptionKeyLength = 128
        policy.isPreferAES = true
        doc.protect(policy)
        doc.save(out)
    }

    fun removeProtection(doc: PDDocument, out: File) {
        doc.setAllSecurityToBeRemoved(true)
        doc.save(out)
    }

    fun listFormFields(doc: PDDocument): List<String> {
        val acro = doc.documentCatalog.acroForm ?: return emptyList()
        return acro.fields.map { field ->
            val type = when (field) {
                is PDRadioButton -> "radio"
                is PDCheckBox -> "checkbox"
                is PDPushButton -> "button"
                is PDTextField -> "text"
                else -> "other"
            }
            val value = try { field.valueAsString } catch (_: Exception) { "" }
            listOf(field.partialName, type, value).joinToString("|")
        }
    }

    fun fillForm(doc: PDDocument, values: Map<String, FormEntry>) {
        val acro = doc.documentCatalog.acroForm ?: throw IllegalArgumentException("NO_ACROFORM")
        values.forEach { (name, entry) ->
            val field = acro.getField(name) ?: throw IllegalArgumentException("FORM_FIELD_NOT_FOUND")
            when (entry) {
                is FormEntry.Text -> {
                    require(field is PDTextField) { "FORM_FIELD_TYPE_MISMATCH" }
                    field.setValue(entry.value)
                }
                is FormEntry.Check -> {
                    require(field is PDCheckBox) { "FORM_FIELD_TYPE_MISMATCH" }
                    if (entry.on) field.check() else field.unCheck()
                }
                is FormEntry.Radio -> {
                    require(field is PDRadioButton) { "FORM_FIELD_TYPE_MISMATCH" }
                    val exportValues = field.exportValues
                    require(entry.index in exportValues.indices) { "FORM_FIELD_TYPE_MISMATCH" }
                    field.setValue(exportValues[entry.index])
                }
            }
        }
    }

    /**
     * Flattening: this port's PDAcroForm.flatten() strips fields without embedding widget
     * appearance streams, so paint each appearance onto its page and drop the AcroForm.
     */
    fun flattenForm(doc: PDDocument, out: File) {
        val acro = doc.documentCatalog.acroForm ?: throw IllegalArgumentException("NO_ACROFORM")
        val widgetsByPage = mutableMapOf<PDPage, MutableList<PDAnnotation>>()
        acro.fieldTree.forEach { field ->
            field.widgets.forEach { widget ->
                if (widget == null || widget.page == null) return@forEach
                widgetsByPage.getOrPut(widget.page) { mutableListOf() }.add(widget)
            }
        }
        widgetsByPage.forEach { (page, widgets) ->
            val survivors = mutableListOf<PDAnnotation>()
            try {
                page.annotations.forEach { ann -> if (widgets.none { it === ann }) survivors.add(ann) }
                page.setAnnotations(survivors)
            } catch (_: Exception) { /* keep original annotations */ }
            widgets.forEach { widget ->
                val appearance = widget.getNormalAppearanceStream() ?: return@forEach
                val rect = widget.rectangle ?: return@forEach
                val bbox = appearance.bBox
                val scaleX = if (bbox.width > 0.01f) rect.width / bbox.width else 1f
                val scaleY = if (bbox.height > 0.01f) rect.height / bbox.height else 1f
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.concatenate2CTM(
                        scaleX.toDouble(), 0.0, 0.0, scaleY.toDouble(),
                        (rect.lowerLeftX - scaleX * bbox.lowerLeftX).toDouble(),
                        (rect.lowerLeftY - scaleY * bbox.lowerLeftY).toDouble()
                    )
                    cs.drawForm(appearance)
                }
            }
        }
        doc.documentCatalog.acroForm = null
        doc.save(out)
    }

    fun compressImages(doc: PDDocument, maxDpi: Int, jpegQuality: Int = 85, onPage: ((Int, Int) -> Unit)? = null): Int =
        compress(doc, maxDpi, jpegQuality, onPage)

    /**
     * Multi-codec PDF image compressor:
     * 1. On Android, decodes any embedded PDImageXObject (JPEG/DCTDecode, PNG/Flate, etc.),
     *    downscales toward [maxDpi] if larger, and re-encodes via [JPEGFactory] at [jpegQuality]
     *    whenever the resulting stream is strictly smaller than the original stream.
     * 2. On pure JVM (or when Bitmap is unavailable), box-downscales 8-bit Flate DeviceRGB/Gray
     *    streams and quantizes toward [jpegQuality] for better deflate compression.
     */
    fun compress(doc: PDDocument, maxDpi: Int, jpegQuality: Int = 85, onPage: ((Int, Int) -> Unit)? = null): Int {
        require(maxDpi in 36..600) { "INVALID_COMPRESS_SETTINGS" }
        require(jpegQuality in 0..100) { "INVALID_COMPRESS_SETTINGS" }
        var replaced = 0
        val pageCount = doc.numberOfPages
        // Scanner apps often put the photo inside Form XObjects. The old loop
        // only looked at page-level resources, silently missing those images.
        val visited = java.util.IdentityHashMap<COSDictionary, Boolean>()
        fun process(resources: com.tom_roush.pdfbox.pdmodel.PDResources?,
                    maxWidth: Int, maxHeight: Int, depth: Int) {
            if (resources == null || depth > 8) return
            if (visited.put(resources.cosObject, true) != null) return
            for (name in resources.xObjectNames.toList()) {
                val objectRef = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
                when (objectRef) {
                    is PDFormXObject -> process(objectRef.resources, maxWidth, maxHeight, depth + 1)
                    is PDImageXObject -> {
                        val newImage = downscaleAndCompressImage(
                            doc, objectRef, maxWidth, maxHeight, jpegQuality
                        ) ?: downscaleFlateImage(objectRef, maxWidth, maxHeight, jpegQuality)
                        if (newImage != null) {
                            val dict = resources.cosObject.getDictionaryObject(COSName.XOBJECT) as? COSDictionary
                            if (dict != null) {
                                dict.setItem(name, newImage)
                                replaced++
                            }
                        }
                    }
                }
            }
        }
        doc.pages.forEachIndexed { pageIndex, page ->
            val media = page.mediaBox
            val maxWidth = (media.width * maxDpi / 72.0).toInt().coerceAtLeast(16)
            val maxHeight = (media.height * maxDpi / 72.0).toInt().coerceAtLeast(16)
            process(page.resources, maxWidth, maxHeight, 0)
            onPage?.invoke(pageIndex + 1, pageCount)
        }
        return replaced
    }

    private fun downscaleAndCompressImage(
        doc: PDDocument,
        image: PDImageXObject,
        maxWidth: Int,
        maxHeight: Int,
        jpegQuality: Int
    ): PDImageXObject? = runCatching {
        val originalLength = (image.cosObject as? COSStream)?.length ?: Long.MAX_VALUE
        // Decode high-resolution scans with PDFBox subsampling instead of
        // dropping 300+ DPI scans because their full ARGB size exceeds 32 MiB.
        val subsampling = PdfCompressionPolicy.decodeSubsampling(
            image.width, image.height, Runtime.getRuntime().maxMemory()
        )
        if (subsampling < 1) return null
        // Preserve masks, transparency and 1-bit scan/text rasters rather than
        // replacing them with lossy JPEG artifacts.
        val dict = image.cosObject as? COSDictionary ?: return null
        if (dict.getDictionaryObject(COSName.SMASK) != null ||
            dict.getDictionaryObject(COSName.MASK) != null ||
            dict.getInt(COSName.BITS_PER_COMPONENT) == 1) return null
        val bitmap = (if (subsampling > 1) image.getImage(null, subsampling)
            else image.image) ?: return null
        try {
            val scale = minOf(1.0, minOf(maxWidth.toDouble() / bitmap.width.coerceAtLeast(1), maxHeight.toDouble() / bitmap.height.coerceAtLeast(1)))
            val targetW = maxOf(1, (bitmap.width * scale).toInt())
            val targetH = maxOf(1, (bitmap.height * scale).toInt())
            val workBitmap = if (targetW < bitmap.width || targetH < bitmap.height) {
                android.graphics.Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else bitmap
            try {
                val q = (jpegQuality.coerceIn(10, 95) / 100f)
                val candidate = JPEGFactory.createFromImage(doc, workBitmap, q)
                val newLength = (candidate.cosObject as? COSStream)?.length ?: Long.MAX_VALUE
                if (newLength > 0L && newLength < originalLength) candidate else null
            } finally {
                if (workBitmap !== bitmap && !workBitmap.isRecycled) workBitmap.recycle()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }.getOrNull()

    private fun downscaleFlateImage(image: PDImageXObject, maxWidth: Int, maxHeight: Int, jpegQuality: Int = 85): PDImageXObject? {
        if (image.width <= maxWidth && image.height <= maxHeight && jpegQuality >= 95) return null
        val cosStream = image.cosObject as? COSStream ?: return null
        val dict = cosStream as COSDictionary
        val filter = dict.getDictionaryObject(COSName.FILTER)
        val isFlate = when (filter) {
            is COSName -> filter == COSName.FLATE_DECODE
            is COSArray -> filter.size() == 1 && (filter.get(0) as? COSName) == COSName.FLATE_DECODE
            else -> false
        }
        if (!isFlate) return null
        if (dict.getDictionaryObject(COSName.DECODE_PARMS) != null) return null
        val bits = (dict.getDictionaryObject(COSName.BITS_PER_COMPONENT) as? COSNumber)?.intValue() ?: return null
        if (bits != 8) return null
        val colorSpace = (dict.getDictionaryObject(COSName.COLORSPACE) as? COSName) ?: return null
        if (colorSpace != COSName.DEVICERGB && colorSpace != COSName.DEVICEGRAY) return null
        if (dict.getDictionaryObject(COSName.SMASK) != null) return null
        val bytesPerPixel = if (colorSpace == COSName.DEVICERGB) 3 else 1
        val decodedBytes = image.width.toLong() * image.height * bytesPerPixel
        if (image.width <= 0 || image.height <= 0 || decodedBytes > minOf(Runtime.getRuntime().maxMemory() / 4, 96L * 1024 * 1024)) return null
        val raw = ByteArray(decodedBytes.toInt())
        image.createInputStream().use { input ->
            var read = 0
            while (read < raw.size) {
                val n = input.read(raw, read, raw.size - read)
                if (n < 0) return null
                read += n
            }
        }
        val scale = minOf(1.0, minOf(maxWidth.toDouble() / image.width, maxHeight.toDouble() / image.height))
        val targetW = maxOf(1, (image.width * scale).toInt())
        val targetH = maxOf(1, (image.height * scale).toInt())
        val data = boxDownscale(raw, image.width, image.height, targetW, targetH, bytesPerPixel, jpegQuality)
        val stream = COSStream()
        stream.setItem(COSName.SUBTYPE, COSName.IMAGE)
        stream.setInt(COSName.WIDTH, targetW)
        stream.setInt(COSName.HEIGHT, targetH)
        stream.setItem(COSName.COLORSPACE, colorSpace)
        stream.setInt(COSName.BITS_PER_COMPONENT, 8)
        stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
        stream.createOutputStream().use { it.write(data) }
        return PDImageXObject(PDStream(stream), null)
    }

    private fun boxDownscale(src: ByteArray, sw: Int, sh: Int, tw: Int, th: Int, cpp: Int, quality: Int = 85): ByteArray {
        val out = ByteArray(tw * th * cpp)
        val step = if (quality in 1..79) ((100 - quality) / 12).coerceAtLeast(1) else 1
        for (y in 0 until th) {
            val y0 = y * sh / th
            val y1 = ((y + 1) * sh / th).coerceAtLeast(y0 + 1).coerceAtMost(sh)
            for (x in 0 until tw) {
                val x0 = x * sw / tw
                val x1 = ((x + 1) * sw / tw).coerceAtLeast(x0 + 1).coerceAtMost(sw)
                val sums = IntArray(cpp)
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val base = (yy * sw + xx) * cpp
                    for (c in 0 until cpp) sums[c] += src[base + c].toInt() and 255
                }
                val count = (y1 - y0) * (x1 - x0)
                val base = (y * tw + x) * cpp
                for (c in 0 until cpp) {
                    val avg = sums[c] / count
                    val quantized = if (step > 1) (avg / step) * step else avg
                    out[base + c] = quantized.toByte()
                }
            }
        }
        return out
    }

    fun overlayText(doc: PDDocument, zeroBasedPages: List<Int>, text: String, xNorm: Double, yNormTop: Double, fontSize: Float = 12f, gray: Float = 0.1f) {
        stampText(doc, zeroBasedPages, text, xNorm, yNormTop, fontSize, gray)
    }

    /**
     * Rasterize only the watermark overlay (never PDF pages). Android text shaping
     * preserves Arabic/Unicode and a shared transparent image is reused for all
     * selected pages. Text and vector data in the source document stay intact.
     */
    fun stampWatermarkAndNumbers(
        doc: PDDocument, zeroBasedPages: List<Int>, watermarkText: String,
        addPageNumbers: Boolean, style: PdfWatermarkStyle = PdfWatermarkStyle()
    ) {
        require(zeroBasedPages.all { it in 0 until doc.numberOfPages }) { "INVALID_PAGE_INDEX" }
        val text = watermarkText.trim().replace(Regex("\\s+"), " ")
        require(text.length <= 120) { "WATERMARK_TEXT_TOO_LONG" }
        if (text.isNotEmpty() && zeroBasedPages.isNotEmpty()) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                textSize = 84f
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                color = android.graphics.Color.parseColor(style.colorHex)
                alpha = (style.opacityPercent * 255 / 100).coerceIn(0,255)
            }
            // Keep temporary ARGB and PDF image allocations bounded.
            val rawWidth = paint.measureText(text)
            if (rawWidth > 1650f) paint.textSize *= 1650f / rawWidth
            val metrics = paint.fontMetrics
            val rasterWidth = (paint.measureText(text) + 48f).toInt().coerceIn(64,1710)
            val rasterHeight = (metrics.descent - metrics.ascent + 36f).toInt().coerceIn(80,220)
            val bitmap = android.graphics.Bitmap.createBitmap(
                rasterWidth,rasterHeight,android.graphics.Bitmap.Config.ARGB_8888)
            val image = try {
                android.graphics.Canvas(bitmap).drawText(text,24f,18f-metrics.ascent,paint)
                LosslessFactory.createFromImage(doc,bitmap)
            } finally {
                bitmap.recycle()
            }
            val widthPt = rasterWidth * (style.fontSizePt / paint.textSize)
            val heightPt = rasterHeight * (style.fontSizePt / paint.textSize)
            val radians = Math.toRadians((-style.rotationDegrees).toDouble())
            val spots = if (style.tiled) {
                listOf(0.18,0.5,0.82).flatMap { y ->
                    listOf(0.18,0.5,0.82).map { x -> x to y }
                }
            } else listOf(style.centerX to style.centerY)
            zeroBasedPages.forEach { index ->
                val page = doc.getPage(index)
                val media = page.mediaBox
                val cap = media.width * (if(style.tiled) 0.36f else 0.8f)
                val scale = if(widthPt > cap) cap / widthPt else 1f
                val drawW = widthPt * scale
                val drawH = heightPt * scale
                PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { cs ->
                    spots.forEach { (fx,fy) ->
                        val x = (media.lowerLeftX + fx * media.width).toFloat()
                        val y = (media.lowerLeftY + (1.0-fy) * media.height).toFloat()
                        cs.saveGraphicsState()
                        try {
                            cs.transform(Matrix.getRotateInstance(radians,x,y))
                            cs.drawImage(image,-drawW/2f,-drawH/2f,drawW,drawH)
                        } finally {
                            cs.restoreGraphicsState()
                        }
                    }
                }
            }
        }
        if (addPageNumbers) zeroBasedPages.forEach { idx ->
            stampText(doc,listOf(idx),"${idx + 1}",0.47,0.96,10f,0.4f)
        }
    }

    /** Overlay text (annotations/overlay, watermark, page numbers share this primitive). */
    fun stampText(doc: PDDocument, zeroBasedPages: List<Int>, text: String, xNorm: Double, yNormTop: Double, fontSize: Float, gray: Float) {
        require(text.isNotBlank() && fontSize in 4f..200f && gray in 0f..1f) { "INVALID_STAMP" }
        require(zeroBasedPages.all { it in 0 until doc.pages.count }) { "INVALID_PAGE_INDEX" }
        val safeText = text.map { if (it in ' '..'~') it else ' ' }.joinToString("").ifBlank { "Note" }
        zeroBasedPages.forEach { index ->
            val page = doc.getPage(index)
            val media = page.mediaBox
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, fontSize)
                cs.setNonStrokingColor(gray, gray, gray)
                cs.newLineAtOffset((media.lowerLeftX + xNorm * media.width).toFloat(), (media.lowerLeftY + (1.0 - yNormTop) * media.height).toFloat())
                cs.showText(safeText)
                cs.endText()
            }
        }
    }

    /** Invisible (but extractable/searchable) text layer for OCR output. */
    fun stampInvisibleText(doc: PDDocument, zeroBasedPage: Int, text: String, xNorm: Double, yNormTop: Double, fontSize: Float, strict: Boolean = true) {
        require(zeroBasedPage in 0 until doc.pages.count) { "INVALID_PAGE_INDEX" }
        val lines = if (strict) com.jumlo.app.core.OcrTextPolicy.requireRepresentable(text)
            else com.jumlo.app.core.OcrTextPolicy.sanitizeForLayer(text)
        if (lines.isEmpty()) return
        val page = doc.getPage(zeroBasedPage); val media = page.mediaBox
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.beginText(); cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, fontSize)
            cs.setRenderingMode(com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER)
            cs.newLineAtOffset((media.lowerLeftX + xNorm * media.width).toFloat(), (media.lowerLeftY + (1.0 - yNormTop) * media.height).toFloat())
            lines.forEachIndexed { i, line -> if (i > 0) cs.newLineAtOffset(0f, -fontSize * 1.2f); cs.showText(line) }
            cs.endText()
        }
    }

    /** Places an image (signature/logo) at a normalized top-left-origin rect on one page. */
    fun stampImage(doc: PDDocument, zeroBasedPage: Int, imageFile: File, left: Double, top: Double, right: Double, bottom: Double) {
        require(zeroBasedPage in 0 until doc.pages.count) { "INVALID_PAGE_INDEX" }
        listOf(left, top, right, bottom).forEach { require(it in 0.0..1.0) { "INVALID_CROP" } }
        require(left < right && top < bottom) { "INVALID_CROP" }
        val page = doc.getPage(zeroBasedPage)
        val media = page.mediaBox
        val image = com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromFile(imageFile.path, doc)
        val placement = PdfImagePlacementPolicy.fit(
            (media.lowerLeftX + left * media.width).toFloat(),
            (media.lowerLeftY + (1.0 - bottom) * media.height).toFloat(),
            ((right - left) * media.width).toFloat(),
            ((bottom - top) * media.height).toFloat(),
            image.width, image.height
        )
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.drawImage(image, placement.x, placement.y, placement.width, placement.height)
        }
    }

    /** Adds a free-text annotation anchored at a normalized point. */
    fun addTextAnnotation(doc: PDDocument, zeroBasedPage: Int, contents: String, xNorm: Double, yNormTop: Double) {
        require(contents.isNotBlank() && zeroBasedPage in 0 until doc.pages.count) { "INVALID_ANNOTATION" }
        val page = doc.getPage(zeroBasedPage)
        val media = page.mediaBox
        val annotation = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText()
        val x = (media.lowerLeftX + xNorm * media.width).toFloat(); val y = (media.lowerLeftY + (1.0 - yNormTop) * media.height).toFloat()
        annotation.rectangle = PDRectangle(x, y, 20f, 20f)
        annotation.contents = contents
        annotation.setOpen(true)
        val annotations = page.annotations.toMutableList(); annotations.add(annotation); page.setAnnotations(annotations)
    }

    fun compareText(a: PDDocument, b: PDDocument): CompareResult = compare(a, b)

    fun compare(a: PDDocument, b: PDDocument): CompareResult {
        val countA = a.numberOfPages
        val countB = b.numberOfPages
        if (countA != countB) return CompareResult(countA,countB,CompareVerdict.DIFFERENT,null)
        val first = PageTextComparison.firstMismatch(countA,
            left = { pageText(a,it) }, right = { pageText(b,it) })
        return CompareResult(countA,countB,if(first == null) CompareVerdict.SAME else CompareVerdict.DIFFERENT,first)
    }

    /**
     * Operator-level redaction: removes text-showing operands, fully-covered images and
     * annotations inside each region. Pages too complex for a pure pass are returned in
     * [pageFallbacks] and must be rasterized by the Android handler before publication.
     */
    fun redact(doc: PDDocument, regionsByPage: Map<Int, List<RedactRegion>>): RedactResult {
        var removedChunks = 0
        var removedImages = 0
        val fallbacks = mutableListOf<Int>()
        val fragments = mutableMapOf<Int, Set<String>>()
        doc.pages.forEachIndexed { pageIndex, page ->
            val regions = regionsByPage[pageIndex].orEmpty()
            if (regions.isEmpty()) return@forEachIndexed
            val media = page.mediaBox
            val areas = regions.map { r -> Geo.Area(
                (r.left * media.width).toFloat(),
                ((1.0 - r.bottom) * media.height).toFloat(),
                (r.right * media.width).toFloat(),
                ((1.0 - r.top) * media.height).toFloat()
            ) }
            var streamCount = 0
            page.getContentStreams().let { streams ->
                while (streams.hasNext()) { ++streamCount; streams.next(); if (streamCount > 1) break }
            }
            if (streamCount > 1 || !page.hasContents()) { fallbacks.add(pageIndex); return@forEachIndexed }
            val plan = ContentRedactor(doc, page, areas)
            val removed = plan.execute()
            removedChunks += removed.removedChunks
            removedImages += removed.images
            if (removed.complex || removed.removedChunks == 0) fallbacks.add(pageIndex)
            plan.paintOver(areas)
            fragments[pageIndex] = plan.removedWordFragments.toSet()
        }
        val removedAnnotations = stripAnnotations(doc, regionsByPage)
        val leftover = leftoverAfterRedact(doc, fragments)
        return RedactResult(removedChunks, removedImages, removedAnnotations, fallbacks.distinct(), leftover)
    }

    private fun leftoverAfterRedact(doc: PDDocument, fragments: Map<Int, Set<String>>): Map<Int, List<String>> {
        if (fragments.isEmpty()) return emptyMap()
        val leftover = mutableMapOf<Int, List<String>>()
        fragments.forEach { (page, words) ->
            if (page !in 0 until doc.numberOfPages) return@forEach
            val text = pageText(doc,page)
            val stuck = words.filter { it.isNotBlank() && text.contains(it) }
            if (stuck.isNotEmpty()) leftover[page] = stuck
        }
        return leftover
    }

    private class Geo private constructor() {
        data class Area(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
            val w: Float get() = x1 - x0
            val h: Float get() = y1 - y0
            fun intersects(o: Area): Boolean = x0 < o.x1 && o.x0 < x1 && y0 < o.y1 && o.y0 < y1
            fun contains(o: Area): Boolean = o.x0 >= x0 && o.y0 >= y0 && o.x1 <= x1 && o.y1 <= y1
        }
        /** PDF affine matrix (a b c d e f). */
        data class Mat(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
            operator fun times(m: Mat): Mat = Mat(
                a * m.a + b * m.c, a * m.b + b * m.d,
                c * m.a + d * m.c, c * m.b + d * m.d,
                e * m.a + f * m.c + m.e, e * m.b + f * m.d + m.f
            )
            fun apply(x: Float, y: Float): Pair<Float, Float> = Pair(a * x + c * y + e, b * x + d * y + f)
            val scale: Float get() = sqrt(abs((a * d - b * c).toDouble())).toFloat().coerceAtLeast(0.05f)
            companion object {
                val IDENTITY = Mat(1f, 0f, 0f, 1f, 0f, 0f)
                val TRANSLATE = { dx: Float, dy: Float -> Mat(1f, 0f, 0f, 1f, dx, dy) }
            }
        }
    }

    private class ContentRedactor(private val doc: PDDocument, private val page: PDPage, private val areas: List<Geo.Area>) {
        val removedWordFragments = mutableSetOf<String>()
        private val tokens = mutableListOf<Any>()
        private val removeIdx = mutableSetOf<Int>()
        private var ctm = Geo.Mat.IDENTITY
        private var tlm = Geo.Mat.IDENTITY
        private var fontSize = 0f
        private var leading = 0f
        private var mutated = false
        var removedChunks = 0; private set
        var images = 0; private set
        var complex = false; private set

        data class Outcome(val removedChunks: Int, val images: Int, val complex: Boolean)

        private fun bounds() = Geo.Area(areas.minOf { it.x0 }, areas.minOf { it.y0 }, areas.maxOf { it.x1 }, areas.maxOf { it.y1 })

        fun execute(): Outcome {
            try {
                val parser = PDFStreamParser(page)
                parser.parse()
                parser.tokens.forEach { tokens.add(it) }
            } catch (_: Exception) { complex = true; return Outcome(0, 0, true) }
            val graphicsStack = mutableListOf<Geo.Mat>()
            var i = 0
            while (i < tokens.size) {
                val operator = tokens[i] as? Operator
                if (operator == null) { i++; continue }
                when (operator.getName()) {
                    "q" -> graphicsStack.add(ctm)
                    "Q" -> ctm = if (graphicsStack.isNotEmpty()) graphicsStack.removeAt(graphicsStack.lastIndex) else Geo.Mat.IDENTITY
                    "cm" -> { val m = operandMatrix(i); if (m != null) ctm = m * ctm else complex = true }
                    "Tf" -> fontSize = (tokens.getOrNull(i - 1) as? COSNumber)?.floatValue() ?: 0f
                    "Tm" -> { val m = operandMatrix(i); if (m != null) tlm = m else complex = true }
                    "Td" -> {
                        val dx = (tokens.getOrNull(i - 2) as? COSNumber)?.floatValue() ?: 0f
                        val dy = (tokens.getOrNull(i - 1) as? COSNumber)?.floatValue() ?: 0f
                        tlm = Geo.Mat.TRANSLATE(dx, dy) * tlm
                    }
                    "TD" -> {
                        val dx = (tokens.getOrNull(i - 2) as? COSNumber)?.floatValue() ?: 0f
                        val dy = (tokens.getOrNull(i - 1) as? COSNumber)?.floatValue() ?: 0f
                        leading = -dy
                        tlm = Geo.Mat.TRANSLATE(dx, dy) * tlm
                    }
                    "TL" -> leading = (tokens.getOrNull(i - 1) as? COSNumber)?.floatValue() ?: 0f
                    "T*" -> tlm = Geo.Mat.TRANSLATE(0f, -leading) * tlm
                    "Tj" -> {
                        val text = (tokens.getOrNull(i - 1) as? COSString)?.string ?: ""
                        if (mutateText(i, text)) removedChunks++
                        i++; continue
                    }
                    "TJ" -> {
                        val text = (tokens.getOrNull(i - 1) as? COSArray)?.let { array ->
                            (0 until array.size()).map { (array.getObject(it) as? COSString)?.string ?: "" }.joinToString("")
                        } ?: ""
                        if (mutateText(i, text)) removedChunks++
                        i++; continue
                    }
                    "'" -> {
                        tlm = Geo.Mat.TRANSLATE(0f, -leading) * tlm
                        val text = (tokens.getOrNull(i - 1) as? COSString)?.string ?: ""
                        if (mutateText(i, text)) removedChunks++
                        i++; continue
                    }
                    "\"" -> {
                        tlm = Geo.Mat.TRANSLATE((tokens.getOrNull(i - 2) as? COSNumber)?.floatValue() ?: 0f, -leading) * tlm
                        val text = (tokens.getOrNull(i - 1) as? COSString)?.string ?: ""
                        if (mutateText(i, text)) removedChunks++
                        i++; continue
                    }
                    "Do" -> {
                        val name = tokens.getOrNull(i - 1) as? COSName
                        val xobject = name?.let { page.resources?.getXObject(it) }
                        val image = xobject as? PDImageXObject
                        if (name == null || image == null) return Outcome(removedChunks, images, true)
                        val w = image.width.toFloat(); val h = image.height.toFloat()
                        val corners = listOf(0f to 0f, w to 0f, w to h, 0f to h).map { (x, y) -> ctm.apply(x, y) }
                        val rect = Geo.Area(
                            corners.minOf { it.first }, corners.minOf { it.second },
                            corners.maxOf { it.first }, corners.maxOf { it.second }
                        )
                        when {
                            rect.intersects(bounds()) && areas.any { it.contains(rect) } -> {
                                removeIdx.add(i - 1); removeIdx.add(i)
                                (page.resources?.cosObject?.getDictionaryObject(COSName.XOBJECT) as? COSDictionary)?.removeItem(name)
                                page.resources?.cosObject?.removeItem(name)
                                images++
                            }
                            rect.intersects(bounds()) -> complex = true
                        }
                    }
                    "re", "W", "Wn", "gs", "sc", "scn", "rg" -> { /* benign */ }
                    "BT" -> { tlm = Geo.Mat.IDENTITY; leading = 0f }
                    "ET", "Tc", "Tw", "Tz", "Ts", "Tr" -> { /* benign */ }
                    "f", "S", "m", "l", "c", "h", "B", "Bs", "b", "b*", "B*" -> { /* draw pass-through */ }
                    "sh", "fs", "BI", "DI", "EI" -> complex = true
                    else -> { /* unknown operators leave removal intact; kept as-is */ }
                }
                i++
            }
            if (removeIdx.isNotEmpty() || mutated) replaceContent(page, tokens.filterIndexed { index, _ -> index !in removeIdx })
            return Outcome(removedChunks, images, complex)
        }

        private fun operandMatrix(index: Int): Geo.Mat? {
            fun num(delta: Int, fallback: Float): Float = (tokens.getOrNull(index - delta) as? COSNumber)?.floatValue() ?: fallback
            return Geo.Mat(num(6, 1f), num(5, 0f), num(4, 0f), num(3, 1f), num(2, 0f), num(1, 0f))
        }

        private fun mutateText(operatorIndex: Int, chars: String): Boolean {
            val point = ctm.apply(tlm.e, tlm.f)
            val size = (fontSize * ctm.scale * tlm.scale).coerceAtLeast(0.5f)
            val width = (maxOf(chars.length, 1) * size * 0.65f).coerceAtLeast(size)
            val run = Geo.Area(point.first - size * 0.3f, point.second - size, point.first + width, point.second + size * 1.2f)
            if (areas.none { it.intersects(run) }) return false
            if (chars.length in 1..40) removedWordFragments.add(chars)
            when ((tokens[operatorIndex] as Operator).getName()) {
                "Tj", "'", "\"" -> tokens[operatorIndex - 1] = COSString("")
                "TJ" -> {
                    val array = COSArray()
                    array.add(COSString(""))
                    tokens[operatorIndex - 1] = array
                }
            }
            mutated = true
            return true
        }

        /** Paints opaque bars over each region so nothing visual remains. */
        fun paintOver(paintAreas: List<Geo.Area>) {
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.setNonStrokingColor(0, 0, 0)
                paintAreas.forEach { area -> cs.addRect(area.x0, area.y0, area.w, area.h); cs.fill() }
            }
        }
    }

    private fun stripAnnotations(doc: PDDocument, regionsByPage: Map<Int, List<RedactRegion>>): Int {
        var removed = 0
        doc.pages.forEachIndexed { pageIndex, page ->
            val regions = regionsByPage[pageIndex].orEmpty()
            if (regions.isEmpty()) return@forEachIndexed
            val media = page.mediaBox
            val areas = regions.map { r -> Geo.Area(
                (r.left * media.width).toFloat(), ((1.0 - r.bottom) * media.height).toFloat(),
                (r.right * media.width).toFloat(), ((1.0 - r.top) * media.height).toFloat()
            ) }
            val survivors = mutableListOf<PDAnnotation>()
            try {
                page.annotations.forEach { annotation ->
                    if (annotation == null) return@forEach
                    val rect = annotation.rectangle ?: return@forEach
                    val area = Geo.Area(rect.lowerLeftX, rect.lowerLeftY, rect.upperRightX, rect.upperRightY)
                    if (!area.containsAll(areas)) survivors.add(annotation) else removed++
                }
            } catch (_: Exception) { return@forEachIndexed }
            page.setAnnotations(survivors)
        }
        return removed
    }

    private fun Geo.Area.containsAll(areas: List<Geo.Area>): Boolean = areas.any { it.contains(this) || it.intersects(this) }

    private fun replaceContent(page: PDPage, tokens: List<Any>) {
        val stream = COSStream()
        stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
        stream.createOutputStream().use { output -> ContentStreamWriter(output).writeTokens(tokens) }
        page.setContents(PDStream(stream))
    }
}
