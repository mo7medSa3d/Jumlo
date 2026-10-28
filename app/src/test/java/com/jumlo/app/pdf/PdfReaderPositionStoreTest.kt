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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfReaderPositionStoreTest {
    private fun document() = "content://reader.test/private/" + UUID.randomUUID()

    @Test fun persistsLastPageAndKeepsPositionsIndependent() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val first=document()
        val second=document()
        assertNull(PdfReaderPositionStore.load(context,first))
        PdfReaderPositionStore.save(context,first,14)
        PdfReaderPositionStore.save(context,second,3)
        assertEquals(14,PdfReaderPositionStore.load(context,first))
        assertEquals(3,PdfReaderPositionStore.load(context,second))
        PdfReaderPositionStore.save(context,first,21)
        assertEquals(21,PdfReaderPositionStore.load(context,first))
        assertEquals(3,PdfReaderPositionStore.load(context,second))
    }

    @Test fun dataStoreKeysAreOpaqueHashesNeverRawUris() {
        val uri="content://reader.test/private/personal-name.pdf?secret=private-token"
        val digest=PdfReaderPositionStore.fingerprint(uri)
        assertEquals(64,digest.length)
        assertTrue(digest.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(digest.contains("personal-name"))
        assertFalse(digest.contains("private-token"))
        assertEquals(digest,PdfReaderPositionStore.fingerprint(uri))
    }

    @Test fun rejectsNegativePagesWithoutOverwritingSavedPosition() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val uri=document()
        PdfReaderPositionStore.save(context,uri,4)
        val result=runCatching { PdfReaderPositionStore.save(context,uri,-1) }
        assertTrue(result.isFailure)
        assertEquals(4,PdfReaderPositionStore.load(context,uri))
    }
}
