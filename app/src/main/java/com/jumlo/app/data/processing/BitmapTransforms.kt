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

/** Metadata for the candidate that was actually encoded, including any size-search changes. */
data class ImageEncodingResult(val format:String,val quality:Int?,val width:Int,val height:Int,val sizeBytes:Long)

data class CodecCapability(
    val format: String,
    val canDecode: Boolean,
    val canEncode: Boolean,
    val canPreserveAnimation: Boolean,
    val supportedApiRange: IntRange,
    val verifiedFixtureIds: List<String>
)

object BitmapTransforms {
    data class ImageInfo(val width: Int, val height: Int, val orientation: Int) {
        val normalizedWidth: Int get() = if (orientation in 5..8) height else width
        val normalizedHeight: Int get() = if (orientation in 5..8) width else height
        val decodePeakBytes: Long get() = ImageWorkPolicy.bytes(width, height) * if (orientation in 2..8) 2 else 1
    }

    fun inspect(file: File): ImageInfo {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) { "UNSUPPORTED_IMAGE_FORMAT" }
        rejectAnimation(file, bounds.outMimeType)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "INVALID_DIMENSIONS" }
        val orientation = try { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } catch (_: java.io.IOException) { 1 }
        return ImageInfo(bounds.outWidth, bounds.outHeight, orientation)
    }

    /** Exact, EXIF-normalized decode. Resource failure is explicit; geometry never silently changes. */
    fun decode(file: File, budget: Long): DecodedImage = decodeAtSample(file, inspect(file), budget, 1)

    /** Explicitly sampled camera/gallery decode for scan-page generation, preserving EXIF orientation. */
    fun decodeSampled(file: File, budget: Long, maxEdge: Int = 3200): DecodedImage {
        require(maxEdge > 0 && budget >= 4L) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        val info = inspect(file)
        var sample = 1
        while (true) {
            val width = sampledDimension(info.width, sample)
            val height = sampledDimension(info.height, sample)
            val copies = if (info.orientation in 2..8) 2 else 1
            val pixels = width.toLong() * height
            if (maxOf(width, height) <= maxEdge && pixels <= minOf(Int.MAX_VALUE / 4L, budget / copies / 4)) break
            require(sample < (1 shl 30)) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
            sample *= 2
        }
        return decodeAtSample(file, info, budget, sample)
    }

    private fun sampledDimension(value: Int, sample: Int) = ((value.toLong() + sample - 1) / sample).toInt().coerceAtLeast(1)

    private fun decodeAtSample(file: File, info: ImageInfo, budget: Long, sample: Int): DecodedImage {
        val width = sampledDimension(info.width, sample)
        val height = sampledDimension(info.height, sample)
        ImageWorkPolicy.requireFits(width, height, if (info.orientation in 2..8) ImageWorkPolicy.bytes(width, height) else 0L, budget)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sample
            inScaled = false
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: throw IllegalArgumentException("IMAGE_DECODE_FAILED")
        try {
            if (sample == 1) require(bitmap.width == info.width && bitmap.height == info.height) { "IMAGE_DECODE_DIMENSIONS_CHANGED" }
            require(bitmap.allocationByteCount.toLong() <= budget) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
            val matrix = Matrix().apply { when (info.orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            } }
            if (info.orientation in 2..8) {
                val normalizedWidth = if (info.orientation in 5..8) bitmap.height else bitmap.width
                val normalizedHeight = if (info.orientation in 5..8) bitmap.width else bitmap.height
                ImageWorkPolicy.requireFits(normalizedWidth, normalizedHeight, bitmap.allocationByteCount.toLong(), budget)
                val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (normalized !== bitmap) bitmap.recycle()
                return DecodedImage(normalized, 1)
            }
            return DecodedImage(bitmap, 1)
        } catch (t: Throwable) { bitmap.recycle(); throw t }
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
        ImageWorkPolicy.requireFits(geometry.width,geometry.height,source.allocationByteCount.toLong(),budget)
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
        val w=(right-x).coerceAtLeast(1); val h=(bottom-y).coerceAtLeast(1)
        if(x==0 && y==0 && w==source.width && h==source.height && turns%4==0 && !flipX && !flipY) return source
        ImageWorkPolicy.requireFits(w,h,source.allocationByteCount.toLong(),budget)
        val matrix=Matrix().apply { setScale(if(flipX) -1f else 1f,if(flipY) -1f else 1f); postRotate(((turns%4+4)%4)*90f) }
        return Bitmap.createBitmap(source,x,y,w,h,matrix,true)
    }
    private fun Double.floorToInt()=floor(this).toInt()
    private fun Double.ceilToInt()=ceil(this).toInt()
    fun encode(bitmap:Bitmap,file:File,settings:ImageConversionSettings,budget:Long) {
        encodeWithResult(bitmap,file,settings,budget)
    }

    fun encodeWithResult(bitmap:Bitmap,file:File,settings:ImageConversionSettings,budget:Long,checkCancel:()->Unit={}):ImageEncodingResult {
        checkCancel()
        require(settings.quality in 1..100) { "INVALID_QUALITY" }
        val format=when(settings.format) {
            "JPEG" -> Bitmap.CompressFormat.JPEG
            "PNG" -> Bitmap.CompressFormat.PNG
            "WEBP" -> if(Build.VERSION.SDK_INT>=30) Bitmap.CompressFormat.WEBP_LOSSY else {
                @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }
            else -> throw IllegalArgumentException("UNSUPPORTED_IMAGE_FORMAT")
        }
        require(bitmap.allocationByteCount.toLong() <= budget) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        if (settings.format=="JPEG" && bitmap.hasAlpha()) ImageWorkPolicy.requireFits(bitmap.width,bitmap.height,bitmap.allocationByteCount.toLong(),budget)
        var matte:Bitmap?=null
        try {
            val output=if(settings.format=="JPEG" && bitmap.hasAlpha()) {
                Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888).also {
                    matte=it; Canvas(it).apply { drawColor(settings.matteArgb or (0xff shl 24)); drawBitmap(bitmap,0f,0f,null) }
                }
            } else bitmap
            checkCancel()
            FileOutputStream(file).use { stream ->
                val cancellable=object:java.io.OutputStream() {
                    override fun write(value:Int) {checkCancel();stream.write(value);checkCancel()}
                    override fun write(bytes:ByteArray,offset:Int,length:Int) {checkCancel();stream.write(bytes,offset,length);checkCancel()}
                    override fun flush() {stream.flush()}
                }
                val compressed=output.compress(format,settings.quality,cancellable)
                checkCancel()
                require(compressed) { "IMAGE_ENCODE_FAILED" }
                stream.fd.sync()
            }
            val check=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(file.path,check)
            require(check.outWidth==bitmap.width && check.outHeight==bitmap.height) { "IMAGE_VERIFY_FAILED" }
            checkCancel()
            return ImageEncodingResult(settings.format,settings.quality.takeUnless {settings.format=="PNG"},bitmap.width,bitmap.height,file.length())
        } finally { matte?.recycle() }
    }

    /**
     * Guarantees that [file] is strictly smaller than [maxAllowedBytes] (e.g., original file size).
     * Progressively steps down quality and, if needed, scales dimensions down so compression
     * never produces an equal-size or larger output file.
     */
    fun encodeGuaranteedSmaller(
        bitmap: Bitmap,
        file: File,
        maxAllowedBytes: Long,
        preferredSettings: ImageConversionSettings,
        budget: Long,
        checkCancel: () -> Unit = {}
    ): String = encodeGuaranteedSmallerWithResult(bitmap,file,maxAllowedBytes,preferredSettings,budget,checkCancel).format

    fun encodeGuaranteedSmallerWithResult(
        bitmap: Bitmap,
        file: File,
        maxAllowedBytes: Long,
        preferredSettings: ImageConversionSettings,
        budget: Long,
        checkCancel: () -> Unit = {}
    ): ImageEncodingResult {
        require(maxAllowedBytes > 1L) { "INVALID_IMAGE_SIZE_BOUND" }
        val effectiveFormat = if (preferredSettings.format == "PNG") "WEBP" else preferredSettings.format
        var smallest = Long.MAX_VALUE
        val candidate = File.createTempFile("compress-step-", ".tmp", file.parentFile)
        try {
            checkCancel()
            try {
                val encoded=encodeWithResult(bitmap, candidate, preferredSettings, budget,checkCancel)
                smallest = candidate.length()
                if (ImageWorkPolicy.meetsStrictBound(smallest, maxAllowedBytes)) {
                    candidate.copyTo(file, overwrite = true)
                    checkCancel()
                    return encoded
                }
            } catch(e: IllegalArgumentException) {
                if(e.message != "IMAGE_MEMORY_BUDGET_EXCEEDED") throw e
            }
            // Every pass stays at or below the caller's requested quality. Dimension
            // reduction is explicit behavior of Compress/Target Size only.
            val qualities = ImageWorkPolicy.qualities(preferredSettings.quality)
            val seenDimensions = mutableSetOf<Pair<Int, Int>>()
            var scale = 1.0
            while (true) {
                checkCancel()
                val sw = maxOf(1, (bitmap.width * scale).roundToInt())
                val sh = maxOf(1, (bitmap.height * scale).roundToInt())
                val sameSize=sw==bitmap.width && sh==bitmap.height
                val resident=if(sameSize) 0L else bitmap.allocationByteCount.toLong()
                val required=ImageWorkPolicy.bytes(sw,sh)*(if(effectiveFormat=="JPEG" && bitmap.hasAlpha()) 2L else 1L)
                if (seenDimensions.add(sw to sh) && resident<=budget && required<=budget-resident) {
                    val scaled = if (sw == bitmap.width && sh == bitmap.height) bitmap else {
                        ImageWorkPolicy.requireFits(sw, sh, bitmap.allocationByteCount.toLong(), budget)
                        Bitmap.createScaledBitmap(bitmap, sw, sh, true)
                    }
                    try {
                        val encodeBudget = budget - if (scaled !== bitmap) bitmap.allocationByteCount.toLong() else 0L
                        for (quality in qualities) {
                            checkCancel()
                            val encoded=encodeWithResult(scaled, candidate, preferredSettings.copy(format = effectiveFormat, quality = quality), encodeBudget,checkCancel)
                            smallest = minOf(smallest, candidate.length())
                            if (ImageWorkPolicy.meetsStrictBound(candidate.length(), maxAllowedBytes)) {
                                candidate.copyTo(file, overwrite = true)
                                checkCancel()
                                return encoded
                            }
                        }
                    } finally { if (scaled !== bitmap) scaled.recycle() }
                }
                if (sw == 1 && sh == 1) break
                scale *= 0.70
            }
            file.delete() // Never leave an over-bound candidate available for publication.
            require(smallest != Long.MAX_VALUE) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
            throw ImageSizeBoundException(maxAllowedBytes - 1L, smallest)
        } finally { candidate.delete() }
    }

    fun extension(format:String)=when(format) { "JPEG" -> "jpg"; "PNG" -> "png"; "WEBP" -> "webp"; else -> throw IllegalArgumentException("UNSUPPORTED_IMAGE_FORMAT") }
    fun mime(format:String)=when(format) { "JPEG" -> "image/jpeg"; "PNG" -> "image/png"; "WEBP" -> "image/webp"; else -> throw IllegalArgumentException("UNSUPPORTED_IMAGE_FORMAT") }
}

