package com.jumlo.app.core

import java.net.URI
import java.util.Locale

/**
 * Default HTTP compatibility profiles belong to the transport, never the download UI.
 * These are ordinary request headers, not real browsers, login sessions, DRM decoders,
 * or a way to evade explicit access denials. Keep retries bounded and side-effect free.
 */
data class BrowserProfile(val id: String, val userAgent: String, val acceptLanguage: String = "en-US,en;q=0.8")

object BrowserProfilePolicy {
    private val mobile = BrowserProfile("android", "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
    private val desktop = BrowserProfile("desktop", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
    private val mobileFirst = setOf("tiktok.com", "instagram.com", "facebook.com", "fb.com", "threads.net")

    fun forUrl(url: String): List<BrowserProfile> {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.ROOT).orEmpty()
        return if (mobileFirst.any { host == it || host.endsWith(".$it") }) listOf(mobile, desktop)
            else listOf(desktop, mobile)
    }

    /** A successful public HTML page without playable candidates may have a
     * mobile/desktop equivalent. Never make more than one alternate attempt. */
    fun tryAlternatePage(hasVerifiedMedia: Boolean, attempt: Int, profileCount: Int): Boolean =
        !hasVerifiedMedia && attempt >= 0 && attempt + 1 < profileCount

    /** A 401/403/429 means denied/throttled: profile rotation must not bypass it. */
    fun tryNext(responseCode: Int, attempt: Int, profileCount: Int): Boolean =
        responseCode in setOf(406, 415) && attempt >= 0 && attempt + 1 < profileCount
}
