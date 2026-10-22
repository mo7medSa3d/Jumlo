package com.jumlo.app.model

import com.jumlo.app.data.processing.PdfAnnotateSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceInkCompatibilityTest {
    private val json=Json {ignoreUnknownKeys=true}
    private val first="[0.1,0.1,0.2,0.2]"
    private val second="[0.7,0.8,0.8,0.9]"

    @Test fun savedDrawingPreservesSeparateStrokesInExecutionSettings() {
        val draft=json.decodeFromString<WorkspaceDraft>("""{"toolId":"pdf_annotate","text2":"ink","inkStrokes":[$first,$second]}""")
        val restored=json.decodeFromString<WorkspaceDraft>(json.encodeToString(draft))
        val settings=json.parseToJsonElement(restored.executionSettings()).jsonObject
        assertEquals("both strokes must survive saving the workspace",2,settings["inkStrokes"]?.jsonArray?.size ?: 0)
        assertEquals(json.parseToJsonElement(first),settings["inkStrokes"]!!.jsonArray[0])
        assertEquals(json.parseToJsonElement(second),settings["inkStrokes"]!!.jsonArray[1])
    }

    @Test fun legacyWorkspaceAndQueuedSettingsStillDecode() {
        val draft=json.decodeFromString<WorkspaceDraft>("""{"toolId":"pdf_annotate","text2":"ink","inkPoints":$first}""")
        val settings=json.decodeFromString<PdfAnnotateSettings>(draft.executionSettings())
        assertEquals(listOf(.1f,.1f,.2f,.2f),settings.inkPoints)
        assertTrue(settings.inkStrokes.isEmpty())
        val queued=json.decodeFromString<PdfAnnotateSettings>("""{"kind":"ink","inkPoints":$first}""")
        assertEquals(settings.inkPoints,queued.inkPoints)
        assertTrue(queued.inkStrokes.isEmpty())
    }

    @Test fun newQueuedSettingsRoundTripWithSeparateStrokes() {
        val queued=Json.decodeFromString<PdfAnnotateSettings>("""{"kind":"ink","inkStrokes":[$first,$second]}""")
        val restored=Json.decodeFromString<PdfAnnotateSettings>(Json.encodeToString(queued))
        assertEquals(listOf(listOf(.1f,.1f,.2f,.2f),listOf(.7f,.8f,.8f,.9f)),restored.inkStrokes)
        assertTrue(restored.inkPoints.isEmpty())
    }

    @Test fun emptyDrawingCannotBeSubmitted() {
        val draft=WorkspaceDraft.forTool("pdf_annotate",listOf("pdf")).copy(text2="ink")
        assertTrue("an empty drawing must not publish an unchanged document",runCatching {draft.executionSettings()}.isFailure)
    }
}
