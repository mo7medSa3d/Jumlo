package com.jumlo.app.data.media
import org.junit.Assert.*
import org.junit.Test
import com.jumlo.app.core.media.MediaCatalog
import com.jumlo.app.core.media.MediaMode

class MediaMetadataTest {
    @Test fun unusableMetadataEntriesDoNotHideAValidSource() {
        val asset=MediaMetadata.parse("""{"id":"clip","extractor_key":"Fixture","formats":[null,{},42,{"format_id":"18","url":"https://cdn.example/clip.mp4","ext":"mp4","vcodec":"h264","acodec":"aac"}]}""")
        assertEquals(listOf("18"),MediaCatalog.choices(asset,MediaMode.VIDEO).single().formatIds)
    }
    @Test fun signedEndpointsAreDiscardedFromModel() {
        val asset=MediaMetadata.parse("""{"id":"a","extractor":"generic","title":"file","formats":[{"format_id":"720","url":"https://media.example/video?secret=do-not-store","vcodec":"avc1","acodec":"aac","ext":"mp4","height":720}]}""")
        assertEquals(720,asset.formats.single().height)
        assertFalse(asset.toString().contains("do-not-store"))
    }
    @Test fun unknownCodecsDoNotInventPlayableTracks() {
        val asset=MediaMetadata.parse("""{"id":"a","extractor":"generic","format_id":"source","url":"https://media.example/file","ext":"mp4"}""")
        assertFalse(asset.formats.single().hasAudio)
        assertFalse(asset.formats.single().hasVideo)
    }
    @Test(expected=IllegalArgumentException::class) fun playlistsNeedExplicitItemSelection() {
        MediaMetadata.parse("""{"_type":"playlist","entries":[]}""")
    }
    @Test(expected=IllegalArgumentException::class) fun liveOutputIsNotPresentedAsFiniteFile() {
        MediaMetadata.parse("""{"id":"a","is_live":true}""")
    }

    @Test fun instagramProgressiveAudioIsAvailableWithoutInventingItsCodec() {
        // Instagram's extractor leaves acodec unset when has_audio is true. Only
        // has_audio=false is represented by the explicit acodec="none" marker.
        val asset=MediaMetadata.parse("""{"id":"reel","extractor_key":"Instagram","formats":[{"format_id":"0","url":"https://cdn.example/reel.mp4","ext":"mp4","width":1080,"height":1920,"vcodec":"h264"}]}""")
        val video=MediaCatalog.choices(asset,MediaMode.VIDEO).single()
        assertTrue(video.expectsAudio)
        assertEquals("unknown",video.formats.single().audioCodec)
        assertEquals(listOf("0"),MediaCatalog.choices(asset,MediaMode.AUDIO).single().formatIds)
    }

    @Test fun explicitSilentVideoDoesNotAcquireAnImaginaryAudioTrack() {
        val asset=MediaMetadata.parse("""{"id":"reel","extractor_key":"Instagram","formats":[{"format_id":"0","url":"https://cdn.example/reel.mp4","ext":"mp4","width":1080,"height":1920,"vcodec":"h264","acodec":"none"}]}""")
        assertTrue(MediaCatalog.choices(asset,MediaMode.AUDIO).isEmpty())
        assertFalse(MediaCatalog.choices(asset,MediaMode.VIDEO).single().expectsAudio)
    }

    @Test fun unreportedCodecsRemainUnknownInsteadOfFabricatedH264AndAac() {
        val asset=MediaMetadata.parse("""{"id":"clip","extractor_key":"Instagram","formats":[{"format_id":"0","url":"https://cdn.example/clip.webm","ext":"webm","width":1280,"height":720}]}""")
        assertEquals("unknown",asset.formats.single().videoCodec)
        assertEquals("unknown",asset.formats.single().audioCodec)
    }

    @Test fun adaptiveVideoWithoutAudioMetadataDoesNotGuessAMuxedAudioTrack() {
        val asset=MediaMetadata.parse("""{"id":"clip","extractor_key":"Instagram","formats":[{"format_id":"dash-0","url":"https://cdn.example/segment","ext":"mp4","width":1280,"height":720,"protocol":"http_dash_segments"}]}""")
        assertFalse(asset.formats.single().hasAudio)
    }

    @Test fun anExplicitUnknownCodecStillDescribesAnExistingTrack() {
        val asset=MediaMetadata.parse("""{"id":"clip","extractor_key":"Generic","formats":[{"format_id":"0","url":"https://cdn.example/clip.mp4","ext":"mp4","vcodec":"unknown","acodec":"unknown"}]}""")
        assertTrue(asset.formats.single().hasVideo)
        assertTrue(asset.formats.single().hasAudio)
    }
}
