package com.jumlo.app.data.processing

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tflite.java.TfLite
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.floor

/**
 * Google Play Services TFLite runner executing MediaPipe's quantized selfie segmenter
 * (`assets/ml/selfie_segmenter.tflite`, 256x256 float16) via system Play Services XNNPACK
 * without bundling heavy C++ native `.so` runtimes in the APK.
 */
@Singleton
class LiteRtPortraitSegmenter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: InterpreterApi? = null
    private var unavailable = false
    private val lock = Any()

    suspend fun segmentMask(bitmap: Bitmap): FloatArray? {
        if (unavailable || bitmap.isRecycled) return null
        val engine = getOrCreateInterpreter() ?: return null
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        return try {
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            val inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
                .order(ByteOrder.nativeOrder())
            for (c in pixels) {
                inputBuffer.putFloat(((c ushr 16) and 0xFF) / 255f)
                inputBuffer.putFloat(((c ushr 8) and 0xFF) / 255f)
                inputBuffer.putFloat((c and 0xFF) / 255f)
            }
            inputBuffer.rewind()

            val outputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 4)
                .order(ByteOrder.nativeOrder())

            synchronized(lock) {
                engine.run(inputBuffer, outputBuffer)
            }
            outputBuffer.rewind()
            val smallMask = FloatArray(INPUT_SIZE * INPUT_SIZE)
            outputBuffer.asFloatBuffer().get(smallMask)
            upsampleMaskBilinear(smallMask, INPUT_SIZE, INPUT_SIZE, bitmap.width, bitmap.height)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        } finally {
            if (resized !== bitmap && !resized.isRecycled) resized.recycle()
        }
    }

    fun close() {
        synchronized(lock) {
            runCatching { interpreter?.close() }
            interpreter = null
        }
    }

    private suspend fun getOrCreateInterpreter(): InterpreterApi? {
        synchronized(lock) {
            interpreter?.let { return it }
            if (unavailable) return null
        }
        try {
            TfLite.initialize(context).awaitTask()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            synchronized(lock) { unavailable = true }
            return null
        }
        return synchronized(lock) {
            interpreter?.let { return it }
            try {
                val modelBuffer = loadModelBuffer(MODEL_ASSET_PATH)
                val opts = Options()
                    .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
                    .setUseXNNPACK(true)
                    .setNumThreads(4)
                val created = InterpreterApi.create(modelBuffer, opts)
                interpreter = created
                created
            } catch (_: Exception) {
                unavailable = true
                null
            }
        }
    }

    private fun loadModelBuffer(assetPath: String): ByteBuffer {
        return runCatching {
            context.assets.openFd(assetPath).use { afd ->
                FileInputStream(afd.fileDescriptor).use { fis ->
                    fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                }
            }
        }.getOrElse {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                rewind()
            }
        }
    }

    companion object {
        private const val MODEL_ASSET_PATH = "ml/selfie_segmenter.tflite"
        const val INPUT_SIZE = 256

        /** Bilinearly upsamples a `[srcW x srcH]` probability mask to `[dstW x dstH]`. */
        internal fun upsampleMaskBilinear(
            src: FloatArray,
            srcW: Int,
            srcH: Int,
            dstW: Int,
            dstH: Int
        ): FloatArray {
            if (srcW == dstW && srcH == dstH) return src.copyOf()
            val dst = FloatArray(dstW * dstH)
            val xRatio = if (dstW > 1) (srcW - 1).toFloat() / (dstW - 1) else 0f
            val yRatio = if (dstH > 1) (srcH - 1).toFloat() / (dstH - 1) else 0f
            for (y in 0 until dstH) {
                val sy = (y * yRatio).coerceIn(0f, (srcH - 1).toFloat())
                val y0 = floor(sy).toInt()
                val y1 = minOf(y0 + 1, srcH - 1)
                val fy = sy - y0
                val row0 = y0 * srcW
                val row1 = y1 * srcW
                val outRow = y * dstW
                for (x in 0 until dstW) {
                    val sx = (x * xRatio).coerceIn(0f, (srcW - 1).toFloat())
                    val x0 = floor(sx).toInt()
                    val x1 = minOf(x0 + 1, srcW - 1)
                    val fx = sx - x0
                    val v00 = src[row0 + x0]
                    val v10 = src[row0 + x1]
                    val v01 = src[row1 + x0]
                    val v11 = src[row1 + x1]
                    val top = v00 + (v10 - v00) * fx
                    val bot = v01 + (v11 - v01) * fx
                    dst[outRow + x] = (top + (bot - top) * fy).coerceIn(0f, 1f)
                }
            }
            return dst
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel(CancellationException("TFLITE_CANCELLED")) }
}
