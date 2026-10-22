package com.jumlo.app.printing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/** Printing may downsample pixels without changing physical page geometry (kept in PrintPageInfo). */
object PrintImageDecoder {
    class Decoded(val bitmap:Bitmap):Closeable {override fun close() {bitmap.recycle()}}
    fun decode(file:File):Decoded {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeFile(file.path,bounds)
        require(bounds.outMimeType in setOf("image/jpeg","image/png","image/webp","image/heic","image/heif")) { "PRINT_UNSUPPORTED" }
        rejectAnimation(file,bounds.outMimeType)
        val options=BitmapFactory.Options().apply {
            inSampleSize=PrintImageBudget.sampleSize(bounds.outWidth,bounds.outHeight)
            inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888
            inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val bitmap=BitmapFactory.decodeFile(file.path,options) ?: throw IllegalArgumentException("PRINT_UNSUPPORTED")
        try {
            val orientation=runCatching {ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
            val transform=Matrix().apply {when(orientation) {
                2->setScale(-1f,1f)
                3->setRotate(180f)
                4->setScale(1f,-1f)
                5->{setRotate(90f);postScale(-1f,1f)}
                6->setRotate(90f)
                7->{setRotate(-90f);postScale(-1f,1f)}
                8->setRotate(-90f)
            }}
            if(orientation in 2..8) {
                val normalized=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,transform,true)
                if(normalized!==bitmap) bitmap.recycle()
                return Decoded(normalized)
            }
            return Decoded(bitmap)
        } catch(t:Throwable) {bitmap.recycle();throw t}
    }
    private fun rejectAnimation(file:File,mime:String?) {
        if(mime !in setOf("image/webp","image/png")) return
        RandomAccessFile(file,"r").use {input ->
            var position=if(mime=="image/png") 8L else 12L
            while(position+8<=input.length()) {
                input.seek(position);val size:Long;val type=ByteArray(4)
                if(mime=="image/png") {size=input.readInt().toLong() and 0xffffffffL;input.readFully(type)}
                else {input.readFully(type);size=Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL}
                val kind=String(type,Charsets.US_ASCII)
                require(kind !in setOf("acTL","ANIM","ANMF")) { "PRINT_UNSUPPORTED" }
                val step=8+size+if(mime=="image/png") 4 else size%2
                require(step>=8 && step<=input.length()-position) { "PRINT_UNSUPPORTED" }
                position+=step
            }
        }
    }
}
