package com.jumlo.app.printing

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable data class PrintPage(val sourcePage: Int, val rotationDegrees: Int = 0)
@Serializable data class PrintSource(val path: String, val name: String, val mimeType: String, val pages: List<PrintPage> = emptyList())
@Serializable enum class PrintPaper { A4, A5, LETTER, LEGAL, SOURCE, CUSTOM }
@Serializable enum class PrintOrientation { PORTRAIT, LANDSCAPE }
@Serializable enum class PrintScale { FIT, FILL, ACTUAL }
@Serializable enum class PrintDuplex { NONE, LONG_EDGE, SHORT_EDGE }
@Serializable data class PrintMargins(val left: Double = 0.0, val top: Double = 0.0, val right: Double = 0.0, val bottom: Double = 0.0) {
    fun atLeast(other: PrintMargins) = PrintMargins(max(left,other.left),max(top,other.top),max(right,other.right),max(bottom,other.bottom))
    fun validate() = require(listOf(left,top,right,bottom).all { it.isFinite() && it >= 0.0 }) { "PRINT_MARGINS" }
}
@Serializable data class PrintOptions(
    val paper: PrintPaper = PrintPaper.A4,
    val orientation: PrintOrientation = PrintOrientation.PORTRAIT,
    val scale: PrintScale = PrintScale.FIT,
    val scalePercent: Int = 100,
    val margins: PrintMargins = PrintMargins(18.0,18.0,18.0,18.0),
    val grayscale: Boolean = false,
    val duplex: PrintDuplex = PrintDuplex.NONE,
    val range: String = "",
    val customWidthMm: Double = 210.0,
    val customHeightMm: Double = 297.0,
    // Images without trustworthy physical metadata have no natural paper size. This explicit DPI
    // controls actual-size/source-size; downsampling for decoding never changes physical intent.
    val imageDpi: Int = 300,
    // Page management for the print preview. Both are expressed against the source order
    // (1-based) so they survive reordering. Empty means "unchanged".
    val pageOrder: List<Int> = emptyList(),
    val pageRotations: Map<Int, Int> = emptyMap()
) {
    fun validate() {
        require(scalePercent in 10..400 && imageDpi in 36..1200) { "PRINT_SCALE" }
        require(customWidthMm.isFinite() && customHeightMm.isFinite() && customWidthMm in 25.0..1000.0 && customHeightMm in 25.0..1000.0) { "PRINT_PAPER" }
        require(range.length<=2000) { "PRINT_RANGE" }
        require(pageOrder.all { it > 0 }) { "PRINT_RANGE" }
        require(pageRotations.values.all { it % 90 == 0 }) { "PRINT_ROTATION" }
        margins.validate()
    }
    /** Presets and reopened documents keep paper/scale choices, never a stale page order. */
    fun reusable() = copy(range = "", pageOrder = emptyList(), pageRotations = emptyMap())
    fun paperSize(source: PrintSize): PrintSize {
        validate()
        val size = when(paper) {
            PrintPaper.A4 -> PrintSize.mm(210.0,297.0)
            PrintPaper.A5 -> PrintSize.mm(148.0,210.0)
            PrintPaper.LETTER -> PrintSize(612.0,792.0)
            PrintPaper.LEGAL -> PrintSize(612.0,1008.0)
            PrintPaper.SOURCE -> source
            PrintPaper.CUSTOM -> PrintSize.mm(customWidthMm,customHeightMm)
        }
        return if(orientation==PrintOrientation.LANDSCAPE) PrintSize(max(size.width,size.height),min(size.width,size.height))
            else PrintSize(min(size.width,size.height),max(size.width,size.height))
    }
}
@Serializable data class PrintPreset(val name:String,val options:PrintOptions) {
    companion object {
        fun create(name:String,options:PrintOptions):PrintPreset {
            require(name.trim().length in 1..60) { "PRINT_PRESET_NAME" }
            options.validate()
            return PrintPreset(name.trim(),options.reusable())
        }
    }
}
@Serializable enum class PrintJobState { CREATED, QUEUED, STARTED, BLOCKED, COMPLETED, FAILED, CANCELED, UNKNOWN }
/** Office margin presets. Values are PDF points (1/72 inch). */
enum class PrintMarginPreset(val margins: PrintMargins, val label: Int) {
    NORMAL(PrintMargins(18.0, 18.0, 18.0, 18.0), com.jumlo.app.R.string.print_margin_normal),
    NARROW(PrintMargins(10.0, 10.0, 10.0, 10.0), com.jumlo.app.R.string.print_margin_narrow),
    WIDE(PrintMargins(28.0, 28.0, 28.0, 28.0), com.jumlo.app.R.string.print_margin_wide)
}
@Serializable data class PrintHistoryEntry(val id:String,val source:PrintSource,val options:PrintOptions,val state:PrintJobState,val createdAt:Long)
@Serializable data class PrintRecent(val source:PrintSource,val favorite:Boolean=false,val openedAt:Long=0)
@Serializable data class PrintSavedData(val version:Int=1,val sessionSource:PrintSource?=null,val lastOptions:PrintOptions=PrintOptions(),val presets:List<PrintPreset> = emptyList(),val recent:List<PrintRecent> = emptyList(),val history:List<PrintHistoryEntry> = emptyList())
object PrintHistoryPolicy {
    fun terminal(state:PrintJobState)=state in setOf(PrintJobState.COMPLETED,PrintJobState.FAILED,PrintJobState.CANCELED)
    fun missing(state:PrintJobState)=if(terminal(state)) state else PrintJobState.UNKNOWN
    fun canCancel(state:PrintJobState)=state in setOf(PrintJobState.CREATED,PrintJobState.QUEUED,PrintJobState.STARTED,PrintJobState.BLOCKED)
}
