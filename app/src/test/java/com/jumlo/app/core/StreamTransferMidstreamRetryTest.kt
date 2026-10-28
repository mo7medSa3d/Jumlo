package com.jumlo.app.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPOutputStream

class StreamTransferMidstreamRetryTest {
    private fun partial():File = File(Files.createTempDirectory("jumlo-resume").toFile(), "download.part")
    private fun brokenStream(prefix:String):InputStream =
        object: ByteArrayInputStream(prefix.toByteArray(Charsets.UTF_8)) {
            override fun read(b:ByteArray,off:Int,len:Int):Int {
                if(available()==0) throw IOException("simulated connection reset")
                return super.read(b,off,len)
            }
            override fun read():Int {
                if(available()==0) throw IOException("simulated connection reset")
                return super.read()
            }
        }

    @Test fun lostConnectionAfterBytesWrittenResumesFromVerifiedOffset() {
        val file=partial()
        var calls=0
        val transport=HttpTransport { _, headers ->
            calls++
            if(calls==1) {
                assertTrue("A fresh download must not send Range", "Range" !in headers)
                TransportResponse(200,mapOf("etag" to "\"v1\"","content-length" to "11"),brokenStream("hello"))
            } else {
                assertEquals("bytes=5-",headers["Range"])
                assertEquals("\"v1\"",headers["If-Range"])
                TransportResponse(206,mapOf("etag" to "\"v1\"","content-range" to "bytes 5-10/11",
                    "content-length" to "6")," world".byteInputStream())
            }
        }
        val result=StreamTransfer(transport).execute(TransferRequest("https://example.org/download",file,
            minFreeBytes=0,retryBackoffMs=0,maxRetries=2))
        assertEquals(2,calls)
        assertEquals(11L,result.bytes)
        assertEquals("hello world",file.readText())
    }

    @Test fun truncatedBodyResumesWhenServerSuppliesAnEtag() {
        val file=partial()
        var calls=0
        val transport=HttpTransport { _, headers ->
            calls++
            if(calls==1) TransportResponse(200,mapOf("etag" to "\"v1\"",
                "content-length" to "11"),"hello".byteInputStream())
            else {
                assertEquals("bytes=5-",headers["Range"])
                TransportResponse(206,mapOf("etag" to "\"v1\"",
                    "content-range" to "bytes 5-10/11","content-length" to "6")," world".byteInputStream())
            }
        }
        val result=StreamTransfer(transport).execute(TransferRequest("https://example.org/download",file,
            minFreeBytes=0,retryBackoffMs=0,maxRetries=2))
        assertEquals(2,calls)
        assertEquals(11L,result.bytes)
        assertEquals("hello world",file.readText())
    }

    @Test fun noValidatorNeverAppendsAfterMidstreamFailure() {
        val file=partial()
        var calls=0
        val transport=HttpTransport { _, _ ->
            calls++
            TransportResponse(200,mapOf("etag" to "W/\"weak\"",
                "content-length" to "11"),brokenStream("hello"))
        }
        val failure=runCatching {
            StreamTransfer(transport).execute(TransferRequest("https://example.org/download",file,
                minFreeBytes=0,retryBackoffMs=0,maxRetries=3))
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(1,calls)
        assertEquals("hello",file.readText())
    }

    @Test fun compressedRepresentationsNeverResumeUsingDecodedByteOffsets() {
        val file=partial()
        val original="repeated gzip fixture ".repeat(3000).toByteArray()
        val buffer=ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { it.write(original) }
        // Remove the gzip trailer, causing a failure after some decoded bytes
        // have been copied to disk. The correct response is NOT a ranged retry.
        val truncated=buffer.toByteArray().dropLast(8).toByteArray()
        var calls=0
        var safeForResume:Boolean?=null
        val transport=HttpTransport { _, headers ->
            calls++
            assertFalse("Never send a Range for decoded bytes", headers.containsKey("Range"))
            TransportResponse(200,mapOf("etag" to "\"gzip-v1\"",
                "content-encoding" to "gzip"),ByteArrayInputStream(truncated))
        }
        val failure=runCatching {
            StreamTransfer(transport).execute(
                TransferRequest("https://example.org/compressed",file,
                    minFreeBytes=0,retryBackoffMs=0,maxRetries=3),
                onMetadata={ safeForResume=it.resumeSafe })
        }.exceptionOrNull()
        assertTrue("Incomplete gzip must fail, not publish", failure is IOException)
        assertEquals("Do not issue a second GET or unsafe ranged append",1,calls)
        assertEquals(false,safeForResume)
        assertTrue("Fixture must have actually produced partial decoded data", file.length()>0L)
    }
}
