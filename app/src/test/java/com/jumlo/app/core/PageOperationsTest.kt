package com.jumlo.app.core

import org.junit.Assert.*
import org.junit.Test

/** Phase 5 unit tests for page-operation logic: reorder, rotate, delete, extract,
 *  split, merge, undo/redo and range parsing. Pure logic; no Android runtime. */
class PageOperationsTest {

    private fun doc(vararg pages: Int, rotations: Map<Int, Int> = emptyMap(),
        selected: Set<Int> = emptySet()) =
        PdfPageEdit(pages.toList(), selected, rotations)

    @Test fun `move relocates a page to the requested position`() {
        val d = doc(1, 2, 3, 4).move(3, 1)
        assertEquals(listOf(3, 1, 2, 4), d.order)
    }

    @Test fun `move rejects out-of-range positions`() {
        val d = doc(1, 2, 3)
        try { d.move(1, 0); fail("expected INVALID_PAGE_INDEX") }
        catch (e: IllegalArgumentException) { assertEquals("INVALID_PAGE_INDEX", e.message) }
    }

    @Test fun `toggle adds and removes selection`() {
        val d = doc(1, 2, 3).toggle(2)
        assertEquals(setOf(2), d.selected)
        assertEquals(emptySet(), d.toggle(2).selected)
    }

    @Test fun `selectRange adds a contiguous range`() {
        val d = doc(1, 2, 3, 4, 5).selectRange(2, 4)
        assertEquals(setOf(2, 3, 4), d.selected)
    }

    @Test fun `selectAll invertSelection and selectNone`() {
        val d = doc(1, 2, 3).toggle(1)
        assertEquals(setOf(1, 2, 3), d.selectAll().selected)
        assertEquals(setOf(2, 3), d.selectAll().invertSelection().selected)
        assertEquals(emptySet(), d.selectAll().selectNone().selected)
    }

    @Test fun `deleteSelected removes pages and keeps the rest`() {
        val edited = doc(1, 2, 3, 4, rotations = mapOf(2 to 90), selected = setOf(2, 4)).deleteSelected()
        assertEquals(listOf(1, 3), edited.order)
        assertEquals(emptyMap<Int, Int>(), edited.rotations)
    }

    @Test fun `deleteSelected refuses to empty the document`() {
        val d = doc(1, 2, selected = setOf(1, 2))
        try { d.deleteSelected(); fail("expected AT_LEAST_ONE_PAGE_REQUIRED") }
        catch (e: IllegalArgumentException) { assertEquals("AT_LEAST_ONE_PAGE_REQUIRED", e.message) }
    }

    @Test fun `duplicate inserts a copy next to the page`() {
        val d = doc(1, 2, 3).duplicate(2)
        assertEquals(listOf(1, 2, 2, 3), d.order)
    }

    @Test fun `rotate accumulates modulo 360`() {
        var d = doc(1)
        repeat(4) { d = d.rotate(1, 90) }
        assertEquals(0, d.rotations[1])
        d = d.rotate(1, 270)
        assertEquals(270, d.rotations[1])
    }

    @Test fun `rotate rejects invalid degrees`() {
        val d = doc(1)
        try { d.rotate(1, 45); fail("expected INVALID_ROTATION") }
        catch (e: IllegalArgumentException) { assertEquals("INVALID_ROTATION", e.message) }
    }

    @Test fun `rotateSelected only touches selected pages`() {
        val d = doc(1, 2, 3, selected = setOf(1, 3)).rotateSelected(90)
        assertEquals(mapOf(1 to 90, 3 to 90), d.rotations)
    }

    @Test fun `rotateSelected with no selection is a no-op`() {
        val d = doc(1, 2).rotateSelected(90)
        assertEquals(emptyMap<Int, Int>(), d.rotations)
    }

