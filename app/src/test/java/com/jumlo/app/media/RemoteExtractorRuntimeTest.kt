package com.jumlo.app.media

import com.jumlo.app.core.media.MediaMode
import com.jumlo.app.core.media.MediaChoice
import com.jumlo.app.core.media.MediaFormat
import com.jumlo.app.data.media.RemoteExtractorRuntime
import com.jumlo.app.data.media.executeCancellable
import com.jumlo.app.data.jobs.OperationFailure
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The app half of the extraction architecture: post the URL, read the formats, pull
 * the chosen stream to the device. These tests use a small in-process service so the
 * contract is verified without a network or an extractor.
 */
class RemoteExtractorRuntimeTest {
    private lateinit var server: ServerSocket
    private lateinit var thread: Thread
    private lateinit var baseUrl: String
    @Volatile private var extractBody = "{}"
    private val streamBytes = ByteArray(4096) { (it % 251).toByte() }.apply {
        byteArrayOf(0,0,0,24,102,116,121,112).copyInto(this)
    }
    @Volatile private var lastAuthorization: String? = null
    @Volatile private var streamName = "media.mp4"
    @Volatile private var streamDelayMillis = 0L
    @Volatile private var streamBodyDelayMillis = 0L
    @Volatile private var streamFailureStatus = 0
    @Volatile private var streamFailureBody = ""
    private val streamRequests = java.util.concurrent.atomic.AtomicInteger()
    private val streamStarted = CountDownLatch(1)

