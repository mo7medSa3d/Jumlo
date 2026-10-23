package com.jumlo.app.media

import com.jumlo.app.core.media.MediaNativeLibraries
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

class MediaNativeAliasCleanupTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun packages(): File = temporary.newFolder("packages").also { root ->
        for (runtime in listOf("python", "ffmpeg")) {
            val directory = File(root, "$runtime/usr/lib").apply { mkdirs() }.toPath()
            Files.write(directory.resolve("liblzma.so.5.8.1"), byteArrayOf(1, 2, 3, 4))
            Files.createSymbolicLink(directory.resolve("liblzma.so.5"), Path.of("liblzma.so.5.8.1"))
        }
    }

    private fun aliases(root: File) = listOf("python", "ffmpeg").map {
        File(root, "$it/usr/lib/liblzma.so").toPath()
    }

    private fun rejects(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue(error.toString(), error is IllegalArgumentException || error is IllegalStateException)
    }

    @Test fun removesBothLegacyAliasesAndPreservesPrivateLibrariesByteForByte() {
        val root = packages()
        aliases(root).forEach { Files.createSymbolicLink(it, Path.of("liblzma.so.5.8.1")) }
        MediaNativeLibraries.removePlatformLzmaAliases(root)
        for (alias in aliases(root)) {
            assertFalse(Files.exists(alias, NOFOLLOW_LINKS))
            assertTrue(Files.isSymbolicLink(alias.resolveSibling("liblzma.so.5")))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(alias.resolveSibling("liblzma.so.5")))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(alias.resolveSibling("liblzma.so.5.8.1")))
        }
    }

    @Test fun alreadyCleanAndRepeatedInitializationLeavePrivateFilesUnchanged() {
        val root = packages()
        repeat(2) { MediaNativeLibraries.removePlatformLzmaAliases(root) }
        aliases(root).forEach {
            assertFalse(Files.exists(it, NOFOLLOW_LINKS))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(it.resolveSibling("liblzma.so.5")))
        }
    }

    @Test fun danglingAliasesAreRemovedWithNoFollowPresenceChecks() {
        val root = packages()
        aliases(root).forEach { Files.createSymbolicLink(it, Path.of("missing-old-version.so")) }
        MediaNativeLibraries.removePlatformLzmaAliases(root)
        aliases(root).forEach { assertFalse(Files.exists(it, NOFOLLOW_LINKS)) }
    }

    @Test fun aliasRemovalNeverDeletesAnOutsideTarget() {
        val root = packages()
        val outside = temporary.newFile("outside-library").apply { writeText("keep me") }
        aliases(root).forEach { Files.createSymbolicLink(it, outside.toPath()) }
        MediaNativeLibraries.removePlatformLzmaAliases(root)
        assertArrayEquals("keep me".toByteArray(), outside.readBytes())
        aliases(root).forEach { assertFalse(Files.exists(it, NOFOLLOW_LINKS)) }
    }

    @Test fun unexpectedUnversionedRegularFileFailsWithoutDeletingIt() {
        val root = packages()
        val file = aliases(root).first()
        Files.write(file, byteArrayOf(9, 8, 7))
        rejects { MediaNativeLibraries.removePlatformLzmaAliases(root) }
        assertArrayEquals(byteArrayOf(9, 8, 7), Files.readAllBytes(file))
    }

    @Test fun unexpectedUnversionedDirectoryFailsWithoutRecursiveDeletion() {
        val root = packages()
        val directory = Files.createDirectory(aliases(root).first())
        Files.write(directory.resolve("retained"), byteArrayOf(4, 2))
        rejects { MediaNativeLibraries.removePlatformLzmaAliases(root) }
        assertArrayEquals(byteArrayOf(4, 2), Files.readAllBytes(directory.resolve("retained")))
    }

    @Test fun redirectedRuntimeDirectoryIsRejectedBeforeTouchingItsContents() {
        val root = packages()
        val original = File(root, "python/usr/lib").toPath()
        val outside = temporary.root.toPath().resolve("outside-directory")
        Files.move(original, outside)
        Files.createSymbolicLink(outside.resolve("liblzma.so"), Path.of("liblzma.so.5.8.1"))
        Files.createSymbolicLink(original, outside)
        rejects { MediaNativeLibraries.removePlatformLzmaAliases(root) }
        assertTrue(Files.isSymbolicLink(outside.resolve("liblzma.so")))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(outside.resolve("liblzma.so.5")))
    }
}
