package com.jumlo.app.data.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jumlo.app.data.ocr.OcrImageEnhancer
import com.jumlo.app.data.ocr.awaitNativeCompletion
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Automatically fixes rotated or upside-down ID card & passport scans before A4 layout.
 * Uses ML Kit Text Recognition line baseline vectors first, falling back to ML Kit Face Detection.
 */
internal object CardOrientationDetector {
    suspend fun autoOrientLandscape(source: Bitmap): Bitmap {
        if (source.isRecycled || source.width < 48 || source.height < 48) return source
        var rotationNeeded = detectRotationViaText(source)
        if (rotationNeeded == 0 && source.height > source.width) {
            // Portrait-oriented card without dominant text angle: try upright rotation and confirm via Face Detection or default 90°.
            rotationNeeded = detectPortraitCardTurn(source)
        } else if (rotationNeeded == 0) {
            // Even when already landscape, check if 180°-upside-down via Face Detection head angle fallback.
            rotationNeeded = detectUpsideDownViaFace(source)
        }
        if (rotationNeeded == 0) return source
        val matrix = Matrix().apply { postRotate(rotationNeeded.toFloat()) }
        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrDefault(source)
    }

    private suspend fun detectRotationViaText(source: Bitmap): Int = withTimeoutOrNull(1_600L) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(InputImage.fromBitmap(source, 0)).awaitNativeCompletion()
            var sumDx = 0f
            var sumDy = 0f
            var lineCount = 0
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val pts = line.cornerPoints
                    if (pts != null && pts.size >= 2) {
                        val dx = (pts[1].x - pts[0].x).toFloat()
                        val dy = (pts[1].y - pts[0].y).toFloat()
                        val weight = maxOf(1, line.text.length)
                        sumDx += dx * weight
                        sumDy += dy * weight
                        lineCount++
                    }
                }
            }
            if (lineCount == 0) 0 else OcrImageEnhancer.quarterTurnFromBaselineVector(sumDx, sumDy)
        } catch (_: Exception) {
            0
        } finally {
            runCatching { recognizer.close() }
        }
    } ?: 0

    private suspend fun detectPortraitCardTurn(source: Bitmap): Int = withTimeoutOrNull(1_600L) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.08f)
            .build()
        val detector = FaceDetection.getClient(options)
        try {
            val faces = detector.process(InputImage.fromBitmap(source, 0)).awaitNativeCompletion()
            val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            if (primaryFace != null) {
                val angle = primaryFace.headEulerAngleZ
                when {
                    angle > 45f -> 270
                    angle < -45f -> 90
                    else -> 90
                }
            } else {
                90
            }
        } catch (_: Exception) {
            90
        } finally {
            runCatching { detector.close() }
        }
    } ?: 90

    private suspend fun detectUpsideDownViaFace(source: Bitmap): Int = withTimeoutOrNull(1_400L) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.08f)
            .build()
        val detector = FaceDetection.getClient(options)
        try {
            // Upright faces detect normally at 0°; if none found, probe 180°-rotated frame cleanly.
            val normal = detector.process(InputImage.fromBitmap(source, 0)).awaitNativeCompletion()
            if (normal.isNotEmpty()) return@withTimeoutOrNull 0
            val m = Matrix().apply { postRotate(180f) }
            val flipped = Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
            try {
                val inverted = detector.process(InputImage.fromBitmap(flipped, 0)).awaitNativeCompletion()
                if (inverted.isNotEmpty()) 180 else 0
            } finally {
                if (flipped !== source && !flipped.isRecycled) flipped.recycle()
            }
        } catch (_: Exception) {
            0
        } finally {
            runCatching { detector.close() }
        }
    } ?: 0
}
