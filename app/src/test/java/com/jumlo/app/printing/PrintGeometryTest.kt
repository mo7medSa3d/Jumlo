package com.jumlo.app.printing

import org.junit.Assert.*
import org.junit.Test

class PrintGeometryTest {
    @Test fun fitUsesPrintableAreaAndKeepsAspect() {
        val layout = PrintGeometry.place(PrintSize(200.0, 100.0), PrintSize(600.0, 800.0), PrintMargins(50.0, 40.0, 30.0, 60.0), PrintScale.FIT, 100)
        assertEquals(2.6, layout.scale, 0.00001)
        assertEquals(50.0, layout.x, 0.00001)
        assertEquals(280.0, layout.y, 0.00001)
    }
    @Test fun fillAndActualSizeHaveExplicitClipping() {
        val fill = PrintGeometry.place(PrintSize(200.0, 100.0), PrintSize(600.0, 800.0), PrintMargins(), PrintScale.FILL, 100)
        assertEquals(8.0, fill.scale, 0.00001)
        assertEquals(-500.0, fill.x, 0.00001)
        val actual = PrintGeometry.place(PrintSize(200.0, 100.0), PrintSize(600.0, 800.0), PrintMargins(), PrintScale.ACTUAL, 125)
        assertEquals(1.25, actual.scale, 0.00001)
    }
    @Test fun printerMinimumMarginsAreCombinedByPhysicalEdge() {
        val result = PrintMargins(2.0, 30.0, 40.0, 2.0).atLeast(PrintMargins(10.0, 10.0, 10.0, 10.0))
        assertEquals(PrintMargins(10.0, 30.0, 40.0, 10.0), result)
    }
    @Test fun cropRotationAndUserUnitProduceCorrectCorners() {
        val crop=PrintCrop(20.0, 30.0, 200.0, 100.0, 2.0)
        val expected = listOf(PrintSize(400.0,200.0),PrintSize(200.0,400.0),PrintSize(400.0,200.0),PrintSize(200.0,400.0))
        listOf(0,90,180,270).forEachIndexed { i, rotation ->
            val matrix=PrintGeometry.normalize(crop,rotation)
            val corners=listOf(20.0 to 30.0,220.0 to 30.0,20.0 to 130.0,220.0 to 130.0).map { matrix.map(it.first,it.second) }
            assertEquals(0.0,corners.minOf {it.first},.0001)
            assertEquals(0.0,corners.minOf {it.second},.0001)
            assertEquals(expected[i].width,corners.maxOf {it.first},.0001)
            assertEquals(expected[i].height,corners.maxOf {it.second},.0001)
        }
        // Clockwise 90: lower-left becomes upper-left in PDF's y-up coordinates.
        assertEquals(0.0 to 400.0,PrintGeometry.normalize(crop,90).map(20.0,30.0))
    }
    @Test fun rejectsInvalidMarginsAndUnboundedScale() {
        assertThrows(IllegalArgumentException::class.java) { PrintGeometry.place(PrintSize(100.0,100.0),PrintSize(100.0,100.0),PrintMargins(60.0,0.0,50.0,0.0),PrintScale.FIT,100) }
        assertThrows(IllegalArgumentException::class.java) { PrintOptions(scalePercent=0).validate() }
        assertThrows(IllegalArgumentException::class.java) { PrintOptions(customWidthMm=Double.NaN).validate() }
    }
    @Test fun rangesUseLogicalPositionsAfterEditingAndPreserveDuplicates() {
        val pages=listOf(PrintPage(5,90),PrintPage(2),PrintPage(5,180),PrintPage(1))
        assertEquals(listOf(PrintPage(5,90),PrintPage(5,180)),PrintRanges.select(pages,"1, 3"))
        assertEquals(listOf(0,1,2,4),PrintRanges.parse("1-3,2,5",5))
        assertEquals((0..4).toList(),PrintRanges.parse("",5))
        listOf("0","6","3-1","1,,2","-2","1-", "2147483648").forEach { value ->
            assertThrows(value,IllegalArgumentException::class.java) { PrintRanges.parse(value,5) }
        }
    }
    @Test fun rangesAcceptArabicAndPersianNumeralsAndSeparators() {
        assertEquals(listOf(0,1,2,4),PrintRanges.parse("١-٣، ٥",5))
        assertEquals(listOf(1,3),PrintRanges.parse("۲،۴",5))
        assertEquals((0..4).toList(),PrintRanges.parse("الكل",5))
        assertEquals((0..4).toList(),PrintRanges.parse("all",5))
    }
    @Test fun spoolerRangesAreClippedDeduplicatedAndReportedTruthfully() {
        assertEquals(listOf(0,1,3,4),PrintRanges.requested(listOf(0..1,1..1,3..Int.MAX_VALUE),5))
        assertEquals(listOf(0..1,3..4),PrintRanges.compact(listOf(0,1,3,4)))
        assertEquals(emptyList<Int>(),PrintRanges.requested(listOf(8..9),5))
    }
    @Test fun presetIsReusableWithoutStaleDocumentRange() {
        val preset=PrintPreset.create("  Letter draft  ",PrintOptions(paper=PrintPaper.LETTER,range="2-8"))
        assertEquals("Letter draft",preset.name)
        assertEquals("",preset.options.range)
        assertEquals(PrintPaper.LETTER,preset.options.paper)
        assertThrows(IllegalArgumentException::class.java) { PrintPreset.create("  ",PrintOptions()) }
    }
    @Test fun sourceMappingRejectsInvalidReferencesAndKeepsRotation() {
        assertEquals(listOf(PrintPage(1),PrintPage(2)),PrintRanges.sourcePages(emptyList(),2))
        assertEquals(listOf(PrintPage(2,270)),PrintRanges.sourcePages(listOf(PrintPage(2,-90)),2))
        assertThrows(IllegalArgumentException::class.java) { PrintRanges.sourcePages(listOf(PrintPage(3)),2) }
        assertThrows(IllegalArgumentException::class.java) { PrintRanges.sourcePages(listOf(PrintPage(1,45)),2) }
    }
    @Test fun historyDoesNotTreatMissingSpoolerRecordAsCompleted() {
        assertEquals(PrintJobState.UNKNOWN,PrintHistoryPolicy.missing(PrintJobState.STARTED))
        assertEquals(PrintJobState.COMPLETED,PrintHistoryPolicy.missing(PrintJobState.COMPLETED))
        assertFalse(PrintHistoryPolicy.canCancel(PrintJobState.UNKNOWN))
        assertTrue(PrintHistoryPolicy.canCancel(PrintJobState.BLOCKED))
    }
}
