package com.jumlo.app.media

import com.jumlo.app.core.StreamTransfer
import com.jumlo.app.core.media.AudioOutput
import com.jumlo.app.core.media.MediaChoice
import com.jumlo.app.core.media.MediaFormat
import com.jumlo.app.core.media.MediaMode
import com.jumlo.app.data.media.RemoteExtractorRuntime
import com.jumlo.app.data.processing.OkHttpTransport
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The full loop against a real extraction service: the app posts a URL, reads the
 * formats the extractor offers, and pulls the chosen stream to the device.
 *
 * The test is skipped unless a service address is provided, so a normal run never
 * needs the network. To run it:
 *
 *     python3 tools/media-service/service.py --port 8731 &
 *     JUMLO_MEDIA_SERVICE=http://127.0.0.1:8731 ./gradlew :app:testDebugUnitTest \
 *         --tests com.jumlo.app.media.RemoteExtractorServiceLiveTest
 */
class RemoteExtractorServiceLiveTest {
    private val serviceUrl: String? = System.getenv("JUMLO_MEDIA_SERVICE")

    private fun runtime() = RemoteExtractorRuntime(
        baseUrlProvider = { serviceUrl.orEmpty() },
        client = OkHttpClient(),
        transfer = StreamTransfer(OkHttpTransport(OkHttpClient()))
    )

    @Test fun aDirectMediaLinkIsDownloadedToTheDevice() {
        assumeTrue("set JUMLO_MEDIA_SERVICE to run this", !serviceUrl.isNullOrBlank())
        val url = "https://download.samplelib.com/mp4/sample-5s.mp4"
        val asset = runtime().inspect(url, allowHttp = false)
        assertTrue("the service must offer a format", asset.formats.isNotEmpty())
        val format = asset.formats.first()
        val dir = File(System.getProperty("java.io.tmpdir"), "live-${System.nanoTime()}").apply { mkdirs() }
        try {
            val choice = MediaChoice(key = "video:${format.id}", mode = MediaMode.VIDEO, formats = listOf(format),
                container = format.extension, requiresMux = false, expectedHeight = format.height,
                estimatedBytes = format.bytes, sizeIsApproximate = format.bytes == null)
            // A loopback service is plain http; the cleartext consent flag covers it.
            runtime().download(url, choice, AudioOutput.ORIGINAL, 0, true, dir, {}, {})
            val file = File(dir, "media.${format.extension}")
            assertTrue("the file must exist", file.exists())
            assertTrue("the file must have content", file.length() > 0L)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun theServiceReportsItsExtractorVersion() {
        assumeTrue("set JUMLO_MEDIA_SERVICE to run this", !serviceUrl.isNullOrBlank())
        val version = runtime().refreshExtractor()
        assertTrue("the service must report a version: $version", version != "MEDIA_RUNTIME_UNAVAILABLE")
    }
}
