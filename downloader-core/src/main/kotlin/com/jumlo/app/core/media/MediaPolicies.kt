package com.jumlo.app.core.media

import com.jumlo.app.core.Hashing
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.core.SafeNames
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object MediaCatalog {
    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,95}")
    private val protocols = setOf("http", "https", "m3u8", "m3u8_native", "http_dash_segments")
    private val extensions = setOf("mp4", "m4v", "mov", "webm", "mkv", "m4a", "mp3", "ogg", "opus", "flac", "wav", "aac", "ts", "pdf", "jpg", "jpeg", "png", "webp", "zip")
    private val mp4VideoExtensions = setOf("mp4", "m4v", "mov")
    private val mp4AudioExtensions = setOf("m4a", "mp4")
    private val webmAudioExtensions = setOf("webm", "opus", "ogg")
    private val standalonePairExtractors = setOf("Html5", "DirectMedia")
    private val audioOrder = compareBy<MediaFormat> { it.audioBitrateKbps ?: it.bitrateKbps ?: -1.0 }.thenBy { it.preference }.thenBy { it.id }
    private fun validMetric(v: Double?): Boolean = v == null || (v.isFinite() && v >= 0.0)
    private fun validSize(v: Long?): Boolean = v == null || v >= 0L
    fun choices(asset: MediaAsset, mode: MediaMode): List<MediaChoice> {
        require(!asset.isPlaylist) { "PLAYLIST_REQUIRES_ITEM_SELECTION" }
        require(!asset.isLive) { "LIVE_MEDIA_NOT_FINITE" }
        require(asset.formats.size <= 2000) { "TOO_MANY_MEDIA_FORMATS" }
        val seen = HashSet<String>()
        val formats = asset.formats.filter { f ->
            // Extractors also return thumbnails/storyboards with zero dimensions.
            // They are not media candidates and must not hide a valid stream.
            if (f.drm || f.protocol !in protocols || f.extension.lowercase(Locale.ROOT) !in extensions ||
                (!f.hasAudio && !f.hasVideo)) return@filter false
            require(idPattern.matches(f.id)) { "INVALID_MEDIA_FORMAT" }
            if (!seen.add(f.id)) return@filter false
            require(f.width == null || f.width in 1..65536) { "INVALID_MEDIA_FORMAT" }
            require(f.height == null || f.height in 1..65536) { "INVALID_MEDIA_FORMAT" }
            require(validMetric(f.fps) && validMetric(f.bitrateKbps) && validMetric(f.audioBitrateKbps)) { "INVALID_MEDIA_FORMAT" }
            require(validSize(f.bytes) && validSize(f.approximateBytes)) { "INVALID_MEDIA_FORMAT" }
            true
        }
        val audioOnly = formats.filter { it.hasAudio && !it.hasVideo }
        fun audioContainerPriority(ext: String): Int = when (ext.lowercase(Locale.ROOT)) {
            "m4a", "mp3", "aac" -> 2
            "opus", "ogg", "webm" -> 1
            else -> 0
        }
        if (mode == MediaMode.AUDIO) {
            val sources = audioOnly.ifEmpty { formats.filter { it.hasAudio } }
            val seenAudio = HashSet<Int>()
            val rankedAudio = sources.sortedWith(
                compareByDescending<MediaFormat> { ((it.audioBitrateKbps ?: it.bitrateKbps ?: 0.0) / 16.0).roundToInt() }
                    .thenByDescending { audioContainerPriority(it.extension) }
                    .thenByDescending { it.audioBitrateKbps ?: it.bitrateKbps ?: -1.0 }
                    .thenByDescending { it.preference }
                    .thenBy { it.id }
            )
            return rankedAudio.mapNotNull { f ->
                val kbpsBucket = ((f.audioBitrateKbps ?: f.bitrateKbps ?: 0.0) / 16.0).roundToInt()
                if (!seenAudio.add(kbpsBucket)) null else choice(mode, listOf(f), f.extension)
            }
        }
        val rawVideos = formats.filter { it.hasVideo }
        val hasDimensioned = rawVideos.any { it.width != null || it.height != null }
        val candidateVideos = if (hasDimensioned) {
            rawVideos.filter { it.width != null || it.height != null }
        } else rawVideos

        fun codecPriority(codec: String): Int {
            val c = codec.lowercase(Locale.ROOT)
            return when {
                c.startsWith("avc") || c.contains("h264") -> 3
                c.startsWith("hvc") || c.startsWith("hev") || c.contains("265") -> 2
                c.startsWith("vp9") || c.startsWith("av01") -> 1
                else -> 0
            }
        }

        fun containerPriority(ext: String): Int = when (ext.lowercase(Locale.ROOT)) {
            "mp4", "m4v", "mov" -> 2
            "webm" -> 1
            else -> 0
        }
        fun fpsTier(fps: Double?): Int = if ((fps ?: 0.0) >= 50.0) 60 else 0

        val isStandaloneExtractor = asset.extractor in standalonePairExtractors
        val bestAnyAudio = if (isStandaloneExtractor) null else audioOnly.maxWithOrNull(audioOrder)
        val bestMp4Audio = if (isStandaloneExtractor) null else audioOnly.asSequence()
            .filter { it.extension in mp4AudioExtensions }
            .maxWithOrNull(audioOrder) ?: bestAnyAudio
        val bestWebmAudio = if (isStandaloneExtractor) null else audioOnly.asSequence()
            .filter { it.extension in webmAudioExtensions && (it.audioCodec.contains("opus", ignoreCase = true) || it.audioCodec.contains("vorbis", ignoreCase = true)) }
            .maxWithOrNull(audioOrder) ?: bestAnyAudio
        val builtCandidates = candidateVideos.map { video ->
            // A static webpage may link an unrelated standalone audio file. Never
            // silently combine two sources without an extractor declaring them a pair.
            val audio = if (video.hasAudio || isStandaloneExtractor) null else when (video.extension) {
                "mp4", "m4v", "mov" -> bestMp4Audio
                "webm" -> bestWebmAudio
                else -> bestAnyAudio
            }
            val selected = listOfNotNull(video, audio)
            val container = when {
                audio == null -> video.extension
                video.extension in mp4VideoExtensions && audio.extension in mp4AudioExtensions -> "mp4"
                video.extension == "webm" && audio.extension in webmAudioExtensions -> "webm"
                else -> "mkv"
            }
            video to choice(mode, selected, container)
        }.sortedWith(
            compareByDescending<Pair<MediaFormat, MediaChoice>> { qualityHeight(it.second) ?: 0 }
                .thenByDescending { fpsTier(it.first.fps) }
                .thenByDescending { it.second.expectsAudio }
                .thenByDescending { containerPriority(it.second.container) }
                .thenByDescending { codecPriority(it.first.videoCodec) }
                .thenByDescending { !it.second.requiresMux }
                .thenByDescending { it.first.bitrateKbps ?: 0.0 }
                .thenByDescending { it.second.estimatedBytes ?: 0L }
                .thenByDescending { it.first.preference }
                .thenBy { it.first.id }
        )

        val seenVideoChoices = HashSet<String>()
        return builtCandidates.mapNotNull { (video, built) ->
            // Deduplicate CDN mirrors, container variants (MP4/WEBM/MKV), and codec variants
            // strictly per resolution + FPS tier so YouTube/TikTok never show duplicate quality cards.
            val h = qualityHeight(built) ?: -1
            val dedupKey = "$h:${fpsTier(video.fps)}"
            if (!seenVideoChoices.add(dedupKey)) null else built
        }
    }
    /** Default: choose best verified audio-backed video; an explicit quality cap is an advanced preference. */
    fun preferred(choices:List<MediaChoice>,mode:MediaMode,maxHeight:Int=0):MediaChoice? {
        if(mode==MediaMode.AUDIO) return choices.firstOrNull()
        val withAudio=choices.filter { it.expectsAudio }.ifEmpty { choices }
        return withAudio.firstOrNull { maxHeight==0 || (qualityHeight(it) ?: Int.MAX_VALUE)<=maxHeight }
            ?: withAudio.lastOrNull()
    }
    private fun qualityHeight(choice: MediaChoice): Int? {
        val video = choice.formats.firstOrNull { it.hasVideo }
        val height = choice.expectedHeight ?: video?.height ?: return null
        return video?.width?.let { minOf(it, height) } ?: height
    }
    /** Human-readable quality badge for UI cards (e.g. "1080p Full HD · 60fps" or "160 kbps M4A"). */
    fun qualityTitle(choice: MediaChoice): String {
        if (choice.mode == MediaMode.AUDIO) {
            val f = choice.formats.firstOrNull()
            val kbps = (f?.audioBitrateKbps ?: f?.bitrateKbps?.takeUnless { f.hasVideo })?.roundToInt()?.takeIf { it > 0 }
            val ext = (f?.extension ?: choice.container).uppercase(Locale.ROOT)
            return if (kbps != null) "$kbps kbps · $ext" else ext
        }
        val v = choice.formats.firstOrNull { it.hasVideo }
        val h = qualityHeight(choice)
        val tier = when {
            h == null -> "Standard"
            h >= 2160 -> "${h}p 4K"
            h >= 1440 -> "${h}p 2K"
            h >= 1080 -> "${h}p Full HD"
            h >= 720 -> "${h}p HD"
            else -> "${h}p"
        }
        val fps = v?.fps?.roundToInt()?.takeIf { it >= 50 }?.let { " · ${it}fps" }.orEmpty()
        return tier + fps
    }
    /** Estimates byte size for UI display using reported bytes or duration * bitrate when bytes are omitted. */
    fun displayBytes(choice: MediaChoice, durationSeconds: Double?): Long? {
        choice.estimatedBytes?.let { return it }
        val dur = durationSeconds?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val rates = choice.formats.map { it.bitrateKbps ?: it.audioBitrateKbps }
        if (rates.any { it == null || !it.isFinite() || it <= 0.0 }) return null
        val totalKbps = rates.filterNotNull().sum()
        val estimated = (totalKbps * 1000.0 / 8.0) * dur
        return estimated.takeIf { it.isFinite() && it > 0.0 && it < Long.MAX_VALUE.toDouble() }?.roundToLong()?.takeIf { it > 0L }
    }
    private fun choice(mode: MediaMode, formats: List<MediaFormat>, container: String): MediaChoice {
        val sizes = formats.map { it.bytes ?: it.approximateBytes }
        var sum: Long? = if (sizes.any { it == null }) null else 0L
        if (sum != null) for (size in sizes.filterNotNull()) sum = sum?.let { if (it <= Long.MAX_VALUE - size) it + size else null }
        return MediaChoice("${mode.name}:${formats.joinToString("+") { it.id }}", mode, formats, container, formats.size > 1,
            formats.firstOrNull { it.hasVideo }?.height, sum, formats.any { it.bytes == null && it.approximateBytes != null })
    }
    fun identity(asset: MediaAsset, pageUrl: String): String {
        require(asset.id.isNotBlank() && asset.id.length <= 4096 && asset.extractor.isNotBlank() && asset.extractor.length <= 512) { "INVALID_MEDIA_IDENTITY" }
        return Hashing.sha256(ByteArrayInputStream((asset.extractor + "\u0000" + asset.id + "\u0000" + pageUrl).toByteArray(Charsets.UTF_8)))
    }
    fun fingerprint(choice: MediaChoice): String {
        val text = choice.mode.name + "|" + choice.container + "|" + choice.formats.joinToString(";") {
            listOf(it.id,it.extension,it.videoCodec,it.audioCodec,it.width,it.height,it.fps,it.language).joinToString("|")
        }
        return Hashing.sha256(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    }
    fun validateSelector(choice: MediaChoice): String {
        require(choice.formats.size in 1..2 && choice.formatIds.distinct().size == choice.formats.size && choice.formatIds.all(idPattern::matches)) { "INVALID_MEDIA_FORMAT" }
        require(choice.formats.none { it.drm || it.protocol !in protocols }) { "INVALID_MEDIA_FORMAT" }
        require(choice.mode != MediaMode.AUDIO || choice.expectsAudio) { "AUDIO_TRACK_UNAVAILABLE" }
        // yt-dlp treats an unquoted numeric value as a number, while format_id is
        // always text. Quoting is required for YouTube IDs such as 137 and 140;
        // the strict ID grammar above also prevents quotes or selector injection.
        return choice.formatIds.joinToString("+") { "best*[format_id='$it']" }
    }
    /** Builds a resilient format selector with a safe resolution/mode fallback if a CDN mirror ID rotates between inspect and download. */
    fun downloadSelector(choice: MediaChoice): String {
        val exact = validateSelector(choice)
        if (choice.mode == MediaMode.AUDIO) return "$exact/bestaudio/best"
        val h = qualityHeight(choice)
        return if (h != null && h > 0) {
            "$exact/bestvideo[height<=$h]+bestaudio/best[height<=$h]/best"
        } else {
            "$exact/bestvideo+bestaudio/best"
        }
    }
}

