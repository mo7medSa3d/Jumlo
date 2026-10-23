package com.jumlo.app.core

import java.io.*
import java.util.Locale
import java.util.zip.GZIPInputStream

data class TransportResponse(val code:Int,val headers:Map<String,String>,val body:InputStream,val close:()->Unit={})
fun interface HttpTransport { fun open(url:String,headers:Map<String,String>):TransportResponse }
data class TransferRequest(val url:String,val partialFile:File,val offset:Long=0,val strongEtag:String?=null,val lastModified:String?=null,val knownTotalBytes:Long?=null,val cleartextConsent:Boolean=false,val maxBytes:Long=8L*1024*1024*1024,val minFreeBytes:Long=64L*1024*1024,val finalUrlSha256:String?=null,val maxRetries:Int=3,val retryBackoffMs:Long=300L)
data class TransferMetadata(val finalUrlSha256:String,val strongEtag:String?,val lastModified:String?,val totalBytes:Long?,val mimeType:String?=null,val contentDisposition:String?=null)
data class TransferResult(val bytes:Long,val totalBytes:Long?,val strongEtag:String?,val lastModified:String?,val finalUrlSha256:String,val mimeType:String?=null,val contentDisposition:String?=null)

class StreamTransfer(private val transport:HttpTransport) {
    private fun isTransient(code:Int)=code in setOf(408,425,429,500,502,503,504)
    private fun isTransientIo(e:IOException):Boolean {
        if(e is InterruptedIOException && e !is java.net.SocketTimeoutException) return false
        val msg=e.message.orEmpty()
        if(msg in setOf("DISK_BUDGET_EXCEEDED","INSUFFICIENT_STORAGE","TRUNCATED_HTTP_BODY","OVERFLOW_HTTP_BODY","HTTP_CONTENT_LENGTH_MISMATCH")) return false
        return true
    }
    fun execute(request:TransferRequest,checkCancel:()->Unit={},progress:(Long,Long?)->Unit={_,_->},onMetadata:(TransferMetadata)->Unit={}):TransferResult {
        require(request.offset>=0 && (request.offset==0L || request.partialFile.length()==request.offset)) { "PARTIAL_OFFSET_MISMATCH" }
        val resolvedInitialUrl = DownloadLinkPolicy.resolveDirectUrl(request.url)
        var attempt=0
        val startOffset=request.offset
        while(true) {
            checkCancel()
            try {
                return executeSingleAttempt(request.copy(url = resolvedInitialUrl),checkCancel,progress,onMetadata)
            } catch(restart:RestartRequired) {
                throw restart
            } catch(e:IllegalArgumentException) {
                val code=e.message.orEmpty()
                val status=code.removePrefix("HTTP_").toIntOrNull()
                if(status!=null && isTransient(status) && attempt<request.maxRetries && request.partialFile.length()==startOffset) {
                    attempt++
                    sleepBackoff(request.retryBackoffMs*(1L shl (attempt-1)),checkCancel)
                    continue
                }
                throw e
            } catch(e:IOException) {
                if(isTransientIo(e) && attempt<request.maxRetries && request.partialFile.length()==startOffset) {
                    attempt++
                    sleepBackoff(request.retryBackoffMs*(1L shl (attempt-1)),checkCancel)
                    continue
                }
                throw e
            }
        }
    }

    private fun sleepBackoff(ms:Long,checkCancel:()->Unit) {
        if(ms<=0L) return
        val end=System.currentTimeMillis()+ms
        while(System.currentTimeMillis()<end) {
            checkCancel()
            Thread.sleep(minOf(25L,maxOf(1L,end-System.currentTimeMillis())))
        }
    }

