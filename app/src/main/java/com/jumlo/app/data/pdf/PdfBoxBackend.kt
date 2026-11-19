package com.jumlo.app.data.pdf

import com.jumlo.app.core.PageTextComparison
import com.jumlo.app.core.PdfImagePlacementPolicy
import com.jumlo.app.core.PdfDisplayCoordinates
import com.jumlo.app.core.PdfDisplayFrame
import com.jumlo.app.core.PdfWatermarkStyle
import com.jumlo.app.core.OverlayStylePolicy
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Box coordinates are [x0, y0, x1, y1] in PDF user space (y grows upward). */
data class PageBox(val media: List<Float>, val crop: List<Float>, val rotationDegrees: Int)
data class PdfInfo(val pageCount: Int, val pageBoxes: List<PageBox>, val hasDigitalText: Boolean, val capabilities: Set<String>, val warnings: List<String>)

class PdfInput(val file: File, val password: CharArray? = null) {
    override fun toString(): String = "PdfInput(${file.name})"
    override fun equals(other: Any?): Boolean = other is PdfInput && other.file == file
    override fun hashCode(): Int = file.hashCode()
}

/**
 * The bitmap budget the compressor works to when the caller does not give one.
 * It mirrors the job budget: half the heap, capped.
 */
val DEFAULT_MAX_BITMAP_BYTES: Long = minOf(Runtime.getRuntime().maxMemory() / 2, 192L * 1024 * 1024)

data class OrganizeOp(val sourceIndex: Int, val rotationDegrees: Int = 0)
data class PageGroup(val first: Int, val count: Int)

data class PdfImageOverlay(val pageIndex: Int, val left: Double, val top: Double, val right: Double, val bottom: Double, val rotationDegrees: Float = 0f)

/** Declared capabilities of this backend; drives UI gating and the support matrix. */
object PdfCapabilityPolicy {
    val capabilities: Set<String> = setOf(
        "MERGE", "SPLIT", "EXTRACT", "REORDER", "ROTATE", "DELETE", "INSERT",
        "CROP_BOX", "METADATA", "PROTECT_STANDARD", "REMOVE_PROTECTION",
        "COMPRESS_IMAGE_DOWNSCALE", "STAMP_TEXT", "STAMP_IMAGE", "OCR_INVISIBLE_TEXT"
    )
}

/**
 * Structural PDF backend on PDFBox (Apache-2.0). Page indexes are zero-based unless
 * the caller is UI text; passwords are session-only via EphemeralSecretStore handles.
 */
