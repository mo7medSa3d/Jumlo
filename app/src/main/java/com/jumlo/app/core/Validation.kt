package com.jumlo.app.core

import java.io.File
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

/** UI page numbers are physical, one-based, and preserve the user's order. */
object PageSelection {
    fun parse(value: String, count: Int): List<Int> {
        require(count > 0) { "INVALID_PAGE_COUNT" }
        val normalized = value.map { c -> when (c) {
            in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
            in '\u06f0'..'\u06f9' -> '0' + (c - '\u06f0')
            '\u060c' -> ','
            else -> c
        } }.joinToString("")
        require(normalized.isNotBlank() && normalized.length <= 100000) { "INVALID_PAGE_RANGE" }
        val result = linkedSetOf<Int>()
        normalized.split(',').forEach { part ->
            val match = Regex("^\\s*([0-9]+)\\s*(?:-\\s*([0-9]+)\\s*)?$").matchEntire(part)
            require(match != null) { "INVALID_PAGE_RANGE" }
            val first = match.groupValues[1].toIntOrNull()
            val endpoint=match.groupValues[2]
            val last=if(endpoint.isEmpty()) first else endpoint.toIntOrNull()
            require(first != null && last != null && first in 1..count && last in first..count) { "INVALID_PAGE_RANGE" }
            for (page in first..last) result.add(page)
        }
        return result.toList()
    }
    fun groups(count: Int, every: Int): List<List<Int>> {
        require(count > 0 && every > 0) { "INVALID_SPLIT_SIZE" }
        return (1..count).toList().chunked(every)
    }
}

object SafeNames {
    /** Public provider names have a byte budget, not a UTF-16 character budget.
     * Keep the original suffix and stop at complete Unicode code points. */
    fun fitUtf8(value: String, maxBytes: Int = 200): String {
        require(maxBytes in 1..200) { "INVALID_FILENAME" }
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
        val dot = normalized.lastIndexOf('.').takeIf { it > 0 && normalized.length - it <= 16 }
        val suffix = dot?.let { normalized.substring(it) }.orEmpty()
        val budget = maxBytes - suffix.toByteArray(Charsets.UTF_8).size
        require(budget > 0) { "INVALID_FILENAME" }
        val prefix = if (dot != null) normalized.substring(0, dot) else normalized
        val result = StringBuilder()
        var index = 0
        var used = 0
        while (index < prefix.length) {
            val codePoint = Character.codePointAt(prefix, index)
            require(codePoint !in 0xD800..0xDFFF) { "INVALID_FILENAME" }
            val encodedLength = when {
                codePoint <= 0x7F -> 1
                codePoint <= 0x7FF -> 2
                codePoint <= 0xFFFF -> 3
                else -> 4
            }
            if (used + encodedLength > budget) break
            result.appendCodePoint(codePoint)
            used += encodedLength
            index += Character.charCount(codePoint)
        }
        // Do not permit a malformed surrogate anywhere in a name, including
        // text beyond the truncation boundary or an untrusted extension.
        require(normalized.codePoints().noneMatch { it in 0xD800..0xDFFF }) { "INVALID_FILENAME" }
        return basename(result.toString().trimEnd(' ', '.') + suffix)
    }

    fun basename(value: String): String {
        val name = Normalizer.normalize(value, Normalizer.Form.NFC)
        require(name.isNotBlank() && name != "." && name != ".." && name.toByteArray(Charsets.UTF_8).size <= 200) { "INVALID_FILENAME" }
        require(name.codePoints().noneMatch { it in 0xD800..0xDFFF }) { "INVALID_FILENAME" }
        require(name.none { it < ' ' || it == '\u007f' || it in "/\\:" }) { "INVALID_FILENAME" }
        require(!name.endsWith('.') && !name.endsWith(' ')) { "INVALID_FILENAME" }
        return name
    }
    fun fromDisplayName(value: String, fallback: String = "file.bin"): String =
        runCatching { basename(value) }.getOrElse { basename(fallback) }

