package com.jumlo.app.core

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamTransferSecurityTest {

    @Test
    fun streamTransferIsolatesCookiesAcrossHostPortAndSchemeRedirects() {
        val cases = listOf(
            // Cross-host redirect must not replay cookies.
            Triple("https://origin.example.com/start", "https://cdn.example.net/file.bin", false),
            // Cross-port redirect must not replay cookies.
            Triple("https://origin.example.com/start", "https://origin.example.com:8443/file.bin", false),
            // Scheme upgrade (http -> https) must not carry cleartext cookies into https.
            Triple("http://origin.example.com/start", "https://origin.example.com/file.bin", true)
        )

        for ((startUrl, targetUrl, cleartext) in cases) {
            val observed = mutableListOf<Pair<String, Map<String, String>>>()
            val file = Files.createTempFile("redirect-cookie-iso-", ".part").toFile()
            file.delete()
            try {
                val transport = HttpTransport { url, headers ->
                    observed += url to headers
                    if (url == startUrl) {
                        TransportResponse(
                            code = 302,
                            headers = mapOf(
                                "Location" to targetUrl,
                                "Set-Cookie" to "session=secret123; Path=/"
                            ),
                            body = ByteArrayInputStream(ByteArray(0))
                        )
                    } else {
                        TransportResponse(
                            code = 200,
                            headers = mapOf("Content-Length" to "4"),
                            body = ByteArrayInputStream("data".toByteArray())
                        )
                    }
                }
                StreamTransfer(transport).execute(
                    TransferRequest(
                        url = startUrl,
                        partialFile = file,
                        cleartextConsent = cleartext,
                        minFreeBytes = 0L,
                        maxRetries = 0
                    )
                )
                assertEquals(2, observed.size)
                assertNull(
                    "Cookie leaked across authority boundary $startUrl -> $targetUrl",
                    observed[1].second["Cookie"]
                )
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun streamTransferPreservesCookiesOnSameOriginRedirectAndBlocksHttpsDowngrade() {
        val observed = mutableListOf<Pair<String, Map<String, String>>>()
        val file = Files.createTempFile("redirect-same-origin-", ".part").toFile()
        file.delete()
        try {
            val transport = HttpTransport { url, headers ->
                observed += url to headers
                if (url.endsWith("/start")) {
                    TransportResponse(
                        code = 302,
                        headers = mapOf(
                            "Location" to "https://origin.example.com:443/final",
                            "Set-Cookie" to "session=ok123; Path=/"
                        ),
                        body = ByteArrayInputStream(ByteArray(0))
                    )
                } else {
                    TransportResponse(
                        code = 200,
                        headers = mapOf("Content-Length" to "2"),
                        body = ByteArrayInputStream("ok".toByteArray())
                    )
                }
            }
            StreamTransfer(transport).execute(
                TransferRequest(
                    url = "https://origin.example.com/start",
                    partialFile = file,
                    minFreeBytes = 0L,
                    maxRetries = 0
                )
            )
            assertEquals("session=ok123", observed[1].second["Cookie"])

            val downgrade = StreamTransfer { _, _ ->
                TransportResponse(
                    code = 302,
                    headers = mapOf("Location" to "http://origin.example.com/downgrade"),
                    body = ByteArrayInputStream(ByteArray(0))
                )
            }
            val err = assertThrows(IllegalArgumentException::class.java) {
                downgrade.execute(
                    TransferRequest(
                        url = "https://origin.example.com/start",
                        partialFile = file.apply { delete() },
                        cleartextConsent = true,
                        minFreeBytes = 0L,
                        maxRetries = 0
                    )
                )
            }
            assertEquals("INSECURE_REDIRECT", err.message)
        } finally {
            file.delete()
        }
    }

    @Test
    fun streamTransferClosesBodyAndTransportOnceWithoutMaskingPrimaryFailure() {
        val bodyCloses = AtomicInteger(0)
        val responseCloses = AtomicInteger(0)
        val file = Files.createTempFile("stream-close-", ".part").toFile()
        file.delete()
        try {
            val failingBody = object : InputStream() {
                override fun read(): Int = throw IOException("PRIMARY_READ_FAILURE")
                override fun close() {
                    bodyCloses.incrementAndGet()
                    throw IOException("SECONDARY_CLOSE_FAILURE")
                }
            }
            val transfer = StreamTransfer { _, _ ->
                TransportResponse(
                    code = 200,
                    headers = mapOf("Content-Length" to "10"),
                    body = failingBody,
                    close = {
                        responseCloses.incrementAndGet()
                        throw IOException("SECONDARY_TRANSPORT_CLOSE_FAILURE")
                    }
                )
            }
            val err = assertThrows(IOException::class.java) {
                transfer.execute(
                    TransferRequest(
                        url = "https://example.com/file.bin",
                        partialFile = file,
                        minFreeBytes = 0L,
                        maxRetries = 0
                    )
                )
            }
            assertEquals("PRIMARY_READ_FAILURE", err.message)
            assertEquals(1, bodyCloses.get())
            assertEquals(1, responseCloses.get())
        } finally {
            file.delete()
        }
    }

    @Test
    fun platformHeaderPolicyRejectsInvalidPortsAndStripsCredentialsOnRedirect() {
        assertFalse(PlatformHeaderPolicy.sameOrigin("https://example.com:70000/a", "https://example.com:70000/b"))
        assertFalse(PlatformHeaderPolicy.sameOrigin("https://example.com:0/a", "https://example.com:0/b"))
        assertThrows(IllegalArgumentException::class.java) {
            PlatformHeaderPolicy.headersFor("https://example.com:70000/file")
        }
        val redirected = PlatformHeaderPolicy.headersFor(
            url = "https://cdn.other.net/file",
            customUserAgent = "PrivateUA/1.0",
            customHeaders = mapOf(
                "Authorization" to "Bearer secret",
                "Proxy-Authorization" to "Basic secret",
                "Cookie" to "a=b",
                "Referer" to "https://origin.example.com/private",
                "Origin" to "https://origin.example.com"
            ),
            customHeaderOrigin = "https://origin.example.com/start"
        )
        assertFalse(redirected.keys.any { it.equals("Authorization", ignoreCase = true) })
        assertFalse(redirected.keys.any { it.equals("Proxy-Authorization", ignoreCase = true) })
        assertFalse(redirected.keys.any { it.equals("Cookie", ignoreCase = true) })
        assertFalse(redirected.keys.any { it.equals("Referer", ignoreCase = true) })
        assertFalse(redirected.keys.any { it.equals("Origin", ignoreCase = true) })
        assertTrue(redirected["User-Agent"] != "PrivateUA/1.0")
    }

    @Test
    fun streamTransferClearsStalePartialFileOnOffsetZeroBeforeRetryingTransientError() {
        val file = Files.createTempFile("stream-stale-partial-", ".part").toFile()
        try {
            file.writeBytes("stale-previous-bytes".toByteArray(Charsets.UTF_8))
            val attempts = AtomicInteger(0)
            val transfer = StreamTransfer { _, headers ->
                assertNull("Fresh offset=0 attempt must not send Range", headers["Range"])
                if (attempts.incrementAndGet() == 1) {
                    TransportResponse(503, emptyMap(), ByteArrayInputStream(ByteArray(0)))
                } else {
                    TransportResponse(
                        code = 200,
                        headers = mapOf("Content-Length" to "5"),
                        body = ByteArrayInputStream("fresh".toByteArray(Charsets.UTF_8))
                    )
                }
            }
            val result = transfer.execute(
                TransferRequest(
                    url = "https://example.com/fresh.bin",
                    partialFile = file,
                    offset = 0L,
                    minFreeBytes = 0L,
                    maxRetries = 1,
                    retryBackoffMs = 0L
                )
            )
            assertEquals(2, attempts.get())
            assertEquals(5L, result.bytes)
            assertEquals("fresh", file.readText(Charsets.UTF_8))
        } finally {
            file.delete()
        }
    }
}
