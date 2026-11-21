package com.jumlo.app.core.media

import com.jumlo.app.core.DownloadLinkPolicy

/** Pure routing contract for two real installed engines; no network request is made here. */
object MediaEngineRouting {
    private val PAGE_SOURCE_ID = Regex("page_[0-9]{1,4}")
    private val DIRECT_CODECS = setOf("direct", "none")
    private val HTTP_PROTOCOLS = setOf("http", "https")
    fun preferExtractor(url: String): Boolean = DownloadLinkPolicy.requiresMediaExtractor(url)

    /** Direct downloads are identifiable by the source IDs and direct transport marker. */
    fun isNativeChoice(choice: MediaChoice): Boolean = choice.formats.size == 1 &&
        choice.formats.single().let { source ->
            (source.id == "source" || PAGE_SOURCE_ID.matches(source.id)) &&
                (source.videoCodec == "direct" || source.audioCodec == "direct") &&
                source.videoCodec in DIRECT_CODECS && source.audioCodec in DIRECT_CODECS &&
                source.protocol in HTTP_PROTOCOLS
        }

    /** Do not retry via a different engine on auth, ban, rate limits, or invalid input. */
    fun canUseAlternate(error: String?): Boolean = error == "MEDIA_SITE_UNSUPPORTED"
}
