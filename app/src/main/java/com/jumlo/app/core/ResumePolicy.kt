package com.jumlo.app.core
import java.io.IOException

class RestartRequired(message: String): IOException(message)
data class ByteRange(val start: Long,val end: Long,val total: Long?)
object ResumePolicy {
    fun strong(tag: String?): String? = tag?.takeIf { it.startsWith('"') && it.endsWith('"') && !it.startsWith("W/") }
    fun range(header: String?): ByteRange? {
        val m=Regex("bytes ([0-9]+)-([0-9]+)/(\\*|[0-9]+)").matchEntire(header ?: return null) ?: return null
        val start=m.groupValues[1].toLongOrNull() ?: return null; val end=m.groupValues[2].toLongOrNull() ?: return null
        val total=if(m.groupValues[3]=="*") null else m.groupValues[3].toLongOrNull() ?: return null
        if(start>end || (total!=null && end>=total)) return null
        return ByteRange(start,end,total)
    }
    fun validate(offset: Long,etag: String?,modified: String?,total: Long?,status: Int,headers: Map<String,String>) {
        require(offset>=0)
        if(offset==0L) { require(status==200) { "HTTP_$status" }; return }
        fun restart(): Nothing = throw RestartRequired("DOWNLOAD_RESTART_REQUIRED")
        val validator=strong(etag)
        if(validator==null && modified.isNullOrBlank()) restart()
        if(status!=206) restart()
        val r=range(headers["content-range"]) ?: restart()
        if(r.start!=offset || (total!=null && r.total!=total)) restart()
        if(validator!=null && headers["etag"]!=validator) restart()
        if(validator==null && headers["last-modified"]!=modified) restart()
        val length=headers["content-length"]?.toLongOrNull()
        if(length!=null && length!=r.end-r.start+1) restart()
        if(headers["content-encoding"]?.let { !it.equals("identity",true) }==true) restart()
    }
}
