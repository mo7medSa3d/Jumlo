package com.jumlo.app.data.media

import com.jumlo.app.core.media.*
import java.io.File

/** A real, optional APK-packaged runtime. The base variant never simulates results. */
interface MediaRuntime {
    fun inspect(url: String, allowHttp: Boolean, check: () -> Unit = {}): MediaAsset
    fun download(url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
        allowHttp: Boolean, directory: File, check: () -> Unit,
        progress: (MediaProgress) -> Unit)
    fun refreshExtractor(): String = "MEDIA_BUILD_NOT_ENABLED"
}
