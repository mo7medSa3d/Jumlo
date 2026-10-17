package com.jumlo.app.core

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
        if(name !in existing) return name
        val ext=name.substringAfterLast('.',"")
        val base=if(ext.isNotBlank() && ext.length<12) name.dropLast(ext.length+1) else name
        val suffix=if(base==name) "" else ".$ext"
        for(i in 2..9999) {
            val proposed="${base.take(180)} ($i)$suffix"
            if(proposed !in existing) return SafeNames.basename(proposed)
        }
        throw IllegalStateException("NAME_COLLISION_LIMIT")
    }
}
