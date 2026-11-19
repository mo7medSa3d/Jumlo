package com.jumlo.app.diagnostics

import android.app.Application
import android.os.Process
import com.jumlo.app.BuildConfig
import com.jumlo.app.core.ErrorLog
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
    private const val MAX_FAILURE_LOG_BYTES = 64L * 1024
    private val SCOPE_PATTERN = Regex("[a-z][a-z0-9_]{1,40}")
    private val CLASS_NAME_PATTERN = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")

    @Volatile private var store: DiagnosticStore? = null
    @Volatile private var failureFile: File? = null
    @Volatile private var reporter: CrashReportSink? = null
    private val failureLock = Any()
    private val writer = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64),
        { runnable -> Thread(runnable, "jumlo-diagnostics").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy())

    @Synchronized fun install(app: Application) {
        if (store != null) return
        store = DiagnosticStore(File(app.filesDir, "diagnostics/events.jsonl"))
        failureFile = File(app.filesDir, "diagnostics/failures.jsonl")
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

    /**
     * Records a structured, secret-free failure entry with sanitized scope, SNAKE_CASE error code,
     * and non-negative duration. Never logs exception messages, URLs, file names, or job IDs.
     */
    fun reportFailure(scope: String, errorCode: String, durationMs: Long, error: Throwable? = null) {
        if (error is java.util.concurrent.CancellationException) return
        val line = formatFailureLine(scope, errorCode, durationMs, error)
        writer.execute { writeFailureLine(line) }
        if (error != null) report(scope, error)
    }

    internal fun formatFailureLine(
        scope: String,
        errorCode: String,
        durationMs: Long,
        error: Throwable? = null,
        timestampMillis: Long = System.currentTimeMillis()
    ): String {
        val safeScope = scope.takeIf(SCOPE_PATTERN::matches) ?: "application"
        val safeCode = ErrorLog.sanitize(errorCode) ?: "UNKNOWN_ERROR"
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safeException = error?.javaClass?.name
            ?.takeIf { it.length <= 180 && CLASS_NAME_PATTERN.matches(it) }
        return buildString {
            append("{\"version\":1,\"timestamp\":").append(timestampMillis.coerceAtLeast(0L))
            append(",\"scope\":\"").append(safeScope)
            append("\",\"errorCode\":\"").append(safeCode)
            append("\",\"durationMs\":").append(safeDuration)
            if (safeException != null) {
                append(",\"exception\":\"").append(safeException).append('"')
            }
            append("}\n")
        }
    }

    private fun write(event: DiagnosticEvent) {
        runCatching { store?.record(event) }
        if (BuildConfig.CRASH_REPORTING_ENABLED) runCatching { reporter?.record(event) }
    }

    private fun writeFailureLine(line: String) {
        val target = failureFile ?: return
        runCatching {
            synchronized(failureLock) {
                target.parentFile?.mkdirs()
                val bytes = line.toByteArray(Charsets.UTF_8).size
                if (target.exists() && target.length() + bytes > MAX_FAILURE_LOG_BYTES) {
                    val archive = File(target.parentFile, target.name + ".1")
                    if (archive.exists()) archive.delete()
                    if (!target.renameTo(archive)) target.delete()
                }
                target.appendText(line, Charsets.UTF_8)
            }
        }
    }

    private fun readFailures(): String {
        val target = failureFile ?: return ""
        return runCatching {
            synchronized(failureLock) {
                val archive = File(target.parentFile, target.name + ".1")
                listOf(archive, target).filter(File::isFile).joinToString("") { source ->
                    source.inputStream().use { input ->
                        val limit = MAX_FAILURE_LOG_BYTES.toInt()
                        val buffer = ByteArray(minOf(source.length().coerceAtLeast(0L), limit.toLong()).toInt())
                        var offset = 0
                        while (offset < buffer.size) {
                            val n = input.read(buffer, offset, buffer.size - offset)
                            if (n < 0) break
                            offset += n
                        }
                        String(buffer, 0, offset, Charsets.UTF_8)
                    }
                }
            }
        }.getOrDefault("")
    }

    /** Call only on I/O dispatcher as part of an explicit user-requested diagnostic export. */
    fun read(): String = store?.read().orEmpty() + readFailures()
}