    @Test fun `extractSelected keeps order and rotation`() {
        val d = doc(1, 2, 3, 4, rotations = mapOf(2 to 90), selected = setOf(2, 4)).extractSelected()
        assertEquals(listOf(2, 4), d.order)
        assertEquals(mapOf(2 to 90), d.rotations)
    }

    @Test fun `splitEvery chunks the order`() {
        val parts = doc(1, 2, 3, 4, 5).splitEvery(2)
        assertEquals(3, parts.size)
        assertEquals(listOf(1, 2), parts[0].order)
        assertEquals(listOf(5), parts[2].order)
    }

    @Test fun `merge renumbers and carries rotation`() {
        val a = doc(1, 2, 3, rotations = mapOf(2 to 90))
        val b = doc(1, 2)
        val m = PageDocumentMerger.merge(listOf(a, b))
        assertEquals(listOf(1, 2, 3, 4, 5), m.order)
        assertEquals(90, m.rotations[2])
    }

    @Test fun `merge rejects empty document list`() {
        try { PageDocumentMerger.merge(emptyList()); fail("expected NO_DOCUMENTS") }
        catch (e: IllegalArgumentException) { assertEquals("NO_DOCUMENTS", e.message) }
    }

    @Test fun `history undo and redo move a cursor`() {
        val h = PageEditHistory(doc(1, 2, 3))
        assertFalse(h.canUndo)
        h.apply(doc(1, 2, 3, 4))
        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
        assertEquals(listOf(1, 2, 3), h.undo().order)
        assertTrue(h.canRedo)
        assertEquals(listOf(1, 2, 3, 4), h.redo().order)
    }

    @Test fun `history clears redo after a new edit`() {
        val h = PageEditHistory(doc(1, 2, 3))
        h.apply(doc(1, 2, 3, 4))
        h.undo()
        assertTrue(h.canRedo)
        h.apply(doc(2, 1, 3, 4))
        assertFalse(h.canRedo)
    }

    @Test fun `history refuses to undo past the start`() {
        val h = PageEditHistory(doc(1, 2))
        try { h.undo(); fail("expected CANNOT_UNDO") }
        catch (e: IllegalArgumentException) { assertEquals("CANNOT_UNDO", e.message) }
    }

    @Test fun `range parsing accepts typed ranges and clamps to count`() {
        assertEquals(listOf(1, 2, 3, 7, 10, 11, 12), PageSelection.parse("1-3, 7, 10-12", 12))
        assertEquals(listOf(1, 2, 3), PageSelection.parse("1-3", 3))
        assertEquals(listOf(1, 2, 3), PageSelection.parse("1-9", 3)) // clamped
    }

    @Test fun `range parsing rejects out-of-range and malformed input`() {
        try { PageSelection.parse("1-5", 3); fail("expected INVALID_PAGE_RANGE") }
        catch (e: IllegalArgumentException) { assertEquals("INVALID_PAGE_RANGE", e.message) }
        try { PageSelection.parse("abc", 5); fail("expected INVALID_PAGE_RANGE") }
        catch (e: IllegalArgumentException) { assertEquals("INVALID_PAGE_RANGE", e.message) }
    }

    @Test fun `strict page order rejects duplicates`() {
        try { PdfPageOrder.parse("1,2,2,3", 3); fail("expected DUPLICATE_PAGE_ORDER") }
        catch (e: IllegalArgumentException) { assertEquals("DUPLICATE_PAGE_ORDER", e.message) }
    }

    @Test fun `strict page order rejects out-of-range pages`() {
        try { PdfPageOrder.parse("1,9", 3); fail("expected INVALID_PAGE_ORDER") }
        catch (e: IllegalArgumentException) { assertEquals("INVALID_PAGE_ORDER", e.message) }
    }

    @Test fun `split plan produces zero-based start-length groups`() {
        val groups = PdfSplitPlan.splitAfter(6, "2, 4")
        assertEquals(listOf(0 to 2, 2 to 2, 4 to 2), groups)
    }
}
