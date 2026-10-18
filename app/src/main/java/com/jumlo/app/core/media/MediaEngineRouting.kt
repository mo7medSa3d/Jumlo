package com.jumlo.app.core.media

import com.jumlo.app.core.DownloadLinkPolicy

/** Pure routing contract for two real installed engines; no network request is made here. */
object MediaEngineRouting {
    fun preferExtractor(url: String): Boolean = DownloadLinkPolicy.requiresMediaExtractor(url)

    /** Direct downloads are identifiable by the source IDs and direct transport marker. */
    fun isNativeChoice(choice: MediaChoice): Boolean = choice.formats.size == 1 &&
        choice.formats.single().let { source ->
            (source.id == "source" || Regex("page_[0-9]{1,4}").matches(source.id)) &&
                (source.videoCodec == "direct" || source.audioCodec == "direct") &&
                source.videoCodec in setOf("direct", "none") && source.audioCodec in setOf("direct", "none") &&
                source.protocol in setOf("http", "https")
        }

    /** Do not retry via a different engine on auth, ban, rate limits, or invalid input. */
    fun canUseAlternate(error: String?): Boolean = error == "MEDIA_SITE_UNSUPPORTED"
}
