package com.jumlo.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPoliciesTest {

    @Test
    fun downloadSelectorAppendsResolutionAndModeFallbacks() {
        val video = MediaFormat(
            id = "137",
            extension = "mp4",
            videoCodec = "avc1.640028",
            width = 1920,
            height = 1080,
            fps = 30.0
        )
        val audio = MediaFormat(
            id = "140",
            extension = "m4a",
            audioCodec = "mp4a.40.2",
            audioBitrateKbps = 128.0
        )
        val muxedChoice = MediaChoice(
            key = "VIDEO:137+140",
            mode = MediaMode.VIDEO,
            formats = listOf(video, audio),
            container = "mp4",
            requiresMux = true,
            expectedHeight = 1080,
            estimatedBytes = null,
            sizeIsApproximate = false
        )
        assertEquals(
            "best*[format_id='137']+best*[format_id='140']/bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
            MediaCatalog.downloadSelector(muxedChoice)
        )

        val portrait = MediaFormat(
            id = "v720",
            extension = "mp4",
            videoCodec = "avc1.64001f",
            audioCodec = "mp4a.40.2",
            width = 720,
            height = 1280
        )
        val portraitChoice = MediaChoice(
            key = "VIDEO:v720",
            mode = MediaMode.VIDEO,
            formats = listOf(portrait),
            container = "mp4",
            requiresMux = false,
            expectedHeight = 1280,
            estimatedBytes = null,
            sizeIsApproximate = false
        )
        assertEquals(
            "best*[format_id='v720']/bestvideo[height<=720]+bestaudio/best[height<=720]/best",
            MediaCatalog.downloadSelector(portraitChoice)
        )

        val unknownDim = MediaFormat(
            id = "direct_mp4",
            extension = "mp4",
            videoCodec = "h264",
            audioCodec = "aac"
        )
        val unknownDimChoice = MediaChoice(
            key = "VIDEO:direct_mp4",
            mode = MediaMode.VIDEO,
            formats = listOf(unknownDim),
            container = "mp4",
            requiresMux = false,
            expectedHeight = null,
            estimatedBytes = null,
            sizeIsApproximate = false
        )
        assertEquals(
            "best*[format_id='direct_mp4']/bestvideo+bestaudio/best",
            MediaCatalog.downloadSelector(unknownDimChoice)
        )

        val audioChoice = MediaChoice(
            key = "AUDIO:140",
            mode = MediaMode.AUDIO,
            formats = listOf(audio),
            container = "m4a",
            requiresMux = false,
            expectedHeight = null,
            estimatedBytes = null,
            sizeIsApproximate = false
        )
        assertEquals(
            "best*[format_id='140']/bestaudio/best",
            MediaCatalog.downloadSelector(audioChoice)
        )

        val unsafeChoice = audioChoice.copy(formats = listOf(audio.copy(id = "bad'id")))
        assertThrows(IllegalArgumentException::class.java) {
            MediaCatalog.downloadSelector(unsafeChoice)
        }
    }

    @Test
    fun choicesDeduplicatesCdnMirrorsAndRedundantCodecVariantsPerTier() {
        val asset = MediaAsset(
            id = "mirror-test",
            extractor = "Generic",
            title = "CDN Mirrors",
            durationSeconds = 120.0,
            formats = listOf(
                MediaFormat(
                    id = "hls-1080-0",
                    extension = "mp4",
                    videoCodec = "avc1.640028",
                    audioCodec = "mp4a.40.2",
                    width = 1920,
                    height = 1080,
                    fps = 30.0,
                    bitrateKbps = 3000.0
                ),
                MediaFormat(
                    id = "hls-1080-1",
                    extension = "mp4",
                    videoCodec = "avc1.640028",
                    audioCodec = "mp4a.40.2",
                    width = 1920,
                    height = 1080,
                    fps = 30.0,
                    bitrateKbps = 4200.0
                ),
                MediaFormat(
                    id = "hls-1080-2",
                    extension = "mp4",
                    videoCodec = "av01.0.08M.08",
                    audioCodec = "mp4a.40.2",
                    width = 1920,
                    height = 1080,
                    fps = 30.0,
                    bitrateKbps = 5000.0
                ),
                MediaFormat(
                    id = "hls-1080-60",
                    extension = "mp4",
                    videoCodec = "avc1.64002a",
                    audioCodec = "mp4a.40.2",
                    width = 1920,
                    height = 1080,
                    fps = 60.0,
                    bitrateKbps = 4800.0
                ),
                MediaFormat(
                    id = "hls-720-0",
                    extension = "mp4",
                    videoCodec = "avc1.64001f",
                    audioCodec = "mp4a.40.2",
                    width = 1280,
                    height = 720,
                    fps = 30.0,
                    bitrateKbps = 2000.0
                )
            )
        )

        val videoChoices = MediaCatalog.choices(asset, MediaMode.VIDEO)
        assertEquals(listOf("hls-1080-60", "hls-1080-1", "hls-720-0"), videoChoices.map { it.formatIds.single() })
    }

    @Test
    fun choicesDeduplicatesAudioMirrorsWithinBitrateBucket() {
        val asset = MediaAsset(
            id = "audio-mirrors",
            extractor = "Generic",
            title = "Audio Mirrors",
            durationSeconds = 180.0,
            formats = listOf(
                MediaFormat(id = "a-128-0", extension = "m4a", audioCodec = "mp4a.40.2", audioBitrateKbps = 128.0),
                MediaFormat(id = "a-128-1", extension = "m4a", audioCodec = "mp4a.40.2", audioBitrateKbps = 129.0),
                MediaFormat(id = "a-256-0", extension = "m4a", audioCodec = "mp4a.40.2", audioBitrateKbps = 256.0),
                MediaFormat(id = "a-128-opus", extension = "opus", audioCodec = "opus", audioBitrateKbps = 128.0)
            )
        )

        val audioChoices = MediaCatalog.choices(asset, MediaMode.AUDIO)
        assertEquals(listOf("a-256-0", "a-128-1"), audioChoices.map { it.formatIds.single() })
    }

    @Test
    fun mediaArgumentsRestrictsExtractorArgsToYouTubeHostsOnly() {
        val sampleChoice = MediaChoice(
            key = "VIDEO:137",
            mode = MediaMode.VIDEO,
            formats = listOf(
                MediaFormat(
                    id = "137",
                    extension = "mp4",
                    videoCodec = "avc1.640028",
                    audioCodec = "mp4a.40.2",
                    width = 1920,
                    height = 1080
                )
            ),
            container = "mp4",
            requiresMux = false,
            expectedHeight = 1080,
            estimatedBytes = null,
            sizeIsApproximate = false
        )

        val youtubeUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://m.youtube.com/shorts/abcdef12345",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"
        )
        for (url in youtubeUrls) {
            val probeArgs = MediaArguments.probe(url)
            val probeIdx = probeArgs.indexOf("--extractor-args")
            assertTrue("Expected --extractor-args in probe for $url", probeIdx >= 0)
            assertEquals("youtube:player_client=android_vr,web", probeArgs[probeIdx + 1])
            assertTrue(probeArgs.contains("--compat-options"))

            val dlArgs = MediaArguments.download(url, sampleChoice)
            val dlIdx = dlArgs.indexOf("--extractor-args")
            assertTrue("Expected --extractor-args in download for $url", dlIdx >= 0)
            assertEquals("youtube:player_client=android_vr,web", dlArgs[dlIdx + 1])
            val fmtIdx = dlArgs.indexOf("--format")
            assertEquals(MediaCatalog.downloadSelector(sampleChoice), dlArgs[fmtIdx + 1])
        }

        val nonYoutubeUrls = listOf(
            "https://www.tiktok.com/@user/video/1234567890",
            "https://www.instagram.com/reel/C123456/",
            "https://vimeo.com/123456789",
            "https://notyoutube.com/watch?v=dQw4w9WgXcQ"
        )
        for (url in nonYoutubeUrls) {
            val probeArgs = MediaArguments.probe(url)
            assertFalse("Unexpected --extractor-args in probe for $url", probeArgs.contains("--extractor-args"))
            assertFalse("Unexpected --compat-options in probe for $url", probeArgs.contains("--compat-options"))

            val dlArgs = MediaArguments.download(url, sampleChoice)
            assertFalse("Unexpected --extractor-args in download for $url", dlArgs.contains("--extractor-args"))
            val fmtIdx = dlArgs.indexOf("--format")
            assertEquals(MediaCatalog.downloadSelector(sampleChoice), dlArgs[fmtIdx + 1])
        }
    }

    @Test
    fun choicesCollapseMixedContainerAndFpsJitterDuplicatesAcrossYouTubeAndTikTok() {
        val audioM4a = MediaFormat(id = "140", extension = "m4a", audioCodec = "mp4a.40.2", audioBitrateKbps = 128.0)
        val audioOpus = MediaFormat(id = "251", extension = "webm", audioCodec = "opus", audioBitrateKbps = 160.0)
        val asset = MediaAsset(
            id = "yt-dedup-test",
            extractor = "Youtube",
            title = "Mixed Container Test",
            durationSeconds = 240.0,
            formats = listOf(
                MediaFormat(id = "313", extension = "webm", videoCodec = "vp9", width = 3840, height = 2160, fps = 30.0, bitrateKbps = 18000.0),
                MediaFormat(id = "401", extension = "mp4", videoCodec = "av01.0.12M.08", width = 3840, height = 2160, fps = 30.0, bitrateKbps = 14000.0),
                MediaFormat(id = "248", extension = "webm", videoCodec = "vp9", width = 1920, height = 1080, fps = 30.0, bitrateKbps = 2500.0),
                MediaFormat(id = "137", extension = "mp4", videoCodec = "avc1.640028", width = 1920, height = 1080, fps = 29.97, bitrateKbps = 2400.0),
                MediaFormat(id = "299", extension = "mp4", videoCodec = "avc1.64002a", width = 1920, height = 1080, fps = 60.0, bitrateKbps = 4500.0),
                MediaFormat(id = "303", extension = "webm", videoCodec = "vp9", width = 1920, height = 1080, fps = 59.94, bitrateKbps = 4800.0),
                MediaFormat(id = "22", extension = "mp4", videoCodec = "avc1.64001f", audioCodec = "mp4a.40.2", width = 1280, height = 720, fps = 30.0, bitrateKbps = 1500.0),
                MediaFormat(id = "247", extension = "webm", videoCodec = "vp9", width = 1280, height = 720, fps = 30.0, bitrateKbps = 1600.0),
                audioM4a,
                audioOpus
            )
        )
        val choices = MediaCatalog.choices(asset, MediaMode.VIDEO)
        assertEquals(listOf("2160p 4K", "1080p Full HD · 60fps", "1080p Full HD", "720p HD"), choices.map(MediaCatalog::qualityTitle))
        assertEquals(listOf("401", "299", "137", "22"), choices.map { it.formatIds.first() })
        assertTrue(choices.all { it.container == "mp4" && it.expectsAudio })
    }

    @Test
    fun jsonGuardRejectsLeadingClosingBracesBypassingDepthLimit() {
        val malicious = "}".repeat(100) + "{".repeat(65) + "}".repeat(65)
        val failure = assertThrows(IllegalArgumentException::class.java) {
            MediaJsonGuard.check(malicious)
        }
        assertEquals("MEDIA_METADATA_DEPTH", failure.message)
    }

    @Test
    fun displayBytesReturnsNullWhenEstimateOverflowsLong() {
        val hugeFormat = MediaFormat(id = "f1", extension = "mp4", videoCodec = "avc1", bitrateKbps = 1e300)
        val choice = MediaChoice(
            key = "VIDEO:f1",
            mode = MediaMode.VIDEO,
            formats = listOf(hugeFormat),
            container = "mp4",
            requiresMux = false,
            expectedHeight = 1080,
            estimatedBytes = null,
            sizeIsApproximate = false
        )
        assertNull(MediaCatalog.displayBytes(choice, durationSeconds = 1e20))
    }
}
