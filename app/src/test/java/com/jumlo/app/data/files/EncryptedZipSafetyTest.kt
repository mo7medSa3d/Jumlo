package com.jumlo.app.data.files

import com.jumlo.app.core.ArchiveLimits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    private fun encryptedAt(root: File, names: List<String>): File = File(root, "fixture-encrypted.zip").also { archive ->
        net.lingala.zip4j.io.outputstream.ZipOutputStream(archive.outputStream(), password).use { zip ->
            for (name in names) {
                val parameters = net.lingala.zip4j.model.ZipParameters().apply {
                    fileNameInZip = name
                    isEncryptFiles = true
                    encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                    aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
                }
                zip.putNextEntry(parameters); zip.write("bytes".toByteArray()); zip.closeEntry()
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
                val archive = encryptedAt(root, entries)
                val dest = File(root, "out")
                val failure = runCatching { EncryptedZip.extract(archive, dest, password, limit) }.exceptionOrNull()
                assertTrue("must reject the actual collision $entries: $failure",
                    failure?.message in setOf("DUPLICATE_ARCHIVE_ENTRY", "ARCHIVE_COLLISION"))
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

    @Test fun sameDisplayNamesAndUnicodeEquivalentNamesKeepEveryPayload() {
        val root = temp()
        try {
            val names = listOf("report.txt", "REPORT.txt", "caf\u00e9.txt", "cafe\u0301.txt")
            val inputs = names.mapIndexed { index, name ->
                EncryptedZipEntrySource(name, File(root, "source-$index").apply { writeText("payload-$index") })
            }
            val archive = File(root, "duplicates.zip")
            EncryptedZip.create(archive, inputs, password, limit)
            val files = EncryptedZip.extract(archive, File(root, "unpacked"), password, limit)
            assertEquals(names.size, files.size)
            assertEquals(names.indices.map { "payload-$it" }.toSet(), files.map { it.readText() }.toSet())
            assertEquals(names.size, files.map { it.name.lowercase(java.util.Locale.ROOT) }.toSet().size)
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationIsObservedWhileOneLargeEntryIsBeingCompressed() {
        val root = temp()
        try {
            val bytes = ByteArray(8 * 1024 * 1024).also { java.util.Random(53).nextBytes(it) }
            val source = File(root, "large.bin").apply { writeBytes(bytes) }
            val archive = File(root, "cancelled.zip")
            val cancelled = java.util.concurrent.CancellationException("cancel during compression")
            val failure = runCatching {
                EncryptedZip.create(archive, listOf(EncryptedZipEntrySource("large.bin", source)), password,
                    limit.copy(maxBytes = bytes.size.toLong())) {
                    // A caller can cancel while compressed bytes are growing. A
                    // blocking addFile() only checks again after the entire entry.
                    val partial = root.listFiles().orEmpty().firstOrNull { it.isFile && it.extension == "zip" && it != archive }
                    if (partial != null && partial.length() in 262_144L until bytes.size / 2L) throw cancelled
                }
            }.exceptionOrNull()
            assertTrue("Cancellation must be checked during a single large member", failure === cancelled)
            assertFalse(archive.exists())
            assertEquals(listOf("large.bin"), root.listFiles().orEmpty().map { it.name }.sorted())
            assertArrayEquals(bytes, source.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun tamperedEncryptedPayloadFailsAuthenticationWithoutPublishing() {
        val root = temp()
        try {
            val source = File(root, "payload.bin").apply { writeBytes(ByteArray(65536).also { java.util.Random(37).nextBytes(it) }) }
            val archive = File(root, "tampered.zip")
            EncryptedZip.create(archive, listOf(EncryptedZipEntrySource("payload.bin", source)), password, limit)
            val bytes = archive.readBytes()
            val nameLength = (bytes[26].toInt() and 255) or ((bytes[27].toInt() and 255) shl 8)
            val extraLength = (bytes[28].toInt() and 255) or ((bytes[29].toInt() and 255) shl 8)
            val payload = 30 + nameLength + extraLength + 40
            bytes[payload] = (bytes[payload].toInt() xor 1).toByte()
            archive.writeBytes(bytes)
            val target = File(root, "unpacked")
            assertTrue(runCatching { EncryptedZip.extract(archive, target, password, limit) }.isFailure)
            assertFalse(target.exists())
            assertArrayEquals(bytes, archive.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun cancelledExtractionDeletesOnlyItsPrivateStagingTree() {
        val root = temp()
        try {
            val source = File(root, "payload.bin").apply { writeBytes(ByteArray(512 * 1024).also { java.util.Random(39).nextBytes(it) }) }
            val archive = File(root, "source.zip")
            EncryptedZip.create(archive, listOf(EncryptedZipEntrySource("payload.bin", source)), password, limit)
            val original = archive.readBytes()
            val target = File(root, "unpacked")
            val cancelled = java.util.concurrent.CancellationException("cancel extraction")
            val failure = runCatching {
                EncryptedZip.extract(archive, target, password, limit) {
                    if (root.listFiles().orEmpty().filter { it.isDirectory }.any { dir ->
                        dir.walkTopDown().any { it.isFile && it.length() >= 65536 }
                    }) throw cancelled
                }
            }.exceptionOrNull()
            assertTrue(failure === cancelled)
            assertFalse(target.exists())
            assertFalse(root.listFiles().orEmpty().any { it.isDirectory })
            assertArrayEquals(original, archive.readBytes())
        } finally { root.deleteRecursively() }
    }
}
