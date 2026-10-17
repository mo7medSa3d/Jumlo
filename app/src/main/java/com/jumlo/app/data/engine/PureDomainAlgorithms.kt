package com.jumlo.app.data.engine

import com.jumlo.app.model.FittingStrategy
import com.jumlo.app.model.ParsedUrlCandidate
import com.jumlo.app.model.ResizeMode
import com.jumlo.app.model.UrlValidationStatus
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure, deterministic domain utilities for Jumlo:
 * 1. Page Range Syntax Parser (pdf_06 & pdf_05): e.g., "1-3, 7, 10-12"
 * 2. Batch URL Intake Parser & Deduplicator (dl_03)
 * 3. Zip-Slip Path Traversal Security Validator (file_08)
 * 4. Collision Filename Resolver (file_07, file_08, dl_02)
 * 5. Image Constraint & Bounding-Box Resize Calculator (img_02)
 * 6. Byte & Tabular Metric Formatter (tnum)
 */
object PureDomainAlgorithms {

    /**
     * Parses a page-range expression like "1-3, 7, 10-12" against [maxPages] (1-indexed)
     * and returns a sorted list of distinct valid page numbers.
     */
    fun parsePageRangeSyntax(input:String,maxPages:Int):Result<List<Int>> =
        runCatching { com.jumlo.app.core.PageSelection.parse(input,maxPages) }

