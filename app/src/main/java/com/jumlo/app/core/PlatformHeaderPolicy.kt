package com.jumlo.app.core

import java.net.URI
import java.util.Locale

/**
 * Conservative, per-request headers. Cookies are supplied only by a
 * URL-scoped CookieManager lookup. Never forward credential-like custom
 * headers to redirects or unrelated download hosts.
 */
object PlatformHeaderPolicy {
    private const val desktopAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val mobileAgent =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private val safeExtras = setOf(
        "accept", "accept-language", "accept-encoding", "range",
        "origin", "referer", "user-agent", "dnt", "x-requested-with"
    )
    private val headerName = Regex("[A-Za-z0-9-]{1,64}")

    fun headersFor(
        url: String,
        customUserAgent: String = "",
        preferDesktop: Boolean = true,
        cookieHeader: String? = null,
        customHeaders: Map<String, String> = emptyMap()
    ): Map<String, String> {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: throw IllegalArgumentException("INVALID_URL")
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("https", "http") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null) { "INVALID_URL" }

        val agent = customUserAgent.trim().takeIf {
            it.isNotEmpty() && it.length <= 320 && it.none(Char::isISOControl)
        } ?: if (preferDesktop) desktopAgent else mobileAgent
        val headers = linkedMapOf("User-Agent" to agent, "Accept" to "*/*")
        val safeCookie = cookieHeader?.trim()?.takeIf {
            uri.scheme.equals("https", true) && it.isNotEmpty() &&
                it.length <= 32768 && it.none(Char::isISOControl)
        }
        if (safeCookie != null) headers["Cookie"] = safeCookie

        // Custom headers never override the cookie jar or transport security.
        for ((key, value) in customHeaders) {
            val name = key.trim()
            val text = value.trim()
            if (!headerName.matches(name) || name.lowercase(Locale.ROOT) !in safeExtras ||
                text.isEmpty() || text.length > 2048 || text.any(Char::isISOControl)) continue
            headers[name] = text
        }
        return headers
    }
}
