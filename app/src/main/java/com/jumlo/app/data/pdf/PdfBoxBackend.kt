package com.jumlo.app.data.pdf

import com.jumlo.app.core.PageTextComparison
import com.jumlo.app.core.PdfCompressionPolicy
import com.jumlo.app.core.PdfImagePlacementPolicy
import com.jumlo.app.core.PdfDisplayCoordinates
import com.jumlo.app.core.PdfDisplayFrame
import com.jumlo.app.core.PdfWatermarkStyle
import com.jumlo.app.core.OverlayStylePolicy
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
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
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

/**
 * The bitmap budget the compressor works to when the caller does not give one.
 * It mirrors the job budget: half the heap, capped.
 */
val DEFAULT_MAX_BITMAP_BYTES: Long = minOf(Runtime.getRuntime().maxMemory() / 2, 192L * 1024 * 1024)

data class OrganizeOp(val sourceIndex: Int, val rotationDegrees: Int = 0)
data class PageGroup(val first: Int, val count: Int)

/** Normalized rectangle with a TOP-LEFT origin; converted once at the PDF boundary. */
class RedactRegion(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init { require(left in 0.0..1.0 && top in 0.0..1.0 && right in 0.0..1.0 && bottom in 0.0..1.0 && left < right && top < bottom) { "INVALID_REDACT_REGION" } }
}

sealed interface FormEntry {
    data class Input(val value: String) : FormEntry
    data class Text(val value: String) : FormEntry
    data class Check(val on: Boolean) : FormEntry
    data class Radio(val index: Int) : FormEntry
}

/** Read-only discovery for typed form controls; names match AcroForm.getField(). */
data class PdfFormFieldInfo(
    val name: String,
    val kind: String,
    val value: String,
    val readOnly: Boolean,
    val choices: List<String> = emptyList(),
    val selectedIndex: Int = -1,
    val checked: Boolean = false,
    val widgets: List<PdfFormWidgetInfo> = emptyList()
)

/** Visible, upright CropBox coordinates; page is one based, widgetIndex preserves radio choice order. */
data class PdfFormWidgetInfo(val page: Int, val left: Double, val top: Double, val right: Double, val bottom: Double, val widgetIndex: Int)

