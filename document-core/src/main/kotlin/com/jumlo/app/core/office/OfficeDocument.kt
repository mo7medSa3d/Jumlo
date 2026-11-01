package com.jumlo.app.core.office

/** Office kind, detected from the package content types (extension is only a fallback). */
enum class OfficeKind { DOCX, XLSX, PPTX }

/** One styled run inside a paragraph. Runs keep bold/italic per word run. */
data class OfficeRun(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/**
 * A paragraph. Level: 0 body, 1 title, 2 heading 1, 3 heading 2, 4 heading 3.
 * Alignment: left, center, right, justify. listMarker is "• " or "3. " when the
 * paragraph belongs to a list; listIndent is the nesting depth (0 = flat).
 */
data class OfficeParagraph(
    val runs: List<OfficeRun>,
    val level: Int = 0,
    val align: String = "left",
    val listMarker: String? = null,
    val listIndent: Int = 0
) {
    val text: String get() = runs.joinToString("") { it.text }
    val isBlank: Boolean get() = text.isBlank()
}

data class OfficeCell(val text: String, val bold: Boolean = false, val colSpan: Int = 1)

/** Blocks in document order. Tables render with borders; images keep aspect. */
sealed interface OfficeBlock {
    data class ParagraphBlock(val paragraph: OfficeParagraph) : OfficeBlock
    data class TableBlock(val rows: List<List<OfficeCell>>, val headerRow: Boolean = false) : OfficeBlock
    data class ImageBlock(val bytes: ByteArray, val extension: String) : OfficeBlock
    /** A hard page break (docx page break, slide boundary). */
    data object PageBreak : OfficeBlock
    /** A new sheet: rendered as a heading on a fresh page. */
    data class Section(val title: String) : OfficeBlock
}

/**
 * A parsed Office document, ready to render. pageWidthPt/pageHeightPt come from
 * the document's own page/slide setup when present, otherwise A4. marginPt is
 * the document's left margin when present, otherwise 56pt.
 *
 * unsupported lists what was deliberately dropped, with stable tokens the
 * handler reports: HEADER_FOOTER, FOOTNOTE, CHART, UNSUPPORTED_IMAGE.
 * Layout fidelity is structural, not pixel-perfect: the renderer keeps text,
 * headings, lists, tables and pictures, but not headers, footers, comments,
 * or exact Word/PowerPoint pagination.
 */
data class OfficeDocument(
    val kind: OfficeKind,
    val pageWidthPt: Double,
    val pageHeightPt: Double,
    val marginPt: Double,
    val blocks: List<OfficeBlock>,
    val unsupported: List<String>
)
