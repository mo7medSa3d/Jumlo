package com.jumlo.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.pdf.ReaderPositionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReaderPositionStoreTest {
    @Test fun lastPageSurvivesSeparateReadsAndIsIsolatedPerDocument() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val docA = "content://jumlo.test.documents/document/" + UUID.randomUUID()
        val docB = "content://jumlo.test.documents/document/" + UUID.randomUUID()
        assertNull(ReaderPositionStore.read(context, docA))
        assertTrue(ReaderPositionStore.save(context, docA, 18))
        assertEquals(18, ReaderPositionStore.read(context, docA))
        assertNull(ReaderPositionStore.read(context, docB))
        assertTrue(ReaderPositionStore.save(context, docA, 42))
        assertEquals(42, ReaderPositionStore.read(context, docA))
        assertFalse(ReaderPositionStore.save(context, docA, -1))
        assertEquals(42, ReaderPositionStore.read(context, docA))
    }

    @Test fun opaqueKeyNeverIncludesOriginalUriText() {
        val uri = "content://private.example/Sensitive%20Document.pdf"
        val key = ReaderPositionStore.opaqueKey(uri)
        assertTrue(key.startsWith("page_"))
        assertFalse(key.contains("Sensitive"))
        assertFalse(key.contains("private.example"))
        assertEquals(key, ReaderPositionStore.opaqueKey(uri))
        assertTrue(key != ReaderPositionStore.opaqueKey(uri + "?version=2"))
    }
}
