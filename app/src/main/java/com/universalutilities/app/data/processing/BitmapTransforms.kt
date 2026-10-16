package com.universalutilities.app.data.processing

import android.graphics.*
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.universalutilities.app.core.*
import java.io.File
import java.io.FileOutputStream
import java.io.Closeable
import kotlin.math.*

data class DecodedImage(val bitmap:Bitmap,val normalizedExifOrientation:Int=1):Closeable { override fun close() { if(!bitmap.isRecycled) bitmap.recycle() } }

object BitmapTransforms {
    private fun requireAggregate(source:Bitmap,w:Int,h:Int,budget:Long) {
        MemoryBudget.requireBitmaps(w,h,1,budget)
        require(source.allocationByteCount.toLong() <= budget - w.toLong()*h*4) { "BITMAP_BUDGET_EXCEEDED" }
    }
    fun decode(file:File,budget:Long):DecodedImage {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(file.path,bounds)
        require(bounds.outMimeType in setOf("image/jpeg","image/png","image/webp","image/heic","image/heif")) { "UNSUPPORTED_IMAGE_FORMAT" }
        rejectAnimation(file,bounds.outMimeType)
        MemoryBudget.requireBitmaps(bounds.outWidth,bounds.outHeight,2,budget)
        val options=BitmapFactory.Options().apply { inPreferredConfig=Bitmap.Config.ARGB_8888; inScaled=false; inPreferredColorSpace=ColorSpace.get(ColorSpace.Named.SRGB) }
        val bitmap=BitmapFactory.decodeFile(file.path,options) ?: throw IllegalArgumentException("IMAGE_DECODE_FAILED")
        try {
            val orientation=try { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL) } catch(_:java.io.IOException) { 1 }
            val matrix=Matrix().apply { when(orientation) {
                2 -> setScale(-1f,1f)
                3 -> setRotate(180f)
                4 -> setScale(1f,-1f)
                5 -> { setRotate(90f); postScale(-1f,1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f,1f) }
                8 -> setRotate(-90f)
            } }
            if(orientation in 2..8) {
                val normalized=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
                if(normalized!==bitmap) bitmap.recycle()
                return DecodedImage(normalized)
            }
            return DecodedImage(bitmap)
        } catch(t:Throwable) { bitmap.recycle(); throw t }
    }
    /** Refuse animation rather than silently converting only its first frame. */
    private fun rejectAnimation(file:File,mime:String?) {
        if(mime!="image/webp" && mime!="image/png") return
        java.io.RandomAccessFile(file,"r").use { f ->
            var at=if(mime=="image/png") 8L else 12L
            while(at+8<=f.length()) {
                f.seek(at)
                val length:Long; val kind:String
                if(mime=="image/png") { length=f.readInt().toLong() and 0xffffffffL; val type=ByteArray(4); f.readFully(type); kind=String(type,Charsets.US_ASCII) }
                else { val type=ByteArray(4); f.readFully(type); kind=String(type,Charsets.US_ASCII); length=Integer.reverseBytes(f.readInt()).toLong() and 0xffffffffL }
                require(kind !in setOf("acTL","ANIM","ANMF")) { "ANIMATED_IMAGE_UNSUPPORTED" }
                val step=8+length+(if(mime=="image/png") 4 else length%2)
                require(step>=8 && step<=f.length()-at) { "MALFORMED_IMAGE" }
                at+=step
            }
        }
    }
    fun resize(source:Bitmap,w:Int,h:Int,mode:FitMode,upscale:Boolean,matte:Int,budget:Long):Bitmap {
        val geometry=ImageGeometry.resolve(source.width,source.height,w,h,mode,upscale)
        requireAggregate(source,geometry.width,geometry.height,budget)
        val out=Bitmap.createBitmap(geometry.width,geometry.height,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(out); if(mode==FitMode.PAD) canvas.drawColor(matte)
            val c=geometry.crop; val d=geometry.draw
            val sourceRect=Rect((c.left*source.width).roundToInt(),(c.top*source.height).roundToInt(),(c.right*source.width).roundToInt(),(c.bottom*source.height).roundToInt())
            canvas.drawBitmap(source,sourceRect,RectF(d.left.toFloat(),d.top.toFloat(),d.right.toFloat(),d.bottom.toFloat()),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return out
        } catch(t:Throwable) { out.recycle(); throw t }
    }
    fun crop(source:Bitmap,box:Box,turns:Int,flipX:Boolean,flipY:Boolean,budget:Long):Bitmap {
        box.normalized()
        val x=(box.left*source.width).floorToInt(); val y=(box.top*source.height).floorToInt()
        val right=(box.right*source.width).ceilToInt().coerceAtMost(source.width); val bottom=(box.bottom*source.height).ceilToInt().coerceAtMost(source.height)
        val w=right-x; val h=bottom-y; requireAggregate(source,w,h,budget)
        val matrix=Matrix().apply { setScale(if(flipX) -1f else 1f,if(flipY) -1f else 1f); postRotate(((turns%4+4)%4)*90f) }
        return Bitmap.createBitmap(source,x,y,w,h,matrix,true)
    }
    private fun Double.floorToInt()=floor(this).toInt()
    private fun Double.ceilToInt()=ceil(this).toInt()
    fun encode(bitmap:Bitmap,file:File,settings:ImageConversionSettings,budget:Long) {
        require(settings.quality in 1..100) { "INVALID_QUALITY" }
        val format=when(settings.format) {
            "JPEG" -> Bitmap.CompressFormat.JPEG
            "PNG" -> Bitmap.CompressFormat.PNG
            "WEBP" -> if(Build.VERSION.SDK_INT>=30) Bitmap.CompressFormat.WEBP_LOSSY else {
                @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }
            else -> throw IllegalArgumentException("UNSUPPORTED_IMAGE_FORMAT")
        }
        var matte:Bitmap?=null
        try {
            val output=if(settings.format=="JPEG" && bitmap.hasAlpha()) {
                requireAggregate(bitmap,bitmap.width,bitmap.height,budget)
                Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also {
                    matte=it; Canvas(it).apply { drawColor(settings.matteArgb or (0xff shl 24)); drawBitmap(bitmap,0f,0f,null) }
                }
            } else bitmap
            FileOutputStream(file).use { stream -> require(output.compress(format,settings.quality,stream)) { "IMAGE_ENCODE_FAILED" }; stream.fd.sync() }
            val check=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(file.path,check)
            require(check.outWidth==bitmap.width && check.outHeight==bitmap.height) { "IMAGE_VERIFY_FAILED" }
        } finally { matte?.recycle() }
    }
    fun extension(format:String)=when(format) { "JPEG" -> "jpg"; "PNG" -> "png"; "WEBP" -> "webp"; else -> throw IllegalArgumentException("UNSUPPORTED_IMAGE_FORMAT") }
    fun mime(format:String)=when(format) { "JPEG" -> "image/jpeg"; "PNG" -> "image/png"; "WEBP" -> "image/webp"; else -> throw IllegalArgumentException("UNSUPPORTED_IMAGE_FORMAT") }
}