    /**
     * Formats a collection of 1-indexed page numbers back into canonical range syntax (e.g. "1-3, 7, 10-12").
     */
    fun formatPagesToRangeSyntax(pages: Collection<Int>): String {
        val sorted = pages.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var rangeStart = sorted[0]
        var prev = sorted[0]

        for (i in 1 until sorted.size) {
            val current = sorted[i]
            if (current == prev + 1) {
                prev = current
            } else {
                ranges.add(if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev")
                rangeStart = current
                prev = current
            }
        }
        ranges.add(if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev")
        return ranges.joinToString(", ")
    }

    /**
     * Parses multi-line batch URL text (dl_03), detecting valid HTTP/HTTPS links,
     * duplicate URLs, and unsupported protocols (such as ftp://, magnet:, file://).
     */
    fun parseBatchUrls(multilineText:String):List<ParsedUrlCandidate> {
        require(multilineText.length<=100000) { "URL_LIST_TOO_LARGE" }
        val seen=mutableSetOf<String>()
        return multilineText.lineSequence().mapIndexedNotNull { index,raw ->
            val url=raw.trim();if(url.isEmpty()) return@mapIndexedNotNull null
            val unsupported=!url.startsWith("http://",true) && !url.startsWith("https://",true)
            val valid=runCatching { com.jumlo.app.core.HttpPolicy.url(url,true) }.isSuccess
            val status=when { unsupported -> UrlValidationStatus.UNSUPPORTED_PROTOCOL;!valid -> UrlValidationStatus.MALFORMED;!seen.add(url) -> UrlValidationStatus.DUPLICATE;else -> UrlValidationStatus.VALID }
            val name=com.jumlo.app.core.SafeNames.fromDisplayName(url.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" },"download.bin")
            ParsedUrlCandidate(index+1,url,name,"Unknown",status,if(status==UrlValidationStatus.VALID) "Not requested" else status.name)
        }.toList().also { require(it.size<=100) { "URL_LIST_TOO_LARGE" } }
    }

    /**
     * Validates a ZIP entry path against Zip-Slip directory traversal attacks (file_08).
     * Throws SecurityException if the resolved entry escapes [targetDirectory].
     */
    fun validateZipEntryPath(targetDirectory:File,entryName:String):File = try {
        com.jumlo.app.core.SafeNames.entry(targetDirectory,entryName)
    } catch(error:IllegalArgumentException) { throw SecurityException("UNSAFE_ARCHIVE_PATH",error) }

    /**
     * Resolves file naming collisions according to the user's policy:
     * - Keep Both: appends " (1)", " (2)", etc. before extension
     */
    fun resolveUniqueFileName(existingNames: Set<String>, desiredName: String): String {
        if (desiredName !in existingNames) return desiredName
        val dotIndex = desiredName.lastIndexOf('.')
        val base = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
        val ext = if (dotIndex > 0) desiredName.substring(dotIndex) else ""
        var counter = 1
        while (true) {
            val candidate = "$base ($counter)$ext"
            if (candidate !in existingNames) return candidate
            counter++
        }
    }

    /**
     * Calculates target output dimensions for Resize Images (img_02) across all 4 ResizeModes,
     * 4 FittingStrategies, and upscaling guard.
     */
    fun calculateTargetDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        mode: ResizeMode,
        strategy: FittingStrategy,
        targetWidth: Int,
        targetHeight: Int,
        percentage: Int = 50,
        allowUpscaling: Boolean = false
    ): Pair<Int, Int> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1 to 1

        val rawDimensions = when (mode) {
            ResizeMode.PERCENTAGE -> {
                val scale = (percentage.coerceIn(1, 400)) / 100.0
                (sourceWidth * scale).roundToInt().coerceAtLeast(1) to
                    (sourceHeight * scale).roundToInt().coerceAtLeast(1)
            }
            ResizeMode.LONGEST_EDGE -> {
                val longest = max(targetWidth, targetHeight).coerceAtLeast(1)
                if (sourceWidth >= sourceHeight) {
                    val scale = longest.toDouble() / sourceWidth.toDouble()
                    longest to (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                } else {
                    val scale = longest.toDouble() / sourceHeight.toDouble()
                    (sourceWidth * scale).roundToInt().coerceAtLeast(1) to longest
                }
            }
            ResizeMode.WIDTH_HEIGHT, ResizeMode.BOUNDING_BOX -> {
                val boundW = targetWidth.coerceAtLeast(1)
                val boundH = targetHeight.coerceAtLeast(1)
                when (strategy) {
                    FittingStrategy.FIT_WITHIN -> {
                        val scale = min(
                            boundW.toDouble() / sourceWidth.toDouble(),
                            boundH.toDouble() / sourceHeight.toDouble()
                        )
                        (sourceWidth * scale).roundToInt().coerceAtLeast(1) to
                            (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                    }
                    FittingStrategy.FILL_CROP,
                    FittingStrategy.PAD_MATTE,
                    FittingStrategy.STRETCH -> boundW to boundH
                }
            }
        }

        if (!allowUpscaling && mode != ResizeMode.PERCENTAGE) {
            if (rawDimensions.first > sourceWidth && rawDimensions.second > sourceHeight) {
                return sourceWidth to sourceHeight
            }
        }
        return rawDimensions
    }

    /** Batch-rename preview: pattern supports {n}, {name}, {ext}; result is validated + uniquified. */
    fun batchRenamePreview(names: List<String>, pattern: String, startAt: Int): List<String> {
        require(pattern.isNotBlank() && pattern.length <= 200 && startAt >= 0) { "INVALID_RENAME_PATTERN" }
        val used = mutableSetOf<String>()
        return names.mapIndexed { index, original ->
            val ext = original.substringAfterLast('.', "").takeIf { original.contains('.') }?.let { ".$it" } ?: ""
            var candidate = pattern.replace("{n}", (startAt + index).toString()).replace("{name}", original.removeSuffix(ext)).replace("{ext}", ext.removePrefix("."))
            if (!candidate.endsWith(ext, true) && ext.isNotEmpty() && !pattern.contains("{ext}")) candidate += ext
            val safe = com.jumlo.app.core.SafeNames.basename(candidate)
            val unique = com.jumlo.app.core.SafeNames.unique(used, safe); used += unique.lowercase(Locale.ROOT); unique
        }
    }

    /** Groups indices of identical SHA-256 digests; only groups of 2+ are duplicates. */
    fun duplicateGroups(hashes: List<String>): List<List<Int>> =
        hashes.mapIndexed { i, h -> h to i }.groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }

    /**
     * Formats byte counts into clean tabular strings (e.g. "2.62 GB", "840 KB").
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }
}
