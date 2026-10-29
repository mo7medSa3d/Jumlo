package com.jumlo.app.core

import com.jumlo.app.core.media.MediaAsset
import com.jumlo.app.core.media.MediaCatalog
import com.jumlo.app.core.media.MediaFormat
import com.jumlo.app.core.media.MediaMode
import com.jumlo.app.model.AppSettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBestSelectionTest {
    private fun asset(vararg formats: MediaFormat) =
        MediaAsset("sample", "YouTube", "Fixture", 60.0, formats.toList())

    private fun video(id: String, height: Int, audio: Boolean = false) =
        MediaFormat(
            id = id, extension = "mp4", videoCodec = "avc1",
            audioCodec = if (audio) "mp4a" else "none",
            width = height * 16 / 9, height = height, bitrateKbps = height.toDouble()
        )

    private fun audio(id: String, rate: Double) =
        MediaFormat(id = id, extension = "m4a", audioCodec = "mp4a",
            audioBitrateKbps = rate, bitrateKbps = rate)

    @Test fun freshSettingsDefaultToAutomaticBestQuality() {
        assertEquals("BEST", AppSettingsState().defaultVideoQuality)
    }

    @Test fun automaticBestPrefersVideoWithAudioOverSilentHigherResolution() {
        val choices = MediaCatalog.choices(asset(video("silent2160", 2160),
            video("voiced1080", 1080, audio = true)), MediaMode.VIDEO)
        assertEquals("VIDEO:voiced1080", MediaCatalog.preferred(choices, MediaMode.VIDEO, 0)?.key)
    }

    @Test fun automaticBestCanMuxHighestVideoWithAvailableCompanionAudio() {
        val choices = MediaCatalog.choices(asset(video("video2160", 2160),
            video("video1080", 1080), audio("audio256", 256.0)), MediaMode.VIDEO)
        val selected = MediaCatalog.preferred(choices, MediaMode.VIDEO, 0)
        assertEquals("VIDEO:video2160+audio256", selected?.key)
        assertTrue(selected?.expectsAudio == true)
    }

    @Test fun explicitAdvancedResolutionCapIsRespected() {
        val choices = MediaCatalog.choices(asset(video("v2160", 2160, audio = true),
            video("v720", 720, audio = true)), MediaMode.VIDEO)
        assertEquals("VIDEO:v720", MediaCatalog.preferred(choices, MediaMode.VIDEO, 720)?.key)
    }

    @Test fun audioOnlyLinksHaveAnAutomaticHighestBitrateSelection() {
        val choices = MediaCatalog.choices(asset(audio("a128", 128.0),
            audio("a256", 256.0)), MediaMode.AUDIO)
        assertEquals("AUDIO:a256", MediaCatalog.preferred(choices, MediaMode.AUDIO, 0)?.key)
    }

    @Test fun unavailableSourceNeverFabricatesAQuality() {
        assertNull(MediaCatalog.preferred(emptyList(), MediaMode.VIDEO, 0))
        assertNull(MediaCatalog.preferred(emptyList(), MediaMode.AUDIO, 0))
    }
}
