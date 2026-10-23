package com.jumlo.app.data.media

import com.jumlo.app.core.media.*
import org.junit.Assert.*
import org.junit.Test

class MediaProbeMetadataTest {
    private val asset=MediaAsset("clip","Generic","clip.mp4",null,listOf(MediaFormat("mp4","mp4")))

    @Test fun aSilentVideoGetsItsMeasuredResolutionAndNeverAnAudioChoice() {
        val result=MediaProbeMetadata.enrich(asset,"""{"streams":[{"codec_type":"video","codec_name":"h264","width":1920,"height":1080,"avg_frame_rate":"30000/1001","bit_rate":"2000000"}],"format":{"duration":"12.5","bit_rate":"2100000"}}""",4000000)
        val video=MediaCatalog.choices(result,MediaMode.VIDEO).single()
        assertEquals(1080,video.expectedHeight)
        assertEquals(4000000L,video.estimatedBytes)
        assertEquals(12.5,result.durationSeconds!!,0.0)
        assertEquals(29.970,video.formats.single().fps!!,0.001)
        assertTrue(MediaCatalog.choices(result,MediaMode.AUDIO).isEmpty())
    }

    @Test fun muxedAudioUsesItsOwnMeasuredBitrate() {
        val result=MediaProbeMetadata.enrich(asset,"""{"streams":[{"codec_type":"video","codec_name":"h264","width":1280,"height":720},{"codec_type":"audio","codec_name":"aac","bit_rate":"128000"}],"format":{"bit_rate":"2500000"}}""",null)
        assertTrue(MediaCatalog.choices(result,MediaMode.VIDEO).single().expectsAudio)
        val audio=MediaCatalog.choices(result,MediaMode.AUDIO).single()
        assertEquals(128.0,audio.formats.single().audioBitrateKbps!!,0.0)
        assertEquals("128 kbps · MP4",MediaCatalog.qualityTitle(audio))
    }

    @Test fun albumArtworkIsNotAdvertisedAsVideo() {
        val audioAsset=asset.copy(formats=listOf(MediaFormat("mp3","mp3")))
        val result=MediaProbeMetadata.enrich(audioAsset,"""{"streams":[{"codec_type":"video","codec_name":"mjpeg","width":500,"height":500,"disposition":{"attached_pic":1}},{"codec_type":"audio","codec_name":"mp3","bit_rate":"192000"}]}""",null)
        assertTrue(MediaCatalog.choices(result,MediaMode.VIDEO).isEmpty())
        assertEquals("192 kbps · MP3",MediaCatalog.qualityTitle(MediaCatalog.choices(result,MediaMode.AUDIO).single()))
    }

    @Test fun malformedNumericMetadataStaysUnknown() {
        val result=MediaProbeMetadata.enrich(asset,"""{"streams":[{"codec_type":"video","codec_name":"h264","width":-1,"height":0,"avg_frame_rate":"1/0","bit_rate":"NaN"}],"format":{"duration":"Infinity","bit_rate":"-100"}}""",null)
        val format=result.formats.single()
        assertNull(format.width);assertNull(format.height);assertNull(format.fps)
        assertNull(result.durationSeconds);assertNull(format.bitrateKbps)
        assertTrue(format.hasVideo)
    }

    @Test(expected=IllegalArgumentException::class) fun aProbeWithoutMediaDoesNotCreateChoices() {
        MediaProbeMetadata.enrich(asset,"""{"streams":[]}""",null)
    }
}
