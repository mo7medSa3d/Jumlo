package com.jumlo.app.core

import com.jumlo.app.core.media.MediaAsset
import com.jumlo.app.core.media.MediaCatalog
import com.jumlo.app.core.media.MediaFormat
import com.jumlo.app.core.media.MediaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** A single-click download must select a usable format without opening Advanced. */
class MediaAutoSelectionTest {
    private fun media(): MediaAsset = MediaAsset(
        id = "video-1", extractor = "YouTube", title = "Example", durationSeconds = 30.0,
        formats = listOf(
            MediaFormat("v480", "mp4", "h264", "aac", width = 854, height = 480, bitrateKbps = 900.0),
            MediaFormat("v1080", "mp4", "h264", "aac", width = 1920, height = 1080, bitrateKbps = 3400.0),
            MediaFormat("v2160", "mp4", "h264", "aac", width = 3840, height = 2160, bitrateKbps = 9000.0),
            MediaFormat("a128", "m4a", audioCodec = "aac", audioBitrateKbps = 128.0),
            MediaFormat("a320", "m4a", audioCodec = "aac", audioBitrateKbps = 320.0)
        )
    )

    @Test fun videoDefaultsToBestAvailableInsteadOfSilentlyCappingAt1080() {
        val choices = MediaCatalog.choices(media(), MediaMode.VIDEO)
        val preferred = MediaCatalog.preferred(choices, MediaMode.VIDEO)
        assertNotNull(preferred)
        assertEquals("v2160", preferred!!.formatIds.first())
        assertEquals(true, preferred.expectsAudio)
    }

    @Test fun explicitlyChosenAdvancedCapStillSelects1080p() {
        val choices = MediaCatalog.choices(media(), MediaMode.VIDEO)
        val preferred = MediaCatalog.preferred(choices, MediaMode.VIDEO, 1080)
        assertNotNull(preferred)
        assertEquals("v1080", preferred!!.formatIds.first())
    }

    @Test fun audioOnlyModeDefaultsToHighestAvailableAudioBitrate() {
        val choices = MediaCatalog.choices(media(), MediaMode.AUDIO)
        val preferred = MediaCatalog.preferred(choices, MediaMode.AUDIO)
        assertNotNull(preferred)
        assertEquals("a320", preferred!!.formatIds.first())
    }

    @Test fun audioOnlyAssetCanBeSelectedWithoutVideo() {
        val asset = MediaAsset("audio-1", "SoundCloud", "Audio", 20.0,
            listOf(MediaFormat("a192", "m4a", audioCodec = "aac", audioBitrateKbps = 192.0)))
        assertEquals(0, MediaCatalog.choices(asset, MediaMode.VIDEO).size)
        assertEquals("a192", MediaCatalog.preferred(MediaCatalog.choices(asset, MediaMode.AUDIO), MediaMode.AUDIO)?.formatIds?.first())
    }
}
