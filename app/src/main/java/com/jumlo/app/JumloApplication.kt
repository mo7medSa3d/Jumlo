package com.jumlo.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.jumlo.app.diagnostics.AppDiagnostics
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class JumloApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        AppDiagnostics.install(this)
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
            .onFailure { AppDiagnostics.report("startup", it) }
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build())
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // Single Coil 2.7.0 ImageLoader reusing Hilt's OkHttpClient pool with bounded memory/disk caches.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { if (::okHttpClient.isInitialized) okHttpClient.newBuilder().followRedirects(true).followSslRedirects(true).build() else OkHttpClient() }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(32L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}
