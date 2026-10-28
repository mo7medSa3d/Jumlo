package com.jumlo.app.core

import java.net.URI
import java.util.Locale

/**
 * Conservative, per-request headers. Cookies are supplied only by a
 * URL-scoped CookieManager lookup. Never forward credential-like custom
 * headers to redirects or unrelated download hosts.
 */
object PlatformHeaderPolicy {
    private val safeExtras = setOf(
        "accept", "accept-language", "accept-encoding", "range",
        "origin", "referer", "user-agent", "dnt", "x-requested-with"
    )
    private val headerName = Regex("[A-Za-z0-9-]{1,64}")

    /**
     * Extra user-supplied headers (including Origin and Referer) belong only
     * to the origin where the user started. A redirect can change host, port
     * or scheme; never replay those headers to an unrelated destination.
     * Standard browser headers and URL-scoped cookies remain independent.
     */
    fun sameOrigin(initialUrl: String, requestUrl: String): Boolean {
        val initial = runCatching { URI(initialUrl) }.getOrNull() ?: return false
        val request = runCatching { URI(requestUrl) }.getOrNull() ?: return false
        fun port(uri: URI): Int = if (uri.port >= 0) uri.port else when (uri.scheme?.lowercase(Locale.ROOT)) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
        val aScheme = initial.scheme?.lowercase(Locale.ROOT)
        val bScheme = request.scheme?.lowercase(Locale.ROOT)
        return aScheme in setOf("http", "https") &&
            aScheme == bScheme &&
            !initial.host.isNullOrBlank() &&
            initial.host.equals(request.host, ignoreCase = true) &&
            initial.rawUserInfo == null && request.rawUserInfo == null &&
            port(initial) > 0 && port(initial) == port(request)
    }

    fun headersFor(
        url: String,
        customUserAgent: String = "",
        preferDesktop: Boolean = true,
        cookieHeader: String? = null,
        customHeaders: Map<String, String> = emptyMap(),
        compatibilityAttempt: Int = 0,
        customHeaderOrigin: String = url
    ): Map<String, String> {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: throw IllegalArgumentException("INVALID_URL")
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("https", "http") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null) { "INVALID_URL" }

        val allowOriginExtras = sameOrigin(customHeaderOrigin, url)
        val agent = customUserAgent.trim().takeIf {
            allowOriginExtras && compatibilityAttempt == 0 &&
                it.isNotEmpty() && it.length <= 320 && it.none(Char::isISOControl)
        } ?: BrowserProfilePolicy.forUrl(url, preferDesktop).let { profiles ->
            // Attempt 0 is stable for an origin; attempt 1 is used only after
            // a 406/415 content-negotiation response, never after 401/403.
            profiles[compatibilityAttempt.coerceIn(0, profiles.lastIndex)].userAgent
        }
        val headers = linkedMapOf("User-Agent" to agent, "Accept" to "*/*")
        // The browser identity isn't a permission token; no credentials are
        // created, copied or migrated between different hosts.
        val safeCookie = cookieHeader?.trim()?.takeIf {
            uri.scheme.equals("https", true) && it.isNotEmpty() &&
                it.length <= 32768 && it.none(Char::isISOControl)
        }
        if (safeCookie != null) headers["Cookie"] = safeCookie

        // Cross-origin redirects may be controlled by a third party. The
        // original user's Referer/Origin/custom headers must not follow them.
        // URL-scoped CookieManager lookups are performed by the caller.
        val scopedExtras = if (allowOriginExtras) customHeaders else emptyMap()
        for ((key, value) in scopedExtras) {
            val name = key.trim()
            val text = value.trim()
            if (!headerName.matches(name) || name.lowercase(Locale.ROOT) !in safeExtras ||
                (compatibilityAttempt > 0 && name.equals("user-agent", true)) ||
                text.isEmpty() || text.length > 2048 || text.any(Char::isISOControl)) continue
            headers[name] = text
        }
        return headers
    }
}
