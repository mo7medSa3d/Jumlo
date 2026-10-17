package com.jumlo.app.core

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
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
                    return if (dir.toRealPath().startsWith(root)) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    checkCancelled()
                    if (attrs.isRegularFile && !attrs.isSymbolicLink && attrs.lastModifiedTime().toMillis() < cutoffEpochMs && file.toRealPath().startsWith(root)) {
                        val current = Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                        if (current.isRegularFile && current.lastModifiedTime().toMillis() < cutoffEpochMs && Files.deleteIfExists(file)) freed += current.size()
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return freed
    }
}
