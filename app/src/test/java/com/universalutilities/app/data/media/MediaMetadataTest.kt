package com.universalutilities.app.data.media
import org.junit.Assert.*
import org.junit.Test

class MediaMetadataTest {
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
}
