package com.jumlo.app.core.media

/** Only measured extractor metadata. Unknown values are null, never a fabricated quality. */
enum class MediaMode { VIDEO, AUDIO }
enum class AudioOutput { ORIGINAL, MP3, M4A, OPUS }

data class MediaFormat(
    val id: String, val extension: String,
    val videoCodec: String = "none", val audioCodec: String = "none",
    val width: Int? = null, val height: Int? = null, val fps: Double? = null,
    val bitrateKbps: Double? = null, val audioBitrateKbps: Double? = null,
    val bytes: Long? = null, val approximateBytes: Long? = null,
    val language: String? = null, val protocol: String = "https",
    val drm: Boolean = false, val preference: Int = 0
) {
    val hasVideo get() = videoCodec.isNotBlank() && videoCodec != "none"
    val hasAudio get() = audioCodec.isNotBlank() && audioCodec != "none"
}
data class MediaAsset(
    val id: String, val extractor: String, val title: String,
    val durationSeconds: Double?, val formats: List<MediaFormat>,
    val isLive: Boolean = false, val isPlaylist: Boolean = false,
    val uploader: String? = null, val thumbnailUrl: String? = null
)
data class MediaChoice(
    val key: String, val mode: MediaMode, val formats: List<MediaFormat>,
    val container: String, val requiresMux: Boolean,
    val expectedHeight: Int?, val estimatedBytes: Long?, val sizeIsApproximate: Boolean
) {
    val expectsAudio get() = formats.any { it.hasAudio }
    val formatIds get() = formats.map { it.id }
}
data class MediaProgress(
    val streamId: String, val downloadedBytes: Long,
    val totalBytes: Long?, val estimatedBytes: Long?, val bytesPerSecond: Double?, val etaSeconds: Long?
)
data class MediaFileInfo(val extension: String, val mimeType: String, val sizeBytes: Long)
