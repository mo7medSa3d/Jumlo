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

    @Test fun newlyAddedMediaPlatformsRouteToExtractorAndReportNames() {
        val cases = listOf(
            "https://vk.com/video-1_2" to "VK",
            "https://vkvideo.ru/video-1_2" to "VK",
            "https://ok.ru/video/123" to "OK.ru",
            "https://rumble.com/v123-clip.html" to "Rumble",
            "https://odysee.com/@ch/clip" to "Odysee",
            "https://kick.com/channel/clips/1" to "Kick",
            "https://streamable.com/abc" to "Streamable",
            "https://www.loom.com/share/abc" to "Loom",
            "https://artist.bandcamp.com/track/song" to "Bandcamp",
            "https://www.mixcloud.com/user/mix/" to "Mixcloud",
            "https://www.ted.com/talks/example" to "TED",
            "https://archive.org/details/example" to "Internet Archive"
        )
        for ((url, expectedName) in cases) {
            assertTrue(url, DownloadLinkPolicy.requiresMediaExtractor(url))
            assertEquals(url, expectedName, DownloadLinkPolicy.detectPlatformName(url))
        }
    }

    @Test fun resourceKeysRemainOpaqueAndCannotInjectAnotherParameter() {
        assertEquals(
            "https://drive.usercontent.google.com/download?id=1abcDE_fgHiJ23KLmno&export=download&confirm=t&resourcekey=opaque%26confirm%3Dsecret",
            DownloadLinkPolicy.resolveDirectUrl("https://drive.google.com/file/d/1abcDE_fgHiJ23KLmno/view?resourcekey=opaque%26confirm%3Dsecret")
        )
    }
}
