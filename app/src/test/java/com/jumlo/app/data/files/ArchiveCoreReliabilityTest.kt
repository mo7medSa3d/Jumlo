package com.jumlo.app.data.files

import com.jumlo.app.core.ArchiveCore
import com.jumlo.app.core.ArchiveLimits
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ArchiveCoreReliabilityTest {
    private val limits = ArchiveLimits(1024 * 1024, 16, 8)

    private fun fixture(root: File): File = File(root, "archive.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("report.txt"))
            zip.write("the original report bytes".toByteArray())
            zip.closeEntry()
        }
    }

    @Test fun extractionRejectsCentralDirectoryCrcMismatchAndPublishesNothing() {
        val root = Files.createTempDirectory("zip-central-crc-").toFile()
        try {
            val file = fixture(root)
            val bytes = file.readBytes()
            val central = bytes.indices.first { i -> i + 4 < bytes.size && bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4b.toByte() && bytes[i + 2] == 1.toByte() && bytes[i + 3] == 2.toByte() }
            bytes[central + 16] = (bytes[central + 16].toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertEquals(1, ZipFile(file).use { it.size() })
            val out = File(root, "extracted")
            assertTrue("The local payload and central directory must agree",
                runCatching { ArchiveCore.extract(file, out, limits) }.isFailure)
            assertFalse(out.exists())
            assertArrayEquals(bytes, file.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun extractionRejectsCentralDirectorySizeMismatchAndPublishesNothing() {
        val root = Files.createTempDirectory("zip-central-size-").toFile()
        try {
            val file = fixture(root)
            val bytes = file.readBytes()
            val central = bytes.indices.first { i -> i + 4 < bytes.size && bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4b.toByte() && bytes[i + 2] == 1.toByte() && bytes[i + 3] == 2.toByte() }
            bytes[central + 24] = (bytes[central + 24].toInt() + 1).toByte()
            file.writeBytes(bytes)
            val out = File(root, "extracted")
            assertTrue("The extracted byte count must match the central directory",
                runCatching { ArchiveCore.extract(file, out, limits) }.isFailure)
            assertFalse(out.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun ordinaryArchiveExtractsTheSameBytes() {
        val root = Files.createTempDirectory("zip-valid-").toFile()
        try {
            val outputs = ArchiveCore.extract(fixture(root), File(root, "extracted"), limits)
            assertEquals("the original report bytes", outputs.single().readText())
        } finally { root.deleteRecursively() }
    }
}
