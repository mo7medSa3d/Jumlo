package com.jumlo.app.hosttest

import com.jumlo.app.core.TemporaryCleanup
import java.io.File
import java.nio.file.Files

fun main() {
    var passed = 0
    fun checkCase(name: String, test: (File) -> Unit) {
        val root = Files.createTempDirectory("jumlo-cleanup-").toFile()
        try {
            test(root)
            passed++
            println("PASS $name")
        } finally { root.deleteRecursively() }
    }
    fun file(root: File, name: String, time: Long): File = File(root, name).apply {
        parentFile.mkdirs(); writeText("irreplaceable test bytes"); check(setLastModified(time))
    }
    val cutoff = 1_700_000_000_000L
    checkCase("cleanup deletes only expired seek and extract files") { root ->
        val expired = file(root, "cache/seek/old/page.bin", cutoff - 10_000)
        val extracted = file(root, "cache/extract/old/image.png", cutoff - 10_000)
        val fresh = file(root, "cache/seek/active/page.bin", cutoff + 10_000)
        val expected = expired.length() + extracted.length()
        check(TemporaryCleanup.removeExpired(File(root, "cache"), cutoff) == expected)
        check(!expired.exists() && !extracted.exists() && fresh.exists())
    }
    checkCase("cleanup never purges trash outputs originals or resumable staging") { root ->
        val kept = listOf("processing/trash/a.pdf", "processing/results/a.pdf", "processing/staging/a.part", "inbound/source.bin", "cache/other/data.bin", "transfers/job.part")
            .map { file(root, it, cutoff - 10_000) }
        TemporaryCleanup.removeExpired(File(root, "cache"), cutoff)
        check(kept.all { it.readText() == "irreplaceable test bytes" })
    }
    checkCase("cleanup never follows a symbolic directory into originals") { root ->
        val original = file(root, "original/document.pdf", cutoff - 10_000)
        val seek = File(root, "cache/seek").apply { mkdirs() }
        Files.createSymbolicLink(File(seek, "outside").toPath(), original.parentFile.toPath())
        TemporaryCleanup.removeExpired(File(root, "cache"), cutoff)
        check(original.readText() == "irreplaceable test bytes")
    }
    checkCase("cleanup refuses a substituted cleanup root") { root ->
        val original = file(root, "original/document.pdf", cutoff - 10_000)
        val cache = File(root, "cache").apply { mkdirs() }
        Files.createSymbolicLink(File(cache, "seek").toPath(), original.parentFile.toPath())
        TemporaryCleanup.removeExpired(cache, cutoff)
        check(original.exists())
    }
    checkCase("cleanup preserves cutoff-boundary files") { root ->
        val sameTime = file(root, "cache/extract/same.bin", cutoff)
        check(TemporaryCleanup.removeExpired(File(root, "cache"), cutoff) == 0L)
        check(sameTime.exists())
    }
    checkCase("cancellation stops before deleting the next file") { root ->
        val pending = file(root, "cache/seek/pending.bin", cutoff - 10_000)
        var cancelled = false
        try { TemporaryCleanup.removeExpired(File(root, "cache"), cutoff) { throw InterruptedException("cancel") } }
        catch (_: InterruptedException) { cancelled = true }
        check(cancelled && pending.exists())
    }
    println("$passed workspace safety tests passed")
}
