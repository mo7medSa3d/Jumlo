package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCompressionPolicyTest {
    @Test fun pngEncodedToJpegGetsJpegExtensionAndMimeType() {
        val value=DownloadCompressionPolicy.choose("الصورة.png","image/png",10000,3000)
        assertTrue(value.useCompressed)
        assertEquals("الصورة.jpg",value.filename)
        assertEquals("image/jpeg",value.mimeType)
    }

    @Test fun originalSurvivesWhenCompressionIsLarger() {
        val value=DownloadCompressionPolicy.choose("my photo.webp","image/webp",1000,1200)
        assertFalse(value.useCompressed)
        assertEquals("my photo.webp",value.filename)
        assertEquals("image/webp",value.mimeType)
    }

    @Test fun emptyCandidateNeverReplacesOriginal() {
        assertFalse(DownloadCompressionPolicy.choose("small.jpeg","image/jpeg",300,0).useCompressed)
    }
}