object PdfBoxBackend {
    private val WHITESPACE_REGEX = Regex("\\s+")

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
        require(groups.isNotEmpty() && groups.size == destinations.size) { "INVALID_SPLIT_GROUPS" }
        val temp = File.createTempFile("split-src-", ".pdf", destinations.first().parentFile)
        try {
            doc.save(temp)
            val pageCount = doc.numberOfPages
            return groups.mapIndexed { index, group ->
                require(group.first >= 0 && group.count > 0 && group.first.toLong() + group.count <= pageCount) { "INVALID_SPLIT_GROUP" }
                assemble(temp, destinations[index], (group.first until group.first + group.count).map { it to 0 })
                destinations[index]
            }
        } finally { temp.delete() }
    }

    fun extract(doc: PDDocument, zeroBasedPages: List<Int>, out: File): File {
        val temp = File.createTempFile("extract-src-", ".pdf", out.parentFile)
        try {
            doc.save(temp)
            assemble(temp, out, zeroBasedPages.map { it to 0 })
        } finally { temp.delete() }
        return out
    }

    fun organize(doc: PDDocument, ops: List<OrganizeOp>, out: File) {
        val pageCount = doc.numberOfPages
        require(ops.isNotEmpty() && ops.all { it.sourceIndex in 0 until pageCount }) { "INVALID_PAGE_INDEX" }
        val temp = File.createTempFile("organize-src-", ".pdf", out.parentFile)
        try {
            doc.save(temp)
            assemble(temp, out, ops.map { it.sourceIndex to it.rotationDegrees })
        } finally { temp.delete() }
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
        val box=PdfDisplayCoordinates.normalizedRectangle(displayFrame(page),left,top,right,bottom)
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
    fun protect(doc: PDDocument, userPassword: CharArray, ownerPassword: CharArray, out: File,
                permissions: com.jumlo.app.core.PdfProtectionPermissions = com.jumlo.app.core.PdfProtectionPermissions()) {
        require(ownerPassword.isNotEmpty()) { "OWNER_PASSWORD_REQUIRED" }
        val permission = AccessPermission()
        permission.setCanModify(permissions.allowModify)
        permission.setCanAssembleDocument(permissions.allowModify)
        permission.setCanPrint(permissions.allowPrinting)
        permission.setCanPrintFaithful(permissions.allowPrinting)
        permission.setCanExtractContent(permissions.allowCopy)
        permission.setCanModifyAnnotations(permissions.allowAnnotations)
        permission.setCanFillInForm(permissions.allowForms)
        permission.setCanExtractForAccessibility(true)
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

    /**
     * Compress the images of [doc] and return how many were re-encoded.
     *
     * The work lives in [PdfImageCompressor], which has no Android types in it,
     * so the same code can be run on the host as on the device.
     */
    fun compress(
        doc: PDDocument,
        maxDpi: Int,
        jpegQuality: Int = 85,
        onPage: ((Int, Int) -> Unit)? = null
    ): Int = compressImages(doc, maxDpi, jpegQuality, onPage)

    fun compressImages(
        doc: PDDocument,
        maxDpi: Int,
        jpegQuality: Int = 85,
        onPage: ((Int, Int) -> Unit)? = null,
        maxBitmapBytes: Long = DEFAULT_MAX_BITMAP_BYTES,
        codec: PdfImageCodec? = null
    ): Int = compressImagesWithReport(doc, maxDpi, jpegQuality, onPage, maxBitmapBytes, codec).replaced

    /**
     * The image compressor, with the report of what it did.
     *
     * The work itself lives in [PdfImageCompressor], which has no Android types
     * in it, so the same code can be run on the host. The codec is the byte-level
     * seam: the Android one uses `BitmapFactory`, the host one uses ImageIO.
     */
    fun compressImagesWithReport(
        doc: PDDocument,
        maxDpi: Int,
        jpegQuality: Int = 85,
        onPage: ((Int, Int) -> Unit)? = null,
        maxBitmapBytes: Long = DEFAULT_MAX_BITMAP_BYTES,
        codec: PdfImageCodec? = null,
        checkCancel: () -> Unit = {}
    ): PdfImageCompressor.Report =
        PdfImageCompressor.compress(doc, maxDpi, jpegQuality, onPage, codec ?: AndroidImageCodec(maxBitmapBytes),
            maxBitmapBytes, checkCancel)

    fun overlayText(doc: PDDocument, zeroBasedPages: List<Int>, text: String, xNorm: Double, yNormTop: Double, fontSize: Float = 12f, gray: Float = 0.1f, colorHex:String="") {
        stampText(doc, zeroBasedPages, text, xNorm, yNormTop, fontSize, gray,colorHex)
    }

    /**
     * Rasterize only the watermark overlay (never PDF pages). Android text shaping
     * preserves Arabic/Unicode and a shared transparent image is reused for all
     * selected pages. Text and vector data in the source document stay intact.
     */
    fun stampWatermarkAndNumbers(
        doc: PDDocument, zeroBasedPages: List<Int>, watermarkText: String,
        addPageNumbers: Boolean, style: PdfWatermarkStyle = PdfWatermarkStyle(), checkCancellation: () -> Unit = {}
    ) {
        require(zeroBasedPages.all { it in 0 until doc.numberOfPages }) { "INVALID_PAGE_INDEX" }
        val text = watermarkText.trim().replace(WHITESPACE_REGEX, " ")
        require(text.length <= 120) { "WATERMARK_TEXT_TOO_LONG" }
        if (text.isNotEmpty() && zeroBasedPages.isNotEmpty()) {
            checkCancellation()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                textSize = 84f
                typeface = android.graphics.Typeface.create(when(style.fontFamily) {
                    "serif" -> "serif"; "mono" -> "monospace"; else -> "sans-serif"
                }, android.graphics.Typeface.BOLD)
                color = runCatching { android.graphics.Color.parseColor(OverlayStylePolicy.requireColor(style.colorHex)) }.getOrElse { android.graphics.Color.parseColor("#64748B") }
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
            stampWatermarkImage(doc,zeroBasedPages,image,widthPt,heightPt,style,checkCancellation)
        }
        if (addPageNumbers) zeroBasedPages.forEach { idx ->
            checkCancellation()
            val label = "${idx + 1}"
            val frame = displayFrame(doc.getPage(idx))
            val labelW = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA.getStringWidth(label) / 1000.0 * 10.0
            val centeredX = ((frame.widthPt - labelW) / (2.0 * frame.widthPt)).coerceIn(0.05, 0.95)
            stampText(doc,listOf(idx),label,centeredX,0.96,10f,0.4f)
        }
    }

    /** One embedded logo reused across pages; alpha is applied independently of the source PNG alpha. */
    fun stampWatermarkLogo(doc: PDDocument, zeroBasedPages: List<Int>, image: PDImageXObject, style: PdfWatermarkStyle, checkCancellation: () -> Unit = {}) {
        require(zeroBasedPages.all {it in 0 until doc.numberOfPages}) { "INVALID_PAGE_INDEX" }
        val centers=OverlayStylePolicy.centers(style.centerX,style.centerY,style.tiled)
        zeroBasedPages.forEach {index ->
            checkCancellation()
            val page=doc.getPage(index);val frame=displayFrame(page)
            val (width,height)=OverlayStylePolicy.size(frame.widthPt,frame.heightPt,image.width.toDouble(),image.height.toDouble(),style.imageScale)
            PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use {stream ->
                stream.transform(displayMatrix(frame))
                val alpha=com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant=style.opacityPercent/100f
                }
                centers.forEach {(x,y) ->
                    stream.saveGraphicsState()
                    try {
                        stream.setGraphicsStateParameters(alpha)
                        stream.transform(Matrix.getRotateInstance(Math.toRadians(-style.rotationDegrees.toDouble()),(x*frame.widthPt).toFloat(),((1-y)*frame.heightPt).toFloat()))
                        stream.drawImage(image,(-width/2).toFloat(),(-height/2).toFloat(),width.toFloat(),height.toFloat())
                    } finally {stream.restoreGraphicsState()}
                }
            }
        }
    }

    private fun displayFrame(page:PDPage):PdfDisplayFrame {
        val crop=page.cropBox ?: page.mediaBox
        return PdfDisplayCoordinates.frame(crop.lowerLeftX.toDouble(),crop.lowerLeftY.toDouble(),
            crop.width.toDouble(),crop.height.toDouble(),page.rotation,page.userUnit.toDouble())
    }

    private fun displayMatrix(frame:PdfDisplayFrame):Matrix = frame.displayToPage.let { m ->
        val values=listOf(m.a,m.b,m.c,m.d,m.e,m.f)
        require(values.all { it.toFloat().isFinite() } && m.a*m.d-m.b*m.c!=0.0) { "INVALID_PDF_PAGE_GEOMETRY" }
        Matrix(m.a.toFloat(),m.b.toFloat(),m.c.toFloat(),m.d.toFloat(),m.e.toFloat(),m.f.toFloat())
    }

    /** Shared image placement, separate from Android text rasterization for real-PDF JVM fixtures. */
    internal fun stampWatermarkImage(doc:PDDocument,zeroBasedPages:List<Int>,image:PDImageXObject,widthPt:Float,heightPt:Float,style:PdfWatermarkStyle,checkCancellation:()->Unit={}) {
        require(widthPt.isFinite() && heightPt.isFinite() && widthPt>0 && heightPt>0) { "INVALID_STAMP" }
        require(zeroBasedPages.all { it in 0 until doc.numberOfPages }) { "INVALID_PAGE_INDEX" }
        val radians=Math.toRadians((-style.rotationDegrees).toDouble())
        val spots=if(style.tiled) listOf(0.18,0.5,0.82).flatMap { y -> listOf(0.18,0.5,0.82).map { x -> x to y } }
            else listOf(style.centerX to style.centerY)
        zeroBasedPages.forEach { index ->
            checkCancellation()
            val page=doc.getPage(index)
            val frame=displayFrame(page)
            val cap=(frame.widthPt*(if(style.tiled) 0.36 else 0.8)).toFloat()
            val scale=if(widthPt>cap) cap/widthPt else 1f
            val drawW=widthPt*scale; val drawH=heightPt*scale
            PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { cs ->
                cs.transform(displayMatrix(frame))
                spots.forEach { (fx,fy) ->
                    val x=(fx*frame.widthPt).toFloat(); val y=((1.0-fy)*frame.heightPt).toFloat()
                    require(x.isFinite() && y.isFinite()) { "INVALID_PDF_PAGE_GEOMETRY" }
                    cs.saveGraphicsState()
                    try {
                        cs.transform(Matrix.getRotateInstance(radians,x,y))
                        cs.drawImage(image,-drawW/2f,-drawH/2f,drawW,drawH)
                    } finally { cs.restoreGraphicsState() }
                }
            }
        }
    }

    /** Overlay text (annotations/overlay, watermark, page numbers share this primitive). */
    fun stampText(doc: PDDocument, zeroBasedPages: List<Int>, text: String, xNorm: Double, yNormTop: Double, fontSize: Float, gray: Float, colorHex:String="") {
        require(text.isNotBlank() && xNorm.isFinite() && yNormTop.isFinite() && xNorm in 0.0..1.0 && yNormTop in 0.0..1.0 && fontSize in 4f..200f && gray in 0f..1f) { "INVALID_STAMP" }
        require(zeroBasedPages.all { it in 0 until doc.pages.count }) { "INVALID_PAGE_INDEX" }
        val safeText = text.map { if (it in ' '..'~') it else ' ' }.joinToString("").ifBlank { "Note" }
        zeroBasedPages.forEach { index ->
            val page = doc.getPage(index)
            val frame=displayFrame(page)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.transform(displayMatrix(frame))
                cs.beginText()
                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, fontSize)
                if(colorHex.isBlank()) cs.setNonStrokingColor(gray,gray,gray)
                else {
                    val rgb = OverlayStylePolicy.rgb(colorHex)
                    cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2])
                }
                cs.newLineAtOffset((xNorm*frame.widthPt).toFloat(), ((1.0-yNormTop)*frame.heightPt).toFloat())
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
        val page = doc.getPage(zeroBasedPage)
        val frame = displayFrame(page)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.transform(displayMatrix(frame))
            cs.beginText(); cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, fontSize)
            cs.setRenderingMode(com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER)
            cs.newLineAtOffset((xNorm * frame.widthPt).toFloat(), ((1.0 - yNormTop) * frame.heightPt).toFloat())
            lines.forEachIndexed { i, line -> if (i > 0) cs.newLineAtOffset(0f, -fontSize * 1.2f); cs.showText(line) }
            cs.endText()
        }
    }

    /** Places an upright image in a normalized visible top-left rectangle. */
    fun stampImage(doc: PDDocument, zeroBasedPage: Int, imageFile: File, left: Double, top: Double, right: Double, bottom: Double, rotationDegrees: Float = 0f) {
        require(zeroBasedPage in 0 until doc.pages.count) { "INVALID_PAGE_INDEX" }
        val image=PDImageXObject.createFromFile(imageFile.path,doc)
        stampImageObject(doc,zeroBasedPage,image,left,top,right,bottom,rotationDegrees)
    }

    fun stampImages(doc:PDDocument,imageFile:File,placements:List<PdfImageOverlay>,checkCancellation:()->Unit={}) {
        require(placements.isNotEmpty() && placements.size<=1000) { "INVALID_SIGNATURE_PLACEMENT" }
        placements.forEach {
            require(it.pageIndex in 0 until doc.numberOfPages && it.rotationDegrees.isFinite() && it.rotationDegrees in -180f..180f) { "INVALID_SIGNATURE_PLACEMENT" }
            com.jumlo.app.core.Box(it.left,it.top,it.right,it.bottom).normalized()
        }
        checkCancellation()
        val image=PDImageXObject.createFromFile(imageFile.path,doc)
        placements.forEach {
            checkCancellation()
            stampImageObject(doc,it.pageIndex,image,it.left,it.top,it.right,it.bottom,it.rotationDegrees)
        }
    }

    /** Placement is codec-independent, so the actual PDF operators can be tested on the JVM. */
    internal fun stampImageObject(doc:PDDocument,zeroBasedPage:Int,image:PDImageXObject,left:Double,top:Double,right:Double,bottom:Double,rotationDegrees:Float=0f) {
        require(zeroBasedPage in 0 until doc.pages.count) { "INVALID_PAGE_INDEX" }
        require(rotationDegrees.isFinite() && rotationDegrees in -180f..180f) { "INVALID_ROTATION" }
        com.jumlo.app.core.Box(left,top,right,bottom).normalized()
        val page=doc.getPage(zeroBasedPage)
        val frame=displayFrame(page)
        val placement=PdfImagePlacementPolicy.fit((left*frame.widthPt).toFloat(),((1.0-bottom)*frame.heightPt).toFloat(),
            ((right-left)*frame.widthPt).toFloat(),((bottom-top)*frame.heightPt).toFloat(),image.width,image.height)
        PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { cs ->
            cs.saveGraphicsState()
            try {
                cs.transform(displayMatrix(frame))
                val centerX=placement.x+placement.width/2;val centerY=placement.y+placement.height/2
                cs.transform(Matrix.getRotateInstance(Math.toRadians(-rotationDegrees.toDouble()),centerX,centerY))
                cs.drawImage(image,-placement.width/2,-placement.height/2,placement.width,placement.height)
            } finally { cs.restoreGraphicsState() }
        }
    }

    /** Adds a Unicode note anchored in the visible physical page frame. */
    fun addTextAnnotation(doc: PDDocument, zeroBasedPage: Int, contents: String, xNorm: Double, yNormTop: Double, colorHex:String="") {
        require(contents.isNotBlank() && zeroBasedPage in 0 until doc.pages.count && xNorm.isFinite() && yNormTop.isFinite()
            && xNorm in 0.0..1.0 && yNormTop in 0.0..1.0) { "INVALID_ANNOTATION" }
        val page=doc.getPage(zeroBasedPage)
        val frame=displayFrame(page)
        // Preserve the ordinary-page lower-left note anchor and 20pt size while
        // mapping that physical icon rectangle through crop, rotation and UserUnit.
        val rect=PdfDisplayCoordinates.pageRectangle(frame,xNorm*frame.widthPt,(1.0-yNormTop)*frame.heightPt,20.0,20.0)
        val annotation=com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText()
        annotation.rectangle=PDRectangle(rect.x,rect.y,rect.width,rect.height)
        annotation.contents=contents
        if(colorHex.isNotBlank()) annotation.color=com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(OverlayStylePolicy.rgb(colorHex),com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE)
        annotation.setPage(page)
        annotation.setOpen(true)
        page.annotations=page.annotations.toMutableList().also { it.add(annotation) }
    }

    fun embeddedImageSummary(doc: PDDocument): Pair<Long, Long> {
        var bytes = 0L
        var pixels = 0L
        val seen = java.util.IdentityHashMap<com.tom_roush.pdfbox.cos.COSBase, Boolean>()
        fun visit(resources: com.tom_roush.pdfbox.pdmodel.PDResources?) {
            resources ?: return
            resources.xObjectNames?.forEach { name ->
                val stream = runCatching { resources.getXObject(name) }.getOrNull() ?: return@forEach
                if (seen.put(stream.cosObject, true) != null) return@forEach
                if (stream is PDImageXObject) {
                    val length = stream.cosObject.getInt(com.tom_roush.pdfbox.cos.COSName.LENGTH)
                    if (length > 0) bytes += length.toLong()
                    pixels += stream.width.toLong() * stream.height.toLong()
                }
            }
        }
        doc.pages.forEach { page -> visit(page.resources) }
        return bytes to pixels
    }

    fun scrubMetadata(doc: PDDocument, keepTitle: Boolean = true): List<String> {
        val removed = mutableListOf<String>()
        val cos = doc.documentInformation.cosObject
        val contentKeys = setOf("title", "subject", "keywords")
        val keys = cos.keySet().toList()
        keys.forEach { key ->
            val name = key.name
            if (name.equals("Trapped", ignoreCase = true)) return@forEach
            val lower = name.lowercase(java.util.Locale.ROOT)
            if (keepTitle && lower in contentKeys) return@forEach
            removed += if (lower in contentKeys) "content:$name" else name
        }
        keys.forEach { key ->
            if (!(keepTitle && key.name.lowercase(java.util.Locale.ROOT) in contentKeys)) cos.removeItem(key)
        }
        val catalog = doc.documentCatalog
        val metadataStream = catalog.getCOSObject().getDictionaryObject(com.tom_roush.pdfbox.cos.COSName.METADATA)
        if (metadataStream != null) {
            catalog.getCOSObject().removeItem(com.tom_roush.pdfbox.cos.COSName.METADATA)
            removed += "xmp"
        }
        return removed.distinct().sorted()
    }

    fun split(source: File, groups: List<PageGroup>, destinations: List<File>, checkCancellation: () -> Unit = {}): List<File> {
        val pageCount = PDDocument.load(source, "", MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
        require(groups.isNotEmpty() && groups.size == destinations.size) { "INVALID_SPLIT_GROUPS" }
        return groups.mapIndexed { index, group ->
            checkCancellation()
            require(group.first >= 0 && group.count > 0 && group.first.toLong() + group.count <= pageCount) { "INVALID_SPLIT_GROUP" }
            assemble(source, destinations[index], (group.first until group.first + group.count).map { it to 0 }, checkCancellation)
            destinations[index]
        }
    }

    fun extract(source: File, zeroBasedPages: List<Int>, out: File, checkCancellation: () -> Unit = {}): File {
        assemble(source, out, zeroBasedPages.map { it to 0 }, checkCancellation)
        return out
    }

    fun organize(source: File, ops: List<OrganizeOp>, out: File, checkCancellation: () -> Unit = {}) {
        require(ops.isNotEmpty()) { "INVALID_PAGE_INDEX" }
        assemble(source, out, ops.map { it.sourceIndex to it.rotationDegrees }, checkCancellation)
    }


    fun insert(base: File, other: PdfInput, atZeroIndex: Int, out: File) =
        open(base, null).use { insert(it, other, atZeroIndex, out) }


    private fun assemble(source: File, out: File, pages: List<Pair<Int, Int>>, checkCancellation: () -> Unit = {}) {
        require(pages.isNotEmpty()) { "NO_PAGES" }
        val tempOut = File.createTempFile("assemble-", ".pdf", out.parentFile ?: source.parentFile)
        try {
            PDDocument.load(source, "", MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val pageCount = doc.numberOfPages
                require(pages.all { it.first in 0 until pageCount }) { "INVALID_PAGE_INDEX" }
                val repeated = pages.groupBy { it.first }.filterValues { it.size > 1 }.keys
                val donor = if (repeated.isEmpty()) null else
                    PDDocument.load(source, "", MemoryUsageSetting.setupTempFileOnly())
                try {
                    val used = mutableSetOf<Int>()
                    val assembled = pages.map { (index, degrees) ->
                        checkCancellation()
                        require(degrees % 90 == 0) { "INVALID_ROTATION" }
                        val page = if (used.add(index)) doc.getPage(index) else {
                            val copy = requireNotNull(donor).let { doc.importPage(it.getPage(index)) }
                            detachAnnotations(copy)
                            copy
                        }
                        if (degrees != 0) page.rotation = ((page.rotation + degrees) % 360 + 360) % 360
                        page
                    }
                    val tree = doc.pages
                    (0 until tree.getCount()).map { tree.get(it) }.forEach { tree.remove(it) }
                    assembled.forEach { tree.add(it) }
                    pruneDeadFormFields(doc)
                    checkCancellation()
                    doc.save(tempOut)
                } finally { donor?.close() }
            }
            try {
                java.nio.file.Files.move(tempOut.toPath(), out.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(tempOut.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tempOut.exists()) tempOut.delete()
        }
    }


    private fun detachAnnotations(page: PDPage) {
        val annotations = page.annotations.toList()
        page.annotations.clear()
        for (annotation in annotations) {
            annotation.cosObject.removeItem(COSName.PARENT)
            annotation.cosObject.setItem(COSName.P, page.cosObject)
            page.annotations.add(annotation)
        }
    }

    private fun pruneDeadFormFields(doc: PDDocument) {
        val acroForm = doc.documentCatalog.acroForm ?: return
        val live = doc.pages.flatMap { it.annotations }.toSet()
        val kept = acroForm.fields.filter { field -> field.widgets.isEmpty() || field.widgets.any { it in live } }
        if (kept.size != acroForm.fields.size) acroForm.fields = kept
    }
}
