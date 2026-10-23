package com.jumlo.app.core

/**
 * The annotations a page can carry.
 *
 * The markup kinds (highlight, underline, strikethrough) are real PDF markup
 * annotations, so a reader can select and comment on them. The shape and text kinds
 * are free-standing annotations. [TEXT] is the original flat text behaviour and is
 * kept so older documents keep producing the same result.
 */
enum class PdfAnnotationKind(val id: String) {
    HIGHLIGHT("highlight"),
    UNDERLINE("underline"),
    STRIKETHROUGH("strikethrough"),
    NOTE("note"),
    SQUARE("square"),
    CIRCLE("circle"),
    TEXTBOX("textbox"),
    INK("ink"),
    TEXT("text");

    /** Markup annotations cover a text run rather than a point. */
    val isMarkup: Boolean get() = this == HIGHLIGHT || this == UNDERLINE || this == STRIKETHROUGH

    /** Kinds that carry their own text. */
    val carriesText: Boolean get() = this == NOTE || this == TEXTBOX || this == TEXT

    /** Freehand strokes are drawn, not placed in a rectangle. */
    val isFreehand: Boolean get() = this == INK

    companion object {
        fun from(id: String?): PdfAnnotationKind =
            entries.firstOrNull { it.id == id?.trim()?.lowercase() } ?: TEXT
    }
}

/** A point of a freehand stroke, normalized to the page with a top-left origin. */
data class PdfInkPoint(val x: Double, val y: Double)
