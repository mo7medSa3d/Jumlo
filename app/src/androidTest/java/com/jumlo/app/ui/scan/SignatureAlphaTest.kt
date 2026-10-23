package com.jumlo.app.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Inspects the actual production renderer's exported PNG, including decoded alpha pixels. */
@RunWith(AndroidJUnit4::class)
class SignatureAlphaTest {
    @Test fun exportedSignatureRetainsTransparentBackgroundAndSelectedInk() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=File.createTempFile("signature-alpha-", ".png", context.cacheDir)
        val bitmap=renderSignature(listOf(
            SignatureStroke(listOf(Offset(.2f,.5f),Offset(.8f,.5f)), Color.RED, .04f)
        ),400,200)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            val decoded=BitmapFactory.decodeFile(file.path)
            assertNotNull("Exported PNG must decode",decoded)
            decoded!!
            try {
                assertEquals(600,decoded.width)
                assertEquals(300,decoded.height)
                assertTrue(decoded.hasAlpha())
                assertEquals(0,Color.alpha(decoded.getPixel(0,0)))
                assertEquals(0,Color.alpha(decoded.getPixel(300,25)))
                assertEquals(255,Color.alpha(decoded.getPixel(300,150)))
                assertEquals(Color.RED,decoded.getPixel(300,150))
                var transparent=0
                val pixels=IntArray(decoded.width*decoded.height)
                decoded.getPixels(pixels,0,decoded.width,0,0,decoded.width,decoded.height)
                for(pixel in pixels) if(Color.alpha(pixel)==0) transparent++
                assertTrue("A signature must not acquire an opaque page-sized background",transparent>pixels.size*9/10)
            } finally {decoded.recycle()}
        } finally {bitmap.recycle();file.delete()}
    }

    @Test fun differentInkWidthsAndColorsProduceDifferentRealPixels() {
        val bitmap=renderSignature(listOf(
            SignatureStroke(listOf(Offset(.2f,.25f),Offset(.8f,.25f)),Color.BLUE,.01f),
            SignatureStroke(listOf(Offset(.2f,.75f),Offset(.8f,.75f)),Color.BLACK,.05f)
        ),600,300)
        try {
            assertEquals(Color.BLUE,bitmap.getPixel(300,75))
            assertEquals(Color.BLACK,bitmap.getPixel(300,225))
            assertEquals(0,Color.alpha(bitmap.getPixel(300,83)))
            assertEquals(255,Color.alpha(bitmap.getPixel(300,233)))
        } finally {bitmap.recycle()}
    }
}
