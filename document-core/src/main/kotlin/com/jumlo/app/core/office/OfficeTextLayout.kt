package com.jumlo.app.core.office

data class OfficeParagraph(val text: String, val headingLevel: Int = 0)
data class OfficeTextPage(val paragraphs: List<OfficeParagraph>) {
    val isBlank: Boolean get() = paragraphs.all { it.text.isBlank() }

    companion object {
        fun fromPlainText(text: String): OfficeTextPage = OfficeTextPage(
            text.replace("\r\n", "\n").replace('\r', '\n').trim('\n')
                .split(Regex("\n[\\t ]*\n+")).map { OfficeParagraph(it) })
    }
}

/** Font evidence addresses the exact emitted text range, not guessed PDF reading-order offsets. */
data class OfficeTextSpan(val start: Int, val end: Int, val fontSizePt: Double, val bold: Boolean)

/**
 * Conservative heading recovery from PDF font evidence. The weighted median establishes body
 * size; short, larger lines become headings. Ordinary bold body text stays body text. PDFs do
 * not necessarily contain semantic headings, so complex columns/tables remain a stated limit.
 */
object OfficeTextLayout {
    fun fromExtracted(text: String, spans: List<OfficeTextSpan>): OfficeTextPage {
        require(text.length <= OfficePackageWriter.MAX_PAGE_TEXT) { "OFFICE_TEXT_BUDGET_EXCEEDED" }
        val valid = spans.filter { it.start >= 0 && it.end in (it.start + 1)..text.length && it.fontSizePt.isFinite() && it.fontSizePt > 0.0 }
            .sortedBy { it.start }
        if (valid.isEmpty()) return OfficeTextPage.fromPlainText(text)
        require(valid.zipWithNext().all { (a, b) -> a.end <= b.start }) { "OFFICE_VERIFY_FAILED" }
        val bodySize = median(valid.map { it.fontSizePt to (it.end - it.start).toLong() })
        val paragraphs = mutableListOf<OfficeParagraph>()
        val current = StringBuilder()
        var currentHeading = 0
        var offset = 0
        var spanIndex = 0
        fun flush() {
            if (current.isNotEmpty()) paragraphs += OfficeParagraph(current.toString(), currentHeading)
            current.setLength(0)
        }
        for (line in text.split('\n')) {
            val start = offset
            offset += line.length + 1
            if (line.isBlank()) { flush(); continue }
            while (spanIndex < valid.size && valid[spanIndex].end <= start) spanIndex++
            val overlapping = mutableListOf<OfficeTextSpan>()
            var next = spanIndex
            while (next < valid.size && valid[next].start < start + line.length) {
                overlapping += valid[next++]
            }
            val weighted = overlapping.map { it.fontSizePt to (minOf(it.end, start + line.length) - maxOf(it.start, start)).toLong() }
            val fontSize = if (weighted.isEmpty()) bodySize else median(weighted)
            val length = weighted.sumOf { it.second }
            val boldLength = overlapping.filter { it.bold }.sumOf { (minOf(it.end, start + line.length) - maxOf(it.start, start)).toLong() }
            val ratio = fontSize / bodySize
            val short = line.trim().length <= 160 && line.trim().split(Regex("\\s+")).size <= 20
            val heading = if (!short || ratio < 1.25 && !(ratio >= 1.1 && boldLength * 2 >= length && length > 0)) 0
                else if (ratio >= 1.75) 1 else if (ratio >= 1.35) 2 else 3
            if (current.isNotEmpty() && heading != currentHeading) flush()
            currentHeading = heading
            if (current.isNotEmpty()) current.append('\n')
            current.append(line.trimEnd('\r'))
        }
        flush()
        return OfficeTextPage(paragraphs)
    }

    private fun median(values: List<Pair<Double, Long>>): Double {
        val sorted = values.sortedBy { it.first }
        val half = (sorted.sumOf { it.second } + 1) / 2
        var count = 0L
        for ((size, weight) in sorted) {
            count += weight
            if (count >= half) return size
        }
        return sorted.last().first
    }
}
