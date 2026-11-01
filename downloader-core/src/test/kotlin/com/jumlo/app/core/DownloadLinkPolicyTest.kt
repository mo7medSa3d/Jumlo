package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadLinkPolicyTest {

    @Test
    fun detectPlatformNameIdentifiesPopularPlatformsCorrectly() {
        assertEquals("TikTok", DownloadLinkPolicy.detectPlatformName("https://www.tiktok.com/@user/video/1234567890"))
        assertEquals("TikTok", DownloadLinkPolicy.detectPlatformName("https://vm.tiktok.com/ZMxxxxxx/"))
        assertEquals("YouTube", DownloadLinkPolicy.detectPlatformName("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("YouTube", DownloadLinkPolicy.detectPlatformName("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("YouTube", DownloadLinkPolicy.detectPlatformName("https://youtube.com/shorts/abcdef12345"))
        assertEquals("Instagram", DownloadLinkPolicy.detectPlatformName("https://www.instagram.com/reel/Cxxxxxx/"))
        assertEquals("Instagram", DownloadLinkPolicy.detectPlatformName("https://instagr.am/p/Cxxxxxx/"))
        assertEquals("Facebook", DownloadLinkPolicy.detectPlatformName("https://www.facebook.com/watch/?v=123456"))
        assertEquals("Facebook", DownloadLinkPolicy.detectPlatformName("https://fb.watch/xxxxxx/"))
        assertEquals("LinkedIn", DownloadLinkPolicy.detectPlatformName("https://www.linkedin.com/posts/activity-123456"))
        assertEquals("X (Twitter)", DownloadLinkPolicy.detectPlatformName("https://twitter.com/user/status/1234567890"))
        assertEquals("X (Twitter)", DownloadLinkPolicy.detectPlatformName("https://x.com/user/status/1234567890"))
        assertEquals("Pinterest", DownloadLinkPolicy.detectPlatformName("https://pin.it/xxxxxx"))
        assertEquals("Google Drive", DownloadLinkPolicy.detectPlatformName("https://drive.google.com/file/d/123456789/view"))
    }

    @Test
    fun requiresMediaExtractorForSocialMediaPlatforms() {
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.tiktok.com/@user/video/1234567890"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.instagram.com/reel/Cxxxxxx/"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://fb.watch/xxxxxx/"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.linkedin.com/posts/activity-123456"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://x.com/user/status/1234567890"))
        
        // Google Drive is a file download, not a media extractor requirement
        assertFalse(DownloadLinkPolicy.requiresMediaExtractor("https://drive.google.com/file/d/123456789/view"))
    }

    @Test
    fun classifiesDirectFileTypesProperly() {
        assertEquals(DirectFileKind.PDF, DownloadLinkPolicy.classifyDirectFile("doc.pdf", "application/pdf"))
        assertEquals(DirectFileKind.VIDEO, DownloadLinkPolicy.classifyDirectFile("clip.mp4", "video/mp4"))
        assertEquals(DirectFileKind.AUDIO, DownloadLinkPolicy.classifyDirectFile("song.mp3", "audio/mpeg"))
        assertEquals(DirectFileKind.IMAGE, DownloadLinkPolicy.classifyDirectFile("photo.png", "image/png"))
        assertEquals(DirectFileKind.ARCHIVE, DownloadLinkPolicy.classifyDirectFile("bundle.zip", "application/zip"))
    }

    @Test
    fun identifiesDirectUrlsAccurately() {
        assertTrue(DownloadLinkPolicy.isDirect("https://example.com/downloads/file.pdf"))
        assertTrue(DownloadLinkPolicy.isDirect("https://example.com/media/clip.mp4"))
        assertFalse(DownloadLinkPolicy.isDirect("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(DownloadLinkPolicy.isDirect("https://vm.tiktok.com/ZMxxxxxx/"))
    }
}
