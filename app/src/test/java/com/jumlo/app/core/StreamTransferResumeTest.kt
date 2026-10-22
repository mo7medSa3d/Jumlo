package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

/** Resume behaviour against a scripted HTTP transport. */
class StreamTransferResumeTest {

    private class ScriptedTransport(private val responses: List<Triple<Int, Map<String, String>, ByteArray>>) : HttpTransport {
        var calls = 0
        override fun open(url: String, headers: Map<String, String>): TransportResponse {
            val index = calls.coerceAtMost(responses.lastIndex)
            calls++
            val (code, responseHeaders, body) = responses[index]
            return TransportResponse(code, responseHeaders, body.inputStream())
        }
    }

    private fun body(text: String) = text.toByteArray()

    @Test fun a_server_that_ignores_the_range_header_restarts_the_transfer_instead_of_failing() {
        val dir = createTempDir()
        val partial = File(dir, "part.bin")
        partial.writeBytes("partial".toByteArray())
        // The server answers a ranged request with the whole representation (200, no Content-Range).
        val transport = ScriptedTransport(listOf(Triple(200, mapOf("content-type" to "application/octet-stream"), body("complete-payload"))))
        val request = TransferRequest(
            url = "https://example.org/file.bin",
            partialFile = partial,
            offset = partial.length(),
            strongEtag = "\"v1\"",
            knownTotalBytes = 15L
        )
        val result = StreamTransfer(transport).execute(request)
        assertEquals("complete-payload".length.toLong(), result.bytes)
        assertEquals("complete-payload", partial.readText())
    }

    @Test fun a_complete_transfer_is_recognised_without_another_request() {
        assertTrue(DownloadRestartPolicy.isTransferComplete(15L, 15L))
    }

    @Test fun a_resumed_response_must_cover_the_representation_total() {
        var failed = false
        try {
            ResumePolicy.validate(
                offset = 5L,
                etag = "\"v1\"",
                modified = null,
                total = 10L,
                status = 206,
                headers = mapOf("content-range" to "bytes 5-7/*", "etag" to "\"v1\"")
            )
        } catch (error: RestartRequired) {
            failed = true
        }
        assertTrue("a slice without a total must restart", failed)
    }

    @Test fun a_strong_validator_is_required_to_resume() {
        var failed = false
        try {
            ResumePolicy.validate(offset = 5L, etag = null, modified = null, total = 10L, status = 206, headers = mapOf("content-range" to "bytes 5-9/10"))
        } catch (error: RestartRequired) {
            failed = true
        }
        assertTrue("a resume without a validator must restart", failed)
    }

    private fun createTempDir(): File = java.nio.file.Files.createTempDirectory("transfer-test").toFile().also { it.deleteOnExit() }
}