data class PdfImageOverlay(val pageIndex: Int, val left: Double, val top: Double, val right: Double, val bottom: Double, val rotationDegrees: Float = 0f)

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
        require(groups.isNotEmpty() && groups.size == destinations.size) { "INVALID_SPLIT_GROUPS" }
        val temp = File.createTempFile("split-src-", ".pdf")
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
        val temp = File.createTempFile("extract-src-", ".pdf")
        try {
            doc.save(temp)
            assemble(temp, out, zeroBasedPages.map { it to 0 })
        } finally { temp.delete() }
        return out
    }

    fun organize(doc: PDDocument, ops: List<OrganizeOp>, out: File) {
        val pageCount = doc.numberOfPages
        require(ops.isNotEmpty() && ops.all { it.sourceIndex in 0 until pageCount }) { "INVALID_PAGE_INDEX" }
        val temp = File.createTempFile("organize-src-", ".pdf")
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

    private fun writeAssembled(ordered: List<Pair<PDPage, Int>>, out: File): File {
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { target ->
            val cloner = com.tom_roush.pdfbox.multipdf.PDFCloneUtility(target)
            ordered.forEach { (page, degrees) ->
                // importPage creates a distinct root dictionary on every invocation,
                // including repeated source pages. Materialize inherited resources;
                // the PDFBox port otherwise drops resources inherited from /Pages.
                val imported = target.importPage(page)
                require(imported.cosObject !== page.cosObject) { "PDF_PAGE_IMPORT_FAILED" }
                page.resources?.let { resources ->
                    imported.resources = com.tom_roush.pdfbox.pdmodel.PDResources(cloner.cloneForNewDocument(resources) as COSDictionary)
                }
                imported.rotation = ((page.rotation + degrees) % 360 + 360) % 360
            }
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

    fun formFields(doc: PDDocument): List<PdfFormFieldInfo> {
        // Discovery must not run PDFBox's default fixup, which can add fonts and
        // change form dictionaries simply by opening the configuration screen.
        val acro = doc.documentCatalog.getAcroForm(null) ?: return emptyList()
        val pages=doc.pages.toList()
        val widgetPages=java.util.IdentityHashMap<COSDictionary,Int>()
        val pageIndexes=java.util.IdentityHashMap<COSDictionary,Int>()
        pages.forEachIndexed { index,page ->
            pageIndexes[page.cosObject]=index
            page.annotations.forEach {widgetPages[it.cosObject]=index}
        }
        return acro.fieldTree.filterIsInstance<PDTerminalField>().map { field ->
            val type = when (field) {
                is PDRadioButton -> "radio"
                is PDCheckBox -> "checkbox"
                is PDPushButton -> "button"
                is PDTextField -> "text"
                else -> "other"
            }
            val value = try { field.valueAsString } catch (_: Exception) { "" }
            PdfFormFieldInfo(
                name = field.fullyQualifiedName,
                kind = type,
                value = value,
                readOnly = field.isReadOnly,
                choices = if (field is PDRadioButton) radioChoices(field) else emptyList(),
                selectedIndex = if (field is PDRadioButton) field.selectedIndex else -1,
                checked = field is PDCheckBox && field.isChecked,
                widgets = field.widgets.mapIndexedNotNull { widgetIndex,widget ->
                    val pageIndex=widgetPages[widget.cosObject] ?: widget.page?.let {pageIndexes[it.cosObject]} ?: return@mapIndexedNotNull null
                    val rect=widget.rectangle ?: return@mapIndexedNotNull null
                    if(listOf(rect.lowerLeftX,rect.lowerLeftY,rect.upperRightX,rect.upperRightY).any {!it.isFinite()} || rect.width<=0 || rect.height<=0) return@mapIndexedNotNull null
                    val frame=displayFrame(pages[pageIndex]);val matrix=frame.displayToPage
                    val determinant=matrix.a*matrix.d-matrix.b*matrix.c
                    fun normalized(x:Double,y:Double):Pair<Double,Double> {
                        val dx=x-matrix.e;val dy=y-matrix.f
                        return ((matrix.d*dx-matrix.c*dy)/determinant/frame.widthPt) to
                            (1.0-(-matrix.b*dx+matrix.a*dy)/determinant/frame.heightPt)
                    }
                    val corners=listOf(normalized(rect.lowerLeftX.toDouble(),rect.lowerLeftY.toDouble()),
                        normalized(rect.upperRightX.toDouble(),rect.lowerLeftY.toDouble()),
                        normalized(rect.lowerLeftX.toDouble(),rect.upperRightY.toDouble()),
                        normalized(rect.upperRightX.toDouble(),rect.upperRightY.toDouble()))
                    val left=corners.minOf {it.first}.coerceIn(0.0,1.0);val right=corners.maxOf {it.first}.coerceIn(0.0,1.0)
                    val top=corners.minOf {it.second}.coerceIn(0.0,1.0);val bottom=corners.maxOf {it.second}.coerceIn(0.0,1.0)
                    if(left>=right || top>=bottom) null else PdfFormWidgetInfo(pageIndex+1,left,top,right,bottom,widgetIndex)
                }
            )
        }
    }

    fun listFormFields(doc: PDDocument): List<String> = formFields(doc).map {
        listOf(it.name, it.kind, it.value).joinToString("|")
    }

    private fun radioChoices(field: PDRadioButton): List<String> {
        val exports = field.exportValues
        if (exports.isNotEmpty()) return exports
        // /Opt is optional. Most radio forms instead name their on-state in
        // each widget's normal appearance. Keep widget order for visual choice.
        return field.widgets.map { widget ->
            widget.appearance?.normalAppearance?.subDictionary?.keys
                ?.filter { it.name != "Off" }?.map { it.name }?.sorted()?.firstOrNull().orEmpty()
        }
    }

    private fun selectRadio(field: PDRadioButton, index: Int) {
        val choices = radioChoices(field)
        require(index in choices.indices && choices[index].isNotBlank()) { "FORM_FIELD_TYPE_MISMATCH" }
        if (field.exportValues.isNotEmpty()) field.setValue(index)
        else field.setValue(choices[index])
    }

    fun fillForm(doc: PDDocument, values: Map<String, FormEntry>) {
        val acro = doc.documentCatalog.acroForm ?: throw IllegalArgumentException("NO_ACROFORM")
        values.forEach { (name, entry) ->
            val field = acro.getField(name) ?: throw IllegalArgumentException("FORM_FIELD_NOT_FOUND")
            require(!field.isReadOnly) { "FORM_FIELD_READ_ONLY" }
            when (entry) {
                is FormEntry.Input -> when (field) {
                    is PDTextField -> field.setValue(entry.value)
                    is PDCheckBox -> {
                        require(entry.value.equals("true",true) || entry.value.equals("false",true)) { "FORM_FIELD_TYPE_MISMATCH" }
                        if(entry.value.equals("true",true)) field.check() else field.unCheck()
                    }
                    is PDRadioButton -> {
                        val index=entry.value.toIntOrNull() ?: throw IllegalArgumentException("FORM_FIELD_TYPE_MISMATCH")
                        selectRadio(field,index)
                    }
                    else -> throw IllegalArgumentException("FORM_FIELD_TYPE_MISMATCH")
                }
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
                    selectRadio(field,entry.index)
                }
            }
        }
    }

    /** Preflight every appearance and page before any widget or form is removed. */
    fun flattenForm(doc: PDDocument, out: File) {
        val acro = doc.documentCatalog.acroForm ?: throw IllegalArgumentException("NO_ACROFORM")
        require(!acro.hasXFA()) { "FORM_FLATTEN_UNSUPPORTED" }
        val fields=acro.fieldTree.toList()
        val values=fields.associate { it.fullyQualifiedName to it.valueAsString }
        try { acro.refreshAppearances() } catch (e: Exception) {
            throw IllegalArgumentException("FORM_APPEARANCE_UNAVAILABLE",e)
        }
        require(fields.all { values[it.fullyQualifiedName] == it.valueAsString }) { "FORM_VALUE_CHANGED" }
        data class PaintWidget(val page: PDPage,val widget: PDAnnotation,val appearance: PDFormXObject,val sx: Double,val sy: Double,val tx: Double,val ty: Double)
        val pages=doc.pages.toList()
        val plans=mutableListOf<PaintWidget>()
        fields.forEach { field -> field.widgets.forEach { widget ->
            val declaredPage=widget.page
            val page=pages.firstOrNull { candidate -> candidate.annotations.any { it.cosObject === widget.cosObject } }
                ?: pages.firstOrNull { it.cosObject === declaredPage?.cosObject }
                ?: throw IllegalArgumentException("FORM_WIDGET_PAGE_MISSING")
            val appearance=widget.getNormalAppearanceStream() ?: throw IllegalArgumentException("FORM_APPEARANCE_UNAVAILABLE")
            val rect=widget.rectangle ?: throw IllegalArgumentException("FORM_APPEARANCE_UNAVAILABLE")
            val bbox=appearance.bBox ?: throw IllegalArgumentException("FORM_APPEARANCE_UNAVAILABLE")
            val matrix=appearance.matrix
            // Compute the appearance's transformed bounds without Android PointF,
            // so the same code and fixtures also execute on the JVM backend.
            val corners=listOf(bbox.lowerLeftX to bbox.lowerLeftY,bbox.lowerLeftX to bbox.upperRightY,bbox.upperRightX to bbox.lowerLeftY,bbox.upperRightX to bbox.upperRightY)
            val xs=corners.map { (x,y) -> x*matrix.scaleX+y*matrix.shearX+matrix.translateX }
            val ys=corners.map { (x,y) -> x*matrix.shearY+y*matrix.scaleY+matrix.translateY }
            val minX=xs.minOrNull()!!; val minY=ys.minOrNull()!!
            val width=xs.maxOrNull()!!-minX; val height=ys.maxOrNull()!!-minY
            require(rect.width>0f && rect.height>0f && width>0f && height>0f &&
                xs.all { it.isFinite() } && ys.all { it.isFinite() } &&
                listOf(rect.lowerLeftX,rect.lowerLeftY,rect.upperRightX,rect.upperRightY).all { it.isFinite() }) { "FORM_APPEARANCE_UNAVAILABLE" }
            if(field.valueAsString.isNotBlank() && field.valueAsString != "Off") {
                require(appearance.contents.use { it.read() } >= 0) { "FORM_APPEARANCE_UNAVAILABLE" }
            }
            val sx=rect.width.toDouble()/width; val sy=rect.height.toDouble()/height
            plans+=PaintWidget(page,widget,appearance,sx,sy,rect.lowerLeftX-sx*minX,rect.lowerLeftY-sy*minY)
        } }
        // Paint all appearances first. A missing/invalid widget above fails with
        // the original AcroForm and page annotations still attached.
        plans.forEach { plan ->
            PDPageContentStream(doc,plan.page,PDPageContentStream.AppendMode.APPEND,true,true).use { cs ->
                cs.concatenate2CTM(plan.sx,0.0,0.0,plan.sy,plan.tx,plan.ty)
                cs.drawForm(plan.appearance)
            }
        }
        pages.forEach { page ->
            val widgets=plans.filter { it.page.cosObject === page.cosObject }.map { it.widget.cosObject }
            page.annotations=page.annotations.filter { ann -> widgets.none { it === ann.cosObject } }
        }
        doc.documentCatalog.acroForm=null
        doc.save(out)
        open(out,null).use { verified ->
            require(verified.numberOfPages==doc.numberOfPages && verified.documentCatalog.acroForm==null) { "PDF_VERIFY_FAILED" }
        }
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
        val text = watermarkText.trim().replace(Regex("\\s+"), " ")
        require(text.length <= 120) { "WATERMARK_TEXT_TOO_LONG" }
        if (text.isNotEmpty() && zeroBasedPages.isNotEmpty()) {
            checkCancellation()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                textSize = 84f
                typeface = android.graphics.Typeface.create(when(style.fontFamily) {
                    "serif" -> "serif"; "mono" -> "monospace"; else -> "sans-serif"
                }, android.graphics.Typeface.BOLD)
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
            stampWatermarkImage(doc,zeroBasedPages,image,widthPt,heightPt,style,checkCancellation)
        }
        if (addPageNumbers) zeroBasedPages.forEach { idx ->
            checkCancellation()
            stampText(doc,listOf(idx),"${idx + 1}",0.47,0.96,10f,0.4f)
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
                else cs.setNonStrokingColor(OverlayStylePolicy.rgb(colorHex))
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
        val page = doc.getPage(zeroBasedPage); val media = page.mediaBox
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.beginText(); cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, fontSize)
            cs.setRenderingMode(com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER)
            cs.newLineAtOffset((media.lowerLeftX + xNorm * media.width).toFloat(), (media.lowerLeftY + (1.0 - yNormTop) * media.height).toFloat())
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
            if (keepTitle && name.lowercase() in contentKeys) return@forEach
            removed += if (name.lowercase() in contentKeys) "content:$name" else name
        }
        keys.forEach { key ->
            if (!(keepTitle && key.name.lowercase() in contentKeys)) cos.removeItem(key)
        }
        val catalog = doc.documentCatalog
        val metadataStream = catalog.getCOSObject().getDictionaryObject(com.tom_roush.pdfbox.cos.COSName.METADATA)
        if (metadataStream != null) {
            catalog.getCOSObject().removeItem(com.tom_roush.pdfbox.cos.COSName.METADATA)
            removed += "xmp"
        }
        return removed.distinct().sorted()
    }

    fun addAnnotation(
        doc: PDDocument, zeroBasedPage: Int, kind: com.jumlo.app.core.PdfAnnotationKind,
        left: Double, top: Double, right: Double, bottom: Double, contents: String = "", colorHex: String = "", thicknessPt: Float = 2f
    ) {
        require(zeroBasedPage in 0 until doc.pages.count) { "INVALID_ANNOTATION" }
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()
            && left in 0.0..1.0 && right in 0.0..1.0 && top in 0.0..1.0 && bottom in 0.0..1.0
            && left < right && top < bottom) { "INVALID_ANNOTATION" }
        val page = doc.getPage(zeroBasedPage)
        val frame = displayFrame(page)
        require(thicknessPt.isFinite() && thicknessPt in .5f..20f) { "INVALID_ANNOTATION" }
        val rect = PdfDisplayCoordinates.normalizedRectangle(frame, left, top, right, bottom)
        val annotation: com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation = when (kind) {
            com.jumlo.app.core.PdfAnnotationKind.HIGHLIGHT ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup(
                    com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT)
            com.jumlo.app.core.PdfAnnotationKind.UNDERLINE ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup(
                    com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE)
            com.jumlo.app.core.PdfAnnotationKind.STRIKETHROUGH ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup(
                    com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT)
            com.jumlo.app.core.PdfAnnotationKind.SQUARE ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle(
                    com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle.SUB_TYPE_SQUARE)
            com.jumlo.app.core.PdfAnnotationKind.CIRCLE ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle(
                    com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle.SUB_TYPE_CIRCLE)
            com.jumlo.app.core.PdfAnnotationKind.TEXTBOX ->
                com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup(
                    com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup.SUB_TYPE_FREETEXT)
            else -> com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText()
        }
        annotation.rectangle = PDRectangle(rect.x, rect.y, rect.width, rect.height)
        if (annotation is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup) {
            val corners = PdfDisplayCoordinates.normalizedCorners(frame, left, top, right, bottom)
            annotation.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.QUADPOINTS,
                com.tom_roush.pdfbox.cos.COSArray().apply { corners.forEach { add(com.tom_roush.pdfbox.cos.COSFloat(it)) } })
        }
        if (kind == com.jumlo.app.core.PdfAnnotationKind.SQUARE || kind == com.jumlo.app.core.PdfAnnotationKind.CIRCLE) {
            annotation.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.C,
                rgbColor(0.10f, 0.30f, 0.70f))
        } else if (kind.isMarkup) {
            val tint = when (kind) {
                com.jumlo.app.core.PdfAnnotationKind.HIGHLIGHT -> Triple(1f, .92f, .23f)
                com.jumlo.app.core.PdfAnnotationKind.UNDERLINE -> Triple(.10f, .60f, .30f)
                else -> Triple(.85f, .20f, .20f)
            }
            annotation.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.C, rgbColor(tint.first, tint.second, tint.third))
        }
        if(colorHex.isNotBlank()) annotation.color=com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(OverlayStylePolicy.rgb(colorHex),com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE)
        annotation.cosObject.setItem(COSName.BS,com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary().apply {width=thicknessPt/page.userUnit})
        if (contents.isNotBlank()) annotation.contents = contents
        annotation.isPrinted=true
        annotation.setPage(page)
        if(kind.isMarkup) {
            // PDFBox's rounded highlight generator expands /Rect beyond the selected box.
            // An explicit appearance keeps the visible quad and editable bounds aligned.
            val appearance=com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream(doc).apply {
                bBox=PDRectangle(rect.width,rect.height)
                resources=com.tom_roush.pdfbox.pdmodel.PDResources()
            }
            val quad=PdfDisplayCoordinates.normalizedCorners(frame,left,top,right,bottom)
            fun x(index:Int)=quad[index*2]-rect.x
            fun y(index:Int)=quad[index*2+1]-rect.y
            PDPageContentStream(doc,appearance).use {stream ->
                stream.setNonStrokingColor(annotation.color);stream.setStrokingColor(annotation.color)
                if(kind==com.jumlo.app.core.PdfAnnotationKind.HIGHLIGHT) {
                    stream.setGraphicsStateParameters(com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant=.35f
                        blendMode=com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode.MULTIPLY
                    })
                    stream.moveTo(x(0),y(0));stream.lineTo(x(1),y(1));stream.lineTo(x(3),y(3));stream.lineTo(x(2),y(2))
                    stream.closePath();stream.fill()
                } else {
                    val thickness=thicknessPt/page.userUnit
                    val fraction=if(kind==com.jumlo.app.core.PdfAnnotationKind.UNDERLINE)
                        minOf(.5f,thickness/(2*kotlin.math.hypot(x(0)-x(2),y(0)-y(2)))) else .5f
                    stream.setLineWidth(thickness)
                    stream.moveTo(x(2)+(x(0)-x(2))*fraction,y(2)+(y(0)-y(2))*fraction)
                    stream.lineTo(x(3)+(x(1)-x(3))*fraction,y(3)+(y(1)-y(3))*fraction)
                    stream.stroke()
                }
            }
            annotation.appearance=com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary().apply {setNormalAppearance(appearance)}
        } else annotation.constructAppearances(doc)
        page.annotations = page.annotations.toMutableList().also { it.add(annotation) }
    }

    fun addInkAnnotation(doc: PDDocument, zeroBasedPage: Int, points: List<com.jumlo.app.core.PdfInkPoint>, contents: String = "") {
        addInkStrokes(doc,zeroBasedPage,listOf(points),contents)
    }

    fun addInkStrokes(doc: PDDocument, zeroBasedPage: Int, strokes: List<List<com.jumlo.app.core.PdfInkPoint>>, contents: String = "", colorHex: String = "", thicknessPt: Float = com.jumlo.app.core.PdfInkDrawing.STROKE_WIDTH_PT) {
        require(zeroBasedPage in 0 until doc.pages.count) { "INVALID_ANNOTATION" }
        require(thicknessPt.isFinite() && thicknessPt in .5f..20f) { "INVALID_ANNOTATION" }
        require(strokes.isNotEmpty() && strokes.size<=com.jumlo.app.core.PdfInkDrawing.MAX_STROKES) { "INVALID_ANNOTATION" }
        var total=0L
        strokes.forEach {points ->
            require(points.size in 2..com.jumlo.app.core.PdfInkDrawing.MAX_POINTS_PER_STROKE &&
                points.all {it.x.isFinite() && it.y.isFinite() && it.x in 0.0..1.0 && it.y in 0.0..1.0}) { "INVALID_ANNOTATION" }
            total+=points.size
            require(total<=com.jumlo.app.core.PdfInkDrawing.MAX_TOTAL_POINTS) { "INVALID_ANNOTATION" }
        }
        val page = doc.getPage(zeroBasedPage)
        val frame = displayFrame(page)
        val mapped = strokes.map {points -> points.map { point ->
                val x = point.x * frame.widthPt
                val y = (1.0 - point.y) * frame.heightPt
                frame.displayToPage.map(x, y).also {(px,py) ->
                    require(px.toFloat().isFinite() && py.toFloat().isFinite()) { "INVALID_ANNOTATION" }
                }
            }
        }
        val inkList=com.tom_roush.pdfbox.cos.COSArray()
        mapped.forEach {points ->
            val stroke=com.tom_roush.pdfbox.cos.COSArray()
            points.forEach {(x,y) ->
                stroke.add(com.tom_roush.pdfbox.cos.COSFloat(x.toFloat()))
                stroke.add(com.tom_roush.pdfbox.cos.COSFloat(y.toFloat()))
            }
            inkList.add(stroke)
        }
        // TextMarkup only constructs highlight/underline appearances. Generic markup
        // dispatches Ink to PDFBox's actual stroke appearance generator.
        val annotation = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup().apply {
            cosObject.setName(COSName.SUBTYPE,com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup.SUB_TYPE_INK)
        }
        annotation.cosObject.setItem(com.tom_roush.pdfbox.cos.COSName.INKLIST,
            inkList)
        val xs = mapped.flatMap {points->points.map {it.first}}
        val ys = mapped.flatMap {points->points.map {it.second}}
        val minX = xs.min()
        val minY = ys.min()
        val strokeWidth=thicknessPt/page.userUnit
        require(strokeWidth.isFinite() && strokeWidth>0f) { "INVALID_ANNOTATION" }
        // Include the painted stroke on both axes, including perfectly straight paths.
        val padding=strokeWidth*2f
        val bounds=PDRectangle((minX-padding).toFloat(),(minY-padding).toFloat(),
            (xs.max()-minX+padding*2).toFloat(),(ys.max()-minY+padding*2).toFloat())
        require(listOf(bounds.lowerLeftX,bounds.lowerLeftY,bounds.upperRightX,bounds.upperRightY).all {it.isFinite()} &&
            bounds.width>0f && bounds.height>0f) { "INVALID_ANNOTATION" }
        annotation.rectangle=bounds
        annotation.color=if(colorHex.isBlank()) com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(floatArrayOf(0f),com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray.INSTANCE)
            else com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(OverlayStylePolicy.rgb(colorHex),com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE)
        annotation.borderStyle=com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary().apply {width=strokeWidth}
        annotation.isPrinted=true
        if (contents.isNotBlank()) annotation.contents = contents
        annotation.setPage(page)
        annotation.constructAppearances(doc)
        require(annotation.normalAppearanceStream!=null) { "INVALID_ANNOTATION" }
        page.annotations = page.annotations.toMutableList().also { it.add(annotation) }
    }


    fun split(source: File, groups: List<PageGroup>, destinations: List<File>): List<File> {
        val pageCount = PDDocument.load(source, "", MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
        require(groups.isNotEmpty() && groups.size == destinations.size) { "INVALID_SPLIT_GROUPS" }
        return groups.mapIndexed { index, group ->
            require(group.first >= 0 && group.count > 0 && group.first.toLong() + group.count <= pageCount) { "INVALID_SPLIT_GROUP" }
            assemble(source, destinations[index], (group.first until group.first + group.count).map { it to 0 })
            destinations[index]
        }
    }

    fun extract(source: File, zeroBasedPages: List<Int>, out: File): File {
        assemble(source, out, zeroBasedPages.map { it to 0 })
        return out
    }

    fun organize(source: File, ops: List<OrganizeOp>, out: File) {
        val pageCount = PDDocument.load(source, "", MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
        require(ops.isNotEmpty() && ops.all { it.sourceIndex in 0 until pageCount }) { "INVALID_PAGE_INDEX" }
        assemble(source, out, ops.map { it.sourceIndex to it.rotationDegrees })
    }


    fun insert(base: File, other: PdfInput, atZeroIndex: Int, out: File) =
        open(base, null).use { insert(it, other, atZeroIndex, out) }


    private fun rgbColor(r: Float, g: Float, b: Float): com.tom_roush.pdfbox.cos.COSArray =
        com.tom_roush.pdfbox.cos.COSArray().apply {
            add(com.tom_roush.pdfbox.cos.COSFloat(r))
            add(com.tom_roush.pdfbox.cos.COSFloat(g))
            add(com.tom_roush.pdfbox.cos.COSFloat(b))
        }

    private fun assemble(source: File, out: File, pages: List<Pair<Int, Int>>) {
        require(pages.isNotEmpty()) { "NO_PAGES" }
        source.copyTo(out, overwrite = true)
        PDDocument.load(out, "", MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val pageCount = doc.numberOfPages
            require(pages.all { it.first in 0 until pageCount }) { "INVALID_PAGE_INDEX" }
            val repeated = pages.groupBy { it.first }.filterValues { it.size > 1 }.keys
            val donor = if (repeated.isEmpty()) null else
                PDDocument.load(source, "", MemoryUsageSetting.setupTempFileOnly())
            try {
                val used = mutableSetOf<Int>()
                val assembled = pages.map { (index, degrees) ->
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
                doc.save(out)
            } finally { donor?.close() }
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
