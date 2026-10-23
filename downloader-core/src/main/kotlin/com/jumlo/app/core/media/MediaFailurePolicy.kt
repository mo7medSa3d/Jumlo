package com.jumlo.app.core.media

import java.util.Locale

/** Converts untrusted extractor diagnostics into a fixed, localizable vocabulary. */
object MediaFailurePolicy {
    private val serviceCodes = setOf(
        "MEDIA_SITE_UNSUPPORTED", "MEDIA_NO_USABLE_FORMAT", "MEDIA_REANALYSIS_REQUIRED",
        "MEDIA_PRIVATE_CONTENT", "MEDIA_AGE_RESTRICTED", "MEDIA_SESSION_REQUIRED",
        "MEDIA_GEO_BLOCKED", "MEDIA_REMOVED", "MEDIA_ENCRYPTED_SOURCE", "MEDIA_RATE_LIMITED",
        "MEDIA_NETWORK_ERROR", "MEDIA_TLS_FAILED", "MEDIA_SOURCE_REJECTED",
        "MEDIA_EXTRACTION_FAILED", "MEDIA_STORAGE_FULL", "MEDIA_OUTPUT_LIMIT",
        "MEDIA_CONVERSION_UNAVAILABLE", "MEDIA_RUNTIME_UNAVAILABLE"
    )

    fun serviceCode(raw: String): String = raw.takeIf { it in serviceCodes } ?: "MEDIA_EXTRACTION_FAILED"

    fun httpStatus(status: Int): String = when (status) {
        401, 403 -> "MEDIA_SOURCE_REJECTED"
        404, 410 -> "MEDIA_REMOVED"
        408, 425, 500, 502, 503, 504 -> "MEDIA_NETWORK_ERROR"
        413 -> "MEDIA_OUTPUT_LIMIT"
        415 -> "MEDIA_SITE_UNSUPPORTED"
        429 -> "MEDIA_RATE_LIMITED"
        else -> "MEDIA_EXTRACTION_FAILED"
    }

    fun extractorMessage(raw: String): String {
        val message = raw.takeLast(64 * 1024).lowercase(Locale.ROOT)
        fun contains(vararg words: String) = words.any { it in message }
        return when {
            "media_http_consent_required" in message -> "CLEARTEXT_CONSENT_REQUIRED"
            contains("media_tls_downgrade", "certificate") -> "MEDIA_TLS_FAILED"
            contains("age-restricted", "age restricted", "confirm your age", "age verification") -> "MEDIA_AGE_RESTRICTED"
            contains("429", "rate limit", "too many requests") -> "MEDIA_RATE_LIMITED"
            contains("unsupported url", "unsupported site") -> "MEDIA_SITE_UNSUPPORTED"
            "requested format is not available" in message -> "MEDIA_REANALYSIS_REQUIRED"
            contains("no video formats found", "no formats found") -> "MEDIA_NO_USABLE_FORMAT"
            contains("private video", "this video is private", "members-only", "private content") -> "MEDIA_PRIVATE_CONTENT"
            contains("not available in your country", "geo-restricted", "geo restricted") -> "MEDIA_GEO_BLOCKED"
            contains("video unavailable", "has been removed", "does not exist", "404", "410 gone") -> "MEDIA_REMOVED"
            contains("drm", "encrypted") -> "MEDIA_ENCRYPTED_SOURCE"
            contains("sign in", "login", "log in", "logged-in", "cookies", "not a bot", "authentication is required") -> "MEDIA_SESSION_REQUIRED"
            contains("timed out", "connection reset", "network is unreachable", "unable to download webpage", "name or service not known") -> "MEDIA_NETWORK_ERROR"
            contains("no space left", "disk full") -> "MEDIA_STORAGE_FULL"
            contains("403", "401") -> "MEDIA_SOURCE_REJECTED"
            else -> "MEDIA_EXTRACTION_FAILED"
        }
    }
}
