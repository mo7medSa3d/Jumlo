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

    @Test fun preferDesktopSelectsPrimaryUserAgentAndRotatesOnFallback() {
        val desktopFirst = PlatformHeaderPolicy.headersFor("https://downloads.example.org/file", preferDesktop = true, compatibilityAttempt = 0)
        val mobileFirst = PlatformHeaderPolicy.headersFor("https://downloads.example.org/file", preferDesktop = false, compatibilityAttempt = 0)
        val mobileFallback = PlatformHeaderPolicy.headersFor("https://downloads.example.org/file", preferDesktop = false, compatibilityAttempt = 1)
        assertTrue(desktopFirst.getValue("User-Agent").contains("X11"))
        assertTrue(mobileFirst.getValue("User-Agent").contains("Android"))
        assertTrue(mobileFallback.getValue("User-Agent").contains("X11"))
    }
}
