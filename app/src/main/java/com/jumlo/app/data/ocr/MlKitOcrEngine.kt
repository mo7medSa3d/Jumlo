package com.jumlo.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jumlo.app.data.jobs.OperationFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

enum class OcrScript(val id: String) {
    AUTO("AUTO"),
    ARABIC("ARABIC"),
    LATIN("LATIN"),
    CHINESE("CHINESE"),
    JAPANESE("JAPANESE"),
    KOREAN("KOREAN"),
    DEVANAGARI("DEVANAGARI");

    companion object {
        fun fromId(raw: String?): OcrScript = entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: AUTO
    }
}

/** Multi-script on-device ML Kit v2 OCR with adaptive upscaling, contrast fallback, and reading-order sorting. */
@Singleton
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arabicEngine: ArabicTesseractOcrEngine
) {
    constructor(context: Context) : this(context, ArabicTesseractOcrEngine(context))
    private fun createRecognizer(script: OcrScript): TextRecognizer = when (script) {
        OcrScript.AUTO, OcrScript.ARABIC, OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(bitmap: Bitmap, script: OcrScript = OcrScript.AUTO): String {
        currentCoroutineContext().ensureActive()
        if (script == OcrScript.AUTO || script == OcrScript.ARABIC) {
            val prepared = prepareForAccuracy(bitmap)
            try {
                val tessResult = try {
                    val first = arabicEngine.recognize(prepared, includeEnglish = true)
                    if (first.isNotBlank()) first else {
                        val enhanced = enhanceContrast(prepared)
                        try { arabicEngine.recognize(enhanced, includeEnglish = true) } finally { enhanced.recycle() }
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    ""
                }
                if (tessResult.isNotBlank()) return tessResult
                if (script == OcrScript.ARABIC) throw OperationFailure("OCR_ARABIC_MODEL_MISSING")
            } finally {
                if (prepared !== bitmap) prepared.recycle()
            }
        }
        val recognizer = createRecognizer(script)
        try {
            ensureModuleInstalled(recognizer)
            currentCoroutineContext().ensureActive()
            val prepared = prepareForAccuracy(bitmap)
            try {
                val firstPass = runPass(recognizer, prepared)
                if (firstPass.isNotBlank()) return firstPass
                // Second pass with grayscale contrast boost for faint scans or low-contrast photos.
                val enhanced = enhanceContrast(prepared)
                try {
                    return runPass(recognizer, enhanced)
                } finally {
                    enhanced.recycle()
                }
            } finally {
                if (prepared !== bitmap) prepared.recycle()
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: OperationFailure) {
            throw error
        } catch (error: Exception) {
            throw OperationFailure("OCR_MODEL_UNAVAILABLE")
        } finally {
            recognizer.close()
        }
    }

    private suspend fun ensureModuleInstalled(recognizer: TextRecognizer) {
        val modules = ModuleInstall.getClient(context)
        if (!modules.areModulesAvailable(recognizer).awaitCancelable().areModulesAvailable()) {
            val installed = CompletableDeferred<Unit>()
            val listener = InstallStatusListener { update ->
                when (update.installState) {
                    InstallState.STATE_COMPLETED -> installed.complete(Unit)
                    InstallState.STATE_FAILED, InstallState.STATE_CANCELED -> installed.completeExceptionally(OperationFailure("OCR_MODEL_UNAVAILABLE"))
                }
            }
            try {
                // installModules() acknowledges the request; only the status callback proves completion.
                val response = modules.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).setListener(listener).build()).awaitCancelable()
                if (!response.areModulesAlreadyInstalled()) {
                    try { withTimeout(120_000) { installed.await() } }
                    catch (timeout: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        throw OperationFailure("OCR_MODEL_UNAVAILABLE")
                    }
                }
            } finally { modules.unregisterListener(listener) }
            if (!modules.areModulesAvailable(recognizer).awaitCancelable().areModulesAvailable()) {
                throw OperationFailure("OCR_MODEL_UNAVAILABLE")
            }
        }
    }

    private suspend fun runPass(recognizer: TextRecognizer, bitmap: Bitmap): String {
        currentCoroutineContext().ensureActive()
        val text = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitNativeCompletion()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            throw OperationFailure("OCR_FAILED")
        }
        currentCoroutineContext().ensureActive()
        return formatReadingOrder(text)
    }

    private fun prepareForAccuracy(source: Bitmap): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        if (maxSide in 1200..2400) return source
        if (maxSide < 1200 && maxSide > 0) {
            val scale = (1600f / maxSide).coerceAtMost(2.5f)
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            return runCatching { Bitmap.createScaledBitmap(source, w, h, true) }.getOrDefault(source)
        }
        return source
    }

    private fun enhanceContrast(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
        val canvas = Canvas(out)
        val contrast = 1.55f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val boost = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        gray.postConcat(boost)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(gray)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
        } catch (t: Throwable) {
            out.recycle()
            throw t
        }
    }

    private fun formatReadingOrder(result: Text): String {
        val sortedBlocks = result.textBlocks.sortedWith(
            compareBy<Text.TextBlock> { (it.boundingBox?.top ?: Int.MAX_VALUE) / 36 }
                .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
        )
        return sortedBlocks.joinToString("\n\n") { block ->
            block.lines.sortedWith(
                compareBy<Text.Line> { it.boundingBox?.top ?: Int.MAX_VALUE }
                    .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
            ).joinToString("\n") { it.text }
        }.trim()
    }
}

/**
 * A native OCR task cannot be cancelled by a coroutine. Wait for its completion before releasing
 * the input bitmap, then observe coroutine cancellation; recycling while ML Kit reads it is unsafe.
 */
internal suspend fun <T> Task<T>.awaitNativeCompletion(): T = suspendCoroutine { continuation ->
    addOnSuccessListener(ocrCallbackExecutor) { continuation.resume(it) }
    addOnFailureListener(ocrCallbackExecutor) { continuation.resumeWithException(it) }
    addOnCanceledListener(ocrCallbackExecutor) { continuation.resumeWithException(CancellationException("OCR_CANCELLED")) }
}

/** Module queries/install requests never read the bitmap and may stop waiting immediately. */
private suspend fun <T> Task<T>.awaitCancelable(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener(ocrCallbackExecutor) { continuation.resume(it) }
    addOnFailureListener(ocrCallbackExecutor) { continuation.resumeWithException(it) }
    addOnCanceledListener(ocrCallbackExecutor) { continuation.cancel(CancellationException("OCR_CANCELLED")) }
}

private val ocrCallbackExecutor = Executor { it.run() }
