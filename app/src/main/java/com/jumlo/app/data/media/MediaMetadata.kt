package com.jumlo.app.data.media

import com.jumlo.app.core.media.*
import kotlinx.serialization.json.*

/** Convert bounded extractor metadata, discarding every signed stream URL/header. */
object MediaMetadata {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): MediaAsset {
        MediaJsonGuard.check(text)
        val value = json.parseToJsonElement(text).jsonObject
        val kind = value.string("_type")
        require(kind !in setOf("playlist", "multi_video") && value["entries"] == null) { "PLAYLIST_REQUIRES_ITEM_SELECTION" }
        val live = value.bool("is_live") || value.string("live_status") in setOf("is_live", "is_upcoming", "post_live")
        require(!live) { "LIVE_MEDIA_NOT_FINITE" }
        val entries = (value["formats"] as? JsonArray)?.map { it.jsonObject } ?: listOf(value)
        require(entries.size <= 2000) { "TOO_MANY_MEDIA_FORMATS" }
        val formats = entries.mapNotNull { f ->
            if (f.string("url").isBlank()) return@mapNotNull null
            // Missing codecs must not invent an audio/video stream.
            MediaFormat(
                id = f.string("format_id"), extension = f.string("ext").lowercase(),
                videoCodec = f.string("vcodec").ifBlank { "none" }, audioCodec = f.string("acodec").ifBlank { "none" },
                width = f.int("width"), height = f.int("height"), fps = f.double("fps"),
                bitrateKbps = f.double("tbr"), audioBitrateKbps = f.double("abr"), bytes = f.long("filesize"),
                approximateBytes = f.long("filesize_approx"), language = f.string("language").takeIf(String::isNotBlank),
                protocol = f.string("protocol").ifBlank { if (f.string("url").startsWith("https://")) "https" else "http" },
                drm = f.bool("has_drm") || value.bool("has_drm"), preference = f.int("preference") ?: 0)
        }
        val uploader = value.string("uploader").ifBlank { value.string("channel") }.take(256).takeIf(String::isNotBlank)
        val thumb = value.string("thumbnail").takeIf { it.startsWith("https://") && '?' !in it && it.length <= 512 }
        return MediaAsset(
            value.string("id"),
            value.string("extractor_key").ifBlank { value.string("extractor") },
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
