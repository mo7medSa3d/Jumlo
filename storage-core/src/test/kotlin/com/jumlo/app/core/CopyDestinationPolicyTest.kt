package com.jumlo.app.core

import com.jumlo.app.model.CollisionPolicy
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CopyDestinationPolicyTest {
    private val existing = listOf("source-b" to "Archive.zip")

    @Test fun keepBothGeneratesSafeDeterministicName() {
        assertEquals("Archive (1).zip", CopyDestinationPolicy.name(
            "source-a", "Archive.zip", existing, move = false, policy = CollisionPolicy.KEEP_BOTH))
    }

    @Test fun skipNeverSilentlyOverwritesMatchingDestination() {
        assertNull(CopyDestinationPolicy.name(
            "source-a", "archive.zip", existing, move = false, policy = CollisionPolicy.SKIP))
    }

    @Test fun storagePathsNormalizesMimeCaseAndHandlesDotfileCollisions() {
        assertEquals("PDF", StoragePaths.category("Application/PDF; charset=utf-8"))
        assertEquals("Images", StoragePaths.category("IMAGE/JPEG"))
        assertEquals(".pdf (2)", StoragePaths.uniqueName(".pdf", setOf(".pdf")))
    }

    @Test fun moveToSameDocumentAndUnsafeAtomicReplaceFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            CopyDestinationPolicy.name("source-b", "Archive.zip", existing,
                move = true, policy = CollisionPolicy.KEEP_BOTH)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CopyDestinationPolicy.name("source-a", "Archive.zip", existing,
                move = false, policy = CollisionPolicy.REPLACE)
        }
    }

    @Test fun publicationAndTrashRejectInvalidIdentityAndCorruptSizeWithStableCodes() {
        val root = Files.createTempDirectory("storage-core-test-").toFile()
        try {
            val pub = DurablePublication(root)
            val invalidJob = assertThrows(IllegalArgumentException::class.java) {
                pub.reserve("not-a-uuid", "item-1", "report.pdf", "application/pdf")
            }
            assertEquals("INVALID_OUTPUT_IDENTITY", invalidJob.message)

            val trash = ManagedTrashStorage(root)
            val id = UUID.randomUUID().toString()
            val entryDir = File(File(root, "trash"), id).apply { mkdirs() }
            File(entryDir, "entry.properties").writeText(
                "id=$id\nname=doc.pdf\nsize=corrupt\nsha256=${"0".repeat(64)}\nrecord={}\njournal=${"1".repeat(64)}\nstate=TRASH\n"
            )
            val corruptTrash = assertThrows(IllegalArgumentException::class.java) {
                trash.entries()
            }
            assertEquals("INVALID_TRASH_RECORD", corruptTrash.message)
        } finally {
            root.deleteRecursively()
        }
    }
}
