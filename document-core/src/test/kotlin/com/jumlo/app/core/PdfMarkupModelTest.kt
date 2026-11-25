package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMarkupModelTest {

    @Test
    fun selectToolIsAvailableAndDistinctFromDrawingAndErasingTools() {
        assertTrue(PdfMarkupTool.entries.contains(PdfMarkupTool.SELECT))
        assertFalse(PdfMarkupTool.SELECT == PdfMarkupTool.NONE)
        assertFalse(PdfMarkupTool.SELECT == PdfMarkupTool.PEN)
        assertFalse(PdfMarkupTool.SELECT == PdfMarkupTool.ERASER)
    }

    @Test
    fun boundingBoxComputesNormalizedBoundsAcrossAllMarkupItemTypes() {
        val ink = PdfMarkupItem.InkStroke(
            id = "ink-1",
            pageIndex = 0,
            points = listOf(NormPoint(0.20f, 0.30f), NormPoint(0.50f, 0.60f)),
            colorArgb = 0xFF000000.toInt(),
            widthNorm = 0.01f
        )
        val inkBox = PdfMarkupPolicy.boundingBox(ink)
        val inkPad = 0.01f * 0.5f + 0.012f
        assertEquals(0.20f - inkPad, inkBox.x, 1e-4f)
        assertEquals(0.30f - inkPad, inkBox.y, 1e-4f)
        assertEquals((0.50f + inkPad) - (0.20f - inkPad), inkBox.width, 1e-4f)
        assertEquals((0.60f + inkPad) - (0.30f - inkPad), inkBox.height, 1e-4f)

        val emptyInkBox = PdfMarkupPolicy.boundingBox(ink.copy(points = emptyList()))
        assertEquals(PdfRect(0f, 0f, 0.04f, 0.04f), emptyInkBox)

        val smallCheck = PdfMarkupItem.CheckMark(
            id = "chk-1",
            pageIndex = 0,
            normX = 0.40f,
            normY = 0.50f,
            colorArgb = 0xFF16A34A.toInt(),
            sizeNorm = 0.01f
        )
        assertEquals(PdfRect(0.40f, 0.50f, 0.025f, 0.025f), PdfMarkupPolicy.boundingBox(smallCheck))

        val largeCheck = smallCheck.copy(sizeNorm = 0.30f)
        assertEquals(PdfRect(0.40f, 0.50f, 0.14f, 0.14f), PdfMarkupPolicy.boundingBox(largeCheck))

        val singleLine = PdfMarkupItem.TextBox(
            id = "txt-1",
            pageIndex = 0,
            normX = 0.10f,
            normY = 0.20f,
            text = "Hello",
            colorArgb = 0xFF111111.toInt(),
            fontSizePt = 14f
        )
        val multiLine = singleLine.copy(id = "txt-2", text = "Hello\nSecond line\nThird line")
        val singleBox = PdfMarkupPolicy.boundingBox(singleLine)
        val multiBox = PdfMarkupPolicy.boundingBox(multiLine)
        assertTrue(multiBox.height > singleBox.height)
        assertTrue(multiBox.width > singleBox.width)

        val stamp = PdfMarkupItem.SavedSignatureStamp(
            id = "sig-1",
            pageIndex = 0,
            signaturePath = "/private/sig.png",
            normX = 0.55f,
            normY = 0.75f,
            normW = 0.28f,
            normH = 0.10f
        )
        assertEquals(PdfRect(0.55f, 0.75f, 0.28f, 0.10f), PdfMarkupPolicy.boundingBox(stamp))
    }

    @Test
    fun hitTestItemMatchesPolylinesAndBoxItemsWithPadding() {
        val stroke = PdfMarkupItem.InkStroke(
            id = "ink-hit",
            pageIndex = 0,
            points = listOf(NormPoint(0.20f, 0.20f), NormPoint(0.60f, 0.20f)),
            colorArgb = 0xFF000000.toInt(),
            widthNorm = 0.006f
        )
        assertTrue(PdfMarkupPolicy.hitTestItem(stroke, 0.40f, 0.20f))
        assertTrue(PdfMarkupPolicy.hitTestItem(stroke, 0.40f, 0.22f))
        assertFalse(PdfMarkupPolicy.hitTestItem(stroke, 0.40f, 0.35f))
        assertFalse(PdfMarkupPolicy.hitTestItem(stroke.copy(points = emptyList()), 0.40f, 0.20f))

        val singlePoint = PdfMarkupItem.HighlightStroke(
            id = "hl-1",
            pageIndex = 0,
            points = listOf(NormPoint(0.50f, 0.50f)),
            colorArgb = 0xFFFFFF00.toInt(),
            widthNorm = 0.024f
        )
        assertTrue(PdfMarkupPolicy.hitTestItem(singlePoint, 0.52f, 0.50f))
        assertFalse(PdfMarkupPolicy.hitTestItem(singlePoint, 0.65f, 0.50f))

        val stamp = PdfMarkupItem.SavedSignatureStamp(
            id = "sig-hit",
            pageIndex = 0,
            signaturePath = "/private/sig.png",
            normX = 0.30f,
            normY = 0.40f,
            normW = 0.20f,
            normH = 0.10f
        )
        assertTrue(PdfMarkupPolicy.hitTestItem(stamp, 0.40f, 0.45f))
        assertTrue(PdfMarkupPolicy.hitTestItem(stamp, 0.285f, 0.45f, padNorm = 0.024f))
        assertFalse(PdfMarkupPolicy.hitTestItem(stamp, 0.10f, 0.45f, padNorm = 0.024f))
    }

    @Test
    fun hitTestEraserCoversFullMultiLineTextBoxAndRejectsOutsidePoints() {
        val multiLine = PdfMarkupItem.TextBox(
            id = "txt-multi",
            pageIndex = 0,
            normX = 0.15f,
            normY = 0.20f,
            text = "Line one\nLine two\nLine three\nLine four",
            colorArgb = 0xFF111111.toInt(),
            fontSizePt = 14f
        )
        val box = PdfMarkupPolicy.boundingBox(multiLine)
        // Expected 4-line height: 4 * 0.032 + 0.014 = 0.142
        assertEquals(0.142f, box.height, 1e-4f)

        // Eraser hits first line, middle line, and bottom line of the multi-line text box.
        assertTrue(PdfMarkupPolicy.hitTestEraser(multiLine, box.x + 0.02f, box.y + 0.01f, radiusNorm = 0.02f))
        assertTrue(PdfMarkupPolicy.hitTestEraser(multiLine, box.x + box.width * 0.5f, box.y + box.height * 0.5f, radiusNorm = 0.02f))
        assertTrue(PdfMarkupPolicy.hitTestEraser(multiLine, box.x + 0.02f, box.y + box.height - 0.005f, radiusNorm = 0.02f))

        // Eraser outside box + radius misses cleanly.
        assertFalse(PdfMarkupPolicy.hitTestEraser(multiLine, box.x + 0.02f, box.y + box.height + 0.05f, radiusNorm = 0.02f))
        assertFalse(PdfMarkupPolicy.hitTestEraser(multiLine, box.x + box.width + 0.05f, box.y + 0.02f, radiusNorm = 0.02f))
    }

    @Test
    fun toPdfPointHandlesPageRotationsAndCropBoxOffsets() {
        val (x0, y0) = PdfMarkupPolicy.toPdfPoint(10f, 20f, 200f, 400f, 0, 0.25f, 0.25f)
        assertEquals(60f, x0, 1e-3f)
        assertEquals(320f, y0, 1e-3f)

        val (x90, y90) = PdfMarkupPolicy.toPdfPoint(10f, 20f, 200f, 400f, 90, 0.25f, 0.75f)
        assertEquals(160f, x90, 1e-3f)
        assertEquals(120f, y90, 1e-3f)

        val (x180, y180) = PdfMarkupPolicy.toPdfPoint(0f, 0f, 200f, 400f, 180, 0.25f, 0.25f)
        assertEquals(150f, x180, 1e-3f)
        assertEquals(100f, y180, 1e-3f)

        val (x270, y270) = PdfMarkupPolicy.toPdfPoint(0f, 0f, 200f, 400f, 270, 0.25f, 0.25f)
        assertEquals(150f, x270, 1e-3f)
        assertEquals(300f, y270, 1e-3f)
    }
}
