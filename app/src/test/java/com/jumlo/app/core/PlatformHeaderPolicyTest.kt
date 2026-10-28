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

    @Test fun realStreamRedirectRetainsOnlyOriginScopedCustomHeaders() {
        val start = "https://source.example.org/start"
        val sameOriginHop = "https://source.example.org/continue"
        val newHost = "https://cdn.example.net/file.bin"
        val privateAgent = "AccountSpecificAgent/1.0"
        val custom = mapOf("Referer" to "https://source.example.org/account?id=private", "Origin" to "https://source.example.org")
        val observed = mutableListOf<Pair<String, Map<String, String>>>()
        val transport = HttpTransport { target, _ ->
            val actual = PlatformHeaderPolicy.headersFor(
                url = target, customHeaderOrigin = start,
                customUserAgent = privateAgent, customHeaders = custom
            )
            observed += target to actual
            when (target) {
                start -> TransportResponse(302, mapOf("location" to sameOriginHop), byteArrayOf().inputStream())
                sameOriginHop -> TransportResponse(302, mapOf("location" to newHost), byteArrayOf().inputStream())
                newHost -> TransportResponse(200, mapOf("content-length" to "5", "content-type" to "application/octet-stream"), "hello".byteInputStream())
                else -> error("Unexpected redirected target: $target")
            }
        }
        val file = java.nio.file.Files.createTempFile("scoped-headers-", ".part").toFile()
        try {
            val result = StreamTransfer(transport).execute(TransferRequest(
                url = start, partialFile = file, minFreeBytes = 0L, maxRetries = 0
            ))
            assertEquals(5L, result.bytes)
            assertEquals("hello", file.readText())
            assertEquals(listOf(start, sameOriginHop, newHost), observed.map { it.first })
            for (index in 0..1) {
                assertEquals(custom["Referer"], observed[index].second["Referer"])
                assertEquals(privateAgent, observed[index].second["User-Agent"])
            }
            val redirected = observed.last().second
            assertFalse(redirected.keys.any { it.equals("Referer", ignoreCase = true) })
            assertFalse(redirected.keys.any { it.equals("Origin", ignoreCase = true) })
            assertFalse(redirected["User-Agent"] == privateAgent)
        } finally {
            file.delete()
        }
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
