package com.jumlo.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
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

/** Unbundled Latin-script OCR. Arabic UI does not imply Arabic OCR model support. */
@Singleton
class MlKitOcrEngine @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun recognize(bitmap: Bitmap): String {
        currentCoroutineContext().ensureActive()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
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
            currentCoroutineContext().ensureActive()
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitNativeCompletion()
            currentCoroutineContext().ensureActive()
            return text.textBlocks.joinToString("\n\n") { block -> block.lines.joinToString("\n") { it.text } }.trim()
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
