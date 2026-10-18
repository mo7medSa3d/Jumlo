package com.jumlo.app.core

import java.util.Locale

/** Pure path policy used by MediaStore and the user's optional SAF destination. */
object StoragePaths {
    fun category(mime:String):String=when {
        mime.startsWith("video/") -> "Video"
        mime.startsWith("audio/") -> "Audio"
        mime == "application/pdf" -> "PDF"
        mime.startsWith("image/") -> "Images"
        else -> "Documents"
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
        val folded = existing.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        if(name.lowercase(Locale.ROOT) !in folded) return name
        val ext=name.substringAfterLast('.',"")
        val base=if(ext.isNotBlank() && ext.length<12) name.dropLast(ext.length+1) else name
        val suffix=if(base==name) "" else ".$ext"
        for(i in 2..9999) {
            val counterSuffix=" ($i)$suffix"
            val maxBaseBytes=(200 - counterSuffix.toByteArray(Charsets.UTF_8).size).coerceAtLeast(1)
            val trimmedBase = SafeNames.fitUtf8(base, maxBaseBytes)
            val proposed="${trimmedBase.ifBlank { "file" }}$counterSuffix"
            if(proposed.lowercase(Locale.ROOT) !in folded) return SafeNames.basename(proposed)
        }
        throw IllegalStateException("NAME_COLLISION_LIMIT")
    }
}
