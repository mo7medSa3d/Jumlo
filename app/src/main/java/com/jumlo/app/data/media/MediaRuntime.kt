package com.jumlo.app.data.media

import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.OperationFailure
import java.io.File

/** Analyzer/downloader contract shared by the normal full and explicit lightweight builds. */
interface MediaRuntime {
    fun inspect(url: String, allowHttp: Boolean, check: () -> Unit = {}): MediaAsset
    fun download(url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
        allowHttp: Boolean, directory: File, check: () -> Unit,
        progress: (MediaProgress) -> Unit)
    fun refreshExtractor(): String = "MEDIA_BUILD_NOT_ENABLED"
}

/** Native loading failures are recoverable UI errors; cancellation is untouched. */
internal inline fun <T> guardMediaRuntime(action:()->T):T = try {
    action()
} catch (_:LinkageError) {
    throw OperationFailure("MEDIA_RUNTIME_INITIALIZATION_FAILED")
} catch (_:OutOfMemoryError) {
    throw OperationFailure("LOW_MEMORY")
}
