package com.jumlo.app.core
import java.io.IOException

class RestartRequired(message: String): IOException(message)
data class ByteRange(val start: Long,val end: Long,val total: Long?)
object ResumePolicy {
    private val CONTENT_RANGE = Regex("bytes ([0-9]+)-([0-9]+)/(\\*|[0-9]+)")
    fun strong(tag: String?): String? = tag?.takeIf { it.startsWith('"') && it.endsWith('"') && !it.startsWith("W/") }
    fun range(header: String?): ByteRange? {
        val m=CONTENT_RANGE.matchEntire(header ?: return null) ?: return null
        val start=m.groupValues[1].toLongOrNull() ?: return null; val end=m.groupValues[2].toLongOrNull() ?: return null
        val total=if(m.groupValues[3]=="*") null else m.groupValues[3].toLongOrNull() ?: return null
        if(start>end || (total!=null && end>=total)) return null
        return ByteRange(start,end,total)
    }
    fun validate(offset: Long,etag: String?,modified: String?,total: Long?,status: Int,headers: Map<String,String>) {
        require(offset>=0)
        if(offset==0L) { require(status==200) { "HTTP_$status" }; return }
        fun restart(): Nothing = throw RestartRequired("DOWNLOAD_RESTART_REQUIRED")
        fun header(name: String): String? = headers[name] ?: headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        val validator=strong(etag)
        if(validator==null && modified.isNullOrBlank()) restart()
        if(status!=206) restart()
        val r=range(header("content-range")) ?: restart()
        // A resumed 206 must identify the full object length. An arbitrary byte
        // slice (Content-Range: bytes 5-9/*) cannot establish completion.
        if(r.start!=offset || r.total==null || (total!=null && r.total!=total)) restart()
        if(validator!=null && header("etag")!=validator) restart()
        if(validator==null && header("last-modified")!=modified) restart()
        val length=header("content-length")?.toLongOrNull()
        if(length!=null && length!=r.end-r.start+1) restart()
        if(header("content-encoding")?.let { !it.equals("identity",true) }==true) restart()
    }
}