object MediaLinks {
    private val URL_PATTERN = Regex("https?://[^\\s<>\"\\u0000-\\u001f]+", RegexOption.IGNORE_CASE)
    /** Share intents often contain captions and several links. Return only validated, unique
     * HTTP(S) URLs in their original order. Do not resolve DNS, execute page scripts, or
     * interpret arbitrary prose as a URL. Transfer-time consent remains enforced separately. */
    fun sharedUrls(text: String): List<String> {
        require(text.length <= 100000) { "INVALID_DOWNLOAD_LIST" }
        val seen = LinkedHashSet<String>()
        for (match in URL_PATTERN.findAll(text)) {
            val candidate = match.value.trimEnd('.', ',', ';', ')', ']', '!').substringBefore('#')
            val uri = runCatching { HttpPolicy.url(candidate, cleartext = true) }.getOrNull() ?: continue
            if (privateSharedHost(uri.host.orEmpty())) continue
            if (seen.add(uri.toASCIIString())) require(seen.size <= 100) { "TOO_MANY_DOWNLOAD_URLS" }
        }
        return seen.toList()
    }

    private fun privateSharedHost(hostInput: String): Boolean {
        val host = hostInput.lowercase(Locale.ROOT).trimEnd('.').removePrefix("[").removeSuffix("]")
        if (host.isEmpty() || host == "localhost" || host.endsWith(".localhost") ||
            host.endsWith(".local") || host.endsWith(".internal") ||
            host == "::1" || host == "::" || host == "0:0:0:0:0:0:0:1" || host == "0:0:0:0:0:0:0:0" ||
            host.startsWith("::ffff:") || host.startsWith("fe80:")) return true
        if (':' in host) return true
        val parts = host.split('.')
        val ipv4 = parts.map { it.toIntOrNull() }
        if (ipv4.size == 4 && ipv4.all { it != null && it in 0..255 }) {
            if (parts.any { it.length > 1 && it.startsWith('0') }) return true
            val a = ipv4[0]!!; val b = ipv4[1]!!
            return a == 0 || a == 10 || a == 127 || a >= 224 ||
                (a == 169 && b == 254) || (a == 172 && b in 16..31) ||
                (a == 192 && (b == 168 || b == 0)) || (a == 100 && b in 64..127) ||
                (a == 198 && b in 18..19)
        }
        // Reject shorthand/octal/hex IPv4 forms (e.g. 127.1, 0x7f000001) or single-label hosts.
        if (parts.size < 2 || parts.last().all { it.isDigit() } ||
            parts.all { it.all(Char::isDigit) || it.startsWith("0x") }) return true
        return false
    }
    fun normalize(text: String, allowHttp: Boolean = false): String {
        require(text.length <= 32768) { "INVALID_URL" }
        val trimmed = text.trim()
        val direct = trimmed.takeIf { (it.startsWith("https://", true) || it.startsWith("http://", true)) && it.none(Char::isWhitespace) }
        val urls = direct?.let(::listOf) ?: URL_PATTERN.findAll(trimmed).map { it.value.trimEnd('.', ',', ')', ']') }.toList()
        require(urls.isNotEmpty()) { "INVALID_URL" }
        require(urls.size == 1) { "MULTIPLE_MEDIA_LINKS" }
        val candidate = urls.single().substringBefore('#')
        val uri = HttpPolicy.url(candidate, allowHttp)
        require(!privateSharedHost(uri.host.orEmpty())) { "INVALID_URL" }
        return uri.toASCIIString()
    }
}

