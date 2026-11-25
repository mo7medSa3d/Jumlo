package com.jumlo.app.core

import java.text.Normalizer
import java.util.Locale

/** Pure path policy used by MediaStore and the user's optional SAF destination. */
object StoragePaths {
    fun category(mime:String):String {
        val normalized=mime.substringBefore(';').trim().lowercase(Locale.ROOT)
        return when {
            normalized.startsWith("video/") -> "Video"
            normalized.startsWith("audio/") -> "Audio"
            normalized == "application/pdf" -> "PDF"
            normalized.startsWith("image/") -> "Images"
            else -> "Documents"
        }
    }
    fun relativePath(mime:String, subfolder:String?=null):String {
        val base="Download/Jumlo/${category(mime)}"
        val cleanSub=subfolder?.trim()?.takeIf { it.isNotEmpty() }?.let { AutoOutputName.cleanBaseName(it,"Folder") }
        return if(cleanSub!=null) "$base/$cleanSub/" else "$base/"
    }
    /** Only SAF tree URIs represent custom destinations. Previous generated
     * default folder values are replaced by the current public directory.
     * Existing result URIs are unaffected. */
    fun canonicalDownloadFolder(saved:String?):String =
        saved?.takeIf { it.startsWith("content://") } ?: "Download/Jumlo"
    fun uniqueName(desired:String,existing:Set<String>):String {
        val name=SafeNames.basename(desired)
        val folded = existing.mapTo(HashSet()) { Normalizer.normalize(it, Normalizer.Form.NFC).lowercase(Locale.ROOT) }
        if(name.lowercase(Locale.ROOT) !in folded) return name
        val dot=name.lastIndexOf('.').takeIf { it > 0 && name.length - it <= 12 }
        val base=if(dot != null) name.substring(0, dot) else name
        val suffix=if(dot != null) name.substring(dot) else ""
        for(i in 2..9999) {
            val counterSuffix=" ($i)$suffix"
            val maxBaseBytes=(200 - counterSuffix.toByteArray(Charsets.UTF_8).size).coerceAtLeast(1)
            val trimmedBase = if (base.isBlank()) "file" else runCatching { SafeNames.fitUtf8(base, maxBaseBytes) }.getOrDefault("file")
            val proposed="$trimmedBase$counterSuffix"
            if(proposed.lowercase(Locale.ROOT) !in folded) return SafeNames.basename(proposed)
        }
        throw IllegalStateException("NAME_COLLISION_LIMIT")
    }
}
