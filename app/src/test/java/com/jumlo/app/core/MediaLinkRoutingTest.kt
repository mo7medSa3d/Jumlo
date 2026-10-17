package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jumlo.app.core.media.MediaLinks

class MediaLinkRoutingTest {
    @Test fun socialPagesRouteToQualityPicker() {
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.youtube.com/watch?v=abc123"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.tiktok.com/@author/video/123"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.instagram.com/reel/example"))
        assertFalse(DownloadLinkPolicy.requiresMediaExtractor("https://cdn.example.com/video.mp4"))
        assertFalse(DownloadLinkPolicy.requiresMediaExtractor("https://drive.google.com/file/d/1abcDE_fgHiJ23KLmno/view"))
    }
    @Test fun sharedTextWithTitleStillShowsQualityPicker() {
        val link = MediaLinks.normalize("Watch this: https://www.youtube.com/watch?v=abc123")
        assertEquals("https://www.youtube.com/watch?v=abc123", link)
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor(link))
    }
}
