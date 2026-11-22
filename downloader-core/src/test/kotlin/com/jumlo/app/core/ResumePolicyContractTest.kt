package com.jumlo.app.core

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ResumePolicyContractTest {
    @Test fun parseRangeRejectsUnknownAndContradictoryLengths() {
        assertEquals(ByteRange(10, 19, 30), ResumePolicy.range("bytes 10-19/30"))
        assertNull(ResumePolicy.range("bytes 10-19/19"))
        assertNull(ResumePolicy.range("bytes 20-19/30"))
        assertNull(ResumePolicy.range("bytes 10-19"))
    }

    @Test fun validatesResumptionOnlyWithMatchingStrongValidatorAndCompleteRange() {
        val headers = mapOf(
            "etag" to "\"unchanged\"",
            "content-range" to "bytes 10-19/30",
            "content-length" to "10",
            "content-encoding" to "identity"
        )
        ResumePolicy.validate(10, "\"unchanged\"", null, 30, 206, headers)
        assertThrows(RestartRequired::class.java) {
            ResumePolicy.validate(10, "\"previous\"", null, 30, 206, headers)
        }
        assertThrows(RestartRequired::class.java) {
            ResumePolicy.validate(10, "\"unchanged\"", null, 30, 200, headers)
        }
        assertThrows(RestartRequired::class.java) {
            ResumePolicy.validate(10, "\"unchanged\"", null, 30, 206, headers + ("content-range" to "bytes 10-19/*"))
        }
    }

    @Test fun onlyAcceptsFullInitialResponses() {
        ResumePolicy.validate(0, null, null, null, 200, emptyMap())
        assertThrows(IllegalArgumentException::class.java) {
            ResumePolicy.validate(0, null, null, null, 206, emptyMap())
        }
    }

    @Test fun validatesMixedCaseHeadersAndRejectsCompressedResume() {
        val mixedCase = mapOf(
            "ETag" to "\"unchanged\"",
            "Content-Range" to "bytes 10-19/30",
            "Content-Length" to "10",
            "Content-Encoding" to "gzip"
        )
        assertThrows(RestartRequired::class.java) {
            ResumePolicy.validate(10, "\"unchanged\"", null, 30, 206, mixedCase)
        }
    }

    @Test fun streamTransferFailsFastOnOverflowAndDropsUnquotedEtag() {
        val partial = File.createTempFile("stream-overflow-", ".part")
        partial.delete()
        try {
            val overflowTransfer = StreamTransfer { _, _ ->
                TransportResponse(
                    code = 200,
                    headers = mapOf("Content-Length" to "4", "ETag" to "unquoted-tag"),
                    body = ByteArrayInputStream(ByteArray(64) { 0x41 })
                )
            }
            var capturedEtag: String? = "sentinel"
            val failure = assertThrows(IOException::class.java) {
                overflowTransfer.execute(
                    TransferRequest(url = "https://example.com/file.bin", partialFile = partial, maxRetries = 0),
                    onMetadata = { capturedEtag = it.strongEtag }
                )
            }
            assertEquals("OVERFLOW_HTTP_BODY", failure.message)
            assertNull(capturedEtag)
            assertEquals(0L, partial.length())
        } finally {
            partial.delete()
        }
    }
}
