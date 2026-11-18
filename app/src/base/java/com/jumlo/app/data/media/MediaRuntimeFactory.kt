package com.jumlo.app.data.media

import android.content.Context
import okhttp3.OkHttpClient

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
        serviceToken: () -> String = { "" },
        serviceConfiguration: (() -> Pair<String, String>)? = null
    ): MediaRuntime {
        val native = NativeDirectMediaRuntime(client, sessions)
        val engines = buildList {
            add(RemoteExtractorRuntime(serviceUrl, client, serviceToken,
                allowDebugLoopback = com.jumlo.app.BuildConfig.DEBUG, configurationProvider = serviceConfiguration))
            add(native)
        }
        return RoutingMediaRuntime(engines)
    }
}
