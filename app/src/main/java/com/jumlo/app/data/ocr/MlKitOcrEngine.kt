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
import com.jumlo.app.core.OcrTextPolicy
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

/** Multi-script on-device OCR combining Tesseract (Arabic+English) and ML Kit v2 with RTL-aware reading order. */
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

    suspend fun recognize(
        bitmap: Bitmap,
        script: OcrScript = OcrScript.AUTO,
        onProgress: ((Float) -> Unit)? = null
    ): String {
        currentCoroutineContext().ensureActive()
        var arabicModelMissing = false
        var tessResult = ""

        if (script == OcrScript.AUTO || script == OcrScript.ARABIC) {
            tessResult = try {
                arabicEngine.recognize(bitmap, includeEnglish = true, onProgress = onProgress)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: OperationFailure) {
                if (error.code == "OCR_ARABIC_MODEL_MISSING" || error.code == "OCR_MODEL_UNAVAILABLE") {
                    arabicModelMissing = true
                }
                if (script == OcrScript.ARABIC) throw error
                ""
            } catch (_: Exception) {
                if (script == OcrScript.ARABIC) throw OperationFailure("OCR_FAILED")
                ""
            }

            if (script == OcrScript.ARABIC) {
                if (tessResult.isNotBlank()) return tessResult
                if (arabicModelMissing) throw OperationFailure("OCR_ARABIC_MODEL_MISSING")
                return ""
            }

            // In AUTO mode, keep Tesseract ara+eng output whenever real Arabic script is present
            // (avoiding a single stray Arabic punctuation mark overriding a pure-English page).
            if (hasSubstantialArabic(tessResult)) {
                return tessResult
            }
        }

        val recognizer = createRecognizer(script)
        try {
            ensureModuleInstalled(recognizer)
            currentCoroutineContext().ensureActive()
            val prepared = prepareForAccuracy(bitmap)
            val mlResult = try {
                val firstPass = runPass(recognizer, prepared)
                if (firstPass.isNotBlank()) firstPass else {
                    // Second pass with grayscale contrast boost for faint scans or low-contrast photos.
                    val enhanced = enhanceContrast(prepared)
                    try {
                        runPass(recognizer, enhanced)
                    } finally {
                        enhanced.recycle()
                    }
                }
            } finally {
                if (prepared !== bitmap) prepared.recycle()
            }

            if (script == OcrScript.AUTO) {
                if (arabicModelMissing && isLikelyLatinGibberish(mlResult)) {
                    throw OperationFailure("OCR_ARABIC_MODEL_MISSING")
                }
                if (mlResult.isNotBlank() && !isLikelyLatinGibberish(mlResult)) return mlResult
                if (tessResult.isNotBlank()) return tessResult
                if (arabicModelMissing) throw OperationFailure("OCR_ARABIC_MODEL_MISSING")
            }
            return mlResult
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: OperationFailure) {
            if (script == OcrScript.AUTO && tessResult.isNotBlank()) return tessResult
            if (script == OcrScript.AUTO && arabicModelMissing) throw OperationFailure("OCR_ARABIC_MODEL_MISSING")
            throw error
        } catch (_: Exception) {
            if (script == OcrScript.AUTO && tessResult.isNotBlank()) return tessResult
            if (script == OcrScript.AUTO && arabicModelMissing) throw OperationFailure("OCR_ARABIC_MODEL_MISSING")
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
        } catch (_: Exception) {
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
        val blocks = result.textBlocks
        if (blocks.isEmpty()) return ""
        val overallRtl = isRtlText(result.text)
        val lineHeights = blocks.flatMap { it.lines }.mapNotNull { it.boundingBox?.height()?.takeIf { h -> h > 0 } }
        val avgLineHeight = if (lineHeights.isNotEmpty()) lineHeights.average().toInt() else 36
        val blockRowBand = maxOf(24, avgLineHeight)
        val lineRowBand = maxOf(12, avgLineHeight / 2)

        val sortedBlocks = blocks.sortedWith(
            compareBy<Text.TextBlock> { (it.boundingBox?.top ?: Int.MAX_VALUE) / blockRowBand }
                .thenBy { block ->
                    val box = block.boundingBox
                    if (overallRtl) -(box?.right ?: Int.MIN_VALUE) else (box?.left ?: Int.MAX_VALUE)
                }
        )
        val formatted = sortedBlocks.joinToString("\n\n") { block ->
            val blockRtl = isRtlText(block.text)
            val sortedLines = block.lines.sortedWith(
                compareBy<Text.Line> { (it.boundingBox?.top ?: Int.MAX_VALUE) / lineRowBand }
                    .thenBy { line ->
                        val box = line.boundingBox
                        if (blockRtl) -(box?.right ?: Int.MIN_VALUE) else (box?.left ?: Int.MAX_VALUE)
                    }
            )
            // Merge line segments sharing the same visual row in natural reading order.
            val rows = mutableListOf<MutableList<Text.Line>>()
            for (line in sortedLines) {
                val top = line.boundingBox?.top
                val prev = rows.lastOrNull()?.lastOrNull()?.boundingBox?.top
                if (top != null && prev != null && kotlin.math.abs(top - prev) <= lineRowBand) {
                    rows.last().add(line)
                } else {
                    rows.add(mutableListOf(line))
                }
            }
            rows.joinToString("\n") { row ->
                val ordered = if (blockRtl) {
                    row.sortedByDescending { it.boundingBox?.right ?: Int.MIN_VALUE }
                } else {
                    row.sortedBy { it.boundingBox?.left ?: Int.MAX_VALUE }
                }
                ordered.joinToString(" ") { it.text.trim() }
            }
        }
        return OcrTextPolicy.normalizeUtf8(formatted)
    }

    companion object {
        private val LATIN_VOWELS = setOf(
            'a', 'e', 'i', 'o', 'u', 'y',
            'à', 'á', 'â', 'ä', 'é', 'è', 'ê', 'ë', 'í', 'î', 'ï', 'ó', 'ô', 'ö', 'ú', 'û', 'ü'
        )

        internal fun hasSubstantialArabic(text: String): Boolean {
            var arabicLetters = 0
            var latinLetters = 0
            for (ch in text) {
                val c = ch.code
                if (ch.isLetter() && (c in 0x0621..0x064A || c in 0x066E..0x06D3 || c in 0x0750..0x077F || c in 0x08A0..0x08FF)) {
                    arabicLetters++
                } else if (ch in 'A'..'Z' || ch in 'a'..'z') {
                    latinLetters++
                }
            }
            return arabicLetters >= 3 || (arabicLetters > 0 && arabicLetters >= latinLetters)
        }

        internal fun isRtlText(text: String): Boolean {
            var rtl = 0
            var ltr = 0
            for (ch in text) {
                when (Character.getDirectionality(ch)) {
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtl++
                    Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltr++
                }
            }
            return rtl > 0 && rtl >= ltr
        }

        /** Detects empty, symbol-heavy, or vowel-less Latin hallucinations produced when Latin OCR runs on Arabic scans. */
        internal fun isLikelyLatinGibberish(text: String): Boolean {
            if (text.isBlank()) return true
            val nonSpace = text.count { !it.isWhitespace() }
            if (nonSpace == 0) return true
            val alnum = text.count { it.isLetterOrDigit() }
            if (alnum.toFloat() / nonSpace < 0.55f) return true
            val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val realTokens = tokens.count { token ->
                val digits = token.count { it.isDigit() }
                if (digits >= 2 && digits * 2 >= token.length) return@count true
                val letters = token.filter { it.isLetter() }
                if (letters.length < 3) return@count false
                val allCapsAcronym = letters.length <= 5 && letters.all { it.isUpperCase() }
                val hasVowel = letters.any { it.lowercaseChar() in LATIN_VOWELS }
                val bizarreCase = letters.drop(1).any { it.isUpperCase() } && letters.any { it.isLowerCase() }
                (hasVowel || allCapsAcronym) && !bizarreCase
            }
            return (tokens.size >= 3 && realTokens * 2 < tokens.size) || (tokens.isNotEmpty() && realTokens == 0 && tokens.all { it.length <= 3 })
        }
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
