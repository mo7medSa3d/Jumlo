package com.jumlo.app.core

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Only reproducible cache entries are eligible. Trash and job staging are never cache. */
object TemporaryCleanup {
    fun removeExpired(cacheDirectory: File, cutoffEpochMs: Long, checkCancelled: () -> Unit = {}): Long {
        val cache = cacheDirectory.canonicalFile.toPath()
        var freed = 0L
        for (name in listOf("seek", "extract")) {
            checkCancelled()
            val root = cache.resolve(name)
            // Never follow a replacement symlink, including one at the allowed root itself.
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) continue
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    checkCancelled()
                    val real = try { dir.toRealPath() } catch (_: NoSuchFileException) { return FileVisitResult.SKIP_SUBTREE }
                    return if (real.startsWith(root)) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    checkCancelled()
                    if (attrs.isRegularFile && !attrs.isSymbolicLink && attrs.lastModifiedTime().toMillis() < cutoffEpochMs) {
                        try {
                            if (file.toRealPath().startsWith(root)) {
                                val current = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                                if (current.isRegularFile && current.lastModifiedTime().toMillis() < cutoffEpochMs && Files.deleteIfExists(file)) freed += current.size()
                            }
                        } catch (_: NoSuchFileException) {
                            // Concurrently removed cache entry; continue cleaning remaining files.
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                    if (exc is NoSuchFileException) FileVisitResult.CONTINUE else super.visitFileFailed(file, exc)
            })
        }
        return freed
    }
}
