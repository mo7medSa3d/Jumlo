package com.jumlo.app.media

import com.jumlo.app.core.StreamTransfer
import com.jumlo.app.core.media.MediaMode
import com.jumlo.app.core.media.MediaChoice
import com.jumlo.app.core.media.MediaFormat
import com.jumlo.app.data.media.RemoteExtractorRuntime
import com.jumlo.app.data.processing.OkHttpTransport
import com.jumlo.app.data.jobs.OperationFailure
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket

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
    private val streamBytes = ByteArray(4096) { (it % 251).toByte() }

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
                val start = Regex("range:\\s*bytes=(\\d+)-").find(headers)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val slice = streamBytes.copyOfRange(start, streamBytes.size)
                val status = if (start > 0) "206 Partial Content" else "200 OK"
                output.write("HTTP/1.1 $status\r\nContent-Type: video/mp4\r\nAccept-Ranges: bytes\r\nContent-Length: ${slice.size}\r\nConnection: close\r\n\r\n".toByteArray())
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
        transfer = StreamTransfer(OkHttpTransport(OkHttpClient()))
    )

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
}
