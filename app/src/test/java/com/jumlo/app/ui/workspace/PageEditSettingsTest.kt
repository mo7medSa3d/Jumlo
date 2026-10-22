package com.jumlo.app.ui.workspace

import com.jumlo.app.data.processing.PdfOrganizeSettings
import com.jumlo.app.data.processing.PdfPageEdit
import com.jumlo.app.model.WorkspaceDraft
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PageEditSettingsTest {
    @Test fun oldSavedJobsDecodeWithLegacyFieldsAndNoPageEntries() {
        val legacy=Json.decodeFromString<PdfOrganizeSettings>("""{"order":"3,1","rotatePages":"1","rotateDegrees":-90}""")
        assertTrue(legacy.pageEdits.isEmpty())
        assertEquals("3,1",legacy.order)
        assertEquals(-90,legacy.rotateDegrees)
    }
    @Test fun executionSettingsPreserveDuplicatePageIdentityAndRelativeRotation() {
        val entries=listOf(PdfPageEdit("original",2,90),PdfPageEdit("copy",2,270))
        val draft=WorkspaceDraft(toolId="pdf_04",outputName="Organized.pdf",pageEdits=entries)
        val settings=Json.decodeFromString<PdfOrganizeSettings>(draft.executionSettings())
        assertEquals(entries,settings.pageEdits)
        assertEquals(draft,Json.decodeFromString<WorkspaceDraft>(Json.encodeToString(WorkspaceDraft.serializer(),draft)))
    }
    @Test fun authoritativePageEntriesDoNotDependOnOldRotationText() {
        val draft=WorkspaceDraft(toolId="pdf_04",num1="old invalid text",pageEdits=listOf(PdfPageEdit("a",1,180)))
        assertEquals(180,Json.decodeFromString<PdfOrganizeSettings>(draft.executionSettings()).pageEdits.single().rotationDegrees)
    }
}