    private fun executeSingleAttempt(request:TransferRequest,checkCancel:()->Unit,progress:(Long,Long?)->Unit,onMetadata:(TransferMetadata)->Unit):TransferResult {
        var current=HttpPolicy.url(request.url,request.cleartextConsent)
        repeat(11) { redirects ->
            checkCancel()
            val headers=mutableMapOf("Accept-Encoding" to "identity")
            if(request.offset>0) {
                headers["Range"]="bytes=${request.offset}-"
                val validator=request.strongEtag?.takeIf { !it.startsWith("W/") } ?: request.lastModified
                require(!validator.isNullOrBlank()) { "MISSING_RESUME_VALIDATOR" }
                headers["If-Range"]=validator
            }
            val response=transport.open(current.toString(),headers)
            try {
                if(response.code in setOf(301,302,303,307,308)) {
                    require(redirects<10) { "TOO_MANY_REDIRECTS" }
                    val location=response.headers["location"] ?: throw IllegalArgumentException("REDIRECT_WITHOUT_LOCATION")
                    val next=HttpPolicy.url(current.resolve(location).toString(),request.cleartextConsent)
                    require(current.scheme!="https" || next.scheme=="https") { "INSECURE_REDIRECT" }
                    current=next; return@repeat
                }
                val finalDigest=Hashing.sha256(ByteArrayInputStream(current.toString().toByteArray(Charsets.UTF_8)))
                if(request.offset>0 && response.code==200) {
                    // The server ignored the Range header and is sending the whole
                    // representation. Restart the transfer from byte 0 instead of
                    // failing the whole download.
                    response.close()
                    require(!request.partialFile.exists() || request.partialFile.delete()) { "DOWNLOAD_RESTART_FAILED" }
                    // The replacement may have no validators at all. Never carry
                    // the old representation's ETag into a later resumed request.
                    return executeSingleAttempt(request.copy(offset=0,strongEtag=null,lastModified=null,
                        knownTotalBytes=null,finalUrlSha256=null),checkCancel,progress,onMetadata)
                }
                if(request.offset>0 && (request.finalUrlSha256==null || request.finalUrlSha256!=finalDigest)) throw RestartRequired("FINAL_URL_CHANGED")
                val etag=response.headers["etag"]?.takeUnless { it.startsWith("W/") }
                val modified=response.headers["last-modified"]
                val contentEncoding=response.headers["content-encoding"]?.trim()?.lowercase(Locale.ROOT)
                val isGzipped = contentEncoding == "gzip" || contentEncoding == "x-gzip"
                val declared=if(isGzipped) null else response.headers["content-length"]?.let { value -> value.toLongOrNull()?.takeIf { it>=0 } ?: throw IOException("INVALID_CONTENT_LENGTH") }
                val total=if(request.offset>0) {
                    ResumePolicy.validate(request.offset,request.strongEtag,request.lastModified,request.knownTotalBytes,response.code,response.headers)
                    val end=response.headers.getValue("content-range").substringAfter("bytes ").substringBefore('/').substringAfter('-').toLong()
                    val expectedSlice=end-request.offset+1
                    if(declared!=null && declared!=expectedSlice) throw IOException("HTTP_CONTENT_LENGTH_MISMATCH")
                    // The end of this 206 range may be just an intermediate chunk.
                    // Completion is defined by Content-Range's representation total,
                    // never by the end index of the returned slice.
                    request.knownTotalBytes ?: requireNotNull(ResumePolicy.range(response.headers["content-range"])?.total) { "MISSING_RESUME_TOTAL" }
                } else {
                    require(response.code==200) { "HTTP_${response.code}" }
                    declared
                }
                if(total!=null) require(total<=request.maxBytes) { "DISK_BUDGET_EXCEEDED" }
                request.partialFile.parentFile?.mkdirs()
                val mime=response.headers["content-type"]?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
                val disposition=response.headers["content-disposition"]?.takeIf { it.isNotBlank() }
                onMetadata(TransferMetadata(finalDigest,etag ?: request.strongEtag,modified ?: request.lastModified,total,mime,disposition))
                var written=request.offset
                val streamToRead: InputStream = if(isGzipped && request.offset==0L) GZIPInputStream(response.body) else response.body
                FileOutputStream(request.partialFile,request.offset>0).use { out ->
                    val buf=ByteArray(64*1024)
                    while(true) {
                        checkCancel()
                        val read=streamToRead.read(buf); if(read<0) break
                        written+=read
                        if(written>request.maxBytes) throw IllegalArgumentException("DISK_BUDGET_EXCEEDED")
                        if((request.partialFile.parentFile?.usableSpace ?: Long.MAX_VALUE)<request.minFreeBytes) throw IOException("INSUFFICIENT_STORAGE")
                        out.write(buf,0,read); progress(written,total)
                    }
                    out.fd.sync()
                }
                checkCancel()
                if(written==0L) throw IOException("EMPTY_HTTP_BODY")
                if(total!=null && written!=total) throw IOException(if(written<total) "TRUNCATED_HTTP_BODY" else "OVERFLOW_HTTP_BODY")
                return TransferResult(written,total,etag ?: request.strongEtag,modified ?: request.lastModified,finalDigest,mime,disposition)
            } finally { response.close() }
        }
        throw IllegalArgumentException("TOO_MANY_REDIRECTS")
    }
}
