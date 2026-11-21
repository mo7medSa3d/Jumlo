package com.jumlo.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.jumlo.app.data.jobs.OperationFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 100% on-device Arabic OCR engine powered by Tesseract 5 (tesseract4android).
 * Copies bundled assets/tessdata/ara.traineddata into private app storage on first run.
 */
@Singleton
class ArabicTesseractOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        currentCoroutineContext().ensureActive()
        val dataRoot = ensureArabicModelReady()
        val api = TessBaseAPI()
        try {
            val initialized = api.init(dataRoot.absolutePath, "ara")
            if (!initialized) throw OperationFailure("OCR_MODEL_UNAVAILABLE")
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            currentCoroutineContext().ensureActive()
            api.setImage(bitmap)
            val raw = api.utF8Text.orEmpty()
            currentCoroutineContext().ensureActive()
            raw.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .trim()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: OperationFailure) {
            throw error
        } catch (oom: OutOfMemoryError) {
            throw OperationFailure("LOW_MEMORY")
        } catch (_: Exception) {
            throw OperationFailure("OCR_FAILED")
        } finally {
            runCatching { api.recycle() }
        }
    }

    @Synchronized
    private fun ensureArabicModelReady(): File {
        val rootDir = File(context.filesDir, "tesseract")
        val tessDir = File(rootDir, "tessdata")
        val target = File(tessDir, "ara.traineddata")
        if (target.isFile && target.length() > 100_000L) return rootDir
        tessDir.mkdirs()
        val temp = File(tessDir, "ara-${java.util.UUID.randomUUID()}.part")
        try {
            context.assets.open("tessdata/ara.traineddata").use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            return rootDir
        } catch (_: Exception) {
            temp.delete()
            throw OperationFailure("OCR_ARABIC_MODEL_MISSING")
        }
    }
}
