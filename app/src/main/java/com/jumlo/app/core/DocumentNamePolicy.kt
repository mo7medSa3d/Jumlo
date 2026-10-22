package com.jumlo.app.core

/** User-facing names are metadata; immutable verified payload names and hashes stay stable. */
object DocumentNamePolicy {
    fun rename(original:String, entered:String):String {
        val value=entered.trim()
        require(value.isNotEmpty() && value.length<=180 && value.none { it<' ' || it=='/' || it=='\\' }) { "INVALID_OUTPUT_NAME" }
        val extension=original.substringAfterLast('.',"")
        val named=if(extension.isNotBlank() && !value.endsWith(".$extension",true)) "$value.$extension" else value
        return SafeNames.basename(named)
    }
}
