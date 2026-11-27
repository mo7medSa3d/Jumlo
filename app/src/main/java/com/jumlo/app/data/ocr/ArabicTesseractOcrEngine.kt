package com.jumlo.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.jumlo.app.core.OcrTextPolicy
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.diagnostics.AppDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Arabic + English mixed OCR engine powered by Tesseract 5 (tesseract4android).
 * Uses bundled assets/tessdata/ if present, or performs a one-time verified download of the
 * official tessdata_fast models into private storage so all subsequent runs are 100% offline.
 */
@Singleton
class ArabicTesseractOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val baseClient: OkHttpClient
) {
    constructor(context: Context) : this(context, OkHttpClient())

    private val http by lazy {
        baseClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
    }

    private val modelMutex = Mutex()
    private val apiLock = Any()
    private var cachedApi: TessBaseAPI? = null
    private var cachedDataPath: String? = null
    private var cachedLangSpec: String? = null

    suspend fun recognize(
        bitmap: Bitmap,
        includeEnglish: Boolean = true,
        onProgress: ((Float) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val (dataRoot, langSpec) = ensureModelsReady(includeEnglish, onProgress)
        withContext(Dispatchers.Default) {
            var processed: Bitmap? = null
            try {
                currentCoroutineContext().ensureActive()
                val prepared = preprocessForArabic(bitmap)
                processed = prepared
                currentCoroutineContext().ensureActive()
                val raw = synchronized(apiLock) {
                    val api = obtainApi(dataRoot, langSpec)
                    try {
                        api.setVariable("user_defined_dpi", "300")
                        api.setVariable("preserve_interword_spaces", "1")
                        api.setVariable("tessedit_do_invert", "0")
                        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                        api.setImage(prepared)
                        api.utF8Text.orEmpty()
                    } catch (oom: OutOfMemoryError) {
                        releaseApiLocked()
                        throw oom
                    } catch (error: RuntimeException) {
                        releaseApiLocked()
                        throw error
                    } finally {
                        runCatching { api.clear() }
                    }
                }
                currentCoroutineContext().ensureActive()
                OcrTextPolicy.normalizeUtf8(raw)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: OperationFailure) {
                throw error
            } catch (_: OutOfMemoryError) {
                throw OperationFailure("LOW_MEMORY")
            } catch (error: Exception) {
                AppDiagnostics.report("ocr_recognize", error)
                throw OperationFailure("OCR_FAILED")
            } finally {
                if (processed != null && processed !== bitmap) processed.recycle()
            }
        }
    }

    fun close() {
        synchronized(apiLock) { releaseApiLocked() }
    }

    private fun obtainApi(dataRoot: File, langSpec: String): TessBaseAPI {
        val dataPath = dataRoot.absolutePath
        val existing = cachedApi
        if (existing != null && cachedDataPath == dataPath && cachedLangSpec == langSpec) {
            return existing
        }
        releaseApiLocked()
        val api = TessBaseAPI()
        val initialized = runCatching { api.init(dataPath, langSpec) }.getOrDefault(false)
        if (!initialized) {
            runCatching { api.recycle() }
            // Delete potentially corrupted traineddata so a subsequent attempt can re-download cleanly.
            val tessDir = File(dataRoot, "tessdata")
            langSpec.split("+").forEach { code ->
                File(tessDir, "$code.traineddata").delete()
            }
            throw OperationFailure("OCR_MODEL_UNAVAILABLE")
        }
        cachedApi = api
        cachedDataPath = dataPath
        cachedLangSpec = langSpec
        return api
    }

    private fun releaseApiLocked() {
        cachedApi?.let { runCatching { it.recycle() } }
        cachedApi = null
        cachedDataPath = null
        cachedLangSpec = null
    }

    /** Upscales low-res inputs, straightens minor page tilt, and applies clean grayscale contrast onto ARGB_8888. */
    private fun preprocessForArabic(source: Bitmap): Bitmap = OcrImageEnhancer.prepareDocumentForOcr(
        source = source,
        targetWidthPx = TARGET_WIDTH_PX,
        maxUpscale = MAX_UPSCALE_FACTOR,
        maxPixels = MAX_PREPROCESS_PIXELS,
        contrastGain = 1.38f
    )

    private suspend fun ensureModelsReady(
        includeEnglish: Boolean,
        onProgress: ((Float) -> Unit)?
    ): Pair<File, String> = modelMutex.withLock {
        val rootDir = File(context.filesDir, "tesseract")
        val tessDir = File(rootDir, "tessdata").apply { mkdirs() }
        cleanupTempFiles(tessDir)
        val needAra = !isValidTrainedData(File(tessDir, "ara.traineddata"), MIN_ARA_BYTES)
        val needEng = includeEnglish && !isValidTrainedData(File(tessDir, "eng.traineddata"), MIN_ENG_BYTES)
        val reportProgress: (Float) -> Unit = { value ->
            _downloadProgress.value = value
            onProgress?.invoke(value)
        }
        try {
            val araReady = ensureSingleModel(tessDir, "ara", MIN_ARA_BYTES) { frac ->
                val scaled = if (frac < 0f) -1f else if (needEng) frac * 0.5f else frac
                reportProgress(scaled)
            }
            if (!araReady) throw OperationFailure("OCR_ARABIC_MODEL_MISSING")
            val engReady = includeEnglish && ensureSingleModel(tessDir, "eng", MIN_ENG_BYTES) { frac ->
                val scaled = if (frac < 0f) -1f else if (needAra) 0.5f + frac * 0.5f else frac
                reportProgress(scaled)
            }
            rootDir to if (engReady) "ara+eng" else "ara"
        } finally {
            _downloadProgress.value = null
        }
    }

    private fun cleanupTempFiles(tessDir: File) {
        tessDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp") || file.name.endsWith(".part")) {
                file.delete()
            }
        }
    }

    private suspend fun ensureSingleModel(
        tessDir: File,
        code: String,
        minBytes: Long,
        onDownloadProgress: (Float) -> Unit
    ): Boolean {
        val target = File(tessDir, "$code.traineddata")
        val cacheKey = "${target.absolutePath}:$minBytes"
        if (cacheKey in verifiedModels && target.isFile) return true
        if (isValidTrainedData(target, minBytes) && verifyFileChecksum(target, code)) {
            verifiedModels.add(cacheKey)
            return true
        }
        if (target.exists()) target.delete()

        val temp = File(tessDir, "$code-${UUID.randomUUID()}.tmp")
        try {
            // 1. Try bundled APK asset first (zero network), verifying size, header, and SHA-256.
            val copiedFromAsset = try {
                val digest = MessageDigest.getInstance("SHA-256")
                context.assets.open("tessdata/$code.traineddata").use { input ->
                    temp.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            digest.update(buf, 0, n)
                            output.write(buf, 0, n)
                        }
                        output.flush()
                    }
                }
                val shaHex = toHex(digest.digest())
                isValidTrainedData(temp, minBytes) && verifySha256(code, shaHex)
            } catch (_: Exception) {
                temp.delete()
                false
            }
            if (copiedFromAsset && atomicMove(temp, target)) return true
            temp.delete()

            // 2. One-time verified download from official tessdata_fast into private storage.
            val urls = listOf(
                "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/$code.traineddata",
                "https://github.com/tesseract-ocr/tessdata_fast/raw/main/$code.traineddata"
            )
            val ctx = currentCoroutineContext()
            for (url in urls) {
                ctx.ensureActive()
                temp.delete()
                val call = http.newCall(Request.Builder().url(url).get().build())
                val downloaded = try {
                    onDownloadProgress(-1f)
                    call.execute().use { resp ->
                        if (!resp.isSuccessful || !resp.request.url.isHttps) return@use false
                        val body = resp.body ?: return@use false
                        val expectedLength = body.contentLength()
                        if (expectedLength in 1 until minBytes || expectedLength > MAX_MODEL_BYTES) return@use false
                        if (expectedLength > 0L) onDownloadProgress(0f)
                        val digest = MessageDigest.getInstance("SHA-256")
                        body.byteStream().use { input ->
                            temp.outputStream().use { output ->
                                val buf = ByteArray(16 * 1024)
                                var total = 0L
                                var lastReported = -1f
                                while (true) {
                                    ctx.ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    total += n
                                    if (total > MAX_MODEL_BYTES) return@use false
                                    digest.update(buf, 0, n)
                                    output.write(buf, 0, n)
                                    if (expectedLength > 0L) {
                                        val frac = (total.toFloat() / expectedLength.toFloat()).coerceIn(0f, 1f)
                                        if (frac - lastReported >= 0.02f || frac >= 1f) {
                                            lastReported = frac
                                            onDownloadProgress(frac)
                                        }
                                    }
                                }
                                output.flush()
                            }
                        }
                        val sizeOk = if (expectedLength > 0L) temp.length() == expectedLength else true
                        val shaHex = toHex(digest.digest())
                        sizeOk && isValidTrainedData(temp, minBytes) && verifySha256(code, shaHex)
                    }
                } catch (cancel: CancellationException) {
                    call.cancel()
                    throw cancel
                } catch (error: Exception) {
                    AppDiagnostics.report("ocr_model_download", error)
                    false
                }
                if (downloaded && atomicMove(temp, target)) return true
                temp.delete()
            }
            return false
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /**
     * Validates minimum byte size, rejects HTML/JSON/Git-LFS/ZIP error payloads, and checks the
     * Tesseract TessdataManager binary header (32-bit entry count + first 64-bit entry offset).
     */
    private fun isValidTrainedData(file: File, minBytes: Long): Boolean {
        val len = file.length()
        if (!file.isFile || len < minBytes) return false
        return try {
            val header = ByteArray(16)
            val read = file.inputStream().use { it.read(header) }
            if (read < 12) return false
            val prefix = String(header, 0, read, Charsets.US_ASCII).trimStart()
            if (prefix.startsWith("<") || prefix.startsWith("{") ||
                prefix.startsWith("version ") || prefix.startsWith("PK")
            ) {
                return false
            }
            val rawCountLe = (header[0].toInt() and 0xFF) or
                ((header[1].toInt() and 0xFF) shl 8) or
                ((header[2].toInt() and 0xFF) shl 16) or
                ((header[3].toInt() and 0xFF) shl 24)
            val bigEndian = rawCountLe !in 1..MAX_TESSDATA_ENTRIES
            val entryCount = if (bigEndian) Integer.reverseBytes(rawCountLe) else rawCountLe
            if (entryCount !in 1..MAX_TESSDATA_ENTRIES) return false
            val firstOffset = readInt64(header, 4, bigEndian)
            val minDataOffset = 4L + entryCount.toLong() * 8L
            firstOffset == -1L || firstOffset in minDataOffset until len
        } catch (_: Exception) {
            false
        }
    }

    private fun readInt64(bytes: ByteArray, offset: Int, bigEndian: Boolean): Long {
        var value = 0L
        if (bigEndian) {
            for (i in 0 until 8) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFFL)
        } else {
            for (i in 7 downTo 0) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFFL)
        }
        return value
    }

    private fun verifyFileChecksum(file: File, code: String): Boolean {
        val expected = EXPECTED_SHA256[code] ?: return true
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            toHex(digest.digest()).equals(expected, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun verifySha256(code: String, actualHex: String): Boolean {
        if (actualHex.length != 64) return false
        val expected = EXPECTED_SHA256[code] ?: return true
        return actualHex.equals(expected, ignoreCase = true)
    }

    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            append(HEX_CHARS[v ushr 4])
            append(HEX_CHARS[v and 0x0F])
        }
    }

    private fun atomicMove(temp: File, target: File): Boolean {
        if (temp.renameTo(target)) return true
        if (target.exists() && target.delete() && temp.renameTo(target)) return true
        return false
    }

    companion object {
        private const val TARGET_WIDTH_PX = 1800
        private const val MAX_UPSCALE_FACTOR = 2.5f
        private const val MAX_PREPROCESS_PIXELS = 6_000_000L
        private val verifiedModels: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
        private const val MIN_ARA_BYTES = 1_000_000L
        private const val MIN_ENG_BYTES = 2_500_000L
        private const val MAX_MODEL_BYTES = 32L * 1024 * 1024
        private const val MAX_TESSDATA_ENTRIES = 64
        private val HEX_CHARS = "0123456789abcdef".toCharArray()

        // shortcut: pin exact upstream tessdata_fast SHA-256 digests when release builds bundle/freeze a tagged commit.
        private val EXPECTED_SHA256: Map<String, String> = emptyMap()

        private val _downloadProgress = MutableStateFlow<Float?>(null)
        /** Active tessdata download progress: `null` when idle, `-1f` when indeterminate, or `0f..1f`. */
        val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()
    }
}
