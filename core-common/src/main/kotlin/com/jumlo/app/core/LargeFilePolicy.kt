package com.jumlo.app.core

import java.io.File
import java.nio.file.Files
import java.util.UUID

/** Allow large fan-outs without creating thousands of separate published records. */
enum class LargeResultStrategy {
    INDIVIDUAL, ARCHIVE;

    companion object {
        fun forPageOutputs(pageCount: Int, individualLimit: Int): LargeResultStrategy {
            require(pageCount > 0 && individualLimit > 0) { "INVALID_PAGE_COUNT" }
            return if (pageCount > individualLimit) ARCHIVE else INDIVIDUAL
        }
    }
}

/** An imported file is immutable; reuse it instead of making an additional multi-GB seek copy.
 * Reject forged locations, symlinks, missing data or another source's cached path. */
object SourceSnapshotPolicy {
    fun cachedFile(privateDirectory: File, sourceId: String, cachedPath: String?): File? {
        if (cachedPath.isNullOrBlank()) return null
        if (runCatching { UUID.fromString(sourceId).toString() == sourceId }.getOrDefault(false).not()) return null
        val expected = File(privateDirectory, "$sourceId.bin")
        val provided = File(cachedPath)
        if (provided.toPath().toAbsolutePath().normalize() != expected.toPath().toAbsolutePath().normalize()) return null
        if (Files.isSymbolicLink(provided.toPath()) || !provided.isFile || provided.length() <= 0L) return null
        return provided.canonicalFile.takeIf { it == expected.canonicalFile }
    }
}
