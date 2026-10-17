package com.jumlo.app.data.processing

import android.graphics.*
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.jumlo.app.core.*
import java.io.File
import java.io.FileOutputStream
import java.io.Closeable
import kotlin.math.*

data class DecodedImage(val bitmap:Bitmap,val normalizedExifOrientation:Int=1):Closeable { override fun close() { if(!bitmap.isRecycled) bitmap.recycle() } }

data class CodecCapability(
    val format: String,
    val canDecode: Boolean,
    val canEncode: Boolean,
    val canPreserveAnimation: Boolean,
    val supportedApiRange: IntRange,
    val verifiedFixtureIds: List<String>
)

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
                return DecodedImage(normalized,1)
            }
            return DecodedImage(bitmap,1)
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

/** Expansion transforms: watermark, join/stitch, adjustments, codec matrix. All budget-checked. */
object BitmapExtras {
    fun codecCapabilities(sdkInt: Int = Build.VERSION.SDK_INT): List<CodecCapability> = listOf(
        CodecCapability("JPEG", canDecode = true, canEncode = true, canPreserveAnimation = false, supportedApiRange = 26..36, verifiedFixtureIds = emptyList()),
        CodecCapability("PNG", canDecode = true, canEncode = true, canPreserveAnimation = false, supportedApiRange = 26..36, verifiedFixtureIds = emptyList()),
        CodecCapability("WEBP", canDecode = true, canEncode = true, canPreserveAnimation = false, supportedApiRange = 26..36, verifiedFixtureIds = emptyList()),
        CodecCapability("HEIC", canDecode = sdkInt >= 28, canEncode = false, canPreserveAnimation = false, supportedApiRange = 28..36, verifiedFixtureIds = emptyList())
    )

