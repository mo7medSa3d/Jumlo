package com.jumlo.app.core.diagnostics

import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** A diagnostic deliberately has no exception message, URL, document name, URI or thread name. */
class DiagnosticEvent private constructor(
    val timestampMillis: Long,
    val scope: String,
    val exceptionType: String,
    val fatal: Boolean,
    val frames: List<String>
) {
    fun toJsonLine(maxFrames: Int = 16): String = buildString {
        append("{\"version\":1,\"timestamp\":").append(timestampMillis)
        append(",\"scope\":\"").append(scope)
        append("\",\"exception\":\"").append(exceptionType)
        append("\",\"fatal\":").append(fatal).append(",\"frames\":[")
        frames.take(maxFrames.coerceIn(0, 16)).forEachIndexed { index, frame ->
            if (index > 0) append(',')
            append('"').append(frame).append('"')
        }
        append("]}")
    }

    companion object {
        private val scopes = setOf("application", "process", "workspace", "printing", "media", "pdf", "startup")
        private val className = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
        private val methodName = Regex("[A-Za-z_$<>][A-Za-z0-9_$<>-]*")

        fun capture(scope: String, error: Throwable, fatal: Boolean, timestampMillis: Long = System.currentTimeMillis()): DiagnosticEvent {
            val frames = error.stackTrace.asSequence().filter {
                it.className.length <= 180 && className.matches(it.className) &&
                    it.methodName.length <= 100 && methodName.matches(it.methodName)
            }.take(16).map { "${it.className}.${it.methodName}:${it.lineNumber.coerceAtLeast(-1)}" }.toList()
            return DiagnosticEvent(
                timestampMillis.coerceAtLeast(0), scope.takeIf(scopes::contains) ?: "application",
                error.javaClass.name.takeIf { it.length <= 180 && className.matches(it) } ?: "java.lang.Throwable",
                fatal, frames
            )
        }
    }
}

/** Two bounded private generations. No unbounded stack/message serialization is accepted. */
class DiagnosticStore(private val file: File, private val maxBytes: Long = 128L * 1024) {
    init { require(maxBytes >= 1024) }
    private val archive get() = File(file.parentFile, file.name + ".1")

    fun record(event: DiagnosticEvent) = synchronized(lock) {
        file.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("DIAGNOSTIC_DIRECTORY_FAILED") }
        var frameCount = 16
        var line = event.toJsonLine(frameCount) + "\n"
        while (line.toByteArray(Charsets.UTF_8).size > maxBytes && frameCount > 0) {
            line = event.toJsonLine(--frameCount) + "\n"
        }
        if (file.exists() && file.length() + line.toByteArray(Charsets.UTF_8).size > maxBytes) {
            if (archive.exists() && !archive.delete()) throw IOException("DIAGNOSTIC_ROTATION_FAILED")
            if (!file.renameTo(archive)) throw IOException("DIAGNOSTIC_ROTATION_FAILED")
        }
        file.appendText(line, Charsets.UTF_8)
    }

    fun read(): String = synchronized(lock) {
        listOf(archive, file).filter(File::isFile).joinToString("") { source ->
            source.inputStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var remaining = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(remaining, buffer.size))
                    if (count < 0) break
                    if (count > 0) { output.write(buffer, 0, count); remaining -= count }
                }
                output.toString("UTF-8")
            }
        }
    }

    fun clear() = synchronized(lock) {
        for (entry in listOf(file, archive)) if (entry.exists() && !entry.delete()) throw IOException("DIAGNOSTIC_DELETE_FAILED")
    }

    companion object { private val lock = Any() }
}

/** Always preserves Android's original crash/termination handling, including when logging itself fails. */
class DelegatingCrashHandler(
    private val record: (DiagnosticEvent) -> Unit,
    private val delegate: (Thread, Throwable) -> Unit
) : Thread.UncaughtExceptionHandler {
    private val recording = AtomicBoolean(false)
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            if (recording.compareAndSet(false, true)) {
                try { record(DiagnosticEvent.capture("process", error, fatal = true)) }
                catch (_: Throwable) { /* A full disk or reporting SDK must never hide the original crash. */ }
                finally { recording.set(false) }
            }
        } finally { delegate(thread, error) }
    }
}
