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
    private val extensions = setOf("mp4", "m4v", "mov", "webm", "mkv", "m4a", "mp3", "ogg", "opus", "flac", "wav", "aac", "ts")
    fun choices(asset: MediaAsset, mode: MediaMode): List<MediaChoice> {
        require(!asset.isPlaylist) { "PLAYLIST_REQUIRES_ITEM_SELECTION" }
        require(!asset.isLive) { "LIVE_MEDIA_NOT_FINITE" }
        require(asset.formats.size <= 2000) { "TOO_MANY_MEDIA_FORMATS" }
        val seen = HashSet<String>()
        val formats = asset.formats.filter { f ->
            require(idPattern.matches(f.id)) { "INVALID_MEDIA_FORMAT" }
            if (!seen.add(f.id)) return@filter false
            require(f.width == null || f.width in 1..65536) { "INVALID_MEDIA_FORMAT" }
            require(f.height == null || f.height in 1..65536) { "INVALID_MEDIA_FORMAT" }
            require(listOf(f.fps, f.bitrateKbps, f.audioBitrateKbps).all { it == null || it.isFinite() && it >= 0 }) { "INVALID_MEDIA_FORMAT" }
            require(listOf(f.bytes, f.approximateBytes).all { it == null || it >= 0 }) { "INVALID_MEDIA_FORMAT" }
            !f.drm && f.protocol in protocols && f.extension.lowercase(Locale.ROOT) in extensions && (f.hasAudio || f.hasVideo)
        }
        val audioOnly = formats.filter { it.hasAudio && !it.hasVideo }
        val audioOrder = compareBy<MediaFormat> { it.audioBitrateKbps ?: it.bitrateKbps ?: -1.0 }.thenBy { it.preference }.thenBy { it.id }
        if (mode == MediaMode.AUDIO) {
            val sources = audioOnly.ifEmpty { formats.filter { it.hasAudio } }
            return sources.sortedWith(audioOrder.reversed()).map { choice(mode, listOf(it), "auto") }
        }
        return formats.filter { it.hasVideo }
            .sortedWith(compareByDescending<MediaFormat> { it.height ?: 0 }.thenByDescending { it.fps ?: 0.0 }.thenByDescending { it.bitrateKbps ?: 0.0 }.thenByDescending { it.preference }.thenBy { it.id })
            .map { video ->
                val preferred = audioOnly.filter { audio ->
                    when (video.extension) {
                        "mp4", "m4v", "mov" -> audio.extension in setOf("m4a", "mp4")
                        "webm" -> audio.extension in setOf("webm", "opus", "ogg") && (audio.audioCodec.contains("opus") || audio.audioCodec.contains("vorbis"))
                        else -> false
                    }
                }.ifEmpty { audioOnly }
                // A static webpage may link an unrelated standalone audio file. Never
                // silently combine two sources without an extractor declaring them a pair.
                val audio = if (video.hasAudio || asset.extractor in setOf("Html5", "DirectMedia"))
                    null else preferred.maxWithOrNull(audioOrder)
                val selected = listOfNotNull(video, audio)
                val container = when {
                    audio == null -> video.extension
                    video.extension in setOf("mp4", "m4v", "mov") && audio.extension in setOf("m4a", "mp4") -> "mp4"
                    video.extension == "webm" && audio.extension in setOf("webm", "opus", "ogg") -> "webm"
                    else -> "mkv"
                }
                choice(mode, selected, container)
            }
    }
    /** One-time preference: balanced caps at 1080p; never picks silent video if a voiced choice exists. */
    fun preferred(choices:List<MediaChoice>,mode:MediaMode,maxHeight:Int=1080):MediaChoice? {
        if(mode==MediaMode.AUDIO) return choices.firstOrNull()
        val withAudio=choices.filter { it.expectsAudio }.ifEmpty { choices }
        return withAudio.firstOrNull { maxHeight==0 || (it.expectedHeight ?: Int.MAX_VALUE)<=maxHeight }
            ?: withAudio.lastOrNull()
    }
    /** Human-readable quality badge for UI cards (e.g. "1080p Full HD · 60fps" or "160 kbps M4A"). */
    fun qualityTitle(choice: MediaChoice): String {
        if (choice.mode == MediaMode.AUDIO) {
            val f = choice.formats.firstOrNull()
            val kbps = (f?.audioBitrateKbps ?: f?.bitrateKbps)?.roundToInt()?.takeIf { it > 0 }
            val ext = (f?.extension ?: choice.container).uppercase(Locale.ROOT)
            return if (kbps != null) "$kbps kbps · $ext" else ext
        }
        val v = choice.formats.firstOrNull { it.hasVideo }
        val h = choice.expectedHeight ?: v?.height
        val tier = when {
            h == null -> choice.container.uppercase(Locale.ROOT)
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
        val totalKbps = choice.formats.sumOf { (it.bitrateKbps ?: it.audioBitrateKbps ?: 0.0) }
        if (totalKbps <= 0.0) return null
        return ((totalKbps * 1000.0 / 8.0) * dur).roundToLong().takeIf { it > 0L }
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
        return choice.formatIds.joinToString("+") { "best*[format_id=$it]" }
    }
}

