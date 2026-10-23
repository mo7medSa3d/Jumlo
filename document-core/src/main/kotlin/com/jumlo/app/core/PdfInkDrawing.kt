package com.jumlo.app.core

/** Bounded normalized paths shared by saved drafts, touch capture and PDF export. */
object PdfInkDrawing {
    const val STROKE_WIDTH_PT=1f
    const val MAX_STROKES=256
    const val MAX_POINTS_PER_STROKE=5000
    const val MAX_TOTAL_POINTS=50000

    fun resolve(strokes:List<List<Float>>,legacyPoints:List<Float>):List<List<Float>> =
        strokes.ifEmpty {if(legacyPoints.isEmpty()) emptyList() else listOf(legacyPoints)}

    fun requireValid(strokes:List<List<Float>>) {
        require(strokes.isNotEmpty() && strokes.size<=MAX_STROKES) {"INVALID_ANNOTATION"}
        var total=0L
        strokes.forEach {stroke ->
            require(stroke.size in 4..MAX_POINTS_PER_STROKE*2 && stroke.size%2==0 &&
                stroke.all {it.isFinite() && it in 0f..1f}) {"INVALID_ANNOTATION"}
            total+=stroke.size/2
            require(total<=MAX_TOTAL_POINTS) {"INVALID_ANNOTATION"}
        }
    }

    fun append(strokes:List<List<Float>>,stroke:List<Float>):List<List<Float>> =
        (strokes+listOf(stroke.toList())).also(::requireValid)

    fun strokeCapacity(strokes:List<List<Float>>):Int {
        if(strokes.size>=MAX_STROKES) return 0
        val remaining=(MAX_TOTAL_POINTS-strokes.sumOf {it.size.toLong()/2}).coerceAtLeast(0)
        return minOf(MAX_POINTS_PER_STROKE.toLong(),remaining).toInt().takeIf {it>=2} ?: 0
    }
}
