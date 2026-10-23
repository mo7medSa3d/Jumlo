package com.jumlo.app.data.media

import com.jumlo.app.core.media.*
import kotlinx.serialization.json.*

/** Convert bounded extractor metadata, discarding every signed stream URL/header. */
object MediaMetadata {
    private val json = Json { ignoreUnknownKeys = true }
    /** Transient probe input; signed URLs and headers never enter MediaAsset or Room. */
    internal class ProbeSource(val url:String,val headers:Map<String,String>)
    internal fun directProbeSource(text:String,formatId:String):ProbeSource? {
        MediaJsonGuard.check(text)
        val root=json.parseToJsonElement(text) as? JsonObject ?: return null
        val entries=(root["formats"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOf(root)
        val source=entries.singleOrNull { it.string("format_id")==formatId } ?: return null
        if(source.string("protocol").ifBlank { "https" } !in setOf("http","https")) return null
        val url=source.string("url").takeIf(String::isNotBlank) ?: return null
        val permitted=setOf("user-agent","referer","origin","accept","accept-language")
        val headers=((root["http_headers"] as? JsonObject).orEmpty() + (source["http_headers"] as? JsonObject).orEmpty())
            .mapNotNull { (name,value) ->
                val content=(value as? JsonPrimitive)?.contentOrNull
                if(name.lowercase() in permitted && content!=null && content.length<=2048 && content.none { it<' ' || it=='\u007f' }) name to content else null
            }.toMap()
        return ProbeSource(url,headers)
    }
    fun parse(text: String): MediaAsset {
        MediaJsonGuard.check(text)
        val value = json.parseToJsonElement(text).jsonObject
        val kind = value.string("_type")
        require(kind !in setOf("playlist", "multi_video") && value["entries"] == null) { "PLAYLIST_REQUIRES_ITEM_SELECTION" }
        val live = value.bool("is_live") || value.string("live_status") in setOf("is_live", "is_upcoming", "post_live")
        require(!live) { "LIVE_MEDIA_NOT_FINITE" }
        val extractorName = value.string("extractor_key").ifBlank { value.string("extractor") }
        val isGeneric = extractorName.equals("generic", ignoreCase = true)
        val rawEntries=value["formats"] as? JsonArray
        require(rawEntries==null || rawEntries.size<=2000) { "TOO_MANY_MEDIA_FORMATS" }
        val entries = rawEntries?.mapNotNull { it as? JsonObject } ?: listOf(value)
        require(entries.size <= 2000) { "TOO_MANY_MEDIA_FORMATS" }
        val formats = entries.mapNotNull { f ->
            if (f.string("url").isBlank()) return@mapNotNull null
            val ext = f.string("ext").lowercase()
            val width = f.int("width")
            val height = f.int("height")
            val rawVcodec = f.string("vcodec")
            val rawAcodec = f.string("acodec")
            val protocol = f.string("protocol").ifBlank { if (f.string("url").startsWith("https://")) "https" else "http" }
            // "none" means an absent track; "unknown" means an existing track whose
            // codec was not reported. Instagram sets acodec="none" for has_audio=false
            // and omits it for ordinary progressive videos with audio.
            val vcodec = when {
                rawVcodec.isNotBlank() -> rawVcodec
                !isGeneric && (height != null || width != null) && ext in setOf("mp4", "webm", "mov", "mkv", "m4v") -> "unknown"
                else -> "none"
            }
            val acodec = when {
                rawAcodec.isNotBlank() -> rawAcodec
                !isGeneric && (f.double("abr") != null || ext in setOf("mp3", "m4a", "opus", "ogg", "wav", "aac", "flac")) -> "unknown"
                !isGeneric && vcodec != "none" && protocol in setOf("http", "https") &&
                    ext in setOf("mp4", "webm", "mov", "mkv", "m4v") -> "unknown"
                else -> "none"
            }
            MediaFormat(
                id = f.string("format_id"), extension = ext,
                videoCodec = vcodec, audioCodec = acodec,
                width = width, height = height, fps = f.double("fps"),
                bitrateKbps = f.double("tbr") ?: f.double("vbr"), audioBitrateKbps = f.double("abr"), bytes = f.long("filesize"),
                approximateBytes = f.long("filesize_approx"), language = f.string("language").takeIf(String::isNotBlank),
                protocol = protocol,
                drm = f.bool("has_drm") || value.bool("has_drm"), preference = f.int("preference") ?: 0
            )
        }
        val uploader = value.string("uploader").ifBlank { value.string("channel") }.take(256).takeIf(String::isNotBlank)
        val thumb = value.string("thumbnail").takeIf {
            it.startsWith("https://") && it.length <= 1024 && !it.contains("secret=", ignoreCase = true)
        }
        return MediaAsset(
            value.string("id"),
            extractorName,
            value.string("title").take(1024),
            value.double("duration"),
            formats,
            uploader = uploader,
            thumbnailUrl = thumb
        )
    }
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""
    private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull == true
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.double(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 }
}