object MediaArguments {
    private val commonArgs = listOf("--ignore-config", "--no-cache-dir", "--no-playlist", "--geo-bypass", "--socket-timeout", "30", "--retries", "5", "--extractor-retries", "5", "--fragment-retries", "10", "--abort-on-error")
    private val probeFlags = listOf("--skip-download", "--dump-single-json", "--no-warnings", "--flat-playlist")
    private val youtubeProbeArgs = listOf("--compat-options", "no-youtube-unavailable-videos", "--extractor-args", "youtube:player_client=android_vr,web")
    private val youtubeFallbackArgs = listOf("--compat-options", "no-youtube-unavailable-videos", "--extractor-args", "youtube:player_client=tv_embedded,web_safari")
    private val youtubeDownloadArgs = listOf("--extractor-args", "youtube:player_client=android_vr,web")
    private val tiktokFallbackArgs = listOf("--extractor-args", "tiktok:api_hostname=api22-normal-c-useast2a.tiktokv.com")
    private val allowedAudioBitrates = setOf(64, 96, 128, 160, 192, 256, 320)
    private val muxContainers = setOf("mp4", "webm", "mkv")
    private fun isYouTubeUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") || host.endsWith(".youtube-nocookie.com")
    }
    private fun isTikTokUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }
    fun probe(url: String, allowHttp: Boolean = false): List<String> {
        val normalized = MediaLinks.normalize(url, allowHttp)
        val args = commonArgs.toMutableList()
        args += probeFlags
        if (isYouTubeUrl(normalized)) {
            args += youtubeProbeArgs
        }
        args += listOf("--", normalized)
        return args
    }
    /** Platform-aware fallback probe when primary client extraction fails. */
    fun probeFallback(url: String, allowHttp: Boolean = false): List<String> {
        val normalized = MediaLinks.normalize(url, allowHttp)
        val args = commonArgs.toMutableList()
        args += probeFlags
        when {
            isYouTubeUrl(normalized) -> args += youtubeFallbackArgs
            isTikTokUrl(normalized) -> args += tiktokFallbackArgs
        }
        args += listOf("--", normalized)
        return args
    }
    fun download(url: String, choice: MediaChoice, audio: AudioOutput = AudioOutput.ORIGINAL, bitrate: Int = 192, allowHttp: Boolean = false): List<String> {
        require(bitrate in allowedAudioBitrates) { "INVALID_AUDIO_BITRATE" }
        val normalized = MediaLinks.normalize(url, allowHttp)
        val args = commonArgs.toMutableList()
        if (isYouTubeUrl(normalized)) {
            args += youtubeDownloadArgs
        }
        if (choice.mode == MediaMode.VIDEO) {
            args += listOf("--format-sort", "res,vcodec:h264,acodec:m4a")
        }
        args += listOf("--format", MediaCatalog.downloadSelector(choice), "--output", "media.%(ext)s", "--continue", "--force-overwrites", "--skip-unavailable-fragments", "--hls-prefer-native", "--downloader", "native", "--concurrent-fragments", "4", "--newline", "--progress", "--progress-delta", "0.2", "--progress-template", "download:UU_PROGRESS:%(info.format_id)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s")
        if (choice.mode == MediaMode.AUDIO) {
            args += listOf("--extract-audio", "--audio-format", if (audio == AudioOutput.ORIGINAL) "best" else audio.name.lowercase(Locale.ROOT))
            if (audio != AudioOutput.ORIGINAL) args += listOf("--audio-quality", "${bitrate}K")
        } else {
            val container = choice.container.takeIf { it in muxContainers } ?: "mp4"
            args += listOf("--merge-output-format", container)
        }
        args += listOf("--", normalized)
        return args
    }
    /** Resilient fallback download using universal MP4/M4A format selection and default client ladder. */
    fun downloadFallback(url: String, choice: MediaChoice, audio: AudioOutput = AudioOutput.ORIGINAL, bitrate: Int = 192, allowHttp: Boolean = false): List<String> {
        require(bitrate in allowedAudioBitrates) { "INVALID_AUDIO_BITRATE" }
        val normalized = MediaLinks.normalize(url, allowHttp)
        val args = commonArgs.toMutableList()
        val selector = if (choice.mode == MediaMode.AUDIO) {
            "bestaudio/best"
        } else {
            val h = choice.expectedHeight?.takeIf { it > 0 }
            if (h != null) "bestvideo[height<=$h][ext=mp4]+bestaudio[ext=m4a]/best[height<=$h][ext=mp4]/bestvideo[height<=$h]+bestaudio/best[height<=$h]/best"
            else "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/bestvideo+bestaudio/best"
        }
        args += listOf("--format", selector, "--output", "media.%(ext)s", "--force-overwrites", "--skip-unavailable-fragments", "--hls-prefer-native", "--downloader", "native", "--concurrent-fragments", "4", "--newline", "--progress", "--progress-delta", "0.2", "--progress-template", "download:UU_PROGRESS:%(info.format_id)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s")
        if (choice.mode == MediaMode.AUDIO) {
            args += listOf("--extract-audio", "--audio-format", if (audio == AudioOutput.ORIGINAL) "best" else audio.name.lowercase(Locale.ROOT))
            if (audio != AudioOutput.ORIGINAL) args += listOf("--audio-quality", "${bitrate}K")
        } else {
            args += listOf("--merge-output-format", "mp4")
        }
        args += listOf("--", normalized)
        return args
    }
}

