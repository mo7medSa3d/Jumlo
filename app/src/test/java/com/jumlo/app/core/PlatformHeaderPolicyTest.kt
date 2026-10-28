package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformHeaderPolicyTest {
    @Test fun scopedCookiesAndSafeExtraHeaders() {
        val headers = PlatformHeaderPolicy.headersFor(
            url = "https://downloads.example.org/file", cookieHeader = "session=token",
            customHeaders = mapOf("Authorization" to "Bearer secret",
                "Cookie" to "override=bad", "Accept-Language" to "ar-EG")
        )
        assertEquals("session=token", headers["Cookie"])
        assertEquals("ar-EG", headers["Accept-Language"])
        assertFalse(headers.keys.any { it.equals("Authorization", ignoreCase = true) })
        val insecure = PlatformHeaderPolicy.headersFor(
            url = "http://downloads.example.org/file", cookieHeader = "session=token")
        assertFalse(insecure.keys.any { it.equals("Cookie", ignoreCase = true) })
    }

    @Test fun customRefererAndOriginDoNotLeakAcrossRedirectOrigins() {
        val sensitive = mapOf(
            "Referer" to "https://origin.example.com/download?private_token=keep_here",
            "Origin" to "https://origin.example.com",
            "Accept-Language" to "ar-EG"
        )
        val original = "https://origin.example.com/path?private_token=keep_here"
        val secretUserAgent = "PrivateClientProfile/1.0"
        val sameSite = PlatformHeaderPolicy.headersFor(
            url = "https://ORIGIN.example.com:443/redirected-file",
            customHeaders = sensitive,
            customUserAgent = secretUserAgent,
            customHeaderOrigin = original
        )
        assertEquals(sensitive["Referer"], sameSite["Referer"])
        assertEquals(sensitive["Origin"], sameSite["Origin"])
        assertEquals("ar-EG", sameSite["Accept-Language"])
        assertEquals(secretUserAgent, sameSite["User-Agent"])

        for (redirected in listOf(
            "https://attacker.example.net/file",
            "https://sub.origin.example.com/file",
            "https://origin.example.com:8443/file",
            "http://origin.example.com/file"
        )) {
            val headers = PlatformHeaderPolicy.headersFor(
                url = redirected,
                customHeaders = sensitive,
                customUserAgent = secretUserAgent,
                customHeaderOrigin = original
            )
            assertFalse("$redirected must not inherit custom User-Agent", headers["User-Agent"] == secretUserAgent)
            assertFalse("$redirected leaked a user Referer", headers.keys.any { it.equals("Referer", true) })
            assertFalse("$redirected leaked a user Origin", headers.keys.any { it.equals("Origin", true) })
            assertFalse("$redirected leaked a custom language", headers.keys.any { it.equals("Accept-Language", true) })
            assertTrue("$redirected must retain normal browser headers", headers.keys.any { it.equals("User-Agent", true) })
        }
    }

    @Test fun originMatchingNormalizesPortsAndRejectsMalformedSources() {
        assertTrue(PlatformHeaderPolicy.sameOrigin("https://example.org/path", "https://EXAMPLE.org:443/other"))
        assertTrue(PlatformHeaderPolicy.sameOrigin("http://example.org", "http://example.org:80"))
        assertFalse(PlatformHeaderPolicy.sameOrigin("https://example.org", "https://example.org:444/other"))
        assertFalse(PlatformHeaderPolicy.sameOrigin("https://example.org", "http://example.org"))
        assertFalse(PlatformHeaderPolicy.sameOrigin("not-a-url", "https://example.org"))
        assertFalse(PlatformHeaderPolicy.sameOrigin("https://example.org", "not-a-url"))
        assertFalse(PlatformHeaderPolicy.sameOrigin("https://example.org", "https://user@example.org/file"))
    }

    @Test fun preferDesktopSelectsPrimaryUserAgentAndRotatesOnFallback() {
        val desktopFirst = PlatformHeaderPolicy.headersFor("https://downloads.example.org/file", preferDesktop = true, compatibilityAttempt = 0)
        val mobileFirst = PlatformHeaderPolicy.headersFor("https://downloads.example.org/file", preferDesktop = false, compatibilityAttempt = 0)
        val mobileFallback = PlatformHeaderPolicy.headersFor("https://downloads.example.org/file", preferDesktop = false, compatibilityAttempt = 1)
        assertTrue(desktopFirst.getValue("User-Agent").contains("X11"))
        assertTrue(mobileFirst.getValue("User-Agent").contains("Android"))
        assertTrue(mobileFallback.getValue("User-Agent").contains("X11"))
    }
}
