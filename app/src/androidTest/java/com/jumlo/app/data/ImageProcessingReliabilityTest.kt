package com.jumlo.app.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.core.FitMode
import com.jumlo.app.core.ImageSizeBoundException
import com.jumlo.app.data.processing.BitmapExtras
import com.jumlo.app.data.processing.BitmapTransforms
import com.jumlo.app.data.processing.ImageConversionSettings
import com.jumlo.app.data.processing.ImageWatermarkSettings
import com.jumlo.app.data.pdf.AndroidImageCodec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageProcessingReliabilityTest {
    private fun temp(suffix:String)=File.createTempFile("image-reliability-",suffix,ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir)
    private val budget=1024L*1024L

    @Test fun positionedRotatedLogoAndTiledOpacityReachTheExportPixels() {
        val source=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)}
        val logo=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.RED)}
        try {
            val settings=ImageWatermarkSettings(useLogo=true,opacityPercent=100,centerX=.25,centerY=.3,rotationDegrees=90)
            val output=BitmapExtras.watermark(source,logo,settings,budget)
            try {
                assertTrue(Color.red(output.getPixel(50,40))>240 && Color.green(output.getPixel(50,40))<10)
                assertEquals(Color.WHITE,output.getPixel(75,60))
                assertEquals(Color.WHITE,source.getPixel(50,40))
            } finally {output.recycle()}
            val tiled=BitmapExtras.watermark(source,logo,settings.copy(tiled=true,rotationDegrees=0,scale=.5,opacityPercent=50),budget)
            try {
                for(x in listOf(36,100,164)) for(y in listOf(36,100,164)) {
                    val pixel=tiled.getPixel(x,y)
                    assertTrue(Color.red(pixel)>250 && Color.green(pixel) in 120..135 && Color.blue(pixel) in 120..135)
                }
                assertEquals(Color.WHITE,tiled.getPixel(70,70))
            } finally {tiled.recycle()}
        } finally {source.recycle();logo.recycle()}
    }

    @Test fun sharedWatermarkTextRasterHasRealAlphaAndSelectedInkColor() {
        val raster=BitmapExtras.watermarkTextBitmap("مرحبا Jumlo","serif","#2050D0")
        try {
            assertEquals(0,Color.alpha(raster.getPixel(0,0)))
            assertTrue(raster.width<=1710 && raster.height<=220)
            val pixels=IntArray(raster.width*raster.height)
            raster.getPixels(pixels,0,raster.width,0,0,raster.width,raster.height)
            val ink=pixels.filter {Color.alpha(it)>240}
            assertTrue(ink.isNotEmpty())
            assertTrue(ink.all {Color.blue(it)>190 && Color.red(it)<50})
        } finally {raster.recycle()}
    }

    @Test fun sampledScanDecodeBoundsPixelsAndNormalizesExifWithoutChangingSource() {
        val source=temp(".jpg")
        val bitmap=Bitmap.createBitmap(320,160,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.RED)}
        try {
            BitmapTransforms.encode(bitmap,source,ImageConversionSettings(format="JPEG"),budget)
            ExifInterface(source).apply {setAttribute(ExifInterface.TAG_ORIENTATION,"6");saveAttributes()}
            val original=source.readBytes()
            BitmapTransforms.decodeSampled(source,30_000,160).use { decoded ->
                assertEquals(40,decoded.bitmap.width)
                assertEquals(80,decoded.bitmap.height)
                assertTrue(decoded.bitmap.allocationByteCount<=30_000)
                assertTrue(Color.red(decoded.bitmap.getPixel(20,40))>240)
            }
            assertArrayEquals(original,source.readBytes())
        } finally {bitmap.recycle();source.delete()}
    }

    @Test fun impossibleSizeBoundDoesNotPublishCandidateAndKeepsSource() {
        val source=temp(".png"); val output=temp(".jpg")
        val bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            BitmapTransforms.encode(bitmap,source,ImageConversionSettings(format="PNG"),budget)
            val original=source.readBytes()
            try {
                BitmapTransforms.encodeGuaranteedSmaller(bitmap,output,100,ImageConversionSettings(format="JPEG",quality=25),budget)
                fail("A JPEG header alone exceeds the requested 99-byte bound")
            } catch(expected:ImageSizeBoundException) {
                assertEquals(99L,expected.expectedMaximumBytes)
                assertTrue(expected.actualBytes>expected.expectedMaximumBytes)
            }
            assertFalse(output.exists())
            assertArrayEquals(original,source.readBytes())
            assertFalse(bitmap.isRecycled)
        } finally { bitmap.recycle();source.delete();output.delete() }
    }

    @Test fun decodeFailsAtTrueBudgetInsteadOfSilentlySamplingOrIgnoringIt() {
        val source=temp(".png")
        val bitmap=Bitmap.createBitmap(32,16,Bitmap.Config.ARGB_8888)
        try {
            BitmapTransforms.encode(bitmap,source,ImageConversionSettings(format="PNG"),budget)
            try { BitmapTransforms.decode(source,1000).use { fail("Full dimensions cannot fit in 1000 bytes") } }
            catch(expected:IllegalArgumentException) { assertEquals("IMAGE_MEMORY_BUDGET_EXCEEDED",expected.message) }
            BitmapTransforms.decode(source,budget).use { assertEquals(32,it.bitmap.width);assertEquals(16,it.bitmap.height) }
        } finally { bitmap.recycle();source.delete() }
    }

    @Test fun resizePercentageUsesUprightOriginalDimensions() {
        val source=temp(".jpg")
        val bitmap=Bitmap.createBitmap(12,8,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            BitmapTransforms.encode(bitmap,source,ImageConversionSettings(format="JPEG"),budget)
            ExifInterface(source).apply { setAttribute(ExifInterface.TAG_ORIENTATION,"6");saveAttributes() }
            val info=BitmapTransforms.inspect(source)
            assertEquals(8,info.normalizedWidth);assertEquals(12,info.normalizedHeight)
            BitmapTransforms.decode(source,budget).use { decoded ->
                val output=BitmapTransforms.resize(decoded.bitmap,info.normalizedWidth/2,info.normalizedHeight/2,FitMode.FIT,false,Color.WHITE,budget)
                try { assertEquals(4,output.width);assertEquals(6,output.height) } finally { output.recycle() }
            }
        } finally { bitmap.recycle();source.delete() }
    }

    @Test fun expandedTransformsRejectCombinedResidentAllocations() {
        val source=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888)
        val logo=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888)
        fun rejects(action:()->Bitmap) {
            try { action().also { it.recycle() };fail("Must reject before allocation") }
            catch(expected:IllegalArgumentException) { assertEquals("IMAGE_MEMORY_BUDGET_EXCEEDED",expected.message) }
        }
        try {
            rejects { BitmapExtras.adjust(source,0,100,100,2000,1000) }
            rejects { BitmapExtras.watermarkLogo(source,logo,1000) }
            rejects { BitmapExtras.watermarkText(source,"test",100,799) }
            rejects { BitmapTransforms.resize(source,20,20,FitMode.PAD,true,Color.WHITE,1000) }
        } finally { source.recycle();logo.recycle() }
    }

    @Test fun pdfJpegCodecBudgetsTheDecodedAndScaledBitmapsTogether() {
        val bitmap=Bitmap.createBitmap(1000,1000,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED);setHasAlpha(false) }
        val source=java.io.ByteArrayOutputStream()
        try {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,90,source))
            // A 1000x1000 decode alone fits 4 MB. Keeping it resident while
            // allocating an 800x800 resize needs 6.56 MB and must be sampled.
            val recoded=AndroidImageCodec(4_000_000).recode(source.toByteArray(),800,800,80)
            assertNotNull(recoded)
            assertTrue(recoded!!.width<=500 && recoded.height<=500)
            val result=android.graphics.BitmapFactory.decodeByteArray(recoded.bytes,0,recoded.bytes.size)
            try { assertEquals(recoded.width,result.width);assertEquals(recoded.height,result.height) }
            finally { result.recycle() }
        } finally { bitmap.recycle() }
    }
}
