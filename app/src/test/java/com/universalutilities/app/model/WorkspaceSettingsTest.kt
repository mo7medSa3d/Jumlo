package com.universalutilities.app.model

import com.universalutilities.app.data.processing.ImageResizeSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WorkspaceSettingsTest {
    @Test(expected=IllegalArgumentException::class)
    fun percentageBeyondEngineLimitIsRejectedBeforeEnqueue() {
        WorkspaceDraft(toolId="img_02",percentage="500").executionSettings()
    }
    @Test fun percentageModeDoesNotRequireUnusedPixelFields() {
        val json=WorkspaceDraft(toolId="img_02",percentage="50",width="",height="").executionSettings()
        assertEquals(50,Json.decodeFromString<ImageResizeSettings>(json).percentage)
    }
    @Test(expected=IllegalArgumentException::class)
    fun conflictingResizeModesAreNotSilentlyIgnored() {
        WorkspaceDraft(toolId="img_02",percentage="50",longestEdge="1000").executionSettings()
    }
}
