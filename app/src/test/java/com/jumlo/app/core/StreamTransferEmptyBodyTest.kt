package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files

class StreamTransferEmptyBodyTest {
    @Test fun successfulHttpStatusWithEmptyBodyIsNotAFile() {
        val directory=Files.createTempDirectory("jumlo-empty-download-").toFile()
        try {
            val destination=directory.resolve("partial")
            val transport=HttpTransport { _,_ ->
                TransportResponse(200, mapOf("content-length" to "0"), ByteArrayInputStream(byteArrayOf()))
            }
            val failure=runCatching {
                StreamTransfer(transport).execute(
                    TransferRequest("https://example.org/file.bin", destination, minFreeBytes=0, maxRetries=0)
                )
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertEquals("EMPTY_HTTP_BODY", failure?.message)
            assertTrue(!destination.exists() || destination.length()==0L)
        } finally {
            directory.deleteRecursively()
        }
    }
}
