package com.jumlo.app

import android.app.Application
import android.os.StrictMode
import com.jumlo.app.diagnostics.AppDiagnostics
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class JumloApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
}
