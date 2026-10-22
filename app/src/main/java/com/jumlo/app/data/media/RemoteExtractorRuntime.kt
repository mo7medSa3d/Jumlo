package com.jumlo.app.data.media

import com.jumlo.app.core.StreamTransfer
import com.jumlo.app.core.TransferRequest
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.HttpTransport
import com.jumlo.app.core.TransportResponse
import com.jumlo.app.core.media.AudioOutput
import com.jumlo.app.core.media.MediaAsset
import com.jumlo.app.core.media.MediaChoice
import com.jumlo.app.core.media.MediaFormat
import com.jumlo.app.core.media.MediaMode
import com.jumlo.app.core.media.MediaProgress
import com.jumlo.app.core.media.MediaCatalog
import com.jumlo.app.core.media.MediaJsonGuard
import com.jumlo.app.core.media.MediaOutputPolicy
import com.jumlo.app.data.jobs.OperationFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The on-device half of the backend extraction architecture.
 *
 * The service owns the extractor; the app owns the download. The app posts a public
 * URL, receives the formats the extractor can offer, and then pulls the chosen stream
 * from the service with the ordinary transfer machinery - so progress, pause, resume,
 * cancel and retry keep working and the bytes land on the device.
 *
 * The service never sees anything the user did not paste or share, and the app
 * never asks it to decrypt anything: if the upstream site needs a session, the
 * service says so and the app shows a plain message.
 */
