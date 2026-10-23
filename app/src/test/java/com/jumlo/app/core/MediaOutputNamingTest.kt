package com.jumlo.app.core

import com.jumlo.app.core.media.MediaOutputPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaOutputNamingTest {
    @Test fun conversionReplacesOnlyKnownMediaExtensions() {
        assertEquals("My clip.mp3", MediaOutputPolicy.displayName("My clip.mp4", "mp3"))
        assertEquals("تسجيل.opus", MediaOutputPolicy.displayName("تسجيل.M4A", "opus"))
        assertEquals("Chapter 2.1.mp3", MediaOutputPolicy.displayName("Chapter 2.1", "mp3"))
    }

    @Test fun remoteTitlesStayInsideTheFilenameBudgetAndCannotChooseDirectories() {
        val name = MediaOutputPolicy.displayName("../مسار\\file:?" + "🎥".repeat(100), "mp4")
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 200)
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertTrue(name.endsWith(".mp4"))
        assertFalse(name.dropLast(4).last().isHighSurrogate())
    }
}
