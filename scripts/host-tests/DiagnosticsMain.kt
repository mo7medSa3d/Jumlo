package com.jumlo.app.hosttest

import com.jumlo.app.core.diagnostics.DiagnosticEvent
import com.jumlo.app.core.diagnostics.DiagnosticStore
import com.jumlo.app.core.diagnostics.DelegatingCrashHandler
import java.nio.file.Files

fun main() {
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        body(); passed++; println("PASS $name")
    }
    test("diagnostic contains source frames but never exception messages, URLs or email") {
        val failure = IllegalArgumentException("https://example.test/a?token=super-secret user@example.test /private/customer.pdf")
        failure.stackTrace = arrayOf(
            StackTraceElement("com.jumlo.app.ui.AppViewModel", "intake", "AppViewModel.kt", 42),
            StackTraceElement("https://example.test/?token=secret", "bad/path", "customer.pdf", 7)
        )
        val event = DiagnosticEvent.capture("workspace", failure, fatal = false, timestampMillis = 123L)
        val json = event.toJsonLine()
        check(json.contains("com.jumlo.app.ui.AppViewModel.intake:42"))
        for (privateValue in listOf("super-secret", "https://", "user@", "customer.pdf", "bad/path")) {
            check(!json.contains(privateValue)) { "Private value leaked: $privateValue" }
        }
        check(json.contains("java.lang.IllegalArgumentException"))
        check(json.contains("123"))
    }
    test("untrusted scope is replaced and frames are bounded") {
        val failure = RuntimeException("private")
        failure.stackTrace = Array(1000) { StackTraceElement("com.jumlo.Test", "run", "Test.kt", it) }
        val event = DiagnosticEvent.capture("token=secret", failure, true)
        check(event.scope == "application")
        check(event.frames.size == 16)
    }
    test("logger failure never prevents original uncaught handler") {
        val failure = IllegalStateException("private")
        var received: Throwable? = null
        val handler = DelegatingCrashHandler({ throw java.io.IOException("disk full") }) { _, error -> received = error }
        handler.uncaughtException(Thread.currentThread(), failure)
        check(received === failure)
    }
    test("crash capture records before delegating exactly once") {
        val events = mutableListOf<String>()
        val handler = DelegatingCrashHandler({ event ->
            check(event.fatal); events += "record"
        }) { _, _ -> events += "delegate" }
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("private"))
        check(events == listOf("record", "delegate"))
    }
    test("structured store rotates and clears both generations") {
        val folder = Files.createTempDirectory("jumlo-diagnostics-").toFile()
        try {
            val file = folder.resolve("events.jsonl")
            val store = DiagnosticStore(file, maxBytes = 1024L)
            repeat(40) { store.record(DiagnosticEvent.capture("workspace", IllegalStateException("private"), false, it.toLong())) }
            val text = store.read()
            check(text.lines().filter(String::isNotBlank).all { it.startsWith("{\"version\":1,") && it.endsWith("}") })
            check(!text.contains("private"))
            check(file.length() <= 1024L)
            check(folder.resolve("events.jsonl.1").length() <= 1024L)
            store.clear()
            check(store.read().isEmpty())
        } finally { folder.deleteRecursively() }
    }
    println("Diagnostics: $passed/5 passed")
}
