package com.jumlo.app.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jumlo.app.ui.components.userMessageResource
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInExtractorTest {
    private fun runtime(): NativeDirectMediaRuntime {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return NativeDirectMediaRuntime(OkHttpClient(), MediaSessionStore(ctx))
    }

    @Test fun extractBalancedJsonArrayHandlesNestedStreamingLocations() {
        val raw = """"progressiveStreams":[{"width":720,"streamingLocations":[{"url":"https://dms.licdn.com/playlist/vid/720p.mp4"}],"bitRate":1200000},{"width":1080,"streamingLocations":[{"url":"https://dms.licdn.com/playlist/vid/1080p.mp4"}],"bitRate":3200000}],"trailing":[]"""
        val start = raw.indexOf('[')
        val extracted = NativeDirectMediaRuntime.extractBalancedJsonArray(raw, start)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("1080p.mp4"))
        assertTrue(extracted.endsWith("]"))
    }

    @Test fun regionalSubdomainsAndTrackingParamsNormalizeToCanonicalWww() {
        val normalized = NativeDirectMediaRuntime.normalizeLinkedInUrl(
            "https://fr.linkedin.com/posts/someone_demo-activity-12345/?utm_source=share&rcm=abc&trk=xyz",
            allowHttp = false
        )
        assertTrue(normalized.startsWith("https://www.linkedin.com/posts/someone_demo-activity-12345/"))
        assertTrue(!normalized.contains("utm_source"))
        assertTrue(!normalized.contains("rcm="))
    }

    @Test fun nestedProgressiveStreamsAndMetaFallbackExtractBestQualityMp4() {
        val html = """
            <html><head>
            <meta content="Product Demo" property="og:title"/>
            <script>
            {"progressiveStreams":[
              {"width":360,"bitRate":500000,"mediaType":"video/mp4","streamingLocations":[{"url":"https://dms.licdn.com/playlist/vid/v2/demo-360.mp4"}]},
              {"width":1080,"bitRate":3500000,"mediaType":"video/mp4","streamingLocations":[{"url":"https://dms.licdn.com/playlist/vid/v2/demo-1080.mp4"}]}
            ]}
            </script>
            </head></html>
        """.trimIndent()
        val scan = runtime().extractLinkedInCandidates(
            "https://www.linkedin.com/posts/user_demo-activity-1",
            html,
            allowHttp = false
        )
        assertTrue(scan.streams.isNotEmpty())
        assertEquals(
            "https://dms.licdn.com/playlist/vid/v2/demo-1080.mp4",
            scan.streams.first().url
        )
    }

    @Test fun reverseOrderedOgVideoMetaTagExtractsMp4() {
        val html = """
            <html><head>
            <meta content="Clip" property="og:title"/>
            <meta content="https://media.licdn.com/dms/video/C5605AQ/mp4-720p-30fp-crf28/0/clip.mp4" property="og:video:secure_url"/>
            </head></html>
        """.trimIndent()
        val scan = runtime().extractLinkedInCandidates(
            "https://www.linkedin.com/feed/update/urn:li:activity:999",
            html,
            allowHttp = false
        )
        assertTrue(scan.streams.any { it.url.contains("mp4-720p-30fp-crf28") && it.extension == "mp4" })
    }

    @Test fun linkedInErrorsResolveToLocalizedMessagesNeverLiteralNull() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        listOf(
            "LINKEDIN_AUTH_REQUIRED",
            "LINKEDIN_HLS_UNSUPPORTED",
            "LINKEDIN_NO_PUBLIC_MEDIA",
            "MEDIA_EXTRACTION_FAILED"
        ).forEach { code ->
            val resolved = ctx.getString(userMessageResource(code))
            assertTrue("$code must not resolve to empty or literal null", resolved.isNotBlank() && resolved != "null")
        }
    }
}
