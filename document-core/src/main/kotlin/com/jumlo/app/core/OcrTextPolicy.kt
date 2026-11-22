package com.jumlo.app.core

import java.text.Normalizer

/**
 * Text policies for OCR outputs.
 * - Full UTF-8 `.txt` output uses [normalizeUtf8] so Arabic and multi-script text is never dropped.
 * - The bundled Helvetica PDF text layer has an ASCII-only repertoire; [requireRepresentable]
 *   guards strict stamping while [sanitizeForLayer] chunks safely for non-strict layers.
 */
object OcrTextPolicy {
    fun requireRepresentable(text: String): List<String> {
        val lines = text.lines()
        require(lines.size <= 200 && lines.all { it.length <= 500 }) { "OCR_PDF_TEXT_BUDGET_EXCEEDED" }
        require(lines.all { line -> line.all { it in ' '..'~' } }) { "OCR_PDF_TEXT_UNSUPPORTED" }
        return lines
    }

    /** Safe line chunker for on-device searchable PDF layer that never blocks on long pages or non-Latin glyphs. */
    fun sanitizeForLayer(text: String): List<String> {
        return text.lines()
            .flatMap { line ->
                val ascii = buildString(line.length) {
                    for (ch in line) append(if (ch in ' '..'~') ch else ' ')
                }.trim()
                if (ascii.isEmpty()) emptyList() else ascii.chunked(480)
            }
            .take(195)
    }

    /**
     * Normalizes UTF-8 OCR output via NFKC (decomposing Arabic Presentation Forms 0xFB50..0xFDFF
     * and 0xFE70..0xFEFF into canonical Arabic), strips stray control/bidi-override/isolate chars
     * (including U+2066..U+2069), and preserves clean logical UTF-8 paragraph breaks.
     */
    fun normalizeUtf8(text: String): String {
        if (text.isEmpty()) return ""
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val cleaned = buildString(nfkc.length) {
            for (ch in nfkc) {
                val c = ch.code
                when {
                    ch == '\r' -> {}
                    ch == '\n' -> append('\n')
                    ch == '\t' -> append(' ')
                    ch == '\uFEFF' || ch == '\uFFFD' || ch == '\u00AD' -> {}
                    c in 0x202A..0x202E || c in 0x2066..0x2069 || ch == '\u200E' || ch == '\u200F' -> {}
                    c < 0x20 || c == 0x7F -> {}
                    else -> append(ch)
                }
            }
        }
        val out = ArrayList<String>()
        var lastBlank = true
        for (rawLine in cleaned.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (!lastBlank) {
                    out += ""
                    lastBlank = true
                }
            } else {
                out += line
                lastBlank = false
            }
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.lastIndex)
        return out.joinToString("\n")
    }
}
