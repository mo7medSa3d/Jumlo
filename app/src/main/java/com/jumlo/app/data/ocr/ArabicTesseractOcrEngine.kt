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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Arabic + English mixed OCR engine powered by Tesseract 5 (tesseract4android).
 * Uses bundled assets/tessdata/ if present, or performs a one-time download of the official
 * tessdata_fast models into private storage so all subsequent runs are 100% offline.
 */
@Singleton
class ArabicTesseractOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun recognize(bitmap: Bitmap, includeEnglish: Boolean = true): String = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val (dataRoot, langSpec) = ensureModelsReady(includeEnglish)
        withContext(Dispatchers.Default) {
            val api = TessBaseAPI()
            try {
                val initialized = api.init(dataRoot.absolutePath, langSpec)
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
            } catch (_: OutOfMemoryError) {
                throw OperationFailure("LOW_MEMORY")
            } catch (_: Exception) {
                throw OperationFailure("OCR_FAILED")
            } finally {
                runCatching { api.recycle() }
            }
        }
    }

    @Synchronized
    private fun ensureModelsReady(includeEnglish: Boolean): Pair<File, String> {
        val rootDir = File(context.filesDir, "tesseract")
        val tessDir = File(rootDir, "tessdata").apply { mkdirs() }
        val araReady = ensureSingleModel(tessDir, "ara", "https://github.com/tesseract-ocr/tessdata_fast/raw/main/ara.traineddata")
        if (!araReady) throw OperationFailure("OCR_ARABIC_MODEL_MISSING")
        val engReady = includeEnglish && ensureSingleModel(tessDir, "eng", "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata")
        return rootDir to if (engReady) "ara+eng" else "ara"
    }

    private fun ensureSingleModel(tessDir: File, code: String, url: String): Boolean {
        val target = File(tessDir, "$code.traineddata")
        if (target.isFile && target.length() > 100_000L) return true
        val temp = File(tessDir, "$code-${UUID.randomUUID()}.part")
        // 1. Try bundled APK asset first (zero network).
        try {
            context.assets.open("tessdata/$code.traineddata").use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (temp.length() > 100_000L) {
                if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
                return true
            }
        } catch (_: Exception) {
            temp.delete()
        }
        // 2. One-time download from official tessdata_fast CDN into private storage for permanent offline use.
        try {
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body ?: return false
                body.byteStream().use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (temp.length() > 100_000L) {
                if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
                return true
            }
            temp.delete()
            return false
        } catch (_: Exception) {
            temp.delete()
            return false
        }
    }
}