object MediaLinks {
    /** Share intents often contain captions and several links. Return only validated, unique
     * HTTP(S) URLs in their original order. Do not resolve DNS, execute page scripts, or
     * interpret arbitrary prose as a URL. Transfer-time consent remains enforced separately. */
    fun sharedUrls(text: String): List<String> {
        require(text.length <= 100000) { "INVALID_DOWNLOAD_LIST" }
        val seen = LinkedHashSet<String>()
        val pattern = Regex("https?://[^\\s<>\\\"\\u0000-\\u001f]+", RegexOption.IGNORE_CASE)
        for (match in pattern.findAll(text)) {
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
            host.startsWith("::ffff:") || host.startsWith("fe80:") ||
            host.startsWith("fc") || host.startsWith("fd")) return true
        if (':' in host) return true
        val parts = host.split('.')
        val ipv4 = parts.map { it.toIntOrNull() }
        if (ipv4.size == 4 && ipv4.all { it != null && it in 0..255 }) {
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
        val urls = direct?.let(::listOf) ?: Regex("https?://[^\\s<>\"\\u0000-\\u001f]+", RegexOption.IGNORE_CASE).findAll(trimmed).map { it.value.trimEnd('.', ',', ')', ']') }.toList()
        require(urls.isNotEmpty()) { "INVALID_URL" }
        require(urls.size == 1) { "MULTIPLE_MEDIA_LINKS" }
        val candidate = urls.single().substringBefore('#')
        return HttpPolicy.url(candidate, allowHttp).toASCIIString()
    }
}

object MediaArguments {
    private fun common() = listOf("--ignore-config", "--no-cache-dir", "--no-playlist", "--socket-timeout", "20", "--retries", "2", "--extractor-retries", "2", "--fragment-retries", "2", "--abort-on-error", "--no-check-formats")
    fun probe(url: String, allowHttp: Boolean = false): List<String> = common() + listOf("--skip-download", "--dump-single-json", "--no-warnings", "--", MediaLinks.normalize(url, allowHttp))
    fun download(url: String, choice: MediaChoice, audio: AudioOutput = AudioOutput.ORIGINAL, bitrate: Int = 192, allowHttp: Boolean = false): List<String> {
        require(bitrate in setOf(64, 96, 128, 160, 192, 256, 320)) { "INVALID_AUDIO_BITRATE" }
        val args = common().toMutableList()
        args += listOf("--format", MediaCatalog.validateSelector(choice), "--output", "media.%(ext)s", "--continue", "--no-overwrites", "--abort-on-unavailable-fragments", "--hls-prefer-native", "--downloader", "native", "--concurrent-fragments", "1", "--newline", "--progress", "--progress-delta", "0.5", "--progress-template", "download:UU_PROGRESS:%(info.format_id)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s")
        if (choice.mode == MediaMode.AUDIO) {
            args += listOf("--extract-audio", "--audio-format", if (audio == AudioOutput.ORIGINAL) "best" else audio.name.lowercase(Locale.ROOT))
            if (audio != AudioOutput.ORIGINAL) args += listOf("--audio-quality", "${bitrate}K")
        } else if (choice.requiresMux) {
            require(choice.container in setOf("mp4", "webm", "mkv")) { "INVALID_MEDIA_CONTAINER" }
            args += listOf("--merge-output-format", choice.container)
        }
        args += listOf("--", MediaLinks.normalize(url, allowHttp))
        return args
    }
}

object MediaOutputPolicy {
    private val mime = mapOf("mp4" to "video/mp4", "m4v" to "video/mp4", "mov" to "video/quicktime", "mkv" to "video/x-matroska", "webm" to "video/webm", "m4a" to "audio/mp4", "mp3" to "audio/mpeg", "ogg" to "audio/ogg", "opus" to "audio/ogg", "flac" to "audio/flac", "wav" to "audio/wav", "aac" to "audio/aac", "ts" to "video/mp2t")
    fun finishedFile(root: File): File {
        val files = root.listFiles()?.filter { it.name.startsWith("media.") && it.extension.lowercase(Locale.ROOT) in mime.keys } ?: emptyList()
        require(files.isNotEmpty()) { "MEDIA_OUTPUT_MISSING" }
        require(files.size == 1) { "MEDIA_OUTPUT_AMBIGUOUS" }
        val file = files.single()
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
            else -> false
        }
        require(valid) { "INVALID_MEDIA_CONTAINER" }
        return MediaFileInfo(extension, mime.getValue(extension), file.length())
    }
    fun displayName(title: String, extension: String): String {
        require(extension in mime) { "INVALID_MEDIA_EXTENSION" }
        var clean = title.replace(Regex("[\\p{Cc}\\p{Cf}/\\\\:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim(' ', '.')
        if (clean.isBlank()) clean = "media"
        val limit = 198 - extension.toByteArray(Charsets.UTF_8).size
        while (clean.toByteArray(Charsets.UTF_8).size > limit) clean = clean.dropLast(if (clean.length >= 2 && Character.isLowSurrogate(clean.last()) && Character.isHighSurrogate(clean[clean.lastIndex-1])) 2 else 1)
        return SafeNames.basename("${clean.trimEnd(' ', '.')}.$extension")
    }
}

object MediaProgressParser {
    fun parse(line: String): MediaProgress? {
        if (!line.startsWith("UU_PROGRESS:") || line.length > 512) return null
        val fields = line.removePrefix("UU_PROGRESS:").split('|')
        if (fields.size != 6 || !Regex("[A-Za-z0-9_.+-]{1,200}").matches(fields[0])) return null
        fun nonNegative(value: String) = value.toLongOrNull()?.takeIf { it >= 0 }
        val done = nonNegative(fields[1]) ?: return null
        val total = nonNegative(fields[2])?.takeIf { it >= done }
        return MediaProgress(fields[0], done, total, nonNegative(fields[3]), fields[4].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }, nonNegative(fields[5]))
    }
}
