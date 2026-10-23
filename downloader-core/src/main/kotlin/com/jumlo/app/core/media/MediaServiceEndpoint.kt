package com.jumlo.app.core.media

import java.net.URI
import java.util.Locale

/** Validation shared by preferences and every optional-backend request. */
object MediaServiceEndpoint {
    fun normalize(value: String, allowDebugLoopback: Boolean = false): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        require(trimmed.length <= 300 && trimmed.none { it.isISOControl() || it == '\\' }) { ERROR }
        // URI exceptions include their input. Replace them with the stable, non-sensitive code.
        val uri = try { URI(trimmed) } catch (_: Exception) { throw IllegalArgumentException(ERROR) }
        val host = uri.host?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException(ERROR)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(!uri.isOpaque && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { ERROR }
        require(uri.port == -1 || uri.port in 1..65535) { ERROR }
        require(scheme == "https" || scheme == "http" && allowDebugLoopback && host in loopbackHosts) { ERROR }
        return trimmed.trimEnd('/')
    }

    fun origin(value: String, allowDebugLoopback: Boolean = false): String {
        val endpoint = normalize(value, allowDebugLoopback)
        if (endpoint.isEmpty()) return ""
        val uri = URI(endpoint)
        val scheme = uri.scheme.lowercase(Locale.ROOT)
        val port = if (uri.port >= 0) uri.port else if (scheme == "https") 443 else 80
        return "$scheme://${uri.host.lowercase(Locale.ROOT)}:$port"
    }

    private const val ERROR = "INVALID_MEDIA_SERVICE_URL"
    // Explicit spellings avoid DNS lookup and numeric/authority parser ambiguities.
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "[::1]", "[0:0:0:0:0:0:0:1]")
}
