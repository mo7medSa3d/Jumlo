package com.jumlo.app.data.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.jumlo.app.data.jobs.OperationFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device foreground cutout powered by Google Play Services ML Kit Subject Segmentation.
 * The neural model (~few MB) is provisioned lazily by Play Services via ModuleInstall so the app
 * APK stays small while delivering crisp pixel-level subject alpha masks and edge decontamination.
 */
@Singleton
class SubjectSegmentationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val portraitSegmenter: LiteRtPortraitSegmenter
) {
    suspend fun removeBackground(
        source: Bitmap,
        backgroundMode: String = MODE_TRANSPARENT,
        trimTransparent: Boolean = false
    ): Bitmap = withContext(Dispatchers.Default) {
        currentCoroutineContext().ensureActive()
        val working = prepareWorkingBitmap(source)
        var segmenter: SubjectSegmenter? = null
        var mlForeground: Bitmap? = null
        var cutout: Bitmap? = null
        var trimmed: Bitmap? = null
        var resultBitmap: Bitmap? = null
        try {
            val w = working.width
            val h = working.height
            val total = w * h
            val hasPortraitFace = hasDetectedPortraitFace(working)

            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .enableForegroundConfidenceMask()
                .build()
            segmenter = SubjectSegmentation.getClient(options)

            var primaryConfidence: FloatArray? = null
            var primaryError: OperationFailure? = null
            try {
                ensureModuleInstalled(segmenter)
                currentCoroutineContext().ensureActive()
                val result = segmenter.process(InputImage.fromBitmap(working, 0)).awaitNativeSegmentation()
                mlForeground = result.foregroundBitmap
                val mask = result.foregroundConfidenceMask
                if (mask != null) {
                    mask.rewind()
                    if (mask.remaining() >= total) {
                        primaryConfidence = FloatArray(total).also { mask.get(it, 0, total) }
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: OperationFailure) {
                primaryError = failure
            } catch (_: Exception) {
                primaryError = OperationFailure("BG_REMOVE_MODEL_UNAVAILABLE")
            }

            currentCoroutineContext().ensureActive()

            // Specialized dual-model resilience & portrait hair-edge fusion via LiteRT selfie segmenter.
            if (hasPortraitFace) {
                val portraitMask = runCatching { portraitSegmenter.segmentMask(working) }.getOrNull()
                if (portraitMask != null && portraitMask.size >= total) {
                    primaryConfidence = if (primaryConfidence == null) {
                        portraitMask
                    } else {
                        val merged = primaryConfidence!!
                        for (i in 0 until total) {
                            val m = merged[i]
                            if (m in 0.08f..0.92f) {
                                merged[i] = (0.70f * m + 0.30f * portraitMask[i]).coerceIn(0f, 1f)
                            }
                        }
                        merged
                    }
                }
            }

            cutout = if (primaryConfidence != null) {
                val pixels = IntArray(total)
                working.getPixels(pixels, 0, w, 0, 0, w, h)
                val stats = applyConfidenceMaskAndDecontaminate(pixels, primaryConfidence!!, w, h)
                if (stats.foregroundPixels == 0) {
                    throw OperationFailure("BG_REMOVE_NO_SUBJECT")
                }
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                    setHasAlpha(true)
                    setPixels(pixels, 0, w, 0, 0, w, h)
                }
            } else if (mlForeground != null) {
                val fg = mlForeground!!
                val pixels = IntArray(fg.width * fg.height)
                fg.getPixels(pixels, 0, fg.width, 0, 0, fg.width, fg.height)
                if (!hasForegroundAlpha(pixels)) {
                    throw OperationFailure("BG_REMOVE_NO_SUBJECT")
                }
                fg.copy(Bitmap.Config.ARGB_8888, false).apply { setHasAlpha(true) }
            } else {
                throw primaryError ?: OperationFailure("BG_REMOVE_NO_SUBJECT")
            }

            val cropped = if (trimTransparent) {
                val cw = cutout!!.width
                val ch = cutout!!.height
                val cropPixels = IntArray(cw * ch)
                cutout!!.getPixels(cropPixels, 0, cw, 0, 0, cw, ch)
                val bounds = computeTrimBounds(cropPixels, cw, ch, paddingPx = max(4, min(cw, ch) / 50))
                if (bounds != null && (bounds.width() < cw || bounds.height() < ch)) {
                    trimmed = Bitmap.createBitmap(
                        cutout!!,
                        bounds.left,
                        bounds.top,
                        bounds.width(),
                        bounds.height()
                    ).apply { setHasAlpha(true) }
                    trimmed!!
                } else {
                    cutout!!
                }
            } else {
                cutout!!
            }

            val bgColor = backdropColorForMode(backgroundMode)
            if (bgColor == null) {
                resultBitmap = if (cropped === source) {
                    cropped.copy(Bitmap.Config.ARGB_8888, false).apply { setHasAlpha(true) }
                } else {
                    cropped
                }
                return@withContext resultBitmap
            }

            val composited = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
            composited.setHasAlpha(false)
            val canvas = Canvas(composited)
            canvas.drawColor(bgColor)
            canvas.drawBitmap(cropped, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            resultBitmap = composited
            composited
        } finally {
            runCatching { segmenter?.close() }
            if (mlForeground != null && mlForeground !== resultBitmap && !mlForeground!!.isRecycled) mlForeground!!.recycle()
            if (trimmed != null && trimmed !== resultBitmap && !trimmed!!.isRecycled) trimmed!!.recycle()
            if (cutout != null && cutout !== resultBitmap && !cutout!!.isRecycled) cutout!!.recycle()
            if (working !== source && working !== resultBitmap && !working.isRecycled) working.recycle()
        }
    }

    private suspend fun hasDetectedPortraitFace(bitmap: Bitmap): Boolean = withTimeoutOrNull(900L) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.10f)
            .build()
        val detector = FaceDetection.getClient(options)
        try {
            detector.process(InputImage.fromBitmap(bitmap, 0)).awaitNativeSegmentation().isNotEmpty()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false
        } finally {
            runCatching { detector.close() }
        }
    } ?: false

    private fun prepareWorkingBitmap(source: Bitmap): Bitmap {
        val minDim = min(source.width, source.height)
        val maxDim = max(source.width, source.height)
        if (minDim < MIN_INPUT_DIM) {
            val scale = MIN_INPUT_DIM.toFloat() / minDim.coerceAtLeast(1)
            val w = (source.width * scale).roundToInt().coerceAtLeast(MIN_INPUT_DIM)
            val h = (source.height * scale).roundToInt().coerceAtLeast(MIN_INPUT_DIM)
            return Bitmap.createScaledBitmap(source, w, h, true)
        }
        if (maxDim > MAX_INPUT_DIM) {
            val scale = MAX_INPUT_DIM.toFloat() / maxDim
            val w = (source.width * scale).roundToInt().coerceAtLeast(1)
            val h = (source.height * scale).roundToInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(source, w, h, true)
        }
        return if (source.config == Bitmap.Config.ARGB_8888) source else source.copy(Bitmap.Config.ARGB_8888, false)
    }

    private suspend fun ensureModuleInstalled(segmenter: SubjectSegmenter) {
        val modules = ModuleInstall.getClient(context)
        val initiallyAvailable = try {
            modules.areModulesAvailable(segmenter).awaitCancelable().areModulesAvailable()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            throw OperationFailure("BG_REMOVE_MODEL_UNAVAILABLE")
        }
        if (initiallyAvailable) return

        val installed = CompletableDeferred<Unit>()
        val listener = InstallStatusListener { update ->
            when (update.installState) {
                InstallState.STATE_COMPLETED -> installed.complete(Unit)
                InstallState.STATE_FAILED,
                InstallState.STATE_CANCELED -> installed.completeExceptionally(OperationFailure("BG_REMOVE_MODEL_UNAVAILABLE"))
            }
        }
        try {
            val response = try {
                modules.installModules(
                    ModuleInstallRequest.newBuilder()
                        .addApi(segmenter)
                        .setListener(listener)
                        .build()
                ).awaitCancelable()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                throw OperationFailure("BG_REMOVE_MODEL_UNAVAILABLE")
            }
            if (!response.areModulesAlreadyInstalled()) {
                try {
                    withTimeout(MODULE_INSTALL_TIMEOUT_MS) { installed.await() }
                } catch (timeout: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    throw OperationFailure("BG_REMOVE_MODEL_UNAVAILABLE")
                }
            }
        } finally {
            runCatching { modules.unregisterListener(listener) }
        }
        val availableAfter = runCatching {
            modules.areModulesAvailable(segmenter).awaitCancelable().areModulesAvailable()
        }.getOrDefault(false)
        if (!availableAfter) {
            throw OperationFailure("BG_REMOVE_MODEL_UNAVAILABLE")
        }
    }

    internal data class CutoutStats(val foregroundPixels: Int, val edgeDecontaminatedPixels: Int)

    internal data class IntBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width(): Int = right - left + 1
        fun height(): Int = bottom - top + 1
    }

    companion object {
        const val MODE_TRANSPARENT = "TRANSPARENT"
        const val MODE_WHITE = "WHITE"
        const val MODE_DARK = "DARK"
        const val MODE_BLUE = "BLUE"

        private const val MIN_INPUT_DIM = 32
        private const val MAX_INPUT_DIM = 2400
        private const val MODULE_INSTALL_TIMEOUT_MS = 25_000L

        private const val CONF_LOW = 0.12f
        private const val CONF_HIGH = 0.88f
        private const val SOLID_ALPHA = 205

        internal fun normalizeMode(raw: String?): String = when (raw?.trim()?.uppercase()) {
            MODE_WHITE -> MODE_WHITE
            MODE_DARK -> MODE_DARK
            MODE_BLUE -> MODE_BLUE
            else -> MODE_TRANSPARENT
        }

        internal fun backdropColorForMode(mode: String?): Int? = when (normalizeMode(mode)) {
            MODE_WHITE -> Color.WHITE
            MODE_DARK -> 0xFF14181F.toInt()
            MODE_BLUE -> 0xFF1D4ED8.toInt()
            else -> null
        }

        internal fun hasForegroundAlpha(pixels: IntArray): Boolean {
            for (p in pixels) {
                if ((p ushr 24) >= 64) return true
            }
            return false
        }

        /**
         * Converts raw confidence probabilities `[0..1]` into smoothstep anti-aliased alpha values
         * and removes background fringe color bleeding along semi-transparent edges by sampling nearby
         * opaque foreground pixels.
         */
        internal fun applyConfidenceMaskAndDecontaminate(
            pixels: IntArray,
            confidence: FloatArray,
            width: Int,
            height: Int
        ): CutoutStats {
            val total = width * height
            val alphas = IntArray(total)
            var foregroundPixels = 0

            for (i in 0 until total) {
                val c = confidence[i].coerceIn(0f, 1f)
                val alpha = when {
                    c <= CONF_LOW -> 0
                    c >= CONF_HIGH -> 255
                    else -> {
                        val t = (c - CONF_LOW) / (CONF_HIGH - CONF_LOW)
                        val smooth = t * t * (3f - 2f * t)
                        (smooth * 255f).roundToInt().coerceIn(0, 255)
                    }
                }
                alphas[i] = alpha
                if (alpha >= 96) foregroundPixels++
            }

            if (foregroundPixels == 0) {
                for (i in 0 until total) pixels[i] = 0
                return CutoutStats(0, 0)
            }

            // Iterative BFS connected-component pruning strips isolated background specks while preserving all main subjects.
            val minIslandSize = max(4, minOf(48, foregroundPixels / 300))
            if (total >= 64) {
                val visited = BooleanArray(total)
                val queue = IntArray(total)
                val component = IntArray(total)
                for (start in 0 until total) {
                    if (alphas[start] == 0 || visited[start]) continue
                    var head = 0
                    var tail = 0
                    var compSize = 0
                    visited[start] = true
                    queue[tail++] = start
                    while (head < tail) {
                        val curr = queue[head++]
                        component[compSize++] = curr
                        val cx = curr % width
                        val cy = curr / width
                        if (cx > 0) {
                            val n = curr - 1
                            if (alphas[n] > 0 && !visited[n]) { visited[n] = true; queue[tail++] = n }
                        }
                        if (cx + 1 < width) {
                            val n = curr + 1
                            if (alphas[n] > 0 && !visited[n]) { visited[n] = true; queue[tail++] = n }
                        }
                        if (cy > 0) {
                            val n = curr - width
                            if (alphas[n] > 0 && !visited[n]) { visited[n] = true; queue[tail++] = n }
                        }
                        if (cy + 1 < height) {
                            val n = curr + width
                            if (alphas[n] > 0 && !visited[n]) { visited[n] = true; queue[tail++] = n }
                        }
                    }
                    if (compSize < minIslandSize) {
                        for (k in 0 until compSize) {
                            val idx = component[k]
                            if (alphas[idx] >= 96) foregroundPixels--
                            alphas[idx] = 0
                        }
                    }
                }
                if (foregroundPixels <= 0) {
                    for (i in 0 until total) pixels[i] = 0
                    return CutoutStats(0, 0)
                }
            }

            // Copy original pixels before modifying RGB on semi-transparent borders so neighbors read unshifted source colors.
            val srcPixels = pixels.copyOf()
            var decontaminated = 0

            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val idx = rowOffset + x
                    val alpha = alphas[idx]
                    if (alpha == 0) {
                        pixels[idx] = 0
                        continue
                    }
                    val src = srcPixels[idx]
                    val srcA = (src ushr 24) and 0xFF
                    if (srcA == 0) {
                        pixels[idx] = 0
                        continue
                    }
                    if (alpha >= SOLID_ALPHA) {
                        pixels[idx] = (alpha shl 24) or (src and 0x00FFFFFF)
                        continue
                    }

                    // Semi-transparent border pixel: pull RGB toward nearest solid foreground neighbors to avoid halos.
                    var sumR = 0f
                    var sumG = 0f
                    var sumB = 0f
                    var weightSum = 0f

                    val yMin = max(0, y - 2)
                    val yMax = min(height - 1, y + 2)
                    val xMin = max(0, x - 2)
                    val xMax = min(width - 1, x + 2)
                    for (ny in yMin..yMax) {
                        val dy = ny - y
                        val nRow = ny * width
                        for (nx in xMin..xMax) {
                            val nIdx = nRow + nx
                            val nAlpha = alphas[nIdx]
                            if (nAlpha >= SOLID_ALPHA) {
                                val dx = nx - x
                                val distSq = (dx * dx + dy * dy).coerceAtLeast(1)
                                val w = (nAlpha / 255f) / distSq
                                val np = srcPixels[nIdx]
                                sumR += ((np ushr 16) and 0xFF) * w
                                sumG += ((np ushr 8) and 0xFF) * w
                                sumB += (np and 0xFF) * w
                                weightSum += w
                            }
                        }
                    }

                    val r0 = (src ushr 16) and 0xFF
                    val g0 = (src ushr 8) and 0xFF
                    val b0 = src and 0xFF
                    if (weightSum > 0f) {
                        val fgR = sumR / weightSum
                        val fgG = sumG / weightSum
                        val fgB = sumB / weightSum
                        val mix = ((SOLID_ALPHA - alpha).toFloat() / SOLID_ALPHA).coerceIn(0f, 0.85f)
                        val r = (r0 * (1f - mix) + fgR * mix).roundToInt().coerceIn(0, 255)
                        val g = (g0 * (1f - mix) + fgG * mix).roundToInt().coerceIn(0, 255)
                        val b = (b0 * (1f - mix) + fgB * mix).roundToInt().coerceIn(0, 255)
                        pixels[idx] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
                        decontaminated++
                    } else {
                        pixels[idx] = (alpha shl 24) or (r0 shl 16) or (g0 shl 8) or b0
                    }
                }
            }
            return CutoutStats(foregroundPixels, decontaminated)
        }

        internal fun computeTrimBounds(
            pixels: IntArray,
            width: Int,
            height: Int,
            paddingPx: Int = 0,
            alphaThreshold: Int = 8
        ): IntBounds? {
            var left = width
            var top = height
            var right = -1
            var bottom = -1
            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                    if (alpha > alphaThreshold) {
                        if (x < left) left = x
                        if (x > right) right = x
                        if (y < top) top = y
                        if (y > bottom) bottom = y
                    }
                }
            }
            if (right < left || bottom < top) return null
            val pad = paddingPx.coerceAtLeast(0)
            return IntBounds(
                left = (left - pad).coerceAtLeast(0),
                top = (top - pad).coerceAtLeast(0),
                right = (right + pad).coerceAtMost(width - 1),
                bottom = (bottom + pad).coerceAtMost(height - 1)
            )
        }
    }
}

private suspend fun <T> Task<T>.awaitNativeSegmentation(): T = suspendCoroutine { continuation ->
    addOnSuccessListener(segCallbackExecutor) { continuation.resume(it) }
    addOnFailureListener(segCallbackExecutor) { continuation.resumeWithException(it) }
    addOnCanceledListener(segCallbackExecutor) {
        continuation.resumeWithException(CancellationException("BG_REMOVE_CANCELLED"))
    }
}

private suspend fun <T> Task<T>.awaitCancelable(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener(segCallbackExecutor) { continuation.resume(it) }
    addOnFailureListener(segCallbackExecutor) { continuation.resumeWithException(it) }
    addOnCanceledListener(segCallbackExecutor) {
        continuation.cancel(CancellationException("BG_REMOVE_CANCELLED"))
    }
}

private val segCallbackExecutor = Executor { it.run() }
