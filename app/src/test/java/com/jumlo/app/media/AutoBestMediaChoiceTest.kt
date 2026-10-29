package com.jumlo.app.media

import com.jumlo.app.core.media.MediaAsset
import com.jumlo.app.core.media.MediaCatalog
import com.jumlo.app.core.media.MediaFormat
import com.jumlo.app.core.media.MediaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The simple downloader path must select real formats, without guessing qualities. */
class AutoBestMediaChoiceTest {
    @Test fun bestAutoSelectionUsesHighestAvailableAudibleVideo() {
        val asset = MediaAsset(
            "fixture", "TestExtractor", "Sample", 25.0,
            listOf(
                MediaFormat("360", "mp4", "h264", "aac", height = 360, bitrateKbps = 900.0),
                MediaFormat("1080", "mp4", "h264", "aac", height = 1080, bitrateKbps = 2600.0),
                MediaFormat("2160", "mp4", "h264", "aac", height = 2160, bitrateKbps = 8500.0)
            )
        )
        val choices = MediaCatalog.choices(asset, MediaMode.VIDEO)
        val auto = requireNotNull(MediaCatalog.preferred(choices, MediaMode.VIDEO, maxHeight = 0))
        assertEquals(2160, auto.expectedHeight)
        assertTrue(auto.expectsAudio)
        assertEquals("2160", auto.formats.first().id)

        // A saved advanced preference is still meaningful and must not be erased.
        val limited = requireNotNull(MediaCatalog.preferred(choices, MediaMode.VIDEO, maxHeight = 1080))
        assertEquals(1080, limited.expectedHeight)
    }

    @Test fun audioOnlySourcesSelectHighestQualityAudioWithoutVideo() {
        val asset = MediaAsset(
            "audio-fixture", "TestExtractor", "Audio", 20.0,
            listOf(
                MediaFormat("low", "m4a", "none", "aac", audioBitrateKbps = 64.0),
                MediaFormat("high", "m4a", "none", "aac", audioBitrateKbps = 192.0)
            )
        )
        assertTrue(MediaCatalog.choices(asset, MediaMode.VIDEO).isEmpty())
        val audioChoices = MediaCatalog.choices(asset, MediaMode.AUDIO)
        val auto = requireNotNull(MediaCatalog.preferred(audioChoices, MediaMode.AUDIO, maxHeight = 0))
        assertTrue(auto.expectsAudio)
        assertEquals("high", auto.formats.single().id)
    }
}
