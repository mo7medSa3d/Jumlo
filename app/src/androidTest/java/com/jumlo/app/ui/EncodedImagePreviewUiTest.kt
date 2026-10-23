package com.jumlo.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.R
import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.data.processing.EncodedImagePreview
import com.jumlo.app.data.processing.ImageEncodingPreview
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.theme.JumloTheme
import com.jumlo.app.ui.workspace.EncodedImagePreviewPanel
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real Android encode/decode results drive the production UI; no screenshot or output is mocked. */
class EncodedImagePreviewUiTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun qualityChangesRenderNewCodecResultAndReleaseReplacedLayers() {
        val directory=File(context.cacheDir,"encoded-ui-${UUID.randomUUID()}").apply {mkdirs()}
        val source=sourceImage(directory,480,320)
        val delivered=CopyOnWriteArrayList<EncodedImagePreview>()
        var quality by mutableIntStateOf(90)
        var visible by mutableStateOf(true)
        try {
            compose.setContent {JumloTheme(ThemeMode.LIGHT,AppLanguage.ENGLISH) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if(visible) EncodedImagePreviewPanel("source","img_01",
                        Result.success(WorkspaceDraft.forTool("img_01",emptyList()).copy(quality=quality.toString()).executionSettings())) {_,tool,json ->
                        withContext(Dispatchers.IO) {ImageEncodingPreview.create(source,directory,tool,json,32L*1024*1024,0).also(delivered::add)}
                    }
                }
            }}
            waitForPreview()
            val first=delivered.single()
            assertFalse(first.sampled)
            assertEquals(90,requireNotNull(first.quality))
            compose.onNodeWithTag("encoded.preview.exact").assertExists()
            compose.onNodeWithTag("encoded.preview.quality").assertTextEquals(context.getString(R.string.encoded_preview_quality,90))
            compose.runOnIdle {quality=25}
            compose.waitUntil(10_000) {delivered.size==2 && compose.onAllNodesWithTag("image.comparison").fetchSemanticsNodes().isNotEmpty()}
            val second=delivered.last()
            compose.runOnIdle {
                assertTrue(first.before.isRecycled)
                assertTrue(first.after.isRecycled)
                assertFalse(second.after.isRecycled)
            }
            compose.onNodeWithTag("encoded.preview.quality").assertTextEquals(context.getString(R.string.encoded_preview_quality,25))
            compose.onNodeWithTag("encoded.preview.output").assertTextEquals(context.getString(R.string.encoded_preview_output,
                second.format,PureDomainAlgorithms.formatBytes(second.encodedBytes),second.encodedWidth,second.encodedHeight))
            assertTrue(second.encodedBytes<first.encodedBytes)
            compose.runOnIdle {visible=false}
            compose.runOnIdle {assertTrue(second.before.isRecycled);assertTrue(second.after.isRecycled)}
        } finally {
            compose.runOnIdle {visible=false}
            compose.waitForIdle()
            delivered.forEach(EncodedImagePreview::close)
            directory.deleteRecursively()
        }
    }

    @Test fun sampledTargetPreviewLabelsSampleBytesAndDoesNotClaimFullSizeSuccess() {
        val directory=File(context.cacheDir,"encoded-ui-${UUID.randomUUID()}").apply {mkdirs()}
        val source=sourceImage(directory,1800,900)
        val delivered=CopyOnWriteArrayList<EncodedImagePreview>()
        var visible by mutableStateOf(true)
        try {
            val settings=WorkspaceDraft.forTool("img_target",emptyList()).copy(num1="200000").executionSettings()
            compose.setContent {JumloTheme(ThemeMode.DARK,AppLanguage.ENGLISH) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if(visible) EncodedImagePreviewPanel("source","img_target",Result.success(settings)) {_,tool,json ->
                        withContext(Dispatchers.IO) {ImageEncodingPreview.create(source,directory,tool,json,32L*1024*1024,0).also(delivered::add)}
                    }
                }
            }}
            waitForPreview()
            val result=delivered.single()
            assertTrue(result.sampled)
            assertEquals(200000L,requireNotNull(result.requestedTargetBytes))
            compose.onNodeWithTag("encoded.preview.sample").assertTextEquals(context.getString(R.string.encoded_preview_sample_notice))
            compose.onNodeWithTag("encoded.preview.exact").assertDoesNotExist()
            compose.onNodeWithTag("encoded.preview.output").assertTextEquals(context.getString(R.string.encoded_preview_sample_output,
                result.format,PureDomainAlgorithms.formatBytes(result.encodedBytes),result.encodedWidth,result.encodedHeight))
            compose.onNodeWithTag("encoded.preview.quality").assertTextEquals(context.getString(R.string.encoded_preview_sample_quality,result.quality))
            compose.onNodeWithTag("encoded.preview.target").assertTextEquals(context.getString(R.string.encoded_preview_target,
                PureDomainAlgorithms.formatBytes(200000L)))
            compose.runOnIdle {visible=false}
            compose.runOnIdle {assertTrue(result.before.isRecycled);assertTrue(result.after.isRecycled)}
        } finally {
            compose.runOnIdle {visible=false}
            compose.waitForIdle()
            delivered.forEach(EncodedImagePreview::close)
            directory.deleteRecursively()
        }
    }

    private fun waitForPreview() {
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("image.comparison").fetchSemanticsNodes().isNotEmpty()}
    }

    private fun sourceImage(directory:File,width:Int,height:Int):File {
        val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val row=IntArray(width)
        try {
            for(y in 0 until height) {
                for(x in 0 until width) row[x]=Color.rgb((x*13+y*3)%256,(x*3+y*17)%256,(x*19+y*7)%256)
                image.setPixels(row,0,width,0,y,width,1)
            }
            return File(directory,"source.png").also {file ->
                file.outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
            }
        } finally {image.recycle()}
    }
}
