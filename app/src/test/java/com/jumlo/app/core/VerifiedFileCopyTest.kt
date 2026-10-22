package com.jumlo.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

class VerifiedFileCopyTest {
    private fun fixture(block:(File,File)->Unit) {
        val directory=Files.createTempDirectory("jumlo-copy-publish-").toFile()
        try {
            val source=File(directory,"source.bin").apply { writeBytes(ByteArray(180_000) { (it*29).toByte() }) }
            block(source,File(directory,"output.bin"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun existingDestinationIsNeverRemovedOnCollision() = fixture { source,target ->
        target.writeText("previous completed output")
        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) {
            VerifiedFileCopy.copy(source,target,Hashing.sha256(source))
        }
        assertEquals("previous completed output",target.readText())
        assertTrue(source.exists())
    }

    @Test fun cancellationRemovesOnlyThePartialCopy() = fixture { source,target ->
        var checks=0
        assertThrows(CancellationException::class.java) {
            VerifiedFileCopy.copy(source,target,Hashing.sha256(source)) {
                if(++checks>=3) throw CancellationException("cancelled")
            }
        }
        assertFalse(target.exists())
        assertEquals(180_000L,source.length())
    }

    @Test fun corruptOutputIsNotPublished() = fixture { source,target ->
        assertThrows(IllegalStateException::class.java) {
            VerifiedFileCopy.copy(source,target,"0".repeat(64))
        }
        assertFalse(target.exists())
        assertTrue(source.exists())
    }

    @Test fun successfulCopyHasExactBytesAndKeepsSource() = fixture { source,target ->
        VerifiedFileCopy.copy(source,target,Hashing.sha256(source))
        assertArrayEquals(source.readBytes(),target.readBytes())
    }
}
