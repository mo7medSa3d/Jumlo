package com.jumlo.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.jumlo.app.diagnostics.AppDiagnostics
import com.jumlo.app.worker.ActiveTaskTracker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltAndroidApp
class JumloApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val startedCount = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val foreground = startedCount.incrementAndGet() > 0
                isAppInForeground = foreground
                ActiveTaskTracker.setAppForeground(foreground)
            }

            override fun onActivityStopped(activity: Activity) {
                val foreground = startedCount.updateAndGet { (it - 1).coerceAtLeast(0) } > 0
                isAppInForeground = foreground
                ActiveTaskTracker.setAppForeground(foreground)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        AppDiagnostics.install(this)
        // Proactively warm up Google ML Kit Document Scanner (SCANNER_MODE_FULL) so Smart Scan & ID Card open fast.
        runCatching {
            val scanner = com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(
                com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
                    .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
            )
            val segmenter = com.google.mlkit.vision.segmentation.subject.SubjectSegmentation.getClient(
                com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .enableForegroundConfidenceMask()
                    .build()
            )
            val installer = com.google.android.gms.common.moduleinstall.ModuleInstall.getClient(this)
            installer.areModulesAvailable(scanner, segmenter).addOnSuccessListener { avail ->
                if (!avail.areModulesAvailable()) {
                    installer.installModules(
                        com.google.android.gms.common.moduleinstall.ModuleInstallRequest.newBuilder()
                            .addApi(scanner)
                            .addApi(segmenter)
                            .build()
                    )
                }
            }
        }
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
        .okHttpClient {
            val base = if (::okHttpClient.isInitialized) okHttpClient.newBuilder() else OkHttpClient.Builder()
            base.followRedirects(true)
                .followSslRedirects(true)
                .writeTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }
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
