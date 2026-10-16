package com.universalutilities.app.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.universalutilities.app.data.processing.BitmapTransforms
import com.universalutilities.app.core.Box
import com.universalutilities.app.core.FitMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagePixelTest {
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
