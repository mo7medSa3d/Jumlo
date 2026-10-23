package com.jumlo.app.diagnostics

import android.app.Application
import android.os.Process
import com.jumlo.app.BuildConfig
import com.jumlo.app.core.diagnostics.DelegatingCrashHandler
import com.jumlo.app.core.diagnostics.DiagnosticEvent
import com.jumlo.app.core.diagnostics.DiagnosticStore
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Implement with Crashlytics or another SDK; only this already-sanitized event crosses the hook. */
fun interface CrashReportSink { fun record(event: DiagnosticEvent) }

object AppDiagnostics {
    @Volatile private var store: DiagnosticStore? = null
    @Volatile private var reporter: CrashReportSink? = null
    private val writer = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64),
        { runnable -> Thread(runnable, "jumlo-diagnostics").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy())

    @Synchronized fun install(app: Application) {
        if (store != null) return
        store = DiagnosticStore(File(app.filesDir, "diagnostics/events.jsonl"))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(DelegatingCrashHandler(::write) { thread, error ->
            if (previous != null) previous.uncaughtException(thread, error)
            else { Process.killProcess(Process.myPid()); exitProcess(10) }
        })
    }

    /** Disabled by default. Shipping an SDK also requires its own privacy/consent configuration. */
    fun registerReporter(sink: CrashReportSink?) {
        reporter = if (BuildConfig.CRASH_REPORTING_ENABLED) sink else null
    }

    fun report(scope: String, error: Throwable, fatal: Boolean = false) {
        if (error is java.util.concurrent.CancellationException) return
        val event = DiagnosticEvent.capture(scope, error, fatal)
        if (fatal) write(event) else writer.execute { write(event) }
    }

    private fun write(event: DiagnosticEvent) {
        runCatching { store?.record(event) }
        if (BuildConfig.CRASH_REPORTING_ENABLED) runCatching { reporter?.record(event) }
    }

    /** Call only on I/O dispatcher as part of an explicit user-requested diagnostic export. */
    fun read(): String = store?.read().orEmpty()
}
