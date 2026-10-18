package com.jumlo.app.core

/** The bundled PDF font has a bounded repertoire. Never replace or truncate OCR content in strict mode; sanitize safely for multi-page streaming layer. */
object OcrTextPolicy {
    fun requireRepresentable(text:String):List<String> {
        val lines=text.lines()
        require(lines.size<=200 && lines.all { it.length<=500 }) { "OCR_PDF_TEXT_BUDGET_EXCEEDED" }
        require(lines.all { line -> line.all { it in ' '..'~' } }) { "OCR_PDF_TEXT_UNSUPPORTED" }
        return lines
    }

    /** Safe line chunker for on-device searchable PDF layer that never blocks on long pages or non-Latin glyphs. */
    fun sanitizeForLayer(text:String):List<String> {
        return text.lines()
            .flatMap { line ->
                val ascii = line.map { if (it in ' '..'~') it else ' ' }.joinToString("").trim()
                if (ascii.isEmpty()) emptyList() else ascii.chunked(480)
            }
            .take(195)
    }
}
