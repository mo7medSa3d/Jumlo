package com.jumlo.app.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.jumlo.app.ui.theme.JumloTheme
import com.jumlo.app.ui.viewer.PdfViewerScreen
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PdfViewerInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersPagesAndSupportsThumbnailsZoomDarkPagesAndContinuousScroll() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val file = File(app.filesDir, "processing/results/fixture/viewer-${UUID.randomUUID()}.pdf").apply { requireNotNull(parentFile).mkdirs() }
        PDDocument().use { document ->
            repeat(3) { number ->
                val page = PDPage(PDRectangle.LETTER)
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText(); content.setFont(PDType1Font.HELVETICA, 20f)
                    content.newLineAtOffset(48f, 700f); content.showText("Reader page ${number + 1}"); content.endText()
                }
            }
            document.save(file)
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file).toString()
        val visible = mutableStateOf(true)
        compose.setContent { JumloTheme { if (visible.value) PdfViewerScreen(uri, "Three-page fixture", onBack = {}, onOpenFile = {}, onPrint = { _, _ -> }) } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("pdf.page.image.0", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pdf.thumbnail.1").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("pdf.page.image.1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pdf.thumbnail.1").assertIsSelected()
        compose.onNodeWithTag("pdf.page.1").performTouchInput { doubleClick() }
        compose.onNodeWithTag("pdf.page.1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "250%"))
        compose.onNodeWithTag("pdf.dark.toggle").performClick().assertIsSelected()
        compose.onNodeWithTag("pdf.thumbnails.toggle").performClick()
        compose.onNodeWithTag("pdf.thumbnails").assertDoesNotExist()
        compose.onNodeWithTag("pdf.pages").performScrollToIndex(2)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("pdf.page.image.2", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pdf.page.2").assertExists()
        // Removing the screen exercises the renderer's cancellation-safe cleanup.
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        file.delete()
    }
}
