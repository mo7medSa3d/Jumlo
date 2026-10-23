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
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.facebook.com/watch?v=123"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://www.linkedin.com/posts/example"))
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor("https://lnkd.in/example"))
        assertFalse(DownloadLinkPolicy.requiresMediaExtractor("https://cdn.example.com/video.mp4"))
        assertFalse(DownloadLinkPolicy.requiresMediaExtractor("https://drive.google.com/file/d/1abcDE_fgHiJ23KLmno/view"))
    }
    @Test fun sharedTextWithTitleStillShowsQualityPicker() {
        val link = MediaLinks.normalize("Watch this: https://www.youtube.com/watch?v=abc123")
        assertEquals("https://www.youtube.com/watch?v=abc123", link)
        assertTrue(DownloadLinkPolicy.requiresMediaExtractor(link))
    }

    @Test fun DriveSharedResourceKeysSurviveDownloadRouting() {
        assertEquals(
            "https://drive.usercontent.google.com/download?id=1abcDE_fgHiJ23KLmno&export=download&confirm=t&resourcekey=0-fixtureKey",
            DownloadLinkPolicy.resolveDirectUrl("https://drive.google.com/file/d/1abcDE_fgHiJ23KLmno/view?usp=sharing&resourcekey=0-fixtureKey")
        )
    }

    @Test fun DocsExportLinksRetainAccessKeysWithoutCopyingUnrelatedQueryData() {
        assertEquals(
            "https://docs.google.com/document/d/1abcDE_fgHiJ23KLmno/export?format=pdf&resourcekey=0-fixtureKey",
            DownloadLinkPolicy.resolveDirectUrl("https://docs.google.com/document/d/1abcDE_fgHiJ23KLmno/edit?resourcekey=0-fixtureKey&utm_source=private")
        )
    }

    @Test fun resourceKeysRemainOpaqueAndCannotInjectAnotherParameter() {
        assertEquals(
            "https://drive.usercontent.google.com/download?id=1abcDE_fgHiJ23KLmno&export=download&confirm=t&resourcekey=opaque%26confirm%3Dsecret",
            DownloadLinkPolicy.resolveDirectUrl("https://drive.google.com/file/d/1abcDE_fgHiJ23KLmno/view?resourcekey=opaque%26confirm%3Dsecret")
        )
    }
}
