package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
