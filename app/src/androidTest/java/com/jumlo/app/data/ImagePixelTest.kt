package com.jumlo.app.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.processing.BitmapTransforms
import com.jumlo.app.data.processing.BitmapExtras
import com.jumlo.app.core.Box
import com.jumlo.app.core.FitMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePixelTest {
    @Test fun compareIncludesAlphaAndDoesNotClaimTransparentPixelsAreIdentical() {
        val a=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val b=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        try { assertTrue(BitmapExtras.meanDifference(a,b)>0.0);assertEquals(0.0,BitmapExtras.meanDifference(a,a),0.0) }
        finally { a.recycle();b.recycle() }
    }
    @Test(expected=IllegalArgumentException::class) fun joinBudgetsResidentSourceAndOutputTogether() {
        val a=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888)
        val b=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888)
        try { BitmapExtras.join(listOf(a,b),"HSTRIP",0,1000) }
        finally { a.recycle();b.recycle() }
    }

    @Test fun logoWatermarkRespectsOpacityAndWorksOnSmallImages() {
        val source=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val logo=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val marked=BitmapExtras.watermarkLogo(source,logo,4096,50)
        try { assertTrue(Color.red(marked.getPixel(7,7)) in 120..135);assertEquals(Color.BLACK,source.getPixel(7,7)) }
        finally { marked.recycle();source.recycle();logo.recycle() }
    }
    @Test fun cropUsesSourcePixelsAndPreservesInput() {
        val bitmap=Bitmap.createBitmap(4,2,Bitmap.Config.ARGB_8888)
        for(y in 0..1) for(x in 0..3) bitmap.setPixel(x,y,if(x<2) Color.RED else Color.BLUE)
        val result=BitmapTransforms.crop(bitmap,Box(0.5,0.0,1.0,1.0),0,false,false,1024*1024)
        assertEquals(2,result.width); assertEquals(Color.BLUE,result.getPixel(0,0)); assertEquals(Color.RED,bitmap.getPixel(0,0))
        if(result!==bitmap) result.recycle(); bitmap.recycle()
    }
    @Test fun fitAndPadHaveDifferentRealDimensions() {
        val source=Bitmap.createBitmap(8,4,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val fit=BitmapTransforms.resize(source,4,4,FitMode.FIT,true,Color.WHITE,1024*1024)
        val pad=BitmapTransforms.resize(source,4,4,FitMode.PAD,true,Color.WHITE,1024*1024)
        assertEquals(2,fit.height); assertEquals(4,pad.height); assertEquals(Color.WHITE,pad.getPixel(0,0))
        fit.recycle(); pad.recycle(); source.recycle()
    }
}