    fun watermarkText(source:Bitmap,text:String,opacityPercent:Int,budget:Long):Bitmap {
        MemoryBudget.requireBitmaps(source.width,source.height,2,budget)
        val out=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(out); canvas.drawBitmap(source,0f,0f,null)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{ color=Color.WHITE; alpha=(opacityPercent.coerceIn(1,100)*255/100); textSize=(source.width/12f).coerceAtLeast(18f); isFakeBoldText=true; setShadowLayer(4f,2f,2f,Color.BLACK) }
            canvas.drawText(text,source.width*0.08f,source.height*0.92f,paint); return out
        } catch(t:Throwable){ out.recycle(); throw t }
    }
    fun watermarkLogo(source:Bitmap,logo:Bitmap,budget:Long,opacityPercent:Int=100):Bitmap {
        MemoryBudget.requireBitmaps(source.width,source.height,2,budget-logo.allocationByteCount.toLong())
        val out=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(out); canvas.drawBitmap(source,0f,0f,null)
            require(opacityPercent in 1..100) { "INVALID_OPACITY" }
            val pad=minOf(16,source.width/20,source.height/20)
            val w=(source.width/4).coerceAtLeast(1)
            val h=(logo.height*w.toFloat()/logo.width).toInt().coerceIn(1,(source.height-pad*2).coerceAtLeast(1))
            canvas.drawBitmap(logo,null,RectF((source.width-w-pad).toFloat(),(source.height-h-pad).toFloat(),(source.width-pad).toFloat(),(source.height-pad).toFloat()),Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha=opacityPercent*255/100 }); return out
        } catch(t:Throwable){ out.recycle(); throw t }
    }
    fun join(bitmaps:List<Bitmap>,layout:String,gapPx:Int,budget:Long,overlapPx:Int=0):Bitmap {
        require(bitmaps.isNotEmpty()){"NO_SOURCES"}
        val gap=gapPx.coerceIn(0,500)
        val overlap=if(layout=="VSTRIP") overlapPx.coerceIn(0,400) else 0
        val (w,h)=when(layout){
            "VSTRIP" -> {
                val totalH=bitmaps.mapIndexed { idx,b -> if(idx==0) b.height else (b.height-overlap).coerceAtLeast(1) }.sum() + gap*(bitmaps.size-1)
                bitmaps.maxOf{it.width} to totalH.coerceAtLeast(1)
            }
            "HSTRIP" -> bitmaps.sumOf{it.width}+gap*(bitmaps.size-1) to bitmaps.maxOf{it.height}
            else -> { val cols=2; val rows=(bitmaps.size+cols-1)/cols; val cw=bitmaps.maxOf{it.width}; val ch=bitmaps.maxOf{it.height}; (cw*cols+gap*(cols-1)) to (ch*rows+gap*(rows-1)) }
        }
        val resident=bitmaps.sumOf { it.allocationByteCount.toLong() }
        MemoryBudget.requireBitmaps(w,h,1,budget-resident)
        val out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(out); canvas.drawColor(Color.WHITE); var x=0; var y=0; var col=0; val cw=bitmaps.maxOf{it.width}; val ch=bitmaps.maxOf{it.height}
            bitmaps.forEachIndexed{ idx,b ->
                when(layout){
                    "VSTRIP" -> {
                        val trimTop=if(idx==0) 0 else overlap.coerceAtMost(b.height-1)
                        val drawH=(b.height-trimTop).coerceAtLeast(1)
                        canvas.drawBitmap(b,Rect(0,trimTop,b.width,b.height),Rect(0,y,b.width,y+drawH),null)
                        y+=drawH+gap
                    }
                    "HSTRIP" -> { canvas.drawBitmap(b,x.toFloat(),y.toFloat(),null); x+=b.width+gap }
                    else -> { canvas.drawBitmap(b,x.toFloat(),y.toFloat(),null); col++; if(col>=2){col=0;x=0;y+=ch+gap}else x+=cw+gap }
                }
            }
            return out
        } catch(t:Throwable){ out.recycle(); throw t }
    }
    fun adjust(source:Bitmap,brightness:Int,contrastPercent:Int,saturationPercent:Int,padPx:Int,budget:Long):Bitmap {
        val pad=padPx.coerceIn(0,2000)
        MemoryBudget.requireBitmaps(source.width+pad*2,source.height+pad*2,2,budget)
        val out=Bitmap.createBitmap(source.width+pad*2,source.height+pad*2,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(out); canvas.drawColor(Color.WHITE)
            val c=contrastPercent.coerceIn(0,300)/100f; val s=saturationPercent.coerceIn(0,300)/100f; val b=brightness.coerceIn(-100,100)*1.27f
            val matrix=ColorMatrix(floatArrayOf(c,0f,0f,0f,b, 0f,c,0f,0f,b, 0f,0f,c,0f,b, 0f,0f,0f,1f,0f))
            matrix.postConcat(ColorMatrix().apply{ setSaturation(s) })
            canvas.drawBitmap(source,pad.toFloat(),pad.toFloat(),Paint(Paint.FILTER_BITMAP_FLAG).apply{ colorFilter=ColorMatrixColorFilter(matrix) }); return out
        } catch(t:Throwable){ out.recycle(); throw t }
    }
    /** Mean absolute per-pixel difference 0..255 between same-size images (else -1). */
    fun meanDifference(a:Bitmap,b:Bitmap):Double {
        if(a.width!=b.width||a.height!=b.height) return -1.0
        // One scanline at a time: comparison must not allocate two full-size pixel arrays.
        val pa=IntArray(a.width); val pb=IntArray(b.width)
        var sum=0L
        for(y in 0 until a.height) {
            a.getPixels(pa,0,a.width,0,y,a.width,1);b.getPixels(pb,0,b.width,0,y,b.width,1)
            for(i in pa.indices) sum+=abs(Color.red(pa[i])-Color.red(pb[i]))+abs(Color.green(pa[i])-Color.green(pb[i]))+abs(Color.blue(pa[i])-Color.blue(pb[i]))+abs(Color.alpha(pa[i])-Color.alpha(pb[i]))
        }
        return sum.toDouble()/a.width/a.height/4.0
    }
}
