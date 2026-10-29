package com.jumlo.app.data.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** PR #42 JVM regression coverage adapted to the canonical debounced PR #30 store. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderPositionStoreJvmTest {
    private fun uri() = "content://reader.test/secret/" + UUID.randomUUID()

    @Test fun positionsPersistAndRemainIsolatedPerDocument() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = uri()
        val second = uri()
        assertNull(ReaderPositionStore.read(context, first))
        assertTrue(ReaderPositionStore.save(context, first, 14))
        assertTrue(ReaderPositionStore.save(context, second, 3))
        assertEquals(14, ReaderPositionStore.read(context, first))
        assertEquals(3, ReaderPositionStore.read(context, second))
        assertTrue(ReaderPositionStore.save(context, first, 21))
        assertEquals(21, ReaderPositionStore.read(context, first))
        assertEquals(3, ReaderPositionStore.read(context, second))
    }

    @Test fun preferenceKeysNeverContainRawDocumentUris() {
        val raw = "content://reader.test/private/personal-name.pdf?secret=private-token"
        val key = ReaderPositionStore.opaqueKey(raw)
        assertTrue(key.startsWith("page_"))
        val hash = key.removePrefix("page_")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(key.contains("personal-name"))
        assertFalse(key.contains("private-token"))
        assertEquals(key, ReaderPositionStore.opaqueKey(raw))
    }

    @Test fun negativePagesCannotOverwriteSavedPage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = uri()
        assertTrue(ReaderPositionStore.save(context, target, 4))
        assertFalse(ReaderPositionStore.save(context, target, -1))
        assertEquals(4, ReaderPositionStore.read(context, target))
    }
}
