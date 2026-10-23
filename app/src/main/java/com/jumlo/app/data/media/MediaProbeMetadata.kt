package com.jumlo.app.data.media

import com.jumlo.app.core.media.MediaAsset
import com.jumlo.app.core.media.MediaJsonGuard
import kotlinx.serialization.json.*

/** Combines a measured local ffprobe result with one extractor-owned format. */
internal object MediaProbeMetadata {
    fun enrich(asset:MediaAsset, probeJson:String, sourceBytes:Long?):MediaAsset {
        require(asset.formats.size==1) { "INVALID_MEDIA_PROBE" }
        MediaJsonGuard.check(probeJson)
        val json=Json.parseToJsonElement(probeJson) as? JsonObject ?: error("INVALID_MEDIA_PROBE")
        val streams=json["streams"] as? JsonArray ?: error("MEDIA_NO_USABLE_FORMAT")
        require(streams.size<=64) { "TOO_MANY_MEDIA_FORMATS" }
        val tracks=streams.mapNotNull { it as? JsonObject }
        val video=tracks.firstOrNull { it.string("codec_type")=="video" &&
            (it["disposition"] as? JsonObject)?.number("attached_pic")!=1.0 }
        val audio=tracks.firstOrNull { it.string("codec_type")=="audio" }
        require(video!=null || audio!=null) { "MEDIA_NO_USABLE_FORMAT" }
        fun codec(track:JsonObject?)=if(track==null) "none" else track.string("codec_name").ifBlank { "unknown" }.take(80)
        fun dimension(track:JsonObject?,key:String)=track?.get(key)?.let { it as? JsonPrimitive }
            ?.intOrNull?.takeIf { it in 1..65536 }
        val rate=video?.string("avg_frame_rate")?.split('/')
        val fps=if(rate?.size==2) {
            val numerator=rate[0].toDoubleOrNull()
            val denominator=rate[1].toDoubleOrNull()
            if(numerator!=null && denominator!=null && denominator>0) (numerator/denominator).takeIf { it.isFinite() && it>0 } else null
        } else null
        val container=json["format"] as? JsonObject
        val format=asset.formats.single().copy(
            videoCodec=codec(video),audioCodec=codec(audio),
            width=dimension(video,"width"),height=dimension(video,"height"),fps=fps,
            bitrateKbps=container?.number("bit_rate")?.div(1000),
            audioBitrateKbps=audio?.number("bit_rate")?.div(1000),
            bytes=sourceBytes?.takeIf { it>=0 },approximateBytes=null
        )
        return asset.copy(durationSeconds=container?.number("duration") ?: asset.durationSeconds,formats=listOf(format))
    }

    private fun JsonObject.string(key:String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.number(key:String)=(this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it>=0 }
}
