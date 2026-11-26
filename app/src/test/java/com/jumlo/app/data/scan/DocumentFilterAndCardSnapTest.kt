package com.jumlo.app.data.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.jumlo.app.ui.scan.snapToCardEdges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentFilterAndCardSnapTest {
    @Test
    fun documentFilterHandlesOriginalTinyAndShadowedBitmaps() {
        val tiny = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(180, 180, 180))
        }
        assertSame(tiny, DocumentFilterEngine.apply(tiny, ScanFilter.ORIGINAL))
        for (filter in listOf(ScanFilter.CLEAN_DOCUMENT, ScanFilter.CRISP_BW, ScanFilter.MAGIC_COLOR)) {
            val out = DocumentFilterEngine.apply(tiny, filter)
            assertEquals(1, out.width)
            assertEquals(1, out.height)
            out.recycle()
        }
        tiny.recycle()

        val page = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(150, 150, 150))
            for (y in 40 until 52) {
                for (x in 30 until 90) {
                    setPixel(x, y, Color.rgb(30, 30, 30))
                }
            }
        }
        val bw = DocumentFilterEngine.apply(page, ScanFilter.CRISP_BW)
        val paperPixel = bw.getPixel(10, 10)
        val inkPixel = bw.getPixel(60, 46)
        assertTrue(Color.red(paperPixel) > 200)
        assertTrue(Color.red(inkPixel) < 80)
        bw.recycle()
        page.recycle()
    }

    @Test
    fun snapToCardEdgesCropsClearBorderedCardAndFallsBackOnFlatOrTiny() {
        val tiny = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        assertSame(tiny, snapToCardEdges(tiny))
        tiny.recycle()

        val flat = Bitmap.createBitmap(200, 130, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        assertSame(flat, snapToCardEdges(flat))
        flat.recycle()

        val scene = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
            Canvas(this).drawRect(32f, 20f, 288f, 180f, Paint().apply { color = Color.WHITE })
        }
        val cropped = snapToCardEdges(scene)
        assertTrue(cropped.width < scene.width)
        assertTrue(cropped.height < scene.height)
        assertTrue(cropped.width >= 200)
        assertTrue(cropped.height >= 120)
        if (cropped !== scene) cropped.recycle()
        scene.recycle()
    }
}
