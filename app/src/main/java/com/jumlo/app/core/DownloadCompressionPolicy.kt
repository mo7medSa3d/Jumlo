package com.jumlo.app.core

/** Keeps the published name and MIME type aligned with the bytes selected for download. */
data class DownloadImagePublication(val filename:String,val mimeType:String,val useCompressed:Boolean)

object DownloadCompressionPolicy {
    fun choose(sourceName:String,sourceMime:String,originalBytes:Long,candidateBytes:Long):DownloadImagePublication {
        val compressed=originalBytes>0L && candidateBytes>0L && candidateBytes<originalBytes
        return if(compressed) DownloadImagePublication(
            SafeNames.basename(AutoOutputName.cleanBaseName(sourceName,"Image")+".jpg"),"image/jpeg",true
        ) else DownloadImagePublication(SafeNames.basename(sourceName),sourceMime,false)
    }
}
