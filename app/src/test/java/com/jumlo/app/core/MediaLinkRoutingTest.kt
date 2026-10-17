package com.jumlo.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLinkRoutingTest {
    @Test fun socialPagesRouteToQualityPicker() {
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.youtube.com/watch?v=abc123"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.tiktok.com/@author/video/123"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.instagram.com/reel/example"))
        assertFalse(DownloadLinkPolicy.requiresMediaExtractor("https://cdn.example.com/video.mp4"))
        assertFalse(DownloadLinkPolicy.requiresMediaExtractor("https://drive.google.com/file/d/1abcDE_fgHiJ23KLmno/view"))
    }
}
