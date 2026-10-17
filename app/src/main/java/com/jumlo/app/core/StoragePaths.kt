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
    fun relativePath(mime:String):String="Download/Jumlo/${category(mime)}/"
    /** Keep custom SAF locations, but normalize the short-lived previous default.
     * Files already saved there remain reachable through their existing persisted URIs. */
    fun canonicalDownloadFolder(saved:String?):String = when(saved) {
        null, "Download/Jevlo" -> "Download/Jumlo"
        else -> saved
    }
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