    @Before fun setup() {
        server = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        thread = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket -> serve(socket) }
                } catch (_: Exception) {
                    if (server.isClosed) return@Thread
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        baseUrl = "http://127.0.0.1:${server.localPort}"
    }

    @After fun teardown() {
        server.close()
    }

    /** A minimal HTTP/1.1 responder: enough for the extract, stream and health calls. */
    /** The path of the last /stream request, so a test can see what was asked for. */
    @Volatile private var lastStreamRequest: String? = null

    private fun serve(socket: Socket) {
        val input = socket.getInputStream()
        val request = StringBuilder()
        var line = readHeaderLine(input)
        while (line != null && line.isNotEmpty()) {
            request.append(line).append('\n')
            line = readHeaderLine(input)
        }
        val headers = request.toString().lowercase()
        val path = request.toString().substringBefore('\n').split(' ').getOrNull(1).orEmpty()
        val contentLength = Regex("content-length:\\s*(\\d+)").find(headers)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) input.readNBytes(contentLength) else ByteArray(0)
        val output = socket.getOutputStream()
        when {
            path.startsWith("/extract") -> {
                val url = Regex("\"url\"\\s*:\\s*\"([^\"]*)\"").find(String(body))?.groupValues?.get(1).orEmpty()
                val payload = if (url.contains("fail")) "{\"error\":\"MEDIA_PRIVATE_CONTENT\"}" else extractBody
                val bytes = payload.toByteArray()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(bytes)
            }
            path.startsWith("/stream") -> {
                lastStreamRequest = path
                streamRequests.incrementAndGet()
                streamStarted.countDown()
                if (streamDelayMillis > 0) Thread.sleep(streamDelayMillis)
                if (streamFailureStatus != 0) {
                    val failure = streamFailureBody.toByteArray()
                    output.write("HTTP/1.1 $streamFailureStatus Rejected\r\nContent-Type: application/json\r\nContent-Length: ${failure.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(failure)
                    output.flush()
                    return
                }
                lastAuthorization = Regex("authorization:\\s*([^\\n]+)").find(headers)?.groupValues?.get(1)?.trim()
                val start = Regex("range:\\s*bytes=(\\d+)-").find(headers)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val slice = streamBytes.copyOfRange(start, streamBytes.size)
                val status = if (start > 0) "206 Partial Content" else "200 OK"
                output.write("HTTP/1.1 $status\r\nContent-Type: video/mp4\r\nContent-Disposition: attachment; filename=\"$streamName\"\r\nAccept-Ranges: bytes\r\nContent-Length: ${slice.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.flush()
                if (streamBodyDelayMillis > 0) Thread.sleep(streamBodyDelayMillis)
                output.write(slice)
            }
            else -> {
                val bytes = "{\"ok\":true,\"ytdlp\":\"2026.08.19\"}".toByteArray()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(bytes)
            }
        }
        output.flush()
    }

    /** Reads one CRLF-terminated header line without buffering past it. */
    private fun readHeaderLine(input: java.io.InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
            if (byte == '\n'.code) break
            if (byte != '\r'.code) buffer.write(byte)
        }
        return buffer.toString("UTF-8")
    }

    private fun runtime(url: () -> String = { baseUrl }) = RemoteExtractorRuntime(
        baseUrlProvider = url,
        client = OkHttpClient(),
        allowDebugLoopback = true
    )

    @Test fun releaseClientRejectsCleartextBeforeSendingCredentials() {
        val error = runCatching {
            RemoteExtractorRuntime({ baseUrl }, OkHttpClient(), { "fixture-token" })
                .inspect("https://example.com/video", allowHttp = true)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("INVALID_MEDIA_SERVICE_URL", error?.message)
        assertNull(lastAuthorization)
        assertEquals(0, streamRequests.get())
    }

    @Test fun backendRequestUsesOneAtomicConfigurationSnapshot() {
        var reads = 0
        val dir = java.nio.file.Files.createTempDirectory("media-config-").toFile()
        try {
            val runtime = RemoteExtractorRuntime({ error("MIXED_CONFIG_READ") }, OkHttpClient(),
                { error("MIXED_TOKEN_READ") }, allowDebugLoopback = true,
                configurationProvider = { reads++; baseUrl to "bound-token" })
            runtime.download("https://example.com/video", fixtureChoice(),
                com.jumlo.app.core.media.AudioOutput.ORIGINAL, 192, true, dir, {}, {})
            assertEquals(1, reads)
            assertEquals("bearer bound-token", lastAuthorization)
            assertTrue(File(dir, "media.mp4").isFile)
        } finally { dir.deleteRecursively() }
    }

    @Test fun theFormatsTheServiceOffersBecomeTheAppsFormats() {
        extractBody = """
            {"id":"abc","extractor":"youtube","title":"A video","duration":120.5,
             "thumbnail":"https://example.com/t.jpg","uploader":"Someone",
             "formats":[
               {"id":"137","ext":"mp4","vcodec":"avc1","acodec":"none","height":1080,"fps":30,"vbr":4000,"filesize":123456,"protocol":"https"},
               {"id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a","abr":128,"protocol":"https"}
             ]}
        """.trimIndent()
        val asset = runtime().inspect("https://www.youtube.com/watch?v=abc", allowHttp = false)
        assertEquals("youtube", asset.extractor)
        assertEquals("A video", asset.title)
        assertEquals(2, asset.formats.size)
        val video = asset.formats.first { it.hasVideo }
        assertEquals("137", video.id)
        assertEquals(1080, video.height)
        assertEquals(123456L, video.bytes)
        val audio = asset.formats.first { it.hasAudio }
        assertEquals("140", audio.id)
        assertEquals(128L, audio.audioBitrateKbps?.toLong())
    }

    @Test fun aServiceErrorIsReportedWithTheAppsOwnVocabulary() {
        extractBody = "{\"error\":\"MEDIA_PRIVATE_CONTENT\"}"
        val failure = runCatching { runtime().inspect("https://example.com/fail", allowHttp = false) }
            .exceptionOrNull()
        assertTrue("the failure must surface", failure is OperationFailure)
        assertEquals("MEDIA_PRIVATE_CONTENT", (failure as OperationFailure).message)
    }

    @Test fun malformedDurationNeverReachesNumericUiFormatting() {
        extractBody="""{"id":"fixture","extractor":"fixture","title":"Fixture","duration":"NaN","formats":[{"id":"18","ext":"mp4","vcodec":"h264","acodec":"aac"}]}"""
        assertNull(runtime().inspect("https://example.org/watch",false).durationSeconds)
    }

    @Test fun aRejectedStreamKeepsThePrivateContentErrorWithoutRepeatedRequests() {
        streamFailureStatus = 422
        streamFailureBody = """{"error":"MEDIA_PRIVATE_CONTENT"}"""
        assertRejectedStream("MEDIA_PRIVATE_CONTENT")
        assertEquals(1, streamRequests.get())
    }

    @Test fun aRateLimitedStreamHasAnActionableErrorEvenWithoutAJsonBody() {
        streamFailureStatus = 429
        streamFailureBody = "<html>Too many requests</html>"
        assertRejectedStream("MEDIA_RATE_LIMITED")
        assertEquals(1, streamRequests.get())
    }

    @Test fun arbitraryServiceErrorTextNeverBecomesThePublicErrorCode() {
        streamFailureStatus = 422
        streamFailureBody = """{"error":"https://example.org/private?token=secret"}"""
        assertRejectedStream("MEDIA_EXTRACTION_FAILED")
    }

    @Test fun anOversizedRejectedBodyIsBoundedWithoutRepeatingTheRequest() {
        streamFailureStatus = 422
        streamFailureBody = "x".repeat(128 * 1024)
        assertRejectedStream("MEDIA_OUTPUT_LIMIT")
        assertEquals(1, streamRequests.get())
    }

    private fun assertRejectedStream(expected: String) {
        val dir = java.nio.file.Files.createTempDirectory("media-rejected-").toFile()
        try {
            val failure = runCatching {
                runtime().download("https://example.org/video", fixtureChoice(),
                    com.jumlo.app.core.media.AudioOutput.ORIGINAL, 192, true, dir, {}, {})
            }.exceptionOrNull()
            assertTrue("service rejection must be mapped, got $failure", failure is OperationFailure)
            assertEquals(expected, failure?.message)
            assertTrue("a rejected response must not create a playable output", !File(dir, "media.mp4").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun anUnconfiguredServiceIsReportedInsteadOfBeingIgnored() {
        val failure = runCatching { runtime { "" }.inspect("https://example.com/x", allowHttp = false) }
            .exceptionOrNull()
        assertTrue("the failure must surface", failure is OperationFailure)
        assertEquals("MEDIA_SERVICE_NOT_CONFIGURED", (failure as OperationFailure).message)
    }

    @Test fun theChosenStreamIsDownloadedToTheDevice() {
        val dir = File(System.getProperty("java.io.tmpdir"), "media-${System.nanoTime()}").apply { mkdirs() }
        try {
            val format = MediaFormat(id = "137", extension = "mp4", videoCodec = "avc1", audioCodec = "none", height = 720)
            val choice = MediaChoice(key = "video:137", mode = MediaMode.VIDEO, formats = listOf(format),
                container = "mp4", requiresMux = false, expectedHeight = 720, estimatedBytes = streamBytes.size.toLong(),
                sizeIsApproximate = false)
            val downloaded = java.util.concurrent.atomic.AtomicLong(0L)
            runtime().download("https://example.com/video", choice, com.jumlo.app.core.media.AudioOutput.ORIGINAL,
                0, true, dir, {}, { progress -> downloaded.set(maxOf(downloaded.get(), progress.downloadedBytes)) })
            val file = File(dir, "media.mp4")
            assertTrue("the file must exist", file.exists())
            assertEquals("every byte must arrive", streamBytes.size.toLong(), file.length())
            assertEquals(streamBytes.size.toLong(), downloaded.get())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun theExtractorVersionComesFromTheService() {
        assertEquals("2026.08.19", runtime().refreshExtractor())
    }

    @Test fun aVideoAndAnAudioTrackAreAskedForTogether() {
        val dir = File(System.getProperty("java.io.tmpdir"), "media-mux-${System.nanoTime()}").apply { mkdirs() }
        try {
            val video = MediaFormat(id = "137", extension = "mp4", videoCodec = "avc1", audioCodec = "none", height = 720)
            val audio = MediaFormat(id = "140", extension = "m4a", videoCodec = "none", audioCodec = "mp4a")
            val choice = MediaChoice(key = "video:137+140", mode = MediaMode.VIDEO,
                formats = listOf(video, audio), container = "mp4", requiresMux = true,
                expectedHeight = 720, estimatedBytes = streamBytes.size.toLong(), sizeIsApproximate = false)
            runtime().download("https://example.com/video", choice, com.jumlo.app.core.media.AudioOutput.ORIGINAL,
                0, true, dir, {}, { })
            val requested = lastStreamRequest.orEmpty()
            assertTrue("the stream must have been requested", requested.isNotEmpty())
            assertTrue("both tracks must be asked for, but the request was $requested",
                requested.contains("format=137%2B140") || requested.contains("format=137+140"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun aServiceThatIsNotThereIsReportedAsUnavailable() {
        val failure = runCatching { runtime { "http://127.0.0.1:1" }.refreshExtractor() }.getOrNull()
        assertEquals("MEDIA_RUNTIME_UNAVAILABLE", failure)
    }

    @Test fun serviceTokenIsSentForTheDownloadToo() {
        val dir = java.nio.file.Files.createTempDirectory("media-auth-").toFile()
        try {
            val choice = com.jumlo.app.core.media.MediaCatalog.choices(
                com.jumlo.app.core.media.MediaAsset("id","fixture","Fixture",1.0,
                    listOf(MediaFormat("18","mp4","h264","aac"))),MediaMode.VIDEO).single()
            RemoteExtractorRuntime({baseUrl},OkHttpClient(),{"test-token"}, allowDebugLoopback = true)
                .download("https://example.com/video",choice,com.jumlo.app.core.media.AudioOutput.ORIGINAL,192,true,dir,{},{})
            assertEquals("bearer test-token",lastAuthorization)
        } finally { dir.deleteRecursively() }
    }

    @Test fun originalAudioKeepsTheSelectedSourceAndReturnedExtension() {
        val dir = java.nio.file.Files.createTempDirectory("media-original-").toFile()
        try {
            streamName="media.m4a"
            val choice = com.jumlo.app.core.media.MediaCatalog.choices(
                com.jumlo.app.core.media.MediaAsset("id","fixture","Fixture",1.0,
                    listOf(MediaFormat("140","m4a","none","aac"))),MediaMode.AUDIO).single()
            runtime().download("https://example.com/video",choice,com.jumlo.app.core.media.AudioOutput.ORIGINAL,192,true,dir,{},{})
            val query=lastStreamRequest.orEmpty()
            assertTrue(query,query.contains("selector=140"))
            assertTrue(query,query.contains("format=best"))
            assertTrue(File(dir,"media.m4a").isFile)
        } finally { dir.deleteRecursively() }
    }

    @Test fun preparationCanOutlastTheOrdinaryReadTimeout() {
        val dir = java.nio.file.Files.createTempDirectory("media-preparation-").toFile()
        streamDelayMillis = 200
        try {
            val client = OkHttpClient.Builder().readTimeout(25, TimeUnit.MILLISECONDS).build()
            RemoteExtractorRuntime({ baseUrl }, client, allowDebugLoopback = true).download("https://example.com/video",
                fixtureChoice(), com.jumlo.app.core.media.AudioOutput.ORIGINAL, 192, true, dir, {}, {})
            assertEquals(streamBytes.size.toLong(), File(dir, "media.mp4").length())
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellationClosesTheCallWhileTheServiceIsPreparingHeaders() {
        val dir = java.nio.file.Files.createTempDirectory("media-cancel-").toFile()
        val cancelled = AtomicBoolean(false)
        val callClosed = CountDownLatch(1)
        streamDelayMillis = 3000
        val client = OkHttpClient.Builder().eventListener(object : okhttp3.EventListener() {
            override fun callFailed(call: okhttp3.Call, ioe: java.io.IOException) {
                if (call.isCanceled()) callClosed.countDown()
            }
        }).build()
        val work = java.util.concurrent.FutureTask {
            RemoteExtractorRuntime({ baseUrl }, client, allowDebugLoopback = true).download("https://example.com/video",
                fixtureChoice(), com.jumlo.app.core.media.AudioOutput.ORIGINAL, 192, true, dir,
                { if (cancelled.get()) throw java.util.concurrent.CancellationException("fixture cancelled") }, {})
        }
        val worker = Thread(work, "remote-cancellation-fixture").apply { isDaemon = true; start() }
        try {
            assertTrue("request did not reach fixture", streamStarted.await(2, TimeUnit.SECONDS))
            cancelled.set(true)
            val error = runCatching { work.get(1, TimeUnit.SECONDS) }.exceptionOrNull()
            assertTrue("cancellation must interrupt header preparation: $error",
                error is java.util.concurrent.ExecutionException && error.cause is java.util.concurrent.CancellationException)
            assertTrue("the outstanding HTTP call must be cancelled", callClosed.await(1, TimeUnit.SECONDS))
            assertTrue("cancelled preparation must not publish a file", !File(dir, "media.mp4").exists())
        } finally {
            work.cancel(true)
            worker.join(100)
            dir.deleteRecursively()
        }
    }

    @Test fun headerPreparationHasABoundedDeadline() {
        streamDelayMillis = 1000
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val error = runCatching {
            client.executeCancellable(okhttp3.Request.Builder().url("$baseUrl/stream").build(), {}, 80)
                .use { }
        }.exceptionOrNull()
        assertTrue("an idle service must not wait indefinitely: $error", error is java.net.SocketTimeoutException)
        assertEquals("MEDIA_SERVICE_PREPARATION_TIMEOUT", error?.message)
    }

    @Test fun cancellationClosesAStalledBodyAfterHeadersHaveArrived() {
        val cancelled=AtomicBoolean(false)
        val reading=CountDownLatch(1)
        streamBodyDelayMillis=3000
        val client=OkHttpClient.Builder().readTimeout(10,TimeUnit.SECONDS).build()
        val work=java.util.concurrent.FutureTask {
            client.executeCancellable(okhttp3.Request.Builder().url("$baseUrl/stream").build(),
                { if(cancelled.get()) throw java.util.concurrent.CancellationException("fixture body cancelled") }).use { response ->
                reading.countDown()
                response.body!!.byteStream().read()
            }
        }
        val worker=Thread(work,"body-cancellation-fixture").apply { isDaemon=true;start() }
        try {
            assertTrue("response headers did not arrive",reading.await(2,TimeUnit.SECONDS))
            cancelled.set(true)
            val error=runCatching { work.get(1,TimeUnit.SECONDS) }.exceptionOrNull()
            assertTrue("stalled reads must preserve cancellation immediately: $error",
                error is java.util.concurrent.ExecutionException && error.cause is java.util.concurrent.CancellationException)
        } finally {
            work.cancel(true)
            worker.join(100)
        }
    }

    @Test fun aStalledBodyStillUsesTheOrdinaryIdleTimeout() {
        val dir = java.nio.file.Files.createTempDirectory("media-body-timeout-").toFile()
        streamBodyDelayMillis = 200
        try {
            val client = OkHttpClient.Builder().readTimeout(25, TimeUnit.MILLISECONDS).build()
            val error = runCatching {
                RemoteExtractorRuntime({ baseUrl }, client, allowDebugLoopback = true).download("https://example.com/video",
                    fixtureChoice(), com.jumlo.app.core.media.AudioOutput.ORIGINAL, 192, true, dir, {}, {})
            }.exceptionOrNull()
            assertTrue("stalled response bytes must retain an idle timeout: $error", error is java.net.SocketTimeoutException)
            assertTrue(!File(dir, "media.mp4").exists())
        } finally { dir.deleteRecursively() }
    }

    private fun fixtureChoice() = com.jumlo.app.core.media.MediaCatalog.choices(
        com.jumlo.app.core.media.MediaAsset("id", "fixture", "Fixture", 1.0,
            listOf(MediaFormat("18", "mp4", "h264", "aac"))), MediaMode.VIDEO).single()
}
