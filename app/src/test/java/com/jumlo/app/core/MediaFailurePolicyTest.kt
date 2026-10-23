package com.jumlo.app.core

import com.jumlo.app.core.media.MediaFailurePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFailurePolicyTest {
    @Test fun ageAndRateRestrictionsTakePriorityOverLoginText() {
        assertEquals("MEDIA_AGE_RESTRICTED", MediaFailurePolicy.extractorMessage("Sign in to confirm your age"))
        assertEquals("MEDIA_AGE_RESTRICTED", MediaFailurePolicy.extractorMessage("This video is age-restricted"))
        assertEquals("MEDIA_RATE_LIMITED", MediaFailurePolicy.extractorMessage("HTTP 429: sign in and try again"))
    }

    @Test fun extractorFailuresKeepTheirActionableCategories() {
        val examples = mapOf(
            "This video is private" to "MEDIA_PRIVATE_CONTENT",
            "Video has been removed" to "MEDIA_REMOVED",
            "Unsupported URL" to "MEDIA_SITE_UNSUPPORTED",
            "Connection reset by peer" to "MEDIA_NETWORK_ERROR",
            "This media is protected by DRM" to "MEDIA_ENCRYPTED_SOURCE",
            "Requested format is not available" to "MEDIA_REANALYSIS_REQUIRED",
            "Sign in to confirm you are not a bot" to "MEDIA_SESSION_REQUIRED"
        )
        examples.forEach { (message, expected) -> assertEquals(expected, MediaFailurePolicy.extractorMessage(message)) }
    }

    @Test fun serviceStatusWorksWhenAnErrorBodyIsMissing() {
        assertEquals("MEDIA_RATE_LIMITED", MediaFailurePolicy.httpStatus(429))
        assertEquals("MEDIA_REMOVED", MediaFailurePolicy.httpStatus(410))
        assertEquals("MEDIA_SOURCE_REJECTED", MediaFailurePolicy.httpStatus(403))
        assertEquals("MEDIA_NETWORK_ERROR", MediaFailurePolicy.httpStatus(503))
    }

    @Test fun untrustedDiagnosticsNeverBecomePublicErrorCodes() {
        assertEquals("MEDIA_EXTRACTION_FAILED", MediaFailurePolicy.serviceCode("ACCESS_TOKEN_SECRET"))
        assertEquals("MEDIA_EXTRACTION_FAILED", MediaFailurePolicy.serviceCode("https://example.org/?token=secret"))
        assertEquals("MEDIA_EXTRACTION_FAILED", MediaFailurePolicy.extractorMessage("unexpected value https://example.org/?token=secret"))
        assertEquals("MEDIA_PRIVATE_CONTENT", MediaFailurePolicy.serviceCode("MEDIA_PRIVATE_CONTENT"))
    }
}
