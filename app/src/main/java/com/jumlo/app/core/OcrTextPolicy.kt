package com.jumlo.app.core

/** The bundled PDF font has a bounded repertoire. Never replace or truncate OCR content. */
object OcrTextPolicy {
    fun requireRepresentable(text:String):List<String> {
        val lines=text.lines()
        require(lines.size<=200 && lines.all { it.length<=500 }) { "OCR_PDF_TEXT_BUDGET_EXCEEDED" }
        require(lines.all { line -> line.all { it in ' '..'~' } }) { "OCR_PDF_TEXT_UNSUPPORTED" }
        return lines
    }
}
