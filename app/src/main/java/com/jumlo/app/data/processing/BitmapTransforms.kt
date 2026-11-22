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
        val orientation = try { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } catch (_: Exception) { 1 }
        return ImageInfo(bounds.outWidth, bounds.outHeight, orientation)
    }

    /** Exact, EXIF-normalized decode. Resource failure is explicit; geometry never silently changes. */
    fun decode(file: File, budget: Long): DecodedImage = decodeAtSample(file, inspect(file), budget, 1)

    /** Explicitly sampled camera/gallery decode, preserving EXIF orientation and retrying at 2x sample on OOM. */
    fun decodeSampled(file: File, budget: Long, maxEdge: Int = 3200): DecodedImage {
        require(maxEdge > 0 && budget >= 4L) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
        val info = inspect(file)
        var sample = computeSampleSize(info.width, info.height, if (info.orientation in 2..8) 2 else 1, budget, maxEdge)
        var lastOom: OutOfMemoryError? = null
        for (attempt in 0 until 4) {
            try {
                return decodeAtSample(file, info, budget, sample)
            } catch (oom: OutOfMemoryError) {
                lastOom = oom
                if (sample >= (1 shl 29)) throw oom
                sample *= 2
                System.gc()
            }
        }
        throw lastOom ?: OutOfMemoryError("Sampled image decode exceeded available memory")
    }

    /**
     * Decodes only the requested normalized [box] using [BitmapRegionDecoder] so cropping a 50 MP
     * photo never decodes the full image into RAM first. Falls back to [decodeSampled] + [crop]
     * if the codec/container is not supported by [BitmapRegionDecoder].
     */
    fun decodeCroppedRegion(
        file: File,
        box: Box,
        turns: Int,
        flipX: Boolean,
        flipY: Boolean,
        budget: Long,
        maxEdge: Int = ImageWorkPolicy.MAX_EDGE_PX
    ): DecodedImage {
        box.normalized()
        val info = inspect(file)
        val isFullFrame = box.left <= 0.0 && box.top <= 0.0 && box.right >= 1.0 && box.bottom >= 1.0
        if (!isFullFrame) {
            val rawRect = mapNormalizedBoxToRawRect(box, info.width, info.height, info.orientation)
            val regionW = rawRect.width().coerceAtLeast(1)
            val regionH = rawRect.height().coerceAtLeast(1)
            val needsTransform = info.orientation in 2..8 || turns % 4 != 0 || flipX || flipY
            val copies = if (needsTransform) 2 else 1
            val decoder = openRegionDecoder(file)
            if (decoder != null) {
                try {
                    var sample = computeSampleSize(regionW, regionH, copies, budget, maxEdge)
                    var lastOom: OutOfMemoryError? = null
                    for (attempt in 0 until 4) {
                        var region: Bitmap? = null
                        try {
                            val opts = BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                inSampleSize = sample
                                inScaled = false
                                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                            }
                            val decoded = decoder.decodeRegion(rawRect, opts)
                                ?: throw IllegalArgumentException("IMAGE_DECODE_FAILED")
                            region = decoded
                            if (!needsTransform) {
                                region = null
                                return DecodedImage(decoded, 1)
                            }
                            val matrix = exifMatrix(info.orientation).apply {
                                if (flipX || flipY) postScale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
                                val rot = ((turns % 4 + 4) % 4) * 90f
                                if (rot != 0f) postRotate(rot)
                            }
                            val opaque = !decoded.hasAlpha()
                            val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                            if (opaque) transformed.setHasAlpha(false)
                            if (transformed !== decoded && !decoded.isRecycled) decoded.recycle()
                            region = null
                            return DecodedImage(transformed, 1)
                        } catch (oom: OutOfMemoryError) {
                            region?.let { if (!it.isRecycled) it.recycle() }
                            lastOom = oom
                            if (sample >= (1 shl 29)) throw oom
                            sample *= 2
                            System.gc()
                        } catch (t: Throwable) {
                            region?.let { if (!it.isRecycled) it.recycle() }
                            throw t
                        }
                    }
                    throw lastOom ?: OutOfMemoryError("Cropped region decode exceeded available memory")
                } finally {
                    if (!decoder.isRecycled) decoder.recycle()
                }
            }
        }
        // Fallback for full-frame rotations/flips or codecs without BitmapRegionDecoder support.
        val decodeBudget = (budget / 2L).coerceAtLeast(4L * 1024 * 1024)
        val decoded = if (info.decodePeakBytes <= decodeBudget && maxOf(info.normalizedWidth, info.normalizedHeight) <= maxEdge) {
            decode(file, decodeBudget)
        } else {
            decodeSampled(file, decodeBudget, maxEdge)
        }
        return try {
            val cropped = crop(decoded.bitmap, box, turns, flipX, flipY, budget)
            if (cropped !== decoded.bitmap && !decoded.bitmap.isRecycled) decoded.bitmap.recycle()
            DecodedImage(cropped, 1)
        } catch (t: Throwable) {
            decoded.close()
            throw t
        }
    }

    private fun openRegionDecoder(file: File): BitmapRegionDecoder? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(file.path)
        else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(file.path, false)
    }.getOrNull()

    private fun mapNormalizedBoxToRawRect(box: Box, rawW: Int, rawH: Int, orientation: Int): Rect {
        val l = box.left; val t = box.top; val r = box.right; val b = box.bottom
        val (rl, rt, rr, rb) = when (orientation) {
            2 -> doubleArrayOf(1.0 - r, t, 1.0 - l, b)
            3 -> doubleArrayOf(1.0 - r, 1.0 - b, 1.0 - l, 1.0 - t)
            4 -> doubleArrayOf(l, 1.0 - b, r, 1.0 - t)
            5 -> doubleArrayOf(t, l, b, r)
            6 -> doubleArrayOf(t, 1.0 - r, b, 1.0 - l)
            7 -> doubleArrayOf(1.0 - b, 1.0 - r, 1.0 - t, 1.0 - l)
            8 -> doubleArrayOf(1.0 - b, l, 1.0 - t, r)
            else -> doubleArrayOf(l, t, r, b)
        }
        val left = (rl * rawW).floorToInt().coerceIn(0, rawW - 1)
        val top = (rt * rawH).floorToInt().coerceIn(0, rawH - 1)
        val right = (rr * rawW).ceilToInt().coerceIn(left + 1, rawW)
        val bottom = (rb * rawH).ceilToInt().coerceIn(top + 1, rawH)
        return Rect(left, top, right, bottom)
    }

    private fun computeSampleSize(width: Int, height: Int, copies: Int, budget: Long, maxEdge: Int): Int {
        var sample = 1
        while (true) {
            val w = sampledDimension(width, sample)
            val h = sampledDimension(height, sample)
            val pixels = w.toLong() * h
            if (maxOf(w, h) <= maxEdge && pixels <= minOf(Int.MAX_VALUE / 4L, (budget / copies / 4L).coerceAtLeast(1L))) return sample
            require(sample < (1 shl 30)) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
            sample *= 2
        }
    }

    private fun exifMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(-90f); postScale(-1f, 1f) }
            8 -> setRotate(-90f)
        }
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
            if (info.orientation in 2..8) {
                val matrix = exifMatrix(info.orientation)
                val normalizedWidth = if (info.orientation in 5..8) bitmap.height else bitmap.width
                val normalizedHeight = if (info.orientation in 5..8) bitmap.width else bitmap.height
                val opaque = !bitmap.hasAlpha()
                ImageWorkPolicy.requireFits(normalizedWidth, normalizedHeight, bitmap.allocationByteCount.toLong(), budget)
                val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (opaque) normalized.setHasAlpha(false)
                if (normalized !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                return DecodedImage(normalized, 1)
            }
            return DecodedImage(bitmap, 1)
        } catch (t: Throwable) { if (!bitmap.isRecycled) bitmap.recycle(); throw t }
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
                if (mime == "image/png" && kind == "IEND") break
                at+=step
            }
        }
    }
    fun resize(source:Bitmap,w:Int,h:Int,mode:FitMode,upscale:Boolean,matte:Int,budget:Long):Bitmap {
        var targetW = w
        var targetH = h
        var lastOom: OutOfMemoryError? = null
        for (attempt in 0 until 4) {
            val geometry=ImageGeometry.resolve(source.width,source.height,targetW,targetH,mode,upscale)
            ImageWorkPolicy.requireFits(geometry.width,geometry.height,source.allocationByteCount.toLong(),budget)
            var out: Bitmap? = null
            try {
                val created = Bitmap.createBitmap(geometry.width,geometry.height,Bitmap.Config.ARGB_8888)
                out = created
                val canvas=Canvas(created); if(mode==FitMode.PAD) canvas.drawColor(matte)
                val c=geometry.crop; val d=geometry.draw
                val sourceRect=Rect((c.left*source.width).roundToInt(),(c.top*source.height).roundToInt(),(c.right*source.width).roundToInt(),(c.bottom*source.height).roundToInt())
                canvas.drawBitmap(source,sourceRect,RectF(d.left.toFloat(),d.top.toFloat(),d.right.toFloat(),d.bottom.toFloat()),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                if (!source.hasAlpha() || (mode == FitMode.PAD && Color.alpha(matte) == 255)) created.setHasAlpha(false)
                return created
            } catch(oom: OutOfMemoryError) {
                out?.let { if (!it.isRecycled) it.recycle() }
                lastOom = oom
                targetW = (targetW * 0.75).roundToInt().coerceAtLeast(1)
                targetH = (targetH * 0.75).roundToInt().coerceAtLeast(1)
                System.gc()
            } catch(t:Throwable) {
                out?.let { if (!it.isRecycled) it.recycle() }
                throw t
            }
        }
        throw lastOom ?: OutOfMemoryError("Image resize exceeded available memory")
    }
    fun crop(source:Bitmap,box:Box,turns:Int,flipX:Boolean,flipY:Boolean,budget:Long):Bitmap {
        box.normalized()
        val x=(box.left*source.width).floorToInt().coerceIn(0,source.width-1); val y=(box.top*source.height).floorToInt().coerceIn(0,source.height-1)
        val right=(box.right*source.width).ceilToInt().coerceIn(x+1,source.width); val bottom=(box.bottom*source.height).ceilToInt().coerceIn(y+1,source.height)
        val w=right-x; val h=bottom-y
        if(x==0 && y==0 && w==source.width && h==source.height && turns%4==0 && !flipX && !flipY) return source
        ImageWorkPolicy.requireFits(w,h,source.allocationByteCount.toLong(),budget)
        var scale = 1f
        var lastOom: OutOfMemoryError? = null
        for (attempt in 0 until 4) {
            try {
                val matrix=Matrix().apply {
                    setScale(if(flipX) -scale else scale,if(flipY) -scale else scale)
                    postRotate(((turns%4+4)%4)*90f)
                }
                return Bitmap.createBitmap(source,x,y,w,h,matrix,true).also {
                    if (!source.hasAlpha()) it.setHasAlpha(false)
                }
            } catch (oom: OutOfMemoryError) {
                lastOom = oom
                scale *= 0.75f
                System.gc()
            }
        }
        throw lastOom ?: OutOfMemoryError("Image crop exceeded available memory")
    }
    private fun Double.floorToInt()=floor(this).toInt()
    private fun Double.ceilToInt()=ceil(this).toInt()
    fun encode(bitmap:Bitmap,file:File,settings:ImageConversionSettings,budget:Long,checkCancel:()->Unit={}) {
        encodeWithResult(bitmap,file,settings,budget,checkCancel)
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
                    matte=it
                    Canvas(it).apply { drawColor(settings.matteArgb or (0xff shl 24)); drawBitmap(bitmap,0f,0f,null) }
                    it.setHasAlpha(false)
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
        } finally { matte?.let { if (!it.isRecycled) it.recycle() } }
    }

    /**
     * Guarantees that [file] is strictly smaller than [maxAllowedBytes] (e.g., original file size).
     * Progressively steps down quality and, if needed, scales dimensions down so compression
     * never produces an equal-size or larger output file.
     */
    fun compressToTarget(
        bitmap: Bitmap,
        destination: File,
        settings: ImageConversionSettings,
        targetBytes: Long,
        maxBitmapBytes: Long,
        checkCancel: () -> Unit = {}
    ): Long {
        checkCancel()
        val effectiveFmt = if (settings.format.uppercase() == "PNG") "WEBP" else settings.format
        val base = settings.copy(format = effectiveFmt)
        var low = 12
        var high = base.quality.coerceIn(12, 95)
        var bestFile: File? = null
        var bestSize = Long.MAX_VALUE
        val tmp = File(destination.parentFile, ".cmp-${java.util.UUID.randomUUID()}.tmp")
        try {
            // Binary search quality first (max 5 passes) to hit targetBytes cleanly.
            var attempts = 0
            while (low <= high && attempts < 5) {
                checkCancel()
                attempts++
                val mid = (low + high) / 2
                encode(bitmap, tmp, base.copy(quality = mid), maxBitmapBytes, checkCancel)
                val sz = tmp.length()
                if (sz < bestSize) {
                    bestSize = sz
                    tmp.copyTo(destination, overwrite = true)
                    bestFile = destination
                }
                if (sz <= targetBytes) {
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            if (bestSize > targetBytes && bitmap.width > 640 && bitmap.height > 640) {
                checkCancel()
                val ratio = kotlin.math.sqrt(targetBytes.toDouble() / bestSize.toDouble()).toFloat().coerceIn(0.45f, 0.90f)
                val nw = (bitmap.width * ratio).toInt().coerceAtLeast(480)
                val nh = (bitmap.height * ratio).toInt().coerceAtLeast(480)
                val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true).also {
                    if (!bitmap.hasAlpha()) it.setHasAlpha(false)
                }
                try {
                    encode(scaled, tmp, base.copy(quality = base.quality.coerceIn(25, 80)), maxBitmapBytes, checkCancel)
                    val sz = tmp.length()
                    if (sz < bestSize) {
                        bestSize = sz
                        tmp.copyTo(destination, overwrite = true)
                        bestFile = destination
                    }
                } finally {
                    if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
                }
            }
            if (bestFile == null || !destination.exists() || destination.length() <= 0L) {
                encode(bitmap, destination, base, maxBitmapBytes, checkCancel)
            }
            return destination.length()
        } finally {
            tmp.delete()
        }
    }

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
                    moveSynced(candidate, file)
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
                        Bitmap.createScaledBitmap(bitmap, sw, sh, true).also {
                            if (!bitmap.hasAlpha()) it.setHasAlpha(false)
                        }
                    }
                    try {
                        val encodeBudget = budget - if (scaled !== bitmap) bitmap.allocationByteCount.toLong() else 0L
                        for (quality in qualities) {
                            checkCancel()
                            val encoded=encodeWithResult(scaled, candidate, preferredSettings.copy(format = effectiveFormat, quality = quality), encodeBudget,checkCancel)
                            smallest = minOf(smallest, candidate.length())
                            if (ImageWorkPolicy.meetsStrictBound(candidate.length(), maxAllowedBytes)) {
                                moveSynced(candidate, file)
                                checkCancel()
                                return encoded
                            }
                        }
                    } finally { if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle() }
                }
                if (sw == 1 && sh == 1) break
                scale *= 0.70
            }
            file.delete() // Never leave an over-bound candidate available for publication.
            require(smallest != Long.MAX_VALUE) { "IMAGE_MEMORY_BUDGET_EXCEEDED" }
            throw ImageSizeBoundException(maxAllowedBytes - 1L, smallest)
        } finally { candidate.delete() }
    }

    private fun moveSynced(source: File, destination: File) {
        try {
            java.nio.file.Files.move(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun extension(format:String)=when(format) { "JPEG" -> "jpg"; "PNG" -> "png"; "WEBP" -> "webp"; else -> throw IllegalArgumentException("UNSUPPORTED_IMAGE_FORMAT") }
    fun mime(format:String)=when(format) { "JPEG" -> "image/jpeg"; "PNG" -> "image/png"; "WEBP" -> "image/webp"; else -> throw IllegalArgumentException("UNSUPPORTED_IMAGE_FORMAT") }
}

/** Expansion transforms: watermark, join/stitch, adjustments, codec matrix. */
object BitmapExtras {
    private val WHITESPACE_REGEX=Regex("\\s+")
    /** Bounded transparent text raster shared by the interactive studio and final image export. */
    fun watermarkTextBitmap(text:String,fontFamily:String,colorHex:String,budget:Long=2L*1024*1024):Bitmap {
        OverlayStylePolicy.requireFont(fontFamily);OverlayStylePolicy.requireColor(colorHex)
        val clean=text.trim().replace(WHITESPACE_REGEX," ")
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
        catch(failure:Throwable) {if (!bitmap.isRecycled) bitmap.recycle();throw failure}
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
                if (!source.hasAlpha()) output.setHasAlpha(false)
                return output
            } catch(failure:Throwable) {if (!output.isRecycled) output.recycle();throw failure}
        } finally {if(logo==null && !overlay.isRecycled) overlay.recycle()}
    }

    fun watermarkText(source:Bitmap,text:String,opacityPercent:Int,budget:Long):Bitmap {
        ImageWorkPolicy.requireFits(source.width,source.height,source.allocationByteCount.toLong(),budget)
        val out=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(out); canvas.drawBitmap(source,0f,0f,null)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{ color=Color.WHITE; alpha=(opacityPercent.coerceIn(1,100)*255/100); textSize=(source.width/12f).coerceAtLeast(18f); isFakeBoldText=true; setShadowLayer(4f,2f,2f,Color.BLACK) }
            canvas.drawText(text.ifBlank { "Jumlo" },source.width*0.08f,source.height*0.92f,paint)
            if (!source.hasAlpha()) out.setHasAlpha(false)
            return out
        } catch(t:Throwable){ if (!out.isRecycled) out.recycle(); throw t }
    }
    fun watermarkLogo(source:Bitmap,logo:Bitmap,budget:Long,opacityPercent:Int=100):Bitmap {
        ImageWorkPolicy.requireFits(source.width,source.height,source.allocationByteCount.toLong()+logo.allocationByteCount.toLong(),budget)
        val out=Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        try { val canvas=Canvas(out); canvas.drawBitmap(source,0f,0f,null)
            require(opacityPercent in 1..100) { "INVALID_OPACITY" }
            val pad=minOf(16,source.width/20,source.height/20)
            val w=(source.width/4).coerceAtLeast(1)
            val h=(logo.height*w.toFloat()/logo.width).toInt().coerceIn(1,(source.height-pad*2).coerceAtLeast(1))
            canvas.drawBitmap(logo,null,RectF((source.width-w-pad).toFloat(),(source.height-h-pad).toFloat(),(source.width-pad).toFloat(),(source.height-pad).toFloat()),Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha=opacityPercent*255/100 })
            if (!source.hasAlpha()) out.setHasAlpha(false)
            return out
        } catch(t:Throwable){ if (!out.isRecycled) out.recycle(); throw t }
    }
    fun join(bitmaps:List<Bitmap>,layout:String,gapPx:Int,budget:Long,overlapPx:Int=0):Bitmap {
        val plan = ImageJoinPolicy.plan(bitmaps.map { it.width to it.height },layout,gapPx,overlapPx)
        ImageWorkPolicy.requireFits(plan.width,plan.height,bitmaps.sumOf { it.allocationByteCount.toLong() },budget)
        val out=Bitmap.createBitmap(plan.width,plan.height,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(out); canvas.drawColor(Color.WHITE)
            out.setHasAlpha(false)
            bitmaps.forEachIndexed { index, bitmap -> drawJoinPart(canvas,bitmap,plan.placements[index]) }
            return out
        } catch(t:Throwable) { if (!out.isRecycled) out.recycle(); throw t }
    }

    fun drawJoinPart(canvas: Canvas, bitmap: Bitmap, placement: ImagePlacement) {
        canvas.drawBitmap(bitmap,Rect(0,placement.trimTop,bitmap.width,bitmap.height),
            Rect(placement.x,placement.y,placement.x+bitmap.width,placement.y+bitmap.height-placement.trimTop),null)
    }

    fun drawJoinPartScaled(
        canvas: Canvas,
        bitmap: Bitmap,
        srcTrimTop: Int,
        dstRect: Rect,
        paint: Paint
    ) {
        val clampedTrim = srcTrimTop.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
        canvas.drawBitmap(bitmap, Rect(0, clampedTrim, bitmap.width, bitmap.height), dstRect, paint)
    }
}
