package com.jumlo.app.data.files

import com.jumlo.app.core.ArchiveLimits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** JVM tests of the real zip4j integration. Authored here; Gradle/device runs required. */
class EncryptedZipSafetyTest {
    private val password = "session-only-password".toCharArray()
    private val limit = ArchiveLimits(maxBytes = 1024 * 1024, maxEntries = 16, maxDepth = 8)

    private fun temp(): File = Files.createTempDirectory("encrypted-zip-safety-").toFile()

    private fun zipAt(root: File, names: List<String>): File = File(root, "fixture.zip").also { archive ->
        ZipOutputStream(archive.outputStream()).use { zip ->
            names.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                if (!name.endsWith('/')) zip.write("bytes".toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test fun rejectsTraversalAbsoluteWindowsAndNoncanonicalNamesWithoutLeavingOutputs() {
        val attacks = listOf(
            "../escape.txt", "nested/../../escape.txt", "/absolute.txt",
            "C:/absolute.txt", "nested\\escape.txt", "./same.txt",
            "nested/../escape.txt", "nested//file.txt", "folder/./file.txt"
        )
        attacks.forEach { name ->
            val root = temp()
            try {
                val archive = zipAt(root, listOf(name))
                val dest = File(root, "out")
                assertTrue("must reject $name", runCatching { EncryptedZip.extract(archive, dest, password, limit) }.isFailure)
                assertFalse("must not leave $name", dest.exists())
                assertFalse("must not escape $name", File(root.parentFile, "escape.txt").exists())
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun rejectsCaseAndUnicodeCanonicalCollisionsBeforeWriting() {
        val collisions = listOf(
            listOf("a.txt", "A.txt"),
            listOf("caf\u00e9.txt", "cafe\u0301.txt"),
            listOf("parent", "parent/child.txt")
        )
        collisions.forEach { entries ->
            val root = temp()
            try {
                val archive = zipAt(root, entries)
                val dest = File(root, "out")
                assertTrue("must reject $entries", runCatching { EncryptedZip.extract(archive, dest, password, limit) }.isFailure)
                assertFalse(dest.exists())
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun refusesToEraseExistingTargetOrExistingArchive() {
        val root = temp()
        try {
            val source = File(root, "source.txt").apply { writeText("private source") }
            val existingZip = File(root, "existing.zip").apply { writeText("KEEP ORIGINAL ZIP") }
            assertTrue(runCatching {
                EncryptedZip.create(existingZip, listOf(EncryptedZipEntrySource("source.txt", source)), password, limit)
            }.isFailure)
            assertTrue(existingZip.readText() == "KEEP ORIGINAL ZIP")

            val archive = zipAt(root, listOf("../escape.txt"))
            val existingTarget = File(root, "out").apply { mkdirs() }
            val sentinel = File(existingTarget, "important.dat").apply { writeText("KEEP ORIGINAL FILE") }
            assertTrue(runCatching { EncryptedZip.extract(archive, existingTarget, password, limit) }.isFailure)
            assertTrue(sentinel.readText() == "KEEP ORIGINAL FILE")
        } finally { root.deleteRecursively() }
    }

    @Test fun encryptedRoundTripCreatesVerifiedBytesAndBadPasswordPublishesNothing() {
        val root = temp()
        try {
            val content = byteArrayOf(0, 1, 2, 127, -1, 21)
            val source = File(root, "bytes.bin").apply { writeBytes(content) }
            val archive = File(root, "encrypted.zip")
            EncryptedZip.create(archive, listOf(EncryptedZipEntrySource("bytes.bin", source)), password, limit)
            assertTrue(archive.isFile)
            val wrong = File(root, "wrong")
            assertTrue(runCatching { EncryptedZip.extract(archive, wrong, "wrong-password".toCharArray(), limit) }.isFailure)
            assertFalse(wrong.exists())
            val unpacked = EncryptedZip.extract(archive, File(root, "good"), password, limit)
            assertTrue(unpacked.size == 1)
            assertArrayEquals(content, unpacked.single().readBytes())
            assertArrayEquals(content, source.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun plaintextArchiveIsRejectedEvenWithAProvidedPassword() {
        val root = temp()
        try {
            val archive = zipAt(root, listOf("safe.txt"))
            val target = File(root, "plaintext")
            assertTrue(runCatching { EncryptedZip.extract(archive, target, password, limit) }.isFailure)
            assertFalse(target.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun entryAndByteLimitsLeaveNoPublishedArtifacts() {
        val root = temp()
        try {
            val original = File(root, "big.bin").apply { writeBytes(ByteArray(20)) }
            val output = File(root, "limit.zip")
            assertTrue(runCatching {
                EncryptedZip.create(output, listOf(EncryptedZipEntrySource("big.bin", original)), password,
                    limit.copy(maxBytes = 8))
            }.isFailure)
            assertFalse(output.exists())
            val archive = zipAt(root, listOf("a", "b", "c"))
            val dest = File(root, "bounded")
            assertTrue(runCatching { EncryptedZip.extract(archive, dest, password, limit.copy(maxEntries = 2)) }.isFailure)
            assertFalse(dest.exists())
            assertArrayEquals(ByteArray(20), original.readBytes())
        } finally { root.deleteRecursively() }
    }
}