internal class RemoteExtractorRuntime(
    private val baseUrlProvider: () -> String,
    client: OkHttpClient,
    private val tokenProvider: () -> String = { "" }
) : MediaRuntime {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES).build()
    // The service prepares a complete selected/muxed file before response headers.
    // Header waiting has its own bounded deadline and cancellation; body reads keep
    // the ordinary idle timeout instead of allowing an hour-long stalled transfer.
    private val preparationClient = this.client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS).callTimeout(0, TimeUnit.MILLISECONDS).build()
    private val bodyTimeoutMillis = this.client.readTimeoutMillis.takeIf { it > 0 } ?: 30_000
    private val baseUrl: String get() = baseUrlProvider()
    private val token: String get() = tokenProvider()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun serviceUrl(path: String, query: Map<String, String> = emptyMap()): String {
        val base = baseUrl.trimEnd('/')
        val encoded = query.entries.joinToString("&") { (key, value) ->
            "${key}=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }
        return if (encoded.isEmpty()) "$base$path" else "$base$path?$encoded"
    }

    private fun request(url: String): Request = Request.Builder().url(url).apply {
        if (token.isNotBlank()) header("Authorization", "Bearer $token")
    }.build()

    override fun inspect(url: String, allowHttp: Boolean, check: () -> Unit): MediaAsset {
        check()
        if (baseUrl.isBlank()) throw OperationFailure("MEDIA_SERVICE_NOT_CONFIGURED")
        val body = json.encodeToString(ExtractRequest.serializer(), ExtractRequest(url))
        val httpRequest = Request.Builder().url(serviceUrl("/extract")).apply {
            if (token.isNotBlank()) header("Authorization", "Bearer $token")
        }.post(body.toRequestBody("application/json".toMediaType())).build()
        val payload = try {
            preparationClient.executeCancellable(httpRequest, check, 130_000L).use { response ->
                response.body?.source()?.timeout()?.timeout(bodyTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                val text = boundedBody(response.body?.byteStream(), check)
                if (!response.isSuccessful) {
                    // The service reports the failure in the app's own vocabulary.
                    throw OperationFailure(parseError(text) ?: "MEDIA_EXTRACTION_FAILED")
                }
                text
            }
        } catch (cancel: java.util.concurrent.CancellationException) {
            throw cancel
        } catch (failure: OperationFailure) {
            throw failure
        } catch (_: Exception) {
            throw OperationFailure("MEDIA_NETWORK_ERROR")
        }
        // A service may answer 200 with an error body; that is still a failure.
        parseError(payload)?.let { throw OperationFailure(it) }
        check()
        return parseAsset(payload)
    }

    private fun boundedBody(input: java.io.InputStream?, check: () -> Unit): String {
        if (input == null) return ""
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        input.use {
            while (true) {
                check()
                val count = it.read(buffer)
                if (count < 0) break
                if (output.size() + count > 4 * 1024 * 1024) throw OperationFailure("MEDIA_OUTPUT_LIMIT")
                output.write(buffer, 0, count)
            }
        }
        return output.toString("UTF-8")
    }

    private fun parseError(text: String): String? = runCatching {
        val element = json.parseToJsonElement(text)
        (element as? JsonObject)?.get("error")?.jsonPrimitive?.content
            ?.takeIf { Regex("[A-Z][A-Z_0-9]{2,80}").matches(it) }
    }.getOrNull()

    private fun parseAsset(payload: String): MediaAsset {
        MediaJsonGuard.check(payload)
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }
            .getOrElse { throw OperationFailure("MEDIA_EXTRACTION_FAILED") }
        val formats = (root["formats"] as? JsonArray).orEmpty().mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val id = entry["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            MediaFormat(
                id = id,
                extension = entry["ext"]?.jsonPrimitive?.content ?: "mp4",
                videoCodec = entry["vcodec"]?.jsonPrimitive?.content ?: "none",
                audioCodec = entry["acodec"]?.jsonPrimitive?.content ?: "none",
                width = entry["width"]?.jsonPrimitive?.content?.toIntOrNull(),
                height = entry["height"]?.jsonPrimitive?.content?.toIntOrNull(),
                fps = entry["fps"]?.jsonPrimitive?.doubleOrNull,
                bitrateKbps = entry["vbr"]?.jsonPrimitive?.doubleOrNull,
                audioBitrateKbps = entry["abr"]?.jsonPrimitive?.doubleOrNull,
                bytes = entry["filesize"]?.jsonPrimitive?.longOrNull,
                approximateBytes = entry["filesize_approx"]?.jsonPrimitive?.longOrNull,
                protocol = entry["protocol"]?.jsonPrimitive?.content ?: "https",
                language = entry["language"]?.jsonPrimitive?.content,
                drm = entry["drm"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        }
        if (formats.isEmpty()) throw OperationFailure("MEDIA_NO_USABLE_FORMAT")
        return MediaAsset(
            id = root["id"]?.jsonPrimitive?.content ?: urlFallback(),
            extractor = root["extractor"]?.jsonPrimitive?.content ?: "remote",
            title = root["title"]?.jsonPrimitive?.content ?: "media",
            durationSeconds = root["duration"]?.jsonPrimitive?.doubleOrNull,
            formats = formats,
            isLive = root["isLive"]?.jsonPrimitive?.booleanOrNull ?: false,
            isPlaylist = root["isPlaylist"]?.jsonPrimitive?.booleanOrNull ?: false,
            uploader = root["uploader"]?.jsonPrimitive?.content,
            thumbnailUrl = root["thumbnail"]?.jsonPrimitive?.content
        )
    }

    private fun urlFallback(): String = "remote"

    override fun download(
        url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
        allowHttp: Boolean, directory: File, check: () -> Unit,
        progress: (MediaProgress) -> Unit
    ) {
        check()
        if (baseUrl.isBlank()) throw OperationFailure("MEDIA_SERVICE_NOT_CONFIGURED")
        MediaCatalog.validateSelector(choice)
        val mode = if (choice.mode == MediaMode.AUDIO) "audio" else "video"
        val format = when (mode) {
            "audio" -> when (audio) {
                AudioOutput.MP3 -> "mp3"
                AudioOutput.M4A -> "m4a"
                AudioOutput.OPUS -> "opus"
                AudioOutput.ORIGINAL -> "best"
            }
            else -> {
                val video = choice.formats.firstOrNull { it.hasVideo }
                val audio = choice.formats.firstOrNull { it.hasAudio && !it.hasVideo }
                // A source that keeps the picture and the sound apart has to be
                // asked for both, or the file arrives with a missing track. The
                // service hands the selector to the extractor, which merges them.
                if (video != null && audio != null) "${video.id}+${audio.id}"
                else video?.id ?: choice.formats.first().id
            }
        }
        val streamUrl = serviceUrl("/stream", mapOf(
            "url" to url, "mode" to mode, "format" to format, "bitrate" to bitrate.toString(),
            "selector" to choice.formatIds.joinToString("+"),
            "container" to if (choice.requiresMux) choice.container else ""
        ))
        val endpoint = streamUrl.toHttpUrl()
        val serviceToken = token
        val transfer = StreamTransfer(HttpTransport { targetUrl, headers ->
            val target = targetUrl.toHttpUrl()
            val request = Request.Builder().url(target).get().apply {
                headers.forEach { (key, value) -> header(key, value) }
                if (serviceToken.isNotBlank() && target.scheme == endpoint.scheme &&
                    target.host == endpoint.host && target.port == endpoint.port)
                    header("Authorization", "Bearer $serviceToken")
            }.build()
            val response = preparationClient.executeCancellable(request, check, 61 * 60_000L)
            val body = response.body
            if (body == null) { response.close(); throw java.io.IOException("EMPTY_HTTP_BODY") }
            body.source().timeout().timeout(bodyTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            TransportResponse(response.code, response.headers.names().associate {
                it.lowercase(Locale.ROOT) to response.header(it).orEmpty()
            }, body.byteStream()) { response.close() }
        })
        val partial = File(directory, "remote-media.part")
        val journal = File(directory, "remote-media.properties")
        val old = Properties().apply { if (journal.isFile) journal.inputStream().use(::load) }
        val digest = Hashing.sha256(ByteArrayInputStream(streamUrl.toByteArray(Charsets.UTF_8)))
        if (partial.exists() && (old.getProperty("urlDigest") != digest ||
            old.getProperty("etag").isNullOrBlank())) {
            require(partial.delete()) { "MEDIA_WORKSPACE_LIMIT" }
            journal.delete()
        }
        val request = TransferRequest(
            url = streamUrl,
            partialFile = partial, offset = partial.length(),
            strongEtag = old.getProperty("etag"),
            knownTotalBytes = old.getProperty("total")?.toLongOrNull(),
            finalUrlSha256 = old.getProperty("finalUrlSha256"),
            // A service on the local network is plain http; the user's consent covers it.
            cleartextConsent = allowHttp,
            maxBytes = 8L * 1024 * 1024 * 1024
        )
        val result = transfer.execute(
            request = request,
            checkCancel = check,
            progress = { downloaded, total ->
                progress(MediaProgress(streamId = choice.key, downloadedBytes = downloaded,
                    totalBytes = total, estimatedBytes = choice.estimatedBytes,
                    bytesPerSecond = null, etaSeconds = null))
            },
            onMetadata = { metadata ->
                val state = Properties().apply {
                    setProperty("urlDigest", digest)
                    setProperty("finalUrlSha256", metadata.finalUrlSha256)
                    metadata.strongEtag?.let { setProperty("etag", it) }
                    metadata.totalBytes?.let { setProperty("total", it.toString()) }
                }
                val pending = File(directory, "remote-media.pending")
                FileOutputStream(pending).use { state.store(it, null); it.fd.sync() }
                Files.move(pending.toPath(), journal.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        )
        if (result.bytes <= 0L) throw OperationFailure("MEDIA_DOWNLOAD_EMPTY")
        check()
        val reported = result.contentDisposition?.let {
            Regex("filename\\s*=\\s*\"?media\\.([a-z0-9]+)(?:\"|;|$)", RegexOption.IGNORE_CASE)
                .find(it)?.groupValues?.get(1)?.lowercase()
        }
        val extension = reported ?: if (mode == "audio" && format == "best")
            throw OperationFailure("MEDIA_OUTPUT_MISSING") else if (mode == "audio") format else choice.container
        val published = File(directory, MediaOutputPolicy.displayName("media", extension))
        if (!partial.exists()) throw OperationFailure("MEDIA_OUTPUT_MISSING")
        Files.move(partial.toPath(), published.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        journal.delete()
        try { MediaOutputPolicy.inspect(published) }
        catch (error: Exception) { published.delete(); throw error }
        check()
    }

    /** The service can update its own extractor; the app only has to ask. */
    override fun refreshExtractor(): String {
        val payload = try {
            client.newCall(request(serviceUrl("/health"))).execute().use { response ->
                if (!response.isSuccessful) return "MEDIA_RUNTIME_UNAVAILABLE"
                response.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return "MEDIA_RUNTIME_UNAVAILABLE"
        }
        val version = runCatching {
            json.parseToJsonElement(payload).jsonObject["ytdlp"]?.jsonPrimitive?.content
        }.getOrNull()
        return version?.takeIf { it.isNotBlank() } ?: "MEDIA_RUNTIME_UNAVAILABLE"
    }
}

@kotlinx.serialization.Serializable
private data class ExtractRequest(val url: String)

/**
 * Tries each engine in order and keeps using the one that answered.
 *
 * This lives in the main source set because the standard and the extended-media builds
 * are mutually exclusive source sets, and both need the same routing.
 *
 * A failure that means "this site is not supported here" may fall through to the
 * next engine. Every other failure - a private video, a rate limit, a network
 * error - stays failed: retrying it elsewhere would be trying to get around an
 * access control.
 */
internal class RoutingMediaRuntime(private val engines: List<MediaRuntime>) : MediaRuntime {
    private val answered = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, MediaRuntime>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaRuntime>?): Boolean = size > 128
        })

    override fun inspect(url: String, allowHttp: Boolean, check: () -> Unit): MediaAsset {
        var last: OperationFailure? = null
        for (engine in engines) {
            check()
            try {
                val asset = engine.inspect(url, allowHttp, check)
                answered[url] = engine
                return asset
            } catch (cancel: java.util.concurrent.CancellationException) {
                throw cancel
            } catch (failure: OperationFailure) {
                val code = failure.message.orEmpty()
                if (code != "MEDIA_SITE_UNSUPPORTED" && code != "MEDIA_RUNTIME_UNAVAILABLE" &&
                    code != "MEDIA_SERVICE_NOT_CONFIGURED" && code != "MEDIA_BUILD_NOT_ENABLED") throw failure
                last = failure
            }
        }
        throw last ?: OperationFailure("MEDIA_SITE_UNSUPPORTED")
    }

    override fun download(
        url: String, choice: MediaChoice, audio: AudioOutput, bitrate: Int,
        allowHttp: Boolean, directory: File, check: () -> Unit,
        progress: (MediaProgress) -> Unit
    ) {
        check()
        val engine = answered[url] ?: throw OperationFailure("MEDIA_REANALYSIS_REQUIRED")
        engine.download(url, choice, audio, bitrate, allowHttp, directory, check, progress)
    }

    override fun refreshExtractor(): String {
        // Follow the same engine preference used for analysis.
        for (engine in engines) {
            val result = engine.refreshExtractor()
            if (result != "MEDIA_BUILD_NOT_ENABLED" && result != "MEDIA_RUNTIME_UNAVAILABLE") return result
        }
        return "MEDIA_BUILD_NOT_ENABLED"
    }
}
