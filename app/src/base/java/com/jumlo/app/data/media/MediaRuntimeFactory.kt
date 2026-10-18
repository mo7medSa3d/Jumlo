package com.jumlo.app.data.media

import android.content.Context
import okhttp3.OkHttpClient

/** The standard APK contains no additional executable or Python runtime. */
object MediaRuntimeFactory {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context,
               client: OkHttpClient, sessions: MediaSessionStore): MediaRuntime =
        NativeDirectMediaRuntime(client, sessions)
}
