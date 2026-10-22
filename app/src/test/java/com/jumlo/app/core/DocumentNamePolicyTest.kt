package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentNamePolicyTest {
    @Test fun `document labels preserve real type and support Arabic`() {
        assertEquals("تقرير.pdf", DocumentNamePolicy.rename("report.pdf", "تقرير"))
        assertEquals("New.PDF", DocumentNamePolicy.rename("report.pdf", "New.PDF"))
        assertEquals("notes.txt.pdf", DocumentNamePolicy.rename("report.pdf", "notes.txt"))
    }
    @Test(expected=IllegalArgumentException::class) fun `paths cannot become document labels`() {
        DocumentNamePolicy.rename("report.pdf", "../secret.pdf")
    }
    @Test(expected=IllegalArgumentException::class) fun `empty label cannot be persisted`() {
        DocumentNamePolicy.rename("report.pdf", "   ")
    }
}
