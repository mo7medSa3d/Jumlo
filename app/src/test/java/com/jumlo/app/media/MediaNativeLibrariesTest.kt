package com.jumlo.app.media

import com.jumlo.app.core.Hashing
import com.jumlo.app.core.media.MediaNativeLibraries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Properties

class MediaNativeLibrariesTest {
    private fun elf(machine: Int = 183, elfClass: Int = 2, alignment: Long = 16384,
        address: Long = 0, relro: Boolean = false, writableTail: Boolean = true): ByteArray {
        val bytes = ByteArray(if (relro) 0x5000 else 256)
        bytes[0] = 0x7f; bytes[1] = 'E'.code.toByte(); bytes[2] = 'L'.code.toByte(); bytes[3] = 'F'.code.toByte()
        bytes[4] = elfClass.toByte(); bytes[5] = 1
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        data.putShort(18, machine.toShort()); data.putLong(32, 64)
        data.putShort(54, 56); data.putShort(56, if (relro) 3 else 1)
        fun segment(at: Int, type: Int, flags: Int, offset: Long, vaddr: Long, size: Long, align: Long) {
            data.putInt(at, type); data.putInt(at + 4, flags); data.putLong(at + 8, offset)
            data.putLong(at + 16, vaddr); data.putLong(at + 32, size); data.putLong(at + 40, size)
            data.putLong(at + 48, align)
        }
        segment(64, 1, 4, 0, address, 256, alignment)
        if (relro) {
            segment(120, 1, 6, 0x4000, 0x4000, if (writableTail) 0x1000 else 0x100, 16384)
            segment(176, 0x6474e552, 4, 0x4000, 0x4000, 0x100, 1)
        }
        return bytes
    }

    private fun rejects(code: String, bytes: ByteArray) {
        val error = runCatching { MediaNativeLibraries.require16KiB(bytes, "arm64-v8a") }.exceptionOrNull()
        assertTrue(error.toString(), error is IllegalArgumentException)
        assertEquals(code, error?.message)
    }

    @Test fun abiFollowsExecutableClassAndMachine() {
        assertEquals("arm64-v8a", MediaNativeLibraries.processAbi(elf()))
        assertEquals("x86_64", MediaNativeLibraries.processAbi(elf(machine = 62)))
        assertNull(MediaNativeLibraries.processAbi(elf(machine = 40, elfClass = 1)))
        assertNull(MediaNativeLibraries.processAbi(elf(machine = 3, elfClass = 1)))
        assertTrue(runCatching { MediaNativeLibraries.processAbi(elf(machine = 40)) }.isFailure)
    }

    @Test fun originalFourKiBLayoutIsRejected() = rejects("MEDIA_NATIVE_ALIGNMENT", elf(alignment = 4096))
    @Test fun changingOnlyAlignmentDoesNotMakeOffsetsCongruent() = rejects("MEDIA_NATIVE_ALIGNMENT", elf(address = 4096))
    @Test fun truncatedProgramHeadersAreRejected() = rejects("MEDIA_NATIVE_ELF_INVALID", elf().copyOf(64))
    @Test fun wrongArchitectureIsRejected() = rejects("MEDIA_NATIVE_ELF_INVALID", elf(machine = 62))
    @Test fun relroCannotProtectTrailingWritableData() = rejects("MEDIA_NATIVE_RELRO_ALIGNMENT", elf(relro = true))
    @Test fun separateRelroLoadWithoutTrailingDataIsValid() {
        MediaNativeLibraries.require16KiB(elf(relro = true, writableTail = false), "arm64-v8a")
    }

    @Test fun actualApkRepairAssetsMatchTheirPinsAndHaveValidLoadableLayouts() {
        val root = listOf(File("app/src/media/assets/media/native-overrides"),
            File("src/media/assets/media/native-overrides")).first { it.isDirectory }
        val manifest = Properties().apply { File(root, "manifest.properties").inputStream().use(::load) }
        for (abi in listOf("arm64-v8a", "x86_64")) for (name in MediaNativeLibraries.repairedNames) {
            val file = File(root, "$abi/$name")
            assertEquals(manifest.getProperty("$abi.$name.sha256"), file.inputStream().use { Hashing.sha256(it) })
            MediaNativeLibraries.require16KiB(file.readBytes(), abi)
        }
    }
}