    fun entry(root: File, value: String, maxDepth: Int = 32): File {
        require(value.isNotEmpty() && '\\' !in value && !value.startsWith('/') && ':' !in value) { "UNSAFE_ARCHIVE_PATH" }
        val parts = value.removeSuffix("/").split('/')
        require(parts.size <= maxDepth && parts.none { it.isEmpty() || it == "." || it == ".." }) { "UNSAFE_ARCHIVE_PATH" }
        val normalizedParts=parts.map(::basename)
        val destination = File(root, normalizedParts.joinToString(File.separator)).canonicalFile
        require(destination.path.startsWith(root.canonicalPath + File.separator)) { "UNSAFE_ARCHIVE_PATH" }
        return destination
    }
    fun unique(existing: Set<String>, desired: String): String {
        val safe = basename(desired)
        val folded = existing.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        if (safe.lowercase(Locale.ROOT) !in folded) return safe
        val dot = safe.lastIndexOf('.').takeIf { it > 0 } ?: safe.length
        val suffixPart = safe.drop(dot)
        for (n in 1..100000) {
            val counterPart = " ($n)$suffixPart"
            val maxBaseBytes = (200 - counterPart.toByteArray(Charsets.UTF_8).size).coerceAtLeast(1)
            var prefixPart = safe.take(dot)
            while (prefixPart.toByteArray(Charsets.UTF_8).size > maxBaseBytes && prefixPart.isNotEmpty()) {
                val drop = if (prefixPart.length >= 2 && Character.isLowSurrogate(prefixPart.last()) && Character.isHighSurrogate(prefixPart[prefixPart.lastIndex - 1])) 2 else 1
                prefixPart = prefixPart.dropLast(drop).trimEnd(' ', '.')
            }
            val candidate = "${prefixPart.ifBlank { "file" }}$counterPart"
            if (candidate.lowercase(Locale.ROOT) !in folded) return basename(candidate)
        }
        error("NAME_SPACE_EXHAUSTED")
    }
}

object MemoryBudget {
    fun requireBitmaps(w: Int, h: Int, n: Int, b: Long) {
        require(w > 0 && h > 0 && n > 0 && b > 0) { "INVALID_DIMENSIONS" }
        val pixels = w.toLong() * h
        require(pixels <= b / 4 / n) { "BITMAP_BUDGET_EXCEEDED" }
    }
}

enum class FitMode { FIT, FILL, PAD, STRETCH }
data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    fun normalized(): Box {
        require(listOf(left,top,right,bottom).all { it.isFinite() && it in 0.0..1.0 } && width > 0 && height > 0) { "INVALID_CROP" }
        return this
    }
}
data class Geometry(val width: Int, val height: Int, val crop: Box, val draw: Box)

object ImageGeometry {
    fun resolve(sw: Int, sh: Int, w: Int, h: Int, mode: FitMode, upscale: Boolean): Geometry {
        require(sw > 0 && sh > 0 && w > 0 && h > 0) { "INVALID_DIMENSIONS" }
        var ow = w; var oh = h
        val full = Box(0.0,0.0,1.0,1.0)
        if (mode == FitMode.FIT) {
            val scale = min(w.toDouble()/sw,h.toDouble()/sh).let { if(upscale) it else min(1.0,it) }
            ow = max(1,(sw*scale).roundToInt()); oh = max(1,(sh*scale).roundToInt())
            return Geometry(ow,oh,full,Box(0.0,0.0,ow.toDouble(),oh.toDouble()))
        }
        if (!upscale && mode != FitMode.PAD) {
            val shrink = min(1.0,min(sw.toDouble()/w,sh.toDouble()/h))
            ow=max(1,(ow*shrink).roundToInt()); oh=max(1,(oh*shrink).roundToInt())
        }
        if (mode == FitMode.FILL) {
            val scale = max(ow.toDouble()/sw,oh.toDouble()/sh)
            val cw=ow/scale/sw; val ch=oh/scale/sh
            return Geometry(ow,oh,Box((1-cw)/2,(1-ch)/2,(1+cw)/2,(1+ch)/2),Box(0.0,0.0,ow.toDouble(),oh.toDouble()))
        }
        if (mode == FitMode.PAD) {
            val scale=min(ow.toDouble()/sw,oh.toDouble()/sh).let { if(upscale) it else min(1.0,it) }
            val dw=sw*scale; val dh=sh*scale
            return Geometry(ow,oh,full,Box((ow-dw)/2,(oh-dh)/2,(ow+dw)/2,(oh+dh)/2))
        }
        return Geometry(ow,oh,full,Box(0.0,0.0,ow.toDouble(),oh.toDouble()))
    }
}

object HttpPolicy {
    fun url(url: String, cleartext: Boolean = false): URI {
        val u = try { URI(url.trim()) } catch (_: Exception) { throw IllegalArgumentException("INVALID_URL") }
        val scheme=u.scheme?.lowercase(Locale.ROOT)
        require(scheme == "https" || (scheme == "http" && cleartext)) { "HTTPS_REQUIRED" }
        require(!u.host.isNullOrBlank() && u.rawUserInfo == null && u.fragment == null && u.port in -1..65535) { "INVALID_URL" }
        require(url.none { it < ' ' || it == '\u007f' }) { "INVALID_URL" }
        return URI(scheme + ":" + u.rawSchemeSpecificPart)
    }
    fun origin(u: URI): String = "${u.scheme.lowercase(Locale.ROOT)}://${u.host.lowercase(Locale.ROOT)}:${if(u.port>=0) u.port else if(u.scheme.equals("https",true)) 443 else 80}"
}

/** Compatibility facade. Standard availability is defined solely by CapabilityPolicy. */
object Capabilities {
    fun isEnabled(tool: String): Boolean = CapabilityPolicy.availability(tool).enabled
}