object MediaOutputPolicy {
    private val INVALID_NAME_CHARS = Regex("[\\p{Cc}\\p{Cf}/\\\\:*?\"<>|]")
    private val WHITESPACE = Regex("\\s+")
    private val mime = mapOf("mp4" to "video/mp4", "m4v" to "video/mp4", "mov" to "video/quicktime", "mkv" to "video/x-matroska", "webm" to "video/webm", "m4a" to "audio/mp4", "mp3" to "audio/mpeg", "ogg" to "audio/ogg", "opus" to "audio/ogg", "flac" to "audio/flac", "wav" to "audio/wav", "aac" to "audio/aac", "ts" to "video/mp2t", "pdf" to "application/pdf", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp", "zip" to "application/zip")
    private val FRAGMENT_SUFFIX = Regex("^media\\.f[A-Za-z0-9_-]+\\.")
    fun finishedFile(root: File): File {
        val all = root.listFiles()?.filter { it.name.startsWith("media.") && it.extension.lowercase(Locale.ROOT) in mime.keys && it.isFile && it.length() > 0 } ?: emptyList()
        require(all.isNotEmpty()) { "MEDIA_OUTPUT_MISSING" }
        // Ignore leftover intermediate yt-dlp stream files (e.g. media.f137.mp4) when a merged media.<ext> exists.
        val merged = all.filterNot { FRAGMENT_SUFFIX.containsMatchIn(it.name) }
        val candidates = merged.ifEmpty { all }
        require(candidates.size == 1) { "MEDIA_OUTPUT_AMBIGUOUS" }
        val file = candidates.single()
        require(file.isFile && !Files.isSymbolicLink(file.toPath()) && file.canonicalFile.parentFile == root.canonicalFile && file.length() > 0) { "INVALID_MEDIA_OUTPUT" }
        return file
    }
    /** A container signature is a first guard, not a replacement for track/decoder validation. */
    fun inspect(file: File): MediaFileInfo {
        val extension = file.extension.lowercase(Locale.ROOT)
        require(extension in mime) { "INVALID_MEDIA_EXTENSION" }
        require(file.isFile && !Files.isSymbolicLink(file.toPath()) && file.length() > 0) { "INVALID_MEDIA_OUTPUT" }
        val head = ByteArray(16)
        val n = file.inputStream().use { it.read(head) }
        fun prefix(vararg bytes: Int) = n >= bytes.size && bytes.indices.all { head[it].toInt() and 255 == bytes[it] }
        fun text(offset: Int, value: String) = n >= offset + value.length && String(head, offset, value.length, Charsets.US_ASCII) == value
        val valid = when (extension) {
            "mp4", "m4v", "m4a", "mov" -> text(4,"ftyp")
            "mkv", "webm" -> prefix(0x1a,0x45,0xdf,0xa3)
            "mp3" -> text(0,"ID3") || n >= 2 && (head[0].toInt() and 255 == 255) && (head[1].toInt() and 0xe0 == 0xe0)
            "ogg", "opus" -> text(0,"OggS")
            "flac" -> text(0,"fLaC")
            "wav" -> text(0,"RIFF") && text(8,"WAVE")
            "aac" -> n >= 2 && (head[0].toInt() and 255 == 255) && (head[1].toInt() and 0xf6 == 0xf0)
            "ts" -> prefix(0x47) && file.length() >= 188
            "pdf" -> text(0, "%PDF-")
            "jpg", "jpeg" -> prefix(0xFF, 0xD8, 0xFF)
            "png" -> prefix(0x89, 0x50, 0x4E, 0x47)
            "webp" -> text(0, "RIFF") && text(8, "WEBP")
            "zip" -> prefix(0x50, 0x4B, 0x03, 0x04)
            else -> false
        }
        require(valid) { "INVALID_MEDIA_CONTAINER" }
        return MediaFileInfo(extension, mime.getValue(extension), file.length())
    }
    fun displayName(title: String, extension: String): String {
        require(extension in mime) { "INVALID_MEDIA_EXTENSION" }
        var clean = title.replace(INVALID_NAME_CHARS, " ").replace(WHITESPACE, " ").trim(' ', '.')
        val sourceExtension = clean.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if(sourceExtension in mime) clean=clean.dropLast(sourceExtension.length+1).trimEnd(' ', '.')
        if (clean.isBlank()) clean = "media"
        val limit = 198 - extension.toByteArray(Charsets.UTF_8).size
        while (clean.toByteArray(Charsets.UTF_8).size > limit) clean = clean.dropLast(if (clean.length >= 2 && Character.isLowSurrogate(clean.last()) && Character.isHighSurrogate(clean[clean.lastIndex-1])) 2 else 1)
        return SafeNames.basename("${clean.trimEnd(' ', '.').ifBlank { "media" }}.$extension")
    }
}

object MediaProgressParser {
    private val FORMAT_ID = Regex("[A-Za-z0-9_.+-]{1,200}")
    fun parse(line: String): MediaProgress? {
        if (!line.startsWith("UU_PROGRESS:") || line.length > 512) return null
        val fields = line.removePrefix("UU_PROGRESS:").split('|')
        if (fields.size != 6 || !FORMAT_ID.matches(fields[0])) return null
        fun nonNegative(value: String) = value.toLongOrNull()?.takeIf { it >= 0 }
        val done = nonNegative(fields[1]) ?: return null
        val total = nonNegative(fields[2])?.takeIf { it >= done }
        return MediaProgress(fields[0], done, total, nonNegative(fields[3]), fields[4].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }, nonNegative(fields[5]))
    }
}