/** Expansion transforms: watermark, join/stitch, adjustments, codec matrix. */
object BitmapExtras {
    /** Bounded transparent text raster shared by the interactive studio and final image export. */
    fun watermarkTextBitmap(text:String,fontFamily:String,colorHex:String,budget:Long=2L*1024*1024):Bitmap {
        OverlayStylePolicy.requireFont(fontFamily);OverlayStylePolicy.requireColor(colorHex)
        val clean=text.trim().replace(Regex("\\s+")," ")
        require(clean.isNotEmpty() && clean.length<=512) { "WATERMARK_CONTENT_REQUIRED" }
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            textSize=84f
            typeface=Typeface.create(when(fontFamily) {"serif"->"serif";"mono"->"monospace";else->"sans-serif"},Typeface.BOLD)
            color=Color.parseColor(colorHex)
        }
        val measured=paint.measureText(clean)
        require(measured.isFinite() && measured>0) { "WATERMARK_CONTENT_REQUIRED" }
        if(measured>1650f) paint.textSize*=1650f/measured
        val metrics=paint.fontMetrics
        val width=(paint.measureText(clean)+48f).toInt().coerceIn(64,1710)
        val height=(metrics.descent-metrics.ascent+36f).toInt().coerceIn(80,220)
        ImageWorkPolicy.requireFits(width,height,0L,budget)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {Canvas(bitmap).drawText(clean,24f,18f-metrics.ascent,paint);return bitmap}
        catch(failure:Throwable) {bitmap.recycle();throw failure}
    }

    /** Applies the exact shared placement geometry; only one output and one overlay are resident. */
    fun watermark(source:Bitmap,logo:Bitmap?,settings:ImageWatermarkSettings,budget:Long):Bitmap {
        require(settings.opacityPercent in 1..100 && settings.rotationDegrees in -180..180) { "INVALID_OPACITY" }
        require(settings.useLogo==(logo!=null)) { "WATERMARK_LOGO_REQUIRED" }
        OverlayStylePolicy.requireFont(settings.fontFamily);OverlayStylePolicy.requireColor(settings.colorHex)
        val centers=OverlayStylePolicy.centers(settings.centerX,settings.centerY,settings.tiled)
        val sourceBytes=source.allocationByteCount.toLong()
        val outputBytes=ImageWorkPolicy.bytes(source.width,source.height)
        val overlay=logo ?: watermarkTextBitmap(settings.text,settings.fontFamily,settings.colorHex,budget-sourceBytes-outputBytes)
        try {
            ImageWorkPolicy.requireFits(source.width,source.height,sourceBytes+overlay.allocationByteCount.toLong(),budget)
            val size=OverlayStylePolicy.size(source.width.toDouble(),source.height.toDouble(),overlay.width.toDouble(),overlay.height.toDouble(),settings.scale)
            val width=size.first.toFloat();val height=size.second.toFloat()
            val output=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
            try {
                val canvas=Canvas(output);canvas.drawBitmap(source,0f,0f,null)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {alpha=settings.opacityPercent*255/100}
                centers.forEach {(x,y) ->
                    canvas.save()
                    try {
                        canvas.translate((x*source.width).toFloat(),(y*source.height).toFloat())
                        canvas.rotate(settings.rotationDegrees.toFloat())
                        canvas.drawBitmap(overlay,null,RectF(-width/2,-height/2,width/2,height/2),paint)
                    } finally {canvas.restore()}
                }
                return output
            } catch(failure:Throwable) {output.recycle();throw failure}
        } finally {if(logo==null) overlay.recycle()}
    }

    fun codecCapabilities(sdkInt: Int = Build.VERSION.SDK_INT): List<CodecCapability> = listOf(
        CodecCapability("JPEG", canDecode = true, canEncode = true, canPreserveAnimation = false, supportedApiRange = 26..36, verifiedFixtureIds = emptyList()),
        CodecCapability("PNG", canDecode = true, canEncode = true, canPreserveAnimation = false, supportedApiRange = 26..36, verifiedFixtureIds = emptyList()),
        CodecCapability("WEBP", canDecode = true, canEncode = true, canPreserveAnimation = false, supportedApiRange = 26..36, verifiedFixtureIds = emptyList()),
        CodecCapability("HEIC", canDecode = sdkInt >= 28, canEncode = false, canPreserveAnimation = false, supportedApiRange = 28..36, verifiedFixtureIds = emptyList())
    )

    fun watermarkText(source:Bitmap,text:String,opacityPercent:Int,budget:Long):Bitmap {
        ImageWorkPolicy.requireFits(source.width,source.height,source.allocationByteCount.toLong(),budget)
        val out=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(out); canvas.drawBitmap(source,0f,0f,null)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{ color=Color.WHITE; alpha=(opacityPercent.coerceIn(1,100)*255/100); textSize=(source.width/12f).coerceAtLeast(18f); isFakeBoldText=true; setShadowLayer(4f,2f,2f,Color.BLACK) }
            canvas.drawText(text.ifBlank { "Jumlo" },source.width*0.08f,source.height*0.92f,paint); return out
        } catch(t:Throwable){ out.recycle(); throw t }
    }
    fun watermarkLogo(source:Bitmap,logo:Bitmap,budget:Long,opacityPercent:Int=100):Bitmap {
        ImageWorkPolicy.requireFits(source.width,source.height,source.allocationByteCount.toLong()+logo.allocationByteCount.toLong(),budget)
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
        val plan = ImageJoinPolicy.plan(bitmaps.map { it.width to it.height },layout,gapPx,overlapPx)
        ImageWorkPolicy.requireFits(plan.width,plan.height,bitmaps.sumOf { it.allocationByteCount.toLong() },budget)
        val out=Bitmap.createBitmap(plan.width,plan.height,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(out); canvas.drawColor(Color.WHITE)
            bitmaps.forEachIndexed { index, bitmap -> drawJoinPart(canvas,bitmap,plan.placements[index]) }
            return out
        } catch(t:Throwable) { out.recycle(); throw t }
    }

    fun drawJoinPart(canvas: Canvas, bitmap: Bitmap, placement: ImagePlacement) {
        canvas.drawBitmap(bitmap,Rect(0,placement.trimTop,bitmap.width,bitmap.height),
            Rect(placement.x,placement.y,placement.x+bitmap.width,placement.y+bitmap.height-placement.trimTop),null)
    }
    fun adjust(source:Bitmap,brightness:Int,contrastPercent:Int,saturationPercent:Int,padPx:Int,budget:Long):Bitmap {
        val pad=padPx.coerceIn(0,2000)
        require(source.width <= Int.MAX_VALUE-pad*2 && source.height <= Int.MAX_VALUE-pad*2) { "IMAGE_DIMENSIONS_UNREPRESENTABLE" }
        ImageWorkPolicy.requireFits(source.width+pad*2,source.height+pad*2,source.allocationByteCount.toLong(),budget)
        val out=Bitmap.createBitmap(source.width+pad*2,source.height+pad*2,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(out); canvas.drawColor(Color.WHITE)
            val c=contrastPercent.coerceIn(0,300)/100f; val s=saturationPercent.coerceIn(0,300)/100f; val b=brightness.coerceIn(-100,100)*1.27f
            val matrix=ColorMatrix(floatArrayOf(c,0f,0f,0f,b, 0f,c,0f,0f,b, 0f,0f,c,0f,b, 0f,0f,0f,1f,0f))
            matrix.postConcat(ColorMatrix().apply{ setSaturation(s) })
            canvas.drawBitmap(source,pad.toFloat(),pad.toFloat(),Paint(Paint.FILTER_BITMAP_FLAG).apply{ colorFilter=ColorMatrixColorFilter(matrix) }); return out
        } catch(t:Throwable){ out.recycle(); throw t }
    }
    /** Mean absolute per-pixel difference 0..255 between same-size images (else -1). */
    fun meanDifference(a:Bitmap,b:Bitmap,budget:Long=Long.MAX_VALUE):Double {
        if(a.width!=b.width||a.height!=b.height) return -1.0
        val resident=a.allocationByteCount.toLong()+b.allocationByteCount.toLong()
        require(resident<=budget && a.width.toLong()*8L<=budget-resident) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        val pa=IntArray(a.width); val pb=IntArray(b.width)
        var sum=0L
        for(y in 0 until a.height) {
            a.getPixels(pa,0,a.width,0,y,a.width,1);b.getPixels(pb,0,b.width,0,y,b.width,1)
            for(i in pa.indices) sum+=abs(Color.red(pa[i])-Color.red(pb[i]))+abs(Color.green(pa[i])-Color.green(pb[i]))+abs(Color.blue(pa[i])-Color.blue(pb[i]))+abs(Color.alpha(pa[i])-Color.alpha(pb[i]))
        }
        return sum.toDouble()/a.width/a.height/4.0
    }
    /**
     * Visual difference map: identical pixels become grey, changed pixels become the
     * accent red. Returns null when the images differ in size or the map does not fit
     * the memory budget, so the text report is still produced.
     */
    fun diffMap(a:Bitmap,b:Bitmap,budget:Long):Bitmap? {
        if(a.width!=b.width||a.height!=b.height) return null
        val resident=a.allocationByteCount.toLong()+b.allocationByteCount.toLong()
        val outBytes=ImageWorkPolicy.bytes(a.width,a.height)
        if(resident+outBytes>budget) return null
        val out=Bitmap.createBitmap(a.width,a.height,Bitmap.Config.ARGB_8888)
        try {
            val rowA=IntArray(a.width); val rowB=IntArray(b.width); val rowOut=IntArray(a.width)
            for(y in 0 until a.height) {
                a.getPixels(rowA,0,a.width,0,y,a.width,1)
                b.getPixels(rowB,0,b.width,0,y,b.width,1)
                for(x in 0 until a.width) {
                    val pa=rowA[x]; val pb=rowB[x]
                    val delta=abs(Color.red(pa)-Color.red(pb))+abs(Color.green(pa)-Color.green(pb))+abs(Color.blue(pa)-Color.blue(pb))+abs(Color.alpha(pa)-Color.alpha(pb))
                    rowOut[x]=if(delta==0) {
                        val grey=(Color.red(pa)*30+Color.green(pa)*59+Color.blue(pa)*11)/100
                        Color.argb(255,grey,grey,grey)
                    } else Color.argb(255,255,32,96)
                }
                out.setPixels(rowOut,0,a.width,0,y,a.width,1)
            }
            return out
        } catch(t:Throwable) { out.recycle(); throw t }
    }
}
