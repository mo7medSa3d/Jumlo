package com.jumlo.app.core

import com.jumlo.app.core.media.MediaServiceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaServiceEndpointTest {
    @Test fun anEmptySettingKeepsTheBackendDisabled() {
        assertEquals("", MediaServiceEndpoint.normalize("  "))
    }

    @Test fun httpsBasePathsArePreservedWithoutTrailingSeparators() {
        assertEquals("https://media.example.com/v1", MediaServiceEndpoint.normalize("  https://media.example.com/v1/  "))
        assertEquals("https://media.example.com:8443", MediaServiceEndpoint.normalize("https://media.example.com:8443/"))
    }

    @Test fun releaseRejectsCleartextEvenForLoopback() {
        listOf("http://media.example.com", "http://localhost:8731", "http://127.0.0.1:8731", "http://[::1]:8731")
            .forEach { rejected(it) }
    }

    @Test fun debugAllowsOnlyExplicitLoopbackHttp() {
        listOf("http://localhost:8731", "http://127.0.0.1:8731", "http://[::1]:8731").forEach {
            assertEquals(it, MediaServiceEndpoint.normalize(it, allowDebugLoopback = true))
        }
        listOf("http://media.example.com", "http://192.168.0.4:8731", "http://10.0.2.2:8731",
            "http://localhost.example.com", "http://127.0.0.1.example.com", "http://2130706433:8731")
            .forEach { rejected(it, debug = true) }
    }

    @Test fun credentialsQueriesAndFragmentsAreNeverEndpoints() {
        listOf("https://user:password@media.example.com", "https://user@media.example.com",
            "https://media.example.com?token=secret", "https://media.example.com?",
            "https://media.example.com#private", "https://media.example.com#")
            .forEach { rejected(it, debug = true) }
    }

    @Test fun malformedOrAmbiguousAuthoritiesAreRejectedWithOnlyTheStableCode() {
        listOf("https://", "https:///media.example.com", "ftp://media.example.com",
            "https://media.example.com:0", "https://media.example.com:65536",
            "https://media.example.com:wrong", "https://media.example.com\\@evil.example",
            "https://media.example.com/\nsecret", "https://media.example.com/" + "a".repeat(300))
            .forEach { rejected(it) }
    }

    private fun rejected(value: String, debug: Boolean = false) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MediaServiceEndpoint.normalize(value, allowDebugLoopback = debug)
        }
        assertEquals("INVALID_MEDIA_SERVICE_URL", error.message)
    }
}
