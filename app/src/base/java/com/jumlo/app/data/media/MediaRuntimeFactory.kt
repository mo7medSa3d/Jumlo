package com.jumlo.app.data.media

import android.content.Context
import com.jumlo.app.core.media.AudioOutput
import com.jumlo.app.core.media.MediaAsset
import com.jumlo.app.core.media.MediaChoice
import com.jumlo.app.core.media.MediaProgress
import com.jumlo.app.data.jobs.OperationFailure
import okhttp3.OkHttpClient
import java.io.File

/**
 * The explicitly selected lightweight APK contains no executable or Python runtime.
 *
 * The extractor therefore lives in a service. When the user has configured one, the
 * service answers first and the on-device direct fetcher is the fallback for plain
 * media links. Without a configured service the app only offers direct links, which
 * is the supported capability of the lightweight build.
 */
object MediaRuntimeFactory {
    fun create(
        @Suppress("UNUSED_PARAMETER") context: Context,
        client: OkHttpClient,
        sessions: MediaSessionStore,
        serviceUrl: () -> String = { "" },
        serviceToken: () -> String = { "" }
    ): MediaRuntime {
        val native = NativeDirectMediaRuntime(client, sessions)
        val engines = buildList {
            add(RemoteExtractorRuntime(serviceUrl, client, serviceToken))
            add(native)
        }
        return RoutingMediaRuntime(engines)
    }
}
