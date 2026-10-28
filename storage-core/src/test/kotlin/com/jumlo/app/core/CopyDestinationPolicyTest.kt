package com.jumlo.app.core

import com.jumlo.app.model.CollisionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CopyDestinationPolicyTest {
    private val existing = listOf("source-b" to "Archive.zip")

    @Test fun keepBothGeneratesSafeDeterministicName() {
        assertEquals("Archive (1).zip", CopyDestinationPolicy.name(
            "source-a", "Archive.zip", existing, move = false, CollisionPolicy.KEEP_BOTH))
    }

    @Test fun skipNeverSilentlyOverwritesMatchingDestination() {
        assertNull(CopyDestinationPolicy.name(
            "source-a", "archive.zip", existing, move = false, CollisionPolicy.SKIP))
    }

    @Test fun moveToSameDocumentAndUnsafeAtomicReplaceFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            CopyDestinationPolicy.name("source-b", "Archive.zip", existing,
                move = true, CollisionPolicy.KEEP_BOTH)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CopyDestinationPolicy.name("source-a", "Archive.zip", existing,
                move = false, CollisionPolicy.REPLACE)
        }
    }
}
