package com.jumlo.app.ui.workspace

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.jumlo.app.data.processing.PdfPageEdit
import org.junit.Assert.*
import org.junit.Test

class PdfPageEditorSessionTest {
    private val original=listOf(PdfPageEdit("a",1),PdfPageEdit("b",2))

    @Test fun routeViewModelRetainsHistoryAndSelectionAcrossRecompositionAndConfiguration() {
        val store=ViewModelStore()
        val factory=ViewModelProvider.NewInstanceFactory()
        val before=ViewModelProvider(store,factory)[PdfPageEditorSession::class.java]
        val session=before.forDocument("document",original)
        session.history.value=session.history.value.rotate(setOf("b"))
        session.selection.value=listOf("b")

        // A recreated route composition obtains the same ViewModelStore after configuration change.
        val after=ViewModelProvider(store,factory)[PdfPageEditorSession::class.java]
        val retained=after.forDocument("document",original)
        assertSame(session,retained)
        assertEquals(90,retained.history.value.current[1].rotationDegrees)
        assertEquals(1,retained.history.value.past.size)
        assertEquals(listOf("b"),retained.selection.value)
        store.clear()
    }

    @Test fun processDeathRestoresCurrentPagesFromDraftWithoutPuttingHistoryInSavedState() {
        val old=PdfPageEditorSession().forDocument("document",original)
        old.history.value=old.history.value.duplicate(setOf("b")).rotate(setOf("a"))
        val durableDraftPages=old.history.value.current

        val restored=PdfPageEditorSession().forDocument("document",durableDraftPages)
        assertEquals(durableDraftPages,restored.history.value.current)
        assertTrue(restored.history.value.past.isEmpty())
        assertTrue(restored.history.value.future.isEmpty())
        assertTrue(restored.selection.value.isEmpty())
    }

    @Test fun changingDocumentsDropsThePreviousLargeSession() {
        val model=PdfPageEditorSession()
        val previous=model.forDocument("one",original)
        previous.history.value=previous.history.value.rotate(setOf("a"))
        previous.selection.value=listOf("a")
        val next=model.forDocument("two",listOf(PdfPageEdit("new",1)))
        assertNotSame(previous,next)
        assertEquals(listOf(PdfPageEdit("new",1)),next.history.value.current)
        assertTrue(next.history.value.past.isEmpty())
        assertTrue(next.selection.value.isEmpty())
    }
}
