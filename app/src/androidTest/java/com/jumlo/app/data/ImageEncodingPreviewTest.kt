package com.jumlo.app.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.processing.BitmapTransforms
import com.jumlo.app.data.processing.ImageCompressionSettings
import com.jumlo.app.data.processing.ImageConversionSettings
import com.jumlo.app.data.processing.ImageEncodingPreview
import com.jumlo.app.data.processing.ImageOutputEncoder
import com.jumlo.app.data.processing.ImageTargetSettings
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Android codecs; these are device regressions, not mock bitmap assertions. */
@RunWith(AndroidJUnit4::class)
class ImageEncodingPreviewTest {
    private val budget = 32L * 1024 * 1024
    private fun directory() = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
        "encoded-preview-test-${UUID.randomUUID()}").apply { mkdirs() }

    private fun noisyBitmap(width:Int=128,height:Int=96) = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply {
        val random=java.util.Random(2026)
        val pixels=IntArray(width*height) { Color.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256)) }
        setPixels(pixels,0,width,0,0,width,height)
    }

    @Test fun jpegPreviewUsesActualProductionBytesAndMatteWithoutChangingSource() {
        val directory=directory();val source=File(directory,"source.png");val expected=File(directory,"expected.jpg")
        val scratch=File(directory,"preview").apply {mkdirs()}
        val original=noisyBitmap().apply {for(y in 0 until 32) for(x in 0 until 32) setPixel(x,y,Color.TRANSPARENT)}
        val settings=ImageConversionSettings(format="JPEG",quality=23,matteArgb=Color.CYAN)
        try {
            BitmapTransforms.encode(original,source,ImageConversionSettings(format="PNG"),budget)
            val sourceBytes=source.readBytes()
            val preview=ImageEncodingPreview.create(source,scratch,"img_01",Json.encodeToString(settings),budget,0)
            try {
                assertFalse(preview.sampled)
                assertEquals("JPEG",preview.format);assertEquals(23,preview.quality)
                assertNotSame(preview.before,preview.after)
                assertEquals(0,Color.alpha(preview.before.getPixel(8,8)))
                val matte=preview.after.getPixel(8,8)
                assertEquals(255,Color.alpha(matte));assertTrue(Color.green(matte)>240 && Color.blue(matte)>240)
                BitmapTransforms.decode(source,budget).use { decoded ->
                    val actual=ImageOutputEncoder.encode(decoded.bitmap,expected,"img_01",settings,source.length(),budget)
                    assertEquals(actual.sizeBytes,preview.encodedBytes)
                    BitmapTransforms.decode(expected,budget).use { encoded ->
                        assertTrue(encoded.bitmap.sameAs(preview.after))
                    }
                }
                assertArrayEquals(sourceBytes,source.readBytes())
                assertFalse(original.isRecycled)
                assertTrue(scratch.listFiles().isNullOrEmpty())
            } finally {preview.close()}
            assertTrue(preview.before.isRecycled && preview.after.isRecycled)
            assertFalse(original.isRecycled)
        } finally {original.recycle();directory.deleteRecursively()}
    }

    @Test fun compressionAndTargetUseTheSameCodecAndSelectedQualityAsPublication() {
        val directory=directory();val source=File(directory,"source.png");val scratch=File(directory,"preview").apply {mkdirs()}
        val original=noisyBitmap(256,192)
        try {
            BitmapTransforms.encode(original,source,ImageConversionSettings(format="PNG"),budget)
            val conversion=ImageConversionSettings(format="PNG",quality=71)
            ImageEncodingPreview.create(source,scratch,"img_04",Json.encodeToString(ImageCompressionSettings(conversion)),budget,0).use { preview ->
                assertEquals("WEBP",preview.format)
                assertTrue(preview.encodedBytes<source.length())
                assertTrue(requireNotNull(preview.quality)<=71)
            }
            val target=ImageTargetSettings(targetBytes=3000)
            ImageEncodingPreview.create(source,scratch,"img_target",Json.encodeToString(target),budget,0).use { preview ->
                assertFalse(preview.sampled)
                assertEquals(3000L,preview.requestedTargetBytes)
                assertEquals("JPEG",preview.format)
                assertTrue(preview.encodedBytes in 1..3000L)
                val expected=File(directory,"target.jpg")
                BitmapTransforms.decode(source,budget).use { decoded ->
                    val result=ImageOutputEncoder.encode(decoded.bitmap,expected,"img_target",target.output,source.length(),budget,target.targetBytes)
                    assertEquals(result.sizeBytes,preview.encodedBytes)
                    assertEquals(result.quality,preview.quality)
                    assertEquals(result.width,preview.encodedWidth);assertEquals(result.height,preview.encodedHeight)
                }
            }
            assertTrue(scratch.listFiles().isNullOrEmpty())
        } finally {original.recycle();directory.deleteRecursively()}
    }

    @Test fun sampledPreviewReportsItsActualDimensionsRatherThanFullExportDimensions() {
        val directory=directory();val source=File(directory,"source.png");val scratch=File(directory,"preview").apply {mkdirs()}
        val original=noisyBitmap(400,200)
        try {
            BitmapTransforms.encode(original,source,ImageConversionSettings(format="PNG"),budget)
            ImageEncodingPreview.create(source,scratch,"img_target",Json.encodeToString(ImageTargetSettings(1000)),budget,0,maxEdge=100).use { preview ->
                assertTrue(preview.sampled)
                assertEquals(400,preview.sourceWidth);assertEquals(200,preview.sourceHeight)
                assertTrue(preview.before.width<=100 && preview.before.height<=100)
                assertTrue(preview.encodedWidth<=preview.before.width && preview.encodedHeight<=preview.before.height)
                assertEquals(1000L,preview.requestedTargetBytes)
            }
            assertTrue(scratch.listFiles().isNullOrEmpty())
        } finally {original.recycle();directory.deleteRecursively()}
    }

    @Test fun cancellationAfterEncodingStartsDeletesPrivateCandidatesAndPreservesSource() {
        val directory=directory();val source=File(directory,"source.png");val scratch=File(directory,"preview").apply {mkdirs()}
        val original=noisyBitmap(256,192)
        try {
            BitmapTransforms.encode(original,source,ImageConversionSettings(format="PNG"),budget)
            val bytes=source.readBytes()
            var reachedCandidate=false
            try {
                ImageEncodingPreview.create(source,scratch,"img_01",Json.encodeToString(ImageConversionSettings()),budget,0) {
                    if(scratch.walkTopDown().any {it.isFile && it.length()>0}) {
                        reachedCandidate=true
                        throw CancellationException("test cancellation")
                    }
                }.close()
                fail("Cancellation must propagate")
            } catch(expected:CancellationException) {assertTrue(reachedCandidate)}
            assertTrue(scratch.listFiles().isNullOrEmpty())
            assertArrayEquals(bytes,source.readBytes())
            assertFalse(original.isRecycled)
        } finally {original.recycle();directory.deleteRecursively()}
    }
}
