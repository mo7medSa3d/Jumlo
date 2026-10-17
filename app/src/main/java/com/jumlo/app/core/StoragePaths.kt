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
    fun relativePath(mime:String):String="Download/Jevlo/${category(mime)}/"
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
