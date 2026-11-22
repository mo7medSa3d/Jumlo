package com.jumlo.app.pdf

import com.jumlo.app.data.pdf.PdfLongImageComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLongImageComposerTest {
    @Test fun emptyPageListProducesNoChunks() {
        assertTrue(
            PdfLongImageComposer.partitionFullQualityChunks(
                dims = emptyList(),
                maxCanvasHeight = 65_500,
                maxCanvasBytes = 64L * 1024 * 1024
            ).isEmpty()
        )
    }

    @Test fun smallDocumentStaysInSinglePart() {
        val dims = List(3) { 1000 to 1400 }
        val chunks = PdfLongImageComposer.partitionFullQualityChunks(
            dims = dims,
            maxCanvasHeight = 65_500,
            maxCanvasBytes = 64L * 1024 * 1024
        )
        assertEquals(listOf(listOf(0, 1, 2)), chunks)
    }

    @Test fun tallDocumentSplitsOnFormatHeightLimitWithoutDroppingPages() {
        val dims = List(5) { 1000 to 20_000 }
        val chunks = PdfLongImageComposer.partitionFullQualityChunks(
            dims = dims,
            maxCanvasHeight = 45_000,
            maxCanvasBytes = 512L * 1024 * 1024,
            gap = 12
        )
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), chunks)
    }

    @Test fun heavyDocumentSplitsOnCanvasByteBudget() {
        // Each 2000x2000 page is 16 MiB ARGB; 40 MiB budget fits 2 pages per chunk.
        val dims = List(5) { 2000 to 2000 }
        val chunks = PdfLongImageComposer.partitionFullQualityChunks(
            dims = dims,
            maxCanvasHeight = 131_000,
            maxCanvasBytes = 40L * 1024 * 1024,
            gap = 0
        )
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), chunks)
    }

    @Test fun singleOversizedPageGetsItsOwnChunkWithoutBlockingNeighbors() {
        val dims = listOf(1000 to 1000, 1000 to 80_000, 1000 to 1000)
        val chunks = PdfLongImageComposer.partitionFullQualityChunks(
            dims = dims,
            maxCanvasHeight = 65_500,
            maxCanvasBytes = 512L * 1024 * 1024
        )
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), chunks)
    }
}
