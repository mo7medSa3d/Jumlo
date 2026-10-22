package com.jumlo.app.ui.workspace

import org.junit.Assert.*
import org.junit.Test

class InteractivePageSelectionTest {
    @Test fun pageSelectionsRoundTripAsCompactRanges() {
        val selected=setOf(1,2,3,6,8,9)
        assertEquals("1-3,6,8-9",pageSelectionSpec(selected))
        assertEquals(selected,selectedPageNumbers(pageSelectionSpec(selected),10))
    }

    @Test fun allSelectionIsCaseInsensitive() {
        assertEquals(setOf(1,2,3),selectedPageNumbers(" ALL ",3))
    }

    @Test fun deselectingTheLastPageDoesNotSilentlySelectAllPages() {
        assertEquals(setOf(3),toggleSelectedPage(setOf(3),3))
        assertEquals(setOf(3,4),toggleSelectedPage(setOf(3),4))
        assertEquals(setOf(4),toggleSelectedPage(setOf(3,4),3))
    }

    @Test fun splitPreviewUsesTheSamePageBoundariesAsExport() {
        assertEquals(listOf(1..2,3..5,6..7),previewSplitGroups(7,false,true,"2,5",1))
    }

    @Test fun everyNPreviewIncludesTheLastPartialGroup() {
        assertEquals(listOf(1..3,4..6,7..7),previewSplitGroups(7,true,false,"all",3))
    }

    @Test fun customRangesStayGroupedAndAllKeepsOneFilePerPage() {
        assertEquals(listOf(1..3,5..5),previewSplitGroups(5,false,false,"1-3,5",1))
        assertEquals(listOf(1..1,2..2),previewSplitGroups(2,false,false,"all",1))
    }
}
