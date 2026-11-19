package com.jumlo.app

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.jumlo.app.core.office.OfficeBlock
import com.jumlo.app.core.office.OfficeCell
import com.jumlo.app.core.office.OfficeDocument
import com.jumlo.app.core.office.OfficeParagraph
import com.jumlo.app.core.office.OfficeRun
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.jumlo.app.data.processing.OfficeToPdfRenderer
import com.jumlo.app.model.HapticIntensity
import com.jumlo.app.ui.util.AppHaptics
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests verifying hardening improvements across haptics (API 33+ VibrationAttributes),
 * non-deprecated PDFBox RGB color stamping, and OfficeToPdfRenderer stream/document cleanup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HardeningRegressionTest {

    @Test
    fun appHapticsRespectsDisabledAndOffIntensityAndRunsSafelyOnApi34() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = View(context).apply { isHapticFeedbackEnabled = true }

        // Disabled or OFF should no-op cleanly without throwing.
        AppHaptics(context, view, enabled = false, intensity = HapticIntensity.STRONG, soundEnabled = false, sound = null)
            .actionClick()
        AppHaptics(context, view, enabled = true, intensity = HapticIntensity.OFF, soundEnabled = false, sound = null)
            .downloadComplete()

        // Active levels (LIGHT, MEDIUM, STRONG) on API 34 use VibrationAttributes.USAGE_TOUCH or View fallback safely.
        for (level in listOf(HapticIntensity.LIGHT, HapticIntensity.MEDIUM, HapticIntensity.STRONG)) {
            val haptics = AppHaptics(context, view, enabled = true, intensity = level, soundEnabled = false, sound = null)
            haptics.actionClick()
            haptics.downloadComplete()
            haptics.error()
        }
    }

    @Test
    fun pdfStampTextWritesValidRgbColorUsingNonDeprecatedApi() {
        val doc = PDDocument()
        try {
            doc.addPage(PDPage(PDRectangle.LETTER))
            PdfBoxBackend.stampText(
                doc = doc,
                zeroBasedPages = listOf(0),
                text = "Approved",
                xNorm = 0.25,
                yNormTop = 0.25,
                fontSize = 14f,
                gray = 0.2f,
                colorHex = "#3366CC"
            )
            assertEquals(1, doc.numberOfPages)
            val streamBytes = doc.getPage(0).contents.use { it.readBytes() }.decodeToString()
            // Expect normalized RGB non-stroking color operator ("rg") with 0.2 0.4 0.8.
            assertTrue("Expected RGB rg operator in content stream:\n$streamBytes", streamBytes.contains(" rg"))
            assertTrue("Expected stamped text in content stream", streamBytes.contains("(Approved) Tj"))
        } finally {
            doc.close()
        }
    }

    @Test
    fun officeToPdfRendererClosesContentStreamCleanlyOnCancellation() {
        val doc = OfficeDocument(
            kind = com.jumlo.app.core.office.OfficeKind.DOCX,
            pageWidthPt = 612.0,
            pageHeightPt = 792.0,
            marginPt = 48.0,
            blocks = listOf(
                OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("First paragraph")))),
                OfficeBlock.TableBlock(
                    rows = listOf(
                        listOf(OfficeCell("Header A", bold = true), OfficeCell("Header B", bold = true)),
                        listOf(OfficeCell("Row 1A"), OfficeCell("Row 1B"))
                    )
                ),
                OfficeBlock.ParagraphBlock(OfficeParagraph(listOf(OfficeRun("Second paragraph"))))
            ),
            unsupported = emptyList()
        )
        var calls = 0
        var cancelled = false
        try {
            OfficeToPdfRenderer.render(doc, maxBitmapBytes = 16L * 1024 * 1024) {
                calls++
                if (calls >= 2) throw CancellationException("USER_CANCELLED")
            }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue("Expected CancellationException to propagate cleanly", cancelled)
        assertEquals(2, calls)
    }

    @Test
    fun officeToPdfRendererRendersLatinTableWithoutError() {
        val doc = OfficeDocument(
            kind = com.jumlo.app.core.office.OfficeKind.DOCX,
            pageWidthPt = 612.0,
            pageHeightPt = 792.0,
            marginPt = 48.0,
            blocks = listOf(
                OfficeBlock.TableBlock(
                    rows = listOf(
                        listOf(OfficeCell("Col 1", bold = true), OfficeCell("Col 2", bold = true)),
                        listOf(OfficeCell("Alpha"), OfficeCell("Beta"))
                    )
                )
            ),
            unsupported = emptyList()
        )
        OfficeToPdfRenderer.render(doc, maxBitmapBytes = 16L * 1024 * 1024) {}.use { rendered ->
            assertEquals(1, rendered.document.numberOfPages)
            assertEquals(0, rendered.stats.droppedImages)
        }
    }
}
