package com.jumlo.app.data.media

import android.content.Context
import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.OperationFailure
import java.io.File

object MediaRuntimeFactory {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): MediaRuntime = object : MediaRuntime {
        override fun inspect(url: String, allowHttp: Boolean, check: () -> Unit): MediaAsset = throw OperationFailure("MEDIA_BUILD_NOT_ENABLED")
        override fun download(url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
            allowHttp: Boolean, directory: File, check: () -> Unit, progress: (MediaProgress) -> Unit): Unit = throw OperationFailure("MEDIA_BUILD_NOT_ENABLED")
    }
}
