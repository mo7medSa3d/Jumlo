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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImageProcessingReliabilityTest {
    private fun temp(suffix:String)=File.createTempFile("image-reliability-",suffix,ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir)
    private val budget=1024L*1024L

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
}
