package com.jumlo.app.data.processing

import android.graphics.Color
import com.jumlo.app.core.OutputFileChoice
import com.jumlo.app.core.OutputWorkflowPolicy
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.workspace.ToolInputKind
import com.jumlo.app.ui.workspace.ToolInputPolicy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundRemovalPolicyTest {
    @Test fun draftDefaultsPreserveTransparencyAndCoerceJpegWhenTransparent() {
        val defaultDraft = WorkspaceDraft.forTool("img_bg_remove", listOf("img-1"))
        assertEquals("PNG", defaultDraft.format)
        assertEquals("nobg_", defaultDraft.prefix)
        assertEquals("TRANSPARENT", defaultDraft.text1)

        val transparentJpegAttempt = defaultDraft.copy(format = "JPEG", text1 = "TRANSPARENT")
        val decoded = Json.decodeFromString<ImageBgRemoveSettings>(transparentJpegAttempt.executionSettings())
        assertEquals("TRANSPARENT", decoded.backgroundMode)
        assertEquals("PNG", decoded.output.format)

        val studioBlue = defaultDraft.copy(format = "JPEG", text1 = "BLUE", flag1 = true)
        val blueSettings = Json.decodeFromString<ImageBgRemoveSettings>(studioBlue.executionSettings())
        assertEquals("BLUE", blueSettings.backgroundMode)
        assertTrue(blueSettings.trimTransparent)
        assertEquals("JPEG", blueSettings.output.format)
    }

    @Test fun inputAndWorkflowPoliciesAcceptImageInputs() {
        val draft = WorkspaceDraft.forTool("img_bg_remove", emptyList())
        assertEquals(ToolInputKind.IMAGES, ToolInputPolicy.resolve("img_bg_remove", draft).kind)
        val choice = OutputFileChoice(UUID.randomUUID().toString(), "pic.png", "image/png", 1024L)
        assertEquals(listOf(choice), OutputWorkflowPolicy.validate(listOf(choice), "img_bg_remove"))
    }

    @Test fun confidenceMaskThresholdingDecontaminatesEdgesAndTrimsBounds() {
        // 5x5 image: central 3x3 pure red foreground, edge pixel at (0, 2) with 0.45 confidence over white background
        val w = 5
        val h = 5
        val bgWhite = Color.argb(255, 255, 255, 255)
        val fgRed = Color.argb(255, 200, 20, 20)
        val pixels = IntArray(w * h) { bgWhite }
        val conf = FloatArray(w * h) { 0.02f }

        for (y in 1..3) {
            for (x in 1..3) {
                pixels[y * w + x] = fgRed
                conf[y * w + x] = 0.96f
            }
        }
        pixels[2 * w + 0] = bgWhite
        conf[2 * w + 0] = 0.45f

        val stats = SubjectSegmentationEngine.applyConfidenceMaskAndDecontaminate(pixels, conf, w, h)
        assertEquals(10, stats.foregroundPixels)
        assertTrue("Expected edge decontamination on border pixel", stats.edgeDecontaminatedPixels > 0)

        assertEquals(0, (pixels[0] ushr 24) and 0xFF)
        val edgePixel = pixels[2 * w + 0]
        val r = (edgePixel ushr 16) and 0xFF
        val g = (edgePixel ushr 8) and 0xFF
        assertTrue("Decontaminated border pixel should pull toward foreground red", r > g)

        val trim = SubjectSegmentationEngine.computeTrimBounds(pixels, w, h, paddingPx = 0)
        assertNotNull(trim)
        assertEquals(0, trim!!.left)
        assertEquals(1, trim.top)
        assertEquals(3, trim.right)
        assertEquals(3, trim.bottom)

        val allBgConf = FloatArray(w * h) { 0.05f }
        val allBgPixels = IntArray(w * h) { bgWhite }
        assertEquals(0, SubjectSegmentationEngine.applyConfidenceMaskAndDecontaminate(allBgPixels, allBgConf, w, h).foregroundPixels)
        assertNull(SubjectSegmentationEngine.computeTrimBounds(allBgPixels, w, h))
        assertNull(SubjectSegmentationEngine.backdropColorForMode("TRANSPARENT"))
        assertEquals(Color.WHITE, SubjectSegmentationEngine.backdropColorForMode("WHITE"))
    }

    @Test fun isolatedMicroNoiseIslandsArePrunedWhileMainSubjectRemainsIntact() {
        val w = 10
        val h = 10
        val pixels = IntArray(w * h) { Color.WHITE }
        val conf = FloatArray(w * h) { 0.0f }
        // Main subject block (4x4 = 16 px)
        for (y in 2..5) {
            for (x in 2..5) {
                pixels[y * w + x] = Color.BLUE
                conf[y * w + x] = 0.98f
            }
        }
        // Stray isolated 1px & 2px noise specks at far corner
        pixels[0] = Color.DKGRAY
        conf[0] = 0.90f
        pixels[9 * w + 9] = Color.DKGRAY
        pixels[9 * w + 8] = Color.DKGRAY
        conf[9 * w + 9] = 0.90f
        conf[9 * w + 8] = 0.90f

        val stats = SubjectSegmentationEngine.applyConfidenceMaskAndDecontaminate(pixels, conf, w, h)
        assertEquals(16, stats.foregroundPixels)
        assertEquals("Isolated corner speck should be pruned", 0, (pixels[0] ushr 24) and 0xFF)
        assertEquals("Isolated 2px dust speck should be pruned", 0, (pixels[9 * w + 9] ushr 24) and 0xFF)
    }
}
